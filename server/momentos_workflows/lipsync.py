"""Bounded, local MuseTalk jobs. External media is accepted only from Comfy input."""
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time


def duration(path, audio=False):
    import av
    with av.open(str(path)) as container:
        streams = container.streams.audio if audio else container.streams.video
        if not streams:
            raise ValueError("Arquivo sem áudio." if audio else "Arquivo sem vídeo.")
        stream = streams[0]
        seconds = float(stream.duration * stream.time_base) if stream.duration is not None else (container.duration or 0) / 1_000_000
        if not math.isfinite(seconds) or seconds <= 0:
            raise ValueError("Duração inválida.")
        return seconds


def validate(kind, seconds, video_seconds=None):
    if kind not in ("image", "video") or not math.isfinite(seconds) or not .2 <= seconds <= 30:
        raise ValueError("MuseTalk aceita áudio de 0,2 a 30 segundos.")
    if kind == "video" and (video_seconds is None or seconds > video_seconds + .08):
        raise ValueError("O áudio precisa caber no vídeo. Envie um áudio mais curto.")


def runtime(models_dir):
    root = (Path(models_dir) / "bwdacc_musetalk").resolve()
    marker = root / "ready.json"
    python = root / "environment" / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
    if not marker.is_file() or not python.is_file() or not (root / "runner.py").is_file():
        raise ValueError("MuseTalk 1.5 não está instalado. Prepare o pacote local no computador.")
    data = json.loads(marker.read_text(encoding="utf-8"))
    if data.get("version") != 1:
        raise ValueError("Pacote MuseTalk incompatível. Atualize o ambiente local.")
    return root, python


class BwdaccMuseTalk:
    @classmethod
    def INPUT_TYPES(cls):
        return {"required": {"source": ("STRING",), "audio": ("STRING",),
            "kind": (["image", "video"],), "batch_size": ("INT", {"default": 2, "min": 1, "max": 4}),
            "max_side": ("INT", {"default": 1024, "min": 512, "max": 1280}),
            "seed": ("INT", {"default": 42, "min": 0, "max": 2147483647})}}
    RETURN_TYPES = ("VIDEO",)
    FUNCTION = "execute"
    CATEGORY = "bwdacc/video"

    def execute(self, source, audio, kind, batch_size=2, max_side=1024, seed=42):
        import folder_paths
        import comfy.model_management as memory
        from comfy_api.latest import InputImpl
        from . import input_path, ffmpeg
        roots = [Path(folder_paths.models_dir)]
        for category in ("bwdacc_musetalk", "vision", "checkpoints"):
            if category in folder_paths.folder_names_and_paths:
                roots.extend(Path(path).parent for path in folder_paths.get_folder_paths(category))
        package, python = runtime(next((root for root in roots if (root / "bwdacc_musetalk/ready.json").is_file()), roots[0]))
        source_path, audio_path = input_path(source), input_path(audio)
        seconds = duration(audio_path, True)
        validate(kind, seconds, duration(source_path) if kind == "video" else None)
        if not 1 <= batch_size <= 4 or not 512 <= max_side <= 1280:
            raise ValueError("Configuração MuseTalk inválida.")
        scratch = Path(folder_paths.get_temp_directory()).resolve() / "bwdacc-lipsync"
        scratch.mkdir(parents=True, exist_ok=True)
        work = Path(tempfile.mkdtemp(prefix="job-", dir=scratch)).resolve()
        result_path = None
        try:
            wav = work / "audio.wav"
            ffmpeg(["-i", str(audio_path), "-vn", "-t", str(seconds), "-ar", "16000", "-ac", "1", str(wav)])
            # Uniform 25 fps aligns Whisper's temporal windows. Bound decoded RAM usage.
            prepared = work / ("source.png" if kind == "image" else "source.mp4")
            scale = f"scale=w='min({max_side},iw)':h='min({max_side},ih)':force_original_aspect_ratio=decrease:force_divisible_by=2,setsar=1"
            options = ["-i", str(source_path), "-an", "-vf", scale + (",fps=25" if kind == "video" else "")]
            options += ["-frames:v", "1"] if kind == "image" else ["-t", str(seconds), "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "18"]
            ffmpeg(options + [str(prepared)])
            job = work / "job.json"
            job.write_text(json.dumps({"source": str(prepared), "audio": str(wav), "output": str(work),
                                       "result": str(work / "result.json"), "batch": batch_size, "seed": seed}), encoding="utf-8")
            memory.unload_all_models()
            memory.soft_empty_cache()
            environment = os.environ.copy()
            environment.update(HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1", PYTHONNOUSERSITE="1")
            with (work / "worker.log").open("wb") as log:
                process = subprocess.Popen([str(python), "-I", str(package / "runner.py"), str(job)],
                    stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, env=environment,
                    creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
                deadline = time.monotonic() + 900
                try:
                    while process.poll() is None:
                        memory.throw_exception_if_processing_interrupted()
                        if time.monotonic() > deadline:
                            raise TimeoutError("MuseTalk excedeu 15 minutos. Tente um áudio mais curto.")
                        time.sleep(.25)
                finally:
                    if process.poll() is None:
                        process.kill()
                    process.wait(timeout=15)
            if process.returncode:
                # Do not return filesystem paths, job data, or unrestricted worker logs to the phone.
                message = (work / "worker.log").read_text(encoding="utf-8", errors="replace")
                if "Rosto não encontrado" in message or "Nenhum rosto visível" in message:
                    raise ValueError("MuseTalk não detectou um rosto em todo o trecho. Use um rosto frontal e visível.")
                if "out of memory" in message.lower():
                    raise ValueError("Memória insuficiente para MuseTalk. Use 512 px e lote 1.")
                raise ValueError("MuseTalk falhou. Confira o ambiente local, áudio e rosto de entrada.")
            output = Path(json.loads((work / "result.json").read_text(encoding="utf-8"))["file"]).resolve()
            if output.parent != work or not output.is_file():
                raise ValueError("Saída MuseTalk inválida.")
            if abs(duration(output) - seconds) > .15:
                raise ValueError("MuseTalk retornou uma duração inesperada.")
            duration(output, True)
            result_path = scratch / (work.name + ".mp4")
            output.replace(result_path)
            return (InputImpl.VideoFromFile(str(result_path)),)
        finally:
            # work was created by this invocation directly below the owned temp directory.
            if work.parent == scratch and work.name.startswith("job-"):
                shutil.rmtree(work, ignore_errors=True)
