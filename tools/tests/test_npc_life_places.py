"""Life metadata must agree with actual rotated buildings and reject unusable fixtures."""
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import gen_npc_life_places as places
from worldplan.generate_assets import city_blocks


class NpcLifePlaceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.authored = places.read(places.AUTHORED)
        cls.compiled = places.generate(cls.authored)
        cls.layouts = {layout['id']: layout for layout in cls.compiled['layouts']}

    def test_planned_points_equal_native_city_placement(self):
        layout = places.read(ROOT / 'blueprints/worldplan/yunshan_placeholder_v1.json')
        blocks, _, native = city_blocks(layout)
        for point in self.layouts['yunshan_planned_v1']['places']:
            target = Path(point['source']).stem + '/' + point['target']
            with self.subTest(point=point['id']):
                self.assertEqual(point['pos'], native['walk_targets'][target])
                x, y, z = point['pos']
                self.assertEqual('minecraft:air', blocks[x, y, z])
                self.assertEqual('minecraft:air', blocks[x, y + 1, z])
                if point['block']:
                    self.assertEqual(point['block'], blocks[tuple(point['block_pos'])].split('[')[0])

    def test_rotation_keeps_the_bed_and_service_next_to_the_right_room(self):
        points = {p['id']: p for p in self.layouts['yunshan_planned_v1']['places']}
        self.assertEqual([34, 6, 82], points['guest_west']['pos'])
        self.assertEqual([35, 6, 86], points['guest_west']['block_pos'])
        self.assertEqual([48, 3, 54], points['herbalist_work']['pos'])
        self.assertEqual([49, 3, 54], points['herbalist_work']['block_pos'])
        self.assertEqual('myvillage:alchemy_furnace', points['herbalist_work']['block'])

    def test_showcase_has_four_distinct_real_guest_beds_and_relative_height(self):
        layout = self.layouts['yunshan_showcase_v2']
        self.assertEqual([0, -61, 0], layout['source_origin'])
        guests = [p for p in layout['places'] if p['id'].startswith('guest_')]
        self.assertEqual(4, len(guests))
        self.assertEqual(4, len({tuple(p['block_pos']) for p in guests}))
        self.assertTrue(all(p['block'] == 'minecraft:red_bed' for p in guests))
        self.assertTrue(all(p['pos'][1] == 7 for p in guests))

    def test_gathering_is_never_invented_from_a_building_yard(self):
        self.assertTrue(all(p['kind'] != 'gather' for layout in self.compiled['layouts'] for p in layout['places']))
        spec = copy.deepcopy(self.authored['layouts'][0])
        spec['buildings'][0]['places'][0]['kind'] = 'gather'
        with self.assertRaisesRegex(ValueError, 'explicit administrator'):
            places.compile_layout(spec)

    def test_unknown_target_and_duplicate_identity_are_rejected(self):
        spec = copy.deepcopy(self.authored['layouts'][0])
        spec['buildings'][0]['places'][0]['target'] = 'not_a_real_room'
        with self.assertRaisesRegex(ValueError, 'Unknown walk target'):
            places.compile_layout(spec)
        spec = copy.deepcopy(self.authored['layouts'][0])
        spec['buildings'][1]['places'][0]['id'] = spec['buildings'][0]['places'][0]['id']
        with self.assertRaisesRegex(ValueError, 'duplicate place'):
            places.compile_layout(spec)

    def test_fixture_removed_from_the_blueprint_is_rejected(self):
        original_read = places.read

        def changed(path):
            data = original_read(path)
            if path.name == 'apothecary_v1.json':
                data = copy.deepcopy(data)
                service = data['service_blocks'][0]
                data['ops'].append({'op': 'set', 'pos': service['pos'], 'state': 'minecraft:stone'})
            return data

        with patch.object(places, 'read', side_effect=changed):
            with self.assertRaisesRegex(ValueError, 'Missing/overwritten facility'):
                places.compile_layout(self.authored['layouts'][0])

    def test_blocked_station_is_not_published_as_a_place(self):
        original_read = places.read

        def changed(path):
            data = original_read(path)
            if path.name == 'south_gate_inn_v3.json':
                data = copy.deepcopy(data)
                data['ops'].append({'op': 'set', 'pos': data['walk_targets']['counter'], 'state': 'minecraft:stone'})
            return data

        with patch.object(places, 'read', side_effect=changed):
            with self.assertRaisesRegex(ValueError, 'Blocked body'):
                places.compile_layout(self.authored['layouts'][0])

    def test_check_detects_stale_output_without_overwriting_it(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'places.json'
            path.write_text(places.encoded(self.compiled), encoding='utf-8')
            places.check(path, self.compiled)
            path.write_text('{"stale":true}', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'missing or stale'):
                places.check(path, self.compiled)
            self.assertEqual('{"stale":true}', path.read_text(encoding='utf-8'))

    def test_checked_in_resource_is_current_and_sources_are_identified(self):
        places.check(places.OUTPUT, self.compiled)
        for layout in self.compiled['layouts']:
            for point in layout['places']:
                self.assertEqual(places.digest(ROOT / point['source']), layout['source_hashes'][point['source']])
        self.assertEqual(64, len(self.layouts['yunshan_planned_v1']['worldplan_layout_hash']))


if __name__ == '__main__':
    unittest.main()
