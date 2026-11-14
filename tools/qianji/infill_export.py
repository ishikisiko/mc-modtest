"""按已确认的73栋民居清单，把总舵写入中州现有城域。"""
from __future__ import annotations
import gzip,json,math,struct
from pathlib import Path
from .infill import site,read
from .model import build,ROOT,AIR
from province_capitals.environment_v3.world import World
from province_capitals.blueprint_plan.nbt_entities import block_entities,write_tile
from province_capitals.central.game_models import rotate_state
from province_capitals.game.compile import resource_file,write,sha,life_inputs
from buildgen.nbtread import read_gzipped_nbt,state_string

def verify_existing_road_cut(world,x,z,feet,ground):
    if world.ground(x,z)!=ground:raise ValueError('旧路地形列与实际切片不一致 '+str((x,z,ground,world.ground(x,z))))
    if world.get((x,feet-1,z))in(AIR,'minecraft:water','minecraft:lava'):raise ValueError('旧路缺少实际路面支撑 '+str((x,feet,z)))
    return world.walkable((x,feet,z))

def component_summary():
    from .components import compile_components
    from .bookcases import ROLE
    d=compile_components()
    return dict(profiles=len(d['profiles']),bookcases=len(d['scene'].get('bookcases',[])),
                procedural_profiles=sum(any(b[6]==ROLE for b in p)for p in d['profiles']))

def refresh_components(out,city,inputs):
    """在当前正式城市中重导出构件；布局、地形和非千机阁方块必须保持相同。"""
    out=Path(out);cfg=read();previous=city['qianji'];a,b,e,f=cfg['plot'];base=cfg['base_y']
    if previous['bounds']!=cfg['plot']or previous['base_y']!=base:raise ValueError('构件更新改变地块，须先完整重建中州')
    for ref in [city['terrain'],*city['tiles']]:
        path=resource_file(out,ref.get('resource',ref.get('template')),template='template'in ref)
        if sha(path.read_bytes())!=ref['sha256']:raise ValueError('构件更新前正式资源摘要失配')
    m,checks=build('zhongzhou_headquarters')
    if previous['size']!=[m.w,m.h,m.d]or previous['rooms']!=m.rooms:raise ValueError('构件更新改变楼体布局，须先完整重建中州')
    def point(p):return(e-1-p[2],base+p[1],b+p[0])
    targets=[dict(id=t['id'],position=list(point(t['position'])))for t in m.targets]
    if targets!=previous['targets']:raise ValueError('构件更新改变功能点，须先完整重建中州')
    world=World(out,city,json.loads((ROOT/'blueprints/province_capitals/environment_v3/materials.json').read_text()))
    for z in range(m.d):
        for x in range(m.w):
            wx,_,wz=point((x,0,z))
            if world.ground(wx,wz)!=base+int(m.terrain[z*m.w+x]):raise ValueError('构件更新改变地形，须先完整重建中州')
    desired={point(p):rotate_state(s,1)for p,s in m.blocks.items()}
    owned={}
    for tile in city['tiles']:
        if not(tile['x']<e and tile['x']+16>a and tile['z']<f and tile['z']+16>b):continue
        for(lx,y,lz),state in world.tile((tile['x']//16,tile['z']//16)).items():
            x,z=tile['x']+lx,tile['z']+lz
            if a<=x<e and b<=z<f and state.startswith('myvillage:qianji_'):owned[x,y,z]=state
    changes={}
    for p,state in desired.items():
        current=world.get(p)
        if p in owned or state.startswith('myvillage:qianji_'):
            if p not in owned and current!=AIR and current!=state:raise ValueError('构件更新覆盖非千机阁方块 '+str((p,current,state)))
            if current!=state:changes[p]=state
        elif current!=state:raise ValueError('构件更新改变非千机阁方块 '+str((p,current,state)))
    for p in owned.keys()-desired.keys():changes[p]=AIR
    for p,state in changes.items():world.set(p,state,'千机阁构件重导出')
    world.verify_life()
    for tile in city['tiles']:
        key=tile['x']//16,tile['z']//16
        if key not in world.dirty:continue
        path=resource_file(out,tile['template'],template=True);entities=block_entities(path)
        cells={p:(s,'qianji','component_refresh')for p,s in world.tile(key).items()}
        count,pal,_=write_tile(path,cells,world.height,entities)
        _,saved=read_gzipped_nbt(path);palette=[state_string(v)for v in saved['palette']]
        if {tuple(v['pos']):palette[v['state']]for v in saved['blocks']}!={p:v[0]for p,v in cells.items()}:raise ValueError('构件更新切片回读不一致')
        if block_entities(path)!=entities:raise ValueError('构件更新丢失原始方块实体')
        tile.update(block_count=count,palette_count=pal,bytes=path.stat().st_size,sha256=sha(path.read_bytes()))
    city['palette_states']=sorted({s for s in city['palette_states']if not s.startswith('myvillage:qianji_')}|{s for s in desired.values()if s.startswith('myvillage:qianji_')})
    previous.update(inputs=inputs,checks=checks)
    previous['infill']['components']=component_summary()
    write(out/'data/myvillage/capitals/v4/capital_zhongzhou/city.json',city)
    print('总舵构件重导出：'+json.dumps(dict(changed_blocks=len(changes),changed_tiles=len(world.dirty),**component_summary()),ensure_ascii=False),flush=True)
    return previous

def integrate(out,city,inputs):
    from .runtime_export import atmosphere_payload
    out=Path(out);file=out/'data/myvillage/capitals/v4/capital_zhongzhou/city.json';cfg=read()
    previous=city.get('qianji')
    if previous:
        if previous.get('infill',{}).get('id')!=cfg['id']:
            raise ValueError('中州总舵输入变化，须先由 owning 中州导出器重建基础资源')
        if previous['inputs']!=inputs:return refresh_components(out,city,inputs)
        for ref in [city['terrain'],*city['tiles']]:
            p=resource_file(out,ref.get('resource',ref.get('template')),template='template'in ref)
            if sha(p.read_bytes())!=ref['sha256']:raise ValueError('中州总舵输出摘要失配')
        return previous
    if city['size']!=[1200,320,1440]:raise ValueError('城内总舵必须消费当前中州原城域')
    m,checks=build('zhongzhou_headquarters');g=site()['geometry'];retired=set(cfg['approved_retirements']);rows=[r for r in g.rows if r['id']in retired]
    if len(rows)!=len(retired)or any(r['source_kind']!='house'or r['service']for r in rows):raise ValueError('总舵撤并必须恰好对应已获准的普通民居')
    a,b,e,f=cfg['plot'];base=cfg['base_y'];world=World(out,city,json.loads((ROOT/'blueprints/province_capitals/environment_v3/materials.json').read_text()))
    regions=[(a,b,e,f)]+[(r['bounds']['min'][0],r['bounds']['min'][2],r['bounds']['max'][0]+1,r['bounds']['max'][2]+1)for r in rows]
    columns={(x,z)for x0,z0,x1,z1 in regions for z in range(z0,z1)for x in range(x0,x1)};touched={(x//16,z//16)for x,z in columns};retained={}
    for row in g.rows:
        if row['id']in retired:continue
        lo,hi=row['bounds']['min'],row['bounds']['max']
        if not any(lo[0]//16<=tx<=hi[0]//16 and lo[2]//16<=tz<=hi[2]//16 for tx,tz in touched):continue
        for tx in range(lo[0]//16,hi[0]//16+1):
            for tz in range(lo[2]//16,hi[2]//16+1):
                for (lx,y,lz),state in world.tile((tx,tz)).items():
                    x,z=tx*16+lx,tz*16+lz
                    if lo[0]<=x<=hi[0]and lo[2]<=z<=hi[2]and lo[1]<=y<=hi[1]and state!=AIR:retained[x,y,z]=state
    existing_road_readback=0;overbuilt_roads=[]
    for lx,lz,old,new,foot2 in site()['existing_road_cuts']:
        x,z=e-1-lz,b+lx;feet=math.ceil(foot2/2)
        # Existing cuts belong to the original ground, including lane surfaces
        # now covered by the eaves of houses approved for retirement.
        if not verify_existing_road_cut(world,x,z,feet,base+new):overbuilt_roads.append([x,feet,z])
        existing_road_readback+=1
    source_heights=site()['heights']
    for x,z in columns:
        world.terrain[z*world.width+x]=int(source_heights[z,x]);key=x//16,z//16;cells=world.tile(key)
        for p in [p for p in cells if p[0]==x%16 and p[2]==z%16]:del cells[p]
        world.dirty.add(key)
    preserved_roads=0
    for x,z,foot2,face,wood,rid in site()['roads']['columns']:
        if (x,z)not in columns or a<=x<e and b<=z<f:continue
        y=foot2//2-1;state='minecraft:spruce_planks'if wood else'minecraft:stone_bricks'
        if face:state=f'minecraft:stone_brick_stairs[facing={face},half=bottom,shape=straight,waterlogged=false]'
        world.set((x,y,z),state,'地块外原路')
        if foot2%2:world.set((x,y+1,z),'minecraft:stone_brick_slab[type=bottom,waterlogged=false]','地块外原半阶')
        for yy in range((foot2+1)//2,(foot2+1)//2+3):world.set((x,yy,z),AIR,'地块外通行净空')
        preserved_roads+=1
    def point(p):return[e-1-p[2],base+p[1],b+p[0]]
    for zz in range(m.d):
        for xx in range(m.w):
            wx,_,wz=point((xx,0,zz));world.terrain[wz*world.width+wx]=base+int(m.terrain[zz*m.w+xx])
    for p,state in m.blocks.items():world.set(tuple(point(p)),rotate_state(state,1),'千机阁总舵')
    old=next((p for p in city['life']['places']if p['id']=='jianghu_board'),None)
    if old:
        p=tuple(old['block_pos'])
        if world.get(p).startswith('myvillage:jianghu_board['):world.set(p,AIR,'撤下原委托榜')
        city['life']['places'].remove(old)
    board=point(m.spec['board']);reader=point(m.spec['reader']);entry=point(m.spec['paths'][0]);anchor=point(m.spec['atmosphere_anchor'])
    city['life']['places'].append(dict(id='jianghu_board',kind='entry',pos=reader,block_pos=board,block='myvillage:jianghu_board',capacity=1,building_id='qianji'))
    for p,state in retained.items():
        if world.get(p)!=state:raise ValueError('总舵改动保留建筑 '+str((p,state,world.get(p))))
    # The existing estate and every functional life point remain active.
    world.verify_life()
    if world.get(tuple(board))!='myvillage:jianghu_board[facing=east]':raise ValueError('任务榜未朝向实际操作地坪')
    expected_terrain_before=site()['terrain'];cut=max(int(expected_terrain_before[z,x])-int(m.terrain[z*m.w+x])for z in range(m.d)for x in range(m.w))
    if cut>cfg['local_earthwork_limit']:raise ValueError('总舵局部整地超过三格')
    for tile in city['tiles']:
        key=tile['x']//16,tile['z']//16
        if key not in world.dirty:continue
        path=resource_file(out,tile['template'],template=True);entities=block_entities(path);cells={p:(s,'qianji','infill')for p,s in world.tile(key).items()}
        if key==(anchor[0]//16,anchor[2]//16):entities[anchor[0]%16,anchor[1],anchor[2]%16]=dict(block='myvillage:qianji_atmosphere_anchor',payload=atmosphere_payload())
        count,pal,_=write_tile(path,cells,world.height,entities);_,saved=read_gzipped_nbt(path);palette=[state_string(v)for v in saved['palette']]
        actual={tuple(v['pos']):palette[v['state']]for v in saved['blocks']}
        if actual!={p:v[0]for p,v in cells.items()}:raise ValueError('总舵正式切片逐坐标回读不一致')
        expected={p:v for p,v in entities.items()if p in cells and cells[p][0].split('[',1)[0]==v['block']}
        if block_entities(path)!=expected:raise ValueError('总舵施工丢失原始方块实体')
        tile.update(block_count=count,palette_count=pal,bytes=path.stat().st_size,sha256=sha(path.read_bytes()))
    if world.dirty-set(world.refs):raise ValueError('城内总舵超出当前中州切片')
    terrain=resource_file(out,city['terrain']['resource']);terrain.write_bytes(gzip.compress(struct.pack('<'+'h'*len(world.terrain),*world.terrain),mtime=0));city['terrain']['sha256']=sha(terrain.read_bytes())
    city['palette_states']=sorted(set(city['palette_states'])|{rotate_state(s,1)for s in m.blocks.values()});city['buildings']=[r for r in city.get('buildings',[])if r['id']not in retired]
    city['access_paths']=[r for r in city.get('access_paths',[])if r.get('building_id')not in retired]
    city['buildings'].append(dict(id='qianji',name=cfg['name'],role='civic',family='qianji',position=[a,base,b],size=[e-a,f-b],bounds=dict(min=[a,base,b],max=[e-1,base+m.h-1,f-1]),facing='east',entrance=entry,stories=5))
    city['entries'].append(dict(id='qianji',position=entry,facing='east'))
    lighting=json.loads((ROOT/'blueprints/qianji/lighting.json').read_text())
    from .infill import lighting_checks
    light_checks=[dict(name=row['name'],position=point(row['position']),minimum_light=lighting['minimum_light'])for row in lighting_checks()]
    descriptor=dict(variant='zhongzhou_headquarters',name=cfg['name'],scale='headquarters',group='qianji',facing='east',source_dimensions=city['size'],bounds=cfg['plot'],size=[m.w,m.h,m.d],base_y=base,rotation_quarters=1,entry=entry,access=entry,board=dict(position=board,reader=reader,facing='east'),rooms=m.rooms,targets=[dict(id=t['id'],position=point(t['position']))for t in m.targets],checks=checks,inputs=inputs,old_city_preserved=False,old_board_retired=old is not None,atmosphere=dict(anchor=anchor,appearance='myvillage:qianji/atmosphere.json',source_offset=m.spec['source_offset']),lighting_checks=light_checks,interior_lights=len(m.lights),landmarks=[],infill=dict(id=cfg['id'],plot=cfg['plot'],retired_buildings=sorted(retired),retained_structural_cells=len(retained),preserved_road_columns=preserved_roads,changed_tiles=len(world.dirty),maximum_terrain_cut=cut,construction=m.construction_checks,route_count=len(m.routes)))
    descriptor['infill']['verified_existing_road_cut_columns']=existing_road_readback
    descriptor['infill']['existing_road_columns_with_blocked_headroom']=overbuilt_roads
    descriptor['infill']['components']=component_summary()
    city['qianji']=descriptor;life_inputs(city,*city['size'],192);write(file,city)
    # Only templates referenced by the newly authored current city are packed.
    used={resource_file(out,t['template'],template=True).resolve()for t in city['tiles']}
    templates=out/'data/myvillage/structure/province_capitals/v4/capital_zhongzhou'
    removed=[]
    for p in templates.glob('tile_*.nbt'):
        if p.resolve()not in used:removed.append(p.name);p.unlink()
    descriptor['infill']['removed_unreferenced_current_templates']=removed;write(file,city)
    print('中州城内总舵：'+json.dumps(descriptor['infill'],ensure_ascii=False),flush=True)
    return descriptor
