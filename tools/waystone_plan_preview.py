#!/usr/bin/env python3
"""只读核对八城规划占地，并生成传送石碑蓝图叠加与装饰示例。"""
from __future__ import annotations
import argparse,base64,gzip,hashlib,json,math
from html import escape
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1]
WEB=ROOT/'web/waystone-plan'
def cells(a,box,value=True):
 x0,z0,x1,z1=box;a[max(0,z0):min(a.shape[0],z1+1),max(0,x0):min(a.shape[1],x1+1)]=value

def line(a,points,width=1,value=True):
 rad=int(width)//2
 for p,q in zip(points,points[1:]):
  x0,z0=p[0],p[-1];x1,z1=q[0],q[-1];n=math.ceil(max(abs(x1-x0),abs(z1-z0),1))
  for t in range(n+1):
   x,z=round(x0+(x1-x0)*t/n),round(z0+(z1-z0)*t/n);cells(a,[x-rad,z-rad,x+rad,z+rad],value)

def mask(plans,region,central_source):
 d=json.loads((plans/f'{region}.json').read_text());w,_,h=d['dimensions'];terrain=np.array(d['terrain']).reshape(h,w);occ=np.zeros((h,w),dtype=bool);road=occ.copy();primary=occ.copy();height=terrain+1
 for plot in d['plots']:
  for z,x0,x1 in plot['cell_runs']:cells(occ,[x0-1,z-1,x1+1,z+1])
 for g in d['gardens']:cells(occ,g['bounds'])
 for key in ['retained_courtyards','source_lot_pads']:
  for c in d.get(key,[]):
   for z,x0,x1 in c.get('cell_runs',[]): cells(occ,[x0,z,x1,z])
 if d.get('landmark',{}).get('bounds'):cells(occ,d['landmark']['bounds'])
 wall=d['wall'];line(occ,wall.get('points',[]),5)
 for s in wall.get('segments',[]):line(occ,s['points'],s.get('width',3)+2)
 if wall.get('points'):
  poly=wall['points'];zz,xx=np.indices((h,w));inside=np.zeros((h,w),dtype=bool)
  for (x0,z0),(x1,z1) in zip(poly,poly[1:]):
   if z1!=z0:inside ^= ((z0>zz)!=(z1>zz)) & (xx<(x1-x0)*(zz-z0)/(z1-z0)+x0)
  occ |= ~inside
 wet=occ.copy();wet[:]=False
 if region!='zhongzhou':
  t=json.loads((ROOT/f'blueprints/province_capitals/regional/v2/cities/{region}.json').read_text())['terrain']
  if t['kind']=='connected_supported_rift_banks':
   rowx=np.interp(np.arange(h),[a[0] for a in t['rift_x_by_z']],[a[1] for a in t['rift_x_by_z']]);wet=abs(np.arange(w)[None,:]-rowx[:,None])<t['half_width']+3
  elif 'water_top_y' in t or 'sea_y' in t:wet=terrain< t.get('water_top_y',t.get('sea_y',0))
 else:
  water=np.frombuffer(gzip.decompress((central_source/'water.bin.gz').read_bytes()),dtype='<i2').reshape(h,w)
  wet=water!=-32768
 occ |= wet
 if region=='zhongzhou':
  source=json.loads((central_source/'source.json').read_text())
  offset=json.loads((ROOT/'blueprints/province_capitals/central/game-spec.json').read_text())['bounds']['source_offset']
  for tree in source['trees']:
   x,z=round(tree['x']-offset[0]),round(tree['z']-offset[2])
   if not(0<=x<w and 0<=z<h):continue
   columns=[]
   for ox,oy,oz,wood in source['tree_models'][tree['id']]['cells']:
    for _ in range((4-tree['rot'])%4):ox,oz=-oz,ox
    columns.append((x+ox,z+oz))
   if columns:
    xs,zs=zip(*columns);cells(occ,[min(xs),min(zs),max(xs),max(zs)])
  for lamp in source['lamps']:
   x,z=round(lamp['x']-offset[0]),round(lamp['z']-offset[2]);cells(occ,[x,z,x,z])
 for r in d['roads']:
  cols=r.get('surface_columns')
  if cols:
   for x,y,z in cols:
    if 0<=x<w and 0<=z<h:road[z,x]=True;height[z,x]=y
  else:
   line(road,r['points'],r.get('width',1))
   rad=int(r.get('width',1))//2
   for a,b in zip(r['points'],r['points'][1:]):
    n=math.ceil(max(abs(b[0]-a[0]),abs(b[2]-a[2]),1))
    for t in range(n+1):
     x,z=round(a[0]+(b[0]-a[0])*t/n),round(a[2]+(b[2]-a[2])*t/n);y=round(a[1]+(b[1]-a[1])*t/n)
     cells(height,[x-rad,z-rad,x+rad,z+rad],y)
  if (r['id'].startswith('replan_') or region=='zhongzhou') and r.get('width',1)>=3:
   if cols:
    for x,y,z in cols:
     if 0<=x<w and 0<=z<h:primary[z,x]=True
   else:line(primary,r['points'],r.get('width',1))
 for r in d['building_access']+d['paths']:line(road,r['points'],1)
 return d,occ,road,primary,height,wet

def simplify(points):
 """只压缩三维共线节点；不移动道路转弯。"""
 out=[]
 for p in points:
  p=[round(v,3) for v in p]
  if len(out)>1:
   a,b=out[-2:];u=[b[i]-a[i] for i in range(3)];v=[p[i]-b[i] for i in range(3)]
   if all(abs(u[i]*v[j]-u[j]*v[i])<1e-8 for i,j in [(0,1),(0,2),(1,2)]) and sum(u[i]*v[i] for i in range(3))>=0:out[-1]=p;continue
  out.append(p)
 return out

def verify(ctx,sites,rules):
 d,occ,road,primary,height,wet=ctx;h,w=occ.shape;issues=[];rows=[];used=np.zeros_like(occ)
 for s in sites:
  sid=s['id'];x,y,z=s['center'];n=s['size'];r=n//2;b=s['bounds'];route=s['approach'];found=[]
  if b!=[x-r,z-r,x+r,z+r] or n not in rules['allowed_sizes']:found.append('占地尺寸或中心不符')
  if not(0<=b[0]<=b[2]<w and 0<=b[1]<=b[3]<h):found.append('占地超出蓝图')
  else:
   pad=(slice(b[1],b[3]+1),slice(b[0],b[2]+1))
   if occ[pad].any():found.append('装饰范围与建筑、庭院、地标、城墙、水体或源树木灯柱相交')
   if road[pad].any():found.append('装饰范围侵入现有通路')
   if used[pad].any():found.append('传送点装饰占地互相重叠')
   if height[pad].max()>y or y-height[pad].min()>rules['support_limit']:found.append('平台标高超过三格支承范围')
   used[pad]=True
  if not route or route[0][1]!=y or max(abs(route[0][0]-x),abs(route[0][2]-z))!=r:found.append('接路起点不在平台边界')
  for i,(xx,yy,zz) in enumerate(route):
   if not(1<=xx<w-1 and 1<=zz<h-1):found.append('接路超出蓝图');continue
   if occ[zz-1:zz+2,xx-1:xx+2].any():found.append('三格宽接路与保护用地相交')
   if i and max(abs(xx-x),abs(zz-z))<=r:found.append('接路重新进入装饰平台')
   if road[zz,xx] and height[zz,xx]!=yy:found.append('接路改动现有道路标高')
   if not road[zz,xx] and not 0<=yy-height[zz,xx]<=rules['support_limit']:found.append('接路支承高度越界')
  for a,bp in zip(route,route[1:]):
   if abs(a[0]-bp[0])+abs(a[2]-bp[2])!=1 or abs(a[1]-bp[1])>rules['step_limit']:found.append('接路不连续或台阶超过一格')
  if route and not primary[route[-1][2],route[-1][0]]:found.append('接路未抵达公共街道')
  found=sorted(set(found));issues.extend({'site':sid,'issue':v} for v in found)
  rows.append({'id':sid,'status':'pass' if not found else 'fail','footprint':n*n,'approach_width':rules['approach_width'],'approach_steps':len(route)-1,'step_limit':rules['step_limit'],'issues':found})
 return {'status':'pass' if not issues else 'fail','issues':issues,'sites':rows,'scope':'平面占地与规划接路标高检查；非实际方块、碰撞或传送验收'}

def closest_road(d,site):
 x,y,z=site['approach'][-1];best=None
 for r in d['roads']:
  if r.get('width',1)<3:continue
  points=r.get('surface_columns',r['points']);dist=min((abs(p[0]-x)+abs(p[2]-z)+abs(p[1]-y)*2 for p in points),default=1e9)
  if best is None or dist<best[0]:best=(dist,r.get('name',r['id']))
 name=best[1]
 if name.startswith(('source_','local_','access_','original_','road_','revised_','correction_')):name='现有公共街路'
 return name

def visual_data(ctx,authored):
 d,occ,road,primary,height,wet=ctx;w,_,h=d['dimensions'];step=4 if d['region']=='zhongzhou' else 2
 ground=np.array(d['terrain']).reshape(h,w)
 v={**authored,'dimensions':d['dimensions'],'plots':[],'gardens':[],'roads':[],'walls':[],'landmark':None,'labels':[]}
 v['terrain']={'step':step,'values':ground[::step,::step].ravel().tolist(),'wet':wet[::step,::step].astype(int).ravel().tolist(),'min':int(ground.min()),'max':int(ground.max())}
 for p in d['plots']:
  b=p['building'];v['plots'].append({'id':p['id'],'name':p['name'],'role':b['role'],'runs':p['cell_runs']})
  if b['role'] in ('gate','lord_manor','market','inn','apothecary','smith','citygate','pagoda','palgate'):
   v['labels'].append({'name':p['name'].split('·')[0],'p':[b['position'][0],b['position'][2]]})
 for g in d['gardens']:v['gardens'].append({k:g[k] for k in ['id','name','bounds','access']})
 for g in d.get('retained_courtyards',[]):
  if g.get('access')=='public':v['gardens'].append({k:g[k] for k in ['id','name','bounds','access']})
 for r in d['roads']:
  if r.get('points'):v['roads'].append({'id':r['id'],'name':r.get('name','街路'),'width':r.get('width',1),'points':simplify(r['points'])})
 for p in d['building_access']+d['paths']:
  v['roads'].append({'id':p.get('id',p.get('building_id','access')),'name':'接巷','width':1,'points':simplify(p['points'])})
 if d['wall'].get('points'):v['walls'].append({'points':[[x,0,z] for x,z in d['wall']['points']],'width':2})
 for s in d['wall'].get('segments',[]):v['walls'].append({'points':s['points'],'width':s.get('width',2)})
 if d.get('landmark',{}).get('bounds'):
  v['landmark']=d['landmark'];b=d['landmark']['bounds'];v['labels'].append({'name':d['landmark']['name'],'p':[(b[0]+b[2])/2,(b[1]+b[3])/2]})
 for i,s in enumerate(v['sites'],1):
  s['index']=i;s['road_name']=closest_road(d,s);s['stairs']=any(a[1]!=b[1] for a,b in zip(s['approach'],s['approach'][1:]))
 return v

def svg(city):
 """可下载的同坐标矢量蓝图；列表与图上编号共用选址清单。"""
 W,H=1100,1050;b=city['view'];scale=min(750/(b[2]-b[0]),900/(b[3]-b[1]));tx=34-b[0]*scale;tz=80-b[1]*scale
 out=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}"><rect width="1100" height="1050" fill="#12202a"/><g font-family="Noto Sans CJK SC,Microsoft YaHei,sans-serif" fill="#e7eef2"><text x="28" y="33" font-size="22">{escape(city["name"])} · 传送点位置规划</text><text x="28" y="56" fill="#a6b8c5" font-size="12">局部坐标 · 北为 −Z · 青色框为完整装饰占地 · 规划尚未接入游戏</text></g>',f'<defs><clipPath id="mapClip"><rect x="24" y="70" width="770" height="940"/></clipPath></defs><g clip-path="url(#mapClip)"><g transform="translate({tx} {tz}) scale({scale})">']
 for p in city['plots']:
  color='#80665b' if p['role']=='lord_manor' else '#506775'
  out.append(f'<g fill="{color}">')
  for z,x0,x1 in p['runs']:out.append(f'<rect x="{x0}" y="{z}" width="{x1-x0+1}" height="1"/>')
  out.append('</g>')
 for g in city['gardens']:
  x0,z0,x1,z1=g['bounds'];c='#3b6658' if g['access']=='private' else '#887759';out.append(f'<rect x="{x0}" y="{z0}" width="{x1-x0+1}" height="{z1-z0+1}" fill="{c}"/>')
 if city['landmark']:
  x0,z0,x1,z1=city['landmark']['bounds'];out.append(f'<rect x="{x0}" y="{z0}" width="{x1-x0+1}" height="{z1-z0+1}" fill="#89685e"/>')
 for r in city['roads']:
  pts=' '.join(f'{p[0]},{p[2]}' for p in r['points']);out.append(f'<polyline points="{pts}" fill="none" stroke="#b99e6e" stroke-width="{r["width"]}" stroke-linejoin="round"/>')
 for r in city['walls']:
  pts=' '.join(f'{p[0]},{p[2]}' for p in r['points']);out.append(f'<polyline points="{pts}" fill="none" stroke="#99acb8" stroke-width="{r["width"]}"/>')
 for s in city['sites']:
  x,y,z=s['center'];n=s['size'];bb=s['bounds'];pts=' '.join(f'{p[0]},{p[2]}' for p in s['approach']);out.append(f'<polyline points="{pts}" fill="none" stroke="#73dfc6" stroke-width="3"/><rect x="{bb[0]}" y="{bb[1]}" width="{n}" height="{n}" fill="#61d7c8" fill-opacity=".5" stroke="#92f0dd" stroke-width="{1.6/scale}"/>')
 out.append('</g></g><g font-family="Noto Sans CJK SC,Microsoft YaHei,sans-serif">')
 for s in city['sites']:
  x,y,z=s['center'];px=tx+(x+.5)*scale;pz=tz+(z+.5)*scale;out.append(f'<circle cx="{px}" cy="{pz}" r="13" fill="#a1ebd8"/><text x="{px}" y="{pz+4}" font-size="12" text-anchor="middle" fill="#152832">{s["index"]}</text>')
  yy=105+(s['index']-1)*123;out.append(f'<text x="820" y="{yy}" fill="#e5edf3" font-size="15">{s["index"]}. {escape(s["name"])}</text><text x="820" y="{yy+25}" fill="#a5c5c6" font-size="13">X {x} / Y {y} / Z {z}</text><text x="820" y="{yy+48}" fill="#afbec8" font-size="12">占地 {s["size"]} × {s["size"]} 格</text><text x="820" y="{yy+70}" fill="#afbec8" font-size="12">接路宽 3 格，长 {len(s["approach"])-1} 格</text>')
 out.append('<text x="820" y="935" fill="#a1bac7" font-size="12">中央 5 × 5 格保留到达净空</text><text x="820" y="957" fill="#a1bac7" font-size="12">柱、灯、座椅均在范围内布置</text><text x="820" y="979" fill="#a1bac7" font-size="12">世界坐标须叠加城市原点与旋转</text></g></svg>')
 return ''.join(out)

def build(plans,central_source,out,base):
 if not base.startswith('https://assets.ishiki333hhh.win/'):raise ValueError('预览资源必须使用既定 HTTPS 域名')
 proposal=json.loads((WEB/'sites.json').read_text());catalog=json.loads((ROOT/'blueprints/province_capitals/blueprint_plan/catalog.json').read_text())
 if {c['id'] for c in proposal['cities']}!={c['region'] for c in catalog['cities']}:raise ValueError('必须覆盖八城')
 payload={'base':base.rstrip('/')+'/','rules':proposal['constraints'],'cities':[],'evidence':proposal['evidence']};checks={'status':'pass','cities':{},'issues':[],'evidence':{'minecraft':'not_run','owner_visual_acceptance':'not_verified','runtime_placement':'not_implemented','props_and_tree_clearance':'not_verified'}}
 if proposal['constraints']['approach_width']!=3 or proposal['constraints']['arrival_square']!=5:raise ValueError('当前示例几何仅实现三格接路与五格到达区')
 out.mkdir(parents=True,exist_ok=True);identities={}
 for c in proposal['cities']:
  d=json.loads((plans/f'{c["id"]}.json').read_text())
  identities[c['id']]={'plan_sha256':hashlib.sha256((plans/f'{c["id"]}.json').read_bytes()).hexdigest(),'spatial_sources':d['planning_source_hashes'],'reuse':d.get('planning_reuse')}
  for name,value in d['planning_source_hashes'].items():
   if hashlib.sha256((ROOT/name).read_bytes()).hexdigest()!=value:raise ValueError('蓝图输入变化：'+name)
  ctx=mask(plans,c['id'],central_source);check=verify(ctx,c['sites'],proposal['constraints']);checks['cities'][c['id']]=check
  if check['issues']:checks['issues'].extend({'city':c['id'],**x} for x in check['issues'])
  v=visual_data(ctx,c);payload['cities'].append(v);(out/(c['id']+'.svg')).write_text(svg(v))
 if checks['issues']:checks['status']='fail'
 checks['selected']=sum(len(c['sites']) for c in proposal['cities']);checks['completed']=sum(s['status']=='pass' for c in checks['cities'].values() for s in c['sites'])
 checks['central_source_sha256']=hashlib.sha256((central_source/'source.json').read_bytes()).hexdigest()
 checks['water_source_sha256']=hashlib.sha256((central_source/'water.bin.gz').read_bytes()).hexdigest();checks['source_identity']=identities
 (out/'checks.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2)+'\n')
 public={**proposal,'cities':[{k:v for k,v in c.items() if k not in ('terrain','plots','gardens','roads','walls','landmark','labels')} for c in payload['cities']]};(out/'plan.json').write_text(json.dumps(public,ensure_ascii=False,indent=2)+'\n')
 packed=base64.b64encode(gzip.compress(json.dumps({'data':payload,'viewer':(WEB/'viewer.js').read_text()},ensure_ascii=False,separators=(',',':')).encode(),mtime=0)).decode()
 boot="""(async()=>{try{const bytes=Uint8Array.from(atob('PAYLOAD'),x=>x.charCodeAt(0)),p=JSON.parse(await new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip'))).text());new Function('DATA',p.viewer)(p.data);}catch(e){document.getElementById('loading').innerHTML='预览载入失败：'+e.message;console.error(e);}})();""".replace('PAYLOAD',packed)
 page=(WEB/'viewer.html').read_text().replace('__BOOTSTRAP__',boot)
 (out/'review.html').write_text(page)
 standalone=page.replace('</head>','<style>:root{--foreground:#e8edf1;--card:#18232c;--border:#34444f;--secondary:#263640;--primary:#b8dfe6;--primary-foreground:#142329;--ring:#7fdbe3}html{background:#101820;color-scheme:dark}body{max-width:1600px;margin:0 auto;padding:24px}@media(max-width:530px){body{padding:12px}}</style></head>')
 (out/'index.html').write_text(standalone)
 print(json.dumps({'status':checks['status'],'selected':checks['selected'],'completed':checks['completed'],'issues':checks['issues'],'html_bytes':len(page.encode()),'out':str(out)},ensure_ascii=False))
 return 0 if not checks['issues'] else 1

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--plans',type=Path,required=True);p.add_argument('--central-source',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--base',required=True);a=p.parse_args();raise SystemExit(build(a.plans,a.central_source,a.out,a.base))
