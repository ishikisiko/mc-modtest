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
                "src/main/java/com/example/myvillage/item/CombatWeaponItem.java",
                "src/main/resources/data/myvillage/combat/weapon",
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
                COMBAT_WEAPON_ITEM=self.root / "src/main/java/com/example/myvillage/item/CombatWeaponItem.java",
                COMBAT_WEAPON_DATA=self.root / "src/main/resources/data/myvillage/combat/weapon",
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

    def test_spear_attribute_drift_is_named(self) -> None:
        self.replace_in_registration(
            "LINGXIAO_SPEAR",
            "SwordItem.createAttributes(Tiers.DIAMOND, 4, -2.8F)",
            "SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)")
        self.assertIn(
            "lingxiao_spear_registration_drift:SwordItem.createAttributes(Tiers.DIAMOND, 4, -2.8F)",
            self.errors())

    def test_spear_tier_drift_is_named(self) -> None:
        self.replace_in_registration(
            "LINGXIAO_SPEAR", "new CombatWeaponItem(\n                            Tiers.DIAMOND",
            "new CombatWeaponItem(\n                            Tiers.NETHERITE")
        self.assertIn(
            "lingxiao_spear_registration_drift:SwordItem.createAttributes(Tiers.DIAMOND, 4, -2.8F)",
            self.errors())

    def test_combat_weapon_as_plain_sword_item_is_named(self) -> None:
        self.replace_in_registration("QINGFENG_SWORD", "new CombatWeaponItem(", "new SwordItem(")
        errors = self.errors()
        self.assertIn("combat_weapon_not_CombatWeaponItem:qingfeng_sword", errors)
        self.assertIn("qingfeng_registration_drift:new CombatWeaponItem", errors)

    def test_plain_sword_as_combat_weapon_item_is_named(self) -> None:
        self.replace_in_registration("XUANYUE_ZHENSHAN_SWORD", "new SwordItem(", "new CombatWeaponItem(")
        self.assertIn("xuanyue_zhenshan_registration_drift:new SwordItem", self.errors())

    def test_weapon_entry_for_a_plain_sword_item_is_named(self) -> None:
        weapon = self.root / "src/main/resources/data/myvillage/combat/weapon/xuanyue_zhenshan_sword.json"
        weapon.write_text(json.dumps({"schema": 1, "item": "myvillage:xuanyue_zhenshan_sword",
                                      "style": "myvillage:basic_sword"}), encoding="utf-8")
        errors = self.errors()
        self.assertIn("combat_weapon_not_CombatWeaponItem:xuanyue_zhenshan_sword", errors)
        self.assertIn("xuanyue_zhenshan_registration_drift:new CombatWeaponItem", errors)

    def test_combat_weapon_item_without_its_reequip_rule_is_named(self) -> None:
        path = self.root / "src/main/java/com/example/myvillage/item/CombatWeaponItem.java"
        source = path.read_text(encoding="utf-8")
        path.write_text(source.replace("DataComponents.DAMAGE", "DataComponents.CUSTOM_NAME"), encoding="utf-8")
        self.assertIn("combat_weapon_item_contract:reequip_rule", self.errors())
        path.write_text(source.replace("shouldCauseReequipAnimation(", "reequipAnimation("), encoding="utf-8")
        self.assertIn("combat_weapon_item_contract:reequip_rule", self.errors())
        path.write_text(source.replace("return !before.equals(after);", "return !after.equals(before);"),
                        encoding="utf-8")
        self.assertNotIn("combat_weapon_item_contract:reequip_rule", self.errors(), "a refactor of the body passes")
        path.write_text(source.replace("if (!sameItemAndCount) {", "if (slotChanged || !sameItemAndCount) {"),
                        encoding="utf-8")
        self.assertIn("combat_weapon_item_contract:reequip_rule", self.errors())
        path.write_text(source, encoding="utf-8")
        self.assertNotIn("combat_weapon_item_contract:reequip_rule", self.errors())
        path.unlink()
        self.assertIn("combat_weapon_item_missing:CombatWeaponItem.java", self.errors())

    def test_spear_after_swords_in_creative_tab(self) -> None:
        path = self.root / "src/main/java/com/example/myvillage/item/ModItems.java"
        content = path.read_text(encoding="utf-8")
        spear = "                        output.accept(LINGXIAO_SPEAR.get());\n"
        self.assertIn(spear, content)
        content = content.replace(spear, "", 1).replace(
            "                        output.accept(QINGFENG_SWORD.get());\n",
            spear + "                        output.accept(QINGFENG_SWORD.get());\n", 1)
        path.write_text(content, encoding="utf-8")
        self.assertIn(
            "sword_creative_order:rideable->qingfeng->xuanyue->chilian->qingxiao->lingxiao->spirit_stone",
            self.errors())

    def test_missing_spear_tag_entry_and_names_are_named(self) -> None:
        tag_path = self.root / "src/main/resources/data/minecraft/tags/item/swords.json"
        tag = json.loads(tag_path.read_text(encoding="utf-8"))
        tag["values"].remove("myvillage:lingxiao_spear")
        tag_path.write_text(json.dumps(tag, indent=2) + "\n", encoding="utf-8")
        for name in ("en_us", "zh_cn"):
            path = self.root / f"src/main/resources/assets/myvillage/lang/{name}.json"
            language = json.loads(path.read_text(encoding="utf-8"))
            del language["item.myvillage.lingxiao_spear"]
            path.write_text(json.dumps(language, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        errors = self.errors()
        self.assertIn("lingxiao_spear_sword_tag_contract", errors)
        self.assertIn("lingxiao_spear_en_us_name", errors)
        self.assertIn("lingxiao_spear_zh_cn_name", errors)
        self.assertIn("missing_lang:item.myvillage.lingxiao_spear|block.myvillage.lingxiao_spear", errors)

    def test_spear_flat_handheld_model_is_named(self) -> None:
        path = self.root / "src/main/resources/assets/myvillage/models/item/lingxiao_spear.json"
        path.write_text(json.dumps({"parent": "minecraft:item/handheld",
                                    "textures": {"layer0": "myvillage:item/lingxiao_spear"}}) + "\n",
                        encoding="utf-8")
        self.assertIn("lingxiao_spear_model_contract", self.errors())

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

    def qingfeng_model(self, name: str = "qingfeng_sword") -> tuple[Path, dict]:
        path = self.root / f"src/main/resources/assets/myvillage/models/item/{name}.json"
        return path, json.loads(path.read_text(encoding="utf-8"))

    def test_qingfeng_is_a_separate_transforms_3d_model(self) -> None:
        _, model = self.qingfeng_model()
        self.assertEqual("neoforge:separate_transforms", model["loader"])
        self.assertEqual({"parent": "myvillage:item/qingfeng_sword_3d"}, model["base"])
        self.assertEqual("myvillage:item/qingfeng_sword", model["perspectives"]["gui"]["textures"]["layer0"])

    def test_qingfeng_reverted_to_flat_handheld_is_named(self) -> None:
        path, _ = self.qingfeng_model()
        path.write_text(json.dumps({"parent": "minecraft:item/handheld",
                                    "textures": {"layer0": "myvillage:item/qingfeng_sword"}}) + "\n",
                        encoding="utf-8")
        self.assertIn("qingfeng_model_contract", self.errors())

    def test_qingfeng_gui_without_2d_icon_is_named(self) -> None:
        path, model = self.qingfeng_model()
        del model["perspectives"]["gui"]
        path.write_text(json.dumps(model) + "\n", encoding="utf-8")
        self.assertIn("qingfeng_model_contract", self.errors())

    def test_qingfeng_3d_model_on_generated_parent_is_named(self) -> None:
        path, model = self.qingfeng_model("qingfeng_sword_3d")
        model["parent"] = "minecraft:item/handheld"
        path.write_text(json.dumps(model) + "\n", encoding="utf-8")
        self.assertIn("qingfeng_model_3d_contract:generated_parent_ignores_elements", self.errors())

    def test_qingfeng_3d_invalid_rotation_and_bounds_are_named(self) -> None:
        path, model = self.qingfeng_model("qingfeng_sword_3d")
        model["elements"][0]["rotation"] = {"angle": 30, "axis": "x", "origin": [8, 8, 8]}
        model["elements"][1]["to"][1] = 40
        path.write_text(json.dumps(model) + "\n", encoding="utf-8")
        errors = self.errors()
        self.assertIn("qingfeng_model_3d_contract:element_0:rotation", errors)
        self.assertIn("qingfeng_model_3d_contract:element_1:outside_-16_32", errors)

    def test_qingfeng_3d_missing_model_and_texture_are_named(self) -> None:
        path, model = self.qingfeng_model("qingfeng_sword_3d")
        model["textures"]["sword"] = "myvillage:item/iron_blade"
        path.write_text(json.dumps(model) + "\n", encoding="utf-8")
        errors = self.errors()
        self.assertIn("qingfeng_model_3d_contract:texture_sword", errors)
        self.assertIn("missing_texture:src/main/resources/assets/myvillage/textures/item/iron_blade.png", errors)
        path.unlink()
        self.assertIn("qingfeng_model_3d_missing", self.errors())

    def test_chilian_semitransparent_pixels_are_named(self) -> None:
        self.write_rgba_texture("chilian_lihuo_sword", [0, 1, 254, 255])
        self.assertIn("chilian_lihuo_texture_non_binary_alpha:2", self.errors())


if __name__ == "__main__":
    unittest.main()
