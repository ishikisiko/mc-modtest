"""冻结编译器拒绝缺州、重复、倒序和超阶差输入。"""
import copy
import json
from pathlib import Path
import unittest
from tools.province_capitals.game.compile_v11 import apply_region_progression

ROOT=Path(__file__).resolve().parents[2]

class FixedRegionProgressionTests(unittest.TestCase):
    def setUp(self):
        self.policy=json.loads((ROOT/'blueprints/worldplan/rules_v11.json').read_text())['region_progression']
        self.base={'profiles':[json.loads(p.read_text()) for p in sorted((ROOT/'src/main/resources/data/myvillage/worldgen/region_profile').glob('*.json'))],
                   'tier_range':[1,20],'tier_step':[2,5]}

    def test_authored_order_keeps_existing_profile_balances(self):
        frozen=copy.deepcopy(self.base);apply_region_progression(frozen,self.policy)
        self.assertEqual(self.policy,frozen.pop('region_progression'));self.assertEqual(self.base,frozen)

    def test_bad_orders_do_not_mutate_frozen_inputs(self):
        order=self.policy['order']
        for policy in [None,{}, {'order':[]},{'order':order[:-1]}, {'order':order[:-1]+[order[-2]]},
                       {'order':['unknown']+order[1:]}, {'order':order[1:]+order[:1]},
                       {'order':[order[1],order[0]]+order[2:]}, {'order':[1]+order[1:]},
                       {'order':order,'tiers':[1]}]:
            with self.subTest(policy=policy):
                frozen=copy.deepcopy(self.base)
                with self.assertRaises(ValueError):apply_region_progression(frozen,policy)
                self.assertEqual(self.base,frozen)
        frozen=copy.deepcopy(self.base);frozen['tier_step']=[1,2]
        with self.assertRaises(ValueError):apply_region_progression(frozen,self.policy)

if __name__=='__main__':unittest.main()
