"""靴子作者输入：完整方腿包覆、分级脚背与独立16像素GUI图稿。"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def styles(root=ROOT):
    return json.loads((Path(root) / "equipment/boots/styles.json").read_text())["styles"]


def parts(style):
    """返回作者像素坐标中的实心部件；Y向上、Z朝鞋尖，宽度保留MC脚部。"""
    h, w, front, toe = (style[k] for k in ("height", "width", "toe_front", "toe_height"))
    mats, result = style["materials"], []
    toe_width = style["toe_width"]

    def box(key, sign, mat, pos, size, name):
        result.append({"id": f"boot_{key}_{sign}", "material": mats[mat],
                       "position": [sign * 1.9 + pos[0], pos[1], pos[2]],
                       "size": size, "name": name})

    for sign in (-1, 1):
        # 靴身完整覆盖4×4原版脚部及0.25像素外层皮肤；脚背和鞋头只在下端前伸。
        box("shaft", sign, "upper", [0, (h-.1)/2, 0], [w, h-1, 4.6], "完整方靴身")
        if "core" in mats:
            box("core", sign, "core", [0, (h-.1)/2, 0], [4.51, h-1.04, 4.54], "琉璃外壳下的不透明完整靴芯")
        box("instep", sign, "vamp", [0, 1.7, 2.5], [w, 2.5, 1.65], "从脚踝下降的脚背")
        box("toe", sign, "vamp", [0, (.45+toe)/2, (front+2.8)/2],
            [toe_width, toe-.45, front-2.8], "前伸低方鞋头")
        # 护头是鞋尖本体的一部分；不会把一块高筒前面板当作鞋头。
        box("toe_cap", sign, "cap", [0, (.48+toe)/2, front-.4],
            [toe_width+.04, toe-.38, .88], "方鞋头材质护面")
        # 足弓只是底纹的浅凹口；连续底面仍低于皮肤外层的-0.25，不能露出脚底。
        box("welt", sign, "sole", [0, .12, .4],
            [w+.22, .8, 5.5], "贯通前掌与后跟的鞋底")
        box("toe_welt", sign, "sole", [0, .12, (front+3.1)/2],
            [toe_width+.14, .8, front-2.9], "方鞋头下收窄的前底")
        box("heel", sign, "sole", [0, -.3, -1.52], [w+.18, .32, 1.85], "低方鞋跟")
        box("fore_sole", sign, "sole", [0, -.3, 2.25],
            [w+.18, .32, 1.9], "前掌实心底与浅足弓")
        box("toe_tread", sign, "sole", [0, -.3, (front+3.1)/2],
            [toe_width+.12, .32, front-2.9], "鞋尖前掌底")
        if "edge" in mats:
            box("toe_edge", sign, "edge", [0, .6, front+.06],
                [toe_width-.15, .18, .14], "琉璃护头下的窄银边")
        collar = style["collar"]
        flare = .35 if collar in ("flare", "roll") else .14
        thick = .23 if collar in ("roll", "fold", "court") else .16
        lip = .7 if collar in ("fold", "roll", "flare") else .5
        for side in (-1, 1):
            box(f"cuff_side_{side}", sign, "cuff", [side*(w/2+flare/2), h-lip/2, 0],
                [thick+flare, lip, 4.72+flare], "靴口两侧硬边")
        box("cuff_back", sign, "cuff", [0, h-lip/2, -2.38-flare/2],
            [w+flare, lip, thick+flare], "保留内孔的后靴口")
        if collar == "notch":
            for side in (-1, 1):
                box(f"cuff_front_{side}", sign, "cuff", [side*(w/4+.3), h-.25, 2.4],
                    [w/2-.6, .5, .25], "前口两端方缺角")
            box("cuff_notch", sign, "cuff", [0, h-.65, 2.4], [1.25, .3, .25], "靴口中央低缺口")
        else:
            box("cuff_front", sign, "cuff", [0, h-lip/2, 2.38+flare/2],
                [w+flare, lip, thick+flare], "前靴口收边")
        if collar in ("high_back", "facet", "sprout"):
            box("cuff_raise", sign, "cuff", [0, h+.18, -2.4], [w-.12, .36, .28], "略高的后靴口")
        if collar in ("court", "lotus", "sprout"):
            box("cuff_step", sign, "trim", [0, h+.14, 2.42], [w*.48, .3, .3], "靴口中央阶形收口")
        # 外踝饰纹按各套主题绘制，使用有厚度的短色块，避免以装饰替代靴身。
        motif = style["motif"]
        outer = sign*(w/2+.1)
        def side(key, mat, z, y, dz, dy):
            box(key, sign, mat, [outer, y, z], [.18, dy, dz], "外踝"+key)
        y = min(h-1.15, 3.8)
        if motif in ("fold", "quiet", "cloud"):
            side("fold_h", "trim", .65, y-.4, 1.8, .35)
            side("fold_v", "trim", -.1, y+.13, .35, 1.4)
            if motif == "cloud": side("cloud_step", "accent", -.65, y+.65, 1.35, .32)
        elif motif in ("window", "seal", "sun"):
            for j, (z, yy, dz, dy) in enumerate([(0,y-.7,1.9,.3),(0,y+.7,1.9,.3),(-.8,y,.3,1.3),(.8,y,.3,1.3)]):
                side(f"frame_{j}", "trim", z, yy, dz, dy)
            side("frame_core", "accent", 0, y, .8, .8)
        elif motif in ("gate", "thunder"):
            side("gate_top", "trim", 0, y+.7, 1.9, .3)
            side("gate_stem", "trim", -.8, y, .3, 1.4)
            side("gate_turn", "trim", -.25, y-.6, 1.3, .3)
            side("gate_heart", "accent", .7, y-.15, .4, .75)
        elif motif == "moon":
            side("moon_back", "trim", -.6, y, .45, 1.7)
            side("moon_top", "trim", -.1, y+.7, 1.3, .32)
            side("moon_base", "trim", -.1, y-.7, 1.3, .32)
        elif motif == "star":
            side("star_h", "trim", 0, y, 1.8, .4)
            side("star_v", "accent", 0, y, .4, 1.8)
            side("star_point", "accent", 1.25, y-.6, .35, .35)
        elif motif == "root":
            side("root_stem", "trim", -.3, y-.25, .35, 2)
            side("root_branch", "trim", .4, y+.2, 1.6, .35)
            side("root_fork", "trim", 1.05, y+.6, .35, 1.05)
            side("root_bud", "accent", -1, h-.4, .65, .6)
        elif motif == "lotus":
            side("lotus_low", "trim", 0, y-.55, 2.4, .3)
            side("lotus_mid", "trim", 0, y-.1, 1.6, .3)
            side("lotus_high", "trim", 0, y+.35, .65, .6)
        elif motif == "facet":
            side("facet_lower", "trim", .7, 2.35, 1.6, .18)
            side("facet_upper", "trim", -.2, 3.1, .18, 1.3)
        elif motif == "strap":
            side("strap", "cuff", 0, y, 4.6, .55)
            side("strap_buckle", "trim", .4, y, 1.05, 1.1)
            side("strap_hole", "upper", .4, y, .5, .55)
        if motif in ("ridge", "axis"):
            box("front_axis", sign, "trim", [0, (3.15+h-.65)/2, 2.4],
                [.6, h-3.8, .25], "靴筒正面直脊")
            if motif == "axis":
                box("front_cross", sign, "trim", [0, h-.9, 2.4], [2.6, .45, .26], "靴筒定极铭横笔")
            box("instep_axis", sign, "trim", [0, 2.98, 2.55], [.6, .22, 1.6], "脚背短脊")
        if motif in ("axis", "lotus", "quiet"):
            box("heel_mark", sign, "accent", [0, 1.65, -2.38], [1.15, 1.3, .22], "后帮嵌色小印")
    return result


