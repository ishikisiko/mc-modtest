"""Regression for world-space red death tint miscounted as a visible HUD."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock
from PIL import Image
from tools.bossbar_evidence import BossHudEvidence, cinnabar_pixels


class BossBarPixelEvidenceTests(unittest.TestCase):
    def test_platform_never_fills_when_chunk_readiness_times_out(self):
        evidence=BossHudEvidence.__new__(BossHudEvidence)
        evidence.rc=Mock(return_value='That position is not loaded')
        evidence.poll=Mock(side_effect=TimeoutError('chunks not ready'))
        with self.assertRaises(TimeoutError):evidence.prepare_platform()
        self.assertEqual([('forceload add 224 224 288 288',)], [c.args for c in evidence.rc.call_args_list])

    def test_platform_checks_every_chunk_and_requires_successful_fill(self):
        evidence=BossHudEvidence.__new__(BossHudEvidence)
        evidence.rc=Mock(side_effect=lambda command: 'Successfully filled 4225 block(s)' if command.startswith('fill ') else 'The time is 500')
        evidence.must=Mock()
        def poll(query,predicate,timeout,reason):
            self.assertFalse(predicate('That position is not loaded'))
            self.assertTrue(predicate(query()))
        evidence.poll=poll
        evidence.prepare_platform()
        calls=[c.args[0] for c in evidence.rc.call_args_list]
        self.assertEqual(25,calls[1].count('if loaded'))
        self.assertTrue(calls[2].startswith('fill '))
        self.assertTrue(evidence.must.call_args.args[1])

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
