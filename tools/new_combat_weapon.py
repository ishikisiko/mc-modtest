#!/usr/bin/env python3
"""Start a new combat weapon from an existing one, and report how far a weapon has come.

The procedure is docs/ai-kb/36_new_combat_weapon.md.  Its numbered steps carry the ids in
``STEPS``; both modes speak in those ids, in that order.

    python3 tools/new_combat_weapon.py scaffold --from myvillage:qingfeng_sword --id myvillage:jade_sword --dry-run
    python3 tools/new_combat_weapon.py scaffold --from myvillage:lingxiao_spear --id myvillage:iron_halberd \\
        --style myvillage:basic_halberd [--moves thrust,sweep,hook,chop,lunge] [--dry-run]
    python3 tools/new_combat_weapon.py progress myvillage:iron_halberd [--fast] [--json]

``scaffold`` derives, from the template weapon (``--from``), only what is a mechanical renaming:
the weapon file, the index entries, a copy of the template's first-person rig, with ``--style``
a copy of the template's style with every move re-identified, and the translation entries (marked
``TODO(new_combat_weapon)``).  It never writes Java, a generator-owned file (item models, textures,
geometry contracts, PAL animation files, the parity golden), or test fixtures; for each of those
it prints the manual step with its file.  It refuses ids that are taken and files that exist, and
``--dry-run`` writes nothing.

``progress`` checks, in playbook order, which facts of a weapon exist and pass.  It runs the
existing checks (tools/validate_sword_combat_foundation.py, tools/validate_mod_items.py, the PAL
generator's pose tables, ``tools/release_gate.py --list``, and the tests that pin the shipped
weapon list) and sorts their findings by step; it re-implements none of them.  A rig, a style, or a
name that is still the template's copy is reported as ``placeholder``.  Exit status: 0 when no
checked step is missing, 1 otherwise, 2 for a bad request.

Standard library only.  ``--root`` (default: this repository) lets both modes work on a copy.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import importlib.util
import json
import math
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

try:
    from tools import combat_data
except ImportError:  # run as a script: tools/ itself is on sys.path
    import combat_data


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = combat_data.RESOURCES
PLAYBOOK = "docs/ai-kb/36_new_combat_weapon.md"
# Every value the scaffold copies that a person must replace starts with this.
PLACEHOLDER = "TODO(new_combat_weapon)"
NAMESPACE = "myvillage"
ITEM_PATH = re.compile(r"^[a-z0-9_]+$")
JAVA_ROOT = "src/main/java"
MOD_ITEMS = f"{JAVA_ROOT}/com/example/myvillage/item/ModItems.java"
SWORD_TAG = f"{RESOURCES}/data/minecraft/tags/item/swords.json"
ITEM_CONTRACTS = "genops/contracts/items"
ITEM_CONTRACT_SCHEMA = "genops/schemas/item_contract.schema.json"
PARITY_GOLDEN = "src/test/resources/first_person_preview_parity.json"
PAL_GENERATOR = "tools/gen_sword_pal_anims.py"
PARITY_TEST_JAVA = "com.example.myvillage.client.combat.FirstPersonPreviewParityTest"
GRADLE = "flock /home/ubuntu/code/mc/.mc-heavy.lock ./gradlew"
# Tests that pin the shipped style and weapon lists; a new weapon makes them fail until they are
# updated (found by the rehearsal in docs/ai-kb/36_new_combat_weapon.md).
PINNED_TESTS = (
    "tools.tests.test_combat_data.CombatDataTest.test_committed_data_is_valid",
    "tools.tests.test_validate_sword_combat_foundation.SwordCombatFoundationValidatorTest"
    ".test_valid_repository_fixture_passes",
)
# A second weapon on a shared style is drawn with the PAL poses solved for the style's table weapon;
# its grip after its own thirdperson_righthand transform must sit where the table weapon's does.
GRIP_TOLERANCE_PX = 0.1
AXIS_TOLERANCE_DEG = 0.5


# ---------------------------------------------------------------------------------------------
# The playbook's steps (docs/ai-kb/36_new_combat_weapon.md), in order.
# ---------------------------------------------------------------------------------------------

@dataclass(frozen=True)
class Step:
    id: str
    title: str
    checked: bool  # False: a process step this tool cannot check (preview, capture, owner, release)


STEPS: tuple[Step, ...] = (
    Step("choose", "Choose the weapon, its template, and its style", False),
    Step("scaffold", "Run the scaffold", False),
    Step("item-contract", "Item contract", True),
    Step("item-registration", "Java item registration", True),
    Step("item-tag", "Item tag", True),
    Step("model-and-contract", "Model generator, item model, textures, geometry contract", True),
    Step("weapon-data", "Weapon file and index entries", True),
    Step("style-data", "Style moves: timing, damage, reaction, feedback", True),
    Step("names", "Item and move names", True),
    Step("hit-samples", "Hit samples and world trails", True),
    Step("third-person-poses", "Third-person poses", True),
    Step("first-person-rig", "First-person rig", True),
    Step("parity-golden", "First-person parity golden", True),
    Step("item-validator-pin", "Item validator entry", True),
    Step("tests", "Tests", True),
    Step("release-gate-step", "Release gate step", True),
    Step("readme", "README", True),
    Step("preview", "Iterate offline", False),
    Step("candidate-sheet", "Candidate sheets for the owner", False),
    Step("capture", "Confirm in game once", False),
    Step("owner-review", "Owner review and the not_verified list", False),
    Step("release", "Release", False),
)
STEP_NUMBER = {step.id: number for number, step in enumerate(STEPS, start=1)}
CHECKED_STEPS = tuple(step.id for step in STEPS if step.checked)
PROCESS_AFTER_FACTS = tuple(step.id for step in STEPS if not step.checked and STEP_NUMBER[step.id] > 2)


def step_label(step_id: str) -> str:
    return f"step {STEP_NUMBER[step_id]} `{step_id}`"


# ---------------------------------------------------------------------------------------------
# Small helpers.
# ---------------------------------------------------------------------------------------------

class Refusal(Exception):
    """The request cannot be carried out; nothing was written."""


def rel(root: Path, path: Path) -> str:
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        return path.as_posix()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def item_model_file(root: Path, item_id: str) -> Path:
    namespace, path = combat_data.split_id(item_id)
    return root / RESOURCES / "assets" / namespace / "models" / "item" / f"{path}.json"


def lang_files(root: Path, namespace: str) -> list[Path]:
    directory = root / RESOURCES / "assets" / namespace / "lang"
    return sorted(directory.glob("*.json")) if directory.is_dir() else []


def render_lang(document: dict) -> str:
    """The layout of the committed lang files (two-space indent, UTF-8 text, final newline)."""
    return json.dumps(document, indent=2, ensure_ascii=False) + "\n"


_JSON_STRING = re.compile(r'"(?:[^"\\]|\\.)*"')


def substitute_strings(text: str, mapping: dict[str, str]) -> str:
    """``text`` with every JSON string token (key or value) that equals a key of ``mapping``
    replaced by its value; everything else, layout included, is kept byte for byte."""
    def replace(match: re.Match) -> str:
        value = json.loads(match.group(0))
        return json.dumps(mapping[value], ensure_ascii=False) if value in mapping else match.group(0)
    return _JSON_STRING.sub(replace, text)


def append_to_index_list(text: str, key: str, value: str) -> str:
    """The index text with ``value`` appended to its ``key`` list, layout kept."""
    match = re.search(rf'("{re.escape(key)}"\s*:\s*\[)(.*?)(\])', text, re.DOTALL)
    if match is None:
        raise Refusal(f"index has no {key!r} list")
    body = match.group(2)
    stripped = body.rstrip()
    joined = (stripped + ", " if stripped.strip() else "") + json.dumps(value) + body[len(stripped):]
    return text[:match.start(2)] + joined + text[match.end(2):]


def java_sources(root: Path) -> list[str]:
    base = root / JAVA_ROOT
    return [path.read_text(encoding="utf-8") for path in sorted(base.rglob("*.java"))] if base.is_dir() else []


def animation_names(root: Path, namespace: str) -> set[str]:
    directory = root / RESOURCES / "assets" / namespace / "player_animations"
    names: set[str] = set()
    for path in sorted(directory.glob("*.json")) if directory.is_dir() else ():
        try:
            names.update(read_json(path).get("animations", {}))
        except (ValueError, AttributeError):
            continue
    return names


def template_registration(root: Path, item_path: str) -> tuple[str, str] | None:
    """(holder, declaration text) of an item's ``DeferredItem`` in ModItems.java, or None."""
    path = root / MOD_ITEMS
    if not path.is_file():
        return None
    text = path.read_text(encoding="utf-8")
    match = re.search(
        rf"public\s+static\s+final\s+DeferredItem<\w+>\s+(\w+)\s*=\s*ITEMS\.registerItem\(\s*\"{re.escape(item_path)}\".*?\)\s*;",
        text, re.DOTALL)
    return None if match is None else (match.group(1), match.group(0))


def load_module(root: Path, script: str):
    """A ``tools/<script>.py`` of ``root`` imported under a private name, so its ``ROOT`` is ``root``."""
    path = root / "tools" / f"{script}.py"
    if not path.is_file():
        return None
    digest = hashlib.sha1(str(path.resolve()).encode()).hexdigest()[:10]
    name = f"_new_combat_weapon_{script}_{digest}"
    if name in sys.modules:
        return sys.modules[name]
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    try:
        spec.loader.exec_module(module)
    except BaseException:
        del sys.modules[name]
        raise
    return module


# ---------------------------------------------------------------------------------------------
# Scaffold.
# ---------------------------------------------------------------------------------------------

@dataclass
class Plan:
    """What a scaffold writes (new files), changes (existing files), and leaves to a person."""

    root: Path
    weapon_id: str
    template_id: str
    style_id: str
    new_style: bool
    writes: list[tuple[Path, str]] = field(default_factory=list)
    changes: list[tuple[Path, str, str]] = field(default_factory=list)  # path, new text, what changes
    manual: list[tuple[str, str]] = field(default_factory=list)         # step id, instruction

    def manual_ids(self) -> list[str]:
        return [step_id for step_id, _ in self.manual]


@dataclass(frozen=True)
class Names:
    """Every id and path of the new weapon, derived from ``--id`` and ``--style``."""

    weapon_id: str
    item_path: str
    rig_id: str
    geometry_id: str
    style_id: str
    move_ids: tuple[str, ...]
    animation_ids: dict[str, str]


def derive_names(weapon_id: str, style_id: str, template_style: dict | None,
                 move_names: list[str] | None) -> Names:
    namespace, item_path = combat_data.split_id(weapon_id)
    move_ids: list[str] = []
    animations: dict[str, str] = {}
    if template_style is not None:
        _, new_path = combat_data.split_id(style_id)
        _, old_path = combat_data.split_id(template_style["id"])
        for index, move in enumerate(template_style["moves"]):
            move_ns, move_path = combat_data.split_id(move["id"])
            if move_names is not None:
                suffix = f"_{index + 1:02d}_{move_names[index]}"
            elif move_path.startswith(old_path + "_"):
                suffix = move_path[len(old_path):]
            else:
                suffix = f"_{index + 1:02d}"
            move_ids.append(f"{namespace}:{new_path}{suffix}")
        animations = {role: f"{namespace}:{new_path}_{role}" for role in template_style["animations"]}
    return Names(
        weapon_id=weapon_id,
        item_path=item_path,
        rig_id=f"{namespace}:combat/{item_path}_first_person.json",
        geometry_id=f"{namespace}:combat/{item_path}_geometry.json",
        style_id=style_id,
        move_ids=tuple(move_ids),
        animation_ids=animations,
    )


def display_key(move_id: str) -> str:
    namespace, path = combat_data.split_id(move_id)
    return f"combat.{namespace}.move.{path}"


def plan_scaffold(root: Path, template_id: str, weapon_id: str, style_id: str | None = None,
                  move_names: list[str] | None = None) -> Plan:
    """Everything a scaffold would do; raises :class:`Refusal` before anything is written."""
    root = Path(root)
    data = combat_data.load(root)
    if template_id not in data.weapons:
        issues = "; ".join(str(issue) for issue in data.issues)
        raise Refusal(f"--from {template_id} is not a weapon that loads from {combat_data.INDEX_FILE} "
                      f"(weapons that load: {', '.join(data.weapons) or 'none'})"
                      + (f"; combat data issues: {issues}" if issues else ""))
    template = data.weapons[template_id]
    template_style_id = template["style"]
    if template_style_id not in data.styles:
        raise Refusal(f"the template's style {template_style_id} does not load")
    template_style = data.styles[template_style_id]

    try:
        namespace, item_path = combat_data.split_id(weapon_id)
    except ValueError as exc:
        raise Refusal(f"--id: {exc}") from None
    if namespace != NAMESPACE or not ITEM_PATH.match(item_path):
        raise Refusal(f"--id must be {NAMESPACE}:<lower_case_name> (letters, digits, underscores), got {weapon_id!r}")

    new_style = style_id is not None and style_id != template_style_id
    if style_id is None:
        style_id = template_style_id
    if new_style:
        try:
            style_ns, style_path = combat_data.split_id(style_id)
        except ValueError as exc:
            raise Refusal(f"--style: {exc}") from None
        if style_ns != NAMESPACE or not ITEM_PATH.match(style_path):
            raise Refusal(f"--style must be {NAMESPACE}:<lower_case_name>, got {style_id!r}")
        if style_id in data.index.get("styles", ()) or combat_data.style_file(root, style_id).exists():
            raise Refusal(f"--style {style_id} is taken (listed in the index or its file exists); a weapon "
                          f"on an existing style starts from a template weapon of that style")
    if move_names is not None:
        if not new_style:
            raise Refusal("--moves renames the moves of a new style; it needs --style")
        if len(move_names) != len(template_style["moves"]):
            raise Refusal(f"--moves needs {len(template_style['moves'])} names (the template style's move "
                          f"count), got {len(move_names)}")
        bad = [name for name in move_names if not ITEM_PATH.match(name)]
        if bad or len(set(move_names)) != len(move_names):
            raise Refusal(f"--moves names must be distinct lower_case_names, got {move_names}")

    names = derive_names(weapon_id, style_id, template_style if new_style else None, move_names)
    taken: list[str] = []

    # The weapon and item ids.
    if weapon_id in data.index.get("weapons", ()) or combat_data.weapon_file(root, weapon_id).exists():
        taken.append(f"weapon {weapon_id} (listed in the index or {rel(root, combat_data.weapon_file(root, weapon_id))} exists)")
    users = [other for other, weapon in data.weapons.items() if weapon["item"] == weapon_id]
    if users:
        taken.append(f"item {weapon_id} is the item of weapon {', '.join(users)}")
    registration = re.compile(rf'\.register(?:Item|SimpleItem|Block|SimpleBlock)?\(\s*"{re.escape(item_path)}"')
    if any(registration.search(source) for source in java_sources(root)):
        taken.append(f"item {weapon_id} is already registered in {JAVA_ROOT}")
    for path in (item_model_file(root, weapon_id),
                 combat_data.asset_file(root, names.rig_id),
                 combat_data.asset_file(root, names.geometry_id),
                 root / ITEM_CONTRACTS / f"{item_path}.json"):
        if path.exists():
            taken.append(f"{rel(root, path)} exists")
    item_key = f"item.{namespace}.{item_path}"
    for path in lang_files(root, namespace):
        if item_key in read_json(path):
            taken.append(f"{item_key} is already in {rel(root, path)}")

    # The style, move, and animation ids of a new style.
    if new_style:
        existing_moves = {move["id"] for _, move in data.moves()}
        existing_animations = animation_names(root, namespace)
        for move_id in names.move_ids:
            if move_id in existing_moves or combat_data.split_id(move_id)[1] in existing_animations:
                taken.append(f"move id {move_id} (a move or a PAL animation of that name exists)")
        for animation_id in names.animation_ids.values():
            if combat_data.split_id(animation_id)[1] in existing_animations:
                taken.append(f"animation id {animation_id} exists")
        keys = {display_key(move_id) for move_id in names.move_ids}
        for path in lang_files(root, namespace):
            for key in sorted(keys & read_json(path).keys()):
                taken.append(f"{key} is already in {rel(root, path)}")
    if taken:
        raise Refusal("refused, nothing written: " + "; ".join(taken))

    plan = Plan(root, weapon_id, template_id, style_id, new_style)

    # Style (new style only): the template's text with every id renamed.
    mapping: dict[str, str] = {}
    if new_style:
        mapping[template_style_id] = style_id
        for move, new_id in zip(template_style["moves"], names.move_ids):
            mapping[move["id"]] = new_id
            mapping[move["display_key"]] = display_key(new_id)
        for role, old in template_style["animations"].items():
            mapping[old] = names.animation_ids[role]
        style_text = substitute_strings(combat_data.style_file(root, template_style_id).read_text(encoding="utf-8"),
                                        mapping)
        plan.writes.append((combat_data.style_file(root, style_id), style_text))

    # First-person rig: the template's text, its move keys renamed for a new style.
    template_rig = combat_data.asset_file(root, template["first_person_rig"])
    if not template_rig.is_file():
        raise Refusal(f"the template's first-person rig {rel(root, template_rig)} is missing")
    rig_text = template_rig.read_text(encoding="utf-8")
    if new_style:
        rig_text = substitute_strings(rig_text, {old: new for old, new in mapping.items()
                                                 if old in {m["id"] for m in template_style["moves"]}})
    plan.writes.append((combat_data.asset_file(root, names.rig_id), rig_text))

    # Weapon file, in the layout of the committed ones.
    weapon_doc = {"schema": combat_data.SCHEMA_VERSION, "item": weapon_id, "style": style_id,
                  "first_person_rig": names.rig_id, "geometry": names.geometry_id}
    plan.writes.append((combat_data.weapon_file(root, weapon_id), json.dumps(weapon_doc, indent=2) + "\n"))

    # Index: appended, so the first weapon of a style and the first style keep their roles.
    index_path = root / combat_data.INDEX_FILE
    index_text = index_path.read_text(encoding="utf-8")
    new_index = index_text
    if new_style:
        new_index = append_to_index_list(new_index, "styles", style_id)
    new_index = append_to_index_list(new_index, "weapons", weapon_id)
    plan.changes.append((index_path, new_index,
                         ("styles += " + style_id + "; " if new_style else "") + "weapons += " + weapon_id))

    # Translations: placeholders after the template's entries, in every lang file.
    template_ns, template_path = combat_data.split_id(template["item"])
    for path in lang_files(root, namespace):
        text = path.read_text(encoding="utf-8")
        document = json.loads(text)
        if render_lang(document) != text:
            raise Refusal(f"{rel(root, path)} is not in the two-space JSON layout this tool rewrites; "
                          f"add the keys by hand")
        insertions: dict[str, list[tuple[str, str]]] = {}
        template_key = f"item.{template_ns}.{template_path}"
        insertions.setdefault(template_key, []).append(
            (item_key, f"{PLACEHOLDER} {document.get(template_key, weapon_id)}"))
        if new_style:
            last = template_style["moves"][-1]["display_key"]
            for move, new_id in zip(template_style["moves"], names.move_ids):
                insertions.setdefault(last, []).append(
                    (display_key(new_id), f"{PLACEHOLDER} {document.get(move['display_key'], new_id)}"))
        updated: dict[str, str] = {}
        for key, value in document.items():
            updated[key] = value
            for new_key, new_value in insertions.pop(key, []):
                updated[new_key] = new_value
        for leftover in insertions.values():  # anchor missing in this file: append at the end
            for new_key, new_value in leftover:
                updated[new_key] = new_value
        added = [key for key in updated if key not in document]
        plan.changes.append((path, render_lang(updated), "adds " + ", ".join(added)))

    plan.manual = manual_steps(root, plan, names, template, template_style)
    verify_plan(plan, names)
    return plan


def verify_plan(plan: Plan, names: Names) -> None:
    """The planned files parse with the real reader and agree on every id (a scaffold bug, not a
    user error, if this fails)."""
    for path, text in plan.writes + [(p, t) for p, t, _ in plan.changes]:
        json.loads(text)
    root = plan.root
    weapon_text = next(text for path, text in plan.writes if path == combat_data.weapon_file(root, plan.weapon_id))
    problems = [str(issue) for issue in combat_data.validate_weapon(json.loads(weapon_text), "weapon")]
    if plan.new_style:
        style_text = next(text for path, text in plan.writes if path == combat_data.style_file(root, plan.style_id))
        style = json.loads(style_text)
        problems += [str(issue) for issue in combat_data.validate_style(style, "style")]
        if [move["id"] for move in style["moves"]] != list(names.move_ids):
            problems.append("style move ids differ from the derived ids")
    if problems:
        raise RuntimeError("scaffold produced invalid data: " + "; ".join(problems))


def manual_steps(root: Path, plan: Plan, names: Names, template: dict, template_style: dict) -> list[tuple[str, str]]:
    """The playbook steps the scaffold leaves to a person, in playbook order, with files and places."""
    namespace, item_path = combat_data.split_id(plan.weapon_id)
    template_ns, template_path = combat_data.split_id(template["item"])
    holder = item_path.upper()
    weapon_rel = rel(root, combat_data.weapon_file(root, plan.weapon_id))
    rig_rel = rel(root, combat_data.asset_file(root, names.rig_id))
    geometry_rel = rel(root, combat_data.asset_file(root, names.geometry_id))
    style_rel = rel(root, combat_data.style_file(root, plan.style_id))
    contracts = combat_data.asset_file(root, template["geometry"])
    try:
        template_contract = read_json(contracts)
    except (OSError, ValueError):
        template_contract = {}
    template_generator = template_contract.get("generator", "tools/gen_<template>_model.py")
    two_handed = "off_hand_grip_center" in template_contract
    generator = f"tools/gen_{item_path}_model.py"
    out: list[tuple[str, str]] = []

    contract_template = root / ITEM_CONTRACTS / f"{template_path}.json"
    out.append(("item-contract",
                f"Write {ITEM_CONTRACTS}/{item_path}.json"
                + (f", starting from a copy of {rel(root, contract_template)}" if contract_template.is_file() else "")
                + f": item_id {plan.weapon_id}, kind functional_item, names, creative-tab place, the "
                f"CombatWeaponItem registration and attributes, behaviour, tests, acceptance; it must "
                f"match {ITEM_CONTRACT_SCHEMA}."))

    found = template_registration(root, template_path)
    if found is not None:
        template_holder, declaration = found
        snippet = declaration.replace(template_holder, holder, 1).replace(f'"{template_path}"', f'"{item_path}"', 1)
        registration = (f"add after the {template_holder} declaration:\n\n    {snippet}\n\n  and in the creative "
                        f"tab, after output.accept({template_holder}.get());, add output.accept({holder}.get());. "
                        f"The attributes are the template's; set the new weapon's.")
    else:
        registration = (f"register \"{item_path}\" as new CombatWeaponItem(Tiers.<tier>, props.attributes(...)) "
                        f"and add it to the creative tab.")
    out.append(("item-registration", f"In {MOD_ITEMS}, {registration}"))

    tag_path = root / SWORD_TAG
    try:
        tag_values = read_json(tag_path).get("values", [])
    except (OSError, ValueError, AttributeError):
        tag_values = []
    if template["item"] in tag_values:
        out.append(("item-tag",
                    f"After the item is registered, add \"{plan.weapon_id}\" after \"{template['item']}\" to "
                    f"\"values\" in {SWORD_TAG} (a tag that names an unregistered item fails to load in game)."))

    out.append(("model-and-contract",
                f"Write {generator} (start from {template_generator}), the only writer of "
                f"{RESOURCES}/assets/{namespace}/models/item/{item_path}.json (a neoforge:separate_transforms "
                f"wrapper: the 3D model as base, the 2D icon myvillage:item/{item_path} in gui), the 3D model "
                f"myvillage:item/{item_path}_3d, textures/item/{item_path}.png (64x64 with alpha), its model "
                f"texture (textures/item/{item_path}_*.png), and {geometry_rel} (format 2: units, generator "
                f"\"{generator}\", model, axes, grip_center, handle, collar, butt, head, head_base, head_tip, "
                f"edge_axis, flat_axis, overall_y"
                + (", and off_hand_grip_center because the template is two-handed (its rig has rig.off_hand)"
                   if two_handed else "")
                + "; trail optional). Run it, then python3 " + generator + " --check."))

    if plan.new_style:
        out.append(("style-data",
                    f"Author the moves in {style_rel}: every number is a placeholder copied from "
                    f"{template_style['id']} (timing, chain, damage, targets, range, reaction, step, feedback, "
                    f"camera)."))

    keys = [f"item.{namespace}.{item_path}"] + ([display_key(move_id) for move_id in names.move_ids]
                                                 if plan.new_style else [])
    out.append(("names",
                f"Replace the \"{PLACEHOLDER} ...\" values of {', '.join(keys)} in every "
                f"{RESOURCES}/assets/{namespace}/lang/*.json."))

    out.append(("hit-samples",
                (f"Author the hit samples of {style_rel}" if plan.new_style else
                 f"Check the shared style {plan.style_id}")
                + f" against this weapon: every drawn frame of each cut's world trail must reach the weapon's "
                f"trail tip radius (validator COMBAT_TRAIL_CUT_REACH); add a move's \"trail\": "
                f"{{\"samples\": ...}} to draw the trail apart from the hit volume"
                + ("" if plan.new_style else "; the style's samples serve every weapon of the style, so they "
                   "must reach the longest one's radius")
                + "."))

    if plan.new_style:
        out.append(("third-person-poses",
                    f"Add a PoseTable for {plan.style_id} to POSE_TABLES in {PAL_GENERATOR} (start from the "
                    f"{template_style['id']} table; output player_animations/{combat_data.split_id(plan.style_id)[1]}"
                    f"_combat.json; geometry {geometry_rel}; item_model the 3D model; moves keyed "
                    f"{', '.join(names.move_ids)}"
                    + ("; two_handed=True" if two_handed else "")
                    + f"), then run python3 {PAL_GENERATOR} and python3 {PAL_GENERATOR} --check. Never edit "
                    f"the PAL file by hand."))
    else:
        out.append(("third-person-poses",
                    f"No new pose table: {plan.style_id}'s table was solved for {template['item']}. Make this "
                    f"weapon's grip land where that weapon's does after its thirdperson_righthand display "
                    f"(progress checks it), and look at the poses with python3 -m tools.combat_preview pose "
                    f"--item myvillage:item/{item_path}."))

    out.append(("first-person-rig",
                f"Author {rig_rel} (a copy of {template['first_person_rig']}): rig.weapon_scale, rig.shoulder, "
                f"rig.arm"
                + (", rig.off_hand" if two_handed else "")
                + ", neutral, and every move's keys, strike (covering the server active ticks; see the "
                f"validator's MAX_STRIKE_TICKS) and contact."))

    out.append(("parity-golden",
                f"Rewrite {PARITY_GOLDEN}: {GRADLE} test --tests {PARITY_TEST_JAVA} -PupdatePreviewParity "
                f"-x generateAllStructures --console=plain, then .venv-preview/bin/python -m unittest "
                f"tools.tests.test_combat_preview_parity."))

    out.append(("item-validator-pin",
                f"In tools/validate_mod_items.py add (\"{item_path}\", \"{holder}\", <en_us name>, <zh_cn name>, "
                f"\"{item_path}\", <binary-alpha icon?>, <(attack bonus, \"speed F\")>) to SWORD_CONTRACTS, "
                f"\"{holder}\" to CREATIVE_SWORD_ORDER in its tab place, and \"{item_path}\": "
                f"\"myvillage:item/{item_path}_3d\" to SWORD_3D_MODELS."))

    out.append(("tests",
                f"Add tools/tests/test_gen_{item_path}_model.py for {generator}; add the new ids to the pinned "
                f"lists in " + " and ".join(PINNED_TESTS) + "; Java (not checked here): the style and weapon "
                f"constants in src/test/java/com/example/myvillage/combat/definition/CombatTestData.java"
                + (f" and a style test like BasicSpearStyleTest for {plan.style_id}" if plan.new_style else "")
                + "."))

    out.append(("release-gate-step",
                f"In tools/release_gate.py STEPS add generator_check(\"gen_{item_path}_model\") after "
                f"generator_check(\"{Path(template_generator).stem}\")."))

    jar_lines = [f"assets/{namespace}/models/item/{item_path}.json",
                 f"assets/{namespace}/combat/{item_path}_geometry.json",
                 f"assets/{namespace}/combat/{item_path}_first_person.json"]
    if plan.new_style:
        jar_lines.append(f"assets/{namespace}/player_animations/{combat_data.split_id(plan.style_id)[1]}_combat.json")
    out.append(("readme",
                f"In README.md add jar tf ... | grep lines for {', '.join(jar_lines)} (and the 3D model and "
                f"textures) to both jar listings, and a section with /give @s {plan.weapon_id}."))
    return out


def apply_plan(plan: Plan) -> None:
    """Write the plan; re-checks that no new file exists first."""
    existing = [rel(plan.root, path) for path, _ in plan.writes if path.exists()]
    if existing:
        raise Refusal("refused, nothing written: " + ", ".join(existing) + " exist")
    for path, text in plan.writes:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    for path, text, _ in plan.changes:
        path.write_text(text, encoding="utf-8")


def print_plan(plan: Plan, dry_run: bool, out=None) -> None:
    out = out or sys.stdout
    verb = "Would write" if dry_run else "Wrote"
    change = "Would change" if dry_run else "Changed"
    print(f"{'Dry run: ' if dry_run else ''}{plan.weapon_id} from {plan.template_id}, style {plan.style_id}"
          f"{' (new)' if plan.new_style else ' (shared with the template)'}", file=out)
    for path, _ in plan.writes:
        print(f"  {verb} {rel(plan.root, path)}", file=out)
    for path, _, what in plan.changes:
        print(f"  {change} {rel(plan.root, path)}: {what}", file=out)
    print(f"\nManual steps, in the order of {PLAYBOOK}:", file=out)
    for step_id, text in plan.manual:
        print(f"\n  [{step_label(step_id)}] {text}", file=out)
    print("\nThen the process steps the tool cannot check: "
          + ", ".join(step_label(step_id) for step_id in PROCESS_AFTER_FACTS) + ".", file=out)
    print(f"\nProgress: python3 tools/new_combat_weapon.py progress {plan.weapon_id}", file=out)


# ---------------------------------------------------------------------------------------------
# Progress.
# ---------------------------------------------------------------------------------------------

DONE, PLACEHOLDER_STATE, MISSING, BLOCKED, NOT_APPLICABLE = "done", "placeholder", "missing", "blocked", "n/a"
OPEN_STATES = (PLACEHOLDER_STATE, MISSING, BLOCKED)


@dataclass
class Result:
    step: str
    status: str
    details: list[str] = field(default_factory=list)


def canonical_style(style: dict) -> str:
    """A style without its ids: two styles with the same canonical text differ only in names."""
    doc = copy.deepcopy(style)
    doc.pop("id", None)
    doc.pop("animations", None)
    for move in doc.get("moves", []):
        move.pop("id", None)
        move.pop("display_key", None)
    return json.dumps(doc, sort_keys=True)


def canonical_rig(rig: Any, move_ids: list[str]) -> str:
    """A rig with its moves in combo order instead of keyed by id."""
    if not isinstance(rig, dict):
        return json.dumps(rig, sort_keys=True)
    doc = {key: value for key, value in rig.items() if key != "moves"}
    moves = rig.get("moves") if isinstance(rig.get("moves"), dict) else {}
    doc["moves"] = [moves.get(move_id) for move_id in move_ids]
    return json.dumps(doc, sort_keys=True)


class Progress:
    """The facts of one weapon, checked in playbook order."""

    def __init__(self, root: Path, weapon_id: str, fast: bool = False, python: str | None = None):
        self.root = Path(root)
        self.weapon_id = weapon_id
        self.fast = fast
        self.python = python or sys.executable
        self.namespace, self.item_path = combat_data.split_id(weapon_id)
        self.data = combat_data.load(self.root)
        self.weapon_path = combat_data.weapon_file(self.root, weapon_id)
        self.weapon = self.data.weapons.get(weapon_id)
        if self.weapon is None and self.weapon_path.is_file():
            try:
                raw = read_json(self.weapon_path)
                self.weapon = raw if isinstance(raw, dict) else None
            except ValueError:
                self.weapon = None
        self.style_id = self.weapon.get("style") if self.weapon else None
        self.style = self.data.styles.get(self.style_id) if self.style_id else None
        others = [w for other, w in self.data.weapons.items() if other != weapon_id]
        self.own_style = bool(self.style_id) and not any(w["style"] == self.style_id for w in others)
        self.move_ids = [move["id"] for move in self.style["moves"]] if self.style else []
        self.rig_path = (combat_data.asset_file(self.root, self.weapon["first_person_rig"])
                         if self.weapon and isinstance(self.weapon.get("first_person_rig"), str) else None)
        self.geometry_path = (combat_data.asset_file(self.root, self.weapon["geometry"])
                              if self.weapon and isinstance(self.weapon.get("geometry"), str) else None)
        self.contract = None
        if self.geometry_path is not None and self.geometry_path.is_file():
            try:
                self.contract = read_json(self.geometry_path)
            except ValueError:
                self.contract = None
        generator = self.contract.get("generator") if isinstance(self.contract, dict) else None
        self.generator = generator if isinstance(generator, str) and re.match(r"^tools/[A-Za-z0-9_]+\.py$", generator) else None
        self.findings, self.mod_item_errors = self._run_validators()
        self.claimed: set[int] = set()  # indexes of validator findings sorted into a step
        self.claimed_mod: set[int] = set()

    # --- the existing checks ----------------------------------------------------------------

    def _run_validators(self):
        validator = load_module(self.root, "validate_sword_combat_foundation")
        findings = [] if validator is None else [(f.code, f.detail)
                                                  for f in validator.validate(self.root, not self.fast)]
        mod_items = load_module(self.root, "validate_mod_items")
        try:
            errors = [] if mod_items is None else list(mod_items.validate())
        except (OSError, ValueError) as exc:
            errors = [f"validate_mod_items failed: {exc}"]
        return findings, errors

    def take(self, codes: tuple[str, ...], *needles: str, prefix: bool = False) -> list[str]:
        """Validator findings with one of ``codes`` whose detail contains any needle; claimed."""
        out = []
        for index, (code, detail) in enumerate(self.findings):
            if any(code == c or (prefix and code.startswith(c)) for c in codes) \
                    and any(needle and needle in detail for needle in needles):
                self.claimed.add(index)
                out.append(f"{code}: {detail}")
        return out

    def take_mod(self, *needles: str) -> list[str]:
        out = []
        for index, error in enumerate(self.mod_item_errors):
            if any(needle and needle in error for needle in needles):
                self.claimed_mod.add(index)
                out.append(f"validate_mod_items: {error}")
        return out

    def rel(self, path: Path | None) -> str:
        return rel(self.root, path) if path is not None else "?"

    # --- steps ------------------------------------------------------------------------------

    def run(self) -> list[Result]:
        results = []
        for step in STEPS:
            if not step.checked:
                continue
            method = getattr(self, "check_" + step.id.replace("-", "_"))
            results.append(method())
        return results

    def check_item_contract(self) -> Result:
        path = self.root / ITEM_CONTRACTS / f"{self.item_path}.json"
        if not path.is_file():
            return Result("item-contract", MISSING, [f"{self.rel(path)} does not exist"])
        try:
            contract = read_json(path)
        except ValueError as exc:
            return Result("item-contract", MISSING, [f"{self.rel(path)}: {exc}"])
        if not isinstance(contract, dict) or contract.get("item_id") != self.weapon_id:
            return Result("item-contract", MISSING, [f"{self.rel(path)}: item_id is not {self.weapon_id}"])
        if PLACEHOLDER in path.read_text(encoding="utf-8"):
            return Result("item-contract", PLACEHOLDER_STATE, [f"{self.rel(path)} still has {PLACEHOLDER}"])
        return Result("item-contract", DONE, [self.rel(path)])

    def check_item_registration(self) -> Result:
        problems = self.take(("COMBAT_WEAPON_ITEM_UNREGISTERED",), f"{self.weapon_id}:")
        problems += self.take_mod(f"combat_weapon_not_CombatWeaponItem:{self.item_path}")
        if problems:
            return Result("item-registration", MISSING, problems)
        return Result("item-registration", DONE, [f"{self.item_path} registered as CombatWeaponItem"])

    def check_item_tag(self) -> Result:
        try:
            values = read_json(self.root / SWORD_TAG).get("values", [])
        except (OSError, ValueError, AttributeError):
            values = []
        others = [w["item"] for other, w in self.data.weapons.items() if other != self.weapon_id]
        if self.weapon_id in values:
            return Result("item-tag", DONE, [f"in {SWORD_TAG}"])
        if others and all(item in values for item in others):
            return Result("item-tag", MISSING, [f"every other combat weapon is in {SWORD_TAG}; this one is not"])
        return Result("item-tag", NOT_APPLICABLE, [f"the other combat weapons are not all in {SWORD_TAG}"])

    def check_model_and_contract(self) -> Result:
        problems = []
        if self.geometry_path is None:
            problems.append("no weapon file names a geometry contract")
        else:
            problems += self.take(("COMBAT_GEOMETRY_CONTRACT", "COMBAT_GEOMETRY_OFF_HAND_GRIP", "COMBAT_GEOMETRY_TRAIL"),
                                  self.rel(self.geometry_path))
        problems += self.take(("COMBAT_WEAPON_ITEM_MODEL", "COMBAT_WEAPON_MODEL_MISSING",
                               "COMBAT_WEAPON_TEXTURE_MISSING", "COMBAT_WEAPON_MODEL_3D"), f"{self.weapon_id}:")
        # A rig with rig.off_hand on a contract without off_hand_grip_center: the contract lacks it.
        for index, (code, detail) in enumerate(self.findings):
            if code == "COMBAT_FIRST_PERSON_RIG_OFF_HAND" and detail.startswith(f"{self.weapon_id}:") \
                    and "has no off_hand_grip_center" in detail:
                self.claimed.add(index)
                problems.append(f"{code}: {detail}")
        if self.generator:
            problems += self.take(("COMBAT_WEAPON_MODEL_GENERATOR_DRIFT",), f"{self.generator}:")
        problems += self.take_mod(f"models/item/{self.item_path}.json", f"textures/item/{self.item_path}")
        if problems:
            return Result("model-and-contract", MISSING, problems)
        details = [f"{self.rel(self.geometry_path)} from {self.generator}"]
        if self.fast:
            details.append(f"{self.generator} --check not run (--fast)")
        return Result("model-and-contract", DONE, details)

    def check_weapon_data(self) -> Result:
        problems = [f"COMBAT_DATA_{issue.code}: {issue}" for issue in self.data.issues
                    if issue.file == self.rel(self.weapon_path)
                    or (issue.code == "INDEX" and self.weapon_id in issue.message)]
        for index, (code, detail) in enumerate(self.findings):
            if code.startswith("COMBAT_DATA_") and (self.rel(self.weapon_path) in detail):
                self.claimed.add(index)
        if not self.weapon_path.is_file():
            problems.insert(0, f"{self.rel(self.weapon_path)} does not exist")
        elif self.weapon_id not in self.data.index.get("weapons", ()):
            problems.append(f"{self.weapon_id} is not listed in {combat_data.INDEX_FILE}")
        if problems:
            return Result("weapon-data", MISSING, problems)
        return Result("weapon-data", DONE, [f"{self.rel(self.weapon_path)}, style {self.style_id}"])

    def _other_rigs(self) -> dict[str, str]:
        out = {}
        for other, weapon in self.data.weapons.items():
            if other == self.weapon_id:
                continue
            style = self.data.styles.get(weapon["style"])
            try:
                rig = read_json(combat_data.asset_file(self.root, weapon["first_person_rig"]))
            except (OSError, ValueError):
                continue
            out[other] = canonical_rig(rig, [m["id"] for m in style["moves"]] if style else [])
        return out

    def check_style_data(self) -> Result:
        if self.style_id is None:
            return Result("style-data", MISSING, ["no weapon file"])
        if not self.own_style:
            return Result("style-data", NOT_APPLICABLE, [f"{self.style_id} is shared with another weapon"])
        style_file = combat_data.style_file(self.root, self.style_id)
        problems = [f"COMBAT_DATA_{issue.code}: {issue}" for issue in self.data.issues
                    if issue.file == self.rel(style_file) and issue.code not in ("SAMPLE_ORDER", "SAMPLE_COUNT")]
        for index, (code, detail) in enumerate(self.findings):
            if code.startswith("COMBAT_DATA_") and self.rel(style_file) in detail \
                    and code not in ("COMBAT_DATA_SAMPLE_ORDER", "COMBAT_DATA_SAMPLE_COUNT"):
                self.claimed.add(index)
        problems += self.take(("COMBAT_SOUND_EVENT",), *self.move_ids)
        if self.style is None:
            return Result("style-data", MISSING, problems or [f"{self.style_id} does not load"])
        if problems:
            return Result("style-data", MISSING, problems)
        mine = canonical_style(self.style)
        copies = [other for other, style in self.data.styles.items()
                  if other != self.style_id and canonical_style(style) == mine]
        if copies:
            return Result("style-data", PLACEHOLDER_STATE,
                          [f"every move of {self.style_id} is still identical to {copies[0]}'s (copied placeholder)"])
        details = [f"{self.style_id} loads, {len(self.move_ids)} moves"]
        for other, style in self.data.styles.items():
            if other == self.style_id:
                continue
            same = sum(1 for a, b in zip(json.loads(mine)["moves"], json.loads(canonical_style(style))["moves"]) if a == b)
            if same:
                details.append(f"{same} move(s) still identical to {other}'s")
        return Result("style-data", DONE, details)

    def check_names(self) -> Result:
        keys = [f"item.{self.namespace}.{self.item_path}"]
        if self.own_style and self.style:
            keys += [move["display_key"] for move in self.style["moves"]]
        problems = self.take_mod(f"missing_lang:item.{self.namespace}.{self.item_path}|")
        if self.own_style:
            problems += self.take(("COMBAT_TRANSLATIONS",), *keys[1:])
        placeholders = []
        for path in lang_files(self.root, self.namespace):
            try:
                language = read_json(path)
            except ValueError as exc:
                problems.append(f"{self.rel(path)}: {exc}")
                continue
            for key in keys:
                value = language.get(key)
                if value is None:
                    problems.append(f"{key} missing from {self.rel(path)}")
                elif isinstance(value, str) and value.startswith(PLACEHOLDER):
                    placeholders.append(f"{path.name}: {key}")
        if problems:
            return Result("names", MISSING, problems)
        if placeholders:
            return Result("names", PLACEHOLDER_STATE, [f"{len(placeholders)} value(s) still start with "
                                                       f"{PLACEHOLDER}: " + ", ".join(placeholders)])
        return Result("names", DONE, [f"{len(keys)} key(s) in every lang file"])

    def check_hit_samples(self) -> Result:
        style_file = combat_data.style_file(self.root, self.style_id) if self.style_id else None
        problems = self.take(("COMBAT_TRAIL_CUT_REACH",), f"{self.weapon_id} ")
        if style_file is not None:
            problems += self.take(("COMBAT_DATA_SAMPLE_ORDER", "COMBAT_DATA_SAMPLE_COUNT"), self.rel(style_file))
        if problems:
            return Result("hit-samples", MISSING, problems)
        validator = load_module(self.root, "validate_sword_combat_foundation")
        radius = validator.trail_tip_radius(self.root, self.contract) if isinstance(self.contract, dict) else None
        if radius is None:
            return Result("hit-samples", BLOCKED, ["needs the geometry contract and 3D model "
                                                   "(step `model-and-contract`) to know the trail tip radius"])
        return Result("hit-samples", DONE, [f"every cut reaches the trail tip radius {radius:.3f} blocks"])

    def check_third_person_poses(self) -> Result:
        if self.style_id is None:
            return Result("third-person-poses", MISSING, ["no weapon file"])
        try:
            generator = load_module(self.root, "gen_sword_pal_anims")
        except Exception as exc:  # the generator raises on invalid combat data at import time only if built so
            return Result("third-person-poses", MISSING, [f"{PAL_GENERATOR} does not import: {exc}"])
        tables = {table.style_id: table for table in generator.POSE_TABLES} if generator else {}
        table = tables.get(self.style_id)
        if self.own_style:
            problems = []
            if table is None:
                problems.append(f"POSE_TABLES in {PAL_GENERATOR} has no table for {self.style_id}")
            elif self.geometry_path is not None and table.geometry.resolve() != self.geometry_path.resolve():
                problems.append(f"the {self.style_id} table measures {self.rel(table.geometry)}, not "
                                f"{self.rel(self.geometry_path)}")
            problems += self.take(("COMBAT_ANIMATION_", "COMBAT_READY_IDLE_LOOP"), self.style_id, *self.move_ids,
                                  *(self.style["animations"].values() if self.style else ()), prefix=True)
            problems += self.take(("COMBAT_PAL_GENERATOR_DRIFT",), PAL_GENERATOR)
            if problems:
                return Result("third-person-poses", MISSING, problems)
            details = [f"{self.rel(table.output)} from the {self.style_id} table"]
            if self.fast:
                details.append(f"{PAL_GENERATOR} --check not run (--fast)")
            return Result("third-person-poses", DONE, details)
        # A shared style: this weapon is drawn with poses solved for the table's weapon.
        if table is None:
            return Result("third-person-poses", MISSING, [f"POSE_TABLES has no table for {self.style_id}"])
        if not isinstance(self.contract, dict) or self.contract.get("model") is None:
            return Result("third-person-poses", BLOCKED, ["needs the geometry contract and 3D model "
                                                          "(step `model-and-contract`)"])
        try:
            model_ns, model_path = combat_data.split_id(self.contract["model"])
            model = self.root / RESOURCES / "assets" / model_ns / "models" / f"{model_path}.json"
            mine = generator.load_rig(self.geometry_path, model)
            theirs = generator.load_rig(table.geometry, table.item_model)
        except Exception as exc:
            return Result("third-person-poses", BLOCKED, [f"the weapon rig does not load: {exc}"])

        def held(rig):
            chain = generator._chain(*rig.display)
            grip = generator._apply_raw(chain, rig.grip)
            tip = generator._apply_raw(chain, rig.tip)
            axis = [t - g for t, g in zip(tip, grip)]
            length = math.sqrt(sum(v * v for v in axis)) or 1.0
            return grip, [v / length for v in axis]

        grip_a, axis_a = held(mine)
        grip_b, axis_b = held(theirs)
        offset_px = math.dist(grip_a, grip_b) * 16.0
        angle = math.degrees(math.acos(max(-1.0, min(1.0, sum(a * b for a, b in zip(axis_a, axis_b))))))
        detail = (f"grip {offset_px:.2f} px and axis {angle:.2f} degrees from {self.rel(table.item_model)}'s "
                  f"after thirdperson_righthand (limits {GRIP_TOLERANCE_PX} px, {AXIS_TOLERANCE_DEG} degrees)")
        if offset_px > GRIP_TOLERANCE_PX or angle > AXIS_TOLERANCE_DEG:
            return Result("third-person-poses", MISSING, [detail])
        return Result("third-person-poses", DONE, [detail])

    def check_first_person_rig(self) -> Result:
        if self.rig_path is None:
            return Result("first-person-rig", MISSING, ["no weapon file names a rig"])
        rig_name = self.rel(self.rig_path)
        problems = self.take(("COMBAT_FIRST_PERSON_RIG_MISSING", "COMBAT_FIRST_PERSON_RIG_JSON",
                              "COMBAT_FIRST_PERSON_RIG_ARM", "COMBAT_FIRST_PERSON_RIG_MOVES",
                              "COMBAT_FIRST_PERSON_RIG_OFF_HAND"), rig_name, f"{self.weapon_id}:")
        # Key, neutral, strike and contact findings name only the move (validate_rig): with a shared
        # style they may also come from the other weapon's rig.
        problems += self.take(("COMBAT_FIRST_PERSON_RIG_KEYS", "COMBAT_FIRST_PERSON_RIG_NEUTRAL",
                               "COMBAT_FIRST_PERSON_RIG_STRIKE", "COMBAT_FIRST_PERSON_RIG_CONTACT"),
                              *[f"{move_id}" for move_id in self.move_ids])
        if problems:
            return Result("first-person-rig", MISSING, problems)
        try:
            rig = read_json(self.rig_path)
        except (OSError, ValueError) as exc:
            return Result("first-person-rig", MISSING, [f"{rig_name}: {exc}"])
        mine = canonical_rig(rig, self.move_ids)
        copies = [other for other, text in self._other_rigs().items() if text == mine]
        if copies:
            return Result("first-person-rig", PLACEHOLDER_STATE,
                          [f"{rig_name} is still identical to {copies[0]}'s rig (copied placeholder)"])
        return Result("first-person-rig", DONE, [rig_name])

    def check_parity_golden(self) -> Result:
        path = self.root / PARITY_GOLDEN
        try:
            golden = read_json(path)
            weapons = [entry["weapon"] for entry in golden["weapons"]]
        except (OSError, ValueError, KeyError, TypeError) as exc:
            return Result("parity-golden", MISSING, [f"{PARITY_GOLDEN}: {exc}"])
        index = list(self.data.index.get("weapons", []))
        if self.weapon_id not in weapons or weapons != index:
            return Result("parity-golden", MISSING, [f"{PARITY_GOLDEN} covers {', '.join(weapons)}; the index lists "
                                                     f"{', '.join(index)}"])
        return Result("parity-golden", DONE, [f"{PARITY_GOLDEN} covers {self.weapon_id} (its values are checked by "
                                              f"FirstPersonPreviewParityTest and test_combat_preview_parity)"])

    def check_item_validator_pin(self) -> Result:
        module = load_module(self.root, "validate_mod_items")
        contracts = {entry[0]: entry for entry in getattr(module, "SWORD_CONTRACTS", ())}
        entry = contracts.get(self.item_path)
        if entry is None:
            return Result("item-validator-pin", MISSING, [f"{self.item_path} is not in SWORD_CONTRACTS of "
                                                          f"tools/validate_mod_items.py"])
        problems = []
        if entry[1] not in getattr(module, "CREATIVE_SWORD_ORDER", ()):
            problems.append(f"{entry[1]} is not in CREATIVE_SWORD_ORDER")
        model = item_model_file(self.root, self.weapon_id)
        try:
            is_3d = read_json(model).get("loader") == "neoforge:separate_transforms"
        except (OSError, ValueError, AttributeError):
            is_3d = False
        if is_3d and self.item_path not in getattr(module, "SWORD_3D_MODELS", {}):
            problems.append(f"{self.item_path} is a 3D model but not in SWORD_3D_MODELS")
        problems += self.take_mod(f"{entry[4]}_", f":{self.item_path}")
        if problems:
            return Result("item-validator-pin", MISSING, problems)
        return Result("item-validator-pin", DONE, [f"SWORD_CONTRACTS pins {self.item_path}"])

    def check_tests(self) -> Result:
        problems = []
        if self.generator is None:
            problems.append("needs the model generator (step `model-and-contract`) to name its test")
        else:
            test = self.root / "tools/tests" / f"test_{Path(self.generator).stem}.py"
            if not test.is_file():
                problems.append(f"{self.rel(test)} does not exist")
        details = ["Java tests are not checked here (they need Gradle)"]
        problems += [f"{name} fails" for name in self._failing_pinned_tests()]
        if problems:
            status = BLOCKED if self.generator is None and len(problems) == 1 else MISSING
            return Result("tests", status, problems + details)
        return Result("tests", DONE, [f"tools/tests/test_{Path(self.generator).stem}.py exists"] + details)

    def _failing_pinned_tests(self) -> list[str]:
        failed = []
        for name in PINNED_TESTS:
            module_file = self.root / (name.rsplit(".", 2)[0].replace(".", "/") + ".py")
            if not module_file.is_file():
                failed.append(f"{name} (no {self.rel(module_file)})")
                continue
            result = subprocess.run([self.python, "-m", "unittest", name], cwd=self.root,
                                    capture_output=True, text=True, timeout=600, check=False)
            if result.returncode != 0:
                failed.append(name)
        return failed

    def check_release_gate_step(self) -> Result:
        if self.generator is None:
            return Result("release-gate-step", BLOCKED, ["needs the model generator (step `model-and-contract`)"])
        script = self.root / "tools/release_gate.py"
        if not script.is_file():
            return Result("release-gate-step", MISSING, ["tools/release_gate.py does not exist"])
        result = subprocess.run([self.python, str(script), "--list"], cwd=self.root,
                                capture_output=True, text=True, timeout=120, check=False)
        wanted = f"{self.generator} --check"
        if result.returncode != 0 or not any(wanted in line for line in result.stdout.splitlines()):
            return Result("release-gate-step", MISSING, [f"tools/release_gate.py --list has no step running {wanted}"])
        return Result("release-gate-step", DONE, [f"release gate runs {wanted}"])

    def check_readme(self) -> Result:
        readme = self.root / "README.md"
        text = readme.read_text(encoding="utf-8") if readme.is_file() else ""
        wanted = [f"assets/{self.namespace}/models/item/{self.item_path}.json"]
        if self.weapon:
            for asset in ("geometry", "first_person_rig"):
                if isinstance(self.weapon.get(asset), str):
                    wanted.append(combat_data.asset_rel(self.weapon[asset]))
        if self.own_style and self.style_id:
            try:
                generator = load_module(self.root, "gen_sword_pal_anims")
                table = next((t for t in generator.POSE_TABLES if t.style_id == self.style_id), None)
            except Exception:
                table = None
            if table is not None:
                wanted.append(f"assets/{self.namespace}/player_animations/{table.output.name}")
            else:
                wanted.append(f"assets/{self.namespace}/player_animations/<the {self.style_id} pose table's output>")
        problems = [f"no 'jar tf ... | grep \"{line}\"' line" for line in wanted
                    if not re.search(rf'jar tf \S+ \| grep "{re.escape(line)}"', text)]
        if f"/give @s {self.weapon_id}" not in text:
            problems.append(f"no /give @s {self.weapon_id}")
        if problems:
            return Result("readme", MISSING, problems)
        return Result("readme", DONE, [f"{len(wanted)} jar lines and the /give command"])

    # --- the rest ---------------------------------------------------------------------------

    def unclaimed(self) -> list[str]:
        return ([f"{code}: {detail}" for index, (code, detail) in enumerate(self.findings) if index not in self.claimed]
                + [f"validate_mod_items: {error}" for index, error in enumerate(self.mod_item_errors)
                   if index not in self.claimed_mod])


def report(root: Path, weapon_id: str, fast: bool = False, python: str | None = None) -> dict:
    progress = Progress(root, weapon_id, fast=fast, python=python)
    results = progress.run()
    return {
        "weapon": weapon_id,
        "style": progress.style_id,
        "own_style": progress.own_style,
        "steps": [{"number": STEP_NUMBER[r.step], "id": r.step, "title": next(s.title for s in STEPS if s.id == r.step),
                   "status": r.status, "details": r.details} for r in results],
        "missing": [r.step for r in results if r.status in OPEN_STATES],
        "process": list(PROCESS_AFTER_FACTS),
        "other_findings": progress.unclaimed(),
        "fast": fast,
    }


def print_report(result: dict, out=None) -> None:
    out = out or sys.stdout
    style = result["style"] or "?"
    print(f"{result['weapon']}: style {style} ({'own' if result['own_style'] else 'shared'}); "
          f"steps from {PLAYBOOK}" + (" (--fast: generator --check runs skipped)" if result["fast"] else ""), file=out)
    for step in result["steps"]:
        print(f"  {step['number']:>2} {step['id']:<20} {step['status'].upper():<12}"
              f"{step['details'][0] if step['details'] else ''}", file=out)
        for detail in step["details"][1:]:
            print(f"  {'':<35}{detail}", file=out)
    print("\nMissing, in playbook order: " + (", ".join(result["missing"]) or "none"), file=out)
    print("Process steps this tool cannot check: " + ", ".join(result["process"]), file=out)
    if result["other_findings"]:
        print(f"\nValidator findings not about this weapon ({len(result['other_findings'])}):", file=out)
        for finding in result["other_findings"]:
            print(f"  {finding}", file=out)


# ---------------------------------------------------------------------------------------------
# Command line.
# ---------------------------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="python3 tools/new_combat_weapon.py",
        description=f"Scaffold a new combat weapon from an existing one, or report a weapon's progress "
                    f"through the playbook ({PLAYBOOK}).",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "examples:\n"
            "  python3 tools/new_combat_weapon.py scaffold --from myvillage:qingfeng_sword --id myvillage:jade_sword --dry-run\n"
            "  python3 tools/new_combat_weapon.py scaffold --from myvillage:lingxiao_spear --id myvillage:iron_halberd \\\n"
            "      --style myvillage:basic_halberd --moves thrust,sweep,hook,chop,lunge\n"
            "  python3 tools/new_combat_weapon.py progress myvillage:iron_halberd\n"))
    parser.add_argument("--root", type=Path, default=ROOT, help="repository root to work on (default: this one)")
    commands = parser.add_subparsers(dest="command", required=True)

    scaffold = commands.add_parser(
        "scaffold", help="write the derivable files of a new weapon and print the manual steps",
        description="Derive a new weapon from a template weapon: weapon file, index entries, first-person rig "
                    "copy, translation placeholders, and with --style a re-identified copy of the template's "
                    "style. Prints every remaining manual step. Refuses taken ids and existing files.")
    scaffold.add_argument("--from", dest="template", required=True, metavar="WEAPON_ID",
                          help="the template weapon (a weapon id in data/myvillage/combat/index.json)")
    scaffold.add_argument("--id", dest="weapon_id", required=True, metavar="ITEM_ID",
                          help="the new weapon's id, which is also its item id (myvillage:<name>)")
    scaffold.add_argument("--style", metavar="STYLE_ID",
                          help="a new style id (myvillage:<name>) copied from the template's style; omit to "
                               "share the template's style")
    scaffold.add_argument("--moves", metavar="A,B,...",
                          help="with --style: one name per template move; move ids become <style>_NN_<name>")
    scaffold.add_argument("--dry-run", action="store_true", help="print every write, change, and manual step; write nothing")

    progress = commands.add_parser(
        "progress", help="report which playbook facts of a weapon exist and pass",
        description="Check a weapon's facts in playbook order with the existing validators, the PAL "
                    "generator, the release gate's step list, and the pinned-list tests.")
    progress.add_argument("weapon_id", metavar="WEAPON_ID")
    progress.add_argument("--fast", action="store_true",
                          help="skip the generator --check runs inside the combat validator")
    progress.add_argument("--json", action="store_true", help="print the report as JSON")
    progress.add_argument("--python", default=None,
                          help="interpreter for the tests and the release gate listing (default: this one)")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    root = args.root.resolve()
    if args.command == "scaffold":
        moves = [name.strip() for name in args.moves.split(",")] if args.moves else None
        try:
            plan = plan_scaffold(root, args.template, args.weapon_id, args.style, moves)
            if not args.dry_run:
                apply_plan(plan)
        except Refusal as exc:
            print(f"new_combat_weapon: {exc}", file=sys.stderr)
            return 2
        print_plan(plan, args.dry_run)
        return 0
    try:
        combat_data.split_id(args.weapon_id)
    except ValueError as exc:
        print(f"new_combat_weapon: {exc}", file=sys.stderr)
        return 2
    result = report(root, args.weapon_id, fast=args.fast, python=args.python)
    if args.json:
        print(json.dumps(result, indent=2, ensure_ascii=False))
    else:
        print_report(result)
    return 1 if result["missing"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
