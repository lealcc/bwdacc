"""Attach the installed isolated MuseTalk package to an explicitly chosen ComfyUI.

No Python dependencies are installed in that ComfyUI and no process is restarted.
Only the owned custom_nodes/bwdacc_musetalk directory is created or updated.
"""
import argparse
import json
from pathlib import Path
import shutil


def connect(comfy, package):
    comfy, package = comfy.resolve(), package.resolve()
    if not (comfy / "main.py").is_file() or not (comfy / "custom_nodes").is_dir():
        raise ValueError("Choose a ComfyUI installation containing main.py and custom_nodes.")
    if package.name != "bwdacc_musetalk" or not (package / "ready.json").is_file():
        raise ValueError("Install the isolated MuseTalk package first.")
    destination = comfy / "custom_nodes/bwdacc_musetalk"
    if destination.is_symlink() or destination.resolve().parent != (comfy / "custom_nodes").resolve():
        raise ValueError("Node directory must stay inside the chosen ComfyUI.")
    marker = destination / ".bwdacc-musetalk-node"
    if destination.exists() and any(destination.iterdir()) and not marker.is_file():
        raise ValueError("Existing unowned node directory; nothing was changed.")
    destination.mkdir(parents=True, exist_ok=True)
    marker.write_text("bwdacc-musetalk-node-v1", encoding="utf-8")
    source = Path(__file__).resolve().parent
    shutil.copy2(source / "comfy_entry.py", destination / "__init__.py")
    shutil.copy2(source.parent / "momentos_workflows/lipsync.py", destination / "lipsync.py")
    (destination / "package.json").write_text(json.dumps({"path": str(package)}), encoding="utf-8")
    print("MuseTalk node prepared. Restart the chosen ComfyUI when its queue is empty.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--comfy", type=Path, required=True)
    parser.add_argument("--package", type=Path, required=True)
    args = parser.parse_args()
    connect(args.comfy, args.package)
