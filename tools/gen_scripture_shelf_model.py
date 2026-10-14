#!/usr/bin/env python3
"""Deterministic two-block scripture furnishing and pixel atlas (stdlib).

North is the reading/front side (-Z). The full item model is split at Y=16
into lower and upper block models; all element rotations are vanilla-legal.
Use --check for resource drift; --preview needs numpy/Pillow in .venv-preview.
"""
from __future__ import annotations

import argparse
import copy
import itertools
import json
import math
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/myvillage"
PREVIEW = ROOT / "out/preview/cultivation_polish/shelf"
SIZE = 128
FACES = ("down", "up", "north", "south", "west", "east")
# Pixel rectangles. One generated atlas, no vanilla bookshelf/planks dependency.
REGIONS = {name: ((i % 4) * 32, (i // 4) * 32, (i % 4 + 1) * 32, (i // 4 + 1) * 32)
           for i, name in enumerate(("wood", "edge", "paper", "page_left", "page_right",
                                     "scroll_end", "brass", "jade", "tie"))}
BOUNDING_BOX = ((0.4, 0.0, 2.4), (15.6, 27.52, 13.6))


def png(rows):
    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xffffffff)
    h, w = len(rows), len(rows[0])
    raw = b"".join(b"\0" + bytes(v for pixel in row for v in pixel) for row in rows)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def texture_rows():
    rows = [[(36, 26, 21, 255) for _ in range(SIZE)] for _ in range(SIZE)]
    for name, (x0, y0, x1, y1) in REGIONS.items():
        for y in range(32):
            for x in range(32):
                noise = ((x * 73 + y * 37 + x * y * 11) % 9) - 4
                rim = x < 2 or y < 2 or x >= 30 or y >= 30
                if name in ("wood", "edge"):
                    grain = ((x + (y // 7) + (x // 5)) % 7 == 0)
                    base = (74, 47, 30) if name == "wood" else (104, 67, 41)
                    delta = noise - (14 if grain else 0) + (10 if x % 7 == 2 else 0)
                    c = tuple(v + delta for v in base)
                elif name in ("paper", "page_left", "page_right"):
                    c = (217 + noise, 204 + noise, 167 + noise)
                    if name == "paper":
                        if y % 6 == 0:
                            c = (178, 161, 128)
                    else:
                        if rim:
                            c = (182, 159, 115)
                        # Quiet inset rubric and six vertical columns of broken ink strokes.
                        if 3 <= y <= 27 and (x == 3 or x == 28):
                            c = (168, 127, 81)
                        if 5 <= x <= 26 and y == 5:
                            c = (149, 104, 62)
                        col, row = (x - 5) // 4, (y - 9) // 5
                        if 5 <= x <= 26 and 9 <= y <= 27 and (x - 5) % 4 < 2:
                            if (y - 9) % 5 in (0, 2) or ((x + col + row) % 3 == 0 and (y - 9) % 5 == 1):
                                c = (92, 89, 66)
                elif name == "scroll_end":
                    c = (229 + noise, 216 + noise, 179 + noise)
                    dx, dy = x - 15.5, y - 15.5
                    r, a = math.hypot(dx, dy), math.atan2(dy, dx)
                    spiral = (r - 1.3 * (a + math.pi)) % 6.5
                    if 3 < r < 13 and spiral < 1.3:
                        c = (116, 96, 68)
                    if r >= 14:
                        c = (94, 58, 33)
                elif name == "brass":
                    c = (167 + noise, 136 + noise, 78 + noise) if rim else (197 + noise, 169 + noise, 109 + noise)
                    if y == 3 or x == 3:
                        c = (225, 200, 139)
                elif name == "jade":
                    c = (45 + noise, 113 + noise, 92 + noise) if rim else (74 + noise, 151 + noise, 122 + noise)
                    if (x + y // 2) % 17 == 0:
                        c = (116, 185, 145)
                else:
                    c = (107 + noise, 72 + noise, 44 + noise)
                    if (x + y) % 6 == 0:
                        c = (151, 108, 66)
                rows[y0 + y][x0 + x] = tuple(max(0, min(255, v)) for v in c) + (255,)
    return rows


def uv(material):
    return [v / 8 for v in REGIONS[material]]


def elements():
    out = []

    def box(name, center, size, material="wood", *, angle=0, up=None, north=None):
        f = [round(c - s / 2, 5) for c, s in zip(center, size)]
        t = [round(c + s / 2, 5) for c, s in zip(center, size)]
        el = {"name": name, "from": f, "to": t,
              "faces": {face: {"uv": uv(up if face == "up" and up else north if face == "north" and north else material),
                                "texture": "#atlas"} for face in FACES}}
        if angle:
            el["rotation"] = {"origin": list(center), "axis": "x", "angle": angle, "rescale": False}
        out.append(el)

    for xi, x in enumerate((2.16, 13.84)):
        for zi, z in enumerate((4.08, 11.92)):
            name = f"foot_{xi}_{zi}"
            box(name, (x, 10.55, z), (1.36, 20.5, 1.36))
            box(name + "_pad", (x, .56, z), (2.08, 1.12, 2.08), "edge")
            box(name + "_band", (x, 5.28, z), (1.52, .64, 1.52), "brass")
    for i, y in enumerate((4.8, 12.0, 18.08)):
        box(f"shelf_{i}", (8, y, 8), (14.4, 1.04, 10.56))
        box(f"front_lip_{i}", (8, y + .29, 2.68), (15.04, .56, .56), "edge")
    for i, x in enumerate((1.28, 14.72)):
        box(f"side_low_{i}", (x, 6.88, 8), (.72, 2.88, 11.2))
        box(f"side_mid_{i}", (x, 13.6, 8), (.72, 2.4, 11.2))
        box(f"lectern_support_{i}", (x + (.48 if i == 0 else -.48), 21.6, 8.8), (.96, 5.6, 7.36))
    box("back_crossbar", (8, 14.4, 12.7), (12.48, 1.12, .72), "edge")
    box("jade_nameplate", (8, 19.04, 2.34 + .42), (1.76, 1.76, .56), "jade")
    box("nameplate_inlay", (8, 19.04, 2.46), (.35, 1.04, .12), "brass")
    box("reading_platform", (8, 23.2, 8), (14.4, 1.04, 9.2), angle=-22.5)
    box("reading_front_lip", (8, 21.6, 3.44), (15.2, .72, .88), "edge")
    box("reading_back_trim", (8, 25.28, 12.48), (15.04, .56, .72), "brass")
    for i, x in enumerate((2.24, 13.76)):
        box(f"finial_{i}", (x, 26.8, 11.92), (1.44, 1.44, 1.6), "jade")
    # Two page slabs, parchment on their edges and actual writing on their upper faces.
    # The book rests on the same 22.5-degree plane as the platform.
    for i, x in enumerate((5.36, 10.64)):
        box(f"open_page_{i}", (x, 24.12, 7.8), (4.96, .56, 6.4), "paper",
            angle=-22.5, up=("page_left" if i == 0 else "page_right"))
        box(f"book_cover_{i}", (x, 23.75, 7.8), (5.08, .20, 6.64), "tie", angle=-22.5)
    box("book_spine", (8, 24.16, 7.8), (.40, .72, 6.56), "tie", angle=-22.5)
    for j in range(5):
        x, y, z = 3.52 + j * 2.24, 6.28 + (j % 2) * .40, 7.52
        box(f"scroll_{j}", (x, y, z), (1.6, 1.52, 5.92), "paper")
        box(f"scroll_octagon_{j}", (x, y, z), (1.36, 1.76, 5.92), "paper")
        box(f"scroll_end_{j}", (x, y, 4.50), (1.64, 1.60, .16), "paper", north="scroll_end")
        box(f"scroll_back_{j}", (x, y, 10.58), (1.68, 1.60, .24), "edge")
        box(f"scroll_tie_{j}", (x, y, 7.8), (1.70, 1.86, .40), "tie")
    for j in range(3):
        box(f"folded_manuscript_{j}", (4.48, 13.12 + j * .72, 8), (3.84, .56, 5.44), "paper")
        box(f"manuscript_tie_{j}", (4.48, 13.12 + j * .72, 8), (.42, .64, 5.52), "tie")
    for j in range(4):
        height = 3.52 + j * .32
        box(f"jade_tablet_{j}", (9.92 + j * 1.20, 12.64 + height / 2, 8.64), (1.12, height, 1.28), "jade")
        box(f"tablet_inscription_{j}", (9.92 + j * 1.20, 14.72, 7.94), (.32, 1.76, .12), "brass")
    return out


def vertices(element):
    points = itertools.product(*zip(element["from"], element["to"]))
    rot = element.get("rotation")
    if not rot:
        return list(points)
    a = math.radians(rot["angle"])
    c, s = math.cos(a), math.sin(a)
    ox, oy, oz = rot["origin"]
    return [(x, oy + c * (y - oy) - s * (z - oz), oz + s * (y - oy) + c * (z - oz)) for x, y, z in points]


def split_elements(full, half):
    result = []
    for source in full:
        min_y, max_y = min(p[1] for p in vertices(source)), max(p[1] for p in vertices(source))
        if half == "lower" and min_y >= 16 or half == "upper" and max_y <= 16:
            continue
        element = copy.deepcopy(source)
        # Only the unrotated legs cross the seam. Clipping a rotated box would create
        # a different silhouette, so reject that instead of silently distorting it.
        if min_y < 16 < max_y:
            if source.get("rotation"):
                raise ValueError("Rotated element crosses the two-block seam: " + source["name"])
            element["to" if half == "lower" else "from"][1] = 16.0
        if half == "upper":
            for key in ("from", "to"):
                element[key][1] = round(element[key][1] - 16, 5)
            if "rotation" in element:
                element["rotation"]["origin"][1] = round(element["rotation"]["origin"][1] - 16, 5)
        result.append(element)
    return result


def encode(obj):
    return (json.dumps(obj, ensure_ascii=False, indent=2) + "\n").encode()


def outputs(assets=ASSETS):
    full = elements()
    texture = {"atlas": "myvillage:block/scripture_shelf", "particle": "myvillage:block/scripture_shelf"}
    result = {assets / "textures/block/scripture_shelf.png": png(texture_rows())}
    for suffix, data in (("", full), ("_lower", split_elements(full, "lower")), ("_upper", split_elements(full, "upper"))):
        result[assets / f"models/block/scripture_shelf{suffix}.json"] = encode({"ambientocclusion": True,
            "textures": texture, "elements": data})
    result[assets / "models/item/scripture_shelf.json"] = encode({"parent": "myvillage:block/scripture_shelf", "display": {
        "gui": {"rotation": [25, 225, 0], "translation": [0, -2.5, 0], "scale": [.48, .48, .48]},
        "ground": {"translation": [0, 1.4, 0], "scale": [.26, .26, .26]},
        "fixed": {"translation": [0, -2, 0], "scale": [.40, .40, .40]},
        "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, -1.5, 0], "scale": [.38, .38, .38]},
        "firstperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, -1.5, 0], "scale": [.38, .38, .38]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.7, 0], "scale": [.32, .32, .32]},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 1.7, 0], "scale": [.32, .32, .32]}}})
    result[assets / "blockstates/scripture_shelf.json"] = encode({"variants": {
        f"facing={direction},half={half}": {"model": f"myvillage:block/scripture_shelf_{half}", "y": rotation}
        for half in ("lower", "upper") for direction, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    return result


def preview(directory=PREVIEW):
    """Read generated resources back; never render a separate conceptual geometry."""
    import sys
    if str(ROOT) not in sys.path:
        sys.path.insert(0, str(ROOT))
    import numpy as np
    from PIL import Image
    from tools.combat_preview import item_model as pim
    from tools.beastgen.preview import Frame, render
    assets = pim.Assets([ROOT / "src/main/resources"], None)
    models = []
    for half in ("lower", "upper"):
        ref = f"myvillage:block/scripture_shelf_{half}"
        resolved = pim.resolve_model(assets, assets.model_json(ref), ref)
        for idx, el in enumerate(resolved.elements):
            for q in pim.element_quads(assets, resolved, el, idx):
                # Assemble actual block halves exactly as placed in the world.
                p = q.pos + np.array([0, 16 if half == "upper" else 0, 0])
                models.append(((p - np.array([8, 0, 8])) / 16, q.uv, q.tex, None))
        if resolved.errors or resolved.warnings:
            raise ValueError(resolved.errors + resolved.warnings)
    directory.mkdir(parents=True, exist_ok=True)
    for name, cam in (("front", (0, .32, -1)), ("quarter", (-1, .55, -1.3)), ("back", (1, .45, 1.1))):
        frame = Frame(cam, 700, 800, 360, (0, .86, 0), ss=2)
        render(models, frame, bg=(41, 54, 47), body_rot=180).save(directory / f"{name}.png")
    Image.open(ASSETS / "textures/block/scripture_shelf.png").resize((512, 512), Image.Resampling.NEAREST).save(directory / "atlas.png")
    (directory / "artifact.html").write_text('''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>藏经案实际生成模型</title>
<style>body{background:#17221d;color:#dae3d7;font:16px system-ui;margin:32px}main{max-width:1400px;margin:auto}section{display:flex;gap:12px}img{max-width:32%;object-fit:contain}p{color:#b9cbbd}</style>
<main><h1>藏经案 · 实际生成资源预览</h1><p>读回游戏 JSON 模型与 128px 专用纹理，拼合上下两格后离线渲染。正面朝北，0.95 × 0.70 × 1.72 格。游戏内光照、选中框、交互与破坏仍待实机验收。</p>
<section><img src="front.png"><img src="quarter.png"><img src="back.png"></section><p>像素材质图集：深木、经页、卷轴端面、铜饰、青玉</p><img src="atlas.png"></main></html>''', encoding="utf-8")
    print(f"Preview: {directory / 'artifact.html'}")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--preview", action="store_true")
    parser.add_argument("--assets-dir", type=Path, default=ASSETS)
    args = parser.parse_args(argv)
    stale = []
    for path, data in outputs(args.assets_dir).items():
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                stale.append(str(path))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    if stale:
        print("Stale scripture shelf resources:\n" + "\n".join(stale))
        return 1
    print("Scripture shelf resources match." if args.check else "Generated six scripture shelf resources.")
    if args.preview:
        if args.assets_dir != ASSETS:
            parser.error("--preview reads repository resources; --assets-dir must be default")
        preview()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
