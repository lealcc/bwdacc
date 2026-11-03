import importlib.util
from pathlib import Path
import unittest
import torch

spec = importlib.util.spec_from_file_location("reference_focus", Path(__file__).resolve().parents[1] / "server/momentos_workflows/reference_focus.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ReferenceFocusTests(unittest.TestCase):
    def test_crop_and_background(self):
        image = torch.zeros(1, 100, 120, 3)
        image[..., 0] = 1
        mask = torch.zeros(1, 100, 120)
        mask[:, 30:70, 40:80] = 1
        result, = module.BwdaccReferenceFocus().focus(image, mask, torch.zeros(1, 64, 64))
        self.assertEqual(tuple(result.shape), (1, 56, 56, 3))
        self.assertTrue(torch.allclose(result[0, 0, 0], torch.full((3,), .5)))
        self.assertTrue(torch.equal(result[0, 20, 20], torch.tensor([1., 0., 0.])))
        self.assertEqual(image[0, 0, 0, 0], 1)  # source never modified

    def test_empty_mask_preserves_visible_source(self):
        image = torch.rand(1, 40, 60, 3)
        result, = module.BwdaccReferenceFocus().focus(image, torch.zeros(1, 40, 60), torch.zeros(1, 64, 64))
        self.assertTrue(torch.equal(result, image))

    def test_transparent_rgb_never_reaches_vlm(self):
        image = torch.ones(1, 40, 60, 3)
        result, = module.BwdaccReferenceFocus().focus(image, torch.ones(1, 40, 60), torch.ones(1, 40, 60))
        self.assertTrue(torch.all(result == .5))


if __name__ == "__main__":
    unittest.main()
