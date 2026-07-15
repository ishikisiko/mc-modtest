from __future__ import annotations

import json
import shutil
import struct
import tempfile
import unittest
import zlib
from pathlib import Path
from unittest.mock import patch

from tools import validate_mod_items as validator


class ModItemsValidatorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        for relative in (
                "src/main/java/com/example/myvillage/item/ModItems.java",
                "src/main/java/com/example/myvillage/block/ModBlocks.java",
                "src/main/java/com/example/myvillage/block/RockeryBlock.java",
                "src/main/resources/assets/myvillage",
                "src/main/resources/data/myvillage/recipe/qingfeng_sword.json",
                "src/main/resources/data/minecraft/tags/item/swords.json"):
            source = validator.ROOT / relative
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            if source.is_dir():
                shutil.copytree(source, target, dirs_exist_ok=True)
            else:
                shutil.copy2(source, target)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def errors(self) -> set[str]:
        assets = self.root / "src/main/resources/assets/myvillage"
        with patch.multiple(
                validator,
                ROOT=self.root,
                ASSET_ROOT=assets,
                MOD_ITEMS=self.root / "src/main/java/com/example/myvillage/item/ModItems.java",
                MOD_BLOCKS=self.root / "src/main/java/com/example/myvillage/block/ModBlocks.java",
                ROCKERY_BLOCK=self.root / "src/main/java/com/example/myvillage/block/RockeryBlock.java",
                LANG=assets / "lang/en_us.json",
                ZH_LANG=assets / "lang/zh_cn.json"):
            return set(validator.validate())

    def replace_in_registration(self, holder: str, old: str, new: str) -> None:
        path = self.root / "src/main/java/com/example/myvillage/item/ModItems.java"
        content = path.read_text(encoding="utf-8")
        start = content.index(f"DeferredItem<SwordItem> {holder}")
        end = content.find("public static final", start + 1)
        if end < 0:
            end = len(content)
        registration = content[start:end]
        self.assertIn(old, registration)
        content = content[:start] + registration.replace(old, new, 1) + content[end:]
        path.write_text(content, encoding="utf-8")

    def write_rgba_texture(self, item_id: str, alphas: list[int]) -> None:
        width = height = 64
        pixels = bytearray(width * height * 4)
        for pixel, alpha in enumerate(alphas):
            pixels[pixel * 4:pixel * 4 + 4] = bytes((255, 64, 32, alpha))

        def chunk(kind: bytes, payload: bytes) -> bytes:
            checksum = zlib.crc32(kind + payload) & 0xFFFFFFFF
            return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", checksum)

        rows = b"".join(
            b"\x00" + pixels[row * width * 4:(row + 1) * width * 4]
            for row in range(height)
        )
        png = (
            b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(rows))
            + chunk(b"IEND", b"")
        )
        path = (
            self.root
            / f"src/main/resources/assets/myvillage/textures/item/{item_id}.png"
        )
        path.write_bytes(png)

    def test_repository_fixture_passes(self) -> None:
        self.assertEqual(set(), self.errors())

    def test_qingfeng_attribute_drift_is_named(self) -> None:
        self.replace_in_registration(
            "QINGFENG_SWORD",
            "SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)",
            "SwordItem.createAttributes(Tiers.DIAMOND, 4, -2.4F)")
        self.assertIn(
            "qingfeng_registration_drift:SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)",
            self.errors())

    def test_new_sword_attribute_drift_is_named(self) -> None:
        self.replace_in_registration(
            "XUANYUE_ZHENSHAN_SWORD",
            "SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)",
            "SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.2F)")
        self.assertIn(
            "xuanyue_zhenshan_registration_drift:"
            "SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)",
            self.errors())

    def test_missing_sword_tag_is_named(self) -> None:
        (self.root / "src/main/resources/data/minecraft/tags/item/swords.json").unlink()
        self.assertIn("qingfeng_sword_tag_missing", self.errors())

    def test_missing_new_sword_tag_entry_is_named(self) -> None:
        path = self.root / "src/main/resources/data/minecraft/tags/item/swords.json"
        tag = json.loads(path.read_text(encoding="utf-8"))
        tag["values"].remove("myvillage:chilian_lihuo_sword")
        path.write_text(json.dumps(tag, indent=2) + "\n", encoding="utf-8")
        self.assertIn("chilian_lihuo_sword_tag_contract", self.errors())

    def test_missing_new_sword_zh_name_is_named(self) -> None:
        path = self.root / "src/main/resources/assets/myvillage/lang/zh_cn.json"
        language = json.loads(path.read_text(encoding="utf-8"))
        del language["item.myvillage.qingxiao_liuyun_sword"]
        path.write_text(
            json.dumps(language, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8")
        self.assertIn("qingxiao_liuyun_zh_cn_name", self.errors())

    def test_new_sword_model_texture_reference_drift_is_named(self) -> None:
        path = (
            self.root
            / "src/main/resources/assets/myvillage/models/item/xuanyue_zhenshan_sword.json"
        )
        model = json.loads(path.read_text(encoding="utf-8"))
        model["textures"]["layer0"] = "myvillage:item/qingfeng_sword"
        path.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
        self.assertIn("xuanyue_zhenshan_model_contract", self.errors())

    def test_chilian_semitransparent_pixels_are_named(self) -> None:
        self.write_rgba_texture("chilian_lihuo_sword", [0, 1, 254, 255])
        self.assertIn("chilian_lihuo_texture_non_binary_alpha:2", self.errors())


if __name__ == "__main__":
    unittest.main()
