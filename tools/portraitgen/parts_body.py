"""The robe and collar, headwear, and marks (bandage, scar). Owner of this module: the body agent.

Geometry contract: the coat begins at y 54 under a neck x 27..36 and reaches the full width by
y 60; the collar is a crossed 交领 with the viewer's-left band on top (右衽) in trim roles G/g over an
inner garment N/u. Headwear sits on the hair: the knot area is y 0..3 at x 27..36, the dome's right
flank around (40..46, 2..6), the forehead band rows 11..13 across x 13..50. Marks go over the face
(a scar on the right cheek, under the eye's outer corner) and over the hair (a bandage across the brow).
Roles: O c C Q (coat), g G (trim), u N (inner), a A (sect accent), Z z (jade), V (red cord),
F f (bandage), Y (scar); the palette decides the actual colours from the realm tier and sect.

Robes by realm tier (the palette gives each tier its cloth; the drawing gives it its cut):
  plain   炼气  a narrow 2 px dark collar band (g only, no glint), the underneath band tucked away below
                the crossing so it reads as a 交领 edge, a soft sleeve fold, nothing else
  dyed    筑基  a contrasting 2 px band (G) with a thin light piping (N) outside and a dark line (g)
                inside, the underneath band tucked away below the crossing
  brocade 金丹  the sample's 4 px gold band, a jade pendant (Z/z on a red cord) at the crossing and
                a few gold embroidery dots on the chest
  court   元婴  a stiff 5 px double-edged band that stands up beside the neck (y 52..53), a gold 云肩
                yoke line along the shoulders and a cloud on each breast; court_f keeps the approved
                sample's coverage exactly (coat_crossed is the sample's coat, pixel for pixel)
Men's shoulders are wider and squarer (a steeper rise off the neck, a flat top, a rounded corner)."""
from __future__ import annotations

from .pix import Grid, mask_of, outline


# ---- coat bodies -----------------------------------------------------------------------------------

# the approved sample (women): soft sloping shoulders
_COAT_F = {54: (25, 38), 55: (22, 41), 56: (19, 44), 57: (16, 47), 58: (13, 50), 59: (10, 53), 60: (7, 56), 61: (5, 58),
           62: (4, 59), 63: (3, 60)}
# men: a steeper rise off the neck, a flatter, wider top and a rounded corner at the canvas edge
_COAT_M = {54: (24, 39), 55: (20, 43), 56: (15, 48), 57: (10, 53), 58: (6, 57), 59: (4, 59), 60: (2, 61), 61: (1, 62),
           62: (0, 63), 63: (0, 63)}
_INNER = {54: (28, 35), 55: (28, 35), 56: (29, 34), 57: (29, 34), 58: (30, 33), 59: (30, 33), 60: (31, 32)}
SKIN = "SsTKLBb"


def _body(g: Grid, rows: dict, male: bool) -> set:
    """Fill, outline the row ends, a lit stripe on the left and shade on the right (light from the top-left)."""
    g.spans(rows, "C")
    for y, (x0, x1) in rows.items():
        g.put(x0, y, "O")
        g.put(x1, y, "O")
        g.put(x0 + 1, y, "Q")
        g.put(x0 + 2, y, "Q")
        g.put(x1 - 1, y, "c")
        g.put(x1 - 2, y, "c")
        g.put(x1 - 3, y, "c")
    mask = mask_of(rows)
    if male:
        # the squarer shoulder needs a closed top line; the lit rim sits just under it on the left
        for x, y in outline(mask):
            if (x, y - 1) not in mask and y < 62:
                g.put(x, y, "O")
                if (x, y + 1) in mask and x < 31 and g.get(x, y + 1) == "C":
                    g.put(x, y + 1, "Q")
    g.spans(_INNER, "N")
    for y, (x0, x1) in _INNER.items():
        g.put(x1, y, "u")
    return mask


def _collar(g: Grid, band: str, k0: int = 0, k1: int = 8, under_to: int = 8) -> None:
    """The crossed collar. band lists the band's roles from its outer edge to its inner edge; the
    inner edges run down from the neck (x 27 and 36 at y 54) one pixel per row and cross at y ~59.
    The right band is drawn first (underneath), the left band on top (右衽). under_to < 8 tucks the
    underneath band away below the crossing (a plain robe reads as a 交领 edge, not a gold X)."""
    w = len(band)
    for k in range(k0, min(k1, under_to) + 1):
        y = 54 + k
        for i, r in enumerate(band):
            g.put(36 - k + (w - 1 - i), y, r)
    for k in range(k0, k1 + 1):
        y = 54 + k
        for i, r in enumerate(band):
            g.put(28 - w + k + i, y, r)
    g.put(36, 54, "O")


def _side(ch) -> bool:
    return ch.robe.endswith("_m")


# ---- tiers -------------------------------------------------------------------------------------------

def robe_plain(g: Grid, ch, p, ctx) -> set:
    """炼气: hemp (men) or 月白 ru (women) with a narrow dark band and nothing else."""
    male = _side(ch)
    mask = _body(g, _COAT_M if male else _COAT_F, male)
    _collar(g, "gg", under_to=5)
    # a soft fold where the sleeve meets the shoulder: two shade pixels each side
    for x, y in ((14, 61), (15, 62)) if not male else ((11, 60), (12, 61)):
        g.put(x, y, "c")
        g.put(63 - x, y, "c")
    return mask


def robe_dyed(g: Grid, ch, p, ctx) -> set:
    """筑基: a dyed robe, a contrasting band with a thin light piping (N) outside and a dark line inside."""
    male = _side(ch)
    mask = _body(g, _COAT_M if male else _COAT_F, male)
    _collar(g, "NGGg", under_to=5)
    return mask


def _pendant(g: Grid) -> None:
    """A jade pendant (玉佩) hanging from the collar crossing on a red cord."""
    g.put(31, 59, "V")
    g.put(31, 60, "V")
    g.span(61, 30, 32, "Z")
    g.put(32, 61, "z")
    g.put(29, 62, "z")
    g.span(62, 30, 32, "Z")
    g.put(33, 62, "z")
    g.put(31, 62, "X")
    g.span(63, 30, 32, "z")


def robe_brocade(g: Grid, ch, p, ctx) -> set:
    """金丹: the sample's gold band, a jade pendant at the crossing, gold embroidery dots."""
    male = _side(ch)
    mask = _body(g, _COAT_M if male else _COAT_F, male)
    _collar(g, "gGGG")
    dots = ((18, 59), (13, 62), (21, 62), (9, 63)) if not male else ((17, 58), (11, 61), (20, 61), (6, 63), (15, 63))
    for x, y in dots:
        g.put(x, y, "G")
        g.put(63 - x, y, "g")
    _pendant(g)
    return mask


def robe_court(g: Grid, ch, p, ctx) -> set:
    """元婴: a stiff double-edged band standing up beside the neck, a gold shoulder hem with cloud dots.
    court_f keeps the sample's coverage (every extra pixel lands on hair or neck that is already there)."""
    male = _side(ch)
    rows = _COAT_M if male else _COAT_F
    mask = _body(g, rows, male)
    # 云肩: a gold yoke line following the shoulder, lit left, shaded right, and a cloud on each breast
    d = 7 if male else 6
    for y, (x0, x1) in rows.items():
        if y >= 56:
            g.put(x0 + d, y, "G")
            g.put(x1 - d, y, "g")
    cloud = ((16, 60), (18, 60), (17, 61), (15, 61), (19, 61), (16, 62), (17, 62), (18, 62)) if not male else \
        ((14, 60), (16, 60), (15, 61), (13, 61), (17, 61), (14, 62), (15, 62), (16, 62))
    for x, y in cloud:
        g.put(x, y, "G")
        g.put(63 - x, y, "g")
    _collar(g, "gGGGg")
    # the stiff collar stands up beside the neck
    for y in (52, 53):
        g.span(y, 24, 26, "G")
        g.put(24, y, "g")
        g.put(27, y, "g")
        g.span(y, 37, 39, "G")
        g.put(36, y, "g")
        g.put(39, y, "g")
    g.put(37, 53, "g")
    return mask


ROBES = {
    "plain_m": robe_plain,
    "plain_f": robe_plain,
    "dyed_m": robe_dyed,
    "dyed_f": robe_dyed,
    "brocade_m": robe_brocade,
    "brocade_f": robe_brocade,
    "court_m": robe_court,
    "court_f": robe_court,
}


def coat_crossed(g: Grid, ch, p, ctx) -> set:
    """The approved sample's coat exactly (kept for reference and tests)."""
    mask = _body(g, _COAT_F, False)
    _collar(g, "gGGG")
    return mask


def draw_coat(g: Grid, ch, p, ctx) -> set:
    return ROBES[ch.robe](g, ch, p, ctx)


# ---- headwear ----------------------------------------------------------------------------------------

def _head(ctx) -> set:
    """Every hair cell (the silhouette, the side locks and the bangs) - headwear and bandages wrap it."""
    return set(ctx.get("hair_back", set())) | set(ctx.get("locks", set())) | set(ctx.get("bangs", set()))


def _band(g: Grid, ctx, rows: dict) -> list:
    """Paint a band that wraps the head: rows -> role, clipped to the hair. Returns (x0, x1) per row."""
    head = _head(ctx)
    ends = []
    for y, role in rows.items():
        xs = [x for x in range(64) if (x, y) in head]
        if not xs:
            continue
        x0, x1 = min(xs), max(xs)
        g.span(y, x0, x1, role)
        ends.append((y, x0, x1))
    return ends


def jade_pin(g: Grid, ch, p, ctx) -> None:
    """A jade hairpin through the half-up knot with a red cord (the approved sample)."""
    for k in range(8):
        g.put(37 + k, 2 + k // 4, "Z")
    g.put(36, 2, "z")
    g.put(45, 3, "z")
    g.put(44, 3, "Z")
    g.put(37, 3, "V")
    g.put(37, 4, "V")
    g.put(38, 4, "V")


def cloth(g: Grid, ch, p, ctx) -> None:
    """A plain cloth band across the brow (rogues): sect accent, light top, dark lower edge, a knot
    with two short ends on the lit side."""
    ends = _band(g, ctx, {11: "A", 12: "A", 13: "a"})
    for y, x0, x1 in ends:
        g.put(x0, y, "a")
        for x in range(x1 - 3, x1 + 1):
            g.put(x, y, "a")
    # a fold every few pixels so it reads as cloth, not a metal ring
    for x in (22, 30, 39):
        g.put(x, 12, "a")
    # the knot and two ends on the viewer's left
    x0 = min(x for _, x, _ in ends) if ends else 10
    g.put(x0 - 1, 11, "a"); g.put(x0 - 1, 12, "A"); g.put(x0 - 1, 13, "a")
    for x, y in ((x0 - 2, 13), (x0 - 2, 14), (x0 - 3, 15), (x0 - 3, 16)):
        g.put(x, y, "A")
        g.put(x + 1, y, "a")
    for x, y in ((x0, 14), (x0, 15), (x0 - 1, 16), (x0 - 1, 17), (x0 - 1, 18)):
        g.put(x, y, "A" if y < 17 else "a")


def headband(g: Grid, ch, p, ctx) -> None:
    """抹额 (elders): a dark band with a lighter top edge and a jade rhombus in a light (A) setting at the centre."""
    ends = _band(g, ctx, {11: "A", 12: "a", 13: "a"})
    for y, x0, x1 in ends:
        g.put(x1, y, "a")
    # the jade: a small rhombus in a thin dark setting
    for x, y in ((31, 11), (32, 11)):
        g.put(x, y, "Z")
    g.span(12, 30, 33, "Z")
    g.put(33, 12, "z")
    g.put(31, 12, "X")
    g.span(13, 31, 32, "z")
    for x, y in ((29, 12), (34, 12), (30, 11), (33, 11), (30, 13), (33, 13)):
        g.put(x, y, "A")


_RIBBON = """
.aa.....aa.
aAAa...aAAa
aAAAaaaAAAa
aAAaaAaaAAa
.aa.aAa.aa.
...aA.Aa...
...aA.Aa...
..aA...Aa..
..aa...aa..
"""


def _sprite(g: Grid, art: str, x0: int, y0: int) -> None:
    for dy, line in enumerate(art.strip("\n").splitlines()):
        for dx, r in enumerate(line):
            if r != ".":
                g.put(x0 + dx, y0 + dy, r)


def ribbon(g: Grid, ch, p, ctx) -> None:
    """发带 (inner disciples): a bow tied on the viewer's right of the head with two tails, sect accent."""
    _sprite(g, _RIBBON, 41, 7)


_CROWN = """
....gGGg....
..gGGZZGGg..
GGgGzZZzGgGG
..gGGGGGGg..
...gggggg...
"""


def crown(g: Grid, ch, p, ctx) -> None:
    """玉冠 (sect masters): a small gold crown over the top-knot, a jade gem in front, a gold pin through it."""
    _sprite(g, _CROWN, 26, 0)
    g.put(25, 2, "g")
    g.put(38, 2, "g")


_PHOENIX = """
...gG..
..gGGG.
.gGZGGg
gGGGgG.
.gG.g..
"""


def phoenix_pin(g: Grid, ch, p, ctx) -> None:
    """凤钗 (women masters): a gold pin into the dome's right flank with a spread phoenix head (a jade eye),
    a jade drop on a short chain and a red tassel."""
    for k in range(6):
        g.put(35 + k, 8 - k // 2, "G")
        g.put(35 + k, 9 - k // 2, "g")
    _sprite(g, _PHOENIX, 40, 1)
    g.put(44, 6, "g")
    g.put(44, 7, "z"); g.put(45, 7, "Z")
    g.put(44, 8, "Z"); g.put(45, 8, "z")
    g.put(44, 9, "z")
    for x, y in ((44, 10), (43, 11), (44, 11), (45, 11), (43, 12), (45, 12)):
        g.put(x, y, "V")


def none(g: Grid, ch, p, ctx) -> None:
    return None


HEADWEAR = {
    "none": none,
    "jade_pin": jade_pin,
    "cloth": cloth,
    "headband": headband,
    "ribbon": ribbon,
    "crown": crown,
    "phoenix_pin": phoenix_pin,
}


def draw_headwear(g: Grid, ch, p, ctx) -> None:
    HEADWEAR[ch.headwear](g, ch, p, ctx)


# ---- marks ------------------------------------------------------------------------------------------

def _bandage_top(x: int) -> int:
    return 12 + (x * 6) // 64        # slopes down to the right, 12 -> 17, above the brows (rows 25..27)


def bandage(g: Grid, ch, p, ctx) -> None:
    """A bandage wrapped slantwise across the brow over the hair and bangs, wrap creases, a knot and two
    loose ends hanging past the head on the viewer's right."""
    head = _head(ctx)
    face = ctx.get("face", set())
    cells = set()
    for x in range(64):
        y0 = _bandage_top(x)
        for d in range(3):
            if (x, y0 + d) in head or (x, y0 + d) in face:
                cells.add((x, y0 + d))
    for x, y in cells:
        g.put(x, y, "f" if (x, y + 1) not in cells else "F")
    for x in (19, 27, 35, 43):
        y0 = _bandage_top(x)
        g.put(x, y0, "f")
        g.put(x + 1, y0 + 1, "f")
    if not cells:
        return
    x1 = max(x for x, _ in cells)
    y1 = _bandage_top(x1)
    g.put(x1, y1, "f"); g.put(x1, y1 + 1, "F"); g.put(x1 + 1, y1 + 1, "f")
    for x, y in ((x1 + 1, y1 + 2), (x1 + 1, y1 + 3), (x1 + 2, y1 + 4), (x1 + 2, y1 + 5), (x1 + 3, y1 + 6)):
        g.put(x, y, "F")
        g.put(x + 1, y, "f")
    for x, y in ((x1 - 1, y1 + 3), (x1 - 1, y1 + 4), (x1 - 1, y1 + 5), (x1, y1 + 6), (x1, y1 + 7)):
        g.put(x, y, "F")
        g.put(x + 1, y, "f")


def scar(g: Grid, ch, p, ctx) -> None:
    """A healed cut on the right cheek under the eye's outer corner: four pixels down-left, a light edge."""
    for x, y in ((44, 39), (43, 40), (42, 41), (41, 42)):
        g.put_if(x, y, "Y", SKIN)
    for x, y in ((44, 40), (43, 41), (42, 42)):
        g.put_if(x, y, "L", SKIN)


def draw_marks(g: Grid, ch, p, ctx) -> None:
    if "scar" in ch.marks:
        scar(g, ch, p, ctx)
    if "bandage" in ch.marks:
        bandage(g, ch, p, ctx)
