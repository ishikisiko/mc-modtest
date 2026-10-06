from __future__ import annotations

import contextlib
import io
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from tools import gen_technique_catalogue as cli
from tools import validate_world_sim as validator
from tools.technique_catalogue import generator, importer

SOURCES = ("catalogue.json", "schools.json", "heritages.json", "rules.json")
BASIC = generator.TECHNIQUE_REL / "basic_breathing.json"
BASIC_KEY = "cultivation.technique.myvillage.basic_breathing"


class Fixture(unittest.TestCase):
    """A temp root holding the sources and only the inputs the generator reads, before any output exists."""

    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name) / "repo"
        self.src = Path(self.temp_dir.name) / "src"
        self.src.mkdir()
        for name in SOURCES:
            shutil.copy(generator.SOURCE_DIR / name, self.src / name)
        for rel in (generator.REALM_REL, generator.WORLD_SIM_REL, generator.LANG_REL):
            shutil.copytree(generator.ROOT / rel, self.root / rel)
        (self.root / BASIC).parent.mkdir(parents=True)
        shutil.copy(generator.ROOT / BASIC, self.root / BASIC)
        (self.root / generator.WORLD_SIM_REL / "heritages.json").unlink()
        for code in generator.LANGS:
            path = self.root / generator.LANG_REL / f"{code}.json"
            lang = json.loads(path.read_text(encoding="utf-8"))
            path.write_text(generator.dump({k: v for k, v in lang.items() if k == BASIC_KEY
                                            or not generator.managed_key(k, "myvillage")}), encoding="utf-8")

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def write(self) -> list[str]:
        return generator.write(self.root, self.src)

    def run_cli(self, *args: str) -> int:
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return cli.main([*args, "--root", str(self.root), "--source-dir", str(self.src)])

    def edit_source(self, name: str, change) -> None:
        path = self.src / name
        doc = json.loads(path.read_text(encoding="utf-8"))
        change(doc)
        path.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


class GenerationTest(Fixture):
    def test_generation_is_idempotent_and_leaves_the_hand_written_file_alone(self) -> None:
        basic = (self.root / BASIC).read_bytes()
        self.assertTrue(self.write())
        self.assertEqual(self.write(), [])
        self.assertEqual(generator.check(self.root, self.src), [])
        self.assertEqual((self.root / BASIC).read_bytes(), basic)

    def test_world_sim_techniques_regenerate_byte_identical(self) -> None:
        before = (self.root / generator.WORLD_SIM_REL / "techniques.json").read_bytes()
        changed = self.write()
        self.assertNotIn((generator.WORLD_SIM_REL / "techniques.json").as_posix(), changed)
        self.assertEqual((self.root / generator.WORLD_SIM_REL / "techniques.json").read_bytes(), before)

    def test_technique_file_shape(self) -> None:
        self.write()
        doc = json.loads((self.root / generator.TECHNIQUE_REL / "tiangang_jiandian.json").read_text("utf-8"))
        self.assertEqual(list(doc), ["translation_key", "category", "grade", "elements", "school", "requirements",
                                     "lineage"])
        self.assertEqual(doc["requirements"], {
            "minimum_realm": "myvillage:foundation_establishment", "minimum_stage": "myvillage:foundation_early",
            "minimum_element_affinity": {"myvillage:metal": 1500}})
        core = json.loads((self.root / generator.TECHNIQUE_REL / "yinqi_jue.json").read_text("utf-8"))
        self.assertNotIn("school", core)
        self.assertNotIn("lineage", core)
        self.assertEqual(core["elements"], [])
        self.assertEqual(core["effects"], {"core": {"meditation_route": "xiaozhoutian"}})
        self.assertEqual(core["requirements"], {"minimum_realm": "myvillage:qi_refining",
                                                "minimum_stage": "myvillage:qi_refining_1"})

    def test_removed_rows_leave_no_stale_outputs(self) -> None:
        self.write()
        self.edit_source("schools.json", lambda d: d.update(schools=[s for s in d["schools"] if s["id"] != "spear"]))
        changed = self.write()
        self.assertIn(f"{(generator.SCHOOL_REL / 'spear.json').as_posix()} (removed)", changed)
        for code in generator.LANGS:
            lang = json.loads((self.root / generator.LANG_REL / f"{code}.json").read_text("utf-8"))
            self.assertNotIn("cultivation.school.myvillage.spear", lang)
        self.assertTrue((self.root / BASIC).is_file())

    def test_bad_sources_are_refused(self) -> None:
        def bad(d):
            d["techniques"][0]["previous"] = d["techniques"][1]["id"]
            d["techniques"][1]["previous"] = d["techniques"][0]["id"]
            d["techniques"][2]["grade"] = 5
        self.edit_source("catalogue.json", bad)
        with self.assertRaises(generator.CatalogueError) as caught:
            generator.plan(self.root, self.src)
        text = "\n".join(caught.exception.problems)
        self.assertIn("lineage.previous cycle", text)
        self.assertIn("grade must be an integer 1..4", text)
        self.assertEqual(self.run_cli(), 2)


class CheckTest(Fixture):
    def test_check_fails_on_a_tampered_output(self) -> None:
        self.assertEqual(self.run_cli(), 0)
        self.assertEqual(self.run_cli("--check"), 0)
        path = self.root / generator.TECHNIQUE_REL / "gengjin_jianjue.json"
        path.write_text(path.read_text("utf-8").replace('"grade": 2', '"grade": 3'), encoding="utf-8")
        self.assertEqual(self.run_cli("--check"), 1)
        self.assertEqual(generator.check(self.root, self.src),
                         [f"{(generator.TECHNIQUE_REL / 'gengjin_jianjue.json').as_posix()}: differs from the "
                          f"generated content"])
        self.assertEqual(self.run_cli(), 0)
        self.assertEqual(self.run_cli("--check"), 0)

    def test_check_fails_on_a_missing_or_stale_output(self) -> None:
        self.write()
        (self.root / generator.HERITAGE_REL / "taibai_jianmai.json").unlink()
        (self.root / generator.SCHOOL_REL / "saber.json").write_text("{}\n", encoding="utf-8")
        problems = generator.check(self.root, self.src)
        self.assertIn(f"{(generator.HERITAGE_REL / 'taibai_jianmai.json').as_posix()}: missing", problems)
        self.assertIn(f"{(generator.SCHOOL_REL / 'saber.json').as_posix()}: stale (not in the catalogue)", problems)
        self.assertEqual(self.run_cli("--check"), 1)

    def test_the_checked_in_tree_is_current(self) -> None:
        self.assertEqual(generator.check(), [])


class LangTest(Fixture):
    def lang(self, code: str) -> str:
        return (self.root / generator.LANG_REL / f"{code}.json").read_text(encoding="utf-8")

    def test_only_managed_keys_change_and_every_other_key_keeps_its_place(self) -> None:
        managed = lambda k: generator.managed_key(k, "myvillage")  # noqa: E731
        for code in generator.LANGS:
            path = self.root / generator.LANG_REL / f"{code}.json"
            doc = json.loads(path.read_text("utf-8"))
            keys = list(doc)
            # an existing managed key in the middle, a stale one, and the hand-written one
            seeded = {}
            for i, key in enumerate(keys):
                seeded[key] = doc[key]
                if i == 3:
                    seeded["cultivation.technique.myvillage.jingang_gong"] = "old"
                    seeded["cultivation.heritage.myvillage.gone"] = "stale"
            path.write_text(generator.dump(seeded), encoding="utf-8")
        before = {code: json.loads(self.lang(code)) for code in generator.LANGS}
        self.write()
        for code in generator.LANGS:
            after = json.loads(self.lang(code))
            others_before = [(k, v) for k, v in before[code].items() if not managed(k)
                             or k == BASIC_KEY]
            others_after = [(k, v) for k, v in after.items() if not managed(k)
                            or k == BASIC_KEY]
            self.assertEqual(others_after, others_before, code)
            order = list(after)
            self.assertEqual(order.index("cultivation.technique.myvillage.jingang_gong"),
                             list(before[code]).index("cultivation.technique.myvillage.jingang_gong"))
            self.assertEqual(after["cultivation.technique.myvillage.jingang_gong"], "金刚功")
            self.assertNotIn("cultivation.heritage.myvillage.gone", after)
            self.assertEqual(after["cultivation.technique.myvillage.taibai_jianjing"], "太白剑经")
            self.assertEqual(after["cultivation.school.myvillage.fist"], "拳掌")
            self.assertEqual(after["cultivation.heritage.myvillage.taibai_jianmai"], "太白剑脉")
            new_keys = [k for k in order if managed(k) and k not in before[code]]
            self.assertEqual(order[-len(new_keys):], new_keys, "new keys are appended as one group")
            self.assertTrue(self.lang(code).startswith('{\n  "'))
        basic_en = json.loads(self.lang("en_us"))[BASIC_KEY]
        self.assertEqual(basic_en, "Basic Breathing Technique")

    def test_a_non_canonical_lang_file_is_refused(self) -> None:
        path = self.root / generator.LANG_REL / "zh_cn.json"
        path.write_text(json.dumps(json.loads(path.read_text("utf-8")), ensure_ascii=False, indent=4) + "\n",
                        encoding="utf-8")
        with self.assertRaises(generator.CatalogueError):
            generator.plan(self.root, self.src)


class ClassificationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.rules = json.loads((generator.SOURCE_DIR / "rules.json").read_text(encoding="utf-8"))

    def test_names_classify_by_their_fragments(self) -> None:
        cases = {
            "庚金剑诀": ("active", "sword"), "天罡剑典": ("active", "sword"), "太白剑经": ("active", "sword"),
            "某某剑章": ("active", "sword"), "锻体诀": ("body", None), "金鳞淬体诀": ("body", None),
            "金罡护体功": ("body", None), "庚金不灭体": ("body", None), "万劫金身诀": ("body", None),
            "铁骨功": ("body", None), "金刚功": ("body", None), "磐石功": ("body", None), "岩甲诀": ("body", None),
            "石甲玄功": ("body", None), "元神不灭诀": ("core", None), "青木长春功": ("core", None),
            "白虎锻金功": ("core", None), "七杀金章": ("core", None),
        }
        for name, expected in cases.items():
            self.assertEqual(importer.classify(name, self.rules), expected, name)

    def test_catalogue_agrees_with_a_fresh_import_except_hand_set_lineage_and_school(self) -> None:
        sim = json.loads((generator.ROOT / generator.WORLD_SIM_REL / "techniques.json").read_text("utf-8"))
        seeded = importer.rows_from_world_sim(sim, self.rules)
        catalogue = json.loads((generator.SOURCE_DIR / "catalogue.json").read_text("utf-8"))["techniques"]
        fields = ("id", "zh", "category", "grade", "element")
        self.assertEqual([{k: r[k] for k in fields} for r in catalogue], [{k: r[k] for k in fields} for r in seeded])
        self.assertEqual(importer.catalogue_text(catalogue),
                         (generator.SOURCE_DIR / "catalogue.json").read_text("utf-8"))


class ValidatorCrossCheckTest(unittest.TestCase):
    """The world-sim validator against a copy of the generated tree."""

    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        for rel in (validator.DATA_REL, validator.PLAYER_REALM_REL, validator.TECHNIQUE_REL, validator.SCHOOL_REL,
                    validator.LANG_REL, validator.SIM_REL):
            shutil.copytree(validator.ROOT / rel, self.root / rel)
        self.baseline = set(validator.validate(self.root).errors)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def edit_technique(self, tid: str, change) -> None:
        path = self.root / validator.TECHNIQUE_REL / f"{tid}.json"
        doc = json.loads(path.read_text(encoding="utf-8"))
        change(doc)
        path.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    def new_errors(self) -> list[str]:
        return sorted(set(validator.validate(self.root).errors) - self.baseline)

    def test_the_generated_tree_is_clean(self) -> None:
        self.assertEqual([e for e in self.baseline if e.startswith(("datapack:", "data:"))], [])

    def test_grade_mismatch(self) -> None:
        self.edit_technique("gengjin_jianjue", lambda d: d.update(grade=3))
        errors = self.new_errors()
        self.assertTrue(any("datapack: technique/gengjin_jianjue.json: grade 3 disagrees with the ledger's xuan"
                            in e for e in errors), errors)

    def test_element_mismatch_and_missing_file(self) -> None:
        self.edit_technique("qingxi_jue", lambda d: d.update(elements=["myvillage:fire"]))
        (self.root / validator.TECHNIQUE_REL / "hantan_gong.json").unlink()
        errors = self.new_errors()
        self.assertTrue(any("technique/qingxi_jue.json: elements" in e for e in errors), errors)
        self.assertTrue(any("hantan_gong has no datapack file" in e for e in errors), errors)

    def test_lineage_cycle(self) -> None:
        self.edit_technique("gengjin_yinqi_fa", lambda d: d.update(lineage={"previous": "myvillage:taibai_jianjing"}))
        errors = self.new_errors()
        self.assertTrue(any("lineage.previous cycle through ['gengjin_jianjue', 'gengjin_yinqi_fa', "
                            "'taibai_jianjing', 'tiangang_jiandian']" in e for e in errors), errors)

    def test_heritage_rules(self) -> None:
        path = self.root / validator.DATA_REL / "heritages.json"
        doc = json.loads(path.read_text(encoding="utf-8"))
        doc["heritages"][1]["techniques"].append("taibai_jianjing")
        doc["heritages"][2]["techniques"].append("no_such_technique")
        doc["heritages"][0]["school"] = "saber"
        doc["heritages"].append({"id": "empty", "name": "空", "school": "none", "techniques": []})
        path.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        errors = "\n".join(self.new_errors())
        self.assertIn("heritages[1] mixes the elements ['metal', 'wood']", errors)
        self.assertIn("taibai_jianjing already belongs to heritage taibai_jianmai", errors)
        self.assertIn("'no_such_technique', which is not in techniques.json", errors)
        self.assertIn("heritages[0].school 'saber' is not none or a datapack school", errors)
        self.assertIn("heritages[3].techniques must be a non-empty list", errors)

    def test_heritage_school_must_match_its_techniques(self) -> None:
        self.edit_technique("tiangang_jiandian", lambda d: d.pop("school"))
        errors = self.new_errors()
        self.assertTrue(any("technique/tiangang_jiandian.json: school None is not heritage taibai_jianmai's 'sword'"
                            in e for e in errors), errors)


if __name__ == "__main__":
    unittest.main()
