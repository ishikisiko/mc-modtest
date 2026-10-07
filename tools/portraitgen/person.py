"""A person as the portrait sees them, and the deterministic choice of parts."""
from __future__ import annotations

from dataclasses import dataclass, field

REALMS = ["mortal", "qi_refining", "foundation_establishment", "golden_core", "nascent_soul"]
REALM_ZH = {"mortal": "凡人", "qi_refining": "炼气", "foundation_establishment": "筑基",
            "golden_core": "金丹", "nascent_soul": "元婴"}
LIFESPAN = {"mortal": 80, "qi_refining": 120, "foundation_establishment": 240, "golden_core": 500, "nascent_soul": 1000}
RANKS = ["rogue", "outer", "inner", "elder", "sect_master"]
RANK_ZH = {"rogue": "散修", "outer": "外门", "inner": "内门", "elder": "长老", "sect_master": "宗主"}
ELEMENTS = ["metal", "wood", "water", "fire", "earth"]
ELEMENT_ZH = {"metal": "金", "wood": "木", "water": "水", "fire": "火", "earth": "土", "spirit": "异彩"}
TRAITS = ["ambition", "aggression", "caution", "wanderlust", "loyalty"]
TRAIT_ZH = {"ambition": "野心", "aggression": "好斗", "caution": "谨慎", "wanderlust": "好游", "loyalty": "忠诚", "calm": "平和"}


@dataclass
class Person:
    id: int
    name: str = ""
    gender: str = "m"           # "m" or "f"
    realm: str = "qi_refining"
    rank: str = "outer"
    sect_id: int = -1
    age_years: float = 20.0
    root: list = field(default_factory=lambda: [2000, 2000, 2000, 2000, 2000])  # metal wood water fire earth, bp
    traits: list = field(default_factory=lambda: [50, 50, 50, 50, 50])          # ambition aggression caution wanderlust loyalty
    injury: int = 0
    alive: bool = True
    title: str = ""

    @property
    def female(self) -> bool:
        return self.gender == "f"

    @property
    def tier(self) -> int:
        """0 plain, 1 dyed, 2 brocade, 3 court."""
        i = REALMS.index(self.realm) if self.realm in REALMS else 1
        return max(0, min(3, i - 1))

    @property
    def age_fraction(self) -> float:
        return self.age_years / LIFESPAN.get(self.realm, 120)

    @property
    def dominant_element(self) -> str:
        if self.realm == "nascent_soul":
            return "spirit"
        best = max(range(5), key=lambda i: (self.root[i], -i))
        return ELEMENTS[best]

    @property
    def mood(self) -> str:
        """The strongest trait when it stands out, else calm."""
        best = max(range(5), key=lambda i: (self.traits[i], -i))
        if self.traits[best] >= 70:
            return TRAITS[best]
        return "calm"


def mix(seed: int, salt: int) -> int:
    """splitmix64 of (seed * 0x9E3779B97F4A7C15 + salt): stable, trivially portable to Java."""
    z = (seed * 0x9E3779B97F4A7C15 + salt * 0xBF58476D1CE4E5B9) & 0xFFFFFFFFFFFFFFFF
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & 0xFFFFFFFFFFFFFFFF
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & 0xFFFFFFFFFFFFFFFF
    return (z ^ (z >> 31)) & 0x7FFFFFFFFFFFFFFF


def pick(seed: int, salt: int, options):
    return options[mix(seed, salt) % len(options)]


FACE_SHAPES = ["oval", "round", "sharp"]
SKIN_TONES = ["pale", "light", "tan"]
HAIR_COLOURS = ["ink_blue", "ink_violet", "dark_brown", "chestnut"]
EYE_SHAPES = ["round", "almond", "sharp", "droopy"]
EYE_SHAPES_F = ["round", "round", "almond", "droopy", "sharp"]
EYE_SHAPES_M = ["almond", "almond", "sharp", "round", "droopy"]
FRONTS_M = ["split", "sweep", "spiky", "split", "curtain"]
FRONTS_F = ["hime", "split", "sweep", "curtain", "hime"]
BACKS_M = ["short", "short", "long", "ponytail"]
BACKS_F = ["long", "long", "twin_buns", "ponytail", "half_up"]


@dataclass
class Choice:
    face: str
    skin: str
    hair_colour: str
    front: str
    back: str
    eye_shape: str
    eye_colour: str
    brow: str
    mouth: str
    nose: bool
    blush: bool
    robe: str
    accent: int
    headwear: str
    marks: list
    old: bool
    dead: bool

    def summary(self) -> str:
        return (f"{self.face}/{self.skin}/{self.hair_colour} {self.front}+{self.back} "
                f"eye={self.eye_shape}:{self.eye_colour} brow={self.brow} mouth={self.mouth} "
                f"robe={self.robe} head={self.headwear} marks={','.join(self.marks) or '-'}")


def assign(p: Person) -> Choice:
    s = p.id
    old = p.age_fraction >= 0.8
    greying = 0.55 <= p.age_fraction < 0.8
    face = pick(s, 1, FACE_SHAPES)
    if p.female and face == "sharp" and mix(s, 2) % 3 != 0:
        face = "oval"   # women are mostly soft-jawed
    skin = pick(s, 3, SKIN_TONES)
    if p.female and skin == "tan" and mix(s, 4) % 2 == 0:
        skin = "pale"
    hair_colour = "silver" if old else pick(s, 5, HAIR_COLOURS)
    if greying:
        hair_colour = "grey_" + hair_colour
    front = pick(s, 6, FRONTS_F if p.female else FRONTS_M)
    back = pick(s, 7, BACKS_F if p.female else BACKS_M)
    if not p.female and p.rank in ("elder", "sect_master"):
        back = "bun"            # 道髻: the sect's men of standing tie their hair up
        if front == "spiky":
            front = "split"
    if p.female and p.rank in ("elder", "sect_master") and back in ("twin_buns", "ponytail"):
        back = "half_up"
    if p.female and back == "twin_buns" and (p.tier >= 2 or p.age_years > 40):
        back = "long"
    mood = p.mood
    eye_shape = pick(s, 8, EYE_SHAPES_F if p.female else EYE_SHAPES_M)
    if mood == "aggression":
        eye_shape = "sharp"
    elif mood == "wanderlust":
        eye_shape = "round"
    elif mood == "caution" and eye_shape == "sharp":
        eye_shape = "almond"
    if p.female and eye_shape == "sharp" and mood != "aggression":
        eye_shape = "almond"
    brow = {"aggression": "angry", "ambition": "arched", "caution": "worried",
            "wanderlust": "arched", "loyalty": "straight", "calm": "straight"}[mood]
    if mood == "calm":
        brow = pick(s, 9, ["straight", "arched", "straight"])
    mouth = {"aggression": "frown", "ambition": "smirk", "caution": "small", "wanderlust": "open",
             "loyalty": "neutral", "calm": "smile"}[mood]
    if mood == "calm":
        mouth = pick(s, 10, ["smile", "neutral", "small"])
    nose = mix(s, 11) % 3 != 0
    blush = p.female or (p.age_years < 30 and mix(s, 12) % 2 == 0)
    robe = ("plain", "dyed", "brocade", "court")[p.tier] + ("_f" if p.female else "_m")
    accent = 6 if p.sect_id < 0 else p.sect_id % 6
    headwear = {"sect_master": "crown", "elder": "headband", "inner": "ribbon", "outer": "none", "rogue": "none"}[p.rank]
    if p.rank == "rogue" and mix(s, 13) % 3 == 0:
        headwear = "cloth"
    if p.female and headwear == "crown":
        headwear = "phoenix_pin"
    if p.female and headwear == "headband":
        headwear = "jade_pin"
    marks = []
    if p.injury >= 20:
        marks.append("bandage")
    if p.injury >= 50 or (p.injury >= 20 and mood == "aggression"):
        marks.append("scar")
    return Choice(face, skin, hair_colour, front, back, eye_shape, p.dominant_element, brow, mouth, nose, blush,
                  robe, accent, headwear, marks, old, not p.alive)
