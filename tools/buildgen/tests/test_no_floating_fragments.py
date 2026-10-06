"""Guard: sect-family templates carry no floating block fragments.

A block is "floating" when none of its six face neighbours inside the template
is a non-air block. Such blocks read as debris hanging in the air in game (the
eave wing slabs and corner caps the owner flagged on the 山门 roofs).

Run from the repository root:
    python3 tools/buildgen/tests/test_no_floating_fragments.py
Pass ``--all`` to list floating blocks in every template (informational).
"""

from __future__ import annotations

import sys
from collections import Counter
from pathlib import Path
from typing import Dict, List, Tuple

_TOOLS_DIR = Path(__file__).resolve().parents[2]
if str(_TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(_TOOLS_DIR))

from buildgen.nbtread import read_gzipped_nbt  # noqa: E402

STRUCTURE_DIR = _TOOLS_DIR.parent / "src/main/resources/data/myvillage/structure"

SECT_FAMILY_PREFIXES = (
    "sect_gate", "sect_main_hall", "scripture_pavilion", "alchemy_room",
    "disciple_quarters", "bell_drum_tower", "pagoda", "pavilion",
    "cultivation_sect",
)

_AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air",
        "minecraft:structure_void"}
_FACES = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))

Pos = Tuple[int, int, int]


def floating_blocks(path: Path) -> List[Tuple[Pos, str]]:
    """Non-air blocks with no non-air block on any of their six faces."""
    _name, root = read_gzipped_nbt(str(path))
    palette = root.get("palette") or root.get("palettes", [[]])[0]
    names = [entry["Name"] for entry in palette]
    solid: Dict[Pos, str] = {}
    for block in root["blocks"]:
        name = names[int(block["state"])]
        if name in _AIR:
            continue
        x, y, z = (int(v) for v in block["pos"])
        solid[(x, y, z)] = name
    out = []
    for (x, y, z), name in solid.items():
        if not any((x + dx, y + dy, z + dz) in solid for dx, dy, dz in _FACES):
            out.append(((x, y, z), name))
    return sorted(out)


def sect_family_templates() -> List[Path]:
    return sorted(p for p in STRUCTURE_DIR.glob("*.nbt")
                  if p.stem.startswith(SECT_FAMILY_PREFIXES))


def test_sect_family_has_no_floating_fragments() -> None:
    paths = sect_family_templates()
    if len(paths) < 20:
        raise AssertionError(f"expected the sect family templates, found {len(paths)}")
    failures = []
    for path in paths:
        floating = floating_blocks(path)
        if floating:
            kinds = Counter(name.split(":", 1)[-1] for _pos, name in floating)
            sample = ", ".join(f"{pos}" for pos, _name in floating[:4])
            failures.append(f"{path.stem}: {len(floating)} floating "
                            f"{dict(kinds)} e.g. {sample}")
    if failures:
        raise AssertionError("floating block fragments:\n  " + "\n  ".join(failures))


def report_all() -> int:
    for path in sorted(STRUCTURE_DIR.glob("*.nbt")):
        floating = floating_blocks(path)
        if floating:
            kinds = Counter(name.split(":", 1)[-1] for _pos, name in floating)
            print(f"{path.stem}: {len(floating)} {dict(kinds)}")
    return 0


def main() -> int:
    if "--all" in sys.argv[1:]:
        return report_all()
    test_sect_family_has_no_floating_fragments()
    print(f"OK no floating fragments in {len(sect_family_templates())} sect-family templates")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
