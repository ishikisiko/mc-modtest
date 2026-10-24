"""离线插件公共契约的回归边界；不依赖六个地点实现，不启动游戏。"""
from dataclasses import replace
import json
from pathlib import Path
import subprocess
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from qianzhen import story_assets as assets
from qianzhen.storygen import (
    Adjacency, GenerationContext, SemanticPlan, SemanticSpace, SiteBuilder,
    SiteResult, compose_scene, derive_seed, generate_sites, load_contract, validate_result,
)


def smoke_plan(contract):
    # Only a plumbing fixture. Real site modules must describe their actual functional spaces.
    return SemanticPlan((SemanticSpace('fixture', '公共接口测试体积', (0, 0, 0),
                                     tuple(v - 1 for v in contract.size),
                                     tuple(contract.blueprint()['walk_targets'])),))


def authored_result(contract):
    return SiteResult(contract.site, contract.blueprint_json, smoke_plan(contract))


class StorygenContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.scene = load_contract()

    def test_contract_comes_from_existing_story_and_keeps_exact_states(self):
        self.assertEqual(set(self.scene.site_ids), {r['id'] for r in json.loads(self.scene.manifest_json)['locations']})
        roles = {key for c in self.scene.sites for key in c.roles}
        targets = {key for c in self.scene.sites for key in c.targets}
        self.assertEqual(roles, assets.ROLES)
        self.assertEqual(targets, assets.TARGETS)
        garden = self.scene.site('canal_gardens')
        self.assertEqual(garden.targets['water_sample']['state'], 'minecraft:water_cauldron[level=3]')
        self.assertEqual(dict(garden.protected)[39, 2, 11], 'minecraft:water_cauldron[level=3]')
        self.assertEqual(self.scene.site('apothecary').roles['zhou_he_home']['approach'], [35, 1, 19])
        copy = garden.blueprint()
        copy['walk_targets']['entry'][0] += 1
        self.assertNotEqual(copy, garden.blueprint())
        targets = garden.targets
        targets['water_sample']['state'] = 'minecraft:air'
        self.assertEqual(garden.targets['water_sample']['state'], 'minecraft:water_cauldron[level=3]')

    def test_seed_is_stable_across_process_and_scoped_by_site_version_stream(self):
        value = derive_seed(17, '1', 'apothecary')
        expected = 42405670077030770177793400214812630330407967732045285903039064682547656292804
        self.assertEqual(value, expected)
        code = "import sys;sys.path.insert(0,'tools');from qianzhen.storygen import derive_seed;print(derive_seed(17,'1','apothecary'))"
        output = subprocess.check_output([sys.executable, '-c', code], cwd=ROOT, text=True)
        self.assertEqual(int(output), value)
        for args in ((18, '1', 'apothecary'), (17, '2', 'apothecary'),
                     (17, '1', 'old_relay'), (17, '1', 'apothecary', 'decoration')):
            self.assertNotEqual(derive_seed(*args), value)
        for bad in (True, '17', 17.5):
            with self.assertRaises(ValueError):
                derive_seed(bad, '1', 'apothecary')

    def test_rng_draws_at_other_site_do_not_affect_site_or_stream(self):
        first = GenerationContext(self.scene.site('apothecary'), 17)
        expected = [first.rng().random(), first.rng('decoration').random()]
        other = GenerationContext(self.scene.site('old_relay'), 17).rng()
        for _ in range(10000):
            other.random()
        self.assertEqual([first.rng().random(), first.rng('decoration').random()], expected)

    def test_facilities_keep_solid_cells_and_approach_rays_out_of_actor_roles(self):
        facilities = {key for contract in self.scene.sites for key in contract.facilities}
        self.assertEqual(facilities, assets.FACILITIES)
        for contract in self.scene.sites:
            for key, facility in contract.facilities.items():
                self.assertNotIn(key, contract.roles)
                self.assertNotIn(key, contract.targets)
                protected = dict(contract.protected)
                self.assertEqual(protected[tuple(facility['pos'])], facility['state'])
                self.assertEqual(protected[tuple(facility['approach'])], 'minecraft:air')
                before = contract.facilities
                before[key]['state'] = 'minecraft:air'
                self.assertNotEqual(before, contract.facilities)
                builder = SiteBuilder(contract)
                with self.assertRaisesRegex(ValueError, 'protected'):
                    builder.apply_ops([{'op':'set', 'pos':facility['pos'], 'state':'minecraft:air'}])
                ray = assets.check_target_sightline(assets.nbt.expand_structure(contract.blueprint())[2],
                                                   key, facility['pos'], facility['approach'])
                for cell in ray['intermediate_cells']:
                    self.assertEqual(protected[tuple(cell)], 'minecraft:air')

    def test_builder_protects_support_feet_head_target_and_sightline_atomically(self):
        contract = self.scene.site('canal_gardens')
        builder = SiteBuilder(contract)
        self.assertNotIn((0, 0, 0), dict(contract.protected))
        target = contract.targets['water_sample']
        for pos in (target['pos'], target['approach'], assets.add(target['approach'], (0, -1, 0)),
                    assets.add(target['approach'], (0, 1, 0))):
            before = dict(builder.grid.cells)
            with self.assertRaisesRegex(ValueError, 'protected'):
                builder.apply_ops([{'op': 'set', 'pos': [0, 0, 0], 'state': 'minecraft:stone'},
                                   {'op': 'set', 'pos': list(pos), 'state': 'minecraft:lava'}])
            self.assertEqual(builder.grid.cells, before)
        # A later restoration must not hide an intermediate destructive write.
        with self.assertRaisesRegex(ValueError, 'protected'):
            builder.apply_ops([{'op': 'set', 'pos': target['pos'], 'state': 'minecraft:stone'},
                               {'op': 'set', 'pos': target['pos'], 'state': target['state']}])

    def test_legacy_dsl_ops_and_aliases_export_with_fixed_extent(self):
        contract = self.scene.site('relay_inn')
        builder = SiteBuilder(contract, from_authored=True)
        builder.apply_ops([{'op': 'fill', 'from': [0, 0, 0], 'to': [1, 0, 0], 'state': 'test'},
                           {'op': 'line', 'from': [0, 0, 0], 'to': [0, 0, 1], 'state': 'test'}],
                          palette={'test': 'minecraft:smooth_sandstone'})
        result = builder.finish(smoke_plan(contract))
        data = result.to_blueprint()
        self.assertEqual(data['size'], list(contract.size))
        blocks, _, _ = assets.validate(data, contract.max_size)
        self.assertEqual(blocks[0, 0, 1], 'minecraft:smooth_sandstone')
        self.assertEqual(data['walk_targets'], contract.blueprint()['walk_targets'])
        with self.assertRaisesRegex(ValueError, 'integers'):
            builder.apply_ops([{'op': 'set', 'pos': [0.5, 0, 0], 'state': 'stone'}])

    def test_final_validation_rejects_metadata_and_direct_grid_bypass(self):
        contract = self.scene.site('canal_gardens')
        for key, replacement in (('size', [1, 2, 3]), ('story_anchors', []),
                                 ('walk_targets', {'entry': [0, 1, 0]})):
            data = contract.blueprint()
            data[key] = replacement
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, 'immutable story metadata'):
                validate_result(contract, SiteResult(contract.site, json.dumps(data), smoke_plan(contract)))
        builder = SiteBuilder(contract, from_authored=True)
        builder.grid.set((39, 2, 11), 'minecraft:water_cauldron[level=2]', (), 0, force=True)
        with self.assertRaisesRegex(ValueError, 'protected story cell'):
            builder.finish(smoke_plan(contract))

    def test_existing_blueprint_reachability_still_rejects_a_blocked_entry(self):
        contract = self.scene.site('relay_inn')
        data = contract.blueprint()
        data['ops'].append({'op': 'set', 'pos': data['walk_targets']['entry'], 'state': 'minecraft:stone'})
        with self.assertRaisesRegex(ValueError, 'entry is obstructed'):
            validate_result(contract, SiteResult(contract.site, json.dumps(data), smoke_plan(contract)))

    def test_semantic_coverage_and_actual_adjacency_not_just_labels(self):
        contract = self.scene.site('relay_inn')
        result = authored_result(contract)
        space = result.semantics.spaces[0]
        bad = replace(result, semantics=SemanticPlan((replace(space, walk_targets=('entry',)),)))
        with self.assertRaisesRegex(ValueError, 'cover every'):
            validate_result(contract, bad)
        second = replace(space, id='other')
        good_edge = Adjacency(space.id, second.id, ((9, 1, 7), (10, 1, 7)))
        good = replace(result, semantics=SemanticPlan((space, second), (good_edge,)))
        validate_result(contract, good)
        bad_edge = replace(good_edge, path=((9, 1, 7), (15, 1, 7)))
        with self.assertRaisesRegex(ValueError, 'not walkable'):
            validate_result(contract, replace(good, semantics=SemanticPlan((space, second), (bad_edge,))))

    def test_plugins_are_independent_and_load_only_selected_sites(self):
        seen = []
        draws = {'apothecary': 100, 'old_relay': 1}

        def fake_import(name):
            site = name.rsplit('.', 1)[1]
            seen.append(site)

            def generate(ctx):
                # Different numbers of draws simulate changing one site's implementation.
                rng = ctx.rng()
                for _ in range(draws[site]):
                    rng.random()
                data = ctx.contract.blueprint()
                data['description'] = str(rng.getrandbits(64))
                data['ops'].append({'op': 'set', 'pos': [0, 0, 0],
                                    'state': rng.choice(['minecraft:stone', 'minecraft:smooth_sandstone'])})
                return SiteResult(site, json.dumps(data), smoke_plan(ctx.contract))
            return SimpleNamespace(SITE_ID=site, generate=generate)

        with patch('qianzhen.storygen.core.importlib.import_module', side_effect=fake_import):
            a = generate_sites(self.scene, 9, ['apothecary', 'old_relay'])
            b = generate_sites(self.scene, 9, ['old_relay', 'apothecary'])
            draws['apothecary'] = 200
            c = generate_sites(self.scene, 9, ['apothecary', 'old_relay'])
        self.assertEqual(a, b)
        self.assertEqual(a['old_relay'], c['old_relay'])
        self.assertNotEqual(a['apothecary'], c['apothecary'])
        self.assertEqual(seen, ['apothecary', 'old_relay', 'old_relay', 'apothecary', 'apothecary', 'old_relay'])
        with self.assertRaisesRegex(ValueError, 'unknown story site'):
            generate_sites(self.scene, 9, ['../bad'])
        with patch('qianzhen.storygen.core.importlib.import_module', return_value=SimpleNamespace(SITE_ID='wrong')):
            with self.assertRaisesRegex(ValueError, 'SITE_ID'):
                generate_sites(self.scene, 9, ['relay_inn'])

    def test_mixed_composition_preserves_ids_targets_props_and_npc_checks(self):
        contract = self.scene.site('relay_inn')
        builder = SiteBuilder(contract, from_authored=True)
        builder.apply_ops([{'op': 'set', 'pos': [0, 0, 0], 'state': 'minecraft:smooth_sandstone'}])
        generated = builder.finish(smoke_plan(contract))
        layout, authored, report = compose_scene(self.scene, {'relay_inn': generated})
        self.assertEqual({s['id'] for s in layout['sites']}, set(self.scene.site_ids))
        self.assertEqual({t['id'] for t in layout['targets']}, assets.TARGETS)
        self.assertEqual({t['id'] for t in layout['facilities']}, assets.FACILITIES)
        self.assertTrue(report['all_player_approaches_reachable_with_npcs'])
        blocks = assets.nbt.expand_structure(authored['relay_inn'])[2]
        self.assertEqual(blocks[0, 0, 0], 'minecraft:smooth_sandstone')
        blocks = assets.nbt.expand_structure(authored['canal_gardens'])[2]
        self.assertEqual(blocks[39, 2, 11], 'minecraft:water_cauldron[level=3]')
        self.assertIn('deployment_runtime', report['not_verified'])


if __name__ == '__main__':
    unittest.main()
