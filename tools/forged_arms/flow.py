"""Cutout emissive layer for the grooves already present in the Jinwu meshes."""
from io import BytesIO
import math
import numpy as np
from PIL import Image

SIZE = 64
FRAMES = 48
FRAME_TICKS = 2
CYCLE = FRAMES * FRAME_TICKS
PROFILES = {
    "sword": (8.50,27.80,7.17,.59,2.72,2.35),
    "spear": (37.00,46.75,36.76,1.06,2.85,1.62),
}
CORE = {"日轮中央斜切面", "日轮芯", "退火线", "枪颈日芯斜面", "枪颈日芯"}
RING = {"日轮内沿", "日轮压低一层的内缘"}


def clamp(value):
    return min(1,max(0,value))


def soft(value, low, high):
    u = clamp((value-low)/(high-low))
    return u*u*(3-2*u)


def envelope(t,a,b,c,d):
    return soft(t,a,b)*(1-soft(t,c,d))


def overlays(faces, family, ref, offset):
    lo,hi,ring_y,_,_,_ = PROFILES[family]
    result=[]
    for face in faces:
        part=face['part']
        mat=face['texture'].rsplit('/',1)[-1]
        p=np.array(face['points'])
        ys=p[:,1]-offset
        if part=="一体刃身与浮雕截面" and mat in ("rim","bronze"):
            if ys.max()<lo or ys.min()>hi:
                continue
            column=(.24 if mat=="bronze" else .08)
            phase=np.clip(1-(ys-lo)/(hi-lo),.008,.992)
        elif part in RING:
            column=.63
            angle=np.arctan2(ys-ring_y,p[:,2]-8)
            if angle.max()-angle.min()>math.pi:
                angle=np.where(angle<0,angle+math.tau,angle)
            phase=np.clip((angle+math.pi)/math.tau,.008,.992)
        elif part in CORE and mat in ('sun','sun_light','sun_dark','ember'):
            column=.90
            phase=np.array([uv[1] for uv in face['uv']])
        else:
            continue
        normal=np.cross(p[1]-p[0],p[2]-p[0])
        p+=(normal/np.linalg.norm(normal))*.0075
        uv=[(column+(uv[0]-.5)*.07,float(v)) for uv,v in zip(face['uv'],phase)]
        result.append({'part':'日魄流光','points':p.tolist(),'uv':uv,'texture':ref})
    if not result:
        raise ValueError(f"日魄乌金{family}缺少流光表面")
    return result


def color(kind, strength):
    if strength < .19:
        return (0,0,0,0)
    base,end={
        "blade":((155,100,49),(248,187,96)),
        "bronze":((123,79,41),(214,150,76)),
        "ring":((152,98,45),(252,196,104)),
        "core":((169,98,44),(245,163,76)),
    }[kind]
    return (*[round(a+(b-a)*strength) for a,b in zip(base,end)],255)


def texture(family):
    lo,hi,_,start,end,tail=PROFILES[family]
    atlas=Image.new('RGBA',(SIZE,SIZE*FRAMES))
    for frame in range(FRAMES):
        t=frame*FRAME_TICKS/20
        head=lo+(hi-lo)*clamp((t-start)/(end-start))
        gate=envelope(t,start,start+.24,end-.35,end)
        core=envelope(t,0,.38,.55,1.07)
        spear=family=="spear"
        turn=-.5*math.pi+1.74*math.pi*clamp((t-.24)/1.2)
        for row in range(SIZE):
            v=(row+.5)/SIZE
            y=hi-v*(hi-lo)
            delta=head-y
            gleam=math.exp(-.5*(delta/(tail if delta>=0 else .64))**2)*gate
            if spear:
                arc=(v*math.tau-math.pi-turn+math.pi)%math.tau-math.pi
                ring=math.exp(-.5*(arc/(.34 if arc>0 else .67))**2)*envelope(t,.24,.52,1.13,1.48)
            else:
                ring=envelope(t,.13,.42,.63,.97)*.71
            for x in range(SIZE):
                u=(x+.5)/SIZE
                kind,strength=(("blade",gleam),("bronze",gleam*.69),("ring",ring),("core",core*.67))[
                    0 if u<.16 else 1 if u<.38 else 2 if u<.78 else 3
                ]
                atlas.putpixel((x,frame*SIZE+row),color(kind,strength))
    out=BytesIO()
    atlas.save(out,'PNG',optimize=True)
    return out.getvalue(),{'animation':{'width':SIZE,'height':SIZE,'frametime':FRAME_TICKS,'frames':list(range(FRAMES))}}
