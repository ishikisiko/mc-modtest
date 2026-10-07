"""Composes a portrait from the exported role maps with the shared rules: the executable spec of
the Java PortraitComposer. Must equal render.render pixel for pixel."""
from __future__ import annotations

import json
import os

from PIL import Image

from .person import Choice, Person
from .pix import Grid
from .render import to_image
from .roles import palette

DEFAULT_DIR = "src/main/resources/assets/myvillage/portrait"


class Maps:
    def __init__(self, root: str = DEFAULT_DIR):
        self.root = root
        self.manifest = json.load(open(os.path.join(root, "manifest.json"), encoding="utf-8"))
        self.roles = self.manifest["roles"]
        self.clears_face = set(self.manifest["clears_face"])
        self.cache = {}

    def load(self, layer: str, name: str):
        """(delta {(x, y): role}, mask set) or None when the variant has no file."""
        key = (layer, name)
        if key in self.cache:
            return self.cache[key]
        path = os.path.join(self.root, layer, name + ".png")
        if not os.path.exists(path):
            self.cache[key] = None
            return None
        px = Image.open(path).convert("RGBA").load()
        delta, mask = {}, set()
        for y in range(64):
            for x in range(64):
                r, g, b, a = px[x, y]
                if r:
                    delta[(x, y)] = self.roles[r - 1]
                if g:
                    mask.add((x, y))
        self.cache[key] = (delta, mask)
        return self.cache[key]


def paint(g: Grid, m) -> None:
    if m:
        for (x, y), role in m[0].items():
            g.put(x, y, role)


def replay(maps: Maps, p: Person, ch: Choice) -> Grid:
    g = "f" if p.female else "m"
    grid = Grid()
    old = "_old" if ch.old else ""
    behind = maps.load("behind", ch.back)
    paint(grid, behind)
    face = maps.load("face", f"{ch.face}_{g}{old}")
    paint(grid, face)
    face_mask = face[1]
    silhouette = behind[1] - (face_mask if ch.back in maps.clears_face else set())
    if ch.blush:
        paint(grid, maps.load("blush", "old" if ch.old else "normal"))
    if ch.nose:
        paint(grid, maps.load("nose", g))
    paint(grid, maps.load("mouth", ch.mouth + old))
    paint(grid, maps.load("eyes", f"{ch.eye_shape}_{g}"))
    locks = maps.load("locks", ch.back)
    paint(grid, locks)
    bangs = maps.load("bangs", f"{ch.front}_{ch.back}")
    paint(grid, bangs)
    finish(grid, face_mask, silhouette, locks[1], bangs[1])
    brows = maps.load("brows", f"{ch.brow}_{g}")
    for (x, y) in brows[0]:
        grid.put(x, y, "R" if grid.get(x, y) in "HhDIJ" else "h")
    paint(grid, maps.load("coat", f"{ch.robe}"))
    paint(grid, maps.load("knot", ch.back))
    if ch.headwear != "none":
        paint(grid, maps.load("headwear", f"{ch.headwear}_{ch.back}"))
    if "bandage" in ch.marks:
        paint(grid, maps.load("marks", f"bandage_{ch.front}_{ch.back}"))
    if "scar" in ch.marks:
        paint(grid, maps.load("marks", f"scar_{ch.back}"))
    return grid


def finish(g: Grid, face: set, silhouette: set, locks: set, bangs: set) -> None:
    front = locks | bangs
    skin = "SLBb"
    for x, y in bangs:
        if (x, y + 1) not in front and (x, y + 1) in face:
            g.put_if(x, y + 1, "s", skin)
            if (x, y + 2) in face and (x, y + 2) not in front:
                g.put_if(x, y + 2, "s", skin)
    for x, y in locks:
        if y < 30:
            continue
        q = (x + 1, y) if x < 32 else (x - 1, y)
        if q in face and q not in front:
            g.put_if(q[0], q[1], "s", "S")
    all_hair = silhouette | front
    for x, y in all_hair:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in all_hair:
                g.put(x, y, "D")
                break
    for x, y in front:
        for dx, dy in ((0, 1), (1, 0), (-1, 0)):
            q = (x + dx, y + dy)
            if q not in front and q in face:
                g.put(x, y, "D")
                break


def render_replay(maps: Maps, p: Person, ch: Choice) -> Image.Image:
    return to_image(replay(maps, p, ch), palette(p, ch), ch.dead)
