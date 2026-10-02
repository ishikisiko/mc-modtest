"""Reader for one weapon's capture inputs (index, weapon, style, first-person rig)
from any resources root, such as ``src/main/resources``. Pure functions over JSON;
no Minecraft or host programs. Id-to-path rules come from ``tools/combat_data.py``.
"""
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field
from pathlib import Path

from tools import combat_data

INDEX_REL = "data/myvillage/combat/index.json"

# Column order of every stills sheet; see key_ticks().
KEY_NAMES = ("idle", "strike_start", "contact", "strike_end", "recovery")


class DataError(ValueError):
    pass


def split_id(ident: str) -> tuple[str, str]:
    try:
        return combat_data.split_id(ident)
    except ValueError:
        raise DataError(f"expected an id like ns:path, got {ident!r}") from None


def style_rel(style_id: str) -> str:
    split_id(style_id)
    return combat_data.style_rel(style_id)


def weapon_rel(weapon_id: str) -> str:
    split_id(weapon_id)
    return combat_data.weapon_rel(weapon_id)


def asset_rel(location: str) -> str:
    """Asset location ``ns:combat/x.json`` -> ``assets/ns/combat/x.json``."""
    split_id(location)
    return combat_data.asset_rel(location)


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
    # Third-person held length in blocks (see held_length_blocks), None without a geometry contract.
    held_length: float | None = None


def model_rel(location: str) -> str:
    """Model location ``ns:item/x`` -> ``assets/ns/models/item/x.json``."""
    ns, path = split_id(location)
    return f"assets/{ns}/models/{path}.json"


def held_length_blocks(geometry: dict, model: dict | None) -> float:
    """Length of the weapon as drawn in a third-person hand, in blocks: the
    geometry contract's ``overall_y`` span (model pixels) times the model's
    ``thirdperson_righthand`` scale along the blade axis (1 if absent), /16."""
    try:
        y0, y1 = (float(v) for v in geometry["overall_y"])
    except (KeyError, TypeError, ValueError) as e:
        raise DataError(f"geometry needs overall_y [min, max] ({e})") from None
    scale = 1.0
    display = ((model or {}).get("display") or {}).get("thirdperson_righthand") or {}
    if isinstance(display.get("scale"), list) and len(display["scale"]) == 3:
        scale = float(display["scale"][1])
    return abs(y1 - y0) * scale / 16.0


def load_weapon(resources: Path, weapon_id: str, require_rig: bool = True) -> Weapon:
    """Read index -> weapon -> style -> rig from a resources root such as
    ``src/main/resources``. Every move of the style needs a rig entry. With
    ``require_rig=False`` a missing rig file is allowed (moves then have no key
    ticks); scene and camera commands use that, the stills never do."""
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
    files = {"index": INDEX_REL, "weapon": w_rel, "style": s_rel, "rig": r_rel}
    if require_rig or (resources / r_rel).is_file():
        rig_moves = load_json(resources / r_rel).get("moves", {})
    else:
        rig_moves = None
        del files["rig"]
    moves = []
    for i, m in enumerate(style.get("moves", []), start=1):
        mid = m["id"]
        if rig_moves is not None and mid not in rig_moves:
            raise DataError(f"move {mid} of {style_id} has no entry in rig {r_rel}")
        moves.append(Move(index=i, id=mid, kind=m.get("kind", ""), total_ticks=int(m["total_ticks"]),
                          active_ticks=list(m["active_ticks"]), buffer_start_tick=int(m["buffer_start_tick"]),
                          chain_tick=int(m["chain_tick"]),
                          keys=key_ticks(rig_moves[mid]) if rig_moves is not None else []))
    if not moves:
        raise DataError(f"style {style_id} has no moves")
    held = None
    if weapon.get("geometry"):
        g_rel = asset_rel(weapon["geometry"])
        geometry = load_json(resources / g_rel)
        files["geometry"] = g_rel
        model = None
        if geometry.get("model"):
            m_path = resources / model_rel(geometry["model"])
            model = load_json(m_path) if m_path.is_file() else None
        held = round(held_length_blocks(geometry, model), 3)
    return Weapon(id=weapon_id, item=weapon["item"], style_id=style_id, rig_location=rig_loc,
                  files=files, moves=moves, combo_timeout_ticks=int(style.get("combo_timeout_ticks", 0)),
                  held_length=held)


def fmt_tick(t: float) -> str:
    return f"{float(t):g}"
