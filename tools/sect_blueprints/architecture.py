"""宗门差分建筑：语义体块和功能室内共用现有 pass 与屋顶注册表。"""
from __future__ import annotations
import json,random
from pathlib import Path
from buildgen import ops
from buildgen.massing import MassingGraph,Node
from buildgen.style import Style
from buildgen.ops import validate_style_vocabulary,stair_state
from buildgen.passes import BuildContext,PIPELINE
from buildgen.archetypes import (_cultivation_platform,_cultivation_colonnade,_reserve_stairwell,_zone,_alchemy_furnace_node)
from province_capitals.blueprint_plan.atomic_blocks import parse

ROOT=Path(__file__).resolve().parents[2]
PROFILES=ROOT/'blueprints/sects/architecture_profiles.json'

def profile(key):
    data=json.loads(PROFILES.read_text())['profiles'][key]
    style=json.loads((ROOT/'tools/buildgen/styles/cultivation_sect.json').read_text())
    style['style_id']='sect_'+key
    style['material_slots'].update(data['material_slots'])
    style['variation_rate']={k:0 for k in style['variation_rate']}
    value=Style(style);validate_style_vocabulary(value)
    return data,value

def normalize_beds(grid):
    """按实际头尾位置统一朝向，同时拒绝孤立或歧义床头。"""
    directions={'north':(0,-1),'south':(0,1),'east':(1,0),'west':(-1,0)}
    for p,cell in list(grid.iter_cells()):
        name,props=parse(cell.state)
        if not name.endswith('_bed') or props.get('part')!='head':continue
        candidates=[]
        for facing,(dx,dz) in directions.items():
            foot=(p[0]-dx,p[1],p[2]-dz);fn,fp=parse(grid.state_at(foot))
            if fn==name and fp.get('part')=='foot':candidates.append((foot,facing))
        if len(candidates)!=1:raise ValueError('宗门床头尾不完整或存在歧义：'+str(p))
        foot,facing=candidates[0]
        for pos,part in ((p,'head'),(foot,'foot')):
            state=name+f'[facing={facing},occupied=false,part={part}]'
            grid.set(pos,state,cell.tags,cell.priority,cell.slot,force=True)


def furnish(graph,main,archetype):
    cfg=json.loads(PROFILES.read_text())['interiors'][archetype]
    fh=main.meta['foundation_h'];story_h=main.meta['story_wall_h']
    for story in range(main.meta['stories']):
        inset=main.meta.get('story_insets',[0]*main.meta['stories'])[story]
        x0,x1=main.x0+1+inset,main.x1-1-inset
        z0,z1=main.z0+1+inset,main.z1-1-inset
        split=(x0+x1)//2
        for i,row in enumerate(cfg['zones']):
            bounds=(x0,z0,split,z1) if row['side']=='left' else (split+1,z0,x1,z1) if row['side']=='right' else (x0,z0,x1,z1)
            zone=_zone(graph,main,row['kind'],bounds,y=fh+story*story_h+(1 if story else 0),suffix=f'{story}_{i}')
            zone.meta.update(row)
    if cfg.get('alchemy_furnace'):
        node=_alchemy_furnace_node(graph,main)
        node.origin=(node.x0,node.y0,main.z1-cfg['furnace_rear_inset'])
    if cfg.get('belfry'):
        main.type='tower_volume';main.meta['belfry']=True;main.meta['bell_hang_height']=cfg['bell_hang_height']


def stair_passages(graph):
    result=[]
    for vol in graph.volumes():
        opening=vol.meta.get('stairwell')
        if not opening:continue
        run=list(range(opening['z0'],opening['z1']+1))
        if opening['direction']=='north':run.reverse()
        for story in range(vol.meta['stories']-1):
            base=vol.meta['foundation_h']+story*vol.meta['story_wall_h']
            result.extend((opening['x0'],base+step+1,z) for step,z in
                          enumerate(run[:vol.meta['story_wall_h']]+[opening['landing_z']]))
            exit_x=opening['x1']+1 if opening['x0']<(vol.x0+vol.x1)//2 else opening['x0']-1
            result.extend((x,base+vol.meta['story_wall_h']+1,opening['landing_z'])
                          for x in range(min(opening['x0'],exit_x),max(opening['x0'],exit_x)+1))
    return result


def bedchamber(grid,zone,keep):
    """床沿后墙、储物箱向外，朝房间中部留连续床侧通道。"""
    inner=1 if zone.meta['side']=='left' else -1
    xs=range(zone.x1-1,zone.x0,-1) if inner==1 else range(zone.x0+1,zone.x1)
    for z in range(zone.z1,zone.z0+1,-1):
        for x in xs:
            head=(x,zone.y0,z);foot=(x,zone.y0,z-1);side=(x+inner,zone.y0,z-1)
            positions=(head,foot,side)
            if any(abs(p[0]-q[0])+abs(p[2]-q[2])<=1 and abs(p[1]-q[1])<=1 for p in positions for q in keep):continue
            if any(grid.state_at((p[0],p[1]-1,p[2]))=='minecraft:air' or
                   any(not grid.is_empty((p[0],p[1]+dy,p[2])) or
                       (grid.get((p[0],p[1]+dy,p[2])) and grid.get((p[0],p[1]+dy,p[2])).protected) for dy in (0,1)) for p in positions):continue
            for p,part in ((head,'head'),(foot,'foot')):
                if not grid.set(p,zone.meta['bed']+f'[facing=south,occupied=false,part={part}]',['INTERIOR','PROTECTED'],60,'FURNITURE'):
                    raise ValueError('床铺写入与预留冲突：'+str(p))
            for p in (side,(side[0],side[1],z)):
                for dy in (0,1):grid.set((p[0],p[1]+dy,p[2]),'minecraft:air',['INTERIOR','PROTECTED'],60)
            chest=(x-inner,zone.y0,z)
            if zone.x0<=chest[0]<=zone.x1 and grid.is_empty(chest) and grid.state_at((chest[0],chest[1]-1,chest[2]))!='minecraft:air':
                grid.set(chest,zone.meta['chest'],['INTERIOR','PROTECTED'],60,'FURNITURE')
            return 2
    raise ValueError('弟子居无法安排完整床铺和床侧通道：'+zone.id)


def component(building,size,key,reserved=()):
    cfg,style=profile(key);role={**cfg['default'],**cfg['roles'].get(building['archetype'],{})}
    w,_,d=size;center=w//2;hall=building['archetype']=='sect_main_hall'
    fh=3 if hall else 2;front=7 if hall else 4
    graph=MassingGraph(meta={'archetype':building['archetype'],'style_family':'cultivation_sect','cultivation_form':True,'architecture_profile':key})
    def volume(i,x,z,width,depth,wall_h,roof,axis='x',parent=None,side=None,stories=1):
        return graph.add(Node(id=i,type='main_volume' if i=='main' else 'side_wing' if side in ('west','east') else 'rear_shed',
            origin=(x,0,z),size=(width,fh+wall_h*stories,depth),attach_to=parent,side=side,tags=['STRUCTURE'],
            meta={'foundation_h':fh,'wall_h':wall_h*stories,'stories':stories,'story_wall_h':wall_h,'wall_type':role['wall_type'],
                  'roof':{'type':roof,'ridge_axis':axis,'overhang':2}}))
    bw=w-role.get('width_inset',6);bd=role.get('depth',d-front-3)
    form=role['form']
    if form=='winged':
        bw=w-role.get('width_inset',14)
    elif form=='cross_ridge':
        bd-=6
    elif form=='pavilion':
        bw=bd=min(bw,bd)
    main=volume('main',center-bw//2,front,bw,bd,role['wall_height'],role['roof'],role.get('axis','x'),stories=role['stories'])
    inset=role.get('upper_inset',0)
    if inset and role['stories']>1:
        main.type='tower_volume';main.meta['story_insets']=[i*inset for i in range(role['stories'])]
        main.meta['roof']['footprint_inset']=(role['stories']-1)*inset
    if form=='winged':
        for side,x in [('west',main.x0-3),('east',main.x1+1)]:
            volume(side+'_wing',x,front+2,3,max(5,bd-4),role['wing_height'],'sweeping_eave_roof','z','main',side)
    elif form=='cross_ridge':
        volume('rear_shrine',center-4,main.z1+1,9,6,role['wall_height']+1,'sweeping_eave_roof','z','main','back')
    elif form not in ('hall','pavilion'):
        raise ValueError('未注册建筑体量：'+form)
    graph.meta['door']={'volume':'main','wall':'front','along':center}
    if role['stories']>1:_reserve_stairwell(graph,main,center,preferred_side='west')
    rng=random.Random(0)
    _cultivation_platform(graph,main,center,pad=1,height=fh)
    _cultivation_colonnade(graph,main,center,sides=tuple(role['colonnades']),
        top_y=fh+role['wall_height']*role.get('colonnade_storeys',role['stories'])-1)
    furnish(graph,main,building['archetype'])
    ctx=BuildContext(style=style,archetype=building['archetype'],scale_tier='design',seed=0,rng=rng,group_id='cultivation_sect',graph=graph)
    for step in PIPELINE[1:]:
        if step.__name__=='material_variation_pass':continue
        if step.__name__=='interior_furnishing_pass':
            # 只预留原本为空的通道，不移除墙体、台阶或其他结构。
            keep=[*reserved,*stair_passages(graph),(center,fh,front),(center,fh,front+1)]
            for x,y,z in keep:
                if not (0<=x<w and 0<=z<d and y>=0):continue
                for dy in (0,1,2):
                    pos=(x,y+dy,z)
                    if ctx.grid.is_empty(pos):ctx.grid.set(pos,'minecraft:air',['INTERIOR','PROTECTED'],60)
            for vol in graph.by_type('tower_volume'):
                if not vol.meta.get('belfry'):continue
                story=vol.meta['stories']-1
                floor=vol.meta['foundation_h']+story*vol.meta['story_wall_h']+(1 if story else 0)
                y=floor+vol.meta['bell_hang_height'];x=(vol.x0+vol.x1)//2;z=(vol.z0+vol.z1)//2
                inset=vol.meta.get('story_insets',[0]*vol.meta['stories'])[-1]
                for bx in range(vol.x0+inset,vol.x1-inset+1):
                    ctx.grid.set((bx,y+1,z),ops.log_state(style,'x'),['INTERIOR','STRUCTURE','PROTECTED'],60,'FRAME_WOOD',force=True)
                ctx.grid.set((x,y,z),ops._bell_state(style,'north','ceiling'),['INTERIOR','DETAIL','PROTECTED'],60,'INTERIOR_CIVIC',force=True)
                ctx.interior_function_blocks+=1
            for zone in graph.by_type('interior_zone'):
                ctx.interior_function_blocks+=(bedchamber(ctx.grid,zone,keep) if zone.meta['kind']=='bedchamber' else
                    ops.interior_zone(ctx.grid,style,rng,graph.get(zone.attach_to),zone,keep))
            ctx.passes_run.append(step.__name__)
            continue
        step(ctx)
        if step.__name__=='stair_pass':
            for vol in graph.volumes():
                opening=vol.meta.get('stairwell')
                if not opening:continue
                run=list(range(opening['z0'],opening['z1']+1))
                if opening['direction']=='north':run.reverse()
                for story in range(vol.meta['stories']-1):
                    base=vol.meta['foundation_h']+story*vol.meta['story_wall_h']
                    for tread,z in enumerate(run[:vol.meta['story_wall_h']]):
                        pos=(opening['x0'],base+tread,z)
                        # 多段楼梯先前预留的空气不能吞掉下一段的前两级。
                        ctx.grid.set(pos,stair_state(style,opening['direction']),['STRUCTURE','INTERIOR','PROTECTED'],60,'ROOF_DARK',force=True)
                for story in range(1,vol.meta['stories']):
                    y=fh+story*vol.meta['story_wall_h']
                    # 原 helper 在楼板前少了一阶；将实心落脚格改为最后一级台阶。
                    pos=(opening['x0'],y,opening['landing_z'])
                    ctx.grid.set(pos,stair_state(style,opening['direction']),['STRUCTURE','INTERIOR','PROTECTED'],60,'ROOF_DARK',force=True)
    normalize_beds(ctx.grid)
    cells=[];palette=[];indices={};max_y=0;passages=[]
    for p,c in sorted(ctx.grid.iter_cells()):
        if not (0<=p[0]<w and 0<=p[2]<d and p[1]>=0):
            raise ValueError(f'建筑候选越过原占地 {building["id"]}: {p}, {size}')
        name,props=parse(c.state)
        state=name+('['+','.join(k+'='+v for k,v in sorted(props.items()))+']' if props else '')
        if state not in indices:indices[state]=len(palette);palette.append(state)
        layer='roof' if c.slot in ('ROOF_TILE','ROOF_DARK','RIDGE_ORNAMENT') else 'building'
        cells.append({'pos':p,'state':indices[state],'layer':layer});max_y=max(max_y,p[1])
        if 'OPENING' in c.tags:
            def free(pos):
                state=ctx.grid.state_at(pos)
                return state=='minecraft:air' or '_door[' in state
            if free(p) and free((p[0],p[1]+1,p[2])) and not free((p[0],p[1]-1,p[2])):
                passages.append(p)
    # 楼梯井按真实玩家的两格净空核对，室外三格净空不变。
    passages.extend(stair_passages(graph))
    return {'size':[w,max_y+1,d],'palette':palette,'blocks':cells,'entities':[],'massing':[n.to_dict() for n in graph.volumes()],
            'passages':sorted(set(passages))}
