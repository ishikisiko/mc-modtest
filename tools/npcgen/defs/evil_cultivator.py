"""邪修沿用男修长衫和骨架，面具几何、裁出的眼缝及配色来自已确认的预览。"""

import math
from collections import Counter

from .. import male

ID = "yuanxian:evil_cultivator"
ENTITY = "evil_cultivator"
NAME = "evil_cultivator"
LOOK = "default"
FIXED_PALETTE = True
HITBOX = (0.6, 1.9)
BASE = "cultivator_m_05_05_a"
definition = male.definition(BASE)

MASK_RES = 36
RIM_PIXEL = (40, 2)
COLUMNS = (-1, -0.70, -0.23, 0, 0.23, 0.70, 1)
# Height, half-width and ridge depth of the faceplate, in the stock head's model units.
PROFILE = (
    (-13.10, 6.08, .48), (-11.76, 6.82, .74), (-8.45, 7.12, .87),
    (-6.08, 7.12, 1.04), (-5.20, 7.12, 1.10), (-4.32, 7.05, 1.27),
    (-2.80, 6.98, 1.12), (-1.25, 6.55, .92), (.14, 5.34, .55),
)
EYE_ROWS = 4, 5
EYE_COLUMNS = 1, 4


def build_model():
    model = definition.build_model()
    model.id = ID
    return model


def clips(model):
    return definition.clips(model)


def clamp(value):
    return max(0, min(255, round(value)))


def hash_pixel(x, y, salt):
    value = ((x + 37) * 374761393 + (y + 11) * 668265263 + salt * 99173) & 0xffffffff
    value = ((value ^ (value >> 13)) * 1274126177) & 0xffffffff
    return ((value ^ (value >> 16)) & 255) / 255


def painter(model):
    base = definition.painter(model)

    def draw(texel):
        color = base(texel)
        if color is None:
            return None
        if len(color) == 3:
            color = (*color, 255)
        return color_look(texel, color)

    return draw

def rgba(color, shade):
    return (*(clamp(channel + shade) for channel in color), 255)


def is_eye_x(x):
    return 1.5 < abs(x) < 5.1


def color_look(texel, source):
    if source[3] == 0:
        return source
    name = texel.cube
    r, g, b, _ = source
    luminance = (r * 2 + g * 5 + b) / 8
    x, y, _ = texel.local
    grain = (hash_pixel(texel.u, texel.v, 12) - .5) * 5

    if name == "headstudy_skull":
        # Cloth under the plate conceals the skin at its sides and preserves the eye line through the slits.
        if texel.face == "NORTH" and -5.7 < y < -4.0 and is_eye_x(x):
            shade = 12 if b > r * 1.2 and luminance > 80 else 0
            return rgba((30, 29, 33), shade + grain)
        if r < g < b and r < 90:
            return rgba((33, 33, 42), .32 * (luminance - 39))
        return rgba((27, 24, 28), grain)
    if name.startswith("headstudy_"):
        if name.endswith(("band", "clasp")):
            return rgba((63, 38, 42), .23 * (luminance - 49))
        return rgba((32, 32, 41), .57 * (luminance - 39))
    if name == "male_neck":
        return rgba((32, 27, 31), grain)

    if name.startswith("male_lapel_") or (name in {"male_torso", "male_jacket"} and r > g > b and r > 185):
        base = (112, 34, 44)
        shade = .24 * (luminance - 218)
        if texel.fu > .76 and "lapel" in name:
            shade += 14
    elif name.startswith(("male_belt", "male_side_tie", "male_cuff_")):
        base = (96, 28, 37) if "belt" not in name else (113, 33, 43)
        shade = -8 if texel.fv > .82 else 5 if texel.fv < .21 else 0
    elif name in {"male_buckle", "male_jade"}:
        base, shade = (83, 66, 59), 14 if texel.face == "DOWN" else 0
    elif name.startswith("male_robe_") and texel.height < 7.5:
        base = (101, 31, 39)
        shade = 8 if texel.height > 6.6 else -7 if texel.height < 4.4 else 0
    elif name.startswith(("male_robe_", "male_torso", "male_jacket", "male_sleeve_", "male_inner_")):
        base = (30, 27, 33) if "inner_" in name else (36, 32, 37)
        ridge = math.sin(texel.p[0] * .66 + texel.p[2] * .37 + math.sin(texel.p[1] * .24))
        shade = .48 * (luminance - 118) + ridge * 4
        if texel.face in {"NORTH", "SOUTH", "EAST", "WEST"} and texel.fu > .91:
            shade -= 6
    elif name.startswith(("boot_", "hand_")):
        if name.startswith("hand_"):
            base, shade = (44, 38, 44), .18 * (luminance - 199)
            if 12.9 < y < 13.9 and texel.face not in {"UP", "DOWN"}:
                shade += 5
        else:
            base, shade = (36, 32, 36), .32 * (luminance - 49)
    else:
        return source
    return rgba(base, shade + grain)


def mask_ink(i, j):
    x = (i + .5) * 14.8 / MASK_RES - 7.4
    y = (j + .5) * 14.0 / MASK_RES - 13.4
    noise = (hash_pixel(i, j, 43) - .5) * 6
    band = abs(x) / 7.4
    shade = 4 * math.cos(x * .95 + y * .09) - 8 * band + noise
    base = (65, 62, 67)
    # The raised brow catches the light. Two low-contrast edges at the jaw suggest worked iron.
    if -6.25 < y < -5.89 and 1.7 < abs(x) < 6.5:
        shade += 11
    if -.65 < y < -.26 and abs(x) < 4.65:
        shade += 9
    if -11.5 < y < -8.6 and abs(x) < .48:
        base, shade = (100, 43, 45), shade - 2
    if band > .82:
        shade += 8
    return rgba(base, shade)


def faceplate():
    front = []
    for j, (y, width, ridge) in enumerate(PROFILE):
        row = []
        for location in COLUMNS:
            x = location * width
            angle = .25 * (1 - abs(location)) if j in EYE_ROWS else 0
            z = -6.90 - ridge * (1 - abs(location)**1.35)
            row.append((x, y + angle, z))
        front.append(row)

    panel = []
    boundary: Counter = Counter()
    directions = {}

    def back(point):
        return point[0], point[1], point[2] + .39

    for row in range(len(front) - 1):
        for column in range(len(COLUMNS) - 1):
            if row == EYE_ROWS[0] and column in EYE_COLUMNS:
                continue
            a, b = front[row][column:column + 2]
            d, c = front[row + 1][column:column + 2]
            quad = (a, d, c, b)
            panel.append((quad, True))
            panel.append((tuple(back(point) for point in reversed(quad)), False))
            for u, v in zip(quad, quad[1:] + quad[:1]):
                edge = tuple(sorted((u, v)))
                boundary[edge] += 1
                directions[edge] = (u, v)
    for edge, count in boundary.items():
        if count == 1:
            u, v = directions[edge]
            panel.append(((v, u, back(u), back(v)), False))
    return panel


def surface(model):
    width, base_height = model.texture_size
    height = 1 << (base_height + MASK_RES + 3).bit_length()
    model.texture_size = width, height

    def vertex(point, front):
        x, y, z = point
        if front:
            u = 2.5 + (x + 7.4) * (MASK_RES - 1) / 14.8
            v = 2.5 + (y + 13.4) * (MASK_RES - 1) / 14.0
        else:
            u, v = (value + .5 for value in RIM_PIXEL)
        return [round(value, 7) for value in (x, y, z, u / width, (base_height + v) / height)]

    triangles = []
    for (a, b, c, d), front in faceplate():
        for triangle in ((a, b, c), (a, c, d)):
            triangles.append([value for point in triangle for value in vertex(point, front)])
    return {"schema": 1, "id": ID, "parts": [{"bone": "head", "triangles": triangles}]}


def authored_texture(model, rows):
    base_height = model.texture_size[1] // 2
    for y in range(MASK_RES):
        for x in range(MASK_RES):
            rows[base_height + 2 + y][2 + x] = mask_ink(x, y)
    u, v = RIM_PIXEL
    rows[base_height + v][u] = (56, 53, 58, 255)
    return rows
