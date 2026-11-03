# Public snapshot validation

Checked on 2 November 2026. This is a publication readiness record, not an
independent security audit or a guarantee of all hardware/model combinations.

## Checks executed on this source snapshot

- **Android build passed** with JDK 17 and Android SDK tools. APK signature
  verification passed. A fresh local debug key was generated and is excluded
  from Git; no production or personal signing key is published.
- **Windows build passed**: self-contained `win-x64`, .NET 10, locked restore.
  The pinned SDK already present on the development PC was used; a new SDK
  bootstrap/download on a fresh Windows machine was not tested in this pass.
- **22 Python tests passed**: workflow catalog and hashes (5), lipsync boundaries
  and explicit installer behavior (5), visual extraction contracts (7), prompt
  guard contracts (5). They used an existing Python environment and synthetic
  fixtures, without submitting model inference jobs.
- **58 Desktop checks passed**: external Comfy connection (10), core installer
  and runtime boundaries (16), pairing (10), historical carrier tests (7),
  persistent connection tests (8), private network tests (7). These use synthetic
  servers and temporary credentials. Cross-device Tailscale was not retested.
- **34 graph hash references verified against staged Git bytes**, including
  memory profiles and optional media-operation references. Text normalization
  did not change the pinned graph hashes.
- Known credential patterns and personal workstation addresses were searched
  in staged text files; no matches were found. This is a bounded check, not proof
  that no possible secret could exist.
- Bundled binary files were inventoried: dependency JARs, fonts, neutral covers,
  synthetic test media, demo screenshots and the demo video. Build output,
  model weights, runtime folders, signing keys, pairing data and private history
  are excluded.

The app's Java/Python/C# implementation and workflow graphs are unchanged from
the development snapshot. Public packaging adds license notices and setup/docs,
and replaces the personal LoRA inventory with entries from the published model
catalog. Unknown adapters retain the existing classification policy.

## Repeat the bounded checks

From the repository root, with Python and the dependencies used by each test:

```powershell
python -m unittest discover -s tests -p test_workflow_catalog.py -v
python -m unittest discover -s tests -p test_lipsync.py -v
python -m unittest discover -s tests -p test_visual_extract.py -v
python -m unittest discover -s tests -p test_prompt_guard.py -v
powershell -ExecutionPolicy Bypass -File .\build.ps1
powershell -ExecutionPolicy Bypass -File .\desktop\build.ps1 -Test
```

Visual contract tests require NumPy and Pillow. Tests that exercise real ComfyUI
nodes need its Python environment and imports. Android emulator suites live in
`tests/android-*`; their scripts require a disposable emulator and must not be
pointed at a personal phone. Live generation tests are separate opt-in scripts.

## Earlier functional evidence and remaining limits

The demo's original image, image edit, animation, composition and object mask
were produced on local ComfyUI with an RTX 3090 (24 GB). The UI footage is from
the compiled Android app with a synthetic library. See [demo provenance](demo/README.md).

This publication pass does not repeat every generation, model download,
mobile UI scenario or complete installation on a clean PC. It does not establish
8/16 GB performance, worldwide model-license availability, production signing,
hosted-service readiness or an independent security assessment.
