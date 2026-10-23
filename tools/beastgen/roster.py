"""Ordinary wilderness creatures: deliberately separate silhouettes, joints and pixel materials."""
from __future__ import annotations
import json
import math
from pathlib import Path
from .builder import Builder
from . import anim

ROOT=Path(__file__).resolve().parents[2]
def entry(name):
    return next(x for x in json.loads((ROOT/'tools/wilderness_roster/catalogue.json').read_text())['mobs'] if x['id']==name)

def box(b,bone,name,lo,size):return b.box_local(bone,name,lo,size)

def build_model(name):
    m=entry(name);b=Builder('myvillage:'+name,look={'bone':'head','max_yaw':28.,'max_pitch':22.},shadow_radius=m['dimensions']['shadow_radius'])
    if name in ('thornback_badger','redplate_lizard'):
        badger=name=='thornback_badger';hip=10 if badger else 8
        b.bone('body','root',at=(0,-hip,0));box(b,'body','body',(-6,-5,-8),(12,9,18))
        box(b,'body','chest',(-6.5,-4,-10),(13,8,8))
        b.bone('head','body',at_local=(0,-1,-10));box(b,'head','skull',(-4.5,-4,-5),(9,7,7))
        box(b,'head','muzzle',(-3,-1,-8),(6,4,4));box(b,'head','nose',(-2,-1,-8.5),(4,2,1))
        box(b,'head','throat',(-3,2,-5),(6,2,6))
        for side,s in [('right',-1),('left',1)]:
            b.bone('ear_'+side,'head',at_local=(s*3,-4,0));box(b,'ear_'+side,'ear_'+side,(-1,-2,-1),(2,3,2))
            box(b,'head','eye_'+side,((-4.55 if s<0 else 3.55),-2,-3.6),(1,1,2))
        for front,z in [('front',-6),('hind',7)]:
            for side,s in [('right',-1),('left',1)]:
                leg=front+'_'+side;b.bone(leg,'body',at_local=(s*5,0,z));box(b,leg,'leg_'+leg,(-2,-1,-2),(4,hip-2,4))
                b.bone(leg+'_paw',leg,at_local=(0,hip-3,-.6));box(b,leg+'_paw','paw_'+leg,(-2.5,0,-2.4),(5,3,6))
                for k in [-1,1]:box(b,leg+'_paw','claw_'+leg+str(k).replace('-','n'),(k-.4,1,-2.8),(1,1,2))
        for i in range(4 if badger else 5):
            for s in [-1,1]:
                bn=f'plate_{i}_{s}'.replace('-','n');b.bone(bn,'body',at_local=(s*3,-5,-6+i*4),rot=(8,0,-s*13))
                box(b,bn,'bark_'+bn if badger else 'armor_'+bn,(-3,-1.5,-2.5),(6,2,6))
        parent='body'
        for i in range(1 if badger else 3):
            n='tail_'+str(i);b.bone(n,parent,at_local=(0,0,9) if i==0 else (0,0,6),rot=(12 if i==0 else 18,0,0))
            w=(4 if badger else 6-i);box(b,n,'tail_'+str(i),(-w/2,-w/2,0),(w,w,7));parent=n
    elif name=='frostplume_pheasant':
        b.bone('body','root',at=(0,-12,0));box(b,'body','body',(-3.5,-4,-3),(7,8,10))
        box(b,'body','breast',(-3,-4,-5),(6,8,4));b.bone('head','body',at_local=(0,-5,-4))
        box(b,'head','neck',(-2,-3,-1.5),(4,7,4));box(b,'head','skull',(-2.5,-6,-3),(5,5,5))
        box(b,'head','beak',(-1,-4.5,-6),(2,2,4));box(b,'head','crest',(-.5,-7,-1),(1,2,6))
        for side,s in [('right',-1),('left',1)]:
            box(b,'head','eye_'+side,((-2.55 if s<0 else 1.55),-5,-1.8),(1,1,1))
            b.bone('wing_'+side,'body',at_local=(s*3,-3,-1),rot=(0,s*7,s*10))
            for i in range(3):box(b,'wing_'+side,'feather_'+side+str(i),(s*.4-.5,i*1.2,i*2),(1,6-i,6))
            b.bone('leg_'+side,'body',at_local=(s*2,3,2));box(b,'leg_'+side,'leg_'+side,(-.6,0,-.6),(1,6,1))
            box(b,'leg_'+side,'paw_'+side,(-1.4,8,-2.7),(3,1,4));box(b,'leg_'+side,'shin_'+side,(-.6,5,-.6),(1,4,1))
        for i in range(3):
            n='tail_'+str(i);b.bone(n,'body',at_local=((i-1)*1.7,-2,5),rot=(28,(i-1)*9,0));box(b,n,'feather_tail_'+str(i),(-1,-.5,0),(2,1,15-abs(i-1)*2))
    elif name=='lantern_sporeling':
        b.bone('body','root',at=(0,-10,0));box(b,'body','bark_trunk',(-3,-8,-3),(6,13,6))
        b.bone('head','body',at_local=(0,-9,0))
        for n,x,y,z,size in [('main',1,-2,0,14),('side',-5,5,1,10)]:
            b.bone('cap_'+n,'head',at_local=(x,y,z),rot=(0,0,-6 if n=='side' else 5))
            for tier in range(3):
                w=size-tier*3;box(b,'cap_'+n,'cap_'+n+str(tier),(-w/2,-tier*2,-w/2),(w,2,w))
            box(b,'cap_'+n,'gill_'+n,(-size/2+.5,1,-size/2+.5),(size-1,1,size-1))
        for i,(x,z) in enumerate([(-4,-2),(4,-2),(0,4)]):
            n='root_'+str(i);b.bone(n,'body',at_local=(x,2,z),rot=(0,0,x*3));box(b,n,'bark_'+n,(-1.5,0,-1.5),(3,5,3))
            b.bone(n+'_foot',n,at_local=(0,5,0),rot=(0,0,-x*3));box(b,n+'_foot','paw_'+n,(-2,0,-2.5),(4,3,5))
        for i,(x,y,z) in enumerate([(-4,-6,-3),(4,-10,-3),(2,-9,4)]):
            n='spore_'+str(i);b.bone(n,'body',at_local=(x,y,z));box(b,n,'cord_'+n,(-.5,0,-.5),(1,3,1));box(b,n,'glow_'+n,(-1.5,3,-1.5),(3,3,3))
        for s in [-1,1]:
            n='arm_'+('right' if s<0 else 'left');b.bone(n,'body',at_local=(s*3,-1,0),rot=(0,0,-s*35));box(b,n,'bark_'+n,(-1,0,-1),(2,7,2))
    else:raise ValueError(name)
    return b.model

def painter(name):
    m=entry(name);palette=[tuple(bytes.fromhex(c)) for c in m['palette']]
    def paint(t):
        n=t.cube;c=palette[0]
        if n.startswith(('bark','armor','cap','feather','ear','chest')):c=palette[1] if n.startswith(('bark','feather')) else palette[0]
        if n.startswith(('paw','claw','nose','leg','shin','cord')):c=(64,58,49)
        if name=='redplate_lizard' and n in ('body','chest'):c=(68,71,73)
        if name=='redplate_lizard' and n.startswith('tail_'):c=palette[0]
        if name=='frostplume_pheasant' and n=='beak':c=(77,87,97)
        if n.startswith(('muzzle','throat','gill','beak')):c=palette[2]
        if n.startswith('eye'):return (194,132,48,255) if name!='frostplume_pheasant' else (173,204,216,255)
        if n.startswith('glow'):return (248,192,85,255)
        # Badger face stripes live on geometry coordinates and run continuously across faces.
        if name=='thornback_badger' and n=='skull':
            c=(220,210,178) if abs(t.local[0])<1.8 else (53,44,35)
        if name=='frostplume_pheasant' and n in ('neck','skull') and t.face!='SOUTH':
            if t.local[1]>-3:c=(214,219,210)
        if name=='frostplume_pheasant' and n.startswith('feather_tail') and t.local[2]>10:c=palette[2]
        # Broad pixel clusters, light upper edges and quiet material bands; no photographic noise.
        u,v=int(t.fu*16),int(t.fv*16)
        band=((u//3+v//4)%3-1)*.045
        shade=.88+max(0,-t.n[1])*.14+band
        if n.startswith(('bark','armor','cap','feather')) and (v in (0,1,14,15)):shade+=.10
        if n.startswith('bark') and u%7==0:shade-=.13
        return tuple(max(0,min(255,int(x*shade))) for x in c)+(255,)
    return paint

def glow(name):
    def draw(t):
        if t.cube.startswith('glow'):return (95,63,15,255)
        if t.cube.startswith('eye'):return (25,40,40,255)
        return None
    return draw

def clips(model,server):
    names={b.name for b in model.bones}
    def pose(**rot):return {n:{'rotation':v} for n,v in rot.items() if n in names}
    idle=anim.Clip('idle',3,True,[anim.Key(0,{}),anim.Key(1.5,{'body':{'position':(0,.18,0)}}),anim.Key(3,{})])
    result=[idle]
    limbs=[n for n in names if n in ('front_right','front_left','hind_right','hind_left','leg_right','leg_left','root_0','root_1','root_2')]
    for clip,amp in [('walk',18),('run',30)]:
        keys=[]
        for k in range(9):
            p={}
            for i,n in enumerate(sorted(limbs)):
                phase=2*math.pi*k/8+(math.pi if i in (1,2) else 0)
                p[n]={'rotation':(amp*math.sin(phase),0,0)}
            for n in names:
                if n.startswith('tail_'):p[n]={'rotation':(0,4*math.sin(2*math.pi*k/8),0)}
            keys.append(anim.Key(k/8,p,'linear'))
        result.append(anim.Clip(clip,1,True,keys))
    result.append(anim.Clip('stagger',.5,False,[anim.Key(0,{}),anim.Key(.1,pose(body=(-9,0,0),head=(12,0,0))),anim.Key(.5,{})]))
    for move in server['moves']:
        name=move['animation'];w=move['windup_ticks'];end=move['total_ticks'];wind=pose(body=(9,0,0),head=(-12,0,0));hit=pose(body=(-7,0,0),head=(10,0,0))
        if name=='tail_sweep':wind=pose(body=(0,-24,0),tail_0=(0,-40,0));hit=pose(body=(0,32,0),tail_0=(0,70,0),tail_1=(0,25,0))
        if name in ('spores','sigil','talisman'):
            wind=pose(head=(-8,0,0),arm_right=(-85,0,15),forearm_right=(-35,0,0),arm_left=(-45,0,-15));hit=pose(head=(6,0,0),arm_right=(-75,0,0),arm_left=(-80,0,0))
        if name=='feathers':wind=pose(head=(-15,0,0),wing_right=(0,0,-35),wing_left=(0,0,35));hit=pose(head=(12,0,0),wing_right=(-20,0,30),wing_left=(-20,0,-30))
        result.append(anim.Clip(name,end/20,False,[anim.Key(0,{}),anim.Key((w-5)/20,wind),anim.Key((w-1)/20,wind),anim.Key((w+1)/20,hit),anim.Key((end-1)/20,{}),anim.Key(end/20,{})]))
    return result

def authored_texture(name,model,fallback):
    """Only validated material tones may influence broad surfaces; native face details remain exact."""
    path=ROOT/'art/entities'/name/'material_tones.png'
    if not path.exists():return fallback
    from tools.gen_qingfeng_sword_model import decode_png_rgba
    from .paint import texels
    w,h,raw=decode_png_rgba(path.read_bytes())
    if (w,h)!=model.texture_size:raise ValueError('authored atlas dimensions differ: '+name)
    rows=[list(row) for row in fallback]
    for t in texels(model,{c.name for _,c in model.cubes()}):
        if t.cube.startswith(('eye','skull','muzzle','nose','throat','glow','skin','hand','face','hair')):continue
        p=(t.v*w+t.u)*4
        if raw[p+3]==0:continue
        tone=sum(raw[p:p+3])/3
        factor=1.055 if tone>170 else .95 if tone<85 else 1
        base=rows[t.v][t.u]
        rows[t.v][t.u]=tuple(min(255,round(x*factor)) for x in base[:3])+(base[3],)
    return rows
