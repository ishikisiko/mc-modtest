#!/usr/bin/env python3
"""Offline isometric preview of a 48³ hero-rockery micro-voxel field.

The block-level `preview_structure.py` renders each Minecraft block as one solid
cube, so it cannot show the *sub-block* sculpt that `rockery_block` carries. This
tool decodes the 1/16-resolution `docs/rockery_compressed.json` (or any field of
the same schema) and renders every micro-cube, so the layered-ledge form, the
waterfall groove, the pond and the summit tree are all visible without launching
Minecraft.

Self-contained (stdlib only): reuses the same isometric projection / painter's
order / face shading as `preview_structure.render_isometric`.

Usage:
  python3 tools/buildgen/preview_voxel_field.py [field.json] [-o out.png] [-s SCALE]
"""

from __future__ import annotations

import argparse
import math
import re
import struct
import sys
import zlib
from pathlib import Path
from typing import Dict, List, Sequence, Tuple

RGBA = Tuple[int, int, int, int]
Field = Dict[Tuple[int, int, int], str]

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_FIELD = REPO_ROOT / "docs" / "rockery_compressed.json"

# Preview palette (tuned so material bands read clearly, not Minecraft-accurate).
COLORS: Dict[str, Tuple[int, int, int]] = {
    "s": (134, 132, 130),   # stone — warm grey
    "m": (104, 124, 78),    # mossy stone — olive
    "w": (60, 120, 210),    # water — blue
    "g": (104, 168, 56),    # grass — green
    "t": (118, 84, 50),     # oak log — brown
    "l": (54, 104, 46),     # oak leaves — dark green
    "f": (214, 126, 196),   # flower accent — pink (optional palette ext.)
    "v": (74, 132, 60),     # vine accent — green (optional palette ext.)
}
_RLE = re.compile(r"(\d+)([a-z])")


def decode_field(path: Path) -> Tuple[Tuple[int, int, int], Field]:
    import json
    data = json.loads(path.read_text())
    size = tuple(int(v) for v in data["size"])  # (x, y, z)
    field: Field = {}
    for layer in data["layers"]:
        y = int(layer["y"])
        for z, row in enumerate(layer["rows"]):
            x = 0
            for n, ch in _RLE.findall(row):
                n = int(n)
                if ch != "a":
                    for dx in range(n):
                        field[(x + dx, y, z)] = ch
                x += n
    return size, field  # type: ignore[return-value]


# ----- minimal PNG canvas (copied from preview_structure.py) -----------------
class Canvas:
    __slots__ = ("w", "h", "buf")

    def __init__(self, w: int, h: int) -> None:
        self.w, self.h = w, h
        self.buf = bytearray(w * h * 4)

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
                    i = (y * self.w + x) * 4
                    self.buf[i:i + 4] = bytes(color)

    def write_png(self, path: Path) -> None:
        def chunk(tag: bytes, data: bytes) -> bytes:
            return (struct.pack(">I", len(data)) + tag + data
                    + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
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


def shade(c: Tuple[int, int, int], f: float) -> RGBA:
    return (max(0, min(255, int(c[0] * f))),
            max(0, min(255, int(c[1] * f))),
            max(0, min(255, int(c[2] * f))), 255)


def _exposed(p: Tuple[int, int, int], field: Field) -> bool:
    x, y, z = p
    for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
        if (x + d[0], y + d[1], z + d[2]) not in field:
            return True
    return False


def render(size: Tuple[int, int, int], field: Field, out: Path, scale: int) -> None:
    s = scale
    half = s / 2.0
    voxel_h = float(s)

    def tc(x: int, y: int, z: int) -> Tuple[float, float]:
        return (x - z) * s, (x + z) * half - y * voxel_h

    draw = [(x, y, z, ch) for (x, y, z), ch in field.items() if _exposed((x, y, z), field)]
    draw.sort(key=lambda v: (v[0] + v[2], v[1]))

    min_x = min_y = math.inf
    max_x = max_y = -math.inf
    for x, y, z, _ in draw:
        cx, cy = tc(x, y, z)
        min_x, max_x = min(min_x, cx - s), max(max_x, cx + s)
        min_y, max_y = min(min_y, cy - half), max(max_y, cy + half + voxel_h)
    margin = 6
    off_x, off_y = -min_x + margin, -min_y + margin
    w = int(math.ceil(max_x - min_x)) + 2 * margin
    h = int(math.ceil(max_y - min_y)) + 2 * margin
    cv = Canvas(w, h)

    for x, y, z, ch in draw:
        base = COLORS.get(ch, (200, 0, 200))
        cx, cy = tc(x, y, z)
        cx += off_x
        cy += off_y
        top = [(cx, cy - half), (cx + s, cy), (cx, cy + half), (cx - s, cy)]
        left = [(cx - s, cy), (cx, cy + half), (cx, cy + half + voxel_h), (cx - s, cy + voxel_h)]
        right = [(cx + s, cy), (cx, cy + half), (cx, cy + half + voxel_h), (cx + s, cy + voxel_h)]
        cv.fill_polygon(left, shade(base, 0.60))
        cv.fill_polygon(right, shade(base, 0.82))
        cv.fill_polygon(top, shade(base, 1.0))
    cv.write_png(out)
    print(f"wrote {out}  ({w}x{h}px, {len(draw)} exposed micro-cubes of {len(field)} solid)")


def main(argv: List[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("field", nargs="?", default=str(DEFAULT_FIELD))
    ap.add_argument("-o", "--out", default=None)
    ap.add_argument("-s", "--scale", type=int, default=8)
    args = ap.parse_args(argv)
    field_path = Path(args.field)
    size, field = decode_field(field_path)
    out = Path(args.out) if args.out else field_path.with_suffix(".preview.png")
    render(size, field, out, args.scale)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
