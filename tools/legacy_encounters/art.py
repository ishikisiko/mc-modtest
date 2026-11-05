"""真实模型预览、UV 真相、内置 imagegen 材质的确定性回贴。"""
from __future__ import annotations
import argparse,base64,hashlib,io,json,uuid
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
from tools.gen_legacy_encounters import ROOT,catalogue
from tools.beastgen.build import Built
from tools.beastgen import cuboid,anim
from tools.gen_qingfeng_sword_model import encode_png

WORK=ROOT/'work/entity-art/legacy_encounters'
OUT=ROOT/'reports/four-legacies/visuals'

def prepare():
    for m in catalogue()['mobs']:
        name=m['id'];b=Built(name);p=ROOT/'art/entities'/name;p.mkdir(parents=True,exist_ok=True)
        work=WORK/name;work.mkdir(parents=True,exist_ok=True)
        tex=Image.open(io.BytesIO(encode_png(b.texture))).convert('RGBA');w,h=tex.size
        faces=[]
        for bone,cube in b.model.cubes():
            for face,(u,v,fw,fh) in cuboid.uv_regions(cube).items():faces.append({'id':cube.name+'.'+face.lower(),'bone':bone.name,'rect':[u,v,fw,fh]})
        (p/'uv_truth.json').write_text(json.dumps({'size':[w,h],'faces':faces},indent=2)+'\n')
        tex.getchannel('A').save(p/'uv_islands_mask.png')
        canvas=Image.new('RGBA',(128,128),(34,36,40,255));canvas.paste(tex,(0,0),tex)
        mask=Image.new('L',(128,128));mask.paste(tex.getchannel('A'),(0,0))
        if not (work/'work_base.png').exists():canvas.resize((1024,1024),Image.Resampling.NEAREST).save(work/'work_base.png')
        if not (work/'composite_mask.png').exists():mask.resize((1024,1024),Image.Resampling.NEAREST).save(work/'composite_mask.png')
        guide=canvas.resize((1024,1024),Image.Resampling.NEAREST);draw=ImageDraw.Draw(guide)
        for f in faces:
            x,y,fw,fh=f['rect'];draw.rectangle((x*8,y*8,(x+fw)*8-1,(y+fh)*8-1),outline='#d9973f',width=1)
            if fw>=4 and fh>=4:draw.text((x*8+2,y*8+2),f['id'][:12],fill='#ffffff')
        if not (work/'face_overlay.png').exists():guide.save(work/'face_overlay.png')
        pivots={};groups={};elements=[];roots=[]
        for bone in b.model.bones:
            parent=pivots.get(bone.parent,(0,0,0));origin=tuple(parent[k]+bone.pivot[k] for k in range(3));pivots[bone.name]=origin
            uid=str(uuid.uuid5(uuid.NAMESPACE_URL,name+'/'+bone.name));group={'name':bone.name,'origin':list(origin),'rotation':list(bone.rotation),'uuid':uid,'children':[]}
            groups[bone.name]=group;(groups[bone.parent]['children'] if bone.parent else roots).append(group)
            for cube in bone.cubes:
                cid=str(uuid.uuid5(uuid.NAMESPACE_URL,name+'/'+cube.name));lo=[origin[k]+cube.origin[k] for k in range(3)]
                elements.append({'name':cube.name,'from':lo,'to':[lo[k]+cube.size[k] for k in range(3)],'origin':list(origin),'box_uv':True,'uv_offset':list(cube.uv),'inflate':cube.inflate,'uuid':cid,'type':'cube'});group['children'].append(cid)
        bb={'meta':{'format_version':'4.10','model_format':'free','box_uv':True},'name':name,'resolution':{'width':w,'height':h},'elements':elements,'outliner':roots,'textures':[{'name':name+'.png','id':'0','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,name+'/texture')),'source':'data:image/png;base64,'+base64.b64encode(encode_png(b.texture)).decode()}]}
        (p/(name+'.bbmodel')).write_text(json.dumps(bb,separators=(',',':'))+'\n')
    print(WORK)

def import_material(name,path):
    m=next(m for m in catalogue()['mobs'] if m['id']==name)
    image=Image.open(path).convert('RGBA')
    if image.width!=image.height:raise ValueError('UV candidate must preserve square working canvas')
    work=WORK/name;mask=Image.open(work/'composite_mask.png').convert('L');base=Image.open(work/'work_base.png').convert('RGBA')
    normalized=image.resize(base.size,Image.Resampling.NEAREST)
    composite=Image.composite(normalized,base,mask);composite.save(work/'composited.png')
    native=composite.resize((128,128),Image.Resampling.NEAREST)
    p=ROOT/'art/entities'/name;truth=json.loads((p/'uv_truth.json').read_text());w,h=truth['size']
    result=native.crop((0,0,w,h)).convert('RGB').quantize(colors=32,dither=Image.Dither.NONE).convert('RGBA')
    result.putalpha(Image.open(p/'uv_islands_mask.png').convert('L'));result.save(p/'material_tones.png')
    provenance={'generator':'built-in image_gen','candidate_sha256':hashlib.sha256(Path(path).read_bytes()).hexdigest(),'work_base_sha256':hashlib.sha256((work/'work_base.png').read_bytes()).hexdigest(),'concept_sha256':hashlib.sha256((WORK/'concept.png').read_bytes()).hexdigest(),'normalization':'nearest-neighbour 1024 square -> 128 square; crop to native UV size; binary island mask; 32 colors','usage':'broad material tones; native eyes, face, inscriptions and continuous silhouettes preserved','human_verdict':'pending'}
    (p/'texture_provenance.json').write_text(json.dumps(provenance,ensure_ascii=False,indent=2)+'\n')

def preview():
    from tools.beastgen.preview import Mesh,rows_to_array,fit_frame,render
    OUT.mkdir(parents=True,exist_ok=True)
    font=ImageFont.truetype(str(ROOT/'src/main/resources/assets/myvillage/font/panel_body.ttf'),21)
    cards=[]
    for m in catalogue()['mobs']:
        b=Built(m['id']);mesh=Mesh(b.model,rows_to_array(b.texture),rows_to_array(b.glow),scale=b.model.scale)
        quads=mesh.world(anim.posed_matrices(b.model));im=render(quads,fit_frame(quads,(-.8,.4,-1),420,380,ss=2),bg=(225,228,216))
        panel=Image.new('RGB',(420,420),(225,228,216));panel.paste(im,(0,0));ImageDraw.Draw(panel).text((16,387),m['name']+'（实际模型）',font=font,fill=(35,49,45));panel.save(OUT/(m['id']+'.png'));cards.append(panel)
        views=[]
        for label,camera in [('front',(0,.12,-1)),('side',(-1,.12,0)),('back',(0,.12,1))]:
            view=render(quads,fit_frame(quads,camera,320,340,ss=2),bg=(225,228,216));view.save(OUT/(m['id']+'-'+label+'.png'));views.append(view)
        # Beast wrapper owns exact runtime animation sampling for the action sheets.
        from tools.beastgen.preview import Beast,Frame,CAMS
        beast=Beast(m['id'])
        for move in m['moves']:
            cells=[]
            for tick in [move['turn_lock_tick'],move['active_ticks'][0],move['total_ticks']-8]:
                q=beast.quads(move['animation'],tick/20)
                cells.append(render(q,fit_frame(q,(-.8,.4,-1),280,310,ss=2),bg=(225,228,216)))
            sheet=Image.new('RGB',(840,310));[sheet.paste(im,(i*280,0)) for i,im in enumerate(cells)];sheet.save(OUT/(m['id']+'-'+move['animation']+'.png'))
    sheet=Image.new('RGB',(840,840));[sheet.paste(im,((i%2)*420,(i//2)*420)) for i,im in enumerate(cards)];sheet.save(OUT/'models.png');print(OUT/'models.png')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('action',choices=['prepare','preview','import']);p.add_argument('name',nargs='?');p.add_argument('path',nargs='?');a=p.parse_args()
    if a.action=='prepare':prepare()
    elif a.action=='preview':preview()
    else:import_material(a.name,a.path)
