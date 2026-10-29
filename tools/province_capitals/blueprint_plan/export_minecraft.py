#!/usr/bin/env python3
"""把灵岳蓝图编译成可在独立测试世界放置的原生结构数据包。"""
from __future__ import annotations

import argparse
from collections import deque
import copy
import hashlib
import json
from pathlib import Path
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'tools'))
from province_capitals.blueprint_plan.generate import DEFAULT,Planner,digest,read,rect,perimeter
from province_capitals.environment_v3.world import World,AIR
from province_capitals.environment_v3.export_game import construct
from province_capitals.environment_v4.export_game import replace
from province_capitals.regional.v2.export_game import write_tile
from province_capitals.regional.v2.common import props
from province_capitals.game.compile import resource_file,canonical,sha
from buildgen.nbtread import read_gzipped_nbt,state_string
from buildgen.modset import load_modset


def write(path,value):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')


def baseline(spec,root):
    manifest=read(root/'data/myvillage/capitals/v2/capital_lingyue/city.json')
    for name,expected in manifest['source']['hashes'].items():
        if sha((ROOT/name).read_bytes())!=expected:raise ValueError('原建筑输入变动，须先重新导出：'+name)
    for tile in manifest['tiles']:
        if sha(resource_file(root,tile['template'],template=True).read_bytes())!=tile['sha256']:
            raise ValueError('原建筑切片摘要不符：'+tile['template'])
    if sha(resource_file(root,manifest['terrain']['resource']).read_bytes())!=manifest['terrain']['sha256']:
        raise ValueError('原地形摘要不符')
    cfg=read(ROOT/'blueprints/province_capitals/environment_v3/materials.json')
    env=read(ROOT/spec['sources']['environment']);env=copy.deepcopy(env);env['gardens']=[]
    catalog=read(ROOT/spec['sources']['landmarks'])
    world=World(root,copy.deepcopy(manifest),cfg)
    for key in world.refs:
        for (x,y,z),state in list(world.tile(key).items()):
            if state in cfg['inherited_state_rewrites']:
                world.set((key[0]*16+x,y,key[1]*16+z),cfg['inherited_state_rewrites'][state],'已声明的继承状态修正')
    construct(world,env,catalog)
    world.manifest['environment']={'decorations':world.decorations}
    # 既有替换器检查本阶段写入范围；先保留环境施工证据，再独立记录家具阶段。
    environment_edits=dict(world.edit_source)
    world.edit_source.clear()
    replace(world,env,catalog,read(ROOT/'blueprints/street_furniture/replacement.json'),
            read(ROOT/'blueprints/street_furniture/catalog.json'),World(root,manifest,cfg))
    world.edit_source={**environment_edits,**world.edit_source}
    return world


def route(world,points,materials,tag,protected_roads):
    """上行、下行均在高端放朝上坡的楼梯；不把整格跳跃当阶梯。"""
    for x,y,z in points:
        if (x,z) in protected_roads:continue
        world.surface(x,y,z,materials['path'],tag)
    stairs={}
    for a,b in zip(points,points[1:]):
        if abs(a[0]-b[0])+abs(a[2]-b[2])!=1 or abs(a[1]-b[1])>1:
            raise ValueError('接路存在断点或超过一格高差：'+tag)
        if a[1]==b[1]:continue
        low,high=(a,b) if a[1]<b[1] else (b,a)
        dx,dz=high[0]-low[0],high[2]-low[2]
        facing='east' if dx>0 else 'west' if dx<0 else 'south' if dz>0 else 'north'
        state=props(materials['stairs'],facing=facing,half='bottom',shape='straight',waterlogged=False)
        p=(high[0],high[1]-1,high[2])
        if (p[0],p[2]) not in protected_roads:
            world.set(p,state,tag+'_台阶');stairs[p]=state
    for p in points:
        if not world.walkable(tuple(p)):raise ValueError('实际方块通路不满足支撑与净空：'+str((tag,p)))
    return stairs


def fixture(world,item,materials,rack_facing):
    x,y,z=item['position'];w,h,d=item['size'];kind=item['kind'];tag=item['id']
    if kind=='tree':
        world.box((x+w//2,y,z+d//2),(x+w//2,y+h-2,z+d//2),materials['log'],tag)
        world.box((x,y+h-3,z),(x+w-1,y+h-1,z+d-1),materials['leaves'],tag)
        return {(xx,yy,zz) for xx in range(x,x+w) for yy in range(y+h-3,y+h) for zz in range(z,z+d)}
    elif kind=='herb_beds':
        world.box((x,y-1,z),(x+w-1,y-1,z+d-1),materials['soil'],tag)
        world.box((x,y,z),(x+w-1,y,z+d-1),materials['crop'],tag)
    elif kind=='well':
        for xx,zz in rect([x,z,x+w-1,z+d-1]):
            world.set((xx,y,zz),materials['well'] if xx in (x,x+w-1) or zz in (z,z+d-1) else materials['well_water'],tag)
    elif kind=='drying_rack':
        if (w,h,d)!=(2,2,1) or rack_facing!='north':
            raise ValueError('晒架须精确匹配既有 2×2×1 north 部件布置')
        for part in range(w*h*d):
            dx=part%w;dz=(part//w)%d;dy=part//(w*d)
            world.set((x+dx,y+dy,z+dz),props(materials['drying_rack'],facing=rack_facing,part=part,waterlogged=False),tag)
    elif kind in ('bench','workbench'):
        world.box((x,y,z),(x+w-1,y,z+d-1),materials[kind],tag)
    else:raise ValueError('未实现的实际方块设施：'+kind)
    return set()


def leaf_distances(world,new_leaves,log_blocks):
    """叶距沿六邻接叶片传播；只改本次新叶片，保留 persistent 等其余属性。"""
    def neighbors(p):
        x,y,z=p
        return ((x-1,y,z),(x+1,y,z),(x,y-1,z),(x,y+1,z),(x,y,z-1),(x,y,z+1))
    leaves=set(new_leaves);queue=deque(sorted(leaves))
    while queue:
        for p in neighbors(queue.popleft()):
            if p not in leaves and world.get(p).split('[',1)[0].endswith('_leaves'):
                leaves.add(p);queue.append(p)
    distance={p:7 for p in leaves};queue=deque()
    for p in sorted(leaves):
        if any(world.get(n).split('[',1)[0] in log_blocks for n in neighbors(p)):
            distance[p]=1;queue.append(p)
    while queue:
        at=queue.popleft()
        for p in neighbors(at):
            if p in distance and distance[p]>distance[at]+1:
                distance[p]=distance[at]+1;queue.append(p)
    for p in sorted(new_leaves):
        state=world.get(p);values=dict(s.split('=',1) for s in state.split('[',1)[1][:-1].split(',')) if '[' in state else {}
        values['distance']=distance[p]
        world.set(p,props(state,**values),'新庭树叶距')
    return {p:distance[p] for p in new_leaves}


def build(spec,base):
    world=baseline(spec,base)
    planner=Planner(spec,read(ROOT/spec['sources']['city']),read(ROOT/spec['sources']['environment']),
                    read(ROOT/spec['sources']['landmarks']),block_world=world)
    if planner.original_terrain!=world.terrain:raise ValueError('蓝图与实际方块使用了不同的原地形')
    data=planner.compile()
    if data['checks']['issues']:raise ValueError('实际方块避障未通过：'+json.dumps(data['checks']['issues'],ensure_ascii=False))
    materials=spec['minecraft']['materials'];door_state=planner.model.material('DOOR')
    before_terrain=world.terrain[:];allowed=set();door_edits=set();stairs={};new_leaves=set()
    original_buildings={}
    for b in world.manifest['buildings']:
        lo,hi=b['bounds']['min'],b['bounds']['max']
        for z in range(lo[2],hi[2]+1):
            for x in range(lo[0],hi[0]+1):
                for y in range(b['position'][1],hi[1]+1):
                    state=world.get((x,y,z))
                    if state!=AIR:original_buildings[x,y,z]=state
    for e in data['earthwork']:
        x,z=e['column'];world.terrain[z*world.width+x]=e['air_y']-1;allowed.add((x,z))
    for g in data['gardens']:
        y=g['y'];gx,_,gz=g['gate'];bounds=g['bounds']
        for x,z in rect(bounds):world.set((x,y-1,z),materials['path'],g['id']+'_地坪')
        for x,z in perimeter(bounds):
            if abs(x-gx)+abs(z-gz)>1:world.set((x,y,z),materials['edge'],g['id']+'_院界')
        for item in g['fixtures']:new_leaves.update(fixture(world,item,materials,spec['minecraft']['rack_facing']))
        for kind,points in [('接路',g['path']),('院内',g['internal_path'])]:
            stairs.update(route(world,points,materials,g['id']+'_'+kind,planner.model.road_columns))
    for d in data['doors']:
        if d['action']!='open_door':continue
        x,y,z=d['position']
        for dy in range(d['headroom']):
            state=props(door_state,facing=d['facing'],half='lower' if dy==0 else 'upper',hinge='left',open=False,powered=False) if dy<2 else AIR
            world.set((x,y+dy,z),state,d['garden_id']+'_新门洞');door_edits.add((x,y+dy,z))
    leaf_distances(world,new_leaves,set(spec['minecraft']['leaf_log_blocks']))
    world.verify_life()
    for pos,old in original_buildings.items():
        if pos not in door_edits and world.get(pos)!=old:
            raise ValueError('蓝图施工改到了声明门洞以外的原房屋：'+str((pos,old,world.get(pos))))
    changed_terrain={(i%world.width,i//world.width) for i,(a,b) in enumerate(zip(before_terrain,world.terrain)) if a!=b}
    if not changed_terrain<=allowed:raise ValueError('整地超出蓝图范围')
    for p,state in stairs.items():
        if world.get(p)!=state:raise ValueError('后续施工覆盖了楼梯')
    for g in data['gardens']:
        for p in g['path']+g['internal_path']:
            if not world.walkable(tuple(p)):raise ValueError('最终通路不通：'+str((g['id'],p)))
    data['minecraft_checks']={'status':'pass','stairs':len(stairs),'new_door_cells':len(door_edits),
                              'changed_terrain_columns':len(changed_terrain),'preserved_building_cells':len(original_buildings)-len(door_edits),
                              'life_places':len(world.manifest['life']['places']),'runtime':'not_run'}
    data['stair_blocks']=[{'position':list(p),'state':s} for p,s in sorted(stairs.items())]
    return planner,world,data


def export(spec_path,base,out):
    out=Path(out)
    if out.exists():raise ValueError('输出目录已存在；每次实际方块试验使用独立目录保留失败证据')
    spec=read(spec_path);planner,world,data=build(spec,base)
    out.mkdir(parents=True)
    pack=out/'datapack';write(pack/'pack.mcmeta',{'pack':{'pack_format':spec['minecraft']['pack_format'],'description':'灵岳蓝图原生结构试验'}})
    origin=spec['minecraft']['origin'];tiles=[];palette=set();block_count=0
    for tz in range(world.depth//16):
        for tx in range(world.width//16):
            cells={}
            for z in range(16):
                for x in range(16):
                    top=world.ground(tx*16+x,tz*16+z)
                    for y in range(top+1):cells[x,y,z]=(world.manifest['terrain']['top_block'] if y==top else world.manifest['terrain']['fill_block'],'terrain','terrain')
            cells.update({p:(s,'','') for p,s in world.tile((tx,tz)).items()})
            path=pack/f'data/myvillage/structure/blueprint_trial/lingyue/tile_{tx}_{tz}.nbt';path.parent.mkdir(parents=True,exist_ok=True)
            count,_=write_tile(path,cells,world.height);block_count+=count
            _,raw=read_gzipped_nbt(str(path));states=[state_string(s) for s in raw['palette']]
            decoded={tuple(b['pos']):states[b['state']] for b in raw['blocks']}
            if decoded!={p:s[0] for p,s in cells.items()}:raise ValueError('NBT 回读不同于施工结果：'+str(path))
            palette.update(states)
            tiles.append({'x':tx*16,'z':tz*16,'template':f'myvillage:blueprint_trial/lingyue/tile_{tx}_{tz}',
                          'bytes':path.stat().st_size,'sha256':sha(path.read_bytes()),'blocks':count})
    errors=load_modset('vanilla').palette_block_errors(sorted(palette))
    if errors:raise ValueError('; '.join(errors))
    functions=pack/'data/myvillage/function/blueprint_trial/lingyue';functions.mkdir(parents=True)
    batches=[]
    for start in range(0,world.depth//16,spec['minecraft']['batch_rows']):
        rows=[t for t in tiles if start*16<=t['z']<(start+spec['minecraft']['batch_rows'])*16]
        name=f'batch_{start:02}'
        commands=[f"place template {t['template']} {origin[0]+t['x']} {origin[1]} {origin[2]+t['z']}" for t in rows]
        (functions/(name+'.mcfunction')).write_text('\n'.join(commands)+'\n')
        batches.append({'function':'myvillage:blueprint_trial/lingyue/'+name,'tiles':len(rows),
                        'bounds':[origin[0],origin[2]+start*16,origin[0]+world.width-1,origin[2]+max(t['z'] for t in rows)+15]})
    checks={}
    for g in data['gardens']:
        for x,y,z in g['path']+g['internal_path']:
            for dy in (-1,0,1):checks[x,y+dy,z]=world.get((x,y+dy,z))
    for d in data['doors']:
        x,y,z=d['position']
        for dy in range(3):checks[x,y+dy,z]=world.get((x,y+dy,z))
    for item in data['fixtures']:
        x,y,z=item['position'];w,h,d=item['size']
        for xx in range(x,x+w):
            for yy in range(y-1,y+h):
                for zz in range(z,z+d):checks[xx,yy,zz]=world.get((xx,yy,zz))
    lines=['scoreboard objectives add bp_audit dummy','scoreboard players set matched bp_audit 0']
    for (x,y,z),state in sorted(checks.items()):
        lines.append(f'execute if block {x+origin[0]} {y+origin[1]} {z+origin[2]} {state} run scoreboard players add matched bp_audit 1')
    (functions/'audit.mcfunction').write_text('\n'.join(lines)+'\n')
    files=[spec_path,ROOT/'tools/province_capitals/blueprint_plan/generate.py',Path(__file__),
           ROOT/'blueprints/province_capitals/environment_v3/materials.json',ROOT/'blueprints/street_furniture/replacement.json',
           ROOT/'blueprints/street_furniture/catalog.json']+[ROOT/v for v in spec['sources'].values()]
    files += [ROOT/name for name in read(base/'data/myvillage/capitals/v2/capital_lingyue/city.json')['source']['hashes']]
    for folder in ('environment_v3','environment_v4','environment_design'):
        files += list((ROOT/'tools/province_capitals'/folder).glob('*.py'))
    source_hashes={str(p.relative_to(ROOT)):sha(p.read_bytes()) for p in files}
    data['source_hashes']=source_hashes;data['source_fingerprint']=digest(source_hashes)
    write(out/'plan.json',data)
    inventory={'schema':1,'scope':spec['minecraft']['scope'],'origin':origin,'dimensions':data['dimensions'],
               'source_fingerprint':data['source_fingerprint'],'tiles':tiles,'batches':batches,'palette_states':sorted(palette),
               'blocks':block_count,'expected_audit_matches':len(checks),'checks':data['minecraft_checks'],'runtime':'not_run'}
    write(out/'inventory.json',inventory)
    archive=out/'lingyue-blueprint-trial.zip'
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for p in sorted(pack.rglob('*')):
            if p.is_file():z.write(p,p.relative_to(pack))
    write(out/'artifact.json',{'file':archive.name,'bytes':archive.stat().st_size,'sha256':sha(archive.read_bytes()),'source_fingerprint':data['source_fingerprint']})
    print(json.dumps({'output':str(out),'tiles':len(tiles),'blocks':block_count,'checks':inventory['checks'],'audit_samples':len(checks)},ensure_ascii=False),flush=True)
    return inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--input',type=Path,default=DEFAULT)
    p.add_argument('--base-v2',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    a=p.parse_args();export(a.input.resolve(),a.base_v2.resolve(),a.out.resolve())


if __name__=='__main__':main()
