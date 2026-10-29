"""招牌文字与 NBT 数值类型在原生蓝图重打包后保持原字节。"""
from pathlib import Path
import copy
import json
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import json_to_nbt as nbt
from buildgen.nbtread import read_gzipped_nbt
from .nbt_entities import audit_entries, block_entities, matches_saved_entity, write_tile


class BlockEntityTests(unittest.TestCase):
    def source(self, path, *, state='minecraft:oak_sign[rotation=0,waterlogged=false]', pos=(1,2,3)):
        sign = nbt.Compound({'front_text': nbt.Compound({
            'has_glowing_text': nbt.Byte(0), 'color': nbt.String('black'),
            'messages': nbt.ListTag(nbt.TAG_STRING, [nbt.String('{"text":"药堂"}')]*4)}),
            'is_waxed': nbt.Byte(1)})
        root = nbt.Compound({
            'palette': nbt.ListTag(nbt.TAG_COMPOUND, [nbt.palette_state_to_tag(state)]),
            'blocks': nbt.ListTag(nbt.TAG_COMPOUND, [nbt.Compound({
                'pos': nbt.ListTag(nbt.TAG_INT, [nbt.Int(v) for v in pos]),
                'state': nbt.Int(0), 'nbt': sign})]),
            'entities': nbt.ListTag(nbt.TAG_COMPOUND, [])})
        nbt.write_gzipped_nbt(root,str(path))

    def test_same_block_keeps_exact_nested_nbt_with_property_changes(self):
        with tempfile.TemporaryDirectory() as folder:
            source, out = Path(folder)/'source.nbt', Path(folder)/'out.nbt'
            self.source(source); before = block_entities(source)
            counts = write_tile(out, {(1,2,3):('minecraft:oak_sign[rotation=4,waterlogged=false]','','')}, 16, before)
            self.assertEqual((1,1,1), counts)
            self.assertEqual(before,block_entities(out))
            raw=read_gzipped_nbt(str(out))[1]
            self.assertEqual('{"text":"药堂"}',raw['blocks'][0]['nbt']['front_text']['messages'][0])

    def test_replaced_block_cannot_inherit_foreign_block_entity(self):
        with tempfile.TemporaryDirectory() as folder:
            source, out = Path(folder)/'source.nbt', Path(folder)/'out.nbt'
            self.source(source)
            counts=write_tile(out,{(1,2,3):('minecraft:air','','')},16,block_entities(source))
            self.assertEqual(0,counts[2])
            self.assertEqual({},block_entities(out))

    def test_buffered_writer_preserves_plain_template_gzip_bytes(self):
        from province_capitals.regional.v2.export_game import write_tile as original_writer
        cells={(0,0,0):('minecraft:stone','',''),(15,319,15):('minecraft:air','',''),
               (7,22,3):('minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]','','')}
        with tempfile.TemporaryDirectory() as folder:
            old,new=Path(folder)/'old.nbt',Path(folder)/'new.nbt'
            original_writer(old,cells,320);write_tile(new,cells,320,{})
            self.assertEqual(old.read_bytes(),new.read_bytes())

    def test_saved_sign_comparison_accepts_only_plain_text_equivalence(self):
        expected={'front_text':{'messages':['{"text":"药堂"}'],'color':'black'},'is_waxed':1}
        saved={'front_text':{'messages':['"药堂"'],'color':'black'},'is_waxed':1,'x':23,'id':'minecraft:sign'}
        self.assertTrue(matches_saved_entity(expected,saved))
        for changed in ('{"text":"药堂","bold":true}','"别处"'):
            actual=copy.deepcopy(saved);actual['front_text']['messages']=[changed]
            self.assertFalse(matches_saved_entity(expected,actual))
        del saved['is_waxed']
        self.assertFalse(matches_saved_entity(expected,saved))
        self.assertFalse(matches_saved_entity({'custom_name':'{"text":"药堂"}'},{'custom_name':'"药堂"'}))

    def test_cross_tile_wall_sign_export_delays_and_preserves_original_nbt(self):
        from . import export_minecraft as exporter
        from .test_native_rules import World
        state='minecraft:spruce_wall_sign[facing=west,waterlogged=false]';p=(15,3,8)
        with tempfile.TemporaryDirectory() as folder:
            base=Path(folder);source=base/'data/myvillage/structure/source.nbt';source.parent.mkdir(parents=True)
            self.source(source,state=state,pos=p);before=block_entities(source)
            world=World({p:state,(16,3,8):'minecraft:glass_pane'})
            world.width=32;world.depth=16;world.height=8;world.nbt_tiles={(0,0)}
            world.refs={(0,0):{'template':'myvillage:source'}}
            world.dirty={(0,0),(1,0)};world.ground=lambda x,z:0
            world.manifest={'terrain':{'top_block':'minecraft:stone','fill_block':'minecraft:stone'}}
            data={'gardens':[],'doors':[],'fixtures':[],'minecraft_checks':{},'dimensions':[32,8,16]}
            spec={'city_id':'capital_zhongzhou','name':'测试牌子',
                  'sources':{'native_rules':'blueprints/province_capitals/blueprint_plan/native_rules.json'},
                  'minecraft':{'origin':[100,64,200],'pack_format':48,'batch_rows':1,'scope':'test'}}
            spec_path=base/'spec.json';spec_path.write_text(json.dumps(spec))
            with patch.object(exporter,'build',return_value=(SimpleNamespace(),world,data)),patch.object(exporter,'source_hashes',return_value={}):
                result=exporter.export(spec_path,base,base/'output')
            self.assertEqual(3,len(result['tiles']));self.assertEqual(1,result['atomic_groups'])
            self.assertEqual(['tiles','atomic_groups'],[b['phase'] for b in result['batches']])
            entries=result['block_entity_checks'];self.assertEqual(1,result['expected_block_entity_matches'])
            self.assertEqual(audit_entries({p:(state,'','')},before),entries)
            records={}
            for tile in result['tiles']:
                path=base/'output/datapack/data/myvillage/structure'/ (tile['template'].split(':')[1]+'.nbt')
                raw=read_gzipped_nbt(str(path))[1]
                for block in raw['blocks']:
                    at=tuple(block['pos'][i]+tile[axis] for i,axis in enumerate(('x','y','z')))
                    self.assertNotIn(at,records);records[at]=(tile,block)
            tile,block=records[p];self.assertEqual('atomic_groups',tile['phase'])
            self.assertEqual((15,3,8),(tile['x'],tile['y'],tile['z']))
            self.assertEqual([1,1,1],tile['size']);self.assertEqual('tiles',records[16,3,8][0]['phase'])
            self.assertEqual(before[p],block_entities(base/'output/datapack/data/myvillage/structure'/ (tile['template'].split(':')[1]+'.nbt'))[0,0,0])
            function=base/'output/datapack/data/myvillage/function/blueprint_trial/zhongzhou'
            self.assertIn('115 67 208',(function/'compound_0000.mcfunction').read_text())
            self.assertIn('115 67 208 '+state,(function/'audit.mcfunction').read_text())


if __name__=='__main__':unittest.main()
