"""扩展采集离线测试：边界、派发、语义、fixture权限及原始像素；不启动游戏。"""
from __future__ import annotations

import copy
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
import zipfile

from tools.qianzhen_opening_extended import (ActorCollector, ExtendedSession, InventoryFixture,
    identity, inventory_semantics, item_count, personal, saved_semantics, validate_path)
from tools.qianzhen_opening_extended_scenarios import ExtendedScenarios, fate_identity, marker_centres
from tools.qianzhen_opening_extended_fixtures import NativeFixtureProbe, uuid_text
from tools.qianzhen_opening_extended_saved import extract_saved_fixture, materialize_saved_resources, SavedFixtureSession
from tools.qianzhen_opening_evidence import digest, fixture
from tools.qianzhen_opening_extended_run import validate_execution_manifest

ROOT = Path(__file__).resolve().parents[2]


def state(name='a'):
    return {'player': name, 'chapter': 'qianzhen_chapter_1', 'schema': 1, 'definition_version': 2,
            'site': 'qianzhen_chapter_1', 'dimension': 'minecraft:overworld', 'stage': 'report', 'revision': 20,
            'accepted': True, 'completed': False, 'evidence': [], 'receipts': ['supplies', 'plant_a'],
            'reward': 'pending', 'opening': {'planted': 1}, 'shared': {'returned': False, 'regulated': False},
            'npc': {name: {'personId': i, 'epoch': 1, 'position': {'x': 0, 'y': 1, 'z': 2}}
                    for i, name in enumerate(('guide', 'pharmacist', 'zhou_he', 'warden'), 165)}}


class SemanticTests(unittest.TestCase):
    def test_runtime_revision_epochs_and_body_position_are_not_identity(self):
        before, after = state(), state()
        after['revision'] += 5
        after['npc']['guide']['epoch'] += 2
        after['npc']['guide']['position']['x'] += 1
        result = saved_semantics(before, after)
        self.assertTrue(result['personal_equal'])
        self.assertTrue(result['people_equal'])
        self.assertEqual({'before': 1, 'after': 3}, result['epochs']['guide'])

    def test_persisted_progress_reward_receipt_or_person_changes_fail(self):
        before = state()
        for field in ('stage', 'reward', 'receipts', 'evidence', 'opening', 'definition_version'):
            with self.subTest(field=field):
                after = copy.deepcopy(before)
                after[field] = 'bad'
                self.assertFalse(saved_semantics(before, after)['personal_equal'])
        after = copy.deepcopy(before)
        after['npc']['guide']['personId'] += 100
        self.assertFalse(saved_semantics(before, after)['people_equal'])
        after['shared']['returned'] = True
        self.assertFalse(saved_semantics(before, after)['shared_equal'])

    def test_duplicate_missing_people_never_pass_identity(self):
        for change in ('duplicate', 'missing', 'negative'):
            bad = state()
            if change == 'duplicate':
                bad['npc']['guide']['personId'] = bad['npc']['warden']['personId']
            elif change == 'missing':
                bad['npc'].pop('guide')
            else:
                bad['npc']['guide']['personId'] = -1
            with self.assertRaises(ValueError):
                identity(bad)

    def test_inventory_order_ignored_but_components_and_slot_preserved(self):
        rows = [{'Slot': 2, 'id': 'a', 'count': 1, 'components': {'quality': 4}}, {'Slot': 0, 'id': 'b', 'count': 2}]
        self.assertEqual(inventory_semantics(rows), inventory_semantics(rows[::-1]))
        changed = copy.deepcopy(rows)
        changed[0]['components']['quality'] = 1
        self.assertNotEqual(inventory_semantics(rows), inventory_semantics(changed))
        self.assertEqual(2, item_count(rows, 'b'))

    def test_fate_rolls_locked_selection_are_preserved_not_runtime_session(self):
        before = dict(candidates=['a', 'b'], confirmed=True, selected=['a'], locked='a', rolls=1, awakening_session=9, now=100)
        after = {**before, 'now': 200, 'awakening_session': 0}
        self.assertEqual(fate_identity(before), fate_identity(after))
        for field, bad in [('rolls', 2), ('locked', 'b'), ('selected', ['b']), ('candidates', ['b', 'a'])]:
            self.assertNotEqual(fate_identity(before), fate_identity({**after, field: bad}))
        with self.assertRaises(ValueError):
            fate_identity({})

    def test_paths_refuse_diagonal_teleport_bool_and_unbounded(self):
        self.assertEqual([(0, 0, 0), (1, 1, 0)], validate_path([[0, 0, 0], [1, 1, 0]]))
        for bad in ([], [[0, 0, 0], [2, 0, 0]], [[0, 0, 0], [1, 0, 1]], [[True, 0, 0]], [[0, 0, 0]] * 513):
            with self.assertRaises(ValueError):
                validate_path(bad)


class FixtureTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.primary = SimpleNamespace(emit=Mock())
        self.sender = Mock(return_value='Replaced a slot on A with [Cobblestone]')
        self.api = InventoryFixture(self.sender, self.root / 'fixture.jsonl', self.primary)
        self.addCleanup(self.api.stream.close)

    def test_fixture_writes_only_unrelated_inventory_and_cleans_owned_slots(self):
        self.api.command('A', 0, True, purpose='full_inventory_pending')
        self.api.command('A', 9, True, purpose='full_inventory_pending')
        self.api.command('A', 0, False, purpose='fixture_cleanup')
        commands = [call.args[0] for call in self.sender.call_args_list]
        self.assertEqual('item replace entity A hotbar.0 with minecraft:cobblestone 64', commands[0])
        self.assertEqual('item replace entity A inventory.0 with minecraft:cobblestone 64', commands[1])
        self.assertEqual('item replace entity A hotbar.0 with minecraft:air', commands[2])
        self.assertEqual(3, self.api.verify()['receipts'])
        self.assertTrue(all(call.args[0] == 'observation' and 'command' not in call.kwargs for call in self.primary.emit.call_args_list))

    def test_fixture_rejects_injection_wrong_slot_and_unowned_cleanup(self):
        for player, slot in [('A\nstop', 0), ('A', 36), ('A', -1), ('A', True)]:
            with self.assertRaises(ValueError):
                self.api.command(player, slot, True, purpose='full_inventory_pending')
        with self.assertRaises(ValueError):
            self.api.command('A', 1, False, purpose='cleanup')
        self.sender.assert_not_called()

    def test_fixture_failed_write_retains_raw_failure_and_cannot_verify(self):
        self.sender.side_effect = TimeoutError('retained')
        with self.assertRaises(TimeoutError):
            self.api.command('A', 0, True, purpose='full_inventory_pending')
        with self.assertRaises(AssertionError):
            self.api.verify()
        self.assertIn('retained', self.api.path.read_text())

    def test_cleanup_never_deletes_modified_or_player_reward_stack(self):
        self.api.command('A', 0, True, purpose='full_inventory_pending')
        self.api.command('A', 1, True, purpose='full_inventory_pending')
        self.api.command('A', 2, True, purpose='full_inventory_pending')
        self.sender.reset_mock()
        flow = SimpleNamespace(c=SimpleNamespace(username='A'), data=lambda _: [
            {'Slot': 0, 'id': 'minecraft:bread', 'count': 6},
            {'Slot': 1, 'id': 'minecraft:cobblestone', 'count': 63},
            {'Slot': 2, 'id': 'minecraft:cobblestone', 'count': 64}])
        self.api.cleanup((flow,))
        self.sender.assert_called_once_with('item replace entity A hotbar.2 with minecraft:air', 8)

    def test_full_bag_refuses_reward_stack_merging_instead_of_overwriting(self):
        flow = SimpleNamespace(c=SimpleNamespace(username='A'), chapter={'work': {'item': 'myvillage:spirit_leaf'},
                   'rewards': [{'item': 'minecraft:bread', 'count': 6}]},
                   data=lambda _: [{'Slot': 0, 'id': 'minecraft:bread', 'count': 1}])
        with self.assertRaisesRegex(ValueError, '奖励堆叠'):
            self.api.fill(flow)
        self.sender.assert_not_called()

    def test_native_probe_uses_real_registration_and_only_current_npc_uuid_for_write(self):
        probe = NativeFixtureProbe(self.sender, self.root / 'native.jsonl', ROOT, Mock(), provenance={'kind': 'declared'})
        self.addCleanup(probe.close)
        self.assertEqual(1.9, probe.height)
        self.assertEqual('00000000-0000-0000-0000-000000000001', uuid_text([0, 0, 0, 1]))
        with self.assertRaises(ValueError):
            probe.body(-1)
        self.sender.assert_not_called()


class SharedCollectorTests(unittest.TestCase):
    def test_guest_keeps_own_input_gate_but_global_action_readback_ids(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            log = root / 'guest.log'
            log.write_text('joined')
            primary = SimpleNamespace(sequence=11, readback_id=12, capture_id=13, out=root,
                stream=io.StringIO(), readbacks=io.StringIO(), read_command=Mock(), emit=Mock())
            game = SimpleNamespace(client_log=log)
            c = ActorCollector(primary, game, 'B')
            self.assertIs(c.input.game, game)
            self.assertEqual('B', c.input.player)
            c.readback_id += 1
            c.capture_id += 1
            self.assertEqual(13, primary.readback_id)
            self.assertEqual(14, primary.capture_id)
            self.assertEqual(11, c.sequence)
            c.emit('observation', label='guest')
            primary.emit.assert_called_once_with('observation', label='guest')


class MarkerTests(unittest.TestCase):
    def test_native_diamond_detector_refuses_gold_text_and_finds_unique_marker(self):
        from PIL import Image
        for scale in (1, 2, 3):
            image = Image.new('RGB', (100 * scale, 100 * scale), 'black')
            for x in range(10 * scale, 30 * scale):
                image.putpixel((x, 20 * scale), (243, 216, 148))
            self.assertEqual([], marker_centres(image, scale))
            # Pixel geometry of production diamond, not a screenshot substitute.
            for dy in range(-4, 5):
                dx = 4 - abs(dy)
                for px in (-dx, dx):
                    for sx in range(scale):
                        for sy in range(scale):
                            image.putpixel(((50 + px) * scale + sx, (50 + dy) * scale + sy), (243, 216, 148))
            for x in range(49 * scale, 52 * scale):
                for y in range(49 * scale, 52 * scale):
                    image.putpixel((x, y), (255, 243, 201))
            found = marker_centres(image, scale)
            self.assertEqual(1, len(found))
            self.assertLessEqual(abs(found[0][0] - 50 * scale), scale)

    def test_work_rejects_duplicate_execution_before_any_action(self):
        session = SimpleNamespace(work={'complete': True})
        with self.assertRaisesRegex(RuntimeError, '只能执行一次'):
            ExtendedScenarios(session).work_reward()

    def test_marker_cannot_be_claimed_from_final_state_without_earlier_native_matrix(self):
        scenarios = ExtendedScenarios(SimpleNamespace(a=Mock(), work={'complete': True}))
        with self.assertRaisesRegex(ValueError, '同一run'):
            scenarios.marker_matrix()


class DispatchTests(unittest.TestCase):
    def make_session(self):
        s = object.__new__(ExtendedSession)
        s.a = SimpleNamespace(checks=[], screens=[], capture=Mock(return_value={'path': 'failure.png'}))
        s.a.c = SimpleNamespace(sequence=0, readback_id=0)
        s.binding = dict(run_id='extended-test-123', source_commit='a' * 40, source_fingerprint='b' * 64, request_sha256='c' * 64)
        s.assert_source = Mock()
        s.results, s.work, s.selected, s.active = {}, {}, [], None
        s.home = s.portal = s.marker_fixture = None
        s.scenarios = SimpleNamespace(marker_prepare=Mock(), marker={})
        def run_stage(name):
            def action():
                s.a.checks = [{'id': key, 'pass': True} for key in next(row['checks'] for row in fixture()['stages'] if row['id'] == name)]
                s.a.screens = [{'path': name + '.png'}]
            return action
        for name in ('natural_spawn', 'recruitment_walk', 'voluntary_contract', 'early_teaching', 'roof_seed20261024'):
            setattr(s.a, name, run_stage(name))
        s.work_reward = run_stage('work_reward')
        s.restart_two_players = run_stage('restart_two_players')
        s.optional_personal_story = run_stage('optional_personal_story')
        s.marker_matrix = run_stage('marker_matrix')
        for name in ('restart_two_players', 'optional_personal_story', 'marker_matrix'):
            setattr(s.a, name, lambda name=name: getattr(s, name)())
        s.a.roof_seed20261024 = run_stage('roof_seed20261024')
        return s

    def test_roof_does_not_silently_require_work_or_teaching(self):
        s = self.make_session()
        rows = list(s.dispatch(['roof_seed20261024']))
        self.assertEqual(['natural_spawn', 'recruitment_walk', 'roof_seed20261024'], [row['id'] for row in rows])
        self.assertEqual([False, False, True], [row['counted_as_selected'] for row in rows])

    def test_work_and_restart_dispatch_actual_prerequisites_only_once(self):
        s = self.make_session()
        rows = list(s.dispatch(['work_reward', 'restart_two_players']))
        self.assertEqual(['natural_spawn', 'recruitment_walk', 'voluntary_contract', 'early_teaching', 'work_reward', 'restart_two_players'], [r['id'] for r in rows])
        self.assertEqual(2, sum(r['counted_as_selected'] for r in rows))

    def test_marker_preparation_occurs_before_work_and_then_final_selected_stage(self):
        s = self.make_session()
        s.portal, s.marker_fixture = {}, SimpleNamespace()
        order = []
        s.scenarios.marker_prepare = lambda: order.append('prepare')
        work = s.work_reward
        s.work_reward = lambda: (order.append('work'), work())
        rows = list(s.dispatch(['marker_matrix']))
        self.assertEqual(['prepare', 'work'], order)
        self.assertEqual('marker_matrix', rows[-1]['id'])

    def test_missing_fixture_and_saved_cases_refuse_before_player_actions(self):
        for scenario in ('marker_matrix', 'optional_personal_story', 'legacy_accepted', 'existing_awakened'):
            s = self.make_session()
            s.a.natural_spawn = Mock()
            with self.assertRaises(ValueError):
                list(s.dispatch([scenario]))
            s.a.natural_spawn.assert_not_called()

    def test_manifest_failure_preserves_selected_and_failed_vs_unrun(self):
        s = self.make_session()
        with tempfile.TemporaryDirectory() as folder:
            s.out = Path(folder)
            s.binding = dict(run_id='extended-test-123', source_commit='a' * 40, source_fingerprint='b' * 64, request_sha256='c' * 64)
            s.work_reward = Mock(side_effect=TimeoutError('real maturity timeout retained'))
            with self.assertRaises(TimeoutError):
                s.run_selected(['work_reward', 'restart_two_players'], lambda row: None)
            result = json.loads((s.out / 'extended-dispatch.json').read_text())
            self.assertEqual([], result['completed'])
            self.assertEqual(['restart_two_players'], result['unrun'])
            self.assertEqual([], result['captured'])
            self.assertEqual([], result['pending_acceptance'])
            self.assertEqual(['work_reward'], result['failed'])
            self.assertIn('maturity', result['failure'])


class InteractionStateTests(unittest.TestCase):
    def actors(self):
        def actor(name):
            story = state(name)
            story.update(stage='planting', reward='locked')
            story['opening'] = {'planted': 0, 'supplies': True}
            inventory = [{'Slot': 0, 'id': 'myvillage:spirit_leaf', 'count': 3}]
            flow = SimpleNamespace(chapter={'work': {'item': 'myvillage:spirit_leaf', 'count': 3},
                'rewards': [{'item': 'minecraft:bread', 'count': 6}, {'item': 'myvillage:low_grade_spirit_stone', 'count': 1}]},
                state=lambda: copy.deepcopy(story), data=lambda _: copy.deepcopy(inventory), capture=lambda label: {'path': label})
            def execute(step):
                if step['op'] == 'right_click':
                    story['opening']['planted'] += 1
                    story['receipts'].append('plant_' + str(story['opening']['planted']))
                    inventory[0]['count'] -= 1
            flow.c = SimpleNamespace(username=name, execute=execute)
            def poll(fn, predicate, timeout, label):
                value = fn()
                if not predicate(value):
                    raise TimeoutError(label)
                return value
            flow.poll = poll
            flow._story, flow._inventory = story, inventory
            return flow
        a, b = actor('A'), actor('B')
        session = SimpleNamespace(a=a, b=b, closed_menu=Mock(), work={})
        scenarios = ExtendedScenarios(session)
        scenarios.crop_config = lambda: ('myvillage:spirit_leaf_crop', 0, 2)
        scenarios.is_crop = Mock(return_value=True)
        scenarios.select_item = Mock()
        scenarios.approach_crop = Mock()
        scenarios.note = Mock()
        return scenarios, a, b

    def test_two_positive_actors_plant_and_pay_their_own_seed_cost(self):
        scenarios, a, b = self.actors()
        ra = scenarios.plant(a, (1, 0, 1))
        rb = scenarios.plant(b, (2, 0, 1))
        self.assertEqual(1, ra['after']['opening']['planted'])
        self.assertEqual(1, rb['after']['opening']['planted'])
        self.assertEqual(1, rb['peer_after']['opening']['planted'])
        self.assertEqual(2, a._inventory[0]['count'])
        self.assertEqual(2, b._inventory[0]['count'])
        self.assertEqual(ra['peer_before'], ra['peer_after'])
        self.assertEqual(rb['peer_inventory_before'], rb['peer_inventory_after'])

    def test_plant_fails_if_peer_receipts_or_inventory_changed(self):
        for mutate in ('receipt', 'inventory'):
            scenarios, a, b = self.actors()
            original = a.c.execute
            def execute(step):
                original(step)
                if mutate == 'receipt':
                    b._story['receipts'].append('stolen')
                else:
                    b._inventory[0]['count'] -= 1
            a.c.execute = execute
            with self.assertRaisesRegex(AssertionError, '归因'):
                scenarios.plant(a, (1, 0, 1))

    def test_plant_fails_without_actual_seed_cost_even_if_state_count_changes(self):
        scenarios, a, b = self.actors()
        original = a.c.execute
        def free_plant(step):
            original(step)
            a._inventory[0]['count'] += 1
        a.c.execute = free_plant
        with self.assertRaisesRegex(AssertionError, '归因'):
            scenarios.plant(a, (1, 0, 1))

    def test_late_player_teaching_really_runs_after_first_player_payment(self):
        scenarios, a, b = self.actors()
        session = scenarios.s
        session.selected = []
        session.work = {}
        session.fixture = SimpleNamespace(cleanup=Mock(), verify=lambda: {'declared': True})
        session.actor_proof = lambda flow: {'name': flow.c.username}
        session.guest_start = lambda: b
        b._story.update(accepted=False, stage='new')
        b._story['receipts'] = []
        position = [0., 1., 0.]
        b.data = lambda path: position[:] if path == 'Pos' else []
        b.c.input = SimpleNamespace(hold=lambda *args: position.__setitem__(0, position[0] + .2))
        a.check = lambda name, passed, detail: self.assertTrue(passed, name)
        a.stage = lambda name: a.state()
        b.stage = lambda name: b.state()
        scenarios.soils = lambda: [(i,0,0) for i in range(5)]
        events = []
        def supplies(flow):
            flow._story['opening']['supplies'] = True
            return {'after': flow.state()}
        scenarios.take_supplies = supplies
        def plant(flow, soil):
            flow._story['opening']['planted'] += 1
            events.append('plant_' + flow.c.username)
            return {'soil': soil, 'player': flow.c.username}
        scenarios.plant = plant
        def claim(flow, full=False):
            flow._story.update(completed=True, reward='claimed', stage='complete')
            events.append('paid_' + flow.c.username)
            return {'delta': {r['item']:r['count'] for r in flow.chapter['rewards']}, 'repeat': {'options': []}}
        scenarios.claim_once = claim
        def prepare():
            self.assertTrue(a._story['completed'])
            self.assertFalse(b._story['accepted'])
            events.append('teach_B')
            b._story.update(accepted=True, stage='work_offer')
            return b
        scenarios.prepare_b = prepare
        def rotation(flow, soil):
            flow._story['opening']['planted'] += 1
            events.append('rotate_B')
            return {'frames': ['actual fixture in native, fake here']}
        scenarios.harvest_replant = rotation
        scenarios.work_reward()
        self.assertLess(events.index('paid_A'), events.index('teach_B'))
        self.assertLess(events.index('teach_B'), events.index('plant_B'))
        self.assertLess(events.index('rotate_B'), events.index('paid_B'))
        self.assertTrue(session.work['a_complete_before_b_teaching'])
        self.assertEqual(3, a._story['opening']['planted'])
        self.assertEqual(3, b._story['opening']['planted'])

    def test_duplicate_reward_option_and_extra_gain_are_failures(self):
        for bad in ('option', 'extra'):
            scenarios, a, b = self.actors()
            a._story.update(stage='report', reward='pending')
            def actor(role, option):
                if option == 'claim_reward':
                    a._story.update(stage='complete', completed=True, reward='claimed')
                    a._story['receipts'].append('reward')
                    for i,row in enumerate(a.chapter['rewards'], 1):
                        a._inventory.append({'Slot': i, 'id': row['item'], 'count': row['count'] + (1 if bad == 'extra' else 0)})
            a.actor = actor
            a.stage = lambda _: a.state()
            scenarios.menu_options = lambda _: {'options': ['claim_reward'] if bad == 'option' else []}
            with self.assertRaises(AssertionError):
                scenarios.claim_once(a)

    def test_configured_reward_delta_is_measured_not_absolute_inventory(self):
        scenarios, a, b = self.actors()
        a._story.update(stage='report', reward='pending')
        a._inventory.append({'Slot': 1, 'id': 'minecraft:bread', 'count': 2})
        def actor(role, option):
            if option == 'claim_reward':
                a._story.update(stage='complete', completed=True, reward='claimed')
                a._story['receipts'].append('reward')
                a._inventory[1]['count'] += 6
                a._inventory.append({'Slot': 2, 'id': 'myvillage:low_grade_spirit_stone', 'count': 1})
        a.actor = actor
        a.stage = lambda _: a.state()
        scenarios.menu_options = lambda _: {'options': []}
        result = scenarios.claim_once(a)
        self.assertEqual({'minecraft:bread': 6, 'myvillage:low_grade_spirit_stone': 1}, result['delta'])
        self.assertEqual(result['peer_before'], result['peer_after'])


class EntryTests(unittest.TestCase):
    def manifest(self):
        return dict(schema='qianzhen-opening-extended-attachment-v1', run_id='extended-12345', username='A',
                    scenarios=['work_reward'], timeout=600, session_config={'session_id':'extended-12345',
                    'username':'A', 'natural_terrain':True, 'world_seed':20261024})

    def test_direct_entry_requires_isolated_fresh_source_output_and_exact_actor(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)/'extended-12345/repo'
            root.mkdir(parents=True)
            out=root.parent/'evidence'
            self.assertEqual(['work_reward'], validate_execution_manifest(self.manifest(),root,out))
            for change in ({'username':'B'}, {'timeout':1}, {'scenarios':['legacy_accepted']},
                           {'scenarios':['marker_matrix']}, {'schema':'other'}):
                with self.assertRaises(ValueError):
                    validate_execution_manifest({**self.manifest(), **change},root,out)
            with self.assertRaises(ValueError):
                validate_execution_manifest(self.manifest(),root,root/'evidence')
            out.mkdir()
            with self.assertRaises(ValueError):
                validate_execution_manifest(self.manifest(),root,out)

    def test_direct_entry_never_relabels_a_reused_world_as_fresh(self):
        spec=self.manifest()
        spec['session_config']['reuse_world']=True
        with self.assertRaises(ValueError):
            validate_execution_manifest(spec,Path('/tmp/extended-12345/repo'),Path('/tmp/extended-12345/new-evidence'))


class SavedFixtureTests(unittest.TestCase):
    def test_bad_zip_hash_and_zip_slip_refuse_without_extracting(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive = root / 'old.zip'
            with zipfile.ZipFile(archive, 'w') as zipped:
                zipped.writestr('Qianzhen Story/../../evil', 'unsafe')
            spec = dict(schema='qianzhen-saved-fixture-v1', archive=str(archive), archive_sha256='0' * 64,
                        archive_root='Qianzhen Story', player_uuid='uuid', provenance={'source': 'real'})
            with self.assertRaisesRegex(ValueError, '身份不符'):
                extract_saved_fixture(spec, root / 'copy')
            spec['archive_sha256'] = digest(archive)
            with self.assertRaisesRegex(ValueError, '越界'):
                extract_saved_fixture(spec, root / 'copy')
            self.assertFalse((root / 'copy').exists())

    def test_checkpoint_refuses_reload_or_missing_saved_sources(self):
        adapter = SavedFixtureSession({}, {}, None)
        valid = {'flush': {'requests': 1, 'status': 'confirmed'}, 'termination': {'status': 'terminated'},
                 'archive': {'exact_sources': {'worldsim': 'sha'}}, 'old_server': {'pid': 1}, 'new_server': {'pid': 2},
                 'before': state(), 'after': state()}
        self.assertTrue(adapter.validate_restart(valid)['people_equal'])
        for field, bad in [('old_server', {'pid': 2}), ('archive', {'exact_sources': {}}),
                           ('flush', {'requests': 2, 'status': 'confirmed'})]:
            with self.assertRaises(AssertionError):
                adapter.validate_restart({**valid, field: bad})

    def test_saved_scene_resource_copy_rejects_unrelated_new_pack(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            jar = root / 'original.jar'
            with zipfile.ZipFile(jar, 'w') as zipped:
                zipped.writestr('wrong', 'new candidate')
            spec = {'resources': {'kind': 'original_jar', 'path': str(jar), 'sha256': '0' * 64}}
            with self.assertRaisesRegex(ValueError, '历史jar hash'):
                materialize_saved_resources(spec, root, root / 'resources')


if __name__ == '__main__':
    unittest.main()
