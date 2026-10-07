"""Everything of the hair except the bangs: the silhouette behind the head, the dome over the skull
with its highlight, the side locks beside the face, the ends, and the knot or buns on top.
Owner of this module: the hair-back agent.

Geometry contract: the dome's top is y 1 (y 0 is reserved for a knot), the face sits at x 16..47
between y 14 and 50, the neck at x 27..36, the coat covers from y 54 (full width by y 60). A style has
three hooks, called at different times by render.py:
  behind(g, ch, ctx) -> mask   painted first (before the face): the whole silhouette incl. the dome,
                               back shading, the dome's highlight, the right-side shadow and rim light
  locks(g, ch, ctx) -> mask    painted after the face and eyes, before the bangs: the side locks
                               (鬓发) that cover the face's edges, with their own lit/shadow stripes
                               and a D line against the hair behind them
  knot(g, ch, ctx)             painted after the bangs: a top-knot, half-up knot or twin buns, with
                               their own outline
render.py outlines the union of all hair afterwards and shades the forehead under the bangs.
Roles: H base, h shadow, D deep/outline, I light, J sheen."""
from __future__ import annotations

from .pix import Grid, mask_of, mirror, outline, rng


# ---- the approved sample: long hime hair with a half-up knot ---------------------------------------

_LONG = {1: (27, 36), 2: (23, 40), 3: (20, 43), 4: (18, 45), 5: (16, 47), 6: (15, 48), 7: (14, 49), 8: (13, 50),
         9: (12, 51), 10: (11, 52)}
_LONG.update(rng(11, 13, 10, 53))
_LONG.update(rng(14, 44, 9, 54))
_LONG.update(rng(45, 53, 10, 53))
_LONG_ENDS = {54: [(10, 12), (15, 20), (23, 26), (37, 40), (43, 48), (51, 53)],
              55: [(11, 11), (17, 18), (25, 25), (38, 38), (45, 46), (52, 52)]}

_LOCK = {12: (12, 16), 13: (11, 17)}
_LOCK.update(rng(14, 30, 11, 18))
_LOCK.update(rng(31, 40, 12, 18))
_LOCK.update(rng(41, 46, 13, 18))
_LOCK.update({47: (13, 17), 48: (14, 17), 49: (14, 16), 50: (15, 16), 51: (15, 15), 52: (16, 16)})


def _dome_highlight(g: Grid) -> None:
    """An angel ring with zigzag edges across the crown."""
    for x in range(18, 45):
        if (x - 18) % 6 < 4:
            g.put(x, 7, "I")
    g.span(8, 14, 48, "I")
    g.span(8, 20, 28, "J")
    for x in range(16, 47):
        if (x - 16) % 6 < 2:
            g.put(x, 9, "I")


def _right_shadow(g: Grid, rows: dict, y0: int, y1: int, rim=(14, 50)) -> None:
    for y in range(y0, y1 + 1):
        if y not in rows:
            continue
        x1 = rows[y][1]
        g.put(x1 - 1, y, "I" if rim[0] <= y <= rim[1] else "h")
        g.put(x1 - 2, y, "h")
        g.put(x1 - 3, y, "h")
        g.put(x1 - 4, y, "h")


def long_behind(g: Grid, ch, ctx) -> set:
    mask = mask_of(_LONG)
    for y, segs in _LONG_ENDS.items():
        for a, b in segs:
            for x in range(a, b + 1):
                mask.add((x, y))
    g.paint(mask, "H")
    for x, y in mask:
        if 16 <= y and (x <= 19 or x >= 44):
            g.put(x, y, "h")
    _dome_highlight(g)
    _right_shadow(g, _LONG, 10, 53)
    # the mass below the jaw splits into the locks its ends show: crevices leading to the gaps, a lit stripe
    _crevices(g, (22, 41), 46, 53, "h", "HI")
    _crevices(g, (21, 42), 50, 53, "h", "HI")
    _crevices(g, (24,), 48, 52, "I", "H")
    return mask


def long_locks(g: Grid, ch, ctx) -> set:
    left = mask_of(_LOCK)
    both = left | mirror(left)
    g.paint(both, "H")
    for y in range(14, 36):
        g.put(13, y, "I")
        g.put(14, y, "I")
    for y in range(44, 51):
        g.put(14, y, "I")
    for y in range(16, 20):
        g.put(13, y, "J")                      # sheen where the lit lock turns towards the light
    for y, (x0, _) in _LOCK.items():
        if y >= 13:
            g.put(x0, y, "D")
            g.put(63 - x0, y, "D")
    for y in range(18, 47):
        g.put(18, y, "h")
        g.put(45, y, "h")
        g.put(44, y, "h")
    for y in range(14, 40):
        g.put(50, y, "h")
        g.put(51, y, "h")
    return both


def half_up_knot(g: Grid, ch, ctx) -> None:
    for y, (x0, x1) in {0: (29, 34), 1: (27, 36)}.items():
        g.span(y, x0, x1, "H")
    g.span(0, 30, 33, "I")
    for x, y in ((28, 1), (35, 1), (29, 0), (34, 0)):
        g.put(x, y, "D")


def no_knot(g: Grid, ch, ctx) -> None:
    return None



# ---- shared helpers ------------------------------------------------------------------------------

_DOME = {y: _LONG[y] for y in range(1, 11)}


def _with_ends(rows: dict, ends: dict) -> set:
    mask = mask_of(rows)
    for y, segs in ends.items():
        for a, b in segs:
            for x in range(a, b + 1):
                mask.add((x, y))
    return mask


def _line(g: Grid, pts, role: str, only: str = "HhIJ") -> None:
    for x, y in pts:
        g.put_if(x, y, role, only)


def _crevices(g: Grid, cols, y0: int, y1: int, role: str = "h", only: str = "HI") -> None:
    for x in cols:
        for y in range(y0, y1 + 1):
            g.put_if(x, y, role, only)


def _ear(g: Grid, right: bool) -> None:
    """A small ear on the side of the head, x 13..16 (mirrored 47..50), rows 27..34: K outline,
    skin with one pixel of inner shadow, joined to the cheek (the face's outline is opened there)."""
    X = (lambda x: 63 - x) if right else (lambda x: x)
    cells = {27: (14, 15), 28: (13, 16), 29: (13, 16), 30: (13, 16), 31: (13, 16), 32: (13, 16), 33: (14, 16),
             34: (15, 16)}
    for y, (a, b) in cells.items():
        for x in range(a, b + 1):
            g.put(X(x), y, "s" if right else "S")
    for x, y in ((14, 27), (15, 27), (13, 28), (13, 29), (13, 30), (13, 31), (13, 32), (14, 33), (15, 34), (16, 34)):
        g.put(X(x), y, "K")
    g.put(X(16), 27, "K")
    g.put(X(15), 29, "s" if not right else "T")       # the inner fold
    g.put(X(15), 30, "s")
    if not right:
        g.put(X(14), 31, "s")
    g.put(X(16), 33, "s")


def _lock_shade(g: Grid, rows: dict, lit=(14, 35), sheen=(15, 18), inner_shadow=(18, 46)) -> set:
    """Paint a pair of side locks from the left lock's rows: lit stripes on the left lock, a J sheen,
    a D line on the outer edge against the hair behind, shadow on the inner edges and the right lock."""
    left = mask_of(rows)
    both = left | mirror(left)
    g.paint(both, "H")
    for y in range(lit[0], lit[1] + 1):
        g.put_if(13, y, "I", "H")
        g.put_if(14, y, "I", "H")
    for y in range(sheen[0], sheen[1] + 1):
        g.put_if(13, y, "J", "I")
    for y, (x0, _) in rows.items():
        if y >= 13:
            g.put(x0, y, "D")
            g.put(63 - x0, y, "D")
    for y in range(inner_shadow[0], inner_shadow[1] + 1):
        if y in rows:
            g.put_if(rows[y][1], y, "h", "HI")
            g.put_if(63 - rows[y][1], y, "h", "HI")
            g.put_if(63 - rows[y][1] + 1, y, "h", "HI")
    return both


def _clear_face(ctx) -> None:
    """For silhouettes that end above the chin: the part of the back mass the face now covers is not
    hair any more, so the shared outline runs along the jaw instead of across it."""
    ctx["hair_back"] = ctx["hair_back"] - ctx["face"]


def _back_shade(g: Grid, mask: set, rows: dict, y_rim=(14, 50)) -> None:
    for x, y in mask:
        if 16 <= y and (x <= 19 or x >= 44):
            g.put(x, y, "h")
    _right_shadow(g, rows, 10, max(rows), rim=y_rim)


# ---- short: ends at the jaw in points, sideburns stop above the ear, ears show -------------------------

_SHORT = dict(_DOME)
_SHORT.update(rng(11, 30, 10, 53))
_SHORT.update(rng(31, 36, 11, 52))
_SHORT.update(rng(37, 40, 12, 51))
_SHORT.update(rng(41, 44, 13, 50))
_SHORT.update(rng(45, 46, 14, 49))
_SHORT_ENDS = {47: [(14, 17), (20, 25), (38, 43), (46, 49)],
               48: [(15, 16), (21, 24), (39, 42), (47, 48)],
               49: [(22, 23), (40, 41)]}
_SHORT_LOCK = {12: (12, 17), 13: (11, 18)}
_SHORT_LOCK.update(rng(14, 20, 11, 18))
_SHORT_LOCK.update({21: (12, 18), 22: (12, 18), 23: (13, 18), 24: (14, 18), 25: (15, 18), 26: (16, 18), 27: (17, 18)})


def short_behind(g: Grid, ch, ctx) -> set:
    mask = _with_ends(_SHORT, _SHORT_ENDS)
    g.paint(mask, "H")
    _back_shade(g, mask, _SHORT, y_rim=(14, 44))
    _dome_highlight(g)
    # the hair behind and below the ears: a lit stripe on the left, crevices splitting it into points
    for y in range(35, 46):
        g.put(12, y, "I")
    _crevices(g, (16,), 40, 47, "D", "Hh")
    _crevices(g, (47,), 40, 47, "D", "Hh")
    _crevices(g, (22,), 44, 48, "h", "H")
    _crevices(g, (41,), 44, 48, "D", "Hh")
    return mask


def short_locks(g: Grid, ch, ctx) -> set:
    _clear_face(ctx)
    _ear(g, False)
    _ear(g, True)
    return _lock_shade(g, _SHORT_LOCK, lit=(14, 22), sheen=(15, 17), inner_shadow=(18, 27))


# ---- ponytail: the head's hair pulled back, a high tail falling behind the right shoulder ----------------

_PONY = dict(_DOME)
_PONY.update(rng(11, 36, 10, 53))
_PONY.update(rng(37, 38, 11, 52))
_PONY.update({39: (12, 51), 40: (13, 50), 41: (15, 48), 42: (17, 46)})
_TAIL = {0: (44, 48), 1: (43, 51), 2: (44, 54), 3: (46, 56), 4: (48, 57), 5: (50, 58), 6: (51, 59), 7: (52, 59)}
_TAIL.update(rng(8, 12, 53, 60))
_TAIL.update(rng(13, 20, 54, 60))
_TAIL.update(rng(21, 44, 55, 61))
_TAIL.update(rng(45, 50, 56, 61))
_TAIL.update({51: (56, 60), 52: (56, 60), 53: (56, 60), 54: (57, 60), 55: (57, 60), 56: (57, 60), 57: (58, 60),
              58: (58, 60), 59: (58, 59), 60: (59, 59)})
_TAIL_ENDS = {47: [(54, 55)], 48: [(54, 55)], 49: [(54, 55)], 50: [(54, 55)], 51: [(55, 55)], 52: [(55, 55)]}
_PONY_LOCK = {12: (12, 16), 13: (11, 17)}
_PONY_LOCK.update(rng(14, 30, 11, 18))
_PONY_LOCK.update(rng(31, 35, 12, 18))
_PONY_LOCK.update({36: (13, 18), 37: (14, 18), 38: (15, 18), 39: (16, 18), 40: (17, 17)})


def ponytail_behind(g: Grid, ch, ctx) -> set:
    tail = _with_ends(_TAIL, _TAIL_ENDS)
    g.paint(tail, "H")
    for y, (x0, x1) in _TAIL.items():
        g.put(x1, y, "h")
        g.put(x1 - 1, y, "h")
        if y >= 9:
            g.put(x0 + 1, y, "I")
    _crevices(g, (57,), 14, 50, "h", "HI")
    _crevices(g, (58,), 30, 56, "D", "Hh")
    for x, y in ((46, 1), (47, 1), (48, 2), (49, 2), (50, 3), (51, 3), (52, 4)):
        g.put(x, y, "I")
    for x, y in ((48, 1), (49, 1), (51, 2), (53, 4), (54, 5)):
        g.put(x, y, "J")
    head = mask_of(_PONY)
    g.paint(head, "H")
    _back_shade(g, head, _PONY, y_rim=(14, 38))
    _dome_highlight(g)
    # the head stands in front of the tail
    for y in range(3, 37):
        if y in _PONY and (_PONY[y][1] + 1, y) in tail:
            g.put(_PONY[y][1], y, "D")
    return tail | head


def ponytail_locks(g: Grid, ch, ctx) -> set:
    _clear_face(ctx)
    return _lock_shade(g, _PONY_LOCK, lit=(14, 34), sheen=(15, 19), inner_shadow=(18, 40))


def ponytail_tie(g: Grid, ch, ctx) -> None:
    """A cord tied round the root of the tail where it leaves the crown, with one loose end."""
    for x, y in ((43, 1), (44, 1), (44, 2), (45, 2), (45, 3), (46, 3)):
        g.put(x, y, "A")
    for x, y in ((42, 1), (43, 2), (44, 3), (45, 4), (46, 4)):
        g.put(x, y, "a")
    g.put(44, 0, "a")


# ---- twin buns 双髻: two buns on the upper sides of the dome, ribbons, short back hair -----------------

_TWIN = {3: (26, 37), 4: (22, 41), 5: (19, 44), 6: (17, 46), 7: (15, 48), 8: (14, 49), 9: (13, 50), 10: (12, 51)}
_TWIN.update({y: _SHORT[y] for y in _SHORT if y >= 11})
_TWIN_ENDS = _SHORT_ENDS
_BUN_L = {0: (16, 21), 1: (14, 23), 2: (13, 24), 3: (13, 25), 4: (13, 25), 5: (13, 25), 6: (13, 25), 7: (14, 24),
          8: (15, 23)}
_TWIN_LOCK = {12: (12, 16), 13: (11, 17)}
_TWIN_LOCK.update(rng(14, 26, 11, 18))
_TWIN_LOCK.update(rng(27, 33, 12, 18))
_TWIN_LOCK.update({34: (13, 18), 35: (14, 18), 36: (14, 18), 37: (15, 18), 38: (15, 17), 39: (16, 17), 40: (16, 16),
                   41: (17, 17)})


def _bun_shade(g: Grid, right: bool) -> set:
    """One round bun: lit crescent on the upper left, shaded lower right, a wrap crevice curling round it."""
    X = (lambda x: 63 - x) if right else (lambda x: x)
    cells = {(X(x), y) for x, y in mask_of(_BUN_L)}
    g.paint(cells, "H")
    ox = 0 if not right else 25                # both buns lit from the top-left: shift, don't mirror
    for x, y in cells:
        lx = x - ox
        if (lx >= 22 and y >= 3) or (lx >= 23 and y >= 2) or (y >= 7 and lx >= 18):
            g.put(x, y, "h")
    for lx, y in ((16, 1), (17, 1), (18, 1), (19, 1), (15, 2), (14, 3), (14, 4), (14, 5), (15, 3)):
        g.put(lx + ox, y, "I")
    for lx, y in ((16, 2), (17, 2), (15, 4)):
        g.put(lx + ox, y, "J")
    for lx, y in ((17, 5), (18, 4), (19, 4), (20, 4), (21, 5), (22, 5), (23, 6)):
        g.put(lx + ox, y, "h")
    for lx, y in ((16, 6), (17, 6)):
        g.put(lx + ox, y, "I")
    g.paint(outline(cells), "D")
    for lx in range(17, 22):                   # the bun's foot sinks into the dome: a shadow, not a line
        g.put(lx + ox, 8, "h")
    return cells


def twin_buns_behind(g: Grid, ch, ctx) -> set:
    mask = _with_ends(_TWIN, _TWIN_ENDS)
    g.paint(mask, "H")
    _back_shade(g, mask, _TWIN, y_rim=(14, 44))
    _dome_highlight(g)
    for x, y in ((18, 7), (19, 7), (44, 7), (45, 7)):
        g.put(x, y, "H")
    _crevices(g, (16,), 40, 47, "D", "Hh")
    _crevices(g, (47,), 40, 47, "D", "Hh")
    _crevices(g, (22,), 44, 48, "h", "H")
    _crevices(g, (41,), 44, 48, "D", "Hh")
    buns = _bun_shade(g, False) | _bun_shade(g, True)
    return mask | buns


def twin_buns_locks(g: Grid, ch, ctx) -> set:
    _clear_face(ctx)
    return _lock_shade(g, _TWIN_LOCK, lit=(14, 30), sheen=(15, 18), inner_shadow=(18, 41))


def twin_buns_ribbons(g: Grid, ch, ctx) -> None:
    """A ribbon bow at the outer foot of each bun, two short streamers hanging over the hair below it."""
    for right in (False, True):
        X = (lambda x: 63 - x) if right else (lambda x: x)
        for x, y in ((13, 6), (13, 7), (14, 7), (14, 8), (15, 8), (11, 6), (11, 7), (12, 8), (12, 9), (11, 10),
                     (11, 11), (13, 10), (14, 11), (14, 12), (10, 12)):
            g.put(X(x), y, "A")
        for x, y in ((12, 6), (12, 7), (13, 8), (13, 9), (12, 10), (12, 11), (11, 12), (14, 13), (10, 13), (15, 9),
                     (16, 9)):
            g.put(X(x), y, "a")


# ---- bun 道髻: hair drawn up into a top-knot, strands rising to it, ends at the nape, slim locks ---------

_BUNHEAD = {3: (25, 38), 4: (21, 42), 5: (18, 45), 6: (16, 47), 7: (15, 48), 8: (14, 49), 9: (13, 50), 10: (12, 51)}
_BUNHEAD.update(rng(11, 33, 11, 52))
_BUNHEAD.update(rng(34, 37, 12, 51))
_BUNHEAD.update({38: (13, 50), 39: (13, 50), 40: (14, 49), 41: (15, 48), 42: (17, 46), 43: (19, 44)})
_BUN_LOCK = {12: (13, 18), 13: (14, 18), 14: (15, 18), 15: (15, 18), 16: (16, 18)}
_BUN_LOCK.update(rng(17, 31, 16, 18))
_BUN_LOCK.update({32: (17, 18), 33: (17, 18), 34: (17, 18), 35: (17, 17), 36: (18, 18)})
_KNOT = {0: (29, 34), 1: (28, 35), 2: (27, 36), 3: (27, 36), 4: (28, 35)}


_BUN_CREVICES = [((14, 10), (15, 9), (16, 8), (17, 7), (18, 7), (19, 6), (20, 6), (21, 5), (22, 5), (23, 4)),
                 ((19, 11), (20, 10), (21, 9), (22, 8), (23, 8), (24, 7), (25, 6), (26, 6), (27, 5)),
                 ((25, 11), (26, 10), (26, 9), (27, 8), (28, 7), (28, 6), (29, 5))]
_BUN_LIT = ((17, 9), (18, 9), (19, 8), (20, 8), (21, 7), (22, 7), (23, 6), (24, 6), (25, 5), (26, 5),
            (22, 10), (23, 10), (23, 9), (24, 9), (25, 8), (26, 7))


def bun_behind(g: Grid, ch, ctx) -> set:
    mask = mask_of(_BUNHEAD)
    g.paint(mask, "H")
    _back_shade(g, mask, _BUNHEAD, y_rim=(14, 40))
    # strands swept up to the knot: three curved crevices a side, lit between them on the left only
    for x, y in _BUN_LIT:
        g.put(x, y, "I")
    for x, y in ((24, 6), (25, 5), (23, 6)):
        g.put(x, y, "J")
    for line in _BUN_CREVICES:
        for x, y in line:
            g.put(x, y, "h")
            g.put(63 - x, y, "h")
    for x, y in ((43, 9), (44, 9), (42, 8), (41, 8), (40, 7), (39, 7), (38, 6), (37, 6)):
        g.put_if(x, y, "h", "H")
    return mask


def bun_locks(g: Grid, ch, ctx) -> set:
    _clear_face(ctx)
    _ear(g, False)
    _ear(g, True)
    return _lock_shade(g, _BUN_LOCK, lit=(15, 30), sheen=(16, 18), inner_shadow=(14, 36))


def bun_knot(g: Grid, ch, ctx) -> None:
    """道髻: a round knot on the crown, wound hair shown by a crevice across it, tied off at its foot."""
    km = mask_of(_KNOT)
    g.paint(km, "H")
    for x, y in ((29, 1), (30, 1), (31, 1), (28, 2), (29, 2), (30, 0), (31, 0)):
        g.put(x, y, "I")
    g.put(30, 1, "J")
    for x, y in ((34, 1), (35, 2), (34, 2), (35, 3), (34, 3), (33, 3)):
        g.put(x, y, "h")
    for x, y in ((29, 3), (30, 3), (31, 2), (32, 2)):
        g.put(x, y, "h")
    g.paint(outline(km), "D")
    g.span(4, 29, 34, "D")


class Style:
    def __init__(self, behind, locks, knot):
        self.behind = behind
        self.locks = locks
        self.knot = knot


BACKS = {
    "half_up": Style(long_behind, long_locks, half_up_knot),
    "long": Style(long_behind, long_locks, no_knot),
    "short": Style(short_behind, short_locks, no_knot),
    "ponytail": Style(ponytail_behind, ponytail_locks, ponytail_tie),
    "twin_buns": Style(twin_buns_behind, twin_buns_locks, twin_buns_ribbons),
    "bun": Style(bun_behind, bun_locks, bun_knot),
}


def behind(g: Grid, ch, ctx) -> set:
    return BACKS[ch.back].behind(g, ch, ctx)


def locks(g: Grid, ch, ctx) -> set:
    return BACKS[ch.back].locks(g, ch, ctx)


def knot(g: Grid, ch, ctx) -> None:
    BACKS[ch.back].knot(g, ch, ctx)
