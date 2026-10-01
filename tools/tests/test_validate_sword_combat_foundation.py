from __future__ import annotations

import json
import re
import shutil
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
    SOUNDS,
    validator.QINGFENG_MODEL,
    validator.QINGFENG_TEXTURE,
    validator.QINGFENG_MODEL_3D,
    validator.QINGFENG_MODEL_TEXTURE,
    validator.QINGFENG_RECIPE,
    validator.SWORD_TAG,
    validator.BLADE_CUT_PARTICLE,
    validator.BLADE_CUT_TEXTURE,
    "tools/combat_data.py",
    *(script for script, _ in validator.GENERATOR_CHECKS),
)


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

    # --- whole repository -------------------------------------------------------------------

    def test_valid_repository_fixture_passes(self) -> None:
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

    # --- Qingfeng item and presentation assets ----------------------------------------------

    def test_qingfeng_attribute_drift_has_named_failure(self) -> None:
        self.edit("src/main/java/com/example/myvillage/item/ModItems.java",
                  "SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)",
                  "SwordItem.createAttributes(Tiers.DIAMOND, 4, -2.4F)", count=-1)
        self.assertIn("QINGFENG_ATTRIBUTES", self.codes())

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
        path = self.root / ANIMATIONS
        path.write_text(path.read_text(encoding="utf-8") + "\n", encoding="utf-8")
        self.assertIn("COMBAT_PAL_GENERATOR_DRIFT", self.codes(run_generators=True))

    def test_style_without_poses_fails_the_generator_check(self) -> None:
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
        self.assertIn("COMBAT_SWORD_MODEL_GENERATOR_DRIFT", self.codes(run_generators=True))

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
