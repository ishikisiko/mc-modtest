#!/usr/bin/env python3
"""Generate the original blade-cut particle sprite (MIT, procedural; no third-party art).

The sprite is a thin white slash on a transparent 32x32 canvas: a shallow crescent about
2 px thick at its middle, tapering to nothing at both ends, with a faint soft halo. The
particle rolls this horizontal slash to each cut's angle at runtime.

Writes src/main/resources/assets/myvillage/textures/particle/blade_cut.png using only the
standard library (zlib + struct), so no imaging package is needed.

Usage: python3 tools/gen_blade_cut_sprite.py [--output PATH] [--check]
  --check  exit non-zero if the file on disk differs from what would be generated.
"""
from __future__ import annotations

import argparse
import math
import struct
import sys
import zlib
from pathlib import Path

SIZE = 32
SUPERSAMPLE = 4
LINE_START_X = 2.0
LINE_END_X = 30.0
CENTER_Y = 16.5
# The middle of the slash bows up by this many pixels, like the tip's arc.
SAG = 1.6
CORE_HALF_THICKNESS = 1.1
HALO_HALF_THICKNESS = 2.6
HALO_ALPHA = 0.22

DEFAULT_OUTPUT = (Path(__file__).resolve().parent.parent
                  / "src/main/resources/assets/myvillage/textures/particle/blade_cut.png")


def taper(u: float) -> float:
    """Thickness share along the slash: 0 at both ends, 1 in the middle, with long soft tails."""
    if u <= 0.0 or u >= 1.0:
        return 0.0
    return math.sin(math.pi * u) ** 0.7


def coverage(x: float, y: float) -> float:
    """Alpha of the continuous slash shape at a point (pixel units)."""
    if x < LINE_START_X or x > LINE_END_X:
        return 0.0
    u = (x - LINE_START_X) / (LINE_END_X - LINE_START_X)
    centre = CENTER_Y - SAG * math.sin(math.pi * u)
    share = taper(u)
    distance = abs(y - centre)
    core = CORE_HALF_THICKNESS * share
    halo = HALO_HALF_THICKNESS * share
    if distance <= core:
        return 1.0
    if distance <= halo and halo > core:
        falloff = 1.0 - (distance - core) / (halo - core)
        return HALO_ALPHA * falloff * falloff
    return 0.0


def render() -> list[list[int]]:
    """Returns SIZE rows of SIZE alpha values (0-255), supersampled for smooth edges."""
    rows = []
    step = 1.0 / SUPERSAMPLE
    for py in range(SIZE):
        row = []
        for px in range(SIZE):
            total = 0.0
            for sy in range(SUPERSAMPLE):
                for sx in range(SUPERSAMPLE):
                    total += coverage(px + (sx + 0.5) * step, py + (sy + 0.5) * step)
            row.append(round(255 * total / (SUPERSAMPLE * SUPERSAMPLE)))
        rows.append(row)
    return rows


def encode_png(alpha_rows: list[list[int]]) -> bytes:
    """RGBA PNG: white everywhere, the shape carried entirely by alpha."""
    raw = bytearray()
    for row in alpha_rows:
        raw.append(0)  # filter type: none
        for alpha in row:
            raw.extend((255, 255, 255, alpha))

    def chunk(kind: bytes, data: bytes) -> bytes:
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", len(alpha_rows[0]), len(alpha_rows), 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))


def generate() -> bytes:
    return encode_png(render())


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)
    data = generate()
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != data:
            print(f"{args.output} is out of date; run tools/gen_blade_cut_sprite.py", file=sys.stderr)
            return 1
        return 0
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(data)
    print(f"wrote {args.output} ({len(data)} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
