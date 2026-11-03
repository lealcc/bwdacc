"""Boundary validation without models or access to user media."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("lipsync", ROOT / "server/momentos_workflows/lipsync.py")
lipsync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lipsync)
connector_spec = importlib.util.spec_from_file_location("muse_connector", ROOT / "server/musetalk/connect_existing.py")
connector = importlib.util.module_from_spec(connector_spec)
connector_spec.loader.exec_module(connector)


class LipsyncTests(unittest.TestCase):
    def test_bounds_and_no_silent_video_loop(self):
        lipsync.validate("image", .2)
        lipsync.validate("video", 30, 30)
        for kind, seconds, source in [("image", float("nan"), None), ("image", 30.1, None),
                                      ("image", 0, None), ("bad", 2, None), ("video", 5, 3)]:
            with self.assertRaises(ValueError):
                lipsync.validate(kind, seconds, source)

    def test_missing_runtime_does_not_fall_back_to_system_python(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "não está instalado"):
                lipsync.runtime(directory)

    def test_download_manifest_is_bounded_pinned_and_unique(self):
        manifest = json.loads((ROOT / "server/musetalk/manifest.json").read_text())
        paths = set()
        for item in manifest["files"]:
            self.assertNotIn(item["path"], paths)
            paths.add(item["path"])
            self.assertNotIn("..", item["path"])
            self.assertFalse(Path(item["path"]).is_absolute())
            self.assertRegex(item["sha256"], "^[0-9a-f]{64}$")
            self.assertGreater(item["bytes"], 0)
            self.assertTrue(item["url"].startswith("https://"))
        self.assertIn("models/musetalkV15/unet_fp16.safetensors", paths)
        self.assertIn("licenses/FFMPEGA-GPL-3.0.txt", paths)

    def test_existing_comfy_connector_preserves_unowned_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            comfy, package = root / "ComfyUI", root / "bwdacc_musetalk"
            node = comfy / "custom_nodes/bwdacc_musetalk"
            node.mkdir(parents=True)
            package.mkdir()
            (package / "ready.json").write_text("{}")
            (comfy / "main.py").write_text("# existing ComfyUI")
            original = node / "__init__.py"
            original.write_text("# user-owned node")
            with self.assertRaisesRegex(ValueError, "unowned"):
                connector.connect(comfy, package)
            self.assertEqual(original.read_text(), "# user-owned node")

    def test_existing_comfy_connector_only_writes_optional_node(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            comfy, package = root / "ComfyUI", root / "bwdacc_musetalk"
            (comfy / "custom_nodes").mkdir(parents=True)
            package.mkdir()
            (package / "ready.json").write_text("{}")
            (comfy / "main.py").write_text("# untouched")
            connector.connect(comfy, package)
            connector.connect(comfy, package)
            self.assertEqual((comfy / "main.py").read_text(), "# untouched")
            node = comfy / "custom_nodes/bwdacc_musetalk"
            self.assertEqual(Path(json.loads((node / "package.json").read_text())["path"]), package.resolve())
            self.assertEqual((node / "lipsync.py").read_bytes(), (ROOT / "server/momentos_workflows/lipsync.py").read_bytes())


if __name__ == "__main__":
    unittest.main()
