"""Offline collector checks: strict logs, physical routes and safety boundaries."""
import ast
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from tools.qianzhen.story_evidence_helpers import (
    cardinal_path, compile_route, dialogue_buttons, finite_vector,
    require_subset, reward_counts, status_object,
)
from tools.qianzhen.story_evidence import StoryEvidence, HANDOFF_SNAPSHOT, route_ends


class StoryParserTests(unittest.TestCase):
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
