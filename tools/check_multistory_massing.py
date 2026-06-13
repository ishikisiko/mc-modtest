#!/usr/bin/env python3
"""Assert that explicit one-story massing matches legacy main-volume massing."""

from __future__ import annotations

import os
import random
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

from buildgen.archetypes import _main_volume  # noqa: E402
from buildgen.massing import MassingGraph  # noqa: E402
from buildgen.style import load_style  # noqa: E402


def main() -> int:
    style = load_style("medieval_village")
    legacy = MassingGraph()
    explicit = MassingGraph()
    _main_volume(legacy, style, random.Random(17), "small", 4, 1)
    _main_volume(explicit, style, random.Random(17), "small", 4, 1,
                 stories=1, story_wall_h=4)
    assert legacy.to_dict() == explicit.to_dict()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
