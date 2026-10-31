"""精雕方块的可见表面；保留原体积/状态编号，仅裁掉重复与被遮住的面。"""
from __future__ import annotations

from collections import defaultdict

# 面、法向轴、正向、U轴及方向、V轴及方向。方向遵循原版 FaceInfo 的顶点次序。
FACES=(('west',0,False,2,True,1,False),('east',0,True,2,False,1,False),
       ('down',1,False,0,True,2,False),('up',1,True,0,True,2,True),
       ('north',2,False,0,False,1,False),('south',2,True,0,True,1,False))


def subtract(rect,cover):
    """矩形差集，返回互不重叠的余面；边/点接触不算覆盖。"""
    x0,y0,x1,y1=rect
    a,b=max(x0,cover[0]),max(y0,cover[1])
    c,d=min(x1,cover[2]),min(y1,cover[3])
    if a>=c or b>=d:return [rect]
    return [r for r in ((x0,y0,a,y1),(c,y0,x1,y1),(a,y0,c,b),(a,d,c,y1))
            if r[0]<r[2] and r[1]<r[3]]


def merge(rects):
    """合并同一原始面的相邻余面，避免为隐藏表面制造额外四边形。"""
    changed=True
    while changed:
        changed=False
        for axis in (0,1):
            groups=defaultdict(list);other=1-axis
            for rect in rects:groups[(rect[other],rect[other+2])].append(rect)
            result=[]
            for group in groups.values():
                group.sort(key=lambda r:r[axis]);last=group[0]
                for rect in group[1:]:
                    if last[axis+2]==rect[axis]:
                        combined=list(last);combined[axis+2]=rect[axis+2];last=tuple(combined);changed=True
                    else:result.append(last);last=rect
                result.append(last)
            rects=result
    return sorted(rects)


def priority(box,index,definitions):
    volume=(box[3]-box[0])*(box[4]-box[1])*(box[5]-box[2])
    # 实心面遮住同面的玻璃；局部饰面优于大面积基底。完全同形则使用固定 profile 次序。
    return definitions[box[6]]['opaque'],-volume,index


def visible(profile,index,face,definitions):
    _,axis,positive,u,_,v,_=face
    box=profile[index];plane=box[axis+3 if positive else axis]
    rect=(box[u],box[v],box[u+3],box[v+3]);remaining=[rect]
    own=priority(box,index,definitions)
    for j,other in enumerate(profile):
        if index==j:continue
        outward=(other[axis]<=plane<other[axis+3]) if positive else (other[axis]<plane<=other[axis+3])
        blocked=outward and (definitions[other[6]]['opaque'] or other[6]==box[6])
        coplanar=other[axis+3 if positive else axis]==plane
        if not blocked and not(coplanar and priority(other,j,definitions)>own):continue
        cover=(other[u],other[v],other[u+3],other[v+3])
        remaining=[part for previous in remaining for part in subtract(previous,cover)]
        if not remaining:break
    return merge(remaining) if len(remaining)>1 else remaining


def cropped_uv(box,face,rect,uv):
    _,_,_,u,forward_u,v,forward_v=face
    result=[]
    for axis,forward,low,high,start,end in (
        (u,forward_u,rect[0],rect[2],uv[0],uv[2]),
        (v,forward_v,rect[1],rect[3],uv[1],uv[3])):
        a=(low-box[axis])/(box[axis+3]-box[axis]);b=(high-box[axis])/(box[axis+3]-box[axis])
        if not forward:a,b=1-b,1-a
        result.append((round(start+(end-start)*a,6),round(start+(end-start)*b,6)))
    return [result[0][0],result[1][0],result[0][1],result[1][1]]
