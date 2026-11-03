"""Explicit local smoke test using only the synthetic graphs from PrivateConditioningTest.

Runs one job at a time; refuses to start if another job is queued. Does not restart
ComfyUI, change models, interrupt jobs, or read user history/media.
"""
import argparse
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--server", required=True)
    parser.add_argument("--graphs-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--workflows", nargs="+", default=["ideogram4_t2i_api", "ideogram4_turbotime_api", "krea2_raw_api", "krea2_turbo_api", "krea2_turbo_4step_api"])
    args = parser.parse_args()
    server = args.server.rstrip("/")
    args.output_dir.mkdir(parents=True, exist_ok=True)

    def request(route, body=None):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(server + route, data, {"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            raise RuntimeError(error.read().decode()[:3000]) from error

    reports = []
    for name in args.workflows:
        queue = request("/queue")
        if queue.get("queue_running") or queue.get("queue_pending"):
            raise RuntimeError("ComfyUI queue is occupied; no additional test submitted")
        graph = json.loads((args.graphs_dir / (name + ".json")).read_text(encoding="utf-8"))
        # Guard against accidentally using a user workflow or fixture.
        texts = [n["inputs"].get("text", "") for n in graph.values() if n["class_type"] == "CLIPTextEncode"]
        texts += [n["inputs"].get("high_level_description", "") for n in graph.values() if n["class_type"] == "Ideogram4PromptBuilderKJ"]
        assert any(isinstance(t, str) and t.startswith("Product photograph of a blue ceramic teapot") for t in texts)
        assert all(n["inputs"].get("filename_prefix", "").startswith("bwdacc_QA/private_conditioning/") for n in graph.values() if n["class_type"] == "SaveImage")
        start = time.monotonic()
        result = request("/prompt", {"prompt": graph, "client_id": "bwdacc-private-conditioning-qa"})
        prompt_id = result["prompt_id"]
        report = {"workflow": name, "prompt_id": prompt_id, "status": "submitted"}
        reports.append(report)
        print(json.dumps(report), flush=True)
        for _ in range(600):
            history = request("/history/" + prompt_id).get(prompt_id, {})
            status = history.get("status", {})
            if status.get("completed") or status.get("status_str") == "error":
                report.update(status=status.get("status_str"), seconds=round(time.monotonic() - start, 1))
                (args.output_dir / (name + "-history.json")).write_text(json.dumps(history, indent=2), encoding="utf-8")
                for node_id, output in history.get("outputs", {}).items():
                    for index, file in enumerate(output.get("images", [])):
                        if file.get("type") != "output":
                            continue
                        target = args.output_dir / (name + "-" + node_id + "-" + str(index) + ".png")
                        with urllib.request.urlopen(server + "/view?" + urllib.parse.urlencode(file), timeout=30) as response:
                            target.write_bytes(response.read())
                break
            time.sleep(2)
        else:
            report["status"] = "timeout_job_not_interrupted"
        (args.output_dir / "report.json").write_text(json.dumps(reports, indent=2), encoding="utf-8")
        print(json.dumps(report), flush=True)
        if report["status"] != "success":
            raise RuntimeError("Test did not complete: " + name)


if __name__ == "__main__":
    main()
