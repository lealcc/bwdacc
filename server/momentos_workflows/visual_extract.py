"""Local image analysis. Jobs use the authenticated Comfy queue, never a public API."""
import base64
import hashlib
import io
import json
import math
from pathlib import Path
import sys
import threading

import numpy as np
from PIL import Image

MODEL_NAME = "efficientsam3_efficientvit.pt"
MODEL_SHA256 = "086b04b2e7da7cc98aa4621b70c7291608aa9d187357b98d03bd4d6533ed5a17"
MODEL_LABEL = "EfficientSAM3 EV-M"
_lock = threading.Lock()
_model = None
_verified = None


def validate_points(raw):
    points = json.loads(raw) if isinstance(raw, str) else raw
    if not isinstance(points, list) or not 1 <= len(points) <= 16:
        raise ValueError("Selecione de 1 a 16 pontos.")
    for point in points:
        if not isinstance(point, dict) or point.get("label") not in (0, 1):
            raise ValueError("Ponto de seleção inválido.")
        for key in ("x", "y"):
            value = point.get(key)
            if isinstance(value, bool) or not isinstance(value, (float, int)) or not math.isfinite(value) or not 0 <= value <= 1:
                raise ValueError("Ponto fora da imagem.")
    if not any(p["label"] == 1 for p in points):
        raise ValueError("Inclua pelo menos um ponto no objeto.")
    return points


def mask_result(masks, scores, points, width, height):
    """Choose a mask that satisfies the clicks, not an unrelated higher score."""
    best = None
    for mask, score in zip(masks, scores):
        mask = np.asarray(mask, dtype=bool).reshape(height, width)
        if not mask.any():
            continue
        if any(bool(mask[min(height - 1, int(p["y"] * height)), min(width - 1, int(p["x"] * width))]) != bool(p["label"]) for p in points):
            continue
        candidate = (float(score), -int(mask.sum()), mask)
        if best is None or candidate[:2] > best[:2]:
            best = candidate
    if best is None:
        raise ValueError("Não encontrei uma seleção para esses pontos. Desfaça ou toque mais ao centro do objeto.")
    mask = best[2]
    yy, xx = np.where(mask)
    png = io.BytesIO()
    Image.fromarray(mask.astype(np.uint8) * 255).save(png, format="PNG")
    return {"mask": base64.b64encode(png.getvalue()).decode("ascii"),
            "bounds": [int(xx.min()), int(yy.min()), int(xx.max()) + 1, int(yy.max()) + 1],
            "width": width, "height": height, "score": best[0], "model": MODEL_LABEL}


def load_model():
    global _model, _verified
    import folder_paths
    import torch
    roots = folder_paths.get_folder_paths("segmentation") if "segmentation" in folder_paths.folder_names_and_paths else [str(Path(folder_paths.models_dir) / "segmentation")]
    checkpoint = next((Path(root) / MODEL_NAME for root in roots if (Path(root) / MODEL_NAME).is_file()), None)
    if checkpoint is None:
        raise ValueError("Instale Compositor e seleção visual em Modelos no bwdacc Desktop.")
    identity = (str(checkpoint), checkpoint.stat().st_size, checkpoint.stat().st_mtime_ns)
    if identity != _verified:
        with checkpoint.open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != MODEL_SHA256:
                raise ValueError("Modelo SAM com checksum inválido. Baixe novamente pelo Desktop.")
        _model = None
        _verified = identity
    vendor = Path(__file__).parent / "vendor"
    for path in (vendor / "iopath", vendor / "efficientsam3" / "sam3"):
        if path.is_dir() and str(path) not in sys.path:
            sys.path.insert(0, str(path))
    try:
        from sam3.model_builder import build_efficientsam3_image_model
    except ImportError as error:
        raise ValueError("Atualize o motor no Desktop para instalar o suporte SAM.") from error
    if _model is None:
        _model = build_efficientsam3_image_model(checkpoint_path=str(checkpoint), backbone_type="efficientvit", model_name="b1",
            text_encoder_type="MobileCLIP-S0", text_encoder_context_length=16, device="cpu", load_from_HF=False)
    return _model, "cuda" if torch.cuda.is_available() else "cpu"


class BwdaccVisualExtract:
    @classmethod
    def INPUT_TYPES(cls):
        return {"required": {"image": ("IMAGE",), "operation": (["composition", "segment"],),
                             "points": ("STRING", {"default": "[]"})}}

    RETURN_TYPES = ("STRING",)
    FUNCTION = "execute"
    CATEGORY = "bwdacc/references"
    OUTPUT_NODE = True

    def execute(self, image, operation, points="[]"):
        import torch
        import comfy.model_management as memory
        if operation not in ("composition", "segment"):
            raise ValueError("Operação inválida.")
        selected = validate_points(points) if operation == "segment" else []
        if image.ndim != 4 or image.shape[0] != 1 or image.shape[-1] < 3:
            raise ValueError("Selecione uma única imagem.")
        height, width = image.shape[1:3]
        if max(width, height) > 2048 or width * height > 4_194_304:
            raise ValueError("A imagem de análise deve ter até 2048 pixels por lado.")
        picture = Image.fromarray((image[0, :, :, :3].detach().cpu().numpy().clip(0, 1) * 255).astype(np.uint8))
        with _lock:
            memory.throw_exception_if_processing_interrupted()
            memory.free_memory(2 * 1024 ** 3, memory.get_torch_device())
            if operation == "composition":
                from .florence_extract import analyse_composition
                result = analyse_composition(picture)
            else:
                model, device = load_model()
                from sam3.model.sam3_image_processor import Sam3Processor
                processor = state = None
                try:
                    model.to(device)
                    processor = Sam3Processor(model, device=device, confidence_threshold=.25)
                    with torch.inference_mode(), torch.autocast(device, dtype=torch.bfloat16,
                            enabled=device == "cuda" and torch.cuda.is_bf16_supported()):
                        state = processor.set_image(picture)
                        for point in selected:
                            memory.throw_exception_if_processing_interrupted()
                            state = processor.add_point_prompt([point["x"] * width, point["y"] * height], point["label"], state)
                        result = mask_result(state["masks"].cpu().numpy(), state["scores"].float().cpu().numpy(), selected, width, height)
                finally:
                    state = processor = None
                    model.to("cpu")
                    memory.soft_empty_cache()
        encoded = json.dumps(result, allow_nan=False)
        return {"ui": {"text": [encoded]}, "result": (encoded,)}
