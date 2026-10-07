"""Role characters and the per-person palette that resolves them. The letters are the ones the
hand-drawn sample (old/hand.py) used, so a drawing can be copied across unchanged.

  skin   S base  s shade  T deep  K outline  L light      B blush  b blush light
  hair   H base  h shadow D outline/deep  I light  J sheen   R brow seen through the bangs
  eyes   W white w white shade  E lash  e lower lid  1..4 iris dark->pale  P pupil  X big highlight  x glint
  mouth  M lip line  m lower lip  n inside of an open mouth
  coat   C base  c shade  Q light  O outline     G trim  g trim dark     N inner garment  u inner shade
  accent A base  a dark (sect ribbon, cord, headband)     Z jade  z jade dark   V red cord
  marks  F bandage  f bandage shade  Y scar
"""
from __future__ import annotations

from .person import Choice, Person


def rgb(h: str):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def mix(a, b, t: float):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def ramp(*hexes):
    return [rgb(h) for h in hexes]


# K T s S L
SKINS = {
    "pale": ramp("#9A5A62", "#E0A494", "#F4C8B8", "#FFE6DC", "#FFF3EE"),
    "light": ramp("#8E5250", "#D9A08A", "#EEC0A6", "#F9DCC8", "#FFF0E4"),
    "tan": ramp("#7A4438", "#C58C6E", "#DCA98A", "#ECC4A6", "#F6DCC6"),
}
# D h H I J
HAIRS = {
    "ink_violet": ramp("#1A1236", "#30245C", "#4A3880", "#7664B4", "#B4A4E4"),
    "ink_blue": ramp("#121A3A", "#223060", "#324A8A", "#5A74BC", "#A0B4E8"),
    "dark_brown": ramp("#2A1410", "#4A2A1E", "#6E4230", "#9A6648", "#D0A080"),
    "chestnut": ramp("#301408", "#6A3418", "#9A5028", "#C47A44", "#EAB07C"),
    "silver": ramp("#4A4E6A", "#8A90AC", "#B8C0D4", "#DCE2EE", "#FAFAFF"),
}
for _k in list(HAIRS):
    HAIRS["grey_" + _k] = [mix(HAIRS[_k][i], HAIRS["silver"][i], 0.38) for i in range(5)]
# 1 2 3 4 (dark -> pale), pupil P
EYES = {
    "spirit": (ramp("#3A1C6E", "#7A48C2", "#B48CEC", "#E0CCF8"), rgb("#1C0C38")),
    "water": (ramp("#1E3A8A", "#3C78D8", "#8CCCF4", "#D4ECFC"), rgb("#0E1C48")),
    "wood": (ramp("#1C5A3A", "#3E9A62", "#96DEB0", "#D4F4E0"), rgb("#0C2C1C")),
    "fire": (ramp("#7A2A14", "#C86A2C", "#F0B878", "#FBE4C0"), rgb("#3C120A")),
    "earth": (ramp("#4A3018", "#8C6230", "#D2A868", "#F0DCB4"), rgb("#24160A")),
    "metal": (ramp("#3C4454", "#7E8AA0", "#C4CCD8", "#E8ECF4"), rgb("#1C2028")),
}
# O c C Q, trim (g G), inner (u N)
ROBES = {
    "plain_m": (ramp("#4A4438", "#A89C86", "#D8D0BC", "#ECE6DA"), ramp("#1B4A60", "#52A3B8"), ramp("#CFD4DB", "#F5F6F8")),
    "plain_f": (ramp("#404856", "#9DABBF", "#DEE6EE", "#F8FAFC"), ramp("#1E2531", "#617391"), ramp("#CFD4DB", "#F5F6F8")),
    "dyed_m": (ramp("#102A36", "#27687F", "#35869E", "#7FC0CF"), ramp("#1E2A55", "#4A5E9E"), ramp("#CFD4DB", "#F5F6F8")),
    "dyed_f": (ramp("#34262E", "#836D7B", "#B49FAD", "#DACAD4"), ramp("#2A1F27", "#876E7E"), ramp("#DEDBD3", "#F8F6F1")),
    "brocade_m": (ramp("#141E34", "#30466E", "#5373A8", "#84A6D6"), ramp("#7A6432", "#E6CC7E"), ramp("#CFD4DB", "#F5F6F8")),
    "brocade_f": (ramp("#4A1420", "#8A2A3C", "#B84056", "#D0607A"), ramp("#9A7838", "#E2C478"), ramp("#D8D0C8", "#F6F2EC")),
    "court_m": (ramp("#080C1A", "#182243", "#2A3A6E", "#445C9E"), ramp("#7A6432", "#E8D08A"), ramp("#B4BBC5", "#E6E9ED")),
    "court_f": (ramp("#2C1024", "#5A2244", "#8A3868", "#A84A80"), ramp("#9A7838", "#E2C478"), ramp("#D8D0C8", "#F6F2EC")),
}
# a A
ACCENTS = [ramp("#561317", "#C83C40"), ramp("#174354", "#3D95AE"), ramp("#22665A", "#6CC5B0"), ramp("#7A6432", "#E2C97F"),
           ramp("#3B2A5A", "#8E6CC4"), ramp("#5A3A1E", "#C08A50"), ramp("#4A4A50", "#A8A8B0")]
FIXED = {"W": rgb("#F8F8FF"), "w": rgb("#D6D4EC"), "E": rgb("#1C1030"), "e": rgb("#B890A8"), "X": rgb("#FFFFFF"),
         "x": rgb("#F0E6FF"), "Z": rgb("#8CDCC0"), "z": rgb("#3A9080"), "V": rgb("#CC4048"), "F": rgb("#F4F0E8"),
         "f": rgb("#C8C0B4"), "Y": rgb("#B04C50")}


def palette(p: Person, ch: Choice) -> dict:
    """Role -> RGBA for this person."""
    k, t, s, S, L = SKINS[ch.skin]
    D, h, Hh, I, J = HAIRS[ch.hair_colour]
    iris, pupil = EYES[ch.eye_colour]
    coat, trim, inner = ROBES[ch.robe]
    a, A = ACCENTS[ch.accent]
    out = dict(FIXED)
    out.update({"S": S, "s": s, "T": t, "K": k, "L": L,
                "B": mix(S, rgb("#FF96AA"), 0.55), "b": mix(S, rgb("#FF96AA"), 0.28),
                "H": Hh, "h": h, "D": D, "I": I, "J": J, "R": mix(I, J, 0.5),
                "1": iris[0], "2": iris[1], "3": iris[2], "4": iris[3], "P": pupil,
                "M": rgb("#D2607A") if p.female else mix(k, t, 0.35), "m": rgb("#F2A8B4") if p.female else mix(S, t, 0.5),
                "n": rgb("#8A2C48") if p.female else mix(k, rgb("#3A1A1A"), 0.5),
                "O": coat[0], "c": coat[1], "C": coat[2], "Q": coat[3], "g": trim[0], "G": trim[1], "u": inner[0], "N": inner[1],
                "a": a, "A": A})
    if p.female and p.tier < 2:
        out["M"] = rgb("#C8707E")
        out["m"] = mix(S, rgb("#F2A8B4"), 0.6)
    return out
