"""入门女修重做：MC 方头、低马尾、自然垂手与独立连续裙片。

面部使用原创 8×8 像素构图，衣料采用基础色；服装细纹与完整动作留待后续审美闸门。
保留实体、Look、scale 0.5、基础骨骼和碰撞接口。
模型、动画、贴图、角色材质图均由 npcgen 生成，不使用贴图后处理覆盖。
"""
from __future__ import annotations

import math

from ...beastgen.builder import Builder
from ...beastgen.paint import ramp
from ..humanoid import (HumanoidPaint, _box, _clamp, _form, _leg_angle, _loop,
                        _mix, _pair, _rgb, _rx, _sole_low, _tone, _xhz)

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
HOLLOW = ("jacket", *SKIRT_PANEL_CUBES)

RU = ramp("#8392A8", "#9DABBF", "#B6C3D3", "#CCD7E3", "#DEE6EE", "#ECF1F6", "#F8FAFC")
INNER = ramp("#B4BBC5", "#CFD4DB", "#E6E9ED", "#F5F6F8", "#FFFFFF")
JACKET_CLOTH = ramp("#1E2531", "#283242", "#334055", "#404F67", "#4F607B", "#617391", "#7688A6")
SKIRT = ramp("#6A5562", "#836D7B", "#9C8695", "#B49FAD", "#C9B6C3", "#DACAD4", "#E9DFE6")
SASH = ramp("#45343F", "#5A4653", "#705968", "#876E7E", "#9E8696")
EDGE = ramp("#C9C6BE", "#DEDBD3", "#EEEBE4", "#F8F6F1")
SHOE = ramp("#181920", "#23252F", "#30333F", "#404452", "#525767")
SOLE = ramp("#A8A49A", "#CAC6BC", "#E8E4DA")
RIBBON = ramp("#561317", "#781D22", "#9C2A2E", "#BC3C3B", "#D45A55")
SKIN = ramp("#BA8A74", "#D6A690", "#ECC3AE", "#F8D8C2", "#FEE6DA", "#FFF0E8", "#FFF8F2")
HAIR = ramp("#0C0B10", "#16141C", "#211E29", "#2E2A38", "#3E394B", "#544E64", "#6D6680")
EYE_WHITE = "#F8FAFF"
IRIS_TOP = "#5A2E1A"
IRIS = "#B2702F"
IRIS_LOW = "#F0C27A"
LASH = "#32252F"
LIP = "#EBC8BF"

# 从头顶向下、从 -X 向 +X；H 为发色，L 为短睫毛，W 为眼白，I/A 为上下虹膜。
# 只存索引，调色板在 painter 调用时读取，人物发色与瞳色才能进入 role map。
FACE_TEMPLATE = (
    "HHHHHHHH",
    "HHHHHHHH",
    "HHHHHHHH",
    ".LLHHLL.",
    ".WI..IW.",
    ".WA..AW.",
    "...mm...",
    "........",
)


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
    _box(b, "body", "band", -7.5, BAND[0], -5, 15, 3, 10)
    _box(b, "body", "neck", -3, NECK - 2.5, -2.5, 6, 3, 5)
    # 交领仅保留辨识轮廓，不绘制最终衣纹。
    b.bone("collar_over", "body", at=(3.0, -COLLAR_TOP, -4.25), rot=(0.0, 0.0, 25.0))
    b.box_local("collar_over", "collar_over_band", (-1, -0.5, -0.5), (2, 12, 1))
    b.bone("collar_under", "body", at=(-3.0, -COLLAR_TOP, -4.1), rot=(0.0, 0.0, -25.0))
    b.box_local("collar_under", "collar_under_band", (-1, -0.5, -0.5), (2, 9, 1))
    _box(b, "body", "knot", -1.5, 28, -6, 3, 2, 2)
    _pair(b, "tie_{}", "body", (-1.0, -29.0, -5.8), rot=(-5.0, 0.0, -4.0),
          boxes=[("tie_{}_band", (-1, 0, -0.5), (2, 8, 1))], local=True)

    b.bone("head", "body", at=(0.0, -NECK, 0.0))
    _box(b, "head", "skull", -HEAD_SIZE / 2, NECK, -HEAD_SIZE / 2,
         HEAD_SIZE, HEAD_SIZE, HEAD_SIZE)
    # 发型由大体块组织：顶发、后发、三束不等长刘海与两侧短鬓发。
    _box(b, "head", "hair_cap", -7, 59, -7, 14, 2, 14)
    _box(b, "head", "hair_back_head", -7, 49, 6, 14, 11, 2)
    _box(b, "head", "hair_side_right", -7.5, 51, -7, 1, 9, 14)
    _box(b, "head", "hair_side_left", 6.5, 51, -7, 1, 9, 14)
    _box(b, "head", "hair_fringe_right", -7, 54, -8, 5, 6, 2)
    _box(b, "head", "hair_fringe_middle", -2, 56, -8, 4, 4, 2)
    _box(b, "head", "hair_fringe_left", 2, 55, -8, 5, 5, 2)
    _box(b, "head", "hair_temple_right", -7.5, 48, -7.75, 2, 7, 4)
    _box(b, "head", "hair_temple_left", 5.5, 49, -7.75, 2, 6, 4)

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
        b.box_local("forearm_" + side, "cuff_" + side, (-3, 6, -3), (6, 2, 6), inflate=0.15, **uv("cuff"))
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
    """基础服色与像素脸；头发、虹膜通过角色材质图进入人物换色流程。"""
    SKIN = SKIN
    HAIR = HAIR
    FINGER_ROW = 9.0

    def __init__(self, model):
        mapping = {"skull": self.skull, "torso": self.ru, "jacket": self.jacket,
                   "band": self.band, "neck": self.neck,
                   "collar_over_band": self.collar, "collar_under_band": self.collar,
                   "knot": self.band, "tie_right_band": self.band,
                   "sleeve_upper_right": self.ru, "short_sleeve_right": self.short_sleeve,
                   "sleeve_lower_right": self.ru, "cuff_right": self.cuff,
                   "hand_right": self.hand, "leg_inner_right": self.ru, "shoe_right": self.shoe,
                   "ribbon": self.ribbon, "ribbon_right_tail": self.ribbon}
        for name in ("hair_cap", "hair_back_head", "hair_side_right", "hair_side_left",
                     "hair_fringe_right", "hair_fringe_middle", "hair_fringe_left",
                     "hair_temple_right", "hair_temple_left", "hair_back_root", "hair_back_main", "hair_back_tip"):
            mapping[name] = self.hair
        for name in SKIRT_PANEL_CUBES:
            mapping[name] = self.skirt
        for side in ("right", "left"):
            for end in ("front", "back"):
                mapping[f"skirt_corner_{side}_{end}_fold"] = self.skirt_corner
        super().__init__(model, HOLLOW, mapping)

    def face(self, t):
        x, h, _ = _xhz(t)
        row, col = face_cell(x, h)
        symbol = FACE_TEMPLATE[row][col]
        if symbol == "H":
            return _tone(HAIR, 2.5)
        if symbol == "L":
            return _rgb(LASH)
        if symbol == "W":
            return _rgb(EYE_WHITE)
        if symbol == "I":
            px, py = int(x + HEAD_SIZE / 2), int(HEAD_TOP - h)
            # 每眼仅一颗原生纹素高光，避免整片眼白式亮斑；其余仍是成块的虹膜。
            if py == 7 and px in (3, 10):
                return _mix(_rgb(IRIS_LOW), _rgb(EYE_WHITE), 0.5)
            return _rgb(IRIS_TOP if py == 7 else IRIS)
        if symbol == "A":
            return _rgb(IRIS_LOW)
        if symbol == "m":
            # 两格构图落到 14 宽头面后，嘴线只取中间两纹素与一行，避免变成大色块。
            if abs(x) < 1 and int(HEAD_TOP - h) == 11:
                return _rgb(LIP)
        return _tone(SKIN, 5.5)

    def skull(self, t):
        if t.face == "NORTH":
            return self.face(t)
        if t.face in ("WEST", "EAST", "UP"):
            return _tone(SKIN, 5.0 if t.face != "UP" else 4.0)
        return _tone(HAIR, 2.0)

    def hair(self, t):
        tone = {"hair_fringe_right": 2.5, "hair_fringe_middle": 3.0,
                "hair_fringe_left": 2.0, "hair_temple_right": 2.0,
                "hair_temple_left": 2.5, "hair_back_main": 2.5, "hair_back_tip": 2.0}.get(t.cube, 2.5)
        return _tone(HAIR, tone + _form(t, 0.35))

    def neck(self, t):
        return _tone(SKIN, 4.5)

    def hand(self, t):
        return _tone(SKIN, 4.8 + _form(t, 0.25))

    def ru(self, t):
        return _tone(RU, 4.8 + _form(t, 0.3))

    def jacket(self, t):
        x, h, z = _xhz(t)
        if t.face == "NORTH" and abs(x) < OPENING:
            return None
        if t.face == "DOWN" and abs(x) < 3.5 and abs(z) < 3.0:
            return None
        return _tone(JACKET_CLOTH, 4.5 + _form(t, 0.35))

    def collar(self, t):
        return _tone(INNER, 3.5 + _form(t, 0.2))

    def short_sleeve(self, t):
        return _tone(JACKET_CLOTH, 4.5 + _form(t, 0.3))

    def cuff(self, t):
        return _tone(EDGE, 2.0 + _form(t, 0.2))

    def band(self, t):
        return _tone(SASH, 3.0 + _form(t, 0.25))

    def ribbon(self, t):
        return _tone(RIBBON, 3.0 + _form(t, 0.3))

    def skirt(self, t):
        lx, ly, lz = t.local
        front = t.cube in ("skirt_front_panel", "skirt_back_panel")
        # 薄片表面的梯形裁切是二值像素轮廓；四片在边角处互相重叠，不制造横向台阶。
        drop = SKIRT_WAIST - _xhz(t)[1]
        half = (7.2 + drop * math.tan(math.radians(SKIRT_FLARE_X))) if front else \
               (4.95 + drop * math.tan(math.radians(SKIRT_FLARE_Z)))
        if abs(lx if front else lz) > half:
            return None
        tone = 4.3 + (0.25 if front else -0.1) + _form(t, 0.2)
        if ly > SKIRT_LENGTH - 1:
            return _tone(EDGE, 1.8)
        return _tone(SKIRT, tone)

    def skirt_corner(self, t):
        if t.local[1] > SKIRT_LENGTH - 1:
            return _tone(EDGE, 1.8)
        return _tone(SKIRT, 4.4 + _form(t, 0.2))

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
