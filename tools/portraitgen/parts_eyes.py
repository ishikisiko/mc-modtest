"""Eyes and brows. Owner of this module: the eyes agent.

Geometry contract: the left eye's outer corner is x = 19, the right eye's is x = 44 (mirror: 63 - x);
rows 28 (crease) .. 38 (lower lid). Local lx runs 0 (outer corner) .. 8 (inner corner); ly 0 is
row 29 (the top lash row). Highlights sit at the top-LEFT of each iris in canvas terms (light from
the top-left), so they are not mirrored. Brows are drawn after the bangs in role R so they show
through the hair, rows 25..27. Roles: W w E e 1 2 3 4 P X x R (see roles.py)."""
from __future__ import annotations

from .pix import Grid

EYE_TOP = 29
L_OUTER = 19
R_OUTER = 44


def _round(g: Grid, ox: int, right: bool, female: bool) -> None:
    """The approved sample's eye: 9 wide, 10 tall with lashes, iris 6x7, big and small highlight."""
    def X(lx):
        return ox - lx if right else ox + lx

    def Y(ly):
        return EYE_TOP + ly

    for ly in range(2, 8):
        for lx in range(0, 9):
            g.put(X(lx), Y(ly), "w" if ly == 2 else "W")
    g.put(X(1), 37, "W")
    g.put(X(7), 37, "W")
    iris = {2: (3, 6), 3: (2, 7), 4: (2, 7), 5: (2, 7), 6: (2, 7), 7: (3, 6), 8: (4, 5)}
    tone = {2: "1", 3: "1", 4: "2", 5: "2", 6: "3", 7: "3", 8: "4"}
    for ly, (a, b) in iris.items():
        for lx in range(a, b + 1):
            g.put(X(lx), Y(ly), tone[ly])
    for ly in (3, 4, 5):
        g.put(X(4), Y(ly), "P")
        g.put(X(5), Y(ly), "P")
    hl = (X(2), X(3)) if not right else (X(7), X(6))
    for x in hl:
        g.put(x, 31, "X")
        g.put(x, 32, "X")
    g.put(X(7) if not right else X(2), 36, "x")
    for ly, a, b in ((0, 1, 7), (1, 0, 8)):
        for lx in range(a, b + 1):
            g.put(X(lx), Y(ly), "E")
    g.put(X(0), 31, "E")
    g.put(X(8), 31, "E")
    g.put(X(-1), 29, "E")
    g.put(X(-2), 28, "E")
    g.put(X(-1), 30, "E")
    for lx in range(1, 8):
        g.put(X(lx), 38, "e")
    for lx in range(2, 7):
        g.put(X(lx), 28, "s")


def _stamp(g: Grid, ox: int, right: bool, shape: dict) -> None:
    """Paint a template eye. `rows` maps a canvas row to 9 role chars for lx 0 (outer) .. 8 (inner),
    '.' = leave; drawn mirrored for the right eye. `hl` and `glint` are (lx, row) cells given for the
    LEFT eye; on the right eye they are reflected inside the iris span so the light still comes from
    the top-left of the canvas."""
    a, b = shape["span"]
    for y, line in shape["rows"].items():
        for lx, r in enumerate(line):
            if r != ".":
                g.put(ox - lx if right else ox + lx, y, r)
    for key, role in (("hl", "X"), ("glint", "x")):
        for lx, y in shape[key]:
            if right:
                lx = a + b - lx
            g.put(ox - lx if right else ox + lx, y, role)


# Templates, left eye, lx 0 = outer corner (x 19) .. 8 = inner corner (x 27). Women: two-row lash
# and an outer flick; men: one-row lash, no flick, one row shorter, a little less iris.
# almond: one row shorter than round, flat calm lash laid over the iris top; the default for men.
ALMOND_F = {"span": (2, 7), "hl": [(3, 32), (4, 32), (3, 33), (4, 33)], "glint": [(6, 36)], "rows": {
    29: "...ssss..",
    30: "E.EEEEEE.",
    31: "EEEEEEEEE",
    32: "Ew11PP11E",
    33: "WW22PP22W",
    34: "WW222222W",
    35: "WW333333W",
    36: "WWW3333WW",
    37: ".W..44.W.",
    38: ".eeeeeee.",
}}
ALMOND_M = {"span": (2, 7), "hl": [(3, 32), (4, 32), (3, 33), (4, 33)], "glint": [(6, 36)], "rows": {
    30: "..sssss..",
    31: ".EEEEEEEE",
    32: "Ew11PP11w",
    33: "WW22PP22W",
    34: "WW222222W",
    35: "WW333333W",
    36: "WWW3443WW",
    37: ".eeeeeee.",
}}
# round, man: the sample's eye with a one-row curved lash and the pale bottom row of iris dropped.
ROUND_M = {"span": (2, 7), "hl": [(3, 32), (4, 32), (3, 33), (4, 33)], "glint": [(6, 36)], "rows": {
    29: "..sssss..",
    30: ".EEEEEEE.",
    31: "Eww1111wE",
    32: "WW11PP11W",
    33: "WW22PP22W",
    34: "WW222222W",
    35: "WW333333W",
    36: "WWW3443WW",
    37: ".eeeeeee.",
}}
# sharp (吊眼): the lash rises to a heavy outer end, slopes down over the inner iris; the lower lid
# climbs to the outer corner, so the white is a slanted wedge and the iris shows less. Slant reads in
# rows 31..37 because bang tips may cover rows 28..30.
SHARP_F = {"span": (2, 7), "hl": [(3, 33), (4, 33), (3, 34), (4, 34)], "glint": [(6, 35)], "rows": {
    29: "EE.sss...",
    30: "EEEEE.ss.",
    31: "EEEEEEEEE",
    32: "Ew11PEEEE",
    33: "EW11PP11w",
    34: ".e22PP22W",
    35: "..e33333W",
    36: "...e443We",
    37: "....eeee.",
}}
SHARP_M = {"span": (2, 7), "hl": [(3, 33), (4, 33), (3, 34), (4, 34)], "glint": [(6, 35)], "rows": {
    30: "EEE.sss..",
    31: "EEEEEE...",
    32: "Ew11PEEEE",
    33: "EW11PP11w",
    34: ".e22PP22W",
    35: "..e3333We",
    36: "...eeeee.",
}}
# droopy (垂眼): the lash is high at the inner end and falls past the outer corner; round full iris,
# highlight on the pupil; soft.
DROOPY_F = {"span": (2, 7), "hl": [(3, 33), (4, 33), (3, 34), (4, 34)], "glint": [(6, 36)], "rows": {
    29: ".....sss.",
    30: "....EEEE.",
    31: "..EEEEEEE",
    32: "EEEE1PP1w",
    33: "EE11PP11W",
    34: "WW22PP22W",
    35: "WW222222W",
    36: "WW333333W",
    37: "eWW3443We",
    38: ".eeeeeee.",
}}
DROOPY_M = {"span": (2, 7), "hl": [(3, 33), (4, 33), (3, 34), (4, 34)], "glint": [(6, 35)], "rows": {
    30: ".....sss.",
    31: "....EEEEE",
    32: "..EE1PP1w",
    33: "EE11PP11W",
    34: "WW22PP22W",
    35: "WW333333W",
    36: "eWW3443We",
    37: ".eeeeeee.",
}}


def _shape(f_tpl, m_tpl):
    def draw(g: Grid, ox: int, right: bool, female: bool) -> None:
        _stamp(g, ox, right, f_tpl if female else m_tpl)
    return draw


def _round_any(g: Grid, ox: int, right: bool, female: bool) -> None:
    if female:
        _round(g, ox, right, female)
    else:
        _stamp(g, ox, right, ROUND_M)


EYE_SHAPES = {
    "round": _round_any,
    "almond": _shape(ALMOND_F, ALMOND_M),
    "sharp": _shape(SHARP_F, SHARP_M),
    "droopy": _shape(DROOPY_F, DROOPY_M),
}


def draw_eyes(g: Grid, ch, p) -> None:
    fn = EYE_SHAPES[ch.eye_shape]
    fn(g, L_OUTER, False, p.female)
    fn(g, R_OUTER, True, p.female)


# Left brow pixels (x, y); the right brow mirrors x -> 63 - x. Rows 25..27 only, x 20 (outer) .. 27
# (inner). Women: a one-pixel line. Men: a deliberate two-row bar, thickest toward the nose.
_ARCHED = [(20, 27), (21, 26), (22, 26), (23, 25), (24, 25), (25, 25), (26, 25), (27, 26)]
BROWS = {
    "arched": _ARCHED,
    "straight": [(20, 27), (21, 26), (22, 26), (23, 26), (24, 26), (25, 26), (26, 26), (27, 26)],
    "angry": [(20, 25), (21, 25), (22, 25), (23, 26), (24, 26), (25, 26), (26, 27), (27, 27)],
    "worried": [(20, 27), (21, 27), (22, 27), (23, 26), (24, 26), (25, 26), (26, 25), (27, 25)],
}


def _rows(spec: dict) -> list:
    return [(x, y) for y, (x0, x1) in spec.items() for x in range(x0, x1 + 1)]


BROWS_M = {
    "arched": _rows({25: (23, 26), 26: (21, 27), 27: (20, 21)}),
    "straight": _rows({26: (22, 27), 27: (20, 27)}),
    "angry": _rows({25: (20, 23), 26: (21, 26), 27: (24, 27)}),
    "worried": _rows({27: (20, 23), 26: (21, 26), 25: (24, 27)}),
}


def draw_brows(g: Grid, ch, p) -> None:
    pts = BROWS[ch.brow] if p.female else BROWS_M[ch.brow]
    for x, y in pts:
        for xx in (x, 63 - x):
            g.put(xx, y, "R" if g.get(xx, y) in "HhDIJ" else "h")
