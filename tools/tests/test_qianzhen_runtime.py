"""阡镇原生验收的路线覆盖、云端边界与输入前提。"""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools.qianzhen.game_evidence import QianzhenEvidence, door_stance, nbt
from tools.qianzhen.game_routes import nearest_path, straight_ends
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

    def test_straight_legs_keep_corner_and_stair_exit(self):
        path = [(0,1,0),(0,1,1),(0,2,2),(0,3,3),(1,3,3),(2,3,3)]
        self.assertEqual(straight_ends(path), [(0,3,3),(2,3,3)])
        with self.assertRaisesRegex(ValueError, 'non-cardinal'):
            straight_ends([(0,1,0),(1,1,1)])

    def test_unreachable_target_does_not_become_a_teleport(self):
        with self.assertRaisesRegex(ValueError, 'No route'):
            nearest_path({(0,1,0):[]}, (0,1,0), {(2,1,0)})

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


if __name__ == '__main__':
    unittest.main()
