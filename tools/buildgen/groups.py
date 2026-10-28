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
    "thunder_tiger_lair": SettlementGroup(
        group_id="thunder_tiger_lair", style_id="chinese_courtyard",
        archetype_roster=("valley", "arena", "pillar"), layout_strategy="natural_boss_valley",
        classifications={"valley": "natural", "arena": "natural", "pillar": "natural"},
    ),
    "duange_ruin": SettlementGroup(
        group_id="duange_ruin", style_id="chinese_courtyard",
        archetype_roster=("gate", "command_hall"), layout_strategy="ruined_garrison",
        classifications={"gate": "infrastructure", "command_hall": "civic"},
    ),
    "fuyao_ruin": SettlementGroup(
        group_id="fuyao_ruin", style_id="chinese_courtyard",
        archetype_roster=("gate", "sanctum"), layout_strategy="sunken_sanctuary",
        classifications={"gate": "infrastructure", "sanctum": "civic"},
    ),
    "danxia_ruin": SettlementGroup(
        group_id="danxia_ruin", style_id="chinese_courtyard",
        archetype_roster=("gate", "main_hall", "east_wing"),
        layout_strategy="ruined_monastery",
        classifications={"gate": "infrastructure", "main_hall": "civic", "east_wing": "functional"},
    ),
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
            "pagoda",
            "pavilion",
            "bell_drum_tower",
        ),
        layout_strategy="town_generation",
        scale_params={
            "gallery_group": "cultivation_town",
            "compound_count": 6,
            "parcel_form": "courtyard_street_block",
            # Mid-size fair footprint cap consumed by the town planner.
            "footprint": {"width": 160, "depth": 160},
            # District brief replaces the flat soft_functional_brief. The planner
            # consumes it generically by ``kind`` (gate/market/residential/
            # civic_core/fringe); generation passes MUST NOT branch on style_id
            # or district-name strings. ``density`` is the target frontage-module
            # count for the district (geometry may cap it). ``storey_band`` is the
            # [min, max] building storeys. ``archetypes`` bind the roster the
            # district draws fill from; the static cultivation_town_NNN compound
            # library supplies additional courtyard tissue, not a standalone town.
            "district_brief": [
                {
                    "kind": "gate",
                    "density": 2,
                    "storey_band": [1, 1],
                    "material_register": "timber_lantern_gate",
                    "archetypes": ["cultivation_house", "cultivation_shop"],
                },
                {
                    "kind": "market",
                    "density": 5,
                    "storey_band": [1, 2],
                    "material_register": "timber_painted_market",
                    "archetypes": ["cultivation_shop", "cultivation_market"],
                },
                {
                    "kind": "residential",
                    "density": 5,
                    "storey_band": [1, 2],
                    "material_register": "timber_plain_house",
                    "archetypes": ["cultivation_house"],
                },
                {
                    "kind": "civic_core",
                    "density": 3,
                    "storey_band": [2, 3],
                    "material_register": "timber_ceremonial",
                    "archetypes": ["town_shrine", "cultivation_inn", "cultivation_shop", "pagoda", "pavilion", "bell_drum_tower"],
                },
                {
                    "kind": "fringe",
                    "density": 1,
                    "storey_band": [1, 1],
                    "material_register": "field_stone_fringe",
                    "archetypes": ["cultivation_house"],
                },
            ],
        },
        classifications={
            "cultivation_house": "housing",
            "cultivation_shop": "functional",
            "cultivation_inn": "functional",
            "cultivation_market": "infrastructure",
            "town_shrine": "civic",
            "pagoda": "civic",
            "pavilion": "civic",
            "bell_drum_tower": "civic",
        },
    ),
    "chinese_mansion": SettlementGroup(
        group_id="chinese_mansion",
        style_id="chinese_mansion",
        archetype_roster=(
            "main_hall",
            "open_hall",
            "side_wing",
            "flower_hall",
            "front_row",
            "gate_house",
            "tower_house",
            "garden_pavilion",
            "service_house",
        ),
        layout_strategy="mansion_compound",
        scale_params={"gallery_group": "chinese_mansion", "compound_count": 6},
        classifications={
            "main_hall": "civic",
            "open_hall": "civic",
            "side_wing": "housing",
            "flower_hall": "civic",
            "front_row": "functional",
            "tower_house": "housing",
            "garden_pavilion": "civic",
            "service_house": "functional",
        },
    ),
    "chinese_huipai_mansion": SettlementGroup(
        group_id="chinese_huipai_mansion",
        style_id="chinese_huipai_mansion",
        archetype_roster=(
            "huipai_entry_hall",
            "huipai_family_hall",
            "huipai_rear_hall",
        ),
        layout_strategy="huipai_tianjing_reference_slice",
        scale_params={
            "gallery_group": "chinese_huipai_mansion",
            "compound_count": 2,
            "reference_candidate": "candidate_003",
        },
        classifications={
            "huipai_entry_hall": "infrastructure",
            "huipai_family_hall": "civic",
            "huipai_rear_hall": "housing",
        },
    ),
    "ganlan_stilted_house": SettlementGroup(
        group_id="ganlan_stilted_house",
        style_id="ganlan_stilted_house",
        archetype_roster=(
            "ganlan_small_house",
            "ganlan_medium_house",
        ),
        layout_strategy="ganlan_stilted_reference_slice",
        scale_params={
            "gallery_group": "ganlan_stilted_house",
            "compound_count": 2,
            "reference_candidate": "candidate_005",
        },
        classifications={
            "ganlan_small_house": "housing",
            "ganlan_medium_house": "housing",
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


# Region-level recipe; existing building owners still generate every building.
# Appended after existing groups so style-to-group lookup remains unchanged.
GROUPS["river_valley"] = SettlementGroup(
    group_id="river_valley", style_id="cultivation_town",
    archetype_roster=("cultivation_house", "cultivation_inn", "cultivation_shop"),
    layout_strategy="region_recipe",
    classifications={"cultivation_house": "housing", "cultivation_inn": "functional",
                     "cultivation_shop": "functional"},
)

GROUPS["bay_terraces"] = SettlementGroup(
    group_id="bay_terraces", style_id="cultivation_town",
    archetype_roster=("cultivation_house", "cultivation_inn", "cultivation_shop"),
    layout_strategy="terrace_market_region",
    classifications={"cultivation_house": "housing", "cultivation_inn": "functional",
                     "cultivation_shop": "functional"},
)

GROUPS["qianzhen_chapter_sites"] = SettlementGroup(
    group_id="qianzhen_chapter_sites", style_id="cultivation_town",
    archetype_roster=("relay_inn", "apothecary", "canal_gardens", "old_relay", "spring_array", "initiation_court"),
    layout_strategy="authored_oasis_region",
    classifications={"relay_inn": "functional", "apothecary": "functional", "canal_gardens": "garden",
                     "old_relay": "ruin", "spring_array": "civic", "initiation_court": "civic"},
)


# Explicit capital families.  The owning exporters consume these descriptors;
# neither this registry nor consumers infer a region from a name prefix.
# Existing groups stay ahead of these additions in style-to-group lookup.
_CAPITAL_FAMILIES = {
    "zhongzhou": ("chinese_huipai_mansion", "full_yunshan_authored", "central", None),
    "donghai": ("province_capital_donghai", "coastal_harbour", "regional", (224, 272)),
    "ximo": ("province_capital_ximo", "oasis_courtyard_routes", "regional", (224, 272)),
    "nanjiang": ("province_capital_nanjiang", "stream_bank_boardwalks", "regional", (224, 272)),
    "beiyuan": ("province_capital_beiyuan", "sheltered_courtyard_streets", "regional", (224, 272)),
    "donghuang": ("province_capital_donghuang", "long_valley_relay_streets", "regional", (160, 416)),
    "lingyue": ("province_capital_lingyue", "supported_ridge_terraces", "regional", (224, 272)),
    "moyu": ("province_capital_moyu", "connected_rift_neighbourhoods", "regional", (224, 272)),
}
for _region_id, (_style_id, _strategy, _owner, _footprint) in _CAPITAL_FAMILIES.items():
    _group_id = "province_capital_" + _region_id
    GROUPS[_group_id] = SettlementGroup(
        group_id=_group_id,
        style_id=_style_id,
        archetype_roster=(
            "cultivation_house", "cultivation_inn", "cultivation_shop",
            "cultivation_market", "pavilion", "bell_drum_tower", "town_shrine",
        ),
        layout_strategy=_strategy,
        scale_params={
            "region_id": _region_id,
            "city_id": "capital_" + _region_id,
            "exporter": "tools/province_capitals/" + _owner + "/export_game.py",
            "resource_format": "capital_game_bundle",
            "target_scale": "full_yunshan" if _footprint is None else "d1_district",
            **({"reference_footprint": {"width": _footprint[0], "depth": _footprint[1]},
                "reference_only": True} if _footprint is not None else {
                    "authored_extent_source": "blueprints/province_capitals/central/game-spec.json",
                    "auxiliary_style_ids": ("cultivation_sect",)}),
        },
        classifications={
            "cultivation_house": "housing", "cultivation_inn": "functional",
            "cultivation_shop": "functional", "cultivation_market": "functional",
            "pavilion": "infrastructure", "bell_drum_tower": "infrastructure",
            "town_shrine": "civic",
        },
    )


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
