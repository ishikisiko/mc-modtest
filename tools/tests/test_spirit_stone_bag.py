import json
from pathlib import Path
import unittest
from tools import gen_spirit_stone_bag as assets
from tools.spirit_stone_bag_evidence import stored,bag_spec,counted_bag_spec,BAG,STONE,LEDGER,HUGE
from tools.modal_runner import remote
ROOT=Path(__file__).resolve().parents[2]
class SpiritStoneBagResourcesTest(unittest.TestCase):
    def test_owned_resources_match_and_recipe_uses_grade_tag(self):
        files=assets.outputs()
        for path,data in files.items():self.assertEqual(data,path.read_bytes(),str(path))
        recipe=json.loads(files[ROOT/'src/main/resources/data/myvillage/recipe/spirit_stone_bag.json'])
        self.assertEqual(1,sum(i=={'tag':'c:spirit_stones/low_grade'} for i in recipe['ingredients']))
        self.assertEqual({'id':BAG,'count':1},recipe['result'])
        self.assertNotIn('spirit_stone_bags',str(recipe['ingredients']))
    def test_unbounded_capacity_and_scan_values_have_one_bundled_source(self):
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/spirit_stones/bag.json').read_text())
        self.assertEqual({'collection_interval_ticks'},set(rules))
        self.assertGreaterEqual(rules['collection_interval_ticks'],1)
        for en,zh in assets.LANG.values():self.assertEqual(en.count('%s'),zh.count('%s'))
    def test_runtime_parser_reads_only_actual_stored_stones(self):
        stack={'id':BAG,'components':{'minecraft:container':[{'slot':0,'item':{'id':STONE,'count':63}},{'slot':1,'item':{'id':'minecraft:diamond','count':5}}]}}
        self.assertEqual(63,stored(stack));self.assertEqual(0,stored({'id':STONE,'count':64}))
        self.assertEqual(0,stored({'id':BAG}));self.assertIn('count:30',bag_spec(30))
    def test_huge_quantities_are_exact_and_migration_parser_sums_both_formats(self):
        stack={'id':BAG,'components':{LEDGER:[{'item':{'id':STONE,'count':1},'quantity':str(HUGE)}],
            'minecraft:container':[{'slot':0,'item':{'id':STONE,'count':9}}]}}
        self.assertEqual(HUGE+9,stored(stack));self.assertIn('quantity:"'+str(HUGE)+'"',counted_bag_spec(HUGE))
        self.assertIn('count:1',counted_bag_spec(HUGE))
    def test_remote_scope_keeps_game_and_build_evidence_distinct(self):
        build=remote.commands_for('spirit-bag-build');runtime=remote.commands_for('spirit-bag-runtime')
        self.assertIn('com.example.myvillage.spiritstone.*',build[-1]);self.assertIn('com.example.myvillage.sim.runtime.commerce.*',build[-1])
        self.assertEqual(runtime[:-1],build);self.assertEqual(['python3','tools/spirit_stone_bag_evidence.py'],runtime[-1])
        self.assertFalse(any('release_gate' in ' '.join(command) for command in runtime))
