"""Data-driven archetype -> plaque binding resolution."""

from __future__ import annotations

import json
import random
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Any, Optional

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[1]
FRAME_MANIFEST = SCRIPT_DIR / "plaque_frames.json"
BINDINGS_PATH = REPO_ROOT / "src" / "main" / "resources" / "data" / "myvillage" / "plaque_bindings.json"
PAINTING_ROOT = REPO_ROOT / "src" / "main" / "resources" / "data" / "myvillage" / "painting_variant" / "inscription"
INSCRIPTION_TEXTURE_ROOT = REPO_ROOT / "src" / "main" / "resources" / "assets" / "myvillage" / "textures" / "painting" / "inscription"
VALID_MOUNTS = {"wall", "hanging"}
VALID_ORIENTATIONS = {"horizontal", "vertical"}


@dataclass(frozen=True)
class FrameVariant:
    frame: str
    orientation: str
    width: int
    height: int
    bucket: str


@dataclass(frozen=True)
class Inscription:
    bucket: str
    id: str

    @property
    def variant(self) -> str:
        return f"myvillage:inscription/{self.bucket}/{self.id}"


@dataclass(frozen=True)
class Binding:
    archetype: str
    frame: str
    orientation: str
    mount: str
    inscription: Inscription
    width: int
    height: int
    bucket: str

    @property
    def block_id(self) -> str:
        base = "hanging_plaque" if self.mount == "hanging" else "wall_plaque"
        if self.orientation == "vertical":
            base += "_vertical"
        return f"myvillage:{base}"


class BindingValidationError(ValueError):
    def __init__(self, errors: list[str]) -> None:
        super().__init__("\n".join(errors))
        self.errors = errors


def _load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


@lru_cache(maxsize=1)
def frame_variants() -> dict[tuple[str, str], FrameVariant]:
    manifest = _load_json(FRAME_MANIFEST)
    variants: dict[tuple[str, str], FrameVariant] = {}
    for preset in manifest["presets"]:
        pid = preset["id"]
        width, height = preset["horizontal_size"]
        variants[(pid, "horizontal")] = FrameVariant(
            pid, "horizontal", int(width), int(height), preset["interior_bucket"])
        vertical = preset.get("vertical_size")
        if vertical:
            vwidth, vheight = vertical
            variants[(pid, "vertical")] = FrameVariant(
                pid, "vertical", int(vwidth), int(vheight), preset["vertical_interior_bucket"])
    return variants


def _inscription_exists(inscription: Inscription) -> bool:
    json_path = PAINTING_ROOT / inscription.bucket / f"{inscription.id}.json"
    png_path = INSCRIPTION_TEXTURE_ROOT / inscription.bucket / f"{inscription.id}.png"
    return json_path.is_file() and png_path.is_file()


def _entry_bindings(archetype: str, entry: dict[str, Any], index: int,
                    errors: list[str]) -> list[Binding]:
    prefix = f"{archetype}[{index}]"
    frame = str(entry.get("frame", ""))
    orientation = str(entry.get("orientation", ""))
    mount = str(entry.get("mount", ""))
    if mount not in VALID_MOUNTS:
        errors.append(f"invalid_mount: {prefix}: {mount!r}")
    if orientation not in VALID_ORIENTATIONS:
        errors.append(f"invalid_orientation: {prefix}: {orientation!r}")
    variant = frame_variants().get((frame, orientation))
    if variant is None:
        errors.append(f"unknown_frame_preset: {prefix}: frame={frame!r} orientation={orientation!r}")
        return []

    pool = entry.get("inscription_pool", [])
    if not isinstance(pool, list) or not pool:
        errors.append(f"missing_inscription: {prefix}: empty inscription_pool")
        return []

    out: list[Binding] = []
    for pindex, raw in enumerate(pool):
        if not isinstance(raw, dict):
            errors.append(f"missing_inscription: {prefix}.pool[{pindex}]: not an object")
            continue
        inscription = Inscription(str(raw.get("bucket", "")), str(raw.get("id", "")))
        if inscription.bucket != variant.bucket:
            errors.append(
                f"bucket_mismatch: {prefix}.pool[{pindex}]: "
                f"frame_bucket={variant.bucket} inscription_bucket={inscription.bucket}"
            )
        if not _inscription_exists(inscription):
            errors.append(f"missing_inscription: {prefix}.pool[{pindex}]: {inscription.variant}")
        out.append(Binding(
            archetype=archetype,
            frame=frame,
            orientation=orientation,
            mount=mount,
            inscription=inscription,
            width=variant.width,
            height=variant.height,
            bucket=variant.bucket,
        ))
    return out


@lru_cache(maxsize=1)
def load_bindings() -> dict[str, tuple[Binding, ...]]:
    data = _load_json(BINDINGS_PATH)
    raw_bindings = data.get("bindings", {})
    if not isinstance(raw_bindings, dict):
        raise BindingValidationError(["bindings_not_object"])
    errors: list[str] = []
    out: dict[str, tuple[Binding, ...]] = {}
    for archetype, entries in sorted(raw_bindings.items()):
        if not isinstance(entries, list):
            errors.append(f"invalid_binding_entries: {archetype}: not a list")
            continue
        resolved: list[Binding] = []
        for index, entry in enumerate(entries):
            if not isinstance(entry, dict):
                errors.append(f"invalid_binding_entry: {archetype}[{index}]: not an object")
                continue
            resolved.extend(_entry_bindings(str(archetype), entry, index, errors))
        out[str(archetype)] = tuple(resolved)
    if errors:
        raise BindingValidationError(errors)
    return out


def binding_for(archetype: str, rng: random.Random) -> Optional[Binding]:
    bindings = load_bindings().get(archetype)
    if not bindings:
        return None
    return rng.choice(bindings)
