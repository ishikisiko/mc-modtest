import json
import tempfile
import unittest
from pathlib import Path

from tools.gen_storage_bags import ASSETS, DATA, EXTRA, ROOT, material_values, outputs


class StorageBagAssetsTest(unittest.TestCase):
    def test_new_catalogue_entries_join_the_dictionary(self):
        paths = {
            "tools/beast_materials/catalogue.json": ("materials", {"id": "added_beast_family"}),
            "tools/forging/materials.json": ("materials", {"tag": "yuanxian:forging_materials/added"}),
            "tools/legacy_encounters/catalogue.json": ("mobs", {"material": ["added_ruins", "新增遗迹材料"]}),
            "tools/wilderness_roster/catalogue.json": ("mobs", {"material_id": "added_wilderness"}),
            DATA / "alchemy/rules.json": ("recipes", {"ingredients": ["yuanxian:added_herb", "minecraft:apple"]}),
            DATA / "immortal_equipment/catalogue.json": ("sets", {"ingot": {"id": "yuanxian:added_ingot"}}),
        }
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for path, (field, addition) in paths.items():
                target = root / path
                target.parent.mkdir(parents=True, exist_ok=True)
                raw = json.loads((ROOT / path).read_text())
                raw[field].append(addition)
                target.write_text(json.dumps(raw))
            values = material_values(root)
        for item in ("#yuanxian:added_beast_family", "#yuanxian:forging_materials/added",
                     "yuanxian:added_ruins", "yuanxian:added_wilderness", "yuanxian:added_herb",
                     "yuanxian:added_ingot"):
            self.assertIn(item, values)
        self.assertIn({"id": EXTRA, "required": False}, values)
        self.assertNotIn("minecraft:apple", values)
        self.assertNotIn("yuanxian:low_grade_spirit_stone", values)

    def test_all_variants_share_the_approved_art_by_realm(self):
        files = outputs()
        models = {p: b for p, b in files.items() if "/models/" in p.as_posix()}
        self.assertEqual(len(models), 15)
        art = ROOT / "art/storage-bags"
        for png in art.glob("*.png"):
            tex = f"storage_bag_{png.stem}"
            self.assertEqual(files[ROOT / ASSETS / f"textures/item/{tex}.png"], png.read_bytes())
            self.assertEqual(3, sum(json.loads(model)["textures"]["layer0"] == f"yuanxian:item/{tex}"
                                    for model in models.values()))


if __name__ == "__main__":
    unittest.main()
