import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch

from tools.alchemy_quality_evidence import (
    QUALITY, QualityEvidence, SecondClient, inventory_point, menu_point, pill_quality, quality_stacks, read_batch, read_data,
)


class QualityReadbackTest(unittest.TestCase):
    def test_full_components_survive_saved_stack_parsing(self):
        reply = ('Player has the following entity data: [{Slot: 0b, count: 2, id: "myvillage:pill_huiling", '
                 'components: {"myvillage:pill_quality": 4, "minecraft:custom_data": '
                 '{proof: "same, nested: [value]", list: [1b,2L]}}}]')
        rows = quality_stacks(reply)
        self.assertEqual(rows[0]['components'][QUALITY], 4)
        self.assertEqual(rows[0]['components']['minecraft:custom_data'],
                         {'proof': 'same, nested: [value]', 'list': [1, 2]})
        self.assertEqual(pill_quality(rows[0]), 4)

    def test_legacy_component_absence_is_preserved_not_normalized(self):
        rows = quality_stacks('Block has the following block data: [{Slot:4b,id:"myvillage:pill_huiling",count:2}]')
        self.assertEqual(pill_quality(rows[4]), 1)
        self.assertNotIn('components', rows[4])
        explicit = {'id': 'myvillage:pill_huiling', 'count': 2, 'components': {QUALITY: 1}}
        self.assertEqual(pill_quality(explicit), 1)
        self.assertNotEqual(rows[4], explicit)

    def test_invalid_component_does_not_infer_high_quality(self):
        for value in (-1, 0, 5, 100, '4', True, 4.0):
            self.assertEqual(pill_quality({'components': {QUALITY: value}}), 1)

    def test_missing_incomplete_duplicate_or_truncated_inventory_fails(self):
        for reply in ('No entity was found', 'data: {}', 'data: [{Slot:0b,id:"a:b"}]',
                      'data: [{Slot:0b,id:"a:b",count:1},{Slot:0b,id:"a:b",count:1}]',
                      'data: [{Slot:0b,id:"a:b",count:0}]',
                      'data: [{Slot:0b,id:"a:b",count:1,components:{"myvillage:pill_quality":4}]'):
            with self.subTest(reply=reply), self.assertRaises(ValueError):
                quality_stacks(reply)

    def test_whole_batch_including_all_uuid_words(self):
        reply = ('0, -60, 3 has the following block data: {Brewer:[I;123,-456,789,-123456789], '
                 'Quality:3, Cap:4, Version:1, Recipe:"huiling", Score:100}')
        value = read_batch(reply)
        self.assertEqual(value, {'Brewer': [123, -456, 789, -123456789],
                                'Quality': 3, 'Cap': 4, 'Version': 1, 'Recipe': 'huiling', 'Score': 100})
        self.assertNotEqual(value, {**value, 'Quality': 2})
        self.assertNotEqual(value, {**value, 'Brewer': [123, -456, 789, -123456788]})

    def test_absent_batch_differs_from_failed_readback(self):
        self.assertIsNone(read_batch('Found no elements matching Batch'))
        for value in ('No block entity was found', 'data: {}', 'data: {Version:1,Score:100}',
                      'data: {Version:1,Brewer:[I;1,2,3,4],Recipe:"huiling",Score:100,Cap:2,Quality:4}',
                      'data: {Version:1,Brewer:[I;1,2,3],Recipe:"huiling",Score:100,Cap:4,Quality:4}'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                read_batch(value)

    def test_read_data_rejects_partial_duplicate_and_extra_payload(self):
        for value in ('data: {a:1,a:2}', 'data: {a:[1,2}', 'data: {a:"unterminated}',
                      'data: {a:1} ignored', 'data: '):
            with self.subTest(value=value), self.assertRaises(ValueError):
                read_data(value)
        self.assertEqual(read_data('data: {quoted:\'x"y\', n:1.25f, z:-3L}'),
                         {'quoted': 'x"y', 'n': 1.25, 'z': -3})


class QualityCaptureContractTest(unittest.TestCase):
    def test_start_coordinates_fit_all_required_gui_sizes(self):
        for size, expected in (((960, 540), (480, 274)), ((854, 480), (426, 244)), ((640, 480), (320, 244))):
            x, y = menu_point(size, 106, 113)
            self.assertEqual((x, y), expected)
            self.assertGreater(x, 0)
            self.assertLess(y, size[1])

    def test_mapped_start_sends_actual_mouse_click(self):
        with tempfile.TemporaryDirectory() as directory:
            evidence = QualityEvidence(Path(directory))
            calls = []
            game = SimpleNamespace(size=(960, 540), xdo=lambda *args: calls.append(args))
            with patch('tools.alchemy_quality_evidence.time.sleep'):
                evidence.mapped_start(game)
            self.assertEqual(calls, [('mousemove', '480', '274'), ('click', '1')])

    def test_inventory_hover_follows_vanilla_recipe_book_shift(self):
        self.assertEqual(inventory_point((960, 540), 0), (336, 404))
        self.assertEqual(inventory_point((960, 540), 2), (408, 404))
        self.assertEqual(inventory_point((960, 540), 0, True), (490, 404))
        for slot in (-1, 9, True):
            with self.assertRaises(ValueError): inventory_point((960, 540), slot)

    def test_pill_tooltips_keep_hover_and_never_send_use_or_click(self):
        with tempfile.TemporaryDirectory() as directory:
            evidence = QualityEvidence(Path(directory)/'proof')
            options = Path(directory)/'repo/run-combat-capture/client/options.txt'
            options.parent.mkdir(parents=True); options.write_text('key_key.inventory:key.keyboard.e\n')
            calls = []
            def xdo(*args):
                calls.append(args)
                return '42' if args[0] == 'search' else ''
            game = SimpleNamespace(size=(960, 540), env={}, xdo=xdo,
                                   require_ingame=Mock(), key=Mock(), pointer_grabbed=lambda: False)
            evidence.s = SimpleNamespace(username='CaptureDev', game=game)
            inventory = {i: {'id': recipe['output'], 'count': 2, 'components': {QUALITY: 4}}
                         for i, recipe in enumerate(evidence.rules['recipes'])}
            evidence.inventory = Mock(return_value=inventory)
            evidence.med = Mock(return_value={'cooldown_ticks': 400, 'effect_ticks': 1000, 'quality': 4})
            evidence.rc = Mock(side_effect=lambda command: 'Player has the following entity data: 0b'
                               if command.endswith('recipeBook.isGuiOpen') else 'fixture')
            with patch('tools.alchemy_quality_evidence.ROOT', options.parents[2]), \
                    patch('tools.alchemy_quality_evidence.time.sleep'), \
                    patch('tools.alchemy_quality_evidence.subprocess.run') as capture:
                evidence.pill_tooltips('zh_cn')
            game.key.assert_called_once_with('e')
            self.assertEqual(capture.call_count, 3)
            self.assertEqual([args for args in calls if args[0] == 'mousemove'],
                             [('mousemove', '336', '404'), ('mousemove', '372', '404'), ('mousemove', '408', '404')])
            self.assertFalse(any(args[0] in ('click', 'mousedown', 'mouseup') for args in calls))
            self.assertEqual(calls[-1], ('key', 'Escape'))
            self.assertTrue(evidence.report['checks'][-1]['pass'])
            self.assertEqual(evidence.report['phases'][-1]['phase'], 'pill_quality_tooltips')

    def test_existing_evidence_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            marker = Path(directory)/'evidence.json'; marker.write_text('earlier proof')
            with self.assertRaisesRegex(ValueError, 'retain earlier runs'):
                QualityEvidence(Path(directory))
            self.assertEqual(marker.read_text(), 'earlier proof')

    def test_second_client_cleanup_targets_owned_trees_only(self):
        with tempfile.TemporaryDirectory() as directory:
            evidence = QualityEvidence(Path(directory))
            guest = SecondClient(evidence)
            # No process was started: cleanup cannot reach the capture supervisor or server.
            with patch('tools.alchemy_quality_evidence.procs.kill_pids') as kill:
                guest.__exit__(None, None, None)
            kill.assert_called_once_with([], grace=8)
            self.assertTrue(evidence.report['checks'][-1]['pass'])


if __name__ == '__main__':
    unittest.main()
