"""Compare every saved sample block with its authored volume after native play.

Vanilla defaults absent from the authored state are accepted. Derived connection
and stair-shape changes are counted separately; block identity, facing, half,
waterlogging, slabs and all other authored properties remain strict. Only doors
recorded as opened by the real input collector may differ in their open state.
"""
from __future__ import annotations
import argparse
from array import array
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import sys
import numpy as np

from .assets import canonical
from yunshan.natural_terrain import read_region, decode_section
from json_to_nbt import parse_block_state

DERIVED={'north','east','south','west','up','shape','distance'}


def difference(expected,actual,opened=False):
    name,props=parse_block_state(expected);actual_name,actual_props=parse_block_state(actual)
    if name!=actual_name:return 'block_id'
    if name.endswith('_door'):props={'open':'false','powered':'false',**props}
    for key,value in props.items():
        if key in DERIVED:continue
        if key=='open' and opened and actual_props.get(key)=='true':continue
        if actual_props.get(key)!=value:return 'property:'+key
    return None


def decode_grid(section):
    decoded=decode_section(section)
    if decoded is None:return ['minecraft:air'],np.zeros((16,16,16),dtype=np.uint16)
    palette,data,bits=decoded
    if len(palette)==1:return palette,np.zeros((16,16,16),dtype=np.uint16)
    packed=np.array([v&((1<<64)-1) for v in data],dtype=np.uint64)
    indices=np.arange(4096,dtype=np.uint64);per=64//bits
    values=(packed[indices//per] >> ((indices%per)*bits)) & ((1<<bits)-1)
    if np.any(values>=len(palette)):raise ValueError('Saved palette index exceeds palette')
    return palette,values.astype(np.uint16).reshape(16,16,16)


def compare(pack,world,run):
    m=json.loads((pack/'manifest.json').read_text());x0,y0,z0=m['minimum'];w,h,d=m['size']
    raw=gzip.decompress((pack/'expected-cells.bin.gz').read_bytes())
    expected=np.frombuffer(raw,dtype='<u2').reshape(h,d,w)
    if len(raw)!=w*h*d*2:raise ValueError('Expected volume is truncated')
    ep=m['palette'];regions={};hashes={};errors=[];counts=Counter();derived=Counter();total=0;chunks_count=0
    opened={tuple(p) for p in run.get('opened_doors',[])}
    opened|={(p[0],p[1]+1,p[2]) for p in list(opened)}
    cached={};signs={}
    for cz in range(z0//16,(z0+d-1)//16+1):
        for cx in range(x0//16,(x0+w-1)//16+1):
            key=(cx//32,cz//32)
            if key not in regions:
                path=world/'region'/f'r.{key[0]}.{key[1]}.mca';regions[key],hashes[path.name]=read_region(path)
            chunk=regions[key].get((cx%32,cz%32))
            if chunk is None or str(chunk.get('Status','')).split(':')[-1]!='full':raise ValueError(f'Missing FULL saved chunk {cx},{cz}')
            if (chunk.get('xPos'),chunk.get('zPos'))!=(cx,cz):raise ValueError('Saved chunk coordinate mismatch')
            for b in chunk.get('block_entities',[]):
                if b.get('id') in ('minecraft:sign','minecraft:hanging_sign'):signs[(b['x'],b['y'],b['z'])]=b
            chunks_count+=1;sections={int(s['Y']):s for s in chunk.get('sections',[])}
            for sy in range(y0//16,(y0+h-1)//16+1):
                ap,ag=decode_grid(sections.get(sy,{}));lo=max(y0,sy*16);hi=min(y0+h,(sy+1)*16)
                # This sample starts on chunk boundaries and fills complete horizontal chunks.
                eg=expected[lo-y0:hi-y0,cz*16-z0:cz*16-z0+16,cx*16-x0:cx*16-x0+16]
                ag=ag[lo-sy*16:hi-sy*16];total+=eg.size
                pairs=np.unique(np.stack((eg.flatten(),ag.flatten()),axis=1),axis=0)
                for ei,ai in pairs:
                    e,a=ep[ei],ap[ai];pair=(e,a)
                    if pair not in cached:cached[pair]=difference(e,a)
                    reason=cached[pair];mask=(eg==ei)&(ag==ai);n=int(mask.sum())
                    if reason:
                        for yy,zz,xx in np.argwhere(mask):
                            p=(cx*16+int(xx),lo+int(yy),cz*16+int(zz))
                            why=difference(e,a,p in opened)
                            if why:
                                counts[why]+=1
                                if len(errors)<80:errors.append({'pos':p,'expected':e,'actual':a,'reason':why})
                    elif canonical(e)!=canonical(a):derived[e+' -> '+a]+=n
    sign_errors=[]
    for s in m['signs']:
        b=signs.get(tuple(s['pos']))
        if not b:sign_errors.append({'pos':s['pos'],'reason':'missing_sign'});continue
        texts=[]
        for t in b.get('front_text',{}).get('messages',[]):
            item=json.loads(t);texts.append(item if isinstance(item,str) else item.get('text',''))
        if texts!=s['messages']:sign_errors.append({'pos':s['pos'],'expected':s['messages'],'actual':texts})
    result={'passed':not counts and not sign_errors and total==w*h*d,'cells':total,'chunks':chunks_count,
        'differences':dict(counts),'examples':errors,'sign_errors':sign_errors,'signs_checked':len(m['signs']),
        'default_or_derived_state_changes':dict(derived),'region_sha256':hashes,
        'opened_door_cells':len(opened),'plan_sha256':m['plan_sha256'],'geometry_sha256':m['geometry_sha256']}
    return result


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--pack',type=Path,required=True);p.add_argument('--world',type=Path,required=True);p.add_argument('--run',type=Path,required=True);a=p.parse_args()
    report=json.loads((a.run/'report.json').read_text());result=compare(a.pack,a.world,report)
    (a.run/'saved-world-comparison.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
    report['saved_world_comparison_passed']=result['passed']
    report['attempted'].append('saved_world_comparison');report['unrun'].remove('saved_world_comparison')
    if not result['passed']:report['failed'].append('saved_world_comparison')
    if result['passed']:
        report['completed'].append('saved_world_comparison')
    if report['status']=='tour_passed_pending_saved_world':
        cleanup=report.get('cleanup',{})
        report['status']='passed' if result['passed'] and not cleanup.get('alive',['unknown']) and cleanup.get('lock_free') and report.get('video_exit')==0 and 'Saved' in report.get('flush_response','') else 'failed'
    (a.run/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print(json.dumps({k:v for k,v in result.items() if k not in ('default_or_derived_state_changes','examples')},ensure_ascii=False))
    if not result['passed']:sys.exit(1)

if __name__=='__main__':main()
