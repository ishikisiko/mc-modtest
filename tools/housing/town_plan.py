"""把已确认的七城宅地和千机阁布置应用到正式构造输入。"""
from __future__ import annotations
from collections import deque
import copy,heapq,json,math
from functools import lru_cache
from itertools import count
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]


@lru_cache(maxsize=1)
def settings():return json.loads((ROOT/'blueprints/housing/towns.json').read_text())


@lru_cache(maxsize=1)
def rules():return json.loads((ROOT/'blueprints/housing/placement_rules.json').read_text())


def move_box(b,dx,dz):return [b[0]+dx,b[1]+dz,b[2]+dx,b[3]+dz]


def private_link(owner,garden):
    """紧邻私院的连接带属于该户，公共巷道须绕行。"""
    a,b,e,f=owner;x,z,u,v=garden
    if max(b,z)<min(f,v):
        if 0<=x-e<=3:return {(xx,zz)for xx in range(e,x)for zz in range(max(b,z),min(f,v))}
        if 0<=a-u<=3:return {(xx,zz)for xx in range(u,a)for zz in range(max(b,z),min(f,v))}
    if max(a,x)<min(e,u):
        if 0<=z-f<=3:return {(xx,zz)for zz in range(f,z)for xx in range(max(a,x),min(e,u))}
        if 0<=b-v<=3:return {(xx,zz)for zz in range(v,b)for xx in range(max(a,x),min(e,u))}
    return set()


def wall_union(points,boxes):
    from province_capitals.blueprint_plan.study_surfaces import in_polygon
    if not boxes:return points
    lo=min(x for x,z in points);hi=max(x for x,z in points);top=min(z for x,z in points);bottom=max(z for x,z in points)
    cells={(x,z)for z in range(top,bottom)for x in range(lo,hi)if in_polygon(points,x+.5,z+.5)}
    for a,b,e,f in boxes:cells.update((x,z)for z in range(b,f)for x in range(a,e))
    seen={next(iter(cells))};queue=deque(seen)
    while queue:
        x,z=queue.popleft()
        for p in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
            if p in cells and p not in seen:seen.add(p);queue.append(p)
    if seen!=cells:raise ValueError('扩建城墙区域没有接通')
    edges={}
    def edge(a,b):
        if a in edges:raise ValueError('扩建城墙在角点分叉')
        edges[a]=b
    for x,z in cells:
        if(x,z-1)not in cells:edge((x,z),(x+1,z))
        if(x+1,z)not in cells:edge((x+1,z),(x+1,z+1))
        if(x,z+1)not in cells:edge((x+1,z+1),(x,z+1))
        if(x-1,z)not in cells:edge((x,z+1),(x,z))
    start=min(edges);at=start;loop=[]
    while True:
        loop.append(at);at=edges.pop(at)
        if at==start:break
    if edges:raise ValueError('扩建城墙出现内圈')
    result=[]
    for i,p in enumerate(loop):
        a=loop[i-1];b=loop[(i+1)%len(loop)]
        if(p[0]-a[0],p[1]-a[1])!=(b[0]-p[0],b[1]-p[1]):result.append(list(p))
    return result+[result[0]]


def prepare(region,city,environment,layout,include_courts=True):
    c=copy.deepcopy(settings()['regions'][region]);city=copy.deepcopy(city);environment=copy.deepcopy(environment);layout=copy.deepcopy(layout)
    sx,sz=c['city_shift'];city['dimensions']=c['dimensions'];layout['dimensions']=c['dimensions']
    # 地形控制点与来源房屋一起平移，模板内部仍消费来源的局部坐标。
    terrain=city['terrain']
    for key in ('shore_x_by_z','stream_x_by_z','rift_x_by_z'):
        if key in terrain:terrain[key]=[[z+sz,x+sx]for z,x in terrain[key]]
    if 'center'in terrain:terrain['center']=[terrain['center'][0]+sx,terrain['center'][1]+sz]
    if 'ravine'in terrain:
        terrain['ravine']['center_x']+=sx;terrain['ravine']['z_range']=[z+sz for z in terrain['ravine']['z_range']]
    if 'shelves'in terrain:terrain['shelves']=[[z+sz,y]for z,y in terrain['shelves']]
    for patch in city.get('terrain_patches',[]):
        if 'bounds'in patch:patch['bounds']=move_box(patch['bounds'],sx,sz)
    for b in city['buildings']:b['position'][0]+=sx;b['position'][2]+=sz
    for entry in city.get('external_entries',[]):entry['position'][0]+=sx;entry['position'][2]+=sz
    for court in city['courtyards']:court['center'][0]+=sx;court['center'][2]+=sz
    for district in city['districts']:district['bounds']=move_box(district['bounds'],sx,sz)
    retired=set(layout['retired_buildings'])|set(c['retired_houses']);layout['retired_buildings']=sorted(retired)
    final={b['id']:b['box']for b in c['buildings']};deltas={}
    active=[];moved=[]
    for b in layout['buildings']:
        if b['id']in retired:continue
        old=b['box'];new=final.get(b['id'],move_box(old,sx,sz));deltas[b['id']]=[new[0]-old[0],new[1]-old[1]]
        if new!=move_box(old,sx,sz):b['status']='relocated';moved.append(b['id'])
        b['box']=new;active.append(b)
    layout['buildings']=active
    absorbed={gid for q in c['qianji']for gid in q['courts']if gid not in c['entity_moves']}
    gardens=[]
    for g in layout['gardens']:
        if g.get('owner')in retired or g['id']in absorbed:continue
        if g.get('owner')in deltas:dx,dz=deltas[g['owner']]
        elif g['id']in c['entity_moves']:
            m=c['entity_moves'][g['id']];dx=m['to'][0]-m['from'][0]+sx;dz=m['to'][1]-m['from'][1]+sz
        else:dx,dz=sx,sz
        g['box']=move_box(g['box'],dx,dz);gardens.append(g)
    court_file=ROOT/'blueprints/housing/court_layouts.json'
    courts=json.loads(court_file.read_text())['regions'][region]if include_courts and court_file.exists()else{'gardens':{},'wall_additions':[]}
    for g in gardens:
        if g['id']in courts['gardens']:
            old=g['box'];new=courts['gardens'][g['id']]
            if(old[2]-old[0])*(old[3]-old[1])!=(new[2]-new[0])*(new[3]-new[1]):raise ValueError('私院重新选址改变面积 '+g['id'])
            g['box']=new
    changed_owners={g['owner']for g in gardens if g['id']in courts['gardens']and g.get('owner')}
    moved=sorted(set(moved)|changed_owners)
    layout['gardens']=gardens
    layout['roads']=[{k:v for k,v in r.items()if k!='plan_status'}for r in c['roads']]
    layout['access_hint']=[h for h in layout['access_hint']if h['building_id']not in retired and h['building_id']not in changed_owners and deltas.get(h['building_id'],[sx,sz])==[sx,sz]]
    for h in layout['access_hint']:h['points']=[[x+sx,z+sz]for x,z in h['points']]
    for p in layout['public_passages']:p['bounds']=move_box(p['bounds'],sx,sz)
    for b in layout['water_channels']:
        b[:]=move_box(b,sx,sz)
    for key in ('manor','landmark'):
        layout[key]['bounds']=move_box(layout[key]['bounds'],sx,sz)
    layout['manor']['site']=[layout['manor']['site'][0]+sx,layout['manor']['site'][1]+sz]
    layout['landmark']['origin'][0]+=sx;layout['landmark']['origin'][2]+=sz
    if 'reserve'in environment:environment['reserve']=move_box(environment['reserve'],sx,sz)
    else:
        a,b,e,f=layout['landmark']['bounds'];environment['reserve']=[a,b,e-1,f-1]
    if 'landmark_origin'in environment:environment['landmark_origin'][0]+=sx;environment['landmark_origin'][2]+=sz
    else:environment['landmark_origin']=layout['landmark']['origin'][:]
    environment['wall']['points']=c['wall']or[[x+sx,z+sz]for x,z in environment['wall']['points']]
    environment['wall']['points']=wall_union(environment['wall']['points'],courts['wall_additions'])
    for gate in environment['wall'].get('gates',[]):
        if 'bounds'in gate:gate['bounds']=move_box(gate['bounds'],sx,sz)
    sources={b['id']:b for b in city['buildings']}
    layout['expected']['houses_after']=sum(sources[b['id']]['role']=='house'for b in active)
    layout['_housing']=c
    layout['_housing_moved']=moved
    return city,environment,layout


def reserve(planner):
    c=planner.layout['_housing'];land=c['player_land'];a,b,e,f=land['box']
    heights=[planner.ground(x,z)for z in range(b,f)for x in range(a,e)];ground=(min(heights)+max(heights))//2
    if max(abs(v-ground)for v in heights)>rules()['max_earthwork']:raise ValueError('宅地整地超出作者规则 '+planner.region)
    for z in range(b,f):
        for x in range(a,e):planner.set_ground(x,z,ground)
    land['floor_y']=ground+1
    # 退界带不属于玩家净用地；道路寻路不能穿过宅地。
    planner.housing_reserved={(x,z)for z in range(b,f)for x in range(a,e)}
    if planner.housing_reserved&planner.housing_waystones:raise ValueError('玩家宅地覆盖保留传送亭台 '+planner.region)
    planner.housing_models=[]
    from qianji.model import Model
    spec=json.loads((ROOT/'blueprints/qianji'/(c['qianji_variant']+'.json')).read_text())
    from province_capitals.blueprint_plan.manor_relayout import rotate_cell
    from province_capitals.central.game_models import rotate_state
    for q in c['qianji']:
        room=next(r for r in spec['rooms']if r['id']==q['room']);model=Model(spec);model.hall(room);model.illuminate()
        x0,z0,w,d=room['box'];a,b,e,f=q['box'];turn=q['rotation_quarters'];floor=max(planner.ground(x,z)for z in range(b,f)for x in range(a,e))+1
        def point(pos):
            x,y,z=pos;u,v=rotate_cell(x-x0,z-z0,w,d,turn);return a+u,floor+y-room['floor_y'],b+v
        blocks={point(p):rotate_state(state,turn)for p,state in model.blocks.items()if p[1]>=room['floor_y']-2}
        # 入口取真实前廊外一格，避免入户寻路更改原作楼梯或大门。
        entry=point((x0+w//2,room['floor_y'],z0));vx,vz=((0,-1),(1,0),(0,1),(-1,0))[turn];outside=[entry[0]+vx,entry[1],entry[2]+vz]
        low=[min(p[i]for p in blocks)for i in range(3)];hi=[max(p[i]for p in blocks)for i in range(3)]
        q.update(floor_y=floor,entry=outside,blocks=blocks,source_room=room,model=model,
                 signs=[{**sign,'position':list(point(sign['position']))}for sign in model.signs])
        planner.housing_models.append(q)
        columns={(x,z)for z in range(b,f)for x in range(a,e)}|{(x,z)for(x,y,z),state in blocks.items()if state!='minecraft:air'and floor-1<=y<floor+planner.cfg['headroom']}
        for x,z in columns:
            if (x,z)in planner.building_columns or (x,z)in planner.core or (x,z)in planner.private_owner or (x,z)in planner.housing_waystones:raise ValueError('千机阁入城覆盖保留院宅 '+q['id']+' '+str((x,z)))
            planner.building_columns[x,z]=q['id']
        from province_capitals.blueprint_plan.manor_relayout import solid
        planner.floor_columns.update((x,z)for(x,y,z),state in blocks.items()if y==floor-1 and solid(state)
            and all(blocks.get((x,floor+k,z),'minecraft:air')=='minecraft:air'for k in range(planner.cfg['headroom'])))
        planner.summaries[q['id']]={'id':q['id'],'name':q['name'],'role':'qianji','family':'qianji','size':[e-a,hi[1]-low[1]+1,f-b],
            'position':[a,floor,b],'bounds':{'min':low,'max':hi},'rotation_quarters':turn,'entrance':outside,'volumes':[],'road_id':''}
        planner.parcels[q['id']]={'id':q['id'],'box':q['box']}
    planner.layout['expected']['services']+=len(planner.housing_models)


def route_roads(planner,roads):
    """保留道路端点和完整路幅，只绕开新房屋、宅田及调整后的庭院。"""
    blocked=set(planner.housing_reserved)|planner.core|set(planner.private_owner)|planner.public_garden_columns|planner.housing_waystones
    blocked|={p for p,owner in planner.building_columns.items()if planner.summaries[owner]['role']!='gate'}
    # 门墩只能按原作者声明的开口通行；绕路不能横切门楼的实墙。
    from province_capitals.regional.v2.common import point
    from province_capitals.blueprint_plan.manor_relayout import passable
    passages={o['owner']:o['bounds']for o in planner.layout['public_passages']}
    for bid,template in planner.templates.items():
        if planner.summaries[bid]['role']!='gate':continue
        opening=passages.get(bid)
        for (x,y,z),(state,*unused)in template.cells.items():
            if not 0<=y<planner.cfg['headroom']or passable(state):continue
            xx,yy,zz=point(template.source,x,y,z)
            if opening and opening[0]<=xx<=opening[2]and opening[1]<=zz<=opening[3]:continue
            blocked.add((xx,zz))
    W,D=planner.width,planner.depth;serial=count();result=[]
    for r in roads:
        radius=r['width']//2
        def valid(p):
            x,z=p
            return 0<=x<W and 0<=z<D and all((xx,zz)not in blocked for zz in range(max(0,z-radius),min(D,z+radius+1))for xx in range(max(0,x-radius),min(W,x+radius+1)))
        original=[]
        for a,b in zip(r['points'],r['points'][1:]):
            n=max(abs(a[0]-b[0]),abs(a[1]-b[1]))
            if a[0]!=b[0]and a[1]!=b[1]:raise ValueError('城市道路须保持正交')
            original.extend((a[0]+(b[0]-a[0])*i//max(1,n),a[1]+(b[1]-a[1])*i//max(1,n))for i in range(n+1))
        original=[p for i,p in enumerate(original)if i==0 or p!=original[i-1]];indices=[i for i,p in enumerate(original)if valid(p)]
        if not indices:raise ValueError('道路已完全被地块覆盖 '+r['id'])
        first,last=indices[0],indices[-1];path=[original[first]];i=first;preferred=set(original)
        while i<last:
            if valid(original[i+1]):path.append(original[i+1]);i+=1;continue
            j=i+1
            while j<=last and not valid(original[j]):j+=1
            start,goal=original[i],original[j];previous={start:None};cost={start:0};queue=[(0,0,next(serial),start)]
            while queue:
                _,price,_,at=heapq.heappop(queue)
                if price!=cost[at]:continue
                if at==goal:break
                for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
                    n=(at[0]+dx,at[1]+dz)
                    if not valid(n):continue
                    value=price+(10 if n in preferred else 12)+abs(planner.ground(*at)-planner.ground(*n))
                    if value<cost.get(n,10**12):cost[n]=value;previous[n]=at;heapq.heappush(queue,(value+10*(abs(n[0]-goal[0])+abs(n[1]-goal[1])),value,next(serial),n))
                if len(cost)>rules()['road_search_limit']:raise ValueError('城市道路搜索超出边界 '+r['id'])
            if goal not in previous:raise ValueError('城市道路不能绕开新地块 '+r['id'])
            detour=[];at=goal
            while at!=start:detour.append(at);at=previous[at]
            path.extend(reversed(detour));i=j
        # 压缩共线段，正式道路生成器仍负责共享交叉口标高。
        points=[list(path[0])]
        for i in range(1,len(path)-1):
            a,b,c=path[i-1:i+2]
            if(b[0]-a[0],b[1]-a[1])!=(c[0]-b[0],c[1]-b[1]):points.append(list(b))
        if path[-1]!=tuple(points[-1]):points.append(list(path[-1]))
        result.append({**r,'points':points})
    return result


def add_lanes(planner,roads):
    """为迁排后的门口补公共接巷，住宅入户段仍遵守原来的长度限制。"""
    from province_capitals.blueprint_plan.study_surfaces import natural_feature,in_polygon
    c=planner.layout['_housing'];goals=set()
    for r in roads:
        for a,b in zip(r['points'],r['points'][1:]):
            n=max(abs(a[0]-b[0]),abs(a[1]-b[1]))
            for i in range(n+1):goals.add((a[0]+(b[0]-a[0])*i//max(1,n),a[1]+(b[1]-a[1])*i//max(1,n)))
    blocked=set(planner.housing_reserved)|planner.core|set(planner.private_owner)|planner.public_garden_columns|set(planner.building_columns)|planner.housing_waystones
    blocked-=goals;result=list(roads)
    bridges={(x,z)for r in c.get('public_bridges',[])for x in range(r['bounds'][0],r['bounds'][2])for z in range(r['bounds'][1],r['bounds'][3])}
    for width in (rules()['public_alley_width'],rules()['qianji_lane_width']):
        radius=width//2
        @lru_cache(maxsize=None)
        def valid(p):
            x,z=p
            return all(0<=xx<planner.width and 0<=zz<planner.depth and(xx,zz)not in blocked
                and((xx,zz)in goals or in_polygon(planner.env['wall']['points'],xx+.5,zz+.5)and((xx,zz)in bridges or not planner.is_wet(xx,zz)
                and not natural_feature(c['natural_reserve'],planner.city['terrain'],xx+.5,zz+.5,rules()['natural_margin'])))
                for zz in range(z-radius,z+radius+1)for xx in range(x-radius,x+radius+1))
        distance={p:0 for p in goals if valid(p)};parent={p:None for p in distance};queue=[(0,p)for p in distance];heapq.heapify(queue)
        while queue:
            price,p=heapq.heappop(queue)
            if price!=distance[p]:continue
            for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
                n=p[0]+dx,p[1]+dz
                if not valid(n):continue
                value=price+10+abs(planner.ground(*p)-planner.ground(*n))
                if value<distance.get(n,10**12):distance[n]=value;parent[n]=p;heapq.heappush(queue,(value,n))
        ids=planner.layout['_housing_moved']if width==rules()['public_alley_width']else[q['id']for q in planner.housing_models]
        for bid in ids:
            b=planner.summaries[bid];ex,ey,ez=b['entrance'];limit=planner.model.geometry.get('max_access_length',planner.model.geometry['access_max_distance'])
            candidates=[p for p in distance if abs(p[0]-ex)+abs(p[1]-ez)<limit//2]
            if not candidates:raise ValueError('迁排门口没有公共接巷空间 '+bid)
            start=min(candidates,key=lambda p:(abs(p[0]-ex)+abs(p[1]-ez),distance[p],p));path=[];at=start
            while at is not None:path.append(list(at));at=parent[at]
            if len(path)<2:continue
            result.append({'id':'housing_lane_'+bid,'name':b['name']+'接巷','width':width,'points':path,'kind':'bridge'if any(tuple(p)in bridges for p in path)else'alley','surface':'lane'})
    return result


def emit(planner):
    from province_capitals.regional.v2.common import AIR
    for q in planner.housing_models:
        for p,state in q['blocks'].items():planner.set(p,state,q['id'],'qianji')
        ex,ey,ez=q['entry'];planner.emit_floor(ex,ey,ez,q['id']+'_门前','road')
    # 把地块的实际地坪和两格净空写入切片，来源地形中的旧路不得残留。
    land=planner.layout['_housing']['player_land'];a,b,e,f=land['box'];feet=land['floor_y']
    for z in range(b,f):
        for x in range(a,e):
            planner.set((x,feet-1,z),'minecraft:grass_block',land['id'],'player_land')
            planner.box((x,feet,z),(x,feet+2,z),AIR,land['id'],'player_land')


def connect_plot(planner):
    land=planner.layout['_housing']['player_land'];a,b,e,f=land['box'];y=land['floor_y'];goals={(x,y,z)for(x,z),y in planner.road_field.items()}
    candidates=[(a-1,z)for z in range(b+4,f-4)]+[(e,z)for z in range(b+4,f-4)]+[(x,b-1)for x in range(a+4,e-4)]+[(x,f)for x in range(a+4,e-4)]
    def distance(p):return min(abs(p[0]-x)+abs(p[1]-z)for x,z in planner.road_field)
    for x,z in sorted(candidates,key=distance):
        if not(1<=x<planner.width-1 and 1<=z<planner.depth-1)or(x,z)in planner.building_columns or(x,z)in planner.core:continue
        path=planner.path((x,y,z),goals,limit=128)
        surface=plot_surface(planner,path)if path else None
        if surface is not None:break
    else:raise ValueError('玩家宅地没有可通行的公共接路 '+planner.region)
    # 路幅和退界均由配置定义；先检查，再写入全宽地坪。
    for (x,z),feet in surface.items():
        if (x,z)in planner.road_field or(x,z)in planner.access_field:continue
        planner.emit_floor(x,feet,z,land['id']+'_接路','road')
    entry=path[0];land['entry']=list(entry)
    # 执事沿接路向外站两格，不占据净用地或入口中央。
    candidates=[(entry[0]+dx,entry[1],entry[2]+dz)for dx,dz in ((2,0),(-2,0),(0,2),(0,-2))]
    for p in candidates:
        if (p[0],p[2])in planner.housing_reserved or(p[0],p[2])in planner.building_columns or(p[0],p[2])in planner.core or(p[0],p[2])in planner.housing_waystones:continue
        px,py,pz=p;py=planner.road_field.get((px,pz),planner.access_field.get((px,pz),surface.get((px,pz),py)))
        if(px,pz)not in planner.road_field and(px,pz)not in planner.access_field and(px,pz)not in surface:planner.emit_floor(px,py,pz,land['id']+'_执事','road')
        if not planner.walkable((px,py,pz)):continue
        land['steward']=[px,py,pz];break
    else:raise ValueError('宅地执事没有公共站位')
    planner.housing_plot_path=path
    planner.housing_plot_surface=surface


def plot_surface(planner,path):
    radius=rules()['player_access_width']//2;field={};fixed={}
    for x,y,z in path:
        fixed[x,z]=y
        for dz in range(-radius,radius+1):
            for dx in range(-radius,radius+1):
                p=x+dx,z+dz
                if p in planner.housing_reserved:continue
                if not(0<=p[0]<planner.width and 0<=p[1]<planner.depth)or p in planner.building_columns or p in planner.core or p in planner.private_owner or p in planner.housing_waystones:return None
                field[p]=max(field.get(p,0),y)
    existing={**planner.road_field,**planner.access_field}
    for p in list(field):
        for dx,dz in ((0,0),(1,0),(-1,0),(0,1),(0,-1)):
            q=p[0]+dx,p[1]+dz
            if q in existing:
                if q in fixed and fixed[q]!=existing[q]:return None
                fixed[q]=existing[q];field[q]=existing[q]
    field.update(fixed);ceiling=dict(fixed);queue=[(y,p)for p,y in fixed.items()];heapq.heapify(queue)
    while queue:
        y,p=heapq.heappop(queue)
        if ceiling[p]!=y:continue
        for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
            q=p[0]+dx,p[1]+dz
            if q in field and y+1<ceiling.get(q,10**6):ceiling[q]=y+1;field[q]=min(field[q],y+1);heapq.heappush(queue,(y+1,q))
    queue=[(-y,p)for p,y in field.items()];heapq.heapify(queue)
    while queue:
        neg,p=heapq.heappop(queue);y=-neg
        if field[p]!=y:continue
        for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
            q=p[0]+dx,p[1]+dz
            if q in field and field[q]<y-1:field[q]=y-1;heapq.heappush(queue,(-(y-1),q))
    if any(field[p]!=y for p,y in fixed.items()):return None
    if any(abs(y-(planner.ground(*p)+1))>rules()['max_earthwork'] for p,y in field.items()if p not in existing):return None
    return field


def landscape(planner):
    """街坊完成面铺到原地面；绿植只进入有边界的庭院。"""
    from province_capitals.blueprint_plan.study_surfaces import in_polygon,natural_feature
    from province_capitals.blueprint_plan.manor_relayout import half_cells
    from lord_manors.native_trees import load as native_trees
    c=planner.layout['_housing'];wall=planner.env['wall']['points'];W,D=planner.width,planner.depth
    protected=set(planner.building_columns)|planner.core|set(planner.private_owner)|planner.housing_reserved|set(planner.road_field)|set(planner.access_field)|planner.housing_waystones
    protected|={p for g in planner.layout['gardens']for p in half_cells(g['box'])}
    safe=set()
    for z in range(D):
        for x in range(W):
            p=x,z
            if p in protected or not in_polygon(wall,x+.5,z+.5)or natural_feature(c['natural_reserve'],planner.city['terrain'],x+.5,z+.5,
                rules()['natural_margin']):continue
            safe.add(p)
    # 固定树院的方向，原位置与新宅田冲突时，在可用院场中选最近的完整矩形。
    green=[];sx,sz=c['city_shift']
    for source in c['green_sites']:
        original=move_box(source['box'],sx,sz);target=((original[0]+original[2])/2,(original[1]+original[3])/2);chosen=None
        for width,depth in [(original[2]-original[0],original[3]-original[1]),*rules()['green_fallback_sizes']]:
            candidates=[]
            for z in range(4,D-depth-3,2):
                for x in range(4,W-width-3,2):
                    cells={(xx,zz)for zz in range(z,z+depth)for xx in range(x,x+width)}
                    if not cells<=safe:continue
                    heights=[planner.ground(*p)for p in cells]
                    if max(heights)-min(heights)>rules()['green_max_height_range']:continue
                    candidates.append((math.dist((x+width/2,z+depth/2),target),[x,z,x+width,z+depth],cells))
            if candidates:chosen=min(candidates,key=lambda r:(r[0],r[1]));break
        if chosen:
            _,bounds,cells=chosen;safe-=cells;green.append({**source,'box':bounds})
    material=planner.surfaces.profile['lane_base'];paved=0
    for x,z in sorted(safe):
        y=planner.ground(x,z);above=planner.model.construction.record_at((x,y+1,z))
        if above is not None and above[0]!='minecraft:air':continue
        # 保留道路、平台和庭院已经铺好的完成面。
        record=planner.model.construction.record_at((x,y,z))
        if record is None or record[0]in('minecraft:dirt','minecraft:grass_block','minecraft:coarse_dirt'):
            planner.set((x,y,z),material,'街坊院场','urban_surface');paved+=1
    tree_data=native_trees();templates=[]
    for data in tree_data['cities'].values():
        cells={tuple(p):state for p,state in data['blocks']if any(t in state for t in ('_log[','_wood[','_leaves['))}
        roots={tuple(t['root'])for t in data['trees']}
        for root in sorted(roots):
            if root not in cells:continue
            todo=[root];connected={root}
            while todo:
                p=todo.pop()
                for delta in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)):
                    q=tuple(p[i]+delta[i]for i in range(3))
                    if q in cells and q not in connected:connected.add(q);todo.append(q)
            if len(connected&roots)!=1:continue
            extent=[max(p[i]for p in connected)-min(p[i]for p in connected)+1 for i in range(3)]
            if extent[0]>9 or extent[2]>9 or extent[1]>15:continue
            templates.append({tuple(p[i]-root[i]for i in range(3)):cells[p]for p in connected})
        if templates:break
    if green and not templates:raise ValueError('庭院缺少完整的原版树形')
    trees=0
    for site in green:
        a,b,e,f=site['box'];mid=(a+e)//2
        for z in range(b,f):
            for x in range(a,e):
                y=planner.ground(x,z);border=x in (a,e-1)or z in(b,f-1);path=abs(x-mid)<=1
                planner.set((x,y,z),planner.surfaces.profile['civic']if border or path else'minecraft:grass_block',site['id'],'green_court')
                if border and not path:planner.set((x,y+1,z),'minecraft:stone_brick_slab[type=bottom,waterlogged=false]',site['id'],'green_border')
        for x,z in ((a+5,(b+f)//2),(e-6,(b+f)//2)):
            root=(x,planner.ground(x,z)+1,z)
            for tree in templates:
                target={tuple(root[i]+p[i]for i in range(3)):s for p,s in tree.items()}
                if not all(a<xx<e-1 and b<zz<f-1 and planner.get((xx,yy,zz))=='minecraft:air'for xx,yy,zz in target):continue
                planner.set((x,root[1]-1,z),'minecraft:dirt',site['id'],'tree_soil')
                for p,state in target.items():planner.set(p,state,site['id'],'native_tree')
                trees+=1;break
    # 题榜和读榜位置消费城内办公楼的实际前廊标高。
    office=next((q for q in planner.housing_models if q['room']=='office'),planner.housing_models[0]);turn=office['rotation_quarters']
    ex,ey,ez=office['entry'];vx,vz=((0,-1),(1,0),(0,1),(-1,0))[turn];tx,tz=-vz,vx
    board=reader=None
    for forward in (0,1,2,3,4,-1,-2,-3,-4):
        for lateral in (4,-4,6,-6,8,-8,10,-10):
            x,z=ex+tx*lateral+vx*forward,ez+tz*lateral+vz*forward
            xx,zz=x+vx,z+vz
            if any(not(0<=u<planner.width and 0<=v<planner.depth)or(u,v)in(planner.core|set(planner.private_owner)|planner.housing_reserved|set(planner.road_field)|set(planner.access_field)|planner.public_garden_columns)
                or(u,v)in planner.housing_waystones or planner.building_columns.get((u,v),office['id'])!=office['id']for u,v in((x,z),(xx,zz))):continue
            y=office['floor_y']if(x,z)in planner.building_columns else planner.ground(x,z)+1;ry=office['floor_y']if(xx,zz)in planner.building_columns else planner.ground(xx,zz)+1
            if y!=ry or abs(y-ey)>1:continue
            if not all(planner.get((u,y+dy,v))=='minecraft:air'for u,v in((x,z),(xx,zz))for dy in range(planner.cfg['headroom'])):continue
            board=[x,y,z];reader=[xx,y,zz];break
        if board is not None:break
    if board is None:raise ValueError('千机阁题榜缺少路外前坪 '+planner.region)
    for p in (board,reader):
        if not planner.walkable(tuple(p)):planner.emit_floor(*p,'千机阁题榜前坪','road')
    face=('north','east','south','west')[turn]
    old=next((p for p in planner.model.places if p['id']=='jianghu_board'),None)
    if old:
        planner.set(tuple(old['block_pos']),'minecraft:air','旧题榜移入千机阁','organization');planner.model.places.remove(old)
    planner.set(tuple(board),f'yuanxian:jianghu_board[facing={face}]','千机阁题榜','organization')
    planner.model.places.append({'id':'jianghu_board','kind':'entry','pos':reader,'capacity':1,'block':'yuanxian:jianghu_board','block_pos':board,'building_id':'qianji'})
    c['qianji_board']={'position':board,'reader':reader,'facing':face};c['qianji_entry']=office['entry']
    planner.housing_landscape={'paved_columns':paved,'green_courts':green,'native_trees':trees,'natural_reserve':c['natural_reserve']}


def bundle_fields(planner):
    c=planner.layout['_housing'];land=c['player_land'];qs=planner.housing_models
    a=min(q['box'][0]for q in qs);b=min(q['box'][1]for q in qs);e=max(q['box'][2]for q in qs);f=max(q['box'][3]for q in qs)
    qianji={'group':'qianji','variant':c['qianji_variant'],'name':'千机阁城内署区','scale':json.loads((ROOT/'blueprints/qianji'/(c['qianji_variant']+'.json')).read_text())['scale'],
        'entry':c['qianji_entry'],'facing':c['qianji_board']['facing'],'board':c['qianji_board'],'bounds':[a,b,e,f],
        'base_y':min(q['floor_y']for q in qs)-1,'placement':'town_housing_plan','rooms':[
            {'id':q['id'],'source_room':q['room'],'bounds':q['box'],'floor_y':q['floor_y'],'rotation':q['rotation_quarters']}for q in qs]}
    walls=[]
    for (tx,tz),unused in planner.model.construction.by_tile.items():
        for (x,y,z),row in planner.model.construction.tile(tx,tz).items():
            if row[1]=='城防':walls.append([tx*16+x,y,tz*16+z])
    return {'player_housing':[{k:land[k]for k in ('id','name','floor_y','steward','entry')}|{'bounds':land['box']}],
            'qianji':qianji,'housing_landscape':planner.housing_landscape,
            'waystone_reservations':[{'id':s['id'],'bounds':s['bounds']}for s in planner.housing_waystone_sites],
            'town_wall':{'points':planner.env['wall']['points'],'height':planner.cfg['wall_height'],'material':planner.model.material('BASE_STONE'),'cells':walls}}


def entities(planner):
    from qianji.runtime_export import sign_payload
    result={}
    for q in planner.housing_models:
        for sign in q['signs']:
            p=tuple(sign['position']);state=planner.get(p)
            if state.split('[',1)[0].endswith('wall_sign'):result[p]={'block':state.split('[',1)[0],'payload':sign_payload(sign['text'])}
    return result
