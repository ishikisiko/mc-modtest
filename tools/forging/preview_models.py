"""Render the exported meshes, not hand-drawn substitutes. No game/server required."""
from pathlib import Path
import argparse
import io
import json
from collections import defaultdict
import numpy as np
from PIL import Image,ImageDraw,ImageFont
from tools.forging.models import Catalogue,ROOT
from tools.equipment import mesh
from tools.combat_preview import item_model as im

def render(faces,display,textures,size=128):
 quads=[im.Quad(f['points'],uv=[[u*16,v*16] for u,v in f['uv']],tex=textures[f['texture']]) for f in faces]
 quads=im.transform_quads(quads,im.display_matrix(display))
 fit=np.array([[-.5,-.5,0],[.5,.5,0]])
 color=im.render(quads,im.Camera((0,0,1)),size,fit_points=fit,margin=.035,bg=(0,0,0),ss=2)[0]
 masks=[im.Quad(q.pos,color=(1,1,1),shade=False) for q in quads]
 alpha=np.asarray(im.render(masks,im.Camera((0,0,1)),size,fit_points=fit,margin=.035,bg=(0,0,0),ss=2)[0])[:,:,0:1]/255.
 return Image.fromarray(np.clip(np.asarray(color)+(1-alpha)*np.array([231,221,189]),0,255).astype(np.uint8))

def svg(image):
 image=image.resize((40,40),Image.Resampling.NEAREST).quantize(colors=32).convert('RGB');paths=defaultdict(list)
 for y in range(40):
  x=0
  while x<40:
   color=image.getpixel((x,y));start=x;x+=1
   while x<40 and image.getpixel((x,y))==color:x+=1
   paths[color].append(f'M{start},{y}h{x-start}v1h{start-x}Z')
 return '<svg viewBox="0 0 40 40" shape-rendering="crispEdges">'+''.join('<path fill="#%02x%02x%02x" d="%s"/>'%(*c,''.join(v)) for c,v in paths.items())+'</svg>'

def generate(out,root=ROOT):
 out=Path(out);out.mkdir(parents=True,exist_ok=True);catalogue=Catalogue(root);outputs=catalogue.outputs();base=Path(root)/'src/main/resources'
 textures={}
 allrefs={f['texture'] for rid in catalogue.kinds for stage in range(6) for f in catalogue.stage(rid,stage)}
 for ref in allrefs:
  ns,path=ref.split(':');rel=f'assets/{ns}/textures/{path}.png';data=outputs.get(base/rel) or catalogue.material_outputs.get(rel) or (base/rel).read_bytes()
  textures[ref]=np.asarray(Image.open(io.BytesIO(data)).convert('RGBA'),dtype=float)/255
 order=['qingfeng_sword','lingxiao_spear','forged_pickaxe','forged_axe','forged_shovel','forged_hoe','xuantie_helmet','xuantie_chestplate','xuantie_leggings','xuantie_boots']
 font=ImageFont.truetype(str(base/'assets/myvillage/font/book.ttf'),21)
 sheet=Image.new('RGB',(980,1180),(231,221,189));draw=ImageDraw.Draw(sheet);draw.text((25,20),'同源立体模型 · 全阶段预览',font=font,fill='#303b3c')
 headers=['粗坯／料片','成形','淬火','回火','精磨／修整','装配成器']
 for i,title in enumerate(headers):draw.text((130+140*i,65),title,font=font,fill='#625845')
 rows=[];manifest={}
 for row,rid in enumerate(order):
  rows.append('<div class="name">'+catalogue.names[rid]+'</div>');draw.text((12,135+105*row),catalogue.names[rid],font=font,fill='#303b3c')
  manifest[rid]=[]
  for stage in range(6):
   source=catalogue.stage(rid,stage);ref='myvillage:preview/'+rid+'_'+str(stage)
   # Round-trip through the same OBJ/MTL that Minecraft consumes before rendering.
   files,_=mesh.obj_assets(source,ref);faces=mesh.read_obj(files[f'assets/myvillage/models/preview/{rid}_{stage}.obj'],files[f'assets/myvillage/models/preview/{rid}_{stage}.mtl'])
   display=mesh.gui_transform(source,*mesh.gui_profile(catalogue.armor(rid),root))
   image=render(faces,display,textures);image.save(out/f'{rid}-{stage}.png');sheet.paste(image.resize((104,104)),(130+140*stage,100+105*row))
   rows.append('<div class="cell">'+svg(image)+'</div>');manifest[rid].append({'stage':stage,'quads':len(faces),'parts':len({f['part'] for f in faces})})
 sheet.save(out/'sheet.png')
 html='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>*{box-sizing:border-box}body{margin:0;padding:18px;background:#e7ddbd;color:#303b3c;font:14px system-ui}h2{margin:0 0 8px;font-size:21px}p{line-height:1.6;color:#625845}.grid{display:grid;grid-template-columns:70px repeat(6,minmax(0,1fr));gap:6px;align-items:center;text-align:center}.cell svg{display:block;width:100%}.name{font-weight:600;font-size:12px}.label{font-size:12px}footer{border-top:1px solid #b7a882;margin-top:18px;padding-top:8px}@media(max-width:430px){body{padding:10px}.grid{grid-template-columns:45px repeat(6,minmax(0,1fr));gap:3px}.name,.label{font-size:10px}}</style><h2>同一件器物 · 立体结构贯穿锻造</h2><p>实际模型及材质的离线渲染。剑、枪沿用本体；工具增加铁头、木柄与连接结构；防具由原甲片和衬垫组成。</p><div class="grid"><div></div>'''+''.join('<div class="label">'+h+'</div>' for h in headers)+''.join(rows)+'''</div><footer><p>成形、淬火、回火保留结构，通过锻痕、氧化色和表面处理区分。精磨后露出成品材质，装配时加入柄、饰件或衬垫。</p><p>这是当前候选模型预览；游戏内光照、持握与资源加载另做代表性核对。</p></footer></html>'''
 (out/'index.html').write_text(html);(out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');return out/'index.html'

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();print(generate(a.out))
