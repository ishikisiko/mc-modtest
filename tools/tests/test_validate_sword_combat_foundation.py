from __future__ import annotations

import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

from tools import validate_sword_combat_foundation as validator


class SwordCombatFoundationValidatorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        for relative in (
                validator.PAL_JAR_NAME,
                "build.gradle",
                "gradle.properties",
                "README.md",
                "AGENTS.md",
                "docs/ai-kb/32_pal_combat_integration.md",
                "src/main/resources/META-INF/neoforge.mods.toml",
                "src/main/resources/assets/myvillage/player_animations/sword_combat.json",
                "src/main/resources/assets/myvillage/models/item/qingfeng_sword.json",
                "src/main/resources/assets/myvillage/textures/item/qingfeng_sword.png",
                "src/main/resources/assets/myvillage/lang/en_us.json",
                "src/main/resources/assets/myvillage/lang/zh_cn.json",
                "src/main/resources/data/myvillage/recipe/qingfeng_sword.json",
                "src/main/resources/data/minecraft/tags/item/swords.json",
                "src/main/java/com/example/myvillage/client/combat",
                "src/main/java/com/example/myvillage/combat",
                "src/main/java/com/example/myvillage/item/ModItems.java",
                "src/main/java/com/example/myvillage/cultivation/CultivationProfile.java",
                "src/main/java/com/example/myvillage/cultivation/meditation/MeditationManager.java",
                "src/main/java/com/example/myvillage/network/ModPayloads.java"):
            source = validator.ROOT / relative
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            if source.is_dir():
                shutil.copytree(source, target, dirs_exist_ok=True)
            else:
                shutil.copy2(source, target)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def codes(self) -> set[str]:
        return {finding.code for finding in validator.validate(self.root)}

    def test_valid_repository_fixture_passes(self) -> None:
        self.assertEqual(set(), self.codes())

    def test_missing_jar_has_named_failure(self) -> None:
        (self.root / validator.PAL_JAR_NAME).unlink()
        self.assertIn("PAL_JAR_MISSING", self.codes())

    def test_wrong_jar_hash_has_named_failure(self) -> None:
        (self.root / validator.PAL_JAR_NAME).write_bytes(b"not-pal")
        self.assertIn("PAL_JAR_SHA256", self.codes())

    def test_dependency_metadata_drift_has_named_failure(self) -> None:
        path = self.root / "src/main/resources/META-INF/neoforge.mods.toml"
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                'modId = "player_animation_library"',
                'modId = "guessed_animation_library"'),
            encoding="utf-8")
        self.assertIn("PAL_DEPENDENCY_MOD_ID", self.codes())

    def test_pal_import_outside_client_combat_is_rejected(self) -> None:
        path = self.root / "src/main/java/com/example/myvillage/combat/BadCommonImport.java"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            "package com.example.myvillage.combat;\n"
            "import com.zigythebird.playeranim.api.PlayerAnimationAccess;\n",
            encoding="utf-8")
        self.assertIn("PAL_IMPORT_OUTSIDE_CLIENT_COMBAT", self.codes())

    def test_smoke_animation_id_drift_has_named_failure(self) -> None:
        path = self.root / "src/main/resources/assets/myvillage/player_animations/sword_combat.json"
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                '"sword_mode_enter"',
                '"wrong_smoke_id"'),
            encoding="utf-8")
        self.assertIn("PAL_SMOKE_ANIMATION_ID", self.codes())

    def test_shaded_pal_class_is_rejected(self) -> None:
        jar = self.root / "build/libs/myvillage-negative.jar"
        jar.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("com/zigythebird/playeranim/Fake.class", b"")
        self.assertIn("PAL_SHADED_CONTENT", self.codes())

    def test_qingfeng_attribute_drift_has_named_failure(self) -> None:
        path = self.root / "src/main/java/com/example/myvillage/item/ModItems.java"
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)",
                "SwordItem.createAttributes(Tiers.DIAMOND, 4, -2.4F)"),
            encoding="utf-8")
        self.assertIn("QINGFENG_ATTRIBUTES", self.codes())

    def test_c2s_move_authority_has_named_failure(self) -> None:
        path = self.root / (
            "src/main/java/com/example/myvillage/combat/network/SwordAttackIntentPayload.java")
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "record SwordAttackIntentPayload()",
                "record SwordAttackIntentPayload(int moveId)"),
            encoding="utf-8")
        self.assertIn("COMBAT_C2S_AUTHORITY_FIELD", self.codes())

    def test_client_action_revision_reset_drift_has_named_failure(self) -> None:
        path = self.root / (
            "src/main/java/com/example/myvillage/client/combat/ClientCombatState.java")
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "ACTION_REVISIONS.clear();", "ACTION_REVISIONS.size();", 1),
            encoding="utf-8")
        self.assertIn("COMBAT_CLIENT_WORLD_REVISION_RESET", self.codes())

    def test_first_person_mode_drift_has_named_failure(self) -> None:
        path = self.root / (
            "src/main/java/com/example/myvillage/client/combat/CombatAnimationController.java")
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "FirstPersonMode.DISABLED", "FirstPersonMode.THIRD_PERSON_MODEL"),
            encoding="utf-8")
        self.assertIn("PAL_CUSTOM_FIRST_PERSON_DISABLED", self.codes())

    def test_first_person_item_extension_registration_drift_has_named_failure(self) -> None:
        path = self.root / (
            "src/main/java/com/example/myvillage/client/combat/ClientCombatBootstrap.java")
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "event.registerItem(QingfengFirstPersonAnimator.INSTANCE, "
                "ModItems.QINGFENG_SWORD.get());",
                "// removed first-person extension registration"),
            encoding="utf-8")
        self.assertIn("COMBAT_FIRST_PERSON_EXTENSION_REGISTRATION", self.codes())

    def test_first_person_move_curve_alias_has_named_failure(self) -> None:
        path = self.root / (
            "src/main/java/com/example/myvillage/client/combat/FirstPersonSwordPose.java")
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "List.of(THRUST, HORIZONTAL_CUT, RISING_CUT, DIAGONAL_CUT, LUNGE_THRUST)",
                "List.of(THRUST, HORIZONTAL_CUT, RISING_CUT, DIAGONAL_CUT, THRUST)"),
            encoding="utf-8")
        self.assertIn("COMBAT_FIRST_PERSON_MOVE_CURVES", self.codes())

    def test_first_person_amplitude_drift_has_named_failure(self) -> None:
        path = self.root / (
            "src/main/java/com/example/myvillage/client/combat/FirstPersonSwordPose.java")
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "AMPLITUDE_SCALE = 1.20F", "AMPLITUDE_SCALE = 1.00F"),
            encoding="utf-8")
        self.assertIn("COMBAT_FIRST_PERSON_AMPLITUDE_SCALE", self.codes())

    def test_first_person_continuity_timing_drift_has_named_failure(self) -> None:
        path = self.root / (
            "src/main/java/com/example/myvillage/client/combat/FirstPersonSwordPose.java")
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "frame(0.84F", "frame(0.62F", 1),
            encoding="utf-8")
        self.assertIn("COMBAT_FIRST_PERSON_CONTINUITY_TIMING", self.codes())

    def test_first_person_corrected_timeline_drift_has_named_failure(self) -> None:
        path = self.root / (
            "src/main/java/com/example/myvillage/client/combat/QingfengFirstPersonAnimator.java")
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "player.level().getGameTime() - Math.max(0.0F, elapsedTicks)",
                "player.level().getGameTime()"),
            encoding="utf-8")
        self.assertIn("COMBAT_FIRST_PERSON_CORRECTED_TIMELINE", self.codes())

    def test_serverbound_vanilla_swing_is_rejected(self) -> None:
        path = self.root / (
            "src/main/java/com/example/myvillage/client/combat/ClientCombatEvents.java")
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "player.swing(InteractionHand.MAIN_HAND, false);",
                "player.swing(InteractionHand.MAIN_HAND);"),
            encoding="utf-8")
        codes = self.codes()
        self.assertIn("COMBAT_LOCAL_FIRST_PERSON_FEEDBACK", codes)
        self.assertIn("COMBAT_SERVERBOUND_VANILLA_SWING", codes)

    def test_move_definition_drift_has_named_failure(self) -> None:
        path = self.root / (
            "src/main/java/com/example/myvillage/combat/definition/BasicSwordStyle.java")
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "11, 3, 4, 0.90, 1, 3.0",
                "11, 3, 4, 1.90, 1, 3.0"),
            encoding="utf-8")
        self.assertIn("COMBAT_MOVE_DEFINITION_DRIFT", self.codes())

    def test_attack_animation_length_drift_has_named_failure(self) -> None:
        path = self.root / "src/main/resources/assets/myvillage/player_animations/sword_combat.json"
        data = path.read_text(encoding="utf-8").replace(
            '"basic_sword_01_thrust": {\n      "animation_length": 0.55',
            '"basic_sword_01_thrust": {\n      "animation_length": 0.75')
        path.write_text(data, encoding="utf-8")
        self.assertIn("COMBAT_ANIMATION_LENGTH", self.codes())

    def test_geometry_tolerance_drift_has_named_failure(self) -> None:
        path = self.root / (
            "src/main/java/com/example/myvillage/combat/definition/BasicSwordStyle.java")
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "new HitboxDefinition(shape, samples, 0.20, 0.12)",
                "new HitboxDefinition(shape, samples, 0.30, 0.12)"),
            encoding="utf-8")
        self.assertIn("COMBAT_TOLERANCE_BOUND", self.codes())

    def test_damage_hook_drift_has_named_failure(self) -> None:
        path = self.root / (
            "src/main/java/com/example/myvillage/combat/runtime/CombatDamageService.java")
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "CommonHooks.onPlayerAttackTarget", "CommonHooks.removedAttackGate"),
            encoding="utf-8")
        self.assertIn("COMBAT_ATTACK_GATE", self.codes())

    def test_docs_drift_has_named_failure(self) -> None:
        path = self.root / "README.md"
        path.write_text(
            path.read_text(encoding="utf-8").replace(
                "SWORD_COMBAT_FOUNDATION", "REMOVED_COMBAT_MARKER"),
            encoding="utf-8")
        self.assertIn("COMBAT_DOC_DRIFT", self.codes())

    def test_packaged_resource_drift_has_named_failure(self) -> None:
        jar = self.root / "build/libs/myvillage-negative.jar"
        jar.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        self.assertIn("COMBAT_JAR_RESOURCE_MISSING", self.codes())


if __name__ == "__main__":
    unittest.main()
