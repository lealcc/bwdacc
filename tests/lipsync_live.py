"""Opt-in GPU smoke test against a local ComfyUI, using supplied synthetic fixtures.

Does not restart ComfyUI, interrupt jobs, or read unrelated history/media.
Run with the isolated MuseTalk Python (PyAV is required).
"""
import argparse
import json
from pathlib import Path
import time
import urllib.parse
import urllib.request
import uuid

import av


def run(server, fixtures, report):
    url = urllib.parse.urlparse(server)
    if url.hostname not in ("127.0.0.1", "localhost") or url.scheme != "http":
        raise ValueError("This fixture requires an explicit loopback ComfyUI.")
    server = server.rstrip("/")

    def request(path, value=None):
        data = json.dumps(value).encode() if value is not None else None
        req = urllib.request.Request(server + path, data=data, headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=30) as response:
            return json.load(response)

    deadline = time.monotonic() + 180
    while True:
        try:
            schema = request("/object_info/BwdaccMuseTalk")
            if "BwdaccMuseTalk" in schema:
                break
        except OSError:
            pass
        if time.monotonic() > deadline:
            raise RuntimeError("MuseTalk node did not become available.")
        time.sleep(2)
    queue = request("/queue")
    if queue.get("queue_running") or queue.get("queue_pending"):
        raise RuntimeError("Queue is busy; smoke test did not submit anything.")
    print("Node schema ready; queue empty.", flush=True)

    def upload(path, suffix):
        boundary = "bwdacc" + uuid.uuid4().hex
        name = "bwdacc-lipsync-fixture-" + uuid.uuid4().hex + suffix
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="image"; filename="{name}"\r\n'
                'Content-Type: application/octet-stream\r\n\r\n').encode() + path.read_bytes()
        body += f"\r\n--{boundary}--\r\n".encode()
        req = urllib.request.Request(server + "/upload/image", data=body,
                                     headers={"Content-Type": "multipart/form-data; boundary=" + boundary})
        with urllib.request.urlopen(req, timeout=30) as response:
            result = json.load(response)
        return (result.get("subfolder", "").rstrip("/") + "/" + result["name"]).lstrip("/")

    audio = upload(fixtures / "voice.wav", ".audio")
    source = upload(fixtures / "portrait.png", ".png")
    result = {"passed": False, "jobs": []}
    for kind in ("image", "video"):
        started = time.monotonic()
        graph = {
            "lipsync": {"class_type": "BwdaccMuseTalk", "inputs": {"source": source, "audio": audio,
                "kind": kind, "max_side": 512, "batch_size": 2, "seed": 42}},
            "save": {"class_type": "SaveVideo", "inputs": {"video": ["lipsync", 0],
                "filename_prefix": "bwdacc/ValidationLipsync", "format": "mp4", "codec": "h264"}}}
        prompt_id = request("/prompt", {"prompt": graph, "client_id": str(uuid.uuid4())})["prompt_id"]
        print(kind + " queued: " + prompt_id, flush=True)
        while True:
            history = request("/history/" + prompt_id).get(prompt_id)
            if history:
                if history.get("status", {}).get("status_str") == "error":
                    report.write_text(json.dumps(history, indent=2), encoding="utf-8")
                    raise RuntimeError("Comfy execution failed; see fixture report.")
                if history.get("outputs"):
                    break
            if time.monotonic() - started > 1000:
                raise TimeoutError("Fixture wait expired; no unrelated jobs were interrupted.")
            time.sleep(2)
        output = history["outputs"]["save"]
        files = [entry for entries in output.values() if isinstance(entries, list)
                 for entry in entries if isinstance(entry, dict) and "filename" in entry]
        if len(files) != 1:
            raise AssertionError("Expected one video result: " + repr(output))
        path = fixtures / ("comfy_" + kind + "_lipsync.mp4")
        with urllib.request.urlopen(server + "/view?" + urllib.parse.urlencode(files[0]), timeout=30) as response:
            path.write_bytes(response.read())
        with av.open(str(path)) as container:
            video = container.streams.video[0]
            assert container.streams.audio, "Missing generated audio"
            assert video.width == 512 and video.height == 512, "Aspect ratio/size changed"
            assert video.codec_context.name == "h264", "Unexpected video codec"
            assert video.average_rate == 25, "Unexpected frame rate"
            frames = sum(1 for _ in container.decode(video))
            seconds = float(video.duration * video.time_base)
            assert frames > 100 and 4.6 < seconds < 4.95, "Incomplete output"
        row = {"kind": kind, "promptId": prompt_id, "seconds": seconds, "frames": frames,
               "elapsed": round(time.monotonic() - started, 2), "bytes": path.stat().st_size}
        result["jobs"].append(row)
        print(json.dumps(row), flush=True)
        if kind == "image":
            source = upload(path, ".mp4")
    result["passed"] = True
    report.write_text(json.dumps(result, indent=2), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--server", required=True)
    parser.add_argument("--fixtures", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    run(args.server, args.fixtures, args.report)
