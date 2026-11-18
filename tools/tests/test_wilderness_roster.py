import copy
import unittest
from tools.gen_wilderness_roster import catalogue,outputs,scaled_moves
from tools.validate_alchemy import check_rules
from tools.validate_custom_entities import check_beast_data
from tools.gen_wilderness_roster import ROOT
import json

class WildernessRosterTests(unittest.TestCase):
    def test_body_scale_preserves_combat_power_and_ranged_geometry(self):
        from tools.beastgen.roster import build_model
        for m in catalogue()['mobs']:
            moves=scaled_moves(m)
            for raw,move in zip(m['moves'],moves):
                self.assertEqual(raw['damage_multiplier'],move['damage_multiplier'])
                self.assertEqual(raw['lunge'],move['lunge'])
                factor=m['body_scale'] if raw['_mode']=='melee' else 1
                for field in ('forward','height'):
                    for a,b in zip(raw['hit'][field],move['hit'][field]):self.assertAlmostEqual(a*factor,b)
            if m['kind']!='人形':
                model=build_model(m['id'])
                self.assertEqual(model.scale,m['body_scale'])
                self.assertAlmostEqual(model.shadow_radius,m['dimensions']['shadow_radius']*m['body_scale'])
    def test_every_material_has_two_real_consumers(self):
        c=catalogue();res=ROOT/'src/main/resources';ar=json.loads((res/'data/yuanxian/alchemy/rules.json').read_text());fr=json.loads((res/'data/yuanxian/forging/rules.json').read_text())
        for mob in c['mobs']:
            material='yuanxian:'+mob['material_id']
            self.assertTrue(any(material in r['ingredients'] and r['id']==mob['pill_id'] for r in ar['recipes']))
            self.assertIn(material,fr['traits'][mob['trait_id']]['costs'])
    def test_unknown_material_cannot_enter_existing_furnace(self):
        data=json.loads((ROOT/'src/main/resources/data/yuanxian/alchemy/rules.json').read_text());self.assertFalse(check_rules(data))
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

    def test_common_materials_use_looting_without_a_quantity_limit(self):
        generated = outputs()
        tables = ROOT / 'src/main/resources/data/yuanxian/loot_table/entities'
        for mob in catalogue()['mobs']:
            with self.subTest(mob=mob['id']):
                table = json.loads(generated[tables / (mob['id'] + '.json')])
                entry = table['pools'][0]['entries'][0]
                self.assertEqual('yuanxian:' + mob['material_id'], entry['name'])
                set_count, looting = entry['functions']
                self.assertEqual(mob['drop_count'], [set_count['count']['min'], set_count['count']['max']])
                self.assertEqual({
                    'function': 'minecraft:enchanted_count_increase', 'enchantment': 'minecraft:looting',
                    'count': {'type': 'minecraft:uniform', 'min': 0, 'max': 1},
                }, looting)

if __name__=='__main__':unittest.main()
