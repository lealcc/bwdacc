# bwdacc mobile demo

[Watch the video](bwdacc-mobile-demo.mp4) · [English captions](captions.en.srt) ·
[Narration script](SCRIPT_EN.md)

**1 minute 47 seconds**, 1920×1080, 30 fps, H.264/AAC. English synthetic narration
and burned-in English captions. The film emphasizes ease of use: a bottom
composer, touch controls, editing and swipeable versions, reusable Fragments,
Prompter experiments and Projects. It ends with the Android/Desktop/ComfyUI architecture.

SHA-256: `9469163bee338f8e6344b34a4ded9eb46a8923c70368e20a20aaa51ce22c1668`.

## What is recorded

The screens are actual compiled bwdacc Android UI running in an isolated demo
package with a synthetic library. Image/video jobs were run separately on local
ComfyUI with an RTX 3090 (24 GB). UI capture and model execution are not one
continuous end-to-end recording; the opening identifies the demo library.
The video contains no private gallery media, real-person likeness or pairing data.

| Showcase media | Operation |
|---|---|
| Ivory ceramic owl | MOSS Image SFT, 1024×768, 50 steps, CFG 3 |
| Cobalt blue edit | FLUX.2 Klein 4B, 4 steps, CFG 1 |
| Camera motion | MiniMax H3 Turbo image-to-video, approximately 0.33 MP |
| Reusable object | EfficientSAM3 selection from the generated owl |
| Composition | Florence-2-base-ft structured boxes |
| Depth / lineart | Local ComfyUI preprocessors |

The three generation graphs are in [workflows/](workflows/), in ComfyUI API
format. Input references use demo filenames; upload your own source and update
the relevant filename before running an edit or animation graph. The original
animation contains 107 frames at nominal 24 fps (about 4.46 seconds). Some UI
shots use a repeated playback asset; the film does not claim a new longer generation.

## Credits

- [MOSS Image SFT](https://huggingface.co/OpenMOSS-Team/MOSS-Image-SFT).
- [FLUX.2 Klein 4B](https://huggingface.co/black-forest-labs/FLUX.2-klein-4B).
- [MiniMax H3](https://huggingface.co/MiniMaxAI/MiniMax-H3), under its separate
  [model license](https://huggingface.co/MiniMaxAI/MiniMax-H3/blob/main/LICENSE).
- Local [Kokoro](https://huggingface.co/hexgrad/Kokoro-82M) narration, `af_heart`
  voice; original script, no imitation of the author's voice.
- Syne and Manrope typography, SIL OFL notices in the app assets.
- Original bwdacc mark and motion composition; no stock music or footage.

Model weights are not distributed with this demo. See the repository's
[third-party notices](../../THIRD_PARTY_NOTICES.md) for scope and limitations.
