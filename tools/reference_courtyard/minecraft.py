#!/usr/bin/env python3
"""Bake the authored quarter-meter model into reusable Minecraft block pieces.

Library IDs are content-addressed and append-only. --extend-library explicitly
accepts newly authored patterns; ordinary generation/check never prunes an ID.
All render boxes and server collision boxes come from the same occupancy mask.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import hashlib
import io
import json
from pathlib import Path
import struct
import sys
import tempfile
import zlib

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
sys.path.insert(0,str(ROOT/'tools'))
sys.path.insert(0,str(ROOT))
from tools.reference_courtyard.build import Model
from buildgen.modset import load_modset
from buildgen.nbtread import read_gzipped_nbt, state_string
import json_to_nbt as nbt
import voxel_dump_to_structures as exports

ASSETS=ROOT/'src/main/resources/assets/myvillage'
DATA=ROOT/'src/main/resources/data/myvillage'
LIBRARY=HERE/'piece_library.json'


def packed(cells):
    return tuple(sorted((*p,mat) for p,mat in cells.items()))


def transform(cells,turns=0,mirror=False):
    result={((3-x) if mirror else x,y,z):mat for (x,y,z),mat in cells.items()}
    for _ in range(turns%4):
        result={(3-z,y,x):mat for (x,y,z),mat in result.items()}
    return result


def canonical(cells):
    choices=[]
    for mirror in [False,True]:
        for turn in range(4):
            key=packed(transform(cells,turn,mirror))
            inverse=turn if mirror else (-turn)%4
            choices.append((key,inverse,mirror))
    return min(choices)


def piece_id(key,config):
    dominant=sorted(Counter(row[3] for row in key).items(),key=lambda kv:(-kv[1],kv[0]))[0][0]
    family=config['materials'][dominant]['family']
    digest=hashlib.sha256(json.dumps(key,separators=(',',':')).encode()).hexdigest()[:12]
    return f'courtyard_{family}_{digest}'


def merged_boxes(cells):
    """Lossless local greedy cuboids; bounds are Minecraft model units (0..16)."""
    left=dict(cells);result=[]
    for x,y,z in sorted(cells):
        if (x,y,z) not in left:continue
        mat=left[(x,y,z)];x1=x+1
        while left.get((x1,y,z))==mat:x1+=1
        z1=z+1
        while all(left.get((a,y,z1))==mat for a in range(x,x1)):z1+=1
        y1=y+1
        while all(left.get((a,y1,b))==mat for a in range(x,x1) for b in range(z,z1)):y1+=1
        for a in range(x,x1):
            for b in range(y,y1):
                for c in range(z,z1):del left[(a,b,c)]
        result.append({'from':[4*x,4*y,4*z],'to':[4*x1,4*y1,4*z1],'material':mat})
    return result


def model_elements(boxes):
    result=[]
    for b in boxes:
        x0,y0,z0=b['from'];x1,y1,z1=b['to']
        faces={
            'down':([x0,16-z1,x1,16-z0],y0==0),
            'up':([x0,z0,x1,z1],y1==16),
            'north':([16-x1,16-y1,16-x0,16-y0],z0==0),
            'south':([x0,16-y1,x1,16-y0],z1==16),
            'west':([z0,16-y1,z1,16-y0],x0==0),
            'east':([16-z1,16-y1,16-z0,16-y0],x1==16),
        }
        result.append({'from':b['from'],'to':b['to'],'faces':{side:{'uv':uv,'texture':'#'+b['material'],**({'cullface':side} if boundary else {})} for side,(uv,boundary) in faces.items()}})
    return result


def png_texture(material,spec,config):
    """Generator-owned pixel materials, no external or AI image dependency."""
    size=config['texture_size'];noise=config['texture_noise']
    rgb=tuple(int(spec['color'][i:i+2],16) for i in [1,3,5]);pixels=bytearray()
    for y in range(size):
        pixels.append(0)
        for x in range(size):
            seed=int.from_bytes(hashlib.sha256(f'{material}:{x}:{y}'.encode()).digest()[:4],'big')
            d=seed%(2*noise+1)-noise
            if material in ('timber','deck'):
                d+=(x%4==0)*-8+(y%8==0 and (x+y)%4==0)*-5
            elif material=='stone':
                if y in (0,8) or (x+(8 if y>=8 else 0))%16==0:d-=19
            elif material=='tile':
                d+=(x%4==0)*9+(y%8==0)*-8
            elif material=='plaster':
                d=round(d*.5)
            elif material=='lamp':
                d=round(d*.3)
            pixels.extend(max(0,min(255,c+d)) for c in rgb)
            pixels.append(255)
    def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(bytes(pixels),9))+chunk(b'IEND',b'')


def source_cells(config):
    authored=json.loads((HERE/'model.json').read_text())
    assert config['micro_grid']==4 and authored['voxel_meters']==.25
    m=Model(authored).build();cells=defaultdict(dict);water=set()
    for (x,y,z),(material,part) in m.voxels.items():
        x=authored['size'][0]-1-x
        pos=(x//4,y//4,z//4)
        if part=='water':water.add(pos);continue
        cells[pos][(x%4,y%4,z%4)]=config['aliases'].get(material,material)
    return authored,cells,water


def library_for(cells,config,extend=False):
    existing=json.loads(LIBRARY.read_text()) if LIBRARY.exists() else {'format':1,'pieces':[]}
    lookup={p['id']:p for p in existing['pieces']};placements={};missing={}
    for pos,local in sorted(cells.items()):
        key,turn,mirrored=canonical(local);identity=piece_id(key,config)
        entry={'id':identity,'cells':[list(row) for row in key]}
        if identity in lookup:
            assert lookup[identity]==entry,identity
        else:missing[identity]=entry
        assert transform({tuple(row[:3]):row[3] for row in key},turn,mirrored)==local
        placements[pos]=(identity,turn,mirrored)
    if missing:
        if not extend:raise ValueError(f'{len(missing)} new authored shapes; run --extend-library to add stable IDs')
        lookup.update(missing)
        existing={'format':1,'pieces':[lookup[k] for k in sorted(lookup)]}
        LIBRARY.write_text(json.dumps(existing,separators=(',',':'),ensure_ascii=False)+'\n')
    return existing,placements


def generate(extend=False):
    config=json.loads((HERE/'minecraft.json').read_text())
    authored,cells,water=source_cells(config)
    library,placements=library_for(cells,config,extend)
    out={};catalog=[];langs={'zh_cn':{},'en_us':{}};family_counts=Counter()
    def j(path,data):out[path]=(json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n').encode()
    materials=config['materials']
    for p in library['pieces']:
        identity=p['id'];local={tuple(row[:3]):row[3] for row in p['cells']}
        dominant=sorted(Counter(local.values()).items(),key=lambda kv:(-kv[1],kv[0]))[0][0]
        spec=materials[dominant];family=spec['family'];family_counts[family]+=1
        boxes=merged_boxes(local);solid=merged_boxes({point:'solid' for point in local})
        full=len(local)==64
        light=config['light_level'] if 'lamp' in local.values() else 0
        catalog.append({'id':identity,'family':family,'sound':spec['sound'],'strength':spec['strength'],'full':full,'light':light,'boxes':[[*b['from'],*b['to']] for b in solid]})
        variants={}
        for mirrored in [False,True]:
            suffix='_mirrored' if mirrored else ''
            transformed=transform(local,mirror=mirrored)
            model={'parent':'minecraft:block/block','ambientocclusion':True,
                   'textures':{'particle':f'myvillage:block/courtyard/{dominant}',**{mat:f'myvillage:block/courtyard/{mat}' for mat in sorted(set(local.values()))}},
                   'elements':model_elements(merged_boxes(transformed))}
            j(ASSETS/f'models/block/courtyard/{identity}{suffix}.json',model)
            for turn,facing in enumerate(['north','east','south','west']):
                variants[f'facing={facing},mirrored={str(mirrored).lower()}']={'model':f'myvillage:block/courtyard/{identity}{suffix}','y':90*turn}
        j(ASSETS/f'blockstates/{identity}.json',{'variants':variants})
        j(ASSETS/f'models/item/{identity}.json',{'parent':f'myvillage:block/courtyard/{identity}'})
        j(DATA/f'loot_table/blocks/{identity}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'myvillage:'+identity,'functions':[{'function':'minecraft:copy_state','block':'myvillage:'+identity,'properties':['mirrored']}]}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
        label=f'{family_counts[family]:03}'
        langs['zh_cn']['block.myvillage.'+identity]=f'庭院{spec["zh"]}构件 · {label}'
        langs['en_us']['block.myvillage.'+identity]=f'Courtyard {spec["en"]} Piece {label}'
    j(DATA/'courtyard/pieces.json',{'format':1,'pieces':catalog})
    for lang,entries in langs.items():
        path=ASSETS/f'lang/{lang}.json';data=json.loads(path.read_text())
        # This generator owns only its own translation prefix and group title.
        data={k:v for k,v in data.items() if not k.startswith('block.myvillage.courtyard_')}
        data.update(entries);data['itemGroup.myvillage.courtyard']='月洞水院 · 建筑构件' if lang=='zh_cn' else 'Moon Gate Courtyard · Building Pieces'
        out[path]=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode()
    for material,spec in materials.items():
        out[ASSETS/f'textures/block/courtyard/{material}.png']=png_texture(material,spec,config)
    for tool,sounds in [('axe',{'wood'}),('pickaxe',{'stone','metal'})]:
        # Classification only. Decorative pieces require no specific tool;
        # shared vanilla mineable tags retain their existing generator owner.
        j(DATA/f'tags/block/courtyard_{tool}.json',{'replace':False,'values':['myvillage:'+p['id'] for p in catalog if p['sound'] in sounds]})
    blocks={};rows={p['id']:p for p in catalog}
    for pos,(identity,turn,mirror) in placements.items():
        wet=pos in water
        assert not(wet and rows[identity]['full']),('water inside full block',pos)
        blocks[pos]=f'myvillage:{identity}[facing={['north','east','south','west'][turn]},mirrored={str(mirror).lower()},waterlogged={str(wet).lower()}]'
    for pos in water-cells.keys():blocks[pos]='minecraft:water[level=0]'
    size=[(n+3)//4 for n in authored['size']]
    # Explicit air creates a faithful standalone sample when placed on prepared ground.
    for x in range(size[0]):
        for y in range(size[1]):
            for z in range(size[2]):blocks.setdefault((x,y,z),'minecraft:air')
    states=sorted(set(blocks.values()))
    errors=load_modset('vanilla').palette_block_errors(states)
    assert not errors,errors
    structure=exports.Structure(blocks)
    with tempfile.TemporaryDirectory() as temp:
        nbt_path=Path(temp)/'sample.nbt'
        nbt.write_gzipped_nbt(exports.build_structure_nbt(structure,exports.DEFAULT_DATA_VERSION,'MyVillage reference courtyard'),str(nbt_path))
        out[DATA/f'structure/{config["template"]}.nbt']=nbt_path.read_bytes()
        _,root=read_gzipped_nbt(str(nbt_path));palette=[state_string(p) for p in root['palette']]
        restored={tuple(b['pos']):palette[b['state']] for b in root['blocks']}
        assert len(restored)==len(blocks)
        for p,s in blocks.items():assert nbt.parse_block_state(s)==nbt.parse_block_state(restored[p]),p
        schem,_=exports.build_schem(structure,exports.DEFAULT_DATA_VERSION)
        sp=Path(temp)/'sample.schem';exports.write_gzipped_nbt_ext(schem,sp,'Schematic')
        out[ROOT/'out/preview/reference-courtyard-mc/reference_courtyard.schem']=sp.read_bytes()
    runtime={'template':'myvillage:'+config['template'],'size':size,'origin':config['runtime_origin'],
             'piece_count':len(catalog),'placed_piece_cells':len(cells),'real_water_cells':len(water-cells.keys()),'waterlogged_cells':len(water&cells.keys()),
             'palette_families':dict(family_counts),'viewpoints':authored['views'],
             'material_counts':dict(Counter(p['family'] for p in catalog)),
             'source_hashes':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [HERE/'build.py',HERE/'model.json',HERE/'minecraft.json',LIBRARY]},
             'fixture_cells':[[*p,s] for p,s in blocks.items() if s!='minecraft:air']}
    j(ROOT/'out/preview/reference-courtyard-mc/reference_courtyard.structure.json',{
        'id':runtime['template'],'size':size,'fill_air':True,
        'blocks':[{'pos':list(p),'state':s} for p,s in blocks.items() if s!='minecraft:air'],
        'site':{'world_min':config['runtime_origin']},'presentation':{'role':'courtyard','name':authored['name']}})
    j(ROOT/'out/preview/reference-courtyard-mc/build-manifest.json',runtime)
    out[ROOT/'out/preview/reference-courtyard-mc/reference_courtyard.nbt']=out[DATA/f'structure/{config["template"]}.nbt']
    return out,runtime,catalog,cells,placements


def validate_outputs(outputs,catalog,cells,placements):
    assert len({p['id'] for p in catalog})==len(catalog)
    counts=Counter();max_boxes=0
    for p in catalog:
        assert p['boxes'] and len(p['boxes'])<=64
        for b in p['boxes']:
            assert len(b)==6 and all(0<=v<=16 for v in b)
            assert all(b[i]<b[i+3] for i in range(3))
        max_boxes=max(max_boxes,len(p['boxes']))
        counts[p['family']]+=1
    # Compare every placed local mask against the shipped, rotated render boxes.
    for pos,(identity,turn,mirror) in placements.items():
        suffix='_mirrored' if mirror else ''
        model=json.loads(outputs[ASSETS/f'models/block/courtyard/{identity}{suffix}.json'])
        rebuilt={}
        for e in model['elements']:
            material=e['faces']['up']['texture'][1:]
            for x in range(e['from'][0]//4,e['to'][0]//4):
                for y in range(e['from'][1]//4,e['to'][1]//4):
                    for z in range(e['from'][2]//4,e['to'][2]//4):
                        assert (x,y,z) not in rebuilt
                        rebuilt[(x,y,z)]=material
        assert transform(rebuilt,turn)==cells[pos],pos
    return {'pieces':len(catalog),'families':dict(counts),'render_cells_verified':len(cells),'max_collision_boxes':max_boxes,'template_roundtrip':True}


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--check',action='store_true');ap.add_argument('--extend-library',action='store_true');args=ap.parse_args()
    if args.check and args.extend_library:ap.error('--check cannot extend the library')
    outputs,runtime,catalog,cells,placements=generate(args.extend_library)
    report=validate_outputs(outputs,catalog,cells,placements)
    mismatches=[]
    for path,data in outputs.items():
        generated_report=path.is_relative_to(ROOT/'out')
        if args.check and not generated_report:
            if not path.exists() or path.read_bytes()!=data:mismatches.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    if mismatches:raise SystemExit('Stale courtyard resources: '+', '.join(mismatches[:20]))
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__=='__main__':main()
