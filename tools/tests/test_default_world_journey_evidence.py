import unittest
from tools.default_world_journey_evidence import latest_ui,find_button

class UiEvidenceTest(unittest.TestCase):
    def test_reads_latest_actual_snapshot(self):
        self.assertEqual(latest_ui('noise\n[INFO] WORLD_UI_EVIDENCE {"screen":"A"}\n[INFO] WORLD_UI_EVIDENCE {"screen":"B"}')['screen'],'B')
        self.assertIsNone(latest_ui('noise'))
    def test_refuses_ambiguous_or_offscreen_click(self):
        w={'text':'返回原世界','active':True,'x':10,'y':20,'width':80,'height':20}
        ui={'width':320,'height':240,'widgets':[w]}
        self.assertEqual(find_button(ui,'返回原世界',True),(50,30))
        ui['widgets'].append({**w,'x':100})
        with self.assertRaises(ValueError):find_button(ui,'返回原世界',True)
        ui['widgets']=[{**w,'y':250}]
        with self.assertRaises(ValueError):find_button(ui,'返回原世界',True)
