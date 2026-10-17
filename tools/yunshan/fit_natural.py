#!/usr/bin/env python3
"""Fit the accepted Yunshan core into a surveyed natural world, without its slab.

Survey arrays are z-major, x-fastest; every *_y height is a FEET height.
Candidate origin is the minimum x/z of the rotated original 210x265 window.
Pure Python, no Minecraft, Gradle, network, or ignored historical inputs.
"""
from __future__ import annotations
import argparse
from collections import Counter, defaultdict
from functools import lru_cache
import hashlib
import heapq
import json
import math
from pathlib import Path
import sys
import time

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
import json_to_nbt as nbt
import voxel_dump_to_structures as export
from yunshan import build_district as city
from yunshan.build_functional import rotated_state
from yunshan.build_inn import sign_data
from yunshan.plan_district import rect_cells,street_cells,polygon_cells

DEFAULT_CONFIG=ROOT/'blueprints/yunshan/natural_fit.json'


def load(value):
    return value if isinstance(value,dict) else json.loads(Path(value).read_text())


def config_data(config=None):
    return load(config or DEFAULT_CONFIG)


@lru_cache(maxsize=2)
def source_data(source_config):
    return city.realize(Path(source_config))


class Survey:
    def __init__(self,data):
        self.data=load(data);self.window=self.data['window']
        self.x0,self.z0,self.x1,self.z1=self.window
        self.width=self.x1-self.x0;self.depth=self.z1-self.z0
        if (self.width,self.depth)!=(self.data['width'],self.data['depth']):raise ValueError('survey dimensions disagree')
        n=self.width*self.depth
        for key in ('ground_y','surface_y','water_y','ground_index'):
            if len(self.data[key])!=n:raise ValueError('survey array size: '+key)
        self.ground=self.data['ground_y'];self.surface=self.data['surface_y'];self.water=self.data['water_y']
        if any(not isinstance(y,int) or not -63<=y<=320 for y in self.ground):raise ValueError('invalid ground feet')

    def index(self,x,z):
        if not(self.x0<=x<self.x1 and self.z0<=z<self.z1):raise ValueError(f'survey margin missing at {x},{z}')
        return (z-self.z0)*self.width+x-self.x0

    def pos(self,k):return self.x0+k%self.width,self.z0+k//self.width

    def neighbors(self,k):
        x=k%self.width;z=k//self.width
        if x:yield k-1
        if x+1<self.width:yield k+1
        if z:yield k-self.width
        if z+1<self.depth:yield k+self.width

    def state(self,k):return self.data['ground_palette'][self.data['ground_index'][k]]


class Transform:
    def __init__(self,candidate,source_min=(2850,62,110),size=(210,82,265)):
        self.candidate=candidate;self.q=candidate['rotation_quarters']
        if self.q not in (0,1,2,3):raise ValueError('rotation_quarters must be 0..3')
        self.origin=candidate['origin'];self.dy=candidate['dy'];self.source_min=source_min;self.size=size
        if candidate['water_surface']-68!=self.dy:raise ValueError('dy must align original lake feet 68 to native water feet')

    def xz(self,x,z):
        w,_,d=self.size
        x-=self.source_min[0];z-=self.source_min[2]
        for _ in range(self.q):x,z=d-1-z,x;w,d=d,w
        return self.origin[0]+x,self.origin[1]+z

    def point(self,p,continuous=False):
        # Continuous point rotation acts on block boundaries; integer rotation
        # acts on block indices. This is crucial for camera/player centering.
        w,_,d=self.size;x=p[0]-self.source_min[0];z=p[2]-self.source_min[2]
        for _ in range(self.q):
            x,z=d-z if continuous else d-1-z,x
            w,d=d,w
        return [self.origin[0]+x,p[1]+self.dy,self.origin[1]+z]

    def vector(self,dx,dz):
        for _ in range(self.q):dx,dz=-dz,dx
        return dx,dz


def source_core(data,cfg):
    _,plan,site,land,volume,levels,kinds,walking,buildings,houses,signs,planning=data
    ox,oy,oz=site['world_offset']
    core=set()
    for h in houses:core.update((p[0],p[2]) for p in h['_expected'])
    for lot in site['lots']:
        if any(h['id']==lot['id'] for h in houses):core.update((x+ox,z+oz) for x,z in rect_cells(lot['rect']))
    for b in buildings:core.update((x+ox,z+oz) for x,z in rect_cells(b['site']))
    core.update((x+ox,z+oz) for x,z in walking)
    for path in planning['routes'].values():core.update((x+ox,z+oz) for x,z,y in path)
    for r in [cfg['preserve']['gate_rect'],*cfg['preserve']['wall_rects']]:
        core.update((x+ox,z+oz) for x,z in rect_cells(r))
    for spot in land['spots']:
        if spot['kind']=='landmark':
            r=spot['rect'];pad=cfg['preserve']['landmark_padding']
            core.update((x+ox,z+oz) for x,z in rect_cells([r[0]-pad,r[1]-pad,r[2]+pad,r[3]+pad]))
        elif spot['kind'] in ('tree','well'):
            x,z=spot['at'];r=cfg['preserve']['prop_radius']
            core.update((a+ox,b+oz) for a,b in rect_cells([x-r,z-r,x+r,z+r]))
    core.update((x+ox,z+oz) for (x,z),kind in kinds.items() if kind=='bridge')
    core={p for p in core if volume.minimum[0]<=p[0]<=volume.maximum[0] and volume.minimum[2]<=p[1]<=volume.maximum[2]}
    wet={(x+ox,z+oz) for (x,z),kind in kinds.items() if kind=='water'}
    return core,wet


def distance_field(survey,anchors,cfg):
    n=len(survey.ground);distance=[math.inf]*n;nearest=[None]*n;queue=[]
    for k,y in anchors.items():distance[k]=0;nearest[k]=y;queue.append((0,k))
    heapq.heapify(queue);radius=cfg['blend']['radius'];slope_cost=cfg['blend']['natural_slope_cost']
    while queue:
        dist,k=heapq.heappop(queue)
        if dist!=distance[k] or dist>radius:continue
        for j in survey.neighbors(k):
            nd=dist+1+slope_cost*abs(survey.ground[j]-survey.ground[k])
            if nd<distance[j] and nd<=radius:
                distance[j]=nd;nearest[j]=nearest[k];heapq.heappush(queue,(nd,j))
    return distance,nearest


def road_route(survey,after,changed,core,wet,transform,cfg):
    spec=cfg['outgoing_road'];sx,sz=spec['source_start_artwork']
    offset=source_data(str(ROOT/'blueprints/yunshan'/cfg['source_config']))[2]['world_offset']
    start=survey.index(*transform.xz(sx+offset[0],sz+offset[2]))
    dx,dz=transform.vector(*spec['direction_artwork']);startpos=survey.pos(start)
    limit=spec['search_length'];queue=[(0,start)];cost={start:0};parent={start:None};target=None
    tail=spec['minimum_untouched_tail']
    while queue:
        score,k=heapq.heappop(queue)
        if score!=cost[k]:continue
        x,z=survey.pos(k);advance=(x-startpos[0])*dx+(z-startpos[1])*dz
        # A chain of flat, clear, entirely untouched native ground is the
        # connection target. Its final six cells are left completely unedited.
        if advance>=cfg['gates']['minimum_outgoing_length'] and not changed[k] and k not in core and k not in wet:
            chain=[];p=k
            for _ in range(tail):
                if p is None:break
                chain.append(p);p=parent[p]
            if len(chain)==tail and all(not changed[p] and survey.water[p] is None and survey.surface[p]<=survey.ground[p]
                                        and after[p]==after[k] for p in chain):target=k;break
        for j in survey.neighbors(k):
            xx,zz=survey.pos(j)
            if abs(xx-startpos[0])+abs(zz-startpos[1])>limit or j in wet or survey.water[j] is not None or (j in core and j!=start):continue
            if abs(after[j]-after[k])>spec['maximum_grade']:continue
            if xx in (survey.x0,survey.x1-1) or zz in (survey.z0,survey.z1-1):continue
            progress=(xx-startpos[0])*dx+(zz-startpos[1])*dz
            if progress < -spec['maximum_backtrack']:continue
            step=spec['cost_distance']+spec['cost_rise']*abs(after[j]-after[k])
            step+=spec['cost_turn']*(1-((xx-x)*dx+(zz-z)*dz))
            if survey.water[j] is not None:step+=spec['cost_water']
            nc=score+step
            if nc<cost.get(j,math.inf):cost[j]=nc;parent[j]=k;heapq.heappush(queue,(nc,j))
    if target is None:raise ValueError('no walkable outgoing road reaches clear untouched natural terrain')
    route=[]
    while target is not None:route.append(target);target=parent[target]
    return route[::-1]


def plan_fit(survey,candidate,config=None):
    cfg=config_data(config);s=Survey(survey);candidate=load(candidate)
    source_config=ROOT/'blueprints/yunshan'/cfg['source_config'];data=source_data(str(source_config))
    volume=data[4];source_levels=data[5];source_kinds=data[6];ox,oy,oz=data[2]['world_offset']
    t=Transform(candidate,volume.minimum,volume.size);src_core,src_wet=source_core(data,cfg)
    core={s.index(*t.xz(x,z)):(x,z) for x,z in src_core}
    wet={s.index(*t.xz(x,z)) for x,z in src_wet}-set(core)
    plane=candidate['water_surface'];bed=plane-cfg['lake']['bed_depth']
    hard={k:source_levels[x-ox,z-oz]+oy+t.dy for k,(x,z) in core.items()}
    # bridge columns have a deck above the lake; underlying earth is still bed.
    for k,(x,z) in core.items():
        if source_kinds[x-ox,z-oz]=='bridge':hard[k]=max(hard[k],plane+1)
    anchors=dict(hard);anchors.update({k:plane+1 for k in wet})
    distance,nearest=distance_field(s,anchors,cfg)
    before=list(s.ground);after=before[:];influence=[False]*len(before)
    radius=cfg['blend']['radius'];wave=cfg['blend']['warp_wavelength'];amp=cfg['blend']['warp_amplitude']
    for k,distance_k in enumerate(distance):
        if k in hard:after[k]=hard[k];influence[k]=True;continue
        if k in wet:after[k]=bed;influence[k]=True;continue
        if distance_k==math.inf:continue
        x,z=s.pos(k)
        scale=cfg['blend']['minimum_radius_fraction']+amp*(1+math.sin(x/wave)*math.cos(z/(wave*.79)))
        r=min(radius,radius*scale)
        if distance_k>=r:continue
        if s.water[k] is not None:
            # Native lakes, rivers and ocean outside the authored basin survive.
            continue
        u=1-distance_k/r;weight=u*u*(3-2*u)
        after[k]=round(before[k]+(nearest[k]-before[k])*weight)
        influence[k]=after[k]!=before[k]
    # Dry land immediately bounding the fixed lake must contain its water.
    bank=set()
    for k in wet:
        for j in s.neighbors(k):
            if j not in wet and j not in core and s.water[j] is None:
                bank.add(j);after[j]=max(after[j],plane+cfg['blend']['bank_freeboard']);influence[j]=True
    # Constrained relaxation removes sharp seams through the irregular blend,
    # while native outer boundary and the complete authored core remain fixed.
    maximum=cfg['blend']['maximum_ground_step']
    adjustable=[k for k in range(len(after)) if influence[k] and k not in core and k not in wet and k not in bank]
    for _ in range(cfg['blend']['relaxation_passes']):
        updates=[]
        for k in adjustable:
            ns=[after[j] for j in s.neighbors(k) if j not in wet]
            if not ns:continue
            lo,hi=min(ns),max(ns)
            value=after[k]
            if value>lo+maximum:value=max(lo+maximum,value-1)
            elif value<hi-maximum:value=min(hi-maximum,value+1)
            if value!=after[k]:updates.append((k,value))
        if not updates:break
        for k,y in updates:after[k]=y
    changed=[after[k]!=before[k] or k in core or k in wet for k in range(len(after))]
    route_error=None
    try:route=road_route(s,after,changed,set(core),wet,t,cfg)
    except ValueError as error:route=[];route_error=str(error)
    path_surface={};path_clear=set();half=cfg['outgoing_road']['width']//2
    untouched_tail=cfg['outgoing_road']['minimum_untouched_tail']
    center_levels={k:after[k] for k in route}
    tail_guard=set(route[-untouched_tail:])
    for k in route[:-untouched_tail]:
        x,z=s.pos(k)
        for xx in range(x-half,x+half+1):
            for zz in range(z-half,z+half+1):
                j=s.index(xx,zz)
                if j in core or j in tail_guard:continue
                if j in wet:raise ValueError('outgoing road width enters retained water')
                after[j]=center_levels.get(j,center_levels[k]);changed[j]=True;path_clear.add(j)
                path_surface[j]=cfg['materials']['road']
    for a,b in zip(route,route[1:]):
        if after[a]==after[b]:continue
        low,high=(a,b) if after[a]<after[b] else (b,a)
        if high not in path_clear:continue
        lx,lz=s.pos(low);hx,hz=s.pos(high)
        state=cfg['materials']['road_stairs']+f'[facing={city.FACE[hx-lx,hz-lz]},half=bottom]'
        x,z=s.pos(high)
        for xx in range(x-half,x+half+1):
            for zz in range(z-half,z+half+1):
                j=s.index(xx,zz)
                if j in path_clear and after[j]==after[high]:path_surface[j]=state
    # Add same-level natural water to the containment check only; leave its
    # original block columns untouched. No rectangular water rim is exported.
    water_surface={k:plane for k in wet}
    water_surface.update({k:y for k,y in enumerate(s.water) if y is not None and k not in core and k not in wet})
    leaks=[]
    water_mismatches=[]
    for k in wet:
        for j in s.neighbors(k):
            if j in water_surface and water_surface[j]!=plane:water_mismatches.append([*s.pos(j),water_surface[j]])
            if water_surface.get(j,-math.inf)<plane and after[j]<plane:leaks.append([*s.pos(j),after[j]])
    boundary=[k for k in range(len(after)) if k%s.width in (0,s.width-1) or k//s.width in (0,s.depth-1)]
    delta=[abs(after[k]-before[k]) for k in range(len(after))]
    modified=[k for k in range(len(after)) if changed[k]]
    core_mismatch=[]
    for k,y in hard.items():
        if after[k]!=y:core_mismatch.append(k)
    checks={'boundary_unchanged':all(not changed[k] and after[k]==before[k] for k in boundary),
            'water_contained':len(leaks)<=cfg['gates']['maximum_water_leaks'],
            'protected_core_heights_preserved':len(core_mismatch)<=cfg['gates']['maximum_core_mismatches'],
            'outgoing_reaches_untouched_native_ground':bool(route) and all(not changed[k] for k in route[-untouched_tail:]),
            'native_water_contact_level_matches':not water_mismatches,
            'outgoing_grade_walkable':all(abs(after[a]-after[b])<=cfg['outgoing_road']['maximum_grade'] for a,b in zip(route,route[1:])),
            'source_water_aligned':candidate['dy']==candidate['water_surface']-cfg['source_water_feet']}
    metrics={'modified_columns':len(modified),'protected_columns':len(core),'authored_lake_columns':len(wet),
             'cut_blocks':sum(max(0,before[k]-after[k]) for k in modified),
             'fill_blocks':sum(max(0,after[k]-before[k]) for k in modified),
             'maximum_cut_or_fill':max(delta),'boundary_delta':max(delta[k] for k in boundary),
             'water_leaks':len(leaks),'outgoing_length':len(route),
             'peripheral_art_columns_discarded':volume.size[0]*volume.size[2]-len(core)-len(wet)}
    return {'cfg':cfg,'survey':s,'candidate':candidate,'source':data,'transform':t,'core':core,'wet':wet,
            'before':before,'after':after,'changed':changed,'path_surface':path_surface,'path_clear':path_clear,
            'route':route,'bank':bank,'metrics':metrics,'validation':{'passed':all(checks.values()),'checks':checks,'water_leaks':leaks[:20],'native_water_mismatches':water_mismatches[:20],'outgoing_error':route_error}}


def metadata(result):
    s=result['survey'];t=result['transform'];data=result['source'];volume=data[4];candidate=result['candidate']
    ox,oy,oz=data[2]['world_offset']
    buildings=[]
    for source in data[8]:
        b={k:v for k,v in source.items()}
        corners=[t.point([source['world_min'][0],source['world_min'][1],source['world_min'][2]]),
                 t.point([source['world_max'][0],source['world_max'][1],source['world_max'][2]])]
        b['world_min']=[min(p[i] for p in corners) for i in range(3)]
        b['world_max']=[max(p[i] for p in corners) for i in range(3)]
        b['size']=[b['world_max'][i]-b['world_min'][i]+1 for i in range(3)]
        b['rotation_quarters']=(source['rotation_quarters']+t.q)%4
        for key in ('door','approach','proposed_world_min'):b[key]=t.point(source[key])
        b['proposed_world_min']=b['world_min']
        b['threshold_world_y']=source['threshold_world_y']+t.dy
        buildings.append(b)
    routes={key:[t.point([x+2995,y+30,z-30]) for x,z,y in route] for key,route in data[11]['routes'].items()}
    route=[[s.pos(k)[0],result['after'][k],s.pos(k)[1]] for k in result['route']]
    routes['south_gate_to_natural_land']=route
    trials=city.path_trials(routes)
    # Real outgoing path bends: test each monotone straight part independently,
    # and retain the complete route for live walk-through/player navigation.
    segments=[];start=0
    for i in range(1,len(route)):
        direction=(route[i][0]-route[i-1][0],route[i][2]-route[i-1][2])
        if i>1 and direction!=previous:
            if i-1-start>=3:segments.append(route[start:i])
            start=i-1
        previous=direction
    if len(route)-1-start>=3:segments.append(route[start:])
    for i,part in enumerate(segments[:4]):
        a,b=part[0],part[-1];dx=b[0]-a[0];dz=b[2]-a[2]
        trials.append({'name':f'natural_exit_segment_{i+1}','start':[a[0]+.5,a[1],a[2]+.5],
                       'end':[b[0]+.5,b[1],b[2]+.5],'min_y':b[1]-.1,'timeout_s':max(15,len(part)*.5),
                       'yaw':math.degrees(math.atan2(-dx,dz)),'pitch':0})
    cx=candidate['origin'][0]+(volume.size[0] if t.q%2==0 else volume.size[2])/2
    cz=candidate['origin'][1]+(volume.size[2] if t.q%2==0 else volume.size[0])/2
    cameras=[]
    for camera in result['cfg']['preview']['cameras']:
        name=camera['name'];position=camera['position_source_world'];target=camera['target_source_world']
        pos=t.point(position,continuous=True);aim=t.point(target,continuous=True)
        try:local_ground=s.ground[s.index(math.floor(pos[0]),math.floor(pos[2]))]
        except ValueError:local_ground=candidate['water_surface']
        pos[1]=max(pos[1],local_ground+result['cfg']['preview']['camera_ground_clearance'])
        dx,dy,dz=(aim[i]-pos[i] for i in range(3))
        cameras.append({'name':name,'pos':pos,'target':aim,'yaw':math.degrees(math.atan2(-dx,dz)),
                        'pitch':math.degrees(math.atan2(-dy,math.hypot(dx,dz)))})
    spawn_art=data[0]['recommended_spawn_artwork']
    spawn=t.point([spawn_art[0]+ox+.5,spawn_art[1]+oy,spawn_art[2]+oz+.5],continuous=True)
    return {'schema_version':1,'id':'myvillage:yunshan_natural/'+str(candidate['id']),
            'candidate':candidate,'world_min':[s.x0,min(result['after'])-5,s.z0],
            'world_max':[s.x1-1,max(volume.maximum[1]+t.dy,max(s.surface)),s.z1-1],
            'buildings':buildings,'routes':routes,'path_trials':trials,'recommended_spawn':{'pos':spawn,'yaw':180+t.q*90,'pitch':0},
            'fixture_cameras':cameras,'matched_cameras':cameras,'metrics':result['metrics'],'validation':result['validation'],
            'survey_provenance':s.data.get('provenance',{}),'not_verified':['live_collision','natural_visual_acceptance','fps','npc_services','multiplayer']}


def heightmaps(result):
    s=result['survey'];core=result['core'];wet=result['wet'];path=result['path_clear']
    return {'window':s.window,'width':s.width,'depth':s.depth,'height_convention':'feet',
            'before':result['before'],'after':result['after'],'water_y':s.water,
            'protected_mask':[1 if k in core else 2 if k in wet else 3 if k in path else 4 if result['changed'][k] else 0
                              for k in range(len(result['before']))],
            'mask_legend':{'0':'unchanged natural terrain','1':'exact authored core columns','2':'fixed lake basin',
                           '3':'outgoing road','4':'irregular terrain blend'}}


def preview_svg(result):
    s=result['survey'];h=result['after'];low=min(h);high=max(h);span=max(1,high-low)
    # One SVG run per constant-height row; actual new terrain is visible, rather
    # than rectangles representing proposed sites. Dashed outline is old slab.
    bits=[f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {s.width} {s.depth}">']
    for z in range(s.depth):
        x=0
        while x<s.width:
            k=z*s.width+x;y=h[k]
            kind=1 if k in result['core'] else 2 if k in result['wet'] or s.water[k] is not None else 0
            end=x+1
            while end<s.width and h[z*s.width+end]==y and (1 if z*s.width+end in result['core'] else 2 if z*s.width+end in result['wet'] or s.water[z*s.width+end] is not None else 0)==kind:end+=1
            color='#76a9b7' if kind==2 else f'hsl({83 if kind==0 else 38} {22 if kind==0 else 26}% {40+45*(y-low)/span:.1f}%)'
            bits.append(f'<rect x="{x}" y="{z}" width="{end-x}" height="1" fill="{color}"/>');x=end
    route=' '.join(f'{s.pos(k)[0]-s.x0+.5},{s.pos(k)[1]-s.z0+.5}' for k in result['route'])
    bits.append(f'<polyline points="{route}" stroke="#b46b3e" fill="none" stroke-width="2"/>')
    bits.append('</svg>');return ''.join(bits)


def write_preview(result,out):
    out=Path(out);out.mkdir(parents=True,exist_ok=True)
    (out/'heightmaps.json').write_text(json.dumps(heightmaps(result),separators=(',',':'))+'\n')
    (out/'preview.svg').write_text(preview_svg(result))
    meta=metadata(result);meta['heightmaps']='heightmaps.json';meta['preview']='preview.svg'
    (out/'fit-preview.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
    return meta


def preview_fit(survey,candidate,out=None,config=None):
    result=plan_fit(survey,candidate,config)
    meta=write_preview(result,out) if out else metadata(result)
    meta['heightmaps_data']=heightmaps(result)
    return meta


def split_fill(lo,hi,limit):
    pending=[(lo,hi)]
    while pending:
        a,b=pending.pop();size=[b[i]-a[i]+1 for i in range(3)]
        if math.prod(size)<=limit:yield a,b;continue
        axis=max(range(3),key=lambda i:size[i]);middle=a[axis]+size[axis]//2
        end=list(b);end[axis]=middle-1;begin=list(a);begin[axis]=middle
        pending.extend([(a,end),(begin,b)])


def terrain_commands(result):
    s=result['survey'];cfg=result['cfg'];after=result['after'];materials=cfg['materials'];core=result['core'];wet=result['wet']
    byrow=defaultdict(list);plane=result['candidate']['water_surface'];depth=cfg['blend']['topsoil_depth']
    for k,changed in enumerate(result['changed']):
        if not changed:continue
        x,z=s.pos(k);native=s.ground[k];target=after[k];surface=s.surface[k]
        clear_top=surface+cfg['blend']['vegetation_clearance']-1
        if k in core:clear_top=max(clear_top,result['source'][4].maximum[1]+result['transform'].dy)
        clear_bottom=target if k not in wet else plane
        if clear_top>=clear_bottom:byrow[z].append((x,clear_bottom,clear_top,materials['air']))
        if k in core:
            foundation_y=result['source'][4].minimum[1]+result['transform'].dy-1
            byrow[z].append((x,min(native-1,foundation_y),foundation_y,materials['body']))
        bottom=min(native-1,target-1)
        if target-depth-1>=bottom:byrow[z].append((x,bottom,target-depth-1,materials['body']))
        if k in wet:
            byrow[z].append((x,max(bottom,target-depth),target-1,cfg['lake']['bed_material']))
            byrow[z].append((x,target,plane-1,cfg['lake']['water_material']))
        else:
            if target-2>=max(bottom,target-depth):byrow[z].append((x,max(bottom,target-depth),target-2,materials['soil']))
            surface_state=result['path_surface'].get(k,materials['bank'] if k in result['bank'] else materials['surface'])
            byrow[z].append((x,target-1,target-1,surface_state))
    # Merge identical vertical intervals into x-runs, then consecutive z rows.
    active={};boxes=[]
    for z in range(s.z0,s.z1):
        group=defaultdict(list)
        for x,a,b,state in byrow.get(z,[]):
            if a<=b:group[a,b,state].append(x)
        runs={}
        for (a,b,state),xs in group.items():
            xs.sort();start=previous=xs[0]
            for x in xs[1:]+[None]:
                if x is not None and x==previous+1:previous=x;continue
                key=(start,previous,a,b,state);runs[key]=True
                if x is not None:start=previous=x
        next_active={}
        for key in runs:
            if key in active:next_active[key]=(active[key][0],z)
            else:next_active[key]=(z,z)
        for key,(za,zb) in active.items():
            if key not in next_active:boxes.append((key,za,zb))
        active=next_active
    boxes.extend((key,za,zb) for key,(za,zb) in active.items())
    # Clear before fills. Source semantic tiles are applied after this stream.
    boxes.sort(key=lambda b:0 if b[0][4]==materials['air'] else 2 if b[0][4]==cfg['lake']['water_material'] else 1)
    for (x0,x1,y0,y1,state),z0,z1 in boxes:
        for lo,hi in split_fill([x0,y0,z0],[x1,y1,z1],cfg['export']['fill_limit']):
            if min(lo[1],hi[1])<-64 or max(lo[1],hi[1])>319:raise ValueError('terrain command exceeds overworld height')
            command=f'fill {lo[0]} {lo[1]} {lo[2]} {hi[0]} {hi[1]} {hi[2]} {state}'
            yield {'command':command,'world_min':lo,'world_max':hi,'probe':{'pos':lo,'state':state}}


def export_core_tiles(result,out):
    cfg=result['cfg'];t=result['transform'];volume=result['source'][4];step=cfg['export']['tile_size'];out=Path(out)
    groups=defaultdict(list)
    for k,(sx,sz) in result['core'].items():
        wx,wz=result['survey'].pos(k);groups[wx//step,wz//step].append((sx,sz,wx,wz))
    states=[rotated_state(state,t.q) for state in volume.palette]
    palette=[]
    for state in states:
        name,props=nbt.parse_block_state(state);p={'Name':nbt.String(name)}
        if props:p['Properties']=nbt.Compound({k:nbt.String(v) for k,v in props.items()})
        palette.append(nbt.Compound(p))
    sign_map={tuple(t.point(sign['pos'])):sign for sign in result['source'][10]}
    ymin=volume.minimum[1]+t.dy;ymax=volume.maximum[1]+t.dy
    if ymin<-64 or ymax>319:raise ValueError('translated core exceeds build height')
    tiles=[];voxel_count=0
    candidate_id=str(result['candidate']['id']).lower().replace(' ','_')
    for by in range(ymin//step,ymax//step+1):
        for (bx,bz),columns in sorted(groups.items()):
            minimum=[bx*step,by*step,bz*step];blocks=[];probe=None
            for sx,sz,wx,wz in columns:
                for wy in range(max(ymin,by*step),min(ymax+1,(by+1)*step)):
                    source=(sx,wy-t.dy,sz);index=volume.cells[volume.index(source)]
                    p=[wx-minimum[0],wy-minimum[1],wz-minimum[2]]
                    block={'pos':nbt.ListTag(nbt.TAG_INT,[nbt.Int(v) for v in p]),'state':nbt.Int(index)}
                    if (wx,wy,wz) in sign_map:block['nbt']=nbt._nbt_value_to_tag('nbt',sign_data(sign_map[wx,wy,wz]['messages']))
                    blocks.append(nbt.Compound(block));voxel_count+=1
                    if probe is None and index:probe={'pos':[wx,wy,wz],'state':states[index]}
            if not blocks:continue
            if probe is None:
                p=[v.value for v in blocks[0].value['pos'].value]
                probe={'pos':[minimum[i]+p[i] for i in range(3)],'state':city.AIR}
            root=nbt.Compound({'DataVersion':nbt.Int(cfg['export']['data_version']),
                'size':nbt.ListTag(nbt.TAG_INT,[nbt.Int(step)]*3),'palette':nbt.ListTag(nbt.TAG_COMPOUND,palette),
                'blocks':nbt.ListTag(nbt.TAG_COMPOUND,blocks),'entities':nbt.ListTag(nbt.TAG_COMPOUND,[])})
            name=f't_{bx}_{by}_{bz}';relative=f'tiles/{name}.nbt';path=out/relative;path.parent.mkdir(parents=True,exist_ok=True)
            nbt.write_gzipped_nbt(root,str(path))
            resource=f'yunshan_natural/{candidate_id}/{name}'
            dest=out/f'datapack/data/myvillage/structure/{resource}.nbt';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(path.read_bytes())
            tiles.append({'id':'myvillage:'+resource,'nbt':relative,'world_min':minimum,'size':[step]*3,'probe':probe,
                          'blocks':len(blocks),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    if voxel_count!=len(result['core'])*volume.size[1]:raise AssertionError('protected column voxels were lost')
    (out/'datapack/pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':cfg['export']['pack_format'],'description':'Yunshan natural core patch'}})+'\n')
    return tiles,voxel_count


def expected_probe(result,k,category):
    s=result['survey'];x,z=s.pos(k);target=result['after'][k];cfg=result['cfg']
    if k in result['core']:
        sx,sz=result['core'][k];source_y=target-result['transform'].dy-1
        state=rotated_state(result['source'][4].get((sx,source_y,sz)),result['transform'].q)
    elif not result['changed'][k]:state=s.state(k)
    elif k in result['wet']:state=cfg['lake']['bed_material']
    else:state=result['path_surface'].get(k,cfg['materials']['bank'] if k in result['bank'] else cfg['materials']['surface'])
    return {'category':category,'pos':[x,target-1,z],'state':state}


def compile_fit(survey,candidate,out,config=None,source_manifest=None):
    started=time.monotonic();result=plan_fit(survey,candidate,config);out=Path(out)
    meta=write_preview(result,out)
    if not result['validation']['passed']:
        (out/'fit.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
        raise ValueError('natural fit failed: '+json.dumps(result['validation']))
    commands=list(terrain_commands(result));commands_path=out/'terrain_commands.jsonl'
    commands_path.write_text(''.join(json.dumps(c,separators=(',',':'))+'\n' for c in commands))
    tiles,voxel_count=export_core_tiles(result,out)
    s=result['survey'];probes=[]
    # Deterministic sample probes supplement exact core-column export counts.
    for category,keys in [('support',list(result['core'])[::max(1,len(result['core'])//12)]),
                          ('boundary',[s.index(x,z) for x in (s.x0,s.x1-1) for z in range(s.z0,s.z1,max(1,s.depth//4))]+[s.index(x,z) for z in (s.z0,s.z1-1) for x in range(s.x0,s.x1,max(1,s.width//4))]),
                          ('shore',list(result['bank'])[::max(1,len(result['bank'])//8)]),
                          ('path',result['route'][::max(1,len(result['route'])//10)])]:
        probes.extend(expected_probe(result,k,category) for k in keys)
    foundation_y=result['source'][4].minimum[1]+result['transform'].dy-1
    core_keys=list(result['core'])
    for k in core_keys[::max(1,len(core_keys)//12)]:
        x,z=s.pos(k)
        probes.append({'category':'foundation','pos':[x,foundation_y,z],'state':result['cfg']['materials']['body']})
        probes.append({'category':'foundation_contact','pos':[x,min(s.ground[k]-1,foundation_y),z],'state':result['cfg']['materials']['body']})
    wetkeys=sorted(result['wet'])
    for k in wetkeys[::max(1,len(wetkeys)//8)]:
        x,z=s.pos(k);probes.append({'category':'water','pos':[x,result['candidate']['water_surface']-1,z],
                                  'state':result['cfg']['lake']['water_material']})
    meta.update({'tiles':tiles,'terrain_commands':'terrain_commands.jsonl','terrain_command_count':len(commands),
                 'placement_order':['terrain_commands','tiles'],'probes':probes,
                 'core_voxels_preserved':voxel_count,'generation_seconds':round(time.monotonic()-started,3),
                 'heightmaps':'heightmaps.json','preview':'preview.svg',
                 'inputs_sha256':{'config':hashlib.sha256(json.dumps(result['cfg'],sort_keys=True).encode()).hexdigest(),
                                  'generator':hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}})
    for name in ('fit.json','manifest.json'):(out/name).write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
    return meta


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--survey',type=Path,required=True)
    p.add_argument('--candidate',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--config',type=Path,default=DEFAULT_CONFIG);p.add_argument('--preview-only',action='store_true')
    a=p.parse_args();result=(preview_fit if a.preview_only else compile_fit)(a.survey,a.candidate,a.out,a.config)
    print(json.dumps({k:result[k] for k in ('metrics','validation')},ensure_ascii=False))


if __name__=='__main__':main()
