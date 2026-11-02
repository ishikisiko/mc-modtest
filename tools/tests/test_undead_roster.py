import copy
import unittest
from tools.gen_undead_roster import catalogue,validate,beast_data
class UndeadRosterTest(unittest.TestCase):
    def test_whole_region_family_matrix(self):
        c=validate(catalogue());self.assertEqual(18,len(c['variants']))
        for v in c['variants']:
            d=beast_data(v,c);self.assertEqual(2,len(d['moves']))
            for original,move in zip(v['moves'],d['moves']):
                self.assertLess(move['turn_lock_tick'],original['strike_ticks'][0])
                self.assertEqual([original['strike_ticks'][0],original['strike_ticks'][-1]],move['active_ticks'])
                self.assertNotIn('projectile',move)
    def test_missing_region_and_duplicate_are_rejected(self):
        for mutate in [lambda c:c['variants'].pop(),lambda c:c['variants'].append(copy.deepcopy(c['variants'][0]))]:
            c=catalogue();mutate(c)
            with self.assertRaises(AssertionError):validate(c)
    def test_damage_during_windup_and_missing_recovery_are_rejected(self):
        c=catalogue();c['variants'][0]['moves'][0]['strike_ticks']=[1]
        with self.assertRaises(AssertionError):validate(c)
        c=catalogue();m=c['variants'][0]['moves'][0];m['total_ticks']=m['strike_ticks'][-1]+1
        with self.assertRaises(AssertionError):validate(c)
