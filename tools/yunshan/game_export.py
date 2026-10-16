"""Convert a Minecraft-saved structure to Sponge v2 without losing NBT types.

Unlike a voxel-only dump, this preserves live block entities (signs, furnace
inventories, campfire timers, beds, components) from the game's own save.
"""
from __future__ import annotations
from collections import OrderedDict
import copy
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import struct
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
import json_to_nbt as nbt
import voxel_dump_to_structures as export
from yunshan.build_inn import canonical

FORMATS={1:'>b',2:'>h',3:'>i',4:'>q',5:'>f',6:'>d'}


def exact(f,n):
    data=f.read(n)
    if len(data)!=n:raise EOFError('truncated NBT')
    return data


def utf(f):
    n=struct.unpack('>H',exact(f,2))[0]
    return exact(f,n).decode('utf-8')


def read_payload(f,kind):
    if kind in FORMATS:
        fmt=FORMATS[kind];return nbt.Tag(kind,struct.unpack(fmt,exact(f,struct.calcsize(fmt)))[0])
    if kind==8:return nbt.String(utf(f))
    if kind in (7,11,12):
        count=struct.unpack('>i',exact(f,4))[0]
        if count<0 or count>4_000_000:raise ValueError('invalid NBT array length')
        value=exact(f,count) if kind==7 else [struct.unpack('>i' if kind==11 else '>q',exact(f,4 if kind==11 else 8))[0] for _ in range(count)]
        return nbt.Tag(kind,value)
    if kind==9:
        element=exact(f,1)[0];count=struct.unpack('>i',exact(f,4))[0]
        if count<0 or count>4_000_000:raise ValueError('invalid NBT list length')
        return nbt.Tag(kind,[read_payload(f,element) for _ in range(count)],element)
    if kind==10:
        values=OrderedDict()
        while True:
            child=exact(f,1)[0]
            if child==0:break
            name=utf(f);values[name]=read_payload(f,child)
        return nbt.Compound(values)
    raise ValueError(f'unsupported NBT kind {kind}')


def read_typed(path):
    with gzip.open(path,'rb') as f:
        if exact(f,1)!=b'\x0a':raise ValueError('root must be compound')
        name=utf(f);root=read_payload(f,10)
        if f.read(1):raise ValueError('trailing NBT data')
        return name,root


def write_payload(f,tag):
    k=tag.tag_id
    if k in FORMATS:f.write(struct.pack(FORMATS[k],tag.value))
    elif k==8:nbt._write_utf(f,tag.value)
    elif k in (7,11,12):
        f.write(struct.pack('>i',len(tag.value)))
        if k==7:f.write(bytes(tag.value))
        else:
            for v in tag.value:f.write(struct.pack('>i' if k==11 else '>q',v))
    elif k==9:
        f.write(bytes([tag.element_type]));f.write(struct.pack('>i',len(tag.value)))
        for child in tag.value:write_payload(f,child)
    elif k==10:
        for name,child in tag.value.items():
            f.write(bytes([child.tag_id]));nbt._write_utf(f,name);write_payload(f,child)
        f.write(b'\0')
    else:raise ValueError(f'unsupported NBT kind {k}')


def write_typed(path,name,root):
    path.parent.mkdir(parents=True,exist_ok=True)
    with path.open('wb') as raw,gzip.GzipFile(filename='',fileobj=raw,mode='wb',mtime=0) as f:
        f.write(b'\x0a');nbt._write_utf(f,name);write_payload(f,root)


def plain(tag):
    if tag.tag_id==10:return {k:plain(v) for k,v in tag.value.items()}
    if tag.tag_id==9:return [plain(v) for v in tag.value]
    if tag.tag_id==7:return list(tag.value)
    return tag.value


def saved_cells(root):
    data=plain(root);palette=[]
    for p in data['palette']:
        props=p.get('Properties',{})
        palette.append(p['Name']+('['+','.join(f'{k}={v}' for k,v in sorted(props.items()))+']' if props else ''))
    size=data['size'];cells={(x,y,z):'minecraft:air' for y in range(size[1]) for z in range(size[2]) for x in range(size[0])}
    for b in data['blocks']:cells[tuple(b['pos'])]=palette[b['state']]
    return size,cells


def compare_authored(root,authored):
    expected_size,_,expected,_=nbt.expand_structure(authored)
    actual_size,actual=saved_cells(root)
    if actual_size!=expected_size:raise AssertionError(f'game export size {actual_size} != authored {expected_size}')
    mismatches=[]
    for pos,state in expected.items():
        name,props=nbt.parse_block_state(state);actual_name,actual_props=nbt.parse_block_state(actual[pos])
        if name!=actual_name or any(props[k]!=actual_props.get(k) for k in ('facing','half','type','axis') if k in props):
            mismatches.append({'pos':pos,'expected':state,'actual':actual[pos]})
    if mismatches:raise AssertionError(f'game block mismatch ({len(mismatches)}): {mismatches[:12]}')
    return {'cells_compared':len(expected),'base_ids_and_structural_states_match':True,
            'neighbour_derived_properties':'kept from actual game save'}


def convert_game_save(source,out_stem,authored):
    _,root=read_typed(source)
    comparison=compare_authored(root,authored)
    size,cells=saved_cells(root);structure=export.Structure(cells)
    schem,_=export.build_schem(structure,root.value['DataVersion'].value)
    entities=[];json_entities=[]
    for block in root.value['blocks'].value:
        if 'nbt' not in block.value:continue
        pos=[p.value for p in block.value['pos'].value]
        data=copy.deepcopy(block.value['nbt']);json_entities.append({'pos':pos,'nbt':plain(data)})
        identity=data.value.pop('id')
        for key in ('x','y','z'):data.value.pop(key,None)
        data.value['Id']=identity;data.value['Pos']=export.IntArray(pos)
        entities.append(data)
    schem.value['BlockEntities']=nbt.ListTag(nbt.TAG_COMPOUND,entities)
    out_stem.parent.mkdir(parents=True,exist_ok=True)
    nbt_path=out_stem.with_suffix('.nbt');schem_path=out_stem.with_suffix('.schem')
    shutil.copyfile(source,nbt_path);write_typed(schem_path,'Schematic',schem)
    _,readback=read_typed(schem_path)
    if readback!=schem:raise AssertionError('Sponge typed-NBT readback mismatch')
    sp=plain(readback);ids=export.decode_varints(sp['BlockData']);pal={i:s for s,i in sp['Palette'].items()}
    for (x,y,z),state in cells.items():
        actual=pal[ids[export.schem_index(x,y,z,size[0],size[2])]]
        if canonical(actual)!=canonical(state):raise AssertionError('Sponge cell mismatch')
    digest=hashlib.sha256(source.read_bytes()).hexdigest()
    meta={'source':'Minecraft structure-block SAVE','minecraft_nbt_sha256':digest,
          'block_entities':json_entities,'authored_id':authored['id'],'site':authored['site']}
    blueprint=export.build_blueprint(structure,authored['id']+'_game',meta)
    export.write_blueprint(blueprint,out_stem.with_suffix('.blueprint.json'))
    report={**comparison,'size':size,'saved_block_entities':len(entities),'game_nbt_sha256':digest,
            'nbt_is_unmodified_game_save':nbt_path.read_bytes()==source.read_bytes(),
            'schem_typed_nbt_roundtrip':True,'non_air_blocks':sum(s!='minecraft:air' for s in cells.values())}
    out_stem.with_suffix('.report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    return report
