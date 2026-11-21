from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.drop_workbench.realm_drops import compile_plan, encoded_config
from tools.drop_workbench.rules import Invalid
from tools.drop_workbench.server import Conflict, SOURCE, Store


FOUNDATION = 'yuanxian:foundation_establishment'
GOLDEN = 'yuanxian:golden_core'
NASCENT = 'yuanxian:nascent_soul'
LIZARD = 'yuanxian:redplate_lizard'
WOLF = 'yuanxian:demon_wolf'
CORE = 'yuanxian:beast_core_foundation_establishment_common'
SCALE = 'yuanxian:redplate_scale'


class RealmDropTest(unittest.TestCase):
    def setUp(self):
        self.catalogue = {
            'items': [{'id': CORE, 'name': '筑基妖丹', 'beast_material': {'family': 'beast_core'}},
                      {'id': SCALE, 'name': '赤甲片'}],
            'entities': [{'id': LIZARD, 'name': '赤甲蜥'}, {'id': WOLF, 'name': '妖狼'}],
            'regions': [],
            'realms': [{'id': id, 'name': name, 'order': order, 'stages': 3}
                       for order, (id, name) in enumerate(((FOUNDATION, '筑基'), (GOLDEN, '金丹'), (NASCENT, '元婴')), 1)],
        }
        old_core = {'item': CORE, 'min': 1, 'max': 1, 'chance': .2,
                    'factory': 'beast_material', 'rarity_weights': [8000, 1600, 350, 50]}
        self.baseline = {
            'schema': 1, 'defaults': {'mode': 'inherit', 'drops': []}, 'entities': {}, 'overrides': [],
            'additions': [
                {'id': 'beast_cores', 'when': {'entities': [LIZARD, WOLF]}, 'drops': [old_core]},
                {'id': 'keep_this', 'when': {'entities': [LIZARD]},
                 'drops': [{'item': SCALE, 'min': 2, 'max': 3, 'chance': 1}]},
            ],
        }
        self.blueprint = {
            'id': 'test_cores', 'realms': [FOUNDATION, GOLDEN, NASCENT],
            'when': {'scenes': ['normal']}, 'replace_additions': ['beast_cores'],
            'template': {'core': {**old_core, 'chance': {'base': .2, 'per_realm': .05}}},
            'entities': {LIZARD: {'drops': {'core': {'by_realm': {GOLDEN: {'chance': .4}}}}}, WOLF: {}},
        }

    def compile(self):
        return compile_plan(self.baseline, self.blueprint, self.catalogue)

    def test_shared_formula_and_entity_exception_generate_exclusive_rows(self):
        original = copy.deepcopy(self.baseline)
        plan = self.compile()
        self.assertEqual(original, self.baseline)
        self.assertEqual(['beast_cores'], plan.replaced)
        self.assertEqual(original['additions'][1], plan.config['additions'][0])
        self.assertEqual(4, len(plan.additions))

        for realm, odds in ((FOUNDATION, (.2, .2)), (GOLDEN, (.4, .25)), (NASCENT, (.3, .3))):
            for actor, chance in zip((LIZARD, WOLF), odds, strict=True):
                matched = [entry for entry in plan.additions if actor in entry['when']['entities']
                           and entry['when']['min_realm']['realm'] == realm]
                self.assertEqual(1, len(matched))
                self.assertEqual({'realm': realm, 'stage': 0}, matched[0]['when']['min_realm'])
                self.assertEqual({'realm': realm, 'stage': 2}, matched[0]['when']['max_realm'])
                self.assertEqual(['normal'], matched[0]['when']['scenes'])
                self.assertEqual(1, len(matched[0]['drops']))
                self.assertEqual(chance, matched[0]['drops'][0]['chance'])
                self.assertEqual('beast_material', matched[0]['drops'][0]['factory'])

    def test_individual_drop_and_tier_scope(self):
        self.blueprint['entities'][LIZARD]['realms'] = [GOLDEN, NASCENT]
        self.blueprint['entities'][LIZARD]['drops']['scale'] = {
            'by_realm': {NASCENT: {'item': SCALE, 'min': 1, 'max': 4, 'chance': .1}},
        }
        self.blueprint['template']['core']['max'] = {'base': 1, 'per_realm': 1}
        rows = [row for row in self.compile().rows if row['entity_id'] == LIZARD]
        self.assertEqual([('金丹', CORE, 2), ('元婴', CORE, 3), ('元婴', SCALE, 4)],
                         [(r['realm'], r['drop']['item'], r['drop']['max']) for r in rows])
        self.assertEqual('单独配置', rows[-1]['source'])

    def test_disabled_realm_and_fixed_count(self):
        self.blueprint['entities'][LIZARD]['drops']['core'] = {'by_realm': {GOLDEN: None}}
        rows = [r for r in self.compile().rows if r['entity_id'] == LIZARD]
        self.assertEqual(['筑基', '元婴'], [row['realm'] for row in rows])
        self.assertTrue(all(row['drop']['min'] == row['drop']['max'] == 1 for row in rows))

    def test_formula_rejects_application_outside_the_table_bounds(self):
        self.blueprint['template']['core']['chance']['per_realm'] = .6
        with self.assertRaisesRegex(Invalid, '计算结果应在'):
            self.compile()

    def test_regeneration_preserves_workbench_edits_to_generated_ids(self):
        self.blueprint['replace_additions'] = []
        self.baseline = self.compile().config
        again = self.compile()
        self.assertEqual(self.baseline, again.config)
        self.baseline['additions'][-1]['drops'][0]['chance'] = .01
        with self.assertRaisesRegex(Invalid, '现有规则不同'):
            self.compile()

    def test_apply_honors_the_preview_revision(self):
        plan = self.compile()
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            table = root / SOURCE
            table.parent.mkdir(parents=True)
            table.write_text(encoded_config(self.baseline), encoding='utf-8')
            store = Store(root, self.catalogue, root / 'state')
            before = store.read()
            changed = copy.deepcopy(before['config'])
            changed['additions'][1]['drops'][0]['chance'] = .8
            after = store.save(changed, before['revision'])
            with self.assertRaises(Conflict):
                store.save(plan.config, before['revision'])
            self.assertEqual(after['config'], json.loads(table.read_text()))


if __name__ == '__main__':
    unittest.main()
