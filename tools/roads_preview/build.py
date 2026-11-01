#!/usr/bin/env python3
"""导出道路外观的离线三维选样，不修改运行时配置、不启动 Minecraft。"""
from __future__ import annotations

import argparse
import base64
import gzip
import hashlib
import heapq
import html
import json
import math
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]


def build_scene(scene, style, keys):
    length, span = scene['length'], scene['half_span']
    radius, step = style['width']//2, scene['ramp_step']
    terrain, shoulder = scene['terrain'], scene['shoulders']
    quantize=lambda h:round(h/step)*step
    center = [round(scene['curve_amplitude']*math.sin(z/length*math.pi*2)) for z in range(length)]
    def profile(z):
        return scene['base_height']+scene['rise']*max(0, min(1, (z-scene['rise_start'])/(scene['rise_end']-scene['rise_start'])))
    def natural(x, z):
        return max(1, math.floor(profile(z)+x*terrain['cross_slope']+max(0,x-terrain['bank_start'])*terrain['bank_slope']+terrain['undulation']*math.sin(z*terrain['frequency_z']+x*terrain['frequency_x'])))
    road = {}
    station = {}
    for z, cx in enumerate(center):
        for dx in range(-radius, radius+1):
            for dz in range(-radius, radius+1):
                zz = z+dz
                if not 0 <= zz < length: continue
                k = cx+dx, zz
                h = round((profile(z)+style['lift'])/step)*step
                if k not in road or h > road[k]: road[k], station[k] = h, z
    # Half-block transitions apply across the entire footprint, including bends.
    queue = [(h, x, z) for (x,z),h in road.items()]
    heapq.heapify(queue)
    while queue:
        h,x,z=heapq.heappop(queue)
        if h != road[x,z]: continue
        for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]:
            k=x+dx,z+dz
            if k in road and road[k] > h+step:
                road[k]=h+step;heapq.heappush(queue,(h+step,*k))
    for z,cx in enumerate(center):
        for dx in range(-radius,radius+1):
            for dz in range(-radius,radius+1):
                if 0 <= z+dz < length: assert (cx+dx,z+dz) in road
    for (x,z),h in road.items():
        for dx,dz in [(1,0),(0,1)]:
            if (x+dx,z+dz) in road: assert abs(road[x+dx,z+dz]-h) <= step

    columns=[]
    soil = 'clay' if style['biome']=='desert' else 'soil'
    ground = 'sand' if style['biome']=='desert' else 'grass'
    for z in range(length):
        for x in range(-span,span+1):
            k=x,z; h=natural(x,z); top=ground; side=soil; role=0
            candidates=[(math.hypot(x-rx,z-rz),rh,rx,rz) for (rx,rz),rh in road.items()]
            distance,rh,rx,rz=min(candidates)
            lateral=x-center[z]
            if k in road:
                h=road[k];top=style['surface'];side=style['side'];role=1
                if style['id']=='tracks' and abs(lateral)==1:top='gravel'
                if style['id']=='official' and abs(lateral)==radius:top='setts'
                if style['id']=='mountain' and abs(lateral)==radius:top='stone'
                if style['id']=='desert' and abs(lateral)==1:top='worn_sand'
            elif distance<=style['shoulder']:
                role=2
                if style['grading']=='formal':h=rh+shoulder['curb_rise'];top='edge';side='edge'
                elif style['grading']=='retained':
                    h=max(h,rh-shoulder['retained_offset']) if x<rx else min(h,rh+shoulder['retained_offset'])
                    top='gravel';side='setts' if x<rx else soil
                elif style['grading']=='desert':
                    h=quantize(rh+(h-rh)*shoulder['desert_blend'])
                    top='sand_edge' if x<rx and z%shoulder['edge_period']<shoulder['edge_length'] else 'sand';side='sandstone'
                else:
                    h=quantize(rh+(h-rh)*shoulder['soft_blend'])
                    top='gravel' if distance<shoulder['gravel_distance'] else ('soil' if (z//shoulder['grass_period'])%shoulder['grass_cycle'] else 'grass')
            elif distance < scene['grade_run']+style['shoulder']:
                fraction=(distance-style['shoulder'])/scene['grade_run']
                target=rh*(1-fraction)+h*fraction
                if style['grading']=='retained':target=h # retain the natural hillside outside the narrow shoulder
                h=quantize(target)
            columns.append([x,z,h,keys.index(top),keys.index(side),role])
    boxes=[]
    for z in scene['milestones']:
        x=center[z]-radius-style['shoulder']-1
        h=next(c[2] for c in columns if c[0]==x and c[1]==z)
        boxes.append([x+.25,h,z+.25,.5,1,.5,keys.index('sandstone' if style['biome']=='desert' else 'edge')])
    # Small, off-road vegetation masses give scale without obscuring the paving.
    if style['biome']=='green':
        for x,z in scene['shrubs']:
            h=next(c[2] for c in columns if c[0]==x and c[1]==z)
            boxes.append([x,h,z,1,.75,1,keys.index('leaves')])
    return {'style':style,'columns':columns,'boxes':boxes,
            'checks':{'declared_width':style['width'],'carriage_columns':len(road),'full_width':True,'max_cardinal_step':step},
            'centerline':[[x,z] for z,x in enumerate(center)]}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out',type=Path,default=ROOT/'reports/road-style-samples')
    p.add_argument('--base-url',default='https://assets.ishiki333hhh.win/previews/intercity-road-styles-20261101/')
    a=p.parse_args();assert a.base_url.startswith('https://assets.ishiki333hhh.win/')
    config=json.loads((HERE/'styles.json').read_text());assert config['schema']==1
    keys=[*config['materials'],'grass','leaves']
    textures={};sources={}
    for name,stem in config['materials'].items():
        f=ROOT/'src/main/resources/assets/myvillage/textures/block/lord_manors'/f'{stem}.png'
        b=f.read_bytes();textures[name]='data:image/png;base64,'+base64.b64encode(b).decode()
        sources[str(f.relative_to(ROOT))]=hashlib.sha256(b).hexdigest()
    models=[build_scene(config['scene'],s,keys) for s in config['styles']]
    data={'scene':config['scene'],'keys':keys,'textures':textures,'models':models}
    compressed=base64.b64encode(gzip.compress(json.dumps(data,separators=(',',':'),ensure_ascii=False).encode(),mtime=0)).decode()
    script=(HERE/'viewer.js').read_text().replace('__DATA__',compressed)
    cards=[]
    for i,s in enumerate(config['styles']):
        cards.append(f'''<button class="sample" data-style="{i}" aria-pressed="false"><canvas aria-label="{html.escape(s['name'])}的三维样段"></canvas><div class="caption"><strong>{s['letter']} · {s['name']}</strong><span>{s['tag']} · {s['width']} 格通行</span><p>{s['description']}</p></div></button>''')
    page=(HERE/'viewer.html').read_text().replace('__CARDS__',''.join(cards)).replace('__SCRIPT__',script).replace('__BASE_URL__',a.base_url)
    a.out.mkdir(parents=True,exist_ok=True)
    (a.out/'preview.html').write_text(page)
    fallback=':root{--foreground:#242b2c;--muted-foreground:#637071;--card:#f3f2ea;--border:#d5d8ce;--primary:#2d6e59;--radius:12px;--font-sans:system-ui,sans-serif}body{max-width:1050px;margin:24px auto;padding:0 20px;background:#e8e9df!important}'
    (a.out/'index.html').write_text(page.replace('</style>',fallback+'</style>'))
    manifest={'kind':'offline_3d_design_samples','runtime_changed':False,'config_sha256':hashlib.sha256((HERE/'styles.json').read_bytes()).hexdigest(),
              'sources':sources,'checks':{m['style']['letter']:m['checks'] for m in models}}
    (a.out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'out':str(a.out),'html_bytes':len(page.encode()),'samples':len(models),'checks':manifest['checks']},ensure_ascii=False))


if __name__=='__main__':main()
