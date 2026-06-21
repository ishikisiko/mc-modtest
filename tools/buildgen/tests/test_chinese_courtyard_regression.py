"""Regression guards for the isolated Chinese-courtyard rebuild.

Run from the repository root:
    python3 tools/buildgen/tests/test_chinese_courtyard_regression.py
"""

from __future__ import annotations

import hashlib
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
STRUCTURES = ROOT / "src/main/resources/data/myvillage/structure"
MEDIEVAL_ARCHETYPES = (
    "small_house", "medium_house", "blacksmith", "small_shop",
    "medium_shop", "big_house",
)


def _legacy_files() -> list[Path]:
    files = list(STRUCTURES.glob("cultivation_*.nbt"))
    for archetype in MEDIEVAL_ARCHETYPES:
        files.extend(STRUCTURES.glob(f"{archetype}_*.nbt"))
    return sorted(files)


def _hashes() -> dict[str, str]:
    return {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in _legacy_files()}


def main() -> int:
    before = _hashes()
    if not before:
        raise AssertionError("legacy cultivation/medieval structure libraries are missing")
    subprocess.run(
        [sys.executable, "tools/generate_compound_library.py", "--profile", "full"],
        cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
    after = _hashes()
    if before != after:
        changed = sorted(set(before) | set(after), key=str)
        changed = [name for name in changed if before.get(name) != after.get(name)]
        raise AssertionError(f"Chinese courtyard generation changed legacy NBTs: {changed}")
    print(f"OK legacy byte stability: {len(before)} cultivation/medieval NBTs unchanged")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
