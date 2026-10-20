"""Three authored forging materials and 32px inventory sprites; this generator owns no acquisition recipes."""
import json
import re
from pathlib import Path
from tools.gen_alchemy_assets import Canvas,png
from tools.ingot_shape import draw as draw_flat_ingot
ROOT=Path(__file__).resolve().parents[2]

def load(path=None):
    data=json.loads((path or ROOT/'tools/forging/materials.json').read_text())
    if set(data)!={'version','materials'} or data['version']!=4:raise ValueError('Material schema')
    materials=data['materials'];ids=set();traits=set();tags=set()
    for m in materials:
        if set(m)!={'id','name','trait','description','acquisition','tag'}:raise ValueError('Material fields')
        if not re.fullmatch('[a-z0-9_]+',m['id']) or m['id'] in ids:raise ValueError('Material id')
        ids.add(m['id'])
        if not re.fullmatch('[a-z0-9_.-]+:[a-z0-9_./-]+',m['tag']) or any(part in ('','.','..') for part in m['tag'].split(':')[-1].split('/')) or m['tag'] in tags:raise ValueError('Material tag')
        tags.add(m['tag'])
        if m['trait'] not in ('sharp','channeling','tough') or m['trait'] in traits:raise ValueError('Material trait')
        traits.add(m['trait'])
        for key in ('name','description','acquisition'):
            if len(m[key])!=2 or not all(isinstance(s,str) and s.strip() for s in m[key]):raise ValueError('Bilingual material strings')
    if len(materials)!=3 or traits!={'sharp','channeling','tough'}:raise ValueError('Three specialized materials required')
    return materials

def lang():
    strings={'tooltip.myvillage.forging_material.use':('Use as a forging additive.','在炼器鼎中用作辅材。')}
    for m in load():
        strings['item.myvillage.'+m['id']]=m['name']
        strings['tag.item.'+m['tag'].replace(':','.').replace('/','.')]=m['name']
        strings['tooltip.myvillage.forging_material.'+m['id']]=m['description']
        strings['tooltip.myvillage.forging_material.source.'+m['id']]=m['acquisition']
    return strings

def icon(item):
    c=Canvas()
    if item=='xuantie_ingot':
        side,front,top,shine,edge=(26,36,48),(56,72,90),(84,102,122),(188,214,238),(112,146,167)
        draw_flat_ingot(c,side,front,top)
        c.line(9,10,23,8,shine);c.line(4,17,7,20,shine)
        c.line(7,22,23,18,side);c.line(7,24,22,20,top);c.line(25,16,27,15,shine)
        # Silver seam keeps the dark-iron identity without changing the flat pose.
        c.line(8,16,13,13,(36,48,64));c.line(13,13,18,15,(36,48,64));c.line(18,15,23,12,(36,48,64))
        c.line(8,15,13,12,edge);c.line(13,12,18,14,edge);c.line(18,14,23,11,edge)
        c.put(10,14,(206,226,242));c.put(16,13,(206,226,242));c.put(21,12,(206,226,242))
    elif item=='chitong_ingot':
        top,front,side,shine=(202,125,76),(140,66,42),(91,44,35),(248,190,113)
        draw_flat_ingot(c,side,front,top)
        c.line(9,10,23,8,shine);c.line(4,17,7,20,shine);c.line(7,22,23,18,side)
        c.line(7,24,22,20,top);c.line(25,16,27,15,shine)
        c.line(11,15,18,12,(88,53,39));c.line(18,12,22,13,(88,53,39));c.line(22,13,15,16,(88,53,39))
        c.line(12,14,18,13,(255,98,70));c.line(18,13,20,14,(255,169,103));c.put(9,16,(230,83,53))
    elif item=='edge_tempering_sand':
        c.poly([(3,20),(8,15),(25,14),(29,18),(27,25),(17,28),(6,26)],(36,45,51))
        c.poly([(4,20),(9,17),(25,16),(28,19),(24,24),(8,25)],(76,88,93))
        c.poly([(5,20),(10,14),(13,10),(17,12),(20,8),(25,16),(27,19),(23,22),(9,23)],(156,112,67))
        c.poly([(7,19),(11,14),(14,11),(17,15),(20,9),(24,17),(25,20),(15,22)],(219,183,114))
        for x,y in [(10,16),(13,13),(15,17),(19,13),(21,16),(23,19),(16,20),(8,20)]:
            c.rect(x,y,x+1,y+1,(250,233,180))
        for x,y in [(12,18),(17,15),(20,19),(14,21),(24,17)]:c.put(x,y,(109,81,54))
        c.line(7,25,17,27,(112,132,139));c.line(17,27,25,24,(72,90,102))
        c.put(6,14,(224,195,127));c.put(25,10,(246,227,177));c.put(28,23,(156,126,77))
    else:raise ValueError('Unknown material icon: '+item)
    c.outline((19,27,31));return c.rows

def outputs(root=ROOT):
    base=root/'src/main/resources';result={}
    encode=lambda v:(json.dumps(v,ensure_ascii=False,indent=2)+'\n').encode()
    for m in load(root/'tools/forging/materials.json'):
        name=m['id'];namespace,tag=m['tag'].split(':')
        result[base/f'data/{namespace}/tags/item/{tag}.json']=encode({'replace':False,'values':['myvillage:'+name]})
        result[base/f'assets/myvillage/textures/item/{name}.png']=png(icon(name))
        result[base/f'assets/myvillage/models/item/{name}.json']=encode({'parent':'minecraft:item/generated','textures':{'layer0':'myvillage:item/'+name}})
    return result

def retired_paths(root=ROOT):
    """Only the six acquisition outputs owned by this generator in 0.57.2."""
    base=root/'src/main/resources/data/myvillage'
    return [base/f'{folder}/{name}.json'
            for name in ('xuantie_ingot','chitong_ingot','edge_tempering_sand')
            for folder in ('recipe','advancement/recipes/misc')]

def preview(root=ROOT):
    from PIL import Image,ImageDraw,ImageFont
    out=root/'out/preview/forging_materials';out.mkdir(parents=True,exist_ok=True)
    sheet=Image.new('RGB',(900,390),(27,37,37));d=ImageDraw.Draw(sheet)
    font=ImageFont.truetype('/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc',23)
    small=ImageFont.truetype('/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc',17)
    for i,m in enumerate(load()):
        x=i*300;d.rounded_rectangle((x+8,8,x+291,380),12,fill=(36,47,47),outline=(71,88,84))
        im=Image.open(root/f'src/main/resources/assets/myvillage/textures/item/{m["id"]}.png').convert('RGBA').resize((224,224),Image.Resampling.NEAREST)
        sheet.paste(im,(x+38,24),im);d.text((x+150,280),m['name'][1],font=font,fill=(237,219,183),anchor='mm')
        label={'sharp':'锋锐 · 提高攻击','channeling':'导灵 · 降低耗灵','tough':'坚韧 · 耐久与维修'}[m['trait']]
        d.text((x+150,318),label,font=small,fill=(163,190,181),anchor='mm')
        d.text((x+150,352),m['id'],font=small,fill=(136,158,151),anchor='mm')
    sheet.save(out/'materials.png')
    (out/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>炼器材料</title><style>body{background:#1b2525;color:#eadbb7;font:16px system-ui;max-width:1000px;margin:24px auto}img{width:100%;image-rendering:pixelated}</style><h1>玄铁、赤铜锭与淬锋砂</h1><p>读取正式 32px 物品贴图放大。三种材料分别接入坚韧、导灵与锋锐专长。</p><img src="materials.png">')
    print('Material preview:',out)
