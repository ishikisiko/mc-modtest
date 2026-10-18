import unittest
from pathlib import Path
from unittest.mock import Mock
from tools.worldplan.atlas_capture import capture_screen

class AtlasCaptureTest(unittest.TestCase):
    def test_menu_capture_uses_raw_stable_frames_without_disabling_game_input_guards(self):
        game=Mock(); game.ui_check=True
        game.stable_grab.return_value=(b'frame',{'stable':True})
        result=capture_screen(game,Path('menu.png'))
        game.stable_grab.assert_called_once_with(max_wait=10,settle=.5)
        game.save_png.assert_called_once_with(b'frame',Path('menu.png'))
        game.shot.assert_not_called()
        self.assertTrue(game.ui_check)
        self.assertEqual(result,{'file':'menu.png','stability':{'stable':True}})
