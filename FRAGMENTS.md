# Fragmentos

Android 0.9.0 adds Fragmentos to the Feed/Gallery/Prompter/Projects dropdown.
This is a full management destination, with search and categories for styles,
characters, places, objects, compositions and control blocks. Opening a fragment
here edits it; **Usar no prompt** explicitly returns to the previous workspace.
The prompt composer is hidden in this area and its previous draft is preserved.
The prompt's quick picker remains available under its new Fragmentos label.
Existing IDs, tags, permissions, jobs and reference images are retained.

## Control blocks

A block can hold one map of each type: depth, pose, lineart, Canny, soft edge,
normals, scribble and semantic segmentation. Users can import completed maps
from the Android photo gallery or app library, or extract them from a source
image using the connected ComfyUI. Each layer can be replaced, removed or used
as the block's cover. Blocks can also be duplicated and renamed.

Extraction queries the live node schema before uploading any image. It selects
an installed preprocessor with an IMAGE output, preserves its declared defaults,
and uses a 512-pixel processing resolution where supported. Depth Anything V2
prefers its Small checkpoint when offered. LoadImage, the selected preprocessor
and PreviewImage form an auxiliary job. Completed results are fetched through
the existing authenticated transport; no separate public vision API is used.
Missing nodes produce an actionable error before upload. Node installation and
checkpoint downloads are not provisioned by this Android change. Some third-party
preprocessors may download their own checkpoints on first execution.

Imported and generated reference images are saved as PNG through VaultFiles.
Block JSON refers only to validated files in the existing reference library,
so backup, cleanup and encryption keep their current path and key boundaries.
Sources and maps inherit private status. Private fragments are hidden while
private mode is off; private maps retain stealth covers, hold-to-reveal and
screen-capture protection in the editor. Shared files survive removal of a layer
that is still used by another layer or fragment.

Extraction submits once. An uncertain POST, missing preprocessor or failed
execution does not retry generation automatically. Closing the extraction editor
stops client polling; it does not interrupt unrelated ComfyUI jobs. An unsaved
block is a draft, and its unused files follow existing library cleanup rules.

## Model integration remains separate

These typed maps are not passed as ordinary image references, prompt text or
LoRAs. The block editor states that generation use is not enabled yet. The next
phase should evaluate each existing model/workflow against each map type, record
required conditioning nodes and weights, test influence and composition with
other references, and publish availability only for validated combinations.
If the current catalog cannot support a type reliably, evaluate an additional
model and its license, memory profiles and download footprint before adding it.

Preprocessor schema sources:

- [ComfyUI ControlNet auxiliary preprocessors](https://github.com/Fannovel16/comfyui_controlnet_aux)
- [Depth Anything V2 wrapper](https://github.com/Fannovel16/comfyui_controlnet_aux/blob/main/node_wrappers/depth_anything_v2.py)
- [DWPose wrapper](https://github.com/Fannovel16/comfyui_controlnet_aux/blob/main/node_wrappers/dwpose.py)
- [Lineart wrapper](https://github.com/Fannovel16/comfyui_controlnet_aux/blob/main/node_wrappers/lineart.py)

Validation uses the disposable `com.leal.bwdacc.visualtest` emulator package,
synthetic images and a fake queue API. It covers encryption, legacy data,
multi-map persistence, shared-file retention, privacy inheritance, traversal
rejection, exclusion from generation conditioning, schema-based graph creation,
result download, missing-node handling and no retry after uncertain submission.
It does not validate inference quality or compatibility with generation models.
On 2026-11-02, 29 emulator checks passed. The real MainActivity navigation was
also exercised inside the disposable package: the dropdown contains Fragmentos,
the block list opens correctly and the generation composer stays hidden.
The new copy is included in all seven supported UI languages.
