"""Versioned framing, runtime wiring and bounded offer/data contracts. No game launches."""
import copy
import json
from pathlib import Path
import sys
import unittest
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from worldplan.district import descriptor,encode_rules
from gen_npc_life_places import generate

class YunshanV4Test(unittest.TestCase):
    def test_shared_frame_contains_all_four_real_district_placements(self):
        definition,frame,buildings=descriptor()
        self.assertEqual(4,len(buildings));self.assertEqual([224,82,272],frame['size'])
        self.assertEqual([2850,62,110],frame['source_origin'])
        life=next(l for l in generate()['layouts'] if l['id']==definition['life_layout'])
        self.assertEqual(frame['layout_hash'],life['worldplan_layout_hash'])
        self.assertEqual(frame['size'],life['size'])
        self.assertEqual(4,len({p['source'] for p in life['places']}))
    def test_staff_homes_and_stations_exist_in_the_same_approved_frame(self):
        life=next(l for l in generate()['layouts'] if l['id']=='yunshan_planned_v4')
        points={p['id']:p for p in life['places']}
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/world_sim/commerce_rules.json').read_text())
        for job in rules['staff']:
            self.assertEqual('rest',points[job['home']]['kind']);self.assertEqual('work',points[job['work']]['kind'])
            a,b=points[job['home']]['pos'],points[job['work']]['pos']
            self.assertLess(sum((x-y)**2 for x,y in zip(a,b)),192**2)
        reserved={job['home'] for job in rules['staff']}
        self.assertTrue(any(p['block'].endswith('_bed') and p['id'] not in reserved for p in life['places']))
    def test_codec_registrations_leave_legacy_ids_in_place(self):
        text=(ROOT/'src/main/java/com/example/myvillage/worldplan/runtime/WorldPlanRuntime.java').read_text()
        for version in range(1,5):self.assertIn(f'"planned_v{version}"',text)
        for filename in ('PlannedChunkGenerator','PlannedBiomeSource'):
            text=(ROOT/f'src/main/java/com/example/myvillage/worldplan/runtime/{filename}.java').read_text()
            self.assertIn('CODEC_V3=createCodec(WorldPlanRules.V3_ALGORITHM)',text)
            self.assertIn('CODEC_V4=createCodec(WorldPlanRules.ALGORITHM)',text)
            self.assertIn('FrozenRulesEncoding.decode',text)
    def test_offer_ids_are_shipped_items_and_have_bilingual_feedback(self):
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/world_sim/commerce_rules.json').read_text())
        langs=[json.loads((ROOT/f'src/main/resources/assets/myvillage/lang/{x}.json').read_text()) for x in ('zh_cn','en_us')]
        for o in rules['offers']:
            if o['item'].startswith('myvillage:'):
                key='item.'+o['item'].replace(':','.')
                for lang in langs:self.assertIn(key,lang)
            self.assertGreater(o['price'],0);self.assertLessEqual(o['initial_stock'],o['capacity'])
        for lang in langs:
            for key in ('buy','sell','rest','status.busy','status.no_room','status.hostile','status.inventory_full'):
                self.assertTrue(lang['screen.myvillage.commerce.'+key])
    def test_compressed_input_is_deterministic_and_bounded(self):
        self.assertEqual(encode_rules({'name':'云山','a':123}),encode_rules({'a':123,'name':'云山'}))
        with self.assertRaises(ValueError):encode_rules({'huge':'a'*(4*1024*1024)})
    def test_older_worlds_need_an_explicit_commercial_opt_in(self):
        code=(ROOT/'src/main/java/com/example/myvillage/sim/runtime/commerce/NpcCommerce.java').read_text()
        self.assertIn('sim.commerce(life.siteId()).isEmpty()',code)
        self.assertNotIn('sim.initializeCommerce(',code)

    def test_acceptance_reads_bounded_v4_rules_without_starting_game(self):
        from tools.worldplan.acceptance import decode_saved_rules
        import base64, gzip, json
        value={"algorithm":"myvillage:worldplan_v4"}
        encoded="gzip:"+base64.b64encode(gzip.compress(json.dumps(value).encode(),mtime=0)).decode()
        self.assertEqual(value,decode_saved_rules(encoded))
        for bad in ["{}","gzip:???","gzip:"+"a"*65000]:
            with self.assertRaises((ValueError,EOFError,OSError)):
                decode_saved_rules(bad)
        oversized="gzip:"+base64.b64encode(gzip.compress(b" "*(4*1024*1024+1),mtime=0)).decode()
        with self.assertRaises(ValueError):decode_saved_rules(oversized)
