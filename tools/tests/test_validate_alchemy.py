"""Mutation fixtures guard the authored alchemy boundary and packaged resource closure."""
from __future__ import annotations

import copy
import json
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

from tools import validate_alchemy as validator

ROOT = Path(__file__).resolve().parents[2]


class AlchemyRulesTest(unittest.TestCase):
    def setUp(self):
        self.rules = json.loads((ROOT / validator.RULES).read_text())

    def assert_code(self, code, errors):
        self.assertTrue(any(e.startswith(code + ':') for e in errors), errors)

    def test_shipped_rules(self):
        self.assertEqual([], validator.check_rules(self.rules))

    def test_unknown_fields_missing_effects_and_bad_versions_rejected(self):
        for mutation, code in (
            (lambda d: d.update(quality_chance=0.5), 'ALCHEMY_SCHEMA'),
            (lambda d: d.update(version=True), 'ALCHEMY_VERSION'),
            (lambda d: d['effects'].pop('ningshen'), 'ALCHEMY_SCHEMA'),
            (lambda d: d['recipes'][0].update(hidden_success=0.8), 'ALCHEMY_SCHEMA'),
            (lambda d: d['effects']['huiling'].update(progress=500), 'ALCHEMY_SCHEMA'),
        ):
            data = copy.deepcopy(self.rules)
            mutation(data)
            self.assert_code(code, validator.check_rules(data))

    def test_tick_and_count_fields_reject_nonintegers_nonfinite_and_out_of_bounds(self):
        for location, key, maximum in (
            ((), 'use_ticks', 72000), ((), 'shared_cooldown_ticks', 1200000),
            (('recipes', 0), 'duration_ticks', 32767), (('recipes', 0), 'fuel_count', 64),
            (('recipes', 0), 'output_count', 16),
            (('effects', 'yangyuan'), 'duration_ticks', 1200000),
            (('effects', 'yangyuan'), 'interval_ticks', 1200000),
            (('effects', 'ningshen'), 'duration_ticks', 1200000),
        ):
            for invalid in (True, '20', None, -1, 0, 1.5, maximum + 1, float('nan'), float('inf'), 10 ** 400):
                with self.subTest(location=location, key=key, invalid=invalid):
                    data = copy.deepcopy(self.rules)
                    target = data
                    for part in location:
                        target = target[part]
                    target[key] = invalid
                    self.assert_code('ALCHEMY_BOUNDS', validator.check_rules(data))

    def test_effect_bounds_do_not_allow_negative_overfull_or_nonfinite_effects(self):
        for kind, key, maximum in (('huiling', 'power_fraction', 1), ('yangyuan', 'heal_fraction', 1),
                                    ('ningshen', 'stability_multiplier', 16)):
            for invalid in (True, None, '1', 0, -0.1, maximum + .1, float('nan'), float('inf'), 10 ** 400):
                data = copy.deepcopy(self.rules)
                data['effects'][kind][key] = invalid
                self.assert_code('ALCHEMY_BOUNDS', validator.check_rules(data))
        data = copy.deepcopy(self.rules)
        data['effects']['yangyuan']['interval_ticks'] = data['effects']['yangyuan']['duration_ticks'] + 1
        self.assert_code('ALCHEMY_BOUNDS', validator.check_rules(data))

    def test_formula_pill_herb_and_identity_cross_references(self):
        for mutation in (
            lambda d: d['recipes'][0].update(id='other'),
            lambda d: d['recipes'][1].update(id='huiling'),
            lambda d: d['recipes'][0].update(formula='myvillage:formula_ningshen'),
            lambda d: d['recipes'][0].update(output='myvillage:pill_yangyuan'),
            lambda d: d['recipes'][0].update(ingredients=['myvillage:spirit_leaf'] * 2),
            lambda d: d['recipes'][0].update(ingredients=['myvillage:spirit_leaf', 'myvillage:sunroot']),
            lambda d: d['recipes'][0].update(ingredients=[[], {}]),
        ):
            data = copy.deepcopy(self.rules)
            mutation(data)
            self.assert_code('ALCHEMY_REFERENCE', validator.check_rules(data))

    def test_recipe_order_and_herb_slot_order_are_irrelevant(self):
        data = copy.deepcopy(self.rules)
        data['recipes'].reverse()
        for recipe in data['recipes']:
            recipe['ingredients'].reverse()
        self.assertEqual([], validator.check_rules(data))

    def test_bad_json_constants_and_duplicate_keys_are_errors(self):
        for raw in (b'{"version":1,"version":2}', b'{"heal_fraction":NaN}', b'{"duration":Infinity}'):
            with self.assertRaises(ValueError):
                validator.load_json(raw)


class AlchemyResourceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = validator.validate(ROOT)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for relative in self.report['checked_resources']:
            relative = Path(relative)
            source = ROOT / relative
            if source.is_file():
                target = self.root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, target)

    def mutate(self, relative, mutation):
        path = self.root / relative
        data = json.loads(path.read_text())
        mutation(data)
        path.write_text(json.dumps(data))

    def errors(self):
        return validator.validate(self.root)['errors']

    def assert_code(self, code):
        errors = self.errors()
        self.assertTrue(any(e.startswith(code + ':') for e in errors), errors)

    def test_shipped_resources(self):
        self.assertEqual([], self.report['errors'])
        self.assertEqual([], self.errors())

    def test_cube_substitute_missing_geometry_and_broken_face_reference(self):
        model = validator.ASSETS / 'models/block/alchemy_furnace.json'
        self.mutate(model, lambda d: d.update(elements=[{'from': [0, 0, 0], 'to': [16, 16, 16]}]))
        self.assert_code('ALCHEMY_FURNACE_MODEL')
        shutil.copy2(ROOT / model, self.root / model)
        self.mutate(model, lambda d: d['elements'][0].update(faces={'north': {'texture': '#missing'}}))
        self.assert_code('ALCHEMY_FURNACE_MODEL')

    def test_shared_icons_and_missing_bilingual_name(self):
        textures = self.root / validator.ASSETS / 'textures/item'
        shutil.copy2(textures / 'spirit_leaf.png', textures / 'dew_blossom.png')
        self.assert_code('ALCHEMY_TEXTURE_DISTINCT')
        self.mutate(validator.ASSETS / 'lang/zh_cn.json', lambda d: d.pop('item.myvillage.formula_huiling'))
        self.assert_code('ALCHEMY_LANG')

    def test_wrong_survival_ingredient(self):
        path = validator.DATA / 'recipe/spirit_leaf.json'
        self.mutate(path, lambda d: d.update(ingredients=[{'item': 'minecraft:fern'}]))
        self.assert_code('ALCHEMY_ACQUISITION')

    def test_recipe_malformed_ingredients_and_loot_report_errors_without_crashing(self):
        self.mutate(validator.DATA / 'recipe/formula_huiling.json', lambda d: d.update(ingredients=None))
        self.assert_code('ALCHEMY_ACQUISITION')
        self.mutate(validator.DATA / 'loot_table/blocks/alchemy_furnace.json', lambda d: d.update(pools=[None]))
        self.assert_code('ALCHEMY_LOOT')

    def test_extra_drop_and_missing_pickaxe_tag(self):
        path = validator.DATA / 'loot_table/blocks/alchemy_furnace.json'
        self.mutate(path, lambda d: d['pools'].append(copy.deepcopy(d['pools'][0])))
        self.assert_code('ALCHEMY_LOOT')
        self.mutate(validator.RESOURCES / 'data/minecraft/tags/block/mineable/pickaxe.json',
                    lambda d: d['values'].remove('myvillage:alchemy_furnace'))
        self.assert_code('ALCHEMY_TAG')

    def test_missing_and_wrong_lit_blockstate(self):
        self.mutate(validator.ASSETS / 'blockstates/alchemy_furnace.json',
                    lambda d: d['variants'].pop('facing=north,lit=true'))
        self.assert_code('ALCHEMY_BLOCKSTATE')

    def test_jar_checks_missing_and_stale_contents(self):
        jar = self.root / 'fixture.jar'
        resources = [Path(p) for p in self.report['checked_resources']]
        def package(omit=None, stale=None, omit_class=None):
            with zipfile.ZipFile(jar, 'w') as archive:
                for name in validator.JAR_CLASSES:
                    if name != omit_class:
                        archive.writestr(name, b'fixture class')
                for relative in resources:
                    if relative != omit:
                        archive.writestr(relative.relative_to(validator.RESOURCES).as_posix(),
                                         b'{}' if relative == stale else (self.root / relative).read_bytes())
        package()
        self.assertEqual([], validator.validate(self.root, jar)['errors'])
        for options in ({'omit': validator.RULES}, {'stale': validator.ASSETS / 'models/item/pill_huiling.json'},
                        {'omit_class': validator.JAR_CLASSES[0]}):
            package(**options)
            self.assertTrue(any(e.startswith('ALCHEMY_JAR:') for e in validator.validate(self.root, jar)['errors']))
        jar.write_bytes(b'not zip')
        self.assertTrue(any(e.startswith('ALCHEMY_JAR:') for e in validator.validate(self.root, jar)['errors']))


if __name__ == '__main__':
    unittest.main()
