# Build and first generation

Run these commands from the repository root on Windows. None of the build
commands starts or stops an existing ComfyUI server.

## 1. Build Android

Install JDK 17 and the Android SDK (Android 36 platform, Build Tools 36 and
platform-tools). Set `JAVA_HOME` to the JDK directory and `ANDROID_SDK_ROOT`
or `ANDROID_HOME` to the SDK directory, for example:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_SDK_ROOT = "$env:LOCALAPPDATA\Android\Sdk"
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

The repository includes the six Android JARs listed in `libs/dependencies.json`;
the build checks each SHA-256. This project uses SDK tools directly, not Gradle.
Output: `build/bwdacc-commercial-debug.apk`. A new local debug signing key is
created in ignored `.debug/`. Keep that key private and retain it to sign updates.
This is a development APK; there is no production signing key in the repository.

Transfer the APK to your phone and install it, or use an authorized ADB device:

```powershell
& "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe" install -r .\build\bwdacc-commercial-debug.apk
```

The package is `com.leal.bwdacc.commercial`, launcher label **bwdacc dev**.
A differently signed installation with that same package ID cannot be updated
in place. Do not uninstall an existing app containing media just to bypass this;
export an encrypted backup first and preserve its recovery code.

## 2. Build Desktop

```powershell
powershell -ExecutionPolicy Bypass -File .\desktop\build.ps1 -Test
```

The script downloads a pinned .NET SDK when absent, verifies its hash, restores
locked NuGet packages and produces `desktop/artifacts/bwdacc-desktop.exe` plus
`SHA256SUMS.txt`. The executable is self-contained and unsigned. `-Test` runs
synthetic Desktop and connection checks; it does not download model weights.

Open the executable and follow the three setup stages: ComfyUI, phone pairing,
then optional Tailscale. See [the Desktop guide](../desktop/README.md).

### Separate managed installation

Choose an empty studio folder. Reserve at least 25 GB for the environment plus
the displayed model sizes. The preview targets Windows x64, NVIDIA compute
capability 7.5+, driver 580+ and the Microsoft Visual C++ x64 runtime. Python,
ComfyUI and supported core nodes are pinned and installed into that folder.
The managed server uses loopback port 8189.

Choose models explicitly, review their upstream terms and complete their
downloads. Optional tool packages are not all automatic; the catalog is not a
promise that a fresh core installation supports every tool immediately.

### Use your existing ComfyUI

Start your own ComfyUI normally, bound to loopback, and select the existing
installation option. Enter its local address, such as `http://127.0.0.1:8188`.
Desktop checks the service and reports missing nodes. It does not install into,
start, stop or upgrade that installation. Manage its models and nodes yourself.

For the bundled graph families, use the revisions and file destinations in
`app/src/main/assets/workflows/catalog.json`. The base catalog needs compatible
ComfyUI, KJNodes and rgthree versions. Additional operations list their own nodes
in `media-operations.json`. Install only the dependencies for the tools you use.

To enable bwdacc's own extension/media nodes in an existing installation, copy
`server/momentos_workflows` into `ComfyUI/custom_nodes/momentos_workflows`.
Inspect any existing folder before replacing it. These nodes need the ComfyUI
Python environment, NumPy, PyTorch, PyAV, and FFmpeg (on PATH or via
`imageio_ffmpeg`). Restart ComfyUI yourself after its queue is empty.

Optional reference extraction uses the resources in
`desktop/Bwdacc.Desktop/Payload/visual-models.json` and the pinned vendor packages
in `runtime.json`. The explicit `tools/install_existing_references.py` accepts
`--comfy`, `--models` and `--vendor` paths from a prepared managed environment;
it is not a general arbitrary-node installer. See [FRAGMENTS.md](../FRAGMENTS.md).
Lipsync has a separate isolated installer: [LIPSYNC.md](../LIPSYNC.md).

## 3. Pair the phone

Keep the phone and PC on the same private network. In Desktop's Devices page,
select the PC's private network address, activate the connector and create a
temporary pairing code/QR. Scan or enter it in Android and approve the request
on Desktop. Allow the connector through Windows Firewall for the intended
private network. The authenticated gateway uses port 8190; ComfyUI remains local.

For access away from home, install and sign in to Tailscale on both devices,
select the PC's Tailscale address in Desktop and pair for that endpoint.
Tailscale is optional on the same LAN. Do not forward ComfyUI's port on your
router or use a public plaintext URL.

## 4. Generate and edit

Select the GPU memory profile in Settings, then choose an installed workflow
from the bottom composer. Start with a small image or a short 0.33 MP video.
The app checks the live node/model inventory before submission. Open a result
to edit or animate it. Reuse restores the saved recipe and original references.

For an accessible first image, choose MOSS only if its weights, encoder and VAE
are present and the selected memory profile is available. The demo graphs are
also provided in `docs/demo/workflows/`; change input filenames to your own
uploaded files before submitting them through ComfyUI's `/prompt` API.

## Troubleshooting

| Symptom | Check |
|---|---|
| Build cannot find SDK/JDK tools | Environment variables, Android platform and Build Tools installation |
| Dependency hash mismatch | Restore the exact pinned file; do not disable verification |
| Phone cannot pair | Private address, firewall profile, same LAN/Tailscale network, Desktop approval |
| Missing node or model | The selected workflow's manifest and live ComfyUI inventory |
| Out of GPU memory | Lower resolution/duration, close other GPU applications, choose a supported profile |
| Certificate changed | Re-pair intentionally with the correct Desktop; do not disable pinning |

The Android app's local vault does not erase or encrypt files retained by ComfyUI
on your PC. See [VAULT.md](../VAULT.md) for the storage boundary.
