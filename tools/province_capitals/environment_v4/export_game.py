#!/usr/bin/env python3
"""仅替换符灯和晒布架的 v4 城市资源；已发布 v3 与其余布局只读。"""
from __future__ import annotations

import argparse
from collections import defaultdict
import copy
import json
from pathlib import Path
import shutil
import sys

ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'tools'))
from buildgen.modset import load_modset
from province_capitals.environment_design.geometry import Scene
from province_capitals.environment_v3.export_game import copy_unchanged_tile,REGIONS
from province_capitals.environment_v3.world import World,AIR,raster
from province_capitals.game.compile import canonical,identity,resource_file,sha,write,life_inputs
from province_capitals.regional.v2.export_game import write_tile

POLICY=ROOT/'blueprints/street_furniture/replacement.json'
CATALOG=ROOT/'blueprints/street_furniture/catalog.json'
DESIGN=ROOT/'blueprints/province_capitals/environment_design/catalog.json'
ENV=ROOT/'blueprints/province_capitals/environment_v3'


def read(path):return json.loads(Path(path).read_text())


class Capture:
    def __init__(self):self.cells={};self.changes=defaultdict(int)
    def set(self,pos,state,reason,**unused):self.cells[pos]=state;self.changes[reason]+=1


def source_cells(record,design,materials):
    scene=Scene(design['materials']);scene.recipe(record['recipe'],design['recipes'],record['origin'],record['scale'],q=record['rotation_quarters'])
    capture=Capture();raster(capture,scene.boxes,materials,record['id']);return capture.cells


def facing(record,rule,plan):
    if rule['facing']=='garden_gate_or_authored':
        for garden in plan.get('gardens',[]):
            if record['id']!=garden['id']+'_晒架':continue
            x0,z0,x1,z1=garden['bounds'];gx,gz=garden['gate']
            # 精确消费院门所在边；不使用附近房屋名称推断朝向。
            if gz==z0:return 'north'
            if gz==z1:return 'south'
            if gx==x0:return 'west'
            if gx==x1:return 'east'
            raise ValueError('院门不在院墙边上：'+garden['id'])
    return ('north','east','south','west')[record['rotation_quarters']]


def placement(record,rule,family,plan):
    face=facing(record,rule,plan);turn=('north','east','south','west').index(face);x,y,z=record['origin']
    if any(type(v) is not int for v in (x,y,z)):raise ValueError('旧设施没有整数原点')
    if rule['anchor']=='centered':
        dx,dz=((1,0),(0,1),(-1,0),(0,-1))[turn];x-=max(dx,0);z-=max(dz,0)
    elif rule['anchor']!='source_origin':raise ValueError('未知街景原点策略')
    root=[x,y,z];width,height,depth=family['size'];cells={};floors=[]
    for index in range(width*height*depth):
        dx=index%width;dz=(index//width)%depth;dy=index//(width*depth)
        for _ in range(turn):dx,dz=-dz,dx
        point=(x+dx,y+dy,z+dz);cells[point]=f"{rule['block']}[facing={face},part={index},waterlogged=false]"
        if dy==0:floors.append((point[0],point[1]-1,point[2]))
    return root,face,cells,floors


def replace(world,plan,design,policy,catalog,building_base):
    families={f['id']:f for f in catalog['families']};records=[];before={}
    world.protect_life();world.protect_routes(world.manifest.get('roads',[]))
    world.protect_routes([{'id':'door:'+a['building_id'],'points':a['points']} for a in world.manifest.get('access_paths',[])])
    for garden in plan.get('gardens',[]):
        path=garden.get('path',[])
        if not path:continue
        ys=garden.get('path_y',[garden['y']]*len(path))
        if len(ys)!=len(path):raise ValueError('原院门接路标高数量不一致')
        points=[[x,y,z] for (x,z),y in zip(path,ys)]
        if len(points)==1:points.append(points[0])
        world.protect_routes([{'id':garden['id']+'_院门接路','points':points,'width':1}])
    for decoration in world.manifest['environment']['decorations']:
        recipe=decoration.get('recipe')
        if recipe not in policy['recipes']:continue
        rule=policy['recipes'][recipe];family=families[rule['block'].split(':',1)[1]]
        old=source_cells(decoration,design,world.config['materials']);root,face,new,floors=placement(decoration,rule,family,plan)
        index=len(records);touched=set(old)|set(new)
        for pos in touched:
            before.setdefault(pos,world.get(pos))
        records.append({'id':f"{recipe}_{index:03}",'source':decoration,'old':old,'new':new,'touched':touched,'root':root,'facing':face,'floors':floors,'block':rule['block']})
    # v3 的建筑包围盒里可能含后来加的晒架，不能把这些陈设误认成原房屋。
    # 反查经过身份核验的 owning v2 原房屋，逐点保留真正的实体构件。
    structural={}
    for pos in before:
        x,y,z=pos
        if any(b['bounds']['min'][0]<=x<=b['bounds']['max'][0]
               and b['position'][1]<=y<=b['bounds']['max'][1]
               and b['bounds']['min'][2]<=z<=b['bounds']['max'][2] for b in world.manifest.get('buildings',[])):
            state=building_base.get(pos)
            if state!=AIR and 'leaves' not in state and not state.startswith('minecraft:water'):
                structural[pos]=world.config['inherited_state_rewrites'].get(state,state)
    for record in records:
        for pos,state in record['old'].items():
            if before[pos]==state:world.set(pos,structural.get(pos,AIR),'移出旧设施:'+record['id'])
    for record in records:
        accepted=None
        for dx,dz in policy['placement_offsets']:
            if type(dx) is not int or type(dz) is not int or abs(dx)+abs(dz)>2:raise ValueError('设施微调必须在明确两格范围内')
            candidate={(x+dx,y,z+dz):state for (x,y,z),state in record['new'].items()}
            floors=[(x+dx,y,z+dz) for x,y,z in record['floors']]
            if any(not(0<=x<world.width and 0<=y<world.height and 0<=z<world.depth) or not world.passable(world.get((x,y,z))) or (x,y,z) in world.protected for x,y,z in candidate):continue
            if any(world.get(pos) in (AIR,'minecraft:water','minecraft:lava') for pos in floors):continue
            accepted=(dx,dz,candidate,floors);break
        if accepted is None:raise ValueError('新设施在明确微调范围内没有合法空间：'+record['id'])
        dx,dz,record['new'],record['floors']=accepted;record['root']=[record['root'][0]+dx,record['root'][1],record['root'][2]+dz]
        record['relocation']=[dx,0,dz];record['touched']=set(record['old'])|set(record['new'])
        for pos,state in record['new'].items():
            before.setdefault(pos,world.get(pos))
            world.set(pos,state,'自定义设施:'+record['id'])
    world.verify_life()
    if set(world.edit_source)-set(before):raise ValueError('街景替换改到了声明范围以外')
    for pos,state in structural.items():
        if world.get(pos)!=state:raise ValueError('街景替换改动原房屋实体：'+str((pos,state,world.get(pos))))
    replacements={id(r['source']):r for r in records};decorations=[]
    for original in world.manifest['environment']['decorations']:
        record=replacements.get(id(original))
        if record is None:decorations.append(original);continue
        decorations.append({'id':original['id'],'kind':'custom_furniture','source_recipe':original['recipe'],'block':record['block'],'root':record['root'],'facing':record['facing'],'relocation':record['relocation'],
                            'source_origin':original['origin'],'source_scale':original['scale'],'source_rotation_quarters':original['rotation_quarters']})
    world.manifest['environment']['decorations']=decorations
    return records


def export(region,out,source,v9_source,legacy_source):
    plan=read(ENV/'cities'/f'{region}.json');config=read(ENV/'materials.json');design=read(DESIGN);policy=read(POLICY);catalog=read(CATALOG)
    if (policy['schema'],policy['source_version'],policy['target_version'])!=(1,'v3','v4'):raise ValueError('街景替换版本错误')
    resource=f'myvillage:capitals/v3/capital_{region}/city.json';previous=read(resource_file(source,resource));manifest=copy.deepcopy(previous);world=World(source,manifest,config)
    base_resource=previous['source']['base_resource']
    base_root=legacy_source if plan['base_version']=='v1' else v9_source
    base_manifest=read(resource_file(base_root,base_resource))
    if sha(canonical(identity(base_manifest)))!=previous['source']['base_manifest_sha256']:raise ValueError('原房屋资源身份不一致')
    building_base=World(base_root,base_manifest,config)
    records=replace(world,plan,design,policy,catalog,building_base)
    target=out/f'data/myvillage/capitals/v4/capital_{region}';target.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(resource_file(source,previous['terrain']['resource']),target/'terrain.bin.gz');manifest['terrain']['resource']=f'myvillage:capitals/v4/capital_{region}/terrain.bin.gz'
    tiles=[];palette=set()
    for tx,tz in sorted(set(world.refs)|world.dirty):
        path=out/f'data/myvillage/structure/province_capitals/v4/capital_{region}/tile_{tx}_{tz}.nbt';path.parent.mkdir(parents=True,exist_ok=True)
        original=world.refs.get((tx,tz))
        if (tx,tz) in world.dirty:
            if (tx,tz) in world.nbt_tiles:raise ValueError('街景改造不得丢弃原分块 NBT：'+str((tx,tz)))
            cells=world.tile((tx,tz));palette.update(cells.values());count,palette_count=write_tile(path,{p:(s,'','') for p,s in cells.items()},world.height)
            record={'block_count':count,'palette_count':palette_count}
        else:
            palette.update(copy_unchanged_tile(resource_file(source,original['template'],template=True),path))
            record={k:v for k,v in original.items() if k not in {'x','z','template','sha256','bytes'}}
        tiles.append({**record,'x':tx*16,'z':tz*16,'template':f'myvillage:province_capitals/v4/capital_{region}/'+path.stem,'sha256':sha(path.read_bytes()),'bytes':path.stat().st_size})
    errors=load_modset('vanilla').palette_block_errors(sorted(palette))
    if errors:raise ValueError('; '.join(errors))
    manifest['tiles']=tiles;manifest['palette_states']=sorted(palette);manifest['appearance_id']='capital_'+region+'_street_furniture_v4'
    manifest['environment']['street_furniture']={'count':len(records),'changed_tiles':len(world.dirty),'source_version':'v3','target_version':'v4'}
    inputs=[POLICY,CATALOG,Path(__file__),ROOT/'tools/street_furniture/generate.py']
    manifest['source']={'authored':[str(p.relative_to(ROOT)) for p in inputs[:2]],'generator':str(Path(__file__).relative_to(ROOT)),
                        'hashes':{str(p.relative_to(ROOT)):sha(p.read_bytes()) for p in inputs},'base_resource':resource,'base_manifest_sha256':sha(canonical(identity(previous))),
                        'modset':{'profile':'vanilla','resolver':'tools/buildgen/modset.py'},'scope':'仅符灯和晒布架；其余 V10 布局、生活身份和方块保留。'}
    life_inputs(manifest,world.width,world.height,world.depth,192);write(target/'city.json',manifest)
    print(f'{region}: v4 fixtures={len(records)} changed_tiles={len(world.dirty)}',flush=True)
    return manifest


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,default=ROOT/'build/generated/province_capitals_v11/resources');parser.add_argument('--source',type=Path,default=ROOT/'build/generated/province_capitals_v10/resources');parser.add_argument('--v9-source',type=Path,default=ROOT/'build/generated/province_capitals_v9/resources');parser.add_argument('--legacy-source',type=Path,default=ROOT/'build/generated/province_capitals/resources');parser.add_argument('--regions',nargs='+',choices=REGIONS,default=list(REGIONS));args=parser.parse_args()
    if args.out.resolve() in {args.source.resolve(),args.v9_source.resolve(),args.legacy_source.resolve()}:raise ValueError('新街景资源不得覆盖已发布资源')
    # 用户选择旧城保持原样，撤销本轮草稿曾生成的旧城改造清单。
    abandoned=args.out/'data/myvillage/street_furniture/repairs'
    if abandoned.is_dir():shutil.rmtree(abandoned)
    for region in args.regions:export(region,args.out,args.source,args.v9_source,args.legacy_source)


if __name__=='__main__':main()
