"""把完整造型裁入方块格，合并相邻同材质体积并复用水平旋转。"""
from __future__ import annotations
from collections import defaultdict
from functools import lru_cache

FULL=(0,0,0,16,16,16)


@lru_cache(maxsize=None)
def normalize(raw):
    boxes=list(set(raw));changed=True
    while changed:
        changed=False
        for axis in range(3):
            groups=defaultdict(list);others=[i for i in range(6) if i not in (axis,axis+3)]
            for b in boxes:groups[(b[6:],*(b[i] for i in others))].append(b)
            result=[]
            for group in groups.values():
                group.sort(key=lambda b:b[axis]);last=group[0]
                for b in group[1:]:
                    if b[axis]<=last[axis+3]:
                        v=list(last);v[axis+3]=max(v[axis+3],b[axis+3]);last=tuple(v);changed=True
                    else:result.append(last);last=b
                result.append(last)
            boxes=result
    # Fully enclosed boxes have no visible surface and add no collision volume.
    keep=[]
    for i,b in enumerate(boxes):
        if not any(i!=j and b[6:]==a[6:] and all(a[k]<=b[k] and a[k+3]>=b[k+3] for k in range(3)) for j,a in enumerate(boxes)):keep.append(b)
    return tuple(sorted(keep,key=repr))


def clockwise(profile):
    return tuple(sorted(((16-b[5],b[1],b[0],16-b[2],b[4],b[3],*b[6:]) for b in profile),key=repr))


@lru_cache(maxsize=None)
def canonical(raw):
    p=normalize(raw)
    if any(len(b)>7 for b in p):return p,0
    turns=[p]
    for _ in range(3):turns.append(clockwise(turns[-1]))
    index=min(range(4),key=lambda i:repr(turns[i]))
    return turns[index],(-index)%4


def cells(scene,engraved_roles=(),overlay_roles=()):
    result=defaultdict(list)
    for b in scene['boxes']:
        if b[7]=='edict_stone':continue
        lo=[round((b[i]-b[i+3]/2)*16) for i in range(3)]
        hi=[max(lo[i]+1,round((b[i]+b[i+3]/2)*16)) for i in range(3)]
        for y in range(lo[1]//16,(hi[1]+15)//16):
            for z in range(lo[2]//16,(hi[2]+15)//16):
                for x in range(lo[0]//16,(hi[0]+15)//16):
                    off=(x*16,y*16,z*16)
                    p=tuple(max(0,lo[i]-off[i]) for i in range(3))+tuple(min(16,hi[i]-off[i]) for i in range(3))+(b[6],)
                    if b[6] in engraved_roles:
                        low=[max(lo[i],off[i]) for i in range(3)];high=[min(hi[i],off[i]+16) for i in range(3)];uv=[]
                        for face in ('west','east','down','up','north','south'):
                            if face in ('north','south'):
                                u0,u1=[(p[0]-lo[0])/(hi[0]-lo[0])*16 for p in (low,high)]
                                v0,v1=[(hi[1]-p[1])/(hi[1]-lo[1])*16 for p in (high,low)]
                                if face=='north':u0,u1=16-u1,16-u0
                            elif face in ('east','west'):
                                u0,u1=[(p[2]-lo[2])/(hi[2]-lo[2])*16 for p in (low,high)]
                                v0,v1=[(hi[1]-p[1])/(hi[1]-lo[1])*16 for p in (high,low)]
                                if face=='east':u0,u1=16-u1,16-u0
                            else:
                                u0,u1=[(p[0]-lo[0])/(hi[0]-lo[0])*16 for p in (low,high)]
                                v0,v1=[(p[2]-lo[2])/(hi[2]-lo[2])*16 for p in (low,high)]
                            uv.extend(round(n,6) for n in (u0,v0,u1,v1))
                        p+=tuple(uv)
                    if p[:6]==FULL and b[6] not in overlay_roles:result[x,y,z]=[p]
                    else:result[x,y,z].append(p)
    return result


def add_plaques(target,scene):
    for index,(text,cx,cy,z,width) in enumerate(scene.get('signs',[])):
        lo=[round((cx-width/2)*16),round((cy-.65)*16),round((z-.08)*16)]
        hi=[round((cx+width/2)*16),round((cy+.65)*16),round((z+.08)*16)]
        for y in range(lo[1]//16,(hi[1]+15)//16):
            for zz in range(lo[2]//16,(hi[2]+15)//16):
                for x in range(lo[0]//16,(hi[0]+15)//16):
                    off=(x*16,y*16,zz*16)
                    low=[max(lo[i],off[i]) for i in range(3)];high=[min(hi[i],off[i]+16) for i in range(3)]
                    uv=(round((hi[0]-high[0])/(hi[0]-lo[0])*16,6),round((hi[1]-high[1])/(hi[1]-lo[1])*16,6),
                        round((hi[0]-low[0])/(hi[0]-lo[0])*16,6),round((hi[1]-low[1])/(hi[1]-lo[1])*16,6))
                    target[x,y,zz].append(tuple(low[i]-off[i] for i in range(3))+tuple(high[i]-off[i] for i in range(3))+(f'plaque_{index}',*uv))
