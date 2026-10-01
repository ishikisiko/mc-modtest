#!/usr/bin/env python3
"""Validate the cultivation sword-combat contract: data, cross-file resources, and boundaries.

The move facts live in the bundled combat data (``data/myvillage/combat/``), read through
``tools/combat_data.py``.  This validator checks that data against the schema and the move
invariants, then cross-checks it against the resources that depend on it: the PAL player
animations, translations, sound events, item models, first-person rigs, and geometry
contracts.  It holds no per-move numbers; the accepted Qingfeng values are pinned once in
``tools/tests/test_combat_style_baseline.py``.

Java source checks are limited to invariants that do not depend on class or method names
inside the combat packages: PAL and client imports stay client-side, the client-to-server
combat payloads are empty records, combat code makes no vanilla attack, and the impact
payload is clientbound only without damage or health.  Behaviour inside the combat packages
is covered by the Java unit tests.
"""

from __future__ import annotations

import hashlib
import json
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
GEOMETRY_FIELDS = ("grip_center", "handle", "guard", "pommel", "blade_base", "blade_tip",
                   "edge_axis", "flat_axis", "axes")
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
GENERATOR_CHECKS = (
    ("tools/gen_sword_pal_anims.py", "COMBAT_PAL_GENERATOR_DRIFT"),
    ("tools/gen_blade_cut_sprite.py", "COMBAT_BLADE_CUT_SPRITE_DRIFT"),
    ("tools/gen_qingfeng_sword_model.py", "COMBAT_SWORD_MODEL_GENERATOR_DRIFT"),
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


def validate_weapon_items(root: Path, data: combat_data.CombatData, findings: list[Finding]) -> None:
    """A weapon's item is registered in Java under its id and has an item model."""
    sources = [content for _, content in java_sources(root, "")]
    for weapon_id, weapon in data.weapons.items():
        namespace, name = combat_data.split_id(weapon["item"])
        registration = re.compile(rf'\.register(?:Item)?\(\s*"{re.escape(name)}"')
        if not any(registration.search(content) for content in sources):
            findings.append(Finding("COMBAT_WEAPON_ITEM_UNREGISTERED", f"{weapon_id}: {weapon['item']}"))
        model = root / RESOURCES / "assets" / namespace / "models/item" / f"{name}.json"
        if not model.is_file():
            findings.append(Finding("COMBAT_WEAPON_ITEM_MODEL", f"{weapon_id}: {relative(root, model)}"))


def validate_first_person_rigs(root: Path, data: combat_data.CombatData, findings: list[Finding]) -> None:
    """Each weapon's rig poses every move of its style, with strike windows over the active ticks."""
    checked: set[tuple[str, str]] = set()
    for weapon_id, weapon in data.weapons.items():
        style = data.styles.get(weapon["style"])
        if style is None or (weapon["first_person_rig"], weapon["style"]) in checked:
            continue
        checked.add((weapon["first_person_rig"], weapon["style"]))
        path = combat_data.asset_file(root, weapon["first_person_rig"])
        rig = read_json(path, root, "COMBAT_FIRST_PERSON_RIG_MISSING", findings)
        if rig is None:
            continue
        validate_rig(rig, relative(root, path), style, findings)


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
    scale = rig_settings.get("sword_scale", 0.6)
    arm = rig_settings.get("arm", {})
    if (not is_number(scale) or not 0.2 <= scale <= 1.5 or not isinstance(arm, dict)
            or any(not is_number(arm.get(field, 0))
                   for field in ("upper_arm", "forearm", "thickness", "grip_diagonal", "follow_through"))
            or not 0.2 <= arm.get("thickness", 0.5) <= 1.2
            or not 0 <= arm.get("grip_diagonal", 40) <= 50):
        findings.append(Finding("COMBAT_FIRST_PERSON_RIG_ARM", f"{name}: rig.sword_scale/rig.arm"))
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
    """Each weapon's geometry contract carries the grip, guard, blade, and axes fields."""
    for geometry_id in sorted({weapon["geometry"] for weapon in data.weapons.values()}):
        path = combat_data.asset_file(root, geometry_id)
        geometry = read_json(path, root, "COMBAT_GEOMETRY_CONTRACT", findings)
        if geometry is None:
            continue
        name = relative(root, path)
        if not isinstance(geometry, dict):
            findings.append(Finding("COMBAT_GEOMETRY_CONTRACT", f"{name}: not an object"))
            continue
        missing = [field for field in GEOMETRY_FIELDS if field not in geometry]
        if missing or geometry.get("units") != "model_pixels":
            findings.append(Finding("COMBAT_GEOMETRY_CONTRACT", f"{name}: missing {','.join(missing or ['units'])}"))
            continue
        try:
            grip_y = float(geometry["grip_center"][1])
            handle = [float(v) for v in geometry["handle"]["y"]]
            guard = [float(v) for v in geometry["guard"]["y"]]
            pommel = [float(v) for v in geometry["pommel"]["y"]]
            base_y, tip_y = float(geometry["blade_base"][1]), float(geometry["blade_tip"][1])
        except (KeyError, IndexError, TypeError, ValueError) as exc:
            findings.append(Finding("COMBAT_GEOMETRY_CONTRACT", f"{name}: malformed {exc}"))
            continue
        axes = geometry.get("axes", {})
        if (not pommel[1] <= handle[0] < grip_y < handle[1] <= guard[0] < guard[1] <= base_y < tip_y
                or not isinstance(axes, dict)
                or (axes.get("blade"), axes.get("flat_normal"), axes.get("edge")) != ("+y", "x", "z")):
            findings.append(Finding("COMBAT_GEOMETRY_CONTRACT", f"{name}: pommel<handle<guard<blade, axes +y/x/z"))


# ---------------------------------------------------------------------------------------------
# Java source invariants (no class or method names inside the combat packages).
# ---------------------------------------------------------------------------------------------

RECORD_PATTERN = re.compile(r"\brecord\s+(\w+)\s*\((.*?)\)\s*(?:implements\b|\{)", re.DOTALL)
REGISTRATION_PATTERN = re.compile(r"\b(playToServer|playToClient|playBidirectional)\s*\(\s*(\w+)\s*\.\s*TYPE\b")


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

    records: dict[str, str] = {}
    directions: dict[str, set[str]] = {}
    for _, content in sources:
        for match in RECORD_PATTERN.finditer(content):
            records.setdefault(match.group(1), match.group(2))
        for direction, payload in REGISTRATION_PATTERN.findall(content):
            directions.setdefault(payload, set()).add(direction)

    # Client-to-server combat payloads are empty records: the client sends intent only.
    serverbound = sorted(name for name, seen in directions.items()
                         if seen & {"playToServer", "playBidirectional"})
    if not serverbound:
        findings.append(Finding("COMBAT_C2S_PAYLOADS_MISSING", "no playToServer registration in combat code"))
    for name in serverbound:
        components = records.get(name)
        if components is None:
            findings.append(Finding("COMBAT_C2S_PAYLOAD_UNRESOLVED", f"{name} is not a record in combat code"))
        elif components.strip():
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

    # Combat state stays out of the cultivation profile.
    profile = root / JAVA_ROOT / "com/example/myvillage/cultivation/CultivationProfile.java"
    if profile.is_file():
        lowered = text(profile).lower()
        for forbidden in ("combatmode", "comboindex", "actiontick", "attackrevision"):
            if forbidden in lowered:
                findings.append(Finding("COMBAT_STATE_IN_CULTIVATION_PROFILE", forbidden))


# ---------------------------------------------------------------------------------------------
# Qingfeng item and presentation assets (not part of the combat data refactor).
# ---------------------------------------------------------------------------------------------

def validate_qingfeng_item(root: Path, findings: list[Finding]) -> None:
    items_path = root / JAVA_ROOT / "com/example/myvillage/item/ModItems.java"
    if require_file(items_path, root, "QINGFENG_ITEMS_FILE", findings):
        items = text(items_path)
        for needle, code in (
                ("DeferredItem<SwordItem> QINGFENG_SWORD", "QINGFENG_REGISTRATION_TYPE"),
                ('ITEMS.registerItem("qingfeng_sword"', "QINGFENG_REGISTRATION_ID"),
                ("Tiers.DIAMOND", "QINGFENG_DIAMOND_TIER"),
                ("SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)", "QINGFENG_ATTRIBUTES"),
                ("output.accept(QINGFENG_SWORD.get())", "QINGFENG_CREATIVE_TAB")):
            require_contains(items, needle, code, items_path.name, findings)
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

def validate_generated_assets(root: Path, findings: list[Finding]) -> None:
    """Runs each committed generator's --check so hand edits to generated assets are caught."""
    for relative_script, code in GENERATOR_CHECKS:
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
        namespace, name = combat_data.split_id(weapon["item"])
        expected.add(f"assets/{namespace}/models/item/{name}.json")
    for path in (QINGFENG_MODEL, QINGFENG_TEXTURE, QINGFENG_MODEL_3D, QINGFENG_MODEL_TEXTURE, QINGFENG_RECIPE,
                 SWORD_TAG, BLADE_CUT_PARTICLE, BLADE_CUT_TEXTURE):
        expected.add(Path(path).relative_to(RESOURCES).as_posix())
    return expected


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
    sources = [root / RESOURCES / name for name in resources] + [path for path, _ in combat_sources(root)]
    newest_source = max((path.stat().st_mtime for path in sources if path.is_file()), default=0)
    if jar.stat().st_mtime < newest_source:
        findings.append(Finding("COMBAT_JAR_STALE", jar.name))
        return
    try:
        with zipfile.ZipFile(jar) as archive:
            names = set(archive.namelist())
            packaged = {name: archive.read(name) for name in resources
                        if name in names and (name.startswith("data/") or "/player_animations/" in name)}
    except zipfile.BadZipFile as exc:
        findings.append(Finding("COMBAT_JAR_INVALID", str(exc)))
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
    validate_weapon_items(root, data, findings)
    validate_first_person_rigs(root, data, findings)
    validate_geometry_contracts(root, data, findings)
    validate_source_invariants(root, findings)
    validate_qingfeng_item(root, findings)
    validate_blade_cut_assets(root, findings)
    if run_generators:
        validate_generated_assets(root, findings)
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
