"""八州主城 exchange schema 1 的定义与显式读取辅助。

只读取调用者指定的文件，不生成资源、不加载世界、不推断地形完成。
本模块没有 CLI，也不会在 import 时读取目录或运行检查。
"""

from __future__ import annotations

from copy import deepcopy
import json
import math
from pathlib import Path
import re
from typing import Any, Mapping


SCHEMA_VERSION = 1
STAGE = "preview"
UNITS = "block"
COORDINATE_SYSTEM = "+X east, +Y up, +Z south; origin local; 1 authored metre = 1 horizontal block"
STABLE_REGION_IDS = (
    "zhongzhou", "donghai", "ximo", "nanjiang", "beiyuan", "donghuang", "lingyue", "moyu",
)
REQUIRED_KEYS = (
    "schema", "city_id", "region_id", "stage", "units", "bounds", "source", "terrain",
    "districts", "roads", "buildings", "entries", "services",
)
CATALOG_PATH = Path(__file__).resolve().parents[2] / "blueprints/province_capitals/catalog.json"
_ID_PATTERN = r"^[a-zA-Z0-9_.:/-]{1,96}$"
_ID = {"type": "string", "pattern": _ID_PATTERN}
_TEXT = {"type": "string", "minLength": 1, "pattern": r"\S"}
_POINT = {"type": "array", "items": {"type": "number"}, "minItems": 3, "maxItems": 3}
_BOUNDS = {
    "type": "object", "required": ["min", "max"],
    "properties": {"min": _POINT, "max": _POINT}, "additionalProperties": True,
}


def _object_schema(properties: dict[str, Any]) -> dict[str, Any]:
    return {"type": "object", "required": list(properties), "properties": properties, "additionalProperties": True}


MANIFEST_SCHEMA = {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "八州主城预览交换格式",
    "type": "object",
    "required": list(REQUIRED_KEYS),
    "additionalProperties": True,
    "properties": {
        "schema": {"type": "integer", "const": SCHEMA_VERSION},
        "city_id": _ID,
        "region_id": {"type": "string", "enum": list(STABLE_REGION_IDS)},
        "stage": {"const": STAGE},
        "units": {"const": UNITS},
        "bounds": _BOUNDS,
        "source": _object_schema({"kind": _TEXT, "ref": _TEXT}),
        "terrain": _object_schema({
            "kind": _TEXT,
            "data": {"oneOf": [_TEXT, {"type": "object"}, {"type": "array"}]},
        }),
        "districts": {"type": "array", "items": _object_schema({"id": _ID, "role": _TEXT, "bounds": _BOUNDS})},
        "roads": {"type": "array", "items": _object_schema({
            "id": _ID, "kind": _TEXT, "width": {"type": "number", "exclusiveMinimum": 0},
            "points": {"type": "array", "items": _POINT, "minItems": 2},
        })},
        "buildings": {"type": "array", "items": _object_schema({
            "id": _ID, "role": _TEXT, "template": _TEXT, "position": _POINT,
            "rotation_quarters": {"type": "integer", "minimum": 0, "maximum": 3},
        })},
        "entries": {"type": "array", "items": _object_schema({
            "id": _ID, "position": _POINT, "facing": {"enum": ["north", "south", "east", "west"]},
        })},
        "services": {"type": "array", "items": _object_schema({
            "id": _ID, "role": _TEXT, "building_id": _ID, "position": _POINT,
        })},
    },
}


class ManifestError(ValueError):
    """读取时发现结构或身份不相容；不表示曾运行独立 validator。"""


def manifest_schema() -> dict[str, Any]:
    """返回可交给未来消费者的 JSON Schema 副本，不执行检查。"""
    return deepcopy(MANIFEST_SCHEMA)


def _object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ManifestError(f"{label} 必须为对象")
    return value


def _required(value: dict[str, Any], key: str) -> Any:
    if key not in value or value[key] is None:
        raise ManifestError(f"缺少字段 {key}")
    return value[key]


def _text(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ManifestError(f"{label} 必须为非空文本")
    return value


def _id(value: Any, label: str) -> str:
    text = _text(value, label)
    if not re.fullmatch(_ID_PATTERN, text):
        raise ManifestError(f"{label} 必须为 1..96 字符的稳定身份")
    return text


def _number(value: Any, label: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ManifestError(f"{label} 必须为有限数值")
    try:
        number = float(value)
    except (ValueError, OverflowError) as failure:
        raise ManifestError(f"{label} 超出可表示数值范围") from failure
    if not math.isfinite(number):
        raise ManifestError(f"{label} 必须为有限数值")
    return number


def _integer(value: Any, label: str) -> int:
    number = _number(value, label)
    if not number.is_integer() or not -(2**31) <= number < 2**31:
        raise ManifestError(f"{label} 必须为 int 整数")
    return int(number)


def _point(value: Any, label: str) -> tuple[float, float, float]:
    if not isinstance(value, list) or len(value) != 3:
        raise ManifestError(f"{label} 必须为 [x,y,z]")
    return tuple(_number(axis, label) for axis in value)


def _bounds(value: Any, label: str) -> None:
    value = _object(value, label)
    low, high = _point(_required(value, "min"), label), _point(_required(value, "max"), label)
    if any(a > b for a, b in zip(low, high)):
        raise ManifestError(f"{label} 的 min 必须不大于 max")


def _items(root: dict[str, Any], key: str) -> list[dict[str, Any]]:
    values = _required(root, key)
    if not isinstance(values, list):
        raise ManifestError(f"{key} 必须为数组")
    seen = set()
    for value in values:
        _object(value, key)
        identity = _id(_required(value, "id"), f"{key}.id")
        if identity in seen:
            raise ManifestError(f"{key} 身份重复: {identity}")
        seen.add(identity)
    return values


def read_manifest(data: Mapping[str, Any], *, catalog: Mapping[str, Any] | None = None) -> dict[str, Any]:
    """显式解码并保留所有扩展字段；不读取 terrain.data，不确认地形/设施存在。

    空数组可表示白模阶段的缺项，不能据此创建运行时主城。
    传入 catalog 时才额外要求新 city_id 和 region_id 配对一致。
    """
    root = _object(deepcopy(dict(data)), "manifest")
    for key in REQUIRED_KEYS:
        _required(root, key)
    if _integer(root["schema"], "schema") != SCHEMA_VERSION or root["stage"] != STAGE or root["units"] != UNITS:
        raise ManifestError("仅支持 exchange schema=1, stage=preview, units=block")
    _id(root["city_id"], "city_id")
    if root["region_id"] not in STABLE_REGION_IDS:
        raise ManifestError(f"未知核心州域: {root['region_id']}")
    _bounds(root["bounds"], "bounds")
    for name in ("source", "terrain"):
        item = _object(root[name], name)
        _text(_required(item, "kind"), f"{name}.kind")
    _text(_required(root["source"], "ref"), "source.ref")
    terrain_data = _required(root["terrain"], "data")
    if not isinstance(terrain_data, (dict, list, str)) or isinstance(terrain_data, str) and not terrain_data.strip():
        raise ManifestError("terrain.data 必须为相对路径或内联数组/对象")
    for district in _items(root, "districts"):
        _text(_required(district, "role"), "district.role")
        _bounds(_required(district, "bounds"), "district.bounds")
    for road in _items(root, "roads"):
        _text(_required(road, "kind"), "road.kind")
        if _number(_required(road, "width"), "road.width") <= 0:
            raise ManifestError("道路需要正宽度")
        points = _required(road, "points")
        if not isinstance(points, list) or len(points) < 2:
            raise ManifestError("道路需要至少两个路径点")
        for point in points:
            _point(point, "road.points")
    buildings = _items(root, "buildings")
    for building in buildings:
        for key in ("role", "template"):
            _text(_required(building, key), f"building.{key}")
        _point(_required(building, "position"), "building.position")
        if not 0 <= _integer(_required(building, "rotation_quarters"), "rotation_quarters") <= 3:
            raise ManifestError("建筑旋转必须为 0..3")
    for entry in _items(root, "entries"):
        _point(_required(entry, "position"), "entry.position")
        if _required(entry, "facing") not in ("north", "south", "east", "west"):
            raise ManifestError("未知城市入口朝向")
    building_ids = {item["id"] for item in buildings}
    for service in _items(root, "services"):
        _text(_required(service, "role"), "service.role")
        building_id = _id(_required(service, "building_id"), "service.building_id")
        if building_id not in building_ids:
            raise ManifestError(f"服务锚点引用不存在的建筑: {service['id']}")
        _point(_required(service, "position"), "service.position")
    if catalog is not None:
        definition = capital_for_region(catalog, root["region_id"])
        if root["city_id"] != definition["city_id"]:
            raise ManifestError("manifest 的 city_id 与目录身份不一致")
    return root


def _read_json(path: str | Path) -> dict[str, Any]:
    # json 默认接受 NaN/Infinity；禁止把它们作为合法 JSON 扩展读入。
    def reject_constant(value: str) -> None:
        raise ManifestError(f"JSON 不允许常量 {value}")

    with Path(path).open(encoding="utf-8") as handle:
        return _object(json.load(handle, parse_constant=reject_constant), str(path))


def load_manifest(path: str | Path, *, catalog: Mapping[str, Any] | None = None) -> dict[str, Any]:
    return read_manifest(_read_json(path), catalog=catalog)


def load_catalog(path: str | Path = CATALOG_PATH) -> dict[str, Any]:
    root = _read_json(path)
    if _integer(_required(root, "schema"), "schema") != 1 or root.get("activation") != "foundation_only":
        raise ManifestError("仅支持未激活的八州基础目录")
    policy = _object(_required(root, "region_policy"), "region_policy")
    if (policy.get("scope") != "core_only" or _integer(policy.get("region_count"), "region_count") != len(STABLE_REGION_IDS)
            or _integer(policy.get("capitals_per_region"), "capitals_per_region") != 1
            or policy.get("outer_replicas_have_capital") is not False
            or policy.get("on_candidate_exhaustion") != "fail_whole_plan"):
        raise ManifestError("目录必须要求八个核心州域各一城，失败不得跳过")
    definitions = _required(root, "capitals")
    if not isinstance(definitions, list) or len(definitions) != len(STABLE_REGION_IDS):
        raise ManifestError("目录必须包含八座主城")
    regions, cities = set(), set()
    for definition in definitions:
        _object(definition, "capital")
        region = _text(_required(definition, "region_id"), "region_id")
        city = _id(_required(definition, "city_id"), "city_id")
        if region not in STABLE_REGION_IDS or region in regions or city in cities:
            raise ManifestError("州域与主城身份必须一一对应")
        regions.add(region)
        cities.add(city)
        for key in ("display_name", "appearance_id", "layout_id", "target_scale", "terrain_profile", "definition_stage"):
            _text(_required(definition, key), key)
        roles = _required(definition, "required_service_roles")
        if not isinstance(roles, list) or not roles or len(set(_text(role, "service_role") for role in roles)) != len(roles):
            raise ManifestError("服务职责必须为非空且无重复的文本数组")
    return root


def capital_for_region(catalog: Mapping[str, Any], region_id: str) -> dict[str, Any]:
    """返回定义副本；目录不存在该核心州域时拒绝，不选择第一个城市兜底。"""
    matches = [item for item in catalog["capitals"] if item["region_id"] == region_id]
    if len(matches) != 1:
        raise ManifestError(f"核心州域没有唯一主城定义: {region_id}")
    return deepcopy(matches[0])


def new_manifest(*, region_id: str, bounds: dict[str, Any], source: dict[str, Any],
                 terrain: dict[str, Any], catalog: Mapping[str, Any]) -> dict[str, Any]:
    """供两个预览生成器建立同形 manifest；空列表明确是待作者填充的预览数据。"""
    definition = capital_for_region(catalog, region_id)
    return read_manifest({
        "schema": SCHEMA_VERSION, "city_id": definition["city_id"], "region_id": region_id,
        "stage": STAGE, "units": UNITS, "coordinate_system": COORDINATE_SYSTEM,
        "bounds": bounds, "source": source, "terrain": terrain,
        "districts": [], "roads": [], "buildings": [], "entries": [], "services": [],
        "layout_id": definition["layout_id"], "appearance_id": definition["appearance_id"],
        "bounds_convention": "closed_geometric_envelope",
    }, catalog=catalog)


def resolve_data_path(manifest_path: str | Path, reference: str, *, base_directory: str | Path | None = None,
                      allowed_root: str | Path | None = None) -> Path:
    """只解析生成器所属相对路径，不读取文件；拒绝 URL、绝对路径及越出允许根的引用。

    契约尚未指定路径基准。默认相对 manifest 目录；repo-relative 引用应显式传 base_directory。
    """
    reference = _text(reference, "terrain.data path")
    relative = Path(reference)
    if relative.is_absolute() or ":" in reference or "\\" in reference:
        raise ManifestError("数据引用必须为本地 POSIX 相对路径")
    base = Path(base_directory).resolve() if base_directory is not None else Path(manifest_path).resolve().parent
    root = Path(allowed_root).resolve() if allowed_root is not None else base
    resolved = (base / relative).resolve()
    if not resolved.is_relative_to(root):
        raise ManifestError("数据引用越出允许的作者目录")
    return resolved
