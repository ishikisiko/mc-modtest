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
                validator.QINGFENG_MODEL_3D,
                validator.QINGFENG_MODEL_TEXTURE,
                validator.QINGFENG_GEOMETRY,
                "src/main/resources/assets/myvillage/lang/en_us.json",
                "src/main/resources/assets/myvillage/lang/zh_cn.json",
                "src/main/resources/assets/myvillage/sounds.json",
                "src/main/resources/assets/myvillage/combat/qingfeng_first_person.json",
                validator.BLADE_CUT_PARTICLE,
                validator.BLADE_CUT_TEXTURE,
                *(script for script, _ in validator.GENERATOR_CHECKS),
                "src/main/resources/data/myvillage/recipe/qingfeng_sword.json",
                "src/main/resources/data/minecraft/tags/item/swords.json",
                "src/main/java/com/example/myvillage/client/combat",
                "src/main/java/com/example/myvillage/combat",
                "src/main/java/com/example/myvillage/item/ModItems.java",
                "src/main/java/com/example/myvillage/cultivation/CultivationProfile.java",
                "src/main/java/com/example/myvillage/cultivation/meditation/MeditationManager.java",
                "src/main/java/com/example/myvillage/network/ModPayloads.java",
                "src/test/java/com/example/myvillage/client/combat/FirstPersonSwingTest.java",
                "src/test/java/com/example/myvillage/client/combat/SwingClockTest.java",
                "src/test/java/com/example/myvillage/client/combat/FirstPersonArmIkTest.java"):
            source = validator.ROOT / relative
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            if source.is_dir():
                shutil.copytree(source, target, dirs_exist_ok=True)
            else:
                shutil.copy2(source, target)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def codes(self, run_generators: bool = False) -> set[str]:
        # The generator drift checks spawn Python; only the tests that need them run them.
        return {finding.code for finding in validator.validate(self.root, run_generators)}

    def test_valid_repository_fixture_passes(self) -> None:
        self.assertEqual(set(), self.codes(run_generators=True))

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

    def edit(self, relative: str, old: str, new: str, count: int = 1) -> None:
        path = self.root / relative
        content = path.read_text(encoding="utf-8")
        self.assertIn(old, content)
        path.write_text(content.replace(old, new, count), encoding="utf-8")

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
            "poseStack.mulPose(itemToGrip(swing.sword(), swing.rig().swordScale(), display));",
            "// removed display undo")
        self.assertIn("COMBAT_FIRST_PERSON_TRANSFORM_ORDER", self.codes())

    def test_hardcoded_grip_constants_are_rejected(self) -> None:
        self.edit(self.CLIENT + "FirstPersonSwordTransform.java",
                  "    static final float EQUIP_DROP = 0.60F;",
                  "    static final float EQUIP_DROP = 0.60F;\n    private static final float GRIP_X = 0.0706F;")
        self.assertIn("COMBAT_FIRST_PERSON_HARDCODED_GRIP", self.codes())

    def test_display_transform_undo_removal_has_named_failure(self) -> None:
        self.edit(self.CLIENT + "QingfengFirstPersonAnimator.java",
                  "model.applyTransform(context, scratch, leftHand);", "// display ignored")
        self.assertIn("COMBAT_FIRST_PERSON_DISPLAY_UNDO", self.codes())

    def test_sword_geometry_reload_removal_has_named_failure(self) -> None:
        self.edit(self.CLIENT + "FirstPersonSwingResources.java",
                  "geometry = SwordGeometry.parse(geometryJson.get());", "geometry = null;")
        self.assertIn("COMBAT_SWORD_GEOMETRY_RELOAD", self.codes())

    def test_trail_sprite_blade_constants_are_rejected(self) -> None:
        self.edit(self.CLIENT + "FirstPersonSwordTrail.java",
                  "    private FirstPersonSwordTrail() {",
                  "    private static final Vector3f BLADE_TIP = new Vector3f(60.0F / 64.0F, 0.97F, 0.5F);\n\n"
                  "    private FirstPersonSwordTrail() {")
        self.assertIn("COMBAT_FIRST_PERSON_TRAIL_HARDCODED_BLADE", self.codes())

    def test_arm_without_fist_or_wrist_lag_has_named_failure(self) -> None:
        self.edit(self.CLIENT + "QingfengFirstPersonArmRenderer.java",
                  "FirstPersonArmLag.offset(", "FirstPersonArmLag.ignored(")
        self.assertIn("COMBAT_FIRST_PERSON_WRIST_LAG", self.codes())
        self.edit(self.CLIENT + "QingfengFirstPersonArmRenderer.java", "model.fist(sleeve)", "model.forearm(sleeve)")
        self.assertIn("COMBAT_FIRST_PERSON_FIST", self.codes())

    def test_first_person_rig_arm_nonsense_has_named_failure(self) -> None:
        path = self.root / "src/main/resources/assets/myvillage/combat/qingfeng_first_person.json"
        rig = json.loads(path.read_text(encoding="utf-8"))
        rig["rig"]["arm"]["thickness"] = 3.0
        path.write_text(json.dumps(rig), encoding="utf-8")
        self.assertIn("COMBAT_FIRST_PERSON_RIG_ARM", self.codes())
        rig["rig"]["arm"]["thickness"] = 0.5
        rig["neutral"]["grip_roll"] = "palm up"
        path.write_text(json.dumps(rig), encoding="utf-8")
        self.assertIn("COMBAT_FIRST_PERSON_RIG_ARM", self.codes())

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
            "1.0F",
            count=-1)
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

    def current_jar_name(self) -> str:
        properties = (self.root / "gradle.properties").read_text(encoding="utf-8")
        version = next(line.split("=", 1)[1] for line in properties.splitlines()
                       if line.startswith("mod_version="))
        return f"myvillage-{version}.jar"

    def test_packaged_resource_drift_has_named_failure(self) -> None:
        jar = self.root / "build/libs" / self.current_jar_name()
        jar.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        self.assertIn("COMBAT_JAR_RESOURCE_MISSING", self.codes())

    def test_jar_from_an_older_version_is_stale(self) -> None:
        jar = self.root / "build/libs/myvillage-0.0.1.jar"
        jar.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        self.assertIn("COMBAT_JAR_STALE", self.codes())

    # --- Action-feel revision (0.27.0) invariants ---

    STYLE = "src/main/java/com/example/myvillage/combat/definition/BasicSwordStyle.java"
    MOVE = "src/main/java/com/example/myvillage/combat/definition/AttackMoveDefinition.java"
    SESSION = "src/main/java/com/example/myvillage/combat/session/CombatSession.java"
    MANAGER = "src/main/java/com/example/myvillage/combat/session/CombatSessionManager.java"
    STEP = "src/main/java/com/example/myvillage/combat/runtime/CombatStepService.java"
    FEEDBACK = "src/main/java/com/example/myvillage/combat/runtime/CombatFeedbackService.java"
    REACTION = "src/main/java/com/example/myvillage/combat/runtime/CombatReactionService.java"
    PAYLOADS = "src/main/java/com/example/myvillage/combat/network/CombatPayloads.java"
    IMPACT = "src/main/java/com/example/myvillage/combat/network/CombatImpactPayload.java"
    CLIENT = "src/main/java/com/example/myvillage/client/combat/"

    def test_protocol_version_drift_has_named_failure(self) -> None:
        self.edit("src/main/java/com/example/myvillage/network/ModPayloads.java",
                  'PROTOCOL_VERSION = "6"', 'PROTOCOL_VERSION = "5"')
        self.assertIn("COMBAT_PROTOCOL_VERSION", self.codes())

    def test_chain_tick_drift_has_named_failure(self) -> None:
        self.edit(self.STYLE, "11, 3, 4, 0.90, 1, 3.0, 3, 7,", "11, 3, 4, 0.90, 1, 3.0, 3, 9,")
        self.assertIn("COMBAT_CHAIN_WINDOW_DRIFT", self.codes())

    def test_chain_invariant_removal_has_named_failure(self) -> None:
        self.edit(self.MOVE, "chainTick <= activeEndTick || ", "")
        self.assertIn("COMBAT_CHAIN_INVARIANT", self.codes())

    def test_late_recovery_buffer_regression_has_named_failure(self) -> None:
        self.edit(self.MOVE, "return actionTick >= bufferStartTick && actionTick < totalTicks;",
                  "return actionTick >= totalTicks - 2 && actionTick < totalTicks;")
        self.assertIn("COMBAT_BUFFER_FROM_ACTIVE_START", self.codes())

    def test_one_slot_buffer_removal_has_named_failure(self) -> None:
        self.edit(self.SESSION, "REJECTED_BUFFER_FULL", "REJECTED_QUEUE_FULL", count=-1)
        self.assertIn("COMBAT_BUFFER_CAPACITY", self.codes())

    def test_step_distance_drift_has_named_failure(self) -> None:
        self.edit(self.STYLE, "new StepDefinition(6, 1.40, 0.35)", "new StepDefinition(6, 1.60, 0.35)")
        self.assertIn("COMBAT_STEP_BOUND", self.codes())

    def test_server_side_step_move_is_rejected(self) -> None:
        self.edit(self.STEP, "player.hurtMarked = true;",
                  "player.hurtMarked = true;\n        player.move(MoverType.PLAYER, Vec3.ZERO);")
        self.assertIn("COMBAT_STEP_SERVER_MOVE", self.codes())

    def test_step_impulse_removal_has_named_failure(self) -> None:
        self.edit(self.STEP, "player.hurtMarked = true;", "// no sync")
        self.assertIn("COMBAT_STEP_IMPULSE", self.codes())

    def test_swing_sound_timing_drift_has_named_failure(self) -> None:
        self.edit(self.MANAGER, "actionTick == move.activeStartTick() - 1", "actionTick == move.activeStartTick()")
        self.assertIn("COMBAT_SWING_SOUND_TIMING", self.codes())

    def test_impact_payload_client_to_server_is_rejected(self) -> None:
        self.edit(self.PAYLOADS, "registrar.playToClient(\n                CombatImpactPayload.TYPE",
                  "registrar.playToServer(\n                CombatImpactPayload.TYPE")
        self.assertIn("COMBAT_IMPACT_S2C_ONLY", self.codes())

    def test_impact_payload_damage_field_is_rejected(self) -> None:
        self.edit(self.IMPACT, "        List<Vec3> contactPoints) implements",
                  "        List<Vec3> contactPoints,\n        float damage) implements")
        self.assertIn("COMBAT_IMPACT_AUTHORITY_FIELD", self.codes())

    def test_player_freeze_is_rejected(self) -> None:
        self.edit(self.REACTION, "boolean freezable = target instanceof Mob && !excluded",
                  "boolean freezable = target instanceof LivingEntity && !excluded")
        self.assertIn("COMBAT_REACTION_NO_PLAYER_FREEZE", self.codes())

    def test_vanilla_sweep_particle_is_rejected(self) -> None:
        self.edit(self.FEEDBACK, "ParticleTypes.CRIT, point.x", "ParticleTypes.SWEEP_ATTACK, point.x")
        self.assertIn("COMBAT_VANILLA_SWEEP_PARTICLE", self.codes())

    def test_combat_slow_fov_correction_removal_has_named_failure(self) -> None:
        self.edit(self.CLIENT + "ClientCombatBootstrap.java",
                  "NeoForge.EVENT_BUS.addListener(CombatCameraFx::onComputeFovModifier);", "")
        self.assertIn("COMBAT_SLOW_FOV_CORRECTION_REGISTRATION", self.codes())

    def test_camera_fx_authority_leak_is_rejected(self) -> None:
        self.edit(self.CLIENT + "CombatCameraFx.java", "event.setNewFovModifier(",
                  "PacketDistributor.sendToServer(null);\n            event.setNewFovModifier(")
        self.assertIn("COMBAT_PRESENTATION_AUTHORITY_LEAK", self.codes())

    def test_additive_trail_is_rejected(self) -> None:
        self.edit(self.CLIENT + "CombatRenderTypes.java", "RenderStateShard.TRANSLUCENT_TRANSPARENCY",
                  "RenderStateShard.LIGHTNING_TRANSPARENCY")
        self.assertIn("COMBAT_TRAIL_ADDITIVE_BLEND", self.codes())

    def test_arm_renderer_cancelling_hand_is_rejected(self) -> None:
        self.edit(self.CLIENT + "QingfengFirstPersonArmRenderer.java",
                  "public static void onRenderHand(RenderHandEvent event) {",
                  "public static void onRenderHand(RenderHandEvent event) {\n        event.setCanceled(true);")
        self.assertIn("COMBAT_FIRST_PERSON_ARM_CANCELS_HAND", self.codes())

    def test_arm_renderer_registration_removal_has_named_failure(self) -> None:
        self.edit(self.CLIENT + "ClientCombatBootstrap.java",
                  "NeoForge.EVENT_BUS.addListener(QingfengFirstPersonArmRenderer::onRenderHand);", "")
        self.assertIn("COMBAT_FIRST_PERSON_ARM_REGISTRATION", self.codes())

    def test_heavy_impact_sound_missing_has_named_failure(self) -> None:
        path = self.root / "src/main/resources/assets/myvillage/sounds.json"
        sounds = json.loads(path.read_text(encoding="utf-8"))
        del sounds["combat.sword.impact_heavy"]["subtitle"]
        path.write_text(json.dumps(sounds), encoding="utf-8")
        self.assertIn("COMBAT_SOUND_EVENT", self.codes())

    def test_blade_cut_particle_json_missing_has_named_failure(self) -> None:
        (self.root / validator.BLADE_CUT_PARTICLE).unlink()
        self.assertIn("COMBAT_BLADE_CUT_PARTICLE_JSON", self.codes())

    def test_hand_edited_pal_animation_has_generator_drift(self) -> None:
        path = self.root / "src/main/resources/assets/myvillage/player_animations/sword_combat.json"
        path.write_text(path.read_text(encoding="utf-8") + "\n", encoding="utf-8")
        self.assertIn("COMBAT_PAL_GENERATOR_DRIFT", self.codes(run_generators=True))

    def test_hand_edited_blade_cut_sprite_has_generator_drift(self) -> None:
        path = self.root / validator.BLADE_CUT_TEXTURE
        path.write_bytes(path.read_bytes() + b"\0")
        self.assertIn("COMBAT_BLADE_CUT_SPRITE_DRIFT", self.codes(run_generators=True))

    def test_qingfeng_flat_handheld_model_has_named_failure(self) -> None:
        path = self.root / "src/main/resources/assets/myvillage/models/item/qingfeng_sword.json"
        path.write_text(json.dumps({"parent": "minecraft:item/handheld",
                                    "textures": {"layer0": "myvillage:item/qingfeng_sword"}}), encoding="utf-8")
        self.assertIn("QINGFENG_MODEL_CONTRACT", self.codes())

    def test_qingfeng_3d_model_missing_has_named_failure(self) -> None:
        (self.root / validator.QINGFENG_MODEL_3D).unlink()
        self.assertIn("QINGFENG_MODEL_3D", self.codes())

    def test_qingfeng_3d_model_with_gui_display_has_named_failure(self) -> None:
        path = self.root / validator.QINGFENG_MODEL_3D
        model = json.loads(path.read_text(encoding="utf-8"))
        model["display"]["gui"] = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}
        path.write_text(json.dumps(model), encoding="utf-8")
        self.assertIn("QINGFENG_MODEL_3D", self.codes())

    def test_qingfeng_geometry_contract_drift_has_named_failure(self) -> None:
        path = self.root / validator.QINGFENG_GEOMETRY
        geometry = json.loads(path.read_text(encoding="utf-8"))
        geometry["grip_center"][1] = geometry["guard"]["y"][1]  # grip outside the handle
        path.write_text(json.dumps(geometry), encoding="utf-8")
        self.assertIn("QINGFENG_GEOMETRY_CONTRACT", self.codes())
        del geometry["blade_tip"]
        path.write_text(json.dumps(geometry), encoding="utf-8")
        self.assertIn("QINGFENG_GEOMETRY_CONTRACT", self.codes())

    def test_hand_edited_sword_model_has_generator_drift(self) -> None:
        path = self.root / validator.QINGFENG_MODEL_3D
        path.write_text(path.read_text(encoding="utf-8").replace('"scale": [0.8, 0.8, 0.8]', '"scale": [0.85, 0.85, 0.85]'),
                        encoding="utf-8")
        self.assertIn("COMBAT_SWORD_MODEL_GENERATOR_DRIFT", self.codes(run_generators=True))


if __name__ == "__main__":
    unittest.main()
