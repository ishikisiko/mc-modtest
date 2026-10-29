#!/usr/bin/env python3
"""把省城蓝图编译成可在独立测试世界放置的原生结构数据包。"""
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
from province_capitals.blueprint_plan.generate import DEFAULT,make_planner,digest,read,rect,perimeter,region_id,environment_input,preview_scene,html
from province_capitals.environment_v3.world import World,AIR
from province_capitals.environment_v3.export_game import construct
from province_capitals.environment_v4.export_game import replace
from province_capitals.blueprint_plan.nbt_entities import block_entities,write_tile
from province_capitals.regional.v2.common import props
from province_capitals.game.compile import resource_file,canonical,sha
from buildgen.nbtread import read_gzipped_nbt,state_string
from buildgen.modset import load_modset


def write(path,value):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')


def base_manifest_path(spec, root):
    backend = spec.get('backend', 'regional')
    if backend not in ('regional', 'central'):
        raise ValueError('未知蓝图生成器：'+str(backend))
    version = 'v1' if backend == 'central' else 'v2'
    return root/f'data/myvillage/capitals/{version}/{spec["city_id"]}/city.json'


def baseline(spec,root):
    manifest=read(base_manifest_path(spec,root))
    if manifest['city_id'] != spec['city_id']:
        raise ValueError('原建筑资源与蓝图城市身份不一致')
    for name,expected in manifest['source']['hashes'].items():
        if sha((ROOT/name).read_bytes())!=expected:raise ValueError('原建筑输入变动，须先重新导出：'+name)
    for tile in manifest['tiles']:
        if sha(resource_file(root,tile['template'],template=True).read_bytes())!=tile['sha256']:
            raise ValueError('原建筑切片摘要不符：'+tile['template'])
    if sha(resource_file(root,manifest['terrain']['resource']).read_bytes())!=manifest['terrain']['sha256']:
        raise ValueError('原地形摘要不符')
    cfg=read(ROOT/'blueprints/province_capitals/environment_v3/materials.json')
    env=environment_input(spec,read(ROOT/spec['sources']['environment']));env['gardens']=[]
    catalog=read(ROOT/spec['sources']['landmarks'])
    world=World(root,copy.deepcopy(manifest),cfg)
    if spec.get('backend') == 'central':
        from province_capitals.blueprint_plan.central import prepare_world
        prepare_world(spec,world)
    inherited_rewrites=[]
    for key in world.refs:
        for (x,y,z),state in list(world.tile(key).items()):
            if state in cfg['inherited_state_rewrites']:
                replacement=cfg['inherited_state_rewrites'][state]
                p=(key[0]*16+x,y,key[1]*16+z)
                world.set(p,replacement,'已声明的继承状态修正')
                world.originals[key][x,y,z]=replacement
                inherited_rewrites.append({'position':list(p),'from':state,'to':replacement})
    world.inherited_rewrites=inherited_rewrites
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


def road_audit(planner,world):
    """核对实际身体净空及完整在用路幅，不只保护原本已经能走的格子。"""
    if planner.spec.get('backend')=='central':
        points=getattr(planner,'original_road_walkpoints',{})
        failed=[{'position':list(p),'road_id':rid} for p,rid in points.items() if not world.walkable(p)]
        unresolved=getattr(planner,'road_unavailable',[])
        return {'status':'pass' if not failed and not unresolved else 'needs_design',
                'headroom':world.config['collision']['headroom'],'columns':len(points),
                'failed':failed,'source_unavailable':unresolved}
    failed=[]
    for (x,z),r in planner.model.road_columns.items():
        p=(x,r[0],z)
        if not world.walkable(p):
            failed.append({'position':list(p),'road_id':r[4],
                           'states':[world.get((x,r[0]+dy,z)) for dy in (-1,0,1)]})
    return {'status':'pass' if not failed else 'needs_design','headroom':world.config['collision']['headroom'],
            'columns':len(planner.model.road_columns),'failed':failed}


def build(spec,base):
    world=baseline(spec,base)
    planner=make_planner(spec,read(ROOT/spec['sources']['city']),read(ROOT/spec['sources']['environment']),
                    read(ROOT/spec['sources']['landmarks']),block_world=world)
    if planner.original_terrain!=world.terrain:raise ValueError('蓝图与实际方块使用了不同的原地形')
    data=planner.compile()
    if data['checks']['issues']:raise ValueError('实际方块避障未通过：'+json.dumps(data['checks']['issues'],ensure_ascii=False))
    materials=spec['minecraft']['materials']
    before_terrain=world.terrain[:];allowed=set();door_edits=set();passage_edits=set();road_edits=set();stairs={};new_leaves=set()
    original_buildings={}
    for b in world.manifest['buildings']:
        lo,hi=b['bounds']['min'],b['bounds']['max']
        for z in range(lo[2],hi[2]+1):
            for x in range(lo[0],hi[0]+1):
                for y in range(b['position'][1],hi[1]+1):
                    state=world.get((x,y,z))
                    if state!=AIR:original_buildings[x,y,z]=state
    if spec.get('backend')=='central' and spec.get('road_adjustments'):
        from province_capitals.blueprint_plan.central import apply_road_adjustments
        road_edits=apply_road_adjustments(world,planner,data)
        if not isinstance(road_edits,set) or any(not isinstance(p,tuple) or len(p)!=3
                or any(type(v) is not int for v in p)
                or not (0<=p[0]<world.width and 0<=p[1]<world.height and 0<=p[2]<world.depth) for p in road_edits):
            raise ValueError('道路施工须返回实际变化的有界整数坐标set')
        if any(p in original_buildings and world.get(p)==original_buildings[p] for p in road_edits):
            raise ValueError('道路施工例外包含未发生变化的原实体')
    for opening in data.get('public_passages',[]):
        for p in map(tuple,opening['cells']):
            if world.get(p)!=AIR:passage_edits.add(p)
            world.set(p,AIR,opening['id']+'_公共门楼开口')
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
        door_state=(planner.model.material('DOOR') if spec.get('backend')=='central'
                    else planner.door_material(d['owner']))
        x,y,z=d['position']
        door_tag=d.get('garden_id') or d.get('id') or (d['owner']+'_入户门')
        for dy in range(d['headroom']):
            state=props(door_state,facing=d['facing'],half='lower' if dy==0 else 'upper',hinge='left',open=False,powered=False) if dy<2 else AIR
            world.set((x,y+dy,z),state,door_tag+'_新门洞');door_edits.add((x,y+dy,z))
    leaf_distances(world,new_leaves,set(spec['minecraft']['leaf_log_blocks']))
    world.verify_life()
    for pos,old in original_buildings.items():
        if pos not in door_edits and pos not in passage_edits and pos not in road_edits and world.get(pos)!=old:
            raise ValueError('蓝图施工改到了声明门洞以外的原房屋：'+str((pos,old,world.get(pos))))
    changed_terrain={(i%world.width,i//world.width) for i,(a,b) in enumerate(zip(before_terrain,world.terrain)) if a!=b}
    if not changed_terrain<=allowed:raise ValueError('整地超出蓝图范围')
    for p,state in stairs.items():
        if world.get(p)!=state:raise ValueError('后续施工覆盖了楼梯')
    for g in data['gardens']:
        for p in g['path']+g['internal_path']:
            if not world.walkable(tuple(p)):raise ValueError('最终通路不通：'+str((g['id'],p)))
    for opening in data.get('public_passages',[]):
        for x,z in rect(opening['bounds']):
            y=opening['y']
            if not world.walkable((x,y,z)) or any(world.get((x,y+dy,z))!=AIR for dy in range(opening['height'])):
                raise ValueError('最终公共门楼开口不通：'+opening['id'])
    if spec.get('backend')=='central':
        from province_capitals.blueprint_plan.central import verify_applied_world
        data['central_structural_checks']=verify_applied_world(world,data)
    data['road_audit']=road_audit(planner,world)
    if data['road_audit']['status']!='pass':
        raise ValueError('整城实际道路净空未通过：'+json.dumps(data['road_audit'],ensure_ascii=False))
    data['minecraft_checks']={'status':'pass','stairs':len(stairs),'new_door_cells':len(door_edits),
                              'public_passage_cells':len(passage_edits),
                              'road_adjustment_cells':len(road_edits),
                              'changed_terrain_columns':len(changed_terrain),'preserved_building_cells':len(set(original_buildings)-door_edits-passage_edits-road_edits),
                              'life_places':len(world.manifest['life']['places']),'runtime':'not_run'}
    data['inherited_state_rewrites']=world.inherited_rewrites
    data['stair_blocks']=[{'position':list(p),'state':s} for p,s in sorted(stairs.items())]
    data['road_changed_blocks']=[{'position':list(p),'state':world.get(p)} for p in sorted(road_edits)]
    return planner,world,data


def source_hashes(spec_path,base,spec):
    files=[spec_path,ROOT/'tools/province_capitals/blueprint_plan/generate.py',Path(__file__),
           ROOT/'tools/province_capitals/blueprint_plan/nbt_entities.py',
           ROOT/'tools/province_capitals/blueprint_plan/viewer.js',ROOT/'tools/province_capitals/blueprint_plan/template.html',
           ROOT/'tools/web/three.min.js',ROOT/'tools/web/OrbitControls.js',
           ROOT/'blueprints/province_capitals/environment_v3/materials.json',ROOT/'blueprints/street_furniture/replacement.json',
           ROOT/'blueprints/street_furniture/catalog.json']+[ROOT/v for v in spec['sources'].values()]
    files += [ROOT/name for name in read(base_manifest_path(spec,base))['source']['hashes']]
    if spec.get('backend')=='central':files.append(ROOT/'tools/province_capitals/blueprint_plan/central.py')
    for folder in ('environment_v3','environment_v4','environment_design'):
        files += list((ROOT/'tools/province_capitals'/folder).glob('*.py'))
    return {str(p.relative_to(ROOT)):sha(p.read_bytes()) for p in sorted(set(files))}


def export(spec_path,base,out,*,preview_out=None):
    spec_path=Path(spec_path).resolve();base=Path(base).resolve()
    out=Path(out)
    if out.exists():raise ValueError('输出目录已存在；每次实际方块试验使用独立目录保留失败证据')
    spec=read(spec_path);region=region_id(spec)
    hashes=source_hashes(spec_path,base,spec)
    out.mkdir(parents=True)
    write(out/'input-identity.json',{'source_hashes':hashes,'source_fingerprint':digest(hashes)})
    planner,world,data=build(spec,base)
    pack=out/'datapack';write(pack/'pack.mcmeta',{'pack':{'pack_format':spec['minecraft']['pack_format'],'description':spec['name']+'原生结构试验'}})
    origin=spec['minecraft']['origin'];tiles=[];palette=set();block_count=0;entity_count=0
    for tz in range(world.depth//16):
        for tx in range(world.width//16):
            cells={}
            for z in range(16):
                for x in range(16):
                    top=world.ground(tx*16+x,tz*16+z)
                    for y in range(top+1):cells[x,y,z]=(world.manifest['terrain']['top_block'] if y==top else world.manifest['terrain']['fill_block'],'terrain','terrain')
            cells.update({p:(s,'','') for p,s in world.tile((tx,tz)).items()})
            path=pack/f'data/myvillage/structure/blueprint_trial/{region}/tile_{tx}_{tz}.nbt';path.parent.mkdir(parents=True,exist_ok=True)
            source=world.refs.get((tx,tz))
            original_nbt=(block_entities(resource_file(base,source['template'],template=True))
                          if (tx,tz) in world.nbt_tiles and source else {})
            count,_,preserved_nbt=write_tile(path,cells,world.height,original_nbt);block_count+=count;entity_count+=preserved_nbt
            _,raw=read_gzipped_nbt(str(path));states=[state_string(s) for s in raw['palette']]
            decoded={tuple(b['pos']):states[b['state']] for b in raw['blocks']}
            if decoded!={p:s[0] for p,s in cells.items()}:raise ValueError('NBT 回读不同于施工结果：'+str(path))
            palette.update(states)
            tiles.append({'x':tx*16,'z':tz*16,'template':f'myvillage:blueprint_trial/{region}/tile_{tx}_{tz}',
                          'bytes':path.stat().st_size,'sha256':sha(path.read_bytes()),'blocks':count,'block_entities':preserved_nbt})
    errors=load_modset('vanilla').palette_block_errors(sorted(palette))
    if errors:raise ValueError('; '.join(errors))
    functions=pack/f'data/myvillage/function/blueprint_trial/{region}';functions.mkdir(parents=True)
    batches=[]
    for start in range(0,world.depth//16,spec['minecraft']['batch_rows']):
        rows=[t for t in tiles if start*16<=t['z']<(start+spec['minecraft']['batch_rows'])*16]
        name=f'batch_{start:02}'
        commands=[f"place template {t['template']} {origin[0]+t['x']} {origin[1]} {origin[2]+t['z']}" for t in rows]
        (functions/(name+'.mcfunction')).write_text('\n'.join(commands)+'\n')
        batches.append({'function':f'myvillage:blueprint_trial/{region}/'+name,'tiles':len(rows),
                        'bounds':[origin[0],origin[2]+start*16,origin[0]+world.width-1,origin[2]+max(t['z'] for t in rows)+15]})
    checks={}
    for g in data['gardens']:
        for x,y,z in g['path']+g['internal_path']:
            for dy in (-1,0,1):checks[x,y+dy,z]=world.get((x,y+dy,z))
    for d in data['doors']:
        x,y,z=d['position']
        for dy in range(3):checks[x,y+dy,z]=world.get((x,y+dy,z))
    for opening in data.get('public_passages',[]):
        for x,z in rect(opening['bounds']):
            for dy in range(-1,opening['height']):
                y=opening['y']+dy;checks[x,y,z]=world.get((x,y,z))
    for block in data.get('road_changed_blocks',[]):
        checks[tuple(block['position'])]=block['state']
    for item in data['fixtures']:
        x,y,z=item['position'];w,h,d=item['size']
        for xx in range(x,x+w):
            for yy in range(y-1,y+h):
                for zz in range(z,z+d):checks[xx,yy,zz]=world.get((xx,yy,zz))
    lines=['scoreboard objectives add bp_audit dummy','scoreboard players set matched bp_audit 0']
    for (x,y,z),state in sorted(checks.items()):
        lines.append(f'execute if block {x+origin[0]} {y+origin[1]} {z+origin[2]} {state} run scoreboard players add matched bp_audit 1')
    (functions/'audit.mcfunction').write_text('\n'.join(lines)+'\n')
    if source_hashes(spec_path,base,spec)!=hashes:
        raise RuntimeError('导出期间输入变动，拒绝发布混合来源数据包；失败产物保留')
    data['source_hashes']=hashes;data['source_fingerprint']=digest(hashes)
    write(out/'plan.json',data)
    inventory={'schema':1,'city_id':spec['city_id'],'region':region,'scope':spec['minecraft']['scope'],'origin':origin,'dimensions':data['dimensions'],
               'source_fingerprint':data['source_fingerprint'],'tiles':tiles,'batches':batches,'palette_states':sorted(palette),
               'blocks':block_count,'block_entities':entity_count,'expected_audit_matches':len(checks),'checks':data['minecraft_checks'],'runtime':'not_run'}
    write(out/'inventory.json',inventory)
    archive=out/f'{region}-blueprint-trial.zip'
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for p in sorted(pack.rglob('*')):
            if p.is_file():
                info=zipfile.ZipInfo(str(p.relative_to(pack)),date_time=(1980,1,1,0,0,0))
                info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
                z.writestr(info,p.read_bytes(),compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
    if preview_out is not None:
        preview_out=Path(preview_out);preview_out.mkdir(parents=True,exist_ok=True)
        write(preview_out/'plan.json',data)
        write(preview_out/'checks.json',{'blueprint':data['checks'],'minecraft':data['minecraft_checks'],'roads':data['road_audit']})
        write(preview_out/'construction.json',{key:data[key] for key in
              ('source_fingerprint','paths','doors','public_passages','earthwork','fixtures','wall','landmark','landmark_paths','retained_environment')})
        (preview_out/'index.html').write_text(html(data,preview_scene(planner,data)))
    if source_hashes(spec_path,base,spec)!=hashes:
        raise RuntimeError('生成预览期间输入变动，拒绝发布混合来源清单；失败产物保留')
    write(out/'artifact.json',{'file':archive.name,'bytes':archive.stat().st_size,'sha256':sha(archive.read_bytes()),'source_fingerprint':data['source_fingerprint']})
    print(json.dumps({'output':str(out),'tiles':len(tiles),'blocks':block_count,'checks':inventory['checks'],'audit_samples':len(checks)},ensure_ascii=False),flush=True)
    return inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--input',type=Path,default=DEFAULT)
    p.add_argument('--base-v2',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--preview-out',type=Path)
    a=p.parse_args();export(a.input.resolve(),a.base_v2.resolve(),a.out.resolve(),preview_out=a.preview_out)


if __name__=='__main__':main()
