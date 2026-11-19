#!/usr/bin/env python3
"""Draw the H panel maps from the current capital heightfields and sparse structure tiles."""
from __future__ import annotations

import argparse
from array import array
from collections import defaultdict
import gzip
import json
from pathlib import Path
import sys

from buildgen.nbtread import read_gzipped_nbt
from gen_meridian_figure import encode_png
from province_capitals.game.compile import resource_file

ASSETS = Path("assets/yuanxian/textures/gui/town_map")
DATA = Path("data/yuanxian/town_maps")
FROZEN = Path("data/yuanxian/worldplan/frozen_v11.json")
MAX_PIXELS = 448
SERVICES = {
    "apothecary": ("药铺", "apothecary_entry"),
    "smith": ("铁作坊", "smith_entry"),
    "inn": ("客栈", "inn_entry"),
    "guide": ("向导亭", "guide_entry"),
}
AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def services(city: dict) -> list[dict]:
    buildings = {building["id"]: building for building in city.get("buildings", [])}
    places = {place["id"]: place for place in city["life"]["places"]}
    width, height, depth = city["size"]
    result = []
    for kind, (default_name, entry_id) in SERVICES.items():
        building = buildings.get(kind)
        position = building.get("entrance") if building else places[entry_id]["pos"]
        name = building["name"] if building else default_name
        if position is None or not all(0 <= n < limit for n, limit in zip(position, (width, height, depth), strict=True)):
            raise ValueError(f"{city['city_id']} 的 {kind} 入口超出城市范围")
        result.append({"id": kind, "name": name, "position": position})
    return result


def ink(block: str, elevated: bool) -> tuple[int, int, int]:
    if block == "minecraft:water":
        return (133, 167, 164)
    if block == "minecraft:lava":
        return (178, 125, 89)
    if any(term in block for term in ("leaves", "grass_block", "moss", "azalea", "stem", "farmland")):
        return (155, 169, 133)
    if any(term in block for term in ("paving", "flagstone", "road_", "cobblestone", "gravel", "sidewalk")):
        return (207, 193, 157)
    if any(term in block for term in ("roof", "tile", "shingle")):
        return (104, 124, 115)
    if any(term in block for term in ("planks", "log", "wood", "terracotta", "brick")):
        return (154, 139, 111) if elevated else (186, 169, 130)
    if any(term in block for term in ("sand", "snow", "calcite")):
        return (221, 216, 188)
    return (141, 151, 129) if elevated else (198, 198, 163)


def map_pixels(root: Path, city: dict, pixel_width: int, pixel_depth: int) -> list[bytes]:
    width, _, depth = city["size"]
    terrain = array("h")
    with gzip.open(resource_file(root, city["terrain"]["resource"]), "rb") as stream:
        terrain.frombytes(stream.read())
    if sys.byteorder != "little":
        terrain.byteswap()
    if len(terrain) != width * depth:
        raise ValueError(f"{city['city_id']} 高度场大小不符")
    top_block = city["terrain"]["top_block"]
    fill_block = city["terrain"]["fill_block"]
    ground = []
    samples = defaultdict(dict)
    for pz in range(pixel_depth):
        z = (2 * pz + 1) * depth // (2 * pixel_depth)
        for px in range(pixel_width):
            x = (2 * px + 1) * width // (2 * pixel_width)
            samples[(x // 16, z // 16)][(x % 16, z % 16)] = len(ground)
            ground.append(terrain[z * width + x])
    heights = list(ground)
    material = [top_block] * len(ground)
    for tile in city["tiles"]:
        wanted = samples.get((tile["x"] // 16, tile["z"] // 16))
        if not wanted:
            continue
        template = resource_file(root, tile["template"], template=True)
        _, nbt = read_gzipped_nbt(str(template))
        palette = [entry["Name"] for entry in nbt["palette"]]
        written_air = defaultdict(set)
        solid = {}
        for block in nbt["blocks"]:
            x, y, z = block["pos"]
            index = wanted.get((x, z))
            if index is None:
                continue
            name = palette[block["state"]]
            if name in AIR:
                if y <= ground[index]:
                    written_air[index].add(y)
            elif index not in solid or y > solid[index][0]:
                solid[index] = (y, name)
        for index in set(solid) | set(written_air):
            base = ground[index]
            air = written_air.get(index, ())
            while base >= 0 and base in air:
                base -= 1
            y, name = solid.get(index, (-1, fill_block))
            if y >= base:
                heights[index], material[index] = y, name
            else:
                heights[index] = base
                if base != ground[index]:
                    material[index] = fill_block

    colors = {}
    rows = []
    for pz in range(pixel_depth):
        row = bytearray()
        for px in range(pixel_width):
            i = pz * pixel_width + px
            name, y = material[i], heights[i]
            key = (name, y - ground[i] > 2)
            if key not in colors:
                colors[key] = ink(*key)
            color = colors[key]
            slope = y - heights[i - 1] if px else 0
            slope += y - heights[i - pixel_width] if pz else 0
            shade = 0 if name in ("minecraft:water", "minecraft:lava") else max(-24, min(24, slope * 5))
            row.extend((*[max(0, min(255, c + shade)) for c in color], 255))
        rows.append(bytes(row))
    return rows


def generate(root: Path, output: Path) -> None:
    refs = read_json(root / FROZEN)["capitals"]["bundles"]
    for ref in refs:
        city = read_json(resource_file(root, ref["resource"]))
        city_id = ref["city_id"]
        if city["city_id"] != city_id:
            raise ValueError(f"{city_id} 的地图输入身份不符")
        width, _, depth = city["size"]
        scale = min(1, MAX_PIXELS / max(width, depth))
        pw, pd = max(1, round(width * scale)), max(1, round(depth * scale))
        entries = services(city)
        image = encode_png(map_pixels(root, city, pw, pd), pw, pd)
        map_data = {
            "city": city_id,
            "name": city["display_name"],
            "bundle": ref["bundle_sha256"],
            "width": width,
            "depth": depth,
            "texture_width": pw,
            "texture_height": pd,
            "services": entries,
        }
        (output / ASSETS).mkdir(parents=True, exist_ok=True)
        (output / DATA).mkdir(parents=True, exist_ok=True)
        (output / ASSETS / f"{city_id}.png").write_bytes(image)
        (output / DATA / f"{city_id}.json").write_text(json.dumps(map_data, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"{city_id}: {pw}×{pd}，{len(entries)} 处入口", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--capitals", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    generate(args.capitals, args.out)


if __name__ == "__main__":
    main()
