"""Style Profile layer.

Loads a style JSON (abstract material slots, allowed roof/wall/opening/motif
vocabularies, forbidden blocks, proportion ranges) and exposes material slot
resolution so no template hardcodes concrete blocks.
"""

from __future__ import annotations

import json
import os
import random
from typing import Dict, Iterable, List, Optional, Set

STYLE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "styles")
REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
MOD_BLOCK_CATALOG_PATH = os.path.join(REPO_ROOT, "exmod", "mod_block_catalog.json")
VANILLA_NAMESPACE = "minecraft"
OPTIONAL_MATERIAL_SLOTS = (
    "SPIRIT_CRYSTAL",
    "RITUAL_METAL",
    "ROOF_TILE",
    "PAPER_LANTERN",
    "RITUAL_ANCHOR",
    "MARKET_FITTINGS",
    "COLUMN",
    "PLATFORM_STONE",
    "RIDGE_ORNAMENT",
    "BALUSTRADE",
)


def _block_id(state: str) -> str:
    return state.split("[", 1)[0]


def _namespace(state: str) -> str:
    block = _block_id(state)
    if ":" not in block:
        return VANILLA_NAMESPACE
    return block.split(":", 1)[0]


def _copy_material_slots(slots: dict) -> Dict[str, List[str]]:
    return {slot: list(entries) for slot, entries in slots.items()}


def _validate_vanilla_fallbacks(style_id: str, slots: Dict[str, List[str]]) -> None:
    errors = []
    for slot, entries in sorted(slots.items()):
        if not entries:
            if slot not in OPTIONAL_MATERIAL_SLOTS:
                errors.append(f"{slot}=empty")
            continue
        if _namespace(entries[-1]) != VANILLA_NAMESPACE:
            errors.append(f"{slot} last={entries[-1]!r}")
    if errors:
        raise ValueError(
            f"style {style_id!r} material slots must end with minecraft fallbacks: "
            + "; ".join(errors))


def _filter_material_slots(style_id: str, slots: Dict[str, List[str]],
                           available_namespaces: Set[str]) -> Dict[str, List[str]]:
    filtered = {
        slot: [state for state in entries if _namespace(state) in available_namespaces]
        for slot, entries in slots.items()
    }
    empty_required = sorted(
        slot for slot, entries in filtered.items()
        if slot not in OPTIONAL_MATERIAL_SLOTS and not entries
    )
    if empty_required:
        raise ValueError(
            f"style {style_id!r} has empty required material slots after "
            f"namespace filtering: {empty_required}")
    return filtered


def modset_namespaces(profile: str, catalog_path: Optional[str] = None) -> Set[str]:
    """Return the active namespaces for a named modset profile."""
    if profile == "vanilla":
        return {VANILLA_NAMESPACE}
    if profile != "full":
        raise KeyError(f"unknown modset profile {profile!r}")

    path = catalog_path or MOD_BLOCK_CATALOG_PATH
    with open(path, "r", encoding="utf-8") as f:
        catalog = json.load(f)
    return {VANILLA_NAMESPACE, *catalog["confirmed_mod_namespaces"]}


class Style:
    def __init__(self, data: dict) -> None:
        self.style_id: str = data["style_id"]
        self.material_slots: Dict[str, List[str]] = _copy_material_slots(data["material_slots"])
        for slot in OPTIONAL_MATERIAL_SLOTS:
            self.material_slots.setdefault(slot, [])
        self.active_namespaces: Set[str] = {
            _namespace(state)
            for entries in self.material_slots.values()
            for state in entries
        }
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

    def has_external_blocks(self) -> bool:
        return any(ns != VANILLA_NAMESPACE for ns in self.active_namespaces)

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


def load_style(style_id: str,
               available_namespaces: Optional[Iterable[str]] = None) -> Style:
    path = os.path.join(STYLE_DIR, f"{style_id}.json")
    if not os.path.isfile(path):
        raise FileNotFoundError(f"unknown style {style_id!r}: {path}")
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    slots = _copy_material_slots(data["material_slots"])
    _validate_vanilla_fallbacks(data.get("style_id", style_id), slots)
    if available_namespaces is not None:
        slots = _filter_material_slots(data.get("style_id", style_id), slots,
                                       set(available_namespaces))
    data["material_slots"] = slots
    style = Style(data)
    from .ops import validate_style_vocabulary
    validate_style_vocabulary(style)
    return style
