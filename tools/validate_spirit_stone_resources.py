#!/usr/bin/env python3
"""Validate the first spirit-stone item, ore-loot, and Overworld worldgen slice."""

from __future__ import annotations

import argparse
import json
import re
import struct
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = Path("src/main/java/com/example/myvillage")
RESOURCE_ROOT = Path("src/main/resources")
ASSET_ROOT = RESOURCE_ROOT / "assets/myvillage"
DATA_ROOT = RESOURCE_ROOT / "data/myvillage"

STONE_ITEM = "low_grade_spirit_stone"
ORES = ("spirit_stone_ore", "deepslate_spirit_stone_ore")
ORE_IDS = tuple(f"myvillage:{ore}" for ore in ORES)

CONFIGURED_FEATURES = {
    "spirit_stone_ore": 6,
    "spirit_stone_ore_small": 3,
}
PLACED_FEATURES = {
    "spirit_stone_ore_upper": {
        "feature": "myvillage:spirit_stone_ore",
        "count": 30,
        "height": {
            "type": "minecraft:trapezoid",
            "min_inclusive": {"absolute": 80},
            "max_inclusive": {"absolute": 384},
        },
    },
    "spirit_stone_ore_middle": {
        "feature": "myvillage:spirit_stone_ore",
        "count": 3,
        "height": {
            "type": "minecraft:trapezoid",
            "min_inclusive": {"absolute": -24},
            "max_inclusive": {"absolute": 56},
        },
    },
    "spirit_stone_ore_deep": {
        "feature": "myvillage:spirit_stone_ore_small",
        "count": 3,
        "height": {
            "type": "minecraft:uniform",
            "min_inclusive": {"above_bottom": 0},
            "max_inclusive": {"absolute": 0},
        },
    },
}

REQUIRED_RESOURCE_FILES = (
    "assets/myvillage/lang/en_us.json",
    "assets/myvillage/lang/zh_cn.json",
    "assets/myvillage/blockstates/spirit_stone_ore.json",
    "assets/myvillage/blockstates/deepslate_spirit_stone_ore.json",
    "assets/myvillage/models/item/low_grade_spirit_stone.json",
    "assets/myvillage/models/item/spirit_stone_ore.json",
    "assets/myvillage/models/item/deepslate_spirit_stone_ore.json",
    "assets/myvillage/models/block/spirit_stone_ore.json",
    "assets/myvillage/models/block/deepslate_spirit_stone_ore.json",
    "assets/myvillage/textures/item/low_grade_spirit_stone.png",
    "assets/myvillage/textures/block/spirit_stone_ore.png",
    "assets/myvillage/textures/block/deepslate_spirit_stone_ore.png",
    "data/myvillage/loot_table/blocks/spirit_stone_ore.json",
    "data/myvillage/loot_table/blocks/deepslate_spirit_stone_ore.json",
    "data/minecraft/tags/block/mineable/pickaxe.json",
    "data/minecraft/tags/block/needs_iron_tool.json",
    "data/myvillage/worldgen/configured_feature/spirit_stone_ore.json",
    "data/myvillage/worldgen/configured_feature/spirit_stone_ore_small.json",
    "data/myvillage/worldgen/placed_feature/spirit_stone_ore_upper.json",
    "data/myvillage/worldgen/placed_feature/spirit_stone_ore_middle.json",
    "data/myvillage/worldgen/placed_feature/spirit_stone_ore_deep.json",
    "data/myvillage/tags/worldgen/biome/has_spirit_stone_ore.json",
    "data/myvillage/neoforge/biome_modifier/add_spirit_stone_ores.json",
)

FORBIDDEN_ITEM_IDS = (
    "raw_spirit_stone",
    "medium_grade_spirit_stone",
    "middle_grade_spirit_stone",
    "high_grade_spirit_stone",
    "spirit_stone_fragment",
    "spirit_stone_shard",
    "spirit_stone_block",
)


class DuplicateJsonKey(ValueError):
    """Raised when a resource hides an earlier value behind a duplicate key."""


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateJsonKey(f"duplicate JSON key {key!r}")
        result[key] = value
    return result


def _strip_java_comments(source: str) -> str:
    output: list[str] = []
    index = 0
    state = "code"
    while index < len(source):
        char = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""
        if state == "code":
            if char == '"':
                state = "string"
                output.append(char)
            elif char == "'":
                state = "char"
                output.append(char)
            elif char == "/" and following == "/":
                state = "line_comment"
                output.extend("  ")
                index += 1
            elif char == "/" and following == "*":
                state = "block_comment"
                output.extend("  ")
                index += 1
            else:
                output.append(char)
        elif state in {"string", "char"}:
            output.append(char)
            if char == "\\" and following:
                output.append(following)
                index += 1
            elif (state == "string" and char == '"') or (state == "char" and char == "'"):
                state = "code"
        elif state == "line_comment":
            if char == "\n":
                output.append(char)
                state = "code"
            else:
                output.append(" ")
        else:
            if char == "*" and following == "/":
                output.extend("  ")
                index += 1
                state = "code"
            elif char == "\n":
                output.append(char)
            else:
                output.append(" ")
        index += 1
    return "".join(output)


def _balanced_body(source: str, opening_index: int) -> str | None:
    if opening_index < 0 or source[opening_index] != "{":
        return None
    depth = 0
    state = "code"
    index = opening_index
    while index < len(source):
        char = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""
        if state == "code":
            if char == '"':
                state = "string"
            elif char == "'":
                state = "char"
            elif char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    return source[opening_index + 1 : index]
        elif char == "\\" and following:
            index += 1
        elif (state == "string" and char == '"') or (state == "char" and char == "'"):
            state = "code"
        index += 1
    return None


def _method_body(source: str, name: str) -> str | None:
    match = re.search(rf"\b{re.escape(name)}\s*\([^)]*\)\s*\{{", source)
    if match is None:
        return None
    return _balanced_body(source, source.find("{", match.start()))


def _png_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    if len(data) < 24 or not data.startswith(b"\x89PNG\r\n\x1a\n") or data[12:16] != b"IHDR":
        raise ValueError("not a PNG file")
    return struct.unpack(">II", data[16:24])


@dataclass(frozen=True)
class ValidationResult:
    errors: tuple[str, ...]
    checked_files: int
    jar_status: str


class SpiritStoneResourcesValidator:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.errors: list[str] = []
        self.checked_files: set[Path] = set()
        self._json_cache: dict[Path, Any] = {}

    def label(self, path: Path) -> str:
        try:
            return path.relative_to(self.root).as_posix()
        except ValueError:
            return str(path)

    def error(self, location: str | Path, message: str) -> None:
        label = self.label(location) if isinstance(location, Path) else location
        self.errors.append(f"{label}: {message}")

    def read_text(self, relative: Path, purpose: str = "required file") -> str | None:
        path = self.root / relative
        if not path.is_file():
            self.error(relative, f"missing {purpose}")
            return None
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeError) as exc:
            self.error(relative, f"cannot read UTF-8 file: {exc}")
            return None
        self.checked_files.add(path)
        return text

    def load_json(self, relative: Path, purpose: str = "JSON resource") -> Any | None:
        path = self.root / relative
        if path in self._json_cache:
            return self._json_cache[path]
        text = self.read_text(relative, purpose)
        if text is None:
            return None
        try:
            value = json.loads(text, object_pairs_hook=_reject_duplicate_keys)
        except (json.JSONDecodeError, DuplicateJsonKey) as exc:
            self.error(relative, f"invalid JSON: {exc}")
            return None
        self._json_cache[path] = value
        return value

    def validate_registration(self) -> None:
        item_path = JAVA_ROOT / "item/ModItems.java"
        block_path = JAVA_ROOT / "block/ModBlocks.java"
        item_text = self.read_text(item_path, "item registry")
        block_text = self.read_text(block_path, "block registry")
        if item_text is None or block_text is None:
            return
        item_source = _strip_java_comments(item_text)
        block_source = _strip_java_comments(block_text)

        patterns = {
            "low_grade_spirit_stone registration": (
                item_source,
                r"\bLOW_GRADE_SPIRIT_STONE\s*=\s*ITEMS\.registerSimpleItem\(\s*"
                r'"low_grade_spirit_stone"\s*\)',
            ),
            "spirit_stone_ore BlockItem registration": (
                item_source,
                r"\bSPIRIT_STONE_ORE_ITEM\s*=\s*ITEMS\.registerItem\(\s*"
                r'"spirit_stone_ore"\s*,.*?new\s+BlockItem\(\s*'
                r"ModBlocks\.SPIRIT_STONE_ORE\.get\(\)",
            ),
            "deepslate_spirit_stone_ore BlockItem registration": (
                item_source,
                r"\bDEEPSLATE_SPIRIT_STONE_ORE_ITEM\s*=\s*ITEMS\.registerItem\(\s*"
                r'"deepslate_spirit_stone_ore"\s*,.*?new\s+BlockItem\(\s*'
                r"ModBlocks\.DEEPSLATE_SPIRIT_STONE_ORE\.get\(\)",
            ),
            "spirit_stone_ore block registration": (
                block_source,
                r"\bSPIRIT_STONE_ORE\s*=\s*BLOCKS\.registerBlock\(\s*"
                r'"spirit_stone_ore"\s*,\s*Block::new\s*,\s*spiritStoneOreProperties\(\)',
            ),
            "deepslate_spirit_stone_ore block registration": (
                block_source,
                r"\bDEEPSLATE_SPIRIT_STONE_ORE\s*=\s*BLOCKS\.registerBlock\(\s*"
                r'"deepslate_spirit_stone_ore"\s*,\s*Block::new\s*,\s*'
                r"deepslateSpiritStoneOreProperties\(\)",
            ),
        }
        for description, (source, pattern) in patterns.items():
            if re.search(pattern, source, re.DOTALL) is None:
                self.error(item_path if source is item_source else block_path, f"missing {description}")

        for constant in (
            "LOW_GRADE_SPIRIT_STONE",
            "SPIRIT_STONE_ORE_ITEM",
            "DEEPSLATE_SPIRIT_STONE_ORE_ITEM",
        ):
            if re.search(rf"output\.accept\(\s*{constant}\.get\(\)\s*\)", item_source) is None:
                self.error(item_path, f"creative tab myvillage:main omits {constant}")

        ids_match = re.search(r"\bBLOCK_IDS\s*=\s*List\.of\((.*?)\)\s*;", block_source, re.DOTALL)
        registered_ids = set(re.findall(r'"([a-z0-9_]+)"', ids_match.group(1))) if ids_match else set()
        for ore in ORES:
            if ore not in registered_ids:
                self.error(block_path, f"registered-block verification omits myvillage:{ore}")
        verify_body = _method_body(block_source, "verifyRegistered")
        if verify_body is None or not all(
            fragment in verify_body
            for fragment in ("BLOCK_IDS", "BuiltInRegistries.BLOCK.containsKey")
        ):
            self.error(block_path, "verifyRegistered must check every BLOCK_IDS entry")

        for method in ("spiritStoneOreProperties", "deepslateSpiritStoneOreProperties"):
            body = _method_body(block_source, method)
            if body is None or ".requiresCorrectToolForDrops()" not in body:
                self.error(block_path, f"{method} must require the correct tool for drops")

        for forbidden in FORBIDDEN_ITEM_IDS:
            if re.search(rf'"{re.escape(forbidden)}"', item_source + block_source):
                self.error(JAVA_ROOT, f"forbidden intermediate or higher-grade id myvillage:{forbidden}")

    def validate_assets(self) -> None:
        expected_models: dict[Path, dict[str, Any]] = {
            ASSET_ROOT / "models/item/low_grade_spirit_stone.json": {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": "myvillage:item/low_grade_spirit_stone"},
            },
            ASSET_ROOT / "models/item/spirit_stone_ore.json": {
                "parent": "myvillage:block/spirit_stone_ore",
            },
            ASSET_ROOT / "models/item/deepslate_spirit_stone_ore.json": {
                "parent": "myvillage:block/deepslate_spirit_stone_ore",
            },
            ASSET_ROOT / "models/block/spirit_stone_ore.json": {
                "parent": "minecraft:block/cube_all",
                "textures": {"all": "myvillage:block/spirit_stone_ore"},
            },
            ASSET_ROOT / "models/block/deepslate_spirit_stone_ore.json": {
                "parent": "minecraft:block/cube_all",
                "textures": {"all": "myvillage:block/deepslate_spirit_stone_ore"},
            },
        }
        for path, expected in expected_models.items():
            value = self.load_json(path)
            if not isinstance(value, dict):
                continue
            for key, expected_value in expected.items():
                if value.get(key) != expected_value:
                    self.error(path, f"field {key!r} must be {expected_value!r}")

        for ore in ORES:
            path = ASSET_ROOT / f"blockstates/{ore}.json"
            value = self.load_json(path)
            expected = {"variants": {"": {"model": f"myvillage:block/{ore}"}}}
            if isinstance(value, dict) and value != expected:
                self.error(path, f"must contain the single default model myvillage:block/{ore}")

        texture_paths = (
            ASSET_ROOT / "textures/item/low_grade_spirit_stone.png",
            ASSET_ROOT / "textures/block/spirit_stone_ore.png",
            ASSET_ROOT / "textures/block/deepslate_spirit_stone_ore.png",
        )
        texture_bytes: list[bytes] = []
        for relative in texture_paths:
            path = self.root / relative
            if not path.is_file():
                self.error(relative, "missing texture")
                continue
            self.checked_files.add(path)
            try:
                width, height = _png_size(path)
            except (OSError, ValueError) as exc:
                self.error(relative, f"invalid texture: {exc}")
                continue
            if width <= 0 or height <= 0 or width > 256 or height > 256:
                self.error(relative, f"texture dimensions must be 1..256, got {width}x{height}")
            texture_bytes.append(path.read_bytes())
        if len(texture_bytes) == 3 and len(set(texture_bytes)) != 3:
            self.error(ASSET_ROOT / "textures", "item, stone ore, and deepslate ore textures must be distinct")

        keys = (
            "item.myvillage.low_grade_spirit_stone",
            "block.myvillage.spirit_stone_ore",
            "block.myvillage.deepslate_spirit_stone_ore",
        )
        for locale in ("en_us", "zh_cn"):
            path = ASSET_ROOT / f"lang/{locale}.json"
            language = self.load_json(path)
            if not isinstance(language, dict):
                continue
            for key in keys:
                if not isinstance(language.get(key), str) or not language[key].strip():
                    self.error(path, f"missing non-empty translation {key}")

    def validate_loot(self) -> None:
        for ore in ORES:
            path = DATA_ROOT / f"loot_table/blocks/{ore}.json"
            table = self.load_json(path)
            if not isinstance(table, dict):
                continue
            if table.get("type") != "minecraft:block":
                self.error(path, "loot table type must be minecraft:block")
            pools = table.get("pools")
            if not isinstance(pools, list) or len(pools) != 1 or not isinstance(pools[0], dict):
                self.error(path, "must contain exactly one loot pool")
                continue
            pool = pools[0]
            if pool.get("rolls") not in (1, 1.0) or pool.get("bonus_rolls") not in (0, 0.0):
                self.error(path, "loot pool must have one roll and zero bonus rolls")
            entries = pool.get("entries")
            if not isinstance(entries, list) or len(entries) != 1 or not isinstance(entries[0], dict):
                self.error(path, "loot pool must contain one alternatives entry")
                continue
            alternatives = entries[0]
            children = alternatives.get("children")
            if alternatives.get("type") != "minecraft:alternatives" or not isinstance(children, list) or len(children) != 2:
                self.error(path, "loot must have exactly Silk Touch and ordinary alternatives")
                continue
            silk, ordinary = children
            if not isinstance(silk, dict) or not isinstance(ordinary, dict):
                self.error(path, "loot alternatives must be objects")
                continue
            if silk.get("type") != "minecraft:item" or silk.get("name") != f"myvillage:{ore}":
                self.error(path, f"Silk Touch branch must drop myvillage:{ore}")
            conditions = silk.get("conditions")
            silk_valid = False
            if isinstance(conditions, list):
                for condition in conditions:
                    if not isinstance(condition, dict) or condition.get("condition") != "minecraft:match_tool":
                        continue
                    enchantments = condition.get("predicate", {}).get("predicates", {}).get(
                        "minecraft:enchantments"
                    )
                    if not isinstance(enchantments, list):
                        continue
                    for enchantment in enchantments:
                        if not isinstance(enchantment, dict):
                            continue
                        levels = enchantment.get("levels")
                        if (
                            enchantment.get("enchantments") == "minecraft:silk_touch"
                            and isinstance(levels, dict)
                            and levels.get("min") == 1
                        ):
                            silk_valid = True
            if not silk_valid:
                self.error(path, "Silk Touch branch is missing the level-1 match_tool predicate")

            if ordinary.get("type") != "minecraft:item" or ordinary.get("name") != "myvillage:low_grade_spirit_stone":
                self.error(path, "ordinary branch must start from one myvillage:low_grade_spirit_stone")
            if ordinary.get("conditions"):
                self.error(path, "ordinary low-grade drop must not require an extra condition")
            functions = ordinary.get("functions")
            if not isinstance(functions, list):
                self.error(path, "ordinary branch is missing Fortune and explosion functions")
                functions = []
            fortune = any(
                isinstance(function, dict)
                and function.get("function") == "minecraft:apply_bonus"
                and function.get("enchantment") == "minecraft:fortune"
                and function.get("formula") == "minecraft:ore_drops"
                for function in functions
            )
            explosion = any(
                isinstance(function, dict)
                and function.get("function") == "minecraft:explosion_decay"
                for function in functions
            )
            if not fortune:
                self.error(path, "ordinary branch is missing Fortune minecraft:ore_drops")
            if not explosion:
                self.error(path, "ordinary branch is missing minecraft:explosion_decay")
            if any(
                isinstance(function, dict)
                and function.get("function") in {"minecraft:set_count", "minecraft:furnace_smelt"}
                for function in functions
            ):
                self.error(path, "ordinary branch must remain a one-item unsmelted base drop")

            item_names: set[str] = set()

            def collect_item_names(value: Any) -> None:
                if isinstance(value, dict):
                    if value.get("type") == "minecraft:item" and isinstance(value.get("name"), str):
                        item_names.add(value["name"])
                    for nested in value.values():
                        collect_item_names(nested)
                elif isinstance(value, list):
                    for nested in value:
                        collect_item_names(nested)

            collect_item_names(table)
            expected_names = {f"myvillage:{ore}", "myvillage:low_grade_spirit_stone"}
            if item_names != expected_names:
                self.error(path, f"loot item set must be {sorted(expected_names)}, got {sorted(item_names)}")

    def validate_tool_tags(self) -> None:
        for relative in (
            Path("src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json"),
            Path("src/main/resources/data/minecraft/tags/block/needs_iron_tool.json"),
        ):
            tag = self.load_json(relative)
            values = tag.get("values") if isinstance(tag, dict) else None
            if not isinstance(values, list):
                self.error(relative, "block tag values must be a list")
                continue
            for ore_id in ORE_IDS:
                if ore_id not in values:
                    self.error(relative, f"tool tag omits {ore_id}")

    def validate_configured_features(self) -> None:
        expected_targets = {
            (
                "minecraft:stone_ore_replaceables",
                "myvillage:spirit_stone_ore",
            ),
            (
                "minecraft:deepslate_ore_replaceables",
                "myvillage:deepslate_spirit_stone_ore",
            ),
        }
        for feature, expected_size in CONFIGURED_FEATURES.items():
            path = DATA_ROOT / f"worldgen/configured_feature/{feature}.json"
            value = self.load_json(path)
            if not isinstance(value, dict):
                continue
            if value.get("type") != "minecraft:ore":
                self.error(path, "configured feature type must be minecraft:ore")
            config = value.get("config")
            if not isinstance(config, dict):
                self.error(path, "missing ore config")
                continue
            if config.get("size") != expected_size:
                self.error(path, f"config.size must be {expected_size}")
            targets = config.get("targets")
            actual_targets: set[tuple[str, str]] = set()
            if isinstance(targets, list):
                for target in targets:
                    if not isinstance(target, dict):
                        continue
                    predicate = target.get("target")
                    state = target.get("state")
                    if not isinstance(predicate, dict) or not isinstance(state, dict):
                        continue
                    if predicate.get("predicate_type") != "minecraft:tag_match":
                        self.error(path, "every ore target must use minecraft:tag_match")
                    tag = predicate.get("tag")
                    block = state.get("Name")
                    if isinstance(tag, str) and isinstance(block, str):
                        actual_targets.add((tag, block))
            if actual_targets != expected_targets or not isinstance(targets, list) or len(targets) != 2:
                self.error(
                    path,
                    f"configured target mapping must be {sorted(expected_targets)}, got {sorted(actual_targets)}",
                )

    def validate_placed_features(self) -> None:
        for feature, expected in PLACED_FEATURES.items():
            path = DATA_ROOT / f"worldgen/placed_feature/{feature}.json"
            value = self.load_json(path)
            if not isinstance(value, dict):
                continue
            if value.get("feature") != expected["feature"]:
                self.error(path, f"feature must reference {expected['feature']}")
            placement = value.get("placement")
            if not isinstance(placement, list):
                self.error(path, "placement must be a list")
                continue
            by_type: dict[str, list[dict[str, Any]]] = {}
            for modifier in placement:
                if isinstance(modifier, dict) and isinstance(modifier.get("type"), str):
                    by_type.setdefault(modifier["type"], []).append(modifier)
            expected_types = {
                "minecraft:count",
                "minecraft:in_square",
                "minecraft:height_range",
                "minecraft:biome",
            }
            if set(by_type) != expected_types or any(len(values) != 1 for values in by_type.values()):
                self.error(path, f"placement modifiers must be exactly {sorted(expected_types)}")
                continue
            count = by_type["minecraft:count"][0].get("count")
            if count != expected["count"]:
                self.error(path, f"count must be {expected['count']}, got {count!r}")
            height = by_type["minecraft:height_range"][0].get("height")
            if height != expected["height"]:
                self.error(path, f"height provider must be {expected['height']!r}, got {height!r}")

    def validate_biome_modifier(self) -> None:
        tag_path = DATA_ROOT / "tags/worldgen/biome/has_spirit_stone_ore.json"
        tag = self.load_json(tag_path)
        if isinstance(tag, dict) and (
            tag.get("replace") is not False or tag.get("values") != ["#minecraft:is_overworld"]
        ):
            self.error(tag_path, "biome tag must contain only #minecraft:is_overworld with replace=false")

        modifier_path = DATA_ROOT / "neoforge/biome_modifier/add_spirit_stone_ores.json"
        modifier = self.load_json(modifier_path)
        if not isinstance(modifier, dict):
            return
        if modifier.get("type") != "neoforge:add_features":
            self.error(modifier_path, "type must be neoforge:add_features")
        if modifier.get("biomes") != "#myvillage:has_spirit_stone_ore":
            self.error(modifier_path, "biomes must use #myvillage:has_spirit_stone_ore")
        expected_features = {f"myvillage:{feature}" for feature in PLACED_FEATURES}
        features = modifier.get("features")
        if not isinstance(features, list) or set(features) != expected_features or len(features) != 3:
            self.error(modifier_path, f"features must be {sorted(expected_features)}")
        if modifier.get("step") != "underground_ores":
            self.error(modifier_path, "generation step must be underground_ores")

    def validate_no_processing_chain(self) -> None:
        for directory_name in ("recipe", "recipes"):
            directory = self.root / DATA_ROOT / directory_name
            if not directory.is_dir():
                continue
            for path in sorted(directory.rglob("*.json")):
                try:
                    text = path.read_text(encoding="utf-8")
                except (OSError, UnicodeError) as exc:
                    self.error(path, f"cannot inspect recipe: {exc}")
                    continue
                lowered = text.lower()
                if "spirit_stone" in path.name.lower() or any(
                    identifier in lowered for identifier in (f"myvillage:{STONE_ITEM}", *ORE_IDS)
                ):
                    self.error(path, "spirit-stone recipe or smelting chain is forbidden in this slice")

        for forbidden in FORBIDDEN_ITEM_IDS:
            for base in (self.root / ASSET_ROOT, self.root / DATA_ROOT):
                if not base.is_dir():
                    continue
                for path in sorted(base.rglob("*")):
                    if not path.is_file():
                        continue
                    if forbidden in path.name:
                        self.error(path, f"forbidden intermediate or higher-grade resource {forbidden}")
                    if path.suffix == ".json":
                        try:
                            text = path.read_text(encoding="utf-8")
                        except (OSError, UnicodeError):
                            continue
                        if forbidden in text:
                            self.error(path, f"references forbidden intermediate or higher-grade id {forbidden}")

    def _expected_jar(self) -> tuple[Path | None, str | None]:
        properties_path = self.root / "gradle.properties"
        if not properties_path.is_file():
            return None, "missing gradle.properties"
        try:
            properties = properties_path.read_text(encoding="utf-8")
        except (OSError, UnicodeError) as exc:
            return None, f"cannot read gradle.properties: {exc}"
        match = re.search(r"(?m)^mod_version\s*=\s*([^\s#]+)\s*$", properties)
        if match is None:
            return None, "gradle.properties has no mod_version"
        return self.root / "build/libs" / f"myvillage-{match.group(1)}.jar", None

    def validate_jar(
        self,
        explicit_jar: Path | None,
        require_current_jar: bool,
    ) -> str:
        expected_jar, resolution_error = self._expected_jar()
        if explicit_jar is not None:
            jar = explicit_jar if explicit_jar.is_absolute() else self.root / explicit_jar
            explicit = True
        else:
            jar = expected_jar
            explicit = False
        if jar is None:
            status = f"skipped_unresolved ({resolution_error})"
            if require_current_jar:
                self.error("jar", status)
            return status
        if not jar.is_file():
            status = f"skipped_missing ({self.label(jar)})"
            if explicit or require_current_jar:
                self.error(jar, "requested practical jar is missing")
            return status

        required_source_paths = [
            self.root / JAVA_ROOT / "item/ModItems.java",
            self.root / JAVA_ROOT / "block/ModBlocks.java",
            *(self.root / RESOURCE_ROOT / entry for entry in REQUIRED_RESOURCE_FILES),
        ]
        existing_inputs = [path for path in required_source_paths if path.is_file()]
        if existing_inputs:
            newest_input = max(path.stat().st_mtime for path in existing_inputs)
            if jar.stat().st_mtime < newest_input and not explicit:
                status = f"skipped_stale ({self.label(jar)} predates source/resources)"
                if require_current_jar:
                    self.error(jar, "practical jar is stale; rebuild before required jar inspection")
                return status

        try:
            with zipfile.ZipFile(jar) as archive:
                names = set(archive.namelist())
                for class_entry in (
                    "com/example/myvillage/item/ModItems.class",
                    "com/example/myvillage/block/ModBlocks.class",
                ):
                    if class_entry not in names:
                        self.error(jar, f"missing class entry {class_entry}")
                for entry in REQUIRED_RESOURCE_FILES:
                    if entry not in names:
                        self.error(jar, f"missing resource entry {entry}")
                        continue
                    source = self.root / RESOURCE_ROOT / entry
                    if source.is_file() and archive.read(entry) != source.read_bytes():
                        self.error(jar, f"resource entry differs from source tree: {entry}")
        except (OSError, zipfile.BadZipFile) as exc:
            self.error(jar, f"cannot inspect practical jar: {exc}")
            return f"failed ({self.label(jar)})"
        self.checked_files.add(jar)
        return f"checked ({self.label(jar)})"

    def validate(
        self,
        *,
        jar: Path | None = None,
        require_current_jar: bool = False,
    ) -> ValidationResult:
        self.validate_registration()
        self.validate_assets()
        self.validate_loot()
        self.validate_tool_tags()
        self.validate_configured_features()
        self.validate_placed_features()
        self.validate_biome_modifier()
        self.validate_no_processing_chain()
        jar_status = self.validate_jar(jar, require_current_jar)
        return ValidationResult(tuple(self.errors), len(self.checked_files), jar_status)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument(
        "--jar",
        type=Path,
        help="inspect this jar explicitly; a missing explicit jar is an error",
    )
    parser.add_argument(
        "--require-current-jar",
        action="store_true",
        help="fail when the current-version jar is missing or older than required inputs",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    result = SpiritStoneResourcesValidator(args.root.resolve()).validate(
        jar=args.jar,
        require_current_jar=args.require_current_jar,
    )
    if result.errors:
        print(
            f"spirit-stone resource validation failed ({len(result.errors)} error(s)):",
            file=sys.stderr,
        )
        for error in result.errors:
            print(f"- {error}", file=sys.stderr)
        print(f"jar_check={result.jar_status}", file=sys.stderr)
        return 1
    print(
        "spirit-stone resource validation passed: "
        f"checked_files={result.checked_files}; items=3; ores=2; worldgen_layers=3"
    )
    print(f"jar_check={result.jar_status}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
