import hashlib
import json
from pathlib import Path, PurePosixPath
import unittest

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/assets/workflows"
CATALOG = json.loads((ROOT / "catalog.json").read_text(encoding="utf-8"))


class WorkflowCatalogTest(unittest.TestCase):
    def test_matrix_and_hashes(self):
        enabled = 0
        for entry in CATALOG["workflows"]:
            self.assertEqual(set(entry["profiles"]), {"24plus", "24", "16", "8"})
            for profile, variant in entry["profiles"].items():
                if variant["status"] == "unavailable":
                    self.assertTrue(variant["reason"])
                    self.assertNotIn("graph", variant)
                    continue
                enabled += 1
                self.verify_graph(variant)
        self.assertEqual(enabled, 24)

    def test_turbotime_is_a_real_adapter_workflow(self):
        entry = next(w for w in CATALOG["workflows"] if w["id"] == "ideogram4_turbotime_api")
        for profile in ("24plus", "24"):
            variant = entry["profiles"][profile]
            graph = json.loads((ROOT / variant["graph"]).read_text(encoding="utf-8"))
            self.assertEqual(graph["9"]["inputs"]["steps"], 8)
            self.assertEqual(graph["10"]["class_type"], "BasicGuider")
            self.assertEqual(graph["14"]["inputs"]["strength_model"], 1.0)
            self.assertIn("loras/ideogram4/ideogram_4_turbotime_v1.safetensors", variant["model_dependencies"])
            self.assertFalse(any("unconditional" in str(n["inputs"]) for n in graph.values()))
        for profile in ("16", "8"):
            self.assertEqual(entry["profiles"][profile]["status"], "unavailable")

    def verify_graph(self, variant):
        path = (ROOT / variant["graph"]).resolve()
        self.assertTrue(path.is_relative_to(ROOT.resolve()))
        self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), variant["sha256"])
        graph = json.loads(path.read_text(encoding="utf-8"))
        self.assertTrue(any(n["class_type"] in ["SaveImage", "SaveVideo"] for n in graph.values()))
        for dependency in variant["model_dependencies"]:
            self.assertIn(dependency, CATALOG["models"])

    def test_dependencies_are_portable_and_pinned(self):
        for dependency in CATALOG["models"].values():
            self.assertEqual(len(dependency["sha256"]), 64)
            self.assertGreater(dependency["bytes"], 0)
            self.assertEqual(len(dependency["revision"]), 40)
            self.assertIn(dependency["revision"], dependency["url"])
            path = PurePosixPath(dependency["destination"])
            self.assertFalse(path.is_absolute())
            self.assertNotIn("..", path.parts)
            self.assertNotIn(":", str(path))
            self.assertTrue(str(path).startswith("models/"))

    def test_optional_graphs(self):
        extension = CATALOG["addons"]["video_extension"]
        self.assertEqual(len(extension["variants"]), 6)
        for variant in extension["variants"]:
            self.verify_graph(variant)
            self.assertNotEqual(variant["profile"], "8")
            graph = json.loads((ROOT / variant["graph"]).read_text(encoding="utf-8"))
            self.assertEqual(graph["17"]["inputs"]["video"][0], "momentos_extend_merge")
        for variant in CATALOG["addons"]["boxes"]["examples"]:
            self.verify_graph(variant)

    def test_media_operation_contracts(self):
        catalog = json.loads((ROOT / "media-operations.json").read_text(encoding="utf-8"))
        self.assertEqual(len(catalog["operations"]), 4)
        for entry in catalog["operations"]:
            path = ROOT / entry["base_graph"]
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), entry["sha256"])
            graph = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual(sorted({n["class_type"] for n in graph.values()}), entry["node_types"])
            for dependency in entry["model_dependencies"]:
                self.assertIn(dependency, catalog["models"])
            if entry["operation"] == "video_upscale":
                self.assertEqual(entry["profiles"]["8"], "unavailable")
                self.assertEqual(entry["profiles"]["16"], "unavailable")
                self.assertIn("BlockSparseAttention", entry["node_types"])
                self.assertEqual(graph["decode"]["inputs"]["vae"], ["decode_vae", 0])
                self.assertIn("int8", graph["decode_vae"]["inputs"]["vae_name"])
        for dependency in catalog["models"].values():
            self.assertEqual(len(dependency["sha256"]), 64)
            self.assertGreater(dependency["bytes"], 0)
            path = PurePosixPath(dependency["destination"])
            self.assertFalse(path.is_absolute())
            self.assertNotIn("..", path.parts)
            self.assertNotIn(":", str(path))
            if "url" in dependency:
                self.assertIn(dependency["revision"], dependency["url"])
            else:
                self.assertFalse(dependency["automatic_download"])
                self.assertEqual(dependency["install_state"], "manual_model_required")


if __name__ == "__main__": unittest.main()
