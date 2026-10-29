"""Compile the approved complete D1 city into a new, frozen V4 generation family.

Old inputs and NBT are never replaced. A slice is 16 x city-height x 16, owned by
one chunk column, including explicit air and block-entity data. No game/network.
"""
from __future__ import annotations
import copy
import gzip
import base64
import hashlib
import json
import math
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import json_to_nbt as nbt
import voxel_dump_to_structures as export
from buildgen.nbtread import read_gzipped_nbt, state_string
from yunshan.build_district import realize, validate_blocks, rect_cells, street_cells
from yunshan.build_inn import attach_signs, canonical
from yunshan.plan_district import prepare


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def content_hash(value):
    return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()).hexdigest()


def compatible_layouts(root=ROOT):
    data=read(root/'blueprints/worldplan/compatible_v4.json')
    if data.get('schema')!=1 or not isinstance(data.get('layouts'),list) or len(data['layouts'])>16:
        raise ValueError('Invalid V4 compatibility manifest')
    seen=set()
    for entry in data['layouts']:
        if set(entry)!={'layout_hash','city_geometry_sha256','places_sha256'} or any(
                not isinstance(v,str) or len(v)!=64 or any(c not in '0123456789abcdef' for c in v) for v in entry.values()):
            raise ValueError('Invalid V4 compatibility fingerprint')
        if entry['layout_hash'] in seen:raise ValueError('Duplicate V4 compatibility identity')
        seen.add(entry['layout_hash'])
    return data['layouts']


def check_compatible_geometry(compiled,root=ROOT):
    actual=content_hash({k:v for k,v in compiled.items() if k!='layout_hash'})
    if any(entry['city_geometry_sha256']!=actual for entry in compatible_layouts(root)):
        raise ValueError('V4 compatibility alias does not match the actual city geometry')


def descriptor(root=ROOT):
    """Cheap shared frame/hash for worldgen AND life metadata; no duplicate geometry."""
    definition = read(root / 'blueprints/worldplan/rules_v4.json')
    config_path = root / definition['district']
    cfg = read(config_path)
    plan_path = config_path.parent / cfg['plan']
    plan, site, land, buildings, streets, houses = prepare(plan_path)
    baseline = read(config_path.parent / cfg['baseline'])
    x0, z0, x1, z1 = baseline['window']
    ox, oy, oz = site['world_offset']
    origin = [x0 + ox, cfg['y_base'], z0 + oz]
    source_size = [x1-x0, cfg['y_top']-cfg['y_base']+1, z1-z0]
    size = [math.ceil(source_size[0]/16)*16, source_size[1], math.ceil(source_size[2]/16)*16]
    if size[0] > 512 or size[2] > 512 or size[1] > 128:
        raise ValueError('V4 city exceeds bounded tile contract')
    paths = [root/'blueprints/worldplan/rules_v4.json', root/'blueprints/worldplan/compatible_v4.json', config_path, plan_path,
             config_path.parent/cfg['baseline'], config_path.parent/plan['site_reference'],
             config_path.parent/plan['landscape_reference'], root/'tools/yunshan/build_district.py',
             root/'tools/yunshan/baseline_houses.py', root/'tools/worldplan/district.py',
             root/'tools/yunshan/plan_district.py',
             *[config_path.parent/b['source'] for b in buildings]]
    sources = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
    frame = {'id':definition['city']['id'], 'name':definition['city']['name'],
             'size':size, 'source_size':source_size, 'source_origin':origin, 'sources':sources}
    frame['layout_hash'] = hashlib.sha256(json.dumps(frame,sort_keys=True,ensure_ascii=False).encode()).hexdigest()
    return definition, frame, buildings


def encode_rules(rules):
    # NBT strings have a 65535-byte modified UTF-8 limit. Tile hashes and the D1
    # height field make raw JSON too large. ASCII encoding is deterministic.
    raw = json.dumps(rules,sort_keys=True,ensure_ascii=False,separators=(',',':')).encode()
    encoded = 'gzip:' + base64.b64encode(gzip.compress(raw,mtime=0)).decode('ascii')
    if len(encoded) > 65000 or len(raw) > 4*1024*1024:
        raise ValueError('Frozen V4 rules exceed save envelope')
    return encoded


def compile_district(out: Path, root=ROOT):
    definition, frame, _ = descriptor(root)
    result = realize(root / definition['district'])
    cfg, plan, site, land, volume, levels, kinds, walking, buildings, houses, signs, planning = result
    changed = set().union(*(rect_cells(b['site']) for b in buildings),
        *(set(street_cells(s)) for s in plan['replace_streets']+plan['add_streets']),
        *(rect_cells(c['rect']) for c in plan['forecourts']))
    checked = validate_blocks(volume,walking,buildings,houses,signs,planning,site['world_offset'],changed)
    if not checked['passed']:
        raise ValueError('D1 source failed geometry/walkway validation')
    w,h,d = frame['size']; sw,_,sd = volume.size
    mx,my,mz = volume.minimum; ox,oy,oz = site['world_offset']
    surfaces = []
    for z in range(d):
        for x in range(w):
            local = (mx+min(x,sw-1)-ox, mz+min(z,sd-1)-oz)
            wet = kinds[local] in ('water','bridge')
            surfaces.append((cfg['lake_bed_level'] if wet else levels[local])+oy-my)
    # Run-length coding remains readable and inexpensive to validate at load.
    runs=[]
    for surface in surfaces:
        if runs and runs[-1][1]==surface: runs[-1][0]+=1
        else: runs.append([1,surface])
    relative_signs=[{**s,'pos':[s['pos'][i]-volume.minimum[i] for i in range(3)]} for s in signs]
    tiles=[]
    palette_count=len(volume.palette)
    for tz in range(0,d,16):
        for tx in range(0,w,16):
            cells={}
            for y in range(h):
                for z in range(16):
                    for x in range(16):
                        wx,wz=tx+x,tz+z
                        if wx<sw and wz<sd:
                            state=volume.get((mx+wx,my+y,mz+wz))
                        else:
                            surface=surfaces[wz*w+wx]
                            state=(definition['foundation_block'] if y<surface-1 else
                                   definition['padding_block'] if y==surface-1 else 'minecraft:air')
                        cells[x,y,z]=state
            tag=export.build_structure_nbt(export.Structure(cells),export.DEFAULT_DATA_VERSION,'MyVillage frozen Yunshan D1 V4')
            tile_signs=[{**s,'pos':[s['pos'][0]-tx,s['pos'][1],s['pos'][2]-tz]} for s in relative_signs
                        if tx<=s['pos'][0]<tx+16 and tz<=s['pos'][2]<tz+16]
            attach_signs(tag,nbt.Compound({}),tile_signs)
            name=f'tile_{tx//16}_{tz//16}'
            path=out/f'data/myvillage/structure/worldplan/yunshan_v4/{name}.nbt'
            path.parent.mkdir(parents=True,exist_ok=True);nbt.write_gzipped_nbt(tag,str(path))
            _,decoded=read_gzipped_nbt(str(path))
            restored={tuple(b['pos']):canonical(state_string(decoded['palette'][b['state']])) for b in decoded['blocks']}
            if restored!={p:canonical(s) for p,s in cells.items()}:
                raise ValueError('V4 tile roundtrip failed: '+name)
            for s in tile_signs:
                b=next(b for b in decoded['blocks'] if b['pos']==s['pos'])
                if [json.loads(t)['text'] for t in b['nbt']['front_text']['messages']]!=s['messages']:
                    raise ValueError('V4 sign roundtrip failed')
            tiles.append({'template':'myvillage:worldplan/yunshan_v4/'+name,'x':tx,'z':tz,
                          'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    compiled={'id':frame['id'],'name':frame['name'],'size':frame['size'],'layout_hash':frame['layout_hash'],
              'terraces':[{'last_z':d-1,'height':surfaces[0]-1}],
              'surface_runs':runs,'tiles':tiles,'life_layout':definition['life_layout']}
    report={**frame,'tiles':len(tiles),'retained_houses':len(houses),'functional_buildings':len(buildings),
            'source_blocks':len(volume.cells),'compiled_blocks':w*h*d,'signs':len(signs),
            'nbt_roundtrip':True,'surface_runs':len(runs),'geometry_checks':len(checked['checks']),
            'actual_game_generation':'not_verified_by_this_offline_generator'}
    return definition,compiled,report
