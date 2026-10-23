"""沙埋旧驿真实体块、剧情接口与双层调查回环的离线检查。"""
from collections import deque
from dataclasses import replace
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from qianzhen import story_assets as assets
from qianzhen.build import check_walks, validate, walk_graph
from qianzhen.storygen import (
    GenerationContext, SiteBuilder, compose_scene, generate_sites, load_contract, validate_result,
)
from qianzhen.storygen import old_relay


SEEDS = (0, 1, 2, 7, 17, 20261024, -42, 2**63 - 1)
AIR = 'minecraft:air'


def reachable(graph, start, blocked=()):
    blocked = set(blocked)
    start = tuple(start)
    seen, todo = {start}, deque([start])
    while todo:
        for p in graph.get(todo.popleft(), ()):
            if p not in seen and p not in blocked:
                seen.add(p)
                todo.append(p)
    return seen


class OldRelayStorygenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.scene = load_contract()
        cls.contract = cls.scene.site('old_relay')
        cls.results = {}
        cls.blocks = {}
        cls.graphs = {}
        for seed in SEEDS:
            result = old_relay.generate(GenerationContext(cls.contract, seed))
            cls.results[seed] = result
            cls.blocks[seed] = assets.nbt.expand_structure(result.to_blueprint())[2]
            cls.graphs[seed] = walk_graph(cls.blocks[seed], cls.contract.size)

    def test_same_seed_is_reproducible_and_plugin_loads_without_authored_copy(self):
        expected = self.results[17]
        self.assertEqual(old_relay.SITE_ID, 'old_relay')
        self.assertEqual(old_relay.generate(GenerationContext(self.contract, 17)), expected)
        self.assertEqual(generate_sites(self.scene, 17, ['old_relay']), {'old_relay': expected})
        # Emptying the authored geometry must not affect the generated structure.
        # Keep only its metadata, palette and independently extracted fixed sockets.
        data = self.contract.blueprint()
        data['ops'] = []
        contract = replace(self.contract, blueprint_json=json.dumps(data, ensure_ascii=False))
        with patch.object(old_relay, 'SiteBuilder', wraps=SiteBuilder) as builder:
            self.assertEqual(old_relay.generate(GenerationContext(contract, 17)), expected)
        builder.assert_called_once_with(contract)
        with self.assertRaisesRegex(ValueError, 'own site contract'):
            old_relay.generate(GenerationContext(self.scene.site('relay_inn'), 17))

    def test_different_seeds_have_three_real_geometry_variants(self):
        by_roof = {}
        for seed, result in self.results.items():
            end = result.to_blueprint()['roof_checks'][-1]['z_range'][1]
            by_roof.setdefault(end, seed)
        self.assertEqual(set(by_roof), {19, 22, 24})
        occupied = {end: {p for p, state in self.blocks[seed].items() if state != AIR}
                    for end, seed in by_roof.items()}
        for a, b in ((19, 22), (19, 24), (22, 24)):
            # Compare occupied coordinates, so material swaps cannot satisfy this test.
            difference = occupied[a] ^ occupied[b]
            self.assertGreater(len(difference), 100)
            self.assertTrue(all(27 <= x <= 41 and 1 <= y <= 14 and 20 <= z <= 38
                                for x, y, z in difference))
        authored = assets.nbt.expand_structure(self.contract.blueprint())[2]
        self.assertNotEqual(self.blocks[0], authored)

    def test_wrong_medicine_chest_construction_is_explicitly_rejected(self):
        original = old_relay._story_interior

        def wrong_chest(masonry):
            original(masonry)
            masonry.put((31, 1, 18), 'minecraft:stone')

        with patch.object(old_relay, '_story_interior', side_effect=wrong_chest), \
                patch.object(old_relay, 'SiteBuilder', wraps=SiteBuilder) as builder:
            with self.assertRaisesRegex(ValueError, r'protected story cell.*\(31, 1, 18\)'):
                old_relay.generate(GenerationContext(self.contract, 17))
        builder.assert_not_called()

    def test_contract_exact_states_metadata_and_existing_validator_for_every_seed(self):
        baseline = self.contract.blueprint()
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                data, blocks = result.to_blueprint(), self.blocks[seed]
                for key in ('id', 'size', 'site', 'walk_targets', 'story_anchors', 'service_blocks', 'entities'):
                    self.assertEqual(data.get(key), baseline.get(key), key)
                for pos, state in self.contract.protected:
                    self.assertEqual(assets.canonical(blocks[pos]), state, pos)
                for name, target in self.contract.targets.items():
                    self.assertEqual(blocks[tuple(target['pos'])], target['state'], name)
                    assets.check_target_sightline(blocks, name, target['pos'], target['approach'])
                checks = validate_result(self.contract, result)
                self.assertEqual(len(checks['roof_sections']), 4)
                self.assertTrue(all(checks['walks']['targets'].values()))
                # The established validator remains in use, independently of semantic paths.
                self.assertEqual(validate(data, self.contract.max_size)[0], blocks)

    def test_every_target_and_npc_approach_reachable_even_with_npc_occupancy(self):
        baseline = self.contract.blueprint()
        for seed, graph in self.graphs.items():
            with self.subTest(seed=seed):
                seen = reachable(graph, baseline['walk_targets']['entry'])
                required = dict(baseline['walk_targets'])
                for key, spec in self.contract.roles.items():
                    required[key + ':feet'] = spec['pos']
                    required[key + ':approach'] = spec['approach']
                for key, spec in self.contract.targets.items():
                    required[key] = spec['approach']
                self.assertTrue(all(tuple(p) in seen for p in required.values()))
                check_walks(self.blocks[seed], required, self.contract.size)
                # Zhou He's body cannot be treated as a walk-through path to evidence.
                occupied = [tuple(s['pos']) for s in self.contract.roles.values()]
                seen = reachable(graph, baseline['walk_targets']['entry'], occupied)
                for spec in (*self.contract.roles.values(), *self.contract.targets.values()):
                    self.assertIn(tuple(spec['approach']), seen)

    def test_semantic_rooms_and_adjacency_are_real_walk_routes(self):
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                spaces = {s.id: s for s in result.semantics.spaces}
                self.assertEqual(len(spaces), 14)
                self.assertEqual(spaces['hideout'].minimum, (5, 0, 24))
                self.assertEqual(spaces['ledger_room'].minimum, (5, 6, 24))
                self.assertEqual(spaces['array_court'].minimum, (20, 0, 21))
                for space in spaces.values():
                    self.assertLessEqual(space.maximum[0] - space.minimum[0], 17)
                    for key in space.walk_targets:
                        self.assertTrue(space.contains(self.contract.blueprint()['walk_targets'][key]))
                connectivity = {name: set() for name in spaces}
                graph = self.graphs[seed]
                for edge in result.semantics.adjacencies:
                    connectivity[edge.source].add(edge.target)
                    connectivity[edge.target].add(edge.source)
                    self.assertTrue(spaces[edge.source].contains(edge.path[0]))
                    self.assertTrue(spaces[edge.target].contains(edge.path[-1]))
                    self.assertGreater(len(edge.path), 2)
                    for a, b in zip(edge.path, edge.path[1:]):
                        self.assertIn(b, graph[a])
                seen, todo = {'gate_court'}, deque(['gate_court'])
                while todo:
                    for name in connectivity[todo.popleft()] - seen:
                        seen.add(name)
                        todo.append(name)
                self.assertEqual(seen, set(spaces))

    def test_both_stairs_and_three_wide_routes_keep_full_clearance(self):
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                blocks, graph = self.blocks[seed], self.graphs[seed]
                edges = {(e.source, e.target): e.path for e in result.semantics.adjacencies}
                front = edges['front_stair', 'guard_room']
                rear = edges['west_gallery', 'rear_route']
                for path in (front, rear):
                    self.assertEqual({p[1] for p in path}, set(range(1, 8)))
                    self.assertEqual(sum(a[1] != b[1] for a, b in zip(path, path[1:])), 6)
                # Check every column of both stairs, not just their central route.
                for step in range(1, 7):
                    for x in range(9, 12):
                        self.assertIn((x, step + 1, 7 + step), graph)
                        self.assertIn('facing=south', blocks[x, step, 7 + step])
                    for x in range(16, 19):
                        self.assertIn((x, step + 1, 39 - step), graph)
                        self.assertIn('facing=north', blocks[x, step, 39 - step])
                # The high route survives closing all ground-level nodes outside the
                # stairs: real upper-room/upper-gallery connections cannot use a detour below.
                upper = {p: [q for q in neighbours if q[1] == 7]
                         for p, neighbours in graph.items() if p[1] == 7}
                seen = reachable(upper, (10, 7, 15))
                for pos in ((11, 7, 19), (8, 7, 28), (17, 7, 23), (17, 7, 32)):
                    self.assertIn(pos, seen)
                for x in range(20, 25):
                    for z in range(6, 11):
                        for y in range(1, 4):
                            self.assertEqual(blocks[x, y, z], AIR)
                for x in range(42, 45):
                    for z in range(2, 42):
                        self.assertIn((x, 1, z), graph)
                for x in range(30, 40):
                    for z in range(25, 28):
                        self.assertIn((x, 1, z), graph)

    def test_intact_before_damage_and_coherent_bay_collapse_afterwards(self):
        masonry = old_relay._Masonry(self.contract.blueprint()['palette'])
        old_relay._complete_relay(masonry)
        for z in range(13, 34):
            for x in range(27, 42):
                y = 6 + min(x - 27, 41 - x)
                self.assertNotEqual(masonry.blocks.get((x, y, z), AIR), AIR)
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                blocks = self.blocks[seed]
                end = result.to_blueprint()['roof_checks'][-1]['z_range'][1]
                # Retained tiles form continuous complete cross sections, followed by
                # exposed frames. There are no stochastic pinholes in the retained bay.
                for z in range(13, end + 1):
                    for x in range(27, 42):
                        y = 6 + min(x - 27, 41 - x)
                        self.assertNotEqual(blocks[x, y, z], AIR)
                for z in range(end + 1, 32):
                    for x in range(27, 42):
                        self.assertNotIn('deepslate_tile', blocks[x, 6 + min(x - 27, 41 - x), z])
                # Sand must rest on another actual block, not float above the breach.
                for (x, y, z), state in blocks.items():
                    if state == 'minecraft:sand' and y > 0:
                        self.assertNotEqual(blocks[x, y - 1, z], AIR)
                # Sheltered beds and actual room separation survive every collapse.
                self.assertIn('part=foot', blocks[7, 1, 28])
                self.assertIn('part=head', blocks[7, 1, 29])
                self.assertIn('spruce_door', blocks[10, 1, 24])
                self.assertIn('spruce_door', blocks[11, 7, 24])
                self.assertEqual(blocks[13, 2, 26], 'minecraft:dark_oak_planks')

    def test_west_lost_roof_bay_keeps_floor_and_supported_boundary_frame(self):
        intact = old_relay._Masonry(self.contract.blueprint()['palette'])
        old_relay._complete_relay(intact)
        for seed, result in self.results.items():
            with self.subTest(seed=seed):
                blocks = self.blocks[seed]
                roof = result.to_blueprint()['roof_checks'][0]
                self.assertEqual(roof['z_range'], [12, 24])
                # The entire rear bay loses its cover, including the gallery cover.
                for x in range(4, 21):
                    for z in range(25, 34):
                        for y in range(11, 19):
                            self.assertNotIn('deepslate_tile', blocks[x, y, z])
                # All original upper-floor cells survive, including the open bay.
                for x in range(5, 20):
                    for z in range(14, 33):
                        self.assertEqual(blocks[x, 6, z], intact.blocks.get((x, 6, z), AIR))
                self.assertEqual(blocks[5, 10, 28], AIR)  # Stepped outer wall, no box silhouette.
                self.assertEqual(blocks[8, 9, 31], intact.blocks[8, 9, 31])  # Sign backing.
                for x in (5, 15):
                    for y in range(7, 12):
                        self.assertIn('stripped_dark_oak_log', blocks[x, y, 25])
                self.assertIn('stripped_dark_oak_log', blocks[10, 17, 25])
                # The remaining guest-room roof is still the original continuous shell.
                for x in range(4, 17):
                    for z in range(12, 25):
                        y = 11 + min(x - 4, 16 - x)
                        self.assertEqual(blocks[x, y, z], intact.blocks[x, y, z])

    def test_broken_crowns_drift_banks_and_all_timber_have_real_support(self):
        offsets = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
        for seed, blocks in self.blocks.items():
            with self.subTest(seed=seed):
                # The standing wall is buried on its east flank, before the rear dune.
                self.assertEqual(blocks[40, 3, 23], 'minecraft:sand')
                # The leaning scheme keeps its supported crown at z=26, but no
                # longitudinal ridge may bridge into/out of that isolated frame.
                for z in (25, 27):
                    self.assertEqual(blocks[34, 13, z], AIR)
                # Do not accept disconnected sticks masquerading as a ruined roof.
                timber = {p for p, state in blocks.items() if 'stripped_dark_oak_log' in state}
                while timber:
                    start = timber.pop()
                    component, todo = {start}, deque([start])
                    while todo:
                        p = todo.popleft()
                        for d in offsets:
                            q = tuple(a + b for a, b in zip(p, d))
                            if q in timber:
                                timber.remove(q)
                                component.add(q)
                                todo.append(q)
                    self.assertTrue(any(
                        (x, y - 1, z) not in component and blocks.get((x, y - 1, z), AIR) != AIR
                        for x, y, z in component), f'unsupported timber: {sorted(component)}')

    def test_mixed_scene_composition_preserves_road_and_story_deployment(self):
        representatives = {}
        for seed, result in self.results.items():
            representatives.setdefault(result.to_blueprint()['roof_checks'][-1]['z_range'][1], seed)
        for seed in representatives.values():
            with self.subTest(seed=seed):
                layout, blueprints, report = compose_scene(self.scene, {'old_relay': self.results[seed]})
                self.assertTrue(report['all_player_approaches_reachable_with_npcs'])
                self.assertEqual({t['id'] for t in layout['targets']}, assets.TARGETS)
                self.assertEqual(set(layout['positions']), assets.ROLES | assets.TARGETS)
                self.assertEqual(blueprints['old_relay']['walk_targets'], self.contract.blueprint()['walk_targets'])
                self.assertIn('deployment_runtime', report['not_verified'])


if __name__ == '__main__':
    unittest.main()
