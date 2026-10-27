"""Small authored equipment catalogue; art ownership remains explicit."""
from __future__ import annotations

import json
import math
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ID = re.compile(r"myvillage:[a-z][a-z0-9_]*$")
SLOTS = {"head": {"head"}, "chest": {"body", "right_arm", "left_arm"},
         "legs": {"body", "right_leg", "left_leg"}, "feet": {"right_leg", "left_leg"}}
PROFILES = ("standard", "slim")


def read(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def vector(value, name, *, positive=False):
    if not isinstance(value, list) or len(value) != 3 or any(
            isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v)
            or abs(v) > 64 or (positive and v <= 0) for v in value):
        raise ValueError(f"{name}: 需要三个有界数值")


def validate(spec: dict, materials: dict) -> None:
    if spec.get("version") != 1 or not ID.fullmatch(spec.get("id", "")):
        raise ValueError("装备 version/id 无效")
    if not isinstance(spec.get("name"), str) or not spec["name"].strip():
        raise ValueError("装备缺少中文名称")
    if spec.get("kind") == "weapon_reference":
        for key in ("generator", "model", "geometry", "weapon"):
            if not isinstance(spec.get(key), str) or not spec[key]:
                raise ValueError(f"武器引用缺少 {key}")
        return
    if spec.get("kind") != "armor_set":
        raise ValueError("未知装备 kind")
    if "enabled" in spec and type(spec["enabled"]) is not bool:
        raise ValueError("enabled 必须为布尔值")
    if spec.get("material") not in ("minecraft:iron",):
        raise ValueError("当前基础防具仅支持原版 iron 材质")
    multiplier = spec.get("durability_multiplier")
    if type(multiplier) is not int or not 1 <= multiplier <= 100:
        raise ValueError("durability_multiplier 必须为 1..100")
    if spec.get("texture_size") not in (64, 128, 256, 512):
        raise ValueError("texture_size 必须为 64/128/256/512")
    if spec.get("texel_density") not in (1, 2, 4):
        raise ValueError("texel_density 必须为 1/2/4")
    ids, slots = set(), set()
    for item in spec.get("items", []):
        if not ID.fullmatch(item.get("id", "")) or item["id"] in ids:
            raise ValueError("防具物品 id 无效或重复")
        ids.add(item["id"])
        slot = item.get("slot")
        if slot not in SLOTS or slot in slots:
            raise ValueError("防具 slot 无效或重复")
        slots.add(slot)
        if not item.get("name") or not item.get("boxes") or len(item["boxes"]) > 256:
            raise ValueError("防具必须有名称及 1..256 个部件")
        names = set()
        for box in item["boxes"]:
            name = box.get("name")
            if not isinstance(name, str) or not name or name in names:
                raise ValueError("部件名称无效或重复")
            names.add(name)
            if box.get("bone") not in SLOTS[slot]:
                raise ValueError(f"{name}: {slot} 不允许挂到 {box.get('bone')}")
            if box.get("material") not in materials:
                raise ValueError(f"{name}: 未知材质 {box.get('material')}")
            vector(box.get("from"), name + ".from")
            vector(box.get("size"), name + ".size", positive=True)
            for key in ("pivot", "rotation"):
                if key in box:
                    vector(box[key], name + "." + key)
            if box.get("fit", "fixed") not in ("fixed", "arm"):
                raise ValueError(f"{name}: 未知 fit")
            if box.get("fit") == "arm" and not box["bone"].endswith("_arm"):
                raise ValueError(f"{name}: arm fit 只能用于手臂")
    if not ids:
        raise ValueError("空防具集合")


def load(root: Path = ROOT):
    material_doc = read(root / "equipment/materials.json")
    if material_doc.get("version") != 1:
        raise ValueError("材质目录 version 无效")
    materials = material_doc["materials"]
    for name, mat in materials.items():
        if mat.get("pattern") not in ("metal", "cloth", "leather", "jade"):
            raise ValueError(f"{name}: 未知材质 pattern")
        for key in ("base", "light", "dark"):
            if not re.fullmatch(r"#[0-9a-fA-F]{6}", mat.get(key, "")):
                raise ValueError(f"{name}.{key}: 无效颜色")
    specs, ids = [], set()
    for path in sorted((root / "equipment").glob("*/equipment.json")):
        spec = read(path)
        validate(spec, materials)
        owned = [spec["id"]] + [i["id"] for i in spec.get("items", [])]
        if any(i in ids for i in owned) or len(set(owned)) != len(owned):
            raise ValueError(f"{path}: 目录中有重复 id")
        ids.update(owned)
        specs.append(spec)
    return specs, materials
