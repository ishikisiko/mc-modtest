#!/usr/bin/env python3
"""已确认宗门蓝图的正式资源：与预览同源分块，设施和站位随清单打包。"""
from __future__ import annotations
import argparse
from collections import deque
import gzip
import json
from pathlib import Path
import struct
import sys
import shutil

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from sect_blueprints.generate import (AIR,SectModel,digest,final_tiles,source_hashes,inside_polygon,route_cells,passable)
from buildgen.modset import load_modset
from buildgen.nbtread import read_gzipped_nbt,state_string
from province_capitals.blueprint_plan.nbt_entities import write_tile
from province_capitals.regional.v2.common import write_json
from sect_blueprints.walks import can_step
from province_capitals.blueprint_plan.atomic_blocks import parse


def runtime_hashes(spec_path,model):
    return {p:h for p,h in source_hashes(spec_path,model).items()
            if not p.endswith(('.html','.js')) and '/test_' not in p
            and p!='tools/province_capitals/regional/v2/preview.py'
            and (not p.startswith('tools/sect_blueprints/') or p in ('tools/sect_blueprints/runtime.py','tools/sect_blueprints/generate.py','tools/sect_blueprints/walks.py','tools/sect_blueprints/architecture.py','tools/sect_blueprints/terrain_forms.py'))}


def metadata(model):
    cfg=model.spec['runtime']
    valid={(x,y,z) for (x,z),(y,*_) in model.surface_columns.items() if model.walkable((x,y,z))}
    for area in model.spec['terrain']['shelves']:
        x0,z0,x1,z1=area['bounds'];y=area['walk_y']
        valid.update((x,y,z) for z in range(z0,z1) for x in range(x0,x1)
                     if inside_polygon(x+.5,z+.5,area['outline']) and model.walkable((x,y,z)))
    for connection in model.spec['entry_connections']:
        for x,y,z in route_cells(connection['points']):
            # 原版门洞允许1.8格玩家通过；室外道路仍保持三格净空断言。
            if model.get((x,y-1,z))==AIR or not all(passable(model.get((x,y+dy,z))) for dy in (0,1)):
                raise ValueError('门槛实体或两格门洞不可达：'+str((x,y,z)))
            valid.add((x,y,z))
    candidates=set()
    for area in cfg['avatar_areas']:
        x0,z0,x1,z1=area['bounds'];y=area['walk_y']
        cells={(x,y,z) for z in range(z0,z1) for x in range(x0,x1) if model.walkable((x,y,z)) and (x,z) not in model.surface_columns}
        if len(cells)<area['min_cells']:raise ValueError('活动区域可站立格不足：'+str(area))
        candidates.update(cells);valid.update(cells)
    for shelf in cfg['shelves']:
        x,y,z=shelf['position']
        if not model.walkable((x,y,z)) or not model.walkable(tuple(shelf['reader'])):
            raise ValueError('经架或读者位置没有真实地面/净空：'+str(shelf))
        building=next(b for b in model.buildings if b['id']==shelf['building'])
        ox,_,oz=building['origin'];w,_,d=building['size']
        valid.update((xx,y,zz) for xx in range(ox,ox+w) for zz in range(oz,oz+d) if model.walkable((xx,y,zz)))
    shop=cfg.get('linlang')
    if shop:
        if not model.walkable(tuple(shop['position'])) or not model.walkable(tuple(shop['reader'])):
            raise ValueError('琳琅阁柜台或操作位置缺少地面与净空')
    floors=[]
    for building in model.buildings:
        ox,oy,oz=building['origin']
        for p in building.get('passages',[]):
            x,y,z=p
            # 注册 pass 明确生成的室内 1x2 门洞，同室外三格净空分开核对。
            if model.get((x,y-1,z))==AIR or not all(passable(model.get((x,y+dy,z))) for dy in (0,1)):
                raise ValueError('室内门洞缺少承托或两格净空：'+str(p))
            valid.add(tuple(p))
        for volume in building.get('massing',[]):
            x0,_,z0=volume['origin'];w,_,d=volume['size'];meta=volume['meta'];fh=meta['foundation_h']
            for y in range(fh,fh+meta['wall_h']+1):
                valid.update((ox+x,oy+y,oz+z) for z in range(z0-1,z0+d+1) for x in range(x0-1,x0+w+1)
                             if model.walkable((ox+x,oy+y,oz+z)))
            for story in range(meta['stories']):
                inset=meta.get('story_insets',[0]*meta['stories'])[story]
                y=oy+fh+story*meta['story_wall_h']+(1 if story else 0)
                floors.append((building['id'],story,{(ox+x,y,oz+z) for z in range(z0+1+inset,z0+d-1-inset)
                              for x in range(x0+1+inset,x0+w-1-inset)}))
    if shop:valid.discard(tuple(shop['position']))
    start=tuple(cfg['entry_anchor']);reachable={start} if start in valid else set();queue=deque(reachable)
    while queue:
        x,y,z=queue.popleft()
        for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
            for dy in (-1,0,1):
                p=(x+dx,y+dy,z+dz)
                if p in valid and p not in reachable and can_step(model,(x,y,z),p):reachable.add(p);queue.append(p)
    required={tuple(cfg['steward']),*(tuple(s['reader']) for s in cfg['shelves']),*candidates}
    if shop:required.add(tuple(shop['reader']))
    if required-reachable:raise ValueError('设施或活动位置与山门断开：'+str(sorted(required-reachable)[:16]))
    if tuple(cfg['steward']) not in candidates:raise ValueError('执事须站在有界活动区内')
    for owner,story,cells in floors:
        if not cells&reachable:raise ValueError(f'建筑楼层无免跳跃通路：{owner} 第{story+1}层')
    if model.spec.get('architecture_profile'):
        beds=set()
        for p,(owner,_) in model.protected.items():
            state=model.get(p);name,props=parse(state);operate=[p]
            if name.endswith('_bed'):beds.add(owner)
            if name.endswith('_bed'):
                # 一张床是一个设施，头尾任一侧有操作位即可；完整头尾另由 audit 核对。
                if props.get('part')!='foot':continue
                dx,dz={'north':(0,-1),'south':(0,1),'east':(1,0),'west':(-1,0)}[props['facing']]
                operate.append((p[0]+dx,p[1],p[2]+dz))
            if name=='minecraft:bell':operate.extend((p[0],p[1]-dy,p[2]) for dy in (1,2))
            if name.endswith('_bed') or name in ('minecraft:furnace','minecraft:smithing_table','minecraft:crafting_table','minecraft:lectern','minecraft:bell'):
                if not any((q[0]+dx,q[1],q[2]+dz) in reachable for q in operate for dx,dz in ((1,0),(-1,0),(0,1),(0,-1))):
                    raise ValueError('室内设施缺少可达操作位置：'+str((owner,p,state)))
        for building in model.buildings:
            if building['archetype']=='disciple_quarters' and building['id'] not in beds:
                raise ValueError('弟子居缺少完整床铺：'+building['id'])
    return {'entry_anchor':cfg['entry_anchor'],'steward':cfg['steward'],
            'courtyard_cells':[list(p) for p in sorted(candidates,key=lambda p:(p[1],p[2],p[0]))],
            'shelves':cfg['shelves'],**({'linlang':shop} if shop else {})}


def export(spec_path,out):
    spec_path=Path(spec_path).resolve();out=Path(out).resolve();spec=json.loads(spec_path.read_text())
    if spec['status']!='approved':raise ValueError('正式导出仅接受已确认的设计')
    model=SectModel(spec).construct();hashes=runtime_hashes(spec_path,model)
    checks=model.audit()
    if checks['status']!='pass':raise ValueError('宗门几何失败：'+json.dumps(checks,ensure_ascii=False))
    anchors=metadata(model);key=spec['id'].split(':',1)[1]
    folder=out/'data/myvillage/sect_blueprints'/key
    tiles=[];palette=set(model.surface_states);max_y=max(model.terrain)
    for tx,tz,cells,entities,_ in final_tiles(model,terrain=False):
        if not cells:continue
        path=out/'data/myvillage/structure/sect_blueprints'/key/f'tile_{tx}_{tz}.nbt'
        count,_,_=write_tile(path,cells,model.height,entities)
        _,root=read_gzipped_nbt(path);states=[state_string(s) for s in root['palette']]
        actual={tuple(row['pos']):states[row['state']] for row in root['blocks']}
        if actual!={p:v[0] for p,v in cells.items()}:raise ValueError('正式分块回读不一致：'+str(path))
        palette.update(states);max_y=max(max_y,max(p[1] for p,state in actual.items() if state!=AIR))
        tiles.append({'x':tx*16,'z':tz*16,'template':f'myvillage:sect_blueprints/{key}/{path.stem}',
                      'sha256':digest(path),'blocks':count})
    errors=load_modset('full').palette_block_errors(sorted(palette))
    if errors:raise ValueError('; '.join(errors))
    folder.mkdir(parents=True,exist_ok=True)
    terrain=folder/'terrain.bin.gz'
    surfaces=sorted(set(model.surface_states))
    if len(surfaces)>256:raise ValueError('宗门地表材质过多')
    indices={state:i for i,state in enumerate(surfaces)}
    field=b''.join(struct.pack('<hBB',h,weight,indices[state]) for h,weight,state in zip(model.terrain,model.influence,model.surface_states))
    terrain.write_bytes(gzip.compress(field,mtime=0))
    manifest={'schema':1,'id':spec['id'],'name':spec['name'],'size':spec['dimensions'],'max_y':max_y,
              'blend_margin':spec['runtime']['blend_margin'],'vegetation_clearance':spec['runtime']['vegetation_clearance'],
              'rock':model.materials['rock'],'surface':model.materials['surface'],
              'archetype':spec['archetype'],'scale':spec['scale'],'road_port':spec['runtime']['road_port'],
              'surfaces':surfaces,'subsoil':'minecraft:dirt','topsoil_depth':spec['landscape']['topsoil_depth'],
              'regional_surfaces':spec['landscape']['regional_surfaces'],
              'terrain':{'resource':f'myvillage:sect_blueprints/{key}/terrain.bin.gz','sha256':digest(terrain)},
              'worldgen':json.loads((ROOT/'blueprints/sects/catalog.json').read_text())['worldgen'],'tiles':tiles,**anchors,'source_hashes':hashes,
              'routes':[{k:v for k,v in route.items() if k!='columns'} for route in model.routes],
              'entry_connections':spec['entry_connections'],
              'component_repairs':[{'position':list(p),**row} for p,row in sorted(model.component_repairs.items())],
              'buildings':model.buildings}
    write_json(folder/'sect.json',manifest)
    if runtime_hashes(spec_path,model)!=hashes:raise ValueError('生成期间来源改变')
    print(json.dumps({'id':spec['id'],'tiles':len(tiles),'courtyard_cells':len(anchors['courtyard_cells']),
                      'shelves':len(anchors['shelves']),'checks':'pass'},ensure_ascii=False))
    return manifest


def export_catalog(out):
    source=ROOT/'blueprints/sects/catalog.json';catalog=json.loads(source.read_text());out=Path(out)
    for relative in ('data/myvillage/sect_blueprints','data/myvillage/structure/sect_blueprints'):
        shutil.rmtree(out/relative,ignore_errors=True)
    refs=[]
    for row in catalog['layouts']:
        path=source.parent/row['source'];spec=export(path,out);key=spec['id'].split(':')[1]
        if any(spec[k]!=row[k] for k in ('id','archetype','scale')):raise ValueError('宗门目录与输入不符')
        refs.append({'id':spec['id'],'resource':f'myvillage:sect_blueprints/{key}/sect.json',
                     'sha256':digest(out/f'data/myvillage/sect_blueprints/{key}/sect.json')})
    if len({r['id'] for r in refs})!=len(refs) or catalog['default'] not in {r['id'] for r in refs}:raise ValueError('宗门目录身份无效')
    write_json(out/'data/myvillage/sect_blueprints/catalog.json',{'schema':1,'default':catalog['default'],'layouts':refs})


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--input',type=Path)
    p.add_argument('--out',type=Path,default=ROOT/'build/generated/sect_blueprints/resources')
    a=p.parse_args()
    if a.input:export(a.input,a.out)
    else:export_catalog(a.out)
