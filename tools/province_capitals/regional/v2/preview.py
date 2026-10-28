"""Review geometry derived from FINAL tile cells, not the former OBJ preview."""
from __future__ import annotations

import base64
import gzip
import json
from pathlib import Path
import struct

from .common import AIR, ROOT

HERE = Path(__file__).resolve().parent
COLORS = {
    "stone": "#9a9a91", "wall": "#cfccb7", "wood": "#61513f", "wood_light": "#98744e",
    "roof": "#4a5156", "roof_edge": "#657174", "ground": "#809069", "earth": "#74766a",
    "road": "#a4a195", "water": "#3d8c98", "window": "#b5cfcb", "cloth": "#a28662",
    "paper": "#e7dec7", "iron": "#4b5050", "leaf": "#4f7249", "snow": "#e7ebe7", "light": "#dfb46b",
}


class SceneBuilder:
    def __init__(self, model):
        self.model = model
        self.boxes = []
        self.meshes = []
        self.labels = []
        self.palette = {**COLORS, **model.city.get("palette", {})}
        self.terrain_surface()

    def box(self, p, s, material, layer, tag):
        self.boxes.append({"p": p, "s": s, "q": 0, "m": material, "layer": layer, "tag": tag, "detail": "detailed"})

    def material(self, state, layer):
        base = state.split("[", 1)[0]
        if base == "minecraft:water":
            return "water"
        if base == "minecraft:snow" or base == "minecraft:snow_block":
            return "snow"
        if "leaves" in base:
            return "leaf"
        if "glass" in base:
            return "window"
        if "lantern" in base:
            return "light"
        if "_bed" in base:
            return "paper"
        if "wool" in base:
            return "cloth"
        if layer == "roof":
            return "roof"
        if "log" in base or "wood" in base or "fence" in base or "_door" in base:
            return "wood"
        if "planks" in base or "barrel" in base or "crafting" in base or "bookshelf" in base:
            return "wood_light"
        if any(name in base for name in ("anvil", "furnace", "cauldron", "brewing")):
            return "iron"
        if layer == "road":
            return "road"
        if base == self.model.material("WALL_MAIN"):
            return "wall"
        return "stone"

    def terrain_surface(self):
        m = self.model
        vertices, faces, sides, side_faces = [], [], [], []

        def quad(points, side=False):
            verts, indices = (sides, side_faces) if side else (vertices, faces)
            n = len(verts)
            verts.extend(points)
            indices.extend([[n, n + 1, n + 2], [n, n + 2, n + 3]])

        # Exposed unit-column surfaces preserve the exact int16 steps.  No
        # interpolated triangle slope is substituted for Minecraft terrain.
        for z in range(m.depth):
            x = 0
            while x < m.width:
                y = m.ground(x, z) + 1
                end = x + 1
                while end < m.width and m.ground(end, z) + 1 == y:
                    end += 1
                quad([[x, y, z], [x, y, z + 1], [end, y, z + 1], [end, y, z]])
                x = end
            for x in range(m.width):
                y = m.ground(x, z) + 1
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, nz = x + dx, z + dz
                    ny = m.ground(nx, nz) + 1 if 0 <= nx < m.width and 0 <= nz < m.depth else 0
                    if ny >= y:
                        continue
                    if dx:
                        xx = x + (1 if dx > 0 else 0)
                        quad([[xx, ny, z], [xx, y, z], [xx, y, z + 1], [xx, ny, z + 1]], True)
                    else:
                        zz = z + (1 if dz > 0 else 0)
                        quad([[x, ny, zz], [x + 1, ny, zz], [x + 1, y, zz], [x, y, zz]], True)
        self.meshes.append({"v": vertices, "f": faces, "m": "ground", "layer": "terrain", "tag": "heightfield", "detail": "detailed"})
        self.meshes.append({"v": sides, "f": side_faces, "m": "earth", "layer": "terrain", "tag": "heightfield_sides", "detail": "detailed"})

    def add_tile(self, tx, tz, tile):
        keys = sorted(tile, key=lambda p: (p[1], p[2], p[0]))
        i = 0
        while i < len(keys):
            pos = keys[i]
            state, tag, layer = tile[pos]
            i += 1
            if state == AIR:
                continue
            x, y, z = pos
            material = self.material(state, layer)
            name = state.split("[", 1)[0]
            special = any(v in name for v in ("_stairs", "_slab", "_bed", "_door", "_fence", "glass_pane", "lantern", "snow", "brewing_stand", "lectern"))
            span = 1
            if not special:
                while i < len(keys) and keys[i] == (x + span, y, z) and tile[keys[i]] == (state, tag, layer):
                    span += 1
                    i += 1
            wx, wz = tx * 16 + x, tz * 16 + z
            props = {}
            if "[" in state:
                props = dict(v.split("=", 1) for v in state[:-1].split("[", 1)[1].split(","))
            if "_stairs" in name:
                upper = props.get("half") == "top"
                self.box([wx + .5, y + (.75 if upper else .25), wz + .5], [1, .5, 1], material, layer, tag)
                face = props.get("facing", "north")
                dx, dz = {"east": (.25, 0), "west": (-.25, 0), "south": (0, .25), "north": (0, -.25)}[face]
                self.box([wx + .5 + dx, y + (.25 if upper else .75), wz + .5 + dz], [.5 if dx else 1, .5, .5 if dz else 1], material, layer, tag)
            elif "_slab" in name:
                self.box([wx + .5, y + (.75 if props.get("type") == "top" else .25), wz + .5], [1, .5, 1], material, layer, tag)
            elif "_bed" in name:
                self.box([wx + .5, y + .28, wz + .5], [1, .56, 1], material, layer, tag)
            elif "_door" in name:
                face = props.get("facing", "south")
                dx, dz = {"east": (-.40625, 0), "west": (.40625, 0), "south": (0, -.40625), "north": (0, .40625)}[face]
                self.box([wx + .5 + dx, y + .5, wz + .5 + dz], [.1875 if dx else 1, 1, .1875 if dz else 1], material, layer, tag)
            elif name == "minecraft:snow":
                h = int(props.get("layers", "1")) / 8
                self.box([wx + .5, y + h / 2, wz + .5], [1, h, 1], material, layer, tag)
            elif "glass_pane" in name:
                ew = props.get("east") == "true" or props.get("west") == "true"
                self.box([wx + .5, y + .5, wz + .5], [1 if ew else .125, 1, .125 if ew else 1], material, layer, tag)
            elif "_fence" in name:
                self.box([wx + .5, y + .75, wz + .5], [.25, 1.5, .25], material, layer, tag)
                self.box([wx + .5, y + .8, wz + .5], [1, .16, .16], material, layer, tag)
                self.box([wx + .5, y + .8, wz + .5], [.16, .16, 1], material, layer, tag)
            elif "lantern" in name:
                self.box([wx + .5, y + .25, wz + .5], [.4, .5, .4], material, layer, tag)
            elif "brewing_stand" in name:
                self.box([wx + .5, y + .3, wz + .5], [.75, .6, .75], material, layer, tag)
            elif "lectern" in name:
                self.box([wx + .5, y + .5, wz + .5], [.75, 1, .75], material, layer, tag)
            else:
                self.box([wx + span / 2, y + .5, wz + .5], [span, 1, 1], material, layer, tag)

    def finish(self, bundle):
        m = self.model
        self.labels = [{"text": b["name"], "position": [b["position"][0], b["position"][1] + 8, b["position"][2]],
                        "kind": "service", "target": b["id"]} for b in m.buildings if b["role"] != "house"]
        entry = bundle["entries"][0]["position"]
        w, d = m.width, m.depth
        cameras = {"panorama": {"eye": [w * 1.55, 220, d * 1.25], "target": [w / 2, 22, d / 2]},
                   "aerial": {"eye": [w / 2, max(w, d) * 1.35, d / 2 + .1], "target": [w / 2, 10, d / 2]},
                   "street": {"eye": [entry[0] + 3, entry[1] + 1.65, entry[2]], "target": [w / 2, entry[1] + 1.5, d / 2]},
                   "feature": {"eye": [w * 1.1, 120, d * .8], "target": [w / 2, 22, d / 2]},
                   "courtyard": {"eye": [w / 2 + 24, 36, d / 2 + 24], "target": [w / 2, 12, d / 2]}}
        authored = m.city.get("design_cameras", m.city.get("viewpoints", []))
        if isinstance(authored, dict):
            authored = [{"id": k, **v} for k, v in authored.items()]
        mapping = {"overview": "panorama", "entry": "street", "public": "feature", "courtyard": "courtyard",
                   "panorama": "panorama", "street": "street", "feature": "feature", "aerial": "aerial"}
        for spec in authored:
            if spec["id"] in mapping:
                cameras[mapping[spec["id"]]] = {"eye": spec.get("position", spec.get("eye")), "target": spec["target"], "name": spec.get("name", spec["id"])}
        manifest = dict(bundle)
        manifest["coverage"] = {**bundle["coverage"], "detailed_houses": bundle["coverage"]["houses"], "service_anchors": 5}
        notes = m.city.get("design_notes", m.city.get("summary", ""))
        if isinstance(notes, list):
            notes = " ".join(notes)
        return {"schema": 1, "city_id": bundle["city_id"], "name": bundle["display_name"],
                "summary": notes + " 本页来自最终分块记录；显示真实阶梯屋盖和门窗，块名配色为示意。原生游戏未验收。",
                "materials": {k: {"color": v} for k, v in self.palette.items()}, "boxes": self.boxes, "meshes": self.meshes,
                "labels": self.labels, "cameras": cameras, "manifest": manifest,
                "presentation_context": {"open_east_sea": bundle["region_id"] == "donghai"}}


def make_html(scenes):
    vendor = (ROOT / "web/yunshan-city/vendor/three.module.min.js").read_text(encoding="utf-8")
    viewer = (HERE / "viewer.js").read_text(encoding="utf-8")
    template = (HERE / "template.html").read_text(encoding="utf-8")
    compact = []
    for scene in scenes:
        slim = dict(scene)
        materials = list(scene["materials"])
        layers = list(dict.fromkeys(b["layer"] for b in scene["boxes"]))
        tags = list(dict.fromkeys(b["tag"] for b in scene["boxes"]))
        mat_id = {v: i for i, v in enumerate(materials)}
        layer_id = {v: i for i, v in enumerate(layers)}
        tag_id = {v: i for i, v in enumerate(tags)}
        boxes = scene["boxes"]
        packed = bytearray()
        # Columnar deltas keep repeated unit sizes and equal-height rows small
        # without discarding a single final construction cell.
        for component in range(6):
            previous = 0
            for box in boxes:
                value = round((box["p"] + box["s"])[component] * 32)
                packed.extend(struct.pack("<h", value - previous))
                previous = value
        packed.extend(bytes(mat_id[b["m"]] for b in boxes))
        packed.extend(bytes(layer_id[b["layer"]] for b in boxes))
        packed.extend(struct.pack("<" + "H" * len(boxes), *[tag_id[b["tag"]] for b in boxes]))
        slim.pop("boxes")
        slim["packed_boxes"] = {"encoding": "soa_i16le_delta_1_32_xyz_size_u8_material_layer_u16_tag", "count": len(boxes),
                                 "data": base64.b64encode(packed).decode(), "materials": materials,
                                 "layers": layers, "tags": tags}
        width, _, depth = scene["manifest"]["size"]
        heights = [0] * (width * depth)
        # Lossless reconstruction of the exact column tops from our exposed
        # top-face rectangles; sides are then recreated in the browser.
        for mesh in scene["meshes"]:
            if mesh["tag"] != "heightfield":
                continue
            for i in range(0, len(mesh["v"]), 4):
                a, _, c, _ = mesh["v"][i:i + 4]
                for x in range(a[0], c[0]):
                    heights[a[2] * width + x] = a[1] - 1
        slim["voxel_terrain"] = {"width": width, "depth": depth,
                                 "data": base64.b64encode(struct.pack("<" + "h" * len(heights), *heights)).decode()}
        slim["meshes"] = [m for m in scene["meshes"] if m["tag"] not in ("heightfield", "heightfield_sides")]
        compact.append(slim)
    payload = json.dumps({"three": vendor, "viewer": viewer, "scenes": compact}, ensure_ascii=False, separators=(",", ":")).encode()
    packed = base64.b64encode(gzip.compress(payload, mtime=0, compresslevel=9)).decode()
    return template.replace("__REGIONAL_PAYLOAD_GZIP_BASE64__", packed)


def write_city_preview(dest, scene):
    dest.mkdir(parents=True, exist_ok=True)
    (dest / "scene.json").write_text(json.dumps(scene, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    (dest / "index.html").write_text(make_html([scene]), encoding="utf-8")


def write_combined_preview(dest, scenes):
    dest.mkdir(parents=True, exist_ok=True)
    (dest / "index.html").write_text(make_html(scenes), encoding="utf-8")
