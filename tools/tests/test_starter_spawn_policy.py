"""出生省城排除规则只改变出生候选，不删除城市或重写地区强度。"""
import copy
import json
from pathlib import Path
import unittest

from tools.province_capitals.game.compile_v11 import apply_spawn_policy

ROOT=Path(__file__).resolve().parents[2]


class StarterSpawnPolicyTests(unittest.TestCase):
    def setUp(self):
        self.base={'profiles':[{'id':'donghai','role':'peripheral','tier':1},
                               {'id':'beiyuan','role':'peripheral','tier':2},
                               {'id':'moyu','role':'walled','tier':3}],
                   'spawn':{'minimum_y':72},'capitals':{'bundles':['sea','land','walled']}}

    def test_authored_policy_removes_the_sea_city_only_from_spawn_candidates(self):
        source=json.loads((ROOT/'blueprints/worldplan/rules_v11.json').read_text())
        previous=copy.deepcopy(self.base)
        apply_spawn_policy(self.base,source['spawn'])
        self.assertEqual(['donghai'],self.base['spawn'].pop('excluded_regions'))
        self.assertEqual(previous,self.base)

    def test_invalid_policies_fail_without_mutating_frozen_rules(self):
        for policy in [None,{}, {'excluded_regions':['missing']}, {'excluded_regions':['donghai','donghai']},
                       {'excluded_regions':['donghai','beiyuan']}, {'excluded_regions':[1]},
                       {'excluded_regions':['donghai'],'fallback':'moyu'}]:
            with self.subTest(policy=policy):
                value=copy.deepcopy(self.base)
                with self.assertRaises(ValueError):apply_spawn_policy(value,policy)
                self.assertEqual(self.base,value)


if __name__=='__main__':unittest.main()
