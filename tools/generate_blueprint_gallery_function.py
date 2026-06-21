"""Generate a Minecraft function that places all phase 4 buildings in a row.

Scans out/1_21_1/*.nbt (the project's generated structure output), skips
temporary structures (test/debug/gallery/preview), and emits a datapack
containing one mcfunction that lays every building out along one axis:

    place template myvillage:small_house_01 ~0 ~ ~0
    place template myvillage:small_house_02 ~20 ~ ~0
    ...

Output:
    out/datapack_phase5_gallery/pack.mcmeta
    out/datapack_phase5_gallery/data/myvillage/function/phase5_blueprint_gallery.mcfunction
    reports/phase5_blueprint_gallery_manifest.json

In-game usage (after copying the datapack into <world>/datapacks/ and the
structures into <world>/generated/myvillage/structures/):
    /reload
    /function myvillage:phase5_blueprint_gallery

This is a phase 5 *pre-stage* visual acceptance tool only: no roads, no
terrain fitting, no village layout.
"""

from __future__ import annotations

import argparse
import gzip
import json
import os
import struct
import sys

NAMESPACE = "myvillage"
EXCLUDE_KEYWORDS = ("test", "debug", "gallery", "preview")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NBT_DIR = os.path.join(ROOT, "out", "1_21_1")
DATAPACK_DIR = os.path.join(ROOT, "out", "datapack_phase5_gallery")
FUNCTION_PATH = os.path.join(
    DATAPACK_DIR, "data", NAMESPACE, "function", "phase5_blueprint_gallery.mcfunction"
)
MANIFEST_PATH = os.path.join(ROOT, "reports", "phase5_blueprint_gallery_manifest.json")

PACK_MCMETA = {
    "pack": {
        "pack_format": 48,
        "description": "Phase 5 pre-stage: blueprint gallery for phase 4 buildings",
    }
}


def read_structure_size(path):
    """Best-effort read of the root 'size' list from a structure NBT.

    Returns (x, y, z) or None on any parse problem; spacing falls back to the
    fixed value so a parse failure is never fatal.
    """
    try:
        data = gzip.open(path, "rb").read()
        pos = [0]

        def take(n):
            b = data[pos[0]:pos[0] + n]
            pos[0] += n
            return b

        def read_name():
            (n,) = struct.unpack(">H", take(2))
            return take(n).decode("utf-8")

        def skip_payload(tag):
            if tag in (1, 2, 3, 4, 5, 6):
                take({1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}[tag])
            elif tag == 7:
                (n,) = struct.unpack(">i", take(4))
                take(n)
            elif tag == 8:
                (n,) = struct.unpack(">H", take(2))
                take(n)
            elif tag == 9:
                etag = take(1)[0]
                (n,) = struct.unpack(">i", take(4))
                for _ in range(n):
                    skip_payload(etag)
            elif tag == 10:
                while True:
                    t = take(1)[0]
                    if t == 0:
                        break
                    read_name()
                    skip_payload(t)
            elif tag in (11, 12):
                (n,) = struct.unpack(">i", take(4))
                take(n * (4 if tag == 11 else 8))

        if take(1)[0] != 10:
            return None
        read_name()
        while True:
            t = take(1)[0]
            if t == 0:
                return None
            name = read_name()
            if name == "size" and t == 9:
                etag = take(1)[0]
                (n,) = struct.unpack(">i", take(4))
                if etag == 3 and n == 3:
                    return struct.unpack(">3i", take(12))
                for _ in range(n):
                    skip_payload(etag)
            else:
                skip_payload(t)
    except Exception:
        return None


def main():
    parser = argparse.ArgumentParser(description="Generate phase 5 blueprint gallery mcfunction")
    parser.add_argument("--spacing", type=int, default=20, help="Distance between buildings (default 20)")
    parser.add_argument("--axis", choices=("x", "z"), default="x", help="Layout axis (default x)")
    args = parser.parse_args()

    if not os.path.isdir(NBT_DIR):
        print(f"error: NBT dir not found: {NBT_DIR}", file=sys.stderr)
        return 1

    names = sorted(
        os.path.splitext(f)[0]
        for f in os.listdir(NBT_DIR)
        if f.endswith(".nbt")
        and not any(k in f.lower() for k in EXCLUDE_KEYWORDS)
    )
    if not names:
        print("error: no building NBT files found", file=sys.stderr)
        return 1

    lines = ["# Phase 5 pre-stage: place all phase 4 buildings in a row for visual acceptance"]
    buildings = []
    for i, name in enumerate(names):
        offset = i * args.spacing
        ox, oz = (offset, 0) if args.axis == "x" else (0, offset)
        lines.append(f"place template {NAMESPACE}:{name} ~{ox} ~ ~{oz}")
        nbt_path = os.path.join(NBT_DIR, name + ".nbt")
        size = read_structure_size(nbt_path)
        buildings.append({
            "structure_name": f"{NAMESPACE}:{name}",
            "source_nbt": os.path.relpath(nbt_path, ROOT).replace(os.sep, "/"),
            "size": list(size) if size else None,
            "offset_x": ox,
            "offset_y": 0,
            "offset_z": oz,
        })

    os.makedirs(os.path.dirname(FUNCTION_PATH), exist_ok=True)
    with open(FUNCTION_PATH, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    with open(os.path.join(DATAPACK_DIR, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump(PACK_MCMETA, f, indent=2)

    os.makedirs(os.path.dirname(MANIFEST_PATH), exist_ok=True)
    manifest = {
        "function_file": os.path.relpath(FUNCTION_PATH, ROOT).replace(os.sep, "/"),
        "datapack_dir": os.path.relpath(DATAPACK_DIR, ROOT).replace(os.sep, "/"),
        "spacing": args.spacing,
        "axis": args.axis,
        "building_count": len(buildings),
        "buildings": buildings,
    }
    with open(MANIFEST_PATH, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)

    print(f"buildings: {len(buildings)}")
    print(f"function:  {manifest['function_file']}")
    print(f"manifest:  {os.path.relpath(MANIFEST_PATH, ROOT).replace(os.sep, '/')}")
    for b in buildings:
        print(f"  {b['structure_name']} -> ~{b['offset_x']} ~ ~{b['offset_z']} (size={b['size']})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
