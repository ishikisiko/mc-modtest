"""Compile the six actual Chapter 1 sites and their roads into chunk-owned settlement tiles.

The existing Qianzhen author owns every building and its relative position. This adapter
changes neither the source scene nor story bindings; it only normalizes and slices it.
"""
from __future__ import annotations
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
import json_to_nbt as nbt
from buildgen.groups import get_group
from buildgen.nbtread import read_gzipped_nbt,state_string
from qianzhen import story_assets

RECIPE=ROOT/'blueprints/regiongen/qianzhen_region_v1.json'
DEFAULT_OUT=ROOT/'build/generated/qianzhen_region/resources'
RESOURCE='data/myvillage/regiongen/qianzhen_region_v1.json'


def sha(data):return hashlib.sha256(data).hexdigest()
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,sort_keys=True,indent=2)+'\n')


def compile_resources(out):
    recipe=json.loads(RECIPE.read_text());group=get_group(recipe['group'])
    if recipe['id']!='qianzhen_region_v1' or recipe['schema']!=1:raise ValueError('Unsupported authored settlement')
    with tempfile.TemporaryDirectory(prefix='qianzhen-region-source-') as folder:
        source=Path(folder);scene_report=story_assets.generate(source,ROOT/recipe['source'])
        scene=json.loads((source/'data/myvillage/story/qianzhen_scene.json').read_text())
        if set(group.archetype_roster)!={s['id'] for s in scene['sites']}:raise ValueError('Chapter sites differ from registered settlement family')
        lo,hi=scene['bounds']['min'],scene['bounds']['max'];padding=recipe['padding']
        size=(max(hi[0]-lo[0]+1,hi[2]-lo[2]+1)+2*padding+15)//16*16
        if not 0<padding<=32 or size>512 or lo[1]!=0:raise ValueError('Invalid settlement frame')
        shift=[padding-lo[0],0,padding-lo[2]];height=hi[1]+1
        cells={};entities={};sites=[];tiles=[];sources={}
        def point(pos):return [pos[i]+shift[i] for i in range(3)]
        for site in scene['sites']:
            path=source/('data/myvillage/structure/'+site['template'].split(':',1)[1]+'.nbt')
            if sha(path.read_bytes())!=site['sha256']:raise ValueError('Source template drift')
            sources[site['id']]=site['sha256'];_,tag=read_gzipped_nbt(str(path));origin=point(site['origin'])
            for block in tag['blocks']:
                pos=tuple(origin[i]+block['pos'][i] for i in range(3))
                if pos in cells:raise ValueError('Overlapping authored scene')
                cells[pos]=state_string(tag['palette'][block['state']])
                if 'nbt' in block:entities[pos]=block['nbt']
            authored=json.loads((ROOT/recipe['source']/(site['id']+'.json')).read_text())
            sites.append({**site,'origin':origin,'display_name':authored['presentation']['name'],
                          'entry':[origin[i]+authored['walk_targets']['entry'][i] for i in range(3)]})
        for road in scene['roads']:
            pos=tuple(point(road['pos']))
            if pos in cells:raise ValueError('Road overwrites Chapter 1 building')
            cells[pos]=road['state']
        slices=defaultdict(dict)
        for (x,y,z),state in cells.items():
            if not(0<=x<size and 0<=z<size and 0<=y<height):raise ValueError('Overlay outside region')
            slices[x//16,z//16][x%16,y,z%16]=state
        for (tx,tz),owned in sorted(slices.items()):
            data={'size':[16,height,16],'author':'MyVillage 阡镇第一章聚落 V1',
                  'blocks':[{'pos':list(pos),'state':state} for pos,state in sorted(owned.items())],'entities':[]}
            tag=nbt.structure_json_to_root_nbt(data)
            for block in tag.value['blocks'].value:
                local=tuple(v.value for v in block.value['pos'].value);global_pos=(tx*16+local[0],local[1],tz*16+local[2])
                if global_pos in entities:block.value['nbt']=nbt._nbt_value_to_tag('nbt',entities[global_pos])
            template=f'regiongen/qianzhen_region_v1/tile_{tx}_{tz}'
            path=out/('data/myvillage/structure/'+template+'.nbt');path.parent.mkdir(parents=True,exist_ok=True)
            nbt.write_gzipped_nbt(tag,str(path));_,restored=read_gzipped_nbt(str(path))
            actual={tuple(b['pos']):story_assets.canonical(state_string(restored['palette'][b['state']])) for b in restored['blocks']}
            if actual!={p:story_assets.canonical(s) for p,s in owned.items()}:raise ValueError('Chapter tile roundtrip differs')
            for block in restored['blocks']:
                x,y,z=block['pos'];expected=entities.get((tx*16+x,y,tz*16+z))
                if block.get('nbt')!=expected:raise ValueError('Chapter block entity roundtrip differs')
            tiles.append({'x':tx*16,'z':tz*16,'template':'myvillage:'+template,'sha256':sha(path.read_bytes()),'cells':len(owned)})
        result={'schema':1,'id':recipe['id'],'size':size,'height':height,'terrain':recipe['terrain'],'siting':recipe['siting'],
                'scene_version':scene['version'],'scene_sha256':sha((source/'data/myvillage/story/qianzhen_scene.json').read_bytes()),
                'source_templates':sources,'sites':sites,'tiles':tiles,'source_shift':shift,
                'entry':point(scene['positions']['spawn']['pos']),
                'positions':{key:{k:point(p) for k,p in value.items()} for key,value in scene['positions'].items()},
                'roads':[{**road,'pos':point(road['pos'])} for road in scene['roads']],
                'authored_inputs':scene['inputs'],'recipe_sha256':sha(RECIPE.read_bytes())}
        result['layout_hash']=sha(json.dumps(result,sort_keys=True,ensure_ascii=False,separators=(',',':')).encode())
        write(out/RESOURCE,result)
        report={'passed':True,'sites':len(sites),'tiles':len(tiles),'cells':len(cells),'block_entities':len(entities),
                'size':size,'height':height,'layout_hash':result['layout_hash'],'source_templates':sources,
                'source_geometry_unchanged':True,'connected_stations':scene_report['connected_stations'],'nbt_roundtrip':True}
        return report


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,default=DEFAULT_OUT);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    if args.check:
        with tempfile.TemporaryDirectory(prefix='qianzhen-region-check-') as folder:
            temp=Path(folder);report=compile_resources(temp)
            expected={p.relative_to(temp):p.read_bytes() for p in temp.rglob('*') if p.is_file()}
            actual={p.relative_to(args.out):p.read_bytes() for p in args.out.rglob('*') if p.is_file()}
            if expected!=actual:raise ValueError('Stale Qianzhen region resources')
    else:report=compile_resources(args.out)
    print(json.dumps(report,ensure_ascii=False))


if __name__=='__main__':main()
