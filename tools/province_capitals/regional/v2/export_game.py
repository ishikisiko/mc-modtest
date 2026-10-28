#!/usr/bin/env python3
"""正常导出七州 v2 schema1 资源与最终方块 HTML；不运行 Gradle/MC/云端。"""
from __future__ import annotations

import argparse
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import time

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools"))
from province_capitals.regional.v2.common import AIR, CITY_INPUT, LIBRARY, REGIONS, read_json, write_json
from province_capitals.regional.v2.city import Capital, load_city
from province_capitals.regional.v2.preview import SceneBuilder, write_city_preview, write_combined_preview
import json_to_nbt as nbt

DEFAULT_OUT = ROOT / "build/generated/province_capitals_v9/resources"
PREVIEW_OUT = ROOT / "out/preview/province-capitals-v2/final-blocks"


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def source_hashes(city):
    files = [CITY_INPUT / (city["region_id"] + ".json"), LIBRARY,
             ROOT / "tools/buildgen/styles" / (city["group_id"] + ".json"),
             ROOT / "blueprints/province_capitals/catalog.json"]
    files += list(Path(__file__).parent.glob("*.py")) + list(Path(__file__).parent.glob("*.js")) + list(Path(__file__).parent.glob("*.html"))
    files += [ROOT / "tools" / f for f in ("json_to_nbt.py", "buildgen/groups.py", "buildgen/ops.py", "buildgen/grid.py", "buildgen/massing.py", "buildgen/modset.py", "buildgen/style.py", "buildgen/orientation.py")]
    return {str(p.relative_to(ROOT)): sha(p) for p in sorted(set(files))}


def write_tile(path, tile, height):
    ordered = sorted(tile.items(), key=lambda row: (row[0][1], row[0][2], row[0][0]))
    palette = sorted({data[0] for _, data in ordered}); indices = {state: i for i, state in enumerate(palette)}
    blocks = [nbt.Compound({"pos": nbt.ListTag(nbt.TAG_INT, [nbt.Int(v) for v in pos]), "state": nbt.Int(indices[data[0]])}) for pos, data in ordered]
    root = nbt.Compound({"DataVersion": nbt.Int(nbt.DATA_VERSION_BY_MC_VERSION["1.21.1"]),
                         "size": nbt.ListTag(nbt.TAG_INT, [nbt.Int(v) for v in (16, height, 16)]),
                         "palette": nbt.ListTag(nbt.TAG_COMPOUND, [nbt.palette_state_to_tag(s) for s in palette]),
                         "blocks": nbt.ListTag(nbt.TAG_COMPOUND, blocks), "entities": nbt.ListTag(nbt.TAG_COMPOUND, [])})
    nbt.write_gzipped_nbt(root, str(path))
    return len(blocks), len(palette)


def family_stats(buildings):
    stats = {}
    for b in buildings:
        f = stats.setdefault(b["family"], {"count": 0, "variants": Counter(), "actual_sizes": Counter(), "stories": Counter(), "geometry_hashes": set()})
        f["count"] += 1; f["variants"][str(b["variant"]) ] += 1
        f["actual_sizes"]["×".join(map(str, b["size"]))] += 1; f["stories"][str(b["stories"]) ] += 1
        f["geometry_hashes"].add(b["geometry_sha256"])
    return {name: {**data, "geometry_hashes": sorted(data["geometry_hashes"])} for name, data in sorted(stats.items())}


def export_city(city, out, preview_out):
    started = time.monotonic(); hashes = source_hashes(city)
    model = Capital(city).construct()
    city_id = city["city_id"]
    data_dir = out / "data/myvillage/capitals/v2" / city_id
    structure_dir = out / "data/myvillage/structure/province_capitals/v2" / city_id
    data_dir.mkdir(parents=True, exist_ok=True); structure_dir.mkdir(parents=True, exist_ok=True)
    terrain_path = data_dir / "terrain.bin.gz"
    raw = struct.pack("<" + "h" * len(model.terrain), *model.terrain)
    terrain_path.write_bytes(gzip.compress(raw, mtime=0))
    scene = SceneBuilder(model); tiles = []; counts = Counter(); palette = set()
    for tx, tz in sorted(model.construction.by_tile, key=lambda p: (p[1], p[0])):
        tile = model.construction.tile(tx, tz)
        if not tile:
            continue
        path = structure_dir / f"tile_{tx}_{tz}.nbt"
        block_count, palette_count = write_tile(path, tile, model.height)
        tiles.append({"x": tx * 16, "z": tz * 16, "template": f"myvillage:province_capitals/v2/{city_id}/tile_{tx}_{tz}",
                      "sha256": sha(path), "bytes": path.stat().st_size, "block_count": block_count, "palette_count": palette_count})
        counts["blocks"] += block_count; counts["air"] += sum(row[0] == AIR for row in tile.values()); counts["bytes"] += path.stat().st_size
        palette.update(row[0] for row in tile.values()); scene.add_tile(tx, tz, tile)
    errors = model.modset.palette_block_errors(sorted(palette))
    if errors:
        raise ValueError("; ".join(errors))
    if source_hashes(city) != hashes:
        raise RuntimeError(f"{city_id} 生成期间输入变动；保留失败，不发布混合来源清单")
    previous_path = data_dir / "city.json"
    if previous_path.is_file():
        previous = read_json(previous_path); keep = {row["template"] for row in tiles}
        prefix = f"myvillage:province_capitals/v2/{city_id}/"
        for row in previous.get("tiles", []):
            leaf = row["template"].removeprefix(prefix)
            if row["template"].startswith(prefix) and row["template"] not in keep and re.fullmatch(r"tile_\d+_\d+", leaf):
                old = structure_dir / (leaf + ".nbt")
                if old.is_file():
                    old.unlink()
    catalog = next(c for c in read_json(ROOT / "blueprints/province_capitals/catalog.json")["capitals"] if c["region_id"] == city["region_id"])
    coverage = {"houses": sum(b["role"] == "house" for b in model.buildings), "buildings": len(model.buildings),
                "service_roles": ["inn", "apothecary", "smith", "market", "guide"], "landmarks": sum(b["role"] == "landmark" for b in model.buildings),
                "templates": len(tiles), "recorded_blocks": counts["blocks"], "explicit_air": counts["air"], "template_bytes": counts["bytes"],
                "life_places": len(model.places), "fixtures": len(model.fixtures), "palette_states": len(palette)}
    bundle = {"schema": 1, "format": "capital_game_bundle", "city_id": city_id, "region_id": city["region_id"],
              "display_name": city["name"], "layout_family_id": model.group.scale_params["layout_family_id"],
              "appearance_id": catalog["appearance_id"] + "_v2", "group_id": city["group_id"], "style_id": model.group.style_id,
              "size": city["dimensions"], "source_offset": city["source_offset"], "placement": city["placement"],
              "terrain": {"resource": f"myvillage:capitals/v2/{city_id}/terrain.bin.gz", "sha256": sha(terrain_path),
                          "encoding": "i16le_z_major_top_solid", "width": model.width, "depth": model.depth,
                          "top_block": model.material("GROUND_TOP").split("[", 1)[0], "fill_block": model.material("GROUND_FILL").split("[", 1)[0],
                          "subsurface_block": model.material("GROUND_SUBSURFACE").split("[", 1)[0], "subsurface_depth": 3,
                          "minimum_top_solid_y": min(model.terrain), "maximum_top_solid_y": max(model.terrain), "bytes": terrain_path.stat().st_size, "uncompressed_bytes": len(raw)},
              "tiles": tiles, "entries": model.entries,
              "life": {"layout_id": city_id + "_blocks_v2", "name": city["name"], "places": model.places, "fixtures": model.fixtures,
                       "binding": "runtime_owned_not_bound", "resident_source_hint": city.get("resident_source_hint", "runtime_worldsim_city_population_required")},
              "buildings": model.buildings, "roads": model.roads, "districts": city.get("districts", []), "access_paths": model.access,
              "palette_states": sorted(palette), "coverage": coverage,
              "design": {"family_statistics": family_stats(model.buildings), "cameras": city.get("design_cameras", []), "diagnostics": model.diagnostics},
              "source": {"authored": [str((CITY_INPUT / (city["region_id"] + ".json")).relative_to(ROOT)), str(LIBRARY.relative_to(ROOT))],
                         "generator": "tools/province_capitals/regional/v2/export_game.py", "hashes": hashes,
                         "coordinates": "+X东/+Y上/+Z南；position.y为首层空气；size含地坪屋檐与前场，完整保持奇偶整数。",
                         "quantization": "局部lot=[0,w)×[0,d)，减floor(w/2),floor(d/2)后围绕position旋转；不改变尺寸或另取中心。",
                         "terrain_semantics": "top_solid_y为自然/整地最高实心格；实际地坪、道路阶梯、桥、水和地基由稀疏NBT覆盖。",
                         "modset": {"profile": model.modset.name, "resolver": "tools/buildgen/modset.py", "registry_validation": "not_run"}},
              "unimplemented": ["原生碰撞、NPC寻路与岗位绑定等待父会话基础Colab验收。", "玩家外观、手感、音效、FPS和多人未验收。", "本轮不进行交易细节验收；离线相机不模拟碰撞，方块名配色不等于游戏纹理。"],
              "status": {"generation": "resource_export_executed", "basic_geometry": "passed", "tests": "not_run", "compilation": "not_run", "gradle": "not_run", "minecraft": "not_run", "cloud": "not_run", "browser": "not_run", "owner_appearance": "not_verified", "npc_binding": "not_verified", "fps": "not_verified", "multiplayer": "not_verified"},
              "contract": {"name": "capital_game_bundle", "schema": 1, "deviations": [], "tile_xz_semantics": "block_offset_from_city_origin"}}
    # 与真实 V9 聚合器共享既有 schema1 life 边界，绝不放宽192预算。
    from province_capitals.game.compile import life_inputs
    life_inputs(bundle, model.width, model.height, model.depth, 192)
    write_json(previous_path, bundle)
    result = scene.finish(bundle); write_city_preview(preview_out / city["region_id"], result)
    print(f'{city_id}: buildings={len(model.buildings)} houses={coverage["houses"]} life={len(model.places)} tiles={len(tiles)} NBT={counts["bytes"]} bytes elapsed={time.monotonic()-started:.2f}s', flush=True)
    return bundle, result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--preview-out", type=Path, default=PREVIEW_OUT)
    parser.add_argument("--regions", nargs="+", choices=REGIONS, default=list(REGIONS))
    args = parser.parse_args()
    if len(set(args.regions)) != len(args.regions):
        parser.error("--regions 不得重复")
    args.out = args.out.resolve(); args.preview_out = args.preview_out.resolve()
    bundles = []; scenes = []
    for region in args.regions:
        bundle, scene = export_city(load_city(region), args.out, args.preview_out)
        bundles.append(bundle); scenes.append(scene)
    write_combined_preview(args.preview_out, scenes)
    inventory = {"schema": 1, "owner": "regional_v2", "resource_root": str(args.out),
                 "generation_command": "python3 tools/province_capitals/regional/v2/export_game.py --out " + str(args.out) + " --regions " + " ".join(args.regions),
                 "regions": args.regions, "cities": [{"city_id": b["city_id"], "city_json": str(args.out / "data/myvillage/capitals/v2" / b["city_id"] / "city.json"), "size": b["size"], "coverage": b["coverage"], "family_statistics": b["design"]["family_statistics"], "source_hashes": b["source"]["hashes"]} for b in bundles],
                 "minecraft": "not_run", "owner_appearance": "not_verified"}
    write_json(args.preview_out / "resource-inventory.json", inventory)
    print(f"v2 正常导出完成：{args.out}；同源预览：{args.preview_out}；原生与玩家验收未执行。", flush=True)


if __name__ == "__main__":
    main()
