"""Aspect-preserving video input/output for the Open-H3 refinement workflow."""
import math
import uuid
import numpy as np
import torch
import av
import comfy.nested_tensor
from comfy_api.latest import InputImpl


def frame_counts(duration):
    if not math.isfinite(duration) or not .2 <= duration <= 15.1:
        raise ValueError("Upscale H3 aceita clipes de até 15 segundos; corte o vídeo antes.")
    visible = max(5, round(duration * 24))
    return visible, visible + (5 - visible) % 17


class MomentosUpscaleSource:
    @classmethod
    def INPUT_TYPES(cls):
        return {"required": {"video": ("STRING",), "width": ("INT", {"default": 768, "min": 64, "max": 2048}),
                             "height": ("INT", {"default": 432, "min": 64, "max": 2048})}}
    RETURN_TYPES = ("IMAGE", "INT")
    FUNCTION = "execute"
    CATEGORY = "Momentos/upscale"

    def execute(self, video, width, height):
        from . import input_path, probe, ffmpeg
        source = input_path(video)
        visible, padded = frame_counts(probe(source)[0])
        if width % 16 or height % 16 or width * height > 1_500_000:
            raise ValueError("Resolução de entrada do upscale inválida.")
        raw = ffmpeg(["-i", str(source), "-an", "-vf", f"fps=24,scale={width}:{height}:flags=lanczos,setsar=1",
                      "-frames:v", str(visible), "-f", "rawvideo", "-pix_fmt", "rgb24", "pipe:1"])
        count = len(raw) // (width * height * 3)
        if count < 1:
            raise ValueError("Vídeo não contém frames.")
        images = torch.from_numpy(np.frombuffer(raw, np.uint8).copy().reshape(count, height, width, 3)).float().div_(255)
        if count < padded:
            images = torch.cat((images, images[-1:].expand(padded - count, -1, -1, -1)))
        return images[:padded], min(count, visible)


class MomentosUpscaleAV:
    @classmethod
    def INPUT_TYPES(cls):
        return {"required": {"latent": ("LATENT",), "audio_vae": ("VAE",)}}
    RETURN_TYPES = ("LATENT",)
    FUNCTION = "execute"
    CATEGORY = "Momentos/upscale"

    def execute(self, latent, audio_vae):
        # Native H3 runs joint AV sampling. Silence is encoded, then frozen with an
        # audio-zero noise mask. The original soundtrack is muxed after refinement.
        video = latent["samples"]
        if video.ndim != 5 or video.shape[1] != 24:
            raise ValueError("Upscale requer latent de vídeo MiniMax H3.")
        # H3 temporal compression is piecewise: 5 initial frames, then 17 per group.
        frames = 5 + max(0, (video.shape[2] - 2) // 5) * 17
        rate = int(getattr(audio_vae, "audio_sample_rate", 32000))
        sound = audio_vae.encode(torch.zeros(1, round(frames / 24 * rate), 2)).to(video.device, torch.float32)
        length = round(frames / 24 * 40)
        if sound.shape[-1] < length:
            sound = torch.cat((sound, sound[..., -1:].expand(*sound.shape[:-1], length - sound.shape[-1])), -1)
        sound = sound[..., :length].contiguous()
        nested = comfy.nested_tensor.NestedTensor
        return ({"samples": nested((video, sound)), "noise_mask": nested((torch.ones_like(video), torch.zeros_like(sound)))},)


class MomentosUpscaleFinish:
    @classmethod
    def INPUT_TYPES(cls):
        return {"required": {"images": ("IMAGE",), "video": ("STRING",), "frame_count": ("INT", {"default": 120}),
                             "width": ("INT", {"default": 1920}), "height": ("INT", {"default": 1080})}}
    RETURN_TYPES = ("VIDEO",)
    FUNCTION = "execute"
    CATEGORY = "Momentos/upscale"

    def execute(self, images, video, frame_count, width, height):
        from . import input_path, workspace, ffmpeg
        source = input_path(video)
        if min(width, height) < 64 or max(width, height) > 2560 or width * height > 6_600_000 or width % 2 or height % 2:
            raise ValueError("Resolução final do upscale inválida.")
        if frame_count < 1 or frame_count > images.shape[0]:
            raise ValueError("Frames insuficientes para preservar duração.")
        draft = workspace() / (uuid.uuid4().hex + "-upscale-silent.mp4")
        output = workspace() / (uuid.uuid4().hex + "-upscale.mp4")
        try:
            with av.open(str(draft), "w") as container:
                stream = container.add_stream("libx264", rate=24)
                stream.width, stream.height = images.shape[2], images.shape[1]
                stream.pix_fmt = "yuv420p"
                stream.options = {"crf": "16", "preset": "veryfast"}
                for i in range(frame_count):
                    frame = av.VideoFrame.from_ndarray((images[i].clamp(0, 1).cpu().numpy() * 255).round().astype(np.uint8), format="rgb24")
                    for packet in stream.encode(frame): container.mux(packet)
                for packet in stream.encode(): container.mux(packet)
            ffmpeg(["-i", str(draft), "-i", str(source), "-map", "0:v:0", "-map", "1:a:0?",
                    "-vf", f"scale={width}:{height}:flags=lanczos,setsar=1", "-t", str(frame_count / 24),
                    "-c:v", "libx264", "-crf", "18", "-preset", "veryfast", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-movflags", "+faststart", str(output)])
        finally:
            draft.unlink(missing_ok=True)
        return (InputImpl.VideoFromFile(str(output)),)


NODES = (MomentosUpscaleSource, MomentosUpscaleAV, MomentosUpscaleFinish)
