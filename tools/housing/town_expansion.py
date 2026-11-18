"""向城东扩建住宅坊，大院宅、街路和新城墙一起导出。"""
from __future__ import annotations
from functools import lru_cache
from collections import deque
import gzip,heapq,json,math,struct,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT));sys.path.insert(0,str(ROOT/'tools'))
from tools.housing.town_plan import settings,rules,wall_union
from tools.housing.generate import build_houses
from province_capitals.central.game_models import rotate_state
from province_capitals.game.compile import sha,write,resource_file,life_inputs
from province_capitals.blueprint_plan.nbt_entities import block_entities,write_tile
from province_capitals.blueprint_plan.study_surfaces import in_polygon
from province_capitals.blueprint_plan.manor_relayout import rotate_cell
from province_capitals.blueprint_plan.road_surfaces import RoadSurfaces
from road_port_sources import CitySource


def area(box):
    a,b,e,f=box;return {(x,z)for z in range(b,f)for x in range(a,e)}


def integrate(out):
    out=Path(out);policy=json.loads((ROOT/'blueprints/housing/npc_neighborhood.json').read_text());homes={h['id']:h for h in build_houses()[0]}
    paths={p for folder in ('blueprints/housing','tools/house_choices','tools/complex_houses','tools/house_choices/astra','tools/complex_houses/astra')for p in sorted((ROOT/folder).glob('*'))if p.suffix in('.py','.js','.json')}
    paths.update(ROOT/p for p in json.loads((ROOT/'blueprints/housing/generator_inputs.json').read_text()))
    inputs={str(p.relative_to(ROOT)):sha(p.read_bytes())for p in sorted(paths)}
    reports=[]
    for region,cfg in settings()['regions'].items():
        manifest=out/f'data/yuanxian/capitals/v4/capital_{region}/city.json';city=json.loads(manifest.read_text())
        if city.get('npc_housing'):
            if city['npc_housing'].get('inputs')!=inputs:raise ValueError('NPC 院宅输入已变，须重新导出所属城市 '+region)
            for ref in [city['terrain'],*city['tiles']]:
                resource=resource_file(out,ref.get('resource',ref.get('template')),template='template'in ref)
                if sha(resource.read_bytes())!=ref['sha256']:raise ValueError('扩建住宅坊切片摘要不符 '+region)
            reports.append(city['npc_housing']);continue
        reports.append(expand(out,manifest,city,homes[cfg['npc_house']],region,policy,inputs))
    return reports


def expand(out,manifest,city,home,region,policy,inputs):
    source=CitySource(out,region);ow,oh,od=city['size'];sw,_,sd=home['size'];turn=3;w,d=sd,sw;edge=policy['edge_clearance']
    wall=city['town_wall'];old_wall_cells={tuple(p)for p in wall['cells']};wall_columns={(x,z)for x,y,z in old_wall_cells}
    inside={(x,z)for z in range(od)for x in range(ow)if in_polygon(wall['points'],x+.001,z+.001)}|wall_columns
    protected=set();gates=[]
    for b in city['buildings']:
        lo,hi=b['bounds']['min'],b['bounds']['max'];box=[math.floor(lo[0]),math.floor(lo[2]),math.ceil(hi[0])+1,math.ceil(hi[2])+1]
        if b['role']=='gate':gates.append(area(box))
        protected|=area(box)
    for land in city['player_housing']:
        a,b,e,f=land['bounds'];pad=rules()['public_setback'];protected|=area([a-pad,b-pad,e+pad,f+pad])
    for g in city['blueprint'].get('gardens',[]):
        a,b,e,f=g['bounds'];protected|=area([a,b,e+1,f+1])
    reserved_waystones=set()
    for s in city.get('waystone_reservations',[]):
        a,b,e,f=s['bounds'];protected|=area([a,b,e+1,f+1])
        reserved_waystones|=area([a,b,e+1,f+1])
    for place in city['life']['places']:
        if place.get('block'):
            x,y,z=place['block_pos'];protected.add((x,z))
    original_roads={(x,z):y*2 for r in city['roads']for x,y,z in r.get('surface_columns',r['points'])}
    original_roads.update({(x,z):y*2 for r in city.get('access_paths',[])for x,y,z in r['points']})
    road_points=[tuple(p)for r in city['roads']if r['width']>=3 for p in r['points']if(p[0],p[2])not in protected]
    if not road_points:raise ValueError('扩建住宅坊缺少公共街路 '+region)
    candidates=sorted(set(road_points),key=lambda p:(ow-p[0],abs(p[2]-od*.65),p));chosen=None
    for anchor in candidates:
        ax,ay,az=anchor;z0=max(edge,min(od-edge-d,az-d//2));za=max(1,z0-edge//2);zb=min(od-1,z0+d+edge//2)
        if any((x,z)in protected or not(0<=x<ow and 0<=z<od)or source.floor(x,z,12,policy['headroom'])!=ay*2 for z in range(az-1,az+2)for x in range(ax-1,ax+2)):continue
        if any(any(za<=z<zb for x,z in gate)for gate in gates):continue
        if any(za<=z<zb for x,z in inside):chosen=(anchor,z0,za,zb);break
    if chosen is None:raise ValueError('扩建区无法避开既有城门 '+region)
    (ax,ay,az),z0,za,zb=chosen;feet2=source.floor(ax,az,12,policy['headroom'])
    if feet2 is None:raise ValueError('扩建接点没有真实地坪 '+region)
    feet=(feet2+1)//2;x0=ow+policy['gap'];nw=math.ceil((x0+w+edge)/16)*16;nd=od
    if feet+max(p[1]for p in home['blocks'])>=oh:raise ValueError('院宅超出城市高度 '+region)
    right=max(x for x,z in inside if za<=z<zb);xa=max(0,right-edge);annex=area([xa,za,nw-4,zb]);expanded=inside|annex
    seen={next(iter(expanded))};todo=deque(seen)
    while todo:
        x,z=todo.popleft()
        for p in((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
            if p in expanded and p not in seen:seen.add(p);todo.append(p)
    if seen!=expanded:raise ValueError('扩建城域出现未接通的区域 '+region)
    boundary={p for p in expanded if any((p[0]+dx,p[1]+dz)not in expanded for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)))}
    old_boundary={p for p in inside if any((p[0]+dx,p[1]+dz)not in inside for dx,dz in((1,0),(-1,0),(0,1),(0,-1)))}
    gate_distances=[]
    for gate in gates:
        old_distance=min(abs(x-a)+abs(z-b)for x,z in gate for a,b in old_boundary)
        new_distance=min(abs(x-a)+abs(z-b)for x,z in gate for a,b in boundary)
        if new_distance>old_distance:raise ValueError('扩建把原城门留在了内墙 '+region)
        gate_distances.append({'before':old_distance,'after':new_distance})
    heights=[source.terrain[z*ow+min(x,ow-1)]for z in range(nd)for x in range(nw)]
    for x,z in annex:
        if x>=ow:heights[z*nw+x]=feet-1
    dirty={};entities={};refs={(r['x']//16,r['z']//16):r for r in city['tiles']}
    def tile(key):
        if key not in dirty:
            if key in refs:
                source.block(key[0]*16,0,key[1]*16);dirty[key]={p:(s,'housing','expansion')for p,s in source.cache[key].items()}
                entities[key]=block_entities(resource_file(out,refs[key]['template'],template=True))
            else:dirty[key]={};entities[key]={}
        return dirty[key]
    def put(p,state):
        x,y,z=p
        if not(0<=x<nw and 0<=z<nd and 0<=y<oh):raise ValueError('住宅坊方块越界 '+str(p))
        tile((x//16,z//16))[x%16,y,z%16]=(state,'npc_housing','expansion')
    def point(p):
        x,y,z=p;u,v=rotate_cell(x,z,sw,sd,turn);return x0+u,feet+y,z0+v
    entry=point(home['entry']);start=(x0-1,entry[2]);goal=(ax,az);radius=policy['road_width']//2
    @lru_cache(maxsize=None)
    def floor(x,z):
        if x>=ow:return feet*2
        if(x,z)in wall_columns:return(source.terrain[z*ow+x]+1)*2
        actual=source.floor(x,z,12,policy['headroom'])
        if actual is not None:return actual
        bridge=policy['water_bridges'];ground=source.terrain[z*ow+x]
        if region in bridge['regions']and 0<feet-ground<=rules()['max_earthwork']and source.block(x,ground+1,z).startswith('minecraft:water')and all(source.empty(source.block(x,feet+dy,z))for dy in range(policy['headroom'])):return feet*2
        return None
    @lru_cache(maxsize=None)
    def valid(x,z):
        for zz in range(z-radius,z+radius+1):
            for xx in range(x-radius,x+radius+1):
                if not(0<=xx<nw and 0<=zz<nd)or(xx,zz)not in expanded or(xx,zz)in protected:return False
                y=floor(xx,zz)
                if y is None or abs(y-feet*2)>rules()['max_earthwork']*2:return False
                if(xx,zz)in original_roads and original_roads[xx,zz]!=feet*2:return False
        return True
    costs={start:0};previous={start:None};queue=[(0,0,start)]
    while queue:
        _,cost,p=heapq.heappop(queue)
        if cost!=costs[p]:continue
        if p==goal:break
        for dx,dz in((1,0),(-1,0),(0,1),(0,-1)):
            q=p[0]+dx,p[1]+dz
            if not valid(*q):continue
            value=cost+10+abs(floor(*q)-feet*2)
            if value<costs.get(q,10**12):costs[q]=value;previous[q]=p;heapq.heappush(queue,(value+10*(abs(q[0]-goal[0])+abs(q[1]-goal[1])),value,q))
        if len(costs)>rules()['road_search_limit']:raise ValueError('扩建接路搜索超出边界 '+region)
    if goal not in previous:raise ValueError('扩建住宅坊不能接回原城 '+region)
    route=[];p=goal
    while p is not None:route.append(p);p=previous[p]
    route.reverse()
    road={'id':'npc_neighborhood','name':'院宅街坊接街','width':policy['road_width'],'points':[[x,feet,z]for x,z in route],'surface':'lane','kind':'street'}
    new_field={}
    for a,b in zip(route,route[1:]):
        dx,dz=b[0]-a[0],b[1]-a[1]
        for x,z in(a,b):
            for k in range(-radius,radius+1):new_field[x+(k if dz else 0),z+(k if dx else 0)]=feet
    road['surface_columns']=[[x,y,z]for(x,z),y in sorted(new_field.items())]
    surfaces=RoadSurfaces(region,[road],new_field,{p:{road['id']}for p in new_field})
    plaza=surfaces.oriented(surfaces.profile['civic'],'z')
    for p in old_wall_cells:
        if source.block(*p)!=wall['material']:raise ValueError('原城墙清单与实际方块不符')
        put(p,'minecraft:air')
    new_wall_cells=[]
    for x,z in boundary:
        if(x,z)in protected or(x,z)in original_roads:continue
        y=heights[z*nw+x]+1
        for yy in range(y,y+wall['height']):put((x,yy,z),wall['material']);new_wall_cells.append([x,yy,z])
    for x,z in annex:
        if x>=ow and(x,z)not in boundary:put((x,feet-1,z),'minecraft:grass_block'if(x,z)in reserved_waystones else plaza)
    blocks={point(p):rotate_state(state,turn)for p,state in home['blocks'].items()}
    for p,state in blocks.items():put(p,state)
    for x,z in route:
        for zz in range(z-radius,z+radius+1):
            for xx in range(x-radius,x+radius+1):
                if(xx,zz)not in new_field:continue
                if xx<ow and source.floor(xx,zz,12,policy['headroom'])==feet*2 and(xx,zz)not in wall_columns:continue
                heights[zz*nw+xx]=min(heights[zz*nw+xx],feet-2);put((xx,feet-1,zz),surfaces.state(xx,zz))
                for yy in range(feet,feet+policy['headroom']):put((xx,yy,zz),'minecraft:air')
                if xx<ow and source.floor(xx,zz,12,policy['headroom'])is None:
                    bridge=policy['water_bridges'];spacing=bridge['support_spacing']
                    if xx%spacing==0 and zz%spacing==0:
                        for yy in range(source.terrain[zz*ow+xx]+1,feet-1):put((xx,yy,zz),bridge['support'])
    occupied_columns={(x,z)for x,y,z in blocks if y>=feet}
    for x in range(start[0],entry[0]+1):
        for z in range(entry[2]-1,entry[2]+2):
            if(x,z)not in occupied_columns and(x,feet-1,z)not in blocks:put((x,feet-1,z),plaza)
    # 两条有收边的绿带靠院宅两侧布置，街口保留完整铺地。
    g=policy['gardens'];gap=g['gap'];depth=g['depth'];inset=g['inset'];greens=[];furniture=[]
    for box in ([x0+inset,z0-gap-depth,x0+w-inset,z0-gap],[x0+inset,z0+d+gap,x0+w-inset,z0+d+gap+depth]):
        a,b,e,f=box;cells=area(box)
        if not cells<=annex or cells&set(new_field)or cells&occupied_columns or cells&boundary:raise ValueError('住宅坊绿带侵入街路或院宅 '+region)
        for x,z in cells:
            edge_cell=x in(a,e-1)or z in(b,f-1);put((x,feet-1,z),plaza if edge_cell else'minecraft:grass_block')
            if edge_cell:put((x,feet,z),g['border'])
        for x in range(a+2,e-1,g['plant_spacing']):put((x,feet,b+1),g['hedge'])
        greens.append(box)
    cfg=policy['street_furniture'];sx=x0-cfg['offset']
    for z in(z0+3,z0+d-4):
        if(sx,z)in new_field or(sx,z)in reserved_waystones:continue
        put((sx,feet,z),cfg['lamp_post']);put((sx,feet+1,z),cfg['lamp_post']);put((sx,feet+2,z),'minecraft:lantern[hanging=false,waterlogged=false]');furniture.append({'kind':'lamp','pos':[sx,feet,z]})
    for z in(entry[2]-cfg['bench_offset'],entry[2]+cfg['bench_offset']):
        if any((sx,zz)in new_field or(sx,zz)in reserved_waystones for zz in(z,z+1)):continue
        for zz in(z,z+1):put((sx,feet,zz),cfg['bench'])
        furniture.append({'kind':'bench','pos':[sx,feet,z]})
    def get(p):
        x,y,z=p;row=dirty.get((x//16,z//16),{}).get((x%16,y,z%16))
        if row:return row[0]
        return source.block(x,y,z)if x<ow else'minecraft:air'if y>heights[z*nw+x]else'minecraft:dirt'
    def standing(p):
        x,y,z=p
        return get((x,y,z))=='minecraft:air'and get((x,y+1,z))=='minecraft:air'and get((x,y-1,z))not in('minecraft:air','minecraft:water[level=0]')
    places=[]
    for i,bed in enumerate(home['beds']):
        fixture=point(bed);x,y,z=fixture;position=next(((x+dx,y,z+dz)for dx,dz in((-1,0),(1,0),(0,-1),(0,1))if standing((x+dx,y,z+dz))),None)
        if position is None:raise ValueError('NPC 床边没有真实站位 '+str((region,home['id'],fixture)))
        places.append({'id':f'npc_courtyard_rest_{i}','kind':'rest','pos':list(position),'capacity':1,'block':blocks[fixture].split('[',1)[0],'block_pos':list(fixture),'building_id':'npc_courtyard'})
    if not places:raise ValueError('NPC 院宅没有床位')
    households=[]
    for i,home_place in enumerate(list(places)):
        position=[start[0],feet,start[1]+(-3 if i%2==0 else 3)];work=f'npc_courtyard_social_{i}'
        if not standing(tuple(position)):raise ValueError('NPC 宅前生活站位受阻 '+region)
        places.append({'id':work,'kind':'social','pos':position,'capacity':1,'block':'','block_pos':position,'building_id':'npc_courtyard'})
        households.append({'home':home_place['id'],'work':work})
    if any((x,z)not in expanded for x,y,z in blocks):raise ValueError('NPC 院宅超出新城墙')
    city['life']['places'].extend(places)
    city['npc_households']=households
    city['buildings'].append({'id':'npc_courtyard','name':home['name'],'role':'house','family':home['group_id'],'template':home['id'],'position':[x0,feet,z0],
        'size':[w,home['size'][1],d],'bounds':{'min':[x0,feet-2,z0],'max':[x0+w-1,feet+max(p[1]for p in home['blocks']),z0+d-1]},'entrance':list(entry),'road_id':'npc_neighborhood','rotation_quarters':turn})
    city['roads'].append(road)
    changed=[p for p,state in blocks.items()if get(p)!=state]
    if changed:raise ValueError('接街或街边布置覆盖了正式院宅模板 '+str((region,changed[:8])))
    for key,cells in sorted(dirty.items()):
        tx,tz=key;ref=refs.get(key);file=resource_file(out,ref['template'],template=True)if ref else out/f'data/yuanxian/structure/province_capitals/v4/capital_{region}/tile_{tx}_{tz}.nbt'
        count,pal,_=write_tile(file,cells,oh,entities[key]);record={'x':tx*16,'z':tz*16,'template':f'yuanxian:province_capitals/v4/capital_{region}/tile_{tx}_{tz}',
            'sha256':sha(file.read_bytes()),'bytes':file.stat().st_size,'block_count':count,'palette_count':pal}
        if ref:ref.update(record)
        else:city['tiles'].append(record)
    terrain=resource_file(out,city['terrain']['resource']);terrain.write_bytes(gzip.compress(struct.pack('<'+'h'*len(heights),*heights),mtime=0))
    city['size']=[nw,oh,nd];city['terrain'].update(width=nw,depth=nd,sha256=sha(terrain.read_bytes()))
    city['palette_states']=sorted(set(city['palette_states'])|{v[0]for rows in dirty.values()for v in rows.values()});city['coverage']['houses']+=1;city['coverage']['buildings']+=1
    city['coverage'].update(templates=len(city['tiles']),life_places=len(city['life']['places']),recorded_blocks=sum(r['block_count']for r in city['tiles']))
    city['npc_housing']={'template':home['id'],'name':home['name'],'bounds':[x0,z0,x0+w,z0+d],'entry':list(entry),'rest_places':len(households),'inside_town_wall':True,'retired_small_houses':[],
        'original_size':[ow,oh,od],'expanded_size':[nw,oh,nd],'wall_boundary_cells':len(boundary),'original_gates_kept':len(gates),'inputs':inputs,'player_homes_excluded':True,
        'landscape':{'surface_profile':surfaces.profile['name'],'green_strips':greens,'street_furniture':furniture},'gate_wall_distances':gate_distances}
    city['town_wall'].update(points=wall_union(wall['points'],[[xa,za,nw-4,zb]]),cells=new_wall_cells,expansion_bounds=[xa,za,nw-4,zb],boundary_columns=[list(p)for p in sorted(boundary)])
    life_inputs(city,nw,oh,nd,192);write(manifest,city);print(region+'：扩建住宅坊 '+home['name'],flush=True)
    return city['npc_housing']
