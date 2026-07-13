import json
import shutil
import tempfile
import unittest
from pathlib import Path

from tools.validate_spirit_stone_resources import SpiritStoneResourcesValidator


ROOT = Path(__file__).resolve().parents[2]


class SpiritStoneResourcesValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.fixture_root = Path(self.temp.name)
        self.build_complete_fixture()

    def build_complete_fixture(self) -> None:
        copies = (
            "src/main/java/com/example/myvillage/item/ModItems.java",
            "src/main/java/com/example/myvillage/block/ModBlocks.java",
            "src/main/resources/assets/myvillage",
            "src/main/resources/data/myvillage",
            "src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json",
            "src/main/resources/data/minecraft/tags/block/needs_iron_tool.json",
        )
        for relative in copies:
            source = ROOT / relative
            target = self.fixture_root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            if source.is_dir():
                shutil.copytree(source, target)
            else:
                shutil.copy2(source, target)

    def load_json(self, relative: str) -> dict:
        return json.loads((self.fixture_root / relative).read_text(encoding="utf-8"))

    def write_json(self, relative: str, value: dict) -> None:
        (self.fixture_root / relative).write_text(
            json.dumps(value, indent=2) + "\n",
            encoding="utf-8",
        )

    def validate(self):
        return SpiritStoneResourcesValidator(self.fixture_root).validate()

    def assert_error_contains(self, result, expected: str) -> None:
        self.assertTrue(
            any(expected in error for error in result.errors),
            f"expected error containing {expected!r}, got {result.errors!r}",
        )

    def test_complete_shipped_fixture_passes_without_a_jar(self) -> None:
        result = self.validate()

        self.assertEqual((), result.errors)
        self.assertIn("skipped_unresolved", result.jar_status)

    def test_missing_fortune_ore_drops_is_rejected(self) -> None:
        relative = (
            "src/main/resources/data/myvillage/loot_table/blocks/"
            "spirit_stone_ore.json"
        )
        loot = self.load_json(relative)
        ordinary = loot["pools"][0]["entries"][0]["children"][1]
        ordinary["functions"] = [
            function
            for function in ordinary["functions"]
            if function.get("function") != "minecraft:apply_bonus"
        ]
        self.write_json(relative, loot)

        result = self.validate()

        self.assert_error_contains(result, "missing Fortune minecraft:ore_drops")

    def test_missing_iron_tool_tag_entry_is_rejected(self) -> None:
        relative = "src/main/resources/data/minecraft/tags/block/needs_iron_tool.json"
        tag = self.load_json(relative)
        tag["values"].remove("myvillage:deepslate_spirit_stone_ore")
        self.write_json(relative, tag)

        result = self.validate()

        self.assert_error_contains(
            result,
            "tool tag omits myvillage:deepslate_spirit_stone_ore",
        )

    def test_placed_feature_count_drift_is_rejected(self) -> None:
        relative = (
            "src/main/resources/data/myvillage/worldgen/placed_feature/"
            "spirit_stone_ore_upper.json"
        )
        placed = self.load_json(relative)
        placed["placement"][0]["count"] = 29
        self.write_json(relative, placed)

        result = self.validate()

        self.assert_error_contains(result, "count must be 30, got 29")

    def test_configured_feature_target_drift_is_rejected(self) -> None:
        relative = (
            "src/main/resources/data/myvillage/worldgen/configured_feature/"
            "spirit_stone_ore_small.json"
        )
        configured = self.load_json(relative)
        configured["config"]["targets"][1]["target"]["tag"] = (
            "minecraft:stone_ore_replaceables"
        )
        self.write_json(relative, configured)

        result = self.validate()

        self.assert_error_contains(result, "configured target mapping must be")


if __name__ == "__main__":
    unittest.main()
