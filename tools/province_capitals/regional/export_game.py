#!/usr/bin/env python3
"""Export the seven regional capital_game_bundle resources (not a test runner)."""
from __future__ import annotations

import argparse
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import struct
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "tools"))
import json_to_nbt as nbt
from block_model import AIR, Capital, load_inputs

DEFAULT_OUT = ROOT / "build/generated/province_capitals/resources"
PREVIEW_OUT = ROOT / "out/preview/province-capitals/regional-game"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path, data, compact=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=None if compact else 2,
                               separators=(",", ":") if compact else None) + "\n", encoding="utf-8")


def source_hashes(city):
    files = [
        "blueprints/province_capitals/regional/game-cities.json",
        "blueprints/province_capitals/catalog.json",
        "tools/buildgen/groups.py", "tools/buildgen/ops.py",
        "tools/buildgen/grid.py", "tools/buildgen/massing.py",
        "tools/buildgen/modset.py", "tools/buildgen/orientation.py",
        "tools/buildgen/style.py", "tools/json_to_nbt.py",
        "tools/province_capitals/regional/block_model.py",
        "tools/province_capitals/regional/forms.py",
        "tools/province_capitals/regional/export_game.py",
        "tools/buildgen/styles/" + city["group_id"] + ".json",
    ]
    if "layout_source" in city:
        files.append(city["layout_source"])
    # The existing vanilla modset intentionally has no external catalogue
    # dependency.  Do not invent a digest for an absent optional checkout file.
    if (ROOT / "exmod/mod_block_catalog.json").is_file():
        files.append("exmod/mod_block_catalog.json")
    return {name: sha(ROOT / name) for name in sorted(set(files))}


def write_tile(path, tile, height):
    """Use the existing NBT tag/writer API, retaining sparse explicit air."""
    ordered = sorted(tile.items(), key=lambda item: (item[0][1], item[0][2], item[0][0]))
    palette = sorted({data[0] for _, data in ordered})
    indices = {state: i for i, state in enumerate(palette)}
    blocks = [nbt.Compound({"pos": nbt.ListTag(nbt.TAG_INT, [nbt.Int(v) for v in pos]),
                            "state": nbt.Int(indices[data[0]])}) for pos, data in ordered]
    root = nbt.Compound({"DataVersion": nbt.Int(nbt.DATA_VERSION_BY_MC_VERSION["1.21.1"]),
                         "size": nbt.ListTag(nbt.TAG_INT, [nbt.Int(v) for v in (16, height, 16)]),
                         "palette": nbt.ListTag(nbt.TAG_COMPOUND, [nbt.palette_state_to_tag(s) for s in palette]),
                         "blocks": nbt.ListTag(nbt.TAG_COMPOUND, blocks),
                         "entities": nbt.ListTag(nbt.TAG_COMPOUND, [])})
    nbt.write_gzipped_nbt(root, str(path))
    return len(blocks), len(palette)


def export_city(city, inputs, definition, out, preview_out):
    from game_preview import SceneBuilder, write_city_preview
    model = Capital(city, inputs["modset"]).construct()
    city_id = city["city_id"]
    data_dir = out / "data/myvillage/capitals/v1" / city_id
    structure_dir = out / "data/myvillage/structure/province_capitals/v1" / city_id
    data_dir.mkdir(parents=True, exist_ok=True)
    structure_dir.mkdir(parents=True, exist_ok=True)
    terrain_path = data_dir / "terrain.bin.gz"
    raw = struct.pack("<" + "h" * len(model.terrain), *model.terrain)
    terrain_path.write_bytes(gzip.compress(raw, mtime=0))
    scene = SceneBuilder(model)
    tiles = []
    counts = Counter()
    palette = set()
    for tx, tz in sorted(model.construction.by_tile, key=lambda p: (p[1], p[0])):
        tile = model.construction.tile(tx, tz)
        if not tile:
            continue
        tile_path = structure_dir / f"tile_{tx}_{tz}.nbt"
        blocks, palette_count = write_tile(tile_path, tile, model.height)
        tiles.append({"x": tx * 16, "z": tz * 16,
                      "template": f"myvillage:province_capitals/v1/{city_id}/tile_{tx}_{tz}",
                      "sha256": sha(tile_path), "bytes": tile_path.stat().st_size,
                      "block_count": blocks, "palette_count": palette_count})
        counts["blocks"] += blocks
        counts["air"] += sum(v[0] == AIR for v in tile.values())
        counts["template_bytes"] += tile_path.stat().st_size
        palette.update(v[0] for v in tile.values())
        scene.add_tile(tx, tz, tile)
        # The next iteration releases the only 16×height×16 block dictionary.
    # Only remove obsolete files from THIS city and the prior owning manifest.
    # A sibling city's output or an unknown file in the root is never touched.
    previous = data_dir / "city.json"
    if previous.exists():
        old = json.loads(previous.read_text(encoding="utf-8"))
        retained = {t["template"] for t in tiles}
        own_prefix = f"myvillage:province_capitals/v1/{city_id}/"
        for tile in old.get("tiles", []):
            name = tile.get("template", "")
            leaf = name.removeprefix(own_prefix)
            if name.startswith(own_prefix) and name not in retained and "/" not in leaf and leaf.startswith("tile_"):
                stale = structure_dir / (leaf + ".nbt")
                if stale.is_file():
                    stale.unlink()
    hashes = source_hashes(city)
    bundle = {
        "schema": 1, "format": "capital_game_bundle", "city_id": city_id,
        "region_id": city["region_id"], "display_name": definition["display_name"],
        "layout_family_id": definition["layout_id"], "appearance_id": definition["appearance_id"],
        "group_id": city["group_id"], "style_id": model.group.style_id,
        "size": city["dimensions"], "source_offset": city["source_offset"],
        "placement": city["placement"],
        "terrain": {"resource": f"myvillage:capitals/v1/{city_id}/terrain.bin.gz", "sha256": sha(terrain_path),
                    "encoding": "i16le_z_major_top_solid", "width": model.width, "depth": model.depth,
                    "top_block": model.material("GROUND_TOP"), "fill_block": model.material("GROUND_FILL"),
                    "subsurface_block": model.material("GROUND_SUBSURFACE"), "subsurface_depth": 3,
                    "minimum_top_solid_y": min(model.terrain), "maximum_top_solid_y": max(model.terrain),
                    "bytes": terrain_path.stat().st_size, "uncompressed_bytes": len(raw)},
        "tiles": tiles, "entries": model.entries,
        "life": {"layout_id": city_id + "_blocks_v1", "name": definition["display_name"], "places": model.places,
                 "binding": "runtime_owned_not_bound", "fixtures": model.fixtures,
                 "resident_source_hint": city.get("resident_source_hint", "runtime_worldsim_city_population_required")},
        "buildings": model.buildings, "roads": model.roads,
        "palette_states": sorted(palette),
        "source": {"authored": ["blueprints/province_capitals/regional/game-cities.json"] + ([city["layout_source"]] if "layout_source" in city else []),
                   "generator": "tools/province_capitals/regional/export_game.py", "hashes": hashes,
                   "coordinates": "+X东/+Y上/+Z南；局部整数格；source_offset为规范化前来源偏移",
                   "quantization": "原预览中心逐格取整；建筑宽深取覆盖中心的奇数格；一源单位对应一游戏格，无OBJ表面体素化。",
                   "terrain_semantics": "top_solid_y是地基/河床最高实心格；建筑、桥面和水由NBT叠加。",
                   "modset": {"profile": model.modset.name,
                              "resolver": "tools/buildgen/modset.py",
                              "external_catalogue": "exmod/mod_block_catalog.json" if (ROOT / "exmod/mod_block_catalog.json").is_file() else None,
                              "external_catalogue_required": model.modset.name != "vanilla",
                              "palette_policy": "vanilla profile has no external namespace or external block dependency; registry validation not_run"}},
        "coverage": {"houses": sum(b["role"] == "house" for b in model.buildings),
                     "buildings": len(model.buildings), "service_roles": ["inn", "apothecary", "smith", "market", "guide"],
                     "landmarks": sum(b["role"] == "landmark" for b in model.buildings),
                     "templates": len(tiles), "recorded_blocks": counts["blocks"], "explicit_air": counts["air"],
                     "template_bytes": counts["template_bytes"], "life_places": len(model.places),
                     "fixtures": len(model.fixtures), "palette_states": len(palette)},
        "unimplemented": ["NPC居民、岗位分配、商贸、住宿与服务运行由runtime接入，当前没有NPC绑定完成证据。",
                          "通行碰撞、楼梯寻路、自然地貌过渡、世界高度适配和游戏注册有效性尚未测试。",
                          "完整住户装饰、药圃生产玩法、潮汐/天气及动态排水没有实现。",
                          "浏览器、玩家外观、手感、音效、FPS和多人验收没有执行。"],
        "status": {"generation": "resource_export_executed", "tests": "not_run", "unit_tests": "not_run",
                   "validator": "not_run", "lint_check": "not_run", "compilation": "not_run", "gradle": "not_run",
                   "minecraft": "not_run", "cloud": "not_run", "browser": "not_run",
                   "owner_appearance": "not_verified", "npc_binding": "not_verified",
                   "collision_walkability": "not_verified", "fps": "not_verified", "multiplayer": "not_verified"},
        "contract": {"name": "capital_game_bundle", "schema": 1, "deviations": [],
                     "tile_xz_semantics": "block_offset_from_city_origin"},
    }
    write_json(data_dir / "city.json", bundle)
    preview = scene.finish(bundle)
    write_city_preview(preview_out / city["region_id"], preview)
    print(f"{city_id}: city.json={(data_dir / 'city.json').stat().st_size} bytes templates={len(tiles)} NBT={counts['template_bytes']} bytes terrain={terrain_path.stat().st_size} bytes houses={bundle['coverage']['houses']} life={len(model.places)}", flush=True)
    return bundle, preview


def main():
    parser = argparse.ArgumentParser(description="生成七州正式方块资源；不运行测试、检查或编译")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--preview-out", type=Path, default=PREVIEW_OUT)
    args = parser.parse_args()
    inputs, cities = load_inputs()
    definitions = {c["region_id"]: c for c in json.loads((ROOT / inputs["catalog_source"]).read_text(encoding="utf-8"))["capitals"]}
    bundles, scenes = [], []
    for city in cities:
        bundle, scene = export_city(city, inputs, definitions[city["region_id"]], args.out, args.preview_out)
        bundles.append(bundle)
        scenes.append(scene)
    from game_preview import write_combined_preview
    write_combined_preview(args.preview_out, scenes)
    # Regional inventory is a preview/handoff artifact, never the shared index.
    inventory = {"schema": 1, "owner": "regional", "resource_root": str(args.out),
                 "generation_command": "python3 tools/province_capitals/regional/export_game.py --out " + str(args.out),
                 "cities": [{"city_id": b["city_id"], "city_json": str(args.out / "data/myvillage/capitals/v1" / b["city_id"] / "city.json"),
                             "size": b["size"], "coverage": b["coverage"], "terrain_bytes": b["terrain"]["bytes"],
                             "city_json_bytes": (args.out / "data/myvillage/capitals/v1" / b["city_id"] / "city.json").stat().st_size,
                             "source_hashes": b["source"]["hashes"]} for b in bundles],
                 "all_tests": "not_run", "owner_appearance": "not_verified", "contract_deviations": []}
    write_json(args.preview_out / "resource-inventory.json", inventory)
    print(f"七城正式资源已正常导出到 {args.out}；审阅产物 {args.preview_out}。所有测试 not_run。", flush=True)


if __name__ == "__main__":
    main()
