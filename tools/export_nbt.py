#!/usr/bin/env python3
"""Placeholder vanilla structure NBT exporter."""

from __future__ import annotations

import argparse
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("blueprint", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    raise SystemExit(
        f"NBT export is not implemented yet: blueprint={args.blueprint} output={args.output}"
    )


if __name__ == "__main__":
    main()

