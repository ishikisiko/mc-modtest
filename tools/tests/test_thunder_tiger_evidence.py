import copy
import unittest
from pathlib import Path
import tempfile
from unittest.mock import patch

from tools import thunder_tiger_evidence as evidence
from tools.modal_runner import remote


class ThunderTigerEvidenceTest(unittest.TestCase):
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
