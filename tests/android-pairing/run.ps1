[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$FixtureRoot,[string]$Serial='emulator-5556',[switch]$Remote,[switch]$RestartServer,[switch]$PrivateNetwork)
$ErrorActionPreference='Stop'
if ($Remote -and $PrivateNetwork) { throw 'Choose either the legacy remote carrier or the private network fixture.' }
if ($RestartServer -and -not ($Remote -or $PrivateNetwork)) { throw 'Restart test requires a persistent fixture.' }
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$reportPath=Join-Path $repo $(if($PrivateNetwork){'desktop\artifacts\android-private-tests.json'}elseif($RestartServer){'desktop\artifacts\android-persistent-remote-tests.json'}elseif($Remote){'desktop\artifacts\android-remote-tests.json'}else{'desktop\artifacts\android-pairing-tests.json'})
if(Test-Path -LiteralPath $reportPath){Remove-Item -LiteralPath $reportPath}
$fixture=[IO.Path]::GetFullPath($FixtureRoot)
$sdk=$env:ANDROID_HOME
$adb=Join-Path $sdk 'platform-tools\adb.exe'
if ((& $adb -s $Serial shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Transport tests require an emulator, never a personal phone.' }
$tools=Get-ChildItem (Join-Path $sdk 'build-tools') -Directory|Sort-Object {[version]$_.Name} -Descending|Select-Object -First 1 -ExpandProperty FullName
$platform=Get-ChildItem (Join-Path $sdk 'platforms') -Directory|Sort-Object {[int]($_.Name -replace 'android-','')} -Descending|Select-Object -First 1 -ExpandProperty FullName
$android=Join-Path $platform 'android.jar'
$stage=Join-Path $repo ('desktop\artifacts\android-transport-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $stage 'classes'),(Join-Path $stage 'dex'),(Join-Path $stage 'assets') -Force|Out-Null
$libraries=@(Get-ChildItem (Join-Path $repo 'libs') -Filter '*.jar'|Select-Object -ExpandProperty FullName)
$transportSource=Join-Path $stage 'RemoteTunnel.java'
$transportText=[IO.File]::ReadAllText((Join-Path $repo 'app\src\main\java\com\leal\comfyremote\RemoteTunnel.java'))
# Diagnostic logging exists only in this synthetic APK, never the product sources.
$transportText=$transportText.Replace('catch(Exception ignored){}finally{','catch(Exception error){android.util.Log.e("bwdacc-remote-test","carrier failure",error);}finally{')
$transportText=$transportText.Replace('public void onError(Exception error){','public void onError(Exception error){android.util.Log.e("bwdacc-remote-test","outer websocket",error);')
# Accelerate only the synthetic APK's idle clock. A 12-second download must keep
# its carrier alive past the 10-second idle limit because receive traffic is active.
if($RestartServer){$transportText=$transportText.Replace('local.setSoTimeout(30_000);','local.setSoTimeout(1_000);').Replace('TimeUnit.SECONDS.toNanos(120)','TimeUnit.SECONDS.toNanos(10)')}
[IO.File]::WriteAllText($transportSource,$transportText,[Text.UTF8Encoding]::new($false))
$sources=@((Join-Path $PSScriptRoot 'TransportProbe.java'),(Join-Path $repo 'app\src\main\java\com\leal\comfyremote\DesktopConnection.java'),$transportSource,(Join-Path $repo 'app\src\main\java\com\leal\comfyremote\ComfyProgressSocket.java'))
& (Join-Path $env:JAVA_HOME 'bin\javac.exe') -encoding UTF-8 -source 8 -target 8 -classpath ((@($android)+$libraries)-join [IO.Path]::PathSeparator) -d (Join-Path $stage 'classes') @sources
if($LASTEXITCODE -ne 0){throw 'Probe compilation failed'}
& (Join-Path $env:JAVA_HOME 'bin\jar.exe') cf (Join-Path $stage 'classes.jar') -C (Join-Path $stage 'classes') .
if($LASTEXITCODE -ne 0){throw 'Probe packaging failed'}
& (Join-Path $tools 'd8.bat') --lib $android --min-api 26 --output (Join-Path $stage 'dex') (Join-Path $stage 'classes.jar') @libraries
if($LASTEXITCODE -ne 0){throw 'Probe dex failed'}
Copy-Item -LiteralPath (Join-Path $fixture 'invite.json'),(Join-Path $fixture 'invite.png') -Destination (Join-Path $stage 'assets')
$unsigned=Join-Path $stage 'unsigned.apk'
& (Join-Path $tools 'aapt2.exe') link --manifest (Join-Path $PSScriptRoot 'AndroidManifest.xml') -I $android --min-sdk-version 26 --target-sdk-version 36 -A (Join-Path $stage 'assets') -o $unsigned
if($LASTEXITCODE -ne 0){throw 'Probe resources failed'}
Push-Location (Join-Path $stage 'dex')
try { & (Join-Path $tools 'aapt.exe') add $unsigned @(Get-ChildItem -Filter "classes*.dex"|Select-Object -ExpandProperty Name); if($LASTEXITCODE -ne 0){throw 'Probe dex packaging failed'} } finally { Pop-Location }
& (Join-Path $tools 'zipalign.exe') -f 4 $unsigned (Join-Path $stage 'aligned.apk')
if($LASTEXITCODE -ne 0){throw 'Probe alignment failed'}
$apk=Join-Path $stage 'transport-test.apk'
& (Join-Path $tools 'apksigner.bat') sign --ks (Join-Path $repo '.debug\debug.keystore') --ks-pass pass:android --key-pass pass:android --out $apk (Join-Path $stage 'aligned.apk')
if($LASTEXITCODE -ne 0){throw 'Probe signing failed'}
if(-not ($Remote -or $PrivateNetwork)){& $adb -s $Serial reverse tcp:8190 tcp:8190
if($LASTEXITCODE -ne 0){throw 'ADB test forwarding failed'}}
& $adb -s $Serial install -r $apk
if($LASTEXITCODE -ne 0){throw 'Probe installation failed'}
$results=@()
try {
 $phases=@('pair','reopen')
 if($RestartServer){$phases+='serverrestart'}
 $phases+=@('revoked','corrupt','corruptcheck')
 foreach($phase in $phases){
    if($phase -eq 'serverrestart'){
        Set-Content -LiteralPath (Join-Path $fixture 'restart') -Value 'true'
        $limit=[DateTime]::UtcNow.AddSeconds(15)
        while(-not (Test-Path -LiteralPath (Join-Path $fixture 'restarted')) -and [DateTime]::UtcNow -lt $limit){Start-Sleep -Milliseconds 200}
        if(-not(Test-Path -LiteralPath (Join-Path $fixture 'restarted'))){throw 'Fixture restart timed out'}
    }
    if($phase -eq 'revoked'){
        Set-Content -LiteralPath (Join-Path $fixture 'revoke') -Value 'true'
        $limit=[DateTime]::UtcNow.AddSeconds(10)
        while(-not (Test-Path -LiteralPath (Join-Path $fixture 'revoked')) -and [DateTime]::UtcNow -lt $limit){Start-Sleep -Milliseconds 200}
        if(-not(Test-Path -LiteralPath (Join-Path $fixture 'revoked'))){throw 'Fixture revocation timed out'}
    }
    & $adb -s $Serial shell am force-stop com.leal.bwdacc.transporttest
    & $adb -s $Serial shell run-as com.leal.bwdacc.transporttest rm -f "files/result-$phase.json"
    & $adb -s $Serial shell am start -n com.leal.bwdacc.transporttest/com.leal.comfyremote.TransportProbe --es phase $phase | Out-Null
    $limit=[DateTime]::UtcNow.AddSeconds(100); $result=$null
    while([DateTime]::UtcNow -lt $limit){
        $raw=& $adb -s $Serial exec-out run-as com.leal.bwdacc.transporttest cat "files/result-$phase.json" 2>$null
        if($LASTEXITCODE -eq 0){try{$result=($raw -join [Environment]::NewLine)|ConvertFrom-Json;break}catch{}}
        Start-Sleep -Milliseconds 500
    }
    if(-not $result -or -not $result.passed){throw "Android phase $phase failed: $($result|ConvertTo-Json -Compress)"}
    $results+=$result
 }
 $results|ConvertTo-Json -Depth 5|Set-Content -LiteralPath $reportPath -Encoding utf8
 $results|ConvertTo-Json -Depth 5
} finally {
 & $adb -s $Serial shell am force-stop com.leal.bwdacc.transporttest
 if(-not ($Remote -or $PrivateNetwork)){& $adb -s $Serial reverse --remove tcp:8190}
}
