"""CPU integration test for real trimming, context extraction and joining, without inference."""
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

SERVER = Path(__file__).resolve().parents[1] / "server/momentos_workflows/__init__.py"
spec = importlib.util.spec_from_file_location("momentos_workflows_test", SERVER)
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)


class VideoExtensionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory(prefix="momentos-extension-")
        cls.root = Path(cls.tmp.name)
        module.folder_paths.set_input_directory(str(cls.root / "input"))
        module.folder_paths.set_temp_directory(str(cls.root / "temp"))
        (cls.root / "input").mkdir()
        cls.source = cls.root / "input/source.mp4"
        module.ffmpeg(["-f", "lavfi", "-i", "testsrc2=s=192x320:r=24:d=4",
                       "-f", "lavfi", "-i", "sine=frequency=440:duration=4:sample_rate=48000",
                       "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", str(cls.source)])
        cls.original = hashlib.sha256(cls.source.read_bytes()).hexdigest()

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def test_trim_and_join(self):
        frames, audio, info = module.MomentosVideoRange().execute("source.mp4", 1000, 3500, 192, 320)
        self.assertEqual(tuple(frames.shape), (22, 320, 192, 3))
        self.assertEqual(audio["sample_rate"], 48000)
        self.assertEqual(audio["waveform"].shape[:2], (1, 2))
        self.assertAlmostEqual(audio["waveform"].shape[-1] / 48000, 22/24, delta=.03)
        self.assertAlmostEqual(json.loads(info)["duration"], 2.5, delta=1/24)
        # A real encoded clip substitutes for the model's generated VIDEO output.
        result, = module.MomentosVideoExtensionMerge().execute(module.InputImpl.VideoFromFile(str(self.source)), info)
        destination = self.root / "joined.mp4"
        result.save_to(str(destination))
        duration, width, height, sound = module.probe(destination)
        self.assertEqual((width, height, sound), (192, 320, True))
        self.assertAlmostEqual(duration, 2.5 + 4 - 22/24, delta=.1)
        self.assertEqual(hashlib.sha256(self.source.read_bytes()).hexdigest(), self.original)

    def test_silent_source_and_limits(self):
        silent = self.root / "input/silent.mp4"
        module.ffmpeg(["-i", str(self.source), "-an", "-c:v", "copy", str(silent)])
        _, audio, _ = module.MomentosVideoRange().execute("silent.mp4", 0, 1500, 192, 320)
        self.assertLess(float(audio["waveform"].abs().max()), .001)
        for start, end in [(0, 500), (3000, 5000), (-1000, 2000)]:
            with self.assertRaises(ValueError): module.validate_range(4, start, end, 0, 0, 1, 1)
        with self.assertRaises(ValueError): module.input_path("../source.mp4")
        with self.assertRaises(ValueError): module.validate_range(4, 0, 2000, .5, 0, 1, 1)

    def test_layout_bridge(self):
        item = {"type": "obj", "desc": "Blue boat", "bbox": [200, 100, 600, 400]}
        self.assertEqual(module.MomentosLayoutElements().execute(json.dumps([item]))[0], [item])
        with self.assertRaises(ValueError): module.MomentosLayoutElements().execute('[{"bbox":[0,0,0,100]}]')


if __name__ == "__main__":
    unittest.main()
