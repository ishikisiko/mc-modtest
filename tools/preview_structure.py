#!/usr/bin/env python3
"""Offline visual preview for Minecraft structures.

Renders a structure (.nbt artifact or .json DSL) to PNG images without launching
the game, so layout, massing, and roof shapes can be eyeballed quickly. Produces:
  - per-Y XZ top-down slice PNGs (one per layer) + a contact sheet
  - an isometric 3D overview PNG (painter's-algorithm, shaded cube faces)
  - a numbered color legend PNG plus legend.txt mapping swatch ids -> block id
  - viewer.html interactive previews, plus index.html when multiple viewers are
    generated in one run

This is primarily a coarse voxel-color preview. Plaque block textures are
resolved from shipped models, and legacy inscription paintings are overlaid
when present so signage can be checked before opening a Minecraft client; other
blockstate detail such as door facing or trapdoor open/closed still needs an
in-game /place template check.

Input may be either:
  - a Minecraft structure .nbt file (the artifact loaded by /place template), or
  - a structure JSON DSL file (palette + ops) understood by tools/json_to_nbt.py.

Example:
  python3 tools/preview_structure.py \\
      src/main/resources/data/myvillage/structure/small_house_001.nbt
  python3 tools/preview_structure.py --all \\
      --src src/main/resources/data/myvillage/structure
"""

from __future__ import annotations

import argparse
import html
import json
import math
import os
import shutil
import struct
import sys
import zlib
from pathlib import Path
from typing import Dict, Iterable, List, NamedTuple, Sequence, Tuple

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

from buildgen.nbtread import read_gzipped_nbt, state_string  # noqa: E402
from buildgen.gen_plaque_assets import read_png_rgba  # noqa: E402
from buildgen.groups import GROUPS  # noqa: E402
from json_to_nbt import expand_structure  # noqa: E402

BLOCK_COLORS_PATH = os.path.join(SCRIPT_DIR, "block_colors.json")
DEFAULT_OUT_ROOT = os.path.join(REPO_ROOT, "out", "preview")
WEB_ASSET_DIR = os.path.join(SCRIPT_DIR, "web")

RGBA = Tuple[int, int, int, int]
Voxels = Dict[Tuple[int, int, int], str]
Entities = List[dict]
AIR_BASES = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
PLAQUE_BLOCK_IDS = {
    "myvillage:wall_plaque",
    "myvillage:wall_plaque_vertical",
    "myvillage:hanging_plaque",
    "myvillage:hanging_plaque_vertical",
}
FACING_FROM_2D = {0: "south", 1: "west", 2: "north", 3: "east"}
FACING_NORMAL = {
    "north": (0.0, 0.0, -1.0),
    "south": (0.0, 0.0, 1.0),
    "west": (-1.0, 0.0, 0.0),
    "east": (1.0, 0.0, 0.0),
}
FACING_COUNTERCLOCKWISE = {
    "north": (-1.0, 0.0),
    "south": (1.0, 0.0),
    "west": (0.0, 1.0),
    "east": (0.0, -1.0),
}


class RenderResult(NamedTuple):
    status: int
    source: str
    stem: str
    out_dir: str
    viewer_path: str

_DIGITS = {
    "0": ["111", "101", "101", "101", "111"],
    "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"],
    "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"],
    "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"],
    "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"],
    "9": ["111", "101", "111", "001", "111"],
}


def load_block_colors(path: str = BLOCK_COLORS_PATH) -> Dict[str, Tuple[int, int, int]]:
    with open(path, "r", encoding="utf-8") as f:
        raw = json.load(f)
    out: Dict[str, Tuple[int, int, int]] = {}
    for key, val in raw.items():
        if key.startswith("_"):
            continue
        if val is None:
            continue
        if not (isinstance(val, list) and len(val) == 3):
            raise ValueError(f"block_colors.json entry {key!r} must be [r,g,b] or null")
        out[key] = (int(val[0]), int(val[1]), int(val[2]))
    return out


def state_base(state: str) -> str:
    return state.split("[", 1)[0]


def is_air_state(state: str) -> bool:
    return state_base(state) in AIR_BASES


_FAMILY_SUFFIXES = (
    "_stairs",
    "_slab",
    "_double_slab",
    "_fence_gate",
    "_fence",
    "_door",
    "_trapdoor",
    "_wall_sign",
    "_sign",
    "_wall_banner",
    "_banner",
    "_bed",
    "_button",
    "_pressure_plate",
)


def resolve_color(state: str, colors: Dict[str, Tuple[int, int, int]]) -> Tuple[int, int, int]:
    base = state_base(state)
    if base in colors:
        return colors[base]
    for suffix in _FAMILY_SUFFIXES:
        if base.endswith(suffix):
            root = base[: -len(suffix)]
            for candidate in (root, f"{root}s", f"{root}_planks",
                              f"{root}_log", f"{root}_wood", f"{root}_block"):
                if candidate in colors:
                    return colors[candidate]
    return (180, 80, 200)


def read_nbt_voxels(path: str) -> Tuple[List[int], Voxels, Entities]:
    _, root = read_gzipped_nbt(path)
    size = [int(v) for v in root["size"]]
    palette = [state_string(p) for p in root["palette"]]
    voxels: Voxels = {}
    for entry in root["blocks"]:
        pos = tuple(int(v) for v in entry["pos"])
        voxels[pos] = palette[int(entry["state"])]
    return size, voxels, list(root.get("entities", []))


def read_json_voxels(path: str) -> Tuple[List[int], Voxels, Entities]:
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    size, _overwritten, blocks, _states = expand_structure(data)
    voxels: Voxels = {pos: state for pos, state in blocks.items()}
    return list(size), voxels, list(data.get("entities", []))


def read_voxels(path: str) -> Tuple[List[int], Voxels, Entities]:
    ext = os.path.splitext(path)[1].lower()
    if ext == ".nbt":
        return read_nbt_voxels(path)
    if ext == ".json":
        return read_json_voxels(path)
    raise ValueError(f"unsupported input extension {ext!r} for {path!r}")


def state_props(state: str) -> Dict[str, str]:
    if "[" not in state:
        return {}
    raw = state.split("[", 1)[1].rstrip("]")
    props: Dict[str, str] = {}
    for part in raw.split(","):
        key, _, value = part.partition("=")
        if key:
            props[key] = value
    return props


def _resource_path(ref: str, kind: str) -> str:
    namespace, path = ref.split(":", 1)
    if kind == "model":
        return os.path.join(REPO_ROOT, "src", "main", "resources", "assets", namespace, "models", f"{path}.json")
    if kind == "texture":
        return os.path.join(REPO_ROOT, "src", "main", "resources", "assets", namespace, "textures", f"{path}.png")
    raise ValueError(kind)


def _copy_preview_texture(out_dir: str, source: str) -> str:
    asset_dir = os.path.join(os.path.dirname(out_dir), "_assets", "textures")
    os.makedirs(asset_dir, exist_ok=True)
    rel_name = os.path.relpath(source, os.path.join(REPO_ROOT, "src", "main", "resources", "assets")).replace(os.sep, "__")
    target = os.path.join(asset_dir, rel_name)
    if not os.path.exists(target) or os.path.getmtime(source) > os.path.getmtime(target):
        shutil.copy2(source, target)
    return os.path.relpath(target, out_dir).replace(os.sep, "/")


def _copy_preview_texture_uv(out_dir: str, source: str, uv: list[float] | None) -> str:
    if not uv or uv == [0, 0, 16, 16]:
        return _copy_preview_texture(out_dir, source)
    src_w, src_h, rgba = read_png_rgba(Path(source))
    u0, v0, u1, v1 = uv
    x0 = max(0, min(src_w, int(round(min(u0, u1) / 16.0 * src_w))))
    x1 = max(0, min(src_w, int(round(max(u0, u1) / 16.0 * src_w))))
    y0 = max(0, min(src_h, int(round(min(v0, v1) / 16.0 * src_h))))
    y1 = max(0, min(src_h, int(round(max(v0, v1) / 16.0 * src_h))))
    if x1 <= x0 or y1 <= y0:
        return _copy_preview_texture(out_dir, source)

    crop_w = x1 - x0
    crop_h = y1 - y0
    crop = bytearray(crop_w * crop_h * 4)
    for y in range(crop_h):
        src_i = ((y0 + y) * src_w + x0) * 4
        dst_i = y * crop_w * 4
        crop[dst_i:dst_i + crop_w * 4] = rgba[src_i:src_i + crop_w * 4]

    asset_dir = os.path.join(os.path.dirname(out_dir), "_assets", "textures")
    os.makedirs(asset_dir, exist_ok=True)
    rel_name = os.path.relpath(source, os.path.join(REPO_ROOT, "src", "main", "resources", "assets")).replace(os.sep, "__")
    uv_key = "_".join(str(int(round(value * 1000))) for value in uv)
    target = os.path.join(asset_dir, f"{rel_name}__uv_{uv_key}.png")
    if not os.path.exists(target) or os.path.getmtime(source) > os.path.getmtime(target):
        write_rgba_png(target, crop_w, crop_h, bytes(crop))
    return os.path.relpath(target, out_dir).replace(os.sep, "/")


def _placeholder_texture(out_dir: str) -> str:
    path = os.path.join(os.path.dirname(out_dir), "_assets", "textures", "missing_inscription_asset.png")
    if not os.path.exists(path):
        canvas = Canvas(16, 16)
        canvas.fill_rect(0, 0, 16, 16, (146, 45, 80, 255))
        for i in range(16):
            canvas.set(i, i, (255, 220, 235, 255))
            canvas.set(15 - i, i, (255, 220, 235, 255))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        canvas.write_png(path)
    return os.path.relpath(path, out_dir).replace(os.sep, "/")


def _face_center(pos: Tuple[int, int, int], facing: str, width: float = 1.0,
                 height: float = 1.0) -> List[float]:
    x, y, z = pos
    nx, _ny, nz = FACING_NORMAL.get(facing, FACING_NORMAL["north"])
    cx = x + 0.5 + nx * 0.515
    cy = y + height / 2.0
    cz = z + 0.5 + nz * 0.515
    return [cx, cy, cz]


def _plaque_texture_for_state(state: str) -> tuple[str, list[float] | None] | None:
    base = state_base(state)
    if base not in PLAQUE_BLOCK_IDS:
        return None
    props = state_props(state)
    block_name = base.split(":", 1)[1]
    key = (
        f"facing={props.get('facing', 'north')},"
        f"frame={props.get('frame', 'town_shop_wood_3w')},"
        f"row={props.get('row', 'single')},"
        f"col={props.get('col', 'center')}"
    )
    blockstate_path = os.path.join(REPO_ROOT, "src", "main", "resources", "assets", "myvillage", "blockstates", f"{block_name}.json")
    with open(blockstate_path, "r", encoding="utf-8") as f:
        blockstate = json.load(f)
    variant = blockstate.get("variants", {}).get(key)
    if not variant:
        return None
    model_ref = variant["model"]
    with open(_resource_path(model_ref, "model"), "r", encoding="utf-8") as f:
        model = json.load(f)
    texture_ref = model.get("textures", {}).get("plaque")
    if not texture_ref:
        return None
    uv = None
    for element in model.get("elements", []):
        face = element.get("faces", {}).get("north")
        if face and face.get("texture") == "#plaque":
            uv = face.get("uv")
            break
    return _resource_path(texture_ref, "texture"), uv


def _painting_variant_size_and_texture(variant: str) -> tuple[int, int, str]:
    namespace, path = variant.split(":", 1)
    data_path = os.path.join(REPO_ROOT, "src", "main", "resources", "data", namespace, "painting_variant", f"{path}.json")
    with open(data_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    texture_ref = data["asset_id"]
    texture_path = _resource_path(texture_ref, "texture")
    if not os.path.exists(texture_path):
        texture_namespace, texture_id = texture_ref.split(":", 1)
        texture_path = os.path.join(
            REPO_ROOT,
            "src",
            "main",
            "resources",
            "assets",
            texture_namespace,
            "textures",
            "painting",
            f"{texture_id}.png",
        )
    if not os.path.exists(texture_path):
        texture_namespace, texture_id = texture_ref.split(":", 1)
        texture_path = os.path.join(
            REPO_ROOT,
            "src",
            "main",
            "resources",
            "assets",
            texture_namespace,
            "textures",
            "entity",
            f"{texture_id}.png",
        )
    return int(data["width"]), int(data["height"]), texture_path


def _painting_center_from_anchor(entity: dict, nbt: dict, facing: str,
                                 width: int, height: int) -> list[float]:
    raw_pos = entity.get("pos", [0.0, 0.0, 0.0])
    anchor = [
        int(nbt.get("TileX", math.floor(float(raw_pos[0])))),
        int(nbt.get("TileY", math.floor(float(raw_pos[1])))),
        int(nbt.get("TileZ", math.floor(float(raw_pos[2])))),
    ]
    nx, _ny, nz = FACING_NORMAL.get(facing, FACING_NORMAL["north"])
    tx, tz = FACING_COUNTERCLOCKWISE.get(facing, FACING_COUNTERCLOCKWISE["north"])
    tangent_offset = 0.5 if width % 2 == 0 else 0.0
    vertical_offset = 0.5 if height % 2 == 0 else 0.0
    return [
        anchor[0] + 0.5 - nx * 0.46875 + tx * tangent_offset,
        anchor[1] + 0.5 + vertical_offset,
        anchor[2] + 0.5 - nz * 0.46875 + tz * tangent_offset,
    ]


def build_texture_overlays(voxels: Voxels, entities: Entities, out_dir: str) -> tuple[list[dict], list[str]]:
    overlays: list[dict] = []
    flags: list[str] = []
    plaque_count = 0
    inscription_count = 0

    for pos, state in sorted(voxels.items()):
        if state_base(state) not in PLAQUE_BLOCK_IDS:
            continue
        plaque_count += 1
        props = state_props(state)
        facing = props.get("facing", "north")
        resolved = _plaque_texture_for_state(state)
        if not resolved:
            continue
        texture_path, uv = resolved
        if not texture_path or not os.path.exists(texture_path):
            continue
        overlays.append({
            "kind": "plaque",
            "center": _face_center(pos, facing),
            "width": 1,
            "height": 1,
            "facing": facing,
            "texture": _copy_preview_texture_uv(out_dir, texture_path, uv),
        })

    for entity in entities:
        nbt = entity.get("nbt", {})
        if not isinstance(nbt, dict) or nbt.get("id") != "minecraft:painting":
            continue
        variant = str(nbt.get("variant", ""))
        if not variant.startswith("myvillage:inscription/"):
            continue
        inscription_count += 1
        facing = FACING_FROM_2D.get(int(nbt.get("facing", 2)), "north")
        try:
            width, height, texture_path = _painting_variant_size_and_texture(variant)
            if not os.path.exists(texture_path):
                raise FileNotFoundError(texture_path)
            texture_href = _copy_preview_texture(out_dir, texture_path)
        except Exception:
            width, height = 1, 1
            texture_href = _placeholder_texture(out_dir)
            if "missing_inscription_asset" not in flags:
                flags.append("missing_inscription_asset")
        center = _painting_center_from_anchor(entity, nbt, facing, width, height)
        overlays.append({
            "kind": "inscription",
            "center": center,
            "width": width,
            "height": height,
            "facing": facing,
            "texture": texture_href,
            "variant": variant,
        })

    return overlays, flags


class Canvas:
    __slots__ = ("w", "h", "buf")

    def __init__(self, w: int, h: int) -> None:
        self.w = w
        self.h = h
        self.buf = bytearray(w * h * 4)

    def set(self, x: int, y: int, color: RGBA) -> None:
        if x < 0 or y < 0 or x >= self.w or y >= self.h:
            return
        i = (y * self.w + x) * 4
        r, g, b, a = color
        if a >= 255:
            self.buf[i] = r
            self.buf[i + 1] = g
            self.buf[i + 2] = b
            self.buf[i + 3] = 255
            return
        if a <= 0:
            return
        ia = 255 - a
        self.buf[i] = (r * a + self.buf[i] * ia) // 255
        self.buf[i + 1] = (g * a + self.buf[i + 1] * ia) // 255
        self.buf[i + 2] = (b * a + self.buf[i + 2] * ia) // 255
        self.buf[i + 3] = 255

    def fill_rect(self, x0: int, y0: int, x1: int, y1: int, color: RGBA) -> None:
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.set(x, y, color)

    def fill_polygon(self, pts: Sequence[Tuple[float, float]], color: RGBA) -> None:
        if len(pts) < 3:
            return
        ys = [p[1] for p in pts]
        y_lo = max(0, int(math.floor(min(ys))))
        y_hi = min(self.h - 1, int(math.ceil(max(ys))))
        n = len(pts)
        for y in range(y_lo, y_hi + 1):
            yc = y + 0.5
            xs: List[float] = []
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
                    self.set(x, y, color)

    def draw_number(self, x: int, y: int, text: str, color: RGBA) -> None:
        cx = x
        for ch in text:
            glyph = _DIGITS.get(ch)
            if glyph is None:
                cx += 4
                continue
            for ry, row in enumerate(glyph):
                for rx, bit in enumerate(row):
                    if bit == "1":
                        self.set(cx + rx, y + ry, color)
            cx += 4

    def write_png(self, path: str) -> None:
        write_rgba_png(path, self.w, self.h, self.buf)


def write_rgba_png(path: str, w: int, h: int, buf: bytes) -> None:
    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    raw = bytearray()
    idx = 0
    for _y in range(h):
        raw.append(0)
        raw.extend(buf[idx:idx + w * 4])
        idx += w * 4
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def shade(color: Tuple[int, int, int], factor: float) -> RGBA:
    return (max(0, min(255, int(color[0] * factor))),
            max(0, min(255, int(color[1] * factor))),
            max(0, min(255, int(color[2] * factor))), 255)


def render_slices(size: List[int], voxels: Voxels, out_dir: str, scale: int,
                  contact_only: bool, max_px: int) -> List[str]:
    sx, sy, sz = size
    cols = max(1, math.ceil(math.sqrt(sy)))
    rows = max(1, math.ceil(sy / cols))
    eff_scale = scale
    est_w = cols * sx * eff_scale
    est_h = rows * sz * eff_scale
    if max(est_w, est_h) > max_px:
        factor = min(max_px / est_w if est_w else 1.0, max_px / est_h if est_h else 1.0)
        eff_scale = max(1, int(eff_scale * factor))
    tw, th = sx * eff_scale, sz * eff_scale
    pad = 6
    sheet_w = cols * tw + (cols + 1) * pad
    sheet_h = rows * th + (rows + 1) * pad
    sheet = Canvas(sheet_w, sheet_h)
    written: List[str] = []

    for y in range(sy):
        tile = Canvas(tw, th)
        for z in range(sz):
            for x in range(sx):
                state = voxels.get((x, y, z))
                if not state or is_air_state(state):
                    continue
                color = resolve_color(state, _COLORS)
                tile.fill_rect(x * eff_scale, z * eff_scale, x * eff_scale + eff_scale, z * eff_scale + eff_scale, color + (255,))
        tile.draw_number(2, 2, str(y), (0, 0, 0, 255))
        tile.draw_number(3, 3, str(y), (255, 255, 255, 255))

        if not contact_only:
            path = os.path.join(out_dir, f"slice_y{y:02d}.png")
            tile.write_png(path)
            written.append(path)

        row, col = divmod(y, cols)
        ox = pad + col * (tw + pad)
        oy = pad + row * (th + pad)
        for zy in range(th):
            src = zy * tw * 4
            dst = ((oy + zy) * sheet_w + ox) * 4
            sheet.buf[dst:dst + tw * 4] = tile.buf[src:src + tw * 4]

    path = os.path.join(out_dir, "slices_contact.png")
    sheet.write_png(path)
    written.append(path)
    return written


def render_isometric(size: List[int], voxels: Voxels, out_dir: str, scale: int,
                     cull_interior: bool, max_px: int) -> str:
    sx, sy, sz = size
    eff_scale = scale
    est_w = (sx + sz) * eff_scale + 8
    est_h = ((sx + sz) / 2.0 + sy) * eff_scale + 8
    if max(est_w, est_h) > max_px:
        factor = min(max_px / est_w, max_px / est_h)
        eff_scale = max(2, int(eff_scale * factor))
    s = eff_scale
    half = s / 2.0
    voxel_h = float(s)

    def top_center(x: int, y: int, z: int) -> Tuple[float, float]:
        cx = (x - z) * s
        cy = (x + z) * half - y * voxel_h
        return cx, cy

    if cull_interior:
        draw = [(x, y, z, st) for (x, y, z), st in voxels.items()
                if _is_exposed(x, y, z, voxels)]
    else:
        draw = list((x, y, z, st) for (x, y, z), st in voxels.items())

    draw.sort(key=lambda v: (v[0] + v[2], v[1]))

    min_x = min_y = math.inf
    max_x = max_y = -math.inf
    for x, y, z, _ in draw:
        cx, cy = top_center(x, y, z)
        min_x = min(min_x, cx - s, cx + s)
        max_x = max(max_x, cx - s, cx + s)
        min_y = min(min_y, cy - half)
        max_y = max(max_y, cy + half + voxel_h)
    margin = 4
    off_x = -min_x + margin
    off_y = -min_y + margin
    width = int(math.ceil(max_x - min_x)) + 2 * margin
    height = int(math.ceil(max_y - min_y)) + 2 * margin
    canvas = Canvas(width, height)

    for x, y, z, state in draw:
        if is_air_state(state):
            continue
        base_color = resolve_color(state, _COLORS)
        cx, cy = top_center(x, y, z)
        cx += off_x
        cy += off_y
        top_face = [(cx, cy - half), (cx + s, cy), (cx, cy + half), (cx - s, cy)]
        left_face = [(cx - s, cy), (cx, cy + half),
                     (cx, cy + half + voxel_h), (cx - s, cy + voxel_h)]
        right_face = [(cx + s, cy), (cx, cy + half),
                      (cx, cy + half + voxel_h), (cx + s, cy + voxel_h)]
        canvas.fill_polygon(left_face, shade(base_color, 0.62))
        canvas.fill_polygon(right_face, shade(base_color, 0.80))
        canvas.fill_polygon(top_face, shade(base_color, 1.0))

    path = os.path.join(out_dir, "isometric.png")
    canvas.write_png(path)
    return path


def _is_exposed(x: int, y: int, z: int, voxels: Voxels) -> bool:
    for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
        nb = voxels.get((x + dx, y + dy, z + dz))
        if nb is None or is_air_state(nb):
            return True
    return False


def render_legend(voxels: Voxels, out_dir: str, swatch: int = 18) -> Tuple[str, str]:
    seen: Dict[str, None] = {}
    for state in voxels.values():
        base = state_base(state)
        if base not in AIR_BASES:
            seen.setdefault(base, None)
    bases = list(seen.keys())
    cols = max(1, math.ceil(math.sqrt(len(bases))))
    rows = max(1, math.ceil(len(bases) / cols))
    pad = 4
    cell = swatch + pad
    label_h = 8
    canvas = Canvas(cols * cell + pad, rows * (cell + label_h) + pad)
    lines = ["# legend index -> block id (base name, states stripped)"]
    for i, base in enumerate(bases):
        row, col = divmod(i, cols)
        ox = pad + col * cell
        oy = pad + row * (cell + label_h)
        color = resolve_color(base, _COLORS)
        canvas.fill_rect(ox, oy, ox + swatch, oy + swatch, color + (255,))
        canvas.draw_number(ox + 1, oy + swatch + 1, str(i), (0, 0, 0, 255))
        canvas.draw_number(ox + 2, oy + swatch + 2, str(i), (255, 255, 255, 255))
        lines.append(f"{i}: {base}  rgb={color[0]},{color[1]},{color[2]}")
    png_path = os.path.join(out_dir, "legend.png")
    canvas.write_png(png_path)
    txt_path = os.path.join(out_dir, "legend.txt")
    with open(txt_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    return png_path, txt_path


VIEWER_TEMPLATE = """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Minecraft Structure Preview</title>
  <style>
    :root {
      color-scheme: light;
      --bg: #f6f7f9;
      --panel: rgba(255, 255, 255, 0.94);
      --ink: #172033;
      --muted: #5f6b7a;
      --line: #d6dce5;
      --accent: #256d7b;
      --accent-strong: #174c57;
      --danger: #9f3a46;
    }
    * { box-sizing: border-box; }
    html, body {
      width: 100%;
      height: 100%;
      margin: 0;
      overflow: hidden;
      background: var(--bg);
      color: var(--ink);
      font: 13px/1.35 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      letter-spacing: 0;
    }
    #viewer {
      position: fixed;
      inset: 0;
      width: 100%;
      height: 100%;
      display: block;
    }
    .panel {
      position: fixed;
      top: 12px;
      left: 12px;
      width: min(340px, calc(100vw - 24px));
      max-height: calc(100vh - 24px);
      overflow: auto;
      padding: 12px;
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 8px;
      box-shadow: 0 10px 30px rgba(23, 32, 51, 0.14);
      backdrop-filter: blur(8px);
    }
    h1 {
      margin: 0 0 2px;
      font-size: 16px;
      line-height: 1.2;
      font-weight: 700;
    }
    .meta {
      margin: 0 0 10px;
      color: var(--muted);
      font-size: 12px;
    }
    .status {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 6px;
      margin: 0 0 10px;
    }
    .stat {
      min-width: 0;
      padding: 7px 8px;
      border: 1px solid var(--line);
      border-radius: 6px;
      background: #fbfcfd;
    }
    .stat b {
      display: block;
      overflow-wrap: anywhere;
      font-size: 12px;
      color: var(--ink);
    }
    .stat span {
      display: block;
      color: var(--muted);
      font-size: 11px;
    }
    fieldset {
      margin: 10px 0 0;
      padding: 9px;
      border: 1px solid var(--line);
      border-radius: 8px;
      min-width: 0;
    }
    legend {
      padding: 0 5px;
      color: var(--muted);
      font-weight: 700;
      font-size: 11px;
      text-transform: uppercase;
    }
    label {
      display: block;
      color: var(--ink);
      font-weight: 600;
    }
    .row {
      display: grid;
      grid-template-columns: 76px minmax(0, 1fr) auto;
      align-items: center;
      gap: 8px;
      margin-top: 7px;
    }
    select,
    input[type="range"] {
      width: 100%;
      min-width: 0;
    }
    select {
      height: 28px;
      border: 1px solid var(--line);
      border-radius: 6px;
      background: white;
      color: var(--ink);
      font: inherit;
    }
    input[type="range"] {
      accent-color: var(--accent);
    }
    output {
      min-width: 42px;
      text-align: right;
      color: var(--muted);
      font-variant-numeric: tabular-nums;
    }
    .block-actions {
      display: flex;
      gap: 8px;
      margin: 4px 0 8px;
    }
    button {
      min-height: 28px;
      border: 1px solid var(--line);
      border-radius: 6px;
      background: white;
      color: var(--accent-strong);
      font: inherit;
      font-weight: 700;
      cursor: pointer;
    }
    button:hover {
      border-color: var(--accent);
    }
    #block-list {
      display: grid;
      gap: 5px;
      max-height: 210px;
      overflow: auto;
      padding-right: 2px;
    }
    .check-row {
      display: grid;
      grid-template-columns: auto 14px minmax(0, 1fr);
      gap: 7px;
      align-items: center;
      min-width: 0;
      font-weight: 500;
      color: var(--ink);
    }
    .swatch {
      width: 14px;
      height: 14px;
      border-radius: 3px;
      border: 1px solid rgba(0, 0, 0, 0.22);
    }
    .block-name {
      min-width: 0;
      overflow-wrap: anywhere;
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      font-size: 11px;
    }
    .caveat {
      margin: 10px 0 0;
      color: var(--danger);
      font-size: 11px;
    }
    @media (max-width: 600px) {
      .panel {
        top: auto;
        bottom: 8px;
        left: 8px;
        width: calc(100vw - 16px);
        max-height: 42vh;
        padding: 10px;
      }
      .status {
        grid-template-columns: 1fr;
      }
      #block-list {
        max-height: 120px;
      }
    }
  </style>
</head>
<body>
  <canvas id="viewer"></canvas>
  <aside class="panel" aria-label="Preview controls">
    <h1 id="title">Structure Preview</h1>
    <p class="meta" id="source"></p>
    <div class="status">
      <div class="stat"><b id="visible-count">0 / 0</b><span>visible voxels</span></div>
      <div class="stat"><b id="size-label">0 x 0 x 0</b><span>size X Y Z</span></div>
    </div>

    <fieldset>
      <legend>Cross-section</legend>
      <div class="row">
        <label for="cut-axis">Axis</label>
        <select id="cut-axis">
          <option value="x">X</option>
          <option value="y">Y</option>
          <option value="z">Z</option>
        </select>
        <output id="cut-value">0</output>
      </div>
      <div class="row">
        <label for="cut-slider">Plane</label>
        <input id="cut-slider" type="range" min="0" max="0" value="0">
        <output id="cut-max">0</output>
      </div>
    </fieldset>

    <fieldset>
      <legend>Y layers</legend>
      <div class="row">
        <label for="layer-min">From</label>
        <input id="layer-min" type="range" min="0" max="0" value="0">
        <output id="layer-min-value">0</output>
      </div>
      <div class="row">
        <label for="layer-max">To</label>
        <input id="layer-max" type="range" min="0" max="0" value="0">
        <output id="layer-max-value">0</output>
      </div>
    </fieldset>

    <fieldset>
      <legend>Blocks</legend>
      <div class="block-actions">
        <button id="all-blocks" type="button">All</button>
        <button id="no-blocks" type="button">None</button>
      </div>
      <div id="block-list"></div>
    </fieldset>

    <p class="caveat">Voxel-color preview only; verify blockstate detail in game.</p>
  </aside>

  <script src="__THREE_SRC__"></script>
  <script src="__ORBIT_SRC__"></script>
  <script>
    window.VIEWER_PAYLOAD = __VIEWER_PAYLOAD__;
  </script>
  <script>
    (function () {
      "use strict";

      const payload = window.VIEWER_PAYLOAD;
      const axisIndex = { x: 0, y: 1, z: 2 };
      const canvas = document.getElementById("viewer");
      const title = document.getElementById("title");
      const source = document.getElementById("source");
      const visibleCount = document.getElementById("visible-count");
      const sizeLabel = document.getElementById("size-label");
      const cutAxis = document.getElementById("cut-axis");
      const cutSlider = document.getElementById("cut-slider");
      const cutValue = document.getElementById("cut-value");
      const cutMax = document.getElementById("cut-max");
      const layerMin = document.getElementById("layer-min");
      const layerMax = document.getElementById("layer-max");
      const layerMinValue = document.getElementById("layer-min-value");
      const layerMaxValue = document.getElementById("layer-max-value");
      const blockList = document.getElementById("block-list");

      if (!window.THREE || typeof THREE.OrbitControls !== "function") {
        throw new Error("three.js or OrbitControls did not load");
      }

      title.textContent = payload.name || "Structure Preview";
      source.textContent = payload.source || "";
      sizeLabel.textContent = payload.size.join(" x ");

      const palette = payload.palette.map((rgb) => new THREE.Color(rgb[0] / 255, rgb[1] / 255, rgb[2] / 255));
      const baseEnabled = new Uint8Array(payload.blockBases.length);
      baseEnabled.fill(1);

      const basePalette = new Array(payload.blockBases.length).fill(0);
      for (let i = 0; i < payload.voxels.length; i += 1) {
        basePalette[payload.blockBase[i]] = payload.voxels[i][3];
      }

      function rgbCss(idx) {
        const rgb = payload.palette[idx] || [180, 80, 200];
        return "rgb(" + rgb[0] + "," + rgb[1] + "," + rgb[2] + ")";
      }

      payload.blockBases.forEach((base, idx) => {
        const row = document.createElement("label");
        row.className = "check-row";
        const input = document.createElement("input");
        input.type = "checkbox";
        input.checked = true;
        input.dataset.index = String(idx);
        const swatch = document.createElement("span");
        swatch.className = "swatch";
        swatch.style.background = rgbCss(basePalette[idx]);
        const name = document.createElement("span");
        name.className = "block-name";
        name.textContent = base;
        input.addEventListener("change", () => {
          baseEnabled[idx] = input.checked ? 1 : 0;
          updateVisibility();
        });
        row.append(input, swatch, name);
        blockList.append(row);
      });

      function setAllBlocks(enabled) {
        baseEnabled.fill(enabled ? 1 : 0);
        blockList.querySelectorAll("input[type='checkbox']").forEach((input) => {
          input.checked = enabled;
        });
        updateVisibility();
      }
      document.getElementById("all-blocks").addEventListener("click", () => setAllBlocks(true));
      document.getElementById("no-blocks").addEventListener("click", () => setAllBlocks(false));

      const scene = new THREE.Scene();
      scene.background = new THREE.Color(0xf6f7f9);

      const camera = new THREE.PerspectiveCamera(45, 1, 0.1, 1000);
      const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: "high-performance" });
      renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
      renderer.outputEncoding = THREE.sRGBEncoding;

      const sx = Math.max(1, payload.size[0]);
      const sy = Math.max(1, payload.size[1]);
      const sz = Math.max(1, payload.size[2]);
      const center = [(sx - 1) / 2, (sy - 1) / 2, (sz - 1) / 2];
      const radius = Math.max(1, Math.sqrt(sx * sx + sy * sy + sz * sz) / 2);
      const fov = camera.fov * Math.PI / 180;
      const distance = radius / Math.sin(fov / 2) * 1.15;
      camera.near = Math.max(0.1, distance / 1000);
      camera.far = Math.max(1000, distance * 5);
      camera.position.set(distance * 0.62, distance * 0.48, distance * 0.62);
      camera.updateProjectionMatrix();

      const controls = new THREE.OrbitControls(camera, renderer.domElement);
      controls.enableDamping = true;
      controls.dampingFactor = 0.08;
      controls.screenSpacePanning = false;
      controls.target.set(0, 0, 0);
      controls.update();

      scene.add(new THREE.HemisphereLight(0xffffff, 0xaeb7c2, 0.7));
      const key = new THREE.DirectionalLight(0xffffff, 0.78);
      key.position.set(1.2, 2.0, 1.3);
      scene.add(key);
      const fill = new THREE.DirectionalLight(0xffffff, 0.32);
      fill.position.set(-1.5, 1.0, -1.0);
      scene.add(fill);

      const gridSize = Math.max(sx, sz) + 2;
      const grid = new THREE.GridHelper(gridSize, Math.min(gridSize, 80), 0x9aa7b8, 0xd9dee6);
      grid.position.y = -center[1] - 0.51;
      scene.add(grid);

      const geometry = new THREE.BoxGeometry(0.98, 0.98, 0.98);
      const material = new THREE.ShaderMaterial({
        toneMapped: false,
        vertexShader: `
          varying vec3 vColor;
          void main() {
            vec3 lightDir = normalize(vec3(0.45, 0.85, 0.65));
            vec3 worldNormal = normalize(mat3(instanceMatrix) * normal);
            float shade = 0.68 + 0.32 * max(dot(worldNormal, lightDir), 0.0);
            vColor = instanceColor * shade;
            vec4 mvPosition = modelViewMatrix * instanceMatrix * vec4(position, 1.0);
            gl_Position = projectionMatrix * mvPosition;
          }
        `,
        fragmentShader: `
          varying vec3 vColor;
          void main() {
            gl_FragColor = vec4(vColor, 1.0);
          }
        `
      });
      const mesh = new THREE.InstancedMesh(geometry, material, Math.max(1, payload.voxels.length));
      mesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
      mesh.instanceColor = new THREE.InstancedBufferAttribute(new Float32Array(Math.max(1, payload.voxels.length) * 3), 3);
      mesh.instanceColor.setUsage(THREE.DynamicDrawUsage);
      scene.add(mesh);

      const textureLoader = new THREE.TextureLoader();
      const overlayGroup = new THREE.Group();
      scene.add(overlayGroup);

      function addOverlay(overlay) {
        const texture = textureLoader.load(overlay.texture);
        texture.encoding = THREE.sRGBEncoding;
        const overlayMaterial = new THREE.MeshBasicMaterial({
          map: texture,
          transparent: true,
          alphaTest: 0.02,
          side: THREE.DoubleSide,
          toneMapped: false
        });
        const overlayGeometry = new THREE.PlaneGeometry(overlay.width, overlay.height);
        const overlayMesh = new THREE.Mesh(overlayGeometry, overlayMaterial);
        const p = overlay.center;
        overlayMesh.position.set(p[0] - center[0], p[1] - center[1], p[2] - center[2]);
        if (overlay.facing === "north") {
          overlayMesh.rotation.y = Math.PI;
        } else if (overlay.facing === "east") {
          overlayMesh.rotation.y = Math.PI / 2;
        } else if (overlay.facing === "west") {
          overlayMesh.rotation.y = -Math.PI / 2;
        }
        overlayGroup.add(overlayMesh);
      }

      (payload.overlays || []).forEach(addOverlay);

      const dummy = new THREE.Object3D();
      window.__previewDebug = { camera: camera, controls: controls, mesh: mesh };

      function updateCutLimits(reset) {
        const max = Math.max(0, payload.size[axisIndex[cutAxis.value]] - 1);
        cutSlider.min = "0";
        cutSlider.max = String(max);
        if (reset || Number(cutSlider.value) > max) {
          cutSlider.value = String(max);
        }
        cutMax.value = String(max);
        cutValue.value = String(cutSlider.value);
      }

      function updateLayerLabels() {
        let lo = Number(layerMin.value);
        let hi = Number(layerMax.value);
        if (lo > hi) {
          if (document.activeElement === layerMin) {
            layerMax.value = String(lo);
            hi = lo;
          } else {
            layerMin.value = String(hi);
            lo = hi;
          }
        }
        layerMinValue.value = String(lo);
        layerMaxValue.value = String(hi);
      }

      function updateVisibility() {
        const axis = axisIndex[cutAxis.value];
        const cut = Number(cutSlider.value);
        const yMin = Number(layerMin.value);
        const yMax = Number(layerMax.value);
        let shown = 0;

        for (let i = 0; i < payload.voxels.length; i += 1) {
          const voxel = payload.voxels[i];
          const y = payload.y[i];
          if (voxel[axis] > cut) continue;
          if (y < yMin || y > yMax) continue;
          if (!baseEnabled[payload.blockBase[i]]) continue;

          dummy.position.set(voxel[0] - center[0], voxel[1] - center[1], voxel[2] - center[2]);
          dummy.updateMatrix();
          mesh.setMatrixAt(shown, dummy.matrix);
          mesh.instanceColor.setXYZ(shown, palette[voxel[3]].r, palette[voxel[3]].g, palette[voxel[3]].b);
          shown += 1;
        }

        mesh.count = shown;
        mesh.instanceMatrix.needsUpdate = true;
        if (mesh.instanceColor) {
          mesh.instanceColor.needsUpdate = true;
        }
        material.needsUpdate = true;
        cutValue.value = String(cut);
        visibleCount.textContent = shown.toLocaleString() + " / " + payload.count.toLocaleString();
      }

      function resize() {
        const width = window.innerWidth;
        const height = window.innerHeight;
        renderer.setSize(width, height, false);
        camera.aspect = width / Math.max(1, height);
        camera.updateProjectionMatrix();
      }

      cutAxis.addEventListener("change", () => {
        updateCutLimits(true);
        updateVisibility();
      });
      cutSlider.addEventListener("input", updateVisibility);
      layerMin.addEventListener("input", () => {
        updateLayerLabels();
        updateVisibility();
      });
      layerMax.addEventListener("input", () => {
        updateLayerLabels();
        updateVisibility();
      });
      window.addEventListener("resize", resize);

      layerMin.min = "0";
      layerMin.max = String(Math.max(0, payload.size[1] - 1));
      layerMin.value = "0";
      layerMax.min = "0";
      layerMax.max = String(Math.max(0, payload.size[1] - 1));
      layerMax.value = String(Math.max(0, payload.size[1] - 1));
      updateCutLimits(true);
      updateLayerLabels();
      resize();
      updateVisibility();

      function animate() {
        requestAnimationFrame(animate);
        controls.update();
        renderer.render(scene, camera);
      }
      animate();
    }());
  </script>
</body>
</html>
"""


def build_viewer_payload(size: List[int], voxels: Voxels, overlays: list[dict]) -> Dict[str, object]:
    non_air = [((x, y, z), state) for (x, y, z), state in voxels.items() if not is_air_state(state)]
    non_air.sort(key=lambda item: (item[0][1], item[0][2], item[0][0]))

    block_bases = sorted({state_base(state) for _pos, state in non_air})
    base_index = {base: i for i, base in enumerate(block_bases)}
    palette: List[List[int]] = []
    palette_index: Dict[Tuple[int, int, int], int] = {}
    packed_voxels: List[List[int]] = []
    per_voxel_base: List[int] = []
    per_voxel_y: List[int] = []

    for (x, y, z), state in non_air:
        color = resolve_color(state, _COLORS)
        if color not in palette_index:
            palette_index[color] = len(palette)
            palette.append([color[0], color[1], color[2]])
        packed_voxels.append([x, y, z, palette_index[color]])
        per_voxel_base.append(base_index[state_base(state)])
        per_voxel_y.append(y)

    return {
        "size": list(size),
        "count": len(packed_voxels),
        "palette": palette,
        "voxels": packed_voxels,
        "blockBase": per_voxel_base,
        "y": per_voxel_y,
        "blockBases": block_bases,
        "overlays": overlays,
    }


def _web_asset_src(out_dir: str, filename: str) -> str:
    source = os.path.join(WEB_ASSET_DIR, filename)
    if not os.path.exists(source):
        raise FileNotFoundError(f"missing web asset: {source}")
    asset_dir = os.path.join(os.path.dirname(out_dir), "_assets")
    os.makedirs(asset_dir, exist_ok=True)
    target = os.path.join(asset_dir, filename)
    if not os.path.exists(target) or os.path.getmtime(source) > os.path.getmtime(target):
        shutil.copy2(source, target)
    return os.path.relpath(target, out_dir).replace(os.sep, "/")


def render_interactive_html(size: List[int], voxels: Voxels, entities: Entities, out_dir: str,
                            source: str = "", name: str = "") -> str:
    overlays, _flags = build_texture_overlays(voxels, entities, out_dir)
    payload = build_viewer_payload(size, voxels, overlays)
    payload["source"] = source
    payload["name"] = name or "Structure Preview"
    payload_json = json.dumps(payload, separators=(",", ":"))
    html_text = (VIEWER_TEMPLATE
                 .replace("__THREE_SRC__", html.escape(_web_asset_src(out_dir, "three.min.js"), quote=True))
                 .replace("__ORBIT_SRC__", html.escape(_web_asset_src(out_dir, "OrbitControls.js"), quote=True))
                 .replace("__VIEWER_PAYLOAD__", payload_json))
    path = os.path.join(out_dir, "viewer.html")
    with open(path, "w", encoding="utf-8") as f:
        f.write(html_text)
    return path


_CULTIVATION_GROUP_IDS = {"cultivation_town", "cultivation_sect"}
_REVIEW_SUFFIX = "_review"


def _preview_family_prefixes() -> Dict[str, List[str]]:
    """Derive family -> sorted prefix list (longest first) from buildgen.groups.

    Each settlement group contributes its group id (for compound-named files
    like chinese_courtyard_NNN / cultivation_town_NNN) plus every archetype in
    its roster. Cultivation groups feed the cultivation family; all others feed
    the original family. Longer prefixes are tried first so sect_main_hall is
    matched before any shorter overlapping prefix.
    """
    families: Dict[str, List[str]] = {"original": [], "cultivation": []}
    for group_id, group in GROUPS.items():
        family = "cultivation" if group_id in _CULTIVATION_GROUP_IDS else "original"
        bucket = families[family]
        for name in (group_id, *group.archetype_roster):
            if name not in bucket:
                bucket.append(name)
    for family in families:
        families[family].sort(key=len, reverse=True)
    return families


_PREVIEW_FAMILY_PREFIXES: Dict[str, List[str]] = _preview_family_prefixes()


def classify_preview_stem(stem: str) -> Tuple[str, str]:
    """Classify a preview stem into (family, archetype).

    family is one of "original", "cultivation", "other". archetype is the
    matched prefix used as the name-based sub-category. A trailing ``_review``
    or ``_<digits>`` suffix is tolerated: ``_review`` is stripped first, then
    the stem must equal a known prefix or start with ``<prefix>_``. Stems that
    start with ``test_`` or ``town_plan_`` fall back to the "other" family.
    """
    candidate = stem
    if candidate.endswith(_REVIEW_SUFFIX):
        candidate = candidate[: -len(_REVIEW_SUFFIX)]
    if candidate.startswith("test_") or candidate.startswith("town_plan_"):
        return "other", stem
    for family in ("cultivation", "original"):
        for prefix in _PREVIEW_FAMILY_PREFIXES[family]:
            if candidate == prefix or candidate.startswith(prefix + "_"):
                return family, prefix
    return "other", stem


def render_preview_index(out_root: str, results: Sequence[RenderResult]) -> str:
    viewers = [result for result in results if result.viewer_path]
    if len(viewers) <= 1:
        return ""

    first_href = os.path.relpath(viewers[0].viewer_path, out_root).replace(os.sep, "/")

    # Group viewers into family -> archetype -> [results]. Families follow the
    # two settlement-group superfamilies (original vs cultivation) sourced from
    # tools/buildgen/groups.py; the archetype is the matched prefix (group id or
    # archetype roster entry) and is used as the name-based sub-category.
    family_order = ("original", "cultivation", "other")
    family_labels = {
        "original": "原始 / Original",
        "cultivation": "修仙 / Cultivation",
        "other": "其他 / Other",
    }
    tree: Dict[str, Dict[str, List[RenderResult]]] = {f: {} for f in family_order}
    for result in viewers:
        family, archetype = classify_preview_stem(result.stem)
        tree[family].setdefault(archetype, []).append(result)

    family_blocks: List[str] = []
    total_count = 0
    for family in family_order:
        groups = tree[family]
        if not groups:
            continue
        family_count = sum(len(items) for items in groups.values())
        total_count += family_count
        group_blocks: List[str] = []
        for archetype in sorted(groups):
            items = groups[archetype]
            item_buttons: List[str] = []
            for result in items:
                href = os.path.relpath(result.viewer_path, out_root).replace(os.sep, "/")
                label = html.escape(result.stem)
                source = html.escape(os.path.relpath(result.source, REPO_ROOT))
                item_buttons.append(
                    f'<button class="item" type="button" data-src="{html.escape(href, quote=True)}" '
                    f'data-name="{label.lower()}">'
                    f'<span class="name">{label}</span><span class="source">{source}</span></button>'
                )
            group_blocks.append(
                f'<details class="group" data-archetype="{html.escape(archetype, quote=True)}" open>'
                f'<summary><span class="toggle"></span><span class="group-name">{html.escape(archetype)}</span>'
                f'<span class="badge">{len(items)}</span></summary>'
                f'<div class="items">{"".join(item_buttons)}</div></details>'
            )
        family_blocks.append(
            f'<details class="family" data-family="{family}" open>'
            f'<summary><span class="toggle"></span><span class="family-name">{html.escape(family_labels[family])}</span>'
            f'<span class="badge">{family_count}</span></summary>'
            f'<div class="groups">{"".join(group_blocks)}</div></details>'
        )

    html_text = f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>MyVillage Preview Index</title>
  <style>
    :root {{
      color-scheme: dark;
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: #121417;
      color: #eef2f3;
      --accent: #7aa2c7;
      --accent-strong: #9fc0dd;
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      min-height: 100vh;
      display: grid;
      grid-template-columns: minmax(280px, 360px) 1fr;
      background: #121417;
    }}
    aside {{
      border-right: 1px solid #303840;
      background: #181c20;
      overflow: auto;
      padding: 16px;
      display: flex;
      flex-direction: column;
      min-height: 0;
    }}
    h1 {{
      margin: 0 0 6px;
      font-size: 18px;
      font-weight: 650;
      letter-spacing: 0;
    }}
    .count {{
      margin: 0 0 12px;
      color: #aab4bd;
      font-size: 13px;
    }}
    .filter {{
      width: 100%;
      margin: 0 0 12px;
      padding: 7px 9px;
      border: 1px solid #39424b;
      border-radius: 6px;
      background: #20262c;
      color: inherit;
      font: inherit;
    }}
    .filter:focus {{
      outline: none;
      border-color: var(--accent);
    }}
    .tree {{
      display: grid;
      gap: 10px;
      align-content: start;
    }}
    details {{
      min-width: 0;
    }}
    details > summary {{
      list-style: none;
      cursor: pointer;
      user-select: none;
    }}
    details > summary::-webkit-details-marker {{
      display: none;
    }}
    summary {{
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 7px 9px;
      border: 1px solid #39424b;
      border-radius: 6px;
      background: #20262c;
    }}
    summary:hover {{
      border-color: #4d5a66;
    }}
    .toggle {{
      position: relative;
      width: 10px;
      height: 10px;
      flex: 0 0 auto;
      color: #9da8b1;
    }}
    .toggle::before {{
      content: "";
      position: absolute;
      left: 1px;
      top: 4px;
      width: 6px;
      height: 2px;
      background: currentColor;
    }}
    .toggle::after {{
      content: "";
      position: absolute;
      left: 4px;
      top: 1px;
      width: 2px;
      height: 6px;
      background: currentColor;
      transition: transform 0.12s ease;
    }}
    details[open] > summary .toggle::after {{
      transform: rotate(90deg);
      transform-origin: 1px 1px;
    }}
    .family > summary {{
      background: #1d242b;
      border-color: #3b4651;
    }}
    .family-name {{
      font-size: 14px;
      font-weight: 700;
      letter-spacing: 0.2px;
    }}
    .group-name {{
      font-size: 13px;
      font-weight: 600;
      color: #cdd6dd;
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      overflow-wrap: anywhere;
    }}
    .badge {{
      margin-left: auto;
      min-width: 24px;
      padding: 1px 7px;
      border-radius: 999px;
      background: #2c3742;
      color: #c6d0d8;
      font-size: 11px;
      font-weight: 600;
      text-align: center;
      font-variant-numeric: tabular-nums;
    }}
    .family .badge {{
      background: #34465a;
      color: #e6eef5;
    }}
    .groups {{
      display: grid;
      gap: 7px;
      padding: 8px 0 0 10px;
      border-left: 1px dashed #333c45;
      margin: 6px 0 0 4px;
    }}
    .items {{
      display: grid;
      gap: 6px;
      padding: 7px 0 2px 12px;
      border-left: 1px dashed #2a323b;
      margin: 6px 0 2px 6px;
    }}
    .item {{
      width: 100%;
      min-height: 50px;
      border: 1px solid #39424b;
      border-radius: 6px;
      padding: 7px 10px;
      background: #20262c;
      color: inherit;
      text-align: left;
      cursor: pointer;
    }}
    .item:hover,
    .item.active {{
      border-color: var(--accent);
      background: #26313b;
    }}
    .item.active {{
      box-shadow: inset 2px 0 0 var(--accent-strong);
    }}
    .name {{
      display: block;
      font-size: 13px;
      font-weight: 650;
      overflow-wrap: anywhere;
    }}
    .source {{
      display: block;
      margin-top: 3px;
      color: #9da8b1;
      font-size: 11px;
      overflow-wrap: anywhere;
    }}
    .hidden {{
      display: none !important;
    }}
    main {{
      min-width: 0;
      min-height: 100vh;
    }}
    iframe {{
      display: block;
      width: 100%;
      height: 100vh;
      border: 0;
      background: #0d1014;
    }}
    @media (max-width: 860px) {{
      body {{
        grid-template-columns: 1fr;
        grid-template-rows: auto 1fr;
      }}
      aside {{
        max-height: 46vh;
        border-right: 0;
        border-bottom: 1px solid #303840;
      }}
      iframe {{
        height: 54vh;
      }}
    }}
  </style>
</head>
<body>
  <aside>
    <h1>Preview Index</h1>
    <p class="count">{total_count} interactive structure previews across {sum(1 for f in family_order if tree[f])} categories</p>
    <input id="filter" class="filter" type="search" placeholder="Filter by name (e.g. blacksmith, cultivation_house)..." autocomplete="off">
    <div class="tree">
      {"".join(family_blocks)}
    </div>
  </aside>
  <main>
    <iframe id="viewer" src="{html.escape(first_href, quote=True)}" title="Structure preview"></iframe>
  </main>
  <script>
    const frame = document.getElementById("viewer");
    const filter = document.getElementById("filter");
    const items = Array.from(document.querySelectorAll(".item"));
    const families = Array.from(document.querySelectorAll(".family"));
    const groups = Array.from(document.querySelectorAll(".group"));

    function select(item) {{
      items.forEach((candidate) => candidate.classList.toggle("active", candidate === item));
      frame.src = item.dataset.src;
      if (!item.isConnected) return;
      // expand ancestors so the active item stays visible
      let node = item.parentElement;
      while (node && node !== document.body) {{
        if (node.tagName === "DETAILS") node.open = true;
        node = node.parentElement;
      }}
    }}
    items.forEach((item) => item.addEventListener("click", () => select(item)));

    function applyFilter() {{
      const q = filter.value.trim().toLowerCase();
      if (!q) {{
        items.forEach((item) => item.classList.remove("hidden"));
        groups.forEach((g) => g.classList.remove("hidden"));
        families.forEach((f) => f.classList.remove("hidden"));
        return;
      }}
      items.forEach((item) => {{
        item.classList.toggle("hidden", !item.dataset.name.includes(q));
      }});
      groups.forEach((group) => {{
        const anyVisible = group.querySelectorAll(".item:not(.hidden)").length > 0;
        group.classList.toggle("hidden", !anyVisible);
        if (anyVisible) group.open = true;
      }});
      families.forEach((family) => {{
        const anyVisible = family.querySelectorAll(".group:not(.hidden)").length > 0;
        family.classList.toggle("hidden", !anyVisible);
        if (anyVisible) family.open = true;
      }});
    }}
    filter.addEventListener("input", applyFilter);

    const initial = items.find((item) => !item.classList.contains("hidden")) || items[0];
    if (initial) select(initial);
  </script>
</body>
</html>
"""
    path = os.path.join(out_root, "index.html")
    with open(path, "w", encoding="utf-8") as f:
        f.write(html_text)
    return path


def write_info(path: str, source: str, size: List[int], voxels: Voxels,
               entities: Entities, flags: Iterable[str], outputs: Iterable[str]) -> None:
    non_air = sum(1 for s in voxels.values() if not is_air_state(s))
    bases = sorted({state_base(s) for s in voxels.values() if not is_air_state(s)})
    with open(path, "w", encoding="utf-8") as f:
        f.write(f"source: {source}\n")
        f.write(f"size (x,y,z): {size[0]}, {size[1]}, {size[2]}\n")
        f.write(f"voxels total: {len(voxels)}  non-air: {non_air}\n")
        f.write(f"entities: {len(entities)}\n")
        f.write(f"unique block bases: {len(bases)}\n")
        flags = list(flags)
        if flags:
            f.write("flags:\n")
            for flag in flags:
                f.write(f"  {flag}\n")
        f.write("outputs:\n")
        for o in outputs:
            f.write(f"  {os.path.relpath(o, REPO_ROOT)}\n")


_COLORS: Dict[str, Tuple[int, int, int]] = {}


def render_one(path: str, out_root: str, args: argparse.Namespace) -> RenderResult:
    size, voxels, entities = read_voxels(path)
    stem = os.path.splitext(os.path.basename(path))[0]
    out_dir = os.path.join(out_root, stem)
    if not voxels:
        print(f"SKIP empty structure: {path}", file=sys.stderr)
        return RenderResult(1, path, stem, out_dir, "")
    os.makedirs(out_dir, exist_ok=True)
    outputs: List[str] = []
    _overlays, flags = build_texture_overlays(voxels, entities, out_dir)
    viewer_path = ""
    if not args.iso_only and not args.viewer_only:
        outputs += render_slices(size, voxels, out_dir, args.scale, args.contact_only, args.max_px)
    if not args.slices_only and not args.viewer_only:
        outputs.append(render_isometric(size, voxels, out_dir, args.iso_scale, not args.no_cull, args.max_px))
    if not args.viewer_only:
        legend_png, legend_txt = render_legend(voxels, out_dir)
        outputs += [legend_png, legend_txt]
    if not args.no_viewer and not args.iso_only and not args.slices_only:
        viewer_path = render_interactive_html(size, voxels, entities, out_dir, os.path.relpath(path, REPO_ROOT), stem)
        outputs.append(viewer_path)
    info_path = os.path.join(out_dir, "info.txt")
    write_info(info_path, os.path.relpath(path, REPO_ROOT), size, voxels, entities, flags, outputs)
    outputs.append(info_path)
    print(f"rendered {os.path.relpath(path, REPO_ROOT)} -> {os.path.relpath(out_dir, REPO_ROOT)}/ "
          f"({size[0]}x{size[1]}x{size[2]}, {len(voxels)} voxels)")
    return RenderResult(0, path, stem, out_dir, viewer_path)


def iter_inputs(args: argparse.Namespace) -> List[str]:
    if args.all:
        src = args.src or os.path.join(REPO_ROOT, "src", "main", "resources", "data", "myvillage", "structure")
        if not os.path.isdir(src):
            raise SystemExit(f"--src directory not found: {src}")
        files = sorted(os.path.join(src, f) for f in os.listdir(src) if f.endswith(".nbt"))
        if not files:
            raise SystemExit(f"no .nbt files in {src}")
        return files
    if not args.input:
        raise SystemExit("provide an input file or use --all")
    if not os.path.exists(args.input):
        raise SystemExit(f"input not found: {args.input}")
    return [args.input]


def main() -> int:
    parser = argparse.ArgumentParser(description="Render Minecraft structures to offline previews.")
    parser.add_argument("input", nargs="?", help="path to a .nbt or structure .json file")
    parser.add_argument("--all", action="store_true", help="render every .nbt under --src")
    parser.add_argument("--src", default=None, help="directory scanned when --all is set")
    parser.add_argument("--out", default=DEFAULT_OUT_ROOT, help=f"output root (default {DEFAULT_OUT_ROOT})")
    parser.add_argument("--scale", type=int, default=16, help="pixels per block for slice images (default 16)")
    parser.add_argument("--iso-scale", type=int, default=10, help="isometric tile half-width in pixels (default 10)")
    parser.add_argument("--contact-only", action="store_true", help="skip per-Y slice files, keep only contact sheet")
    parser.add_argument("--slices-only", action="store_true", help="render only slice images")
    parser.add_argument("--iso-only", action="store_true", help="render only the isometric overview")
    parser.add_argument("--no-viewer", action="store_true", help="skip the interactive viewer.html output")
    parser.add_argument("--viewer-only", action="store_true", help="render only the interactive viewer.html output")
    parser.add_argument("--no-cull", action="store_true", help="disable interior-voxel culling in iso view")
    parser.add_argument("--max-px", type=int, default=2048,
                        help="cap the largest output image dimension by auto-reducing scale (default 2048)")
    args = parser.parse_args()

    only_modes = [args.slices_only, args.iso_only, args.viewer_only]
    if sum(1 for enabled in only_modes if enabled) > 1:
        raise SystemExit("choose only one of --slices-only, --iso-only, or --viewer-only")
    if args.viewer_only and args.no_viewer:
        raise SystemExit("--viewer-only cannot be combined with --no-viewer")

    global _COLORS
    _COLORS = load_block_colors()

    out_root = args.out
    os.makedirs(out_root, exist_ok=True)

    inputs = iter_inputs(args)
    rc = 0
    results: List[RenderResult] = []
    for path in inputs:
        try:
            result = render_one(path, out_root, args)
            rc |= result.status
            results.append(result)
        except Exception as exc:
            rc = 1
            print(f"FAIL {path}: {exc}", file=sys.stderr)
    index_path = render_preview_index(out_root, results)
    if index_path:
        print(f"preview index: {os.path.relpath(index_path, REPO_ROOT)}")
        print(f"serve preview: python3 -m http.server 8765 --bind 0.0.0.0 --directory {os.path.relpath(out_root, REPO_ROOT)}")
    print(f"done: {len(inputs)} structure(s) -> {os.path.relpath(out_root, REPO_ROOT)}/")
    return rc


if __name__ == "__main__":
    raise SystemExit(main())
