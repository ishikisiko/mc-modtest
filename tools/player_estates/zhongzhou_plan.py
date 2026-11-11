#!/usr/bin/env python3
"""依据中州真实建筑和一格地形生成庄园选址草案；不修改正式世界资源。"""
from __future__ import annotations

import argparse
import base64
from collections import defaultdict
import gzip
import hashlib
from html import escape
from io import BytesIO
import json
import math
from pathlib import Path
import re
import subprocess
import sys

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
sys.path.insert(0, str(ROOT))
from province_capitals.blueprint_plan.central import geometry, source_surfaces

SOURCE = ROOT / 'blueprints/player_estates/zhongzhou_placement.json'
HERE = Path(__file__).resolve().parent


def read(path):
    return json.loads((ROOT / path).read_text())


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def intersects(a, b):
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def row_box(row):
    a, b = row['bounds']['min'], row['bounds']['max']
    return [a[0], a[2], b[0] + 1, b[2] + 1]


def feature_mask(shape, f, offset, clearance=0):
    """一格采样的源特征投影；道路按完整宽度保护。"""
    D, W = shape
    ox, _, oz = offset
    kind = f['t']
    if kind == 0:
        bounds = [f['x0']-ox-clearance, f['z0']-oz-clearance,
                  f['x1']-ox+clearance, f['z1']-oz+clearance]
    elif kind == 1:
        cx, cz = f['cx']-ox, f['cz']-oz
        rx, rz = f['rx']+clearance, f['rz']+clearance
        bounds = [cx-rx, cz-rz, cx+rx, cz+rz]
    else:
        ax, az, bx, bz = f['ax']-ox, f['az']-oz, f['bx']-ox, f['bz']-oz
        radius = f['hw'] + clearance
        bounds = [min(ax,bx)-radius, min(az,bz)-radius, max(ax,bx)+radius, max(az,bz)+radius]
    a,b,e,h = bounds
    a,b,e,h = max(0,math.floor(a)),max(0,math.floor(b)),min(W,math.ceil(e)+1),min(D,math.ceil(h)+1)
    if a >= e or b >= h:
        return (slice(0,0),slice(0,0)), np.zeros((0,0), dtype=bool)
    zz, xx = np.mgrid[b:h,a:e]
    if kind == 0:
        hit = (xx+.5 >= bounds[0]) & (xx+.5 < bounds[2]) & (zz+.5 >= bounds[1]) & (zz+.5 < bounds[3])
    elif kind == 1:
        hit = ((xx+.5-cx)/rx)**2 + ((zz+.5-cz)/rz)**2 <= 1
    else:
        t = np.clip(((xx+.5-ax)*(bx-ax)+(zz+.5-az)*(bz-az))/max((bx-ax)**2+(bz-az)**2,1e-8),0,1)
        hit = (xx+.5-ax-t*(bx-ax))**2 + (zz+.5-az-t*(bz-az))**2 <= radius**2
    return (slice(b,h),slice(a,e)), hit


def paint(mask, f, offset, clearance=0):
    area, hit = feature_mask(mask.shape, f, offset, clearance)
    mask[area] |= hit


def protect_box(mask, bounds, clearance=0):
    a,b,e,f = bounds
    mask[max(0,math.floor(b-clearance)):min(mask.shape[0],math.ceil(f+clearance)),
         max(0,math.floor(a-clearance)):min(mask.shape[1],math.ceil(e+clearance))] = True


def transform(x,z,candidate,estate):
    a,b,_,_ = candidate['estate_bounds']
    w,_,d = estate['size']
    q = candidate['rotation_quarters']
    xx,zz = ((x,z),(d-z,x),(w-x,d-z),(z,w-x))[q]
    return [round(a+xx,3),round(b+zz,3)]


def affected_lanes(features, candidate, offset, heights, width):
    a,b,e,f = candidate['plot']; ox,_,oz=offset
    grouped=defaultdict(list)
    for feature in features:
        if feature.get('kind')==3 and feature.get('pid'):
            grouped[feature['pid']].append(feature)
    result=[]
    for pid,segments in grouped.items():
        points=[]
        for segment in segments:
            ax,az,bx,bz=segment['ax']-ox,segment['az']-oz,segment['bx']-ox,segment['bz']-oz
            count=max(1,math.ceil(math.hypot(bx-ax,bz-az)))
            points.extend([[ax+(bx-ax)*i/count,az+(bz-az)*i/count]for i in range(count)])
        points.append([segments[-1]['bx']-ox,segments[-1]['bz']-oz])
        hits=[i for i,(x,z) in enumerate(points) if a<=x<e and b<=z<f]
        if not hits:continue
        # A lane that originally ended among the retired houses must not retain
        # its old internal end as an anchor inside the new main building.
        endpoints=[]
        if hits[0]>0:endpoints.append(points[hits[0]-1])
        if hits[-1]<len(points)-1:endpoints.append(points[hits[-1]+1])
        spurs=[]
        inset=width/2
        for x,z in endpoints:
            options=[([min(e-inset,max(a+inset,x)),b+inset],abs(z-b)),
                     ([e-inset,min(f-inset,max(b+inset,z))],abs(x-e)),
                     ([min(e-inset,max(a+inset,x)),f-inset],abs(z-f)),
                     ([a+inset,min(f-inset,max(b+inset,z))],abs(x-a))]
            join,_=min(options,key=lambda item:item[1]);spurs.append([[x,z],join])
        # Coordinates and existing end levels are evidence; intermediate levels
        # are deliberately left for the terraced architectural design.
        def feet(p):
            x,z=map(round,p);return int(heights[z,x])+1
        result.append({'source_path_id':f'source_path_{pid}','width':width,
                       'existing_xz':[[round(x,2),round(z,2)]for x,z in points],
                       'replaced_xz':[[round(x,2),round(z,2)]for x,z in points[hits[0]:hits[-1]+1]],
                       'connection_spurs_xz':[[[round(x,2),round(z,2)]for x,z in spur]for spur in spurs],
                       'endpoint_feet_y':[feet(p)for p in endpoints],
                       'old_internal_dead_end_removed':len(endpoints)<2,'vertical_status':'needs_design'})
    return result


def evaluate(cfg, estate, city, bp, g, heights, water, candidate):
    c=dict(candidate);a,b,e,f=c['plot'];inset=cfg['rules']['plot_inset']
    c['estate_bounds']=[a+inset,b+inset,e-inset,f-inset]
    ew,_,ed=estate['size'];requested=(ew,ed)if c['rotation_quarters']%2==0 else(ed,ew)
    assert(e-a-2*inset,f-b-2*inset)==requested,'庄园占地不能缩放或裁切'
    assert 0<=a<e<=heights.shape[1] and 0<=b<f<=heights.shape[0]
    retired={r['id']for r in g.rows if intersects(row_box(r),c['plot'])}
    # Private courtyard ownership follows its actual dwelling, including plots
    # whose yard reaches the proposed boundary before the house does.
    private=[]
    for garden in bp['gardens']:
        binding=bp['garden_bindings'][garden['id']]
        bounds=garden['bounds'];bounds=[bounds[0],bounds[1],bounds[2]+1,bounds[3]+1]
        if binding['access']=='private' and intersects(bounds,c['plot']):
            retired.add(binding['owner']);private.append(garden['id'])
    rows={r['id']:r for r in g.rows}
    invalid=[r for r in retired if r not in rows or rows[r]['source_kind']not in cfg['rules']['retire_roles']]
    if invalid:raise ValueError(f'候选侵入保留功能建筑：{invalid}')
    c['retired_buildings']=[{'id':r,'template':rows[r]['source_template'],'kind':rows[r]['source_kind'],
                            'bounds':rows[r]['bounds'],'entry':rows[r]['entry']}for r in sorted(retired,key=lambda v:int(v.split('_')[1]))]
    c['private_gardens_retired']=private
    terrain=heights[b:f,a:e].astype(int)
    low,high=int(terrain.min()),int(terrain.max())
    ia,ib,ie,if_=c['estate_bounds']
    estate_ground=heights[ib:if_,ia:ie]
    estate_low,estate_high=int(estate_ground.min()),int(estate_ground.max())
    best=(estate_low+estate_high)//2
    limit=bp['rules']['local_earthwork_limit']
    c['terrain']={'min_ground_y':low,'max_ground_y':high,'relief':high-low,
                  'estate_ground_range':[estate_low,estate_high],
                  'flat_reference_ground_y':best,'minimum_flat_max_earthwork':max(best-estate_low,estate_high-best),
                  'existing_local_earthwork_limit':limit,'flat_paste_feasible':max(best-estate_low,estate_high-best)<=limit,
                  'terrace_design':'needs_design'}
    # Each profile band covers the complete transverse width, rather than a
    # flattering centre-line sample that could hide a steep parcel corner.
    bands=[]
    for z in range(ed):
        cells=[transform(x+.5,z+.5,c,estate)for x in range(ew)]
        values=np.array([heights[math.floor(zz),math.floor(xx)]for xx,zz in cells])
        bands.append({'distance':z,'min':int(values.min()),'median':float(np.median(values)),'max':int(values.max())})
    c['profile']=bands
    c['entrance_xz']=transform(49.5,1.5,c,estate)
    gate=next(b for b in estate['buildings']if b['role']=='gate')
    c['gate_portal_xz']=transform((gate['rect'][0]+gate['rect'][2])/2,gate['rect'][1],c,estate)
    c['lanes']=affected_lanes(g.source['features'],c,city['bounds']['source_offset'],heights,cfg['rules']['lane_width'])
    half=cfg['rules']['lane_width']/2
    c['perimeter_lane_xz']=[[a+half,b+half],[e-half,b+half],[e-half,f-half],[a+half,f-half],[a+half,b+half]]
    c['gate_approach_xz']=[[e-half,c['gate_portal_xz'][1]],c['gate_portal_xz']]
    c['demolition_count']=len(retired)
    c['residences_remaining']=sum(r['source_kind']=='house'for r in g.rows)-len(retired)
    c['population']='not_inferred_from_building_count'
    return c


def source_grid(out, g):
    cache=out/'source';cache.mkdir(exist_ok=True)
    stamp=cache/'identity.json';valid=False
    names=('terrain.bin.gz','water.bin.gz','materials.bin.gz','source.json')
    if stamp.exists():
        old=json.loads(stamp.read_text())
        valid=old.get('inputs')==g.hashes and all((cache/name).exists() and sha(cache/name)==old['outputs'].get(name)for name in names)
    if not valid:
        subprocess.run(['node',str(ROOT/'tools/province_capitals/central/source_game.mjs'),'--out',str(cache)],cwd=ROOT,check=True)
        save(stamp,{'inputs':g.hashes,'outputs':{name:sha(cache/name)for name in names}})
    return cache


def scene_svg(g, city, heights, cfg, bp, waystones, candidates):
    """同源总平面；颜色表达规划状态，建筑轮廓取正式模板包围盒。"""
    bounds=[310,500,910,1270];a,b,e,f=bounds
    sample=heights[b:f,a:e].astype(float);t=np.clip((sample-80)/150,0,1)
    dz,dx=np.gradient(sample);shade=np.clip(.92-(dx+dz)*.018,.65,1.03)
    rgb=np.stack((105+80*t,139+56*t,103+60*t),axis=2)*shade[:,:,None]
    im=Image.fromarray(np.clip(rgb,0,255).astype('uint8'));buf=BytesIO();im.save(buf,format='JPEG',quality=78,optimize=True)
    image='data:image/jpeg;base64,'+base64.b64encode(buf.getvalue()).decode()
    out=[f'<svg id="map" xmlns="http://www.w3.org/2000/svg" viewBox="{a} {b} {e-a} {f-b}" aria-label="中州庄园规划平面图"><image x="{a}" y="{b}" width="{e-a}" height="{f-b}" href="{image}"/>']
    offset=city['bounds']['source_offset'];ox,_,oz=offset
    for feature in g.source['features']:
        kind=feature.get('kind',0);top=feature.get('top');t=feature['t'];color=None
        if kind in(1,4):color='#795f55'
        elif top in(12,19,25):color='#488ba2'
        elif t==2 and feature.get('pid') and top in(7,8,10,11,23,27):color='#baaf98'if kind==3 else'#e0d0a7'
        elif top in(11,15):color='#ad9172'
        if color is None:continue
        if t==2:
            out.append(f'<path d="M{feature["ax"]-ox},{feature["az"]-oz} L{feature["bx"]-ox},{feature["bz"]-oz}" stroke="{color}" stroke-width="{feature["hw"]*2}" fill="none" stroke-linecap="round"/>')
        elif t==1:
            out.append(f'<ellipse cx="{feature["cx"]-ox}" cy="{feature["cz"]-oz}" rx="{feature["rx"]}" ry="{feature["rz"]}" fill="{color}"/>')
        elif t==0:
            out.append(f'<rect x="{feature["x0"]-ox}" y="{feature["z0"]-oz}" width="{feature["x1"]-feature["x0"]}" height="{feature["z1"]-feature["z0"]}" fill="{color}"/>')
    out.append('<g id="old-buildings">')
    for row in g.rows:
        x,z,x1,z1=row_box(row);house=row['source_kind']=='house'
        out.append(f'<rect data-building="{row["id"]}" x="{x}" y="{z}" width="{x1-x}" height="{z1-z}" fill="{"#c0c7b8"if house else"#d9ad79"}" stroke="#45544c" stroke-width=".7"><title>{row["id"]} · {"民居"if house else row["name"]}</title></rect>')
    out.append('</g>')
    for site in waystones:
        x,z,x1,z1=site['bounds'];out.append(f'<rect x="{x}" y="{z}" width="{x1-x+1}" height="{z1-z+1}" fill="#627d9d" stroke="#c2d9ee" stroke-width="1"><title>{escape(site["name"])}</title></rect>')
    out.append('<g id="estate-overlay"></g><g fill="#fff9e8" font-family="sans-serif" font-size="9" paint-order="stroke" stroke="#35473c" stroke-width="2">')
    for x,z,label in [(579,670,'三台宫苑'),(558,1196,'正阳门'),(640,1120,'镜湖'),(620,930,'御河'),(750,915,'东寺'),(778,844,'凌霄塔'),(366,850,'西坡宅坊')]:
        out.append(f'<text x="{x}" y="{z}" text-anchor="middle">{label}</text>')
    out.append('<text x="865" y="545">北 ↑</text></g></svg>')
    return re.sub(r'-?\d+\.\d+', lambda m:format(float(m.group()),'.2f'), ''.join(out))


def selected_svg(base, candidate, estate):
    """供下载和归档的静态局部蓝图，与交互页共用候选数据。"""
    a,b,e,f=candidate['plot']
    svg=re.sub(r'viewBox="[^"]+"',f'viewBox="{a-24} {b-20} {e-a+48} {f-b+40}"',base,count=1)
    for row in candidate['retired_buildings']:
        svg=svg.replace(f'data-building="{row["id"]}"',f'data-building="{row["id"]}" display="none"')
    overlay=['<g id="estate-overlay">']
    def rectangle(bounds,color,stroke='#445c4b'):
        x,z,x1,z1=bounds;overlay.append(f'<rect x="{x}" y="{z}" width="{x1-x}" height="{z1-z}" fill="{color}" stroke="{stroke}" stroke-width=".5"/>')
    def translated(r):
        p,q=transform(r[0],r[1],candidate,estate),transform(r[2],r[3],candidate,estate)
        return[min(p[0],q[0]),min(p[1],q[1]),max(p[0],q[0]),max(p[1],q[1])]
    rectangle(candidate['plot'],'#8d9f8d','#f1d694')
    rectangle(candidate['estate_bounds'],'#68816c')
    for corridor in estate['corridors']:rectangle(translated(corridor['rect']),'#c3ae88')
    for building in estate['buildings']:
        bounds=translated(building['rect']);rectangle(bounds,'#dee7d8')
        name='三层主楼'if building['role']=='main'else building['name']
        overlay.append(f'<text x="{(bounds[0]+bounds[2])/2}" y="{(bounds[1]+bounds[3])/2}" text-anchor="middle" font-family="sans-serif" font-size="3" fill="#2a4635">{escape(name)}</text>')
    pond=estate['pond'];cx,cz=transform(*pond['center'],candidate,estate)
    overlay.append(f'<ellipse cx="{cx}" cy="{cz}" rx="{pond["radius"][1]}" ry="{pond["radius"][0]}" fill="#679cb1"/>')
    routes=[candidate['perimeter_lane_xz'],candidate['gate_approach_xz'],*[spur for lane in candidate['lanes']for spur in lane['connection_spurs_xz']]]
    for route in routes:
        d=' '.join(('M'if i==0 else'L')+f'{x},{z}'for i,(x,z)in enumerate(route))
        overlay.append(f'<path d="{d}" fill="none" stroke="#78d2c2" stroke-width="3" stroke-linejoin="round"/>')
    overlay.append(f'<text x="{a}" y="{b-9}" font-family="sans-serif" font-size="5" fill="#f4f0da">{escape(candidate["name"])} · 拟替换 {candidate["demolition_count"]} 栋民居 · 北 ↑</text>')
    overlay.append('</g>')
    return svg.replace('<g id="estate-overlay"></g>',''.join(overlay))


def build(out):
    out.mkdir(parents=True,exist_ok=True)
    cfg=read(SOURCE);estate=read(cfg['sources']['estate']);city=read(cfg['sources']['city']);bp=read(cfg['sources']['city_blueprint'])
    g=geometry(city);cache=source_grid(out,g)
    W,D=city['bounds']['width'],city['bounds']['depth']
    heights=np.frombuffer(gzip.decompress((cache/'terrain.bin.gz').read_bytes()),dtype='<i2').reshape(D,W)
    waters=np.frombuffer(gzip.decompress((cache/'water.bin.gz').read_bytes()),dtype='<i2').reshape(D,W)
    guards={name:np.zeros((D,W),bool)for name in('functional_buildings','main_roads','walls','water','public_courts','waystones')}
    clearance=cfg['rules']['public_clearance'];offset=city['bounds']['source_offset']
    for row in g.rows:
        if row['source_kind']not in cfg['rules']['retire_roles']:protect_box(guards['functional_buildings'],row_box(row),clearance)
    for feature in g.source['features']:
        kind=feature.get('kind',0)
        if kind in(1,4):paint(guards['walls'],feature,offset,clearance)
        if kind==2 or feature.get('top')in(12,19,25):paint(guards['water'],feature,offset,clearance)
        if feature['t']==2 and kind!=3 and feature.get('pid') and feature.get('top')in(7,8,10,11,23,27):paint(guards['main_roads'],feature,offset,clearance)
    guards['water']|=waters!=-32768
    bindings=bp['source_courtyard_bindings']
    for i,feature,owner in source_surfaces(g):
        if bindings[f'source_court_{i}']['access']=='public':paint(guards['public_courts'],feature,offset,clearance)
    for garden in bp['gardens']:
        if bp['garden_bindings'][garden['id']]['access']=='public':
            a,b,e,f=garden['bounds'];protect_box(guards['public_courts'],[a,b,e+1,f+1],clearance)
    ws=next(c for c in read(cfg['sources']['waystones'])['cities']if c['id']=='zhongzhou')['sites']
    for site in ws:
        a,b,e,f=site['bounds'];protect_box(guards['waystones'],[a,b,e+1,f+1],clearance)
    candidates=[]
    for candidate in cfg['candidates']:
        result=evaluate(cfg,estate,city,bp,g,heights,waters,candidate)
        a,b,e,f=result['plot'];overlaps={name:int(mask[b:f,a:e].sum())for name,mask in guards.items()}
        result['protected_overlap_columns']=overlaps
        if any(overlaps.values()):raise ValueError(f'候选 {candidate["id"]} 侵入保护范围：{overlaps}')
        candidates.append(result)
    files={str(SOURCE.relative_to(ROOT)):sha(SOURCE),**g.hashes,
           **{path:sha(ROOT/path)for path in cfg['sources'].values()},
           'tools/player_estates/zhongzhou_plan.py':sha(Path(__file__)),
           'tools/player_estates/zhongzhou_viewer.html':sha(HERE/'zhongzhou_viewer.html')}
    checks={'planning_consistency':'pass','dimensions':'98×111，原比例，未裁切','retire_roles':['house'],
            'protected_parcel_overlap':'zero_in_all_candidates','terrain_sampling':'one_block_from_current_owning_source',
            'flat_paste':'not_feasible_in_all_candidates','terraced_architecture':'needs_design',
            'lane_vertical_connectivity':'needs_design','retained_dwelling_access':'not_verified',
            'minecraft':'not_run','runtime_integration':'not_implemented',
            'owner_visual_acceptance':'not_verified'}
    if any(c['terrain']['flat_paste_feasible']for c in candidates):checks['flat_paste']='see_individual_candidates'
    result={'schema':1,'name':cfg['name'],'scope':cfg['scope'],'selected':cfg['selected'],'coordinate_space':cfg['rules']['coordinate_space'],
            'source_offset':city['bounds']['source_offset'],'existing_residences':sum(r['source_kind']=='house'for r in g.rows),
            'estate':estate,'candidates':candidates,'checks':checks,'source_hashes':files,
            'terrain_sha256':sha(cache/'terrain.bin.gz')}
    save(out/'plan.json',result);save(out/'checks.json',checks)
    svg=scene_svg(g,city,heights,cfg,bp,ws,candidates)
    (out/'city-plan.svg').write_text(svg)
    for candidate in candidates:
        (out/f'{candidate["id"]}.svg').write_text(selected_svg(svg,candidate,estate))
    template=(HERE/'zhongzhou_viewer.html').read_text()
    html=template.replace('/*DATA*/',json.dumps(result,ensure_ascii=False,separators=(',',':'))).replace('<!--MAP-->',svg)
    if len(html)>512000:raise ValueError('规划预览超出前端大小上限')
    (out/'index.html').write_text(html)
    save(out/'manifest.json',{'source_hashes':files,'files':{p.name:sha(p)for p in out.iterdir()if p.is_file()and p.name!='manifest.json'}})
    print(json.dumps({'selected':cfg['selected'],'checks':checks,'candidates':[{k:c[k]for k in('id','demolition_count','residences_remaining','terrain')}for c in candidates]},ensure_ascii=False,indent=2))
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,default=ROOT/'out/preview/zhongzhou-player-estate-plan')
    build(parser.parse_args().out.resolve())
