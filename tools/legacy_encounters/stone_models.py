"""守碑石灵与镇岳尺的连续截面模型；游戏、UV 和预览共用这些顶点。"""
from __future__ import annotations

import json
import math
from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path

from tools.beastgen.builder import Builder
from tools.beastgen import cuboid

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'src/main/resources'


@dataclass
class Panel:
    name: str
    kind: str
    width: float
    height: float
    rect: tuple = ()


@dataclass
class Face:
    bone: str
    panel: str
    points: list
    uv: list


@dataclass
class Surface:
    name: str
    panels: dict = field(default_factory=dict)
    faces: list = field(default_factory=list)
    texture_size: tuple = (0, 0)

    def pack(self, density, width):
        sizes = []
        for p in self.panels.values():
            w, h = max(12, math.ceil(p.width * density)), max(12, math.ceil(p.height * density))
            sizes.append((p.name, w + 4, h + 4))
        sizes.sort(key=lambda r: (-r[2], -r[1], r[0]))
        packed = cuboid._skyline_pack(sizes, width)
        if packed is None:
            raise ValueError('surface atlas does not fit')
        positions, height = packed
        self.texture_size = (width, 2 ** math.ceil(math.log2(height)))
        for name, w, h in sizes:
            x, y = positions[name]
            self.panels[name].rect = (x + 2, y + 2, w - 4, h - 4)

    def mapped(self, face):
        x, y, w, h = self.panels[face.panel].rect
        tw, th = self.texture_size
        return [((x + .5 + u * (w - 1)) / tw, (y + .5 + v * (h - 1)) / th) for u, v in face.uv]

    def triangles(self):
        for f in self.faces:
            uv = self.mapped(f)
            for i in range(1, len(f.points) - 1):
                yield f.bone, f.panel, [(*f.points[j], *uv[j]) for j in (0, i, i + 1)]


def _cross(a, b, c):
    p = [b[i] - a[i] for i in range(3)]
    q = [c[i] - a[i] for i in range(3)]
    return (p[1] * q[2] - p[2] * q[1], p[2] * q[0] - p[0] * q[2], p[0] * q[1] - p[1] * q[0])


def _ring(row):
    y, hx, hz, bevel, cx, cz = row
    b = min(bevel, hx * .8, hz * .8)
    return [(cx + x, y, cz + z) for x, z in [
        (-hx + b, -hz), (hx - b, -hz), (hx, -hz + b), (hx, hz - b),
        (hx - b, hz), (-hx + b, hz), (-hx, hz - b), (-hx, -hz + b)]]


def loft(surface, name, bone, rows, kind, *, front_warp=None, broad_sides=False):
    """封闭八边形截面；相邻截面共享边界，不通过叠加盒子拼出斜面。"""
    rows = sorted(rows)
    rings = [_ring(row) for row in rows]
    top, bottom = rows[0][0], rows[-1][0]
    for side in range(8):
        title = ('front', 'front_bevel_r', 'right', 'back_bevel_r', 'back', 'back_bevel_l', 'left', 'front_bevel_l')[side]
        panel_name = name + '.' + title
        panel_kind = kind + ('_front' if side in ((2, 6) if broad_sides else (0,)) else '_back' if side == 4 else '_edge')
        width = max(math.dist(r[side], r[(side + 1) % 8]) for r in rings)
        surface.panels[panel_name] = Panel(panel_name, panel_kind, width, bottom - top)
        columns = [0, .12, .28, .5, .72, .88, 1] if side == 0 and front_warp else [0, 1]
        for j in range(len(rings) - 1):
            for i in range(len(columns) - 1):
                points, uv = [], []
                for row_index, u in [(j, columns[i]), (j + 1, columns[i]), (j + 1, columns[i + 1]), (j, columns[i + 1])]:
                    a, b = rings[row_index][side], rings[row_index][(side + 1) % 8]
                    p = [a[k] * (1 - u) + b[k] * u for k in range(3)]
                    v = (p[1] - top) / (bottom - top)
                    if side == 0 and front_warp:
                        p[2] += front_warp(u, v)
                    points.append(tuple(p));uv.append((u, v))
                center = [sum(p[k] for p in points) / 4 for k in range(3)]
                axis_center = ((rows[j][4] + rows[j + 1][4]) / 2, center[1], (rows[j][5] + rows[j + 1][5]) / 2)
                normal = _cross(*points[:3])
                if sum(normal[k] * (center[k] - axis_center[k]) for k in range(3)) < 0:
                    points.reverse();uv.reverse()
                surface.faces.append(Face(bone, panel_name, points, uv))
    for index, sign, end in [(0, -1, 'top'), (-1, 1, 'bottom')]:
        ring = rings[index];row = rows[index];panel_name = name + '.' + end
        surface.panels[panel_name] = Panel(panel_name, kind + '_cap', row[1] * 2, row[2] * 2)
        center = (row[4], row[0], row[5])
        for i in range(8):
            pts = [center, ring[i], ring[(i + 1) % 8]]
            uv = [((p[0] - row[4]) / (row[1] * 2) + .5, (p[2] - row[5]) / (row[2] * 2) + .5) for p in pts]
            if _cross(*pts)[1] * sign < 0:
                pts.reverse();uv.reverse()
            surface.faces.append(Face(bone, panel_name, pts, uv))


def _chest(u, v):
    # The inscription lies in an actual shallow recess carved into the continuous front surface.
    return .52 * min(1, max(0, (u - .12) / .12), max(0, (.88 - u) / .12)) * math.sin(math.pi * v) ** 2


def _face(u, v):
    edge = math.sin(math.pi * u) ** 2
    brow = math.exp(-((v - .29) / .08) ** 2) * .8
    eye = (math.exp(-((u - .29) / .13) ** 2) + math.exp(-((u - .71) / .13) ** 2)) * math.exp(-((v - .43) / .10) ** 2) * .65
    nose = math.exp(-((u - .5) / .13) ** 2) * math.exp(-((v - .59) / .19) ** 2) * 1.0
    return edge * (eye - brow - nose)


@lru_cache(maxsize=1)
def guardian_surface():
    s = Surface('stele_guardian')
    loft(s, 'torso', 'body', [
        (-58, 8, 6.5, 2.0, 0, 3.0), (-53, 12.0, 8.1, 2.1, 0, 2.0),
        (-47, 14.0, 9.0, 2.3, 0, 1.5), (-40, 12.8, 9.0, 2.1, 0, .8),
        (-30, 10.4, 7.4, 1.8, 0, .8), (-22, 8.4, 6.0, 1.5, 0, 1.0),
        (-20, 8.2, 5.8, 1.6, 0, 1.0)], 'stone_torso', front_warp=_chest)
    loft(s, 'stone_head', 'head', [
        (-64, 4.2, 3.8, 1.0, 0, -2.5), (-62, 5.7, 4.8, 1.2, 0, -3.0),
        (-59.8, 6.0, 5.0, 1.1, 0, -3.7), (-58, 6.1, 5.0, 1.0, 0, -3.8),
        (-56, 5.9, 4.7, 1.1, 0, -4.0), (-53, 5.5, 4.5, 1.2, 0, -4.2),
        (-50, 4.8, 4.0, 1.4, 0, -4.0), (-48.5, 4.1, 3.4, 1.1, 0, -3.5)], 'stone_head', front_warp=_face)
    for side, sign in [('right', -1), ('left', 1)]:
        loft(s, 'upper_arm_' + side, 'arm_' + side, [
            (-50, 4.5, 5.1, 1.5, sign * 13.5, 1.8), (-46, 6.1, 6.1, 1.8, sign * 15.2, 1.0),
            (-37, 5.0, 5.1, 1.6, sign * 15.7, -.1), (-31, 4.4, 4.6, 1.4, sign * 16, -.8)], 'stone_arm')
        loft(s, 'forearm_' + side, 'forearm_' + side, [
            (-32, 4.5, 4.7, 1.4, sign * 16, -.8), (-27, 5.4, 5.9, 1.6, sign * 16.1, -1.7),
            (-19, 6.0, 7.0, 1.8, sign * 16.1, -2.8), (-14, 5.8, 6.7, 1.9, sign * 15.8, -3.0),
            (-12.5, 4.7, 5.5, 1.6, sign * 15.6, -2.9)], 'stone_fist')
        loft(s, 'leg_' + side, 'leg_' + side, [
            (-22, 5.2, 5.7, 1.5, sign * 6.1, 1.0), (-17, 5.4, 5.8, 1.6, sign * 6.9, 1.0),
            (-9, 4.5, 4.8, 1.3, sign * 7.3, .4), (-4, 5.5, 6.8, 1.7, sign * 7.5, -.9),
            (-1.5, 6.0, 7.5, 1.8, sign * 7.5, -1.8), (0, 5.7, 7.0, 1.5, sign * 7.5, -1.8)], 'stone_leg')
    s.pack(4, 1024)
    return s


def guardian_skeleton():
    from tools.gen_legacy_encounters import catalogue
    entry = next(m for m in catalogue()['mobs'] if m['id'] == 'stele_guardian')
    b = Builder('myvillage:stele_guardian', look={'bone': 'head', 'max_yaw': 16, 'max_pitch': 12}, shadow_radius=entry['physical']['shadow_radius'])
    b.bone('body', 'root', at=(0, -36, 0))
    b.bone('head', 'body', at=(0, -53, -3.5))
    for side, sign in [('right', -1), ('left', 1)]:
        b.bone('arm_' + side, 'body', at=(sign * 13.5, -48, 1.2))
        b.bone('forearm_' + side, 'arm_' + side, at=(sign * 16, -31.5, -.8))
        b.bone('leg_' + side, 'root', at=(sign * 6.5, -21, 1))
    # Cubes are bind bounds for gait/animation tooling. A surface_mesh replaces their visible draw.
    for bone in b.model.bones:
        points = [p for f in guardian_surface().faces if f.bone == bone.name for p in f.points]
        if not points:
            continue
        lo = [math.floor(min(p[k] for p in points)) for k in range(3)]
        hi = [math.ceil(max(p[k] for p in points)) for k in range(3)]
        b.box(bone.name, bone.name + '_bind_bounds', lo, [hi[k] - lo[k] for k in range(3)])
    b.model.surface_texture_size = guardian_surface().texture_size
    return b.model


def guardian_document(model):
    matrices = model.rest_matrices()
    root_inv = cuboid.mat_inverse_rigid(matrices['root'])
    local = {name: cuboid.mat_inverse_rigid(cuboid.mat_mul(root_inv, matrix)) for name, matrix in matrices.items()}
    parts = {}
    for bone, _, vertices in guardian_surface().triangles():
        triangle = []
        for vertex in vertices:
            triangle.extend(round(v, 6) for v in (*cuboid.mat_apply(local[bone], vertex[:3]), *vertex[3:]))
        parts.setdefault(bone, []).append(triangle)
    return {'schema': 1, 'id': model.id, 'parts': [{'bone': bone, 'triangles': faces} for bone, faces in parts.items()]}


def finalize_guardian(built):
    from .stone_materials import atlas
    built.model.texture_size = guardian_surface().texture_size
    built.texture, built.glow = atlas(guardian_surface())
    built.surface = guardian_document(built.model)
    built.model.surface = built.surface
    built.model_doc['texture'] = dict(zip(('width', 'height'), built.model.texture_size))
    built.model_doc['surface_mesh'] = 'myvillage:beast/stele_guardian_surface.json'


def guardian_extra_outputs(built):
    return {RES / 'assets/myvillage/beast/stele_guardian_surface.json': (json.dumps(built.surface, separators=(',', ':')) + '\n').encode()}


@lru_cache(maxsize=1)
def ruler_surface():
    from tools.gen_legacy_encounters import catalogue
    shape = next(i['shape'] for i in catalogue()['items'] if i['id'] == 'zhenyue_ruler')
    tip, root = shape['tip_y'], shape['blade_root_y']
    thick, wide = shape['head_half_thickness'], shape['head_half_width']
    s = Surface('zhenyue_ruler')
    # Author in item coordinates (+Y up). Ends, waist and grip are one uninterrupted loft.
    rows = [
        (0, .55, .72, .18, 0, 0), (.55, .8, 1.0, .24, 0, 0),
        (1.35, .72, .85, .22, 0, 0), (2.3, shape['handle_half_thickness'], shape['handle_half_width'], .24, 0, 0),
        (5.7, shape['handle_half_thickness'] * .86, shape['handle_half_width'] * .91, .23, 0, 0),
        (root - .7, 1.05, 1.3, .30, 0, 0), (root + .25, thick * .82, wide * .80, .52, 0, 0),
        (root + 1.5, thick, wide, .70, 0, 0), (tip - 2.2, thick * .79, wide * .95, .62, 0, 0),
        (tip - .65, thick * .73, wide * .81, .58, 0, 0), (tip, thick * .55, wide * .66, .45, 0, 0)]
    loft(s, 'ruler', 'item', rows, 'jade', broad_sides=True)
    for f in s.faces:
        f.uv = [(u, 1-v) for u,v in f.uv]
    s.pack(12, 512)
    return s


def ruler_model(item):
    from tools.gen_qingfeng_sword_model import build_display
    return {'loader': 'neoforge:obj', 'model': 'myvillage:models/item/zhenyue_ruler.obj',
            'automatic_culling': False, 'shade_quads': True, 'flip_v': False, 'emissive_ambient': False,
            'gui_light': 'side', 'textures': {'particle': 'myvillage:item/zhenyue_ruler'}, 'display': build_display()}


def ruler_outputs():
    from .stone_materials import atlas
    from tools.gen_qingfeng_sword_model import encode_png
    from tools.equipment.mesh import obj_assets
    s = ruler_surface();faces=[]
    for face in s.faces:
        points=[(x+8,y,z+8) for x,y,z in face.points];uv=s.mapped(face)
        if len(points)==3:points.append(points[-1]);uv.append(uv[-1])
        faces.append({'part':face.panel,'points':points,'uv':uv,'texture':'myvillage:item/zhenyue_ruler'})
    files,_=obj_assets(faces,'myvillage:item/zhenyue_ruler')
    texture, _ = atlas(s)
    return {**{RES/path:data for path,data in files.items()},RES / 'assets/myvillage/textures/item/zhenyue_ruler.png':encode_png(texture)}
