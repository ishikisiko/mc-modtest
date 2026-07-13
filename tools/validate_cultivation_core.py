#!/usr/bin/env python3
"""Validate shipped cultivation registry definitions and translations."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
REGISTRY_ROOT = Path("src/main/resources/data/myvillage/myvillage")
LANG_ROOT = Path("src/main/resources/assets/myvillage/lang")

RESOURCE_LOCATION_PATTERN = re.compile(
    r"[a-z0-9_.-]+:[a-z0-9/._-]+", re.ASCII
)
TECHNIQUE_CATEGORIES = frozenset({"core", "active", "movement", "body"})

REQUIRED_ELEMENTS = frozenset(
    {
        "myvillage:metal",
        "myvillage:wood",
        "myvillage:water",
        "myvillage:fire",
        "myvillage:earth",
    }
)
REQUIRED_REALMS = frozenset(
    {
        "myvillage:mortal",
        "myvillage:qi_refining",
        "myvillage:foundation_establishment",
    }
)
REQUIRED_STAGE_OWNERS = {
    "myvillage:mortal_unawakened": "myvillage:mortal",
    "myvillage:mortal_qi_sensed": "myvillage:mortal",
    **{
        f"myvillage:qi_refining_{index}": "myvillage:qi_refining"
        for index in range(1, 10)
    },
    "myvillage:foundation_early": "myvillage:foundation_establishment",
}
REQUIRED_TECHNIQUE = "myvillage:basic_breathing"
MAX_AWAKENING_WEIGHT = 1_000_000
REQUIRED_REALM_LIFESPANS = {
    "myvillage:mortal": 80,
    "myvillage:qi_refining": 120,
    "myvillage:foundation_establishment": 240,
}
REQUIRED_STAGE_CAPS = {
    "myvillage:mortal_qi_sensed": 1000,
    "myvillage:qi_refining_1": 1100,
    "myvillage:qi_refining_2": 1200,
    "myvillage:qi_refining_3": 1300,
}
REQUIRED_STAGE_SPIRIT_STONE_COSTS = {
    "myvillage:mortal_qi_sensed": 1,
    "myvillage:qi_refining_1": 1,
    "myvillage:qi_refining_2": 2,
    "myvillage:qi_refining_3": 3,
}
REQUIRED_ADVANCEMENTS = {
    "myvillage:mortal_qi_sensed": {
        "target_realm": "myvillage:qi_refining",
        "target_stage": "myvillage:qi_refining_1",
        "kind": "ordinary",
        "duration_ticks": 100,
        "required_stability": 500,
        "stability_cost": 250,
        "interruption_stability_loss": 0,
    },
    "myvillage:qi_refining_1": {
        "target_realm": "myvillage:qi_refining",
        "target_stage": "myvillage:qi_refining_2",
        "kind": "ordinary",
        "duration_ticks": 100,
        "required_stability": 550,
        "stability_cost": 275,
        "interruption_stability_loss": 0,
    },
    "myvillage:qi_refining_2": {
        "target_realm": "myvillage:qi_refining",
        "target_stage": "myvillage:qi_refining_3",
        "kind": "ordinary",
        "duration_ticks": 120,
        "required_stability": 600,
        "stability_cost": 300,
        "interruption_stability_loss": 0,
    },
    "myvillage:qi_refining_3": {
        "target_realm": "myvillage:qi_refining",
        "target_stage": "myvillage:qi_refining_4",
        "kind": "bottleneck",
        "duration_ticks": 200,
        "required_stability": 650,
        "stability_cost": 325,
        "interruption_stability_loss": 5,
    },
}


class DuplicateJsonKey(ValueError):
    """Raised when JSON contains a key that the normal decoder would discard."""


def reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateJsonKey(f"duplicate JSON key {key!r}")
        result[key] = value
    return result


def is_resource_location(value: str) -> bool:
    return RESOURCE_LOCATION_PATTERN.fullmatch(value) is not None


@dataclass(frozen=True)
class TechniqueReferences:
    identifier: str
    location: str
    elements: tuple[str, ...]
    minimum_realm: str | None
    minimum_stage: str | None
    affinity_elements: tuple[str, ...]


@dataclass(frozen=True)
class ValidationResult:
    errors: tuple[str, ...]
    realm_count: int
    stage_count: int
    element_count: int
    technique_count: int


class CultivationValidator:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.errors: list[str] = []
        self.translation_keys: set[str] = set()

        self.realm_ids: set[str] = set()
        self.realm_stages: dict[str, set[str]] = {}
        self.stage_owners: dict[str, list[str]] = {}
        self.realm_next: dict[str, str] = {}
        self.realm_lifespans: dict[str, int] = {}
        self.stage_caps: dict[str, int] = {}
        self.stage_spirit_stone_costs: dict[str, int] = {}
        self.stage_advancements: dict[str, dict[str, Any]] = {}

        self.element_ids: set[str] = set()
        self.technique_ids: set[str] = set()
        self.technique_references: list[TechniqueReferences] = []

    def path_label(self, path: Path) -> str:
        try:
            return path.relative_to(self.root).as_posix()
        except ValueError:
            return str(path)

    def error(self, location: str, message: str) -> None:
        self.errors.append(f"{location}: {message}")

    def load_object(self, path: Path) -> dict[str, Any] | None:
        label = self.path_label(path)
        try:
            text = path.read_text(encoding="utf-8")
            value = json.loads(text, object_pairs_hook=reject_duplicate_keys)
        except OSError as exc:
            self.error(label, f"cannot read file: {exc}")
            return None
        except UnicodeError as exc:
            self.error(label, f"file is not valid UTF-8: {exc}")
            return None
        except (json.JSONDecodeError, DuplicateJsonKey) as exc:
            self.error(label, f"invalid JSON: {exc}")
            return None

        if type(value) is not dict:
            self.error(label, "top-level JSON value must be an object")
            return None
        return value

    def registry_entries(self, registry_name: str) -> list[tuple[str, Path]]:
        directory = self.root / REGISTRY_ROOT / registry_name
        label = self.path_label(directory)
        if not directory.is_dir():
            self.error(label, "missing registry directory")
            return []

        paths = sorted(directory.rglob("*.json"))
        if not paths:
            self.error(label, "registry directory contains no JSON definitions")
            return []

        entries: list[tuple[str, Path]] = []
        seen: dict[str, Path] = {}
        for path in paths:
            entry_path = path.relative_to(directory).with_suffix("").as_posix()
            identifier = f"myvillage:{entry_path}"
            if not is_resource_location(identifier):
                self.error(
                    self.path_label(path),
                    f"file path does not map to a valid resource id: {identifier!r}",
                )
                continue
            if identifier in seen:
                self.error(
                    self.path_label(path),
                    f"duplicate registry id {identifier}; first defined by "
                    f"{self.path_label(seen[identifier])}",
                )
                continue
            seen[identifier] = path
            entries.append((identifier, path))
        return entries

    def required_string(
        self, value: dict[str, Any], field: str, location: str
    ) -> str | None:
        if field not in value:
            self.error(location, f"missing required field {field!r}")
            return None
        field_value = value[field]
        if type(field_value) is not str:
            self.error(location, f"field {field!r} must be a string")
            return None
        if not field_value.strip():
            self.error(location, f"field {field!r} must not be empty")
            return None
        return field_value

    def required_resource_location(
        self, value: dict[str, Any], field: str, location: str
    ) -> str | None:
        field_value = self.required_string(value, field, location)
        if field_value is not None and not is_resource_location(field_value):
            self.error(
                location,
                f"field {field!r} is not a valid namespaced resource id: "
                f"{field_value!r}",
            )
            return None
        return field_value

    def optional_resource_location(
        self, value: dict[str, Any], field: str, location: str
    ) -> str | None:
        if field not in value:
            return None
        return self.required_resource_location(value, field, location)

    def required_integer(
        self,
        value: dict[str, Any],
        field: str,
        location: str,
        minimum: int,
        maximum: int | None = None,
    ) -> int | None:
        if field not in value:
            self.error(location, f"missing required field {field!r}")
            return None
        field_value = value[field]
        if type(field_value) is not int:
            self.error(location, f"field {field!r} must be an integer")
            return None
        if field_value < minimum or (maximum is not None and field_value > maximum):
            expected = f"{minimum}..{maximum}" if maximum is not None else f">= {minimum}"
            self.error(
                location,
                f"field {field!r} must be {expected}, got {field_value}",
            )
            return None
        return field_value

    def record_translation_key(self, value: dict[str, Any], location: str) -> None:
        translation_key = self.required_string(value, "translation_key", location)
        if translation_key is not None:
            self.translation_keys.add(translation_key)

    def validate_realms(self) -> None:
        entries = self.registry_entries("realm")
        self.realm_ids.update(identifier for identifier, _path in entries)

        for identifier, path in entries:
            location = f"{self.path_label(path)} ({identifier})"
            value = self.load_object(path)
            self.realm_stages.setdefault(identifier, set())
            if value is None:
                continue

            self.record_translation_key(value, location)
            self.required_integer(value, "sort_order", location, 0)
            maximum_lifespan = self.required_integer(
                value, "maximum_lifespan_years", location, 1
            )
            if maximum_lifespan is not None:
                self.realm_lifespans[identifier] = maximum_lifespan

            next_realm = self.optional_resource_location(value, "next_realm", location)
            if next_realm is not None:
                self.realm_next[identifier] = next_realm

            if "stages" not in value:
                self.error(location, "missing required field 'stages'")
                continue
            stages = value["stages"]
            if type(stages) is not list:
                self.error(location, "field 'stages' must be a list")
                continue
            if not stages:
                self.error(location, "field 'stages' must contain at least one stage")
                continue

            stage_order_owners: dict[int, str] = {}
            previous_stage_order: int | None = None
            for index, stage in enumerate(stages):
                stage_location = f"{location}.stages[{index}]"
                if type(stage) is not dict:
                    self.error(stage_location, "stage must be an object")
                    continue

                stage_id = self.required_resource_location(
                    stage, "id", stage_location
                )
                self.record_translation_key(stage, stage_location)
                stage_order = self.required_integer(
                    stage, "sort_order", stage_location, 0
                )

                cultivation_cap: int | None = None
                if "cultivation_cap" in stage:
                    cultivation_cap = self.required_integer(
                        stage, "cultivation_cap", stage_location, 1
                    )
                    if stage_id is not None and cultivation_cap is not None:
                        self.stage_caps[stage_id] = cultivation_cap

                spirit_stone_cost: int | None = None
                if "spirit_stone_cost" in stage:
                    spirit_stone_cost = self.required_integer(
                        stage, "spirit_stone_cost", stage_location, 1
                    )
                    if stage_id is not None and spirit_stone_cost is not None:
                        self.stage_spirit_stone_costs[stage_id] = spirit_stone_cost
                if cultivation_cap is not None and spirit_stone_cost is None:
                    self.error(
                        stage_location,
                        "a cultivatable stage must declare positive spirit_stone_cost",
                    )

                advancement = stage.get("advancement")
                if advancement is not None:
                    if type(advancement) is not dict:
                        self.error(stage_location, "field 'advancement' must be an object")
                    elif stage_id is not None:
                        advancement_location = f"{stage_location}.advancement"
                        target_realm = self.required_resource_location(
                            advancement, "target_realm", advancement_location
                        )
                        target_stage = self.required_resource_location(
                            advancement, "target_stage", advancement_location
                        )
                        kind = self.required_string(
                            advancement, "kind", advancement_location
                        )
                        if kind is not None and kind not in {"ordinary", "bottleneck"}:
                            self.error(
                                advancement_location,
                                "field 'kind' must be ordinary or bottleneck",
                            )
                        duration = self.required_integer(
                            advancement, "duration_ticks", advancement_location, 1
                        )
                        required_stability = self.required_integer(
                            advancement, "required_stability", advancement_location, 0
                        )
                        stability_cost = self.required_integer(
                            advancement, "stability_cost", advancement_location, 0
                        )
                        interruption_loss = self.required_integer(
                            advancement,
                            "interruption_stability_loss",
                            advancement_location,
                            0,
                        )
                        if (
                            required_stability is not None
                            and stability_cost is not None
                            and stability_cost > required_stability
                        ):
                            self.error(
                                advancement_location,
                                "stability_cost must not exceed required_stability",
                            )
                        if cultivation_cap is None:
                            self.error(
                                advancement_location,
                                "advancement requires a positive cultivation_cap",
                            )
                        elif required_stability is not None:
                            expected_stability = cultivation_cap // 2
                            if required_stability != expected_stability:
                                self.error(
                                    advancement_location,
                                    "required_stability must equal half of cultivation_cap",
                                )
                            if stability_cost is not None:
                                expected_cost = expected_stability - expected_stability // 2
                                if stability_cost != expected_cost:
                                    self.error(
                                        advancement_location,
                                        "stability_cost must retain half of required stability",
                                    )
                        if all(
                            value is not None
                            for value in (
                                target_realm,
                                target_stage,
                                kind,
                                duration,
                                required_stability,
                                stability_cost,
                                interruption_loss,
                            )
                        ) and kind in {"ordinary", "bottleneck"}:
                            self.stage_advancements[stage_id] = {
                                "target_realm": target_realm,
                                "target_stage": target_stage,
                                "kind": kind,
                                "duration_ticks": duration,
                                "required_stability": required_stability,
                                "stability_cost": stability_cost,
                                "interruption_stability_loss": interruption_loss,
                            }

                if stage_id is not None:
                    previous_owners = self.stage_owners.setdefault(stage_id, [])
                    if previous_owners:
                        self.error(
                            stage_location,
                            f"stage id {stage_id} duplicates stage owned by "
                            f"{previous_owners[0]}",
                        )
                    previous_owners.append(identifier)
                    self.realm_stages[identifier].add(stage_id)

                if stage_order is not None:
                    previous_stage = stage_order_owners.get(stage_order)
                    if previous_stage is not None:
                        self.error(
                            stage_location,
                            f"sort_order {stage_order} duplicates stage "
                            f"{previous_stage} in realm {identifier}",
                        )
                    else:
                        stage_order_owners[stage_order] = stage_id or f"index {index}"
                    if (
                        previous_stage_order is not None
                        and stage_order <= previous_stage_order
                    ):
                        self.error(
                            stage_location,
                            "stage sort_order values must be strictly increasing "
                            f"in realm {identifier}",
                        )
                    previous_stage_order = stage_order

        for identifier, next_realm in sorted(self.realm_next.items()):
            if next_realm not in self.realm_ids:
                self.error(
                    f"realm {identifier}",
                    f"next_realm references missing realm {next_realm}",
                )

        for source_stage, advancement in sorted(self.stage_advancements.items()):
            target_realm = advancement["target_realm"]
            target_stage = advancement["target_stage"]
            owners = self.stage_owners.get(target_stage, [])
            if target_realm not in self.realm_ids:
                self.error(
                    f"stage {source_stage}",
                    f"advancement references missing target realm {target_realm}",
                )
            elif target_realm not in owners:
                self.error(
                    f"stage {source_stage}",
                    f"advancement target stage {target_stage} does not belong to {target_realm}",
                )
            if target_stage == source_stage:
                self.error(f"stage {source_stage}", "advancement must not target itself")

    def validate_elements(self) -> None:
        entries = self.registry_entries("spiritual_element")
        self.element_ids.update(identifier for identifier, _path in entries)

        for identifier, path in entries:
            location = f"{self.path_label(path)} ({identifier})"
            value = self.load_object(path)
            if value is None:
                continue

            self.record_translation_key(value, location)
            self.required_integer(value, "sort_order", location, 0)

            if "display_color" in value:
                self.required_integer(
                    value, "display_color", location, 0, 0xFFFFFF
                )
            if "awakening_weight" in value:
                self.required_integer(
                    value,
                    "awakening_weight",
                    location,
                    0,
                    MAX_AWAKENING_WEIGHT,
                )

    def validate_techniques(self) -> None:
        entries = self.registry_entries("technique")
        self.technique_ids.update(identifier for identifier, _path in entries)

        for identifier, path in entries:
            location = f"{self.path_label(path)} ({identifier})"
            value = self.load_object(path)
            if value is None:
                continue

            self.record_translation_key(value, location)

            category = self.required_string(value, "category", location)
            if category is not None and category not in TECHNIQUE_CATEGORIES:
                allowed = ", ".join(sorted(TECHNIQUE_CATEGORIES))
                self.error(
                    location,
                    f"field 'category' must be one of {allowed}, got {category!r}",
                )
            self.required_integer(value, "grade", location, 0)

            elements: list[str] = []
            if "elements" not in value:
                self.error(location, "missing required field 'elements'")
            elif type(value["elements"]) is not list:
                self.error(location, "field 'elements' must be a list")
            else:
                seen_elements: set[str] = set()
                for index, element in enumerate(value["elements"]):
                    element_location = f"{location}.elements[{index}]"
                    if type(element) is not str or not is_resource_location(element):
                        self.error(
                            element_location,
                            f"element must be a namespaced resource id, got {element!r}",
                        )
                        continue
                    if element in seen_elements:
                        self.error(
                            element_location,
                            f"duplicate technique element {element}",
                        )
                        continue
                    seen_elements.add(element)
                    elements.append(element)

            minimum_realm: str | None = None
            minimum_stage: str | None = None
            affinity_elements: list[str] = []
            if "requirements" not in value:
                self.error(location, "missing required field 'requirements'")
            elif type(value["requirements"]) is not dict:
                self.error(location, "field 'requirements' must be an object")
            else:
                requirements = value["requirements"]
                requirement_location = f"{location}.requirements"
                minimum_realm = self.optional_resource_location(
                    requirements, "minimum_realm", requirement_location
                )
                minimum_stage = self.optional_resource_location(
                    requirements, "minimum_stage", requirement_location
                )
                if minimum_stage is not None and minimum_realm is None:
                    self.error(
                        requirement_location,
                        "minimum_stage requires minimum_realm",
                    )

                if "minimum_element_affinity" in requirements:
                    affinities = requirements["minimum_element_affinity"]
                    if type(affinities) is not dict:
                        self.error(
                            requirement_location,
                            "field 'minimum_element_affinity' must be an object",
                        )
                    else:
                        for element, amount in sorted(affinities.items()):
                            affinity_location = (
                                f"{requirement_location}.minimum_element_affinity"
                                f"[{element!r}]"
                            )
                            if not is_resource_location(element):
                                self.error(
                                    affinity_location,
                                    "affinity key must be a namespaced resource id",
                                )
                                continue
                            affinity_elements.append(element)
                            if type(amount) is not int:
                                self.error(
                                    affinity_location,
                                    f"affinity must be an integer, got {amount!r}",
                                )
                            elif not 0 <= amount <= 10000:
                                self.error(
                                    affinity_location,
                                    f"affinity must be 0..10000, got {amount}",
                                )

            self.technique_references.append(
                TechniqueReferences(
                    identifier,
                    location,
                    tuple(elements),
                    minimum_realm,
                    minimum_stage,
                    tuple(affinity_elements),
                )
            )

        self.validate_technique_references()

    def validate_technique_references(self) -> None:
        for technique in self.technique_references:
            for element in technique.elements:
                if element not in self.element_ids:
                    self.error(
                        technique.location,
                        f"technique {technique.identifier} references missing element "
                        f"{element}",
                    )
            for element in technique.affinity_elements:
                if element not in self.element_ids:
                    self.error(
                        technique.location,
                        f"technique {technique.identifier} affinity references missing "
                        f"element {element}",
                    )

            realm_exists = (
                technique.minimum_realm is None
                or technique.minimum_realm in self.realm_ids
            )
            stage_exists = (
                technique.minimum_stage is None
                or technique.minimum_stage in self.stage_owners
            )
            if technique.minimum_realm is not None and not realm_exists:
                self.error(
                    technique.location,
                    f"technique {technique.identifier} references missing minimum realm "
                    f"{technique.minimum_realm}",
                )
            if technique.minimum_stage is not None and not stage_exists:
                self.error(
                    technique.location,
                    f"technique {technique.identifier} references missing minimum stage "
                    f"{technique.minimum_stage}",
                )
            if (
                technique.minimum_realm is not None
                and technique.minimum_stage is not None
                and realm_exists
                and stage_exists
                and technique.minimum_stage
                not in self.realm_stages.get(technique.minimum_realm, set())
            ):
                self.error(
                    technique.location,
                    f"technique {technique.identifier} requires mismatched realm/stage: "
                    f"{technique.minimum_realm} does not own {technique.minimum_stage}",
                )

    def validate_required_foundation(self) -> None:
        for realm in sorted(REQUIRED_REALMS - self.realm_ids):
            self.error("foundation definitions", f"missing required realm {realm}")
        for element in sorted(REQUIRED_ELEMENTS - self.element_ids):
            self.error("foundation definitions", f"missing required spiritual element {element}")
        if REQUIRED_TECHNIQUE not in self.technique_ids:
            self.error(
                "foundation definitions",
                f"missing required technique {REQUIRED_TECHNIQUE}",
            )

        for realm, expected in REQUIRED_REALM_LIFESPANS.items():
            actual = self.realm_lifespans.get(realm)
            if actual != expected:
                self.error(
                    "foundation definitions",
                    f"realm {realm} maximum_lifespan_years must be {expected}, got {actual!r}",
                )
        for stage, expected in REQUIRED_STAGE_CAPS.items():
            actual = self.stage_caps.get(stage)
            if actual != expected:
                self.error(
                    "foundation definitions",
                    f"stage {stage} cultivation_cap must be {expected}, got {actual!r}",
                )
        for stage, expected in REQUIRED_STAGE_SPIRIT_STONE_COSTS.items():
            actual = self.stage_spirit_stone_costs.get(stage)
            if actual != expected:
                self.error(
                    "foundation definitions",
                    f"stage {stage} spirit_stone_cost must be {expected}, got {actual!r}",
                )
        for stage in sorted(set(self.stage_caps) - set(REQUIRED_STAGE_CAPS)):
            self.error(
                "foundation definitions",
                f"release-ceiling stage {stage} must omit cultivation_cap",
            )
        for stage, expected in REQUIRED_ADVANCEMENTS.items():
            actual = self.stage_advancements.get(stage)
            if actual != expected:
                self.error(
                    "foundation definitions",
                    f"stage {stage} advancement must be {expected!r}, got {actual!r}",
                )
        for stage in sorted(set(self.stage_advancements) - set(REQUIRED_ADVANCEMENTS)):
            self.error(
                "foundation definitions",
                f"release-ceiling stage {stage} must omit advancement",
            )

        for stage, expected_realm in sorted(REQUIRED_STAGE_OWNERS.items()):
            if stage in self.realm_stages.get(expected_realm, set()):
                continue
            owners = self.stage_owners.get(stage, [])
            if owners:
                self.error(
                    "foundation definitions",
                    f"required stage {stage} must belong to {expected_realm}; found in "
                    f"{', '.join(owners)}",
                )
            else:
                self.error(
                    "foundation definitions",
                    f"missing required stage {stage} in realm {expected_realm}",
                )

    def validate_translations(self) -> None:
        for locale in ("en_us", "zh_cn"):
            path = self.root / LANG_ROOT / f"{locale}.json"
            if not path.is_file():
                self.error(self.path_label(path), "missing language file")
                continue
            language = self.load_object(path)
            if language is None:
                continue
            for key in sorted(self.translation_keys):
                if key not in language:
                    self.error(
                        self.path_label(path),
                        f"missing declared translation key {key!r}",
                    )
                elif type(language[key]) is not str or not language[key].strip():
                    self.error(
                        self.path_label(path),
                        f"translation {key!r} must be a non-empty string",
                    )

    def validate(self) -> ValidationResult:
        self.validate_realms()
        self.validate_elements()
        self.validate_techniques()
        self.validate_required_foundation()
        self.validate_translations()
        return ValidationResult(
            tuple(self.errors),
            len(self.realm_ids),
            len(self.stage_owners),
            len(self.element_ids),
            len(self.technique_ids),
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate shipped MyVillage cultivation registry data."
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=ROOT,
        help="repository root (defaults to the parent of this tools directory)",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = CultivationValidator(args.root.resolve()).validate()
    if result.errors:
        print(
            f"cultivation core validation failed ({len(result.errors)} error(s)):",
            file=sys.stderr,
        )
        for error in result.errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        "cultivation core validation passed: "
        f"realms={result.realm_count}, stages={result.stage_count}, "
        f"elements={result.element_count}, techniques={result.technique_count}"
    )
    print(
        "required foundation ids present: mortal -> qi_refining -> "
        "foundation_establishment; mortal stages, qi_refining_1..9, "
        "foundation_early; metal/wood/water/fire/earth; basic_breathing; "
        "realm lifespans=80/120/240; caps=1000/1100/1200/1300; "
        "stability=500/550/600/650; spirit-stone costs=1/1/2/3; "
        "four deterministic half-retention advancement rules; "
        "en_us/zh_cn translations"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
