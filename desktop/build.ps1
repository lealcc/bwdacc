[CmdletBinding()]
param([switch]$Test)
$ErrorActionPreference = 'Stop'
$taskSdk = Join-Path $PSScriptRoot '.tools\dotnet\dotnet.exe'
if (-not (Test-Path -LiteralPath $taskSdk)) {
    $taskTools = Join-Path $PSScriptRoot '.tools'
    New-Item -ItemType Directory -Path $taskTools -Force | Out-Null
    $taskArchive = Join-Path $taskTools 'dotnet-sdk-10.0.403-win-x64.zip'
    $taskExpected = '7a114c318fa7f1d9822991b534b2814d0e78386ceda816b371f5141acefe741d87ccb606ede06cadac8f9ac5070e32b708a67bbd1619ff5e5a7858a27874ef48'
    if (-not (Test-Path -LiteralPath $taskArchive)) {
        Invoke-WebRequest -Uri 'https://builds.dotnet.microsoft.com/dotnet/Sdk/10.0.403/dotnet-sdk-10.0.403-win-x64.zip' -OutFile $taskArchive
    }
    if ((Get-FileHash -LiteralPath $taskArchive -Algorithm SHA512).Hash.ToLowerInvariant() -ne $taskExpected) { throw 'SDK integrity check failed. No archive was extracted.' }
    Expand-Archive -LiteralPath $taskArchive -DestinationPath (Join-Path $taskTools 'dotnet')
}
$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
$env:DOTNET_NOLOGO = '1'
$project = Join-Path $PSScriptRoot 'Bwdacc.Desktop\Bwdacc.Desktop.csproj'
$artifacts = Join-Path $PSScriptRoot 'artifacts'
& $taskSdk publish $project -c Release -r win-x64 --self-contained true -p:RestoreLockedMode=true -o $artifacts --nologo -v minimal
if ($LASTEXITCODE -ne 0) { throw 'Desktop build failed' }
$exe = Join-Path $artifacts 'bwdacc-desktop.exe'
Get-FileHash -LiteralPath $exe -Algorithm SHA256
$taskHash = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath (Join-Path $artifacts 'SHA256SUMS.txt') -Value "$taskHash  bwdacc-desktop.exe" -Encoding utf8
if ($Test) {
    $externalReport = Join-Path $artifacts 'external-comfy-tests.json'
    $externalProcess = Start-Process -FilePath $exe -ArgumentList @('--test-external', ('"' + $externalReport + '"')) -WindowStyle Hidden -PassThru -Wait
    if ($externalProcess.ExitCode -ne 0) { throw "External Comfy tests failed. See $externalReport.error.txt" }
    Get-Content -LiteralPath $externalReport
    $report = Join-Path $artifacts 'self-tests.json'
    $process = Start-Process -FilePath $exe -ArgumentList @('--self-test', ('"' + $report + '"')) -WindowStyle Hidden -PassThru -Wait
    if ($process.ExitCode -ne 0) { throw "Desktop tests failed. See $report.error.txt" }
    Get-Content -LiteralPath $report
    $pairingReport = Join-Path $artifacts 'pairing-tests.json'
    $pairingProcess = Start-Process -FilePath $exe -ArgumentList @('--test-pairing', ('"' + $pairingReport + '"')) -WindowStyle Hidden -PassThru -Wait
    if ($pairingProcess.ExitCode -ne 0) { throw "Pairing tests failed. See $pairingReport.error.txt" }
    Get-Content -LiteralPath $pairingReport
    $remoteReport = Join-Path $artifacts 'remote-tests.json'
    $remoteProcess = Start-Process -FilePath $exe -ArgumentList @('--test-remote', ('"' + $remoteReport + '"')) -WindowStyle Hidden -PassThru -Wait
    if ($remoteProcess.ExitCode -ne 0) { throw "Remote tests failed. See $remoteReport.error.txt" }
    Get-Content -LiteralPath $remoteReport
    $persistentReport = Join-Path $artifacts 'persistent-remote-tests.json'
    $persistentProcess = Start-Process -FilePath $exe -ArgumentList @('--test-persistent-remote', ('"' + $persistentReport + '"')) -WindowStyle Hidden -PassThru -Wait
    if ($persistentProcess.ExitCode -ne 0) { throw "Persistent remote tests failed. See $persistentReport.error.txt" }
    Get-Content -LiteralPath $persistentReport
    $privateReport = Join-Path $artifacts 'private-connection-tests.json'
    $privateProcess = Start-Process -FilePath $exe -ArgumentList @('--test-private', ('"' + $privateReport + '"')) -WindowStyle Hidden -PassThru -Wait
    if ($privateProcess.ExitCode -ne 0) { throw "Private connection tests failed. See $privateReport.error.txt" }
    Get-Content -LiteralPath $privateReport
}
