#!/usr/bin/env python3
"""Build the five Minecraft item models from the approved slim sword meshes."""
from __future__ import annotations

import argparse
import io
import json
from pathlib import Path
from zipfile import ZipFile

from PIL import Image

from tools.equipment import mesh

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "equipment/weapons/companion_sword_meshes.zip"
RESOURCE = ROOT / "src/main/resources"
ITEM = "primordial_companion_sword"
GRIP = [8, 2.33, 8]
FORMS = ("qi_refining", "foundation", "golden_core", "nascent_soul", "spirit_transformation")


def authored_faces(archive: ZipFile, form: str) -> tuple[list[dict], bytes]:
    faces = mesh.read_obj(archive.read(f"{form}/{form}.obj"), archive.read(f"{form}/{form}.mtl"))
    materials = list(dict.fromkeys(face["texture"] for face in faces))
    atlas = Image.new("RGBA", (256, 512))
    tile = {}
    for index, material in enumerate(materials):
        texture = Image.open(io.BytesIO(archive.read(f"{form}/{material}"))).convert("RGBA")
        if texture.size != (32, 64) or index >= 16:
            raise ValueError(f"Invalid sword texture {form}/{material}")
        u, v = (index % 4) * 64 + 16, (index // 4) * 128 + 32
        # Repeat the source pixels into the gutter for UVs on the edge and lower mip levels.
        for i in (-1, 0, 1):
            for j in (-1, 0, 1):
                atlas.paste(texture, (u + i * 32, v + j * 64))
        tile[material] = (u, v)
    faces = mesh.map_points(faces, lambda point: (point[0] + 8, point[1], point[2] + 8))
    for face in faces:
        u, v = tile[face["texture"]]
        face["uv"] = [[(u + 0.5 + a * 31) / atlas.width, (v + 0.5 + (1 - b) * 63) / atlas.height]
                      for a, b in face["uv"]]
        face["texture"] = f"yuanxian:item/{ITEM}/{form}"
        if len(face["points"]) == 3:
            face["points"].append(face["points"][-1])
            face["uv"].append(face["uv"][-1])
    output = io.BytesIO()
    atlas.save(output, format="PNG", optimize=True)
    return faces, output.getvalue()


def geometry(faces: list[dict], model: str) -> dict:
    points = [point for face in faces for point in face["points"]]
    tip = max(points, key=lambda point: point[1])
    # Each of the approved shapes uses the same wrapped grip. The tip is read from the mesh.
    return {
        "format": 2, "units": "model_pixels", "generator": "tools/gen_companion_sword.py",
        "model": f"yuanxian:item/{model}",
        "axes": {"length": "+y", "flat_normal": "x", "edge": "z", "center_x": 8, "center_z": 8},
        "grip_center": GRIP,
        "handle": {"y": [.83, 3.87], "half_width": .73, "half_thickness": .58},
        "collar": {"y": [3.87, 4.66], "half_width": 2.2, "half_thickness": .61},
        "butt": {"y": [0, .83], "half_width": .88, "half_thickness": .67},
        "head": {"half_width": 1.5, "half_thickness": .57, "ridge_half_thickness": .65,
                 "taper_start_y": tip[1] - 2},
        "head_base": [8, 4.66, 8], "head_tip": tip,
        "trail": {"base": [8, 4.66, 8], "tip": [8, tip[1], 8]},
    }


def outputs() -> dict[str, bytes]:
    def encoded(data: dict) -> bytes:
        return (json.dumps(data, ensure_ascii=False, indent=2) + "\n").encode()

    assets = "assets/yuanxian"
    result = {}
    with ZipFile(SOURCE) as archive:
        for index, form in enumerate(FORMS):
            faces, texture = authored_faces(archive, form)
            name = ITEM if index == 0 else f"{ITEM}_{form}"
            display = mesh.item_display(faces, grip=GRIP, root=ROOT)
            obj, model = mesh.obj_assets(faces, f"yuanxian:item/{name}", display)
            if index == 0:
                model["overrides"] = [
                    {"predicate": {"yuanxian:companion_form": stage / 4}, "model": f"yuanxian:item/{ITEM}_{other}"}
                    for stage, other in enumerate(FORMS[1:], 1)
                ]
            result.update(obj)
            result[f"{assets}/models/item/{name}.json"] = encoded(model)
            result[f"{assets}/combat/{name}_geometry.json"] = encoded(geometry(faces, name))
            result[f"{assets}/textures/item/{ITEM}/{form}.png"] = texture
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    changed = []
    for name, content in outputs().items():
        path = RESOURCE / name
        if path.is_file() and path.read_bytes() == content:
            continue
        changed.append(name)
        if not args.check:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    if args.check:
        for name in changed:
            print(f"Outdated companion sword asset {name}")
    else:
        print(f"Updated {len(changed)} companion sword assets")
    return int(args.check and bool(changed))


if __name__ == "__main__":
    raise SystemExit(main())
