"""正式关卡方块导出必须能发现出生点占用、失去支撑和缺失光源。"""
from copy import deepcopy
import unittest
from tools.quest_realms.game import compile_map
from tools.quest_realms.game_audit import audit
from tools.quest_realms.model import read
from tools.buildgen.modset import load_modset

class QuestGameTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.maps={name:compile_map(name,read('runtime')) for name in read('runtime')['maps']}
    def test_both_exports_have_clear_routes_stands_and_light_sources(self):
        for name,model in self.maps.items():
            with self.subTest(map=name):
                result=audit(model);self.assertGreater(result['checks'],3000);self.assertEqual([],result['failures'])
                self.assertEqual([],load_modset('vanilla').palette_block_errors(model['palette']))
                self.assertTrue(any('stairs[' in state for state in model['palette']))
    def test_solid_block_at_a_guard_spawn_is_detected(self):
        model=deepcopy(self.maps['bandit_stronghold']);x,y,z=model['encounters'][0]['actors'][0]['pos']
        model['runs'].append([x,y,z,2,model['palette'].index('minecraft:stone')])
        self.assertTrue(any(f['kind']=='站立空间被占用' for f in audit(model)['failures']))
    def test_removing_spawn_support_is_detected(self):
        model=deepcopy(self.maps['beast_den']);x,y,z=model['encounters'][0]['actors'][0]['pos'];model['runs'].append([x,y-1,z,1,0])
        self.assertTrue(any(f['kind']=='没有足下支撑' for f in audit(model)['failures']))
    def test_missing_lighting_is_detected(self):
        model=deepcopy(self.maps['beast_den']);model['lighting_probes'].append([2,75,2])
        self.assertTrue(any(f['kind']=='检查点缺少直接补光' for f in audit(model)['failures']))

if __name__=='__main__':unittest.main()
