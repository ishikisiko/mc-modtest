"""Sheets for checking one layer's variants on the two base people.

  /usr/bin/python3 -m tools.portraitgen.preview_layer face|eyes|hair_front|hair_back|body|all --out FILE.png [--scale 4]
"""
from __future__ import annotations

import argparse
from dataclasses import replace

from PIL import Image, ImageDraw, ImageFont

from .person import Choice, Person
from .render import render

BASE_F = Person(19, "汪觉非", "f", "golden_core", "elder", 0, 60.0, [1000, 1000, 1000, 1000, 6000], [50] * 5)
BASE_M = Person(36, "毕福安", "m", "golden_core", "elder", 1, 70.0, [1000, 1000, 6000, 1000, 1000], [50] * 5)
CHOICE_F = Choice("oval", "pale", "ink_violet", "hime", "half_up", "round", "spirit", "arched", "smile", True, True,
                  "court_f", 0, "jade_pin", [], False, False)
CHOICE_M = Choice("oval", "light", "ink_blue", "split", "bun", "almond", "water", "straight", "neutral", True, False,
                  "brocade_m", 1, "headband", [], False, False)
FONT = "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"


def rows_for(layer: str):
    rows = []
    if layer in ("face", "all"):
        rows.append(("face shapes · f", [(BASE_F, replace(CHOICE_F, face=s), s) for s in ("oval", "round", "sharp")]
                     + [(BASE_F, replace(CHOICE_F, face="oval", skin=k), k) for k in ("light", "tan")]))
        rows.append(("face shapes · m", [(BASE_M, replace(CHOICE_M, face=s), s) for s in ("oval", "round", "sharp")]
                     + [(BASE_M, replace(CHOICE_M, old=True, hair_colour="silver"), "old")]))
        rows.append(("mouths · f", [(BASE_F, replace(CHOICE_F, mouth=m), m) for m in ("smile", "neutral", "small", "smirk", "frown", "open")]))
        rows.append(("mouths · m", [(BASE_M, replace(CHOICE_M, mouth=m), m) for m in ("smile", "neutral", "small", "smirk", "frown", "open")]))
    if layer in ("eyes", "all"):
        rows.append(("eyes · f", [(BASE_F, replace(CHOICE_F, eye_shape=e), e) for e in ("round", "almond", "sharp", "droopy")]
                     + [(BASE_F, replace(CHOICE_F, eye_colour=c), c) for c in ("water", "fire")]))
        rows.append(("eyes · m", [(BASE_M, replace(CHOICE_M, eye_shape=e), e) for e in ("round", "almond", "sharp", "droopy")]
                     + [(BASE_M, replace(CHOICE_M, eye_colour=c), c) for c in ("wood", "metal")]))
        rows.append(("brows · f", [(BASE_F, replace(CHOICE_F, brow=b), b) for b in ("arched", "straight", "angry", "worried")]))
        rows.append(("brows · m", [(BASE_M, replace(CHOICE_M, brow=b), b) for b in ("arched", "straight", "angry", "worried")]))
    if layer in ("hair_front", "all"):
        rows.append(("bangs · f", [(BASE_F, replace(CHOICE_F, front=f), f) for f in ("hime", "split", "sweep", "curtain", "spiky")]))
        rows.append(("bangs · m", [(BASE_M, replace(CHOICE_M, front=f), f) for f in ("hime", "split", "sweep", "curtain", "spiky")]))
    if layer in ("hair_back", "all"):
        rows.append(("back · f", [(BASE_F, replace(CHOICE_F, back=b), b) for b in ("long", "half_up", "twin_buns", "ponytail", "short", "bun")]))
        rows.append(("back · m", [(BASE_M, replace(CHOICE_M, back=b), b) for b in ("short", "bun", "long", "ponytail", "half_up")]))
        rows.append(("hair colours", [(BASE_F, replace(CHOICE_F, hair_colour=c), c) for c in ("ink_violet", "ink_blue", "dark_brown", "chestnut", "grey_ink_blue", "silver")]))
    if layer in ("body", "all"):
        rows.append(("robes · f", [(BASE_F, replace(CHOICE_F, robe=r, headwear="none"), r) for r in ("plain_f", "dyed_f", "brocade_f", "court_f")]))
        rows.append(("robes · m", [(BASE_M, replace(CHOICE_M, robe=r, headwear="none"), r) for r in ("plain_m", "dyed_m", "brocade_m", "court_m")]))
        rows.append(("headwear · f", [(BASE_F, replace(CHOICE_F, headwear=h), h) for h in ("none", "jade_pin", "phoenix_pin", "ribbon", "cloth")]))
        rows.append(("headwear · m", [(BASE_M, replace(CHOICE_M, headwear=h), h) for h in ("none", "headband", "crown", "ribbon", "cloth")]))
        rows.append(("marks", [(BASE_M, replace(CHOICE_M, marks=["bandage"]), "bandage"), (BASE_M, replace(CHOICE_M, marks=["bandage", "scar"]), "bandage+scar"),
                              (BASE_F, replace(CHOICE_F, dead=True), "dead")]))
    return rows


def sheet(layer: str, scale: int = 4) -> Image.Image:
    rows = rows_for(layer)
    try:
        font = ImageFont.truetype(FONT, 13)
    except OSError:
        font = ImageFont.load_default()
    cell = 64 * scale
    width = max(len(r[1]) for r in rows) * (cell + 8) + 8
    height = len(rows) * (cell + 40) + 8
    out = Image.new("RGBA", (width, height), (38, 38, 44, 255))
    draw = ImageDraw.Draw(out)
    y = 8
    for title, items in rows:
        draw.text((8, y), title, fill=(230, 220, 200, 255), font=font)
        x = 8
        for p, ch, label in items:
            im = render(p, ch).resize((cell, cell), Image.NEAREST)
            out.paste(im, (x, y + 18), im)
            draw.text((x, y + 18 + cell + 2), label, fill=(200, 200, 210, 255), font=font)
            x += cell + 8
        y += cell + 40
    return out


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("layer")
    ap.add_argument("--out", required=True)
    ap.add_argument("--scale", type=int, default=4)
    a = ap.parse_args(argv)
    sheet(a.layer, a.scale).save(a.out)
    print("saved", a.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
