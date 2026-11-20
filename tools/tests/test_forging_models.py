import copy
import hashlib
import json
import math
from pathlib import Path
import unittest
from tools.equipment import mesh,geometry
from tools.forging.models import Catalogue,ROOT

class SharedForgingModelsTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):cls.c=Catalogue();cls.outputs=cls.c.outputs()
 def test_obj_roundtrip_preserves_geometry_uv_and_material_for_every_stage(self):
  for rid in self.c.sources:
   for stage in range(6):
    faces=self.c.stage(rid,stage);files,_=mesh.obj_assets(faces,'yuanxian:probe')
    parsed=mesh.read_obj(files['assets/yuanxian/models/probe.obj'],files['assets/yuanxian/models/probe.mtl'])
    self.assertEqual(len(faces),len(parsed))
    for a,b in zip(faces,parsed):
     self.assertEqual(a['texture'],b['texture']);self.assertEqual(a['part'],b['part'])
     for va,vb in zip(a['points']+a['uv'],b['points']+b['uv']):
      for x,y in zip(va,vb):self.assertAlmostEqual(x,y,places=6)
 def test_gui_framing_keeps_every_vertex_inside_slot_in_every_stage(self):
  for rid in self.c.sources:
   for stage in range(6):
    faces=self.c.stage(rid,stage);d=mesh.gui_transform(faces,*mesh.gui_profile(self.c.armor(rid)))
    self.assertLessEqual(max(d['scale']),4)
    for f in faces:
     for p in f['points']:
      q=geometry.display_point(d,p)
      self.assertTrue(all(abs(q[i])<=7.000001 for i in (0,1)),(rid,stage,q))
 def test_nonstandard_materials_are_stitched_into_the_block_atlas(self):
  atlas=json.loads(self.outputs[ROOT/'src/main/resources/assets/minecraft/atlases/blocks.json'])
  refs={s['resource'] for s in atlas['sources']}
  for rid in self.c.sources:
   for stage in range(6):
    for hot in (False,True):
     for face in self.c.stage(rid,stage,hot):
      if not face['texture'].split(':')[1].startswith(('item/','block/')):self.assertIn(face['texture'],refs)
 def test_native_warning_for_missing_atlas_sprite_is_a_failure(self):
  from tools.forging_models_evidence import model_load_errors
  warning='[Worker-Main-4/WARN] [net.minecraft.client.resources.model.ModelManager/]: Missing textures in model yuanxian:forged_hoe#inventory:\n    minecraft:textures/atlas/blocks.png:yuanxian:equipment/forging/wood'
  self.assertTrue(model_load_errors(warning))
  self.assertFalse(model_load_errors('[INFO]: EQUIPMENT_MODELS loaded 8 item/profile models'))
 def test_heat_treatments_preserve_shape_and_polishing_restores_source_material(self):
  for rid in self.c.sources:
   stages=[self.c.stage(rid,s) for s in (1,2,3,4)]
   self.assertEqual(stages[-1],self.c.heads[rid])
   positions=lambda fs:[f['points'] for f in fs]
   self.assertTrue(all(positions(fs)==positions(stages[0]) for fs in stages))
   self.assertEqual(4,len({tuple(f['texture'] for f in fs) for fs in stages}))
 def test_tools_have_actual_head_volume_and_assembly_adds_the_wooden_handle(self):
  shared=set()
  base=ROOT/'src/main/resources/assets/yuanxian'
  def resolved(path):
   model=json.loads(self.outputs[path])
   obj,mtl=(base/ref.split(':',1)[1] for ref in (model['model'],model['mtl_override']))
   shared.add(obj)
   faces=mesh.read_obj(self.outputs[obj],self.outputs[mtl])
   return obj,tuple(f['texture'] for f in faces)
  for rid,(spec,_,_,key) in self.c.tools.models.items():
   head=self.c.stage(rid,4);finished=self.c.stage(rid,5)
   self.assertFalse(any('wood' in f['texture'] for f in head))
   self.assertTrue(any('wood' in f['texture'] for f in finished))
   self.assertEqual(head,finished[:len(head)])
   lo,hi=mesh.bounds(head);self.assertAlmostEqual(hi[0]-lo[0],spec['head_depth'])
   self.assertGreater(hi[0],lo[0])
   self.assertLessEqual(hi[0]-lo[0],1.03)
   self.assertNotEqual(self.c.stage(rid,0)[0]['points'],head[0]['points'])
   self.assertGreater(len(finished),len(head))
   item=[resolved(base/f'models/item/workpiece_{rid}_{s}.json') for s in ('rough','shaped','quenched','tempered','ground')]
   self.assertEqual(1,len({obj for obj,_ in item[1:]}))
   self.assertEqual(4,len({maps for _,maps in item[1:]}))
   resolved(base/f'models/item/{rid}.json')
   for stage in range(6):
    cold=resolved(base/f'models/block/workshop/{key}_{stage}.json')
    if stage in (0,1,5):
     hot=resolved(base/f'models/block/workshop/{key}_{stage}_hot.json')
     self.assertEqual(cold[0],hot[0])
  self.assertEqual(24,len(shared))
 def test_bench_and_trough_meshes_fit_the_same_authored_volume(self):
  for rid in self.c.sources:
   for stage in range(6):
    fs=self.c.bench(rid,self.c.stage(rid,stage),stage);lo,hi=mesh.bounds(fs)
    self.assertAlmostEqual(0,lo[1]);self.assertLessEqual(hi[1],4.000001)
    for i in (0,2):self.assertGreaterEqual(lo[i],1.999999);self.assertLessEqual(hi[i],14.000001)
 def test_hot_finished_gear_does_not_turn_wood_cloth_or_wraps_into_hot_metal(self):
  for rid in self.c.sources:
   cold=self.c.stage(rid,5);hot=self.c.stage(rid,5,True)
   for a,b in zip(cold,hot):
    self.assertEqual(a['points'],b['points'])
    if not a['metal']:self.assertEqual(a['texture'],b['texture'])
 def test_moxing_iron_is_forged_but_robe_and_jade_are_added_at_assembly(self):
  spec=json.loads((ROOT/'equipment/xuantie_armor/equipment.json').read_text())
  for item in spec['items']:
   rid=item['id'].split(':')[1]
   iron={b['name'] for b in item['boxes'] if b['material'] in ('moxing_iron','moxing_dark')}
   soft={b['name'] for b in item['boxes'] if b['material'] in ('moxing_ivory','moxing_blue','moxing_leather','moxing_jade')}
   self.assertTrue(iron);self.assertEqual(bool(soft),item['slot']!='head')
   head_parts={f['part'] for f in self.c.stage(rid,4)}
   self.assertTrue(head_parts);self.assertTrue(head_parts<=iron);self.assertFalse(head_parts&soft)
   hot=self.c.stage(rid,5,True)
   self.assertEqual({f['part'] for f in hot if f['texture'].endswith('/hot')},iron)
   self.assertTrue(soft<={f['part'] for f in hot if f['texture']=='yuanxian:equipment/xuantie_armor'})
 def test_approved_weapon_geometry_textures_and_non_gui_transforms_are_unchanged(self):
  from tools import gen_qingfeng_sword_model as q,gen_lingxiao_spear_model as s
  for gen,expected in [(q,'dbffa13a45ef43148e4db66262528b29b5e068a90d03a1c39d0e4f06e85193ee'),(s,'4e4b8d5c50128390768e5b10010f5d5141b501c60b2e74f83923291efa0cd2bd')]:
   model=gen.build_model_3d();model['display'].pop('gui')
   self.assertEqual(expected,hashlib.sha256(json.dumps(model,sort_keys=True).encode()).hexdigest())
 def test_moxing_worn_armor_and_atlas_match_redesign_baseline(self):
  for filename,expected in [('equipment/xuantie_armor.json','726f4cb8f2456559e765aaaf9b471e4d3d9becee93b6ae3fb3c7b6623e1d39f8'),('textures/equipment/xuantie_armor.png','6ccdff0748041d1ee626d0533f40968f6fd35cd327f4f979673b88b80ba59d91')]:
   self.assertEqual(expected,hashlib.sha256((ROOT/'src/main/resources/assets/yuanxian'/filename).read_bytes()).hexdigest())
 def test_armor_obj_matches_the_existing_worn_model_preview_vertex_for_vertex(self):
  from tools.equipment.export import compile_armor
  from tools.equipment.preview import scene
  root=ROOT;spec=json.loads((root/'equipment/xuantie_armor/equipment.json').read_text());mats=json.loads((root/'equipment/materials.json').read_text())['materials'];model,pixels=compile_armor(spec,mats)
  for item in spec['items']:
   faces=mesh.armor_mesh(model,item['id'],root);original=scene(model,pixels,body=False,selected={item['id']})
   self.assertEqual(len(faces),len(original))
   for f,q in zip(faces,original):
    for a,b in zip(f['points'],q.pos):
     for x,y in zip(a,b):self.assertAlmostEqual(x,y,places=8)
    for a,b in zip(f['uv'],q.uv):
     for x,y in zip(a,b):self.assertAlmostEqual(x,y/16,places=8)

if __name__=='__main__':unittest.main()
