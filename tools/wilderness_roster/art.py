"""Offline real-model previews, UV truth and deterministic imagegen material normalization."""
from __future__ import annotations
import argparse,base64,io,json,uuid
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
from tools.beastgen import anim,cuboid
from tools.beastgen.build import Built as BeastBuilt
from tools.npcgen.build import Built as NpcBuilt
from tools.gen_wilderness_roster import catalogue,ROOT

OUT=ROOT/'out/preview/wilderness-roster'
def built(name):return NpcBuilt(name) if name=='ridge_reaver' else BeastBuilt(name)

def prepare():
    OUT.mkdir(parents=True,exist_ok=True)
    sheet=Image.new('RGBA',(384,256),(34,36,40,255));mask=Image.new('L',sheet.size,0);manifest=[]
    for i,m in enumerate(catalogue()['mobs']):
        name=m['id'];b=built(name);p=ROOT/'art/entities'/name;p.mkdir(parents=True,exist_ok=True)
        from tools.gen_qingfeng_sword_model import encode_png
        tex=Image.open(io.BytesIO(encode_png(b.texture))).convert('RGBA');w,h=tex.size
        if w>128 or h>128:raise ValueError('sheet requires at most 128-square native atlas')
        rect=(i%3*128,i//3*128);sheet.paste(tex,rect,tex);mask.paste(tex.getchannel('A'),rect)
        faces=[]
        for bone,cube in b.model.cubes():
            for face,(u,v,fw,fh) in cuboid.uv_regions(cube).items():
                faces.append({'id':cube.name+'.'+face.lower(),'bone':bone.name,'rect':[u,v,fw,fh]})
        (p/'uv_truth.json').write_text(json.dumps({'size':[w,h],'faces':faces},indent=2)+'\n')
        tex.getchannel('A').save(p/'uv_islands_mask.png')
        # Editable Blockbench source mirrors the original unrotated group coordinates.
        pivots={};groups={};elements=[];roots=[]
        for bone in b.model.bones:
            parent=pivots.get(bone.parent,(0,0,0));origin=tuple(parent[k]+bone.pivot[k] for k in range(3));pivots[bone.name]=origin
            uid=str(uuid.uuid5(uuid.NAMESPACE_URL,name+'/'+bone.name));g={'name':bone.name,'origin':list(origin),'rotation':list(bone.rotation),'uuid':uid,'children':[]}
            groups[bone.name]=g
            (groups[bone.parent]['children'] if bone.parent else roots).append(g)
            for cube in bone.cubes:
                cid=str(uuid.uuid5(uuid.NAMESPACE_URL,name+'/'+cube.name));lo=[origin[k]+cube.origin[k] for k in range(3)]
                elements.append({'name':cube.name,'from':lo,'to':[lo[k]+cube.size[k] for k in range(3)],'origin':list(origin),'box_uv':True,'uv_offset':list(cube.uv),'inflate':cube.inflate,'uuid':cid,'type':'cube'})
                g['children'].append(cid)
        bb={'meta':{'format_version':'4.10','model_format':'free','box_uv':True},'name':name,'resolution':{'width':w,'height':h},'elements':elements,'outliner':roots,'textures':[{'name':name+'.png','id':'0','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,name+'/texture')),'source':'data:image/png;base64,'+base64.b64encode(encode_png(b.texture)).decode()}]}
        (p/(name+'.bbmodel')).write_text(json.dumps(bb,separators=(',',':'))+'\n')
        manifest.append({'id':name,'rect':[*rect,w,h]})
    sheet.resize((1536,1024),Image.Resampling.NEAREST).save(OUT/'uv-work-base.png')
    mask.resize((1536,1024),Image.Resampling.NEAREST).save(OUT/'uv-work-mask.png')
    (OUT/'uv-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')

def import_material(path):
    im=Image.open(path).convert('RGBA')
    if abs(im.width/im.height-1.5)>.01:raise ValueError('imagegen aspect ratio drift')
    im=im.resize((384,256),Image.Resampling.NEAREST)
    for m in json.loads((OUT/'uv-manifest.json').read_text()):
        name=m['id'];x,y,w,h=m['rect'];p=ROOT/'art/entities'/name
        tex=im.crop((x,y,x+w,y+h));mask=Image.open(p/'uv_islands_mask.png').convert('L');tex.putalpha(mask)
        # Exact binary mask, including transparent gutters. Normalize palette using nearest colors.
        rgb=tex.convert('RGB').quantize(colors=32,dither=Image.Dither.NONE).convert('RGBA');rgb.putalpha(mask)
        rgb.save(p/'material_tones.png')
        (p/'texture_provenance.json').write_text(json.dumps({'generator':'built-in image_gen','source_sheet':'wilderness-roster/uv-material.raw.png','mask':'uv_islands_mask.png','normalization':'nearest, 32 colors, exact binary mask','usage':'bounded broad-material tone influence; native face/eye details preserved'},ensure_ascii=False,indent=2)+'\n')

def preview():
    from tools.beastgen.preview import Mesh,rows_to_array,fit_frame,render
    font=ImageFont.truetype(str(ROOT/'src/main/resources/assets/myvillage/font/panel_body.ttf'),20)
    cells=[]
    for m in catalogue()['mobs']:
        b=built(m['id']);mesh=Mesh(b.model,rows_to_array(b.texture),rows_to_array(b.glow) if hasattr(b,'glow') else None,scale=b.model.scale)
        quads=mesh.world(anim.posed_matrices(b.model));im=render(quads,fit_frame(quads,(-.8,.45,-1),400,370,ss=2),bg=(230,224,210))
        panel=Image.new('RGB',(400,410),(235,230,220));panel.paste(im,(0,0));ImageDraw.Draw(panel).text((15,376),m['name']+' · 实际模型',fill=(45,52,50),font=font)
        panel.save(OUT/(m['id']+'-model.png'));cells.append(panel)
        views=[]
        for camera in [(0,.1,-1),(-1,.1,0),(0,.1,1),(1,.1,0)]:views.append(render(quads,fit_frame(quads,camera,320,340,ss=2),bg=(230,224,210)))
        turn=Image.new('RGB',(1280,340));[turn.paste(v,(i*320,0)) for i,v in enumerate(views)];turn.save(OUT/(m['id']+'-turnaround.png'))
    page=Image.new('RGB',(1200,820));[page.paste(im,((i%3)*400,(i//3)*410)) for i,im in enumerate(cells)];page.save(OUT/'models.png')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('action',choices=['prepare','preview','import']);p.add_argument('source',nargs='?');a=p.parse_args()
    if a.action=='prepare':prepare()
    elif a.action=='import':import_material(a.source)
    else:preview()
