"""The per-person recolour of a look's baked texture: hair and eyes take a portrait's colours.

Standard library only (no Pillow). This is the rule Java `portrait/NpcSkinComposer` implements; the
goldens written by `python3 -m tools.npcgen goldens` (`src/test/resources/npc_skin_goldens/`) pin the
two together pixel for pixel.

- The portrait palette (`tools/portraitgen/roles.py`, Java `PortraitPalette`) gives a 5-step hair ramp
  per hair colour (the `grey_*` forms mixed toward silver there) and a 4-step iris ramp plus the
  pupil per eye colour.
- `hair_ramp7` resamples the 5-step ramp to the painter's 7 steps; the role map's hair index is a
  half-step tone on it (`hair_tone`). No darkening: npcgen's base tones sit high because entity faces
  draw at 50 to 74 % brightness.
- The role map's iris index is a row of the iris ramp, or 3 for the pale row mixed half way toward
  EYE_WHITE (`iris`).
- Every mix is `int(round(a + (b - a) * f))` per channel: Python rounds half to even, Java uses
  `Math.rint`.
"""
from __future__ import annotations

import json
import math

from ..portraitgen import roles as _portrait
from . import roles

# PortraitSpec.HairColour / EyeColour, in ordinal order, lower-cased.
HAIR_COLOURS = ("ink_violet", "ink_blue", "dark_brown", "chestnut", "silver",
                "grey_ink_violet", "grey_ink_blue", "grey_dark_brown", "grey_chestnut")
EYE_COLOURS = ("metal", "wood", "water", "fire", "earth", "spirit")
EYE_WHITE = (0xF8, 0xFA, 0xFF)
RAMP7 = 7

GOLDENS = "src/test/resources/npc_skin_goldens"
# (definition, hair, eye): four per look, together covering every hair and every eye colour.
GOLDEN_CASES = (
    ("cultivator", "ink_blue", "water"),
    ("cultivator", "silver", "spirit"),
    ("cultivator", "grey_chestnut", "fire"),
    ("cultivator", "dark_brown", "earth"),
    ("cultivator_f_novice", "ink_violet", "metal"),
    ("cultivator_f_novice", "grey_ink_blue", "wood"),
    ("cultivator_f_novice", "chestnut", "spirit"),
    ("cultivator_f_novice", "grey_dark_brown", "water"),
    ("cultivator_f_adept", "grey_ink_violet", "earth"),
    ("cultivator_f_adept", "silver", "fire"),
    ("cultivator_f_adept", "ink_blue", "metal"),
    ("cultivator_f_adept", "chestnut", "wood"),
)


def mix(a, b, f):
    """humanoid._mix: per channel round(a + (b - a) * f), half to even."""
    return tuple(int(round(a[k] + (b[k] - a[k]) * f)) for k in range(3))


def hair_ramp5(name):
    """The portrait's D h H I J hair ramp, dark to pale, as RGB tuples (PortraitPalette.hair)."""
    if name not in HAIR_COLOURS:
        raise ValueError(f"unknown hair colour {name!r}; known: {', '.join(HAIR_COLOURS)}")
    return [tuple(c[:3]) for c in _portrait.HAIRS[name]]


def eye_ramp(name):
    """The portrait's iris ramp 1..4 (dark to pale) and the pupil, as RGB tuples (PortraitPalette.eyes)."""
    if name not in EYE_COLOURS:
        raise ValueError(f"unknown eye colour {name!r}; known: {', '.join(EYE_COLOURS)}")
    iris, pupil = _portrait.EYES[name]
    return [tuple(c[:3]) for c in iris] + [tuple(pupil[:3])]


def hair_ramp7(ramp5):
    """A 5-step ramp resampled to 7: step j samples it at 2j / 3 (NpcSkinComposer.hairRamp7)."""
    out = []
    for j in range(RAMP7):
        p = (2.0 * j) / 3.0
        i = int(math.floor(p))
        out.append(tuple(ramp5[-1]) if i >= len(ramp5) - 1 else mix(ramp5[i], ramp5[i + 1], p - i))
    return out


def hair_tone(ramp7, index):
    """Half-step tone `index` (0..12) on a 7-step ramp: humanoid._tone at index / 2."""
    if not 0 <= index < roles.HAIR_TONES:
        raise ValueError(f"hair tone {index}")
    i = index // 2
    if i >= RAMP7 - 1:
        return tuple(ramp7[RAMP7 - 1])
    return mix(ramp7[i], ramp7[i + 1], (index % 2) * 0.5)


def iris(eye, index):
    """Iris row `index` (0..2, dark to pale) of `eye`, or 3: the pale row mixed half way to EYE_WHITE."""
    if 0 <= index < roles.IRIS_WHITE_MIX:
        return tuple(eye[index])
    if index == roles.IRIS_WHITE_MIX:
        return mix(eye[2], EYE_WHITE, 0.5)
    raise ValueError(f"iris index {index}")


def compose_with(base, role_rows, ramp7, eye):
    """`base` (RGBA rows) with every role texel recoloured from a 7-step hair ramp and an iris ramp
    whose rows 0..2 are dark to pale. A new list of rows; `base` is untouched."""
    if len(base) != len(role_rows) or any(len(a) != len(b) for a, b in zip(base, role_rows)):
        raise ValueError("role map size does not match the texture")
    out = []
    for brow, rrow in zip(base, role_rows):
        row = list(brow)
        for x, (material, index, _, alpha) in enumerate(rrow):
            if not alpha:
                continue
            if material == roles.MATERIAL_HAIR:
                row[x] = hair_tone(ramp7, index) + (255,)
            elif material == roles.MATERIAL_IRIS:
                row[x] = iris(eye, index) + (255,)
            else:
                raise ValueError(f"unknown role material {material}")
        out.append(row)
    return out


def compose(base, role_rows, hair_name, eye_name):
    """A look's baked texture recoloured to a portrait's hair and eye colours (NpcSkinComposer.compose)."""
    return compose_with(base, role_rows, hair_ramp7(hair_ramp5(hair_name)), eye_ramp(eye_name))


def _hex(c):
    return "#%02X%02X%02X" % tuple(c[:3])


def golden_files():
    """{repo-relative path: bytes} of the goldens: one recoloured texture per case, and a JSON index
    with the cases and the ramp7 / iris vectors of every colour."""
    from .. import gen_qingfeng_sword_model as pngio
    from . import build

    built = {}
    files = {}
    cases = []
    for name, hair, eye in GOLDEN_CASES:
        if name not in built:
            built[name] = build.Built(name)
        b = built[name]
        file = f"{b.d.LOOK}_{hair}_{eye}.png"
        files[f"{GOLDENS}/{file}"] = pngio.encode_png(compose(b.texture, b.roles, hair, eye))
        p = build.paths(name)
        cases.append({"look": b.d.LOOK, "name": b.d.NAME, "hair": hair, "eye": eye, "file": file,
                      "texture": p["texture"].relative_to(build.RESOURCES).as_posix(),
                      "roles": p["roles"].relative_to(build.RESOURCES).as_posix()})
    doc = {
        "note": ("tools/npcgen/recolour.py compose of the baked texture and role map (paths under "
                 "src/main/resources); NpcSkinComposer must match pixel for pixel. Regenerate with "
                 "python3 -m tools.npcgen goldens"),
        "cases": cases,
        "ramp7": {h: [_hex(c) for c in hair_ramp7(hair_ramp5(h))] for h in HAIR_COLOURS},
        "iris": {e: [_hex(iris(eye_ramp(e), i)) for i in range(roles.IRIS_WHITE_MIX + 1)] for e in EYE_COLOURS},
    }
    files[f"{GOLDENS}/goldens.json"] = (json.dumps(doc, indent=1) + "\n").encode("utf-8")
    return files


def write_goldens(check=False):
    from ..beastgen.build import REPO, same_file
    files = golden_files()
    stale = [p for p, data in files.items() if not same_file(REPO / p, data)]
    if check:
        for p in stale:
            print(f"STALE {p}")
        if stale:
            print("run: python3 -m tools.npcgen goldens")
            return 1
        print(f"goldens: {len(files)} files up to date")
        return 0
    (REPO / GOLDENS).mkdir(parents=True, exist_ok=True)
    for p in stale:
        (REPO / p).write_bytes(files[p])
    total = sum(len(d) for d in files.values())
    print(f"goldens: {len(files)} files ({len(stale)} written), {total} bytes -> {GOLDENS}")
    return 0
