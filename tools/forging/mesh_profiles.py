"""Lofted forged surfaces with the original part materials."""
from collections import defaultdict


def cross(a,b,c):
    u=[y-x for x,y in zip(a,b)]
    v=[z-x for x,z in zip(a,c)]
    return (u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])


def points_for(axis, pos, u, v):
    return (pos,u,v) if axis=='x' else (u,pos,v) if axis=='y' else (u,v,pos)


def octagon(a,b,c):
    d=min(c,a*.73)
    e=min(c,b*.73)
    return [(-a+d,-b),(a-d,-b),(a,-b+e),(a,b-e),(a-d,b),(-a+d,b),(-a,b-e),(-a,-b+e)]


class Surface:
    """Choose UV rectangles from the same catalogued part of each shipped item."""
    def __init__(self, before):
        self.parts=defaultdict(list)
        self.refs={f['texture'] for f in before}
        self.default=before
        for face in before:
            self.parts[face['part']].append(face)

    def face(self, part, points, hint=None, centre=None, remit=False):
        points=list(points)
        n=cross(*points[:3])
        if centre is not None and sum(v*(p-o) for v,p,o in zip(n,points[0],centre))<0:
            points.reverse()
            n=cross(*points[:3])
        if remit:
            points.reverse()
            n=cross(*points[:3])
        candidates=self.parts.get(hint,[])
        if not candidates and hint:
            candidates=[face for key,group in self.parts.items() if hint in key for face in group]
        if not candidates:
            candidates=self.default
        # Reuse the closest facing patch of the source piece. No new texture pixels are authored.
        def facing(face):
            other=cross(*face['points'][:3])
            size=sum(k*k for k in other)**.5 or 1
            return sum(a*b/size for a,b in zip(n,other))
        material=max(candidates,key=facing)
        uv=material['uv']
        if len(points)==4:
            use=uv
        else:
            # A cap can differ in vertex count while staying within its original atlas rectangle.
            dimensions=[max(p[i] for p in points)-min(p[i] for p in points) for i in range(3)]
            u,v=sorted(range(3),key=dimensions.__getitem__,reverse=True)[:2]
            pu,pv=min(p[u] for p in points),min(p[v] for p in points)
            lo_u,hi_u=min(x for x,_ in uv),max(x for x,_ in uv)
            lo_v,hi_v=min(y for _,y in uv),max(y for _,y in uv)
            use=[(lo_u+(p[u]-pu)/max(1e-6,dimensions[u])*(hi_u-lo_u),
                  hi_v-(p[v]-pv)/max(1e-6,dimensions[v])*(hi_v-lo_v)) for p in points]
        return {'part':part,'points':points,'uv':use,'texture':material['texture'],
                'metal':material.get('metal',True)}


def rings_for(axis, stations, section):
    return [[points_for(axis,pos,x+u,z+v) for u,v in section(a,b,c)]
            for pos,x,z,a,b,c in stations]


def average(vertices):
    return tuple(sum(p[i] for p in vertices)/len(vertices) for i in range(3))


def solid(surface,name,axis,stations,hints,section=octagon):
    """Join stations into one tapered piece with a real end cap."""
    rings=rings_for(axis,stations,section)
    faces=[]
    if isinstance(hints,str):hints=[hints]*(len(rings)-1)
    assert len(hints)==len(rings)-1
    for i,(group,after) in enumerate(zip(rings,rings[1:])):
        centre=average(group+after)
        for j in range(len(group)):
            nxt=(j+1)%len(group)
            faces.append(surface.face(name,[group[j],group[nxt],after[nxt],after[j]],hints[i],centre))
    faces.extend(surface.face(name,rings[where],hints[where],average(rings[where]+rings[adjacent]))
                 for where,adjacent in ((0,1),(-1,-2)))
    return faces


def quads(faces):
    """Split convex bevel caps into quads that the NeoForge OBJ loader can bake."""
    for face in faces:
        if len(face['points']) == 4:
            yield face
            continue
        size = len(face['points'])
        for i in range(1, size - 1, 2):
            indices = (0, i, i+1, min(i+2, size-1))
            yield dict(face, points=[face['points'][n] for n in indices], uv=[face['uv'][n] for n in indices])


def socketed_beam(surface,name,stations,hints,opening,half_width,roof):
    """Cut a blind socket from the underside, leaving the beam's top face solid."""
    rings=rings_for('z',stations,octagon)
    z0,z1=opening
    result=[]
    for i,(near,far) in enumerate(zip(rings,rings[1:])):
        centre=average(near+far)
        inside=stations[i][0]==z0 and stations[i+1][0]==z1
        for j in range(8):
            k=(j+1)%8
            if not inside or j!=0:
                result.append(surface.face(name,[near[j],near[k],far[k],far[j]],hints[i],centre))
                continue
            # Keep both edge strips on the underside around the socket mouth.
            for a in (j,k):
                x=8+(-half_width if near[a][0]<8 else half_width)
                hole_near=(x,near[a][1],z0)
                hole_far=(x,far[a][1],z1)
                result.append(surface.face(name,[near[a],hole_near,hole_far,far[a]],hints[i],centre))
        if inside:
            def opening_at(ring,z):
                low,high=ring[0][1],ring[4][1]-roof
                return [(8-half_width,low,z),(8+half_width,low,z),
                        (8+half_width,high,z),(8-half_width,high,z)]
            a,b=opening_at(near,z0),opening_at(far,z1)
            hole_centre=average(a+b)
            for face in (a,b,[a[0],a[3],b[3],b[0]],[a[1],a[2],b[2],b[1]],[a[3],a[2],b[2],b[3]]):
                result.append(surface.face(name+'的孔壁',face,hints[i],hole_centre,remit=True))
    result.extend(surface.face(name,rings[where],hints[where],average(rings[where]+rings[adjacent]))
                  for where,adjacent in ((0,1),(-1,-2)))
    return result


def tool_faces(templates,rid,stage,before):
    surface=Surface(before)
    spec=templates['模板'][rid]
    result=[]
    for part_index,part in enumerate(spec['粗坯' if stage==0 else '成形坯']):
        sections=part['sections'][:]
        if stage>=4:
            for change in spec['精磨截面覆盖']:
                if change['part_index']==part_index:
                    sections[change['section_index']]=change['section']
        if 'socket' in part:
            hole=part['socket']
            result+=socketed_beam(surface,part['part'],sections,part['surface_parts'],
                                  hole['z_range'],hole['half_width_x'],templates['坐标']['柄孔顶壁厚度'])
        else:
            result+=solid(surface,part['part'],part['axis'],sections,part['surface_parts'])
    if stage==5:
        grip=spec['成品柄']
        result+=solid(surface,grip['part'],grip['axis'],grip['sections'],grip['surface_parts'])
    assert {face['texture'] for face in result}==surface.refs, rid
    return list(quads(result))
