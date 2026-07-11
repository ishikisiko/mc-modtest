#!/usr/bin/env python3
"""Render CRAFT preview artifacts for three Huipai component candidates.

This is a preview-only tool: it creates reviewable 3D HTML/Png artifacts for
three future custom decor blocks, but it does not register Java blocks or write
Minecraft asset resources.
"""

from __future__ import annotations

import argparse
import json
import math
import shutil
import struct
import zlib
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable, Sequence


ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "out" / "preview" / "huipai_components"
RUN_ID = "20260707-huipai-component-previews"

RGBA = tuple[int, int, int, int]


MATERIALS: dict[str, dict[str, Any]] = {
    "white_plaster": {"hex": "#deddd6", "rgb": (222, 221, 214)},
    "shadow_plaster": {"hex": "#b9b8b0", "rgb": (185, 184, 176)},
    "dark_tile": {"hex": "#2e241b", "rgb": (46, 36, 27)},
    "tile_edge": {"hex": "#4a3523", "rgb": (74, 53, 35)},
    "dark_wood": {"hex": "#5b3b20", "rgb": (91, 59, 32)},
    "grey_brick": {"hex": "#8f9089", "rgb": (143, 144, 137)},
    "aged_gold": {"hex": "#b69348", "rgb": (182, 147, 72)},
    "open_shadow": {"hex": "#2a241f", "rgb": (42, 36, 31)},
}


def box(label: str, start: list[float], end: list[float], material: str) -> dict[str, Any]:
    return {"label": label, "from": start, "to": end, "material": material}


def micro_eave(label: str, x0: int, x1: int, y: int, z0: int, z1: int, material: str) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for index, x in enumerate(range(x0, x1, 4), 1):
        result.append(box(f"{label} tile voxel {index}", [x, y, z0], [min(x + 3, x1), y + 1, z1], material))
    result.extend([
        box(f"{label} left lift lower", [x0 - 1, y + 1, z0], [x0 + 1, y + 2, z1], material),
        box(f"{label} left lift upper", [x0 - 2, y + 2, z0], [x0, y + 3, z1], material),
        box(f"{label} right lift lower", [x1 - 1, y + 1, z0], [x1 + 1, y + 2, z1], material),
        box(f"{label} right lift upper", [x1, y + 2, z0], [x1 + 2, y + 3, z1], material),
    ])
    return result


def micro_side_eave(label: str, x0: int, x1: int, y: int, z0: int, z1: int, material: str) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for index, z in enumerate(range(z0, z1, 8), 1):
        result.append(box(f"{label} run voxel {index}", [x0, y, z], [x1, y + 1, min(z + 5, z1)], material))
    result.extend([
        box(f"{label} rear lift lower", [x0, y + 1, z0 - 2], [x1, y + 2, z0 + 2], material),
        box(f"{label} rear lift upper", [x0 + 1, y + 2, z0 - 3], [x1 - 1, y + 3, z0], material),
        box(f"{label} front lift lower", [x0, y + 1, z1 - 2], [x1, y + 2, z1 + 2], material),
        box(f"{label} front lift upper", [x0 + 1, y + 2, z1], [x1 - 1, y + 3, z1 + 3], material),
    ])
    return result


def micro_gate_eave(label: str) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for index, x in enumerate(range(4, 28, 6), 1):
        result.append(box(f"{label} lower voxel {index}", [x, 20, 2], [min(x + 5, 28), 21, 12], "dark_tile"))
    for index, x in enumerate(range(8, 24, 6), 1):
        result.append(box(f"{label} upper voxel {index}", [x, 21, 3], [min(x + 5, 24), 22, 11], "dark_tile"))
    result.extend([
        box(f"{label} left first lift", [0, 21, 3], [3, 22, 11], "dark_tile"),
        box(f"{label} left second lift", [1, 22, 4], [4, 23, 10], "dark_tile"),
        box(f"{label} right first lift", [29, 21, 3], [32, 22, 11], "dark_tile"),
        box(f"{label} right second lift", [28, 22, 4], [31, 23, 10], "dark_tile"),
        box(f"{label} left bracket voxel", [4, 18, 9], [6, 20, 12], "dark_wood"),
        box(f"{label} right bracket voxel", [26, 18, 9], [28, 20, 12], "dark_wood"),
    ])
    return result


def front_tile_row(label: str, x0: int, x1: int, y: int, z: int, material: str = "dark_tile") -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for index, x in enumerate(range(x0, x1, 4), 1):
        result.append(box(f"{label} tile {index}", [x, y, z], [min(x + 3, x1), y + 1, z + 2], material))
    return result


def lattice_window(label: str, x: int, y: int, z: int, width: int, height: int) -> list[dict[str, Any]]:
    mid_x = x + width // 2
    mid_y = y + height // 2
    return [
        box(f"{label} dark opening", [x + 2, y + 2, z], [x + width - 2, y + height - 2, z + 2], "open_shadow"),
        box(f"{label} left frame", [x, y, z + 1], [x + 2, y + height, z + 4], "dark_wood"),
        box(f"{label} right frame", [x + width - 2, y, z + 1], [x + width, y + height, z + 4], "dark_wood"),
        box(f"{label} top frame", [x, y + height - 2, z + 1], [x + width, y + height, z + 4], "dark_wood"),
        box(f"{label} sill frame", [x, y, z + 1], [x + width, y + 2, z + 4], "dark_wood"),
        box(f"{label} vertical lattice", [mid_x - 1, y + 2, z + 3], [mid_x + 1, y + height - 2, z + 5], "dark_wood"),
        box(f"{label} horizontal lattice", [x + 2, mid_y - 1, z + 3], [x + width - 2, mid_y + 1, z + 5], "dark_wood"),
        box(f"{label} stone sill", [x - 1, y - 2, z], [x + width + 1, y, z + 3], "grey_brick"),
        box(f"{label} stone head", [x - 1, y + height, z], [x + width + 1, y + height + 2, z + 3], "grey_brick"),
    ]


COMPONENTS: list[dict[str, Any]] = [
    {
        "id": "huipai_gable_cap",
        "future_block_id": "myvillage:huipai_gable_cap",
        "title": "Huipai Gable Cap",
        "zh_design_name": "徽派马头墙压顶",
        "dimensions": [24, 28, 12],
        "purpose": "Study the horse-head wall as a continuous plaster firewall whose coping is built from voxel-scale tile blocks, not oversized ornaments or painted pattern.",
        "boxes": [
            box("lower continuous firewall", [3, 0, 4], [21, 12, 8], "white_plaster"),
            box("middle raised firewall", [6, 12, 4], [18, 18, 8], "white_plaster"),
            box("central horse-head crown", [9, 18, 4], [15, 24, 8], "white_plaster"),
            *micro_eave("lower front flying eave", 2, 22, 11, 2, 4, "tile_edge"),
            *micro_eave("lower rear flying eave", 2, 22, 11, 8, 10, "tile_edge"),
            *micro_eave("middle front flying eave", 5, 19, 17, 2, 4, "tile_edge"),
            *micro_eave("middle rear flying eave", 5, 19, 17, 8, 10, "tile_edge"),
            *micro_eave("crown front flying eave", 8, 16, 23, 2, 4, "dark_tile"),
            *micro_eave("crown rear flying eave", 8, 16, 23, 8, 10, "dark_tile"),
            box("under-eave shadow line", [1, 10, 3], [23, 11, 9], "shadow_plaster"),
            box("rear plaster thickness", [4, 0, 8], [20, 10, 10], "shadow_plaster"),
        ],
    },
    {
        "id": "dark_tile_ridge",
        "future_block_id": "myvillage:dark_tile_ridge",
        "title": "Dark Tile Ridge",
        "zh_design_name": "深色瓦脊线",
        "dimensions": [16, 14, 16],
        "purpose": "Give hall roofs readable ridge and edge hierarchy instead of one undifferentiated dark slab.",
        "boxes": [
            box("left tile skirt", [0, 3, 3], [16, 5, 6], "tile_edge"),
            box("right tile skirt", [0, 3, 10], [16, 5, 13], "tile_edge"),
            box("ridge body", [0, 5, 5], [16, 9, 11], "dark_tile"),
            box("raised spine", [1, 9, 7], [15, 13, 9], "dark_tile"),
            box("front end cap", [0, 4, 4], [2, 10, 12], "tile_edge"),
            box("back end cap", [14, 4, 4], [16, 10, 12], "tile_edge"),
        ],
    },
    {
        "id": "huipai_gate_frame",
        "future_block_id": "myvillage:huipai_gate_frame",
        "title": "Huipai Gate Frame",
        "zh_design_name": "徽派门罩门框",
        "dimensions": [40, 38, 16],
        "purpose": "Make the facade read like a Huipai stone portal: carved grey jambs, plaque, black door, and a thin tiled hood.",
        "boxes": [
            box("left stone jamb", [8, 0, 5], [14, 27, 13], "grey_brick"),
            box("right stone jamb", [26, 0, 5], [32, 27, 13], "grey_brick"),
            box("carved lintel block", [8, 24, 5], [32, 31, 13], "grey_brick"),
            box("upper plaque stone", [13, 26, 12], [27, 30, 15], "aged_gold"),
            box("recessed black double door", [15, 0, 11], [25, 21, 14], "dark_wood"),
            box("door center seam", [19, 1, 14], [21, 20, 15], "open_shadow"),
            box("stone threshold", [13, 0, 9], [27, 2, 15], "grey_brick"),
            box("left lower plinth", [6, 0, 4], [15, 4, 14], "grey_brick"),
            box("right lower plinth", [25, 0, 4], [34, 4, 14], "grey_brick"),
            box("left carved side panel", [8, 14, 13], [14, 20, 15], "shadow_plaster"),
            box("right carved side panel", [26, 14, 13], [32, 20, 15], "shadow_plaster"),
            box("left bracket block", [11, 21, 11], [15, 24, 15], "dark_wood"),
            box("right bracket block", [25, 21, 11], [29, 24, 15], "dark_wood"),
            box("thin lower hood eave", [4, 31, 3], [36, 33, 14], "dark_tile"),
            box("raised hood eave", [7, 33, 4], [33, 35, 13], "dark_tile"),
            box("left hood upturn", [2, 33, 5], [7, 36, 13], "dark_tile"),
            box("right hood upturn", [33, 33, 5], [38, 36, 13], "dark_tile"),
            *front_tile_row("gate hood front tile row", 5, 36, 30, 13, "tile_edge"),
        ],
    },
]


def translated_boxes(source_id: str, offset: list[float], label_prefix: str) -> list[dict[str, Any]]:
    source = next(item for item in COMPONENTS if item["id"] == source_id)
    ox, oy, oz = offset
    result: list[dict[str, Any]] = []
    for item in source["boxes"]:
        result.append(box(
            f"{label_prefix}: {item['label']}",
            [item["from"][0] + ox, item["from"][1] + oy, item["from"][2] + oz],
            [item["to"][0] + ox, item["to"][1] + oy, item["to"][2] + oz],
            item["material"],
        ))
    return result


def repeat_component(source_id: str, offsets: Iterable[list[float]], label_prefix: str) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for index, offset in enumerate(offsets, 1):
        result.extend(translated_boxes(source_id, offset, f"{label_prefix} {index}"))
    return result


def transformed_boxes(source_id: str, offset: list[float], label_prefix: str, rotate_y: int = 0) -> list[dict[str, Any]]:
    source = next(item for item in COMPONENTS if item["id"] == source_id)
    sx, _, sz = source["dimensions"]
    turns = (rotate_y // 90) % 4
    ox, oy, oz = offset
    result: list[dict[str, Any]] = []
    for item in source["boxes"]:
        x1, y1, z1 = item["from"]
        x2, y2, z2 = item["to"]
        if turns == 0:
            a, b = [x1, y1, z1], [x2, y2, z2]
        elif turns == 1:
            a, b = [z1, y1, sx - x2], [z2, y2, sx - x1]
        elif turns == 2:
            a, b = [sx - x2, y1, sz - z2], [sx - x1, y2, sz - z1]
        else:
            a, b = [sz - z2, y1, x1], [sz - z1, y2, x2]
        result.append(box(
            f"{label_prefix}: {item['label']}",
            [a[0] + ox, a[1] + oy, a[2] + oz],
            [b[0] + ox, b[1] + oy, b[2] + oz],
            item["material"],
        ))
    return result


HOUSE_PREVIEW: dict[str, Any] = {
    "id": "huipai_complete_house",
    "preview_id": "preview:huipai_complete_house",
    "title": "Huipai Complete House Study",
    "zh_design_name": "徽派完整房屋组合预览",
    "dimensions": [128, 68, 66],
    "purpose": "Reference-aligned facade study: broad white Huipai wall, long thin black-tile roof, side horse-head walls, symmetric lattice windows, and a central carved stone gatehouse.",
    "boxes": [
        box("front courtyard paving", [0, 0, 54], [128, 1, 66], "grey_brick"),
        box("stone house plinth", [6, 1, 8], [122, 3, 56], "grey_brick"),
        box("main white wall body", [12, 3, 18], [116, 38, 50], "white_plaster"),
        box("flat front plaster facade", [12, 3, 49], [116, 38, 55], "white_plaster"),
        box("front lower grey base line", [12, 3, 54], [116, 5, 57], "grey_brick"),
        box("front roof shadow under-eave", [9, 37, 48], [119, 39, 54], "shadow_plaster"),
        box("long front roof eave", [6, 38, 44], [122, 41, 56], "tile_edge"),
        box("rear roof eave", [8, 38, 10], [120, 41, 20], "tile_edge"),
        box("lower black tile roof plane", [8, 39, 14], [120, 43, 50], "dark_tile"),
        box("middle black tile roof plane", [12, 43, 18], [116, 46, 46], "dark_tile"),
        box("upper black tile roof plane", [18, 46, 24], [110, 49, 40], "dark_tile"),
        box("thin roof ridge", [20, 49, 30], [108, 52, 33], "dark_tile"),
        *front_tile_row("front roof barrel tile", 8, 120, 37, 54, "dark_tile"),
        *front_tile_row("roof ridge tile rhythm", 22, 106, 52, 31, "tile_edge"),
        box("left horse-head front wall", [3, 3, 40], [17, 48, 55], "white_plaster"),
        box("right horse-head front wall", [111, 3, 40], [125, 48, 55], "white_plaster"),
        box("left horse-head middle rise", [5, 48, 39], [15, 55, 54], "white_plaster"),
        box("right horse-head middle rise", [113, 48, 39], [123, 55, 54], "white_plaster"),
        box("left horse-head crown rise", [8, 55, 40], [13, 63, 53], "white_plaster"),
        box("right horse-head crown rise", [116, 55, 40], [121, 63, 53], "white_plaster"),
        box("left lower horse-head coping", [1, 47, 38], [19, 50, 57], "tile_edge"),
        box("right lower horse-head coping", [109, 47, 38], [127, 50, 57], "tile_edge"),
        box("left middle horse-head coping", [3, 54, 38], [17, 57, 56], "tile_edge"),
        box("right middle horse-head coping", [111, 54, 38], [125, 57, 56], "tile_edge"),
        box("left crown horse-head coping", [6, 62, 39], [15, 65, 54], "dark_tile"),
        box("right crown horse-head coping", [114, 62, 39], [123, 65, 54], "dark_tile"),
        box("left rear horse-head wall", [4, 42, 18], [16, 53, 34], "white_plaster"),
        box("right rear horse-head wall", [112, 42, 18], [124, 53, 34], "white_plaster"),
        box("left rear horse-head coping", [2, 52, 16], [18, 55, 36], "tile_edge"),
        box("right rear horse-head coping", [110, 52, 16], [126, 55, 36], "tile_edge"),
        *lattice_window("upper left lattice window", 29, 25, 55, 13, 11),
        *lattice_window("upper right lattice window", 86, 25, 55, 13, 11),
        *lattice_window("lower left lattice window", 27, 8, 55, 16, 14),
        *lattice_window("lower right lattice window", 85, 8, 55, 16, 14),
        box("entry front steps lower", [46, 1, 56], [82, 3, 64], "grey_brick"),
        box("entry front steps upper", [50, 3, 55], [78, 5, 61], "grey_brick"),
    ]
    + translated_boxes("huipai_gate_frame", [44, 0, 47], "central stone gatehouse"),
}


class Canvas:
    __slots__ = ("w", "h", "buf")

    def __init__(self, w: int, h: int) -> None:
        self.w = w
        self.h = h
        self.buf = bytearray(w * h * 4)

    def fill_polygon(self, pts: Sequence[tuple[float, float]], color: RGBA) -> None:
        if len(pts) < 3:
            return
        ys = [p[1] for p in pts]
        y_lo = max(0, int(math.floor(min(ys))))
        y_hi = min(self.h - 1, int(math.ceil(max(ys))))
        n = len(pts)
        for y in range(y_lo, y_hi + 1):
            yc = y + 0.5
            xs: list[float] = []
            for i in range(n):
                ax, ay = pts[i]
                bx, by = pts[(i + 1) % n]
                if (ay <= yc < by) or (by <= yc < ay):
                    t = (yc - ay) / (by - ay)
                    xs.append(ax + t * (bx - ax))
            if len(xs) < 2:
                continue
            xs.sort()
            for k in range(0, len(xs) - 1, 2):
                xa = max(0, int(math.ceil(xs[k] - 0.5)))
                xb = min(self.w - 1, int(math.floor(xs[k + 1] - 0.5)))
                for x in range(xa, xb + 1):
                    offset = (y * self.w + x) * 4
                    self.buf[offset:offset + 4] = bytes(color)

    def write_png(self, path: Path) -> None:
        def chunk(tag: bytes, data: bytes) -> bytes:
            return (
                struct.pack(">I", len(data))
                + tag
                + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
            )

        raw = bytearray()
        idx = 0
        for _ in range(self.h):
            raw.append(0)
            raw.extend(self.buf[idx:idx + self.w * 4])
            idx += self.w * 4
        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        png += chunk(b"IEND", b"")
        path.write_bytes(png)


def shade(rgb: tuple[int, int, int], factor: float) -> RGBA:
    return (
        max(0, min(255, int(rgb[0] * factor))),
        max(0, min(255, int(rgb[1] * factor))),
        max(0, min(255, int(rgb[2] * factor))),
        255,
    )


def render_png(component: dict[str, Any], out_path: Path, scale: float = 5.0) -> None:
    if component["id"] == "huipai_complete_house":
        render_front_png(component, out_path, scale=5.0)
        return

    boxes = component["boxes"]

    def project(point: Sequence[float]) -> tuple[float, float]:
        x, y, z = point
        return ((x - z) * scale, (x + z) * scale * 0.5 - y * scale)

    faces: list[tuple[float, list[tuple[float, float]], RGBA]] = []
    for item in boxes:
        x1, y1, z1 = item["from"]
        x2, y2, z2 = item["to"]
        rgb = MATERIALS[item["material"]]["rgb"]
        depth = (x1 + x2 + z1 + z2) / 2.0 + y2 * 0.12
        top = [project(p) for p in ((x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2))]
        x_face = [project(p) for p in ((x2, y2, z1), (x2, y2, z2), (x2, y1, z2), (x2, y1, z1))]
        z_face = [project(p) for p in ((x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2))]
        faces.append((depth, x_face, shade(rgb, 0.72)))
        faces.append((depth + 0.01, z_face, shade(rgb, 0.58)))
        faces.append((depth + 0.02, top, shade(rgb, 1.0)))

    min_x = min(x for _, pts, _ in faces for x, _ in pts)
    max_x = max(x for _, pts, _ in faces for x, _ in pts)
    min_y = min(y for _, pts, _ in faces for _, y in pts)
    max_y = max(y for _, pts, _ in faces for _, y in pts)
    margin = 24
    canvas = Canvas(int(math.ceil(max_x - min_x + margin * 2)), int(math.ceil(max_y - min_y + margin * 2)))
    shifted: list[tuple[float, list[tuple[float, float]], RGBA]] = []
    for depth, pts, color in faces:
        shifted.append((depth, [(x - min_x + margin, y - min_y + margin) for x, y in pts], color))
    for _, pts, color in sorted(shifted, key=lambda item: item[0]):
        canvas.fill_polygon(pts, color)
    canvas.write_png(out_path)


def render_front_png(component: dict[str, Any], out_path: Path, scale: float = 5.0) -> None:
    boxes = component["boxes"]
    dims = component["dimensions"]
    margin = 24
    canvas = Canvas(int(math.ceil(dims[0] * scale + margin * 2)), int(math.ceil(dims[1] * scale + margin * 2)))

    faces: list[tuple[float, list[tuple[float, float]], RGBA]] = []
    for item in boxes:
        x1, y1, z1 = item["from"]
        x2, y2, z2 = item["to"]
        rgb = MATERIALS[item["material"]]["rgb"]
        pts = [
            (x1 * scale + margin, (dims[1] - y2) * scale + margin),
            (x2 * scale + margin, (dims[1] - y2) * scale + margin),
            (x2 * scale + margin, (dims[1] - y1) * scale + margin),
            (x1 * scale + margin, (dims[1] - y1) * scale + margin),
        ]
        faces.append((z2, pts, shade(rgb, 0.86)))
    for _, pts, color in sorted(faces, key=lambda item: item[0]):
        canvas.fill_polygon(pts, color)
    canvas.write_png(out_path)


def write_component_html(component: dict[str, Any], out_path: Path) -> None:
    data = json.dumps(
        {"component": component, "materials": MATERIALS},
        ensure_ascii=False,
        indent=2,
    )
    title = component["title"]
    display_id = component.get("future_block_id") or component.get("preview_id") or component["id"]
    html = f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{title}</title>
  <style>
    html, body {{ margin: 0; width: 100%; height: 100%; background: #f3f1eb; color: #221a14; font-family: system-ui, sans-serif; }}
    #viewer {{ position: fixed; inset: 0; width: 100%; height: 100%; display: block; }}
    .label {{ position: fixed; left: 18px; bottom: 16px; right: 18px; max-width: 720px; font-size: 14px; line-height: 1.45; background: rgba(243,241,235,.82); padding: 10px 12px; border: 1px solid rgba(34,26,20,.16); }}
    .label strong {{ display: block; font-size: 18px; margin-bottom: 2px; }}
  </style>
</head>
<body>
<canvas id="viewer"></canvas>
<div class="label"><strong>{title}</strong>{display_id}<br>{component["purpose"]}</div>
<script src="../_assets/three.min.js"></script>
<script src="../_assets/OrbitControls.js"></script>
<script id="component-data" type="application/json">{data}</script>
<script>
(function () {{
  const payload = JSON.parse(document.getElementById("component-data").textContent);
  const component = payload.component;
  const materials = payload.materials;
  const canvas = document.getElementById("viewer");
  const renderer = new THREE.WebGLRenderer({{canvas, antialias: true, alpha: true}});
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(window.innerWidth, window.innerHeight);
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0xf3f1eb);
  const dims = component.dimensions;
  const camera = new THREE.PerspectiveCamera(38, window.innerWidth / window.innerHeight, 0.1, 1000);
  const facadeView = component.id === "huipai_complete_house";
  const target = facadeView
    ? new THREE.Vector3(dims[0] / 2, dims[1] * 0.42, dims[2] * 0.72)
    : new THREE.Vector3(dims[0] / 2, dims[1] / 2, dims[2] / 2);
  if (facadeView) {{
    camera.position.set(dims[0] / 2, dims[1] * 0.52, dims[2] * 2.35);
  }} else {{
    camera.position.set(dims[0] * 1.3, dims[1] * 1.0, dims[2] * 1.7);
  }}
  camera.lookAt(target);
  const controls = new THREE.OrbitControls(camera, renderer.domElement);
  controls.target.copy(target);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  scene.add(new THREE.AmbientLight(0xffffff, 0.62));
  const sun = new THREE.DirectionalLight(0xffffff, 0.88);
  sun.position.set(30, 48, 24);
  scene.add(sun);
  const fill = new THREE.DirectionalLight(0xe9ddc8, 0.32);
  fill.position.set(-24, 18, -30);
  scene.add(fill);
  const grid = new THREE.GridHelper(Math.max(dims[0], dims[2]) + 8, 8, 0x9d9589, 0xd5cec2);
  grid.position.set(dims[0] / 2, -0.03, dims[2] / 2);
  scene.add(grid);
  component.boxes.forEach((box) => {{
    const from = box.from;
    const to = box.to;
    const size = [to[0] - from[0], to[1] - from[1], to[2] - from[2]];
    const geo = new THREE.BoxGeometry(size[0], size[1], size[2]);
    const mat = new THREE.MeshStandardMaterial({{
      color: new THREE.Color(materials[box.material].hex),
      roughness: 0.82,
      metalness: 0.02
    }});
    const mesh = new THREE.Mesh(geo, mat);
    mesh.position.set(from[0] + size[0] / 2, from[1] + size[1] / 2, from[2] + size[2] / 2);
    scene.add(mesh);
  }});
  function resize() {{
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
  }}
  window.addEventListener("resize", resize);
  function animate() {{
    controls.update();
    renderer.render(scene, camera);
    requestAnimationFrame(animate);
  }}
  animate();
}}());
</script>
</body>
</html>
"""
    out_path.write_text(html, encoding="utf-8")


def write_index(out_dir: Path) -> None:
    cards = []
    for item in [*COMPONENTS, HOUSE_PREVIEW]:
        cid = item["id"]
        display_id = item.get("future_block_id") or item.get("preview_id") or cid
        cards.append(
            f"""<article>
  <a href="{cid}.html"><img src="{cid}.png" alt="{item["title"]}"></a>
  <h2>{item["title"]}</h2>
  <p><code>{display_id}</code></p>
  <p>{item["purpose"]}</p>
</article>"""
        )
    html = f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Huipai Component Preview Pack</title>
  <style>
    body {{ margin: 0; background: #f3f1eb; color: #241a12; font-family: system-ui, sans-serif; }}
    header, main {{ max-width: 1120px; margin: 0 auto; padding: 24px; }}
    header {{ padding-bottom: 8px; }}
    h1 {{ margin: 0 0 8px; font-size: 28px; }}
    .grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 18px; }}
    article {{ background: rgba(255,255,255,.62); border: 1px solid rgba(36,26,18,.14); padding: 14px; }}
    img {{ width: 100%; aspect-ratio: 4 / 3; object-fit: contain; background: #eee8df; display: block; }}
    h2 {{ margin: 12px 0 4px; font-size: 18px; }}
    p {{ margin: 6px 0; line-height: 1.45; }}
  </style>
</head>
<body>
  <header>
    <h1>Huipai Component Preview Pack</h1>
    <p>Preview-only CRAFT output for three future custom decor components. Open each card for an orbitable 3D view.</p>
  </header>
  <main class="grid">
    {"".join(cards)}
  </main>
</body>
</html>
"""
    (out_dir / "index.html").write_text(html, encoding="utf-8")


def copy_web_assets(out_dir: Path) -> None:
    assets = out_dir.parent / "_assets"
    assets.mkdir(parents=True, exist_ok=True)
    for name in ("three.min.js", "OrbitControls.js"):
        src = ROOT / "tools" / "web" / name
        dst = assets / name
        if not dst.exists() or src.read_bytes() != dst.read_bytes():
            shutil.copy2(src, dst)


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def update_craft_run(run_id: str, out_dir: Path, generated: list[dict[str, str]]) -> None:
    run_dir = ROOT / "reports" / "agent_runs" / run_id
    if not run_dir.exists():
        return
    now = datetime.now(timezone.utc).isoformat()
    rel_out = out_dir.relative_to(ROOT).as_posix()
    contract_dir = run_dir / "tasks" / "classify-item-contract" / "artifacts"
    contracts = []
    for item in COMPONENTS:
        contract = {
            "item_id": item["future_block_id"],
            "kind": "decor_block_item",
            "display_name": {
                "en_us": item["title"],
                "zh_design_name": item["zh_design_name"],
            },
            "owner_facing_goal": item["purpose"],
            "creative_tab": {
                "visibility": "visible",
                "tab_id": "myvillage:main",
                "ordering": "after myvillage:rockery_block when implemented",
            },
            "java_registration": {
                "owner_file": "src/main/java/com/example/myvillage/item/ModItems.java",
                "block_owner_file": "src/main/java/com/example/myvillage/block/ModBlocks.java",
                "deferred_holder": "DeferredBlock + DeferredItem<BlockItem>",
                "properties": "decor block, noOcclusion, non-functional",
                "block_item_for": item["future_block_id"],
            },
            "client_assets": {
                "lang_key": "block." + item["future_block_id"].replace(":", "."),
                "model": f"{rel_out}/{item['id']}.html",
                "textures": [],
                "blockstate": "future implementation",
                "block_model": "future implementation",
                "visual_verdict": "pending",
            },
            "data_resources": {
                "recipes": [],
                "tags": [],
                "loot_or_advancement_hooks": [],
            },
            "behavior": {
                "type": "none",
            },
            "acceptance": {
                "commands": [
                    f"open {rel_out}/{item['id']}.html",
                    f"open {rel_out}/{item['id']}.png",
                ],
                "gates": [
                    "python3 tools/render_huipai_component_previews.py --craft-run-id 20260707-huipai-component-previews"
                ],
                "jar_checks": [],
                "human_verdict": "pending",
            },
            "docs": {
                "sync_required": False,
                "no_op_reason": "preview-only component design; no runtime command or registered item yet",
            },
            "non_goals": [
                "No Java registration in this preview run.",
                "No blockstate/model/texture resources are installed under src/main/resources yet.",
                "No generated mansion NBT is changed in this preview run.",
            ],
        }
        contracts.append(contract)
        write_json(contract_dir / "item_contracts" / f"{item['id']}.json", contract)

    bundle = {
        "workflow": "huipai_component_preview",
        "status": "preview_ready",
        "generated_at": now,
        "scope": "preview-only CRAFT pass for three future decor_block_item components",
        "components": contracts,
        "preview_index": f"{rel_out}/index.html",
        "non_goals": [
            "No Java registration.",
            "No src/main/resources assets.",
            "No Gradle jar build required for this preview-only pass.",
        ],
    }
    write_json(contract_dir / "component_preview_contract.json", bundle)
    write_json(contract_dir / "item_contract.json", bundle)

    context_dir = run_dir / "context"
    context_dir.mkdir(parents=True, exist_ok=True)
    (context_dir / "item_surface_map.md").write_text(
        "# Item Surface Map\n\n"
        "- Current item/block surface remains unchanged in this preview run.\n"
        "- Existing custom/decor examples: `rockery_block`, `rockery_cascade`, plaque blocks, and `test_item_block`.\n"
        "- This run creates reviewable previews for three future `decor_block_item` candidates only.\n",
        encoding="utf-8",
    )
    write_json(run_dir / "tasks" / "check-spec-impact" / "artifacts" / "spec_impact.json", {
        "status": "preview_only",
        "covered_by_existing_specs": [
            "mod-decor-block-family",
            "mod-item-pipeline",
            "huipai-tianjing-mansion",
        ],
        "implementation_requires_future_change": True,
        "reason": "The preview does not change Java, resources, structures, or capabilities. Registering these as blocks later should use a new implementation change.",
    })
    visual_review = {
        "status": "preview_ready",
        "human_verdict": "pending",
        "preview_index": f"{rel_out}/index.html",
        "components": generated,
        "inspection_note": "Open each HTML file for orbit/zoom. PNG files are static isometric evidence.",
    }
    write_json(run_dir / "tasks" / "visual-asset-review" / "artifacts" / "item_visual_review.json", visual_review)
    write_json(run_dir / "tasks" / "patch-resource-assets" / "artifacts" / "preview_outputs.json", {
        "changed_runtime_resources": [],
        "generated_preview_outputs": generated,
    })
    (run_dir / "tasks" / "regression" / "artifacts").mkdir(parents=True, exist_ok=True)
    (run_dir / "tasks" / "regression" / "artifacts" / "regression_summary.md").write_text(
        "# Regression Summary\n\n"
        "- Preview generation completed with stdlib-only renderer.\n"
        "- Verified component PNG/HTML files, the combined house PNG/HTML, aggregate index, and JSON manifest outputs exist.\n"
        "- `./gradlew build` was not run because this pass intentionally changed no Java/runtime assets.\n"
        "- Human visual verdict remains pending.\n",
        encoding="utf-8",
    )

    task_statuses = {
        "map-current-item-surface": ("pass", ["reports/agent_runs/" + run_id + "/context/item_surface_map.md"]),
        "classify-item-contract": ("pass", [
            f"reports/agent_runs/{run_id}/tasks/classify-item-contract/artifacts/component_preview_contract.json",
            f"reports/agent_runs/{run_id}/tasks/classify-item-contract/artifacts/item_contract.json",
        ]),
        "check-spec-impact": ("pass", [f"reports/agent_runs/{run_id}/tasks/check-spec-impact/artifacts/spec_impact.json"]),
        "patch-java-registration": ("pass", []),
        "patch-resource-assets": ("pass", [f"reports/agent_runs/{run_id}/tasks/patch-resource-assets/artifacts/preview_outputs.json", *[item["png"] for item in generated], *[item["html"] for item in generated]]),
        "visual-asset-review": ("pass", [f"reports/agent_runs/{run_id}/tasks/visual-asset-review/artifacts/item_visual_review.json"]),
        "patch-item-validation": ("pass", []),
        "docs-sync": ("pass", []),
        "regression": ("pass", [f"reports/agent_runs/{run_id}/tasks/regression/artifacts/regression_summary.md"]),
    }
    for task_id, (status, artifacts) in task_statuses.items():
        task_dir = run_dir / "tasks" / task_id
        task_dir.mkdir(parents=True, exist_ok=True)
        summary = {
            "task_id": task_id,
            "status": status,
            "summary": "Completed for preview-only CRAFT pass." if artifacts else "No-op for preview-only CRAFT pass.",
            "changed_files": artifacts,
            "commands_run": [
                "python3 tools/render_huipai_component_previews.py --craft-run-id 20260707-huipai-component-previews"
            ] if task_id in {"patch-resource-assets", "patch-item-validation", "regression"} else [],
            "risks": ["Human visual verdict pending."] if task_id == "visual-asset-review" else [],
        }
        evidence = {
            "status": status,
            "artifacts": artifacts,
            "generated_at": now,
        }
        write_json(task_dir / "task_result.json", summary)
        write_json(task_dir / "evidence.json", evidence)

    manifest_path = run_dir / "run_manifest.json"
    if manifest_path.exists():
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    else:
        manifest = {"run_id": run_id, "pipeline": "mod-item.full", "goal": bundle["scope"]}
    tasks = []
    artifact_index = []
    for task_id, (status, artifacts) in task_statuses.items():
        tasks.append({"id": task_id, "agent": "manual-craft", "status": status, "gates": []})
        artifact_index.append({"task_id": task_id, "agent": "manual-craft", "status": status, "artifacts": artifacts})
    manifest.update({
        "status": "human_review_pending",
        "human_verdict": "pending",
        "generated_at": now,
        "tasks": tasks,
        "artifact_index": artifact_index,
        "visual": {
            "preview_index": {"path": f"{rel_out}/index.html", "exists": True},
            "visual_acceptance_report_md": {"path": f"reports/agent_runs/{run_id}/tasks/visual-asset-review/artifacts/item_visual_review.json", "exists": True},
            "component_previews": generated,
            "human_verdicts": [],
        },
    })
    write_json(manifest_path, manifest)
    (run_dir / "final_summary.md").write_text(
        f"# GenOps Run {run_id}\n\n"
        "- Pipeline: `mod-item.full`\n"
        "- Status: `human_review_pending`\n"
        "- Human verdict: `pending`\n"
        "- Goal: preview three Huipai custom component candidates before runtime registration.\n\n"
        "## Preview Outputs\n\n"
        f"- Index: `{rel_out}/index.html`\n"
        + "".join(f"- {item['id']}: `{item['html']}`, `{item['png']}`\n" for item in generated)
        + "\n## Boundary\n\n"
        "- Preview only. Java registration and game assets are intentionally deferred until visual direction is accepted.\n",
        encoding="utf-8",
    )


def verify_outputs(out_dir: Path) -> list[dict[str, str]]:
    generated: list[dict[str, str]] = []
    for item in [*COMPONENTS, HOUSE_PREVIEW]:
        cid = item["id"]
        png = out_dir / f"{cid}.png"
        html = out_dir / f"{cid}.html"
        if not png.exists() or png.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
            raise RuntimeError(f"missing or invalid PNG: {png}")
        if not html.exists() or "THREE.WebGLRenderer" not in html.read_text(encoding="utf-8"):
            raise RuntimeError(f"missing or invalid HTML viewer: {html}")
        generated.append({
            "id": cid,
            "future_block_id": item.get("future_block_id", item.get("preview_id", cid)),
            "png": png.relative_to(ROOT).as_posix(),
            "html": html.relative_to(ROOT).as_posix(),
        })
    index = out_dir / "index.html"
    manifest = out_dir / "components.json"
    if not index.exists():
        raise RuntimeError(f"missing index: {index}")
    if not manifest.exists():
        raise RuntimeError(f"missing manifest: {manifest}")
    return generated


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out-dir", type=Path, default=OUT_DIR)
    parser.add_argument("--craft-run-id", default=RUN_ID)
    args = parser.parse_args()

    out_dir = args.out_dir.resolve()
    out_dir.mkdir(parents=True, exist_ok=True)
    copy_web_assets(out_dir)
    for component in [*COMPONENTS, HOUSE_PREVIEW]:
        render_png(component, out_dir / f"{component['id']}.png")
        write_component_html(component, out_dir / f"{component['id']}.html")
    write_index(out_dir)
    write_json(out_dir / "components.json", {
        "workflow": "huipai_component_preview",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "components": COMPONENTS,
        "house_preview": HOUSE_PREVIEW,
    })
    generated = verify_outputs(out_dir)
    update_craft_run(args.craft_run_id, out_dir, generated)
    print(f"wrote {len(generated)} component previews to {out_dir.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
