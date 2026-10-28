"""将原作者真实三角面导出为穿戴网格、三维物品和同源像素 PNG。

这里只拥有导出；游戏数值只复制 balance.json，不计算玩法或配方。
"""
from __future__ import annotations

import copy
import hashlib
import json
import math
from pathlib import Path
import struct
import subprocess
import zlib

ROOT = Path(__file__).resolve().parents[2]
SLOTS = {"head": "helmet", "chest": "chestplate", "legs": "leggings", "feet": "boots"}
ROLES = {"head": "head", "chest": "chest", "legs": "legs", "boots": "feet", "aura": "chest"}
BONES = {
    "head": {"head"}, "chest": {"body", "left_arm", "right_arm"},
    "legs": {"body", "left_leg", "right_leg"}, "feet": {"left_leg", "right_leg"},
}


def json_bytes(value, *, compact=False):
    return (json.dumps(value, ensure_ascii=False, allow_nan=False,
                       **({"separators": (",", ":")} if compact else {"indent": 2})) + "\n").encode()


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def normalized(values):
    size = math.sqrt(sum(x * x for x in values))
    if size < 1e-12 or not math.isfinite(size):
        raise ValueError("零长度或非有限法线")
    return [v / size for v in values]


def nondegenerate(triangle):
    a, b, c = triangle
    ab = [b[i] - a[i] for i in range(3)]
    ac = [c[i] - a[i] for i in range(3)]
    cross = [ab[1]*ac[2]-ab[2]*ac[1], ab[2]*ac[0]-ab[0]*ac[2], ab[0]*ac[1]-ab[1]*ac[0]]
    return sum(n*n for n in cross) > 1e-24


def clip_polygon(polygon, axis, boundary, lower):
    """Sutherland–Hodgman，在 UV 边界插值位置与法线，保持面绕序。"""
    if not polygon:
        return []
    result = []
    previous = polygon[-1]
    pin = previous[axis] >= boundary if lower else previous[axis] <= boundary
    for current in polygon:
        cin = current[axis] >= boundary if lower else current[axis] <= boundary
        if cin != pin:
            t = (boundary - previous[axis]) / (current[axis] - previous[axis])
            value = [a + (b-a)*t for a, b in zip(previous, current)]
            value[axis] = boundary
            result.append(value)
        if cin:
            result.append(current)
        previous, pin = current, cin
    return result


def split_triangle(triangle, *, tile=True):
    """按两个 UV 周期逐格裁切，不对跨缝三角面的顶点独立取余。"""
    if not nondegenerate(triangle):
        return []
    if tile:
        lo = [math.floor(min(v[a] for v in triangle)) for a in (3, 4)]
        hi = [max(lo[i], math.ceil(max(v[a] for v in triangle))-1) for i, a in enumerate((3, 4))]
    else:
        if any(not -1e-5 <= v[a] <= 1+1e-5 for v in triangle for a in (3, 4)):
            raise ValueError("fit 材质的作者 UV 超出 0..1")
        lo = hi = [0, 0]
    result = []
    for u in range(lo[0], hi[0]+1):
        for v in range(lo[1], hi[1]+1):
            polygon = triangle
            if tile:
                for axis, low in ((3, u), (4, v)):
                    polygon = clip_polygon(polygon, axis, low, True)
                    polygon = clip_polygon(polygon, axis, low+1, False)
            for i in range(1, len(polygon)-1):
                tri = [list(polygon[j]) for j in (0, i, i+1)]
                if not nondegenerate(tri):
                    continue
                for vertex in tri:
                    vertex[3] = max(0, min(1, vertex[3]-u))
                    vertex[4] = max(0, min(1, 1-(vertex[4]-v)))
                    vertex[5:] = normalized(vertex[5:])
                result.append(tri)
    return result


def folded_mesh(mesh, spec):
    material = spec["materials"][mesh["material"]]
    # 主网格的像素 UV 由原内核生成。Tube 嵌边保留内核自身 UV 的周期语义。
    tile = material.get("pixelArt", {}).get("mapping", "tile") != "fit"
    vertices, indices, unique = [], [], {}
    source = mesh["vertices"]
    for offset in range(0, len(mesh["indices"]), 3):
        triangle = [source[j] for j in mesh["indices"][offset:offset+3]]
        for tri in split_triangle(triangle, tile=tile):
            for vertex in tri:
                if any(not math.isfinite(v) for v in vertex):
                    raise ValueError("网格包含非有限数值")
                key = tuple(round(v, 10) for v in vertex)
                if key not in unique:
                    unique[key] = len(vertices)
                    vertices.append(list(key))
                indices.append(unique[key])
    if not indices:
        raise ValueError("导出后空组件：" + mesh["id"])
    return {"id": mesh["id"], "slot": ROLES.get(mesh["role"], "ingot"),
            "material": mesh["material"], "side": mesh["side"],
            "detail": mesh["detail"], "vertices": vertices, "indices": indices}


def wearing_vertex(vertex, bone, origins, profile, rig):
    x, y, z, u, v, nx, ny, nz = vertex
    x, y, z = 16*x, 24-16*y, -16*z
    nx, ny, nz = nx, -ny, -nz
    if profile == "slim" and bone.endswith("_arm"):
        fit = rig["profiles"]["slim"]["arm_fit"]
        anchor = fit["inner_edge"] * (1 if bone == "left_arm" else -1)
        x = anchor + (x-anchor) * fit["width_scale"]
        y += fit["y_offset"]
        nx /= fit["width_scale"]
    normal = normalized([nx, ny, nz])
    # UV 与 OBJ 共用同一份十位精度，不随模型像素坐标再做八位舍入。
    return ([round(value, 8) for value in [x-origins[bone][0], y-origins[bone][1], z-origins[bone][2]]]
            + [u, v] + [round(value, 8) for value in normal])


def compile_profiles(meshes, rig_set, rig, bones):
    profiles = {}
    for profile in ("standard", "slim"):
        origins = copy.deepcopy(bones)
        origins.update(rig["profiles"][profile].get("bone_origin_overrides", {}))
        parts = []
        for mesh in meshes:
            bone = rig_set["components"][mesh["id"]]
            if bone not in BONES[mesh["slot"]]:
                raise ValueError(f"{mesh['id']}: {mesh['slot']} 的骨骼 {bone} 非法")
            parts.append({"id": mesh["id"], "slot": mesh["slot"], "bone": bone,
                          "material": mesh["material"], "side": mesh["side"],
                          "vertices": [wearing_vertex(v, bone, origins, profile, rig) for v in mesh["vertices"]],
                          "indices": mesh["indices"]})
        profiles[profile] = {"bone_origins": origins, "parts": parts}
    return profiles


def png(material):
    """PNG 行序与作者 rows 一致；alpha 在材质 opacity / MTL Kd.a 中只应用一次。"""
    art = material["pixelArt"]
    rows, palette = art["rows"], art["palette"]
    size = len(rows)
    raw = b"".join(b"\x00" + b"".join(bytes.fromhex(palette[int(c)][1:]) + b"\xff" for c in row) for row in rows)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind+data) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
            + chunk(b"sRGB", b"\x00") + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def texture_id(design, material):
    return f"myvillage:immortal_equipment/{design}/{material}"


def item_assets(spec, meshes, suffix, root):
    """同一真实网格用于 GUI、手持、地面；OBJ 维持作者 Y 向上与 X 朝向。"""
    from tools.equipment.mesh import item_display
    item_id = spec["id"] + "_" + suffix
    base = f"immortal_equipment/{item_id}"
    materials = list(dict.fromkeys(m["material"] for m in meshes))
    lines = ["# 原作者三角面；生成文件，禁止手改。", f"mtllib myvillage:models/{base}.mtl"]
    count = 0
    for mesh in meshes:
        lines.extend(["o " + mesh["id"], "usemtl " + mesh["material"]])
        for v in mesh["vertices"]:
            lines.append("v " + " ".join(format(n, ".10g") for n in v[:3]))
        for v in mesh["vertices"]:
            lines.append("vt " + " ".join(format(n, ".10g") for n in v[3:5]))
        for v in mesh["vertices"]:
            lines.append("vn " + " ".join(format(n, ".10g") for n in v[5:]))
        for i in range(0, len(mesh["indices"]), 3):
            face = [count+j+1 for j in mesh["indices"][i:i+3]]
            lines.append("f " + " ".join(f"{j}/{j}/{j}" for j in face))
        count += len(mesh["vertices"])
    mtl = []
    for key in materials:
        material = spec["materials"][key]
        # NeoForge 21.1 的 ObjModel 烘焙只读取 diffuseColor.a，不使用已解析的 dissolve。
        # Kd 的第四分量为其受支持扩展；d 同时保留给标准 OBJ 阅读器。
        opacity = material.get("opacity", 1)
        mtl.extend(["newmtl " + key, f"Kd 1 1 1 {opacity}", "Ka 0 0 0", f"d {opacity}",
                    "map_Kd " + texture_id(spec["id"], key), ""])
    # 只将真实网格的包围框交给已有取景工具，不生成或替代任何几何。
    points = [v[:3] for m in meshes for v in m["vertices"]]
    bounds = [[min(v[a] for v in points)*16 for a in range(3)],
              [max(v[a] for v in points)*16 for a in range(3)]]
    model = {"loader": "neoforge:obj", "model": f"myvillage:models/{base}.obj",
             "automatic_culling": False, "shade_quads": False, "flip_v": False,
             "emissive_ambient": False, "gui_light": "side",
             "render_type": "minecraft:translucent" if any(spec["materials"][k].get("opacity", 1) < 1 for k in materials) else "minecraft:cutout",
             "textures": {"particle": texture_id(spec["id"], materials[0])},
             "display": item_display([{"points": bounds}], armor=True, root=root)}
    return {f"models/{base}.obj": ("\n".join(lines)+"\n").encode(),
            f"models/{base}.mtl": ("\n".join(mtl).rstrip()+"\n").encode(),
            f"models/item/{item_id}.json": json_bytes(model)}


def validate_rig(spec, extracted, rig_set):
    expected = {c["id"] for c in spec["components"]}
    if set(rig_set["components"]) != expected:
        raise ValueError(f"{spec['id']}: rig 必须逐项列出全部组件 ID，不允许猜测或遗留绑定")
    effects = extracted["effects"]
    bindings = rig_set["effect_bindings"]
    for kind in ("flows", "arcs"):
        if len(bindings[kind]) != len(effects[kind]):
            raise ValueError(f"{spec['id']}: {kind} 绑定与作者效果数量不符")
        for effect, bone in zip(effects[kind], bindings[kind]):
            if bone not in BONES[ROLES[effect["role"]]]:
                raise ValueError("动态骨骼不合法")
    for kind in ("motions", "glows"):
        for effect in effects[kind]:
            if not set(effect["targets"]) <= expected:
                raise ValueError(f"{spec['id']}: 未知动态目标")
            if kind == "motions" and len({rig_set["components"][c] for c in effect["targets"]}) != 1:
                raise ValueError("motion 不能跨骨骼")


def outputs(root=ROOT):
    root = Path(root)
    atlas = root / "art/immortal-armor-atlas"
    specs = [d for level in range(1, 6) for d in read(atlas / f"levels/level{level}.json")]
    if len(specs) != 15 or len({d["id"] for d in specs}) != 15:
        raise ValueError("必须保留十五套已确认 ID")
    rig = read(root / "equipment/immortal/rig.json")
    balance = read(root / "equipment/immortal/balance.json")
    bones = read(root / "equipment/poses.json")["bones"]
    if rig["version"] != 1 or balance["version"] != 1 or set(rig["sets"]) != {d["id"] for d in specs}:
        raise ValueError("rig/balance 版本或套装身份错误")
    run = subprocess.run(["node", str(Path(__file__).with_name("extract.cjs")), str(root)],
                         check=False, capture_output=True, text=True)
    if run.returncode:
        raise RuntimeError("原作者网格提取失败：\n" + run.stderr)
    extracted = {d["id"]: d for d in json.loads(run.stdout)}
    resources = root / "src/main/resources"
    assets = resources / "assets/myvillage"
    out, sets, names, atlas_sources = {}, [], {}, []
    for spec in specs:
        design = spec["id"]
        source = extracted[design]
        rig_set = rig["sets"][design]
        validate_rig(spec, source, rig_set)
        armor = [folded_mesh(mesh, spec) for mesh in source["armor"]]
        ingot = [folded_mesh(mesh, spec) for mesh in source["ingot"]]
        materials = {}
        for key, material in spec["materials"].items():
            path = f"textures/immortal_equipment/{design}/{key}.png"
            out[assets / path] = png(material)
            out[assets / (path + ".mcmeta")] = json_bytes({"texture": {"blur": False, "clamp": True}})
            materials[key] = {"texture": "myvillage:" + path, "opacity": material.get("opacity", 1)}
            atlas_sources.append({"type": "minecraft:single", "resource": texture_id(design, key), "sprite": texture_id(design, key)})
        model = {"version": 1, "set_id": design, "materials": materials,
                 "profiles": compile_profiles(armor, rig_set, rig, bones),
                 "effects": source["effects"], "effect_bindings": rig_set["effect_bindings"]}
        out[assets / f"immortal_equipment/{design}.json"] = json_bytes(model, compact=True)
        entry = {"id": design, "name": spec["name"], "level": spec["level"],
                 "model": f"myvillage:immortal_equipment/{design}.json",
                 "texture": next(iter(materials.values()))["texture"],
                 "ingot": {"id": f"myvillage:{design}_ingot", "name": spec["ingot"]["name"]}, "armor": []}
        for slot, suffix in SLOTS.items():
            role = "boots" if slot == "feet" else slot
            item = {"id": f"myvillage:{design}_{suffix}", "slot": slot, "name": spec["parts"][role]["name"]}
            entry["armor"].append(item)
            out.update({assets / path: data for path, data in item_assets(spec, [m for m in armor if m["slot"] == slot], suffix, root).items()})
            names["item.myvillage." + design + "_" + suffix] = item["name"]
        out.update({assets / path: data for path, data in item_assets(spec, ingot, "ingot", root).items()})
        names["item.myvillage." + design + "_ingot"] = spec["ingot"]["name"]
        sets.append(entry)
    out[resources / "data/myvillage/immortal_equipment/catalogue.json"] = json_bytes({"version": 1, "tiers": balance["tiers"], "sets": sets})
    # 此清单供 blocks 图集原拥有者合并，绝不直接写共享 blocks.json。
    out[assets / "immortal_equipment/atlas_sources.json"] = json_bytes({"sources": atlas_sources})
    for slot, tag in (("head", "head_armor"), ("chest", "chest_armor"), ("legs", "leg_armor"), ("feet", "foot_armor")):
        path = resources / f"data/minecraft/tags/item/{tag}.json"
        current = read(path) if path.exists() else {"replace": False, "values": []}
        for entry in sets:
            item = next(a["id"] for a in entry["armor"] if a["slot"] == slot)
            if item not in current["values"]:
                current["values"].append(item)
        out[path] = json_bytes(current)
    path = resources / "data/minecraft/tags/item/trimmable_armor.json"
    current = read(path) if path.exists() else {"replace": False, "values": [], "remove": []}
    current.setdefault("remove", [])
    for entry in sets:
        for item in entry["armor"]:
            if item["id"] not in current["remove"]:
                current["remove"].append(item["id"])
    out[path] = json_bytes(current)
    lang = assets / "lang/zh_cn.json"
    translations = read(lang)
    translations.update(names)
    out[lang] = json_bytes(translations)
    # 确切输入哈希用于区分导出身份，不能复用别的美术源证据。
    inputs = sorted([*atlas.glob("levels/*.json"), atlas / "core/engine.js", atlas / "effects/catalogue.js",
                     root / "tools/web/three.min.js", root / "equipment/immortal/rig.json",
                     root / "equipment/immortal/balance.json", root / "equipment/poses.json", root / "equipment/display.json",
                     *Path(__file__).parent.glob("*.py"), Path(__file__).with_name("extract.cjs")])
    out[assets / "immortal_equipment/provenance.json"] = json_bytes({"version": 1,
        "inputs": {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs},
        "set_count": len(sets), "item_count": len(names)})
    return out


def write_or_check(root=ROOT, *, check=False):
    root = Path(root)
    rendered = outputs(root)
    catalogue = root / "src/main/resources/data/myvillage/immortal_equipment/catalogue.json"
    if catalogue.exists():
        old_ids = {a["id"] for s in read(catalogue)["sets"] for a in [*s["armor"], s["ingot"]]}
        new_ids = {a["id"] for s in json.loads(rendered[catalogue])["sets"] for a in [*s["armor"], s["ingot"]]}
        if old_ids - new_ids:
            raise ValueError("不得删除原注册装备 ID：" + ", ".join(sorted(old_ids-new_ids)))
    changed = []
    for path, data in rendered.items():
        if not path.exists() or path.read_bytes() != data:
            changed.append(str(path.relative_to(root)))
            if not check:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
    return changed
