#!/usr/bin/env python3
"""15族各四变体的真实方块展厅；不是正式城市或 runtime 资源。"""
from __future__ import annotations

import argparse
from pathlib import Path
import sys
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools"))
from province_capitals.regional.v2.buildings import Building
from province_capitals.regional.v2.common import Construction, LIBRARY, read_json, write_json
from province_capitals.regional.v2.preview import SceneBuilder, write_city_preview, write_combined_preview
from buildgen.style import Style


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=ROOT / "out/preview/province-capitals-v2/families")
    args = parser.parse_args()
    library = read_json(LIBRARY)
    style = Style(read_json(ROOT / "tools/buildgen/styles/province_capital_donghai.json"))
    scenes = []; inventory = []
    for family, spec in library["families"].items():
        construction = Construction([192, 64, 64]); buildings = []; fixtures = []
        for variant in range(4):
            source = dict(id=f"{family}_{variant}", name=f'{spec["name"]} · {variant}', family=family, role=spec["role"],
                          variant=variant, position=[24 + variant * 46, 8, 28], size=spec["sample"], stories=2, facing="south")
            b = Building(source, style, library=library).build(); b.emit(construction)
            buildings.append(b.summary()); fixtures.extend(b.fixtures)
        model = SimpleNamespace(width=192, height=64, depth=64, buildings=buildings, style=style,
                                city={"design_notes": f'{spec["name"]}，左至右为结构变体0..3；同尺寸、同材质、相同两层输入。'},
                                material=style.primary, ground=lambda x, z: 6)
        model.terrain = [6] * (192 * 64)
        scene = SceneBuilder(model)
        for tx, tz in sorted(construction.by_tile):
            scene.add_tile(tx, tz, construction.tile(tx, tz))
        bundle = {"schema": 1, "city_id": family, "region_id": "family_showcase", "display_name": spec["name"] + " · 四结构变体",
                  "size": [192, 64, 64], "entries": [{"position": buildings[0]["entrance"]}], "buildings": buildings,
                  "coverage": {"houses": 4 if spec["role"] == "house" else 0, "buildings": 4, "life_places": 0, "fixtures": len(fixtures)},
                  "unimplemented": ["仅为同源几何展厅，不是正式城市或游戏验收。"]}
        result = scene.finish(bundle)
        result["cameras"].update(panorama={"eye": [106, 112, 161], "target": [96, 12, 28]}, feature={"eye": [22, 23, 57], "target": [24, 12, 28]}, courtyard={"eye": [164, 24, 55], "target": [162, 12, 28]})
        write_city_preview(args.out / family, result); scenes.append(result)
        inventory.append({"family": family, "minimum": spec["minimum"], "actual_sample_size": spec["sample"], "variants": [{"id": b["variant"], "name": b["variant_name"], "geometry_sha256": b["geometry_sha256"]} for b in buildings]})
    write_combined_preview(args.out, scenes)
    write_json(args.out / "families.json", {"schema": 1, "families": inventory, "scope": "临时方块家族展厅；非正式资源"})
    print(f"15族×4结构变体已生成同源HTML：{args.out}")


if __name__ == "__main__":
    main()
