"""Production geometry checks; no Minecraft, Gradle, browser or cloud runtime."""
import copy
import json
from pathlib import Path
import shutil
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
        self.assertEqual(targets['sample_examination']['pos'], [98, 1, 77])
        self.assertEqual(targets['sample_examination']['approach'], [98, 1, 78])
        for target in targets.values():
            site = next(s for s in self.layout['sites'] if all(s['origin'][i] <= target['pos'][i] < s['origin'][i]+s['size'][i] for i in range(3)))
            _, root = read_gzipped_nbt(str(self.out/'data/myvillage/structure/qianzhen'/(site['id']+'.nbt')))
            local = [target['pos'][i]-site['origin'][i] for i in range(3)]
            cell = next(b for b in root['blocks'] if b['pos'] == local)
            self.assertEqual(assets.canonical(state_string(root['palette'][cell['state']])), assets.canonical(target['state']))
        self.assertEqual(self.layout['positions']['zhou_he']['pos'], [201, 1, 51])
        self.assertEqual(self.layout['positions']['warden']['pos'], [230, 3, -24])

    def test_all_nine_target_rays_have_only_air_before_destination(self):
        blocks = {tuple(row['pos']):row['state'] for row in self.layout['roads']}
        for site in self.layout['sites']:
            _, root = read_gzipped_nbt(str(self.out/'data/myvillage/structure/qianzhen'/(site['id']+'.nbt')))
            blocks.update({assets.add(site['origin'], row['pos']):state_string(root['palette'][row['state']])
                           for row in root['blocks']})
        self.assertEqual(set(self.report['target_sightlines']), assets.TARGETS)
        for target in self.layout['targets']:
            with self.subTest(target=target['id']):
                ray = assets.check_target_sightline(blocks, target['id'], target['pos'], target['approach'])
                self.assertEqual(ray, self.report['target_sightlines'][target['id']])
                self.assertTrue(ray['intermediate_cells'])
                for cell in ray['intermediate_cells']:
                    self.assertEqual(assets.state_at(blocks, cell)[0], 'minecraft:air')
                self.assertLessEqual(sum((a-b)**2 for a, b in zip(ray['eye'], ray['click_point'])), 4.5**2)
        source = json.loads((assets.SOURCE/'apothecary.json').read_text())
        self.assertEqual(source['walk_targets']['sample_exam'], [34, 1, 12])
        self.assertIn('native_target_outline_hit', self.report['not_verified'])

    def test_original_sample_approach_is_rejected_by_real_obstruction(self):
        with tempfile.TemporaryDirectory(prefix='qianzhen-story-old-approach-') as folder:
            source = Path(folder)/'source'
            shutil.copytree(assets.SOURCE, source)
            scene_path = source/'story_scene.json'
            scene = json.loads(scene_path.read_text())
            del scene['targets']['sample_examination']['approach']
            scene_path.write_text(json.dumps(scene))
            with self.assertRaisesRegex(ValueError, r'target sightline obstructed: sample_examination at \(97, 2, 77\) by minecraft:brewing_stand'):
                assets.assemble(source)
            # The full stone bench also intersects this ray, independently of the stand's outline.
            data = json.loads((source/'apothecary.json').read_text())
            _, _, blocks, _ = assets.nbt.expand_structure(data)
            blocks[35, 2, 12] = 'minecraft:air'
            with self.assertRaisesRegex(ValueError, r'\(35, 1, 12\) by minecraft:smooth_stone'):
                assets.check_target_sightline(blocks, 'sample_examination', [36, 1, 12], [34, 1, 12])

    def test_ray_traversal_keeps_short_crossings_but_excludes_corner_touches(self):
        self.assertIn((0, 1, 0), assets.sightline_cells((.5, .500001, .5), (2.5, 2.500001, .5)))
        self.assertEqual(assets.sightline_cells((.5, .5, .5), (2.5, 2.5, .5)),
                         [(0, 0, 0), (1, 1, 0), (2, 2, 0)])
        self.assertEqual(assets.sightline_cells((-.5, .5, .5), (-2.5, .5, .5)),
                         [(-1, 0, 0), (-2, 0, 0), (-3, 0, 0)])

    def test_partial_destination_does_not_exempt_intermediate_partial_blocks(self):
        for target in ('cauldron', 'water_cauldron[level=3]', 'chest', 'lectern'):
            with self.subTest(target=target):
                blocks = {(2, 1, 0):'minecraft:'+target}
                assets.check_target_sightline(blocks, 'fixture', [2, 1, 0], [0, 1, 0])
                for blocker in ('brewing_stand', 'oak_door[open=true]', 'glass', 'stone'):
                    blocks[1, 2, 0] = 'minecraft:'+blocker
                    with self.assertRaisesRegex(ValueError, 'sightline obstructed'):
                        assets.check_target_sightline(blocks, 'fixture', [2, 1, 0], [0, 1, 0])

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
