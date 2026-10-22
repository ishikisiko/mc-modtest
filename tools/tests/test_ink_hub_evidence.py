import unittest
from tools.ink_hub_evidence import bounds, tile_center, choose_mask, fate_state
from tools.combat_capture.session import SessionConfig
from tools.modal_runner.remote import commands_for


class InkHubEvidenceTests(unittest.TestCase):
    def test_default_false_fate_fields_are_optional_but_clock_is_required(self):
        state=fate_state('FATES {"revision":1,"now":200,"budget":8}\n')
        self.assertFalse(state['confirmed']);self.assertEqual([],state['selected'])
        self.assertTrue(fate_state('FATES {"confirmed":true,"now":201,"budget":8,"selected":["a"]}')['confirmed'])
        with self.assertRaises(ValueError):fate_state('FATES {"budget":8}')
    def test_gui_scales_use_actual_window_dimensions(self):
        wide=bounds((960,540));large=bounds((1920,1080));narrow=bounds((640,480))
        self.assertEqual((68,40,399,208),(wide['x'],wide['y'],wide['width'],wide['height']))
        self.assertEqual(2,wide['scale']);self.assertEqual(4,large['scale'])
        self.assertEqual(wide['width'],large['width']);self.assertEqual(239,narrow['width'])
        self.assertEqual(tile_center(wide,0)[0],tile_center(wide,4)[0])
        self.assertEqual(tile_center(narrow,0)[0],tile_center(narrow,3)[0])

    def test_fixture_does_not_confirm_an_overbudget_or_duplicate_group(self):
        state={'candidates':['a','b','c','d'],'budget':6}
        defs={'a':{'cost':5,'group':'a'},'b':{'cost':2,'group':'b'},'c':{'cost':2,'group':'c'},'d':{'cost':2,'group':'d'}}
        self.assertEqual(14,choose_mask(state,defs))
        defs['d']['group']='c'
        with self.assertRaises(ValueError):choose_mask(state,defs)

    def test_framebuffer_is_opt_in_and_bounded(self):
        default=SessionConfig('test')
        self.assertEqual((960,540),(default.framebuffer_width,default.framebuffer_height))
        SessionConfig('large',framebuffer_width=1920,framebuffer_height=1080)
        with self.assertRaises(ValueError):SessionConfig('huge',framebuffer_width=3840)

    def test_preset_is_scoped_to_panel_and_binding_rules(self):
        build=commands_for('ink-hub-build')
        self.assertEqual(build,commands_for('ink-hub')[:-1])
        self.assertIn('com.example.myvillage.client.cultivation.panel.*',build[2])
        self.assertNotIn('generateWorldPlanAssets',build[2])
        self.assertEqual(['python3','tools/ink_hub_evidence.py'],commands_for('ink-hub')[-1])
        self.assertNotIn('test',commands_for('ink-hub-play')[0])
        self.assertEqual(commands_for('ink-hub')[-1],commands_for('ink-hub-play')[-1])


if __name__=='__main__':unittest.main()
