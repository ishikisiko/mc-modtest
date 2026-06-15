"""Settlement-group descriptors.

Groups sit above style profiles. Each group binds one style id to the archetype
roster and layout strategy that are allowed for that settlement family. Add a
new group descriptor here when introducing a new family instead of branching on
style-id naming conventions elsewhere in the generator.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, Mapping, Optional, Tuple


@dataclass(frozen=True)
class SettlementGroup:
    group_id: str
    style_id: str
    archetype_roster: Tuple[str, ...]
    layout_strategy: str
    scale_params: Mapping[str, object] = field(default_factory=dict)
    classifications: Mapping[str, str] = field(default_factory=dict)


GROUPS: Dict[str, SettlementGroup] = {
    "medieval_village": SettlementGroup(
        group_id="medieval_village",
        style_id="medieval_village",
        archetype_roster=(
            "small_house", "medium_house", "blacksmith",
            "small_shop", "medium_shop", "big_house",
        ),
        layout_strategy="standalone_library",
        scale_params={"gallery_group": "medieval_village"},
        classifications={
            "small_house": "housing",
            "medium_house": "housing",
            "blacksmith": "functional",
            "small_shop": "functional",
            "medium_shop": "functional",
            "big_house": "housing",
        },
    ),
    "civic": SettlementGroup(
        group_id="civic",
        style_id="medieval_village",
        archetype_roster=("tavern", "lord_manor"),
        layout_strategy="standalone_library",
        scale_params={"gallery_group": "civic"},
        classifications={
            "tavern": "civic",
            "lord_manor": "civic",
        },
    ),
    "chinese_courtyard": SettlementGroup(
        group_id="chinese_courtyard",
        style_id="chinese_courtyard",
        archetype_roster=("main_hall", "side_wing", "front_row", "gate_house"),
        layout_strategy="courtyard_compound",
        scale_params={"gallery_group": "chinese_courtyard", "compound_count": 6},
        classifications={
            "main_hall": "civic",
            "side_wing": "housing",
            "front_row": "functional",
            "gate_house": "infrastructure",
        },
    ),
    "cultivation_town": SettlementGroup(
        group_id="cultivation_town",
        style_id="cultivation_town",
        archetype_roster=(
            "cultivation_house",
            "cultivation_shop",
            "cultivation_inn",
            "cultivation_market",
            "town_shrine",
        ),
        layout_strategy="town_generation",
        scale_params={
            "gallery_group": "cultivation_town",
            "compound_count": 6,
            "parcel_form": "courtyard_street_block",
            "soft_functional_brief": {
                "housing": 8,
                "market": 4,
                "civic": 1,
                "defense": 2,
            },
        },
        classifications={
            "cultivation_house": "housing",
            "cultivation_shop": "functional",
            "cultivation_inn": "functional",
            "cultivation_market": "infrastructure",
            "town_shrine": "civic",
        },
    ),
    "cultivation_sect": SettlementGroup(
        group_id="cultivation_sect",
        style_id="cultivation_sect",
        archetype_roster=(
            "sect_gate",
            "sect_main_hall",
            "scripture_pavilion",
            "alchemy_room",
            "disciple_quarters",
        ),
        layout_strategy="sect_terraced_axial_compound",
        scale_params={"gallery_group": "cultivation_sect", "monumental_scale": True},
        classifications={
            "sect_gate": "infrastructure",
            "sect_main_hall": "civic",
            "scripture_pavilion": "civic",
            "alchemy_room": "functional",
            "disciple_quarters": "housing",
        },
    ),
}


def get_group(group_id: str) -> SettlementGroup:
    try:
        return GROUPS[group_id]
    except KeyError:
        raise ValueError(f"unknown settlement group {group_id!r}") from None


def group_for_style(style_id: str) -> Optional[SettlementGroup]:
    for group in GROUPS.values():
        if group.style_id == style_id and group.group_id != "civic":
            return group
    return None


def validate_group_archetype(group_id: str, style_id: str, archetype: str) -> SettlementGroup:
    group = get_group(group_id)
    if style_id != group.style_id:
        raise ValueError(
            f"group {group_id!r} uses style {group.style_id!r}, got {style_id!r}")
    if archetype not in group.archetype_roster:
        raise ValueError(
            f"archetype {archetype!r} is not in settlement group "
            f"{group_id!r} roster {list(group.archetype_roster)!r}")
    return group
