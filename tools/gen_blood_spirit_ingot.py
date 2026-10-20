#!/usr/bin/env python3
"""Generate the blood spirit ingot: one 32x32 inventory sprite and its item model.

The ingot keeps the three-face silhouette of the existing forging ingots so it
reads as the same material family, but uses a near-black maroon body, a cold
pink edge light and a single glowing blood channel with a drip. There is no
skull or bone motif: the evil-cultivation read comes from the colour, the
fractured channel and the almost-black metal.

Usage: python3 tools/gen_blood_spirit_ingot.py [--check] [--preview DIR]
  (no flag)  write the PNG and item model.
  --check    exit non-zero if either file differs from what would be generated.
  --preview  also write DIR/blood_spirit_ingot_preview.png: the sprite at 1x,
             4x and 10x on the vanilla inventory slot grey, for review.

Standard library only (zlib + struct), as the other item generators.
"""
from __future__ import annotations

import argparse
import json
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEXTURE = ROOT / "src/main/resources/assets/myvillage/textures/item/blood_spirit_ingot.png"
MODEL = ROOT / "src/main/resources/assets/myvillage/models/item/blood_spirit_ingot.json"
DEFAULT_PREVIEW_DIR = ROOT / "out/preview/blood_spirit_ingot"
SIZE = 32

# Deep burgundy metal with cold mauve speculars and a crimson channel. The body stays
# darker and less saturated than copper; the cool highlights keep it reading as metal.
SIDE = (30, 10, 18)
FRONT = (66, 16, 28)
TOP = (100, 24, 38)
TOP_INNER = (86, 20, 32)
EDGE = (168, 68, 82)
EDGE_PALE = (196, 126, 138)
TOP_EDGE_DARK = (70, 18, 32)
GROOVE = (24, 6, 16)
BLOOD_DEEP = (112, 14, 38)
BLOOD = (198, 28, 54)
BLOOD_HOT = (255, 96, 104)
OUTLINE = (17, 9, 13)


def chunk(kind: bytes, data: bytes) -> bytes:
    body = kind + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def encode_png(rows: list[list[tuple[int, int, int, int]]], scale: int = 1) -> bytes:
    raw = bytearray()
    width = len(rows[0]) * scale
    for row in rows:
        line = bytearray()
        for pixel in row:
            line += bytes(pixel) * scale
        for _ in range(scale):
            raw.append(0)
            raw.extend(line)
    header = struct.pack(">IIBBBBB", width, len(rows) * scale, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))


def put(rows, x: int, y: int, color: tuple[int, int, int], alpha: int = 255) -> None:
    if 0 <= x < SIZE and 0 <= y < SIZE:
        rows[y][x] = (*color, alpha)


def rect(rows, x0: int, y0: int, x1: int, y1: int, color) -> None:
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(rows, x, y, color)


def line(rows, x0: int, y0: int, x1: int, y1: int, color, width: int = 1) -> None:
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(steps + 1):
        x = round(x0 + (x1 - x0) * i / steps)
        y = round(y0 + (y1 - y0) * i / steps)
        rect(rows, x, y, x + width - 1, y + width - 1, color)


def poly(rows, points: list[tuple[int, int]], color) -> None:
    """Scanline-fill a closed polygon, half-open rows to avoid double edges."""
    ys = [p[1] for p in points]
    for y in range(min(ys), max(ys) + 1):
        nodes = []
        for i, (x1, y1) in enumerate(points):
            x2, y2 = points[(i + 1) % len(points)]
            if y1 == y2:
                continue
            if y1 <= y < y2 or y2 <= y < y1:
                nodes.append(x1 + (y - y1) * (x2 - x1) / (y2 - y1))
        nodes.sort()
        for i in range(0, len(nodes) - 1, 2):
            for x in range(round(nodes[i]), round(nodes[i + 1]) + 1):
                put(rows, x, y, color)


def outline(rows, color=OUTLINE) -> None:
    src = [row[:] for row in rows]
    for y in range(SIZE):
        for x in range(SIZE):
            if src[y][x][3]:
                continue
            if any(0 <= x + dx < SIZE and 0 <= y + dy < SIZE and src[y + dy][x + dx][3]
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                put(rows, x, y, color)


def icon() -> list[list[tuple[int, int, int, int]]]:
    rows = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]

    # Exact three-face pose of the copper ingot: family silhouette, no extra bulk.
    side = [(3, 17), (9, 10), (24, 8), (29, 14), (23, 23), (7, 26), (3, 22)]
    front = [(3, 17), (23, 15), (29, 14), (25, 22), (7, 26), (3, 22)]
    top = [(3, 17), (9, 10), (24, 8), (29, 14), (23, 18), (7, 22)]
    poly(rows, side, SIDE)
    poly(rows, front, FRONT)
    poly(rows, top, TOP)

    # Clean top plane: lit back ridge, one inner band, shadow at the front lip.
    line(rows, 9, 11, 23, 9, TOP_INNER)
    line(rows, 7, 20, 22, 16, TOP_EDGE_DARK)
    line(rows, 9, 10, 24, 8, EDGE_PALE)
    line(rows, 24, 8, 29, 14, EDGE)
    line(rows, 3, 17, 9, 10, EDGE)
    line(rows, 8, 12, 10, 14, (126, 44, 58))

    # Front face: lighter lip under the top edge, then a darker lower plane.
    line(rows, 4, 18, 22, 15, (92, 26, 42))
    line(rows, 5, 19, 21, 17, (72, 18, 32))
    line(rows, 8, 24, 23, 21, (52, 12, 24))
    line(rows, 8, 25, 23, 22, (38, 10, 20))

    # One restrained blood channel, left of centre: dark fracture with a hot core.
    line(rows, 7, 16, 11, 14, GROOVE, 2)
    line(rows, 11, 14, 15, 15, GROOVE, 2)
    line(rows, 15, 15, 19, 13, GROOVE, 2)
    line(rows, 19, 13, 23, 11, GROOVE, 2)
    line(rows, 8, 15, 11, 14, BLOOD_DEEP)
    line(rows, 11, 14, 15, 15, BLOOD_DEEP)
    line(rows, 15, 15, 19, 13, BLOOD_DEEP)
    line(rows, 19, 13, 22, 12, BLOOD_DEEP)
    line(rows, 9, 15, 11, 14, BLOOD)
    line(rows, 16, 15, 19, 13, BLOOD)
    line(rows, 20, 13, 22, 12, BLOOD)
    put(rows, 10, 14, BLOOD_HOT)
    put(rows, 13, 14, BLOOD_HOT)
    put(rows, 17, 14, BLOOD_HOT)
    put(rows, 21, 12, BLOOD)

    # Warm spill around the channel: a few maroon pixels, not a full red body.
    put(rows, 9, 13, (78, 34, 56))
    put(rows, 12, 13, (78, 34, 56))
    put(rows, 18, 12, (78, 34, 56))
    put(rows, 22, 13, (78, 34, 56))

    # A short drip over the front lip, ending in one bead.
    line(rows, 21, 14, 21, 16, GROOVE)
    line(rows, 22, 13, 22, 16, BLOOD_DEEP)
    put(rows, 21, 15, BLOOD)
    rect(rows, 20, 17, 22, 18, BLOOD_DEEP)
    put(rows, 21, 17, BLOOD)
    put(rows, 21, 18, BLOOD_HOT)

    # Sparse secondary veins, one pixel wide so the silhouette stays clean.
    line(rows, 7, 22, 11, 21, (84, 24, 42))
    line(rows, 18, 20, 22, 19, (84, 24, 42))

    # Cold edge light, kept thin; broad pale bands made the first pass read as stone.
    line(rows, 3, 17, 3, 22, (140, 58, 74))
    line(rows, 3, 22, 7, 26, (96, 34, 50))
    line(rows, 7, 26, 24, 22, (58, 16, 30))
    put(rows, 9, 10, (210, 168, 180))
    put(rows, 10, 12, (176, 112, 126))
    put(rows, 22, 9, (150, 86, 102))
    put(rows, 27, 21, (120, 54, 70))
    put(rows, 4, 20, (128, 62, 78))

    outline(rows)
    return rows


def model() -> bytes:
    return (json.dumps({"parent": "minecraft:item/generated",
                        "textures": {"layer0": "myvillage:item/blood_spirit_ingot"}},
                       ensure_ascii=False, indent=2) + "\n").encode()


def inventory_downsample(rows) -> list[list[tuple[int, int, int, int]]]:
    """32px source to the 16px GUI read the inventory actually shows."""
    out = []
    for y in range(0, SIZE, 2):
        line = []
        for x in range(0, SIZE, 2):
            block = [rows[y + dy][x + dx] for dy in range(2) for dx in range(2)]
            solid = [p for p in block if p[3]]
            if not solid:
                line.append((0, 0, 0, 0))
                continue
            line.append(tuple(sum(p[i] for p in solid) // len(solid) for i in range(3)) + (255,))
        out.append(line)
    return out


def preview_sheet(rows) -> list[list[tuple[int, int, int, int]]]:
    """Downsampled slot read, 4x and 10x nearest-neighbour enlargements."""
    slot = (139, 139, 139)
    background = (50, 50, 50)
    width, height = 700, 380
    sheet = [[(*background, 255)] * width for _ in range(height)]

    # Vanilla inventory read: the 32px source averaged down to a 16px slot.
    small = inventory_downsample(rows)
    ox, oy, scale = 28, 34, 6
    size = 16 * scale
    for y in range(size):
        for x in range(size):
            sheet[oy + y][ox + x] = (*slot, 255)
    for y in range(16):
        for x in range(16):
            p = small[y][x]
            if not p[3]:
                continue
            for dy in range(scale):
                for dx in range(scale):
                    sheet[oy + y * scale + dy][ox + x * scale + dx] = p

    # 4x and 10x source-pixel enlargements.
    for index, scale in enumerate((4, 10)):
        ox = 150 + index * 170
        oy = 30
        size = SIZE * scale
        for y in range(size):
            for x in range(size):
                sheet[oy + y][ox + x] = (*slot, 255)
        for y in range(SIZE):
            for x in range(SIZE):
                p = rows[y][x]
                if not p[3]:
                    continue
                for dy in range(scale):
                    for dx in range(scale):
                        sheet[oy + y * scale + dy][ox + x * scale + dx] = p
    return sheet


def outputs() -> dict[Path, bytes]:
    return {
        TEXTURE: encode_png(icon()),
        MODEL: model(),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--preview", type=Path, nargs="?", const=DEFAULT_PREVIEW_DIR)
    args = parser.parse_args(argv)
    files = outputs()
    if args.preview:
        args.preview.mkdir(parents=True, exist_ok=True)
        target = args.preview / "blood_spirit_ingot_preview.png"
        target.write_bytes(encode_png(preview_sheet(icon()), scale=2))
        print(f"wrote {target.relative_to(ROOT)}")
    if args.check:
        stale = [path for path, data in files.items() if not path.is_file() or path.read_bytes() != data]
        for path in stale:
            print(f"{path.relative_to(ROOT)} is out of date; run tools/gen_blood_spirit_ingot.py", file=sys.stderr)
        return 1 if stale else 0
    for path, data in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.is_file() or path.read_bytes() != data:
            path.write_bytes(data)
            print(f"wrote {path.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
