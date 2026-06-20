#!/usr/bin/env python3
"""Generate per-seed region-topology previews under ``out/preview``.

For each seed this writes ``out/preview/region_topology_s<seed>/`` containing:

- ``graph.json`` — the generated region graph (data contract),
- ``layout.svg``  — a human-reviewable layout (regions by tier, 连 / 隔 edges
  with separator type, walled regions and their 关隘),
- ``layout.txt``  — an ASCII rendering of the same (text/ASCII minimum),
- ``viewer.html`` — a dark-theme page embedding the SVG, the ASCII map, and the
  graph data.

It then refreshes the shared aggregate ``out/preview/index.html`` so the region
previews are reachable from the review entry point, consistent with the
``AGENTS.md`` acceptance / preview convention.
"""

from __future__ import annotations

import argparse
import html
import json
import math
import shutil
import sys
from pathlib import Path
from typing import Iterable, List, Sequence, Tuple

from buildgen.region_topology import (
    DEFAULT_CATALOG_DIR,
    DEFAULT_RULESET_PATH,
    EDGE_GE,
    EDGE_LIAN,
    PASS_GUANAI,
    GenEdge,
    RegionGraph,
    GenRegion,
    SEP_MOUNTAIN,
    SEP_OCEAN,
    generate,
    load_catalog_dir,
    load_ruleset,
)

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
DEFAULT_OUT = REPO_ROOT / "out" / "preview"

# Seed base / step mirror the town-plan preview convention (`s` prefix keeps
# generated dir names from colliding with real dates in the listing).
DEFAULT_SEED = 20260620
DEFAULT_SEED_STEP = 101

# Canvas geometry for the SVG.
_SVG_SIZE = 760
_SVG_PAD = 60

# Role -> (symbol, node fill, short label) for both renderings.
_ROLE_STYLE = {
    "anchor": ("@", "#e8c46a", "anchor"),
    "peripheral": ("o", "#7aa2c7", "peripheral"),
    "walled": ("X", "#c77a7a", "walled"),
}

_SEP_COLOR = {SEP_MOUNTAIN: "#9c6b3f", SEP_OCEAN: "#3f7c9c"}


# --------------------------------------------------------------------------- #
# ASCII rendering.
# --------------------------------------------------------------------------- #


def _grid_dims(n_regions: int) -> Tuple[int, int]:
    cols = 41
    rows = 21
    return cols, rows


def render_ascii(graph: RegionGraph) -> str:
    """A text/ASCII layout: a coarse grid of role symbols plus structured
    region and typed-edge tables. Self-contained and dependency-free."""
    cols, rows = _grid_dims(len(graph.regions))
    cx, cy = (cols - 1) / 2.0, (rows - 1) / 2.0
    # Positions are in roughly [-1.4, 1.4]; scale to half-extent.
    half = 1.5

    grid = [[" " for _ in range(cols)] for _ in range(rows)]

    def place(r: GenRegion, sym: str) -> None:
        gx = int(round(cx + (r.position[0] / half) * (cols / 2.0 - 1)))
        gy = int(round(cy + (r.position[1] / half) * (rows / 2.0 - 1)))
        gx = max(0, min(cols - 1, gx))
        gy = max(0, min(rows - 1, gy))
        grid[gy][gx] = sym

    # anchor last so it is never overwritten
    for r in sorted(graph.regions, key=lambda x: (x.role != "anchor", x.id)):
        place(r, _ROLE_STYLE[r.role][0])

    border = "+" + "-" * cols + "+"
    lines = [border]
    for row in grid:
        lines.append("|" + "".join(row) + "|")
    lines.append(border)

    # Legend / region table.
    tlo, thi = graph.tier_range
    region_lines = [
        f"seed={graph.seed}  count={graph.count}  "
        f"tier_range=[{tlo},{thi}]  tier_step=N={graph.tier_step}",
        "",
        "regions (symbol: @ anchor / o peripheral / X walled):",
    ]
    for r in sorted(graph.regions, key=lambda x: (-x.tier, x.id)):
        gate = ""
        if r.role == "walled":
            inc = [e for e in graph.edges if r.id in (e.a, e.b)]
            n_lian = sum(1 for e in inc if e.type == EDGE_LIAN)
            gate = f"  [walled: {n_lian} 关隘]" if n_lian else "  [walled: sealed]"
        region_lines.append(
            f"  {_ROLE_STYLE[r.role][0]} {r.display_name} ({r.id})  "
            f"tier={r.tier}  role={r.role}  qi={list(r.qi)}  "
            f"danger={list(r.danger)}{gate}"
        )

    # Typed edge table.
    edge_lines = ["", "edges:"]
    by_id = {r.id: r for r in graph.regions}
    for e in sorted(graph.edges, key=lambda x: (x.type != EDGE_LIAN, x.a, x.b)):
        if e.type == EDGE_LIAN:
            tag = "连 (关隘)" if e.is_pass else "连"
        else:
            tag = f"隔 [{e.separator}]"
        dt = abs(by_id[e.a].tier - by_id[e.b].tier)
        edge_lines.append(f"  {e.a:>10} -- {e.b:<10}  {tag:<16}  |Δtier|={dt}")

    return "\n".join(lines) + "\n\n" + "\n".join(region_lines) + "\n" + "\n".join(edge_lines) + "\n"


# --------------------------------------------------------------------------- #
# SVG rendering.
# --------------------------------------------------------------------------- #


def _project(pos: Tuple[float, float], span: float) -> Tuple[float, float]:
    half = span / 2.0
    ux = (pos[0] + half) / span  # [0,1]
    uy = (pos[1] + half) / span
    px = _SVG_PAD + ux * (_SVG_SIZE - 2 * _SVG_PAD)
    py = _SVG_PAD + uy * (_SVG_SIZE - 2 * _SVG_PAD)
    return px, py


def render_svg(graph: RegionGraph) -> str:
    span = 3.0  # covers [-1.5, 1.5]

    def P(pos: Tuple[float, float]) -> Tuple[float, float]:
        return _project(pos, span)

    tlo, thi = graph.tier_range
    tier_span = max(1, thi - tlo)

    def node_radius(r: GenRegion) -> float:
        base = 16.0
        return base + 10.0 * (r.tier - tlo) / tier_span

    parts: List[str] = []
    parts.append(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{_SVG_SIZE}" '
        f'height="{_SVG_SIZE}" viewBox="0 0 {_SVG_SIZE} {_SVG_SIZE}" '
        f'font-family="Inter, ui-sans-serif, system-ui, sans-serif">'
    )
    parts.append('<rect width="100%" height="100%" fill="#121417"/>')

    # Edges first (under nodes).
    for e in graph.edges:
        ra = next(r for r in graph.regions if r.id == e.a)
        rb = next(r for r in graph.regions if r.id == e.b)
        x1, y1 = P(ra.position)
        x2, y2 = P(rb.position)
        if e.type == EDGE_LIAN:
            if e.is_pass:
                # 关隘: solid gold, slightly thicker, with a gate glyph + label.
                parts.append(
                    f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" '
                    f'stroke="#e8c46a" stroke-width="3.5"/>'
                )
                mx, my = (x1 + x2) / 2, (y1 + y2) / 2
                parts.append(
                    f'<circle cx="{mx:.1f}" cy="{my:.1f}" r="6" fill="#e8c46a" '
                    f'stroke="#121417" stroke-width="1.5"/>'
                )
                parts.append(
                    f'<text x="{mx:.1f}" y="{my - 10:.1f}" fill="#e8c46a" '
                    f'font-size="11" text-anchor="middle">关隘</text>'
                )
            else:
                parts.append(
                    f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" '
                    f'stroke="#8fb39a" stroke-width="2.2"/>'
                )
        else:
            color = _SEP_COLOR.get(e.separator, "#888")
            parts.append(
                f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" '
                f'stroke="{color}" stroke-width="2" stroke-dasharray="7 5"/>'
            )

    # Nodes.
    for r in graph.regions:
        px, py = P(r.position)
        rad = node_radius(r)
        _, fill, _ = _ROLE_STYLE[r.role]
        stroke = "#e8c46a" if r.role == "anchor" else "#2a2f35"
        parts.append(
            f'<circle cx="{px:.1f}" cy="{py:.1f}" r="{rad:.1f}" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="2.5"/>'
        )
        label = f"{r.display_name} ·T{r.tier}"
        parts.append(
            f'<text x="{px:.1f}" y="{py + 4:.1f}" fill="#121417" font-size="13" '
            f'font-weight="650" text-anchor="middle">{html.escape(label)}</text>'
        )
        role_tag = "魔域" if r.role == "walled" else ("中州" if r.role == "anchor" else "")
        if role_tag:
            parts.append(
                f'<text x="{px:.1f}" y="{py + rad + 14:.1f}" fill="{fill}" '
                f'font-size="11" text-anchor="middle">{role_tag}</text>'
            )

    # Legend.
    legend_items = [
        ("#e8c46a", "anchor 中州 (top tier, centered)"),
        ("#7aa2c7", "peripheral"),
        ("#c77a7a", "walled 魔域"),
        ("#8fb39a", "—— 连 (passable)"),
        ("#e8c46a", "—— 连 + 关隘 (walled pass)"),
        (_SEP_COLOR[SEP_MOUNTAIN], "- - 隔 特殊山脉"),
        (_SEP_COLOR[SEP_OCEAN], "- - 隔 特殊海洋"),
    ]
    lx, ly = 16, _SVG_SIZE - 28 * len(legend_items) - 8
    parts.append(
        f'<rect x="{lx}" y="{ly - 8}" width="270" height="{28 * len(legend_items) + 8}" '
        f'fill="#181c20" stroke="#303840" rx="6"/>'
    )
    for i, (color, text) in enumerate(legend_items):
        y = ly + i * 28
        parts.append(
            f'<rect x="{lx + 12}" y="{y - 9}" width="16" height="10" '
            f'fill="{color}" rx="2"/>'
        )
        parts.append(
            f'<text x="{lx + 38}" y="{y}" fill="#cdd4da" font-size="12">'
            f'{html.escape(text)}</text>'
        )

    parts.append(
        f'<text x="{_SVG_SIZE - 12}" y="20" fill="#aab4bd" font-size="12" '
        f'text-anchor="end">seed={graph.seed}  regions={graph.count}  '
        f"N={graph.tier_step}</text>"
    )
    parts.append("</svg>")
    return "\n".join(parts)


# --------------------------------------------------------------------------- #
# Viewer HTML.
# --------------------------------------------------------------------------- #


def render_viewer(graph: RegionGraph, svg: str, ascii_text: str) -> str:
    graph_json = json.dumps(graph.to_dict(), ensure_ascii=False, indent=2)
    n_lian = sum(1 for e in graph.edges if e.type == EDGE_LIAN)
    n_ge = sum(1 for e in graph.edges if e.type == EDGE_GE)
    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Region topology · seed {graph.seed}</title>
  <style>
    :root {{ color-scheme: dark; font-family: Inter, ui-sans-serif, system-ui, sans-serif;
            background: #121417; color: #eef2f3; }}
    body {{ margin: 0; padding: 24px; }}
    h1 {{ font-size: 20px; margin: 0 0 4px; }}
    .meta {{ color: #aab4bd; margin-bottom: 18px; font-size: 13px; }}
    .grid {{ display: grid; grid-template-columns: 1fr; gap: 18px; max-width: 820px; }}
    svg {{ width: 100%; height: auto; background: #121417; border: 1px solid #303840;
           border-radius: 8px; }}
    pre {{ background: #181c20; border: 1px solid #303840; border-radius: 8px;
           padding: 14px; overflow: auto; font-size: 12px; line-height: 1.5;
           font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }}
    .ascii {{ white-space: pre; }}
    details summary {{ cursor: pointer; color: #9fc0dd; padding: 6px 0; }}
  </style>
</head>
<body>
  <h1>Region topology · seed {graph.seed}</h1>
  <div class="meta">Offline-only layer (no runtime worldgen). regions={graph.count} ·
    连={n_lian} · 隔={n_ge} · tier_step N={graph.tier_step}</div>
  <div class="grid">
    <div>{svg}</div>
    <details open><summary>ASCII map</summary>
      <pre class="ascii">{html.escape(ascii_text)}</pre>
    </details>
    <details><summary>graph.json</summary>
      <pre>{html.escape(graph_json)}</pre>
    </details>
  </div>
</body>
</html>
"""


# --------------------------------------------------------------------------- #
# Index refresh (shared aggregate).
# --------------------------------------------------------------------------- #


def purge_old_previews(out_root: Path, prefix: str) -> int:
    if not out_root.is_dir():
        return 0
    removed = 0
    for child in out_root.iterdir():
        if child.is_dir() and child.name.startswith(prefix + "_"):
            shutil.rmtree(child)
            removed += 1
    return removed


def write_index(out_root: Path) -> Path:
    viewers = sorted(out_root.glob("*/viewer.html"))
    index = out_root / "index.html"
    index.parent.mkdir(parents=True, exist_ok=True)
    try:
        from preview_structure import RenderResult, render_preview_index

        structure_dir = (
            REPO_ROOT / "src" / "main" / "resources" / "data" / "myvillage" / "structure"
        )
        results = []
        for v in viewers:
            stem = v.parent.name
            nbt = structure_dir / f"{stem}.nbt"
            source = str(nbt if nbt.exists() else v)
            results.append(
                RenderResult(0, source, stem, str(v.parent), str(v))
            )
        written = render_preview_index(str(out_root), results)
        if written:
            return Path(written)
    except Exception:
        pass
    links = "\n".join(
        f'<li><a href="{html.escape(str(v.relative_to(out_root)))}">'
        f"{html.escape(v.parent.name)}</a></li>"
        for v in viewers
    )
    index.write_text(
        '<!doctype html><html><head><meta charset="utf-8">'
        "<title>Preview index</title></head><body><h1>Preview index</h1><ul>"
        f"{links}</ul></body></html>",
        encoding="utf-8",
    )
    return index


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--seed",
        type=int,
        default=DEFAULT_SEED,
        help=f"base seed (default {DEFAULT_SEED}); dir names carry an `s` prefix",
    )
    parser.add_argument(
        "--count",
        type=int,
        default=6,
        help="number of seeds to render (default 6)",
    )
    parser.add_argument(
        "--step",
        type=int,
        default=DEFAULT_SEED_STEP,
        help=f"seed step between renders (default {DEFAULT_SEED_STEP})",
    )
    parser.add_argument("--ruleset", type=Path, default=DEFAULT_RULESET_PATH)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG_DIR)
    parser.add_argument("--out", default=str(DEFAULT_OUT))
    parser.add_argument(
        "--keep-existing",
        action="store_true",
        help="skip cleanup of previous region_topology_s* dumps",
    )
    args = parser.parse_args()

    ruleset = load_ruleset(args.ruleset)
    catalog = load_catalog_dir(args.catalog)
    out_root = Path(args.out)
    if not args.keep_existing:
        purged = purge_old_previews(out_root, "region_topology")
        if purged:
            print(f"purged {purged} previous region_topology_* dump(s) under {out_root}")

    for i in range(args.count):
        seed = args.seed + i * args.step
        graph = generate(seed, ruleset, catalog)
        out_dir = out_root / f"region_topology_s{seed}"
        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / "graph.json").write_text(
            json.dumps(graph.to_dict(), ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        svg = render_svg(graph)
        ascii_text = render_ascii(graph)
        (out_dir / "layout.svg").write_text(svg, encoding="utf-8")
        (out_dir / "layout.txt").write_text(ascii_text, encoding="utf-8")
        viewer = render_viewer(graph, svg, ascii_text)
        (out_dir / "viewer.html").write_text(viewer, encoding="utf-8")
        print(f"OK region_topology_s{seed}: {out_dir.relative_to(REPO_ROOT)}/viewer.html")

    index = write_index(out_root)
    print(f"index: {index.relative_to(REPO_ROOT)}")
    print("hint: run preview_structure.py --all to merge index categories")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
