"""Shared textured quad export. OBJ keeps authored rotations and one geometry in every view."""
from __future__ import annotations
import copy
import json
import math
from pathlib import Path
from . import geometry as g

FACE_VERTS={
 'down':[(0,0,1),(0,0,0),(1,0,0),(1,0,1)],
 'up':[(0,1,0),(0,1,1),(1,1,1),(1,1,0)],
 'north':[(1,1,0),(1,0,0),(0,0,0),(0,1,0)],
 'south':[(0,1,1),(0,0,1),(1,0,1),(1,1,1)],
 'west':[(0,1,0),(0,0,0),(0,0,1),(0,1,1)],
 'east':[(1,1,1),(1,0,1),(1,0,0),(1,1,0)]}

def bounds(faces):
 points=[p for f in faces for p in f['points']]
 if not points:raise ValueError('Empty equipment mesh')
 return [min(p[i] for p in points) for i in range(3)],[max(p[i] for p in points) for i in range(3)]

def map_points(faces,fn):
 result=copy.deepcopy(faces)
 for face in result:face['points']=[list(fn(p)) for p in face['points']]
 return result

def elements_mesh(model):
 faces=[]
 for index,el in enumerate(model['elements']):
  lo,hi=el['from'],el['to'];rot=el.get('rotation');name=el.get('name',str(index))
  if len(lo)!=3 or len(hi)!=3 or any(not math.isfinite(v) for v in lo+hi) or any(lo[i]>=hi[i] for i in range(3)):
   raise ValueError('Invalid volume: '+name)
  if rot and (rot.get('axis') not in ('x','y','z') or not math.isfinite(rot.get('angle',float('nan')))):
   raise ValueError('Invalid rotation: '+name)
  for side,fd in el['faces'].items():
   pts=[]
   for v in FACE_VERTS[side]:
    p=[hi[i] if v[i] else lo[i] for i in range(3)]
    if rot:
     o=rot['origin'];r=g.mv(g._rot(rot['axis'],rot['angle']),[p[i]-o[i] for i in range(3)])
     p=[o[i]+r[i]*(1/math.cos(math.radians(rot['angle'])) if rot.get('rescale') and 'xyz'[i]!=rot['axis'] else 1) for i in range(3)]
    pts.append(p)
   u0,v0,u1,v1=fd.get('uv',[0,0,16,16]);uv=[(u0,v0),(u0,v1),(u1,v1),(u1,v0)]
   n=fd.get('rotation',0)//90;uv=uv[n:]+uv[:n]
   texture=fd['texture']
   for _ in range(8):
    if not texture.startswith('#'):break
    texture=model['textures'][texture[1:]]
   if texture.startswith('#'):raise ValueError('Cyclic texture reference')
   faces.append({'part':name,'points':pts,'uv':[[u/16,v/16] for u,v in uv],'texture':texture,'metal':True})
 return faces

def armor_mesh(compiled,item_id,root):
 from .export import cube_faces
 poses=json.loads((Path(root)/'equipment/poses.json').read_text());faces=[]
 item=next(i for i in compiled['profiles']['standard'] if i['id']==item_id)
 for b in item['boxes']:
  pivot=b.get('pivot',[0,0,0]);x,y,z=b.get('rotation',[0,0,0]);rotation=g.mm(g._rot('z',z),g.mm(g._rot('y',y),g._rot('x',x)))
  bone=poses['bones'][b['bone']]
  for corners,(u0,v0,u1,v1) in cube_faces(b['size'],b['uv'],compiled['texel_density']):
   points=[]
   for corner in corners:
    local=[b['from'][i]+b['size'][i]*corner[i]-pivot[i] for i in range(3)]
    rotated=g.mv(rotation,local);p=[rotated[i]+pivot[i]+bone[i] for i in range(3)]
    points.append([-p[0],24-p[1],p[2]])
   uv=[[u1,v0],[u0,v0],[u0,v1],[u1,v1]]
   faces.append({'part':b['name'],'points':points,'uv':[[u/compiled['texture_size'],v/compiled['texture_size']] for u,v in uv],
                 'texture':compiled['texture'].replace('textures/','').removesuffix('.png'),'metal':True})
 return faces

def display_config(root=None):
 return json.loads(((Path(root) if root else Path(__file__).resolve().parents[2])/'equipment/display.json').read_text())

def gui_profile(armor=False,root=None):
 config=display_config(root)['gui']
 return config['armor_rotation' if armor else 'weapon_rotation'],config['extent']

def gui_transform(faces,rotation=None,extent=None):
 if rotation is None:rotation,default_extent=gui_profile()
 else:default_extent=gui_profile()[1]
 if extent is None:extent=default_extent
 r=g.euler_xyz(rotation)
 rotated=map_points(faces,lambda p:g.mv(r,[p[i]-8 for i in range(3)]));lo,hi=bounds(rotated)
 scale=min(4,extent/max(hi[0]-lo[0],hi[1]-lo[1],.01))
 return {'rotation':list(rotation),'translation':[-(lo[i]+hi[i])*.5*scale for i in range(3)],'scale':[scale]*3}

def item_display(faces,armor=False,grip=None,root=None):
 config=display_config(root);settings=config['object'];gui=gui_transform(faces,*gui_profile(armor,root))
 lo,hi=bounds(faces);center=[(lo[i]+hi[i])*.5 for i in range(3)]
 def centered(rotation,scale,y=0):
  r=g.euler_xyz(rotation);offset=g.mv(r,[(center[i]-8)*scale for i in range(3)])
  return {'rotation':rotation,'translation':[-offset[0],y-offset[1],-offset[2]],'scale':[scale]*3}
 size=max(hi[i]-lo[i] for i in range(3));fixed=centered(settings['fixed_rotation'],min(settings['fixed_scale'],settings['fixed_extent']/size));ground=centered(settings['ground_rotation'],min(settings['ground_scale'],settings['ground_extent']/size),settings['ground_lift'])
 if grip:
  from tools import gen_qingfeng_sword_model as q
  third=q.derive('thirdperson_righthand',grip,anchor_target=q.FIST_CENTER_ITEM,scale=config['tool']['third_scale'])
  first=q.derive('firstperson_righthand',grip,anchor_old=q.OLD_HANDLE_CENTER,scale=config['tool']['first_scale'])
  left=q.mirror
 else:
  third=centered(settings['third_rotation'],min(settings['third_scale'],settings['third_extent']/size));first=centered(settings['first_rotation'],min(settings['first_scale'],settings['first_extent']/size))
  def left(t):return t
 return {'gui':gui,'ground':ground,'fixed':fixed,'head':fixed,'thirdperson_righthand':third,'thirdperson_lefthand':left(third),
         'firstperson_righthand':first,'firstperson_lefthand':left(first)}

def obj_assets(faces,resource,display=None):
 """Return relative assets paths. v uses block units; vt is Minecraft's top-down 0..1 UV."""
 name=resource.split(':',1)[1];namespace=resource.split(':',1)[0]
 textures=list(dict.fromkeys(f['texture'] for f in faces));materials={t:'m'+str(i) for i,t in enumerate(textures)}
 lines=['# Authored equipment mesh; generated, do not edit.',f'mtllib {namespace}:models/{name}.mtl','g geometry'];n=0
 for index,f in enumerate(faces):
  if any(not math.isfinite(c) for p in f['points'] for c in p):raise ValueError('Nonfinite mesh')
  a,b,c=f['points'][:3];normal=g.normalize(g.cross([b[i]-a[i] for i in range(3)],[c[i]-a[i] for i in range(3)]))
  lines.extend([f'# {f["part"]}',f'usemtl {materials[f["texture"]]}'])
  lines.extend('v '+' '.join(format(v/16,'.9g') for v in p) for p in f['points'])
  lines.extend('vt '+' '.join(format(v,'.9g') for v in uv) for uv in f['uv'])
  lines.extend(['vn '+' '.join(format(v,'.9g') for v in normal)]*4)
  lines.append('f '+' '.join(f'{n+i}/{n+i}/{n+i}' for i in range(1,5)));n+=4
 mtl='\n'.join(f'newmtl {materials[t]}\nKd 1 1 1\nKa 0 0 0\nd 1\nmap_Kd {t}\n' for t in textures)
 model={'loader':'neoforge:obj','model':f'{namespace}:models/{name}.obj','automatic_culling':False,'shade_quads':True,
        'flip_v':False,'emissive_ambient':False,'gui_light':'side','textures':{'particle':textures[0]}}
 if display:model['display']=display
 return {f'assets/{namespace}/models/{name}.obj':('\n'.join(lines)+'\n').encode(),f'assets/{namespace}/models/{name}.mtl':mtl.encode()},model

def read_obj(data,material_data):
 """Read our emitted subset for preview/parity tests, without substituting another shape."""
 materials={};current=None
 for line in material_data.decode().splitlines():
  bits=line.split()
  if not bits:continue
  if bits[0]=='newmtl':current=bits[1]
  if bits[0]=='map_Kd':materials[current]=bits[1]
 vertices=[];uv=[];faces=[];material=None;part=''
 for line in data.decode().splitlines():
  bits=line.split()
  if not bits:continue
  if bits[0]=='v':vertices.append([float(v)*16 for v in bits[1:]])
  elif bits[0]=='vt':uv.append([float(v) for v in bits[1:]])
  elif bits[0]=='usemtl':material=materials[bits[1]]
  elif line.startswith('# '):part=line[2:]
  elif bits[0]=='f':
   indices=[tuple(int(i)-1 for i in s.split('/')) for s in bits[1:]]
   faces.append({'part':part,'points':[vertices[i[0]] for i in indices],'uv':[uv[i[1]] for i in indices],'texture':material})
 return faces
