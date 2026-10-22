import copy
import json
import unittest
from pathlib import Path
from tools import gen_elemental_arts as g

class ElementalArtsTest(unittest.TestCase):
    def setUp(self):self.doc=json.loads((g.ROOT/g.SOURCE).read_text())
    def test_all_five_kits_have_survival_acquisition_and_unique_recipes(self):
        plan=g.plan();recipes=[]
        for a in self.doc['arts']:
            recipe=json.loads(plan[g.DATA/f'recipe/manual_{a["id"]}.json'])
            self.assertEqual(recipe['result']['components']['myvillage:technique'],'myvillage:'+a['id'])
            recipes.append(tuple(sorted(json.dumps(i,sort_keys=True) for i in recipe['ingredients'])))
            technique=json.loads(plan[g.DATA/f'myvillage/technique/{a["id"]}.json'])
            self.assertNotIn('minimum_element_affinity',technique['requirements'])
            self.assertEqual(a['qi_cost'],technique['effects']['active']['qi_cost'])
        self.assertEqual(20,len(set(recipes)))
    def test_generation_is_current_and_keeps_existing_icon_contract(self):
        self.assertEqual([],g.generate(check=True))
        self.assertFalse(any('cultivation_panel' in str(p) or p.suffix=='.png' for p in g.plan()))
    def test_invalid_boundaries_are_rejected(self):
        for key,value in [('maximum_targets',999),('range',float('nan')),('qi_cost',0),('ward_fraction',1),('shape','unknown')]:
            d=copy.deepcopy(self.doc);d['arts'][0][key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):g.validate(d)
    def test_missing_school_or_prerequisite_is_rejected(self):
        d=copy.deepcopy(self.doc);d['schools'].pop()
        with self.assertRaises(ValueError):g.validate(d)
        d=copy.deepcopy(self.doc);d['arts'][1]['previous']='linghuo_shu'
        with self.assertRaises(ValueError):g.validate(d)
    def test_growth_has_playable_early_kit_and_later_area(self):
        for e in g.ELEMENTS:
            kit={a['role']:a for a in self.doc['arts'] if a['element']==e}
            self.assertEqual('qi_refining_1',kit['basic']['minimum_stage'])
            self.assertEqual('qi_refining_1',kit['ward']['minimum_stage'])
            self.assertEqual('qi_refining_4',kit['burst']['minimum_stage'])
            self.assertEqual('foundation_early',kit['field']['minimum_stage'])
            self.assertEqual(kit['basic']['id'],kit['burst']['previous'])
            self.assertEqual(kit['burst']['id'],kit['field']['previous'])

if __name__=='__main__':unittest.main()
