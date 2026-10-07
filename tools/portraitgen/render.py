"""Composes one portrait from hand-drawn parts (see parts_*.py) and resolves roles to colours.

Order: hair behind -> neck and face -> blush, nose, mouth -> eyes -> side locks -> bangs ->
shared hair finish (forehead shadow, outline, inner line) -> brows through the bangs -> coat ->
knot -> headwear -> marks -> grey for the dead."""
from __future__ import annotations

from PIL import Image

from . import parts_body, parts_eyes, parts_face, parts_hair_back, parts_hair_front
from .person import Choice, Person, assign
from .pix import Grid, outline
from .roles import palette


STEPS = ("behind", "face", "blush", "nose", "mouth", "eyes", "locks", "bangs", "finish", "brows", "coat", "knot",
         "headwear", "marks")


def compose(p: Person, ch: Choice, trace=None) -> Grid:
    """`trace(step, grid, ctx)` is called after every step in STEPS; `grid.journal` then holds every
    cell the step wrote (the exporter's stamps), whether or not the role changed."""
    g = Grid()
    ctx = {"male": not p.female}
    if trace:
        g.journal = {}

    def done(step):
        if trace:
            trace(step, g, ctx)
            g.journal = {}

    ctx["hair_back"] = parts_hair_back.behind(g, ch, ctx)
    done("behind")
    face = parts_face.draw(g, ch, p)
    ctx["face"] = face
    done("face")
    if ch.blush:
        parts_face.draw_blush(g, ch, p, face)
    done("blush")
    if ch.nose:
        parts_face.draw_nose(g, ch, p)
    done("nose")
    parts_face.draw_mouth(g, ch, p)
    done("mouth")
    parts_eyes.draw_eyes(g, ch, p)
    done("eyes")
    ctx["locks"] = parts_hair_back.locks(g, ch, ctx)
    done("locks")
    ctx["bangs"] = parts_hair_front.draw(g, ch, ctx)
    done("bangs")
    finish_hair(g, ctx)
    done("finish")
    parts_eyes.draw_brows(g, ch, p)
    done("brows")
    ctx["coat"] = parts_body.draw_coat(g, ch, p, ctx)
    done("coat")
    parts_hair_back.knot(g, ch, ctx)
    done("knot")
    parts_body.draw_headwear(g, ch, p, ctx)
    done("headwear")
    parts_body.draw_marks(g, ch, p, ctx)
    done("marks")
    return g


def finish_hair(g: Grid, ctx: dict) -> None:
    """The shadow the bangs cast on the forehead, the outline of all hair, the line where the
    front hair meets skin."""
    face = ctx["face"]
    bangs = ctx["bangs"]
    locks = ctx["locks"]
    front = locks | bangs
    skin = "SLBb"
    for x, y in list(bangs):
        if (x, y + 1) not in front and (x, y + 1) in face:
            g.put_if(x, y + 1, "s", skin)
            if (x, y + 2) in face and (x, y + 2) not in front:
                g.put_if(x, y + 2, "s", skin)
    # the side locks shade the jaw beside them
    for x, y in locks:
        if y < 30:
            continue
        q = (x + 1, y) if x < 32 else (x - 1, y)
        if q in face and q not in front:
            g.put_if(q[0], q[1], "s", "S")
    all_hair = ctx["hair_back"] | front
    g.paint(outline(all_hair), "D")
    for x, y in front:
        for dx, dy in ((0, 1), (1, 0), (-1, 0)):
            q = (x + dx, y + dy)
            if q not in front and q in face:
                g.put(x, y, "D")
                break


def to_image(g: Grid, pal: dict, dead: bool = False) -> Image.Image:
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    for y in range(64):
        for x in range(64):
            role = g.cells[y][x]
            if role == ".":
                continue
            c = pal[role]
            if dead:
                grey = int(0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2])
                c = (int(grey * 0.72 + 8), int(grey * 0.72 + 10), int(grey * 0.72 + 18), 255)
            px[x, y] = c
    return img


def render(p: Person, ch: Choice | None = None) -> Image.Image:
    ch = ch or assign(p)
    return to_image(compose(p, ch), palette(p, ch), ch.dead)
