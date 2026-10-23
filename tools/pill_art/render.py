"""32px art composition: support back -> body -> support front.

Supports know nothing about medicines or their palettes. Bodies share one anchor
and never draw packaging. Existing resource owners bake the resulting rows to
their original PNG paths; no runtime models, quality or gameplay data change.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

SIZE = 32
CENTER = (16, 12)
RADIUS = 8.5
SUPPORTS = ("leaf", "dish", "casket")


def catalogue():
    return json.loads(Path(__file__).with_name("catalogue.json").read_text())


def color(value):
    return tuple(bytes.fromhex(value)) + (255,)


def blank():
    return [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]


def polygon(rows, points, ink):
    for y in range(SIZE):
        for x in range(SIZE):
            inside = False
            for a, b in zip(points, points[1:] + points[:1]):
                if (a[1] > y) != (b[1] > y) and x < (b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0]:
                    inside = not inside
            if inside:
                rows[y][x] = color(ink)


def ellipse(rows, cx, cy, rx, ry, ink):
    for y in range(SIZE):
        for x in range(SIZE):
            if ((x-cx)/rx)**2 + ((y-cy)/ry)**2 <= 1:
                rows[y][x] = color(ink)


def line(rows, points, ink):
    for a, b in zip(points, points[1:]):
        steps = max(abs(b[0]-a[0]), abs(b[1]-a[1]), 1)
        for i in range(steps+1):
            x, y = (round(a[j]+(b[j]-a[j])*i/steps) for j in (0, 1))
            if 0 <= x < SIZE and 0 <= y < SIZE:
                rows[y][x] = color(ink)


def support_layers(kind):
    """Fresh independent layers, identical for every pill using this support."""
    back, front = blank(), blank()
    if kind == "leaf":
        polygon(back, [(2,16),(9,18),(24,17),(30,14),(28,24),(18,29),(7,27),(3,23)], "243b30")
        polygon(back, [(3,17),(11,20),(24,18),(29,15),(26,23),(17,27),(7,25)], "4f7950")
        polygon(back, [(3,17),(13,22),(26,21),(17,27),(7,25)], "385e40")
        line(back, [(4,18),(12,23),(22,24),(28,18)], "8d9f60")
        line(back, [(8,22),(8,25)], "6c8d52")
        line(back, [(21,22),(25,19)], "acc17b")
        line(front, [(5,24),(11,27),(18,28),(26,24)], "65905b")
        line(front, [(18,28),(23,29)], "a5ad70")
    elif kind == "dish":
        ellipse(back, 16, 23, 8, 4, "263d3c")
        ellipse(back, 16, 22, 14, 5, "344d4c")
        ellipse(back, 16, 20, 14, 5, "9ebdb0")
        ellipse(back, 16, 20, 12, 3.5, "e1e2c6")
        ellipse(back, 16, 20, 10, 2.5, "799c91")
        ellipse(back, 16, 20, 7, 1.5, "47695f")
        line(front, [(3,22),(7,25),(14,27),(20,27),(26,25),(29,22)], "587c73")
        line(front, [(4,22),(8,24),(15,25),(21,25),(27,23)], "d0d7b9")
        line(front, [(13,28),(19,28)], "a3b6a3")
    elif kind == "casket":
        polygon(back, [(2,19),(15,14),(30,19),(30,26),(16,31),(2,26)], "283c38")
        polygon(back, [(3,19),(15,15),(29,19),(16,25)], "b5b18a")
        polygon(back, [(5,19),(15,16),(27,19),(16,23)], "586f60")
        polygon(back, [(7,19),(16,17),(25,19),(16,22)], "c0b897")
        polygon(front, [(3,22),(16,26),(16,30),(3,26)], "465a49")
        polygon(front, [(16,26),(29,22),(29,26),(16,30)], "35483e")
        line(front, [(3,21),(16,25),(29,21)], "dbca8d")
        line(front, [(4,25),(16,29),(28,25)], "8a8b60")
        polygon(front, [(14,25),(18,25),(18,28),(14,28)], "c8a75e")
        line(front, [(16,26),(16,27)], "f3dea0")
    else:
        raise ValueError(f"Unknown pill support: {kind}")
    return back, front


# Authored organic motifs, independent of support shape and item identity.
MOTIFS = {
    "tide": [[(10,15),(13,17),(18,17),(21,14),(20,12),(18,12)]],
    "ember": [[(12,17),(15,18),(19,16),(20,12)],[(16,16),(17,13),(15,11)]],
    "cloud": [[(10,14),(13,14),(15,12),(18,12),(20,14),(18,16),(14,16)]],
    "fissure": [[(19,6),(17,10),(19,12),(15,15),(16,19)]],
    "orbit": [[(10,14),(12,17),(17,18),(21,15)],[(18,9),(20,11),(21,13)]],
    "branches": [[(13,18),(16,15),(17,11)],[(16,15),(20,14),(21,11)],[(16,15),(12,13)]],
    "flame": [[(11,17),(14,16),(15,12),(18,9),(17,14),(20,13),(19,17)]],
    "wind": [[(9,14),(14,16),(19,15),(22,12)],[(12,18),(17,19),(20,17)]],
    "stream": [[(11,7),(13,10),(17,12),(19,15),(18,18)],[(9,12),(11,15),(14,17)]],
    "ring": [[(11,13),(12,16),(16,18),(20,16),(21,12)],[(17,8),(20,10)]],
    "mend": [[(12,18),(15,16),(16,12),(20,9)],[(16,14),(19,16),(21,16)]]
}


def body_layer(spec):
    """A body-only transparent canvas anchored at CENTER; no support access."""
    rows = blank()
    base, light = color(spec["base"]), color(spec["light"])
    for y in range(SIZE):
        for x in range(SIZE):
            nx, ny = (x-CENTER[0])/RADIUS, (y-CENTER[1])/RADIUS
            d = nx*nx + ny*ny
            if d > 1:
                continue
            z = math.sqrt(1-d)
            brightness = max(0, -.38*nx-.48*ny+.7*z)
            # Banded values preserve deliberate pixels instead of noisy gradients.
            brightness = round(brightness*6)/6
            if d > .88:
                rgb = tuple(round(v*.42) for v in base[:3])
            else:
                rgb = tuple(round(v*.55 + (hi-v*.55)*brightness*.78) for v, hi in zip(base[:3], light[:3]))
            rows[y][x] = (*rgb, 255)
    motif = blank()
    for path in MOTIFS[spec["motif"]]:
        line(motif, path, spec["vein"])
    for y in range(SIZE):
        for x in range(SIZE):
            if motif[y][x][3] and rows[y][x][3]:
                rows[y][x] = motif[y][x]
    line(rows, [(12,7),(14,6),(16,6)], spec["light"])
    rows[8][11] = light
    return rows


def compose(body, support):
    if len(body) != SIZE or any(len(row) != SIZE for row in body):
        raise ValueError("Pill bodies must use the shared 32x32 canvas")
    back, front = support_layers(support)
    return [[next(layer[y][x] for layer in (front, body, back) if layer[y][x][3])
             if any(layer[y][x][3] for layer in (front, body, back)) else (0,0,0,0)
             for x in range(SIZE)] for y in range(SIZE)]


def pill_icon(kind):
    entry = catalogue()[kind]
    return compose(body_layer(entry["body"]), entry["support"])
