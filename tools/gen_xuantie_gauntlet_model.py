#!/usr/bin/env python3
"""由三款确认的 GLB 导出双手拳套及日魄乌金动效。"""

from __future__ import annotations

import argparse
from functools import cache
from io import BytesIO
import math
import sys
from pathlib import Path

try:
    from tools.equipment import gauntlet_heat, gauntlet_mesh
    from tools.equipment import geometry as model_geometry
except ModuleNotFoundError:
    from equipment import gauntlet_heat, gauntlet_mesh
    from equipment import geometry as model_geometry

try:
    from tools import gen_qingfeng_sword_model as qf
except ImportError:  # run as a script: tools/ itself is on sys.path
    import gen_qingfeng_sword_model as qf

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/yuanxian"
SOURCES = ROOT / "equipment/weapons/gauntlets"
BASE = "xuantie_gauntlet"
JINWU = "jindan_jinwu_gauntlet"
ITEMS = (BASE, "zhuji_baiqing_lingyun_gauntlet", JINWU)
BROW = dict(zip(ITEMS, ("手背至拳峰的折甲", "折脊护手", "宽拳面折边")))
_r = qf._r

CX = CZ = GRIP_Y = 8.0
FIST = (4.0, 12.0)
CUFF_END_Y = 1.0
CUFF_Y = (CUFF_END_Y, 4.4)
HAND_Y = (4.4, 11.4)
KNUCKLE_Y = (11.4, 12.6)
FINGER_TOP = 13.2
KNUCKLE_TOP = 13.5
HEAD_Y = (12.6, KNUCKLE_TOP)
TRAIL_Y = (11.4, KNUCKLE_TOP)
THIRD_PERSON_SCALE = 0.5


GRIP_CENTER = (CX, GRIP_Y, CZ)
# Third person, in ItemInHandLayer's item frame (see qf.FIST_CENTER_ITEM: item +X = arm -X,
# item +Y = arm -Z (forward), item +Z = arm -Y (toward the shoulder)): model +X (palm) onto the
# arm's inner side (item -X), model +Y (wrist to knuckles) down the arm toward the hand (item -Z),
# model -Z (thumb) onto the arm's front (item +Y), i.e. model +Z onto item -Y.
WORN_AXES = ((-1.0, 0.0, 0.0), (0.0, 0.0, -1.0), (0.0, -1.0, 0.0))   # images of model X, Y, Z
FIRST_PERSON_SCALE = 0.7
GROUND_SCALE = 0.5
FIXED_SCALE = 0.75
HEAD_SCALE = 0.6


def worn_rotation():
    """Euler XYZ (degrees) of the rotation whose columns are the images of model X, Y and Z."""
    m = [[WORN_AXES[c][r] for c in range(3)] for r in range(3)]
    return tuple(round(a, 2) + 0.0 for a in model_geometry.to_euler_xyz(m))


def placed(rotation, scale: float, anchor, target) -> dict:
    offset = model_geometry.mv(model_geometry.euler_xyz(rotation), tuple(scale * (anchor[i] - 8.0) for i in range(3)))
    translation = tuple(round(target[i] - offset[i], 3) + 0.0 for i in range(3))
    if any(abs(t) > 80.0 for t in translation):
        raise ValueError(f"translation {translation} exceeds the 5-block clamp")
    return {"rotation": tuple(rotation), "translation": translation, "scale": (scale, scale, scale)}


def build_display(item: str = BASE) -> dict:
    third = placed(worn_rotation(), THIRD_PERSON_SCALE, GRIP_CENTER, qf.FIST_CENTER_ITEM)
    # Outside combat the first-person hold shows the fist knuckles-forward where the vanilla
    # sprite's handle sat: the punch axis takes the old blade direction, the palm its normal.
    first = qf.derive("firstperson_righthand", GRIP_CENTER, anchor_old=qf.OLD_HANDLE_CENTER, scale=FIRST_PERSON_SCALE)
    center = model_center(item)
    return {
        "thirdperson_righthand": third,
        "thirdperson_lefthand": dict(third),   # the game mirrors a left-hand entry itself (see the spear generator)
        "firstperson_righthand": first,
        "firstperson_lefthand": dict(first),
        "ground": qf.derive("ground", center, anchor_old=qf.OLD_SPRITE_CENTER, scale=GROUND_SCALE),
        "head": qf.derive("head", center, anchor_old=qf.OLD_SPRITE_CENTER, scale=HEAD_SCALE),
        "fixed": qf.derive("fixed", center, anchor_old=qf.OLD_SPRITE_CENTER, scale=FIXED_SCALE),
    }


def _json_display(display: dict) -> dict:
    return {k: {key: [_r(c) for c in v[key]] for key in ("rotation", "translation", "scale")}
            for k, v in display.items()}


@cache
def source_mesh(item: str = BASE) -> tuple[list[dict], bytes]:
    return gauntlet_mesh.read_source(SOURCES / f"{item}.glb")


@cache
def mesh_bounds(item: str = BASE) -> tuple[tuple, tuple]:
    meshes, _ = source_mesh(item)
    points = [gauntlet_mesh.model_point(p) for mesh in meshes for p in mesh["vertices"]]
    axes = tuple(zip(*points))
    return tuple(min(a) for a in axes), tuple(max(a) for a in axes)


def model_center(item: str) -> tuple[float, float, float]:
    lo, hi = mesh_bounds(item)
    return tuple((a + b) / 2 for a, b in zip(lo, hi))


def build_model_3d(item: str = BASE) -> dict:
    model = {
        "loader": "neoforge:obj",
        "model": f"yuanxian:models/item/{item}_3d.obj",
        "automatic_culling": False,
        "shade_quads": True,
        "flip_v": False,
        "emissive_ambient": item == JINWU,
        "gui_light": "side",
        "textures": {"particle": f"yuanxian:item/{item}_model"},
        "display": _json_display(build_display(item)),
    }
    if item == JINWU:
        model["render_type"] = "minecraft:cutout"
    return model


def build_model(item: str = BASE) -> dict:
    texture = f"yuanxian:item/{item}"
    return {
        "loader": "neoforge:separate_transforms",
        "gui_light": "front",
        "base": {"parent": f"yuanxian:item/{item}_3d"},
        "perspectives": {"gui": {"parent": "minecraft:item/handheld", "textures": {"layer0": texture}}},
        "textures": {"particle": texture},
    }


def geometry_contract(item: str = BASE) -> dict:
    return {
        "format": 2,
        "units": "model_pixels",
        "generator": "tools/gen_xuantie_gauntlet_model.py",
        "model": f"yuanxian:item/{item}_3d",
        "axes": {"length": "+y", "flat_normal": "x", "edge": "z", "center_x": CX, "center_z": CZ},
        "grip_center": [CX, GRIP_Y, CZ],
        "handle": {"y": list(HAND_Y), "half_width": 4.6,
                   "half_thickness": 4.6},
        "collar": {"y": list(KNUCKLE_Y), "half_width": 4.4,
                   "half_thickness": 5.4},
        "butt": {"y": list(CUFF_Y), "half_width": 4.85,
                 "half_thickness": 4.85},
        "head": {"half_width": 4.5, "half_thickness": 5.4,
                 "ridge_half_thickness": 5.4, "taper_start_y": FINGER_TOP},
        "head_base": [CX, HEAD_Y[0], CZ],
        "head_tip": [CX, HEAD_Y[1], CZ],
        "trail": {"base": [CX, TRAIL_Y[0], CZ], "tip": [CX, TRAIL_Y[1], CZ]},
        "edge_axis": [0.0, 0.0, 1.0],
        "flat_axis": [1.0, 0.0, 0.0],
        "overall_y": [CUFF_END_Y, KNUCKLE_TOP],
    }



def render_icon(meshes: list[dict], texture: bytes) -> bytes:
    # Same fitted geometry and albedo as the held item, rasterised at a pixel grid.
    from PIL import Image
    import numpy as np
    from tools.combat_preview.item_model import Camera, Quad, render

    tex = np.asarray(Image.open(BytesIO(texture)).convert("RGBA"), dtype=float) / 255
    paint, silhouette = [], []
    for mesh in meshes:
        for start in range(0, len(mesh["indices"]), 3):
            tri = mesh["indices"][start:start + 3]
            quad = [*tri, tri[-1]]
            points = [mesh["vertices"][i] for i in quad]
            uv = [tuple(16 * axis for axis in mesh["uvs"][i]) for i in quad]
            paint.append(Quad(points, uv, tex))
            silhouette.append(Quad(points, color=(1, 1, 1), shade=False))
    camera = Camera((.64, .32, .70), up=(.54, .79, -.33))
    options = dict(size=32, ss=1, margin=.09, bg=(0, 0, 0))
    pixels = render(paint, camera, **options)[0].convert("RGBA")
    pixels.putalpha(render(silhouette, camera, **options)[0].getchannel("R"))
    icon = pixels.resize((64, 64), Image.Resampling.NEAREST)
    data = list(icon.getdata())
    return model_geometry.encode_png([data[i:i + 64] for i in range(0, len(data), 64)])


@cache
def outputs() -> dict[Path, bytes]:
    result = {}
    for item in ITEMS:
        meshes, texture = source_mesh(item)
        obj_path = ASSETS / f"models/item/{item}_3d.obj"
        glow_texture = None
        visual = meshes
        if item == JINWU:
            glow_texture = f"yuanxian:item/{item}_heat"
            visual = meshes + gauntlet_heat.overlay_meshes(meshes)
            heat, metadata = gauntlet_heat.animated_texture()
            result[ASSETS / f"textures/item/{item}_heat.png"] = heat
            result[ASSETS / f"textures/item/{item}_heat.png.mcmeta"] = model_geometry.render_model_json(metadata).encode()
        obj, mtl = gauntlet_mesh.export_obj(visual, f"yuanxian:models/item/{item}_3d",
                                           f"yuanxian:item/{item}_model", glow_texture)
        result.update({
            obj_path: obj,
            obj_path.with_suffix(".mtl"): mtl,
            obj_path.with_suffix(".json"): model_geometry.render_model_json(build_model_3d(item)).encode(),
            ASSETS / f"models/item/{item}.json": model_geometry.render_model_json(build_model(item)).encode(),
            ASSETS / f"textures/item/{item}_model.png": texture,
            ASSETS / f"textures/item/{item}.png": render_icon(meshes, texture),
            ASSETS / f"combat/{item}_geometry.json": model_geometry.render_model_json(geometry_contract(item)).encode(),
        })
    return result


def self_check() -> list[str]:
    errors = []
    for item in ITEMS:
        lo, hi = mesh_bounds(item)
        if any(a < -16 or b > 32 or a >= b for a, b in zip(lo, hi)):
            errors.append(f"{item} has vertices outside the item space")
        third = build_display(item)["thirdperson_righthand"]
        if math.dist(model_geometry.display_point(third, GRIP_CENTER), qf.FIST_CENTER_ITEM) > .01:
            errors.append(f"{item} has a shifted fist pivot")
        along = model_geometry.normalize(model_geometry.display_vector(third, (0, 1, 0)))
        if math.dist(along, (0, 0, -1)) > 1e-6:
            errors.append(f"{item} points away from the fist")
        meshes, _ = source_mesh(item)
        brow = [point for mesh in meshes if mesh["part"] == BROW[item] for point in mesh["vertices"]]
        if not brow or not 4.6 <= max(p[1] for p in brow) <= 6:
            errors.append(f"{item} has a misplaced knuckle plate")
    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--report", action="store_true")
    args = parser.parse_args(argv)
    errors = self_check()
    if args.report:
        for item in ITEMS:
            meshes, _ = source_mesh(item)
            print(item, sum(len(m["indices"]) // 3 for m in meshes), "triangles", mesh_bounds(item))
    paths = outputs()
    if args.check:
        errors.extend(f"{path.relative_to(ROOT)} differs from the generator"
                      for path,data in paths.items() if not path.is_file() or path.read_bytes() != data)
    elif not args.report and not errors:
        for path, data in paths.items():
            path.write_bytes(data)
    for error in errors:
        print("ERROR:", error, file=sys.stderr)
    if not errors:
        print(f"OK {len(ITEMS)} gauntlet models, {len(paths)} resource files")
    return int(bool(errors))


if __name__ == "__main__":
    sys.path.insert(0, str(ROOT))
    sys.exit(main())
