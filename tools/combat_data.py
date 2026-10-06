#!/usr/bin/env python3
"""Read and validate the bundled combat data (styles and weapons, schema version 1).

The combat move sets live in ``src/main/resources/data/<ns>/combat/``, listed by
``data/myvillage/combat/index.json`` (see the ``add-combat-data-infrastructure`` design, D1-D3).
This module is the one Python reader of those files.  It uses only the standard library.

API
---
``load(root=ROOT) -> CombatData``
    Read the index, every listed style and weapon, and the style/weapon directories.  Never
    raises on bad data: every problem becomes an :class:`Issue` in ``CombatData.issues``, and
    only files that validated cleanly appear in ``CombatData.styles`` / ``CombatData.weapons``.
``load_strict(root=ROOT) -> CombatData``
    Same, but raises :class:`CombatDataError` (listing every issue) when there is any issue.
``validate_style(document, file) -> list[Issue]`` / ``validate_weapon(document, file) -> list[Issue]``
    Check one parsed document against the schema and the move invariants.
``expand_samples(move) -> list[dict]`` / ``expand_trail_samples(move) -> list[dict]``
    A validated move's hit samples, or the samples its world trail draws (the optional
    ``trail.samples``, else the hit samples), as explicit samples (a generator's output, or the
    list).
``split_id(value) -> (namespace, path)``, ``style_file(root, id)``, ``weapon_file(root, id)``,
``asset_file(root, id)``
    Resolve resource ids to repository paths.
``geometry_format_problems(contract) -> list[str]`` / ``rig_format_problems(rig) -> list[str]``
    Why a weapon's geometry contract is not format 2 or its first-person rig still uses a renamed
    field (``GEOMETRY_RENAMED_FIELDS``, ``GEOMETRY_RENAMED_AXES``, ``RIG_RENAMED_FIELDS``).

Styles and weapons are returned as the validated JSON objects, so field names are the ones
in the design (``total_ticks``, ``active_ticks``, ``chain_tick``, ``step.tick``, ...).
An :class:`Issue` names the repository-relative file and the dotted field path.
"""

from __future__ import annotations

import json
import math
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Iterator

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = "src/main/resources"
INDEX_FILE = f"{RESOURCES}/data/myvillage/combat/index.json"
SCHEMA_VERSION = 1
MOVE_KINDS = ("thrust", "cut")
ID_PATTERN = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")


# ---------------------------------------------------------------------------------------------
# Results.
# ---------------------------------------------------------------------------------------------

@dataclass(frozen=True)
class Issue:
    """One problem in the combat data.

    ``code`` is one of ``JSON``, ``MISSING_FILE``, ``SCHEMA``, ``UNKNOWN_FIELD``, ``INVARIANT``,
    ``DUPLICATE``, ``INDEX`` (index and directory disagree), ``REFERENCE`` (an id that does
    not resolve), ``SAMPLE_ORDER`` (explicit hit or trail samples whose ticks go backwards) or
    ``SAMPLE_COUNT`` (uneven explicit samples per active tick).  ``file`` is relative to the
    repository root; ``field`` is a dotted path such as ``moves[2].chain_tick`` (empty for a
    whole-file problem).
    """

    code: str
    file: str
    field: str
    message: str

    def __str__(self) -> str:
        where = f"{self.file}: {self.field}" if self.field else self.file
        return f"{where}: {self.message}"


class CombatDataError(ValueError):
    """Raised by :func:`load_strict`; ``issues`` holds every problem found."""

    def __init__(self, issues: list[Issue]):
        self.issues = list(issues)
        super().__init__("invalid combat data:\n" + "\n".join(f"  {issue}" for issue in self.issues))


@dataclass(frozen=True)
class CombatData:
    """The loaded combat data.

    ``styles`` and ``weapons`` map ids to validated documents in index order; files with any
    issue are left out.  ``files`` maps every listed id to its path (present or not).
    """

    root: Path
    index: dict
    styles: dict[str, dict] = field(default_factory=dict)
    weapons: dict[str, dict] = field(default_factory=dict)
    files: dict[str, Path] = field(default_factory=dict)
    issues: tuple[Issue, ...] = ()

    def moves(self) -> Iterator[tuple[str, dict]]:
        """Every move of every valid style as ``(style_id, move)``, in index and combo order."""
        for style_id, style in self.styles.items():
            for move in style["moves"]:
                yield style_id, move

    def relative(self, path: Path) -> str:
        return _relative(self.root, path)


# ---------------------------------------------------------------------------------------------
# Ids and paths.
# ---------------------------------------------------------------------------------------------

def split_id(value: str) -> tuple[str, str]:
    """``"ns:path"`` -> ``("ns", "path")``; raises ``ValueError`` for anything else."""
    if not isinstance(value, str) or not ID_PATTERN.match(value):
        raise ValueError(f"not a resource id: {value!r}")
    namespace, path = value.split(":", 1)
    if path.startswith("/") or ".." in path.split("/"):
        raise ValueError(f"not a resource id: {value!r}")
    return namespace, path


def style_rel(style_id: str) -> str:
    """Path of a style file relative to a resources root (``src/main/resources`` or a jar)."""
    namespace, path = split_id(style_id)
    return f"data/{namespace}/combat/style/{path}.json"


def weapon_rel(weapon_id: str) -> str:
    """Path of a weapon file relative to a resources root."""
    namespace, path = split_id(weapon_id)
    return f"data/{namespace}/combat/weapon/{path}.json"


def asset_rel(asset_id: str) -> str:
    """A client asset location such as ``myvillage:combat/x.json`` -> ``assets/myvillage/combat/x.json``."""
    namespace, path = split_id(asset_id)
    return f"assets/{namespace}/{path}"


def style_file(root: Path, style_id: str) -> Path:
    return root / RESOURCES / style_rel(style_id)


def weapon_file(root: Path, weapon_id: str) -> Path:
    return root / RESOURCES / weapon_rel(weapon_id)


def asset_file(root: Path, asset_id: str) -> Path:
    return root / RESOURCES / asset_rel(asset_id)


def _relative(root: Path, path: Path) -> str:
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        return path.as_posix()


# ---------------------------------------------------------------------------------------------
# Geometry contract and first-person rig names (client assets, read by the Java client).
# ---------------------------------------------------------------------------------------------

GEOMETRY_FORMAT = 2
# Format 1 named the contract after a sword; format 2 uses weapon-neutral names.  An old name is
# never read as a fallback: a contract that still has one is rejected, naming the new field.
GEOMETRY_RENAMED_FIELDS = {
    "pommel": "butt",
    "guard": "collar",
    "blade": "head",
    "blade_base": "head_base",
    "blade_tip": "head_tip",
}
GEOMETRY_RENAMED_AXES = {"blade": "length"}
# Unknown rig fields are ignored by the game, so an old rig field would silently fall back to its
# default; the renamed ones are rejected instead.
RIG_RENAMED_FIELDS = {"sword_scale": "weapon_scale"}


def geometry_format_problems(geometry: Any) -> list[str]:
    """Why a geometry contract is not format 2, as WeaponGeometry.parse rejects it: a missing or
    other ``format``, or a format 1 field name (each message names the format 2 field)."""
    if not isinstance(geometry, dict):
        return ["the geometry contract must be an object"]
    problems = []
    if geometry.get("format") != GEOMETRY_FORMAT:
        problems.append(f"format is {geometry.get('format')!r}, expected {GEOMETRY_FORMAT} (format 2 renamed "
                        + ", ".join([f"axes.{old} to axes.{new}" for old, new in GEOMETRY_RENAMED_AXES.items()]
                                    + [f"{old} to {new}" for old, new in GEOMETRY_RENAMED_FIELDS.items()])
                        + ")")
    for old, new in GEOMETRY_RENAMED_FIELDS.items():
        if old in geometry:
            problems.append(f"{old} was renamed {new} in format {GEOMETRY_FORMAT}")
    axes = geometry.get("axes")
    for old, new in GEOMETRY_RENAMED_AXES.items():
        if isinstance(axes, dict) and old in axes:
            problems.append(f"axes.{old} was renamed axes.{new} in format {GEOMETRY_FORMAT}")
    return problems


def rig_format_problems(rig: Any) -> list[str]:
    """Renamed fields a first-person rig still uses, as FirstPersonSwing.parse rejects them."""
    settings = rig.get("rig") if isinstance(rig, dict) else None
    if not isinstance(settings, dict):
        return []
    return [f"rig.{old} was renamed rig.{new}" for old, new in RIG_RENAMED_FIELDS.items() if old in settings]


# ---------------------------------------------------------------------------------------------
# Schema checking.
# ---------------------------------------------------------------------------------------------

class _Checker:
    def __init__(self, file: str):
        self.file = file
        self.issues: list[Issue] = []

    def fail(self, code: str, path: str, message: str) -> None:
        self.issues.append(Issue(code, self.file, path, message))


Rule = Callable[[_Checker, Any, str], bool]


def _join(path: str, name: str) -> str:
    return f"{path}.{name}" if path else name


def _is_number(value: Any) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def integer(minimum: int | None = None) -> Rule:
    def rule(c: _Checker, value: Any, path: str) -> bool:
        if not isinstance(value, int) or isinstance(value, bool):
            c.fail("SCHEMA", path, f"must be an integer, got {value!r}")
            return False
        if minimum is not None and value < minimum:
            c.fail("SCHEMA", path, f"must be >= {minimum}, got {value}")
            return False
        return True
    return rule


def number(minimum: float | None = None, positive: bool = False) -> Rule:
    def rule(c: _Checker, value: Any, path: str) -> bool:
        if not _is_number(value):
            c.fail("SCHEMA", path, f"must be a finite number, got {value!r}")
            return False
        if positive and not value > 0:
            c.fail("SCHEMA", path, f"must be > 0, got {value}")
            return False
        if minimum is not None and value < minimum:
            c.fail("SCHEMA", path, f"must be >= {minimum}, got {value}")
            return False
        return True
    return rule


def boolean(c: _Checker, value: Any, path: str) -> bool:
    if not isinstance(value, bool):
        c.fail("SCHEMA", path, f"must be true or false, got {value!r}")
        return False
    return True


def text(c: _Checker, value: Any, path: str) -> bool:
    if not isinstance(value, str) or not value.strip():
        c.fail("SCHEMA", path, f"must be a non-blank string, got {value!r}")
        return False
    return True


def resource_id(c: _Checker, value: Any, path: str) -> bool:
    if not isinstance(value, str) or not ID_PATTERN.match(value):
        c.fail("SCHEMA", path, f"must be a namespaced id like 'myvillage:name', got {value!r}")
        return False
    return True


FAMILY_PATTERN = re.compile(r"[a-z][a-z0-9_]*\Z")


def weapon_family(c: _Checker, value: Any, path: str) -> bool:
    """A weapon family (``WeaponDefinition.family``): lower_snake_case, like ``fist``.  Whether a
    school names it is checked by the validator, which reads the school files."""
    if not isinstance(value, str) or not FAMILY_PATTERN.match(value):
        c.fail("SCHEMA", path, f"must be a lower_snake_case weapon family like 'fist', got {value!r}")
        return False
    return True


def one_of(*values: str) -> Rule:
    def rule(c: _Checker, value: Any, path: str) -> bool:
        if value not in values:
            c.fail("SCHEMA", path, f"must be one of {', '.join(values)}, got {value!r}")
            return False
        return True
    return rule


def constant(expected: Any) -> Rule:
    def rule(c: _Checker, value: Any, path: str) -> bool:
        if value != expected or isinstance(value, bool):
            c.fail("SCHEMA", path, f"must be {expected!r}, got {value!r}")
            return False
        return True
    return rule


def vector(size: int, item: Rule) -> Rule:
    def rule(c: _Checker, value: Any, path: str) -> bool:
        if not isinstance(value, list) or len(value) != size:
            c.fail("SCHEMA", path, f"must be a list of {size} values, got {value!r}")
            return False
        return all([item(c, element, f"{path}[{i}]") for i, element in enumerate(value)])
    return rule


def id_list(c: _Checker, value: Any, path: str) -> bool:
    if not isinstance(value, list):
        c.fail("SCHEMA", path, f"must be a list of ids, got {value!r}")
        return False
    ok = all([resource_id(c, element, f"{path}[{i}]") for i, element in enumerate(value)])
    seen: set[str] = set()
    for i, element in enumerate(value):
        if not isinstance(element, str):
            continue
        if element in seen:
            c.fail("DUPLICATE", f"{path}[{i}]", f"{element} is listed twice")
            ok = False
        seen.add(element)
    return ok


def obj(required: dict[str, Rule], optional: dict[str, Rule] | None = None) -> Rule:
    """An object with exactly these fields: missing required and unknown fields are issues."""
    optional = optional or {}

    def rule(c: _Checker, value: Any, path: str) -> bool:
        if not isinstance(value, dict):
            c.fail("SCHEMA", path, f"must be an object, got {type(value).__name__}")
            return False
        ok = True
        for name in value:
            if name not in required and name not in optional:
                known = ", ".join(sorted({*required, *optional}))
                c.fail("UNKNOWN_FIELD", _join(path, name), f"unknown field (known: {known})")
                ok = False
        for name, child in required.items():
            if name not in value:
                c.fail("SCHEMA", _join(path, name), "required field is missing")
                ok = False
            elif not child(c, value[name], _join(path, name)):
                ok = False
        for name, child in optional.items():
            if name in value and not child(c, value[name], _join(path, name)):
                ok = False
        return ok
    return rule


VEC3 = vector(3, number())

EXPLICIT_SAMPLE = obj({
    "tick": integer(0),
    "start": VEC3,
    "end": VEC3,
    "horizontal_radius": number(positive=True),
    "vertical_radius": number(positive=True),
})

SAMPLE_GENERATORS: dict[str, Rule] = {
    "thrust": obj({"generator": constant("thrust"), "first_range": number(positive=True),
                   "final_range": number(positive=True), "radius": number(positive=True)}),
    "arc": obj({"generator": constant("arc"), "range": number(positive=True), "start_angle": number(),
                "end_angle": number(), "height": number(), "radius": number(positive=True)}),
    "diagonal": obj({"generator": constant("diagonal"), "descending": boolean,
                     "radius": number(positive=True)}),
}


def samples(c: _Checker, value: Any, path: str) -> bool:
    """Either a non-empty list of explicit samples or one generator object."""
    if isinstance(value, list):
        if not value:
            c.fail("SCHEMA", path, "must not be empty")
            return False
        return all([EXPLICIT_SAMPLE(c, sample, f"{path}[{i}]") for i, sample in enumerate(value)])
    if isinstance(value, dict):
        generator = value.get("generator")
        if generator not in SAMPLE_GENERATORS:
            c.fail("SCHEMA", _join(path, "generator"),
                   f"must be one of {', '.join(SAMPLE_GENERATORS)}, got {generator!r}")
            return False
        return SAMPLE_GENERATORS[generator](c, value, path)
    c.fail("SCHEMA", path, "must be a list of samples or a generator object")
    return False


MOVE = obj(
    {
        "id": resource_id,
        "display_key": text,
        "kind": one_of(*MOVE_KINDS),
        "total_ticks": integer(1),
        "active_ticks": vector(2, integer(0)),
        "buffer_start_tick": integer(0),
        "chain_tick": integer(0),
        "damage_multiplier": number(positive=True),
        "maximum_targets": integer(1),
        "range": number(positive=True),
        "reaction": obj({"hitstun_ticks": integer(0), "slide_distance": number(0.0), "lift": number(0.0),
                         "lateral_bias": number()}),
        "hitbox": obj({"shape_family": text, "horizontal_tolerance": number(0.0),
                       "vertical_tolerance": number(0.0), "samples": samples}),
        "feedback": obj(
            {"swing_sound": resource_id, "swing_pitch": number(positive=True), "hit_sound": resource_id,
             "heavy_hit": boolean, "hit_stop_ticks": number(0.0), "camera_trauma": number(0.0),
             "cut_roll_degrees": number()},
            {"heavy_layer_sound": resource_id}),
        "camera": obj({"hit_pitch_kick": number(), "hit_roll_kick": number(), "hit_fov_punch": number(),
                       "swing_lean_degrees": number(), "step_fov_surge": number()}),
    },
    {
        "step": obj({"tick": integer(0), "maximum_distance": number(positive=True),
                     "support_depth": number(positive=True)}),
        # Presentation only: the samples the world trail follows instead of the hit samples.
        "trail": obj({"samples": samples}),
    })


def move_list(c: _Checker, value: Any, path: str) -> bool:
    if not isinstance(value, list) or not value:
        c.fail("SCHEMA", path, "must be a non-empty list of moves")
        return False
    return all([MOVE(c, move, f"{path}[{i}]") for i, move in enumerate(value)])


STYLE = obj({
    "schema": constant(SCHEMA_VERSION),
    "id": resource_id,
    "combo_timeout_ticks": integer(1),
    "minimum_intent_interval_ticks": integer(1),
    "animations": obj({"ready_idle": resource_id, "mode_enter": resource_id}),
    "moves": move_list,
})

WEAPON = obj({
    "schema": constant(SCHEMA_VERSION),
    "item": resource_id,
    "style": resource_id,
    "first_person_rig": resource_id,
    "geometry": resource_id,
}, {
    # The weapon family a school's weapon_family names (sword, spear, fist); no runtime reads it yet.
    "family": weapon_family,
    # 0.39.1: one item worn as a pair (the gauntlet); the client mirrors it onto an empty off hand.
    "paired": boolean,
})

INDEX = obj({"schema": constant(SCHEMA_VERSION), "styles": id_list, "weapons": id_list})


def _move_invariants(c: _Checker, move: dict, path: str) -> None:
    """The timing invariants of ``AttackMoveDefinition`` plus the step and sample ticks."""
    total = move["total_ticks"]
    start, end = move["active_ticks"]
    buffer_start = move["buffer_start_tick"]
    chain = move["chain_tick"]
    if not 0 <= start <= end < total:
        c.fail("INVARIANT", _join(path, "active_ticks"),
               f"needs 0 <= start <= end < total_ticks ({total}), got [{start}, {end}]")
    if not start <= buffer_start < total:
        c.fail("INVARIANT", _join(path, "buffer_start_tick"),
               f"needs active start ({start}) <= buffer_start_tick < total_ticks ({total}), got {buffer_start}")
    if not end < chain <= total:
        c.fail("INVARIANT", _join(path, "chain_tick"),
               f"needs active end ({end}) < chain_tick <= total_ticks ({total}), got {chain}")
    step = move.get("step")
    if step is not None and not 0 <= step["tick"] < total:
        c.fail("INVARIANT", _join(path, "step.tick"),
               f"must lie inside the move (0..{total - 1}), got {step['tick']}")
    _sample_invariants(c, move, move["hitbox"]["samples"], _join(path, "hitbox.samples"))
    trail = move.get("trail")
    if trail is not None:
        _sample_invariants(c, move, trail["samples"], _join(path, "trail.samples"))


def _sample_invariants(c: _Checker, move: dict, sample_list: Any, path: str) -> None:
    """The explicit-sample rules, for the hit samples and for the optional trail samples alike."""
    if not isinstance(sample_list, list):
        return
    start, end = move["active_ticks"]
    for i, sample in enumerate(sample_list):
        if not start <= sample["tick"] <= end:
            c.fail("INVARIANT", f"{path}[{i}].tick",
                   f"must lie inside the active ticks [{start}, {end}], got {sample['tick']}")
        # The world trail walks the samples in list order and keeps the list order within a
        # tick, so the ticks may repeat but never go back.
        if i > 0 and sample["tick"] < sample_list[i - 1]["tick"]:
            c.fail("SAMPLE_ORDER", f"{path}[{i}].tick",
                   f"explicit samples must be in non-decreasing tick order; {move['id']} "
                   f"samples[{i}] has tick {sample['tick']} after samples[{i - 1}] at tick "
                   f"{sample_list[i - 1]['tick']}")
    # The world trail spaces the n samples of one tick 1/n of a tick apart (CombatWorldTrails
    # .sampleTime), so once a tick carries several samples every active tick needs the same
    # number, or the drawn sweep changes speed at the tick boundaries.  A tick without samples
    # counts as zero.  One sample per tick (or a gap between single samples) sits on its tick.
    counts = {tick: 0 for tick in range(start, end + 1)}
    for sample in sample_list:
        if sample["tick"] in counts:
            counts[sample["tick"]] += 1
    if max(counts.values(), default=0) > 1 and len(set(counts.values())) > 1:
        per_tick = counts[start] if counts[start] > 0 else max(counts.values())
        shown = ", ".join(f"tick {tick}: {count}" for tick, count in counts.items())
        c.fail("SAMPLE_COUNT", path,
               f"{move['id']} has uneven samples per active tick ({shown}); once a tick carries "
               f"several samples, give every active tick {start}..{end} the same number (for "
               f"example {per_tick}), because the world trail spaces the n samples of a tick 1/n "
               f"of a tick apart")


def expand_samples(move: dict) -> list[dict]:
    """A validated move's hit samples as explicit samples, in tick order: the list itself, or the
    output of its generator (a port of ``HitboxGenerators.java``, same constants)."""
    return _expand(move, move["hitbox"]["samples"])


def expand_trail_samples(move: dict) -> list[dict]:
    """The samples a validated move's world trail draws (``AttackMoveDefinition.worldTrailSamples``):
    its optional ``trail.samples``, else its hit samples, expanded like :func:`expand_samples`."""
    trail = move.get("trail")
    return expand_samples(move) if trail is None else _expand(move, trail["samples"])


def _expand(move: dict, samples: Any) -> list[dict]:
    if isinstance(samples, list):
        return samples
    start, end = move["active_ticks"]
    out = []
    for tick in range(start, end + 1):
        progress = (tick - start) / max(1, end - start)
        if samples["generator"] == "thrust":
            reach = samples["first_range"] + (samples["final_range"] - samples["first_range"]) * progress
            near, far, vertical = [0.0, 1.05, 0.55], [0.0, 1.20, reach], 0.25
        elif samples["generator"] == "arc":
            angle = math.radians(samples["start_angle"] + (samples["end_angle"] - samples["start_angle"]) * progress)
            height, length = samples["height"], samples["range"]
            near = [math.sin(angle) * 0.45, height, math.cos(angle) * 0.45]
            far, vertical = [math.sin(angle) * length, height, math.cos(angle) * length], 0.34
        else:  # diagonal
            side, opposite = -0.75 + 1.50 * progress, 0.95 - 1.90 * progress
            low, high = 0.45 + 0.30 * progress, 1.90 - 0.15 * progress
            if samples["descending"]:
                near, far, vertical = [-side, high, 0.55], [-opposite, low, 2.75], 0.22
            else:
                near, far, vertical = [side, low, 0.55], [opposite, high, 2.55], 0.20
        out.append({"tick": tick, "start": near, "end": far,
                    "horizontal_radius": samples["radius"], "vertical_radius": vertical})
    return out


def validate_style(document: Any, file: str) -> list[Issue]:
    """Schema, unique move ids within the style, and the move invariants."""
    c = _Checker(file)
    if not STYLE(c, document, ""):
        # Invariants need well-typed fields; check them only on moves that passed the schema.
        moves = document.get("moves") if isinstance(document, dict) else None
        if not isinstance(moves, list):
            return c.issues
    else:
        moves = document["moves"]
    seen: dict[str, int] = {}
    for i, move in enumerate(moves):
        probe = _Checker(file)
        if not MOVE(probe, move, ""):
            continue
        _move_invariants(c, move, f"moves[{i}]")
        if move["id"] in seen:
            c.fail("DUPLICATE", f"moves[{i}].id", f"{move['id']} repeats moves[{seen[move['id']]}]")
        seen.setdefault(move["id"], i)
    return c.issues


def validate_weapon(document: Any, file: str) -> list[Issue]:
    c = _Checker(file)
    WEAPON(c, document, "")
    return c.issues


# ---------------------------------------------------------------------------------------------
# Loading.
# ---------------------------------------------------------------------------------------------

class _DuplicateKey(ValueError):
    pass


def _unique_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    """A repeated key would silently keep only its last value; reject it instead."""
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise _DuplicateKey(f"duplicate key {key!r}")
        result[key] = value
    return result


def _read_json(root: Path, path: Path, issues: list[Issue]) -> Any:
    file = _relative(root, path)
    if not path.is_file():
        issues.append(Issue("MISSING_FILE", file, "", "file is missing"))
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=_unique_keys)
    except (json.JSONDecodeError, UnicodeDecodeError, _DuplicateKey) as exc:
        issues.append(Issue("JSON", file, "", f"invalid JSON: {exc}"))
        return None


def _on_disk(root: Path, kind: str) -> dict[str, Path]:
    """Ids of every ``data/<ns>/combat/<kind>/**/*.json`` file."""
    found: dict[str, Path] = {}
    data_root = root / RESOURCES / "data"
    if not data_root.is_dir():
        return found
    for namespace_dir in sorted(p for p in data_root.iterdir() if p.is_dir()):
        kind_dir = namespace_dir / "combat" / kind
        if not kind_dir.is_dir():
            continue
        for path in sorted(kind_dir.rglob("*.json")):
            found[f"{namespace_dir.name}:{path.relative_to(kind_dir).with_suffix('').as_posix()}"] = path
    return found


def load(root: Path = ROOT) -> CombatData:
    """Load and validate everything; problems are collected, never raised."""
    root = Path(root)
    issues: list[Issue] = []
    index_path = root / INDEX_FILE
    index_name = _relative(root, index_path)
    index = _read_json(root, index_path, issues)
    if index is None:
        return CombatData(root, {}, issues=tuple(issues))
    checker = _Checker(index_name)
    if not INDEX(checker, index, ""):
        issues.extend(checker.issues)
        return CombatData(root, index if isinstance(index, dict) else {}, issues=tuple(issues))

    files: dict[str, Path] = {}
    documents: dict[str, dict[str, Any]] = {"styles": {}, "weapons": {}}
    for key, kind, resolve, validate in (
            ("styles", "style", style_file, validate_style),
            ("weapons", "weapon", weapon_file, validate_weapon)):
        listed = index[key]
        disk = _on_disk(root, kind)
        for unlisted in sorted(set(disk) - set(listed)):
            issues.append(Issue("INDEX", _relative(root, disk[unlisted]), "",
                                f"{kind} file is not listed in {index_name} ({key}: {unlisted})"))
        for i, item_id in enumerate(listed):
            path = resolve(root, item_id)
            files[item_id] = path
            if item_id not in disk:
                issues.append(Issue("INDEX", index_name, f"{key}[{i}]",
                                    f"{item_id} has no file at {_relative(root, path)}"))
                continue
            document = _read_json(root, path, issues)
            if document is None:
                continue
            found = validate(document, _relative(root, path))
            if kind == "style" and isinstance(document, dict) and document.get("id") != item_id \
                    and not any(issue.field == "id" for issue in found):
                found.append(Issue("INDEX", _relative(root, path), "id",
                                   f"is {document.get('id')!r} but the index lists the file as {item_id}"))
            issues.extend(found)
            if not found:
                documents[key][item_id] = document

    styles, weapons = documents["styles"], documents["weapons"]

    # Move ids are unique across styles (they are PAL animation ids and rig keys).
    owners: dict[str, str] = {}
    for style_id, style in styles.items():
        for i, move in enumerate(style["moves"]):
            other = owners.get(move["id"])
            if other is not None and other != style_id:
                issues.append(Issue("DUPLICATE", _relative(root, files[style_id]), f"moves[{i}].id",
                                    f"{move['id']} is also declared by style {other}"))
            owners.setdefault(move["id"], style_id)

    # Weapons reference listed styles, and an item selects at most one weapon.
    items: dict[str, str] = {}
    for weapon_id, weapon in list(weapons.items()):
        file = _relative(root, files[weapon_id])
        if weapon["style"] not in index["styles"]:
            issues.append(Issue("REFERENCE", file, "style", f"{weapon['style']} is not listed in {index_name}"))
        elif weapon["style"] not in styles:
            issues.append(Issue("REFERENCE", file, "style", f"{weapon['style']} did not load"))
        other = items.get(weapon["item"])
        if other is not None:
            issues.append(Issue("DUPLICATE", file, "item", f"{weapon['item']} is also the item of weapon {other}"))
        items.setdefault(weapon["item"], weapon_id)

    return CombatData(root, index, styles, weapons, files, tuple(issues))


def load_strict(root: Path = ROOT) -> CombatData:
    """Load and raise :class:`CombatDataError` if anything is wrong."""
    data = load(root)
    if data.issues:
        raise CombatDataError(list(data.issues))
    return data


def main() -> int:
    data = load()
    for issue in data.issues:
        print(f"COMBAT_DATA_{issue.code} {issue}")
    if data.issues:
        return 1
    moves = sum(len(style["moves"]) for style in data.styles.values())
    print(f"combat data ok: {len(data.styles)} style(s), {moves} move(s), {len(data.weapons)} weapon(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
