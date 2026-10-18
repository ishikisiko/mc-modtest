"""Whole-building translation, level streets, bounded backup and refusal safety."""
import copy
from pathlib import Path
import unittest
from unittest.mock import patch

from tools.yunshan import build_district as city
from tools.yunshan.fit_natural import config_data,plan_fit,metadata,source_core,terrain_commands
from tools.yunshan.flat_fallback import select_with_fallback
from tools.yunshan.natural_terrain import evaluate_site,read_settings
from tools.tests.test_yunshan_natural_fit import synthetic_survey

ROOT=Path(__file__).resolve().parents[2]


class FlatTownTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.flat=city.realize(ROOT/'blueprints/yunshan/district_flat_fallback.json')
        cls.original=city.realize(ROOT/'blueprints/yunshan/district_realization_v2.json')

    def test_all_streets_approaches_and_old_house_entries_share_one_level(self):
        d=self.flat;report=city.validate_blocks(d[4],d[7],d[8],d[9],d[10],d[11],d[2]['world_offset'])
        self.assertTrue(report['passed'],[c for c in report['checks'] if not c['passed']])
        self.assertEqual(set(d[7].values()),{39})
        self.assertEqual({b['approach'][1] for b in d[8]},{69})
        self.assertEqual({h['door'][1] for h in d[9]},{69})
        self.assertEqual((len(d[8]),len(d[9])),(4,70))
        self.assertGreater(len(set(self.original[7].values())),1)
        for route in d[11]['routes'].values():self.assertEqual({p[2] for p in route},{39})

    def test_all_house_cells_are_rigid_vertical_translations(self):
        old={h['id']:h for h in self.original[9]}
        for house in self.flat[9]:
            previous=old[house['id']];dy=house['door'][1]-previous['door'][1]
            translated={(x,y+dy,z):state for (x,y,z),state in previous['_expected'].items()}
            self.assertEqual(house['_expected'],translated,house['id'])
            for position,state in translated.items():
                self.assertEqual(self.flat[4].get(position),city.canonical(state))

    def test_new_building_roofs_interiors_and_intrinsic_steps_are_unchanged(self):
        for old,new in zip(self.original[8],self.flat[8]):
            self.assertEqual(old['size'],new['size'])
            self.assertEqual(old['world_min'][::2],new['world_min'][::2])
            self.assertEqual(old['threshold_world_y']-old['world_min'][1],
                             new['threshold_world_y']-new['world_min'][1])
            lo=old['world_min'];target=new['world_min'];w,h,d=old['size']
            for y in range(h):
                for z in range(d):
                    for x in range(w):
                        self.assertEqual(self.original[4].get((lo[0]+x,lo[1]+y,lo[2]+z)),
                                         self.flat[4].get((target[0]+x,target[1]+y,target[2]+z)),new['key'])

    def test_flat_route_avoids_retained_well_instead_of_erasing_it(self):
        d=self.flat;well=(2931,69,316)
        self.assertEqual(d[4].get(well),'minecraft:cobblestone')
        route=d[11]['routes']['smithy']
        self.assertNotIn([well[0]-2995,well[2]+30,39],route)

    def test_flat_city_envelope_and_surface_clear_are_level_with_native_outer_boundary(self):
        cfg=config_data(ROOT/'blueprints/yunshan/natural_fit_flat.json')
        data=synthetic_survey()
        for key in ('artificial','hazard','surface_structure'):data[key]=[False]*len(data['ground_y'])
        candidate={'id':'flat-fixture','origin':[0,0],
                                         'rotation_quarters':0,'water_surface':63,'dy':-5}
        result=plan_fit(data,candidate,cfg);self.assertTrue(result['validation']['passed'],result['validation'])
        self.assertEqual({result['after'][k] for k in result['core']},{64})
        self.assertEqual(result['metrics']['boundary_delta'],0)
        self.assertEqual(result['metrics']['water_leaks'],0)
        self.assertLessEqual(result['metrics']['maximum_new_step'],2)
        original_core,_=source_core(self.original,config_data())
        flat_core,wet=source_core(self.flat,cfg)
        self.assertTrue(original_core<flat_core)
        self.assertFalse(flat_core&wet)
        # Put a tall native canopy over a protected flat city column: clearing
        # must reach its actual top, even above the template's roof/air volume.
        k=next(iter(result['core']));x,z=result['survey'].pos(k)
        result['survey'].surface[k]=160
        clears=[op for op in terrain_commands(result) if op['command'].endswith(' minecraft:air')
                and op['world_min'][0]<=x<=op['world_max'][0]
                and op['world_min'][2]<=z<=op['world_max'][2]]
        self.assertTrue(any(op['world_min'][1]<=64 and op['world_max'][1]>=160 for op in clears))
        meta=metadata(result)
        self.assertEqual(meta['street_height_range'],[64,64])
        self.assertEqual(meta['building_approach_heights'],[64]*4)

    def test_dry_flat_site_uses_native_height_but_keeps_existing_water_anchor(self):
        profile=read_settings();profile.update(dry_height_policy='median_ground',sample_stride=1)
        core={'size':[4,4,4],'ground_y':[69]*16,'water_surface':68,'dry_ground_above_water':1}
        data={'window':[0,0,4,4],'width':4,'depth':4,'ground_y':[100]*16,'water_y':[None]*16}
        candidate=evaluate_site(data,[0,0],0,core,profile)
        self.assertEqual((candidate['water_surface'],candidate['dy']),(99,31))
        self.assertEqual(candidate['metrics']['mean_earthwork'],0)
        data['water_y']=[101]*16
        self.assertEqual(evaluate_site(data,[0,0],0,core,profile)['water_surface'],101)

    def test_native_feature_outside_city_is_preserved_and_road_width_avoids_it(self):
        cfg=config_data(ROOT/'blueprints/yunshan/natural_fit_flat.json');data=synthetic_survey()
        for key in ('artificial','hazard','surface_structure'):data[key]=[False]*len(data['ground_y'])
        x0,z0,_,_=data['window'];width=data['width'];position=(-8,260)
        index=(position[1]-z0)*width+position[0]-x0
        data['hazard'][index]=True;data['surface_structure'][index]=True
        result=plan_fit(data,{'id':'preserved-feature','origin':[0,0],'rotation_quarters':0,'water_surface':63,'dy':-5},cfg)
        self.assertTrue(result['validation']['passed'],result['validation'])
        self.assertIn(index,result['reserved'])
        self.assertTrue(all(not result['changed'][k] and result['after'][k]==data['ground_y'][k] for k in result['reserved']))
        self.assertFalse(result['path_clear']&result['reserved'])
        for operation in terrain_commands(result):
            lo,hi=operation['world_min'],operation['world_max']
            self.assertFalse(lo[0]<=position[0]<=hi[0] and lo[2]<=position[1]<=hi[2])


class FlatBackupFlowTests(unittest.TestCase):
    def report(self,status,selected=None):
        return {'status':status,'selected':selected,'manual_refinements_used':False,'attempts':[]}

    def test_primary_success_does_not_construct_or_search_flat_variant(self):
        selected={'origin':[1,2]};report=self.report('selected',selected);fit=object();data=object()
        with (patch('tools.yunshan.flat_fallback.select_site',return_value=(report,data,fit)) as search,
              patch('tools.yunshan.flat_fallback.source_core') as flat):
            result,actual,model=select_with_fallback(7,lambda *_:None)
            self.assertEqual(search.call_count,1);flat.assert_not_called()
            self.assertIs(actual,data);self.assertIs(model,fit)
            self.assertEqual(result['selected_variant'],'terraced')

    def test_refusal_triggers_backup_reuses_snapshot_and_keeps_protection_gates(self):
        profile=read_settings();before=copy.deepcopy(profile);calls=[];snap={'actual':'immutable fixture'}
        def provider(window,label,settings):calls.append((window,label));return snap
        def search(seed,read,settings,**kwargs):
            data=read([0,0,384,384],'coarse-0',settings)
            if 'core' not in kwargs:return self.report('no_suitable_site_within_budget'),None,None
            self.assertIs(data,snap)
            self.assertEqual(settings['automatic']['gates'],profile['automatic']['gates'])
            self.assertEqual(settings['dry_height_policy'],'median_ground')
            return self.report('selected',{'origin':[3,4]}),data,{'flat':True}
        with (patch('tools.yunshan.flat_fallback.select_site',side_effect=search),
              patch('tools.yunshan.flat_fallback.source_core',return_value={'flat':True})):
            report,data,fit=select_with_fallback(7,provider,profile)
        self.assertEqual(len(calls),1)
        self.assertEqual(report['selected_variant'],'flat_fallback_v1')
        self.assertIsNone(report['primary']['selected'])
        self.assertEqual(report['trigger'],'primary_no_suitable_site_within_budget')
        self.assertEqual(len(report['survey_reuse']),1)
        self.assertEqual(profile,before)

    def test_invalid_data_and_program_errors_do_not_trigger_backup(self):
        with (patch('tools.yunshan.flat_fallback.select_site',side_effect=ValueError('missing FULL chunk')),
              patch('tools.yunshan.flat_fallback.source_core') as flat):
            with self.assertRaisesRegex(ValueError,'FULL'):select_with_fallback(7,lambda *_:None)
            flat.assert_not_called()

    def test_backup_failure_remains_a_refusal_without_chosen_site(self):
        with (patch('tools.yunshan.flat_fallback.select_site',side_effect=[
                (self.report('no_suitable_site_within_budget'),None,None),
                (self.report('no_suitable_site_within_budget'),None,None)]) as search,
              patch('tools.yunshan.flat_fallback.source_core',return_value={'flat':True})):
            report,data,fit=select_with_fallback(7,lambda *_:None)
        self.assertEqual(search.call_count,2)
        self.assertEqual(report['status'],'no_suitable_site_within_budget')
        self.assertIsNone(report['selected']);self.assertIsNone(data);self.assertIsNone(fit)


if __name__=='__main__':unittest.main()
