#!/usr/bin/env python3
"""Generate the technique-manual (秘籍) item textures: four 16x16 pixel-art icons, two layers each.

One icon per technique category, shared by the four grades of that category:

  manual_core      线装书  a thread-bound book: tinted cover, paper title slip, stitched spine, page block
  manual_active    卷轴    a rolled scroll lying diagonally: tinted silk wrap, wooden roller knobs, red tie cord
  manual_movement  折页    an accordion-folded book standing in a zigzag: tinted covers, inked paper panels
  manual_body      玉简    a jade slip: tinted jade tablet with grain and inscription, red cord and tassel

Each icon is written as two files the item model stacks (``minecraft:item/generated``):

  textures/item/manual_<category>.png       layer0, untinted: outline, paper, ink, thread, wood, cord, glints
  textures/item/manual_<category>_tint.png  layer1, tint index 1: white (or grey, for shading) where the
                                            grade colour (MyVillageClient) multiplies in; alpha 0 elsewhere

The two layers never overlap: a pixel belongs to exactly one of them, so the line art is never hidden
under the tint layer. The art is the character grids below; edit them and rerun, never the PNGs.

Usage: python3 tools/gen_manual_textures.py [--check] [--preview DIR]
  (no flag)  write the eight PNGs.
  --check    exit non-zero if any PNG on disk differs from what would be generated.
  --preview  also write DIR/manual_textures_preview.png: every icon in all four grade colours, 16x
             nearest-neighbour, for review (not a release artefact).

Standard library only (zlib + struct), as tools/gen_meridian_figure.py.
"""
from __future__ import annotations

import argparse
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEXTURE_DIR = ROOT / "src" / "main" / "resources" / "assets" / "myvillage" / "textures" / "item"
DEFAULT_PREVIEW_DIR = ROOT / "out" / "preview" / "technique_manuals"
SIZE = 16
CATEGORIES = ("core", "active", "movement", "body")

# Grade colours, as MyVillageClient registers them (黄 玄 地 天); used only for the preview.
GRADE_COLORS = (0xC9A227, 0x3F6FB5, 0x8B5A2B, 0xE8D9A0)

# Untinted line-art palette (layer0).
DETAIL = {
    "o": (0x1E, 0x17, 0x12),  # outline, warm near-black
    "p": (0xF0, 0xE6, 0xC8),  # paper
    "P": (0xC9, 0xB8, 0x92),  # paper in shadow / page edges
    "k": (0x2E, 0x27, 0x20),  # ink, engraving
    "w": (0xEC, 0xE3, 0xCB),  # silk binding thread
    "W": (0xB0, 0xA4, 0x88),  # thread in shadow
    "K": (0x7A, 0x52, 0x2E),  # wooden roller knob
    "J": (0x4B, 0x31, 0x1B),  # knob in shadow
    "L": (0xA8, 0x7A, 0x48),  # knob highlight
    "r": (0xB0, 0x34, 0x2A),  # red cord, tassel
    "R": (0x6E, 0x1E, 0x19),  # cord in shadow
    "g": (0xFF, 0xFA, 0xEC),  # glint
}
# Tint mask levels (layer1): the grade colour times level/255.
TINT = {
    "H": 255,  # lit
    "T": 228,  # body
    "t": 172,  # shade
    "d": 128,  # deep shade
}

ART = {
    # 线装书: cover facing us, spine (and the four-hole stitching) on the right as in a Chinese book,
    # the title slip at the upper left, the page block showing below and to the right.
    "core": (
        ".oooooooooooo...",
        ".oHHHHHHHHwtoo..",
        ".oHppppTTTwwopo.",
        ".oHpkkpTTTwtoPo.",
        ".oHpppPTTTwtopo.",
        ".oHpkkpTTTwwoPo.",
        ".oHpkkpTTTwtopo.",
        ".oTpppPTTTwtoPo.",
        ".oTPPPPTTTwwopo.",
        ".oTTTTTTTTwtoPo.",
        ".oTTTTTTTTwtopo.",
        ".oTTTTTTTTwwoPo.",
        ".ottttttttWtopo.",
        ".oooooooooooopo.",
        "..oPpPpPpPpPpPo.",
        "..ooooooooooooo.",
    ),
    # 卷轴: a rolled scroll from lower left to upper right, lit from the upper left; knobs at both
    # ends, a red cord tied round the middle with its ends hanging down.
    "active": (
        ".............oo.",
        "............oLKo",
        "............oKJo",
        "..........ooooo.",
        ".........oHTTto.",
        "........oHTTto..",
        ".......orrTto...",
        "......oHrrRo....",
        ".....oHTTRor....",
        "....oHTTto.rr...",
        "...oHTTto..r.r..",
        "..oHTTto...R.R..",
        ".oooooo.........",
        "oLKo............",
        "oKJo............",
        ".oo.............",
    ),
    # 折页: an accordion book standing in a zigzag; the front and back covers tinted, the inner
    # panels paper with columns of text, near folds taller than far ones.
    "movement": (
        "....o.......o...",
        "...oHo.....oto..",
        "..oHHpo...oPtto.",
        ".oHHHppo.oPPttto",
        "oHHHHpkpoPkPtto.",
        "oHHHHppkPPPPtto.",
        "oHTHHpkpPkPPtto.",
        "oHTHHppkPPkPtto.",
        "oHTHHpkpPkPPtto.",
        "oHTHHppkPPPPtto.",
        "oHHHHpppPPPPtto.",
        ".oHHHppo.oPPttto",
        "..oHHpo...oPtto.",
        "...oHo.....oto..",
        "....o.......o...",
        "................",
    ),
    # 玉简: a jade tablet with a carved border, a column of inscription, light grain and a glint;
    # a red cord through the hole at the top, its tassel hanging to the right.
    "body": (
        ".....oooooo.....",
        "....oHgHHTto....",
        "....oHHTTTto....",
        "....oHTddTto....",
        "....oHTTdTto....",
        "....oHTTTTto....",
        "....oHTddTto....",
        "....oHTdTTto....",
        "....oHTTTTto....",
        "....oHTddTto....",
        "....oHTTdTto....",
        "....otttttdo....",
        ".....oooooo.....",
        "......orro......",
        ".....orrRRo.....",
        ".....rRrRrR.....",
    ),
}


def layers(category: str) -> tuple[list[bytes], list[bytes]]:
    """The (detail, tint) RGBA rows of one icon."""
    grid = ART[category]
    if len(grid) != SIZE or any(len(row) != SIZE for row in grid):
        raise ValueError(f"{category}: the grid must be {SIZE}x{SIZE}")
    detail_rows: list[bytes] = []
    tint_rows: list[bytes] = []
    for y, row in enumerate(grid):
        detail = bytearray()
        tint = bytearray()
        for x, char in enumerate(row):
            if char == ".":
                detail += bytes(4)
                tint += bytes(4)
            elif char in DETAIL:
                detail += bytes((*DETAIL[char], 255))
                tint += bytes(4)
            elif char in TINT:
                detail += bytes(4)
                level = TINT[char]
                tint += bytes((level, level, level, 255))
            else:
                raise ValueError(f"{category}: unknown character {char!r} at {x},{y}")
        detail_rows.append(bytes(detail))
        tint_rows.append(bytes(tint))
    return detail_rows, tint_rows


def encode_png(rows: list[bytes], width: int, height: int) -> bytes:
    raw = bytearray()
    for row in rows:
        raw.append(0)
        raw.extend(row)

    def chunk(kind: bytes, data: bytes) -> bytes:
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))


def outputs(texture_dir: Path = TEXTURE_DIR) -> dict[Path, bytes]:
    """Every generated file and its bytes."""
    files: dict[Path, bytes] = {}
    for category in CATEGORIES:
        detail, tint = layers(category)
        files[texture_dir / f"manual_{category}.png"] = encode_png(detail, SIZE, SIZE)
        files[texture_dir / f"manual_{category}_tint.png"] = encode_png(tint, SIZE, SIZE)
    return files


def composite(category: str, color: int) -> list[list[tuple[int, int, int, int]]]:
    """The icon as the game draws it: layer0, then layer1 multiplied by ``color`` on top."""
    detail, tint = layers(category)
    tint_rgb = ((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF)
    pixels: list[list[tuple[int, int, int, int]]] = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            d = detail[y][x * 4:x * 4 + 4]
            t = tint[y][x * 4:x * 4 + 4]
            if t[3]:
                row.append(tuple(t[i] * tint_rgb[i] // 255 for i in range(3)) + (255,))
            else:
                row.append(tuple(d))
        pixels.append(row)
    return pixels


def preview(scale: int = 16, gap: int = 2) -> tuple[list[bytes], int, int]:
    """Rows of a sheet: one row per category, one column per grade colour, on a slot-grey background."""
    cell = SIZE + gap
    width = (len(GRADE_COLORS) * cell + gap) * scale
    height = (len(CATEGORIES) * cell + gap) * scale
    background = (0x8B, 0x8B, 0x8B)
    canvas = [[background for _ in range(width // scale)] for _ in range(height // scale)]
    for row_index, category in enumerate(CATEGORIES):
        for column_index, color in enumerate(GRADE_COLORS):
            pixels = composite(category, color)
            ox = gap + column_index * cell
            oy = gap + row_index * cell
            for y in range(SIZE):
                for x in range(SIZE):
                    r, g, b, a = pixels[y][x]
                    if a:
                        canvas[oy + y][ox + x] = (r, g, b)
    rows: list[bytes] = []
    for line in canvas:
        scaled = bytearray()
        for r, g, b in line:
            scaled += bytes((r, g, b, 255)) * scale
        rows.extend([bytes(scaled)] * scale)
    return rows, width, height


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--preview", type=Path, nargs="?", const=DEFAULT_PREVIEW_DIR)
    parser.add_argument("--texture-dir", type=Path, default=TEXTURE_DIR)
    args = parser.parse_args(argv)
    files = outputs(args.texture_dir)
    if args.preview:
        rows, width, height = preview()
        args.preview.mkdir(parents=True, exist_ok=True)
        target = args.preview / "manual_textures_preview.png"
        target.write_bytes(encode_png(rows, width, height))
        print(f"wrote {target}")
    if args.check:
        stale = [path for path, data in files.items() if not path.is_file() or path.read_bytes() != data]
        for path in stale:
            print(f"{path} is out of date; run tools/gen_manual_textures.py", file=sys.stderr)
        return 1 if stale else 0
    for path, data in files.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.is_file() or path.read_bytes() != data:
            path.write_bytes(data)
            print(f"wrote {path.relative_to(ROOT) if path.is_relative_to(ROOT) else path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
