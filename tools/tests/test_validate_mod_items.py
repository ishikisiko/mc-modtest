from __future__ import annotations

import shutil
import tempfile
import unittest
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

    def test_repository_fixture_passes(self) -> None:
        self.assertEqual(set(), self.errors())

    def test_qingfeng_attribute_drift_is_named(self) -> None:
        path = self.root / "src/main/java/com/example/myvillage/item/ModItems.java"
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)",
                "SwordItem.createAttributes(Tiers.DIAMOND, 4, -2.4F)"),
            encoding="utf-8")
        self.assertIn(
            "qingfeng_registration_drift:SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)",
            self.errors())

    def test_missing_sword_tag_is_named(self) -> None:
        (self.root / "src/main/resources/data/minecraft/tags/item/swords.json").unlink()
        self.assertIn("qingfeng_sword_tag_missing", self.errors())


if __name__ == "__main__":
    unittest.main()
