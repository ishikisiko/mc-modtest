#!/usr/bin/env python3
"""Copy exported 1.21.1 structure NBT files into a Minecraft world save.

Targets <world>/generated/myvillage/structures/ so structures can be loaded
in-game with: /place template myvillage:<structure_name>
"""

from __future__ import annotations

import argparse
import os
import shutil
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)

SUPPORTED_MC_VERSION = "1.21.1"
SOURCE_DIR = os.path.join(REPO_ROOT, "out", "1_21_1")
NAMESPACE = "myvillage"


def main() -> int:
    parser = argparse.ArgumentParser(description="Copy out/1_21_1/*.nbt into a world's generated structures")
    parser.add_argument("--world", required=True, help="Path to the Minecraft world save directory")
    parser.add_argument("--clean", action="store_true", help="Remove old .nbt files in the target directory first")
    parser.add_argument(
        "--version",
        default=SUPPORTED_MC_VERSION,
        help=f"Target Minecraft version (only {SUPPORTED_MC_VERSION} is supported)",
    )
    args = parser.parse_args()

    if args.version != SUPPORTED_MC_VERSION:
        print(f"ERROR: unsupported --version {args.version!r}; only {SUPPORTED_MC_VERSION} is supported", file=sys.stderr)
        return 1

    if not os.path.isdir(args.world):
        print(f"ERROR: world directory not found: {args.world}", file=sys.stderr)
        return 1

    if not os.path.isdir(SOURCE_DIR):
        print(f"ERROR: source directory not found: {SOURCE_DIR}; run tools/batch_export.py first", file=sys.stderr)
        return 1

    nbt_files = sorted(f for f in os.listdir(SOURCE_DIR) if f.endswith(".nbt"))
    if not nbt_files:
        print(f"ERROR: no .nbt files in {SOURCE_DIR}; run tools/batch_export.py first", file=sys.stderr)
        return 1

    target_dir = os.path.join(args.world, "generated", NAMESPACE, "structures")
    os.makedirs(target_dir, exist_ok=True)

    if args.clean:
        for filename in os.listdir(target_dir):
            if filename.endswith(".nbt"):
                os.remove(os.path.join(target_dir, filename))
                print(f"removed old {filename}")

    for filename in nbt_files:
        shutil.copy2(os.path.join(SOURCE_DIR, filename), os.path.join(target_dir, filename))
        name = os.path.splitext(filename)[0]
        print(f"copied {filename} -> /place template {NAMESPACE}:{name} ~ ~ ~")

    print(f"\n{len(nbt_files)} structure(s) copied to {target_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
