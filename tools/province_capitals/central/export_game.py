#!/usr/bin/env python3
"""导出完整中州正式资源。仅正常生成；不运行 tests/check/validator/MC。"""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timezone
import gzip
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import traceback

ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'tools'))
sys.path.insert(0,str(Path(__file__).resolve().parent))
from game_models import Factory, rotate_point, rotate_state
from game_world import World, TileSpool, i16_bytes, sha

SPEC=ROOT/'blueprints/province_capitals/central/game-spec.json'
PREVIEW=ROOT/'out/preview/province-capitals/central'
DEFAULT_OUT=ROOT/'build/generated/province_capitals/resources'


def write_json(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def adapter(profile):
    knots=profile['height_proposal']['knots']
    def adapt(y):
        if y<=knots[0][0]:return knots[0][1]
        for a,b in zip(knots,knots[1:]):
            if y<=b[0]:return a[1]+(b[1]-a[1])*(y-a[0])/(b[0]-a[0])
        return profile['height_proposal']['max_ground_y']
    return adapt


def source_export(run):
    folder=run/'source'
    folder.mkdir(parents=True,exist_ok=True)
    with (run/'source-export.log').open('w',encoding='utf-8') as log:
        proc=subprocess.Popen(['node',str(ROOT/'tools/province_capitals/central/source_game.mjs'),'--out',str(folder)],
                              cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
        for line in proc.stdout:
            print(line,end='',flush=True);log.write(line);log.flush()
        code=proc.wait()
        if code:raise RuntimeError(f'原作输入导出失败，原始日志: {run/"source-export.log"}，exit={code}')
    return json.loads((folder/'source.json').read_text()),folder


def source_hashes(spec):
    paths=[
        'blueprints/province_capitals/central/game-spec.json',spec['height_profile_source'],
        'tools/province_capitals/central/export_game.py','tools/province_capitals/central/game_models.py',
        'tools/province_capitals/central/game_world.py','tools/province_capitals/central/source_game.mjs',
        'web/yunshan-city/src/world/plan.js','web/yunshan-city/src/world/field.js',
        'web/yunshan-city/src/world/geo.js','web/yunshan-city/src/world/geom.js',
        'web/yunshan-city/src/world/noise.js','web/yunshan-city/src/world/materials.js',
        'web/yunshan-city/src/voxel/catalog.js','web/yunshan-city/src/voxel/trees.js',
        'web/yunshan-city/src/voxel/model.js','web/yunshan-city/src/voxel/palette.js',
        'tools/buildgen/grid.py','tools/buildgen/massing.py','tools/buildgen/ops.py',
        'tools/buildgen/style.py','tools/buildgen/modset.py','tools/json_to_nbt.py',
        f'tools/buildgen/styles/{spec["materials"]["style"]}.json',
        f'tools/buildgen/styles/{spec["materials"]["monumental_style"]}.json']
    # The vanilla modset intentionally has no external block catalogue input.
    # full is resolved by load_modset and must include its real catalogue.
    if spec['materials']['modset']=='full':paths.append('exmod/mod_block_catalog.json')
    return {path:sha(ROOT/path) for path in paths}


def model_transform(position,quarters):
    def trans(p):
        x,y,z=rotate_point(p,quarters)
        return position[0]+x,position[1]+y,position[2]+z
    return trans


def prepare_buildings(spec,source,world,factory,adapt):
    choices={};used=set()
    for service in spec['services']:
        candidates=[b for b in source['buildings'] if source['defs'][b['id']]['kind']==service['kind']
                    and b['source_instance_index'] not in used]
        near=service['near']
        chosen=min(candidates,key=lambda b:(b['x']-near[0])**2+(b['z']-near[1])**2)
        used.add(chosen['source_instance_index']);choices[chosen['source_instance_index']]=service
    rows=[];models=[];transforms=[]
    for ordinal,b in enumerate(source['buildings'],1):
        definition=source['defs'][b['id']];kind=definition['kind'];service=choices.get(b['source_instance_index'])
        compound=next((c for c in spec.get('connected_compounds',[]) if c['wing']==b['id'] and c['source_y']==b['y']),None)
        if compound:
            definition={**definition,'platform':compound['platform'],'variant':b['id']+'_compound'}
        model=factory.build(definition,service['role'] if service else '')
        wall_offset=9 if kind=='walltower' else 0
        base=round(adapt(b['y']-wall_offset)+wall_offset)-1
        adjustment=max(0,base+model.top-(319-spec['height_clearance']))
        base-=adjustment
        # Source bridge local X is already its span. q=0 corrects the source
        # rot=1 annotation/render mismatch for ALL cross-river/outlet bridges.
        quarters=0 if kind in ('archbridge','langqiao') else (4-b['rot'])%4
        position=world.local((b['x'],base,b['z']))
        trans=model_transform(position,quarters)
        occupied=[trans(p) for p,state in model.cells.items() if state!='minecraft:air']
        row={'id':f'building_{b["source_instance_index"]}', 'ordinal':ordinal,
             'source_instance_index':b['source_instance_index'],'source_template':b['id'],'source_kind':kind,
             'source_position':[b['x'],b['y'],b['z']],'source_rotation_quarters':b['rot'],
             'position':list(position),'rotation_quarters':quarters,'service':service['role'] if service else '',
             'name':service['name'] if service else b['id'],
             'entry':list(trans(model.entry)),'floors':model.floors,'roof':model.roof_type,
             'bounds':{'min':[min(p[i] for p in occupied) for i in range(3)],
                       'max':[max(p[i] for p in occupied) for i in range(3)]},
             'height_adjustment':-adjustment,'template_top_local':model.top,
             'geometry':'minecraft_enterable_template','traversal':'not_verified'}
        if compound:
            main=next(r for r in rows if r['source_template']==compound['main'])
            row['shared_foundation_ordinal']=main['ordinal'];row['entry_via']=main['id']
            row['compound']=compound
            row['entry']=list(trans(model.doors[0]))
        rows.append(row);models.append(model);transforms.append(trans)
        if kind not in ('archbridge','langqiao'):world.reserve(row,model,trans)
    return rows,models,transforms


def write_buildings(spec,world,rows,models,transforms):
    places=[];services=[]
    for row,model,trans in zip(rows,models,transforms):
        q=row['rotation_quarters']
        for p,state in model.cells.items():world.spool.put(*trans(p),rotate_state(state,q))
        for p,text in model.signs.items():world.spool.signs[trans(p)]=text
        for place in model.places:
            places.append({**place,'pos':list(trans(place['pos'])),
                           'block_pos':list(trans(place['block_pos'])) if place['block'] else [0,0,0],
                           'building_id':row['id']})
        if row['service']:
            services.append({'id':row['service'],'name':row['name'],'building_id':row['id'],
                             'entry':row['entry'],'places':[p['id'] for p in model.places],
                             'binding':'runtime_WorldSim_facade','availability':'not_verified'})
        # Bridges have real abutments down to the heightfield, not floating end caps.
        if row['source_kind'] in ('archbridge','langqiao'):
            span=round(world.source['defs'][row['source_template']]['span']*.2)
            for x in (-(span//2),-(span//2)+span-1):
                for z in (-1,0,1):
                    xx,base,zz=trans((x,-1,z))
                    for y in range(world.ground(xx,zz)+1,base):world.spool.put(xx,y,zz,spec['materials']['foundation'])
    for row,model,trans in zip(rows,models,transforms):
        compound=row.get('compound')
        if not compound:continue
        dx,fy,dz=model.doors[0]
        floor=fy-1
        for along in range(-3,5):
            for across in range(-(compound['corridor_width']//2),compound['corridor_width']//2+1):
                p=(dx+across,floor,dz+along)
                world.spool.put(*trans(p),spec['materials']['foundation'])
                for head in range(1,compound['corridor_clearance']+1):
                    world.spool.put(*trans((p[0],floor+head,p[2])),'minecraft:air')
    return places,services


def connect_bridges(world,source,rows):
    for row in rows:
        if row['source_kind'] not in ('archbridge','langqiao'):continue
        definition=source['defs'][row['source_template']]
        span=round(definition['span']*.2);x,y,z=row['position']
        # End landings extend onto the original quays; their block height equals
        # the deck endpoints, not the crown of the arch.
        for side,ex in ((-1,x-span//2),(1,x-span//2+span-1)):
            points=[[ex,y+1,z],[ex+side*4,y+1,z]]
            world.add_path(f'bridge_landing_{row["id"]}_{side}',points,3,wood=True,local=True)


def generate_preview(factory,rows,spec,report):
    # Parent displays this generated page. No browser/render/visual check is run here.
    models=[]
    for (template,role),m in factory.cache.items():
        labels={'house':'民居','shop':'商铺','tea':'茶楼','hall':'殿宇','pavilion':'亭','pagoda':'凌霄塔','lou':'楼阁',
                'drumtower':'鼓楼','walltower':'敌楼','citygate':'正阳门','watergate':'水门','palgate':'宫门','archbridge':'石拱桥','langqiao':'廊桥'}
        label=next((s['name'] for s in spec['services'] if s['role']==role),None)
        if not label:
            source_id=template.removesuffix('_compound')
            source_kind=next((r['source_kind'] for r in rows if r['source_template']==source_id),'')
            label=labels.get(source_kind,'建筑')+' · '+template
        models.append({'id':label,'entry':m.entry,'roof':m.roof_type,
                       'cells':[[*p,s] for p,s in m.cells.items() if s!='minecraft:air']})
    payload=gzip.compress(json.dumps(models,separators=(',',':'),ensure_ascii=False).encode(),mtime=0)
    import base64
    template=(ROOT/'tools/province_capitals/central/game-viewer.html').read_text()
    html=template.replace('__THREE__',(ROOT/'tools/web/three.min.js').read_text())
    html=html.replace('__ORBIT__',(ROOT/'tools/web/OrbitControls.js').read_text())
    html=html.replace('__PAYLOAD__',base64.b64encode(payload).decode())
    html=html.replace('__REPORT__',json.dumps({k:report[k] for k in ('buildings','houses','shops','tiles','blocks','resource_bytes','top_building_y','trees','lamps')},ensure_ascii=False))
    target=PREVIEW/'game/index.html';target.parent.mkdir(parents=True,exist_ok=True);target.write_text(html)
    return str(target.relative_to(ROOT))


def export(out,run):
    spec=json.loads(SPEC.read_text());profile=json.loads((ROOT/spec['height_profile_source']).read_text())
    adapt=adapter(profile);source,folder=source_export(run)
    spool=TileSpool(run/'spool',spec);world=World(spec,source,folder,adapt,spool)
    factory=Factory(ROOT,spec)
    rows,models,transforms=prepare_buildings(spec,source,world,factory,adapt)
    world.prepare_walls()
    print(f'生成 {len(rows)} 座真实模板；预留逐栋地基与入口',flush=True)
    for path in source['paths']:
        world.add_path(path['id'],path['points'],path['width'],wood=path['material']==23)
    for link in spec['links']:world.add_path(link['id'],link['points'],link['width'],wood=link.get('wood',False))
    connect_bridges(world,source,rows)
    # A walking entrance on the upper/lower cliff is connected to the same routes.
    # Isolated outer pillar pavilions keep their real entrance, not an invented
    # connection across the cloud sea.
    for row in rows:
        if row['source_kind'] in ('archbridge','langqiao','watergate','walltower') or row['source_template']=='pillar_ting' or row.get('entry_via'):continue
        world.connect(row,row['entry'])
    print(f'已生成 {len(world.access)} 条建筑入口接驳；补入宫后/崖阶/峰顶路线',flush=True)
    world.prepare_water();world.emit_surfaces();world.emit_falls();world.emit_roads();world.emit_walls()
    world.landscape(rotate_point)
    places,services=write_buildings(spec,world,rows,models,transforms)
    gate=next(row for row in rows if row['source_template']=='gate_south')
    # A public, supported cultivation/social landing beside the south gate.
    for place_id,kind,dx,dz in (('city_entry','entry',0,0),('cultivation','cultivate',8,3)):
        if place_id=='city_entry':pos=gate['entry']
        else:
            x,y,z=gate['entry'];x+=dx;z+=dz;y=world.ground(x,z)+1
            world.spool.put(x,y-1,z,'minecraft:polished_andesite')
            world.spool.put(x,y,z,'minecraft:air');world.spool.put(x,y+1,z,'minecraft:air')
            pos=[x,y,z]
        places.append({'id':place_id,'kind':kind,'pos':pos,'capacity':1,'block':'','block_pos':[0,0,0]})
    tiles,counts=spool.export(out)
    city_dir=out/f'data/myvillage/capitals/v1/{spec["city_id"]}';city_dir.mkdir(parents=True,exist_ok=True)
    terrain=city_dir/'terrain.bin.gz';terrain.write_bytes(gzip.compress(i16_bytes(world.h),compresslevel=9,mtime=0))
    routes_path=city_dir/'routes.json'
    write_json(routes_path,{'schema':1,'city_id':spec['city_id'],'coordinate_space':'normalized_absolute_y',
                            'routes':world.routes,'access':world.access,'traversal':'not_verified'})
    construction=city_dir/'buildings.json'
    write_json(construction,{'schema':1,'family':spec['family'],'buildings':rows,
                             'template_count':len(factory.cache),'source_lot_count':source['lot_count']})
    waterworks=city_dir/'waterworks.json'
    write_json(waterworks,{'schema':1,'pools':source['pools'],'source_pool_count':len(source['pools']),
                           'falls':world.waterworks,'hydraulics':'not_run',
                           'note':'原作七级池、六处内跌水，加南出水为七处跌水；飞云瀑另列。落差按主世界高度适配，未复刻原作竖向尺度。'})
    catalog=json.loads((ROOT/'blueprints/province_capitals/catalog.json').read_text())
    family=next(row['layout_id'] for row in catalog['capitals'] if row['city_id']==spec['city_id'])
    city={'schema':1,'format':'capital_game_bundle','city_id':spec['city_id'],'region_id':spec['region_id'],
          'display_name':spec['display_name'],'layout_family_id':family,'settlement_family':spec['family'],
          'size':[world.w,spec['bounds']['height'],world.d],'source_offset':spec['bounds']['source_offset'],
          'placement':spec['placement'],
          'terrain':{'resource':f'myvillage:capitals/v1/{spec["city_id"]}/terrain.bin.gz','sha256':sha(terrain),
                     'encoding':'i16le_z_major_top_solid','width':world.w,'depth':world.d,
                     'top_block':'minecraft:grass_block','fill_block':spec['materials']['fill'],
                     'subsurface_block':'minecraft:dirt','subsurface_depth':3},
          'tiles':tiles,'entries':[{'id':'main_gate','position':gate['entry'],'facing':'south'}],
          'life':{'layout_id':'capital_zhongzhou_v1','places':places},'services':services,
          'details':{p.stem:{'resource':f'myvillage:capitals/v1/{spec["city_id"]}/{p.name}','sha256':sha(p)}
                     for p in (routes_path,construction,waterworks)},
          'height_adaptation':{'id':profile['height_proposal']['id'],'origin_y':0,
                               'building_scale':1,'top_actual_building_y':max(r['bounds']['max'][1] for r in rows),
                               'ceiling_clearance':spec['height_clearance'],'waterfall_vertical_scale':'piecewise_compressed'},
          'landscape':{'source_trees':len(source['trees']),'placed_trees':world.tree_count,
                       'trees_outside_reserved_extent':world.cropped_trees,'trees_omitted_for_clearance':world.omitted_trees,
                       'placed_lamps':world.lamp_count,'resident_entities':0,
                       'pending_cosmetic':source['omitted']},
          'source':{'authored':['blueprints/province_capitals/central/game-spec.json',spec['height_profile_source']],
                    'generator':'tools/province_capitals/central/export_game.py','hashes':source_hashes(spec)},
          'status':spec['status']}
    city_path=city_dir/'city.json';write_json(city_path,city)
    paths=[city_path,terrain,routes_path,construction,waterworks]
    resource_bytes=sum(p.stat().st_size for p in paths)+sum(t['bytes'] for t in tiles)
    kinds=Counter(row['source_kind'] for row in rows)
    report={'city_id':spec['city_id'],'generation':'completed','tests':'not_run','compile':'not_run','minecraft':'not_run',
            'player_appearance':'not_verified','player_traversal':'not_verified',
            'buildings':len(rows),'houses':kinds['house'],'shops':kinds['shop'],'tea':kinds['tea'],
            'source_lots':source['lot_count'],'building_kinds':dict(kinds),'unique_templates':len(factory.cache),
            'tiles':len(tiles),'blocks':counts['blocks'],'terrain_columns':world.w*world.d,'terrain_bytes':terrain.stat().st_size,
            'terrain_uncompressed_bytes':len(world.h)*2,'resource_files':len(paths)+len(tiles),
            'resource_bytes':resource_bytes,'top_building_y':max(r['bounds']['max'][1] for r in rows),
            'height_adjusted_buildings':sum(r['height_adjustment']!=0 for r in rows),
            'heightfield_range':[min(world.h),max(world.h)],'access_paths':len(world.access),
            'routes':len(world.routes),'life_places':len(places),'services':services,
            'trees':world.tree_count,'lamps':world.lamp_count,'counts':counts,
            'resource_root':str(out),'city_json':str(city_path),'run_directory':str(run),
            'generation_command':'python3 tools/province_capitals/central/export_game.py --out build/generated/province_capitals/resources'}
    report['preview']=generate_preview(factory,rows,spec,report)
    write_json(PREVIEW/'game/export-report.json',report);write_json(run/'export-report.json',report)
    print(json.dumps({k:report[k] for k in ('generation','buildings','houses','shops','tiles','resource_files','resource_bytes','top_building_y','access_paths','life_places','trees')},ensure_ascii=False),flush=True)
    return report


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,default=DEFAULT_OUT)
    args=parser.parse_args()
    run=PREVIEW/'game-export-runs'/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    run.mkdir(parents=True,exist_ok=False)
    try:export(args.out.resolve(),run)
    except Exception:
        error=traceback.format_exc();(run/'failure.log').write_text(error)
        print(f'正式导出失败；原始异常保留于 {run/"failure.log"}',file=sys.stderr)
        raise


if __name__=='__main__':main()
