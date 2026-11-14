import gzip
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile
from PIL import Image

from tools.runtime_resources.pack import (CONFIG, decode_records, encode_records, encoded,
                                         factor_model, pack, png_recompress, restore_model, valid_path)
from tools.runtime_resources.archive import compact


class RuntimeResourcesTest(unittest.TestCase):
    def test_geometry_is_shared_across_materials_and_uv_without_changing_bytes(self):
        a={'parent':'minecraft:block/block','ambientocclusion':False,'textures':{'a':'myvillage:block/a','particle':'#a'},
           'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':{'north':{'texture':'#a','uv':[0,0,16,16],'cullface':'north'}}}]}
        b=json.loads(json.dumps(a));b['textures']={'jade':'myvillage:block/jade','particle':'#jade'}
        b['elements'][0]['faces']['north'].update(texture='#jade',uv=[1.25,2.5,14.75,15])
        ca,pa=factor_model(encoded(a)+b'\n');cb,pb=factor_model(encoded(b)+b'\n')
        self.assertEqual(ca,cb)
        self.assertNotEqual(pa,pb)
        self.assertEqual(restore_model(ca,pa),encoded(a)+b'\n')
        self.assertEqual(restore_model(cb,pb),encoded(b)+b'\n')
        b['ambientocclusion']=True
        self.assertNotEqual(ca,factor_model(encoded(b)+b'\n')[0])

    def test_composite_order_and_procedural_book_regions_survive(self):
        value={'loader':'myvillage:procedural_books','textures':{'book':'myvillage:block/book'},'book_regions':[[0,1,2,3,4,5,0,-8]],
               'base_model':{'loader':'neoforge:composite','children':{'solid':{'elements':[]},'translucent':{'elements':[]}},
                             'item_render_order':['solid','translucent']}}
        blob=encoded(value)+b'\n';constants,parameters=factor_model(blob)
        self.assertEqual(blob,restore_model(constants,parameters))
        with self.assertRaises(ValueError):restore_model(constants,parameters+[1])

    def test_binary_roundtrip_and_malformed_lengths(self):
        values=[b'',gzip.compress(b'NBT payload',mtime=0),b'\x00\xff']
        blob=encode_records(values);self.assertEqual(values,decode_records(blob,1024))
        for broken in [b'',b'BAD!'+blob[4:],blob[:-1],blob+b'X',b'MVRB\x01'+struct.pack('>I',5000)]:
            with self.assertRaises(ValueError):decode_records(broken,1024)
        with self.assertRaises(ValueError):decode_records(blob,1)

    def test_path_validation_rejects_traversal_and_namespaces(self):
        self.assertTrue(valid_path('assets/myvillage/models/block/a.json'))
        for value in ['', '/absolute','../escape','a//b','a/../b','a/./b','a\\b','a:b']:
            self.assertFalse(valid_path(value),value)

    def test_png_recompression_preserves_pixels_and_metadata(self):
        image=Image.new('RGBA',(32,32),(10,20,30,40));out=io.BytesIO();image.save(out,format='PNG',compress_level=0)
        blob=out.getvalue();fresh=png_recompress(blob)
        self.assertLess(len(fresh),len(blob));self.assertEqual(image.tobytes(),Image.open(io.BytesIO(fresh)).tobytes())
        self.assertEqual(fresh,png_recompress(fresh))
        with self.assertRaises(ValueError):png_recompress(blob[:-1])

    def test_build_output_contains_one_geometry_for_two_instances_and_no_expanded_copy(self):
        with tempfile.TemporaryDirectory()as tmp:
            root=Path(tmp);cfg=json.loads(CONFIG.read_text());cfg.update(catalogs=['data/myvillage/catalog.json'],png_recompress=False)
            config=root/'rules.json';config.write_bytes(encoded(cfg))
            def write(name,value):
                f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(value)
            write('data/myvillage/catalog.json',encoded({'banks':[{'id':'sample','profiles':[{},{}]}]}))
            for i in range(2):write(f'assets/myvillage/models/block/sample/shape_{i}.json',encoded({'textures':{'all':f'myvillage:block/{i}'},'elements':[]})+b'\n')
            for name in ['assets/myvillage/blockstates/sample.json','assets/myvillage/models/item/sample.json','data/myvillage/loot_table/blocks/sample.json']:
                write(name,b'{}\n')
            write('assets/myvillage/models/equipment/probe.obj',b'mtllib probe.mtl\nv 0 0 0\n')
            write('assets/myvillage/models/equipment/probe.mtl',b'newmtl material\n')
            write('assets/myvillage/models/item/other.json',b'{ "parent": "minecraft:item/generated" }\n')
            raw=gzip.compress(b'original structure bytes',mtime=0);write('data/myvillage/structure/sample.nbt',raw)
            result=pack(root,config_path=config)
            self.assertEqual(1,result['shared_geometry_templates']);self.assertEqual(2,result['model_instances'])
            self.assertEqual(9,result['logical_files']);self.assertEqual(3,result['other_model_resources']);self.assertFalse((root/'data/myvillage/structure/sample.nbt').exists())
            self.assertTrue((root/'myvillage_packed/manifest.json').exists());self.assertTrue((root/'myvillage_packed/rules.json').exists())
            from tools.runtime_resources.read import DirectoryResources
            reader=DirectoryResources(root)
            self.assertEqual(raw,reader.read('data/myvillage/structure/sample.nbt'))
            self.assertEqual(encoded({'textures':{'all':'myvillage:block/0'},'elements':[]})+b'\n',reader.read('assets/myvillage/models/block/sample/shape_0.json'))
            self.assertEqual(b'mtllib probe.mtl\nv 0 0 0\n',reader.read('assets/myvillage/models/equipment/probe.obj'))
            self.assertEqual(b'{ "parent": "minecraft:item/generated" }\n',reader.read('assets/myvillage/models/item/other.json'))

    def test_archive_normalization_preserves_all_payloads_and_stores_group_gzip(self):
        with tempfile.TemporaryDirectory()as tmp:
            path=Path(tmp)/'test.jar';original={'META-INF/MANIFEST.MF':b'Manifest-Version: 1.0\n','test.class':b'\xca\xfe\xba\xbe','myvillage_packed/0.bytes.gz':gzip.compress(b'abc',mtime=0)}
            with zipfile.ZipFile(path,'w',compression=zipfile.ZIP_DEFLATED)as z:
                for name,value in original.items():z.writestr(name,value)
            compact(path)
            with zipfile.ZipFile(path)as z:
                self.assertEqual(original,{n:z.read(n)for n in z.namelist()})
                self.assertEqual(zipfile.ZIP_STORED,z.getinfo('myvillage_packed/0.bytes.gz').compress_type)


if __name__=='__main__':unittest.main()
