"""Negative fixtures exercise the data boundary, rather than reproducing the growth implementation."""
import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools import validate_realm_stats as validator

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / validator.DATA


class RealmStatsValidationTest(unittest.TestCase):
    def setUp(self):
        self.rules = json.loads((DATA / 'stats/rules.json').read_text())
        self.sim = json.loads((DATA / 'world_sim/realms.json').read_text())
        self.beasts = [json.loads((DATA / 'beast/demon_wolf.json').read_text())['attributes']]
        self.stages = {stage['id'] for path in (DATA / 'myvillage/realm').glob('*.json')
                       for stage in json.loads(path.read_text())['stages']}
        self.stage_keys = {stage['id']: (f'{path.parent.parent.name}:{path.stem}', index)
                           for path in (DATA / 'myvillage/realm').glob('*.json')
                           for index, stage in enumerate(sorted(json.loads(path.read_text())['stages'],
                                                                key=lambda s: s['sort_order']))}
        self.wilderness = json.loads((DATA / 'wilderness/rules.json').read_text())
        self.regions = {json.loads(p.read_text())['id'] for p in (DATA / 'worldgen/region_profile').glob('*.json')}

    def errors(self, data=None):
        return validator.check_rules(self.rules if data is None else data, self.stages, self.sim, self.beasts, self.stage_keys)

    def wilderness_errors(self, data=None):
        return validator.check_wilderness(self.wilderness if data is None else data, self.rules, self.regions)

    def assert_code(self, code, errors):
        self.assertTrue(any(e.startswith(code + ':') for e in errors), errors)

    def test_shipped_data_and_all_cross_file_mappings_pass(self):
        self.assertEqual([], self.errors())
        self.assertEqual([], self.wilderness_errors())
        self.assertEqual([], validator.validate(ROOT)['errors'])

    def test_nonfinite_boolean_and_numeric_string_growth_rejected(self):
        for key in self.rules['growth']:
            for invalid in (True, '2', float('nan'), float('inf'), -float('inf'), 10 ** 400):
                with self.subTest(key=key, invalid=invalid):
                    data = copy.deepcopy(self.rules)
                    data['growth'][key] = invalid
                    self.assert_code('REALM_GROWTH', self.errors(data))

    def test_growth_requires_real_increase(self):
        for key, values in {'entry_multiplier': [0, 1], 'major_ratio': [-1, 1], 'minor_slope': [-0.01, 0]}.items():
            for invalid in values:
                data = copy.deepcopy(self.rules)
                data['growth'][key] = invalid
                self.assert_code('REALM_GROWTH', self.errors(data))

    def test_major_entry_cannot_reverse_or_tie_previous_minor_peak(self):
        for ratio in (1.1, 1.4):
            data = copy.deepcopy(self.rules)
            data['growth']['major_ratio'] = ratio
            self.assert_code('REALM_BOUNDARY', self.errors(data))

    def test_shipped_growth_cannot_exceed_native_health_or_attack(self):
        for key, invalid in (('max_base_health', 80), ('max_base_attack', 200)):
            data = copy.deepcopy(self.rules)
            data['limits'][key] = invalid
            self.assert_code('REALM_NATIVE_OVERFLOW', self.errors(data))
        data = copy.deepcopy(self.rules)
        data['growth']['major_ratio'] = 1e308
        self.assert_code('REALM_NATIVE_OVERFLOW', self.errors(data))

    def test_configuration_cannot_raise_native_ceilings_to_hide_overflow(self):
        for key, invalid in (('max_health', 1025), ('max_attack', 2049)):
            data = copy.deepcopy(self.rules)
            data['limits'][key] = invalid
            self.assert_code('REALM_NATIVE_LIMIT', self.errors(data))

    def test_base_envelope_must_cover_actual_shipped_beast(self):
        for attr, limit in (('max_health', 'max_base_health'), ('attack_damage', 'max_base_attack')):
            data = copy.deepcopy(self.rules)
            data['limits'][limit] = self.beasts[0][attr] - 0.01
            self.assert_code('REALM_BASE', self.errors(data))

    def test_missing_unknown_duplicate_and_invalid_player_aliases(self):
        for mutation, code in (
            (lambda d: d['player_stage_aliases'].pop(), 'REALM_PLAYER_ALIAS_COVERAGE'),
            (lambda d: d['player_stage_aliases'][0].update(id='myvillage:unknown'), 'REALM_PLAYER_ALIAS_COVERAGE'),
            (lambda d: d['player_stage_aliases'].append(d['player_stage_aliases'][0]), 'REALM_PLAYER_ALIAS'),
            (lambda d: d['player_stage_aliases'][0].update(stage=True), 'REALM_ENDPOINT'),
            (lambda d: d['player_stage_aliases'][0].update(stage=999), 'REALM_ENDPOINT'),
            (lambda d: d['player_stage_aliases'][0].update(realm='myvillage:unknown'), 'REALM_ENDPOINT'),
        ):
            data = copy.deepcopy(self.rules)
            mutation(data)
            self.assert_code(code, self.errors(data))

    def test_ledger_alias_and_stage_count_must_match(self):
        data = copy.deepcopy(self.rules)
        data['realms'][1]['aliases'] = ['renamed']
        self.assert_code('REALM_SIM_MAPPING', self.errors(data))
        data = copy.deepcopy(self.rules)
        data['realms'][2]['stages'] = 4
        self.assert_code('REALM_SIM_MAPPING', self.errors(data))

    def test_existing_alias_cannot_silently_map_to_another_valid_realm_or_stage(self):
        for realm, stage in (('myvillage:qi_refining', 0), ('myvillage:mortal', 1)):
            data = copy.deepcopy(self.rules)
            data['player_stage_aliases'][0].update(realm=realm, stage=stage)
            self.assert_code('REALM_PLAYER_ALIAS_MAPPING', self.errors(data))

    def test_realm_stage_count_order_and_uniqueness(self):
        for key, value in (('stages', True), ('stages', 0), ('stages', 257), ('order', 2)):
            data = copy.deepcopy(self.rules)
            data['realms'][1][key] = value
            self.assertTrue(self.errors(data))
        data = copy.deepcopy(self.rules)
        data['realms'][2]['id'] = data['realms'][1]['id']
        self.assert_code('REALM_ID', self.errors(data))
        data = copy.deepcopy(self.rules)
        data['realms'][1:3] = reversed(data['realms'][1:3])
        self.assert_code('REALM_ORDER', self.errors(data))

    def test_resistance_penetration_and_neutral_multiplier_bounds(self):
        for key, value, code in (
            ('min_resistance', 0.1, 'REALM_RESISTANCE'), ('max_resistance', 1, 'REALM_RESISTANCE'),
            ('max_penetration', 1.01, 'REALM_RESISTANCE'), ('max_move_multiplier', 0.5, 'REALM_LIMIT'),
            ('max_total_multiplier', 0.5, 'REALM_LIMIT'), ('max_bonus', True, 'REALM_LIMIT'),
            ('max_flat_damage', float('nan'), 'REALM_LIMIT'), ('max_damage', float('inf'), 'REALM_LIMIT')):
            data = copy.deepcopy(self.rules)
            data['limits'][key] = value
            self.assert_code(code, self.errors(data))
        data = copy.deepcopy(self.rules)
        data['limits']['max_resistance'] = 0
        data['limits']['max_penetration'] = 1
        self.assertEqual([], self.errors(data))

    def test_damage_channels_are_known_unique_and_complete(self):
        for types in (['physical'], self.rules['damage_types'] + ['arcane'], self.rules['damage_types'] + ['fire']):
            data = copy.deepcopy(self.rules)
            data['damage_types'] = types
            self.assert_code('REALM_DAMAGE_TYPES', self.errors(data))

    def test_regions_are_explicit_exact_and_cannot_fall_back(self):
        data = copy.deepcopy(self.wilderness)
        del data['regions'][next(iter(data['regions']))]
        self.assert_code('WILDERNESS_REGION_COVERAGE', self.wilderness_errors(data))
        data = copy.deepcopy(self.wilderness)
        data['regions']['unknown'] = next(iter(data['regions'].values()))
        self.assert_code('WILDERNESS_REGION_COVERAGE', self.wilderness_errors(data))

    def test_range_endpoints_have_known_realm_and_valid_stage(self):
        for key, value in (('realm', 'myvillage:unknown'), ('stage', True), ('stage', -1), ('stage', 999), ('stage', float('inf'))):
            data = copy.deepcopy(self.wilderness)
            next(iter(data['regions'].values()))['beast']['min'][key] = value
            self.assert_code('REALM_ENDPOINT', self.wilderness_errors(data))

    def test_reversed_ranges_rejected_and_inclusive_single_stage_allowed(self):
        for kind in ('beast', 'rogue'):
            data = copy.deepcopy(self.wilderness)
            span = next(iter(data['regions'].values()))[kind]
            span['min'], span['max'] = span['max'], span['min']
            self.assert_code('WILDERNESS_RANGE_ORDER', self.wilderness_errors(data))
            span['min'] = copy.deepcopy(span['max'])
            self.assertEqual([], self.wilderness_errors(data))

    def test_encounter_settings_are_bounded_finite_integers(self):
        for key, (low, high) in validator.ROGUE_BOUNDS.items():
            for invalid in (True, float('nan'), float('inf'), 1.5, low - 1, high + 1):
                data = copy.deepcopy(self.wilderness)
                data['rogues'][key] = invalid
                self.assert_code('WILDERNESS_SETTING', self.wilderness_errors(data))

    def test_radius_and_cap_ordering(self):
        for key, value, code in (('withdraw_radius', 64, 'WILDERNESS_DISTANCE'),
                                 ('min_distance', 64, 'WILDERNESS_DISTANCE'),
                                 ('max_per_region', 13, 'WILDERNESS_CAP'),
                                 ('interval_ticks', 101, 'WILDERNESS_SETTING')):
            data = copy.deepcopy(self.wilderness)
            data['rogues'][key] = value
            self.assert_code(code, self.wilderness_errors(data))

    def test_unknown_fields_and_duplicate_json_fields_are_errors(self):
        data = copy.deepcopy(self.rules)
        data['growth']['minor_ratio'] = 1.05
        self.assert_code('REALM_SCHEMA', self.errors(data))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'rules.json').write_text('{"major_ratio":2,"major_ratio":3}')
            errors = []
            self.assertEqual({}, validator.read_json(root, Path('rules.json'), errors))
            self.assert_code('REALM_FILE', errors)


if __name__ == '__main__':
    unittest.main()
