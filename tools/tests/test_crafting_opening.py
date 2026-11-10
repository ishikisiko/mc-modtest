"""独立哈希参考向量、权重与开局配置拒绝规则。"""
import copy
import itertools
import unittest
from tools import crafting_opening as opening


class CraftingOpeningTest(unittest.TestCase):
    def setUp(self):
        self.rules = opening.load()
        self.player = '00112233-4455-6677-8899-aabbccddeeff'
        self.fates = ['myvillage:robust', 'myvillage:lightfoot', 'myvillage:bright']

    def test_reference_vectors_and_selection_order(self):
        self.assertEqual([5,0,-5], [opening.delta(self.rules,self.player,'alchemy',f) for f in self.fates])
        self.assertEqual([-5,0,0], [opening.delta(self.rules,self.player,'forging',f) for f in self.fates])
        for order in itertools.permutations(self.fates):
            self.assertEqual(30, opening.score(self.rules,self.player,'alchemy',order))
            self.assertEqual(25, opening.score(self.rules,self.player,'forging',order))
            self.assertEqual(60, opening.score(self.rules,self.player,'alchemy',order,60))

    def test_weights_and_only_replaced_fate_contribution_changes(self):
        self.assertEqual([(-5,15),(5,15),(-10,5),(10,5),(0,60)],
                         [(r['value'],r['weight']) for r in self.rules['weighted_deltas']])
        changed = self.fates[:2] + ['myvillage:clod']
        for profession in ('alchemy','forging'):
            before = opening.score(self.rules,self.player,profession,self.fates)
            expected = before - opening.delta(self.rules,self.player,profession,self.fates[2]) + opening.delta(self.rules,self.player,profession,changed[2])
            self.assertEqual(expected, opening.score(self.rules,self.player,profession,changed))

    def test_invalid_config_and_unconfirmed_choices_are_rejected(self):
        for mutation in (lambda d:d.update(version=True),lambda d:d.update(base=29),lambda d:d.update(base=30.5),
                         lambda d:d.update(extra=0),lambda d:d['weighted_deltas'][0].update(weight=0),
                         lambda d:d['weighted_deltas'].append(dict(d['weighted_deltas'][0]))):
            data=copy.deepcopy(self.rules);mutation(data)
            with self.assertRaises(ValueError):opening.parse(data)
        for selected in ([],self.fates[:2],[self.fates[0]]*3,['bad']+self.fates[:2]):
            with self.assertRaises(ValueError):opening.score(self.rules,self.player,'alchemy',selected)
        for override in (-1,60.5,True):
            with self.assertRaises(ValueError):opening.score(self.rules,self.player,'alchemy',self.fates,override)
        with self.assertRaises(ValueError):opening.score(self.rules,self.player,'talisman',self.fates,60)
