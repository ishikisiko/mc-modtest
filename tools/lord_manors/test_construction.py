import copy
from pathlib import Path
import tempfile
import unittest

from tools.lord_manors.construction import bounds,box_at,prepare,check,source_identity
from tools.lord_manors.geometry import cells

RULES={'roof_joint_depth':1,'roof_joint_overlap':1,'sign_clearance':1}


class ConstructionTest(unittest.TestCase):
    def roof_scene(self,delta=1):
        scene={'boxes':[[.5,10.3125,.5,1,.625,1,'roof','hall','roof'],
                        [1.5,10.3125+delta,.5,1,.625,1,'roof','hall','roof']]}
        cfg={'roof_sheets':[{'id':'hall','owner':'hall','boxes':[0,1]}],'hanging_panels':[]}
        return scene,cfg

    def test_real_one_block_rise_gets_a_closed_riser_with_unchanged_roof_tops(self):
        scene,cfg=self.roof_scene();out,result=prepare(scene,cfg,RULES)
        self.assertEqual(1,len(result['roof_joints']))
        self.assertEqual((16,169,0,17,177,16),bounds(out['boxes'][2]))
        self.assertEqual(scene['boxes'],out['boxes'][:2])
        self.assertEqual([],check(out,scene,cfg,RULES,result))

    def test_already_touching_roof_is_not_thickened(self):
        scene,cfg=self.roof_scene(.5);out,result=prepare(scene,cfg,RULES)
        self.assertEqual(scene,out);self.assertEqual([],result['roof_joints'])

    def test_independent_roof_calls_are_not_bridged_across_storeys(self):
        scene,cfg=self.roof_scene(5)
        cfg['roof_sheets']=[{'id':'lower','owner':'hall','boxes':[0]},{'id':'upper','owner':'hall','boxes':[1]}]
        out,result=prepare(scene,cfg,RULES);self.assertEqual(scene,out);self.assertEqual([],result['roof_joints'])

    def test_detached_riser_fails_contact_check(self):
        scene,cfg=self.roof_scene();out,result=prepare(scene,cfg,RULES)
        out['boxes'][2][1]+=1
        self.assertTrue(any(i[0]=='roof_joint_incomplete' for i in check(out,scene,cfg,RULES,result)))

    def test_roof_contract_cannot_silently_omit_tiles(self):
        scene,cfg=self.roof_scene();cfg['roof_sheets'][0]['boxes']=[0]
        with self.assertRaisesRegex(ValueError,'完整且唯一'):prepare(scene,cfg,RULES)

    def banner_scene(self):
        scene={'boxes':[[0,4.25,0,2,.5,4,'roof','residence','roof'],
                        [.5,6,0,1,.25,.25,'bronze','banner','structure'],
                        [1,4.875,0,.125,2,1,'banner','banner','detail']]}
        cfg={'roof_sheets':[{'id':'house','owner':'residence','boxes':[0]}],
             'hanging_panels':[{'id':'banner','box':2,'support':1,'axis':0,'direction':1,'anchor_owner':'residence'}]}
        return scene,cfg

    def test_banner_moves_outside_eaves_and_support_is_anchored(self):
        scene,cfg=self.banner_scene();out,result=prepare(scene,cfg,RULES);row=result['hanging_panels'][0]
        self.assertGreater(row['shift_16ths'],0)
        self.assertGreaterEqual(bounds(out['boxes'][2])[0],bounds(out['boxes'][0])[3]+1)
        self.assertEqual(scene['boxes'][2][1:3],out['boxes'][2][1:3])
        self.assertEqual([],check(out,scene,cfg,RULES,result))

    def test_new_occluder_on_banner_is_rejected(self):
        scene,cfg=self.banner_scene();out,result=prepare(scene,cfg,RULES)
        panel=copy.deepcopy(out['boxes'][2]);panel[6:]=['wall','house','body'];out['boxes'].append(panel)
        self.assertTrue(any(i[0]=='panel_embedded' for i in check(out,scene,cfg,RULES,result)))

    def test_floating_mount_is_rejected(self):
        scene,cfg=self.banner_scene();out,result=prepare(scene,cfg,RULES)
        out['boxes'][result['hanging_panels'][0]['peg']][0]+=3
        self.assertTrue(any(i[0]=='panel_unanchored' for i in check(out,scene,cfg,RULES,result)))

    def test_stale_authored_box_indices_are_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'scene.json';p.write_text('{}')
            with self.assertRaisesRegex(ValueError,'不一致'):source_identity(p,{'source_sha256':'0'*64})

    def test_full_leaf_overlay_does_not_erase_real_roof(self):
        scene={'boxes':[[.5,.75,.5,1,.5,1,'roof','house','roof'],
                        [.5,.5,.5,1,1,1,'leaf','tree','foliage']]}
        pieces=cells(scene,overlay_roles=['leaf'])[0,0,0]
        self.assertEqual({'roof','leaf'},{b[6] for b in pieces})
        self.assertIn((0,8,0,16,16,16,'roof'),pieces)

    def test_full_structural_paint_still_replaces_previous_volume(self):
        scene={'boxes':[[.5,.75,.5,1,.5,1,'roof','house','roof'],
                        [.5,.5,.5,1,1,1,'wall','house','body']]}
        self.assertEqual([(0,0,0,16,16,16,'wall')],cells(scene,overlay_roles=['leaf'])[0,0,0])

    def test_eave_blocked_title_is_lowered_without_losing_its_beam_mount(self):
        scene={'boxes':[[0,5.75,1,4,.5,1,'roof','gate','roof'],
                        [0,5,1.5,4,1.5,1,'wood','gate','body']],
               'signs':[['府门',0,5,.9375,2]]}
        cfg={'roof_sheets':[{'id':'roof','owner':'gate','boxes':[0]}],'hanging_panels':[],
             'title_boards':[{'id':'title','sign':0,'mount':1,'axis':1,'direction':-1}]}
        out,result=prepare(scene,cfg,RULES)
        self.assertLess(out['signs'][0][2],scene['signs'][0][2])
        self.assertEqual(out['signs'][0][3],scene['signs'][0][3])
        self.assertEqual([],check(out,scene,cfg,RULES,result))


if __name__=='__main__':unittest.main()
