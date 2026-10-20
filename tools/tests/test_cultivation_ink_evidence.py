from pathlib import Path
import tempfile
import unittest

from PIL import Image
from tools.cultivation_ink_evidence import changed_pixels, figure_box, panel_bounds
from tools.modal_runner.remote import commands_for


class InkEvidenceTests(unittest.TestCase):
    def test_motion_crop_excludes_progress_readouts_and_footer(self):
        bounds = panel_bounds(960, 540)
        self.assertEqual((54, 48, 413), (bounds["body_x"], bounds["body_y"], bounds["body_w"]))
        self.assertEqual((116, 156, 462, 380), figure_box((960, 540)))
        self.assertGreater(bounds["action_y"] * 2, figure_box((960, 540))[3])
        with self.assertRaises(ValueError):
            figure_box((640, 480))

    def test_text_changes_outside_the_art_cannot_prove_flow(self):
        with tempfile.TemporaryDirectory() as directory:
            a, b = Path(directory) / "a.png", Path(directory) / "b.png"
            first = Image.new("RGB", (960, 540), (232, 226, 206))
            first.save(a)
            second = first.copy()
            second.paste((20, 60, 30), (650, 220, 740, 240))
            second.save(b)
            self.assertEqual(0, changed_pixels(a, b, figure_box((960, 540))))
            second.paste((20, 60, 30), (250, 250, 260, 260))
            second.save(b)
            self.assertEqual(100, changed_pixels(a, b, figure_box((960, 540))))

    def test_scoped_modal_job_preserves_packaging_and_runs_mapped_ui_collector(self):
        build = commands_for("cultivation-ink-build")
        self.assertIn("com.example.myvillage.client.cultivation.panel.*", build[1])
        self.assertNotIn("generateWorldPlanAssets", build[1])
        self.assertNotIn("generateExplorationGuide", build[1])
        self.assertEqual(build, commands_for("cultivation-ink")[:-1])
        self.assertEqual(["python3", "tools/cultivation_ink_evidence.py"], commands_for("cultivation-ink")[-1])


if __name__ == "__main__":
    unittest.main()
