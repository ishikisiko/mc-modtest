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
if __name__=='__main__':unittest.main()
