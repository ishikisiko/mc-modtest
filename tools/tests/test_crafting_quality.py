"""验证共用品质配置、跨品阶归一化及含失败的五段概率。"""
import copy
import math
import unittest
from tools import crafting_quality as quality


class CraftingQualityTest(unittest.TestCase):
    def setUp(self):
        self.rules = quality.load()
        self.qi = 'myvillage:qi_refining'
        self.foundation = 'myvillage:foundation_establishment'

    def test_confirmed_cutoffs_and_difficulty_shift_every_boundary(self):
        for tier in self.rules['tiers']:
            base = [20, 40, 70, 90] if tier['id'] == self.qi else [-20, 20, 60, 80]
            for difficulty in range(4):
                self.assertEqual([v + difficulty * 10 for v in base],
                                 quality.thresholds(self.rules, tier['id'], difficulty))

    def test_five_probabilities_cover_the_whole_distribution(self):
        for tier in self.rules['tiers']:
            for aptitude in (0, 20, 60, 100, 160, 300, 1000, 2_147_483_647):
                for difficulty in range(4):
                    result = quality.probabilities(self.rules, aptitude, tier['id'], difficulty)
                    self.assertEqual(5, len(result))
                    self.assertAlmostEqual(1, sum(result), places=14)
                    self.assertTrue(all(0 <= p <= 1 for p in result))
        self.assertEqual(.5, quality.probabilities(self.rules, 20, self.qi, 0)[0])
        self.assertAlmostEqual(.0477903522728147, quality.probabilities(self.rules, 100, self.foundation, 0)[0])

    def test_equal_relative_aptitude_has_equal_probabilities_in_higher_tiers(self):
        expected = quality.probabilities(self.rules, 160, self.foundation, 2)
        for tier in self.rules['tiers'][1:]:
            self.assertEqual(expected, quality.probabilities(self.rules, tier['baseline'] + 60, tier['id'], 2))
        self.assertTrue(all(p > 0 for p in quality.probabilities(self.rules, 60, self.qi, 0)))
        self.assertEqual(1, quality.probabilities(self.rules, 1000, self.qi, 0)[4])

    def test_normal_parameters_and_thresholds_remain_configurable(self):
        data = copy.deepcopy(self.rules)
        data['standard_deviation'] = 8
        data['tiers'][0].update(baseline=50, span=200, thresholds=[-10, 15, 55, 85])
        quality.parse(data)
        expected = .5 * math.erfc(-(-10 - 25) / 8 / math.sqrt(2))
        self.assertAlmostEqual(expected, quality.probabilities(data, 100, self.qi, 0)[0])

    def test_invalid_schema_and_ambiguous_json_are_rejected(self):
        mutations = [lambda d: d.update(version=True), lambda d: d.update(extra=1),
                     lambda d: d['initial_roll'].update(max=0.5), lambda d: d['initial_roll'].update(min=61),
                     lambda d: d['tiers'].append(copy.deepcopy(d['tiers'][0])),
                     lambda d: d['tiers'][0].update(span=0),
                     lambda d: d['tiers'][0].update(thresholds=[20, 40, 40, 90])]
        for invalid in ('20', True, None, float('nan'), float('inf'), 10 ** 400):
            mutations.append(lambda d, value=invalid: d['tiers'][0]['thresholds'].__setitem__(0, value))
        for invalid in ('12', True, None, 0, -1, float('nan'), float('inf')):
            mutations.append(lambda d, value=invalid: d.update(standard_deviation=value))
        for mutation in mutations:
            data = copy.deepcopy(self.rules)
            mutation(data)
            with self.assertRaises(ValueError):
                quality.parse(data)
        for raw in ('{"version":1,"version":1}', '{"x":NaN}'):
            with self.assertRaises(ValueError):
                quality.load_json(raw)

    def test_invalid_runtime_inputs_are_rejected(self):
        for difficulty in (-1, 4, True, 1.5):
            with self.assertRaises(ValueError):
                quality.probabilities(self.rules, 60, self.qi, difficulty)
        for aptitude in (-1, True, 60.5, 2_147_483_648):
            with self.assertRaises(ValueError):
                quality.probabilities(self.rules, aptitude, self.qi, 0)
