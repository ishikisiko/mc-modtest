import copy
import json
import unittest
from pathlib import Path

from tools.beast_materials.generate import ROOT, catalogue, outputs, variants
from tools.drop_workbench.rules import Invalid, validate


class BeastMaterialsTest(unittest.TestCase):
    def test_effective_cartesian_product_excludes_qi_cores(self):
        rows = [v for v, *_ in variants(catalogue())]
        self.assertEqual(56, len(rows))
        self.assertEqual(56, len({v['id'] for v in rows}))
        self.assertEqual({'beast_hide': 20, 'beast_bone': 20, 'beast_core': 16},
                         {family: sum(v['family'] == family for v in rows)
                          for family in ('beast_hide', 'beast_bone', 'beast_core')})
        self.assertFalse(any(v['family'] == 'beast_core' and v['realm'].endswith(':qi_refining') for v in rows))

    def test_approved_realm_and_rarity_prices(self):
        prices = {v['id'].split(':')[1]: v['price'] for v, *_ in variants(catalogue())}
        self.assertEqual(2, prices['beast_hide_qi_refining_common'])
        self.assertEqual(12, prices['beast_bone_foundation_establishment_common'])
        self.assertEqual(40, prices['beast_core_foundation_establishment_common'])
        self.assertEqual(800, prices['beast_core_golden_core_rare'])
        self.assertEqual(25_600, prices['beast_core_spirit_transformation_legendary'])

    def test_generator_outputs_are_current_and_models_have_their_textures(self):
        produced = outputs()
        for path, blob in produced.items():
            self.assertEqual(blob, path.read_bytes(), str(path.relative_to(ROOT)))
            if '/models/item/' in str(path):
                texture = json.loads(blob)['textures']['layer0'].split(':')[1]
                self.assertIn(ROOT / f'src/main/resources/assets/yuanxian/textures/{texture}.png', produced)

    def test_only_real_beasts_get_body_materials_and_lantern_only_gets_cores(self):
        rules = json.loads((ROOT / 'src/main/resources/data/yuanxian/drops/monster_drops.json').read_text())
        by_id = {v['id']: v for v in rules['additions']}
        self.assertEqual({'yuanxian:demon_wolf', 'yuanxian:thornback_badger', 'yuanxian:redplate_lizard',
                          'yuanxian:frostplume_pheasant', 'yuanxian:ember_toad'},
                         set(by_id['beast_body_materials']['when']['entities']))
        self.assertIn('yuanxian:lantern_sporeling', by_id['beast_cores']['when']['entities'])
        self.assertEqual({'realm': 'yuanxian:foundation_establishment', 'stage': 0},
                         by_id['beast_cores']['when']['min_realm'])
        for row in [by_id[name] for name in ('beast_body_materials', 'beast_cores', 'beast_boss_body', 'beast_boss_core')]:
            self.assertNotIn('player_kill', row['when'])
            self.assertTrue(set(row['when']['scenes']).isdisjoint({'quest_realm', 'sect_trial', 'aptitude_trial'}))

    def test_workbench_validates_all_new_fields_and_preserves_them(self):
        data = json.loads((ROOT / 'src/main/resources/data/yuanxian/drops/monster_drops.json').read_text())
        rows = [v for v, *_ in variants(catalogue())]
        ids = set(data['entities'])
        for rule in data['overrides'] + data['additions']:
            ids.update(rule['when'].get('entities', []))
        items = {v['id']: {'id': v['id'], 'beast_material': v, 'qualities': [], 'rarities': []} for v in rows}
        for policy in [data['defaults'], *data['entities'].values(), *data['overrides'], *data['additions']]:
            for drop in policy['drops']:
                items.setdefault(drop['item'], {'id': drop['item'], 'qualities': [], 'rarities': ['common']})
        cat = {'items': list(items.values()), 'entities': [{'id': id} for id in ids],
               'regions': [{'id': 'donghuang'}], 'realms': [{'id': 'yuanxian:foundation_establishment', 'order': 1, 'stages': 3}]}
        self.assertEqual(data, validate(copy.deepcopy(data), cat))
        for weights in ([0, 0, 0, 0], [1, 2, 3], [1, 2, 3, -1], [1, 2, 3, True], [1, 2, 3, 0.5]):
            bad = copy.deepcopy(data)
            bad['additions'][1]['drops'][0]['rarity_weights'] = weights
            with self.assertRaises(Invalid):
                validate(bad, cat)


if __name__ == '__main__':
    unittest.main()
