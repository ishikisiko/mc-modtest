"""飞剑专属资源：真实三维体积、升级来源与生成漂移。"""
import json
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from gen_flying_swords import model, outputs

class FlyingSwordAssetsTest(unittest.TestCase):
    def test_outputs_match_committed_resources(self):
        for path,data in outputs().items():
            self.assertEqual(data,path.read_bytes(),str(path))
    def test_every_piece_has_thickness_and_vanilla_legal_bounds(self):
        for azure in (False,True):
            parts=model(azure)['elements']
            self.assertGreaterEqual(len(parts),10)
            for part in parts:
                for a,b in zip(part['from'],part['to']):
                    self.assertGreater(b,a);self.assertGreaterEqual(a,-16);self.assertLessEqual(b,32)
            self.assertGreater(max(p['to'][1] for p in parts)-min(p['from'][1] for p in parts),40)
    def test_azure_has_separate_side_supports(self):
        basic=model(False)['elements'];azure=model(True)['elements']
        supports=[p for p in azure if 7<=p['from'][1]<=14 and p not in basic]
        self.assertTrue(any(p['to'][0]<6 for p in supports))
        self.assertTrue(any(p['from'][0]>10 for p in supports))
    def test_uv_samples_each_real_palette_band_without_black_padding(self):
        from gen_flying_swords import box, COLORS
        for index,name in enumerate(COLORS):
            uv=box([0,0,0],[1,1,1],name)['faces']['up']['uv']
            # Minecraft UV units are always 0..16, even on a 32px-wide texture.
            self.assertGreaterEqual(uv[0]*32/16,index*4)
            self.assertLess(uv[2]*32/16,index*4+4)
            self.assertLess(uv[2]*32/16,20)
    def test_presentation_keeps_motion_and_feedback_restrained(self):
        root=Path(__file__).resolve().parents[2]
        visual=json.loads((root/'tools/flying_sword/visuals.json').read_text())
        self.assertGreaterEqual(visual['boost_fov'],1)
        self.assertLessEqual(visual['boost_fov'],1.04)
        self.assertLessEqual(abs(visual['boost_lean_degrees']),5)
        self.assertLessEqual(visual['max_bank_degrees'],5)
        self.assertLessEqual(visual['wind_volume'],.15)
        self.assertGreater(visual['trail_interval_ticks'],0)
        self.assertGreater(visual['wind_interval_ticks'],0)
    def test_upgrade_consumes_existing_basic_sword(self):
        recipes={p.stem:json.loads(data) for p,data in outputs().items() if p.parent.name=='recipe'}
        upgrade=recipes['azure_cloud_flying_sword']
        self.assertIn({'item':'myvillage:rideable_flying_sword'},upgrade['key'].values())
        self.assertEqual(1,upgrade['result']['count'])
        self.assertNotIn({'item':'myvillage:azure_cloud_flying_sword'},recipes['rideable_flying_sword']['key'].values())

if __name__=='__main__':unittest.main()
