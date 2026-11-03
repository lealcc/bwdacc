"""Package exported API graphs and pinned public dependency metadata; never download weights.

Run WorkflowProfilesTest on Android first, then pass its export directory and a directory
of Hugging Face /api/models/<repo>?blobs=true metadata responses. No local model path is
included in the generated package. Missing model provenance is a build error.
"""
import argparse
import hashlib
import json
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/workflows"
SOURCES = {
    "ideogram": "https://github.com/Comfy-Org/workflow_templates/blob/main/templates/image_ideogram4_t2i.json",
    "ideogram_turbotime": "https://huggingface.co/ostris/ideogram_4_turbotime_lora",
    "ideogram_layout": "https://github.com/kijai/ComfyUI-KJNodes/blob/main/nodes/ideogram4_nodes.py",
    "moss": "https://github.com/OpenMOSS/MOSS-Image/blob/main/gradio_app.py",
    "moss_layout": "https://github.com/OpenMOSS/MOSS-Image/blob/main/USERGUIDE.md",
    "moss_8gb_experimental": "https://github.com/RealRebelAI/ComfyUI-MOSS-GGUF",
    "kromoss": "https://huggingface.co/lodestones/Kromoss",
    "h3": "https://github.com/Comfy-Org/docs/blob/main/tutorials/video/minimax/minimax-h3.mdx",
    "h3_16gb": "https://github.com/kasei-san/minimax-h3-comfyui-workflows",
    "h3_8gb": "https://github.com/FlowForgeLabAi/MiniMax-H3-8GB-Workflows",
    "h3_extension": "https://github.com/Comfy-Org/docs/blob/main/tutorials/video/minimax/minimax-h3-native.mdx",
    "krea2": "https://github.com/krea-ai/krea-2",
    "krea2_turbo": "https://github.com/Comfy-Org/workflow_templates/blob/main/templates/image_krea2_turbo_t2i.json",
    "krea2_4step": "https://huggingface.co/lvladikov/Krea2-Turbo-Distill-4step-LoRA",
    "private_conditioning": "https://github.com/nova452/Rebalance-Pack",
    "ideogram_private_adapter": "https://huggingface.co/Giovska97/lorahot1/blob/e67730e025204e27febf3b9ed8c41a7cb34b7fc7/ideogramSnofsV1.safetensors",
}
NAMES = {"ideogram4_t2i_api": "Ideogram 4", "ideogram4_turbotime_api": "Ideogram 4 TurboTime", "moss_t2i_api": "MOSS Image SFT", "kromoss_t2i_api": "Kromoss · 8 passos", "minimax_h3_i2v_api": "MiniMax H3", "minimax_h3_turbo_api": "MiniMax H3 Turbo"}
NAMES.update(krea2_raw_api="Krea 2 RAW", krea2_turbo_api="Krea 2 Turbo", krea2_turbo_4step_api="Krea 2 Turbo · 4 steps")
PACKAGES = {
    "rebalance": {"repository": "https://github.com/nova452/Rebalance-Pack", "revision": "9ab5315e6aa8ec8cd14cc5354739d938c6fb101b", "destination": "custom_nodes/ComfyUI-ConditioningKrea2Rebalance"},
    "comfyui": {"repository": "https://github.com/Comfy-Org/ComfyUI", "revision": "c06bf5f569d268b80076e5f44126f87cdf835869", "destination": "."},
    "kjnodes": {"repository": "https://github.com/kijai/ComfyUI-KJNodes", "revision": "073efb07419f56cc714e099a82e49fbc23ad9263", "destination": "custom_nodes/ComfyUI-KJNodes"},
    "rgthree": {"repository": "https://github.com/rgthree/rgthree-comfy", "revision": "738105af5fb14e96fbecaf406dc356e284797e8c", "destination": "custom_nodes/rgthree-comfy"},
    "momentos": {"bundled_source": "server/momentos_workflows", "destination": "custom_nodes/momentos_workflows", "runtime": ["ffmpeg", "av", "numpy", "torch"]},
}


def model_inputs(graph):
    found = set()
    for node in graph.values():
        for key, value in node["inputs"].items():
            folder = {"unet_name": "diffusion_models", "clip_name": "text_encoders", "vae_name": "vae", "lora_name": "loras"}.get(key)
            if folder and isinstance(value, str): found.add((folder, value))
            if isinstance(value, dict) and value.get("on") and "lora" in value: found.add(("loras", value["lora"]))
    return sorted(found)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--export-dir", type=Path, required=True)
    parser.add_argument("--metadata-dir", type=Path, required=True)
    args = parser.parse_args()
    metadata = {}
    for path in sorted(args.metadata_dir.glob("*--*.json")):
        repo = json.loads(path.read_text(encoding="utf-8"))
        for sibling in repo.get("siblings", []):
            name = sibling["rfilename"].split("/")[-1]
            if name.endswith(".safetensors"):
                metadata.setdefault(name, (repo, sibling))
    models = {}
    # Retain already pinned provenance when regenerating with metadata for new models only.
    previous = json.loads((ASSETS / "catalog.json").read_text(encoding="utf-8")).get("models", {}) if (ASSETS / "catalog.json").exists() else {}

    def dependency(folder, name):
        name = name.replace("\\", "/")
        key = folder + "/" + name
        if key not in models:
            basename = name.rsplit("/", 1)[-1]
            if basename not in metadata:
                if key in previous:
                    models[key] = previous[key]
                    return key
                raise ValueError("Missing model provenance: " + name)
            repo, file = metadata[basename]
            lfs = file.get("lfs", {})
            if not lfs.get("sha256") or not lfs.get("size"): raise ValueError("Missing integrity metadata: " + name)
            models[key] = {"filename": basename, "destination": "models/" + key, "repository": repo["id"], "revision": repo["sha"],
                           "url": "https://huggingface.co/" + repo["id"] + "/resolve/" + repo["sha"] + "/" + quote(file["rfilename"], safe="/"),
                           "sha256": lfs["sha256"], "bytes": lfs["size"], "gated": repo.get("gated", False),
                           "license": repo.get("cardData", {}).get("license", "See upstream model card"), "model_card": "https://huggingface.co/" + repo["id"]}
        return key

    variants = json.loads((args.export_dir / "profile-matrix.json").read_text())["profiles"]
    output = ASSETS / "profiles"
    output.mkdir(exist_ok=True)
    catalog = {"schema_version": 1, "catalog_version": "2026.11.02.1", "graph_format": "comfyui_api", "sources": SOURCES,
               "packages": PACKAGES, "models": models, "workflows": [],
               "installation_contract": {"automatic_download": False, "separate_hardware_support_from_server_readiness": True,
                   "verify_sha256_before_activation": True, "resolve_paths_under_selected_comfy_root": True,
                   "require_model_terms_when_gated": True, "missing_dependency_state": "needs_install", "unsupported_profile_state": "unavailable"},
               "validation": {"graph_and_comfy_schema": "passed", "trim_and_merge_cpu_integration": "passed", "physical_vram_tiers_tested_locally": [],
                   "note": "Profiles are adaptations supported by cited upstream evidence, not local hardware benchmarks or hard memory guarantees."}}
    for base, label in NAMES.items():
        family = "krea2" if base.startswith("krea2") else "h3" if base.startswith("minimax") else "ideogram" if base.startswith("ideogram") else "kromoss" if base.startswith("kromoss") else "moss"
        item = {"id": base, "name": label, "base_graph": base + ".json", "family": family,
                "capabilities": {"text_to_image": family != "h3", "boxes": family in ["ideogram", "moss", "kromoss"], "boxes_experimental": family in ["moss", "kromoss"],
                                 "text_to_video": family == "h3", "image_to_video": family == "h3", "first_last_frame": family == "h3",
                                 "video_extension": family == "h3", "batch_max": 4}, "profiles": {}}
        for row in [v for v in variants if v["workflow"] == base]:
            profile = row["profile"]
            spec = {"status": "available" if row["available"] else "unavailable", "reason": row["reason"], "evidence": [family]}
            if "turbotime" in base: spec["evidence"].append("ideogram_turbotime")
            if family == "krea2":
                spec["evidence"].append("krea2_4step" if "4step" in base else "krea2_turbo" if "turbo" in base else "krea2")
            item["profiles"][profile] = spec
            if not row["available"]: continue
            graph = json.loads((args.export_dir / row["graph"]).read_text())
            raw = (json.dumps(graph, ensure_ascii=False, indent=2) + "\n").encode()
            (output / row["graph"]).write_bytes(raw)
            spec.update(graph="profiles/" + row["graph"], sha256=hashlib.sha256(raw).hexdigest(),
                        model_dependencies=[dependency(f, n) for f, n in model_inputs(graph)],
                        node_types=sorted({n["class_type"] for n in graph.values()}),
                        package_dependencies=["comfyui", "kjnodes", "rgthree"],
                        recommended={"megapixels": .33 if profile == "8" and family == "h3" else .66 if family == "h3" else 1,
                                     "seconds": 5 if family == "h3" else None},
                        extension_available=family == "h3" and profile != "8")
            if family == "h3":
                spec["evidence"].append("h3_8gb" if profile == "8" else "h3_16gb" if profile == "16" else "h3")
                if profile == "8": spec["launch_requirements"] = {"include": ["--disable-pinned-memory"], "exclude": ["--use-sage-attention", "--use-flash-attention", "--fp16-unet"], "offload": "ComfyUI dynamic memory"}
            if family == "kromoss": spec["evidence_note"] = "Memory strategy inherited from MOSS; the author documents identical architecture/encoder/VAE. Not a separate 16 GB Kromoss benchmark."
        catalog["workflows"].append(item)
    catalog["addons"] = {
        "private_conditioning": {"families": ["ideogram", "krea2"], "nodes": ["ConditioningKrea2Rebalance", "LoraLoaderModelOnly"], "packages": ["comfyui", "rebalance"], "enabled_by": "private_mode", "implementation": "PrivateConditioning.java", "evidence": "private_conditioning",
                                 "variants": {"ideogram": {"node": "LoraLoaderModelOnly", "model_dependencies": [dependency("loras", "ideogram4/ideogramSnofsV1.safetensors")], "strength_model": 1.0, "branches": ["model", "model_negative"], "evidence": "ideogram_private_adapter"}, "krea2": {"node": "ConditioningKrea2Rebalance", "model_dependencies": [], "multiplier": 1.0}},
                                 "note": "Snofs adapter for Ideogram and per-layer conditioning adjustment for Krea. Not a guaranteed removal of every model-level refusal. SFW graphs remain unchanged."},
        "boxes": {"ideogram": "Ideogram4PromptBuilderKJ", "moss": ["MOSSImageLayoutPrompt"], "coordinate_system": "normalized xywh in app; compiled by node to model format"},
        "video_extension": {"nodes": ["MomentosVideoRange", "MiniMaxH3AddGuide", "MomentosVideoExtensionMerge"], "packages": ["comfyui", "momentos"],
                            "trim_seconds": {"min": 1, "max": 120}, "context_frames": 22, "fps": 24, "source_preserved": True, "evidence": "h3_extension"},
    }
    for path in sorted(args.export_dir.glob("minimax_h3_extend_*.json")):
        graph=json.loads(path.read_text());raw=(json.dumps(graph,ensure_ascii=False,indent=2)+"\n").encode();(output/path.name).write_bytes(raw)
        addon=catalog["addons"]["video_extension"];addon.setdefault("variants",[]).append({"graph":"profiles/"+path.name,"profile":path.stem.rsplit("_",1)[1],"turbo":"_turbo_" in path.name,"sha256":hashlib.sha256(raw).hexdigest(),"model_dependencies":[dependency(f,n) for f,n in model_inputs(graph)],"node_types":sorted({n["class_type"] for n in graph.values()}),"input_binding":{"node":"momentos_extend_source","video":"video","start_ms":"start_ms","end_ms":"end_ms"}})
    for path in sorted(args.export_dir.glob("*_boxes.json")):
        graph=json.loads(path.read_text());raw=(json.dumps(graph,ensure_ascii=False,indent=2)+"\n").encode();(output/path.name).write_bytes(raw)
        catalog["addons"]["boxes"].setdefault("examples",[]).append({"graph":"profiles/"+path.name,"sha256":hashlib.sha256(raw).hexdigest(),"model_dependencies":[dependency(f,n) for f,n in model_inputs(graph)]})
    catalog["addons"]["media_operations"]={"catalog":"media-operations.json","operations":["image_edit","image_upscale","video_upscale"]}
    private_adapter = models["loras/ideogram4/ideogramSnofsV1.safetensors"]
    private_adapter["private_mode_only"] = True
    private_adapter["license"] = "Verify original author terms before redistribution; mirror metadata is not authoritative"
    (ASSETS / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Packaged", sum(v["available"] for v in variants), "graphs and", len(models), "pinned model artifacts; no weights downloaded")


if __name__ == "__main__": main()
