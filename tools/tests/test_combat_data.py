from __future__ import annotations

import copy
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from tools import combat_data

DATA = "src/main/resources/data/myvillage/combat"
STYLE = f"{DATA}/style/basic_sword.json"
WEAPON = f"{DATA}/weapon/qingfeng_sword.json"


class CombatDataTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        shutil.copytree(combat_data.ROOT / DATA, self.root / DATA)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def read(self, relative: str) -> dict:
        return json.loads((self.root / relative).read_text(encoding="utf-8"))

    def write(self, relative: str, document: dict) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(document, indent=2), encoding="utf-8")

    def edit_style(self, change) -> None:
        style = self.read(STYLE)
        change(style)
        self.write(STYLE, style)

    def issues(self) -> list[combat_data.Issue]:
        return list(combat_data.load(self.root).issues)

    def find(self, code: str, field: str | None = None) -> combat_data.Issue:
        issues = self.issues()
        for issue in issues:
            if issue.code == code and (field is None or issue.field == field):
                return issue
        self.fail(f"no {code} issue for {field}: {[str(i) for i in issues]}")

    def test_committed_data_is_valid(self) -> None:
        data = combat_data.load()
        self.assertEqual((), data.issues)
        self.assertEqual(["myvillage:basic_sword"], list(data.styles))
        self.assertEqual(["myvillage:qingfeng_sword"], list(data.weapons))
        self.assertEqual(5, len(list(data.moves())))
        self.assertEqual(combat_data.ROOT / STYLE, data.files["myvillage:basic_sword"])

    def test_ids_resolve_to_paths(self) -> None:
        self.assertEqual(self.root / STYLE, combat_data.style_file(self.root, "myvillage:basic_sword"))
        self.assertEqual(self.root / WEAPON, combat_data.weapon_file(self.root, "myvillage:qingfeng_sword"))
        self.assertEqual(self.root / "src/main/resources/assets/myvillage/combat/rig.json",
                         combat_data.asset_file(self.root, "myvillage:combat/rig.json"))
        self.assertEqual(("myvillage", "a/b"), combat_data.split_id("myvillage:a/b"))
        with self.assertRaises(ValueError):
            combat_data.split_id("NoNamespace")

    # --- spec scenarios -------------------------------------------------------------------

    def test_misspelled_field_names_file_and_field(self) -> None:
        def misspell(style):
            style["moves"][0]["chain_tik"] = style["moves"][0].pop("chain_tick")
        self.edit_style(misspell)
        issue = self.find("UNKNOWN_FIELD", "moves[0].chain_tik")
        self.assertEqual(STYLE, issue.file)
        self.assertIn(STYLE, str(issue))
        self.assertIn("moves[0].chain_tik", str(issue))
        self.find("SCHEMA", "moves[0].chain_tick")  # and the real field is reported missing
        self.assertNotIn("myvillage:basic_sword", combat_data.load(self.root).styles)

    def test_unlisted_style_file_is_reported(self) -> None:
        self.write(f"{DATA}/style/spear.json", self.read(STYLE))
        issue = self.find("INDEX")
        self.assertEqual(f"{DATA}/style/spear.json", issue.file)
        self.assertIn("myvillage:spear", issue.message)

    def test_listed_but_missing_file_is_reported(self) -> None:
        index = self.read(f"{DATA}/index.json")
        index["weapons"].append("myvillage:missing_blade")
        self.write(f"{DATA}/index.json", index)
        issue = self.find("INDEX", "weapons[1]")
        self.assertIn("myvillage:missing_blade", issue.message)

    def test_chain_tick_inside_active_window_is_rejected(self) -> None:
        self.edit_style(lambda style: style["moves"][1].update(chain_tick=6))
        self.assertIn("active end (6)", self.find("INVARIANT", "moves[1].chain_tick").message)

    def test_duplicate_move_id_across_styles_names_both_styles(self) -> None:
        other = self.read(STYLE)
        other["id"] = "myvillage:other_sword"
        other["moves"] = [copy.deepcopy(other["moves"][0])]
        self.write(f"{DATA}/style/other_sword.json", other)
        index = self.read(f"{DATA}/index.json")
        index["styles"].append("myvillage:other_sword")
        self.write(f"{DATA}/index.json", index)
        issue = self.find("DUPLICATE", "moves[0].id")
        self.assertEqual(f"{DATA}/style/other_sword.json", issue.file)
        self.assertIn("myvillage:basic_sword", issue.message)

    # --- invariants -----------------------------------------------------------------------

    def test_timing_invariants(self) -> None:
        cases = (
            (lambda m: m.update(active_ticks=[4, 11]), "moves[0].active_ticks"),
            (lambda m: m.update(active_ticks=[4, 3]), "moves[0].active_ticks"),
            (lambda m: m.update(buffer_start_tick=2), "moves[0].buffer_start_tick"),
            (lambda m: m.update(buffer_start_tick=11), "moves[0].buffer_start_tick"),
            (lambda m: m.update(chain_tick=12), "moves[0].chain_tick"),
            (lambda m: m["step"].update(tick=11), "moves[0].step.tick"),
        )
        original = self.read(STYLE)
        for change, field in cases:
            with self.subTest(field=field):
                style = copy.deepcopy(original)
                change(style["moves"][0])
                self.write(STYLE, style)
                self.find("INVARIANT", field)

    def test_finisher_may_chain_on_its_last_tick(self) -> None:
        # chain_tick == total_ticks is the "cannot chain" finisher and is valid.
        self.assertEqual(20, self.read(STYLE)["moves"][4]["chain_tick"])
        self.assertEqual([], self.issues())

    def test_explicit_samples_are_accepted_and_checked(self) -> None:
        sample = {"tick": 3, "start": [0, 1.2, 0.5], "end": [0, 1.2, 2.9],
                  "horizontal_radius": 0.16, "vertical_radius": 0.16}
        self.edit_style(lambda style: style["moves"][0]["hitbox"].update(samples=[sample, dict(sample, tick=4)]))
        self.assertEqual([], self.issues())
        self.edit_style(lambda style: style["moves"][0]["hitbox"]["samples"][1].update(tick=6))
        self.find("INVARIANT", "moves[0].hitbox.samples[1].tick")
        self.edit_style(lambda style: style["moves"][0]["hitbox"]["samples"][1].update(radius=1))
        self.find("UNKNOWN_FIELD", "moves[0].hitbox.samples[1].radius")

    def test_unknown_sample_generator_is_rejected(self) -> None:
        self.edit_style(lambda style: style["moves"][2]["hitbox"]["samples"].update(generator="spiral"))
        self.find("SCHEMA", "moves[2].hitbox.samples.generator")

    def test_generator_parameters_are_exact(self) -> None:
        self.edit_style(lambda style: style["moves"][1]["hitbox"]["samples"].pop("height"))
        self.find("SCHEMA", "moves[1].hitbox.samples.height")

    def test_types_are_checked(self) -> None:
        self.edit_style(lambda style: style["moves"][0].update(maximum_targets=True))
        self.find("SCHEMA", "moves[0].maximum_targets")
        self.edit_style(lambda style: style["moves"][0].update(maximum_targets=1, kind="slash"))
        self.find("SCHEMA", "moves[0].kind")

    def test_duplicate_move_id_within_a_style(self) -> None:
        self.edit_style(lambda style: style["moves"][3].update(id=style["moves"][1]["id"]))
        self.find("DUPLICATE", "moves[3].id")

    def test_style_id_must_match_the_index(self) -> None:
        self.edit_style(lambda style: style.update(id="myvillage:renamed"))
        self.find("INDEX", "id")

    def test_weapon_unknown_field_and_style_reference(self) -> None:
        weapon = self.read(WEAPON)
        weapon["styel"] = weapon.pop("style")
        self.write(WEAPON, weapon)
        self.find("UNKNOWN_FIELD", "styel")
        weapon["style"] = "myvillage:unlisted"
        del weapon["styel"]
        self.write(WEAPON, weapon)
        self.find("REFERENCE", "style")

    def test_invalid_json_is_reported(self) -> None:
        (self.root / WEAPON).write_text("{", encoding="utf-8")
        self.assertEqual(WEAPON, self.find("JSON").file)

    def test_load_strict_raises_with_every_issue(self) -> None:
        self.edit_style(lambda style: style["moves"][1].update(chain_tick=6, unknown=1))
        with self.assertRaises(combat_data.CombatDataError) as raised:
            combat_data.load_strict(self.root)
        self.assertEqual(2, len(raised.exception.issues))
        self.assertIn("moves[1].unknown", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
