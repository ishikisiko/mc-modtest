"""Small orthographic portraits from the exact housing construction voxels."""
from io import BytesIO
from pathlib import Path
import re

from PIL import Image, ImageDraw

from tools.housing.materialize import MATERIALS
from tools.preview_structure import BLOCK_COLORS_PATH, load_block_colors, resolve_color

SIZE = 256
BOUNDS = (0, 0, 0, 16, 16, 16)
DETAIL = re.compile(r"yuanxian:housing_details_(\d+)\[variant=(\d+),facing=north\]")


def boxes(state, shapes):
    detail = DETAIL.fullmatch(state)
    if detail:
        group, index = map(int, detail.groups())
        for low, high, material in shapes[group * 128 + index]:
            yield (*low, *high), "minecraft:" + MATERIALS[material]
        return
    name = state.split("[", 1)[0]
    if name.endswith("_slab") and "type=double" not in state:
        shift = 8 if "type=top" in state else 0
        yield (0, shift, 0, 16, shift + 8, 16), state
    elif name == "minecraft:water":
        yield (0, 0, 0, 16, 14, 16), state
    elif name.endswith(("_fence", "_wall", "lantern", "torch")):
        yield (5, 0, 5, 11, 16, 11), state
    elif name.endswith("_stairs"):
        top = "half=top" in state
        shift = 8 if top else 0
        yield (0, shift, 0, 16, shift + 8, 16), state
        x0, z0, x1, z1 = next((rect for key, rect in (
            ("facing=north", (0, 0, 16, 8)), ("facing=south", (0, 8, 16, 16)),
            ("facing=west", (0, 0, 8, 16)), ("facing=east", (8, 0, 16, 16)),
        ) if key in state), (0, 0, 16, 8))
        shift = 0 if top else 8
        yield (x0, shift, z0, x1, shift + 8, z1), state
    else:
        yield BOUNDS, state


def image(home, shapes, colors):
    blocks = home["blocks"]
    faces = []
    for (x, y, z), state in blocks.items():
        if state.startswith(("minecraft:air", "minecraft:structure_void")):
            continue
        for (a, b, c, d, e, f), material in boxes(state, shapes):
            x0, y0, z0 = x + a / 16, y + b / 16, z + c / 16
            x1, y1, z1 = x + d / 16, y + e / 16, z + f / 16
            color = resolve_color(material, colors)
            if color == (180, 80, 200):
                color = (135, 130, 111)
            tone = .96 + ((x * 23 + y * 5 + z * 13) % 5) * .018
            top = ((x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1))
            right = ((x1,y1,z0),(x1,y0,z0),(x1,y0,z1),(x1,y1,z1))
            left = ((x0,y1,z1),(x1,y1,z1),(x1,y0,z1),(x0,y0,z1))
            for face, shade in ((right,.71),(left,.83),(top,1.05)):
                depth = sum(p[0] + .92 * p[1] + p[2] for p in face)
                projected = [(vx-vz, .47*(vx+vz)-vy) for vx,vy,vz in face]
                rgb = tuple(min(255, round(c * shade * tone)) for c in color)
                faces.append((depth, projected, rgb))
    all_points = [p for _, face, _ in faces for p in face]
    min_x = min(p[0] for p in all_points)
    max_x = max(p[0] for p in all_points)
    min_y = min(p[1] for p in all_points)
    max_y = max(p[1] for p in all_points)
    center_x, center_y = (min_x+max_x)/2, (min_y+max_y)/2
    rate = (2*SIZE-32) / max(max_x-min_x, max_y-min_y)
    canvas = Image.new("RGB", (2*SIZE,2*SIZE), (193, 205, 201))
    draw = ImageDraw.Draw(canvas)
    for _, points, color in sorted(faces, key=lambda f: f[0]):
        draw.polygon([(SIZE+(x-center_x)*rate, SIZE+(y-center_y)*rate) for x,y in points], fill=color)
    out = BytesIO()
    canvas.resize((SIZE,SIZE), Image.Resampling.LANCZOS).save(out, "PNG", optimize=True)
    return out.getvalue()


def images(homes, shapes):
    colors = load_block_colors(str(Path(BLOCK_COLORS_PATH)))
    return {h["id"]: image(h, shapes, colors) for h in homes if h["role"] == "player"}
