#!/usr/bin/env python3
"""构造中州西坡庄园的独立三维样稿；不写模组或玩家世界。"""
from __future__ import annotations

import argparse
import base64
import copy
import gzip
import hashlib
import json
from pathlib import Path
from io import BytesIO
import sys

import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'));sys.path.insert(0,str(ROOT))
from tools.player_estates.terrace_model import TerraceEstate,coarse_connectivity
from tools.player_estates.zhongzhou_plan import read,source_grid,evaluate,sha,save
from tools.player_estates.preview import bake
from tools.player_estates.geometry import Estate,AIR,state
from province_capitals.blueprint_plan.central import geometry
from province_capitals.central.game_world import World
from province_capitals.central.export_game import connect_bridges
from province_capitals.central.game_models import rotate_state
import voxel_dump_to_structures as exports
import json_to_nbt as nbt
from buildgen.nbtread import read_gzipped_nbt,state_string

CONFIG=ROOT/'blueprints/player_estates/zhongzhou_terraces.json'
JAR=Path('/home/ubuntu/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')


def original_roads(city,g,cache,out):
    signature={**g.hashes,'terrain':sha(cache/'terrain.bin.gz')}
    target=out/'original-roads.json';stamp=out/'roads-identity.json'
    if stamp.exists()and target.exists():
        ident=json.loads(stamp.read_text())
        if ident['inputs']==signature and ident['sha256']==sha(target):return json.loads(target.read_text())
    raw=json.loads((cache/'source.json').read_text());world=World(city,raw,cache,g.adapt,None)
    for row,model,transform in zip(g.rows,g.models,g.transforms):
        if row['source_kind']not in('archbridge','langqiao'):world.reserve(row,model,transform)
    world.prepare_walls()
    for path in raw['paths']:world.add_path(path['id'],path['points'],path['width'],wood=path['material']==23)
    for path in city['links']:world.add_path(path['id'],path['points'],path['width'],wood=path.get('wood',False))
    connect_bridges(world,raw,g.rows)
    for row in g.rows:
        if row['source_kind']in('archbridge','langqiao','watergate','walltower')or row['source_template']=='pillar_ting'or row.get('entry_via'):continue
        world.connect(row,row['entry'])
    result={'routes':world.routes,'access':world.access,'columns':[[i%world.w,i//world.w,*value]for i,value in world.roads.items()]}
    save(target,result);save(stamp,{'inputs':signature,'sha256':sha(target)})
    return result


def construct(out):
    out.mkdir(parents=True,exist_ok=True);cfg=read(CONFIG);placement=read(cfg['placement'])
    if placement['selected']!=cfg['site']or placement['review']['site_selection']!='accepted_by_owner':raise ValueError('西坡选址尚未确认')
    estate=read(placement['sources']['estate']);city=read(placement['sources']['city']);bp=read(placement['sources']['city_blueprint']);g=geometry(city)
    cache=source_grid(out,g)
    H=np.frombuffer(gzip.decompress((cache/'terrain.bin.gz').read_bytes()),dtype='<i2').reshape(1440,1200)
    W=np.frombuffer(gzip.decompress((cache/'water.bin.gz').read_bytes()),dtype='<i2').reshape(1440,1200)
    selected=next(c for c in placement['candidates']if c['id']==cfg['site'])
    selected=evaluate(placement,estate,city,bp,g,H,W,selected)
    ids=[r['id']for r in selected['retired_buildings']]
    if ids!=placement['approved_retirements']:raise ValueError('替换范围已偏离获准民居清单')
    a,b,e,f=selected['plot'];base=cfg['base_y']
    terrain=np.array([[H[b+x,e-1-z]-base for x in range(104)]for z in range(117)])
    model=TerraceEstate(estate,cfg,terrain,bp['rules']['local_earthwork_limit']).buildings_build()
    roads=original_roads(city,g,cache,out);columns={(x,z):data for x,z,*data in roads['columns']}
    anchors={};interfaces=[]
    for lane in selected['lanes']:
        for spur in lane['connection_spurs_xz']:
            x,z=map(round,spur[0]);foot2=columns[x,z][0]
            xx,zz=z-b,e-1-x;point=min(102,max(1,xx)),min(115,max(1,zz))
            feet=(foot2+1)//2-base
            if point in anchors and anchors[point]!=feet:raise ValueError('原生活巷接点存在冲突')
            anchors[point]=feet;interfaces.append({'source':lane['source_path_id'],'city_xz':[x,z],'existing_feet2':foot2,'plot_point':list(point),'new_feet_y':feet+base})
    print('逐栋首层标高', {k:v+base for k,v in model.floor_by_id.items()},flush=True)
    model.routes_build(anchors).gardens_build()
    start=(52,model.path_field[52,1],1)
    check=coarse_connectivity(model,start)
    result={'name':cfg['name'],'selected':selected,'base_y':base,'floors':{k:v+base for k,v in model.floor_by_id.items()},
            'interfaces':interfaces,'walkability':check,'terrain_cut_max':max((int(model.original[z,x])-int(model.terrain[z,x])for x,z in model.ground_edits),default=0),
            'structural_support_max':max(model.support_heights,default=0),'roof_join_trim_cells':len(model.roof_junctions),
            'routes':model.route_records,'source_hashes':g.hashes,'terrain_source_sha256':sha(cache/'terrain.bin.gz'),
            'retained_dwelling_access':'not_verified','game_runtime':'not_verified','owner_visual_acceptance':'not_verified','runtime_integration':'not_implemented'}
    save(out/'checks.json',result)
    if not check['all_reachable']:raise ValueError(f'分台地楼层未连通：{check["failed"]}')
    return cfg,placement,estate,city,g,cache,H,model,roads,result


def scene(cfg,placement,estate,city,g,cache,H,model,roads,result):
    margin=cfg['preview']['context_margin'];base=cfg['base_y'];a,b,e,f=result['selected']['plot'];retired=set(placement['approved_retirements'])
    preview=Estate({'size':[104+2*margin,128,117+2*margin],'palette':{},'views':{},'reference':estate['reference']})
    preview.rooms=copy.deepcopy(model.rooms)
    context_heights=H.copy()
    for row,template,transform in zip(g.rows,g.models,g.transforms):
        if row['id']in retired:continue
        for x,z in template.floor_footprint:
            wx,wy,wz=transform((x,0,z));context_heights[wz,wx]=wy
    for wx,wz,foot2,face,wood,rid in roads['columns']:
        if not wood:context_heights[wz,wx]=foot2//2-1+(foot2%2)
    for z in range(117):
        for x in range(104):context_heights[b+x,e-1-z]=int(model.terrain[z,x])+base
    def put_local(x,y,z,s,layer):
        p=(x+margin,y,z+margin)
        if 0<=p[0]<preview.c['size'][0]and 0<=p[2]<preview.c['size'][2]and 0<=y<128:
            if s==AIR:preview.blocks.pop(p,None);preview.layers.pop(p,None)
            else:preview.blocks[p]=s;preview.layers[p]=layer
    # Only visible mountain columns are baked; the source heightfield remains
    # authoritative for support, cut depth and the complete demonstration export.
    for z in range(-margin,117+margin):
        for x in range(-margin,104+margin):
            wx,wz=e-1-z,b+x;h=int(context_heights[wz,wx])-base
            inside=0<=x<104 and 0<=z<117
            if inside:h=int(model.terrain[z,x])
            neighbour=min(int(context_heights[wz+dz,wx+dx])-base for dx,dz in((1,0),(-1,0),(0,1),(0,-1)))
            bottom=max(0,min(h-2,neighbour))
            if x in(-margin,103+margin)or z in(-margin,116+margin):bottom=0
            for y in range(bottom,h+1):put_local(x,y,z,state('grass_block',snowy=False)if y==h else'minecraft:dirt'if y>=h-2 else'minecraft:stone','terrain')
    count=0;omitted=0
    for row,template,transform in zip(g.rows,g.models,g.transforms):
        bounds=row['bounds'];lo,hi=bounds['min'],bounds['max']
        if hi[0]<a-margin or lo[0]>=e+margin or hi[2]<b-margin or lo[2]>=f+margin:continue
        if row['id']in retired:continue
        count+=1
        for p,s in template.cells.items():
            if s==AIR:continue
            name=s.split('[')[0]
            if name.endswith(('_sign','_banner'))or name in('minecraft:chest','minecraft:trapped_chest','minecraft:ender_chest'):
                omitted+=1;continue
            wx,wy,wz=transform(p);x,z=wz-b,e-1-wx
            put_local(x,wy-base,z,rotate_state(s,3),'context')
    for wx,wz,foot2,face,wood,rid in roads['columns']:
        if a<=wx<e and b<=wz<f:continue
        x,z=wz-b,e-1-wx;y=foot2//2-1-base
        block=state('spruce_planks')if wood else state('stone_bricks')
        if face:block=rotate_state(state('stone_brick_stairs',facing=face,half='bottom',shape='straight',waterlogged=False),3)
        put_local(x,y,z,block,'context_roads')
        if foot2%2:put_local(x,y+1,z,state('stone_brick_slab',type='bottom',waterlogged=False),'context_roads')
    for(x,y,z),s in model.blocks.items():put_local(x,y,z,s,model.layers[x,y,z])
    # Soft furniture and the source bed preview have the same limitations as
    # the original independent estate viewer; the building bodies are native.
    cx,cz=52+margin,58+margin
    preview.c['views']={
        'overview':{'name':'坡地全景','eye':[cx+115,132,cz-145],'target':[cx,49,cz],'fov':39},
        'front':{'name':'门楼与入园阶梯','eye':[cx+40,46,cz-108],'target':[cx,36,cz-20],'fov':42},
        'court':{'name':'院内层台与主楼','eye':[cx+5,67,cz-30],'target':[cx,65,cz+32],'fov':47},
        'side':{'name':'侧看阶廊与支承','eye':[cx-155,100,cz+20],'target':[cx,49,cz],'fov':38},
        'cut':{'name':'去顶查看楼层','eye':[cx+120,155,cz-120],'target':[cx,47,cz],'fov':40,'cut':96}}
    result['context_buildings']=count
    result['context_entity_furniture_omitted']=omitted
    return preview


def export_demo(out,model):
    blocks={}
    for z in range(117):
        for x in range(104):
            h=int(model.terrain[z,x])
            for y in range(h+1):blocks[x,y,z]=state('grass_block',snowy=False)if y==h else'minecraft:dirt'if y>=h-2 else'minecraft:stone'
    blocks.update(model.blocks)
    structure=exports.Structure(blocks)
    root=exports.build_structure_nbt(structure,exports.DEFAULT_DATA_VERSION,'MyVillage 西坡独立展示样稿；仅用于空白测试区域')
    nbt.write_gzipped_nbt(root,str(out/'west_middle_terraces.nbt'))
    schematic,_=exports.build_schem(structure,exports.DEFAULT_DATA_VERSION)
    exports.write_gzipped_nbt_ext(schematic,out/'west_middle_terraces.schem','Schematic')
    _,readback=read_gzipped_nbt(str(out/'west_middle_terraces.nbt'))
    palette=[state_string(p)for p in readback['palette']]
    actual={tuple(b['pos']):palette[b['state']]for b in readback['blocks']if palette[b['state']]!=AIR}
    expected={p:s for p,s in blocks.items()if s!=AIR}
    # Palette property order is canonical in both generators.
    from tools.player_estates.build import canonical
    if actual!={p:canonical(s)for p,s in expected.items()}:raise ValueError('台地 NBT 回读不同')
    _,sc=read_gzipped_nbt(str(out/'west_middle_terraces.schem'))
    inv={v:canonical(k)for k,v in sc['Palette'].items()};values=exports.decode_varints(sc['BlockData']);w,h,d=readback['size']
    if len(values)!=w*h*d:raise ValueError('台地 SCHEM 体积不符')
    for i,v in enumerate(values):
        p=(i%w,i//(w*d),(i//w)%d)
        if inv[v]!=canonical(expected.get(p,AIR)):raise ValueError(f'台地 SCHEM 回读不同 {p}')
    return{'nbt_cells':len(expected),'nbt_mismatches':0,'schem_cells':len(values),'schem_mismatches':0,'size':readback['size']}


def write_plan(out,cfg,result):
    estate=read(read(cfg['placement'])['sources']['estate'])
    items=['<svg xmlns="http://www.w3.org/2000/svg" width="762" height="708" viewBox="-5 -9 127 118">',
           '<rect x="-5" y="-9" width="127" height="118" fill="#f3f1e9"/>',
           '<rect width="117" height="104" fill="#8d9f8d" stroke="#526b56" stroke-width=".5"/>',
           '<text x="0" y="-4" font-family="sans-serif" font-size="4" fill="#314837">中州西坡庄园 · 台地平面 · 北 ↑</text>']
    def line(points,color,width=1):
        d=' '.join(('M'if i==0 else'L')+f'{116.5-z},{x+.5}'for i,(x,y,z)in enumerate(points))
        items.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{width}" stroke-linejoin="round"/>')
    for route in result['routes']:line(route['points'],'#72c4b4'if route['id']=='public_ring'else'#bcb69c',3)
    for b in estate['buildings']:
        x0,z0,x1,z1=cfg['building_rect_overrides'].get(b['id'],b['rect']);x0+=3;z0+=3;x1+=3;z1+=3
        a,c,e,f=117-z1,x0,117-z0,x1;y=result['floors'][b['id']]
        items.append(f'<rect x="{a}" y="{c}" width="{e-a}" height="{f-c}" fill="#e0e6d9" stroke="#526b56" stroke-width=".6"/>')
        items.append(f'<text x="{(a+e)/2}" y="{(c+f)/2-1}" text-anchor="middle" font-family="sans-serif" font-size="2.5" fill="#314837">{b["name"]}</text>')
        items.append(f'<text x="{(a+e)/2}" y="{(c+f)/2+3}" text-anchor="middle" font-family="sans-serif" font-size="2.5" fill="#314837">地板 Y{y} · {b["levels"]} 层</text>')
    pond=cfg['garden']['pond'];cx,cz=pond['center'];items.append(f'<ellipse cx="{117-cz-3}" cy="{cx+3}" rx="{pond["radius"][1]}" ry="{pond["radius"][0]}" fill="#6c9fab"/>')
    items.append('<text x="117" y="108" text-anchor="end" font-family="sans-serif" font-size="2.7" fill="#314837">东侧入口 → · 地块 117×104 格 · Y 为中州局部标高</text></svg>')
    (out/'terrace-plan.svg').write_text(''.join(items))


def write_viewer(out,cfg,result):
    template=(ROOT/'tools/player_estates/terrace_viewer.html').read_text()
    libraries=[base64.b64encode(gzip.compress((ROOT/name).read_bytes(),mtime=0)).decode()for name in('tools/web/three.min.js','tools/web/OrbitControls.js')]
    loader='window.__librariesReady=(async()=>{for(const text of '+json.dumps(libraries)+'){const bytes=Uint8Array.from(atob(text),c=>c.charCodeAt(0));const code=await new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream("gzip"))).text();const script=document.createElement("script");script.textContent=code;document.head.append(script)}})();'
    template=template.replace('/*THREE*/',loader).replace('/*ORBIT*/','')
    template=template.replace('const BASE=','(async()=>{await window.__librariesReady;const BASE=')
    template=template.replace('</script></body>','})();</script></body>')
    template=template.replace('/*BASE*/',json.dumps(cfg['preview']['base_url']))
    template=template.replace('/*REV*/',json.dumps(sha(out/'scene.js')[:16]))
    template=template.replace('/*ATLAS*/',json.dumps('data:image/png;base64,'+base64.b64encode((out/'atlas.png').read_bytes()).decode()))
    poster=cfg['preview']['base_url']+'overview.png'
    if(out/'overview.png').exists():
        frame=Image.open(out/'overview.png').convert('RGB');frame.thumbnail((880,600));buf=BytesIO();frame.save(buf,format='JPEG',quality=80,optimize=True)
        poster='data:image/jpeg;base64,'+base64.b64encode(buf.getvalue()).decode()
    template=template.replace('/*POSTER*/',json.dumps(poster))
    floors='<p>原地形最多下挖 '+str(result['terrain_cut_max'])+' 格；建筑及阶廊最大支承高度 '+str(result['structural_support_max'])+' 格。高架支承另计，未把山坡整片铺平。</p><table><tr><th>建筑</th><th>首层地板 Y</th></tr>'
    for key,y in result['floors'].items():
        name=next(b['name']for b in read(read(cfg['placement'])['sources']['estate'])['buildings']if b['id']==key)
        floors+='<tr><td>'+name+'</td><td>'+str(y)+'</td></tr>'
    floors+='</table><p>18 处楼层及全部已建路径中心通过离线格点检查。上行一格必须有朝向正确的楼梯；这项结果不代替游戏碰撞和实际操作验收。</p>'
    template=template.replace('<!--FLOORS-->',floors)
    if len(template)>512000:raise ValueError('台地预览超出前端 HTML 限制')
    (out/'index.html').write_text(template)


def finalize(out):
    cfg=read(CONFIG);result=json.loads((out/'checks.json').read_text());write_plan(out,cfg,result);write_viewer(out,cfg,result)
    estate_source=read(cfg['placement'])['sources']['estate']
    inputs={**result['source_hashes'],str(CONFIG.relative_to(ROOT)):sha(CONFIG),cfg['placement']:sha(ROOT/cfg['placement']),
            estate_source:sha(ROOT/estate_source),
            **{str(p.relative_to(ROOT)):sha(p)for p in (ROOT/'tools/player_estates').glob('*')if p.suffix in('.py','.html')}}
    private={'manifest.json','original-roads.json','roads-identity.json'}
    save(out/'manifest.json',{'source_hashes':inputs,'files':{p.name:sha(p)for p in out.iterdir()if p.is_file()and p.name not in private}})


def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--out',type=Path,default=ROOT/'out/preview/zhongzhou-west-terraces');ap.add_argument('--geometry-only',action='store_true');ap.add_argument('--finalize',action='store_true')
    args=ap.parse_args();out=args.out.resolve()
    if args.finalize:finalize(out);return
    cfg,placement,estate,city,g,cache,H,model,roads,result=construct(out)
    if not args.geometry_only:
        result['export']=export_demo(out,model)
        preview=scene(cfg,placement,estate,city,g,cache,H,model,roads,result)
        result['preview']=bake(preview,JAR,out)
    save(out/'checks.json',result)
    if not args.geometry_only:finalize(out)
    print(json.dumps({k:result[k]for k in('floors','terrain_cut_max','structural_support_max','walkability')},ensure_ascii=False,indent=2))


if __name__=='__main__':main()
