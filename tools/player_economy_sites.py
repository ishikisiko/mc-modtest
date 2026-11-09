"""正式城市切片中的江湖榜；保留源方块及方块实体。"""
from __future__ import annotations
import json
from pathlib import Path
from buildgen.nbtread import read_gzipped_nbt,state_string
from province_capitals.game.compile import resource_file,write,sha,life_inputs
from province_capitals.blueprint_plan.nbt_entities import block_entities,write_tile

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'blueprints/economy/town_boards.json'

def place_capital_boards(out):
    out=Path(out);spec=json.loads(SOURCE.read_text());reports=[]
    if len(spec)!=8:raise ValueError('江湖榜必须覆盖八州主城')
    for city_id,point in sorted(spec.items()):
        file=out/f'data/myvillage/capitals/v4/{city_id}/city.json';city=json.loads(file.read_text())
        if city['city_id']!=city_id or set(point)!={'position','reader','facing'}:raise ValueError('江湖榜归属无效')
        x,y,z=point['position'];reader=tuple(point['reader']);tiles={(t['x']//16,t['z']//16):t for t in city['tiles']};cache={}
        def tile_at(xx,zz):
            key=xx//16,zz//16
            if key not in tiles:raise ValueError(f'{city_id} 江湖榜须位于既有公共设施切片')
            if key not in cache:
                path=resource_file(out,tiles[key]['template'],template=True);_,root=read_gzipped_nbt(path)
                palette=[state_string(s) for s in root['palette']]
                cells={tuple(b['pos']):(palette[b['state']],'source','source') for b in root['blocks']}
                cache[key]=(path,cells,block_entities(path))
            return cache[key]
        def get(p):
            xx,yy,zz=p;return tile_at(xx,zz)[1].get((xx%16,yy,zz%16),('minecraft:air',))[0]
        def support(p):
            floor=get((p[0],p[1]-1,p[2]));return floor not in ('minecraft:air','minecraft:water','minecraft:lava') and not any(k in floor for k in ('stairs','slab','fence','wall','door','trapdoor'))
        state=f"myvillage:jianghu_board[facing={point['facing']}]"
        if point['facing'] not in ('north','east','south','west') or not support((x,y,z)) or not support(reader):raise ValueError('江湖榜或读者缺少完整地面')
        if any(get((reader[0],reader[1]+dy,reader[2]))!='minecraft:air' for dy in (0,1,2)) or get((x,y,z)) not in ('minecraft:air',state):raise ValueError(f'{city_id} 江湖榜或操作位置被占用')
        if abs(reader[0]-x)+abs(reader[2]-z)!=1 or reader[1]!=y:raise ValueError('江湖榜操作位置须紧邻柜台')
        path,cells,entities=tile_at(x,z);before={p:v[0] for p,v in cells.items()};cells[x%16,y,z%16]=(state,'jianghu_board','facility')
        count,palette_count,_=write_tile(path,cells,city['size'][1],entities)
        _,saved=read_gzipped_nbt(path);palette=[state_string(s) for s in saved['palette']];after={tuple(b['pos']):palette[b['state']] for b in saved['blocks']}
        expected={**before,(x%16,y,z%16):state}
        if after!=expected:raise ValueError('江湖榜切片回读改变了原有方块')
        tile=tiles[x//16,z//16];tile.update(sha256=sha(path.read_bytes()),bytes=path.stat().st_size,block_count=count,palette_count=palette_count)
        city['palette_states']=sorted(set(city.get('palette_states',[]))|{state})
        place={'id':'jianghu_board','kind':'entry','pos':list(reader),'capacity':1,'block':'myvillage:jianghu_board','block_pos':[x,y,z],'building_id':'inn'}
        existing=next((p for p in city['life']['places'] if p['id']=='jianghu_board'),None)
        if existing is not None and existing!=place:raise ValueError('江湖榜位置发生变化，请从作者输入重新导出城市')
        if existing is None:city['life']['places'].append(place)
        city['player_economy']={'board':point,'source_hashes':{str(SOURCE.relative_to(ROOT)):sha(SOURCE.read_bytes()),str(Path(__file__).relative_to(ROOT)):sha(Path(__file__).read_bytes())}}
        life_inputs(city,*city['size'],192);write(file,city);reports.append({'city':city_id,**point,'checks':'pass'})
    return reports
