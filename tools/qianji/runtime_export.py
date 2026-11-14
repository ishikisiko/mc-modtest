"""生成前七州东侧分阁，并把中州总舵写入获准的城内住宅坊。"""
from __future__ import annotations
from collections import defaultdict
from functools import lru_cache
import gzip,io,json,math,struct
from pathlib import Path
from qianji.model import build,ROOT,AIR
from province_capitals.game.compile import sha,write,resource_file
from province_capitals.blueprint_plan.nbt_entities import block_entities,write_tile
from province_capitals.central.game_models import rotate_state
from road_port_sources import CitySource
import json_to_nbt as nbt

@lru_cache(maxsize=8)
def model(ident):return build(ident)

def sign_payload(text):
    messages=[nbt.String(json.dumps({'text':v},ensure_ascii=False,separators=(',',':'))) for v in [text,'','','']]
    face=nbt.Compound({'messages':nbt.ListTag(nbt.TAG_STRING,messages),'color':nbt.String('black'),'has_glowing_text':nbt.Byte(0)})
    tag=nbt.Compound({'id':nbt.String('minecraft:sign'),'front_text':face,'back_text':face,'is_waxed':nbt.Byte(1)})
    stream=io.BytesIO();nbt._write_payload(stream,tag);return stream.getvalue()

def atmosphere_payload():
    # 自然生成使用方块实体的默认零偏移；管理员调整后由实体自己保存 long 值。
    tag=nbt.Compound({'id':nbt.String('myvillage:qianji_atmosphere_anchor')})
    stream=io.BytesIO();nbt._write_payload(stream,tag);return stream.getvalue()

def entrance_road(entry,road_z,base,policy,paving):
    """署区入口接路同时定义正式写入和复用检查所需的最终方块。"""
    radius=policy['road_width']//2
    for z in range(entry[2],road_z+1):
        for x in range(entry[0]-radius,entry[0]+radius+1):
            yield(x,base,z),paving
            for y in range(base+1,base+1+policy['headroom']):yield(x,y,z),AIR

def integrate(out):
    out=Path(out);spec=json.loads((ROOT/'blueprints/qianji/placement.json').read_text());reports=[]
    inputs={str(p.relative_to(ROOT)):sha(p.read_bytes()) for folder,pattern in [('blueprints/qianji','*.json'),('tools/qianji','model.py'),('tools/qianji','runtime_export.py')] for p in sorted((ROOT/folder).glob(pattern))}
    for name in ['headquarters.py','bookcases.py','components.py','headquarters_model.py','generate_assets.py','infill.py','infill_export.py']:
        p=ROOT/'tools/qianji'/name;inputs[str(p.relative_to(ROOT))]=sha(p.read_bytes())
    from qianji.infill import site
    inputs.update(site()['geometry'].hashes)
    for name in ['tools/player_estates/zhongzhou_plan.py','tools/player_estates/build_terraces.py']:inputs[name]=sha((ROOT/name).read_bytes())
    for f in ['tools/buildgen/groups.py','tools/buildgen/modset.py','tools/buildgen/style.py','tools/buildgen/massing.py','tools/buildgen/compound.py','exmod/mod_block_catalog.json']:inputs[f]=sha((ROOT/f).read_bytes())
    for region,ident in spec['regions'].items():
        file=out/f'data/myvillage/capitals/v4/capital_{region}/city.json';city=json.loads(file.read_text())
        if region=='zhongzhou':
            from qianji.infill_export import integrate as integrate_infill
            reports.append(integrate_infill(out,city,inputs));continue
        if 'qianji' in city:
            for ref in [city['terrain'],*city['tiles']]:
                p=resource_file(out,ref.get('resource',ref.get('template')),template='template' in ref)
                if sha(p.read_bytes())!=ref['sha256']:raise ValueError('千机阁现有输出摘要不符')
            if city['qianji']['inputs']!=inputs or 'facing'not in city['qianji']:
                # A metadata refresh is allowed only after comparing every
                # expected annex voxel with the current owning output.
                m,checks=model(ident);source=CitySource(out,region);q=city['qianji'];x0,z0,_,_=q['bounds'];base=q['base_y'];signs={tuple(s['position'])for s in m.signs}
                road=dict(entrance_road(q['entry'],q['road_z'],base,spec['annex'],m.material['paving']))
                for pos,state in m.blocks.items():
                    if pos in signs:continue
                    x,y,z=pos;p=(x0+m.w-1-x,base+y,z0+m.d-1-z)
                    if source.block(*p)!=road.get(p,rotate_state(state,2)):raise ValueError('分阁几何变化，须重建 owning 城市 '+str((region,p)))
                for sign in m.signs:
                    x,y,z=sign['position'];p=(x0+m.w-1-x,base+y,z0+m.d-1-z)
                    if source.block(*p)!=rotate_state('minecraft:dark_oak_wall_sign[facing=north,waterlogged=false]',2):raise ValueError('分阁题匾位置失配')
                for p,state in road.items():
                    if source.block(*p)!=state:raise ValueError('分阁入口接路几何变化 '+str((region,p)))
                q['inputs']=inputs;q['facing']='south';q['checks']=checks;q['geometry_cache_check']=dict(compared_voxels=len(m.blocks),compared_road_voxels=len(road),same_as_current_output=True);write(file,city)
            reports.append(city['qianji']);continue
        m,checks=model(ident);source=CitySource(out,region);ow,oh,od=city['size'];policy=spec['annex']
        binding=next(p for p in json.loads((ROOT/'config/roads/prototype.json').read_text())['ports'] if p['region']==region)
        layout=json.loads((ROOT/binding['layout']).read_text());passage=next(p for p in layout['public_passages'] if p['id']==binding['passage'])
        if region in spec.get('city_access',{}):
            access=spec['city_access'][region]['position']
            if access not in [p for road in city.get('roads',[]) for p in road.get('points',[])]:raise ValueError('千机阁接路点不是既有公共道路')
            ax,feet,az=access
            if source.floor(ax,az,8,policy['headroom'])!=feet*2:raise ValueError('千机阁公共接路点高度或净空失效')
        else:
            if binding['facing']!='south':raise ValueError('非南向省城须显式声明公共道路接点')
            ax=(passage['bounds'][0]+passage['bounds'][2])//2;az=od-1
            feet2=source.floor(ax,az,12,policy['headroom'])
            if feet2 is None:raise ValueError('省城原边缘没有真实接路地坪')
            feet=(feet2+1)//2;access=[ax,feet,az]
        base=feet-1;x0=ow+policy['gap'];z0=od-policy['edge_clearance']-m.d
        nw=math.ceil((x0+m.w+policy['edge_clearance'])/16)*16;nd=od+policy['south_extension'];nh=max(oh,math.ceil((base+m.h+8)/16)*16)
        if z0<policy['edge_clearance'] or nw>2048 or nd>2048 or nh>320:raise ValueError('千机阁署区越过当前城市容量')
        # 扩展高度场时保留旧城每一列；新增远端地形延续原边界，只整地新署区和接路大道。
        heights=[source.terrain[min(z,od-1)*ow+min(x,ow-1)] for z in range(nd) for x in range(nw)]
        for z in range(z0-4,od+policy['south_extension']):
            for x in range(ow,nw):heights[z*nw+x]=base
        for z in range(od,nd):
            for x in range(nw):heights[z*nw+x]=base
        cache={};entities={};old_tiles={(v['x']//16,v['z']//16):v for v in city['tiles']};new_cells=defaultdict(dict)
        def tile(key):
            if key not in cache:
                if key in old_tiles:
                    p=resource_file(out,old_tiles[key]['template'],template=True)
                    cache[key]={(x,y,z):(source.block(key[0]*16+x,y,key[1]*16+z),'source','source') for x,y,z in source.cache.get(key,{})}
                    # CitySource may not have loaded this tile yet.
                    source.block(key[0]*16,0,key[1]*16)
                    cache[key]={p:(state,'source','source') for p,state in source.cache[key].items()};entities[key]=block_entities(p)
                else:cache[key]={};entities[key]={}
            return cache[key]
        def put(x,y,z,state,entity=None):
            if not(0<=x<nw and 0<=y<nh and 0<=z<nd):raise ValueError('千机阁集成越界')
            key=x//16,z//16;p=x%16,y,z%16
            if x<ow and z<od:
                # 原城唯一允许的改动是撤下旧江湖榜，绝不覆盖房屋、城门或道路。
                before=source.block(x,y,z)
                if before!=state and not(before.startswith('myvillage:jianghu_board[') and state==AIR):
                    raise ValueError('千机阁施工试图覆盖原城：'+str((region,x,y,z,before,state)))
            cells=tile(key);cells[p]=(state,'qianji','organization');new_cells[key][p]=state
            if entity:entities[key][p]=entity
        # 先撤去属于该城市的旧榜，设施身份继续使用原城市 ID。
        old=next((p for p in city['life']['places'] if p['id']=='jianghu_board'),None)
        if old:
            px,py,pz=old['block_pos']
            if source.block(px,py,pz).startswith('myvillage:jianghu_board['):put(px,py,pz,AIR)
            city['life']['places'].remove(old)
        def point(p):return [x0+m.w-1-p[0],base+p[1],z0+m.d-1-p[2]]
        def direction(state):return rotate_state(state,2)
        for (x,y,z),state in m.blocks.items():
            px,py,pz=point((x,y,z));entity=dict(block='myvillage:qianji_atmosphere_anchor',payload=atmosphere_payload()) if state.split('[',1)[0]=='myvillage:qianji_atmosphere_anchor' else None
            put(px,py,pz,direction(state),entity)
        for sign in m.signs:
            px,py,pz=point(sign['position']);state=direction('minecraft:dark_oak_wall_sign[facing=north,waterlogged=false]')
            put(px,py,pz,state,dict(block='minecraft:dark_oak_wall_sign',payload=sign_payload(sign['text'])))
        # 南侧新增大道与原城出口平接；从署区入口延至大道，不改原门内地坪。
        entry=point(m.spec['paths'][0]);road_z=od+policy['south_extension']//2
        road_width=policy['road_width'];radius=road_width//2
        for zz in range(od,nd):
            for xx in range(max(0,ax-radius),min(nw,ax+radius+1)):
                put(xx,base,zz,m.material['paving'])
                for yy in range(base+1,base+1+policy['headroom']):put(xx,yy,zz,AIR)
        for xx in range(min(ax,entry[0])-radius,max(ax,entry[0])+radius+1):
            for zz in range(road_z-radius,road_z+radius+1):
                put(xx,base,zz,m.material['paving'])
                for yy in range(base+1,base+1+policy['headroom']):put(xx,yy,zz,AIR)
        for pos,state in entrance_road(entry,road_z,base,policy,m.material['paving']):put(*pos,state)
        # 原城出口可能含半砖，各列从实际旧截面以半格步进接到新大道。
        for xx in range(max(0,ax-radius),min(ow,ax+radius+1)):
            old_feet=source.floor(xx,od-1,12,policy['headroom'])
            if old_feet is None:old_feet=feet*2
            if abs(old_feet-feet*2)>policy['grade_rows']:raise ValueError('原城出口接坡超出声明长度')
            for row in range(policy['grade_rows']):
                value=old_feet+max(-row,min(row,feet*2-old_feet));zz=od+row;floor_y=(value-1)//2
                put(xx,floor_y,zz,m.material['paving_slab'] if value%2 else m.material['paving'])
                for yy in range(floor_y+1,base+policy['headroom']+2):put(xx,yy,zz,AIR)
                heights[zz*nw+xx]=floor_y
        for lz in range(m.d):
            for lx in range(m.w):
                px,_,pz=point((lx,0,lz));heights[pz*nw+px]=base+m.terrain[lz*m.w+lx]
        board=point(m.spec['board']);reader=point(m.spec['reader'])
        refs={**old_tiles};palette=set(city.get('palette_states',[]))
        for key,cells in cache.items():
            tx,tz=key;ref=refs.get(key)
            if ref is None:
                template=f'myvillage:province_capitals/v4/capital_{region}/tile_{tx}_{tz}'
                ref=dict(x=tx*16,z=tz*16,template=template);refs[key]=ref
            p=resource_file(out,ref['template'],template=True);count,colors,_=write_tile(p,cells,nh,entities[key]);palette.update(v[0] for v in cells.values())
            ref.update(sha256=sha(p.read_bytes()),bytes=p.stat().st_size,block_count=count,palette_count=colors)
        # 旧地形与旧方块保持原样，原始实体通过同一 NBT writer 保留。
        assert all(heights[z*nw+x]==source.terrain[z*ow+x] for z in range(od) for x in range(ow))
        terrain=resource_file(out,city['terrain']['resource']);terrain.write_bytes(gzip.compress(struct.pack('<'+'h'*len(heights),*heights),mtime=0))
        city['terrain'].update(width=nw,depth=nd,sha256=sha(terrain.read_bytes()));city['size']=[nw,nh,nd];city['tiles']=sorted(refs.values(),key=lambda p:(p['z'],p['x']));city['palette_states']=sorted(palette)
        descriptor=dict(variant=ident,name=m.spec['name'],scale=m.spec['scale'],group='qianji',facing='south',source_dimensions=[ow,oh,od],
                        bounds=[x0,z0,x0+m.w,z0+m.d],size=[m.w,m.h,m.d],base_y=base,rotation_quarters=2,entry=entry,access=access,road_z=road_z,
                        board=dict(position=board,reader=reader,facing='south'),rooms=m.rooms,
                        targets=[dict(id=t['id'],position=point(t['position'])) for t in m.targets],
                        checks=checks,inputs=inputs,old_city_preserved=True,old_board_retired=old is not None)
        descriptor['landmarks']=[]
        if 'atmosphere_anchor' in m.spec:
            descriptor['atmosphere']={'anchor':point(m.spec['atmosphere_anchor']),'appearance':'myvillage:qianji/atmosphere.json','source_offset':m.spec['source_offset']}
        descriptor['lighting_checks']=[dict(name=v['name'],position=point(v['position']),minimum_light=m.lighting['minimum_light']) for v in m.lighting['checks'].get(ident,[])]
        descriptor['interior_lights']=len(m.lights)
        if 'roof_instrument' in m.spec:
            cx,fy,cz,rr=m.spec['roof_instrument']
            for label,p in [('天机仪顶部',[cx,fy+rr*2,cz]),('天机仪核心',[cx+1,fy+rr,cz+1]),('天机仪外环',[cx-rr,fy+rr,cz])]:descriptor['landmarks'].append(dict(name=label,position=point(p)))
        city.setdefault('buildings',[]).append(dict(id='qianji',name=m.spec['name'],role='civic',family='qianji',position=[x0,base+1,z0],size=[m.w,m.d],bounds=dict(min=[x0,base,z0],max=[x0+m.w-1,base+m.h-1,z0+m.d-1]),facing='south',entrance=entry,stories=len(m.spec['rooms'][-1]['levels'])))
        if 'buildings' in city.get('coverage',{}):city['coverage']['buildings']+=1
        city['qianji']=descriptor;city['entries'].append(dict(id='qianji',position=entry,facing='south'))
        write(file,city);reports.append(descriptor)
        print(f"{region} 千机阁 {ident}：{m.w}×{m.d}，最高{m.h}格，功能目标{checks['reachable_targets']}处",flush=True)
    return reports
