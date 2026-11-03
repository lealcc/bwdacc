# Image background removal and outpainting

Android 0.11.3-dev adds two actions inside the image viewer's overflow menu.
Feed and gallery menus remain compact. Both tools create image revisions in the
same family, preserving the original item, project membership, Prompter policy,
private classification and stealth covers. They use the existing authenticated
connection, encrypted local media store and generation progress UI.

## Remove background

BiRefNet runs locally through ComfyUI's native `LoadBackgroundRemovalModel` and
`RemoveBackground` nodes. `InvertMask` adapts the foreground mask to the inverted
alpha convention of `JoinImageWithAlpha`; `SaveImage` writes an RGBA PNG at the
source resolution. Original files and thumbnails retain transparency. The media
viewer draws a checkerboard behind transparent pixels.
Existing transparency is intersected with the new foreground mask, so removing
the background again cannot make previously transparent pixels opaque.

The server needs `models/background_removal/birefnet.safetensors` and the native
nodes. Missing dependencies are reported before uploading. They were already
available on the development server; no installation or restart was needed.
See the [official BiRefNet guide](https://docs.comfy.org/tutorials/utility/remove-background-birefnet).

## Expand image

The sheet previews added canvas, provides independent left/top/right/bottom
controls, five aspect-ratio presets, and an optional description. The output may
be up to 16 MP and 8192 pixels per side. Empty or invalid expansions are rejected.

The workflow uses the included Klein image-edit model, or the selected private
Snofs Klein variant. It pads the canvas, encodes a working image, masks sampling
to the added region with a narrow overlap, and restores the original interior
after decoding. A feathered band of at most 64 source pixels blends the boundary;
the source file is never modified. Generated content can still have seams or
invent details and should be reviewed.
When the input already has transparency, hidden RGB is replaced with neutral
pixels before encoding and transparent areas are added to the generation mask.
The result fills that background and the expanded canvas without recovering
the hidden background from a previous cutout.

Working resolution is capped at approximately 1 MP and aligned to 16 pixels.
The output returns to the requested dimensions before the original interior is
composited back. This bounds sampling memory but means newly generated borders
on large canvases are enlarged from the working resolution. Existing model
memory-profile restrictions remain in force. These tools do not invoke the
prompt Agent. SFW filtering still runs at the final submission boundary.

Recipes retain the source, expansion dimensions, description, model variant,
memory profile and seed. Reuse navigates to the original reference and reopens
the appropriate tool. Image and video version families remain separate.

## Validation

The Android fixture checks graph links, model variants, size and memory limits,
mask polarity, original placement, edge blending and recipe classification.
Both sheets are exercised using bundled stock media, never personal media.
Live tests verify RGBA transparency, exact output dimensions, generated border
detail and unchanged source-interior pixels. Visual inspection also checks edge
continuity; mask blending was added after the first test exposed a hard seam.
