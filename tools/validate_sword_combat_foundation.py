#!/usr/bin/env python3
"""Validate the cultivation sword-combat contract: data, cross-file resources, and boundaries.

The move facts live in the bundled combat data (``data/myvillage/combat/``), read through
``tools/combat_data.py``.  This validator checks that data against the schema and the move
invariants, then cross-checks it against the resources that depend on it: the PAL player
animations, translations, sound events, and, for every weapon in the index, its item model,
the models that wraps, their textures, the 3D model its geometry contract describes, its
first-person rig, its geometry contract (including an optional off-hand grip), and the model
generator the contract names.  It holds no per-move numbers; the accepted Qingfeng values are
pinned once in ``tools/tests/test_combat_style_baseline.py``.  The Qingfeng item checks
(``validate_qingfeng_item``) are deliberately specific to that accepted item.

Java source checks are limited to invariants that do not depend on class or method names
inside the combat packages: PAL and client imports stay client-side, the client-to-server
combat payloads are empty records, combat code makes no vanilla attack, and the impact
payload is clientbound only without damage or health.  Behaviour inside the combat packages
is covered by the Java unit tests.
"""

from __future__ import annotations

import hashlib
import glob
import json
import math
import re
import struct
import subprocess
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path

try:
    from tools import combat_data
except ImportError:  # run as a script: tools/ itself is on sys.path
    import combat_data


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = combat_data.RESOURCES
JAVA_ROOT = "src/main/java"
COMPILED_CLASSES = "build/classes/java/main"
COMBAT_PACKAGE = "com/example/myvillage/combat"
CLIENT_COMBAT_PACKAGE = "com/example/myvillage/client/combat"
NETWORK_PACKAGE = "com/example/myvillage/network"

PAL_JAR_NAME = "PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar"
PAL_SHA256 = "b0836ad98db1e614f1e62cb40d5943eb4ba7d51f298e4b3ad0746770364ab072"
PAL_FORMAT_VERSION = "1.8.0"
REQUIRED_ANIMATION_BONES = ("body", "head", "right_arm", "left_arm", "right_leg", "left_leg")
TICKS_PER_SECOND = 20.0
# A first-person strike window covers the server active ticks and lasts at most this long.
MAX_STRIKE_TICKS = 3.0
GEOMETRY_FIELDS = ("grip_center", "handle", "collar", "butt", "head_base", "head_tip",
                   "edge_axis", "flat_axis", "axes", "model", "generator")
# A contract's generator is a standard-library script in tools/ that writes the contract, the 3D
# model, and its textures, and verifies them with --check.
GENERATOR_SCRIPT = re.compile(r"^tools/[A-Za-z0-9_]+\.py$")
# Two fists on one shaft: a fist is the 4 px bottom cube of the player's arm, drawn at the player
# renderer's 0.9375 scale.  Along the weapon axis the fist centres stay this far apart in blocks,
# which in contract pixels is FIST_WIDTH_BLOCKS * 16 / (the 3D model's third-person scale); the
# same rule as the PAL generator's off-hand solve.
PLAYER_SCALE = 0.9375
FIST_WIDTH_BLOCKS = 4 / 16 * PLAYER_SCALE
AXIS_TOLERANCE_PX = 1.0e-3
# The display contexts the combat tooling reads from a weapon's 3D model: the PAL generator uses
# the third-person transform, the first-person rig the first-person one.
WEAPON_HAND_DISPLAYS = ("thirdperson_righthand", "firstperson_righthand")
# Model and texture references into these namespaces are vanilla and not part of this jar.
EXTERNAL_NAMESPACES = ("minecraft",)
SEPARATE_TRANSFORMS = "neoforge:separate_transforms"
# Combat UI strings that are not move data (mode toggle and debug command feedback).
COMBAT_UI_TRANSLATIONS = (
    "key.myvillage.toggle_combat_mode",
    "message.myvillage.combat.mode.cultivation",
    "message.myvillage.combat.mode.vanilla",
    "commands.myvillage.combat.debug.on",
    "commands.myvillage.combat.debug.off",
    "commands.myvillage.combat.debug.player_only",
)
FEEDBACK_SOUND_FIELDS = ("swing_sound", "hit_sound", "heavy_layer_sound")
BLADE_CUT_PARTICLE = f"{RESOURCES}/assets/myvillage/particles/blade_cut.json"
BLADE_CUT_TEXTURE = f"{RESOURCES}/assets/myvillage/textures/particle/blade_cut.png"
WEAPON_MODEL_GENERATOR_DRIFT = "COMBAT_WEAPON_MODEL_GENERATOR_DRIFT"
# Generators that always run.  The Qingfeng model generator is pinned here as well as named by its
# geometry contract, so the accepted Qingfeng model stays checked even if its contract loses the
# field or the weapon leaves the index; every other weapon's model generator comes from the
# ``generator`` field of its geometry contract (see generator_checks).
GENERATOR_CHECKS = (
    ("tools/gen_sword_pal_anims.py", "COMBAT_PAL_GENERATOR_DRIFT"),
    ("tools/gen_blade_cut_sprite.py", "COMBAT_BLADE_CUT_SPRITE_DRIFT"),
    ("tools/gen_qingfeng_sword_model.py", WEAPON_MODEL_GENERATOR_DRIFT),
)
GENERATOR_TIMEOUT_SECONDS = 120
QINGFENG_ITEM = "myvillage:qingfeng_sword"
QINGFENG_MODEL = f"{RESOURCES}/assets/myvillage/models/item/qingfeng_sword.json"
QINGFENG_TEXTURE = f"{RESOURCES}/assets/myvillage/textures/item/qingfeng_sword.png"
QINGFENG_MODEL_3D = f"{RESOURCES}/assets/myvillage/models/item/qingfeng_sword_3d.json"
QINGFENG_MODEL_TEXTURE = f"{RESOURCES}/assets/myvillage/textures/item/qingfeng_sword_model.png"
QINGFENG_RECIPE = f"{RESOURCES}/data/myvillage/recipe/qingfeng_sword.json"
SWORD_TAG = f"{RESOURCES}/data/minecraft/tags/item/swords.json"
# Fields a payload sent to clients must never carry: the server alone decides damage.
CLIENTBOUND_FORBIDDEN_FIELDS = ("damage", "health")
IMPACT_FORBIDDEN_FIELDS = ("damage", "health", "amount", "knockback", "velocity", "motion")
VANILLA_ATTACK_PATTERNS = (
    (re.compile(r"\b(?:player|attacker|serverPlayer|localPlayer)\s*\.\s*attack\s*\("), "player.attack("),
    (re.compile(r"\bgameMode(?:\(\))?\s*\.\s*attack\s*\("), "gameMode.attack("),
    (re.compile(r"\bServerboundInteractPacket\s*\.\s*createAttackPacket\b"), "ServerboundInteractPacket.createAttackPacket"),
)
DOC_REQUIREMENTS = {
    "README.md": (
        "validate_sword_combat_foundation.py", "gen_sword_pal_anims.py --check", "myvillage_pal_smoke",
        "combat_smoke_server", "not_verified"),
    "docs/ai-kb/32_pal_combat_integration.md": (
        PAL_JAR_NAME, "gen_sword_pal_anims.py --check"),
    "AGENTS.md": (
        "validate_sword_combat_foundation.py", PAL_JAR_NAME, "gen_sword_pal_anims.py"),
}


@dataclass(frozen=True)
class Finding:
    code: str
    detail: str

    def __str__(self) -> str:
        return f"{self.code}: {self.detail}"


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def relative(root: Path, path: Path) -> str:
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        return path.as_posix()


def require_file(path: Path, root: Path, code: str, findings: list[Finding]) -> bool:
    if path.is_file():
        return True
    findings.append(Finding(code, relative(root, path)))
    return False


def require_contains(content: str, needle: str, code: str, detail: str, findings: list[Finding]) -> None:
    if needle not in content:
        findings.append(Finding(code, detail))


def read_json(path: Path, root: Path, code: str, findings: list[Finding]):
    """Parsed JSON, or None after recording a finding (missing file or invalid JSON)."""
    if not require_file(path, root, code, findings):
        return None
    try:
        return json.loads(text(path))
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        findings.append(Finding(code, f"{relative(root, path)}: {exc}"))
        return None


def is_number(value) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def namespace_of(resource_id: str) -> str:
    return combat_data.split_id(resource_id)[0]


# ---------------------------------------------------------------------------------------------
# Player Animation Library dependency.
# ---------------------------------------------------------------------------------------------

def validate_pal_jar(root: Path, findings: list[Finding]) -> None:
    jar = root / PAL_JAR_NAME
    if not require_file(jar, root, "PAL_JAR_MISSING", findings):
        return

    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    if digest != PAL_SHA256:
        findings.append(Finding("PAL_JAR_SHA256", f"expected {PAL_SHA256}, got {digest}"))
        return

    try:
        with zipfile.ZipFile(jar) as archive:
            metadata = archive.read("META-INF/neoforge.mods.toml").decode("utf-8")
            license_text = archive.read("LICENSE").decode("utf-8")
            names = set(archive.namelist())
    except (KeyError, zipfile.BadZipFile, UnicodeDecodeError) as exc:
        findings.append(Finding("PAL_JAR_METADATA", str(exc)))
        return

    require_contains(metadata, 'modId = "player_animation_library"', "PAL_MOD_ID", PAL_JAR_NAME, findings)
    require_contains(metadata, 'version = "1.1.4+mc.1.21.1"', "PAL_VERSION", PAL_JAR_NAME, findings)
    require_contains(metadata, 'license = "MIT License"', "PAL_LICENSE_METADATA", PAL_JAR_NAME, findings)
    require_contains(metadata, 'side = "BOTH"', "PAL_DECLARED_SIDE", PAL_JAR_NAME, findings)
    require_contains(license_text, "MIT License", "PAL_LICENSE_TEXT", PAL_JAR_NAME, findings)
    for entry in (
            "com/zigythebird/playeranim/api/PlayerAnimationFactory.class",
            "com/zigythebird/playeranim/api/PlayerAnimationAccess.class",
            "com/zigythebird/playeranim/animation/PlayerAnimationController.class"):
        if entry not in names:
            findings.append(Finding("PAL_API_CLASS", entry))


def validate_dependency_wiring(root: Path, findings: list[Finding]) -> None:
    build_path = root / "build.gradle"
    mods_path = root / RESOURCES / "META-INF/neoforge.mods.toml"
    if require_file(build_path, root, "PAL_BUILD_FILE_MISSING", findings):
        build = text(build_path)
        for needle, code in (
                (f"def palJarName = '{PAL_JAR_NAME}'", "PAL_BUILD_EXACT_FILENAME"),
                ("if (!palJar.isFile())", "PAL_BUILD_MISSING_GUARD"),
                ("Required Player Animation Library jar is missing", "PAL_BUILD_CLEAR_ERROR"),
                ("implementation files(palJar)", "PAL_BUILD_LOCAL_DEPENDENCY")):
            require_contains(build, needle, code, "build.gradle", findings)
        for forbidden in ("shadowJar", "jarJar", "zipTree(palJar)", "from(palJar)"):
            if forbidden in build:
                findings.append(Finding("PAL_SHADING_FORBIDDEN", forbidden))

    if require_file(mods_path, root, "PAL_MODS_TOML_MISSING", findings):
        mods = text(mods_path)
        for needle, code in (
                ('modId = "player_animation_library"', "PAL_DEPENDENCY_MOD_ID"),
                ('versionRange = "[1.1.4,1.2)"', "PAL_DEPENDENCY_RANGE"),
                ('side = "BOTH"', "PAL_DEPENDENCY_SIDE")):
            require_contains(mods, needle, code, "neoforge.mods.toml", findings)


def validate_no_shaded_pal(root: Path, findings: list[Finding]) -> None:
    source_pal = root / JAVA_ROOT / "com/zigythebird"
    if source_pal.exists():
        findings.append(Finding("PAL_COPIED_SOURCE", relative(root, source_pal)))

    build_libs = root / "build/libs"
    if not build_libs.is_dir():
        return
    for jar in build_libs.glob("myvillage-*.jar"):
        try:
            with zipfile.ZipFile(jar) as archive:
                shaded = next(
                    (name for name in archive.namelist()
                     if name.startswith("com/zigythebird/") or name.endswith(PAL_JAR_NAME)),
                    None)
        except zipfile.BadZipFile:
            continue
        if shaded is not None:
            findings.append(Finding("PAL_SHADED_CONTENT", f"{jar.name}:{shaded}"))


def validate_forbidden_integrations(root: Path, findings: list[Finding]) -> None:
    for path in (root / "build.gradle", root / "gradle.properties"):
        if not path.is_file():
            continue
        lower = text(path).lower()
        for forbidden in ("epicfight", "epic fight", "bettercombat", "better combat", "playeranimator"):
            if forbidden in lower:
                findings.append(Finding("COMBAT_FORBIDDEN_DEPENDENCY", f"{path.name}:{forbidden}"))
    for path, content in java_sources(root, ""):
        if "dev.kosmx.playerAnim" in content or "yesman.epicfight" in content:
            findings.append(Finding("COMBAT_FORBIDDEN_IMPORT", relative(root, path)))
        elif "software.bernie.geckolib" in content and in_package(root, path, CLIENT_COMBAT_PACKAGE):
            findings.append(Finding("COMBAT_FIRST_PERSON_THIRD_PARTY_RIG_FORBIDDEN", relative(root, path)))


# ---------------------------------------------------------------------------------------------
# Combat data (schema, invariants, index) through tools/combat_data.py.
# ---------------------------------------------------------------------------------------------

def validate_combat_data(root: Path, findings: list[Finding]) -> combat_data.CombatData:
    data = combat_data.load(root)
    for issue in data.issues:
        findings.append(Finding(f"COMBAT_DATA_{issue.code}", str(issue)))
    return data


# ---------------------------------------------------------------------------------------------
# Cross-file checks: every style move and every weapon against the resources it needs.
# ---------------------------------------------------------------------------------------------

def load_player_animations(root: Path, namespace: str, findings: list[Finding]) -> dict[str, tuple[str, dict]]:
    """``{animation key: (file name, animation)}`` over ``assets/<ns>/player_animations/*.json``."""
    directory = root / RESOURCES / "assets" / namespace / "player_animations"
    animations: dict[str, tuple[str, dict]] = {}
    for path in sorted(directory.glob("*.json")) if directory.is_dir() else ():
        try:
            document = json.loads(text(path))
        except (json.JSONDecodeError, UnicodeDecodeError) as exc:
            findings.append(Finding("PAL_ANIMATION_JSON", f"{relative(root, path)}: {exc}"))
            continue
        if not isinstance(document, dict) or not isinstance(document.get("animations"), dict):
            findings.append(Finding("PAL_ANIMATION_JSON", f"{relative(root, path)}: no animations object"))
            continue
        if document.get("format_version") != PAL_FORMAT_VERSION:
            findings.append(Finding("PAL_ANIMATION_FORMAT", f"{path.name}: {document.get('format_version')}"))
        for name, animation in document["animations"].items():
            if name in animations:
                findings.append(Finding("COMBAT_ANIMATION_DUPLICATE",
                                        f"{namespace}:{name} in {animations[name][0]} and {path.name}"))
            animations.setdefault(name, (path.name, animation))
    return animations


def rotation_ticks(animation: dict, bone: str) -> list[float]:
    rotation = animation.get("bones", {}).get(bone, {}).get("rotation", {})
    ticks = []
    for timestamp in rotation if isinstance(rotation, dict) else ():
        try:
            ticks.append(float(timestamp) * TICKS_PER_SECOND)
        except (TypeError, ValueError):
            pass
    return ticks


def validate_player_animations(root: Path, data: combat_data.CombatData, findings: list[Finding]) -> None:
    """Every style animation and move has a full-body PAL animation; a move lasts total_ticks / 20 s."""
    by_namespace: dict[str, dict[str, tuple[str, dict]]] = {}

    def lookup(animation_id: str):
        namespace, name = combat_data.split_id(animation_id)
        if namespace not in by_namespace:
            by_namespace[namespace] = load_player_animations(root, namespace, findings)
        entry = by_namespace[namespace].get(name)
        return None if entry is None else entry[1]

    def full_body(animation_id: str, animation) -> bool:
        bones = animation.get("bones") if isinstance(animation, dict) else None
        if not isinstance(bones, dict) or not set(REQUIRED_ANIMATION_BONES).issubset(bones):
            findings.append(Finding("COMBAT_ANIMATION_FULL_BODY", animation_id))
            return False
        return True

    for style_id, style in data.styles.items():
        for role, animation_id in style["animations"].items():
            animation = lookup(animation_id)
            if animation is None:
                findings.append(Finding("COMBAT_ANIMATION_MISSING", f"{style_id} animations.{role}: {animation_id}"))
                continue
            full_body(animation_id, animation)
            if role == "ready_idle" and animation.get("loop") is not True:
                findings.append(Finding("COMBAT_READY_IDLE_LOOP", animation_id))
        for move in style["moves"]:
            move_id = move["id"]
            animation = lookup(move_id)
            if animation is None:
                findings.append(Finding("COMBAT_ANIMATION_MISSING", f"{style_id} move {move_id}"))
                continue
            total = move["total_ticks"]
            expected_length = total / TICKS_PER_SECOND
            length = animation.get("animation_length")
            if not is_number(length) or abs(length - expected_length) > 1.0e-6:
                findings.append(Finding(
                    "COMBAT_ANIMATION_LENGTH",
                    f"{move_id}: expected {expected_length:g} s (total_ticks {total} / 20), actual {length}"))
            if not full_body(move_id, animation):
                continue
            start, end = move["active_ticks"]
            active_key_present = False
            for bone in REQUIRED_ANIMATION_BONES:
                ticks = rotation_ticks(animation, bone)
                active_key_present |= any(start <= tick <= end for tick in ticks)
                if 0.0 not in ticks or not any(abs(tick - total) <= 1.0e-6 for tick in ticks):
                    findings.append(Finding("COMBAT_ANIMATION_RECOVERY", f"{move_id}:{bone}"))
            if not active_key_present:
                findings.append(Finding("COMBAT_ANIMATION_ACTIVE_ALIGNMENT", f"{move_id}: no key in [{start}, {end}]"))


def validate_translations_and_sounds(root: Path, data: combat_data.CombatData, findings: list[Finding]) -> None:
    """Display keys, feedback sound events, their subtitles, and the combat UI strings."""
    sounds_by_namespace: dict[str, dict | None] = {}
    required_keys: dict[str, set[str]] = {}

    def sounds_of(namespace: str):
        if namespace not in sounds_by_namespace:
            path = root / RESOURCES / "assets" / namespace / "sounds.json"
            sounds_by_namespace[namespace] = read_json(path, root, "COMBAT_SOUNDS_JSON", findings)
        return sounds_by_namespace[namespace]

    for style_id, move in data.moves():
        namespace = namespace_of(move["id"])
        required_keys.setdefault(namespace, set()).add(move["display_key"])
        for field in FEEDBACK_SOUND_FIELDS:
            sound_id = move["feedback"].get(field)
            if sound_id is None:
                continue
            sound_namespace, event = combat_data.split_id(sound_id)
            sounds = sounds_of(sound_namespace)
            if sounds is None:
                continue
            entry = sounds.get(event) if isinstance(sounds, dict) else None
            if not isinstance(entry, dict) or not entry.get("sounds") or "subtitle" not in entry:
                findings.append(Finding("COMBAT_SOUND_EVENT", f"{move['id']} feedback.{field}: {sound_id}"))
                continue
            required_keys.setdefault(sound_namespace, set()).add(entry["subtitle"])

    if data.styles:
        required_keys.setdefault("myvillage", set()).update(COMBAT_UI_TRANSLATIONS)
    for namespace, keys in sorted(required_keys.items()):
        lang_dir = root / RESOURCES / "assets" / namespace / "lang"
        lang_files = sorted(lang_dir.glob("*.json")) if lang_dir.is_dir() else []
        if not lang_files:
            findings.append(Finding("COMBAT_TRANSLATIONS", f"no lang files in {relative(root, lang_dir)}"))
        for path in lang_files:
            try:
                language = json.loads(text(path))
            except (json.JSONDecodeError, UnicodeDecodeError) as exc:
                findings.append(Finding("COMBAT_TRANSLATIONS", f"{path.name}: {exc}"))
                continue
            missing = sorted(keys - language.keys())
            if missing:
                findings.append(Finding("COMBAT_TRANSLATIONS", f"{namespace}/{path.name}:{','.join(missing)}"))


def java_sources(root: Path, package: str) -> list[tuple[Path, str]]:
    base = root / JAVA_ROOT / package
    if not base.is_dir():
        return []
    return [(path, text(path)) for path in sorted(base.rglob("*.java"))]


def in_package(root: Path, path: Path, package: str) -> bool:
    try:
        path.relative_to(root / JAVA_ROOT / package)
    except ValueError:
        return False
    return True


def resource_id(value, default_namespace: str = "minecraft") -> str | None:
    """A model or texture reference as ``ns:path`` (vanilla's default namespace when it has none).

    ``None`` for a texture variable (``#name``) or a ``builtin/`` parent; a malformed value is
    returned unchanged so that resolving it fails with a finding."""
    if not isinstance(value, str) or value.startswith("#") or value.startswith("builtin/"):
        return None
    return value if ":" in value else f"{default_namespace}:{value}"


def is_external(reference: str) -> bool:
    return reference.split(":", 1)[0] in EXTERNAL_NAMESPACES


def model_file(root: Path, model_id: str) -> Path:
    namespace, path = combat_data.split_id(model_id)
    return root / RESOURCES / "assets" / namespace / "models" / f"{path}.json"


def texture_file(root: Path, texture_id: str) -> Path:
    namespace, path = combat_data.split_id(texture_id)
    return root / RESOURCES / "assets" / namespace / "textures" / f"{path}.png"


def item_model_id(item_id: str) -> str:
    namespace, name = combat_data.split_id(item_id)
    return f"{namespace}:item/{name}"


def model_parts(model: dict) -> list[dict]:
    """The model and the sub-models a NeoForge ``separate_transforms`` model carries inline."""
    parts = [model]
    if isinstance(model.get("base"), dict):
        parts.append(model["base"])
    if isinstance(model.get("perspectives"), dict):
        parts.extend(part for part in model["perspectives"].values() if isinstance(part, dict))
    return parts


@dataclass
class ModelGraph:
    """The mod models and textures an item model needs: its parents, the ``base`` and
    ``perspectives`` sub-models, and every texture they reference (vanilla ones excepted)."""

    models: dict[str, Path]
    textures: dict[str, Path]
    documents: dict[str, dict]
    problems: list[tuple[str, str]]

    def files(self) -> list[Path]:
        return [*self.models.values(), *self.textures.values()]


def model_graph(root: Path, start: str, start_code: str = "COMBAT_WEAPON_MODEL_MISSING") -> ModelGraph:
    graph = ModelGraph({}, {}, {}, [])
    queue: list[tuple[str, str | None]] = [(start, None)]
    while queue:
        model_id, referrer = queue.pop(0)
        if model_id in graph.models:
            continue
        code = start_code if referrer is None else "COMBAT_WEAPON_MODEL_MISSING"
        via = "" if referrer is None else f"{referrer} -> "
        try:
            path = model_file(root, model_id)
        except ValueError as exc:
            graph.problems.append((code, f"{via}{exc}"))
            continue
        graph.models[model_id] = path
        try:
            document = json.loads(text(path))
        except FileNotFoundError:
            graph.problems.append((code, f"{via}{model_id}: {relative(root, path)}"))
            continue
        except (OSError, json.JSONDecodeError, UnicodeDecodeError) as exc:
            graph.problems.append((code, f"{via}{model_id}: {relative(root, path)}: {exc}"))
            continue
        if not isinstance(document, dict):
            graph.problems.append((code, f"{via}{model_id}: not an object"))
            continue
        graph.documents[model_id] = document
        for part in model_parts(document):
            parent = resource_id(part.get("parent"))
            if parent is not None and not is_external(parent):
                queue.append((parent, model_id))
            textures = part.get("textures")
            for texture in (textures.values() if isinstance(textures, dict) else ()):
                texture_id = resource_id(texture)
                if texture_id is None or is_external(texture_id) or texture_id in graph.textures:
                    continue
                try:
                    texture_path = texture_file(root, texture_id)
                except ValueError as exc:
                    graph.problems.append(("COMBAT_WEAPON_TEXTURE_MISSING", f"{model_id}: {exc}"))
                    continue
                graph.textures[texture_id] = texture_path
                if not texture_path.is_file():
                    graph.problems.append(("COMBAT_WEAPON_TEXTURE_MISSING",
                                           f"{model_id} -> {texture_id}: {relative(root, texture_path)}"))
                elif not texture_path.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n":
                    graph.problems.append(("COMBAT_WEAPON_TEXTURE_MISSING",
                                           f"{model_id} -> {texture_id}: not a PNG"))
    return graph


def in_hand_models(graph: ModelGraph, start: str) -> list[str]:
    """The models the item draws in the hand: the parent chain, through ``base`` for a
    ``separate_transforms`` model (whose ``perspectives`` only replace other contexts)."""
    chain: list[str] = []
    current: str | None = start
    while current is not None and current not in chain:
        chain.append(current)
        document = graph.documents.get(current)
        if document is None:
            break
        part = document["base"] if (document.get("loader") == SEPARATE_TRANSFORMS
                                     and isinstance(document.get("base"), dict)) else document
        current = resource_id(part.get("parent"))
    return chain


def read_geometry_contracts(root: Path, data: combat_data.CombatData) -> dict[str, dict]:
    """Every weapon's geometry contract that parses to an object (findings come from
    validate_geometry_contracts)."""
    contracts: dict[str, dict] = {}
    for geometry_id in sorted({weapon["geometry"] for weapon in data.weapons.values()}):
        try:
            document = json.loads(text(combat_data.asset_file(root, geometry_id)))
        except (OSError, ValueError):
            continue
        if isinstance(document, dict):
            contracts[geometry_id] = document
    return contracts


def contract_model_id(contract: dict) -> str | None:
    model_id = resource_id(contract.get("model"))
    try:
        combat_data.split_id(model_id)
    except ValueError:
        return None
    return model_id


def weapon_resource_files(root: Path, data: combat_data.CombatData) -> list[Path]:
    """Item models, 3D models, and textures of every weapon (present or not)."""
    contracts = read_geometry_contracts(root, data)
    files: list[Path] = []
    for weapon in data.weapons.values():
        files += model_graph(root, item_model_id(weapon["item"])).files()
        model_id = contract_model_id(contracts.get(weapon["geometry"], {}))
        if model_id is not None:
            files += model_graph(root, model_id).files()
    return files


SCHOOL_DIR = "src/main/resources/data/myvillage/myvillage/school"


def school_weapon_families(root: Path, findings: list[Finding]) -> set[str]:
    """The ``weapon_family`` of every weapon school (``data/myvillage/myvillage/school/*.json``)."""
    families: set[str] = set()
    for path in sorted((root / SCHOOL_DIR).glob("*.json")):
        school = read_json(path, root, "COMBAT_WEAPON_FAMILY", findings)
        if isinstance(school, dict) and isinstance(school.get("weapon_family"), str):
            families.add(school["weapon_family"])
    return families


def validate_weapon_families(root: Path, data: combat_data.CombatData, findings: list[Finding]) -> None:
    """A weapon's optional ``family`` names the ``weapon_family`` of a school (technique-system brief
    2.1), so a school's techniques can later ask for a weapon of their family without naming an item.
    No runtime reads the field yet."""
    families = school_weapon_families(root, findings)
    for weapon_id, weapon in data.weapons.items():
        family = weapon.get("family")
        if family is not None and family not in families:
            findings.append(Finding("COMBAT_WEAPON_FAMILY",
                                    f"{weapon_id}: family {family!r} is no school's weapon_family "
                                    f"({', '.join(sorted(families)) or 'no schools'}) in {SCHOOL_DIR}"))


def validate_paired_weapons(root: Path, data: combat_data.CombatData, contracts: dict[str, dict],
                            findings: list[Finding]) -> None:
    """A ``paired`` weapon (0.39.1, one item worn on both hands) is drawn mirrored on the empty off
    hand, so its first-person rig must keep that hand free (``rig.off_hand.free``) and its contract
    must not put the off hand on the weapon (no ``off_hand_grip_center``)."""
    for weapon_id, weapon in data.weapons.items():
        if weapon.get("paired") is not True:
            continue
        rig = read_json(combat_data.asset_file(root, weapon["first_person_rig"]), root,
                        "COMBAT_WEAPON_PAIRED", findings)
        settings = rig.get("rig") if isinstance(rig, dict) else None
        block = settings.get("off_hand") if isinstance(settings, dict) else None
        if not (isinstance(block, dict) and block.get("free") is True):
            findings.append(Finding("COMBAT_WEAPON_PAIRED",
                                    f"{weapon_id}: paired, but {weapon['first_person_rig']} has no free "
                                    f"rig.off_hand to wear the second one"))
        contract = contracts.get(weapon["geometry"])
        if contract is not None and contract.get("off_hand_grip_center") is not None:
            findings.append(Finding("COMBAT_WEAPON_PAIRED",
                                    f"{weapon_id}: paired, but {weapon['geometry']} holds the off hand on "
                                    f"the weapon (off_hand_grip_center)"))


def validate_weapon_items(root: Path, data: combat_data.CombatData, findings: list[Finding]) -> None:
    """A weapon's item is registered in Java under its id and has an item model."""
    sources = [content for _, content in java_sources(root, "")]
    for weapon_id, weapon in data.weapons.items():
        namespace, name = combat_data.split_id(weapon["item"])
        registration = re.compile(rf'\.register(?:Item)?\(\s*"{re.escape(name)}"')
        if not any(registration.search(content) for content in sources):
            findings.append(Finding("COMBAT_WEAPON_ITEM_UNREGISTERED", f"{weapon_id}: {weapon['item']}"))


def validate_weapon_models(root: Path, data: combat_data.CombatData, contracts: dict[str, dict],
                           findings: list[Finding]) -> None:
    """Every weapon's item model, the models it wraps, and their textures exist; the item draws the
    3D model its geometry contract describes, and that model has geometry and hand transforms."""
    for weapon_id, weapon in data.weapons.items():
        start = item_model_id(weapon["item"])
        graph = model_graph(root, start, "COMBAT_WEAPON_ITEM_MODEL")
        for code, detail in graph.problems:
            findings.append(Finding(code, f"{weapon_id}: {detail}"))
        contract = contracts.get(weapon["geometry"])
        if contract is None:
            continue  # reported by validate_geometry_contracts
        model_id = contract_model_id(contract)
        if model_id is None:
            continue  # reported by validate_geometry_contracts
        if model_id not in in_hand_models(graph, start):
            findings.append(Finding(
                "COMBAT_WEAPON_MODEL_3D",
                f"{weapon_id}: item model {start} does not draw {model_id} in the hand, "
                f"the model its geometry contract {weapon['geometry']} describes"))
            own = model_graph(root, model_id)
            for code, detail in own.problems:
                findings.append(Finding(code, f"{weapon_id}: {detail}"))
            graph = own
        model = graph.documents.get(model_id)
        if model is None:
            continue  # missing or unreadable, reported above
        display = model.get("display")
        missing = [context for context in WEAPON_HAND_DISPLAYS
                   if not isinstance(display, dict) or not isinstance(display.get(context), dict)]
        if not isinstance(model.get("elements"), list) or not model["elements"] or missing:
            findings.append(Finding(
                "COMBAT_WEAPON_MODEL_3D",
                f"{weapon_id}: {model_id} needs elements and display "
                f"{', '.join(WEAPON_HAND_DISPLAYS)} (missing {', '.join(missing) or 'elements'})"))


def validate_first_person_rigs(root: Path, data: combat_data.CombatData, contracts: dict[str, dict],
                               findings: list[Finding]) -> None:
    """Each weapon's rig poses every move of its style, with strike windows over the active ticks."""
    checked: set[tuple[str, str]] = set()
    rigs: dict[str, object] = {}
    for weapon_id, weapon in data.weapons.items():
        rig_id = weapon["first_person_rig"]
        path = combat_data.asset_file(root, rig_id)
        if rig_id not in rigs:
            rigs[rig_id] = read_json(path, root, "COMBAT_FIRST_PERSON_RIG_MISSING", findings)
        rig = rigs[rig_id]
        if rig is None:
            continue
        style = data.styles.get(weapon["style"])
        if style is not None and (rig_id, weapon["style"]) not in checked:
            checked.add((rig_id, weapon["style"]))
            validate_rig(rig, relative(root, path), style, findings)
        # A rig that draws the off hand on the shaft needs a two-handed weapon: a contract without
        # off_hand_grip_center describes a one-handed weapon.
        settings = rig.get("rig") if isinstance(rig, dict) else None
        contract = contracts.get(weapon["geometry"])
        block = settings.get("off_hand") if isinstance(settings, dict) else None
        free = isinstance(block, dict) and block.get("free") is True
        if (block is not None and not free and contract is not None
                and contract.get("off_hand_grip_center") is None):
            findings.append(Finding(
                "COMBAT_FIRST_PERSON_RIG_OFF_HAND",
                f"{weapon_id}: {relative(root, path)} has rig.off_hand but {weapon['geometry']} "
                f"has no off_hand_grip_center"))
        for problem in off_hand_problems(rig, contract):
            findings.append(Finding("COMBAT_FIRST_PERSON_RIG_OFF_HAND",
                                    f"{weapon_id}: {relative(root, path)}: {problem}"))


OFF_HAND_POSE_FIELDS = ("off_hand_slide", "off_hand_roll", "off_hand_elbow", "off_hand_hold")
# FirstPersonSwing.Arm.MAXIMUM_GRIP_DIAGONAL: 90 lays a worn weapon (the gauntlet) along the hand.
MAX_GRIP_DIAGONAL = 90
# FirstPersonArmIk.MINIMUM_REACH_FRACTION..REACH_FRACTION: a released off hand's rest stays reachable.
OFF_HAND_REST_REACH = (0.30, 0.97)


def off_hand_problems(rig, contract) -> list[str]:
    """What FirstPersonSwing would reject in the rig.off_hand block and the per-key off-hand fields.

    The key fields are numbers and ``off_hand_hold`` is within 0..1 in any rig (Java parses them
    either way), a key's ``off_hand_rest`` a non-zero [x, y, z] and ``off_hand_reach`` within the
    reach clamp 0.3..0.97; with the block, ``shoulder_offset`` is three numbers, ``grip_diagonal`` 0..90,
    ``thickness`` 0.2..1.2, ``upper_arm`` and ``forearm`` 0.1..0.6 (as ``rig.arm``),
    ``rest_direction`` a non-zero [x, y, z], ``rest_reach`` within the solver's reach clamp
    0.3..0.97, and each key's ``off_hand_slide`` (inherited from the previous key) keeps the
    contract's ``off_hand_grip_center`` on the handle. ``free`` is true or false; a free off hand
    (bare, never on the weapon) needs no ``off_hand_grip_center``, and ``off_hand_slide`` and
    ``off_hand_hold`` keyed on it are reported because they have no effect. Unknown fields are
    ignored, as everywhere in the rig.
    """
    if not isinstance(rig, dict) or not isinstance(rig.get("rig"), dict):
        return []
    problems = []
    poses = [("neutral", rig.get("neutral"))]
    moves = rig.get("moves")
    for move_id, move in (moves.items() if isinstance(moves, dict) else ()):
        keys = move.get("keys") if isinstance(move, dict) else None
        for index, key in enumerate(keys if isinstance(keys, list) else ()):
            poses.append((f"{move_id} key {index}", key))
    for where, pose in poses:
        if not isinstance(pose, dict):
            continue
        bad = [field for field in OFF_HAND_POSE_FIELDS if field in pose and not is_number(pose[field])]
        if bad:
            problems.append(f"{where}: {', '.join(bad)} must be numbers")
        elif "off_hand_hold" in pose and not 0 <= pose["off_hand_hold"] <= 1:
            problems.append(f"{where}: off_hand_hold {pose['off_hand_hold']} must be within 0..1")
        rest = pose.get("off_hand_rest")
        if rest is not None and (not isinstance(rest, list) or len(rest) != 3 or not all(is_number(v) for v in rest)
                                 or math.sqrt(sum(v * v for v in rest)) < 1.0e-3):
            problems.append(f"{where}: off_hand_rest {rest!r} must be a non-zero [x, y, z]")
        reach = pose.get("off_hand_reach")
        if reach is not None and (not is_number(reach)
                                  or not OFF_HAND_REST_REACH[0] <= reach <= OFF_HAND_REST_REACH[1]):
            problems.append(f"{where}: off_hand_reach {reach!r} must be within "
                            f"{OFF_HAND_REST_REACH[0]:g}..{OFF_HAND_REST_REACH[1]:g}")

    block = rig["rig"].get("off_hand")
    if block is None:
        return problems
    if not isinstance(block, dict):
        return problems + ["rig.off_hand must be an object"]
    offset = block.get("shoulder_offset", [0, 0, 0])
    if not isinstance(offset, list) or len(offset) != 3 or not all(is_number(v) for v in offset):
        problems.append(f"rig.off_hand.shoulder_offset {offset!r} must be [x, y, z]")
    diagonal = block.get("grip_diagonal", 0)
    if not is_number(diagonal) or not 0 <= diagonal <= MAX_GRIP_DIAGONAL:
        problems.append(f"rig.off_hand.grip_diagonal {diagonal!r} must be within 0..{MAX_GRIP_DIAGONAL}")
    free = block.get("free", False)
    if not isinstance(free, bool):
        problems.append(f"rig.off_hand.free {free!r} must be true or false")
    thickness = block.get("thickness", 0.5)
    if not is_number(thickness) or not 0.2 <= thickness <= 1.2:
        problems.append(f"rig.off_hand.thickness {thickness!r} must be within 0.2..1.2")
    for bone in ("upper_arm", "forearm"):
        length = block.get(bone, 0.33)
        if not is_number(length) or not 0.1 <= length <= 0.6:
            problems.append(f"rig.off_hand.{bone} {length!r} must be within 0.1..0.6")
    rest = block.get("rest_direction", [0.15, -1.0, -0.2])
    if (not isinstance(rest, list) or len(rest) != 3 or not all(is_number(v) for v in rest)
            or math.sqrt(sum(v * v for v in rest)) < 1.0e-3):
        problems.append(f"rig.off_hand.rest_direction {rest!r} must be a non-zero [x, y, z]")
    reach = block.get("rest_reach", 0.9)
    if not is_number(reach) or not OFF_HAND_REST_REACH[0] <= reach <= OFF_HAND_REST_REACH[1]:
        problems.append(f"rig.off_hand.rest_reach {reach!r} must be within "
                        f"{OFF_HAND_REST_REACH[0]:g}..{OFF_HAND_REST_REACH[1]:g}")
    if free is True:
        for where, pose in poses:
            unused = [field for field in ("off_hand_slide", "off_hand_hold") if isinstance(pose, dict) and field in pose]
            if unused:
                problems.append(f"{where}: {', '.join(unused)} has no effect on a free off hand")
        return problems
    point = contract.get("off_hand_grip_center") if isinstance(contract, dict) else None
    handle = contract.get("handle", {}).get("y") if isinstance(contract, dict) else None
    if not (isinstance(point, list) and len(point) == 3 and is_number(point[1])
            and isinstance(handle, list) and len(handle) == 2 and all(is_number(v) for v in handle)):
        return problems  # no usable off-hand point: reported above or by the contract check
    neutral = rig.get("neutral") if isinstance(rig.get("neutral"), dict) else {}
    neutral_slide = neutral.get("off_hand_slide", 0.0)
    slides = [("neutral", neutral_slide)]
    for move_id, move in (moves.items() if isinstance(moves, dict) else ()):
        keys = move.get("keys") if isinstance(move, dict) else None
        slide = neutral_slide  # each move's keys inherit from the neutral hold, then from the key before
        for index, key in enumerate(keys if isinstance(keys, list) else ()):
            if not isinstance(key, dict):
                continue
            if key.get("pose") == "neutral":
                slide = neutral_slide
            else:
                slide = key.get("off_hand_slide", slide)
            slides.append((f"{move_id} key {index}", slide))
    for where, slide in slides:
        if is_number(slide) and not handle[0] <= point[1] + slide <= handle[1]:
            problems.append(f"{where}: off_hand_slide {slide:g} puts the off hand at y={point[1] + slide:g}, "
                            f"off the handle {handle}")
    return problems


def validate_rig(rig, name: str, style: dict, findings: list[Finding]) -> None:
    try:
        moves = rig["moves"]
        rig_settings = rig["rig"]
        rig_settings["shoulder"]
        neutral = rig["neutral"]
        if not isinstance(moves, dict) or not isinstance(neutral, dict):
            raise TypeError("moves and neutral must be objects")
    except (KeyError, TypeError) as exc:
        findings.append(Finding("COMBAT_FIRST_PERSON_RIG_JSON", f"{name}: {exc}"))
        return
    for problem in combat_data.rig_format_problems(rig):
        findings.append(Finding("COMBAT_FIRST_PERSON_RIG_JSON", f"{name}: {problem}"))
    scale = rig_settings.get("weapon_scale", 0.6)
    arm = rig_settings.get("arm", {})
    if (not is_number(scale) or not 0.2 <= scale <= 1.5 or not isinstance(arm, dict)
            or any(not is_number(arm.get(field, 0))
                   for field in ("upper_arm", "forearm", "thickness", "grip_diagonal", "follow_through"))
            or not 0.2 <= arm.get("thickness", 0.5) <= 1.2
            or not 0 <= arm.get("grip_diagonal", 40) <= MAX_GRIP_DIAGONAL):
        findings.append(Finding("COMBAT_FIRST_PERSON_RIG_ARM", f"{name}: rig.weapon_scale/rig.arm"))
    poses = [neutral] + [key for move in moves.values() if isinstance(move, dict)
                         for key in move.get("keys", []) if isinstance(key, dict)]
    if any(not is_number(pose.get(field, 0)) for pose in poses for field in ("grip_roll", "elbow")):
        findings.append(Finding("COMBAT_FIRST_PERSON_RIG_ARM", f"{name}: grip_roll/elbow must be numbers"))

    style_moves = {move["id"]: move for move in style["moves"]}
    missing = sorted(set(style_moves) - set(moves))
    unknown = sorted(set(moves) - set(style_moves))
    if missing or unknown:
        findings.append(Finding(
            "COMBAT_FIRST_PERSON_RIG_MOVES",
            f"{name} vs {style['id']}: missing {','.join(missing) or '-'}; unknown {','.join(unknown) or '-'}"))
    for move_id, definition in style_moves.items():
        move = moves.get(move_id)
        if not isinstance(move, dict):
            continue
        total = definition["total_ticks"]
        start, end = definition["active_ticks"]
        keys = move.get("keys", [])
        ticks = [key.get("tick") if isinstance(key, dict) else None for key in keys]
        if (not isinstance(keys, list) or len(keys) < 3 or not all(is_number(t) for t in ticks)
                or ticks[0] != 0 or ticks[-1] != total
                or any(later <= earlier for earlier, later in zip(ticks, ticks[1:]))):
            findings.append(Finding("COMBAT_FIRST_PERSON_RIG_KEYS", f"{move_id}: keys must rise from 0 to {total}"))
        elif keys[0].get("pose") != "neutral" or keys[-1].get("pose") != "neutral":
            findings.append(Finding("COMBAT_FIRST_PERSON_RIG_NEUTRAL", move_id))
        strike = move.get("strike")
        if (not isinstance(strike, list) or len(strike) != 2 or not all(is_number(v) for v in strike)
                or not strike[0] < strike[1] or strike[0] > start or strike[1] < end
                or strike[1] > total or strike[1] - strike[0] > MAX_STRIKE_TICKS):
            findings.append(Finding(
                "COMBAT_FIRST_PERSON_RIG_STRIKE",
                f"{move_id}: strike {strike} must cover active ticks [{start}, {end}] "
                f"within {MAX_STRIKE_TICKS:g} ticks and end by {total}"))
            continue
        contact = move.get("contact", strike[0])
        if not is_number(contact) or not strike[0] <= contact <= strike[1]:
            findings.append(Finding("COMBAT_FIRST_PERSON_RIG_CONTACT",
                                    f"{move_id}: contact {contact} outside strike {strike}"))


def validate_geometry_contracts(root: Path, data: combat_data.CombatData, findings: list[Finding]) -> None:
    """Each weapon's geometry contract is format 2 and carries the grip, collar, head, axes, model,
    and generator fields in order along +Y, a valid off-hand grip when it has one, and a valid
    trail span when it names one."""
    for geometry_id in sorted({weapon["geometry"] for weapon in data.weapons.values()}):
        path = combat_data.asset_file(root, geometry_id)
        geometry = read_json(path, root, "COMBAT_GEOMETRY_CONTRACT", findings)
        if geometry is None:
            continue
        name = relative(root, path)
        if not isinstance(geometry, dict):
            findings.append(Finding("COMBAT_GEOMETRY_CONTRACT", f"{name}: not an object"))
            continue
        format_problems = combat_data.geometry_format_problems(geometry)
        if format_problems:
            findings.append(Finding("COMBAT_GEOMETRY_CONTRACT", f"{name}: {'; '.join(format_problems)}"))
            continue
        missing = [field for field in GEOMETRY_FIELDS if field not in geometry]
        if missing or geometry.get("units") != "model_pixels":
            findings.append(Finding("COMBAT_GEOMETRY_CONTRACT", f"{name}: missing {','.join(missing or ['units'])}"))
            continue
        try:
            grip_y = float(geometry["grip_center"][1])
            handle = [float(v) for v in geometry["handle"]["y"]]
            collar = [float(v) for v in geometry["collar"]["y"]]
            butt = [float(v) for v in geometry["butt"]["y"]]
            base_y, tip_y = float(geometry["head_base"][1]), float(geometry["head_tip"][1])
        except (KeyError, IndexError, TypeError, ValueError) as exc:
            findings.append(Finding("COMBAT_GEOMETRY_CONTRACT", f"{name}: malformed {exc}"))
            continue
        axes = geometry.get("axes", {})
        if (not butt[1] <= handle[0] < grip_y < handle[1] <= collar[0] < collar[1] <= base_y < tip_y
                or not isinstance(axes, dict)
                or (axes.get("length"), axes.get("flat_normal"), axes.get("edge")) != ("+y", "x", "z")):
            findings.append(Finding("COMBAT_GEOMETRY_CONTRACT", f"{name}: butt<handle<collar<head, axes +y/x/z"))
            continue
        generator = geometry.get("generator")
        if not isinstance(generator, str) or not GENERATOR_SCRIPT.match(generator):
            findings.append(Finding("COMBAT_GEOMETRY_CONTRACT",
                                    f"{name}: generator {generator!r} must name a tools/<script>.py"))
        if contract_model_id(geometry) is None:
            findings.append(Finding("COMBAT_GEOMETRY_CONTRACT",
                                    f"{name}: model {geometry.get('model')!r} must be a model id"))
        if "off_hand_grip_center" in geometry:
            problem = off_hand_problem(root, geometry, grip_y, handle)
            if problem:
                findings.append(Finding("COMBAT_GEOMETRY_OFF_HAND_GRIP", f"{name}: {problem}"))
        if "trail" in geometry:
            problem = trail_problem(geometry, butt[0], tip_y)
            if problem:
                findings.append(Finding("COMBAT_GEOMETRY_TRAIL", f"{name}: {problem}"))


def trail_problem(geometry: dict, bottom_y: float, tip_y: float) -> str | None:
    """Why the optional ``trail`` span (the part of the weapon that draws the trails; without it
    ``head_base``..``head_tip``) is unusable, or None.

    It is ``{"base": [x, y, z], "tip": [x, y, z]}`` on the weapon axis, base below tip, from the
    butt's bottom up to the head tip at most (WeaponGeometry.parse checks the same)."""
    trail = geometry["trail"]
    if not isinstance(trail, dict) or set(trail) != {"base", "tip"}:
        return f"trail must be an object with exactly base and tip, got {trail!r}"
    points = []
    for key in ("base", "tip"):
        point = trail[key]
        if not isinstance(point, list) or len(point) != 3 or not all(is_number(v) for v in point):
            return f"trail.{key} {point!r} must be [x, y, z]"
        points.append(point)
    axes = geometry["axes"]
    grip = geometry["grip_center"]
    axis_x, axis_z = axes.get("center_x", grip[0]), axes.get("center_z", grip[2])
    if not is_number(axis_x) or not is_number(axis_z):
        return "the weapon axis (axes.center_x/center_z or grip_center x/z) must be numbers"
    for key, point in zip(("base", "tip"), points):
        if abs(point[0] - axis_x) > AXIS_TOLERANCE_PX or abs(point[2] - axis_z) > AXIS_TOLERANCE_PX:
            return f"trail.{key} {point} is off the weapon axis x={axis_x:g}, z={axis_z:g}"
    base_y, trail_tip_y = points[0][1], points[1][1]
    if not base_y < trail_tip_y:
        return f"trail base y={base_y:g} must lie below its tip y={trail_tip_y:g}"
    if base_y < bottom_y or trail_tip_y > tip_y:
        return f"trail y {base_y:g}..{trail_tip_y:g} must lie on the weapon y {bottom_y:g}..{tip_y:g}"
    return None


def model_third_person_scale(root: Path, geometry: dict) -> float | None:
    """Length scale along the weapon axis (+Y) of the contract model's thirdperson_righthand transform,
    as CombatWorldTrails.thirdPersonScale measures it (|scale y|; rotation keeps lengths), or
    None when the model or the transform is unreadable."""
    model_id = contract_model_id(geometry)
    try:
        model = json.loads(text(model_file(root, model_id)))
        scale = abs(float(model["display"]["thirdperson_righthand"]["scale"][1]))
    except (OSError, ValueError, KeyError, IndexError, TypeError):
        return None
    return scale if scale > 0 and math.isfinite(scale) else None


def third_person_scale(root: Path, geometry: dict) -> float:
    """The y scale of the contract model's thirdperson_righthand transform (1 when unreadable; the
    model check reports an unreadable model)."""
    return model_third_person_scale(root, geometry) or 1.0


def off_hand_problem(root: Path, geometry: dict, grip_y: float, handle: list[float]) -> str | None:
    """Why ``off_hand_grip_center`` is not a second hand on the shaft, or None.

    It lies on the weapon axis, inside the handle, ahead of (above) the main grip, and at least
    one fist width from it so the two fists do not overlap."""
    point = geometry["off_hand_grip_center"]
    if (not isinstance(point, list) or len(point) != 3 or not all(is_number(v) for v in point)):
        return f"off_hand_grip_center {point!r} must be [x, y, z]"
    axes = geometry["axes"]
    grip = geometry["grip_center"]
    axis_x, axis_z = axes.get("center_x", grip[0]), axes.get("center_z", grip[2])
    if not is_number(axis_x) or not is_number(axis_z):
        return "the weapon axis (axes.center_x/center_z or grip_center x/z) must be numbers"
    x, y, z = point
    if abs(x - axis_x) > AXIS_TOLERANCE_PX or abs(z - axis_z) > AXIS_TOLERANCE_PX:
        return f"off_hand_grip_center {point} is off the weapon axis x={axis_x:g}, z={axis_z:g}"
    if not handle[0] <= y <= handle[1]:
        return f"off_hand_grip_center y={y:g} is outside the handle {handle}"
    if y <= grip_y:
        return f"off_hand_grip_center y={y:g} must be ahead of grip_center y={grip_y:g}"
    gap = FIST_WIDTH_BLOCKS * 16.0 / third_person_scale(root, geometry)
    if y - grip_y < gap:
        return (f"off_hand_grip_center y={y:g} is {y - grip_y:g} px from grip_center y={grip_y:g}; "
                f"two fists need {gap:.2f} px at the model's third-person scale")
    return None


# The world trail other players see (client/combat/CombatWorldTrails.java), mirrored here rather
# than imported from tools/gen_sword_pal_anims.py, whose port (trail_tip_radius) needs a pose table
# and measures grip to tip along y only; the Java code measures the 3D distance.
TRAIL_PIVOT_HEIGHT = 1.3          # CombatWorldTrails.PIVOT_HEIGHT: the attacker's centre, 1.3 up
TRAIL_ARM_REACH = 0.705           # CombatWorldTrails.ARM_REACH
TRAIL_FRAME_TICKS = 1.2 / 24      # CombatWorldTrails.TRAIL_TICKS / SEGMENTS: one drawn frame
TRAIL_TOLERANCE = 1.0e-6


def trail_tip_radius(root: Path, geometry: dict) -> float | None:
    """CombatWorldTrails.trailSize(...).tipRadius(): ARM_REACH plus grip centre to trail tip (the
    contract's trail.tip, else head_tip) in model px at the model's third-person scale.  None when
    the contract or model cannot give one (the game then draws a fallback; other checks report it)."""
    scale = model_third_person_scale(root, geometry)
    try:
        trail = geometry.get("trail")
        tip = trail["tip"] if isinstance(trail, dict) else geometry["head_tip"]
        grip = geometry["grip_center"]
        reach = math.dist([float(v) for v in tip], [float(v) for v in grip])
    except (KeyError, TypeError, ValueError):
        return None
    if scale is None or len(tip) != 3 or len(grip) != 3:
        return None
    return TRAIL_ARM_REACH + reach * scale / 16.0


def far_end_reach(end) -> float:
    """Distance of a sample's far end from the trail pivot."""
    return math.sqrt(end[0] ** 2 + (end[1] - TRAIL_PIVOT_HEIGHT) ** 2 + end[2] ** 2)


def trail_sample_times(samples: list[dict]) -> list[float]:
    """CombatWorldTrails.sampleTime: the n samples of tick t sit at t + (i + 0.5) / n - 0.5."""
    times = []
    for index, sample in enumerate(samples):
        same = [other for other in range(len(samples)) if samples[other]["tick"] == sample["tick"]]
        times.append(sample["tick"] + (same.index(index) + 0.5) / len(same) - 0.5)
    return times


def trail_far_end(samples: list[dict], times: list[float], tick: float) -> tuple[list[float], int, int]:
    """CombatWorldTrails.blade's far end at ``tick`` (polar interpolation between the neighbouring
    samples, clamped to the first and last), with the indexes of those two samples."""
    before, after = 0, len(samples) - 1
    for index, time in enumerate(times):
        if time <= tick:
            before = index
        if time >= tick:
            after = index
            break
    first, second = samples[before]["end"], samples[after]["end"]
    if before == after or not times[after] > times[before]:
        return list(first), before, after
    progress = max(0.0, min(1.0, (tick - times[before]) / (times[after] - times[before])))
    first_angle, second_angle = math.atan2(first[0], first[2]), math.atan2(second[0], second[2])
    angle = first_angle + (second_angle - first_angle) * progress
    first_radius = math.hypot(first[0], first[2])
    radius = first_radius + (math.hypot(second[0], second[2]) - first_radius) * progress
    return ([math.sin(angle) * radius, first[1] + (second[1] - first[1]) * progress, math.cos(angle) * radius],
            before, after)


def cut_reach_problem(move: dict, radius: float) -> str | None:
    """Why a cut's trail head leaves the weapon's tip-radius sphere, or None.

    The drawn head sits at min(far-end distance, tip radius) from the pivot, so a far end shorter
    than the radius pulls the head inward and bends the trail (CombatWorldTrailsTest
    .shippedSpearCutsKeepTheTrailHeadOnTheTipRadius).  Checked on every sample, then on every
    drawn frame from half a tick before the active window to half a tick after it.  The samples are
    the ones the trail draws: the move's ``trail.samples`` when it has them, else its hit samples."""
    samples = combat_data.expand_trail_samples(move)
    owner = "trail" if move.get("trail") is not None else "hitbox"
    spec = move[owner]["samples"]
    source = (f"explicit {owner}.samples" if isinstance(spec, list)
              else f"the {owner}.samples {spec['generator']} generator's samples")
    short = [(far_end_reach(sample["end"]), index) for index, sample in enumerate(samples)
             if far_end_reach(sample["end"]) < radius - TRAIL_TOLERANCE]
    if short:
        reach, index = min(short)
        return (f"{source}[{index}] (tick {samples[index]['tick']}) has its far end {reach:.3f} blocks from "
                f"the trail pivot (0, {TRAIL_PIVOT_HEIGHT:g}, 0), inside the weapon's trail tip radius "
                f"{radius:.3f}; {len(short)} sample(s) fall short.  Move each such far end out along its "
                f"direction from the pivot to at least {radius:.3f} blocks")
    times = trail_sample_times(samples)
    start, end = move["active_ticks"]
    frames = int(math.floor((end - start + 1.0) / TRAIL_FRAME_TICKS + 1.0e-6)) + 1
    for frame in range(frames):
        tick = start - 0.5 + frame * TRAIL_FRAME_TICKS
        far, before, after = trail_far_end(samples, times, tick)
        reach = far_end_reach(far)
        if reach < radius - TRAIL_TOLERANCE:
            return (f"between {source}[{before}] and [{after}] the drawn far end comes to {reach:.3f} blocks "
                    f"from the trail pivot near tick {tick:.2f}, inside the weapon's trail tip radius "
                    f"{radius:.3f}, although both samples reach it.  Push those far ends further out, or "
                    f"add samples between them, so the interpolated far end stays at least {radius:.3f}")
    return None


def validate_trail_reach(root: Path, data: combat_data.CombatData, contracts: dict[str, dict],
                         findings: list[Finding]) -> None:
    """For each weapon and each cut of its style, every drawn trail frame keeps its head on the
    weapon's tip radius.  Per weapon, because weapons that share a style have their own radius."""
    for weapon_id, weapon in data.weapons.items():
        style = data.styles.get(weapon["style"])
        contract = contracts.get(weapon["geometry"])
        if style is None or contract is None:
            continue
        if "trail" in contract and not contract_problem_free_trail(contract):
            continue  # COMBAT_GEOMETRY_TRAIL reports it; the game draws the fallback size
        radius = trail_tip_radius(root, contract)
        if radius is None:
            continue
        for move in style["moves"]:
            if move["kind"] != "cut":
                continue  # a thrust draws a streak along the blade, not a swept band
            problem = cut_reach_problem(move, radius)
            if problem:
                findings.append(Finding("COMBAT_TRAIL_CUT_REACH", f"{weapon_id} {move['id']}: {problem}"))


def contract_problem_free_trail(contract: dict) -> bool:
    """Whether the contract's optional trail span is usable (see trail_problem)."""
    try:
        butt_y = float(contract["butt"]["y"][0])
        tip_y = float(contract["head_tip"][1])
    except (KeyError, IndexError, TypeError, ValueError):
        return False
    return trail_problem(contract, butt_y, tip_y) is None


# ---------------------------------------------------------------------------------------------
# Java source invariants (no class or method names inside the combat packages).
# ---------------------------------------------------------------------------------------------

CLIENT_COMBAT_CLOCK = "ClientCombatClock.java"
GAME_CLOCK_READ = re.compile(r"\bgetGameTime\s*\(\s*\)")
JAVA_COMMENTS = re.compile(r"//[^\n]*|/\*.*?\*/", re.DOTALL)
RECORD_PATTERN = re.compile(r"\brecord\s+(\w+)\s*\((.*?)\)\s*(?:implements\b|\{)", re.DOTALL)
REGISTRATION_PATTERN = re.compile(r"\b(playToServer|playToClient|playBidirectional)\s*\(\s*(\w+)\s*\.\s*TYPE\b")
# Record component types a serverbound combat payload may carry: quantised player input with no
# authority over timing, distance, damage or protection (the dodge direction is one byte).
INPUT_ONLY_COMPONENT_TYPES = frozenset({"DodgeDirection"})


def input_only_components(components: str) -> bool:
    """True when every record component is of an INPUT_ONLY_COMPONENT_TYPES type (or there is none)."""
    text = components.strip()
    if not text:
        return True
    for component in text.split(","):
        parts = component.split()
        if len(parts) < 2 or parts[-2].split("<")[0] not in INPUT_ONLY_COMPONENT_TYPES:
            return False
    return True


def combat_sources(root: Path) -> list[tuple[Path, str]]:
    return java_sources(root, COMBAT_PACKAGE) + java_sources(root, CLIENT_COMBAT_PACKAGE)


def validate_source_invariants(root: Path, findings: list[Finding]) -> None:
    # PAL stays in client/combat; common combat and network code import no client classes.
    for path, content in java_sources(root, ""):
        if "com.zigythebird." in content and not in_package(root, path, CLIENT_COMBAT_PACKAGE):
            findings.append(Finding("PAL_IMPORT_OUTSIDE_CLIENT_COMBAT", relative(root, path)))
    for package in (COMBAT_PACKAGE, NETWORK_PACKAGE):
        for path, content in java_sources(root, package):
            if "import net.minecraft.client" in content or "import com.example.myvillage.client" in content:
                findings.append(Finding("CLIENT_IMPORT_IN_COMMON_COMBAT", relative(root, path)))

    sources = combat_sources(root)
    # Combat code never runs a vanilla attack.
    for path, content in sources:
        for pattern, label in VANILLA_ATTACK_PATTERNS:
            if pattern.search(content):
                findings.append(Finding("COMBAT_VANILLA_ATTACK", f"{relative(root, path)}: {label}"))

    # Combat code selects weapons through the weapon data, never by a named item.
    for path, content in sources:
        if "ModItems" in content:
            findings.append(Finding("COMBAT_NAMED_ITEM", relative(root, path)))

    records: dict[str, str] = {}
    directions: dict[str, set[str]] = {}
    for _, content in sources:
        for match in RECORD_PATTERN.finditer(content):
            records.setdefault(match.group(1), match.group(2))
        for direction, payload in REGISTRATION_PATTERN.findall(content):
            directions.setdefault(payload, set()).add(direction)

    # Client-to-server combat payloads carry intent only: an empty record, or components whose
    # types are pure player input (INPUT_ONLY_COMPONENT_TYPES); timing, distance, damage and
    # windows stay server-decided.
    serverbound = sorted(name for name, seen in directions.items()
                         if seen & {"playToServer", "playBidirectional"})
    if not serverbound:
        findings.append(Finding("COMBAT_C2S_PAYLOADS_MISSING", "no playToServer registration in combat code"))
    for name in serverbound:
        components = records.get(name)
        if components is None:
            findings.append(Finding("COMBAT_C2S_PAYLOAD_UNRESOLVED", f"{name} is not a record in combat code"))
        elif not input_only_components(components):
            findings.append(Finding("COMBAT_C2S_AUTHORITY_FIELD", f"{name}({' '.join(components.split())})"))

    # Clientbound payloads carry no damage or health; the impact payload is clientbound only.
    for name, seen in sorted(directions.items()):
        components = (records.get(name) or "").lower()
        is_impact = "Impact" in name
        if is_impact and seen != {"playToClient"}:
            findings.append(Finding("COMBAT_IMPACT_S2C_ONLY", f"{name} registered {','.join(sorted(seen))}"))
        if "playToClient" in seen or "playBidirectional" in seen:
            forbidden = IMPACT_FORBIDDEN_FIELDS if is_impact else CLIENTBOUND_FORBIDDEN_FIELDS
            for word in forbidden:
                if word in components:
                    code = "COMBAT_IMPACT_AUTHORITY_FIELD" if is_impact else "COMBAT_PAYLOAD_AUTHORITY_FIELD"
                    findings.append(Finding(code, f"{name}:{word}"))
    if not any("Impact" in name and "playToClient" in seen for name, seen in directions.items()):
        findings.append(Finding("COMBAT_IMPACT_S2C_ONLY", "no impact payload registered playToClient"))

    # Client combat timing reads one clock. The client's game time is re-set by the server's time
    # packet every 20 ticks; only ClientCombatClock reads it (the one server-tick conversion and the
    # reset watch). Everything else (swing, prediction, impact, trail, camera, arm lag) reads that clock.
    for path, content in java_sources(root, CLIENT_COMBAT_PACKAGE):
        if path.name == CLIENT_COMBAT_CLOCK:
            continue
        code = JAVA_COMMENTS.sub("", content)
        if GAME_CLOCK_READ.search(code):
            findings.append(Finding("COMBAT_CLIENT_GAME_CLOCK_READ", relative(root, path)))

    # Combat state stays out of the cultivation profile.
    profile = root / JAVA_ROOT / "com/example/myvillage/cultivation/CultivationProfile.java"
    if profile.is_file():
        lowered = text(profile).lower()
        for forbidden in ("combatmode", "comboindex", "actiontick", "attackrevision"):
            if forbidden in lowered:
                findings.append(Finding("COMBAT_STATE_IN_CULTIVATION_PROFILE", forbidden))


# ---------------------------------------------------------------------------------------------
# Qingfeng item and presentation assets (not part of the combat data refactor).  Deliberately
# specific to the accepted Qingfeng item; the checks every weapon gets are validate_weapon_models.
# ---------------------------------------------------------------------------------------------

def validate_qingfeng_item(root: Path, findings: list[Finding]) -> None:
    items_path = root / JAVA_ROOT / "com/example/myvillage/item/ModItems.java"
    if require_file(items_path, root, "QINGFENG_ITEMS_FILE", findings):
        items = text(items_path)
        for needle, code in (
                ("DeferredItem<SwordItem> QINGFENG_SWORD", "QINGFENG_REGISTRATION_TYPE"),
                ('ITEMS.registerItem("qingfeng_sword"', "QINGFENG_REGISTRATION_ID"),
                ("output.accept(QINGFENG_SWORD.get())", "QINGFENG_CREATIVE_TAB")):
            require_contains(items, needle, code, items_path.name, findings)
        # The registration itself: a CombatWeaponItem (a landed hit's durability change must not
        # replay the equip animation), diamond tier, the accepted attribute pair.
        start = items.find("DeferredItem<SwordItem> QINGFENG_SWORD")
        end = items.find(";", start) if start >= 0 else -1
        registration = items[start:end + 1] if start >= 0 and end >= 0 else ""
        require_contains(registration, "new CombatWeaponItem(", "QINGFENG_COMBAT_WEAPON_ITEM", items_path.name, findings)
        if re.search(r"new\s+\w+\(\s*Tiers\.DIAMOND\s*,", registration) is None:
            findings.append(Finding("QINGFENG_DIAMOND_TIER", items_path.name))
        require_contains(registration, "SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)", "QINGFENG_ATTRIBUTES",
                         items_path.name, findings)
        rideable = items.find("output.accept(RIDEABLE_FLYING_SWORD.get())")
        qingfeng = items.find("output.accept(QINGFENG_SWORD.get())")
        spirit = items.find("output.accept(LOW_GRADE_SPIRIT_STONE.get())")
        if not (0 <= rideable < qingfeng < spirit):
            findings.append(Finding("QINGFENG_CREATIVE_ORDER", "rideable -> qingfeng -> spirit stone"))

    model_path = root / QINGFENG_MODEL
    texture_path = root / QINGFENG_TEXTURE
    recipe_path = root / QINGFENG_RECIPE
    tag_path = root / SWORD_TAG
    en_path = root / RESOURCES / "assets/myvillage/lang/en_us.json"
    zh_path = root / RESOURCES / "assets/myvillage/lang/zh_cn.json"
    for path, code in (
            (model_path, "QINGFENG_MODEL"),
            (texture_path, "QINGFENG_TEXTURE"),
            (recipe_path, "QINGFENG_RECIPE"),
            (tag_path, "QINGFENG_SWORD_TAG"),
            (en_path, "QINGFENG_EN_LANG"),
            (zh_path, "QINGFENG_ZH_LANG")):
        require_file(path, root, code, findings)

    if model_path.is_file():
        validate_qingfeng_model(root, json.loads(text(model_path)), findings)
    if texture_path.is_file():
        data = texture_path.read_bytes()
        if (not data.startswith(b"\x89PNG\r\n\x1a\n")
                or data[12:16] != b"IHDR"
                or struct.unpack(">II", data[16:24]) != (64, 64)):
            findings.append(Finding("QINGFENG_TEXTURE_DIMENSIONS", "expected 64x64 PNG"))
        elif data[25] not in (4, 6):
            findings.append(Finding("QINGFENG_TEXTURE_ALPHA", f"png color type={data[25]}"))
    if recipe_path.is_file():
        recipe = json.loads(text(recipe_path))
        if (recipe.get("pattern") != ["D", "D", "S"]
                or recipe.get("key", {}).get("D", {}).get("item") != "minecraft:diamond"
                or recipe.get("key", {}).get("S", {}).get("item") != "minecraft:stick"
                or recipe.get("result", {}).get("id") != QINGFENG_ITEM):
            findings.append(Finding("QINGFENG_RECIPE_CONTRACT", recipe_path.name))
    if tag_path.is_file():
        tag = json.loads(text(tag_path))
        if QINGFENG_ITEM not in tag.get("values", []):
            findings.append(Finding("QINGFENG_SWORD_TAG_CONTRACT", tag_path.name))
    for path, item_name, code in (
            (en_path, "Qingfeng Sword", "QINGFENG_EN_NAME"),
            (zh_path, "青锋剑", "QINGFENG_ZH_NAME")):
        if path.is_file() and json.loads(text(path)).get("item.myvillage.qingfeng_sword") != item_name:
            findings.append(Finding(code, item_name))


def validate_qingfeng_model(root: Path, model: dict, findings: list[Finding]) -> None:
    """3D jian in hand (separate_transforms base), 2D icon in the GUI."""
    icon = {"parent": "minecraft:item/handheld", "textures": {"layer0": "myvillage:item/qingfeng_sword"}}
    perspectives = model.get("perspectives") if isinstance(model, dict) else None
    if (not isinstance(model, dict) or model.get("loader") != "neoforge:separate_transforms"
            or model.get("base") != {"parent": "myvillage:item/qingfeng_sword_3d"}
            or not isinstance(perspectives, dict) or perspectives.get("gui") != icon):
        findings.append(Finding("QINGFENG_MODEL_CONTRACT", "separate_transforms: 3D base, 2D gui icon"))
    model_3d_path = root / QINGFENG_MODEL_3D
    if require_file(model_3d_path, root, "QINGFENG_MODEL_3D", findings):
        try:
            model_3d = json.loads(text(model_3d_path))
        except json.JSONDecodeError as exc:
            findings.append(Finding("QINGFENG_MODEL_3D", str(exc)))
        else:
            display = model_3d.get("display", {}) if isinstance(model_3d, dict) else {}
            if (not isinstance(model_3d, dict) or "parent" in model_3d
                    or not model_3d.get("elements")
                    or model_3d.get("textures", {}).get("sword") != "myvillage:item/qingfeng_sword_model"
                    or not all(context in display for context in (
                        "thirdperson_righthand", "thirdperson_lefthand",
                        "firstperson_righthand", "firstperson_lefthand"))
                    or "gui" in display):
                findings.append(Finding("QINGFENG_MODEL_3D", "elements, own display, texture qingfeng_sword_model"))
    texture_path = root / QINGFENG_MODEL_TEXTURE
    if require_file(texture_path, root, "QINGFENG_MODEL_TEXTURE", findings):
        data = texture_path.read_bytes()
        if not data.startswith(b"\x89PNG\r\n\x1a\n") or data[12:16] != b"IHDR":
            findings.append(Finding("QINGFENG_MODEL_TEXTURE", "expected PNG"))


def validate_blade_cut_assets(root: Path, findings: list[Finding]) -> None:
    particle_json = root / BLADE_CUT_PARTICLE
    if require_file(particle_json, root, "COMBAT_BLADE_CUT_PARTICLE_JSON", findings):
        try:
            textures = json.loads(text(particle_json)).get("textures")
        except (json.JSONDecodeError, AttributeError) as exc:
            findings.append(Finding("COMBAT_BLADE_CUT_PARTICLE_JSON", str(exc)))
        else:
            if textures != ["myvillage:blade_cut"]:
                findings.append(Finding("COMBAT_BLADE_CUT_PARTICLE_JSON", str(textures)))
    particle_texture = root / BLADE_CUT_TEXTURE
    if require_file(particle_texture, root, "COMBAT_BLADE_CUT_TEXTURE", findings):
        if not particle_texture.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"):
            findings.append(Finding("COMBAT_BLADE_CUT_TEXTURE", "not a PNG"))


# ---------------------------------------------------------------------------------------------
# Generated assets, docs, and the packaged jar.
# ---------------------------------------------------------------------------------------------

def generator_checks(root: Path, data: combat_data.CombatData) -> list[tuple[str, str]]:
    """``(script, finding code)`` of every generator to run: the fixed ones, then the model
    generator named by each weapon's geometry contract."""
    checks = list(GENERATOR_CHECKS)
    for contract in read_geometry_contracts(root, data).values():
        generator = contract.get("generator")
        if (isinstance(generator, str) and GENERATOR_SCRIPT.match(generator)
                and generator not in (script for script, _ in checks)):
            checks.append((generator, WEAPON_MODEL_GENERATOR_DRIFT))
    return checks


def validate_generated_assets(root: Path, data: combat_data.CombatData, findings: list[Finding]) -> None:
    """Runs each committed generator's --check so hand edits to generated assets are caught."""
    for relative_script, code in generator_checks(root, data):
        script = root / relative_script
        if not require_file(script, root, "COMBAT_GENERATOR_MISSING", findings):
            continue
        try:
            result = subprocess.run(
                [sys.executable, str(script), "--check"],
                cwd=root,
                capture_output=True,
                text=True,
                timeout=GENERATOR_TIMEOUT_SECONDS,
                check=False)
        except (OSError, subprocess.TimeoutExpired) as exc:
            findings.append(Finding(code, f"{relative_script}: {exc}"))
            continue
        if result.returncode != 0:
            lines = (result.stderr or result.stdout).strip().splitlines()
            findings.append(Finding(code, f"{relative_script}: {lines[-1] if lines else result.returncode}"))


def validate_docs(root: Path, findings: list[Finding]) -> None:
    for name, needles in DOC_REQUIREMENTS.items():
        path = root / name
        if not require_file(path, root, "COMBAT_DOC_MISSING", findings):
            continue
        content = text(path)
        for needle in needles:
            if needle not in content:
                findings.append(Finding("COMBAT_DOC_DRIFT", f"{path.name}:{needle}"))


def packaged_resources(root: Path, data: combat_data.CombatData) -> set[str]:
    """Source paths (relative to src/main/resources) that the mod jar must contain."""
    resources = root / RESOURCES
    expected: set[str] = set()
    data_root = resources / "data"
    if data_root.is_dir():
        for path in data_root.glob("*/combat/**/*.json"):
            expected.add(path.relative_to(resources).as_posix())
    namespaces = {namespace_of(move["id"]) for _, move in data.moves()}
    for namespace in namespaces:
        directory = resources / "assets" / namespace / "player_animations"
        expected.update(p.relative_to(resources).as_posix() for p in directory.glob("*.json"))
        expected.add(f"assets/{namespace}/sounds.json")
        expected.update(p.relative_to(resources).as_posix() for p in (resources / "assets" / namespace / "lang").glob("*.json"))
    for weapon in data.weapons.values():
        for asset in (weapon["first_person_rig"], weapon["geometry"]):
            expected.add(combat_data.asset_file(root, asset).relative_to(resources).as_posix())
    # Each weapon's item model, the models it wraps, the 3D model of its contract, and their textures.
    expected.update(path.relative_to(resources).as_posix() for path in weapon_resource_files(root, data))
    # The accepted Qingfeng item stays expected even if its weapon entry changes.
    for path in (QINGFENG_MODEL, QINGFENG_TEXTURE, QINGFENG_MODEL_3D, QINGFENG_MODEL_TEXTURE, QINGFENG_RECIPE,
                 SWORD_TAG, BLADE_CUT_PARTICLE, BLADE_CUT_TEXTURE):
        expected.add(Path(path).relative_to(RESOURCES).as_posix())
    return expected


def stale_jar_sources(root: Path, jar: Path, archive: zipfile.ZipFile, names: set[str],
                      resources: list[str]) -> list[str]:
    """Sources changed after the jar was written that the jar does not hold as a build would pack them.

    Gradle leaves the jar untouched when a rebuild changes no bytes (a comment-only Java edit), so
    a jar older than a source is still current when it holds the resource's bytes or, for a Java
    source, the bytes of its class files compiled since the source last changed.
    """
    jar_time = jar.stat().st_mtime
    stale = []
    for name in resources:
        source = root / RESOURCES / name
        if (source.is_file() and source.stat().st_mtime > jar_time
                and (name not in names or archive.read(name) != source.read_bytes())):
            stale.append(name)
    compiled_root = root / COMPILED_CLASSES
    for path, _ in combat_sources(root):
        source_time = path.stat().st_mtime
        if source_time <= jar_time:
            continue
        relative_class = path.relative_to(root / JAVA_ROOT).with_suffix(".class")
        compiled = compiled_root / relative_class
        nested = sorted(compiled.parent.glob(glob.escape(compiled.stem) + "$*.class")) if compiled.is_file() else []
        current = compiled.is_file() and compiled.stat().st_mtime >= source_time and all(
            (entry := item.relative_to(compiled_root).as_posix()) in names
            and archive.read(entry) == item.read_bytes()
            for item in [compiled, *nested])
        if not current:
            stale.append(relative(root, path))
    return stale


def validate_jar_resources(root: Path, data: combat_data.CombatData, findings: list[Finding]) -> None:
    """The newest mod jar, when present and current, packages the combat data, assets, and classes."""
    build_libs = root / "build/libs"
    jars = list(build_libs.glob("myvillage-*.jar")) if build_libs.is_dir() else []
    if not jars:
        return
    jar = max(jars, key=lambda path: path.stat().st_mtime)
    properties = root / "gradle.properties"
    version = re.search(r"^mod_version=(\S+)$", text(properties), re.MULTILINE) if properties.is_file() else None
    if version is not None and jar.name != f"myvillage-{version.group(1)}.jar":
        # The newest jar predates the current release version; rebuild before inspecting it.
        findings.append(Finding("COMBAT_JAR_STALE", f"{jar.name}:expected myvillage-{version.group(1)}.jar"))
        return
    resources = sorted(packaged_resources(root, data))
    classes = sorted(path.relative_to(root / JAVA_ROOT).with_suffix(".class").as_posix()
                     for path, _ in combat_sources(root))
    try:
        with zipfile.ZipFile(jar) as archive:
            names = set(archive.namelist())
            stale = stale_jar_sources(root, jar, archive, names, resources)
            packaged = {name: archive.read(name) for name in resources
                        if name in names and (name.startswith("data/") or "/player_animations/" in name)}
    except zipfile.BadZipFile as exc:
        findings.append(Finding("COMBAT_JAR_INVALID", str(exc)))
        return
    if stale:
        findings.append(Finding("COMBAT_JAR_STALE", f"{jar.name}:{','.join(stale)}"))
        return
    missing = sorted(set(resources + classes) - names)
    if missing:
        findings.append(Finding("COMBAT_JAR_RESOURCE_MISSING", ",".join(missing)))
    for name, content in sorted(packaged.items()):
        if name.startswith("data/") and content != (root / RESOURCES / name).read_bytes():
            findings.append(Finding("COMBAT_JAR_DATA_DRIFT", name))
    packaged_animations: set[str] = set()
    for name, content in packaged.items():
        if "/player_animations/" not in name:
            continue
        try:
            namespace = name.split("/")[1]
            packaged_animations.update(f"{namespace}:{key}" for key in json.loads(content)["animations"])
        except (KeyError, IndexError, TypeError, json.JSONDecodeError, UnicodeDecodeError) as exc:
            findings.append(Finding("COMBAT_JAR_ANIMATION_JSON", f"{name}: {exc}"))
    required = {move["id"] for _, move in data.moves()}
    for style in data.styles.values():
        required.update(style["animations"].values())
    missing_animations = sorted(required - packaged_animations)
    if missing_animations:
        findings.append(Finding("COMBAT_JAR_ANIMATION_IDS", f"{jar.name}:{','.join(missing_animations)}"))


# ---------------------------------------------------------------------------------------------
# Entry point.
# ---------------------------------------------------------------------------------------------

def validate(root: Path = ROOT, run_generators: bool = True) -> list[Finding]:
    root = Path(root)
    findings: list[Finding] = []
    validate_pal_jar(root, findings)
    validate_dependency_wiring(root, findings)
    data = validate_combat_data(root, findings)
    validate_player_animations(root, data, findings)
    validate_translations_and_sounds(root, data, findings)
    contracts = read_geometry_contracts(root, data)
    validate_weapon_items(root, data, findings)
    validate_weapon_families(root, data, findings)
    validate_paired_weapons(root, data, contracts, findings)
    validate_weapon_models(root, data, contracts, findings)
    validate_first_person_rigs(root, data, contracts, findings)
    validate_geometry_contracts(root, data, findings)
    validate_trail_reach(root, data, contracts, findings)
    validate_source_invariants(root, findings)
    validate_qingfeng_item(root, findings)
    validate_blade_cut_assets(root, findings)
    if run_generators:
        validate_generated_assets(root, data, findings)
    validate_docs(root, findings)
    validate_forbidden_integrations(root, findings)
    validate_jar_resources(root, data, findings)
    validate_no_shaded_pal(root, findings)
    return findings


def main() -> int:
    findings = validate()
    if findings:
        for finding in findings:
            print(f"FAIL {finding}")
        return 1
    print("sword combat foundation validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
