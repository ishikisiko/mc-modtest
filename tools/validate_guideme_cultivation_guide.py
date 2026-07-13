#!/usr/bin/env python3
"""Validate the bounded GuideME cultivation-guide integration and package."""

from __future__ import annotations

import argparse
import json
import posixpath
import re
import sys
import tomllib
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any

try:
    import yaml
except ImportError:  # pragma: no cover - the repository toolchain provides PyYAML
    yaml = None


ROOT = Path(__file__).resolve().parents[1]
GUIDEME_VERSION = "21.1.17"
MOD_VERSION = "0.25.1-fix1"

BUILD_GRADLE = Path("build.gradle")
GRADLE_PROPERTIES = Path("gradle.properties")
MODS_TOML = Path("src/main/resources/META-INF/neoforge.mods.toml")
GUIDE_DEFINITION = Path(
    "src/main/resources/assets/myvillage/guideme_guides/cultivation.json"
)
GUIDEBOOK = Path("guidebook")
RESOURCE_MIRROR = Path(
    "src/main/resources/assets/myvillage/guides/myvillage/cultivation"
)
ASSET_ROOT = Path("src/main/resources/assets/myvillage")
JAVA_ROOT = Path("src/main/java/com/example/myvillage")
MOD_ITEMS = JAVA_ROOT / "item/ModItems.java"
MOD_BLOCKS = JAVA_ROOT / "block/ModBlocks.java"
HANDBOOK_JAVA = JAVA_ROOT / "item/CultivationHandbookItem.java"
CLIENT_KEYS = JAVA_ROOT / "client/cultivation/ClientCultivationKeyMappings.java"
HANDBOOK_MODEL = ASSET_ROOT / "models/item/cultivation_handbook.json"
HANDBOOK_TEXTURE = ASSET_ROOT / "textures/item/cultivation_handbook.png"

DEFAULT_PAGES = (
    "index.md",
    "getting_started/initiation.md",
    "getting_started/cultivation_loop.md",
)
ENGLISH_PAGES = tuple(f"_en_us/{path}" for path in DEFAULT_PAGES)
EXPECTED_PAGES = frozenset((*DEFAULT_PAGES, *ENGLISH_PAGES))

PAGE_ITEM_IDS = {
    "index.md": frozenset({"myvillage:cultivation_handbook"}),
    "getting_started/initiation.md": frozenset(
        {
            "myvillage:spirit_testing_stele",
            "myvillage:technique_inheritance_stele",
        }
    ),
    "getting_started/cultivation_loop.md": frozenset(
        {
            "myvillage:low_grade_spirit_stone",
            "myvillage:spirit_stone_ore",
            "myvillage:deepslate_spirit_stone_ore",
        }
    ),
}

REQUIRED_KEYBINDS = frozenset(
    {
        "key.myvillage.open_cultivation_profile",
        "key.myvillage.start_normal_meditation",
        "key.myvillage.start_spirit_meditation",
        "key.myvillage.stop_meditation",
        "key.myvillage.start_advancement",
    }
)

PACKAGED_GUIDE_ROOT = "assets/myvillage/guides/myvillage/cultivation"
PACKAGED_PAGE_ENTRIES = frozenset(
    f"{PACKAGED_GUIDE_ROOT}/{path}" for path in EXPECTED_PAGES
)
REQUIRED_JAR_ENTRIES = frozenset(
    {
        "META-INF/neoforge.mods.toml",
        "assets/myvillage/guideme_guides/cultivation.json",
        "assets/myvillage/models/item/cultivation_handbook.json",
        "assets/myvillage/lang/en_us.json",
        "assets/myvillage/lang/zh_cn.json",
        "com/example/myvillage/item/ModItems.class",
        "com/example/myvillage/item/CultivationHandbookItem.class",
        *PACKAGED_PAGE_ENTRIES,
    }
)


class DuplicateJsonKey(ValueError):
    """Raised when a JSON object hides a value behind a duplicate key."""


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateJsonKey(f"duplicate JSON key {key!r}")
        result[key] = value
    return result


def _strip_code_comments(source: str) -> str:
    """Strip Java/Groovy comments while preserving quoted strings."""
    output: list[str] = []
    index = 0
    state = "code"
    while index < len(source):
        char = source[index]
        following = source[index + 1] if index + 1 < len(source) else ""
        if state == "code":
            if char == '"':
                state = "double"
                output.append(char)
            elif char == "'":
                state = "single"
                output.append(char)
            elif char == "/" and following == "/":
                state = "line"
                output.extend("  ")
                index += 1
            elif char == "/" and following == "*":
                state = "block"
                output.extend("  ")
                index += 1
            else:
                output.append(char)
        elif state in {"double", "single"}:
            output.append(char)
            if char == "\\" and following:
                output.append(following)
                index += 1
            elif (state == "double" and char == '"') or (
                state == "single" and char == "'"
            ):
                state = "code"
        elif state == "line":
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
                state = "double"
            elif char == "'":
                state = "single"
            elif char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    return source[opening_index + 1 : index]
        elif char == "\\" and following:
            index += 1
        elif (state == "double" and char == '"') or (
            state == "single" and char == "'"
        ):
            state = "code"
        index += 1
    return None


def _method_body(source: str, name: str) -> str | None:
    match = re.search(rf"\b{re.escape(name)}\s*\([^)]*\)\s*\{{", source)
    if match is None:
        return None
    return _balanced_body(source, source.find("{", match.start()))


def _string_set(value: Any) -> set[str] | None:
    if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
        return None
    return set(value)


@dataclass(frozen=True)
class Page:
    path: str
    text: str
    frontmatter: dict[str, Any]
    body: str


@dataclass(frozen=True)
class ValidationResult:
    errors: tuple[str, ...]
    checked_files: int
    jar_status: str


class GuideMECultivationGuideValidator:
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

    def validate_dependencies(self) -> None:
        properties = self.read_text(GRADLE_PROPERTIES, "Gradle properties")
        build = self.read_text(BUILD_GRADLE, "Gradle build")
        mods_text = self.read_text(MODS_TOML, "NeoForge mod metadata")

        if properties is not None:
            match = re.search(r"(?m)^guideme_version\s*=\s*([^\s#]+)\s*$", properties)
            if match is None:
                self.error(GRADLE_PROPERTIES, "missing guideme_version property")
            elif match.group(1) != GUIDEME_VERSION:
                self.error(
                    GRADLE_PROPERTIES,
                    f"guideme_version must be {GUIDEME_VERSION}, got {match.group(1)}",
                )

        if build is not None:
            active = _strip_code_comments(build)
            if "mavenCentral()" not in active:
                self.error(BUILD_GRADLE, "GuideME dependencies must resolve from Maven Central")
            compile_pattern = (
                r"compileOnly\s+[\"']org\.appliedenergistics:guideme:"
                r"\$\{guideme_version\}:api[\"']"
            )
            runtime_pattern = (
                r"runtimeOnly\s+[\"']org\.appliedenergistics:guideme:"
                r"\$\{guideme_version\}[\"']"
            )
            if re.search(compile_pattern, active) is None:
                self.error(
                    BUILD_GRADLE,
                    "missing Maven GuideME API compileOnly coordinate using guideme_version",
                )
            if re.search(runtime_pattern, active) is None:
                self.error(
                    BUILD_GRADLE,
                    "missing Maven GuideME full runtimeOnly coordinate using guideme_version",
                )
            local_patterns = (
                r"\bfiles\s*\([^)]*guideme",
                r"\bflatDir\s*\{",
                r"\bfileTree\s*\([^)]*guideme",
                r"guideme-21\.1\.17\.jar",
                r"(?:^|[/\\])libs[/\\][^\s\"']*guideme",
                r"\bjarJar\b[^\n]*guideme",
            )
            if any(re.search(pattern, active, re.IGNORECASE | re.MULTILINE) for pattern in local_patterns):
                self.error(
                    BUILD_GRADLE,
                    "local GuideME jar wiring is forbidden; use the published Maven artifacts",
                )

        if mods_text is None:
            return
        try:
            metadata = tomllib.loads(mods_text)
        except tomllib.TOMLDecodeError as exc:
            self.error(MODS_TOML, f"invalid TOML: {exc}")
            return
        dependencies = metadata.get("dependencies", {})
        myvillage = dependencies.get("myvillage", []) if isinstance(dependencies, dict) else []
        guide_entries = [
            entry
            for entry in myvillage
            if isinstance(entry, dict) and entry.get("modId") == "guideme"
        ] if isinstance(myvillage, list) else []
        if len(guide_entries) != 1:
            self.error(MODS_TOML, "must declare exactly one guideme dependency")
            return
        dependency = guide_entries[0]
        expected = {
            "type": "required",
            "side": "BOTH",
            "versionRange": "[21.1.17,22)",
        }
        for key, expected_value in expected.items():
            if dependency.get(key) != expected_value:
                self.error(
                    MODS_TOML,
                    f"guideme dependency {key} must be {expected_value!r}, got {dependency.get(key)!r}",
                )

    def validate_guide_definition(self) -> None:
        definition = self.load_json(GUIDE_DEFINITION, "GuideME guide definition")
        if not isinstance(definition, dict):
            return
        if definition.get("default_language") != "zh_cn":
            self.error(GUIDE_DEFINITION, "default_language must be zh_cn")
        if "custom_colors" in definition:
            self.error(GUIDE_DEFINITION, "first-slice guide must omit custom_colors")
        settings = definition.get("item_settings")
        if not isinstance(settings, dict):
            self.error(GUIDE_DEFINITION, "item_settings must be an object")
            return
        display_name = settings.get("display_name")
        expected_name = {
            "type": "translatable",
            "translate": "item.myvillage.cultivation_handbook",
        }
        if display_name != expected_name:
            self.error(
                GUIDE_DEFINITION,
                f"item_settings.display_name must be {expected_name!r}",
            )
        if settings.get("model") != "myvillage:item/cultivation_handbook":
            self.error(
                GUIDE_DEFINITION,
                "item_settings.model must be myvillage:item/cultivation_handbook",
            )
        tooltip_lines = settings.get("tooltip_lines")
        if not isinstance(tooltip_lines, list) or not tooltip_lines:
            self.error(GUIDE_DEFINITION, "item_settings.tooltip_lines must be non-empty")
        elif "item.myvillage.cultivation_handbook.tooltip" not in json.dumps(
            tooltip_lines, ensure_ascii=False
        ):
            self.error(
                GUIDE_DEFINITION,
                "item_settings.tooltip_lines must use the handbook tooltip translation",
            )

    def _parse_page(self, relative: str) -> Page | None:
        path = GUIDEBOOK / relative
        text = self.read_text(path, "GuideME Markdown page")
        if text is None:
            return None
        match = re.match(r"\A---[ \t]*\r?\n(.*?)\r?\n---[ \t]*\r?\n", text, re.DOTALL)
        if match is None:
            self.error(path, "missing YAML frontmatter delimited by ---")
            return Page(relative, text, {}, text)
        if yaml is None:
            self.error(path, "PyYAML is required to validate GuideME frontmatter")
            frontmatter: Any = {}
        else:
            try:
                frontmatter = yaml.safe_load(match.group(1))
            except yaml.YAMLError as exc:
                self.error(path, f"invalid YAML frontmatter: {exc}")
                frontmatter = {}
        if not isinstance(frontmatter, dict):
            self.error(path, "frontmatter must be a mapping")
            frontmatter = {}
        body = text[match.end() :]
        return Page(relative, text, frontmatter, body)

    def _registered_ids(self) -> tuple[set[str], set[str]]:
        item_text = self.read_text(MOD_ITEMS, "item registry")
        block_text = self.read_text(MOD_BLOCKS, "block registry")
        items: set[str] = set()
        blocks: set[str] = set()
        if item_text is not None:
            source = _strip_code_comments(item_text)
            items.update(
                re.findall(
                    r'ITEMS\.register(?:SimpleItem|Item)\(\s*"([a-z0-9_./-]+)"',
                    source,
                )
            )
        if block_text is not None:
            source = _strip_code_comments(block_text)
            blocks.update(
                re.findall(
                    r'BLOCKS\.registerBlock\(\s*"([a-z0-9_./-]+)"',
                    source,
                )
            )
        return ({f"myvillage:{item}" for item in items}, {f"myvillage:{block}" for block in blocks})

    @staticmethod
    def _tag_ids(body: str, tag: str) -> set[str]:
        return set(
            re.findall(
                rf'<{re.escape(tag)}\b[^>]*\bid\s*=\s*["\']([^"\']+)["\']',
                body,
            )
        )

    def _resolve_page_link(self, source: str, target: str) -> str | None:
        target = target.split("#", 1)[0].split("?", 1)[0]
        if not target or "://" in target or target.startswith("#"):
            return None
        if target.startswith("myvillage:"):
            return target.split(":", 1)[1]
        if ":" in target:
            return None
        language_prefix = "_en_us/" if source.startswith("_en_us/") else ""
        source_in_language = source[len(language_prefix) :]
        target_path = posixpath.normpath(
            posixpath.join(posixpath.dirname(source_in_language), target)
        )
        if target_path.startswith("../") or target_path == "..":
            return "__outside__"
        return f"{language_prefix}{target_path}"

    def _validate_frontmatter_and_links(self, pages: dict[str, Page]) -> None:
        for path, page in pages.items():
            location = GUIDEBOOK / path
            navigation = page.frontmatter.get("navigation")
            if not isinstance(navigation, dict):
                self.error(location, "frontmatter.navigation must be a mapping")
            else:
                if not isinstance(navigation.get("title"), str) or not navigation["title"].strip():
                    self.error(location, "navigation.title must be non-empty")
                base_path = path.removeprefix("_en_us/")
                parent = navigation.get("parent")
                if base_path == "index.md":
                    if parent is not None:
                        self.error(location, "index navigation must not declare a parent")
                elif parent != "index.md":
                    self.error(location, "detail navigation.parent must be index.md")

            base_path = path.removeprefix("_en_us/")
            item_ids = _string_set(page.frontmatter.get("item_ids"))
            expected_ids = set(PAGE_ITEM_IDS[base_path])
            if item_ids is None:
                self.error(location, "frontmatter.item_ids must be a string list")
            elif item_ids != expected_ids:
                self.error(
                    location,
                    f"item_ids must be {sorted(expected_ids)}, got {sorted(item_ids)}",
                )

            if re.search(r"(?m)^#\s+\S", page.body) is None:
                self.error(location, "page must contain a level-one heading")
            if re.search(r"<Color\b[^>]*\bid\s*=", page.body):
                self.error(location, "first-slice page must not reference a custom color id")

            for target in re.findall(r"\[[^\]]+\]\(([^)]+\.md(?:#[^)]*)?)\)", page.body):
                resolved = self._resolve_page_link(path, target)
                if resolved is not None and resolved not in pages:
                    self.error(location, f"internal page link does not resolve: {target}")

        for language_prefix in ("", "_en_us/"):
            index = pages.get(f"{language_prefix}index.md")
            if index is None:
                continue
            for target in (
                "getting_started/initiation.md",
                "getting_started/cultivation_loop.md",
            ):
                if not re.search(
                    rf"\]\((?:\./)?{re.escape(target)}(?:#[^)]*)?\)", index.body
                ):
                    self.error(
                        GUIDEBOOK / index.path,
                        f"index must link to {target}",
                    )
            if re.search(r"<SubPages\b[^>]*\bicons=\{true\}[^>]*/>", index.body) is None:
                self.error(GUIDEBOOK / index.path, "index must render <SubPages icons={true} />")

        for default_path in DEFAULT_PAGES:
            default = pages.get(default_path)
            english = pages.get(f"_en_us/{default_path}")
            if default is None or english is None:
                continue
            for key in ("parent", "position", "icon"):
                default_nav = default.frontmatter.get("navigation", {})
                english_nav = english.frontmatter.get("navigation", {})
                if isinstance(default_nav, dict) and isinstance(english_nav, dict):
                    if default_nav.get(key) != english_nav.get(key):
                        self.error(
                            GUIDEBOOK / f"_en_us/{default_path}",
                            f"navigation.{key} must match the default-language page",
                        )

    def _validate_references(self, pages: dict[str, Page]) -> None:
        registered_items, registered_blocks = self._registered_ids()
        for path, page in pages.items():
            location = GUIDEBOOK / path
            for item_id in sorted(self._tag_ids(page.body, "ItemLink")):
                if ":" not in item_id:
                    self.error(location, f"ItemLink id must be fully qualified: {item_id}")
                elif item_id.startswith("myvillage:") and item_id not in registered_items:
                    self.error(location, f"ItemLink references unknown registered item {item_id}")
            for block_id in sorted(self._tag_ids(page.body, "BlockImage")):
                if ":" not in block_id:
                    self.error(location, f"BlockImage id must be fully qualified: {block_id}")
                elif block_id.startswith("myvillage:") and block_id not in registered_blocks:
                    self.error(location, f"BlockImage references unknown registered block {block_id}")
            item_ids = _string_set(page.frontmatter.get("item_ids")) or set()
            for item_id in sorted(item_ids):
                if item_id not in registered_items:
                    self.error(location, f"item_ids references unknown registered item {item_id}")

        for language_prefix in ("", "_en_us/"):
            initiation = pages.get(f"{language_prefix}getting_started/initiation.md")
            loop = pages.get(f"{language_prefix}getting_started/cultivation_loop.md")
            if initiation is not None:
                visual_ids = self._tag_ids(initiation.body, "BlockImage") | self._tag_ids(
                    initiation.body, "ItemLink"
                )
                required = PAGE_ITEM_IDS["getting_started/initiation.md"]
                if not required.issubset(visual_ids):
                    self.error(
                        GUIDEBOOK / initiation.path,
                        f"visual/link markup omits {sorted(required - visual_ids)}",
                    )
            if loop is not None:
                item_links = self._tag_ids(loop.body, "ItemLink")
                block_images = self._tag_ids(loop.body, "BlockImage")
                if "myvillage:low_grade_spirit_stone" not in item_links:
                    self.error(
                        GUIDEBOOK / loop.path,
                        "cultivation loop must ItemLink the low-grade spirit stone",
                    )
                required_ores = {
                    "myvillage:spirit_stone_ore",
                    "myvillage:deepslate_spirit_stone_ore",
                }
                if not required_ores.issubset(block_images):
                    self.error(
                        GUIDEBOOK / loop.path,
                        f"cultivation loop BlockImage markup omits {sorted(required_ores - block_images)}",
                    )
                keybinds = self._tag_ids(loop.body, "KeyBind")
                if keybinds != set(REQUIRED_KEYBINDS):
                    self.error(
                        GUIDEBOOK / loop.path,
                        f"KeyBind ids must be {sorted(REQUIRED_KEYBINDS)}, got {sorted(keybinds)}",
                    )
                fixed_patterns = (
                    r"(?:按下?|按)\s*[HVBXGN]\s*键",
                    r"\bpress\s+(?:the\s+)?[HVBXGN]\b",
                )
                if any(re.search(pattern, loop.body, re.IGNORECASE) for pattern in fixed_patterns):
                    self.error(
                        GUIDEBOOK / loop.path,
                        "control instructions must use live KeyBind components, not fixed letters",
                    )

        keys_source = self.read_text(CLIENT_KEYS, "cultivation key registration")
        if keys_source is not None:
            for key in sorted(REQUIRED_KEYBINDS):
                if f'"{key}"' not in keys_source:
                    self.error(CLIENT_KEYS, f"missing registered key binding {key}")
            if "GLFW_KEY_X" not in keys_source:
                self.error(CLIENT_KEYS, "stop meditation must default to X")
            if "GLFW_KEY_G" in keys_source:
                self.error(CLIENT_KEYS, "MyVillage must leave GuideME's default G hotkey unreserved")
        for locale in ("en_us", "zh_cn"):
            lang_path = ASSET_ROOT / f"lang/{locale}.json"
            language = self.load_json(lang_path, f"{locale} language file")
            if not isinstance(language, dict):
                continue
            for key in sorted(REQUIRED_KEYBINDS):
                if not isinstance(language.get(key), str) or not language[key].strip():
                    self.error(lang_path, f"missing non-empty key binding translation {key}")

    def _require_terms(
        self,
        page: Page | None,
        terms: tuple[tuple[str, str], ...],
    ) -> None:
        if page is None:
            return
        for description, pattern in terms:
            if re.search(pattern, page.body, re.IGNORECASE | re.DOTALL) is None:
                self.error(GUIDEBOOK / page.path, f"missing factual anchor: {description}")

    def _validate_content(self, pages: dict[str, Page]) -> None:
        self._require_terms(
            pages.get("index.md"),
            (
                ("spiritual-root testing", r"测灵"),
                ("Basic Breathing inheritance", r"吐纳诀"),
                ("progress", r"(?:进度|修为)"),
                ("stability", r"稳定"),
                ("advancement", r"冲关"),
                ("Qi Refining IV release ceiling", r"炼气(?:四层|\s*IV)"),
                ("deferred Qi Refining V", r"炼气(?:五层|\s*V)"),
                ("deferred Foundation Establishment", r"筑基"),
                ("unavailable/deferred boundary", r"(?:未实现|未开放|暂不可|后续|延期)"),
            ),
        )
        self._require_terms(
            pages.get("_en_us/index.md"),
            (
                ("spiritual-root awakening", r"(?:spiritual[- ]root|awakening)"),
                ("Basic Breathing inheritance", r"Basic Breathing"),
                ("progress", r"\bprogress\b"),
                ("stability", r"\bstability\b"),
                ("deterministic advancement", r"deterministic.{0,30}advancement|advancement.{0,30}deterministic"),
                ("Qi Refining IV release ceiling", r"Qi Refining IV"),
                ("deferred Qi Refining V", r"Qi Refining V"),
                ("deferred Foundation Establishment", r"Foundation Establishment"),
                ("unavailable/deferred boundary", r"(?:not (?:yet )?(?:playable|implemented|available)|deferred)"),
            ),
        )

        self._require_terms(
            pages.get("getting_started/initiation.md"),
            (
                ("separate right-click interactions", r"右键"),
                ("testing stele", r"测灵碑"),
                ("inheritance stele", r"(?:传承碑|传功碑)"),
                ("repeat does not reroll", r"(?:不|不会|无法).{0,12}重抽"),
                (
                    "repeat does not reset mastery",
                    r"(?:不|不会|无法).{0,24}(?:重置.{0,16}熟练度|熟练度.{0,16}重置)",
                ),
                ("Basic Breathing", r"吐纳诀"),
            ),
        )
        self._require_terms(
            pages.get("_en_us/getting_started/initiation.md"),
            (
                ("separate right-click interactions", r"right[- ]click"),
                ("testing stele", r"(?:testing stele|spirit testing)"),
                ("inheritance stele", r"(?:inheritance stele|technique inheritance)"),
                ("repeat does not reroll", r"(?:does not|won't|will not|no).{0,20}reroll"),
                ("repeat does not reset mastery", r"(?:does not|won't|will not|no).{0,20}reset.{0,20}mastery"),
                ("Basic Breathing", r"Basic Breathing"),
            ),
        )

        numeric_anchors = (
            "40", "10", "50", "1000", "500", "1100", "550", "1200", "600",
            "1300", "650", "100", "120", "200", "80", "5",
        )
        for path in (
            "getting_started/cultivation_loop.md",
            "_en_us/getting_started/cultivation_loop.md",
        ):
            page = pages.get(path)
            if page is None:
                continue
            for number in numeric_anchors:
                if re.search(rf"(?<!\d){re.escape(number)}(?!\d)", page.body) is None:
                    self.error(GUIDEBOOK / path, f"missing factual numeric anchor {number}")

        self._require_terms(
            pages.get("getting_started/cultivation_loop.md"),
            (
                ("eligibility preparation", r"准备"),
                ("normal affinity gain", r"普通打坐.{0,120}亲和|亲和.{0,120}普通打坐"),
                ("spirit-stone direct mode", r"灵石"),
                ("stage costs 1/1/2/3", r"1\s*[/／,，]\s*1\s*[/／,，]\s*2\s*[/／,，]\s*3"),
                (
                    "progress before stability",
                    r"(?:进度|修为).{0,120}(?:满|上限|圆满).{0,120}稳定",
                ),
                (
                    "integer-floor half stability",
                    r"稳定.{0,60}(?:折半|一半|除以(?:\s*2|二)|/\s*2)",
                ),
                ("interruptions", r"中断"),
                ("lifespan gating", r"寿元"),
                ("Qi Refining IV ceiling", r"炼气(?:四层|\s*IV)"),
            ),
        )
        self._require_terms(
            pages.get("_en_us/getting_started/cultivation_loop.md"),
            (
                ("eligibility preparation", r"prepar"),
                ("normal affinity gain", r"normal.{0,120}affinity|affinity.{0,120}normal"),
                ("spirit-stone direct mode", r"spirit[- ]stone"),
                ("stage costs 1/1/2/3", r"1\s*[/,]\s*1\s*[/,]\s*2\s*[/,]\s*3"),
                ("progress before stability", r"progress.{0,100}(?:full|cap).{0,100}stability"),
                (
                    "integer-floor half stability",
                    r"(?:floor|integer).{0,80}(?:half.{0,20}stability|"
                    r"stability.{0,40}(?:half|two|2))|"
                    r"stability.{0,60}(?:floor|integer|half)",
                ),
                ("interruptions", r"interrupt"),
                ("lifespan gating", r"lifespan"),
                ("Qi Refining IV ceiling", r"Qi Refining IV"),
            ),
        )

    def validate_pages(self) -> None:
        guidebook_root = self.root / GUIDEBOOK
        actual_pages = {
            path.relative_to(guidebook_root).as_posix()
            for path in guidebook_root.rglob("*.md")
        } if guidebook_root.is_dir() else set()
        if actual_pages != set(EXPECTED_PAGES):
            missing = sorted(EXPECTED_PAGES - actual_pages)
            extra = sorted(actual_pages - EXPECTED_PAGES)
            self.error(
                GUIDEBOOK,
                f"page topology must be exactly six paired pages; missing={missing}, extra={extra}",
            )

        mirror = self.root / RESOURCE_MIRROR
        mirrored_pages = sorted(
            path.relative_to(mirror).as_posix() for path in mirror.rglob("*.md")
        ) if mirror.is_dir() else []
        if mirrored_pages:
            self.error(
                RESOURCE_MIRROR,
                f"checked-in Markdown mirror is forbidden: {mirrored_pages}",
            )

        pages: dict[str, Page] = {}
        for relative in sorted(EXPECTED_PAGES):
            page = self._parse_page(relative)
            if page is not None:
                pages[relative] = page
        self._validate_frontmatter_and_links(pages)
        self._validate_references(pages)
        self._validate_content(pages)

    def validate_build_mapping_and_preview(self) -> None:
        build = self.read_text(BUILD_GRADLE, "Gradle build")
        if build is None:
            return
        active = _strip_code_comments(build)
        process_match = re.search(
            r"tasks\.named\(\s*['\"]processResources['\"]\s*\)\s*\{(.*?)\n\}",
            active,
            re.DOTALL,
        )
        process_body = process_match.group(1) if process_match else ""
        if not process_body:
            self.error(BUILD_GRADLE, "missing processResources configuration")
        elif re.search(
            r"from\(\s*['\"]guidebook['\"]\s*\)\s*\{.*?"
            r"into\s+['\"]assets/myvillage/guides/myvillage/cultivation['\"]",
            process_body,
            re.DOTALL,
        ) is None:
            self.error(
                BUILD_GRADLE,
                "processResources must map root guidebook to the cultivation guide resource path",
            )

        guide_match = re.search(r"\bguide\s*\{(.*?)\n\s*\}", active, re.DOTALL)
        guide_body = guide_match.group(1) if guide_match else ""
        if not guide_body or "client()" not in guide_body:
            self.error(BUILD_GRADLE, "missing client-backed guide run configuration")
            return
        required_fragments = (
            "systemProperty 'guideme.myvillage.cultivation.sources', file('guidebook').absolutePath",
            "systemProperty 'guideme.myvillage.cultivation.sourcesNamespace', 'myvillage'",
            "systemProperty 'guideme.showOnStartup', 'myvillage:cultivation'",
            "systemProperty 'guideme.validateAtStartup', 'myvillage:cultivation'",
        )
        normalized = re.sub(r"\s+", " ", guide_body)
        for fragment in required_fragments:
            if re.sub(r"\s+", " ", fragment) not in normalized:
                self.error(BUILD_GRADLE, f"runGuide missing exact preview setting: {fragment}")
        if "!index.md" in guide_body or "myvillage:index.md" in guide_body:
            self.error(
                BUILD_GRADLE,
                "showOnStartup/validateAtStartup must use only guide id myvillage:cultivation",
            )

    def validate_handbook(self) -> None:
        registry = self.read_text(MOD_ITEMS, "item registry")
        handbook = self.read_text(HANDBOOK_JAVA, "handbook implementation")
        if registry is not None:
            active = _strip_code_comments(registry)
            registration = re.search(
                r"CULTIVATION_HANDBOOK\s*=\s*ITEMS\.registerItem\(\s*"
                r'"cultivation_handbook"\s*,.*?new\s+CultivationHandbookItem\('
                r".*?\.stacksTo\(\s*1\s*\).*?\)\s*\)\s*;",
                active,
                re.DOTALL,
            )
            if registration is None:
                self.error(
                    MOD_ITEMS,
                    "missing one-stack cultivation_handbook functional item registration",
                )
            if re.search(
                r"output\.accept\(\s*CULTIVATION_HANDBOOK\.get\(\)\s*\)", active
            ) is None:
                self.error(MOD_ITEMS, "creative tab myvillage:main omits CULTIVATION_HANDBOOK")
            stele_position = active.find("output.accept(TECHNIQUE_INHERITANCE_STELE_ITEM.get())")
            handbook_position = active.find("output.accept(CULTIVATION_HANDBOOK.get())")
            if stele_position < 0 or handbook_position <= stele_position:
                self.error(
                    MOD_ITEMS,
                    "creative tab must place CULTIVATION_HANDBOOK after the inheritance stele",
                )

        if handbook is not None:
            active = _strip_code_comments(handbook)
            if "extends Item" not in active:
                self.error(HANDBOOK_JAVA, "CultivationHandbookItem must extend Item")
            if "GuidesCommon.openGuide" not in active:
                self.error(HANDBOOK_JAVA, "handbook must delegate opening to GuidesCommon.openGuide")
            if not (
                "MyVillageMod.MOD_ID" in active
                and re.search(r'["\']cultivation["\']', active)
            ):
                self.error(HANDBOOK_JAVA, "handbook guide id must be myvillage:cultivation")
            use_body = _method_body(active, "use")
            if use_body is None:
                self.error(HANDBOOK_JAVA, "missing handbook use method")
            else:
                guarded = re.search(
                    r"if\s*\(\s*level\.isClientSide\(\)\s*\)\s*\{[^}]*"
                    r"GuidesCommon\.openGuide\s*\(",
                    use_body,
                    re.DOTALL,
                )
                if guarded is None:
                    self.error(
                        HANDBOOK_JAVA,
                        "GuideME open call must be guarded by level.isClientSide()",
                    )
                if "InteractionResultHolder.sidedSuccess" not in use_body:
                    self.error(HANDBOOK_JAVA, "handbook use must retain the stack via sidedSuccess")
            tooltip_body = _method_body(active, "appendHoverText")
            if tooltip_body is None or "item.myvillage.cultivation_handbook.tooltip" not in tooltip_body:
                self.error(HANDBOOK_JAVA, "handbook must append its translated tooltip")
            forbidden_network = (
                "PacketDistributor",
                "StreamCodec",
                "CustomPacketPayload",
                "sendToServer",
                "sendToPlayer",
            )
            for fragment in forbidden_network:
                if fragment in active:
                    self.error(
                        HANDBOOK_JAVA,
                        f"handbook must not introduce a MyVillage guide payload ({fragment})",
                    )

        network_root = self.root / JAVA_ROOT / "cultivation/network"
        if network_root.is_dir():
            for path in sorted(network_root.rglob("*.java")):
                try:
                    source = path.read_text(encoding="utf-8")
                except (OSError, UnicodeError) as exc:
                    self.error(path, f"cannot inspect network source: {exc}")
                    continue
                lowered = _strip_code_comments(source).lower()
                if "cultivation_handbook" in lowered or "guidescommon" in lowered:
                    self.error(path, "network source must not add a handbook guide-opening payload")

        model = self.load_json(HANDBOOK_MODEL, "handbook item model")
        if model != {"parent": "guideme:item/guide_base"}:
            self.error(
                HANDBOOK_MODEL,
                "handbook model must contain only parent guideme:item/guide_base",
            )
        if (self.root / HANDBOOK_TEXTURE).exists():
            self.error(HANDBOOK_TEXTURE, "custom handbook texture is forbidden in the first slice")

        for locale in ("en_us", "zh_cn"):
            lang_path = ASSET_ROOT / f"lang/{locale}.json"
            language = self.load_json(lang_path, f"{locale} language file")
            if not isinstance(language, dict):
                continue
            for key in (
                "item.myvillage.cultivation_handbook",
                "item.myvillage.cultivation_handbook.tooltip",
            ):
                if not isinstance(language.get(key), str) or not language[key].strip():
                    self.error(lang_path, f"missing non-empty handbook translation {key}")

    def validate_documentation_and_release(self) -> None:
        properties = self.read_text(GRADLE_PROPERTIES, "Gradle properties")
        mods = self.read_text(MODS_TOML, "NeoForge mod metadata")
        readme = self.read_text(Path("README.md"), "README")
        changelog = self.read_text(Path("CHANGELOG.md"), "changelog")
        kb = self.read_text(
            Path("docs/ai-kb/31_guideme_cultivation_guide.md"),
            "GuideME cultivation-guide knowledge-base note",
        )
        index = self.read_text(Path("docs/ai-kb/INDEX.md"), "knowledge-base index")
        agents = self.read_text(Path("AGENTS.md"), "agent guidance")

        if properties is not None:
            match = re.search(r"(?m)^mod_version\s*=\s*([^\s#]+)\s*$", properties)
            if match is None or match.group(1) != MOD_VERSION:
                got = match.group(1) if match else None
                self.error(GRADLE_PROPERTIES, f"mod_version must be {MOD_VERSION}, got {got!r}")
        if mods is not None:
            match = re.search(
                r'(?ms)^\s*\[\[mods\]\]\s*$.*?^\s*modId\s*=\s*"myvillage"\s*$.*?'
                r'^\s*version\s*=\s*"([^"]+)"',
                mods,
            )
            if match is None or match.group(1) != MOD_VERSION:
                got = match.group(1) if match else None
                self.error(MODS_TOML, f"myvillage version must be {MOD_VERSION}, got {got!r}")
        if changelog is not None:
            if re.search(rf"(?m)^##\s+{re.escape(MOD_VERSION)}\s*$", changelog) is None:
                self.error(Path("CHANGELOG.md"), f"missing release heading {MOD_VERSION}")
            if not all(term.lower() in changelog.lower() for term in ("GuideME", "cultivation_handbook")):
                self.error(
                    Path("CHANGELOG.md"),
                    f"{MOD_VERSION} release notes must mention GuideME and cultivation_handbook",
                )
        if readme is not None:
            required = (
                "/give @s myvillage:cultivation_handbook",
                "/guidemec myvillage:cultivation open",
                "runGuide",
                "validate_guideme_cultivation_guide.py",
                "not_verified",
            )
            for fragment in required:
                if fragment not in readme:
                    self.error(Path("README.md"), f"missing GuideME usage detail {fragment}")
            if "/guidemec open myvillage:cultivation" in readme:
                self.error(
                    Path("README.md"),
                    "GuideME preview command order is wrong; use /guidemec myvillage:cultivation open",
                )
            guide_required = (
                "GuideME" in readme
                and re.search(r"(?:require(?:d|s)|必须|必需|需要).{0,80}(?:client|客户端)", readme, re.IGNORECASE | re.DOTALL)
                and re.search(r"(?:require(?:d|s)|必须|必需|需要).{0,80}(?:server|服务端)", readme, re.IGNORECASE | re.DOTALL)
            )
            if not guide_required:
                self.error(Path("README.md"), "must document GuideME as required on client and server")
            jar_versions = re.findall(
                r"(?:build/libs/)?myvillage-([0-9]+\.[0-9]+\.[0-9]+(?:-fix[0-9]+)?)\.jar",
                readme,
            )
            if not jar_versions:
                self.error(Path("README.md"), "missing explicit current jar-name example")
            for version in sorted(set(jar_versions)):
                if version != MOD_VERSION:
                    self.error(
                        Path("README.md"),
                        f"jar-name example version {version} does not match {MOD_VERSION}",
                    )
        if kb is not None:
            for fragment in (
                "guidebook/",
                "myvillage:cultivation",
                "add-guideme-cultivation-guide-slice",
                "/guidemec myvillage:cultivation open",
            ):
                if fragment not in kb:
                    self.error(
                        Path("docs/ai-kb/31_guideme_cultivation_guide.md"),
                        f"missing integration fact {fragment}",
                    )
        if index is not None and "31_guideme_cultivation_guide.md" not in index:
            self.error(Path("docs/ai-kb/INDEX.md"), "missing GuideME cultivation guide entry")
        if agents is not None:
            for fragment in (
                "validate_guideme_cultivation_guide.py",
                "31_guideme_cultivation_guide.md",
            ):
                if fragment not in agents:
                    self.error(Path("AGENTS.md"), f"missing GuideME acceptance guidance {fragment}")

    def _expected_jar(self) -> tuple[Path | None, str | None]:
        properties_path = self.root / GRADLE_PROPERTIES
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

    def validate_jar(self, explicit_jar: Path | None, require_current_jar: bool) -> str:
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

        input_paths = [
            self.root / BUILD_GRADLE,
            self.root / GRADLE_PROPERTIES,
            self.root / MODS_TOML,
            self.root / GUIDE_DEFINITION,
            self.root / HANDBOOK_JAVA,
            self.root / MOD_ITEMS,
            self.root / HANDBOOK_MODEL,
            self.root / ASSET_ROOT / "lang/en_us.json",
            self.root / ASSET_ROOT / "lang/zh_cn.json",
            *(self.root / GUIDEBOOK / page for page in EXPECTED_PAGES),
        ]
        existing_inputs = [path for path in input_paths if path.is_file()]
        if existing_inputs and not explicit:
            newest_input = max(path.stat().st_mtime for path in existing_inputs)
            if jar.stat().st_mtime < newest_input:
                status = f"skipped_stale ({self.label(jar)} predates source/resources)"
                if require_current_jar:
                    self.error(jar, "practical jar is stale; rebuild before required jar inspection")
                return status

        try:
            with zipfile.ZipFile(jar) as archive:
                names = set(archive.namelist())
                for entry in sorted(REQUIRED_JAR_ENTRIES - names):
                    self.error(jar, f"missing packaged entry {entry}")
                actual_pages = {
                    name
                    for name in names
                    if name.startswith(f"{PACKAGED_GUIDE_ROOT}/") and name.endswith(".md")
                }
                if actual_pages != set(PACKAGED_PAGE_ENTRIES):
                    self.error(
                        jar,
                        "packaged guide page topology must be exactly six pages; "
                        f"missing={sorted(PACKAGED_PAGE_ENTRIES - actual_pages)}, "
                        f"extra={sorted(actual_pages - PACKAGED_PAGE_ENTRIES)}",
                    )
                source_map = {
                    "META-INF/neoforge.mods.toml": self.root / MODS_TOML,
                    "assets/myvillage/guideme_guides/cultivation.json": self.root / GUIDE_DEFINITION,
                    "assets/myvillage/models/item/cultivation_handbook.json": self.root / HANDBOOK_MODEL,
                    "assets/myvillage/lang/en_us.json": self.root / ASSET_ROOT / "lang/en_us.json",
                    "assets/myvillage/lang/zh_cn.json": self.root / ASSET_ROOT / "lang/zh_cn.json",
                    **{
                        f"{PACKAGED_GUIDE_ROOT}/{page}": self.root / GUIDEBOOK / page
                        for page in EXPECTED_PAGES
                    },
                }
                for entry, source in source_map.items():
                    if entry in names and source.is_file() and archive.read(entry) != source.read_bytes():
                        self.error(jar, f"packaged entry differs from source truth: {entry}")

                if "assets/myvillage/textures/item/cultivation_handbook.png" in names:
                    self.error(jar, "must not package a MyVillage handbook texture")
                for name in sorted(names):
                    lowered = name.lower()
                    if lowered.startswith("guideme/") and lowered.endswith(".class"):
                        self.error(jar, f"must not copy GuideME class {name}")
                    if lowered.endswith(".jar") and "guideme" in PurePosixPath(lowered).name:
                        self.error(jar, f"must not embed GuideME jar {name}")
                for metadata_name in (
                    "META-INF/jarjar/metadata.json",
                    "META-INF/jarjar/metadata.toml",
                ):
                    if metadata_name in names:
                        metadata_text = archive.read(metadata_name).decode("utf-8", errors="replace")
                        if "guideme" in metadata_text.lower():
                            self.error(jar, f"must not declare nested GuideME in {metadata_name}")
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
        self.validate_dependencies()
        self.validate_guide_definition()
        self.validate_pages()
        self.validate_build_mapping_and_preview()
        self.validate_handbook()
        self.validate_documentation_and_release()
        jar_status = self.validate_jar(jar, require_current_jar)
        return ValidationResult(tuple(self.errors), len(self.checked_files), jar_status)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root",
        type=Path,
        default=ROOT,
        help="repository root (defaults to the parent of this tools directory)",
    )
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
    result = GuideMECultivationGuideValidator(args.root.resolve()).validate(
        jar=args.jar,
        require_current_jar=args.require_current_jar,
    )
    if result.errors:
        print(
            f"GuideME cultivation-guide validation failed ({len(result.errors)} error(s)):",
            file=sys.stderr,
        )
        for error in result.errors:
            print(f"- {error}", file=sys.stderr)
        print(f"jar_check={result.jar_status}", file=sys.stderr)
        return 1
    print(
        "GuideME cultivation-guide validation passed: "
        f"checked_files={result.checked_files}; guide_pages=6; locales=2"
    )
    print(f"jar_check={result.jar_status}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
