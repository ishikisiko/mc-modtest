#!/usr/bin/env python3
"""Read flushed 1.21.1 Anvil terrain and rank distinct natural city sites.

All column arrays use z-major/x-fastest ordering. Heights are feet/first-air
levels, never top-block coordinates. This module neither starts Minecraft nor
writes worlds; absent or incomplete chunks are errors, never invented terrain.
"""
from __future__ import annotations

import argparse
from collections import Counter
import gzip
import hashlib
import io
import json
import math
from pathlib import Path
import statistics
import struct
import sys
import zlib

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from buildgen import nbtread


def read_settings(path=None):
    return json.loads(Path(path or ROOT/'blueprints/yunshan/natural_sites.json').read_text())


def coarse_windows(settings=None):
    return (settings or read_settings())['search_windows']


def unpack_padded(data,index,bits):
    """Modern Anvil palettes/heightmaps never span one value over two longs."""
    per_long=64//bits
    if index//per_long>=len(data):
        raise ValueError('truncated packed block/heightmap long array')
    value=data[index//per_long]&((1<<64)-1)
    return (value>>((index%per_long)*bits))&((1<<bits)-1)


def decode_section(section):
    states=section.get('block_states')
    if states is None:
        return None
    palette=states.get('palette')
    if not isinstance(palette,list) or not palette:
        raise ValueError('section has no block-state palette')
    palette=[nbtread.state_string(entry) for entry in palette]
    data=states.get('data')
    bits=max(4,(len(palette)-1).bit_length())
    if len(palette)>1 and (not data or len(data)<math.ceil(4096/(64//bits))):
        raise ValueError('nonuniform section has missing/truncated palette data')
    return palette,data,bits


def section_state(decoded,x,y,z):
    if decoded is None:
        return 'minecraft:air'
    palette,data,bits=decoded
    index=0 if len(palette)==1 else unpack_padded(data,(y*16+z)*16+x,bits)
    if index>=len(palette):
        raise ValueError('packed block palette index is out of bounds')
    return palette[index]


def read_region(path):
    """Read all present chunks with strict offsets, compression and NBT checks."""
    raw=Path(path).read_bytes()
    if len(raw)<8192:
        raise ValueError(f'truncated Anvil header: {path}')
    chunks={};occupied=set()
    for index in range(1024):
        location=int.from_bytes(raw[index*4:index*4+4],'big')
        offset,count=location>>8,location&255
        if not offset and not count:
            continue
        start=offset*4096
        if offset<2 or not count or start+5>len(raw):
            raise ValueError(f'invalid Anvil sector offset: {path}, chunk {index}')
        allocated_end=start+count*4096
        # Region snapshots may end immediately after the final valid payload,
        # without zero-padding its last sector. Missing complete sectors or
        # overlapping allocations are still invalid header offsets.
        if allocated_end>len(raw) and allocated_end!=math.ceil(len(raw)/4096)*4096:
            raise ValueError(f'invalid Anvil sector allocation: {path}, chunk {index}')
        allocation=set(range(offset,offset+count))
        if allocation&occupied:
            raise ValueError(f'overlapping Anvil sector allocation: {path}, chunk {index}')
        occupied.update(allocation)
        length=struct.unpack('>I',raw[start:start+4])[0]
        if length<1 or length+4>count*4096:
            raise ValueError(f'invalid Anvil chunk length: {path}, chunk {index}')
        if start+4+length>len(raw):
            raise ValueError(f'truncated Anvil chunk payload: {path}, chunk {index}')
        scheme=raw[start+4]
        if scheme&128:
            raise ValueError('external .mcc chunks are unsupported; explicit snapshot required')
        payload=raw[start+5:start+4+length]
        if scheme==2:payload=zlib.decompress(payload)
        elif scheme==1:payload=gzip.decompress(payload)
        elif scheme!=3:raise ValueError(f'unsupported Anvil compression {scheme}')
        f=io.BytesIO(payload)
        if f.read(1)!=bytes([nbtread.TAG_COMPOUND]):
            raise ValueError('Anvil chunk is not a root NBT compound')
        nbtread._read_utf(f)
        chunks[index%32,index//32]=nbtread._read_payload(f,nbtread.TAG_COMPOUND)
        if f.read(1):raise ValueError('trailing chunk NBT data')
    return chunks,hashlib.sha256(raw).hexdigest()


def sample_chunk(chunk,settings=None):
    settings=settings or read_settings()
    if str(chunk.get('Status','')).split(':')[-1]!='full':
        raise ValueError(f'chunk is not FULL: {chunk.get("Status")}')
    sections={int(s['Y']):decode_section(s) for s in chunk.get('sections',[])}
    minimum=settings['world_min_y'];maximum=minimum+settings['world_height']-1
    natural={'minecraft:'+name for name in settings['natural_ground_blocks']}
    artificial=set(settings['artificial_blocks'])
    suffixes=tuple(settings['artificial_block_suffixes'])
    air={'minecraft:air','minecraft:cave_air','minecraft:void_air'}
    def state(x,y,z):
        return section_state(sections.get(y//16),x,y%16,z)
    heights=chunk.get('Heightmaps',{}).get('WORLD_SURFACE')
    height_bits=settings['world_height'].bit_length()
    columns=[]
    for z in range(16):
        for x in range(16):
            # Heightmaps are a starting bound, verified against actual blocks.
            hint=(unpack_padded(heights,z*16+x,height_bits)+minimum) if heights else maximum+1
            if not minimum<=hint<=maximum+1:raise ValueError('WORLD_SURFACE heightmap out of range')
            top=hint-1
            while top<maximum and state(x,top+1,z).split('[',1)[0] not in air:top+=1
            while top>=minimum and state(x,top,z).split('[',1)[0] in air:top-=1
            ground=None;ground_state=None;water=None;built=False
            for y in range(top,minimum-1,-1):
                current=state(x,y,z);name=current.split('[',1)[0]
                if water is None and name=='minecraft:water':water=y+1
                if name in artificial or name.endswith(suffixes):built=True
                if name in natural or name.endswith('_ore'):
                    ground,ground_state=y+1,current
                    break
            if ground is None:raise ValueError(f'no natural solid ground in FULL chunk column {x},{z}')
            columns.append({'ground_y':ground,'surface_y':top+1,'water_y':water,
                            'ground_state':ground_state,'artificial':built})
    return columns,bool(heights)


def sample_window(world_dir,window,seed,settings=None):
    settings=settings or read_settings();world_dir=Path(world_dir)
    x0,z0,x1,z1=map(int,window)
    if x1<=x0 or z1<=z0:raise ValueError('survey window must have exclusive increasing bounds')
    level_path=world_dir/'level.dat'
    _,level=nbtread.read_gzipped_nbt(str(level_path))
    worldgen=level['Data']['WorldGenSettings']
    if int(worldgen['seed'])!=seed:raise ValueError('survey seed differs from actual level.dat')
    generator=worldgen.get('dimensions',{}).get('minecraft:overworld',{}).get('generator',{}).get('type')
    if generator=='minecraft:flat':raise ValueError('natural-site sampling refuses a flat world')
    if generator is None:raise ValueError('actual overworld generator type is missing')
    width,depth=x1-x0,z1-z0
    result={'format':1,'window':[x0,z0,x1,z1],'width':width,'depth':depth,
            'ground_y':[None]*(width*depth),'surface_y':[None]*(width*depth),
            'water_y':[None]*(width*depth),'ground_index':[None]*(width*depth),
            'artificial':[False]*(width*depth),'ground_palette':[]}
    palette={};regions={};hashes={};heightmapped=0;chunks_count=0;structures=[];versions=set()
    for cz in range(z0//16,(z1-1)//16+1):
        for cx in range(x0//16,(x1-1)//16+1):
            region=(cx//32,cz//32)
            if region not in regions:
                path=world_dir/'region'/f'r.{region[0]}.{region[1]}.mca'
                if not path.is_file():raise ValueError(f'missing survey region: {path.name}')
                regions[region],hashes[path.name]=read_region(path)
            chunk=regions[region].get((cx%32,cz%32))
            if chunk is None:raise ValueError(f'missing survey chunk: {cx},{cz}; flush/generate the entire requested window')
            if chunk.get('xPos')!=cx or chunk.get('zPos')!=cz:raise ValueError('Anvil chunk coordinate mismatch')
            columns,uses_heightmap=sample_chunk(chunk,settings)
            heightmapped+=uses_heightmap;chunks_count+=1
            if 'DataVersion' in chunk:versions.add(chunk['DataVersion'])
            for key,start in chunk.get('structures',{}).get('starts',{}).items():
                if start and start.get('id')!='INVALID':structures.append({'chunk':[cx,cz],'id':key})
            for z in range(max(z0,cz*16),min(z1,(cz+1)*16)):
                for x in range(max(x0,cx*16),min(x1,(cx+1)*16)):
                    column=columns[(z%16)*16+x%16];index=(z-z0)*width+x-x0
                    for key in ('ground_y','surface_y','water_y','artificial'):result[key][index]=column[key]
                    state=column['ground_state']
                    if state not in palette:
                        palette[state]=len(palette);result['ground_palette'].append(state)
                    result['ground_index'][index]=palette[state]
    result['provenance']={'seed':seed,'status':'saved_anvil_snapshot','overworld_generator':generator,
        'level_dat_sha256':hashlib.sha256(level_path.read_bytes()).hexdigest(),'region_sha256':hashes,
        'chunk_count':chunks_count,'heightmap_chunks':heightmapped,'data_versions':sorted(versions),
        'height_source':'WORLD_SURFACE hint verified against packed block states; natural ground scanned below vegetation/water',
        'structure_starts':structures,'sampling':'offline_saved_actual_blocks',
        'artificial_detection':'conservative above-ground block palette heuristic; natural cobblestone boulders may be flagged',
        'flush_verified_by_sampler':False,'flush_requirement':'caller must save-all flush then snapshot before sampling; the sampler cannot verify a running server flush'}
    return result


def source_core(settings=None):
    settings=settings or read_settings()
    from yunshan.fit_natural import config_data,source_data,source_core as semantic_core
    fit_cfg=config_data()
    result=source_data(str(ROOT/'blueprints/yunshan'/fit_cfg['source_config']))
    cfg,_,site,_,volume,levels,kinds=result[:7]
    ox,oy,oz=site['world_offset'];w,_,d=volume.size
    ground=[];wet=[];weights=[];protected=[];outlets=[]
    protected_columns,wet_columns=semantic_core(result,fit_cfg)
    for z in range(volume.minimum[2],volume.maximum[2]+1):
        for x in range(volume.minimum[0],volume.maximum[0]+1):
            lake=(x,z) in wet_columns
            ground.append((cfg['lake_bed_level'] if lake else levels[x-ox,z-oz])+oy)
            wet.append(lake)
            core=(x,z) in protected_columns
            protected.append(core)
            weights.append(settings['column_weights']['protected_core' if core else 'lake' if lake else 'peripheral_art'])
            outlets.append(lake and min(x-volume.minimum[0],volume.maximum[0]-x,z-volume.minimum[2],volume.maximum[2]-z)<settings['outlet_band'])
    return {'minimum':volume.minimum,'size':volume.size,'ground_y':ground,'water_mask':wet,
            'column_weights':weights,'core_mask':protected,'outlet_mask':outlets,
            'water_surface':settings['source_water_surface']}


def rotate_column(x,z,width,depth,q):
    q%=4
    if q==0:return x,z
    if q==1:return depth-1-z,x
    if q==2:return width-1-x,depth-1-z
    return z,width-1-x


def rank_sites(surveys,core=None,settings=None):
    settings=settings or read_settings();core=core or source_core(settings)
    width,_,depth=core['size'];stride=settings['sample_stride'];ranked=[]
    core_water=core.get('water_mask') or [False]*(width*depth)
    column_weights=core.get('column_weights') or [1.0]*(width*depth)
    outlet_mask=core.get('outlet_mask') or [False]*(width*depth)
    samples=[(x,z,core['ground_y'][z*width+x],core_water[z*width+x],column_weights[z*width+x],outlet_mask[z*width+x])
             for z in range(0,depth,stride) for x in range(0,width,stride)]
    for si,survey in enumerate(surveys):
        x0,z0,x1,z1=survey['window'];sw=survey['width']
        artificial=survey.get('artificial') or [False]*len(survey['ground_y'])
        for q in range(4):
            rw,rd=(depth,width) if q%2 else (width,depth)
            transformed=[(*rotate_column(x,z,width,depth,q),y,wet,weight,outlet) for x,z,y,wet,weight,outlet in samples]
            for oz in range(z0,z1-rd+1,settings['origin_stride']):
                for ox in range(x0,x1-rw+1,settings['origin_stride']):
                    indices=[(oz+z-z0)*sw+ox+x-x0 for x,z,*_ in transformed]
                    water=[survey['water_y'][i] for i in indices if survey['water_y'][i] is not None]
                    water_surface=Counter(water).most_common(1)[0][0] if water else settings['water_surface_fallback']
                    dy=water_surface-core['water_surface']
                    deltas=[y+dy-survey['ground_y'][i] for (_,_,y,*_),i in zip(transformed,indices)]
                    absolute=sorted((abs(v),sample[4]) for v,sample in zip(deltas,transformed))
                    total_weight=sum(sample[4] for sample in transformed)
                    cumulative=0;p90=absolute[-1][0]
                    for error,weight in absolute:
                        cumulative+=weight
                        if cumulative>=total_weight*.9:p90=error;break
                    roughness=[]
                    for i in indices:
                        # Local roughness uses neighboring sampled original columns.
                        if (i%sw)+stride<sw:roughness.append(abs(survey['ground_y'][i+stride]-survey['ground_y'][i]))
                        if i+stride*sw<len(survey['ground_y']):roughness.append(abs(survey['ground_y'][i+stride*sw]-survey['ground_y'][i]))
                    n=len(indices)
                    slope_error=0;slope_weight=0
                    for (x,z,target,_,weight,_),index in zip(samples,indices):
                        for dx,dz in ((stride,0),(0,stride)):
                            if x+dx>=width or z+dz>=depth:continue
                            tx,tz=rotate_column(x+dx,z+dz,width,depth,q)
                            ni=(oz+tz-z0)*sw+ox+tx-x0
                            neighbor=(z+dz)*width+x+dx
                            importance=min(weight,column_weights[neighbor])
                            slope_error+=importance*abs((survey['ground_y'][ni]-survey['ground_y'][index])-(core['ground_y'][neighbor]-target))/stride
                            slope_weight+=importance
                    outlet_indices=[i for sample,i in zip(transformed,indices) if sample[5]]
                    metrics={'mean_earthwork':sum(abs(v)*sample[4] for v,sample in zip(deltas,transformed))/total_weight,'p90_earthwork':p90,
                        'mean_cut':sum(max(0,-v)*sample[4] for v,sample in zip(deltas,transformed))/total_weight,
                        'mean_fill':sum(max(0,v)*sample[4] for v,sample in zip(deltas,transformed))/total_weight,
                        'roughness':statistics.mean(roughness) if roughness else 0,
                        'slope_mismatch':slope_error/slope_weight if slope_weight else 0,
                        'water_mismatch':sum((bool(survey['water_y'][i] is not None)!=sample[3])*sample[4] for sample,i in zip(transformed,indices))/total_weight,
                        'outlet_mismatch':sum(survey['water_y'][i] is None or abs(survey['water_y'][i]-water_surface)>settings['water_surface_tolerance'] for i in outlet_indices)/len(outlet_indices) if outlet_indices else 0,
                        'water_fraction':len(water)/n,
                        'deep_water_fraction':sum(survey['water_y'][i] is not None and survey['water_y'][i]-survey['ground_y'][i]>settings['gates']['deep_water_depth'] for i in indices)/n,
                        'artificial_fraction':sum(artificial[i] for i in indices)/n,
                        'sample_columns':n,'effective_column_weight':total_weight,'outlet_sample_columns':len(outlet_indices),
                        'height_min':min(survey['ground_y'][i] for i in indices),'height_max':max(survey['ground_y'][i] for i in indices)}
                    reasons=[name for name in ('mean_earthwork','roughness','deep_water_fraction','artificial_fraction')
                             if metrics[name]>settings['gates']['max_'+name]]
                    score=sum(metrics[k]*weight for k,weight in settings['weights'].items())
                    ranked.append({'id':f'site-{si}-q{q}-{ox}-{oz}','origin':[ox,oz],'rotation_quarters':q,
                        'water_surface':water_surface,'dy':dy,'score':round(score,4),'metrics':metrics,
                        'eligible':not reasons,'limitations':reasons,'survey_index':si,'size':[rw,rd]})
    ranked.sort(key=lambda c:(not c['eligible'],c['score'],c['id']))
    selected=[]
    for candidate in ranked:
        cx,cz=[candidate['origin'][i]+candidate['size'][i]/2 for i in range(2)]
        if any(math.hypot(cx-(s['origin'][0]+s['size'][0]/2),cz-(s['origin'][1]+s['size'][1]/2))<settings['minimum_site_separation'] for s in selected):continue
        candidate['rank']=len(selected)+1;selected.append(candidate)
        if len(selected)==settings['candidate_count']:break
    if len(selected)<settings['candidate_count']:
        raise ValueError(f'only {len(selected)} spatially distinct candidates; expand real survey coverage')
    return selected


def expanded_window(candidate,settings=None):
    settings=settings or read_settings();q=candidate['rotation_quarters']
    width,depth=settings['expanded_size']
    if q%2:width,depth=depth,width
    rw,rd=candidate.get('size',([265,210] if q%2 else [210,265]))
    if min((width-rw)//2,(depth-rd)//2)<settings['minimum_margin']:
        raise ValueError('expanded survey has insufficient fusion margin')
    x=candidate['origin'][0]-(width-rw)//2;z=candidate['origin'][1]-(depth-rd)//2
    return [x,z,x+width,z+depth]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world',type=Path,required=True)
    parser.add_argument('--seed',type=int,default=20261002)
    parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--settings',type=Path)
    parser.add_argument('--window',type=int,nargs=4)
    args=parser.parse_args();settings=read_settings(args.settings);args.out.mkdir(parents=True,exist_ok=True)
    windows=[{'id':'window','window':args.window}] if args.window else coarse_windows(settings)
    surveys=[]
    for item in windows:
        result=sample_window(args.world,item['window'],args.seed,settings)
        (args.out/(item['id']+'.json')).write_text(json.dumps(result,separators=(',',':'))+'\n')
        surveys.append(result)
    if not args.window:
        candidates=rank_sites(surveys,settings=settings)
        (args.out/'top3.json').write_text(json.dumps(candidates,indent=2)+'\n')
        print(json.dumps(candidates,indent=2))


if __name__=='__main__':main()
