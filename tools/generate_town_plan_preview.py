#!/usr/bin/env python3
"""Generate deterministic town-plan dumps and top-down previews."""

from __future__ import annotations

import argparse
import html
import os
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
<title>Town plan {plan.seed}</title>
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
<h1>Town plan {plan.seed}</h1>
<img src="plan.png" alt="Top-down generated town plan">
<div class="legend">
<div><span class="swatch" style="background:#56534e"></span>wall</div>
<div><span class="swatch" style="background:#9d6338"></span>gate</div>
<div><span class="swatch" style="background:#49708b"></span>main street</div>
<div><span class="swatch" style="background:#8e7c5c"></span>lane</div>
<div><span class="swatch" style="background:#874442"></span>dominant landmark</div>
<div><span class="swatch" style="background:#4f8671"></span>negative space</div>
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
    index = out_root / "index.html"
    index.parent.mkdir(parents=True, exist_ok=True)
    index.write_text(page, encoding="utf-8")
    return index


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=int, default=20260618)
    parser.add_argument("--count", type=int, default=3)
    parser.add_argument("--width", type=int, default=64)
    parser.add_argument("--depth", type=int, default=56)
    parser.add_argument("--out", default=str(REPO_ROOT / "out" / "preview"))
    args = parser.parse_args()

    group = get_group("cultivation_town")
    brief = dict(group.scale_params.get("soft_functional_brief", {}))
    out_root = Path(args.out)
    site = TownSite(width=args.width, depth=args.depth)
    for index in range(args.count):
        seed = args.seed + index * 101
        plan = generate_town_plan(seed, site, brief)
        out_dir = out_root / f"town_plan_{seed}"
        out_dir.mkdir(parents=True, exist_ok=True)
        write_plan_json(plan, out_dir / "plan.json")
        render_plan_png(plan, out_dir / "plan.png")
        viewer = write_viewer(plan, out_dir)
        print(f"OK town_plan_{seed}: {viewer.relative_to(REPO_ROOT)}")
    index = write_index(out_root)
    print(f"index: {index.relative_to(REPO_ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
