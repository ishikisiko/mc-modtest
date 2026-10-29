"""中州完整覆盖、真实模板归属、局部施工和有界缓存的针对性检查。"""
import copy
import os
from pathlib import Path
import tempfile
import unittest

from .generate import ROOT, read, rect, perimeter
from .central import TileCache, prepare_world, source_surfaces, verify_applied_world, road_network_audit, _segment_columns
from .generate import digest
from .export_minecraft import build


class CentralCacheTests(unittest.TestCase):
    def test_eviction_preserves_mutations_and_bounds_memory(self):
        root=ROOT/'reports/all-capital-blueprints-20261030/zhongzhou'
        root.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root) as folder:
            cache=TileCache(folder,2)
            cache[0,0]={(1,2,3):'minecraft:stone'}
            cache[0,0][2,2,3]='minecraft:spruce_planks'
            cache[1,0]={};cache[2,0]={}
            self.assertLessEqual(len(cache.hot),2)
            self.assertEqual('minecraft:spruce_planks',cache[0,0][2,2,3])
            self.assertEqual(3,len(cache))
            del cache[0,0]
            self.assertNotIn((0,0),cache)


class CentralPlanTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec=read(ROOT/'blueprints/province_capitals/blueprint_plan/zhongzhou.json')
        cls.base=Path(os.environ.get('MYVILLAGE_CENTRAL_BASE',ROOT/'build/generated/province_capitals/resources')).resolve()
        cls.planner,cls.world,cls.data=build(cls.spec,cls.base)

    def test_every_active_building_has_its_true_footprint(self):
        p,d=self.planner,self.data
        expected={b['id'] for b in p.geometry.rows}
        self.assertEqual(expected,{b['id'] for b in d['plots']})
        self.assertEqual(expected,{b['id'] for b in self.world.manifest['buildings']})
        for plot in d['plots']:
            restored={(x,z) for z,x0,x1 in plot['source_footprint_runs'] for x in range(x0,x1+1)}
            self.assertEqual(p.geometry.world.footprints[plot['id']],restored,plot['id'])
        self.assertFalse(d['checks']['issues'])
        self.assertEqual([1200,320,1440],d['dimensions'])
        self.assertEqual(4,d['checks']['source_isolated_pavilions'])
        structural=verify_applied_world(self.world,d)
        self.assertGreater(structural['preserved_structural_cells'],0)
        d['central_structural_checks']=structural

    def test_all_source_courts_and_lot_pads_have_explicit_owners(self):
        d=self.data;p=self.planner
        surfaces=source_surfaces(p.geometry)
        self.assertEqual({f'source_court_{i}' for i,_,_ in surfaces},
                         {r['id'] for r in d['courtyards']+d['source_lot_pads']})
        for court in d['courtyards']+d['source_lot_pads']:
            self.assertIn(court['access'],('private','public'))
            self.assertIn(court['owner'],p.buildings)
            self.assertTrue(court['reason'])
            if court['access']=='private':
                cells={(x,z) for z,x0,x1 in court['cell_runs'] for x in range(x0,x1+1)}
                self.assertFalse(cells & p.declared_road_columns)
        self.assertEqual(len([x for x in surfaces if x[2]]),len(d['source_lot_pads']))

    def test_private_gardens_reach_real_interiors_without_public_crossings(self):
        p=self.planner
        for g in self.data['gardens']:
            if g['access']!='private':continue
            with self.subTest(g=g['id']):
                cells=rect(g['bounds']) | {(x,z) for x,y,z in g['path']}
                self.assertFalse(cells & p.declared_road_columns)
                self.assertFalse(cells & p.model.court_columns.keys())
                self.assertEqual(g['owner'],g['door']['owner'])
                self.assertGreater(g['door']['reachable_interior_cells'],0)
                self.assertTrue(p.clear(tuple(g['door']['inside']),2))
                self.assertIn((g['door']['inside'][0],g['door']['inside'][2]),p.model.footprints[g['owner']])
        self.assertGreater(self.data['checks']['private_gardens'],0)
        self.assertGreater(self.data['checks']['public_gardens'],0)

    def test_native_routes_have_support_clearance_and_unit_steps(self):
        for g in self.data['gardens']:
            for route in (g['path'],g['internal_path']):
                self.assertTrue(route,g['id'])
                for p in route:self.assertTrue(self.world.walkable(tuple(p)),(g['id'],p))
                for a,b in zip(route,route[1:]):
                    self.assertEqual(1,abs(a[0]-b[0])+abs(a[2]-b[2]))
                    self.assertLessEqual(abs(a[1]-b[1]),1)
        for p in self.planner.original_road_walkpoints:self.assertTrue(self.world.walkable(p),p)
        self.world.verify_life()

    def test_local_earthwork_is_declared_and_bounded(self):
        allowed=set()
        for g in self.data['gardens']:
            allowed|=rect(g['bounds']) | {(x,z) for x,y,z in g['path']}
        for row in self.spec.get('road_adjustments',[]):
            raised={tuple(r['column']) for r in row.get('raised_columns',[])}
            for segment in row['segments']:
                allowed |= _segment_columns(segment)-raised
        before=self.planner.original_terrain
        actual={(i%self.world.width,i//self.world.width) for i,(a,b) in enumerate(zip(before,self.world.terrain)) if a!=b}
        declared={tuple(e['column']) for e in self.data['earthwork']}
        self.assertLessEqual(actual,declared)
        self.assertLessEqual(declared,allowed)
        for e in self.data['earthwork']:
            self.assertLessEqual(abs(e['air_y']-e['before_air_y']),self.spec['rules']['local_earthwork_limit'])

    def test_all_original_road_failures_have_explicit_verified_replacements(self):
        data=self.data
        original={tuple(p) for p in data['original_road_unavailable']}
        self.assertEqual(self.spec['road_rules']['source_failed_cell_count'],len(original))
        self.assertEqual(self.spec['road_rules']['original_failure_sha256'],digest(data['original_road_unavailable']))
        records=[(*p,r['route_id']) for r in data['road_adjustments'] for p in r['retired_failed_cells']]
        self.assertEqual(len(records),len(set(records)))
        self.assertEqual(original,set(records))
        self.assertFalse(self.planner.road_unavailable)
        self.assertEqual('pass',data['road_adjustment_checks']['status'])
        self.assertEqual('pass',data['road_audit']['status'])

    def test_raised_decks_keep_the_original_heightfield_and_have_piers(self):
        count=0
        for row in self.data['road_adjustments']:
            for record in row.get('raised_columns',[]):
                count+=1;x,z=record['column']
                self.assertEqual(self.planner.original_terrain[z*self.world.width+x],self.world.terrain[z*self.world.width+x])
            for pier in row.get('piers',[]):
                self.assertTrue(pier['blocks'])
                for pos in pier['blocks']:self.assertEqual(self.spec['minecraft']['materials']['path'],self.world.get(tuple(pos)))
        self.assertGreater(count,0)

    def test_true_ground_floor_is_not_exempted_by_air_position_metadata(self):
        world=self.world;pos=tuple(self.planner.geometry.rows[0]['position'])
        self.assertIn(pos,world.central_structural_cells)
        class BrokenFloor:
            def __getattr__(self,name):return getattr(world,name)
            def get(self,p):return 'minecraft:air' if p==pos else world.get(p)
        with self.assertRaisesRegex(ValueError,'真实模板实体'):
            verify_applied_world(BrokenFloor(),self.data)

    def test_all_street_entries_connect_from_the_real_city_gate(self):
        result=road_network_audit(self.world,self.planner)
        self.data['road_network_audit']=result
        self.assertFalse(result['unreachable_entries'],str(result['unreachable_entries'][:8]))
        primary=self.data['primary_road_network_audit']
        self.assertEqual(638,primary['connected_access_entries'])
        self.assertEqual('not_used',primary['door_interaction'])

    def test_approved_west_entry_reaches_all_source_interior_cells(self):
        doors=self.data['entry_doors'];self.assertEqual(1,len(doors))
        door=doors[0];self.assertEqual([381,170,733],door['position'])
        self.assertNotIn('garden_id',door)
        proof=self.data['central_structural_checks']['entry_doors'][0]
        self.assertEqual(25,proof['source_reachable_cells'])
        self.assertEqual(25,proof['source_cells_reached'])
        self.assertEqual(10,proof['outside_route_points'])
        self.assertTrue(proof['original_entrance_reached'])

    def test_private_takeover_of_public_forecourt_is_rejected(self):
        p=self.planner;private=next(g for g in self.data['gardens'] if g['access']=='private')
        bad=copy.deepcopy(private)
        target=next(c for c in self.data['courtyards'] if c['name']=='下宫前庭')
        bad['bounds']=target['bounds']
        with self.assertRaisesRegex(ValueError,'公共'):
            p.garden_path(bad,{'access':'private','owner':private['owner']})

    def test_stale_base_identity_is_rejected_before_geometry(self):
        manifest=copy.deepcopy(self.world.manifest)
        name=next(iter(manifest['source']['hashes']))
        manifest['source']['hashes'][name]='0'*64
        fake=type('ManifestOnly',(),{'manifest':manifest})()
        with self.assertRaisesRegex(ValueError,'源身份过期'):
            prepare_world(self.spec,fake)


if __name__=='__main__':unittest.main()
