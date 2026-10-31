#!/usr/bin/env python3
"""独立回读精雕 JSON：检查共面重复、实心外表面覆盖和 UV 范围。"""
from __future__ import annotations

import argparse
from collections import Counter
from functools import lru_cache
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
# 使用带空边的 18³ 格点位图，独立于生成器的矩形差集。
FACES={'west':(0,False,2,1),'east':(0,True,2,1),'down':(1,False,0,2),
       'up':(1,True,0,2),'north':(2,False,0,1),'south':(2,True,0,1)}
STRIDE=(324,18,1)


@lru_cache(maxsize=16384)
def volume_bits(box):
    x0,y0,z0,x1,y1,z1=box;bits=0;row=((1<<(z1-z0))-1)<<(z0+1)
    for x in range(x0+1,x1+1):
        for y in range(y0+1,y1+1):bits|=row<<(x*324+y*18)
    return bits


@lru_cache(maxsize=32768)
def face_bits(name,lo,hi):
    axis,positive,u,v=FACES[name];plane=hi[axis] if positive else lo[axis]
    base=(plane+(not positive))*STRIDE[axis];bits=0
    for a in range(lo[u]+1,hi[u]+1):
        for b in range(lo[v]+1,hi[v]+1):bits|=1<<(base+a*STRIDE[u]+b*STRIDE[v])
    return bits


def model_layers(model):
    """只接受原版模型或 NeoForge 官方 composite；每个子模型有明确渲染层。"""
    if 'loader' not in model:
        for element in model['elements']:yield model['render_type'],element
        return
    if model['loader']!='neoforge:composite' or model.get('elements'):raise ValueError('未声明的精雕模型加载器')
    children=model['children']
    if not children or model['item_render_order']!=list(children):raise ValueError('复合模型物品渲染次序不完整')
    for child in children.values():yield from model_layers(child)


def audit_model(model,profile,opaque_roles,expected_layers=None):
    occupied=0;opaque=0;seen={f:0 for f in FACES};solid={f:0 for f in FACES}
    for box in profile:
        bits=volume_bits(tuple(box[:6]));occupied|=bits
        if box[6] in opaque_roles:opaque|=bits
    overlaps=0;quads=0;layer_errors=0
    for layer,element in model_layers(model):
        lo=tuple(element['from']);hi=tuple(element['to'])
        if any(not isinstance(v,int) or not 0<=v<=16 for v in lo+hi):raise ValueError('模型顶点越出原 1/16 网格')
        if any(lo[i]>=hi[i] for i in range(3)):raise ValueError('模型余面体积无效')
        for name,face in element['faces'].items():
            bits=face_bits(name,lo,hi);role=face['texture'].removeprefix('#')
            if expected_layers is not None and layer!=expected_layers[role]:layer_errors+=1
            if bits&~occupied:raise ValueError('模型面越出原构件体积')
            overlaps+=(seen[name]&bits).bit_count();seen[name]|=bits;quads+=1
            if role in opaque_roles:solid[name]|=bits
            if len(face['uv'])!=4 or any(not 0<=v<=16 for v in face['uv']):raise ValueError('UV 越出原材质范围')
            if 'cullface' in face:
                axis,positive,_,_=FACES[name]
                if face['cullface']!=name or (hi if positive else lo)[axis]!=(16 if positive else 0):raise ValueError('邻块剔除面不在方块边界')
    coverage_errors=0
    for name,(axis,positive,_,_) in FACES.items():
        neighbor=(opaque>>STRIDE[axis]) if positive else (opaque<<STRIDE[axis])
        expected=opaque&~neighbor
        coverage_errors+=(solid[name]^expected).bit_count()
    return {'overlap_units':overlaps,'opaque_coverage_errors':coverage_errors,'render_layer_errors':layer_errors,'quads':quads}


def audit(root=ROOT):
    root=Path(root);catalog=json.loads((root/'src/main/resources/data/myvillage/lord_manors/catalog.json').read_text())
    opaque_by_city={};layers_by_city={};city_counts=Counter();bad=[];profiles=0;old_faces=0;new_faces=0;max_quads=0
    for material in catalog['materials']:
        city=material['id'].split('_')[1]
        if material['opaque']:opaque_by_city.setdefault(city,set()).add(material['role'])
        base=json.loads((root/f'src/main/resources/assets/myvillage/models/block/{material["id"]}.json').read_text())
        layers_by_city.setdefault(city,{})[material['role']]=base['render_type']
    for bank in catalog['banks']:
        city=bank['id'].split('_')[1]
        for shape,p in enumerate(bank['profiles']):
            path=root/f'src/main/resources/assets/myvillage/models/block/{bank["id"]}/shape_{shape}.json'
            model=json.loads(path.read_text());opaque=opaque_by_city[city]|{b[6] for b in p['boxes'] if b[6].startswith('plaque_')}
            layers={**layers_by_city[city],**{b[6]:'minecraft:solid' for b in p['boxes'] if b[6].startswith('plaque_')}}
            result=audit_model(model,p['boxes'],opaque,layers);profiles+=1;old_faces+=6*len(p['boxes']);new_faces+=result['quads'];max_quads=max(max_quads,result['quads'])
            if result['overlap_units'] or result['opaque_coverage_errors'] or result['render_layer_errors']:
                city_counts[city]+=1
                if len(bad)<8:bad.append({'model':str(path.relative_to(root)),**result})
    return {'status':'fail' if city_counts else 'pass','profiles':profiles,'invalid_profiles':sum(city_counts.values()),
            'cities_with_errors':dict(city_counts),'original_quads':old_faces,'visible_quads':new_faces,'max_profile_quads':max_quads,'examples':bad}


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--report',type=Path)
    args=parser.parse_args();result=audit(args.root);text=json.dumps(result,ensure_ascii=False,indent=2)+'\n'
    if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(text)
    print(text,end='')
    if result['status']!='pass':raise SystemExit(1)


if __name__=='__main__':main()
