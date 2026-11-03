"""Opt-in full H3 extension test against an already running ComfyUI.

Requires existing model weights and a source video under ComfyUI/input.
No installation, restart or cancellation of other jobs is performed.
"""
import argparse
import hashlib
import json
import shutil
import subprocess
import time
import urllib.request
from pathlib import Path


def verify_media(history, comfy_root):
    """Check actual encoded output, including overlap removal and audio synchronization."""
    root = (comfy_root / "output").resolve()

    def media(node):
        file = history["outputs"][node]["images"][0]
        path = (root / file.get("subfolder", "").replace("\\", "/") / file["filename"]).resolve()
        assert path.is_relative_to(root) and path.is_file()
        data = json.loads(subprocess.check_output([shutil.which("ffprobe"), "-v", "error", "-show_streams", "-of", "json", str(path)]))
        video = next(s for s in data["streams"] if s["codec_type"] == "video")
        audio = next(s for s in data["streams"] if s["codec_type"] == "audio")
        return path, video, audio

    path, video, audio = media("17")
    _, raw, _ = media("qa_raw")
    expected_frames = 3 * 24 + int(raw["nb_frames"]) - 22
    assert int(video["nb_frames"]) == expected_frames, (video, raw)
    assert video["r_frame_rate"] == "24/1" and video["codec_name"] == "h264", video
    assert (video["width"], video["height"]) == (768, 416), video
    assert int(audio["sample_rate"]) == 48000 and audio["channels"] == 2, audio
    assert abs(float(video["duration"]) - float(audio["duration"])) < .1, (video, audio)
    subprocess.run([shutil.which("ffmpeg"), "-v", "error", "-nostdin", "-i", str(path), "-f", "null", "-"], check=True, capture_output=True)
    return {"file": str(path), "frames": expected_frames, "duration": float(video["duration"]),
            "audio_sample_rate": 48000, "audio_channels": 2, "overlap_frames_removed": 22, "full_decode": "passed"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--server", required=True)
    parser.add_argument("--comfy-root", type=Path, required=True)
    parser.add_argument("--source", required=True, help="Relative input video path")
    parser.add_argument("--variant", choices=["turbo", "normal"], default="turbo")
    parser.add_argument("--report-dir", type=Path, required=True)
    args = parser.parse_args()
    args.report_dir.mkdir(parents=True, exist_ok=True)
    source = (args.comfy_root / "input" / args.source).resolve()
    assert source.is_relative_to((args.comfy_root / "input").resolve())
    before = hashlib.sha256(source.read_bytes()).hexdigest()
    graph_path = Path(__file__).resolve().parents[1] / "app/src/main/assets/workflows/profiles" / f"minimax_h3_extend_{args.variant}_24.json"
    graph = json.loads(graph_path.read_text(encoding="utf-8"))
    graph["momentos_extend_source"]["inputs"].update(video=args.source, start_ms=1000, end_ms=4000)
    graph["8"]["inputs"]["prompt"] = (
        "integrated_multimodal_description: [Shot 1] Continue the source shot naturally after the opening motion guide. "
        "A red ceramic mug stands on the left and a blue ceramic vase stands on the right, on a clean white table in soft studio light. "
        "The camera keeps slowly pushing forward toward both stationary objects. Preserve their colors, shapes, positions and lighting. "
        "One continuous shot, no cuts and no new objects.\n\noverall_soundscape: Quiet room ambience. No speech, no voices, no vocalizations."
        "\n\nnon_diegetic_music: N/A"
    )
    graph["17"]["inputs"]["filename_prefix"] = "Momentos/QA/Extension/" + args.variant
    # Keep the model's unjoined clip for a measurable seam/overlap check.
    graph["qa_raw"] = {"class_type": "SaveVideo", "inputs": {
        "video": ["16", 0], "filename_prefix": "Momentos/QA/Extension/" + args.variant + "-raw",
        "format": "mp4", "format.codec": "h264"}}
    (args.report_dir / (args.variant + "-graph.json")).write_text(json.dumps(graph, indent=2), encoding="utf-8")
    base = args.server.rstrip("/")
    request = urllib.request.Request(base + "/prompt", json.dumps({"prompt": graph, "client_id": "momentos-extension-live"}).encode(), {"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=30) as response:
        submitted = json.load(response)
    prompt_id = submitted["prompt_id"]
    (args.report_dir / (args.variant + "-submission.json")).write_text(json.dumps(submitted), encoding="utf-8")
    print("Submitted", args.variant, prompt_id, flush=True)
    start = time.monotonic()
    last_log = start
    while time.monotonic() - start < 3600:
        history = json.load(urllib.request.urlopen(base + "/history/" + prompt_id, timeout=20)).get(prompt_id)
        if history:
            (args.report_dir / (args.variant + "-history.json")).write_text(json.dumps(history, indent=2), encoding="utf-8")
            errors = [data for kind, data in history["status"]["messages"] if kind == "execution_error"]
            if errors:
                raise AssertionError(f"{errors[0].get('node_type')}: {errors[0].get('exception_message')}")
            assert history["status"]["status_str"] == "success", history["status"]
            assert hashlib.sha256(source.read_bytes()).hexdigest() == before, "Original video changed"
            media = verify_media(history, args.comfy_root)
            (args.report_dir / (args.variant + "-verification.json")).write_text(json.dumps(media, indent=2), encoding="utf-8")
            print("PASS inference", args.variant, round(time.monotonic() - start, 1), "s", flush=True)
            print(json.dumps(history["outputs"], ensure_ascii=True), flush=True)
            return
        if time.monotonic() - last_log > 30:
            print("Running", args.variant, round(time.monotonic() - start), "s", flush=True)
            last_log = time.monotonic()
        time.sleep(3)
    raise TimeoutError(f"No final status after one hour; prompt {prompt_id} was not cancelled")


if __name__ == "__main__": main()
