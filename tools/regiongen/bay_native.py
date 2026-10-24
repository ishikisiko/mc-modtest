"""Cloud-only ordinary-world generation and restart evidence; no scripted player walk.

RCON performs locate, bounded chunk tickets and a persistence marker fixture.
No /place, /fill, structure API or imported settlement blocks are used.
"""
from __future__ import annotations
import argparse
from collections import Counter
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import re
import secrets
import struct
import time
import zipfile

from .assets import ROOT, rotated_state
from .native_compare import difference, decode_grid
from .build import write_json
from .bay_assets import DEFAULT_OUT
from tools.worldplan import acceptance as server_support
from yunshan.natural_terrain import read_region
from buildgen.nbtread import read_gzipped_nbt, state_string


def world_point(piece,x,y,z,size):
    for _ in range(piece['rotation']):x,z=size-1-z,x
    return piece['ox']+x,piece['oy']+y,piece['oz']+z


class SavedWorld:
    def __init__(self,world):self.world=Path(world);self.regions={};self.decoded={}
    def chunk(self,cx,cz):
        key=(cx//32,cz//32)
        if key not in self.regions:self.regions[key]=read_region(self.world/'region'/f'r.{key[0]}.{key[1]}.mca')[0]
        return self.regions[key].get((cx%32,cz%32))
    def block(self,x,y,z):
        cx,cz=x//16,z//16;key=(cx,y//16,cz)
        if key not in self.decoded:
            chunk=self.chunk(cx,cz)
            if not chunk or str(chunk.get('Status','')).split(':')[-1]!='full':raise ValueError(f'Not a saved FULL chunk: {cx},{cz}')
            section=next((s for s in chunk.get('sections',[]) if int(s['Y'])==y//16),{})
            self.decoded[key]=decode_grid(section)
        palette,grid=self.decoded[key]
        return palette[int(grid[y%16,z%16,x%16])]


def structure_piece(saved,cx,cz):
    chunk=saved.chunk(cx,cz)
    starts=chunk.get('structures',{}).get('starts',{}) if chunk else {}
    start=starts.get('myvillage:bay_terraces',{})
    children=start.get('Children',[])
    if start.get('id')!='myvillage:bay_terraces' or len(children)!=1:
        raise ValueError(f'No natural bay StructureStart at {cx},{cz}: {start}')
    piece=children[0]
    if piece.get('id')!='myvillage:bay_terraces' or not 0<=piece.get('rotation',-1)<4:
        raise ValueError('Invalid persisted bay piece')
    return piece


def audit(world,piece,manifest):
    saved=SavedWorld(world);n=manifest['size'];cfg=manifest['worldgen'];chunks=0
    for z in range(piece['oz']-cfg['margin'],piece['oz']+n+cfg['margin'],16):
        for x in range(piece['ox']-cfg['margin'],piece['ox']+n+cfg['margin'],16):
            chunk=saved.chunk(x//16,z//16)
            if not chunk or str(chunk.get('Status','')).split(':')[-1]!='full':raise ValueError(f'Incomplete footprint: {x//16},{z//16}')
            chunks+=1
    errors=[];counts=Counter();derived=Counter();cache={};expected={};signs=0
    for tile in manifest['tiles']:
        path=DEFAULT_OUT/('data/myvillage/structure/'+tile['template'].split(':',1)[1]+'.nbt')
        if hashlib.sha256(path.read_bytes()).hexdigest()!=tile['sha256']:raise ValueError('Audit tile hash differs')
        _,tag=read_gzipped_nbt(str(path));palette=[rotated_state(state_string(p),piece['rotation']) for p in tag['palette']]
        for block in tag['blocks']:
            x,y,z=block['pos'];at=world_point(piece,tile['x']+x,manifest['base_y']+y,tile['z']+z,n)
            expected[at]=palette[block['state']]
            if 'nbt' in block:
                entities=saved.chunk(at[0]//16,at[2]//16).get('block_entities',[])
                actual=next((b for b in entities if (b['x'],b['y'],b['z'])==at),None)
                if block['nbt'].get('id')=='minecraft:sign':
                    signs+=1
                    wanted=[json.loads(t) for t in block['nbt'].get('front_text',{}).get('messages',[])]
                    found=[json.loads(t) for t in actual.get('front_text',{}).get('messages',[])] if actual else None
                    if found!=wanted:
                        errors.append({'position':at,'reason':'sign_text'});counts['sign_text']+=1
    core=0;materials=manifest['materials'];inner=cfg['blend_width']-cfg['margin']
    for z in range(inner,n-inner):
        for x in range(inner,n-inner):
            h=manifest['heights'][z*n+x];kind=manifest['kinds'][z*n+x]
            at=world_point(piece,x,h-1,z,n)
            state=materials['bank' if h<=manifest['water_y']+1 else 'court' if kind==1 else 'road' if kind==2 else 'ground']
            expected.setdefault(at,state);core+=1
    for at,state in sorted(expected.items()):
        actual=saved.block(*at);pair=(state,actual)
        if pair not in cache:cache[pair]=difference(state,actual)
        why=cache[pair]
        if why:
            counts[why]+=1
            if len(errors)<60:errors.append({'position':at,'expected':state,'actual':actual,'reason':why})
        elif state!=actual:derived[state+' -> '+actual]+=1
    return {'passed':not counts,'chunks':chunks,'compared_cells':len(expected),'core_surface_columns':core,'signs':signs,
            'differences':dict(counts),'examples':errors,'default_or_derived_changes':dict(derived),
            'layout_hash':manifest['layout_hash'],'rotation':piece['rotation']}


def creative_handoff_metadata(path):
    """Patch only existing scalar NBT fields; preserve all other bytes and tag types."""
    raw=bytearray(gzip.decompress(path.read_bytes()));stream=io.BytesIO(raw);positions={}
    def read(size):
        data=stream.read(size)
        if len(data)!=size:raise ValueError('Truncated level.dat')
        return data
    def utf():
        length=struct.unpack('>H',read(2))[0];return read(length).decode('utf-8')
    widths={1:1,2:2,3:4,4:8,5:4,6:8}
    def skip(kind):
        if kind in widths:read(widths[kind])
        elif kind==8:read(struct.unpack('>H',read(2))[0])
        elif kind in (7,11,12):
            count=struct.unpack('>i',read(4))[0]
            if count<0:raise ValueError('Negative NBT array')
            read(count*{7:1,11:4,12:8}[kind])
        elif kind==9:
            child=read(1)[0];count=struct.unpack('>i',read(4))[0]
            if count<0:raise ValueError('Negative NBT list')
            for _ in range(count):skip(child)
        elif kind==10:compound(False)
        else:raise ValueError('Unsupported NBT kind '+str(kind))
    def compound(data):
        while True:
            kind=read(1)[0]
            if not kind:return
            name=utf()
            if data and name in ('allowCommands','GameType'):positions[name]=(kind,stream.tell())
            skip(kind)
    if read(1)!=b'\x0a':raise ValueError('Expected compound level.dat')
    utf()
    while True:
        kind=read(1)[0]
        if not kind:break
        name=utf()
        if name=='Data' and kind==10:compound(True)
        else:skip(kind)
    if set(positions)!= {'allowCommands','GameType'} or positions['allowCommands'][0]!=1 or positions['GameType'][0]!=3:
        raise ValueError('Missing typed world settings')
    changes={}
    for name,expected,value in [('allowCommands',1,b'\x01'),('GameType',3,struct.pack('>i',1))]:
        _,offset=positions[name];changes[name]={'before':raw[offset:offset+len(value)].hex(),'after':value.hex()}
        raw[offset:offset+len(value)]=value
    path.write_bytes(gzip.compress(bytes(raw),mtime=0))
    return changes


def wait_loaded(server,coordinates,timeout=420):
    deadline=time.monotonic()+timeout;pending=set(coordinates)
    while pending:
        for x,z in list(pending):
            if 'Test passed' in server.cmd(f'execute if loaded {x} 80 {z}'):pending.remove((x,z))
        if not pending:return
        if time.monotonic()>deadline:raise TimeoutError(f'Native generation incomplete: {len(pending)} chunks')
        print(json.dumps({'phase':'generating','remaining_chunks':len(pending)}),flush=True);time.sleep(2)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,default=ROOT/'reports/regiongen/bay-native')
    parser.add_argument('--seed',type=int,default=20261025);args=parser.parse_args()
    if not os.environ.get('MODAL_SANDBOX_ID') and not Path('/workspace/request.json').is_file():
        raise SystemExit('Heavy bay-world validation must run in the authorized Modal environment')
    out=args.out;out.mkdir(parents=True,exist_ok=False)
    server_support.OUT=out;server_support.SEED=args.seed
    manifest=json.loads((DEFAULT_OUT/'data/myvillage/regiongen/bay_terraces_v1.json').read_text())
    name='bay_terraces_'+str(args.seed)+'_'+secrets.token_hex(4);world=server_support.GAME/name
    if world.exists():raise ValueError('Fresh world path already exists')
    request=json.loads(Path('/workspace/request.json').read_text())
    report={'status':'running','source_commit':request['commit'],'seed':args.seed,'world':name,
            'scope':'ordinary-world native generation and saved-state evidence; no automated player walk',
            'selected':['jar_resources','natural_locate','partial_save','saved_structure','full_overlay_and_surface','restart_preserves_edit','creative_handoff'],
            'completed':[],'checks':{},'not_verified':['player_traversal','NPC_navigation','FPS','multi_seed_native_coverage']}
    def save():write_json(out/'report.json',report)
    def check(key,passed,details=None):
        report['checks'][key]={'pass':bool(passed),'details':details};save()
        if not passed:raise AssertionError(key+': '+str(details))
    try:
        jar=server_support.check_jar();report['jar']=jar
        with zipfile.ZipFile(ROOT/'build/libs'/jar['file']) as archive:
            packaged=json.loads(archive.read('data/myvillage/regiongen/bay_terraces_v1.json'))
            check('jar_resources',packaged==manifest and all('data/myvillage/structure/'+t['template'].split(':',1)[1]+'.nbt' in archive.namelist() for t in manifest['tiles']))
        report['completed'].append('jar_resources')
        with server_support.Server(name,'initial',planned=False) as server:
            reply=server.cmd('execute positioned 0 80 0 run locate structure myvillage:bay_terraces')
            match=re.search(r'\[(-?\d+),\s*(?:~|-?\d+),\s*(-?\d+)\]',reply)
            check('natural_locate',match is not None,reply);x,z=map(int,match.groups());cx,cz=x//16,z//16
            report['locate']={'command':'/locate structure myvillage:bay_terraces','reply':reply,'x':x,'z':z}
            report['completed'].append('natural_locate')
            server.cmd(f'forceload add {cx*16} {cz*16}');wait_loaded(server,[(cx*16,cz*16)])
            flush=server.cmd('save-all flush');check('initial_flush','Saved' in flush,flush)
            piece=structure_piece(SavedWorld(world),cx,cz);report['piece']=piece
            check('saved_structure',piece['layout_hash']==manifest['layout_hash'],piece)
            report['completed'].append('saved_structure')
            # An administrator fixture in the first loaded chunk tests preservation across restart and extension.
            marker=(cx*16+8,piece['oy']+manifest['base_y']+manifest['height']+2,cz*16+8)
            at=' '.join(map(str,marker));check('marker_was_air','Test passed' in server.cmd(f'execute if block {at} minecraft:air'))
            server.cmd(f'setblock {at} minecraft:gold_block');server.cmd(f'forceload remove {cx*16} {cz*16}')
            report['marker_fixture']=marker;report['completed'].append('partial_save')
        cleanup=json.loads((out/'initial-cleanup.json').read_text())
        check('initial_stop',cleanup['terminated'] and not cleanup['forced_stop'],cleanup)
        _,level=read_gzipped_nbt(str(world/'level.dat'));settings=level['Data']['WorldGenSettings']
        check('ordinary_world_seed',settings['seed']==args.seed and settings['dimensions']['minecraft:overworld']['generator']['type']=='minecraft:noise',{'seed':settings['seed'],'generator':settings['dimensions']['minecraft:overworld']['generator']['type']})
        n=manifest['size'];margin=manifest['worldgen']['margin'];bounds=(piece['ox']-margin,piece['oz']-margin,piece['ox']+n+margin-1,piece['oz']+n+margin-1)
        coords=[(x,z) for z in range(bounds[1],bounds[3]+1,16) for x in range(bounds[0],bounds[2]+1,16)]
        if len(coords)>256:raise ValueError('Too many owned generation tickets')
        with server_support.Server(name,'restart',planned=False) as server:
            server.cmd('forceload add '+' '.join(map(str,bounds)));wait_loaded(server,coords)
            at=' '.join(map(str,marker));check('restart_preserves_edit','Test passed' in server.cmd(f'execute if block {at} minecraft:gold_block'))
            server.cmd(f'setblock {at} minecraft:air');report['completed'].append('restart_preserves_edit')
            entry=manifest['entry'];spawn=world_point(piece,entry['x'],entry['y'],entry['z'],n);sx,sy,sz=spawn
            check('handoff_spawn_clear','Test passed' in server.cmd(f'execute if block {sx} {sy} {sz} minecraft:air if block {sx} {sy+1} {sz} minecraft:air unless block {sx} {sy-1} {sz} minecraft:air'))
            server.cmd(f'setworldspawn {sx} {sy} {sz} {(180+piece["rotation"]*90)%360}')
            server.cmd('gamerule spawnRadius 0');server.cmd('defaultgamemode creative')
            report['entry']=spawn;server.cmd('forceload remove '+' '.join(map(str,bounds)))
            report['ticket_release']=server.cmd('forceload query')
        cleanup=json.loads((out/'restart-cleanup.json').read_text())
        check('restart_stop',cleanup['terminated'] and not cleanup['forced_stop'],cleanup)
        after=structure_piece(SavedWorld(world),cx,cz);check('structure_identity_survived',after==piece)
        result=audit(world,piece,manifest);write_json(out/'saved-world-comparison.json',result)
        check('full_overlay_and_surface',result['passed'],{k:v for k,v in result.items() if k not in ('default_or_derived_changes','examples')})
        report['completed'].append('full_overlay_and_surface')
        report['handoff_metadata_changes']=creative_handoff_metadata(world/'level.dat')
        _,level=read_gzipped_nbt(str(world/'level.dat'))
        check('creative_handoff',level['Data']['allowCommands']==1 and level['Data']['GameType']==1)
        report['completed'].append('creative_handoff')
        archive=out/'saved-world.zip'
        with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as bundle:
            for path in sorted(world.rglob('*')):
                if path.is_file() and path.name not in ('session.lock','level.dat_old'):bundle.write(path,str(path.relative_to(world)))
        report['saved_world']={'file':archive.name,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'bytes':archive.stat().st_size}
        report['status']='passed'
    except BaseException as error:
        report['status']='failed';report['error']=repr(error);raise
    finally:
        report['unrun']=[x for x in report['selected'] if x not in report['completed']];save()
        print(json.dumps({k:v for k,v in report.items() if k!='checks'},ensure_ascii=False),flush=True)


if __name__=='__main__':main()
