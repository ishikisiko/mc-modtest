"""独立设计几何。体素配方是 authored 输入；不写 NBT、registry 或旧世界资源。"""
from __future__ import annotations

import math
from collections import defaultdict


def shape(recipe):
    """Cook explicit constructive recipes into integer cells, then lossless X runs."""
    cells = {}
    for op in recipe:
        kind, material = op['op'], op.get('m')
        if kind == 'box':
            lo, hi = op['a'], op['b']
            candidates = ((x, y, z) for y in range(lo[1], hi[1] + 1)
                          for z in range(lo[2], hi[2] + 1) for x in range(lo[0], hi[0] + 1))
        elif kind == 'ellipsoid':
            c, r = op['c'], op['r']
            candidates = ((x, y, z)
                          for y in range(math.floor(c[1] - r[1]), math.ceil(c[1] + r[1]) + 1)
                          for z in range(math.floor(c[2] - r[2]), math.ceil(c[2] + r[2]) + 1)
                          for x in range(math.floor(c[0] - r[0]), math.ceil(c[0] + r[0]) + 1)
                          if sum(((v - a) / b) ** 2 for v, a, b in zip((x, y, z), c, r)) <= 1)
        elif kind == 'ring':
            c, outer, inner, thick = op['c'], op['outer'], op['inner'], op['thick']
            plane = op.get('plane', 'xz')
            axes = {'xz': (0, 2, 1), 'xy': (0, 1, 2), 'yz': (2, 1, 0)}[plane]
            pts = []
            for u in range(-outer, outer + 1):
                for v in range(-outer, outer + 1):
                    radius = u * u + v * v
                    if inner * inner <= radius <= outer * outer:
                        for t in range(-(thick // 2), thick - thick // 2):
                            p = list(c)
                            p[axes[0]] += u
                            p[axes[1]] += v
                            p[axes[2]] += t
                            pts.append(tuple(p))
            candidates = pts
        elif kind == 'lathe':
            cx, cy, cz = op['c']
            profile = op['profile']
            pts = []
            for (y0, r0), (y1, r1) in zip(profile, profile[1:]):
                for yy in range(y0, y1 + 1):
                    r = r0 + (r1 - r0) * (yy - y0) / max(1, y1 - y0)
                    inner = max(0, r - op.get('wall', 999))
                    for z in range(-math.ceil(r), math.ceil(r) + 1):
                        for x in range(-math.ceil(r), math.ceil(r) + 1):
                            if inner * inner <= x*x + z*z <= r*r:
                                pts.append((cx+x, cy+yy, cz+z))
            candidates = pts
        elif kind == 'beam':
            a, b, radius = op['a'], op['b'], op['radius']
            steps = max(abs(b[i] - a[i]) for i in range(3))
            pts = set()
            for n in range(steps + 1):
                p = [round(a[i] + (b[i] - a[i])*n/max(1, steps)) for i in range(3)]
                for x in range(p[0]-radius, p[0]+radius+1):
                    for y in range(p[1]-radius, p[1]+radius+1):
                        for z in range(p[2]-radius, p[2]+radius+1):
                            pts.add((x, y, z))
            candidates = sorted(pts)
        else:
            raise ValueError(f'未知设计配方类型：{kind}')
        for p in candidates:
            if material is None:
                cells.pop(p, None)
            else:
                cells[p] = material
    rows = defaultdict(dict)
    for (x, y, z), material in cells.items():
        rows[y, z][x] = material
    boxes = []
    for (y, z), row in sorted(rows.items()):
        xs = sorted(row)
        i = 0
        while i < len(xs):
            start, material = xs[i], row[xs[i]]
            end = start
            i += 1
            while i < len(xs) and xs[i] == end + 1 and row[xs[i]] == material:
                end = xs[i]
                i += 1
            boxes.append({'p': [(start+end+1)/2, y+.5, z+.5], 's': [end-start+1, 1, 1], 'm': material})
    return boxes


class Scene:
    def __init__(self, materials):
        self.boxes = []
        self.meshes = []
        self.materials = materials
        self.items = []
        self.cache = {}

    def box(self, x, y, z, w, h, d, material, layer, tag):
        if min(w, h, d) <= 0:
            raise ValueError(f'非正几何尺寸：{tag}')
        self.boxes.append({'p': [x+w/2, y+h/2, z+d/2], 's': [w, h, d],
                           'm': material, 'layer': layer, 'tag': tag, 'q': 0})

    def recipe(self, name, recipes, origin=(0, 0, 0), scale=1, layer='landmark', tag=None, q=0):
        if name not in self.cache:
            self.cache[name] = shape(recipes[name])
        first = len(self.boxes)
        for b in self.cache[name]:
            x, y, z = [v*scale for v in b['p']]
            x, z = ((x, z), (-z, x), (-x, -z), (z, -x))[q % 4]
            s = [v*scale for v in b['s']]
            if q % 2:
                s[0], s[2] = s[2], s[0]
            self.boxes.append({'p': [x+origin[0], y+origin[1], z+origin[2]], 's': s,
                               'm': b['m'], 'layer': layer, 'tag': tag or name, 'q': 0})
        self.items.append({'id': tag or name, 'recipe': name, 'origin': list(origin),
                           'scale': scale, 'box_count': len(self.boxes)-first})

    def bounds(self):
        return [[min(b['p'][i]-b['s'][i]/2 for b in self.boxes) for i in range(3)],
                [max(b['p'][i]+b['s'][i]/2 for b in self.boxes) for i in range(3)]]

    def export(self, metadata):
        unknown = {b['m'] for b in self.boxes} - self.materials.keys()
        if unknown:
            raise ValueError(f'未定义材质：{unknown}')
        return {**metadata, 'materials': self.materials, 'boxes': self.boxes,
                'meshes': self.meshes, 'bounds': self.bounds(), 'items': self.items}
