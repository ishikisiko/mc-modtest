"""女修组合的唯一配置入口；生成供服务器和客户端共同读取的目录。"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = "tools/npcgen/female_variants.json"
FACTORY = "tools/npcgen/female.py"
RUNTIME = "src/main/resources/assets/myvillage/npc/cultivator_variants.json"


def load(root=ROOT):
    data = json.loads((Path(root) / SOURCE).read_text(encoding="utf-8"))
    if data["version"] != 1 or data["legacy_looks"] != ["default", "f_novice", "f_adept"]:
        raise ValueError("不支持的女修目录版本或旧外观 ID")
    if set(data["faces"]) != {"01", "02", "03"}:
        raise ValueError("女修目录只能包含保留的三张脸")
    for face in data["faces"].values():
        if len(face["pixels"]) != 14 or any(len(row) != 14 or set(row) - set("HLWCDGPM.") for row in face["pixels"]):
            raise ValueError("脸部必须为有效的 14×14 索引画布")
    expected = {(f, h, o) for f in data["faces"] for h in data["hair"] for o in data["outfits"]}
    seen, ids = set(), set(data["legacy_looks"])
    for item in data["variants"]:
        key = (item["face"], item["hair"], item["outfit"])
        look = item["look"]
        if key not in expected or key in seen or look in ids or look != f"f_novice_{key[0]}_{key[1].lower()}_{key[2]}":
            raise ValueError(f"重复或无效的女修组合：{item}")
        seen.add(key)
        ids.add(look)
    if seen != expected or not seen:
        raise ValueError("女修组合目录不完整")
    return data


def variants(root=ROOT):
    return load(root)["variants"]


def definitions(root=ROOT):
    return tuple("cultivator_" + item["look"] for item in variants(root))


def runtime_document(root=ROOT):
    source = load(root)
    return {key: source[key] for key in ("version", "legacy_looks", "selection_salt", "variants")}


def for_identity(person_id, birth_day):
    data = runtime_document()
    def score(item):
        value = f"{data['selection_salt']}:{person_id}:{birth_day}:{item['look']}"
        result = 0xcbf29ce484222325
        for byte in value.encode():
            result = ((result ^ byte) * 0x100000001b3) & 0xffffffffffffffff
        return result
    return max(data['variants'], key=score)['look']


def write(check=False):
    path = ROOT / RUNTIME
    data = (json.dumps(runtime_document(), ensure_ascii=False, indent=2) + "\n").encode()
    if check:
        if not path.is_file() or path.read_bytes() != data:
            raise SystemExit(f"STALE {RUNTIME}")
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    vectors = [{"person_id": person_id, "birth_day": birth_day, "look": for_identity(person_id, birth_day)}
               for person_id, birth_day in ((0, 0), (1, -100), (17, -12345), (2147483647, -2147483648))]
    vector_path = ROOT / "src/test/resources/npc_variant_identity.json"
    vector_data = (json.dumps(vectors, indent=2) + "\n").encode()
    if check:
        if not vector_path.is_file() or vector_path.read_bytes() != vector_data:
            raise SystemExit(f"STALE {vector_path}")
    else:
        vector_path.write_bytes(vector_data)


def run(check=False):
    from . import build
    write(check)
    return max(build.run(name, check) for name in definitions())
