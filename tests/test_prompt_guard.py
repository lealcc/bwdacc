import importlib.util
from pathlib import Path
import unittest

source = Path(__file__).parents[1] / "server/momentos_prompt_guard/__init__.py"
spec = importlib.util.spec_from_file_location("momentos_guard", source)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PromptGuardTest(unittest.TestCase):
    def setUp(self):
        self.guard = module.MomentosPromptGuard()
        self.body = "integrated_multimodal_description: [Shot 1] A blue paper boat.\noverall_soundscape: Water.\nnon_diegetic_music: N/A"

    def test_valid_text_video(self):
        self.assertEqual(self.guard.validate(self.body, "h3", 0, 0, 0), (self.body,))

    def test_missing_sections_and_empty_reasoning(self):
        for value in ("", "<think>unfinished", "[no text returned]", "integrated_multimodal_description: boat"):
            with self.assertRaises(ValueError):
                self.guard.validate(value, "h3", 0, 0, 0)

    def test_real_reference_count(self):
        value = self.body.replace("A blue paper boat.", "Animate <Picture 2>.")
        with self.assertRaises(ValueError):
            self.guard.validate(value, "h3", 1, 0, 0)
        self.assertEqual(self.guard.validate(value, "h3", 2, 0, 0), (value,))

    def test_reference_mode_sections(self):
        value = "subject_definitions: <Video 1> is the source.\nsummary: Change color.\nretention_analysis: Keep motion.\ndetailed_description: [Shot 1] Blue boat.\noverall_soundscape: Water.\nnon_diegetic_music: N/A"
        self.assertEqual(self.guard.validate(value, "h3_reference", 0, 1, 0), (value,))
        with self.assertRaises(ValueError):
            self.guard.validate(self.body, "h3_reference", 0, 1, 0)

    def test_image_edit_needs_no_video_fields(self):
        value = "Make the notebook blue. Keep the composition."
        self.assertEqual(self.guard.validate(value, "image", 1, 0, 0), (value,))


if __name__ == "__main__":
    unittest.main()
