"""阡陌驿离线空间生成的聚焦证据；不启动游戏或写入生产资源。"""
from collections import deque
from dataclasses import replace
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))

from qianzhen import story_assets as assets
from qianzhen.build import check_walks, validate, walk_graph
from qianzhen.storygen import (
    Adjacency, GenerationContext, SemanticPlan, compose_scene, generate_sites,
    load_contract, validate_result,
)
from qianzhen.storygen.relay_inn import SITE_ID, generate


def reachable(graph, start, blocked=()):
    blocked = set(blocked)
    seen, todo = {start}, deque([start])
    while todo:
        for pos in graph.get(todo.popleft(), ()):
            if pos not in blocked and pos not in seen:
                seen.add(pos)
                todo.append(pos)
    return seen


class RelayInnGenerationTest(unittest.TestCase):
    # 包含负数和大整数，覆盖马棚两种宽度/前沿、接待两种前后沿及回游廊开关。
    SEEDS = (0, 1, 2, 17, 20261024, -9, 2**63 + 1)

    @classmethod
    def setUpClass(cls):
        cls.scene = load_contract()
        cls.contract = cls.scene.site(SITE_ID)
        cls.results = {}
        cls.blocks = {}
        cls.graphs = {}
        for seed in cls.SEEDS:
            result = generate(GenerationContext(cls.contract, seed))
            cls.results[seed] = result
            cls.blocks[seed] = assets.nbt.expand_structure(result.to_blueprint())[2]
            cls.graphs[seed] = walk_graph(cls.blocks[seed], cls.contract.size)

    def test_same_seed_reproduces_complete_result_through_public_plugin_entry(self):
        self.assertEqual(SITE_ID, 'relay_inn')
        result = generate_sites(self.scene, 2, [SITE_ID])[SITE_ID]
        self.assertEqual(result, self.results[2])
        self.assertEqual(result.blueprint_json, self.results[2].blueprint_json)
        with self.assertRaisesRegex(ValueError, 'relay_inn'):
            generate(GenerationContext(self.scene.site('apothecary'), 2))

    def test_generation_does_not_depend_on_authored_shell_ops(self):
        # 保留唯一事实/保护格与材质，去掉原始壳体。若复制 authored 再改装饰，此项会失败。
        data = self.contract.blueprint()
        data['ops'] = []
        empty_shell = replace(self.contract, blueprint_json=json.dumps(data, ensure_ascii=False))
        self.assertEqual(generate(GenerationContext(empty_shell, 2)), self.results[2])

    def test_seeds_change_occupied_volume_and_actual_upper_route_layout(self):
        # 忽略材质和 y=0 铺装，防止只换 palette 或地砖也获得“几何差异”通过。
        occupied = {seed: {p for p, state in blocks.items() if p[1] > 0 and state != 'minecraft:air'}
                    for seed, blocks in self.blocks.items()}
        signatures = {frozenset(points) for points in occupied.values()}
        self.assertGreaterEqual(len(signatures), 6)
        for seed in (1, 2, 17, 20261024, 2**63 + 1):
            with self.subTest(seed=seed):
                self.assertGreater(len(occupied[0] ^ occupied[seed]), 100)
        # 回游廊不是报告文字：有楼板、有净空、有第二个客楼桥口，且能完整通行。
        with_loop, without_loop = self.blocks[2], self.blocks[0]
        for z in (27, 28):
            for x in (40, 41, 42):
                self.assertEqual(with_loop[x, 6, z], 'minecraft:spruce_planks')
                self.assertEqual(without_loop[x, 6, z], 'minecraft:air')
                self.assertIn((x, 7, z), self.graphs[2])
        self.assertIn((41, 7, 29), self.graphs[2])
        self.assertNotIn((41, 7, 29), self.graphs[0])
        central_only = {(x, 7, z) for x in (33, 34, 35) for z in (27, 28)}
        for seed, connected in ((2, True), (0, False)):
            seen = reachable(self.graphs[seed], (40, 7, 24), central_only)
            self.assertEqual((34, 7, 31) in seen, connected)

    def test_all_representative_seeds_keep_exact_contract_and_existing_validation(self):
        baseline = self.contract.blueprint()
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                data = result.to_blueprint()
                for key in ('id', 'size', 'site', 'walk_targets', 'story_anchors', 'service_blocks', 'entities'):
                    self.assertEqual(data.get(key), baseline.get(key))
                self.assertNotEqual(data['ops'], baseline['ops'])
                for pos, state in self.contract.protected:
                    self.assertEqual(assets.canonical(self.blocks[seed][pos]), state)
                for spec in self.contract.targets.values():
                    self.assertEqual(self.blocks[seed][tuple(spec['pos'])], spec['state'])
                checks = validate_result(self.contract, result)
                self.assertTrue(all(checks['walks']['targets'].values()))
                self.assertGreaterEqual(len(checks['roof_sections']), 7)
                self.assertEqual(checks['wall_roof_contacts'], 25)
                self.assertEqual(checks['pairs_supports_water']['bed_halves'], 8)
                self.assertEqual(checks['pairs_supports_water']['door_halves'], 8)
                self.assertEqual(checks['pairs_supports_water']['water_cells'], 3)

    def test_semantic_spaces_are_functional_and_every_adjacency_is_a_real_walk(self):
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                spaces = {space.id: space for space in result.semantics.spaces}
                self.assertEqual(len(spaces), 18)
                assigned = {target for space in spaces.values() for target in space.walk_targets}
                self.assertEqual(assigned, set(result.to_blueprint()['walk_targets']))
                self.assertTrue(spaces['arrival'].contains(self.contract.roles['spawn']['pos']))
                self.assertTrue(spaces['reception'].contains(self.contract.roles['guide']['pos']))
                self.assertTrue(spaces['reception'].contains(self.contract.roles['guide']['approach']))
                self.assertLess(spaces['arrival'].maximum[0], spaces['reception'].minimum[0])
                self.assertEqual(spaces['guest_gallery'].minimum[1], 6)
                self.assertEqual(len(result.semantics.adjacencies), 17)
                graph = self.graphs[seed]
                for edge in result.semantics.adjacencies:
                    self.assertTrue(spaces[edge.source].contains(edge.path[0]))
                    self.assertTrue(spaces[edge.target].contains(edge.path[-1]))
                    self.assertGreaterEqual(len(edge.path), 2)
                    for a, b in zip(edge.path, edge.path[1:]):
                        self.assertIn(b, graph[a])

    def test_every_target_and_role_reaches_both_levels_over_continuous_stairs(self):
        for seed, result in self.results.items():
            data, graph = result.to_blueprint(), self.graphs[seed]
            seen = reachable(graph, tuple(data['walk_targets']['entry']))
            required = {name: tuple(pos) for name, pos in data['walk_targets'].items()}
            for name, spec in self.contract.roles.items():
                required[name + ':feet'] = tuple(spec['pos'])
                required[name + ':approach'] = tuple(spec['approach'])
            for name, pos in required.items():
                with self.subTest(seed=seed, target=name):
                    self.assertIn(pos, seen)
            ascent = next(edge.path for edge in result.semantics.adjacencies
                          if edge.source == 'stairs' and edge.target == 'viewing_deck')
            self.assertEqual(sum(b[1] - a[1] for a, b in zip(ascent, ascent[1:])), 6)
            self.assertEqual(sum(b[1] > a[1] for a, b in zip(ascent, ascent[1:])), 6)
            # 切断三格宽楼梯的一整级，所有上层站位应一起失去连接。
            blocked = {(x, 4, 18) for x in (30, 31, 32)}
            ground_only = reachable(graph, tuple(data['walk_targets']['entry']), blocked)
            for name, pos in required.items():
                if pos[1] == 7:
                    self.assertNotIn(pos, ground_only, (seed, name))

    def test_guest_rooms_are_separate_enclosures_with_their_own_door_access(self):
        targets = self.contract.blueprint()['walk_targets']
        for seed in (0, 2):
            for x, name in zip((21, 27, 33, 39),
                               ('guest_west', 'guest_west_inner', 'guest_east_inner', 'guest_east')):
                with self.subTest(seed=seed, room=name):
                    seen = reachable(self.graphs[seed], tuple(targets['guest_rooms']), {(x, 7, 33)})
                    self.assertNotIn(tuple(targets[name]), seen)
                    self.assertTrue(self.blocks[seed][x, 7, 33].startswith('minecraft:spruce_door['))
                    self.assertEqual(self.blocks[seed][x, 12, 37], 'minecraft:spruce_planks')

    def test_existing_checks_reject_cut_stair_and_invented_adjacency(self):
        blocked = dict(self.blocks[2])
        for x in (30, 31, 32):
            blocked[x, 4, 18] = 'minecraft:stone'
        with self.assertRaisesRegex(ValueError, 'unreachable walk targets'):
            check_walks(blocked, self.contract.blueprint()['walk_targets'], self.contract.size)
        result = self.results[2]
        bad = Adjacency('gate', 'arrival', ((23, 1, 0), (9, 1, 7)))
        plan = SemanticPlan(result.semantics.spaces, (bad,))
        with self.assertRaisesRegex(ValueError, 'not walkable'):
            validate_result(self.contract, replace(result, semantics=plan))
        # 既有屋面门床校验也直接运行，而非由假 SemanticSpace 绕过。
        validate(result.to_blueprint(), self.contract.max_size)

    def test_scene_composition_keeps_roads_and_player_approaches_with_npc_occupancy(self):
        for seed in (0, 2):
            with self.subTest(seed=seed):
                layout, blueprints, report = compose_scene(self.scene, {SITE_ID: self.results[seed]})
                self.assertEqual(blueprints[SITE_ID]['id'], self.contract.blueprint()['id'])
                self.assertEqual({row['id'] for row in layout['targets']}, assets.TARGETS)
                self.assertTrue(report['all_player_approaches_reachable_with_npcs'])
                self.assertEqual(report['road_building_overlap'], 0)
                self.assertIn('deployment_runtime', report['not_verified'])


if __name__ == '__main__':
    unittest.main()
