"""Pure evidence-parser, geometry and ownership checks; never launches a game."""
from collections import Counter
from copy import deepcopy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch
import zipfile

from tools.forging_array_evidence import (
    ROOT, CASES, ORIGIN, VECTORS, ForgingArrayEvidence, aim_candidates,
    collision_boxes, frozen_identity, holdings, infusion_costs, ingredient_item,
    inventory_point, look_at, menu_point, mesh_pos, multiset, ray_distance,
    saved_items, select_cases, world_pos,
)


def active():
    item = dict(id='myvillage:forged_pickaxe', count=1,
                components={'minecraft:damage': 37, 'myvillage:temper': {'experience': 17}})
    final = deepcopy(item)
    final['components']['myvillage:forge_mark'] = dict(trait='tough', quality=4)
    return dict(Version=1, Progress=17, Duration=200, Trait=2, Quality=4,
                Authorizer=[1, -2, 3, -4], Frozen=final, Snapshot=item,
                Reserved=[dict(id='myvillage:xuantie_ingot', count=1,
                               components={'minecraft:custom_data': {'identity': 'keep'}})],
                Items=[dict(Slot=0, **item)])


class ParserTests(unittest.TestCase):
    def test_case_selection_is_canonical_visual_last_and_rejects_partial_names(self):
        self.assertEqual(select_cases('all'), list(CASES))
        self.assertEqual(select_cases('visual,infusion'), ['infusion', 'visual'])
        for bad in ('', 'infusion,infusion', 'unknown', 'visual,', ' infusion'):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                select_cases(bad)

    def test_full_snbt_retains_components_uuid_and_exact_reservations(self):
        state = active()
        e = ForgingArrayEvidence.__new__(ForgingArrayEvidence)
        e.inspect = Mock(return_value={'controller': True, 'nbt': json.dumps(state)})
        self.assertEqual(e.saved(), state)
        e.inspect.return_value = {'controller': False}
        with self.assertRaises(ValueError):
            e.saved()
        e.inspect.return_value = {'controller': True, 'nbt': '{Progress:1} trailing'}
        with self.assertRaises(ValueError):
            e.saved()

    def test_identity_contains_progress_and_never_aliases_source(self):
        state = active(); identity = frozen_identity(state)
        state['Progress'] += 1
        self.assertNotEqual(identity, frozen_identity(state))
        self.assertEqual(frozen_identity(state, False), frozen_identity(active(), False))
        state['Reserved'][0]['components']['minecraft:custom_data']['identity'] = 'lost'
        self.assertNotEqual(identity['Reserved'], state['Reserved'])
        for key in ('Progress', 'Frozen', 'Snapshot', 'Reserved', 'Authorizer'):
            bad = active(); del bad[key]
            with self.subTest(key=key), self.assertRaises(ValueError):
                frozen_identity(bad)
        for key, value in (('Authorizer', [1]), ('Reserved', []), ('Duration', 0),
                           ('Progress', 200), ('Quality', 0), ('Frozen', {'id': 'only_id'})):
            bad = active(); bad[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                frozen_identity(bad)

    def test_items_and_multiset_preserve_component_identity(self):
        state = active(); rows = saved_items(state)
        self.assertEqual(rows[0], state['Snapshot'])
        self.assertEqual(multiset([rows[0], rows[0]]), multiset([dict(rows[0], count=2)]))
        changed = deepcopy(rows[0]); changed['components']['minecraft:damage'] = 0
        self.assertNotEqual(multiset([rows[0]]), multiset([changed]))
        with self.assertRaises(ValueError):
            saved_items(dict(Items=[state['Items'][0], state['Items'][0]]))

    def test_ledger_expansion_does_not_truncate_big_integers_or_components(self):
        stone = dict(id='myvillage:low_grade_spirit_stone', count=1,
                     components={'minecraft:custom_data': {'same': 1}})
        total = 10**40+9
        bag = dict(id='myvillage:spirit_stone_bag', count=1, components={
            'myvillage:spirit_stone_balance': [dict(item=stone, quantity=str(total))]})
        self.assertEqual(holdings({0: bag}), multiset([dict(stone, count=total)]))
        bag['components']['myvillage:spirit_stone_balance'][0]['quantity'] = '-1'
        with self.assertRaises(ValueError):
            holdings({0: bag})

    def test_costs_match_workshop_rule_and_ignore_blank_costs(self):
        rules = dict(recipes=[dict(id='tool', costs={'iron': 3, '#stone': 2}, traits=['tough'])],
                     traits={'tough': {'costs': {'#metal': 1}}})
        workshop = {'spirit_ingredient': '#stone'}
        self.assertEqual(infusion_costs(rules, workshop, 'tool', 'tough'), {'#metal': 1, '#stone': 2})
        with self.assertRaises(ValueError):
            infusion_costs(rules, workshop, 'tool', 'sharp')
        live_rules = json.loads((ROOT/'src/main/resources/data/myvillage/forging/rules.json').read_text())
        live_workshop = json.loads((ROOT/'src/main/resources/data/myvillage/forging/workshop.json').read_text())
        costs = infusion_costs(live_rules, live_workshop, 'forged_pickaxe', 'tough')
        self.assertNotIn('minecraft:iron_ingot', costs)
        for reference in costs:
            self.assertFalse(ingredient_item(reference).startswith('#'))


class GeometryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.layout = json.loads((ROOT/'src/main/resources/data/myvillage/forging/array.json').read_text())
        cls.mesh = json.loads((ROOT/'src/main/resources/assets/myvillage/forging_array/formed_mesh.json').read_text())

    def test_four_way_transform_and_mesh_origin(self):
        cell = dict(x=1, y=-1, z=1)
        expected = [(1, -60, 4), (-1, -60, 4), (-1, -60, 2), (1, -60, 2)]
        for facing, pos in zip(VECTORS, expected):
            self.assertEqual(world_pos(ORIGIN, facing, cell), pos)
        self.assertEqual(mesh_pos(ORIGIN, 'north', (0, 0, 0)), (.5, -60, 5.5))
        self.assertEqual(mesh_pos(ORIGIN, 'east', (0, 0, 0)), (-1.5, -60, 3.5))

    def test_actual_menu_and_slot_centres(self):
        self.assertEqual(menu_point((960, 540), 186, 128), (536, 278))
        self.assertEqual(inventory_point(0), (152, 232))
        self.assertEqual(inventory_point(8), (296, 232))
        self.assertEqual(inventory_point(9), (152, 174))
        self.assertEqual(inventory_point(35), (296, 210))
        for slot in (-1, 36, True):
            with self.assertRaises(ValueError):
                inventory_point(slot)

    def test_yaw_pitch_and_ray_slab_intersection(self):
        self.assertEqual(look_at((0, 0, 0), (0, 0, 1)), (0., 0.))
        self.assertEqual(look_at((0, 0, 0), (1, 0, 0)), (-90., 0.))
        self.assertEqual(look_at((0, 0, 0), (0, 1, 0)), (0., -90.))
        self.assertAlmostEqual(ray_distance((0, 0, 0), (0, 0, 4), (-1, -1, 3), (1, 1, 5)), 3)
        self.assertIsNone(ray_distance((0, 0, 0), (0, 0, -4), (-1, -1, 3), (1, 1, 5)))
        self.assertIsNone(ray_distance((3, 0, 0), (3, 0, 4), (-1, -1, 3), (1, 1, 5)))

    def test_controller_rays_hit_actual_sliced_shape_before_any_other_block(self):
        cell = next(c for c in self.layout['cells'] if c['role'] == 'controller')
        for facing in VECTORS:
            for formed in (False, True):
                boxes = collision_boxes(self.layout, self.mesh, ORIGIN, facing, formed)
                candidates = aim_candidates(self.layout, self.mesh, ORIGIN, facing, cell, formed)
                self.assertTrue(candidates)
                for ray in candidates:
                    eye = (ray['feet'][0], ray['feet'][1]+1.62, ray['feet'][2])
                    hits = [(d, pos) for pos, lo, hi in boxes
                            if (d := ray_distance(eye, ray['target'], lo, hi)) is not None]
                    self.assertEqual(min(hits)[1], ORIGIN)
                    self.assertLessEqual(min(hits)[0], 4.4)
        # A full block's centre would be wrong for the small formed pedestal.
        self.assertNotEqual(candidates[0]['target'][1], ORIGIN[1]+.5)

    def test_native_casing_repair_has_a_real_reachable_neighbor_face(self):
        e = ForgingArrayEvidence.__new__(ForgingArrayEvidence)
        e.layout, e.mesh, e.origin, e.facing = self.layout, self.mesh, ORIGIN, 'north'
        e.inspect = Mock(return_value={'formed': True})
        cell = e.casing()
        e.close_menu, e.rc, e.aim, e.log_input, e.poll = (Mock() for _ in range(5))
        e.s = Mock(); e.s.username = 'Fixture'
        with patch('tools.forging_array_evidence.time.sleep'):
            e.repair(cell)
        self.assertEqual(e.s.game.xdo.call_args.args, ('click', '3'))
        self.assertTrue(e.aim.called)
        self.assertEqual(e.poll.call_count, 2)
        self.assertIn('place_missing_casing', e.log_input.call_args.args)


class SafetyTests(unittest.TestCase):
    def test_close_uses_ui_safe_escape(self):
        e = ForgingArrayEvidence.__new__(ForgingArrayEvidence)
        e.s = Mock(); e.s.game.pointer_grabbed.return_value = False; e.log_input = Mock()
        with patch('tools.forging_array_evidence.time.sleep'):
            e.close_menu()
        e.s.game.xdo.assert_called_once_with('key', 'Escape')
        e.s.game.key.assert_not_called()
        e.s.game.require_ingame.assert_called_once()

    def test_stop_never_touches_another_session(self):
        e = ForgingArrayEvidence.__new__(ForgingArrayEvidence)
        e.s = Mock(); e.owned_session_ids = ['mine']
        with patch('tools.forging_array_evidence.cs.read_state', return_value={'session_id': 'other'}), \
             patch('tools.forging_array_evidence.cs.stop') as stop:
            with self.assertRaises(RuntimeError):
                e.stop()
            stop.assert_not_called()

    def test_existing_output_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); (root/'failed.json').write_text('preserve')
            with self.assertRaises(ValueError):
                ForgingArrayEvidence(root, ['structure'])
            self.assertEqual((root/'failed.json').read_text(), 'preserve')

    def test_export_keeps_owned_world_only_and_does_not_follow_symlinks(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); world = root/'server'/'fixture'; world.mkdir(parents=True)
            for name in ('level.dat', 'region/r.0.0.mca', 'playerdata/user.dat', 'session.lock', 'stats/user.json'):
                path = world/name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(name)
            outside = root/'owner.dat'; outside.write_text('private')
            (world/'symlink.dat').symlink_to(outside)
            e = ForgingArrayEvidence.__new__(ForgingArrayEvidence)
            e.out = root/'out'; e.out.mkdir(); e.report = {}; e.save = Mock()
            e.check = lambda name, value, detail: self.assertTrue(value, name)
            with patch('tools.forging_array_evidence.cs.SERVER_DIR', world.parent), \
                 patch('tools.forging_array_evidence.cs.LEVEL_NAME', 'fixture'):
                e.export_world()
            with zipfile.ZipFile(e.out/'forging_array_preview_world.zip') as archive:
                names = archive.namelist()
                self.assertTrue(any(name.endswith('/level.dat') for name in names))
                self.assertFalse(any('symlink' in name or 'session.lock' in name or '/stats/' in name for name in names))
            self.assertEqual(outside.read_text(), 'private')


if __name__ == '__main__':
    unittest.main()
