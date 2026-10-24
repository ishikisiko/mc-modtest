"""Native fixture contracts: persistent identities versus execution leases."""
import copy
import unittest
from tools.qianzhen_opening_repair import require_same_people

class RepairLifecycleTests(unittest.TestCase):
    def setUp(self):
        self.before = {role: {'personId': 165 + i, 'epoch': 7 + i}
                       for i, role in enumerate(('guide','pharmacist','zhou_he','warden'))}

    def test_restart_allows_fresh_execution_leases_with_same_people(self):
        after = copy.deepcopy(self.before)
        for row in after.values(): row['epoch'] += 4
        require_same_people(self.before, after)
        require_same_people(self.before, self.before)

    def test_changed_missing_duplicate_people_and_bad_epochs_refuse(self):
        bad = []
        for field,value in [('personId',999), ('epoch',-1), ('epoch',True), ('epoch','12'), ('epoch',1)]:
            after = copy.deepcopy(self.before); after['guide'][field] = value; bad.append(after)
        after = copy.deepcopy(self.before); after.pop('guide'); bad.append(after)
        after = copy.deepcopy(self.before); after['other'] = after['guide']; bad.append(after)
        after = copy.deepcopy(self.before); after['guide']['personId'] = after['warden']['personId']; bad.append(after)
        for after in bad:
            with self.subTest(after=after), self.assertRaises(AssertionError):
                require_same_people(self.before, after)

    def test_persistent_bindings_compare_every_field(self):
        from tools.qianzhen_opening_repair import require_same_story_bindings
        original = [{'actor_key':'guide','person_id':165,'site_id':'qianzhen_chapter_1',
                     'current_place_id':'guide','revision':0,'returned':False}]
        require_same_story_bindings(original,copy.deepcopy(original))
        for key in original[0]:
            changed = copy.deepcopy(original); changed[0][key] = 'tampered'
            with self.subTest(field=key), self.assertRaises(AssertionError):
                require_same_story_bindings(original,changed)

    def test_real_worldsim_payload_reads_complete_bindings(self):
        import gzip, json, struct, tempfile
        from pathlib import Path
        from tools.qianzhen_opening_repair import saved_story_bindings
        roles = ('guide','pharmacist','zhou_he','warden')
        bindings = [{'story_id':'qianzhen_chapter_1','actor_key':r,'person_id':165+i,
                     'site_id':'qianzhen_chapter_1','revision':0,'returned':False}
                    for i,r in enumerate(roles)]
        payload = json.dumps({'life':{'story_actors':bindings},
                              'persons':[{'id':165+i} for i in range(4)]}).encode()
        def utf(s):
            b=s.encode(); return struct.pack('>H',len(b))+b
        raw = b'\x0a'+utf('')+b'\x0a'+utf('data')+b'\x07'+utf('payload')+struct.pack('>i',len(payload))+payload+b'\0\0'
        with tempfile.TemporaryDirectory() as folder:
            world=Path(folder); (world/'data').mkdir()
            (world/'data/myvillage_world_sim.dat').write_bytes(gzip.compress(raw))
            self.assertEqual(sorted(bindings,key=lambda r:r['actor_key']),saved_story_bindings(world))

class FixedBuildReuseTests(unittest.TestCase):
    def setUp(self):
        import tempfile, subprocess, json
        from pathlib import Path
        from tools.qianzhen_opening_run import PAL, CATALOG
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        base = Path(self.temp.name); self.previous = base/'previous'
        self.old = self.previous/'repo'; self.root = base/'new'
        self.old.mkdir(parents=True)
        def git(*args,cwd=self.old):
            return subprocess.check_output(['git',*args],cwd=cwd,text=True,stderr=subprocess.DEVNULL).strip()
        git('init'); (self.old/'production.txt').write_text('fixed')
        git('add','production.txt'); git('-c','user.name=fixture','-c','user.email=fixture@example.test','commit','-m','fixture')
        commit=git('rev-parse','HEAD')
        git('clone',str(self.old),str(self.root),cwd=base)
        for repo in (self.old,self.root):
            for name in (PAL,CATALOG):
                p=repo/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'dependency')
        evidence=self.previous/'evidence'; evidence.mkdir()
        (evidence/'source-snapshot.json').write_text(json.dumps({'commit':commit}))
        (evidence/'build.log').write_text('BUILD SUCCESSFUL')
        (evidence/'fixture.jar').write_bytes(b'jar')
        for name,data in [('build/libs/fixture.jar',b'jar'),('build/generated/resource.json',b'resource'),
                          ('build/test-results/test/TEST-fixture.xml',b'<testsuite tests="1" failures="0" errors="0" skipped="0"/>')]:
            p=self.old/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)

    def test_explicit_reuse_records_old_build_and_new_collector(self):
        from tools.qianzhen_opening_run import reuse_fixed_build
        result=reuse_fixed_build(self.root,self.previous)
        self.assertTrue(result['complete_production_packaged_inputs_equal'])
        self.assertFalse(result['new_ref_build_claimed'])
        self.assertEqual(1,result['junit']['tests'])
        self.assertEqual(b'resource',(self.root/'build/generated/resource.json').read_bytes())

    def test_changed_production_or_dependency_or_jar_refuses(self):
        import subprocess
        from tools.qianzhen_opening_run import reuse_fixed_build, PAL
        for kind in ('production','dependency','jar','junit'):
            if kind=='production':
                # An uncommitted source drift in the original also invalidates reuse.
                p=self.old/'production.txt'
            elif kind=='dependency': p=self.root/PAL
            elif kind=='jar': p=self.old/'build/libs/fixture.jar'
            else: p=self.old/'build/test-results/test/TEST-fixture.xml'
            original=p.read_bytes();p.write_bytes(b'changed' if kind!='junit' else b'<testsuite tests="1" failures="1"/>')
            try:
                with self.subTest(kind=kind), self.assertRaises((ValueError,subprocess.CalledProcessError)):
                    reuse_fixed_build(self.root,self.previous)
                self.assertFalse((self.root/'build').exists())
            finally:p.write_bytes(original)

class RecordingStartTests(unittest.TestCase):
    def test_only_actual_encoded_frame_opens_capture_gate(self):
        import tempfile
        from pathlib import Path
        from unittest.mock import Mock
        from tools.qianzhen_opening_run import wait_recorded_frame
        with tempfile.TemporaryDirectory() as folder:
            progress=Path(folder)/'progress.log';process=Mock();process.poll.return_value=None
            progress.write_text('frame=0\nprogress=continue\n')
            with self.assertRaises(TimeoutError):wait_recorded_frame(process,progress,timeout=.01)
            progress.write_text('frame=1\nprogress=continue\n')
            wait_recorded_frame(process,progress)
            process.poll.return_value=1
            with self.assertRaises(RuntimeError):wait_recorded_frame(process,progress)

class ForcedTicketOwnershipTests(unittest.TestCase):
    class API:
        def __init__(self, chunks):
            self.chunks=set(chunks); self.writes=[]; self.bad_query=False; self.fail_add=False
        def command(self, command):
            if command=='forceload query':
                if self.bad_query: return 'unrecognized reply'
                if not self.chunks: return 'No force loaded chunks were found in Overworld'
                prefix='A force loaded chunk was found' if len(self.chunks)==1 else str(len(self.chunks))+' force loaded chunks were found'
                return prefix+' in Overworld at: '+', '.join(f'[{x}, {z}]' for x,z in sorted(self.chunks))
            self.writes.append(command)
            x,z=map(int,command.split()[2:4]); point=(x//16,z//16)
            if command.startswith('forceload add '):
                self.chunks.add(point)
                if self.fail_add: raise TimeoutError('indeterminate add after server write')
            elif command.startswith('forceload remove '): self.chunks.remove(point)
            else: raise AssertionError(command)
            return 'fixture'

    def test_all_preexisting_mixed_and_none_restore_across_two_startups(self):
        from tools.qianzhen_opening_repair import ForcedTickets
        target={(9,4),(10,4),(9,5),(10,5)}
        points=[[x*16,0,z*16] for x,z in target]
        for before in (target|{(-5,-5)}, {(9,4),(10,5),(-5,-5)}, set()):
            with self.subTest(before=before):
                api=self.API(before); result={}
                for _ in range(2):
                    manager=ForcedTickets(api,points,result);manager.acquire()
                    self.assertEqual(before|target,api.chunks)
                    manager.cleanup();self.assertEqual(before,api.chunks)
                removed={(int(c.split()[2])//16,int(c.split()[3])//16) for c in api.writes if c.startswith('forceload remove ')}
                self.assertEqual(target-before,removed)
                self.assertTrue(all(row['before']==row['after']==sorted(before) for row in result['forced_ticket_lifecycle']))

    def test_query_failure_refuses_and_indeterminate_add_cleans_only_owned(self):
        from tools.qianzhen_opening_repair import ForcedTickets
        api=self.API({(9,4)});api.bad_query=True
        manager=ForcedTickets(api,[[144,0,64],[160,0,64]],{})
        with self.assertRaises(ValueError):manager.acquire()
        manager.cleanup();self.assertEqual([],api.writes)
        api.bad_query=False;api.fail_add=True
        manager=ForcedTickets(api,[[144,0,64],[160,0,64]],{})
        with self.assertRaises(TimeoutError):manager.acquire()
        api.bad_query=True
        with self.assertRaises(ValueError):manager.cleanup()
        self.assertEqual({(9,4),(10,4)},api.chunks)
        api.bad_query=False;manager.cleanup();self.assertEqual({(9,4)},api.chunks)

    def test_query_parser_rejects_count_duplicates_unknown_and_limit_stays_four(self):
        from tools.qianzhen_opening_repair import parse_forced_chunks,ForcedTickets
        for value in ('', 'No chunks were marked for force loading',
                      '2 force loaded chunks were found in Overworld at: [1, 2]',
                      '2 force loaded chunks were found in Overworld at: [1, 2], [1, 2]',
                      'A force loaded chunk was found in Overworld at: [1, 2] garbage'):
            with self.subTest(value=value),self.assertRaises(ValueError):parse_forced_chunks(value)
        self.assertEqual({(-1,-17)},parse_forced_chunks('A force loaded chunk was found in Overworld at: [-1, -17]'))
        with self.assertRaises(ValueError):ForcedTickets(self.API(set()),[[i*16,0,0] for i in range(5)],{})

    def test_actual_saved_long_array_signed_chunk_coordinates(self):
        import tempfile,gzip,struct
        from pathlib import Path
        from tools.qianzhen_opening_repair import saved_forced_chunks
        expected={(-1,-17),(9,4),(10,5)}
        encoded=[]
        for x,z in expected:
            v=(x&0xffffffff)|((z&0xffffffff)<<32);encoded.append(v-(1<<64) if v>=(1<<63) else v)
        def utf(s):
            b=s.encode();return struct.pack('>H',len(b))+b
        raw=b'\x0a'+utf('')+b'\x0a'+utf('data')+b'\x0c'+utf('Forced')+struct.pack('>i',len(encoded))+b''.join(struct.pack('>q',v) for v in encoded)+b'\0\0'
        with tempfile.TemporaryDirectory() as folder:
            world=Path(folder);(world/'data').mkdir()
            self.assertEqual(set(),saved_forced_chunks(world))
            (world/'data/chunks.dat').write_bytes(gzip.compress(raw))
            self.assertEqual(expected,saved_forced_chunks(world))
