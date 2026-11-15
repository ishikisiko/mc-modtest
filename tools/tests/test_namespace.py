import base64,gzip,json,tempfile,unittest
from pathlib import Path
from tools.validate_namespace import inspect
class NamespaceTest(unittest.TestCase):
 def setUp(self):self.folder=tempfile.TemporaryDirectory();self.addCleanup(self.folder.cleanup);self.root=Path(self.folder.name)
 def write(self,name,value):
  p=self.root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(value if isinstance(value,bytes) else json.dumps(value).encode());return p
 def test_model_parent_and_texture_references_must_exist(self):
  self.write('assets/yuanxian/models/item/a.json',{'parent':'yuanxian:item/base','textures':{'layer0':'yuanxian:item/a'}})
  self.assertEqual(2,len(inspect(self.root)['errors']))
  self.write('assets/yuanxian/models/item/base.json',{'parent':'minecraft:item/generated'});self.write('assets/yuanxian/textures/item/a.png',b'fixture')
  self.assertEqual('passed',inspect(self.root)['status'])
 def test_old_namespace_is_found_in_json_nbt_and_encoded_frozen_rules(self):
  self.write('data/yuanxian/a.json',{'item':'myvillage:old'})
  self.write('data/yuanxian/structure/a.nbt',gzip.compress(b'fixture myvillage:old'))
  packed='gzip:'+base64.b64encode(gzip.compress(json.dumps({'generator':'myvillage:planned_v11'}).encode())).decode()
  self.write('data/yuanxian/worldgen/world_preset/a.json',{'rules':packed})
  report=inspect(self.root);self.assertEqual(3,len(report['errors']));self.assertEqual(1,report['counts']['encoded_payloads'])
 def test_old_resource_paths_and_translation_keys_fail(self):
  self.write('data/myvillage/a.json',{})
  self.write('assets/yuanxian/lang/zh_cn.json',{'item.myvillage.a':'旧键'})
  self.assertEqual(2,len(inspect(self.root)['errors']))
 def test_java_implementation_package_is_not_a_resource_id(self):
  self.write('META-INF/enumextensions.json',{'class':'com.example.myvillage.SomeType'});self.assertEqual('passed',inspect(self.root)['status'])
 def test_explicit_equipment_namespace_rename_keeps_every_item_path(self):
  from tools.immortal_equipment.exporter import validate_retained_ids
  old={'myvillage:a','myvillage:b'};new={'yuanxian:a','yuanxian:b'}
  with self.assertRaisesRegex(ValueError,'不得删除'):validate_retained_ids(old,new)
  validate_retained_ids(old,new,'myvillage')
  with self.assertRaisesRegex(ValueError,'不得删除'):validate_retained_ids(old,{'yuanxian:a'},'myvillage')
  with self.assertRaisesRegex(ValueError,'未声明'):validate_retained_ids(old|{'other:c'},new,'myvillage')
  for bad in ('yuanxian','../bad',''):
   with self.assertRaisesRegex(ValueError,'无效'):validate_retained_ids(old,new,bad)
 def test_explicit_tag_input_rename_preserves_members_and_foreign_namespaces(self):
  from tools.namespace_migration import tag_values
  values=['myvillage:leaf','#myvillage:logs','other:leaf','minecraft:oak_log']
  self.assertEqual(['yuanxian:leaf','#yuanxian:logs','other:leaf','minecraft:oak_log'],tag_values(values,'myvillage'))
  self.assertEqual(values,tag_values(values));self.assertEqual('myvillage:leaf',values[0])
  for bad in ('yuanxian','../bad',''):
   with self.assertRaisesRegex(ValueError,'无效'):tag_values(values,bad)
 def test_wood_owner_migrates_merged_tag_members_without_editing_input(self):
  from unittest.mock import patch
  from tools.ningyuan_tree import assets
  relative=Path('src/main/resources/data/minecraft/tags/block/leaves.json');path=self.root/relative;path.parent.mkdir(parents=True);path.write_text(json.dumps({'replace':False,'values':['myvillage:ningyuan_leaves','other:leaves','yuanxian:ningyuan_leaves']}));before=path.read_bytes();out={}
  with patch.object(assets,'ROOT',self.root):assets.tags(lambda name,value:out.__setitem__(name,value),'myvillage')
  self.assertEqual(['yuanxian:ningyuan_leaves','other:leaves'],out[relative]['values']);self.assertEqual(before,path.read_bytes())
 def test_crop_owner_migrates_merged_tag_members(self):
  from tools import gen_spirit_crops as gen
  base=self.root/'src/main/resources'
  for locale in ('zh_cn','en_us'):
   p=base/f'assets/yuanxian/lang/{locale}.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text('{}')
  p=base/'data/minecraft/tags/block/mineable/shovel.json';p.parent.mkdir(parents=True);p.write_text(json.dumps({'replace':False,'values':['myvillage:spirit_soil','other:soil']}))
  out=gen.merged(self.root,rename_namespace_from='myvillage')
  self.assertEqual(['yuanxian:spirit_soil','other:soil'],json.loads(out[p])['values'])
 def test_road_owner_migrates_fence_tag_instead_of_accumulating_both_ids(self):
  from tools.gen_road_ports import assets,ROOT
  out=assets(rename_namespace_from='myvillage')
  for kind in ('block','item'):
   data=json.loads(out[ROOT/f'src/main/resources/data/minecraft/tags/{kind}/fences.json'])
   self.assertEqual(['yuanxian:road_stone_guardrail'],data['values'])
if __name__=='__main__':unittest.main()
