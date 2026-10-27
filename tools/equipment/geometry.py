"""Shared deterministic image, item geometry and display helpers.

Extracted without arithmetic changes from the Qingfeng generator; weapon art
continues to belong to its dedicated generator. No equipment-specific constants.
"""
from __future__ import annotations

import json
import math
import struct
import zlib

FACES = ("north", "south", "east", "west", "up", "down")

def encode_png(pixels) -> bytes:
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b, a in row:
            raw.extend((r, g, b, a))

    def chunk(kind: bytes, data: bytes) -> bytes:
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", len(pixels[0]), len(pixels), 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def decode_png_rgba(data: bytes) -> tuple[int, int, bytes]:
    """Decode an 8-bit RGBA non-interlaced PNG (enough to compare generator output)."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos, idat, width = 8, b"", 0
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            if (depth, ctype, interlace) != (8, 6, 0):
                raise ValueError("only 8-bit RGBA non-interlaced PNG is supported")
        elif kind == b"IDAT":
            idat += body
        pos += 12 + length
    raw = zlib.decompress(idat)
    stride = width * 4
    out, prev = bytearray(), bytearray(stride)
    for row in range(height):
        f = raw[row * (stride + 1)]
        line = bytearray(raw[row * (stride + 1) + 1:(row + 1) * (stride + 1)])
        for k in range(stride):
            a = line[k - 4] if k >= 4 else 0
            b = prev[k]
            c = prev[k - 4] if k >= 4 else 0
            if f == 1:
                line[k] = (line[k] + a) & 0xFF
            elif f == 2:
                line[k] = (line[k] + b) & 0xFF
            elif f == 3:
                line[k] = (line[k] + (a + b) // 2) & 0xFF
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if pa <= pb and pa <= pc else b if pb <= pc else c
                line[k] = (line[k] + pred) & 0xFF
        out += line
        prev = line
    return width, height, bytes(out)


def _r(v: float) -> float:
    out = round(v, 4)
    return 0.0 if out == 0 else out


def box(name: str, lo, hi, faces: dict, rotation: dict | None = None, texture: str = "#sword") -> dict:
    element = {"name": name, "from": [_r(c) for c in lo], "to": [_r(c) for c in hi]}
    if rotation:
        element["rotation"] = {"angle": rotation["angle"], "axis": rotation["axis"],
                               "origin": [_r(c) for c in rotation["origin"]]}
    element["faces"] = {face: {"uv": faces[face], "texture": texture} for face in FACES if face in faces}
    return element


def _rot(axis: str, degrees: float):
    c, s = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
    if axis == "x":
        return [[1, 0, 0], [0, c, -s], [0, s, c]]
    if axis == "y":
        return [[c, 0, s], [0, 1, 0], [-s, 0, c]]
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(a, v):
    return tuple(sum(a[i][k] * v[k] for k in range(3)) for i in range(3))


def euler_xyz(rotation):
    """Matrix of Quaternionf.rotationXYZ(x, y, z) = Rx * Ry * Rz (degrees)."""
    x, y, z = rotation
    return mm(mm(_rot("x", x), _rot("y", y)), _rot("z", z))


def to_euler_xyz(m):
    y = math.degrees(math.asin(max(-1.0, min(1.0, m[0][2]))))
    x = math.degrees(math.atan2(-m[1][2], m[2][2]))
    z = math.degrees(math.atan2(-m[0][1], m[0][0]))
    if abs(x) > 90.0:  # Rx(a) Ry(b) Rz(c) == Rx(a + 180) Ry(180 - b) Rz(c + 180): prefer the small pitch
        x, y, z = x + 180.0, 180.0 - y, z + 180.0

    def wrap(a: float) -> float:
        a = (a + 180.0) % 360.0 - 180.0
        return 180.0 if abs(a + 180.0) < 1e-9 else a
    return (wrap(x), wrap(y), wrap(z))


def normalize(v):
    n = math.sqrt(sum(c * c for c in v))
    return tuple(c / n for c in v)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def display_point(transform: dict, p) -> tuple[float, float, float]:
    """Model pixel -> display-frame pixel for one ItemTransform (right-hand, no mirroring)."""
    r = euler_xyz(transform["rotation"])
    s = transform["scale"][0]
    q = mv(r, tuple(s * (p[i] - 8.0) for i in range(3)))
    return tuple(transform["translation"][i] + q[i] for i in range(3))


def display_vector(transform: dict, v) -> tuple[float, float, float]:
    return mv(euler_xyz(transform["rotation"]), v)


def render_model_json(doc: dict) -> str:
    """Stable JSON: two-space indent, one element / display entry per line."""
    lines = ["{"]
    items = list(doc.items())
    for index, (key, value) in enumerate(items):
        comma = "," if index < len(items) - 1 else ""
        if key == "elements":
            lines.append('  "elements": [')
            for e_index, element in enumerate(value):
                tail = "," if e_index < len(value) - 1 else ""
                lines.append("    " + json.dumps(element, separators=(", ", ": ")) + tail)
            lines.append("  ]" + comma)
        elif key in ("display", "perspectives") and isinstance(value, dict):
            lines.append(f'  "{key}": {{')
            sub = list(value.items())
            for s_index, (name, entry) in enumerate(sub):
                tail = "," if s_index < len(sub) - 1 else ""
                lines.append(f"    {json.dumps(name)}: " + json.dumps(entry, separators=(", ", ": ")) + tail)
            lines.append("  }" + comma)
        else:
            lines.append(f"  {json.dumps(key)}: " + json.dumps(value, separators=(", ", ": ")) + comma)
    lines.append("}")
    return "\n".join(lines) + "\n"


def check_elements(elements: list[dict]) -> list[str]:
    errors = []
    for e in elements:
        for c in e["from"] + e["to"]:
            if not -16.0 <= c <= 32.0:
                errors.append(f"{e['name']}: coordinate {c} outside [-16, 32]")
        for i in range(3):
            if e["from"][i] > e["to"][i]:
                errors.append(f"{e['name']}: from > to on axis {i}")
        rot = e.get("rotation")
        if rot and rot["angle"] not in (-45, -22.5, 0, 22.5, 45):
            errors.append(f"{e['name']}: rotation {rot['angle']} not allowed")
        for face, data in e["faces"].items():
            if any(not 0.0 <= c <= 16.0 for c in data["uv"]):
                errors.append(f"{e['name']}.{face}: uv out of range")
    return errors


def element_corners(e: dict):
    lo, hi = e["from"], e["to"]
    corners = [(x, y, z) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
    rot = e.get("rotation")
    if not rot:
        return corners
    m = _rot(rot["axis"], rot["angle"])
    o = rot["origin"]
    return [tuple(o[i] + mv(m, tuple(p[k] - o[k] for k in range(3)))[i] for i in range(3)) for p in corners]
