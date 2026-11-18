"""Tool grips and hammerheads with separate pixel materials."""
import io
import math
import random
from PIL import Image
from tools.forging.mesh_profiles import quads

PREFIX = "yuanxian:block/workshop/finish/"

PALETTES={
 'iron':((95,111,114),'polish',.40,.77),'face':((160,174,169),'polish',.27,.85),
 'black':((50,64,72),'iron',.62,.76),'darkface':((112,137,140),'polish',.36,.84),
 'copper':((143,88,62),'copper',.44,.77),'copper_face':((210,143,92),'polish',.32,.81),
 'alloy':((64,80,91),'alloy',.35,.84),'alloy_face':((132,160,164),'polish',.27,.90),
 'brass':((161,133,74),'polish',.40,.74),'oxide':((80,100,87),'copper',.59,.51),
 'steel':((113,130,134),'iron',.48,.74),'edge':((167,178,173),'polish',.29,.82),
 'wood':((108,80,50),'wood',.86,.0),'leather':((63,47,39),'leather',.89,.0),
 'bamboo':((135,150,83),'bamboo',.69,.0),'bamboo_node':((111,122,61),'wood',.73,.0),
 'ningyuan':((77,110,87),'wood',.77,.0),'jade':((93,150,122),'bone',.49,.10),
 'bone':((166,157,126),'bone',.78,.0),'bone_rim':((119,104,79),'bone',.82,.0),
}

def material_image(color,kind,seed):
    rng=random.Random(seed)
    size=64
    broad=[[rng.uniform(-1,1) for _ in range(10)] for _ in range(10)]
    image=Image.new('RGBA',(size,size))
    for y in range(size):
        for x in range(size):
            coarse=broad[y//7][x//7]
            medium=broad[(x+2*y)%10][(y//3+x//5)%10]
            gr=coarse*9+medium*4+rng.randint(-3,3)
            tone=[0,0,0]
            if kind=='polish':
                gr=coarse*5+medium*2+rng.randint(-2,2)+((x+(y//3))%5-2)*1.8
            elif kind=='wood':
                gr+=math.sin((x+math.floor(y/17))/3)*9+(-9 if x%11==(y//32) else 0)
            elif kind=='leather':
                gr+=-7 if x%9<2 else 0
                gr+=13 if (x*3+y)%31==4 else 0
            elif kind=='bamboo':
                gr+=3*(x%5-2)+(-15 if y%23 in (3,4) else 0)
            elif kind=='bone':
                gr+=3*(y%6-2) if coarse<-.4 else 0
            elif kind=='copper':
                if coarse<-.53:tone=[-26,12,9]
                elif coarse>.55:tone=[11,1,-6]
            elif kind=='alloy':
                tone=[1,5,7] if coarse>.52 else [2,-2,-5]
                gr+=8 if ((x-y)%29==1 and x%12<8) else 0
            image.putpixel((x,y),tuple(int(max(0,min(255,c+gr+v))) for c,v in zip(color,tone))+(255,))
    return image

def textures():
    used = {face['texture'].rsplit('/',1)[1] for faces in all_profiles().values() for face in faces}
    for i, (name, (color, kind, _, _)) in enumerate(PALETTES.items()):
        if name not in used:
            continue
        out = io.BytesIO()
        material_image(color, kind, 420+i).save(out, 'PNG')
        yield f'assets/yuanxian/textures/block/workshop/finish/{name}.png', out.getvalue()


def upper_section(a,b,cut):
    u=min(a*0.72,cut);v=min(b*.72,cut)
    return [(-a+u,-b),(a-u,-b),(a,-b+v),(a,b-v),(a-u,b),(-a+u,b),(-a,b-v),(-a,-b+v)]

def loft(name, axis, stations, segment_materials, end_materials=None):
    """A closed, hard-edge body. Each station gives length, lateral centre and bevel."""
    assert len(stations)==len(segment_materials)+1
    rings=[]
    for pos,uc,vc,hu,hv,cut in stations:
        ring=[]
        for u,v in upper_section(hu,hv,cut):
            a,b=u+uc,v+vc
            ring.append((pos,a,b) if axis=='x' else (a,pos,b) if axis=='y' else (a,b,pos))
        rings.append(ring)
    names=end_materials or (segment_materials[0],segment_materials[-1])
    center=[sum(p[k] for ring in rings for p in ring)/(8*len(rings)) for k in range(3)]
    result=[]
    def face(points, material,cap=False):
        a,b,c=points[:3]
        u,v=[[p-d for p,d in zip(q,a)] for q in (b,c)]
        n=(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
        if sum(n[i]*(a[i]-center[i]) for i in range(3))<0:points=list(reversed(points))
        if cap:
            plane=(1,2) if axis=='x' else (0,2) if axis=='y' else (0,1)
            lo=[min(p[i] for p in points) for i in plane];hi=[max(p[i] for p in points) for i in plane]
            uv=[tuple((p[i]-lo[j])/max(hi[j]-lo[j],1e-6) for j,i in enumerate(plane)) for p in points]
        else:uv=[(0,1),(0,0),(1,0),(1,1)]
        result.append({'part':name,'points':points,'uv':uv,'texture':PREFIX+material})
    def cap(ring,material):
        xs=ring[:]
        face(xs,material,True)
    cap(rings[0],names[0])
    for i,(near,far) in enumerate(zip(rings,rings[1:])):
        for j in range(8):
            k=(j+1)%8
            face([near[j],near[k],far[k],far[j]],segment_materials[i])
    cap(rings[-1],names[1])
    return result

def bar(name,axis,sections,connectors,terms=None):
    center=(7.6,8.) if axis=='x' else (8.,8.)
    return loft(name,axis,[(pos,center[0],center[1],a,b,c) for pos,a,b,c in sections],connectors,terms)

HAMMERS={
 'forging_hammer':(
    [(5.3,1.31,1.31,.20),(5.65,1.48,1.47,.25),(6.14,1.48,1.47,.25),(6.39,1.24,1.18,.22),
     (8.93,1.24,1.18,.22),(9.50,1.34,1.28,.24),(10.31,1.34,1.28,.24),(10.64,1.19,1.15,.19)],
    ['face','iron','iron','iron','iron','iron','face'],('face','face'),'wood','iron','brass'),
 'xuantie_forging_hammer':(
    [(5.27,1.37,1.39,.24),(5.65,1.53,1.54,.28),(6.11,1.53,1.54,.28),(6.47,1.28,1.29,.28),
     (8.71,1.28,1.29,.28),(9.14,1.45,1.38,.30),(10.38,1.38,1.22,.29),(10.78,1.22,1.08,.21)],
    ['darkface','black','darkface','black','black','black','darkface'],('darkface','darkface'),'black','darkface','darkface'),
 'chitong_forging_hammer':(
    [(5.28,1.28,1.30,.22),(5.58,1.48,1.46,.24),(5.95,1.48,1.46,.25),(6.39,1.22,1.15,.24),
     (8.76,1.22,1.13,.25),(9.24,.86,1.24,.23),(10.45,.43,1.27,.15),(10.76,.33,1.14,.13)],
    ['copper_face','copper','copper_face','copper','copper','copper','copper_face'],('copper_face','copper_face'),'ningyuan','copper','oxide'),
 'netherite_forging_hammer':(
    [(5.26,1.37,1.42,.29),(5.68,1.57,1.64,.32),(6.21,1.57,1.64,.32),(6.58,1.30,1.33,.27),
     (8.79,1.30,1.33,.27),(9.15,1.56,1.56,.33),(10.31,1.44,1.43,.29),(10.72,1.28,1.29,.23)],
    ['alloy_face','alloy','alloy_face','alloy','alloy_face','alloy','alloy_face'],('alloy_face','alloy_face'),'alloy','black','brass'),
}

def new_hammer(key):
    shape,fill,working,shaft,ferrule,ring=HAMMERS[key]
    result=bar('锤头', 'x',shape,fill,working)
    handle=[(-1,.35,.35,.105),(-.72,.53,.52,.13),(-.31,.53,.52,.13),(.15,.47,.46,.13),
            (4.21,.47,.43,.12),(4.57,.43,.41,.14),(5.64,.45,.42,.14),(5.88,.51,.48,.13),(7.6,.48,.46,.13)]
    result+=bar('锤柄','y',handle,[ferrule,ring,'leather','leather','leather',shaft,ring,ferrule])
    result+=bar('楔帽','y',[(9.14,.31,.29,.08),(9.32,.33,.31,.10),(9.39,.28,.26,.08)],
                [ferrule,'face'])
    return result

HANDLES={
 'wood':('wood','leather','iron',[(0,.64,.64,.17),(1.05,.85,.82,.20),(2.2,.87,.82,.21),(7.4,.77,.70,.19),(11.8,.70,.65,.19),(12.3,.79,.70,.17),(13.25,.79,.70,.17),(15,.69,.64,.16)]),
 'bamboo':('bamboo','bamboo_node','brass',[(0,.65,.63,.17),(1.15,.77,.75,.19),(2.4,.82,.76,.19),(7.0,.76,.70,.18),(11.8,.71,.66,.16),(12.3,.79,.73,.16),(13.25,.82,.73,.16),(15,.68,.64,.16)]),
 'ningyuan':('ningyuan','leather','jade',[(0,.69,.64,.22),(1.15,.94,.86,.24),(2.4,.87,.79,.22),(7.65,.74,.70,.22),(11.8,.72,.65,.23),(12.3,.79,.73,.17),(13.25,.83,.75,.17),(15,.70,.64,.17)]),
 'thorn_bone':('bone','bone_rim','bone_rim',[(0,.61,.62,.20),(1.15,.87,.77,.23),(2.4,.96,.82,.27),(7.5,.72,.72,.22),(11.8,.79,.64,.19),(12.3,.83,.74,.17),(13.25,.83,.76,.17),(15,.67,.64,.17)]),
 'xuantie':('black','leather','darkface',[(0,.66,.65,.20),(1.09,.89,.84,.20),(2.4,.88,.79,.21),(7.4,.78,.67,.20),(11.8,.72,.67,.17),(12.3,.86,.78,.17),(13.25,.86,.78,.17),(15,.69,.65,.17)]),
 'chitong':('copper','oxide','copper_face',[(0,.68,.67,.17),(1.2,.89,.85,.20),(2.4,.85,.77,.21),(7.36,.72,.66,.18),(11.8,.70,.66,.18),(12.3,.81,.76,.18),(13.25,.84,.75,.18),(15,.68,.65,.18)]),
}

def new_handle(key):
    base,grip,collar,stations=HANDLES[key]
    faces=bar('连续柄身','y',stations,[collar,base,grip,base,base,collar,base])
    if key in ('bamboo','thorn_bone'):
        bands = (4.3,8.2,11.2) if key=='bamboo' else (2.3,10.7)
        for i,y in enumerate(bands):
            faces+=bar('柄节'+str(i),'y',[(y,.82,.80,.21),(y+.29,.87,.83,.21),(y+.46,.83,.79,.19)],[grip,grip])
    return faces

def all_profiles():
    return {**{name:list(quads(new_hammer(name))) for name in HAMMERS},
            **{name+'_handle':list(quads(new_handle(name))) for name in HANDLES}}
