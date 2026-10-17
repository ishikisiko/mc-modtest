"""Cloud-only district capture contracts; no Gradle or Minecraft launch."""
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

from tools.combat_capture.session import SessionConfig, server_properties
from tools.modal_runner.remote import commands_for
from tools.yunshan.district_game_evidence import (
    DistrictEvidence, chunk_rectangle, package_world, traversal_passed, world_position,
)
from tools.yunshan.game_export import plain, read_typed, write_typed
from tools.realm_stats_evidence import Evidence
import tools.json_to_nbt as nbt


class DistrictCoordinatesTests(unittest.TestCase):
    def test_continuous_and_block_centers_coincide_for_every_rotation(self):
        size = [19, 24, 23]
        for quarter in range(4):
            building = {'rotation_quarters': quarter, 'world_min': [2850, 62, 110]}
            for pos in ([0, 0, 0], [18, 7, 22], [7, 1, 8]):
                block = world_position(building, size, pos, block=True)
                center = world_position(building, size, [pos[0]+.5, pos[1], pos[2]+.5])
                self.assertEqual(center, [block[0]+.5, block[1], block[2]+.5])

    def test_rotated_stair_requires_forward_progress_height_and_lane(self):
        size = [19, 24, 23]
        for quarter in range(4):
            building = {'rotation_quarters': quarter, 'world_min': [2860, 62, 120]}
            start = world_position(building, size, [7.5, 1, 8.5])
            end = world_position(building, size, [7.5, 7, 15.5])
            self.assertTrue(traversal_passed(start, end, end, 69))
            self.assertFalse(traversal_passed(start, end, [end[0], 63, end[2]], 69))
            self.assertFalse(traversal_passed(start, end, start, 69))
            self.assertFalse(traversal_passed(start, end, [end[0]+3, end[1], end[2]+3], 69))

    def test_chunk_bounds_include_cross_zero_and_reject_oversized_tile(self):
        self.assertEqual(chunk_rectangle([-1, 62, -1], [32, 32, 32]), [-1, -1, 30, 30])
        self.assertEqual(chunk_rectangle([2850, 62, 110], [32, 32, 32]), [2850, 110, 2881, 141])
        with self.assertRaisesRegex(ValueError, 'maximum is 256'):
            chunk_rectangle([0, 62, 0], [257, 10, 257])

    def test_district_capture_distance_is_bounded_and_existing_default_is_unchanged(self):
        self.assertEqual(SessionConfig('fixture').view_distance, 6)
        self.assertEqual(server_properties(1, 2, 'fixture')['view-distance'], '6')
        self.assertEqual(server_properties(1, 2, 'fixture', view_distance=16)['view-distance'], '16')
        for distance in (True, 0, 33, 16.5):
            with self.assertRaises(ValueError):
                server_properties(1, 2, 'fixture', view_distance=distance)


class WorldDeliveryTests(unittest.TestCase):
    def test_air_tile_overwrites_marker_and_chunk_hold_is_released_on_failure(self):
        for fail in (False, True):
            with self.subTest(fail=fail), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary); (root/'tile.nbt').write_bytes(b'fixture')
                evidence = object.__new__(DistrictEvidence)
                Evidence.__init__(evidence, root/'evidence')
                evidence.report['completed'] = []
                evidence.manifest = root/'manifest.json'
                evidence.s = SimpleNamespace(username='CloudCapture')
                evidence.spec = {'world_min': [2850, 62, 110], 'world_max': [2881, 93, 141],
                    'tiles': [{'id': 'myvillage:yunshan_d1_v2/fixture', 'nbt': 'tile.nbt',
                               'world_min': [2850, 62, 110], 'size': [32, 32, 32],
                               'probe': {'pos': [2850, 62, 110], 'state': 'minecraft:air'}}]}
                commands = []
                def rc(command):
                    commands.append(command)
                    if fail and command.startswith('place template'):
                        raise RuntimeError('injected placement failure')
                    return 'yunshan_d1_v2 passed'
                evidence.rc = rc
                with patch('tools.yunshan.district_game_evidence.cs.SERVER_DIR', root/'server'):
                    if fail:
                        with self.assertRaisesRegex(RuntimeError, 'injected placement'):
                            evidence.setup()
                    else:
                        evidence.setup()
                        marker = commands.index('setblock 2850 62 110 minecraft:barrier')
                        placed = next(i for i, command in enumerate(commands) if command.startswith('place template'))
                        probe = commands.index('execute if block 2850 62 110 minecraft:air')
                        self.assertLess(marker, placed); self.assertLess(placed, probe)
                        self.assertIn('place_complete_district', evidence.report['completed'])
                    self.assertEqual(commands[-1], 'forceload remove 2850 110 2881 141')

    def test_world_keeps_dimension_player_mod_and_attachment_data(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); world = root/'world'; world.mkdir()
            level = nbt.Compound({'Data': nbt.Compound({
                'LevelName': nbt.String('fixture'), 'SpawnX': nbt.Int(2900),
                'neoforge:attachments': nbt.Compound({'fixture': nbt.String('keep unchanged')}),
            })})
            write_typed(world/'level.dat', '', level)
            expected = {
                'dimensions/myvillage/fixture/region/r.0.0.mca': b'dimension',
                'playerdata/fixture.dat': b'player', 'stats/fixture.json': b'stats',
                'advancements/fixture.json': b'advancements', 'data/myvillage_world.dat': b'mod ledger',
                'region/r.178.6.mca': b'city', 'datapacks/yunshan_d1_v2/pack.mcmeta': b'pack',
            }
            for relative, data in expected.items():
                path = world/relative; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
            (world/'session.lock').write_text('do not ship')
            result = package_world(world, root/'city.zip')
            self.assertEqual(result['files'], len(expected)+1)
            with zipfile.ZipFile(root/'city.zip') as archive:
                self.assertNotIn('Yunshan D1 v2/session.lock', archive.namelist())
                for relative, data in expected.items():
                    self.assertEqual(archive.read('Yunshan D1 v2/'+relative), data)
            _, actual = read_typed(world/'level.dat')
            self.assertEqual(plain(actual)['Data']['neoforge:attachments'], plain(level)['Data']['neoforge:attachments'])
            self.assertEqual(actual.value['Data'].value['LevelName'].value, '云山 D1 v2 · 完整街区')

    def test_missing_level_dat_cannot_be_reported_as_playable_world(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with self.assertRaisesRegex(FileNotFoundError, 'no level.dat'):
                package_world(root, root/'city.zip')

    def test_capture_refuses_owner_or_local_workspace_before_any_start(self):
        evidence = object.__new__(DistrictEvidence)
        with patch('tools.yunshan.district_game_evidence.ROOT', Path('/owner-pc')), patch('tools.yunshan.district_game_evidence.cs.start') as start:
            with self.assertRaisesRegex(RuntimeError, 'Modal disposable'):
                evidence.start()
            start.assert_not_called()

    def test_cloud_preset_keeps_scoped_generation_build_and_actual_capture(self):
        commands = commands_for('yunshan-district')
        joined = '\n'.join(' '.join(command) for command in commands)
        self.assertIn('tools/yunshan/build_district.py', joined)
        self.assertEqual(commands[-1], ['python3', 'tools/yunshan/district_game_evidence.py'])
        gradle = next(command for command in commands if command[0] == './gradlew')
        self.assertIn('jar', gradle); self.assertIn('-x', gradle); self.assertIn('generateAllStructures', gradle)
        for forbidden in ('release_gate', 'run_chunky_acceptance', 'runGameTestServer'):
            self.assertNotIn(forbidden, joined)


if __name__ == '__main__':
    unittest.main()
