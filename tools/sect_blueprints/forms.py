"""Validate and stage the approved terrain fields and per-column geometry runs."""
from __future__ import annotations

from collections import defaultdict
import gzip
import hashlib
import json
from pathlib import Path
import re
import struct

from province_capitals.regional.v2.common import write_json
from sect_blueprints.court_routes import court_probes, validate_court

ROW = struct.Struct('<5H')
COLUMN = struct.Struct('<hBB')
STATE = re.compile(r'[a-z0-9_]+:[a-z0-9_]+(?:\[[a-z0-9_=,]+\])?')
LAND = {'air','stone','water','sandstone','andesite','deepslate','grass_block','dirt','moss_block'}


def checked_bytes(path, expected, length):
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError(f'宗门形制摘要不符 {path}')
    with gzip.open(path, 'rb') as stream:
        raw = stream.read(length + 1)
    if len(raw) != length:
        raise ValueError(f'宗门形制长度不符 {path}')
    return data, raw


def export_form(source, out, policy):
    spec = json.loads(source.read_text())
    width,height,depth = spec['size']
    top = spec['max_y']
    name = spec['id'].split(':',1)[1]
    if spec['schema'] != 1 or spec['status'] != 'approved' or source.parent.name != name:
        raise ValueError(f'宗门形制身份无效 {source}')
    if width % 16 or depth % 16 or not (16 <= min(width,depth) <= max(width,depth) <= 512 and 0 < top < height <= 384):
        raise ValueError(f'宗门形制大小无效 {name}')
    field, terrain = spec['terrain'], spec['form']
    if field['bounds'] != [0,0,width,depth] or not (1 <= terrain['runs'] <= 2_000_000):
        raise ValueError(f'宗门形制范围无效 {name}')
    packed_height, heights = checked_bytes(source.parent/'terrain.bin.gz',field['sha256'],width*depth*COLUMN.size)
    packed_forms, runs = checked_bytes(source.parent/'form.bin.gz',terrain['sha256'],terrain['runs']*ROW.size)
    materials = terrain['materials']
    if not 1 <= len(materials) <= 16384:
        raise ValueError(f'宗门形制材质上限无效 {name}')
    for item in materials:
        state = item['state']
        if not STATE.fullmatch(state) or item['landform'] and state not in {'minecraft:'+key for key in LAND}:
            raise ValueError(f'宗门形制材质无效 {name} {state}')

    port = spec['road_port']
    px,py,pz = port['position']
    facing = port['facing']
    faces = {'north':pz == 0,'south':pz == depth-1,'west':px == 0,'east':px == width-1}
    if port['width'] != 3 or not faces.get(facing,False):
        raise ValueError(f'宗门出口不在蓝图边缘 {name}')
    dx,dz = (1,0) if facing in ('north','south') else (0,1)
    ports = [(px+i*dx,py,pz+i*dz) for i in (-1,0,1)]
    probes = [spec['entry_anchor'],spec['steward'],*spec['courtyard_cells'],spec['linlang']['reader'],
              *(s['reader'] for s in spec['shelves']),*ports]
    facilities = {tuple(spec['linlang']['position']):'yuanxian:linlang_counter[facing='+spec['linlang']['facing']+']'}
    for shelf in spec['shelves']:
        x,y,z=shelf['position']
        for dy,half in enumerate(('lower','upper')):
            facilities[x,y+dy,z]=f"yuanxian:scripture_shelf[facing={shelf['facing']},half={half}]"
    if not spec['shelves'] or spec['steward'] not in spec['courtyard_cells']:
        raise ValueError(f'宗门设施或站位缺失 {name}')
    facilities.update({tuple(row['position']):row['state'] for row in spec.get('furniture',[])})
    requested=defaultdict(set)
    for x,y,z in probes:
        if not (0<=x<width and 1<=y<height-2 and 0<=z<depth):
            raise ValueError(f'宗门站位越界 {name} {(x,y,z)}')
        requested[x,z].update((y-1,y,y+1))
    for x,y,z in facilities:
        requested[x,z].add(y)
    court_foot, court_extra = court_probes(spec)
    for x,y,z in court_extra:
        if not (0<=x<width and 0<=y<=top and 0<=z<depth):
            raise ValueError(f'比试院站位或井道越界 {name} {(x,y,z)}')
        requested[x,z].add(y)

    actual={}
    for (x,z), ys in requested.items():
        h,weight,surface=COLUMN.unpack_from(heights,(z*width+x)*COLUMN.size)
        for y in ys:
            actual[x,y,z]='minecraft:stone' if y<=h else 'minecraft:air'
    for x,y,z in ports:
        ground,weight,_ = COLUMN.unpack_from(heights,(z*width+x)*COLUMN.size)
        if (ground+1,weight)!=(y,255):
            raise ValueError(f'宗门出口标高无效 {name}')
    for ground,_,surface in COLUMN.iter_unpack(heights):
        if ground < 0 or ground > top or (surface & 127) >= len(spec['surfaces']):
            raise ValueError(f'宗门高度场记录无效 {name}')

    last=(-1,-1)
    end=0
    for x,z,bottom,count,index in ROW.iter_unpack(runs):
        if (x>=width or z>=depth or not count or bottom+count>top+1 or index>=len(materials)
                or (z,x)<last or ((z,x)==last and bottom<end)):
            raise ValueError(f'宗门形制分段超出范围或重叠 {name} {(x,z,bottom,count,index)}')
        last=(z,x)
        end=bottom+count
        for y in requested.get((x,z),()):
            if bottom<=y<end:
                actual[x,y,z]=materials[index]['state']
    for x,y,z in probes:
        if actual[x,y-1,z] in ('minecraft:air','minecraft:water') or any(actual[x,y+i,z]!='minecraft:air' for i in (0,1)):
            raise ValueError(f'宗门出口、设施操作位或站位缺承托或净空 {name} {(x,y,z)}')
    for pos,state in facilities.items():
        if actual.get(pos)!=state:
            raise ValueError(f'宗门设施方块与清单不符 {name} {pos}')
    validate_court(spec,court_foot,actual)

    result=out/'data/yuanxian/sect_blueprints'/name
    result.mkdir(parents=True,exist_ok=True)
    for kind,data,meta in ('terrain',packed_height,field),('form',packed_forms,terrain):
        basename=kind+'.bin.gz'
        (result/basename).write_bytes(data)
        meta['resource']=f'yuanxian:sect_blueprints/{name}/{basename}'
    manifest={**spec,'worldgen':policy,'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest()}
    write_json(result/'sect.json',manifest)
    print(json.dumps({'id':spec['id'],'runs':terrain['runs'],'bytes':len(packed_height)+len(packed_forms)},ensure_ascii=False),flush=True)
    return manifest
