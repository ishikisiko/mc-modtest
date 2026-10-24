"""长明药庐插件的离线几何、语义与剧情部署边界；不启动 Minecraft。"""
from collections import deque
from dataclasses import replace
from pathlib import Path
import hashlib
import json
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from qianzhen import story_assets as assets
from qianzhen.build import check_walks, walk_graph
from qianzhen.storygen import (GenerationContext, compose_scene, generate_sites,
                              load_contract, validate_result)
from qianzhen.storygen.apothecary import SITE_ID, generate


SEEDS = (0, 1, 2, 3, 4, 7, 17, 20261024, -19, 2**80)


def reachable(graph, start):
    seen, todo = {start}, deque([start])
    while todo:
        for pos in graph.get(todo.popleft(), ()):
            if pos not in seen:
                seen.add(pos)
                todo.append(pos)
    return seen


def occupancy(blocks):
    # Materials and metadata cannot make a geometry-only comparison pass.
    return {pos for pos, state in blocks.items() if pos[1] > 0 and state != 'minecraft:air'}


class ApothecaryGenerationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.scene = load_contract()
        cls.contract = cls.scene.site(SITE_ID)
        cls.results = {seed: generate(GenerationContext(cls.contract, seed)) for seed in SEEDS}
        cls.blocks = {seed: assets.nbt.expand_structure(result.to_blueprint())[2]
                      for seed, result in cls.results.items()}
        # Select representatives by wall positions, independently of variant names.
        cls.representatives = {}
        for seed, blocks in cls.blocks.items():
            shape = tuple(blocks[pos] != 'minecraft:air' for pos in (
                (2, 1, 10), (33, 1, 22), (12, 1, 28)))
            cls.representatives.setdefault(shape, seed)

    def test_same_seed_reproduces_entire_result_even_in_fresh_process(self):
        self.assertEqual(SITE_ID, 'apothecary')
        for seed in (0, 2, 4):
            with self.subTest(seed=seed):
                self.assertEqual(self.results[seed], generate(GenerationContext(self.contract, seed)))
        script = '''import hashlib,sys
sys.path.insert(0,'tools')
from qianzhen.storygen import load_contract,GenerationContext
from qianzhen.storygen.apothecary import generate
r=generate(GenerationContext(load_contract().site('apothecary'),20261024))
print(hashlib.sha256((r.blueprint_json+repr(r.semantics)).encode()).hexdigest())
'''
        expected = self.results[20261024]
        digest = hashlib.sha256((expected.blueprint_json + repr(expected.semantics)).encode()).hexdigest()
        actual = subprocess.check_output([sys.executable, '-c', script], cwd=ROOT, text=True).strip()
        self.assertEqual(actual, digest)

    def test_seeds_change_walls_room_area_and_rack_orientation_not_only_skin(self):
        self.assertEqual(len(self.representatives), 3)
        representatives = list(self.representatives.values())
        for i, first in enumerate(representatives):
            for second in representatives[i + 1:]:
                with self.subTest(first=first, second=second):
                    a, b = occupancy(self.blocks[first]), occupancy(self.blocks[second])
                    self.assertGreater(len(a ^ b), 150)
                    self.assertGreater(len({p for p in a ^ b if p[1] in (1, 2)}), 50)
        # The clinic grows two full usable rows; the preparation wing grows a bay.
        ward = {frozenset((x, z) for x in range(28, 38) for z in range(16, 24)
                          if blocks[x, 1, z] == blocks[x, 2, z] == 'minecraft:air')
                for blocks in self.blocks.values()}
        self.assertGreaterEqual(len(ward), 2)
        rack_sections = {tuple(blocks[x, 2, z] != 'minecraft:air'
                               for x, z in ((32, 28), (35, 25), (35, 33), (39, 28)))
                         for blocks in self.blocks.values()}
        self.assertEqual(len(rack_sections), 2)
        authored = assets.nbt.expand_structure(self.contract.blueprint())[2]
        for seed in representatives:
            self.assertGreater(len(occupancy(authored) ^ occupancy(self.blocks[seed])), 500)

    def test_every_seed_passes_existing_checks_and_exact_story_contract(self):
        baseline = self.contract.blueprint()
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                checked = validate_result(self.contract, result)
                self.assertTrue(all(checked['walks']['targets'].values()))
                self.assertGreaterEqual(len(checked['roof_sections']), 4)
                self.assertGreater(checked['wall_roof_contacts'], 50)
                self.assertEqual(checked['pairs_supports_water']['bed_halves'], 4)
                self.assertEqual(checked['pairs_supports_water']['door_halves'], 4)
                self.assertEqual(checked['pairs_supports_water']['water_cells'], 4)
                data, blocks = result.to_blueprint(), self.blocks[seed]
                for key in ('id', 'size', 'site', 'walk_targets', 'story_anchors', 'service_blocks', 'entities'):
                    self.assertEqual(data.get(key), baseline.get(key))
                for pos, state in self.contract.protected:
                    self.assertEqual(assets.canonical(blocks[pos]), state)
                for target in self.contract.targets.values():
                    self.assertEqual(blocks[tuple(target['pos'])], target['state'])
                    assets.check_target_sightline(blocks, 'sample_examination', target['pos'], target['approach'])
                for service in baseline['service_blocks']:
                    self.assertEqual(blocks[tuple(service['pos'])], service['state'])

    def test_apprentice_bed_starts_empty_with_reachable_planting_aisle(self):
        # Includes all three massing variants and the player's actual main seed.
        authored = assets.nbt.expand_structure(self.contract.blueprint())[2]
        for seed, blocks in [('authored', authored), *self.blocks.items()]:
            with self.subTest(seed=seed):
                entry = tuple(self.contract.blueprint()['walk_targets']['entry'])
                approach = tuple(self.contract.blueprint()['walk_targets']['apprentice_plot'])
                graph = walk_graph(blocks, self.contract.size)
                self.assertIn(approach, reachable(graph, entry))
                for x in range(34, 38):
                    for z in range(36, 39):
                        self.assertEqual(blocks[x, 0, z], 'myvillage:spirit_soil')
                        self.assertEqual(blocks[x, 1, z], 'minecraft:air')
                        self.assertEqual(blocks[x, 2, z], 'minecraft:air')
                        self.assertLessEqual(sum((a-b)**2 for a, b in zip((x, 1, z), approach)), 8**2)

    def test_spaces_describe_functional_volumes_and_actual_connected_paths(self):
        essential = {'front_shop', 'consultation', 'ward', 'archive', 'records',
                     'upper_gallery', 'work_gallery', 'courtyard', 'drying', 'alchemy'}
        space_extents = set()
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                spaces = {space.id: space for space in result.semantics.spaces}
                self.assertTrue(essential <= spaces.keys())
                self.assertGreaterEqual(len(result.semantics.adjacencies), len(spaces) - 1)
                assigned = set()
                site_volume = self.contract.size[0] * self.contract.size[1] * self.contract.size[2]
                for space in spaces.values():
                    volume = 1
                    for low, high in zip(space.minimum, space.maximum):
                        volume *= high - low + 1
                    self.assertLess(volume, site_volume // 3)
                    for name in space.walk_targets:
                        self.assertTrue(space.contains(self.contract.blueprint()['walk_targets'][name]))
                        assigned.add(name)
                self.assertEqual(assigned, set(self.contract.blueprint()['walk_targets']))
                self.assertTrue(spaces['consultation'].contains((36, 1, 12)))
                self.assertTrue(spaces['ward'].contains((35, 1, 19)))
                self.assertTrue(spaces['alchemy'].contains((7, 1, 35)))
                graph = walk_graph(self.blocks[seed], self.contract.size)
                semantic_graph = {name: set() for name in spaces}
                for edge in result.semantics.adjacencies:
                    semantic_graph[edge.source].add(edge.target)
                    semantic_graph[edge.target].add(edge.source)
                    allowed = (spaces[edge.source], spaces[edge.target])
                    self.assertGreaterEqual(len(edge.path), 2)
                    self.assertTrue(allowed[0].contains(edge.path[0]))
                    self.assertTrue(allowed[1].contains(edge.path[-1]))
                    self.assertTrue(all(any(s.contains(p) for s in allowed) for p in edge.path))
                    self.assertTrue(all(b in graph[a] for a, b in zip(edge.path, edge.path[1:])))
                self.assertEqual(reachable(semantic_graph, 'arrival'), set(spaces))
                space_extents.add(tuple((s.id, s.minimum, s.maximum) for s in spaces.values()))
        self.assertEqual(len(space_extents), 3)

    def test_all_story_feet_approaches_and_three_wide_road_connections_are_reachable(self):
        for seed, blocks in self.blocks.items():
            with self.subTest(seed=seed):
                required = dict(self.contract.blueprint()['walk_targets'])
                for key, role in self.contract.roles.items():
                    required[key + ':feet'] = role['pos']
                    required[key + ':approach'] = role['approach']
                for key, target in self.contract.targets.items():
                    required[key] = target['approach']
                checked = check_walks(blocks, required, self.contract.size)
                self.assertTrue(all(checked['targets'].values()))
                # Entrance, principal doors, clinic partition, furnace and back exit
                # retain real three-wide, three-high clear routes.
                openings = [((x, 1, 5) for x in range(18, 21)),
                            ((x, 1, 17) for x in range(20, 23)),
                            ((x, 1, 15) for x in range(30, 33)),
                            ((17, 1, z) for z in range(32, 35)),
                            ((x, 1, 40) for x in range(28, 31))]
                for opening in openings:
                    for x, y, z in opening:
                        self.assertTrue(all(blocks[x, y + dy, z] == 'minecraft:air' for dy in range(3)))
                for z, columns in ((0, range(18, 21)), (42, range(28, 31))):
                    for x in columns:
                        self.assertEqual(blocks[x, 0, z], 'minecraft:cut_sandstone')
                        self.assertEqual(blocks[x, 1, z], 'minecraft:air')
                        self.assertEqual(blocks[x, 2, z], 'minecraft:air')

    def test_three_variants_compose_with_authored_sites_npc_bodies_and_materialized_props(self):
        for seed in self.representatives.values():
            with self.subTest(seed=seed):
                selected = generate_sites(self.scene, seed, [SITE_ID])
                self.assertEqual(selected[SITE_ID], self.results[seed])
                layout, blueprints, report = compose_scene(self.scene, selected)
                self.assertEqual(report['status'], 'passed')
                self.assertEqual(report['road_building_overlap'], 0)
                self.assertTrue(report['all_player_approaches_reachable_with_npcs'])
                self.assertEqual(set(blueprints), set(self.scene.site_ids))
                self.assertEqual({t['id'] for t in layout['targets']}, assets.TARGETS)
                self.assertIn('deployment_runtime', report['not_verified'])
                for contract in self.scene.sites:
                    if contract.site != SITE_ID:
                        self.assertEqual(blueprints[contract.site], contract.blueprint())

    def test_geometry_is_generated_without_authored_ops_or_palette(self):
        # The contract snapshots still own fixed anchors, but removing the source
        # building must not remove this plugin's rooms, stairs or furniture.
        baseline = self.contract.blueprint()
        baseline['ops'] = []
        baseline['palette'] = {}
        without_geometry = replace(self.contract, blueprint_json=json.dumps(baseline))
        result = generate(GenerationContext(without_geometry, 4))
        self.assertEqual(result, self.results[4])

    def test_wrong_site_contract_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'own site contract'):
            generate(GenerationContext(self.scene.site('old_relay'), 0))


if __name__ == '__main__':
    unittest.main()
