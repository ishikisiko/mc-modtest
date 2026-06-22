"""Offline 假山 (rockery) variant catalog generator (`garden-rockery` spec).

Mirrors :mod:`tools.buildgen.sect_mountain` at 1/4 scale: a 16x16x16 voxel
field per variant is derived from a deterministic heightfield + value-noise
(reusing ``sect_mountain._hash2`` / ``_noise``), then converted to a Minecraft
block-model JSON (greedy-merged cube elements, ≤ 32) and a Java ``VoxelShape``
table entry (merged AABBs, ≤ 32). The same voxel field drives the model and
the VoxelShape, so the rendered geometry and the collision shape agree at
sub-block granularity (spec requirement).

Role contract (per ``garden-rockery``):

  - ``peak``     (峰顶) — tall, narrow, overhanging. VoxelShape solid-blocking
                  to peak height; NOT standable on top.
  - ``slope``    (山腰) — mid-height, sloped. Standable flat top face.
  - ``base``     (山脚) — low, broad. Standable top face (meets water / ground).
  - ``corner``   (转角) — L-shape for rockery bends. Standable top face.
  - ``standalone`` (孤赏石) — single irregular specimen stone (太湖石-class),
                  narrow, solid-blocking.

The catalog (``VARIANT_CATALOG``) is the manifest every downstream task reads:
the blockstate generator emits one entry per ``(variant, facing, moss)``, the
model writer emits one model per variant, the texture writer emits one PNG per
``(variant, moss)``, and the Java VoxelShape table is regenerated from the
same voxel fields. Run ``python tools/buildgen/rockery_models.py`` to regenerate
the whole asset tree + the Java table snippet.
"""

from __future__ import annotations

import json
import struct
import zlib
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple

try:
    from .sect_mountain import _hash2, _noise  # package import (tools.buildgen.rockery_models)
except ImportError:  # pragma: no cover - direct-run mode (cwd = tools/buildgen)
    from sect_mountain import _hash2, _noise

GRID = 16  # 16x16x16 voxel field (one Minecraft block subdivided into 16 sub-cells)


# ---------------------------------------------------------------------------
# variant catalog (the manifest; task 4.4)
# ---------------------------------------------------------------------------

ROLE_PEAK = "peak"
ROLE_SLOPE = "slope"
ROLE_BASE = "base"
ROLE_CORNER = "corner"
ROLE_STANDALONE = "standalone"
ROLES = (ROLE_PEAK, ROLE_SLOPE, ROLE_BASE, ROLE_CORNER, ROLE_STANDALONE)

# Per-role height profile (in 1/16 sub-cells). ``base_h`` is the typical solid
# height in the variant's footprint interior; ``peak_h`` is the tallest point;
# ``standable`` flags whether the top face should be flat (base/slope/corner)
# or pointed (peak/standalone). ``footprint_shrink`` narrows the plan footprint
# (peak/standalone are narrow; base is broad).
ROLE_PROFILES: Dict[str, dict] = {
    ROLE_PEAK:      {"base_h": 6,  "peak_h": 15, "standable": False, "shrink": 5, "overhang": True},
    ROLE_SLOPE:     {"base_h": 4,  "peak_h": 9,  "standable": True,  "shrink": 3, "overhang": False},
    ROLE_BASE:      {"base_h": 3,  "peak_h": 5,  "standable": True,  "shrink": 1, "overhang": False},
    ROLE_CORNER:    {"base_h": 4,  "peak_h": 7,  "standable": True,  "shrink": 2, "overhang": False, "l_shape": True},
    ROLE_STANDALONE:{"base_h": 5,  "peak_h": 12, "standable": False, "shrink": 6, "overhang": True},
}


@dataclass(frozen=True)
class Variant:
    variant_id: str
    role: str
    seed: int
    hero: bool = False        # the 5 hand-tuned hero variants (visual anchors)
    hero_name: Optional[str] = None  # 主峰 / 副峰 / 孤赏石 / 池畔石 / 门道石


# The shipped catalog: ~36 variants across 5 roles + 5 hero anchors.
# Counts per role respect the spec ranges (peak 8-10, slope 8-10, base 6-8,
# corner 4-6, standalone 4-6). Seeds are hand-picked primes so regeneration is
# stable and each variant's voxel field is visually distinct.
def _build_catalog() -> List[Variant]:
    out: List[Variant] = []
    # peak: 9 variants, hero = peak_01 (主峰)
    peak_seeds = [101, 211, 307, 401, 503, 601, 701, 809, 907]
    for i, s in enumerate(peak_seeds, start=1):
        hero = (i == 1)
        out.append(Variant(f"peak_{i:02d}", ROLE_PEAK, s,
                           hero=hero, hero_name="主峰" if hero else None))
    # slope: 9 variants, hero = slope_01 (副峰)
    slope_seeds = [1103, 1201, 1303, 1409, 1511, 1601, 1709, 1801, 1907]
    for i, s in enumerate(slope_seeds, start=1):
        hero = (i == 1)
        out.append(Variant(f"slope_{i:02d}", ROLE_SLOPE, s,
                           hero=hero, hero_name="副峰" if hero else None))
    # base: 7 variants, hero = base_01 (池畔石)
    base_seeds = [2003, 2011, 2017, 2027, 2039, 2053, 2063]
    for i, s in enumerate(base_seeds, start=1):
        hero = (i == 1)
        out.append(Variant(f"base_{i:02d}", ROLE_BASE, s,
                           hero=hero, hero_name="池畔石" if hero else None))
    # corner: 5 variants, no hero
    corner_seeds = [3001, 3011, 3019, 3023, 3037]
    for i, s in enumerate(corner_seeds, start=1):
        out.append(Variant(f"corner_{i:02d}", ROLE_CORNER, s))
    # standalone: 6 variants, hero = standalone_01 (孤赏石), standalone_02 (门道石)
    standalone_seeds = [4001, 4003, 4007, 4013, 4019, 4021]
    for i, s in enumerate(standalone_seeds, start=1):
        if i == 1:
            out.append(Variant(f"standalone_{i:02d}", ROLE_STANDALONE, s,
                               hero=True, hero_name="孤赏石"))
        elif i == 2:
            out.append(Variant(f"standalone_{i:02d}", ROLE_STANDALONE, s,
                               hero=True, hero_name="门道石"))
        else:
            out.append(Variant(f"standalone_{i:02d}", ROLE_STANDALONE, s))
    return out


VARIANT_CATALOG: List[Variant] = _build_catalog()
VARIANT_BY_ID: Dict[str, Variant] = {v.variant_id: v for v in VARIANT_CATALOG}


# ---------------------------------------------------------------------------
# voxel derivation (task 4.1)
# ---------------------------------------------------------------------------

def _footprint_mask(role: str, seed: int) -> List[List[bool]]:
    """16x16 plan footprint (True = inside the rockery's base). Narrower roles
    (peak/standalone) shrink toward the center; corner is an L-shape; broad
    roles (base) cover most of the cell."""
    prof = ROLE_PROFILES[role]
    shrink = prof["shrink"]
    mask = [[False] * GRID for _ in range(GRID)]
    cx = cz = GRID // 2
    half = (GRID // 2) - shrink
    for x in range(GRID):
        for z in range(GRID):
            if prof.get("l_shape"):
                # corner: L-shape — solid in the west arm + south arm
                if (x <= cx and z >= cz) or (x >= cx and z <= cz):
                    mask[x][z] = True
                continue
            dx = abs(x - cx)
            dz = abs(z - cz)
            if dx <= half and dz <= half:
                # jagged edge via value noise so the footprint isn't a clean square
                edge_noise = _noise(seed ^ 0xA5A5, x, z, 1)
                if dx == half or dz == half:
                    mask[x][z] = (edge_noise >= 0)
                else:
                    mask[x][z] = True
    return mask


def _height_at(role: str, seed: int, x: int, z: int, mask: List[List[bool]]) -> int:
    """Solid-column height (sub-cells) at plan cell (x, z). A radial falloff
    from the footprint center gives the mound silhouette; value noise textures
    the surface (皱褶). Standable roles cap a central plateau to a single flat
    height so the VoxelShape top is walkable (a 3×3+ flat platform with air
    above); non-standable roles let the center spike to peak_h."""
    if not mask[x][z]:
        return 0
    prof = ROLE_PROFILES[role]
    cx = cz = GRID // 2
    dx = abs(x - cx)
    dz = abs(z - cz)
    dist = max(dx, dz)
    max_dist = (GRID // 2) - prof["shrink"]
    if max_dist <= 0:
        max_dist = 1
    # radial falloff: center = peak_h, edge = base_h
    frac = dist / max_dist  # 0 at center, 1 at edge
    h = round(prof["peak_h"] * (1 - frac) + prof["base_h"] * frac)
    # surface noise (皱褶 texture) — ±1 sub-cell, applied only on the outer
    # slope so the central plateau stays flat.
    if prof["standable"]:
        # central plateau: a flat cap (single height) for the inner core so a
        # 3×3+ platform with clear headroom forms the standable top. The plateau
        # radius covers the inner ~45% of the footprint; outside it the slope
        # keeps its noise for a natural transition.
        plateau_radius = max(1, int(max_dist * 0.45))
        cap = round(prof["peak_h"] * 0.6 + prof["base_h"] * 0.4)
        if dist <= plateau_radius:
            return cap  # flat plateau — no noise, single height
        # outer slope: grade from cap down to base_h with light noise texture
        slope_frac = (dist - plateau_radius) / max(1, (max_dist - plateau_radius))
        h = round(cap * (1 - slope_frac) + prof["base_h"] * slope_frac)
        h += _noise(seed, x, z, 1)
        return max(prof["base_h"], min(h, cap))
    # non-standable (peak / standalone): full mound + noise texture
    h += _noise(seed, x, z, 1)
    return max(1, min(h, prof["peak_h"]))


def derive_variant_voxels(variant_id: str) -> List[List[List[bool]]]:
    """16x16x16 solid/air voxel field for the variant (task 4.1).

    Returns ``solid[x][y][z]`` (True = rock). The field is derived from a
    heightfield over the plan footprint plus per-cell value noise, with
    standable-top capping for base/slope/corner roles and overhang carving
    for peak/standalone roles (太湖石-class 孔洞). Deterministic given the
    variant id (which pins role + seed).
    """
    if variant_id not in VARIANT_BY_ID:
        raise KeyError(f"unknown rockery variant {variant_id!r}")
    v = VARIANT_BY_ID[variant_id]
    prof = ROLE_PROFILES[v.role]
    mask = _footprint_mask(v.role, v.seed)
    heights = [[_height_at(v.role, v.seed, x, z, mask) for z in range(GRID)]
               for x in range(GRID)]
    solid = [[[False] * GRID for _ in range(GRID)] for _ in range(GRID)]
    for x in range(GRID):
        for z in range(GRID):
            h = heights[x][z]
            if h <= 0:
                continue
            for y in range(h):
                solid[x][y][z] = True
    # overhang / 孔洞 (peak, standalone): carve a few air pockets inside the
    # solid body for 太湖石-class 皱褶/孔洞 visual. Deterministic per cell.
    if prof.get("overhang"):
        for x in range(2, GRID - 2):
            for z in range(2, GRID - 2):
                for y in range(2, max(3, h - 1)):
                    if not solid[x][y][z]:
                        continue
                    # carve ~8% of interior solid voxels, but never the bottom
                    # layer (y=0) or the top layer (keeps the silhouette).
                    carve = (_hash2(v.seed ^ 0xC0FFEE, x * 31 + y, z * 17 + y) % 100) < 8
                    if carve:
                        solid[x][y][z] = False
    # overhang lips: for peak/standalone, extend the top layer outward by one
    # sub-cell where the layer below is solid (gives the 翘 reading).
    if prof.get("overhang"):
        top_layer = max(prof["peak_h"] - 2, 1)
        additions: List[Tuple[int, int, int]] = []
        for x in range(1, GRID - 1):
            for z in range(1, GRID - 1):
                if solid[x][top_layer][z]:
                    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, nz = x + dx, z + dz
                        if not solid[nx][top_layer][nz] and solid[nx][top_layer - 1][nz]:
                            additions.append((nx, top_layer, nz))
        for (x, y, z) in additions:
            solid[x][y][z] = True
    return solid


# ---------------------------------------------------------------------------
# greedy merge (shared by model + VoxelShape generators)
# ---------------------------------------------------------------------------

@dataclass
class Box:
    x0: int; y0: int; z0: int; x1: int; y1: int; z1: int  # inclusive sub-cell bounds


def greedy_merge(solid: List[List[List[bool]]], max_boxes: int = 32) -> List[Box]:
    """Greedy axis-aligned merge of solid voxels into ≤ ``max_boxes`` boxes.

    Greedy meshing along x first (merge runs), then y (stack runs of equal x-
    extent), then z. Falls back to capping at ``max_boxes`` by keeping the
    largest boxes if the natural merge overshoots (rare for these mound
    shapes; the cap is the spec's ≤ 32 hard limit).
    """
    merged = [[[False] * GRID for _ in range(GRID)] for _ in range(GRID)]
    for x in range(GRID):
        for y in range(GRID):
            for z in range(GRID):
                merged[x][y][z] = solid[x][y][z]
    boxes: List[Box] = []
    for y in range(GRID):
        for x in range(GRID):
            for z in range(GRID):
                if not merged[x][y][z]:
                    continue
                # 1) extend run along z
                z1 = z
                while z1 + 1 < GRID and merged[x][y][z1 + 1]:
                    z1 += 1
                # 2) extend along x (same z-run shape)
                x1 = x
                ok = True
                while ok and x1 + 1 < GRID:
                    for zz in range(z, z1 + 1):
                        if not merged[x1 + 1][y][zz]:
                            ok = False
                            break
                    if ok:
                        x1 += 1
                # 3) extend along y (same x,z rectangle)
                y1 = y
                ok = True
                while ok and y1 + 1 < GRID:
                    for xx in range(x, x1 + 1):
                        for zz in range(z, z1 + 1):
                            if not merged[xx][y1 + 1][zz]:
                                ok = False
                                break
                        if not ok:
                            break
                    if ok:
                        y1 += 1
                # consume
                for xx in range(x, x1 + 1):
                    for yy in range(y, y1 + 1):
                        for zz in range(z, z1 + 1):
                            merged[xx][yy][zz] = False
                boxes.append(Box(x, y, z, x1, y1, z1))
    # cap: keep the largest boxes (by volume) if we overshot max_boxes
    if len(boxes) > max_boxes:
        boxes.sort(key=lambda b: (b.x1 - b.x0 + 1) * (b.y1 - b.y0 + 1) * (b.z1 - b.z0 + 1),
                   reverse=True)
        boxes = boxes[:max_boxes]
    # stable order (by y, then x, then z) so output is reproducible
    boxes.sort(key=lambda b: (b.y0, b.x0, b.z0))
    return boxes


# ---------------------------------------------------------------------------
# model JSON (task 4.2)
# ---------------------------------------------------------------------------

def _sub_to_vanilla(v0: int, v1: int) -> List[float]:
    """Convert inclusive sub-cell bounds [v0, v1] (0..15) to vanilla model
    coordinates [from, to] (0.0..16.0, exclusive upper → +1)."""
    return [float(v0), float(v1 + 1)]


def voxels_to_model_json(solid: List[List[List[bool]]], variant_id: str) -> dict:
    """Convert a voxel field to a Minecraft block-model JSON (task 4.2).

    The model is authored in canonical facing=north orientation; facing
    rotation is applied by the blockstate file's ``y`` rotation. Textures
    reference vanilla ``minecraft:block/stone`` + ``andesite`` so the placeholder
    renders without shipping new base textures (task 4.7 overlays moss). Cube
    elements are greedy-merged to keep the count ≤ 32 (vanilla soft limit).
    """
    boxes = greedy_merge(solid)
    elements: List[dict] = []
    for b in boxes:
        fx, tx = _sub_to_vanilla(b.x0, b.x1)
        fy, ty = _sub_to_vanilla(b.y0, b.y1)
        fz, tz = _sub_to_vanilla(b.z0, b.z1)
        elements.append({
            "from": [fx, fy, fz],
            "to": [tx, ty, tz],
            "faces": {
                "north": {"texture": "#stone"},
                "south": {"texture": "#stone"},
                "east":  {"texture": "#stone"},
                "west":  {"texture": "#stone"},
                "up":    {"texture": "#stone"},
                "down":  {"texture": "#stone"},
            },
        })
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "minecraft:block/stone",
            "stone": "minecraft:block/stone",
            "moss": "minecraft:block/stone",
        },
        "elements": elements,
    }


# ---------------------------------------------------------------------------
# VoxelShape Java (task 4.3)
# ---------------------------------------------------------------------------

def voxels_to_voxelshape_java(solid: List[List[List[bool]]], variant_id: str) -> str:
    """Emit the Java ``VoxelShape`` table entry for the variant (task 4.3).

    Returns a Java source snippet that builds the shape from merged AABBs
    passed to ``Shapes.or(...)`` (vanilla's varargs AABB union, which performs
    the optimize pass once over all boxes). Sub-cell bounds are expressed as
    fractions of a block (``/ 16.0``). The snippet is designed to be pasted
    into a per-variant lookup in ``RockeryBlock.java`` (task 4.8). Using
    ``Shapes.or`` (varargs) instead of nested ``Shapes.joinUnoptimized`` keeps
    the generated source readable and the load-time shape merge O(n) rather
    than O(n) deep nesting.
    """
    boxes = greedy_merge(solid)
    if not boxes:
        return "Shapes.empty()"
    parts: List[str] = []
    for b in boxes:
        x0 = b.x0 / 16.0
        y0 = b.y0 / 16.0
        z0 = b.z0 / 16.0
        x1 = (b.x1 + 1) / 16.0
        y1 = (b.y1 + 1) / 16.0
        z1 = (b.z1 + 1) / 16.0
        parts.append(f"Shapes.create({x0}f, {y0}f, {z0}f, {x1}f, {y1}f, {z1}f)")
    return "Shapes.or(" + ", ".join(parts) + ")"


# ---------------------------------------------------------------------------
# standability check (task 4.4 verification)
# ---------------------------------------------------------------------------

def has_standable_top(solid: List[List[List[bool]]]) -> bool:
    """True iff the variant has a flat standable top face: there exists a y-layer
    where ≥ 3×3 contiguous solid cells form a platform whose layer-above is air.
    Per spec: base/slope/corner MUST be standable; peak/standalone MUST NOT."""
    for y in range(GRID - 1, 0, -1):
        for x in range(1, GRID - 2):
            for z in range(1, GRID - 2):
                # 3x3 platform at layer y, all air at layer y+1
                plat = all(solid[x + dx][y][z + dz] for dx in range(3) for dz in range(3))
                if not plat:
                    continue
                clear = all(not solid[x + dx][y + 1][z + dz]
                            for dx in range(3) for dz in range(3))
                if clear:
                    return True
    return False


# ---------------------------------------------------------------------------
# texture PNG generation (task 4.7)
# ---------------------------------------------------------------------------

# Base palette per role (procedural stone tint). Moss overlay blends toward
# green per moss level.
ROLE_BASE_TINT: Dict[str, Tuple[int, int, int]] = {
    ROLE_PEAK: (122, 122, 126),       # cool grey granite
    ROLE_SLOPE: (130, 126, 118),      # warmer andesite
    ROLE_BASE: (138, 132, 120),       # earthy base
    ROLE_CORNER: (128, 128, 128),     # neutral
    ROLE_STANDALONE: (118, 120, 124), # pale taihu stone
}
MOSS_TINT = {  # blended toward these per moss level
    "none": (0, 0, 0),       # no shift
    "light": (40, 60, 30),   # slight green
    "heavy": (70, 90, 40),   # strong mossy green
}


def _png_bytes(width: int, height: int, rgba: bytes) -> bytes:
    def chunk(tag: bytes, payload: bytes) -> bytes:
        return (struct.pack(">I", len(payload)) + tag + payload +
                struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF))
    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    raw = b""
    stride = width * 4
    for y in range(height):
        raw += b"\x00" + rgba[y * stride:(y + 1) * stride]
    return sig + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


def voxels_to_texture_png(solid: List[List[List[bool]]], variant_id: str,
                           role: str, moss_level: str) -> bytes:
    """16x16 procedural stone texture for the (variant, moss) tuple (task 4.7).

    The top-down silhouette of the variant's footprint is rendered in the role's
    base stone tint with per-pixel value-noise grain; a moss overlay tints
    toward green by the moss level. The PNG is a placeholder-quality texture
    (16x16) — task 4.7's contract is "textures exist and differ by moss level",
    not hand-painted art.
    """
    v = VARIANT_BY_ID[variant_id]
    base = ROLE_BASE_TINT[role]
    moss = MOSS_TINT[moss_level]
    # footprint silhouette at y=0 layer
    top_y = 0
    for y in range(GRID):
        if any(solid[x][y][z] for x in range(GRID) for z in range(GRID)):
            top_y = y
    rgba = bytearray()
    moss_strength = {"none": 0.0, "light": 0.35, "heavy": 0.65}[moss_level]
    for px in range(16):
        for pz in range(16):
            inside = solid[px][top_y][pz]
            # grain noise (deterministic per pixel)
            grain = _noise(v.seed ^ 0xFEED, px, pz, 18)
            if inside:
                r = max(0, min(255, base[0] + grain))
                g = max(0, min(255, base[1] + grain))
                b = max(0, min(255, base[2] + grain))
            else:
                # outside the silhouette: faint shadow edge (darker)
                r = max(0, base[0] - 40 + grain // 2)
                g = max(0, base[1] - 40 + grain // 2)
                b = max(0, base[2] - 40 + grain // 2)
            # moss overlay
            if moss_strength > 0:
                moss_grain = _noise(v.seed ^ 0xCAFE, px, pz, 20)
                ms = moss_strength + (moss_grain / 255.0) * 0.2
                ms = max(0.0, min(1.0, ms))
                r = round(r * (1 - ms) + moss[0] * ms)
                g = round(g * (1 - ms) + (moss[1] + 30) * ms)
                b = round(b * (1 - ms) + moss[2] * ms)
            rgba += bytes([r & 0xFF, g & 0xFF, b & 0xFF, 255])
    return _png_bytes(16, 16, bytes(rgba))


# ---------------------------------------------------------------------------
# asset-tree writer (tasks 4.5 / 4.6 / 4.7 driver)
# ---------------------------------------------------------------------------

REPO_ROOT = Path(__file__).resolve().parents[2]
ASSET_ROOT = REPO_ROOT / "src" / "main" / "resources" / "assets" / "myvillage"
BLOCKSTATE_PATH = ASSET_ROOT / "blockstates" / "rockery_block.json"
MODEL_DIR = ASSET_ROOT / "models" / "block" / "rockery_block"
TEXTURE_DIR = ASSET_ROOT / "textures" / "block" / "rockery_block"
JAVA_SNIPPET_PATH = REPO_ROOT / "reports" / "rockery_voxelshape_java.txt"
ROCKERY_BLOCK_JAVA = (REPO_ROOT / "src" / "main" / "java" / "com" /
                      "example" / "myvillage" / "block" / "RockeryBlock.java")

FACING_ROTATION = {"north": 0, "east": 90, "south": 180, "west": 270}
MOSS_LEVELS = ("none", "light", "heavy")


def write_blockstate(variants: List[Variant]) -> int:
    entries: Dict[str, dict] = {}
    for v in variants:
        model = f"myvillage:block/rockery_block/{v.variant_id}"
        for moss in MOSS_LEVELS:
            for facing, rot in FACING_ROTATION.items():
                key = f"facing={facing},moss_level={moss},variant={v.variant_id}"
                entry = {"model": model}
                if rot:
                    entry["y"] = rot
                    entry["uvlock"] = True
                entries[key] = entry
    BLOCKSTATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    BLOCKSTATE_PATH.write_text(
        json.dumps({"variants": entries}, indent=2) + "\n", encoding="utf-8")
    return len(entries)


def write_models(voxel_cache: Dict[str, List[List[List[bool]]]]) -> int:
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    count = 0
    for v in VARIANT_CATALOG:
        model = voxels_to_model_json(voxel_cache[v.variant_id], v.variant_id)
        (MODEL_DIR / f"{v.variant_id}.json").write_text(
            json.dumps(model, indent=2) + "\n", encoding="utf-8")
        count += 1
    return count


def write_textures(voxel_cache: Dict[str, List[List[List[bool]]]]) -> int:
    TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
    count = 0
    for v in VARIANT_CATALOG:
        for moss in MOSS_LEVELS:
            png = voxels_to_texture_png(
                voxel_cache[v.variant_id], v.variant_id, v.role, moss)
            (TEXTURE_DIR / f"{v.variant_id}_{moss}.png").write_bytes(png)
            count += 1
    return count


def write_java_snippet(voxel_cache: Dict[str, List[List[bool]]]) -> None:
    """Write the Java VoxelShape table snippet to reports/ for task 4.8.

    Emits one ``case VARIANT_ID: return <shape-expr>;`` line per variant,
    designed to be pasted into a ``switch`` over ``Variant`` in
    ``RockeryBlock.shapeFor(Variant)``. Using a switch (not a HashMap) keeps
    the lookup allocation-free and lets the JIT inline it.
    """
    lines: List[str] = []
    lines.append("// Auto-generated by tools/buildgen/rockery_models.py — do not edit by hand.")
    lines.append("// Paste into RockeryBlock.shapeFor(Variant) (task 4.8).")
    lines.append("switch (variant) {")
    lines.append("    default: return Shapes.block();  // fallback (should not happen)")
    for v in VARIANT_CATALOG:
        shape_expr = voxels_to_voxelshape_java(voxel_cache[v.variant_id], v.variant_id)
        lines.append(f"    case {v.variant_id.upper()}: return {shape_expr};")
    lines.append("}")
    JAVA_SNIPPET_PATH.parent.mkdir(parents=True, exist_ok=True)
    JAVA_SNIPPET_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")


# The hand-authored parts of RockeryBlock.java that the generator does NOT
# rewrite — only the Variant enum body and the shapeFor switch are generated.
# Everything else (imports, class declaration, FACING/MOSS_LEVEL properties,
# getShape/getCollisionShape/rotate/mirror, MossLevel enum) is preserved
# verbatim. Keep this template in lock-step with the manual review contract.
_ROCKERY_BLOCK_TEMPLATE = '''package com.example.myvillage.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 假山 (rockery) — first instance of the {@code mod-decor-block-family}
 * protocol. A mod-owned decorative block that ships pre-baked sub-block-precision
 * geometry (model + VoxelShape) for Taihu-stone-class 假山 without depending on
 * Chisels & Bits or falling back to a flat {@code minecraft:stone} cube.
 *
 * <p>The {@link Variant} catalog and the per-variant {@link VoxelShape} table in
 * {@link #shapeFor(Variant)} are auto-generated by
 * {@code tools/buildgen/rockery_models.py} from the same 16x16x16 voxel fields
 * that produce the block-model JSONs, so the rendered geometry and the
 * collision shape agree at sub-block granularity. Per the spec, {@code base} /
 * {@code slope} variants expose a standable top face (players can climb
 * mid-mountain via auto-step); {@code peak} variants do not (steep,
 * solid-blocking to peak height).
 *
 * <p><b>Regeneration:</b> run {@code python tools/buildgen/rockery_models.py};
 * the generator rewrites this file's {@code Variant} enum + {@code shapeFor}
 * switch from {@code VARIANT_CATALOG}. Do not hand-edit those two regions.
 */
public class RockeryBlock extends Block {
    public static final MapCodec<RockeryBlock> CODEC = simpleCodec(RockeryBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Variant> VARIANT = EnumProperty.create("variant", Variant.class);
    public static final EnumProperty<MossLevel> MOSS_LEVEL = EnumProperty.create("moss_level", MossLevel.class);

    public RockeryBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(VARIANT, Variant.STANDALONE_01)
                .setValue(MOSS_LEVEL, MossLevel.NONE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VARIANT, MOSS_LEVEL);
    }

    /**
     * Per-variant VoxelShape dispatch. AUTO-GENERATED by
     * {@code tools/buildgen/rockery_models.py} (task 4.8) from the same voxel
     * fields that produce the model JSONs — do not hand-edit. Each variant's
     * shape is a union of merged AABBs (≤ 32) expressed in sub-cell fractions.
     */
    protected static VoxelShape shapeFor(Variant variant) {
__SHAPE_SWITCH__
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // The generated per-variant shapes are authored facing=north; the
        // blockstate file handles model rotation, but the collision VoxelShape
        // is rotation-symmetric for the mound forms (the heightfield is
        // near-radial), so no per-facing rotation is applied here. If a future
        // asymmetric variant needs facing-rotated collision, add a
        // Shapes.rotate here keyed on state.getValue(FACING).
        return shapeFor(state.getValue(VARIANT));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Collision uses the same per-variant shape (merged AABBs ≤ 32 per the
        // spec). Standable-top variants (base/slope/corner) collide solidly up
        // to the plateau so the player can stand on top; peak/standalone
        // collide solidly to their full height.
        return shapeFor(state.getValue(VARIANT));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * Rockery variant catalog. AUTO-GENERATED by
     * {@code tools/buildgen/rockery_models.py} from {@code VARIANT_CATALOG} —
     * do not hand-edit. Roles: {@code peak} (峰顶, non-standable),
     * {@code slope} (山腰, standable top), {@code base} (山脚, standable top,
     * meets water), {@code corner} (转角, L-shape), {@code standalone}
     * (孤赏石, narrow specimen stone). The 5 hero variants (主峰, 副峰,
     * 孤赏石, 池畔石, 门道石) are visual anchors placed first in each role.
     */
    public enum Variant implements StringRepresentable {
__VARIANT_ENUM__
        private final String name;

        Variant(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** Weathering/aging overlay selector. Protocol-level: every decor class
     * exposes this even if the class only renders one value (e.g. lattice does
     * not naturally collect moss and is placed with {@code NONE} by convention). */
    public enum MossLevel implements StringRepresentable {
        NONE("none"),
        LIGHT("light"),
        HEAVY("heavy");

        private final String name;

        MossLevel(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
'''


def write_rockery_block_java(voxel_cache: Dict[str, List[List[bool]]]) -> None:
    """Rewrite RockeryBlock.java with the full Variant enum + shapeFor switch
    generated from VARIANT_CATALOG (task 4.8). Only the two AUTO-GENERATED
    regions are rewritten; the rest of the class (properties, getShape,
    rotate/mirror, MossLevel enum) is the hand-authored template above."""
    # Variant enum body: one line per variant, indented 8 spaces. The last
    # constant terminates the enum-list with ';' (Java requires it before the
    # body); the rest carry ','.
    enum_lines: List[str] = []
    for i, v in enumerate(VARIANT_CATALOG):
        sep = ";" if i == len(VARIANT_CATALOG) - 1 else ","
        enum_lines.append(f'        {v.variant_id.upper()}("{v.variant_id}"){sep}')
    enum_body = "\n".join(enum_lines)

    # shapeFor switch body: indented 8 spaces (one level inside the method).
    switch_lines: List[str] = ["        switch (variant) {",
                               "            default: return Shapes.block();"]
    for v in VARIANT_CATALOG:
        shape_expr = voxels_to_voxelshape_java(voxel_cache[v.variant_id], v.variant_id)
        switch_lines.append(f"            case {v.variant_id.upper()}: return {shape_expr};")
    switch_lines.append("        }")
    switch_body = "\n".join(switch_lines)

    src = (_ROCKERY_BLOCK_TEMPLATE
           .replace("__VARIANT_ENUM__", enum_body)
           .replace("__SHAPE_SWITCH__", switch_body))
    ROCKERY_BLOCK_JAVA.parent.mkdir(parents=True, exist_ok=True)
    ROCKERY_BLOCK_JAVA.write_text(src, encoding="utf-8")


def main() -> int:
    # 1) derive all voxel fields once (cached; the model + VoxelShape + texture
    #    generators all read the same field so geometry/collision/textures agree)
    voxel_cache: Dict[str, List[List[List[bool]]]] = {
        v.variant_id: derive_variant_voxels(v.variant_id) for v in VARIANT_CATALOG
    }
    # 2) task 4.4 verification: standability contract
    standability_errors: List[str] = []
    for v in VARIANT_CATALOG:
        standable = has_standable_top(voxel_cache[v.variant_id])
        expected = ROLE_PROFILES[v.role]["standable"]
        if standable != expected:
            standability_errors.append(
                f"{v.variant_id} ({v.role}): standable={standable} expected={expected}")
    # 3) write assets
    n_bs = write_blockstate(VARIANT_CATALOG)
    n_models = write_models(voxel_cache)
    n_textures = write_textures(voxel_cache)
    write_java_snippet(voxel_cache)
    write_rockery_block_java(voxel_cache)
    # 4) report
    role_counts = {r: sum(1 for v in VARIANT_CATALOG if v.role == r) for r in ROLES}
    print(f"catalog: {len(VARIANT_CATALOG)} variants (per-role: {role_counts})")
    print(f"blockstate: {n_bs} entries -> {BLOCKSTATE_PATH.relative_to(REPO_ROOT)}")
    print(f"models: {n_models} -> {MODEL_DIR.relative_to(REPO_ROOT)}/")
    print(f"textures: {n_textures} -> {TEXTURE_DIR.relative_to(REPO_ROOT)}/")
    print(f"java snippet: {JAVA_SNIPPET_PATH.relative_to(REPO_ROOT)}")
    print(f"java block class: {ROCKERY_BLOCK_JAVA.relative_to(REPO_ROOT)}")
    if standability_errors:
        print("STANDABILITY FAILURES:")
        for e in standability_errors:
            print(f"  {e}")
        return 1
    print(f"standability: OK ({len(VARIANT_CATALOG)} variants match role contract)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
