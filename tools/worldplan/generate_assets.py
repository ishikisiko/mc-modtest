#!/usr/bin/env python3
"""Compile frozen worldplan rules and tracked Yunshan sources into chunk-sized generated resources."""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
import json_to_nbt as nbt
import voxel_dump_to_structures as export
from buildgen.nbtread import read_gzipped_nbt,state_string
from yunshan.build_inn import attach_signs,check_walks,canonical
from yunshan.build_functional import rotated_pos,rotated_state,validate_site,validate_services
AUTHORED=ROOT/'blueprints/worldplan/rules_v1.json'
DEFAULT_OUT=ROOT/'build/generated/worldplan/resources'

def digest(data:bytes)->str:return hashlib.sha256(data).hexdigest()
def write_json(path:Path,data)->None:
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2,sort_keys=True)+'\n',encoding='utf-8')

def city_blocks(layout:dict)->tuple[dict,list,dict]:
    width,height,depth=layout['size']
    assert width%16==depth%16==0 and width<=128 and depth<=128
    palette=layout['palette']
    def terrace(z):return next(t['height'] for t in layout['terraces'] if z<=t['last_z'])
    blocks={(x,y,z):'minecraft:air' for y in range(height) for z in range(depth) for x in range(width)}
    for z in range(depth):
        floor=terrace(z)
        for x in range(width):
            for y in range(floor):blocks[x,y,z]=palette['foundation']
            blocks[x,floor,z]=palette['ground']
    inset=layout['wall_inset'];gate=layout['gate'];gate_min=gate['center_x']-gate['width']//2
    for z in range(inset,depth-inset):
        for x in range(inset,width-inset):
            wall=x in (inset,width-inset-1) or z in (inset,depth-inset-1)
            opening=z in (inset,depth-inset-1) and gate_min<=x<gate_min+gate['width']
            if wall and not opening:
                floor=terrace(z)
                for y in range(floor+1,floor+layout['wall_height']+1):blocks[x,y,z]=palette['wall']
                blocks[x,floor+layout['wall_height']+1,z]=palette['wall_cap']
    road_cells=set()
    for road in layout['roads']:
        x0,z0=road['from'];x1,z1=road['to'];assert x0==x1 or z0==z1,'authored road must be axis aligned'
        half=road['width']//2
        for z in range(min(z0,z1)-(half if z0==z1 else 0),max(z0,z1)+(half if z0==z1 else 0)+1):
            for x in range(min(x0,x1)-(half if x0==x1 else 0),max(x0,x1)+(half if x0==x1 else 0)+1):
                assert 0<=x<width and 0<=z<depth
                floor=terrace(z);blocks[x,floor,z]=palette['road']
                if z+1<depth and terrace(z+1)<floor:blocks[x,floor,z]=palette['stairs']
                road_cells.add((x,z))
    manifest_path=ROOT/layout['source_manifest'];manifest=json.loads(manifest_path.read_text())
    reference_path=manifest_path.parent/manifest['site_reference'];reference=json.loads(reference_path.read_text())
    origin=layout['source_origin'];signs,buildings=[],[];targets={'entry':[gate['center_x'],1,depth-4]};occupied=set()
    sources={str(manifest_path.relative_to(ROOT)):digest(manifest_path.read_bytes()),str(reference_path.relative_to(ROOT)):digest(reference_path.read_bytes())}
    for filename in manifest['buildings']:
        source=manifest_path.parent/filename;data=json.loads(source.read_text());size,_,cells,_=nbt.expand_structure(data)
        validate_site(data,reference);check_walks(cells,data['walk_targets'],size);validate_services(data,cells)
        q=data['site']['rotation_quarters'];offset=[a-b for a,b in zip(data['site']['world_min'],origin)]
        def position(p):return tuple(a+b for a,b in zip(rotated_pos(p,size,q),offset))
        changed={}
        for p,state in cells.items():
            at=position(p);assert all(0<=at[i]<layout['size'][i] for i in range(3)),(filename,at)
            assert at not in occupied,f'building volume overlap at {at}'
            occupied.add(at);changed[at]=canonical(rotated_state(state,q))
        blocks.update(changed)
        for sign in data['signs']:
            s=copy.deepcopy(sign);s['pos']=list(position(s['pos']));signs.append(s)
        for name,p in data['walk_targets'].items():targets[f'{source.stem}/{name}']=list(position(p))
        services=[{**s,'pos':list(position(s['pos'])),'state':canonical(rotated_state(s['state'],q))} for s in data.get('service_blocks',[])]
        buildings.append({'id':data['id'],'source':str(source.relative_to(ROOT)),'offset':offset,'rotation_quarters':q,
                          'source_sha256':digest(source.read_bytes()),'services':services,'entry':targets[f'{source.stem}/entry']})
        sources[str(source.relative_to(ROOT))]=digest(source.read_bytes())
    walk=check_walks(blocks,targets,layout['size'])
    return blocks,signs,{'buildings':buildings,'source_hashes':sources,'walk_targets':targets,'walk_check':walk,'road_cells':len(road_cells),'signs':signs}

def generate(out:Path)->dict:
    authored=json.loads(AUTHORED.read_text());city=authored['city'];layout_path=ROOT/city['layout'];layout=json.loads(layout_path.read_text())
    blocks,signs,report=city_blocks(layout);tiles=[];width,height,depth=layout['size']
    for tz in range(0,depth,16):
        for tx in range(0,width,16):
            cells={(x,y,z):blocks[x+tx,y,z+tz] for y in range(height) for z in range(16) for x in range(16)}
            root=export.build_structure_nbt(export.Structure(cells),export.DEFAULT_DATA_VERSION,'MyVillage seeded Yunshan V1 tile')
            tile_signs=[{**s,'pos':[s['pos'][0]-tx,s['pos'][1],s['pos'][2]-tz]} for s in signs if tx<=s['pos'][0]<tx+16 and tz<=s['pos'][2]<tz+16]
            attach_signs(root,nbt.Compound({}),tile_signs)
            name=f'tile_{tx//16}_{tz//16}';path=out/f'data/myvillage/structure/worldplan/yunshan_v1/{name}.nbt'
            path.parent.mkdir(parents=True,exist_ok=True);nbt.write_gzipped_nbt(root,str(path))
            _,decoded=read_gzipped_nbt(str(path))
            restored={tuple(b['pos']):canonical(state_string(decoded['palette'][b['state']])) for b in decoded['blocks']}
            assert restored=={p:canonical(s) for p,s in cells.items()},f'NBT tile roundtrip {name}'
            for s in tile_signs:
                sign=next(b['nbt'] for b in decoded['blocks'] if b['pos']==s['pos'])
                assert [json.loads(x)['text'] for x in sign['front_text']['messages']]==s['messages']
            tiles.append({'template':'myvillage:worldplan/yunshan_v1/'+name,'x':tx,'z':tz,'sha256':digest(path.read_bytes())})
    assert len(tiles)==width*depth//256
    report['layout_source_sha256']=digest(layout_path.read_bytes())
    layout_hash=digest(json.dumps({'layout':layout,'sources':report['source_hashes']},sort_keys=True,ensure_ascii=False).encode())
    compiled_layout={'id':layout['id'],'name':layout['name'],'size':layout['size'],'terraces':layout['terraces'],'layout_hash':layout_hash,'tiles':tiles}
    profiles=[];catalog=ROOT/authored.pop('catalog_directory');terrains=authored.pop('terrain_profiles')
    for name,terrain in sorted(terrains.items()):
        profile=json.loads((catalog/(name+'.json')).read_text());profile.pop('admitted_subjects',None);profiles.append({**profile,**terrain})
    authored['profiles']=profiles;authored['city']['layout']=compiled_layout
    write_json(out/'data/myvillage/worldplan/frozen_v1.json',authored)
    legacy=json.loads((AUTHORED.parent/'legacy_v1_hashes.json').read_text())
    for resource,expected in legacy['resources'].items():
        assert digest((out/resource).read_bytes())==expected, f'Frozen V1 resource changed: {resource}; keep old authored inputs and version new assets separately'
    write_json(out/'data/myvillage/worldplan/yunshan_v1.json',{'schema':1,**compiled_layout,**report,'scope':'four original source buildings; new placement streets/walls; not the unavailable complete D1 city'})
    source={'type':'minecraft:multi_noise','preset':'minecraft:overworld'}
    generator={'type':'myvillage:planned_v1','biome_source':source,'settings':'minecraft:overworld','rules':json.dumps(authored,ensure_ascii=False,sort_keys=True,separators=(',',':'))}
    preset={'dimensions':{
        'minecraft:overworld':{'type':'minecraft:overworld','generator':generator},
        'minecraft:the_nether':{'type':'minecraft:the_nether','generator':{'type':'minecraft:noise','settings':'minecraft:nether','biome_source':{'type':'minecraft:multi_noise','preset':'minecraft:nether'}}},
        'minecraft:the_end':{'type':'minecraft:the_end','generator':{'type':'minecraft:noise','settings':'minecraft:end','biome_source':{'type':'minecraft:the_end'}}}
    }}
    # Keep the complete V1 rules and city resource bytes for existing saves.
    # New worlds opt into a distinct codec and frozen climate algorithm.
    v2=json.loads((AUTHORED.parent/'rules_v2.json').read_text())
    assert v2['base']==AUTHORED.name and v2['algorithm']=='myvillage:worldplan_v2'
    current=copy.deepcopy(authored)
    current['algorithm']=v2['algorithm'];current['ecology']=v2['ecology']
    write_json(out/'data/myvillage/worldplan/frozen_v2.json',current)
    legacy_v2=json.loads((AUTHORED.parent/'legacy_v2_hashes.json').read_text())
    for resource,expected in legacy_v2['resources'].items():
        assert digest((out/resource).read_bytes())==expected, f'Frozen V2 resource changed: {resource}'
    v3=json.loads((AUTHORED.parent/'rules_v3.json').read_text())
    assert v3['base']==AUTHORED.name and v3['algorithm']=='myvillage:worldplan_v3'
    current=copy.deepcopy(authored)
    current['algorithm']=v3['algorithm'];current['ecotone']=v3['ecotone']
    write_json(out/'data/myvillage/worldplan/frozen_v3.json',current)
    generator['type']='myvillage:planned_v3'
    generator['rules']=json.dumps(current,ensure_ascii=False,sort_keys=True,separators=(',',':'))
    write_json(out/'data/myvillage/worldgen/world_preset/cultivation.json',preset)
    write_json(out/'data/minecraft/tags/worldgen/world_preset/normal.json',{'replace':False,'values':['myvillage:cultivation']})
    # Language entries aggregate across asset namespaces without replacing the existing mod file.
    write_json(out/'assets/myvillage_worldplan/lang/zh_cn.json',{'generator.myvillage.cultivation':'修仙大陆（种子蓝图）'})
    write_json(out/'assets/myvillage_worldplan/lang/en_us.json',{'generator.myvillage.cultivation':'Cultivation Continents (Seeded)'})
    report.update({'tiles':len(tiles),'size':layout['size'],'layout_hash':layout_hash,'nbt_roundtrip':True,'actual_game_generation':'not_verified_by_this_offline_generator'})
    return report

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,default=DEFAULT_OUT);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    if args.check:
        with tempfile.TemporaryDirectory() as tmp:
            temp=Path(tmp);report=generate(temp)
            expected={p.relative_to(temp):p.read_bytes() for p in temp.rglob('*') if p.is_file()}
            actual={p.relative_to(args.out):p.read_bytes() for p in args.out.rglob('*') if p.is_file()}
            assert expected==actual,'generated worldplan resources are stale or missing; regenerate'
    else:report=generate(args.out)
    write_json(ROOT/'build/worldplan/asset-report.json',report)
    print(json.dumps({k:report[k] for k in ('tiles','size','layout_hash','nbt_roundtrip')},ensure_ascii=False))
if __name__=='__main__':main()
