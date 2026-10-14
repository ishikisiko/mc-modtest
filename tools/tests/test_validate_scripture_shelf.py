from __future__ import annotations

import copy
import json
from pathlib import Path
import shutil
import tempfile
import unittest

from tools import validate_scripture_shelf as validator


class ScriptureShelfResourceContractTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        assets = validator.ASSETS
        resources = [assets / "blockstates/scripture_shelf.json",
                     assets / "models/item/scripture_shelf.json", validator.LOOT]
        resources.extend(assets / f"models/block/scripture_shelf{suffix}.json"
                         for suffix in ("", "_lower", "_upper"))
        resources.extend(path.relative_to(validator.ROOT)
                         for path in (validator.ROOT / assets / "textures/block").glob("scripture_shelf*.png"))
        for relative in resources:
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(validator.ROOT / relative, target)

    def mutate(self, relative, change):
        path = self.root / relative
        data = json.loads(path.read_text())
        change(data)
        path.write_text(json.dumps(data))

    def assert_defect(self, code):
        errors = validator.validate(self.root)
        self.assertTrue(any(error.startswith(code + ":") for error in errors), errors)

    def test_shipped_resources_pass(self):
        self.assertEqual(validator.validate(self.root), [])

    def test_wrong_direction_and_missing_upper_variant_fail(self):
        path = validator.ASSETS / "blockstates/scripture_shelf.json"
        self.mutate(path, lambda obj: obj["variants"]["facing=east,half=upper"].update(y=270))
        self.assert_defect("SCRIPTURE_BLOCKSTATES")
        self.mutate(path, lambda obj: obj["variants"].pop("facing=west,half=upper"))
        self.assert_defect("SCRIPTURE_BLOCKSTATES")

    def test_item_cannot_show_only_lower_half(self):
        self.mutate(validator.ASSETS / "models/item/scripture_shelf.json",
                    lambda obj: obj.update(parent=validator.MODEL + "_lower"))
        self.assert_defect("SCRIPTURE_ITEM_MODEL")

    def test_upper_geometry_must_be_local_to_upper_cell(self):
        self.mutate(validator.ASSETS / "models/block/scripture_shelf_upper.json",
                    lambda obj: obj["elements"][0]["to"].__setitem__(1, 27.52))
        self.assert_defect("SCRIPTURE_GEOMETRY")

    def test_full_model_cannot_be_a_cube_parent(self):
        self.mutate(validator.ASSETS / "models/block/scripture_shelf.json",
                    lambda obj: obj.update(parent="minecraft:block/bookshelf"))
        self.assert_defect("SCRIPTURE_MODEL")

    def test_illegal_element_rotation_is_rejected(self):
        self.mutate(validator.ASSETS / "models/block/scripture_shelf_upper.json",
                    lambda obj: next(el for el in obj["elements"] if "rotation" in el)["rotation"].update(angle=30))
        self.assert_defect("SCRIPTURE_GEOMETRY")

    def test_vanilla_bookshelf_texture_is_rejected(self):
        self.mutate(validator.ASSETS / "models/block/scripture_shelf_lower.json",
                    lambda obj: obj["textures"].update(atlas="minecraft:block/bookshelf"))
        self.assert_defect("SCRIPTURE_TEXTURE")

    def test_missing_atlas_is_rejected(self):
        (self.root / validator.ASSETS / "textures/block/scripture_shelf.png").unlink()
        self.assert_defect("SCRIPTURE_TEXTURE")

    def test_unresolved_face_texture_is_rejected(self):
        self.mutate(validator.ASSETS / "models/block/scripture_shelf_lower.json",
                    lambda obj: next(iter(obj["elements"][0]["faces"].values())).update(texture="#missing"))
        self.assert_defect("SCRIPTURE_TEXTURE")

    def test_missing_lower_condition_would_duplicate_drops(self):
        self.mutate(validator.LOOT, lambda obj: obj["pools"][0].update(conditions=[
            condition for condition in obj["pools"][0]["conditions"]
            if condition["condition"] != "minecraft:block_state_property"]))
        self.assert_defect("SCRIPTURE_LOOT")

    def test_upper_condition_cannot_transfer_root_drop_to_non_owner(self):
        self.mutate(validator.LOOT, lambda obj: next(condition for condition in obj["pools"][0]["conditions"]
                    if condition["condition"] == "minecraft:block_state_property")["properties"].update(half="upper"))
        self.assert_defect("SCRIPTURE_LOOT")

    def test_second_pool_cannot_duplicate_items(self):
        self.mutate(validator.LOOT, lambda obj: obj["pools"].append(copy.deepcopy(obj["pools"][0])))
        self.assert_defect("SCRIPTURE_LOOT")

    def test_item_count_function_cannot_duplicate_items(self):
        self.mutate(validator.LOOT, lambda obj: obj["pools"][0]["entries"][0].update(functions=[
            {"function": "minecraft:set_count", "count": 2}]))
        self.assert_defect("SCRIPTURE_LOOT")

    def test_bonus_rolls_are_rejected(self):
        self.mutate(validator.LOOT, lambda obj: obj["pools"][0].update(bonus_rolls=1))
        self.assert_defect("SCRIPTURE_LOOT")

    def test_duplicate_json_key_is_reported(self):
        (self.root / validator.ASSETS / "models/item/scripture_shelf.json").write_text(
            '{"parent": "myvillage:block/scripture_shelf", "parent": "minecraft:block/bookshelf"}')
        self.assert_defect("SCRIPTURE_RESOURCE")


if __name__ == "__main__":
    unittest.main()
