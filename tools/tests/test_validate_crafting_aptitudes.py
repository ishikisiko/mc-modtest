"""Negative fixtures enforce the crafting data and server authority boundary, independent of UI layout."""
from __future__ import annotations

import copy
import json
from pathlib import Path
import shutil
import tempfile
import unittest
import zipfile

from tools import validate_crafting_aptitudes as validator

ROOT = Path(__file__).resolve().parents[2]


class CraftingRulesValidationTest(unittest.TestCase):
    def setUp(self):
        self.rules = json.loads((ROOT / validator.RULES).read_text())

    def assert_code(self, code, data):
        errors = validator.check_rules(data)
        self.assertTrue(any(e.startswith(code + ':') for e in errors), errors)

    def test_shipped_balance_is_same_recipe_product_quality_ladder(self):
        self.assertEqual([], validator.check_rules(self.rules))
        self.assertEqual({'min': 0, 'max': 100}, self.rules['initial_roll'])
        self.assertEqual([{'quality': q, 'minimum': minimum} for q, minimum in enumerate((0, 40, 70, 90), 1)],
                         self.rules['quality_thresholds'])

    def test_missing_extra_and_grade_interpretation_fields_rejected(self):
        for mutate in (
            lambda d: d.update(recipe_grade=4),
            lambda d: d.pop('quality_thresholds'),
            lambda d: d['initial_roll'].update(skill_bonus=1),
            lambda d: d['quality_thresholds'][0].update(grade='huang'),
        ):
            data = copy.deepcopy(self.rules)
            mutate(data)
            self.assert_code('CRAFTING_SCHEMA', data)

    def test_version_is_strict_integer_one(self):
        for value in (True, None, '1', 0, 2, 1.0):
            data = copy.deepcopy(self.rules)
            data['version'] = value
            self.assert_code('CRAFTING_VERSION', data)

    def test_initial_roll_requires_bounded_integer_endpoints(self):
        for key in ('min', 'max'):
            for value in (True, None, '0', -1, 101, 0.5, float('nan'), float('inf'), 10 ** 400):
                with self.subTest(key=key, value=value):
                    data = copy.deepcopy(self.rules)
                    data['initial_roll'][key] = value
                    self.assert_code('CRAFTING_ROLL', data)
        data = copy.deepcopy(self.rules)
        data['initial_roll'] = {'min': 80, 'max': 40}
        self.assert_code('CRAFTING_ROLL', data)

    def test_quality_codes_are_complete_ordered_and_unambiguous(self):
        for mutate in (
            lambda d: d['quality_thresholds'].pop(),
            lambda d: d['quality_thresholds'].append(d['quality_thresholds'][0]),
            lambda d: d['quality_thresholds'].reverse(),
            lambda d: d['quality_thresholds'][1].update(quality=1),
            lambda d: d['quality_thresholds'][0].update(quality=True),
            lambda d: d['quality_thresholds'][0].update(quality='lower'),
        ):
            data = copy.deepcopy(self.rules)
            mutate(data)
            self.assert_code('CRAFTING_QUALITY', data)

    def test_minima_cover_zero_increase_strictly_and_stay_within_format(self):
        for index in range(4):
            for value in (True, None, '40', -1, 101, 40.5, float('nan'), float('inf'), 10 ** 400):
                data = copy.deepcopy(self.rules)
                data['quality_thresholds'][index]['minimum'] = value
                self.assert_code('CRAFTING_THRESHOLD', data)
        for index, value in ((0, 1), (1, 0), (2, 30), (3, 70)):
            data = copy.deepcopy(self.rules)
            data['quality_thresholds'][index]['minimum'] = value
            self.assert_code('CRAFTING_THRESHOLD', data)

    def test_valid_data_retune_preserves_format_contract(self):
        data = copy.deepcopy(self.rules)
        data['initial_roll'] = {'min': 20, 'max': 90}
        for threshold, minimum in zip(data['quality_thresholds'], (0, 20, 60, 100)):
            threshold['minimum'] = minimum
        self.assertEqual([], validator.check_rules(data))

    def test_duplicate_fields_and_nonfinite_json_rejected(self):
        for raw in ('{"version":1,"version":2}', '{"max":NaN}', '{"min":Infinity}'):
            with self.assertRaises(ValueError):
                validator.load_json(raw)


class CraftingAuthorityValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for relative in (*validator.SOURCE_FILES, validator.RULES, *validator.LANGS):
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / relative, path)

    def errors(self, jar=None):
        return validator.validate(self.root, jar)['errors']

    def assert_code(self, code, errors=None):
        errors = self.errors() if errors is None else errors
        self.assertTrue(any(e.startswith(code + ':') for e in errors), errors)

    def mutate(self, name, before, after):
        path = self.root / validator.JAVA / name
        source = path.read_text()
        self.assertIn(before, source)
        path.write_text(source.replace(before, after, 1))

    def test_shipped_boundary_passes(self):
        self.assertEqual([], self.errors())
        self.assertEqual([], validator.validate(ROOT)['errors'])

    def test_outside_service_cannot_write_saved_aptitudes(self):
        path = self.root / validator.JAVA / 'cultivation/crafting/BadWriter.java'
        path.write_text('class BadWriter { void change() { player.setData(CultivationAttachments.CRAFTING.get(), replacement); } }')
        self.assert_code('CRAFTING_WRITER')

    def test_attachment_requires_copy_on_death(self):
        self.mutate('cultivation/CultivationAttachments.java',
                    'CraftingAptitudes.CODEC)\n                    .copyOnDeath()', 'CraftingAptitudes.CODEC)')
        self.assert_code('CRAFTING_ATTACHMENT')

    def test_clone_hook_cannot_manually_copy_aptitudes(self):
        path = self.root / validator.JAVA / 'cultivation/crafting/BadClone.java'
        path.write_text('class BadClone { void copy(PlayerEvent.Clone event) { CultivationService.initializeCraftingAptitudes(player); } }')
        self.assert_code('CRAFTING_CLONE')

    def test_profile_version_cannot_be_bumped_for_separate_attachment(self):
        self.mutate('cultivation/CultivationProfile.java', 'CURRENT_SCHEMA_VERSION = 4', 'CURRENT_SCHEMA_VERSION = 5')
        self.assert_code('CRAFTING_PROFILE')

    def test_initialized_guard_cannot_be_removed(self):
        self.mutate('cultivation/CultivationService.java',
                    'if (current.initialized() || getProfile(player).spiritualRoot().isEmpty()) return;',
                    'if (getProfile(player).spiritualRoot().isEmpty()) return;')
        self.assert_code('CRAFTING_INITIALIZATION')

    def test_pending_guard_cannot_be_removed(self):
        self.mutate('cultivation/CultivationService.java',
                    'if (current.initialized() || getProfile(player).spiritualRoot().isEmpty()) return;',
                    'if (current.initialized()) return;')
        self.assert_code('CRAFTING_INITIALIZATION')

    def test_existing_awakened_state_is_prepared_before_snapshot(self):
        self.mutate('cultivation/CultivationService.java',
                    'initializeCraftingAptitudes(player);', 'skipPreparation(player);')
        self.assert_code('CRAFTING_INITIALIZATION')

    def test_generation_uses_world_seed_and_uuid(self):
        self.mutate('cultivation/CultivationService.java', 'player.getUUID(), CraftingRules.get()',
                    'new java.util.UUID(0, 0), CraftingRules.get()')
        self.assert_code('CRAFTING_INITIALIZATION')

    def test_same_rng_domain_for_both_professions_is_rejected(self):
        self.mutate('cultivation/crafting/CraftingGenerator.java', '0x666f7267696e6702L', '0x616c6368656d7901L')
        self.assert_code('CRAFTING_RNG')

    def test_time_or_root_random_dependency_is_rejected(self):
        path = self.root / validator.JAVA / 'cultivation/crafting/CraftingGenerator.java'
        original = path.read_text()
        for extra in ('long time = System.nanoTime();', 'root.getRandom().nextInt(101);', 'new Random();'):
            path.write_text(original + '\n' + extra)
            self.assert_code('CRAFTING_RNG')

    def test_snapshot_codec_cannot_omit_crafting(self):
        self.mutate('cultivation/network/CultivationSnapshotPayload.java',
                    'CultivationSnapshotPayload::crafting', 'CultivationSnapshotPayload::profile')
        self.assert_code('CRAFTING_SNAPSHOT_CODEC')

    def test_snapshot_sender_cannot_use_compatibility_default(self):
        self.mutate('cultivation/CultivationService.java',
                    'budget(), crafting,', 'budget(), CraftingAptitudes.EMPTY,')
        self.assert_code('CRAFTING_SNAPSHOT_SENDER')

    def test_quality_cap_must_be_derived_from_same_saved_profession(self):
        self.mutate('cultivation/CultivationService.java',
                    'crafting.qualityCap(CraftingAptitudes.Profession.FORGING, rules)', '4')
        self.assert_code('CRAFTING_SNAPSHOT_SENDER')

    def test_protocol_must_change_with_wire_format(self):
        self.mutate('network/ModPayloads.java', 'PROTOCOL_VERSION = "21"', 'PROTOCOL_VERSION = "19"')
        self.assert_code('CRAFTING_PROTOCOL')

    def test_no_extra_crafting_payload_or_client_authority(self):
        path = self.root / validator.JAVA / 'cultivation/network/BadCraftingPayload.java'
        path.write_text('record BadCraftingPayload(CraftingAptitudes crafting) implements CustomPacketPayload {}')
        self.assert_code('CRAFTING_PAYLOAD')
        self.mutate('client/cultivation/ClientCultivationState.java',
                    'public final class ClientCultivationState', 'import com.example.myvillage.cultivation.crafting.CraftingRules;\npublic final class ClientCultivationState')
        self.assert_code('CRAFTING_CLIENT_AUTHORITY')

    def test_crafting_cannot_get_separate_client_cache(self):
        path = self.root / validator.JAVA / 'client/cultivation/ClientCultivationState.java'
        path.write_text(path.read_text() + '\nAtomicReference<CraftingAptitudes> EXTRA = new AtomicReference<>();')
        self.assert_code('CRAFTING_CLIENT_CACHE')

    def test_missing_product_quality_label_is_rejected(self):
        path = self.root / validator.LANGS[1]
        values = json.loads(path.read_text())
        values.pop('crafting.myvillage.quality.perfect')
        path.write_text(json.dumps(values))
        self.assert_code('CRAFTING_LANG')

    def test_jar_has_rules_and_runtime_classes(self):
        jar = self.root / 'fixture.jar'
        name = validator.RULES.relative_to(validator.RESOURCES).as_posix()
        def package(omit=None, stale=False):
            with zipfile.ZipFile(jar, 'w') as archive:
                for relative in (validator.RULES, *validator.LANGS):
                    entry = relative.relative_to(validator.RESOURCES).as_posix()
                    if omit != entry:
                        archive.writestr(entry, b'{}' if stale else (self.root / relative).read_bytes())
                for cls in validator.JAR_CLASSES:
                    if cls != omit:
                        archive.writestr(cls, b'fixture class')
        package()
        self.assertEqual([], self.errors(jar))
        for missing in (name, validator.JAR_CLASSES[0]):
            package(omit=missing)
            self.assert_code('CRAFTING_JAR', self.errors(jar))
        package(stale=True)
        self.assert_code('CRAFTING_JAR', self.errors(jar))


if __name__ == '__main__':
    unittest.main()
