"""候选身份和 startup pack 的离线回归；绝不启动游戏或云端。"""
from __future__ import annotations

import copy
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

from tools.qianzhen.evidence_candidate import Candidate, ROOT, assert_cloud, fixed_git, main, runtime_request, sha
from tools.qianzhen.storygen import pipeline
from tools.qianzhen.storygen.core import derive_seed
from tools.qianzhen.storygen.pipeline import digest, generate
from tools.qianzhen.build import source_identity
from tools.qianzhen.game_evidence import QianzhenEvidence, nbt
from tools.qianzhen.story_evidence import StoryEvidence
from tools.combat_capture import datapack, session


class CandidateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.folder = Path(cls.tmp.name)
        cls.bundle = cls.folder/'seeded'
        cls.report = generate(cls.bundle, 20261024)
        cls.candidate = Candidate(cls.bundle)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def test_default_authored_source_and_template_paths_stay_compatible(self):
        candidate = Candidate()
        self.assertEqual(candidate.identity, {'mode':'authored'})
        self.assertEqual(candidate.source, ROOT/'blueprints/qianzhen')
        data = {'id':'myvillage:qianzhen_relay_inn_v1','presentation':{'role':'relay_inn'}}
        self.assertEqual(candidate.template(data, Path('/exports')),
                         Path('/exports/relay_inn/qianzhen_relay_inn_v1.nbt'))
        self.assertEqual(candidate.prepare_pack(self.folder/'no-write'), {})
        self.assertFalse((self.folder/'no-write').exists())

    def test_candidate_binds_scene_source_resources_and_seed(self):
        c = self.candidate
        self.assertEqual(c.identity['root_seed'],20261024)
        self.assertEqual(c.identity['scene_version'], self.report['scene_version'])
        self.assertEqual(len(c.identity['source_sha256']),8)
        self.assertEqual(len(c.identity['resource_sha256']),7)
        self.assertEqual(c.identity['resource_sha256'], self.report['resource_sha256'])
        self.assertEqual(c.layout['version'], self.report['scene_version'])
        self.assertEqual(c.identity['source_fingerprint'], digest(self.report['generation']['materialized_inputs']))
        self.assertEqual(c.identity['resource_fingerprint'], digest(self.report['resource_sha256']))
        self.assertEqual(c.identity['export_source_sha256'], source_identity(c.source))
        self.assertEqual(c.identity['export_source_fingerprint'], digest(source_identity(c.source)))
        self.assertEqual(c.identity['export_source_sha256']['generation-report.json'],
                         c.identity['generation_report_sha256'])

    def test_cached_candidate_request_rejects_preview_source_drift(self):
        with tempfile.TemporaryDirectory() as folder:
            candidate = copy.copy(self.candidate)
            candidate.folder = Path(folder)/'candidate'
            shutil.copytree(self.bundle, candidate.folder)
            candidate.source = candidate.folder/'source'
            request = {'commit':'a'*40, 'candidate':copy.deepcopy(candidate.identity)}
            path = Path(folder)/'request.json'
            path.write_text(json.dumps(request))
            readme = candidate.source/'README.md'
            readme.write_bytes(readme.read_bytes()+b'\n')
            with patch('tools.qianzhen.evidence_candidate.assert_cloud'), \
                 self.assertRaisesRegex(ValueError, 'seed replay materialized source mismatch: README.md'):
                runtime_request(ROOT, path, 'Colab', candidate)
            self.assertEqual(candidate.identity, request['candidate'])

    def test_report_contradictions_fail_before_first_request_is_written(self):
        for kind, error in (
                ('root_seed', 'generation metadata mismatch: reproduce'),
                ('derived_seed', 'root_seed/derived_geometry_seed mismatch'),
                ('reproduce', 'generation metadata mismatch: reproduce'),
                ('algorithm_version', 'generation metadata mismatch: algorithm_version'),
                ('contract_version', 'generation metadata mismatch: contract_version'),
                ('seed_algorithm', 'generation metadata mismatch: seed_algorithm'),
                ('authored_scene_version', 'generation metadata mismatch: authored_scene_version')):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as folder:
                bundle = Path(folder)/'candidate'
                shutil.copytree(self.bundle, bundle)
                report = copy.deepcopy(self.report)
                g = report['generation']
                if kind == 'root_seed':
                    # Review counterexample: change only root_seed in both copies;
                    # source, resources, hashes, derived seeds and command stay intact.
                    g['root_seed'] = 17
                elif kind == 'derived_seed':
                    g['sites']['old_relay']['derived_geometry_seed'] += 1
                elif kind == 'reproduce':
                    g['reproduce'][g['reproduce'].index('--seed')+1] = '17'
                else:
                    g[kind] = 'unrecognized-version'
                for name in ('generation-report.json', 'source/generation-report.json'):
                    (bundle/name).write_text(json.dumps(report))
                before = {p.relative_to(bundle): p.read_bytes() for p in bundle.rglob('*') if p.is_file()}
                request = Path(folder)/'requests/fixed.json'
                with patch('sys.argv', ['candidate', '--candidate', str(bundle), '--provider', 'Colab',
                                        '--request', str(request)]), \
                     patch('tools.qianzhen.evidence_candidate.fixed_git') as fixed, \
                     patch.object(pipeline, 'generate', wraps=pipeline.generate) as replay, \
                     self.assertRaisesRegex(ValueError, error):
                    main()
                fixed.assert_not_called()
                replay.assert_not_called()
                self.assertFalse(request.parent.exists())
                self.assertEqual(before, {p.relative_to(bundle): p.read_bytes()
                                         for p in bundle.rglob('*') if p.is_file()})

    def test_consistent_wrong_seed_labels_still_require_real_generation(self):
        with tempfile.TemporaryDirectory() as folder:
            bundle = Path(folder)/'candidate'
            shutil.copytree(self.bundle, bundle)
            report = copy.deepcopy(self.report)
            g = report['generation']
            g['root_seed'] = 17
            g['reproduce'] = pipeline.reproduction_command(17)
            for site, spec in g['sites'].items():
                spec['derived_geometry_seed'] = derive_seed(17, g['authored_scene_version'], site)
            for name in ('generation-report.json', 'source/generation-report.json'):
                (bundle/name).write_text(json.dumps(report))
            # Re-export of this candidate is coherent, and all internal seed labels
            # now agree. Only actual generation from seed 17 detects the deception.
            request = Path(folder)/'fixed.json'
            with patch('sys.argv', ['candidate', '--candidate', str(bundle), '--request', str(request)]), \
                 patch.object(pipeline, 'generate', wraps=pipeline.generate) as replay, \
                 self.assertRaisesRegex(ValueError, 'seed replay materialized source mismatch'):
                main()
            replay.assert_called_once()
            self.assertEqual(replay.call_args.args[1:], (17, ROOT/'blueprints/qianzhen'))
            self.assertFalse(request.exists())

    def test_first_binding_replays_once_then_rehashes_without_regeneration(self):
        with tempfile.TemporaryDirectory() as folder:
            request = Path(folder)/'fixed.json'
            with patch('sys.argv', ['candidate', '--candidate', str(self.bundle), '--provider', 'Colab',
                                    '--request', str(request)]), \
                 patch('tools.qianzhen.evidence_candidate.fixed_git', return_value='a'*40), \
                 patch.object(pipeline, 'generate', wraps=pipeline.generate) as replay:
                main()
            replay.assert_called_once()
            self.assertEqual(replay.call_args.args[1:], (20261024, ROOT/'blueprints/qianzhen'))
            self.assertEqual(json.loads(request.read_text())['candidate'], self.candidate.identity)
            with patch.object(pipeline, 'generate', side_effect=AssertionError('unchanged bytes already replayed')):
                self.assertEqual(self.candidate.validate(), self.candidate.identity)

    def test_replay_compares_every_production_resource_even_with_self_consistent_hashes(self):
        for name in self.report['resource_sha256']:
            with self.subTest(resource=name), tempfile.TemporaryDirectory() as folder:
                bundle = Path(folder)/'candidate'
                shutil.copytree(self.bundle, bundle)
                path = bundle/'resources'/name
                path.write_bytes(path.read_bytes()+b'\n')
                report = copy.deepcopy(self.report)
                report['resource_sha256'][name] = sha(path)
                for report_name in ('generation-report.json', 'source/generation-report.json'):
                    (bundle/report_name).write_text(json.dumps(report))
                with self.assertRaisesRegex(ValueError, 'seed replay resource mismatch: '+name):
                    Candidate(bundle)

    def test_source_resource_report_and_scene_drift_fail_closed(self):
        for kind in ('source','resource','report_copy','version','incoherent_resources'):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as folder:
                bundle = Path(folder)/'candidate'
                shutil.copytree(self.bundle,bundle)
                report = copy.deepcopy(self.report)
                if kind == 'source':
                    path = bundle/'source/relay_inn.json'
                    path.write_bytes(path.read_bytes()+b'\n')
                elif kind in ('resource','incoherent_resources'):
                    name = 'data/myvillage/story/qianzhen_scene.json'
                    path = bundle/'resources'/name
                    scene = json.loads(path.read_text())
                    scene['version'] = 'wrong-version'
                    path.write_text(json.dumps(scene))
                    if kind == 'incoherent_resources':
                        report['resource_sha256'][name] = sha(path)
                elif kind == 'report_copy':
                    (bundle/'source/generation-report.json').write_text('{}')
                else:
                    report['scene_version'] = 'wrong-version'
                if kind in ('version','incoherent_resources'):
                    for name in ('generation-report.json','source/generation-report.json'):
                        (bundle/name).write_text(json.dumps(report))
                with self.assertRaises(ValueError):
                    Candidate(bundle)

    def test_collectors_plan_the_seeded_geometry_and_preserve_unrun(self):
        for kind in ('building','story'):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as folder:
                if kind == 'building':
                    e = QianzhenEvidence(Path(folder)/'plan', candidate=self.bundle, provider='Colab')
                    try:
                        self.assertEqual(e.source_hashes['myvillage:qianzhen_relay_inn_v1'],
                                         self.report['generation']['materialized_inputs']['relay_inn.json'])
                        data = next(d for d in e.buildings if d['presentation']['role']=='relay_inn')
                        self.assertEqual(e.blocks[data['id']],
                                         nbt.expand_structure(e.candidate.blueprints['relay_inn'])[2])
                    finally:
                        e.position_stream.close()
                else:
                    e = StoryEvidence(Path(folder)/'plan', candidate=self.bundle, provider='Colab')
                    try:
                        e.preflight()
                        self.assertEqual(e.layout['version'], self.report['scene_version'])
                        self.assertEqual(e.report['scene']['inputs'], self.report['generation']['materialized_inputs'])
                        self.assertEqual(set(e.report['planned_routes']),{'primary','secondary'})
                        with self.assertRaisesRegex(ValueError,'Seeded candidate cannot reuse'):
                            e.resume(Path('authored-resume.json'))
                    finally:
                        e.position_stream.close()
                        e.action_stream.close()
                self.assertEqual(e.report['completed'],[])
                self.assertEqual(e.report['selected'], e.report['unrun'])

    def test_startup_pack_copies_pinned_resources_and_restart_rejects_drift(self):
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder)
            candidate = Candidate(self.bundle)
            config = candidate.prepare_pack(out)
            world = out/'world'
            target = datapack.install(config['startup_datapack'], config['startup_datapack_hashes'],world)
            for name, expected in candidate.identity['resource_sha256'].items():
                self.assertEqual(sha(target/name),expected)
            datapack.install(candidate.pack,candidate.pack_hashes,world,reuse=True)
            (target/'pack.mcmeta').write_text('changed')
            with self.assertRaisesRegex(ValueError,'fingerprint'):
                datapack.install(candidate.pack,candidate.pack_hashes,world,reuse=True)
            self.assertEqual((target/'pack.mcmeta').read_text(),'changed')

    def test_supervisor_installs_after_world_clean_and_before_server_spawn(self):
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder)
            candidate = Candidate(self.bundle)
            config = candidate.prepare_pack(out)
            server = out/'server'
            world = server/session.LEVEL_NAME
            world.mkdir(parents=True)
            (world/'old').write_text('old fixture')
            supervisor = session.Supervisor(session.SessionConfig(session_id='offline', **config))
            with patch.object(session,'SERVER_DIR',server), patch.object(session,'PROPS_BACKUP',out/'backup'), \
                 patch.object(session,'PROPS_ABSENT_MARK',out/'absent'), patch.object(session,'write_state'), \
                 patch.object(session.procs,'free_ports',return_value=[12345,12346]), \
                 patch.object(supervisor,'spawn',side_effect=AssertionError('No process may start in offline test')):
                supervisor.write_server_config()
            self.assertFalse((world/'old').exists())
            candidate.verify_installed(world)
            self.assertEqual(supervisor.state['startup_datapack_hashes'],candidate.pack_hashes)

    def test_request_pins_seed_version_commit_path_and_selection(self):
        c = self.candidate
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'request.json'
            request = {'commit':'a'*40,'provider':'Colab','repo_root':str(ROOT), 'candidate':c.identity}
            def call(value, candidate=c):
                path.write_text(json.dumps(value))
                with patch('tools.qianzhen.evidence_candidate.assert_cloud'), \
                     patch('tools.qianzhen.evidence_candidate.fixed_git',return_value='a'*40):
                    return runtime_request(ROOT,path,'Colab',candidate)
            self.assertEqual(call(request)['candidate'],c.identity)
            for field, value in (('root_seed',2),('scene_version','wrong'),('source_fingerprint','b'*64),
                                 ('resource_fingerprint','c'*64)):
                bad = copy.deepcopy(request)
                bad['candidate'][field] = value
                with self.subTest(field=field), self.assertRaisesRegex(ValueError,'candidate fingerprint'):
                    call(bad)
            for field, value in (('commit','b'*40),('provider','Modal'),('repo_root','/content/other')):
                bad = {**request,field:value}
                with self.subTest(field=field), self.assertRaises(ValueError):
                    call(bad)
            with self.assertRaisesRegex(ValueError,'default authored'):
                call(request,Candidate())
            with self.assertRaisesRegex(ValueError,'candidate fingerprint'):
                call({'commit':'a'*40})
            self.assertEqual(call({'commit':'a'*40},Candidate())['commit'],'a'*40)

    def test_actual_saved_deployment_version_and_origin_are_required(self):
        with tempfile.TemporaryDirectory() as folder:
            world = Path(folder)
            path = world/'data/myvillage_qianzhen_scene.dat'
            path.parent.mkdir()
            record = {'schema':1,'phase':'COMPLETE','id':self.candidate.layout['id'],
                      'version':self.report['scene_version'],'dimension':'minecraft:overworld',
                      'x':0,'y':-61,'z':0}
            def write():
                nbt.write_gzipped_nbt(nbt._nbt_value_to_tag('', {'data':record}),str(path))
            write()
            self.assertEqual(self.candidate.verify_deployment(world,(0,-61,0))['version'],
                             self.report['scene_version'])
            for key,value in (('version','authored-version'),('x',1),('phase','PLACING')):
                old = record[key]
                record[key] = value
                write()
                with self.subTest(key=key), self.assertRaisesRegex(ValueError,'Runtime deployed'):
                    self.candidate.verify_deployment(world,(0,-61,0))
                record[key] = old

    def test_provider_guard_uses_real_root_and_explicit_colab_marker(self):
        assert_cloud('/workspace/repo','Modal',{})
        assert_cloud('/content/project','Colab',{'MYVILLAGE_CLOUD_PROVIDER':'Colab'})
        for root, provider, env in ((ROOT,'Colab',{'MYVILLAGE_CLOUD_PROVIDER':'Colab'}),
                                    ('/content/project','Colab',{}),
                                    ('/content/project','Modal',{}),
                                    ('/workspace/repo','Colab',{'MYVILLAGE_CLOUD_PROVIDER':'Colab'})):
            with self.subTest(root=str(root),provider=provider), self.assertRaises(RuntimeError):
                assert_cloud(root,provider,env)

    def test_startup_pack_config_is_opt_in_and_requires_hashes(self):
        self.assertEqual(session.SessionConfig(session_id='default').startup_datapack,'')
        with self.assertRaises(ValueError):
            session.SessionConfig(session_id='missing-hashes',startup_datapack='/tmp/pack')


class StartupDispatchTests(unittest.TestCase):
    def test_both_collectors_pass_pinned_pack_to_session_before_launch(self):
        for cls, module in ((QianzhenEvidence,'game_evidence'),(StoryEvidence,'story_evidence')):
            with self.subTest(collector=module), tempfile.TemporaryDirectory() as folder:
                out = Path(folder)
                request = out/'request.json'
                request.write_text('{"commit":"'+'a'*40+'"}')
                e = cls(out/'evidence', request=request)
                e.contract = {'bindings':{}}
                pack = {'startup_datapack':str(out/'pack'), 'startup_datapack_hashes':{'pack.mcmeta':'b'*64}}
                e.candidate = Mock(identity={'mode':'seeded_offline'})
                e.candidate.prepare_pack.return_value = pack
                try:
                    with patch('tools.qianzhen.'+module+'.runtime_request',return_value={'commit':'a'*40}), \
                         patch.object(session,'merge_options'), \
                         patch.object(session,'start',side_effect=RuntimeError('offline launch boundary')) as start:
                        with self.assertRaisesRegex(RuntimeError,'offline launch boundary'):
                            e.start()
                    config = start.call_args.args[0]
                    self.assertEqual(config.startup_datapack,pack['startup_datapack'])
                    self.assertEqual(config.startup_datapack_hashes,pack['startup_datapack_hashes'])
                    self.assertEqual(e.report['completed'],[])
                finally:
                    e.position_stream.close()
                    if isinstance(e,StoryEvidence):
                        e.action_stream.close()

    def test_fixed_git_requires_clean_tracked_source(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            subprocess.run(['git','init','--quiet'],cwd=root,check=True)
            path = root/'input.txt'
            path.write_text('fixed source')
            subprocess.run(['git','add','input.txt'],cwd=root,check=True)
            subprocess.run(['git','-c','user.name=Offline Test','-c','user.email=test@example.invalid',
                            'commit','--quiet','-m','offline source'],cwd=root,check=True)
            self.assertRegex(fixed_git(root),r'^[0-9a-f]{40}$')
            path.write_text('drift')
            with self.assertRaisesRegex(ValueError,'tracked modifications'):
                fixed_git(root)

    def test_disabled_pack_cannot_be_logged_as_success(self):
        with tempfile.TemporaryDirectory() as folder:
            e = StoryEvidence(Path(folder)/'evidence')
            e.candidate = Mock(folder=Path('/candidate'), pack_hashes={'pack.mcmeta':'b'*64})
            e.rc = lambda command: 'There are no enabled data packs'
            try:
                with self.assertRaisesRegex(AssertionError,'candidate_pack_enabled'):
                    e.verify_candidate_pack()
                self.assertFalse(e.report['checks'][-1]['pass'])
                self.assertEqual(e.report['completed'],[])
            finally:
                e.position_stream.close()
                e.action_stream.close()


if __name__ == '__main__':
    unittest.main()
