"""中州蓝图适配：从原作模板恢复逐栋几何，地形与稀疏切片保持原身份。"""
from __future__ import annotations

from collections import Counter, OrderedDict, deque
from collections.abc import MutableMapping
import copy
from functools import lru_cache
import hashlib
import json
import math
import os
from pathlib import Path
import pickle
import subprocess
import tempfile
from types import SimpleNamespace

from .generate import ROOT, Planner, environment_input, read, rect, runs, perimeter, search_path, digest
from province_capitals.central import export_game as owning
from province_capitals.central.game_models import rotate_point, rotate_state
from province_capitals.central.game_world import World as OriginalWorld
from province_capitals.environment_v3.world import AIR
from province_capitals.game.compile import resource_file
from province_capitals.environment_design.geometry import Scene
from province_capitals.regional.v2.common import DIR
from province_capitals.regional.v2.preview import COLORS, SceneBuilder


class TileCache(MutableMapping):
    """可修改字典的有界逐 tile 缓存；换出时保存修改，绝不展开三维山基。"""
    def __init__(self, folder, capacity):
        self.folder = Path(folder); self.folder.mkdir(parents=True, exist_ok=True)
        self.capacity = capacity; self.hot = OrderedDict(); self.keys_on_disk = set()
        self.peak_tiles = 0

    def __contains__(self, key):
        return key in self.hot or key in self.keys_on_disk

    def __getitem__(self, key):
        if key in self.hot:
            value = self.hot.pop(key); self.hot[key] = value
            return value
        if key not in self.keys_on_disk:
            raise KeyError(key)
        with self.path(key).open('rb') as stream:
            value = pickle.load(stream)
        self[key] = value
        return value

    def path(self, key):
        return self.folder / f'{key[0]}_{key[1]}.pickle'

    def __setitem__(self, key, value):
        self.hot.pop(key, None); self.hot[key] = value
        while len(self.hot) > self.capacity:
            old, cells = self.hot.popitem(last=False)
            with self.path(old).open('wb') as stream:
                pickle.dump(cells, stream, protocol=5)
            self.keys_on_disk.add(old)
        self.peak_tiles = max(self.peak_tiles, len(self.hot))

    def __delitem__(self, key):
        if key not in self: raise KeyError(key)
        self.hot.pop(key, None); self.keys_on_disk.discard(key)
        self.path(key).unlink(missing_ok=True)

    def __iter__(self):
        return iter(set(self.hot) | self.keys_on_disk)

    def __len__(self):
        return len(set(self.hot) | self.keys_on_disk)


def _sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


@lru_cache(maxsize=2)
def _source(seed, fingerprint):
    """只执行原作布局和目录；不调用原作的整城微体素构建。"""
    script = """
import {buildPlan} from './web/yunshan-city/src/world/plan.js';
import {makeCatalog} from './web/yunshan-city/src/voxel/catalog.js';
import {TM} from './web/yunshan-city/src/world/materials.js';
const plan=buildPlan(SEED),defs={...makeCatalog(SEED).defs,...plan.uniques};
const kinds=new Set(KINDS);
const buildings=plan.instances.flatMap((b,i)=>kinds.has(defs[b.id]?.kind)?[{...b,source_instance_index:i}]:[]);
process.stdout.write(JSON.stringify({defs,buildings,features:plan.features,cityPoly:plan.cityPoly,paths:plan.paths,market:plan.market,temple:plan.temple,TM}));
""".replace('SEED', str(seed)).replace('KINDS', json.dumps(list(read(ROOT/'blueprints/province_capitals/central/game-spec.json')['templates']['kind_builders'])))
    result = subprocess.run(['node', '--input-type=module', '-e', script], cwd=ROOT,
                            capture_output=True, text=True, check=True)
    return json.loads(result.stdout)


class GeometryWorld:
    def __init__(self, city):
        self.offset = city['bounds']['source_offset']; self.footprints = {}; self.overlaps = []
        self.occupied = {}; self.ordinals = {}

    def local(self, p):
        return tuple(round(p[i]-self.offset[i]) for i in range(3))

    def reserve(self, row, model, transform):
        cells = {(transform((x, 0, z))[0], transform((x, 0, z))[2]) for x,z in model.floor_footprint}
        self.footprints[row['id']] = cells; self.ordinals[row['ordinal']] = row['id']
        for p in sorted(cells):
            other = self.occupied.get(p)
            if other:
                allowed = self.ordinals.get(row.get('shared_foundation_ordinal'))
                if other != allowed:
                    raise ValueError(f'原作未声明地基相交：{row["id"]} / {other} {p}')
                self.overlaps.append({'column': list(p), 'owners': [other, row['id']], 'reason': row['compound']['note']})
            else:
                self.occupied[p] = row['id']


def geometry(city):
    hashes = owning.source_hashes(city)
    source = _source(city['seed'], digest(hashes))
    world = GeometryWorld(city)
    factory = owning.Factory(ROOT, city)
    adapt = owning.adapter(read(ROOT/city['height_profile_source']))
    rows, models, transforms = owning.prepare_buildings(city, source, world, factory, adapt)
    for row, model, transform in zip(rows, models, transforms):
        if row['id'] not in world.footprints:
            # 桥梁的 floor_footprint 原本为空；取真实桥面构件，不能用桥顶包围盒填假矩形。
            cells = {(transform(p)[0], transform(p)[2]) for p,s in model.cells.items() if s != AIR}
            world.footprints[row['id']] = cells
            for p in cells:
                if p in world.occupied:
                    world.overlaps.append({'column':list(p),'owners':[world.occupied[p],row['id']],
                        'kind':'source_bridge_landing','reason':'原作桥头与既有亭台基础的实际共享列；须在蓝图逐对声明。'})
                else:world.occupied[p] = row['id']
    return SimpleNamespace(source=source, world=world, factory=factory, adapt=adapt,
                           rows=rows, models=models, transforms=transforms, hashes=hashes)


def original_road_columns(spec, g, route_data, *, include_walls=False):
    """重放原道路发射条件，区分真实路面和被水/地基/城墙排除的声明路幅。"""
    city=read(ROOT/spec['sources']['city'])
    folder=ROOT/spec['central']['cache_directory']/'source'/digest(g.hashes)
    stamp=folder/'identity.json'
    names=('source.json','terrain.bin.gz','water.bin.gz','materials.bin.gz')
    valid=stamp.exists()
    if valid:
        identity=read(stamp)
        valid=identity.get('inputs')==g.hashes and all((folder/n).exists() and _sha(folder/n)==identity['outputs'].get(n) for n in names)
    if not valid:
        folder.mkdir(parents=True,exist_ok=True)
        with (folder/'source-export.log').open('w') as stream:
            subprocess.run(['node',str(ROOT/'tools/province_capitals/central/source_game.mjs'),'--out',str(folder)],
                           cwd=ROOT,stdout=stream,stderr=subprocess.STDOUT,check=True)
        if owning.source_hashes(city)!=g.hashes:raise ValueError('原路掩码生成期间 authored 输入变化')
        stamp.write_text(json.dumps({'inputs':g.hashes,'outputs':{n:_sha(folder/n) for n in names}},sort_keys=True))
    source=read(folder/'source.json')
    original=OriginalWorld(city,source,folder,g.adapt,None)
    for row,model,transform in zip(g.rows,g.models,g.transforms):
        if row['source_kind'] not in ('archbridge','langqiao'):original.reserve(row,model,transform)
    original.prepare_walls()
    for route in route_data['routes']:
        # routes.json.width 是已量化的 2*radius+1。原 API 收到的是量化前
        # width，内部 round(width/2)-1；用偶数 2*(radius+1) 精确还原，
        # 直接回传奇数会因 Python 偶数舍入丢掉一圈真实路幅。
        original.add_path(route['id'],route['points'],route['width']+1,wood=route['surface']=='wood_piers',local=True)
    roads={(index%original.w,index//original.w):data for index,data in original.roads.items()}
    if include_walls:
        return roads,{(index%original.w,index//original.w):data for index,data in original.wallmap.items()}
    return roads


def _details(world, key):
    entry = world.manifest['details'][key]
    path = resource_file(world.root, entry['resource'])
    if _sha(path) != entry['sha256']:
        raise ValueError('中州原作详细记录摘要不符：'+key)
    return read(path)


def prepare_world(spec, world):
    """共享导出钩子：补充真实元数据并限制缓存；不改基础地形或切片。"""
    if world.manifest['city_id'] != spec['city_id'] or spec['backend'] != 'central':
        raise ValueError('中州蓝图身份不一致')
    city = read(ROOT/spec['sources']['city'])
    for name, expected in world.manifest['source']['hashes'].items():
        if _sha(ROOT/name) != expected:
            raise ValueError('中州基础资源源身份过期：'+name)
    g = geometry(city)
    declared={frozenset(r['owners']):r['reason'] for r in spec.get('shared_infrastructure',[])}
    for overlap in g.world.overlaps:
        if overlap.get('kind')=='source_bridge_landing':
            pair=frozenset(overlap['owners'])
            if pair not in declared:raise ValueError('桥头共享地块尚未明确归属：'+str(overlap))
            overlap['reason']=declared[pair]
    detailed = _details(world, 'buildings')['buildings']
    if detailed != g.rows:
        raise ValueError('中州原作模板重建与认证 buildings.json 不一致')
    route_data = _details(world, 'routes')
    routes = {r['id']: copy.deepcopy(r) for r in route_data['routes']}
    world.manifest['buildings'] = copy.deepcopy(detailed)
    for b in world.manifest['buildings']:
        b['source_base_position']=b['position'][:]
        b['position'][1]+=1
        b['position_semantics']='首层空气；原作基础实体格保留于 source_base_position'
    # half-slab 的原作脚面可以是半整数；保护器按上方第一格空气查询。
    for route in routes.values():
        route['source_points'] = route['points']
        route['points'] = [[x, math.ceil(y), z] for x,y,z in route['points']]
    world.manifest['roads'] = list(routes.values())
    world.manifest['access_paths'] = [{**copy.deepcopy(a), 'points': routes['access_'+a['building_id']]['points']}
                                      for a in route_data['access']]
    world.central_geometry = g
    world.central_routes = route_data
    cfg = spec['central']
    cache_root = ROOT/cfg['cache_directory']; cache_root.mkdir(parents=True, exist_ok=True)
    world.central_cache = tempfile.TemporaryDirectory(prefix='tiles-', dir=cache_root)
    world.cache = TileCache(Path(world.central_cache.name)/'working', cfg['cache_tiles'])
    world.originals = TileCache(Path(world.central_cache.name)/'original', cfg['cache_tiles'])
    roads,wallmap=original_road_columns(spec,g,route_data,include_walls=True)
    world.central_original_road_columns=roads
    # 共享包围盒保护从首层空气起算；中州额外保留真实模板的所有实体，包含
    # 地板与桥墩，不把屋檐投影下的自然草地误算成模板构件。
    structural={};rewrites=world.config['inherited_state_rewrites']
    for model,transform in zip(g.models,g.transforms):
        for p,state in model.cells.items():
            if state==AIR:continue
            wp=transform(p);actual=world.get(wp)
            if actual!=AIR:structural[wp]=rewrites.get(actual,actual)
    wall_cells=0
    for (x,z),(top,height,kind) in sorted(wallmap.items(),key=lambda row:(row[0][1]//16,row[0][0]//16,row[0])):
        base=min(world.ground(x,z),top-height)
        for y in range(max(0,base+1),min(world.height,top+3)):
            pos=x,y,z;actual=world.get(pos)
            if actual!=AIR:
                structural[pos]=rewrites.get(actual,actual);wall_cells+=1
    world.central_wall_cells=wall_cells
    world.central_structural_cells=structural


def verify_applied_world(world,data):
    """真实模板实体保留检查；地板/桥墩也不能因首层空气元数据被漏掉。"""
    allowed={tuple(d['position'][:1]+[d['position'][1]+dy]+d['position'][2:])
             for d in data.get('doors',[]) if d['action']=='open_door' for dy in range(d['headroom'])}
    allowed.update(tuple(p) for opening in data.get('public_passages',[]) for p in opening['cells'])
    errors=[]
    for p,old in world.central_structural_cells.items():
        if p not in allowed and world.get(p)!=old:
            errors.append({'position':list(p),'before':old,'after':world.get(p),'reason':world.edit_source.get(p)})
            if len(errors)==16:break
    if errors:raise ValueError('中州真实模板实体被越界改写：'+json.dumps(errors,ensure_ascii=False))
    result={'preserved_structural_cells':len(world.central_structural_cells)-len(allowed & world.central_structural_cells.keys()),
            'declared_opening_cells':len(allowed),'status':'pass'}
    if data.get('road_adjustments'):
        result['roads']=verify_road_adjustments(world,data)
    if data.get('entry_doors'):
        result['entry_doors']=verify_entry_doors(world,world.central_planner,data)
    if data.get('road_rules',{}).get('require_city_network'):
        primary=road_network_audit(world,world.central_planner,allow_door_interactions=False,use_primary_entries=True)
        retained=road_network_audit(world,world.central_planner)
        data['primary_road_network_audit']=primary;data['road_network_audit']=retained
        if primary['unreachable_entries'] or retained['unreachable_entries']:
            raise ValueError('全城实际入口网络仍有断路：'+json.dumps({'primary':primary,'retained':retained},ensure_ascii=False))
        result['city_network']={'primary_outdoor_entries':primary['connected_access_entries'],
                                'retained_source_entries':retained['connected_access_entries'],'status':'pass'}
    return result


def _road_surface(state):
    return (state!=AIR and not state.startswith(('minecraft:water','minecraft:lava'))
            and not any(k in state for k in ('deepslate_tile','dark_prismarine','sandstone_stairs','sandstone_slab')))


def _segment_columns(segment):
    a,b=segment['a'],segment['b'];width=segment['width'];offsets=segment['lateral_offsets']
    if any(len(p)!=3 or any(type(v)is not int for v in p) for p in (a,b)):
        raise ValueError('道路端点必须是三维整数格')
    if (width not in (1,2,3) or len(offsets)!=width or offsets!=list(range(min(offsets),max(offsets)+1))
            or 0 not in offsets or min(offsets)<-1 or max(offsets)>1):
        raise ValueError('道路分段宽度或横向偏移无效')
    if abs(a[0]-b[0])+abs(a[2]-b[2])!=1 or abs(a[1]-b[1])>1:
        raise ValueError('绕檐路线不连续或超过逐格一级台阶')
    dx=b[0]-a[0];dz=b[2]-a[2]
    return {(v[0]+(k if dz else 0),v[2]+(k if dx else 0)) for v in (a,b) for k in offsets}


def _validate_road_adjustments(planner):
    """只消费明确 authored 路线；不在正式编译时临时缩口径或删除旧失败。"""
    world=planner.block_world;spec=planner.spec
    sources={r['id']:r for r in world.central_routes['routes']}
    original={tuple(row) for row in planner.original_road_unavailable}
    assigned=set();ids=set();overlay={};earth={};walk={};result=[]
    rules=spec.get('road_rules',{})
    if spec.get('road_adjustments') and (rules.get('headroom')!=spec['rules']['headroom'] or
             rules.get('local_earthwork_limit')!=spec['rules']['local_earthwork_limit']):
        raise ValueError('道路净空/整地上限必须与整城规则一致')
    if spec.get('road_adjustments') and rules.get('original_failure_sha256')!=digest(planner.original_road_unavailable):
        raise ValueError('原始道路失败清单身份改变，须重新设计而非静默删格')
    materials=spec['minecraft']['materials']
    if spec.get('network_baseline'):
        core=copy.deepcopy(spec);core.pop('network_baseline');core.pop('entry_doors',None)
        core['road_adjustments']=[r for r in core['road_adjustments'] if r.get('kind') not in ('network_connection','entry_connection')]
        core['road_rules'].pop('require_city_network',None)
        if digest(core)!=spec['network_baseline']['core_spec_fingerprint']:
            raise ValueError('街坊连接的原蓝图基线已改变，不能沿用旧断路证据')
    for authored in spec.get('road_adjustments',[]):
        row=copy.deepcopy(authored);rid=row['route_id'];aid=row['id']
        network=row.get('kind') in ('network_connection','entry_connection')
        if aid in ids or (not network and rid not in sources):raise ValueError('道路修正 ID 重复或引用不存在的原路')
        ids.add(aid)
        if not row.get('reason'):raise ValueError('道路修正必须说明实际设计理由：'+aid)
        nominal=row['target_width'] if network else row['original_width']
        if nominal!=3:raise ValueError('本轮修正/新增连接以三格为目标，不得窄化公共主干道')
        if network:
            baseline=spec.get('network_baseline',{})
            if row.get('source_network_sha256')!=digest(baseline):raise ValueError('分离路网基线身份不符：'+aid)
            if row['retired_failed_cells']:raise ValueError('新增街坊连接不能冒领原740失败格')
            gap=row['network_gap']['endpoint_gap']
            if row['start']!=gap['a'] or row['end']!=gap['b'] or gap['components'][0]==gap['components'][1]:
                raise ValueError('街坊连接没有对应原分离分区的真实接点：'+aid)
            if row.get('kind')=='entry_connection':
                door=next((d for d in spec.get('entry_doors',[]) if d['id']==row.get('entry_door_id')),None)
                if door is None or row['start']!=door['outside'] or row.get('owner')!=door['owner']:
                    raise ValueError('新入口接路与明确门洞不对应：'+aid)
            required={a['building_id'] for a in world.central_routes['access']}
            if not set(row['network_gap']['affected_entries'])<=required:raise ValueError('街坊连接引用了不存在的入口')
            failures=set();replaced=None
        else:
            if row['original_width']!=sources[rid]['width']:
                raise ValueError('道路修正缺原宽度：'+aid)
            if row.get('source_route_sha256')!=digest(sources[rid]):raise ValueError('被替代原路线身份不符：'+aid)
            replaced=row['replaced_source_segment'];lo,hi=replaced['first_index'],replaced['last_index']
            if not(0<=lo<hi<len(sources[rid]['points'])) or replaced['points']!=sources[rid]['points'][lo:hi+1] or replaced['width']!=row['original_width']:
                raise ValueError('被明确替代的原路段记录不完整：'+aid)
            failures={(*p,rid) for p in row['retired_failed_cells']}
            if not failures or not failures<=original or failures & assigned:
                raise ValueError('旧失败格遗漏身份、重复归属或伪造：'+aid)
            if any(not any(abs(p[0]-q[0])<=1 and abs(p[2]-q[2])<=1 for q in replaced['points']) for p in row['retired_failed_cells']):
                raise ValueError('旧失败不在明确替代路段内：'+aid)
        assigned |= failures
        points=row['points'];segments=row['segments']
        if len(points)<2 or len(segments)!=len(points)-1 or row['start']!=points[0] or row['end']!=points[-1]:
            raise ValueError('道路修正端点/分段数量不符：'+aid)
        lanes={}
        for i,segment in enumerate(segments):
            if segment['a']!=points[i] or segment['b']!=points[i+1]:raise ValueError('道路分段脱离连续中心线：'+aid)
            columns=_segment_columns(segment)
            declared={tuple(p) for p in segment['cells']}
            if {(x,z) for x,y,z in declared}!=columns or len(declared)!=len(columns):
                raise ValueError('完整路幅缺格或叠高：'+aid)
            heights={(x,z):y for x,y,z in declared}
            a,b=segment['a'],segment['b'];dx,dz=b[0]-a[0],b[2]-a[2]
            for endpoint in (a,b):
                if heights[endpoint[0],endpoint[2]]!=endpoint[1]:raise ValueError('路幅中心高度不符：'+aid)
                ys=[heights[endpoint[0]+(k if dz else 0),endpoint[2]+(k if dx else 0)] for k in segment['lateral_offsets']]
                if any(abs(y-endpoint[1])>1 for y in ys) or any(abs(v-u)>1 for u,v in zip(ys,ys[1:])):
                    raise ValueError('路幅横断面超过一级高差：'+aid)
            for k in segment['lateral_offsets']:
                ca=(a[0]+(k if dz else 0),a[2]+(k if dx else 0));cb=(b[0]+(k if dz else 0),b[2]+(k if dx else 0))
                if abs(heights[ca]-heights[cb])>1:raise ValueError('路幅侧边存在纵向断阶：'+aid)
            if segment['width']<nominal:
                if not row.get('narrowing_reason') or not segment.get('narrowing_reason'):
                    raise ValueError('窄段未明确说明实际檐口/错台限制：'+aid)
                if not network and not rid.startswith('access_') and (row.get('semantic')!='secondary_public_path' or segment['width']<2):
                    raise ValueError('公共道路只允许明确次级支路的双格段：'+aid)
            for x,y,z in declared:
                if (x,z) in lanes and lanes[x,z]!=y:raise ValueError('同一路幅列出现两个高度：'+aid)
                if (x,z) in walk and walk[x,z]!=y:raise ValueError('两条修正道路在不同高度争用同一列：'+aid)
                lanes[x,z]=y;walk[x,z]=y
        if {(x,y,z) for (x,z),y in lanes.items()}!={tuple(p) for p in row['walkpoints']}:
            raise ValueError('道路可走格清单与逐段完整路幅不符：'+aid)
        if not network and rid.startswith('access_'):
            source_access=next(a for a in world.central_routes['access'] if a['building_id']==rid.removeprefix('access_'))
            if row['start']!=source_access['entry']:raise ValueError('入户修正丢失原真实入口：'+aid)
            target=source_access['street_target']
            if abs(row['end'][0]-target[0])+abs(row['end'][2]-target[2])>rules['tie_in_radius']:
                raise ValueError('入户道路没有接回原街路邻域：'+aid)
        elif not network and any((row[key][0],row[key][2])!=(replaced['points'][index][0],replaced['points'][index][2]) for key,index in (('start',0),('end',-1))):
            raise ValueError('公共道路修正端点离开原路线：'+aid)
        for point in (tuple(row['start']),tuple(row['end'])):
            at=lambda dy:overlay.get((point[0],point[1]+dy,point[2]),world.get((point[0],point[1]+dy,point[2])))
            if not _road_surface(at(-1)) or not all(world.passable(at(dy)) for dy in range(rules['headroom'])):
                raise ValueError('修正道路没有接回既有可站立端点：'+aid+' '+str(point))
        raised={tuple(r['column']):r for r in row.get('raised_columns',[])}
        if len(raised)!=len(row.get('raised_columns',[])):raise ValueError('架空路面列重复：'+aid)
        for c,r in raised.items():
            ground=planner.original_terrain[c[1]*world.width+c[0]]
            if c not in lanes or r['air_y']!=lanes[c] or r['ground_y']!=ground or not(rules['local_earthwork_limit']<r['air_y']-ground-1<=rules['maximum_supported_rise']):
                raise ValueError('架空路面的地形身份、范围或高度不符：'+aid)
        for pier in row.get('piers',[]):
            c=tuple(pier['column'])
            if c not in raised or pier['ground_y']!=raised[c]['ground_y'] or pier['deck_y']!=raised[c]['air_y']-1:
                raise ValueError('支墩没有对应真实架空路面：'+aid)
            expected=[[c[0],y,c[1]] for y in range(pier['ground_y']+1,pier['deck_y'])]
            if pier['blocks']!=expected:raise ValueError('支墩必须连续落到原地面：'+aid)
        for e in row['earthwork']:
            c=tuple(e['column'])
            if c not in lanes or e['air_y']!=lanes[c] or e['before_air_y']!=planner.original_terrain[c[1]*world.width+c[0]]+1:
                raise ValueError('道路整地列没有真实来源或超出路幅：'+aid)
            if c in raised:raise ValueError('不得把架空支墩伪装成地形填平：'+aid)
            if abs(e['air_y']-e['before_air_y'])>rules['local_earthwork_limit']:raise ValueError('道路整地超过上限：'+aid)
            if c in earth and earth[c]['air_y']!=e['air_y']:raise ValueError('道路整地互相冲突：'+aid)
            earth[c]={**e,'owner':aid}
        for edit in row['edits']:
            pos=tuple(edit['position']);x,y,z=pos;column=x,z
            if any(type(v)is not int for v in pos) or not(0<=x<world.width and 0<=z<world.depth and 0<=y<world.height):
                raise ValueError('道路施工坐标越界：'+aid)
            if column not in lanes:raise ValueError('道路施工超出完整路幅：'+aid)
            floor=lanes[column]-1;ground=planner.original_terrain[z*world.width+x]
            if not(min(ground+1,floor)<=y<=floor+rules['headroom']):raise ValueError('道路施工超出声明纵向范围：'+aid)
            actual=overlay.get(pos,world.get(pos))
            if actual!=edit['before']:raise ValueError('道路施工前置方块与认证底座不符：'+aid+' '+str(pos))
            if edit['after']!=AIR and edit['after']!=materials['path'] and not edit['after'].startswith(materials['stairs']+'['):
                raise ValueError('道路施工使用未声明材料：'+aid)
            if edit['after']==actual:raise ValueError('未变化的方块不得冒充道路施工：'+aid)
            if pos in world.central_structural_cells:raise ValueError('道路施工触碰真实建筑实体：'+aid+' '+str(pos))
            if pos in world.protected:raise ValueError('道路施工触碰原通路/岗位净空：'+aid+' '+str(pos))
            source_failed_column=column in {(p[0],p[2]) for p in row['retired_failed_cells']}
            road_cut=(source_failed_column and actual.split('[',1)[0] in ('minecraft:stone_bricks','minecraft:stone_brick_stairs','minecraft:stone_brick_slab'))
            if not(world.passable(actual) or actual in rules['modifiable_ground_blocks'] or road_cut):
                raise ValueError('道路修正不得清除原水系或原有设施：'+aid+' '+str(pos))
            if abs(lanes[column]-ground-1)>rules['local_earthwork_limit'] and column not in raised:
                raise ValueError('高路面缺显式支撑结构声明：'+aid)
            overlay[pos]=edit['after']
        result.append(row)
    # 不改变原始掩码；只有 actual hook 完成全宽回读后才可标记某条旧段被替代。
    planner.road_adjustments=result;planner.road_overlay=overlay;planner.road_earthwork=list(earth.values())
    planner.road_replaced_failures=assigned;planner.road_adjustment_walkpoints={(x,y,z):'road_revision' for (x,z),y in walk.items()}
    for (x,z),y in walk.items():
        old=planner.model.road_columns.get((x,z))
        if old and old[0]!=y:raise ValueError('修正道路改变原有可走列高度：'+str((x,z,old[0],y)))
        planner.model.road_columns.setdefault((x,z),(y,1,0,0,'road_revision'))
    return result


def verify_road_adjustments(world,data):
    rows=data.get('road_adjustments',[]);points=set();retired=set();edges=0;narrow=0;piers=0;raised_count=0
    for row in rows:
        for segment in row['segments']:
            _segment_columns(segment);edges+=1;narrow+=segment['width']<(row['target_width'] if row.get('kind') in ('network_connection','entry_connection') else row['original_width'])
        for p in map(tuple,row['walkpoints']):
            x,y,z=p
            if not world.walkable(p) or not _road_surface(world.get((x,y-1,z))) or not all(world.passable(world.get((x,y+dy,z))) for dy in range(data['road_rules']['headroom'])):
                raise ValueError('修正后完整路幅支撑/净空失败：'+row['id']+' '+str(p))
            points.add(p)
        heights={(x,z):y for x,y,z in row['walkpoints']}
        raised={tuple(r['column']):r for r in row.get('raised_columns',[])};raised_count+=len(raised)
        supports={tuple(p['column']) for p in row.get('piers',[])};piers+=len(supports)
        for pier in row.get('piers',[]):
            if not _road_surface(world.get((pier['column'][0],pier['ground_y'],pier['column'][1]))):
                raise ValueError('支墩下方没有真实地面支撑：'+row['id'])
            if any(world.get(tuple(p))!=data['road_rules']['surface_material'] for p in pier['blocks']):
                raise ValueError('架空路面支墩存在空格或材料不符：'+row['id'])
        distance={c:0 for c in heights if c not in raised or c in supports};queue=deque(distance)
        while queue:
            x,z=queue.popleft()
            for c in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
                if c in heights and c not in distance and abs(heights[c]-heights[x,z])<=1:
                    distance[c]=distance[x,z]+1;queue.append(c)
        if any(distance.get(c,10**9)>data['road_rules']['maximum_unsupported_span'] for c in raised):
            raise ValueError('连续桥面超过明确承托跨度：'+row['id'])
        retired.update((*p,row['route_id']) for p in row['retired_failed_cells'])
    original={tuple(p) for p in data.get('original_road_unavailable',[])}
    if not retired<=original:raise ValueError('道路修正失去原失败对应关系')
    return {'status':'pass','routes':len(rows),'full_width_walkpoints':len(points),'continuous_segments':edges,
            'explicit_narrow_segments':narrow,'original_failure_cells':len(original),'resolved_original_cells':len(retired),
            'unresolved_original_cells':len(original-retired),'headroom':data['road_rules']['headroom'],
            'raised_deck_columns':raised_count,'support_piers':piers}


def apply_road_adjustments(world,planner,data):
    """共享构建钩子；仅返回本次真实变化格，所有模板实体继续零例外保护。"""
    rows=data.get('road_adjustments',[])
    if rows!=planner.road_adjustments:raise ValueError('施工道路与编译后的 authored 方案不一致')
    before={}
    for row in rows:
        for edit in row['edits']:
            pos=tuple(edit['position'])
            if world.get(pos)!=edit['before']:raise ValueError('道路施工前置条件变化：'+row['id']+' '+str(pos))
            if pos in world.central_structural_cells:raise ValueError('禁止道路施工改写真实模板：'+str(pos))
            before.setdefault(pos,world.get(pos));world.set(pos,edit['after'],row['id'])
    earth={tuple(e['column']):e for e in data['earthwork']}
    for e in planner.road_earthwork:
        c=tuple(e['column'])
        if c in earth and earth[c]['air_y']!=e['air_y']:raise ValueError('道路与庭院整地高度冲突')
        earth[c]=e;world.terrain[c[1]*world.width+c[0]]=e['air_y']-1
    data['earthwork']=list(earth.values())
    checks=verify_road_adjustments(world,data)
    for pos in planner.original_road_walkpoints:
        if not world.walkable(pos) or not all(world.passable(world.get((pos[0],pos[1]+dy,pos[2]))) for dy in range(planner.rules['headroom'])):
            raise ValueError('绕檐修正损失原有可走路格：'+str(pos))
    for pos in planner.road_adjustment_walkpoints:
        world.protected_walkpoints[pos]='verified_road_revision'
        for dy in range(planner.rules['headroom']):world.protected[pos[0],pos[1]+dy,pos[2]]='verified_road_revision'
    planner.road_unavailable=[p for p in planner.original_road_unavailable if tuple(p) not in planner.road_replaced_failures]
    data['road_adjustment_checks']=checks
    data['road_adjustment_coverage']=[{'revision_id':r['id'],'source_route_id':r['route_id'],
                                      'original_failed_cells':r['retired_failed_cells'],'new_walkpoints':r['walkpoints']} for r in rows]
    data['road_geometry']['status']='pass' if not planner.road_unavailable else 'needs_review'
    data['road_geometry']['remaining_unavailable_source_cells']=copy.deepcopy(planner.road_unavailable)
    data['checks']['original_road_clearance']='resolved_by_explicit_revisions' if not planner.road_unavailable else 'needs_review'
    data['checks']['remaining_road_unavailable_cells']=len(planner.road_unavailable)
    if not planner.road_unavailable and not data['checks']['issues']:data['checks']['status']='pass'
    return {p for p,old in before.items() if world.get(p)!=old}


def road_network_audit(world,planner,*,allow_door_interactions=True,use_primary_entries=False):
    """从真实城门出发核对逐户入口；桥面、公共院场和门楼由实际方块补入。"""
    points=set(planner.original_road_walkpoints)|set(planner.road_adjustment_walkpoints)
    def consider(p):
        x,y,z=p
        if world.walkable(p) and _road_surface(world.get((x,y-1,z))) and all(world.passable(world.get((x,y+dy,z))) for dy in range(planner.rules['headroom'])):
            points.add(p)
    for courtyard in planner.model.city['courtyards']:
        for z,x0,x1 in courtyard['cell_runs']:
            for x in range(x0,x1+1):consider((x,world.ground(x,z)+1,z))
    for row,model,transform in zip(planner.geometry.rows,planner.geometry.models,planner.geometry.transforms):
        # 入户门廊/台阶也由真实模板负责；只添实际有支撑与三格净空的节点。
        # 闭门与实体墙仍阻断搜索，不能把每栋私宅的入口简化成孤立一个点。
        for p,state in model.cells.items():
            if state!=AIR:
                x,y,z=transform(p)
                if y+1<world.height-2:consider((x,y+1,z))
        # 室内保持 owning 模板既有两格人体净空；不要求削掉原门楣/屋盖。
        for node in model.nodes if allow_door_interactions else []:
            if 'floor_y' not in node.get('meta',{}):continue
            x0,y0,z0=node['origin'];width,height,depth=node['size']
            for x in range(x0+1,x0+width-1):
                for z in range(z0+1,z0+depth-1):
                    for y in range(y0+1,y0+height):
                        wp=transform((x,y,z))
                        if world.walkable(wp) and _road_surface(world.get((wp[0],wp[1]-1,wp[2]))):points.add(wp)
    portals=set()
    candidates=[transform(tuple(door)) for model,transform in zip(planner.geometry.models,planner.geometry.transforms) for door in model.doors]
    candidates += [tuple(d['position']) for d in planner.doors]
    for pos in candidates if allow_door_interactions else []:
        if _openable_door(world,pos):points.add(pos);portals.add(pos)
    entries={row['building_id']:tuple(row['entry']) for row in world.central_routes['access']}
    if use_primary_entries:
        entries={bid:tuple(planner.buildings[bid]['entrance']) for bid in entries}
    for pos in entries.values():consider(pos)
    root=tuple(world.manifest['entries'][0]['position']);consider(root)
    reachable={root};queue=deque([root])
    while queue:
        x,y,z=queue.popleft()
        for dx,dz in DIR.values():
            for dy in (-1,0,1):
                pos=x+dx,y+dy,z+dz
                if pos in points and pos not in reachable:reachable.add(pos);queue.append(pos)
    missing=[{'building_id':bid,'entry':list(pos)} for bid,pos in entries.items() if pos not in reachable]
    planner.road_network_points=points;planner.road_network_reachable=reachable
    return {'status':'pass' if not missing else 'needs_design','root':list(root),'declared_access_entries':len(entries),
            'connected_access_entries':len(entries)-len(missing),'walkpoints':len(points),'reachable_walkpoints':len(reachable),
            'unreachable_entries':missing,'source_isolated_pavilions':sum(b['access_kind']=='source_isolated_pillar' for b in planner.model.buildings),
            'openable_door_portals':len(portals),'existing_interior_headroom':world.config['collision']['headroom'],
            'outdoor_headroom':planner.rules['headroom'],'door_interaction':'requires_opening_complete_vanilla_doors' if allow_door_interactions else 'not_used',
            'entry_policy':'current_primary' if use_primary_entries else 'retained_original','runtime':'not_run'}


def _openable_door(world,pos):
    """完整两半原版门的可交互门户；不是把任意非空气状态当空气。"""
    x,y,z=pos;lower=world.get(pos);upper=world.get((x,y+1,z))
    expected=world.central_geometry.factory.rules['doors']
    if lower.split('[',1)[0]!=expected or upper.split('[',1)[0]!=expected or '[' not in lower or '[' not in upper:return False
    def properties(state):return dict(part.split('=',1) for part in state.split('[',1)[1][:-1].split(','))
    lo,hi=properties(lower),properties(upper)
    return (lo.get('half')=='lower' and hi.get('half')=='upper' and lo.get('facing')==hi.get('facing')
            and lo.get('hinge')==hi.get('hinge') and _road_surface(world.get((x,y-1,z))))


def verify_entry_doors(world,planner,data):
    result=[]
    for door in data.get('entry_doors',[]):
        if not _openable_door(world,tuple(door['position'])):raise ValueError('新入口门不完整：'+door['id'])
        model,transform=planner._model_by_id[door['owner']]
        base=planner.buildings[door['owner']]['base_position'][1]
        local_y=door['position'][1]-base
        candidates={transform((x,local_y,z)) for x,z in model.floor_footprint}
        candidates.add(tuple(door['outside']))
        allowed={p for p in candidates if world.walkable(p) or _openable_door(world,p)}
        start=tuple(door['inside'])
        if start not in allowed:raise ValueError('新门洞没有连接实际室内：'+door['id'])
        reached={start};queue=deque([start])
        while queue:
            x,y,z=queue.popleft()
            for dx,dz in DIR.values():
                q=x+dx,y,z+dz
                if q in allowed and q not in reached:reached.add(q);queue.append(q)
        original={tuple(p) for p in door['source_reachable_points']}
        if not original<=reached or tuple(door['outside']) not in reached or tuple(door['source_entrance']) not in reached:
            raise ValueError('原室内/原门廊未通过实际新门连接：'+door['id'])
        route=next(r for r in data['road_adjustments'] if r.get('entry_door_id')==door['id'])
        result.append({'id':door['id'],'owner':door['owner'],'source_reachable_cells':len(original),
            'source_cells_reached':len(original & reached),'actual_reachable_cells':len(reached),
            'original_entrance_reached':True,'outside_route_points':len(route['points']),
            'interior_headroom':world.config['collision']['headroom'],'outdoor_headroom':planner.rules['headroom'],
            'door_interaction':'required','status':'pass'})
    return result


def source_surfaces(g):
    """区分原作独立院场与每户 .4 格宅基铺地，不能把宅基当六百处庭园。"""
    result=[]
    allowed={g.source['TM']['COURT'],g.source['TM']['PLAZA']}
    for i,f in enumerate(g.source['features']):
        if f['t'] not in (0,1) or f['top'] not in allowed:continue
        owner=None
        if f['t']==0:
            cx,cz=(f['x0']+f['x1'])/2,(f['z0']+f['z1'])/2
            matches=[r for r in g.rows if r['source_kind'] in ('house','shop','tea','court')
                     and abs(r['source_position'][0]-cx)<=.11 and abs(r['source_position'][2]-cz)<=.11]
            if len(matches)>1:raise ValueError('原作宅基铺地对应多栋建筑')
            if matches:owner=matches[0]['id']
        result.append((i,f,owner))
    return result


def _court_geometry(g, city, bindings):
    records = []; columns = {}
    ox,_,oz = city['bounds']['source_offset']
    for i,f,lot_owner in source_surfaces(g):
        cid = f'source_court_{i}'
        if cid not in bindings:
            raise ValueError('既有院场缺明确归属：'+cid)
        binding = bindings[cid]
        if binding['access'] not in ('private','public') or not binding.get('owner') or not binding.get('reason'):
            raise ValueError('既有院场归属缺访问方式、管理者或理由：'+cid)
        if lot_owner and (binding['owner']!=lot_owner or binding.get('kind')!='source_lot_pad'):
            raise ValueError('原作宅基铺地归属与原建筑不一致：'+cid)
        if f['t']==0:
            bounds=[math.ceil(f['x0']-ox), math.ceil(f['z0']-oz), math.floor(f['x1']-ox), math.floor(f['z1']-oz)]
            cells=rect(bounds)
        else:
            bounds=[math.ceil(f['cx']-f['rx']-ox),math.ceil(f['cz']-f['rz']-oz),math.floor(f['cx']+f['rx']-ox),math.floor(f['cz']+f['rz']-oz)]
            cells={p for p in rect(bounds) if ((p[0]+ox-f['cx'])/f['rx'])**2+((p[1]+oz-f['cz'])/f['rz'])**2<=1}
        cells -= g.world.occupied.keys()
        y=round(g.adapt(f['y']))
        records.append({'id':cid,**copy.deepcopy(binding),'source_feature':i,'source_shape':copy.deepcopy(f),
                        'bounds':bounds,'cell_runs':runs(cells),'center':[(bounds[0]+bounds[2])//2,y,(bounds[1]+bounds[3])//2],
                        'size':[bounds[2]-bounds[0]+1,bounds[3]-bounds[1]+1], 'retained':True})
        if not lot_owner:
            for p in cells:columns[p]=cid
    if set(bindings)!={c['id'] for c in records}:
        raise ValueError('既有院场绑定存在不存在的源 ID')
    return records, columns


class CentralPlanner(Planner):
    def __init__(self, spec, city, environment, catalog, *, block_world):
        self.spec=spec; self.env=environment_input(spec,environment); self.catalog=catalog
        self.rules=spec['rules']; self.block_world=block_world
        block_world.central_planner=self
        verify_applied_world(block_world,{})
        if spec['schema']!=1 or spec['city_id']!=city['city_id']:
            raise ValueError('中州蓝图 schema 或城市身份不一致')
        if self.rules['door_width']!=1 or self.rules['path_width']!=1:
            raise ValueError('中州门洞与庭院支路宽度必须为一格，不能忽略配置')
        self.removed=self.env['remove_buildings']
        if self.removed: raise ValueError('本轮中州保留所有原建筑，不允许移除')
        g=block_world.central_geometry; self.geometry=g
        self.original_terrain=list(block_world.terrain)
        self.paths=[]; self.doors=[]; self.earthwork={}; self.fixtures=[]; self.gardens=[]; self.issues=[]
        self.road_adjustments=[];self.road_overlay={};self.road_earthwork=[]
        self.road_replaced_failures=set();self.road_adjustment_walkpoints={}
        self.entry_doors=[]
        self.proposed_columns={}; self.tiles={}; self.context_cells=set(); self.context_objects=[]
        self.context=Scene(catalog['materials'])
        self.reserve=rect(self.env['reserve'])
        # 城防不被一条闭合折线替代：断开的城门、山墙和宫墙分别保留真实源段。
        ox,_,oz=city['bounds']['source_offset']
        walls=[f for f in g.source['features'] if f.get('kind') in (1,4)]
        self.env['wall']={'inherit_wall':True,'points':[], 'gates':[], 'segments':[
            {'points':[[f['ax']-ox,g.adapt(f['ay']),f['az']-oz],[f['bx']-ox,g.adapt(f['by']),f['bz']-oz]], 'width':f['hw']*2,'kind':f['kind']} for f in walls]}
        courts,court_columns=_court_geometry(g,city,spec['source_courtyard_bindings'])
        self.model=SimpleNamespace(width=block_world.width,height=block_world.height,depth=block_world.depth,
            terrain=block_world.terrain,ground=block_world.ground,buildings=[],access=[],
            footprints=copy.deepcopy(g.world.footprints),occupied=dict(g.world.occupied),court_columns=court_columns,
            road_columns={},roads=copy.deepcopy(block_world.manifest['roads']),
            city={'districts':spec['districts'],'terrain_patches':spec['terraces'],'courtyards':courts},
            material=lambda key:city['templates']['doors'] if key=='DOOR' else city['materials']['wall']['hui'])
        self._model_by_id={r['id']:(m,t) for r,m,t in zip(g.rows,g.models,g.transforms)}
        access_by_id={r['building_id']:r for r in block_world.manifest['access_paths']}
        road_ids={r['id'] for r in self.model.roads}
        labels=spec['central']['building_labels']
        for row in g.rows:
            b=copy.deepcopy(row);m,t=self._model_by_id[b['id']]
            b['base_position']=b['position'][:];b['position'][1]+=1;b['entrance']=b['entry'][:]
            b['role']=b['service'] or ('house' if b['source_kind']=='house' else b['source_kind'])
            b['family']=labels.get(b['source_kind'],'建筑')
            b['name']=b['name'] if b['service'] else labels.get(b['source_template'],labels.get(b['source_kind'],'建筑'))+'·'+str(b['source_instance_index'])
            b['district_id']=next((d['id'] for d in spec['districts'] if d['bounds'][0]<=b['position'][0]<=d['bounds'][2] and d['bounds'][1]<=b['position'][2]<=d['bounds'][3]),'outer')
            b['size']=[b['bounds']['max'][0]-b['bounds']['min'][0]+1,b['bounds']['max'][2]-b['bounds']['min'][2]+1]
            b['volumes']=[]
            for node in m.nodes:
                if not node['id'].startswith('story_'):continue
                nx,ny,nz=node['origin'];nw,nh,nd=node['size']
                b['volumes'].append({'bounds_cells':[nx+b['size'][0]//2,nz+b['size'][1]//2,
                    nx+nw-1+b['size'][0]//2,nz+nd-1+b['size'][1]//2], 'source_node':node['id']})
            bid=b['id'];b['road_id']='access_'+bid if bid in access_by_id else ('access_'+b['entry_via'] if b.get('entry_via') else None)
            if bid in access_by_id:self.model.access.append(copy.deepcopy(access_by_id[bid]))
            if b['source_kind'] in ('archbridge','langqiao'):
                b['access_kind']='bridge';b['road_id']='bridge_landing_'+bid+'_-1'
            elif b.get('entry_via'):b['access_kind']='connected_compound'
            elif b['source_kind']=='walltower':b['access_kind']='inherited_wall_walk'
            elif b['source_kind']=='watergate':b['access_kind']='water_gate_quays'
            elif b['source_template']=='pillar_ting':b['access_kind']='source_isolated_pillar'
            else:b['access_kind']='street'
            self.model.buildings.append(b)
        self.buildings={b['id']:b for b in self.model.buildings}
        self._roads()
        self.original_road_unavailable=copy.deepcopy(self.road_unavailable)
        self.source_lot_pads=[]
        self.model.city['courtyards']=[]
        for court in courts:
            if court.get('kind')!='source_lot_pad':
                self.model.city['courtyards'].append(court);continue
            cells={(x,z) for z,x0,x1 in court['cell_runs'] for x in range(x0,x1+1)}
            public=cells & self.declared_road_columns
            court['source_uncovered_runs']=court['cell_runs']
            court['cell_runs']=runs(cells-public)
            court['public_access_easement_runs']=runs(public)
            self.source_lot_pads.append(court)
        for r in self.model.roads:
            r['name']=spec['road_names'].get(r['id'],'入户巷道' if r['id'].startswith('access_') else '原作街路·'+r['id'])
            r['kind']='stairs' if len({p[1] for p in r['points']})>1 else 'road'
        self.garden_columns={p:gd['id'] for gd in self.env['gardens'] for p in rect(gd['bounds'])}
        if len(self.garden_columns)!=sum(len(rect(gd['bounds'])) for gd in self.env['gardens']):raise ValueError('庭院范围互相重叠')
        if self.garden_columns.keys() & self.model.occupied.keys():raise ValueError('庭院侵入真实建筑地块')
        if set(spec['garden_bindings'])!={gd['id'] for gd in self.env['gardens']}:raise ValueError('庭院归属覆盖不完整')
        for binding in spec['garden_bindings'].values():
            if binding['access'] not in ('private','public') or not binding.get('reason'):raise ValueError('庭院须明确归属和理由')
        self._door_cache={}

    def _roads(self):
        """展开原作道路真实宽度，查询最终方块脚面；所有遗漏原样记录。"""
        candidates={}
        for r in self.geometry_routes():
            radius=r['width']//2
            for x,y,z in r['points']:
                for dz in range(-radius,radius+1):
                    for dx in range(-radius,radius+1):
                        p=(x+dx,z+dz)
                        if not(0<=p[0]<self.model.width and 0<=p[1]<self.model.depth):continue
                        if p in self.model.occupied:continue
                        candidates[p]=(math.ceil(y),r['id'])
        self.declared_road_columns=set(candidates)
        emitted=self.block_world.central_original_road_columns
        self.source_unemitted_columns=sorted(self.declared_road_columns-emitted.keys())
        self.original_road_walkpoints={}; self.road_unavailable=[]
        self.original_native_walkpoints={}
        for (x,z),(foot2,face,wood,rid) in sorted(emitted.items(),key=lambda kv:(kv[0][1]//16,kv[0][0]//16,kv[0])):
            y=math.ceil(foot2/2)
            if (x,z) in self.model.occupied:continue
            if self.block_world.walkable((x,y,z)):
                self.original_native_walkpoints[x,y,z]=rid
            # 原作交叉道路以最终写入者为准；不能拿被覆盖道路的旧高度误报断路。
            found=None
            for yy in (y,):
                state=self.block_world.get((x,yy-1,z))
                if self.block_world.walkable((x,yy,z)) and all(self.block_world.passable(self.block_world.get((x,yy+d,z))) for d in range(self.rules['headroom'])) and ('stone_brick' in state or state.startswith('minecraft:spruce_planks')):
                    found=yy;break
            if found is None:
                self.road_unavailable.append([x,y,z,rid]);continue
            self.model.road_columns[x,z]=(found,1,0,0,rid)
            self.original_road_walkpoints[x,found,z]=rid
        # 保护全宽实际可走格，补足共享通路保护器的横向带状采样。
        self.block_world.protected_walkpoints.update({p:'central_full_width:'+rid for p,rid in self.original_road_walkpoints.items()})
        for (x,y,z),rid in self.original_road_walkpoints.items():
            for dy in range(self.rules['headroom']):
                self.block_world.protected[x,y+dy,z]='central_full_width:'+rid
        # 三格净空不足但原来两格身体可走的旧路也保留，不能用新标准把旧通行证据丢掉。
        for (x,y,z),rid in self.original_native_walkpoints.items():
            self.block_world.protected_walkpoints[x,y,z]='central_retained_native:'+rid
            for dy in range(2):self.block_world.protected[x,y+dy,z]='central_retained_native:'+rid

    def geometry_routes(self):
        return self.block_world.central_routes['routes']

    def state(self,p):
        return self.road_overlay[p] if p in self.road_overlay else self.block_world.get(p)

    def clear(self,p,height):
        x,y,z=p
        return all(self.block_world.passable(self.state((x,y+dy,z))) or '_door[' in self.state((x,y+dy,z)) for dy in range(height))

    def door_candidates(self,owner):
        if owner in self._door_cache:return self._door_cache[owner]
        b=self.buildings[owner];model,trans=self._model_by_id[owner]
        # 以真实模板外门为根，沿有支撑且两格净空的第一层室内做连通搜索。
        local_y=min((p[1] for p in model.doors),default=1)
        floor={(x,z) for x,y,z in model.cells if y==local_y-1 and model.cells[x,y,z]!=AIR}
        def free(p):
            x,z=p
            return p in floor and all(model.cells.get((x,local_y+d,z),AIR)==AIR or '_door[' in model.cells.get((x,local_y+d,z),AIR) for d in range(2))
        seeds={(x,z) for x,y,z in model.doors if y==local_y and free((x,z))}
        reachable=set(seeds);queue=deque(sorted(seeds))
        while queue:
            x,z=queue.popleft()
            for dx,dz in DIR.values():
                p=(x+dx,z+dz)
                if p not in reachable and free(p):reachable.add(p);queue.append(p)
        candidates={}
        for x,z in sorted(floor):
            state=model.cells.get((x,local_y,z),AIR)
            if state==AIR or ('_door[' not in state and state not in set(self.geometry.factory.materials['wall'].values())):continue
            if any(model.cells.get((x,local_y+d,z),AIR)==self.geometry.factory.materials['frame'] for d in range(2)):continue
            for face,(dx,dz) in DIR.items():
                inside=(x-dx,z-dz);outside=(x+dx,z+dz)
                if inside not in reachable or outside in floor:continue
                wp=trans((x,local_y,z));ip=trans((inside[0],local_y,inside[1]));op=trans((outside[0],local_y,outside[1]))
                if not self.clear(ip,2) or not self.clear(op,self.rules['headroom']):continue
                delta=op[0]-wp[0],op[2]-wp[2]
                candidates[op]={'owner':owner,'position':list(wp),'outside':list(op),'inside':list(ip),
                    'facing':next(k for k,v in DIR.items() if v==delta),'action':'retain_door' if '_door[' in state else 'open_door',
                    'width':1,'headroom':self.rules['headroom'],'reachable_interior_cells':len(reachable),
                    'source_reachable_points':[list(trans((xx,local_y,zz))) for xx,zz in sorted(reachable)]}
        self._door_cache[owner]=candidates
        return candidates

    def garden_path(self,garden,binding):
        gid= garden['id'];height=garden['y'];owner=binding.get('owner')
        private=binding['access']=='private';cells=rect(garden['bounds'])
        limit=self.rules['private_access_max_length' if private else 'public_access_max_length']
        if private:
            if owner not in self.buildings:raise ValueError(gid+': 私院缺有效房屋归属')
            if self.buildings[owner]['position'][1]!=height:raise ValueError(gid+': 私院与房屋标高不同')
            if cells & self.model.court_columns.keys():raise ValueError(gid+': 私院不能占用公共前场')
            if cells & self.declared_road_columns:raise ValueError(gid+': 私院不能占用公共路幅')
            doors=self.door_candidates(owner);goals=set(doors)
        else:
            doors={};x0,z0,x1,z1=garden['bounds']
            # 超过 Manhattan 路长预算的道路不可能成为终点；先剔除它们，避免
            # 为一处小庭随机加载全城四万余路格的 tile。可达解集合保持不变。
            goals={(x,v[0],z) for (x,z),v in self.model.road_columns.items()
                   if max(x0-x,0,x-x1)+max(z0-z,0,z-z1)<=limit}
        starts={(x,height,z) for x,z in perimeter(garden['bounds'])}
        @lru_cache(maxsize=None)
        def valid(p):
            x,y,z=p;column=x,z
            if not(0<=x<self.model.width and 0<=z<self.model.depth and 2<=y<self.model.height-3):return False
            if private and y!=height:return False
            if column in self.reserve or self.garden_columns.get(column,gid)!=gid:return False
            if column in self.model.occupied:return False
            if private and (column in self.declared_road_columns or column in self.model.court_columns):return False
            if column in self.proposed_columns and self.proposed_columns[column]!=gid:return False
            if column in self.model.road_columns:return p in goals and self.clear(p,self.rules['headroom'])
            if p not in starts and column in cells:return False
            if abs(y-self.model.ground(x,z)-1)>self.rules['local_earthwork_limit'] and column not in self.model.occupied:return False
            return self.clear(p,self.rules['headroom'])
        starts={p for p in starts if valid(p)}
        path=search_path(starts,{p for p in goals if valid(p)},valid,limit)
        if not path:
            self.issues.append({'id':gid,'kind':'unconnected_garden','message':'在既定距离、净空与整地边界内未找到接路','bounds':garden['bounds']})
            return [],None
        return path,doors.get(path[-1])

    def compile(self):
        self.entry_doors=[]
        for authored in self.spec.get('entry_doors',[]):
            owner=authored['owner']
            if owner not in self.buildings or not authored.get('id') or not authored.get('reason') or 'garden_id' in authored:
                raise ValueError('独立入口门须有真实owner/id/理由，不得虚构庭院关联')
            candidate=next((d for d in self.door_candidates(owner).values() if d['position']==authored['position']),None)
            if candidate is None:raise ValueError('新入口门没有连接既有可达室内：'+authored['id'])
            for key in ('owner','position','outside','inside','facing','action','width','headroom'):
                if authored[key]!=candidate[key]:raise ValueError('新入口门与真实几何不一致：'+authored['id']+' '+key)
            source=next(a for a in self.block_world.central_routes['access'] if a['building_id']==owner)
            door={**candidate,**copy.deepcopy(authored),'source_reachable_points':candidate['source_reachable_points'],
                  'source_entrance':source['entry']}
            self.entry_doors.append(door);self.doors.append(door)
            self.buildings[owner]['source_entrance']=self.buildings[owner]['entrance']
            self.buildings[owner]['entrance']=door['outside'];self.buildings[owner]['entry']=door['outside']
        _validate_road_adjustments(self)
        for row in self.road_adjustments:
            self.model.roads.append({'id':row['id'],'name':row['name'],'points':row['points'],
                'width':max(s['width'] for s in row['segments']),'segments':row['segments'],
                'kind':'stairs' if len({p[1] for p in row['points']})>1 else 'road',
                'replaces_route':row['route_id'],'reason':row['reason']})
            for road in self.model.roads:
                if road['id']==row['route_id']:road.setdefault('explicit_revisions',[]).append(row['id'])
            if row.get('kind') not in ('network_connection','entry_connection') and row['route_id'].startswith('access_'):
                bid=row['route_id'].removeprefix('access_')
                self.buildings[bid]['source_road_id']=self.buildings[bid]['road_id']
                self.buildings[bid]['road_id']=row['id']
                for access in self.model.access:
                    if access['building_id']==bid:
                        access['source_points']=access['points'];access['points']=row['points']
                        access['route_revision']=row['id']
            if row.get('kind')=='entry_connection':
                bid=row['owner'];self.buildings[bid]['source_road_id']=self.buildings[bid]['road_id']
                self.buildings[bid]['road_id']=row['id']
                for access in self.model.access:
                    if access['building_id']==bid:
                        access['source_points']=access['points'];access['source_entry']=access['entry']
                        access['entry']=row['start'];access['points']=row['points'];access['route_revision']=row['id']
        data=super().compile()
        combined={tuple(e['column']):e for e in data['earthwork']}
        for e in self.road_earthwork:
            c=tuple(e['column'])
            if c in combined and combined[c]['air_y']!=e['air_y']:raise ValueError('道路与庭院整地冲突')
            combined[c]=e
        data['earthwork']=list(combined.values())
        return data

    def export(self):
        # 共台基的交叠仍保留原足迹证据；行政地块只把共享列记到主殿，不伪造两份所有权。
        shared=self.geometry.world.overlaps
        saved=self.model.footprints
        self.model.footprints={k:set(v) for k,v in saved.items()}
        for record in shared:self.model.footprints[record['owners'][1]].discard(tuple(record['column']))
        try:data=super().export()
        finally:self.model.footprints=saved
        for plot in data['plots']:
            plot['source_footprint_runs']=runs(saved[plot['id']])
            plot['access_kind']=plot['building']['access_kind']
            plot['source_lot_pad_ids']=[r['id'] for r in self.source_lot_pads if r['owner']==plot['id']]
        data['shared_foundations']=shared
        data['source_geometry']={'generator':'tools/province_capitals/central/export_game.py','hashes':self.geometry.hashes,
                                 'manifest_sha256':_sha(resource_file(self.block_world.root,'myvillage:capitals/v1/capital_zhongzhou/city.json'))}
        data['retained_courtyards']=data['courtyards']
        data['source_lot_pads']=self.source_lot_pads
        data['entry_doors']=copy.deepcopy(self.entry_doors)
        data['road_adjustments']=copy.deepcopy(self.road_adjustments)
        data['road_rules']=copy.deepcopy(self.spec.get('road_rules',{}))
        data['original_road_unavailable']=copy.deepcopy(self.original_road_unavailable)
        # 原失败段完整另存；当前图按各段真实横断面显示，不再把已替代路线画成现行通路。
        revisions={r['route_id']:r for r in self.road_adjustments if r.get('kind') not in ('network_connection','entry_connection')}
        retired=[];active=[]
        source_roads=copy.deepcopy(self.block_world.manifest['roads'])
        old_entry_routes={'access_'+d['owner'] for d in self.entry_doors}
        for road in source_roads:
            rid=road['id'];road['name']=self.spec['road_names'].get(rid,'原有街路')
            revision=revisions.get(rid)
            if rid in old_entry_routes:
                retired.append({'source_route_id':rid,'points':road['points'],'width':road['width'],
                                'reason':'旧门廊及路格保留，新增西侧入口承担本户对外接路。'})
            elif revision:
                replaced=revision['replaced_source_segment'];lo,hi=replaced['first_index'],replaced['last_index']
                retired.append({'source_route_id':rid,'revision_id':revision['id'],**copy.deepcopy(replaced)})
                pieces=[road['points'][:lo+1],road['points'][hi:]]
                number=0
                for piece in pieces:
                    if len(piece)<2:continue
                    active.append({**road,'id':rid if number==0 else rid+'_保留后段','points':piece});number+=1
            else:active.append(road)
        for revision in self.road_adjustments:
            for i,segment in enumerate(revision['segments']):
                a,b=segment['a'],segment['b'];dx,dz=b[0]-a[0],b[2]-a[2]
                offset=sum(segment['lateral_offsets'])/len(segment['lateral_offsets'])
                centerline=[[q[0]+(offset if dz else 0),q[1],q[2]+(offset if dx else 0)] for q in (a,b)]
                active.append({'id':revision['id'] if i==0 else revision['id']+f'_段{i:03d}',
                    'name':revision['name'],'revision_id':revision['id'],'points':centerline,'width':segment['width'],
                    'native_centerline':[a,b],'kind':'stairs' if a[1]!=b[1] else 'road','reason':revision['reason']})
        data['roads']=active;data['retained_source_roads']=source_roads;data['retired_road_segments']=retired
        data['road_geometry']={'source_routes':len(self.geometry_routes()),'source_access':len(self.block_world.central_routes['access']),
            'declared_columns':len(self.declared_road_columns),'walkable_full_width_columns':len(self.original_road_walkpoints),
            'unavailable_source_cells':self.road_unavailable,'source_unemitted_columns':self.source_unemitted_columns,
            'status':'needs_review' if self.road_unavailable else 'pass',
            'note':'重放原作道路发射的地基/水体/城墙排除规则，分别记录未发射列和最终不可走格；所有实际可走格纳入保护。'}
        data['checks'].update(active_source_buildings=len(self.geometry.rows),covered_active_buildings=len(data['plots']),
            retained_courtyards=len(data['courtyards']),source_isolated_pavilions=sum(b['access_kind']=='source_isolated_pillar' for b in self.model.buildings),
            source_lot_pads=len(self.source_lot_pads),
            full_width_original_walkpoints=len(self.original_road_walkpoints),
            planned_road_revisions=len(self.road_adjustments),planned_original_failures_covered=len(self.road_replaced_failures),
            blueprint_geometry='pass' if not self.issues else 'needs_design',
            original_road_clearance='needs_review' if self.road_unavailable else 'pass',
            original_road_unavailable_cells=len(self.road_unavailable))
        if self.road_unavailable and not self.issues:
            data['checks']['status']='needs_original_road_review'
        return data


def create_planner(spec,city,environment,catalog,*,block_world=None):
    if block_world is None:
        from .export_minecraft import baseline
        base=Path(os.environ.get('MYVILLAGE_CENTRAL_BASE',ROOT/'build/generated/province_capitals/resources'))
        block_world=baseline(spec,base)
    return CentralPlanner(spec,city,environment,catalog,block_world=block_world)


def preview_scene(planner,data):
    """逐 tile 消费真实方块，保留山势、河湖、桥梁、城防及完整原作建筑。"""
    builder=SceneBuilder.__new__(SceneBuilder)
    builder.model=planner.model;builder.boxes=[];builder.meshes=[];builder.labels=[]
    builder.palette={**COLORS,'private':'#90b48c','public':'#d4b685','route':'#c7ddbd','door_new':'#e2b960'}
    by_tile={}
    for b in planner.model.buildings:
        lo,hi=b['bounds']['min'],b['bounds']['max']
        for tx in range(lo[0]//16,hi[0]//16+1):
            for tz in range(lo[2]//16,hi[2]//16+1):by_tile.setdefault((tx,tz),[]).append(b)
    world=planner.block_world
    overlay_tiles={};overlay_tags={}
    for row in data.get('road_adjustments',[]):
        for edit in row['edits']:
            x,y,z=edit['position'];key=x//16,z//16
            overlay_tiles.setdefault(key,{})[x%16,y,z%16]=edit['after'];overlay_tags[x,y,z]=row['id']
    holes={tuple(d['position']):d for d in data['doors'] if d['action']=='open_door'}
    for key in sorted(set(world.refs)|world.dirty|set(overlay_tiles),key=lambda k:(k[1],k[0])):
        tx,tz=key;cells={**world.tile(key),**overlay_tiles.get(key,{})};subset={}
        for (x,y,z),state in cells.items():
            if state==AIR:continue
            # 隐藏六面都被实心方块遮住的格子，外壳不简化成体量盒。
            if all(cells.get((x+dx,y+dy,z+dz),AIR)!=AIR for dx,dy,dz in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1))):continue
            wx,wz=tx*16+x,tz*16+z
            b=next((b for b in by_tile.get(key,[]) if b['bounds']['min'][0]<=wx<=b['bounds']['max'][0] and b['bounds']['min'][2]<=wz<=b['bounds']['max'][2] and b['base_position'][1]<=y<=b['bounds']['max'][1]),None)
            tag=b['id'] if b else '原作环境'
            layer='roof' if any(s in state for s in ('deepslate','prismarine','sandstone_stairs','sandstone_slab')) else 'buildings' if b else 'context'
            if (wx,y,wz) in overlay_tags:tag=overlay_tags[wx,y,wz];layer='road'
            subset[x,y,z]=(state,tag,layer)
        for (x,y,z),d in holes.items():
            if (x//16,z//16)==key:
                for dy in range(d['headroom']):subset[x%16,y+dy,z%16]=(AIR,d['owner'],'doors')
        builder.add_tile(tx,tz,subset)
    materials={**planner.catalog['materials'],**{k:{'color':v} for k,v in builder.palette.items()}}
    scene=Scene(materials);scene.boxes=builder.boxes;scene.meshes=[]
    terrain=list(planner.original_terrain)
    for e in data['earthwork']:
        x,z=e['column'];terrain[z*planner.model.width+x]=e['air_y']-1
    for g in data['gardens']:
        x0,z0,x1,z1=g['bounds'];y=g['y']
        scene.box(x0,y,z0,x1-x0+1,.12,z1-z0+1,g['access'],'gardens',g['id'])
        if g['gate']:
            gx,_,gz=g['gate']
            for x,z in perimeter(g['bounds']):
                if abs(x-gx)+abs(z-gz)>1:scene.box(x,y,z,1,.5,1,'stone_light','gardens',g['id'])
        for x,py,z in g['path']+g['internal_path']:scene.box(x,py+.13,z,1,.12,1,'route','paths',g['id'])
        for f in g['fixtures']:
            x,y,z=f['position'];w,h,d=f['size']
            scene.box(x,y,z,w,h,d,'leaf' if f['kind']=='herb_beds' else 'wood_light','fixtures',g['id'])
    for d in data['doors']:
        if d['action']=='open_door':
            x,y,z=d['position'];scene.box(x+.15,y,z+.15,.7,2,.7,'door_new','doors',d.get('garden_id',d['owner']))
    return {'boxes':scene.boxes,'meshes':scene.meshes,'materials':materials,'terrain':terrain}
