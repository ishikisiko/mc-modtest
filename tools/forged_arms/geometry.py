from __future__ import annotations

import math

import numpy as np
from PIL import Image, ImageColor, ImageDraw

from .relief import blade, add_join_details

TEXTURES = 'item/forged_arms'
PALETTES = {
    'xuantie': {
        'face': ('#667d91', 'blade'), 'shade': ('#465a70', 'blade'),
        'edge': ('#aebfca', 'edge'), 'wall': ('#3b4c60', 'edge'),
        'socket': ('#455667', 'plates'), 'socket_dark': ('#293745', 'plates'),
        'rim': ('#8c9cac', 'edge'), 'rivet': ('#a68466', 'stone'),
        'wood': ('#503b34', 'wood'), 'wrap': ('#463b38', 'wrap'),
        'binding': ('#7c5c44', 'wrap'),
    },
    'baiqing': {
        'face': ('#c8e4e4', 'blade'), 'shade': ('#81b6c0', 'blade'),
        'edge': ('#edf7ec', 'edge'), 'wall': ('#6895a4', 'edge'),
        'socket': ('#4d8394', 'plates'), 'socket_dark': ('#2b4b5d', 'plates'),
        'rim': ('#bddee0', 'edge'), 'rivet': ('#a3c8c4', 'stone'),
        'wood': ('#2b464b', 'wood'), 'wrap': ('#315663', 'wrap'),
        'binding': ('#84b3bf', 'wrap'), 'ivory': ('#e2f0e9', 'stone'),
        'cloud': ('#a4cdd3', 'plates'), 'jade': ('#8ac3cc', 'stone'),
        'inset': ('#567f8b', 'stone'),
    },
    'jinwu': {
        'face': ('#6a554b', 'blade'), 'shade': ('#44393c', 'blade'),
        'edge': ('#d4aa57', 'edge'), 'wall': ('#8b6441', 'edge'),
        'socket': ('#78533e', 'plates'), 'socket_dark': ('#362c2e', 'plates'),
        'rim': ('#d4a34a', 'edge'), 'rivet': ('#e5bc72', 'stone'),
        'wood': ('#362926', 'wood'), 'wrap': ('#4e3333', 'wrap'),
        'binding': ('#8d613b', 'wrap'), 'bronze': ('#9e703c', 'plates'),
        'sun': ('#e99543', 'stone'), 'sun_light': ('#f2bf66', 'stone'),
        'sun_dark': ('#b36335', 'stone'), 'ember': ('#aa6141', 'stone'),
    },
}


def tint(color, factor):
    return tuple(min(255, round(component * factor)) for component in color)


def make_texture(hex_color, style):
    width, height = 16, 64
    base = ImageColor.getrgb(hex_color)
    out = Image.new('RGB', (width, height), base)
    draw = ImageDraw.Draw(out)
    if style in ('blade', 'edge', 'plates'):
        # Long forged planes with a few broad, quiet changes in tone.
        draw.rectangle((0, 0, 2, 63), fill=tint(base, .89))
        draw.rectangle((3, 0, 4, 63), fill=tint(base, 1.05))
        draw.rectangle((5, 3, 15, 16), fill=tint(base, 1.04))
        draw.rectangle((8, 25, 15, 39), fill=tint(base, .96))
        draw.rectangle((5, 53, 12, 59), fill=tint(base, 1.05))
        if style == 'blade':
            draw.rectangle((11, 3, 14, 13), fill=tint(base, 1.09))
        if style == 'plates':
            draw.rectangle((0, 0, 15, 4), fill=tint(base, 1.12))
            draw.rectangle((0, 58, 15, 63), fill=tint(base, .78))
    elif style == 'wood':
        for x, y, right, bottom, f in (
            (1, 0, 2, 26, 1.17), (1, 36, 2, 54, 1.10), (6, 11, 6, 55, .80),
            (11, 24, 12, 40, 1.15), (14, 2, 15, 62, .87), (9, 44, 9, 61, 1.15),
        ):
            draw.rectangle((x, y, right, bottom), fill=tint(base, f))
    elif style == 'wrap':
        for x in range(width):
            step = x // 4
            for y in range(height):
                band = (y + step * 2) % 11
                if band == 0 or band == 10:
                    draw.point((x, y), fill=tint(base, .68))
                if band in (1, 2):
                    draw.point((x, y), fill=tint(base, 1.17))
                if band in (5, 6) and x % 4 == 1:
                    draw.point((x, y), fill=tint(base, .92))
    elif style == 'stone':
        draw.polygon([(0,0), (11,0), (14,12), (7,28), (0,34)], fill=tint(base, 1.10))
        draw.polygon([(0,40), (9,37), (15,49), (15,63), (0,63)], fill=tint(base, .88))
        draw.polygon([(6,10), (11,8), (14,11), (8,24), (5,24)], fill=tint(base, 1.08))
    return out


def cross2(a, b, c):
    return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])


def polygon_triangles(outline):
    area = sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(outline, outline[1:] + outline[:1]))
    loop = list(range(len(outline))) if area > 0 else list(reversed(range(len(outline))))
    triangles = []
    while len(loop) > 3:
        for k, b in enumerate(loop):
            a, c = loop[k - 1], loop[(k + 1) % len(loop)]
            va, vb, vc = outline[a], outline[b], outline[c]
            if cross2(va, vb, vc) <= 1e-8:
                continue
            if any(
                min(cross2(va, vb, outline[p]), cross2(vb, vc, outline[p]),
                    cross2(vc, va, outline[p])) >= -1e-8
                for p in loop if p not in (a, b, c)
            ):
                continue
            triangles.append((a, b, c))
            loop.pop(k)
            break
        else:
            raise ValueError(f'自交或退化的平面轮廓 {outline}')
    if cross2(*(outline[i] for i in loop)) <= 1e-9:
        raise ValueError(f'退化末端三角面 {outline}')
    return [*triangles, tuple(loop)]


class Model:
    def __init__(self, key, family, name, description, grip_y, closeup):
        self.key = key
        self.family = family
        self.name = name
        self.description = description
        self.grip_y = grip_y
        self.closeup = closeup
        self.faces = []

    def face(self, part, material, points, uv=None, facing=None):
        if material not in PALETTES[self.family]:
            raise ValueError(material)
        if uv is None:
            uv = [(0, 0), (0, 1), (1, 1), (1, 0)][:len(points)]
        points = list(map(tuple, points))
        uv = list(uv)
        normal = np.cross(np.subtract(points[1], points[0]), np.subtract(points[2], points[0]))
        if np.dot(normal, normal) < 1e-12:
            raise ValueError(f'{self.key} {part}: 零面积面 {points}')
        if facing is not None and np.dot(normal, facing) < 0:
            points.reverse()
            uv.reverse()
        if len(points) == 3:
            points.append(points[-1])
            uv.append(uv[-1])
        assert len(points) == len(uv) == 4
        self.faces.append({
            'part': part, 'texture': f'yuanxian:{TEXTURES}/{self.family}/{material}',
            'points': points, 'uv': uv,
        })

    def loft(self, part, rows, materials, longitudinal=True, caps='wall', segmented=False):
        n = len(rows[0])
        assert all(len(row) == n for row in rows)
        start, height = rows[0][0][1], rows[-1][0][1] - rows[0][0][1]
        for bottom, top in zip(rows, rows[1:]):
            vlo, vhi = (1 - (row[0][1] - start) / height for row in (bottom, top))
            for j in range(n):
                k = (j + 1) % n
                u0, u1 = (j / n, (j + 1) / n) if longitudinal else (0, 1)
                vv = (1, 0) if segmented else (vlo, vhi)
                self.face(part, materials[j % len(materials)],
                          (bottom[j], top[j], top[k], bottom[k]),
                          ((u0, vv[0]), (u0, vv[1]), (u1, vv[1]), (u1, vv[0])))
        for row, direction in ((rows[0], (0, -1, 0)), (rows[-1], (0, 1, 0))):
            center = np.mean(row, axis=0).tolist()
            for j in range(n):
                self.face(part, caps, (center, row[j], row[(j + 1) % n]), facing=direction)

    def shaft(self, part, sections, material, *, sides=8, second=None, cap=None):
        rows = []
        for section in sections:
            y, width = section[:2]
            depth = section[2] if len(section) == 3 else width
            norm = math.cos(math.pi / sides)
            rows.append([
                (depth * math.cos((2 * j + 1) * math.pi / sides) / norm,
                 y, width * math.sin((2 * j + 1) * math.pi / sides) / norm)
                for j in range(sides)
            ])
        materials = [material] if second is None else [material, material, second, material, material, material, second, material]
        self.loft(part, rows, materials, caps=cap or material)

    def blade(self, sections, *, land):
        blade(self, sections, land)

    def plate(self, part, outline, depth, front, bevelmat, *, x=0, bevel=.14):
        outline = list(outline)
        area = sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(outline, outline[1:] + outline[:1]))
        if area < 0:
            outline.reverse()
        triangles = polygon_triangles(outline)
        zc, yc = np.mean(outline, axis=0)
        zmin, ymin = np.min(outline, axis=0)
        zw, yh = np.ptp(outline, axis=0)
        def ring(level, shrink):
            return [(x+level, yc+(y-yc)*shrink, zc+(z-zc)*shrink) for z, y in outline]
        rows = [ring(-depth, 1-bevel), ring(-depth * .65, 1),
                ring(depth * .65, 1), ring(depth, 1-bevel)]
        for k, (a,b) in enumerate(zip(rows, rows[1:])):
            for i in range(len(a)):
                j = (i+1) % len(a)
                self.face(part, front if k == 1 else bevelmat, (a[i], b[i], b[j], a[j]))
        for row, side in ((rows[0], -1), (rows[-1], 1)):
            for indices in triangles:
                points = [row[i] for i in indices]
                uv = [((z-zmin)/zw, 1-(y-ymin)/yh) for _,y,z in points]
                self.face(part, front, points, uv, facing=(side,0,0))

    def ring(self, part, y, outer, inner, depth, face, brim, *, n=8, x=0, roll=0):
        angle = [math.pi * 2*j/n + roll for j in range(n)]
        def point(r, level, j):
            return (x+level, y+r*math.sin(angle[j]), r*math.cos(angle[j]))
        for side in (-1,1):
            for j in range(n):
                k = (j+1)%n
                self.face(part, face, (
                    point(outer-.12, depth*side, j), point(outer-.12, depth*side,k),
                    point(inner+.08, depth*side, k), point(inner+.08, depth*side,j)),
                    facing=(side,0,0))
                for radius, lip, f in ((outer, outer-.12,1),(inner, inner+.08,-1)):
                    ridge = (0, f*math.sin((angle[j]+(2*math.pi/n)/2)), f*math.cos(angle[j]+(2*math.pi/n)/2))
                    self.face(part, brim, (point(radius, depth*.53*side,j),point(lip,depth*side,j),
                                             point(lip,depth*side,k),point(radius,depth*.53*side,k)), facing=ridge)
        for j in range(n):
            k = (j+1)%n
            for radius,f in ((outer,1),(inner,-1)):
                ridge = (0, f*math.sin(angle[j]+(math.pi/n)), f*math.cos(angle[j]+(math.pi/n)))
                self.face(part, brim, (point(radius,-depth*.53,j),point(radius,depth*.53,j),
                                        point(radius,depth*.53,k),point(radius,-depth*.53,k)), facing=ridge)


def mirrored(outline, sign):
    return [(z*sign,y) for z,y in outline]


def rivet_pair(model, y, offsets, depth, material, size=.13):
    for z in offsets:
        poly = [(z-size,y-size*.6),(z-size*.65,y-size),(z+size*.65,y-size),
                (z+size,y-size*.58),(z+size,y+size*.58),(z+size*.65,y+size),
                (z-size*.65,y+size),(z-size,y+size*.6)]
        for x in (-depth,depth):
            model.plate('铆钉',poly,.055,material,'wall',x=x,bevel=.22)


def xuantie_sword():
    m=Model('xuantie_sword','xuantie','玄铁剑','厚脊直刃，剑尖斜收，配短横格。',2.95,(3.35,9.0))
    m.shaft('尾帽',[(0,.56),(.16,.72),(.55,.72),(.71,.61)],'socket',second='rim')
    m.shaft('厚木剑柄',[(.5,.58),(1.3,.63),(3.85,.65),(4.76,.59)],'wood')
    m.shaft('炭褐缠柄',[(.91,.64),(1.3,.68),(2.87,.70),(4.28,.66),(4.49,.61)],'wrap')
    m.shaft('柄箍',[(.71,.65),(.8,.68),(.95,.68),(1.02,.64)],'binding')
    m.shaft('口箍',[(4.49,.64),(4.63,.69),(4.91,.7)],'socket_dark',second='rim')
    guard=[(-2.7,4.99),(-2.46,4.75),(-.70,4.83),(.72,4.83),(2.46,4.75),(2.7,4.99),
           (2.47,5.36),(.78,5.35),(.48,5.62),(-.48,5.62),(-.78,5.35),(-2.47,5.36)]
    m.plate('短横格',guard,.67,'socket','rim')
    for sign in (-1,1):
        m.plate('端头包铁',mirrored([(2.06,4.79),(2.47,4.74),(2.73,4.96),(2.50,5.36),(2.09,5.35)],sign),
                .70,'socket_dark','rim',bevel=.18)
    m.blade([(5.37,1.0,.51),(5.75,1.44,.56),(6.35,1.49,.57),(13.7,1.42,.55),
             (17.45,1.31,.49),(19.63,1.25,.44),(21.93,.4,.19,.58),(22.76,.038,.034,.68)],land=.60)
    m.plate('挡肩包口',[(-.99,5.32),(-.86,6.14),(-.55,6.31),(.55,6.31),(.86,6.14),(.99,5.32)],
            .64,'socket_dark','socket',bevel=.15)
    rivet_pair(m,5.59,[-.54,.54],.64,'rivet',.10)
    return m


def baiqing_sword():
    m=Model('baiqing_sword','baiqing','白青灵金剑','银白窄刃，挑起的双片护手留出空隙。',3.25,(3.60,10.0))
    m.shaft('剑首',[(0,.47),(.19,.76,.67),(.57,.78,.68),(.82,.56,.50)],'ivory',second='socket')
    m.shaft('青玉尾扣',[(.28,.59),(.54,.61),(.89,.60),(.96,.50)],'socket_dark')
    m.shaft('青灰缠柄',[(.72,.52),(1.12,.59),(4.44,.59),(5.07,.56)],'wrap')
    for y in (1.11,4.42):
        m.shaft('缠柄压线',[(y-.12,.58),(y-.04,.61),(y+.08,.61),(y+.16,.58)],'binding')
    m.plate('护手中心',[(-.9,5.10),(-.59,4.94),(.59,4.94),(.9,5.10),(.95,5.66),
                     (.58,6.16),(-.58,6.16),(-.95,5.66)],.63,'socket','rim')
    for s in (-1,1):
        m.plate('上挑护手',mirrored([
                (.67,5.58),(1.58,5.72),(2.43,6.30),(3.13,7.19),(2.76,7.16),
                (2.04,6.81),(1.27,6.28),(.65,6.18)],s),.43,'ivory','cloud',bevel=.09)
        m.plate('下侧支撑',mirrored([
                (.68,5.16),(1.45,5.09),(2.20,5.33),(2.60,5.70),(2.14,5.69),
                (1.48,5.56),(.67,5.68)],s),.37,'cloud','socket',bevel=.12)
    m.blade([(5.9,.80,.47),(6.83,.88,.48),(7.32,1.32,.50),(8.30,1.27,.46),
             (15.1,1.16,.43),(20.1,.98,.36),(22.78,.66,.26),(24.86,.036,.03)],land=.44)
    for x in (-.69,.69):
        m.plate('护手青晶',[(0,5.06),(.46,5.55),(0,6.11),(-.46,5.55)],.14,'jade','inset',x=x,bevel=.16)
    return m


def jinwu_sword():
    m=Model('jinwu_sword','jinwu','日魄乌金剑','宽刃在根部折出肩线，护手中央嵌有日轮。',3.55,(4.78,12.2))
    m.shaft('剑首铜盖',[(0,.49),(.16,.86),(.66,.89),(.96,.62)],'bronze',second='rim')
    m.shaft('剑首芯',[(.28,.89,.72),(.54,.91,.75),(.71,.80,.70)],'socket_dark')
    m.shaft('厚缠柄',[(.79,.57),(1.16,.70),(3.65,.74),(5.26,.65),(5.87,.63)],'wrap')
    m.shaft('上端护箍',[(5.48,.68),(5.64,.76),(5.99,.76),(6.53,.67)],'bronze',second='socket')
    outline=[(-4.11,8.26),(-3.55,8.88),(-2.15,7.98),(-.90,7.65),(.90,7.65),(2.15,7.98),
             (3.55,8.88),(4.11,8.26),(3.82,7.65),(2.94,7.17),(1.55,6.76),(.89,6.22),
             (-.89,6.22),(-1.55,6.76),(-2.94,7.17),(-3.82,7.65)]
    m.plate('整条折翼护手',outline,.83,'socket_dark','bronze',bevel=.085)
    for sign in (-1,1):
        m.plate('折翼金边',mirrored([
            (.80,6.85),(1.61,7.16),(2.86,7.61),(3.65,8.02),(3.76,8.47),
            (3.48,8.67),(2.05,7.85),(.80,7.43)],sign),.91,'bronze','rim',bevel=.09)
        m.plate('末端包角',mirrored([(3.56,8.87),(4.13,8.27),(3.87,7.79),(3.41,8.17)],sign),
                .90,'rim','socket',bevel=.14)
    m.blade([(7.45,1.30,.53),(8.6,1.44,.69),(9.19,2.12,.72),(10.35,2.10,.75),
             (11.03,1.81,.70),(20.5,1.84,.65),(25.75,1.56,.55),(28.34,1.0,.45),
             (30.12,.15,.125),(30.56,.035,.035)],land=.70)
    m.plate('日轮底座',[(-.90,7.67),(-.99,6.94),(-.58,6.39),(.58,6.39),
                      (.99,6.94),(.90,7.67),(.58,8.02),(-.58,8.02)],.99,'socket_dark','bronze')
    for side in (-1,1):
        m.ring('日轮嵌边',7.17,.83,.57,.12,'rim','bronze',n=8,x=side*1.01,roll=math.pi/8)
        sun=[(.57*math.cos((j+.5)*math.tau/8),7.17+.57*math.sin((j+.5)*math.tau/8)) for j in range(8)]
        m.plate('日轮芯',sun,.13,'sun_light','sun_dark',x=side*1.10,bevel=.22)
        m.plate('退火线',[(0,8.0),(-.19,9.32),(0,11.82),(.19,9.32)],.105,'ember','sun_dark',x=side*.70,bevel=.10)
    return m


def xuantie_spear():
    m=Model('xuantie_spear','xuantie','玄铁枪','叶刃较宽，配深木枪杆和短缠带。',19.25,(28.1,39.7))
    m.shaft('束尾',[(0,.37),(.24,.61),(.56,.66),(1.50,.58),(1.71,.47)],'socket_dark',second='rim')
    m.shaft('烟褐硬木枪杆',[(1.30,.49),(4.1,.53),(15.55,.60),(22.9,.59),(28.95,.57),(32.1,.57)],'wood')
    m.shaft('缠握',[(14.7,.61),(14.96,.66),(15.29,.68),(22.38,.67),(22.8,.60)],'wrap')
    for y in (15.04,22.35):
        m.shaft('握带收口',[(y-.12,.65),(y-.04,.69),(y+.12,.69),(y+.20,.65)],'binding')
    m.shaft('收口铁箍',[(28.77,.58),(29.02,.66),(29.59,.67),(29.75,.62)],'socket_dark')
    m.shaft('枪头铁套',[(29.45,.60),(30.20,.67),(31.65,.83),(32.3,.83),(32.46,.64)],'socket',second='rim')
    m.blade([(31.96,.54,.46),(32.78,.67,.63),(33.2,1.12,.66),(34.66,1.71,.63),
             (35.71,1.56,.58),(37.25,.90,.45),(39.44,.042,.048)],land=.50)
    rivet_pair(m,31.35,[0],.79,'rivet',.16)
    return m


def baiqing_spear():
    m=Model('baiqing_spear','baiqing','白青灵金枪','枪头细长，两侧短翼与中刃隔开。',20.06,(29.9,43.9))
    m.shaft('枪尾银箍',[(0,.45),(.22,.63),(.57,.65),(1.32,.62),(1.70,.48)],'ivory',second='socket')
    m.shaft('青灰木枪杆',[(1.12,.48),(9.8,.53),(16.11,.56),(23.8,.54),(29.75,.53),(33.9,.57)],'wood')
    m.shaft('青布握缠',[(16.05,.55),(16.34,.65),(16.75,.65),(22.92,.64),(23.17,.62),(23.42,.55)],'wrap')
    for y in (16.44,22.8):
        m.shaft('外收青绳',[(y-.10,.63),(y-.03,.67),(y+.12,.67),(y+.19,.63)],'binding')
    m.shaft('枪颈',[(28.8,.54),(29.4,.71),(30.38,.63),(32.8,.65),(33.6,.72),(34,.75)],'socket_dark',second='socket')
    m.shaft('短翼卡座',[(31.26,.63),(31.52,.84),(32.6,.88),(32.94,.69)],'cloud',second='ivory')
    for s in (-1,1):
        m.plate('两片留空短翼',mirrored([
            (.49,31.63),(1.65,31.89),(2.37,32.56),(2.92,34.01),(2.76,35.30),
            (2.51,35.73),(2.30,34.54),(1.93,33.66),(1.30,33.21),(.65,32.83)],s),
                .41,'ivory','cloud',bevel=.075)
        m.plate('云翼底座',mirrored([
            (.57,31.54),(1.42,31.63),(1.83,32.33),(1.46,32.41),(.63,32.07)],s),
                .48,'cloud','socket',bevel=.12)
    m.blade([(32.05,.62,.44),(33.25,.65,.57),(34.25,1.10,.69),(35.07,1.43,.73),
             (36.5,1.25,.67),(39.68,.94,.48),(42.5,.43,.27),(43.6,.038,.028)],land=.35)
    for s in (-1,1):
        m.plate('枪颈青晶',[(0,31.2),(.37,31.69),(0,32.55),(-.37,31.69)],.13,'jade','inset',x=s*.83,bevel=.19)
    return m


def jinwu_spear():
    m=Model('jinwu_spear','jinwu','日魄乌金枪','八角日轮套在枪颈，轮内两侧留空。',21.05,(31.2,48))
    m.shaft('沉金枪尾',[(0,.40),(.24,.67),(.67,.76),(1.14,.68),(1.95,.59),(2.22,.53)],'bronze',second='rim')
    m.shaft('烟赤硬木枪杆',[(1.22,.53),(8.9,.57),(15.3,.61),(24.4,.60),(30.3,.59),(36.49,.57)],'wood')
    m.shaft('厚握带',[(16.0,.58),(16.46,.69),(17.1,.70),(24.36,.69),(24.75,.63),(25.07,.60)],
            'wrap')
    for y in (16.63,24.17):
        m.shaft('握带铜箍',[(y-.15,.69),(y-.07,.72),(y+.13,.72),(y+.2,.69)],'binding')
    m.shaft('枪颈底箍',[(30.0,.57),(30.37,.76),(31.24,.78),(31.42,.67)],'socket_dark',second='bronze')
    m.shaft('长榫',[(31.12,.66),(31.63,.70),(33.24,.89),(34.13,.79),(35.59,.68),(36.6,.65)],
            'socket_dark',second='bronze')
    for sign in (-1,1):
        m.plate('日轮支杆',mirrored([
            (.49,32.80),(1.47,33.48),(2.73,34.76),(2.87,35.71),(2.36,35.81),
            (1.27,34.87),(.48,34.06)],sign),.63,'bronze','wall',bevel=.09)
    m.ring('八角日轮',36.76,3.28,2.40,.55,'rim','bronze',n=8,roll=math.pi/8)
    for zz in (-1,1):
        m.plate('日轮侧扣',mirrored([
            (2.63,36.43),(3.11,36.16),(3.56,36.63),(3.56,36.95),
            (3.12,37.42),(2.63,37.08)],zz),.69,'socket_dark','rim',bevel=.18)
    m.blade([(35.10,.62,.45),(36.20,.72,.55),(38.20,.98,.61),(39.55,1.68,.76),
             (40.48,1.59,.75),(42.62,1.30,.69),(45.62,.74,.47),(47.54,.21,.18),
             (48.0,.035,.032)],land=.63)
    m.plate('日轮中央榫座',[(-.73,35.4),(-.58,35.02),(.58,35.02),(.73,35.4),(.78,36.41),
                          (.51,37.12),(-.51,37.12),(-.78,36.41)],.69,'socket_dark','bronze',bevel=.14)
    for side in (-1,1):
        core=[(.42*math.cos((j+.5)*math.tau/8),36.10+.49*math.sin((j+.5)*math.tau/8)) for j in range(8)]
        m.plate('枪颈日芯',core,.17,'sun','sun_dark',x=side*.73,bevel=.21)
    return m


def accepted_models():
    models = [xuantie_sword(), baiqing_sword(), jinwu_sword(), xuantie_spear(), baiqing_spear(), jinwu_spear()]
    return {model.key: add_join_details(model, rivet_pair) for model in models}
