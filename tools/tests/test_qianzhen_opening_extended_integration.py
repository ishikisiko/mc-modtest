"""受控dispatch/身份/录像/失败归档及真实A/C只读适配；不启动游戏。"""
from contextlib import contextmanager, ExitStack
from dataclasses import asdict
import ast
import copy
import json
from pathlib import Path
import tempfile
import time
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
import zipfile

from tools import qianzhen_opening_run as run
from tools.qianzhen_opening_evidence import digest
from tools.qianzhen_opening_extended_integration import finalize_levels, start_recording, supervise_nether
from tools.qianzhen_opening_extended_run import validate_execution_manifest, execute_attachment
from tools.qianzhen_opening_extended_saved import extract_saved_fixture, materialize_saved_resources, SavedSceneFlow, write_awakened_manifest
from tools.qianzhen_opening_extended_saved_driver import install_frozen_original

ROOT = Path(__file__).resolve().parents[2]


class IntegrationTests(unittest.TestCase):
    def args(self, names, **extra):
        return SimpleNamespace(scenarios=names, roof_repair_fixture=None, saved_fixture=None,
                               build_awakened_fixture=False, extended_fixtures=None, allow_nether=False,
                               **extra)

    def test_cli_dispatch_has_executable_distinct_fresh_saved_builder_routes(self):
        args = run.parser().parse_args(['--commit', 'a'*40, '--run-id', 'test-integration', '--pal-sha256', 'b'*64,
                                       '--scenarios', 'work_reward', 'restart_two_players'])
        self.assertEqual('extended', run.dispatch_mode(args, args.scenarios))
        args.scenarios = ['existing_awakened']
        with self.assertRaises(ValueError):
            run.dispatch_mode(args, args.scenarios)
        args.build_awakened_fixture = True
        self.assertEqual('builder', run.dispatch_mode(args, args.scenarios))
        args.build_awakened_fixture, args.saved_fixture = False, '/remote/real.json'
        self.assertEqual('saved', run.dispatch_mode(args, args.scenarios))
        with self.assertRaises(ValueError):
            run.dispatch_mode(args, ['work_reward', 'existing_awakened'])

    def test_missing_actual_fixtures_refuse_before_server_build(self):
        for name in ('optional_personal_story', 'marker_matrix'):
            args = self.args([name])
            with self.assertRaises(ValueError):
                run.selection_preflight(args, [name])
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'fixtures.json'
            path.write_text(json.dumps({'home': {'real': True}, 'native_fixture': {'combat_uuid': 'actual'}}))
            args = self.args(['optional_personal_story'])
            args.extended_fixtures = str(path)
            self.assertEqual('extended', run.selection_preflight(args, args.scenarios))
            path.write_text(json.dumps({'source_commit':'override', 'home':{}}))
            with self.assertRaisesRegex(ValueError, '身份'):
                run.selection_preflight(args, args.scenarios)

    def test_exact_allowlist_includes_native_helper_but_no_production_or_globs(self):
        allowed = run.BUILD_REUSE_COLLECTORS
        self.assertIn('tools/qianzhen_opening_nbt.py', allowed)
        self.assertIn('tools/tests/test_qianzhen_opening_nbt.py', allowed)
        self.assertTrue(all('*' not in p for p in allowed))
        for name in ['src/main/java/Test.java', 'src/main/resources/data/myvillage/story/qianzhen_opening.json',
                     'build.gradle', 'gradle.properties', 'gradle/wrapper/gradle-wrapper.properties',
                     'tools/not_a_collector.py', 'tools/qianzhen/storygen/pipeline.py']:
            self.assertNotIn(name, allowed)
            old = f'100644 blob a\t{name}\n100644 blob x\ttools/qianzhen_opening_nbt.py\n'
            new = f'100644 blob b\t{name}\n100644 blob y\ttools/qianzhen_opening_nbt.py\n'
            with patch.object(run.subprocess, 'check_output', side_effect=[old, new]):
                self.assertNotEqual(run.fixed_build_inputs(ROOT, 'a'*40), run.fixed_build_inputs(ROOT, 'b'*40), name)
        old = '100644 blob a\tbuild.gradle\n100644 blob x\ttools/qianzhen_opening_nbt.py\n'
        new = '100644 blob a\tbuild.gradle\n100644 blob y\ttools/qianzhen_opening_nbt.py\n'
        with patch.object(run.subprocess, 'check_output', side_effect=[old,new]):
            self.assertEqual(run.fixed_build_inputs(ROOT,'a'*40), run.fixed_build_inputs(ROOT,'b'*40))

    def test_recording_requires_shared_actual_encoded_frame_and_retains_failure_png(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'guest-1.mp4'
            process = Mock()
            process.poll.return_value = None
            game = Mock(display=':9', size=(960,540))
            game.grab_raw.return_value = 'raw-png'
            with patch('tools.qianzhen_opening_extended_integration.subprocess.Popen', return_value=process), \
                 patch.object(run, 'wait_recorded_frame', side_effect=TimeoutError('frame=0')) as frame:
                with self.assertRaisesRegex(TimeoutError, 'frame=0'):
                    start_recording(game, path, time.monotonic()+50)
            frame.assert_called_once_with(process,path.with_suffix('.progress.log'))
            process.kill.assert_called_once()
            game.save_png.assert_called_once_with('raw-png',path.with_suffix('.startup-failure.png'))

    def test_nether_option_changes_only_single_key_before_server_start(self):
        from tools.combat_capture import session as cs
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            text = 'allow-nether=false\nlevel-seed=20261024\npvp=false\n'
            def write(supervisor):
                (base/'server.properties').write_text(text)
            def actual(supervisor):
                supervisor.write_server_config()
                self.assertEqual(text.replace('allow-nether=false','allow-nether=true'), (base/'server.properties').read_text())
                self.assertTrue(supervisor.state['allow_nether'])
                return 0
            with patch.object(cs,'SERVER_DIR',base), patch.object(cs.Supervisor,'write_server_config',write), \
                 patch.object(cs.Supervisor,'run',actual), patch.object(cs,'write_state'), \
                 patch('tools.qianzhen.evidence_candidate.assert_cloud'):
                self.assertEqual(0,supervise_nether(json.dumps({'session_id':'nether-integration'})))

    def test_levels_do_not_hide_attempted_failure_as_unrun_or_accept_pixels(self):
        result = finalize_levels({'selected':['work_reward','restart_two_players','marker_matrix'],
                                  'stages':[{'id':'natural_spawn','status':'captured'},
                                            {'id':'work_reward','status':'captured'},
                                            {'id':'restart_two_players','status':'failed'}]})
        self.assertEqual(['work_reward'],result['captured'])
        self.assertEqual(['restart_two_players'],result['failed'])
        self.assertEqual(['marker_matrix'],result['unrun'])
        self.assertEqual([],result['completed'])
        self.assertEqual(['work_reward'],result['pending_acceptance'])

    def test_bootstrap_forwards_every_explicit_saved_and_extended_option(self):
        config = dict(run_id='integration-options',commit='a'*40,source_repo='/content/source',
                      dependency_root='/content/deps',pal_sha256='b'*64,timeout=600,scenarios=['existing_awakened'],
                      saved_fixture='/content/original.json', extended_fixtures='/content/actual.json',
                      maturity_timeout=950, allow_nether=True,build_awakened_fixture=False)
        code = run.remote_bootstrap(config)
        tree=ast.parse(code)
        payload=ast.literal_eval(next(n.args[0] for n in ast.walk(tree) if isinstance(n,ast.Call) and isinstance(n.func,ast.Attribute) and n.func.attr=='write_text'))
        compile(payload,'<bootstrap>','exec')
        self.assertIn("('saved_fixture','extended_fixtures','maturity_timeout')",payload)
        self.assertIn("('build_awakened_fixture','allow_nether')",payload)

    def test_outer_packaging_uses_inner_artifact_root_and_never_flushes_again(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)/'integration-owned/repo'
            root.mkdir(parents=True)
            out=root.parent/'evidence'
            inner=out/'extended-evidence'
            inner.mkdir(parents=True)
            trace={'selected':['work_reward','restart_two_players'],'stages':[{'id':'work_reward','status':'captured'}],
                   'failed':['restart_two_players'],'status':'failed','termination':{'status':'terminated'}}
            run.write_json(inner/'trace.json',trace)
            with patch.object(run,'single_flush') as flush, patch.object(run,'stop_owned') as stop:
                run.finish_handoff(root,out,inner,{},RuntimeError('retained'),root.parent.name)
            flush.assert_not_called()
            stop.assert_not_called()
            done=json.loads((root.parent/'done.json').read_text())
            self.assertEqual('extended-evidence',done['artifact_root'])
            self.assertEqual([],done['completed'])
            self.assertEqual([],done['unrun'])
            with zipfile.ZipFile(root.parent/'evidence.zip') as zipped:
                self.assertIn('extended-evidence/trace.json',zipped.namelist())

    def test_worker_fresh_handoff_happens_before_original_collector_and_owns_cleanup(self):
        from tools.combat_capture import session as cs
        from tools.qianzhen import evidence_candidate as ec
        with tempfile.TemporaryDirectory() as folder, ExitStack() as stack:
            root=Path(folder)/'integration-worker/repo'
            root.mkdir(parents=True)
            (root.parent/'bootstrap-dependencies.json').write_text('{}')
            (root/run.PAL).write_bytes(b'pal')
            (root/run.CATALOG).parent.mkdir()
            (root/run.CATALOG).write_bytes(b'catalog')
            wrapper=root/'gradle/wrapper/gradle-wrapper.properties'
            wrapper.parent.mkdir(parents=True)
            wrapper.write_text('pinned')
            jar=root/'build/libs/mod.jar'
            jar.parent.mkdir(parents=True)
            jar.write_bytes(b'jar')
            snap={'commit':'a'*40,'tracked_status':'','untracked_runtime':[],'fingerprint':'b'*64}
            args=run.parser().parse_args(['--worker','--commit','a'*40,'--run-id',root.parent.name,
                                         '--pal-sha256',digest(root/run.PAL),'--scenarios','work_reward','restart_two_players'])
            candidate=Mock(identity={'scene_version':'scene','resource_sha256':{'resource':'hash'}})
            candidate.prepare_pack.return_value={}
            state_file=root/'run-combat-capture/session.json'
            def start(cfg,**kw):
                world=root/'run-acceptance/combat_capture_world'
                world.mkdir(parents=True)
                (world/'level.dat').write_bytes(b'fresh')
                state_file.parent.mkdir(parents=True)
                state={'session_id':args.run_id,'username':cfg.username,'display':':9','client_log':'fake'}
                state_file.write_text(json.dumps(state))
                return state
            def execute(spec, actual_root, out):
                self.assertEqual(root,actual_root)
                self.assertEqual('OpeningA',spec['session_config']['username'])
                self.assertEqual(digest(state_file),spec['session_state_sha256'])
                self.assertEqual(str(cs.STATE_FILE),spec['session_state'])
                self.assertFalse(spec['session_config']['reuse_world'])
                out.mkdir()
                trace={'selected':args.scenarios,'stages':[], 'status':'captured_not_accepted',
                       'termination':{'status':'terminated'}}
                run.write_json(out/'trace.json',trace)
                return trace
            @contextmanager
            def owned(*a,**kw):
                yield None
            patches=[patch.object(run,'ROOT',root),patch.object(run,'source_snapshot',return_value=snap),
                     patch.object(ec,'assert_cloud'),patch.object(ec,'Candidate',return_value=candidate),
                     patch.object(ec,'runtime_request'),patch('tools.qianzhen.storygen.pipeline.generate'),
                     patch.object(run,'verify_dependencies',return_value={}),patch.object(run,'verify_authored_jar',return_value={}),
                     patch.object(run,'verify_owned_session'),patch.object(run,'owned_command',owned),
                     patch.object(run.subprocess,'check_output',return_value='openjdk version "21.0.1"'),
                     patch('tools.combat_capture.procs.missing_programs',return_value=[]),
                     patch('tools.combat_capture.procs.acquire_lock',return_value=9),patch.object(run.os,'close'),
                     patch.object(cs,'STATE_FILE',state_file),patch('tools.qianzhen_opening_extended_integration.start_session',side_effect=start),
                     patch('tools.qianzhen_opening_extended_run.execute_attachment',side_effect=execute) ,
                     patch('tools.qianzhen_opening_collect.OpeningCollector') ,patch.object(run,'single_flush'),patch.object(run,'stop_owned')]
            handles=[stack.enter_context(p) for p in patches]
            # Original candidate copy is a real directory even with generation mocked.
            (root.parent/'candidate').mkdir()
            self.assertEqual(0,run.run_worker(args))
            handles[-3].assert_not_called()
            handles[-2].assert_not_called()
            handles[-1].assert_not_called()
            self.assertEqual('extended-evidence',json.loads((root.parent/'done.json').read_text())['artifact_root'])

    def test_real_A_C_original_zips_resources_and_saved_geometry_remain_untouched(self):
        for label in ('authored-A','seeded-C'):
            manifest=ROOT/('reports/qianzhen-opening/extended-saved-offline/legacy-'+label+'-manifest.json')
            spec=json.loads(manifest.read_text())
            before=digest(Path(spec['archive']))
            with self.subTest(label=label), tempfile.TemporaryDirectory() as folder:
                base=Path(folder)
                copied=extract_saved_fixture(spec,base/'world')
                hashes=materialize_saved_resources(spec,base/'world',base/'resources')
                installed=install_frozen_original(spec,base/'world',base/'resources',hashes)
                options=base/'options.txt'
                options.write_text('lang:zh_cn\n')
                flow=SavedSceneFlow(SimpleNamespace(),ROOT,base/'world',time.monotonic()+120,base/'resources',options_path=options)
                self.assertGreater(len(flow.graph),10000)
                self.assertEqual(spec['provenance']['expected_scene_version'],flow.saved_version)
                self.assertEqual(spec['resources']['files'],installed['resource_sha256'])
                self.assertTrue(copied['original_unchanged'])
            self.assertEqual(before,digest(Path(spec['archive'])))


class FailureLifecycleTests(unittest.TestCase):
    def test_flow_init_archive_and_audit_failures_still_keep_trace_png_world_cleanup(self):
        from tools.combat_capture import session as cs
        with tempfile.TemporaryDirectory() as folder, ExitStack() as stack:
            root=Path(folder)/'integration-failure/repo'
            root.mkdir(parents=True)
            for name in (run.PAL,run.CATALOG,'gradle/wrapper/gradle-wrapper.properties','tools/qianzhen_opening_fixture.json','build/libs/mod.jar'):
                target=root/name
                target.parent.mkdir(parents=True,exist_ok=True)
                target.write_text('pinned')
            world=root/'run-acceptance/combat_capture_world'
            world.mkdir(parents=True)
            (world/'level.dat').write_bytes(b'world')
            request=root.parent/'request.json'
            request.write_text('{}')
            client=root/'client.log'
            client.write_text('actual log')
            spec={'schema':'qianzhen-opening-extended-attachment-v1','run_id':root.parent.name,
                  'scenarios':['work_reward','restart_two_players'],'source_commit':'a'*40,'source_fingerprint':'b'*64,
                  'request':str(request),'request_sha256':digest(request),'candidate':str(root.parent/'candidate'),
                  'jar':str(root/'build/libs/mod.jar'),'jar_sha256':digest(root/'build/libs/mod.jar'),
                  'pal_sha256':digest(root/run.PAL),'username':'A','timeout':600,
                  'session_config':asdict(cs.SessionConfig(session_id=root.parent.name,username='A',natural_terrain=True,world_seed=20261024))}
            state={'username':'A','session_id':root.parent.name,'display':':9','client_log':str(client)}
            game=Mock(display=':9',client_log=client,size=(960,540))
            game.save_png.side_effect=lambda raw,path:path.write_bytes(b'png')
            candidate=Mock(identity={'scene_version':'frozen','resource_sha256':{str(i):'a'*64 for i in range(7)}})
            out=root.parent/'extended-evidence'
            process=Mock()
            process.poll.return_value=0
            def recording(game,path,deadline):
                path.write_bytes(b'video')
                return process,path.with_suffix('.log').open('w'),path
            patches=[patch('tools.qianzhen_opening_collect.validate_attachment',return_value=state),
                     patch.object(run,'verify_owned_session'),patch.object(run,'verify_dependencies',return_value={}),
                     patch('tools.qianzhen.evidence_candidate.Candidate',return_value=candidate),
                     patch('tools.combat_capture.xgame.Game',return_value=game),
                     patch('tools.qianzhen_opening_extended_integration.start_recording',side_effect=recording),
                     patch('tools.combat_capture.capture.stop_recording',return_value=0),
                     patch('tools.qianzhen_opening_extended_run.OpeningFlow',side_effect=ValueError('flow init original failure')),
                     patch.object(run,'single_flush',return_value={'requests':1,'status':'confirmed'}),
                     patch.object(run,'stop_owned',return_value={'status':'terminated'}),
                     patch.object(run,'archive_world',side_effect=OSError('archive disk failure')),
                     patch('tools.qianzhen_opening_extended_run.audit',side_effect=RuntimeError('audit original failure'))]
            handles=[stack.enter_context(p) for p in patches]
            with self.assertRaisesRegex(ValueError,'flow init original failure'):
                execute_attachment(spec,root,out)
            handles[8].assert_called_once()
            handles[9].assert_called_once()
            trace=json.loads((out/'trace.json').read_text())
            self.assertEqual('terminated',trace['termination']['status'])
            self.assertIn('archive disk failure',trace['archive_error'])
            self.assertIn('audit original failure',trace['structural_audit']['errors'][0])
            self.assertTrue((world/'level.dat').exists())
            self.assertTrue(list((out/'raw').glob('*failure*.png')))
            self.assertTrue((out/'audit.json').is_file())

    def test_saved_restart_updates_existing_read_closure_without_replacing_player_identity(self):
        from tools.qianzhen_opening_extended_saved import SavedCheckpoint
        from tools.combat_capture import session as cs
        with tempfile.TemporaryDirectory() as folder:
            base=Path(folder)
            root=base/'restart-integration/repo'
            root.mkdir(parents=True)
            out=root.parent/'evidence'
            out.mkdir()
            request=root.parent/'saved-request.json'
            request.write_text(json.dumps({'commit':'a'*40,'source_fingerprint':'b'*64}))
            log=root/'client.log'
            log.write_text('original client log')
            state={'session_id':root.parent.name,'procs':{'server':{'pid':1,'start':10}},'rcon_port':100}
            state_identity=id(state)
            new={'session_id':root.parent.name,'procs':{'server':{'pid':2,'start':20}},'rcon_port':200,
                 'display':':10','client_log':str(log)}
            collector=SimpleNamespace(username='OriginalPlayer',game=SimpleNamespace(client_log=log),
                                      rotation=Mock(),emit=Mock(),read_command=lambda command,t:state['rcon_port'])
            flow=SimpleNamespace(root=root,world=root/'world',deadline=time.monotonic()+100,c=collector,
                                 state=lambda:{'player':'original-uuid','accepted':True})
            flow.poll=lambda function,predicate,*args:function()
            archive={'files':{name:'sha' for name in ('playerdata/original-uuid.dat','data/myvillage_world_sim.dat','data/myvillage_qianzhen_scene.dat')}}
            checkpoint=SavedCheckpoint(state,cs.SessionConfig(session_id=root.parent.name,username='OriginalPlayer',reuse_world=True),
                                       out,root.parent.name,request,record_stop=Mock(),record_start=Mock())
            mapped=Mock()
            with patch.object(run,'verify_owned_session'),patch.object(run,'single_flush',return_value={'requests':1,'status':'confirmed'}) as flush, \
                 patch.object(run,'stop_owned',return_value={'status':'terminated'}),patch.object(run,'archive_world',return_value=archive), \
                 patch('tools.qianzhen_opening_evidence.source_snapshot',return_value={'commit':'a'*40,'fingerprint':'b'*64,'tracked_status':'','untracked_runtime':[]}), \
                 patch.object(cs,'start',return_value=new),patch('tools.combat_capture.procs.is_alive',return_value=False), \
                 patch('tools.combat_capture.xgame.Game'),patch('tools.qianzhen_opening_evidence.MappedOpeningInput',return_value=mapped), \
                 patch.dict('os.environ',{'MYVILLAGE_CLOUD_PROVIDER':'Colab'}),patch.object(Path,'is_dir',return_value=True):
                result=checkpoint(flow)
                with self.assertRaisesRegex(RuntimeError,'禁止重试'):
                    checkpoint(flow)
            self.assertEqual(state_identity,id(state))
            self.assertEqual(200,collector.read_command('list',5))
            self.assertEqual('OriginalPlayer',collector.username)
            self.assertEqual('original-uuid',result['after']['player'])
            flush.assert_called_once()

    def test_bound_fixture_rejects_foreign_request_before_inputs(self):
        from tools.qianzhen_opening_extended_fixtures import validate_fixture_bundle
        binding={'run_id':'bound-integration','source_commit':'a'*40,'source_fingerprint':'b'*64,'request_sha256':'c'*64}
        home={'schema':'qianzhen-personal-home-v1','binding':binding.copy(),'provenance':{'actual':True},
              'outbound':[[0,1,0]],'return':[[0,1,0]],'combat_approach':[[0,1,0]],
              'support':[0,0,0],'placed':[0,1,0],'support_state':'minecraft:stone'}
        spec={**binding,'home':home,'native_fixture':{'binding':binding.copy(),'provenance':{'actual':True},
                                                  'combat_uuid':'00000000-0000-0000-0000-000000000001'}}
        validate_fixture_bundle(spec,['optional_personal_story'])
        home['binding']['request_sha256']='d'*64
        with self.assertRaisesRegex(ValueError,'绑定'):
            validate_fixture_bundle(spec,['optional_personal_story'])

    def test_B_manifest_requires_real_checkpoint_zip_and_never_claims_historical_upgrade(self):
        with tempfile.TemporaryDirectory() as folder:
            out=Path(folder)
            archive=out/'saved-checkpoint/world.zip'
            archive.parent.mkdir()
            archive.write_bytes(b'actual stopped zip fixture for interface')
            result={'fixture_archive':{'path':'world.zip','sha256':digest(archive)},
                    'checkpoint':{'before':{'player':'uuid-original'}}}
            flow=SimpleNamespace(c=SimpleNamespace(username='OpeningA'),candidate=SimpleNamespace(identity={'resource_sha256':{'a':'b'}}))
            attachment={'source_commit':'a'*40,'run_id':'builder-integration','request_sha256':'b'*64}
            manifest=write_awakened_manifest(result,flow,out,attachment)
            spec=json.loads((out/manifest['path']).read_text())
            self.assertEqual('current_version_generated',spec['provenance']['kind'])
            self.assertFalse(spec['provenance']['historical_upgrade_proven'])
            self.assertEqual('uuid-original',spec['player_uuid'])
            self.assertEqual(digest(archive),spec['archive_sha256'])
            archive.write_bytes(b'tampered')
            with self.assertRaises(ValueError):
                write_awakened_manifest(result,flow,out,attachment)


if __name__=='__main__':
    unittest.main()
