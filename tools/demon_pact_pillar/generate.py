#!/usr/bin/env python3
"""魔契柱：整体造型裁入真实占用格；题字跨格共用同一幅画布。"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from tools.gen_alchemy_assets import png

SOURCE = Path('tools/demon_pact_pillar')
ASSETS = Path('src/main/resources/assets/myvillage')
DATA = Path('src/main/resources/data/myvillage')
IDENTITY = 'demon_pact_pillar'


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode()


def appearance():
    a = json.loads((ROOT / SOURCE / 'appearance.json').read_text())
    assert a['id'] == IDENTITY and a['size'] == [4, 10, 4]
    assert 0 < a['strength'] <= 20 and 0 <= a['explosion_resistance'] <= 1200
    for f in a['forms']:
        assert all(0 <= lo < hi <= size for lo, hi, size in zip(f['from'], f['to'], a['size']))
        assert all(n * 16 == round(n * 16) for n in f['from'] + f['to'])
    return a


def parts(a):
    result = []
    for y in range(a['size'][1]):
        for z in range(a['size'][2]):
            for x in range(a['size'][0]):
                offset = [x, y, z]
                boxes = []
                for form in a['forms']:
                    low = [max(v, o) for v, o in zip(form['from'], offset)]
                    high = [min(v, o + 1) for v, o in zip(form['to'], offset)]
                    if all(lo < hi for lo, hi in zip(low, high)):
                        boxes.append({'from': [round((v-o)*16, 6) for v, o in zip(low, offset)],
                                      'to': [round((v-o)*16, 6) for v, o in zip(high, offset)],
                                      'form': a['forms'].index(form)})
                if boxes:
                    result.append({'offset': offset, 'boxes': boxes})
    assert result[0]['offset'] == [0, 0, 0] and 1 < len(result) <= 128
    return result


def rgb(h):
    return tuple(bytes.fromhex(h))


def textures(a):
    colors = {k: rgb(v) for k, v in a['colors'].items()}
    mask = (ROOT / SOURCE / 'inscription.pgm').read_bytes()
    header, dims, maximum, pixels = mask.split(b'\n', 3)
    assert header == b'P5' and dims == b'256 640' and maximum == b'255' and len(pixels) == 256*640

    def canvas(w, h, material):
        rows = []
        for y in range(h):
            row = []
            for x in range(w):
                grain = ((x*19 + y*37 + x*y*11) % 17 - 8) * .42
                vein = 2.5*math.sin(x*.13 + math.sin(y*.08)*3)
                base = colors['bronze' if material == 'bronze' else 'stone']
                light = grain + vein
                if material == 'bronze':
                    light += 12*math.cos(y/h*math.tau)
                    if ((x*3+y*11) % 47) < 3: light -= 21
                if material in ('base', 'crown'):
                    light -= 8
                    if y < h*.09 or y > h*.9: light += 14
                row.append(tuple(max(0, min(255, round(c+light))) for c in base) + (255,))
            rows.append(row)
        return rows

    result = {}
    for material, size in [('base',(256,64)),('bronze',(128,64)),('crown',(128,80)),('stone',(128,128))]:
        rows = canvas(*size, material)
        w,h = size
        for y in range(h):
            for x in range(w):
                # A carved continuous perimeter and restrained meander, never separate plates.
                line = y in (round(h*.16),round(h*.83))
                if material in ('base','bronze') and h*.30 < y < h*.68:
                    u, v = x%32, y-round(h*.30)
                    line |= (v in (0,round(h*.38)) or u in (0,31) or (u==16 and v<round(h*.25)))
                if line: rows[y][x] = tuple(round(c*.72) for c in colors['ink'])+(255,)
        result[material] = png(rows)
    front = canvas(256,640,'stone')
    for y in range(640):
        for x in range(256):
            border = (x in (17,18,237,238) and 23<=y<=616) or (y in (23,24,615,616) and 17<=x<=238)
            alpha = pixels[y*256+x]/255
            if border: alpha = .75
            if alpha:
                # Offset dark incision and warm mineral inlay, zero emitted light.
                color = colors['ink']
                front[y][x] = tuple(round(c*(1-alpha)+d*alpha) for c,d in zip(front[y][x][:3],color))+(255,)
            elif x>1 and y>1 and pixels[(y-1)*256+x-1]>96:
                front[y][x] = colors['shadow']+(255,)
    result['front'] = png(front)
    for name, w, h in [('back',256,640),('side',128,400)]:
        rows = canvas(w,h,'stone')
        for y in range(h):
            for x in range(w):
                u,v=(x-(w-1)/2)/w,(y-(h-1)/2)/h
                ring=abs(math.hypot(u*1.8,v*4)-.65)<.014 if name=='back' else False
                line=(abs(u)<.012 and abs(v)<.37) or ring
                for cy in (-.30,-.16,0,.16,.30):
                    line |= abs(abs(u)*.55+abs(v-cy)-.07)<.008 and abs(u)<.13 and abs(v-cy)<.08
                if line: rows[y][x]=tuple(round(c*.56) for c in colors['ink'])+(255,)
        result[name]=png(rows)
    return result


def faces(form, low, high):
    lo, hi = form['from'],form['to']
    material=form['material']
    result={}
    # UVs use whole-form coordinates, so Chinese characters do not repeat per block.
    for face,axis,positive in [('west',0,False),('east',0,True),('down',1,False),('up',1,True),('north',2,False),('south',2,True)]:
        if (high if positive else low)[axis] != (hi if positive else lo)[axis]: continue
        if face in ('north','south'):
            u0,u1=[(p[0]-lo[0])/(hi[0]-lo[0])*16 for p in (low,high)]
            v0,v1=[(hi[1]-p[1])/(hi[1]-lo[1])*16 for p in (high,low)]
            if face=='north': u0,u1=16-u1,16-u0
        elif face in ('west','east'):
            u0,u1=[(p[2]-lo[2])/(hi[2]-lo[2])*16 for p in (low,high)]
            v0,v1=[(hi[1]-p[1])/(hi[1]-lo[1])*16 for p in (high,low)]
            if face=='east': u0,u1=16-u1,16-u0
        else:
            u0,u1=[(p[0]-lo[0])/(hi[0]-lo[0])*16 for p in (low,high)]
            v0,v1=[(p[2]-lo[2])/(hi[2]-lo[2])*16 for p in (low,high)]
        tex = material
        if material=='shaft': tex={'north':'front','south':'back','west':'side','east':'side'}.get(face,'stone')
        if face in ('up','down') and material!='bronze': tex='stone'
        result[face]={'uv':[round(n,6) for n in (u0,v0,u1,v1)],'texture':'#'+tex}
    return result


def generate():
    a=appearance(); cells=parts(a); output={}
    refs={name:'myvillage:block/demon_pact_pillar/'+name for name in ('front','back','side','stone','base','bronze','crown')}
    refs['particle']=refs['stone']
    variants={}
    for i,part in enumerate(cells):
        elements=[]
        for b in part['boxes']:
            offset=part['offset']; f=a['forms'][b['form']]
            low=[o+n/16 for o,n in zip(offset,b['from'])]; high=[o+n/16 for o,n in zip(offset,b['to'])]
            visible=faces(f,low,high)
            if visible: elements.append({'from':b['from'],'to':b['to'],'faces':visible})
        output[ASSETS/f'models/block/{IDENTITY}/part_{i}.json']=encoded({'textures':refs,'ambientocclusion':False,'elements':elements})
        for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:
            variants[f'facing={facing},part={i}']={'model':f'myvillage:block/{IDENTITY}/part_{i}','y':angle}
    output[ASSETS/f'blockstates/{IDENTITY}.json']=encoded({'variants':variants})
    output[DATA/f'decoration/{IDENTITY}.json']=encoded({'size':a['size'],'strength':a['strength'],'explosion_resistance':a['explosion_resistance'],'parts':cells})
    # A proper 3D held/GUI miniature shares the placed block's complete material atlas.
    scale=16/max(f['to'][1] for f in a['forms'])
    elements=[]
    for f in a['forms']:
        elements.append({'from':[(f['from'][0]-2)*scale+8,f['from'][1]*scale,(f['from'][2]-2)*scale+8],
                         'to':[(f['to'][0]-2)*scale+8,f['to'][1]*scale,(f['to'][2]-2)*scale+8],
                         'faces':faces(f,f['from'],f['to'])})
    output[ASSETS/f'models/block/{IDENTITY}.json']=encoded({'parent':'minecraft:block/block','textures':refs,'elements':elements,
        'display':{'gui':{'rotation':[20,200,0],'translation':[0,0,0],'scale':[.9,.9,.9]},
                   'fixed':{'rotation':[0,180,0],'scale':[1,1,1]},'ground':{'scale':[.6,.6,.6]}}})
    output[ASSETS/f'models/item/{IDENTITY}.json']=encoded({'parent':f'myvillage:block/{IDENTITY}'})
    output[DATA/f'loot_table/blocks/{IDENTITY}.json']=encoded({'type':'minecraft:block','pools':[{'rolls':1,
        'conditions':[{'condition':'minecraft:block_state_property','block':f'myvillage:{IDENTITY}','properties':{'part':'0'}}],
        'entries':[{'type':'minecraft:item','name':f'myvillage:{IDENTITY}'}]}]})
    for key,value in textures(a).items(): output[ASSETS/f'textures/block/{IDENTITY}/{key}.png']=value
    return output


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    bad=[]
    for relative,value in generate().items():
        path=ROOT/relative
        if args.check:
            if not path.is_file() or path.read_bytes()!=value: bad.append(str(relative))
        else: path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(value)
    # Chinese shared catalogue edits preserve all other names.
    a=appearance()
    translations={'block.myvillage.demon_pact_pillar':a['name'],
                  'tooltip.myvillage.demon_pact_pillar':f"整根放置 · 底座 {a['size'][0]}×{a['size'][2]} 格 · 高 {max(f['to'][1] for f in a['forms']):g} 格",
                  'tooltip.myvillage.demon_pact_pillar.anchor':'点击底座角点的地面；刻字面朝向放置者'}
    path=ROOT/ASSETS/'lang/zh_cn.json';lang=json.loads(path.read_text())
    if args.check:
        bad.extend(k for k,v in translations.items() if lang.get(k)!=v)
    else: lang.update(translations);path.write_bytes(encoded(lang))
    if bad: raise SystemExit('魔契柱资源漂移：\n'+'\n'.join(bad))
    print(f'魔契柱资源 {"检查通过" if args.check else "已生成"}：{len(parts(appearance()))} 个真实占用格，特效 0')


if __name__=='__main__': main()
