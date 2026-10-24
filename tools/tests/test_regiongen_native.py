"""Failures which must not be misreported as native region acceptance."""
import unittest
from unittest.mock import patch
from types import SimpleNamespace
from tools.regiongen.native_pack import shortest,compress,boundary_attachments
from tools.regiongen.native_run import vector,door_point,placement_confirmed,Run
from tools.regiongen.native_compare import difference,decode_grid


class RegionNativeTests(unittest.TestCase):
    def test_unreachable_route_rejected(self):
        with self.assertRaises(ValueError):shortest({(0,0,0):set()},(0,0,0),(1,0,0))

    def test_corner_and_stair_rise_preserved(self):
        points=[(0,1,0),(0,2,1),(0,3,2),(1,3,2),(2,3,2)]
        self.assertEqual(compress(points),[(0,1,0),(0,3,2),(2,3,2)])

    def test_door_panel_clearance_for_entry_and_exit(self):
        d={'pos':[5,4,6],'state':'minecraft:spruce_door[facing=west,hinge=left]'}
        self.assertEqual(door_point([5,4,6],[d]),[5.5,4,6.40625])
        self.assertEqual(door_point([6,4,6],[d]),[6.5,4,6.40625])
        d['state']=d['state'].replace('left','right')
        self.assertEqual(door_point([4,4,6],[d]),[4.5,4,6.59375])

    def test_native_state_defaults_do_not_hide_solid_or_facing_errors(self):
        self.assertIsNone(difference('minecraft:grass_block','minecraft:grass_block[snowy=false]'))
        self.assertEqual(difference('minecraft:stone','minecraft:air'),'block_id')
        self.assertEqual(difference('minecraft:stone_stairs[facing=west,half=bottom]','minecraft:stone_stairs[facing=east,half=bottom]'),'property:facing')
        self.assertEqual(difference('minecraft:oak_slab[type=bottom]','minecraft:oak_slab[type=top]'),'property:type')

    def test_only_logged_door_actions_allow_open_changes(self):
        e='minecraft:oak_door[open=false,powered=false]';a='minecraft:oak_door[open=true,powered=false]'
        self.assertEqual(difference(e,a),'property:open');self.assertIsNone(difference(e,a,True))
        self.assertEqual(difference('minecraft:oak_door',a),'property:open')

    def test_corrupt_saved_palettes_rejected(self):
        with self.assertRaises(ValueError):decode_grid({'block_states':{'palette':[{'Name':'minecraft:air'},{'Name':'minecraft:stone'}]}})

    def test_vanilla_template_reply_requires_exact_id_and_origin(self):
        reply='Loaded template "myvillage:river_valley_sample/t_0_0_0" at 0, 48, 0\n'
        self.assertTrue(placement_confirmed(reply,'myvillage:river_valley_sample/t_0_0_0',[0,48,0]))
        self.assertFalse(placement_confirmed(reply,'myvillage:river_valley_sample/other',[0,48,0]))
        self.assertFalse(placement_confirmed(reply,'myvillage:river_valley_sample/t_0_0_0',[0,64,0]))
        self.assertFalse(placement_confirmed('Failed to place template','myvillage:river_valley_sample/t_0_0_0',[0,48,0]))

    def test_delayed_mouse_receipt_does_not_duplicate_delta(self):
        moves=[];reads=[0];yaw=[0];pending=[None]
        def xdo(*args):moves.append(args);pending[0]=yaw[0]+int(args[-2])*.15;reads[0]=0
        def rotation():
            reads[0]+=1
            if pending[0] is not None and reads[0]>=3:yaw[0]=pending[0];pending[0]=None
            return [yaw[0],0]
        run=object.__new__(Run);run.gate=lambda:None;run.rotation=rotation;run.mouse_primed=True;run.event=lambda *a,**kw:None
        run.s=SimpleNamespace(game=SimpleNamespace(xdo=xdo))
        with patch('tools.regiongen.native_run.time.sleep',lambda _:None):run.look(30,0)
        self.assertEqual(len(moves),1)
        self.assertEqual(yaw[0],30)

    def test_cursor_prime_tolerates_only_unmeasured_first_callback_loss(self):
        moves=[];yaw=[0]
        def xdo(*args):
            moves.append(args)
            if len(moves)>1:yaw[0]+=int(args[-2])*.15
        run=object.__new__(Run);run.gate=lambda:None;run.rotation=lambda:[yaw[0],0]
        run.mouse_primed=False;run.event=lambda *a,**kw:None
        run.s=SimpleNamespace(game=SimpleNamespace(xdo=xdo))
        with patch('tools.regiongen.native_run.time.sleep',lambda _:None):run.look(30,0)
        self.assertEqual([int(m[-2]) for m in moves],[12,12,-12,200])
        self.assertEqual(yaw[0],30)

    def test_attachment_support_crosses_export_boundary(self):
        from yunshan.build_district import Volume
        v=Volume((0,0,0),(2,3,2));v.set((1,2,1),'minecraft:stone')
        expected={'a':{(1,1,1):'minecraft:lantern[hanging=true]'}}
        self.assertEqual(boundary_attachments(v,expected,2)[0]['support']['pos'],(1,2,1))
        v.set((1,2,1),'minecraft:air')
        with self.assertRaises(ValueError):boundary_attachments(v,expected,2)

    def test_bad_readback_rejected(self):
        self.assertEqual(vector('CaptureDev has the following entity data: [1.5d, 68.0d, -2.0d]',3),[1.5,68,-2])
        for s in ['No entity was found','[1,2]','[nan,0,0]']:
            with self.assertRaises(ValueError):vector(s,3)

if __name__=='__main__':unittest.main()
