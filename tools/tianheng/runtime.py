"""Place the approved Tianheng wards in the eight current capital bundles."""
from __future__ import annotations

import argparse
from collections import defaultdict
import gzip
import hashlib
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tools.buildgen.nbtread import read_gzipped_nbt, state_string
from tools.province_capitals.blueprint_plan.nbt_entities import block_entities, write_tile
from tools.province_capitals.game.compile import life_inputs, resource_file, write
from tools.tianheng.components import AUTHOR, compile_architecture

AIR = 'minecraft:air'
STREET = 'yuanxian:tianheng_pave'
WALL = 'yuanxian:tianheng_ashlar'
RIM = 'yuanxian:tianheng_ink_jade'
COUNTER = 'yuanxian:linlang_counter'
SIDE = ('north', 'east', 'south', 'west')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def transform(site, size, pos):
    x,y,z=map(int,pos)
    ox,oy,oz=site['origin']
    if site['rotation']==3:
        x,z=z,size[0]-1-x
    elif site['rotation']!=0:
        raise ValueError('天衡殿采用预览批准的朝向')
    return ox+x,oy+y,oz+z


def raster_line(a,b):
    steps=max(abs(a[0]-b[0]),abs(a[2]-b[2]),1)
    for n in range(steps+1):
        yield tuple(round(x+(y-x)*n/steps) for x,y in zip(a,b))


class Tiles:
    def __init__(self, output, city):
        self.output=output
        self.city=city
        self.height=city['size'][1]
        self.index={(v['x']//16,v['z']//16):v for v in city['tiles']}
        self.opened={}

    @staticmethod
    def location(pos):
        x,y,z=pos
        return (x//16,z//16),(x%16,y,z%16)

    def tile(self,key):
        if key not in self.opened:
            if key not in self.index:
                tx,tz=key
                self.index[key]={'x':tx*16,'z':tz*16,
                    'template':f"yuanxian:province_capitals/v4/{self.city['city_id']}/tile_{tx}_{tz}"}
            ref=self.index[key]
            file=resource_file(self.output,ref['template'],template=True)
            cells,entities={},{}
            if file.is_file():
                _,tree=read_gzipped_nbt(file)
                palette=[state_string(v) for v in tree['palette']]
                cells={tuple(v['pos']):(palette[v['state']],'source','source') for v in tree['blocks']}
                entities=block_entities(file)
            self.opened[key]=(file,cells,entities)
        return self.opened[key]

    def put(self,pos,state):
        x,y,z=pos
        w,h,d=self.city['size']
        if not (0<=x<w and 0<=y<h and 0<=z<d):
            raise ValueError((self.city['city_id'],'天衡殿方块越界',pos))
        key,local=self.location(pos)
        _,cells,entities=self.tile(key)
        if local in entities and cells[local][0]!=state:
            raise ValueError((self.city['city_id'],'天衡殿不能覆盖已有方块实体',pos))
        cells[local]=(state,'tianheng','district')

    def get(self,pos):
        key,local=self.location(pos)
        if key not in self.index and key not in self.opened:
            return AIR
        return self.tile(key)[1].get(local,(AIR,))[0]

    def clear_street(self,pos):
        x,y,z=pos
        key,(lx,_,lz)=self.location(pos)
        _,cells,entities=self.tile(key)
        selected=[p for p in cells if p[0]==lx and p[2]==lz and p[1]>y]
        if set(selected)&entities.keys():
            raise ValueError((self.city['city_id'],'商街穿过方块实体',pos))
        for p in selected:
            if p[1]>y+14 and cells[p][0]!=AIR:
                raise ValueError((self.city['city_id'],'商街穿过高构件',pos,p,cells[p][0]))
            cells.pop(p)

    def finish(self):
        palette=set(self.city.get('palette_states',[]))
        for key,(path,cells,entities) in self.opened.items():
            if not cells and not path.exists():
                self.index.pop(key)
                continue
            path.parent.mkdir(parents=True,exist_ok=True)
            count,n,_=write_tile(path,cells,self.height,entities)
            self.index[key].update(sha256=digest(path.read_bytes()),bytes=path.stat().st_size,
                                   block_count=count,palette_count=n)
            palette.update(v[0] for v in cells.values())
        self.city['palette_states']=sorted(palette)
        self.city['tiles']=sorted(self.index.values(),key=lambda t:(t['z'],t['x']))


def stop_at(terrain,tiles,pos,profiles):
    x,y,z=pos

    def state(p):
        px,py,pz=p
        value=tiles.get(p)
        if value==AIR and py<=terrain[pz,px]:
            return 'terrain'
        return value

    def obstruct(value):
        if value in (AIR,'minecraft:water[level=0]'):
            return False
        if value.startswith('yuanxian:tianheng_carved_'):
            bank=int(value.split('_carved_')[1].split('[')[0])
            n=int(value.split('shape=')[1].split(']')[0])
            return any(collides for _,collides in profiles[bank*32+n])
        return True

    return obstruct(state((x,y-1,z))) and not any(obstruct(state((x,y+h,z))) for h in (0,1))


def room_places(city,site,plan,terrain,tiles,profiles):
    def world(p):
        return list(transform(site,plan['size'],p))

    points=plan['fixtures']
    result={}
    for role in ('store','auction'):
        pos,reader=map(world,points[role])
        dx,dz=reader[0]-pos[0],reader[2]-pos[2]
        face=SIDE[[(0,-1),(1,0),(0,1),(-1,0)].index((dx,dz))]
        tiles.put(pos,COUNTER+'[facing='+face+']')
        if not stop_at(terrain,tiles,reader,profiles):
            raise ValueError((city['city_id'],role,'柜台前缺少站位',reader))
        result[role]={'position':pos,'reader':reader,'facing':face}
    entry=world(points['entry'])
    seats=[]
    for index,local in enumerate(points['seat']):
        candidates=[local]+[[local[0]+dx,local[1],local[2]+dz]
                            for dx,dz in ((0,1),(0,-1),(1,0),(-1,0),(0,2))]
        selected=next((world(p) for p in candidates if stop_at(terrain,tiles,world(p),profiles)),None)
        if selected is None:
            raise ValueError((city['city_id'],'拍卖座位缺少站位',index))
        seats.append(selected)
    result['entry']=entry
    result['seats']=seats
    return result


def place(output,key,site):
    roles,mats,_,profiles,indices,choices=compile_architecture()
    plan,voxels,shapes=choices[site['scheme']]
    path=output/f'data/yuanxian/capitals/v4/{site["city"]}/city.json'
    city=json.loads(path.read_bytes())
    if city.get('tianheng'):
        # A refresh of the current frozen bundle does not stamp a second ward.
        if city['tianheng']['input_sha256']!=input_identity():
            raise ValueError(f'{key} 天衡殿作者输入已改变，请从城市源重新生成')
        return city['tianheng']
    if city['size']!=site['old_size']:
        raise ValueError((key,'当前城市尺寸和已预览地块不一致',city['size']))
    w,h,d=site['size']
    W,_,D=city['size']
    origin=site['origin'][1]
    a,b,A,B=site['bounds']
    l,t,r,s=site['district']
    tf=resource_file(output,city['terrain']['resource'])
    old=np.frombuffer(gzip.decompress(tf.read_bytes()),'<i2').reshape(D,W)
    terrain=np.pad(old,((0,d-D),(0,w-W)),mode='edge').copy()
    terrain[t:s,l:r]=origin+1
    terrain[b:B,a:A]=origin-1
    city['size']=[w,h,d]
    tiles=Tiles(output,city)
    # The original tiles are empty outside the shared gate and, in Zhongzhou,
    # the district replaces the hill slope south of the old street.
    if key=='zhongzhou':
        for tx in range(l//16,(r+15)//16):
            for tz in range(t//16,(s+15)//16):
                if (tx,tz) not in tiles.index:
                    continue
                _,cells,entities=tiles.tile((tx,tz))
                for p in list(cells):
                    x,y,z=tx*16+p[0],p[1],tz*16+p[2]
                    if l<=x<r and t<=z<s:
                        if p in entities:
                            raise ValueError((key,'南关新区含既有方块实体',x,y,z))
                        cells.pop(p)
    facing=SIDE[site['rotation']]
    states=['']+[('minecraft:water[level=0]' if v=='water' else 'yuanxian:'+mats[v]['id']) for v in roles[1:]]
    for y,z,x in np.argwhere(voxels):
        p=transform(site,plan['size'],(x,y,z))
        tiles.put(p,states[int(voxels[y,z,x])])
    for p,shape in shapes.items():
        ident=indices[shape]
        tiles.put(transform(site,plan['size'],p),f'yuanxian:tianheng_carved_{ident//32}[facing={facing},shape={ident%32}]')

    # Connect the old street and the approach at the exact reviewed gate.
    entry=transform(site,plan['size'],plan['fixtures']['entry'])
    road=[tuple(p) for p in site['road']]+[(entry[0],entry[1]-1,entry[2])]
    surface={}
    for first,last in zip(road,road[1:]):
        for x,y,z in raster_line(first,last):
            for offset in range(-2,3):
                dx,dz=(0,offset) if abs(last[0]-first[0])>=abs(last[2]-first[2]) else (offset,0)
                surface[x+dx,z+dz]=y
    for (x,z),y in sorted(surface.items()):
        terrain[z,x]=y
        tiles.clear_street((x,y,z))
        tiles.put((x,y,z),STREET)
    # Build the new side walls as continuous extensions of the existing edge.
    if site['gate']:
        side,line,_=site['gate']
        perimeter=({(x,z) for x in range(l,r) for z in (t,s-1)}
                   |{(x,z) for z in range(t,s) for x in (l,r-1)})
        if side=='east':
            perimeter={p for p in perimeter if p[0]>line}
        else:
            perimeter={p for p in perimeter if p[1]>line}
        for x,z in sorted(perimeter):
            for y in range(origin+2,origin+5):
                tiles.put((x,y,z),WALL if y<origin+4 else RIM)
    places=room_places(city,site,plan,terrain,tiles,profiles)
    if not stop_at(terrain,tiles,entry,profiles):
        raise ValueError((key,'院门路面未接通',entry))
    dims=[a,origin,b],[A-1,origin+plan['size'][1]-1,B-1]
    city['buildings'].append(dict(id='tianheng',name='天衡殿·'+plan['name'],role='tianheng',
        bounds={'min':dims[0],'max':dims[1]},entrance=list(entry),facing=facing,position=list(entry)))
    city['entries'].append(dict(id='tianheng',position=list(entry),facing=facing))
    life=city['life']['places']
    life.append(dict(id='tianheng_entry',kind='entry',pos=list(entry),capacity=1,
                     block='',block_pos=[0,0,0],building_id='tianheng'))
    for role in ('store','auction'):
        p=places[role]
        life.append(dict(id='tianheng_'+role,kind='entry',pos=p['reader'],capacity=1,
                         block=COUNTER,block_pos=p['position'],building_id='tianheng'))
    for i,p in enumerate(places['seats']):
        life.append(dict(id=f'tianheng_seat_{i}',kind='social',pos=p,capacity=1,
                         block='',block_pos=[0,0,0],building_id='tianheng'))
    city['roads'].append(dict(id='tianheng_street',width=5,points=road))
    city['tianheng']={**places,'scheme':site['scheme'],'bounds':site['bounds'],'district':site['district'],
                     'road':road,'gate':site['gate'],'input_sha256':input_identity()}
    raw=gzip.compress(terrain.astype('<i2',copy=False).tobytes(),mtime=0)
    tf.write_bytes(raw)
    city['terrain'].update(width=w,depth=d,sha256=digest(raw))
    tiles.finish()
    life_inputs(city,w,h,d,192)
    write(path,city)
    return city['tianheng']


def input_identity():
    return digest((AUTHOR/'sites.json').read_bytes()+(AUTHOR/'architecture.json').read_bytes()
                  +Path(__file__).read_bytes())


def integrate(out):
    out=Path(out)
    sites=json.loads((AUTHOR/'sites.json').read_bytes())
    if len(sites)!=8:
        raise ValueError('天衡殿须覆盖八城')
    for key,site in sites.items():
        result=place(out,key,site)
        print(json.dumps({'city':key,'tianheng':result['scheme'],'entry':result['entry']},ensure_ascii=False),flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out',type=Path,required=True)
    integrate(p.parse_args().out)
