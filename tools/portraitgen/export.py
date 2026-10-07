"""Exports the parts as role maps for the Java compositor (docs/portrait-java-contract.md).

Every map is every cell one step of render.compose wrote (whether or not the role changed),
captured in the context of the base person with that variant substituted: R = role index + 1, G = 255 inside the step's mask, A = 255
where either is set. `replay.py` composes from these maps and must reproduce `render.render`
pixel for pixel (tools/tests/test_portraitgen_export.py).

  /usr/bin/python3 -m tools.portraitgen.export [--out src/main/resources/assets/myvillage/portrait]
"""
from __future__ import annotations

import argparse
import json
import os
from dataclasses import replace

from PIL import Image

from . import parts_eyes
from .person import (BACKS_F, BACKS_M, Choice, EYE_SHAPES, FACE_SHAPES, FRONTS_F, FRONTS_M, Person)
from .preview_layer import BASE_F, BASE_M, CHOICE_F, CHOICE_M
from .render import STEPS, compose, render

ROLES = "SsTKLBbHhDIJRWwEe1234PXxMmnOcCQgGuNaAZzVFfY"
BACKS = ["short", "long", "ponytail", "twin_buns", "half_up", "bun"]
FRONTS = ["hime", "split", "sweep", "curtain", "spiky"]
BROWS = ["straight", "arched", "angry", "worried"]
MOUTHS = ["smile", "neutral", "small", "smirk", "frown", "open"]
ROBES = ["plain", "dyed", "brocade", "court"]
HEADWEAR = ["cloth", "headband", "ribbon", "crown", "phoenix_pin", "jade_pin"]
SKINS = ["pale", "light", "tan"]


def base(gender: str, **over):
    p, ch = (BASE_F, CHOICE_F) if gender == "f" else (BASE_M, CHOICE_M)
    return p, replace(ch, **over)


def steps(p: Person, ch: Choice) -> dict:
    """step -> (delta {(x, y): role}, masks snapshot) for one composition."""
    out = {}

    def trace(step, g, ctx):
        out[step] = (dict(g.journal), {k: set(v) for k, v in ctx.items() if isinstance(v, set)})

    compose(p, ch, trace)
    return out


def to_png(delta: dict, mask: set | None) -> Image.Image:
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    for (x, y), role in delta.items():
        px[x, y] = (ROLES.index(role) + 1, 0, 0, 255)
    for x, y in mask or ():
        if 0 <= x < 64 and 0 <= y < 64:
            r = px[x, y][0]
            px[x, y] = (r, 255, 0, 255)
    return img


def export(out_dir: str) -> dict:
    layers = {}
    clears_face = []

    def save(layer: str, name: str, delta: dict, mask=None):
        os.makedirs(os.path.join(out_dir, layer), exist_ok=True)
        to_png(delta, mask).save(os.path.join(out_dir, layer, name + ".png"))
        layers.setdefault(layer, []).append(name)

    for back in BACKS:
        st = steps(*base("f", back=back))
        behind, masks0 = st["behind"]
        face = st["face"][1]["face"]
        after_locks = st["locks"][1]["hair_back"]
        if after_locks == masks0["hair_back"] - face:
            clears_face.append(back)
        elif after_locks != masks0["hair_back"]:
            raise SystemExit(f"back {back}: locks changed the silhouette mask in an unexpected way")
        save("behind", back, behind, masks0["hair_back"])
        save("locks", back, st["locks"][0], st["locks"][1]["locks"])
        save("knot", back, st["knot"][0])
        for front in FRONTS:
            st2 = steps(*base("f", back=back, front=front))
            save("bangs", f"{front}_{back}", st2["bangs"][0], st2["bangs"][1]["bangs"])
        for hw in HEADWEAR:
            st3 = steps(*base("f", back=back, headwear=hw))
            save("headwear", f"{hw}_{back}", st3["headwear"][0])
        for front in FRONTS:      # the bandage wraps the hair, whose edge the bangs' tufts change
            st4 = steps(*base("f", back=back, front=front, marks=["bandage"], headwear="none"))
            save("marks", f"bandage_{front}_{back}", st4["marks"][0])
        st5 = steps(*base("f", back=back, marks=["scar"], headwear="none"))
        save("marks", f"scar_{back}", st5["marks"][0])
    for g in ("f", "m"):
        for shape in FACE_SHAPES:
            for old in (False, True):
                st = steps(*base(g, face=shape, old=old))
                save("face", f"{shape}_{g}" + ("_old" if old else ""), st["face"][0], st["face"][1]["face"])
        st = steps(*base(g, nose=True))
        save("nose", g, st["nose"][0])
        for shape in EYE_SHAPES:
            st = steps(*base(g, eye_shape=shape))
            save("eyes", f"{shape}_{g}", st["eyes"][0])
        for brow in BROWS:
            pts = parts_eyes.BROWS[brow] if g == "f" else parts_eyes.BROWS_M[brow]
            delta = {}
            for x, y in pts:
                delta[(x, y)] = "R"
                delta[(63 - x, y)] = "R"
            save("brows", f"{brow}_{g}", delta)
        for robe in ROBES:
            st = steps(*base(g, robe=f"{robe}_{g}"))
            save("coat", f"{robe}_{g}", st["coat"][0])
    for old in (False, True):
        st = steps(*base("f", blush=True, old=old))
        save("blush", "old" if old else "normal", st["blush"][0])
        for mouth in MOUTHS:
            st = steps(*base("f", mouth=mouth, old=old))
            save("mouth", mouth + ("_old" if old else ""), st["mouth"][0])
    manifest = {"roles": ROLES, "layers": layers, "clears_face": clears_face,
                "steps": list(STEPS), "note": "see docs/portrait-java-contract.md; regenerate with tools.portraitgen.export"}
    with open(os.path.join(out_dir, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=1)
    return manifest


def spec_of(p: Person, ch: Choice) -> dict:
    """The Java PortraitSpec's fields for a Choice."""
    return {"female": p.female, "face": ch.face, "skin": ch.skin, "hairColour": ch.hair_colour, "front": ch.front,
            "back": ch.back, "eyeShape": ch.eye_shape, "eyeColour": ch.eye_colour, "brow": ch.brow, "mouth": ch.mouth,
            "nose": ch.nose, "blush": ch.blush, "robe": ch.robe.rsplit("_", 1)[0], "accent": ch.accent,
            "headwear": ch.headwear, "bandage": "bandage" in ch.marks, "scar": "scar" in ch.marks, "old": ch.old,
            "dead": ch.dead}


def goldens(out_dir: str, cases: list) -> None:
    os.makedirs(out_dir, exist_ok=True)
    index = []
    for i, (p, ch) in enumerate(cases):
        render(p, ch).save(os.path.join(out_dir, f"{i}.png"))
        index.append({"file": f"{i}.png", "spec": spec_of(p, ch)})
    with open(os.path.join(out_dir, "portrait_goldens.json"), "w", encoding="utf-8") as f:
        json.dump({"note": "render.render of these specs; the Java PortraitComposer must match pixel for pixel",
                   "cases": index}, f, indent=1)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="src/main/resources/assets/myvillage/portrait")
    ap.add_argument("--goldens", default="src/test/resources/portrait_goldens")
    a = ap.parse_args(argv)
    m = export(a.out)
    n = sum(len(v) for v in m["layers"].values())
    from .person import assign
    cases = [(BASE_F, CHOICE_F), (BASE_M, CHOICE_M)]
    for pid, gender, realm, rank, sect, age, root, traits, injury, alive in (
            (7, "m", "qi_refining", "outer", 2, 19, [1000, 1000, 5000, 1000, 2000], [30, 80, 30, 30, 30], 25, True),
            (12, "f", "foundation_establishment", "inner", 3, 40, [1000, 5000, 1000, 1000, 2000], [30, 30, 80, 30, 30], 0, True),
            (31, "m", "golden_core", "elder", 1, 430, [5000, 1000, 1000, 1000, 2000], [50] * 5, 60, True),
            (44, "f", "nascent_soul", "sect_master", 4, 700, [1000, 1000, 1000, 5000, 2000], [80, 30, 30, 30, 30], 0, True),
            (58, "m", "foundation_establishment", "rogue", -1, 150, [1000, 1000, 1000, 1000, 6000], [30, 30, 30, 80, 30], 0, True),
            (63, "f", "qi_refining", "outer", 5, 17, [2000] * 5, [30, 30, 30, 30, 80], 0, False)):
        p = Person(pid, f"g{pid}", gender, realm, rank, sect, age, root, traits, injury, alive)
        cases.append((p, assign(p)))
    goldens(a.goldens, cases)
    print(f"{n} maps in {len(m['layers'])} layers -> {a.out}; clears_face={m['clears_face']}; {len(cases)} goldens -> {a.goldens}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
