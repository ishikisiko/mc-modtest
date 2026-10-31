#!/usr/bin/env python3
"""将七府正式构造导出为默认 V11 使用的 v4 分块主城资源。"""
from __future__ import annotations

import argparse
from collections import Counter
import copy
import gzip
import json
from pathlib import Path
import shutil
import struct
import sys

ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'tools'))
from buildgen.groups import get_group
from province_capitals.blueprint_plan.generate import make_planner,digest
from province_capitals.blueprint_plan.manor_relayout import read,AIR
from province_capitals.blueprint_plan.manor_relayout_export import source_hashes,check_tile,pillar_groups
from province_capitals.blueprint_plan.atomic_blocks import collect
from province_capitals.regional.v2.export_game import write_tile
from province_capitals.game.compile import life_inputs,write,sha

REGIONS=('beiyuan','nanjiang','lingyue','ximo','donghai','donghuang','moyu')


def entry_points(planner):
    entries=copy.deepcopy(planner.model.entries)
    for building in planner.summaries.values():
        if building['role']=='gate':
            entries.append({'id':building['id'],'position':building['entrance'],'facing':building['facing']})
    entries.append({'id':'lord_manor','position':planner.manor_entry,
                    'facing':('north','east','south','west')[planner.layout['manor']['rotation_quarters']]})
    if len({p['id'] for p in entries})!=len(entries):raise ValueError('默认城市入口身份重复')
    for entry in entries:
        position=tuple(entry['position'])
        if not planner.walkable(position) or not planner.clear(position,planner.cfg['headroom']):
            raise ValueError('默认城市入口缺少实际支撑或净空：'+entry['id'])
    return entries


def export(region,out):
    if region not in REGIONS:raise ValueError('七府默认资源不覆盖中州或未知城市')
    group=get_group('province_capital_'+region+'_manor');spec_path=ROOT/group.scale_params['authored_input']
    spec=json.loads(spec_path.read_text())
    if spec['backend']!='manor_relayout':raise ValueError('默认七府必须使用正式蓝图构造后端')
    inputs=source_hashes(spec_path,spec)
    planner=make_planner(spec,read(spec['sources']['city']),read(spec['sources']['environment']),read(spec['sources']['landmarks']))
    data=planner.compile()
    if data['checks']['issues']:raise ValueError('正式蓝图实际构造未通过：'+json.dumps(data['checks']['issues'],ensure_ascii=False))
    model=planner.model;city_id=spec['city_id'];out=Path(out).resolve()
    data_dir=out/'data/myvillage/capitals/v4'/city_id
    structures=out/'data/myvillage/structure/province_capitals/v4'/city_id
    if out==ROOT or ROOT/'src' in out.parents:raise ValueError('默认资源须写到独立生成目录')
    # 当前开发版本只发行最终资源，清除同城上轮不再引用的切片。
    for folder in (data_dir,structures):
        if folder.exists():shutil.rmtree(folder)
        folder.mkdir(parents=True)
    terrain=data_dir/'terrain.bin.gz';raw=struct.pack('<'+'h'*len(model.terrain),*model.terrain)
    terrain.write_bytes(gzip.compress(raw,mtime=0));tiles=[];palette=set();counts=Counter()
    # 检查完整组件，但不搬到跨区块模板：运行时直接写当前区块的状态，不触发逐块放置更新。
    planner.refs={};planner.dirty=set(model.construction.by_tile)
    atomic=collect(planner,planner.native_rules,read(planner.native_rules['furniture_source']))+pillar_groups(planner)
    for tx,tz in sorted(model.construction.by_tile,key=lambda p:(p[1],p[0])):
        if not(0<=tx<model.width//16 and 0<=tz<model.depth//16):raise ValueError('默认主城切片越出所属城市')
        cells=model.construction.tile(tx,tz)
        if not cells:continue
        path=structures/f'tile_{tx}_{tz}.nbt';blocks,colors=write_tile(path,cells,model.height)
        if colors>4096:raise ValueError('默认主城切片超出既有 palette 边界')
        check_tile(path,cells);palette.update(v[0] for v in cells.values())
        tiles.append({'x':tx*16,'z':tz*16,'template':f'myvillage:province_capitals/v4/{city_id}/'+path.stem,
                      'sha256':sha(path.read_bytes()),'bytes':path.stat().st_size,'block_count':blocks,'palette_count':colors})
        counts['blocks']+=blocks;counts['air']+=sum(v[0]==AIR for v in cells.values())
    errors=model.modset.palette_block_errors(sorted(palette))
    if errors:raise ValueError('默认主城包含未注册方块：'+str(errors))
    entries=entry_points(planner);source=planner.source_city
    bundle={'schema':1,'format':'capital_game_bundle','city_id':city_id,'region_id':region,
            'display_name':source['name'],'layout_family_id':group.scale_params['layout_family_id'],
            'group_id':group.group_id,'style_id':group.style_id,'appearance_id':city_id+'_manor',
            'size':planner.city['dimensions'],'source_offset':source['source_offset'],'placement':source['placement'],
            'terrain':{'resource':f'myvillage:capitals/v4/{city_id}/terrain.bin.gz','sha256':sha(terrain.read_bytes()),
                       'encoding':'i16le_z_major_top_solid','width':model.width,'depth':model.depth,
                       'top_block':model.material('GROUND_TOP').split('[',1)[0],'fill_block':model.material('GROUND_FILL').split('[',1)[0],
                       'subsurface_block':model.material('GROUND_SUBSURFACE').split('[',1)[0],'subsurface_depth':3},
            'tiles':tiles,'entries':entries,
            'life':{'layout_id':group.scale_params['life_layout_id'],'name':source['name'],'places':model.places,
                    'fixtures':model.fixtures,'binding':'runtime_owned','resident_source_hint':'existing_worldsim_population'},
            'buildings':[p['building'] for p in data['plots']],'roads':data['roads'],'access_paths':data['building_access'],
            'districts':data['districts'],'palette_states':sorted(palette),
            'coverage':{'houses':data['checks']['houses'],'buildings':len(data['plots']),'manors':1,
                        'retained_services':planner.layout['expected']['services'],'life_places':len(model.places),
                        'templates':len(tiles),'recorded_blocks':counts['blocks'],'explicit_air':counts['air'],
                        'complete_component_groups':len(atomic)},
            'blueprint':{'input':str(spec_path.relative_to(ROOT)),'layout':spec['sources']['layout'],
                         'manor':data['manor'],'landmark':data['landmark'],'gardens':data['gardens'],
                         'checks':data['checks'],'construction_audit':data['construction_audit']},
            'source':{'generator':'tools/province_capitals/blueprint_plan/runtime.py','hashes':inputs,
                      'source_fingerprint':digest(inputs),'terrain_semantics':'top-solid 地基；稀疏 NBT 覆盖实际地坪、架空支墩、水、楼梯和空气。'},
            'status':{'generation':'completed','nbt_roundtrip':'pass','palette':'pass','minecraft':'not_run',
                      'owner_appearance':'not_verified','fps':'not_verified','multiplayer':'not_verified'}}
    life_inputs(bundle,model.width,model.height,model.depth,192)
    if inputs!=source_hashes(spec_path,spec):raise ValueError('默认主城生成期间输入变化；保留失败产物')
    write(data_dir/'city.json',bundle)
    print(f'{city_id}: 正式七府默认资源 houses={data["checks"]["houses"]} life={len(model.places)} tiles={len(tiles)} manor_cells={len(planner.manor_cells)}',flush=True)
    return bundle


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,default=ROOT/'build/generated/province_capitals_v11/resources')
    parser.add_argument('--regions',nargs='+',choices=REGIONS,default=list(REGIONS));args=parser.parse_args()
    if len(set(args.regions))!=len(args.regions):parser.error('默认主城不能重复')
    for region in args.regions:export(region,args.out)


if __name__=='__main__':main()
