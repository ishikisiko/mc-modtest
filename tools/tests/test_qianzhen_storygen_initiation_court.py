"""问灵小院的 seeded 几何、真实仪式空间及部署兼容性；纯离线。"""
from collections import deque
from dataclasses import replace
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from qianzhen import story_assets as assets
from qianzhen.build import check_walks, validate, walk_graph
from qianzhen.game_routes import opened_door_graph
from qianzhen.storygen import GenerationContext, compose_scene, generate_sites, load_contract, validate_result
from qianzhen.storygen.initiation_court import SITE_ID, generate


def reachable(graph, start):
    start = tuple(start) if isinstance(start, list) else start
    seen, pending = {start}, deque([start])
    while pending:
        for other in graph[pending.popleft()]:
            if other not in seen:
                seen.add(other)
                pending.append(other)
    return seen


class InitiationCourtTest(unittest.TestCase):
    SEEDS = (0, 1, 2, 7, 42, -1, 20261024)

    @classmethod
    def setUpClass(cls):
        cls.scene = load_contract()
        cls.contract = cls.scene.site(SITE_ID)
        cls.baseline = cls.contract.blueprint()
        cls.results = {seed: generate(GenerationContext(cls.contract, seed)) for seed in cls.SEEDS}
        cls.blocks = {seed: assets.nbt.expand_structure(result.to_blueprint())[2]
                      for seed, result in cls.results.items()}

    def test_same_seed_repeats_after_other_generations_without_mutating_contract(self):
        before = self.contract.blueprint_json
        for seed in (0, 20261024):
            self.assertEqual(generate(GenerationContext(self.contract, seed)), self.results[seed])
        self.assertEqual(self.contract.blueprint_json, before)
        self.assertEqual(self.contract.blueprint(), self.baseline)

    def test_reproducibility_across_process_hash_seeds(self):
        code = """
import hashlib, sys
sys.path.insert(0, 'tools')
from qianzhen.storygen import load_contract, GenerationContext
from qianzhen.storygen.initiation_court import generate
r = generate(GenerationContext(load_contract().site('initiation_court'), 20261024))
print(hashlib.sha256((r.blueprint_json + repr(r.semantics)).encode()).hexdigest())
"""
        result = self.results[20261024]
        expected = hashlib.sha256((result.blueprint_json + repr(result.semantics)).encode()).hexdigest()
        for seed in ('1', '777'):
            output = subprocess.check_output([sys.executable, '-c', code], cwd=ROOT,
                                             env={**os.environ, 'PYTHONHASHSEED': seed}, text=True)
            self.assertEqual(output.strip(), expected)

    def test_seeds_change_structural_occupancy_and_multiple_space_bounds(self):
        # 只比较非空气格位置，换方块材质、文字或花草无法满足此断言。
        occupied = {seed: frozenset(pos for pos, state in blocks.items()
                                    if pos[1] >= 3 and state != 'minecraft:air')
                    for seed, blocks in self.blocks.items()}
        self.assertEqual(len(set(occupied.values())), len(self.SEEDS))
        for seed in self.SEEDS[1:]:
            self.assertTrue(occupied[0] - occupied[seed])
            self.assertTrue(occupied[seed] - occupied[0])
        for space_id in ('testing', 'inheritance', 'west_cloister', 'return_gallery',
                         'quiet_terrace', 'observation_pavilion'):
            bounds = {(space.minimum, space.maximum) for result in self.results.values()
                      for space in result.semantics.spaces if space.id == space_id}
            self.assertGreater(len(bounds), 1, space_id)
        self.assertNotEqual(occupied[0], frozenset(pos for pos, state in
                            assets.nbt.expand_structure(self.baseline)[2].items()
                            if pos[1] >= 3 and state != 'minecraft:air'))

    def test_all_seeds_keep_full_contract_and_pass_existing_validation(self):
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                data, blocks = result.to_blueprint(), self.blocks[seed]
                for key in ('id', 'size', 'site', 'walk_targets', 'story_anchors', 'service_blocks', 'entities'):
                    self.assertEqual(data.get(key), self.baseline.get(key), key)
                for pos, state in self.contract.protected:
                    self.assertEqual(assets.canonical(blocks[pos]), state, pos)
                checks = validate_result(self.contract, result)
                self.assertTrue(all(checks['walks']['targets'].values()))
                self.assertTrue(checks['roof_sections'])
                self.assertGreater(checks['wall_roof_contacts'], 0)
                self.assertEqual(checks['pairs_supports_water']['door_halves'], 4)
                self.assertEqual(checks['pairs_supports_water']['water_cells'], 10)
        # 原 validate 本身同样接受导出的最终 DSL。
        validate(self.results[20261024].to_blueprint(), self.contract.max_size)

    def test_semantic_spaces_describe_distinct_built_rituals_and_real_roofs(self):
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                spaces = {s.id: s for s in result.semantics.spaces}
                blocks = self.blocks[seed]
                testing, hall = spaces['testing'], spaces['inheritance']
                self.assertLess(testing.maximum[2], hall.minimum[2])
                self.assertTrue(testing.contains((9, 4, 22)))
                self.assertTrue(hall.contains((12, 5, 37)))
                self.assertEqual(blocks[9, 7, 20], 'minecraft:deepslate_tiles')
                self.assertEqual(blocks[12, 8, 35], 'minecraft:deepslate_tiles')
                self.assertEqual(blocks[19, 7, 20], 'minecraft:air')  # 院心露天
                for key in ('west_cloister', 'return_gallery'):
                    space = spaces[key]
                    for name in space.walk_targets:
                        x, _, z = self.baseline['walk_targets'][name]
                        self.assertEqual(blocks[x, 7, z], 'minecraft:deepslate_tiles')
                west, east = testing.minimum[0], testing.maximum[0]
                front, back = testing.minimum[2], testing.maximum[2]
                for x, z in ((west + 2, front), (east - 2, front), (west, back - 2), (east, back - 2)):
                    self.assertEqual(blocks[x, 4, z], self.baseline['palette']['post'])
                # 每个声明都只覆盖局部功能区，不用全场包围盒冒充空间。
                for space in spaces.values():
                    self.assertLess(space.maximum[0] - space.minimum[0], self.contract.size[0] - 1)
                    self.assertLess(space.maximum[2] - space.minimum[2], self.contract.size[2] - 1)

    def test_adjacencies_are_connected_walk_paths_through_the_declared_spaces(self):
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                graph = walk_graph(self.blocks[seed], self.contract.size)
                spaces = {s.id: s for s in result.semantics.spaces}
                links = {s: set() for s in spaces}
                for edge in result.semantics.adjacencies:
                    a, b = spaces[edge.source], spaces[edge.target]
                    self.assertTrue(a.contains(edge.path[0]))
                    self.assertTrue(b.contains(edge.path[-1]))
                    self.assertNotEqual(edge.path[0], edge.path[-1])
                    for first, second in zip(edge.path, edge.path[1:]):
                        self.assertIn(second, graph[first])
                    self.assertTrue(all(a.contains(pos) or b.contains(pos) for pos in edge.path), edge)
                    links[a.id].add(b.id)
                    links[b.id].add(a.id)
                self.assertEqual(reachable(links, 'forecourt'), set(spaces))

    def test_all_targets_and_exact_facilities_remain_reachable_with_open_door_panels(self):
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                data, blocks = result.to_blueprint(), self.blocks[seed]
                required = dict(data['walk_targets'])
                for key, spec in self.contract.roles.items():
                    required[key + ':feet'], required[key + ':approach'] = spec['pos'], spec['approach']
                for key, spec in self.contract.targets.items():
                    required[key + ':approach'] = spec['approach']
                    self.assertEqual(assets.canonical(blocks[tuple(spec['pos'])]), assets.canonical(spec['state']))
                for service in data['service_blocks']:
                    approach = data['walk_targets'][service['approach']]
                    required[service['name']] = approach
                    self.assertEqual(blocks[tuple(service['pos'])], service['state'])
                    assets.check_target_sightline(blocks, service['name'], service['pos'], approach)
                self.assertEqual(len(data['service_blocks']), 2)
                check_walks(blocks, required, self.contract.size)
                graph = opened_door_graph(walk_graph(blocks, self.contract.size), blocks)
                seen = reachable(graph, required['entry'])
                for key, pos in required.items():
                    self.assertIn(tuple(pos), seen, key)

    def test_plugin_and_mixed_scene_preserve_optional_branch_deployment(self):
        generated = generate_sites(self.scene, 2, [SITE_ID])
        self.assertEqual(generated[SITE_ID], self.results[2])
        for seed in (2, 20261024):
            layout, blueprints, report = compose_scene(self.scene, {SITE_ID: self.results[seed]})
            self.assertTrue(report['all_player_approaches_reachable_with_npcs'])
            self.assertEqual({r['id'] for r in layout['targets']}, assets.TARGETS)
            self.assertEqual(blueprints[SITE_ID]['walk_targets'], self.baseline['walk_targets'])
            row = next(r for r in layout['sites'] if r['id'] == SITE_ID)
            self.assertEqual(row['origin'], list(self.contract.origin))
        manifest = json.loads(self.scene.manifest_json)
        self.assertEqual(next(r for r in manifest['locations'] if r['id'] == SITE_ID)['act'], '可选入门')
        chapter = json.loads((ROOT / 'src/main/resources/data/myvillage/story/qianzhen_chapter_1.json').read_text())
        self.assertNotIn('initiation_court', json.dumps(chapter))

    def test_wrong_site_is_rejected(self):
        with self.assertRaisesRegex(ValueError, '其他地点'):
            generate(GenerationContext(self.scene.site('apothecary'), 0))

    def test_validation_rejects_broken_semantic_adjacency(self):
        result = self.results[0]
        edge = result.semantics.adjacencies[0]
        broken = replace(edge, path=(edge.path[0], edge.path[-1]))
        plan = replace(result.semantics, adjacencies=(broken,) + result.semantics.adjacencies[1:])
        with self.assertRaisesRegex(ValueError, 'not walkable'):
            validate_result(self.contract, replace(result, semantics=plan))


if __name__ == '__main__':
    unittest.main()
