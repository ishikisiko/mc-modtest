"""四种传承妖物的完整体块、面向像素材质和战斗动作。"""
from __future__ import annotations
import json, math
from types import SimpleNamespace
from pathlib import Path
from functools import lru_cache
from .builder import Builder
from . import anim,paint,cuboid
from tools.gen_legacy_encounters import catalogue,ROOT

def names():return tuple(m['id'] for m in catalogue()['mobs'])
def entry(name):return next(m for m in catalogue()['mobs'] if m['id']==name)

@lru_cache(maxsize=4)
def build_model(name):
    m=entry(name);b=Builder('myvillage:'+name,look={'bone':'head','max_yaw':20,'max_pitch':15},shadow_radius=m['physical']['shadow_radius'])
    b.bone('body','root',at=(0,-12,0))
    if name=='wandering_sword':
        b.bone('head','body',at=(0,-12,0))
        b.box('body','blade',(-2,-22,-.5),(4,21,1))
        b.bone('tip_joint','body',at=(0,-1,0),rot=(0,0,45))
        b.box_local('tip_joint','tip',(-1,-1,-.5),(2,2,1))
        b.box('body','guard',(-6,-24,-1.5),(12,2,3))
        b.box('body','grip',(-1,-31,-1),(2,7,2))
        b.box('body','pommel',(-2,-34,-1.5),(4,3,3))
    elif name=='mirror_wraith':
        b.box('body','robe',(-4,-23,-3),(8,22,6))
        b.bone('head','body',at=(0,-24,0))
        b.box('head','face',(-3,-31,-3),(6,7,6))
        b.box('head','hood_back',(-4,-32,2),(8,9,2))
        b.box('head','hood_top',(-4,-33,-4),(8,2,8))
        b.box('head','hood_left',(3,-31,-4),(1,10,6));b.box('head','hood_right',(-4,-31,-4),(1,10,6))
        for side,s in [('right',-1),('left',1)]:
            b.bone('arm_'+side,'body',at=(s*5,-22,0),rot=(0,0,-s*12))
            b.box_local('arm_'+side,'sleeve_'+side,(-2,0,-3),(4,15,6))
            b.box_local('arm_'+side,'hand_'+side,(-1.5,14,-1.5),(3,2,3))
    elif name=='ember_toad':
        b.box('body','belly',(-9,-13,-12),(18,11,16))
        b.bone('head','body',at=(0,-10,-5))
        b.box('head','head',(-10,-15,-13),(20,7,12))
        b.box('head','mouth',(-9,-9,-14),(18,2,12))
        for s in [-1,1]:
            b.box('head','eye_'+str(s).replace('-','n'),(s*6-2,-18,-10),(4,4,5))
        for side,s in [('right',-1),('left',1)]:
            for end,z in [('front',-7),('back',7)]:
                bone='leg_'+end+'_'+side;b.bone(bone,'body',at=(s*9,-6,z))
                b.box_local(bone,bone,(-3,-1,-3),(6,6,6))
                b.box_local(bone,'foot_'+end+'_'+side,(-3,4,-6),(6,2,8))
    else:
        b.box('body','torso',(-7,-30,-5),(14,18,10))
        b.bone('head','body',at=(0,-31,0));b.box('head','stone_head',(-5,-40,-5),(10,10,10))
        for side,s in [('right',-1),('left',1)]:
            b.bone('arm_'+side,'body',at=(s*10,-29,0))
            b.box_local('arm_'+side,'arm_'+side,(-3,-1,-3),(6,18,6))
            b.bone('leg_'+side,'root',at=(s*4,-12,0))
            b.box_local('leg_'+side,'leg_'+side,(-3,0,-3),(6,12,6))
    return b.model

def painter(name):
    m=entry(name);colors=[paint.hex_rgb(v) for v in m['palette']]
    def color(t):
        base=colors[0];u,v=t.fu,t.fv
        edge=min(u,1-u,v,1-v)
        if name=='wandering_sword':
            if t.cube=='guard':base=(174,141,74)
            elif t.cube=='grip':base=(47,52,44)
            else:
                base=colors[1] if edge<.16 else colors[0]
                if t.face in ('NORTH','SOUTH') and .38<u<.62:base=colors[2]
                if t.cube=='blade' and t.face=='NORTH' and .1<v<.23 and (u<.3 or u>.7):base=(167,249,218)
        elif name=='mirror_wraith':
            if t.cube=='face' or t.cube.startswith('hand'):base=colors[2]
            if t.cube=='face' and t.face=='NORTH':
                if .32<v<.55 and (.1<u<.35 or .65<u<.9):base=(81,212,218)
            elif t.cube=='robe' and t.face in ('NORTH','SOUTH'):
                if v>.55 and abs(u-(.5+.22*math.sin(v*25)))<.12:base=colors[1]
                elif int(u*8)%3==0:base=(32,54,69)
        elif name=='ember_toad':
            if t.cube=='belly' and t.face=='NORTH':base=(224,143,49) if edge>.18 else colors[1]
            elif t.cube.startswith('eye'):
                base=(248,185,45) if t.face in ('NORTH','WEST','EAST') else colors[0]
                if .35<u<.6 and .25<v<.8 and t.face=='NORTH':base=(28,22,14)
            elif t.cube=='mouth' and t.face=='NORTH':base=(61,37,17)
            elif (int(t.p[0]/3)*7+int(t.p[1]/3)*11+int(t.p[2]/3)*3)%13<3:base=colors[1]
        else:
            base=colors[0]
            if t.face=='DOWN' or (int(t.p[0])//3+int(t.p[1])//4)%7==0:base=(83,102,55)
            if t.cube=='torso' and t.face=='NORTH' and .35<u<.65 and .12<v<.87:
                if int(u*16)%4==0 or int(v*24)%6 in (1,2):base=colors[2]
            if t.cube=='stone_head' and t.face=='NORTH':
                if .25<v<.42 and (.1<u<.4 or .6<u<.9):base=(201,222,160)
                if .4<u<.6 and .4<v<.65:base=colors[1]
                if .65<v<.75 and .2<u<.8:base=(48,56,45)
        light={'DOWN':1.12,'UP':.65,'NORTH':.93,'SOUTH':.8,'EAST':1.0,'WEST':.88}[t.face]
        noise=((t.u*17+t.v*11)//5%5-2)*2
        return tuple(max(0,min(255,round(v*light)+noise)) for v in base)+(255,)
    return color

def glow(name):
    color=painter(name)
    def value(t):
        p=color(t)
        bright=(name=='ember_toad' and t.cube=='belly' and t.face=='NORTH' and .2<t.fu<.8 and .15<t.fv<.85)
        bright|=(name=='wandering_sword' and t.cube=='blade' and .1<t.fv<.23 and (t.fu<.3 or t.fu>.7) and t.face=='NORTH')
        bright|=(name=='mirror_wraith' and t.cube=='face' and t.face=='NORTH' and .32<t.fv<.55 and (.1<t.fu<.35 or .65<t.fu<.9))
        bright|=(name=='stele_guardian' and t.cube=='stone_head' and t.face=='NORTH' and .25<t.fv<.42 and (.1<t.fu<.4 or .6<t.fu<.9))
        return tuple(round(v*.32) for v in p[:3])+(255,) if bright else (0,0,0,0)
    return value

def clips(name,model,server):
    bones=tuple(b.name for b in model.bones)
    def pose(**kwargs):return {k:{'rotation':v} for k,v in kwargs.items() if k in bones}
    idle={'body':{'position':(0,.5,0)}}
    result=[anim.Clip('idle',2,True,[anim.Key(0,{}),anim.Key(1,idle),anim.Key(2,{})])]
    for clip,length,amp in [('walk',1.2,20),('run',.7,32)]:
        p1={b:{'rotation':((amp if 'left' in b else -amp),0,0)} for b in bones if b.startswith('leg')}
        p2={b:{'rotation':((-amp if 'left' in b else amp),0,0)} for b in bones if b.startswith('leg')}
        if name in ('mirror_wraith','wandering_sword'):
            p1={'body':{'position':(0,1.5,0),'rotation':(0,0,4)}};p2={'body':{'position':(0,.2,0),'rotation':(0,0,-4)}}
        result.append(anim.Clip(clip,length,True,[anim.Key(0,p1),anim.Key(length/2,p2),anim.Key(length,p1)]))
    result.extend([anim.Clip('stagger',.5,False,[anim.Key(0,{}),anim.Key(.12,pose(body=(-12,0,5))),anim.Key(.5,{})]),anim.Clip('hurt',.3,False,[anim.Key(0,{}),anim.Key(.1,pose(body=(-8,0,0))),anim.Key(.3,{})]),anim.Clip('death',1,False,[anim.Key(0,{}),anim.Key(1,pose(root=(0,0,85)))])])
    for move in server['moves']:
        w=move['windup_ticks'];end=move['total_ticks'];clip=move['animation']
        wind=pose(body=(-12,0,-5),head=(-10,0,0),arm_right=(-75,0,-15),arm_left=(-75,0,15))
        strike=pose(body=(14,0,0),head=(8,0,0),arm_right=(30,0,0),arm_left=(30,0,0))
        if name=='wandering_sword':wind=pose(body=(-50,0,-20));strike=pose(body=(-90,0,0))
        elif name=='mirror_wraith':wind=pose(arm_right=(-80,0,-40),arm_left=(-80,0,40));strike=pose(arm_right=(-100,25,20),arm_left=(-100,-25,-20))
        elif name=='ember_toad':wind={'body':{'scale':(1.08,1.12,1.06)},'head':{'rotation':(-12,0,0)}};strike={'head':{'rotation':(18,0,0)}}
        result.append(anim.Clip(clip,end/20,False,[anim.Key(0,{}),anim.Key(max(1,w-7)/20,wind),anim.Key((w-1)/20,wind),anim.Key((w+1)/20,strike),anim.Key((end-8)/20,strike),anim.Key(end/20,{})],notes={'windup':w,'active':move['active_ticks'],'recovery':end}))
    return result

def definition(name):
    m=entry(name)
    return SimpleNamespace(NAME=name,ID='myvillage:'+name,HITBOX=(m['physical']['width'],m['physical']['height']),build_model=lambda:build_model(name),paint=painter(name),glow=glow(name),clips=lambda model,server:clips(name,model,server),authored_texture=lambda model,base:authored_texture(name,model,base),SKIP_CREASE=tuple(c.name for _,c in build_model(name).cubes()))

def authored_texture(name,model,base):
    path=ROOT/'art/entities'/name/'material_tones.png'
    if not path.exists():return base
    from tools.gen_qingfeng_sword_model import decode_png_rgba
    w,h,raw=decode_png_rgba(path.read_bytes())
    if (w,h)!=model.texture_size:raise ValueError('UV atlas size drift: '+name)
    rows=[list(row) for row in base]
    for t in paint.texels(model,{c.name for _,c in model.cubes()}):
        # Native tiny facial features, mouth boundary and the glowing throat remain exact.
        if t.cube.startswith(('eye','face','hand','stone_head')) or t.cube in ('mouth','belly'):continue
        i=(t.v*w+t.u)*4
        if raw[i+3]!=255:continue
        original=rows[t.v][t.u];candidate=raw[i:i+3]
        rows[t.v][t.u]=tuple(round(.72*candidate[k]+.28*original[k]) for k in range(3))+(255,)
    return rows
