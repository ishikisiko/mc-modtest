"""Offline command reconstruction and a fake owner bridge test the write boundary."""
import copy
import json
from pathlib import Path
import tempfile
import unittest

from tools.yunshan.deploy_district import (Volume,assert_world,block_record,checked_command,
                                         execute,export_plan,split_box,verify_plan)


FOLDER='yunshan-d1-v2-test-20261018'


class FakeBridge:
    def __init__(self,folder=FOLDER,managed=True):
        self.folder=folder
        self.managed=managed
        self.calls=[]
        self.token=0
        self.fail=None
        self.area_counts={'minecraft:stone':1}
        self.command_result=None

    def call(self,method,path,params=None):
        self.calls.append((method,path,params))
        if path=='/client/world':
            return {'folder':self.folder,'singleplayer':True,'inWorld':True,'busy':False}
        if path=='/client/worlds':
            return {'worlds':[{'folder':self.folder,'bridgeOwned':self.managed,'preset':'flat','gameMode':'creative'}]}
        if path=='/world/area':
            return {'counts':self.area_counts}
        if path=='/command':
            command=params['command']
            if command.startswith('scoreboard players get'):
                return {'success':True,'result':self.token,'output':[]}
            if self.fail and self.fail in command:
                return {'success':False,'output':['Permission denied']}
            if self.command_result is not None:
                return self.command_result
            if command.startswith('scoreboard players set'):
                self.token=int(command.split()[-1])
            return {'success':True,'result':1,'output':[]}
        raise AssertionError(path)

    def writes(self):
        return [params['command'] for method,path,params in self.calls if path=='/command']


class DistrictDeploymentTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.progress=Path(self.temp.name)/'progress.json'

    def volume(self):
        v=Volume((126,64,0),(131,68,3))
        v.fill((126,64,0),(131,64,3),'stone')
        v.set((126,65,0),'water')
        v.set((127,65,1),'spruce_door[facing=south,half=lower,hinge=left]')
        v.set((127,66,1),'spruce_door[facing=south,half=upper,hinge=left]')
        # This bed straddles the x=128 load-tile boundary.
        v.set((127,65,2),'red_bed[facing=east,part=foot]')
        v.set((128,65,2),'red_bed[facing=east,part=head]')
        v.set((130,65,1),'spruce_wall_sign[facing=south]')
        return v

    def test_commands_reconstruct_exact_volume_with_ordered_cross_tile_pairs(self):
        v=self.volume()
        plan=export_plan(v,[{'pos':[130,65,1],'messages':['','云山','','']}])
        self.assertTrue(plan['logical_volume_match'])
        self.assertTrue(verify_plan(plan,v))
        phases=[s['phase'] for s in plan['stages']]
        self.assertLess(phases.index('solid'),phases.index('fluid'))
        self.assertLess(phases.index('fluid'),phases.index('pairs'))
        pairs=[r['state'] for s in plan['stages'] if s['phase']=='pairs' for r in s['commands']]
        self.assertIn('half=lower',pairs[0])
        self.assertIn('half=upper',pairs[1])
        self.assertIn('part=foot',pairs[2])
        self.assertIn('part=head',pairs[3])
        self.assertEqual(plan['counts']['sign_text'],1)
        changed=copy.deepcopy(plan)
        next(s for s in changed['stages'] if s['phase']=='solid')['commands'][0]['state']='minecraft:dirt'
        with self.assertRaisesRegex(ValueError,'reconstruct'):
            verify_plan(changed,v)

    def test_large_box_splits_under_vanilla_cap(self):
        boxes=list(split_box((0,0,0),(209,81,264)))
        self.assertEqual(sum((b[0]-a[0]+1)*(b[1]-a[1]+1)*(b[2]-a[2]+1) for a,b in boxes),210*82*265)
        self.assertTrue(all((b[0]-a[0]+1)*(b[1]-a[1]+1)*(b[2]-a[2]+1)<=32768 for a,b in boxes))

    def test_incomplete_bed_is_rejected(self):
        v=Volume((0,0,0),(1,1,1))
        v.set((0,0,0),'red_bed[facing=north,part=foot]')
        with self.assertRaisesRegex(ValueError,'incomplete'):
            export_plan(v,[])

    def test_rejects_original_wrong_or_unmanaged_save_before_writes(self):
        plan=export_plan(self.volume(),[])
        for bridge,folder in ((FakeBridge('dev1'),'dev1'),(FakeBridge('dev1'),FOLDER),(FakeBridge(managed=False),FOLDER)):
            with self.subTest(folder=folder),self.assertRaises((ValueError,RuntimeError)):
                execute(plan,bridge,folder,self.progress)
            self.assertEqual(bridge.writes(),[])

    def test_false_command_is_failure_and_nochange_needs_exact_area_readback(self):
        bridge=FakeBridge()
        bridge.command_result={'success':False,'output':['Permission denied']}
        record=block_record((0,0,0),(0,0,0),'minecraft:stone')
        with self.assertRaisesRegex(RuntimeError,'Command failed'):
            checked_command(bridge,record['command'],record)
        bridge.command_result={'success':False,'output':['Could not set the block']}
        checked_command(bridge,record['command'],record)
        bridge.area_counts={'minecraft:dirt':1}
        with self.assertRaisesRegex(RuntimeError,'Command failed'):
            checked_command(bridge,record['command'],record)

    def test_empty_guarded_air_nochange_requires_correct_guard_and_full_box(self):
        bridge=FakeBridge()
        bridge.token=123
        bridge.command_result={'success':False,'result':0,'output':[]}
        bridge.area_counts={'minecraft:air':4}
        record=block_record((0,0,0),(1,0,1),'minecraft:air')
        command='execute if score #delivery ysd1delivery matches 123 run '+record['command']
        checked_command(bridge,command,record)
        self.assertEqual(sum(path=='/world/area' for _,path,_ in bridge.calls),1)
        self.assertEqual(sum(path=='/command' and p['command'].startswith('scoreboard players get')
                             for _,path,p in bridge.calls),2)
        bridge.token=456
        bridge.calls=[]
        with self.assertRaisesRegex(RuntimeError,'Deployment guard failed'):
            checked_command(bridge,command,record)
        self.assertFalse(any(path=='/world/area' for _,path,_ in bridge.calls))
        bridge.token=123
        for counts in ({'minecraft:stone':4},{'minecraft:air':3}):
            with self.subTest(counts=counts):
                bridge.area_counts=counts
                with self.assertRaisesRegex(RuntimeError,'Command failed') as error:
                    checked_command(bridge,command,record)
                self.assertNotIn('matches 123',str(error.exception))

    def test_guarded_real_error_is_not_accepted_even_if_box_already_matches(self):
        bridge=FakeBridge()
        bridge.token=123
        bridge.area_counts={'minecraft:air':1}
        bridge.command_result={'success':False,'output':['Permission denied']}
        record=block_record((0,0,0),(0,0,0),'minecraft:air')
        with self.assertRaisesRegex(RuntimeError,'Command failed'):
            checked_command(bridge,'execute if score #delivery ysd1delivery matches 123 run '+record['command'],record)
        self.assertFalse(any(path=='/world/area' for _,path,_ in bridge.calls))

    def test_empty_guarded_sign_nochange_requires_exact_nbt_condition(self):
        bridge=FakeBridge()
        bridge.token=123
        original=bridge.call
        matching=True
        def sign_query(method,path,params=None):
            if path=='/command' and 'if data block' in params['command']:
                bridge.calls.append((method,path,params))
                return {'success':matching,'result':1 if matching else 0,'output':[]}
            return original(method,path,params)
        bridge.call=sign_query
        bridge.command_result={'success':False,'result':0,'output':[]}
        record={'pos':[1,2,3],'sign':{'is_waxed':False},'command':'data merge block 1 2 3 {"is_waxed":false}'}
        command='execute if score #delivery ysd1delivery matches 123 run '+record['command']
        checked_command(bridge,command,record)
        query=next(params['command'] for _,path,params in bridge.calls if path=='/command' and 'if data block' in params['command'])
        self.assertEqual(query,'execute if score #delivery ysd1delivery matches 123 if data block 1 2 3 {"is_waxed":false} run time query gametime')
        matching=False
        with self.assertRaisesRegex(RuntimeError,'Command failed'):
            checked_command(bridge,command,record)
        bridge.token=456
        with self.assertRaisesRegex(RuntimeError,'Deployment guard failed'):
            checked_command(bridge,command,record)

    def test_interrupted_resume_replays_nonclear_only_and_completed_is_protected(self):
        plan=export_plan(self.volume(),[])
        bridge=FakeBridge()
        bridge.fail='minecraft:water'
        with self.assertRaisesRegex(RuntimeError,'Command failed'):
            execute(plan,bridge,FOLDER,self.progress,workers=2)
        progress=json.loads(self.progress.read_text())
        self.assertTrue(progress['clear_complete'])
        self.assertEqual(progress['status'],'interrupted')
        self.assertNotIn('pending_forceload',progress)
        bridge.fail=None
        bridge.calls=[]
        result=execute(plan,bridge,FOLDER,self.progress,resume=True)
        self.assertEqual(result['status'],'complete')
        self.assertFalse(any('minecraft:air' in c for c in bridge.writes()))
        self.assertTrue(any('minecraft:stone' in c for c in bridge.writes()))
        self.assertTrue(all('execute if score #delivery ysd1delivery matches ' in c for c in bridge.writes()
                            if not c.startswith('scoreboard players get')))
        bridge.calls=[]
        with self.assertRaisesRegex(ValueError,'interrupted'):
            execute(plan,bridge,FOLDER,self.progress,resume=True)
        self.assertEqual(bridge.writes(),[])

    def test_world_switch_during_job_blocks_later_commands(self):
        bridge=FakeBridge()
        original=bridge.call
        checks=0
        def switch(method,path,params=None):
            nonlocal checks
            if path=='/client/world':
                checks+=1
                if checks==3:bridge.folder='dev1'
            return original(method,path,params)
        bridge.call=switch
        with self.assertRaisesRegex(RuntimeError,'Exact new'):
            execute(export_plan(self.volume(),[]),bridge,FOLDER,self.progress)
        # Only the guard setup and the first bounded forceload preceded switch.
        self.assertFalse(any(' run fill ' in c or ' run setblock ' in c for c in bridge.writes()))
        progress=json.loads(self.progress.read_text())
        self.assertEqual(progress['status'],'interrupted')
        self.assertIn('cleanup_error',progress)


if __name__=='__main__':
    unittest.main()
