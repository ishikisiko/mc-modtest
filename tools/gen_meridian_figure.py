#!/usr/bin/env python3
"""Generate the seated meditation figure for the H panel's meridian view (procedural, original).

The figure is a cultivator sitting cross-legged on a round cushion, seen from the side and
facing right, so the governing vessel runs up the back and the conception vessel down the
front. It is drawn as a translucent ink-jade "inner body" with a cool rim light from the
upper left, a lighter near arm resting its hand on the knee, and a warm woven cushion.
Meridians, acupoints, and the dantian are not in the texture: the panel draws them on top
from the data model in MeridianChart.java, whose normalised coordinates share this
texture's square (0,0 top-left, 1,1 bottom-right).

Writes src/main/resources/assets/myvillage/textures/gui/cultivation/meridian_figure.png with
the standard library only (zlib + struct). The neighbouring .png.mcmeta asks for linear
filtering, because the panel draws the 512 px texture at about 150 GUI pixels.

Usage: python3 tools/gen_meridian_figure.py [--output PATH] [--check]
                                            [--preview PATH [--preview-size N]]
  --check    exit non-zero if the file on disk differs from what would be generated.
  --preview  also write a review image: the figure on the panel's stage colour with the
             acupoints and channels read from MeridianChart.java, so the texture and the
             data model can be checked against each other.
"""
from __future__ import annotations

import argparse
import math
import re
import struct
import sys
import zlib
from pathlib import Path

SIZE = 512
SUPERSAMPLE_ROWS = 4
ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUTPUT = ROOT / "src/main/resources/assets/myvillage/textures/gui/cultivation/meridian_figure.png"
CHART_SOURCE = ROOT / "src/main/java/com/example/myvillage/client/cultivation/panel/MeridianChart.java"
STAGE_COLOR = (14, 20, 22)

Point = tuple[float, float]


# ---------------------------------------------------------------------------------------------
# Geometry: paths in normalised coordinates, flattened to polygons.

def cubic(p0: Point, p1: Point, p2: Point, p3: Point, steps: int = 24) -> list[Point]:
    points = []
    for index in range(1, steps + 1):
        t = index / steps
        u = 1.0 - t
        points.append((
            u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0],
            u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1],
        ))
    return points


def path(start: Point, *segments: tuple[Point, ...]) -> list[Point]:
    """A closed path: each segment is (end,) for a line or (c1, c2, end) for a cubic."""
    points = [start]
    for segment in segments:
        if len(segment) == 1:
            points.append(segment[0])
        else:
            points.extend(cubic(points[-1], segment[0], segment[1], segment[2]))
    return points


def ellipse(cx: float, cy: float, rx: float, ry: float, rotation: float = 0.0, steps: int = 96) -> list[Point]:
    cos_r = math.cos(rotation)
    sin_r = math.sin(rotation)
    points = []
    for index in range(steps):
        angle = 2 * math.pi * index / steps
        x = rx * math.cos(angle)
        y = ry * math.sin(angle)
        points.append((cx + x * cos_r - y * sin_r, cy + x * sin_r + y * cos_r))
    return points


# The silhouette. Facing right; y grows downward. Head about 0.17 tall, seated height about
# four heads, thigh to knee about two and a half heads forward of the hips.
HEAD = ellipse(0.505, 0.150, 0.066, 0.079, rotation=-0.12)
FACE = path(
    (0.540, 0.090),
    ((0.565, 0.100), (0.574, 0.125), (0.573, 0.140)),   # brow
    ((0.582, 0.150),),                                  # nose bridge
    ((0.586, 0.160), (0.583, 0.166), (0.576, 0.167)),   # nose tip
    ((0.577, 0.180), (0.574, 0.188), (0.570, 0.192)),   # lips
    ((0.572, 0.205), (0.562, 0.222), (0.540, 0.224)),   # chin
    ((0.520, 0.200),),
)
TOPKNOT = ellipse(0.468, 0.074, 0.034, 0.025, rotation=-0.45)
HAIRPIN = path((0.418, 0.096), ((0.508, 0.044),), ((0.512, 0.051),), ((0.422, 0.103),))
NECK = path((0.474, 0.200), ((0.540, 0.205),), ((0.548, 0.285),), ((0.462, 0.285),))
TORSO_AND_LEGS = path(
    (0.474, 0.230),                                         # nape
    ((0.446, 0.252), (0.418, 0.290), (0.414, 0.350)),       # rounded upper back
    ((0.410, 0.410), (0.428, 0.480), (0.438, 0.530)),       # lumbar hollow
    ((0.444, 0.580), (0.384, 0.620), (0.382, 0.690)),       # sacrum, seat
    ((0.382, 0.745), (0.414, 0.774), (0.470, 0.778)),       # under the seat
    ((0.600, 0.790), (0.760, 0.804), (0.858, 0.800)),       # under the thigh
    ((0.920, 0.797), (0.944, 0.750), (0.924, 0.718)),       # knee
    ((0.900, 0.686), (0.816, 0.680), (0.738, 0.684)),       # crossed shin, top line
    ((0.700, 0.686), (0.676, 0.662), (0.646, 0.658)),       # upturned foot
    ((0.622, 0.655), (0.608, 0.640), (0.606, 0.615)),       # lap into the belly
    ((0.606, 0.560), (0.616, 0.500), (0.610, 0.462)),       # belly
    ((0.604, 0.420), (0.626, 0.380), (0.608, 0.332)),       # chest
    ((0.594, 0.300), (0.565, 0.280), (0.545, 0.268)),       # collarbone, throat
    ((0.520, 0.250),),
)
SHIN_CREASE = path(
    (0.900, 0.716),
    ((0.850, 0.706), (0.790, 0.704), (0.730, 0.708)),
    ((0.730, 0.713),),
    ((0.790, 0.709), (0.850, 0.711), (0.900, 0.721)),
)
FAR_KNEE = ellipse(0.872, 0.712, 0.050, 0.032, rotation=-0.25)
FOOT = path(
    (0.618, 0.664),
    ((0.630, 0.640), (0.668, 0.634), (0.700, 0.646)),
    ((0.720, 0.655), (0.722, 0.676), (0.704, 0.684)),
    ((0.672, 0.690), (0.636, 0.686), (0.618, 0.664)),
)
ARM = path(
    (0.474, 0.300),                                         # back of the shoulder
    ((0.462, 0.360), (0.466, 0.460), (0.476, 0.515)),       # back of the upper arm
    ((0.482, 0.552), (0.500, 0.572), (0.526, 0.576)),       # elbow
    ((0.600, 0.598), (0.690, 0.648), (0.752, 0.674)),       # forearm, underside
    ((0.780, 0.688), (0.818, 0.692), (0.838, 0.680)),       # heel of the hand
    ((0.856, 0.670), (0.850, 0.650), (0.828, 0.646)),       # fingers over the knee
    ((0.800, 0.643), (0.776, 0.642), (0.750, 0.642)),       # back of the hand
    ((0.684, 0.618), (0.604, 0.570), (0.560, 0.532)),       # forearm, top
    ((0.536, 0.506), (0.534, 0.420), (0.536, 0.360)),       # front of the upper arm
    ((0.540, 0.318), (0.522, 0.292), (0.500, 0.290)),       # front of the shoulder
)
CUSHION_SIDE = path(
    (0.262, 0.800),
    ((0.738, 0.800),),
    ((0.760, 0.830), (0.752, 0.866), (0.700, 0.878)),
    ((0.600, 0.896), (0.400, 0.896), (0.300, 0.878)),
    ((0.248, 0.866), (0.240, 0.830), (0.262, 0.800)),
)
CUSHION_TOP = ellipse(0.500, 0.800, 0.240, 0.046)
SHADOW = ellipse(0.560, 0.890, 0.360, 0.030)


# ---------------------------------------------------------------------------------------------
# Rasterising: even-odd scanline coverage with exact horizontal spans and four rows per pixel.

def coverage(polygon: list[Point]) -> list[float]:
    grid = [0.0] * (SIZE * SIZE)
    edges = []
    count = len(polygon)
    for index in range(count):
        x0, y0 = polygon[index]
        x1, y1 = polygon[(index + 1) % count]
        if y0 != y1:
            edges.append((x0 * SIZE, y0 * SIZE, x1 * SIZE, y1 * SIZE))
    top = max(0, int(min(y for _, y in polygon) * SIZE) - 1)
    bottom = min(SIZE, int(max(y for _, y in polygon) * SIZE) + 2)
    weight = 1.0 / SUPERSAMPLE_ROWS
    for row in range(top, bottom):
        base = row * SIZE
        for sub in range(SUPERSAMPLE_ROWS):
            y = row + (sub + 0.5) / SUPERSAMPLE_ROWS
            crossings = []
            for x0, y0, x1, y1 in edges:
                if (y0 <= y < y1) or (y1 <= y < y0):
                    crossings.append(x0 + (y - y0) * (x1 - x0) / (y1 - y0))
            crossings.sort()
            for pair in range(0, len(crossings) - 1, 2):
                a = max(0.0, crossings[pair])
                b = min(float(SIZE), crossings[pair + 1])
                if b <= a:
                    continue
                first = int(a)
                last = int(b)
                if first == last:
                    grid[base + first] += (b - a) * weight
                    continue
                grid[base + first] += (first + 1 - a) * weight
                for column in range(first + 1, min(last, SIZE)):
                    grid[base + column] += weight
                if last < SIZE:
                    grid[base + last] += (b - last) * weight
    return [min(1.0, value) for value in grid]


def union(*masks: list[float]) -> list[float]:
    result = list(masks[0])
    for mask in masks[1:]:
        result = [a if a > b else b for a, b in zip(result, mask)]
    return result


def subtract(mask: list[float], cut: list[float]) -> list[float]:
    return [a * (1.0 - b) for a, b in zip(mask, cut)]


def box_blur(values: list[float], radius: int, passes: int = 2) -> list[float]:
    """Separable box blur; two passes approximate a Gaussian."""
    width = 2 * radius + 1
    current = values
    for _ in range(passes):
        horizontal = [0.0] * (SIZE * SIZE)
        for row in range(SIZE):
            base = row * SIZE
            line = current[base:base + SIZE]
            total = sum(line[0:radius + 1]) + line[0] * radius
            for column in range(SIZE):
                horizontal[base + column] = total / width
                add = line[min(SIZE - 1, column + radius + 1)]
                remove = line[max(0, column - radius)]
                total += add - remove
        vertical = [0.0] * (SIZE * SIZE)
        for column in range(SIZE):
            line = horizontal[column::SIZE]
            total = sum(line[0:radius + 1]) + line[0] * radius
            for row in range(SIZE):
                vertical[row * SIZE + column] = total / width
                add = line[min(SIZE - 1, row + radius + 1)]
                remove = line[max(0, row - radius)]
                total += add - remove
        current = vertical
    return current


def rim(mask: list[float], radius: int, light: Point) -> tuple[list[float], list[float]]:
    """Returns (lit rim, plain edge) bands just inside the mask's outline."""
    soft = box_blur(mask, radius)
    lx, ly = light
    norm = math.hypot(lx, ly)
    lx, ly = lx / norm, ly / norm
    lit = [0.0] * (SIZE * SIZE)
    edge = [0.0] * (SIZE * SIZE)
    for row in range(1, SIZE - 1):
        base = row * SIZE
        for column in range(1, SIZE - 1):
            index = base + column
            inside = mask[index]
            if inside <= 0.0:
                continue
            value = soft[index]
            band = max(0.0, 1.0 - abs(value - 0.5) * 2.0) * inside
            if band <= 0.0:
                continue
            gx = soft[index + 1] - soft[index - 1]
            gy = soft[index + SIZE] - soft[index - SIZE]
            length = math.hypot(gx, gy)
            facing = 0.0 if length == 0.0 else max(0.0, (-gx * lx - gy * ly) / length)
            lit[index] = band * facing
            edge[index] = band
    return lit, edge


# ---------------------------------------------------------------------------------------------
# Compositing into straight-alpha RGBA.

class Canvas:
    def __init__(self) -> None:
        self.r = [0.0] * (SIZE * SIZE)
        self.g = [0.0] * (SIZE * SIZE)
        self.b = [0.0] * (SIZE * SIZE)
        self.a = [0.0] * (SIZE * SIZE)

    def over(self, mask: list[float], color, alpha: float = 1.0) -> None:
        """Paints colour (an RGB tuple or a function of (x, y) in 0..1) through mask * alpha."""
        constant = not callable(color)
        for index, share in enumerate(mask):
            if share <= 0.0:
                continue
            source_alpha = share * alpha
            if constant:
                cr, cg, cb = color
            else:
                cr, cg, cb = color((index % SIZE + 0.5) / SIZE, (index // SIZE + 0.5) / SIZE)
            # premultiplied "over"
            keep = 1.0 - source_alpha
            self.r[index] = cr * source_alpha + self.r[index] * keep
            self.g[index] = cg * source_alpha + self.g[index] * keep
            self.b[index] = cb * source_alpha + self.b[index] * keep
            self.a[index] = source_alpha + self.a[index] * keep

    def add(self, mask: list[float], color, strength: float = 1.0) -> None:
        """Adds light inside what is already painted (keeps alpha)."""
        cr, cg, cb = color
        for index, share in enumerate(mask):
            if share <= 0.0:
                continue
            amount = share * strength * self.a[index]
            self.r[index] = min(255.0 * self.a[index], self.r[index] + cr * amount)
            self.g[index] = min(255.0 * self.a[index], self.g[index] + cg * amount)
            self.b[index] = min(255.0 * self.a[index], self.b[index] + cb * amount)

    def rgba_rows(self) -> list[bytes]:
        rows = []
        for row in range(SIZE):
            line = bytearray()
            for column in range(SIZE):
                index = row * SIZE + column
                alpha = self.a[index]
                if alpha <= 0.0:
                    line.extend((0, 0, 0, 0))
                    continue
                line.extend((
                    max(0, min(255, round(self.r[index] / alpha))),
                    max(0, min(255, round(self.g[index] / alpha))),
                    max(0, min(255, round(self.b[index] / alpha))),
                    max(0, min(255, round(alpha * 255))),
                ))
            rows.append(bytes(line))
        return rows


def mix(a, b, t: float):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def body_color(x: float, y: float):
    base = mix((46, 78, 78), (28, 48, 52), (y - 0.08) / 0.72)
    # a soft inner light around the trunk, where the circuit runs
    distance = math.hypot((x - 0.520) / 0.16, (y - 0.480) / 0.30)
    glow = max(0.0, 1.0 - distance) ** 2
    return mix(base, (64, 112, 104), glow * 0.6)


def arm_color(x: float, y: float):
    return mix((58, 94, 90), (40, 68, 68), (y - 0.30) / 0.40)


def cushion_side_color(x: float, y: float):
    shade = (1.0 - abs(x - 0.44) / 0.30) * (1.0 - (y - 0.80) / 0.12)
    return mix((54, 38, 20), (120, 90, 48), shade)


def cushion_top_color(x: float, y: float):
    distance = math.hypot((x - 0.50) / 0.23, (y - 0.805) / 0.04)
    return mix((150, 118, 66), (112, 84, 44), distance)


def render() -> list[bytes]:
    canvas = Canvas()
    light = (-0.75, -1.0)

    shadow = box_blur(coverage(SHADOW), 6)
    canvas.over(shadow, (0, 0, 0), 0.55)

    cushion_side = coverage(CUSHION_SIDE)
    canvas.over(cushion_side, cushion_side_color, 0.97)
    for offset in (0.018, 0.040, 0.062):
        coil = subtract(
            coverage(ellipse(0.500, 0.800 + offset, 0.246 - offset * 0.25, 0.050)),
            coverage(ellipse(0.500, 0.800 + offset - 0.004, 0.246 - offset * 0.25, 0.050)))
        canvas.over(coil, (60, 42, 22), 0.55)
    cushion_top = coverage(CUSHION_TOP)
    canvas.over(cushion_top, cushion_top_color, 0.98)
    for radius in (0.205, 0.165, 0.125, 0.085, 0.045):
        ring = subtract(
            coverage(ellipse(0.50, 0.800, radius, radius * 0.19)),
            coverage(ellipse(0.50, 0.800, radius - 0.005, (radius - 0.005) * 0.19)))
        canvas.over(ring, (92, 68, 36), 0.6)
    cushion_lit, _ = rim(cushion_top, 2, (0.0, -1.0))
    canvas.add(cushion_lit, (220, 190, 120), 0.5)

    far_knee = coverage(FAR_KNEE)
    canvas.over(far_knee, (24, 40, 42), 0.92)

    body = union(
        coverage(TORSO_AND_LEGS),
        coverage(NECK),
        coverage(HEAD),
        coverage(FACE),
        coverage(TOPKNOT),
    )
    canvas.over(body, body_color, 0.93)
    body_lit, body_edge = rim(body, 3, light)
    canvas.add(body_edge, (70, 120, 110), 0.35)
    canvas.add(body_lit, (150, 220, 200), 0.75)

    crease = coverage(SHIN_CREASE)
    canvas.over(crease, (18, 30, 32), 0.6)

    foot = coverage(FOOT)
    canvas.over(foot, (52, 86, 84), 0.95)
    foot_lit, foot_edge = rim(foot, 2, light)
    canvas.add(foot_edge, (90, 140, 130), 0.4)
    canvas.add(foot_lit, (150, 215, 195), 0.5)

    hairpin = coverage(HAIRPIN)
    canvas.over(hairpin, (196, 164, 98), 0.95)

    arm = coverage(ARM)
    canvas.over(arm, arm_color, 0.95)
    arm_lit, arm_edge = rim(arm, 3, light)
    canvas.add(arm_edge, (90, 145, 135), 0.55)
    canvas.add(arm_lit, (160, 225, 205), 0.65)
    return canvas.rgba_rows()


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


def generate() -> bytes:
    return encode_png(render(), SIZE, SIZE)


# ---------------------------------------------------------------------------------------------
# Review preview: the texture plus the chart read from MeridianChart.java.

POINT_PATTERN = re.compile(r'point\(\s*"(?P<id>[a-z_]+)"\s*,[^,]*,\s*(?P<x>[0-9.]+)F\s*,\s*(?P<y>[0-9.]+)F')
CHANNEL_PATTERN = re.compile(r'channel\(\s*"(?P<id>[a-z_]+)"\s*,[^,]*,\s*route\(')
WAYPOINT_PATTERN = re.compile(r'"(?P<id>[a-z_]+)"|via\(\s*(?P<x>[0-9.]+)F\s*,\s*(?P<y>[0-9.]+)F\s*\)')


def _balanced(text: str, start: int) -> str:
    """The text from start up to the parenthesis that closes the one just before start."""
    depth = 1
    for index in range(start, len(text)):
        if text[index] == "(":
            depth += 1
        elif text[index] == ")":
            depth -= 1
            if depth == 0:
                return text[start:index]
    raise ValueError("unbalanced route")


def read_chart(source: Path) -> tuple[dict[str, Point], dict[str, list[Point]]]:
    """Acupoints and channel routes as written in MeridianChart.java."""
    text = source.read_text(encoding="utf-8")
    points = {match["id"]: (float(match["x"]), float(match["y"])) for match in POINT_PATTERN.finditer(text)}
    channels = {}
    for match in CHANNEL_PATTERN.finditer(text):
        route = []
        for waypoint in WAYPOINT_PATTERN.finditer(_balanced(text, match.end())):
            if waypoint["id"]:
                route.append(points[waypoint["id"]])
            else:
                route.append((float(waypoint["x"]), float(waypoint["y"])))
        channels[match["id"]] = route
    return points, channels


def write_preview(png_rows: list[bytes], target: Path, scale_to: int) -> None:
    try:
        from PIL import Image, ImageDraw
    except ImportError:  # the preview is a review aid; the texture itself needs no imaging package
        print("--preview needs Pillow (for example .venv-preview/bin/python)", file=sys.stderr)
        return
    figure = Image.frombytes("RGBA", (SIZE, SIZE), b"".join(png_rows))
    stage = Image.new("RGBA", (SIZE, SIZE), STAGE_COLOR + (255,))
    stage.alpha_composite(figure)
    if CHART_SOURCE.is_file():
        points, channels = read_chart(CHART_SOURCE)
        draw = ImageDraw.Draw(stage)
        for route in channels.values():
            draw.line([(x * SIZE, y * SIZE) for x, y in route], fill=(120, 230, 200, 255), width=2)
        for x, y in points.values():
            draw.ellipse((x * SIZE - 4, y * SIZE - 4, x * SIZE + 4, y * SIZE + 4), outline=(255, 220, 140, 255))
    stage = stage.resize((scale_to, scale_to), Image.LANCZOS)
    target.parent.mkdir(parents=True, exist_ok=True)
    stage.save(target)
    print(f"wrote preview {target}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--preview", type=Path)
    parser.add_argument("--preview-size", type=int, default=300)
    args = parser.parse_args(argv)
    rows = render()
    data = encode_png(rows, SIZE, SIZE)
    if args.preview:
        write_preview(rows, args.preview, args.preview_size)
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != data:
            print(f"{args.output} is out of date; run tools/gen_meridian_figure.py", file=sys.stderr)
            return 1
        return 0
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(data)
    print(f"wrote {args.output} ({len(data)} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
