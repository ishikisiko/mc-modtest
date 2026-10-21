"""Authored sculpted cauldron mesh. Runtime and offline previews read its output.

Coordinates are controller-centred: +X right, +Z back. Static body/lid groups
are separate so assembly animation never needs to duplicate geometry in Java.
"""
from __future__ import annotations
import math

SHELL, LIGHT, DARK, TRIM, PATINA, JADE, FIRE, SOOT, STONE, CERAMIC, COPPER, ASH, RIVET, RUNE, EMBER, GOLD = range(16)
# Sixteen broad, uneven facets: cardinal panels stay wide enough for hatches.
SECTION = [(1,-.4),(1,.4),(.93,.68),(.68,.93),(.4,1),(-.4,1),(-.68,.93),(-.93,.68),
           (-1,.4),(-1,-.4),(-.93,-.68),(-.68,-.93),(-.4,-1),(.4,-1),(.68,-.93),(.93,-.68)]
BODY = [(-.82,.98),(-.62,1.36),(-.28,1.67),(.22,1.80),(.64,1.74),(.98,1.49),(1.13,1.38)]


class Mesh:
    def __init__(self):
        self.faces=[]
        self.group='body'

    def quad(self,p,m,glow=False):
        a,b,c=p[:3]
        u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
        n=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
        length=math.sqrt(sum(x*x for x in n))
        if length<1e-8:raise ValueError('degenerate face')
        n=[x/length for x in n]
        self.faces.append({'p':[[round(v,5) for v in pt] for pt in p], 'n':[round(v,5) for v in n],
                           'm':m,'g':self.group,'glow':glow})

    def box(self,lo,hi,m,glow=False,transform=None):
        x,y,z=lo;X,Y,Z=hi
        faces=[[(x,y,z),(x,Y,z),(X,Y,z),(X,y,z)],[(X,y,Z),(X,Y,Z),(x,Y,Z),(x,y,Z)],
               [(x,y,Z),(x,Y,Z),(x,Y,z),(x,y,z)],[(X,y,z),(X,Y,z),(X,Y,Z),(X,y,Z)],
               [(x,y,z),(X,y,z),(X,y,Z),(x,y,Z)],[(x,Y,Z),(X,Y,Z),(X,Y,z),(x,Y,z)]]
        for face in faces:self.quad([transform(p) for p in face] if transform else face,m,glow)

    def profile(self,rows,m,cap=False):
        for (y,r),(Y,R) in zip(rows,rows[1:]):
            for i,(x,z) in enumerate(SECTION):
                X,Z=SECTION[(i+1)%len(SECTION)]
                self.quad([(x*r,y,z*r),(x*R,Y,z*R),(X*R,Y,Z*R),(X*r,y,Z*r)],m)
        if cap:
            y,r=rows[-1]
            for i in range(0,16,2):
                a,b,c=[SECTION[(i+j)%16] for j in range(3)]
                self.quad([(0,y,0),(c[0]*r,y,c[1]*r),(b[0]*r,y,b[1]*r),(a[0]*r,y,a[1]*r)],m)

    def stroke(self,path,width,depth,m,transform=None,glow=False):
        for (x,y),(X,Y) in zip(path,path[1:]):
            self.box((min(x,X)-width/2,min(y,Y)-width/2,-depth/2),
                     (max(x,X)+width/2,max(y,Y)+width/2,depth/2),m,glow,transform)

    def face_frame(self,index,radius,offset=0):
        x,z=SECTION[index];X,Z=SECTION[(index+1)%16]
        length=math.hypot(X-x,Z-z);tx,tz=(X-x)/length,(Z-z)/length
        nx,nz=tz,-tx
        cx,cz=(x+X)*radius/2,(z+Z)*radius/2
        return lambda p:(cx+tx*p[0]+nx*(offset+p[2]),p[1],cz+tz*p[0]+nz*(offset+p[2]))

    def ring_front(self,y,z,outer,inner,depth,m):
        for i in range(8):
            a=(i+.5)*math.pi/4;b=(i+1.5)*math.pi/4
            ca,sa,cb,sb=math.cos(a),math.sin(a),math.cos(b),math.sin(b)
            self.quad([(ca*outer,y+sa*outer,z),(cb*outer,y+sb*outer,z),
                       (cb*inner,y+sb*inner,z),(ca*inner,y+sa*inner,z)],m)
            self.quad([(cb*outer,y+sb*outer,z+depth),(cb*outer,y+sb*outer,z),
                       (ca*outer,y+sa*outer,z),(ca*outer,y+sa*outer,z+depth)],m)
            self.quad([(ca*inner,y+sa*inner,z+depth),(ca*inner,y+sa*inner,z),
                       (cb*inner,y+sb*inner,z),(cb*inner,y+sb*inner,z+depth)],DARK)


def build():
    m=Mesh()
    # A thin carved altar, with open space between three real legs.
    m.box((-2.03,-1.50,-1.84),(2.03,-1.34,1.84),STONE)
    m.box((-2.08,-1.49,-1.89),(2.08,-1.43,1.89),DARK)
    for x,z in [(-1.03,-1.05),(1.03,-1.05),(0,1.31)]:
        turn=math.atan2(x,-z)
        def foot(p,x=x,z=z,turn=turn):
            u,v,w=p;return(x+u*math.cos(turn)-w*math.sin(turn),v,z+u*math.sin(turn)+w*math.cos(turn))
        m.box((-.19,-1.34,-.23),(.19,-.58,.19),DARK,transform=foot)
        m.box((-.29,-.84,-.30),(.29,-.47,.25),TRIM,transform=foot)
        m.box((-.21,-.77,-.34),(.21,-.54,-.29),DARK,transform=foot)
        m.box((-.12,-.80,-.41),(.12,-.65,-.31),TRIM,transform=foot)
        for sign in [-1,1]:
            m.box((sign*.13-.045,-.64,-.353),(sign*.13+.045,-.58,-.335),JADE,transform=foot)
        m.box((-.28,-1.34,-.43),(.28,-1.16,.25),SHELL,transform=foot)
        for i in [-1,0,1]:m.box((i*.17-.055,-1.34,-.45),(i*.17+.055,-1.25,-.30),TRIM,transform=foot)
        m.box((-.22,-1.12,-.25),(.22,-1.02,.21),TRIM,transform=foot)
    m.profile(BODY,SHELL,cap=True)
    m.profile([(-.82,.99),(-.77,1.10),(-.70,1.24)],TRIM)
    m.profile([(1.13,1.38),(1.45,1.38)],DARK)
    for y in [1.10,1.44]:m.profile([(y,1.41),(y+.055,1.41)],TRIM)
    # Raised meanders on the short neck instead of thin painted U-shapes.
    for index in range(16):
        frame=m.face_frame(index,1.38,.015)
        count=3 if index%4==0 else 1
        for j in range(count):
            x=(j-(count-1)/2)*.34
            path=[(x-.125,1.35),(x+.125,1.35),(x+.125,1.20),(x-.055,1.20),(x-.055,1.29),(x+.045,1.29)]
            m.stroke(path,.035,.055,TRIM,frame)
    # Four embossed cloud medallions over broad bevel facets.
    cloud=[(-.23,-.08),(.12,-.08),(.12,.06),(.25,.06),(.25,.24),(.09,.24),(.09,.38),(-.10,.38),(-.10,.22),(-.23,.22)]
    for index in [2,6,10,14]:
        frame=m.face_frame(index,1.79,.025)
        m.stroke(cloud,.062,.065,TRIM,frame)
    # Large recessed octagonal fire bezel, with physical rim depth.
    m.box((-.47,-.28,-1.817),(.47,.66,-1.79),SOOT)
    m.ring_front(.20,-1.95,.72,.48,.15,TRIM)
    m.ring_front(.20,-1.966,.75,.71,.035,DARK)
    trigrams=[7,3,5,1,6,2,4,0]
    for i,bits in enumerate(trigrams):
        a=i*math.pi/4;tx,ty=math.cos(a),math.sin(a);rx,ry=-ty,tx
        for row in range(3):
            r=.535+row*.049
            ranges=[(-.13,.13)] if bits&(1<<row) else [(-.13,-.034),(.034,.13)]
            for lo,hi in ranges:
                # A thin extruded cuboid rotates inside the face plane.
                def bar(p,tx=tx,ty=ty,rx=rx,ry=ry,r=r):
                    u,v,w=p;return(rx*r+tx*u-ty*v,.20+ry*r+ty*u+tx*v,-1.979+w)
                m.box((lo,-.014,-.009),(hi,.014,.009),GOLD,transform=bar)
    m.box((-.13,1.11,-1.445),(.13,1.47,-1.39),COPPER)
    seal=lambda p:(p[0],p[1],-1.455+p[2])
    for path in [[(0,1.15),(0,1.43)],[(-.075,1.35),(.075,1.35)],[(-.07,1.25),(.07,1.25)]]:
        m.stroke(path,.025,.015,TRIM,seal)
    # Square jade medicine window and a low physical pill chute.
    m.box((-1.88,-.10,-.35),(-1.75,.64,.35),TRIM)
    m.box((-1.90,-.045,-.285),(-1.875,.585,.285),DARK)
    m.box((-1.925,.02,-.23),(-1.895,.52,.23),JADE,True)
    m.box((-1.955,.245,-.33),(-1.91,.30,.33),TRIM)
    m.box((1.33,-.79,-.20),(1.68,-.51,.20),TRIM)
    m.box((1.64,-.91,-.14),(1.81,-.54,.14),SHELL)
    m.box((1.44,-1.08,-.34),(2.0,-.98,.34),TRIM)
    m.box((1.49,-.975,-.28),(1.95,-.96,.28),SOOT)
    m.box((1.95,-.99,-.34),(2.01,-.91,.34),TRIM)
    # Rear diamond seal has a stepped bezel, not a large cabinet door.
    for r,z,material in [(.45,1.82,TRIM),(.36,1.84,DARK),(.28,1.86,JADE)]:
        m.quad([(0,.20+r,z),(r,.20,z),(0,.20-r,z),(-r,.20,z)],material,material==JADE)
    # Sparse inlaid spirit routes follow the belly slope. Each route consists of
    # dark seating and thinner jade strokes so it still has depth while idle.
    for side in [-1,1]:
        def route(p,side=side):
            u,y,w=p
            for (y0,r0),(y1,r1) in zip(BODY,BODY[1:]):
                if y0<=y<=y1:
                    r=r0+(r1-r0)*(y-y0)/(y1-y0);break
            else:r=1.38
            return(u,y,side*(r+.035)+w)
        path=[(.35,-.63),(.35,-.40),(.50,-.40),(.50,-.04),(.63,-.04),(.63,.38)]
        m.stroke(path,.065,.025,DARK,route)
        m.stroke(path,.027,.04,JADE,route,True)
    # The two ears sit precisely on the fore/aft centreline, with thickness and
    # a large inward cloud curl, independent of camera position.
    ear=[(1.40,1.02),(2.05,1.02),(2.05,2.26),(1.60,2.26),(1.60,1.88),(1.88,1.88),(1.88,2.08)]
    for sign in [-1,1]:
        mirrored=[(sign*x,y) for x,y in ear]
        m.stroke(mirrored,.24,.42,DARK)
        for z in [-.225,.225]:
            edge=lambda p,z=z:(p[0],p[1],p[2]+z)
            m.stroke(mirrored,.055,.025,TRIM,edge)
        m.box((sign*2.05-.19,.84,-.27),(sign*2.05+.19,1.06,.27),TRIM)
    # Broad lid with a real raised crown, no pagoda eaves.
    m.group='lid'
    m.profile([(1.47,1.66),(1.58,1.66)],TRIM,cap=True)
    m.profile([(1.58,1.56),(1.72,1.39),(1.86,1.12),(2.01,.73),(2.10,.35)],DARK,cap=True)
    for y,r in [(1.59,1.55),(1.73,1.37),(1.87,1.10),(2.02,.70)]:
        m.profile([(y,r),(y+.035,r)],TRIM)
    for index in [0,4,8,12]:
        a=index*math.pi/8;dx,dz=math.cos(a),math.sin(a)
        for y,r in [(1.65,1.47),(1.79,1.25),(1.94,.92)]:
            m.box((dx*r-.04,y-.02,dz*r-.04),(dx*r+.04,y+.09,dz*r+.04),TRIM)
    m.box((-.28,2.08,-.28),(.28,2.17,.28),TRIM)
    m.box((-.21,2.17,-.21),(.21,2.31,.21),JADE)
    m.box((-.115,2.31,-.115),(.115,2.43,.115),JADE)
    bounds=[[min(p[i] for f in m.faces for p in f['p']) for i in range(3)],
            [max(p[i] for f in m.faces for p in f['p']) for i in range(3)]]
    return {'version':1,'texture':'myvillage:textures/block/alchemy_array_formed.png','bounds':bounds,'faces':m.faces}
