#!/usr/bin/env python3
"""Generate plaque blockstates, models, frame PNGs, and artist checklists."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import struct
import sys
import zlib
from pathlib import Path
from typing import Iterable

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import plaque_bindings  # noqa: E402

DEFAULT_MANIFEST = SCRIPT_DIR / "plaque_frames.json"
ASSET_ROOT = REPO_ROOT / "src" / "main" / "resources" / "assets" / "myvillage"
DEFAULT_ASSET_LIST = REPO_ROOT / "reports" / "plaque_frame_assets.json"
DEFAULT_CHECKLIST = REPO_ROOT / "docs" / "ai-kb" / "plaque-frame-asset-checklist.txt"

FACING_ROTATION = {"north": 0, "east": 90, "south": 180, "west": 270}
HORIZONTAL_BLOCKS = {"wall": "wall_plaque", "hanging": "hanging_plaque"}
VERTICAL_BLOCKS = {"wall": "wall_plaque_vertical", "hanging": "hanging_plaque_vertical"}
ROW_PREFIXES = ("upper_middle", "lower_middle", "single", "middle", "top", "bottom")


def read_manifest(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)
    presets = data.get("presets", [])
    if len(presets) != 8:
        raise ValueError(f"expected exactly 8 plaque presets, got {len(presets)}")
    seen = set()
    for preset in presets:
        pid = preset.get("id")
        if not pid or pid in seen:
            raise ValueError(f"duplicate or missing preset id: {pid!r}")
        seen.add(pid)
        if not preset.get("horizontal_size"):
            raise ValueError(f"{pid}: missing horizontal_size")
        if not preset.get("interior_bucket"):
            raise ValueError(f"{pid}: missing interior_bucket")
    return data


def png_bytes(width: int, height: int, rgba: bytes) -> bytes:
    def chunk(tag: bytes, payload: bytes) -> bytes:
        return (
            struct.pack(">I", len(payload))
            + tag
            + payload
            + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)
        )

    raw = bytearray()
    row_len = width * 4
    for y in range(height):
        raw.append(0)
        raw.extend(rgba[y * row_len:(y + 1) * row_len])
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )


def _paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa = abs(p - a)
    pb = abs(p - b)
    pc = abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def read_png_rgba(path: Path) -> tuple[int, int, bytes]:
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError(f"{path}: not a PNG")
    offset = 8
    width = height = None
    idat = bytearray()
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        ctype = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        offset += 12 + length
        if ctype == b"IHDR":
            width, height, bit_depth, color_type, _compression, _filter, interlace = struct.unpack(">IIBBBBB", payload)
            if bit_depth != 8 or color_type != 6 or interlace != 0:
                raise ValueError(f"{path}: expected non-interlaced 8-bit RGBA PNG")
        elif ctype == b"IDAT":
            idat.extend(payload)
        elif ctype == b"IEND":
            break
    if width is None or height is None:
        raise ValueError(f"{path}: missing IHDR")

    raw = zlib.decompress(bytes(idat))
    stride = width * 4
    out = bytearray()
    prev = bytearray(stride)
    cursor = 0
    for _y in range(height):
        ftype = raw[cursor]
        cursor += 1
        scan = bytearray(raw[cursor:cursor + stride])
        cursor += stride
        for i, value in enumerate(scan):
            left = scan[i - 4] if i >= 4 else 0
            up = prev[i]
            up_left = prev[i - 4] if i >= 4 else 0
            if ftype == 1:
                scan[i] = (value + left) & 0xFF
            elif ftype == 2:
                scan[i] = (value + up) & 0xFF
            elif ftype == 3:
                scan[i] = (value + ((left + up) // 2)) & 0xFF
            elif ftype == 4:
                scan[i] = (value + _paeth(left, up, up_left)) & 0xFF
            elif ftype != 0:
                raise ValueError(f"{path}: unsupported PNG filter {ftype}")
        out.extend(scan)
        prev = scan
    return width, height, bytes(out)


def _luminance(color: tuple[int, int, int]) -> float:
    return color[0] * 0.2126 + color[1] * 0.7152 + color[2] * 0.0722


def _part_row_col(part: str) -> tuple[str, str]:
    for row in ROW_PREFIXES:
        if part == row:
            return row, "single"
        prefix = f"{row}_"
        if part.startswith(prefix):
            return row, part[len(prefix):]
    raise ValueError(f"unsupported plaque texture part: {part}")


def _interior_color(preset: dict) -> tuple[int, int, int, int]:
    pal = preset["palette"]
    return tuple(pal["base"]) + (255,)


def _inscription_tint(preset: dict) -> tuple[int, int, int]:
    pal = preset["palette"]
    surface = _interior_color(preset)[:3]
    if _luminance(surface) >= 112:
        return (18, 16, 13)

    candidates = [tuple(pal[name]) for name in ("accent", "trim", "base")]
    bright = max(candidates, key=_luminance)
    if _luminance(bright) - _luminance(surface) < 86:
        return (224, 190, 96)
    return bright


def put(buf: bytearray, x: int, y: int, color: tuple[int, int, int, int]) -> None:
    if not (0 <= x < 16 and 0 <= y < 16):
        return
    i = (y * 16 + x) * 4
    buf[i:i + 4] = bytes(color)


def draw_line(buf: bytearray, points: Iterable[tuple[int, int]], color: tuple[int, int, int, int]) -> None:
    for x, y in points:
        put(buf, x, y, color)


def draw_texture_rgba(preset: dict, mount: str, part: str) -> bytes:
    pal = preset["palette"]
    base = tuple(pal["base"]) + (255,)
    trim = tuple(pal["trim"]) + (255,)
    accent = tuple(pal["accent"]) + (255,)
    shadow = tuple(pal["shadow"]) + (255,)
    surface = _interior_color(preset)
    buf = bytearray(surface * (16 * 16))

    row, col = _part_row_col(part)
    left = col in {"left", "single"}
    right = col in {"right", "single"}
    top = row in {"top", "single"}
    bottom = row in {"bottom", "single"}

    if left:
        for x in (0, 1, 2):
            draw_line(buf, ((x, y) for y in range(16)), shadow if x == 0 else base)
    if right:
        for x in (13, 14, 15):
            draw_line(buf, ((x, y) for y in range(16)), shadow if x == 15 else base)
    if top:
        for y in (0, 1, 2):
            draw_line(buf, ((x, y) for x in range(16)), shadow if y == 0 else trim)
    if bottom:
        for y in (13, 14, 15):
            draw_line(buf, ((x, y) for x in range(16)), shadow if y == 15 else base)

    # Inner lip.
    if left:
        draw_line(buf, ((3, y) for y in range(3 if top else 0, 13 if bottom else 16)), trim)
    if right:
        draw_line(buf, ((12, y) for y in range(3 if top else 0, 13 if bottom else 16)), trim)
    if top:
        draw_line(buf, ((x, 3) for x in range(3 if left else 0, 13 if right else 16)), accent)
    if bottom:
        draw_line(buf, ((x, 12) for x in range(3 if left else 0, 13 if right else 16)), shadow)

    # Corner ornament.
    for cx, cy in ((3, 3), (12, 3), (3, 12), (12, 12)):
        if ((cx == 3 and left) or (cx == 12 and right)) and ((cy == 3 and top) or (cy == 12 and bottom)):
            for dx, dy in ((0, 0), (1 if cx == 3 else -1, 0), (0, 1 if cy == 3 else -1)):
                put(buf, cx + dx, cy + dy, accent)

    # Register-specific motif ticks.
    if preset.get("tier") == "sect":
        for x in (5, 10):
            if top:
                put(buf, x, 2, accent)
                put(buf, x + 1, 3, accent)
            if bottom:
                put(buf, x, 13, accent)
                put(buf, x + 1, 12, accent)
    elif preset.get("tier") == "civic":
        if left:
            draw_line(buf, ((2, y) for y in (5, 6, 9, 10)), accent)
        if right:
            draw_line(buf, ((13, y) for y in (5, 6, 9, 10)), accent)

    if mount == "hanging" and top:
        ring_columns = []
        if left:
            ring_columns.append(5)
        if right:
            ring_columns.append(10)
        if "5w" in preset["id"] and col in {"center", "single"}:
            ring_columns.append(8)
        for rx in ring_columns:
            for x, y in ((rx, 0), (rx - 1, 1), (rx + 1, 1), (rx, 2)):
                put(buf, x, y, accent)

    return bytes(buf)


def _blend_pixel(dst: bytearray, x: int, y: int, rgba: tuple[int, int, int, int]) -> None:
    _blend_pixel_at(dst, 16, x, y, rgba)


def _blend_pixel_at(dst: bytearray, dst_w: int, x: int, y: int, rgba: tuple[int, int, int, int]) -> None:
    if rgba[3] <= 0:
        return
    i = (y * dst_w + x) * 4
    sr, sg, sb, sa = rgba
    dr, dg, db, da = dst[i:i + 4]
    src_a = sa / 255.0
    dst_a = da / 255.0
    out_a = src_a + dst_a * (1.0 - src_a)
    if out_a <= 0.0:
        dst[i:i + 4] = b"\x00\x00\x00\x00"
        return
    out_r = int(round((sr * src_a + dr * dst_a * (1.0 - src_a)) / out_a))
    out_g = int(round((sg * src_a + dg * dst_a * (1.0 - src_a)) / out_a))
    out_b = int(round((sb * src_a + db * dst_a * (1.0 - src_a)) / out_a))
    dst[i:i + 4] = bytes((out_r, out_g, out_b, int(round(out_a * 255.0))))


def _inscription_bounds(src: bytes, src_w: int, src_h: int) -> tuple[int, int, int, int] | None:
    min_x = src_w
    min_y = src_h
    max_x = -1
    max_y = -1
    for y in range(src_h):
        row = y * src_w * 4
        for x in range(src_w):
            if src[row + x * 4 + 3] > 8:
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)
    if max_x < min_x or max_y < min_y:
        return None
    return min_x, min_y, max_x + 1, max_y + 1


def _target_rect(width: int, height: int, src_bounds: tuple[int, int, int, int]) -> tuple[float, float, float, float]:
    full_w = width * 16
    full_h = height * 16
    margin_x = 2 if width == 1 else 5
    margin_y = 5 if width == 1 and height > 1 else (2 if height == 1 else 4)
    inner_w = max(1, full_w - margin_x * 2)
    inner_h = max(1, full_h - margin_y * 2)
    src_w = src_bounds[2] - src_bounds[0]
    src_h = src_bounds[3] - src_bounds[1]
    scale = min(inner_w / src_w, inner_h / src_h)
    draw_w = max(1.0, src_w * scale)
    draw_h = max(1.0, src_h * scale)
    return (
        margin_x + (inner_w - draw_w) / 2.0,
        margin_y + (inner_h - draw_h) / 2.0,
        draw_w,
        draw_h,
    )


def _sample_inscription_pixel(src: bytes, src_w: int, src_h: int,
                              bounds: tuple[int, int, int, int],
                              target: tuple[float, float, float, float],
                              gx: int, gy: int,
                              tint: tuple[int, int, int]) -> tuple[int, int, int, int]:
    tx, ty, tw, th = target
    if gx + 1 <= tx or gx >= tx + tw or gy + 1 <= ty or gy >= ty + th:
        return (0, 0, 0, 0)

    bx0, by0, bx1, by1 = bounds
    sx0 = bx0 + ((max(gx, tx) - tx) / tw) * (bx1 - bx0)
    sx1 = bx0 + ((min(gx + 1, tx + tw) - tx) / tw) * (bx1 - bx0)
    sy0 = by0 + ((max(gy, ty) - ty) / th) * (by1 - by0)
    sy1 = by0 + ((min(gy + 1, ty + th) - ty) / th) * (by1 - by0)
    ix0 = max(0, min(src_w - 1, int(sx0)))
    ix1 = max(ix0 + 1, min(src_w, int(sx1 + 0.999)))
    iy0 = max(0, min(src_h - 1, int(sy0)))
    iy1 = max(iy0 + 1, min(src_h, int(sy1 + 0.999)))

    total_a = 0
    max_a = 0
    count = 0
    for sy in range(iy0, iy1):
        row = sy * src_w * 4
        for sx in range(ix0, ix1):
            a = src[row + sx * 4 + 3]
            total_a += a
            max_a = max(max_a, a)
            count += 1
    if count <= 0 or max_a <= 0:
        return (0, 0, 0, 0)

    avg_a = total_a / count
    alpha = int(round(max(avg_a * 1.65, max_a * 0.45)))
    if alpha < 16:
        return (0, 0, 0, 0)
    return tint[0], tint[1], tint[2], min(255, alpha)


def composite_inscription_tile(base: bytes, preset: dict, inscription: tuple[int, int, bytes],
                               width: int, height: int, tile_col: int, tile_row: int) -> bytes:
    src_w, src_h, src = inscription
    bounds = _inscription_bounds(src, src_w, src_h)
    if bounds is None:
        return base
    target = _target_rect(width, height, bounds)
    tint = _inscription_tint(preset)
    out = bytearray(base)
    for y in range(16):
        for x in range(16):
            rgba = _sample_inscription_pixel(src, src_w, src_h, bounds, target, tile_col * 16 + x, tile_row * 16 + y, tint)
            _blend_pixel(out, x, y, rgba)
    return bytes(out)


def _full_texture_scale(width: int, height: int, inscription: tuple[int, int, bytes] | None) -> int:
    if inscription is None:
        return 1
    src_w, src_h, _src = inscription
    px_per_block = max(src_w / max(1, width), src_h / max(1, height))
    return max(1, min(8, int(round(px_per_block / 16.0))))


def _copy_scaled_tile(dst: bytearray, dst_w: int, dst_x: int, dst_y: int,
                      src: bytes, scale: int) -> None:
    for y in range(16 * scale):
        sy = y // scale
        for x in range(16 * scale):
            sx = x // scale
            si = (sy * 16 + sx) * 4
            di = ((dst_y + y) * dst_w + dst_x + x) * 4
            dst[di:di + 4] = src[si:si + 4]


def _source_canvas_target_rect(full_w: int, full_h: int, width: int, height: int,
                               src_w: int, src_h: int, texture_scale: int) -> tuple[float, float, float, float]:
    margin_x = (2 if width == 1 else 5) * texture_scale
    margin_y = (5 if width == 1 and height > 1 else (2 if height == 1 else 4)) * texture_scale
    inner_w = max(1, full_w - margin_x * 2)
    inner_h = max(1, full_h - margin_y * 2)
    scale = min(inner_w / src_w, inner_h / src_h)
    draw_w = max(1.0, src_w * scale)
    draw_h = max(1.0, src_h * scale)
    return (
        margin_x + (inner_w - draw_w) / 2.0,
        margin_y + (inner_h - draw_h) / 2.0,
        draw_w,
        draw_h,
    )


def composite_inscription_full(base: bytes, full_w: int, full_h: int, texture_scale: int,
                               preset: dict, inscription: tuple[int, int, bytes],
                               width: int, height: int) -> bytes:
    src_w, src_h, src = inscription
    target = _source_canvas_target_rect(full_w, full_h, width, height, src_w, src_h, texture_scale)
    tint = _inscription_tint(preset)
    out = bytearray(base)
    bounds = (0, 0, src_w, src_h)
    for y in range(full_h):
        for x in range(full_w):
            rgba = _sample_inscription_pixel(src, src_w, src_h, bounds, target, x, y, tint)
            _blend_pixel_at(out, full_w, x, y, rgba)
    return bytes(out)


def draw_full_plaque_rgba(preset: dict, mount: str, width: int, height: int,
                          orientation: str,
                          inscription: tuple[int, int, bytes] | None = None,
                          texture_scale: int | None = None) -> tuple[int, int, bytes]:
    scale = texture_scale or _full_texture_scale(width, height, inscription)
    full_w = width * 16 * scale
    full_h = height * 16 * scale
    out = bytearray(_interior_color(preset) * (full_w * full_h))
    if orientation == "horizontal":
        rows = row_values(height)
        cols = col_values(width)
        for row_index, row in enumerate(rows):
            for col_index, col in enumerate(cols):
                part = horizontal_part(row, col)
                tile = draw_texture_rgba(preset, mount, part)
                _copy_scaled_tile(out, full_w, col_index * 16 * scale, row_index * 16 * scale, tile, scale)
    elif orientation == "vertical":
        for row_index, row in enumerate(row_values(height)):
            part = vertical_part(row)
            tile = draw_texture_rgba(preset, mount, part)
            _copy_scaled_tile(out, full_w, 0, row_index * 16 * scale, tile, scale)
    else:
        raise ValueError(f"unsupported plaque orientation {orientation!r}")
    rgba = bytes(out)
    if inscription is not None:
        rgba = composite_inscription_full(rgba, full_w, full_h, scale, preset, inscription, width, height)
    return full_w, full_h, rgba


def draw_full_texture(preset: dict, mount: str, width: int, height: int,
                      orientation: str,
                      inscription: tuple[int, int, bytes] | None = None) -> bytes:
    full_w, full_h, rgba = draw_full_plaque_rgba(preset, mount, width, height, orientation, inscription)
    return png_bytes(full_w, full_h, rgba)


def draw_texture(preset: dict, mount: str, part: str,
                 inscription: tuple[int, int, bytes] | None = None,
                 width: int = 1, height: int = 1,
                 tile_col: int = 0, tile_row: int = 0) -> bytes:
    rgba = draw_texture_rgba(preset, mount, part)
    if inscription is not None:
        rgba = composite_inscription_tile(rgba, preset, inscription, width, height, tile_col, tile_row)
    return png_bytes(16, 16, rgba)


def row_values(height: int) -> list[str]:
    if height <= 1:
        return ["single"]
    if height == 2:
        return ["top", "bottom"]
    if height == 3:
        return ["top", "middle", "bottom"]
    if height == 4:
        return ["top", "upper_middle", "lower_middle", "bottom"]
    if height == 5:
        return ["top", "upper_middle", "middle", "lower_middle", "bottom"]
    raise ValueError(f"unsupported plaque height {height}")


def col_values(width: int) -> list[str]:
    if width <= 1:
        return ["single"]
    if width == 2:
        return ["left", "right"]
    if width == 3:
        return ["left", "center", "right"]
    if width == 4:
        return ["left", "inner_left", "inner_right", "right"]
    if width == 5:
        return ["left", "inner_left", "center", "inner_right", "right"]
    raise ValueError(f"unsupported plaque width {width}")


def horizontal_part(row: str, col: str) -> str:
    if row == "single":
        return f"single_{'center' if col == 'single' else col}"
    return f"{row}_{'center' if col == 'single' else col}"


def vertical_part(row: str) -> str:
    return "middle" if row == "single" else row


def inscription_map() -> dict[tuple[str, str, str], tuple[int, int, bytes]]:
    out: dict[tuple[str, str, str], tuple[int, int, bytes]] = {}
    for bindings in plaque_bindings.load_bindings().values():
        for binding in bindings:
            key = (binding.frame, binding.mount, binding.orientation)
            path = plaque_bindings.INSCRIPTION_TEXTURE_ROOT / binding.inscription.bucket / f"{binding.inscription.id}.png"
            loaded = read_png_rgba(path)
            existing = out.get(key)
            if existing is not None and existing != loaded:
                raise ValueError(f"multiple inscriptions for plaque texture key {key}")
            out[key] = loaded
    return out


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def tile_uv(width: int, height: int, tile_col: int, tile_row: int) -> list[float]:
    return [
        round(16.0 * tile_col / width, 4),
        round(16.0 * tile_row / height, 4),
        round(16.0 * (tile_col + 1) / width, 4),
        round(16.0 * (tile_row + 1) / height, 4),
    ]


def model_json(plaque_texture_ref: str, edge_texture_ref: str, mount: str,
               front_uv: list[float]) -> dict:
    depth = [15, 16] if mount == "wall" else [7, 9]
    u0, v0, u1, v1 = front_uv
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": edge_texture_ref,
            "plaque": plaque_texture_ref,
            "edge": edge_texture_ref
        },
        "elements": [
            {
                "from": [0, 0, depth[0]],
                "to": [16, 16, depth[1]],
                "faces": {
                    "north": {"texture": "#plaque", "uv": [u0, v0, u1, v1]},
                    "south": {"texture": "#plaque", "uv": [u1, v0, u0, v1]},
                    "up": {"texture": "#edge", "uv": [0, 0, 16, 1]},
                    "down": {"texture": "#edge", "uv": [0, 15, 16, 16]},
                    "east": {"texture": "#edge", "uv": [15, 0, 16, 16]},
                    "west": {"texture": "#edge", "uv": [0, 0, 1, 16]}
                }
            }
        ]
    }


def variant_entry(model: str, facing: str) -> dict:
    out = {"model": model}
    y = FACING_ROTATION[facing]
    if y:
        out["y"] = y
    if facing in ("east", "west"):
        out["uvlock"] = True
    return out


def generate(manifest: dict, write_pngs: bool = True) -> list[dict]:
    assets: list[dict] = []
    inscriptions = inscription_map()
    blockstate_variants = {name: {} for name in (
        "wall_plaque", "hanging_plaque", "wall_plaque_vertical", "hanging_plaque_vertical")}

    for preset in manifest["presets"]:
        pid = preset["id"]
        width, height = preset["horizontal_size"]
        for mount, block in HORIZONTAL_BLOCKS.items():
            rows = row_values(height)
            cols = col_values(width)
            inscription = inscriptions.get((pid, mount, "horizontal"))
            full_part = "horizontal_full"
            full_texture_path = ASSET_ROOT / "textures" / "block" / "plaque" / mount / pid / f"{full_part}.png"
            full_texture_ref = f"myvillage:block/plaque/{mount}/{pid}/{full_part}"
            if write_pngs:
                full_texture_path.parent.mkdir(parents=True, exist_ok=True)
                full_texture_path.write_bytes(draw_full_texture(
                    preset,
                    mount,
                    width,
                    height,
                    "horizontal",
                    inscription=inscription,
                ))
            assets.append({"preset": pid, "mount": mount, "orientation": "horizontal",
                           "part": full_part, "role": "full_plaque_texture",
                           "texture": str(full_texture_path.relative_to(REPO_ROOT))})
            for row_index, row in enumerate(rows):
                for col_index, col in enumerate(cols):
                    part = horizontal_part(row, col)
                    texture_path = ASSET_ROOT / "textures" / "block" / "plaque" / mount / pid / f"{part}.png"
                    model_path = ASSET_ROOT / "models" / "block" / "plaque" / mount / pid / f"{part}.json"
                    edge_texture_ref = f"myvillage:block/plaque/{mount}/{pid}/{part}"
                    model_ref = f"myvillage:block/plaque/{mount}/{pid}/{part}"
                    if write_pngs:
                        texture_path.parent.mkdir(parents=True, exist_ok=True)
                        texture_path.write_bytes(draw_texture(
                            preset,
                            mount,
                            part,
                        ))
                    write_json(model_path, model_json(
                        full_texture_ref,
                        edge_texture_ref,
                        mount,
                        tile_uv(width, height, col_index, row_index),
                    ))
                    assets.append({"preset": pid, "mount": mount, "orientation": "horizontal",
                                   "part": part, "role": "edge_texture_and_model",
                                   "texture": str(texture_path.relative_to(REPO_ROOT)),
                                   "plaque_texture": str(full_texture_path.relative_to(REPO_ROOT)),
                                   "model": str(model_path.relative_to(REPO_ROOT))})
                    for facing in FACING_ROTATION:
                        key = f"facing={facing},frame={pid},row={row},col={col}"
                        blockstate_variants[block][key] = variant_entry(model_ref, facing)
                if width == 4:
                    # Backward-compatible model for old generated structures
                    # that used the repeated center state for both inner tiles.
                    alias_model = f"myvillage:block/plaque/{mount}/{pid}/{horizontal_part(row, 'inner_left')}"
                    for facing in FACING_ROTATION:
                        key = f"facing={facing},frame={pid},row={row},col=center"
                        blockstate_variants[block][key] = variant_entry(alias_model, facing)

        vertical_size = preset.get("vertical_size")
        if vertical_size:
            _vw, vheight = vertical_size
            for mount, block in VERTICAL_BLOCKS.items():
                rows = row_values(vheight)
                inscription = inscriptions.get((pid, mount, "vertical"))
                full_part = "vertical_full"
                full_texture_path = ASSET_ROOT / "textures" / "block" / "plaque" / mount / pid / f"{full_part}.png"
                full_texture_ref = f"myvillage:block/plaque/{mount}/{pid}/{full_part}"
                if write_pngs:
                    full_texture_path.parent.mkdir(parents=True, exist_ok=True)
                    full_texture_path.write_bytes(draw_full_texture(
                        preset,
                        mount,
                        1,
                        vheight,
                        "vertical",
                        inscription=inscription,
                    ))
                assets.append({"preset": pid, "mount": mount, "orientation": "vertical",
                               "part": full_part, "role": "full_plaque_texture",
                               "texture": str(full_texture_path.relative_to(REPO_ROOT))})
                for row_index, row in enumerate(rows):
                    part = vertical_part(row)
                    texture_path = ASSET_ROOT / "textures" / "block" / "plaque" / mount / pid / f"{part}.png"
                    model_path = ASSET_ROOT / "models" / "block" / "plaque" / mount / pid / f"{part}.json"
                    edge_texture_ref = f"myvillage:block/plaque/{mount}/{pid}/{part}"
                    model_ref = f"myvillage:block/plaque/{mount}/{pid}/{part}"
                    if write_pngs:
                        texture_path.parent.mkdir(parents=True, exist_ok=True)
                        texture_path.write_bytes(draw_texture(
                            preset,
                            mount,
                            part,
                        ))
                    write_json(model_path, model_json(
                        full_texture_ref,
                        edge_texture_ref,
                        mount,
                        tile_uv(1, vheight, 0, row_index),
                    ))
                    assets.append({"preset": pid, "mount": mount, "orientation": "vertical",
                                   "part": part, "role": "edge_texture_and_model",
                                   "texture": str(texture_path.relative_to(REPO_ROOT)),
                                   "plaque_texture": str(full_texture_path.relative_to(REPO_ROOT)),
                                   "model": str(model_path.relative_to(REPO_ROOT))})
                    for facing in FACING_ROTATION:
                        key = f"facing={facing},frame={pid},row={row},col=single"
                        blockstate_variants[block][key] = variant_entry(model_ref, facing)
                if vheight == 4:
                    alias_model = f"myvillage:block/plaque/{mount}/{pid}/lower_middle"
                    for facing in FACING_ROTATION:
                        key = f"facing={facing},frame={pid},row=middle,col=single"
                        blockstate_variants[block][key] = variant_entry(alias_model, facing)

    for block, variants in blockstate_variants.items():
        write_json(ASSET_ROOT / "blockstates" / f"{block}.json", {"variants": variants})
    return assets


def clean_generated_assets(write_pngs: bool) -> None:
    for path in (ASSET_ROOT / "models" / "block" / "plaque",):
        if path.exists():
            shutil.rmtree(path)
    if write_pngs:
        texture_root = ASSET_ROOT / "textures" / "block" / "plaque"
        if texture_root.exists():
            shutil.rmtree(texture_root)


def write_asset_reports(manifest: dict, assets: list[dict], asset_list: Path, checklist: Path) -> None:
    asset_list.parent.mkdir(parents=True, exist_ok=True)
    asset_list.write_text(json.dumps({
        "manifest": str(DEFAULT_MANIFEST.relative_to(REPO_ROOT)),
        "asset_count": len(assets),
        "assets": assets,
    }, indent=2) + "\n", encoding="utf-8")

    lines = [
        "Plaque frame asset checklist",
        "============================",
        "",
        "Frame edge part PNGs are 16x16. Full plaque PNGs may be higher resolution and are mapped across multipart models with UV windows.",
        "Inspect each mount/preset group for readable full-face calligraphy, edge continuity, and hanging hardware.",
        "",
    ]
    for preset in manifest["presets"]:
        lines.append(f"- {preset['id']} ({preset['display_name']})")
        lines.append(f"  material: {preset['material']}")
        lines.append(f"  edge: {preset['edge_profile']}")
        lines.append(f"  corners: {preset['corner_ornament']}")
        lines.append(f"  hanging: {preset['hanging_hardware']}")
    checklist.parent.mkdir(parents=True, exist_ok=True)
    checklist.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", default=str(DEFAULT_MANIFEST))
    parser.add_argument("--asset-list", default=str(DEFAULT_ASSET_LIST))
    parser.add_argument("--checklist", default=str(DEFAULT_CHECKLIST))
    parser.add_argument("--no-png", action="store_true", help="write JSON assets only")
    args = parser.parse_args()

    manifest = read_manifest(Path(args.manifest))
    clean_generated_assets(write_pngs=not args.no_png)
    assets = generate(manifest, write_pngs=not args.no_png)
    write_asset_reports(manifest, assets, Path(args.asset_list), Path(args.checklist))
    print(f"generated plaque frame assets: {len(assets)}")
    print(f"asset list: {Path(args.asset_list).relative_to(REPO_ROOT)}")
    print(f"artist checklist: {Path(args.checklist).relative_to(REPO_ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
