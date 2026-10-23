"""Offline collector checks: strict logs, physical routes and safety boundaries."""
import ast
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from unittest.mock import Mock

from tools.qianzhen.story_evidence_helpers import (
    cardinal_path, client_views, compile_route, dialogue_buttons, finite_vector,
    require_subset, reward_counts, status_object,
)
from tools.qianzhen.story_evidence import StoryEvidence, HANDOFF_SNAPSHOT, route_ends


class StoryParserTests(unittest.TestCase):
    def test_applied_client_snapshots_keep_false_flags_and_revisions(self):
        text = '[Render thread] QIANZHEN_STORY_VIEW revision=4 available=true accepted=true completed=false chapter=qianzhen_chapter_1 title=灯尽之前\n'
        self.assertEqual(client_views(text), [{'revision':4,'available':True,'accepted':True,'completed':False,'chapter':'qianzhen_chapter_1'}])
        self.assertEqual(client_views('QIANZHEN_STORY_VIEW revision=-1 available=yes'), [])
        self.assertEqual(client_views('server status revision=4 accepted=true'), [])

    def test_status_requires_one_exact_prefix_and_complete_json(self):
        self.assertEqual(status_object('unrelated\nQIANZHEN_STORY_STATUS {"stage":"x"}'), {'stage':'x'})
        for reply in ('ok', 'QIANZHEN_STORY_STATUS []', 'QIANZHEN_STORY_STATUS {',
                      'QIANZHEN_STORY_STATUS {}\nQIANZHEN_STORY_STATUS {}',
                      'chat: QIANZHEN_STORY_STATUS {}'):
            with self.subTest(reply=reply), self.assertRaises(ValueError):
                status_object(reply)

    def test_physical_buttons_latest_nonce_and_actual_bounds(self):
        def line(option, nonce, x=300):
            return f'[Render thread] QIANZHEN_DIALOGUE option={option} x={x} y=220 w=160 h=20 nonce={nonce}\n'
        text = line('old', 1)+line('accept', 2)+line('close', 2)+line('accept', 2)
        self.assertEqual([r['option'] for r in dialogue_buttons(text, (960,540))], ['accept','close'])
        self.assertEqual(dialogue_buttons(text, (960,540), 'old'), [])
        self.assertEqual(dialogue_buttons(text, (960,540), 'accept')[0]['x'], 300)
        for x in (-1, 960):
            with self.assertRaises(ValueError):
                dialogue_buttons(line('accept', 3, x), (960,540))
        self.assertEqual(dialogue_buttons('some old status', (960,540)), [])

    def test_no_nan_boolean_or_partial_coordinates(self):
        for value in ([1,2], [1,True,3], [1,float('nan'),3], [1,float('inf'),3]):
            with self.assertRaises(ValueError):
                finite_vector(value)
        self.assertEqual(finite_vector([1,-61,3]), [1,-61,3])

    def test_missing_and_wrong_readback_fields_fail(self):
        require_subset({'personal': {'stage':'x','evidence':['a']}}, {'personal': {'stage':'x'}})
        for expected in ({'missing':True}, {'personal':{'stage':'y'}}):
            with self.assertRaises(AssertionError):
                require_subset({'personal':{'stage':'x'}}, expected)

    def test_rewards_count_actual_items_and_stacks(self):
        inv = [{'id':'myvillage:reward','count':2}, {'id':'minecraft:dirt','count':64},
               {'id':'myvillage:reward','count':3}]
        self.assertEqual(reward_counts(inv,[{'item':'myvillage:reward','count':5}]), {'myvillage:reward':5})


class StoryRouteTests(unittest.TestCase):
    def test_resume_uses_remaining_steps_then_normal_tail_without_new_setup(self):
        with tempfile.TemporaryDirectory() as folder:
            e = StoryEvidence(Path(folder)/'resume-dispatch')
            calls = []
            try:
                for name in ('preflight', 'resume', 'second_player', 'lifecycle_probes', 'stop',
                             'package_world'):
                    setattr(e, name, lambda *args, _name=name: calls.append((_name, args)))
                e.rc = lambda command: calls.append(('rc', (command,)))
                e.check = lambda *args: None
                e.setup = Mock(side_effect=AssertionError('must not deploy a new world'))
                e.start = Mock(side_effect=AssertionError('resume owns reuse=True startup'))
                e.run(Path('fixture.json'))
                self.assertEqual([name for name, _ in calls], ['preflight', 'resume', 'second_player',
                    'lifecycle_probes', 'rc', 'stop', 'package_world'])
                self.assertEqual(calls[1][1], (Path('fixture.json'),))
                e.setup.assert_not_called()
                e.start.assert_not_called()
            finally:
                e.position_stream.close()
                e.action_stream.close()

    def test_restore_checks_old_state_before_ui_and_preserves_failed_provenance(self):
        with tempfile.TemporaryDirectory() as folder:
            e = StoryEvidence(Path(folder)/'resume-state')
            calls = []
            people = {name: i for i, name in enumerate(('guide','pharmacist','zhou_he','warden'))}
            state = dict(player='uuid', chapter='chapter', stage='report', revision=13,
                accepted=True, completed=False, site='site', dimension='minecraft:overworld',
                evidence=['spring_core'], receipts=[], reward='pending',
                shared={'returned':True, 'regulated':True},
                npc={name:{'personId':i,'position':{'x':i,'y':2,'z':3}} for name,i in people.items()})
            prior = {'status':'fail', 'failures':['old P conflict'], 'termination':'confirmed',
                     'completed':['production_deploy','fresh_entry_world','rescue_restart'],
                     'checks':[{'check':'old-proof','pass':True}], 'routes':[{'route':'old-path'}],
                     'screenshots':[{'path':'normal-tracker-compact.png'}]}
            prior_dir = e.out/'prior-run'
            prior_dir.mkdir()
            (prior_dir/'normal-tracker-compact.png').write_bytes(b'old-frame')
            (prior_dir/'fresh-entry-world.zip').write_bytes(b'old-fresh-world')
            (prior_dir/'evidence.json').write_text(json.dumps(prior))
            checkpoint = {'prior':prior,'state':state,'people':people,'prefix_length':13,
                          'position':[1,2,3], 'manifest':{'schema':1},
                          'baseline_sha256':'a'*64,'remaining_limit':1024}
            e.contract = {'primary':[]}
            e.chapter = {'roles':list(people)}
            e.start = lambda reuse: (calls.append(('start', reuse, e.continuous)),
                                    setattr(e, 's', SimpleNamespace(username='CaptureDev')))
            e.status = lambda label: state
            e.player_pos = lambda: [1.1,2,3]
            e.assert_shared_identity = lambda *args: None
            e.expect_client_view = lambda *args: None
            e.rc = lambda command: calls.append(('rc',command))
            e.ui_smoke = lambda label: calls.append(('smoke',label))
            e.ui_evidence = lambda: calls.append(('ui',))
            e.primary_chapter = lambda length: calls.append(('remaining',length))
            try:
                with patch('tools.qianzhen.story_evidence.ROOT',Path('/workspace/repo')), \
                     patch('tools.qianzhen.story_evidence.story_resume.prepare',return_value=checkpoint), \
                     patch('tools.qianzhen.story_evidence.story_resume.restore_world',
                           side_effect=lambda *args: calls.append(('restore',))):
                    e.resume(Path('fixture.json'))
                self.assertEqual(calls, [('restore',),('start',True,True),
                    ('rc','defaultgamemode survival'),('rc','gamemode survival CaptureDev'),
                    ('smoke','restored'),('ui',),('remaining',13)])
                self.assertEqual(e.report['completed'], prior['completed'])
                self.assertEqual(e.report['prior_run']['status'],'fail')
                self.assertEqual(e.report['failures'],[])
                self.assertEqual(json.loads((prior_dir/'evidence.json').read_text()),prior)
                self.assertEqual((e.out/'prior-normal-tracker-compact.png').read_bytes(),b'old-frame')
                self.assertEqual(e.report['screenshots'][0]['source_run'],
                                 '20261023T155335Z-0cb1b9e3')
            finally:
                e.position_stream.close()
                e.action_stream.close()

    def test_primary_resume_only_claims_and_ends_with_reward_restart(self):
        with tempfile.TemporaryDirectory() as folder:
            e = StoryEvidence(Path(folder)/'resume-primary')
            calls = []
            try:
                e.preflight()
                e.report['source_commit'] = 'new-fixed-source'
                e.continuous = True
                e.reward_step = lambda row, full_bag: calls.append(('claim', row['action']['id'], full_bag))
                e.restart_checkpoint = lambda label: calls.append(('restart', label, e.continuous))
                e.repeat_reward = lambda row, before: calls.append(('duplicate', row['action']['id'], e.continuous))
                e.execute_step = lambda row: calls.append(('physical', row['action']['id'], e.continuous))
                e.status = lambda label: {'completed': True, 'reward': 'claimed'}
                e.primary_chapter(13)
                self.assertEqual(calls, [('claim', 'claim_reward', True),
                    ('restart', 'reward-restart', True), ('duplicate', 'claim_reward', True),
                    ('physical', 'recognize_contract', True)])
                self.assertFalse(e.continuous)
                self.assertEqual(e.report['continuous_chapter_provenance']['prior_status'], 'fail')
            finally:
                e.position_stream.close()
                e.action_stream.close()

    def test_early_smoke_uses_three_y_modes_then_o_and_restores_compact(self):
        with tempfile.TemporaryDirectory() as folder:
            e = StoryEvidence(Path(folder)/'ui-smoke')
            log = Path(folder)/'client.log'
            log.write_text('')
            keys, shots, window_keys = [], [], []
            game = SimpleNamespace(key=keys.append, client_log=log, pointer_grabbed=lambda: True,
                                   xdo=lambda *args:window_keys.append(args))
            e.s = SimpleNamespace(game=game, username='CaptureDev')
            e.close_screen = lambda: None
            e.status = lambda label: {'shared': {'returned': True}, 'npc': {}}
            e.personal = lambda state: {'revision': 13}
            e.poll = lambda *args: None  # input dispatch only; native mode logs remain a runtime gate
            e.capture = shots.append
            try:
                e.ui_smoke('restored')
                self.assertEqual(keys, ['y', 'y', 'y', 'o'])
                self.assertEqual(window_keys, [('key','o')])
                self.assertEqual(len(shots), 4)
                self.assertEqual(e.report['ui_smoke'][0]['final_mode'], 'COMPACT')
                self.assertEqual(e.report['completed'], [])
            finally:
                e.position_stream.close()
                e.action_stream.close()

    def test_end_fixture_checks_loading_and_floor_then_removes_tickets_on_failure(self):
        with tempfile.TemporaryDirectory() as folder:
            e = StoryEvidence(Path(folder)/'end')
            calls = []
            e.rc = lambda command: calls.append(command) or 'Test passed'
            e.poll = lambda getter, predicate, timeout, label: getter()
            e.check = lambda name, passed, detail: self.assertTrue(passed)
            try:
                with self.assertRaisesRegex(RuntimeError, 'probe failed'):
                    with e.end_platform():
                        self.assertIn('run fill', calls[5])
                        self.assertEqual(calls[6].count('if block'),25)
                        raise RuntimeError('probe failed')
                self.assertEqual(sum('if loaded' in c for c in calls),4)
                self.assertTrue(calls[-1].endswith('forceload remove -2 -2 2 2'))
            finally:
                e.position_stream.close()
                e.action_stream.close()

    def test_npc_collision_detour_and_unreachable_fail_without_tp(self):
        a,b,c,d = (0,1,0),(1,1,0),(0,1,1),(1,1,1)
        graph = {a:[b,c],b:[a,d],c:[a,d],d:[b,c]}
        self.assertEqual(cardinal_path(graph,a,d,[b]),[a,c,d])
        with self.assertRaisesRegex(ValueError, 'No continuous'):
            cardinal_path(graph,a,d,[b,c])
        with self.assertRaisesRegex(ValueError, 'absent/blocked'):
            cardinal_path(graph,a,d,[d])

    def test_handoff_order_is_checked_against_real_definition(self):
        chapter = {'stages':[
            {'id':'new','auto_next':'','auto_requires':[]},
            {'id':'sample','auto_next':'complete','auto_requires':['water','crop']},
            {'id':'complete','auto_next':'','auto_requires':[]}], 'actions':[
            {'id':'accept','kind':'talk','stages':['new'],'requires':[],'evidence':'','next':'sample'},
            {'id':'water','kind':'investigate','stages':['sample'],'requires':[],'evidence':'water','next':'sample'},
            {'id':'crop','kind':'investigate','stages':['sample'],'requires':['water'],'evidence':'crop','next':'sample'}]}
        route = compile_route(chapter,['accept','water','crop'])
        self.assertEqual(route[-1]['after'],'complete')
        for ids in (['accept','crop','water'],['accept','imagined_option'],['accept','water']):
            with self.subTest(ids=ids), self.assertRaises(ValueError):
                compile_route(chapter,ids)

    def test_side_door_always_has_a_reached_close_approach_before_use(self):
        path = [(x,1,0) for x in range(12)]
        blocks = {(10,1,0):'minecraft:spruce_door[facing=south,half=lower,hinge=left,open=false]'}
        ends = route_ends(path,blocks)
        self.assertIn((9,1,0),ends)
        self.assertIn((10,1,0),ends)
        self.assertLess(ends.index((9,1,0)),ends.index((10,1,0)))

    def test_physical_click_uses_the_runtime_rectangle_center(self):
        with tempfile.TemporaryDirectory() as folder:
            e = StoryEvidence(Path(folder)/'click')
            commands = []
            game = SimpleNamespace(xdo=lambda *args:commands.append(args))
            e.s = SimpleNamespace(game=game,username='TestPlayer')
            try:
                with patch('tools.qianzhen.story_evidence.time.sleep'):
                    e.click_dialogue({'x':100,'y':200,'w':240,'h':40,'option':'accept','nonce':8},
                                    'guide','test_center')
                self.assertEqual(commands,[('mousemove','--sync','220','220'),('click','1')])
            finally:
                e.position_stream.close()
                e.action_stream.close()

    def test_local_start_and_story_write_are_refused_before_any_launch(self):
        with tempfile.TemporaryDirectory() as folder:
            e = StoryEvidence(Path(folder)/'fresh')
            try:
                with patch('tools.qianzhen.story_evidence.ROOT',Path('/not-modal')):
                    with self.assertRaisesRegex(RuntimeError, 'Modal disposable'):
                        e.start()
                e.continuous = True
                for command in ('tp Player 0 1 2','execute in minecraft:overworld run tp Player 0 1 2',
                                'myvillage story complete Player'):
                    with self.assertRaises(AssertionError):
                        e.rc(command)
                self.assertEqual(e.report['completed'], [])
                self.assertEqual(e.report['unrun'], e.report['selected'])
            finally:
                e.position_stream.close()
                e.action_stream.close()

    def test_collector_does_not_import_grid_setup_or_set_health(self):
        source = (Path(__file__).resolve().parents[1]/'qianzhen/story_evidence.py').read_text()
        tree = ast.parse(source)
        calls = [n for n in ast.walk(tree) if isinstance(n, ast.Call)]
        self.assertFalse(any(isinstance(n.func, ast.Attribute) and n.func.attr == 'setup'
                             and isinstance(n.func.value, ast.Call) and isinstance(n.func.value.func, ast.Name)
                             and n.func.value.func.id == 'super'
                             for n in calls))
        for forbidden in ('data merge entity', 'generic.max_health base set', 'DevBridge', 'force-complete'):
            self.assertNotIn(forbidden, source)


class ActualStoryPlanTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        # The fixed-source Modal checkout does not ship ignored reports. Test
        # exactly the bundled snapshot path that will be used in that checkout.
        cls.e = StoryEvidence(Path(cls.tmp.name)/'plan', HANDOFF_SNAPSHOT)
        cls.e.preflight()

    @classmethod
    def tearDownClass(cls):
        cls.e.position_stream.close()
        cls.e.action_stream.close()
        cls.tmp.cleanup()

    def test_actual_routes_complete_independent_branches_and_late_home(self):
        routes = self.e.report['planned_routes']
        for rows in routes.values():
            self.assertEqual(rows[0]['stage_before'], 'new')
            self.assertEqual(rows[-1]['stage_after'], 'complete')
            self.assertTrue(all(row['cells'] >= 1 for row in rows))
        self.assertIn('sample_examination', {row['target'] for row in routes['primary']})
        self.assertIn('medicine_chest', {row['target'] for row in routes['secondary']})
        self.assertIn('zhou_he', {row['target'] for row in routes['primary']})
        self.assertIn('zhou_he_home', {row['target'] for row in routes['secondary']})

    def test_offline_plan_never_claims_runtime_or_issues_commands(self):
        self.assertEqual(self.e.report['status'], 'ready_not_run')
        self.assertEqual(self.e.report['completed'], [])
        self.assertEqual(self.e.report['unrun'], self.e.report['selected'])
        self.assertEqual(self.e.report['commands'], [])
        self.assertEqual(self.e.sessions, 0)
        self.assertIsNone(self.e.s)

    def test_production_layout_uses_manifest_origins(self):
        manifest = json.loads((Path(__file__).resolve().parents[2]/'blueprints/qianzhen/manifest.json').read_text())
        actual = {row['id']:row['origin'] for row in self.e.layout['sites']}
        self.assertEqual(actual,{row['id']:row['origin'] for row in manifest['locations']})
        self.assertLess(self.e.positions['warden']['pos'][2], 0)


if __name__ == '__main__':
    unittest.main()
