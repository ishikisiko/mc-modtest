"""Export the approved bag art and collect materials from their existing catalogues."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from tools.language_assets import language_text, load_language

ROOT = Path(__file__).resolve().parents[1]
DATA = Path("src/main/resources/data/yuanxian")
ASSETS = Path("src/main/resources/assets/yuanxian")
KINDS = {"material": "材料袋", "general": "通用储物袋", "manual": "秘籍袋"}
EXTRA = "#yuanxian:storage_bag_materials/extra"


def read(root: Path, path: Path | str):
    return json.loads((root / path).read_text(encoding="utf-8"))


def encoded(value) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode()


def material_values(root: Path) -> list[str | dict]:
    beasts = read(root, "tools/beast_materials/catalogue.json")
    forging = read(root, "tools/forging/materials.json")
    legacy = read(root, "tools/legacy_encounters/catalogue.json")
    wilderness = read(root, "tools/wilderness_roster/catalogue.json")
    alchemy = read(root, DATA / "alchemy/rules.json")
    equipment = read(root, DATA / "immortal_equipment/catalogue.json")
    values = {
        *(f"#yuanxian:{entry['id']}" for entry in beasts["materials"]),
        *(f"#{entry['tag']}" for entry in forging["materials"]),
        *(f"yuanxian:{mob['material'][0]}" for mob in legacy["mobs"]),
        *(f"yuanxian:{mob['material_id']}" for mob in wilderness["mobs"]),
        *(item for recipe in alchemy["recipes"] for item in recipe["ingredients"] if item.startswith("yuanxian:")),
        *(entry["ingot"]["id"] for entry in equipment["sets"]),
        "#c:ores/xuantie",
        "#yuanxian:deposits/edge_tempering_sand",
        "yuanxian:raw_xuantie",
        "yuanxian:blood_spirit_ingot",
    }
    return [*sorted(values), {"id": EXTRA, "required": False}]


def outputs(root: Path = ROOT) -> dict[Path, bytes]:
    generated = {}
    names = root / ASSETS / "lang/zh_cn.json"
    original = load_language(names.read_text(encoding="utf-8"))
    lang = dict(original)
    for tier in read(root, DATA / "storage_bags/tiers.json")["tiers"]:
        realm = tier["realm"].split(":")[1]
        tex = f"storage_bag_{realm}"
        generated[root / ASSETS / f"textures/item/{tex}.png"] = (root / f"art/storage-bags/{realm}.png").read_bytes()
        for kind, label in KINDS.items():
            item = f"storage_bag_{kind}_{realm}"
            lang[f"item.yuanxian.{item}"] = f"{tier['name']}·{label}"
            generated[root / ASSETS / f"models/item/{item}.json"] = encoded({
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"yuanxian:item/{tex}"},
            })
    lang.update({
        "screen.yuanxian.storage_bag.forge": "炼制储物袋",
        **{f"screen.yuanxian.storage_bag.kind.{key}": name for key, name in KINDS.items()},
        "screen.yuanxian.storage_bag.page": "%s / %s",
        "screen.yuanxian.storage_bag.auto_on": "拾取已开启",
        "screen.yuanxian.storage_bag.auto_off": "拾取已暂停",
        "screen.yuanxian.storage_bag.forge_start": "开炉",
        "screen.yuanxian.storage_bag.original": "原袋",
        "screen.yuanxian.storage_bag.result": "成品",
        "screen.yuanxian.storage_bag.requirement": "需要%s境界",
        "screen.yuanxian.storage_bag.recipe_result": "%s，共 %s 格",
        "screen.yuanxian.storage_bag.status.0": "材料从背包和灵石袋扣取",
        "screen.yuanxian.storage_bag.status.1": "炼制完成，请取出成品",
        "screen.yuanxian.storage_bag.status.2": "修为还没达到配方要求",
        "screen.yuanxian.storage_bag.status.3": "背包或灵石袋的材料不足",
        "screen.yuanxian.storage_bag.status.4": "请在空闲的普通炼器鼎开炉",
        "screen.yuanxian.storage_bag.status.5": "请在完整且空闲的玄枢炼器鼎开炉",
        "screen.yuanxian.storage_bag.status.6": "请放入可扩容的储物袋",
        "screen.yuanxian.storage_bag.status.7": "请先取出成品",
        "screen.yuanxian.storage_bag.status.8": "当前修炼系统尚未开放该境界",
        "screen.yuanxian.storage_bag.max": "这只袋子已到最高品阶",
        "screen.yuanxian.storage_bag.ready_to_study": "已满足功法研读要求",
        "tooltip.yuanxian.storage_bag.capacity": "容量 %s / %s 格，右键打开",
        "tooltip.yuanxian.storage_bag.unload": "手持袋子潜行右键箱子，可转入箱里已有的同种材料",
        "tooltip.yuanxian.storage_bag.manual": "存放功法秘籍，取出后手持研读",
        "message.yuanxian.storage_bag.unloaded": "已将 %s 件材料放入箱子",
        "message.yuanxian.storage_bag.unload_empty": "箱子里没有能放入的同种材料",
        "tag.item.yuanxian.storage_bag_materials": "储物袋材料",
    })
    generated[names] = language_text(original, lang).encode()
    generated[root / DATA / "tags/item/storage_bag_materials.json"] = encoded({
        "replace": False,
        "values": material_values(root),
    })
    return generated


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    stale = []
    for path, payload in outputs().items():
        if path.is_file() and path.read_bytes() == payload:
            continue
        if args.check:
            stale.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(payload)
    if stale:
        print("储物袋生成输出需要更新\n" + "\n".join(stale))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
