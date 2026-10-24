#!/usr/bin/env python3
"""Read generated V4 tiles and prove the life adapter references the actual placed geometry.

No world generation, game launches or writes to source resources. Run generate_assets.py first.
This is structural evidence only, not evidence of native chunk generation or NPC pathfinding.
"""
from __future__ import annotations
import argparse
import base64
import gzip
import hashlib
import json
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from buildgen.nbtread import read_gzipped_nbt,state_string
from gen_npc_life_places import generate
from worldplan.district import descriptor


def check(out:Path) -> dict:
    legacy_count=0
    for version in (1,2,3,4,5,6):
        source=json.loads((ROOT/f'blueprints/worldplan/legacy_v{version}_hashes.json').read_text())
        for path,expected in source['resources'].items():
            if hashlib.sha256((out/path).read_bytes()).hexdigest()!=expected:
                raise ValueError(f'Changed legacy V{version} asset: {path}')
            legacy_count+=1
    frozen=json.loads((out/'data/myvillage/worldplan/frozen_v4.json').read_text())
    layout=frozen['city']['layout'];w,h,d=layout['size']
    preset=json.loads((out/'data/myvillage/worldgen/world_preset/cultivation.json').read_text())
    encoded=preset['dimensions']['minecraft:overworld']['generator']['rules']
    if not encoded.startswith('gzip:') or len(encoded)>65000:
        raise ValueError('V4 rules do not fit the per-string NBT envelope')
    preset_rules=json.loads(gzip.decompress(base64.b64decode(encoded[5:],validate=True)))
    current=json.loads((out/'data/myvillage/worldplan/frozen_v7.json').read_text())
    if preset_rules!=current or preset_rules['city']!=frozen['city']:
        raise ValueError('Preset or retained V4 city rules disagree')
    _,frame,_=descriptor(ROOT)
    life=next(p for p in generate()['layouts'] if p['id']==layout['life_layout'])
    if life['size']!=[w,h,d] or life['worldplan_layout_hash']!=layout['layout_hash'] or layout['layout_hash']!=frame['layout_hash']:
        raise ValueError('Life, source and generation frames differ')
    tiles={(t['x'],t['z']):t for t in layout['tiles']}
    if len(tiles)!=w*d//256:raise ValueError('Incomplete city tiling')
    for tile in tiles.values():
        path=out/('data/myvillage/structure/'+tile['template'].split(':')[1]+'.nbt')
        if hashlib.sha256(path.read_bytes()).hexdigest()!=tile['sha256']:
            raise ValueError('Changed frozen tile: '+str(path))
    cache={}
    def block(pos):
        x,y,z=pos
        if not (0<=x<w and 0<=y<h and 0<=z<d):raise ValueError('Probe outside city')
        origin=(x//16*16,z//16*16)
        if origin not in cache:
            tile=tiles[origin];path=out/('data/myvillage/structure/'+tile['template'].split(':')[1]+'.nbt')
            _,tag=read_gzipped_nbt(str(path))
            if tag['size']!=[16,h,16] or len(tag['blocks'])!=16*h*16:raise ValueError('Wrong tile bounds/air coverage')
            cache[origin]={tuple(b['pos']):state_string(tag['palette'][b['state']]) for b in tag['blocks']}
        return cache[origin][(x%16,y,z%16)]
    probes=0
    for p in life['places']:
        x,y,z=p['pos']
        if block([x,y,z])!='minecraft:air' or block([x,y+1,z])!='minecraft:air':
            raise ValueError('Actual D1 geometry blocks NPC station '+p['id'])
        if block([x,y-1,z]) in ('minecraft:air','minecraft:water'):raise ValueError('Unsupported D1 station '+p['id'])
        probes+=3
        if p['block']:
            if block(p['block_pos']).split('[')[0]!=p['block']:raise ValueError('Wrong actual D1 facility '+p['id'])
            probes+=1
    return {'legacy_asset_hashes':legacy_count,'v4_tiles':len(tiles),'places':len(life['places']),
            'actual_tile_station_probes':probes,'frozen_text_length':len(encoded),'layout_hash':layout['layout_hash'],
            'native_game':'not_run'}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources',type=Path,default=ROOT/'build/generated/worldplan/resources')
    args=parser.parse_args();print(json.dumps(check(args.resources),ensure_ascii=False,indent=2))
if __name__=='__main__':main()
