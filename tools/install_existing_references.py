"""Explicitly install local reference tools into an existing ComfyUI, without restarting it.

Uses already downloaded, checksum-verified visual models and a prepared vendor directory.
Does not replace the existing momentos_workflows package or change its Python environment.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil


def install(comfy, models, vendor):
    repo = Path(__file__).resolve().parents[1]
    comfy, models, vendor = comfy.resolve(), models.resolve(), vendor.resolve()
    if not (comfy / "main.py").is_file() or not (vendor / "efficientsam3/sam3/sam3/model_builder.py").is_file():
        raise ValueError("Select an existing ComfyUI and a prepared EfficientSAM3 vendor folder.")
    manifest = json.loads((repo / "desktop/Bwdacc.Desktop/Payload/visual-models.json").read_text())
    for entry in manifest["files"]:
        relative = Path(entry["destination"]).relative_to("models")
        source, target = models / relative, comfy / "models" / relative
        for file in (source, target) if target.exists() else (source,):
            if file.stat().st_size != entry["bytes"]:
                raise ValueError("Unexpected resource size: " + str(file))
            with file.open("rb") as stream:
                if hashlib.file_digest(stream, "sha256").hexdigest() != entry["sha256"]:
                    raise ValueError("Unexpected resource checksum: " + str(file))
        if not target.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            partial = target.with_suffix(target.suffix + ".install-part")
            shutil.copyfile(source, partial)
            partial.replace(target)
    target = comfy / "custom_nodes/bwdacc_reference_tools"
    marker = target / ".bwdacc-reference-tools"
    if target.exists() and not marker.is_file():
        raise ValueError("Existing unowned node directory was preserved.")
    target.mkdir(parents=True, exist_ok=True)
    marker.write_text("bwdacc-reference-tools-v1\n")
    for name in ("visual_extract.py", "florence_extract.py", "florence_manifest.json", "reference_focus.py"):
        shutil.copyfile(repo / "server/momentos_workflows" / name, target / name)
    if not (target / "vendor").exists():
        shutil.copytree(vendor, target / "vendor")
    (target / "__init__.py").write_text(
        'from .visual_extract import BwdaccVisualExtract\n'
        'from .reference_focus import BwdaccReferenceFocus\n'
        'NODE_CLASS_MAPPINGS = {"BwdaccVisualExtract": BwdaccVisualExtract, "BwdaccReferenceFocus": BwdaccReferenceFocus}\n')
    print("Reference tools and verified models installed: " + str(target))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--comfy", type=Path, required=True)
    parser.add_argument("--models", type=Path, required=True)
    parser.add_argument("--vendor", type=Path, required=True)
    args = parser.parse_args()
    install(args.comfy, args.models, args.vendor)
