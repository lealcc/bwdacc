"""Local media utilities for Momentos workflows. No external generation API is used."""
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import uuid

import av
import numpy as np
import torch
import folder_paths
from comfy_api.latest import InputImpl


FPS = 24
CONTEXT = 22  # H3 multi-frame guide length: 17k + 5.


def executable():
    command = shutil.which("ffmpeg")
    if command:
        return command
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


def ffmpeg(arguments):
    result = subprocess.run([executable(), "-hide_banner", "-loglevel", "error", "-nostdin", "-y", *arguments],
                            capture_output=True, timeout=300)
    if result.returncode:
        raise ValueError("Falha ao preparar vídeo: " + result.stderr.decode("utf-8", errors="replace")[-700:])
    return result.stdout


def probe(path):
    with av.open(str(path)) as container:
        if not container.streams.video:
            raise ValueError("Arquivo sem vídeo.")
        stream = container.streams.video[0]
        duration = float(stream.duration * stream.time_base) if stream.duration is not None else (container.duration or 0) / 1_000_000
        return duration, stream.width, stream.height, bool(container.streams.audio)


def input_path(name):
    root = Path(folder_paths.get_input_directory()).resolve()
    path = (root / name.replace("\\", "/")).resolve()
    if not path.is_relative_to(root) or not path.is_file():
        raise ValueError("Vídeo de entrada indisponível.")
    return path


def workspace():
    path = Path(folder_paths.get_temp_directory()) / "momentos-extend"
    path.mkdir(parents=True, exist_ok=True)
    return path.resolve()


def validate_range(duration, start_ms, end_ms, crop_x, crop_y, crop_w, crop_h):
    start, end = start_ms / 1000, (end_ms / 1000 if end_ms > 0 else duration)
    if not all(math.isfinite(v) for v in (duration, start, end, crop_x, crop_y, crop_w, crop_h)):
        raise ValueError("Trecho inválido.")
    if start < 0 or end > duration + .1 or end - start < 1 or end - start > 120:
        raise ValueError("Selecione um trecho de 1 a 120 segundos dentro do vídeo.")
    if crop_x < 0 or crop_y < 0 or crop_w <= 0 or crop_h <= 0 or crop_x + crop_w > 1.00001 or crop_y + crop_h > 1.00001:
        raise ValueError("Recorte fora do vídeo.")
    return start, min(end, duration)


class MomentosLayoutElements:
    @classmethod
    def INPUT_TYPES(cls):
        return {"required": {"elements_json": ("STRING", {"multiline": True})}}
    RETURN_TYPES = ("ARRAY",)
    FUNCTION = "execute"
    CATEGORY = "Momentos/layout"

    def execute(self, elements_json):
        elements = json.loads(elements_json)
        if not isinstance(elements, list) or len(elements) > 12:
            raise ValueError("Use até 12 boxes.")
        for element in elements:
            box = element.get("bbox", [])
            if len(box) != 4 or not all(isinstance(v, (int, float)) and math.isfinite(v) and 0 <= v <= 1000 for v in box):
                raise ValueError("Coordenadas de box inválidas.")
            if box[0] >= box[2] or box[1] >= box[3]:
                raise ValueError("Box precisa ter área positiva.")
        return (elements,)


class MomentosVideoRange:
    @classmethod
    def INPUT_TYPES(cls):
        return {"required": {
            "video": ("STRING",), "start_ms": ("INT", {"default": 0, "min": 0}),
            "end_ms": ("INT", {"default": 0, "min": 0}),
            "width": ("INT", {"default": 768, "min": 64, "max": 4096, "step": 32}),
            "height": ("INT", {"default": 448, "min": 64, "max": 4096, "step": 32}),
            "crop_x": ("FLOAT", {"default": 0, "min": 0, "max": 1}),
            "crop_y": ("FLOAT", {"default": 0, "min": 0, "max": 1}),
            "crop_w": ("FLOAT", {"default": 1, "min": .01, "max": 1}),
            "crop_h": ("FLOAT", {"default": 1, "min": .01, "max": 1}),
        }}
    RETURN_TYPES = ("IMAGE", "AUDIO", "STRING")
    RETURN_NAMES = ("context_frames", "context_audio", "trimmed_source")
    FUNCTION = "execute"
    CATEGORY = "Momentos/video"

    def execute(self, video, start_ms, end_ms, width, height, crop_x=0, crop_y=0, crop_w=1, crop_h=1):
        source = input_path(video)
        duration, sw, sh, sound = probe(source)
        start, end = validate_range(duration, start_ms, end_ms, crop_x, crop_y, crop_w, crop_h)
        if width % 32 or height % 32 or width * height > 4_194_304:
            raise ValueError("Resolução da extensão inválida.")
        x, y = int(crop_x * sw) // 2 * 2, int(crop_y * sh) // 2 * 2
        w, h = max(2, int(crop_w * sw) // 2 * 2), max(2, int(crop_h * sh) // 2 * 2)
        trimmed = workspace() / (uuid.uuid4().hex + "-source.mp4")
        crop = f"crop={w}:{h}:{x}:{y}," if (crop_x, crop_y, crop_w, crop_h) != (0, 0, 1, 1) else ""
        args = ["-ss", str(start), "-i", str(source)]
        if not sound:
            args += ["-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo"]
        args += ["-map", "0:v:0", "-map", "0:a:0" if sound else "1:a:0", "-t", str(end-start),
                 "-vf", f"{crop}scale={width}:{height}:flags=lanczos,setsar=1,fps={FPS},setpts=PTS-STARTPTS",
                 "-af", "aresample=48000,apad", "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p",
                 "-c:a", "aac", "-ar", "48000", "-ac", "2", "-shortest", str(trimmed)]
        ffmpeg(args)
        actual, _, _, _ = probe(trimmed)
        tail = max(0, actual - CONTEXT / FPS)
        raw = ffmpeg(["-ss", str(tail), "-i", str(trimmed), "-an", "-frames:v", str(CONTEXT), "-f", "rawvideo", "-pix_fmt", "rgb24", "pipe:1"])
        frame_bytes = width * height * 3
        frames = len(raw) // frame_bytes
        if frames < CONTEXT:
            raise ValueError("O trecho não contém frames suficientes para continuar o movimento.")
        images = torch.from_numpy(np.frombuffer(raw[:CONTEXT*frame_bytes], np.uint8).copy().reshape(CONTEXT, height, width, 3)).float().div_(255)
        pcm = ffmpeg(["-ss", str(tail), "-i", str(trimmed), "-vn", "-t", str(CONTEXT/FPS), "-f", "f32le", "-acodec", "pcm_f32le", "-ar", "48000", "-ac", "2", "pipe:1"])
        waveform = torch.from_numpy(np.frombuffer(pcm, np.float32).copy().reshape(-1, 2).T).unsqueeze(0)
        info = json.dumps({"path": str(trimmed), "context": CONTEXT, "fps": FPS, "width": width, "height": height, "duration": actual})
        return images, {"waveform": waveform, "sample_rate": 48000}, info


class MomentosVideoExtensionMerge:
    @classmethod
    def INPUT_TYPES(cls):
        return {"required": {"video": ("VIDEO",), "trimmed_source": ("STRING",)}}
    RETURN_TYPES = ("VIDEO",)
    FUNCTION = "execute"
    CATEGORY = "Momentos/video"

    def execute(self, video, trimmed_source):
        info = json.loads(trimmed_source)
        source = Path(info["path"]).resolve()
        if source.parent != workspace() or not source.is_file():
            raise ValueError("Trecho original indisponível.")
        if info.get("context") != CONTEXT or info.get("fps") != FPS:
            raise ValueError("Contexto da extensão incompatível.")
        generated = workspace() / (uuid.uuid4().hex + "-generated.mp4")
        output = workspace() / (uuid.uuid4().hex + "-extended.mp4")
        try:
            video.save_to(str(generated))
            duration, _, _, sound = probe(generated)
            if duration <= CONTEXT / FPS:
                raise ValueError("A geração não contém uma continuação após os frames de contexto.")
            if not sound:
                raise ValueError("Extensão MiniMax sem faixa de áudio.")
            offset = CONTEXT / FPS
            filters = f"[0:v]setpts=PTS-STARTPTS[v0];[0:a]aresample=48000,aformat=channel_layouts=stereo,asetpts=PTS-STARTPTS[a0];[1:v]trim=start={offset},setpts=PTS-STARTPTS[v1];[1:a]atrim=start={offset},aresample=48000,aformat=channel_layouts=stereo,asetpts=PTS-STARTPTS[a1];[v0][a0][v1][a1]concat=n=2:v=1:a=1[v][a]"
            ffmpeg(["-i", str(source), "-i", str(generated), "-filter_complex", filters, "-map", "[v]", "-map", "[a]", "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p", "-c:a", "aac", "-movflags", "+faststart", str(output)])
        finally:
            generated.unlink(missing_ok=True)
        return (InputImpl.VideoFromFile(str(output)),)


NODE_CLASS_MAPPINGS = {cls.__name__: cls for cls in (MomentosLayoutElements, MomentosVideoRange, MomentosVideoExtensionMerge)}
NODE_DISPLAY_NAME_MAPPINGS = {"MomentosLayoutElements": "Momentos · elementos dos boxes", "MomentosVideoRange": "Momentos · trecho para extensão", "MomentosVideoExtensionMerge": "Momentos · unir continuação"}

from .upscale import NODES as UPSCALE_NODES
NODE_CLASS_MAPPINGS.update({cls.__name__: cls for cls in UPSCALE_NODES})

from .visual_extract import BwdaccVisualExtract
NODE_CLASS_MAPPINGS["BwdaccVisualExtract"] = BwdaccVisualExtract

from .reference_focus import BwdaccReferenceFocus
NODE_CLASS_MAPPINGS["BwdaccReferenceFocus"] = BwdaccReferenceFocus

from .lipsync import BwdaccMuseTalk
NODE_CLASS_MAPPINGS["BwdaccMuseTalk"] = BwdaccMuseTalk

from .lora_metadata import register as register_lora_metadata
register_lora_metadata()
