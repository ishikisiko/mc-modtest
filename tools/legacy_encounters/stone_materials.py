"""实际游戏使用的像素 atlas：材质底色、可读刻纹和固定 UV 回贴。"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

from .stone_models import ROOT, guardian_surface, ruler_surface

FONT = ROOT / 'src/main/resources/assets/myvillage/font/brush.ttf'


def art_root(name):
    return ROOT / 'art' / ('entities' if name == 'stele_guardian' else 'items') / name


def _mask(panel, width, height):
    image = Image.new('L', (width, height));d = ImageDraw.Draw(image)
    wide = max(1, round(width / 70));kind = panel.kind
    def line(points, thick=1, value=180):
        d.line([(round(u * (width-1)), round(v * (height-1))) for u, v in points], fill=value, width=max(1, round(wide*thick)), joint='curve')
    def box(inset=.09, value=170):
        line([(inset,.065),(1-inset,.065),(1-inset,.935),(inset,.935),(inset,.065)], 1.2, value)
    def cloud(x, y, sx=.22, sy=.07, value=150):
        points=[]
        for i in range(22):
            a=i/21*math.pi*2.2;r=1-i/31
            points.append((x+math.cos(a)*sx*r,y+math.sin(a)*sy*r))
        line(points,1,value);line([(x-sx,y+sy),(x,y+sy*.65),(x+sx,y+sy*.9)],.8,value)
    def glyph(text, x, y, w, h, value=255):
        font=ImageFont.truetype(str(FONT), max(24, round(height*h*1.4)))
        bounds=font.getbbox(text);g=Image.new('L',(max(1,bounds[2]-bounds[0]),max(1,bounds[3]-bounds[1])))
        ImageDraw.Draw(g).text((-bounds[0],-bounds[1]),text,font=font,fill=value)
        g=g.resize((max(1,round(width*w)),max(1,round(height*h))),Image.Resampling.NEAREST)
        image.paste(g,(round(width*x),round(height*y)),g)
    if kind=='stone_torso_front':
        box(.08);box(.12,100)
        glyph('镇',.33,.20,.34,.25);glyph('岳',.35,.52,.30,.24)
        for u in (.22,.78):
            for v in (.27,.45,.63,.80):cloud(u,v,.075,.035,155)
        for u,sign in [(.15,1),(.85,-1)]:
            for v,sy in [(.11,1),(.89,-1)]:line([(u,v+.07*sy),(u,v),(u+.14*sign,v),(u+.14*sign,v+.035*sy),(u+.06*sign,v+.035*sy)],1.2,190)
    elif kind=='stone_head_front':
        glyph('山',.41,.06,.18,.15,170)
        line([(.13,.28),(.31,.25),(.42,.31)],1.4,220);line([(.58,.31),(.69,.25),(.87,.28)],1.4,220)
        line([(.20,.42),(.39,.45)],1.5,255);line([(.61,.45),(.80,.42)],1.5,255)
        line([(.46,.48),(.46,.65),(.50,.69),(.54,.65),(.54,.48)],1.0,180)
        cloud(.25,.66,.11,.08,170);cloud(.75,.66,.11,.08,170)
        line([(.34,.79),(.46,.76),(.55,.76),(.66,.79)],1,180)
    elif kind.startswith('stone_'):
        if 'cap' not in kind and panel.width > 3:
            box(.12,160)
            count=3 if panel.height>16 else 2
            for i in range(count):cloud(.5,.22+i*.57/max(1,count-1),.22,.075,170)
            for i in range(4):line([(.20,.17+i*.20),(.40,.10+i*.20),(.52,.15+i*.20)],.8,95)
        else:
            cloud(.50,.50,.32,.20,100)
    elif kind=='jade_front':
        box(.07,255);box(.11,180)
        glyph('镇',.27,.33,.45,.14);glyph('岳',.29,.52,.41,.13)
        for i in range(24):
            v=.14+i*.029
            line([(.88-(.17 if i%5==0 else .075),v),(.88,v)],1 if i%5==0 else .65,255)
        for i in range(10):cloud(.45,.17+i*.068,.24,.018,85)
        line([(.25,.25),(.44,.17),(.52,.21),(.62,.18),(.75,.25)],1.2,255)
        cloud(.5,.82,.22,.025,255)
        # The lower quarter is the grip; texture follows the one-piece loft instead of adding rings.
        for i in range(8):line([(.20,.84+i*.017),(.80,.88+i*.017)],.7,140)
    else:
        if panel.width>1:
            line([(.15,.04),(.15,.96)],.8,235);line([(.85,.04),(.85,.96)],.8,235)
            for i in range(10):cloud(.5,.10+i*.085,.22,.027,135)
    return image


def _face(panel, size, seed):
    w,h=size;yy,xx=np.mgrid[:h,:w];u=xx/max(1,w-1);v=yy/max(1,h-1)
    value=(xx.astype(np.uint32)*np.uint32(374761393)+yy.astype(np.uint32)*np.uint32(668265263)+np.uint32(seed*1741))
    value=(value^(value>>13))*np.uint32(1274126177);grain=((value^(value>>16))&65535)/65535
    broad=(np.sin(u*9+seed*.7)+np.sin(v*12+seed*.13)+np.sin((u+v)*7+seed*.31))/3
    fleck=np.maximum(0,(grain-.82)/.18)
    edge=np.minimum.reduce([u,1-u,v,1-v]);wear=np.maximum(0,1-edge/.07)
    stone=panel.kind.startswith('stone')
    if stone:
        colors=np.array([111.,119.,114.])[None,None,:]+(broad*12+(grain-.5)*12+fleck*6)[:,:,None]
        colors+=wear[:,:,None]*np.array([10.,10.,9.])
        if 'leg' in panel.kind:
            moss=np.clip((v-.68)*2.2,0,.5)*(np.sin(u*14+seed)*.2+.6)
            colors=colors*(1-moss[:,:,None])+np.array([69.,85.,51.])*moss[:,:,None]
    else:
        veins=np.maximum(0,1-np.abs(np.sin(u*8+v*11+broad*1.4))/.10)
        colors=np.array([31.,48.,51.])[None,None,:]+(broad*7+(grain-.5)*5+veins*5)[:,:,None]
        colors+=wear[:,:,None]*np.array([10.,13.,12.])
    mask=_mask(panel,w,h);a=np.asarray(mask)/255
    # Native-pixel carving shadows/highlights remain readable under vanilla diffuse lighting.
    dilated=np.asarray(mask.filter(ImageFilter.MaxFilter(3)))/255
    shadow=np.maximum(0,dilated-a)
    light=np.maximum(0,a-np.roll(a,1,axis=0))
    colors-=shadow[:,:,None]*(25 if stone else 15)
    carved=(a>.20)&(a<.84);colors-=carved[:,:,None]*(18 if stone else -6)
    colors+=light[:,:,None]*np.array([18.,17.,13.])
    bright=np.clip((a-.84)/.16,0,1)
    tint=np.array([170.,151.,98.] if stone else [191.,151.,74.])
    if 'head_front' in panel.kind:
        eyes=(v>.38)&(v<.49)
        bright*=eyes
        tint=np.array([218.,179.,95.])
    colors=colors*(1-bright[:,:,None]*.90)+tint*bright[:,:,None]*.90
    # Occasional branching weather cracks; the broad color field is not filled with uniform noise.
    im=Image.fromarray(np.uint8(np.clip(colors,0,255))).convert('RGBA')
    if stone and panel.width>4 and 'cap' not in panel.kind:
        d=ImageDraw.Draw(im);sx=.72+.08*math.sin(seed)
        pts=[(round(w*x),round(h*y)) for x,y in [(sx,0),(sx-.04,.13),(sx+.02,.19),(sx-.05,.29)]]
        d.line(pts,fill=(69,78,74,255),width=1)
    return im,mask


def raw_atlas(surface):
    image=Image.new('RGBA',surface.texture_size);mask=Image.new('L',surface.texture_size);features=Image.new('L',surface.texture_size)
    for i,p in enumerate(surface.panels.values()):
        x,y,w,h=p.rect;face,detail=_face(p,(w,h),i+17)
        # Two texels of repeated edge color keep atlas filtering away from transparent neighbors.
        image.paste(face,(x,y));image.paste(face.crop((0,0,1,h)).resize((2,h),Image.Resampling.NEAREST),(x-2,y))
        image.paste(face.crop((w-1,0,w,h)).resize((2,h),Image.Resampling.NEAREST),(x+w,y))
        image.paste(face.crop((0,0,w,1)).resize((w,2),Image.Resampling.NEAREST),(x,y-2))
        image.paste(face.crop((0,h-1,w,h)).resize((w,2),Image.Resampling.NEAREST),(x,y+h))
        ImageDraw.Draw(mask).rectangle((x-2,y-2,x+w+1,y+h+1),fill=255)
        features.paste(detail.filter(ImageFilter.MaxFilter(5)),(x,y))
    image.putalpha(mask)
    return image,mask,features


def atlas(surface):
    image,mask,features=raw_atlas(surface)
    path=art_root(surface.name)/'material_tones.png'
    if path.exists():
        tones=Image.open(path).convert('RGBA')
        if tones.size!=surface.texture_size:raise ValueError('surface UV atlas size drift: '+surface.name)
        combined=Image.blend(image,tones,.32)
        image=Image.composite(image,combined,features.point(lambda a:255 if a>25 else 0))
    image=image.convert('RGB').quantize(colors=64,dither=Image.Dither.NONE).convert('RGBA');image.putalpha(mask)
    glow=Image.new('RGBA',surface.texture_size)
    if surface.name=='stele_guardian':
        for p in surface.panels.values():
            if p.kind!='stone_head_front':continue
            x,y,w,h=p.rect;d=ImageDraw.Draw(glow)
            for a,b in [(.20,.39),(.61,.80)]:d.line((x+round(w*a),y+round(h*.43),x+round(w*b),y+round(h*.45)),fill=(60,44,20,255),width=max(1,round(w/50)))
    return [[image.getpixel((x,y)) for x in range(image.width)] for y in range(image.height)], [[glow.getpixel((x,y)) for x in range(glow.width)] for y in range(glow.height)]


def prepare(surface):
    image,mask,_=raw_atlas(surface);root=art_root(surface.name);root.mkdir(parents=True,exist_ok=True)
    work=ROOT/'work/entity-art/stone_models'/surface.name;work.mkdir(parents=True,exist_ok=True)
    faces=[{'id':p.name,'kind':p.kind,'rect':list(p.rect)} for p in surface.panels.values()]
    truth={'size':list(surface.texture_size),'faces':faces}
    previous=json.loads((root/'uv_truth.json').read_text()) if (root/'uv_truth.json').exists() else None
    if previous!=truth or not (root/'material_tones.png').exists():
        image.save(root/'material_tones.png')
        (root/'texture_provenance.json').write_text(json.dumps({'generator':'tools/legacy_encounters/stone_materials.py','geometry':'tools/legacy_encounters/stone_models.py','usage':'原生像素纹样与固定 UV 材质；等待内置 imagegen 材质底色候选','human_verdict':'pending'},ensure_ascii=False,indent=2)+'\n')
    mask.save(root/'uv_islands_mask.png')
    (root/'uv_truth.json').write_text(json.dumps(truth,indent=2)+'\n')
    factor=max(1,1024//max(image.size));size=(image.width*factor,image.height*factor)
    work_image=Image.new('RGBA',(1024,1024),(39,42,43,255));work_image.paste(image.resize(size,Image.Resampling.NEAREST),(0,0),mask.resize(size,Image.Resampling.NEAREST));work_image.save(work/'work_base.png')
    work_mask=Image.new('L',(1024,1024));work_mask.paste(mask.resize(size,Image.Resampling.NEAREST),(0,0));work_mask.save(work/'composite_mask.png')
    overlay=image.copy();d=ImageDraw.Draw(overlay)
    for p in surface.panels.values():
        x,y,w,h=p.rect;d.rectangle((x,y,x+w-1,y+h-1),outline=(230,112,54,255),width=1)
        if w>55 and h>20:d.text((x+2,y+2),p.name[:23],fill=(255,236,180,255))
    overlay.save(work/'face_overlay.png')
    if surface.name=='stele_guardian':
        export_blockbench(surface,root)
    print(work)


def import_tones(surface,path):
    root=art_root(surface.name);mask=Image.open(root/'uv_islands_mask.png').convert('L');base=Image.open(root/'material_tones.png').convert('RGBA')
    image=Image.open(path).convert('RGBA')
    if image.width!=image.height:raise ValueError('UV candidate must preserve the square work canvas')
    factor=max(1,1024//max(base.size));image=image.resize((1024,1024),Image.Resampling.NEAREST)
    image=image.crop((0,0,base.width*factor,base.height*factor)).resize(base.size,Image.Resampling.NEAREST)
    image=Image.composite(image,base,mask);image.putalpha(mask);image.save(root/'material_tones.png')
    (root/'texture_provenance.json').write_text(json.dumps({'generator':'built-in image_gen','geometry':'tools/legacy_encounters/stone_models.py','candidate_sha256':hashlib.sha256(Path(path).read_bytes()).hexdigest(),'mask_sha256':hashlib.sha256((root/'uv_islands_mask.png').read_bytes()).hexdigest(),'usage':'32% 材质底色混合；原生碑文、刻纹、眼部和岛外透明区由确定性规则保持','normalization':'nearest neighbour and binary UV mask','human_verdict':'pending'},ensure_ascii=False,indent=2)+'\n')


def export_blockbench(surface,root):
    import base64,uuid
    from .stone_models import guardian_skeleton
    from tools.beastgen import cuboid
    model=guardian_skeleton();matrices=model.rest_matrices();root_inv=cuboid.mat_inverse_rigid(matrices['root'])
    elements=[];groups={};outliner=[]
    for bone in model.bones:
        p=cuboid.mat_apply(cuboid.mat_mul(root_inv,matrices[bone.name]),(0,0,0));pivot=[p[0],-p[1],-p[2]]
        uid=str(uuid.uuid5(uuid.NAMESPACE_URL,'stele/'+bone.name));group={'name':bone.name,'origin':pivot,'rotation':[0,0,0],'uuid':uid,'children':[]}
        groups[bone.name]=group;(groups[bone.parent]['children'] if bone.parent else outliner).append(group)
        vertices={};faces={};index=0
        for f in surface.faces:
            if f.bone!=bone.name:continue
            keys=[];uv={}
            for p,t in zip(f.points,surface.mapped(f)):
                key='v'+str(index);index+=1;keys.append(key);vertices[key]=[round(p[0],5),round(-p[1],5),round(-p[2],5)];uv[key]=[t[0]*surface.texture_size[0],t[1]*surface.texture_size[1]]
            faces['f'+str(len(faces))]={'vertices':keys,'uv':uv,'texture':0}
        if vertices:
            mid=str(uuid.uuid5(uuid.NAMESPACE_URL,'stele/mesh/'+bone.name));group['children'].append(mid)
            elements.append({'name':bone.name,'type':'mesh','origin':pivot,'rotation':[0,0,0],'vertices':vertices,'faces':faces,'uuid':mid})
    doc={'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':surface.name,'resolution':dict(zip(('width','height'),surface.texture_size)),'elements':elements,'outliner':outliner,'textures':[{'name':surface.name+'.png','id':'0','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'stele/texture')),'source':'data:image/png;base64,'+base64.b64encode((root/'material_tones.png').read_bytes()).decode()}]}
    (root/(surface.name+'.bbmodel')).write_text(json.dumps(doc,separators=(',',':'))+'\n')


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('action',choices=['prepare','import']);p.add_argument('name',choices=['stele_guardian','zhenyue_ruler']);p.add_argument('path',nargs='?');a=p.parse_args()
    s=guardian_surface() if a.name=='stele_guardian' else ruler_surface()
    prepare(s) if a.action=='prepare' else import_tones(s,a.path)
