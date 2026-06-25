#!/usr/bin/env python3
"""Procedurally author the hero 假山 micro-voxel sculpt -> docs/rockery_compressed.json.

Replaces the hand-authored crude cone with a layered-ledge 太湖石 (Taihu-rock)
form matching docs/mt.png: a stepped/terraced pyramid (收分梯层) with craggy
irregular edges (皱), a recessed front waterfall groove + foot pond, mossy ledge
tops and base (青苔), and a small leaning summit tree. Output is the same 48³ RLE
schema (palette a/s/m/w/g/t/l) the bake pipeline already consumes; water voxels
are cosmetic-for-preview (real water is placed, contained, by derive_hero_rockery).

Deterministic (fixed seed) -> byte-stable artifact.

Usage: python3 tools/buildgen/gen_hero_rockery_sculpt.py [-o docs/rockery_compressed.json]
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path
from typing import Dict, List, Tuple

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUT = REPO_ROOT / "docs" / "rockery_compressed.json"

N = 48                      # grid size (3 blocks * 16)
CX = CZ = 24               # plan center
SEED = 1337

# --- silhouette (terraced cone) ---
PEAK_Y = 39                # top of the rock mass (tree sits above, to ~46)
R_MAX = 23.0               # plan radius at the foot
TERRACE_RISE = 5           # vertical step per ledge (bigger -> chunkier ledges)
SQUARE_BLEND = 0.45        # 0=round plan, 1=square plan (Taihu reads squarish)
EDGE_JITTER = 4.5          # craggy-edge amplitude (in plan-radius units)
SUMMIT_R = 4.0             # plan radius of the flat rocky summit platform

# --- materials --- (reference is stone-dominant; moss is an accent)
MOSS_BASE_Y = 4            # only the lowest courses are solid moss (山脚青苔)
MOSS_LEDGE_BIAS = 0.66     # higher -> fewer mossy ledge tops
MOSS_PATCH_PERIOD = 7.0    # moss clusters (not uniform speckle)

PALETTE = {
    "a": "Air - empty micro-block",
    "s": "Stone - vanilla stone texture",
    "m": "Mossy Stone - vanilla mossy stone bricks texture",
    "w": "Water - vanilla water source",
    "g": "Grass - top texture of grass block",
    "t": "Oak Wood - vanilla oak log",
    "l": "Oak Leaves - vanilla oak leaves",
}


def _hash01(ix: int, iz: int, seed: int) -> float:
    h = (ix * 374761393 + iz * 668265263 + seed * 362437) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 0xFFFF


def vnoise(x: float, z: float, period: float, seed: int) -> float:
    """Smooth value noise in [0,1] on a lattice of the given period."""
    gx, gz = x / period, z / period
    ix, iz = math.floor(gx), math.floor(gz)
    fx, fz = gx - ix, gz - iz
    sx, sz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
    v00, v10 = _hash01(ix, iz, seed), _hash01(ix + 1, iz, seed)
    v01, v11 = _hash01(ix, iz + 1, seed), _hash01(ix + 1, iz + 1, seed)
    a = v00 + (v10 - v00) * sx
    b = v01 + (v11 - v01) * sx
    return a + (b - a) * sz


def plan_radius(dx: float, dz: float) -> float:
    cheb = max(abs(dx), abs(dz))          # square metric
    euc = math.hypot(dx, dz)              # round metric
    return SQUARE_BLEND * cheb + (1 - SQUARE_BLEND) * euc


def column_height(x: int, z: int) -> int:
    """Terraced height of the rock column at (x,z); -1 = no rock."""
    dx, dz = x - CX, z - CZ
    r = plan_radius(dx, dz)
    # craggy edges: two octaves of undulating jitter on the effective radius
    r += (vnoise(x, z, 13.0, SEED) - 0.5) * 2 * EDGE_JITTER
    r += (vnoise(x, z, 5.0, SEED + 3) - 0.5) * EDGE_JITTER
    if r >= R_MAX:
        return -1
    if r <= SUMMIT_R:                      # flat rocky summit platform
        return PEAK_Y
    cont = PEAK_Y * (1.0 - r / R_MAX)     # smooth cone height
    h = int(cont // TERRACE_RISE) * TERRACE_RISE   # quantize to ledges
    return max(0, min(PEAK_Y, h))


def build_field() -> Dict[Tuple[int, int, int], str]:
    field: Dict[Tuple[int, int, int], str] = {}
    heights: Dict[Tuple[int, int], int] = {}
    for x in range(N):
        for z in range(N):
            h = column_height(x, z)
            heights[(x, z)] = h
            if h < 0:
                continue
            patch = vnoise(x, z, MOSS_PATCH_PERIOD, SEED + 7)
            for y in range(0, h + 1):
                is_top = (y == h)
                if y <= MOSS_BASE_Y:
                    ch = "m"                      # solid mossy foot
                elif is_top and patch > MOSS_LEDGE_BIAS:
                    ch = "m"                      # mossy ledge top (clustered)
                else:
                    ch = "s"                      # bare Taihu stone (dominant)
                field[(x, y, z)] = ch

    _carve_waterfall_and_pond(field, heights)
    _plant_summit_tree(field, heights)
    return field


def _front_face_z(heights, x: int, y: int):
    """Front-most z where rock exists at column-height y (the terraced face)."""
    best = None
    for z in range(N):
        if heights.get((x, z), -1) >= y:
            best = z
    return best


def _carve_waterfall_and_pond(field, heights) -> None:
    """Carve a spring-fed cascade that issues FROM INSIDE the rock + a foot pond.

    The face recedes with the terraces, so a fixed-z water column would float in
    front of the upper mountain. Instead the cascade *follows the receding face*
    at every height (always cut INTO the rock) and is capped by solid rock above
    a spring mouth — the water visibly wells out of a grotto inside the 山体 and
    sheets down the steps into the pond. Nothing floats; ``w`` voxels are the
    single source of truth for both preview and (at bake) contained placement.
    """
    chan = range(CX - 1, CX + 2)               # 3-wide cascade
    outlet_y, pond_top = 25, 3                  # spring mouth; rock stays solid above

    # 1) foot pond: a shallow basin EMBEDDED in the low front foot of the rock,
    #    rimmed by the rising rock (= connected to 山体) and pulled inward so it
    #    does not protrude onto the flat apron. Fed by the lower fall.
    front_z = _front_face_z(heights, CX, 1)     # foot front at the center column
    pond_cz = front_z - 1                       # center tucked just inside the face
    rx, rz = 6.5, 3.2                           # wide sideways, shallow forward
    for x in range(CX - 8, CX + 9):
        for z in range(front_z - 4, front_z + 3):
            dx, dz = (x - CX) / rx, (z - pond_cz) / rz
            if dx * dx + dz * dz > 1.0:        # ellipse -> natural shoreline
                continue
            h = heights.get((x, z), -1)
            if h > 9:                          # don't gouge the rising mountain
                continue
            for y in range(1, max(3, h + 1)):  # open the column above the floor
                field.pop((x, y, z), None)
            field[(x, 0, z)] = "s"             # pool floor (continuous with foot)
            field[(x, 1, z)] = "w"
            field[(x, 2, z)] = "w"             # surface (2 deep, flush)

    # 2) cascade channel that hugs the receding terraced face. At each lower
    #    step, fill the horizontal ledge between the previous and current face,
    #    producing one 6-neighbour-connected water route instead of detached
    #    full-block curtains floating outside the mountain.
    prev = {x: None for x in chan}
    for y in range(outlet_y, pond_top - 1, -1):
        for x in chan:
            fz = _front_face_z(heights, x, y)
            if fz is None:
                continue
            pf = prev[x] if prev[x] is not None else fz
            for z in range(min(pf, fz), max(pf, fz) + 1):
                field.pop((x, y, z), None)
                field[(x, y, z)] = "w"
            prev[x] = fz

    # 3) spring grotto: tunnel back from the face and fill the tunnel through to
    #    the first cascade voxel. The water therefore visibly begins inside the
    #    mountain and is topologically connected to the fall below.
    for x in chan:
        fz = _front_face_z(heights, x, outlet_y)
        if fz is None:
            continue
        for z in range(fz - 3, fz + 1):
            field.pop((x, outlet_y, z), None)
            field[(x, outlet_y, z)] = "w"
        field[(x, outlet_y - 1, fz - 3)] = "s"  # dark grotto floor
        field[(x, outlet_y - 1, fz - 2)] = "s"


def _plant_summit_tree(field, heights) -> None:
    h_peak = max(heights.values())
    # grass cap over the summit platform (cells near peak height)
    for x in range(CX - 3, CX + 4):
        for z in range(CZ - 3, CZ + 4):
            h = heights.get((x, z), -1)
            if h >= h_peak - TERRACE_RISE:
                field[(x, h + 1, z)] = "g"
    # Miniature bonsai, entirely inside the summit's 16³ full-block cell.
    # The old dressing pass expanded this into ordinary full blocks; this
    # source geometry is now baked directly, so silhouette matters.
    base_y = h_peak + 1
    trunk_centres = [
        (21, base_y, 23), (21, base_y + 1, 23),
        (22, base_y + 2, 23), (22, base_y + 3, 23),
        (23, base_y + 4, 23), (24, base_y + 5, 23),
        (25, base_y + 6, 23),
    ]
    trunk: set[Tuple[int, int, int]] = set()
    for tx, ty, tz in trunk_centres:
        # Two-micro-voxel trunk gives the tiny tree a readable stroke while
        # retaining its half-block overall scale.
        for ox, oz in ((0, 0), (1, 0), (0, 1)):
            p = (tx + ox, ty, tz + oz)
            if p[1] < N:
                trunk.add(p)

    # Two lateral branches break the lollipop silhouette.
    for x in range(18, 24):
        trunk.add((x, base_y + 3, 23))
    for x in range(24, 29):
        trunk.add((x, base_y + 5, 23))
    for z in range(20, 24):
        trunk.add((23, base_y + 4, z))

    leaves: set[Tuple[int, int, int]] = set()

    def foliage_pad(cx: int, cy: int, cz: int, rx: int, rz: int) -> None:
        """Flat, irregular 云片 canopy pad rather than a spherical leaf blob."""
        for dx in range(-rx, rx + 1):
            for dz in range(-rz, rz + 1):
                if (dx * dx) / max(1, rx * rx) + (dz * dz) / max(1, rz * rz) > 1.0:
                    continue
                if _hash01(cx + dx, cz + dz, SEED + cy) < 0.16:
                    continue
                leaves.add((cx + dx, cy, cz + dz))
                if abs(dx) + abs(dz) <= max(1, (rx + rz) // 2):
                    leaves.add((cx + dx, min(N - 1, cy + 1), cz + dz))

    foliage_pad(19, base_y + 4, 23, 3, 3)  # low left pad
    foliage_pad(27, base_y + 6, 23, 4, 3)  # dominant leaning crown
    foliage_pad(23, base_y + 5, 19, 3, 2)  # rear counterweight
    foliage_pad(25, base_y + 7, 24, 2, 2)  # sparse crown tip

    for p in leaves:
        if all(0 <= c < N for c in p):
            field.setdefault(p, "l")
    for p in trunk:                  # keep trunk/branches visible through pads
        if all(0 <= c < N for c in p):
            field[p] = "t"


def encode_rle(field: Dict[Tuple[int, int, int], str]) -> dict:
    ys = sorted({y for (_x, y, _z) in field})
    layers = []
    for y in ys:
        rows = []
        for z in range(N):
            cells = ["a"] * N
            for x in range(N):
                ch = field.get((x, y, z))
                if ch:
                    cells[x] = ch
            # run-length encode
            row = []
            i = 0
            while i < N:
                j = i
                while j < N and cells[j] == cells[i]:
                    j += 1
                row.append(f"{j - i}{cells[i]}")
                i = j
            rows.append("".join(row))
        layers.append({"y": y, "rows": rows})
    return {"size": [N, N, N], "palette": PALETTE, "layers": layers}


def main(argv: List[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("-o", "--out", default=str(DEFAULT_OUT))
    args = ap.parse_args(argv)
    import json
    field = build_field()
    data = encode_rle(field)
    Path(args.out).write_text(json.dumps(data, indent=2))
    from collections import Counter
    c = Counter(field.values())
    print(f"wrote {args.out}")
    print(f"  layers={len(data['layers'])}  solid micro-cubes={len(field)}")
    print(f"  materials={dict(c)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(__import__("sys").argv[1:]))
