"""Minimal reader for the bundled combat data (index, weapon, style) and the
first-person rig. Pure functions over JSON; no Minecraft or host programs.

Ids follow D1 of add-combat-data-infrastructure: ``ns:path`` maps to
``data/<ns>/combat/style/<path>.json`` or ``data/<ns>/combat/weapon/<path>.json``.
A weapon's ``first_person_rig`` is an asset location ``ns:path`` that maps to
``assets/<ns>/<path>``.
"""
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field
from pathlib import Path

INDEX_REL = "data/myvillage/combat/index.json"

# Column order of every stills sheet; see key_ticks().
KEY_NAMES = ("idle", "strike_start", "contact", "strike_end", "recovery")


class DataError(ValueError):
    pass


def split_id(ident: str) -> tuple[str, str]:
    if not isinstance(ident, str) or ident.count(":") != 1:
        raise DataError(f"expected an id like ns:path, got {ident!r}")
    ns, path = ident.split(":", 1)
    if not ns or not path or path.startswith("/") or ".." in path.split("/"):
        raise DataError(f"invalid id {ident!r}")
    return ns, path


def style_rel(style_id: str) -> str:
    ns, path = split_id(style_id)
    return f"data/{ns}/combat/style/{path}.json"


def weapon_rel(weapon_id: str) -> str:
    ns, path = split_id(weapon_id)
    return f"data/{ns}/combat/weapon/{path}.json"


def asset_rel(location: str) -> str:
    """Asset location ``ns:combat/x.json`` -> ``assets/ns/combat/x.json``."""
    ns, path = split_id(location)
    return f"assets/{ns}/{path}"


def load_json(path: Path):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise DataError(f"missing file {path}") from None
    except ValueError as e:
        raise DataError(f"invalid JSON in {path}: {e}") from None


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def key_ticks(rig_move: dict) -> list[tuple[str, float]]:
    """Named key ticks of one rig move: idle 0, strike start = strike[0],
    contact, strike end = strike[1], recovery = the last key before the final
    neutral key (the last key if the move does not end on a neutral key)."""
    try:
        strike = rig_move["strike"]
        contact = float(rig_move["contact"])
        keys = rig_move["keys"]
        start, end = float(strike[0]), float(strike[1])
    except (KeyError, TypeError, IndexError, ValueError) as e:
        raise DataError(f"rig move needs strike [a, b], contact and keys ({e})") from None
    if not keys:
        raise DataError("rig move has no keys")
    last = keys[-1]
    if last.get("pose") == "neutral" and len(keys) >= 2:
        recovery = float(keys[-2]["tick"])
    else:
        recovery = float(last["tick"])
    return [("idle", 0.0), ("strike_start", start), ("contact", contact),
            ("strike_end", end), ("recovery", recovery)]


def short_name(move_id: str) -> str:
    """``myvillage:basic_sword_02_horizontal_cut`` -> ``02_horizontal_cut``."""
    path = move_id.split(":", 1)[-1]
    parts = path.split("_")
    for i, p in enumerate(parts):
        if p.isdigit():
            return "_".join(parts[i:])
    return path


@dataclass
class Move:
    index: int  # one-based within the style, as the smoke probes take it
    id: str
    kind: str
    total_ticks: int
    active_ticks: list
    buffer_start_tick: int
    chain_tick: int
    keys: list = field(default_factory=list)  # [(name, tick)]

    @property
    def short(self) -> str:
        return short_name(self.id)


@dataclass
class Weapon:
    id: str
    item: str
    style_id: str
    rig_location: str
    files: dict  # role -> repo-relative path (for provenance)
    moves: list  # [Move]
    combo_timeout_ticks: int


def load_weapon(resources: Path, weapon_id: str) -> Weapon:
    """Read index -> weapon -> style -> rig from a resources root such as
    ``src/main/resources``. Every move of the style needs a rig entry."""
    resources = Path(resources)
    index = load_json(resources / INDEX_REL)
    if weapon_id not in index.get("weapons", []):
        raise DataError(f"{weapon_id} is not listed in {INDEX_REL} (weapons: {index.get('weapons')})")
    w_rel = weapon_rel(weapon_id)
    weapon = load_json(resources / w_rel)
    style_id = weapon.get("style")
    if style_id not in index.get("styles", []):
        raise DataError(f"style {style_id!r} of {weapon_id} is not listed in {INDEX_REL}")
    s_rel = style_rel(style_id)
    style = load_json(resources / s_rel)
    rig_loc = weapon.get("first_person_rig")
    r_rel = asset_rel(rig_loc)
    rig = load_json(resources / r_rel)
    rig_moves = rig.get("moves", {})
    moves = []
    for i, m in enumerate(style.get("moves", []), start=1):
        mid = m["id"]
        if mid not in rig_moves:
            raise DataError(f"move {mid} of {style_id} has no entry in rig {r_rel}")
        moves.append(Move(index=i, id=mid, kind=m.get("kind", ""), total_ticks=int(m["total_ticks"]),
                          active_ticks=list(m["active_ticks"]), buffer_start_tick=int(m["buffer_start_tick"]),
                          chain_tick=int(m["chain_tick"]), keys=key_ticks(rig_moves[mid])))
    if not moves:
        raise DataError(f"style {style_id} has no moves")
    return Weapon(id=weapon_id, item=weapon["item"], style_id=style_id, rig_location=rig_loc,
                  files={"index": INDEX_REL, "weapon": w_rel, "style": s_rel, "rig": r_rel},
                  moves=moves, combo_timeout_ticks=int(style.get("combo_timeout_ticks", 0)))


def fmt_tick(t: float) -> str:
    return f"{float(t):g}"
