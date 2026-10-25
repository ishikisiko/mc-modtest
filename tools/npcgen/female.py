"""保留女修方案的参数化定义；每次构建隔离调色板，避免换色探针串色。"""
from __future__ import annotations

import importlib.util
import math

from . import catalog
from .defs import cultivator_f_novice as legacy
from .humanoid import _box, _mix, _rgb, _tone, _xhz
from ..beastgen.builder import Builder
from ..beastgen.paint import ramp

CONFIG = catalog.load()
PANTS = ramp('#1B292F', '#2B3B43', '#3E525C', '#576D75', '#7B9093')
CHEST = CONFIG['chest']
UPPER_DROP = math.sqrt(CHEST['upper_length'] ** 2 - CHEST['projection'] ** 2)
LOWER_DROP = math.sqrt(CHEST['lower_length'] ** 2 - CHEST['projection'] ** 2)
CHEST_BOTTOM = CHEST['top'] - UPPER_DROP - CHEST['middle_length'] - LOWER_DROP
OLD_FRINGE = ('hair_fringe_right', 'hair_fringe_inner_right', 'hair_fringe_middle',
              'hair_fringe_inner_left', 'hair_fringe_left')
FEATURES = set('LWCDGPM')

def _face(spec):
    loader = importlib.util.spec_from_file_location('tools.npcgen.defs._female_variant', legacy.__file__)
    module = importlib.util.module_from_spec(loader)
    loader.loader.exec_module(module)
    module.IRIS_TOP, module.IRIS, module.IRIS_LOW = '#326D64', '#91CDC8', '#C1E8E0'

    class CandidatePaint(module._Paint):
        def face(self, t):
            x, h, z = _xhz(t)
            px, py = int(x + 7), int(60 - h)
            symbol = spec['pixels'][py][px]
            if symbol == 'H':
                return _tone(module.HAIR, module._hair_head_tone(x, h, z, 'NORTH'))
            if symbol == 'L':
                return _rgb(module.LASH)
            if symbol == 'W':
                return _rgb(module.EYE_WHITE)
            if symbol == 'C':
                return _rgb(module.IRIS)
            if symbol == 'D':
                return _rgb(module.IRIS_TOP)
            if symbol == 'G':
                return _mix(_rgb(module.IRIS_LOW), _rgb(module.EYE_WHITE), .5)
            if symbol == 'P':
                return _rgb('#F1CFC5')
            if symbol == 'M':
                return _rgb('#F5DFD5')
            return _tone(module.SKIN, 5.5)

    module.painter = CandidatePaint
    return module

def _hair(module, style):
    original_model, original_painter = module.build_model, module.painter
    pieces = {}

    def model():
        result = original_model()
        if style == 'A':
            return result
        # Keep existing bone interfaces and animation channels; replace only hair cubes.
        for name in ('hair_back', 'ribbon_right', 'ribbon_left'):
            result.bone(name).cubes.clear()
        head = result.bone('head')
        head.cubes[:] = [c for c in head.cubes if c.name not in ('hair_temple_right', 'hair_temple_left')]
        b = Builder(module.ID)
        b.model = result

        def local(bone, name, origin, size, material='hair'):
            pieces[name] = material
            b.box_local(bone, name, origin, size)

        def absolute(name, x, h, z, w, height, d, material='hair'):
            pieces[name] = material
            _box(b, 'head', name, x, h, z, w, height, d)

        if style == 'B':
            b.bone('study_high_tail', 'head', at=(0, -58, 8), rot=(12, 0, 0))
            local('study_high_tail', 'study_pony_root', (-2, -2, -1), (4, 5, 4))
            local('study_high_tail', 'study_pony_main', (-3, 2, 1), (6, 12, 4))
            local('study_high_tail', 'study_pony_mid', (-2.5, 13, 1.5), (5, 8, 3))
            local('study_high_tail', 'study_pony_tip', (-1.5, 20, 1.75), (3, 5, 2))
            local('study_high_tail', 'study_pony_band', (-2.5, 0, -1.5), (5, 2, 5), 'ribbon')
            local('study_high_tail', 'study_pony_clasp', (-1, .5, 3.5), (2, 1, 1), 'gold')
            absolute('study_pony_wisp_right', -7.5, 44, -7.6, 1, 10, 2)
            absolute('study_pony_wisp_left', 6.5, 46, -7.6, 1, 8, 2)
        elif style == 'C':
            b.bone('study_loose_hair', 'head', at=(0, -53, 7.5), rot=(5, 0, 0))
            local('study_loose_hair', 'study_loose_middle', (-3, 0, 0), (6, 22, 2))
            local('study_loose_hair', 'study_loose_right', (-7, 0, 0), (4, 19, 2))
            local('study_loose_hair', 'study_loose_left', (3, 0, 0), (4, 20, 2))
            local('study_loose_hair', 'study_loose_tip_right', (-6, 18, .2), (3, 3, 2))
            local('study_loose_hair', 'study_loose_tip_left', (3, 19, .2), (3, 3, 2))
            absolute('study_half_knot', -2.5, 53, 7.2, 5, 5, 4)
            absolute('study_half_band', -3, 54, 10.5, 6, 2, 1, 'ribbon')
            absolute('study_half_clasp', -1, 54.5, 11.3, 2, 1, 1, 'gold')
            absolute('study_half_wisp_right', -7.5, 41, -7.5, 2, 13, 2)
            absolute('study_half_wisp_left', 5.5, 43, -7.5, 2, 11, 2)
            absolute('study_half_wisp_tip_right', -7, 38, -7.3, 1, 4, 1)
            absolute('study_half_wisp_tip_left', 6, 40, -7.3, 1, 4, 1)
        elif style == 'D':
            b.bone('study_low_bun', 'head', at=(0, -53, 8))
            local('study_low_bun', 'study_bun_middle', (-3, -2, -.5), (6, 6, 4))
            local('study_low_bun', 'study_bun_right', (-4, -1, -.5), (1, 4, 3))
            local('study_low_bun', 'study_bun_left', (3, -1, -.5), (1, 4, 3))
            local('study_low_bun', 'study_bun_back', (-2, -1, 3.5), (4, 4, 1))
            local('study_low_bun', 'study_bun_band', (-3.5, 1, 3.6), (7, 1, 1), 'ribbon')
            local('study_low_bun', 'study_bun_pin', (-6, -1, 2), (12, 1, 1), 'gold')
            local('study_low_bun', 'study_bun_pin_end', (5, -1.5, 1.5), (2, 2, 2), 'ribbon')
            absolute('study_bun_wisp_right', -7.5, 48, -7.5, 1, 6, 2)
            absolute('study_bun_wisp_left', 6.5, 48, -7.5, 1, 6, 2)
        else:
            raise ValueError(style)
        return result

    class HairPaint(original_painter):
        def __init__(self, model):
            super().__init__(model)
            for name in pieces:
                self.by_cube[name] = self.added_hair

        def added_hair(self, t):
            material = pieces[t.cube]
            if material == 'ribbon':
                return _tone(module.RIBBON, 3)
            if material == 'gold':
                return _tone(module.EDGE, 3)
            if t.face in ('DOWN', 'UP'):
                return _tone(module.HAIR, 2.5 if t.face == 'DOWN' else 1.5)
            col = min(4, int(t.fu * 5))
            tone = (2.5, 3.0, 4.0, 3.5, 2.5)[col]
            if t.local[1] > t.box_hi[1] - 3:
                tone -= .5
            return _tone(module.HAIR, tone)

    module.build_model = model
    module.painter = HairPaint
    return module

def _fringe(module, style, face):
    if style == 'A':
        return module
    original_model, original_painter = module.build_model, module.painter
    spec = {key: list(value) for key, value in CONFIG['hair'][style]['fringe'].items()}
    # 三号脸的眼线高一格：按实际五官留出刘海边界，不能遮住睫毛。
    for x in range(14):
        features = [y for y, row in enumerate(face['pixels']) if row[x] in FEATURES]
        if features:
            spec['depth'][x] = min(spec['depth'][x], min(features))
    pieces = set()

    def model():
        result = original_model()
        head = result.bone('head')
        head.cubes[:] = [c for c in head.cubes if c.name not in OLD_FRINGE]
        b = Builder(module.ID)
        b.model = result
        start = 1
        while start < 13:
            end = start + 1
            while end < 13 and spec['depth'][end] == spec['depth'][start]:
                end += 1
            depth, name = spec['depth'][start], 'fringe_study_' + str(start)
            _box(b, 'head', name, start - 7, 60 - depth, -7.6, end - start, depth, 1)
            pieces.add(name)
            start = end
        return result

    def tone(x, h):
        px, py = min(13, max(0, int(x + 7))), min(13, max(0, int(60 - h)))
        offset = (py // 4 - 1) if style == 'B' else 0
        col = min(13, max(0, px + offset))
        value = spec['tones'][col]
        if py < 3:
            value += .5
        if py >= spec['depth'][px] - 2:
            value -= .5
        return value

    class FringePaint(original_painter):
        def __init__(self, model):
            super().__init__(model)
            for name in pieces:
                self.by_cube[name] = self.fringe

        def face(self, t):
            x, h, _ = _xhz(t)
            px, py = int(x + 7), int(60 - h)
            if face['pixels'][py][px] in FEATURES or py >= 10:
                return super().face(t)
            if py < spec['depth'][px]:
                return _tone(module.HAIR, tone(x, h))
            return _tone(module.SKIN, 5.5)

        def fringe(self, t):
            x, h, _ = _xhz(t)
            return _tone(module.HAIR, 1.5 if t.face == 'UP' else tone(x, h))

    module.build_model, module.painter = model, FringePaint
    return module

def _outfit(module, kind, motion_cover=True):
    module.RU = module.JACKET_CLOTH = module.SKIRT = ramp(*CONFIG['palettes']['ink_teal'])
    module.INNER = ramp(*CONFIG['palettes']['ivory'])
    module.SASH = ramp('#183539', '#284D50', '#3A6765', '#578983', '#88B1A1')
    module.EDGE = ramp(*CONFIG['palettes']['gold'])
    module.CHEST = module.JACKET = (31.5, 45.5)
    module.BAND = (31, 34)
    module.SKIRT_WAIST, module.SKIRT_LENGTH = 32, 30
    module.SKIRT_FLARE_X, module.SKIRT_FLARE_Z = 2.0, 2.0
    original_model, original_painter = module.build_model, module.painter
    extras = {}

    def model():
        result = original_model()
        b = Builder(module.ID)
        b.model = result

        def change(name, size, origin=None):
            cube = result.cube(name)[1]
            cube.size = tuple(size)
            if origin is not None:
                cube.origin = tuple(origin)

        def remove(names):
            for bone in result.bones:
                bone.cubes[:] = [c for c in bone.cubes if c.name not in names]

        remove({'short_sleeve_right', 'short_sleeve_left'})
        jacket = result.cube('jacket')[1]
        change('jacket', (15, 14, 9), (-7.5, jacket.origin[1], jacket.origin[2]))
        change('collar_over_band', (2, 10, 1), (-1, -.5, -.5))
        change('collar_under_band', (2, 6, 1), (-1, -.5, -.5))
        band = result.cube('band')[1]
        change('band', (14, 2, 10), (-7, band.origin[1] + 1, band.origin[2]))
        knot = result.cube('knot')[1]
        change('knot', (2, 1, 1), (-1, knot.origin[1] - 2.5, knot.origin[2]))
        for side in ('right', 'left'):
            inward_origin = -2.25 if side == 'right' else -3.75
            change('sleeve_upper_' + side, (6, 9, 6), (inward_origin, 0, -3))
            change('sleeve_lower_' + side, (6, 8, 6), (inward_origin, -.5, -3))
            change('cuff_' + side, (6, 2, 6), (inward_origin, 6, -3))
            result.cube('cuff_' + side)[1].inflate = 0
            result.bone('tie_' + side).cubes.clear()
        _box(b, 'body', 'outfit_small_side_tie', 5, 26.5, -5.8, 1, 5, 1)
        extras['outfit_small_side_tie'] = 'tie'
        if kind == 'pants':
            for bone in result.bones:
                if bone.name in ('skirt_front', 'skirt_back', 'skirt_right', 'skirt_left',
                                 'skirt_corner_right_front', 'skirt_corner_right_back',
                                 'skirt_corner_left_front', 'skirt_corner_left_back'):
                    bone.cubes.clear()
            _box(b, 'body', 'trouser_waist', -7, 24, -4, 14, 8, 8)
            extras['trouser_waist'] = 'pants'
            for side in ('right', 'left'):
                # 腰块跟随 body，腿跟随 root；内藏搭接覆盖两套骨骼摆动的接缝。
                overlap = 2 if motion_cover else 0
                change('leg_inner_' + side, (6, 13 + overlap, 6), (-3, -overlap, -3))
                name = 'trouser_calf_' + side
                b.box_local('leg_' + side, name, (-2.5, 12, -2.5), (5, 9, 5))
                extras[name] = 'pants'
                shoe = result.cube('shoe_' + side)[1]
                change('shoe_' + side, (5, 3, 8), (shoe.origin[0], shoe.origin[1], -5))
        return result

    class Paint(original_painter):
        def __init__(self, model):
            super().__init__(model)
            for name in extras:
                self.by_cube[name] = self.extra

        def jacket(self, t):
            x, h, z = _xhz(t)
            if t.face == 'NORTH' and h > 37.5 and abs(x) < (h - 37.5) * .35:
                return None
            if t.face == 'DOWN' and abs(x) < 3.5 and abs(z) < 3:
                return None
            return _tone(module.JACKET_CLOTH, 3.0 if abs(x) < 4.5 else 2.5)

        def ru(self, t):
            if motion_cover and kind == 'robe' and t.cube == 'leg_inner_right':
                # 长衣内的腿只用于步态/支撑脚解算；不让内藏裤腿穿出连续裙片。
                return None
            if kind == 'pants' and t.cube == 'leg_inner_right':
                return self.pants(t)
            if t.cube == 'torso':
                if t.face == 'NORTH' and _xhz(t)[1] > 37:
                    return _tone(module.INNER, 3)
                return _tone(module.RU, 3)
            return _tone(module.RU, 3 if t.face in ('NORTH', 'SOUTH') else 2.5)

        def cuff(self, t):
            return _tone(module.INNER, 2.5) if t.local[1] > 7 else _tone(module.RU, 2.5)

        def band(self, t):
            return _tone(module.SASH, 2.5)

        def skirt(self, t):
            colour = super().skirt(t)
            if colour is None:
                return None
            down = t.local[1]
            if down > module.SKIRT_LENGTH - 1:
                return _tone(module.INNER, 2.5)
            across = abs(t.local[0] if t.cube in ('skirt_front_panel', 'skirt_back_panel') else t.local[2])
            return _tone(module.SKIRT, 2.5 if 3 < across < 5 else 3)

        def skirt_colour(self, down, tone):
            return _tone(module.INNER, 2.5) if down > module.SKIRT_LENGTH - 1 else _tone(module.SKIRT, 2.5)

        def pants(self, t):
            h = _xhz(t)[1]
            if h < 7:
                return _tone(module.SHOE, 2)
            # Broad, quiet fabric shading, consistent over hip, thigh and calf.
            return _tone(PANTS, 3 if t.face in ('NORTH', 'SOUTH') else 2.5)

        def extra(self, t):
            return _tone(module.SASH, 3) if extras[t.cube] == 'tie' else self.pants(t)

    module.build_model, module.painter = model, Paint
    return module

def _chest(module):
    original_model, original_painter = module.build_model, module.painter
    chest_tones = {'jacket_chest_upper': 2.5, 'jacket_chest_middle': 3.0,
                   'jacket_chest_lower': 3.5}

    def model():
        result = original_model()
        b = Builder(module.ID)
        b.model = result
        # Three joined faces replace the jacket front, avoiding a separate raised chest plate.
        top, dz, z = CHEST['top'], CHEST['projection'], CHEST['front_z']
        sections = (
            ('upper', top, z, CHEST['upper_length'], -math.degrees(math.asin(dz / CHEST['upper_length']))),
            ('middle', top - UPPER_DROP, z - dz, CHEST['middle_length'], 0),
            ('lower', top - UPPER_DROP - CHEST['middle_length'], z - dz,
             CHEST['lower_length'], math.degrees(math.asin(dz / CHEST['lower_length']))),
        )
        for part, height, front, length, angle in sections:
            bone = 'chest_cloth_' + part
            b.bone(bone, 'body', at=(0, -height, front), rot=(angle, 0, 0))
            b.box_local(bone, 'jacket_chest_' + part, (-CHEST['width'] / 2, 0, 0),
                        (CHEST['width'], length, 1))
        # Keep both lapels continuous, with only the offset required to sit above the cloth.
        for name, forward in (('collar_over_band', .45), ('collar_under_band', .65)):
            c = result.cube(name)[1]
            c.origin = (c.origin[0], c.origin[1], c.origin[2] - forward)
        return result

    class Paint(original_painter):
        def __init__(self, model):
            super().__init__(model)
            for name in chest_tones:
                self.by_cube[name] = self.chest

        def jacket(self, t):
            if t.face == 'NORTH' and CHEST_BOTTOM <= _xhz(t)[1] <= CHEST['top']:
                return None
            return super().jacket(t)

        def chest(self, t):
            x, h, _ = _xhz(t)
            if t.face in ('DOWN', 'UP', 'SOUTH'):
                return None
            if h > 37.5 and abs(x) < (h - 37.5) * .35:
                return None
            tone = chest_tones[t.cube] - (.5 if abs(x) >= 4.5 else 0) if t.face == 'NORTH' else 2.5
            return _tone(module.JACKET_CLOTH, tone)

    module.build_model, module.painter = model, Paint
    return module

def definition(name, *, motion_cover=True):
    spec = next((v for v in CONFIG['variants'] if 'cultivator_' + v['look'] == name), None)
    if spec is None:
        raise ValueError(f'未知女修组合：{name}')
    face = CONFIG['faces'][spec['face']]
    module = _face(face)
    module = _hair(module, spec['hair'])
    module = _fringe(module, spec['hair'], face)
    module = _outfit(module, spec['outfit'], motion_cover)
    if CONFIG['outfits'][spec['outfit']]['chest_contour']:
        module = _chest(module)
    module.NAME, module.LOOK = name, spec['look']
    return module
