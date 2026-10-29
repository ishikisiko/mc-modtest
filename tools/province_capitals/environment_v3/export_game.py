#!/usr/bin/env python3
"""八州环境 v3 正常导出器。以 owning v1/v2 方块资源为只读底座。"""
from __future__ import annotations

import argparse
import copy
import gzip
import json
import math
from pathlib import Path
import shutil
import sys
import time

ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'tools'))
from buildgen.groups import get_group
from buildgen.modset import load_modset
from province_capitals.game.compile import canonical,sha,identity,life_inputs,resource_file,write
from province_capitals.environment_design.geometry import Scene,shape
from province_capitals.environment_design.generate import polyline
from province_capitals.regional.v2.export_game import write_tile
from province_capitals.environment_v3.world import World,raster,AIR

INPUT=ROOT/'blueprints/province_capitals/environment_v3'
DESIGN=ROOT/'blueprints/province_capitals/environment_design/catalog.json'
REGIONS=('lingyue','donghai','ximo','beiyuan','nanjiang','donghuang','moyu','zhongzhou')


def read(path):return json.loads(Path(path).read_text())


def recipe(world,catalog,name,origin,scale,tag,q=0):
    scene=Scene(catalog['materials']);scene.recipe(name,catalog['recipes'],origin,scale,tag=tag,q=q)
    raster(world,scene.boxes,world.config['materials'],tag)
    world.decorations.append({'id':tag,'recipe':name,'origin':origin,'scale':scale,'rotation_quarters':q})


def native_garden(world,catalog,garden):
    x0,z0,x1,z1=garden['bounds'];y=garden['y'];tag=garden['id'];kind=garden['kind']
    cfg=world.config['garden'];gate=garden['gate'];cx,cz=(x0+x1)//2,(z0+z1)//2
    world.box((x0,y-1,z0),(x1,y-1,z1),cfg['floor'],tag)
    # 低院墙用原版墙方块，与两格院门相连。明确禁止把细围墙取整成两格高实心墙。
    for x in range(x0,x1+1):
        for z in [z0,z1]:
            if abs(x-gate[0])<=1 and abs(z-gate[1])<=1:continue
            world.set((x,y,z),cfg['wall']+'[east=low,north=none,south=none,up=false,waterlogged=false,west=low]',tag)
    for z in range(z0+1,z1):
        for x in [x0,x1]:
            if abs(x-gate[0])<=1 and abs(z-gate[1])<=1:continue
            world.set((x,y,z),cfg['wall']+'[east=none,north=low,south=low,up=false,waterlogged=false,west=none]',tag)
    if kind=='herb':
        for x in range(x0+cfg['bed_margin'],x1-1,cfg['crop_spacing']*2):
            for z in range(z0+cfg['bed_margin'],z1-1,cfg['crop_spacing']):
                world.set((x,y-1,z),cfg['soil'],tag)
                world.set((x,y,z),cfg['crop'],tag)
    elif kind in ('pine','flower'):
        recipe(world,catalog,'pine' if kind=='pine' else 'blossom_tree',garden.get('tree_position',[x0+3,y,z0+3]),.8,tag+'_庭树',garden.get('tree_rotation',0))
        if x1-x0>12:recipe(world,catalog,'stone_boulder',[x1-3,y,z1-3],.65,tag+'_庭石')
        world.set((cx,y,z1-2),'minecraft:spruce_slab[type=bottom,waterlogged=false]',tag+'_坐凳')
        for z in range(z0+2,z1-1,2):world.set((x1-1,y,z),'minecraft:flowering_azalea',tag+'_花丛')
    elif kind=='well':
        recipe(world,catalog,'well',[cx,y,cz],.65,tag+'_井台')
        recipe(world,catalog,'shade_tree',garden.get('tree_position',[x0+3,y,z0+3]),.75,tag+'_庭树',garden.get('tree_rotation',0))
        world.set((x1-3,y,z1-2),'minecraft:spruce_slab[type=bottom,waterlogged=false]',tag+'_坐凳')
    elif kind=='work':
        for x in [x0+2,x0+4]:world.set((x,y,z0+2),'minecraft:barrel[facing=up,open=false]',tag+'_货箱')
        recipe(world,catalog,'drying_rack',[cx,y,cz],.55,tag+'_晒架')
        world.set((cx,y,z1-2),'minecraft:crafting_table',tag+'_作台')
    else:raise ValueError('未消费的庭院类型：'+kind)
    if not garden['path'] or garden['path'][0]!=garden['gate']:raise ValueError('院门与接路不一致')
    path_y=garden.get('path_y',[y]*len(garden['path']))
    if len(path_y)!=len(garden['path']):raise ValueError('院门路径标高数量错误')
    points=[[x,yy,z] for (x,z),yy in zip(garden['path'],path_y)]
    if len(points)==1:points.append(points[0])
    world.corridor(points,1,cfg['floor'],tag+'_院门接路')
    world.decorations.append({'id':tag,'kind':'garden','garden_kind':kind,'bounds':garden['bounds'],'gate':gate})


def remove_buildings(world,plan):
    m=world.manifest;removed=set(plan.get('remove_buildings',[]))
    buildings=m.get('buildings',[])
    if removed-set(b['id'] for b in buildings):raise ValueError('声明移除的建筑不存在')
    for b in buildings:
        if b['id'] not in removed:continue
        lo,hi=b['bounds']['min'],b['bounds']['max']
        world.box((lo[0],b['position'][1],lo[2]),tuple(hi),AIR,'移除旧体量:'+b['id'],authority=True)
    def keep(p):return not any(p['id']==i or p['id'].startswith(i+'_') for i in removed)
    m['life']['places']=[p for p in m['life']['places'] if keep(p)]
    m['life']['fixtures']=[p for p in m['life'].get('fixtures',[]) if keep(p)]
    m['buildings']=[b for b in buildings if b['id'] not in removed]
    m['access_paths']=[a for a in m.get('access_paths',[]) if a.get('building_id') not in removed]


def fortifications(world,plan):
    """闭环墙顶按实际地形加高过渡；门洞下方保持开口，墙顶连续。"""
    cfg=world.config['fortification'];wall=plan['wall'];route=polyline(wall['points'])
    if route[0]!=route[-1]:raise ValueError('城防边界未闭合')
    route=route[:-1];width=wall.get('width',cfg['width']);heights=[];gates={}
    for i,(x,z) in enumerate(route):
        h=wall.get('sea_height',wall.get('height',cfg['height'])) if x>=wall.get('sea_x',99999) else wall.get('height',cfg['height'])
        top=world.ground(x,z)+1+h
        for gate in wall.get('gates',[]):
            x0,z0,x1,z1=gate['bounds']
            if x0<=x<=x1 and z0<=z<=z1:
                gates[i]=gate
                if gate['type']=='water':top=max(top,gate['y']+15)
                else:
                    bodies=[b for b in world.manifest.get('buildings',[]) if b.get('role')=='gate' and abs(b['position'][0]-x)<30 and abs(b['position'][2]-z)<30]
                    top=max([top]+[b['bounds']['max'][1]+2 for b in bodies])
        heights.append(top)
    # 周期序列的最小1格 Lipschitz 上包络。只加高墙体，不压低地形或容忍不可走高差。
    def continuous(values):
        smooth=values*3
        for i in range(1,len(smooth)):smooth[i]=max(smooth[i],smooth[i-1]-1)
        for i in range(len(smooth)-2,-1,-1):smooth[i]=max(smooth[i],smooth[i+1]-1)
        return smooth[len(route):2*len(route)]
    heights=continuous(heights)
    # 登城阶的接墙处有与其宽度相等的平落地段，再向两侧以一格高差衔接。
    for access in wall.get('accesses',[]):
        target=tuple(access['wall_target'])
        if target not in route:raise ValueError('登城落地段不在城墙上')
        i=route.index(target);radius=access.get('width',3)//2
        indices=[(i+j)%len(route) for j in range(-radius,radius+1)]
        top=max(heights[j] for j in indices)
        for j in indices:heights[j]=top
    heights=continuous(heights)
    material=world.config['materials'][wall['material']]
    for i,((x,z),top) in enumerate(zip(route,heights)):
        nx,nz=route[(i+1)%len(route)];along_x=nx!=x
        for offset in range(-(width//2),width-width//2):
            xx,zz=(x,z+offset) if along_x else (x+offset,z)
            bottom=top-1 if i in gates else world.ground(xx,zz)+1
            if bottom<=top-1:world.box((xx,bottom,zz),(xx,top-1,zz),material,'城防主体')
            if offset in (-(width//2),width-width//2-1):
                high=top+(1 if i%cfg['parapet_step']<2 else 0)
                world.box((xx,top,zz),(xx,high,zz),material,'城防垛口')
    # 将转角与高差交汇也施工成同一条实际通道，而非只验证抽象折线。
    walk={}
    for (x,z),top in zip(route,heights):
        world.set((x,top-1,z),material,'墙顶步道')
        for dy in range(3):world.set((x,top+dy,z),AIR,'墙顶步道净空')
        walk[x,z]=top
    for i,((x,z),top) in enumerate(zip(route,heights)):
        if not world.walkable((x,top,z)) or abs(top-heights[(i+1)%len(route)])>1:
            raise ValueError('真实墙顶步道不连续')
        for dy in range(2):world.protected[x,top+dy,z]='墙顶连续通道'
    for access in wall.get('accesses',[]):
        target=tuple(access['wall_target'])
        if target not in walk:raise ValueError('登城路线没有抵达墙顶')
        points=[[x,walk[target] if y is None else y,z] for x,y,z in access['points']]
        world.corridor(points,access.get('width',3),material,access['id'])
        if tuple(points[-1])!=(target[0],walk[target],target[1]):raise ValueError('登城终点错误')
    world.manifest['fortification']={'closed':True,'walk_cells':len(walk),'max_step':max(abs(heights[i]-heights[(i+1)%len(route)]) for i in range(len(route))),
                                      'gates':wall.get('gates',[]),'accesses':wall.get('accesses',[]),'walk_route':[[x,y,z] for (x,z),y in walk.items()]}


def construct(world,plan,catalog):
    remove_buildings(world,plan)
    m=world.manifest
    if plan.get('clear_road'):
        c=plan['clear_road']
        m['roads']=[r for r in m.get('roads',[]) if r['id']!=c['id']]+plan.get('retained_roads',[])
    if plan.get('remove_roads'):
        m['roads']=[r for r in m.get('roads',[]) if r['id'] not in plan['remove_roads']]
    anchor=plan['cultivation_anchor']
    m['life']['places'].append({'id':'capital_landmark_cultivation','kind':'cultivate','pos':anchor,'capacity':4,'block':'','block_pos':[0,0,0]})
    world.protect_life()
    world.protect_routes(m.get('roads',[]))
    world.protect_routes([{'id':'door:'+a['building_id'],'points':a['points']} for a in m.get('access_paths',[])])
    # 只移除旧边界墙列，不修改旧资源文件。新城防自己负责门洞与可达性。
    for old in plan.get('remove_wall_lines',[]):
        for x,z in polyline(old):
            gy=world.ground(x,z)+1
            for yy in range(gy,gy+3):
                if (x,yy,z) not in world.protected:world.set((x,yy,z),AIR,'移除旧低墙')
    if not plan.get('inherit_wall'):
        fortifications(world,plan)
    spec=next(s for s in catalog['landmarks'] if s['region']==plan['region'])
    origin=plan['landmark_origin'];geom=shape(catalog['recipes'][spec['recipe']])
    lo=[math.floor(min(b['p'][i]-b['s'][i]/2 for b in geom)+origin[i]) for i in range(3)]
    hi=[math.ceil(max(b['p'][i]+b['s'][i]/2 for b in geom)+origin[i])-1 for i in range(3)]
    if plan.get('clear_landmark_volume'):
        world.box(tuple(lo),tuple(hi),AIR,'核心预留空间',authority=True)
    # 承台逐列接到原有实心地表；保留外侧自然高度场。
    for x in range(lo[0],hi[0]+1):
        for z in range(lo[2],hi[2]+1):
            if plan.get('foundation_mode')=='piers' and not any(abs(x-origin[0]-dx)<=2 and abs(z-origin[2]-dz)<=2 for dx,dz in plan['foundation_supports']):continue
            if world.ground(x,z)<origin[1]-1:
                world.box((x,world.ground(x,z)+1,z),(x,origin[1]-1,z),world.config['materials']['slate'],'核心地基')
    recipe(world,catalog,spec['recipe'],origin,1,spec['name'])
    for garden in plan.get('gardens',[]):native_garden(world,catalog,garden)
    for obj in plan.get('public_objects',[])+plan.get('frontages',[]):
        if obj['recipe']=='jar':world.set(tuple(obj['position']),world.config['small_props']['jar'],obj['name'])
        else:recipe(world,catalog,obj['recipe'],obj['position'],obj['scale'],obj['name'],obj.get('q',0))
    for route in plan.get('game_paths',[]):
        world.corridor(route['points'],route.get('width',world.config['collision']['path_width']),world.config['materials']['path'],route['id'])
        m.setdefault('roads',[]).append(route)
    world.surface(*anchor,world.config['materials']['path'],'核心修炼位置',1)
    for fixture in plan.get('functional_fixtures',[]):
        world.surface(*fixture['position'],world.config['materials']['path'],fixture['id']+'_基座',fixture.get('pad_width',1))
        world.set(tuple(fixture['position']),fixture['state'],fixture['id'])
        world.decorations.append({'id':fixture['id'],'kind':'existing_function','state':fixture['state'],'position':fixture['position']})
    world.verify_life()
    world.verify_retained_buildings()
    # 同一入口到明确核心/庭院路线的逐段证据由 game_paths 和原生采集补齐。
    m['landmarks']=[{'id':spec['recipe'],'name':spec['name'],'kind':spec['kind'],'bounds':{'min':lo,'max':hi},'position':origin,'cultivation_anchor':anchor,'function_scope':world.config['function_scope']}]


def export(region,out,old,v9):
    started=time.monotonic();plan=read(INPUT/'cities'/f'{region}.json');config=read(INPUT/'materials.json');catalog=read(DESIGN)
    source=old if plan['base_version']=='v1' else v9
    resource=f'myvillage:capitals/{plan["base_version"]}/capital_{region}/city.json'
    previous=read(resource_file(source,resource));m=copy.deepcopy(previous)
    world=World(source,m,config)
    construct(world,plan,catalog)
    group=get_group('province_capital_'+region+'_v3')
    m.update(display_name=plan['name'],layout_family_id=group.scale_params['layout_family_id'],group_id=group.group_id,style_id=group.style_id,appearance_id='capital_'+region+'_environment_v3')
    m['life']['layout_id']='capital_'+region+'_blocks_v3'
    if 'details' in m:m['inherited_details']=m.pop('details')
    data=out/f'data/myvillage/capitals/v3/capital_{region}';data.mkdir(parents=True,exist_ok=True)
    terrain=resource_file(source,previous['terrain']['resource']);shutil.copyfile(terrain,data/'terrain.bin.gz')
    m['terrain']['resource']=f'myvillage:capitals/v3/capital_{region}/terrain.bin.gz'
    tiles=[];palette=set(m.get('palette_states',[]))
    for tx,tz in sorted(set(world.refs)|world.dirty):
        path=out/f'data/myvillage/structure/province_capitals/v3/capital_{region}/tile_{tx}_{tz}.nbt';path.parent.mkdir(parents=True,exist_ok=True)
        if (tx,tz) in world.dirty:
            if (tx,tz) in world.nbt_tiles:raise ValueError('修改的分块含原有实体 NBT，拒绝丢弃：'+str((tx,tz)))
            cells=world.tile((tx,tz));palette.update(cells.values())
            count,palette_count=write_tile(path,{p:(s,'','') for p,s in cells.items()},world.height)
            record={'block_count':count,'palette_count':palette_count}
        else:
            old_tile=world.refs[tx,tz];shutil.copyfile(resource_file(source,old_tile['template'],template=True),path)
            record={k:v for k,v in old_tile.items() if k not in {'x','z','template','sha256','bytes'}}
        tiles.append({**record,'x':tx*16,'z':tz*16,'template':f'myvillage:province_capitals/v3/capital_{region}/'+path.stem,'sha256':sha(path.read_bytes()),'bytes':path.stat().st_size})
    errors=load_modset('vanilla').palette_block_errors(sorted(palette))
    if errors:raise ValueError('; '.join(errors))
    m['tiles']=tiles;m['palette_states']=sorted(palette)
    life_inputs(m,world.width,world.height,world.depth,192)
    inputs=[DESIGN,INPUT/'materials.json',INPUT/'cities'/f'{region}.json',Path(__file__),Path(__file__).with_name('world.py'),ROOT/'tools/province_capitals/environment_design/geometry.py',ROOT/'tools/province_capitals/environment_design/generate.py']
    m['source']={'authored':[str(p.relative_to(ROOT)) for p in inputs[:3]],'generator':str(Path(__file__).relative_to(ROOT)),
                 'hashes':{str(p.relative_to(ROOT)):sha(p.read_bytes()) for p in inputs},'base_resource':resource,'base_manifest_sha256':sha(canonical(identity(previous))),
                 'modset':{'profile':'vanilla','resolver':'tools/buildgen/modset.py'},'quantization':'原模板原样继承；新大件按整格体素施工，小物件使用方块外包络与明确原版/自有设施；薄铺地落回地表。'}
    m['environment']={'schema':1,'decorations':world.decorations,'changed_tiles':len(world.dirty),'inherited_tiles':len(world.refs.keys()-world.dirty),'changed_cells_by_element':dict(world.changes),'removed_buildings':plan.get('remove_buildings',[]),'garden_count':len(plan.get('gardens',[])),'function_scope':config['function_scope']}
    m['status']={'generation':'resource_export_executed','life_support_and_clearance':'passed','native':'not_run','owner_appearance':'not_verified','fps':'not_verified','multiplayer':'not_verified'}
    write(data/'city.json',m)
    print(f'{region}: v3 tiles={len(tiles)} changed={len(world.dirty)} life={len(m["life"]["places"])} gardens={m["environment"]["garden_count"]} elapsed={time.monotonic()-started:.1f}s',flush=True)
    return m


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=ROOT/'build/generated/province_capitals_v10/resources');p.add_argument('--legacy-out',type=Path,default=ROOT/'build/generated/province_capitals/resources');p.add_argument('--v9-out',type=Path,default=ROOT/'build/generated/province_capitals_v9/resources');p.add_argument('--regions',nargs='+',choices=REGIONS,default=list(REGIONS));a=p.parse_args()
    if a.out.resolve() in {a.legacy_out.resolve(),a.v9_out.resolve()}:raise ValueError('v3 输出不得覆盖旧资源根')
    for region in a.regions:export(region,a.out,a.legacy_out,a.v9_out)


if __name__=='__main__':main()
