"""Dense region captions from the pinned, native Transformers Florence-2 checkpoint."""
import hashlib
import json
import math
from pathlib import Path
import re
import time

MODEL_LABEL = "Florence-2-base-ft"
MODEL_FOLDER = "florence-2-base-ft"
TASK = "<DENSE_REGION_CAPTION>"
_loaded = None
_identity = None


def clean_description(value):
    return " ".join(re.sub(r"<[^>]*>", "", value).split())[:600] if isinstance(value, str) else ""


def composition_result(parsed, width, height):
    """Turn grounded captions into the same editable, normalized layout contract."""
    if width <= 0 or height <= 0 or not isinstance(parsed, dict):
        raise ValueError("Resultado de composição inválido.")
    bounds, labels = parsed.get("bboxes", []), parsed.get("labels", [])
    if not isinstance(bounds, list) or not isinstance(labels, list) or len(bounds) != len(labels):
        raise ValueError("Boxes e descrições não correspondem.")
    candidates = []
    for coords, label in zip(bounds[:128], labels[:128]):
        if not isinstance(label, str) or not isinstance(coords, (list, tuple)) or len(coords) != 4:
            continue
        if any(isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) for v in coords):
            continue
        description = clean_description(label)
        if not description:
            continue
        x1, y1, x2, y2 = coords
        x1, x2 = max(0., x1 / width), min(1., x2 / width)
        y1, y2 = max(0., y1 / height), min(1., y2 / height)
        if x2 <= x1 or y2 <= y1 or (x2 - x1) * (y2 - y1) < .0025:
            continue
        candidates.append({"x": x1, "y": y1, "w": x2 - x1, "h": y2 - y1,
                           "type": "obj", "desc": description, "text": ""})
    # Prefer larger scene elements when the model finds more than the editor can hold.
    candidates.sort(key=lambda b: -(b["w"] * b["h"]))
    accepted = []
    for candidate in candidates:
        duplicate = False
        for box in accepted:
            intersection = max(0, min(candidate["x"] + candidate["w"], box["x"] + box["w"]) - max(candidate["x"], box["x"])) * max(0, min(candidate["y"] + candidate["h"], box["y"] + box["h"]) - max(candidate["y"], box["y"]))
            union = candidate["w"] * candidate["h"] + box["w"] * box["h"] - intersection
            if intersection / union > .9:
                if len(candidate["desc"]) > len(box["desc"]):
                    box["desc"] = candidate["desc"]
                duplicate = True
                break
        if not duplicate:
            accepted.append(candidate)
    if not accepted:
        raise ValueError("Nenhum elemento encontrado. Desenhe os boxes manualmente ou tente outra imagem.")
    total = len(accepted)
    accepted = accepted[:12]
    accepted.sort(key=lambda b: (round(b["y"] * 5), b["x"]))
    return {"enabled": True, "boxes": accepted, "aspect": width / height,
            "width": width, "height": height, "model": MODEL_LABEL, "truncated": total > 12}


def verify_assets(folder, entries):
    """Verify config, tokenizer and safetensors before any model parsing/loading."""
    identity = []
    for entry in entries:
        name = entry["name"]
        if Path(name).name != name or "/" in name or "\\" in name or name in (".", ".."):
            raise ValueError("Arquivo de modelo inválido.")
        file = folder / name
        if not file.is_file() or file.stat().st_size != entry["bytes"]:
            raise ValueError("Instale Compositor e seleção visual em Modelos no bwdacc Desktop.")
        stat = file.stat()
        identity.append((str(file), stat.st_size, stat.st_mtime_ns))
    identity = tuple(identity)
    if identity != _identity:
        for entry in entries:
            with (folder / entry["name"]).open("rb") as stream:
                if hashlib.file_digest(stream, "sha256").hexdigest() != entry["sha256"]:
                    raise ValueError("Modelo Florence com checksum inválido. Baixe novamente pelo Desktop.")
    return identity


def load_model():
    global _loaded, _identity
    import folder_paths
    import torch
    from transformers import AutoProcessor, Florence2ForConditionalGeneration
    roots = folder_paths.get_folder_paths("vision") if "vision" in folder_paths.folder_names_and_paths else [str(Path(folder_paths.models_dir) / "vision")]
    folder = next((Path(root) / MODEL_FOLDER for root in roots if (Path(root) / MODEL_FOLDER).is_dir()), None)
    if folder is None:
        raise ValueError("Instale Compositor e seleção visual em Modelos no bwdacc Desktop.")
    manifest = json.loads(Path(__file__).with_name("florence_manifest.json").read_text(encoding="utf-8"))
    identity = verify_assets(folder, manifest["files"])
    device = "cuda" if torch.cuda.is_available() else "cpu"
    if _loaded is None or identity != _identity:
        _loaded = None
        dtype = torch.float16 if device == "cuda" else torch.float32
        processor = AutoProcessor.from_pretrained(str(folder), local_files_only=True, trust_remote_code=False)
        model = Florence2ForConditionalGeneration.from_pretrained(str(folder), local_files_only=True,
            trust_remote_code=False, use_safetensors=True, dtype=dtype, attn_implementation="sdpa").eval()
        _loaded = (processor, model)
        _identity = identity
    return *_loaded, device


def analyse_composition(picture):
    import torch
    import comfy.model_management as memory
    from transformers import StoppingCriteria, StoppingCriteriaList
    started = time.monotonic()

    class JobStop(StoppingCriteria):
        def __call__(self, input_ids, scores, **kwargs):
            memory.throw_exception_if_processing_interrupted()
            if time.monotonic() - started > 180:
                raise TimeoutError("Análise demorou demais. Tente uma imagem menor.")
            return False

    processor, model, device = load_model()
    inputs = generated = None
    try:
        model.to(device)
        inputs = processor(text=TASK, images=picture, return_tensors="pt").to(device, model.dtype)
        with torch.inference_mode():
            generated = model.generate(**inputs, max_new_tokens=1024, num_beams=3, do_sample=False,
                stopping_criteria=StoppingCriteriaList([JobStop()]))
        answer = processor.batch_decode(generated, skip_special_tokens=False)[0]
        parsed = processor.post_process_generation(answer, task=TASK, image_size=picture.size)
        result = composition_result(parsed.get(TASK, {}), *picture.size)
        # Some fine-tuned dense captions are just category names. Describe those
        # actual crops with the same VLM, keeping the original grounded geometry.
        width, height = picture.size
        for box in result["boxes"]:
            if len(box["desc"].split()) >= 4:
                continue
            memory.throw_exception_if_processing_interrupted()
            crop = picture.crop((int(box["x"] * width), int(box["y"] * height),
                min(width, math.ceil((box["x"] + box["w"]) * width)),
                min(height, math.ceil((box["y"] + box["h"]) * height))))
            try:
                inputs = processor(text="<CAPTION>", images=crop, return_tensors="pt").to(device, model.dtype)
                with torch.inference_mode():
                    generated = model.generate(**inputs, max_new_tokens=96, num_beams=3, do_sample=False,
                        stopping_criteria=StoppingCriteriaList([JobStop()]))
                caption = clean_description(processor.batch_decode(generated, skip_special_tokens=True)[0])
                if len(caption.split()) >= 4:
                    box["label"] = box["desc"]
                    box["desc"] = caption
            finally:
                crop.close()
        return result
    finally:
        inputs = generated = None
        model.to("cpu")
        memory.soft_empty_cache()
