"""Style Profile layer.

Loads a style JSON (abstract material slots, allowed roof/wall/opening/motif
vocabularies, forbidden blocks, proportion ranges) and exposes material slot
resolution so no template hardcodes concrete blocks.
"""

from __future__ import annotations

import json
import os
import random
from typing import Dict, List, Optional

STYLE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "styles")
OPTIONAL_MATERIAL_SLOTS = ("SPIRIT_CRYSTAL", "RITUAL_METAL")


def _block_id(state: str) -> str:
    return state.split("[", 1)[0]


class Style:
    def __init__(self, data: dict) -> None:
        self.style_id: str = data["style_id"]
        self.material_slots: Dict[str, List[str]] = data["material_slots"]
        for slot in OPTIONAL_MATERIAL_SLOTS:
            self.material_slots.setdefault(slot, [])
        self.variation_rate: Dict[str, float] = data.get("variation_rate", {})
        self.allowed_roof_types: List[str] = data["allowed_roof_types"]
        self.allowed_wall_types: List[str] = data["allowed_wall_types"]
        self.allowed_opening_styles: List[str] = data["allowed_opening_styles"]
        self.allowed_motifs: List[str] = data["allowed_motifs"]
        self.forbidden_blocks: List[str] = data["forbidden_blocks"]
        self.proportions: dict = data["proportions"]

    # ---- material slot resolution -------------------------------------

    def primary(self, slot: str) -> str:
        """The slot's primary material (first entry)."""
        return self.material_slots[slot][0]

    def alternates(self, slot: str) -> List[str]:
        return self.material_slots[slot][1:]

    def pick(self, slot: str, rng: random.Random) -> str:
        return rng.choice(self.material_slots[slot])

    def has_slot(self, slot: str) -> bool:
        return bool(self.material_slots.get(slot))

    def slot_options(self, slot: str, contains: Optional[str] = None) -> List[str]:
        entries = list(self.material_slots.get(slot, []))
        if contains is None:
            return entries
        return [state for state in entries if contains in _block_id(state)]

    def slot_entry(self, slot: str, contains: str, default: Optional[str] = None) -> str:
        """Pick the slot entry whose block id contains a substring.

        Used for role-based slots, e.g. ROOF_DARK -> '_stairs' / '_slab' /
        '_planks', so ops never hardcode concrete blocks.
        """
        for state in self.material_slots[slot]:
            if contains in _block_id(state):
                return state
        if default is not None:
            return default
        raise KeyError(f"slot {slot} has no entry containing {contains!r}")

    def optional_slot_entry(self, slot: str, contains: str,
                            default: Optional[str] = None) -> Optional[str]:
        for state in self.material_slots.get(slot, []):
            if contains in _block_id(state):
                return state
        return default

    # ---- proportions / rules ------------------------------------------

    def prop_range(self, key: str) -> tuple:
        lo, hi = self.proportions[key]
        return lo, hi

    def prop(self, key: str):
        return self.proportions[key]

    def is_forbidden(self, state: str) -> bool:
        block = _block_id(state)
        return any(frag in block for frag in self.forbidden_blocks)


def load_style(style_id: str) -> Style:
    path = os.path.join(STYLE_DIR, f"{style_id}.json")
    if not os.path.isfile(path):
        raise FileNotFoundError(f"unknown style {style_id!r}: {path}")
    with open(path, "r", encoding="utf-8") as f:
        style = Style(json.load(f))
    from .ops import validate_style_vocabulary
    validate_style_vocabulary(style)
    return style
