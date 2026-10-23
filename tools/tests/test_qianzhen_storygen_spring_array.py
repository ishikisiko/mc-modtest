"""护泉古阵的离线剖面、剧情保护和真实几何变体；不启动游戏。"""
from collections import deque
import hashlib
from itertools import combinations
import json
import math
from pathlib import Path
import re
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


def ray_obstructions(blocks, eye, target, visible=lambda p: True):
    destination = tuple(math.floor(v) for v in target)
    return [p for p in assets.sightline_cells(eye, target)
            if p != destination and visible(p) and blocks.get(p, 'minecraft:air') != 'minecraft:air']


def in_frame(eye, target, point, margin=.9):
    """现有 viewer 的 43° 透视，采用更窄的 16:9，四边留 10% 余量。"""
    def unit(v):
        length = math.sqrt(sum(n*n for n in v))
        return tuple(n / length for n in v)

    forward = unit(tuple(b-a for a, b in zip(eye, target)))
    right = unit((-forward[2], 0, forward[0]))
    up = (right[1]*forward[2]-right[2]*forward[1],
          right[2]*forward[0]-right[0]*forward[2],
          right[0]*forward[1]-right[1]*forward[0])
    delta = tuple(b-a for a, b in zip(eye, point))
    dot = lambda a, b: sum(x*y for x, y in zip(a, b))
    depth = dot(delta, forward)
    half_height = depth * math.tan(math.radians(43/2))
    return (depth > .1 and abs(dot(delta, right)) < half_height * (16/9) * margin
            and abs(dot(delta, up)) < half_height * margin)


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
        self.assertEqual(starts, {25, 27, 29})

    def test_mixed_scene_composes_with_existing_roads_npc_and_target_checks(self):
        _, authored, report = compose_scene(self.scene, {SITE_ID: self.results[17]})
        self.assertEqual(authored[SITE_ID]['walk_targets'], self.contract.blueprint()['walk_targets'])
        self.assertTrue(report['all_player_approaches_reachable_with_npcs'])
        self.assertIn('deployment_runtime', report['not_verified'])

    def test_review_only_fix_preserves_pre_fix_geometry_for_every_representative_seed(self):
        # 29d4b4f 实际展开方块（含空气/完整 state）的基线；镜头修复不能偷偷开墙。
        expected = {
            0: '18ae621f1602c96b9a8fde09c89c5a6fc97aebdc8ff8aea4df3bb6b6bfa44dbc',
            1: '87d142c9511bed2223e09356edc70af7002b2d0b4f0663342c3c0285a2822768',
            17: '4b22fec1f25a79164793eeeaaf1b6b55adaad843237461bb595b730b0e625fae',
            42: '972f9bf9d8fc0229887418a8a255fa11ef6ee0aa7d2409c51102f260c15f7601',
            -7: '1a69658f8e0ffe9fda309b8b7a1657bac00c4c536e776630d497582f0d503447',
            20261024: '0eb108de102d1b690c09e3fb3f9e1994bca463d4c375b70e96336739e4d8b064',
        }
        for seed, blocks in self.blocks.items():
            with self.subTest(seed=seed):
                encoded = json.dumps(sorted(blocks.items()), separators=(',', ':')).encode()
                self.assertEqual(hashlib.sha256(encoded).hexdigest(), expected[seed])

    def test_review_eyes_are_real_reachable_stances_and_focus_visible_without_any_cut(self):
        for seed, data in self.data.items():
            p = data['presentation']
            maintenance = next(v for v in p['review_views'] if v['id'] == 'maintenance_interior')
            for eye, target in ((p['eye'], p['eye_target']), (maintenance['eye'], maintenance['target'])):
                with self.subTest(seed=seed, eye=eye):
                    feet = (math.floor(eye[0]), round(eye[1] - 1.62), math.floor(eye[2]))
                    self.assertIn(feet, reachable(self.graphs[seed], data['walk_targets']['entry']))
                    self.assertAlmostEqual(eye[1] - feet[1], 1.62)
                    self.assertEqual(ray_obstructions(self.blocks[seed], eye, target), [])
            # 实际任务点击独立于审阅镜头；不能用取景通过替代 approach 视线。
            for key, spec in self.contract.targets.items():
                assets.check_target_sightline(self.blocks[seed], key, spec['pos'], spec['approach'])

    def test_court_eye_has_no_near_wall_across_the_frame_with_old_shot_negative_control(self):
        def near_wall(blocks, eye, target):
            yaw = math.atan2(target[0]-eye[0], target[2]-eye[2])
            return [p for degrees in range(-35, 36, 5)
                    for p in ray_obstructions(blocks, eye,
                        (eye[0]+4*math.sin(yaw+math.radians(degrees)), eye[1],
                         eye[2]+4*math.cos(yaw+math.radians(degrees))))]

        for seed, data in self.data.items():
            with self.subTest(seed=seed):
                p = data['presentation']
                self.assertEqual(near_wall(self.blocks[seed], p['eye'], p['eye_target']), [])
                # game_evidence.INTERIORS 的原 east_descent_foot 固定镜头。
                self.assertTrue(near_wall(self.blocks[seed], (37.5, 4.62, 23.5), (23, 4, 27)))

    def test_section_views_are_consumed_and_frame_court_room_annex_and_vertical_context(self):
        existing = {'full', 'front', 'rear', 'entry', 'cut', 'top', 'upper'}
        for seed, data in self.data.items():
            p = data['presentation']
            views = {v['id']: v for v in p['review_views']}
            self.assertEqual(len(views), len(p['review_views']))
            for view in views.values():
                self.assertTrue(view['id'] in existing or
                                re.fullmatch(r'[xyz] >= \d+', view.get('remove', '')))
            sections = [
                (views['cut'], lambda cell: cell[1] < p['cut_y'],
                 # 剧情脚部 x=15/31 在柱旁；真实铜阵脚位于 x=16/30。
                 [(x+.5, 4.5, z+.5) for x in (16, 30) for z in (22, 34)]),
                (views['maintenance_plan_cut'], lambda cell: cell[1] < 7,
                 # 取实际空闲地坪与家具顶面，不要求看穿北侧桶架下面的地砖。
                 [(x+.5, 2.5, z+.5) for x in (36, 40) for z in (26, 34)] +
                 [(40.5, 4.5, 25.5), (40.5, 3.5, 33.5), (39.5, 3.5, 30.5),
                  (43.5, 2.5, next(e.path[0][2] for e in self.results[seed].semantics.adjacencies
                                    if e.source == 'maintenance' and e.target == 'east_descent')+.5)]),
                (views['underground_side_cut'], lambda cell: cell[0] < 41,
                 [(40.5, y, z) for y in (.5, 7.5, 15.5) for z in (15.5, 34.5)]),
            ]
            for view, visible, points in sections:
                for point in points:
                    with self.subTest(seed=seed, view=view['id'], point=point):
                        self.assertTrue(in_frame(view['eye'], view['target'], point))
                        # 构件检查另有完整几何测试；剖视里关键可读点也必须真正可见。
                        if view['id'] != 'underground_side_cut':
                            self.assertEqual(ray_obstructions(self.blocks[seed], view['eye'], point, visible), [])
            side = views['underground_side_cut']
            # 斜向看地砖时取其上表面，避免误将相邻同层地砖内部当作挡墙；
            # 顶板和屋面的断面则取切面后第一格，三层都必须真实可读。
            for point in ((38.5, 3.01, 28.5), (40.99, 7.5, 28.5), (40.99, 13.5, 25.5)):
                with self.subTest(seed=seed, side_surface=point):
                    self.assertTrue(in_frame(side['eye'], side['target'], point))
                    self.assertEqual(ray_obstructions(self.blocks[seed], side['eye'], point,
                                                      lambda cell: cell[0] < 41), [])


if __name__ == '__main__':
    unittest.main()
