"""原版比例的连续主体；地域差异由像素材质表达，战斗片段从同源时序生成。"""
from __future__ import annotations
import math
from types import SimpleNamespace
from functools import lru_cache
from pathlib import Path
from tools.gen_undead_roster import entry,names
from .builder import Builder
from . import anim

def build_model(name):
    v=entry(name);sk=v['family']=='archer';b=Builder('myvillage:'+name,look={'bone':'head','max_yaw':35,'max_pitch':25},shadow_radius=v['physical']['shadow_radius'])
    b.bone('body','root',at=(0,-12,0));b.box_local('body','torso',(-4,-12,-2),(8,12,4))
    b.bone('head','body',at_local=(0,-12,0));b.box_local('head','head',(-4,-8,-4),(8,8,8))
    for side,s in [('right',-1),('left',1)]:
        w=2 if sk else 4
        b.bone('arm_'+side,'body',at_local=(s*5,-10,0))
        arm_x=-1 if sk else (-3 if s<0 else -1)
        b.box_local('arm_'+side,'arm_'+side,(arm_x,-2,-w/2),(w,12,w))
        b.bone('leg_'+side,'root',at=(s*2,-12,0));b.box_local('leg_'+side,'leg_'+side,(-w/2,0,-w/2),(w,12,w))
    if sk:
        b.bone('bow','arm_left',at_local=(0,9,-1),rot=(0,0,0))
        b.box_local('bow','bow_grip',(-.5,-3,-.5),(1,6,1))
        for i,s in enumerate([-1,1]):
            bn='bow_end_'+str(i);b.bone(bn,'bow',at_local=(0,s*3,0),rot=(s*20,0,0));b.box_local(bn,'bow_limb_'+str(i),(-.5,-7 if s<0 else 0,-.5),(1,7,1))
        b.box_local('bow','bow_string',(-.15,-9,2),(1,18,1))
    elif v['region']=='zhongzhou':
        b.bone('weapon','arm_right',at_local=(0,9,0));b.box_local('weapon','sword_grip',(-.5,-1,-.5),(1,4,1));b.box_local('weapon','sword_blade',(-1,-15,-.5),(2,14,1))
    return b.model

def painter(name):
    v=entry(name);p={k:tuple(bytes.fromhex(x)) for k,x in v['palette'].items()};sk=v['family']=='archer';region=v['region']
    def draw(t):
        n=t.cube;x,y,z=t.local
        if n=='torso':y+=12
        u,w=int(t.fu*8),int(t.fv*12);c=p['body']
        if n.startswith(('bow','sword')):c=(105,79,51) if 'blade' not in n else (132,137,135)
        elif n=='torso':
            c=p['body'] if sk else p['cloth']
            if sk and t.face in ('NORTH','SOUTH'):
                if abs(x)>.7 and 2<y<10 and int(y)%3==0:c=p['dark']
                if y>9:c=p['cloth']
            elif not sk:
                if t.face=='NORTH' and abs(x)<1 and y<3:c=p['body']
                if 8<y<10:c=p['accent']
                if t.face=='NORTH' and abs(x)<.8 and 3<y<8:c=p['dark']
        elif n.startswith('leg') and not sk:c=p['dark'] if y<10 else (58,53,47)
        elif n.startswith('arm') and not sk and y<6:c=p['cloth']
        if n=='head' and not sk and (t.face=='DOWN' or (y < -6 and t.face!='UP')):c=tuple(round(a*.65) for a in p['cloth'])
        if n=='torso' and not sk and t.face in ('NORTH','SOUTH') and 3<y<8 and int(x+4)%4==0:c=tuple(round(a*.82) for a in c)
        if n=='head' and t.face=='NORTH':
            ix,iy=int(x+4),int(y+8)
            if iy in (3,4) and ix in (1,2,5,6):return (*p['dark'],255)
            if iy==4 and ix in (2,5):return (*p['accent'],255)
            if sk and iy==5 and ix in (3,4):return (*p['dark'],255)
            if iy==6 and 2<=ix<=5:return (*p['dark'],255)
        # 大块、低对比材质簇；每个地区有不同分布，脸部关键像素保持确定。
        if not n.startswith(('bow','sword')):
            mark=(u//2+w//3+(0 if t.face in ('NORTH','SOUTH') else 1))%7
            if region=='ximo' and w>7 and mark<2:c=p['accent']
            if region=='donghai' and w in (7,8):c=p['accent']
            if region=='nanjiang' and mark==2:c=p['accent']
            if region=='beiyuan' and w<2:c=p['accent']
            if region=='donghuang' and (u+w)%6<2:c=p['accent']
            if region=='lingyue' and n=='torso' and (u+w)%9==0:c=p['accent']
            if region=='moyu' and (u-2*w)%11==0:c=p['accent']
            if region=='zhongzhou' and n=='torso' and 3<u<5 and 3<w<6:c=p['accent']
        shade=.92+.10*max(0,-t.n[1])+(0.04 if (u//3+w//4)%3==0 else 0)
        return tuple(min(255,round(a*shade)) for a in c)+(255,)
    return draw

def clips(model,server):
    def pose(**angles):return {n:{'rotation':v} for n,v in angles.items()}
    out=[anim.Clip('idle',2,True,[anim.Key(0,{}),anim.Key(1,pose(head=(2,0,0))),anim.Key(2,{})])]
    for label,amp in [('walk',25),('run',34)]:
        keys=[]
        for i in range(9):
            a=amp*math.sin(math.pi*i/4)
            walk=pose(leg_left=(a,0,0),leg_right=(-a,0,0),arm_left=(-a*.6,0,0),arm_right=(a*.6,0,0))
            # Only the returning foot lifts; the planted foot moves backwards and defines gait rate.
            velocity=math.cos(math.pi*i/4)
            walk['leg_left']['position']=(0,1.5*max(0,-velocity),0)
            walk['leg_right']['position']=(0,1.5*max(0,velocity),0)
            keys.append(anim.Key(i/8,walk,'linear'))
        out.append(anim.Clip(label,1,True,keys))
    out.append(anim.Clip('stagger',.5,False,[anim.Key(0,{}),anim.Key(.1,pose(body=(-14,0,0),head=(12,0,0))),anim.Key(.5,{})]))
    v=entry(model.id.split(':')[1])
    for a in v['moves']:
        n=a['animation'];end=a['total_ticks'];w=a['windup_ticks'];ticks=a['strike_ticks']
        wind=pose(body=(-8,-12,0),arm_right=(-45,-15,20),arm_left=(-35,15,-20))
        hit=pose(body=(12,15,0),arm_right=(-95,10,-12),arm_left=(-90,-10,12))
        if n=='shot':
            wind=pose(body=(0,-12,0),arm_left=(-90,18,0),arm_right=(-90,-17,0),bow=(90,0,0),head=(0,12,0))
            hit=pose(body=(0,-7,0),arm_left=(-85,13,0),arm_right=(-85,-22,0),bow=(85,0,0),head=(0,7,0))
        elif n=='bow_sweep':wind=pose(body=(0,-35,0),arm_left=(-65,40,0));hit=pose(body=(0,35,0),arm_left=(-75,-40,0))
        elif n in ('pounce','ice_pounce'):wind=pose(body=(22,0,0),arm_right=(-35,0,25),arm_left=(-35,0,-25),leg_left=(-15,0,0),leg_right=(15,0,0));hit=pose(body=(40,0,0),arm_right=(-95,0,0),arm_left=(-95,0,0))
        elif n=='ankle_sweep':wind=pose(body=(65,0,0),head=(-50,0,0),arm_right=(-30,0,0));hit=pose(body=(75,15,0),head=(-55,0,0),arm_right=(-105,20,0))
        elif n=='gu_breath':wind=pose(body=(-15,0,0),head=(-30,0,0));hit=pose(body=(20,0,0),head=(15,0,0))
        keys=[anim.Key(0,{}),anim.Key(max(1,w-7)/20,wind),anim.Key((w-1)/20,wind)]
        for j,t in enumerate(ticks):
            if j:keys.append(anim.Key((t-3)/20,wind))
            k=hit if j%2==0 or n=='shot' else pose(body=(12,-15,0),arm_right=(-35,0,0),arm_left=(-105,-15,0))
            keys.append(anim.Key(t/20,k,'linear'))
        recover=pose(body=(42,0,0),arm_left=(-60,0,0),arm_right=(-60,0,0)) if n in ('pounce','ice_pounce') else {}
        keys.extend([anim.Key((ticks[-1]+5)/20,recover),anim.Key(end/20,{})])
        out.append(anim.Clip(n,end/20,False,keys))
    return out

def definition(name):
    return SimpleNamespace(build_model=lambda:build_model(name),paint=painter(name),glow=lambda t:None,clips=clips,authored_texture=lambda model,rows:material(name,model,rows),extra_outputs=extra_outputs)


@lru_cache(maxsize=2)
def material_lookup(family):
    from . import cuboid,paint
    from tools.gen_qingfeng_sword_model import decode_png_rgba
    name='bone_archer' if family=='archer' else 'wandering_corpse'
    path=Path(__file__).resolve().parents[2]/'art/entities/undead_roster'/family/'material_tones.png'
    if not path.exists():raise ValueError('missing authored undead material: '+str(path))
    w,h,raw=decode_png_rgba(path.read_bytes());m=build_model(name);cuboid.pack(m)
    if (w,h)!=m.texture_size:raise ValueError('undead material UV dimensions differ')
    return {(t.cube,t.face,round(t.fu,6),round(t.fv,6)):sum(raw[(t.v*w+t.u)*4:(t.v*w+t.u)*4+3])/765 for t in paint.texels(m)}

def material(name,model,rows):
    from .paint import texels
    lookup=material_lookup(entry(name)['family']);out=[list(row) for row in rows]
    for t in texels(model):
        # Face-local native features and region motifs are authoritative.
        if t.cube=='head':continue
        tone=lookup.get((t.cube,t.face,round(t.fu,6),round(t.fv,6)),.5)
        factor=.84+.27*tone
        c=out[t.v][t.u];out[t.v][t.u]=tuple(min(255,round(v*factor)) for v in c[:3])+(c[3],)
    return out

def extra_outputs(built):
    if built.name not in ('wandering_corpse','bone_archer'):return {}
    import json
    from .paint import texels
    from tools.gen_qingfeng_sword_model import encode_png
    family=entry(built.name)['family'];path=Path(__file__).resolve().parents[2]/'art/entities/undead_roster'/family
    w,h=built.model.texture_size;rows=[[(0,0,0,255)]*w for _ in range(h)]
    for t in texels(built.model):rows[t.v][t.u]=(255,255,255,255)
    return {path/'uv_truth.json':(json.dumps(built.model_doc,ensure_ascii=False,indent=2)+'\n').encode(),path/'uv_islands_mask.png':encode_png(rows)}
