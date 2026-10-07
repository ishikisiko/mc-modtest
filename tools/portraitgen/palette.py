"""Colour ramps shared with the cultivator skins (tools/npcgen/defs/cultivator*.py)."""
from __future__ import annotations


def rgb(h: str) -> tuple[int, int, int, int]:
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def mix(a, b, t: float):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def ramp(*hexes):
    return [rgb(h) for h in hexes]


# skin: (outline, deep, shade, base, light)
SKINS = {
    "pale": ramp("#8A4A4E", "#D89A86", "#F0C2AE", "#FFE4D6", "#FFF4EC"),
    "light": ramp("#7A4238", "#CF9478", "#E8BC9E", "#F8DAC2", "#FFEEDD"),
    "tan": ramp("#6A3A2A", "#B47A5A", "#D09A74", "#E8BE98", "#F4D8BC"),
}

# hair: (outline, shadow, base, light, shine)
HAIRS = {
    "ink_blue": ramp("#10142A", "#1C2440", "#2A3658", "#3E4E78", "#6E80A8"),
    "ink_violet": ramp("#150E24", "#231A3A", "#322852", "#473B70", "#7A6AA6"),
    "dark_brown": ramp("#24100E", "#3E2018", "#5A3324", "#7A4A34", "#A8785A"),
    "chestnut": ramp("#2C120A", "#5A2E1C", "#7E4A2E", "#A06A44", "#D0A078"),
    "silver": ramp("#4A4E66", "#8A90A6", "#B8BECE", "#DCE0EA", "#F8F8FC"),
}

# iris: (dark, mid, light) by the dominant root element; violet for the nascent soul realm
EYES = {
    "metal": ramp("#3E4654", "#7E8A9E", "#C4CCD8"),
    "wood": ramp("#1E5A3A", "#3F9A62", "#9BE0B0"),
    "water": ramp("#1E3F8F", "#3D7BD9", "#8FD0F5"),
    "fire": ramp("#6E2A14", "#C06A2C", "#F0C27A"),
    "earth": ramp("#4A3018", "#8C6230", "#D2A868"),
    "spirit": ramp("#4A1F6B", "#8E4FC4", "#D9A8F0"),
}
EYE_WHITE = rgb("#F4F6FF")
EYE_WHITE_SHADE = rgb("#D8DEEA")
LASH = rgb("#171522")
HIGHLIGHT = rgb("#FFFFFF")

LIP = rgb("#C46A6E")
LIP_DEEP = rgb("#A44F5A")
LIP_PALE = rgb("#DA918A")
BLUSH = rgb("#F2A0A0")

# robes by realm tier: (outline, shade, base, light) + trim (dark, base, light) + inner
ROBES = {
    # 炼气 / 凡人: 素色麻布
    "plain_m": {"cloth": ramp("#4C5563", "#93A3B8", "#C6D1DE", "#EBF0F5"), "trim": ramp("#1B4A60", "#27687F", "#52A3B8"),
                "inner": ramp("#CFD4DB", "#F5F6F8")},
    "plain_f": {"cloth": ramp("#505A68", "#9DABBF", "#DEE6EE", "#F8FAFC"), "trim": ramp("#1E2531", "#334055", "#617391"),
                "inner": ramp("#CFD4DB", "#F5F6F8")},
    # 筑基: 染色外袍, 深色交领
    "dyed_m": {"cloth": ramp("#163A48", "#27687F", "#35869E", "#7FC0CF"), "trim": ramp("#0F2A36", "#174354", "#3D95AE"),
               "inner": ramp("#CFD4DB", "#F5F6F8")},
    "dyed_f": {"cloth": ramp("#3E2F3A", "#836D7B", "#B49FAD", "#DACAD4"), "trim": ramp("#2A1F27", "#45343F", "#876E7E"),
               "inner": ramp("#DEDBD3", "#F8F6F1")},
    # 金丹: 锦缎, 金边
    "brocade_m": {"cloth": ramp("#1E2C48", "#30466E", "#5373A8", "#84A6D6"), "trim": ramp("#7A6432", "#C4A75C", "#F6E6AC"),
                  "inner": ramp("#CFD4DB", "#F5F6F8")},
    "brocade_f": {"cloth": ramp("#2A0E1E", "#561F3E", "#873563", "#BA5E8E"), "trim": ramp("#6E5328", "#BE9A48", "#FFF1C4"),
                  "inner": ramp("#D0C8BF", "#FFFCF7")},
    # 元婴: 玄色, 金绣
    "court_m": {"cloth": ramp("#0A0E1E", "#182243", "#2A3A6E", "#445C9E"), "trim": ramp("#7A6432", "#E2C97F", "#F6E6AC"),
                "inner": ramp("#B4BBC5", "#E6E9ED")},
    "court_f": {"cloth": ramp("#1C0A14", "#40162E", "#6E2950", "#A04677"), "trim": ramp("#957333", "#F2DB97", "#FFF1C4"),
                "inner": ramp("#D0C8BF", "#FFFCF7")},
}

# sect accents (ribbons, headbands): (dark, base, light); index by sect id, rogue = last
ACCENTS = [
    ramp("#561317", "#9C2A2E", "#D45A55"),   # 绛红
    ramp("#174354", "#2C7892", "#5FB2C6"),   # 靛青
    ramp("#22665A", "#52B09A", "#BCF0DB"),   # 玉青
    ramp("#7A6432", "#C4A75C", "#F6E6AC"),   # 金
    ramp("#3B2A5A", "#6E4FA0", "#A98BD6"),   # 紫
    ramp("#5A3A1E", "#9C6A38", "#D8A66A"),   # 赭
    ramp("#4A4A50", "#8A8A92", "#C8C8CE"),   # 灰 (散修)
]
for _k in list(HAIRS):
    _r = HAIRS[_k]
    _s = HAIRS["silver"]
    HAIRS["grey_" + _k] = [mix(_r[i], _s[i], 0.5) for i in range(5)]

GOLD = ramp("#7A6432", "#C4A75C", "#E2C97F", "#F6E6AC")
JADE = ramp("#22665A", "#52B09A", "#84D6BA", "#BCF0DB")
WOOD_PIN = ramp("#3A2414", "#6A4426", "#9C6A3E")
BANDAGE = ramp("#9A948A", "#D8D2C6", "#F4F0E8")
SCAR = rgb("#A8514E")
