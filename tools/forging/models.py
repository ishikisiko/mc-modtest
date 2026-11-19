"""One canonical part mesh per implement, shared by items, stages, anvil and trough."""
from __future__ import annotations
import copy
import json
from pathlib import Path
from tools.equipment import mesh
from tools.forging.mesh_profiles import tool_faces
from tools.equipment.geometry import encode_png
ROOT=Path(__file__).resolve().parents[2]
PREFIX='yuanxian:equipment/forging/'

def read(path):return json.loads(path.read_text())
def encoded(v):return (json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode()

def box(name,lo,hi,material):
 return mesh.elements_mesh({'elements':[{'name':name,'from':lo,'to':hi,'faces':{side:{'texture':'#m','uv':[0,0,16,16]} for side in mesh.FACE_VERTS}}],'textures':{'m':material}})

def surface(faces,material,metal_only=False):
 result=copy.deepcopy(faces)
 for f in result:
  if metal_only and not f.get('metal',True):continue
  f['texture']=PREFIX+material;f['uv']=[[0,0],[0,1],[1,1],[1,0]]
 return result

def palette_texture(name,colors):
 rows=[]
 for y in range(32):
  row=[]
  for x in range(32):
   seed=(x*23+y*41+x*y*7)%31
   if name=='wood':color=colors['dark'] if x%7 in (0,1) else colors['light'] if x%7==2 else colors['base']
   elif name=='rough':color=colors['dark'] if seed<5 else colors['light'] if seed>27 else colors['base']
   else:color=colors['light'] if x==0 or y==0 else colors['dark'] if x==31 or y==31 else colors['base']
   grain=(seed%7)-3
   if name=='tempered' and y in (5,6):grain-=8
   row.append(tuple(max(0,min(255,v+grain)) for v in color)+(255,))
  rows.append(row)
 return encode_png(rows)

class Catalogue:
 def __init__(self,root=ROOT):
  self.root=Path(root);self.config=read(self.root/'equipment/workshop/stages.json');self.authored=read(self.root/'equipment/workshop/tools.json')
  self.templates=read(self.root/'equipment/workshop/pickaxe_hoe_shape_templates.json')
  self.sources={};self.tool_sources={};self.heads={};self.kinds={};self.displays={};self.names={};self.material_outputs={}
  self.series=set();self._tools();self._weapons();self._armor();self._flying_sword();self._series()
 def _tools(self):
  for spec in self.authored['tools']:
   faces=[];head=[]
   for part in spec['boxes']:
    if part['material'] not in self.authored['palette']:raise ValueError('Unknown tool material')
    el={'name':part['name'],'from':part['from'],'to':part['to'],'faces':{s:{'texture':'#m','uv':[0,0,16,16]} for s in mesh.FACE_VERTS}}
    if 'rotation' in part:el['rotation']=part['rotation']
    fs=mesh.elements_mesh({'elements':[el],'textures':{'m':PREFIX+part['material']}})
    for f in fs:f['metal']=part['role'] in ('head','edge')
    faces.extend(fs)
    if part['role'] in ('head','edge'):head.extend(fs)
   rid=spec['id']
   self.tool_sources[rid]=faces
   self.heads[rid]=head
   self.kinds[rid]=spec['kind']
   self.names[rid]=spec['name']
   templated=rid in self.templates['模板']
   self.sources[rid]=tool_faces(self.templates,rid,5,faces) if templated else faces
   grip=self.templates['坐标']['握点'] if templated else spec['grip']
   self.displays[rid]=mesh.item_display(self.sources[rid],grip=grip,root=self.root)
 def _weapons(self):
  from tools import gen_qingfeng_sword_model as q,gen_lingxiao_spear_model as s
  for rid,gen in [('qingfeng_sword',q),('lingxiao_spear',s)]:
   definition=self.config['weapons'][rid];model=gen.build_model_3d();faces=mesh.elements_mesh(model)
   selected=set(definition['head_parts']);found={f['part'] for f in faces};assert selected<=found,(rid,selected-found)
   for f in faces:f['metal']=f['part'] in selected
   self.sources[rid]=faces;self.heads[rid]=[f for f in faces if f['metal']];self.kinds[rid]=definition['kind'];self.names[rid]='青锋剑' if rid=='qingfeng_sword' else '凌霄枪';self.displays[rid]=model['display']
 def _series(self):
  from tools.forged_arms import assets
  for spec in assets.specs():
   rid=spec['id'];faces,grip,_=assets.source(spec);self.series.add(rid)
   self.sources[rid]=faces;self.heads[rid]=[f for f in faces if f['metal']]
   self.kinds[rid]=spec['family'];self.names[rid]=spec['name']
   self.displays[rid]=assets.display(spec,faces,grip,self.root)
   assert self.heads[rid],rid
  self.material_outputs.update(assets.textures())
 def _flying_sword(self):
  from tools.gen_flying_swords import model, XUANTIE_NAME
  rid='xuantie_flying_sword'
  finished=model(False)
  self.sources[rid]=mesh.elements_mesh(finished)
  self.heads[rid]=mesh.elements_mesh(model(False, blade_only=True))
  blade_faces={tuple(map(tuple, face['points'])) for face in self.heads[rid]}
  for face in self.sources[rid]:
   face['metal']=tuple(map(tuple, face['points'])) in blade_faces
  self.kinds[rid]='flying_sword'
  self.names[rid]=XUANTIE_NAME
  self.displays[rid]=finished['display']
 def _armor(self):
  from tools.equipment.export import compile_armor
  spec=read(self.root/f"equipment/{self.config['armor']['set']}/equipment.json");materials=read(self.root/'equipment/materials.json')['materials']
  compiled,pixels=compile_armor(spec,materials)
  self.material_outputs[f"assets/yuanxian/textures/equipment/{self.config['armor']['set']}.png"]=encode_png(pixels)
  for item in spec['items']:
   faces=mesh.armor_mesh(compiled,item['id'],self.root);selected={b['name'] for b in item['boxes'] if b['material'] in self.config['armor']['metal_materials'] and (item['slot'] not in self.config['armor']['body_only_slots'] or b['bone']=='body')}
   all_metal={b['name'] for b in item['boxes'] if b['material'] in self.config['armor']['metal_materials']}
   for f in faces:f['metal']=f['part'] in all_metal
   rid=item['id'].split(':')[1];self.sources[rid]=faces;self.heads[rid]=[f for f in faces if f['part'] in selected];self.kinds[rid]={'head':'helmet','chest':'chestplate','legs':'leggings','feet':'boots'}[item['slot']];self.names[rid]=item['name'];self.displays[rid]=mesh.item_display(faces,armor=True)
 def armor(self,rid):return self.kinds[rid] in ('helmet','chestplate','leggings','boots')
 def coarse(self,rid):
  head=self.heads[rid];lo,hi=mesh.bounds(head);rough=self.config['rough']
  if self.armor(rid):
   # Loose plates retain dimensions of the actual shell panels instead of sharing a metal ingot icon.
   parts={}
   for f in head:parts.setdefault(f['part'],[]).append(f)
   volumes=[]
   for name,fs in parts.items():
    a,b=mesh.bounds(fs);ext=sorted([b[i]-a[i] for i in range(3)],reverse=True)
    volumes.append((ext[0]*ext[1],name,ext))
   result=[]
   for n,(_,name,size) in enumerate(sorted(volumes,reverse=True)[:rough['plate_count']]):
    w,h=size[:2];thick=max(rough['plate_min_thickness'],min(rough['plate_max_thickness'],size[2]));x=8-w/2+(n%2)*rough['plate_offset'][0];z=8-h/2+(n%2)*rough['plate_offset'][1]
    result+=box(name+'-料片',[x,n*rough['plate_spacing'],z],[x+w,n*rough['plate_spacing']+thick,z+h],PREFIX+'rough')
   return result
  if rid in self.config['weapons'] or rid in self.series or self.kinds[rid]=='flying_sword':
   # A stepped billet with the same head length and width as the authored weapon.
   result=[];cx=(lo[0]+hi[0])/2;cz=(lo[2]+hi[2])/2;h=(hi[1]-lo[1])/len(rough['bar_widths'])
   for n,width in enumerate(rough['bar_widths']):
    thick=max(hi[0]-lo[0],rough['min_thickness'])*rough['thickness_scale'];wide=(hi[2]-lo[2])*width
    result+=box('料坯段'+str(n),[cx-thick/2,lo[1]+n*h,cz-wide/2],[cx+thick/2,lo[1]+(n+1)*h,cz+wide/2],PREFIX+'rough')
   return result
  # Tool head blanks are taken from their actual head blocks; bevels and fittings come later.
  spec=next(s for s in self.authored['tools'] if s['id']==rid);core={b['name'] for b in spec['boxes'] if b['role']=='head'}
  return surface([f for f in head if f['part'] in core],'rough')
 def stage(self,rid,stage,hot=False):
  templated=rid in self.templates['模板']
  if stage==5:faces=copy.deepcopy(self.tool_sources[rid] if templated else self.sources[rid])
  elif stage==0:faces=self.coarse(rid)
  else:
   faces=copy.deepcopy(self.heads[rid]);material=self.config['states'][stage]['surface']
   if material:faces=surface(faces,material)
  if hot:faces=surface(faces,'hot',metal_only=stage==5)
  if templated:return tool_faces(self.templates,rid,stage,faces)
  return faces
 def bench(self,rid,faces,stage=5):
  # Put the broad face on the anvil, keeping the complete item's part proportions.
  flat=(lambda p:p) if self.armor(rid) and stage==0 else (lambda p:(p[1],p[2],p[0])) if self.armor(rid) else (lambda p:(p[1],p[0],-p[2]))
  f=mesh.map_points(faces,flat);lo,hi=mesh.bounds(f);s=min(self.config['bench']['extent']/max(hi[0]-lo[0],hi[2]-lo[2],.001),self.config['bench']['height']/max(hi[1]-lo[1],.001))
  return mesh.map_points(f,lambda p:(8+(p[0]-(lo[0]+hi[0])/2)*s,(p[1]-lo[1])*s,8+(p[2]-(lo[2]+hi[2])/2)*s))
 def outputs(self):
  base=self.root/'src/main/resources';out={};atlas=set()
  for name,colors in self.authored['palette'].items():out[base/f'assets/yuanxian/textures/equipment/forging/{name}.png']=palette_texture(name,colors)
  def emit(faces,ref,path,display=None):
   atlas.update(f['texture'] for f in faces if not f['texture'].split(':',1)[1].startswith(('item/','block/')))
   assets,model=mesh.obj_assets(faces,ref,display)
   out.update({base/p:data for p,data in assets.items()});out[base/path]=encoded(model)
  for rid,kind in self.kinds.items():
   for stage in range(6):
    fs=self.stage(rid,stage)
    if stage<5:
     sid=self.config['states'][stage]['id'];name=f'workpiece_{rid}_{sid}'
     emit(fs,'yuanxian:equipment/forging/'+name,f'assets/yuanxian/models/item/{name}.json',mesh.item_display(fs,armor=self.armor(rid)))
    elif rid in {t['id'] for t in self.authored['tools']}:
     emit(fs,'yuanxian:equipment/forging/'+rid,f'assets/yuanxian/models/item/{rid}.json',self.displays[rid])
    for hot in ([False,True] if stage in (0,1,5) else [False]):
     key=f'{rid if rid in self.series else kind}_{stage}'+('_hot' if hot else '')
     emit(self.bench(rid,self.stage(rid,stage,hot),stage),'yuanxian:equipment/forging/bench_'+key,f'assets/yuanxian/models/block/workshop/{key}.json')
  # OBJ materials still use the vanilla block atlas. Existing direct armor textures
  # and equipment palettes are outside its default item/block directory sources.
  sources={ref:{'type':'minecraft:single','resource':ref} for ref in atlas}
  # This generator owns the shared block atlas. The new family contributes only
  # material sprites here, without adding forging recipes or workshop models.
  manifest=base/'assets/yuanxian/immortal_equipment/atlas_sources.json'
  if manifest.is_file():
   for source in read(manifest)['sources']:
    if source.get('type')!='minecraft:single':raise ValueError('非法装备图集源')
    sources[source['resource']]=source
  out[base/'assets/minecraft/atlases/blocks.json']=encoded({'sources':[sources[ref] for ref in sorted(sources)]})
  return out
