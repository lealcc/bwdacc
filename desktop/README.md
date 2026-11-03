# bwdacc Desktop preview

Native Windows manager and per-user installer for the commercial fork. It uses
WPF/.NET and a managed native Python environment, not Docker. This preview is
unsigned and is not a production security release.

## Use

Run `artifacts/bwdacc-desktop.exe`, choose an empty studio folder, and select
**Preparar meu PC**. This installs the manager into the current user's Programs
directory, creates a Start-menu shortcut, registers an uninstaller, and prepares
an independent generation runtime. **Ambiente > Instalar bwdacc Desktop** installs
only the manager without downloading the generation runtime.

The initial supported configuration is Windows x64 with an NVIDIA GPU providing
at least 8 GB VRAM, CUDA compute capability 7.5 or newer, and driver 580 or newer.
The Microsoft Visual C++ x64 runtime must already be installed. Updating drivers
or installing missing OS prerequisites may require administrator permission;
the manager and its normal runtime installation are per-user.

Reserve at least 25 GB before preparing the motor, plus the selected model sizes.
Initial setup downloads Python, PyTorch, and dependencies. Models are separate
explicit downloads; the catalog displays package size before confirmation.
Availability by VRAM follows the bundled workflow catalog. Local validation was
performed on RTX 3090; other supported configurations still need pilot testing.

## Implemented

- GPU/driver detection and compatibility preflight.
- Selection of a dedicated, marker-owned root. Nonempty unrelated folders are
  rejected. Existing personal ComfyUI installations are not adopted or modified.
- Pinned ComfyUI, KJNodes, rgthree, uv, and Python versions. Downloaded repository
  archives and uv are verified using bundled SHA-256 values. Python is managed by
  the pinned uv release. Python requirements are pinned and installed with hashes,
  binary wheels only, and explicit PyPI/PyTorch indexes.
- Safe archive extraction with traversal, reserved-name, symlink, collision, and
  expansion-limit checks. No arbitrary custom-node install scripts are run.
- New runtime directories for repairs/updates. GPU allocation, real ComfyUI
  startup, and required node schemas are checked before replacing the active
  pointer. The prior runtime remains available for rollback.
- Model download, cancellation, partial resume, full SHA-256 verification, disk
  checks, and preservation of existing mismatched files.
- LoRA import by family, bounded safetensors header/offset checks, list, and a
  recoverable local trash directory. Header checks do not prove model quality or
  semantic compatibility. This preview does not load existing personal LoRAs.
- Start/stop of owned ComfyUI on `127.0.0.1:8189`. Foreign port listeners are not
  adopted or terminated. A Windows Job Object owns the runtime process tree;
  children terminate if the manager crashes. Stop refuses a nonempty queue.
- A self-contained executable, per-user installation, Start-menu shortcut, and
  uninstaller. Uninstall preserves the studio runtime/models/media deliberately.
- Dark UI, Syne/Manrope fonts, purple accents, and reduced-motion-aware page fades.

## Important boundaries

The **Dispositivos** page now supports [local mobile pairing](../CONNECTION.md):
temporary QR, Desktop approval, authenticated HTTPS/WSS and device revocation.
The gateway binds to the selected LAN or assigned Tailscale IPv4 plus loopback;
ComfyUI stays on loopback. [LAN + Tailscale](../TAILSCALE.md) is the commercial
connection path. Activation persists and resumes on Desktop startup; an absent
interface waits without switching networks. Explicit deactivation persists.
Each customer uses their own Tailscale account/network. No domain or Cloudflare
token is required. Old public profiles are not loaded or automatically started.
Public-carrier code remains accessible only through explicit historical test CLIs.
The new [mobile vault preview](../VAULT.md) protects app-local storage only.
Guided Tailscale installation, managed cloud, subscriptions, server media cleanup and
release signing remain pending. Do not publicly expose the ComfyUI listener.
Loopback binding does not authenticate other processes/users on the same PC.

Media produced by this preview can remain as plaintext inside its `media`
directory. Use synthetic test media. Manager diagnostics discard ComfyUI output
because runtime messages can contain private prompts; this does not guarantee
that ComfyUI or custom nodes never keep their own histories or caches.

Runtime installation is not an OS security sandbox. The manager runs pinned
third-party Python code with the current user's permissions. The dependency locks
improve reproducibility and integrity; they are not a complete supply-chain audit.

Only the core workflow catalog is exposed for automatic model installation in
this preview. Gated models cannot be downloaded automatically. Optional external
LLM services and specialized upscale node packages are not provisioned yet.
Licenses for commercial redistribution/hosting remain a release requirement.

## Build and verification

```powershell
powershell -ExecutionPolicy Bypass -File .\desktop\build.ps1 -Test
```

The build script bootstraps the pinned .NET SDK into `desktop/.tools` with SHA-512
verification if needed. It does not modify the machine's global SDK installation.
Output is `desktop/artifacts/bwdacc-desktop.exe` plus `SHA256SUMS.txt`.

The built-in test runner uses synthetic files and fake HTTP responses. It covers
path containment, archive traversal and symlinks, download verification/resume,
cancellation, rollback, LoRA import containment, owned-process crash cleanup, and
TCP listener ownership. Test artifacts stay in the ignored artifacts directory.
The same `-Test` also runs ten TLS gateway, seven carrier, eight persistent-remote
regression checks, and private-connection tests (seven on a PC with a private adapter).
Remote Android and real-runtime Internet validation are described in REMOTE.md.

For a real installation test, use a new dedicated folder with sufficient disk
space. This command downloads dependencies and briefly starts/stops the isolated
ComfyUI instance, without downloading model weights or submitting generations:

```powershell
.\desktop\artifacts\bwdacc-desktop.exe --smoke-install C:\bwdacc-test C:\bwdacc-install-result.json
```

The smoke harness also checks immediate restart and node schemas. Do not run it
against an existing personal environment. The test uses port 8189 and must not
overlap another commercial instance. On failure it leaves diagnostic information
beside the requested report; a successfully validated prior active runtime is preserved.

`--render <png-path> [overview|models|loras|settings|devices|setup|setup-existing|setup-phone|setup-tailscale]` renders real WPF controls
off-screen with synthetic hardware data. It does not alter user settings.

### Desktop 0.6: setup and existing ComfyUI

First-run setup has three stages: ComfyUI and model choices, mobile pairing on
the local network, then optional Tailscale. Users can skip Tailscale or resume
setup later using **Configurar meu estúdio**. Existing installations and paired
devices retain their current configuration when upgrading.

Stage one offers a separate managed installation (including the model catalog)
or an already running local ComfyUI. The latter accepts an HTTP loopback address,
such as `http://127.0.0.1:8188`, and supports other local ports except the
connector's reserved 8190. It queries `system_stats`, `object_info` and `queue`;
the inventory lists model/LoRA choices exposed by nodes, never input media names.
It reports missing workflow nodes without claiming that weights, memory or all
features are ready. Model and LoRA management remain in the user's installation.
No external files, Python packages or nodes are installed, updated or removed.
The user starts and stops that ComfyUI using their existing launcher. API login,
HTTPS upstreams, IPv6-only services and remote upstream machines are not supported
by this initial external mode.

The phone still uses the existing pinned HTTPS/WSS gateway and scoped pairing
credentials. Only the local upstream port changes. External readiness is tied
to the inspected listener PID and process start time; a replacement process
requires **Verificar conexão**. Switching sources refuses a running managed
engine or queued external jobs, and closes gateway sessions before changing
the upstream. Closing Desktop never stops an external ComfyUI. The connector
does not change an external server's own bind address or access permissions;
the user remains responsible for its configuration and output storage.

Tailscale is optional and uses the user's own account. Changing from a LAN
address to a Tailscale address requires selecting that address in Devices and
pairing for the new endpoint. No automatic VPN installation, login, public
exposure or fallback between interfaces occurs.

`--test-external <report>` adds ten checks covering local endpoint restrictions,
legacy settings, read-only discovery, queue state, malformed/redirect responses,
authenticated proxy and WSS on a custom port, backend lifecycle and persistence.
It uses a synthetic local backend and isolated identity. `build.ps1 -Test`
includes these tests. `--inspect-comfy <local-url> <report>` performs read-only
inspection of a running ComfyUI and records only version and resource counts.

Validation on 2026-11-02: all 58 built-in checks passed, including ten external
backend cases. Read-only inspection of the running commercial ComfyUI 0.41.0
returned 959 nodes, one model choice and no LoRAs; no generation or file mutation
was submitted by this inspection. Both source-choice layouts and the phone and
Tailscale stages were rendered and inspected. Version 0.6.0 was installed on the
pilot PC and its actual window rendered correctly. The preview installer still
uses a different immutable executable path per release; Windows can request
firewall permission again after an update. That permission must be granted by
the user before completing cross-device validation.

Desktop 0.5.2 starts WPF with process-local software rendering. This avoids the
manager's hardware composition path, which left the real window white on the
pilot PC even though its controls and off-screen preview loaded. The separate
ComfyUI process continues to use CUDA. No Windows graphics registry setting or
GPU driver setting is changed. The version badge and Windows uninstall entry
now both use the built assembly version.

Validation included opening the installed 0.5.2 window on the pilot PC, visually
checking its real contents, navigating to Devices, and starting the actual
managed engine. An off-screen preview alone does not validate this failure.
See [WPF process rendering mode](https://learn.microsoft.com/en-us/dotnet/api/system.windows.media.renderoptions.processrendermode).

`--smoke-setup <report-path>` exercises real per-user installation, shortcut and
uninstall registration, removal, and reinstallation. It refuses to run if any
Desktop Preview installation or shortcut already exists. It leaves the manager
installed and does not install or remove runtime/model/media directories.

`--install-manager <report-path>` installs or updates only the per-user executable
and shortcut. It requires other Desktop instances to be closed and preserves
runtime, pairing identity, and media.

Validation on 2026-10-30: all 15 built-in checks passed; an independent runtime
installed on an RTX 3090, passed GPU allocation and required node-schema checks,
and completed start/stop/immediate restart. Per-user setup, shortcut target,
registration, removal, and reinstallation passed. All four WPF pages rendered
and were visually inspected. No model-weight download or generation was part of
these tests. The existing personal server and source tree were preserved.

## Sources and notices

Desktop 0.5.1 includes **Compositor e seleção visual** with Florence-2-base-ft
for described boxes and EfficientSAM3 for point selections. Downloads go into the
registered `models/vision` and `models/segmentation` directories. The
`BwdaccVisualExtract` API remains compatible with Android 0.8. See [Visual references](../VISUAL_REFERENCES.md) for
installation, model provenance, privacy behavior, and isolated inference tests.

The runtime manifest records immutable repository revisions, download URLs, and
checksums. Model provenance is inherited from the app workflow catalog. The
Python lock is generated from the pinned repositories' requirements and explicit
PyTorch pins in `requirements.in`.

- [ComfyUI source](https://github.com/Comfy-Org/ComfyUI)
- [uv Python management](https://docs.astral.sh/uv/guides/install-python/)
- [.NET single-file deployment](https://learn.microsoft.com/en-us/dotnet/core/deploying/single-file/overview)

Fonts and inherited library notices are embedded from the app's licenses folder.
Downloaded source archives retain their upstream license files. Final public
packaging and licensing review remain pending.

Desktop 0.7.0 adopts the mobile typography, rounded controls and neutral surfaces.
Existing connection settings and paired devices are preserved. See [audio and UI update](../AUDIO_FRAGMENTS.md).
