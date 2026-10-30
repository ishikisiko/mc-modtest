#!/usr/bin/env python3
"""七座城主府：原造型 -> 新材质/雕构方块 -> 独立分片 NBT。"""
from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import json
import math
from pathlib import Path
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT));sys.path.insert(0,str(ROOT/'tools'))
from tools.gen_alchemy_assets import png
from tools.lord_manors.geometry import cells,canonical,add_plaques,FULL
from tools.buildgen.groups import get_group
from tools.buildgen.modset import load_modset
from tools import json_to_nbt as nbt

INPUT=Path('blueprints/lord_manors')
ASSET=Path('src/main/resources/assets/myvillage')
DATA=Path('src/main/resources/data/myvillage')
FACINGS=('north','east','south','west')


def encoded(value):return (json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n').encode()
def read(path):return json.loads((ROOT/path).read_text())
def sha(value):return hashlib.sha256(value).hexdigest()


class Writer:
    def __init__(self,check):self.check=check;self.bad=[];self.count=0;self.expected=set()
    def put(self,path,value):
        path=ROOT/path;self.count+=1;self.expected.add(path)
        if self.check:
            if not path.is_file() or path.read_bytes()!=value:self.bad.append(str(path.relative_to(ROOT)))
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(value)
    def json(self,path,value):self.put(path,encoded(value))
    def finish(self):
        owned=[]
        for base in (ASSET/'textures/block/lord_manors',DATA/'structure/lord_manors',DATA/'lord_manors'):
            owned.extend(p for p in (ROOT/base).rglob('*') if p.is_file())
        for base in (ASSET/'models/block',ASSET/'models/item',ASSET/'blockstates',DATA/'loot_table/blocks'):
            for path in (ROOT/base).glob('manor_*'):
                owned.extend(p for p in path.rglob('*') if p.is_file()) if path.is_dir() else owned.append(path)
        for path in set(owned)-self.expected:
            if self.check:self.bad.append('未使用的七府生成资源：'+str(path.relative_to(ROOT)))
            else:path.unlink()


def material(city,role,color,cfg):
    return {'id':f'manor_{city}_{role}','role':role,'color':color.lstrip('#'),
            'name':cfg['city_names'][city]+'·'+cfg['role_names'].get(role,role),
            'collides':role not in cfg['non_collision_roles'],
            'opaque':role not in cfg['glass_roles']+cfg['leaf_roles']+['foam'],
            'sound':'wood' if role in cfg['wood_roles'] else 'glass' if role in cfg['glass_roles'] else 'grass' if role in cfg['leaf_roles'] else 'stone'}


def texture(m,cfg):
    color=tuple(bytes.fromhex(m['color']));role=m['role'];n=cfg['material_resolution'];rows=[]
    for y in range(n):
        row=[]
        for x in range(n):
            noise=((x*17+y*31+x*y*7)%19-9)*.48
            shade=noise+1.8*math.sin(x*.16+math.sin(y*.12)*2);alpha=255
            if role in cfg['wood_roles']:
                shade+=4*math.sin(x*.38+math.sin(y*.14))
                if x%16==0:shade-=11
            elif role=='roof':
                shade+=6*math.cos(x*math.pi/8)
                if y%32<2:shade-=10
            elif role in ('paving','paving_light','stone','stone_dark','seabed','dark_stone'):
                if y%32==0 or (x+(16 if y//32%2 else 0))%32==0:shade-=13
            elif role in ('bronze','gold_light','edge','seal','rune','sigil','carving'):
                shade+=3*math.cos(y*.3)
                if x in (4,n-5) or y in (4,n-5):shade-=12
                if role in ('seal','rune','sigil') and (abs(abs(x-n/2)+abs(y-n/2)-n*.3)<1):shade+=19
            elif role in cfg['glass_roles']:
                alpha=205 if role=='window' else 175
                shade+=3*math.sin((x+y)*.15)
                if x in (1,n-2) or y in (1,n-2):shade+=16
            elif role in cfg['leaf_roles']:
                shade+=6*math.sin(x*.5)*math.cos(y*.4)
                if (x*7+y*13+x*y)%43<3:alpha=0
            elif role in ('paper','cloth','banner'):
                shade+=((x+y)%3-1)*1.5
                if role=='paper' and 12<x<n-12 and y%11==0:shade-=32
            elif role=='snow':shade=noise*.5+2*math.sin(x*.2+y*.3)
            row.append(tuple(max(0,min(255,round(c+shade))) for c in color)+(alpha,))
        rows.append(row)
    motif=ROOT/INPUT/f'motifs/{m["id"].removeprefix("manor_")}.pgm'
    if motif.is_file():
        header,dims,maximum,pixels=motif.read_bytes().split(b'\n',3);w,h=map(int,dims.split())
        assert header==b'P5' and maximum==b'255' and len(pixels)==w*h
        ink=tuple(bytes.fromhex(cfg['inscriptions']['color']));decor=[]
        for y in range(h):
            row=[]
            for x in range(w):
                base=rows[y*n//h][x*n//w];a=pixels[y*w+x]/255
                row.append(tuple(round(c*(1-a)+v*a) for c,v in zip(base[:3],ink))+(255,))
            decor.append(row)
        return png(decor)
    return png(rows)


def plaque_texture(city,index,cfg):
    raw=(ROOT/INPUT/f'inscriptions/{city}_{index}.pgm').read_bytes();header,dims,maximum,pixels=raw.split(b'\n',3)
    w,h=cfg['inscriptions']['width'],cfg['inscriptions']['height']
    assert (header,dims,maximum)==(b'P5',f'{w} {h}'.encode(),b'255') and len(pixels)==w*h
    ink=tuple(bytes.fromhex(cfg['inscriptions']['color']));base=tuple(bytes.fromhex(cfg['inscriptions']['background']));rows=[]
    for y in range(h):
        row=[]
        for x in range(w):
            grain=((x*11+y*17+x*y)%13-6)*.5;alpha=pixels[y*w+x]/255
            if (x in (7,w-8) and 7<=y<h-7) or (y in (7,h-8) and 7<=x<w-7):alpha=.8
            row.append(tuple(round((c+grain)*(1-alpha)+i*alpha) for c,i in zip(base,ink))+(255,))
        rows.append(row)
    return png(rows)


def render_type(profile,definitions):
    if any(not definitions[b[6]]['opaque'] and definitions[b[6]]['role'] not in ('leaf','leaf_light','pine') for b in profile):return 'minecraft:translucent'
    if any(not definitions[b[6]]['opaque'] for b in profile):return 'minecraft:cutout'
    return 'minecraft:solid'


def model(profile,definitions,city):
    refs={};elements=[]
    for b in profile:
        role=b[6];ref=definitions[role]['id'];refs[role]='myvillage:block/lord_manors/'+ref
        lo=b[:3];hi=b[3:6];faces={}
        for face_index,(face,axis,positive) in enumerate([('west',0,False),('east',0,True),('down',1,False),('up',1,True),('north',2,False),('south',2,True)]):
            if face in ('north','south'):
                uv=[16-hi[0],16-hi[1],16-lo[0],16-lo[1]] if face=='north' else [lo[0],16-hi[1],hi[0],16-lo[1]]
            elif face in ('east','west'):
                uv=[lo[2],16-hi[1],hi[2],16-lo[1]] if face=='west' else [16-hi[2],16-hi[1],16-lo[2],16-lo[1]]
            else:uv=[lo[0],lo[2],hi[0],hi[2]]
            if len(b)==11 and face=='north':uv=list(b[7:11])
            if len(b)==31:uv=list(b[7+face_index*4:11+face_index*4])
            face_data={'texture':'#'+role,'uv':uv}
            if (hi if positive else lo)[axis] == (16 if positive else 0):face_data['cullface']=face
            faces[face]=face_data
        elements.append({'from':list(lo),'to':list(hi),'faces':faces})
    refs['particle']=next(iter(refs.values()))
    return {'parent':'minecraft:block/block','ambientocclusion':True,'render_type':render_type(profile,definitions),'textures':refs,'elements':elements}


def write_nbt(writer,path,states,size):
    assert not load_modset('vanilla').palette_block_errors(list(set(states.values())))
    document={'size':size,'author':'MyVillage 七府独立样板','entities':[],
              'blocks':[{'pos':list(p),'state':v} for p,v in sorted(states.items(),key=lambda v:(v[0][1],v[0][2],v[0][0]))]}
    root=nbt.structure_json_to_root_nbt(document)
    with tempfile.TemporaryDirectory() as temp:
        p=Path(temp)/'tile.nbt';nbt.write_gzipped_nbt(root,str(p));value=p.read_bytes()
    writer.put(path,value);return sha(value)


def base_resources(writer,m,cfg,names):
    ident=m['id'];ref='myvillage:block/lord_manors/'+ident
    if m['role'].startswith('plaque_'):return
    writer.put(ASSET/f'textures/block/lord_manors/{ident}.png',texture(m,cfg))
    render='minecraft:solid' if m['opaque'] else 'minecraft:cutout' if m['role'] in cfg['leaf_roles'] else 'minecraft:translucent'
    writer.json(ASSET/f'models/block/{ident}.json',{'parent':'minecraft:block/cube_all','render_type':render,'textures':{'all':ref}})
    writer.json(ASSET/f'blockstates/{ident}.json',{'variants':{'':{'model':'myvillage:block/'+ident}}})
    writer.json(ASSET/f'models/item/{ident}.json',{'parent':'myvillage:block/'+ident})
    writer.json(DATA/f'loot_table/blocks/{ident}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'myvillage:'+ident}]}]})
    names['block.myvillage.'+ident]=m['name']


def bank_resources(writer,bank,definitions,city,names):
    ident=bank['id'];states={};overrides=[];children=[]
    for i,profile in enumerate(bank['profiles']):
        part=f'{ident}/shape_{i}'
        writer.json(ASSET/f'models/block/{part}.json',model(profile,definitions,city))
        for turn,facing in enumerate(FACINGS):states[f'facing={facing},shape={i}']={'model':'myvillage:block/'+part,'y':turn*90}
        overrides.append({'predicate':{'custom_model_data':i+1},'model':'myvillage:block/'+part})
        children.append({'type':'minecraft:item','name':'myvillage:'+ident,
            'conditions':[{'condition':'minecraft:block_state_property','block':'myvillage:'+ident,'properties':{'shape':str(i)}}],
            'functions':[{'function':'minecraft:set_components','components':{'minecraft:block_state':{'shape':str(i)},'minecraft:custom_model_data':i+1}}]})
    writer.json(ASSET/f'blockstates/{ident}.json',{'variants':states})
    writer.json(ASSET/f'models/item/{ident}.json',{'parent':f'myvillage:block/{ident}/shape_0','overrides':overrides})
    writer.json(DATA/f'loot_table/blocks/{ident}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:alternatives','children':children}]}]})
    names['block.myvillage.'+ident]=bank['name']


def construct_city(city,cfg,writer,names):
    scene=read(INPUT/f'cities/{city}.json');identity=scene['identity']
    if identity['group']!='civic' or identity['archetype'] not in get_group('civic').archetype_roster:raise ValueError('七府必须经现有 civic/lord_manor 家族')
    raw=cells(scene,cfg.get('engraved_roles',[]));add_plaques(raw,scene)
    roles=sorted({b[6] for parts in raw.values() for b in parts})
    definitions={role:material(city,role,scene['materials'].get(role,'#3e3125'),cfg) for role in roles}
    materials=[]
    for role,m in definitions.items():
        if role.startswith('plaque_'):
            m['name']=cfg['city_names'][city]+'·题匾';m['opaque']=True;m['collides']=True
            writer.put(ASSET/f'textures/block/lord_manors/{m["id"]}.png',plaque_texture(city,int(role.split('_')[1]),cfg))
        else:materials.append(m);base_resources(writer,m,cfg,names)
    # Freeze cell profiles first; a horizontal rotation reuses the same sculpted piece.
    converted={};unique=set()
    for position,pieces in raw.items():
        p,turn=canonical(tuple(sorted(set(pieces),key=repr)));converted[position]=(p,turn)
        if not(len(p)==1 and p[0][:6]==FULL and len(p[0])==7):unique.add(p)
    profiles=sorted(unique,key=repr);lookup={p:i for i,p in enumerate(profiles)};banks=[];count=cfg['bank_size']
    for offset in range(0,len(profiles),count):
        bank_index=offset//count;part=profiles[offset:offset+count];part+=part[:1]*(count-len(part))
        bank={'id':f'manor_{city}_carved_{bank_index}','name':cfg['city_names'][city]+f'·精雕构件 {bank_index+1}',
              'profiles':[list(map(list,p)) for p in part]}
        bank_resources(writer,bank,definitions,city,names);banks.append(bank)
    blocks={}
    for position,(p,turn) in converted.items():
        if len(p)==1 and p[0][:6]==FULL and len(p[0])==7:state='myvillage:'+definitions[p[0][6]]['id']
        else:
            index=lookup[p];ident=banks[index//count]['id'];state=f'myvillage:{ident}[facing={FACINGS[turn]},shape={index%count}]'
        blocks[position]=state
    if scene.get('edict_roots'):
        pillar=read(DATA/'decoration/demon_pact_pillar.json')
        for root in scene['edict_roots']:
            for index,part in enumerate(pillar['parts']):
                p=tuple(a+b for a,b in zip(root,part['offset']));blocks[p]=f'myvillage:demon_pact_pillar[facing=north,part={index}]'
    known={'myvillage:'+m['id'] for m in materials}|{'myvillage:'+b['id'] for b in banks}|{'myvillage:demon_pact_pillar'}
    if {state.split('[',1)[0] for state in blocks.values()}-known:raise ValueError('七府原生调色板出现未注册构件')
    low=[min(p[i] for p in blocks) for i in range(3)];high=[max(p[i] for p in blocks) for i in range(3)];size=[b-a+1 for a,b in zip(low,high)]
    tiles=defaultdict(dict)
    for position,state in blocks.items():
        x,y,z=[p-a for p,a in zip(position,low)];tiles[x//16,z//16][x%16,y,z%16]=state
    manifest={'city':city,'name':identity['title'],'source_min':low,'size':size,'blocks':len(blocks),'profiles':len(profiles),
              'materials':len(materials),'banks':len(banks),'source_sha256':sha((ROOT/INPUT/f'cities/{city}.json').read_bytes()),'tiles':[],
              'cameras':scene['cameras'],'signs':[s[0] for s in scene.get('signs',[])],'effects':False,'city_binding':False}
    for (x,z),states in sorted(tiles.items()):
        template=f'lord_manors/{city}/tile_{x}_{z}';path=DATA/f'structure/{template}.nbt'
        digest=write_nbt(writer,path,states,[16,size[1],16]);manifest['tiles'].append({'template':'myvillage:'+template,'offset':[x*16,0,z*16],'blocks':len(states),'sha256':digest})
    # Only the generated shape/collision source goes in the runtime catalogue.
    for bank in banks:
        bank['profiles']=[{'boxes':list(profile),
            'collision':[b[:6] for b in profile if definitions[b[6]]['collides']],
            'occlusion':[b[:6] for b in profile if definitions[b[6]]['opaque']]} for profile in bank['profiles']]
    writer.json(DATA/f'lord_manors/{city}.json',manifest)
    print(json.dumps({k:manifest[k] for k in ('city','blocks','profiles','materials','banks','size')},ensure_ascii=False),flush=True)
    return materials,banks,manifest


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    cfg=read(INPUT/'config.json');assert cfg['grid']==16 and cfg['bank_size']==32 and len(cfg['cities'])==7
    writer=Writer(args.check);names={k:v for k,v in json.loads((ROOT/ASSET/'lang/zh_cn.json').read_text()).items() if not k.startswith('block.myvillage.manor_')};materials=[];banks=[];manors=[]
    for city in cfg['cities']:
        m,b,s=construct_city(city,cfg,writer,names);materials+=m;banks+=b;manors.append({k:s[k] for k in ('city','name','size','blocks','source_min')})
    marker={'id':'manor_survey_stone','role':'stone_dark','color':'5a6266','name':'府邸样板界石','collides':True,'opaque':True,'sound':'stone'}
    base_resources(writer,marker,cfg,names);materials.append(marker)
    catalog={'schema':1,'strength':cfg['strength'],'explosion_resistance':cfg['explosion_resistance'],'gallery':cfg['gallery'],
             'materials':materials,'banks':banks,'manors':manors}
    writer.json(DATA/'lord_manors/catalog.json',catalog)
    names['itemGroup.myvillage.lord_manors']='七府营造';names['tooltip.myvillage.manor_component']='中键取样保留造型；放置时随面向旋转'
    writer.put(ASSET/'lang/zh_cn.json',(json.dumps(names,ensure_ascii=False,indent=2)+'\n').encode())
    writer.json(DATA/'dimension_type/manor_gallery.json',{'fixed_time':6000,'has_skylight':True,'has_ceiling':False,'ultrawarm':False,'natural':False,
        'coordinate_scale':1.0,'bed_works':False,'respawn_anchor_works':False,'piglin_safe':True,'has_raids':False,'min_y':-64,'height':384,'logical_height':384,
        'infiniburn':'#minecraft:infiniburn_overworld','effects':'minecraft:overworld','ambient_light':0.0,'monster_spawn_light_level':0,'monster_spawn_block_light_limit':0})
    writer.json(DATA/'dimension/manor_gallery.json',{'type':'myvillage:manor_gallery','generator':{'type':'minecraft:flat','settings':{
        'biome':'minecraft:the_void','lakes':False,'features':False,'layers':[{'height':1,'block':'minecraft:air'}],'structure_overrides':[]}}})
    writer.finish()
    if writer.bad:raise SystemExit('七府生成资源漂移：\n'+'\n'.join(writer.bad[:40]))
    print(json.dumps({'check':args.check,'files':writer.count,'materials':len(materials),'component_banks':len(banks),'manors':len(manors)},ensure_ascii=False))


if __name__=='__main__':main()
