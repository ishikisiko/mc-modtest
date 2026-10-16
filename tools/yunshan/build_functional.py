#!/usr/bin/env python3
"""Build separately saved Yunshan functional buildings and their site variants."""
from __future__ import annotations
import argparse
from collections import OrderedDict
import copy
import html
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
import json_to_nbt as nbt
import voxel_dump_to_structures as export
from yunshan.build_inn import build, attach_signs, verify_exports


def rotated_pos(pos,size,quarters):
    x,y,z = pos
    w,_,length = size
    for _ in range(quarters%4):
        x,z = length-1-z,x
        w,length = length,w
    return x,y,z


def rotated_state(state,quarters):
    name,props = nbt.parse_block_state(state)
    dirs = ['north','east','south','west']
    q = quarters%4
    if props.get('facing') in dirs:
        props['facing'] = dirs[(dirs.index(props['facing'])+q)%4]
    if q%2 and props.get('axis') in ('x','z'):
        props['axis'] = 'z' if props['axis']=='x' else 'x'
    cardinal = {k:v for k,v in props.items() if k in dirs}
    for k in cardinal:
        del props[k]
    for k,v in cardinal.items():
        props[dirs[(dirs.index(k)+q)%4]] = v
    return name+('['+','.join(f'{k}={v}' for k,v in sorted(props.items()))+']' if props else '')


def validate_site(data,reference):
    site = data['site']
    if site.get('placement_mode')=='showcase':
        assert site['original_city_fit']=='requires_replanning'
        assert site['rotation_quarters']==0 and site['facing']=='N'
        assert site['world_max']==[site['world_min'][i]+data['size'][i]-1 for i in range(3)]
        assert all(0<n<=48 for n in data['size']), 'must fit a vanilla structure-block export'
        return {'lot_id':site['lot_id'],'size_after_rotation':data['size'],
                'world_min':site['world_min'],'world_max':site['world_max'],'facing':'N',
                'rotation_quarters':0,'matches_original_lot':False,'placement_mode':'showcase',
                'original_city_fit':'requires_replanning'}
    lot = next(l for l in reference['lots'] if l['id']==site['lot_id'])
    q = site['rotation_quarters']
    assert q == {'N':0,'E':1,'S':2,'W':3}[lot['facing']]
    assert site['facing']==lot['facing']
    w,h,length = data['size']
    if q%2:
        w,length = length,w
    x0,z0,x1,z1 = lot['rect']
    assert (w,length)==(x1-x0+1,z1-z0+1), 'does not fit original lot'
    dx,dy,dz = reference['world_offset']
    minimum = [x0+dx,lot['level']+dy-1,z0+dz]
    maximum = [x1+dx,minimum[1]+h-1,z1+dz]
    assert site['world_min']==minimum and site['world_max']==maximum
    return {'lot_id':lot['id'],'size_after_rotation':[w,h,length],
            'world_min':minimum,'world_max':maximum,'facing':lot['facing'],
            'rotation_quarters':q,'matches_original_lot':True}


def validate_services(data,blocks):
    result = []
    for service in data.get('service_blocks',[]):
        pos = tuple(service['pos'])
        assert blocks[pos]==service['state'], f'overwritten facility {service["name"]}'
        name,props = nbt.parse_block_state(blocks[pos])
        state_file = ROOT/'src/main/resources/assets/myvillage/blockstates'/f'{name.split(":")[1]}.json'
        variants = json.loads(state_file.read_text())['variants']
        keys = [dict(p.split('=') for p in key.split(',')) if key else {} for key in variants]
        assert dict(props) in keys, f'unknown facility state: {blocks[pos]}'
        target = data['walk_targets'][service['approach']]
        assert abs(target[0]-pos[0])+abs(target[2]-pos[2])<=1, 'approach is not adjacent'
        assert abs((target[1]+1.62)-(pos[1]+.5))<2.5, 'facility beyond eye-height reach'
        result.append({'name':service['name'],'block':name,'approach':target,'state_valid':True})
    return result


def export_site(data,blocks,out):
    q = data['site']['rotation_quarters']
    size = data['size']
    rotated = OrderedDict(sorted(((rotated_pos(p,size,q),rotated_state(s,q)) for p,s in blocks.items()),
                                 key=lambda item:(item[0][1],item[0][2],item[0][0])))
    structure = export.Structure(rotated)
    signs = copy.deepcopy(data['signs'])
    for sign in signs:
        sign['pos'] = list(rotated_pos(sign['pos'],size,q))
    stem = out/(data['id'].split(':')[1]+'_site')
    root = export.build_structure_nbt(structure,export.DEFAULT_DATA_VERSION,'MyVillage Yunshan site variant')
    schem,_ = export.build_schem(structure,export.DEFAULT_DATA_VERSION)
    attach_signs(root,schem,signs)
    nbt.write_gzipped_nbt(root,str(stem.with_suffix('.nbt')))
    export.write_gzipped_nbt_ext(schem,stem.with_suffix('.schem'),'Schematic')
    verify_exports(rotated,stem.with_suffix('.nbt'),stem.with_suffix('.schem'),signs)
    return {'schem':stem.with_suffix('.schem').name,'nbt':stem.with_suffix('.nbt').name,
            'clipboard_offset':[0,0,0],'min_corner':data['site']['world_min'],
            'state_rotation_verified':True,'actual_placement':'not_verified'}


def map_svg(reference,entries):
    v=reference['view']; x0,z0,x1,z1=v
    picks={e['data']['site']['lot_id']:(e,e['data']['presentation']['accent']) for e in entries}
    out=[f'<svg viewBox="{x0-4} {z0-4} {x1-x0+8} {z1-z0+8}" role="img" aria-label="南门到主街的原地块与四栋功能建筑位置" xmlns="http://www.w3.org/2000/svg">',
         f'<rect x="{x0-4}" y="{z0-4}" width="{x1-x0+8}" height="{z1-z0+8}" fill="#e6e7dc"/>']
    # Street fields in the original authored blueprint are not inferred from lots.
    for p in reference.get('plazas',[]):
        if p.get('poly'):
            points=' '.join(f'{x},{z}' for x,z in p['poly'])
            out.append(f'<polygon points="{points}" fill="#d0c5ac"/>')
    for s in reference.get('streets',[]):
        points=s.get('pts',[])
        if points:
            poly=' '.join(f'{p[0]},{p[1]}' for p in points)
            out.append(f'<polyline points="{poly}" fill="none" stroke="#c8bda6" stroke-width="{s.get("width",3)}"/>')
    for lot in reference['lots']:
        a,b,c,d=lot['rect'];selected=picks.get(lot['id']);color=selected[1] if selected else '#b5b9ae'
        out.append(f'<rect x="{a}" y="{b}" width="{c-a+1}" height="{d-b+1}" rx=".6" fill="{color}" stroke="#f6f4eb" stroke-width=".5"/>')
        if selected:
            e,_=selected;name=html.escape(e['data']['presentation']['name']);cx,cz=(a+c+1)/2,(b+d+1)/2
            out.append(f'<text x="{cx}" y="{cz}" fill="white" font-size="3.4" text-anchor="middle" dominant-baseline="middle">{name}</text>')
    out.append('<text x="-28" y="372" text-anchor="middle" font-size="4.5" fill="#625f52">南门广场</text>')
    out.append(f'<text x="{x1-2}" y="{z0+5}" text-anchor="end" font-size="4" fill="#625f52">北 ↑</text></svg>')
    return ''.join(out)


def overview(reference,entries,out):
    cards=[]
    for e in entries:
        d=e['data'];role=d['presentation']['role'];name=html.escape(d['presentation']['name']);summary=html.escape(d['presentation']['summary'])
        cards.append(f'<a class="card" href="{role}/index.html"><img src="{role}/full.png" alt="{name}建筑预览"><div><h2>{name}</h2><p>{summary}</p><span>打开 3D 与室内剖切 →</span></div></a>')
    page='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>云山 D1 · 功能建筑第一批</title><style>
*{box-sizing:border-box}body{margin:0;background:#eeeae0;color:#30382f;font:15px system-ui,sans-serif}main{max-width:1180px;margin:auto;padding:28px}h1{font-size:28px;margin:0 0 12px}p{line-height:1.8}a{color:#3f6654}.intro{max-width:900px}.layout{display:grid;grid-template-columns:1fr 280px;gap:22px;margin:25px 0}.cards{display:grid;grid-template-columns:1fr 1fr;gap:16px}.card{background:#faf8f0;border-radius:10px;overflow:hidden;color:inherit;text-decoration:none;border:1px solid #d0d3c7}.card img{display:block;width:100%;aspect-ratio:1.8;object-fit:cover}.card div{padding:15px}.card h2{font-size:20px;margin:0 0 6px}.card p{font-size:14px;margin:0 0 10px}.card span{font-size:13px;color:#496c55}.map{padding:16px;background:#faf8f0;border-radius:10px}.map svg{width:100%;height:455px}.map p{font-size:13px;color:#666d61}.download{display:inline-block;background:#42604d;color:white;padding:11px 18px;border-radius:7px;text-decoration:none}.status{background:#faf8f0;padding:18px;border-radius:10px;font-size:14px}.muted{font-size:13px;color:#687062}@media(max-width:850px){.layout{grid-template-columns:1fr}.map svg{height:330px}}@media(max-width:520px){main{padding:16px}.cards{grid-template-columns:1fr}h1{font-size:24px}}
</style><main><h1>云山 D1 · 功能建筑第一批</h1><p class="intro">新增百草堂、百炼铺、问道院，连同云栖客栈形成四处用途明确的建筑。全部使用原街区地块，逐栋另存；原 D1 与此前客栈版本保留。</p><p class="muted">以下是蓝图方块生成的离线预览，尚未替换游戏存档。原版设施和已有模组设施按位置布置，商人、委托与服务流程尚未接入。</p><a class="download" href="yunshan_functional_buildings_v1.zip" download>下载整批蓝图与定位说明</a><div class="layout"><div class="cards">'''+''.join(cards)+'''</div><aside class="map"><b>街区定位 · 南门至主街</b>'''+map_svg(reference,entries)+'''<p>灰色为原民居地块，彩色为本批四栋候选。位置来自原 D1 蓝图，未对当前游戏世界重新扫描。</p></aside></div><div class="status">已检查地块边界、入口和设施接近路线、屋顶截面与墙顶收口；默认版本及按地块转向的版本均逐格回读核对。实际游戏碰撞、设施交互、视觉质量与 FPS 尚待验收。</div><p class="muted">独栋蓝图包含用于清除旧屋的空气。先在世界副本核对坐标；这份压缩包不是已经替换完成的整街区存档。</p></main></html>'''
    (out/'index.html').write_text(page)


def showcase_overview(entries,out,manifest):
    cards=[]
    for e in entries:
        d=e['data'];p=d['presentation'];role=p['role'];size=d['size'];minimum=d['site']['world_min']
        cards.append(f'<article><a href="{role}/index.html"><img src="{role}/full.png" alt="{html.escape(p["name"])}"><h2>{html.escape(p["name"])}</h2></a><p>{html.escape(p["summary"])}</p><p>用地 {size[0]} × {size[2]} 格 · 主入口净高 3 格<br>样板区最小角：{minimum}</p></article>')
    page='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>云山建筑放大版</title><style>*{box-sizing:border-box}body{margin:0;background:#eeeae0;color:#30382f;font:15px system-ui}main{max-width:1100px;margin:auto;padding:24px}h1{font-size:27px}p{line-height:1.8}a{color:#3d6554}.cards{display:grid;grid-template-columns:1fr 1fr;gap:18px;margin:24px 0}article{background:#faf8f0;border-radius:10px;overflow:hidden}article p,article h2{margin:15px}article a{color:inherit;text-decoration:none}article img{display:block;width:100%;aspect-ratio:1.9;object-fit:cover}.note{padding:18px;background:#faf8f0;border-radius:9px}.button{display:inline-block;padding:10px 16px;border-radius:6px;background:#45634f;color:white;text-decoration:none}@media(max-width:600px){.cards{grid-template-columns:1fr}main{padding:15px}}</style><main><h1>云山功能建筑 · 三格入口放大版</h1><p>四栋按约 1.5 倍建筑尺度重新组织，主入口统一净高三格；床、炉、铁砧等设施保持正常大小。原小版独立保留。</p><div class="note">放大后的建筑已超出原街区小地块。这一版先在独立样板场实建；回填旧城需重排地块，不能直接覆盖原位置。下方为离线模型预览，实机产物另有运行记录。</div><p><a class="button" href="'''+manifest['bundle']+'''" download>下载放大版 authored 蓝图包</a>　<a href="runtime/index.html">查看游戏内实建与导出</a></p><div class="cards">'''+''.join(cards)+'''</div><p>尚未接入商人、收费住宿与委托。运行报告区分离线检查、真实客户端观察和玩家验收。</p></main></html>'''
    (out/'index.html').write_text(page)


def package(out,source_dir,entries,manifest):
    with zipfile.ZipFile(out/manifest.get('bundle','yunshan_functional_buildings_v1.zip'),'w',zipfile.ZIP_DEFLATED) as z:
        for e in entries:
            role=e['data']['presentation']['role'];folder=out/role
            for pattern in ('*.schem','*.nbt','*.blueprint.json','placement.json','report.json','full.png','cut.png'):
                for p in folder.glob(pattern):
                    z.write(p,f'{role}/{p.name}')
            z.write(e['source'],f'authored/{e["source"].name}')
        for p in (source_dir/manifest['site_reference'],ROOT/'tools/yunshan/README.md',out/'batch-report.json'):
            z.write(p,p.name)


def build_batch(manifest_path,out):
    manifest=json.loads(manifest_path.read_text());source_dir=manifest_path.parent
    reference=json.loads((source_dir/manifest['site_reference']).read_text())
    out.mkdir(parents=True,exist_ok=True);entries=[];seen=set()
    for filename in manifest['buildings']:
        source=source_dir/filename;data=json.loads(source.read_text());lotid=data['site']['lot_id']
        assert lotid not in seen, 'two buildings assigned to same lot'
        seen.add(lotid);placement=validate_site(data,reference)
        size,_,blocks,_=nbt.expand_structure(data);services=validate_services(data,blocks)
        if 'entrance' in data:
            e=data['entrance'];assert e['clear_height']==3
            for x in range(e['x_range'][0],e['x_range'][1]+1):
                for y in range(e['floor_y'],e['floor_y']+3):
                    assert blocks[x,y,e['z']]=='minecraft:air', f'blocked three-high entrance: {filename}'
                assert blocks[x,e['floor_y']+3,e['z']]!='minecraft:air', 'entrance lintel is missing'
        folder=out/data['presentation']['role'];report=build(source,folder)
        placement.update(export_site(data,blocks,folder));placement['services']=services
        (folder/'placement.json').write_text(json.dumps(placement,ensure_ascii=False,indent=2)+'\n')
        entries.append({'data':data,'source':source,'report':report,'placement':placement})
    report={'name':manifest['name'],'buildings':[{'id':e['data']['id'],'name':e['data']['presentation']['name'],'role':e['data']['presentation']['role'],
             'source_sha256':e['report']['source_sha256'],'placement':e['placement'],
             'walk_targets':e['report']['walk_targets'],'roof_sections':e['report']['roof_sections'],
             'roof_shell':e['report']['roof_shell'],'wall_roof_contacts':e['report']['wall_roof_contacts']} for e in entries],
             'not_verified':['actual_game_placement','player_collision','facility_interaction','NPC_services','visual_acceptance','FPS'],
             'preserved':['original_D1','south_gate_inn_v1','south_gate_inn_v2']}
    (out/'batch-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    if manifest.get('layout')=='showcase':showcase_overview(entries,out,manifest)
    else:overview(reference,entries,out)
    package(out,source_dir,entries,manifest)
    return report


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--manifest',type=Path,default=ROOT/'blueprints/yunshan/functional_buildings_v1.json')
    p.add_argument('--out',type=Path,default=ROOT/'out/preview/yunshan-functional-v1')
    args=p.parse_args();build_batch(args.manifest.resolve(),args.out.resolve())
