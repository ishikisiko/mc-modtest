"""连续折岭地形：不使用独立圆锥拼成两侧边山。"""
from __future__ import annotations
import math

def polygon_distance(x,z,points):
    result=float('inf')
    for a,b in zip(points,points[1:]+points[:1]):
        dx,dz=b[0]-a[0],b[1]-a[1];den=dx*dx+dz*dz
        t=max(0,min(1,((x-a[0])*dx+(z-a[1])*dz)/den)) if den else 0
        result=min(result,math.hypot(x-a[0]-t*dx,z-a[1]-t*dz))
    return result

def section(points,value):
    for (a,ha),(b,hb) in zip(points,points[1:]):
        if a<=value<=b:return ha+(hb-ha)*(value-a)/(b-a)
    return points[-1][1] if value>=points[-1][0] else points[0][1]

def ridge_height(x,z,ridge,base):
    height=base
    for a,b in zip(ridge['points'],ridge['points'][1:]):
        dx,dz=b[0]-a[0],b[1]-a[1];length2=dx*dx+dz*dz
        if not length2:raise ValueError('山脊控制点不能重合')
        t=max(0,min(1,((x-a[0])*dx+(z-a[1])*dz)/length2))
        cx,cz=a[0]+t*dx,a[1]+t*dz
        side=0 if dx*(z-cz)-dz*(x-cx)>=0 else 1
        width=a[3+side]+(b[3+side]-a[3+side])*t
        r=math.hypot(x-cx,z-cz)/width
        if r>=1:continue
        top=a[2]+(b[2]-a[2])*t
        height=max(height,base+(top-base)*section(ridge['sections'][side],r))
    return height


def cavern_radius(x,z,cavern):
    cx,cz,rx,rz=cavern['shell']
    dx,dz=(x-cx)/rx,(z-cz)/rz
    theta=math.atan2(dz,dx)
    rim=1-cavern['ridge_variation']*(1-.60*math.sin(3*theta+.4)-.40*math.cos(5*theta))
    return (dx**2+dz**2)/rim**2


def cavern_surface(x,z,floor,cavern):
    radius=cavern_radius(x,z,cavern)
    if radius>=1:
        return floor
    return math.floor(floor+max(0,cavern['crest_y']+1-floor)*(1-radius)**.8+.5)


def cavern_air(x,y,z,ground,first_air,cavern):
    if y<=ground or not cavern['floor_y']<=y<=cavern['crest_y']:
        return False
    for a,b,c,aa,bb,cc in cavern['tunnels']:
        if a<=x<=aa and b<=y<=bb and c<=z<=cc:
            return True
    if cavern_radius(x,z,cavern)>=1:
        return False
    # 天窗自拱顶向下贯通，洞室外轮廓沿用同一座山。
    if any(y>=sill and ((x-a)/rx)**2+((z-b)/rz)**2<1
           for a,b,rx,rz,sill in cavern['openings']):
        return True
    if y>=first_air-cavern['roof_thickness']:
        return False
    return any(
        ((x-a)/rr)**2+((y-b)/ry)**2+((z-c)/rz2)**2<1
        for a,b,c,rr,ry,rz2 in cavern['chambers'])
