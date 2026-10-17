#!/usr/bin/env python3
"""Render the authored D1 revision and check its exterior planning grid.

This produces a planning blueprint, never blocks or a world-editing command.
Template dimensions and orientation come from their owning authored inputs.
"""
from __future__ import annotations

import argparse
import base64
from collections import deque
from html import escape
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path):
    return json.loads(path.read_text())


def rect_cells(r):
    return {(x, z) for x in range(r[0], r[2]+1) for z in range(r[1], r[3]+1)}


def inside(x, z, poly):
    hit = False
    for (a,b),(c,d) in zip(poly, poly[1:]+poly[:1]):
        if (b>z) != (d>z) and x < a+(z-b)*(c-a)/(d-b):
            hit = not hit
    return hit


def polygon_cells(poly):
    return {(x,z) for x in range(min(p[0] for p in poly), max(p[0] for p in poly))
            for z in range(min(p[1] for p in poly), max(p[1] for p in poly))
            if inside(x+.5,z+.5,poly)}


def street_cells(st):
    cells, best = {}, {}
    h = (st['width']-1)//2
    assert st['width'] > 0 and st['width'] % 2 == 1
    for si,(a,b) in enumerate(zip(st['pts'],st['pts'][1:])):
        ax,az,al=a; bx,bz,bl=b
        assert ax==bx or az==bz, f"Diagonal street: {st['id']}"
        n=abs(bx-ax)+abs(bz-az)
        assert n and abs(bl-al)<=n, f"Unwalkable slope: {st['id']}"
        dx=(bx>ax)-(bx<ax); dz=(bz>az)-(bz<az)
        for x in range(min(ax,bx)-h,max(ax,bx)+h+1):
            for z in range(min(az,bz)-h,max(az,bz)+h+1):
                u=(x-ax)*dx+(z-az)*dz; t=max(0,min(n,u))
                rank=((x-ax-dx*t)**2+(z-az-dz*t)**2, u!=t, -si)
                if (x,z) not in best or rank<best[x,z]:
                    best[x,z]=rank
                    cells[x,z]=al+((bl>al)-(bl<al))*int(t*abs(bl-al)/n+.5)
    return cells


def rotated(x,z,w,d,q):
    return [(x,z),(d-1-z,x),(w-1-x,d-1-z),(z,w-1-x)][q]


def prepare(source):
    plan=read(source); parent=source.parent
    site=read(parent/plan['site_reference']); land=read(parent/plan['landscape_reference'])
    buildings=[]
    for spec in plan['buildings']:
        b=dict(spec); template=read(parent/b['source']); w,h,d=template['size']; q=b['rotation_quarters']
        assert q in range(4)
        sx,sz=(w,d) if q%2==0 else (d,w); x,z=b['min']
        b['rect']=[x,z,x+sx-1,z+sz-1]; b['size']=[sx,h,sz]
        entry=template['entrance']; ex=sum(entry['x_range'])//2
        dx,dz=rotated(ex,entry['z'],w,d,q)
        b['door']=[x+dx,z+dz]
        dx,dz=rotated(ex,0,w,d,q); fx,fz=[(0,-1),(1,0),(0,1),(-1,0)][q]
        b['approach']=[x+dx+fx,z+dz+fz]; b['facing']=['北','东','南','西'][q]
        ox,oy,oz=site['world_offset']
        b['proposed_world_min']=[x+ox,b['level']+oy-1,z+oz]
        b['threshold_world_y']=b['level']+oy-1+entry['floor_y']
        buildings.append(b)
    replacements={s['id']:s for s in plan['replace_streets']}
    assert set(replacements)<=set(s['id'] for s in site['streets'])
    streets=[replacements.get(s['id'],s) for s in site['streets']]+plan['add_streets']
    removed=[i for b in buildings for i in b['remove_lots']]
    assert len(removed)==len(set(removed)), 'A lot is assigned to two replacements'
    assert set(removed)<=set(l['id'] for l in site['lots'])
    retained=[l for l in site['lots'] if l['id'] not in removed]
    return plan,site,land,buildings,streets,retained


def validate(plan,site,land,buildings,streets,retained):
    checks=[]
    def check(name,ok,detail=None):
        checks.append(dict(name=name,passed=bool(ok),detail=detail))
    water=set().union(*(rect_cells(r) for r in land['water_rects']))
    vx,vz,xx,zz=land['view']
    extent=rect_cells([vx,vz,xx-1,zz-1])
    road_maps=[street_cells(s) for s in streets]
    roads=set().union(*(set(m) for m in road_maps))
    plazas={p:v['level'] for v in site['plazas'] for p in polygon_cells(v['poly'])}
    courts={p:v['level'] for v in plan['forecourts'] for p in rect_cells(v['rect'])}
    walking=dict(plazas)
    for m in road_maps: walking.update(m)
    walking.update(courts)
    occupied=set(); parcels=set()
    for b in buildings:
        cells=rect_cells(b['rect']); parcel=rect_cells(b['site'])
        check(b['key']+': footprint inside site',cells<=parcel)
        check(b['key']+': site inside district',parcel<=extent)
        check(b['key']+': no building overlap',not cells&occupied)
        check(b['key']+': no site overlap',not parcel&parcels)
        check(b['key']+': no water reclamation',not parcel&water)
        check(b['key']+': no street or plaza blocked',not cells&(roads|set(plazas)|set(courts)))
        clashes=[l['id'] for l in retained if parcel&rect_cells(l['rect'])]
        check(b['key']+': retained lots clear',not clashes,clashes)
        p=tuple(b['approach'])
        check(b['key']+': level entrance approach',walking.get(p)==b['level'],
              dict(at=p,expected=b['level'],actual=walking.get(p)))
        occupied|=cells; parcels|=parcel
    changed_roads=plan['replace_streets']+plan['add_streets']
    for st in changed_roads:
        cells=set(street_cells(st))
        clashes=[l['id'] for l in retained if cells&rect_cells(l['rect'])]
        check(st['id']+': retained lots clear',not clashes,clashes)
    for c in plan['forecourts']:
        clashes=[l['id'] for l in retained if rect_cells(c['rect'])&rect_cells(l['rect'])]
        check(c['id']+': retained lots clear',not clashes,clashes)
    for l in retained:
        occupied|=rect_cells(l['house'])
    walking={p:y for p,y in walking.items() if p not in occupied}
    start=tuple(plan['gate_start']); parent={start:None}; q=deque([start])
    assert start in walking
    while q:
        x,z=q.popleft()
        for p in [(x-1,z),(x+1,z),(x,z-1),(x,z+1)]:
            if p in walking and p not in parent and abs(walking[p]-walking[x,z])<=1:
                parent[p]=(x,z); q.append(p)
    routes={}
    for key,target in [(b['key'],b['approach']) for b in buildings]+[('market',plan['market_target'])]:
        p=tuple(target); check(key+': gate reachable on height grid',p in parent)
        path=[]
        while p in parent:
            path.append([*p,walking[p]]); p=parent[p]
        routes[key]=path[::-1]
    for b in buildings:
        check(b['key']+': no landmark removed',not any(
            rect_cells(b['site'])&rect_cells(s['rect']) for s in land['spots'] if s['kind']=='landmark'))
    return {'passed':all(c['passed'] for c in checks),'checks':checks,'counts':{
        'original_lots':len(site['lots']),'removed_lots':len(site['lots'])-len(retained),
        'retained_lots':len(retained),'new_buildings':len(buildings),'total_buildings':len(retained)+len(buildings)},
        'routes':routes,'evidence_scope':'Exterior 2.5D planning grid only; not Minecraft collision or live terrain proof.',
        'not_verified':plan['not_verified']}


def svg_map(plan,site,land,buildings,streets,retained,report):
    parts=['<svg xmlns="http://www.w3.org/2000/svg" viewBox="-149 133 222 278" role="img" aria-label="云山四馆入城规划总图">',
           '<defs><pattern id="grid" width="10" height="10" patternUnits="userSpaceOnUse"><path d="M10 0H0V10" fill="none" stroke="#8b9b80" stroke-width=".12"/></pattern></defs>',
           '<rect x="-149" y="133" width="222" height="278" fill="#edf0df"/>']
    def rect(r,fill,extra=''):
        parts.append(f'<rect x="{r[0]}" y="{r[1]}" width="{r[2]-r[0]+1}" height="{r[3]-r[1]+1}" fill="{fill}" {extra}/>')
    def label(x,z,t,size=3.2,color='#34433b',extra=''):
        parts.append(f'<text x="{x}" y="{z}" text-anchor="middle" fill="{color}" font-size="{size}" {extra}>{escape(t)}</text>')
    def line(pts,color,width,extra=''):
        coords=' '.join(f'{p[0]+.5},{p[1]+.5}' for p in pts)
        parts.append(f'<polyline points="{coords}" fill="none" stroke="{color}" stroke-width="{width}" stroke-linejoin="round" {extra}/>')
    for t in land['terraces']:
        level=t['level']; light=84-(level-38)*.6
        pts=' '.join(f'{x},{z}' for x,z in t['poly'])
        parts.append(f'<polygon points="{pts}" fill="hsl(91 22% {light}%)" stroke="#a4b494" stroke-width=".2"/>')
    parts.append('<g shape-rendering="crispEdges">')
    for r in land['water_rects']: rect(r,'#aacbd0')
    parts.append('</g>')
    parts.append('<rect x="-145" y="140" width="210" height="265" fill="url(#grid)"/>')
    if land['south_wall']:
        line(land['south_wall'],'#7a7972',5)
    if land.get('causeway'):
        line(land['causeway'],'#af9065',2.3)
    for p in site['plazas']:
        pts=' '.join(f'{x},{z}' for x,z in p['poly'])
        parts.append(f'<polygon points="{pts}" fill="#f1e5c7" stroke="#b8a47c" stroke-width=".25"/>')
    for st in site['streets']:
        cls='original' if st['id'] in {s['id'] for s in plan['replace_streets']} else ''
        parts.append(f'<g class="{cls}">'); line(st['pts'],'#b6a17d',st['width']+.5);line(st['pts'],'#e4d5b5',st['width']);parts.append('</g>')
    kept={l['id'] for l in retained}
    for l in site['lots']:
        cls='' if l['id'] in kept else 'original old-lot'
        parts.append(f'<g class="{cls}"><title>{escape(l["id"])}</title>')
        rect(l['rect'],'#d7d0b8');rect(l['house'],'#a9a79a','stroke="#777c70" stroke-width=".3"')
        hx,hz,xx,zz=l['house']
        if l['facing'] in ['N','S']: line([[hx+1,(hz+zz)/2],[xx-1,(hz+zz)/2]],'#dadbd1',.4)
        else: line([[(hx+xx)/2,hz+1],[(hx+xx)/2,zz-1]],'#dadbd1',.4)
        parts.append('</g>')
    parts.append('<g class="proposal">')
    for b in buildings:
        rect(b['site'],b['color'],'fill-opacity=".12" stroke="'+b['color']+'" stroke-width=".35" stroke-dasharray="1.3 1"')
    for c in plan['forecourts']:rect(c['rect'],'#f3d6a4')
    for garden in plan['gardens']:
        rect(garden['rect'],garden['color']);r=garden['rect'];label((r[0]+r[2])/2,(r[1]+r[3])/2+1,garden['name'],2.6)
    for st in plan['replace_streets']+plan['add_streets']:
        line(st['pts'],'#ab7948',st['width']+.6);line(st['pts'],'#f3d6a4',st['width'])
        for a,b in zip(st['pts'],st['pts'][1:]):
            if a[2]!=b[2]:
                for k in range(1,abs(b[2]-a[2])+1):
                    t=k/abs(b[2]-a[2]);x=a[0]+(b[0]-a[0])*t;z=a[1]+(b[1]-a[1])*t
                    line([[x,z-1],[x,z+1]],'#8a5a32',.25)
    for b in buildings:
        r=b['rect']; x=(r[0]+r[2]+1)/2;z=(r[1]+r[3]+1)/2
        parts.append(f'<g class="building" data-key="{b["key"]}" tabindex="0" role="button" aria-label="{b["name"]}">')
        rect(r,b['color'],'stroke="#fff8e7" stroke-width=".65"')
        label(x,z-3,b['number'],4.5,'white','font-weight="bold"');label(x,z+1,b['name'],3.1,'white')
        label(x,z+5,'L'+str(b['level']),2.5,'#f2f3e8')
        ax,az=b['approach'];parts.append(f'<circle cx="{ax+.5}" cy="{az+.5}" r="1.25" fill="white" stroke="{b["color"]}" stroke-width=".5"/>')
        parts.append('</g>')
    parts.append('</g><g class="diff">')
    for l in site['lots']:
        if l['id'] not in kept: rect(l['rect'],'none','stroke="#b64343" stroke-width=".5" stroke-dasharray="1 1"')
    parts.append('</g>')
    for sp in land['spots']:
        if sp['kind']=='landmark':rect(sp['rect'],'#6f6653','stroke="#f8f5e9" stroke-width=".5"')
        if sp['kind']=='tree':
            x,z=sp['at'];parts.append(f'<circle cx="{x}" cy="{z}" r="1.8" fill="#789c70" stroke="#587a58" stroke-width=".3"/>')
    g=land['site']['gate'];rect([g['x']-g['w']/2,g['z']-g['d']/2,g['x']+g['w']/2,g['z']+g['d']/2],'#797461')
    rect([-34,376,-27,395],'#e4d5b5');label(-30,400,'正阳门',4)
    label(-60,174,'鼓楼',3,'white');label(-33,182,'市集广场',4.1)
    label(24,263,'镜  湖',6,'#456f78');label(-111,235,'西坡住区',3.6)
    label(-32,368,'门内广场',3);label(-39,279,'御街',3)
    label(-28,244,'九曲桥头',2.7);label(-21,314,'望湖正巷',2.6)
    parts.append('<g class="routes">')
    for key,route in report['routes'].items():
        if key!='market':line([[x,z] for x,z,y in route],'#275c68',.8,'stroke-dasharray="1.2 1.1"')
    parts.append('</g>')
    line([[51,156],[51,145]],'#34433b',.6);label(51,142,'北 N',3)
    line([[23,390],[63,390]],'#34433b',.6)
    for x in [23,43,63]:line([[x,389],[x,391]],'#34433b',.5)
    label(43,395,'40 格 / 米',3)
    parts.append('</svg>')
    return ''.join(parts)


def build(source,out):
    plan,site,land,buildings,streets,retained=prepare(source)
    report=validate(plan,site,land,buildings,streets,retained)
    out.mkdir(parents=True,exist_ok=True)
    (out/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    if not report['passed']:
        raise ValueError(json.dumps([c for c in report['checks'] if not c['passed']],ensure_ascii=False))
    resolved={'name':plan['name'],'status':plan['status'],'coordinate_convention':plan['coordinate_convention'],
              'world_offset':site['world_offset'],'buildings':buildings,'streets':streets,'plazas':site['plazas'],
              'forecourts':plan['forecourts'],'gardens':plan['gardens'],'retained_lots':retained,'not_verified':plan['not_verified']}
    (out/'layout.json').write_text(json.dumps(resolved,ensure_ascii=False,indent=2)+'\n')
    svg=svg_map(plan,site,land,buildings,streets,retained,report)
    standalone=svg.replace('<defs>','<style>.original,.diff,.routes{display:none}text{font-family:"WenQuanYi Zen Hei",sans-serif}</style><defs>',1)
    (out/'blueprint.svg').write_text(standalone)
    for b in buildings:
        p=ROOT/'out/preview'/b['preview']
        b['image']='data:image/png;base64,'+base64.b64encode(p.read_bytes()).decode() if p.exists() else ''
    page=(Path(__file__).with_name('district_viewer.html').read_text()
          .replace('__MAP__',svg).replace('__DATA__',json.dumps({'plan':plan,'buildings':buildings,'report':report},ensure_ascii=False)))
    (out/'index.html').write_text(page)
    print(json.dumps({'out':str(out),'passed':report['passed'],'checks':len(report['checks']),**report['counts']},ensure_ascii=False))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,default=ROOT/'blueprints/yunshan/district_plan_v2.json')
    parser.add_argument('--out',type=Path,default=ROOT/'out/preview/yunshan-d1-v2')
    args=parser.parse_args();build(args.source,args.out)
