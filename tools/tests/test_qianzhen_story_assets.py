"""Production geometry checks; no Minecraft, Gradle, browser or cloud runtime."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from qianzhen import story_assets as assets
from buildgen.nbtread import read_gzipped_nbt, state_string


class QianzhenStoryAssetsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.folder = tempfile.TemporaryDirectory(prefix='qianzhen-story-test-')
        cls.out = Path(cls.folder.name)/'resources'
        cls.report = assets.generate(cls.out)
        cls.layout = json.loads((cls.out/'data/myvillage/story/qianzhen_scene.json').read_text())

    @classmethod
    def tearDownClass(cls):
        cls.folder.cleanup()

    def test_production_is_six_templates_and_scene_only(self):
        files = {p.relative_to(self.out).as_posix() for p in self.out.rglob('*') if p.is_file()}
        expected = {'data/myvillage/structure/qianzhen/'+s['id']+'.nbt' for s in self.layout['sites']}
        expected.add('data/myvillage/story/qianzhen_scene.json')
        self.assertEqual(files, expected)
        for site in self.layout['sites']:
            self.assertEqual(site['sha256'], assets.sha(self.out/'data/myvillage/structure/qianzhen'/(site['id']+'.nbt')))

    def test_all_routes_and_actor_approaches_connected(self):
        self.assertGreater(self.report['connected_stations'], 140)
        self.assertTrue(self.report['all_player_approaches_reachable_with_npcs'])
        self.assertEqual(self.report['road_building_overlap'], 0)
        roads = {tuple(c['pos']):c['state'] for c in self.layout['roads']}
        for x in range(143, 146):
            self.assertIn('stairs[facing=south', roads[x, 1, 64])
            self.assertEqual(roads[x, 0, 64], 'minecraft:smooth_sandstone')
        for site in self.layout['sites']:
            ox, oy, oz = site['origin']; w, h, d = site['size']
            self.assertFalse(any(ox <= x < ox+w and oz <= z < oz+d for x, y, z in roads))

    def test_click_targets_are_precise_and_shared_core_is_unchanged(self):
        targets = {t['id']:t for t in self.layout['targets']}
        self.assertEqual(set(targets), assets.TARGETS)
        self.assertEqual(targets['water_sample']['pos'], [161, 2, 76])
        self.assertEqual(targets['water_sample']['approach'], [160, 2, 76])
        self.assertEqual(targets['water_sample']['state'], 'minecraft:water_cauldron[level=3]')
        self.assertEqual(targets['spring_core']['pos'], [215, 3, -27])
        self.assertEqual(targets['spring_core']['block'], 'minecraft:chiseled_stone_bricks')
        for target in targets.values():
            site = next(s for s in self.layout['sites'] if all(s['origin'][i] <= target['pos'][i] < s['origin'][i]+s['size'][i] for i in range(3)))
            _, root = read_gzipped_nbt(str(self.out/'data/myvillage/structure/qianzhen'/(site['id']+'.nbt')))
            local = [target['pos'][i]-site['origin'][i] for i in range(3)]
            cell = next(b for b in root['blocks'] if b['pos'] == local)
            self.assertEqual(assets.canonical(state_string(root['palette'][cell['state']])), assets.canonical(target['state']))
        self.assertEqual(self.layout['positions']['zhou_he']['pos'], [201, 1, 51])
        self.assertEqual(self.layout['positions']['warden']['pos'], [230, 3, -24])

    def test_road_cannot_invent_a_diagonal_or_unsupported_stair(self):
        roads = json.loads((assets.SOURCE/'story_scene.json').read_text())['roads']
        bad = copy.deepcopy(roads); bad['paths'][0]['points'][1][0] += 1
        with self.assertRaisesRegex(ValueError, 'axis-aligned'):
            assets.road_blocks(bad, self.layout['sites'])
        bad = copy.deepcopy(roads); bad['steps'][0]['pos'] = [300, 1, 64]
        with self.assertRaisesRegex(ValueError, 'supported'):
            assets.road_blocks(bad, self.layout['sites'])

    def test_check_is_read_only_and_rejects_extra_or_changed_assets(self):
        command = [sys.executable, str(ROOT/'tools/qianzhen/story_assets.py'), '--out', str(self.out), '--check']
        before = {p:p.read_bytes() for p in self.out.rglob('*') if p.is_file()}
        result = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual({p:p.read_bytes() for p in before}, before)
        scene = self.out/'data/myvillage/story/qianzhen_scene.json'
        extra = self.out/'data/myvillage/structure/qianzhen/unexpected.nbt'
        scene.write_text('{}\n'); extra.write_bytes(b'foreign')
        try:
            result = subprocess.run(command, capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('qianzhen_scene.json', result.stderr)
            self.assertIn('unexpected.nbt', result.stderr)
            self.assertEqual(scene.read_text(), '{}\n')
            self.assertEqual(extra.read_bytes(), b'foreign')
        finally:
            scene.write_bytes(before[scene]); extra.unlink()


if __name__ == '__main__':
    unittest.main()
