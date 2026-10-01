#!/usr/bin/env python3
"""Validate the staged PAL and server-authoritative sword-combat contract."""

from __future__ import annotations

import hashlib
import json
import re
import struct
import subprocess
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PAL_JAR_NAME = "PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar"
PAL_SHA256 = "b0836ad98db1e614f1e62cb40d5943eb4ba7d51f298e4b3ad0746770364ab072"
EXPECTED_MOVES = {
    "basic_sword_01_thrust": (11, 3, 4, 0.90, 1, 3.0, 0.55),
    "basic_sword_02_horizontal_cut": (13, 4, 6, 0.95, 3, 2.8, 0.65),
    "basic_sword_03_rising_cut": (15, 5, 7, 1.00, 2, 2.8, 0.75),
    "basic_sword_04_diagonal_cut": (17, 6, 8, 1.10, 3, 3.0, 0.85),
    "basic_sword_05_lunge_thrust": (20, 7, 9, 1.25, 2, 3.5, 1.0),
}
# (bufferStartTick, chainTick) per move: the buffer opens at the active start and a held click
# cancels recovery into the next move at chainTick; the finisher cannot chain (chain == total).
EXPECTED_CHAINS = {
    "basic_sword_01_thrust": (3, 7),
    "basic_sword_02_horizontal_cut": (4, 8),
    "basic_sword_03_rising_cut": (5, 10),
    "basic_sword_04_diagonal_cut": (6, 13),
    "basic_sword_05_lunge_thrust": (7, 20),
}
# Server-decided step impulses: new StepDefinition(actionTick, maximumDistance, supportDepth).
EXPECTED_STEPS = {
    "basic_sword_01_thrust": "new StepDefinition(2, 0.30, 0.35)",
    "basic_sword_02_horizontal_cut": "new StepDefinition(3, 0.25, 0.35)",
    "basic_sword_03_rising_cut": "new StepDefinition(4, 0.30, 0.35)",
    "basic_sword_04_diagonal_cut": "new StepDefinition(5, 0.45, 0.35)",
    "basic_sword_05_lunge_thrust": "new StepDefinition(6, 1.40, 0.35)",
}
REQUIRED_ANIMATION_BONES = {
    "body", "head", "right_arm", "left_arm", "right_leg", "left_leg"
}
REQUIRED_ANIMATION_IDS = {"sword_mode_enter", "sword_ready_idle", *EXPECTED_MOVES}
COMBAT_TRANSLATIONS = {
    "key.myvillage.toggle_combat_mode",
    "message.myvillage.combat.mode.cultivation",
    "message.myvillage.combat.mode.vanilla",
    "commands.myvillage.combat.debug.on",
    "commands.myvillage.combat.debug.off",
    "commands.myvillage.combat.debug.player_only",
    *(f"combat.myvillage.move.{move_id}" for move_id in EXPECTED_MOVES),
    *(f"subtitles.myvillage.combat.sword.{cue}"
      for cue in ("cut", "thrust", "hit", "hit_heavy", "impact_heavy")),
}
COMBAT_SOUND_EVENTS = (
    "combat.sword.cut", "combat.sword.thrust", "combat.sword.hit", "combat.sword.hit_heavy",
    "combat.sword.impact_heavy")
BLADE_CUT_PARTICLE = "src/main/resources/assets/myvillage/particles/blade_cut.json"
BLADE_CUT_TEXTURE = "src/main/resources/assets/myvillage/textures/particle/blade_cut.png"
GENERATOR_CHECKS = (
    ("tools/gen_sword_pal_anims.py", "COMBAT_PAL_GENERATOR_DRIFT"),
    ("tools/gen_blade_cut_sprite.py", "COMBAT_BLADE_CUT_SPRITE_DRIFT"),
    ("tools/gen_qingfeng_sword_model.py", "COMBAT_SWORD_MODEL_GENERATOR_DRIFT"),
)
QINGFENG_MODEL_3D = "src/main/resources/assets/myvillage/models/item/qingfeng_sword_3d.json"
QINGFENG_MODEL_TEXTURE = "src/main/resources/assets/myvillage/textures/item/qingfeng_sword_model.png"
QINGFENG_GEOMETRY = "src/main/resources/assets/myvillage/combat/qingfeng_sword_geometry.json"
QINGFENG_GEOMETRY_FIELDS = ("grip_center", "handle", "guard", "pommel", "blade_base", "blade_tip",
                            "edge_axis", "flat_axis", "axes")
GENERATOR_TIMEOUT_SECONDS = 120
FIRST_PERSON_RIG = "src/main/resources/assets/myvillage/combat/qingfeng_first_person.json"
MAX_STRIKE_TICKS = 3.0


@dataclass(frozen=True)
class Finding:
    code: str
    detail: str

    def __str__(self) -> str:
        return f"{self.code}: {self.detail}"


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def require_file(path: Path, root: Path, code: str, findings: list[Finding]) -> bool:
    if path.is_file():
        return True
    findings.append(Finding(code, path.relative_to(root).as_posix()))
    return False


def require_contains(
        content: str,
        needle: str,
        code: str,
        detail: str,
        findings: list[Finding]) -> None:
    if needle not in content:
        findings.append(Finding(code, detail))


def validate_pal_jar(root: Path, findings: list[Finding]) -> None:
    jar = root / PAL_JAR_NAME
    if not require_file(jar, root, "PAL_JAR_MISSING", findings):
        return

    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    if digest != PAL_SHA256:
        findings.append(Finding("PAL_JAR_SHA256", f"expected {PAL_SHA256}, got {digest}"))
        return

    try:
        with zipfile.ZipFile(jar) as archive:
            metadata = archive.read("META-INF/neoforge.mods.toml").decode("utf-8")
            license_text = archive.read("LICENSE").decode("utf-8")
            names = set(archive.namelist())
    except (KeyError, zipfile.BadZipFile, UnicodeDecodeError) as exc:
        findings.append(Finding("PAL_JAR_METADATA", str(exc)))
        return

    require_contains(metadata, 'modId = "player_animation_library"', "PAL_MOD_ID", PAL_JAR_NAME, findings)
    require_contains(metadata, 'version = "1.1.4+mc.1.21.1"', "PAL_VERSION", PAL_JAR_NAME, findings)
    require_contains(metadata, 'license = "MIT License"', "PAL_LICENSE_METADATA", PAL_JAR_NAME, findings)
    require_contains(metadata, 'side = "BOTH"', "PAL_DECLARED_SIDE", PAL_JAR_NAME, findings)
    require_contains(license_text, "MIT License", "PAL_LICENSE_TEXT", PAL_JAR_NAME, findings)
    for entry in (
            "com/zigythebird/playeranim/api/PlayerAnimationFactory.class",
            "com/zigythebird/playeranim/api/PlayerAnimationAccess.class",
            "com/zigythebird/playeranim/animation/PlayerAnimationController.class"):
        if entry not in names:
            findings.append(Finding("PAL_API_CLASS", entry))


def validate_dependency_wiring(root: Path, findings: list[Finding]) -> None:
    build_path = root / "build.gradle"
    mods_path = root / "src/main/resources/META-INF/neoforge.mods.toml"
    if require_file(build_path, root, "PAL_BUILD_FILE_MISSING", findings):
        build = text(build_path)
        for needle, code in (
                (f"def palJarName = '{PAL_JAR_NAME}'", "PAL_BUILD_EXACT_FILENAME"),
                ("if (!palJar.isFile())", "PAL_BUILD_MISSING_GUARD"),
                ("Required Player Animation Library jar is missing", "PAL_BUILD_CLEAR_ERROR"),
                ("implementation files(palJar)", "PAL_BUILD_LOCAL_DEPENDENCY")):
            require_contains(build, needle, code, "build.gradle", findings)
        for forbidden in ("shadowJar", "jarJar", "zipTree(palJar)", "from(palJar)"):
            if forbidden in build:
                findings.append(Finding("PAL_SHADING_FORBIDDEN", forbidden))

    if require_file(mods_path, root, "PAL_MODS_TOML_MISSING", findings):
        mods = text(mods_path)
        for needle, code in (
                ('modId = "player_animation_library"', "PAL_DEPENDENCY_MOD_ID"),
                ('versionRange = "[1.1.4,1.2)"', "PAL_DEPENDENCY_RANGE"),
                ('side = "BOTH"', "PAL_DEPENDENCY_SIDE")):
            require_contains(mods, needle, code, "neoforge.mods.toml", findings)


def validate_client_boundary(root: Path, findings: list[Finding]) -> None:
    java_root = root / "src/main/java"
    test_root = root / "src/test/java"
    client_combat = java_root / "com/example/myvillage/client/combat"
    client_combat_tests = test_root / "com/example/myvillage/client/combat"
    controller_path = client_combat / "CombatAnimationController.java"
    bootstrap_path = client_combat / "ClientCombatBootstrap.java"
    smoke_path = client_combat / "ClientPalSmokeEvents.java"
    first_person_animator_path = client_combat / "QingfengFirstPersonAnimator.java"
    swing_path = client_combat / "FirstPersonSwing.java"
    swing_resources_path = client_combat / "FirstPersonSwingResources.java"
    first_person_transform_path = client_combat / "FirstPersonSwordTransform.java"
    swing_clock_path = client_combat / "SwingClock.java"
    first_person_trail_path = client_combat / "FirstPersonSwordTrail.java"
    world_trail_path = client_combat / "CombatWorldTrails.java"
    arm_renderer_path = client_combat / "QingfengFirstPersonArmRenderer.java"
    arm_ik_path = client_combat / "QingfengFirstPersonArmIk.java"
    arm_model_path = client_combat / "QingfengFirstPersonArmModel.java"
    arm_lag_path = client_combat / "FirstPersonArmLag.java"
    sword_geometry_path = client_combat / "SwordGeometry.java"
    camera_fx_path = client_combat / "CombatCameraFx.java"
    impact_fx_path = client_combat / "CombatImpactFx.java"
    render_types_path = client_combat / "CombatRenderTypes.java"
    blade_cut_particle_path = client_combat / "BladeCutParticle.java"
    swing_test_path = client_combat_tests / "FirstPersonSwingTest.java"
    swing_clock_test_path = client_combat_tests / "SwingClockTest.java"
    arm_test_path = client_combat_tests / "FirstPersonArmIkTest.java"

    for path, code in (
            (controller_path, "PAL_CONTROLLER_MISSING"),
            (bootstrap_path, "PAL_BOOTSTRAP_MISSING"),
            (smoke_path, "PAL_SMOKE_ENTRY_MISSING"),
            (first_person_animator_path, "COMBAT_FIRST_PERSON_ANIMATOR_MISSING"),
            (swing_path, "COMBAT_FIRST_PERSON_SWING_MISSING"),
            (swing_resources_path, "COMBAT_FIRST_PERSON_SWING_LOADER_MISSING"),
            (first_person_transform_path, "COMBAT_FIRST_PERSON_TRANSFORM_MISSING"),
            (swing_clock_path, "COMBAT_HIT_STOP_CLOCK_MISSING"),
            (first_person_trail_path, "COMBAT_FIRST_PERSON_TRAIL_MISSING"),
            (world_trail_path, "COMBAT_WORLD_TRAIL_MISSING"),
            (arm_renderer_path, "COMBAT_FIRST_PERSON_ARM_MISSING"),
            (arm_ik_path, "COMBAT_FIRST_PERSON_ARM_MISSING"),
            (arm_model_path, "COMBAT_FIRST_PERSON_ARM_MISSING"),
            (arm_lag_path, "COMBAT_FIRST_PERSON_WRIST_LAG_MISSING"),
            (sword_geometry_path, "COMBAT_SWORD_GEOMETRY_LOADER_MISSING"),
            (arm_test_path, "COMBAT_FIRST_PERSON_ARM_TEST_MISSING"),
            (camera_fx_path, "COMBAT_CAMERA_FX_MISSING"),
            (impact_fx_path, "COMBAT_IMPACT_FX_MISSING"),
            (render_types_path, "COMBAT_TRAIL_RENDER_TYPE_MISSING"),
            (blade_cut_particle_path, "COMBAT_BLADE_CUT_PROVIDER_MISSING"),
            (swing_test_path, "COMBAT_FIRST_PERSON_SWING_TEST_MISSING"),
            (swing_clock_test_path, "COMBAT_HIT_STOP_CLOCK_TEST_MISSING")):
        require_file(path, root, code, findings)

    if controller_path.is_file():
        controller = text(controller_path)
        for needle, code in (
                ("PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory", "PAL_FACTORY_REGISTRATION"),
                ("LAYER_PRIORITY = 1600", "PAL_LAYER_PRIORITY"),
                ("triggerAnimation(animationId", "PAL_PLAY_ADAPTER"),
                ("replaceAnimationWithFade", "PAL_TRANSITION_ADAPTER"),
                ("stopTriggeredAnimation", "PAL_STOP_ADAPTER"),
                ("forceAnimationReset", "PAL_POSE_RESET_ADAPTER"),
                ("FirstPersonMode.DISABLED", "PAL_CUSTOM_FIRST_PERSON_DISABLED"),
                ("QingfengFirstPersonAnimator.play(player, animationId, elapsedTicks)",
                 "COMBAT_FIRST_PERSON_PLAY_WIRING"),
                ("QingfengFirstPersonAnimator.stop(player)",
                 "COMBAT_FIRST_PERSON_STOP_WIRING")):
            require_contains(controller, needle, code, controller_path.name, findings)

    if bootstrap_path.is_file():
        bootstrap = text(bootstrap_path)
        for needle, code in (
                ("dist = Dist.CLIENT", "PAL_PHYSICAL_CLIENT_GUARD"),
                ("FMLClientSetupEvent", "PAL_CLIENT_SETUP_EVENT"),
                ("event.enqueueWork(CombatAnimationController::registerFactory)", "PAL_ENQUEUE_WORK"),
                ("RegisterClientExtensionsEvent", "COMBAT_FIRST_PERSON_EXTENSION_EVENT"),
                ("event.registerItem(QingfengFirstPersonAnimator.INSTANCE, "
                 "ModItems.QINGFENG_SWORD.get())",
                 "COMBAT_FIRST_PERSON_EXTENSION_REGISTRATION"),
                ("event.registerReloadListener(FirstPersonSwingResources.INSTANCE)",
                 "COMBAT_FIRST_PERSON_RIG_RELOAD"),
                ("NeoForge.EVENT_BUS.addListener(FirstPersonSwordTrail::onRenderHand)",
                 "COMBAT_FIRST_PERSON_TRAIL_REGISTRATION"),
                ("NeoForge.EVENT_BUS.addListener(CombatWorldTrails::onRenderLevelStage)",
                 "COMBAT_WORLD_TRAIL_REGISTRATION"),
                ("NeoForge.EVENT_BUS.addListener(QingfengFirstPersonArmRenderer::onRenderHand)",
                 "COMBAT_FIRST_PERSON_ARM_REGISTRATION"),
                ("NeoForge.EVENT_BUS.addListener(CombatCameraFx::onComputeCameraAngles)",
                 "COMBAT_CAMERA_FX_REGISTRATION"),
                ("NeoForge.EVENT_BUS.addListener(CombatCameraFx::onComputeFov)",
                 "COMBAT_CAMERA_FX_REGISTRATION"),
                ("NeoForge.EVENT_BUS.addListener(CombatCameraFx::onComputeFovModifier)",
                 "COMBAT_SLOW_FOV_CORRECTION_REGISTRATION"),
                ("NeoForge.EVENT_BUS.addListener(CombatImpactFx::onEntityTickPre)",
                 "COMBAT_IMPACT_FX_REGISTRATION"),
                ("event.registerSpriteSet(CombatParticles.BLADE_CUT.get()",
                 "COMBAT_BLADE_CUT_PROVIDER_REGISTRATION")):
            require_contains(bootstrap, needle, code, bootstrap_path.name, findings)
        arm_at = bootstrap.find("addListener(QingfengFirstPersonArmRenderer::onRenderHand)")
        trail_at = bootstrap.find("addListener(FirstPersonSwordTrail::onRenderHand)")
        if 0 <= trail_at < arm_at:
            # The arm draws first so the translucent trail blends over it.
            findings.append(Finding("COMBAT_FIRST_PERSON_ARM_ORDER", bootstrap_path.name))

    if first_person_animator_path.is_file():
        animator = text(first_person_animator_path)
        for needle, code in (
                ("implements IClientItemExtensions", "COMBAT_FIRST_PERSON_ITEM_EXTENSION"),
                ("applyForgeHandTransform(", "COMBAT_FIRST_PERSON_HAND_TRANSFORM"),
                ("BasicSwordStyle.DEFINITION.indexOf(animationId)",
                 "COMBAT_FIRST_PERSON_MOVE_MAPPING"),
                ("player.level().getGameTime() - Math.max(0.0F, elapsedTicks)",
                 "COMBAT_FIRST_PERSON_CORRECTED_TIMELINE"),
                ("Optional<Frame> currentFrame(LocalPlayer player, float partialTick)",
                 "COMBAT_FIRST_PERSON_SHARED_FRAME"),
                ("clock.visualTick(", "COMBAT_FIRST_PERSON_HIT_STOP_CLOCK"),
                ("ClientCombatState.mode() != CombatMode.CULTIVATION",
                 "COMBAT_FIRST_PERSON_MODE_GUARD"),
                ("arm != player.getMainArm()", "COMBAT_FIRST_PERSON_MAIN_HAND_GUARD"),
                ("FirstPersonSwingResources.current()", "COMBAT_FIRST_PERSON_RIG_SOURCE"),
                ("FirstPersonSwordTransform.apply(", "COMBAT_FIRST_PERSON_ITEM_TRANSFORM"),
                ("model.applyTransform(context, scratch, leftHand)", "COMBAT_FIRST_PERSON_DISPLAY_UNDO"),
                ("player.level().playLocalSound(", "COMBAT_LOCAL_SWING_SOUND"),
                ("visualTick + 1.5F < move.activeStartTick()", "COMBAT_LOCAL_SWING_SOUND_LEAD"),
                ("CombatSounds.jitteredSwingPitch(feedback.swingPitch(), player.getRandom())",
                 "COMBAT_LOCAL_SWING_SOUND_PITCH"),
                ("BasicSwordStyle.feedback(", "COMBAT_FIRST_PERSON_PER_MOVE_HIT_STOP")):
            require_contains(animator, needle, code, first_person_animator_path.name, findings)
        for forbidden in ("RenderHandEvent", "Camera", "PacketDistributor", "ServerboundSwingPacket"):
            if forbidden in animator:
                findings.append(Finding(
                    "COMBAT_FIRST_PERSON_AUTHORITY_OR_CAMERA_LEAK",
                    f"{first_person_animator_path.name}:{forbidden}"))

    if swing_path.is_file():
        swing = text(swing_path)
        for needle, code in (
                ('RESOURCE_PATH = "combat/qingfeng_first_person.json"', "COMBAT_FIRST_PERSON_RIG_PATH"),
                ("for (AttackMoveDefinition definition : style.moves())",
                 "COMBAT_FIRST_PERSON_RIG_SERVER_MOVES"),
                ("strike[0] > definition.activeStartTick()", "COMBAT_FIRST_PERSON_STRIKE_WINDOW"),
                ("strike[1] < definition.activeEndTick()", "COMBAT_FIRST_PERSON_STRIKE_WINDOW"),
                ("must start and end at the neutral hold", "COMBAT_FIRST_PERSON_NEUTRAL_ENDPOINTS")):
            require_contains(swing, needle, code, swing_path.name, findings)

    if first_person_transform_path.is_file():
        first_person_transform = text(first_person_transform_path)
        transform_order = (
            "poseStack.translate(",
            "Axis.ZP.rotationDegrees(side * pose.plane())",
            "Axis.YP.rotationDegrees(side * pose.sweep())",
            "poseStack.translate(0.0F, 0.0F, -pose.reach())",
            "Axis.YP.rotationDegrees(side * pose.lead())",
            "Axis.XP.rotationDegrees(pose.lift())",
            "Axis.YP.rotationDegrees(side * pose.twist())",
            "poseStack.mulPose(itemToGrip(swing.sword(), swing.rig().swordScale(), display))",
            "result.mul(new Matrix4f(display).invert())",
        )
        cursor = -1
        for token in transform_order:
            cursor = first_person_transform.find(token, cursor + 1)
            if cursor < 0:
                findings.append(Finding(
                    "COMBAT_FIRST_PERSON_TRANSFORM_ORDER",
                    f"{first_person_transform_path.name}:{token}"))
                break
        # The grip comes from the geometry contract and the baked display transform, never from
        # constants measured against one sword model.
        if re.search(r"\bGRIP_(?:ALIGN_PITCH|X|Y|Z)\b", first_person_transform):
            findings.append(Finding("COMBAT_FIRST_PERSON_HARDCODED_GRIP", first_person_transform_path.name))

    if swing_resources_path.is_file():
        swing_resources = text(swing_resources_path)
        for needle in ("GEOMETRY_LOCATION", "SwordGeometry.parse(", "FirstPersonSwing.parse(rigJson.get(), "
                       "BasicSwordStyle.DEFINITION, geometry)"):
            require_contains(
                swing_resources, needle, "COMBAT_SWORD_GEOMETRY_RELOAD", swing_resources_path.name, findings)

    if sword_geometry_path.is_file():
        require_contains(
            text(sword_geometry_path), 'RESOURCE_PATH = "combat/qingfeng_sword_geometry.json"',
            "COMBAT_SWORD_GEOMETRY_PATH", sword_geometry_path.name, findings)

    if swing_clock_path.is_file():
        clock = text(swing_clock_path)
        for needle, code in (
                ("HIT_STOP_TICKS = 2.5F", "COMBAT_HIT_STOP_DURATION"),
                ("(totalTicks - frozenAt) / (totalTicks - stopEnd)", "COMBAT_HIT_STOP_CATCH_UP")):
            require_contains(clock, needle, code, swing_clock_path.name, findings)

    if arm_renderer_path.is_file():
        arm = text(arm_renderer_path)
        require_contains(
            arm, "public static void onRenderHand(RenderHandEvent event)",
            "COMBAT_FIRST_PERSON_ARM_EVENT", arm_renderer_path.name, findings)
        if "setCanceled" in arm:
            # The arm is drawn beside the vanilla item pass; cancelling would hide the sword.
            findings.append(Finding("COMBAT_FIRST_PERSON_ARM_CANCELS_HAND", arm_renderer_path.name))
        for needle, code in (
                ("model.fist(sleeve)", "COMBAT_FIRST_PERSON_FIST"),
                ("solution.fistRotation()", "COMBAT_FIRST_PERSON_FIST"),
                ("FirstPersonArmLag.offset(", "COMBAT_FIRST_PERSON_WRIST_LAG")):
            require_contains(arm, needle, code, arm_renderer_path.name, findings)
        for forbidden in ("PacketDistributor", "setDeltaMovement", "hurtMarked", ".hurt("):
            if forbidden in arm:
                findings.append(Finding("COMBAT_PRESENTATION_AUTHORITY_LEAK", f"{arm_renderer_path.name}:{forbidden}"))

    if arm_ik_path.is_file():
        arm_ik = text(arm_ik_path)
        for needle, code in (
                ("pose.gripRoll()", "COMBAT_FIRST_PERSON_GRIP_ROLL"),
                ("pose.elbow()", "COMBAT_FIRST_PERSON_ELBOW_POLE"),
                ("withinLimits(", "COMBAT_FIRST_PERSON_WRIST_LIMITS")):
            require_contains(arm_ik, needle, code, arm_ik_path.name, findings)

    if camera_fx_path.is_file():
        camera_fx = text(camera_fx_path)
        for needle, code in (
                ("screenEffectScale()", "COMBAT_CAMERA_FX_ACCESSIBILITY_SCALE"),
                ("fovEffectScale()", "COMBAT_CAMERA_FX_ACCESSIBILITY_SCALE"),
                ("CombatSessionManager.COMMIT_MODIFIER_ID", "COMBAT_SLOW_FOV_CORRECTION"),
                ("CombatReactionService.STUN_MODIFIER_ID", "COMBAT_SLOW_FOV_CORRECTION"),
                ("public static void onComputeFovModifier(ComputeFovModifierEvent event)",
                 "COMBAT_SLOW_FOV_CORRECTION"),
                ("event.setNewFovModifier(", "COMBAT_SLOW_FOV_CORRECTION")):
            require_contains(camera_fx, needle, code, camera_fx_path.name, findings)

    for path in (camera_fx_path, impact_fx_path):
        if not path.is_file():
            continue
        content = text(path)
        for forbidden in ("PacketDistributor", "setDeltaMovement", "hurtMarked", ".hurt("):
            if forbidden in content:
                findings.append(Finding(
                    "COMBAT_PRESENTATION_AUTHORITY_LEAK", f"{path.name}:{forbidden}"))

    if render_types_path.is_file():
        render_types = text(render_types_path)
        require_contains(
            render_types, "SWORD_TRAIL_TRANSLUCENT = RenderType.create(",
            "COMBAT_TRAIL_TRANSLUCENT", render_types_path.name, findings)
        require_contains(
            render_types, "TRANSLUCENT_TRANSPARENCY",
            "COMBAT_TRAIL_TRANSLUCENT", render_types_path.name, findings)
        if (re.search(r"\b(?:LIGHTNING|ADDITIVE)_TRANSPARENCY\b", render_types)
                or re.search(r"DestFactor\.ONE\b", render_types)):
            findings.append(Finding("COMBAT_TRAIL_ADDITIVE_BLEND", render_types_path.name))
    for path in (first_person_trail_path, world_trail_path):
        if not path.is_file():
            continue
        content = text(path)
        require_contains(
            content, "CombatRenderTypes.SWORD_TRAIL_TRANSLUCENT",
            "COMBAT_TRAIL_TRANSLUCENT", path.name, findings)
        if re.search(r"\bSWORD_TRAIL\b", content):
            findings.append(Finding("COMBAT_TRAIL_ADDITIVE_BLEND", path.name))

    if first_person_trail_path.is_file():
        trail = text(first_person_trail_path)
        for needle, code in (
                ("public static void onRenderHand(RenderHandEvent event)", "COMBAT_FIRST_PERSON_TRAIL_EVENT"),
                ("event.getHand() != InteractionHand.MAIN_HAND", "COMBAT_FIRST_PERSON_TRAIL_MAIN_HAND"),
                ("ClientCombatState.mode() != CombatMode.CULTIVATION", "COMBAT_FIRST_PERSON_TRAIL_MODE_GUARD"),
                (".currentFrame(player, event.getPartialTick())", "COMBAT_FIRST_PERSON_TRAIL_SHARED_FRAME"),
                ("FirstPersonSwordTransform.swordPoint(", "COMBAT_FIRST_PERSON_TRAIL_SHARED_TRANSFORM"),
                ("sword.bladeBase()", "COMBAT_FIRST_PERSON_TRAIL_GEOMETRY"),
                ("sword.bladeTip()", "COMBAT_FIRST_PERSON_TRAIL_GEOMETRY"),
                ("move.strikeEndTick()", "COMBAT_FIRST_PERSON_TRAIL_STRIKE_WINDOW")):
            require_contains(trail, needle, code, first_person_trail_path.name, findings)
        if "event.setCanceled" in trail:
            findings.append(Finding("COMBAT_FIRST_PERSON_TRAIL_ITEM_PASS_CANCEL", first_person_trail_path.name))
        if re.search(r"\bBLADE_(?:BASE|TIP)\s*=\s*new Vector3f", trail):
            findings.append(Finding("COMBAT_FIRST_PERSON_TRAIL_HARDCODED_BLADE", first_person_trail_path.name))
        for forbidden in ("getGameTime", "actionStartTick"):
            if forbidden in trail:
                findings.append(Finding(
                    "COMBAT_FIRST_PERSON_TRAIL_DUPLICATE_TIMELINE",
                    f"{first_person_trail_path.name}:{forbidden}"))

    if world_trail_path.is_file():
        world_trail = text(world_trail_path)
        for needle, code in (
                ("move.hitbox().samples()", "COMBAT_WORLD_TRAIL_HITBOX_SOURCE"),
                ("camera.isDetached()", "COMBAT_WORLD_TRAIL_FIRST_PERSON_SKIP"),
                ("action.facingYaw()", "COMBAT_WORLD_TRAIL_SERVER_FACING"),
                ("drawnBladeLength(", "COMBAT_WORLD_TRAIL_BLADE_LENGTH")):
            require_contains(world_trail, needle, code, world_trail_path.name, findings)

    for path, needles in (
            (swing_test_path, (
                ("visibleStrikeCoversTheServerActiveWindow", "COMBAT_FIRST_PERSON_STRIKE_TEST"),
                ("bladeTravelsMostDuringTheStrike", "COMBAT_FIRST_PERSON_STRIKE_SPEED_TEST"),
                ("gripStaysInFrontOfTheCameraForBothHands", "COMBAT_FIRST_PERSON_NEAR_PLANE_TEST"),
                ("invalidRigsAreRejected", "COMBAT_FIRST_PERSON_RIG_NEGATIVE_TEST"),
                ("swordGripLandsOnTheGripFrameWhateverTheDisplayTransform", "COMBAT_FIRST_PERSON_GRIP_TEST"))),
            (arm_test_path, (
                ("wristStaysAnatomicalAndBonesKeepTheirLength", "COMBAT_FIRST_PERSON_WRIST_TEST"),
                ("fistClosesAroundTheHandleWithGuardAndPommelOutside", "COMBAT_FIRST_PERSON_FIST_TEST"),
                ("forearmAndUpperArmNeverCrossTheBlade", "COMBAT_FIRST_PERSON_BLADE_CLEARANCE_TEST"),
                ("cutsTrailTheArmThenFollowThrough", "COMBAT_FIRST_PERSON_WRIST_LAG_TEST"))),
            (swing_clock_test_path, (
                ("hitStopSlowsThenCatchesUpToTheServerTotal", "COMBAT_HIT_STOP_CATCH_UP_TEST"),))):
        if path.is_file():
            content = text(path)
            for needle, code in needles:
                require_contains(content, needle, code, path.name, findings)

    client_sources = sorted(client_combat.glob("*.java")) if client_combat.is_dir() else []
    for path in client_sources:
        content = text(path)
        for forbidden in ("yesman.epicfight", "software.bernie.geckolib"):
            if forbidden in content:
                findings.append(Finding(
                    "COMBAT_FIRST_PERSON_THIRD_PARTY_RIG_FORBIDDEN", f"{path.name}:{forbidden}"))

    if java_root.is_dir():
        for path in java_root.rglob("*.java"):
            content = text(path)
            if "com.zigythebird." not in content:
                continue
            try:
                path.relative_to(client_combat)
            except ValueError:
                findings.append(Finding(
                    "PAL_IMPORT_OUTSIDE_CLIENT_COMBAT",
                    path.relative_to(root).as_posix()))

    common_roots = (
        java_root / "com/example/myvillage/combat",
        java_root / "com/example/myvillage/network",
    )
    for common_root in common_roots:
        if not common_root.is_dir():
            continue
        for path in common_root.rglob("*.java"):
            content = text(path)
            if "import net.minecraft.client" in content or "import com.example.myvillage.client" in content:
                findings.append(Finding("CLIENT_IMPORT_IN_COMMON_COMBAT", path.relative_to(root).as_posix()))


def validate_animation_resource(root: Path, findings: list[Finding]) -> None:
    animation_path = (
        root / "src/main/resources/assets/myvillage/player_animations/sword_combat.json"
    )
    if not require_file(animation_path, root, "PAL_ANIMATION_RESOURCE_MISSING", findings):
        return
    try:
        data = json.loads(text(animation_path))
    except json.JSONDecodeError as exc:
        findings.append(Finding("PAL_ANIMATION_JSON", str(exc)))
        return

    if data.get("format_version") != "1.8.0":
        findings.append(Finding("PAL_ANIMATION_FORMAT", str(data.get("format_version"))))
    animation = data.get("animations", {}).get("sword_mode_enter")
    if not isinstance(animation, dict):
        findings.append(Finding("PAL_SMOKE_ANIMATION_ID", "sword_mode_enter"))
        return
    if not isinstance(animation.get("animation_length"), (int, float)):
        findings.append(Finding("PAL_SMOKE_ANIMATION_LENGTH", "sword_mode_enter"))
    bones = animation.get("bones")
    if not isinstance(bones, dict) or not REQUIRED_ANIMATION_BONES.issubset(bones):
        findings.append(Finding(
            "PAL_SMOKE_FULL_BODY", ",".join(sorted(REQUIRED_ANIMATION_BONES))))

    animations = data.get("animations", {})
    if set(animations) != REQUIRED_ANIMATION_IDS:
        findings.append(Finding(
            "COMBAT_ANIMATION_ID_SET",
            f"expected={sorted(REQUIRED_ANIMATION_IDS)},actual={sorted(animations)}"))
    ready = animations.get("sword_ready_idle")
    if (not isinstance(ready, dict) or ready.get("loop") is not True
            or ready.get("animation_length") != 1.2):
        findings.append(Finding("COMBAT_READY_IDLE_LOOP", "sword_ready_idle"))
    for move_id, values in EXPECTED_MOVES.items():
        expected_length = values[-1]
        move = animations.get(move_id)
        if not isinstance(move, dict):
            findings.append(Finding("COMBAT_ANIMATION_ID", move_id))
            continue
        actual_length = move.get("animation_length")
        if not isinstance(actual_length, (int, float)) or abs(actual_length - expected_length) > 1.0e-6:
            findings.append(Finding(
                "COMBAT_ANIMATION_LENGTH",
                f"{move_id}:expected={expected_length},actual={actual_length}"))
        move_bones = move.get("bones")
        if not isinstance(move_bones, dict) or not REQUIRED_ANIMATION_BONES.issubset(move_bones):
            findings.append(Finding("COMBAT_ANIMATION_FULL_BODY", move_id))
            continue
        active_start, active_end = values[1], values[2]
        active_key_present = False
        for bone in REQUIRED_ANIMATION_BONES:
            rotation = move_bones.get(bone, {}).get("rotation", {})
            if not isinstance(rotation, dict):
                continue
            times = []
            for key in rotation:
                try:
                    times.append(float(key) * 20.0)
                except (TypeError, ValueError):
                    pass
            active_key_present |= any(active_start <= tick <= active_end for tick in times)
            if 0.0 not in times or not any(abs(tick - values[0]) <= 1.0e-6 for tick in times):
                findings.append(Finding("COMBAT_ANIMATION_RECOVERY", f"{move_id}:{bone}"))
        if not active_key_present:
            findings.append(Finding("COMBAT_ANIMATION_ACTIVE_ALIGNMENT", move_id))
    fifth_body = animations.get("basic_sword_05_lunge_thrust", {}).get("bones", {}).get("body", {})
    fifth_positions = fifth_body.get("position", {}) if isinstance(fifth_body, dict) else {}
    if not isinstance(fifth_positions, dict) or not any(
            isinstance(vector, list) and any(value != 0 for value in vector)
            for vector in fifth_positions.values()):
        findings.append(Finding("COMBAT_FIFTH_STEP_POSE", "basic_sword_05_lunge_thrust"))


def validate_qingfeng_item(root: Path, findings: list[Finding]) -> None:
    items_path = root / "src/main/java/com/example/myvillage/item/ModItems.java"
    if not require_file(items_path, root, "QINGFENG_ITEMS_FILE", findings):
        return
    items = text(items_path)
    for needle, code in (
            ("DeferredItem<SwordItem> QINGFENG_SWORD", "QINGFENG_REGISTRATION_TYPE"),
            ('ITEMS.registerItem("qingfeng_sword"', "QINGFENG_REGISTRATION_ID"),
            ("Tiers.DIAMOND", "QINGFENG_DIAMOND_TIER"),
            ("SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F)", "QINGFENG_ATTRIBUTES"),
            ("output.accept(QINGFENG_SWORD.get())", "QINGFENG_CREATIVE_TAB")):
        require_contains(items, needle, code, items_path.name, findings)
    rideable = items.find("output.accept(RIDEABLE_FLYING_SWORD.get())")
    qingfeng = items.find("output.accept(QINGFENG_SWORD.get())")
    spirit = items.find("output.accept(LOW_GRADE_SPIRIT_STONE.get())")
    if not (0 <= rideable < qingfeng < spirit):
        findings.append(Finding("QINGFENG_CREATIVE_ORDER", "rideable -> qingfeng -> spirit stone"))

    model_path = root / "src/main/resources/assets/myvillage/models/item/qingfeng_sword.json"
    texture_path = root / "src/main/resources/assets/myvillage/textures/item/qingfeng_sword.png"
    recipe_path = root / "src/main/resources/data/myvillage/recipe/qingfeng_sword.json"
    tag_path = root / "src/main/resources/data/minecraft/tags/item/swords.json"
    en_path = root / "src/main/resources/assets/myvillage/lang/en_us.json"
    zh_path = root / "src/main/resources/assets/myvillage/lang/zh_cn.json"
    for path, code in (
            (model_path, "QINGFENG_MODEL"),
            (texture_path, "QINGFENG_TEXTURE"),
            (recipe_path, "QINGFENG_RECIPE"),
            (tag_path, "QINGFENG_SWORD_TAG"),
            (en_path, "QINGFENG_EN_LANG"),
            (zh_path, "QINGFENG_ZH_LANG")):
        require_file(path, root, code, findings)

    if model_path.is_file():
        validate_qingfeng_model(root, json.loads(text(model_path)), findings)
    if texture_path.is_file():
        data = texture_path.read_bytes()
        if (not data.startswith(b"\x89PNG\r\n\x1a\n")
                or data[12:16] != b"IHDR"
                or struct.unpack(">II", data[16:24]) != (64, 64)):
            findings.append(Finding("QINGFENG_TEXTURE_DIMENSIONS", "expected 64x64 PNG"))
        elif data[25] not in (4, 6):
            findings.append(Finding("QINGFENG_TEXTURE_ALPHA", f"png color type={data[25]}"))
    if recipe_path.is_file():
        recipe = json.loads(text(recipe_path))
        if (recipe.get("pattern") != ["D", "D", "S"]
                or recipe.get("key", {}).get("D", {}).get("item") != "minecraft:diamond"
                or recipe.get("key", {}).get("S", {}).get("item") != "minecraft:stick"
                or recipe.get("result", {}).get("id") != "myvillage:qingfeng_sword"):
            findings.append(Finding("QINGFENG_RECIPE_CONTRACT", recipe_path.name))
    if tag_path.is_file():
        tag = json.loads(text(tag_path))
        if "myvillage:qingfeng_sword" not in tag.get("values", []):
            findings.append(Finding("QINGFENG_SWORD_TAG_CONTRACT", tag_path.name))
    for path, item_name, code in (
            (en_path, "Qingfeng Sword", "QINGFENG_EN_NAME"),
            (zh_path, "青锋剑", "QINGFENG_ZH_NAME")):
        if not path.is_file():
            continue
        language = json.loads(text(path))
        if language.get("item.myvillage.qingfeng_sword") != item_name:
            findings.append(Finding(code, item_name))
        missing_translations = sorted(COMBAT_TRANSLATIONS - language.keys())
        if missing_translations:
            findings.append(Finding(
                "COMBAT_TRANSLATIONS", f"{path.name}:{','.join(missing_translations)}"))


def validate_qingfeng_model(root: Path, model: dict, findings: list[Finding]) -> None:
    """3D jian in hand (separate_transforms base), 2D icon in the GUI, geometry contract for the grip."""
    icon = {"parent": "minecraft:item/handheld", "textures": {"layer0": "myvillage:item/qingfeng_sword"}}
    perspectives = model.get("perspectives") if isinstance(model, dict) else None
    if (not isinstance(model, dict) or model.get("loader") != "neoforge:separate_transforms"
            or model.get("base") != {"parent": "myvillage:item/qingfeng_sword_3d"}
            or not isinstance(perspectives, dict) or perspectives.get("gui") != icon):
        findings.append(Finding("QINGFENG_MODEL_CONTRACT", "separate_transforms: 3D base, 2D gui icon"))
    model_3d_path = root / QINGFENG_MODEL_3D
    if require_file(model_3d_path, root, "QINGFENG_MODEL_3D", findings):
        try:
            model_3d = json.loads(text(model_3d_path))
        except json.JSONDecodeError as exc:
            findings.append(Finding("QINGFENG_MODEL_3D", str(exc)))
        else:
            display = model_3d.get("display", {}) if isinstance(model_3d, dict) else {}
            if (not isinstance(model_3d, dict) or "parent" in model_3d
                    or not model_3d.get("elements")
                    or model_3d.get("textures", {}).get("sword") != "myvillage:item/qingfeng_sword_model"
                    or not all(context in display for context in (
                        "thirdperson_righthand", "thirdperson_lefthand",
                        "firstperson_righthand", "firstperson_lefthand"))
                    or "gui" in display):
                findings.append(Finding("QINGFENG_MODEL_3D", "elements, own display, texture qingfeng_sword_model"))
    texture_path = root / QINGFENG_MODEL_TEXTURE
    if require_file(texture_path, root, "QINGFENG_MODEL_TEXTURE", findings):
        data = texture_path.read_bytes()
        if not data.startswith(b"\x89PNG\r\n\x1a\n") or data[12:16] != b"IHDR":
            findings.append(Finding("QINGFENG_MODEL_TEXTURE", "expected PNG"))
    geometry_path = root / QINGFENG_GEOMETRY
    if require_file(geometry_path, root, "QINGFENG_GEOMETRY_CONTRACT", findings):
        try:
            geometry = json.loads(text(geometry_path))
        except json.JSONDecodeError as exc:
            findings.append(Finding("QINGFENG_GEOMETRY_CONTRACT", str(exc)))
            return
        missing = [f for f in QINGFENG_GEOMETRY_FIELDS if f not in geometry]
        if missing or geometry.get("units") != "model_pixels":
            findings.append(Finding("QINGFENG_GEOMETRY_CONTRACT", "missing " + ",".join(missing or ["units"])))
            return
        try:
            grip_y = float(geometry["grip_center"][1])
            handle = [float(v) for v in geometry["handle"]["y"]]
            guard = [float(v) for v in geometry["guard"]["y"]]
            pommel = [float(v) for v in geometry["pommel"]["y"]]
            base_y, tip_y = float(geometry["blade_base"][1]), float(geometry["blade_tip"][1])
        except (KeyError, IndexError, TypeError, ValueError) as exc:
            findings.append(Finding("QINGFENG_GEOMETRY_CONTRACT", f"malformed: {exc}"))
            return
        axes = geometry.get("axes", {})
        if (not pommel[1] <= handle[0] < grip_y < handle[1] <= guard[0] < guard[1] <= base_y < tip_y
                or not isinstance(axes, dict)
                or (axes.get("blade"), axes.get("flat_normal"), axes.get("edge")) != ("+y", "x", "z")):
            findings.append(Finding("QINGFENG_GEOMETRY_CONTRACT", "pommel<handle<guard<blade, axes +y/x/z"))


def validate_preference_and_payloads(root: Path, findings: list[Finding]) -> None:
    combat_root = root / "src/main/java/com/example/myvillage/combat"
    required = {
        "CombatMode.java": "COMBAT_MODE",
        "CombatPreference.java": "COMBAT_PREFERENCE",
        "CombatAttachments.java": "COMBAT_ATTACHMENT",
        "CombatService.java": "COMBAT_SERVICE",
    }
    for name, code in required.items():
        require_file(combat_root / name, root, code, findings)
    if (combat_root / "CombatAttachments.java").is_file():
        attachments = text(combat_root / "CombatAttachments.java")
        for needle, code in (
                ('register("combat_preference"', "COMBAT_ATTACHMENT_ID"),
                (".serialize(CombatPreference.CODEC)", "COMBAT_ATTACHMENT_CODEC"),
                (".copyOnDeath()", "COMBAT_ATTACHMENT_COPY_ON_DEATH")):
            require_contains(attachments, needle, code, "CombatAttachments.java", findings)

    cultivation_profile = root / "src/main/java/com/example/myvillage/cultivation/CultivationProfile.java"
    if cultivation_profile.is_file():
        profile = text(cultivation_profile).lower()
        for forbidden in ("combatmode", "comboindex", "actiontick", "attackrevision"):
            if forbidden in profile:
                findings.append(Finding("COMBAT_STATE_IN_CULTIVATION_PROFILE", forbidden))

    network_root = combat_root / "network"
    for payload_name in ("CombatModeTogglePayload", "SwordAttackIntentPayload"):
        path = network_root / f"{payload_name}.java"
        if not require_file(path, root, f"{payload_name.upper()}_MISSING", findings):
            continue
        payload = text(path)
        if re.search(rf"record\s+{payload_name}\s*\(\s*\)", payload) is None:
            findings.append(Finding("COMBAT_C2S_AUTHORITY_FIELD", payload_name))
        for forbidden in (
                "moveId", "comboIndex", "targetEntity", "damage", "hitbox",
                "position", "velocity", "endpoint", "completion", "clientTick"):
            if forbidden in payload:
                findings.append(Finding("COMBAT_C2S_AUTHORITY_FIELD", f"{payload_name}:{forbidden}"))

    mod_payloads = root / "src/main/java/com/example/myvillage/network/ModPayloads.java"
    if require_file(mod_payloads, root, "COMBAT_PAYLOAD_REGISTRAR", findings):
        content = text(mod_payloads)
        require_contains(content, 'PROTOCOL_VERSION = "6"', "COMBAT_PROTOCOL_VERSION", "ModPayloads", findings)
        require_contains(content, "CombatPayloads.register(registrar)", "COMBAT_PAYLOAD_REGISTRATION", "ModPayloads", findings)

    payloads_path = network_root / "CombatPayloads.java"
    if require_file(payloads_path, root, "COMBAT_PAYLOADS_MISSING", findings):
        content = text(payloads_path)
        if re.search(r"playToClient\(\s*CombatImpactPayload\.TYPE", content) is None:
            findings.append(Finding("COMBAT_IMPACT_S2C_ONLY", "CombatImpactPayload not registered playToClient"))
        if re.search(r"playToServer\(\s*CombatImpactPayload\.TYPE", content) is not None:
            findings.append(Finding("COMBAT_IMPACT_S2C_ONLY", "CombatImpactPayload registered playToServer"))
    impact_path = network_root / "CombatImpactPayload.java"
    if require_file(impact_path, root, "COMBAT_IMPACT_PAYLOAD_MISSING", findings):
        content = text(impact_path)
        components = re.search(r"record\s+CombatImpactPayload\s*\((.*?)\)\s*implements", content, re.DOTALL)
        if components is None:
            findings.append(Finding("COMBAT_IMPACT_PAYLOAD_SHAPE", impact_path.name))
        else:
            lowered = components.group(1).lower()
            for forbidden in ("damage", "health", "amount", "knockback", "velocity", "motion"):
                if forbidden in lowered:
                    findings.append(Finding("COMBAT_IMPACT_AUTHORITY_FIELD", forbidden))
    receiver_path = network_root / "CombatAttackReceiver.java"
    if require_file(receiver_path, root, "COMBAT_ATTACK_RECEIVER_MISSING", findings):
        require_contains(
            text(receiver_path), "public static void receiveImpact(CombatImpactPayload payload)",
            "COMBAT_IMPACT_RECEIVER", receiver_path.name, findings)

    client_input = root / "src/main/java/com/example/myvillage/client/combat/ClientCombatEvents.java"
    client_state = root / "src/main/java/com/example/myvillage/client/combat/ClientCombatState.java"
    key_mapping = root / "src/main/java/com/example/myvillage/client/combat/ClientCombatKeyMappings.java"
    if require_file(client_input, root, "COMBAT_CLIENT_INPUT", findings):
        content = text(client_input)
        for needle, code in (
                ("InputEvent.InteractionKeyMappingTriggered", "COMBAT_MAPPED_ATTACK_EVENT"),
                ("event.isAttack()", "COMBAT_ATTACK_ACTION_CHECK"),
                ("event.setCanceled(true)", "COMBAT_ATTACK_CANCEL"),
                ("event.setSwingHand(false)", "COMBAT_SWING_SUPPRESSION"),
                ("player.swing(InteractionHand.MAIN_HAND, false)",
                 "COMBAT_LOCAL_FIRST_PERSON_FEEDBACK"),
                ("localPredictionPending", "COMBAT_BUFFERED_FIRST_PERSON_FEEDBACK"),
                ("SwordAttackIntentPayload.INSTANCE", "COMBAT_EMPTY_ATTACK_INTENT"),
                ("ClientCombatState.mode() != CombatMode.CULTIVATION", "COMBAT_MODE_GUARD"),
                ("ModItems.QINGFENG_SWORD.get()", "COMBAT_ITEM_GUARD"),
                ("ClientCombatState.localActionActive()", "COMBAT_PREDICTION_DISPOSABLE"),
                ("ClientCultivationState.meditation()", "COMBAT_READY_CULTIVATION_GUARD"),
                ("resetsServerSession", "COMBAT_CLIENT_SESSION_RESET"),
                ("ClientCombatEvents::receiveImpact", "COMBAT_IMPACT_CLIENT_WIRING"),
                ("ClientCombatState.bufferClick(", "COMBAT_CLIENT_ONE_SLOT_BUFFER"),
                ("ClientCombatState.chainDue(", "COMBAT_CLIENT_CHAIN_PREDICTION"),
                ("player.setSprinting(false);", "COMBAT_CLIENT_PREDICTED_SPRINT_STOP")):
            require_contains(content, needle, code, client_input.name, findings)
        if "GLFW_MOUSE_BUTTON" in content or "matchesMouse" in content:
            findings.append(Finding("COMBAT_PHYSICAL_MOUSE_BINDING", client_input.name))
        if ("player.swing(InteractionHand.MAIN_HAND);" in content
                or "ServerboundSwingPacket" in content):
            findings.append(Finding("COMBAT_SERVERBOUND_VANILLA_SWING", client_input.name))
    if require_file(key_mapping, root, "COMBAT_MODE_KEY_MAPPING", findings):
        content = text(key_mapping)
        require_contains(content, "GLFW.GLFW_KEY_R", "COMBAT_MODE_DEFAULT_R", key_mapping.name, findings)
    if require_file(client_state, root, "COMBAT_CLIENT_STATE", findings):
        content = text(client_state)
        require_contains(
            content, "preferenceRevision = revision;\n        ACTION_REVISIONS.clear();",
            "COMBAT_CLIENT_WORLD_REVISION_RESET",
            client_state.name, findings)
        require_contains(
            content, "resetActionRevision", "COMBAT_CLIENT_ENTITY_REVISION_RESET",
            client_state.name, findings)


def validate_definitions_and_runtime(root: Path, findings: list[Finding]) -> None:
    definition = root / "src/main/java/com/example/myvillage/combat/definition/BasicSwordStyle.java"
    session = root / "src/main/java/com/example/myvillage/combat/session/CombatSession.java"
    manager = root / "src/main/java/com/example/myvillage/combat/session/CombatSessionManager.java"
    geometry = root / "src/main/java/com/example/myvillage/combat/runtime/CombatGeometry.java"
    resolver = root / "src/main/java/com/example/myvillage/combat/runtime/CombatHitResolver.java"
    step = root / "src/main/java/com/example/myvillage/combat/runtime/CombatStepService.java"
    damage = root / "src/main/java/com/example/myvillage/combat/runtime/CombatDamageService.java"
    debug = root / "src/main/java/com/example/myvillage/combat/runtime/CombatDebugService.java"
    events = root / "src/main/java/com/example/myvillage/combat/CombatEvents.java"
    meditation = root / "src/main/java/com/example/myvillage/cultivation/meditation/MeditationManager.java"
    for path, code in (
            (definition, "COMBAT_DEFINITION_OWNER"),
            (session, "COMBAT_SESSION_MACHINE"),
            (manager, "COMBAT_SESSION_MANAGER"),
            (geometry, "COMBAT_GEOMETRY"),
            (resolver, "COMBAT_HIT_RESOLVER"),
            (step, "COMBAT_STEP_SERVICE"),
            (damage, "COMBAT_DAMAGE_SERVICE"),
            (debug, "COMBAT_DEBUG_SERVICE"),
            (events, "COMBAT_LIFECYCLE_EVENTS"),
            (meditation, "COMBAT_MEDITATION_INTEGRATION")):
        require_file(path, root, code, findings)

    if definition.is_file():
        content = text(definition)
        for move_id, values in EXPECTED_MOVES.items():
            total, active_start, active_end, multiplier, maximum, attack_range, _ = values
            pattern = re.compile(
                rf'"{re.escape(move_id)}".*?{total},\s*{active_start},\s*{active_end},\s*'
                rf'{multiplier:.2f},\s*{maximum},\s*{attack_range:.1f}',
                re.DOTALL)
            if pattern.search(content) is None:
                findings.append(Finding("COMBAT_MOVE_DEFINITION_DRIFT", move_id))
            buffer_start, chain_tick = EXPECTED_CHAINS[move_id]
            if not (active_start <= buffer_start < total and active_end < chain_tick <= total):
                findings.append(Finding("COMBAT_CHAIN_INVARIANT", move_id))
            chain_pattern = re.compile(
                rf'"{re.escape(move_id)}".*?{total},\s*{active_start},\s*{active_end},\s*'
                rf'{multiplier:.2f},\s*{maximum},\s*{attack_range:.1f},\s*{buffer_start},\s*{chain_tick},',
                re.DOTALL)
            if chain_pattern.search(content) is None:
                findings.append(Finding("COMBAT_CHAIN_WINDOW_DRIFT", move_id))
            require_contains(content, EXPECTED_STEPS[move_id], "COMBAT_STEP_BOUND", move_id, findings)
        last_move = list(EXPECTED_MOVES)[-1]
        if EXPECTED_CHAINS[last_move][1] != EXPECTED_MOVES[last_move][0]:
            findings.append(Finding("COMBAT_CHAIN_INVARIANT", f"{last_move}:finisher must not chain"))
        for shape in (
                "center_thrust", "horizontal_arc_110", "rising_diagonal",
                "descending_diagonal_thick", "long_lunge_thrust"):
            require_contains(content, f'"{shape}"', "COMBAT_DISTINCT_SHAPE", shape, findings)
        require_contains(content, "new ReactionDefinition(", "COMBAT_TARGET_REACTION", definition.name, findings)
        require_contains(content, "new HitboxDefinition(shape, samples, 0.20, 0.12)", "COMBAT_TOLERANCE_BOUND", definition.name, findings)

    move_definition = definition.parent / "AttackMoveDefinition.java"
    if require_file(move_definition, root, "COMBAT_MOVE_DEFINITION_RECORD", findings):
        content = text(move_definition)
        for needle, code in (
                ("bufferStartTick < activeStartTick || bufferStartTick >= totalTicks",
                 "COMBAT_CHAIN_INVARIANT"),
                ("chainTick <= activeEndTick || chainTick > totalTicks || chainTick < bufferStartTick",
                 "COMBAT_CHAIN_INVARIANT"),
                ("return actionTick >= bufferStartTick && actionTick < totalTicks;",
                 "COMBAT_BUFFER_FROM_ACTIVE_START"),
                ("return actionTick >= chainTick;", "COMBAT_CHAIN_TICK")):
            require_contains(content, needle, code, move_definition.name, findings)
    step_definition = definition.parent / "StepDefinition.java"
    if require_file(step_definition, root, "COMBAT_STEP_DEFINITION_RECORD", findings):
        require_contains(
            text(step_definition), "MAXIMUM_STEP_DISTANCE = 1.6",
            "COMBAT_STEP_BOUND", step_definition.name, findings)

    if session.is_file():
        content = text(session)
        for needle, code in (
                ("boolean bufferedIntent", "COMBAT_ONE_SLOT_BUFFER"),
                ("REJECTED_BUFFER_FULL", "COMBAT_BUFFER_CAPACITY"),
                ("attemptedEntityIds", "COMBAT_HIT_DEDUP"),
                ("remainingTargetCapacity", "COMBAT_ACTION_TARGET_CAP"),
                ("comboDeadline", "COMBAT_COMBO_TIMEOUT"),
                ("originalEndTick", "COMBAT_RECOVERY_END"),
                ("boolean chain = bufferedIntent && move.chainsAt(actionTick);", "COMBAT_CHAIN_TICK")):
            require_contains(content, needle, code, session.name, findings)
    if manager.is_file():
        content = text(manager)
        for needle, code in (
                ("BLOCKED_UNTIL_TICKS", "COMBAT_RECOVERY_LOCK"),
                ("MeditationManager.status(player).state().active()", "COMBAT_CULTIVATION_EXCLUSION"),
                ("sendToPlayersTrackingEntityAndSelf", "COMBAT_TRACKING_BROADCAST"),
                ("player.serverLevel().getGameTime()", "COMBAT_SERVER_TICK_AUTHORITY"),
                ("session.tick(tick, player.getYRot())", "COMBAT_VIEW_YAW_FACING"),
                ("player.setYBodyRot(start.facingYaw())", "COMBAT_VIEW_YAW_FACING"),
                ("speed.removeModifier(COMMIT_MODIFIER_ID)", "COMBAT_COMMITMENT_CLEANUP")):
            require_contains(content, needle, code, manager.name, findings)

    if geometry.is_file():
        content = text(geometry)
        for needle, code in (
                ("broadBounds", "COMBAT_BROAD_PHASE"),
                ("firstContact", "COMBAT_NARROW_PHASE"),
                ("segmentAabbContact", "COMBAT_CAPSULE_SEGMENT_TEST")):
            require_contains(content, needle, code, geometry.name, findings)
    if resolver.is_file():
        content = text(resolver)
        for needle, code in (
                ("legalTarget", "COMBAT_TARGET_FILTER"),
                ("blockedByWall", "COMBAT_WALL_CLIP"),
                ("session.wasAttempted", "COMBAT_RESOLVER_DEDUP"),
                ("session.remainingTargetCapacity", "COMBAT_RESOLVER_ACTION_CAP"),
                ("thenComparingInt", "COMBAT_DETERMINISTIC_ORDER")):
            require_contains(content, needle, code, resolver.name, findings)
    if step.is_file():
        content = text(step)
        for needle, code in (
                ("step.maximumDistance()", "COMBAT_STEP_DEFINITION_AUTHORITY"),
                ("noCollision(player, destination)", "COMBAT_STEP_COLLISION"),
                ("destination.move(0.0, -supportDepth, 0.0)", "COMBAT_STEP_SUPPORT"),
                ("player.setDeltaMovement(", "COMBAT_STEP_IMPULSE"),
                ("player.hurtMarked = true", "COMBAT_STEP_IMPULSE"),
                ("GROUND_DRAG_COMPENSATION = 1.0 - 0.6 * 0.91", "COMBAT_STEP_DRAG_COMPENSATION"),
                ("MAGNETISM_STANDOFF = 0.6", "COMBAT_STEP_MAGNETISM"),
                ("MAGNETISM_HALF_ANGLE_DEGREES = 30.0", "COMBAT_STEP_MAGNETISM")):
            require_contains(content, needle, code, step.name, findings)
        if "player.move(MoverType.PLAYER" in content or "teleportTo(" in content:
            # The step is a server-decided impulse executed by client physics, not a server move.
            findings.append(Finding("COMBAT_STEP_SERVER_MOVE", step.name))
    if damage.is_file():
        content = text(damage)
        for needle, code in (
                ("CommonHooks.onPlayerAttackTarget", "COMBAT_ATTACK_GATE"),
                ("Attributes.ATTACK_DAMAGE", "COMBAT_DAMAGE_ATTRIBUTE"),
                ("getAttackDamageBonus", "COMBAT_ITEM_TARGET_BONUS"),
                ("EnchantmentHelper.modifyDamage", "COMBAT_ENCHANT_DAMAGE"),
                ("target.hurt(source, damage)", "COMBAT_STANDARD_HURT"),
                ("EnchantmentHelper.modifyKnockback", "COMBAT_ENCHANT_KNOCKBACK"),
                ("session.facingYaw()", "COMBAT_KNOCKBACK_FROZEN_FACING"),
                ("ClientboundSetEntityMotionPacket", "COMBAT_PLAYER_KNOCKBACK_SYNC"),
                ("doPostAttackEffectsWithItemSource", "COMBAT_POST_ATTACK_EFFECTS"),
                ("weapon.hurtEnemy", "COMBAT_DURABILITY_HOOK"),
                ("weapon.postHurtEnemy", "COMBAT_POST_DURABILITY_HOOK")):
            require_contains(content, needle, code, damage.name, findings)
        if re.search(r"\b(?:player|attacker)\.attack\s*\(", content):
            findings.append(Finding("COMBAT_VANILLA_ATTACK_DUPLICATE", damage.name))
    if debug.is_file():
        content = text(debug)
        for needle, code in (
                ("MAX_SAMPLE_PARTICLES", "COMBAT_DEBUG_BOUNDED"),
                ("hasPermission(2)", "COMBAT_DEBUG_OPERATOR_ONLY"),
                ("successfulContacts", "COMBAT_DEBUG_SUCCESS_ONLY")):
            require_contains(content, needle, code, debug.name, findings)
    if events.is_file():
        content = text(events)
        for needle in (
                "PlayerLoggedInEvent", "PlayerRespawnEvent", "PlayerChangedDimensionEvent",
                "PlayerLoggedOutEvent", "LivingDeathEvent", "EntityMountEvent",
                "ServerTickEvent.Post"):
            require_contains(content, needle, "COMBAT_LIFECYCLE_CLEANUP", needle, findings)
    feedback = root / "src/main/java/com/example/myvillage/combat/runtime/CombatFeedbackService.java"
    if require_file(feedback, root, "COMBAT_FEEDBACK_SERVICE", findings):
        content = text(feedback)
        for needle, code in (
                ("serverLevel().playSound(\n                attacker,", "COMBAT_SWING_SOUND_EXCLUDES_ATTACKER"),
                ("new CombatHitConfirmPayload(", "COMBAT_HIT_CONFIRM_SEND"),
                ("PacketDistributor.sendToPlayer(", "COMBAT_HIT_CONFIRM_ATTACKER_ONLY"),
                ("PacketDistributor.sendToPlayersTrackingEntityAndSelf(", "COMBAT_IMPACT_BROADCAST"),
                ("new CombatImpactPayload(", "COMBAT_IMPACT_BROADCAST"),
                ("CombatParticles.BLADE_CUT.get()", "COMBAT_BLADE_CUT_SPAWN"),
                ("CombatSounds.IMPACT_HEAVY.get()", "COMBAT_HEAVY_IMPACT_LAYER"),
                ("CombatSounds.jitteredSwingPitch(", "COMBAT_SWING_PITCH_JITTER")):
            require_contains(content, needle, code, feedback.name, findings)
        if "SWEEP_ATTACK" in content:
            findings.append(Finding("COMBAT_VANILLA_SWEEP_PARTICLE", feedback.name))
    reaction = root / "src/main/java/com/example/myvillage/combat/runtime/CombatReactionService.java"
    if require_file(reaction, root, "COMBAT_REACTION_SERVICE", findings):
        content = text(reaction)
        for needle, code in (
                # Only Mob targets are frozen or AI-stalled; players get knockback and a slow.
                ("boolean freezable = target instanceof Mob && !excluded", "COMBAT_REACTION_NO_PLAYER_FREEZE"),
                ("!(entity instanceof Mob mob)", "COMBAT_REACTION_NO_PLAYER_FREEZE"),
                ('"combat_stun"', "COMBAT_REACTION_PLAYER_SLOW"),
                ("target instanceof EnderDragon || target instanceof WitherBoss || target instanceof Warden",
                 "COMBAT_REACTION_BOSS_EXCLUSION")):
            require_contains(content, needle, code, reaction.name, findings)
        if re.search(r"instanceof\s+(?:Server)?Player\b[^;]*freezeUntil", content):
            findings.append(Finding("COMBAT_REACTION_NO_PLAYER_FREEZE", reaction.name))
    if events.is_file():
        content = text(events)
        for needle, code in (
                ("NeoForge.EVENT_BUS.addListener(CombatReactionService::onEntityTickPre)",
                 "COMBAT_REACTION_REGISTRATION"),
                ("NeoForge.EVENT_BUS.addListener(CombatEvents::onLivingKnockBack)",
                 "COMBAT_REACTION_KNOCKBACK_OVERRIDE")):
            require_contains(content, needle, code, events.name, findings)
    particles = root / "src/main/java/com/example/myvillage/combat/CombatParticles.java"
    if require_file(particles, root, "COMBAT_PARTICLE_REGISTRY", findings):
        require_contains(
            text(particles), 'PARTICLE_TYPES.register("blade_cut"',
            "COMBAT_BLADE_CUT_REGISTRATION", particles.name, findings)
    particle_json = root / BLADE_CUT_PARTICLE
    if require_file(particle_json, root, "COMBAT_BLADE_CUT_PARTICLE_JSON", findings):
        try:
            textures = json.loads(text(particle_json)).get("textures")
        except (json.JSONDecodeError, AttributeError) as exc:
            findings.append(Finding("COMBAT_BLADE_CUT_PARTICLE_JSON", str(exc)))
        else:
            if textures != ["myvillage:blade_cut"]:
                findings.append(Finding("COMBAT_BLADE_CUT_PARTICLE_JSON", str(textures)))
    particle_texture = root / BLADE_CUT_TEXTURE
    if require_file(particle_texture, root, "COMBAT_BLADE_CUT_TEXTURE", findings):
        if not particle_texture.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"):
            findings.append(Finding("COMBAT_BLADE_CUT_TEXTURE", "not a PNG"))
    if manager.is_file():
        content = text(manager)
        for needle, code in (
                ("actionTick == move.activeStartTick() - 1", "COMBAT_SWING_SOUND_TIMING"),
                ("CombatFeedbackService.hit(player, move, session.revision(), successfulContacts)",
                 "COMBAT_HIT_FEEDBACK_AFTER_DAMAGE"),
                ("start.facingYaw()", "COMBAT_START_FACING_BROADCAST")):
            require_contains(content, needle, code, manager.name, findings)
    hit_payload = root / "src/main/java/com/example/myvillage/combat/network/CombatHitConfirmPayload.java"
    if require_file(hit_payload, root, "COMBAT_HIT_CONFIRM_PAYLOAD", findings):
        content = text(hit_payload)
        for forbidden in ("damage", "targetEntity", "position", "health"):
            if forbidden in content:
                findings.append(Finding("COMBAT_HIT_CONFIRM_AUTHORITY_FIELD", forbidden))
    sounds_path = root / "src/main/resources/assets/myvillage/sounds.json"
    if require_file(sounds_path, root, "COMBAT_SOUNDS_JSON", findings):
        try:
            sounds = json.loads(text(sounds_path))
        except json.JSONDecodeError as exc:
            findings.append(Finding("COMBAT_SOUNDS_JSON", str(exc)))
        else:
            for event in COMBAT_SOUND_EVENTS:
                entry = sounds.get(event)
                if not isinstance(entry, dict) or not entry.get("sounds") or "subtitle" not in entry:
                    findings.append(Finding("COMBAT_SOUND_EVENT", event))

    if meditation.is_file():
        require_contains(
            text(meditation),
            "CombatSessionManager.interrupt(player, CombatStopReason.CULTIVATION_STARTED, true)",
            "COMBAT_CULTIVATION_MUTUAL_INTERRUPTION",
            meditation.name,
            findings)


def validate_first_person_rig(root: Path, findings: list[Finding]) -> None:
    path = root / FIRST_PERSON_RIG
    if not require_file(path, root, "COMBAT_FIRST_PERSON_RIG_MISSING", findings):
        return
    try:
        rig = json.loads(text(path))
        moves = rig["moves"]
        rig["rig"]["shoulder"]
        rig["neutral"]
    except (json.JSONDecodeError, KeyError, TypeError) as exc:
        findings.append(Finding("COMBAT_FIRST_PERSON_RIG_JSON", str(exc)))
        return
    rig_settings = rig["rig"]
    scale = rig_settings.get("sword_scale", 0.6)
    arm = rig_settings.get("arm", {})
    if (not isinstance(scale, (int, float)) or not 0.2 <= scale <= 1.5 or not isinstance(arm, dict)
            or any(not isinstance(arm.get(name, 0), (int, float))
                   for name in ("upper_arm", "forearm", "thickness", "grip_diagonal", "follow_through"))
            or not 0.2 <= arm.get("thickness", 0.5) <= 1.2
            or not 0 <= arm.get("grip_diagonal", 40) <= 50):
        findings.append(Finding("COMBAT_FIRST_PERSON_RIG_ARM", "rig.sword_scale/rig.arm"))
    arm_poses = [rig["neutral"]] + [key for move in moves.values() if isinstance(move, dict)
                                     for key in move.get("keys", []) if isinstance(key, dict)]
    if any(not isinstance(pose.get(name, 0), (int, float)) for pose in arm_poses for name in ("grip_roll", "elbow")):
        findings.append(Finding("COMBAT_FIRST_PERSON_RIG_ARM", "grip_roll/elbow must be numbers"))
    expected_ids = {f"myvillage:{move_id}" for move_id in EXPECTED_MOVES}
    if set(moves) != expected_ids:
        findings.append(Finding("COMBAT_FIRST_PERSON_RIG_MOVES", ",".join(sorted(moves))))
    for move_id, values in EXPECTED_MOVES.items():
        move = moves.get(f"myvillage:{move_id}")
        if not isinstance(move, dict):
            continue
        total, active_start, active_end = values[0], values[1], values[2]
        keys = move.get("keys", [])
        ticks = [key.get("tick") for key in keys]
        if (len(keys) < 3 or ticks[0] != 0 or ticks[-1] != total
                or any(later <= earlier for earlier, later in zip(ticks, ticks[1:]))):
            findings.append(Finding("COMBAT_FIRST_PERSON_RIG_KEYS", move_id))
        elif keys[0].get("pose") != "neutral" or keys[-1].get("pose") != "neutral":
            findings.append(Finding("COMBAT_FIRST_PERSON_RIG_NEUTRAL", move_id))
        strike = move.get("strike")
        if (not isinstance(strike, list) or len(strike) != 2
                or strike[0] > active_start or strike[1] < active_end
                or strike[1] - strike[0] > MAX_STRIKE_TICKS):
            findings.append(Finding("COMBAT_FIRST_PERSON_RIG_STRIKE", move_id))


def validate_generated_assets(root: Path, findings: list[Finding]) -> None:
    """Runs each committed generator's --check so hand edits to generated assets are caught."""
    for relative, code in GENERATOR_CHECKS:
        script = root / relative
        if not require_file(script, root, "COMBAT_GENERATOR_MISSING", findings):
            continue
        try:
            result = subprocess.run(
                [sys.executable, str(script), "--check"],
                cwd=root,
                capture_output=True,
                text=True,
                timeout=GENERATOR_TIMEOUT_SECONDS,
                check=False)
        except (OSError, subprocess.TimeoutExpired) as exc:
            findings.append(Finding(code, f"{relative}: {exc}"))
            continue
        if result.returncode != 0:
            lines = (result.stderr or result.stdout).strip().splitlines()
            findings.append(Finding(code, f"{relative}: {lines[-1] if lines else result.returncode}"))


def validate_docs(root: Path, findings: list[Finding]) -> None:
    paths = {
        root / "README.md": (
            "SWORD_COMBAT_FOUNDATION", "myvillage:qingfeng_sword", "/myvillage combat debug on",
            "myvillage_pal_smoke move", "combat_smoke_server", "combat_smoke_game_dir",
            "combat_smoke_username", "myvillage_pal_smoke first_person", "qingfeng_first_person.json",
            "hit-stop", "not_verified", "gen_sword_pal_anims.py --check",
            "gen_blade_cut_sprite.py --check", "QingfengFirstPersonArmRenderer",
            "gen_qingfeng_sword_model.py --check", "qingfeng_sword_geometry.json"),
        root / "docs/ai-kb/32_pal_combat_integration.md": (
            "PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar", "CombatDamageService", "First-person",
            "IClientItemExtensions", "RegisterClientExtensionsEvent",
            "qingfeng_first_person.json", "FirstPersonSwordTrail", "CombatWorldTrails",
            "SwingClock", "CombatHitConfirmPayload", "CombatImpactPayload", "chainTick",
            "GROUND_DRAG_COMPENSATION", "QingfengFirstPersonArmRenderer", "SWORD_TRAIL_TRANSLUCENT",
            "gen_sword_pal_anims.py --check", "onComputeFovModifier",
            "qingfeng_sword_geometry.json", "SwordGeometry"),
        root / "AGENTS.md": (
            "validate_sword_combat_foundation.py", "PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar",
            "myvillage_pal_smoke move", "combat_smoke_server", "combat_smoke_game_dir",
            "combat_smoke_username", "QingfengFirstPersonArmRenderer", "gen_sword_pal_anims.py",
            "gen_qingfeng_sword_model.py", "qingfeng_sword_geometry.json"),
    }
    for path, needles in paths.items():
        if not require_file(path, root, "COMBAT_DOC_MISSING", findings):
            continue
        content = text(path)
        for needle in needles:
            if needle not in content:
                findings.append(Finding("COMBAT_DOC_DRIFT", f"{path.name}:{needle}"))


def validate_forbidden_integrations(root: Path, findings: list[Finding]) -> None:
    for path in (root / "build.gradle", root / "gradle.properties"):
        if not path.is_file():
            continue
        lower = text(path).lower()
        for forbidden in ("epicfight", "epic fight", "bettercombat", "better combat", "playeranimator"):
            if forbidden in lower:
                findings.append(Finding("COMBAT_FORBIDDEN_DEPENDENCY", f"{path.name}:{forbidden}"))
    java_root = root / "src/main/java"
    if java_root.is_dir():
        for path in java_root.rglob("*.java"):
            content = text(path)
            if "dev.kosmx.playerAnim" in content or "yesman.epicfight" in content:
                findings.append(Finding("COMBAT_FORBIDDEN_IMPORT", path.relative_to(root).as_posix()))


def validate_jar_resources(root: Path, findings: list[Finding]) -> None:
    build_libs = root / "build/libs"
    jars = list(build_libs.glob("myvillage-*.jar")) if build_libs.is_dir() else []
    if not jars:
        return
    jar = max(jars, key=lambda path: path.stat().st_mtime)
    properties = root / "gradle.properties"
    version = re.search(r"^mod_version=(\S+)$", text(properties), re.MULTILINE) if properties.is_file() else None
    if version is not None and jar.name != f"myvillage-{version.group(1)}.jar":
        # The newest jar predates the current release version; rebuild before inspecting it.
        findings.append(Finding("COMBAT_JAR_STALE", f"{jar.name}:expected myvillage-{version.group(1)}.jar"))
        return
    source_paths = (
        root / "src/main/java/com/example/myvillage/item/ModItems.java",
        root / "src/main/java/com/example/myvillage/client/combat/FirstPersonSwing.java",
        root / "src/main/java/com/example/myvillage/client/combat/FirstPersonSwordTransform.java",
        root / "src/main/java/com/example/myvillage/client/combat/QingfengFirstPersonAnimator.java",
        root / "src/main/java/com/example/myvillage/client/combat/FirstPersonSwordTrail.java",
        root / "src/main/java/com/example/myvillage/client/combat/CombatWorldTrails.java",
        root / "src/main/java/com/example/myvillage/client/combat/QingfengFirstPersonArmRenderer.java",
        root / "src/main/java/com/example/myvillage/client/combat/QingfengFirstPersonArmIk.java",
        root / "src/main/java/com/example/myvillage/client/combat/FirstPersonArmLag.java",
        root / "src/main/java/com/example/myvillage/client/combat/SwordGeometry.java",
        root / "src/main/java/com/example/myvillage/client/combat/CombatCameraFx.java",
        root / "src/main/java/com/example/myvillage/combat/network/CombatImpactPayload.java",
        root / FIRST_PERSON_RIG,
        root / BLADE_CUT_PARTICLE,
        root / BLADE_CUT_TEXTURE,
        root / "src/main/resources/assets/myvillage/sounds.json",
        root / "src/main/resources/assets/myvillage/player_animations/sword_combat.json",
        root / "src/main/resources/assets/myvillage/textures/item/qingfeng_sword.png",
        root / "src/main/resources/assets/myvillage/models/item/qingfeng_sword.json",
        root / QINGFENG_MODEL_3D,
        root / QINGFENG_MODEL_TEXTURE,
        root / QINGFENG_GEOMETRY,
    )
    newest_source = max((path.stat().st_mtime for path in source_paths if path.is_file()), default=0)
    if jar.stat().st_mtime < newest_source:
        findings.append(Finding("COMBAT_JAR_STALE", jar.name))
        return
    expected = {
        "assets/myvillage/models/item/qingfeng_sword.json",
        "assets/myvillage/textures/item/qingfeng_sword.png",
        "assets/myvillage/models/item/qingfeng_sword_3d.json",
        "assets/myvillage/textures/item/qingfeng_sword_model.png",
        "assets/myvillage/combat/qingfeng_sword_geometry.json",
        "assets/myvillage/player_animations/sword_combat.json",
        "data/myvillage/recipe/qingfeng_sword.json",
        "data/minecraft/tags/item/swords.json",
        "com/example/myvillage/combat/CombatMode.class",
        "com/example/myvillage/combat/session/CombatSessionManager.class",
        "com/example/myvillage/combat/runtime/CombatDamageService.class",
        "assets/myvillage/combat/qingfeng_first_person.json",
        "assets/myvillage/sounds.json",
        "com/example/myvillage/combat/CombatSounds.class",
        "com/example/myvillage/combat/runtime/CombatFeedbackService.class",
        "com/example/myvillage/combat/network/CombatHitConfirmPayload.class",
        "com/example/myvillage/client/combat/FirstPersonSwing.class",
        "com/example/myvillage/client/combat/FirstPersonSwing$Pose.class",
        "com/example/myvillage/client/combat/FirstPersonSwingResources.class",
        "com/example/myvillage/client/combat/FirstPersonSwordTransform.class",
        "com/example/myvillage/client/combat/SwingClock.class",
        "com/example/myvillage/client/combat/FirstPersonSwordTrail.class",
        "com/example/myvillage/client/combat/CombatWorldTrails.class",
        "com/example/myvillage/client/combat/QingfengFirstPersonAnimator.class",
        "com/example/myvillage/client/combat/QingfengFirstPersonAnimator$Frame.class",
        "assets/myvillage/lang/en_us.json",
        "assets/myvillage/lang/zh_cn.json",
        "assets/myvillage/particles/blade_cut.json",
        "assets/myvillage/textures/particle/blade_cut.png",
        "com/example/myvillage/combat/CombatParticles.class",
        "com/example/myvillage/combat/network/CombatImpactPayload.class",
        "com/example/myvillage/combat/runtime/CombatReactionService.class",
        "com/example/myvillage/client/combat/QingfengFirstPersonArmRenderer.class",
        "com/example/myvillage/client/combat/QingfengFirstPersonArmIk.class",
        "com/example/myvillage/client/combat/FirstPersonArmLag.class",
        "com/example/myvillage/client/combat/SwordGeometry.class",
        "com/example/myvillage/client/combat/CombatCameraFx.class",
        "com/example/myvillage/client/combat/CombatImpactFx.class",
        "com/example/myvillage/client/combat/BladeCutParticle.class",
    }
    try:
        with zipfile.ZipFile(jar) as archive:
            names = set(archive.namelist())
            animation_bytes = archive.read(
                "assets/myvillage/player_animations/sword_combat.json"
            ) if "assets/myvillage/player_animations/sword_combat.json" in names else None
    except zipfile.BadZipFile as exc:
        findings.append(Finding("COMBAT_JAR_INVALID", str(exc)))
        return
    missing = sorted(expected - names)
    if missing:
        findings.append(Finding("COMBAT_JAR_RESOURCE_MISSING", ",".join(missing)))
    if animation_bytes is not None:
        try:
            packaged_animations = json.loads(animation_bytes)["animations"]
        except (KeyError, json.JSONDecodeError, UnicodeDecodeError) as exc:
            findings.append(Finding("COMBAT_JAR_ANIMATION_JSON", str(exc)))
        else:
            if set(packaged_animations) != REQUIRED_ANIMATION_IDS:
                findings.append(Finding("COMBAT_JAR_ANIMATION_IDS", jar.name))


def validate_no_shaded_pal(root: Path, findings: list[Finding]) -> None:
    source_pal = root / "src/main/java/com/zigythebird"
    if source_pal.exists():
        findings.append(Finding("PAL_COPIED_SOURCE", source_pal.relative_to(root).as_posix()))

    build_libs = root / "build/libs"
    if not build_libs.is_dir():
        return
    for jar in build_libs.glob("myvillage-*.jar"):
        try:
            with zipfile.ZipFile(jar) as archive:
                shaded = next(
                    (name for name in archive.namelist()
                     if name.startswith("com/zigythebird/") or name.endswith(PAL_JAR_NAME)),
                    None)
        except zipfile.BadZipFile:
            continue
        if shaded is not None:
            findings.append(Finding("PAL_SHADED_CONTENT", f"{jar.name}:{shaded}"))


def validate(root: Path = ROOT, run_generators: bool = True) -> list[Finding]:
    findings: list[Finding] = []
    validate_pal_jar(root, findings)
    validate_dependency_wiring(root, findings)
    validate_client_boundary(root, findings)
    validate_first_person_rig(root, findings)
    validate_animation_resource(root, findings)
    validate_qingfeng_item(root, findings)
    validate_preference_and_payloads(root, findings)
    validate_definitions_and_runtime(root, findings)
    if run_generators:
        validate_generated_assets(root, findings)
    validate_docs(root, findings)
    validate_forbidden_integrations(root, findings)
    validate_jar_resources(root, findings)
    validate_no_shaded_pal(root, findings)
    return findings


def main() -> int:
    findings = validate()
    if findings:
        for finding in findings:
            print(f"FAIL {finding}")
        return 1
    print("sword combat foundation validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
