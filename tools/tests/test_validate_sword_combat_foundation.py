from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

from tools import validate_sword_combat_foundation as validator

RESOURCES = "src/main/resources"
DATA = f"{RESOURCES}/data/myvillage/combat"
STYLE = f"{DATA}/style/basic_sword.json"
WEAPON = f"{DATA}/weapon/qingfeng_sword.json"
RIG = f"{RESOURCES}/assets/myvillage/combat/qingfeng_first_person.json"
GEOMETRY = f"{RESOURCES}/assets/myvillage/combat/qingfeng_sword_geometry.json"
ANIMATIONS = f"{RESOURCES}/assets/myvillage/player_animations/sword_combat.json"
SOUNDS = f"{RESOURCES}/assets/myvillage/sounds.json"
EN_US = f"{RESOURCES}/assets/myvillage/lang/en_us.json"
COMBAT_JAVA = "src/main/java/com/example/myvillage/combat/"
CLIENT_COMBAT_JAVA = "src/main/java/com/example/myvillage/client/combat/"
THRUST = "myvillage:basic_sword_01_thrust"
HORIZONTAL = "myvillage:basic_sword_02_horizontal_cut"

# The second weapon (add-lingxiao-spear design).  Its data, item model, 3D model, textures, contract,
# and model generator are committed; its first-person rig and third-person animations are built
# synthetically in the fixture from its style file (see SECOND_WEAPON_SYNTHETIC), so the cross-file
# tests do not depend on the committed rig and PAL file.  Tests that run the PAL generator's --check
# first regenerate the PAL files with it (regenerate_player_animations) and fail if it cannot.
SPEAR_WEAPON_ID = "myvillage:lingxiao_spear"
SPEAR_STYLE = f"{DATA}/style/basic_spear.json"
SPEAR_WEAPON = f"{DATA}/weapon/lingxiao_spear.json"
SPEAR_RIG = f"{RESOURCES}/assets/myvillage/combat/lingxiao_spear_first_person.json"
SPEAR_GEOMETRY = f"{RESOURCES}/assets/myvillage/combat/lingxiao_spear_geometry.json"
SPEAR_ANIMATIONS = f"{RESOURCES}/assets/myvillage/player_animations/spear_combat.json"
SPEAR_MODEL = f"{RESOURCES}/assets/myvillage/models/item/lingxiao_spear.json"
SPEAR_MODEL_3D = f"{RESOURCES}/assets/myvillage/models/item/lingxiao_spear_3d.json"
SPEAR_MODEL_TEXTURE = f"{RESOURCES}/assets/myvillage/textures/item/lingxiao_spear_model.png"
SPEAR_TEXTURE = f"{RESOURCES}/assets/myvillage/textures/item/lingxiao_spear.png"
SPEAR_GENERATOR = "tools/gen_lingxiao_spear_model.py"
PAL_GENERATOR = "tools/gen_sword_pal_anims.py"

_REPOSITORY_DATA = validator.combat_data.load(validator.ROOT)
FIXTURE = (
    validator.PAL_JAR_NAME,
    "build.gradle",
    "gradle.properties",
    "README.md",
    "AGENTS.md",
    "docs/ai-kb/32_pal_combat_integration.md",
    "src/main/java",
    f"{RESOURCES}/META-INF/neoforge.mods.toml",
    DATA,
    f"{RESOURCES}/assets/myvillage/combat",
    f"{RESOURCES}/assets/myvillage/player_animations",
    f"{RESOURCES}/assets/myvillage/lang",
    f"{RESOURCES}/assets/myvillage/models/item",
    f"{RESOURCES}/assets/myvillage/textures/item",
    SOUNDS,
    validator.QINGFENG_RECIPE,
    validator.SWORD_TAG,
    validator.BLADE_CUT_PARTICLE,
    validator.BLADE_CUT_TEXTURE,
    "tools/combat_data.py",
    # The fixed generators and the model generator each committed geometry contract names.
    *(script for script, _ in validator.generator_checks(validator.ROOT, _REPOSITORY_DATA)),
)
SECOND_WEAPON_SYNTHETIC = (SPEAR_RIG, SPEAR_ANIMATIONS)


def synthetic_rig(style: dict, template: dict) -> dict:
    """A first-person rig for every move of ``style``: the template's arm and neutral pose, three
    keys per move, and a strike window just around the active ticks."""
    rig = {"rig": template["rig"], "neutral": template["neutral"], "moves": {}}
    pose = {field: value for field, value in template["neutral"].items()}
    for move in style["moves"]:
        start, end = move["active_ticks"]
        rig["moves"][move["id"]] = {
            "strike": [start - 0.25, end + 0.25],
            "contact": start,
            "keys": [{"tick": 0, "pose": "neutral"},
                     {"tick": start, "ease": "in_out", **pose, "reach": pose["reach"] + 0.05},
                     {"tick": move["total_ticks"], "ease": "in_out", "pose": "neutral"}],
        }
    return rig


def synthetic_animations(style: dict) -> dict:
    """A PAL file with a full-body animation for the style's mode-enter, ready-idle, and moves:
    each bone keyed at 0, inside the active window, and at the end."""
    def animation(total: int, middle: float, loop: bool = False) -> dict:
        frames = {f"{tick / 20:g}": [0, 0, 0] for tick in (0, middle, total)}
        document = {"animation_length": total / 20,
                    "bones": {bone: {"rotation": dict(frames)} for bone in validator.REQUIRED_ANIMATION_BONES}}
        if loop:
            document["loop"] = True
        return document

    name = lambda resource_id: validator.combat_data.split_id(resource_id)[1]
    animations = {name(style["animations"]["mode_enter"]): animation(16, 8),
                  name(style["animations"]["ready_idle"]): animation(24, 12, loop=True)}
    for move in style["moves"]:
        animations[name(move["id"])] = animation(move["total_ticks"], move["active_ticks"][0])
    return {"format_version": validator.PAL_FORMAT_VERSION, "animations": animations}


class SwordCombatFoundationValidatorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        for relative in FIXTURE:
            source = validator.ROOT / relative
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            if source.is_dir():
                shutil.copytree(source, target, dirs_exist_ok=True)
            else:
                shutil.copy2(source, target)
        # Rebuilt on every run, whatever the repository holds for them right now.
        spear_style = self.read_json(SPEAR_STYLE)
        self.write_json(SPEAR_RIG, synthetic_rig(spear_style, self.read_json(RIG)))
        self.write_json(SPEAR_ANIMATIONS, synthetic_animations(spear_style))

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    # --- helpers ----------------------------------------------------------------------------

    def findings(self, run_generators: bool = False) -> list[validator.Finding]:
        # The generator drift checks spawn Python; only the tests that need them run them.
        return validator.validate(self.root, run_generators)

    def codes(self, run_generators: bool = False) -> set[str]:
        return {finding.code for finding in self.findings(run_generators)}

    def details(self, code: str) -> list[str]:
        return [finding.detail for finding in self.findings() if finding.code == code]

    def read_json(self, relative: str):
        return json.loads((self.root / relative).read_text(encoding="utf-8"))

    def write_json(self, relative: str, document) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(document, ensure_ascii=False, indent=2), encoding="utf-8")

    def edit(self, relative: str, old: str, new: str, count: int = 1) -> None:
        path = self.root / relative
        content = path.read_text(encoding="utf-8")
        self.assertIn(old, content)
        path.write_text(content.replace(old, new, count), encoding="utf-8")

    def write_java(self, relative: str, source: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding="utf-8")

    def style_move(self, style: dict, move_id: str) -> dict:
        return next(move for move in style["moves"] if move["id"] == move_id)

    def regenerate_player_animations(self) -> None:
        """Replace the fixture's PAL files with the PAL generator's output, so its --check holds.

        The fixture's spear PAL file is synthetic until this runs.  A generator that cannot build
        every pose table fails the calling test with the generator's own message."""
        result = subprocess.run([sys.executable, PAL_GENERATOR], cwd=self.root, capture_output=True,
                                text=True, timeout=validator.GENERATOR_TIMEOUT_SECONDS, check=False)
        if result.returncode != 0:
            output = "\n".join(part.strip() for part in (result.stderr, result.stdout) if part.strip())
            self.fail(f"{PAL_GENERATOR} failed (exit {result.returncode}) on the fixture:\n{output}")

    def generator_details(self, code: str) -> list[str]:
        return [f.detail for f in self.findings(run_generators=True) if f.code == code]

    # --- whole repository -------------------------------------------------------------------

    def test_valid_repository_fixture_passes(self) -> None:
        data = validator.combat_data.load(self.root)
        self.assertEqual(["myvillage:qingfeng_sword", SPEAR_WEAPON_ID], list(data.weapons))
        self.assertEqual(["myvillage:basic_sword", "myvillage:basic_spear"], list(data.styles))
        self.assertTrue((self.root / ANIMATIONS).is_file() and (self.root / SPEAR_ANIMATIONS).is_file())
        self.assertEqual([], [str(f) for f in self.findings()])

    def test_weapon_model_generators_are_derived_from_the_contracts(self) -> None:
        checks = validator.generator_checks(self.root, validator.combat_data.load(self.root))
        self.assertIn((SPEAR_GENERATOR, validator.WEAPON_MODEL_GENERATOR_DRIFT), checks)
        self.assertIn(("tools/gen_qingfeng_sword_model.py", validator.WEAPON_MODEL_GENERATOR_DRIFT), checks)
        self.assertEqual(len(checks), len({script for script, _ in checks}))

    def test_generators_other_than_pal_pass_on_the_fixture(self) -> None:
        # The fixture's spear PAL file is synthetic, so only the PAL generator may disagree here.
        findings = [str(f) for f in self.findings(run_generators=True) if f.code != "COMBAT_PAL_GENERATOR_DRIFT"]
        self.assertEqual([], findings)

    def test_valid_repository_fixture_passes_with_generators(self) -> None:
        self.regenerate_player_animations()
        self.assertEqual([], [str(f) for f in self.findings(run_generators=True)])

    # --- PAL dependency ---------------------------------------------------------------------

    def test_missing_jar_has_named_failure(self) -> None:
        (self.root / validator.PAL_JAR_NAME).unlink()
        self.assertIn("PAL_JAR_MISSING", self.codes())

    def test_wrong_jar_hash_has_named_failure(self) -> None:
        (self.root / validator.PAL_JAR_NAME).write_bytes(b"not-pal")
        self.assertIn("PAL_JAR_SHA256", self.codes())

    def test_dependency_metadata_drift_has_named_failure(self) -> None:
        self.edit(f"{RESOURCES}/META-INF/neoforge.mods.toml",
                  'modId = "player_animation_library"', 'modId = "guessed_animation_library"')
        self.assertIn("PAL_DEPENDENCY_MOD_ID", self.codes())

    def test_shaded_pal_class_is_rejected(self) -> None:
        jar = self.root / "build/libs/myvillage-negative.jar"
        jar.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("com/zigythebird/playeranim/Fake.class", b"")
        self.assertIn("PAL_SHADED_CONTENT", self.codes())

    # --- combat data (spec: validator checks combat data files) -----------------------------

    def test_misspelled_field_names_the_file_and_field(self) -> None:
        style = self.read_json(STYLE)
        style["moves"][0]["chain_tik"] = style["moves"][0].pop("chain_tick")
        self.write_json(STYLE, style)
        details = self.details("COMBAT_DATA_UNKNOWN_FIELD")
        self.assertTrue(any(STYLE in d and "moves[0].chain_tik" in d for d in details), details)

    def test_unlisted_style_file_is_reported(self) -> None:
        self.write_json(f"{DATA}/style/spear.json", self.read_json(STYLE))
        details = self.details("COMBAT_DATA_INDEX")
        self.assertTrue(any("style/spear.json" in d and "not listed" in d for d in details), details)

    def test_index_entry_without_file_is_reported(self) -> None:
        (self.root / WEAPON).unlink()
        self.assertTrue(any("myvillage:qingfeng_sword" in d for d in self.details("COMBAT_DATA_INDEX")))

    def test_explicit_samples_out_of_tick_order_are_reported(self) -> None:
        style = self.read_json(STYLE)
        move = self.style_move(style, THRUST)
        start, end = move["active_ticks"]
        sample = {"start": [0, 1.2, 0.5], "end": [0, 1.2, 2.9], "horizontal_radius": 0.16, "vertical_radius": 0.16}
        move["hitbox"]["samples"] = [dict(sample, tick=t) for t in (start, end, start)]
        self.write_json(STYLE, style)
        details = self.details("COMBAT_DATA_SAMPLE_ORDER")
        self.assertTrue(any(STYLE in d and "moves[0].hitbox.samples[2].tick" in d and THRUST in d
                            for d in details), details)

    def test_timing_invariant_violation_is_reported(self) -> None:
        style = self.read_json(STYLE)
        self.style_move(style, HORIZONTAL)["chain_tick"] = 5
        self.write_json(STYLE, style)
        details = self.details("COMBAT_DATA_INVARIANT")
        self.assertTrue(any("moves[1].chain_tick" in d for d in details), details)

    # --- cross-file checks (spec: validator cross-checks data against resources) ------------

    def test_missing_animation_names_the_move(self) -> None:
        document = self.read_json(ANIMATIONS)
        del document["animations"]["basic_sword_03_rising_cut"]
        self.write_json(ANIMATIONS, document)
        details = self.details("COMBAT_ANIMATION_MISSING")
        self.assertTrue(any("myvillage:basic_sword_03_rising_cut" in d for d in details), details)

    def test_style_animation_missing_is_reported(self) -> None:
        document = self.read_json(ANIMATIONS)
        del document["animations"]["sword_mode_enter"]
        self.write_json(ANIMATIONS, document)
        details = self.details("COMBAT_ANIMATION_MISSING")
        self.assertTrue(any("mode_enter" in d for d in details), details)

    def test_attack_animation_length_drift_has_named_failure(self) -> None:
        document = self.read_json(ANIMATIONS)
        document["animations"]["basic_sword_01_thrust"]["animation_length"] = 0.75
        self.write_json(ANIMATIONS, document)
        details = self.details("COMBAT_ANIMATION_LENGTH")
        self.assertTrue(any(THRUST in d and "0.55" in d for d in details), details)

    def test_ready_idle_must_loop(self) -> None:
        document = self.read_json(ANIMATIONS)
        del document["animations"]["sword_ready_idle"]["loop"]
        self.write_json(ANIMATIONS, document)
        self.assertIn("COMBAT_READY_IDLE_LOOP", self.codes())

    def test_animation_without_full_body_has_named_failure(self) -> None:
        document = self.read_json(ANIMATIONS)
        del document["animations"]["basic_sword_04_diagonal_cut"]["bones"]["left_leg"]
        self.write_json(ANIMATIONS, document)
        self.assertIn("COMBAT_ANIMATION_FULL_BODY", self.codes())

    def test_retuned_move_passes_without_a_validator_edit(self) -> None:
        # Spec scenario: total_ticks changes consistently in the style, the rig, and the
        # (re)generated animation; the validator holds no per-move numbers, so it passes.
        style = self.read_json(STYLE)
        self.style_move(style, THRUST)["total_ticks"] = 12
        self.write_json(STYLE, style)
        rig = self.read_json(RIG)
        rig["moves"][THRUST]["keys"][-1]["tick"] = 12
        self.write_json(RIG, rig)
        document = self.read_json(ANIMATIONS)
        animation = document["animations"]["basic_sword_01_thrust"]
        animation["animation_length"] = 0.6
        for channels in animation["bones"].values():
            for name, frames in channels.items():
                channels[name] = {("0.6" if t == "0.55" else t): v for t, v in frames.items()}
        self.write_json(ANIMATIONS, document)
        self.assertEqual([], [str(f) for f in self.findings()])

    def test_strike_window_missing_the_active_ticks_reports_both_windows(self) -> None:
        rig = self.read_json(RIG)
        rig["moves"][HORIZONTAL]["strike"] = [3.3, 5.5]
        self.write_json(RIG, rig)
        details = self.details("COMBAT_FIRST_PERSON_RIG_STRIKE")
        self.assertTrue(any(HORIZONTAL in d and "[3.3, 5.5]" in d and "[4, 6]" in d for d in details), details)

    def test_strike_after_active_window_has_named_failure(self) -> None:
        rig = self.read_json(RIG)
        rig["moves"][HORIZONTAL]["strike"] = [4.5, 6.2]
        self.write_json(RIG, rig)
        self.assertIn("COMBAT_FIRST_PERSON_RIG_STRIKE", self.codes())

    def test_slow_strike_has_named_failure(self) -> None:
        rig = self.read_json(RIG)
        rig["moves"]["myvillage:basic_sword_04_diagonal_cut"]["strike"] = [2.0, 9.0]
        self.write_json(RIG, rig)
        self.assertIn("COMBAT_FIRST_PERSON_RIG_STRIKE", self.codes())

    def test_contact_outside_strike_has_named_failure(self) -> None:
        rig = self.read_json(RIG)
        rig["moves"][THRUST]["contact"] = 5.5
        self.write_json(RIG, rig)
        self.assertIn("COMBAT_FIRST_PERSON_RIG_CONTACT", self.codes())

    def test_rig_unknown_or_missing_move_has_named_failure(self) -> None:
        rig = self.read_json(RIG)
        rig["moves"]["myvillage:basic_sword_06_extra"] = rig["moves"].pop(THRUST)
        self.write_json(RIG, rig)
        details = self.details("COMBAT_FIRST_PERSON_RIG_MOVES")
        self.assertTrue(any(THRUST in d and "basic_sword_06_extra" in d for d in details), details)

    def test_rig_not_ending_neutral_has_named_failure(self) -> None:
        rig = self.read_json(RIG)
        rig["moves"]["myvillage:basic_sword_03_rising_cut"]["keys"][-1].pop("pose")
        self.write_json(RIG, rig)
        self.assertIn("COMBAT_FIRST_PERSON_RIG_NEUTRAL", self.codes())

    def test_rig_keys_must_span_the_style_total(self) -> None:
        rig = self.read_json(RIG)
        rig["moves"][THRUST]["keys"][-1]["tick"] = 10
        self.write_json(RIG, rig)
        self.assertIn("COMBAT_FIRST_PERSON_RIG_KEYS", self.codes())

    def test_rig_missing_has_named_failure(self) -> None:
        (self.root / RIG).unlink()
        self.assertIn("COMBAT_FIRST_PERSON_RIG_MISSING", self.codes())

    def test_rig_arm_nonsense_has_named_failure(self) -> None:
        rig = self.read_json(RIG)
        rig["rig"]["arm"]["thickness"] = 3.0
        self.write_json(RIG, rig)
        self.assertIn("COMBAT_FIRST_PERSON_RIG_ARM", self.codes())
        rig["rig"]["arm"]["thickness"] = 0.5
        rig["neutral"]["grip_roll"] = "palm up"
        self.write_json(RIG, rig)
        self.assertIn("COMBAT_FIRST_PERSON_RIG_ARM", self.codes())

    def test_missing_display_translation_has_named_failure(self) -> None:
        language = self.read_json(EN_US)
        del language["combat.myvillage.move.basic_sword_05_lunge_thrust"]
        self.write_json(EN_US, language)
        details = self.details("COMBAT_TRANSLATIONS")
        self.assertTrue(any("en_us.json" in d and "basic_sword_05_lunge_thrust" in d for d in details), details)

    def test_missing_sound_event_has_named_failure(self) -> None:
        sounds = self.read_json(SOUNDS)
        del sounds["combat.sword.hit"]
        self.write_json(SOUNDS, sounds)
        details = self.details("COMBAT_SOUND_EVENT")
        self.assertTrue(any("myvillage:combat.sword.hit" in d for d in details), details)

    def test_sound_event_without_subtitle_has_named_failure(self) -> None:
        sounds = self.read_json(SOUNDS)
        del sounds["combat.sword.impact_heavy"]["subtitle"]
        self.write_json(SOUNDS, sounds)
        self.assertIn("COMBAT_SOUND_EVENT", self.codes())

    def test_style_sound_id_must_resolve(self) -> None:
        style = self.read_json(STYLE)
        self.style_move(style, THRUST)["feedback"]["swing_sound"] = "myvillage:combat.sword.whoosh"
        self.write_json(STYLE, style)
        self.assertTrue(any("combat.sword.whoosh" in d for d in self.details("COMBAT_SOUND_EVENT")))

    def test_weapon_item_must_be_registered_with_a_model(self) -> None:
        weapon = self.read_json(WEAPON)
        weapon["item"] = "myvillage:unknown_blade"
        self.write_json(WEAPON, weapon)
        codes = self.codes()
        self.assertIn("COMBAT_WEAPON_ITEM_UNREGISTERED", codes)
        self.assertIn("COMBAT_WEAPON_ITEM_MODEL", codes)

    def test_geometry_contract_drift_has_named_failure(self) -> None:
        geometry = self.read_json(GEOMETRY)
        geometry["grip_center"][1] = geometry["guard"]["y"][1]  # grip outside the handle
        self.write_json(GEOMETRY, geometry)
        self.assertIn("COMBAT_GEOMETRY_CONTRACT", self.codes())
        del geometry["blade_tip"]
        self.write_json(GEOMETRY, geometry)
        self.assertTrue(any("blade_tip" in d for d in self.details("COMBAT_GEOMETRY_CONTRACT")))

    # --- second weapon (every weapon in the index gets the same checks) ----------------------

    def assert_finding(self, code: str, *needles: str) -> None:
        details = self.details(code)
        self.assertTrue(any(all(needle in d for needle in needles) for d in details), (code, needles, details))

    def test_second_weapon_animation_missing_names_its_move(self) -> None:
        document = self.read_json(SPEAR_ANIMATIONS)
        del document["animations"]["basic_spear_02_sweep"]
        self.write_json(SPEAR_ANIMATIONS, document)
        self.assert_finding("COMBAT_ANIMATION_MISSING", "myvillage:basic_spear", "basic_spear_02_sweep")

    def test_second_weapon_item_model_missing_has_named_failure(self) -> None:
        (self.root / SPEAR_MODEL).unlink()
        self.assert_finding("COMBAT_WEAPON_ITEM_MODEL", SPEAR_WEAPON_ID, "lingxiao_spear.json")

    def test_second_weapon_3d_model_missing_has_named_failure(self) -> None:
        (self.root / SPEAR_MODEL_3D).unlink()
        self.assert_finding("COMBAT_WEAPON_MODEL_MISSING", SPEAR_WEAPON_ID, "myvillage:item/lingxiao_spear_3d")

    def test_second_weapon_3d_model_invalid_json_has_named_failure(self) -> None:
        (self.root / SPEAR_MODEL_3D).write_text("{", encoding="utf-8")
        self.assert_finding("COMBAT_WEAPON_MODEL_MISSING", SPEAR_WEAPON_ID, "lingxiao_spear_3d.json")

    def test_second_weapon_model_texture_missing_has_named_failure(self) -> None:
        (self.root / SPEAR_MODEL_TEXTURE).unlink()
        self.assert_finding("COMBAT_WEAPON_TEXTURE_MISSING", SPEAR_WEAPON_ID, "myvillage:item/lingxiao_spear_model")

    def test_second_weapon_icon_texture_missing_has_named_failure(self) -> None:
        (self.root / SPEAR_TEXTURE).unlink()
        self.assert_finding("COMBAT_WEAPON_TEXTURE_MISSING", SPEAR_WEAPON_ID, "lingxiao_spear.png")

    def test_second_weapon_texture_that_is_not_png_has_named_failure(self) -> None:
        (self.root / SPEAR_MODEL_TEXTURE).write_bytes(b"not a png")
        self.assert_finding("COMBAT_WEAPON_TEXTURE_MISSING", SPEAR_WEAPON_ID, "not a PNG")

    def test_second_weapon_flat_item_model_has_named_failure(self) -> None:
        self.write_json(SPEAR_MODEL, {"parent": "minecraft:item/handheld",
                                      "textures": {"layer0": "myvillage:item/lingxiao_spear"}})
        self.assert_finding("COMBAT_WEAPON_MODEL_3D", SPEAR_WEAPON_ID, "myvillage:item/lingxiao_spear_3d")

    def test_second_weapon_model_without_geometry_or_hand_display_has_named_failure(self) -> None:
        model = self.read_json(SPEAR_MODEL_3D)
        del model["display"]["firstperson_righthand"]
        self.write_json(SPEAR_MODEL_3D, model)
        self.assert_finding("COMBAT_WEAPON_MODEL_3D", SPEAR_WEAPON_ID, "firstperson_righthand")
        model = self.read_json(SPEAR_MODEL_3D)
        model["elements"] = []
        self.write_json(SPEAR_MODEL_3D, model)
        self.assert_finding("COMBAT_WEAPON_MODEL_3D", SPEAR_WEAPON_ID, "elements")

    def test_contract_naming_another_model_has_named_failure(self) -> None:
        geometry = self.read_json(SPEAR_GEOMETRY)
        geometry["model"] = "myvillage:item/qingfeng_sword_3d"
        self.write_json(SPEAR_GEOMETRY, geometry)
        self.assert_finding("COMBAT_WEAPON_MODEL_3D", SPEAR_WEAPON_ID, "does not draw myvillage:item/qingfeng_sword_3d")

    def test_second_weapon_rig_missing_has_named_failure(self) -> None:
        (self.root / SPEAR_RIG).unlink()
        self.assert_finding("COMBAT_FIRST_PERSON_RIG_MISSING", "lingxiao_spear_first_person.json")

    def test_second_weapon_rig_checked_against_its_own_style(self) -> None:
        rig = self.read_json(SPEAR_RIG)
        del rig["moves"]["myvillage:basic_spear_05_dragon_lunge"]
        rig["moves"][THRUST] = self.read_json(RIG)["moves"][THRUST]
        self.write_json(SPEAR_RIG, rig)
        self.assert_finding("COMBAT_FIRST_PERSON_RIG_MOVES", "lingxiao_spear_first_person.json",
                            "myvillage:basic_spear", "basic_spear_05_dragon_lunge", THRUST)
        rig = self.read_json(SPEAR_RIG)
        rig["moves"]["myvillage:basic_spear_02_sweep"]["strike"] = [1.0, 3.0]
        self.write_json(SPEAR_RIG, rig)
        self.assert_finding("COMBAT_FIRST_PERSON_RIG_STRIKE", "myvillage:basic_spear_02_sweep", "[5, 7]")

    def test_off_hand_rig_on_a_one_handed_weapon_has_named_failure(self) -> None:
        rig = self.read_json(RIG)
        rig["rig"]["off_hand"] = {}
        self.write_json(RIG, rig)
        self.assert_finding("COMBAT_FIRST_PERSON_RIG_OFF_HAND", "myvillage:qingfeng_sword", "off_hand_grip_center")
        rig = self.read_json(SPEAR_RIG)
        rig["rig"]["off_hand"] = {}
        self.write_json(SPEAR_RIG, rig)
        self.assertFalse(any(SPEAR_WEAPON_ID in d for d in self.details("COMBAT_FIRST_PERSON_RIG_OFF_HAND")))

    def test_off_hand_block_content_has_named_failure(self) -> None:
        good = self.read_json(SPEAR_RIG)
        good["rig"]["off_hand"] = {"shoulder_offset": [0.03, -0.02, -0.12], "grip_diagonal": 30,
                                   "unknown_field": 1}  # unknown fields are ignored, as in Java
        good["neutral"]["off_hand_slide"] = -3
        keys = next(iter(good["moves"].values()))["keys"]
        keys[1].update({"off_hand_slide": -6, "off_hand_roll": 20, "off_hand_elbow": 10, "off_hand_hold": 0.5})
        self.write_json(SPEAR_RIG, good)
        self.assertEqual([], self.details("COMBAT_FIRST_PERSON_RIG_OFF_HAND"))
        cases = {
            "shoulder_offset": lambda rig: rig["rig"]["off_hand"].update(shoulder_offset=[0.1, 0.2]),
            "grip_diagonal": lambda rig: rig["rig"]["off_hand"].update(grip_diagonal=70),
            "must be an object": lambda rig: rig["rig"].update(off_hand=[]),
            "off_hand_hold 1.5": lambda rig: next(iter(rig["moves"].values()))["keys"][1].update(off_hand_hold=1.5),
            "off_hand_roll must be numbers":
                lambda rig: next(iter(rig["moves"].values()))["keys"][1].update(off_hand_roll="up"),
            "off the handle": lambda rig: next(iter(rig["moves"].values()))["keys"][1].update(off_hand_slide=9),
            "neutral: off_hand_slide": lambda rig: rig["neutral"].update(off_hand_slide=-30),
        }
        for needle, change in cases.items():
            rig = json.loads(json.dumps(good))
            change(rig)
            self.write_json(SPEAR_RIG, rig)
            self.assert_finding("COMBAT_FIRST_PERSON_RIG_OFF_HAND", SPEAR_WEAPON_ID, needle)
        # A key without the field inherits the slide of the key before it.
        rig = json.loads(json.dumps(good))
        keys = next(iter(rig["moves"].values()))["keys"]
        keys[1]["off_hand_slide"] = 9
        keys.insert(2, {key: value for key, value in keys[1].items() if key != "off_hand_slide"})
        keys[2]["tick"] = (keys[1]["tick"] + keys[3]["tick"]) / 2
        self.write_json(SPEAR_RIG, rig)
        self.assert_finding("COMBAT_FIRST_PERSON_RIG_OFF_HAND", "key 2: off_hand_slide 9")

    def test_second_weapon_contract_missing_has_named_failure(self) -> None:
        (self.root / SPEAR_GEOMETRY).unlink()
        self.assert_finding("COMBAT_GEOMETRY_CONTRACT", "lingxiao_spear_geometry.json")

    def test_second_weapon_contract_order_has_named_failure(self) -> None:
        geometry = self.read_json(SPEAR_GEOMETRY)
        geometry["blade_tip"][1] = geometry["blade_base"][1] - 1.0
        self.write_json(SPEAR_GEOMETRY, geometry)
        self.assert_finding("COMBAT_GEOMETRY_CONTRACT", "lingxiao_spear_geometry.json", "pommel<handle<guard<blade")

    def test_bad_off_hand_grip_has_named_failure(self) -> None:
        original = self.read_json(SPEAR_GEOMETRY)
        grip_y = original["grip_center"][1]
        handle = original["handle"]["y"]
        cases = {
            "off the weapon axis": [8.5, original["off_hand_grip_center"][1], 8.0],
            "outside the handle": [8.0, handle[1] + 1.0, 8.0],
            "ahead of grip_center": [8.0, grip_y - 3.0, 8.0],
            "two fists need": [8.0, grip_y + 2.0, 8.0],
            "must be [x, y, z]": [8.0, 11.0],
        }
        for needle, point in cases.items():
            geometry = json.loads(json.dumps(original))
            geometry["off_hand_grip_center"] = point
            self.write_json(SPEAR_GEOMETRY, geometry)
            self.assert_finding("COMBAT_GEOMETRY_OFF_HAND_GRIP", "lingxiao_spear_geometry.json", needle)

    def test_trail_span_is_optional_and_checked(self) -> None:
        self.assertEqual([], self.details("COMBAT_GEOMETRY_TRAIL"))
        self.assertNotIn("trail", self.read_json(GEOMETRY), "the sword trails along its blade")
        original = self.read_json(SPEAR_GEOMETRY)
        self.assertIn("trail", original)
        cases = {
            "off the weapon axis": {"base": [8.5, 16.0, 8.0], "tip": [8.0, 32.0, 8.0]},
            "below its tip": {"base": [8.0, 32.0, 8.0], "tip": [8.0, 16.0, 8.0]},
            "must lie on the weapon": {"base": [8.0, 16.0, 8.0], "tip": [8.0, 33.0, 8.0]},
            "must be [x, y, z]": {"base": [8.0, 16.0], "tip": [8.0, 32.0, 8.0]},
            "exactly base and tip": {"base": [8.0, 16.0, 8.0]},
        }
        for needle, trail in cases.items():
            geometry = json.loads(json.dumps(original))
            geometry["trail"] = trail
            self.write_json(SPEAR_GEOMETRY, geometry)
            self.assert_finding("COMBAT_GEOMETRY_TRAIL", "lingxiao_spear_geometry.json", needle)
        geometry = json.loads(json.dumps(original))
        geometry["trail"] = {"base": [8.0, -16.0, 8.0], "tip": [8.0, 32.0, 8.0]}
        self.write_json(SPEAR_GEOMETRY, geometry)
        self.assertEqual([], self.details("COMBAT_GEOMETRY_TRAIL"))

    def test_fist_gap_follows_the_model_third_person_scale(self) -> None:
        # 4 px fists at the player scale 0.9375: the gap is 3.75 px / scale in contract pixels.
        geometry = self.read_json(SPEAR_GEOMETRY)
        geometry["off_hand_grip_center"][1] = geometry["grip_center"][1] + 4.0
        self.write_json(SPEAR_GEOMETRY, geometry)
        model = self.read_json(SPEAR_MODEL_3D)
        model["display"]["thirdperson_righthand"]["scale"] = [1.0, 1.0, 1.0]
        self.write_json(SPEAR_MODEL_3D, model)
        self.assertEqual([], self.details("COMBAT_GEOMETRY_OFF_HAND_GRIP"))
        model["display"]["thirdperson_righthand"]["scale"] = [0.9, 0.9, 0.9]
        self.write_json(SPEAR_MODEL_3D, model)
        self.assert_finding("COMBAT_GEOMETRY_OFF_HAND_GRIP", "4.17 px")

    def test_one_handed_contract_needs_no_off_hand_grip(self) -> None:
        self.assertNotIn("off_hand_grip_center", self.read_json(GEOMETRY))
        geometry = self.read_json(SPEAR_GEOMETRY)
        del geometry["off_hand_grip_center"]
        self.write_json(SPEAR_GEOMETRY, geometry)
        self.assertEqual([], [str(f) for f in self.findings()])

    # --- world trail rules (CombatWorldTrails) ----------------------------------------------

    def spear_cut(self, style: dict) -> dict:
        return next(m for m in style["moves"] if m["kind"] == "cut" and isinstance(m["hitbox"]["samples"], list))

    def trail_reach(self) -> list[str]:
        return self.details("COMBAT_TRAIL_CUT_REACH")

    def test_uneven_samples_per_tick_surface_in_the_validator(self) -> None:
        style = self.read_json(SPEAR_STYLE)
        move = self.spear_cut(style)
        end = move["active_ticks"][1]
        last = max(i for i, s in enumerate(move["hitbox"]["samples"]) if s["tick"] == end)
        del move["hitbox"]["samples"][last]
        self.write_json(SPEAR_STYLE, style)
        self.assert_finding("COMBAT_DATA_SAMPLE_COUNT", SPEAR_STYLE, move["id"], "the same number")

    def test_trail_tip_radius_follows_the_java_trail_size(self) -> None:
        contracts = validator.read_geometry_contracts(self.root, validator.combat_data.load(self.root))
        sword = contracts["myvillage:combat/qingfeng_sword_geometry.json"]
        # CombatWorldTrailsTest pins the jian at 1.7 (0.705 + 19.9 px * 0.8 / 16).
        self.assertAlmostEqual(1.7, validator.trail_tip_radius(self.root, sword), places=6)
        spear = contracts["myvillage:combat/lingxiao_spear_geometry.json"]
        model = self.read_json(SPEAR_MODEL_3D)
        tip = spear["trail"]["tip"] if "trail" in spear else spear["blade_tip"]
        expected = 0.705 + abs(tip[1] - spear["grip_center"][1]) * model["display"]["thirdperson_righthand"]["scale"][1] / 16
        self.assertAlmostEqual(expected, validator.trail_tip_radius(self.root, spear), places=6)

    def test_committed_cuts_keep_the_trail_head_on_the_tip_radius(self) -> None:
        # Sword cuts come from generators and are checked too; the accepted sword passes.
        self.assertEqual([], self.trail_reach())

    def test_short_cut_far_end_has_named_failure(self) -> None:
        style = self.read_json(SPEAR_STYLE)
        move = self.spear_cut(style)
        sample = move["hitbox"]["samples"][1]
        sample["end"] = [value * 0.5 for value in sample["end"]]  # pulled toward the feet and the pivot
        self.write_json(SPEAR_STYLE, style)
        self.assert_finding("COMBAT_TRAIL_CUT_REACH", SPEAR_WEAPON_ID, move["id"], "explicit samples[1]",
                            "at least")

    def test_cut_far_ends_that_dip_between_samples_have_named_failure(self) -> None:
        # Every sample reaches the radius, but the trail interpolates the horizontal radius and the
        # height linearly, so alternating level and overhead far ends cut the corner between them.
        style = self.read_json(SPEAR_STYLE)
        move = self.spear_cut(style)
        contracts = validator.read_geometry_contracts(self.root, validator.combat_data.load(self.root))
        reach = validator.trail_tip_radius(self.root, contracts["myvillage:combat/lingxiao_spear_geometry.json"]) + 0.05
        for index, sample in enumerate(move["hitbox"]["samples"]):
            sample["end"] = [0.0, validator.TRAIL_PIVOT_HEIGHT, reach] if index % 2 == 0 else \
                [0.0, validator.TRAIL_PIVOT_HEIGHT + reach, 0.0]
        self.write_json(SPEAR_STYLE, style)
        self.assert_finding("COMBAT_TRAIL_CUT_REACH", SPEAR_WEAPON_ID, move["id"], "between explicit samples")

    def test_generated_sword_cut_inside_the_radius_has_named_failure(self) -> None:
        style = self.read_json(STYLE)
        move = self.style_move(style, HORIZONTAL)
        move["hitbox"]["samples"]["range"] = 1.5
        self.write_json(STYLE, style)
        self.assert_finding("COMBAT_TRAIL_CUT_REACH", "myvillage:qingfeng_sword", HORIZONTAL, "arc generator")

    def test_thrusts_are_not_held_to_the_tip_radius(self) -> None:
        style = self.read_json(STYLE)
        self.style_move(style, THRUST)["hitbox"]["samples"].update(first_range=1.0, final_range=1.2)
        self.write_json(STYLE, style)
        self.assertEqual([], self.trail_reach())

    def test_trail_reach_is_checked_per_weapon(self) -> None:
        # The sword's rising cut (diagonal generator, far ends from about 2.60) clears the jian's 1.7
        # but not the spear's longer radius: a spear sharing the sword style fails for the spear only.
        weapon = self.read_json(SPEAR_WEAPON)
        weapon["style"] = "myvillage:basic_sword"
        self.write_json(SPEAR_WEAPON, weapon)
        details = self.trail_reach()
        self.assertTrue(any(d.startswith(SPEAR_WEAPON_ID) and "basic_sword_03_rising_cut" in d for d in details),
                        details)
        self.assertFalse(any(d.startswith("myvillage:qingfeng_sword") for d in details), details)

    # --- Java source invariants -------------------------------------------------------------

    def test_pal_import_outside_client_combat_is_rejected(self) -> None:
        self.write_java(COMBAT_JAVA + "BadCommonImport.java",
                        "package com.example.myvillage.combat;\n"
                        "import com.zigythebird.playeranim.api.PlayerAnimationAccess;\n")
        self.assertIn("PAL_IMPORT_OUTSIDE_CLIENT_COMBAT", self.codes())

    def test_client_import_in_common_combat_is_rejected(self) -> None:
        self.write_java(COMBAT_JAVA + "runtime/BadClientImport.java",
                        "package com.example.myvillage.combat.runtime;\n"
                        "import net.minecraft.client.Minecraft;\n")
        self.assertIn("CLIENT_IMPORT_IN_COMMON_COMBAT", self.codes())

    def test_serverbound_payload_with_fields_is_rejected_whatever_its_name(self) -> None:
        self.write_java(COMBAT_JAVA + "network/ProbeIntentPayload.java",
                        "package com.example.myvillage.combat.network;\n"
                        "public record ProbeIntentPayload(int moveId) implements CustomPacketPayload {\n"
                        "    static void register(PayloadRegistrar registrar) {\n"
                        "        registrar.playToServer(ProbeIntentPayload.TYPE, null, null);\n"
                        "    }\n}\n")
        details = self.details("COMBAT_C2S_AUTHORITY_FIELD")
        self.assertTrue(any("ProbeIntentPayload(int moveId)" in d for d in details), details)

    def test_serverbound_registrations_are_required(self) -> None:
        for path in (self.root / COMBAT_JAVA).rglob("*.java"):
            content = path.read_text(encoding="utf-8")
            path.write_text(re.sub(r"\bplayToServer\b", "playToClient", content), encoding="utf-8")
        self.assertIn("COMBAT_C2S_PAYLOADS_MISSING", self.codes())

    def test_named_item_in_combat_code_is_rejected(self) -> None:
        self.write_java(COMBAT_JAVA + "runtime/ProbeItem.java",
                        "package com.example.myvillage.combat.runtime;\n"
                        "import com.example.myvillage.item.ModItems;\n"
                        "final class ProbeItem {\n"
                        "    static boolean armed(ItemStack stack) { return stack.is(ModItems.QINGFENG_SWORD.get()); }\n}\n")
        self.assertIn("COMBAT_NAMED_ITEM", self.codes())

    def test_vanilla_attack_in_combat_code_is_rejected(self) -> None:
        self.write_java(COMBAT_JAVA + "runtime/ProbeAttack.java",
                        "package com.example.myvillage.combat.runtime;\n"
                        "final class ProbeAttack {\n"
                        "    static void hit(ServerPlayer player, Entity target) { player.attack(target); }\n}\n")
        self.assertIn("COMBAT_VANILLA_ATTACK", self.codes())

    def test_client_vanilla_attack_packet_is_rejected(self) -> None:
        self.write_java(CLIENT_COMBAT_JAVA + "ProbeAttack.java",
                        "package com.example.myvillage.client.combat;\n"
                        "final class ProbeAttack {\n"
                        "    static void hit(Minecraft mc, Entity target) { mc.gameMode.attack(mc.player, target); }\n}\n")
        self.assertIn("COMBAT_VANILLA_ATTACK", self.codes())

    def test_impact_payload_registered_serverbound_is_rejected(self) -> None:
        self.write_java(COMBAT_JAVA + "network/ProbeRegistration.java",
                        "package com.example.myvillage.combat.network;\n"
                        "final class ProbeRegistration {\n"
                        "    static void register(PayloadRegistrar registrar) {\n"
                        "        registrar.playToServer(\n                CombatImpactPayload.TYPE, null, null);\n"
                        "    }\n}\n")
        self.assertIn("COMBAT_IMPACT_S2C_ONLY", self.codes())

    def test_impact_payload_damage_field_is_rejected(self) -> None:
        self.write_java(COMBAT_JAVA + "network/ProbeImpactPayload.java",
                        "package com.example.myvillage.combat.network;\n"
                        "public record ProbeImpactPayload(\n        int attackerEntityId,\n"
                        "        float damage) implements CustomPacketPayload {\n"
                        "    static void register(PayloadRegistrar registrar) {\n"
                        "        registrar.playToClient(ProbeImpactPayload.TYPE, null, null);\n"
                        "    }\n}\n")
        self.assertIn("COMBAT_IMPACT_AUTHORITY_FIELD", self.codes())

    def test_clientbound_health_field_is_rejected(self) -> None:
        self.write_java(COMBAT_JAVA + "network/ProbeStatusPayload.java",
                        "package com.example.myvillage.combat.network;\n"
                        "public record ProbeStatusPayload(int entityId, float targetHealth) implements CustomPacketPayload {\n"
                        "    static void register(PayloadRegistrar registrar) {\n"
                        "        registrar.playToClient(ProbeStatusPayload.TYPE, null, null);\n"
                        "    }\n}\n")
        self.assertIn("COMBAT_PAYLOAD_AUTHORITY_FIELD", self.codes())

    def test_combat_state_in_cultivation_profile_is_rejected(self) -> None:
        self.write_java("src/main/java/com/example/myvillage/cultivation/CultivationProfile.java",
                        "record CultivationProfile(int comboIndex) {}\n")
        self.assertIn("COMBAT_STATE_IN_CULTIVATION_PROFILE", self.codes())

    def test_third_party_rig_import_is_rejected(self) -> None:
        self.write_java(CLIENT_COMBAT_JAVA + "ProbeRig.java",
                        "package com.example.myvillage.client.combat;\n"
                        "import yesman.epicfight.api.Placeholder;\n")
        self.assertIn("COMBAT_FORBIDDEN_IMPORT", self.codes())
        self.write_java(CLIENT_COMBAT_JAVA + "ProbeRig.java",
                        "package com.example.myvillage.client.combat;\n"
                        "import software.bernie.geckolib.Placeholder;\n")
        self.assertIn("COMBAT_FIRST_PERSON_THIRD_PARTY_RIG_FORBIDDEN", self.codes())

    def test_client_combat_reads_the_game_clock_only_in_the_combat_clock(self) -> None:
        self.assertNotIn("COMBAT_CLIENT_GAME_CLOCK_READ", self.codes())
        self.write_java(CLIENT_COMBAT_JAVA + "ProbeTimer.java",
                        "package com.example.myvillage.client.combat;\n"
                        "final class ProbeTimer {\n"
                        "    // level.getGameTime() in a comment is fine\n"
                        "    static double now(net.minecraft.world.level.Level level, float partial) {\n"
                        "        return level.getGameTime() + partial;\n    }\n}\n")
        self.assertIn("COMBAT_CLIENT_GAME_CLOCK_READ", self.codes())
        self.write_java(CLIENT_COMBAT_JAVA + "ProbeTimer.java",
                        "package com.example.myvillage.client.combat;\n"
                        "final class ProbeTimer {\n"
                        "    /* level.getGameTime() */ static float partial(net.minecraft.client.DeltaTracker t) {\n"
                        "        return t.getGameTimeDeltaPartialTick(true);\n    }\n}\n")
        self.assertNotIn("COMBAT_CLIENT_GAME_CLOCK_READ", self.codes())

    # --- Qingfeng item and presentation assets ----------------------------------------------

    def test_qingfeng_attribute_drift_has_named_failure(self) -> None:
        self.edit("src/main/java/com/example/myvillage/item/ModItems.java",
                  "SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)",
                  "SwordItem.createAttributes(Tiers.DIAMOND, 4, -2.4F)", count=-1)
        self.assertIn("QINGFENG_ATTRIBUTES", self.codes())

    def test_qingfeng_tier_drift_has_named_failure(self) -> None:
        self.edit("src/main/java/com/example/myvillage/item/ModItems.java",
                  "new CombatWeaponItem(\n                            Tiers.DIAMOND,",
                  "new CombatWeaponItem(\n                            Tiers.NETHERITE,")
        self.assertIn("QINGFENG_DIAMOND_TIER", self.codes())

    def test_qingfeng_as_plain_sword_item_has_named_failure(self) -> None:
        self.edit("src/main/java/com/example/myvillage/item/ModItems.java",
                  'ITEMS.registerItem("qingfeng_sword",\n                    props -> new CombatWeaponItem(',
                  'ITEMS.registerItem("qingfeng_sword",\n                    props -> new SwordItem(')
        self.assertIn("QINGFENG_COMBAT_WEAPON_ITEM", self.codes())

    def test_qingfeng_flat_handheld_model_has_named_failure(self) -> None:
        self.write_json(validator.QINGFENG_MODEL, {"parent": "minecraft:item/handheld",
                                                   "textures": {"layer0": "myvillage:item/qingfeng_sword"}})
        self.assertIn("QINGFENG_MODEL_CONTRACT", self.codes())

    def test_qingfeng_3d_model_missing_has_named_failure(self) -> None:
        (self.root / validator.QINGFENG_MODEL_3D).unlink()
        self.assertIn("QINGFENG_MODEL_3D", self.codes())

    def test_qingfeng_3d_model_with_gui_display_has_named_failure(self) -> None:
        model = self.read_json(validator.QINGFENG_MODEL_3D)
        model["display"]["gui"] = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}
        self.write_json(validator.QINGFENG_MODEL_3D, model)
        self.assertIn("QINGFENG_MODEL_3D", self.codes())

    def test_blade_cut_particle_json_missing_has_named_failure(self) -> None:
        (self.root / validator.BLADE_CUT_PARTICLE).unlink()
        self.assertIn("COMBAT_BLADE_CUT_PARTICLE_JSON", self.codes())

    # --- generated assets -------------------------------------------------------------------

    def test_hand_edited_pal_animation_has_generator_drift(self) -> None:
        self.regenerate_player_animations()
        for animations in (ANIMATIONS, SPEAR_ANIMATIONS):
            path = self.root / animations
            original = path.read_text(encoding="utf-8")
            path.write_text(original + "\n", encoding="utf-8")
            drift = self.generator_details("COMBAT_PAL_GENERATOR_DRIFT")
            self.assertTrue(any(Path(animations).name in d for d in drift), drift)
            path.write_text(original, encoding="utf-8")

    def test_style_without_poses_fails_the_generator_check(self) -> None:
        self.regenerate_player_animations()
        style = self.read_json(STYLE)
        extra = json.loads(json.dumps(style["moves"][0]))
        extra["id"] = "myvillage:basic_sword_06_extra"
        extra["display_key"] = "combat.myvillage.move.basic_sword_06_extra"
        style["moves"].append(extra)
        self.write_json(STYLE, style)
        drift = [f.detail for f in self.findings(run_generators=True) if f.code == "COMBAT_PAL_GENERATOR_DRIFT"]
        self.assertTrue(any("basic_sword_06_extra" in d for d in drift), drift)

    def test_hand_edited_blade_cut_sprite_has_generator_drift(self) -> None:
        path = self.root / validator.BLADE_CUT_TEXTURE
        path.write_bytes(path.read_bytes() + b"\0")
        self.assertIn("COMBAT_BLADE_CUT_SPRITE_DRIFT", self.codes(run_generators=True))

    def test_hand_edited_sword_model_has_generator_drift(self) -> None:
        self.edit(validator.QINGFENG_MODEL_3D, '"scale": [0.8, 0.8, 0.8]', '"scale": [0.85, 0.85, 0.85]')
        drift = self.generator_details(validator.WEAPON_MODEL_GENERATOR_DRIFT)
        self.assertTrue(any("gen_qingfeng_sword_model.py" in d for d in drift), drift)

    def test_qingfeng_model_generator_runs_without_its_contract_field(self) -> None:
        # Pinned: the accepted Qingfeng model stays checked even if its contract stops naming it.
        geometry = self.read_json(GEOMETRY)
        geometry["generator"] = SPEAR_GENERATOR
        self.write_json(GEOMETRY, geometry)
        self.edit(validator.QINGFENG_MODEL_3D, '"scale": [0.8, 0.8, 0.8]', '"scale": [0.85, 0.85, 0.85]')
        drift = self.generator_details(validator.WEAPON_MODEL_GENERATOR_DRIFT)
        self.assertTrue(any("gen_qingfeng_sword_model.py" in d for d in drift), drift)

    def test_hand_edited_second_weapon_model_has_generator_drift(self) -> None:
        model = self.read_json(SPEAR_MODEL_3D)
        model["display"]["thirdperson_righthand"]["scale"] = [0.95, 0.95, 0.95]
        self.write_json(SPEAR_MODEL_3D, model)
        drift = self.generator_details(validator.WEAPON_MODEL_GENERATOR_DRIFT)
        self.assertTrue(any(SPEAR_GENERATOR in d and "lingxiao_spear_3d.json" in d for d in drift), drift)

    def test_hand_edited_second_weapon_texture_has_generator_drift(self) -> None:
        path = self.root / SPEAR_MODEL_TEXTURE
        original = path.read_bytes()
        shutil.copy2(self.root / validator.QINGFENG_MODEL_TEXTURE, path)  # a valid PNG, wrong pixels
        self.assertNotEqual(original, path.read_bytes())
        self.assertEqual([], self.details("COMBAT_WEAPON_TEXTURE_MISSING"))
        drift = self.generator_details(validator.WEAPON_MODEL_GENERATOR_DRIFT)
        self.assertTrue(any(SPEAR_GENERATOR in d and "lingxiao_spear_model.png" in d for d in drift), drift)

    def test_hand_edited_second_weapon_contract_has_generator_drift(self) -> None:
        geometry = self.read_json(SPEAR_GEOMETRY)
        geometry["off_hand_grip_center"][1] += 1.0  # still a valid off-hand grip
        self.write_json(SPEAR_GEOMETRY, geometry)
        self.assertEqual([], self.details("COMBAT_GEOMETRY_OFF_HAND_GRIP"))
        drift = self.generator_details(validator.WEAPON_MODEL_GENERATOR_DRIFT)
        self.assertTrue(any(SPEAR_GENERATOR in d and "lingxiao_spear_geometry.json" in d for d in drift), drift)

    def test_contract_generator_that_does_not_exist_has_named_failure(self) -> None:
        geometry = self.read_json(SPEAR_GEOMETRY)
        geometry["generator"] = "tools/gen_missing_spear_model.py"
        self.write_json(SPEAR_GEOMETRY, geometry)
        missing = [f.detail for f in self.findings(run_generators=True) if f.code == "COMBAT_GENERATOR_MISSING"]
        self.assertTrue(any("gen_missing_spear_model.py" in d for d in missing), missing)

    def test_contract_without_a_generator_has_named_failure(self) -> None:
        for value in (None, "../evil.py", "scripts/gen.py"):
            geometry = self.read_json(SPEAR_GEOMETRY)
            if value is None:
                del geometry["generator"]
            else:
                geometry["generator"] = value
            self.write_json(SPEAR_GEOMETRY, geometry)
            details = self.details("COMBAT_GEOMETRY_CONTRACT")
            self.assertTrue(any("lingxiao_spear_geometry.json" in d and "generator" in d for d in details),
                            (value, details))

    # --- docs -------------------------------------------------------------------------------

    def test_docs_drift_has_named_failure(self) -> None:
        self.edit("README.md", "not_verified", "unchecked", count=-1)
        self.assertIn("COMBAT_DOC_DRIFT", self.codes())

    # --- packaged jar -----------------------------------------------------------------------

    def current_jar(self) -> Path:
        properties = (self.root / "gradle.properties").read_text(encoding="utf-8")
        version = next(line.split("=", 1)[1] for line in properties.splitlines()
                       if line.startswith("mod_version="))
        jar = self.root / "build/libs" / f"myvillage-{version}.jar"
        jar.parent.mkdir(parents=True, exist_ok=True)
        return jar

    def write_complete_jar(self, jar: Path, override: dict[str, bytes] | None = None) -> None:
        data = validator.combat_data.load(self.root)
        resources = validator.packaged_resources(self.root, data)
        classes = {path.relative_to(self.root / validator.JAVA_ROOT).with_suffix(".class").as_posix()
                   for path, _ in validator.combat_sources(self.root)}
        with zipfile.ZipFile(jar, "w") as archive:
            for name in sorted(resources):
                content = (override or {}).get(name, (self.root / RESOURCES / name).read_bytes())
                archive.writestr(name, content)
            for name in sorted(classes):
                archive.writestr(name, b"")

    def test_complete_jar_passes(self) -> None:
        self.write_complete_jar(self.current_jar())
        self.assertEqual([], [str(f) for f in self.findings()])

    def test_every_weapon_is_expected_in_the_jar(self) -> None:
        expected = validator.packaged_resources(self.root, validator.combat_data.load(self.root))
        for path in (SPEAR_MODEL, SPEAR_MODEL_3D, SPEAR_MODEL_TEXTURE, SPEAR_TEXTURE, SPEAR_RIG, SPEAR_GEOMETRY,
                     SPEAR_ANIMATIONS, SPEAR_STYLE, SPEAR_WEAPON, validator.QINGFENG_MODEL,
                     validator.QINGFENG_MODEL_3D, validator.QINGFENG_TEXTURE, validator.QINGFENG_MODEL_TEXTURE,
                     RIG, GEOMETRY, ANIMATIONS):
            self.assertIn(Path(path).relative_to(RESOURCES).as_posix(), expected)

    def test_jar_without_the_second_weapon_model_has_named_failure(self) -> None:
        jar = self.current_jar()
        self.write_complete_jar(jar)
        dropped = (Path(SPEAR_MODEL_3D).relative_to(RESOURCES).as_posix(),
                   Path(SPEAR_MODEL_TEXTURE).relative_to(RESOURCES).as_posix())
        with zipfile.ZipFile(jar) as archive:
            entries = {name: archive.read(name) for name in archive.namelist() if name not in dropped}
        with zipfile.ZipFile(jar, "w") as archive:
            for name, content in entries.items():
                archive.writestr(name, content)
        details = self.details("COMBAT_JAR_RESOURCE_MISSING")
        self.assertTrue(any(all(name in d for name in dropped) for d in details), details)

    def test_packaged_resource_drift_has_named_failure(self) -> None:
        with zipfile.ZipFile(self.current_jar(), "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        details = self.details("COMBAT_JAR_RESOURCE_MISSING")
        self.assertTrue(any("data/myvillage/combat/index.json" in d
                            and "data/myvillage/combat/style/basic_sword.json" in d
                            and "data/myvillage/combat/weapon/qingfeng_sword.json" in d for d in details), details)

    def test_packaged_combat_data_drift_has_named_failure(self) -> None:
        name = "data/myvillage/combat/style/basic_sword.json"
        self.write_complete_jar(self.current_jar(), {name: b"{}"})
        self.assertIn(name, self.details("COMBAT_JAR_DATA_DRIFT"))

    def test_jar_from_an_older_version_is_stale(self) -> None:
        jar = self.root / "build/libs/myvillage-0.0.1.jar"
        jar.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        self.assertIn("COMBAT_JAR_STALE", self.codes())


if __name__ == "__main__":
    unittest.main()
