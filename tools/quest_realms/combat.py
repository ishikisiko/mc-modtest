"""两张纯战斗地图的离线构造；坐标、敌群和场景参数来自作者 JSON。"""
from __future__ import annotations

import math

from .model import Scene, distance_segment


def gable(scene, x, y, z, w, d, material):
    pitch = .42
    for i in range(math.ceil(w / 2)):
        width = w - i * 2
        if width <= 0:
            break
        scene.box(x + i, y + i * pitch, z, width, .7, d, material, 'roof')
        for end in (z, z + d - .7):
            scene.box(x + i, y + i * pitch - .4, end, width, .5, .7, 'darkwood', 'roof')
    scene.box(x + w / 2 - .5, y + math.ceil(w / 2) * pitch, z - .5, 1, .8, d + 1, 'darkwood', 'roof')


def timber_wall(scene, s, side):
    cx, y, cz = s['pos']
    w, h, d = s['size']
    along = side in ('south', 'north')
    length = w if along else d
    x = cx - w / 2 if along or side == 'west' else cx + w / 2 - .8
    z = cz - d / 2 if not along or side == 'south' else cz + d / 2 - .8
    door_width = s.get('door_width', 7)
    for i in range(math.ceil(length / 2)):
        offset = i * 2
        span = min(2, length - offset)
        xx, zz = (x + offset, z) if along else (x, z + offset)
        width, depth = (span, .8) if along else (.8, span)
        opening = side in s.get('doors', []) and abs(offset + span / 2 - length / 2) < door_width / 2 + .4
        if s.get('open_front') and side == 'south':
            opening = True
        if opening:
            scene.box(xx, y + 5, zz, width, h - 5, depth, 'spruce_planks', 'wall')
        else:
            scene.box(xx, y, zz, width, .8, depth, 'rock_dark', 'wall')
            if i % 3 == 1:
                scene.box(xx, y + .8, zz, width, 1.6, depth, 'spruce_planks', 'wall')
                scene.box(xx, y + 3.8, zz, width, h - 3.8, depth, 'spruce_planks', 'wall')
                scene.box(xx, y + 2.4, zz, width, .2, depth, 'darkwood', 'window')
                scene.box(xx, y + 3.6, zz, width, .2, depth, 'darkwood', 'window')
                scene.box(xx + width / 2 - .1, y + 2.4, zz + depth / 2 - .1, .2, 1.4, .2, 'darkwood', 'window')
            else:
                scene.box(xx, y + .8, zz, width, h - .8, depth, 'spruce_planks', 'wall')
        if i % 3 == 0 and not opening:
            scene.box(xx - .15, y, zz - .15, max(.7, width * .35), h, max(.7, depth * .35), 'bark', 'beam')
    scene.box(x, y + h - .7, z, w if along else .9, .8, .9 if along else d, 'darkwood', 'beam')


def house(scene, s):
    cx, y, cz = s['pos']
    w, h, d = s['size']
    x, z = cx - w / 2, cz - d / 2
    scene.box(x - .7, y - 3.5, z - .7, w + 1.4, 3.1, d + 1.4, 'rock_dark', 'foundation')
    scene.box(x, y - .4, z, w, .4, d, 'floor', 'floor')
    for side in ('south', 'north', 'west', 'east'):
        timber_wall(scene, s, side)
    for xx in range(math.ceil(x + 2), math.floor(x + w - 1), 6):
        scene.box(xx, y + h - 1, z, .7, .8, d, 'darkwood', 'beam')
    for xx in (x, x + w - 1):
        scene.box(xx, y + h - 1.5, z, 1, 1.5, d, 'bark', 'beam')
    gable(scene, x - 1.5, y + h, z - 1.5, w + 3, d + 3, s.get('roof_material', 'thatch'))
    porch = min(28, w - 4)
    scene.box(cx - porch / 2, y - .3, z - 4, porch, .3, 4, 'floor', 'floor')
    for xx in (cx - porch / 2, cx + porch / 2 - .7):
        scene.box(xx, y, z - 4, .7, 5.5, .7, 'bark', 'beam')
    scene.box(cx - porch / 2, y + 5.2, z - 4, porch, .6, .7, 'darkwood', 'beam')
    gable(scene, cx - porch / 2 - 1, y + 5.5, z - 5, porch + 2, 5, s.get('roof_material', 'thatch'))
    use = s.get('use', 'depot')
    if use in ('barracks', 'depot', 'workshop'):
        scene.furniture(use, x + 3, y, z + 3, w - 6, d - 6)
    if use == 'depot':
        for xx in (x + 3, x + w - 6):
            for zz in (z + 4, z + d - 6):
                scene.box(xx, y, zz, 2.5, 1.4, 2, 'thatch', 'prop')
                scene.box(xx + .3, y + 1.4, zz + .2, 1.9, 1, 1.6, 'cloth', 'prop')
    if use == 'kitchen':
        scene.box(x + 2, y, z + 3, 5, 2.4, 5, 'rock_dark', 'prop')
        scene.box(x + 3, y + 2.4, z + 4, 3, .3, 3, 'iron', 'prop')
        scene.box(x + 2, y + 2.4, z + 3, 2, h + 4, 2, 'brick', 'detail')
        scene.table(x + 3, y, z + d - 7, 6, 3)
        for j in range(4):
            scene.box(x + w - 5, y + j * .6, z + 3, 2, .5, 5, 'bark', 'prop')
    if use == 'workshop':
        scene.box(x + w - 6, y, z + d - 6, 3, 2, 3, 'rock_dark', 'prop')
        scene.box(x + w - 5.5, y + 2, z + d - 5.5, 2, .2, 2, 'lava', 'prop')
        weapons(scene, dict(pos=[x + 5, y, z + d - 4], size=[7, 4, 2]))
    if use == 'command':
        scene.box(x + 4, y, z + d - 7, 9, .7, 4, 'wood', 'prop')
        scene.box(x + w - 13, y, z + d - 7, 9, .7, 4, 'wood', 'prop')
        scene.table(x + 4, y + .7, z + d - 7, 7, 3)
        for xx in (x + 3, x + w - 5):
            for zz in (z + 5, z + d - 6):
                scene.lantern(xx, y + 2, zz)
        for xx in (cx - 7, cx + 7):
            scene.box(xx, y + h - 4, z + d - 1.4, 3.5, 3, .3, 'redwood', 'detail')
    for xx in (x + 2, x + w - 3):
        scene.lantern(xx, y + 2, z - 1)


def gate(scene, s):
    cx, y, cz = s['pos']
    w, h, d = s['size']
    for xx in (cx - w / 2, cx + w / 2 - 7):
        scene.box(xx, y - 1, cz - d / 2, 7, 5, d, 'rock_dark', 'wall')
        scene.box(xx + .7, y + 4, cz - d / 2 + .7, 5.6, 8, d - 1.4, 'planks', 'wall')
        for px in (xx, xx + 6):
            for pz in (cz - d / 2, cz + d / 2 - 1):
                scene.box(px, y, pz, 1, h - 3, 1, 'bark', 'beam')
        gable(scene, xx - 1, y + 12, cz - d / 2 - 1, 9, d + 2, 'tile_dark')
    scene.box(cx - w / 2 + 6, y + 6, cz - d / 2 + 1, w - 12, 2, d - 2, 'darkwood', 'beam')
    scene.box(cx - w / 2 + 6, y + 8, cz - d / 2 + 1, w - 12, .6, d - 2, 'floor', 'floor')
    for xx in range(math.ceil(cx - w / 2 + 8), math.floor(cx + w / 2 - 6), 3):
        scene.box(xx, y + 8.6, cz - d / 2, .5, 2, .5, 'wood', 'rail')
        scene.box(xx, y + 8.6, cz + d / 2 - 1, .5, 2, .5, 'wood', 'rail')
    scene.box(cx - 6, y + 7, cz - d / 2 - .5, 12, 2, .5, 'redwood', 'detail')
    for xx in (cx - 11, cx + 11):
        scene.lantern(xx, y + 3, cz - d / 2 - 1)


def tower(scene, s):
    cx, y, cz = s['pos']
    w, h, d = s['size']
    x, z = cx - w / 2, cz - d / 2
    scene.box(x, y - 1, z, w, 1, d, 'rock_dark', 'foundation')
    for xx in (x, x + w - 1):
        for zz in (z, z + d - 1):
            scene.box(xx, y, zz, 1, h - 4, 1, 'bark', 'beam')
    scene.box(x + 8, y + 7.5, z, w - 8, .5, d, 'floor', 'floor')
    scene.box(x, y + 7.5, z + 9, 8, .5, d - 9, 'floor', 'floor')
    # 作者路径构造下层台阶；上层围栏给节点留完整通行口。
    for xx in (x, x + w - 1):
        for zz in (z, z + d - 1):
            scene.box(xx, y + 8, zz, 1, 2, 1, 'planks', 'rail')
    scene.box(x + w - 1, y, z, 1, 7.5, d, 'planks', 'wall')
    scene.box(x, y, z + d - 1, w, 7.5, 1, 'planks', 'wall')
    for xx in (x, x + w - 1):
        scene.box(xx, y + 10, z, 1, h - 14, d, 'darkwood', 'beam')
    gable(scene, x - 1.5, y + h - 4, z - 1.5, w + 3, d + 3, 'tile_dark')


def stockade(scene, s):
    cx, y, cz = s['pos']
    w, h, d = s['size']
    along = w > d
    for i in range(math.ceil((w if along else d) / 1.5)):
        xx = cx - w / 2 + i * 1.5 if along else cx - .7
        zz = cz - .7 if along else cz - d / 2 + i * 1.5
        coordinate = (xx if along else zz) + .6
        if any(low <= coordinate <= high for low, high in s.get('gaps', [])):
            continue
        hh = h - (i % 3) * .5
        scene.box(xx, y - .8, zz, 1.2, hh, 1.2, 'bark', 'wall')
        scene.box(xx + .2, y + hh - .8, zz + .2, .8, .7, .8, 'wood', 'wall')
        for yy in (y + 2, y + 5.5):
            scene.box(xx, yy, zz, 1.5 if along else w, .6, d if along else 1.5, 'darkwood', 'beam')


def retaining(scene, s):
    x, y, z = s['pos']
    w, h, d = s['size']
    for i in range(math.ceil(w / 2)):
        xx = x - w / 2 + i * 2
        if scene.doorway(xx + 1, y + h, z):
            continue
        scene.box(xx, y, z - d / 2, min(2, w - i * 2), h, d, 'rock_dark', 'wall')
        scene.box(xx, y + h - .4, z - d / 2 - .3, min(2, w - i * 2), .4, d + .6, 'stone', 'wall')


def cover(scene, s):
    x, y, z = s['pos']
    w, h, d = s['size']
    scene.box(x - w / 2, y, z - d / 2, w, h, d, s.get('material', 'stone'), 'cover')
    scene.box(x - w / 2 - .15, y + h, z - d / 2 - .15, w + .3, .3, d + .3, 'wood' if s.get('material') == 'planks' else 'rock_dark', 'cover')


def cart(scene, s):
    x, y, z = s['pos']
    w, _, d = s['size']
    scene.box(x - w / 2, y + 1, z - d / 2, w, .5, d, 'darkwood', 'cover')
    scene.box(x - w / 2 + .5, y + 1.5, z - d / 2 + 1, w - 1, 1.8, d - 2, 'thatch', 'cover')
    for xx in (x - w / 2 - .3, x + w / 2):
        for zz in (z - d / 3, z + d / 3):
            scene.box(xx, y, zz - 1, .5, 2, 2, 'darkwood', 'cover')
            scene.box(xx - .1, y + .75, zz - .25, .7, .5, .5, 'iron', 'cover')
    for xx in (x - 1, x + 1):
        scene.box(xx, y + 1.2, z - d / 2 - 3, .4, .4, 4, 'wood', 'cover')


def weapons(scene, s):
    x, y, z = s['pos']
    w, h, d = s['size']
    for xx in (x - w / 2, x + w / 2 - .5):
        scene.box(xx, y, z, .5, h, .6, 'darkwood', 'prop')
    scene.box(x - w / 2, y + h * .6, z, w, .5, .6, 'wood', 'prop')
    for xx in range(math.floor(x - w / 2 + 1), math.ceil(x + w / 2 - 1), 2):
        scene.box(xx, y + .3, z - .3, .25, h, .25, 'wood', 'prop')
        scene.box(xx - .2, y + h, z - .4, .65, .7, .35, 'iron', 'prop')


def dummy(scene, s):
    x, y, z = s['pos']
    scene.box(x - .4, y, z - .4, .8, 3, .8, 'bark', 'prop')
    scene.box(x - 1.5, y + 1.4, z - .3, 3, .5, .6, 'wood', 'prop')
    scene.box(x - .7, y + 2.4, z - .7, 1.4, 1.2, 1.4, 'thatch', 'prop')


def exit_stand(scene, s):
    x, y, z = s['pos']
    scene.box(x - 1.5, y, z - 1.5, 3, .8, 3, 'stone', 'detail')
    scene.box(x - 1, y + .8, z - 1, 2, .5, 2, 'bronze', 'detail')
    scene.box(x - .6, y + 1.3, z - .6, 1.2, 1.5, 1.2, 'jade', 'detail')


def rock(scene, s):
    x, y, z = s['pos']
    w, h, d = s['size']
    material = s.get('material', 'rock')
    for row in range(math.ceil(h)):
        inset = row * .35
        scene.box(x - w / 2 + inset, y + row, z - d / 2 + inset / 2,
                  w - inset * 2, 1, d - inset, material if material != 'rock' else 'cave_rock', 'cave_detail')


def pillar(scene, s):
    x, y, z = s['pos']
    w, h, d = s['size']
    for row in range(math.ceil(h / 2)):
        bulge = .9 * math.cos(row * .7)
        scene.box(x - w / 2 + bulge, y + row * 2, z - d / 2 - bulge,
                  w - bulge * 2, min(2, h - row * 2), d + bulge * .7, 'cave_tuff' if row % 4 == 0 else 'cave_rock', 'cave_detail')
    scene.box(x - w / 2 - 1, y, z - d / 2 - 1, w + 2, .7, d + 2, 'cave_moss', 'cave_detail')


def nest(scene, s):
    x, y, z = s['pos']
    w, h, d = s['size']
    scene.box(x - w / 2, y, z - d / 2, w, .25, d, 'earth', 'cave_detail')
    for i in range(3):
        scene.box(x - w / 2 + i, y + .25 + i * .18, z - d / 2 + i * .6,
                  w - i * 2, .25, d - i * 1.2, 'thatch', 'cave_detail')
    for dx in (-w / 2, w / 2 - .5):
        scene.box(x + dx, y, z - d / 2, .5, min(h, .8), d, 'bark', 'cave_detail')


def bones(scene, s):
    x, y, z = s['pos']
    w, h, d = s['size']
    scene.box(x - w / 2, y + .2, z - .3, w, .5, .6, 'bone', 'cave_detail')
    for i in range(max(2, math.floor(w / 2))):
        xx = x - w / 2 + .5 + i * 1.8
        for sign in (-1, 1):
            for row in range(math.ceil(h)):
                spread = (d / 2) * max(.15, 1 - row / max(1, h))
                scene.box(xx, y + row * .7, z + sign * spread - .3, .5, .7, .6, 'bone', 'cave_detail')


FORMS = {
    'combat_house': house, 'combat_gate': gate, 'combat_tower': tower,
    'combat_stockade': stockade, 'combat_retaining': retaining, 'combat_cover': cover, 'combat_cart': cart,
    'combat_weapons': weapons, 'combat_dummy': dummy, 'combat_exit': exit_stand,
    'den_rock': rock, 'den_pillar': pillar, 'den_nest': nest, 'den_bones': bones,
}


def actor(scene, kind, pos, condition='', player=False):
    """明确标为体积参照，不复制运行时实体模型或动画。"""
    x, y, z = pos
    layer = 'scale_actor' if player else 'encounter_actor'
    def box(dx, dy, dz, w, h, d, mat):
        scene.box(x + dx, y + dy, z + dz, w, h, d, mat, layer, condition)
    if kind in ('bandit', 'caster', 'chief', 'player'):
        mat = 'jade' if player else 'redwood' if kind == 'caster' else 'darkwood'
        scale = 1.15 if kind == 'chief' else 1
        for dx in (-.25, .05):
            box(dx, 0, -.18, .2, .7 * scale, .35, 'darkwood')
        box(-.3, .7 * scale, -.18, .6, .65 * scale, .35, mat)
        box(-.23, 1.35 * scale, -.23, .46, .45 * scale, .46, 'cloth')
        for dx in (-.5, .3):
            box(dx, .75 * scale, -.15, .2, .55 * scale, .3, mat)
        box(.55, .55, -.2, .12, 1, .2, 'iron' if kind != 'caster' else 'wood')
        return
    scale = 2.1 if kind == 'wolf_boss' else 1.2 if kind in ('badger', 'lizard') else 1
    coat = 'cave_dark' if kind in ('wolf', 'wolf_boss') else 'earth' if kind == 'badger' else 'redwood'
    box(-.55 * scale, .45 * scale, -1.1 * scale, 1.1 * scale, .75 * scale, 2 * scale, coat)
    box(-.45 * scale, .8 * scale, -1.6 * scale, .9 * scale, .7 * scale, .9 * scale, coat)
    box(-.32 * scale, .85 * scale, -1.95 * scale, .64 * scale, .3 * scale, .45 * scale, 'cave_dark')
    for dx in (-.5, .23):
        for dz in (-.8, .6):
            box(dx * scale, 0, dz * scale, .27 * scale, .55 * scale, .35 * scale, coat)
    for dx in (-.4, .2):
        box(dx * scale, 1.5 * scale, -1.25 * scale, .2 * scale, .35 * scale, .25 * scale, coat)
    box(-.15 * scale, .7 * scale, .9 * scale, .3 * scale, .3 * scale, .85 * scale, coat)


class CombatScene(Scene):
    def compact(self):
        groups = {}
        untouched = []
        for b in self.boxes:
            if b[7] not in ('geology', 'ground', 'cave_roof', 'cave_floor', 'path', 'foundation') or b[8]:
                untouched.append(b)
                continue
            key = (b[1], b[2], b[4], b[5], b[6], b[7], b[8])
            groups.setdefault(key, []).append(b)
        for group in groups.values():
            group.sort(key=lambda b: b[0])
            current = group[0].copy()
            for b in group[1:]:
                if abs(current[0] + current[3] - b[0]) < .001:
                    current[3] = round(current[3] + b[3], 3)
                else:
                    untouched.append(current)
                    current = b.copy()
            untouched.append(current)
        self.boxes = untouched
        self.details.clear()
        for b in self.boxes:
            self.details[b[7]] += 1

    def path_tile_allowed(self, x, z, feet):
        return True

    def walk_level(self, x, z):
        best = (float('inf'), 0, None)
        for a, b, edge in self.segments:
            distance, raw = distance_segment(x, z, a, b)
            if distance >= best[0]:
                continue
            rise = b[1] - a[1]
            run = math.hypot(b[0] - a[0], b[2] - a[2])
            if rise and run:
                t = (raw - a[1]) / rise
                flat = min(1.25, max(0, (run - abs(rise)) / 2))
                t = max(0, min(1, (t * run - flat) / max(.1, run - flat * 2)))
                raw = a[1] + rise * t
            best = distance, round(raw * 2) / 2, edge
        return best

    def paths_geometry(self):
        # 半格栅格允许台阶连续上行；每格只提交一个最终标高。
        cells = set()
        for a, b, _ in self.segments:
            for ix in range(math.floor((min(a[0], b[0]) - 4) * 2), math.ceil((max(a[0], b[0]) + 4) * 2)):
                for iz in range(math.floor((min(a[2], b[2]) - 4) * 2), math.ceil((max(a[2], b[2]) + 4) * 2)):
                    if distance_segment(ix / 2 + .25, iz / 2 + .25, a, b)[0] < 3.4:
                        cells.add((ix, iz))
        rows = {}
        for ix, iz in sorted(cells):
            x, z = ix / 2, iz / 2
            _, feet, edge = self.walk_level(x + .25, z + .25)
            if not self.path_tile_allowed(x + .25, z + .25, feet):
                continue
            mat = 'cave_floor' if edge['kind'] == 'tunnel' else 'stone' if edge['kind'] == 'stairs' else 'dirt_path'
            rows.setdefault((feet, iz, mat), []).append(ix)
        for (feet, iz, mat), xs in rows.items():
            start = last = xs[0]
            for ix in xs[1:] + [10**9]:
                if ix == last + 1:
                    last = ix
                    continue
                self.box(start / 2, feet - .5, iz / 2, (last - start + 1) / 2, .5, .5, mat, 'path')
                start = last = ix

    def structures(self):
        for structure in self.spec['structures']:
            FORMS[structure['kind']](self, structure)

    def props(self):
        for encounter in self.spec['encounters']:
            for a in encounter['actors']:
                actor(self, a['kind'], a['pos'], '!' + encounter['id'])
        pos = self.spec['nodes'][0]['pos']
        actor(self, 'player', [pos[0] + 3, pos[1], pos[2]], player=True)


class StrongholdScene(CombatScene):
    def cuts(self, x, z, h):
        for platform in self.spec['terrain']['platforms']:
            px, pz, w, d = platform['rect']
            if px <= x <= px + w and pz <= z <= pz + d:
                h = platform['y'] - .7
        # 装饰与寨墙不承担整地；只对主路和建筑地基塑形。
        distance, feet, _ = self.walk_level(x, z)
        if distance < 4.6:
            h = min(h, feet - 1.2)
        for s in self.spec['structures']:
            if s['kind'] == 'combat_house' and abs(x - s['pos'][0]) < s['size'][0] / 2 + .5 and abs(z - s['pos'][2]) < s['size'][2] / 2 + .5:
                h = min(h, s['pos'][1] - 1.5)
        return max(1, h), []

    def terrain(self):
        t = self.spec['terrain']
        cell = t['surface_cell']
        width, _, depth = self.spec['size']
        for x in range(0, width, cell):
            for z in range(0, depth, cell):
                h, _ = self.cuts(x + cell / 2, z + cell / 2, self.natural(x + cell / 2, z + cell / 2))
                self.surface[x, z] = h
                mat = t['cover']
                for p in t['platforms']:
                    px, pz, w, d = p['rect']
                    if px <= x + cell / 2 <= px + w and pz <= z + cell / 2 <= pz + d:
                        mat = p.get('cover', mat)
                self.box(x, 0, z, cell, h, cell, t['rock'], 'geology')
                self.box(x, h, z, cell, .7, cell, mat, 'ground')

    def path_tile_allowed(self, x, z, feet):
        return not any(s['kind'] == 'combat_house' and abs(feet - s['pos'][1]) < .1
                       and abs(x - s['pos'][0]) < s['size'][0] / 2 - .8
                       and abs(z - s['pos'][2]) < s['size'][2] / 2 - .8
                       for s in self.spec['structures'])

    def paths_geometry(self):
        super().paths_geometry()
        # 整段台阶填实到地形，避免台地之间留下悬浮薄板。
        for b in self.boxes.copy():
            if b[7] == 'path':
                cell = self.spec['terrain']['surface_cell']
                low = min(b[1], self.surface.get((math.floor(b[0] / cell) * cell, math.floor(b[2] / cell) * cell), b[1]))
                if b[1] > low:
                    self.box(b[0], low, b[2], b[3], b[1] - low, b[5], 'rock_dark', 'foundation')


def merge_spans(spans):
    result = []
    for low, high in sorted(spans):
        if result and low <= result[-1][1]:
            result[-1] = (result[-1][0], max(high, result[-1][1]))
        else:
            result.append((low, high))
    return result


class DenScene(CombatScene):
    def voids(self, x, z):
        terrain = self.spec['terrain']
        spans, floor_candidates, nearest = [], [], (float('inf'), 14)
        skylight = False
        for room in terrain['rooms']:
            cx, y, cz = room['center']
            rx, rz = room['radius']
            angle = math.atan2((z - cz) / rz, (x - cx) / rx)
            irregularity = 1 + .065 * math.sin(angle * 5) + .035 * math.cos(angle * 9)
            q = (((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2) / irregularity ** 2
            distance = (math.sqrt(q) - 1) * min(rx, rz)
            if distance < nearest[0]:
                nearest = distance, y
            if q < 1:
                roof = y + max(8, room['height'] * math.sqrt(1 - q * .78))
                if room.get('skylight') and math.hypot(x - cx, z - cz) < room['skylight']:
                    roof = self.spec['size'][1] + 1
                    skylight = True
                spans.append((y, roof))
                floor_candidates.append(y)
        closest = (float('inf'), 0)
        for a, b, edge in self.segments:
            distance, feet = distance_segment(x, z, a, b)
            if distance < closest[0]:
                closest = distance, feet
            if distance - terrain['passage_radius'] < nearest[0]:
                nearest = distance - terrain['passage_radius'], feet
            if distance < terrain['passage_radius']:
                top = self.spec['size'][1] + 1 if edge['kind'] != 'tunnel' else feet + terrain['passage_height'] + 1.5 * math.cos(distance / terrain['passage_radius'] * math.pi / 2)
                spans.append((math.floor(feet * 2) / 2, top))
                floor_candidates.append(feet)
        spans = merge_spans(spans)
        if spans and closest[0] < 3.8:
            feet = self.walk_level(x, z)[1]
            spans[0] = (feet - .5, max(spans[0][1], feet + terrain['passage_height']))
        return spans, nearest[1], skylight, nearest[0]

    def terrain(self):
        t = self.spec['terrain']
        cell = t['cave_cell']
        w, _, d = self.spec['size']
        self.cave_cells = 0
        self.floor_surface = {}
        for x in range(0, w, cell):
            for z in range(0, d, cell):
                cx, cz = x + cell / 2, z + cell / 2
                h = self.natural(cx, cz)
                spans, nearby_floor, skylight, wall_distance = self.voids(cx, cz)
                self.surface[x, z] = h
                if not spans:
                    section_top = min(h, nearby_floor + t['section_wall_height'] if wall_distance < t['section_wall_band'] else t['section_base_y'])
                    self.box(x, 0, z, cell, section_top, cell, 'cave_rock', 'geology')
                    self.box(x, section_top, z, cell, h - section_top, cell, 'cave_rock', 'cave_roof')
                    self.floor_surface[x, z] = section_top
                else:
                    self.cave_cells += 1
                    floor, ceiling = spans[0]
                    self.floor_surface[x, z] = floor
                    self.box(x, 0, z, cell, floor - .25, cell, 'cave_rock', 'geology')
                    self.box(x, floor - .25, z, cell, .25, cell, 'cave_tuff' if math.sin(x * .1) + math.cos(z * .13) > .5 else 'cave_floor', 'cave_floor')
                    ceiling = math.floor(ceiling)
                    if ceiling < h:
                        self.box(x, ceiling, z, cell, h - ceiling, cell, 'cave_rock', 'cave_roof')
                        if (x * 7 + z * 3) % 59 == 0:
                            self.box(x, ceiling - 1.5, z, cell, 1.5, cell, 'cave_tuff', 'cave_roof')
                if not skylight and (not spans or spans[0][1] < h):
                    self.box(x, h, z, cell, .6, cell, 'forest_grass', 'cave_roof')
        for pool in t['pools']:
            x, z, ww, dd = pool['rect']
            self.box(x, pool['y'] - .3, z, ww, .3, dd, 'water', 'water')

    def paths_geometry(self):
        super().paths_geometry()
        cell = self.spec['terrain']['cave_cell']
        for b in self.boxes.copy():
            if b[7] != 'path':
                continue
            zz = math.floor(b[2] / cell) * cell
            lows = [self.floor_surface.get((xx, zz), b[1])
                    for xx in range(math.floor(b[0] / cell) * cell, math.ceil((b[0] + b[3]) / cell) * cell, cell)]
            low = min([b[1], *lows])
            if low < b[1]:
                self.box(b[0], low, b[2], b[3], b[1] - low, b[5], 'cave_floor', 'foundation')

    def props(self):
        super().props()
        for room in self.spec['terrain']['rooms']:
            x, y, z = room['center']
            rx, rz = room['radius']
            for j in range(12):
                angle = j * math.tau / 12
                xx, zz = x + math.cos(angle) * rx * .88, z + math.sin(angle) * rz * .88
                if self.close_to_route(xx, zz, 6):
                    continue
                self.box(xx, y + .1, zz, 2, .3, 2, 'moss', 'prop')
                if j % 3 == 0:
                    self.box(xx, y + 2, zz, .5, max(2, room['height'] / 3), .5, 'bark', 'cave_roof')


SCENES = {'bandit_stronghold': StrongholdScene, 'beast_den': DenScene}
