#!/usr/bin/env python3
"""Validate plaque frame, inscription, bucket, and mount bindings."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
DEFAULT_REPORT = REPO_ROOT / "reports" / "plaque_binding_validation.json"

if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from buildgen.gen_plaque_assets import (  # noqa: E402
    ASSET_ROOT,
    DEFAULT_MANIFEST,
    draw_full_plaque_rgba,
    read_manifest,
    read_png_rgba,
)
from buildgen.plaque_bindings import Binding, BindingValidationError, BINDINGS_PATH, load_bindings  # noqa: E402


def _diff_pixel_count(actual: bytes, base: bytes) -> int:
    changed = 0
    for i in range(0, len(actual), 4):
        rgb_delta = abs(actual[i] - base[i]) + abs(actual[i + 1] - base[i + 1]) + abs(actual[i + 2] - base[i + 2])
        if actual[i + 3] > 0 and rgb_delta >= 36:
            changed += 1
    return changed


def _texture_check(binding: Binding, preset: dict) -> dict:
    changed_pixels = 0
    errors: list[str] = []
    part = "horizontal_full" if binding.orientation == "horizontal" else "vertical_full"
    texture_path = ASSET_ROOT / "textures" / "block" / "plaque" / binding.mount / binding.frame / f"{part}.png"
    texture_width = texture_height = 0
    if not texture_path.is_file():
        errors.append(f"missing_generated_plaque_texture: {texture_path.relative_to(REPO_ROOT)}")
    else:
        texture_width, texture_height, actual = read_png_rgba(texture_path)
        base_width = binding.width * 16
        base_height = binding.height * 16
        if texture_width % base_width != 0 or texture_height % base_height != 0:
            errors.append(
                f"invalid_generated_plaque_texture_size: {texture_path.relative_to(REPO_ROOT)} "
                f"{texture_width}x{texture_height}"
            )
        else:
            texture_scale = texture_width // base_width
            if texture_height // base_height != texture_scale:
                errors.append(
                    f"non_uniform_generated_plaque_texture_scale: {texture_path.relative_to(REPO_ROOT)} "
                    f"{texture_width}x{texture_height}"
                )
            else:
                _bw, _bh, base = draw_full_plaque_rgba(
                    preset,
                    binding.mount,
                    binding.width,
                    binding.height,
                    binding.orientation,
                    inscription=None,
                    texture_scale=texture_scale,
                )
                changed_pixels = _diff_pixel_count(actual, base)

    total_pixels = texture_width * texture_height
    coverage = changed_pixels / total_pixels if total_pixels else 0.0
    if changed_pixels < max(24, int(total_pixels * 0.01)):
        errors.append(
            f"plaque_inscription_not_visible: frame={binding.frame} mount={binding.mount} "
            f"orientation={binding.orientation} changed_pixels={changed_pixels} coverage={coverage:.3f}"
        )
    if coverage > 0.35:
        errors.append(
            f"plaque_inscription_overfilled: frame={binding.frame} mount={binding.mount} "
            f"orientation={binding.orientation} changed_pixels={changed_pixels} coverage={coverage:.3f}"
        )
    return {
        "frame": binding.frame,
        "mount": binding.mount,
        "orientation": binding.orientation,
        "inscription": binding.inscription.variant,
        "texture": str(texture_path.relative_to(REPO_ROOT)),
        "texture_size": [texture_width, texture_height],
        "changed_pixels": changed_pixels,
        "coverage": round(coverage, 4),
        "errors": errors,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", default=str(DEFAULT_REPORT))
    args = parser.parse_args()
    report_path = Path(args.report)
    if not report_path.is_absolute():
        report_path = (REPO_ROOT / report_path).resolve()

    errors: list[str] = []
    bindings = {}
    texture_checks = []
    try:
        loaded = load_bindings()
        bindings = {
            archetype: [
                {
                    "frame": binding.frame,
                    "orientation": binding.orientation,
                    "mount": binding.mount,
                    "bucket": binding.bucket,
                    "inscription": binding.inscription.variant,
                    "width": binding.width,
                    "height": binding.height,
                }
                for binding in entries
            ]
            for archetype, entries in sorted(loaded.items())
        }
        manifest = read_manifest(DEFAULT_MANIFEST)
        presets = {preset["id"]: preset for preset in manifest["presets"]}
        checked: set[tuple[str, str, str]] = set()
        for entries in loaded.values():
            for binding in entries:
                key = (binding.frame, binding.mount, binding.orientation)
                if key in checked:
                    continue
                checked.add(key)
                preset = presets.get(binding.frame)
                if preset is None:
                    errors.append(f"unknown_frame_preset_for_texture_check: {binding.frame}")
                    continue
                check = _texture_check(binding, preset)
                texture_checks.append(check)
                errors.extend(check["errors"])
    except BindingValidationError as exc:
        errors.extend(exc.errors)
    except Exception as exc:
        errors.append(f"plaque_binding_parse: {exc}")

    report = {
        "bindings": str(BINDINGS_PATH.relative_to(REPO_ROOT)),
        "passed": not errors,
        "binding_count": sum(len(entries) for entries in bindings.values()),
        "archetype_count": len(bindings),
        "bindings_by_archetype": bindings,
        "texture_checks": texture_checks,
        "errors": errors,
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    print(f"validated plaque bindings: {report['binding_count']} entries across {report['archetype_count']} archetypes")
    print(f"report: {report_path.relative_to(REPO_ROOT)}")
    for error in errors:
        print(f"- {error}")
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
