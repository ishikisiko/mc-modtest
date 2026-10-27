"""Build the complete, unregistered 3D design study with the owning equipment pipeline."""
from __future__ import annotations
import argparse,base64,copy,gzip,hashlib,io,json,math,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT))
import numpy as np
from PIL import Image,ImageDraw
from model import ARMOR,MATERIALS,TOOLS
from tools.equipment import catalogue,export,mesh,web,preview
from tools.equipment.geometry import encode_png
from tools.combat_preview import item_model as im
from tools.ingot_shape import FLAT_TOP,FLAT_FRONT,FLAT_SIDE


def jwrite(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n')
def color(s):return tuple(int(s[i:i+2],16) for i in (1,3,5))
def blend(a,b,t):return tuple(round(x+(y-x)*max(0,min(1,t))) for x,y in zip(a,b))
def cloud(draw,rect,ink=(116,137,144,255),scale=.40):
 x0,y0,x1,y1=rect;w=x1-x0;h=y1-y0
 if w<13 or h<10:return
 cx=(x0+x1)/2;cy=(y0+y1)/2
 # One modest cloud hook, not a luminous crest.
 points=[]
 for t in np.linspace(0,math.pi*2.0,45):
  r=(.22-.020*t)*min(w,h)*scale/.40
  points.append((cx+math.cos(t)*r,cy+math.sin(t)*r*.72))
 points += [(cx+w*.09,cy+h*.15),(cx+w*.23,cy+h*.05)]
 draw.line([(x,y+1) for x,y in points],fill=(36,53,64,255),width=1)
 draw.line(points,fill=ink,width=1)


def armor_texture(model):
 """Paint the atlas of the actual compiled boxes, not a separate display illustration."""
 size=model['texture_size'];density=model['texel_density'];image=Image.new('RGBA',(size,size));d=ImageDraw.Draw(image)
 source={b['name']:b for i in ARMOR['items'] for b in i['boxes']};done=set()
 for pieces in model['profiles'].values():
  for item in pieces:
   for b in item['boxes']:
    key=(b['name'],tuple(b['uv']))
    if key in done:continue
    done.add(key);src=source[b['name']];mat=MATERIALS[src['material']];base,light,dark=[color(mat[k]) for k in ['base','light','dark']]
    for fi,(_,r) in enumerate(export.cube_faces(b['size'],b['uv'],density)):
     l,t,rr,bb=[math.floor(min(r[0],r[2])),math.floor(min(r[1],r[3])),math.ceil(max(r[0],r[2])),math.ceil(max(r[1],r[3]))];w=rr-l;h=bb-t
     for y in range(h):
      for x in range(w):
       u=x/max(1,w-1);v=y/max(1,h-1)
       if mat['pattern']=='metal':
        shine=.19*math.exp(-((u-.25)/.19)**2)+.07*(1-v)
        rgb=blend(base,light,shine)
        if x==0 or y==0:rgb=blend(base,light,.30)
        if x==w-1 or y==h-1:rgb=blend(base,dark,.35)
        noise=((x*17+y*23)%13-6)*.08;rgb=tuple(max(0,min(255,round(c+noise))) for c in rgb)
       elif mat['pattern']=='cloth':rgb=blend(base,light,.025 if (x+y)%2 else .07)
       else:rgb=blend(base,dark,.08 if x%4 else .18)
       d.point((l+x,t+y),fill=(*rgb,255))
     # Subtle treatment on broad, continuous faces only. Side walls stay unbroken.
     if fi in (3,5) and any(n in b['name'] for n in ['胸腹','背护面']):cloud(d,(l+2,t+2,rr-2,bb-2),scale=.30)
     if fi==3 and b['name']=='额面' and w>12:
      # A small central stamped mark with just a few texels.
      cx=(l+rr)//2;cy=(t+bb)//2;d.line([(cx-3,cy+1),(cx,cy-1),(cx+3,cy+1)],fill=(110,130,137,255),width=1)
 return image


def material_tiles():
 image=Image.new('RGBA',(256,256));d=ImageDraw.Draw(image);rects={}
 for i,(name,mat) in enumerate(MATERIALS.items()):
  x=(i%4)*64;y=(i//4)*64;rects[name]=[x,y,x+64,y+64];b,li,da=[color(mat[k]) for k in ['base','light','dark']]
  for py in range(64):
   for px in range(64):
    if name=='xg_wood':t=.1+.22*(.5+.5*math.sin(px*.40+math.sin(py*.10)));rgb=blend(b,li,t)
    elif mat['pattern']=='metal':rgb=blend(b,li,.08+.14*math.exp(-((px/63-.3)/.18)**2))
    else:rgb=blend(b,li,.04 if (px+py)%2 else .02)
    d.point((x+px,y+py),fill=(*rgb,255))
 # A separate steel tile carries hand-drawn engraving, solely mapped to selected faces.
 image.paste(image.crop((0,0,64,64)),(192,64));rects['engraved']=[192,64,256,128]
 cloud(d,(200,72,248,120),scale=.32)
 return image,rects


def uvrect(tile):
 x,y,xx,yy=tile;return [(x/256,y/256),(xx/256,y/256),(xx/256,yy/256),(x/256,yy/256)]
def face(points,part,mat='xg_steel',uv=None):return dict(points=[list(p) for p in points],part=part,material=mat,texture='myvillage:equipment/xuangang_objects',uv=uv)

def block(lo,hi,part,mat):
 out=[]
 for vs in mesh.FACE_VERTS.values():out.append(face([[hi[i] if c[i] else lo[i] for i in range(3)] for c in vs],part,mat))
 return out

def beam(p,q,width,part,mat,depth=None):
 p=np.array(p,dtype=float);q=np.array(q,dtype=float);axis=(q-p)/np.linalg.norm(q-p);helper=np.array([1.,0,0]) if abs(axis[0])<.8 else np.array([0.,1,0]);u=np.cross(axis,helper);u=u/np.linalg.norm(u)*width/2;v=np.cross(axis,u);v=v/np.linalg.norm(v)*(depth or width)/2
 rings=[[r-u-v,r+u-v,r+u+v,r-u+v] for r in [p,q]];faces=[face(rings[0][::-1],part,mat),face(rings[1],part,mat)]
 for i in range(4):j=(i+1)%4;faces.append(face([rings[0][i],rings[0][j],rings[1][j],rings[1][i]],part,mat))
 return faces


def triangulate(poly):
 pts=[np.array(p,dtype=float) for p in poly];signed=sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(pts,pts[1:]+pts[:1]));inds=list(range(len(pts))) if signed>0 else list(range(len(pts)-1,-1,-1));out=[]
 def cross(a,b,c):
  u,v=b-a,c-a;return float(u[0]*v[1]-u[1]*v[0])
 while len(inds)>3:
  found=False
  for i in range(len(inds)):
   tri=[inds[(i-1)%len(inds)],inds[i],inds[(i+1)%len(inds)]];a,b,c=[pts[j] for j in tri]
   if cross(a,b,c)<=1e-7:continue
   if any(all(cross(u,v,pts[j])>=-1e-8 for u,v in [(a,b),(b,c),(c,a)]) for j in inds if j not in tri):continue
   out.append(tri);inds.pop(i);found=True;break
  if not found:raise ValueError('Non-simple polygon')
 out.append(inds);return out

def cap_quads(points,part,mat,reverse=False):
 # Planar cap is tessellated without seams; every UV shares one projection.
 points=np.array(points);axis=int(np.argmax(np.abs(np.cross(points[1]-points[0],points[2]-points[0]))));poly=np.delete(points,axis,axis=1);out=[]
 for indices in triangulate(poly.tolist()):
  tri=points[indices];center=tri.mean(axis=0)
  for i in range(3):
   p=tri[i];q=tri[(i+1)%3];r=tri[(i-1)%3];quad=[p,(p+q)/2,center,(r+p)/2]
   out.append(face(quad[::-1] if reverse else quad,part,mat))
 return out

def extrude(poly,thickness,part,mat='xg_steel',engraved=False):
 pts=np.array(poly);center=pts.mean(axis=0);inner=center+(pts-center)*.94;out=[]
 loops=[np.column_stack((inner,np.full(len(pts),-thickness/2))),np.column_stack((pts,np.full(len(pts),-thickness*.28))),np.column_stack((pts,np.full(len(pts),thickness*.28))),np.column_stack((inner,np.full(len(pts),thickness/2)))]
 for k in range(3):
  for i in range(len(pts)):
   j=(i+1)%len(pts);out.append(face([loops[k][i],loops[k][j],loops[k+1][j],loops[k+1][i]],part,mat if k==1 else 'xg_edge'))
 out+=cap_quads(loops[0],part,'engraved' if engraved else mat,True);out+=cap_quads(loops[-1],part,mat)
 return out


def object_mesh(kind):
 if kind=='ingot':
  # Reconstruct the existing flat-ingot top polygon at its canonical oblique view.
  yaw=-.65;pitch=.55;z=np.array([math.sin(yaw)*math.cos(pitch),math.sin(pitch),-math.cos(yaw)*math.cos(pitch)]);x=np.cross([0,1,0],z);x/=np.linalg.norm(x);y=np.cross(z,x);m=np.array([[x[0],x[2]],[y[0],y[2]]])
  def vertex(p,height):
   a,b=np.linalg.solve(m,np.array([(p[0]-16)*.55,(16-p[1])*.55])-np.array([0,y[1]*height]));return [a,height,b]
  top=[vertex(p,2.6) for p in FLAT_TOP];bottom=[vertex([p[0],p[1]+4],0) for p in FLAT_TOP]
  # Retain a low slab and subtly inset the lower footprint.
  center=np.mean(bottom,axis=0);bottom=[(center+(np.array(p)-center)*[.94,1,.94]).tolist() for p in bottom]
  out=cap_quads(top,'完整扁锭','engraved');out+=cap_quads(bottom,'完整扁锭','xg_dark',True)
  for i in range(len(top)):
   j=(i+1)%len(top);out.append(face([top[i],bottom[i],bottom[j],top[j]],'完整扁锭','xg_steel'))
  return out
 spec=TOOLS[kind];out=[]
 # An octagonal full-length shaft and metal socket. One continuous grip sleeve.
 rings=[]
 for yy,r in [(0, .62),(2,.72),(18,.65),(21,.62)]:rings.append([[math.cos(a)*r,yy,math.sin(a)*r] for a in np.linspace(math.pi/8,2*math.pi+math.pi/8,8,endpoint=False)])
 for k in range(len(rings)-1):
  for i in range(8):j=(i+1)%8;out.append(face([rings[k][i],rings[k][j],rings[k+1][j],rings[k+1][i]],'整根乌木柄','xg_wood'))
 out+=cap_quads(rings[0],'柄底','xg_wood',True);out+=cap_quads(rings[-1],'柄顶','xg_wood')
 out+=beam([0,.15,0],[0,.75,0],1.48,'柄尾箍','xg_dark')
 if kind!='hoe':
  out+=extrude(spec['polygon'],spec['thickness'],'完整'+spec['name']+'首',engraved=spec['motif'])
  out+=beam([0,18.5,0],[0,21.35,0],1.8,'嵌柄座','xg_dark');out+=beam([-.98,20.4,0],[.98,20.4,0],.35,'横销','xg_edge')
 else:
  out+=beam(spec['socket'],spec['bridge_end'],1.3,'连体锄颈','xg_steel',1.15)
  # The blade bends down at the end of the neck: a true three-dimensional hoe.
  poly=[[-3.0,19],[-3.35,14.5],[3.35,14.5],[3.0,19]]
  head=extrude(poly,spec['thickness'],'完整锄面',engraved=True)
  for f in head:
   for p in f['points']:p[2]+=5.2+(19-p[1])*.17
  out+=head;out+=beam([0,18.8,0],[0,21,0],1.8,'嵌柄座','xg_dark')
 return out


def map_uv(faces,tiles):
 """One continuous projection per part, so the tessellation does not become decoration."""
 bounds={}
 for f in faces:
  key=(f['part'],f['material']);bounds.setdefault(key,[]).extend(f['points'])
 for f in faces:
  pts=np.array(f['points']);normal=np.cross(pts[1]-pts[0],pts[2]-pts[0]);axis=int(np.argmax(np.abs(normal)));axes=[i for i in range(3) if i!=axis];allpts=np.array(bounds[(f['part'],f['material'])]);lo=allpts.min(axis=0);hi=allpts.max(axis=0);u0,v0,u1,v1=tiles[f['material']]
  f['uv']=[[(u0+1+(p[axes[0]]-lo[axes[0]])/max(.001,hi[axes[0]]-lo[axes[0]])*(u1-u0-2))/256,(v0+1+(p[axes[1]]-lo[axes[1]])/max(.001,hi[axes[1]]-lo[axes[1]])*(v1-v0-2))/256] for p in pts]
 return faces


def quads_of(faces,tex=None):return [im.Quad(f['points'],uv=np.array(f['uv'])*16,tex=tex,tag=f['part']) for f in faces]

def build(out):
 out.mkdir(parents=True,exist_ok=True);catalogue.validate(ARMOR,MATERIALS);model,_=export.compile_armor(ARMOR,MATERIALS);atlas=armor_texture(model);pixels=np.array(atlas).tolist();atlaspath=out/'assets/myvillage/textures/equipment/xuangang_armor.png';atlaspath.parent.mkdir(parents=True,exist_ok=True);atlas.save(atlaspath)
 jwrite(out/'authored/equipment.json',ARMOR);jwrite(out/'authored/materials.json',MATERIALS);jwrite(out/'assets/myvillage/equipment/xuangang_armor.json',model)
 # Export the exact compiled armor meshes through the existing OBJ owner.
 allmeshes={};roundtrip=[]
 for item in ARMOR['items']:
  id=item['id'].split(':')[1];faces=mesh.armor_mesh(model,item['id'],ROOT);files,itemmodel=mesh.obj_assets(faces,'myvillage:equipment/items/'+id,mesh.item_display(faces,armor=True,root=ROOT))
  for rel,data in files.items():p=out/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
  jwrite(out/f'assets/myvillage/models/item/{id}.json',itemmodel)
  readback=mesh.read_obj(files[f'assets/myvillage/models/equipment/items/{id}.obj'],files[f'assets/myvillage/models/equipment/items/{id}.mtl'])
  assert np.allclose([f['points'] for f in faces],[f['points'] for f in readback],atol=1e-6);roundtrip.append(id);allmeshes[id]=faces
 texture,tiles=material_tiles();texture.save(out/'assets/myvillage/textures/equipment/xuangang_objects.png');objects=[]
 for kind in ['ingot','hoe','pickaxe','axe']:
  id='xuangang_'+kind;faces=map_uv(object_mesh(kind),tiles);files,itemmodel=mesh.obj_assets(faces,'myvillage:equipment/items/'+id,mesh.item_display(faces,root=ROOT));allmeshes[id]=faces
  for rel,data in files.items():p=out/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
  jwrite(out/f'assets/myvillage/models/item/{id}.json',itemmodel)
  readback=mesh.read_obj(files[f'assets/myvillage/models/equipment/items/{id}.obj'],files[f'assets/myvillage/models/equipment/items/{id}.mtl']);assert np.allclose([f['points'] for f in faces],[f['points'] for f in readback],atol=1e-6);roundtrip.append(id)
  objects.append({'id':id,'name':'玄罡锭' if kind=='ingot' else TOOLS[kind]['name'],'vertices':web.triangles(quads_of(faces))})
 # Existing armor is loaded as an explicit comparison under the SAME camera and lighting.
 refspec=json.loads((ROOT/'equipment/xuantie_armor/equipment.json').read_text());refmats=json.loads((ROOT/'equipment/materials.json').read_text())['materials'];refmodel,refpixels=export.compile_armor(refspec,refmats)
 newdata=web.payload(ARMOR,model,pixels,ROOT);refdata=web.payload(refspec,refmodel,refpixels,ROOT)
 data={'new':newdata,'reference':refdata,'objects':objects,'object_texture':web.data_uri(np.array(texture).tolist()),'reference_source':__import__('subprocess').check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()}
 encoded=base64.b64encode(gzip.compress(json.dumps(data,ensure_ascii=False,separators=(',',':')).encode(),mtime=0)).decode()
 html=(Path(__file__).with_name('viewer.html')).read_text().replace('__DATA__',encoded);(out/'index.html').write_text(html)
 checks={'items':list(allmeshes),'obj_roundtrip':roundtrip,'profiles':list(model['profiles']),'poses':list(newdata['poses']),'game_runtime':'not_verified','owner_appearance':'not_verified','scope':'完整 3D 几何/纹理/OBJ 与可旋转预览；未注册未打包','source_commit':data['reference_source']}
 for id,faces in allmeshes.items():
  assert all(np.isfinite(f['points']).all() and np.isfinite(f['uv']).all() for f in faces)
  assert all(0<=v<=1 for f in faces for uv in f['uv'] for v in uv)
  assert all(np.linalg.norm(np.cross(np.array(f['points'][1])-f['points'][0],np.array(f['points'][2])-f['points'][0]))>1e-8 for f in faces),id+' has a degenerate face'
  lo,hi=mesh.bounds(faces);assert all(hi[i]-lo[i]>.1 for i in range(3)),id+' is not a volume'
 checks['bounds']={id:mesh.bounds(f) for id,f in allmeshes.items()};checks['input_sha256']={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [Path(__file__),Path(__file__).with_name('model.py'),Path(__file__).with_name('viewer.html'),ROOT/'equipment/xuantie_armor/equipment.json',ROOT/'equipment/materials.json',ROOT/'equipment/poses.json']};jwrite(out/'checks.json',checks)
 with zipfile.ZipFile(out/'xuangang-model-study.zip','w',zipfile.ZIP_DEFLATED) as z:
  for src in ['model.py','build.py','viewer.html','README.md']:
   source=Path(__file__).with_name(src)
   if source.exists():z.write(source,'source/'+src)
  for path in sorted(out.rglob('*')):
   if path.is_file() and path.suffix!='.zip':z.write(path,path.relative_to(out))
 print(json.dumps({'out':str(out),'items':len(allmeshes),'obj_roundtrip':len(roundtrip),'html_bytes':len(html.encode())},ensure_ascii=False))

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);a=p.parse_args();build(a.out.resolve())
