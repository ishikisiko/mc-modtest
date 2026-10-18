import json
from pathlib import Path
import unittest
from tools import gen_forging_assets as assets
from tools.modal_runner.remote import commands_for

ROOT=Path(__file__).resolve().parents[2]
class ForgingResourcesTest(unittest.TestCase):
    def test_generator_outputs_match_and_pattern_recipes_exist(self):
        generated=assets.outputs()
        for path,data in generated.items():self.assertEqual(data,path.read_bytes(),str(path))
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/forging/rules.json').read_text())
        registered=(ROOT/'src/main/java/com/example/myvillage/item/ModItems.java').read_text()
        for recipe in rules['recipes']:
            self.assertIn('"'+recipe['output'].split(':')[1]+'"',registered)
            self.assertIn('"'+recipe['pattern'].split(':')[1]+'"',registered)
            path=ROOT/'src/main/resources/data/myvillage/recipe'/f"{recipe['pattern'].split(':')[1]}.json"
            self.assertEqual(recipe['pattern'],json.loads(path.read_text())['result']['id'])
            for trait in recipe['traits']:
                self.assertLessEqual(len(set(recipe['costs'])|set(rules['traits'][trait]['costs'])),4)
        self.assertNotIn('channeling',next(r for r in rules['recipes'] if r['id']=='lingxiao_spear')['traits'])
    def test_localized_keys_and_placeholders_match(self):
        for key,(en,zh) in assets.LANG.items():
            self.assertEqual(en.count('%s'),zh.count('%s'),key)
            for lang,text in [('en_us',en),('zh_cn',zh)]:
                data=json.loads((ROOT/f'src/main/resources/assets/myvillage/lang/{lang}.json').read_text())
                self.assertEqual(text,data[key])
    def test_focused_runtime_builds_then_runs_only_forging(self):
        commands=commands_for('forging-runtime')
        self.assertEqual(['python3','tools/forging_evidence.py'],commands[-1])
        self.assertIn('com.example.myvillage.forging.*',commands[-2])
        self.assertFalse(any('release_gate.py' in ' '.join(c) for c in commands))
        lifecycle=commands_for('forging-lifecycle')
        self.assertEqual(['python3','tools/forging_evidence.py','--lifecycle-only'],lifecycle[-1])
        self.assertNotIn('test',lifecycle[0])

class DingGeometryTest(unittest.TestCase):
    def test_open_hearth_and_three_feet_are_structural_and_lit_changes_no_geometry(self):
        from tools.forging import ding
        cold,shape=ding.geometry(False);hot,hot_shape=ding.geometry(True)
        self.assertEqual(shape,hot_shape)
        self.assertEqual([(e['name'],e['from'],e['to']) for e in cold],[(e['name'],e['from'],e['to']) for e in hot])
        self.assertEqual(3,len([e for e in cold if e['name'].endswith('_foot')]))
        self.assertEqual(2,len([e for e in cold if e['name'].endswith('_ear_top')]))
        self.assertFalse(any(e['from'][0]<8<e['to'][0] and e['from'][2]<8<e['to'][2] and e['to'][1]>10.1 for e in cold))
        for e in cold:
            self.assertTrue(all(a<b for a,b in zip(e['from'],e['to'])),e['name'])
            self.assertGreaterEqual(min(e['from']),0)
            self.assertLessEqual(e['to'][0],16);self.assertLessEqual(e['to'][2],16)
        self.assertLessEqual(len(cold),160)
    def test_generated_collision_and_states_have_one_geometry_owner(self):
        from tools.forging import ding
        base=ROOT/'src/main/resources';generated=ding.outputs()
        data=json.loads(generated[base/'data/myvillage/forging/ding.json'])
        self.assertEqual(ding.geometry()[1],data['collision'])
        states=json.loads(generated[base/'assets/myvillage/blockstates/forging_table.json'])['variants']
        self.assertEqual(8,len(states))
        for direction,angle in [('north',0),('east',90),('south',180),('west',270)]:
            for lit in ['false','true']:
                self.assertEqual(angle,states[f'facing={direction},lit={lit}']['y'])
        for e in ding.geometry(True)[0]:
            if e['name'].startswith(('ember_','fire_window_')):
                self.assertTrue(all(f['neoforge_data']['block_light']==15 for f in e['faces'].values()))

class MaterialResourcesTest(unittest.TestCase):
    def test_materials_keep_consumers_and_only_allow_the_nether_conversion(self):
        from tools.forging import materials
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/forging/rules.json').read_text())
        generated=materials.outputs()
        registry=(ROOT/'src/main/java/com/example/myvillage/item/ModItems.java').read_text()
        for m in materials.load():
            rid='myvillage:'+m['id'];self.assertEqual({'#'+m['tag']:1},rules['traits'][m['trait']]['costs'])
            namespace,tag=m['tag'].split(':')
            self.assertEqual({'replace':False,'values':[rid]},json.loads(generated[ROOT/f'src/main/resources/data/{namespace}/tags/item/{tag}.json']))
            self.assertIn('"'+m['id']+'"',registry)
            self.assertIn('output.accept('+m['id'].upper()+'.get())',registry)
        for path in materials.retired_paths():
            self.assertNotIn(path,generated)
            self.assertFalse(path.exists(),str(path))
        copper=ROOT/'src/main/resources/data/c/tags/item/ingots/copper.json'
        self.assertNotIn('myvillage:chitong_ingot',json.loads(copper.read_text())['values'] if copper.exists() else [])
        ids={'myvillage:'+m['id'] for m in materials.load()}
        for path in (ROOT/'src/main/resources/data/myvillage/recipe').glob('*.json'):
            recipe=json.loads(path.read_text());result=recipe.get('result',{})
            if isinstance(result,dict) and result.get('id') in ids:
                self.assertEqual('chitong_from_blaze.json',path.name)
                self.assertEqual({'id':'myvillage:chitong_ingot','count':1},result)
                self.assertEqual([{'tag':'c:ingots/copper'},{'item':'minecraft:blaze_powder'},
                                  {'tag':'c:spirit_stones/low_grade'}],recipe['ingredients'])

    def test_materials_do_not_change_base_weapon_or_repair_costs(self):
        rules=json.loads((ROOT/'src/main/resources/data/myvillage/forging/rules.json').read_text())
        self.assertEqual({'minecraft:iron_ingot':1,'#c:spirit_stones/low_grade':1},rules['repair']['costs'])
        for recipe in rules['recipes']:
            self.assertEqual({'minecraft:diamond','minecraft:stick','#c:spirit_stones/low_grade'},set(recipe['costs']))
    def test_retired_recipes_fail_check_and_generation_cleans_only_owned_paths(self):
        import tempfile
        from unittest.mock import patch
        from tools.forging import materials
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            retired=materials.retired_paths(root)
            for path in retired:path.parent.mkdir(parents=True,exist_ok=True);path.write_text('{}')
            other=retired[0].with_name('unrelated.json');other.write_text('{}')
            with patch.object(assets,'ROOT',root),patch.object(assets,'outputs',return_value={}):
                with patch('sys.argv',['generator','--check']):self.assertEqual(1,assets.main())
                self.assertTrue(all(p.exists() for p in retired))
                with patch('sys.argv',['generator']):self.assertEqual(0,assets.main())
                with patch('sys.argv',['generator','--check']):self.assertEqual(0,assets.main())
            self.assertTrue(other.exists())
            self.assertFalse(any(p.exists() for p in retired))

    def test_icons_are_distinct_transparent_sprites_with_visible_silhouettes(self):
        from tools.forging import materials
        from tools.gen_alchemy_assets import png
        seen=set()
        for m in materials.load():
            rows=materials.icon(m['id']);self.assertEqual((32,32),(len(rows),len(rows[0])))
            opaque=sum(p[3]>0 for row in rows for p in row)
            self.assertGreater(opaque,150);self.assertLess(opaque,650)
            self.assertEqual(0,rows[0][0][3]);seen.add(png(rows))
        self.assertEqual(3,len(seen))

class AcquisitionResourcesTest(unittest.TestCase):
    def test_natural_sources_have_tools_drops_biomes_and_bounded_generation(self):
        from tools.forging import acquisition
        config=acquisition.load();out=acquisition.outputs();base=ROOT/'src/main/resources'
        def obj(path):return json.loads(out[base/path])
        ore=obj('data/myvillage/worldgen/configured_feature/xuantie_ore.json')
        self.assertEqual(config['xuantie']['vein_size'],ore['config']['size'])
        self.assertEqual(0,ore['config']['discard_chance_on_air_exposure'])
        self.assertIn('myvillage:xuantie_ore',obj('data/minecraft/tags/block/needs_iron_tool.json')['values'])
        self.assertIn('myvillage:edge_tempering_sand_deposit',obj('data/minecraft/tags/block/mineable/shovel.json')['values'])
        for block,item in [('xuantie_ore','xuantie_ingot'),('edge_tempering_sand_deposit','edge_tempering_sand')]:
            loot=obj(f'data/myvillage/loot_table/blocks/{block}.json')
            alternatives=loot['pools'][0]['entries'][0]['children']
            self.assertEqual('myvillage:'+block,alternatives[0]['name'])
            self.assertEqual('myvillage:'+item,alternatives[1]['name'])
            self.assertGreaterEqual(alternatives[1]['functions'][0]['count']['min'],1)
        sand=obj('data/myvillage/worldgen/configured_feature/edge_tempering_sand.json')
        self.assertEqual('myvillage:edge_tempering_sand',sand['type'])
        self.assertEqual(1,sand['config']['max_water_depth'])
        placement=obj('data/myvillage/worldgen/placed_feature/edge_tempering_sand.json')['placement']
        self.assertEqual({'type':'minecraft:heightmap','heightmap':'WORLD_SURFACE_WG'},placement[-2])
        self.assertEqual({'type':'minecraft:biome'},placement[-1])
        self.assertEqual(config['sand']['biomes'],obj('data/myvillage/tags/worldgen/biome/has_edge_tempering_sand.json')['values'])
    def test_invalid_acquisition_bounds_are_rejected_before_export(self):
        import tempfile
        from tools.forging import acquisition
        source=acquisition.load()
        for group,key,value in [('infusion','qi_per_ingot',0),('infusion','max_batch',65),('infusion','qi_per_pulse',100),('xuantie','min_y',100),('sand','max_water_depth',3),('sand','drop_min',0)]:
            data=json.loads(json.dumps(source));data[group][key]=value
            with tempfile.TemporaryDirectory() as temp:
                root=Path(temp);p=root/'src/main/resources/data/myvillage/forging/acquisition.json';p.parent.mkdir(parents=True);p.write_text(json.dumps(data))
                with self.assertRaises(ValueError):acquisition.load(root)
