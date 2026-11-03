[CmdletBinding()]
param([string]$Serial='emulator-5556')
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$sdk=$env:ANDROID_HOME
$adb=Join-Path $sdk 'platform-tools\adb.exe'
if ((& $adb -s $Serial shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Backup tests require an emulator.' }
$tools=Get-ChildItem (Join-Path $sdk 'build-tools') -Directory|Sort-Object {[version]$_.Name} -Descending|Select-Object -First 1 -ExpandProperty FullName
$platform=Get-ChildItem (Join-Path $sdk 'platforms') -Directory|Sort-Object {[int]($_.Name -replace 'android-','')} -Descending|Select-Object -First 1 -ExpandProperty FullName
$android=Join-Path $platform 'android.jar'
$stage=Join-Path $repo ('desktop\artifacts\android-backup-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $stage 'classes'),(Join-Path $stage 'dex'),(Join-Path $stage 'assets') -Force|Out-Null
Copy-Item (Join-Path $repo 'tests\android-vault\assets\sample.mp4') (Join-Path $stage 'assets\sample.mp4')
$libraries=@(Get-ChildItem (Join-Path $repo 'libs') -Filter '*.jar'|Select-Object -ExpandProperty FullName)
$sources=@((Join-Path $PSScriptRoot 'BackupProbe.java'))+@(Get-ChildItem (Join-Path $repo 'app\src\main\java') -Recurse -Filter '*.java'|Select-Object -ExpandProperty FullName)
& (Join-Path $env:JAVA_HOME 'bin\javac.exe') -encoding UTF-8 -source 8 -target 8 -classpath ((@($android)+$libraries)-join [IO.Path]::PathSeparator) -d (Join-Path $stage 'classes') @sources
if($LASTEXITCODE -ne 0){throw 'Compilation failed'}
& (Join-Path $env:JAVA_HOME 'bin\jar.exe') cf (Join-Path $stage 'classes.jar') -C (Join-Path $stage 'classes') .
if($LASTEXITCODE -ne 0){throw 'Packaging failed'}
& (Join-Path $tools 'd8.bat') --lib $android --min-api 26 --output (Join-Path $stage 'dex') (Join-Path $stage 'classes.jar') @libraries
if($LASTEXITCODE -ne 0){throw 'Dex failed'}
$source='com.leal.bwdacc.backupsource';$target='com.leal.bwdacc.backuptarget'
foreach($package in @($source,$target)){
    $manifest='<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="'+$package+'"><application android:label="Synthetic backup tests" android:debuggable="true" android:allowBackup="false"><activity android:name="com.leal.comfyremote.BackupProbe" android:exported="true" /></application></manifest>'
    $manifestPath=Join-Path $stage ($package+'.xml')
    [IO.File]::WriteAllText($manifestPath,$manifest)
    $unsigned=Join-Path $stage ($package+'-unsigned.apk')
    & (Join-Path $tools 'aapt2.exe') link --manifest $manifestPath -I $android --min-sdk-version 26 --target-sdk-version 36 -A (Join-Path $stage 'assets') -o $unsigned
    if($LASTEXITCODE -ne 0){throw 'Resources failed'}
    Push-Location (Join-Path $stage 'dex')
    try { & (Join-Path $tools 'aapt.exe') add $unsigned @(Get-ChildItem -Filter 'classes*.dex'|Select-Object -ExpandProperty Name); if($LASTEXITCODE -ne 0){throw 'Dex packaging failed'} } finally { Pop-Location }
    & (Join-Path $tools 'zipalign.exe') -f 4 $unsigned (Join-Path $stage 'aligned.apk')
    if($LASTEXITCODE -ne 0){throw 'Alignment failed'}
    $apk=Join-Path $stage ($package+'.apk')
    & (Join-Path $tools 'apksigner.bat') sign --ks (Join-Path $repo '.debug\debug.keystore') --ks-pass pass:android --key-pass pass:android --out $apk (Join-Path $stage 'aligned.apk')
    if($LASTEXITCODE -ne 0){throw 'Signing failed'}
    & $adb -s $Serial install -r $apk
    if($LASTEXITCODE -ne 0){throw 'Installation failed'}
    & $adb -s $Serial shell pm clear $package | Out-Null
}
$results=[Collections.Generic.List[object]]::new()
function Invoke-Phase([string]$Package,[string]$Phase){
    & $adb -s $Serial shell am force-stop $Package
    & $adb -s $Serial shell am start -n "$Package/com.leal.comfyremote.BackupProbe" --es phase $Phase | Out-Null
    $limit=[DateTime]::UtcNow.AddSeconds(45);$result=$null
    while([DateTime]::UtcNow -lt $limit){
        $raw=& $adb -s $Serial exec-out run-as $Package cat "cache/result-$Phase.json" 2>$null
        if($LASTEXITCODE -eq 0){try{$result=($raw-join [Environment]::NewLine)|ConvertFrom-Json;break}catch{}}
        Start-Sleep -Milliseconds 300
    }
    if(-not $result -or -not $result.passed){throw "Phase $Phase failed: $($result|ConvertTo-Json -Compress)"}
    $results.Add($result)
}
try{
    Invoke-Phase $source export
    & $adb -s $Serial shell run-as $target mkdir -p cache
    python (Join-Path $PSScriptRoot 'transfer.py') $adb $Serial $stage
    if($LASTEXITCODE -ne 0){throw 'Fixture transfer failed'}
    foreach($phase in @('negative','restore','reopen','occupied')){Invoke-Phase $target $phase}
    foreach($point in @('before','between','after','corrupt')){
        & $adb -s $Serial shell pm clear $target | Out-Null
        Invoke-Phase $target ('fixture_'+$point)
        Invoke-Phase $target $(if($point -eq 'corrupt'){'reject_corrupt'}else{'recover'})
    }
    $results|ConvertTo-Json -Depth 5|Set-Content (Join-Path $repo 'desktop\artifacts\android-backup-tests.json') -Encoding utf8
    $results|ConvertTo-Json -Depth 5
}finally{foreach($package in @($source,$target)){& $adb -s $Serial shell am force-stop $package}}
