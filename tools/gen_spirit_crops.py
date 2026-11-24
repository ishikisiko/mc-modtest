#!/usr/bin/env python3
"""Deterministic three-stage spirit crops, permanent soil, loot and acquisition assets."""
from __future__ import annotations
import argparse
import json
import math
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path: sys.path.insert(0, str(ROOT))
from tools.gen_alchemy_assets import png, HERBS, NAMES
from tools.namespace_migration import tag_values
ASSET = Path('src/main/resources/assets/yuanxian')
DATA = Path('src/main/resources/data')
OTHERWORLD_ART = Path('art/spirit-crops/otherworld')
OTHERWORLD = json.loads((ROOT / OTHERWORLD_ART / 'catalogue.json').read_text(encoding='utf-8'))
# Minecraft 1.21.1 block/block.json item transforms; world geometry stays full size.
BLOCK_ITEM_DISPLAY = {'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]}, 'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]}, 'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]}, 'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]}, 'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]}, 'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]}}
PARTS = ('stem', 'leaf', 'leaf_light', 'petal', 'heart', 'fruit', 'cap', 'root')
PALETTES = {
 'spirit_leaf': ((49,86,45),(47,111,63),(113,169,89),(133,192,126),(180,211,130),(65,135,78),(80,138,94),(92,71,43)),
 'dew_blossom': ((45,90,62),(48,117,75),(111,166,100),(118,198,225),(241,226,137),(79,166,201),(160,221,230),(77,65,42)),
 'sunroot': ((53,96,45),(57,126,62),(127,175,74),(225,153,60),(252,191,87),(221,101,37),(179,80,29),(217,112,38)),
 'blood_berry': ((99,70,42),(41,99,62),(103,152,77),(155,102,104),(246,170,159),(183,48,72),(115,39,61),(96,68,41)),
 'cloud_fungus': ((205,183,133),(76,113,96),(122,163,126),(201,173,201),(234,213,176),(138,107,146),(164,130,170),(115,85,65)),
 'calm_lotus': ((73,125,91),(45,116,91),(105,166,132),(172,221,196),(236,215,122),(137,190,155),(94,160,128),(85,92,50)),
}
def encoded(value): return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def texture(herb):
    colors=PALETTES[herb]; rows=[]
    for y in range(32):
        row=[]
        for x in range(32):
            tile=(y//8)*4+x//8; base=colors[tile%8]; u,v=x%8,y%8
            noise=((x*17+y*31+x*y*3)%11)-5
            shade=(3-v)*2+noise
            if tile%8 in (1,2):
                shade += 17 if u==3 or (v%3==0 and abs(u-3)<=v//2+1) else 0
                shade -= 13 if u in (0,7) else 0
            elif tile%8 in (3,6):
                shade += 15 if (u-2)**2+(v-2)**2<5 else 0
                shade -= 12 if v==7 else 0
            elif tile%8==5: shade+=27 if u in (2,3) and v in (1,2) else 0
            row.append(tuple(max(0,min(255,c+shade)) for c in base)+(255,))
        rows.append(row)
    return png(rows)
def soil_texture(kind):
    rows=[]
    for y in range(32):
        row=[]
        for x in range(32):
            n=(x*13+y*23+x*y*7)%19-9
            base=(78,62,42) if kind=='top' else (73,57,39)
            if kind=='top':
                n+=-22 if x%8 in (0,1) else 8 if x%8 in (3,4) else 0
                if (x*7+y*13)%101==0:base=(109,155,117);n=0
            elif kind=='side':
                n+=4 if y<5 else -y//3
                if y in (7,8) and (x+y)%9<4:base=(107,112,72)
            row.append(tuple(max(0,min(255,c+n)) for c in base)+(255,))
        rows.append(row)
    return png(rows)
class Plant:
    def __init__(self):self.elements=[]
    def box(self,a,b,part='stem',rotation=None):
        i=PARTS.index(part);u=(i%4)*4;v=(i//4)*4
        faces={f:{'texture':'#plant','uv':[u,v,u+4,v+4]} for f in ('north','south','east','west','up','down')}
        element={'from':[round(n,4) for n in a],'to':[round(n,4) for n in b],'faces':faces}
        if rotation:element['rotation']=rotation
        self.elements.append(element)
    def stem(self,x,y,z,h,w=.7):self.box((x-w/2,y,z-w/2),(x+w/2,y+h,z+w/2))
    def leaf(self,x,y,z,length,width,axis='x',sign=1,part='leaf',tilt=22.5):
        for i,wide in enumerate((.6,1,.45)):
            start=sign*length*i/3;end=sign*length*(i+1)/3
            if axis=='x':a=(x+min(start,end),y,z-width*wide/2);b=(x+max(start,end),y+.3,z+width*wide/2)
            else:a=(x-width*wide/2,y,z+min(start,end));b=(x+width*wide/2,y+.3,z+max(start,end))
            rotation={'origin':[x,y,z],'axis':'z' if axis=='x' else 'x','angle':sign*tilt*(1 if axis=='x' else -1),'rescale':False}
            self.box(a,b,part,rotation)
    def blossom(self,x,y,z,size,part='petal'):
        self.box((x-.65,y,z-.65),(x+.65,y+.8,z+.65),'heart')
        for axis in ('x','z'):
            for sign in (-1,1):self.leaf(x,y-.1,z,size,size*.8,axis,sign,part,22.5)
    def cap(self,x,y,z,r):
        self.box((x-r*.85,y-.5,z-r*.7),(x+r*.85,y,z+r*.7),'stem')
        self.box((x-r,y,z-r*.6),(x+r,y+.65,z+r*.6),'cap')
        self.box((x-r*.65,y,z-r),(x+r*.65,y+.65,z+r),'cap')
        self.box((x-r*.7,y+.65,z-r*.7),(x+r*.7,y+1.2,z+r*.7),'cap')
        self.box((x-r*.4,y+1.2,z-r*.4),(x+r*.4,y+1.55,z+r*.4),'petal')
def crop_model(herb,age):
    p=Plant();scale=(.42,.7,1)[age]
    if herb=='spirit_leaf':
        h=(3,6.5,10)[age];p.stem(8,0,8,h)
        for row in range(age+2):
            y=1.0+row*h/(age+2)
            for sign in (-1,1):p.leaf(8,y,8,(3.7-row*.3)*scale,2.5*scale,'x' if row%2==0 else 'z',sign,'leaf_light' if row==age+1 else 'leaf')
    elif herb=='dew_blossom':
        h=(2.5,6,10)[age];p.stem(8,0,8,h)
        for sign in (-1,1):p.leaf(8,h*.35,8,3.5*scale,2.4*scale,'x',sign)
        if age==1:p.box((7,6,7),(9,8,9),'petal')
        elif age==2:
            p.blossom(8,10,8,3.3)
            p.box((11.8,8.8,7.6),(12.5,9.6,8.3),'fruit')
    elif herb=='sunroot':
        for x,z,r in ((8,8,1.5),(5.3,9.4,1),(10.5,9.3,.9))[:age+1]:
            p.box((x-r*.5,0,z-r*.5),(x+r*.5,1,z+r*.5),'root')
            p.box((x-r,1,z-r),(x+r,2.5*scale+1,z+r),'root')
        for axis in ('x','z'):
            for sign in (-1,1):p.leaf(8,1.6,8,5*scale,1.9*scale,axis,sign,'leaf_light',45)
        p.stem(8,0,8,5*scale)
    elif herb=='blood_berry':
        h=(2.5,5.5,8)[age];p.stem(8,0,8,h,1)
        for j,(x,z) in enumerate(((5,7),(10.5,6.5),(8.5,10.5))[:age+1]):
            y=h*(.5+.13*j);p.box((min(8,x),y-.2,min(8,z)),(max(8,x)+.4,y+.4,max(8,z)+.4))
            p.leaf(x,y,z,3*scale,2.2*scale,'x' if j%2 else 'z',-1 if j==0 else 1)
            if age>0:
                for dx,dy,dz in ((0,0,0),(.9,-1,1.3),(-1,-.8,1)):
                    size=1.5 if age==2 else .7
                    p.box((x+dx-size/2,y+dy,z+dz-size/2),(x+dx+size/2,y+dy+size,z+dz+size/2),'fruit' if age==2 else 'leaf_light')
    elif herb=='cloud_fungus':
        for x,z,h,r in ((8,8,2+age*2,1.5+age*.7),(4.8,10,2+age,1.7),(11,6,5,2))[:age+1]:
            p.stem(x,0,z,h,1.1);p.cap(x,h,z,r)
    elif herb=='calm_lotus':
        for axis in ('x','z'):
            for sign in (-1,1):p.leaf(8,.25,8,5.3*scale,4.3*scale,axis,sign,'leaf',0)
        if age:
            p.stem(8,.4,8,2.5+age)
            if age==1:p.box((7.2,3.5,7.2),(8.8,6,8.8),'petal')
            else:
                p.blossom(8,4.8,8,3.5)
                for axis in ('x','z'):
                    for sign in (-1,1):p.leaf(8,5.3,8,2.1,1.5,axis,sign,'petal',45)
    return {'ambientocclusion':False,'render_type':'minecraft:cutout','textures':{'plant':f'yuanxian:block/{herb}_plant','particle':f'yuanxian:block/{herb}_plant'},'elements':p.elements}


def emit_crop(put, herb, models):
    block = herb + '_crop'
    put(ASSET/f'blockstates/{block}.json', {
        'variants': {f'age={age}': {'model': f'yuanxian:block/{block}_{age}'} for age in range(3)}
    })
    for age, model in enumerate(models):
        put(ASSET/f'models/block/{block}_{age}.json', model)
    put(DATA/f'yuanxian/loot_table/blocks/{block}.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'entries': [{
            'type': 'minecraft:item', 'name': f'yuanxian:{herb}',
            'functions': [
                {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 2, 'max': 4},
                 'conditions': [{'condition': 'minecraft:block_state_property',
                                 'block': f'yuanxian:{block}', 'properties': {'age': '2'}}]},
                {'function': 'minecraft:explosion_decay'},
            ],
        }]}],
    })


def emit_soil(put, soil):
    put(ASSET/f'blockstates/{soil}.json', {'variants': {'': {'model': f'yuanxian:block/{soil}'}}})
    put(ASSET/f'models/block/{soil}.json', {
        'textures': {key: f'yuanxian:block/{soil}_{face}' for key, face in
                     (('top', 'top'), ('bottom', 'bottom'), ('side', 'side'), ('particle', 'top'))},
        'elements': [{'from': [0,0,0], 'to': [16,16,16], 'faces': {
            face: {'texture': '#'+('top' if face == 'up' else 'bottom' if face == 'down' else 'side'),
                   'cullface': face, 'uv': [0,0,16,16]}
            for face in ('north','south','east','west','up','down')
        }}],
    })
    put(ASSET/f'models/item/{soil}.json', {
        'parent': f'yuanxian:block/{soil}', 'gui_light': 'side', 'display': BLOCK_ITEM_DISPLAY,
    })
    put(DATA/f'yuanxian/loot_table/blocks/{soil}.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'yuanxian:{soil}'}],
                                            'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
    })


def emit_otherworld(out, put, root, spec):
    herb, soil = spec['herb'], spec['soil']
    source = root / OTHERWORLD_ART / herb
    for relative, name in {
        f'block/{herb}_plant': 'plant.png',
        f'item/{herb}': 'item.png',
        f'block/{soil}_top': 'soil_top.png',
        f'block/{soil}_side': 'soil_side.png',
        f'block/{soil}_bottom': 'soil_side.png',
    }.items():
        out[root/ASSET/f'textures/{relative}.png'] = (source/name).read_bytes()
    models = []
    stages = json.loads((source/'stages.json').read_text())
    if len(stages) != 3:
        raise ValueError(f'{herb} requires three authored stages')
    for stage in stages:
        plant = Plant()
        for box in stage:
            plant.box(box['from'], box['to'], box['part'], box.get('rotation'))
        texture = f'yuanxian:block/{herb}_plant'
        models.append({'ambientocclusion': False, 'render_type': 'minecraft:cutout',
                       'textures': {'plant': texture, 'particle': texture}, 'elements': plant.elements})
    emit_crop(put, herb, models)
    emit_soil(put, soil)
    put(ASSET/f'models/item/{herb}.json', {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': f'yuanxian:item/{herb}'},
    })
    put(DATA/f'yuanxian/recipe/{soil}.json', {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'item': item} for item in spec['soil_ingredients']],
        'result': {'id': f'yuanxian:{soil}', 'count': 1},
    })
    patch = f'{herb}_patch'
    put(DATA/f'yuanxian/worldgen/configured_feature/{patch}.json', {
        'type': 'minecraft:random_patch', 'config': {
            **spec['patch'], 'feature': {
                'feature': {'type': 'yuanxian:otherworld_herb', 'config': {
                    'crop': {'Name': f'yuanxian:{herb}_crop', 'Properties': {'age': '2'}},
                    'ground': spec['ground'],
                }},
                'placement': [],
            },
        },
    })
    put(DATA/f'yuanxian/worldgen/placed_feature/{patch}.json', {
        'feature': f'yuanxian:{patch}', 'placement': spec['placement'],
    })
    put(DATA/f'yuanxian/neoforge/biome_modifier/{patch}.json', {
        'type': 'neoforge:add_features', 'biomes': spec['biomes'],
        'features': f'yuanxian:{patch}', 'step': 'vegetal_decoration',
    })


def outputs(root=ROOT):
    root=Path(root);out={}
    def put(path,obj):out[root/path]=encoded(obj)
    for herb in HERBS:
        out[root/ASSET/f'textures/block/{herb}_plant.png']=texture(herb)
        emit_crop(put, herb, [crop_model(herb, age) for age in range(3)])
    for face in ('top','side','bottom'):out[root/ASSET/f'textures/block/spirit_soil_{face}.png']=soil_texture(face)
    emit_soil(put, 'spirit_soil')
    put(DATA/'yuanxian/recipe/spirit_soil.json',{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':'minecraft:dirt'},{'tag':'c:spirit_stones/low_grade'}],'result':{'id':'yuanxian:spirit_soil','count':1}})
    for spec in OTHERWORLD:
        emit_otherworld(out, put, root, spec)
    return out
def merged(root=ROOT,rename_namespace_from=None):
    root=Path(root);out={}
    for language,index in (('en_us',0),('zh_cn',1)):
        path=root/ASSET/f'lang/{language}.json';lang=json.loads(path.read_text())
        lang['block.yuanxian.spirit_soil']=('Spirit Soil','灵土')[index]
        for herb in HERBS:lang[f'block.yuanxian.{herb}_crop']=NAMES[herb][index]
        if language == 'zh_cn':
            for spec in OTHERWORLD:
                lang[f"block.yuanxian.{spec['soil']}"] = spec['soil_name']
                lang[f"item.yuanxian.{spec['herb']}"] = spec['name']
                lang[f"block.yuanxian.{spec['herb']}_crop"] = spec['name']
            lang['tooltip.yuanxian.otherworld_herb.soil'] = '种在%s上，成熟后收获同种药材'
        out[path]=encoded(lang)
    tags = {
        'minecraft/tags/block/mineable/shovel': ['yuanxian:spirit_soil', *(f"yuanxian:{s['soil']}" for s in OTHERWORLD)],
        'minecraft/tags/block/crops': [f'yuanxian:{h}_crop' for h in (*HERBS, *(s['herb'] for s in OTHERWORLD))],
        'yuanxian/tags/item/storage_bag_materials/extra': [f"yuanxian:{s['herb']}" for s in OTHERWORLD],
    }
    for tag,values in tags.items():
        path=root/DATA/f'{tag}.json'
        data=json.loads(path.read_text()) if path.exists() else {'replace':False,'values':[]}
        data['values']=list(dict.fromkeys(tag_values(data['values'],rename_namespace_from)+values));out[path]=encoded(data)
    return out
def preview():
    import numpy as np
    from PIL import Image,ImageDraw,ImageFont
    from tools.combat_preview import item_model as pim
    from tools.beastgen.preview import Frame,render
    out=ROOT/'out/preview/spirit_crops';out.mkdir(parents=True,exist_ok=True)
    assets=pim.Assets([ROOT/'src/main/resources'],None)
    soil=pim.resolve_model(assets,assets.model_json('yuanxian:block/spirit_soil'),'yuanxian:block/spirit_soil')
    soilquads=[((q.pos-np.array([8,16,8]))/16,q.uv,q.tex,None) for i,e in enumerate(soil.elements) for q in pim.element_quads(assets,soil,e,i)]
    sheet=Image.new('RGB',(960,1200),(24,32,31));draw=ImageDraw.Draw(sheet)
    font=ImageFont.truetype('/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc',18)
    for row,h in enumerate(HERBS):
        for age in range(3):
            ref=f'yuanxian:block/{h}_crop_{age}';model=pim.resolve_model(assets,assets.model_json(ref),ref)
            if model.errors:raise ValueError(model.errors)
            quads=soilquads+[((q.pos-np.array([8,0,8]))/16,q.uv,q.tex,None) for i,e in enumerate(model.elements) for q in pim.element_quads(assets,model,e,i)]
            img=render(quads,Frame((-1,.75,-1.3),300,172,66,(0,-.1,0),ss=2),bg=(24,32,31),ground=False,body_rot=180)
            img.save(out/f'{h}_{age}.png');sheet.paste(img.convert('RGB'),(age*320+10,row*200))
            draw.text((age*320+160,row*200+177),NAMES[h][1]+' · '+('幼苗','生长','成熟')[age],font=font,fill=(225,220,186),anchor='mt')
    sheet.save(out/'stages.png')
    (out/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>灵植 · 三阶段</title><style>body{background:#18201f;color:#ddd;font:18px sans-serif;max-width:1000px;margin:30px auto}img{width:100%}</style><h1>六种灵植 · 幼苗 / 生长 / 成熟</h1><p>实际游戏模型与贴图离线渲染。灵土永久肥沃，无需浇水；药材兼作播种材料，成熟收获 2–4 份。实机观感待玩家确认。</p><img src="stages.png">')
    print(out/'index.html')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');parser.add_argument('--preview',action='store_true');parser.add_argument('--rename-namespace-from');args=parser.parse_args()
    generated={**outputs(),**merged(rename_namespace_from=args.rename_namespace_from)};stale=[]
    for path,data in generated.items():
        if args.check:
            if not path.exists() or path.read_bytes()!=data:stale.append(str(path.relative_to(ROOT)))
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    if stale:print('\n'.join(stale));return 1
    print(f'Spirit crops: {len(generated)} generated files '+('match' if args.check else 'written'))
    if args.preview:preview()
    return 0
if __name__=='__main__':raise SystemExit(main())
