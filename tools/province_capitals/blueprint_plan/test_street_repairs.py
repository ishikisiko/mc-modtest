"""本轮真实缺陷：平地不得无谓下挖，室外不铺满木板，路幅和挂接必须完整。"""
import json
from pathlib import Path
from types import SimpleNamespace
import unittest

from .manor_relayout import ManorPlanner
from .road_surfaces import RoadSurfaces
from province_capitals.regional.v2.buildings import Building
from province_capitals.regional.v2.city import Capital,load_city
from tools.lord_manors.construction import prepare

ROOT=Path(__file__).resolve().parents[3]


class AccessGradingTests(unittest.TestCase):
    def planner(self,ground):
        p=object.__new__(ManorPlanner);p.width=len(ground);p.depth=3;p.height=32
        p.cfg={'headroom':3};p.rules={'local_earthwork_limit':3}
        p.native_rules=json.loads((ROOT/'blueprints/province_capitals/blueprint_plan/native_rules.json').read_text())
        p.access_field={};p.road_field={};p.core=set();p.building_columns={};p.private_owner={}
        p.public_garden_columns=set();p.floor_columns=set();p.summaries={};p.goal_distances={}
        p.ground=lambda x,z:ground[x];p.is_wet=lambda x,z:False
        p.get=lambda pos:'minecraft:dirt' if pos[1]<=p.ground(pos[0],pos[2]) else 'minecraft:air'
        p.model=SimpleNamespace(construction=SimpleNamespace(record_at=lambda pos:None))
        return p

    def test_clear_nine_column_flat_route_stays_at_surface_height(self):
        p=self.planner([10]*9)
        route=p.path((0,11,1),{(8,11,1)},limit=8)
        self.assertEqual(9,len(route))
        self.assertEqual([11]*9,[row[1] for row in route])

    def test_real_height_changes_remain_walkable_without_flattening_the_hill(self):
        ground=[10,10,11,11,12,12,12,12,12];p=self.planner(ground)
        route=p.path((0,11,1),{(8,13,1)},limit=8)
        self.assertEqual([g+1 for g in ground],[row[1] for row in route])
        self.assertTrue(all(abs(a[1]-b[1])<=1 for a,b in zip(route,route[1:])))

    def test_length_and_foreign_property_limits_are_preserved(self):
        p=self.planner([10]*9)
        self.assertIsNone(p.path((0,11,1),{(8,11,1)},limit=7))
        p.building_columns={(4,z):'someone_else' for z in range(3)}
        self.assertIsNone(p.path((0,11,1),{(8,11,1)},owner='house',limit=12))

    def test_cheaper_longer_prefix_cannot_discard_the_only_route_within_length_budget(self):
        p=self.planner([10]*9)
        p.ground=lambda x,z:9 if z==1 and x in (2,3) else 10
        p.access_field={(x,1):11 for x in range(4,8)}
        route=p.path((0,11,1),{(8,11,1)},limit=8)
        self.assertIsNotNone(route)
        self.assertEqual(9,len(route));self.assertEqual([8,11,1],route[-1])

    def test_existing_raised_deck_is_walkable_without_treating_it_as_new_earthwork(self):
        p=self.planner([10]*9);p.floor_columns={(x,1) for x in range(3)}
        p.get=lambda pos:'minecraft:dark_oak_planks' if (pos[0],pos[2]) in p.floor_columns and pos[1]==14 else 'minecraft:dirt' if pos[1]<=10 else 'minecraft:air'
        route=p.path((0,15,1),{(8,11,1)},limit=8)
        self.assertIsNotNone(route);self.assertEqual(9,len(route))
        self.assertEqual([15,15,15],[v[1] for v in route[:3]])
        self.assertTrue(all(abs(a[1]-b[1])<=1 for a,b in zip(route,route[1:])))


class ExteriorFloorTests(unittest.TestCase):
    def template(self,region,family):
        city=load_city(region);capital=Capital(city)
        source=next(b for b in city['buildings'] if b['family']==family)
        result=Building(source,capital.style,city['construction'],capital.library);result.build()
        return result

    def test_ordinary_house_perimeter_is_stone_and_indoor_floor_remains_wood(self):
        b=self.template('lingyue','terrace_house')
        outside={(x,z) for x in range(b.w) for z in range(b.d)}-b.coverage[0]
        self.assertTrue(outside)
        self.assertTrue(all(b.cells[x,-1,z][0]==b.mat('EXTERIOR_FLOOR') for x,z in outside))
        self.assertNotEqual(b.mat('FLOOR_WOOD'),b.mat('EXTERIOR_FLOOR'))
        self.assertTrue(all(b.cells[x,-1,z][0]==b.mat('FLOOR_WOOD') for x,z in b.coverage[0]))

    def test_explicit_stilt_deck_keeps_its_wood_platform(self):
        b=self.template('nanjiang','stilt_longhouse')
        self.assertTrue(b.family['stilt'])
        self.assertTrue(all(b.cells[x,-1,z][0]==b.mat('FLOOR_WOOD') for x in range(b.w) for z in range(b.d)))

    def test_donghuang_uses_finished_wall_and_timber_even_in_vanilla_modset(self):
        c=Capital(load_city('donghuang'))
        self.assertEqual('myvillage:manor_town_warm_plaster',c.material('WALL_MAIN'))
        self.assertEqual('myvillage:manor_town_chestnut_frame',c.material('FRAME_WOOD'))
        self.assertEqual('minecraft:dark_oak_door',c.material('DOOR'))


class RoadSurfaceTests(unittest.TestCase):
    def road(self,region='ximo',axis='z'):
        points=[[0,11,0],[20,11,0]] if axis=='x' else [[0,11,0],[0,11,20]]
        road={'id':'main','width':7,'surface':'main','points':points}
        field={(z,x):11 for x in range(-3,4) for z in range(21)} if axis=='x' else {(x,z):11 for x in range(-3,4) for z in range(21)}
        return RoadSurfaces(region,[road],field,{p:{'main'} for p in field}),field

    def test_all_seven_profiles_cover_full_width_and_have_multiple_surfaces(self):
        signatures=[]
        for region in ('beiyuan','nanjiang','lingyue','ximo','donghai','donghuang','moyu'):
            paving,field=self.road(region);states={paving.state(*p) for p in field}
            self.assertEqual(set(field),set(paving.members));self.assertGreater(len(states),1)
            signatures.append(tuple(sorted(states)))
        self.assertEqual(7,len(set(signatures)))

    def test_wear_follows_the_road_axis(self):
        z,_=self.road(axis='z');x,_=self.road(axis='x')
        self.assertEqual('myvillage:manor_town_worn_sand[facing=north]',z.state(1,1))
        self.assertEqual('myvillage:manor_town_worn_sand[facing=east]',x.state(1,1))

    def test_missing_road_width_cannot_silently_pass(self):
        road={'id':'broken','width':1,'surface':'main','points':[[0,11,0],[0,11,2]]}
        with self.assertRaisesRegex(ValueError,'遗漏'):
            RoadSurfaces('ximo',[road],{(0,0):11,(0,1):11,(0,2):11,(1,1):11},{p:{'broken'} for p in [(0,0),(0,1),(0,2),(1,1)]})


class AllPlaqueMountTests(unittest.TestCase):
    def test_all_twenty_two_plaques_have_actual_mounts_and_clear_text(self):
        rules=json.loads((ROOT/'blueprints/lord_manors/structure_rules.json').read_text());count=0
        for city,config in rules['cities'].items():
            source=json.loads((ROOT/f'blueprints/lord_manors/cities/{city}.json').read_text())
            result,checks=prepare(source,config,rules)
            self.assertEqual(len(result['signs']),len(checks['title_boards']),city)
            count+=len(checks['title_boards'])
        self.assertEqual(22,count)

    def test_undeclared_floating_plaque_is_rejected(self):
        source={'boxes':[[0,0,0,1,1,1,'wood','hall','structure']],'signs':[['匾额',0,4,0,2]]}
        rules={'roof_joint_depth':1,'roof_joint_overlap':1,'sign_clearance':1,'title_mount_roles':['wood']}
        with self.assertRaisesRegex(ValueError,'title_unmounted'):
            prepare(source,{'roof_sheets':[],'hanging_panels':[]},rules)
