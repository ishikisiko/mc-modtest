#!/usr/bin/env python3
"""Generate deterministic terraced sect-compound plan dumps and top-down previews.

Mirrors generate_town_plan_preview.py: dumps the plan JSON, renders a top-down
PNG (terraces by elevation tier, ritual axis, slotted volumes by archetype,
covered galleries, and the detached-spire flying-bridge feature), writes a
viewer.html, and merges into the shared preview aggregate index.
"""

from __future__ import annotations

import argparse
import html
import json
import sys
from pathlib import Path
from typing import Dict, Iterable, Tuple

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from buildgen.sect import (  # noqa: E402
    SectSite,
    generate_sect_plan,
)
from buildgen.sect_mountain import (  # noqa: E402
    DEFAULT_SKIRT_RADIUS as SKIRT_RADIUS,
    derive_mountain,
    flat_natural,
)
from generate_town_plan_preview import (  # noqa: E402
    DEFAULT_SEED,
    purge_old_plan_previews,
    write_png,
    write_index,
)

RGBA = Tuple[int, int, int, int]

COLORS: Dict[str, RGBA] = {
    "background": (235, 230, 218, 255),
    "terrace0": (206, 198, 170, 255),
    "terrace1": (188, 174, 134, 255),
    "terrace2": (166, 137, 95, 255),
    "terrace3": (138, 99, 70, 255),
    "axis": (73, 112, 139, 255),
    "sect_gate": (157, 99, 56, 255),
    "sect_main_hall": (122, 44, 44, 255),
    "scripture_pavilion": (90, 78, 142, 255),
    "alchemy_room": (96, 132, 86, 255),
    "disciple_quarters": (150, 140, 110, 255),
    "pagoda": (180, 120, 60, 255),
    "pavilion": (180, 120, 60, 255),
    "bell_drum_tower": (120, 110, 130, 255),
    "gallery": (110, 80, 50, 255),
    "detached": (140, 60, 130, 255),
    "bridge": (200, 130, 40, 255),
}


def _tier_color(tier: int) -> RGBA:
    return COLORS.get(f"terrace{min(tier, 3)}", COLORS["terrace0"])


def _rect_cells(x0: int, z0: int, x1: int, z1: int) -> Iterable[Tuple[int, int]]:
    if x1 < x0 or z1 < z0:
        return []
    return ((x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1))


def fill_cell(buf: bytearray, width: int, x: int, z: int, scale: int, color: RGBA) -> None:
    px0 = x * scale
    py0 = z * scale
    for py in range(py0, py0 + scale):
        for px in range(px0, px0 + scale):
            i = (py * width + px) * 4
            buf[i:i + 4] = bytes(color)


def fill_rect(buf: bytearray, width: int, rect, scale: int, color: RGBA) -> None:
    x0, z0, x1, z1 = rect
    for x, z in _rect_cells(x0, z0, x1, z1):
        if 0 <= x and 0 <= z:
            fill_cell(buf, width, x, z, scale, color)


def render_plan_png(plan, path: Path, scale: int = 6) -> None:
    width = plan.site.width * scale
    height = plan.site.depth * scale
    buf = bytearray(COLORS["background"] * (width * height))

    # terraces painted by the highest importance tier they carry
    tier_by_terrace: Dict[int, int] = {}
    for s in plan.slots:
        tier_by_terrace[s.terrace_index] = max(
            tier_by_terrace.get(s.terrace_index, 0), s.importance_tier)
    for terrace in plan.terraces:
        fill_rect(buf, width, terrace.bounds, scale,
                  _tier_color(tier_by_terrace.get(terrace.index, 0)))

    # ritual axis
    for x, z in plan.axis_cells:
        fill_cell(buf, width, x, z, scale, COLORS["axis"])

    # covered galleries (lines between endpoints)
    for g in plan.gallery_links:
        for x, z in _bresenham(g.from_cell, g.to_cell):
            fill_cell(buf, width, x, z, scale, COLORS["gallery"])

    # slotted volumes by archetype
    for slot in plan.slots:
        color = COLORS.get(slot.archetype, COLORS["terrace2"])
        fill_rect(buf, width, slot.bounds, scale, color)

    # detached-spire feature
    if plan.feature is not None:
        fill_rect(buf, width, plan.feature.detached_bounds, scale, COLORS["detached"])
        for x, z in _bresenham(plan.feature.bridge_link.from_cell,
                               plan.feature.bridge_link.to_cell):
            fill_cell(buf, width, x, z, scale, COLORS["bridge"])

    write_png(path, width, height, bytes(buf))


def _height_color(y: int, lo: int, hi: int) -> RGBA:
    """Shade a derived-mountain height: low = mossy green, high = pale rock."""
    span = max(1, hi - lo)
    t = max(0.0, min(1.0, (y - lo) / span))
    r = int(96 + t * (224 - 96))
    g = int(120 + t * (218 - 120))
    b = int(86 + t * (210 - 86))
    return (r, g, b, 255)


def render_mountain_png(plan, mountain, base_y: int, path: Path, scale: int = 5) -> None:
    """Top-down derived-mountain heightfield (反推山形): each footprint+skirt cell
    shaded by derived ground Y, with the cloud-sea sheet, cliff-back, and spire
    overlaid, so the man-made mountain + blend skirt read at a glance."""
    margin = SKIRT_RADIUS + 4
    minx = mountain.core_x0 - margin
    minz = mountain.core_z0 - margin
    maxx = mountain.core_x1 + margin
    maxz = mountain.core_z1 + margin
    gw = maxx - minx + 1
    gh = maxz - minz + 1
    width = gw * scale
    height = gh * scale
    buf = bytearray(COLORS["background"] * (width * height))

    heights = [[mountain.height(minx + cx, minz + cz) for cz in range(gh)] for cx in range(gw)]
    lo = min(min(col) for col in heights)
    hi = max(max(col) for col in heights)
    for cx in range(gw):
        for cz in range(gh):
            fill_cell(buf, width, cx, cz, scale, _height_color(heights[cx][cz], lo, hi))

    # cloud sea: translucent white where the sheet floats above open air
    gate = plan.terraces[0]
    disciple = plan.terraces[1] if len(plan.terraces) > 1 else gate
    y_cloud = mountain.cloud_sea_y
    for cx in range(gw):
        for cz in range(gh):
            x, z = minx + cx, minz + cz
            if gate.bounds[3] < z < disciple.bounds[1] and mountain.core_x0 <= x <= mountain.core_x1:
                if heights[cx][cz] < y_cloud:
                    fill_cell(buf, width, cx, cz, scale, (236, 240, 248, 255))

    # spire footprint (孤峰) highlighted
    if mountain.spire is not None:
        sp = mountain.spire
        for x in range(sp.x0, sp.x1 + 1):
            for z in range(sp.z0, sp.z1 + 1):
                fill_cell(buf, width, x - minx, z - minz, scale, COLORS["detached"])

    write_png(path, width, height, bytes(buf))


def _bresenham(a: Tuple[int, int], b: Tuple[int, int]):
    x0, z0 = a
    x1, z1 = b
    dx = abs(x1 - x0)
    dz = abs(z1 - z0)
    sx = 1 if x0 < x1 else -1
    sz = 1 if z0 < z1 else -1
    err = dx - dz
    out = []
    while True:
        out.append((x0, z0))
        if x0 == x1 and z0 == z1:
            break
        e2 = 2 * err
        if e2 > -dz:
            err -= dz
            x0 += sx
        if e2 < dx:
            err += dx
            z0 += sz
    return out


def write_viewer(plan, out_dir: Path, mountain=None) -> Path:
    slots = "".join(
        f"<li>{html.escape(s.id)}: {html.escape(s.archetype)} → {html.escape(s.template_id)}, "
        f"tier {s.importance_tier}, role {s.role}</li>"
        for s in plan.slots
    )
    terraces = "".join(
        f"<li>{html.escape(t.name)} (terrace {t.index}): y={t.elevation}, "
        f"{t.width}×{t.depth}{' · cliff-back' if t.cliff_back else ''}</li>"
        for t in plan.terraces
    )
    feature = (
        f"<p>detached-spire feature: <strong>{html.escape(plan.feature.variant)}</strong> "
        f"— {html.escape(plan.feature.detached_archetype)}/{html.escape(plan.feature.detached_template)}, "
        f"bridge {plan.feature.bridge_span} ({plan.feature.bridge_shape}), "
        f"bearing {html.escape(plan.feature.bearing)}</p>"
        if plan.feature else "<p>detached-spire feature: <em>absent this seed</em></p>"
    )
    if mountain is not None:
        spire = ("none" if mountain.spire is None
                 else f"top y={mountain.spire.top}, bounds {list(mountain.spire.__dict__.values())[:4]}")
        mountain_section = (
            "<h2>Derived mountain (反推山形)</h2>"
            "<img src=\"mountain.png\" alt=\"Top-down derived mountain heightfield\">"
            "<div class=\"legend\">"
            "<div><span class=\"swatch\" style=\"background:#60784e\"></span>low derived ground</div>"
            "<div><span class=\"swatch\" style=\"background:#e0dad2\"></span>high derived ground</div>"
            "<div><span class=\"swatch\" style=\"background:#ecf0f8\"></span>cloud sea (云海面)</div>"
            "<div><span class=\"swatch\" style=\"background:#8c3c82\"></span>solitary peak (孤峰)</div>"
            "</div>"
            f"<ul><li>cloud-sea Y: {mountain.cloud_sea_y}</li>"
            f"<li>cliff-back top Y: {mountain.cliff_back_top}</li>"
            f"<li>spire: {html.escape(spire)}</li></ul>")
    else:
        mountain_section = ""
    page = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Sect compound s{plan.seed} (seed={plan.seed})</title>
<style>
body {{ margin: 0; font-family: system-ui, sans-serif; background: #ebe6da; color: #252525; }}
main {{ max-width: 980px; margin: 0 auto; padding: 24px; }}
img {{ image-rendering: pixelated; width: min(100%, 640px); border: 1px solid #555; background: #ebe6da; }}
.legend {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 8px 16px; margin: 18px 0; }}
.swatch {{ display: inline-block; width: 1em; height: 1em; margin-right: 0.4em; vertical-align: -0.15em; border: 1px solid #3336; }}
li {{ margin: 0.25em 0; }}
h2 {{ margin-top: 1.4em; }}
</style>
</head>
<body>
<main>
<h1>Sect compound s{plan.seed} (seed={plan.seed})</h1>
<img src="plan.png" alt="Top-down generated sect compound plan">
<div class="legend">
<div><span class="swatch" style="background:#49708b"></span>ritual axis (山门→主殿)</div>
<div><span class="swatch" style="background:#9d6338"></span>mountain gate</div>
<div><span class="swatch" style="background:#7a2c2c"></span>principal hall (summit)</div>
<div><span class="swatch" style="background:#5a4e8e"></span>scripture pavilion</div>
<div><span class="swatch" style="background:#b4783c"></span>pagoda</div>
<div><span class="swatch" style="background:#96786e"></span>disciple quarters</div>
<div><span class="swatch" style="background:#608456"></span>alchemy room</div>
<div><span class="swatch" style="background:#786e82"></span>bell/drum tower</div>
<div><span class="swatch" style="background:#6e5032"></span>covered gallery (廊)</div>
<div><span class="swatch" style="background:#8c3c82"></span>detached spire</div>
<div><span class="swatch" style="background:#c88228"></span>flying bridge (飞桥)</div>
</div>
<h2>Terraces</h2>
<ul>{terraces}</ul>
<h2>Slots</h2>
<ul>{slots}</ul>
<h2>Feature</h2>
{feature}
{mountain_section}
</main>
</body>
</html>
"""
    viewer = out_dir / "viewer.html"
    viewer.write_text(page, encoding="utf-8")
    return viewer


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED,
                        help=f"base seed (default {DEFAULT_SEED}); the generated dir names "
                             "carry an `s` prefix so the seed never collides with real dates "
                             "in the listing. The default base is a sentinel picked so the "
                             "default --count 6 run hits every detached-spire variant "
                             "(pavilion_short_straight_east / pagoda_long_arched_west / "
                             "disciple_medium_angled_north) plus the absent-feature case.")
    parser.add_argument("--count", type=int, default=6,
                        help="number of plans to render (default 6 — the default base seed "
                             "covers every detached-spire variant + the absent case in 6 "
                             "steps; pass --count 3 to mirror the older compact preview set)")
    parser.add_argument("--out", default=str(REPO_ROOT / "out" / "preview"))
    parser.add_argument("--keep-existing", action="store_true",
                        help="skip the cleanup of previous sect_plan_s* dumps in --out")
    args = parser.parse_args()

    out_root = Path(args.out)
    if not args.keep_existing:
        purged = purge_old_plan_previews(out_root, "sect_plan")
        if purged:
            print(f"purged {purged} previous sect_plan_* dump(s) under {out_root}")
    # pick seeds that exhibit present, absent, and each feature variant
    seeds = []
    for index in range(args.count):
        seeds.append(args.seed + index * 101)
    for seed in seeds:
        plan = generate_sect_plan(seed)
        out_dir = out_root / f"sect_plan_s{seed}"
        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / "plan.json").write_text(
            json.dumps(plan.to_dict(), indent=2, ensure_ascii=False), encoding="utf-8")
        render_plan_png(plan, out_dir / "plan.png")
        feature = ({"detached_bounds": list(plan.feature.detached_bounds)}
                   if plan.feature is not None else None)
        mountain = derive_mountain(seed, plan.terrace_profile,
                                   flat_natural(plan.site.base_y), feature=feature)
        (out_dir / "mountain.json").write_text(json.dumps({
            "cloud_sea_y": mountain.cloud_sea_y,
            "cliff_back_top": mountain.cliff_back_top,
            "spire": (None if mountain.spire is None
                      else {"bounds": [mountain.spire.x0, mountain.spire.z0,
                                       mountain.spire.x1, mountain.spire.z1],
                            "top": mountain.spire.top}),
        }, indent=2, ensure_ascii=False), encoding="utf-8")
        render_mountain_png(plan, mountain, plan.site.base_y, out_dir / "mountain.png")
        viewer = write_viewer(plan, out_dir, mountain)
        print(f"OK sect_plan_s{seed}: {viewer.relative_to(REPO_ROOT)} "
              f"feature={plan.feature.variant if plan.feature else 'none'} "
              f"cloud_y={mountain.cloud_sea_y}")
    index_path = write_index(out_root)
    print(f"index: {index_path.relative_to(REPO_ROOT)}")
    print("hint: run preview_structure.py --all to merge index categories")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
