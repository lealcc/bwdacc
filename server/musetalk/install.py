"""Install the optional, pinned MuseTalk package inside a commercial Studio root.

Usage: python server/musetalk/install.py --studio <owned bwdacc Studio folder>
Downloads public weights/code only. No media is uploaded and no Comfy process is restarted.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request


def digest(path):
    with path.open("rb") as stream:
        result = hashlib.sha256()
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
        return result.hexdigest()


def install(studio):
    studio = studio.resolve()
    if (studio / ".bwdacc-runtime").read_text(encoding="utf-8-sig").strip() != "bwdacc-commercial-runtime-v1":
        raise ValueError("Choose a managed commercial Studio root.")
    source = Path(__file__).resolve().parent
    root = studio / "models/bwdacc_musetalk"
    if root.exists() and any(root.iterdir()) and not (root / ".bwdacc-musetalk").is_file():
        raise ValueError("Existing unowned MuseTalk directory; nothing was changed.")
    root.mkdir(parents=True, exist_ok=True)
    (root / ".bwdacc-musetalk").write_text("bwdacc-musetalk-v1", encoding="utf-8")
    manifest = json.loads((source / "manifest.json").read_text())
    print("Preparing optional MuseTalk environment and 2.19 GB of resources.", flush=True)

    def download(item):
        target = (root / item["path"]).resolve()
        if not target.is_relative_to(root.resolve()):
            raise ValueError("Unsafe resource path")
        if target.is_file():
            if target.stat().st_size == item["bytes"] and digest(target) == item["sha256"]:
                return
            raise ValueError("Existing resource differs; preserved: " + item["path"])
        target.parent.mkdir(parents=True, exist_ok=True)
        partial = target.with_suffix(target.suffix + ".part")
        req = urllib.request.Request(item["url"], headers={"User-Agent": "bwdacc-model-installer"})
        with urllib.request.urlopen(req, timeout=90) as response, partial.open("wb") as output:
            received = 0
            for block in iter(lambda: response.read(1024 * 1024), b""):
                received += len(block)
                if received > item["bytes"]:
                    raise ValueError("Resource exceeded expected size")
                output.write(block)
        if partial.stat().st_size != item["bytes"] or digest(partial) != item["sha256"]:
            raise ValueError("Resource checksum mismatch: " + item["path"])
        partial.replace(target)
        print("Ready: " + item["path"], flush=True)

    uv = next((studio / "tools").rglob("uv.exe"))
    active = json.loads((studio / "active.json").read_text(encoding="utf-8-sig"))["Active"]
    base = (studio / "releases" / active / "environment/Scripts/python.exe").resolve()
    if not base.is_relative_to(studio / "releases") or not base.is_file():
        raise ValueError("Managed Python missing")
    environment = root / "environment"
    python = environment / "Scripts/python.exe"
    def execute(args):
        subprocess.run(args, check=True, creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        transfers = [pool.submit(download, item) for item in manifest["files"]]
        if not python.is_file():
            execute([str(uv), "--no-config", "venv", "--python", str(base), str(environment)])
        execute([str(uv), "--no-config", "pip", "sync", "--python", str(python), "--require-hashes", "--only-binary", ":all:",
                 "--default-index", "https://pypi.org/simple", "--index", "https://download.pytorch.org/whl/cu130",
                 "--index-strategy", "unsafe-best-match", str(source / "requirements.lock")])
        for result in transfers:
            result.result()
    # Upstream's ffmpeg call resolves only this packaged executable first.
    ffmpeg = subprocess.check_output([str(python), "-I", "-c", "import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())"], text=True).strip()
    (root / "bin").mkdir(exist_ok=True)
    shutil.copy2(ffmpeg, root / "bin/ffmpeg.exe")
    shutil.copy2(source / "runner.py", root / "runner.py")
    shutil.copy2(source / "manifest.json", root / "manifest.json")
    execute([str(python), "-I", "-c", "import torch, diffusers, transformers, mediapipe, librosa; assert torch.cuda.is_available(); print('MuseTalk CUDA environment ready')"])
    (root / "ready.json").write_text(json.dumps({"version": 1, "manifest": digest(source / "manifest.json"),
        "runner": digest(root / "runner.py"), "requirements": digest(source / "requirements.lock")}), encoding="utf-8")
    print("MuseTalk installed: " + str(root), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--studio", required=True, type=Path)
    install(parser.parse_args().studio)
