"""青泉渠圃的 seeded 几何、剧情契约与真实离线拓扑回归。"""
from dataclasses import replace
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))

from qianzhen import story_assets as assets
from qianzhen.build import check_pairs_and_supports, check_walks, walk_graph
from qianzhen.storygen import (
    GenerationContext, SemanticPlan, compose_scene, generate_sites, load_contract, validate_result,
)
from qianzhen.storygen.canal_gardens import SITE_ID, generate


SEEDS = (0, 1, 2, 17, 20261024, -7, 999999999, 2**80)
AIR = 'minecraft:air'
WATER = 'minecraft:water[level=0]'


def expanded(result):
    return assets.nbt.expand_structure(result.to_blueprint())[2]


class CanalGardensGenerationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.scene = load_contract()
        cls.contract = cls.scene.site(SITE_ID)
        cls.results = {seed: generate(GenerationContext(cls.contract, seed)) for seed in SEEDS}
        cls.blocks = {seed: expanded(result) for seed, result in cls.results.items()}

    def test_same_seed_reproduces_blueprint_semantics_and_plugin_loading(self):
        self.assertEqual(SITE_ID, 'canal_gardens')
        context = GenerationContext(self.contract, 17)
        before = self.contract.blueprint_json
        first = generate(context)
        # 外部阶段的抽样不改变内部流；公开插件入口产生同一个结果。
        unrelated = context.rng('unrelated')
        for _ in range(1000):
            unrelated.random()
        self.assertEqual(first, generate(context))
        self.assertEqual(first, generate_sites(self.scene, 17, [SITE_ID])[SITE_ID])
        self.assertEqual(self.contract.blueprint_json, before)
        with self.assertRaisesRegex(ValueError, 'own site contract'):
            generate(GenerationContext(self.scene.site('apothecary'), 17))

    def test_seeds_change_bed_partition_canal_and_structural_massing(self):
        waters, bed_edges, structures = set(), set(), set()
        regions = {'shed': set(), 'pavilion': set(), 'bridges': set()}
        for seed, blocks in self.blocks.items():
            waters.add(frozenset(p for p, s in blocks.items() if s == WATER))
            bed_edges.add(frozenset(p for p, s in blocks.items()
                                    if s == 'minecraft:cut_sandstone' and p[1] == 4
                                    and 4 <= p[0] <= 19 and 21 <= p[2] <= 27))
            # 只计真实木石/屋面占格；植物、方块颜色和元数据变化不算几何差异。
            structural = frozenset(p for p, s in blocks.items() if p[1] >= 2 and
                                   any(word in s for word in ('sandstone', 'spruce_planks',
                                                             'spruce_log', 'deepslate_tile')))
            structures.add(structural)
            for key, bounds in (('shed', (14, 20, 4, 13)),
                                ('pavilion', (31, 44, 7, 13)),
                                ('bridges', (21, 30, 30, 37))):
                x0, x1, z0, z1 = bounds
                regions[key].add(frozenset(p for p in structural
                                          if x0 <= p[0] <= x1 and z0 <= p[2] <= z1))
            self.assertNotEqual(self.results[seed].blueprint_json, self.contract.blueprint_json)
        self.assertGreater(len(waters), 2)
        self.assertGreater(len(bed_edges), 2)
        self.assertEqual(len(structures), len(SEEDS))
        for name, variants in regions.items():
            with self.subTest(region=name):
                self.assertGreater(len(variants), 1)

    def test_all_representative_seeds_keep_exact_contract_and_existing_validation(self):
        authored = self.contract.blueprint()
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                checks = validate_result(self.contract, result)
                data, blocks = result.to_blueprint(), self.blocks[seed]
                for key in ('id', 'size', 'site', 'walk_targets', 'story_anchors',
                            'service_blocks', 'entities', 'signs'):
                    self.assertEqual(data.get(key), authored.get(key))
                for pos, state in self.contract.protected:
                    self.assertEqual(assets.canonical(blocks[pos]), state)
                for target in self.contract.targets.values():
                    self.assertEqual(blocks[tuple(target['pos'])], target['state'])
                    assets.check_target_sightline(blocks, target['site'], target['pos'], target['approach'])
                self.assertTrue(all(checks['walks']['targets'].values()))
                self.assertGreaterEqual(len(checks['roof_sections']), len(authored['roof_checks']))
                self.assertEqual(checks['pairs_supports_water']['wall_signs'], 4)

    def test_water_has_real_bottom_banks_and_never_supplies_walk_support(self):
        for seed, blocks in self.blocks.items():
            with self.subTest(seed=seed):
                checks = check_pairs_and_supports(blocks)
                waters = {p for p, state in blocks.items() if state == WATER}
                self.assertEqual(checks['water_cells'], len(waters))
                self.assertGreater(len(waters), 100)
                for x, y, z in waters:
                    for dx, dy, dz in ((0, -1, 0), (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1)):
                        self.assertNotEqual(blocks.get((x + dx, y + dy, z + dz), AIR), AIR)
                # 泉池、上渠、两座落水井、中渠和下游石槽属于一个真实水系。
                canal = {p for p in waters if 23 <= p[0] <= 29}
                seen, todo = {(25, 1, 4)}, [(25, 1, 4)]
                while todo:
                    x, y, z = todo.pop()
                    for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0),
                                       (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                        p = (x + dx, y + dy, z + dz)
                        if p in canal and p not in seen:
                            seen.add(p)
                            todo.append(p)
                self.assertEqual(seen, canal)
                self.assertEqual({p[1] for p in canal}, set(range(1, 8)))
                graph = walk_graph(blocks, self.contract.size)
                for x, y, z in graph:
                    self.assertNotEqual(blocks[x, y - 1, z], WATER)
                # 检查器仍拒绝真实破岸；不能删水体检查来让生成结果过关。
                leaking = dict(blocks)
                leaking[24, 1, 5] = AIR
                with self.assertRaisesRegex(ValueError, 'water opens'):
                    check_pairs_and_supports(leaking)
                unsupported = {(0, 0, 0): WATER}
                with self.assertRaisesRegex(ValueError, 'unsupported'):
                    check_walks(unsupported, {'entry': [0, 1, 0]}, [1, 4, 1])

    def test_irrigation_describes_generated_beds_and_supported_crops(self):
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                blocks = self.blocks[seed]
                for bed in result.to_blueprint()['irrigation_beds']:
                    x0, x1, z0, z1 = bed['bounds']
                    y = bed['soil_y']
                    wx, wy, wz = bed['water']
                    self.assertEqual(blocks[wx, wy, wz], WATER)
                    self.assertEqual(blocks[wx, wy - 1, wz], 'minecraft:sandstone')
                    for x in range(x0 + 1, x1):
                        for z in range(z0 + 1, z1):
                            if (x, y, z) == (wx, wy, wz):
                                continue
                            self.assertEqual(blocks[x, y, z], 'minecraft:farmland[moisture=7]')
                            self.assertIn('[age=', blocks[x, y + 1, z])
                            self.assertLessEqual(max(abs(x - wx), abs(z - wz)), 4)
                self.assertGreaterEqual(sum(s == 'minecraft:dead_bush' for s in blocks.values()), 3)

    def test_semantic_spaces_are_real_and_adjacencies_cross_both_actual_stair_routes(self):
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                blocks = self.blocks[seed]
                graph = walk_graph(blocks, self.contract.size)
                spaces = {space.id: space for space in result.semantics.spaces}
                links = {(edge.source, edge.target): edge for edge in result.semantics.adjacencies}
                self.assertEqual(len(spaces), 16)
                self.assertEqual(len(links), 16)
                self.assertTrue(all(len(space.walk_targets) == 1 for space in spaces.values()))
                for edge in result.semantics.adjacencies:
                    self.assertGreater(len(edge.path), 2)
                    self.assertTrue(spaces[edge.source].contains(edge.path[0]))
                    self.assertTrue(spaces[edge.target].contains(edge.path[-1]))
                    for a, b in zip(edge.path, edge.path[1:]):
                        self.assertIn(b, graph[a])
                for pair, x, z0, floor in ((('lower_beds', 'middle_west'), 9, 15, 1),
                                          (('sample_pavilion', 'middle_east'), 37, 15, 1),
                                          (('middle_west', 'upper_beds'), 12, 27, 4),
                                          (('middle_east', 'gatehouse'), 37, 27, 4)):
                    path = links[pair].path
                    for i in range(3):
                        self.assertIn((x, floor + i + 2, z0 + i), path)
                # 构件真实存在；岸廊柱不能浮在沉砂池水面上。
                for x in (20, 24):
                    for z in (19, 24, 28):
                        self.assertEqual(blocks[x, 4, z], 'minecraft:sandstone')
                        self.assertEqual(blocks[x, 5, z], 'minecraft:spruce_log[axis=y]')
                for (x, y, z), state in blocks.items():
                    if state == 'minecraft:spruce_log[axis=y]' and y > 0:
                        self.assertNotIn(blocks.get((x, y - 1, z), AIR), (AIR, WATER))
                self.assertEqual(blocks[38, 1, 11], 'minecraft:spruce_planks')
                self.assertEqual(blocks[26, 2, 11], 'minecraft:spruce_planks')
                self.assertEqual(blocks[26, 8, 33], 'minecraft:spruce_planks')
                for x in range(20, 25):
                    self.assertEqual(blocks[x, 1, 0], 'minecraft:smooth_sandstone')

    def test_invalid_semantic_path_and_changed_target_state_still_fail(self):
        result = self.results[0]
        edge = result.semantics.adjacencies[0]
        bad_edge = replace(edge, path=(edge.path[0], edge.path[-1]))
        bad = replace(result, semantics=SemanticPlan(result.semantics.spaces, (bad_edge,)))
        with self.assertRaisesRegex(ValueError, 'not walkable'):
            validate_result(self.contract, bad)
        data = result.to_blueprint()
        data['ops'].append({'op': 'set', 'pos': [39, 2, 11],
                            'state': 'minecraft:water_cauldron[level=2]'})
        with self.assertRaisesRegex(ValueError, 'protected story cell'):
            validate_result(self.contract, replace(result, blueprint_json=json.dumps(data)))

    def test_mixed_scene_keeps_roads_targets_and_every_approach_reachable(self):
        for seed in (0, 20261024):
            with self.subTest(seed=seed):
                layout, blueprints, report = compose_scene(self.scene, {SITE_ID: self.results[seed]})
                self.assertEqual(blueprints[SITE_ID], self.results[seed].to_blueprint())
                self.assertEqual({target['id'] for target in layout['targets']}, assets.TARGETS)
                self.assertEqual(report['road_building_overlap'], 0)
                self.assertTrue(report['all_player_approaches_reachable_with_npcs'])
                self.assertIn('deployment_runtime', report['not_verified'])


if __name__ == '__main__':
    unittest.main()
