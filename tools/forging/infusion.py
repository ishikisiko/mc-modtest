"""鼎外炼灵表现：参数为 authored 输入，物品居中从各专用模型来源推导。"""
import json,math
from pathlib import Path
from tools.equipment import mesh
from tools.forging.models import Catalogue
from tools.gen_alchemy_assets import Canvas,png
ROOT=Path(__file__).resolve().parents[2]

def outputs(root=ROOT):
 config=json.loads((root/'tools/forging/infusion_visuals.json').read_text());catalogue=Catalogue(root)
 presentation={}
 for rid,faces in catalogue.sources.items():
  lo,hi=mesh.bounds(faces);center=[(a+b)/32 for a,b in zip(lo,hi)]
  extent=config['armor_extent'] if catalogue.armor(rid) else config['item_extent']
  presentation[rid]={'center':center,'scale':extent*16/max(b-a for a,b in zip(lo,hi)), 'rotate_z':0 if catalogue.armor(rid) else 90}
 config['items']=presentation
 base=root/'src/main/resources'
 out={base/'data/myvillage/forging/infusion_visuals.json':(json.dumps(config,ensure_ascii=False,indent=2)+'\n').encode()}
 # White transparent-edged strip; renderer supplies the authored jade/gold tint.
 rows=[]
 for y in range(16):
  a=round(255*max(0,1-abs((y+.5)/8-1))**.5)
  rows.append([(255,255,255,a)]*16)
 out[base/'assets/myvillage/textures/effect/infusion_ribbon.png']=png(rows)
 # Eight original geometric seal marks in a horizontal atlas. No external font or raster dependency.
 c=Canvas(128)
 for i in range(8):
  ox=i*16
  def line(x0,y0,x1,y1):c.line(ox+x0,y0,ox+x1,y1,(255,255,255,255))
  line(7,2,7,13);line(3,4,11,4);line(4,7,10,7)
  if i&1:line(4,10,10,10)
  else:line(4,9,4,12);line(10,9,10,12)
  if i&2:line(3,2,5,3);line(9,3,11,2)
  else:line(4,1,10,1)
  if i&4:line(3,13,6,10);line(8,10,11,13)
  else:line(4,13,10,13)
 out[base/'assets/myvillage/textures/effect/infusion_runes.png']=png(c.rows[:16])
 return out
