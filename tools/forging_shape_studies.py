#!/usr/bin/env python3
"""Shape studies for the three forging materials and the blood spirit ingot.

Four silhouettes per metal, four for the grit:
  slab   the current flat ingot
  bar    a long waisted bar
  cake   a round ingot cake
  stack  two slabs stacked
  grit   triangular heap / round dome / long ridge / abrasive wheel

These are HISTORICAL studies. The shipped convention is that every 锭 uses the
flat silhouette in tools/ingot_shape.py; the bar/cake/block variants must not be
wired into an ingot. Nothing here writes to src/main/resources.

Usage: python3 -m tools.forging_shape_studies
"""
from __future__ import annotations

import json
from pathlib import Path

from tools.gen_alchemy_assets import Canvas, png

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "out/preview/forging_shape_studies"


class Mat:
    def __init__(self, key, name, side, front, top, edge, pale, dark, accent, accent_dark, accent_hot):
        self.key = key
        self.name = name
        self.side = side
        self.front = front
        self.top = top
        self.edge = edge
        self.pale = pale
        self.dark = dark
        self.accent = accent
        self.accent_dark = accent_dark
        self.accent_hot = accent_hot


XUANTIE = Mat("xuantie", "玄铁", (30, 38, 52), (54, 66, 88), (84, 100, 128),
              (170, 196, 232), (214, 232, 255), (20, 26, 38),
              (214, 232, 255), (120, 148, 192), (238, 246, 255))
CHITONG = Mat("chitong", "赤铜锭", (88, 40, 30), (148, 72, 44), (204, 116, 64),
              (244, 170, 104), (255, 204, 140), (52, 22, 16),
              (94, 226, 188), (38, 104, 96), (200, 255, 236))
BLOOD = Mat("blood", "血灵锭", (30, 10, 18), (66, 16, 28), (100, 24, 38),
            (168, 68, 82), (204, 132, 144), (20, 6, 14),
            (198, 28, 54), (112, 14, 38), (255, 96, 104))
SAND = Mat("sand", "淬锋砂", (54, 66, 94), (88, 104, 136), (128, 148, 182),
           (190, 212, 244), (230, 244, 255), (18, 22, 30),
           (216, 234, 255), (118, 142, 180), (250, 254, 255))


# ------------------------------------------------------------------ motifs

def motif_star(c, p, x, y):
    c.put(x, y - 1, p.accent)
    c.put(x, y + 1, p.accent)
    c.put(x - 1, y, p.accent)
    c.put(x + 1, y, p.accent)
    c.put(x, y, p.accent_hot)
    c.put(x - 3, y - 1, p.accent_dark)
    c.put(x + 3, y + 1, p.accent_dark)


def motif_channel(c, p, x, y):
    c.line(x - 4, y, x - 1, y - 1, p.accent_dark)
    c.line(x - 1, y - 1, x + 1, y + 1, p.accent_dark)
    c.line(x + 1, y + 1, x + 4, y - 1, p.accent_dark)
    c.line(x - 4, y, x - 1, y - 1, p.accent)
    c.line(x - 1, y - 1, x + 1, y + 1, p.accent)
    c.line(x + 1, y + 1, x + 4, y - 1, p.accent)
    c.put(x - 4, y, p.accent_hot)
    c.put(x + 4, y - 1, p.accent_hot)
    c.put(x, y, p.accent_hot)


def motif_blood(c, p, x, y):
    c.line(x - 4, y, x - 1, y - 1, p.dark)
    c.line(x - 1, y - 1, x + 1, y + 1, p.dark)
    c.line(x + 1, y + 1, x + 4, y - 1, p.dark)
    c.line(x - 4, y, x - 1, y - 1, p.accent)
    c.line(x - 1, y - 1, x + 1, y + 1, p.accent)
    c.line(x + 1, y + 1, x + 4, y - 1, p.accent)
    c.put(x, y - 1, p.accent_hot)
    c.put(x + 1, y + 1, p.accent_hot)
    c.put(x + 2, y + 3, p.accent_dark)
    c.put(x + 2, y + 4, p.accent_hot)


MOTIFS = {"star": motif_star, "channel": motif_channel, "blood": motif_blood}


# ------------------------------------------------------------------ solids

SLAB_SIDE = [(3, 17), (9, 10), (24, 8), (29, 14), (23, 23), (7, 26), (3, 22)]
SLAB_FRONT = [(3, 17), (23, 15), (29, 14), (25, 22), (7, 26), (3, 22)]
SLAB_TOP = [(3, 17), (9, 10), (24, 8), (29, 14), (23, 18), (7, 22)]


def _mix(color, factor):
    return tuple(max(0, min(255, round(v * factor))) for v in color)


def slab(c, p, motif, *, dx=0, dy=0, tone=1.0):
    side = [(x + dx, y + dy) for x, y in SLAB_SIDE]
    front = [(x + dx, y + dy) for x, y in SLAB_FRONT]
    top = [(x + dx, y + dy) for x, y in SLAB_TOP]
    c.poly(side, _mix(p.side, tone))
    c.poly(front, _mix(p.front, tone))
    c.poly(top, _mix(p.top, tone))
    if tone > 0.8:
        c.line(9 + dx, 12 + dy, 23 + dx, 10 + dy, p.side)
        c.line(7 + dx, 20 + dy, 22 + dx, 16 + dy, p.front)
        c.line(9 + dx, 10 + dy, 24 + dx, 8 + dy, p.pale)
        c.line(24 + dx, 8 + dy, 29 + dx, 14 + dy, p.edge)
        c.line(3 + dx, 17 + dy, 9 + dx, 10 + dy, p.edge)
        c.line(3 + dx, 17 + dy, 3 + dx, 22 + dy, p.edge)
        c.line(3 + dx, 22 + dy, 7 + dx, 26 + dy, p.side)
        c.line(7 + dx, 26 + dy, 23 + dx, 23 + dy, p.dark)
        c.put(9 + dx, 10 + dy, p.pale)
        c.put(10 + dx, 12 + dy, p.edge)
        c.put(22 + dx, 9 + dy, p.edge)
        motif(c, p, 16 + dx, 15 + dy)


BAR_SIDE = [(2, 17), (7, 12), (25, 12), (30, 17), (27, 24), (5, 24)]
BAR_FRONT = [(2, 17), (7, 20), (25, 20), (30, 17), (27, 24), (5, 24)]
BAR_TOP = [(2, 17), (7, 12), (25, 12), (30, 17), (25, 20), (7, 20)]


def bar(c, p, motif):
    c.poly(BAR_SIDE, p.side)
    c.poly(BAR_FRONT, p.front)
    # Solid base band: without it the two bottom corners read as legs.
    c.poly([(5, 24), (27, 24), (26, 26), (6, 26)], p.front)
    c.poly(BAR_TOP, p.top)
    c.line(2, 17, 7, 12, p.pale)
    c.line(7, 12, 25, 12, p.pale)
    c.line(25, 12, 30, 17, p.pale)
    c.line(8, 14, 24, 14, p.edge)
    c.line(8, 19, 24, 19, p.side)
    c.line(7, 20, 5, 24, p.edge)
    c.line(25, 20, 27, 24, p.edge)
    c.line(7, 20, 25, 20, p.dark)
    c.line(6, 26, 26, 26, p.dark)
    motif(c, p, 16, 16)


def cake(c, p, motif):
    # Side wall, top face, then an engraved ring.
    c.ellipse(16, 22, 12, 6, p.side)
    c.rect(4, 20, 28, 22, p.side)
    c.ellipse(16, 20, 12, 5, p.front)
    c.ellipse(16, 20, 11, 4, p.top)
    c.ellipse(16, 20, 8, 3, p.dark)
    c.ellipse(16, 20, 7, 2, p.front)
    c.line(5, 19, 10, 17, p.pale)
    c.line(10, 17, 16, 16, p.pale)
    c.line(4, 24, 9, 26, p.edge)
    c.line(23, 26, 28, 24, p.dark)
    motif(c, p, 16, 20)


def block(c, p, motif):
    """Compact trapezoid ingot, like a struck gold bar."""
    c.poly([(7, 13), (25, 13), (28, 16), (4, 16)], p.top)
    c.poly([(4, 16), (28, 16), (25, 24), (7, 24)], p.front)
    c.poly([(7, 24), (25, 24), (24, 26), (8, 26)], p.side)
    c.line(7, 13, 25, 13, p.pale)
    c.line(25, 13, 28, 16, p.pale)
    c.line(4, 16, 7, 24, p.edge)
    c.line(28, 16, 25, 24, p.edge)
    c.line(7, 24, 25, 24, p.dark)
    c.line(8, 26, 24, 26, p.dark)
    c.line(8, 15, 24, 15, p.front)
    motif(c, p, 16, 15)


def _sand_speckles(c, p, spots):
    for x, y, col in spots:
        c.put(x, y, col)


def sand_heaps(c, p, kind):
    if kind == "grit":
        c.poly([(3, 24), (7, 22), (25, 22), (29, 25), (23, 28), (7, 28)], (18, 22, 30))
        c.poly([(5, 25), (8, 20), (12, 16), (17, 13), (21, 15), (25, 20), (27, 25), (19, 27), (8, 27)], p.side)
        c.poly([(6, 24), (9, 19), (13, 15), (17, 13), (18, 24), (9, 26)], p.top)
        c.poly([(17, 13), (21, 15), (24, 19), (26, 24), (18, 26), (18, 24)], p.front)
        c.poly([(9, 19), (13, 15), (17, 13), (21, 15), (18, 17), (13, 17)], p.top)
        c.line(9, 19, 13, 15, p.pale)
        c.line(13, 15, 17, 13, p.pale)
        c.line(17, 13, 21, 15, p.pale)
        _sand_speckles(c, p, (
            (8, 22, p.accent), (12, 19, p.accent), (16, 17, p.accent_hot),
            (20, 20, p.accent), (10, 24, p.accent_dark), (15, 22, p.accent),
            (19, 24, p.accent_dark), (23, 23, p.accent), (13, 22, p.accent),
            (17, 21, p.accent_dark), (21, 22, p.accent_hot), (9, 23, p.accent_dark),
        ))
        c.put(17, 12, p.accent_hot)
    elif kind == "dome":
        c.poly([(3, 24), (7, 22), (25, 22), (29, 25), (23, 28), (7, 28)], (18, 22, 30))
        c.poly([(4, 24), (7, 21), (10, 18), (13, 19), (16, 17), (19, 19), (22, 18), (25, 21), (28, 24), (22, 27), (8, 27)], p.side)
        c.poly([(5, 23), (8, 21), (11, 19), (13, 20), (16, 18), (19, 20), (22, 19), (25, 21), (26, 23), (21, 25), (9, 25)], p.front)
        # Only a thin lit crest; the rest is grit, not a faceted gem.
        c.line(8, 21, 11, 20, p.top)
        c.line(11, 20, 14, 19, p.top)
        c.line(14, 19, 17, 19, p.top)
        c.line(17, 19, 20, 20, p.top)
        _sand_speckles(c, p, (
            (6, 23, p.accent_dark), (9, 22, p.accent), (12, 21, p.accent_hot),
            (15, 20, p.accent), (18, 20, p.accent_dark), (21, 21, p.accent),
            (24, 22, p.accent_hot), (7, 25, p.accent_dark), (10, 24, p.accent),
            (13, 23, p.accent), (16, 22, p.accent_hot), (19, 23, p.accent),
            (22, 24, p.accent_dark), (25, 23, p.accent), (11, 22, p.accent_dark),
            (17, 22, p.accent), (14, 24, p.accent_dark), (20, 25, p.accent),
            (8, 24, p.accent_hot), (23, 24, p.accent), (12, 23, p.accent_dark),
            (18, 24, p.accent_hot),
        ))
        for x, y, col in ((3, 25, p.accent_dark), (28, 24, p.accent_dark),
                          (6, 26, p.accent), (26, 26, p.accent)):
            c.put(x, y, col)
    elif kind == "puck":
        # A compressed abrasive wheel: dark stone rim, grit ring, centre hole.
        c.ellipse(16, 22, 12, 6, (44, 52, 66))
        c.rect(4, 20, 28, 22, (44, 52, 66))
        c.ellipse(16, 20, 12, 5, p.side)
        c.ellipse(16, 20, 11, 4, p.top)
        c.ellipse(16, 20, 8, 3, p.dark)
        c.ellipse(16, 20, 7, 2, p.front)
        c.ellipse(16, 20, 2, 1, p.dark)
        c.line(5, 19, 10, 17, p.pale)
        c.line(10, 17, 16, 16, p.pale)
        c.line(4, 24, 9, 26, p.edge)
        _sand_speckles(c, p, (
            (6, 21, p.accent), (9, 19, p.accent_hot), (12, 18, p.accent),
            (15, 18, p.accent_dark), (18, 18, p.accent), (21, 19, p.accent_hot),
            (24, 21, p.accent), (26, 22, p.accent_dark), (8, 23, p.accent),
            (11, 22, p.accent_hot), (14, 21, p.accent), (18, 21, p.accent_dark),
            (21, 22, p.accent), (24, 23, p.accent_hot), (10, 24, p.accent_dark),
            (20, 24, p.accent),
        ))
    elif kind == "ridge":
        c.poly([(3, 24), (7, 22), (25, 22), (29, 25), (23, 28), (7, 28)], (18, 22, 30))
        c.poly([(4, 24), (7, 21), (11, 19), (16, 20), (21, 18), (25, 20), (28, 24), (22, 26), (8, 26)], p.side)
        c.poly([(5, 23), (8, 20), (12, 19), (16, 20), (20, 18), (24, 20), (26, 23), (21, 25), (9, 25)], p.front)
        c.line(8, 20, 12, 19, p.top)
        c.line(12, 19, 16, 20, p.top)
        c.line(16, 20, 20, 18, p.top)
        c.line(20, 18, 24, 20, p.top)
        _sand_speckles(c, p, (
            (6, 23, p.accent_dark), (9, 22, p.accent), (12, 21, p.accent_hot),
            (15, 21, p.accent), (18, 21, p.accent_dark), (21, 21, p.accent),
            (24, 22, p.accent_hot), (7, 25, p.accent_dark), (10, 24, p.accent),
            (13, 23, p.accent), (16, 23, p.accent_hot), (19, 23, p.accent),
            (22, 24, p.accent_dark), (25, 23, p.accent), (11, 22, p.accent_dark),
            (17, 22, p.accent), (14, 24, p.accent_dark), (20, 25, p.accent),
            (8, 24, p.accent_hot), (23, 24, p.accent), (13, 20, p.accent_dark),
            (19, 21, p.accent_hot),
        ))
        for x, y, col in ((3, 25, p.accent_dark), (28, 24, p.accent_dark),
                          (6, 26, p.accent), (26, 26, p.accent)):
            c.put(x, y, col)
    c.outline(p.dark)


SHAPES = {
    "xuantie": (XUANTIE, "star"),
    "chitong": (CHITONG, "channel"),
    "blood": (BLOOD, "blood"),
}


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    manifest = []

    def emit(key, label, fn):
        target = OUT / f"{key}.png"
        target.write_bytes(png(fn()))
        manifest.append({"id": key, "name": label, "file": target.name})
        print(f"wrote {target.relative_to(ROOT)}")

    def render(fn):
        canvas = Canvas()
        fn(canvas)
        return canvas.rows

    for key, (p, motif_name) in SHAPES.items():
        motif = MOTIFS[motif_name]
        emit(f"{key}_slab", f"{p.name} · 扁锭", lambda p=p, m=motif: render(lambda c: slab(c, p, m)))
        emit(f"{key}_bar", f"{p.name} · 长条锭", lambda p=p, m=motif: render(lambda c: bar(c, p, m)))
        emit(f"{key}_cake", f"{p.name} · 圆饼锭", lambda p=p, m=motif: render(lambda c: cake(c, p, m)))
        emit(f"{key}_block", f"{p.name} · 方锭", lambda p=p, m=motif: render(lambda c: block(c, p, m)))

    for kind, label in (("grit", "三角堆"), ("dome", "圆堆"), ("ridge", "长条砂垄"), ("puck", "砂轮")):
        emit(f"sand_{kind}", f"淬锋砂 · {label}",
             lambda k=kind: (lambda c: (sand_heaps(c, SAND, k), c.rows)[1])(Canvas()))

    (OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
