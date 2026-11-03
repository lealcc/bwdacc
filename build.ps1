[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$ModuleDir = $PSScriptRoot
$SourceDir = Join-Path $ModuleDir "app\src\main"
$BuildDir = Join-Path $ModuleDir "build"
$SigningDir = Join-Path $ModuleDir ".debug"
$ClassesDir = Join-Path $BuildDir "classes"
$DexDir = Join-Path $BuildDir "dex"

$SdkDir = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
if (-not $SdkDir) {
    throw "ANDROID_SDK_ROOT or ANDROID_HOME must point to an Android SDK."
}

$BuildToolsDir = Get-ChildItem -Directory (Join-Path $SdkDir "build-tools") |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1 -ExpandProperty FullName
$PlatformDir = Get-ChildItem -Directory (Join-Path $SdkDir "platforms") |
    Sort-Object { [int]($_.Name -replace "android-", "") } -Descending |
    Select-Object -First 1 -ExpandProperty FullName

$AndroidJar = Join-Path $PlatformDir "android.jar"
$Libraries = @(Get-ChildItem -LiteralPath (Join-Path $ModuleDir "libs") -Filter "*.jar" | Select-Object -ExpandProperty FullName)
$DependencyLock = Get-Content -LiteralPath (Join-Path $ModuleDir "libs\dependencies.json") -Raw | ConvertFrom-Json
if ($Libraries.Count -ne $DependencyLock.Count) { throw "Unlisted or missing Android dependency." }
foreach ($Dependency in $DependencyLock) {
    $DependencyPath = Join-Path $ModuleDir ("libs\" + $Dependency.file)
    if ((Get-FileHash -LiteralPath $DependencyPath -Algorithm SHA256).Hash -ne $Dependency.sha256) {
        throw "Android dependency checksum failed: $($Dependency.file)"
    }
}
$CompileClasspath = (@($AndroidJar) + $Libraries) -join [IO.Path]::PathSeparator
$Aapt2 = Join-Path $BuildToolsDir "aapt2.exe"
$Aapt = Join-Path $BuildToolsDir "aapt.exe"
$D8 = Join-Path $BuildToolsDir "d8.bat"
$ZipAlign = Join-Path $BuildToolsDir "zipalign.exe"
$ApkSigner = Join-Path $BuildToolsDir "apksigner.bat"
$Javac = Join-Path $env:JAVA_HOME "bin\javac.exe"
$Jar = Join-Path $env:JAVA_HOME "bin\jar.exe"
$KeyTool = Join-Path $env:JAVA_HOME "bin\keytool.exe"

foreach ($RequiredTool in @($AndroidJar, $Aapt2, $Aapt, $D8, $ZipAlign, $ApkSigner, $Javac, $Jar, $KeyTool)) {
    if (-not (Test-Path -LiteralPath $RequiredTool)) {
        throw "Required build tool not found: $RequiredTool"
    }
}

if (Test-Path -LiteralPath $BuildDir) {
    $ResolvedBuildDir = (Resolve-Path -LiteralPath $BuildDir).Path
    $ExpectedBuildDir = [System.IO.Path]::GetFullPath((Join-Path $ModuleDir "build"))
    if ($ResolvedBuildDir -ne $ExpectedBuildDir) {
        throw "Refusing to clean unexpected build directory: $ResolvedBuildDir"
    }
    Remove-Item -LiteralPath $ResolvedBuildDir -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $ClassesDir, $DexDir, $SigningDir | Out-Null

$JavaSources = @(Get-ChildItem -Recurse -File (Join-Path $SourceDir "java") -Filter "*.java" |
    Select-Object -ExpandProperty FullName)
if (-not $JavaSources) {
    throw "No Java sources found."
}

& $Javac -encoding UTF-8 -source 8 -target 8 -classpath $CompileClasspath -d $ClassesDir @JavaSources
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

$ClassesJar = Join-Path $BuildDir "classes.jar"
& $Jar cf $ClassesJar -C $ClassesDir .
if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }
& $D8 --lib $AndroidJar --min-api 26 --output $DexDir $ClassesJar @Libraries
if ($LASTEXITCODE -ne 0) { throw "d8 failed with exit code $LASTEXITCODE" }

$UnsignedApk = Join-Path $BuildDir "app-unsigned-unaligned.apk"
$AlignedApk = Join-Path $BuildDir "app-unsigned-aligned.apk"
$OutputApk = Join-Path $BuildDir "bwdacc-commercial-debug.apk"
$Manifest = Join-Path $SourceDir "AndroidManifest.xml"

$Resources = Join-Path $BuildDir "resources.zip"
& $Aapt2 compile --dir (Join-Path $SourceDir "res") -o $Resources
if ($LASTEXITCODE -ne 0) { throw "aapt2 resource compilation failed with exit code $LASTEXITCODE" }
& $Aapt2 link --manifest $Manifest -I $AndroidJar $Resources --min-sdk-version 26 --target-sdk-version 36 --version-code 19 --version-name "0.11.7-dev" -o $UnsignedApk
if ($LASTEXITCODE -ne 0) { throw "aapt2 failed with exit code $LASTEXITCODE" }

Push-Location $DexDir
try {
    & $Aapt add $UnsignedApk @(Get-ChildItem -Filter "classes*.dex" | Select-Object -ExpandProperty Name)
    if ($LASTEXITCODE -ne 0) { throw "aapt failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

$AssetsDir = Join-Path $SourceDir "assets"
if (Test-Path -LiteralPath $AssetsDir) {
    Push-Location $SourceDir
    try {
        foreach ($Asset in (Get-ChildItem -LiteralPath $AssetsDir -Recurse -File)) {
            $AssetPath = $Asset.FullName.Substring($SourceDir.Length + 1).Replace('\', '/')
            & $Aapt add $UnsignedApk $AssetPath
            if ($LASTEXITCODE -ne 0) { throw "aapt failed to add asset: $AssetPath" }
        }
    } finally {
        Pop-Location
    }
}

& $ZipAlign -f 4 $UnsignedApk $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed with exit code $LASTEXITCODE" }

$DebugKeyStore = Join-Path $SigningDir "debug.keystore"
if (-not (Test-Path -LiteralPath $DebugKeyStore)) {
    & $KeyTool -genkeypair -keystore $DebugKeyStore -storepass android -alias androiddebugkey -keypass android -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000
    if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }
}

& $ApkSigner sign --ks $DebugKeyStore --ks-pass pass:android --key-pass pass:android --out $OutputApk $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed with exit code $LASTEXITCODE" }

& $ApkSigner verify --verbose $OutputApk
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed with exit code $LASTEXITCODE" }

Write-Output $OutputApk
