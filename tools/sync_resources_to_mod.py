"""Sync generated datapack resources into the mod's resources tree.

Copies (stdlib only, run automatically by `./gradlew build` via the
syncGeneratedResources task):

    generated/myvillage/structures/*.nbt
        -> src/main/resources/data/myvillage/structure/
    generated/myvillage/data/myvillage/function/*.mcfunction
        -> src/main/resources/data/myvillage/function/

If the generated/myvillage layout does not exist in this worktree, falls back
to the project's actual output locations:

    out/1_21_1/*.nbt
    out/datapack_phase5_gallery/data/myvillage/function/*.mcfunction
"""

from __future__ import annotations

import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

STRUCTURE_SOURCES = [
    os.path.join(ROOT, "generated", "myvillage", "structures"),
    os.path.join(ROOT, "out", "1_21_1"),
]
FUNCTION_SOURCES = [
    os.path.join(ROOT, "generated", "myvillage", "data", "myvillage", "function"),
    os.path.join(ROOT, "generated", "myvillage", "data", "myvillage", "functions"),
    os.path.join(ROOT, "out", "datapack_phase5_gallery", "data", "myvillage", "function"),
]

STRUCTURE_DEST = os.path.join(ROOT, "src", "main", "resources", "data", "myvillage", "structure")
FUNCTION_DEST = os.path.join(ROOT, "src", "main", "resources", "data", "myvillage", "function")


def sync(sources, dest, ext):
    src_dir = next((d for d in sources if os.path.isdir(d)), None)
    if src_dir is None:
        print(f"warn: no source dir found for *{ext}, skipping ({sources[0]} ...)")
        return 0
    files = sorted(f for f in os.listdir(src_dir) if f.endswith(ext))
    os.makedirs(dest, exist_ok=True)
    # remove stale files of this type so deleted structures don't linger in the jar
    for f in os.listdir(dest):
        if f.endswith(ext) and f not in files:
            os.remove(os.path.join(dest, f))
    for f in files:
        shutil.copy2(os.path.join(src_dir, f), os.path.join(dest, f))
    rel = os.path.relpath(src_dir, ROOT)
    print(f"synced {len(files)} *{ext} from {rel} -> {os.path.relpath(dest, ROOT)}")
    return len(files)


def main():
    n = sync(STRUCTURE_SOURCES, STRUCTURE_DEST, ".nbt")
    m = sync(FUNCTION_SOURCES, FUNCTION_DEST, ".mcfunction")
    if n == 0:
        print("error: no structure NBT files synced", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
