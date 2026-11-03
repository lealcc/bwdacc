"""Local LoRA display metadata. Reads headers only; never downloads or deserializes model weights."""
import asyncio
import json
import re
import struct
from pathlib import Path

_CACHE = {}


def clean(value):
    if not isinstance(value, str): return ""
    name = re.sub(r"[\x00-\x1f\x7f]", " ", value).strip()[:180]
    normalized = re.sub(r"[ _.-]+", " ", name).lower()
    return "" if normalized in {"aitk lora", "lora", "model", "default", "untitled", "output"} else name


def read_local_metadata(path):
    path = Path(path)
    sidecar = Path(str(path) + ".rgthree-info.json")
    stamp = (path.stat().st_mtime_ns, path.stat().st_size, sidecar.stat().st_mtime_ns if sidecar.exists() else 0)
    key = str(path)
    if key in _CACHE and _CACHE[key][0] == stamp: return _CACHE[key][1]
    info, header = {}, {}
    try:
        if sidecar.exists() and sidecar.stat().st_size < 8_000_000:
            info = json.loads(sidecar.read_text(encoding="utf-8"))
            if not isinstance(info, dict): info = {}
        if path.suffix.lower() == ".safetensors":
            with path.open("rb") as stream:
                length = struct.unpack("<Q", stream.read(8))[0]
                if 2 <= length <= 16_000_000:
                    data = json.loads(stream.read(length))
                    header = data.get("__metadata__", {}) if isinstance(data, dict) else {}
                    if not isinstance(header, dict): header = {}
    except (OSError, ValueError, struct.error):
        pass
    civ = info.get("raw", {}).get("civitai", {}) or {}
    model = civ.get("model", {}) or {}
    title = clean(info.get("name")) or clean(model.get("name"))
    origin = "rgthree/Civitai local" if title else "safetensors"
    if not title:
        for field in ("modelspec.title", "title", "name", "ss_output_name"):
            title = clean(header.get(field))
            if title: break
    if origin == "safetensors" and title:
        title = re.sub(r"(?i)\.(safetensors|pt|pth)$", "", title).replace("_", " ")
    if not title:
        title, origin = path.stem.replace("_", " "), "arquivo"
    # Safety signals are explicit metadata, not a claim inferred from model output.
    adult = model.get("nsfw") is True or isinstance(civ.get("nsfwLevel"), (int, float)) and civ["nsfwLevel"] >= 4
    adult |= any(isinstance(image.get("nsfwLevel"), (int, float)) and image["nsfwLevel"] >= 4 for image in info.get("images", []))
    result = {"name": title, "nameSource": origin, "adult": bool(adult)}
    _CACHE[key] = (stamp, result)
    return result


def register():
    from aiohttp import web
    import folder_paths
    import sys
    server = sys.modules.get("server")
    instance = getattr(getattr(server, "PromptServer", None), "instance", None)
    if instance is None: return

    @instance.routes.get("/momentos/loras/metadata")
    async def metadata(request):
        def collect():
            items = []
            for name in folder_paths.get_filename_list("loras"):
                path = folder_paths.get_full_path("loras", name)
                if not path: continue
                try: items.append({"file": name, **read_local_metadata(path)})
                except OSError: continue
            return {"data": items}
        return web.json_response(await asyncio.to_thread(collect))
