"""Minimal entry point for an existing ComfyUI. Installed as __init__.py."""
import json
import os
from pathlib import Path
import subprocess

import folder_paths

package = Path(json.loads((Path(__file__).parent / "package.json").read_text(encoding="utf-8"))["path"]).resolve()
if package.name != "bwdacc_musetalk" or not (package / "ready.json").is_file():
    raise RuntimeError("Prepare o pacote MuseTalk local antes de carregar este node.")
folder_paths.add_model_folder_path("bwdacc_musetalk", str(package))


def input_path(name):
    root = Path(folder_paths.get_input_directory()).resolve()
    path = (root / name.replace("\\", "/")).resolve()
    if not path.is_relative_to(root) or not path.is_file():
        raise ValueError("Mídia de entrada indisponível.")
    return path


def ffmpeg(arguments):
    binary = package / "bin" / ("ffmpeg.exe" if os.name == "nt" else "ffmpeg")
    result = subprocess.run([str(binary), "-hide_banner", "-loglevel", "error", "-nostdin", "-y", *arguments],
                            capture_output=True, timeout=300,
                            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    if result.returncode:
        raise ValueError("Falha ao preparar mídia para MuseTalk. Confira o arquivo de entrada.")
    return result.stdout


from .lipsync import BwdaccMuseTalk
NODE_CLASS_MAPPINGS = {"BwdaccMuseTalk": BwdaccMuseTalk}
NODE_DISPLAY_NAME_MAPPINGS = {"BwdaccMuseTalk": "bwdacc · MuseTalk 1.5"}
