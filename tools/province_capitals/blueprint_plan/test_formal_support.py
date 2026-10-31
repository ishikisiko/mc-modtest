"""七府正式重排的共用验收边界；不把旧坐标当成新布局断言。"""
import unittest
from .generate import ROOT,generate,read,rect


class FormalCityCase(unittest.TestCase):
    region=None

    @classmethod
    def setUpClass(cls):
        if cls.region is None:raise unittest.SkipTest('共用正式蓝图断言')
        cls.planner,cls.data=generate(ROOT/f'blueprints/province_capitals/blueprint_plan/{cls.region}.json')

    @classmethod
    def tearDownClass(cls):
        cls.planner=None;cls.data=None

    def test_only_declared_residences_are_retired_and_native_sizes_stay(self):
        p,d=self.planner,self.data;original={b['id']:b for b in p.source_city['buildings']}
        actual={v['id']:v['building'] for v in d['plots']}
        self.assertEqual((set(original)-p.removed)|{'lord_manor'},set(actual))
        self.assertEqual(p.layout['expected']['houses_after'],d['checks']['houses'])
        for bid,b in actual.items():
            if bid=='lord_manor':continue
            self.assertEqual(original[bid]['size'],b['size'],bid)
            self.assertEqual(original[bid]['family'],b['family'],bid)
            self.assertEqual(original[bid]['role'],b['role'],bid)
            if p.parcels[bid]['status']=='retained':self.assertEqual(original[bid]['position'],b['position'],bid)
        for bid in p.layout['retired_buildings']:
            self.assertEqual('house',original[bid]['role'])
            self.assertNotIn(original[bid]['family'],('shop_house','stilt_longhouse'))
        self.assertEqual(set(actual),{a['building_id'] for a in d['building_access']})

    def test_every_road_width_and_final_access_has_real_support_and_clearance(self):
        p,d=self.planner,self.data
        self.assertEqual('pass',d['checks']['status']);self.assertEqual([],d['construction_audit']['losses'])
        for road in d['roads']:
            self.assertTrue(road['surface_columns'],road['id'])
            for x,y,z in road['surface_columns']:
                self.assertTrue(p.walkable((x,y,z)),(road['id'],x,y,z))
                self.assertTrue(p.clear((x,y,z),p.cfg['headroom']))
        for route in d['building_access']:
            limit=p.rules['public_access_max_length'] if route['building_id']=='lord_manor' else p.model.geometry['max_access_length']
            self.assertLessEqual(route['length'],limit)
            self.assertEqual(len(route['points']),len({(x,z) for x,_,z in route['points']}))
            for pos in route['points']:self.assertTrue(p.walkable(tuple(pos)),(route['building_id'],pos))
        self.assertEqual('not_verified',d['checks']['game_runtime'])
        self.assertEqual('not_verified',d['checks']['owner_acceptance'])

    def test_private_ownership_and_public_court_functions_survive_replanning(self):
        p,d=self.planner,self.data;gardens={g['id']:g for g in d['gardens']}
        self.assertEqual({g['id'] for g in p.layout['gardens']},set(gardens))
        courts={c['id']:c for c in d['courtyards']}
        self.assertEqual({c['id'] for c in p.source_city['courtyards']},set(courts))
        for old in p.source_city['courtyards']:
            self.assertEqual(old['size'],courts[old['id']]['size'])
            expected_kind=old.get('kind',p.model.geometry['default_court_kind'])
            actual=next(c for c in p.model.diagnostics['courtyards'] if c['id']==old['id'])
            self.assertEqual(expected_kind,actual['kind'])
            self.assertEqual(expected_kind,courts[old['id']].get('kind',p.model.geometry['default_court_kind']))
            self.assertEqual('public',courts[old['id']]['access'])
        for g in gardens.values():
            self.assertTrue(g['reason']);self.assertTrue(g['owner'] or g['steward'])
            self.assertLessEqual(len(g['path'])-1,p.rules[g['access']+'_access_max_length'])
            for path in (g['path'],g['internal_path']):
                for a,b in zip(path,path[1:]):
                    self.assertEqual(1,abs(a[0]-b[0])+abs(a[2]-b[2]));self.assertLessEqual(abs(a[1]-b[1]),1)
                for pos in path:self.assertTrue(p.walkable(tuple(pos)),(g['id'],pos))
            if g['access']=='private':
                self.assertEqual(g['owner'],g['door']['owner'])
                self.assertEqual(g['y'],p.summaries[g['owner']]['position'][1])
                self.assertFalse((rect(g['bounds'])|{(x,z) for x,_,z in g['path']})&set(p.road_field))
                self.assertEqual(list(g['path'][-1]),list(g['door'].get('outside',p.summaries[g['owner']]['entrance'])))
            if g['id'] in courts:self.assertEqual([],g['fixtures']);continue
            expected=p.spec['garden_programs'][g['kind']]['fixtures']
            self.assertEqual([f['kind'] for f in expected],[f['kind'] for f in g['fixtures']])
            self.assertEqual([f['size'] for f in expected],[f['size'] for f in g['fixtures']])

    def test_full_manor_and_open_stilt_space_are_preserved(self):
        p,d=self.planner,self.data;w,h,z=p.manor_manifest['size'];q=p.layout['manor']['rotation_quarters'];box=p.layout['manor']['bounds']
        self.assertEqual([z,w] if q%2 else [w,z],[box[2]-box[0],box[3]-box[1]])
        self.assertEqual([w,h,z],d['manor']['size'])
        self.assertGreater(len(p.manor_cells),20000)
        self.assertEqual(p.spec['sources']['manor'],p.layout['manor']['scene'])
        for pos,state in p.manor_cells.items():self.assertEqual(state,p.get(pos))
        open_columns=0
        for (x,z),low in p.manor_lowest.items():
            if (x,z) in p.manor_contacts or low<=p.ground(x,z)+2:continue
            below=p.model.construction.record_at((x,low-1,z))
            self.assertTrue(below is None or below[1]!='府邸基础');open_columns+=1
        self.assertGreater(open_columns,0)
