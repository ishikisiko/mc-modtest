#!/usr/bin/env python3
"""从正式八城资源构造传送亭台；保留原 NBT、道路与功能用地。"""
from __future__ import annotations

import argparse
from collections import Counter
import copy
import hashlib
import json
from pathlib import Path
import shutil
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from buildgen.modset import load_modset
from buildgen.nbtread import read_gzipped_nbt, state_string
from province_capitals.environment_v3.world import World, AIR
from province_capitals.game.compile import resource_file, sha, write
from province_capitals.blueprint_plan.nbt_entities import block_entities, write_tile
from province_capitals.blueprint_plan.manor_relayout_export import check_tile

SITES = ROOT / 'blueprints/capital_waystones/sites.json'
MATERIALS = ROOT / 'blueprints/capital_waystones/materials.json'
DIRECTIONS = {'north': (0, -1), 'east': (1, 0), 'south': (0, 1), 'west': (-1, 0)}


def read(path):
    return json.loads(Path(path).read_text())


def block_name(state):
    return state.split('[', 1)[0]


def stair(block, facing):
    return f'{block}[facing={facing},half=bottom,shape=straight,waterlogged=false]'


def source_hashes():
    paths = [SITES, MATERIALS, Path(__file__), ROOT/'blueprints/province_capitals/blueprint_plan/road_surfaces.json',
             ROOT/'tools/province_capitals/environment_v3/world.py', ROOT/'tools/province_capitals/blueprint_plan/nbt_entities.py']
    return {str(p.relative_to(ROOT)): sha(p.read_bytes()) for p in paths}


def approach_columns(site):
    """每个中心线节点保留自己的标高；转角的三格路幅由最近节点确定。"""
    columns = {}
    for i, (x, y, z) in enumerate(site['approach']):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                p = (x + dx, z + dz)
                choice = (abs(dx) + abs(dz), i, y)
                if p not in columns or choice[:2] < columns[p][:2]:
                    columns[p] = choice
    return {p: v[2] for p, v in columns.items()}


class Builder:
    def __init__(self, root, manifest, materials, constraints):
        if sha(resource_file(root,manifest['terrain']['resource']).read_bytes()) != manifest['terrain']['sha256']:raise ValueError('原地形摘要不符')
        self.world = World(root, manifest, {'collision': {'headroom': 3}})
        self.materials = materials
        self.rules = constraints
        self.original = {}
        self.owner = {}
        self.edits = {}
        self.rows = []
        self.natural = set(materials['natural_cut_blocks']) | {manifest['terrain'][k] for k in ('top_block','fill_block','subsurface_block')}
        self.road_blocks = set(materials['central_road_blocks'])
        surfaces = read(ROOT/'blueprints/province_capitals/blueprint_plan/road_surfaces.json')
        def collect(value):
            if isinstance(value,str) and value.startswith(('minecraft:','myvillage:')):self.road_blocks.add(block_name(value))
            elif isinstance(value,dict):
                for item in value.values():collect(item)
            elif isinstance(value,list):
                for item in value:collect(item)
        collect(surfaces)
        self.plants = set(materials['replaceable_plants'])
        self.entities = {}

    def old(self, p):
        if p not in self.original:
            self.original[p] = self.world.get(p)
        return self.original[p]

    def put(self, p, state, site, *, ground=False):
        x, y, z = p
        if not (0 <= x < self.world.width and 0 <= y < self.world.height and 0 <= z < self.world.depth):
            raise ValueError(f'{site}: 构件越出城市体积 {p}')
        old = self.old(p)
        if old != state and old != AIR and block_name(old) not in self.plants:
            if not ground or block_name(old) not in self.natural:
                raise ValueError(f'{site}: 施工将覆盖原构件 {p} {old} -> {state}')
            if y > self.world.ground(x,z) or self.world.ground(x,z)-y > self.rules['local_earthwork_limit']:
                raise ValueError(f'{site}: 天然土层整地超出范围 {p}')
        if p in self.owner and self.owner[p] != site and self.edits[p] != state:
            raise ValueError(f'{site}: 与另一传送点发生写入冲突 {p}')
        if state != old:
            self.edits[p] = state
            self.owner[p] = site
            self.world.set(p, state, site)

    def floor(self, x, y, z, state, site):
        """在站立高度下铺地；支承只在本地整地限额内补到原有实体。"""
        limit = self.rules['local_earthwork_limit']
        self.put((x, y - 1, z), state, site, ground=True)
        for yy in range(y - 2, max(-1, y - limit - 3), -1):
            old = self.old((x, yy, z))
            if old != AIR and block_name(old) not in self.plants:
                if 'water' in old or 'lava' in old:
                    raise ValueError(f'{site}: 支承进入水体 {x, yy, z}')
                break
            self.put((x, yy, z), state, site, ground=True)
        else:
            raise ValueError(f'{site}: 支承超出局部整地范围 {x, y, z}')

    def clear(self, x, y, z, top, site):
        for yy in range(y, top + 1):
            self.put((x, yy, z), AIR, site, ground=True)

    def build(self, city, site):
        sid = site['id']; x, y, z = site['center']; n = site['size']; r = n // 2
        palette = self.materials['regions'][city['id']]; cfg = self.materials['rules']
        expected = [x-r, z-r, x+r, z+r]
        if site['bounds'] != expected or n not in self.rules['allowed_sizes']:
            raise ValueError(sid + ': 点位尺寸不符')
        if site['kind'] not in ('array', 'pavilion') or site['facing'] not in DIRECTIONS:
            raise ValueError(sid + ': 未知亭台或朝向')
        if self.rules['approach_width'] != 3 or self.rules['arrival_square'] != 5:
            raise ValueError('当前构件只实现三格接路和五格到达区')
        roof_base = y + cfg['roof_base_y']; max_y = roof_base + r - 1
        for dx in range(-r, r+1):
            for dz in range(-r, r+1):
                state = palette['edge'] if max(abs(dx), abs(dz)) == r else palette['rune'] if max(abs(dx), abs(dz)) == 2 or abs(dx)+abs(dz) == r else palette['floor']
                self.clear(x+dx, y, z+dz, max_y, sid)
                self.floor(x+dx, y, z+dz, state, sid)
        # 中央五格保持四格净空，实际石碑由可选适配在同一生成区块内放置。
        for dx in (-r, r):
            for dz in (-r, r):
                self.put((x+dx, y, z+dz), palette['edge'], sid)
                self.put((x+dx, y+1, z+dz), 'minecraft:lantern[hanging=false,waterlogged=false]', sid)
        turn = ('south', 'west', 'north', 'east').index(site['facing'])
        def oriented(dx, dz):
            for _ in range(turn): dx, dz = -dz, dx
            return x+dx, z+dz
        for dx in (-r+1, r-1):
            for dz in (-1, 0, 1):
                xx, zz = oriented(dx, dz)
                self.put((xx, y, zz), palette['bench'], sid)
        if site['kind'] == 'pavilion':
            a = r-1
            for dx in (-a, a):
                for dz in (-a, a):
                    for dy in range(cfg['pavilion_column_height']):
                        self.put((x+dx, y+dy, z+dz), palette['column'], sid)
            for dx in range(-a, a+1):
                for dz in range(-a, a+1):
                    if max(abs(dx), abs(dz)) == a:
                        self.put((x+dx, roof_base-1, z+dz), palette['beam'], sid)
            for tier in range(r):
                a = r-1-tier
                for dx in range(-a, a+1):
                    for dz in range(-a, a+1):
                        if a and max(abs(dx), abs(dz)) != a:continue
                        face = 'east' if dx == -a else 'west' if dx == a else 'south' if dz == -a else 'north'
                        value = palette['roof'] if a == 0 or abs(dx) == abs(dz) else stair(palette['roof_stairs'], face)
                        self.put((x+dx, roof_base+tier, z+dz), value, sid)
        else:
            for dx in (-r+1, r-1):
                for dz in (-r+1, r-1):
                    self.put((x+dx, y, z+dz), palette['edge'], sid)
                    self.put((x+dx, y+1, z+dz), palette['edge'], sid)
        columns = approach_columns(site)
        protected = set()
        for (xx, zz), yy in list(columns.items()):
            if x-r <= xx <= x+r and z-r <= zz <= z+r:continue
            # 道路横坡和低半砖的首个整格空气可能与中心线差一格；保留整个原铺装列。
            existing = next((floor_y+1 for floor_y in range(yy,yy-3,-1)
                             if block_name(self.old((xx,floor_y,zz))) in self.road_blocks
                             and all(self.old((xx,floor_y+dy,zz)) == AIR for dy in range(1,4))), None)
            if existing is not None:
                columns[xx,zz] = existing
                protected.add((xx, zz));continue
            self.clear(xx, yy, zz, yy+cfg['arrival_clearance']-1, sid)
            self.floor(xx, yy, zz, palette['floor'], sid)
        for (xx, zz), yy in columns.items():
            if (xx, zz) in protected or (x-r <= xx <= x+r and z-r <= zz <= z+r):continue
            lower = [(dx, dz) for dx, dz in DIRECTIONS.values() if columns.get((xx+dx, zz+dz)) == yy-1]
            if lower:
                dx, dz = lower[0]
                face = 'west' if dx == 1 else 'east' if dx == -1 else 'north' if dz == 1 else 'south'
                self.put((xx, yy-1, zz), stair(palette['stairs'], face), sid, ground=True)
        # 接路不得把灯柱或边座放到入口的三格净宽内。
        for xx, yy, zz in site['approach']:
            if not self.world.walkable((xx, yy, zz)):
                raise ValueError(f'{sid}: 真实方块接路不可站立 {xx, yy, zz}')
        for a, b in zip(site['approach'], site['approach'][1:]):
            if abs(a[0]-b[0])+abs(a[2]-b[2]) != 1 or abs(a[1]-b[1]) > self.rules['step_limit']:
                raise ValueError(sid + ': 接路步长或高差越界')
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if not self.world.walkable((x+dx, y, z+dz)) or any(self.world.get((x+dx, y+dy, z+dz)) != AIR for dy in range(cfg['arrival_clearance'])):
                    raise ValueError(sid + ': 中央到达空间不完整')
        row = {'id': sid, 'name': city['short_name']+'·'+site['name'], 'position': site['center'], 'facing': site['facing'], 'block': palette['waystone']}
        self.rows.append(row)
        return {'id':sid, 'bounds':site['bounds'], 'position':site['center'], 'arrival_clearance':cfg['arrival_clearance'], 'preserved_road_columns':len(protected), 'approach':site['approach'], 'status':'pass'}

    def export(self, root):
        manifest = self.world.manifest
        refs = {tuple((t['x']//16, t['z']//16)):copy.deepcopy(t) for t in manifest['tiles']}
        changed = []
        for key in sorted(self.world.dirty):
            tx,tz=key
            path = resource_file(root, refs[key]['template'], template=True) if key in refs else Path(root)/f'data/myvillage/structure/province_capitals/v4/{manifest["city_id"]}/tile_{tx}_{tz}.nbt'
            if key in refs and sha(path.read_bytes()) != refs[key]['sha256']:raise ValueError('原分块摘要不符 '+str(path))
            entities = block_entities(path) if path.exists() else {}
            for p,row in entities.items():
                if block_name(self.world.tile(key)[p]) != row['block']:
                    raise ValueError('施工覆盖方块实体 '+str((key,p)))
            values = {p:(state,'waystone_station','building') for p,state in self.world.tile(key).items()}
            write_tile(path, values, self.world.height, entities)
            check_tile(path, values)
            if block_entities(path) != entities:raise ValueError('原方块实体 NBT 被改动 '+str(path))
            refs[key]={'x':tx*16,'z':tz*16,'template':f'myvillage:province_capitals/v4/{manifest["city_id"]}/'+path.stem,'sha256':sha(path.read_bytes()),'bytes':path.stat().st_size,'block_count':len(values),'palette_count':len({s[0] for s in values.values()})}
            changed.append({'tile':[tx,tz], 'cells':sum(1 for x,y,z in self.edits if (x//16,z//16)==key), 'preserved_block_entities':len(entities)})
        manifest['tiles'] = [refs[k] for k in sorted(refs,key=lambda p:(p[1],p[0]))]
        manifest['waystones'] = self.rows
        manifest.setdefault('source',{})['capital_waystones']={'hashes':source_hashes(),'changed_tiles':len(changed)}
        manifest['palette_states'] = sorted(set(manifest.get('palette_states',[])) | set(self.edits.values()))
        write(Path(root)/f'data/myvillage/capitals/v4/{manifest["city_id"]}/city.json', manifest)
        return changed


def generate(resources, *, out=None):
    if out is not None:
        if Path(out).exists():raise ValueError('输出目录已存在，避免覆盖上一轮证据')
        shutil.copytree(resources,out);resources=out
    resources=Path(resources);config=read(SITES);materials=read(MATERIALS);hashes=source_hashes()
    catalog=read(ROOT/'blueprints/province_capitals/blueprint_plan/catalog.json')
    if {c['id'] for c in config['cities']} != {c['region'] for c in catalog['cities']}:raise ValueError('传送点输入必须覆盖八城')
    errors=load_modset('vanilla').palette_block_errors([s for row in materials['regions'].values() for k,s in row.items() if k not in ('waystone','stairs','roof_stairs')])
    if errors:raise ValueError('亭台材料未注册 '+str(errors))
    report={'schema':1,'selected':[], 'completed':[], 'failed':[], 'cities':{}, 'source_hashes':hashes, 'runtime':'not_run'}
    for city in config['cities']:
        region=city['id'];report['selected'].extend(s['id'] for s in city['sites'])
        manifest=read(resources/f'data/myvillage/capitals/v4/capital_{region}/city.json')
        if manifest.get('waystones'):raise ValueError('请从未附加传送亭台的正式八城资源生成，不能重复修补')
        builder=Builder(resources,manifest,materials,config['constraints'])
        result=[]
        for site in city['sites']:
            try:result.append(builder.build(city,site))
            except Exception as e:
                report['failed'].append({'id':site['id'],'error':str(e)});write(resources.parent/(resources.name+'-waystones-report.json'),report);raise
            report['completed'].append(site['id'])
        edits=builder.export(resources);report['cities'][region]={'sites':result,'changed_tiles':edits,'edited_cells':len(builder.edits)}
    if hashes!=source_hashes():raise ValueError('传送亭台生成期间输入改变')
    report['status']='pass';write(resources.parent/(resources.name+'-waystones-report.json'),report)
    print(json.dumps({'status':'pass','cities':len(report['cities']),'stations':len(report['completed']),'edited_cells':sum(r['edited_cells'] for r in report['cities'].values())},ensure_ascii=False))
    return report


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--resources',type=Path,required=True);parser.add_argument('--out',type=Path);args=parser.parse_args();generate(args.resources,out=args.out)
