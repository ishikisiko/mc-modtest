"""Resource export layer (resource_export_pass).

Writes vanilla structure NBT (via tools/json_to_nbt.py, the same writer used
by test_house_01/02) plus gallery and single-place mcfunctions directly into
the mod resources tree.
"""

from __future__ import annotations

import json
import os
import sys
from typing import Dict, List, Tuple

_TOOLS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _TOOLS_DIR not in sys.path:
    sys.path.insert(0, _TOOLS_DIR)

import json_to_nbt  # noqa: E402

from .grid import BlockGrid  # noqa: E402
from .groups import get_group  # noqa: E402

PROJECT_ROOT = os.path.dirname(_TOOLS_DIR)
MOD_ID = "myvillage"
RESOURCES = os.path.join(PROJECT_ROOT, "src", "main", "resources", "data", MOD_ID)


def repo_relpath(path: str, root: str = PROJECT_ROOT) -> str:
    """Repo-relative path as a POSIX string (forward slashes) for stable
    cross-platform JSON reports and human-facing output.

    os.path.relpath yields native separators ("\\" on Windows, "/" elsewhere),
    which makes committed reports flip between platforms. Using POSIX here
    keeps generated reports and CLI prints byte-identical across Linux/Windows.
    The caller never feeds these strings back to open(); when something does
    need a real disk path it keeps the original Path/str.
    """
    return os.path.relpath(path, root).replace(os.sep, "/")


def gallery_group(archetype: str, name: str = "", group_id: str = "") -> str:
    if group_id:
        group = get_group(group_id)
        return str(group.scale_params.get("gallery_group", group.group_id))
    key = name or archetype
    if key.startswith(("small_shop", "medium_shop")) or archetype in ("small_shop", "medium_shop"):
        return "shop"
    if key.startswith(("small_house", "medium_house", "big_house")) or archetype in ("small_house", "medium_house", "big_house"):
        return "house"
    if key.startswith("blacksmith") or archetype == "blacksmith":
        return "blacksmith"
    if archetype == "chinese_courtyard":
        return "chinese_courtyard"
    if key.startswith(("tavern", "lord_manor")) or archetype in ("tavern", "lord_manor"):
        return "civic"
    if key.endswith("_review"):
        return "chinese_review"
    if key.startswith("test_"):
        return "test"
    return archetype


def placement_y_offset(name: str) -> int:
    """Generated structures include terrain-replacement cells one layer below.

    Placing them at Y-1 keeps building floors/stairs at the requested origin
    while water, planting, and entry hardscape replace the terrain block.
    """
    return 0 if name.startswith("test_") else -1


def _rel(value: int) -> str:
    return "~" if value == 0 else f"~{value}"


def grid_to_structure_data(grid: BlockGrid) -> dict:
    """Normalize the grid to origin and emit the json_to_nbt block list."""
    size = grid.normalized()
    blocks = [{"pos": [x, y, z], "state": cell.state}
              for (x, y, z), cell in sorted(grid.iter_cells())]
    return {"size": list(size), "blocks": blocks,
            "entities": grid.entities,
            "author": "generate_building_library.py"}


def write_structure_nbt(grid: BlockGrid, style_id: str, name: str) -> Tuple[str, dict]:
    data = grid_to_structure_data(grid)
    out_dir = os.path.join(RESOURCES, "structure")
    path = os.path.join(out_dir, f"{name}.nbt")
    root = json_to_nbt.structure_json_to_root_nbt(data)
    json_to_nbt.write_gzipped_nbt(root, path)
    info = {
        "size": data["size"],
        "block_count": len(root.value["blocks"].value),
        "entity_count": len(root.value["entities"].value),
        "palette_count": len(root.value["palette"].value),
        "path": repo_relpath(path),
    }
    return path, info


def write_settlement_metadata(name: str, metadata: dict) -> str:
    out = os.path.join(RESOURCES, "settlement_meta", f"{name}.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2, ensure_ascii=False)
        f.write("\n")
    return out


def write_gallery_function(style_id: str, entries: List[dict],
                           spacing_x: int = 28, spacing_z: int = 36) -> str:
    """One mcfunction placing every building in archetype columns."""
    lines = [
        f"# auto-generated building library gallery: {style_id}",
        f"# usage: /function {MOD_ID}:gallery/{style_id}",
    ]
    rows: Dict[str, List[dict]] = {}
    for e in entries:
        rows.setdefault(
            gallery_group(e["archetype"], e["name"], e.get("group_id", "")),
            []).append(e)
    x = 0
    for archetype in sorted(rows):
        lines.append(f"# --- {archetype} ---")
        z = 0
        for e in rows[archetype]:
            lines.append(f"place template {MOD_ID}:{e['name']} "
                         f"~{x} {_rel(placement_y_offset(e['name']))} ~{z}")
            z += spacing_z
        x += spacing_x
    out = os.path.join(RESOURCES, "function", "gallery", f"{style_id}.mcfunction")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    return out


def write_civic_gallery_function(entries: List[dict]) -> str:
    return write_gallery_function("civic", entries, spacing_x=60, spacing_z=60)


def write_place_function(style_id: str, name: str) -> str:
    out = os.path.join(RESOURCES, "function", "place", f"{name}.mcfunction")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write(f"place template {MOD_ID}:{name} ~ {_rel(placement_y_offset(name))} ~\n")
    return out
