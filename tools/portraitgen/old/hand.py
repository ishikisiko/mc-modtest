"""A hand-drawn 64x64 anime bust: every shape is a hand-placed row span or pixel list."""
from PIL import Image
import sys

PAL = {
    # skin
    "S": "#FFE6DC", "s": "#F4C8B8", "T": "#E0A494", "K": "#9A5A62", "L": "#FFF3EE", "B": "#FFB4BC", "b": "#FFD0D2",
    # hair (ink violet)
    "H": "#4A3880", "h": "#30245C", "D": "#1A1236", "I": "#7664B4", "J": "#B4A4E4",
    # eyes
    "W": "#F8F8FF", "w": "#D6D4EC", "E": "#1C1030", "e": "#B890A8", "1": "#3A1C6E", "2": "#7A48C2", "3": "#B48CEC",
    "4": "#E0CCF8", "P": "#1C0C38", "X": "#FFFFFF", "x": "#F0E6FF", "R": "#7E6CA6",
    # mouth
    "M": "#D2607A", "m": "#F2A8B4",
    # coat and collar
    "C": "#8A3868", "c": "#5A2244", "Q": "#A84A80", "O": "#2C1024", "G": "#E2C478", "g": "#9A7838", "N": "#F6F2EC", "u": "#D8D0C8",
    # jade pin, red cord
    "Z": "#8CDCC0", "z": "#3A9080", "V": "#CC4048",
}
W = H = 64
grid = [["."] * W for _ in range(H)]


def put(x, y, ch):
    if 0 <= x < W and 0 <= y < H:
        grid[y][x] = ch


def span(y, x0, x1, ch):
    for x in range(x0, x1 + 1):
        put(x, y, ch)


def spans(rows, ch):
    for y, (x0, x1) in rows.items():
        span(y, x0, x1, ch)


def mask_of(rows):
    return {(x, y) for y, (x0, x1) in rows.items() for x in range(x0, x1 + 1)}


def rng(y0, y1, x0, x1):
    return {y: (x0, x1) for y in range(y0, y1 + 1)}


def outline(mask, ch, skip=set()):
    for x, y in mask:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in mask and (x + dx, y + dy) not in skip:
                put(x, y, ch)
                break


# ---- 1. hair behind everything ---------------------------------------------------------------
HAIR = {1: (27, 36), 2: (23, 40), 3: (20, 43), 4: (18, 45), 5: (16, 47), 6: (15, 48), 7: (14, 49), 8: (13, 50),
        9: (12, 51), 10: (11, 52)}
HAIR.update(rng(11, 13, 10, 53))
HAIR.update(rng(14, 44, 9, 54))
HAIR.update(rng(45, 53, 10, 53))
hair_mask = mask_of(HAIR)
# the back hair ends in pointed locks just past the shoulders
for y, segs in {54: [(10, 12), (15, 20), (23, 26), (37, 40), (43, 48), (51, 53)],
                55: [(11, 11), (17, 18), (25, 25), (38, 38), (45, 46), (52, 52)]}.items():
    for a, b in segs:
        for x in range(a, b + 1):
            hair_mask.add((x, y))
for x, y in hair_mask:
    put(x, y, "H")
# the back hair (behind the head) is in shadow
for y in range(16, 60):
    for x in range(7, 57):
        if (x, y) in hair_mask and (x <= 19 or x >= 44):
            put(x, y, "h")
# crown highlight: an angel ring with zigzag edges
for x in range(18, 45):
    if (x - 18) % 6 < 4:
        put(x, 7, "I")
span(8, 14, 48, "I")
span(8, 20, 28, "J")
for x in range(16, 47):
    if (x - 16) % 6 < 2:
        put(x, 9, "I")
# the right side falls into shadow, with a thin rim light along its edge
for y in range(10, 54):
    x1 = HAIR[y][1]
    put(x1 - 1, y, "I" if 14 <= y <= 50 else "h")
    put(x1 - 2, y, "h"); put(x1 - 3, y, "h"); put(x1 - 4, y, "h")

# ---- 2. neck, then the face over it -------------------------------------------------------------
NECK = rng(47, 56, 27, 36)
spans(NECK, "S")
for y in range(47, 53):
    span(y, 27, 36, "T")
span(53, 27, 36, "s")
for y in range(47, 57):
    put(27, y, "K")
    put(36, y, "K")

FACE = {14: (24, 39), 15: (22, 41), 16: (21, 42), 17: (20, 43), 18: (19, 44), 19: (18, 45), 20: (18, 45), 21: (17, 46),
        22: (17, 46), 23: (17, 46)}
FACE.update(rng(24, 38, 16, 47))
FACE.update({39: (17, 46), 40: (17, 46), 41: (18, 45), 42: (19, 44), 43: (20, 43), 44: (21, 42), 45: (23, 40),
             46: (24, 39), 47: (26, 37), 48: (28, 35), 49: (29, 34), 50: (31, 32)})
face_mask = mask_of(FACE)
spans(FACE, "S")
# shade: the right cheek (light from the left), the jaw's underside, a soft shadow under the chin line
for y in range(26, 47):
    x1 = FACE[y][1]
    put(x1, y, "s")
    if y >= 36:
        put(x1 - 1, y, "s")
for y in range(45, 51):
    x0, x1 = FACE[y]
    span(y, x0, x1, "s")
span(45, 26, 37, "S")
span(46, 27, 36, "S")
span(47, 28, 35, "S")
outline(face_mask, "K")

# ---- 3. features -------------------------------------------------------------------------------
# blush: a soft oval under each eye, towards the cheek
for x0 in (18, 41):
    span(39, x0 + 1, x0 + 3, "b")
    span(40, x0, x0 + 4, "B")
    span(41, x0 + 1, x0 + 3, "b")
# nose: two pixels of shade right of centre
put(33, 41, "s")
put(33, 42, "s")
# mouth: a small smile and a lit lower lip
span(45, 30, 33, "M")
put(29, 44, "M")
put(34, 44, "M")
span(46, 31, 32, "m")


def eye(ox, right):
    """ox: the eye's outer corner column; local lx 0..8 maps outward->inward."""
    def X(lx):
        return ox - lx if right else ox + lx
    # eye white with a shaded top row under the lash
    for ly in range(2, 8):
        for lx in range(0, 9):
            put(X(lx), 29 + ly, "w" if ly == 2 else "W")
    put(X(1), 37, "W"); put(X(7), 37, "W")
    # iris 6 wide, 7 tall, rounded, dark at the top to pale at the bottom
    iris = {2: (3, 6), 3: (2, 7), 4: (2, 7), 5: (2, 7), 6: (2, 7), 7: (3, 6), 8: (4, 5)}
    tone = {2: "1", 3: "1", 4: "2", 5: "2", 6: "3", 7: "3", 8: "4"}
    for ly, (a, b) in iris.items():
        for lx in range(a, b + 1):
            put(X(lx), 29 + ly, tone[ly])
    # pupil
    for ly in (3, 4, 5):
        put(X(4), 29 + ly, "P"); put(X(5), 29 + ly, "P")
    # highlights: a big one at the top-left in canvas terms, a glint at the bottom-right
    hl = (X(2), X(3)) if not right else (X(7), X(6))
    for x in hl:
        put(x, 31, "X"); put(x, 32, "X")
    put(X(7) if not right else X(2), 36, "x")
    # lashes: a thick curved upper line, an outer flick, a thin lower lid
    span_l = [(0, 1, 7), (1, 0, 8)]
    for ly, a, b in span_l:
        for lx in range(a, b + 1):
            put(X(lx), 29 + ly, "E")
    put(X(0), 31, "E"); put(X(8), 31, "E")
    put(X(-1), 29, "E"); put(X(-2), 28, "E")
    put(X(-1), 30, "E")
    for lx in range(1, 8):
        put(X(lx), 38, "e")
    # eyelid crease
    for lx in range(2, 7):
        put(X(lx), 28, "s")


eye(19, False)
eye(44, True)

# ---- 4. the front hair: bangs and side locks ---------------------------------------------------
LOCK = {12: (12, 16), 13: (11, 17)}
LOCK.update(rng(14, 30, 11, 18))
LOCK.update(rng(31, 40, 12, 18))
LOCK.update(rng(41, 46, 13, 18))
LOCK.update({47: (13, 17), 48: (14, 17), 49: (14, 16), 50: (15, 16), 51: (15, 15), 52: (16, 16)})
lock_l = mask_of(LOCK)
lock_r = {(63 - x, y) for x, y in lock_l}
for x, y in lock_l | lock_r:
    put(x, y, "H")
for y in range(14, 36):
    put(13, y, "I"); put(14, y, "I")
for y in range(44, 51):
    put(14, y, "I")
for y in range(13, 53):
    x0 = LOCK[y][0] if y in LOCK else None
    if x0 is not None:
        put(x0, y, "D"); put(63 - x0, y, "D")
for y in range(18, 47):
    put(18, y, "h")
    put(45, y, "h"); put(44, y, "h")
for y in range(14, 40):
    put(50, y, "h"); put(51, y, "h")

BANGS = [(19, 24, 29), (25, 29, 27), (30, 33, 25), (34, 38, 27), (39, 44, 29)]
bang_mask = set()
for i, (x0, x1, tip) in enumerate(BANGS):
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
        for x in range(a, b + 1):
            bang_mask.add((x, y))
            put(x, y, "H")
    for y in range(11, tip - 3):
        put(x1, y, "D" if y >= 16 else "h")
    for y in range(12, tip - 5):
        put(x0 + (0 if i else 1), y, "I")
# the shadow the bangs cast on the forehead
for x, y in list(bang_mask):
    if (x, y + 1) not in bang_mask and (x, y + 1) in face_mask:
        put(x, y + 1, "s")
        if (x, y + 2) in face_mask and (x, y + 2) not in bang_mask:
            put(x, y + 2, "s")
front = lock_l | lock_r | bang_mask | {(x, y) for (x, y) in hair_mask if y <= 13}
all_hair = hair_mask | front
outline(all_hair, "D", skip=set())
# inner outline where the front hair meets skin
for x, y in front:
    for dx, dy in ((0, 1), (1, 0), (-1, 0)):
        q = (x + dx, y + dy)
        if q not in front and q in face_mask:
            put(x, y, "D")
            break
# brows drawn through the bangs
for pts in ([(20, 27), (21, 26), (22, 26), (23, 25), (24, 25), (25, 25), (26, 25), (27, 26)],
            [(43, 27), (42, 26), (41, 26), (40, 25), (39, 25), (38, 25), (37, 25), (36, 26)]):
    for x, y in pts:
        put(x, y, "R")

# ---- 5. the coat with a crossed gold collar ----------------------------------------------------
COAT = {54: (25, 38), 55: (22, 41), 56: (19, 44), 57: (16, 47), 58: (13, 50), 59: (10, 53), 60: (7, 56), 61: (5, 58),
        62: (4, 59), 63: (3, 60)}
coat_mask = mask_of(COAT)
spans(COAT, "C")
for y, (x0, x1) in COAT.items():
    put(x0, y, "O"); put(x1, y, "O")
    put(x0 + 1, y, "Q"); put(x0 + 2, y, "Q")
    put(x1 - 1, y, "c"); put(x1 - 2, y, "c"); put(x1 - 3, y, "c")
INNER = {54: (28, 35), 55: (28, 35), 56: (29, 34), 57: (29, 34), 58: (30, 33), 59: (30, 33), 60: (31, 32)}
spans(INNER, "N")
for y, (x0, x1) in INNER.items():
    put(x1, y, "u")
for k in range(9):            # the right band, underneath
    y = 54 + k
    span(y, 36 - k, 39 - k, "G")
    put(39 - k, y, "g")
for k in range(9):            # the left band on top (右衽)
    y = 54 + k
    span(y, 24 + k, 27 + k, "G")
    put(24 + k, y, "g")
    put(27 + k, y + 1, "g") if False else None
put(36, 54, "O")

# ---- 6. the half-up knot and its jade pin -------------------------------------------------------
for y, (x0, x1) in {0: (29, 34), 1: (27, 36)}.items():
    span(y, x0, x1, "H")
span(0, 30, 33, "I")
put(28, 1, "D"); put(35, 1, "D"); put(29, 0, "D"); put(34, 0, "D")
for k in range(8):
    put(37 + k, 2 + k // 4, "Z")
put(36, 2, "z"); put(45, 3, "z"); put(44, 3, "Z")
put(37, 3, "V"); put(37, 4, "V"); put(38, 4, "V")

# ---- render ---------------------------------------------------------------------------------
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()
for y in range(H):
    for x in range(W):
        ch = grid[y][x]
        if ch != ".":
            h = PAL[ch].lstrip("#")
            px[x, y] = (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)
out = sys.argv[1]
img.save(out)
big = Image.new("RGBA", (64 * 8 + 64 * 3 + 24, 64 * 8), (30, 30, 36, 255))
big.paste(img.resize((512, 512), Image.NEAREST), (0, 0))
big.paste(img.resize((192, 192), Image.NEAREST), (520, 0))
big.paste(img.resize((128, 128), Image.NEAREST), (520, 200))
big.paste(img, (520, 336))
big.save(out.replace(".png", "_view.png"))
print("ok")
