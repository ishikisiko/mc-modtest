#!/usr/bin/env python3
"""Target-based Chunky camera solver + headless renderer.

Scans a Minecraft save's region files for a placed structure's bounding box,
derives the bbox center, generates one or more named views,
computes Chunky ``camera.position`` / ``camera.orientation`` (yaw/pitch/roll in
**radians**) via a look-at solver derived from Chunky's own matrix conventions,
writes a pristine ``scene.json`` per view, drives ``ChunkyLauncher.jar`` to
render + snapshot each one, and finally runs a framing-fail detector over each
PNG (mean luminance near sky color + too few non-sky pixels => framing failed).

No more hand-tuning yaw/pitch by trial and error.

The camera math is derived from se.llbit.chunky 2.4.6 bytecode
(Matrix3.rotX/rotY + Camera.updateTransform + PinholeProjector.apply): the
default center view ray is +Z, transformed by
``rotY(yaw+pi/2) * rotX(pi/2-pitch) * rotZ(roll)``, which simplifies to the
forward direction

    d = ( cos(yaw)*sin(pitch), -cos(pitch), -sin(yaw)*sin(pitch) )

Solving d = normalize(target - camera) for yaw, pitch gives

    pitch = acos(-f.y)
    yaw   = atan2(-f.z, f.x)

(see docs/ai-kb/18_chunky_path_traced_render.md for the full derivation).

Usage:
    python tools/render_structure.py \
        --world run-acceptance/chunky_stage1_world \
        --launcher chunky-render/ChunkyLauncher.jar \
        --chunky-home chunky-render \
        --anchor 0 79 192 \
        --tag-prefix small_house \
        --out chunky-render/renders

By default the tool renders a survey plan: four mid-height cardinal views plus
four high diagonal views. Use ``--view-plan cardinal`` or explicit ``--views
front right back left`` for the old four-view behavior, and
``--view-plan height-sweep`` when layout review needs low/mid/high passes from
each side.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import shutil
import struct
import subprocess
import sys
import zlib
from pathlib import Path
from typing import Any, Iterable

# Reuse the repo's own minimal NBT primitives so this tool adds no new
# runtime dependency. nbtread lives in tools/buildgen/.
sys.path.insert(0, str(Path(__file__).resolve().parent / "buildgen"))
import nbtread  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]


def display_path(path: Path) -> str:
    """Prefer repo-relative paths in output, but allow absolute external dirs."""
    try:
        return str(path.resolve().relative_to(ROOT))
    except ValueError:
        return str(path)

# ---------------------------------------------------------------------------
# Region (.mca) reader — self-contained, builds on nbtread's NBT decoder.
# ---------------------------------------------------------------------------

SECTOR_BYTES = 4096
CHUNK_SECTIONS_PER_CHUNK = 24  # 1.21: y=-64..319 in 16 sections of 16 = 24


def _read_region_chunks(mca: Path) -> dict[tuple[int, int], dict[str, Any]]:
    """Return {(lx, lz): chunk_nbt} for every present chunk in one region file.

    lx/lz are chunk coords LOCAL to the region (0..31). The caller converts to
    absolute chunk coords from the region file name.
    """
    raw = mca.read_bytes()
    if len(raw) < 2 * SECTOR_BYTES:
        return {}
    chunks: dict[tuple[int, int], dict[str, Any]] = {}
    for i in range(1024):
        off_bytes = raw[i * 4 : i * 4 + 3]
        sectors = raw[i * 4 + 3]
        if sectors == 0:
            continue
        offset = (off_bytes[0] << 16) | (off_bytes[1] << 8) | off_bytes[2]
        start = offset * SECTOR_BYTES
        length = struct.unpack(">I", raw[start : start + 4])[0]
        scheme = raw[start + 4]
        data = raw[start + 5 : start + 4 + length]
        if scheme == 2:  # zlib
            try:
                data = zlib.decompress(data)
            except zlib.error:
                continue
        elif scheme == 1:  # gzip
            import gzip

            try:
                data = gzip.decompress(data)
            except OSError:
                continue
        else:
            continue
        # NBT payload starts at byte 0: root tag id, root name, then compound.
        (tag_id,) = struct.unpack(">B", data[0:1])
        if tag_id != nbtread.TAG_COMPOUND:
            continue
        f = _BytesReader(data)
        f.read(1)  # root tag id
        nbtread._read_utf(f)  # root name (discard)
        chunk = nbtread._read_payload(f, nbtread.TAG_COMPOUND)
        lz = i // 32
        lx = i % 32
        chunks[(lx, lz)] = chunk
    return chunks


class _BytesReader:
    """file-like wrapper over a bytes buffer, for nbtread._read_payload."""

    def __init__(self, data: bytes) -> None:
        self._b = data
        self._p = 0

    def read(self, n: int) -> bytes:
        v = self._b[self._p : self._p + n]
        self._p += len(v)
        return v


# ---------------------------------------------------------------------------
# Block bbox scan over the whole save (all region files).
# ---------------------------------------------------------------------------

# Blocks that are part of the natural world, not a placed structure. A
# structure bbox is the AABB of all blocks whose palette name is NOT in this
# terrain set and NOT air. This is a NATURAL-GENERATION allowlist: anything a
# placed structure uses (planks, stairs, doors, fences, glass, crafted bricks,
# furniture, etc.) is intentionally absent so that built structures cluster.
TERRAIN = {
    "minecraft:air",
    "minecraft:cave_air",
    "minecraft:void_air",
    "minecraft:stone",
    "minecraft:dirt",
    "minecraft:grass_block",
    "minecraft:coarse_dirt",
    "minecraft:podzol",
    "minecraft:mycelium",
    "minecraft:mud",
    "minecraft:gravel",
    "minecraft:sand",
    "minecraft:red_sand",
    "minecraft:sandstone",
    "minecraft:red_sandstone",
    "minecraft:water",
    "minecraft:lava",
    "minecraft:bedrock",
    "minecraft:clay",
    "minecraft:snow_block",
    "minecraft:snow",
    "minecraft:ice",
    "minecraft:packed_ice",
    "minecraft:blue_ice",
    "minecraft:frosted_ice",
    # vegetation (natural)
    "minecraft:grass",
    "minecraft:tall_grass",
    "minecraft:fern",
    "minecraft:large_fern",
    "minecraft:seagrass",
    "minecraft:tall_seagrass",
    "minecraft:kelp",
    "minecraft:kelp_plant",
    "minecraft:lily_pad",
    "minecraft:dead_bush",
    "minecraft:cactus",
    "minecraft:bamboo",
    "minecraft:sugar_cane",
    # leaves + logs (trees / natural vegetation, NOT built)
    "minecraft:oak_leaves",
    "minecraft:dark_oak_leaves",
    "minecraft:birch_leaves",
    "minecraft:spruce_leaves",
    "minecraft:jungle_leaves",
    "minecraft:acacia_leaves",
    "minecraft:mangrove_leaves",
    "minecraft:cherry_leaves",
    "minecraft:azalea_leaves",
    "minecraft:flowering_azalea_leaves",
    "minecraft:oak_log",
    "minecraft:dark_oak_log",
    "minecraft:birch_log",
    "minecraft:spruce_log",
    "minecraft:jungle_log",
    "minecraft:acacia_log",
    "minecraft:mangrove_log",
    "minecraft:cherry_log",
    "minecraft:oak_wood",
    "minecraft:spruce_wood",
    "minecraft:birch_wood",
    "minecraft:stripped_spruce_wood",
    "minecraft:stripped_oak_wood",
    # mushrooms / flowers / natural decor
    "minecraft:poppy",
    "minecraft:dandelion",
    "minecraft:allium",
    "minecraft:azure_bluet",
    "minecraft:red_tulip",
    "minecraft:orange_tulip",
    "minecraft:white_tulip",
    "minecraft:pink_tulip",
    "minecraft:oxeye_daisy",
    "minecraft:cornflower",
    "minecraft:lily_of_the_valley",
    "minecraft:sunflower",
    "minecraft:lilac",
    "minecraft:rose_bush",
    "minecraft:peony",
    "minecraft:red_mushroom",
    "minecraft:brown_mushroom",
    "minecraft:red_mushroom_block",
    "minecraft:brown_mushroom_block",
    "minecraft:mushroom_stem",
    "minecraft:small_amethyst_bud",
    "minecraft:medium_amethyst_bud",
    "minecraft:large_amethyst_bud",
    "minecraft:amethyst_cluster",
    "minecraft:budding_amethyst",
    # cave / raw stone variants
    "minecraft:granite",
    "minecraft:diorite",
    "minecraft:andesite",
    "minecraft:deepslate",
    "minecraft:tuff",
    "minecraft:calcite",
    "minecraft:dripstone_block",
    "minecraft:pointed_dripstone",
    "minecraft:moss_block",
    "minecraft:smooth_basalt",
    "minecraft:basalt",
    "minecraft:obsidian",
    "minecraft:crying_obsidian",
    "minecraft:netherrack",
    "minecraft:end_stone",
    "minecraft:terracotta",
    "minecraft:glowstone",
    # ores (raw ground, all variants incl. deepslate)
    "minecraft:coal_ore",
    "minecraft:iron_ore",
    "minecraft:copper_ore",
    "minecraft:gold_ore",
    "minecraft:redstone_ore",
    "minecraft:lapis_ore",
    "minecraft:diamond_ore",
    "minecraft:emerald_ore",
    "minecraft:deepslate_coal_ore",
    "minecraft:deepslate_iron_ore",
    "minecraft:deepslate_copper_ore",
    "minecraft:deepslate_gold_ore",
    "minecraft:deepslate_redstone_ore",
    "minecraft:deepslate_lapis_ore",
    "minecraft:deepslate_diamond_ore",
    "minecraft:deepslate_emerald_ore",
    "minecraft:nether_gold_ore",
    "minecraft:nether_quartz_ore",
    "minecraft:ancient_debris",
    "minecraft:raw_iron_block",
    "minecraft:raw_copper_block",
    "minecraft:raw_gold_block",
}


def _section_block_index(sec: dict[str, Any]) -> tuple[int, list[str]] | None:
    """Decode a chunk section's block_states into (bits, palette_names).

    Returns None for empty sections. palette_names are the resolved block ids;
    air/unknown entries are kept (the caller filters).
    """
    bs = sec.get("block_states")
    if not isinstance(bs, dict):
        return None
    pal = bs.get("palette")
    if not isinstance(pal, list) or not pal:
        return None
    names: list[str] = []
    for entry in pal:
        if isinstance(entry, dict):
            names.append(str(entry.get("Name", "minecraft:air")))
        else:
            names.append("minecraft:air")
    return (0, names)


def _iter_section_blocks(
    sec: dict[str, Any], sx: int, sy: int, sz: int
) -> Iterable[tuple[int, int, int, str]]:
    """Yield (world_x, world_y, world_z, block_name) for each non-skipped block
    in a 16x16x16 section. Only yields blocks whose palette entry is a structure
    block (not in TERRAIN)."""
    decoded = _section_block_index(sec)
    if decoded is None:
        return
    _, names = decoded
    bs = sec["block_states"]
    data = bs.get("data")
    # Resolve the palette index per block.
    if data is None:
        # uniform section — single palette entry fills the whole section
        name = names[0] if names else "minecraft:air"
        if name in TERRAIN:
            return
        for y in range(16):
            for z in range(16):
                for x in range(16):
                    yield (sx + x, sy + y, sz + z, name)
        return
    if len(names) <= 1:
        bits = 4
    else:
        bits = max(4, math.ceil(math.log2(len(names))))
    per_long = 64 // bits
    mask = (1 << bits) - 1
    block_i = 0
    for L in data:
        u = L & ((1 << 64) - 1)  # Java long is signed -> unsigned
        for k in range(per_long):
            if block_i >= 4096:
                return
            idx = (u >> (k * bits)) & mask
            name = names[idx] if idx < len(names) else "minecraft:air"
            if name not in TERRAIN:
                # block index -> (x, y, z): x varies fastest, then z, then y
                by = block_i // 256
                bz = (block_i // 16) % 16
                bx = block_i % 16
                yield (sx + bx, sy + by, sz + bz, name)
            block_i += 1


def scan_structure_bbox(
    world_dir: Path,
    *,
    anchor: tuple[int, int, int] | None = None,
    tag_prefix: str | None = None,
    search_radius: int = 48,
) -> dict[str, Any]:
    """Scan the save's region files and return the bbox of the structure at
    ``anchor``.

    Two modes:

    - **Anchored (recommended):** only non-terrain blocks within
      ``search_radius`` blocks of ``anchor`` (3D box, not sphere) are counted,
      and their AABB is returned directly. No clustering, so neighboring
      structures outside the radius never contaminate the bbox. This is what
      you want for "render the one building I just placed at X Y Z".
    - **Unanchored:** every non-terrain block in the save is clustered on a
      coarse 16-block grid and the densest cluster wins. Slow and noisy on a
      world that contains many structures; keep it as a fallback.

    In both modes 'structure block' = palette name not in TERRAIN (natural
    generation) and not air.
    """
    region_dir = world_dir / "region"
    if not region_dir.is_dir():
        raise FileNotFoundError(f"no region/ dir under {world_dir}")

    all_blocks: list[tuple[int, int, int]] = []
    block_names: dict[str, int] = {}

    # When anchored, narrow the (x,z) chunk range and y section range to the
    # search box so we skip 99% of the world and never pull in faraway
    # structures.
    if anchor is not None:
        ax, ay, az = anchor
        r = search_radius
        cx0, cx1 = (ax - r) // 16, (ax + r) // 16
        cz0, cz1 = (az - r) // 16, (az + r) // 16
        sec0 = (ay - r) // 16
        sec1 = (ay + r) // 16

        def _in_box(bx: int, by: int, bz: int) -> bool:
            return (
                ax - r <= bx <= ax + r
                and ay - r <= by <= ay + r
                and az - r <= bz <= az + r
            )

    region_files = sorted(region_dir.glob("r.*.*.mca"))
    for mca in region_files:
        # filename r.<x>.<z>.mca -> stem r.<x>.<z> -> ['r', '<x>', '<z>']
        parts = mca.stem.split(".")
        if len(parts) != 3:
            continue
        try:
            rx, rz = int(parts[1]), int(parts[2])
        except ValueError:
            continue

        if anchor is not None:
            # region covers chunks [rx*32, rx*32+31]; skip if it can't overlap
            if (
                rx * 32 + 31 < cx0
                or rx * 32 > cx1
                or rz * 32 + 31 < cz0
                or rz * 32 > cz1
            ):
                continue

        chunks = _read_region_chunks(mca)
        for (lx, lz), chunk in chunks.items():
            cx = rx * 32 + lx
            cz = rz * 32 + lz
            if anchor is not None and (cx < cx0 or cx > cx1 or cz < cz0 or cz > cz1):
                continue
            sections = chunk.get("sections")
            if not isinstance(sections, list):
                continue
            for sec in sections:
                if not isinstance(sec, dict) or "Y" not in sec:
                    continue
                yv = sec["Y"]
                sy = yv if isinstance(yv, int) else yv.value
                if anchor is not None and (sy < sec0 or sy > sec1):
                    continue
                for bx, by, bz, name in _iter_section_blocks(
                    sec, cx * 16, sy * 16, cz * 16
                ):
                    if anchor is not None and not _in_box(bx, by, bz):
                        continue
                    all_blocks.append((bx, by, bz))
                    block_names[name] = block_names.get(name, 0) + 1

    if not all_blocks:
        raise RuntimeError(
            f"no structure blocks found under {region_dir} "
            f"(scanned {len(region_files)} region files; "
            f"anchor={anchor} search_radius={search_radius})"
        )

    if anchor is not None:
        # Anchored mode: AABB of everything in the box. Done.
        xs = [p[0] for p in all_blocks]
        ys = [p[1] for p in all_blocks]
        zs = [p[2] for p in all_blocks]
        best = all_blocks
    else:
        # Unanchored: cluster on a coarse 16-block grid, take the densest.
        def cell(p: tuple[int, int, int]) -> tuple[int, int, int]:
            return (p[0] >> 4, p[1] >> 4, p[2] >> 4)

        clusters: dict[tuple[int, int, int], list[tuple[int, int, int]]] = {}
        for p in all_blocks:
            clusters.setdefault(cell(p), []).append(p)
        cells = list(clusters)
        parent = {c: c for c in cells}

        def find(c: tuple[int, int, int]) -> tuple[int, int, int]:
            while parent[c] != c:
                parent[c] = parent[parent[c]]
                c = parent[c]
            return c

        def union(a: tuple[int, int, int], b: tuple[int, int, int]) -> None:
            ra, rb = find(a), find(b)
            if ra != rb:
                parent[ra] = rb

        for c in cells:
            for dx, dy, dz in (
                (1, 0, 0),
                (0, 1, 0),
                (0, 0, 1),
                (1, 1, 0),
                (1, 0, 1),
                (0, 1, 1),
                (1, 1, 1),
            ):
                nb = (c[0] + dx, c[1] + dy, c[2] + dz)
                if nb in parent:
                    union(c, nb)
        grouped: dict[tuple[int, int, int], list[tuple[int, int, int]]] = {}
        for c in cells:
            grouped.setdefault(find(c), []).extend(clusters[c])
        best = max(grouped.values(), key=len)
        xs = [p[0] for p in best]
        ys = [p[1] for p in best]
        zs = [p[2] for p in best]

    bbox = {
        "min": [min(xs), min(ys), min(zs)],
        "max": [max(xs), max(ys), max(zs)],
        "size": [max(xs) - min(xs) + 1, max(ys) - min(ys) + 1, max(zs) - min(zs) + 1],
        "center": [
            (min(xs) + max(xs)) / 2.0,
            (min(ys) + max(ys)) / 2.0,
            (min(zs) + max(zs)) / 2.0,
        ],
        "block_count": len(best),
        "top_blocks": sorted(block_names.items(), key=lambda kv: -kv[1])[:12],
    }
    return bbox


# ---------------------------------------------------------------------------
# Camera solver — look-at, derived from Chunky 2.4.6 matrix conventions.
# ---------------------------------------------------------------------------


def solve_camera(
    target: tuple[float, float, float],
    *,
    azimuth_deg: float,
    distance: float,
    height_above: float,
) -> dict[str, Any]:
    """Place a camera at distance/height offset from target along azimuth, and
    solve yaw/pitch so the center view ray points at target.

    azimuth_deg: compass direction the CAMERA sits at, measured clockwise from
    +Z (south) in world space. 0 = south of target (camera at +Z), 90 = west
    (camera at -X), 180 = north (camera at -Z), 270 = east (camera at +X).
    """
    az = math.radians(azimuth_deg)
    tx, ty, tz = target
    # Camera sits 'distance' horizontally and 'height_above' vertically up.
    cam_x = tx - math.sin(az) * distance
    cam_y = ty + height_above
    cam_z = tz + math.cos(az) * distance

    # Forward = target - camera, normalized.
    fx, fy, fz = tx - cam_x, ty - cam_y, tz - cam_z
    fl = math.sqrt(fx * fx + fy * fy + fz * fz)
    fx, fy, fz = fx / fl, fy / fl, fz / fl

    # From d = (cos(yaw)*sin(pitch), -cos(pitch), -sin(yaw)*sin(pitch)):
    #   cos(pitch) = -fy  =>  pitch = acos(-fy)
    #   yaw = atan2(-fz, fx)
    pitch = math.acos(max(-1.0, min(1.0, -fy)))
    yaw = math.atan2(-fz, fx)
    # Chunky's pinhole projector uses image Y in the opposite screen-up sense
    # expected by these generated PNG reviews. A 180-degree roll keeps the
    # center ray fixed while making world-up read as image-up.
    roll = math.pi
    return {
        "position": [cam_x, cam_y, cam_z],
        "target": [tx, ty, tz],
        "orientation": {"roll": roll, "pitch": pitch, "yaw": yaw},
        "pitch_deg": math.degrees(pitch),
        "yaw_deg": math.degrees(yaw),
        "roll_deg": math.degrees(roll),
        "forward": [fx, fy, fz],
        "distance": fl,
    }


# Azimuth for each named view (where the camera sits, clockwise from +Z/south).
VIEW_AZIMUTH = {
    "front": 0.0,    # camera south of target, looking north (-Z)
    "right": 90.0,   # camera west of target (-X side), looking east (+X)
    "back": 180.0,   # camera north of target, looking south (+Z)
    "left": 270.0,   # camera east of target (+X side), looking west (-X)
}


def _view_spec(
    name: str,
    azimuth_deg: float,
    *,
    distance_factor: float = 1.0,
    height_factor: float = 1.0,
    height_band: str = "mid",
) -> dict[str, Any]:
    return {
        "name": name,
        "azimuth_deg": azimuth_deg,
        "distance_factor": distance_factor,
        "height_factor": height_factor,
        "height_band": height_band,
    }


# Camera-position names use Minecraft world-space X/Z compass directions:
# south = +Z, north = -Z, east = +X, west = -X. Diagonal names describe where
# the camera sits, not the direction it looks.
VIEW_PLANS: dict[str, list[dict[str, Any]]] = {
    "cardinal": [
        _view_spec("front", VIEW_AZIMUTH["front"]),
        _view_spec("right", VIEW_AZIMUTH["right"]),
        _view_spec("back", VIEW_AZIMUTH["back"]),
        _view_spec("left", VIEW_AZIMUTH["left"]),
    ],
    "survey": [
        _view_spec("front_mid", VIEW_AZIMUTH["front"]),
        _view_spec("right_mid", VIEW_AZIMUTH["right"]),
        _view_spec("back_mid", VIEW_AZIMUTH["back"]),
        _view_spec("left_mid", VIEW_AZIMUTH["left"]),
        _view_spec("southwest_high", 45.0, distance_factor=1.15, height_factor=1.55, height_band="high"),
        _view_spec("northwest_high", 135.0, distance_factor=1.15, height_factor=1.55, height_band="high"),
        _view_spec("northeast_high", 225.0, distance_factor=1.15, height_factor=1.55, height_band="high"),
        _view_spec("southeast_high", 315.0, distance_factor=1.15, height_factor=1.55, height_band="high"),
    ],
    "height-sweep": [
        _view_spec("front_low", VIEW_AZIMUTH["front"], distance_factor=0.9, height_factor=0.65, height_band="low"),
        _view_spec("front_mid", VIEW_AZIMUTH["front"]),
        _view_spec("front_high", VIEW_AZIMUTH["front"], distance_factor=1.15, height_factor=1.55, height_band="high"),
        _view_spec("right_low", VIEW_AZIMUTH["right"], distance_factor=0.9, height_factor=0.65, height_band="low"),
        _view_spec("right_mid", VIEW_AZIMUTH["right"]),
        _view_spec("right_high", VIEW_AZIMUTH["right"], distance_factor=1.15, height_factor=1.55, height_band="high"),
        _view_spec("back_low", VIEW_AZIMUTH["back"], distance_factor=0.9, height_factor=0.65, height_band="low"),
        _view_spec("back_mid", VIEW_AZIMUTH["back"]),
        _view_spec("back_high", VIEW_AZIMUTH["back"], distance_factor=1.15, height_factor=1.55, height_band="high"),
        _view_spec("left_low", VIEW_AZIMUTH["left"], distance_factor=0.9, height_factor=0.65, height_band="low"),
        _view_spec("left_mid", VIEW_AZIMUTH["left"]),
        _view_spec("left_high", VIEW_AZIMUTH["left"], distance_factor=1.15, height_factor=1.55, height_band="high"),
    ],
}


def resolve_view_specs(
    *,
    view_plan: str,
    views: list[str] | None,
    base_distance: float,
    base_height: float,
) -> list[dict[str, Any]]:
    """Return render view specs with concrete camera distance and height.

    Explicit ``--views`` preserves the old single-height behavior. Otherwise a
    named view plan can vary azimuth, distance, and height in one run.
    """
    if views:
        plan = [
            _view_spec(view, VIEW_AZIMUTH[view], height_band="custom")
            for view in views
        ]
    else:
        try:
            plan = VIEW_PLANS[view_plan]
        except KeyError as exc:
            raise ValueError(f"unknown view plan: {view_plan}") from exc

    resolved: list[dict[str, Any]] = []
    for spec in plan:
        entry = dict(spec)
        entry["distance"] = base_distance * float(spec["distance_factor"])
        entry["height_above"] = base_height * float(spec["height_factor"])
        resolved.append(entry)
    return resolved


# ---------------------------------------------------------------------------
# Scene writer (pristine template, always carries `world`).
# ---------------------------------------------------------------------------


def write_scene(
    scene_path: Path,
    *,
    world_dir: Path,
    dimension: int,
    chunk_list: list[list[int]],
    camera: dict[str, Any],
    width: int,
    height: int,
    target_spp: int,
) -> None:
    pos = camera["position"]
    ori = camera["orientation"]
    scene = {
        "sdfVersion": 9,
        "name": scene_path.parent.name,
        "world": {
            "path": str(world_dir).replace("\\", "/"),
            "dimension": dimension,
        },
        "width": width,
        "height": height,
        "spp": 0,
        "sppTarget": target_spp,
        "rayDepth": 8,
        "pathTrace": True,
        "dumpFrequency": target_spp,  # save dump once at the end
        "saveSnapshots": True,  # Chunky writes snapshots/snap.png each dump
        "postprocess": "GAMMA",
        "outputMode": "PNG",
        "exposure": 1.0,
        "emittersEnabled": False,
        "sunEnabled": True,
        "stillWater": False,
        "biomeColorsEnabled": True,
        "transparentSky": False,
        "fogDensity": 0.0,
        "skyFogDensity": 1.0,
        "renderActors": False,
        "waterWorldEnabled": False,
        "waterWorldHeight": 63.0,
        "yClipMin": -64,
        "yClipMax": 320,
        "yMin": -64,
        "yMax": 320,
        "chunkList": chunk_list,
        "camera": {
            "name": "camera 1",
            "projectionMode": "PINHOLE",
            "fov": 70.0,
            "dof": "Infinity",
            "focalOffset": 2.0,
            "shift": {"x": 0.0, "y": 0.0},
            "position": {"x": pos[0], "y": pos[1], "z": pos[2]},
            "orientation": {
                "roll": ori["roll"],
                "pitch": ori["pitch"],
                "yaw": ori["yaw"],
            },
        },
        "sun": {
            "altitude": 45.0,
            "azimuth": 135.0,
            "intensity": 1.3,
            "color": {"red": 1.0, "green": 1.0, "blue": 1.0},
            "drawTexture": True,
        },
        "sky": {
            "skyYaw": 0.0,
            "skyMirrored": True,
            "skyLight": 1.0,
            "mode": "SIMULATED",
            "horizonOffset": 0.0,
            "cloudsEnabled": False,
            "cloudSize": 64.0,
            "color": [0.0, 0.0, 0.0],
            "simulatedSky": "Preetham",
            "skyCacheResolution": 128,
            "gradient": [
                {"rgb": "0BABC7", "pos": 0.0},
                {"rgb": "75AAFF", "pos": 1.0},
            ],
        },
        "cameraPresets": {},
        "materials": {},
    }
    scene_path.write_text(json.dumps(scene, indent=2), encoding="utf-8")


def chunks_around_bbox(bbox: dict[str, Any], pad: int = 2) -> list[list[int]]:
    """Chunk coords covering the bbox footprint + a margin."""
    x0, _, z0 = bbox["min"]
    x1, _, z1 = bbox["max"]
    cx0 = (x0 - pad) // 16
    cx1 = (x1 + pad) // 16
    cz0 = (z0 - pad) // 16
    cz1 = (z1 + pad) // 16
    return [[cx, cz] for cx in range(cx0, cx1 + 1) for cz in range(cz0, cz1 + 1)]


def chunks_for_view(
    bbox: dict[str, Any], camera_pos: list[float], *, pad: int = 1
) -> list[list[int]]:
    """Chunk coords covering BOTH the bbox footprint and the camera position,
    plus the line of sight between them. Chunky only renders loaded chunks, so
    if the camera sits outside the chunkList it renders empty sky even with a
    correct look-at. This unions the bbox box and the camera box so the camera
    always has ground under its sightline to the target."""
    x0b, _, z0b = bbox["min"]
    x1b, _, z1b = bbox["max"]
    cam_x, _, cam_z = camera_pos
    xs = [x0b, x1b, int(cam_x)]
    zs = [z0b, z1b, int(cam_z)]
    cx0 = (min(xs) - pad) // 16
    cx1 = (max(xs) + pad) // 16
    cz0 = (min(zs) - pad) // 16
    cz1 = (max(zs) + pad) // 16
    return [[cx, cz] for cx in range(cx0, cx1 + 1) for cz in range(cz0, cz1 + 1)]


# ---------------------------------------------------------------------------
# Chunky launcher driver.
# ---------------------------------------------------------------------------


def run_chunky(
    launcher_jar: Path,
    chunky_home: Path,
    scene_dir: Path,
    args: list[str],
    *,
    world_dir: Path | None = None,
    proxy: str | None = None,
) -> tuple[int, str]:
    chunky_args = [*args]
    if world_dir is not None:
        chunky_args.append(str(world_dir))
    cmd = [
        "java",
        f"-Dchunky.home={chunky_home}",
        "-jar",
        str(launcher_jar),
        "-scene-dir",
        str(scene_dir),
        *chunky_args,
    ]
    # Chunky prints progress with non-UTF8 box-drawing bytes on Windows console;
    # decode leniently so subprocess.run doesn't crash mid-capture.
    env = None
    if proxy:
        host, _, port = proxy.partition(":")
        if not host or not port:
            raise ValueError("--proxy must be host:port")
        env = dict(os.environ)
        env["JAVA_TOOL_OPTIONS"] = (
            f"-Dhttp.proxyHost={host} -Dhttp.proxyPort={port} "
            f"-Dhttps.proxyHost={host} -Dhttps.proxyPort={port}"
        )
    proc = subprocess.run(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
        env=env,
    )
    return proc.returncode, (proc.stdout or "") + (proc.stderr or "")


STALE_SCENE_PATTERNS = ("*.dump", "*.dump.backup", "*.octree2", "*.json.backup")


def clean_scene_cache(scene_dir: Path) -> list[str]:
    """Remove Chunky per-scene state that can make a render reuse old framing."""
    removed: list[str] = []
    for stale in STALE_SCENE_PATTERNS:
        for p in scene_dir.glob(stale):
            if p.is_file():
                p.unlink(missing_ok=True)
                removed.append(p.name)
    snap_dir = scene_dir / "snapshots"
    if snap_dir.exists():
        shutil.rmtree(snap_dir)
        removed.append("snapshots/")
    return removed


def sync_chunky_scene_directory(chunky_home: Path, scene_root: Path) -> dict[str, Any]:
    """Make PersistentSettings.resolveSceneDirectory use this run's scene root.

    Chunky 2.4.6 honors ``-scene-dir`` for initial scene lookup, but later save,
    dump, and snapshot paths call PersistentSettings.getSceneDirectory().
    Updating chunky.json keeps those paths in the requested output tree.
    """
    settings_path = chunky_home / "chunky.json"
    try:
        settings = json.loads(settings_path.read_text(encoding="utf-8"))
        if not isinstance(settings, dict):
            settings = {}
    except (FileNotFoundError, json.JSONDecodeError):
        settings = {}
    previous = settings.get("sceneDirectory")
    settings["sceneDirectory"] = str(scene_root)
    settings_path.parent.mkdir(parents=True, exist_ok=True)
    settings_path.write_text(json.dumps(settings, indent=2), encoding="utf-8")
    return {
        "settings_path": str(settings_path),
        "previous_sceneDirectory": previous,
        "sceneDirectory": str(scene_root),
    }


def _write_log(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8", errors="replace")


def render_scene(
    launcher_jar: Path,
    chunky_home: Path,
    scene_root: Path,
    scene_path: Path,
    scene_name: str,
    world_dir: Path,
    target_spp: int,
    threads: int,
    *,
    proxy: str | None = None,
) -> dict[str, Any]:
    """Fresh render: force + reload chunks (scene carries `world`), then write
    a PNG via the separate ``-snapshot`` command (which reads the render dump
    the render step persisted). Returns a manifest-ready result dict."""
    scene_dir = scene_path.parent
    png = scene_dir / f"{scene_name}.png"
    result: dict[str, Any] = {
        "scene_root": str(scene_root),
        "scene_dir": str(scene_dir),
        "scene_json": str(scene_path),
        "png_path": str(png),
        "render_returncode": None,
        "snapshot_returncode": None,
        "auto_snapshot_path": None,
        "png_exists": False,
        "png_size_bytes": 0,
        "cache_removed": [],
        "logs": {},
        "error": None,
    }
    pristine_scene_json = scene_path.read_text(encoding="utf-8")
    result["cache_removed"] = clean_scene_cache(scene_dir)
    rc, log = run_chunky(
        launcher_jar,
        chunky_home,
        scene_root,
        [
            "-render",
            scene_name,
            "-f",
            "-reload-chunks",
            "-target",
            str(target_spp),
            "-threads",
            str(threads),
        ],
        world_dir=world_dir,
        proxy=proxy,
    )
    render_log = scene_dir / f"{scene_name}.render.log"
    _write_log(render_log, log)
    result["render_returncode"] = rc
    result["logs"]["render"] = str(render_log)
    if rc != 0:
        result["error"] = f"chunky -render {scene_name} exited {rc}"
        return result

    # Chunky rewrites the scene after render with the `world` field removed, so
    # prefer the in-render auto-snapshot that saveSnapshots:true writes to
    # <scene_dir>/snapshots/<*>-<spp>.png.
    produced = latest_snapshot(scene_dir)
    if produced is not None:
        result["auto_snapshot_path"] = str(produced)
        shutil.move(str(produced), str(png))
    if png.is_file():
        result["png_exists"] = True
        result["png_size_bytes"] = png.stat().st_size
        return result

    # Fallback: restore the pristine scene JSON, including the `world` field,
    # before a new launcher process tries to load the scene and read the dump.
    scene_path.write_text(pristine_scene_json, encoding="utf-8")
    rc2, log2 = run_chunky(
        launcher_jar,
        chunky_home,
        scene_root,
        ["-snapshot", scene_name, str(png)],
        world_dir=world_dir,
        proxy=proxy,
    )
    snapshot_log = scene_dir / f"{scene_name}.snapshot.log"
    _write_log(snapshot_log, log2)
    result["snapshot_returncode"] = rc2
    result["logs"]["snapshot"] = str(snapshot_log)
    if rc2 != 0 or not png.is_file():
        result["error"] = f"chunky -snapshot {scene_name} exited {rc2}"
        return result
    result["png_exists"] = True
    result["png_size_bytes"] = png.stat().st_size
    return result


def latest_snapshot(scene_dir: Path) -> Path | None:
    """Newest PNG under <scene_dir>/snapshots/, or None. (Kept for the fallback
    path where Chunky writes auto-snapshots instead of a dump.)"""
    snap_dir = scene_dir / "snapshots"
    if not snap_dir.is_dir():
        return None
    pngs = sorted(snap_dir.glob("*.png"), key=lambda p: p.stat().st_mtime)
    return pngs[-1] if pngs else None


# ---------------------------------------------------------------------------
# Framing-fail detector.
# ---------------------------------------------------------------------------

# Sky gradient top/bottom from the default SIMULATED Preetham sky in scenes.
SKY_TOP = (0x0B, 0xAB, 0xC7)
SKY_BOTTOM = (0x75, 0xAA, 0xFF)


def _luma(rgb: tuple[int, int, int]) -> float:
    return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]


def assess_png(png: Path) -> dict[str, Any]:
    """Read a PNG with stdlib only (no PIL) and score framing.

    Decodes 8-bit RGB/RGBA PNGs via zlib + manual filter un-filtering, which is
    enough for Chunky's output. Returns mean luminance, non-sky pixel ratio,
    and a framing_ok boolean.
    """
    raw = png.read_bytes()
    if raw[:8] != b"\x89PNG\r\n\x1a\n":
        return {"error": "not a PNG", "framing_ok": False}
    pos = 8
    width = height = bit_depth = color_type = 0
    idat = bytearray()
    while pos < len(raw):
        (length,) = struct.unpack(">I", raw[pos : pos + 4])
        ctype = raw[pos + 4 : pos + 8]
        body = raw[pos + 8 : pos + 8 + length]
        if ctype == b"IHDR":
            (width,) = struct.unpack(">I", body[0:4])
            (height,) = struct.unpack(">I", body[4:8])
            bit_depth = body[8]
            color_type = body[9]
        elif ctype == b"IDAT":
            idat.extend(body)
        elif ctype == b"IEND":
            break
        pos += 12 + length
    if bit_depth != 8:
        return {"error": f"unsupported bit depth {bit_depth}", "framing_ok": False}
    channels = {2: 3, 6: 4, 0: 1, 4: 2}.get(color_type, 3)
    decompressed = zlib.decompress(bytes(idat))
    stride = width * channels
    # Undo per-row filters.
    prev = bytearray(stride)
    pixels = bytearray()
    prev_row = bytearray(stride)
    rp = 0
    for _y in range(height):
        filt = decompressed[rp]
        rp += 1
        row = bytearray(decompressed[rp : rp + stride])
        rp += stride
        if filt == 0:  # None
            pass
        elif filt == 1:  # Sub
            for i in range(channels, stride):
                row[i] = (row[i] + row[i - channels]) & 0xFF
        elif filt == 2:  # Up
            for i in range(stride):
                row[i] = (row[i] + prev_row[i]) & 0xFF
        elif filt == 3:  # Average
            for i in range(stride):
                a = row[i - channels] if i >= channels else 0
                row[i] = (row[i] + ((a + prev_row[i]) // 2)) & 0xFF
        elif filt == 4:  # Paeth
            for i in range(stride):
                a = row[i - channels] if i >= channels else 0
                b = prev_row[i]
                c = prev_row[i - channels] if i >= channels else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                if pa <= pb and pa <= pc:
                    pr = a
                elif pb <= pc:
                    pr = b
                else:
                    pr = c
                row[i] = (row[i] + pr) & 0xFF
        pixels.extend(row)
        prev_row = row

    # Robust framing detection via a luminance histogram, independent of sky
    # *color* (Chunky's Preetham sky is greyish, the gradient mode is cyan, so a
    # color test is unreliable). Build a 256-bin luma histogram, find the sky
    # band = the brightest populated mode, then count how many pixels sit well
    # below it. A correctly-framed scene is bimodal: dark content (terrain,
    # structure) + bright sky. A framing FAIL is almost-all-sky.
    hist = [0] * 256
    lum_sum = 0.0
    total = width * height
    lumas: list[int] = []
    for y in range(height):
        for x in range(width):
            i = (y * width + x) * channels
            r, g, b = pixels[i], pixels[i + 1], pixels[i + 2]
            lum = int(_luma((r, g, b)))
            lum = 0 if lum < 0 else 255 if lum > 255 else lum
            hist[lum] += 1
            lum_sum += lum
            lumas.append(lum)
    mean_lum = lum_sum / total if total else 0.0

    # Sky band = the luma value of the brightest 25% of pixels (the sky is the
    # brightest large region in an outdoor Chunky render).
    cum = 0
    sky_threshold = 255
    for lv in range(255, -1, -1):
        cum += hist[lv]
        if cum >= total * 0.25:
            sky_threshold = lv
            break
    # Content pixels = those clearly darker than the sky band.
    content_cut = max(0, sky_threshold - 40)
    content_count = sum(hist[:content_cut])
    non_sky_ratio = content_count / total if total else 0.0
    sky_count = total - content_count
    # Luminance variance: a real scene (terrain + structure + sky) has high
    # spread; an empty all-sky frame is nearly uniform. Use stdev as a second
    # framing signal that doesn't depend on guessing the sky color.
    var_sum = 0.0
    for lv in range(256):
        var_sum += hist[lv] * (lv - mean_lum) ** 2
    lum_stdev = math.sqrt(var_sum / total) if total else 0.0
    edge_sum = 0
    edge_count = 0
    strong_edges = 0
    for y in range(height):
        row = y * width
        for x in range(width):
            lum = lumas[row + x]
            if x:
                diff = abs(lum - lumas[row + x - 1])
                edge_sum += diff
                edge_count += 1
                if diff >= 12:
                    strong_edges += 1
            if y:
                diff = abs(lum - lumas[row + x - width])
                edge_sum += diff
                edge_count += 1
                if diff >= 12:
                    strong_edges += 1
    edge_mean = edge_sum / edge_count if edge_count else 0.0
    strong_edge_ratio = strong_edges / edge_count if edge_count else 0.0
    # Framing is OK when there's real content: enough dark pixels AND enough
    # luminance spread/edges. Smooth sky gradients can have high stdev, so an
    # edge signal is required to reject false positives.
    framing_ok = (
        non_sky_ratio >= 0.10
        and lum_stdev >= 15.0
        and (edge_mean >= 3.0 or strong_edge_ratio >= 0.015)
    )
    return {
        "width": width,
        "height": height,
        "mean_lum": round(mean_lum, 2),
        "mean_luminance": round(mean_lum, 2),
        "non_sky_pixel_ratio": round(non_sky_ratio, 4),
        "sky_pixel_ratio": round(sky_count / total if total else 0.0, 4),
        "lum_stdev": round(lum_stdev, 2),
        "luminance_stdev": round(lum_stdev, 2),
        "edge_mean": round(edge_mean, 3),
        "strong_edge_ratio": round(strong_edge_ratio, 4),
        "framing_ok": framing_ok,
    }


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument(
        "--world", required=True, help="Save root dir (contains level.dat)."
    )
    p.add_argument(
        "--launcher",
        default="chunky-render/ChunkyLauncher.jar",
        help="Path to ChunkyLauncher.jar.",
    )
    p.add_argument(
        "--chunky-home",
        default="chunky-render",
        help="chunky.home dir (holds lib/ + resources/).",
    )
    p.add_argument(
        "--dimension", type=int, default=0, help="World dimension (0=overworld)."
    )
    p.add_argument(
        "--anchor",
        nargs=3,
        type=int,
        metavar=("X", "Y", "Z"),
        default=None,
        help="Approx structure origin (e.g. the placeat coords). Selects the "
        "nearest structure-block cluster.",
    )
    p.add_argument(
        "--tag-prefix",
        default=None,
        help="Advisory: only informational (palette names don't carry tags in "
        "1.21). Kept for future use.",
    )
    p.add_argument(
        "--search-radius",
        type=int,
        default=64,
        help="Block radius around the anchor considered for the cluster.",
    )
    p.add_argument(
        "--views",
        nargs="+",
        default=None,
        choices=["front", "right", "back", "left"],
        help=(
            "Legacy explicit views to render at one height. Overrides "
            "--view-plan when supplied."
        ),
    )
    p.add_argument(
        "--view-plan",
        choices=sorted(VIEW_PLANS),
        default="survey",
        help=(
            "Named camera plan used when --views is not supplied. Default "
            "'survey' renders four mid-height cardinal views plus four high "
            "diagonal views; 'height-sweep' renders low/mid/high from each side."
        ),
    )
    p.add_argument(
        "--distance",
        type=float,
        default=None,
        help="Camera horizontal distance from target. Default = 1.6x max bbox dim.",
    )
    p.add_argument(
        "--height",
        type=float,
        default=None,
        help="Camera height above target center. Default = 0.5x bbox height + 4.",
    )
    p.add_argument("--width", type=int, default=640)
    p.add_argument("--height-px", dest="height_px", type=int, default=480)
    p.add_argument("--spp", type=int, default=20)
    p.add_argument("--threads", type=int, default=6)
    p.add_argument(
        "--chunk-pad",
        type=int,
        default=4,
        help=(
            "Extra chunks around the bbox-to-camera rectangle for each view. "
            "Higher values reduce empty-background clipping at the cost of render time."
        ),
    )
    p.add_argument(
        "--out",
        default="chunky-render/renders",
        help="Output dir (scene dirs + PNGs + a manifest json).",
    )
    p.add_argument(
        "--proxy",
        default=None,
        help="Optional host:port for JAVA_TOOL_OPTIONS proxy (set if rerunning setup).",
    )
    return p.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    world_dir = (ROOT / args.world).resolve()
    launcher = (ROOT / args.launcher).resolve()
    home = (ROOT / args.chunky_home).resolve()
    out_dir = (ROOT / args.out).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    if not launcher.is_file():
        print(f"ERROR: launcher jar not found: {launcher}", file=sys.stderr)
        return 2

    settings_update = sync_chunky_scene_directory(home, out_dir)
    print(
        "[chunky] sceneDirectory -> "
        f"{settings_update['sceneDirectory']} "
        f"(was {settings_update['previous_sceneDirectory']})"
    )

    print(f"[scan] scanning {world_dir / 'region'} for structure bbox ...")
    bbox = scan_structure_bbox(
        world_dir,
        anchor=tuple(args.anchor) if args.anchor else None,
        tag_prefix=args.tag_prefix,
        search_radius=args.search_radius,
    )
    target = tuple(bbox["center"])
    print(
        f"[scan] bbox min={bbox['min']} max={bbox['max']} "
        f"size={bbox['size']} blocks={bbox['block_count']}"
    )
    print(f"[scan] target center = ({target[0]:.1f}, {target[1]:.1f}, {target[2]:.1f})")
    print(f"[scan] top blocks: {bbox['top_blocks'][:6]}")

    chunk_list = chunks_around_bbox(bbox, pad=2)
    print(f"[scan] chunkList ({len(chunk_list)} chunks): {chunk_list}")

    # Default camera distance/height scaled to the bbox so framing adapts.
    max_dim = max(bbox["size"])
    distance = args.distance if args.distance is not None else max_dim * 1.6 + 8
    height = (
        args.height
        if args.height is not None
        else bbox["size"][1] * 0.5 + 4.0
    )
    view_specs = resolve_view_specs(
        view_plan=args.view_plan,
        views=args.views,
        base_distance=distance,
        base_height=height,
    )
    plan_name = "custom-views" if args.views else args.view_plan
    print(
        f"[cam] base_distance={distance:.1f} base_height_above={height:.1f} "
        f"view_plan={plan_name} views={len(view_specs)}"
    )

    manifest: dict[str, Any] = {
        "schema_version": 1,
        "world": str(world_dir),
        "launcher": str(launcher),
        "chunky_home": str(home),
        "dimension": args.dimension,
        "out_dir": str(out_dir),
        "anchor": args.anchor,
        "bbox": bbox,
        "target_center": list(target),
        "chunk_list": chunk_list,
        "view_plan": plan_name,
        "base_distance": distance,
        "base_height_above": height,
        # Kept for older manifest readers; per-view entries record the concrete
        # values used by multi-height plans.
        "distance": distance,
        "height_above": height,
        "width": args.width,
        "height_px": args.height_px,
        "spp": args.spp,
        "threads": args.threads,
        "chunk_pad": args.chunk_pad,
        "chunky_settings": settings_update,
        "views": [],
    }

    overall_ok = True
    for view_spec in view_specs:
        view = str(view_spec["name"])
        az = float(view_spec["azimuth_deg"])
        view_distance = float(view_spec["distance"])
        view_height = float(view_spec["height_above"])
        cam = solve_camera(
            target,
            azimuth_deg=az,
            distance=view_distance,
            height_above=view_height,
        )
        scene_name = f"view_{view}"
        # Each view gets its OWN scene directory so its snapshots/, dump and
        # octree never collide with another view's (Chunky writes auto-snapshots
        # to <scene_dir>/snapshots/, shared across scenes otherwise).
        view_dir = out_dir / scene_name
        view_dir_recreated = view_dir.exists()
        if view_dir.exists():
            shutil.rmtree(view_dir)
        view_dir.mkdir(parents=True)
        scene_path = view_dir / f"{scene_name}.json"
        # Per-view chunkList: the structure footprint, camera position, line of
        # sight, and an environment margin. Chunky renders unloaded chunks as
        # empty background, so bbox-only chunks make valid shots look clipped.
        view_chunk_list = chunks_for_view(bbox, cam["position"], pad=args.chunk_pad)
        write_scene(
            scene_path,
            world_dir=world_dir,
            dimension=args.dimension,
            chunk_list=view_chunk_list,
            camera=cam,
            width=args.width,
            height=args.height_px,
            target_spp=args.spp,
        )
        print(
            f"[render] {view} az={az:.0f} -> "
            f"pos=({cam['position'][0]:.1f},{cam['position'][1]:.1f},{cam['position'][2]:.1f}) "
            f"distance={view_distance:.1f} height={view_height:.1f} "
            f"yaw={cam['yaw_deg']:.1f}deg/{cam['orientation']['yaw']:.4f}rad "
            f"pitch={cam['pitch_deg']:.1f}deg/{cam['orientation']['pitch']:.4f}rad "
            f"chunks={len(view_chunk_list)}"
        )
        render_result = render_scene(
            launcher,
            home,
            out_dir,
            scene_path,
            scene_name,
            world_dir,
            args.spp,
            args.threads,
            proxy=args.proxy,
        )
        png = Path(render_result["png_path"])
        camera_summary = {
            "position": cam["position"],
            "target": cam["target"],
            "yaw_deg": round(cam["yaw_deg"], 3),
            "pitch_deg": round(cam["pitch_deg"], 3),
            "roll_deg": round(cam["roll_deg"], 3),
            "yaw_rad": round(cam["orientation"]["yaw"], 6),
            "pitch_rad": round(cam["orientation"]["pitch"], 6),
            "roll_rad": round(cam["orientation"]["roll"], 6),
            "forward": [round(v, 4) for v in cam["forward"]],
            "distance": round(cam["distance"], 2),
        }
        if not png.is_file():
            entry = {
                "view": view,
                "azimuth_deg": az,
                "scene_dir": str(view_dir),
                "scene_json": str(scene_path),
                "view_dir_recreated": view_dir_recreated,
                "view_spec": view_spec,
                "camera": camera_summary,
                "chunk_list": view_chunk_list,
                "render": render_result,
                "png": display_path(png),
                "png_exists": False,
                "png_size_bytes": 0,
                "error": render_result.get("error") or "PNG not produced",
                "framing_ok": False,
            }
            manifest["views"].append(entry)
            overall_ok = False
            print(
                f"[render] {view}: PNG MISSING "
                f"error={entry['error']} logs={render_result.get('logs')}"
            )
            continue
        assessment = assess_png(png)
        entry = {
            "view": view,
            "azimuth_deg": az,
            "scene_dir": str(view_dir),
            "scene_json": str(scene_path),
            "view_dir_recreated": view_dir_recreated,
            "view_spec": view_spec,
            "camera": camera_summary,
            "chunk_list": view_chunk_list,
            "render": render_result,
            "png": display_path(png),
            "png_exists": True,
            "png_size_bytes": png.stat().st_size,
            "assessment": assessment,
            "framing_ok": assessment.get("framing_ok", False),
        }
        manifest["views"].append(entry)
        status = "OK" if assessment.get("framing_ok") else "FRAMING FAILED"
        if not assessment.get("framing_ok"):
            overall_ok = False
        print(
            f"[render] {view}: {status} "
            f"mean_lum={assessment.get('mean_luminance')} "
            f"non_sky={assessment.get('non_sky_pixel_ratio')} "
            f"-> {display_path(png)}"
        )

    manifest["overall_framing_ok"] = overall_ok
    manifest_path = out_dir / "render_manifest.json"
    manifest_path.write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    print(f"[done] manifest -> {display_path(manifest_path)}")
    print(f"[done] overall framing {'OK' if overall_ok else 'FAILED'}")
    return 0 if overall_ok else 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
