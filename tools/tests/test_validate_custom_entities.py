import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "tools" / "validate_custom_entities.py"
SPEC = importlib.util.spec_from_file_location("validate_custom_entities", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)

WOLF = "myvillage:demon_wolf"
NPC = "myvillage:cultivator"
RESOURCES = ROOT / "src/main/resources"


LOOKS = ["default", "f_novice", "f_adept"]


def load(relative: str):
    return json.loads((RESOURCES / relative).read_text(encoding="utf-8"))


def pending_looks() -> list[str]:
    """Cultivator looks whose generated files are not written yet (the female looks land with their npcgen
    definitions); the validator reports exactly their missing files. Empty once every look is built."""
    return [look for look in LOOKS[1:]
            if not all((RESOURCES / path).is_file() for path in MODULE.npc_look_files(NPC, look).values())]


def unexpected_errors(report) -> list[str]:
    """The report's errors less the ``missing_file`` errors of pending looks and their unwritten definitions."""
    pending = pending_looks()

    def expected(error: str) -> bool:
        return error.startswith("missing_file:") and any(f"cultivator_{look}" in error for look in pending)

    return [error for error in report["errors"] if not expected(error)]


class CustomEntityValidationTest(unittest.TestCase):
    def test_simple_fox_surface_is_complete(self) -> None:
        report = MODULE.validate(ROOT)
        self.assertEqual([], unexpected_errors(report))
        self.assertEqual("pass" if not pending_looks() else "fail", report["status"])
        self.assertEqual([48, 32], report["texture"]["dimensions"])
        self.assertEqual(998, report["texture"]["used_texels"])

    def test_every_indexed_beast_is_validated(self) -> None:
        report = MODULE.validate(ROOT)
        index = load(MODULE.BEAST_INDEX)
        self.assertEqual(index["beasts"], list(report["beasts"]))
        wolf = report["beasts"][WOLF]
        self.assertEqual(0, wolf["errors"])
        self.assertEqual([m["id"] for m in load("data/myvillage/beast/demon_wolf.json")["moves"]], wolf["moves"])
        self.assertTrue({"idle", "walk", "run", "stagger"} <= set(wolf["clips"]))
        self.assertEqual(wolf["textures"]["texture"], wolf["textures"]["glow"])


class BeastDataTest(unittest.TestCase):
    def setUp(self) -> None:
        self.data = load("data/myvillage/beast/demon_wolf.json")

    def errors(self, data=None):
        return MODULE.check_beast_data(self.data if data is None else data, WOLF)

    def move(self, index=0):
        return self.data["moves"][index]

    def test_shipped_data_satisfies_the_invariants(self) -> None:
        self.assertEqual([], self.errors())

    def test_tick_ordering_is_enforced(self) -> None:
        total = self.move()["total_ticks"]
        cases = {
            "active_ticks_outside_move": ("active_ticks", [0, total]),
            "immune_ticks_outside_move": ("immune_ticks", [0, total]),
            "windup_after_first_active_tick": ("windup_ticks", self.move()["active_ticks"][0] + 1),
        }
        for code, (field, value) in cases.items():
            data = copy.deepcopy(self.data)
            data["moves"][0][field] = value
            self.assertTrue(any(e.startswith(code) for e in self.errors(data)), (code, self.errors(data)))

        data = copy.deepcopy(self.data)
        data["moves"][0]["turn_lock_tick"] = data["moves"][0]["windup_ticks"] + 1
        self.assertTrue(any(e.startswith("turn_lock_after_windup") for e in self.errors(data)))
        data = copy.deepcopy(self.data)
        data["moves"][0]["lunge"]["tick"] = data["moves"][0]["active_ticks"][1] + 1
        self.assertTrue(any(e.startswith("lunge_tick_outside_lock_to_last_active") for e in self.errors(data)))

    def test_fields_are_exact(self) -> None:
        data = copy.deepcopy(self.data)
        data["moves"][0]["speed"] = 1
        del data["moves"][1]["hit"]
        data["attributes"]["luck"] = 1.0
        errors = self.errors(data)
        self.assertIn("unknown_field:moves[0].speed", errors)
        self.assertIn("missing_field:moves[1].hit", errors)
        self.assertIn("unknown_field:attributes.luck", errors)

    def test_ids_and_clip_names_are_unique_and_not_reserved(self) -> None:
        data = copy.deepcopy(self.data)
        data["moves"][1]["id"] = data["moves"][0]["id"]
        data["moves"][1]["animation"] = "walk"
        data["entity"] = "myvillage:other"
        errors = self.errors(data)
        self.assertIn(f"duplicate_move_id:{self.move()['id']}", errors)
        self.assertTrue(any(e.startswith("clip_reserved_or_reused") for e in errors))
        self.assertIn("entity_mismatch:myvillage:other", errors)
        data = copy.deepcopy(self.data)
        data["stagger"]["animation"] = "idle"
        self.assertIn("reserved_clip:stagger.animation:idle", self.errors(data))

    def test_value_rules(self) -> None:
        data = copy.deepcopy(self.data)
        data["attributes"]["knockback_resistance"] = 1.5
        data["moves"][0]["use_range"] = [3.0, 1.0]
        data["moves"][0]["hit"]["forward"] = [2.0, 1.0]
        data["moves"][0]["weight"] = 0
        errors = self.errors(data)
        self.assertIn("invalid_attribute:knockback_resistance:fraction", errors)
        for code in ("invalid_use_range", "invalid_hit_forward", "invalid_weight"):
            self.assertTrue(any(e.startswith(code) for e in errors), code)

    def test_index_lists_unique_ids(self) -> None:
        errors, beasts = MODULE.check_beast_index({"schema": 1, "beasts": [WOLF, WOLF]})
        self.assertIn("duplicate_beast_id", errors)
        self.assertEqual([WOLF], beasts)


class BeastAssetFilesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.data = load("data/myvillage/beast/demon_wolf.json")
        self.model = load("assets/myvillage/beast/demon_wolf_model.json")
        self.animations = load("assets/myvillage/beast/demon_wolf_animations.json")
        errors, self.bones = MODULE.check_beast_model(self.model, WOLF)
        self.assertEqual([], errors)

    def errors(self, animations=None, data=None):
        return MODULE.check_beast_animations(
            self.animations if animations is None else animations, WOLF, self.bones, self.data if data is None else data)

    def test_shipped_clips_pass(self) -> None:
        self.assertEqual([], self.errors())

    def test_move_clip_length_follows_the_server_ticks(self) -> None:
        data = copy.deepcopy(self.data)
        data["moves"][0]["total_ticks"] += 2
        self.assertTrue(any(e.startswith(f"move_clip_length:{data['moves'][0]['animation']}") for e in self.errors(data=data)))

    def test_required_clips_and_looping(self) -> None:
        animations = copy.deepcopy(self.animations)
        del animations["clips"][self.data["moves"][1]["animation"]]
        animations["clips"]["walk"]["loop"] = False
        animations["clips"][self.data["stagger"]["animation"]]["loop"] = True
        errors = self.errors(animations)
        self.assertIn(f"missing_clip:{self.data['moves'][1]['animation']}", errors)
        self.assertIn("clip_loop_must_be_true:walk", errors)
        self.assertIn(f"clip_loop_must_be_false:{self.data['stagger']['animation']}", errors)

    def test_channels_name_model_bones(self) -> None:
        animations = copy.deepcopy(self.animations)
        animations["clips"]["idle"]["channels"][0]["bone"] = "wing"
        self.assertIn("channel_bone_missing:idle:wing", self.errors(animations))

    def test_model_bones_list_parents_first(self) -> None:
        model = copy.deepcopy(self.model)
        model["bones"].reverse()
        model["look"]["bone"] = "horn"
        errors, _ = MODULE.check_beast_model(model, WOLF)
        self.assertTrue(any(e.startswith("parent_not_listed_before") for e in errors))
        self.assertIn("look_bone_missing:horn", errors)


class BeastContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.contract = (ROOT / "genops/contracts/entities/demon_wolf.yaml").read_text(encoding="utf-8")
        self.data = load("data/myvillage/beast/demon_wolf.json")
        self.lang = {locale: load(f"assets/myvillage/lang/{locale}.json") for locale in ("en_us", "zh_cn")}

    def errors(self, contract=None, data=None):
        return MODULE.check_beast_contract(
            self.contract if contract is None else contract, WOLF, self.data if data is None else data, self.lang)

    def test_contract_agrees_with_data_and_lang(self) -> None:
        self.assertEqual([], self.errors())

    def test_a_new_move_in_the_data_needs_the_contract(self) -> None:
        data = copy.deepcopy(self.data)
        extra = copy.deepcopy(data["moves"][0])
        extra["id"], extra["animation"] = "myvillage:demon_wolf_howl", "howl"
        data["moves"].append(extra)
        errors = self.errors(data=data)
        self.assertIn("contract_move_ids_differ_from_data", errors)
        self.assertIn("contract_move_clips_differ_from_data", errors)
        self.assertTrue(any(e.startswith("contract_rendering_clips_missing") for e in errors))

    def test_contract_never_repeats_tuned_values(self) -> None:
        contract = self.contract.replace("  registered:\n", "  max_health: 50.0\n  registered:\n", 1)
        self.assertIn("contract_repeats_tuned_attribute_values", self.errors(contract))

    def test_attribute_names_and_display_names_must_match(self) -> None:
        data = copy.deepcopy(self.data)
        data["attributes"]["luck"] = 1.0
        self.assertTrue(any(e.startswith("contract_attribute_names") for e in self.errors(data=data)))
        lang = copy.deepcopy(self.lang)
        lang["zh_cn"]["entity.myvillage.demon_wolf"] = "狼"
        self.assertIn("contract_display_name_differs_from_lang:zh_cn",
                      MODULE.check_beast_contract(self.contract, WOLF, self.data, lang))


class NpcValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.model = load("assets/myvillage/npc/cultivator_model.json")
        self.animations = load("assets/myvillage/npc/cultivator_animations.json")
        self.contract = (ROOT / "genops/contracts/entities/cultivator.yaml").read_text(encoding="utf-8")
        self.lang = {locale: load(f"assets/myvillage/lang/{locale}.json") for locale in ("en_us", "zh_cn")}

    def test_every_generated_npc_is_validated(self) -> None:
        report = MODULE.validate(ROOT)
        # One entity, one npcgen definition per look: the definitions group by ENTITY into looks.
        self.assertEqual([NPC], MODULE.npc_ids(ROOT))
        self.assertEqual([NPC], list(report["npcs"]))
        self.assertEqual(1, report["entities"].count(NPC))
        npc = report["npcs"][NPC]
        self.assertEqual(LOOKS, list(npc["looks"]))
        self.assertEqual([], unexpected_errors(report))
        self.assertEqual(["idle", "walk"], npc["clips"])
        self.assertEqual(self.model["scale"], npc["scale"])
        self.assertEqual([self.model["texture"]["width"], self.model["texture"]["height"]], npc["texture"])
        for look in LOOKS:
            if look in pending_looks():
                self.assertEqual(3, npc["looks"][look]["errors"], look)  # model, clips, texture missing
                continue
            self.assertEqual(0, npc["looks"][look]["errors"], look)
            self.assertEqual(["idle", "walk"], npc["looks"][look]["clips"], look)
            self.assertEqual(self.model["scale"], npc["looks"][look]["scale"], look)

    def test_definitions_group_by_entity_and_a_missing_module_is_a_missing_file(self) -> None:
        groups, errors = MODULE.npc_definitions(ROOT)
        self.assertEqual([NPC], list(groups))
        written = {look: f"cultivator_{look}" for look in LOOKS[1:] if (ROOT / f"{MODULE.NPCGEN_DEFS}/cultivator_{look}.py").is_file()}
        self.assertEqual({"default": "cultivator", **written}, groups[NPC])
        self.assertEqual(len(LOOKS) - 1 - len(written), len(errors))
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            defs = root / MODULE.NPCGEN_DEFS
            defs.mkdir(parents=True)
            (root / MODULE.NPCGEN_BUILD).write_text('DEFINITIONS = ("monk", "monk_old", "nun", "ghost")\n', encoding="utf-8")
            (defs / "monk.py").write_text('ENTITY = "monk"\nLOOK = "default"\n', encoding="utf-8")
            (defs / "monk_old.py").write_text('ENTITY = "monk"  # dir\nLOOK = "old"\n', encoding="utf-8")
            (defs / "nun.py").write_text('ENTITY = "nun"\n', encoding="utf-8")
            groups, errors = MODULE.npc_definitions(root)
            self.assertEqual({"myvillage:monk": {"default": "monk", "old": "monk_old"}}, groups)
            self.assertEqual(["npc_definition_without_entity_or_look:nun", f"missing_file:{defs / 'ghost.py'}"], errors)
            self.assertEqual(["myvillage:monk"], MODULE.npc_ids(root))

    def test_look_files_follow_the_renderer(self) -> None:
        self.assertEqual({"model": "assets/myvillage/npc/cultivator_model.json",
                          "animations": "assets/myvillage/npc/cultivator_animations.json",
                          "texture": "assets/myvillage/textures/entity/cultivator/cultivator.png"},
                         MODULE.npc_look_files(NPC, "default"))
        self.assertEqual({"model": "assets/myvillage/npc/cultivator_f_adept_model.json",
                          "animations": "assets/myvillage/npc/cultivator_f_adept_animations.json",
                          "texture": "assets/myvillage/textures/entity/cultivator/cultivator_f_adept.png"},
                         MODULE.npc_look_files(NPC, "f_adept"))

    def test_contract_looks_equal_definitions_and_java(self) -> None:
        java = (ROOT / "src/main/java/com/example/myvillage/entity/npc/CultivatorEntity.java").read_text(encoding="utf-8")
        self.assertEqual(LOOKS, MODULE.java_looks(java))
        self.assertIsNone(MODULE.java_looks("class Plain {}"))
        defined = {"default": "cultivator", "f_novice": "cultivator_f_novice", "f_adept": "cultivator_f_adept"}
        errors, looks = MODULE.check_npc_looks(ROOT, self.contract, NPC, defined, LOOKS)
        self.assertEqual([], errors)
        self.assertEqual(LOOKS, looks)
        self.assertEqual(["default", "f_novice", "f_adept"],
                         [e["id"] for e in MODULE.yaml_list_entries("looks:\n" + MODULE.yaml_block(self.contract, "looks"), "looks")])
        # A look the Java list lacks, a look the contract lacks, a file path or layer off the renderer's.
        self.assertIn("java_looks_differ_from_contract:['default', 'f_novice']!=['default', 'f_novice', 'f_adept']",
                      MODULE.check_npc_looks(ROOT, self.contract, NPC, defined, LOOKS[:2])[0])
        self.assertIn("java_looks_must_start_with_default:['f_novice', 'default', 'f_adept']",
                      MODULE.check_npc_looks(ROOT, self.contract, NPC, defined, ["f_novice", "default", "f_adept"])[0])
        head, _, tail = self.contract.partition("  - id: f_adept\n")
        without_adept = head + tail[tail.index("\n\ntexture_art_direction:"):]
        self.assertIn("contract_looks_differ_from_definitions:['default', 'f_novice']!=['default', 'f_adept', 'f_novice']",
                      MODULE.check_npc_looks(ROOT, without_adept, NPC, defined, LOOKS[:2])[0])
        moved = self.contract.replace("textures/entity/cultivator/cultivator_f_novice.png",
                                      "textures/entity/cultivator_f_novice/cultivator_f_novice.png")
        self.assertTrue(any(e.startswith("contract_look_texture:f_novice:")
                            for e in MODULE.check_npc_looks(ROOT, moved, NPC, defined, LOOKS)[0]))
        layer = self.contract.replace("myvillage:cultivator#f_adept", "myvillage:cultivator#main")
        self.assertTrue(any(e.startswith("contract_look_model_layer:f_adept:")
                            for e in MODULE.check_npc_looks(ROOT, layer, NPC, defined, LOOKS)[0]))
        stray = self.contract.replace("defs/cultivator_f_adept.py", "defs/somebody_else.py")
        self.assertIn("contract_look_definition_not_in_npcgen:f_adept:tools/npcgen/defs/somebody_else.py",
                      MODULE.check_npc_looks(ROOT, stray, NPC, {"default": "cultivator", "f_novice": "cultivator_f_novice"}, LOOKS)[0])

    def test_a_look_whose_definition_is_not_written_yet_is_pending(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / MODULE.NPCGEN_DEFS).mkdir(parents=True)
            (root / MODULE.NPCGEN_BUILD).write_text((ROOT / MODULE.NPCGEN_BUILD).read_text(encoding="utf-8"), encoding="utf-8")
            (root / MODULE.NPCGEN_DEFS / "cultivator.py").write_text('ENTITY = "cultivator"\nLOOK = "default"\n', encoding="utf-8")
            groups, errors = MODULE.npc_definitions(root)
            self.assertEqual({NPC: {"default": "cultivator"}}, groups)
            self.assertEqual([f"missing_file:{root / MODULE.NPCGEN_DEFS / f'cultivator_{look}.py'}" for look in LOOKS[1:]], errors)
            # Only the missing modules are reported; the contract's looks still name every look to check.
            self.assertEqual(([], LOOKS), MODULE.check_npc_looks(root, self.contract, NPC, groups[NPC], LOOKS))

    def test_every_look_keeps_the_body_scale_and_look_bone(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            files = MODULE.npc_look_files(NPC, "f_test")
            for kind, path in MODULE.npc_look_files(NPC, "default").items():
                target = root / MODULE.RESOURCE_ROOT / files[kind]
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((RESOURCES / path).read_bytes())
            default = {"scale": self.model["scale"]}
            self.assertEqual([], MODULE.check_npc_look(root, NPC, "f_test", "head", default)[0])
            model = copy.deepcopy(self.model)
            model["scale"] = 0.25
            model["look"]["bone"] = "body"
            model["bones"] = [b for b in model["bones"] if b["name"] != "hair_back"]
            (root / MODULE.RESOURCE_ROOT / files["model"]).write_text(json.dumps(model), encoding="utf-8")
            errors, report = MODULE.check_npc_look(root, NPC, "f_test", "head", default)
            self.assertIn("npc_look_bone_differs_from_contract:f_test:body!=head", errors)
            self.assertIn("npc_body_bones_missing:f_test:['hair_back']", errors)
            self.assertIn("npc_scale_differs_from_default:f_test:0.25!=0.5", errors)
            self.assertEqual(len(errors), report["errors"])
            (root / MODULE.RESOURCE_ROOT / files["texture"]).unlink()
            self.assertIn(f"missing_file:{root / MODULE.RESOURCE_ROOT / files['texture']}",
                          MODULE.check_npc_look(root, NPC, "f_test", "head", default)[0])

    def test_model_scale_is_optional_and_positive(self) -> None:
        errors, bones = MODULE.check_beast_model(self.model, NPC)
        self.assertEqual([], errors)
        self.assertIn("head", bones)
        self.assertNotIn("scale", load("assets/myvillage/beast/demon_wolf_model.json"))
        model = copy.deepcopy(self.model)
        model["scale"] = 0
        self.assertIn("invalid_scale", MODULE.check_beast_model(model, NPC)[0])
        model["scale"], model["tint"] = 0.5, 1
        self.assertIn("unknown_field:tint", MODULE.check_beast_model(model, NPC)[0])

    def test_idle_and_walk_must_exist_and_loop(self) -> None:
        errors, bones = MODULE.check_beast_model(self.model, NPC)

        def check(animations):
            return MODULE.check_beast_animations(animations, NPC, bones, None, looping=MODULE.NPC_CLIPS)

        self.assertEqual([], check(self.animations))  # no run clip is asked of an NPC
        animations = copy.deepcopy(self.animations)
        del animations["clips"]["walk"]
        animations["clips"]["idle"]["loop"] = False
        animations["clips"]["idle"]["channels"][0]["bone"] = "tail"
        errors = check(animations)
        self.assertIn("missing_clip:walk", errors)
        self.assertIn("clip_loop_must_be_true:idle", errors)
        self.assertIn("channel_bone_missing:idle:tail", errors)

    def test_contract_agrees_with_files_and_lang(self) -> None:
        self.assertEqual([], MODULE.check_npc_contract(self.contract, NPC, self.lang))
        self.assertIn("contract_kind_must_be_npc",
                      MODULE.check_npc_contract(self.contract.replace("  kind: npc", "  kind: monster"), NPC, self.lang))
        self.assertIn("contract_data_source:model",
                      MODULE.check_npc_contract(self.contract.replace("npc/cultivator_model.json", "beast/x.json", 1), NPC, self.lang))
        lang = copy.deepcopy(self.lang)
        lang["zh_cn"]["entity.myvillage.cultivator"] = "道士"
        self.assertIn("contract_display_name_differs_from_lang:zh_cn", MODULE.check_npc_contract(self.contract, NPC, lang))
        without_walk = self.contract.replace("    - id: walk\n", "", 1)
        self.assertTrue(any(e.startswith("contract_rendering_clips_missing")
                            for e in MODULE.check_npc_contract(without_walk, NPC, self.lang)))

    def test_state_names_the_synced_field_and_the_persisted_tag(self) -> None:
        java = ROOT / "src/main/java/com/example/myvillage/entity/npc"
        base, cls = "com.example.myvillage.entity.npc.NpcEntity", "com.example.myvillage.entity.npc.CultivatorEntity"
        sources = {base: (java / "NpcEntity.java").read_text(encoding="utf-8"),
                   cls: (java / "CultivatorEntity.java").read_text(encoding="utf-8")}
        self.assertEqual([], MODULE.check_npc_state(self.contract, sources))
        self.assertEqual(["ledger_person_id", "look", "ledger_role", "colours"],
                         [e["id"] for e in MODULE.yaml_list_entries(MODULE.yaml_block(self.contract, "state"), "synced")])

        def state(synced: str, persisted: str) -> str:
            head, _, rest = self.contract.partition("\nstate:\n")
            tail = rest[rest.index("  custom_state:"):]
            return f"{head}\nstate:\n  synced:{synced}\n  persisted:{persisted}\n{tail}"

        empty = state(" []", " []")
        self.assertEqual([f"java_synced_data_not_in_contract:{base}#{field}"
                          for field in ("DATA_COLOURS", "DATA_LEDGER_PERSON", "DATA_LEDGER_ROLE", "DATA_LOOK")],
                         MODULE.check_npc_state(empty, sources))
        renamed = self.contract.replace("NpcEntity#DATA_LEDGER_PERSON", "NpcEntity#DATA_PERSON")
        self.assertIn(f"contract_synced_without_java_field:{base}#DATA_PERSON", MODULE.check_npc_state(renamed, sources))
        wrong_tag = self.contract.replace("    - id: WorldSimPerson\n", "    - id: LedgerPerson\n")
        self.assertIn("contract_persisted_tag_not_in_java:LedgerPerson", MODULE.check_npc_state(wrong_tag, sources))
        plain = {base: sources[base].replace("DATA_LEDGER_PERSON =", "DATA_X ="), cls: sources[cls]}
        self.assertIn(f"java_synced_data_not_in_contract:{base}#DATA_X", MODULE.check_npc_state(self.contract, plain))


class SourceScanTest(unittest.TestCase):
    def test_scan_reports_each_forbidden_needle(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            clean = root / "combat/Clean.java"
            leak = root / "combat/Leak.java"
            clean.parent.mkdir(parents=True)
            clean.write_text("class Clean implements StaggerResistant {}\n", encoding="utf-8")
            leak.write_text("import com.example.myvillage.entity.beast.BeastEntity;\n", encoding="utf-8")
            errors = MODULE.scan_sources([clean, leak], ["entity.beast", "BeastEntity", "demon_wolf"], root, "leak")
            self.assertEqual(["leak:combat/Leak.java:entity.beast", "leak:combat/Leak.java:BeastEntity"], errors)


if __name__ == "__main__":
    unittest.main()
