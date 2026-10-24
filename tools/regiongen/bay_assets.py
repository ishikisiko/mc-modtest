"""Generate versioned bay-settlement terrain metadata and chunk-owned NBT overlays."""
from __future__ import annotations
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import tempfile

from .assets import ROOT, canonical
from .bay import generate, RECIPE
from .build import write_json as _write_json
import json_to_nbt as nbt
from buildgen.nbtread import read_gzipped_nbt, state_string
from yunshan.build_inn import attach_signs

DEFAULT_OUT=ROOT/'build/generated/regiongen/resources'


def write_json(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    _write_json(path,value)


def compile_resources(out:Path,work:Path):
    plan,volume,preview,expected,signs,report=generate(work)
    recipe=plan['recipe'];cfg=recipe['worldgen'];n=plan['size'];base=recipe['base_y']
    if n%16 or (n+cfg['margin']*2)>256:raise ValueError('Structure exceeds native bounded reference range')
    for z in range(n):
        for x in range(n):
            if plan['heights'][z*n+x]<recipe['river']['water_y'] and min(x+cfg['margin'],z+cfg['margin'],n+cfg['margin']-1-x,n+cfg['margin']-1-z)<cfg['blend_width']:
                raise ValueError('Water bay reaches the native terrain transition')
    overlay={(x,y,z):preview['palette'][idx] for x,y,z,idx in preview['blocks']}
    # Preserve complete template envelopes, including air and block-entity positions.
    for cells in expected.values():overlay.update(cells)
    slices=defaultdict(dict)
    for (x,y,z),state in overlay.items():
        distance=min(x+cfg['margin'],z+cfg['margin'],n+cfg['margin']-1-x,n+cfg['margin']-1-z)
        if distance<cfg['blend_width']:raise ValueError(f'Authored overlay enters terrain transition: {(x,y,z)}')
        slices[x//16,z//16][x%16,y-base,z%16]=state
    tiles=[]
    for (tx,tz),cells in sorted(slices.items()):
        data={'size':[16,volume.size[1],16],'author':'MyVillage 湾口台地集 V1',
              'blocks':[{'pos':list(p),'state':s} for p,s in sorted(cells.items())],'entities':[]}
        root=nbt.structure_json_to_root_nbt(data)
        local_signs=[{**s,'pos':[s['pos'][0]-tx*16,s['pos'][1]-base,s['pos'][2]-tz*16]}
                     for s in signs if s['pos'][0]//16==tx and s['pos'][2]//16==tz]
        attach_signs(root,nbt.Compound({}),local_signs)
        template=f'regiongen/bay_terraces_v1/tile_{tx}_{tz}'
        path=out/f'data/myvillage/structure/{template}.nbt';path.parent.mkdir(parents=True,exist_ok=True)
        nbt.write_gzipped_nbt(root,str(path))
        _,restored=read_gzipped_nbt(str(path))
        decoded={tuple(b['pos']):canonical(state_string(restored['palette'][b['state']])) for b in restored['blocks']}
        if decoded!={p:canonical(s) for p,s in cells.items()}:raise AssertionError('NBT roundtrip differs: '+template)
        for s in local_signs:
            saved=next(b['nbt'] for b in restored['blocks'] if b['pos']==s['pos'])
            if [json.loads(line)['text'] for line in saved['front_text']['messages']]!=s['messages']:
                raise AssertionError('Sign text differs: '+template)
        tiles.append({'x':tx*16,'z':tz*16,'template':'myvillage:'+template,
                      'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'cells':len(cells)})
    manifest={'schema':1,'id':recipe['id'],'size':n,'base_y':base,'height':volume.size[1],
              'layout_hash':plan['plan_sha256'],'geometry_sha256':report['geometry_sha256'],
              'worldgen':cfg,'materials':recipe['materials'],'soil_depth':recipe['construction']['soil_depth'],
              'water_y':recipe['river']['water_y'],'heights':plan['heights'],'kinds':plan['kinds'],
              'tiles':tiles,'buildings':plan['buildings'],'entry':plan['spawn'],
              'source_sha256':hashlib.sha256(RECIPE.read_bytes()).hexdigest()}
    write_json(out/'data/myvillage/regiongen/bay_terraces_v1.json',manifest)
    write_json(out/'data/myvillage/worldgen/structure/bay_terraces.json',
               {'type':'myvillage:bay_terraces','biomes':'#myvillage:has_bay_terraces','step':'surface_structures',
                'terrain_adaptation':'none','spawn_overrides':{}})
    write_json(out/'data/myvillage/worldgen/structure_set/bay_terraces.json',
               {'structures':[{'structure':'myvillage:bay_terraces','weight':1}],
                'placement':{'type':'minecraft:random_spread','spacing':cfg['spacing'],'separation':cfg['separation'],
                             'salt':cfg['salt'],'exclusion_zone':{'other_set':cfg['exclusion_structure_set'],'chunk_count':cfg['exclusion_chunks']}}})
    write_json(out/'data/myvillage/tags/worldgen/biome/has_bay_terraces.json',{'replace':False,'values':cfg['biomes']})
    write_json(out/'assets/myvillage_regiongen/lang/zh_cn.json',{'structure.myvillage.bay_terraces':'湾口台地集'})
    report.update(tiles=len(tiles),overlay_cells=len(overlay),nbt_roundtrip=True,layout_hash=manifest['layout_hash'])
    write_json(work/'asset-report.json',report)
    return report


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,default=DEFAULT_OUT)
    parser.add_argument('--check',action='store_true');args=parser.parse_args()
    if args.check:
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);report=compile_resources(root/'resources',root/'work')
            expected={p.relative_to(root/'resources'):p.read_bytes() for p in (root/'resources').rglob('*') if p.is_file()}
            actual={p.relative_to(args.out):p.read_bytes() for p in args.out.rglob('*') if p.is_file()}
            if actual!=expected:raise AssertionError('Generated bay resources are stale; run bay_assets')
    else:report=compile_resources(args.out,ROOT/'build/regiongen')
    print(json.dumps({k:report[k] for k in ('passed','buildings','route_points','tiles','overlay_cells','nbt_roundtrip','geometry_sha256')},ensure_ascii=False))


if __name__=='__main__':main()
