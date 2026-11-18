"""Generate the enchanting stations, their recipes and GuideME placement layer diagrams."""
import argparse
import io
import json
import re
from PIL import ImageColor
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


def glyph_paths(path):
    edges, current, start = [], (0, 0), (0, 0)
    for op, raw in re.findall(r"([MHLVZ])([^MHLVZ]*)", path):
        values = list(map(float, raw.split()))
        if op == "M":
            current = start = tuple(values)
            continue
        end = {"H": lambda: (values[0], current[1]), "V": lambda: (current[0], values[0]),
               "L": lambda: tuple(values), "Z": lambda: start}[op]()
        edges.append((current, end))
        current = end
    return edges


def projection(disc):
    tile = Image.new("RGBA", (96, 144))
    draw = ImageDraw.Draw(tile)
    tone = ImageColor.getrgb(disc["color"])
    def pos(point):
        return (round(point[0] * .96), round(point[1] * .90))
    outline = [(29, 13), (71, 13), (71, 19), (81, 19), (81, 27), (85, 27),
               (85, 123), (77, 123), (77, 135), (23, 135), (23, 128), (15, 128),
               (15, 28), (20, 28), (20, 20), (29, 20)]
    draw.polygon([pos(p) for p in outline], fill=(*tone, 42))
    strokes = [
        ("M29 14 H71 V20 H81 V30 M19 30 V44 M19 76 V110 H24 V122 M81 44 V90 M81 101 V116 H75", 1, 138),
        ("M34 25 H64 M33 105 H68 M36 113 H55 M40 119 H61", 1, 105),
        (disc["motif"], 2, 225), (disc["edge"], 1, 164),
    ]
    for path, width, opacity in strokes:
        for near, far in glyph_paths(path):
            draw.line([pos(near), pos(far)], fill=(87, 65, 56, opacity // 2), width=width+2)
            draw.line([pos(near), pos(far)], fill=(*tone, opacity), width=width)
    if disc["role"] == "empty":
        tile.putalpha(tile.getchannel("A").point(lambda alpha: alpha * 5 // 9))
    return tile


def diagram(layout, y):
    colors = {"seal_tile": ("1", "#536b70"), "enchanting_socket": ("2", "#b08b61"),
              "seal_pivot": ("3", "#549888"), "seal_table": ("4", "#9f77ad"),
              "minecraft:deepslate_brick_slab": ("5", "#707c81")}
    cells = {(x, z): part for x, layer, z, part in layout if layer == y}
    image = Image.new("RGB", (482, 256), "#202e32")
    draw = ImageDraw.Draw(image)
    for x in range(-6, 7):
        for z in range(-6, 7):
            symbol, color = colors.get(cells.get((x, z)), ("", "#293b3d"))
            left, top = 15 + 16 * (x + 6), 10 + 16 * (z + 6)
            draw.rectangle((left, top, left + 15, top + 15), fill=color, outline="#70908a")
            if symbol:
                draw.text((left + 5, top + 3), symbol, fill="#f6f0df")
    draw.polygon([(119, 225), (114, 238), (124, 238)], fill="#dfcd9d")
    for x, h, z, part in sorted(layout, key=lambda row: (row[0] + row[2], row[1])):
        if h > y:
            continue
        a, b = 355 + (x - z) * 8, 137 + (x + z) * 4 - h * 12
        top = colors[part][1] if h == y else "#4e5d58"
        depth = 4 if part == "minecraft:deepslate_brick_slab" else 8
        if depth == 4:
            b += 4
        draw.polygon([(a - 8, b), (a, b + 4), (a + 8, b), (a + 8, b + depth),
                      (a, b + 4 + depth), (a - 8, b + depth)], fill="#28323a")
        draw.polygon([(a - 8, b), (a, b - 4), (a + 8, b), (a, b + 4)], fill=top, outline="#9b9b84")
    return image


def starter_diagram(layout, layer, colors):
    """Show one placement layer in plan and from the same fixed angle."""
    image = Image.new("RGB", (342, 156), "#202e32")
    draw = ImageDraw.Draw(image)
    cells = {(x, z): part for x, h, z, part in layout if h == layer}
    for z in range(-2, 3):
        for x in range(-2, 3):
            left, top = 16 + (x + 2) * 24, 10 + (z + 2) * 24
            symbol, tint = colors.get(cells.get((x, z)), ("", "#293b3d"))
            draw.rectangle((left, top, left + 22, top + 22), fill=tint, outline="#9a9a83")
            if symbol:
                draw.text((left + 8, top + 6), symbol, fill="#fff2d8")
    draw.polygon([(75, 134), (70, 146), (80, 146)], fill="#dfcd9d")

    # Draw from rear to front. Grey pieces show the layers already placed below.
    for x, y, z, part in sorted(layout, key=lambda cell: (cell[0] + cell[2], cell[1])):
        if y > layer:
            continue
        a, b = 242 + (x - z) * 15, 91 + (x + z) * 7 - y * 18
        tint = colors[part][1] if y == layer else "#59615b"
        draw.polygon([(a - 14,b),(a,b + 7),(a + 14,b),(a + 14,b + 11),(a,b + 18),(a - 14,b + 11)],
                     fill="#35484b", outline="#263a3d")
        draw.polygon([(a - 14,b),(a,b - 7),(a + 14,b),(a,b + 7)], fill=tint, outline="#a3a390")
    return image


def starter_diagrams(source):
    cfg = json.loads(source.read_text())
    palette = {
        "enchanting_socket": ("1", "#557e73"), "enchanting_table": ("2", "#8a668f"),
        "bookshelf": ("3", "#94794f"), "floor": ("", "#435657"),
        "support": ("4", "#62746f"), "focus_shelf": ("5", "#a38150"),
        "crystal_shelf": ("6", "#8672ad"),
    }
    perimeter = {(x, z) for x in range(-2, 3) for z in range(-2, 3)
                 if max(abs(x), abs(z)) == 2} - {(0, 2)}
    floor = [(x, -1, z, "enchanting_socket" if (x, z) == (0, 0) else "floor")
             for x in range(-2, 3) for z in range(-2, 3)]
    base = list(map(tuple, cfg["empty_socket_shelves"]))
    entry = base + list(map(tuple, cfg["entry_stone_extra_shelves"]))
    focus = tuple(cfg["focus_shelf"])
    raised = set(map(tuple, cfg["raised_crystal_shelves"]))
    assert len(set(base)) == 4 and len(set(entry)) == 8 and set(entry) <= perimeter
    assert focus in perimeter - raised and len(raised) == 2 and raised <= perimeter

    def shelves(positions):
        return floor + [(0, 0, 0, "enchanting_table")] + [(x, 0, z, "bookshelf") for x, z in positions]

    mixed = shelves(sorted(perimeter - raised - {focus}))
    mixed.append((focus[0], 0, focus[1], "focus_shelf"))
    for x, z in sorted(raised):
        mixed.extend([(x, 0, z, "support"), (x, 1, z, "crystal_shelf")])

    for name, layout, layer in [
        ("floor", floor, -1), ("empty_socket", shelves(base), 0),
        ("entry_stone", shelves(entry), 0), ("mixed_table", mixed, 0),
        ("mixed_raised", mixed, 1),
    ]:
        yield name, starter_diagram(layout, layer, palette)


def outputs(root, namespace):
    cfg = root / "enchanting"
    resource = root / "src/main/resources"
    asset = resource / "assets" / namespace
    data = resource / "data" / namespace
    parts = json.loads((cfg / "parts.json").read_text())
    rules = json.loads((cfg / "rules.json").read_text())
    casket = json.loads((cfg / "casket.json").read_text())
    discs = json.loads((cfg / "discs.json").read_text())
    seal_rules = json.loads((cfg / "seal_rules.json").read_text())
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
    for name, color in casket["palette"].items():
        out[asset / f"textures/block/enchanting/casket/{name}.png"] = png(material(name, ImageColor.getrgb(color)))
    for key, disc in discs.items():
        imprint = projection(disc)
        out[asset / f"textures/block/enchanting/projection/{key}.png"] = png(imprint)
        if key == "empty":
            continue
        name = "seal_disc_" + key
        tr[f"item.{namespace}.{name}"] = disc["name"]
        icon = Image.new("RGBA", (16, 16))
        draw = ImageDraw.Draw(icon)
        draw.polygon([(5, 2), (11, 2), (13, 4), (13, 12), (11, 14), (4, 14),
                      (3, 12), (3, 4)], fill="#354039", outline=disc["color"])
        for near, far in glyph_paths(disc["motif"]):
            pos = lambda point: (round(3 + point[0] / 10), round(2 + point[1] / 9))
            draw.line([pos(near), pos(far)], fill=disc["color"], width=1)
        out[asset / f"textures/item/{name}.png"] = png(icon)
        out[asset / f"models/item/{name}.json"] = encoded({
            "parent": "minecraft:item/generated", "textures": {"layer0": loc("item/" + name)}})
        out[data / f"recipe/{name}.json"] = encoded({
            "type": "minecraft:crafting_shapeless", "ingredients": [{"item": loc(i)} for i in disc["ingredients"]],
            "result": {"id": loc(name), "count": 1}})
    hidden = loc("block/seal_member")
    out[asset / "blockstates/seal_member.json"] = encoded({"variants": {"": {"model": hidden}}})
    out[asset / "models/block/seal_member.json"] = encoded({
        "textures": {"particle": loc("block/enchanting/slate")}, "elements": []})
    out[data / "loot_table/blocks/seal_member.json"] = encoded({"type": "minecraft:block", "pools": []})
    for name in ("structures", "rules", "rings", "casket", "discs", "seal_rules"):
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
    tag["values"] = sorted(set(tag["values"]) | {loc("seal_member")}
                           | {loc(part) for part in parts if part in FRONT or part.startswith(("library_", "seal_"))})
    out[mineable] = encoded(tag)
    layout = structures["seal"] + [[0, -1, 0, "enchanting_socket"], [0, 0, 0, "seal_table"]]
    assert len({tuple(row[:3]) for row in layout}) == len(layout)
    assert len(structures["library"]) == 345 and [0, 1, 5, "port"] in structures["library"]
    assert rules["shelf_cap"] == max(row["strength"] for row in rules["stones"])
    assert len(casket["slots"]) == 16 and seal_rules["prices"][-1]["through"] == seal_rules["strength_cap"]
    assert all(disc["role"] in {"front", "direction", "method", "empty"} for disc in discs.values())
    for y in (-1, 0, 1):
        out[root / f"guidebook/getting_started/seal_array/placement_{y + 2}.png"] = png(diagram(layout, y))
    for name, image in starter_diagrams(cfg / "starter_placement.json"):
        out[root / f"guidebook/getting_started/enchanting_table/{name}.png"] = png(image)
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
