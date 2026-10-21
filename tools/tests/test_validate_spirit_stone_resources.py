import copy
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from tools.validate_spirit_stone_resources import ALCHEMY_STONE_CONSUMERS, SpiritStoneResourcesValidator


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
            "src/main/resources/data/c/tags",
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

    def test_higher_grade_cannot_be_added_to_low_grade_payment_tag(self):
        path = 'src/main/resources/data/c/tags/item/spirit_stones/low_grade.json'
        tag = self.load_json(path)
        tag['values'].append('myvillage:high_grade_spirit_stone')
        self.write_json(path, tag)
        self.assert_error_contains(self.validate(), 'higher grades must never enter')

    def test_grade_identity_and_nonintegral_ratio_are_rejected(self):
        path = 'src/main/resources/data/myvillage/spirit_stones/denominations.json'
        data = self.load_json(path)
        data['tiers'][2]['units'] = '10001'
        data['tiers'][1]['item'] = 'myvillage:low_grade_spirit_stone'
        self.write_json(path, data)
        result = self.validate()
        self.assert_error_contains(result, 'denomination identity/tag mismatch')
        self.assert_error_contains(result, 'increasing integral multiples')

    def processing_errors(self):
        validator = SpiritStoneResourcesValidator(self.fixture_root)
        validator.validate_no_processing_chain()
        return validator.errors

    def assert_processing_forbidden(self):
        errors = self.processing_errors()
        self.assertTrue(any("spirit-stone recipe or smelting chain is forbidden" in e for e in errors), errors)

    def test_nine_approved_alchemy_recipes_consume_existing_stone_in_either_input_order(self):
        self.assertEqual(9, len(ALCHEMY_STONE_CONSUMERS))
        for item in ALCHEMY_STONE_CONSUMERS:
            relative = f"src/main/resources/data/myvillage/recipe/{item}.json"
            recipe = self.load_json(relative)
            self.assertEqual(f"myvillage:{item}", recipe["result"]["id"])
            self.assertEqual(1, sum(i == {"tag": "c:spirit_stones/low_grade"}
                                    for i in recipe["ingredients"]))
            recipe["ingredients"].reverse()
            self.write_json(relative, recipe)
        self.assertEqual([], self.processing_errors())

    def test_approved_name_cannot_create_stones_or_new_grades(self):
        relative = "src/main/resources/data/myvillage/recipe/formula_huiling.json"
        original = self.load_json(relative)
        for output in ("low_grade_spirit_stone", "spirit_stone_ore", "medium_grade_spirit_stone"):
            with self.subTest(output=output):
                recipe = copy.deepcopy(original)
                recipe["result"]["id"] = f"myvillage:{output}"
                self.write_json(relative, recipe)
                self.assert_processing_forbidden()

    def test_approved_name_cannot_hide_smelting_or_altered_schema(self):
        relative = "src/main/resources/data/myvillage/recipe/spirit_leaf.json"
        original = self.load_json(relative)
        mutations = (
            lambda d: d.update(type="minecraft:smelting"),
            lambda d: d["result"].update(count=2),
            lambda d: d["result"].update(count=True),
            lambda d: d["ingredients"].append({"item": "myvillage:low_grade_spirit_stone"}),
            lambda d: d["ingredients"][0].update(item="myvillage:spirit_stone_ore"),
            lambda d: d["ingredients"][0].update(item="minecraft:diamond"),
            lambda d: d["ingredients"][0].update(tag="minecraft:flowers"),
            lambda d: d.update(conditions=[]),
        )
        for mutation in mutations:
            recipe = copy.deepcopy(original)
            mutation(recipe)
            self.write_json(relative, recipe)
            self.assert_processing_forbidden()

    def test_unapproved_path_does_not_gain_consumption_permission(self):
        source = self.load_json("src/main/resources/data/myvillage/recipe/spirit_leaf.json")
        for suffix in ("recipe/unapproved.json", "recipe/nested/spirit_leaf.json", "recipes/spirit_leaf.json"):
            path = self.fixture_root / "src/main/resources/data/myvillage" / suffix
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(source))
            self.assert_processing_forbidden()
            path.unlink()

    def test_ore_smelting_and_escaped_stone_creation_remain_forbidden(self):
        relative = "src/main/resources/data/myvillage/recipe/processing.json"
        recipe = {"type": "minecraft:smelting", "ingredient": {"item": "myvillage:spirit_stone_ore"},
                  "result": {"id": "myvillage:low_grade_spirit_stone"}, "experience": 0.7, "cookingtime": 200}
        self.write_json(relative, recipe)
        self.assert_processing_forbidden()
        recipe = {"type": "minecraft:crafting_shapeless", "ingredients": [{"item": "minecraft:dirt"}],
                  "result": {"id": "myvillage:low_grade_spirit_stone", "count": 1}}
        raw = json.dumps(recipe).replace("low_grade_spirit_stone", "\\u006cow_grade_spirit_stone")
        (self.fixture_root / relative).write_text(raw)
        self.assertNotIn("low_grade_spirit_stone", raw)
        self.assert_processing_forbidden()

    def test_missing_common_ore_membership_is_rejected(self):
        relative = "src/main/resources/data/c/tags/item/ores.json"
        tag = self.load_json(relative)
        tag["values"].remove("#c:ores/spirit_stone")
        self.write_json(relative, tag)
        self.assert_error_contains(self.validate(), "common tag must append required spirit-stone members")

    def test_generic_classification_cannot_pay_low_grade_recipe_cost(self):
        relative = "src/main/resources/data/myvillage/recipe/spirit_leaf.json"
        recipe = self.load_json(relative)
        for ingredient in recipe["ingredients"]:
            if "tag" in ingredient:
                ingredient["tag"] = "c:gems/spirit_stone"
        self.write_json(relative, recipe)
        # Still detect the broader spirit-stone reference and reject the changed cost.
        self.assert_processing_forbidden()

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
