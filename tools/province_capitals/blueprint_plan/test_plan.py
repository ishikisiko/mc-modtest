"""正式重排输入、灵岳功能位置与真实门路；不声称游戏验收。"""
import copy
import unittest
from unittest.mock import patch
from pathlib import Path

from .generate import ROOT,read,search_path
from . import manor_relayout
from .test_formal_support import FormalCityCase


class PlanTests(FormalCityCase):
    region='lingyue'

    def test_existing_east_forge_is_preserved_at_its_exact_site(self):
        source=next(b for b in self.planner.source_city['buildings'] if b['id']=='smith')
        self.assertEqual(source['position'],self.planner.summaries['smith']['position'])
        self.assertEqual(source['role'],self.planner.summaries['smith']['role'])

    def test_path_cannot_jump_cliff_or_exceed_limit(self):
        cells={(0,10,0),(1,12,0)}
        self.assertFalse(search_path({(0,10,0)},{(1,12,0)},cells.__contains__,20))
        cells={(0,10,0),(1,11,0),(2,12,0)}
        self.assertFalse(search_path({(0,10,0)},{(2,12,0)},cells.__contains__,1))
        self.assertEqual(3,len(search_path({(0,10,0)},{(2,12,0)},cells.__contains__,2)))


class FormalInputTests(unittest.TestCase):
    def planner(self,edit):
        spec=read(ROOT/'blueprints/province_capitals/blueprint_plan/beiyuan.json')
        original=manor_relayout.read;layout=copy.deepcopy(original(spec['sources']['layout']));edit(layout)
        with patch.object(manor_relayout,'read',side_effect=lambda path:layout if str(path)==spec['sources']['layout'] else original(path)):
            return manor_relayout.ManorPlanner(spec,original(spec['sources']['city']),original(spec['sources']['environment']),original(spec['sources']['landmarks']))

    def test_foreign_city_and_retiring_a_functional_building_are_rejected(self):
        with self.assertRaisesRegex(ValueError,'身份'):self.planner(lambda l:l.update(city_id='capital_other'))
        def retire_service(layout):
            layout['buildings']=[b for b in layout['buildings'] if b['id']!='smith'];layout['retired_buildings'].append('smith')
        with self.assertRaisesRegex(ValueError,'功能建筑'):self.planner(retire_service)

    def test_the_complete_manor_box_cannot_be_shrunk(self):
        p=self.planner(lambda l:l['manor']['bounds'].__setitem__(2,l['manor']['bounds'][2]-1))
        with self.assertRaisesRegex(ValueError,'缩小或裁切'):p.prepare_manor()

    def test_unsupported_corridor_width_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'一格宽'):
            self.planner(lambda l:l['construction'].update(approach_width=3))

    def test_all_landmarks_are_in_the_original_city_central_half(self):
        totals=[0,0,0]
        for path in (ROOT/'blueprints/province_capitals/blueprint_plan/layouts').glob('*.json'):
            layout=read(path);spec=read(path.parent.parent/(path.stem+'.json'));city=read(ROOT/spec['sources']['city'])
            x,_,z=layout['landmark']['origin'];w,_,d=city['dimensions']
            self.assertTrue(w*.25<=x<=w*.75,path.stem);self.assertTrue(d*.25<=z<=d*.75,path.stem)
            expected=layout['expected'];totals=[a+b for a,b in zip(totals,[expected['houses_before'],expected['houses_after'],expected['services']])]
        self.assertEqual([437,386,51],totals)

    def test_cardinal_cell_rotation_preserves_size_and_is_reversible(self):
        for q in range(4):
            points={manor_relayout.rotate_cell(x,z,3,5,q) for x in range(3) for z in range(5)}
            self.assertEqual(15,len(points));w,d=(5,3) if q%2 else (3,5)
            self.assertEqual({(x,z) for x in range(3) for z in range(5)},
                             {manor_relayout.rotate_cell(x,z,w,d,(-q)%4) for x,z in points})
