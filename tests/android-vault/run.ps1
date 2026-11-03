[CmdletBinding()]
param([string]$Serial='emulator-5556')
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$sdk=$env:ANDROID_HOME
$adb=Join-Path $sdk 'platform-tools\adb.exe'
if ((& $adb -s $Serial shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Vault tests require an emulator, never a personal phone.' }
$tools=Get-ChildItem (Join-Path $sdk 'build-tools') -Directory|Sort-Object {[version]$_.Name} -Descending|Select-Object -First 1 -ExpandProperty FullName
$platform=Get-ChildItem (Join-Path $sdk 'platforms') -Directory|Sort-Object {[int]($_.Name -replace 'android-','')} -Descending|Select-Object -First 1 -ExpandProperty FullName
$android=Join-Path $platform 'android.jar'
$stage=Join-Path $repo ('desktop\artifacts\android-vault-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $stage 'classes'),(Join-Path $stage 'dex') -Force|Out-Null
$libraries=@(Get-ChildItem (Join-Path $repo 'libs') -Filter '*.jar'|Select-Object -ExpandProperty FullName)
$sources=@((Join-Path $PSScriptRoot 'VaultProbe.java'),(Join-Path $repo 'tests\GenerationStorageTest.java'))+
    @(Get-ChildItem (Join-Path $repo 'app\src\main\java') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
& (Join-Path $env:JAVA_HOME 'bin\javac.exe') -encoding UTF-8 -source 8 -target 8 -classpath ((@($android)+$libraries)-join [IO.Path]::PathSeparator) -d (Join-Path $stage 'classes') @sources
if($LASTEXITCODE -ne 0){throw 'Probe compilation failed'}
& (Join-Path $env:JAVA_HOME 'bin\jar.exe') cf (Join-Path $stage 'classes.jar') -C (Join-Path $stage 'classes') .
if($LASTEXITCODE -ne 0){throw 'Probe packaging failed'}
& (Join-Path $tools 'd8.bat') --lib $android --min-api 26 --output (Join-Path $stage 'dex') (Join-Path $stage 'classes.jar') @libraries
if($LASTEXITCODE -ne 0){throw 'Probe dex failed'}
$unsigned=Join-Path $stage 'unsigned.apk'
& (Join-Path $tools 'aapt2.exe') link --manifest (Join-Path $PSScriptRoot 'AndroidManifest.xml') -I $android --min-sdk-version 26 --target-sdk-version 36 -A (Join-Path $PSScriptRoot 'assets') -o $unsigned
if($LASTEXITCODE -ne 0){throw 'Probe resources failed'}
Push-Location (Join-Path $stage 'dex')
try { & (Join-Path $tools 'aapt.exe') add $unsigned @(Get-ChildItem -Filter 'classes*.dex'|Select-Object -ExpandProperty Name); if($LASTEXITCODE -ne 0){throw 'Probe dex packaging failed'} } finally { Pop-Location }
& (Join-Path $tools 'zipalign.exe') -f 4 $unsigned (Join-Path $stage 'aligned.apk')
if($LASTEXITCODE -ne 0){throw 'Probe alignment failed'}
$apk=Join-Path $stage 'vault-test.apk'
& (Join-Path $tools 'apksigner.bat') sign --ks (Join-Path $repo '.debug\debug.keystore') --ks-pass pass:android --key-pass pass:android --out $apk (Join-Path $stage 'aligned.apk')
if($LASTEXITCODE -ne 0){throw 'Probe signing failed'}
& $adb -s $Serial install -r $apk
if($LASTEXITCODE -ne 0){throw 'Probe installation failed'}
# Only this disposable synthetic test package may be cleared.
& $adb -s $Serial shell pm clear com.leal.bwdacc.vaulttest
$results=@()
try {
 foreach($phase in @('seed','roundtrip','reopen','corrupt_keyset','reject_keyset','lose_key','reject_missing_key','seed_interrupt','migrate_interrupt','resume_interrupt')){
    if($phase -eq 'seed_interrupt'){ & $adb -s $Serial shell pm clear com.leal.bwdacc.vaulttest | Out-Null }
    & $adb -s $Serial shell am force-stop com.leal.bwdacc.vaulttest
    & $adb -s $Serial shell am start -n com.leal.bwdacc.vaulttest/com.leal.comfyremote.VaultProbe --es phase $phase | Out-Null
    if($phase -eq 'migrate_interrupt'){
        $limit=[DateTime]::UtcNow.AddSeconds(20);$interrupted=$false
        while([DateTime]::UtcNow -lt $limit){
            & $adb -s $Serial shell run-as com.leal.bwdacc.vaulttest test -f files/images/interruption.img.new 2>$null
            if($LASTEXITCODE -eq 0){
                & $adb -s $Serial shell am force-stop com.leal.bwdacc.vaulttest
                $interrupted=$true;break
            }
            Start-Sleep -Milliseconds 30
        }
        if(-not $interrupted){throw 'Did not observe migration staging; interrupted-migration check is inconclusive.'}
        $results+=[pscustomobject]@{phase=$phase;passed=$true;checks=1}
        continue
    }
    $limit=[DateTime]::UtcNow.AddSeconds(45); $result=$null
    while([DateTime]::UtcNow -lt $limit){
        $raw=& $adb -s $Serial exec-out run-as com.leal.bwdacc.vaulttest cat "cache/result-$phase.json" 2>$null
        if($LASTEXITCODE -eq 0){try{$result=($raw -join [Environment]::NewLine)|ConvertFrom-Json;break}catch{}}
        Start-Sleep -Milliseconds 400
    }
    if(-not $result -or -not $result.passed){throw "Android phase $phase failed: $($result|ConvertTo-Json -Compress)"}
    $results+=$result
 }
 $results|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $repo 'desktop\artifacts\android-vault-tests.json') -Encoding utf8
 $results|ConvertTo-Json -Depth 5
} finally { & $adb -s $Serial shell am force-stop com.leal.bwdacc.vaulttest }
