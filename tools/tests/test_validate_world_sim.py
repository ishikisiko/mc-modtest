from __future__ import annotations

import json
import shutil
import tempfile
import unittest
from pathlib import Path

from tools import validate_world_sim as validator


class WorldSimValidatorTest(unittest.TestCase):
    """Each test breaks one thing in a copy of the tree and expects the validator to name it.

    Problems already present in the working tree (another worker's text in progress) are the
    baseline; a test passes when its breakage adds the expected problem on top of it.
    """

    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        for rel in (validator.DATA_REL, validator.PLAYER_REALM_REL, validator.TECHNIQUE_REL, validator.SCHOOL_REL,
                    validator.LANG_REL, validator.SIM_REL):
            shutil.copytree(validator.ROOT / rel, self.root / rel)
        self.baseline = set(validator.validate(self.root).errors)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    # ------------------------------------------------------------------ helpers
    def data_path(self, name: str) -> Path:
        return self.root / validator.DATA_REL / name

    def edit_json(self, path: Path, change) -> None:
        doc = json.loads(path.read_text(encoding="utf-8"))
        change(doc)
        path.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    def edit_data(self, name: str, change) -> None:
        self.edit_json(self.data_path(name), change)

    def edit_lang(self, code: str, change) -> None:
        self.edit_json(self.root / validator.LANG_REL / f"{code}.json", change)

    def new_errors(self) -> set[str]:
        return set(validator.validate(self.root).errors) - self.baseline

    def assert_new(self, *fragments: str) -> None:
        errors = self.new_errors()
        for fragment in fragments:
            self.assertTrue(any(fragment in e for e in errors), f"no new error with {fragment!r} in {sorted(errors)}")

    # ------------------------------------------------------------------ the working tree
    def test_copy_matches_the_working_tree(self) -> None:
        self.assertEqual(set(validator.validate(validator.ROOT).errors), self.baseline)

    def test_working_tree_data_and_purity_are_clean(self) -> None:
        errors = [e for e in validator.validate(validator.ROOT).errors if not e.startswith("keys:")]
        self.assertEqual(errors, [])

    # ------------------------------------------------------------------ data
    def test_schema_version_is_checked_in_every_file(self) -> None:
        self.edit_data("lore.json", lambda d: d.update(schema=2))
        self.assert_new("data: lore.json: schema must be 1")
        self.data_path("extra.json").write_text('{"schema": 9}\n', encoding="utf-8")
        self.assert_new("data: extra.json: schema must be 1")

    def test_duplicate_ids_and_bad_enums(self) -> None:
        def dup(d):
            d["techniques"].append(dict(d["techniques"][0]))
            d["techniques"][1]["element"] = "lightning"
        self.edit_data("techniques.json", dup)
        self.assert_new("techniques[].id has the duplicate", "techniques[1].element must be one of")

    def test_encounter_cross_references(self) -> None:
        def bad(d):
            e = d["encounters"][0]
            e["realms"] = ["immortal"]
            e["statuses"] = ["dreaming"]
            e["weight"] = 0
            e["effects"] = [{"kind": "technique", "grade": "divine"}]
        self.edit_data("encounters.json", bad)
        self.assert_new("names the unknown realm 'immortal'", "names the unknown status 'dreaming'",
                        "encounters[0].weight must be a positive integer", "effects[0].grade must be one of")

    def test_site_kind_needs_a_lore_site(self) -> None:
        self.edit_data("lore.json", lambda d: d.update(sites=[s for s in d["sites"] if s["kind"] != "tomb"]))
        self.assert_new("site_kind tomb has no site of that kind in lore.json")

    def test_granted_grades_need_an_entry(self) -> None:
        self.edit_data("techniques.json",
                       lambda d: d.update(techniques=[t for t in d["techniques"] if t["grade"] != "xuan"]))
        self.assert_new("grants a xuan technique but techniques.json has none")

    def test_heritage_effect_and_chance(self) -> None:
        def bad_encounter(d):
            fx = next(e for e in d["encounters"] if any(f["kind"] == "heritage" for f in e["effects"]))["effects"]
            fx[0]["grade"] = "di"
        self.edit_data("encounters.json", bad_encounter)
        self.edit_data("rules.json", lambda d: d["genesis"].update(heritage_chance=1.5))
        self.edit_data("heritages.json", lambda d: d.update(heritages=[]))
        self.assert_new(".grade is not used by heritage", "genesis.heritage_chance must be a number in 0..1",
                        "a heritage effect needs at least one heritage in heritages.json")

    def test_rules_reference_realms_and_runtime_tiers(self) -> None:
        def bad(d):
            del d["tiers"]["medium"]
            d["sects"]["promotion"]["elder"]["stage"] = 9
            d["roots"]["grades"][0]["weight"] = -1
        self.edit_data("rules.json", bad)
        self.assert_new("tiers.medium is missing", "sects.promotion.elder names an unknown realm/stage",
                        "roots.grades[0].weight must be a positive integer")

    def test_realm_lifespan_agrees_with_the_player_realm(self) -> None:
        self.edit_data("realms.json", lambda d: d["realms"][1].update(lifespan_years=250))
        self.assert_new("foundation_establishment lifespan_years 250 disagrees with the player realm")

    def test_names_need_positive_suffix_weights_and_no_duplicates(self) -> None:
        def bad(d):
            d["sect_suffixes"][0]["weight"] = 0
            d["surnames"].append(d["surnames"][0])
        self.edit_data("names.json", bad)
        self.assert_new("sect_suffixes[0].weight must be a positive integer", "surnames has the duplicate")

    # ------------------------------------------------------------------ language keys
    def test_an_encounter_line_missing_in_one_language(self) -> None:
        text = json.loads(self.data_path("encounters.json").read_text(encoding="utf-8"))["encounters"][0]["text"]
        base = f"world_sim.event.fortune.{text}"
        self.edit_lang("zh_cn", lambda d: [d.pop(k) for k in list(d) if k == base or k.startswith(base + ".")])
        self.assert_new(f"keys: zh_cn.json: {base} is missing")

    def test_a_new_core_literal_key_needs_both_languages(self) -> None:
        path = self.root / validator.SIM_REL / "engine" / "Probe.java"
        path.write_text('package x;\nclass Probe { static final String K = TextKeys.P + "probe.only"; '
                        'String s = "world_sim.probe.literal"; }\n', encoding="utf-8")
        self.assert_new("en_us.json: world_sim.probe.literal is missing",
                        "zh_cn.json: world_sim.event.probe.only is missing")

    def test_slots_must_agree_between_languages(self) -> None:
        self.edit_lang("zh_cn", lambda d: d.update({"world_sim.date.era": "启元%2$s年"}))
        self.assert_new("world_sim.date.era slots [2] do not run 1..n", "world_sim.date.era slots [2] differ")

    def test_family_variants_must_match(self) -> None:
        self.edit_lang("en_us", lambda d: d.update({"world_sim.event.stage_up.9": "%1$s"}))
        self.assert_new("world_sim.event.stage_up variants", "is in the other language file only")

    def test_runtime_command_keys_are_checked(self) -> None:
        self.edit_lang("en_us", lambda d: d.pop("commands.myvillage.world.info.realms"))
        self.edit_lang("zh_cn", lambda d: d.pop("message.myvillage.world.rumor"))
        self.assert_new("en_us.json: commands.myvillage.world.info.realms is missing",
                        "zh_cn.json: message.myvillage.world.rumor is missing")

    # ------------------------------------------------------------------ purity
    def test_core_must_not_import_minecraft_but_runtime_may(self) -> None:
        core = self.root / validator.SIM_REL / "engine" / "Leak.java"
        core.write_text("package x;\nimport net.minecraft.world.level.Level;\nclass Leak {}\n", encoding="utf-8")
        fq = self.root / validator.SIM_REL / "model" / "Leak2.java"
        fq.write_text("package x;\nclass Leak2 { org.slf4j.Logger log; }\n", encoding="utf-8")
        runtime = self.root / validator.SIM_REL / "runtime" / "Fine.java"
        runtime.write_text("package x;\nimport net.minecraft.server.MinecraftServer;\nclass Fine {}\n",
                           encoding="utf-8")
        errors = self.new_errors()
        self.assertTrue(any("purity:" in e and "Leak.java" in e and "net.minecraft" in e for e in errors), errors)
        self.assertTrue(any("purity:" in e and "Leak2.java" in e and "org.slf4j" in e for e in errors), errors)
        self.assertFalse(any("Fine.java" in e for e in errors), errors)


if __name__ == "__main__":
    unittest.main()
