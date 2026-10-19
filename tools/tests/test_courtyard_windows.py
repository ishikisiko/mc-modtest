"""Window-to-wall and window-to-screen alignment, without generated assets."""
import json
from pathlib import Path
import unittest

from tools.reference_courtyard.assembly_scene import Site
from tools.reference_courtyard.window_layout import centered_roots, place_windows


CONFIG = Path(__file__).resolve().parents[1] / 'reference_courtyard' / 'assemblies.json'


class CourtyardWindowsTest(unittest.TestCase):
    def setUp(self):
        self.site = Site(json.loads(CONFIG.read_text()))
        # The actual wall fields, screens and hall posts are the fixture;
        # tests compare window geometry to these features rather than roots.
        for bounds in [
            [3, 3, 25, 24, 8, 26], [3, 3, 25, 4, 8, 33],
            [11, 3, 38, 19, 8, 39], [24, 5, 37, 45, 11, 38],
            [44, 5, 26, 45, 11, 38],
        ]:
            self.site.fill(bounds, 'wall', 'plaster')
        self.wall_cells = set(self.site.blocks)
        for x in [6, 12, 18]:
            self.site.place((x, 3, 26), 'gate', 'timber', 'east')
        self.site.place((24, 5, 27), 'gate', 'timber', 'east')
        for x in [26, 32, 36, 42]:
            for z in [26, 35]:
                self.site.place((x, 5, z), 'column', 'round')
        self.structural_cells = set(self.site.owners)
        place_windows(self.site)
        self.windows = [a for a in self.site.assemblies if a['family'] == 'courtyard_window']

    def at_wall(self, axis, value):
        return sorted([a for a in self.windows if all(p[axis] == value for p in a['parts'])],
                      key=lambda a: a['root'])

    @staticmethod
    def span(unit, axis):
        values = [p[axis] for p in unit['parts']]
        return min(values), max(values) + 1

    def assert_pair_reflects_in_wall(self, units, axis, wall_start, wall_stop):
        self.assertEqual(2, len(units))
        left, right = [self.span(a, axis) for a in units]
        self.assertEqual(left[0] - wall_start, wall_stop - right[1])
        self.assertEqual(wall_start + wall_stop, left[0] + right[1])
        self.assertEqual(wall_start + wall_stop, left[1] + right[0])
        self.assertLessEqual(left[1], right[0])

    def test_front_windows_center_in_clear_bays_between_screen_axes(self):
        screens = sorted(a['root'][0] for a in self.site.assemblies
                         if a['family'] == 'courtyard_gate' and a['root'][1] == 3)
        piers = screens + [24]  # Main hall boundary wall closes the third bay.
        windows = self.at_wall(2, 25)
        self.assertEqual(len(piers) - 1, len(windows))
        for window, left, right in zip(windows, piers, piers[1:]):
            first, stop = self.span(window, 0)
            self.assertEqual(first - (left + 1), right - stop)
            self.assertEqual((left + right + 1) / 2, (first + stop) / 2)

    def test_terminal_window_matches_moon_gate_passage_axis(self):
        window, = self.at_wall(0, 3)
        window_span = self.span(window, 2)
        window_axis = sum(window_span) / 2
        screens = [a for a in self.site.assemblies
                   if a['family'] == 'courtyard_gate' and a['root'][1] == 3]
        self.assertTrue(screens)
        for screen in screens:
            self.assertEqual(window_axis, sum(self.span(screen, 2)) / 2)

    def test_side_room_pair_has_equal_outer_reveals_and_mirrored_spacing(self):
        self.assert_pair_reflects_in_wall(self.at_wall(2, 38), 0, 11, 19)

    def test_hall_rear_pair_has_equal_outer_reveals_and_mirrored_spacing(self):
        self.assert_pair_reflects_in_wall(self.at_wall(2, 37), 0, 24, 45)

    def test_hall_rear_windows_center_in_the_two_outer_column_bays(self):
        posts = sorted(a['root'][0] for a in self.site.assemblies
                       if a['family'] == 'courtyard_column' and a['root'][2] == 35)
        windows = self.at_wall(2, 37)
        self.assertEqual(4, len(posts))
        for window, (left, right) in zip(windows, (posts[:2], posts[2:])):
            first, stop = self.span(window, 0)
            self.assertEqual(first - (left + 1), right - stop)
            self.assertEqual((left + right + 1) / 2, (first + stop) / 2)

    def test_hall_side_window_uses_minimum_grid_error(self):
        window, = self.at_wall(0, 44)
        start, stop = self.span(window, 2)
        wall_start, wall_stop = 26, 38
        width = stop - start
        wall_axis = (wall_start + wall_stop) / 2
        possible_errors = [abs(root + width / 2 - wall_axis)
                           for root in range(wall_start, wall_stop - width + 1)]
        self.assertEqual(min(possible_errors), abs((start + stop) / 2 - wall_axis))
        self.assertEqual(.5, min(possible_errors))

    def test_all_nine_windows_are_whole_inside_walls_and_clear_of_posts_and_gates(self):
        self.assertEqual(9, len(self.windows))
        occupied = set()
        for window in self.windows:
            cells = {tuple(p) for p in window['parts']}
            self.assertEqual(9, len(cells))
            self.assertTrue(cells <= self.wall_cells)
            self.assertFalse(cells & self.structural_cells)
            self.assertFalse(cells & occupied)
            occupied |= cells
            for p in cells:
                owner = self.site.assemblies[self.site.owners[p]]
                self.assertIs(owner, window)

    def test_window_heights_center_with_smallest_whole_item_error(self):
        for window in self.windows:
            y0, y1 = self.span(window, 1)
            wall_low, wall_high = (3, 8) if window['root'][1] == 4 else (5, 11)
            error = abs((y0 + y1) / 2 - (wall_low + wall_high) / 2)
            height = y1 - y0
            possible_errors = [abs(root + height / 2 - (wall_low + wall_high) / 2)
                               for root in range(wall_low, wall_high - height + 1)]
            self.assertEqual(min(possible_errors), error)
            self.assertEqual(0 if wall_high - wall_low == 5 else .5, error)

    def test_centering_rejects_overflow_and_keeps_minimum_parity_error(self):
        for span in range(3, 20):
            root, = centered_roots(7, 7 + span, 3)
            margins = root - 7, 7 + span - (root + 3)
            self.assertGreaterEqual(min(margins), 0)
            self.assertLessEqual(abs(margins[0] - margins[1]), 1)
        with self.assertRaises(ValueError):
            centered_roots(0, 5, 3, count=2)


if __name__ == '__main__':
    unittest.main()
