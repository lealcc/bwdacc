# Local MuseTalk 1.5 lipsync

The commercial Android app exposes **Lipsync** in the image/video viewer's overflow
menu. Select an audio file and a maximum output side (512, 768, 1024 or 1280 px).
An image creates a new video set; a video creates a version in its existing video
set. The original media is retained. Reusing the result restores the original
source, selected audio, size and seed. Audio is independently owned by each result
inside the authenticated mobile vault; deleting the result also deletes that audio.
Encrypted backup/restore includes it. Abandoned picker drafts expire after 24 hours.

The input must contain one visible, front-facing face. The implementation uses
the first detected face and rejects clips with missing face detections. Audio is
limited to 0.2–30 seconds and 50 MB on Android. For video, audio cannot exceed the
source duration. Output duration follows the audio, replaces the original sound,
and uses H.264/AAC at 25 fps. Resizing preserves aspect ratio, rounds to even pixel
dimensions and does not upscale. There is no speech synthesis or cloud generation.
The app picker accepts formats Android can validate; FFmpeg must also decode them
on the connected computer.

## Optional isolated runtime

`server/musetalk/install.py` downloads 2,189,732,666 bytes of pinned public resources,
checks their SHA-256 hashes, and prepares a separate Python environment with a
hash-locked dependency list. PyTorch/dependencies require additional disk space.
Inference enforces offline Hugging Face loading. The package is stored under
`<Studio>/models/bwdacc_musetalk`, separate from ComfyUI's Python environment.
The installer requires an existing managed commercial Studio root; it does not
restart ComfyUI or modify another installation's dependencies.

```powershell
python server/musetalk/install.py --studio 'C:\path\to\bwdacc Studio'
```

Managed runtime release `2026.11.02.3` includes `BwdaccMuseTalk`. To attach the same
installed package to an explicitly selected existing ComfyUI:

```powershell
python server/musetalk/connect_existing.py --comfy 'E:\ComfyUI' --package 'C:\path\to\bwdacc Studio\models\bwdacc_musetalk'
```

The connector writes only its owned `custom_nodes/bwdacc_musetalk` folder and reads
the shared package through an explicit local path. It refuses to overwrite an
unowned node directory. Restart that ComfyUI when its queue is empty. Removing the
optional node does not affect other custom nodes or installed models. No public
listener, gateway route, trust exception or new mobile permission is introduced.
This setup is a developer installer; there is no new Desktop download button yet.

The workflow uses `BwdaccMuseTalk` followed by ComfyUI `SaveVideo`. The node confines
inputs to ComfyUI's input folder, validates durations before inference, normalizes
media, and invokes the isolated worker with a 15-minute timeout. Comfy cancellation
terminates the worker. Job scratch folders are removed in `finally`; normal Comfy
input/output files retain the server's existing storage behavior. Mobile vault
encryption does not imply encryption of files on that computer.

## Implementation and attribution

Model: [TMElyralab/MuseTalk 1.5](https://github.com/TMElyralab/MuseTalk).
The optional worker uses the MuseTalk adapter from
[ComfyUI-FFMPEGA](https://github.com/AEmotionStudio/ComfyUI-FFMPEGA), pinned to
`da3f2372361ead3d0a82d215e8b9d43e7e5362b2`, with FP16 safetensors from
[AEmotionStudio/musetalk-models](https://huggingface.co/AEmotionStudio/musetalk-models).
It loads only the required modules, not FFMPEGA's agent/plugin entry points.
This adapter uses MediaPipe landmarks and a feathered mouth mask instead of the
upstream MMPose/BiSeNet stack; it is not the unmodified reference implementation.
The downloaded package retains the FFMPEGA GPL-3.0 and MuseTalk MIT license texts.
Redistribution review remains part of the commercial release work.

## Validation on this machine

- Five Python boundary/installer tests passed.
- 55 Android fixture checks passed, covering tutorial packs/lifecycle, graph
  construction, encrypted audio, independent ownership, backup and deletion.
- Android `0.10.0-dev` built, signed and installed as `com.leal.bwdacc.commercial`.
- Managed runtime node schemas and immediate stop/start passed.
- After the user's explicit selection, the optional node was attached to the
  existing ComfyUI on port 8188 and loaded through Manager restart with an empty queue.
- Real Comfy image+audio and video+audio jobs passed on the RTX 3090, using a public
  scikit-image astronaut sample and synthetic Windows speech. Outputs decoded
  completely, contained audio and preserved 512×512 dimensions at 25 fps.
  Image: 119 frames / 4.76 s, 38.28 s processing. Video: 118 frames / 4.72 s,
  44.28 s processing. These timings are measurements, not performance guarantees.
- Help library, introduction and lipsync sheet were visually inspected on the
  API 36 emulator. Other GPUs/VRAM tiers and maximum-duration jobs were not benchmarked.

The opt-in live check is `tests/lipsync_live.py`; it submits only supplied synthetic
fixtures, reads only its own prompt IDs and never interrupts unrelated jobs.
