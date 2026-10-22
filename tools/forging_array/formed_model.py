"""Authored Xuan-shu forging ding, in blocks; +Z is back, -Z is front.

The runtime translates this origin to controller.centerXZ + back*2 and
controllerY-1. Only ``ring`` rotates about Y; ``fire`` is shown while running.
Collision is deliberately coarse and contains only the static solid structure.
"""
from __future__ import annotations

import math

IRON, EDGE, DARK, BRONZE, PATINA, JADE, FIRE, SOOT, STONE, STONE_EDGE, COPPER, ASH, RIVET, RUNE, EMBER, GOLD = range(16)


class Mesh:
    def __init__(self):
        self.faces = []
        self.collision_boxes = []
        self.group = "body"

    def quad(self, points, material, glow=False):
        a, b, c = points[:3]
        u = [b[i] - a[i] for i in range(3)]
        v = [c[i] - a[i] for i in range(3)]
        n = [u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0]]
        length = math.sqrt(sum(x*x for x in n))
        if length < 1e-8:
            raise ValueError("Degenerate forging-array face")
        self.faces.append({"p": [[round(x, 5) for x in p] for p in points],
                           "n": [round(x/length, 5) for x in n], "m": material,
                           "g": self.group, "glow": glow})

    def box(self, lo, hi, material, glow=False, collision=False, transform=None):
        x, y, z = lo
        X, Y, Z = hi
        if not (x < X and y < Y and z < Z):
            raise ValueError("Inverted/empty forging-array box")
        if collision:
            if transform is not None or self.group != "body":
                raise ValueError("Collision boxes must be static and axis aligned")
            self.collision_boxes.append([list(lo), list(hi)])
        faces = [[(x,y,z),(x,Y,z),(X,Y,z),(X,y,z)],
                 [(X,y,Z),(X,Y,Z),(x,Y,Z),(x,y,Z)],
                 [(x,y,Z),(x,Y,Z),(x,Y,z),(x,y,z)],
                 [(X,y,z),(X,Y,z),(X,Y,Z),(X,y,Z)],
                 [(x,y,z),(X,y,z),(X,y,Z),(x,y,Z)],
                 [(x,Y,Z),(X,Y,Z),(X,Y,z),(x,Y,z)]]
        for face in faces:
            self.quad([transform(p) for p in face] if transform else face, material, glow)

    def rim(self, outer, inner, bottom, top, material, collision=False):
        """Open square band, without a lid or a centre-spanning collision."""
        self.box((-outer,bottom,-outer),(outer,top,-inner),material,collision=collision)
        self.box((-outer,bottom,inner),(outer,top,outer),material,collision=collision)
        self.box((-outer,bottom,-inner),(-inner,top,inner),material,collision=collision)
        self.box((inner,bottom,-inner),(outer,top,inner),material,collision=collision)

    def line(self, a, b, width, depth, material, glow=False):
        """Thin straight line on a horizontal plane."""
        x, y, z = a
        X, Y, Z = b
        self.box((min(x,X)-width/2,min(y,Y),min(z,Z)-width/2),
                 (max(x,X)+width/2,max(y,Y)+depth,max(z,Z)+width/2),material,glow)


def build():
    m = Mesh()
    # Five-by-five thin stone altar: one collision slab, readable tile joints.
    m.box((-2.5,0,-2.5),(2.5,.18,2.5),DARK,collision=True)
    for x in range(-2,3):
        for z in range(-2,3):
            m.box((x-.486,.18,z-.486),(x+.486,.25,z+.486),STONE)
    m.collision_boxes.append([[-2.5,.18,-2.5],[2.5,.25,2.5]])
    # Quiet bronze seats and one fine spirit route around the altar, not a grid.
    for z in (-2,2):
        m.line((-2,.253,z),(2,.253,z),.065,.012,DARK)
        m.line((-2,.267,z),(2,.267,z),.023,.008,JADE,True)
    for x in (-2,2):
        m.line((x,.253,-2),(x,.253,2),.065,.012,DARK)
        m.line((x,.267,-2),(x,.267,2),.023,.008,JADE,True)

    # Four beast feet: planted toes, square muzzle, heavy brow and small eyes.
    for x in (-1.12,1.12):
        for z in (-1.12,1.12):
            s = -1 if z < 0 else 1
            m.box((x-.28,.25,z-.29),(x+.28,.43,z+.29),BRONZE,collision=True)
            m.box((x-.17,.43,z-.17),(x+.17,1.25,z+.17),IRON,collision=True)
            m.box((x-.23,.58,z-.22),(x+.23,.70,z+.22),BRONZE)
            m.box((x-.29,.89,z-.27),(x+.29,1.20,z+.27),BRONZE,collision=True)
            # Outward-facing muzzle; sorted endpoints keep mirrored geometry valid.
            a,b = sorted((z+s*.28,z+s*.38))
            m.box((x-.17,.88,a),(x+.17,1.04,b),EDGE)
            a,b = sorted((z+s*.273,z+s*.295))
            m.box((x-.23,1.09,a),(x+.23,1.16,b),DARK)
            for eye in (-.15,.15):
                a,b = sorted((z+s*.297,z+s*.311))
                m.box((x+eye-.035,1.105,a),(x+eye+.035,1.135,b),PATINA)
            for toe in (-.16,0,.16):
                a,b = sorted((z+s*.23,z+s*.32))
                m.box((x+toe-.045,.25,a),(x+toe+.045,.35,b),EDGE)

    # Low square belly and separate broad walls. Front wall has a true fire door.
    m.box((-1.34,1.12,-1.34),(1.34,1.42,1.34),IRON,collision=True)
    m.rim(1.43,1.14,1.30,1.47,BRONZE)
    m.box((-1.48,1.42,-1.18),(-1.18,2.79,1.18),IRON,collision=True)
    m.box((1.18,1.42,-1.18),(1.48,2.79,1.18),IRON,collision=True)
    m.box((-1.48,1.42,1.18),(1.48,2.79,1.48),IRON,collision=True)
    for a,b in ((-1.48,-.56),(.56,1.48)):
        m.box((a,1.42,-1.48),(b,2.79,-1.18),IRON,collision=True)
    m.box((-.56,2.20,-1.48),(.56,2.79,-1.18),IRON,collision=True)
    m.rim(1.50,1.16,2.73,2.88,EDGE,collision=True)
    m.rim(1.50,1.15,2.88,2.99,BRONZE,collision=True)
    # Dark inner liner and coal bed remain visible when the ding is cold.
    m.box((-1.13,1.425,-1.13),(1.13,1.45,1.13),SOOT)
    for x,z in ((-.65,-.55),(.3,-.55),(-.45,.35),(.65,.55)):
        m.box((x-.23,1.45,z-.19),(x+.23,1.51,z+.19),ASH)
    # Spare corner bindings and broad recessed panels provide large-scale rhythm.
    for x in (-1.38,1.38):
        for z in (-1.38,1.38):
            m.box((x-.115,1.45,z-.115),(x+.115,2.74,z+.115),BRONZE)
            m.box((x-.16,2.70,z-.16),(x+.16,3.04,z+.16),BRONZE)
            m.box((x-.09,2.76,z-.168),(x+.09,2.97,z-.161),DARK)
    for side in (-1,1):
        a,b = sorted((side*1.482,side*1.51))
        m.box((a,1.73,-.85),(b,2.48,.85),DARK)
        a,b = sorted((side*1.511,side*1.545))
        for z in (-.87,.82):
            m.box((a,1.71,z),(b,2.51,z+.05),BRONZE)
        for y in (1.71,2.46):
            m.box((a,y,-.82),(b,y+.05,.82),BRONZE)
        # A single ancient seal per side; no repeated neon panel treatment.
        a,b = sorted((side*1.548,side*1.565))
        m.box((a,1.95,-.10),(b,2.27,-.072),JADE,True)
        m.box((a,2.08,-.10),(b,2.11,.12),JADE,True)
        m.box((a,2.08,.092),(b,2.30,.12),JADE,True)
    m.box((-.70,1.79,1.482),(.70,2.42,1.51),DARK)
    m.box((-.74,2.44,1.485),(.74,2.49,1.54),BRONZE)
    m.box((-.74,1.72,1.485),(.74,1.77,1.54),BRONZE)
    m.box((-.035,1.91,1.515),(.035,2.29,1.545),BRONZE)

    # Framed front furnace opening and projecting controller foot platform.
    for x in (-.63,.63):
        m.box((x-.09,1.33,-1.60),(x+.09,2.23,-1.46),BRONZE)
    m.box((-.72,2.20,-1.60),(.72,2.33,-1.46),BRONZE)
    m.box((-.56,1.44,-1.19),(.56,1.48,-1.04),SOOT)
    m.box((-.74,1.10,-2.15),(.74,1.26,-1.40),EDGE)
    # Coarse solid core stays in the controller cell; side lips are decorative.
    m.collision_boxes.append([[-.5,1.10,-2.15],[.5,1.26,-1.40]])
    m.box((-.70,1.26,-2.11),(.70,1.31,-1.43),BRONZE)
    m.box((-.52,.25,-2.09),(.52,.42,-1.76),STONE_EDGE,collision=True)
    m.box((-.39,.42,-2.03),(.39,.76,-1.77),IRON,collision=True)
    m.box((-.28,.76,-1.98),(.28,1.10,-1.78),BRONZE,collision=True)
    for x in (-.86,.86):
        m.box((x-.07,1.72,-1.515),(x+.07,2.11,-1.485),DARK)
        m.box((x-.012,1.80,-1.53),(x+.012,2.05,-1.517),JADE,True)

    # Bronze square ears, open windows, understated inward cloud curls.
    for side in (-1,1):
        def ear_box(x0,y0,x1,y1,material,collision=False,z0=-.20,z1=.20):
            a,b = sorted((side*x0,side*x1))
            m.box((a,y0,z0),(b,y1,z1),material,collision=collision)
        ear_box(1.31,2.84,1.81,3.02,BRONZE)
        # The flared ear collar is trim; upper post and inset seat carry collision.
        a,b = sorted((side*1.31,side*1.50))
        m.collision_boxes.append([[a,2.84,-.20],[b,3.02,.20]])
        ear_box(1.66,3.02,1.81,3.93,IRON,True)
        ear_box(1.30,3.78,1.81,4.08,IRON,True)
        ear_box(1.30,3.46,1.44,3.78,BRONZE,True)
        ear_box(1.30,3.42,1.61,3.56,BRONZE,True)
        for z0,z1 in ((-.232,-.202),(.202,.232)):
            ear_box(1.66,3.03,1.71,3.99,BRONZE,z0=z0,z1=z1)
            ear_box(1.31,3.98,1.81,4.04,BRONZE,z0=z0,z1=z1)
            ear_box(1.31,3.74,1.59,3.79,BRONZE,z0=z0,z1=z1)
        ear_box(1.29,3.76,1.51,3.98,BRONZE,z0=-.25,z1=-.233)
        ear_box(1.35,3.82,1.45,3.92,DARK,z0=-.258,z1=-.251)

    # Four compact spirit pylons. The small jade core is seated, not floating.
    for x in (-2,2):
        for z in (-2,2):
            m.box((x-.34,.25,z-.34),(x+.34,.40,z+.34),BRONZE,collision=True)
            m.box((x-.23,.40,z-.23),(x+.23,1.26,z+.23),IRON,collision=True)
            m.box((x-.28,1.19,z-.28),(x+.28,1.34,z+.28),BRONZE,collision=True)
            m.box((x-.13,1.34,z-.13),(x+.13,1.62,z+.13),JADE,True,collision=True)
            for dx in (-.21,.21):
                for dz in (-.21,.21):
                    m.box((x+dx-.045,1.34,z+dz-.045),(x+dx+.045,1.80,z+dz+.045),BRONZE,collision=True)
            for face in (-1,1):
                a,b = sorted((z+face*.232,z+face*.249))
                m.box((x-.015,.53,a),(x+.015,1.10,b),JADE,True)

    # One open, twelve-sided spirit ring rotates as a whole. No collision.
    m.group = "ring"
    for i in range(12):
        angle = (i+.5)*math.tau/12
        half = 1.20*math.tan(math.pi/12)
        def segment(p, a=angle):
            u,y,v = p
            return (math.cos(a)*(1.20+v)-math.sin(a)*u,y,
                    math.sin(a)*(1.20+v)+math.cos(a)*u)
        m.box((-half,3.275,-.035),(half,3.345,.035),BRONZE,transform=segment)
        if i%3 == 0:
            m.box((-.065,3.255,-.055),(.065,3.365,.055),DARK,transform=segment)
            m.box((-.022,3.28,-.058),(.022,3.34,-.056),JADE,True,transform=segment)

    # Runtime-only fire lies inside the furnace, never through the static walls.
    # Actual displayed equipment is the parent renderer's ItemStack, not this mesh.
    m.group = "fire"
    m.box((-1.10,1.452,-1.10),(1.10,1.48,1.10),FIRE,True)
    for x,z,h in ((-.70,-.60,1.18),(.10,-.72,1.34),(.67,-.25,1.22),(-.38,.20,1.55),(.40,.57,1.40)):
        # Low-poly tapered, leaning tongues: no luminous rectangular rods.
        sections = [(1.48,.20,0),(2.10,.22,.035),(2.48,.115,-.025),(1.48+h,.012,.10)]
        perimeter = [(-1,-1),(1,-1),(1,1),(-1,1)]
        def points(section):
            y,r,lean = section
            return [(x+dx*r+lean,y,z+dz*r) for dx,dz in perimeter]
        for low,high in zip(sections,sections[1:]):
            a,b = points(low),points(high)
            for i in range(4):
                j = (i+1)%4
                m.quad([a[i],b[i],b[j],a[j]],FIRE,True)
        m.quad(list(reversed(points(sections[-1]))),EMBER,True)
    # Front orange heart is recessed behind the open bronze bezel.
    m.box((-.48,1.48,-1.165),(.48,2.13,-1.14),FIRE,True)
    m.box((-.24,1.49,-1.18),(.24,1.88,-1.166),EMBER,True)

    bounds = [[min(p[i] for f in m.faces for p in f["p"]) for i in range(3)],
              [max(p[i] for f in m.faces for p in f["p"]) for i in range(3)]]
    return {"version": 1, "texture": "myvillage:textures/block/forging_array_formed.png",
            "bounds": bounds, "collision_boxes": m.collision_boxes, "faces": m.faces}
