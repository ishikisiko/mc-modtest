import copy
import gzip
import unittest
from pathlib import Path
import tempfile
import zipfile
from unittest.mock import patch

from tools import thunder_tiger_evidence as evidence
from tools.modal_runner import remote


class ThunderTigerEvidenceTest(unittest.TestCase):
    def test_rotated_lair_attack_fixture_uses_authored_clear_entrance_lane(self):
        boss,player=evidence.entrance_trial_positions([-872,119,1021],[-871.5,119,1055.5],25)
        self.assertEqual(boss,[-871.5,119,1021.5])
        self.assertEqual(player,[-871.5,119,1046.5])
        boss,player=evidence.entrance_trial_positions([0,70,0],[-33.5,70,.5],12)
        self.assertEqual(player,[-11.5,70,.5])
        with self.assertRaises(ValueError):evidence.entrance_trial_positions([0,70,0],[.5,70,.5],12)

    @staticmethod
    def level_bytes(flag=0):
        return (b'\x0a\x00\x00\x0a\x00\x04Data\x01\x00\x0dallowCommands'+bytes([flag])
                +b'\x03\x00\x07Version\x00\x00\x00\x7b\x00\x00')

    def test_allow_commands_handoff_changes_only_one_byte_and_is_deterministic(self):
        raw=self.level_bytes()
        original=gzip.compress(raw,mtime=123)
        converted,old=evidence.allow_commands_handoff_copy(original)
        self.assertEqual(old,0)
        expected=raw.replace(b'allowCommands\x00',b'allowCommands\x01')
        self.assertEqual(gzip.decompress(converted),expected)
        self.assertEqual(sum(a!=b for a,b in zip(raw,gzip.decompress(converted))),1)
        self.assertEqual(evidence.allow_commands_handoff_copy(original)[0],converted)
        self.assertEqual(gzip.decompress(original),raw)
        marker=b'\x01\x00\x0dallowCommands'
        for invalid in (raw.replace(marker,b'\x01\x00\x0dotherCommandX'),
                        raw[:-2]+marker+b'\x00\x00\x00',self.level_bytes(2)):
            with self.assertRaises(ValueError):evidence.allow_commands_handoff_copy(gzip.compress(invalid))

    def test_recorded_normal_head_miss_has_reachable_body_at_sword_height(self):
        status={'pos':[-869.597,119.0,1017.160],'yaw':'165.5'}
        player=[-868.0192247718684,119.0,1014.2206445575686]
        rules={'body_front':1.7,'body_back':-2.0,'body_width':2.8}
        center,gap=evidence.body_contact_geometry(status,player,rules)
        self.assertLess(gap,2.2)
        self.assertAlmostEqual(center[1],120.1)
        self.assertGreater(center[2],1017)
        # The old normal-head target was Y121.9 and almost due west. The
        # authored torso target lies southeast, within the real sword-height box.
        self.assertGreater(center[2]-player[2],3)

    def test_aim_adjustment_preserves_live_position(self):
        with tempfile.TemporaryDirectory() as directory:
            instance=evidence.TigerEvidence(Path(directory)/'evidence','combat')
            instance.s=type('Actor',(),{'username':'CaptureDev'})()
            with patch.object(instance,'rc',side_effect=['CaptureDev has the following entity data: [1.0d, 70.0d, 3.0d]','']) as rc:
                instance.aim_current([4,71,8])
            command=rc.call_args.args[0]
            self.assertIn('at @s run tp @s ~ ~ ~ ',command)

    def test_nested_hud_hiding_does_not_toggle_twice_or_restore_visible_in_video(self):
        with tempfile.TemporaryDirectory() as directory:
            instance=evidence.TigerEvidence(Path(directory)/'evidence','visuals')
            with patch.object(evidence.time,'sleep'),patch.object(instance,'s') as session:
                instance.hud(True);instance.hud(True);instance.hud(False)
                self.assertEqual(session.game.key.call_count,2)
                self.assertFalse(instance.hud_hidden)

    def test_multipart_parent_count_uses_parent_only_tag_and_anchor_uuid_readback(self):
        with tempfile.TemporaryDirectory() as directory:
            instance=evidence.TigerEvidence(Path(directory)/'evidence','visuals')
            instance.anchor=[24,70,35]
            # Native bounded entity queries return the parent and both same-type
            # colliders. Only the actual parent receives the lair tag.
            def native_reply(command):
                count=1 if 'tag=thunder_tiger_lair_boss' in command else 3
                return f'Test passed, count: {count}\n'
            with patch.object(instance,'rc',side_effect=native_reply) as rc:
                self.assertEqual(instance.count(),1)
                self.assertIn('tag=thunder_tiger_lair_boss',rc.call_args.args[0])
            self.assertIn('tag=thunder_tiger_lair_boss',instance.target)

    def test_root_status_parser_accepts_real_root_only_and_refuses_incomplete_root(self):
        root=('myvillage:thunder_tiger #7 pos=(1,70,3) hp=600 move=none tick=-1 '
              'uuid=00000000-0000-0000-0000-000000000007 max=600 boss_phase=1 '
              'horn_broken=false horn_exposure=0')
        rows=evidence.parse_boss_roots(root+'\n')
        self.assertEqual(len(rows),1)
        self.assertEqual(rows[0]['uuid'],'00000000-0000-0000-0000-000000000007')
        self.assertEqual(evidence.parse_boss_roots('No beast selected\n'),[])
        with self.assertRaises(ValueError):evidence.parse_boss_roots('myvillage:thunder_tiger #7 hp=600')

    def test_pristine_export_precedes_combat_and_wounded_export_has_distinct_stage(self):
        with tempfile.TemporaryDirectory() as directory:
            instance=evidence.TigerEvidence(Path(directory)/'evidence','entry,visuals,combat,death')
            calls=[]
            def export(pristine=False):
                calls.append(('export',pristine))
                instance.report[('pristine' if pristine else 'wounded')+'_world']={}
            with patch.object(instance,'start'),patch.object(instance,'natural'), \
                 patch.object(instance,'export_world',side_effect=export), \
                 patch.object(instance,'entry',side_effect=lambda:calls.append('entry')), \
                 patch.object(instance,'visuals',side_effect=lambda:calls.append('visuals')), \
                 patch.object(instance,'combat',side_effect=lambda:calls.append('combat')), \
                 patch.object(instance,'death',side_effect=lambda:calls.append('death')):
                instance.run()
            self.assertEqual(calls,['entry','visuals',('export',True),'combat',('export',False),'death'])

    def test_world_export_omits_fixture_players_sets_safe_spawn_and_never_overwrites(self):
        with tempfile.TemporaryDirectory() as directory:
            base=Path(directory);server=base/'server';world=server/evidence.cs.LEVEL_NAME
            world.mkdir(parents=True);(world/'level.dat').write_bytes(gzip.compress(self.level_bytes()))
            for folder in ('playerdata','advancements','stats'):
                (world/folder).mkdir();(world/folder/'fixture-player.dat').write_bytes(b'fixture actor')
            instance=evidence.TigerEvidence(base/'out','visuals')
            instance.s=type('Actor',(),{'username':'CaptureDev'})()
            instance.report['natural_lair']={'entry':[24.5,70,1.5],'forceload':[-48,-48,48,48]}
            state={'boss_phase':1,'horn_broken':False,'max':600}
            def command(command):
                if command.startswith('gamerule '):return 'Gamerule is currently set to: true'
                if command=='tick query':return 'Running at 20.0 ticks per second'
                return ''
            with patch.object(evidence.cs,'SERVER_DIR',server),patch.object(instance,'status',return_value=state), \
                 patch.object(instance,'body',return_value={'Health':600}),patch.object(instance,'rc',side_effect=command) as rc:
                instance.export_world(pristine=True)
                with self.assertRaises(FileExistsError):instance.export_world(pristine=True)
                commands=[call.args[0] for call in rc.call_args_list]
            self.assertIn('setworldspawn 24 70 1',commands)
            self.assertIn('defaultgamemode creative',commands)
            self.assertIn('myvillage world resume',commands)
            with zipfile.ZipFile(base/'out/thunder-tiger-pristine-world.zip') as archive:
                names=archive.namelist()
                self.assertTrue(any(name.endswith('level.dat') for name in names))
                self.assertFalse(any('/playerdata/' in name or '/advancements/' in name or '/stats/' in name for name in names))
                self.assertTrue(any(name.endswith('README.txt') for name in names))

    def test_actual_case_dispatch_completes_only_selected_cases(self):
        with tempfile.TemporaryDirectory() as directory:
            instance=evidence.TigerEvidence(Path(directory)/'evidence','visuals,restart')
            calls=[]
            for method in (*evidence.CASES,'run_case'):
                self.assertTrue(callable(getattr(instance,method)),method)
            with patch.object(instance,'start'), patch.object(instance,'natural'), \
                 patch.object(instance,'visuals',side_effect=lambda:calls.append('visuals')), \
                 patch.object(instance,'restart',side_effect=lambda:calls.append('restart')), \
                 patch.object(instance,'export_world'):
                instance.run()
            self.assertEqual(calls,['visuals','restart'])
            self.assertEqual(instance.report['completed'],['visuals','restart'])
            self.assertEqual(instance.report['unrun'],[])
            self.assertEqual(instance.report['cases']['restart']['result'],'pass')

    def test_failed_case_remains_uncompleted_with_error_retained(self):
        with tempfile.TemporaryDirectory() as directory:
            instance=evidence.TigerEvidence(Path(directory)/'evidence','combat,horn')
            def fail():raise RuntimeError('real fixture failed')
            with self.assertRaisesRegex(RuntimeError,'real fixture'):
                instance.run_case('combat',fail)
            self.assertEqual(instance.report['completed'],[])
            self.assertEqual(instance.report['unrun'],['combat','horn'])
            self.assertEqual(instance.report['cases']['combat']['result'],'fail')
            self.assertIn('real fixture',instance.report['cases']['combat']['error'])

    def test_runtime_cannot_fall_back_to_local_game_start(self):
        with tempfile.TemporaryDirectory() as directory:
            instance=evidence.TigerEvidence(Path(directory)/'evidence','visuals')
            with patch.object(evidence,'ROOT',Path('/local/check')), patch.object(evidence.cs,'start') as start:
                with self.assertRaisesRegex(RuntimeError,'Modal disposable'):
                    instance.start()
                start.assert_not_called()

    def test_case_selection_is_bounded_canonical_and_rejects_typos(self):
        self.assertEqual(evidence.select_cases('death,visuals'), ['visuals','death'])
        for value in ('', 'combat,combat', 'all,death', 'missing', '../death'):
            with self.assertRaises(ValueError): evidence.select_cases(value)

    def test_status_refuses_incomplete_or_invalid_authoritative_data(self):
        line = ('myvillage:thunder_tiger pos=(1.0, 70.0, -8.0) hp=580 yaw=180 move=none tick=-1 '
                'uuid=01234567-89ab-cdef-0123-456789abcdef max=600 boss_phase=2 '
                'horn_broken=true horn_exposure=0 home=<1,70,-8> horn_pos=<1,71.45,-10.9>')
        row=evidence.parse_boss_status(line)
        self.assertEqual(row['pos'],[1,70,-8])
        self.assertEqual(row['horn_pos'],[1,71.45,-10.9])
        self.assertTrue(row['horn_broken'])
        self.assertEqual(row['boss_phase'],2)
        for broken in (line.replace('boss_phase=2','boss_phase=0'),
                       line.replace('horn_broken=true','horn_broken=1'),
                       line.replace('max=600','max=NaN'),
                       line.replace('horn_exposure=0',''),
                       line.replace('pos=(1.0, 70.0, -8.0)','pos=missing')):
            with self.assertRaises(ValueError):evidence.parse_boss_status(broken)

    def test_attack_evidence_requires_same_boss_move_and_order(self):
        events=evidence.tiger_events('\n'.join([
            '[server] THUNDER_TIGER event=active uuid=other move=claw tick=18',
            '[server] THUNDER_TIGER event=active uuid=own move=claw tick=18',
            '[server] THUNDER_TIGER event=telegraph uuid=own move=claw tick=0',
            '[server] THUNDER_TIGER event=active uuid=own move=pounce tick=29']))
        self.assertFalse(evidence.attack_evidence(events,'own','claw'))
        events.append({'event':'active','uuid':'own','move':'claw'})
        self.assertTrue(evidence.attack_evidence(events,'own','claw'))
        with self.assertRaises(ValueError):evidence.tiger_events('THUNDER_TIGER event=death')

    def test_horn_requires_authored_pillar_collision_before_break_same_boss(self):
        collision={'event':'horn_collision','uuid':'own','pillar':'<1,70,3>'}
        broken={'event':'horn_break','uuid':'own'}
        self.assertFalse(evidence.horn_chain([broken,collision],'own'))
        self.assertFalse(evidence.horn_chain([collision,{**broken,'uuid':'other'}],'own'))
        self.assertFalse(evidence.horn_chain([{k:v for k,v in collision.items() if k!='pillar'},broken],'own'))
        self.assertTrue(evidence.horn_chain([collision,broken],'own'))

    def test_restart_requires_identity_health_horn_phase_and_lair_not_defaults(self):
        before={'UUID':[1,2,3,4],'Health':123.5,'thunder_tiger':{
            'horn_broken':1,'phase':2,'home':12345,'pillars':[456,789],
            'encounter_realm':'myvillage:foundation','encounter_stage':0}}
        after=copy.deepcopy(before)
        self.assertTrue(evidence.persistence_equal(before,after))
        after['thunder_tiger']['horn_broken']=0
        self.assertFalse(evidence.persistence_equal(before,after))
        after=copy.deepcopy(before);after['Health']=600
        self.assertFalse(evidence.persistence_equal(before,after))
        after=copy.deepcopy(before);after['UUID'][0]=9
        self.assertFalse(evidence.persistence_equal(before,after))
        after=copy.deepcopy(before);del after['thunder_tiger']['pillars']
        self.assertFalse(evidence.persistence_equal(before,after))

    def test_reward_inventory_readback_allows_weapon_wear_but_detects_added_items(self):
        initial=[{'id':'myvillage:qingfeng_sword','count':1,'components':{'minecraft:damage':0}}]
        used=[{'id':'myvillage:qingfeng_sword','count':1,'components':{'minecraft:damage':1}}]
        self.assertEqual(evidence.inventory_counts(initial),evidence.inventory_counts(used))
        used.append({'id':'minecraft:diamond','count':1})
        self.assertNotEqual(evidence.inventory_counts(initial),evidence.inventory_counts(used))

    def test_minecraft_packed_positions_preserve_negative_axes(self):
        for x,y,z in ((24,72,-35),(-10,-60,100),(0,0,0),(33554431,2047,-33554432)):
            packed=((x & 0x3ffffff)<<38)|((z & 0x3ffffff)<<12)|(y & 0xfff)
            if packed >= 1<<63:packed-=1<<64
            self.assertEqual(evidence.TigerEvidence.unpack_pos(packed),[x,y,z])

    def test_modal_selection_has_no_unrelated_release_or_chunky_work(self):
        command=remote.commands_for('thunder-tiger-runtime','visuals,observers')[-1]
        self.assertEqual(command[-2:],['--cases','visuals,observers'])
        planned=' '.join(' '.join(c) for c in remote.commands_for('thunder-tiger-build'))
        self.assertNotIn('release_gate',planned)
        self.assertNotIn('chunky',planned.lower())
        self.assertIn('entity.beast.*',planned)
        with self.assertRaises(ValueError):remote.commands_for('thunder-tiger-build','visuals')
        with self.assertRaises(ValueError):remote.commands_for('thunder-tiger-runtime','typo')


if __name__=='__main__':unittest.main()
