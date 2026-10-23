import copy
import unittest
from tools.gen_wilderness_roster import catalogue,outputs
from tools.validate_alchemy import check_rules
from tools.validate_custom_entities import check_beast_data
from tools.gen_wilderness_roster import ROOT
import json

class WildernessRosterTests(unittest.TestCase):
    def test_every_material_has_two_real_consumers(self):
        c=catalogue();res=ROOT/'src/main/resources';ar=json.loads((res/'data/myvillage/alchemy/rules.json').read_text());fr=json.loads((res/'data/myvillage/forging/rules.json').read_text())
        for mob in c['mobs']:
            material='myvillage:'+mob['material_id']
            self.assertTrue(any(material in r['ingredients'] and r['id']==mob['pill_id'] for r in ar['recipes']))
            self.assertIn(material,fr['traits'][mob['trait_id']]['costs'])
    def test_unknown_material_cannot_enter_existing_furnace(self):
        data=json.loads((ROOT/'src/main/resources/data/myvillage/alchemy/rules.json').read_text());self.assertFalse(check_rules(data))
        changed=copy.deepcopy(data);changed['recipes'][-1]['ingredients'][0]='minecraft:dirt';self.assertTrue(check_rules(changed))
    def test_casts_have_dodge_window_and_recovery(self):
        for m in catalogue()['mobs']:
            for move in m['moves']:
                self.assertLess(move['turn_lock_tick'],move['active_ticks'][0])
                self.assertGreaterEqual(move['total_ticks']-move['active_ticks'][1],10)
                self.assertLessEqual(move['maximum_targets'],4)
    def test_generators_do_not_own_legacy_entity_art(self):
        paths=[str(p) for p in outputs()]
        self.assertFalse(any('/textures/entity/cultivator/' in p or '/textures/entity/demon_wolf/' in p for p in paths))
        self.assertFalse(any('/recipe/pill_' in p for p in paths),'Pills must be brewed, not crafted')

if __name__=='__main__':unittest.main()
