"""阡镇原生验收的路线覆盖、云端边界与输入前提。"""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools.qianzhen.game_evidence import QianzhenEvidence, door_stance, nbt
from tools.qianzhen.game_routes import nearest_path, straight_ends, walk_point, opened_door_graph, traversal_ends
from tools.modal_runner.remote import commands_for


class QianzhenRuntimeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.e = QianzhenEvidence(Path(cls.tmp.name))

    @classmethod
    def tearDownClass(cls):
        cls.e.position_stream.close()
        cls.tmp.cleanup()

    def test_tours_cover_all_targets_through_adjacent_walkable_cells(self):
        for d in self.e.buildings:
            with self.subTest(site=d['id']):
                graph = self.e.graphs[d['id']]
                covered = {'entry'}
                previous = tuple(d['walk_targets']['entry'])
                for part in self.e.routes[d['id']]:
                    path = part['path']
                    self.assertEqual(path[0], previous)
                    for a, b in zip(path, path[1:]):
                        self.assertIn(b, graph[a])
                    covered.update(part['targets'])
                    previous = path[-1]
                self.assertEqual(covered-{'return_entry'}, set(d['walk_targets']))
                self.assertEqual(previous, tuple(d['walk_targets']['entry']))

    def test_every_closed_door_has_an_actual_clear_interaction_stance(self):
        for d in self.e.buildings:
            blocks, graph = self.e.blocks[d['id']], self.e.graphs[d['id']]
            for pos, state in blocks.items():
                name, props = nbt.parse_block_state(state)
                if name.endswith('_door') and props.get('half') == 'lower':
                    with self.subTest(site=d['id'], pos=pos):
                        p = door_stance(blocks, graph, pos, props)
                        self.assertIn((int(p[0]),p[1],int(p[2])), graph)

    def test_entry_apron_connects_to_template_boundary_without_a_drop(self):
        for d in self.e.buildings:
            x,y,z = d['walk_targets']['entry']
            blocks = self.e.blocks[d['id']]
            for front in range(z+1):
                for across in range(x-1,x+2):
                    with self.subTest(site=d['id'],x=across,z=front):
                        self.assertNotIn(blocks.get((across,y-1,front),'minecraft:air'),
                                         ('minecraft:air','minecraft:water[level=0]'))
                        for head in range(y,y+3):
                            self.assertEqual(blocks[across,head,front],'minecraft:air')

    def test_straight_legs_keep_corner_and_stair_exit(self):
        path = [(0,1,0),(0,1,1),(0,2,2),(0,3,3),(1,3,3),(2,3,3)]
        self.assertEqual(straight_ends(path), [(0,3,3),(2,3,3)])
        with self.assertRaisesRegex(ValueError, 'non-cardinal'):
            straight_ends([(0,1,0),(1,1,1)])

    def test_unreachable_target_does_not_become_a_teleport(self):
        with self.assertRaisesRegex(ValueError, 'No route'):
            nearest_path({(0,1,0):[]}, (0,1,0), {(2,1,0)})

    def test_native_open_door_regression_uses_clear_opening_center(self):
        blocks = {(21,7,33):'minecraft:spruce_door[facing=south,half=lower,hinge=left,open=false]'}
        path = [(21,7,z) for z in range(31,36)]
        point = walk_point((21,7,33), blocks)
        self.assertEqual(point, [21.40625,7,33.5])
        # Observed first-run stall: x=21.530244, body half-width .3 hit x=21.8125.
        self.assertGreater(21.530244+.3, 21.8125)
        self.assertLess(point[0]+.085+.3, 21.8125)
        self.assertGreater(point[0]-.085-.3, 21)
        ends = traversal_ends(path, blocks)
        self.assertIn((21,7,32), ends)
        self.assertIn((21,7,33), ends)
        self.assertIn((21,7,34), ends)

    def test_route_cannot_cross_open_panel_from_its_occupied_side(self):
        a,b,c = (0,1,0),(1,1,0),(0,1,1)
        blocks = {a:'minecraft:spruce_door[facing=south,half=lower,hinge=left,open=false]'}
        graph = opened_door_graph({a:[b,c], b:[a], c:[a]}, blocks)
        self.assertNotIn(b, graph[a])
        self.assertNotIn(a, graph[b])
        self.assertIn(c, graph[a])

    def test_local_minecraft_start_is_refused(self):
        with patch('tools.qianzhen.game_evidence.ROOT', Path('/not-modal')):
            with self.assertRaisesRegex(RuntimeError, 'Modal disposable'):
                self.e.start()

    def test_cloud_preset_stays_scoped_to_these_blueprints(self):
        commands = commands_for('qianzhen-buildings')
        self.assertEqual(len(commands), 4)
        self.assertIn('generateAllStructures', commands[2])
        self.assertEqual(commands[-1], ['python3','tools/qianzhen/game_evidence.py'])
        self.assertNotIn('release_gate.py', str(commands))

    def test_walk_subset_is_explicit_and_rejects_unknown_or_duplicate_sites(self):
        command = commands_for('qianzhen-buildings','old_relay,canal_gardens')[-1]
        self.assertEqual(command[-2:],['--walk-locations','canal_gardens,old_relay'])
        for bad in ('no_such_site','old_relay,old_relay',''):
            with self.assertRaises(ValueError):
                commands_for('qianzhen-buildings',bad)

    def test_facility_retry_does_not_claim_walk_or_export_completion(self):
        with tempfile.TemporaryDirectory() as folder:
            e = QianzhenEvidence(Path(folder),facilities_only=True)
            try:
                self.assertEqual(e.report['selected'],['place_six','mapped_existing_facilities'])
                self.assertEqual(e.report['walk_locations'],[])
                self.assertEqual(e.report['walk_scope'],'not_selected')
            finally:
                e.position_stream.close()
        self.assertEqual(commands_for('qianzhen-facilities')[-1],
                         ['python3','tools/qianzhen/game_evidence.py','--facilities-only'])
        with self.assertRaises(ValueError):
            commands_for('qianzhen-facilities','all')


if __name__ == '__main__':
    unittest.main()
