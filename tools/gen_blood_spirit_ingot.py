#!/usr/bin/env python3
"""Generate the blood spirit ingot: one 32x32 inventory sprite and its item model.

The ingot uses the canonical flat-ingot silhouette from tools/ingot_shape.py so
every 锭 shares the same pose, but uses a near-black maroon body, a cold pink
edge light and a single glowing blood channel with a drip. There is no
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
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
from tools.gen_alchemy_assets import Canvas
from tools.ingot_shape import FLAT_FRONT, FLAT_SIDE, FLAT_TOP

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


def icon() -> list[list[tuple[int, int, int, int]]]:
    c = Canvas()

    # Canonical flat-ingot silhouette, shared with every other 锭 item.
    side, front, top = FLAT_SIDE, FLAT_FRONT, FLAT_TOP
    c.poly(side, SIDE)
    c.poly(front, FRONT)
    c.poly(top, TOP)

    # Clean top plane: lit back ridge, one inner band, shadow at the front lip.
    c.line(9, 11, 23, 9, TOP_INNER)
    c.line(7, 20, 22, 16, TOP_EDGE_DARK)
    c.line(9, 10, 24, 8, EDGE_PALE)
    c.line(24, 8, 29, 14, EDGE)
    c.line(3, 17, 9, 10, EDGE)
    c.line(8, 12, 10, 14, (126, 44, 58))

    # Front face: lighter lip under the top edge, then a darker lower plane.
    c.line(4, 18, 22, 15, (92, 26, 42))
    c.line(5, 19, 21, 17, (72, 18, 32))
    c.line(8, 24, 23, 21, (52, 12, 24))
    c.line(8, 25, 23, 22, (38, 10, 20))

    # One restrained blood channel, left of centre: dark fracture with a hot core.
    c.line(7, 16, 11, 14, GROOVE, 2)
    c.line(11, 14, 15, 15, GROOVE, 2)
    c.line(15, 15, 19, 13, GROOVE, 2)
    c.line(19, 13, 23, 11, GROOVE, 2)
    c.line(8, 15, 11, 14, BLOOD_DEEP)
    c.line(11, 14, 15, 15, BLOOD_DEEP)
    c.line(15, 15, 19, 13, BLOOD_DEEP)
    c.line(19, 13, 22, 12, BLOOD_DEEP)
    c.line(9, 15, 11, 14, BLOOD)
    c.line(16, 15, 19, 13, BLOOD)
    c.line(20, 13, 22, 12, BLOOD)
    c.put(10, 14, BLOOD_HOT)
    c.put(13, 14, BLOOD_HOT)
    c.put(17, 14, BLOOD_HOT)
    c.put(21, 12, BLOOD)

    # Warm spill around the channel: a few maroon pixels, not a full red body.
    c.put(9, 13, (78, 34, 56))
    c.put(12, 13, (78, 34, 56))
    c.put(18, 12, (78, 34, 56))
    c.put(22, 13, (78, 34, 56))

    # A short drip over the front lip, ending in one bead.
    c.line(21, 14, 21, 16, GROOVE)
    c.line(22, 13, 22, 16, BLOOD_DEEP)
    c.put(21, 15, BLOOD)
    c.rect(20, 17, 22, 18, BLOOD_DEEP)
    c.put(21, 17, BLOOD)
    c.put(21, 18, BLOOD_HOT)

    # Sparse secondary veins, one pixel wide so the silhouette stays clean.
    c.line(7, 22, 11, 21, (84, 24, 42))
    c.line(18, 20, 22, 19, (84, 24, 42))

    # Cold edge light, kept thin; broad pale bands made the first pass read as stone.
    c.line(3, 17, 3, 22, (140, 58, 74))
    c.line(3, 22, 7, 26, (96, 34, 50))
    c.line(7, 26, 24, 22, (58, 16, 30))
    c.put(9, 10, (210, 168, 180))
    c.put(10, 12, (176, 112, 126))
    c.put(22, 9, (150, 86, 102))
    c.put(27, 21, (120, 54, 70))
    c.put(4, 20, (128, 62, 78))

    c.outline(OUTLINE)
    return c.rows


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
