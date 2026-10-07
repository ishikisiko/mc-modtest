"""Face shapes, neck, skin shading, blush, nose and mouths. Owner of this module: the face agent.

Geometry contract (shared with the other parts; do not move): the bust is centred on x = 31.5,
the chin tip sits at y = 50, the cheeks are widest around y 24..38, the neck is x 27..36 from
y 47 down, the coat begins at y 54. Eyes occupy y 28..38 with outer corners at x 19 and x 44, the
nose shade is at (33, 41..42), the mouth is on rows 44..46, blush on rows 39..41. The hair's bangs
cover the forehead down to about y 25..29, the side locks cover x <= 18 and x >= 45.

Every shape here is hand-placed row spans and pixel lists in the style of old/hand.py (the sample
the owner approved). Roles are the letters in roles.py."""
from __future__ import annotations

from .pix import Grid, mask_of, outline, rng, mirror

# ---- face shapes: rows y -> (x0, x1) of skin. "oval" is the approved sample. ----------------------
# Above y 39 every shape shares the sample's head (temples under the bangs, cheeks x 16..47 behind
# the side locks), so the eyes always sit on skin. The shapes differ in the jaw, y 39..50, which is
# the part of the outline that shows between the locks. LIT is the front of the jaw that stays in
# the light; the rest of rows 45..50 is the jaw's underside in shade.

_HEAD = {14: (24, 39), 15: (22, 41), 16: (21, 42), 17: (20, 43), 18: (19, 44), 19: (18, 45), 20: (18, 45),
         21: (17, 46), 22: (17, 46), 23: (17, 46)}
_HEAD.update(rng(24, 38, 16, 47))


def _shape(jaw: dict) -> dict:
    rows = dict(_HEAD)
    rows.update(jaw)
    return rows


# widths 30 30 28 26 24 22 18 16 12 8 6 2: the sample's soft egg
_OVAL = _shape({39: (17, 46), 40: (17, 46), 41: (18, 45), 42: (19, 44), 43: (20, 43), 44: (21, 42), 45: (23, 40),
                46: (24, 39), 47: (26, 37), 48: (28, 35), 49: (29, 34), 50: (31, 32)})
# widths 30 30 30 28 26 24 22 18 14 10 6 4: cheeks held full one row longer, a soft blunt chin
_ROUND = _shape({39: (17, 46), 40: (17, 46), 41: (17, 46), 42: (18, 45), 43: (19, 44), 44: (20, 43), 45: (22, 41),
                 46: (23, 40), 47: (25, 38), 48: (27, 36), 49: (29, 34), 50: (30, 33)})
# widths 30 28 26 24 22 20 16 14 10 8 4 2: a straight V from the cheekbone to a long fine point
_SHARP = _shape({39: (17, 46), 40: (18, 45), 41: (19, 44), 42: (20, 43), 43: (21, 42), 44: (22, 41), 45: (24, 39),
                 46: (25, 38), 47: (27, 36), 48: (28, 35), 49: (30, 33), 50: (31, 32)})

# men: the jaw keeps its width lower, turns a corner and ends in a flat chin
# widths 30 30 28 26 24 22 20 18 14 10 8 4
_OVAL_M = _shape({39: (17, 46), 40: (17, 46), 41: (18, 45), 42: (19, 44), 43: (20, 43), 44: (21, 42), 45: (22, 41),
                  46: (23, 40), 47: (25, 38), 48: (27, 36), 49: (28, 35), 50: (30, 33)})
# widths 30 30 30 28 26 24 22 20 16 12 8 4: full cheeks, the jaw broad to the corner, a short flat chin
_ROUND_M = _shape({39: (17, 46), 40: (17, 46), 41: (17, 46), 42: (18, 45), 43: (19, 44), 44: (20, 43), 45: (21, 42),
                   46: (22, 41), 47: (24, 39), 48: (26, 37), 49: (28, 35), 50: (30, 33)})
# widths 30 28 26 24 22 20 18 16 12 10 6 4: lean V jaw, a narrow flat chin
_SHARP_M = _shape({39: (17, 46), 40: (18, 45), 41: (19, 44), 42: (20, 43), 43: (21, 42), 44: (22, 41), 45: (23, 40),
                   46: (24, 39), 47: (26, 37), 48: (27, 36), 49: (29, 34), 50: (30, 33)})

SHAPES = {"oval": _OVAL, "round": _ROUND, "sharp": _SHARP}
SHAPES_M = {"oval": _OVAL_M, "round": _ROUND_M, "sharp": _SHARP_M}

# the lit front of the jaw on rows 45..48 (the sample: 45 26..37, 46 27..36, 47 28..35)
LIT = {
    ("oval", False): {45: (26, 37), 46: (27, 36), 47: (28, 35)},
    ("round", False): {45: (25, 38), 46: (26, 37), 47: (27, 36), 48: (29, 34)},
    ("sharp", False): {45: (26, 37), 46: (27, 36), 47: (29, 34)},
    ("oval", True): {45: (25, 38), 46: (26, 37), 47: (27, 36), 48: (29, 34)},
    ("round", True): {45: (24, 39), 46: (25, 38), 47: (26, 37), 48: (28, 35)},
    ("sharp", True): {45: (25, 38), 46: (26, 37), 47: (27, 36), 48: (29, 34)},
}


def shape_rows(shape: str, male: bool) -> dict:
    return dict((SHAPES_M if male else SHAPES)[shape])


def draw(g: Grid, ch, p) -> set:
    """Neck, then the face over it with its shading and outline. Returns the face mask."""
    male = not p.female
    # neck: in the chin's shadow at the top, a hard dark band like the references
    neck = rng(47, 56, 27, 36)
    g.spans(neck, "S")
    for y in range(47, 53):
        g.span(y, 27, 36, "T")
    g.span(53, 27, 36, "s")
    for y in range(47, 57):
        g.put(27, y, "K")
        g.put(36, y, "K")
    rows = shape_rows(ch.face, male)
    face = mask_of(rows)
    g.spans(rows, "S")
    # the right cheek away from the light, the underside of the jaw
    for y in range(26, 47):
        x1 = rows[y][1]
        g.put(x1, y, "s")
        if y >= 36:
            g.put(x1 - 1, y, "s")
    for y in range(45, 51):
        x0, x1 = rows[y]
        g.span(y, x0, x1, "s")
    for y, (x0, x1) in LIT[(ch.face, male)].items():
        g.span(y, x0, x1, "S")
    g.paint(outline(face), "K")
    if ch.old:
        _old_marks(g, male)
    return face


def _old_marks(g: Grid, male: bool) -> None:
    """The very old: a soft bag under each eye, a nasolabial fold from the nose wing to the mouth
    corner. One pixel wide, in shade, so they read as age and not as dirt."""
    for x in range(21, 26):              # bag: a shallow arc one row under the lower lid
        g.put(x, 40, "s")
        g.put(63 - x, 40, "s")
    g.put(20, 39, "s")
    g.put(43, 39, "s")
    for x, y in ((28, 42), (27, 43), (27, 44)):   # folds, the lit side fainter than the shaded one
        g.put(x, y, "s")
    for x, y in ((36, 42), (37, 43), (37, 44)):
        g.put(x, y, "T" if male else "s")


def draw_blush(g: Grid, ch, p, face: set) -> None:
    if ch.old:
        # an old face keeps only a faint warmth low on the cheek, under the eye bags
        for x0 in (18, 41):
            g.span(41, x0 + 1, x0 + 3, "b")
        return
    for x0 in (18, 41):
        g.span(39, x0 + 1, x0 + 3, "b")
        g.span(40, x0, x0 + 4, "B")
        g.span(41, x0 + 1, x0 + 3, "b")


def draw_nose(g: Grid, ch, p) -> None:
    """Two pixels of shade right of centre (the sample); a man's runs one pixel higher and turns
    under the tip, a straighter bridge without drawing a line."""
    g.put(33, 41, "s")
    g.put(33, 42, "s")
    if not p.female:
        g.put(33, 40, "s")
        g.put(32, 42, "s")


# ---- mouths, rows 44..46: M is the lip line, m the lit lower lip, K the inside of an open mouth ----
# The palette makes M/m pink for women and dark, skin-like for men, so one drawing serves both.

def _smile(g: Grid, p) -> None:
    """The sample: a small curve with the corners up and a lit lower lip."""
    g.span(45, 30, 33, "M")
    g.put(29, 44, "M")
    g.put(34, 44, "M")
    g.span(46, 31, 32, "m")


def _neutral(g: Grid, p) -> None:
    """A calm flat line over a lit lower lip, no corners."""
    g.span(45, 30, 33, "M")
    g.span(46, 31, 32, "m")


def _small(g: Grid, p) -> None:
    """A tiny closed mouth: two pixels and a lit one under them."""
    g.span(45, 31, 32, "M")
    g.put(31, 46, "m")


def _smirk(g: Grid, p) -> None:
    """The right corner (the viewer's right) pulled up, the left one flat."""
    g.span(45, 30, 32, "M")
    g.put(33, 44, "M")
    g.put(34, 44, "M")
    g.put(31, 46, "m")


def _frown(g: Grid, p) -> None:
    """A short line with both corners turned down."""
    g.span(45, 30, 33, "M")
    g.put(29, 46, "M")
    g.put(34, 46, "M")


def _open(g: Grid, p) -> None:
    """A small open smile: the upper line with lifted corners, a dark inside, a lit lower lip."""
    g.span(44, 30, 33, "M")
    g.put(29, 44, "M")
    g.put(34, 44, "M")
    g.span(45, 31, 32, "n")
    g.put(30, 45, "M")
    g.put(33, 45, "M")
    g.span(46, 31, 32, "m")


MOUTHS = {
    "smile": _smile,
    "neutral": _neutral,
    "small": _small,
    "smirk": _smirk,
    "frown": _frown,
    "open": _open,
}


def draw_mouth(g: Grid, ch, p) -> None:
    MOUTHS[ch.mouth](g, p)
    if ch.old:
        # age thins the lips: the lit lower lip goes back to skin
        for x in range(29, 35):
            g.put_if(x, 46, "S", "m")
