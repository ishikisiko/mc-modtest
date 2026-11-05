"""从唯一作者输入导出可采掘的城郊矿场；不创建 NPC 或额外战利品。"""
from __future__ import annotations

import argparse
import base64
from collections import deque
from dataclasses import dataclass
import gzip
import hashlib
import json
import math
from pathlib import Path
import random
import struct
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from buildgen.grid import BlockGrid
from buildgen.groups import get_group
from buildgen.massing import Node
from buildgen.modset import load_modset
from buildgen.ops import roof_handler
from buildgen.style import load_style

SOURCE=ROOT/'blueprints/starter_mine/layout.json'
OUT=ROOT/'build/generated/starter_mine/resources'


def canonical(value):return json.dumps(value,ensure_ascii=False,separators=(',',':'),sort_keys=True).encode()
def digest(value):return hashlib.sha256(value).hexdigest()


def property_state(state,**props):
    name=state.split('[',1)[0];values={}
    if '[' in state:values=dict(v.split('=',1) for v in state.split('[',1)[1].rstrip(']').split(','))
    values.update({k:str(v).lower() for k,v in props.items()})
    return name+'['+','.join(k+'='+v for k,v in sorted(values.items()))+']'


@dataclass
class Model:
    rules:dict
    cells:dict
    terrain:list
    weights:list
    bodies:dict
    trees:list
    checks:dict


def build(source=SOURCE):
    r=json.loads(Path(source).read_text());p=r['palette'];w,h,d=r['size'];base=r['ground_y'];t=r['terrain']
    group=get_group(r['group']);modset=load_modset('vanilla');style=load_style(group.style_id,set(modset.namespaces))
    if r['schema']!=1 or r['id']!='starter_mine' or any(n<=0 or n>128 for n in (w,h,d)):raise ValueError('矿场尺寸或身份不合法')
    dense={};changed=set();be={};trees=[]
    def inside(x,y,z):return 0<=x<w and 0<=y<h and 0<=z<d
    def put(x,y,z,state,explicit=True):
        if not inside(x,y,z):raise ValueError(('矿场方块越界',x,y,z,state))
        dense[x,y,z]=state
        if explicit:changed.add((x,y,z))
    def get(x,y,z):return dense.get((x,y,z),p['air'])
    def box(a,b,state,explicit=True):
        for z in range(a[2],b[2]):
            for y in range(a[1],b[1]):
                for x in range(a[0],b[0]):put(x,y,z,state,explicit)
    def height(x,z):
        ramp=min(1,max(0,(z-t['rise_start'])/t['rise_length']))
        v=sum(amplitude*math.exp(-((x-cx-drift*z)/rx)**2-((z-cz)/rz)**2) for cx,cz,rx,rz,amplitude,drift in t['hills'])
        a,b,c,e,f=t['detail'];v+=a*math.sin(x/b+z/c)+e*math.sin(z/f)
        return base+math.floor(max(0,v*ramp))
    terrain=[height(x,z) for z in range(d) for x in range(w)]
    weights=[]
    for z in range(d):
        for x in range(w):
            top=terrain[z*w+x];edge=min(x,z,w-1-x,d-1-z);a=min(1,edge/t['blend_margin']);weights.append(round((a*a*(3-2*a))*255))
            box((x,0,z),(x+1,top-t['soil_depth'],z+1),p['stone'],False)
            box((x,top-t['soil_depth'],z),(x+1,top-1,z+1),p['dirt'],False);put(x,top-1,z,p['grass'],False)
    yard=t['yard'];x0,z0,x1,z1=yard['bounds'];cx,cz=yard['center'];rx,rz=yard['radii']
    for z in range(z0,z1):
        for x in range(x0,x1):
            if ((x-cx)/rx)**2+((z-cz)/rz)**2>=1:continue
            old=terrain[z*w+x];box((x,base,z),(x+1,max(base,old)+1,z+1),p['air'])
            terrain[z*w+x]=base;weights[z*w+x]=255
            put(x,base-1,z,p['path'] if (x+z)%yard['stone_period'] else p['stone'])
    path=t['surface_path']
    for z in range(path['z_end']):
        center=path['center_x']+round(path['curve']*math.sin(z/path['period']))
        # 出口前段固定对准正式 road_port，避免预览曲线与运行时接路错开。
        if z<t['blend_margin']:center=r['road_port'][0]
        for x in range(center-path['radius'],center+path['radius']+1):
            terrain[z*w+x]=base;weights[z*w+x]=255;put(x,base-1,z,p['path'])
    # 地下由运行时洞穴雕刻器按世界种子生成；作者资源只保留地面矿口支护。
    supports=r['supports'];cx=supports['center_x'];radius=supports['half_width']
    for z in supports['z']:
        y=base
        for x in [cx-radius,cx+radius]:box((x,y,z),(x+1,y+supports['height'],z+1),p['log'])
        box((cx-radius,y+supports['height']-1,z),(cx+radius+1,y+supports['height'],z+1),p['beam_x'])
        put(cx-radius,y+supports['torch_height'],z-1,p['torch'])
    style.material_slots['ROOF_DARK']=r['roof_materials'];style.material_slots['GABLE_INFILL']=[p['planks']]
    roof_grid=BlockGrid()
    for shed in r['sheds']:
        if shed['id'] not in group.archetype_roster:raise ValueError('矿场棚架未在家族注册')
        x,y,z=shed['origin'];sx,sy,sz=shed['size']
        for xx in range(x-1,x+sx+1):
            for zz in range(z-1,z+sz+1):weights[zz*w+xx]=255;terrain[zz*w+xx]=y
        for xx in [x,x+sx-1]:
            for zz in [z,z+sz-1]:box((xx,y,zz),(xx+1,y+sy,zz+1),p['log'])
        for zz in [z,z+sz-1]:box((x,y+sy-1,zz),(x+sx,y+sy,zz+1),p['beam_x'])
        box((x+2,y,z+sz-2),(x+sx-2,y+1,z+sz-1),p['planks'])
        node=Node(shed['id'],'hall',tuple(shed['origin']),tuple(shed['size']),meta={'foundation_h':y,'wall_h':sy,'roof':{'ridge_axis':'z','overhang':1,'gable_overhang':1}})
        roof_handler(shed['roof'])(roof_grid,style,random.Random(r['surface_seed']),node,None)
    for pos,cell in roof_grid.cells.items():put(*pos,cell.state)
    logs=set();leaves=set()
    for i,(x,z) in enumerate(r['trees']['positions']):
        y=terrain[z*w+x];trunk=r['trees']['trunk_heights'][i%len(r['trees']['trunk_heights'])];radius=r['trees']['canopy_radius'];trees.append([x,y,z])
        for zz in range(max(0,z-radius),min(d,z+radius+1)):
            for xx in range(max(0,x-radius),min(w,x+radius+1)):weights[zz*w+xx]=255
        for yy in range(y+trunk-2,y+trunk+2):
            rad=radius if yy<y+trunk+1 else radius-1
            for xx in range(x-rad,x+rad+1):
                for zz in range(z-rad,z+rad+1):
                    if abs(xx-x)==rad and abs(zz-z)==rad and yy%2:continue
                    if get(xx,yy,zz)==p['air']:leaves.add((xx,yy,zz))
        for yy in range(y,y+trunk):logs.add((x,yy,z));put(x,yy,z,p['log'])
    distances={pos:0 for pos in logs};queue=deque(logs)
    cardinal=[(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)]
    while queue:
        x,y,z=queue.popleft();distance=distances[x,y,z]+1
        if distance>6:continue
        for dx,dy,dz in cardinal:
            pos=x+dx,y+dy,z+dz
            if pos in leaves and pos not in distances:distances[pos]=distance;queue.append(pos)
    for pos in leaves-logs:
        if pos not in distances:raise ValueError(('树叶没有连通的原木',pos))
        put(*pos,property_state(p['leaves'],distance=distances[pos]))
    for fixture in r['fixtures']:
        put(*fixture['pos'],fixture['state'])
        if 'nbt' in fixture:be[tuple(fixture['pos'])]=fixture['nbt']
    for sign in r['signs']:
        pos=tuple(sign['pos']);put(*pos,property_state('minecraft:oak_sign',rotation=sign['rotation'],waterlogged=False))
        be[pos]={'id':'minecraft:sign','front_text':{'messages':[json.dumps({'text':s},ensure_ascii=False) for s in sign['lines']],'color':'black','has_glowing_text':False},'is_waxed':True}
    cells={pos:get(*pos) for pos in changed}
    legal=modset.palette_block_errors(list(set(cells.values())))
    if legal:raise ValueError(legal)
    if any(not value.startswith('minecraft:') for value in cells.values()):raise ValueError('新手矿场只允许已注册原版材料')
    if any(pos[1]<base-1 for pos in cells):raise ValueError('地面矿场资源不得携带预制地下洞室或矿道')
    underground=r['underground']
    if underground['algorithm']!='minecraft_cave_carver_v1':raise ValueError('未知的矿场洞穴生成算法')
    checks={'trees':len(trees),'leaves_supported':len(leaves-logs),'authored_underground_blocks':0,
            'underground_algorithm':underground['algorithm'],'fixtures':len(r['fixtures']),'npcs':0,'block_count':len(cells)}
    return Model(r,cells,terrain,weights,be,trees,checks)


def export(out=OUT,source=SOURCE):
    model=build(source);r=model.rules;out=Path(out);root=out/'data/myvillage/starter_mine';root.mkdir(parents=True,exist_ok=True)
    palette=sorted(set(model.cells.values()));lookup={s:i for i,s in enumerate(palette)}
    cells=[[x,y,z,lookup[state]] for (x,y,z),state in sorted(model.cells.items(),key=lambda row:(row[0][2],row[0][0],row[0][1]))]
    body={'schema':1,'palette':palette,'cells':cells,'block_entities':[{'pos':list(pos),'nbt':nbt} for pos,nbt in sorted(model.bodies.items())]}
    voxel=gzip.compress(canonical(body),mtime=0);(root/'blocks.json.gz').write_bytes(voxel)
    terrain=b''.join(struct.pack('>hB',height,weight) for height,weight in zip(model.terrain,model.weights))
    definition={'schema':1,'id':r['id'],'display_name':r['display_name'],'size':r['size'],'ground_y':r['ground_y'],'entry':r['entry'],'road_port':r['road_port'],
                'source_sha256':digest(Path(source).read_bytes()),'blocks_resource':'myvillage:starter_mine/blocks.json.gz','blocks_sha256':digest(voxel),
                'terrain_gzip':base64.b64encode(gzip.compress(terrain,mtime=0)).decode(),'terrain':{k:r['terrain'][k] for k in ('soil_depth','clearance','foundation_depth')},
                'surface_materials':{k:r['palette'][k] for k in ('stone','dirt','grass','path')},'placement':r['placement'],
                'landmarks':r['landmarks'],'trees':model.trees,'underground':r['underground'],'regional_entries':r['regional_entries'],'checks':model.checks}
    raw=canonical(definition);(root/'layout.json').write_bytes(raw)
    return {'resource':'myvillage:starter_mine/layout.json','sha256':digest(raw)},model


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,default=OUT);args=parser.parse_args()
    reference,model=export(args.out);print(json.dumps({'reference':reference,'checks':model.checks},ensure_ascii=False))


if __name__=='__main__':main()
