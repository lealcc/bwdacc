# Third-party notices

The root Apache-2.0 license covers original bwdacc contributions. It does not
replace the licenses of bundled dependencies, model weights, external runtimes,
downloaded custom nodes or their dependencies.

## Included in this repository

| Component | License | Notice / source |
|---|---|---|
| Java-WebSocket 1.6.0 | MIT | `libs/Java-WebSocket-LICENSE.txt` |
| SLF4J API and nop 2.0.17 | MIT | `libs/SLF4J-LICENSE.txt` |
| ZXing core 3.5.4 | Apache-2.0 | `libs/ZXING-LICENSE.txt` |
| Tink Android 1.24.0 | Apache-2.0 | `libs/TINK-LICENSE.txt` |
| Gson 2.13.2 | Apache-2.0 | `libs/GSON-LICENSE.txt` |
| Syne | SIL OFL-1.1 | `app/src/main/assets/licenses/Syne-OFL.txt` |
| Manrope | SIL OFL-1.1 | `app/src/main/assets/licenses/Manrope-OFL.txt` |
| QRCoder 1.8.1 (restored at build time) | MIT | `desktop/Bwdacc.Desktop/Payload/QRCoder-LICENSE.txt` |

Android dependency URLs and SHA-256 hashes are in `libs/dependencies.json`.
Desktop dependencies are locked in `desktop/Bwdacc.Desktop/packages.lock.json`.
Fonts also appear in Android resources; the same font notices apply to both copies.
Library archives retain their internal metadata and notices.

## Workflow provenance

bwdacc adapts API graph structure, parameters and documented memory strategies
from the following sources. Source notices are retained in `licenses/`.
Adaptations include mobile inputs, output handling, memory profiles, recipes,
reference routing and application-specific media nodes.

- [Comfy Org workflow templates](https://github.com/Comfy-Org/workflow_templates),
  MIT, copyright 2023-present Comfy Org (`licenses/Comfy-Templates-MIT.txt`).
- [kasei-san MiniMax workflows](https://github.com/kasei-san/minimax-h3-comfyui-workflows),
  MIT (`licenses/kasei-san-MIT.txt`).
- [FlowForgeLabAi MiniMax 8 GB workflows](https://github.com/FlowForgeLabAi/MiniMax-H3-8GB-Workflows),
  MIT (`licenses/FlowForgeLabAi-MIT.txt`).

Further model documentation and node references are recorded in each catalog's
`sources` and `packages` fields and in [WORKFLOWS.md](WORKFLOWS.md).
They describe dependencies, not a transfer of ownership or a replacement license.

## Downloaded separately

ComfyUI, KJNodes, rgthree, EfficientSAM3, Florence, ControlNet preprocessors,
Open-H3 and other runtime/model packages are not bundled as source or weights
in this repository. Installation manifests pin their revisions and hashes.
Each installed package retains its own license; an app build does not grant
permission to redistribute or host every supported model.

The optional MuseTalk worker downloads the
[ComfyUI-FFMPEGA adapter](https://github.com/AEmotionStudio/ComfyUI-FFMPEGA)
under **GPL-3.0** and [MuseTalk](https://github.com/TMElyralab/MuseTalk) under
its upstream terms. Its installer retains the corresponding license texts.
The adapter is not included in this source snapshot. If distributing the
installed adapter or a combined runtime, comply with its GPL source and notice
obligations; the root Apache license does not relicense that adapter.

The [.NET runtime](https://github.com/dotnet/runtime) and NuGet packages have
their own distribution notices. This publication contains source, not a
prepackaged .NET/Comfy/Python runtime or signed installer.

## Model licenses

Weights must be obtained from their original repositories, with any required
access or license acceptance. Read the `license` and source fields in the model
catalogs. A `license` value of `other` or `unknown` is not permission to redistribute.

For example, [MiniMax H3's community license](https://huggingface.co/MiniMaxAI/MiniMax-H3/blob/main/LICENSE)
contains geographic and commercial conditions; see also
[Comfy's explanation](https://comfy.org/minimax/license/). Model permissions and
availability must be checked for the user's location and intended use.

## Original assets and demo

The bwdacc wordmark/vector mark and three built-in neutral covers are application
assets. The covers are synthetic images, not photos from a user's gallery.
The test video in `tests/android-vault/assets` is a synthetic fixture.
The public demo uses newly generated ceramic-owl media and synthetic narration;
its sources and limitations are documented in [docs/demo/README.md](docs/demo/README.md).
No personal gallery, voice recording, model weights or signing credentials are included.
