#!/usr/bin/env python3
"""Publish approved imagegen ink originals unchanged; never repaint generated resources."""
import argparse
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools/cultivation_ink/source"
DESTINATION = ROOT / "src/main/resources/assets/myvillage/textures/gui/cultivation/ink"
NAMES = ("qi", "foundation", "core", "soul")
METADATA = b'{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    failed = []
    for name in NAMES:
        data = (SOURCE / f"{name}.png").read_bytes()
        if data[:8] != b"\x89PNG\r\n\x1a\n":
            raise ValueError(f"Not a PNG: {name}")
        target = DESTINATION / f"{name}.png"
        if args.check:
            if not target.exists() or target.read_bytes() != data:
                failed.append(str(target.relative_to(ROOT)))
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        meta = target.with_suffix(".png.mcmeta")
        if args.check:
            if not meta.exists() or meta.read_bytes() != METADATA:
                failed.append(str(meta.relative_to(ROOT)))
        else:
            meta.write_bytes(METADATA)
        print(name, hashlib.sha256(data).hexdigest())
    if failed:
        raise SystemExit("Ink asset drift: " + ", ".join(failed))
    print("Approved ink assets match authored originals.")


if __name__ == "__main__":
    main()
