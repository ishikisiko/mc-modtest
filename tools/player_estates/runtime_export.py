"""将获准的西坡庄园写入正式中州切片；保留修改范围以外的方块和原始方块实体。"""
from __future__ import annotations

import gzip
import json
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from player_estates.build_terraces import construct
from province_capitals.central import export_game as owning
from province_capitals.central.game_models import rotate_state
from province_capitals.environment_v3.world import World, AIR
from province_capitals.blueprint_plan.nbt_entities import block_entities, write_tile
from province_capitals.game.compile import canonical, sha, write, resource_file, life_inputs
from buildgen.nbtread import read_gzipped_nbt, state_string

RULES = ROOT / 'blueprints/player_estates/purchase.json'


def integrate(out: Path):
    out = Path(out)
    file = out / 'data/myvillage/capitals/v4/capital_zhongzhou/city.json'
    city = json.loads(file.read_text())
    inputs = {str(p.relative_to(ROOT)): sha(p.read_bytes()) for p in
              sorted((ROOT / 'blueprints/player_estates').glob('*.json')) +
              sorted((ROOT / 'tools/player_estates').glob('*.py'))}
    inputs.update(owning.source_hashes(json.loads((ROOT/'blueprints/province_capitals/central/game-spec.json').read_text())))
    if city.get('player_estate_build', {}).get('inputs') == inputs:
        # A refresh must verify outputs too; a stale annotation is not a cache hit.
        for ref in [city['terrain'], *city['tiles']]:
            path = resource_file(out, ref.get('resource', ref.get('template')), template='template' in ref)
            if sha(path.read_bytes()) != ref['sha256']:
                raise ValueError('已集成庄园的正式资源摘要不符：' + str(path))
        return city['player_estate_build']
    cache = ROOT / 'out/preview/zhongzhou-west-terraces'
    cfg, placement, estate, source, geometry, _, heights, model, roads, result = construct(cache)
    rule = json.loads(RULES.read_text())
    if rule['city_id'] != city['city_id'] or rule['schema'] != 1:
        raise ValueError('购房规则归属错误')
    world = World(out, city, json.loads((ROOT / 'blueprints/province_capitals/environment_v3/materials.json').read_text()))
    base = cfg['base_y']; x0, z0, x1, z1 = result['selected']['plot']
    retired = set(placement['approved_retirements'])
    rows = [r for r in geometry.rows if r['id'] in retired]
    if len(rows) != len(retired) or any(r['source_kind'] != 'house' or r.get('service') for r in rows):
        raise ValueError('施工撤并清单必须恰好等于获准民居')
    # Every structural cell of neighbouring buildings that shares a touched tile is checked below.
    dirty_regions = [(x0,z0,x1,z1)] + [(r['bounds']['min'][0],r['bounds']['min'][2],
                       r['bounds']['max'][0]+1,r['bounds']['max'][2]+1) for r in rows]
    def in_region(x,z): return any(a<=x<c and b<=z<d for a,b,c,d in dirty_regions)
    touched_tiles={(x//16,z//16) for a,b,c,d in dirty_regions for x in range(a,c) for z in range(b,d)}
    retained = {}
    for r in geometry.rows:
        if r['id'] in retired: continue
        lo, hi = r['bounds']['min'], r['bounds']['max']
        if not any(lo[0]//16<=tx<=hi[0]//16 and lo[2]//16<=tz<=hi[2]//16 for tx,tz in touched_tiles): continue
        for tx in range(lo[0]//16,hi[0]//16+1):
            for tz in range(lo[2]//16,hi[2]//16+1):
                for (lx,y,lz), state in world.tile((tx,tz)).items():
                    x,z=tx*16+lx,tz*16+lz
                    if lo[0]<=x<=hi[0] and lo[2]<=z<=hi[2] and r['position'][1]<=y<=hi[1] and state!=AIR:
                        retained[x,y,z]=state
    # Restore the source hillside after retiring old house floor reservations. The plot is
    # rebuilt from the accepted terraced model, including its explicit room/headroom air.
    touched_columns=set()
    for a,b,c,d in dirty_regions:
        for z in range(b,d):
            for x in range(a,c): touched_columns.add((x,z))
    for x,z in touched_columns:
        world.terrain[z*world.width+x]=int(heights[z,x])
        tile=world.tile((x//16,z//16))
        for p in [p for p in tile if p[0]==x%16 and p[2]==z%16]: del tile[p]
        world.dirty.add((x//16,z//16))
    # Retain existing public approach geometry outside the new parcel, including stair facing.
    for x,z,foot2,face,wood,rid in roads['columns']:
        if (x,z) not in touched_columns or x0<=x<x1 and z0<=z<z1: continue
        y=foot2//2-1
        state='minecraft:spruce_planks' if wood else 'minecraft:stone_bricks'
        if face: state=f'minecraft:stone_brick_stairs[facing={face},half=bottom,shape=straight,waterlogged=false]'
        world.set((x,y,z),state,'保留地块外原巷道')
        if foot2%2: world.set((x,y+1,z),'minecraft:stone_brick_slab[type=bottom,waterlogged=false]','保留地块外半阶')
        for dy in range(3):world.set((x,(foot2+1)//2+dy,z),AIR,'保留地块外原巷净空')
    for z in range(model.c['size'][2]):
        for x in range(model.c['size'][0]):
            wx,wz=x1-1-z,z0+x
            world.terrain[wz*world.width+wx]=base+int(model.terrain[z,x])
    for (x,y,z),state in model.blocks.items():
        world.set((x1-1-z,base+y,z0+x),rotate_state(state,1),'灰瓦庄园')
    for p,state in retained.items():
        if world.get(p)!=state: raise ValueError(f'庄园施工改动保留建筑：{p} {state} -> {world.get(p)}')
    preserved_roads=0
    for x,z,foot2,*_ in roads['columns']:
        if (x,z) not in touched_columns or x0<=x<x1 and z0<=z<z1:continue
        if not world.walkable((x,(foot2+1)//2,z)):raise ValueError('地块外原公共通路缺少支撑或净空')
        preserved_roads+=1
    world.verify_life()
    def entry(point):
        x,z=point
        if (x,z) not in model.path_field:raise ValueError('管家和出口必须消费实际公共巷道标高')
        p=[x1-1-z,base+model.path_field[x,z],z0+x]
        if not world.walkable(tuple(p)):raise ValueError('管家或出口净空不合格：'+str(p))
        return p
    private=[x0+placement['rules']['plot_inset'],z0+placement['rules']['plot_inset'],
             x1-placement['rules']['plot_inset'],z1-placement['rules']['plot_inset']]
    descriptor={k:rule[k] for k in ('id','name','price','access','vertical_scope')}
    descriptor.update(bounds=private,steward={**{k:rule['steward'][k] for k in ('name','yaw')},'position':entry(rule['steward']['plot_point'])},
                      exit=entry(rule['exit_plot_point']))
    for p in (descriptor['steward']['position'],descriptor['exit']):
        if private[0]-.3<p[0]+.5<private[2]+.3 and private[1]-.3<p[2]+.5<private[3]+.3:
            raise ValueError('管家或出口落入私人范围')
    for tile in city['tiles']:
        key=tile['x']//16,tile['z']//16
        if key not in world.dirty:continue
        path=resource_file(out,tile['template'],template=True);entities=block_entities(path)
        cells={p:(s,'estate','overlay') for p,s in world.tile(key).items()}
        count,pal_count,_=write_tile(path,cells,world.height,entities)
        _,saved=read_gzipped_nbt(path);palette=[state_string(v)for v in saved['palette']]
        actual={tuple(v['pos']):palette[v['state']]for v in saved['blocks']}
        if actual!={p:v[0]for p,v in cells.items()}:raise ValueError('庄园正式切片逐坐标回读不一致')
        expected_entities={p:e for p,e in entities.items() if p in cells and cells[p][0].split('[',1)[0]==e['block']}
        if block_entities(path)!=expected_entities:raise ValueError('庄园施工丢失范围外原始方块实体')
        tile.update(block_count=count,palette_count=pal_count,sha256=sha(path.read_bytes()),bytes=path.stat().st_size)
    if world.dirty-set(world.refs):raise ValueError('庄园超出既有中州切片，需要显式新增切片')
    terrain=resource_file(out,city['terrain']['resource'])
    terrain.write_bytes(gzip.compress(struct.pack('<'+'h'*len(world.terrain),*world.terrain),mtime=0))
    city['terrain']['sha256']=sha(terrain.read_bytes())
    city['palette_states']=sorted(set(city['palette_states'])|{rotate_state(s,1) for s in model.blocks.values()})
    city['buildings']=[r for r in city.get('buildings',[]) if r['id'] not in retired]
    city['access_paths']=[r for r in city.get('access_paths',[]) if r.get('building_id') not in retired]
    city['player_estates']=[descriptor]
    city['player_estate_build']={'inputs':inputs,'retired_buildings':sorted(retired),'changed_tiles':len(world.dirty),
          'retained_structural_cells':len(retained),'preserved_road_columns':preserved_roads,'terrain_cut_max':result['terrain_cut_max'],'walkability':result['walkability'],
          'descriptor':descriptor,'native':'not_verified','owner_visual_acceptance':'not_verified'}
    samples=[]
    for key,target in sorted(model.targets.items()):
        if key=='portals':continue
        x,y,z=target
        point=(x1-1-z,base+y,z0+x)
        for dy in (-1,0,1):
            at=(point[0],point[1]+dy,point[2])
            samples.append({'target':key,'position':list(at),'state':world.get(at)})
    city['player_estate_build']['native_samples']=samples
    life_inputs(city,*city['size'],192);write(file,city)
    print('正式中州庄园：'+json.dumps({'tiles':len(world.dirty),'steward':descriptor['steward']['position'],'retired':len(retired)},ensure_ascii=False),flush=True)
    return city['player_estate_build']
