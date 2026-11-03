"""Local-only MuseTalk 1.5 worker. Optional upstream adapter runs out of process.

Upstream modules and model files are pinned by manifest.json at installation.
The adapter uses MediaPipe landmarks and a feathered mouth mask in place of the
original MMPose/BiSeNet stack. It is not the upstream reference implementation.
"""
import argparse
import importlib
import json
import os
from pathlib import Path
import sys
import types


def run(job_file):
    job = json.loads(Path(job_file).read_text(encoding="utf-8"))
    root = Path(__file__).resolve().parent
    models = root / "models"
    os.environ["FFMPEGA_MUSETALK_MODEL_DIR"] = str(models)
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    # Namespace only: do not load the unrelated FFMPEGA plugin or its agent tools.
    for name, folder in (("bwd_muse", root / "vendor"), ("bwd_muse.musetalk", root / "vendor/musetalk")):
        module = types.ModuleType(name)
        module.__path__ = [str(folder)]
        sys.modules[name] = module
    engine = importlib.import_module("bwd_muse.musetalk_synthesizer")
    detection = importlib.import_module("bwd_muse.musetalk.face_detection")
    import torch
    torch.set_num_threads(4)
    torch.set_num_interop_threads(2)
    if not torch.cuda.is_available():
        raise RuntimeError("MuseTalk precisa de uma GPU NVIDIA disponível neste ambiente.")

    def required(relative):
        path = models / relative
        if not path.is_file():
            raise RuntimeError("Recurso MuseTalk ausente: " + relative)
        return str(path)

    engine._find_or_download_model = lambda key, **_: required(
        "musetalkV15/musetalk.json" if key == "unet_config" else "musetalkV15/unet_fp16.safetensors")
    engine._get_vae_path = lambda: str(models / "sd-vae")
    engine._get_whisper_path = lambda: str(models / "whisper")
    engine._free_vram = lambda: torch.cuda.empty_cache()
    detection._get_model_path = lambda: required("face_landmarker.task")
    original_detect = detection.get_landmarks_and_bboxes

    def detect(*args, **kwargs):
        boxes, frames = original_detect(*args, **kwargs)
        # The adapter cycles face latents; missing detections would shift alignment.
        # Reject that case rather than reporting an unchanged or misaligned success.
        if not boxes or any(not row or row[0] == detection.COORD_PLACEHOLDER for row in boxes):
            raise ValueError("Rosto não encontrado em todos os frames. Use um rosto frontal, visível e bem iluminado.")
        return boxes, frames

    detection.get_landmarks_and_bboxes = detect
    # Preflight before allocating UNet/VAE, also giving an actionable face error.
    import cv2
    if Path(job["source"]).suffix.lower() in (".png", ".jpg", ".jpeg"):
        frame = cv2.imread(job["source"])
        ok = frame is not None
    else:
        first = cv2.VideoCapture(job["source"])
        ok, frame = first.read()
        first.release()
    if not ok or not detection.get_face_bbox(frame):
        raise ValueError("Nenhum rosto visível. Escolha uma imagem ou vídeo com um rosto frontal.")
    # The adapter calls 'ffmpeg'. Installer creates an exact local copy of this binary.
    os.environ["PATH"] = str(root / "bin") + os.pathsep + os.environ.get("PATH", "")
    torch.manual_seed(int(job.get("seed", 42)))
    with torch.inference_mode():
        output = engine.lip_sync(video_path=job["source"], audio_path=job["audio"],
                                 output_dir=job["output"], batch_size=int(job.get("batch", 2)),
                                 face_index=0, use_float16=True, fps=25)
    Path(job["result"]).write_text(json.dumps({"file": output}), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("job")
    run(parser.parse_args().job)
