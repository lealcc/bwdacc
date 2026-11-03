[CmdletBinding()]
param([string]$Serial='emulator-5556')
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$sdk=$env:ANDROID_HOME
$adb=Join-Path $sdk 'platform-tools\adb.exe'
if ((& $adb -s $Serial shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Visual tests require an emulator.' }
$tools=Get-ChildItem (Join-Path $sdk 'build-tools') -Directory|Sort-Object {[version]$_.Name} -Descending|Select-Object -First 1 -ExpandProperty FullName
$platform=Get-ChildItem (Join-Path $sdk 'platforms') -Directory|Sort-Object {[int]($_.Name -replace 'android-','')} -Descending|Select-Object -First 1 -ExpandProperty FullName
$android=Join-Path $platform 'android.jar'
$stage=Join-Path $repo ('desktop\artifacts\android-visual-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $stage 'classes'),(Join-Path $stage 'dex'),(Join-Path $stage 'assets') -Force|Out-Null
Copy-Item -Path (Join-Path $repo 'app/src/main/assets/*') -Destination (Join-Path $stage 'assets') -Recurse
foreach($fixture in @('visual-segment.json','visual-composition.json')) { Copy-Item -LiteralPath (Join-Path $repo "desktop/artifacts/$fixture") -Destination (Join-Path $stage 'assets') }
# Optional live output from the bundled stock image, never a personal generation.
$cutout=Join-Path $repo 'desktop/artifacts/image-tools-live/background.png'
if(Test-Path -LiteralPath $cutout){Copy-Item -LiteralPath $cutout -Destination (Join-Path $stage 'assets/visual-cutout.png')}
$libraries=@(Get-ChildItem (Join-Path $repo 'libs') -Filter '*.jar'|Select-Object -ExpandProperty FullName)
$sources=@((Join-Path $PSScriptRoot 'VisualProbe.java'))+@(Get-ChildItem (Join-Path $repo 'app/src/main/java') -Recurse -Filter '*.java'|Select-Object -ExpandProperty FullName)
& (Join-Path $env:JAVA_HOME 'bin/javac.exe') -encoding UTF-8 -source 8 -target 8 -classpath ((@($android)+$libraries)-join [IO.Path]::PathSeparator) -d (Join-Path $stage 'classes') @sources
if($LASTEXITCODE -ne 0){throw 'Probe compilation failed'}
& (Join-Path $env:JAVA_HOME 'bin/jar.exe') cf (Join-Path $stage 'classes.jar') -C (Join-Path $stage 'classes') .
& (Join-Path $tools 'd8.bat') --lib $android --min-api 26 --output (Join-Path $stage 'dex') (Join-Path $stage 'classes.jar') @libraries
if($LASTEXITCODE -ne 0){throw 'Probe dex failed'}
$unsigned=Join-Path $stage 'unsigned.apk'
& (Join-Path $tools 'aapt2.exe') compile --dir (Join-Path $repo 'app/src/main/res') -o (Join-Path $stage 'resources.zip')
& (Join-Path $tools 'aapt2.exe') link --manifest (Join-Path $PSScriptRoot 'AndroidManifest.xml') -I $android (Join-Path $stage 'resources.zip') --min-sdk-version 26 --target-sdk-version 36 -o $unsigned
if($LASTEXITCODE -ne 0){throw 'Probe resources failed'}
Push-Location (Join-Path $stage 'dex')
try { & (Join-Path $tools 'aapt.exe') add $unsigned @(Get-ChildItem -Filter 'classes*.dex'|Select-Object -ExpandProperty Name) } finally { Pop-Location }
Push-Location $stage
try { foreach($asset in (Get-ChildItem -LiteralPath (Join-Path $stage 'assets') -Recurse -File)) {
 & (Join-Path $tools 'aapt.exe') add $unsigned $asset.FullName.Substring($stage.Length+1).Replace('\','/') | Out-Null
 if($LASTEXITCODE -ne 0){throw 'Probe asset packaging failed'}
} } finally { Pop-Location }
& (Join-Path $tools 'zipalign.exe') -f 4 $unsigned (Join-Path $stage 'aligned.apk')
$apk=Join-Path $stage 'visual-test.apk'
& (Join-Path $tools 'apksigner.bat') sign --ks (Join-Path $repo '.debug/debug.keystore') --ks-pass pass:android --key-pass pass:android --out $apk (Join-Path $stage 'aligned.apk')
if($LASTEXITCODE -ne 0){throw 'Probe signing failed'}
& $adb -s $Serial install -r $apk
if($LASTEXITCODE -ne 0){throw 'Probe installation failed'}
# Only the disposable synthetic package is cleared, never either user's app.
& $adb -s $Serial shell pm clear com.leal.bwdacc.visualtest | Out-Null
& $adb -s $Serial shell am start -n com.leal.bwdacc.visualtest/com.leal.comfyremote.VisualProbe --es phase manager | Out-Null
$until=[DateTime]::UtcNow.AddSeconds(30);$result=$null
while([DateTime]::UtcNow -lt $until){
 $raw=& $adb -s $Serial exec-out run-as com.leal.bwdacc.visualtest cat cache/visual-tests.json 2>&1
 if($LASTEXITCODE -eq 0){try{$result=($raw -join [Environment]::NewLine)|ConvertFrom-Json;break}catch{}}
 Start-Sleep -Milliseconds 300
}
if(-not $result -or -not $result.passed){throw "Visual tests failed: $($result|ConvertTo-Json -Compress)"}
$result|ConvertTo-Json|Set-Content (Join-Path $repo 'desktop/artifacts/visual-android-tests.json')
$result
