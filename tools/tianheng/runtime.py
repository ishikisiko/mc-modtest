"""Place Tianheng halls on blocks inside the current capital enclosures."""
from __future__ import annotations

import argparse
from copy import deepcopy
import gzip
import hashlib
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(ROOT), str(ROOT / 'tools')]

from tools.buildgen.nbtread import read_gzipped_nbt, state_string
from tools.province_capitals.blueprint_plan.nbt_entities import block_entities, write_tile
from tools.province_capitals.game.compile import life_inputs, resource_file, write
from tools.tianheng.components import AUTHOR, compile_architecture

AIR = 'minecraft:air'
COUNTER = 'yuanxian:linlang_counter'
SIDE = ('north', 'east', 'south', 'west')
DXZ = ((0, -1), (1, 0), (0, 1), (-1, 0))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def transform(site, size, pos):
    x, y, z = map(int, pos)
    w, _, d = size
    x, z = ((x,z), (d-1-z,x), (w-1-x,d-1-z), (z,w-1-x))[site['rotation']]
    ox, oy, oz = site['origin']
    return ox+x, oy+y, oz+z


def footprint(bounds):
    if isinstance(bounds, dict):
        x, _, z = bounds['min']
        X, _, Z = bounds['max']
        return x, z, X+1, Z+1
    return tuple(bounds)


def rectangle(box):
    x, z, X, Z = box
    return {(a,b) for a in range(x,X) for b in range(z,Z)}


def overlaps(a, b):
    return a[0]<b[2] and b[0]<a[2] and a[1]<b[3] and b[1]<a[3]


def province_lot(scheme, source, voxels, shapes, roles):
    # Keep the rooms, seating levels and interior routes at their authored sizes.
    # On the paired quays, the adjustments apply to the west warehouse and canal.
    if scheme == '1B':
        xx = list(range(6,64))
        east = west = range(4,60)
    elif scheme == '2B':
        xx = [*range(7,46), *range(61,106)]
        east = range(6,84)
        west = [*range(6,64), *range(83,103)]
    elif scheme == '3A':
        xx = list(range(12,146))
        east = west = range(8,157)
    else:
        raise ValueError(f'未知天衡殿院型 {scheme}')
    xe = {x:i for i,x in enumerate(xx)}
    ze = {z:i for i,z in enumerate(east)}
    zw = {z:i for i,z in enumerate(west)}
    assembled = np.stack([voxels[:,west if x<46 else east,x] for x in xx],axis=-1)
    plan = deepcopy(source)
    plan['size'] = [len(xx), source['size'][1], len(east)]

    def at(p):
        x, y, z = p
        return [xe[x], y, (zw if x<46 else ze)[z]]

    points = source['fixtures']
    plan['fixtures'] = {k: at(v) if k=='entry' else [at(p) for p in v] for k,v in points.items()}
    mapped = {}
    for (x,y,z), shape in shapes.items():
        zs = zw if x<46 else ze
        if x in xe and z in zs and (scheme!='2B' or x!=61 or not 17<=z<=45):
            mapped[(xe[x],y,zs[z])] = shape
    if scheme == '2B':
        # Remove the wider canal's island before laying the compact receiving
        # bridge. The east auction building and its two galleries are intact.
        assembled[2:,11:40,39:46] = 0
        assembled[1,11:40,39:46] = roles.index('water')
        assembled[2,74:77,35:51] = roles.index('deck')
    return plan, assembled, mapped


class Tiles:
    def __init__(self, output, city):
        self.output = output
        self.city = city
        self.height = city['size'][1]
        self.index = {(v['x']//16,v['z']//16):v for v in city['tiles']}
        self.opened = {}
        self.changed = set()

    @staticmethod
    def location(pos):
        x,y,z = pos
        return (x//16,z//16), (x%16,y,z%16)

    def tile(self, key):
        if key not in self.opened:
            tx,tz = key
            ref = self.index.get(key)
            ident = ref['template'] if ref else f"yuanxian:province_capitals/v4/{self.city['city_id']}/tile_{tx}_{tz}"
            file = resource_file(self.output, ident, template=True)
            cells,entities = {},{}
            if ref is not None:
                _,tree = read_gzipped_nbt(file)
                palette = [state_string(v) for v in tree['palette']]
                cells = {tuple(v['pos']):(palette[v['state']],'source','source') for v in tree['blocks']}
                entities = block_entities(file)
            else:
                self.index[key] = {'x':tx*16,'z':tz*16,'template':ident}
            self.opened[key] = (file,cells,entities)
        return self.opened[key]

    def put(self, pos, state, entity=None):
        w,h,d = self.city['size']
        if not all(0 <= v < k for v,k in zip(pos,(w,h,d),strict=True)):
            raise ValueError((self.city['city_id'], '写入超出原城域', pos))
        key,local = self.location(pos)
        _,cells,entities = self.tile(key)
        if local in entities and cells[local][0]!=state:
            raise ValueError((self.city['city_id'], '天衡殿覆盖已有方块实体', pos))
        self.changed.add(key)
        cells[local] = (state,'tianheng','site')
        if entity is not None:
            entities[local] = entity

    def get(self, pos):
        key,local = self.location(pos)
        if key not in self.index:
            return None
        return self.tile(key)[1].get(local,(None,))[0]

    def subset(self, columns):
        keys = {(x//16,z//16) for x,z in columns} & self.index.keys()
        for tx,tz in sorted(keys):
            _,cells,entities = self.tile((tx,tz))
            for local,cell in tuple(cells.items()):
                x,y,z = local
                u,v = tx*16+x,tz*16+z
                if (u,v) in columns:
                    yield (u,y,v), cell[0], entities.get(local)

    def erase(self, columns, entity_columns, *, minimum=None):
        for pos,state,entity in self.subset(columns):
            x,y,z = pos
            if minimum is not None and y < minimum[x,z]:
                continue
            if entity is not None and (x,z) not in entity_columns:
                raise ValueError((self.city['city_id'], '街坊重排遇到既有方块实体', pos, entity['block']))
            key,local = self.location(pos)
            _,cells,entities = self.tile(key)
            del cells[local]
            entities.pop(local,None)
            self.changed.add(key)

    def finish(self):
        palette = set(self.city.get('palette_states',[]))
        for key in self.opened.keys() - self.changed:
            if 'sha256' not in self.index[key]:
                del self.index[key]
        for key in sorted(self.changed):
            path,cells,entities = self.opened[key]
            if not cells:
                path.unlink(missing_ok=True)
                del self.index[key]
                continue
            path.parent.mkdir(parents=True,exist_ok=True)
            count,n,_ = write_tile(path,cells,self.height,entities)
            self.index[key].update(sha256=digest(path.read_bytes()),bytes=path.stat().st_size,
                                   block_count=count,palette_count=n)
            palette.update(v[0] for v in cells.values())
        self.city['palette_states'] = sorted(palette)
        self.city['tiles'] = sorted(self.index.values(),key=lambda t:(t['z'],t['x']))


def stop_at(terrain, tiles, pos, profiles):
    x,y,z = pos

    def solid(p):
        px,py,pz = p
        value = tiles.get(p)
        if value is None:
            return py <= terrain[pz,px]
        if value in (AIR,'minecraft:water[level=0]'):
            return False
        if value.startswith('yuanxian:tianheng_carved_'):
            bank = int(value.split('_carved_')[1].split('[')[0])
            shape = int(value.split('shape=')[1].split(']')[0])
            return any(collides for _,collides in profiles[bank*32+shape])
        return True

    return solid((x,y-1,z)) and not any(solid((x,y+h,z)) for h in (0,1))


def room_places(city, site, plan, terrain, tiles, profiles):
    def world(p):
        return list(transform(site,plan['size'],p))

    points = plan['fixtures']
    result = {}
    for role in ('store','auction'):
        pos,reader = map(world,points[role])
        dx,dz = reader[0]-pos[0],reader[2]-pos[2]
        face = SIDE[DXZ.index((dx,dz))]
        tiles.put(pos,COUNTER+'[facing='+face+']')
        if not stop_at(terrain,tiles,reader,profiles):
            raise ValueError((city['city_id'],role,'柜台前缺少站位',reader))
        result[role] = {'position':pos,'reader':reader,'facing':face}
    result['entry'] = world(points['entry'])
    result['seats'] = []
    for index,local in enumerate(points['seat']):
        candidates = [local]+[[local[0]+dx,local[1],local[2]+dz]
                              for dx,dz in ((0,1),(0,-1),(1,0),(-1,0),(0,2))]
        selected = next((world(p) for p in candidates if stop_at(terrain,tiles,world(p),profiles)),None)
        if selected is None:
            raise ValueError((city['city_id'],'拍卖座位缺少站位',index))
        result['seats'].append(selected)
    return result


def interior_mask(city, site):
    w,_,d = city['size']
    outline = city['town_wall']['points'] if 'town_wall' in city else site['boundary']
    image = Image.new('L',(w,d))
    ImageDraw.Draw(image).polygon([tuple(p) for p in outline],fill=1)
    return np.asarray(image.filter(ImageFilter.MinFilter(5)),dtype=bool)


def shift_building(city, row, places, delta):
    def moved(p):
        return [a+b for a,b in zip(p,delta,strict=True)]

    key = row['id']
    for k in ('min','max'):
        row['bounds'][k] = moved(row['bounds'][k])
    for k in ('position','entrance'):
        row[k] = moved(row[k])
    for place in places:
        if place.get('building_id') == key:
            place['pos'] = moved(place['pos'])
            if place['block']:
                place['block_pos'] = moved(place['block_pos'])
    for house in city.get('npc_housing',[]):
        if house['id'] == key:
            x,z,X,Z = house['bounds']
            house['bounds'] = [x+delta[0],z+delta[2],X+delta[0],Z+delta[2]]
            for k in ('entry','outside'):
                house[k] = moved(house[k])


def road_steps(start, end):
    x,y,z = start
    X,Y,Z = end
    step = max(abs(X-x),abs(Z-z),1)
    return [(round(x+(X-x)*i/step),round(y+(Y-y)*i/step),round(z+(Z-z)*i/step)) for i in range(step+1)]


def paving(tiles, terrain, path, block, protected, inside, width=3):
    surface = {}
    for start,end in zip(path,path[1:]):
        dx,dz = end[0]-start[0],end[2]-start[2]
        cross = (0,1) if abs(dx)>abs(dz) else (1,0)
        for x,y,z in road_steps(start,end):
            for i in range(-(width//2),width//2+1):
                surface[x+i*cross[0],z+i*cross[1]] = y
    if surface.keys() & protected or any(not inside[z,x] for x,z in surface):
        raise ValueError((tiles.city['city_id'],'接街经过受保护地块或城墙',path))
    tiles.erase(surface.keys(),set(),minimum=surface)
    for (x,z),y in sorted(surface.items()):
        terrain[z,x] = y
        tiles.put((x,y,z),block)
    return surface


def prepare_site(city, site, terrain, tiles, lot, inside):
    houses = {b['id']:b for b in city['buildings']}
    moves = site.get('relocations',{})
    places = city['life']['places']
    replaced = set(site['replace'])
    if not replaced<=houses.keys() or any(houses[k]['role']!='house' or houses[k]['family']=='housing_residences' for k in replaced):
        raise ValueError((city['city_id'],'街坊重排名单与当前城市不一致',replaced))
    moved = {}
    for key,dest in moves.items():
        row = houses[key]
        src = footprint(row['bounds'])
        dx,dz = dest[0]-src[0],dest[1]-src[1]
        cols = rectangle(src)
        destcols = {(x+dx,z+dz) for x,z in cols}
        moved[key] = (row, list(tiles.subset(cols)), cols, destcols, (dx,dest[2]-row['bounds']['min'][1],dz))
    protected = set()
    for key,row in houses.items():
        if key not in replaced and key not in moves:
            protected |= rectangle(footprint(row['bounds']))
    for key in ('player_estates','player_housing','landmarks'):
        for item in city.get(key,[]):
            if 'bounds' in item:
                protected |= rectangle(footprint(item['bounds']))
    if city.get('qianji'):
        protected |= rectangle(footprint(city['qianji']['bounds']))
    for row in city.get('waystones',[]):
        x,y,z = row['position']
        protected |= rectangle((x-4,z-4,x+5,z+5))
    discard = set()
    for key in replaced:
        discard |= rectangle(footprint(houses[key]['bounds']))
    for row,cells,cols,dest,delta in moved.values():
        if dest & (protected|lot|discard) or any(not inside[z,x] for x,z in dest):
            raise ValueError((city['city_id'],'迁入地块不足',row['id']))
        discard |= cols
    for x,z,X,Z in site.get('central_houses',{}).values():
        discard |= rectangle((x,z,X+1,Z+1))
    clear = discard | lot
    if clear & protected or any(not inside[z,x] for x,z in clear):
        raise ValueError((city['city_id'],'街坊重排超出许可地块'))
    tiles.erase(clear,discard)
    for row,cells,cols,dest,delta in moved.values():
        tiles.erase(dest,set())
        dx,dy,dz = delta
        for (x,z) in cols:
            terrain[z+dz,x+dx] = terrain[z,x]+dy
        for pos,state,entity in cells:
            tiles.put(tuple(a+b for a,b in zip(pos,delta,strict=True)),state,entity)
        shift_building(city,row,places,delta)
        protected |= dest
    city['buildings'] = [b for b in city['buildings'] if b['id'] not in replaced]
    places[:] = [p for p in places if p.get('building_id') not in replaced]
    return protected


def place(output,key,site):
    roles,mats,_,profiles,indices,choices = compile_architecture()
    old_plan,voxels,shapes = choices[site['scheme']]
    plan,voxels,shapes = province_lot(site['scheme'],old_plan,voxels,shapes,roles)
    path = output/f'data/yuanxian/capitals/v4/{site["city"]}/city.json'
    city = json.loads(path.read_bytes())
    if city.get('tianheng'):
        if city['tianheng']['input_sha256']!=input_identity():
            raise ValueError(f'{key} 天衡殿作者输入已改变，请从城市源重新生成')
        return city['tianheng']
    if city['size']!=site['size']:
        raise ValueError((key,'当前城市尺寸与预览不一致',city['size']))
    w,h,d = city['size']
    tfile = resource_file(output,city['terrain']['resource'])
    terrain = np.frombuffer(gzip.decompress(tfile.read_bytes()),'<i2').reshape(d,w).copy()
    tiles = Tiles(output,city)
    y = site['origin'][1]
    first = transform(site,plan['size'],(0,0,0))
    last = transform(site,plan['size'],(plan['size'][0]-1,0,plan['size'][2]-1))
    if site['bounds']!=[min(first[0],last[0]),min(first[2],last[2]),max(first[0],last[0])+1,max(first[2],last[2])+1]:
        raise ValueError((key,'建筑地块和组装尺寸不符',site['bounds'],plan['size']))
    lot = rectangle(site['bounds'])
    inside = interior_mask(city,site)
    protected = prepare_site(city,site,terrain,tiles,lot,inside)
    a,b,A,B = site['bounds']
    terrain[b:B,a:A] = y-1
    facing = SIDE[site['rotation']]
    states = ['']+[('minecraft:water[level=0]' if v=='water' else 'yuanxian:'+mats[v]['id']) for v in roles[1:]]
    for yy,zz,xx in np.argwhere(voxels):
        p = transform(site,plan['size'],(xx,yy,zz))
        tiles.put(p,states[int(voxels[yy,zz,xx])])
    for p,shape in shapes.items():
        ident = indices[shape]
        tiles.put(transform(site,plan['size'],p),f'yuanxian:tianheng_carved_{ident//32}[facing={facing},shape={ident%32}]')

    places = room_places(city,site,plan,terrain,tiles,profiles)
    ex,ey,ez = places['entry']
    frontage = [(ex,ey-1,ez)]+[tuple(p) for p in site['street']]
    sx,sy,sz = frontage[-1]
    base_state = tiles.get((sx,sy,sz))
    if (sx,sz) in lot or base_state is None or base_state.split('[')[0] != site.get('street_tie_state', site['pavement']):
        raise ValueError((key,'接街终点与原路面不符',frontage[-1],base_state))
    paving(tiles,terrain,frontage,site['pavement'],protected,inside)
    for route in site.get('public_paths',[]):
        paving(tiles,terrain,route,site['pavement'],protected,inside)
    if not stop_at(terrain,tiles,places['entry'],profiles):
        raise ValueError((key,'院门路面未接通',places['entry']))
    dims = [a,y,b],[A-1,y+plan['size'][1]-1,B-1]
    city['buildings'].append(dict(id='tianheng',name='天衡殿·'+plan['name'],role='tianheng',
        bounds={'min':dims[0],'max':dims[1]},entrance=places['entry'],facing=facing,position=places['entry']))
    city['entries'].append(dict(id='tianheng',position=places['entry'],facing=facing))
    life = city['life']['places']
    life.append(dict(id='tianheng_entry',kind='entry',pos=places['entry'],capacity=1,
                     block='',block_pos=[0,0,0],building_id='tianheng'))
    for role in ('store','auction'):
        p = places[role]
        life.append(dict(id='tianheng_'+role,kind='entry',pos=p['reader'],capacity=1,
                         block=COUNTER,block_pos=p['position'],building_id='tianheng'))
    for i,p in enumerate(places['seats']):
        life.append(dict(id=f'tianheng_seat_{i}',kind='social',pos=p,capacity=1,
                         block='',block_pos=[0,0,0],building_id='tianheng'))
    city['roads'].append(dict(id='tianheng_street',width=3,points=frontage))
    for i,route in enumerate(site.get('public_paths',[])):
        city['roads'].append(dict(id=f'tianheng_public_path_{i}',width=3,points=route))
    city['tianheng'] = {**places,'scheme':site['scheme'],'bounds':site['bounds'],
                       'street':frontage,'input_sha256':input_identity()}
    if city.get('coverage'):
        city['coverage'].update(houses=sum(b['role']=='house' for b in city['buildings']),
                                buildings=len(city['buildings']),life_places=len(life))
    raw = gzip.compress(terrain.astype('<i2',copy=False).tobytes(),mtime=0)
    tfile.write_bytes(raw)
    city['terrain'].update(sha256=digest(raw))
    tiles.finish()
    life_inputs(city,w,h,d,192)
    write(path,city)
    return city['tianheng']


def input_identity():
    return digest((AUTHOR/'sites.json').read_bytes()+(AUTHOR/'architecture.json').read_bytes()
                  +Path(__file__).read_bytes())


def integrate(out):
    out = Path(out)
    sites = json.loads((AUTHOR/'sites.json').read_bytes())
    if len(sites)!=8:
        raise ValueError('天衡殿须覆盖八城')
    for key,site in sites.items():
        result = place(out,key,site)
        print(json.dumps({'city':key,'tianheng':result['scheme'],'entry':result['entry']},ensure_ascii=False),flush=True)


if __name__=='__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out',type=Path,required=True)
    integrate(p.parse_args().out)
