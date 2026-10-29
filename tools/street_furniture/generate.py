#!/usr/bin/env python3
"""生成自定义符灯、晒布架资源；实际模型单位为 1/16 格。"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[2]
INPUT=ROOT/'blueprints/street_furniture/catalog.json'
RESOURCES=ROOT/'src/main/resources'
SIDES=('north','south','east','west','up','down')


def read(path):return json.loads(Path(path).read_text())
def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()


def validate(catalog):
    if catalog.get('schema')!=1 or catalog.get('units_per_block')!=16:raise ValueError('未知街景模型格式')
    ids=set()
    for family in catalog['families']:
        ident=family['id'];size=family['size']
        if ident in ids or ident not in {'rune_lamp','drying_rack'}:raise ValueError('未知或重复的设施')
        ids.add(ident)
        if len(size)!=3 or any(type(n) is not int or not 1<=n<=2 for n in size):raise ValueError('设施占地越界')
        if not 0<=family['light']<=15 or not 0<=family['light_part']<size[0]*size[1]*size[2]:raise ValueError('光照配置越界')
        for e in family['elements']:
            if e['texture'] not in catalog['textures'] or e['grain_axis'] not in ('x','y','z'):raise ValueError('未知材质或木纹朝向')
            if any(not 0<=e['from'][i]<e['to'][i]<=size[i]*16 for i in range(3)):raise ValueError('模型超出实际占用体积')
    if ids!={'rune_lamp','drying_rack'}:raise ValueError('两类设施必须齐全')


def face_texture(element,side):
    tex=element['texture'];axis=element['grain_axis'];rotation=0
    if tex=='wood':
        ends={'x':('east','west'),'y':('up','down'),'z':('north','south')}
        if side in ends[axis]:tex='wood_end'
        elif axis in ('x','z') and side not in ('up','down'):rotation=90
    return tex,rotation


def crop_element(element,low,high):
    """裁切只分开占用格；连续表面的 UV 不重复，也不增加内部接缝面。"""
    a,b=element['from'],element['to']
    lo=[max(a[i],low[i]) for i in range(3)];hi=[min(b[i],high[i]) for i in range(3)]
    if any(lo[i]>=hi[i] for i in range(3)):return None
    faces={}
    axes={'north':(2,0,1,False),'south':(2,0,1,True),'west':(0,2,1,False),'east':(0,2,1,True),'down':(1,0,2,False),'up':(1,0,2,True)}
    for side,(normal,u,v,positive) in axes.items():
        if (hi[normal] if positive else lo[normal])!=(b[normal] if positive else a[normal]):continue
        u0,u1=(lo[u]-a[u])/(b[u]-a[u])*16,(hi[u]-a[u])/(b[u]-a[u])*16
        v0,v1=(lo[v]-a[v])/(b[v]-a[v])*16,(hi[v]-a[v])/(b[v]-a[v])*16
        # Vertical Minecraft model UVs increase from top to bottom.
        if v==1:v0,v1=16-v1,16-v0
        if side in ('north','east'):u0,u1=16-u1,16-u0
        tex,rotation=face_texture(element,side)
        if rotation==90:u0,v0,u1,v1=v0,16-u1,v1,16-u0
        face={'uv':[round(n,6) for n in (u0,v0,u1,v1)],'texture':'#'+tex}
        if rotation:face['rotation']=rotation
        faces[side]=face
    return {'name':element['name'],'from':[lo[i]-low[i] for i in range(3)],'to':[hi[i]-low[i] for i in range(3)],'faces':faces}


def parts(family):
    w,h,d=family['size'];result=[]
    for y in range(h):
        for z in range(d):
            for x in range(w):
                low=[x*16,y*16,z*16];high=[n+16 for n in low];elements=[];outline=[];collision=[]
                for source in family['elements']:
                    e=crop_element(source,low,high)
                    if e is None:continue
                    elements.append(e);box=e['from']+e['to'];outline.append(box)
                    if source['collision']:collision.append(box)
                if not outline:raise ValueError('多格设施不能占用空格')
                result.append({'offset':[x,y,z],'elements':elements,'outline':outline,'collision':collision,
                               'light':family['light'] if len(result)==family['light_part'] else 0})
    return result


def noise(x,y,seed=1):return ((x*1973+y*9277+seed*26699)^(x*y*31))%17-8


def textures(catalog):
    cfg=catalog['textures'];images={}
    def render(name,fn):
        size=cfg[name].get('resolution',32);im=Image.new('RGB',(size,size))
        for y in range(size):
            for x in range(size):im.putpixel((x,y),tuple(max(0,min(255,int(v))) for v in fn(x,y)))
        images[name]=im;return im
    def bronze(x,y):
        n=noise(x,y,3)*.45;e=cfg['bronze']['edge_light'] if x in (1,2) else cfg['bronze']['edge_dark'] if x>=30 else 0
        return tuple(v+n+e for v in cfg['bronze']['base'])
    render('bronze',bronze)
    render('stone',lambda x,y:tuple(v+noise(x,y)*.7 for v in cfg['stone']['base']))
    def wood(x,y):
        return tuple(v+noise(x//2,y,6)*weight+(light if (x+y//13)%9==2 else dark if x%11==0 else 0)
                     for v,weight,light,dark in zip(cfg['wood']['base'],(.5,.4,.3),(8,6,3),(-10,-7,-5)))
    render('wood',wood)
    render('wood_end',lambda x,y:tuple(v+(-7 if max(abs(x-15),abs(y-15))%5==0 else 2)+noise(x,y)*.25 for v in cfg['wood_end']['base']))
    render('cloth',lambda x,y:tuple(v+cfg['cloth']['fold_shades'][x//4]+(2 if (x+y)%2 else -2)+(-10 if y>=30 else 0) for v in cfg['cloth']['base']))
    render('cloth_fold',lambda x,y:tuple(v+(5 if y<6 else -8 if y>25 else 0)+noise(x,y)*.2 for v in cfg['cloth_fold']['base']))
    panel=cfg['lamp_panel'];size=panel['resolution'];scale=size/32
    im=render('lamp_panel',lambda x,y:bronze(int(x/scale),int(y/scale)) if min(x,y,size-1-x,size-1-y)<panel['frame_width'] else
              tuple(v-max(abs(x/scale-15)-5,0)*shade for v,shade in zip(panel['center'],(1,1.4,1.7))))
    font_path=ROOT/panel['font'];font_path.resolve().relative_to(ROOT.resolve())
    left,top,right,bottom=panel['glyph_bounds'];chosen=None
    for font_size in range(size*2,1,-1):
        font=ImageFont.truetype(str(font_path),font_size);bounds=font.getbbox(panel['glyph'])
        if bounds[2]-bounds[0]<=right-left and bounds[3]-bounds[1]<=bottom-top:chosen=(font,bounds);break
    if chosen is None:raise ValueError('灯面字体无法装入字框')
    font,(x0,y0,x1,y1)=chosen;mask=Image.new('L',(size,size))
    ImageDraw.Draw(mask).text(((left+right-x1-x0)/2,(top+bottom-y1-y0)/2),panel['glyph'],font=font,fill=255)
    im.paste(tuple(panel['ink']),(0,0),mask)
    result={}
    for name,im in images.items():
        out=io.BytesIO();im.save(out,format='PNG');result[name]=out.getvalue()
    return result


def model(elements,family,texture_names):
    return {'parent':'minecraft:block/block','render_type':'minecraft:cutout','ambientocclusion':False,
            'textures':{'particle':'#'+family['particle'],**{n:'myvillage:block/street_furniture/'+n for n in texture_names}},'elements':elements}


def outputs(catalog):
    validate(catalog);files={};runtime={'schema':1,'source_sha256':hashlib.sha256(INPUT.read_bytes()).hexdigest(),'families':[]}
    def put(path,value):files[path]=encoded(value)
    for f in catalog['families']:
        ident=f['id'];split=parts(f);variants={}
        runtime['families'].append({k:f[k] for k in ('id','name','size','sound','strength')}|{'parts':[{k:p[k] for k in ('offset','outline','collision','light')} for p in split]})
        for index,p in enumerate(split):
            name=f'street_furniture/{ident}_p{index}';put(f'assets/myvillage/models/block/{name}.json',model(p['elements'],f,catalog['textures']))
            for facing,turn in [('north',0),('east',90),('south',180),('west',270)]:
                for water in ('false','true'):
                    value={'model':'myvillage:block/'+name}
                    if turn:value['y']=turn
                    variants[f'facing={facing},part={index},waterlogged={water}']=value
        put(f'assets/myvillage/blockstates/{ident}.json',{'variants':variants})
        # Inventory geometry is centered and uniformly scaled; placed geometry is untouched.
        scale=1/max(f['size']);elements=[]
        for e in f['elements']:
            part=crop_element(e,[0,0,0],[n*16 for n in f['size']])
            for key in ('from','to'):part[key]=[(n-f['size'][i]*8)*scale+8 for i,n in enumerate(part[key])]
            elements.append(part)
        item=model(elements,f,catalog['textures'])
        item['display']={'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.625]*3},
            'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.25]*3},
            'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[.5]*3},
            'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.375]*3},
            'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,0,0],'scale':[.4]*3},
            'firstperson_lefthand':{'rotation':[0,225,0],'translation':[0,0,0],'scale':[.4]*3}}
        put(f'assets/myvillage/models/item/{ident}.json',item)
        put(f'data/myvillage/loot_table/blocks/{ident}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'myvillage:'+ident}],
            'conditions':[{'condition':'minecraft:block_state_property','block':'myvillage:'+ident,'properties':{'part':'0'}},{'condition':'minecraft:survives_explosion'}]}]})
        put(f'data/myvillage/recipe/{ident}.json',{'type':'minecraft:crafting_shaped','category':'building',**f['recipe'],'result':{'id':'myvillage:'+ident,'count':1}})
    put('data/myvillage/street_furniture/catalog.json',runtime)
    for name,data in textures(catalog).items():files[f'assets/myvillage/textures/block/street_furniture/{name}.png']=data
    return files


def shared_outputs(catalog):
    files={};owned={'myvillage:'+f['id'] for f in catalog['families']}
    for language in ('zh_cn','en_us'):
        relative=f'assets/myvillage/lang/{language}.json';p=RESOURCES/relative;data=read(p) if p.exists() else {}
        for f in catalog['families']:data['block.myvillage.'+f['id']]=f['name']
        files[relative]=encoded(data)
    for tool in ('axe','pickaxe'):
        relative=f'data/minecraft/tags/block/mineable/{tool}.json';p=RESOURCES/relative;data=read(p) if p.exists() else {'replace':False,'values':[]}
        data['values']=[x for x in data['values'] if x not in owned]+['myvillage:'+f['id'] for f in catalog['families'] if f['mineable']==tool]
        files[relative]=encoded(data)
    return files


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    catalog=read(INPUT);files=outputs(catalog)|shared_outputs(catalog);different=[]
    for relative,data in files.items():
        target=RESOURCES/relative
        if not target.is_file() or target.read_bytes()!=data:
            different.append(relative)
            if not args.check:target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    if args.check and different:raise SystemExit('街景资源不是当前输入的生成结果：\n'+'\n'.join(different))
    print(json.dumps({'families':len(catalog['families']),'files':len(files),'changed':len(different),'check':args.check},ensure_ascii=False))


if __name__=='__main__':main()
