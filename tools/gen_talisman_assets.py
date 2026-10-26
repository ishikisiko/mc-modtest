#!/usr/bin/env python3
"""制符专属模型/贴图/配方/规则/中文键；--preview 写 ignored reports。"""
from __future__ import annotations
import argparse
import io
import json
from pathlib import Path
import sys
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path: sys.path.insert(0, str(ROOT))
SOURCE = ROOT / 'tools/talisman'
ASSETS = Path('src/main/resources/assets/myvillage')
DATA = Path('src/main/resources/data/myvillage')
FACES = ('up','down','north','south','east','west')

def encoded(value): return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()
class Raster:
    """仅用于固定 32 像素图标的轻量画布，生成/check 不依赖 Pillow。"""
    def __init__(self): self.rows=[[(0,0,0,0) for _ in range(32)] for _ in range(32)]
    def __setitem__(self,xy,color): self.point(xy,color)
    def point(self,xy,fill):
        x,y=map(int,xy)
        if 0<=x<32 and 0<=y<32: self.rows[y][x]=tuple(fill)+(255,) if len(fill)==3 else tuple(fill)
    def line(self,xy,fill,width=1):
        for k in range(0,len(xy)-2,2):
            x,y,X,Y=xy[k:k+4];steps=max(abs(X-x),abs(Y-y),1)
            for i in range(steps+1):
                px,py=round(x+(X-x)*i/steps),round(y+(Y-y)*i/steps)
                for dx in range(width):
                    for dy in range(width): self.point((px+dx,py+dy),fill)
    def rectangle(self,box,fill=None,outline=None,width=1):
        x,y,X,Y=box
        if fill:
            for v in range(y,Y+1):
                for u in range(x,X+1): self.point((u,v),fill)
        if outline:
            for k in range(width): self.line((x+k,y+k,X-k,y+k,X-k,Y-k,x+k,Y-k,x+k,y+k),outline)
    def polygon(self,points,fill=None,outline=None):
        if fill:
            for y in range(32):
                for x in range(32):
                    signs=[]
                    for i,(a,b) in enumerate(points):
                        c,d=points[(i+1)%len(points)];signs.append((c-a)*(y-b)-(d-b)*(x-a))
                    if all(v>=0 for v in signs) or all(v<=0 for v in signs):self.point((x,y),fill)
        if outline:
            p=points+[points[0]];self.line(tuple(n for xy in p for n in xy),outline)
    def ellipse(self,box,fill=None,outline=None):
        x,y,X,Y=box;cx,cy=(x+X)/2,(y+Y)/2;rx,ry=(X-x)/2,(Y-y)/2
        for v in range(y,Y+1):
            for u in range(x,X+1):
                distance=((u-cx)/rx)**2+((v-cy)/ry)**2
                if distance<=1:
                    color=outline if outline and distance>.65 else fill
                    if color:self.point((u,v),color)

def png(image):
    def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    raw=b''.join(b'\0'+bytes(c for pixel in row for c in pixel) for row in image.rows)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,32,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')
def read(name): return json.loads((SOURCE/name).read_text())
def font(size):
    from PIL import ImageFont
    for path in ['/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc','/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc']:
        if Path(path).exists(): return ImageFont.truetype(path,size)
    return ImageFont.load_default()
def texture(kind):
    palettes={'wood':(65,48,36),'copper':(111,82,53),'paper_surface':(227,208,155),'inkwell':(46,47,41)}
    im=Raster(); p=im; base=palettes[kind]
    for y in range(32):
        for x in range(32):
            n=(x*11+y*7+x*y)%11-5
            if kind=='wood': n+=5 if x%7==0 else -4 if x%7==1 else 0
            p[x,y]=tuple(max(0,min(255,c+n)) for c in base)+(255,)
    d=im
    if kind=='copper':
        d.line((1,1,30,1),fill=(180,137,73)); d.line((1,1,1,30),fill=(143,111,66));d.rectangle((12,12,18,18),outline=(78,116,104))
    if kind=='paper_surface':
        d.rectangle((2,2,29,29),outline=(158,58,42));d.line((16,4,12,10,20,13,11,18,19,21,15,28),fill=(158,58,42),width=2)
    return im

def icon(kind):
    im=Raster();d=im
    if kind=='spiritual_ink':
        d.polygon([(8,7),(24,7),(27,23),(22,27),(8,27),(5,22)],fill=(106,76,51),outline=(169,128,71))
        d.ellipse((8,6,24,14),fill=(50,48,40),outline=(186,141,78));d.ellipse((11,8,21,12),fill=(152,44,35))
        d.line((25,5,20,21),fill=(96,136,126),width=2);return im
    d.polygon([(8,2),(24,3),(25,28),(21,30),(7,28)],fill=(237,212,148),outline=(161,119,61))
    d.line((10,4,22,5),fill=(255,237,188));d.line((9,26,22,27),fill=(198,154,88))
    if kind=='talisman_paper': return im
    red=(164,49,36); glow=(82,146,139)
    d.rectangle((10,6,21,24),outline=red)
    if kind.endswith('lightning'): d.line((18,7,13,13,19,13,12,23),fill=red,width=2)
    elif kind.endswith('snare'):
        d.rectangle((12,10,19,18),outline=red,width=2);d.line((16,7,16,23),fill=red,width=2);d.line((11,21,21,21),fill=red,width=2)
    else:
        d.polygon([(16,8),(20,12),(19,19),(16,23),(12,19),(11,12)],outline=red);d.line((16,10,16,20),fill=red,width=2)
    d.point((11,4),fill=glow);d.point((23,17),fill=glow);return im

def outputs():
    rule=read('rules.json');desk=read('desk.json');out={DATA/'talisman/rules.json':encoded({k:v for k,v in rule.items() if k!='names'})}
    for kind in ('wood','copper','paper_surface','inkwell'): out[ASSETS/f'textures/block/talisman_{kind}.png']=png(texture(kind))
    elements=[{'from':p['from'],'to':p['to'],'faces':{f:{'texture':'#'+p['material']} for f in FACES}} for p in desk['parts']]
    model={'parent':'minecraft:block/block','textures':{k:'myvillage:block/'+v for k,v in desk['textures'].items()},'elements':elements}
    model['textures']['particle']=model['textures']['wood']
    out[ASSETS/'models/block/talisman_desk.json']=encoded(model)
    out[ASSETS/'blockstates/talisman_desk.json']=encoded({'variants':{'':{'model':'myvillage:block/talisman_desk'}}})
    out[ASSETS/'models/item/talisman_desk.json']=encoded({'parent':'myvillage:block/talisman_desk'})
    for kind in rule['names']:
        if kind=='talisman_desk': continue
        out[ASSETS/f'textures/item/{kind}.png']=png(icon(kind))
        out[ASSETS/f'models/item/{kind}.json']=encoded({'parent':'minecraft:item/generated','textures':{'layer0':f'myvillage:item/{kind}'}})
    for name,recipe in read('recipes.json').items(): out[DATA/f'recipe/{name}.json']=encoded(recipe)
    out[DATA/'loot_table/blocks/talisman_desk.json']=encoded({'type':'minecraft:block','pools':[]})
    # Only new keys are owned here; existing alchemy/forging translations keep their generator.
    for language in ('zh_cn','en_us'):
        langpath=ASSETS/f'lang/{language}.json'; lang=json.loads((ROOT/langpath).read_text())
        for name,label in rule['names'].items(): lang[('block' if name=='talisman_desk' else 'item')+'.myvillage.'+name]=label
        lang.update({'screen.myvillage.crafting.talisman':'制符','screen.myvillage.crafting.talisman_awaken':'前往测灵碑觉醒灵根，测定炼丹、炼器与制符天资。','screen.myvillage.crafting.talisman_footer':'三项天资独立保存。制符案持续按住注灵，松开暂停；同一符式按品质增强。'})
        out[langpath]=encoded(lang)
    return out

def preview():
    from PIL import Image, ImageDraw
    target=ROOT/'reports/talisman-preview';target.mkdir(parents=True,exist_ok=True)
    # Exact model cuboids, isometric offline projection; no Minecraft process.
    im=Image.new('RGB',(1200,850),(29,36,33));d=ImageDraw.Draw(im)
    d.text((32,24),'朱纹制符案 · 正式生成资源离线预览',font=font(28),fill=(231,220,185))
    # Reuse the established Minecraft JSON-model rasterizer, including final UV textures and depth.
    if str(ROOT) not in sys.path: sys.path.insert(0,str(ROOT))
    import numpy as np
    from tools.combat_preview import item_model as pim
    from tools.beastgen.preview import Frame, render
    assets=pim.Assets([ROOT/'src/main/resources'],pim.VANILLA_JAR)
    if assets.jar is None:
        # Only the vanilla transform parent is missing in isolated worktrees; exact cuboids/UVs are local.
        model=assets.model_json('myvillage:block/talisman_desk');model.pop('parent',None)
    else: model=assets.model_json('myvillage:block/talisman_desk')
    ref='myvillage:block/talisman_desk'
    resolved=pim.resolve_model(assets,model,ref)
    if resolved.errors or resolved.warnings: raise ValueError(resolved.errors+resolved.warnings)
    quads=[((q.pos-np.array([8,0,8]))/16,q.uv,q.tex,None) for idx,el in enumerate(resolved.elements) for q in pim.element_quads(assets,resolved,el,idx)]
    desk=render(quads,Frame((-1,.9,-1.3),520,560,460,(0,.4,0),ss=2),bg=(29,36,33),body_rot=180)
    desk.save(target/'desk_model.png');im.paste(desk,(10,120))
    # Formal texture assets at enlarged nearest-neighbour resolution.
    for i,kind in enumerate(('talisman_paper','spiritual_ink','talisman_lightning','talisman_snare','talisman_ward')):
        pic=Image.open(ROOT/ASSETS/f'textures/item/{kind}.png').resize((96,96),Image.Resampling.NEAREST)
        im.paste(pic,(560+i*120,105),pic);d.text((560+i*120,210),read('rules.json')['names'][kind],font=font(18),fill=(220,204,165))
    # Same GUI geometry as TalismanScreen, shown at 2x; a planning/readability check, not runtime evidence.
    ui=Image.new('RGB',(300,238),(235,227,205));u=ImageDraw.Draw(ui)
    u.rectangle((0,0,299,237),outline=(149,128,84));u.text((9,7),'朱纹制符案 · 一纸承灵',font=font(11),fill=(41,61,51))
    for i,label in enumerate(('引雷符','镇步符','护身符')):
        y=30+24*i;u.rectangle((8,y,96,y+20),fill=(225,211,177),outline=(156,121,60));u.text((20,y+3),label,font=font(11),fill=(41,61,51))
    for x,label in ((116,'符纸'),(152,'灵墨'),(262,'成符')):
        u.text((x-2,22),label,font=font(9),fill=(101,117,93));u.rectangle((x-1,33,x+17,51),outline=(156,121,60))
    paper=Image.open(ROOT/ASSETS/'textures/item/talisman_ward.png');ui.paste(paper,(201,31),paper)
    u.text((110,72),'朱纹渐成 · 持续注灵',font=font(11),fill=(41,61,51));u.text((110,86),'冻结资质 90 · 极品  24/40',font=font(10),fill=(101,117,93))
    u.rectangle((110,100,288,103),fill=(193,189,162));u.rectangle((110,100,217,103),fill=(151,79,63))
    u.rectangle((110,111,288,133),fill=(225,211,177),outline=(156,121,60));u.text((126,116),'持续注灵中 · 松开暂停',font=font(11),fill=(41,61,51))
    u.text((10,108),'松开即停',font=font(10),fill=(41,61,51));u.text((10,121),'关案封存符稿',font=font(10),fill=(101,117,93))
    u.text((9,135),'符式初学即用；品质由独立制符资质决定',font=font(10),fill=(101,117,93));u.text((70,144),'物品栏',font=font(9),fill=(101,117,93))
    for row in range(4):
        for col in range(9):
            x,y=70+col*18,156+row*18+(4 if row==3 else 0);u.rectangle((x-1,y-1,x+16,y+16),outline=(156,121,60))
    im.paste(ui.resize((600,476),Image.Resampling.NEAREST),(560,285))
    d.text((32,700),'小型木案 / 深铜包边 / 朱纸墨池 / 青金灵光',font=font(19),fill=(204,191,161))
    d.text((32,740),'离线资源与布局自查；游戏光照、手感、多人均未验证。',font=font(18),fill=(163,177,155))
    im.save(target/'contact_sheet.png');ui.save(target/'layout.png')
    (target/'manifest.json').write_bytes(encoded({'source':'generated block model + exact item textures + GUI geometry mockup','runtime':'not_verified','screen':[300,238],'hold':[110,111,178,22]}))
    print(target/'contact_sheet.png')

def main():
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');p.add_argument('--preview',action='store_true');args=p.parse_args()
    changed=[]
    for path,data in outputs().items():
        dest=ROOT/path
        if not dest.exists() or dest.read_bytes()!=data:
            changed.append(str(path))
            if not args.check: dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
    if args.check and changed: print('制符资源漂移：\n'+'\n'.join(changed));return 1
    if args.preview: preview()
    print(f'制符资源 {"检查通过" if args.check else "已生成"}');return 0
if __name__=='__main__':raise SystemExit(main())
