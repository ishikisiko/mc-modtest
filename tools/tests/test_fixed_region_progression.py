"""并列地区档位必须完整覆盖清单；同档同阶位，跨档保持阶差约束。"""
import copy,json,unittest
from pathlib import Path
from tools.province_capitals.game.compile_v11 import apply_region_progression
ROOT=Path(__file__).resolve().parents[2]

class FixedRegionProgressionTests(unittest.TestCase):
    def setUp(self):
        self.policy=json.loads((ROOT/'blueprints/worldplan/rules_v11.json').read_text())['region_progression']
        self.base={'profiles':[json.loads(p.read_text())for p in sorted((ROOT/'src/main/resources/data/yuanxian/worldgen/region_profile').glob('*.json'))], 'tier_range':[1,20],'tier_step':[2,5]}
    def test_six_levels_have_the_exact_requested_membership(self):
        self.assertEqual([['donghuang'],['lingyue'],['beiyuan'],['donghai','ximo'],['nanjiang','moyu'],['zhongzhou']],self.policy['levels'])
        frozen=copy.deepcopy(self.base);apply_region_progression(frozen,self.policy)
        self.assertEqual(self.policy,frozen.pop('region_progression'));self.assertEqual(self.base,frozen)
    def test_rejects_missing_duplicate_unknown_split_and_reversed_levels_without_mutation(self):
        levels=self.policy['levels']
        policies=[None,{}, {'levels':[]},{'levels':levels[:-1]}, {'levels':levels[:-1]+[['moyu']]}, {'levels':[['unknown']]+levels[1:]}, {'levels':[levels[1],levels[0]]+levels[2:]}, {'levels':[[]]+levels}, {'levels':[['donghuang','lingyue']]+levels[2:]}, {'levels':levels,'order':[]}, {'levels':[[1]]+levels[1:]}]
        for policy in policies:
            with self.subTest(policy=policy):
                frozen=copy.deepcopy(self.base)
                with self.assertRaises(ValueError):apply_region_progression(frozen,policy)
                self.assertEqual(self.base,frozen)
    def test_equal_rank_and_maximum_step_are_enforced(self):
        frozen=copy.deepcopy(self.base);frozen['profiles'][next(i for i,p in enumerate(frozen['profiles'])if p['id']=='ximo')]['tier']=13
        with self.assertRaises(ValueError):apply_region_progression(frozen,self.policy)
        frozen=copy.deepcopy(self.base);frozen['tier_step']=[1,2]
        with self.assertRaises(ValueError):apply_region_progression(frozen,self.policy)
