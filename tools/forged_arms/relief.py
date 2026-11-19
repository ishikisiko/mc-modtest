from __future__ import annotations

import math

import numpy as np

# The outer dimensions still come from the authored blade sections. These profiles cut
# or raise the interior of the thickness across each front and back surface.
FULLERS = {
    'xuantie_sword': (6.25, 7.4, 17.6, 20.08),
    'xuantie_spear': (31.99, 33.35, 36.0, 38.60),
    'baiqing_sword': (6.63, 8.12, 19.25, 22.94),
    'baiqing_spear': (32.58, 34.63, 39.93, 42.56),
    'jinwu_sword': (8.45, 10.37, 23.65, 28.0),
    'jinwu_spear': (36.99, 39.08, 44.13, 47.0),
}

# Each entry gives a fraction of the distance to the bevel, the depth of the cut,
# and the material of the face between it and the next entry.
PROFILES = {
    'xuantie_sword': [(0,-.39,'socket_dark'),(.37,-.37,'socket_dark'),(.46,-.10,'wall'),
                      (.53,0,'rim'),(.64,0,'face'),(1,0,'edge')],
    'xuantie_spear': [(0,0,'face'),(.27,-.05,'rim'),(.42,-.26,'wall'),
                      (.66,-.24,'shade'),(.88,0,'face'),(1,0,'edge')],
    'baiqing_sword': [(0,-.15,'jade'),(.27,-.15,'jade'),(.37,-.12,'inset'),
                      (.50,0,'rim'),(.59,0,'face'),(1,0,'edge')],
    'baiqing_spear': [(0,-.03,'ivory'),(.26,-.08,'ivory'),(.49,-.31,'inset'),
                      (.75,-.15,'jade'),(.89,0,'face'),(1,0,'edge')],
    'jinwu_sword': [(0,0,'face'),(.24,0,'wall'),(.33,-.35,'socket_dark'),
                    (.61,-.31,'bronze'),(.70,-.08,'rim'),(.77,0,'face'),(1,0,'edge')],
    'jinwu_spear': [(0,0,'face'),(.28,-.02,'bronze'),(.38,-.31,'socket_dark'),
                    (.60,-.32,'socket'),(.72,-.12,'rim'),(.82,0,'face'),(1,0,'edge')],
}

DESCRIPTIONS = {
    'xuantie_sword': '刃面压出窄槽，横格用夹片和铆钉扣住刃根。',
    'xuantie_spear': '叶刃压出中脊，铁套从两侧扣住刃根。',
    'baiqing_sword': '白刃嵌青脊，护手的上下两层在外端留空。',
    'baiqing_spear': '两侧短翼加内托，枪颈用两级卡座接住。',
    'jinwu_sword': '暗脊向两侧下沉，护手的日轮多了一层斜面。',
    'jinwu_spear': '日轮内侧压低一圈，枪头通过加厚榫座接入枪杆。',
}


def blade(model, sections, land):
    raw = np.array([tuple(row) if len(row) == 4 else (*row,0) for row in sections])
    low, enter, leave, high = FULLERS[model.key]
    levels = sorted({*raw[:,0], low, enter, leave, high})
    assert all(raw[0,0] <= y <= raw[-1,0] for y in levels)
    path = PROFILES[model.key]
    # Cross-section order goes from the left edge up the front, then back around
    # the body. At the shoulder and tip it closes to the original profile.
    rows = []
    for y in levels:
        w, d, offset = [np.interp(y,raw[:,0],raw[:,i]) for i in (1,2,3)]
        envelope = np.interp(y, (low,enter,leave,high), (0,1,1,0), left=0,right=0)
        right = [
            (d*(1-.10*t + envelope*cut), y, offset+w*land*t)
            for t,cut,_ in path
        ]
        right.append((.14*d,y,offset+w))
        front = [(x,h,2*offset-z) for x,h,z in reversed(right[1:])] + right
        rows.append(front + [(-x,h,z) for x,h,z in reversed(front)])
    right_materials = [material for _,_,material in path]
    left_materials = ['shade' if s=='face' else s for s in reversed(right_materials)]
    front = left_materials + right_materials
    full_relief = front + ['wall'] + list(reversed(front)) + ['wall']
    plain = ['edge'] + ['shade']*(len(path)-1) + ['face']*(len(path)-1) + ['edge']
    full_plain = plain + ['wall'] + list(reversed(plain)) + ['wall']
    assert len(rows[0]) == len(full_relief) == len(full_plain)
    length = raw[-1,0] - raw[0,0]
    for i,(lo,hi) in enumerate(zip(rows,rows[1:])):
        v0,v1 = [float(1-(r[0][1]-raw[0,0])/length) for r in (lo,hi)]
        materials = full_relief if low <= (levels[i]+levels[i+1])/2 <= high else full_plain
        for j,material in enumerate(materials):
            k=(j+1)%len(lo)
            # The envelope can make a small longitudinal patch non-planar.
            # Bake both triangles explicitly so either camera side culls them correctly.
            model.face('一体刃身与浮雕截面',material,(lo[j],hi[j],hi[k]),[(0,v0),(0,v1),(1,v1)])
            model.face('一体刃身与浮雕截面',material,(lo[j],hi[k],lo[k]),[(0,v0),(1,v1),(1,v0)])
    for rim,facing in ((rows[0],(0,-1,0)),(rows[-1],(0,1,0))):
        # The end sections are convex because the relief closes at each end.
        c = np.mean(rim,axis=0)
        for j in range(len(rim)):
            model.face('刃身封口','wall',(c,rim[j],rim[(j+1)%len(rim)]),facing=facing)


def wings(model, part, polygon, depth, surface, bevel_color, *, split_x=0, bevel=.16):
    for sign in (-1,1):
        outline=[(sign*z,y) for z,y in polygon]
        if split_x:
            for side in (-1,1):
                model.plate(part,outline,depth,surface,bevel_color,x=side*split_x,bevel=bevel)
        else:
            model.plate(part,outline,depth,surface,bevel_color,bevel=bevel)


def paired_plate(model, part, points, depth, face, edge, x, bevel=.16):
    for side in (-1,1):
        model.plate(part,points,depth,face,edge,x=x*side,bevel=bevel)


def domed_stone(model, part, y, z, radii, x, height, materials, *, sides=8):
    # Short faceted cabochon set into an existing seat. Both sides have the same cut.
    start = 0 if sides == 4 else .5
    for sign in (-1,1):
        rim = [
            (sign*x, y+radii[0]*math.sin((j+start)*math.tau/sides),
             z+radii[1]*math.cos((j+start)*math.tau/sides))
            for j in range(sides)
        ]
        inner = [(sign*(x+height*.74), y+.61*(a-y), z+.59*(b-z)) for _,a,b in rim]
        top=(sign*(x+height),y+.04*radii[0],z)
        # Keep isolated pixels out of the stone by giving each bevel a broad face.
        for j in range(sides):
            k=(j+1)%sides
            model.face(part,materials[(j+1)%len(materials)],(rim[j],rim[k],inner[k],inner[j]),
                       facing=(sign,0,0))
            model.face(part,materials[j%len(materials)],(inner[j],inner[k],top),facing=(sign,0,0))
            model.face(part,materials[-1],((sign*x,y,z),rim[j],rim[k]),facing=(-sign,0,0))


def xuantie_sword(model, rivets):
    model.faces = [face for face in model.faces if face['part'] != '铆钉']
    wings(model,'护手夹片',[
        (.43,5.05),(1.08,5.05),(1.42,4.96),(2.19,4.90),(2.47,5.01),
        (2.19,5.18),(1.40,5.18),(1.02,5.22),(.43,5.21)
    ],.12,'socket','rim',split_x=.59)
    wings(model,'刃根夹耳',[
        (.43,5.34),(.91,5.38),(1.14,5.64),(1.06,6.40),(.91,6.65),
        (.66,6.53),(.65,5.85),(.43,5.66)
    ],.145,'socket_dark','rim',split_x=.55)
    rivets(model,5.80,[-.84,.84],.685,'rivet',.13)
    model.shaft('压缠末端',[(1.03,.65),(1.10,.68),(1.20,.70),(1.31,.65)],'binding')
    model.shaft('剑首外沿',[(.18,.71),(.23,.74),(.36,.74),(.42,.71)],'socket',second='rim')


def xuantie_spear(model, rivets):
    model.faces = [face for face in model.faces if face['part'] != '铆钉']
    wings(model,'套筒夹舌',[
        (.36,30.30),(.72,30.59),(.86,31.26),(.87,32.29),
        (.91,33.0),(.74,33.57),(.59,32.99),(.51,32.26),(.36,31.28)
    ],.17,'socket_dark','rim',split_x=.59)
    model.shaft('枪头套筒阶沿',[(29.72,.64),(29.9,.74),(30.34,.76),(30.48,.66)],
                'socket_dark',second='rim')
    model.shaft('接榫上唇',[(31.55,.79),(31.62,.89),(31.98,.89),(32.1,.83)],'socket',second='rim')
    rivets(model,31.23,[-.65,.65],.745,'rivet',.105)
    model.shaft('铁尾箍沿',[(.83,.63),(.93,.65),(1.22,.64),(1.33,.58)],'socket',second='rim')


def baiqing_sword(model, rivets):
    # The narrower lower leaf is a continuous brace attached to the guard base.
    wings(model,'护手内托',[
        (.58,5.08),(1.20,5.04),(1.68,5.15),(2.46,5.57),(2.29,5.63),
        (1.51,5.39),(.91,5.50),(.57,5.56)
    ],.47,'socket_dark','cloud',bevel=.12)
    wings(model,'翼片内侧台阶',[
        (.90,5.80),(1.58,5.98),(2.13,6.18),(2.61,6.67),(2.36,6.65),
        (1.70,6.29),(.90,6.07)
    ],.122,'cloud','rim',split_x=.405,bevel=.13)
    wings(model,'刃口银托',[
        (.39,5.67),(.74,5.80),(.83,6.40),(.73,6.91),(.57,6.78),(.49,6.34),(.39,6.06)
    ],.10,'ivory','inset',split_x=.435)
    domed_stone(model,'青晶四周斜面',5.55,0,(.47,.332),.806,.153,('ivory','jade','inset'),sides=4)
    model.shaft('银尾阶沿',[(.11,.64),(.20,.73),(.32,.77),(.39,.74)],'rim',second='cloud')


def baiqing_spear(model, rivets):
    wings(model,'短翼内侧衬条',[
        (.63,31.78),(1.52,32),(2.18,32.64),(2.59,33.55),
        (2.62,34.57),(2.44,34.95),(2.32,34.14),(2.12,33.4),
        (1.68,32.95),(.64,32.26)
    ],.14,'cloud','inset',split_x=.407,bevel=.12)
    wings(model,'青银枪颈卡簧',[
        (.39,30.58),(.69,30.92),(1.04,31.62),(1.17,32.16),
        (.94,32.30),(.73,31.85),(.42,31.36)
    ],.15,'socket','rim',split_x=.59,bevel=.12)
    model.shaft('下层卡座',[(29.33,.62),(29.59,.74),(29.86,.74),(30.04,.67)],
                'socket',second='rim')
    model.shaft('苍青阶箍',[(30.81,.63),(30.9,.71),(31.21,.75),(31.43,.65)],
                'socket_dark',second='ivory')
    domed_stone(model,'枪颈晶面',31.72,0,(.56,.27),.928,.095,('jade','ivory','inset'),sides=4)


def jinwu_sword(model, rivets):
    wings(model,'折翼内层铜边',[
        (.93,6.84),(1.49,7.10),(2.74,7.51),(3.53,7.98),(3.60,8.27),
        (3.36,8.20),(2.68,7.80),(1.64,7.50),(1.02,7.30)
    ],.12,'rim','bronze',split_x=.86,bevel=.19)
    wings(model,'折翼下缘护条',[
        (.68,6.52),(1.62,6.82),(2.68,7.17),(3.34,7.47),(3.68,7.63),
        (3.59,7.92),(3.29,7.69),(2.57,7.34),(1.35,7.0),(.68,6.80)
    ],.115,'socket_dark','bronze',split_x=.895,bevel=.12)
    wings(model,'剑根抱片',[
        (.69,7.47),(1.17,7.65),(1.47,8.53),(1.77,9.28),(1.6,9.58),
        (1.38,9.35),(1.13,9.02),(.84,8.21)
    ],.16,'bronze','wall',split_x=.54,bevel=.13)
    domed_stone(model,'日轮中央斜切面',7.17,0,(.51,.51),1.205,.147,('sun','sun_light','sun_dark'))
    for sign in (-1,1):
        model.ring('日轮内沿',7.17,.664,.549,.047,'socket_dark','rim',
                   x=sign*1.125,n=8,roll=math.pi/8)
    model.shaft('双阶护箍',[(5.84,.71),(5.96,.81),(6.19,.83),(6.36,.70)],
                'socket_dark',second='rim')


def jinwu_spear(model, rivets):
    for sign in (-1,1):
        model.ring('日轮压低一层的内缘',36.76,3.04,2.59,.13,'bronze','rim',
                   x=sign*.50,n=8,roll=math.pi/8)
    wings(model,'日轮根部嵌套件',[
        (.71,34.06),(1.23,34.45),(1.88,34.96),(2.49,35.5),(2.47,35.88),
        (1.84,35.52),(1.13,35.06),(.69,34.68)
    ],.13,'socket_dark','rim',split_x=.55)
    wings(model,'枪头两侧榫板',[
        (.42,35.61),(.67,35.75),(.89,36.28),(.88,37.32),(.73,37.63),
        (.57,37.52),(.48,36.79),(.42,36.19)
    ],.11,'bronze','socket_dark',split_x=.61,bevel=.14)
    model.shaft('岔支根部三阶榫座',[(31.22,.70),(31.54,.79),(31.69,.91),(32.03,.91),
                              (32.18,.81),(32.41,.79),(32.55,.728)],'socket',second='rim')
    domed_stone(model,'枪颈日芯斜面',36.10,0,(.402,.36),.88,.135,('sun','sun_light','sun_dark'))
    model.shaft('枪尾叠圈',[(.71,.72),(.85,.78),(1.05,.75),(1.24,.681)],'socket_dark',second='rim')


GEOMETRY = {
    'xuantie_sword':xuantie_sword, 'xuantie_spear':xuantie_spear,
    'baiqing_sword':baiqing_sword, 'baiqing_spear':baiqing_spear,
    'jinwu_sword':jinwu_sword, 'jinwu_spear':jinwu_spear,
}


def add_join_details(model, rivets):
    GEOMETRY[model.key](model, rivets)
    model.description = DESCRIPTIONS[model.key]
    return model
