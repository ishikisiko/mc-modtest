#!/usr/bin/env python3
"""Validate cultivation-initiation wiring, resources, and authority boundaries.

Numeric determinism and profile-transition semantics belong to the Java test
suite.  This validator deliberately covers the complementary evidence that is
well suited to deterministic source and resource inspection.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = Path("src/main/java/com/example/myvillage")
RESOURCE_ROOT = Path("src/main/resources")
ASSET_ROOT = RESOURCE_ROOT / "assets/myvillage"
DATA_ROOT = RESOURCE_ROOT / "data/myvillage"
CULTIVATION_DATA_ROOT = DATA_ROOT / "myvillage"
LANG_ROOT = ASSET_ROOT / "lang"

STELES = {
    "spirit_testing_stele": (
        "SPIRIT_TESTING_STELE",
        "SPIRIT_TESTING_STELE_ITEM",
        "SpiritTestingSteleBlock",
        "SpiritualRootAwakeningService",
    ),
    "technique_inheritance_stele": (
        "TECHNIQUE_INHERITANCE_STELE",
        "TECHNIQUE_INHERITANCE_STELE_ITEM",
        "TechniqueInheritanceSteleBlock",
        "TechniqueInheritanceService",
    ),
}
ELEMENTS = ("metal", "wood", "water", "fire", "earth")
EXPECTED_PROFILE_FIELDS = (
    "schemaVersion",
    "realmId",
    "stageId",
    "cultivationProgress",
    "stability",
    "currentSpiritualPower",
    "spiritualAffinity",
    "lifespanConsumedTicks",
    "meditationQiReserve",
    "spiritualRoot",
    "learnedTechniques",
)
ALLOWED_CULTIVATION_PAYLOADS = frozenset(
    {
        "CultivationSnapshotPayload.java",
        "CultivationTimeSnapshotPayload.java",
        "MeditationStatusPayload.java",
        "MeditationIntentPayload.java",
    }
)
CLIENTBOUND_CULTIVATION_PAYLOADS = (
    "CultivationSnapshotPayload",
    "CultivationTimeSnapshotPayload",
    "MeditationStatusPayload",
)
MAX_AWAKENING_WEIGHT = 1_000_000


class DuplicateJsonKey(ValueError):
    """Raised when JSON contains a key hidden by the normal decoder."""


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateJsonKey(f"duplicate JSON key {key!r}")
        result[key] = value
    return result


def _strip_java_comments(source: str) -> str:
    """Remove Java comments while preserving strings, chars, and line numbers."""

    output: list[str] = []
    index = 0
    state = "code"
    while index < len(source):
        char = source[index]
        next_char = source[index + 1] if index + 1 < len(source) else ""
        if state == "code":
            if char == '"':
                state = "string"
                output.append(char)
            elif char == "'":
                state = "char"
                output.append(char)
            elif char == "/" and next_char == "/":
                state = "line_comment"
                output.extend("  ")
                index += 1
            elif char == "/" and next_char == "*":
                state = "block_comment"
                output.extend("  ")
                index += 1
            else:
                output.append(char)
        elif state == "string":
            output.append(char)
            if char == "\\" and next_char:
                output.append(next_char)
                index += 1
            elif char == '"':
                state = "code"
        elif state == "char":
            output.append(char)
            if char == "\\" and next_char:
                output.append(next_char)
                index += 1
            elif char == "'":
                state = "code"
        elif state == "line_comment":
            if char == "\n":
                output.append("\n")
                state = "code"
            else:
                output.append(" ")
        else:
            if char == "*" and next_char == "/":
                output.extend("  ")
                index += 1
                state = "code"
            elif char == "\n":
                output.append("\n")
            else:
                output.append(" ")
        index += 1
    return "".join(output)


def _balanced_content(
    text: str, open_index: int, opening: str = "{", closing: str = "}"
) -> tuple[str, int] | None:
    if open_index < 0 or open_index >= len(text) or text[open_index] != opening:
        return None
    depth = 0
    state = "code"
    index = open_index
    while index < len(text):
        char = text[index]
        next_char = text[index + 1] if index + 1 < len(text) else ""
        if state == "code":
            if char == '"':
                state = "string"
            elif char == "'":
                state = "char"
            elif char == opening:
                depth += 1
            elif char == closing:
                depth -= 1
                if depth == 0:
                    return text[open_index + 1 : index], index
        elif state == "string":
            if char == "\\" and next_char:
                index += 1
            elif char == '"':
                state = "code"
        else:
            if char == "\\" and next_char:
                index += 1
            elif char == "'":
                state = "code"
        index += 1
    return None


def _method_body(source: str, method_name: str) -> str | None:
    for match in re.finditer(rf"\b{re.escape(method_name)}\s*\(", source):
        open_paren = source.find("(", match.start())
        parameters = _balanced_content(source, open_paren, "(", ")")
        if parameters is None:
            continue
        tail = source[parameters[1] + 1 :]
        brace_offset = tail.find("{")
        semicolon_offset = tail.find(";")
        if brace_offset < 0 or (0 <= semicolon_offset < brace_offset):
            continue
        brace = parameters[1] + 1 + brace_offset
        body = _balanced_content(source, brace)
        if body is not None:
            return body[0]
    return None


def _top_level_parts(text: str) -> list[str]:
    parts: list[str] = []
    start = 0
    depths = {"<": 0, "(": 0, "[": 0, "{": 0}
    pairs = {">": "<", ")": "(", "]": "[", "}": "{"}
    for index, char in enumerate(text):
        if char in depths:
            depths[char] += 1
        elif char in pairs:
            depths[pairs[char]] = max(0, depths[pairs[char]] - 1)
        elif char == "," and not any(depths.values()):
            parts.append(text[start:index].strip())
            start = index + 1
    parts.append(text[start:].strip())
    return [part for part in parts if part]


def _json_model_references(value: Any) -> Iterable[str]:
    if isinstance(value, dict):
        for key, nested in value.items():
            if key == "model" and isinstance(nested, str):
                yield nested
            yield from _json_model_references(nested)
    elif isinstance(value, list):
        for nested in value:
            yield from _json_model_references(nested)


def _json_contains_item_entry(value: Any, identifier: str) -> bool:
    if isinstance(value, dict):
        if value.get("type") == "minecraft:item" and value.get("name") == identifier:
            return True
        return any(_json_contains_item_entry(nested, identifier) for nested in value.values())
    if isinstance(value, list):
        return any(_json_contains_item_entry(nested, identifier) for nested in value)
    return False


@dataclass(frozen=True)
class ValidationResult:
    errors: tuple[str, ...]
    checked_files: int


class CultivationInitiationValidator:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.errors: list[str] = []
        self.checked_files: set[Path] = set()
        self._java_cache: dict[Path, str] = {}
        self._json_cache: dict[Path, Any] = {}

    def label(self, path: Path) -> str:
        try:
            return path.relative_to(self.root).as_posix()
        except ValueError:
            return str(path)

    def error(self, location: str | Path, message: str) -> None:
        label = self.label(location) if isinstance(location, Path) else location
        self.errors.append(f"{label}: {message}")

    def read_text(self, relative_path: Path, purpose: str = "required file") -> str | None:
        path = self.root / relative_path
        if not path.is_file():
            self.error(relative_path, f"missing {purpose}")
            return None
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeError) as exc:
            self.error(relative_path, f"cannot read UTF-8 file: {exc}")
            return None
        self.checked_files.add(path)
        return text

    def load_json(self, relative_path: Path, purpose: str = "JSON resource") -> Any | None:
        path = self.root / relative_path
        if path in self._json_cache:
            return self._json_cache[path]
        text = self.read_text(relative_path, purpose)
        if text is None:
            return None
        try:
            value = json.loads(text, object_pairs_hook=_reject_duplicate_keys)
        except (json.JSONDecodeError, DuplicateJsonKey) as exc:
            self.error(relative_path, f"invalid JSON: {exc}")
            return None
        self._json_cache[path] = value
        return value

    def java_class(self, class_name: str) -> tuple[Path, str] | None:
        matches = sorted((self.root / JAVA_ROOT).rglob(f"{class_name}.java"))
        if not matches:
            self.error(JAVA_ROOT, f"missing Java class {class_name}")
            return None
        if len(matches) > 1:
            self.error(
                JAVA_ROOT,
                f"Java class {class_name} is ambiguous: "
                + ", ".join(self.label(path) for path in matches),
            )
            return None
        path = matches[0]
        if path not in self._java_cache:
            try:
                self._java_cache[path] = _strip_java_comments(path.read_text(encoding="utf-8"))
                self.checked_files.add(path)
            except (OSError, UnicodeError) as exc:
                self.error(path, f"cannot read Java source: {exc}")
                return None
        return path, self._java_cache[path]

    def validate_artifacts(self) -> None:
        spec_candidates = sorted(
            path
            for path in (self.root / "openspec").rglob("spec.md")
            if path.parent.name == "cultivation-initiation-ritual"
        )
        if not spec_candidates:
            self.error(
                "openspec",
                "missing cultivation-initiation-ritual capability specification",
            )
        else:
            for path in spec_candidates:
                self.checked_files.add(path)
            if not any(
                re.search(
                    r"(?m)^[ \t]*##\s+(?:ADDED\s+)?Requirements\s*$",
                    path.read_text(encoding="utf-8"),
                )
                and re.search(r"\bRequirement:\s*\S", path.read_text(encoding="utf-8"))
                for path in spec_candidates
            ):
                self.error(
                    self.label(spec_candidates[0]),
                    "capability spec must contain normative requirements",
                )

        kb_path = Path("docs/ai-kb/29_cultivation_initiation_ritual.md")
        kb = self.read_text(kb_path, "cultivation-initiation AI KB note")
        index = self.read_text(Path("docs/ai-kb/INDEX.md"), "AI KB index")
        if kb is not None:
            for identifier in STELES:
                if identifier not in kb:
                    self.error(kb_path, f"missing shipped stele id {identifier}")
            if "cultivation-initiation-ritual" not in kb:
                self.error(kb_path, "missing capability cross-link")
        if index is not None and "29_cultivation_initiation_ritual.md" not in index:
            self.error(Path("docs/ai-kb/INDEX.md"), "initiation KB note is not indexed")

    def validate_elements_and_technique(self) -> None:
        for element in ELEMENTS:
            path = CULTIVATION_DATA_ROOT / "spiritual_element" / f"{element}.json"
            value = self.load_json(path, f"shipped {element} spiritual-element definition")
            if not isinstance(value, dict):
                if value is not None:
                    self.error(path, "spiritual-element definition must be a JSON object")
                continue
            weight = value.get("awakening_weight")
            if type(weight) is not int or not 0 <= weight <= MAX_AWAKENING_WEIGHT:
                self.error(path, "awakening_weight must be an integer in 0..1000000")
            elif weight != 1:
                self.error(path, "the five shipped elements must explicitly use awakening_weight 1")

        definition = self.java_class("SpiritualElementDefinition")
        if definition is not None:
            path, source = definition
            compact = re.sub(r"\s+", " ", source)
            default_match = re.search(
                r"optionalFieldOf\s*\(\s*\"awakening_weight\"\s*,\s*"
                r"([A-Za-z_$][\w$]*|1)\s*\)",
                compact,
            )
            default_is_one = default_match is not None and (
                default_match.group(1) == "1"
                or re.search(
                    rf"\b{re.escape(default_match.group(1))}\s*=\s*1\s*;",
                    source,
                )
                is not None
            )
            if not default_is_one:
                self.error(path, "awakening_weight codec must be optional with default 1")
            if not re.search(r"\bint\s+awakeningWeight\b", source):
                self.error(path, "definition must expose integer awakeningWeight")
            if "1_000_000" not in source and "1000000" not in source:
                self.error(path, "definition must enforce the awakening_weight upper bound")

        technique_path = CULTIVATION_DATA_ROOT / "technique/basic_breathing.json"
        technique = self.load_json(technique_path, "basic_breathing definition")
        if isinstance(technique, dict):
            requirements = technique.get("requirements")
            if not isinstance(requirements, dict):
                self.error(technique_path, "requirements must be a JSON object")
            else:
                if requirements.get("minimum_realm") != "myvillage:mortal":
                    self.error(
                        technique_path,
                        "minimum_realm must be myvillage:mortal",
                    )
                if requirements.get("minimum_stage") != "myvillage:mortal_qi_sensed":
                    self.error(
                        technique_path,
                        "minimum_stage must be myvillage:mortal_qi_sensed",
                    )
                affinity = requirements.get("minimum_element_affinity")
                if affinity not in (None, {}):
                    self.error(
                        technique_path,
                        "basic_breathing must not require element affinity",
                    )
            if technique.get("elements") != []:
                self.error(technique_path, "basic_breathing must remain element-neutral")
            forbidden = sorted(
                key for key in technique if "executor" in key.lower() or key in {"effect", "action"}
            )
            if forbidden:
                self.error(
                    technique_path,
                    "basic_breathing must remain metadata-only; forbidden fields: "
                    + ", ".join(forbidden),
                )

    def validate_block_registration(self) -> dict[str, tuple[Path, str]]:
        block_registry = self.java_class("ModBlocks")
        item_registry = self.java_class("ModItems")
        blocks: dict[str, tuple[Path, str]] = {}
        if block_registry is None or item_registry is None:
            return blocks
        block_path, block_source = block_registry
        item_path, item_source = item_registry
        compact_blocks = re.sub(r"\s+", " ", block_source)
        compact_items = re.sub(r"\s+", " ", item_source)
        verify_body = _method_body(block_source, "verifyRegistered") or ""
        if "BLOCK_IDS" not in verify_body:
            self.error(block_path, "verifyRegistered must verify the BLOCK_IDS registry contract")
        block_ids_match = re.search(
            r"\bBLOCK_IDS\s*=\s*List\.of\s*\((.*?)\)\s*;",
            compact_blocks,
        )
        block_ids = (
            set(re.findall(r'"([a-z0-9_./-]+)"', block_ids_match.group(1)))
            if block_ids_match is not None
            else set()
        )
        if block_ids_match is None:
            self.error(block_path, "BLOCK_IDS must be a statically inspectable List.of contract")

        for identifier, (block_field, item_field, class_name, service_name) in STELES.items():
            block_pattern = (
                rf"\b{block_field}\b\s*=\s*BLOCKS\.registerBlock\s*\(\s*"
                rf'"{identifier}"\s*,\s*{class_name}::new\s*,'
            )
            if not re.search(block_pattern, compact_blocks):
                self.error(
                    block_path,
                    f"{block_field} must register {identifier} with {class_name}::new",
                )
            if identifier not in block_ids:
                self.error(block_path, f"BLOCK_IDS must contain {identifier}")

            item_pattern = (
                rf"\b{item_field}\b\s*=\s*ITEMS\.registerItem\s*\(\s*"
                rf'"{identifier}"\s*,\s*\w+\s*->\s*new\s+BlockItem\s*\(\s*'
                rf"ModBlocks\.{block_field}\.get\s*\(\s*\)"
            )
            if not re.search(item_pattern, compact_items):
                self.error(
                    item_path,
                    f"{item_field} must be the BlockItem for ModBlocks.{block_field}",
                )
            if not re.search(
                rf"output\.accept\s*\(\s*{item_field}\.get\s*\(\s*\)\s*\)",
                compact_items,
            ):
                self.error(item_path, f"creative tab must expose {item_field}")

            block_class = self.java_class(class_name)
            if block_class is None:
                continue
            class_path, class_source = block_class
            blocks[identifier] = block_class
            if re.search(
                r"\b(?:extends\s+(?:BaseEntityBlock|EntityBlock)|implements\s+EntityBlock|"
                r"BlockEntity|MenuProvider|newBlockEntity)\b",
                class_source,
            ):
                self.error(class_path, f"{class_name} must not use a BlockEntity or menu")
            if service_name not in class_source or not re.search(
                rf"\b{service_name}\s*\.\s*\w+\s*\(", class_source
            ):
                self.error(class_path, f"{class_name} must call {service_name}")
            other_service = next(
                values[3] for key, values in STELES.items() if key != identifier
            )
            if re.search(rf"\b{other_service}\s*\.\s*\w+\s*\(", class_source):
                self.error(class_path, f"{class_name} must not call {other_service}")
            if not re.search(r"\buseWithoutItem\s*\(", class_source):
                self.error(
                    class_path,
                    "1.21.1 interaction must override useWithoutItem so dispatch is main-hand-only",
                )
            if re.search(r"\b(?:useItemOn|use)\s*\(", class_source):
                self.error(
                    class_path,
                    "stele must not use legacy/useItemOn interaction hooks",
                )
            if not re.search(r"\bisClientSide(?:\s*\(\s*\))?\b", class_source):
                self.error(class_path, "interaction must branch on the logical side")
            if re.search(
                r"\b(?:setData|CultivationService\."
                r"(?:replaceProfile|updateProfile|set\w+|learnTechnique))\s*\(",
                class_source,
            ):
                self.error(class_path, "block must delegate profile mutation exclusively to its ritual service")
        return blocks

    def validate_block_resources(self, blocks: dict[str, tuple[Path, str]]) -> None:
        languages: dict[str, dict[str, Any]] = {}
        for locale in ("en_us", "zh_cn"):
            path = LANG_ROOT / f"{locale}.json"
            value = self.load_json(path, f"{locale} language file")
            if isinstance(value, dict):
                languages[locale] = value
            elif value is not None:
                self.error(path, "language file must be a JSON object")

        mineable_values: set[str] = set()
        mineable_root = self.root / RESOURCE_ROOT / "data/minecraft/tags/block/mineable"
        if mineable_root.is_dir():
            for path in sorted(mineable_root.rglob("*.json")):
                relative = path.relative_to(self.root)
                value = self.load_json(relative, "mineable tool tag")
                if not isinstance(value, dict) or not isinstance(value.get("values"), list):
                    self.error(relative, "mineable tag must contain a values list")
                    continue
                mineable_values.update(item for item in value["values"] if isinstance(item, str))
        else:
            self.error(
                RESOURCE_ROOT / "data/minecraft/tags/block/mineable",
                "missing mineable tool-tag directory",
            )

        for identifier in STELES:
            resource_id = f"myvillage:{identifier}"
            blockstate_path = ASSET_ROOT / "blockstates" / f"{identifier}.json"
            block_model_path = ASSET_ROOT / "models/block" / f"{identifier}.json"
            item_model_path = ASSET_ROOT / "models/item" / f"{identifier}.json"
            loot_path = DATA_ROOT / "loot_table/blocks" / f"{identifier}.json"
            blockstate = self.load_json(blockstate_path, "stele blockstate")
            block_model = self.load_json(block_model_path, "stele block model")
            item_model = self.load_json(item_model_path, "stele item model")
            loot = self.load_json(loot_path, "stele loot table")

            if isinstance(blockstate, dict):
                references = set(_json_model_references(blockstate))
                expected = f"myvillage:block/{identifier}"
                if expected not in references:
                    self.error(blockstate_path, f"blockstate must reference {expected}")
            elif blockstate is not None:
                self.error(blockstate_path, "blockstate must be a JSON object")

            if isinstance(block_model, dict):
                parent = block_model.get("parent")
                if not isinstance(parent, str) or ":" not in parent:
                    self.error(block_model_path, "block model must have a namespaced parent")
                textures = block_model.get("textures")
                if not isinstance(textures, dict) or not textures:
                    self.error(block_model_path, "block model must declare textures")
                else:
                    for texture in textures.values():
                        if not isinstance(texture, str) or ":" not in texture:
                            self.error(block_model_path, "texture references must be namespaced strings")
                        elif texture.startswith("myvillage:"):
                            texture_path = ASSET_ROOT / "textures" / (texture.split(":", 1)[1] + ".png")
                            if not (self.root / texture_path).is_file():
                                self.error(texture_path, "missing referenced mod texture")
                            else:
                                self.checked_files.add(self.root / texture_path)
            elif block_model is not None:
                self.error(block_model_path, "block model must be a JSON object")

            if not isinstance(item_model, dict) or item_model.get("parent") != f"myvillage:block/{identifier}":
                self.error(item_model_path, f"item model must parent myvillage:block/{identifier}")
            if not isinstance(loot, dict) or loot.get("type") != "minecraft:block":
                self.error(loot_path, "loot table must use type minecraft:block")
            elif not _json_contains_item_entry(loot, resource_id):
                self.error(loot_path, f"loot table must drop {resource_id}")
            if resource_id not in mineable_values:
                self.error("mineable tool tags", f"missing {resource_id}")

            name_key = f"block.myvillage.{identifier}"
            for locale, language in languages.items():
                if not isinstance(language.get(name_key), str) or not language[name_key].strip():
                    self.error(LANG_ROOT / f"{locale}.json", f"missing non-empty {name_key}")

        translation_sources: list[str] = []
        for _identifier, block_class in blocks.items():
            translation_sources.append(block_class[1])
        for class_name in ("SpiritualRootAwakeningService", "TechniqueInheritanceService"):
            service = self.java_class(class_name)
            if service is not None:
                translation_sources.append(service[1])
        declared_keys = set(
            re.findall(
                r'"((?:message|command|block)\.myvillage\.[a-z0-9_.-]+)"',
                "\n".join(translation_sources),
            )
        )
        for key in sorted(declared_keys):
            for locale, language in languages.items():
                if not isinstance(language.get(key), str) or not language[key].strip():
                    self.error(LANG_ROOT / f"{locale}.json", f"missing declared translation {key}")

        all_keys = set.intersection(*(set(language) for language in languages.values())) if languages else set()
        if not any("awaken" in key or "spirit_testing" in key for key in all_keys):
            self.error(LANG_ROOT, "missing bilingual awakening feedback translations")
        if not any("inherit" in key or "initiat" in key for key in all_keys):
            self.error(LANG_ROOT, "missing bilingual inheritance feedback translations")

    def validate_generator(self) -> None:
        generator = self.java_class("SpiritualRootGenerator")
        if generator is None:
            return
        path, source = generator
        if not re.search(
            r"\bstatic\s+final\s+long\s+\w*SALT\w*\s*=\s*"
            r"(?:0[xX][0-9a-fA-F_]+|[0-9][0-9_]*)[lL]?\s*;",
            source,
            re.IGNORECASE,
        ):
            self.error(path, "generator must declare a fixed numeric long salt")
        literal_values = set(re.findall(r'"([a-z0-9_:/.-]+)"', source))
        hardcoded = sorted(
            element
            for element in ELEMENTS
            if element in literal_values or f"myvillage:{element}" in literal_values
        )
        if hardcoded:
            self.error(path, "generator hard-codes shipped element ids: " + ", ".join(hardcoded))

        forbidden_patterns = {
            "wall-clock time": r"System\.(?:currentTimeMillis|nanoTime)\s*\(",
            "player position": r"\b(?:BlockPos|getBlockPos|blockPosition|position)\b",
            "runtime dimension": r"\b(?:ServerLevel|ServerPlayer|dimension|level\s*\(\s*\))\b",
            "unseeded Random": r"\bnew\s+(?:java\.util\.)?Random\s*\(\s*\)",
            "unseeded RandomSource": r"\bRandomSource\.create\s*\(\s*\)",
            "thread-local randomness": r"\b(?:ThreadLocalRandom|Math\.random)\b",
            "Minecraft/client dependency": r"\b(?:Minecraft|LocalPlayer)\b",
        }
        for description, pattern in forbidden_patterns.items():
            if re.search(pattern, source):
                self.error(path, f"generator must not depend on {description}")
        if "UUID" not in source or not re.search(r"\bgenerate\s*\(", source):
            self.error(path, "generator API must take deterministic UUID-based input")
        has_canonical_sort = (
            (".sort(" in source or ".sorted(" in source)
            and "Comparator" in source
            and re.search(r"(?:\.toString\s*\(\s*\)|ResourceLocation::toString)", source)
        )
        if not has_canonical_sort:
            self.error(path, "generator must stably sort candidate ids by full id string")
        if not re.search(
            r"(?:Math\.(?:add|multiply)Exact|Long\.MAX_VALUE|\baddExact\s*\()",
            source,
        ):
            self.error(path, "generator must guard long awakening-weight accumulation")

    def validate_services_and_commands(self) -> None:
        awakening = self.java_class("SpiritualRootAwakeningService")
        inheritance = self.java_class("TechniqueInheritanceService")
        evaluator = self.java_class("TechniqueRequirementEvaluator")
        for service, expected_dependency in (
            (awakening, "SpiritualRootGenerator"),
            (inheritance, "TechniqueRequirementEvaluator"),
        ):
            if service is None:
                continue
            path, source = service
            if expected_dependency not in source:
                self.error(path, f"service must delegate to {expected_dependency}")
            if "CultivationService.getProfile" not in source:
                self.error(path, "service must read the profile through CultivationService")
            if not re.search(r"CultivationService\.(?:replaceProfile|updateProfile)\s*\(", source):
                self.error(path, "service must atomically mutate through CultivationService")
            if re.search(r"\b(?:player\s*\.\s*)?setData\s*\(", source):
                self.error(path, "service must not write player attachment data directly")

        if awakening is not None:
            path, source = awakening
            if "overworld()" not in source or "getSeed()" not in source:
                self.error(path, "awakening must derive the seed from the server Overworld")
            if re.search(r"(?:serverLevel|level)\s*\(\s*\)\s*\.\s*getSeed\s*\(", source):
                self.error(path, "awakening must not use the triggering dimension seed")
            if re.search(r"CultivationService\.(?:setSpiritualRoot|setRealmAndStage)\s*\(", source):
                self.error(path, "awakening root and stage must not be separate mutations")
        if inheritance is not None:
            path, source = inheritance
            if re.search(r"CultivationService\.learnTechnique\s*\(", source):
                self.error(path, "normal inheritance must not use the administrator learn bypass")
            if "MORTAL_QI_SENSED_STAGE_ID" in source:
                self.error(path, "inheritance eligibility must come from the technique definition")
        if evaluator is not None:
            path, source = evaluator
            for token in ("minimumRealm", "minimumStage", "minimumElementAffinity"):
                if token not in source:
                    self.error(path, f"requirements evaluator does not inspect {token}")

        commands = self.java_class("CultivationCommands")
        if commands is None:
            return
        path, source = commands
        command_tree = _method_body(source, "commandTree") or ""
        for literal in ("awaken", "juexing", "initiate", "rumen"):
            if not re.search(rf'\(\s*"{literal}"\s*\)', command_tree):
                self.error(path, f"commandTree must expose {literal} under both command roots")
        if not re.search(r"SpiritualRootAwakeningService\.\w+\s*\(", source):
            self.error(path, "awakening command handler must call SpiritualRootAwakeningService")
        if not re.search(r"TechniqueInheritanceService\.\w+\s*\(", source):
            self.error(path, "initiation command handler must call TechniqueInheritanceService")
        if re.search(r"\b(?:player\s*\.\s*)?setData\s*\(", source):
            self.error(path, "commands must not write player attachment data directly")

        method_names = re.findall(
            r"\b(?:private|public)\s+static\s+[\w<>?,. ]+\s+(\w+)\s*\(", source
        )
        awakening_bodies = [
            _method_body(source, name) or ""
            for name in method_names
            if re.search(r"awaken|juexing", name, re.IGNORECASE)
        ]
        initiation_bodies = [
            _method_body(source, name) or ""
            for name in method_names
            if re.search(r"initiat|inherit|rumen", name, re.IGNORECASE)
        ]
        for description, bodies, forbidden in (
            ("awakening", awakening_bodies, ("seed", "element", "affinity", "root_count")),
            ("initiation", initiation_bodies, ("technique_id", "mastery")),
        ):
            combined = "\n".join(bodies)
            if not bodies:
                self.error(path, f"missing structurally identifiable {description} command methods")
                continue
            if not re.search(r'Commands\.argument\s*\(\s*"target"\s*,\s*EntityArgument\.player\s*\(\s*\)', combined):
                self.error(path, f"{description} command must provide a standard optional target")
            for argument in forbidden:
                if re.search(rf'Commands\.argument\s*\(\s*"{argument}"', combined):
                    self.error(path, f"{description} command must not accept {argument}")

    def validate_profile_network_and_ui(self) -> None:
        profile = self.java_class("CultivationProfile")
        if profile is not None:
            path, source = profile
            if not re.search(r"CURRENT_SCHEMA_VERSION\s*=\s*3\s*;", source):
                self.error(path, "cultivation profile schema must be 3")
            if not re.search(r"DEFAULT_SPIRITUAL_AFFINITY\s*=\s*10\s*;", source):
                self.error(path, "default spiritual affinity must be 10")
            if not re.search(r"spiritualAffinity\s*<\s*0", source):
                self.error(path, "spiritual affinity must reject negative values")
            record_match = re.search(r"\brecord\s+CultivationProfile\s*\(", source)
            components: tuple[str, ...] = ()
            if record_match is not None:
                open_paren = source.find("(", record_match.start())
                region = _balanced_content(source, open_paren, "(", ")")
                if region is not None:
                    names: list[str] = []
                    for part in _top_level_parts(region[0]):
                        name_match = re.search(r"([A-Za-z_$][\w$]*)\s*$", part)
                        if name_match:
                            names.append(name_match.group(1))
                    components = tuple(names)
            if components != EXPECTED_PROFILE_FIELDS:
                self.error(
                    path,
                    "v3 profile components changed; expected "
                    + ", ".join(EXPECTED_PROFILE_FIELDS)
                    + f", got {', '.join(components) or '<unparsed>'}",
                )

        snapshot = self.java_class("CultivationSnapshotPayload")
        if snapshot is not None:
            path, source = snapshot
            if not re.search(r"CultivationProfile\s+profile", source):
                self.error(path, "profile snapshot must carry the complete v3 CultivationProfile")
            if "CultivationProfile.CODEC" not in source:
                self.error(path, "profile snapshot must encode the v3 profile including spiritual affinity")

        payloads = self.java_class("CultivationPayloads")
        if payloads is not None:
            path, source = payloads
            register = _method_body(source, "register") or ""
            clientbound_count = len(re.findall(r"registrar\.playToClient\s*\(", register))
            if clientbound_count != len(CLIENTBOUND_CULTIVATION_PAYLOADS):
                self.error(
                    path,
                    "cultivation networking must register exactly three clientbound snapshots",
                )
            for payload_name in CLIENTBOUND_CULTIVATION_PAYLOADS:
                if f"{payload_name}.TYPE" not in register:
                    self.error(path, f"missing clientbound registration for {payload_name}")
            if len(re.findall(r"registrar\.playToServer\s*\(", register)) != 1:
                self.error(
                    path,
                    "cultivation networking must register exactly one bounded C2S meditation intent",
                )
            if "MeditationIntentPayload.TYPE" not in register:
                self.error(path, "C2S registration must be MeditationIntentPayload")
            if re.search(r"registrar\.(?:common|bidirectional)\s*\(", register):
                self.error(path, "cultivation networking must not register a common/bidirectional payload")

        cultivation_java = self.root / JAVA_ROOT / "cultivation"
        if cultivation_java.is_dir():
            for java_path in sorted(cultivation_java.rglob("*.java")):
                try:
                    source = _strip_java_comments(java_path.read_text(encoding="utf-8"))
                except (OSError, UnicodeError) as exc:
                    self.error(java_path, f"cannot inspect cultivation source: {exc}")
                    continue
                if "playToServer(" in source and java_path.name != "CultivationPayloads.java":
                    self.error(java_path, "C2S registration must remain centralized in CultivationPayloads")
                if (
                    "implements CustomPacketPayload" in source
                    and java_path.name not in ALLOWED_CULTIVATION_PAYLOADS
                ):
                    self.error(
                        java_path,
                        "only the declared cultivation snapshot/time/status/intent payloads are allowed",
                    )

        intent = self.java_class("MeditationIntentPayload")
        if intent is not None:
            path, source = intent
            record_match = re.search(r"\brecord\s+MeditationIntentPayload\s*\(", source)
            components: tuple[str, ...] = ()
            if record_match is not None:
                open_paren = source.find("(", record_match.start())
                region = _balanced_content(source, open_paren, "(", ")")
                if region is not None:
                    names: list[str] = []
                    for part in _top_level_parts(region[0]):
                        name_match = re.search(r"([A-Za-z_$][\w$]*)\s*$", part)
                        if name_match:
                            names.append(name_match.group(1))
                    components = tuple(names)
            if components != ("action",):
                self.error(path, "MeditationIntentPayload must contain only the bounded action intent")
            if re.search(
                r"\b(?:BlockPos|Vec3|position|coordinate|velocity|speed|realmId|stageId|"
                r"spiritualRoot|techniqueId|cultivationProgress|stability|spiritualAffinity|"
                r"lifespan|reserve|cost|rate|inventory|elapsed|mastery|power|target|result)\b",
                source,
                re.IGNORECASE,
            ):
                self.error(path, "MeditationIntentPayload must not carry client-authored state")
            bounded_decode = re.search(
                r"networkId\s*>=\s*actions\.length", source
            ) or re.search(
                r"switch\s*\(\s*networkId\s*\).*?default\s*->\s*throw\s+new\s+"
                r"IllegalArgumentException",
                source,
                re.DOTALL,
            )
            if not re.search(r"readUnsignedByte\s*\(", source) or bounded_decode is None:
                self.error(path, "MeditationIntentPayload codec must reject unknown bounded action ids")

        state = self.java_class("ClientCultivationState")
        if state is not None:
            path, source = state
            public_methods = set(
                re.findall(r"\bpublic\s+static\s+[\w<>?,. ]+\s+(\w+)\s*\(", source)
            )
            if public_methods - {"latest", "time", "meditation"}:
                self.error(path, "client cultivation cache must expose no public mutation API")
            if re.search(r"\b(?:PacketDistributor|sendToServer|setData)\b", source):
                self.error(path, "client cultivation cache must remain read-only")
        screen = self.java_class("CultivationProfileScreen")
        if screen is not None:
            path, source = screen
            for view in ("PROFILE", "MEDITATION"):
                if view not in source:
                    self.error(path, f"H screen must expose the {view.title()} tab")
            for action in ("START_NORMAL", "START_SPIRIT", "STOP", "START_BREAKTHROUGH"):
                if action not in source:
                    self.error(path, f"H meditation tab must expose one {action} button")
            if not re.search(r"Button\.builder\s*\(", source):
                self.error(path, "H tabs and actions must use visible buttons")
            if not re.search(
                r"(?:ClientCultivationIntentSender\.send|PacketDistributor\.sendToServer)\s*\(",
                source,
            ):
                self.error(path, "H action buttons must reuse the bounded meditation intent sender")
            if re.search(r"\b(?:player\s*\.\s*)?setData\s*\(", source):
                self.error(path, "H screen must not write profile attachment data directly")
            set_view = _method_body(source, "setView") or ""
            if re.search(r"(?:sendToServer|IntentSender\.send|new\s+MeditationIntentPayload)", set_view):
                self.error(path, "switching H tabs must not send a cultivation intent")

            declared_keys = set(
                re.findall(
                    r'"(screen\.myvillage\.cultivation\.[a-z0-9_.-]+)"',
                    source,
                )
            )
            declared_keys = {key for key in declared_keys if not key.endswith(".")}
            required_keys = {
                "screen.myvillage.cultivation.tab.profile",
                "screen.myvillage.cultivation.tab.meditation",
                "screen.myvillage.cultivation.button.normal",
                "screen.myvillage.cultivation.button.spirit",
                "screen.myvillage.cultivation.button.stop",
                "screen.myvillage.cultivation.button.advancement",
                "screen.myvillage.cultivation.spiritual_affinity",
            }
            for key in sorted(required_keys - declared_keys):
                self.error(path, f"H screen must declare translatable UI key {key}")
            for locale in ("en_us", "zh_cn"):
                language_path = LANG_ROOT / f"{locale}.json"
                language = self.load_json(language_path, f"{locale} language file")
                if not isinstance(language, dict):
                    continue
                for key in sorted(declared_keys):
                    if not isinstance(language.get(key), str) or not language[key].strip():
                        self.error(language_path, f"missing non-empty H-screen translation {key}")

    def validate_scope_and_release(self) -> None:
        for directory in (DATA_ROOT / "recipe", DATA_ROOT / "worldgen"):
            if not (self.root / directory).is_dir():
                continue
            for path in sorted((self.root / directory).rglob("*.json")):
                try:
                    text = path.read_text(encoding="utf-8")
                except (OSError, UnicodeError) as exc:
                    self.error(path, f"cannot inspect out-of-scope resource: {exc}")
                    continue
                for identifier in STELES:
                    if f"myvillage:{identifier}" in text:
                        self.error(path, f"{identifier} must not have recipe or worldgen integration")

        properties_path = Path("gradle.properties")
        properties = self.read_text(properties_path, "Gradle version properties")
        mods_path = RESOURCE_ROOT / "META-INF/neoforge.mods.toml"
        mods = self.read_text(mods_path, "NeoForge mod metadata")
        readme_path = Path("README.md")
        readme = self.read_text(readme_path, "README")
        changelog_path = Path("CHANGELOG.md")
        changelog = self.read_text(changelog_path, "changelog")
        if None in (properties, mods, readme, changelog):
            return
        assert properties is not None and mods is not None and readme is not None and changelog is not None

        version_match = re.search(r"(?m)^mod_version\s*=\s*([^\s#]+)\s*$", properties)
        if version_match is None:
            self.error(properties_path, "missing mod_version")
            return
        version = version_match.group(1)
        mods_match = re.search(
            r'(?ms)^[ \t]*\[\[mods\]\][ \t]*$.*?'
            r'^[ \t]*modId\s*=\s*"myvillage"[ \t]*$.*?'
            r'^[ \t]*version\s*=\s*"([^"]+)"',
            mods,
        )
        if mods_match is None:
            self.error(mods_path, "cannot resolve myvillage mod version")
        elif mods_match.group(1) != version:
            self.error(mods_path, f"version {mods_match.group(1)} does not match {version}")
        if not re.search(
            rf"(?m)^[ \t]*##[ \t]+{re.escape(version)}[ \t]*$", changelog
        ):
            self.error(changelog_path, f"missing release heading for {version}")

        jar_versions = re.findall(
            r"build/libs/myvillage-([0-9]+\.[0-9]+\.[0-9]+(?:-fix[0-9]+)?)\.jar",
            readme,
        )
        if not jar_versions:
            self.error(readme_path, "missing explicit current jar-name example")
        for documented in sorted(set(jar_versions)):
            if documented != version:
                self.error(
                    readme_path,
                    f"jar-name example version {documented} does not match {version}",
                )
        for document_path, document in (
            (readme_path, readme),
            (changelog_path, changelog),
        ):
            for identifier in STELES:
                if identifier not in document:
                    self.error(document_path, f"missing initiation feature id {identifier}")
        for required in (
            "validate_cultivation_initiation.py",
            "mortal_qi_sensed",
            "awaken",
            "initiate",
        ):
            if required not in readme:
                self.error(readme_path, f"missing initiation usage detail {required}")
        if not (
            re.search(r"Profile.*Meditation|Meditation.*Profile", readme, re.IGNORECASE | re.DOTALL)
            or ("档案" in readme and "修炼" in readme)
        ):
            self.error(readme_path, "H screen must document both Profile and Meditation tabs")

    def validate(self) -> ValidationResult:
        self.validate_artifacts()
        self.validate_elements_and_technique()
        blocks = self.validate_block_registration()
        self.validate_block_resources(blocks)
        self.validate_generator()
        self.validate_services_and_commands()
        self.validate_profile_network_and_ui()
        self.validate_scope_and_release()
        return ValidationResult(tuple(self.errors), len(self.checked_files))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate MyVillage cultivation-initiation integration."
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
    result = CultivationInitiationValidator(args.root.resolve()).validate()
    if result.errors:
        print(
            f"cultivation initiation validation failed ({len(result.errors)} error(s)):",
            file=sys.stderr,
        )
        for error in result.errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(
        "cultivation initiation validation passed: "
        f"checked_files={result.checked_files}; blocks=2; elements=5; "
        "profile_schema=3; cultivation_c2s=1-bounded-intent; h_tabs=2; h_actions=4"
    )
    print(
        "algorithm determinism, affinity arithmetic, atomic transitions, and repeat "
        "semantics are intentionally owned by the Java test suite"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
