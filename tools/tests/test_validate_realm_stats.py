"""Negative fixtures exercise the data boundary, rather than reproducing the growth implementation."""
import copy
import json
import tempfile
import re
import shutil
import zipfile
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


class RealmArmorContractTest(unittest.TestCase):
    """Mutation tests guard authority and interception scope, not a duplicate armor formula."""
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.paths = {
            'hook': validator.JAVA_ROOT / 'mixin/CombatRulesMixin.java',
            'accessor': validator.JAVA_ROOT / 'mixin/LivingDamageContainerAccess.java',
            'runtime': validator.JAVA_ROOT / 'stats/runtime/RealmArmorRuntime.java',
            'stats': validator.JAVA_ROOT / 'stats/runtime/StatsRuntime.java',
            'pressure': validator.JAVA_ROOT / 'stats/ArmorPressure.java',
        }
        self.sources = {name: (ROOT / path).read_text() for name, path in self.paths.items()}
        for path in (*self.paths.values(), validator.MIXIN_CONFIG, validator.MOD_METADATA):
            target = self.root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / path, target)

    def assert_code(self, code, errors):
        self.assertTrue(any(e.startswith(code + ':') for e in errors), errors)

    def hook_errors(self, source=None):
        return validator.check_armor_hook(self.sources['hook'] if source is None else source)

    def runtime_errors(self, **replacements):
        sources = self.sources | replacements
        return validator.check_armor_runtime(sources['runtime'], sources['stats'], sources['accessor'], sources['pressure'])

    def replace(self, key, old, new):
        self.assertIn(old, self.sources[key])
        return self.sources[key].replace(old, new)

    def change_config(self, mutation):
        path = self.root / validator.MIXIN_CONFIG
        config = json.loads(path.read_text())
        mutation(config)
        path.write_text(json.dumps(config))

    def test_shipped_hook_runtime_and_packaging_contract_pass(self):
        self.assertEqual([], self.hook_errors())
        self.assertEqual([], self.runtime_errors())
        self.assertEqual([], validator.check_armor_contract(self.root))

    def test_whitespace_comments_and_local_names_are_not_the_contract(self):
        source = self.sources['hook']
        for old, new in (('arguments', 'clampValues'), ('defender', 'victim'), ('damage', 'enteringAmount'), ('original', 'oldArmorValue')):
            source = re.sub(r'\b' + old + r'\b', new, source)
        source = source.replace('require = 1, allow = 1', 'allow /* required hit count */ = 1,\n require = 1')
        self.assertEqual([], self.hook_errors(source))
        # Comments and quoted text never stand in for missing behavior.
        source = source.replace('ordinal = 0', 'ordinal = 1') + '\n// ordinal = 0\n'
        self.assert_code('ARMOR_HOOK_CLAMP', self.hook_errors(source))

    def test_full_method_descriptor_and_first_clamp_are_required(self):
        for old, new, code in ((validator.ARMOR_DESCRIPTOR, 'getDamageAfterAbsorb', 'ARMOR_HOOK_DESCRIPTOR'),
                               (validator.CLAMP_DESCRIPTOR, 'Lnet/minecraft/util/Mth;clamp(DDD)D', 'ARMOR_HOOK_CLAMP'),
                               ('ordinal = 0', 'ordinal = 1', 'ARMOR_HOOK_CLAMP'),
                               ('value = "INVOKE"', 'value = "RETURN"', 'ARMOR_HOOK_CLAMP')):
            self.assert_code(code, self.hook_errors(self.replace('hook', old, new)))

    def test_target_remapping_and_required_injection_counts_cannot_drift(self):
        for old, new, code in (('require = 1', 'require = 0', 'ARMOR_HOOK_COUNT'),
                               ('allow = 1', 'allow = 2', 'ARMOR_HOOK_COUNT'),
                               ('remap = false', 'remap = true', 'ARMOR_HOOK_REMAP')):
            self.assert_code(code, self.hook_errors(self.replace('hook', old, new)))

    def test_broader_damage_replacement_and_wrong_mixin_target_are_forbidden(self):
        self.assert_code('ARMOR_HOOK_SCOPE', self.hook_errors(self.replace('hook', '@ModifyArgs(', '@Redirect(')))
        self.assert_code('ARMOR_HOOK_TARGET', self.hook_errors(self.replace('hook', 'value = CombatRules.class', 'value = LivingEntity.class')))

    def test_only_the_first_clamp_value_may_change(self):
        self.assert_code('ARMOR_HOOK_ARGUMENT', self.hook_errors(self.replace('hook', 'arguments.set(0,', 'arguments.set(1,')))
        self.assert_code('ARMOR_HOOK_ARGUMENT', self.hook_errors(self.replace('hook', 'float original = arguments.get(0);',
                                                                 'float original = arguments.get(0); arguments.set(2, 100F);')))
        self.assert_code('ARMOR_HOOK_ARGUMENT', self.hook_errors(self.replace('hook', 'float original = arguments.get(0);',
                                                                 'float original = arguments.get(0); arguments.setAll(0, 0, 0);')))

    def test_actual_entering_damage_and_native_bounds_must_flow_unchanged(self):
        for old, new in (('defender, source, damage, armor, original', 'source.getEntity(), source, damage, armor, original'),
                         ('defender, source, damage, armor, original', 'defender, source, damage / 6.6F, armor, original'),
                         ('arguments.get(1), arguments.get(2)', 'arguments.get(1), 30F'),
                         ('float original = arguments.get(0);', 'float original = arguments.get(0); damage /= 6.6F;')):
            self.assert_code('ARMOR_HOOK_FLOW', self.hook_errors(self.replace('hook', old, new)))

    def test_hook_cannot_recalculate_armor_or_enchantments(self):
        source = self.replace('hook', 'float original = arguments.get(0);',
                              'float original = arguments.get(0); CombatRules.getDamageAfterAbsorb(defender, damage, source, armor, toughness);')
        self.assert_code('ARMOR_LAYER_DUPLICATION', self.hook_errors(source))
        runtime = self.sources['runtime'] + '\nclass DuplicateArmor { void apply() { EnchantmentHelper.modifyArmorEffectiveness(); } }\n'
        self.assert_code('ARMOR_LAYER_DUPLICATION', self.runtime_errors(runtime=runtime))
        harmless = self.sources['runtime'] + '\n// EnchantmentHelper.modifyArmorEffectiveness()\n'
        self.assertEqual([], self.runtime_errors(runtime=harmless))

    def test_defender_server_and_persisted_assignment_are_required(self):
        self.assert_code('ARMOR_SERVER_AUTHORITY', self.runtime_errors(runtime=self.replace('runtime', 'if (defender.level().isClientSide())', 'if (false)')))
        self.assert_code('ARMOR_DEFENDER_FLOW', self.runtime_errors(runtime=self.replace('runtime', 'defenderScale(defender);', 'defenderScale(source.getEntity());')))
        self.assert_code('ARMOR_DEFENDER_FLOW', self.runtime_errors(runtime=self.replace('runtime', 'resolveScale(defender.getPersistentData(),', 'resolveScale(source.getEntity().getPersistentData(),')))

    def test_pressure_helper_uses_defender_growth_and_returns_only_the_unclamped_value(self):
        self.assert_code('ARMOR_DEFENDER_FLOW', self.runtime_errors(runtime=self.replace('runtime',
                         'ArmorPressure.normalizeUnclamped(armor, original, scale.factor())',
                         'ArmorPressure.normalizeUnclamped(armor, original, 6.6)')))
        self.assert_code('ARMOR_PRESSURE_OUTPUT', self.runtime_errors(runtime=self.replace('runtime',
                         'return normalized;', 'return damageAtArmor / (float) scale.factor();')))
        runtime = self.sources['runtime']
        for old, new in (('defender', 'victim'), ('original', 'unclampedValue'), ('scale', 'realmFactor'), ('normalized', 'pressureValue')):
            runtime = re.sub(r'\b' + old + r'\b', new, runtime)
        self.assertEqual([], self.runtime_errors(runtime=runtime))

    def test_wrong_nbt_types_or_different_saved_key_cannot_silently_coerce(self):
        for old, new in (('Tag.TAG_COMPOUND', 'Tag.TAG_STRING'), ('Tag.TAG_STRING', 'Tag.TAG_INT'),
                         ('Tag.TAG_INT', 'Tag.TAG_ANY_NUMERIC')):
            self.assert_code('ARMOR_ASSIGNMENT_TYPES', self.runtime_errors(runtime=self.replace('runtime', old, new)))
        self.assert_code('ARMOR_ASSIGNMENT_KEY', self.runtime_errors(runtime=self.replace('runtime', '"myvillage:stats_realm"', '"myvillage:fake_realm"')))

    def test_growth_must_resolve_exactly_once_from_the_validated_defender_key(self):
        for old, new in (('rules.growth(rules.realmKey', 'rules.majorRatio(rules.realmKey'),
                         ('assignment.getInt("stage")', 'assignment.getInt("attacker_stage")')):
            self.assert_code('ARMOR_ASSIGNMENT_GROWTH', self.runtime_errors(runtime=self.replace('runtime', old, new)))

    def test_invalid_assignment_and_unusable_scale_fall_back_to_one(self):
        for old, new, code in (('Double.isFinite(factor)', 'true', 'ARMOR_SCALE_FINITE'),
                               ('factor >= 1', 'factor >= 0', 'ARMOR_SCALE_FINITE'),
                               ('new Scale(1,', 'new Scale(0,', 'ARMOR_ASSIGNMENT_FALLBACK'),
                               ('catch (IllegalArgumentException exception)', 'catch (NullPointerException exception)', 'ARMOR_ASSIGNMENT_FALLBACK')):
            self.assert_code(code, self.runtime_errors(runtime=self.replace('runtime', old, new)))

    def test_damage_path_does_not_rewrite_assignment_or_infer_from_health(self):
        self.assert_code('ARMOR_ASSIGNMENT_MUTATION', self.runtime_errors(runtime=self.replace('runtime', 'CompoundTag assignment = persistent.getCompound(REALM_TAG);',
                                                                                              'CompoundTag assignment = persistent.getCompound(REALM_TAG); assignment.putInt("stage", 2);')))
        self.assert_code('ARMOR_DEFENDER_INFERENCE', self.runtime_errors(runtime=self.replace('runtime', 'Scale scale = defenderScale(defender);',
                                                                                            'Scale scale = new Scale(defender.getMaxHealth() / 20, "inferred");')))

    def test_whole_incoming_damage_is_never_replaced_by_pressure(self):
        runtime = self.sources['runtime'] + '\nclass WholeDamage { void apply() { event.setNewDamage(event.getNewDamage() / 6.6F); } }\n'
        self.assert_code('ARMOR_WHOLE_DAMAGE', self.runtime_errors(runtime=runtime))
        stats = self.replace('stats', 'event.setNewDamage((float) mitigation.damage());',
                             'event.setNewDamage((float) (mitigation.damage() / RealmArmorRuntime.defenderScale(target).factor()));')
        self.assert_code('ARMOR_WHOLE_DAMAGE', self.runtime_errors(stats=stats))

    def test_native_armor_and_toughness_caps_and_no_innate_grant_remain(self):
        for mutation in ('Attributes.ARMOR.value().setMaxValue(60);',
                         'entity.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(30);',
                         'RealmAttributeModifiers.replace(entity.getAttribute(Attributes.ARMOR), id, 6.6);'):
            runtime = self.sources['runtime'] + '\nclass ExpandedArmor { void grant() { ' + mutation + ' } }\n'
            errors = self.runtime_errors(runtime=runtime)
            self.assertTrue(any(e.startswith(('ARMOR_NATIVE_LIMIT:', 'ARMOR_INNATE_GRANT:')) for e in errors), errors)

    def test_container_accessor_and_weak_invocation_identity_cannot_drift(self):
        self.assert_code('ARMOR_CONTAINER_ACCESS', self.runtime_errors(accessor=self.replace('accessor', '"damageContainers"', '"damageSource"')))
        self.assert_code('ARMOR_CONTAINER_ACCESS', self.runtime_errors(accessor=self.replace('accessor', 'Stack<DamageContainer>', 'Deque<DamageContainer>')))
        self.assert_code('ARMOR_TRACE_IDENTITY', self.runtime_errors(runtime=self.replace('runtime', 'new WeakHashMap<>()', 'new HashMap<>()')))
        self.assert_code('ARMOR_TRACE_IDENTITY', self.runtime_errors(runtime=self.replace('runtime', 'Map<DamageContainer, ArmorTrace>', 'Map<DamageSource, ArmorTrace>')))

    def test_actual_armor_input_and_invocation_reduction_drive_the_trace(self):
        self.assert_code('ARMOR_TRACE_BRIDGE', self.runtime_errors(stats=self.replace('stats', 'RealmArmorRuntime.take(event.getContainer())', 'RealmArmorRuntime.take(event.getSource())')))
        for old, new in (('armor.damageAtArmor() - event.getContainer()', 'event.getOriginalDamage() - event.getContainer()'),
                         ('armor.damageAtArmor() - event.getContainer()', 'armor.pressureDamage() - event.getContainer()'),
                         ('DamageContainer.Reduction.ARMOR', 'DamageContainer.Reduction.ABSORPTION')):
            self.assert_code('ARMOR_TRACE_OUTPUT', self.runtime_errors(stats=self.replace('stats', old, new)))

    def test_required_common_config_and_metadata_are_not_optional(self):
        original = (self.root / validator.MIXIN_CONFIG).read_text()
        for mutation in (lambda c: c.update(required=False), lambda c: c.update(compatibilityLevel='JAVA_17'),
                         lambda c: c['injectors'].update(defaultRequire=0), lambda c: c['mixins'].remove('CombatRulesMixin')):
            (self.root / validator.MIXIN_CONFIG).write_text(original)
            self.change_config(mutation)
            self.assert_code('ARMOR_MIXIN_CONFIG', validator.check_armor_packaging(self.root))
        (self.root / validator.MIXIN_CONFIG).write_text(original)
        metadata = self.root / validator.MOD_METADATA
        metadata.write_text(metadata.read_text().replace('config = "myvillage.mixins.json"', 'config = "missing.mixins.json"'))
        self.assert_code('ARMOR_MIXIN_METADATA', validator.check_armor_packaging(self.root))

    def test_client_only_registration_and_missing_source_fail(self):
        self.change_config(lambda c: c.update(client=['CombatRulesMixin']))
        self.assert_code('ARMOR_MIXIN_SIDE', validator.check_armor_packaging(self.root))
        (self.root / self.paths['accessor']).unlink()
        self.assert_code('ARMOR_MIXIN_SOURCE', validator.check_armor_packaging(self.root))

    def make_jar(self, *, omit=None, stale=False):
        """Entry-only zip fixture, not compiled/runtime compatibility evidence."""
        jar = self.root / 'fixture.jar'
        class_names = ['mixin/CombatRulesMixin', 'mixin/LivingDamageContainerAccess', 'stats/ArmorPressure',
                       'stats/runtime/RealmArmorRuntime', 'stats/runtime/RealmArmorRuntime$Scale',
                       'stats/runtime/RealmArmorRuntime$ArmorTrace']
        with zipfile.ZipFile(jar, 'w') as archive:
            for entry, source in ((validator.MIXIN_CONFIG.name, validator.MIXIN_CONFIG),
                                  ('META-INF/neoforge.mods.toml', validator.MOD_METADATA)):
                if entry != omit:
                    archive.writestr(entry, b'{}' if stale and entry == validator.MIXIN_CONFIG.name else (self.root / source).read_bytes())
            for name in class_names:
                entry = 'com/example/myvillage/' + name + '.class'
                if entry != omit:
                    archive.writestr(entry, b'\xca\xfe\xba\xbe\x00\x00\x00\x41')
        return jar

    def test_jar_entries_are_complete_and_authored_configuration_is_current(self):
        self.assertEqual([], validator.check_armor_packaging(self.root, self.make_jar()))
        for omitted in ('myvillage.mixins.json', 'META-INF/neoforge.mods.toml',
                        'com/example/myvillage/mixin/CombatRulesMixin.class',
                        'com/example/myvillage/stats/runtime/RealmArmorRuntime$ArmorTrace.class'):
            self.assert_code('ARMOR_JAR_ENTRY', validator.check_armor_packaging(self.root, self.make_jar(omit=omitted)))
        self.assert_code('ARMOR_JAR_DRIFT', validator.check_armor_packaging(self.root, self.make_jar(stale=True)))
        invalid = self.root / 'invalid.jar'
        invalid.write_text('not a zip')
        self.assert_code('ARMOR_JAR', validator.check_armor_packaging(self.root, invalid))


if __name__ == '__main__':
    unittest.main()
