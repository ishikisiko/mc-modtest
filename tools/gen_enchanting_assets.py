"""Generate the enchanting stations, their recipes and GuideME placement layer diagrams."""
import argparse
import io
import json
from pathlib import Path
import sys

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.language_assets import language_text, load_language

PALETTE = {
    "slate": (54, 72, 77), "jade": (65, 116, 102), "bronze": (125, 87, 59),
    "brass": (168, 132, 76), "violet": (130, 101, 159), "rose": (136, 70, 96),
    "ivory": (216, 198, 151), "shadow": (41, 48, 49), "glow": (197, 222, 174),
}
FACING = {"south": 0, "west": 90, "north": 180, "east": 270}
SIDES = ("up", "down", "north", "south", "east", "west")
FRONT = {"seal_table", "enchanting_socket", "library_port"}


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode()


def png(image):
    out = io.BytesIO()
    image.save(out, "PNG")
    return out.getvalue()


def material(name, rgb):
    image = Image.new("RGB", (16, 16))
    for y in range(16):
        for x in range(16):
            grain = ((x * 17 ^ y * 37 ^ x * y) % 15) - 7
            edge = 12 if x == 1 or y == 1 else -16 if x in (0, 15) or y in (0, 15) else 0
            if name == "slate" and (y % 5 == 0 or (x + y // 5 * 4) % 9 == 0):
                edge -= 9
            if name in {"jade", "rose", "violet"} and (x + 2 * y) % 13 == 0:
                edge += 8
            image.putpixel((x, y), tuple(min(255, max(0, channel + grain + edge)) for channel in rgb))
    return image


def box(lo, hi, material):
    return {"from": lo, "to": hi, "faces": {side: {"texture": "#" + material} for side in SIDES}}


def form(part, color):
    b = box
    if part in {"seal_tile", "library_base"}:
        return [b([0, 0, 0], [16, 16, 16], color)]
    if part == "enchanting_socket":
        return [b([0, 0, 0], [16, 13, 16], "slate"), b([1, 13, 1], [15, 15, 15], "bronze"),
                b([4, 15, 4], [12, 16, 12], color)]
    if part == "seal_table":
        return [b([1, 0, 1], [15, 2, 15], "slate"), b([3, 2, 3], [13, 9, 13], "shadow"),
                b([1, 9, 1], [15, 12, 15], "rose"), b([4, 12, 4], [12, 13, 12], "brass"),
                b([4, 13, 5], [7.9, 13.5, 11], "ivory"), b([8.1, 13, 5], [12, 13.5, 11], "ivory"),
                b([6, 4, 13], [10, 8, 13.5], color)]
    if part == "library_port":
        return [b([1, 0, 1], [15, 3, 15], "slate"), b([2, 3, 2], [14, 13, 14], color),
                b([1, 13, 1], [15, 15, 15], "bronze"), b([4, 5, 14], [12, 11, 15], "brass"),
                b([5, 6, 15], [11, 10, 16], "shadow"), b([7, 6, 15.7], [9, 9, 16], "glow")]
    if part.endswith("shelf") or part == "seal_rack":
        return [b([0, y, 0], [16, y + 2, 16], "bronze") for y in (0, 6, 11)] + [
            b([x, y, 1], [x + w, y + 4, 15], tone)
            for y in (2, 7) for x, w, tone in [(1, 2, color), (4, 3, "rose"), (8, 2, "jade"), (11, 4, "ivory")]]
    if part in {"seal_pivot", "library_frame"}:
        return [b([2, 0, 2], [14, 2, 14], "slate"), b([3, 2, 3], [13, 14, 13], color),
                b([2, 14, 2], [14, 16, 14], "bronze"), b([7, 3, 2.6], [9, 13, 3], "brass"),
                b([7, 3, 13], [9, 13, 13.4], "brass")]
    if part in {"library_outer", "library_equator", "library_oblique"}:
        return [b([3, 3, 3], [13, 13, 13], color), b([5, 5, 2.8], [11, 11, 3], "brass")]
    return [b([1, 1, 1], [15, 15, 15], "slate"), b([3, 0, 3], [13, 16, 13], color),
            b([6, 6, 0], [10, 10, 16], "brass"), b([0, 6, 6], [16, 10, 10], "bronze")]


def diagram(layout, y):
    colors = {"seal_tile": "#536b70", "seal_rack": "#b08b61", "seal_pivot": "#549888",
              "seal_table": "#9f77ad", "enchanting_socket": "#c49d5b"}
    cells = {(x, z): part for x, layer, z, part in layout if layer == y}
    image = Image.new("RGB", (352, 220), "#202e32")
    draw = ImageDraw.Draw(image)
    for x in range(-4, 5):
        for z in range(-4, 5):
            part = cells.get((x, z))
            left, top = 24 + 20 * (x + 4), 12 + 20 * (z + 4)
            draw.rectangle((left, top, left + 18, top + 18), fill=colors.get(part, "#293b3d"), outline="#70908a")
            if part:
                draw.text((left + 5, top + 4), str(list(colors).index(part) + 1), fill="#f6f0df")
    draw.polygon([(114, 194), (109, 207), (119, 207)], fill="#dfcd9d")
    for x, h, z, part in sorted(layout, key=lambda row: (row[0] + row[2], row[1])):
        if h > y:
            continue
        a, b = 276 + (x - z) * 8, 167 + (x + z) * 4 - h * 12
        top = colors[part] if h == y else "#4e5d58"
        draw.polygon([(a - 8, b), (a, b + 4), (a + 8, b), (a + 8, b + 6), (a, b + 10), (a - 8, b + 6)], fill="#28323a")
        draw.polygon([(a - 8, b), (a, b - 4), (a + 8, b), (a, b + 4)], fill=top, outline="#9b9b84")
    return image


def outputs(root, namespace):
    cfg = root / "enchanting"
    resource = root / "src/main/resources"
    asset = resource / "assets" / namespace
    data = resource / "data" / namespace
    parts = json.loads((cfg / "parts.json").read_text())
    rules = json.loads((cfg / "rules.json").read_text())
    structures = json.loads((cfg / "structures.json").read_text())
    out = {}
    tr = {}
    loc = lambda part: part if ":" in part else namespace + ":" + part
    for name, rgb in PALETTE.items():
        out[asset / f"textures/block/enchanting/{name}.png"] = png(material(name, rgb))
    for name, (caption, color, count, ingredients) in parts.items():
        item = name == "loadout_slip" or name.startswith("array_stone")
        tr[f"{'item' if item else 'block'}.{namespace}.{name}"] = caption
        if item:
            icon = Image.new("RGBA", (16, 16))
            draw = ImageDraw.Draw(icon)
            if name == "loadout_slip":
                draw.polygon([(2, 4), (5, 2), (13, 2), (14, 4), (11, 13), (9, 14), (2, 14)], fill=PALETTE[color], outline=PALETTE["brass"])
                draw.line([(5, 5), (10, 5), (9, 9), (5, 9)], fill=PALETTE["glow"])
            else:
                draw.polygon([(7, 1), (12, 4), (14, 10), (9, 15), (3, 13), (1, 7)], fill=PALETTE[color], outline=PALETTE["slate"])
                draw.line([(4, 7), (7, 4), (10, 7), (7, 10), (4, 7)], fill=PALETTE["glow"])
            out[asset / f"textures/item/{name}.png"] = png(icon)
            out[asset / f"models/item/{name}.json"] = encoded({
                "parent": "minecraft:item/generated", "textures": {"layer0": loc("item/" + name)}})
        else:
            ref = loc("block/" + name)
            textures = {tone: loc("block/enchanting/" + tone) for tone in PALETTE}
            textures["particle"] = textures[color]
            out[asset / f"models/block/{name}.json"] = encoded({"textures": textures, "elements": form(name, color)})
            out[asset / f"models/item/{name}.json"] = encoded({"parent": ref, "display": {
                "gui": {"rotation": [26, 225, 0], "scale": [.65, .65, .65]},
                "fixed": {"scale": [.7, .7, .7]}}})
            variant = {f"facing={face}": {"model": ref, "y": angle} for face, angle in FACING.items()}
            out[asset / f"blockstates/{name}.json"] = encoded({"variants": variant if name in FRONT else {"": {"model": ref}}})
            entry = {"type": "minecraft:item", "name": loc(name)}
            if name == "library_port":
                entry["functions"] = [{"function": "minecraft:copy_components", "source": "block_entity",
                                       "include": ["minecraft:custom_data"]}]
            out[data / f"loot_table/blocks/{name}.json"] = encoded({"type": "minecraft:block", "pools": [
                {"rolls": 1, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        if ingredients:
            out[data / f"recipe/{name}.json"] = encoded({"type": "minecraft:crafting_shapeless",
                "ingredients": [{"item": loc(ingredient)} for ingredient in ingredients],
                "result": {"id": loc(name), "count": count}})
    for name in ("structures", "rules", "rings"):
        out[data / f"enchanting/{name}.json"] = (cfg / f"{name}.json").read_bytes()
    lang = asset / "lang/zh_cn.json"
    prior = load_language(lang.read_text())
    out[lang] = language_text(prior, prior | tr).encode()
    for kind in ("pickaxe", "axe", "shovel", "hoe"):
        path = resource / f"data/minecraft/tags/item/{kind}s.json"
        tag = json.loads(path.read_text()) if path.exists() else {"replace": False, "values": []}
        name = loc("forged_" + kind)
        if name not in tag["values"]:
            tag["values"].append(name)
        out[path] = encoded(tag)
    mineable = resource / "data/minecraft/tags/block/mineable/pickaxe.json"
    tag = json.loads(mineable.read_text())
    tag["values"] = sorted(set(tag["values"]) | {loc(part) for part in parts if part in FRONT or part.startswith(("library_", "seal_"))})
    out[mineable] = encoded(tag)
    layout = structures["seal"] + [[0, -1, 0, "enchanting_socket"], [0, 0, 0, "seal_table"]]
    assert len({tuple(row[:3]) for row in layout}) == len(layout)
    assert len(structures["library"]) == 345 and [0, 1, 5, "port"] in structures["library"]
    assert rules["shelf_cap"] == max(row["strength"] for row in rules["stones"])
    for y in (-1, 0, 1, 2, 3):
        out[root / f"guidebook/getting_started/seal_array/placement_{y + 2}.png"] = png(diagram(layout, y))
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    namespace = next(line.split("=", 1)[1] for line in (ROOT / "gradle.properties").read_text().splitlines() if line.startswith("mod_id="))
    mismatches = []
    for path, contents in outputs(ROOT, namespace).items():
        if args.check:
            if not path.exists() or path.read_bytes() != contents:
                mismatches.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(contents)
    if mismatches:
        raise SystemExit("资源需重新生成\n" + "\n".join(mismatches))


if __name__ == "__main__":
    main()
