"""Pure output contracts; no model download, no personal media or Comfy process."""
import base64
import importlib.util
import io
import hashlib
import json
import tempfile
from pathlib import Path
import unittest

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location("visual_extract", Path(__file__).parents[1] / "server/momentos_workflows/visual_extract.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


spec = importlib.util.spec_from_file_location("florence_extract", Path(__file__).parents[1] / "server/momentos_workflows/florence_extract.py")
florence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(florence)


class VisualExtractionTest(unittest.TestCase):
    def test_click_validation(self):
        for value in ([], [{"x": 1.1, "y": .5, "label": 1}], [{"x": .5, "y": .5, "label": 0}],
                      [{"x": float("nan"), "y": .5, "label": 1}], [{"x": True, "y": .5, "label": 1}],
                      [{"x": .5, "y": .5, "label": 1}] * 17):
            with self.assertRaises(ValueError):
                module.validate_points(value)
        self.assertEqual(len(module.validate_points('[{"x":0,"y":1,"label":1}]')), 1)

    def test_selection_obeys_positive_and_negative_clicks(self):
        small = np.zeros((10, 20), dtype=bool)
        small[2:8, 3:11] = True
        large = np.ones_like(small)
        points = [{"x": .3, "y": .4, "label": 1}, {"x": .9, "y": .9, "label": 0}]
        result = module.mask_result([large, small], [.99, .5], points, 20, 10)
        self.assertEqual(result["bounds"], [3, 2, 11, 8])
        mask = np.asarray(Image.open(io.BytesIO(base64.b64decode(result["mask"]))))
        self.assertTrue(np.array_equal(mask > 0, small))
        with self.assertRaises(ValueError):
            module.mask_result([large], [1], points, 20, 10)

    def test_composition_keeps_detailed_captions_and_nested_objects(self):
        result = florence.composition_result({
            "bboxes": [[0, 10, 100, 100], [-1, 9, 101, 101], [20, 20, 60, 80]],
            "labels": ["a wooden chair", "a brown wooden chair beside the table", "a person wearing a blue coat"]}, 200, 100)
        self.assertEqual(result["aspect"], 2)
        self.assertEqual(result["model"], "Florence-2-base-ft")
        self.assertEqual(len(result["boxes"]), 2)
        self.assertIn("a brown wooden chair beside the table", [b["desc"] for b in result["boxes"]])
        for box in result["boxes"]:
            self.assertGreater(box["w"], 0)
            self.assertLessEqual(box["x"] + box["w"], 1)
            self.assertLessEqual(box["y"] + box["h"], 1)
            self.assertNotIn("confidence", box)  # Florence does not supply calibrated detection confidence.

    def test_composition_rejects_bad_or_missing_model_output(self):
        for value in ({}, {"bboxes": [[0,0,2,2]], "labels": []},
                      {"bboxes": [[80,0,20,30]], "labels": ["object"]},
                      {"bboxes": [[0,0,float("nan"),30]], "labels": ["object"]},
                      {"bboxes": [[0,0,20,30]], "labels": ["<pad>"]}):
            with self.assertRaises(ValueError):
                florence.composition_result(value, 100, 100)

    def test_composition_limit_preserves_main_elements_and_reports_truncation(self):
        bounds = [[i*10, 0, i*10+8, 10] for i in range(20)] + [[0, 10, 200, 20]]
        labels = ["small object "+str(i) for i in range(20)] + ["large table"]
        result = florence.composition_result({"bboxes": bounds, "labels": labels}, 200, 20)
        self.assertEqual(len(result["boxes"]), 12)
        self.assertTrue(result["truncated"])
        self.assertIn("large table", [b["desc"] for b in result["boxes"]])

    def test_config_and_tokenizer_are_verified_as_well_as_weights(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); path = root / "tokenizer.json"; path.write_bytes(b"original")
            entry = {"name": path.name, "bytes": 8, "sha256": hashlib.sha256(b"original").hexdigest()}
            self.assertEqual(len(florence.verify_assets(root, [entry])), 1)
            path.write_bytes(b"tampered")
            with self.assertRaisesRegex(ValueError, "checksum"):
                florence.verify_assets(root, [entry])
            with self.assertRaises(ValueError):
                florence.verify_assets(root, [dict(entry, name="../tokenizer.json")])

    def test_desktop_downloads_match_server_pins_and_model_paths(self):
        root = Path(__file__).parents[1]
        catalog = json.loads((root / "desktop/Bwdacc.Desktop/Payload/visual-models.json").read_text(encoding="utf-8"))
        manifest = json.loads((root / "server/momentos_workflows/florence_manifest.json").read_text(encoding="utf-8"))
        files = {f["destination"]: f for f in catalog["files"]}
        self.assertEqual(files["models/segmentation/"+module.MODEL_NAME]["sha256"], module.MODEL_SHA256)
        for entry in manifest["files"]:
            artifact = files["models/vision/florence-2-base-ft/"+entry["name"]]
            self.assertEqual(artifact["sha256"], entry["sha256"])
            self.assertEqual(artifact["bytes"], entry["bytes"])
            self.assertNotIn(".py", entry["name"])


if __name__ == "__main__":
    unittest.main()
