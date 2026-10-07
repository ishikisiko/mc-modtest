"""Bangs (刘海). Owner of this module: the hair-front agent.

Geometry contract: the hair dome (drawn by parts_hair_back) is solid down to about y 13 and the
side locks cover x <= 18 and x >= 45; bangs hang from y 10 inside the dome down to pointed tips
between y 23 and 30, over the forehead x 19..44. Brows (rows 25..27) are drawn through the bangs
afterwards. Each style paints roles H (base), h (shadow), D (deep crevice), I (lit edge), J (sheen)
and returns the set of cells it painted; render.py then shades the forehead under the tips and
outlines the hair as a whole, so a style never draws the outer outline itself.

Every style except hime is a list of locks. A lock is {y: (left x, right x)}, either hand-placed
rows or made by `_lock` (a root span at y 10 bending towards a pointed tip). Later locks lie on top
of earlier ones. `_paint` shades them the same way the sample does: light comes from the top left, so each
lock has a lit stripe just inside its left edge and a shadow down its right edge that deepens to a
short crevice where the locks part above the tips. The eye box (x 21..25 and 38..42, rows 31..36)
is never painted."""
from __future__ import annotations

from .pix import Grid

EYE_BOX = {(x, y) for x in list(range(21, 26)) + list(range(38, 43)) for y in range(31, 37)}


# ---- the approved sample -------------------------------------------------------------------------

def _strand_rows(x0: int, x1: int, tip: int) -> dict:
    """A straight lock from y 10: full width, then a four-row taper to a two-pixel tip."""
    rows = {}
    for y in range(10, tip + 1):
        d = tip - y
        a, b = x0, x1
        if d == 3:
            a, b = x0, x1 - 1
        elif d == 2:
            a, b = x0 + 1, x1 - 1
        elif d == 1:
            a, b = x0 + 1, x1 - 2
        elif d == 0:
            a, b = x0 + 2, x1 - 2
        rows[y] = (a, b)
    return rows


HIME = [(19, 24, 29), (25, 29, 27), (30, 33, 25), (34, 38, 27), (39, 44, 29)]


def hime(g: Grid, ch, ctx) -> set:
    """姬发式: five pointed locks straight down, the centre one shortest (the approved sample's cut).
    Tones refined after "可以优化一点": the shadow down each lock stays soft (h) and turns deep (D)
    only for the three rows above where the locks part, the lit stripe stops halfway, and the
    stripe starts with a sheen pixel under the angel ring."""
    mask = set()
    for i, (x0, x1, tip) in enumerate(HIME):
        for y, (a, b) in _strand_rows(x0, x1, tip).items():
            for x in range(a, b + 1):
                mask.add((x, y))
                g.put(x, y, "H")
        for y in range(11, tip - 3):
            g.put(x1, y, "D" if y >= tip - 6 else "h")
        g.put(x1 - 1, tip - 4, "h")
        lx = x0 + (0 if i else 1)
        for y in range(12, tip - 8):
            g.put(lx, y, "I")
        g.put(lx, 11, "J" if i in (0, 1, 2) else "I")
    return mask


# ---- locks ---------------------------------------------------------------------------------------

def _round(v: float) -> int:
    return int(v + 0.5) if v >= 0 else -int(-v + 0.5)


def _lock(x0: int, x1: int, tx: int, ty: int, lead: int = 0, taper: int = 4, power: float = 1.6, y0: int = 10) -> dict:
    """One lock as {y: (left, right)}: root x0..x1 at y0, a point at (tx, ty). The leading edge
    (right for lead +1, left for lead -1, both for 0) bends towards the tip along t**power, so a lock
    hangs straight at the root and sweeps near the end; over the last `taper` rows the trailing edge
    closes in on the leading one to make the point."""
    rows = {}
    w = x1 - x0
    for y in range(y0, ty + 1):
        t = ((y - y0) / (ty - y0)) ** power if ty > y0 else 1.0
        k = max(0, y - (ty - taper)) / taper          # 0 above the taper, 1 at the tip
        if lead > 0:
            r = _round(x1 + (tx - x1) * t)
            l = _round(r - w + (w * k))
        elif lead < 0:
            l = _round(x0 + (tx - x0) * t)
            r = _round(l + w - (w * k))
        else:
            c = (x0 + x1) / 2 + (tx - (x0 + x1) / 2) * t
            half = w / 2 * (1 - k)
            l, r = _round(c - half), _round(c + half)
            if k >= 1:
                l = r = tx
        rows[y] = (min(l, r), max(l, r))
    return rows


def _mirror(rows: dict) -> dict:
    return {y: (63 - b, 63 - a) for y, (a, b) in rows.items()}


class Lock:
    """rows: {y: (left, right)}; lit: rows of the lit stripe (None = from 12 to halfway down);
    deep: rows of D in the shadow just above the taper; sheen: a J pixel tops the lit stripe."""

    def __init__(self, rows, lit=None, deep=3, sheen=False):
        self.rows = rows
        self.lit = lit
        self.deep = deep
        self.sheen = sheen


def _paint(g: Grid, ctx, locks) -> set:
    """Fill the locks (later ones on top), then shade each lock's visible part. Cells over the side
    locks are painted but left out of the returned mask, so the finish does not cast the forehead
    shadow onto the side locks under a tip that hangs over them."""
    owner = {}
    for i, lk in enumerate(locks):
        for y, (a, b) in lk.rows.items():
            for x in range(a, b + 1):
                if (x, y) not in EYE_BOX:
                    owner[(x, y)] = i
    for (x, y) in owner:
        g.put(x, y, "H")
    face = ctx.get("face", set())
    side = ctx.get("locks", set())

    def skin(x, y):
        return (x, y) in face and (x, y) not in owner and (x, y) not in side

    for i, lk in enumerate(locks):
        top, tip = min(lk.rows), max(lk.rows)
        w0 = lk.rows[top][1] - lk.rows[top][0] + 1
        taper = next((y for y in sorted(lk.rows) if lk.rows[y][1] - lk.rows[y][0] + 1 < w0 - 1), tip)
        lit = lk.lit or (12, top + (taper - top) // 2)
        for y in sorted(lk.rows):
            xs = [x for x in range(lk.rows[y][0], lk.rows[y][1] + 1) if owner.get((x, y)) == i]
            if len(xs) < 3:
                continue
            l, r = xs[0], xs[-1]
            if max(top, 12) < y < taper + 1 and not skin(r + 1, y):
                g.put(r, y, "D" if y > taper - lk.deep else "h")
            if lit[0] <= y <= lit[1]:
                lx = l if not skin(l - 1, y) else l + 1
                g.put(lx, y, "J" if lk.sheen and y == lit[0] else "I")
    for (x, y), i in owner.items():
        if y > 13 and any((q in side and q not in owner) for q in ((x - 1, y), (x + 1, y), (x, y + 1))):
            g.put(x, y, "D")
    return {c for c in owner if c not in side}


# ---- split 中分 M 字 -------------------------------------------------------------------------------

_SPLIT_INNER = {**{y: (27, 31) for y in range(10, 17)}, 17: (27, 30), 18: (27, 30), 19: (26, 30), 20: (26, 29),
                21: (26, 29), 22: (26, 28), 23: (26, 27), 24: (26, 26)}


def _split_locks():
    half = [
        (_lock(19, 23, 19, 28, lead=-1), dict(sheen=True)),          # outer: tip over the brow's end
        (_lock(23, 27, 22, 27, lead=-1), {}),                        # middle: over the brow
        (_SPLIT_INNER, dict(lit=(12, 16))),                          # inner: the M's middle stroke
    ]
    left = [Lock(r, **o) for r, o in half]
    right = [Lock(_mirror(r), **o) for r, o in reversed(half)]
    right[0].sheen = True
    right[0].lit = (12, 18)
    return left + right


def split(g: Grid, ch, ctx) -> set:
    """中分 M 字: parted at the centre, the locks sweep outward and down, the forehead shows in a V
    between the two inner locks, the outer tips reach the brows."""
    return _paint(g, ctx, _split_locks())


# ---- sweep 斜刘海 ---------------------------------------------------------------------------------

def _sweep_locks():
    return [Lock(_lock(39, 44, 44, 29, lead=1)),
            Lock(_lock(34, 39, 42, 28, lead=1)),
            Lock(_lock(29, 34, 38, 26, lead=1)),
            Lock(_lock(24, 29, 32, 24, lead=1), sheen=True),
            Lock(_lock(19, 25, 26, 21, lead=1), sheen=True)]


def sweep(g: Grid, ch, ctx) -> set:
    """斜刘海: every lock sweeps to the viewer's right, each lying over the next; the tips step down
    from the exposed left brow to the longest lock over the right brow."""
    return _paint(g, ctx, _sweep_locks())


# ---- curtain 长帘中分 -------------------------------------------------------------------------------

# drawn aside: the inner edge leaves the brow's outer end and clears the lash line (rows 28..30)
# by x 19, then the lock hangs beside the eye's outer corner over the side lock
_CURTAIN_LONG = {**{y: (21, 27) for y in range(10, 14)}, 14: (20, 27), 15: (20, 26), 16: (19, 26), 17: (19, 25),
                 18: (18, 25), 19: (18, 24), 20: (17, 24), 21: (17, 23), 22: (17, 23), 23: (16, 22), 24: (16, 22),
                 25: (16, 21), 26: (16, 21), 27: (16, 20), 28: (15, 19), 29: (15, 19), 30: (15, 19), 31: (15, 18),
                 32: (15, 18), 33: (15, 18), 34: (16, 18), 35: (16, 17), 36: (17, 17)}

# the short lock beside the part, swept aside so the forehead opens by y 18
_CURTAIN_SHORT = {**{y: (27, 31) for y in range(10, 14)}, 14: (26, 30), 15: (26, 29), 16: (25, 28), 17: (25, 27),
                  18: (24, 26), 19: (24, 25), 20: (24, 24)}


def _curtain_locks():
    long_l = dict(_CURTAIN_LONG)
    short_l = dict(_CURTAIN_SHORT)
    return [
        Lock(long_l, sheen=True, lit=(12, 27)),
        Lock(short_l, lit=(12, 14)),
        Lock(_mirror(short_l), sheen=True, lit=(12, 16)),
        Lock(_mirror(long_l), lit=(13, 22)),
    ]


def curtain(g: Grid, ch, ctx) -> set:
    """长帘中分: a centre part, the forehead open in the middle down to the brows, two long locks
    drawn aside past the brows' ends and down beside the eyes."""
    return _paint(g, ctx, _curtain_locks())


# ---- spiky 乱发 -----------------------------------------------------------------------------------

def _spiky_locks():
    return [
        Lock(_lock(39, 44, 47, 27, lead=1, power=1.4)),
        Lock(_lock(19, 24, 15, 24, lead=-1, power=1.4), sheen=True),
        Lock(_lock(31, 36, 36, 28, lead=1)),
        Lock(_lock(23, 28, 22, 29, lead=-1, power=2.0)),
        Lock(_lock(35, 40, 41, 23, lead=1, power=1.3), sheen=True),
        Lock(_lock(27, 32, 29, 24, lead=0), sheen=True),
    ]


def _flicks(g: Grid, ctx) -> set:
    """Two stray strands sticking out of the silhouette at the temples, at different heights and
    angles, hung from wherever the dome's edge is in their rows."""
    back = ctx.get("hair_back", set())
    cells = set()

    def edge(y, side):
        xs = [x for x, yy in back if yy == y]
        if not xs:
            return None
        return min(xs) if side < 0 else max(xs)

    for y, (a, b) in {15: (-2, -1), 16: (-3, -1), 17: (-4, -1), 18: (-5, -3), 19: (-5, -5)}.items():    # left: down, out
        e = edge(y, -1)
        if e is not None:
            cells |= {(e + dx, y) for dx in range(a, b + 1)}
    for y, (a, b) in {18: (1, 2), 19: (1, 3), 20: (2, 4), 21: (4, 4)}.items():          # right: lower, shorter
        e = edge(y, 1)
        if e is not None:
            cells |= {(e + dx, y) for dx in range(a, b + 1)}
    cells -= back
    g.paint(cells, "H")
    return cells


def spiky(g: Grid, ch, ctx) -> set:
    """乱发: sharp tips at different heights and angles, two flicking out past the face and two
    stray strands out of the silhouette at the temples."""
    return _paint(g, ctx, _spiky_locks()) | _flicks(g, ctx)


FRONTS = {
    "hime": hime,
    "split": split,
    "sweep": sweep,
    "curtain": curtain,
    "spiky": spiky,
}


def draw(g: Grid, ch, ctx) -> set:
    return FRONTS[ch.front](g, ch, ctx)
