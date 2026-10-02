"""Command line for the offline combat previews. See tools/combat_preview/README.md.

    python3 -m tools.combat_preview fp|pose|model [options]     (<tool> -h for its options)
"""
from __future__ import annotations

import importlib
import sys

from .env import ensure_interpreter

TOOLS = {
    "fp": ("fp_rig", "first-person frames of a weapon's rig (item model, arm or arms, trail) at any tick"),
    "pose": ("pal_pose", "third-person PAL poses from an animation file with the item in the hand"),
    "model": ("item_model", "an item model in each display context"),
}
USAGE = "usage: python3 -m tools.combat_preview {" + "|".join(TOOLS) + "} [options]"


def usage() -> str:
    lines = [USAGE, ""] + [f"  {name:6s} {about}" for name, (_, about) in TOOLS.items()]
    lines += ["", "Run '<tool> -h' for a tool's options. See tools/combat_preview/README.md."]
    return "\n".join(lines)


def main(argv=None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    if not argv or argv[0] in ("-h", "--help"):
        print(usage())
        return 0 if argv else 2
    name, rest = argv[0], argv[1:]
    if name not in TOOLS:
        print(f"unknown tool {name!r}\n\n{usage()}", file=sys.stderr)
        return 2
    ensure_interpreter(argv)
    module = importlib.import_module(f"{__package__}.{TOOLS[name][0]}")
    return module.main(rest)
