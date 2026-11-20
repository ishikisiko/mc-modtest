"""Compile the selected Tianheng blueprints into native material and profile banks."""
from __future__ import annotations

import argparse
import base64
from collections import defaultdict
from functools import lru_cache
import gzip
import json
from pathlib import Path
import struct

import numpy as np

from tools.lord_manors.generate import bank_resources, model as native_model

ROOT = Path(__file__).resolve().parents[2]
AUTHOR = ROOT / 'blueprints/tianheng'
ASSETS = Path('assets/yuanxian')
DATA = Path('data/yuanxian')
FULL = (0, 0, 0, 16, 16, 16)
NAMES = {
    'foot':'深色台基', 'stone':'青石', 'pave':'街坪', 'ashlar':'石砌饰面',
    'wall':'灰泥墙', 'earth':'赭砖', 'beam':'梁木', 'wood':'深木',
    'deck':'楼板', 'roof':'碧瓦', 'ridge':'青脊', 'gold':'铜线',
    'grille':'格窗纸', 'lamp':'灯芯', 'grass':'院地', 'leaf':'庭树冠',
    'dirt':'土坪', 'crate':'货箱', 'red':'朱毯', 'fabric':'红篷',
    'green':'青席', 'jade':'碧玉', 'porcelain':'浅玉', 'relief':'浮雕石',
    'gilt':'鎏金', 'lacquer':'漆木', 'slate':'墨瓦', 'ink_jade':'深青饰面',
}


def encode(value):
    return (json.dumps(value, ensure_ascii=False, separators=(',', ':'))+'\n').encode()


def blob(value):
    return gzip.decompress(base64.b64decode(value, validate=True))


class Output:
    def __init__(self, directory):
        self.directory = Path(directory)

    def put(self, path, value):
        destination = self.directory / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(value)

    def json(self, path, value):
        self.put(path, encode(value))


def prisms(row):
    lo, hi, bevel, axis = row[:3], row[3:6], row[7], row[8]
    u, v = (axis+1)%3, (axis+2)%3
    bevel = min(bevel, (hi[u]-lo[u])//2, (hi[v]-lo[v])//2)
    for side in range(2):
        for i in range(bevel):
            low, high = list(lo), list(hi)
            n = lo[v]+i if side==0 else hi[v]-1-i
            low[v], high[v] = n, n+1
            low[u] += bevel-i
            high[u] -= bevel-i
            if high[u]>low[u]:
                yield tuple(low+high)
    low, high = list(lo), list(hi)
    low[v] += bevel
    high[v] -= bevel
    if high[v]>low[v]:
        yield tuple(low+high)


def add_parts(pieces, solid, bounds, role, collision):
    x,y,z,X,Y,Z = bounds
    for yy in range(y//16, (Y+15)//16):
        for zz in range(z//16, (Z+15)//16):
            for xx in range(x//16, (X+15)//16):
                if solid[yy,zz,xx]:
                    continue
                pos=(xx,yy,zz)
                off=(16*xx,16*yy,16*zz)
                lo=tuple(max(0,bounds[i]-off[i]) for i in range(3))
                hi=tuple(min(16,bounds[i+3]-off[i]) for i in range(3))
                if all(a<b for a,b in zip(lo,hi)):
                    pieces[pos].add((lo+hi+(role,),collision))


@lru_cache(maxsize=1)
def compile_architecture():
    data = json.loads((AUTHOR/'architecture.json').read_bytes())
    roles = ['']+data['role_order']
    if data['schema']!=1 or set(roles[1:])!={*NAMES,'water'}:
        raise ValueError('天衡殿材质目录无效')
    mats = {role: {
        'id':'tianheng_'+role, 'role':role, 'name':'天衡殿·'+label,
        'collides':True, 'opaque':True,
        'sound':'wood' if role in ('beam','wood','deck','crate','lacquer') else 'stone',
        'light_level':12 if role=='lamp' else 0,
    } for role,label in NAMES.items()}
    choices={}
    profiles=set()
    for key, plan in sorted(data['plans'].items()):
        w,h,d=plan['size']
        solid=np.zeros((h,d,w),np.uint8)
        for x,y,z,X,Y,Z,value,_ in struct.iter_unpack('<8B',blob(plan['commands'])):
            if not (0<=x<X<=w and 0<=y<Y<=h and 0<=z<Z<=d and value<len(roles)):
                raise ValueError((key,'体块越界'))
            solid[y:Y,z:Z,x:X]=value
        if (solid[0]==0).any():
            raise ValueError((key,'缺少完整院落地基'))
        pieces=defaultdict(set)
        collision=set(plan['collision_components'])
        for i,row in enumerate(struct.iter_unpack('<10H',blob(plan['decor']))):
            if not (0<=row[0]<row[3]<=w*16 and 0<=row[1]<row[4]<=h*16
                    and 0<=row[2]<row[5]<=d*16 and row[8] in (0,1,2) and roles[row[6]] in mats):
                raise ValueError((key,'构件越界',row))
            for box in prisms(row):
                add_parts(pieces,solid,box,roles[row[6]],i in collision)
        shapes={pos:tuple(sorted(boxes)) for pos, boxes in pieces.items()}
        profiles.update(shapes.values())
        choices[key]=(plan,solid,shapes)
    bank=sorted(profiles)
    if not bank or len(bank)>8192:
        raise ValueError('天衡殿细分构件规模越界')
    indices={profile:i for i,profile in enumerate(bank)}
    return roles, mats, data['textures'], bank, indices, choices


def block_model(profile,mats,city):
    value=native_model(profile,mats,city)
    for k,v in value['textures'].items():
        value['textures'][k]=v.replace('/lord_manors/','/tianheng/')
    return value


def export(directory):
    _,mats,images,profiles,_,choices=compile_architecture()
    out=Output(directory)
    names={}
    for role,material in mats.items():
        ident=material['id']
        texture='yuanxian:block/tianheng/'+ident
        out.put(ASSETS/f'textures/block/tianheng/{ident}.png',base64.b64decode(images[role],validate=True))
        out.json(ASSETS/f'blockstates/{ident}.json',{'variants':{'':{'model':'yuanxian:block/'+ident}}})
        out.json(ASSETS/f'models/block/{ident}.json',{'parent':'minecraft:block/cube_all','textures':{'all':texture}})
        out.json(ASSETS/f'models/item/{ident}.json',{'parent':'yuanxian:block/'+ident})
        out.json(DATA/f'loot_table/blocks/{ident}.json',{
            'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'yuanxian:'+ident}]}],
        })
        names['block.yuanxian.'+ident]=material['name']
    banks=[]
    for offset in range(0,len(profiles),32):
        pieces=profiles[offset:offset+32]
        pieces+=pieces[:1]*(32-len(pieces))
        bank={'id':f'tianheng_carved_{offset//32}','name':'天衡殿精雕构件',
              'profiles':[[box for box,_ in parts] for parts in pieces]}
        bank_resources(out,bank,mats,'tianheng',names,model_factory=block_model,
                       asset_root=ASSETS,data_root=DATA)
        bank['profiles']=[{
            'boxes':[box for box,_ in parts],
            'collision':[box[:6] for box,solid in parts if solid],
            'occlusion':[],
            'translucent':{},
            'light_level':max(mats[box[6]]['light_level'] for box,_ in parts),
        } for parts in pieces]
        banks.append(bank)
    out.json(DATA/'tianheng/components/catalog.json',dict(schema=1,materials=list(mats.values()),
             banks=banks,layout={},strength=2.0,explosion_resistance=5.0))
    out.json(DATA/'tianheng/components/names.json',names)
    print(json.dumps(dict(materials=len(mats),profiles=len(profiles),banks=len(banks),buildings=list(choices)),ensure_ascii=False))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    export(parser.parse_args().out)
