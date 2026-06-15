#!/usr/bin/env python3
"""Extract a deterministic external-mod block catalog from staged assets."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import re
import sys
import zipfile
from collections import defaultdict
from pathlib import Path
from typing import Any, Dict, Iterable, Optional

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_ASSET_ZIP = REPO_ROOT / "exmod" / "mod_assets.zip"
DEFAULT_JAR_ZIP = REPO_ROOT / "exmod" / "mod_jars.zip"
DEFAULT_REPORT = REPO_ROOT / "exmod" / "deep-research-report.md"
DEFAULT_OUTPUT = REPO_ROOT / "exmod" / "mod_block_catalog.json"

CONTENT_NAMESPACE_LABELS = {
    "ars_nouveau": "Ars Nouveau",
    "farmersdelight": "Farmer's Delight",
    "fetzisasiandeco": "Fetzi's Asian Decoration",
    "fetzisdisplays": "Fetzi's Displays",
    "mcwfurnitures": "Macaw's Furniture",
    "mcwwindows": "Macaw's Windows",
    "supplementaries": "Supplementaries",
}

SUPPORT_NAMESPACE_LABELS = {
    "curios": "Curios API",
    "dynamictrees": "Dynamic Trees compatibility",
    "geckolib": "GeckoLib",
    "minecraft": "Minecraft",
    "moonlight": "Moonlight Lib",
    "vslab_compat": "Vertical Slab compatibility",
}

ROLE_BLOCK_KEYWORDS = {
    "ROOF_TILE": (
        "awning",
        "eave",
        "pagoda",
        "roof",
        "tile",
    ),
    "PAPER_LANTERN": (
        "candle",
        "lamp",
        "lantern",
        "sconce",
    ),
    "RITUAL_ANCHOR": (
        "altar",
        "arcane",
        "brazier",
        "crystal",
        "glyph",
        "imbuement",
        "magebloom",
        "pedestal",
        "ritual",
        "source",
        "sourcelink",
    ),
    "MARKET_FITTINGS": (
        "barrel",
        "basket",
        "bench",
        "blind",
        "cabinet",
        "chair",
        "counter",
        "crate",
        "curtain",
        "cutting_board",
        "desk",
        "drawer",
        "feast",
        "holder",
        "jar",
        "planter",
        "rack",
        "rope",
        "shelf",
        "shutter",
        "sign",
        "skillet",
        "stove",
        "table",
        "wardrobe",
        "window",
    ),
}

REPORT_ROLE_TERMS = {
    "ROOF_TILE": (
        "pagoda",
        "roof",
        "东亚屋顶",
        "屋顶",
        "曲檐",
        "瓦",
        "飞檐",
    ),
    "PAPER_LANTERN": (
        "lantern",
        "paper lantern",
        "灯笼",
        "纸灯笼",
    ),
    "RITUAL_ANCHOR": (
        "brazier",
        "magebloom",
        "ritual",
        "source",
        "修仙",
        "晶体",
        "法坛",
        "法阵",
        "灵植",
        "祭台",
        "祭坛",
    ),
    "MARKET_FITTINGS": (
        "basket",
        "cabinet",
        "chair",
        "furniture",
        "jar",
        "kitchen",
        "sign",
        "table",
        "市井",
        "家具",
        "小杂物",
        "招牌",
        "桌",
        "椅",
        "罐",
        "食摊",
    ),
}

FAMILY_PREFIXES = {
    "acacia",
    "bamboo",
    "birch",
    "black",
    "blue",
    "brown",
    "cherry",
    "crimson",
    "cyan",
    "dark",
    "gray",
    "green",
    "jungle",
    "light",
    "lime",
    "mangrove",
    "oak",
    "orange",
    "pink",
    "purple",
    "red",
    "spruce",
    "stripped",
    "warped",
    "white",
    "yellow",
}

ASSET_JSON_RE = re.compile(r"^assets/([^/]+)/(blockstates|models)/(.+)\.json$")
BLOCKSTATE_RE = re.compile(r"^assets/([^/]+)/blockstates/(.+)\.json$")
CITATION_RE = re.compile(r"cite.*?")


def _rel(path: Path) -> str:
    try:
        return path.resolve().relative_to(REPO_ROOT).as_posix()
    except ValueError:
        return path.as_posix()


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _logical_asset_path(zip_name: str) -> Optional[str]:
    if zip_name.startswith("assets/"):
        return zip_name
    marker = "/assets/"
    idx = zip_name.find(marker)
    if idx == -1:
        return None
    return zip_name[idx + 1:]


def _read_asset_json_entries(asset_zip: Path, jar_zip: Path) -> Dict[str, bytes]:
    entries: Dict[str, bytes] = {}

    with zipfile.ZipFile(asset_zip) as zf:
        for name in sorted(zf.namelist()):
            logical = _logical_asset_path(name)
            if logical and logical.endswith(".json") and ASSET_JSON_RE.match(logical):
                entries[logical] = zf.read(name)

    if jar_zip.is_file():
        with zipfile.ZipFile(jar_zip) as outer:
            for jar_name in sorted(outer.namelist()):
                if not jar_name.endswith((".jar", ".zip")):
                    continue
                try:
                    with zipfile.ZipFile(io.BytesIO(outer.read(jar_name))) as inner:
                        for name in sorted(inner.namelist()):
                            logical = _logical_asset_path(name)
                            if logical and logical.endswith(".json") and ASSET_JSON_RE.match(logical):
                                entries.setdefault(logical, inner.read(name))
                except zipfile.BadZipFile:
                    continue

    return entries


def _json_load(entries: Dict[str, bytes], logical_path: str) -> dict:
    try:
        return json.loads(entries[logical_path].decode("utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError(f"invalid JSON in {logical_path}: {exc}") from exc


def _split_resource_id(value: str, default_namespace: str) -> Optional[tuple[str, str]]:
    if not value or value.startswith("#"):
        return None
    if ":" in value:
        namespace, path = value.split(":", 1)
    else:
        namespace, path = default_namespace, value
    return namespace, path


def _resource_id(value: str, default_namespace: str) -> Optional[str]:
    split = _split_resource_id(value, default_namespace)
    if split is None:
        return None
    namespace, path = split
    return f"{namespace}:{path}"


def _iter_model_refs(obj: Any) -> Iterable[str]:
    if isinstance(obj, dict):
        for key, value in obj.items():
            if key == "model" and isinstance(value, str):
                yield value
            else:
                yield from _iter_model_refs(value)
    elif isinstance(obj, list):
        for item in obj:
            yield from _iter_model_refs(item)


def _iter_texture_refs(obj: Any) -> Iterable[str]:
    if isinstance(obj, dict):
        for key, value in obj.items():
            if key == "textures" and isinstance(value, dict):
                for texture in value.values():
                    if isinstance(texture, str):
                        yield texture
            else:
                yield from _iter_texture_refs(value)
    elif isinstance(obj, list):
        for item in obj:
            yield from _iter_texture_refs(item)


def _collect_model_textures(
    entries: Dict[str, bytes],
    model_id: str,
    default_namespace: str,
    visited: set[str],
) -> set[str]:
    normalized = _resource_id(model_id, default_namespace)
    if normalized is None or normalized in visited:
        return set()
    visited.add(normalized)

    namespace, model_path = normalized.split(":", 1)
    logical_path = f"assets/{namespace}/models/{model_path}.json"
    if logical_path not in entries:
        return set()

    model = _json_load(entries, logical_path)
    textures = {
        texture
        for value in _iter_texture_refs(model)
        if (texture := _resource_id(value, namespace)) is not None
    }

    parent = model.get("parent")
    if isinstance(parent, str):
        textures.update(_collect_model_textures(entries, parent, namespace, visited))
    return textures


def _add_property_value(properties: defaultdict[str, set[str]], key: str, value: Any) -> None:
    values = value if isinstance(value, list) else str(value).split("|")
    for option in values:
        properties[key].add(str(option))


def _collect_condition_properties(
    condition: Any,
    properties: defaultdict[str, set[str]],
) -> None:
    if isinstance(condition, list):
        for item in condition:
            _collect_condition_properties(item, properties)
        return
    if not isinstance(condition, dict):
        return
    for key, value in condition.items():
        if key in {"AND", "OR"}:
            _collect_condition_properties(value, properties)
        else:
            _add_property_value(properties, key, value)


def _collect_blockstate_properties(blockstate: dict) -> dict[str, list[str]]:
    properties: defaultdict[str, set[str]] = defaultdict(set)
    variants = blockstate.get("variants", {})
    if isinstance(variants, dict):
        for variant_key in variants:
            if not variant_key:
                continue
            for part in variant_key.split(","):
                if "=" not in part:
                    continue
                key, value = part.split("=", 1)
                _add_property_value(properties, key, value)

    multipart = blockstate.get("multipart", [])
    if isinstance(multipart, list):
        for part in multipart:
            if isinstance(part, dict):
                _collect_condition_properties(part.get("when", {}), properties)

    return {key: sorted(values) for key, values in sorted(properties.items())}


def _block_roles(block_id: str) -> list[str]:
    block_path = block_id.split(":", 1)[1].lower()
    roles = [
        role
        for role, keywords in ROLE_BLOCK_KEYWORDS.items()
        if any(keyword in block_path for keyword in keywords)
    ]
    return sorted(roles)


def _family_name(block_id: str) -> str:
    name = block_id.split(":", 1)[1].rsplit("/", 1)[-1].lower()
    parts = name.split("_")
    while parts and parts[0] in FAMILY_PREFIXES:
        parts.pop(0)
    if parts and parts[0] in FAMILY_PREFIXES:
        parts.pop(0)
    return "_".join(parts) or name


def _clean_report_line(line: str) -> str:
    return CITATION_RE.sub("", line).strip()


def _report_mentions(report_path: Path) -> dict[str, list[str]]:
    mentions = {role: [] for role in sorted(REPORT_ROLE_TERMS)}
    if not report_path.is_file():
        return mentions
    text = report_path.read_text(encoding="utf-8")
    for raw_line in text.splitlines():
        line = _clean_report_line(raw_line)
        if not line:
            continue
        lower = line.lower()
        for role, terms in REPORT_ROLE_TERMS.items():
            if any(term.lower() in lower for term in terms):
                mentions[role].append(line)
    return {role: lines[:12] for role, lines in sorted(mentions.items())}


def _role_families(blocks_by_namespace: dict[str, list[dict]]) -> dict[str, list[dict]]:
    families = {role: [] for role in sorted(ROLE_BLOCK_KEYWORDS)}
    grouped: dict[str, dict[tuple[str, str], list[str]]] = {
        role: defaultdict(list) for role in ROLE_BLOCK_KEYWORDS
    }
    for namespace, blocks in blocks_by_namespace.items():
        for block in blocks:
            block_id = block["id"]
            family = _family_name(block_id)
            for role in block["design_roles"]:
                grouped[role][(namespace, family)].append(block_id)

    for role in sorted(grouped):
        for namespace, family in sorted(grouped[role]):
            families[role].append({
                "namespace": namespace,
                "family": family,
                "block_ids": sorted(grouped[role][(namespace, family)]),
            })
    return families


def _namespace_label(namespace: str) -> str:
    return (
        CONTENT_NAMESPACE_LABELS.get(namespace)
        or SUPPORT_NAMESPACE_LABELS.get(namespace)
        or namespace
    )


def build_catalog(asset_zip: Path, jar_zip: Path, report_path: Path) -> dict:
    entries = _read_asset_json_entries(asset_zip, jar_zip)
    blockstate_paths = sorted(path for path in entries if BLOCKSTATE_RE.match(path))
    if not blockstate_paths:
        raise ValueError(f"no blockstate JSON files found in {_rel(asset_zip)}")

    blocks_by_namespace: dict[str, list[dict]] = defaultdict(list)
    all_namespaces: set[str] = set()

    for logical_path in blockstate_paths:
        match = BLOCKSTATE_RE.match(logical_path)
        if match is None:
            continue
        namespace, block_path = match.groups()
        all_namespaces.add(namespace)
        blockstate = _json_load(entries, logical_path)
        model_ids = sorted({
            model
            for model_ref in _iter_model_refs(blockstate)
            if (model := _resource_id(model_ref, namespace)) is not None
        })
        textures: set[str] = set()
        for model_ref in model_ids:
            textures.update(_collect_model_textures(entries, model_ref, namespace, set()))

        block_id = f"{namespace}:{block_path}"
        blocks_by_namespace[namespace].append({
            "id": block_id,
            "blockstate": _rel(Path(logical_path)),
            "properties": _collect_blockstate_properties(blockstate),
            "models": model_ids,
            "textures": sorted(textures),
            "design_roles": _block_roles(block_id),
        })

    sorted_blocks = {
        namespace: sorted(blocks, key=lambda block: block["id"])
        for namespace, blocks in sorted(blocks_by_namespace.items())
    }
    asset_namespaces = sorted(all_namespaces)
    confirmed = sorted(ns for ns in asset_namespaces if ns in CONTENT_NAMESPACE_LABELS)
    support = sorted(ns for ns in asset_namespaces if ns not in confirmed)

    notes = []
    if "fetzisdisplays" in confirmed and "fetzisasiandeco" not in confirmed:
        notes.append(
            "Staged assets include fetzisdisplays rather than fetzisasiandeco; "
            "confirmed namespaces reflect the staged asset zip."
        )

    source_files = {
        "asset_zip": {
            "path": _rel(asset_zip),
            "sha256": _sha256(asset_zip),
        },
        "jar_zip_fallback": {
            "path": _rel(jar_zip),
            "present": jar_zip.is_file(),
        },
        "design_report": {
            "path": _rel(report_path),
            "present": report_path.is_file(),
        },
    }
    if jar_zip.is_file():
        source_files["jar_zip_fallback"]["sha256"] = _sha256(jar_zip)

    return {
        "schema_version": 1,
        "generated_by": "tools/extract_mod_catalog.py",
        "sources": source_files,
        "asset_namespaces": asset_namespaces,
        "confirmed_mod_namespaces": confirmed,
        "confirmed_mods": [
            {"namespace": namespace, "label": _namespace_label(namespace)}
            for namespace in confirmed
        ],
        "support_namespaces": support,
        "namespace_labels": {
            namespace: _namespace_label(namespace)
            for namespace in asset_namespaces
        },
        "notes": notes,
        "design_intent": {
            "role_labels": sorted(ROLE_BLOCK_KEYWORDS),
            "report_mentions": _report_mentions(report_path),
            "role_families": _role_families(sorted_blocks),
        },
        "namespaces": sorted_blocks,
    }


def write_catalog(catalog: dict, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    tmp_path = output_path.with_name(f".{output_path.name}.tmp")
    try:
        with tmp_path.open("w", encoding="utf-8") as f:
            json.dump(catalog, f, ensure_ascii=False, indent=2, sort_keys=True)
            f.write("\n")
        os.replace(tmp_path, output_path)
    finally:
        if tmp_path.exists():
            tmp_path.unlink()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--asset-zip", default=str(DEFAULT_ASSET_ZIP))
    parser.add_argument("--jar-zip", default=str(DEFAULT_JAR_ZIP))
    parser.add_argument("--report", default=str(DEFAULT_REPORT))
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    args = parser.parse_args()

    asset_zip = Path(args.asset_zip)
    jar_zip = Path(args.jar_zip)
    report_path = Path(args.report)
    output_path = Path(args.output)

    if not asset_zip.is_file():
        print(f"ERROR: required input not found: {_rel(asset_zip)}", file=sys.stderr)
        return 1

    try:
        catalog = build_catalog(asset_zip, jar_zip, report_path)
        write_catalog(catalog, output_path)
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    block_count = sum(len(blocks) for blocks in catalog["namespaces"].values())
    print(f"wrote {_rel(output_path)}")
    print(f"cataloged {block_count} blocks across {len(catalog['asset_namespaces'])} namespaces")
    print("confirmed mod namespaces: " + ", ".join(catalog["confirmed_mod_namespaces"]))
    if catalog["support_namespaces"]:
        print("support namespaces: " + ", ".join(catalog["support_namespaces"]))
    for note in catalog["notes"]:
        print(f"note: {note}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
