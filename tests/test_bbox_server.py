"""Opt-in integration checks against a running ComfyUI; no model downloads/restarts.

python tests/test_bbox_server.py --server http://127.0.0.1:8188 [--graphs EXPORT_DIR]
The optional graphs come from BoxesRegressionTest. They render neutral 1 MP fixtures.
"""
import argparse
import json
import time
import urllib.request
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--server", required=True)
    parser.add_argument("--graphs", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    base = args.server.rstrip("/")
    report = []

    def get(path):
        return json.load(urllib.request.urlopen(base + path, timeout=20))

    def run(graph, label, timeout=120):
        request = urllib.request.Request(base + "/prompt", json.dumps({"prompt": graph, "client_id": "momentos-bbox-regression"}).encode(), {"Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=30) as response:
            result = json.load(response)
        prompt_id = result["prompt_id"]
        start = time.monotonic()
        print("Submitted", label, prompt_id, flush=True)
        while time.monotonic() - start < timeout:
            history = get("/history/" + prompt_id).get(prompt_id)
            if history:
                errors = [data for kind, data in history["status"]["messages"] if kind == "execution_error"]
                if errors:
                    raise AssertionError(f"{label}: {errors[0].get('node_type')}: {errors[0].get('exception_message')}")
                assert history["status"]["status_str"] == "success", history["status"]
                report.append({"test": label, "prompt_id": prompt_id, "seconds": round(time.monotonic()-start, 1), "outputs": history["outputs"]})
                if args.report:
                    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
                print("PASS", label, report[-1]["seconds"], "s", flush=True)
                return history
            time.sleep(2)
        raise TimeoutError(f"{label}: no final status after {timeout}s; prompt {prompt_id} was not cancelled")

    for count in (1, 2, 12):
        elements = [{"type": "text" if i % 2 else "obj", "desc": "A blue paper boat", "text": "BOAT", "bbox": [200,100,600,400]} for i in range(count)]
        graph = {
            "layout": {"class_type": "MOSSImageLayoutPrompt", "inputs": {"prompt": "Paper boat poster", "element": {"__value__": elements}, "width": 1024, "height": 1024, "character_boxes": ""}},
            "preview": {"class_type": "PreviewAny", "inputs": {"source": ["layout",0]}},
        }
        result = run(graph, f"native literal array, {count} boxes")
        preview = result["outputs"]["preview"]
        # PreviewAny serializes the compiled prompt as a string in its UI text output.
        text = preview.get("text", [])
        if isinstance(text, list): text = "".join(text)
        layout = json.loads(text)
        assert len(layout["items"]) == count, layout
        assert layout["items"][0]["bbox"] == [-308,-410,103,-102], layout
        if count > 1: assert layout["items"][1]["text"] == "BOAT", layout

    if args.graphs:
        paths = sorted(args.graphs.glob("*_bbox_smoke.json"))
        assert len(paths) == 3, "Export the three BoxesRegressionTest graphs first"
        for path in paths:
            result = run(json.loads(path.read_text(encoding="utf-8")), path.stem, timeout=1800)
            assert any(output.get("images") for output in result["outputs"].values()), "Missing generated image"


if __name__ == "__main__": main()
