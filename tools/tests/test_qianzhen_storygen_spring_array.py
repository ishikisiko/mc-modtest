"""护泉古阵的离线剖面、剧情保护和真实几何变体；不启动游戏。"""
from collections import deque
from itertools import combinations
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))

from qianzhen import story_assets as assets
from qianzhen.build import validate, walk_graph
from qianzhen.storygen import GenerationContext, compose_scene, generate_sites, load_contract, validate_result
from qianzhen.storygen.spring_array import SITE_ID, generate


def reachable(graph, start, allowed=lambda p: True, blocked=frozenset()):
    start = tuple(start)
    if start not in graph or start in blocked or not allowed(start):
        return set()
    seen, todo = {start}, deque([start])
    while todo:
        for neighbor in graph[todo.popleft()]:
            if neighbor not in seen and neighbor not in blocked and allowed(neighbor):
                seen.add(neighbor)
                todo.append(neighbor)
    return seen


class SpringArrayStorygenTest(unittest.TestCase):
    SEEDS = (0, 1, 17, 42, -7, 20261024)

    @classmethod
    def setUpClass(cls):
        cls.scene = load_contract()
        cls.contract = cls.scene.site(SITE_ID)
        cls.results = {seed: generate(GenerationContext(cls.contract, seed)) for seed in cls.SEEDS}
        cls.data = {seed: result.to_blueprint() for seed, result in cls.results.items()}
        cls.blocks = {seed: assets.nbt.expand_structure(data)[2] for seed, data in cls.data.items()}
        cls.graphs = {seed: walk_graph(blocks, cls.contract.size) for seed, blocks in cls.blocks.items()}

    def test_same_seed_reproduces_after_other_seeds_and_plugin_dispatch(self):
        self.assertEqual(generate(GenerationContext(self.contract, 17)), self.results[17])
        self.assertEqual(generate_sites(self.scene, 17, [SITE_ID]), {SITE_ID: self.results[17]})
        other = self.scene.site('old_relay')
        with self.assertRaisesRegex(ValueError, 'own site contract'):
            generate(GenerationContext(other, 17))

    def test_seeds_change_occupied_geometry_in_upper_and_underground_spaces(self):
        occupied = {seed: {p for p, s in blocks.items() if s != 'minecraft:air'}
                    for seed, blocks in self.blocks.items()}
        upper = {seed: frozenset(p for p in points if p[1] >= 13) for seed, points in occupied.items()}
        underground = {seed: frozenset(p for p in points if 41 <= p[0] <= 45 and 2 <= p[1] <= 7
                                      and 25 <= p[2] <= 34) for seed, points in occupied.items()}
        self.assertEqual(len(set(upper.values())), len(self.SEEDS))
        self.assertGreaterEqual(len(set(underground.values())), 2)
        for a, b in combinations(self.SEEDS, 2):
            with self.subTest(a=a, b=b):
                # 占用体素比较排除材质、文字、灯光和 ops 序列差异。
                self.assertGreater(len(occupied[a] ^ occupied[b]), 100)
        baseline = assets.nbt.expand_structure(self.contract.blueprint())[2]
        authored = {p for p, state in baseline.items() if state != 'minecraft:air'}
        for seed in self.SEEDS:
            self.assertGreater(len(occupied[seed] ^ authored), 500)

    def test_all_representative_seeds_pass_contract_existing_validator_and_exact_states(self):
        baseline = self.contract.blueprint()
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                validate_result(self.contract, result)
                blocks, _, checks = validate(self.data[seed], self.contract.max_size)
                for key in ('id', 'size', 'site', 'walk_targets', 'story_anchors', 'service_blocks', 'entities'):
                    self.assertEqual(self.data[seed].get(key), baseline.get(key))
                for pos, state in self.contract.protected:
                    self.assertEqual(assets.canonical(blocks[pos]), state)
                for key, spec in self.contract.targets.items():
                    self.assertEqual(blocks[tuple(spec['pos'])], spec['state'], key)
                self.assertEqual(len(checks['roof_sections']), 4)
                self.assertGreater(checks['pairs_supports_water']['water_cells'], 0)
                self.assertEqual(self.data[seed]['roof_checks'][0], baseline['roof_checks'][0])

    def test_every_walk_target_role_and_player_approach_reachable(self):
        for seed, graph in self.graphs.items():
            with self.subTest(seed=seed):
                targets = self.data[seed]['walk_targets']
                seen = reachable(graph, targets['entry'])
                for key, pos in targets.items():
                    self.assertIn(tuple(pos), seen, key)
                for key, spec in self.contract.roles.items():
                    self.assertIn(tuple(spec['pos']), seen, key)
                    self.assertIn(tuple(spec['approach']), seen, key)
                for key, spec in self.contract.targets.items():
                    self.assertIn(tuple(spec['approach']), seen, key)
                # 守阵人占位时，玩家仍可走到全部交互 approach。
                blocked = {tuple(spec['pos']) for spec in self.contract.roles.values()}
                with_npc = reachable(graph, targets['entry'], blocked=blocked)
                for spec in (*self.contract.roles.values(), *self.contract.targets.values()):
                    self.assertIn(tuple(spec['approach']), with_npc)

    def test_semantic_edges_are_real_and_cover_functional_spaces(self):
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                spaces = {space.id: space for space in result.semantics.spaces}
                self.assertTrue({'gate', 'spring_eye', 'spring_court', 'maintenance', 'overlook',
                                 'west_gallery', 'east_gallery', 'foot_nw', 'foot_ne', 'foot_sw', 'foot_se'} <= spaces.keys())
                connected = set()
                graph = self.graphs[seed]
                for edge in result.semantics.adjacencies:
                    self.assertGreater(len(edge.path), 1)
                    self.assertTrue(spaces[edge.source].contains(edge.path[0]))
                    self.assertTrue(spaces[edge.target].contains(edge.path[-1]))
                    for a, b in zip(edge.path, edge.path[1:]):
                        self.assertIn(b, graph[a])
                    connected.update((edge.source, edge.target))
                self.assertEqual(connected, set(spaces))
                links = {name: set() for name in spaces}
                for edge in result.semantics.adjacencies:
                    links[edge.source].add(edge.target)
                    links[edge.target].add(edge.source)
                seen, todo = {'gate'}, deque(['gate'])
                while todo:
                    for neighbor in links[todo.popleft()] - seen:
                        seen.add(neighbor)
                        todo.append(neighbor)
                self.assertEqual(seen, set(spaces))
                for space in spaces.values():
                    self.assertTrue(space.walk_targets)
                    self.assertLess(space.maximum[2] - space.minimum[2], self.contract.size[2] - 1)

    def test_dual_descents_and_upper_routes_are_independent_and_multilevel(self):
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                edges = result.semantics.adjacencies
                paths = []
                for side, low, high in (('west', 2, 12), ('east', 34, 44)):
                    descent = next(e.path for e in edges if e.source == side + '_descent'
                                   and e.target == 'spring_court')
                    self.assertEqual((descent[0][1], descent[-1][1]), (8, 3))
                    self.assertEqual({p[1] for p in descent}, set(range(3, 9)))
                    self.assertTrue(all(low <= p[0] <= high for p in descent))
                    paths.append(set(descent))
                    upper = next(e.path for e in edges if e.source == side + '_gallery'
                                 and e.target == side + '_upper')
                    self.assertEqual((upper[0][1], upper[-1][1]), (8, 13))
                    self.assertTrue(all(low <= p[0] <= high for p in upper))
                self.assertFalse(paths[0] & paths[1])
                graph, t = self.graphs[seed], self.data[seed]['walk_targets']
                for blocked_path in paths:
                    self.assertIn(tuple(t['array_core']), reachable(graph, t['gate_landing'], blocked=blocked_path))

    def test_maintenance_annex_has_floor_ceiling_and_a_real_three_wide_door(self):
        starts = set()
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                edge = next(e for e in result.semantics.adjacencies
                            if e.source == 'maintenance' and e.target == 'east_descent')
                annex_start = edge.path[0][2] - 2
                starts.add(annex_start)
                blocks = self.blocks[seed]
                for z in range(annex_start + 1, annex_start + 4):
                    for y in range(3, 6):
                        self.assertEqual(blocks[41, y, z], 'minecraft:air')
                for x in range(42, 45):
                    for z in range(annex_start + 1, annex_start + 5):
                        self.assertEqual(blocks[x, 2, z], 'minecraft:stone_bricks')
                        self.assertEqual(blocks[x, 7, z], 'minecraft:smooth_sandstone')
                        for y in (4, 5, 6):
                            self.assertEqual(blocks[x, y, z], 'minecraft:air')
                route = reachable(self.graphs[seed], self.data[seed]['walk_targets']['maintenance_bench'],
                                  allowed=lambda p: p[1] == 3)
                self.assertIn(edge.path[0], route)
                # 前沿平廊上/下相叠；后沿 z>=31 已进入后山升阶，不能假设脚高仍为8。
                lower, upper = (42, 3, annex_start + 1), (42, 8, annex_start + 1)
                self.assertIn(lower, route)
                seen_upper = reachable(self.graphs[seed], self.data[seed]['walk_targets']['east_descent_top'],
                                       allowed=lambda p: p[1] >= 8 and p[0] >= 34)
                self.assertIn(upper, seen_upper)
        self.assertGreaterEqual(len(starts), 2)

    def test_mixed_scene_composes_with_existing_roads_npc_and_target_checks(self):
        _, authored, report = compose_scene(self.scene, {SITE_ID: self.results[17]})
        self.assertEqual(authored[SITE_ID]['walk_targets'], self.contract.blueprint()['walk_targets'])
        self.assertTrue(report['all_player_approaches_reachable_with_npcs'])
        self.assertIn('deployment_runtime', report['not_verified'])


if __name__ == '__main__':
    unittest.main()
