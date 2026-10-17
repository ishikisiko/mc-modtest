"""Natural fit seams, original core rotation, sparse air and outgoing access."""
from pathlib import Path
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from yunshan import fit_natural as fit
from yunshan.game_export import read_typed,plain
from yunshan.build_district import Volume


def synthetic_survey():
    x0,z0,x1,z1=-96,-96,306,361;n=(x1-x0)*(z1-z0);ground=[63]*n
    return {'window':[x0,z0,x1,z1],'width':x1-x0,'depth':z1-z0,'ground_y':ground,
            'surface_y':ground[:],'water_y':[None]*n,'ground_palette':['minecraft:grass_block'],
            'ground_index':[0]*n,'provenance':{'status':'synthetic_test_only'}}


class NaturalFitTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.survey=synthetic_survey();cls.candidate={'id':'fixture','origin':[0,0],
            'rotation_quarters':0,'water_surface':68,'dy':0}
        cls.result=fit.plan_fit(cls.survey,cls.candidate)

    def test_natural_boundary_lake_and_untouched_exit(self):
        r=self.result
        self.assertTrue(r['validation']['passed'],r['validation'])
        self.assertEqual(r['metrics']['boundary_delta'],0)
        self.assertEqual(r['metrics']['water_leaks'],0)
        self.assertGreater(r['metrics']['peripheral_art_columns_discarded'],20000)
        tail=r['route'][-6:]
        self.assertTrue(all(not r['changed'][k] for k in tail))
        self.assertTrue(all(abs(r['after'][a]-r['after'][b])<=1 for a,b in zip(r['route'],r['route'][1:])))

    def test_irregular_blend_leaves_native_corners_and_preserves_source_core(self):
        r=self.result;s=r['survey'];t=r['transform'];source_levels=r['source'][5]
        for k,(x,z) in r['core'].items():
            if r['source'][6][x-2995,z+30]!='bridge':
                self.assertEqual(r['after'][k],source_levels[x-2995,z+30]+30+t.dy)
        for x,z in [(s.x0,s.z0),(s.x0,s.z1-1),(s.x1-1,s.z0),(s.x1-1,s.z1-1)]:
            k=s.index(x,z);self.assertFalse(r['changed'][k]);self.assertEqual(r['after'][k],63)
        # Multiple unchanged gaps survive inside the overall changed bbox;
        # the result is neither a rectangular slab nor a filled circular mound.
        self.assertGreater(sum(not changed for changed in r['changed']),30000)

    def test_new_steep_edges_are_gated_and_historical_walls_are_reported(self):
        r=self.result;seams=r['seams']
        self.assertTrue(r['validation']['checks']['no_new_steep_seams'])
        self.assertEqual(seams['new_step_excess_count'],0)
        self.assertLessEqual(seams['maximum_new_step'],2)
        self.assertTrue(seams['existing_retaining_edges'])
        for edge in seams['existing_retaining_edges']:
            self.assertGreater(edge['source_step'],2)
            self.assertLessEqual(edge['step'],edge['source_step'])
        # These previously became 6/6/6/4-block new drops despite original
        # source pairs being level or at most two blocks apart.
        for a,b in [((90,238),(90,239)),((43,66),(43,67)),
                    ((60,13),(61,13)),((59,129),(59,130))]:
            self.assertLessEqual(abs(r['after'][r['survey'].index(*a)]-
                                     r['after'][r['survey'].index(*b)]),2,(a,b))

    def test_envelope_projection_is_stable_and_rejects_inconsistent_fixed_levels(self):
        survey=fit.Survey({'window':[0,0,3,1],'width':3,'depth':1,'ground_y':[0,0,0],
                          'surface_y':[0,0,0],'water_y':[None]*3,
                          'ground_palette':['minecraft:stone'],'ground_index':[0]*3})
        limits={'horizontal':[2,2,None],'vertical':[None]*3,'blocked':set()}
        cfg=fit.config_data()
        first,report=fit.project_blend(survey,[0,10.,0],set(),set(),set(),[False,True,False],limits,0,cfg)
        self.assertTrue(report['passed']);self.assertEqual(first,[0,2,0])
        second,report=fit.project_blend(survey,first,set(),set(),set(),[False,True,False],limits,0,cfg)
        self.assertTrue(report['passed']);self.assertEqual(second,first)
        _,report=fit.project_blend(survey,[0,10,0],{0,1,2},set(),set(),[False]*3,limits,0,cfg)
        self.assertFalse(report['passed']);self.assertGreater(report['infeasible_columns'],0)

    def test_wrong_water_translation_and_missing_survey_margin_fail(self):
        c={**self.candidate,'dy':1}
        with self.assertRaisesRegex(ValueError,'align original lake'):fit.plan_fit(self.survey,c)
        c={**self.candidate,'origin':[300,300]}
        with self.assertRaisesRegex(ValueError,'survey margin missing'):fit.plan_fit(self.survey,c)

    def test_rotation_of_continuous_points_preserves_block_centers(self):
        for q in range(4):
            t=fit.Transform({**self.candidate,'rotation_quarters':q,'origin':[-513,-721]})
            integer=t.point([2910,78,190]);center=t.point([2910.5,78,190.5],continuous=True)
            self.assertEqual(center,[integer[0]+.5,integer[1],integer[2]+.5])

    def test_region_fills_are_bounded_and_untouched_columns_absent(self):
        r=self.result;s=r['survey'];commands=list(fit.terrain_commands(r))
        self.assertGreater(len(commands),0)
        for command in commands:
            lo,hi=command['world_min'],command['world_max']
            self.assertLessEqual((hi[0]-lo[0]+1)*(hi[1]-lo[1]+1)*(hi[2]-lo[2]+1),32768)
            self.assertLess(len(command['command']),1200)
            self.assertTrue(r['changed'][s.index(lo[0],lo[2])])
            self.assertTrue(r['changed'][s.index(hi[0],hi[2])])

    def test_sparse_core_tiles_keep_signs_air_and_untouched_holes(self):
        volume=Volume((0,62,0),(2,65,2));volume.set((0,62,0),'spruce_log[axis=x]')
        volume.set((0,63,0),'oak_wall_sign[facing=north]')
        cfg=fit.config_data();candidate={**self.candidate,'origin':[100,200],'rotation_quarters':1}
        t=fit.Transform(candidate,volume.minimum,volume.size)
        survey=fit.Survey({'window':[96,192,112,208],'width':16,'depth':16,'ground_y':[63]*256,
                          'surface_y':[63]*256,'water_y':[None]*256,'ground_palette':['minecraft:grass_block'],'ground_index':[0]*256})
        core={survey.index(*t.xz(x,z)):(x,z) for x,z in [(0,0),(2,2)]}
        source=[None]*12;source[4]=volume;source[10]=[{'pos':[0,63,0],'messages':['云山','','','']}]
        r={'cfg':cfg,'transform':t,'source':source,'core':core,'survey':survey,'candidate':candidate}
        with tempfile.TemporaryDirectory() as tmp:
            tiles,count=fit.export_core_tiles(r,Path(tmp));self.assertEqual(count,8)
            blocks={};texts=[]
            for tile in tiles:
                data=plain(read_typed(Path(tmp)/tile['nbt'])[1])
                for block in data['blocks']:
                    p=tuple(tile['world_min'][i]+block['pos'][i] for i in range(3));state=data['palette'][block['state']]
                    blocks[p]=state
                    if 'nbt' in block:texts.extend(block['nbt']['front_text']['messages'])
            self.assertEqual(len(blocks),8)
            self.assertEqual(blocks[102,62,200]['Properties']['axis'],'z')
            self.assertEqual(blocks[102,63,200]['Properties']['facing'],'east')
            self.assertEqual(blocks[102,64,200]['Name'],'minecraft:air')
            self.assertNotIn((101,62,201),blocks)
            self.assertIn('云山',texts[0])


if __name__=='__main__':unittest.main()
