#!/usr/bin/env python3
"""Six forging-material studies: two xianxia-leaning redesigns per material.

These are CANDIDATES, not production resources. They are written to
out/preview/forging_material_studies/ so the shipped item textures stay
untouched until one direction is chosen.

Usage: python3 -m tools.forging_material_studies
"""
from __future__ import annotations

import json
from pathlib import Path

from tools.gen_alchemy_assets import Canvas, png

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "out/preview/forging_material_studies"

# ---------------------------------------------------------------- shared shapes

INGOT_A_SIDE = [(2, 19), (8, 11), (25, 8), (30, 14), (23, 24), (6, 27), (2, 23)]
INGOT_A_FRONT = [(2, 19), (23, 16), (30, 14), (25, 23), (6, 27), (2, 23)]
INGOT_A_TOP = [(2, 19), (8, 11), (25, 8), (30, 14), (23, 19), (6, 23)]

INGOT_B_SIDE = [(4, 19), (9, 12), (24, 9), (29, 15), (23, 24), (9, 27), (4, 23)]
INGOT_B_FRONT = [(4, 19), (22, 16), (29, 15), (24, 23), (9, 27), (4, 23)]
INGOT_B_TOP = [(4, 19), (9, 12), (24, 9), (29, 15), (22, 19), (8, 23)]


def base_ingot(c, side, front, top, *, shape="a", top_inner=None, lip=None, shadow=None):
    """Three-face ingot with a clean value structure."""
    if shape == "a":
        s, f, t = INGOT_A_SIDE, INGOT_A_FRONT, INGOT_A_TOP
    else:
        s, f, t = INGOT_B_SIDE, INGOT_B_FRONT, INGOT_B_TOP
    c.poly(s, side)
    c.poly(f, front)
    c.poly(t, top)
    if top_inner:
        if shape == "a":
            c.line(8, 13, 24, 10, top_inner)
            c.line(6, 21, 22, 17, top_inner)
        else:
            c.line(9, 14, 23, 11, top_inner)
            c.line(8, 21, 21, 17, top_inner)
    if lip:
        if shape == "a":
            c.line(4, 19, 22, 16, lip)
        else:
            c.line(5, 19, 21, 16, lip)
    if shadow:
        if shape == "a":
            c.line(7, 25, 23, 22, shadow)
        else:
            c.line(9, 26, 23, 22, shadow)
    return c


def edge_light(c, color, pale, *, shape="a"):
    if shape == "a":
        c.line(2, 19, 8, 11, color)
        c.line(8, 11, 25, 8, pale)
        c.line(25, 8, 30, 14, color)
        c.line(2, 19, 2, 23, color)
        c.line(2, 23, 6, 27, color)
        c.line(6, 27, 23, 24, color)
    else:
        c.line(4, 19, 9, 12, color)
        c.line(9, 12, 24, 9, pale)
        c.line(24, 9, 29, 15, color)
        c.line(4, 19, 4, 23, color)
        c.line(4, 23, 9, 27, color)
        c.line(9, 27, 23, 24, color)


def outline(c, color):
    c.outline(color)


# ---------------------------------------------------------------- xuantie studies

def _star(c, x, y, color, size=1):
    c.put(x, y, color)
    if size > 1:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            c.put(x + dx, y + dy, color)


def xuantie_starfall():
    """A: deep indigo metal, a small constellation and sparse starlight."""
    c = Canvas()
    side, front, top = (18, 23, 34), (38, 47, 66), (60, 74, 100)
    base_ingot(c, side, front, top, shape="a",
               top_inner=(50, 62, 86), lip=(48, 60, 84), shadow=(24, 30, 44))
    edge_light(c, (120, 146, 188), (196, 220, 252), shape="a")
    c.line(10, 16, 22, 13, (74, 90, 122))
    # Constellation: four stars, two bright, connected by dim lines.
    c.line(10, 15, 14, 13, (88, 112, 152))
    c.line(14, 13, 18, 15, (88, 112, 152))
    c.line(18, 15, 22, 12, (88, 112, 152))
    _star(c, 10, 15, (214, 232, 255), 1)
    _star(c, 14, 13, (236, 244, 255), 2)
    _star(c, 18, 15, (196, 220, 252), 1)
    _star(c, 22, 12, (226, 240, 255), 1)
    # Dim halo around the brightest star and three background grains.
    for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2)):
        c.put(14 + dx, 13 + dy, (96, 118, 158))
    _star(c, 8, 13, (110, 136, 176), 1)
    _star(c, 20, 17, (96, 120, 158), 1)
    _star(c, 24, 15, (84, 106, 142), 1)
    # Two small stars caught on the front face, no framed seal.
    _star(c, 7, 22, (176, 198, 232), 1)
    _star(c, 20, 22, (120, 144, 184), 1)
    c.put(11, 23, (74, 90, 122))
    outline(c, (9, 12, 20))
    return c.rows


def xuantie_thunder():
    """B: charcoal steel with a square thunder meander and a violet glow."""
    c = Canvas()
    side, front, top = (24, 28, 38), (44, 50, 66), (66, 74, 94)
    base_ingot(c, side, front, top, shape="b",
               top_inner=(56, 64, 84), lip=(54, 62, 80), shadow=(28, 32, 44))
    edge_light(c, (156, 170, 204), (216, 226, 248), shape="b")
    # Classic leiwen meander: dark groove, then a bright violet core.
    meander = [(8, 18), (8, 13), (15, 13), (15, 18), (11, 18), (11, 15), (13, 15)]
    for (x1, y1), (x2, y2) in zip(meander, meander[1:]):
        c.line(x1, y1, x2, y2, (34, 26, 52), 2)
    for (x1, y1), (x2, y2) in zip(meander, meander[1:]):
        c.line(x1, y1, x2, y2, (184, 166, 246))
    c.put(8, 13, (240, 232, 255))
    c.put(15, 13, (240, 232, 255))
    c.put(13, 15, (240, 232, 255))
    # A second, smaller meander on the right keeps the motif from looking lonely.
    small = [(19, 16), (19, 13), (23, 13), (23, 17), (21, 17)]
    for (x1, y1), (x2, y2) in zip(small, small[1:]):
        c.line(x1, y1, x2, y2, (34, 26, 52), 2)
    for (x1, y1), (x2, y2) in zip(small, small[1:]):
        c.line(x1, y1, x2, y2, (184, 166, 246))
    # Dim violet bloom around the main meander.
    for x, y in ((7, 13), (16, 14), (15, 19), (10, 19), (20, 12)):
        c.put(x, y, (92, 76, 138))
    # The motif spills a short fork onto the front face.
    c.line(9, 19, 8, 23, (128, 104, 190))
    c.put(8, 23, (92, 76, 138))
    outline(c, (12, 9, 18))
    return c.rows


def chitong_meridian():
    """A: warm copper with a fine jade-green channel network."""
    c = Canvas()
    side, front, top = (78, 36, 28), (140, 66, 42), (196, 108, 58)
    base_ingot(c, side, front, top, shape="a",
               top_inner=(176, 92, 50), lip=(168, 86, 48), shadow=(100, 44, 32))
    edge_light(c, (232, 150, 92), (255, 198, 136), shape="a")
    c.line(10, 15, 22, 12, (174, 90, 48))
    # A branching spirit channel, drawn as one continuous flow with two side veins.
    main = [(7, 18), (10, 16), (13, 17), (16, 15), (19, 16), (22, 13)]
    branch_a = [(12, 17), (11, 20)]
    branch_b = [(17, 15), (18, 19)]
    for path in (main, branch_a, branch_b):
        for (x1, y1), (x2, y2) in zip(path, path[1:]):
            c.line(x1, y1, x2, y2, (38, 104, 96))
        for (x1, y1), (x2, y2) in zip(path, path[1:]):
            c.line(x1, y1, x2, y2, (94, 226, 188))
    for x, y in ((7, 18), (13, 17), (19, 16), (22, 13), (11, 20), (18, 19)):
        c.put(x, y, (200, 255, 236))
    # Jade bead: two pixels with a dark rim, not a square block.
    c.rect(14, 16, 15, 17, (28, 82, 78))
    c.put(14, 16, (120, 228, 196))
    c.put(15, 17, (76, 196, 164))
    # Warm front lip and a faint reflected channel.
    c.line(5, 19, 10, 18, (236, 158, 96))
    c.line(9, 21, 12, 20, (72, 140, 124))
    c.put(10, 21, (116, 204, 176))
    outline(c, (38, 18, 14))
    return c.rows


def chitong_cloud():
    """B: red-gold copper with a gold cloud scroll and a warm reflection."""
    c = Canvas()
    side, front, top = (84, 30, 26), (150, 58, 40), (206, 98, 54)
    base_ingot(c, side, front, top, shape="b",
               top_inner=(186, 84, 46), lip=(174, 76, 44), shadow=(108, 38, 32))
    edge_light(c, (246, 152, 90), (255, 204, 140), shape="b")
    # One large cloud head plus a trailing curl; two mirrored spirals read as eyes.
    cloud = [(7, 18), (7, 15), (9, 13), (12, 13), (14, 15), (12, 17), (10, 16), (10, 14), (12, 14)]
    tail = [(14, 14), (17, 13), (20, 13), (22, 15), (21, 17), (19, 16), (20, 14)]
    for path in (cloud, tail):
        for (x1, y1), (x2, y2) in zip(path, path[1:]):
            c.line(x1, y1, x2, y2, (112, 38, 30), 2)
        for (x1, y1), (x2, y2) in zip(path, path[1:]):
            c.line(x1, y1, x2, y2, (255, 214, 150))
    c.put(12, 13, (255, 246, 206))
    c.put(9, 13, (255, 236, 190))
    c.put(20, 13, (255, 246, 206))
    # A small ember caught on the front face.
    c.line(7, 22, 7, 20, (255, 196, 130))
    c.put(7, 19, (255, 240, 190))
    c.put(8, 22, (255, 160, 96))
    c.line(9, 23, 19, 21, (120, 42, 34))
    outline(c, (42, 16, 14))
    return c.rows


# ---------------------------------------------------------------- sand studies

def _shadow(c, color):
    c.poly([(3, 24), (7, 22), (25, 22), (29, 25), (23, 28), (7, 28)], color)


def _pile(c, dark, mid, light, crest, grains):
    # A triangular heap: lit left face, shadowed right face, sharp crest.
    c.poly([(5, 25), (8, 20), (12, 16), (17, 13), (21, 15), (25, 20), (27, 25), (19, 27), (8, 27)], dark)
    c.poly([(6, 24), (9, 19), (13, 15), (17, 13), (18, 24), (9, 26)], light)
    c.poly([(17, 13), (21, 15), (24, 19), (26, 24), (18, 26), (18, 24)], mid)
    c.poly([(9, 19), (13, 15), (17, 13), (21, 15), (18, 17), (13, 17)], light)
    for (x1, y1), (x2, y2) in (((9, 19), (13, 15)), ((13, 15), (17, 13)), ((17, 13), (21, 15))):
        c.line(x1, y1, x2, y2, crest)
    # A few darker grit bands on the lit face, not a smooth gradient.
    c.line(10, 21, 13, 19, dark)
    c.line(14, 20, 17, 18, dark)
    c.line(11, 24, 15, 22, dark)
    for x, y, col in grains:
        c.put(x, y, col)


def sand_stardust():
    """A: silver-blue grit heap with a bright crest and no container."""
    c = Canvas()
    _shadow(c, (18, 22, 30))
    bright, mid, dim = (216, 234, 255), (176, 200, 238), (118, 142, 180)
    grains = [
        (8, 21, mid), (10, 20, bright), (12, 18, mid), (14, 17, bright),
        (16, 16, mid), (18, 18, bright), (20, 19, mid), (22, 21, bright),
        (7, 23, mid), (9, 22, bright), (11, 21, mid), (13, 20, bright),
        (15, 19, mid), (17, 20, bright), (19, 21, mid), (21, 23, bright),
        (8, 25, dim), (11, 24, mid), (14, 23, dim), (16, 22, bright),
        (19, 24, mid), (22, 25, dim), (24, 23, dim), (5, 24, dim),
        (13, 22, bright), (18, 22, mid),
    ]
    _pile(c, (52, 62, 84), (88, 104, 136), (128, 148, 182), (190, 212, 244), grains)
    c.rect(15, 18, 16, 18, (226, 240, 255))
    c.rect(20, 20, 21, 20, (190, 214, 248))
    c.put(17, 12, (250, 254, 255))
    c.put(16, 13, (220, 236, 255))
    c.put(3, 24, (110, 132, 168))
    c.put(27, 24, (92, 112, 146))
    outline(c, (10, 12, 18))
    return c.rows


def sand_cinnabar():
    """B: cinnabar-gold grit heap with a warm crest and no container."""
    c = Canvas()
    _shadow(c, (34, 16, 18))
    bright, mid, dim = (255, 224, 146), (244, 168, 90), (196, 108, 62)
    grains = [
        (8, 21, mid), (10, 20, bright), (12, 18, mid), (14, 17, bright),
        (16, 16, mid), (18, 18, bright), (20, 19, mid), (22, 21, bright),
        (7, 23, mid), (9, 22, bright), (11, 21, mid), (13, 20, bright),
        (15, 19, mid), (17, 20, bright), (19, 21, mid), (21, 23, bright),
        (8, 25, dim), (11, 24, mid), (14, 23, dim), (16, 22, bright),
        (19, 24, mid), (22, 25, dim), (24, 23, dim), (5, 24, dim),
        (13, 22, bright), (18, 22, mid),
    ]
    _pile(c, (122, 44, 36), (168, 68, 46), (208, 100, 60), (246, 144, 86), grains)
    c.rect(15, 18, 16, 18, (255, 230, 160))
    c.rect(20, 20, 21, 20, (244, 168, 90))
    c.put(17, 12, (255, 250, 210))
    c.put(16, 13, (255, 220, 140))
    c.put(3, 24, (200, 112, 68))
    c.put(27, 24, (168, 82, 54))
    outline(c, (22, 14, 16))
    return c.rows


STUDIES = {
    "xuantie_a": ("玄铁 · 星陨玄铁", "深靛铁体，银蓝星图与细碎星屑；冷冽、克制。", xuantie_starfall),
    "xuantie_b": ("玄铁 · 雷纹玄铁", "炭灰铁体，紫电雷纹与刻痕；更硬朗、更凶。", xuantie_thunder),
    "chitong_a": ("赤铜锭 · 灵渠赤铜", "暖铜色配青碧灵渠与玉嵌心；导灵感最直接。", chitong_meridian),
    "chitong_b": ("赤铜锭 · 祥云赤铜", "赤金铜体，浅金祥云与丹火余光；更传统。", chitong_cloud),
    "sand_a": ("淬锋砂 · 星屑淬锋砂", "深色底上的一撮星蓝砂，银蓝颗粒与锋锐高光。", sand_stardust),
    "sand_b": ("淬锋砂 · 赤霞淬锋砂", "暖色底上的一撮朱砂，金红颗粒与火星。", sand_cinnabar),
}


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    manifest = []
    for key, (name, note, fn) in STUDIES.items():
        target = OUT / f"{key}.png"
        target.write_bytes(png(fn()))
        manifest.append({"id": key, "name": name, "note": note, "file": target.name})
        print(f"wrote {target.relative_to(ROOT)}")
    (OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
