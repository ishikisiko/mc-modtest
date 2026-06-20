#!/usr/bin/env python3
"""Generate deterministic town-plan dumps and top-down previews."""

from __future__ import annotations

import argparse
import html
import os
import shutil
import struct
import sys
import zlib
from pathlib import Path
from typing import Dict, Iterable, Tuple

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from buildgen.groups import get_group  # noqa: E402
from buildgen.town import TownPlan, TownSite, generate_town_plan, write_plan_json  # noqa: E402

DEFAULT_SEED = 123  # sentinel: +101*6 lands each step on a distinct wall family,
                       # so the default --count 6 run covers all six
                       # (octagon, trapezoid, circle, square, dshape, oval)


def purge_old_plan_previews(out_root: Path, prefix: str) -> int:
    """Remove previous ``<prefix>_*`` plan dumps under ``out_root``.

    Keeps the preview tree from accumulating stale ad-hoc runs (different
    ``--seed`` values, partial failures, etc.) so each generator invocation
    leaves a clean canonical set behind it.
    """
    removed = 0
    if not out_root.is_dir():
        return 0
    for child in out_root.iterdir():
        if child.is_dir() and (child.name == prefix or child.name.startswith(prefix + "_")):
            shutil.rmtree(child)
            removed += 1
    return removed


RGBA = Tuple[int, int, int, int]

COLORS: Dict[str, RGBA] = {
    "background": (238, 232, 219, 255),
    "wall": (86, 83, 78, 255),
    "gate": (157, 99, 56, 255),
    "spine": (73, 112, 139, 255),
    "lane": (142, 124, 92, 255),
    "parcel0": (178, 180, 143, 255),
    "parcel1": (169, 151, 105, 255),
    "parcel2": (153, 108, 82, 255),
    "landmark": (135, 68, 66, 255),
    "negative": (79, 134, 113, 255),
    # Civic-precinct framing (drawn over the core so the walled compound,
    # spirit way, colonnade, and precinct gate read in the top-down preview).
    "precinct_wall": (60, 58, 54, 255),
    "colonnade": (120, 96, 64, 255),
    "spirit_way": (200, 178, 110, 255),
    "precinct_gate": (196, 142, 60, 255),
    "side_gate": (110, 110, 118, 255),
}


def write_png(path: Path, width: int, height: int, buf: bytes) -> None:
    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    raw = bytearray()
    stride = width * 4
    for y in range(height):
        raw.append(0)
        raw.extend(buf[y * stride:(y + 1) * stride])
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as f:
        f.write(png)


def fill_cell(buf: bytearray, width: int, x: int, z: int, scale: int, color: RGBA) -> None:
    px0 = x * scale
    py0 = z * scale
    for py in range(py0, py0 + scale):
        for px in range(px0, px0 + scale):
            i = (py * width + px) * 4
            buf[i:i + 4] = bytes(color)


def render_plan_png(plan: TownPlan, path: Path, scale: int = 8) -> None:
    width = plan.site.width * scale
    height = plan.site.depth * scale
    buf = bytearray(COLORS["background"] * (width * height))

    def paint(cells: Iterable[Tuple[int, int]], color: RGBA) -> None:
        for x, z in cells:
            fill_cell(buf, width, x, z, scale, color)

    paint(plan.wall_cells, COLORS["wall"])
    for gate in plan.gates:
        paint(gate.cells, COLORS["gate"])
    paint(plan.spine, COLORS["spine"])
    paint(plan.lane_cells, COLORS["lane"])
    for region in plan.negative_spaces:
        paint(region.cells, COLORS["negative"])
    for parcel in plan.parcels:
        if parcel.dominant_landmark:
            color = COLORS["landmark"]
        else:
            color = COLORS[f"parcel{min(parcel.importance_tier, 2)}"]
        paint(parcel.cells, color)
    # Civic-precinct framing: wall, colonnade, spirit way, precinct + side gates.
    axis = plan.ritual_axis or {}
    paint({tuple(c) for c in axis.get("colonnade_cells", [])}, COLORS["colonnade"])
    paint({tuple(c) for c in axis.get("spirit_way_cells", [])}, COLORS["spirit_way"])
    paint({tuple(c) for c in axis.get("precinct_wall_cells", [])}, COLORS["precinct_wall"])
    paint({tuple(c) for c in axis.get("side_gate_cells", []) + axis.get("precinct_side_gate_cells", [])},
          COLORS["side_gate"])
    paint({tuple(c) for c in axis.get("precinct_gate_cells", [])}, COLORS["precinct_gate"])
    write_png(path, width, height, bytes(buf))


def write_viewer(plan: TownPlan, out_dir: Path) -> Path:
    roles = "".join(
        f"<li>{html.escape(p.id)}: {html.escape(p.role)}, tier {p.importance_tier}</li>"
        for p in plan.parcels
    )
    page = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Town plan s{plan.seed} (seed={plan.seed})</title>
<style>
body {{ margin: 0; font-family: system-ui, sans-serif; background: #eee8db; color: #252525; }}
main {{ max-width: 980px; margin: 0 auto; padding: 24px; }}
img {{ image-rendering: pixelated; width: min(100%, 768px); border: 1px solid #555; background: #eee8db; }}
.legend {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 8px 16px; margin: 18px 0; }}
.swatch {{ display: inline-block; width: 1em; height: 1em; margin-right: 0.4em; vertical-align: -0.15em; border: 1px solid #3336; }}
li {{ margin: 0.25em 0; }}
</style>
</head>
<body>
<main>
<h1>Town plan s{plan.seed} (seed={plan.seed})</h1>
<img src="plan.png" alt="Top-down generated town plan">
<div class="legend">
<div><span class="swatch" style="background:#56534e"></span>wall</div>
<div><span class="swatch" style="background:#9d6338"></span>gate</div>
<div><span class="swatch" style="background:#49708b"></span>main street</div>
<div><span class="swatch" style="background:#8e7c5c"></span>lane</div>
<div><span class="swatch" style="background:#874442"></span>dominant landmark</div>
<div><span class="swatch" style="background:#4f8671"></span>negative space</div>
<div><span class="swatch" style="background:#3c3a36"></span>precinct wall</div>
<div><span class="swatch" style="background:#786040"></span>colonnade</div>
<div><span class="swatch" style="background:#c8b26e"></span>spirit way</div>
<div><span class="swatch" style="background:#c48e3c"></span>precinct gate</div>
</div>
<h2>Parcels</h2>
<ul>{roles}</ul>
</main>
</body>
</html>
"""
    viewer = out_dir / "viewer.html"
    viewer.write_text(page, encoding="utf-8")
    return viewer


def write_index(out_root: Path) -> Path:
    viewers = sorted(p for p in out_root.glob("*/viewer.html"))
    index = out_root / "index.html"
    index.parent.mkdir(parents=True, exist_ok=True)

    # Prefer the categorized index shared with preview_structure.py over a flat
    # list, scanning every existing preview (structures + town plans) so that
    # regenerating town plans never flattens the structure categories. Town
    # plans classify into the "other" family. Fall back to a flat list only if
    # the shared renderer is unavailable or declines (<= 1 viewer).
    try:
        from preview_structure import RenderResult, render_preview_index
        structure_dir = REPO_ROOT / "src" / "main" / "resources" / "data" / "myvillage" / "structure"
        results = []
        for v in viewers:
            stem = v.parent.name
            nbt = structure_dir / f"{stem}.nbt"
            source = str(nbt if nbt.exists() else v)
            results.append(RenderResult(0, source, stem, str(v.parent), str(v)))
        # render_preview_index writes out_root/index.html itself and returns its
        # path (or "" when it declines for <= 1 viewer).
        written = render_preview_index(str(out_root), results)
        if written:
            return Path(written)
    except Exception:
        pass

    links = "\n".join(
        f'<li><a href="{html.escape(str(v.relative_to(out_root)))}">{html.escape(v.parent.name)}</a></li>'
        for v in viewers
    )
    page = f"""<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>Preview index</title></head>
<body>
<h1>Preview index</h1>
<ul>
{links}
</ul>
</body>
</html>
"""
    index.write_text(page, encoding="utf-8")
    return index


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED,
                        help=f"base seed (default {DEFAULT_SEED}); the generated dir names "
                             "carry an `s` prefix so the seed never collides with real dates "
                             "in the listing. The default base is a sentinel picked so that "
                             "`--count 6` covers all six perimeter wall families "
                             "(octagon/trapezoid/circle/square/dshape/oval); smaller --count "
                             "values only show the leading prefix of that sequence.")
    parser.add_argument("--count", type=int, default=6,
                        help="number of plans to render (default 6 — the default base seed "
                             "covers every wall family in 6 steps; pass --count 3 to mirror "
                             "the older compact preview set)")
    parser.add_argument("--width", type=int, default=160)
    parser.add_argument("--depth", type=int, default=160)
    parser.add_argument("--out", default=str(REPO_ROOT / "out" / "preview"))
    parser.add_argument("--keep-existing", action="store_true",
                        help="skip the cleanup of previous town_plan_s* dumps in --out")
    args = parser.parse_args()

    group = get_group("cultivation_town")
    brief = list(group.scale_params.get("district_brief", []))
    out_root = Path(args.out)
    if not args.keep_existing:
        purged = purge_old_plan_previews(out_root, "town_plan")
        if purged:
            print(f"purged {purged} previous town_plan_* dump(s) under {out_root}")
    site = TownSite(width=args.width, depth=args.depth)
    for index in range(args.count):
        seed = args.seed + index * 101
        plan = generate_town_plan(seed, site, brief)
        out_dir = out_root / f"town_plan_s{seed}"
        out_dir.mkdir(parents=True, exist_ok=True)
        write_plan_json(plan, out_dir / "plan.json")
        render_plan_png(plan, out_dir / "plan.png")
        viewer = write_viewer(plan, out_dir)
        print(f"OK town_plan_s{seed}: {viewer.relative_to(REPO_ROOT)}")
    index = write_index(out_root)
    print(f"index: {index.relative_to(REPO_ROOT)}")
    # Re-run structure preview index to merge categories (town_plan entries)
    # are picked up by preview_structure.py --all "other" family.
    print("hint: run preview_structure.py --all to merge index categories")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
