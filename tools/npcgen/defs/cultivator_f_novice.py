"""入门女修重做：MC 方头、低马尾、自然垂手与独立连续裙片。

面部使用原创 8×8 小横眼构图，发束与衣褶使用大像素色块；配色为靛蓝、象牙白与朱红。
当前为样稿落地候选，完整动作与原生游戏表现留待后续审美闸门。
保留实体、Look、scale 0.5、基础骨骼和碰撞接口。
模型、动画、贴图、角色材质图均由 npcgen 生成，不使用贴图后处理覆盖。
"""
from __future__ import annotations

import math

from ...beastgen.builder import Builder
from ...beastgen.paint import ramp
from ..humanoid import (HumanoidPaint, _box, _clamp, _form, _leg_angle, _loop,
                        _pair, _rgb, _rx, _sole_low, _tone, _xhz)

ID = "myvillage:cultivator"
ENTITY = "cultivator"
NAME = "cultivator_f_novice"
LOOK = "f_novice"
HITBOX = (0.6, 1.9)
SCALE = 0.5

# 一单位为 1/32 格。以参考皮肤的头肩关系校准：方头宽约为肩宽的一半。
HIP = 24.0
SHOULDER = 44.5
ARM_X = 11.0
NECK = 46.0
HEAD_TOP = 60.0
HEAD_SIZE = 14
CHEST = (27.5, 45.5)
JACKET = (28.5, 45.5)
BAND = (27.5, 30.5)
COLLAR_TOP = 45.0
OPENING = 4.0
HEM = 2.0

# 四片完整长裙通过实体薄片和二值裁切形成 A 字剪影，没有横向分层，也不跟随左右腿分开。
SKIRT_WAIST = 29.0
SKIRT_LENGTH = 27
SKIRT_FLARE_X = 8.0
SKIRT_FLARE_Z = 5.0
SKIRT_PANEL_CUBES = ("skirt_front_panel", "skirt_back_panel", "skirt_right_panel", "skirt_left_panel")
HOLLOW = ("jacket", "hair_side_right", "hair_side_left", *SKIRT_PANEL_CUBES)

INDIGO = ramp("#161F34", "#222E4A", "#304060", "#405273", "#52678A", "#687F9F", "#8499B4")
RU = JACKET_CLOTH = SKIRT = INDIGO
INNER = ramp("#BDA58A", "#D8C3A5", "#EFDEC1", "#FFF0D7", "#FFF8E6")
SASH = ramp("#541D2B", "#742B3C", "#963A4B", "#B44958", "#CF6070")
EDGE = ramp("#8A5C22", "#B58638", "#D5AC5E", "#F1D28F", "#FFEDBA")
SHOE = ramp("#181920", "#23252F", "#30333F", "#404452", "#525767")
SOLE = ramp("#A8A49A", "#CAC6BC", "#E8E4DA")
RIBBON = SASH
SKIN = ramp("#BA8A74", "#D6A690", "#ECC3AE", "#F8D8C2", "#FEE6DA", "#FFF0E8", "#FFF8F2")
HAIR = ramp("#0C101C", "#161D2C", "#243046", "#34445E", "#4B617D", "#6883A0", "#91AAC4")
EYE_WHITE = "#F8FAFF"
IRIS_TOP = "#5A2E1A"
IRIS = "#B2702F"
IRIS_LOW = "#F0C27A"
LASH = "#32252F"
LIP = "#EBD2C6"

# 从头顶向下、从 -X 向 +X；H 为发色，L 为短睫毛，W 为眼白，I 为单行虹膜。
# 只存索引，调色板在 painter 调用时读取，人物发色与瞳色才能进入 role map。
FACE_TEMPLATE = (
    "HHHHHHHH",
    "HHHHHHHH",
    "HHHHHHHH",
    "HHHHHHHH",
    ".LLHHLL.",
    ".WI..IW.",
    "...mm...",
    "........",
)

# 发束不是随机纹理：每格使用同一发色 ramp 的半阶索引，换色仍能逐纹素还原。
HAIR_TOP = (
    "44556654", "45677654", "46799864", "56765445",
    "66544567", "544567A7", "44568865", "44455544",
)
HAIR_FRONT = (
    "45566554", "45677654", "35676543", "35665443",
    "34554433", "34454433", "23443432", "23333332",
)
HAIR_SIDE = (
    "45555443", "45666443", "45677543", "44577643",
    "34467543", "34456433", "33445333", "33334333",
)
HAIR_BACK = (
    "45566554", "45677654", "34566543", "34455443",
    "34555443", "34444443", "33444333", "23333332",
)


def _grid(value, lo, span):
    return int(_clamp((value - lo) / span, 0.0, 1.0 - 1e-9) * 8)


def _hair_head_tone(x, h, z, face):
    if face == "DOWN":
        pattern, row, col = HAIR_TOP, _grid(z, -7, 14), _grid(x, -7, 14)
    elif face in ("EAST", "WEST"):
        pattern, row, col = HAIR_SIDE, _grid(HEAD_TOP - h, 0, 14), _grid(z, -7, 14)
    else:
        pattern = HAIR_FRONT if face == "NORTH" else HAIR_BACK
        row, col = _grid(HEAD_TOP - h, 0, 14), _grid(x, -7, 14)
    return int(pattern[row][col], 16) / 2


def face_cell(x, h):
    """8×8 构图最近邻映射到当前方头，偶数宽度下保持左右对称。"""
    # 14→8 的部分采样点恰在两格边界；从中心镜像处理平局，防止左右眼相差一列。
    half_col = int(_clamp((HEAD_SIZE / 2 - abs(x)) / HEAD_SIZE, 0.0, 0.5 - 1e-9) * 8)
    col = half_col if x < 0 else 7 - half_col
    row = int(_clamp((HEAD_TOP - h) / HEAD_SIZE, 0.0, 1.0 - 1e-9) * 8)
    return row, col


def build_model():
    b = Builder(ID, look={"bone": "head", "max_yaw": 60.0, "max_pitch": 35.0}, shadow_radius=0.4)
    b.model.scale = SCALE
    b.bone("body", "root", at=(0.0, -HIP, 0.0))
    _box(b, "body", "torso", -7, CHEST[0], -4, 14, CHEST[1] - CHEST[0], 8)
    _box(b, "body", "jacket", -8, JACKET[0], -4.5, 16, JACKET[1] - JACKET[0], 9)
    _box(b, "body", "band", -7.5, BAND[0], -5.25, 15, 3, 10)
    _box(b, "body", "neck", -3, NECK - 2.5, -2.5, 6, 3, 5)
    # 下层领止于交叉处；上层领连续落到腰封，避免两条等长带形成 X。
    b.bone("collar_over", "body", at=(3.0, -COLLAR_TOP, -4.65), rot=(0.0, 0.0, 25.0))
    b.box_local("collar_over", "collar_over_band", (-1.5, -0.5, -0.5), (3, 16, 1))
    b.bone("collar_under", "body", at=(-3.0, -COLLAR_TOP, -4.4), rot=(0.0, 0.0, -25.0))
    b.box_local("collar_under", "collar_under_band", (-1, -0.5, -0.5), (2, 8, 1))
    _box(b, "body", "knot", -1.5, 28, -6.25, 3, 2, 1)
    _pair(b, "tie_{}", "body", (-0.5, -29.0, -6.1), rot=(-2.0, 0.0, -2.0),
          boxes=[("tie_{}_band", (-1, 0, -0.5), (2, 8, 1))], local=True)

    b.bone("head", "body", at=(0.0, -NECK, 0.0))
    _box(b, "head", "skull", -HEAD_SIZE / 2, NECK, -HEAD_SIZE / 2,
         HEAD_SIZE, HEAD_SIZE, HEAD_SIZE)
    # 贴头薄发片；五束刘海错开末端，中央短束落到两眼之间。
    _box(b, "head", "hair_cap", -7, 60, -7, 14, 1, 14)
    _box(b, "head", "hair_back_head", -7, 47, 6.5, 14, 13, 1)
    _box(b, "head", "hair_side_right", -7.5, 47, -7, 1, 13, 14)
    _box(b, "head", "hair_side_left", 6.5, 47, -7, 1, 13, 14)
    _box(b, "head", "hair_fringe_right", -7, 53, -7.5, 3, 7, 1)
    _box(b, "head", "hair_fringe_inner_right", -4, 52, -7.5, 3, 8, 1)
    _box(b, "head", "hair_fringe_middle", -1, 51, -7.5, 2, 9, 1)
    _box(b, "head", "hair_fringe_inner_left", 1, 53, -7.5, 3, 7, 1)
    _box(b, "head", "hair_fringe_left", 4, 52, -7.5, 3, 8, 1)
    _box(b, "head", "hair_temple_right", -7.5, 47, -7.5, 2, 7, 4)
    _box(b, "head", "hair_temple_left", 5.5, 48, -7.5, 2, 6, 4)

    # 低马尾在后颈收束，再以两块宽发束收窄；保留运行时已知的 hair_back 骨。
    b.bone("hair_back", "body", at=(0.0, -50.5, 7.5), rot=(4.0, 0.0, 0.0))
    b.box_local("hair_back", "hair_back_root", (-2, -1, -1), (4, 4, 4))
    b.box_local("hair_back", "hair_back_main", (-3, 2.5, 1), (6, 12, 4))
    b.box_local("hair_back", "hair_back_tip", (-2, 14, 1), (4, 6, 3))
    b.box_local("hair_back", "ribbon", (-2.5, 0, -1.5), (5, 2, 5))
    _pair(b, "ribbon_{}", "hair_back", (-2.0, -49.5, 11), rot=(5.0, 0.0, -12.0),
          boxes=[("ribbon_{}_tail", (-1, 0, -0.5), (2, 6, 1))], local=True)

    # 接近 Alex 的肩臂宽度，手臂自然下垂；两手略有前后差，避免对称抱拳。
    for side, sign in (("right", -1), ("left", 1)):
        own = side == "right"
        def uv(name):
            return {} if own else {"mirror": True, "uv_from": name + "_right"}
        b.bone("arm_" + side, "body", at=(sign * ARM_X, -SHOULDER, 0),
               rot=(-2.0 if own else 1.0, 0.0, -3.0 * sign))
        b.box_local("arm_" + side, "sleeve_upper_" + side, (-3, 0, -3), (6, 9, 6), **uv("sleeve_upper"))
        b.box_local("arm_" + side, "short_sleeve_" + side, (-3.5, -0.25, -3.5), (7, 6, 7), **uv("short_sleeve"))
        b.bone("forearm_" + side, "arm_" + side, at_local=(0, 9, 0), rot=(-3.0 if own else 2.0, 0, 0))
        b.box_local("forearm_" + side, "sleeve_lower_" + side, (-3, -0.5, -3), (6, 8, 6), **uv("sleeve_lower"))
        b.box_local("forearm_" + side, "cuff_" + side, (-3, 5, -3), (6, 3, 6), inflate=0.15, **uv("cuff"))
        b.box_local("forearm_" + side, "hand_" + side, (-2, 6, -2), (4, 5, 4), **uv("hand"))

    _pair(b, "leg_{}", "root", (-3.5, -HIP, 0),
          boxes=[("leg_inner_{}", (-6, 3, -3), (5, 21, 6)),
                 ("shoe_{}", (-6, 0, -5), (5, 3, 8))])

    # 裙体独立于腿：前后片略向外倾，侧片展开。所有片从腰连续落到脚踝。
    b.bone("skirt_root", "body", at=(0, -SKIRT_WAIST, 0))
    for side, sign in (("front", -1), ("back", 1)):
        bone = "skirt_" + side
        b.bone(bone, "skirt_root", at_local=(0, 0, sign * 4.75), rot=(sign * SKIRT_FLARE_Z, 0, 0))
        b.box_local(bone, bone + "_panel", (-11, 0, -0.5), (22, SKIRT_LENGTH, 1))
    for side, sign in (("right", -1), ("left", 1)):
        bone = "skirt_" + side
        b.bone(bone, "skirt_root", at_local=(sign * 7, 0, 0), rot=(0, 0, -sign * SKIRT_FLARE_X))
        b.box_local(bone, bone + "_panel", (-0.5, 0, -8), (1, SKIRT_LENGTH, 16))
    # 四条同色实心转角褶包住二值裁切的片边，使角部形成连续竖褶。
    for side, sx in (("right", -1), ("left", 1)):
        for end, sz in (("front", -1), ("back", 1)):
            bone = f"skirt_corner_{side}_{end}"
            b.bone(bone, "skirt_root", at_local=(sx * 7, 0, sz * 4.75),
                   rot=(sz * SKIRT_FLARE_Z, 0, -sx * SKIRT_FLARE_X))
            b.box_local(bone, bone + "_fold", (-1, 0, -1), (2, SKIRT_LENGTH, 2))
    return b.model


class _Paint(HumanoidPaint):
    """分束像素头发、单行眼睛与连贯衣褶；发色、瞳色继续由角色材质图驱动。"""
    SKIN = SKIN
    HAIR = HAIR
    FINGER_ROW = 9.0

    def __init__(self, model):
        mapping = {"skull": self.skull, "torso": self.ru, "jacket": self.jacket,
                   "band": self.band, "neck": self.neck,
                   "collar_over_band": self.collar, "collar_under_band": self.collar,
                   "knot": self.clasp, "tie_right_band": self.band,
                   "sleeve_upper_right": self.ru, "short_sleeve_right": self.short_sleeve,
                   "sleeve_lower_right": self.ru, "cuff_right": self.cuff,
                   "hand_right": self.hand, "leg_inner_right": self.ru, "shoe_right": self.shoe,
                   "ribbon": self.ribbon, "ribbon_right_tail": self.ribbon}
        for name in ("hair_cap", "hair_back_head", "hair_side_right", "hair_side_left",
                     "hair_fringe_right", "hair_fringe_middle", "hair_fringe_left",
                     "hair_fringe_inner_right", "hair_fringe_inner_left",
                     "hair_temple_right", "hair_temple_left", "hair_back_root", "hair_back_main", "hair_back_tip"):
            mapping[name] = self.hair
        for name in SKIRT_PANEL_CUBES:
            mapping[name] = self.skirt
        for side in ("right", "left"):
            for end in ("front", "back"):
                mapping[f"skirt_corner_{side}_{end}_fold"] = self.skirt_corner
        super().__init__(model, HOLLOW, mapping)

    def face(self, t):
        x, h, z = _xhz(t)
        row, col = face_cell(x, h)
        symbol = FACE_TEMPLATE[row][col]
        py = int(HEAD_TOP - h)
        if symbol == "H":
            return _tone(HAIR, _hair_head_tone(x, h, z, "NORTH"))
        if symbol == "L":
            return _rgb(LASH) if py == 8 else _tone(HAIR, _hair_head_tone(x, h, z, "NORTH"))
        if symbol == "W":
            return _rgb(EYE_WHITE)
        if symbol == "I":
            # 只在一行中分内深、外浅；不再叠三行渐变或添加大高光。
            return _rgb(IRIS_TOP if abs(x) < 3 else IRIS)
        if symbol == "m":
            # 两格构图落到 14 宽头面后，嘴线只取中间两纹素与一行，避免变成大色块。
            if abs(x) < 1 and py == 11:
                return _rgb(LIP)
        return _tone(SKIN, 5.5)

    def skull(self, t):
        if t.face == "NORTH":
            return self.face(t)
        if t.face in ("WEST", "EAST", "UP"):
            return _tone(SKIN, 5.0 if t.face != "UP" else 4.0)
        return _tone(HAIR, 2.0)

    def hair(self, t):
        x, h, z = _xhz(t)
        if t.cube in ("hair_side_right", "hair_side_left"):
            if t.face in ("EAST", "WEST") and -4 < z < 0 and 47 < h < 50:
                return None  # 小块耳侧留白，不再露出整条横向肤色带。
        if t.cube in ("hair_back_root", "hair_back_main", "hair_back_tip"):
            across = t.local[0] if t.face in ("NORTH", "SOUTH") else t.local[2] - 2.5
            tone = 2.0 if across < -1 else 3.0 if across < 1 else 2.5
            if t.local[1] > 10 and across > 1:
                tone -= 0.5
            return _tone(HAIR, tone)
        return _tone(HAIR, _hair_head_tone(x, h, z, t.face))

    def neck(self, t):
        return _tone(SKIN, 4.5)

    def hand(self, t):
        return _tone(SKIN, 4.8 + _form(t, 0.25))

    def ru(self, t):
        x, h, _ = _xhz(t)
        if t.cube == "torso":
            if t.face == "NORTH" and h > 38 and abs(x) < (h - 38) * 0.45:
                return _tone(INNER, 3.0)
            return _tone(RU, 3.0 if abs(x) > 2 else 2.5)
        across = t.local[0] if t.face in ("NORTH", "SOUTH") else t.local[2]
        tone = (2.5, 3.0, 3.5, 3.5, 3.0, 2.5)[int(_clamp(across + 3, 0, 5))]
        return _tone(RU, tone)

    def jacket(self, t):
        x, h, z = _xhz(t)
        if t.face == "NORTH" and abs(x) < OPENING:
            return None
        if t.face == "DOWN" and abs(x) < 3.5 and abs(z) < 3.0:
            return None
        fold = 0.5 if 4 < abs(x) < 6 and h > 33 else 0.0
        if t.face == "SOUTH" and abs(x) < 1:
            fold = -0.5
        return _tone(JACKET_CLOTH, 3.0 + fold)

    def collar(self, t):
        return _tone(INNER, 3.5 if t.cube == "collar_over_band" else 2.5)

    def short_sleeve(self, t):
        accent = 0.5 if -1 < t.local[0] < 2 and t.local[1] < 4 else 0
        return _tone(JACKET_CLOTH, 3.0 + accent)

    def cuff(self, t):
        if t.local[1] < 6:
            return _tone(EDGE, 3.0)
        return _tone(INNER, 3.5)

    def clasp(self, t):
        return _tone(EDGE, 3.0 if t.face == "NORTH" else 2.0)

    def band(self, t):
        if t.cube == "band":
            return _tone(SASH, 3.5 if _xhz(t)[1] > 29.5 else 2.5)
        return _tone(SASH, 3.0 if t.local[0] < 0.5 else 2.5)

    def ribbon(self, t):
        return _tone(RIBBON, 3.0)

    def skirt(self, t):
        lx, ly, lz = t.local
        front = t.cube in ("skirt_front_panel", "skirt_back_panel")
        # 薄片表面的梯形裁切是二值像素轮廓；四片在边角处互相重叠，不制造横向台阶。
        drop = SKIRT_WAIST - _xhz(t)[1]
        half = (7.2 + drop * math.tan(math.radians(SKIRT_FLARE_X))) if front else \
               (4.95 + drop * math.tan(math.radians(SKIRT_FLARE_Z)))
        if abs(lx if front else lz) > half:
            return None
        # 宽色带随 A 字展开，少量亮面对应长褶，避免等宽密条纹与随机噪点。
        across = lx if front else lz
        column = _grid(across, -half, 2 * half)
        tones = (2.5, 3.0, 3.5, 3.0, 2.5, 2.5, 3.5, 3.0)
        return self.skirt_colour(ly, tones[column] if front else tones[column] - 0.5)

    def skirt_colour(self, down, tone):
        if down > SKIRT_LENGTH - 3:
            return _tone(INNER, 3.0)
        if down > SKIRT_LENGTH - 4:
            return _tone(SASH, 3.0)
        return _tone(SKIRT, tone)

    def skirt_corner(self, t):
        return self.skirt_colour(t.local[1], 3.0)

    def shoe(self, t):
        if _xhz(t)[1] < 1:
            return _tone(SOLE, 0.5)
        return _tone(SHOE, 2.0 + _form(t, 0.2))


def painter(model):
    return _Paint(model)


# 为资源消费者保留有效 idle/walk 与支撑脚参数；完整裙摆、衣袖和战斗表现留待阶段 3。
WALK_LENGTH = 1.0
WALK_SWING = 20.0
IDLE_LENGTH = 4.0
SOLE_Z = (-5.0, 3.0)
SWING_LIFT = 1.5
HIP_SWAY = 1.5
LAG = math.pi / 4


def _walk_pose(phase):
    right = _leg_angle(phase, WALK_SWING)
    left = -right
    right_plants = math.cos(phase) >= 0
    stance, swing = (right, left) if right_plants else (left, right)
    drop = _sole_low(stance, HIP, SOLE_Z)
    lift = SWING_LIFT * abs(math.cos(phase)) + max(0, drop - _sole_low(swing, HIP, SOLE_Z))
    s = right / WALK_SWING
    sway = HIP_SWAY * math.cos(phase)
    trail = math.cos(phase - LAG)
    return {"root": {"position": (0, -drop, 0)},
            "leg_right": {"rotation": (right, 0, 0), "position": (0, 0 if right_plants else lift, 0)},
            "leg_left": {"rotation": (left, 0, 0), "position": (0, lift if right_plants else 0, 0)},
            "body": {"rotation": (1, 2 * s, sway)},
            "head": {"rotation": (-1, -1.5 * s, -0.7 * sway)},
            "arm_right": _rx(-1.2 * s), "arm_left": _rx(1.2 * s),
            "hair_back": {"rotation": (2.5, 0, -3 * trail)},
            "ribbon_right": _rx(2 * trail), "ribbon_left": _rx(2 * trail),
            "tie_right": _rx(-1.5), "tie_left": _rx(-1.5)}


def _idle_pose(phase):
    breath = math.sin(phase)
    slow = math.sin(phase - 0.9)
    return {"body": {"position": (0, 0.2 * breath, 0)},
            "head": _rx(0.6 * slow), "hair_back": _rx(0.6 * slow),
            "ribbon_right": _rx(slow), "ribbon_left": _rx(slow)}


def clips(model):
    return [_loop("idle", IDLE_LENGTH, _idle_pose, 8), _loop("walk", WALK_LENGTH, _walk_pose, 32)]
