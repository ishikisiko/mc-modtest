"""四槽原创像素图稿；只接入GUI，不派生或改写穿戴几何。"""
from __future__ import annotations

import json
from pathlib import Path

from .catalogue import ROOT
from .geometry import encode_png


def icon(set_id, slot, materials, root=ROOT):
    doc = json.loads((Path(root) / "equipment/icons/armor.json").read_text())
    if doc["version"] != 1 or doc["size"] != 16:
        raise ValueError("防具图标需要版本1的16像素作者输入")
    art = doc["sets"][set_id][slot]
    if "boot_style" in art:
        from .boots import styles
        style = styles(root)[art["boot_style"]]
        rows = style["icon"]["rows"]
        source = style["materials"]
        bindings = {"b": ("upper", "base"), "s": ("upper", "dark"),
                    "l": ("upper", "light"), "v": ("vamp", "base"),
                    "t": ("cap", "light"), "e": ("trim", "base"),
                    "c": ("cuff", "base"), "a": ("accent", "light"),
                    "r": ("sole", "dark"), "o": ("upper", "outline"),
                    "i": ("upper", "inset")}
    else:
        rows, source = art["rows"], art["materials"]
        bindings = {"b": ("primary", "base"), "s": ("primary", "dark"),
                    "l": ("primary", "light"), "v": ("secondary", "base"),
                    "w": ("secondary", "dark"), "h": ("secondary", "light"),
                    "e": ("trim", "base"), "g": ("trim", "light"),
                    "c": ("lining", "base"), "d": ("lining", "dark"),
                    "a": ("accent", "base"), "t": ("accent", "light"),
                    "o": ("primary", "outline"), "i": ("primary", "inset")}

    def shade(key, tone):
        mat = materials[source[key]]
        level = "dark" if tone in ("outline", "inset") else tone
        if "pixelArt" in mat:
            color = mat["pixelArt"]["palette"][{"dark": 0, "base": 2, "light": 3}[level]]
        else:
            color = mat[level]
        rgb = tuple(int(color[i:i+2], 16) for i in (1, 3, 5))
        factor = doc["shading"].get(tone, 1)
        return tuple(round(c*factor) for c in rgb)+(255,)

    colors = {".": (0, 0, 0, 0)}
    colors.update({char: shade(key, tone) for char, (key, tone) in bindings.items()})
    # 多色主晶直接采样原材质的实际切面，不把自定义九色色表误当通用三阶。
    for char, (material, index) in art.get("palette_samples", {}).items():
        color = materials[material]["pixelArt"]["palette"][index]
        colors[char] = tuple(int(color[i:i+2], 16) for i in (1, 3, 5))+(255,)
    if len(rows) != 16 or any(len(row) != 16 or set(row)-colors.keys() for row in rows):
        raise ValueError(f"{set_id}/{slot}: 图稿必须为16×16，且只能使用已声明的语义色")
    return encode_png([[colors[char] for char in row] for row in rows])
