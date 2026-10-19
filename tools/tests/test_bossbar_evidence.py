"""Regression for world-space red death tint miscounted as a visible HUD."""
from pathlib import Path
import tempfile
import unittest
from PIL import Image
from tools.bossbar_evidence import cinnabar_pixels


class BossBarPixelEvidenceTests(unittest.TestCase):
    def test_hidden_hud_does_not_count_red_mob_below_the_hud_band(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'hidden.png'
            image=Image.new('RGB',(960,540),(140,170,220))
            image.paste((160,70,50),(580,130,710,270))
            image.save(path)
            self.assertEqual(0,cinnabar_pixels(path))
            self.assertGreater(cinnabar_pixels(path,expanded=True),0)

    def test_visible_bar_is_counted_independently_of_world_red(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'visible.png'
            image=Image.new('RGB',(960,540),(140,170,220))
            image.paste((160,70,50),(190,40,600,50))
            image.paste((160,70,50),(580,130,710,270))
            image.save(path)
            self.assertEqual(4100,cinnabar_pixels(path))


if __name__=='__main__':
    unittest.main()
