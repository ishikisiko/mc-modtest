from __future__ import annotations

import json
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
                "src/main/resources/assets/myvillage/sounds.json",
                "src/main/resources/assets/myvillage/combat/qingfeng_first_person.json",
                "src/main/resources/data/myvillage/recipe/qingfeng_sword.json",
                "src/main/resources/data/minecraft/tags/item/swords.json",
                "src/main/java/com/example/myvillage/client/combat",
                "src/main/java/com/example/myvillage/combat",
                "src/main/java/com/example/myvillage/item/ModItems.java",
                "src/main/java/com/example/myvillage/cultivation/CultivationProfile.java",
                "src/main/java/com/example/myvillage/cultivation/meditation/MeditationManager.java",
                "src/main/java/com/example/myvillage/network/ModPayloads.java",
                "src/test/java/com/example/myvillage/client/combat/FirstPersonSwingTest.java",
                "src/test/java/com/example/myvillage/client/combat/SwingClockTest.java"):
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

    def edit(self, relative: str, old: str, new: str) -> None:
        path = self.root / relative
        content = path.read_text(encoding="utf-8")
        self.assertIn(old, content)
        path.write_text(content.replace(old, new, 1), encoding="utf-8")

    def rig(self) -> dict:
        return json.loads((self.root / validator.FIRST_PERSON_RIG).read_text(encoding="utf-8"))

    def write_rig(self, rig: dict) -> None:
        (self.root / validator.FIRST_PERSON_RIG).write_text(json.dumps(rig), encoding="utf-8")

    def test_first_person_rig_missing_has_named_failure(self) -> None:
        (self.root / validator.FIRST_PERSON_RIG).unlink()
        self.assertIn("COMBAT_FIRST_PERSON_RIG_MISSING", self.codes())

    def test_first_person_strike_after_active_window_has_named_failure(self) -> None:
        rig = self.rig()
        rig["moves"]["myvillage:basic_sword_02_horizontal_cut"]["strike"] = [4.5, 6.2]
        self.write_rig(rig)
        self.assertIn("COMBAT_FIRST_PERSON_RIG_STRIKE", self.codes())

    def test_first_person_slow_strike_has_named_failure(self) -> None:
        rig = self.rig()
        rig["moves"]["myvillage:basic_sword_04_diagonal_cut"]["strike"] = [2.0, 9.0]
        self.write_rig(rig)
        self.assertIn("COMBAT_FIRST_PERSON_RIG_STRIKE", self.codes())

    def test_first_person_rig_unknown_move_has_named_failure(self) -> None:
        rig = self.rig()
        rig["moves"]["myvillage:basic_sword_06_extra"] = rig["moves"]["myvillage:basic_sword_01_thrust"]
        self.write_rig(rig)
        self.assertIn("COMBAT_FIRST_PERSON_RIG_MOVES", self.codes())

    def test_first_person_rig_not_ending_neutral_has_named_failure(self) -> None:
        rig = self.rig()
        rig["moves"]["myvillage:basic_sword_03_rising_cut"]["keys"][-1].pop("pose")
        self.write_rig(rig)
        self.assertIn("COMBAT_FIRST_PERSON_RIG_NEUTRAL", self.codes())

    def test_first_person_rig_reload_registration_drift_has_named_failure(self) -> None:
        self.edit(
            "src/main/java/com/example/myvillage/client/combat/ClientCombatBootstrap.java",
            "event.registerReloadListener(FirstPersonSwingResources.INSTANCE);",
            "// removed rig reload")
        self.assertIn("COMBAT_FIRST_PERSON_RIG_RELOAD", self.codes())

    def test_first_person_transform_order_drift_has_named_failure(self) -> None:
        self.edit(
            "src/main/java/com/example/myvillage/client/combat/FirstPersonSwordTransform.java",
            "poseStack.mulPose(Axis.XP.rotationDegrees(GRIP_ALIGN_PITCH));",
            "// removed grip alignment")
        self.assertIn("COMBAT_FIRST_PERSON_TRANSFORM_ORDER", self.codes())

    def test_first_person_trail_independent_clock_is_rejected(self) -> None:
        self.edit(
            "src/main/java/com/example/myvillage/client/combat/FirstPersonSwordTrail.java",
            "        Minecraft minecraft = Minecraft.getInstance();\n",
            "        Minecraft minecraft = Minecraft.getInstance();\n"
            "        long ignored = minecraft.level.getGameTime();\n")
        self.assertIn("COMBAT_FIRST_PERSON_TRAIL_DUPLICATE_TIMELINE", self.codes())

    def test_first_person_trail_item_pass_cancellation_is_rejected(self) -> None:
        self.edit(
            "src/main/java/com/example/myvillage/client/combat/FirstPersonSwordTrail.java",
            "        FirstPersonSwing.Move move =",
            "        event.setCanceled(true);\n        FirstPersonSwing.Move move =")
        self.assertIn("COMBAT_FIRST_PERSON_TRAIL_ITEM_PASS_CANCEL", self.codes())

    def test_world_trail_hitbox_source_drift_has_named_failure(self) -> None:
        self.edit(
            "src/main/java/com/example/myvillage/client/combat/CombatWorldTrails.java",
            "move.hitbox().samples()",
            "List.<HitboxSample>of()")
        self.assertIn("COMBAT_WORLD_TRAIL_HITBOX_SOURCE", self.codes())

    def test_hit_stop_catch_up_drift_has_named_failure(self) -> None:
        self.edit(
            "src/main/java/com/example/myvillage/client/combat/SwingClock.java",
            "(totalTicks - frozenAt) / (totalTicks - stopEnd)",
            "1.0F")
        self.assertIn("COMBAT_HIT_STOP_CATCH_UP", self.codes())

    def test_hit_feedback_before_damage_has_named_failure(self) -> None:
        self.edit(
            "src/main/java/com/example/myvillage/combat/session/CombatSessionManager.java",
            "CombatFeedbackService.hit(player, move, session.revision(), successfulContacts);",
            "CombatFeedbackService.hit(player, move, session.revision(), resolution.contacts());")
        self.assertIn("COMBAT_HIT_FEEDBACK_AFTER_DAMAGE", self.codes())

    def test_hit_confirm_authority_field_is_rejected(self) -> None:
        self.edit(
            "src/main/java/com/example/myvillage/combat/network/CombatHitConfirmPayload.java",
            "        int hitCount) implements",
            "        int hitCount,\n        float damage) implements")
        self.assertIn("COMBAT_HIT_CONFIRM_AUTHORITY_FIELD", self.codes())

    def test_missing_sound_event_has_named_failure(self) -> None:
        path = self.root / "src/main/resources/assets/myvillage/sounds.json"
        sounds = json.loads(path.read_text(encoding="utf-8"))
        del sounds["combat.sword.hit"]
        path.write_text(json.dumps(sounds), encoding="utf-8")
        self.assertIn("COMBAT_SOUND_EVENT", self.codes())

    def test_first_person_third_party_rig_import_is_rejected(self) -> None:
        self.edit(
            "src/main/java/com/example/myvillage/client/combat/FirstPersonSwordTrail.java",
            "import org.joml.Vector3f;",
            "import org.joml.Vector3f;\nimport yesman.epicfight.api.Placeholder;")
        self.assertIn("COMBAT_FIRST_PERSON_THIRD_PARTY_RIG_FORBIDDEN", self.codes())

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
