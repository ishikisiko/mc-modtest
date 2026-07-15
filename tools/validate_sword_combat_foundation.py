#!/usr/bin/env python3
"""Validate the staged PAL and server-authoritative sword-combat contract."""

from __future__ import annotations

import hashlib
import json
import re
import struct
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
}


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
    first_person_pose_path = client_combat / "FirstPersonSwordPose.java"
    first_person_transform_path = client_combat / "FirstPersonSwordTransform.java"
    first_person_arm_renderer_path = client_combat / "QingfengFirstPersonArmRenderer.java"
    first_person_arm_model_path = client_combat / "QingfengFirstPersonArmModel.java"
    first_person_arm_pose_path = client_combat / "FirstPersonArmPose.java"
    first_person_pose_test_path = client_combat_tests / "FirstPersonSwordPoseTest.java"
    first_person_transform_test_path = client_combat_tests / "FirstPersonSwordTransformTest.java"
    first_person_arm_pose_test_path = client_combat_tests / "FirstPersonArmPoseTest.java"

    for path, code in (
            (controller_path, "PAL_CONTROLLER_MISSING"),
            (bootstrap_path, "PAL_BOOTSTRAP_MISSING"),
            (smoke_path, "PAL_SMOKE_ENTRY_MISSING"),
            (first_person_animator_path, "COMBAT_FIRST_PERSON_ANIMATOR_MISSING"),
            (first_person_pose_path, "COMBAT_FIRST_PERSON_POSE_MISSING"),
            (first_person_transform_path, "COMBAT_FIRST_PERSON_TRANSFORM_MISSING"),
            (first_person_arm_renderer_path, "COMBAT_FIRST_PERSON_ARM_RENDERER_MISSING"),
            (first_person_arm_model_path, "COMBAT_FIRST_PERSON_ARM_MODEL_MISSING"),
            (first_person_arm_pose_path, "COMBAT_FIRST_PERSON_ARM_POSE_MISSING"),
            (first_person_pose_test_path, "COMBAT_FIRST_PERSON_POSE_TEST_MISSING"),
            (first_person_transform_test_path, "COMBAT_FIRST_PERSON_TRANSFORM_TEST_MISSING"),
            (first_person_arm_pose_test_path, "COMBAT_FIRST_PERSON_ARM_POSE_TEST_MISSING")):
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
                ("NeoForge.EVENT_BUS.addListener("
                 "QingfengFirstPersonArmRenderer::onRenderHand)",
                 "COMBAT_FIRST_PERSON_ARM_EVENT_REGISTRATION")):
            require_contains(bootstrap, needle, code, bootstrap_path.name, findings)

    if first_person_animator_path.is_file():
        animator = text(first_person_animator_path)
        for needle, code in (
                ("implements IClientItemExtensions", "COMBAT_FIRST_PERSON_ITEM_EXTENSION"),
                ("applyForgeHandTransform(", "COMBAT_FIRST_PERSON_HAND_TRANSFORM"),
                ("BasicSwordStyle.DEFINITION.indexOf(animationId)",
                 "COMBAT_FIRST_PERSON_MOVE_MAPPING"),
                ("BasicSwordStyle.DEFINITION.move(moveIndex).totalTicks()",
                 "COMBAT_FIRST_PERSON_DURATION_MAPPING"),
                ("player.level().getGameTime() - Math.max(0.0F, elapsedTicks)",
                 "COMBAT_FIRST_PERSON_CORRECTED_TIMELINE"),
                ("Optional<Frame> currentFrame(LocalPlayer player, float partialTick)",
                 "COMBAT_FIRST_PERSON_SHARED_FRAME"),
                ("player.level().getGameTime() + partialTick - actionStartTick",
                 "COMBAT_FIRST_PERSON_FRAME_TIMELINE"),
                ("ClientCombatState.mode() != CombatMode.CULTIVATION",
                 "COMBAT_FIRST_PERSON_MODE_GUARD"),
                ("arm != player.getMainArm()", "COMBAT_FIRST_PERSON_MAIN_HAND_GUARD"),
                (".orElse(FirstPersonSwordPose.neutral())",
                 "COMBAT_FIRST_PERSON_NEUTRAL_FALLBACK"),
                ("FirstPersonSwordPose.sample(", "COMBAT_FIRST_PERSON_POSE_SAMPLE"),
                ("FirstPersonSwordTransform.apply(poseStack, arm, equipProcess, pose)",
                 "COMBAT_FIRST_PERSON_ITEM_SHARED_TRANSFORM")):
            require_contains(animator, needle, code, first_person_animator_path.name, findings)
        for forbidden in ("RenderHandEvent", "Camera", "PacketDistributor", "ServerboundSwingPacket"):
            if forbidden in animator:
                findings.append(Finding(
                    "COMBAT_FIRST_PERSON_AUTHORITY_OR_CAMERA_LEAK",
                    f"{first_person_animator_path.name}:{forbidden}"))

    if first_person_pose_path.is_file():
        first_person_pose = text(first_person_pose_path)
        for needle, code in (
                ("MOVE_COUNT = 5", "COMBAT_FIRST_PERSON_MOVE_COUNT"),
                ("private static final List<Keyframe> THRUST", "COMBAT_FIRST_PERSON_THRUST"),
                ("private static final List<Keyframe> HORIZONTAL_CUT",
                 "COMBAT_FIRST_PERSON_HORIZONTAL_CUT"),
                ("private static final List<Keyframe> RISING_CUT",
                 "COMBAT_FIRST_PERSON_RISING_CUT"),
                ("private static final List<Keyframe> DIAGONAL_CUT",
                 "COMBAT_FIRST_PERSON_DIAGONAL_CUT"),
                ("private static final List<Keyframe> LUNGE_THRUST",
                 "COMBAT_FIRST_PERSON_LUNGE_THRUST"),
                ("List.of(THRUST, HORIZONTAL_CUT, RISING_CUT, DIAGONAL_CUT, LUNGE_THRUST)",
                 "COMBAT_FIRST_PERSON_MOVE_CURVES"),
                ("return bounded * bounded * (3.0F - 2.0F * bounded)",
                 "COMBAT_FIRST_PERSON_INTERPOLATION")):
            require_contains(first_person_pose, needle, code, first_person_pose_path.name, findings)
        continuity_keyframes = (
            "frame(0.12F", "frame(0.13F", "frame(0.14F", "frame(0.15F", "frame(0.16F",
            "frame(0.56F", "frame(0.57F", "frame(0.58F", "frame(0.59F", "frame(0.60F",
            "frame(0.84F", "frame(0.85F", "frame(0.86F", "frame(0.87F", "frame(0.88F",
        )
        if any(keyframe not in first_person_pose for keyframe in continuity_keyframes):
            findings.append(Finding(
                "COMBAT_FIRST_PERSON_CONTINUITY_TIMING",
                first_person_pose_path.name))

    if first_person_transform_path.is_file():
        first_person_transform = text(first_person_transform_path)
        for needle, code in (
                ("static void apply(", "COMBAT_FIRST_PERSON_SHARED_TRANSFORM_APPLY"),
                ("side * (BASE_X + pose.x())", "COMBAT_FIRST_PERSON_TRANSFORM_VIEW_X"),
                ("BASE_Y - equipProgress * EQUIP_DROP + pose.y()",
                 "COMBAT_FIRST_PERSON_TRANSFORM_VIEW_Y"),
                ("BASE_Z + pose.z()", "COMBAT_FIRST_PERSON_TRANSFORM_VIEW_Z"),
                ("Axis.XP.rotationDegrees(pose.pitch())",
                 "COMBAT_FIRST_PERSON_TRANSFORM_PITCH"),
                ("Axis.YP.rotationDegrees(side * pose.yaw())",
                 "COMBAT_FIRST_PERSON_TRANSFORM_YAW"),
                ("Axis.ZP.rotationDegrees(side * pose.roll())",
                 "COMBAT_FIRST_PERSON_TRANSFORM_ROLL"),
                ("poseStack.scale(pose.scale(), pose.scale(), pose.scale())",
                 "COMBAT_FIRST_PERSON_TRANSFORM_SCALE")):
            require_contains(
                first_person_transform,
                needle,
                code,
                first_person_transform_path.name,
                findings)
        transform_order = (
            "poseStack.translate(",
            "Axis.XP.rotationDegrees(pose.pitch())",
            "Axis.YP.rotationDegrees(side * pose.yaw())",
            "Axis.ZP.rotationDegrees(side * pose.roll())",
            "poseStack.scale(pose.scale(), pose.scale(), pose.scale())",
        )
        cursor = -1
        for token in transform_order:
            cursor = first_person_transform.find(token, cursor + 1)
            if cursor < 0:
                findings.append(Finding(
                    "COMBAT_FIRST_PERSON_SHARED_TRANSFORM_ORDER",
                    first_person_transform_path.name))
                break

    if first_person_pose_test_path.is_file():
        first_person_pose_test = text(first_person_pose_test_path)
        for needle, code in (
                ("everyMoveCrossesCenterWithAViewportSizedViewPlaneSweep",
                 "COMBAT_FIRST_PERSON_VIEWPORT_CONTRACT_TEST"),
                ("MIN_VIEW_PLANE_SWEEP = 1.00F",
                 "COMBAT_FIRST_PERSON_VIEWPORT_MIN_SWEEP_TEST"),
                ("MAX_VIEW_PLANE_SWEEP = 1.85F",
                 "COMBAT_FIRST_PERSON_VIEWPORT_MAX_SWEEP_TEST"),
                ("MAX_VIEW_PLANE_STEP = 0.08F",
                 "COMBAT_FIRST_PERSON_VIEWPORT_CONTINUITY_TEST"),
                ("viewportCurvesStayInsideSafeBoundsAndRemainContinuous",
                 "COMBAT_FIRST_PERSON_VIEWPORT_BOUNDS_TEST"),
                ("thrustHitsAdvanceIntoTheCenterAndLungeReadsStronger",
                 "COMBAT_FIRST_PERSON_THRUST_DEPTH_TEST"),
                ("cutsKeepTheirDirectionalViewPlaneSignatures",
                 "COMBAT_FIRST_PERSON_CUT_SIGNATURE_TEST")):
            require_contains(
                first_person_pose_test,
                needle,
                code,
                first_person_pose_test_path.name,
                findings)

    if first_person_transform_test_path.is_file():
        first_person_transform_test = text(first_person_transform_test_path)
        require_contains(
            first_person_transform_test,
            "sharedParentTransformMirrorsHandsAndPreservesMatrixOrder",
            "COMBAT_FIRST_PERSON_TRANSFORM_MATRIX_TEST",
            first_person_transform_test_path.name,
            findings)

    if first_person_arm_renderer_path.is_file():
        arm_renderer = text(first_person_arm_renderer_path)
        for needle, code in (
                ("public static void onRenderHand(RenderHandEvent event)",
                 "COMBAT_FIRST_PERSON_ARM_RENDER_EVENT"),
                ("event.getHand() != InteractionHand.MAIN_HAND",
                 "COMBAT_FIRST_PERSON_ARM_MAIN_HAND_GUARD"),
                ("player.isInvisible()", "COMBAT_FIRST_PERSON_ARM_INVISIBLE_GUARD"),
                ("!event.getItemStack().is(ModItems.QINGFENG_SWORD.get())",
                 "COMBAT_FIRST_PERSON_ARM_EVENT_ITEM_GUARD"),
                ("!player.getMainHandItem().is(ModItems.QINGFENG_SWORD.get())",
                 "COMBAT_FIRST_PERSON_ARM_PLAYER_ITEM_GUARD"),
                ("ClientCombatState.mode() != CombatMode.CULTIVATION",
                 "COMBAT_FIRST_PERSON_ARM_MODE_GUARD"),
                (".currentFrame(player, event.getPartialTick())",
                 "COMBAT_FIRST_PERSON_ARM_SHARED_FRAME"),
                (".orElse(FirstPersonSwordPose.neutral())",
                 "COMBAT_FIRST_PERSON_ARM_NEUTRAL_FALLBACK"),
                ("FirstPersonArmPose.sample(",
                 "COMBAT_FIRST_PERSON_ARM_JOINT_SAMPLE"),
                (".orElse(FirstPersonArmPose.neutral(swordPose))",
                 "COMBAT_FIRST_PERSON_ARM_NEUTRAL_JOINTS"),
                ("FirstPersonSwordTransform.apply("
                 "poseStack, arm, event.getEquipProgress(), swordPose)",
                 "COMBAT_FIRST_PERSON_ARM_SHARED_TRANSFORM"),
                ("side * FirstPersonArmPose.GRIP_PIVOT_X",
                 "COMBAT_FIRST_PERSON_ARM_GRIP_PIVOT"),
                ("pose.counterRoll()", "COMBAT_FIRST_PERSON_ARM_COUNTER_ROLL"),
                ("pose.counterYaw()", "COMBAT_FIRST_PERSON_ARM_COUNTER_YAW"),
                ("pose.counterPitch()", "COMBAT_FIRST_PERSON_ARM_COUNTER_PITCH"),
                ("side * FirstPersonArmPose.GRIP_OFFSET_X",
                 "COMBAT_FIRST_PERSON_ARM_GRIP_OFFSET"),
                ("QingfengFirstPersonArmModel.create(true, arm)",
                 "COMBAT_FIRST_PERSON_ARM_SLIM_MODEL"),
                ("QingfengFirstPersonArmModel.create(false, arm)",
                 "COMBAT_FIRST_PERSON_ARM_WIDE_MODEL"),
                ("player.getSkin().model() == PlayerSkin.Model.SLIM",
                 "COMBAT_FIRST_PERSON_ARM_SKIN_MODEL"),
                ("FirstPersonArmPose.gripCorrection(pose, arm)",
                 "COMBAT_FIRST_PERSON_ARM_DISTAL_ANCHOR"),
                ("pose.viewmodelScale()",
                 "COMBAT_FIRST_PERSON_ARM_VIEWMODEL_SCALE"),
                ("model.applyPose(pose, arm)",
                 "COMBAT_FIRST_PERSON_ARM_POSE_APPLY"),
                ("FirstPersonArmPose.scaledCorrectedElbow(pose, arm)",
                 "COMBAT_FIRST_PERSON_ARM_ELBOW_TARGET"),
                ("new Quaternionf().rotationTo(",
                 "COMBAT_FIRST_PERSON_ARM_CONNECTOR_ROTATION"),
                ("QingfengFirstPersonArmModel.CONNECTOR_LENGTH_UNITS",
                 "COMBAT_FIRST_PERSON_ARM_CONNECTOR_LENGTH"),
                ("model.renderSkinConnector(",
                 "COMBAT_FIRST_PERSON_ARM_SKIN_CONNECTOR"),
                ("model.renderSleeveConnector(",
                 "COMBAT_FIRST_PERSON_ARM_SLEEVE_CONNECTOR"),
                ("RenderType.entitySolid(player.getSkin().texture())",
                 "COMBAT_FIRST_PERSON_ARM_SKIN_TEXTURE"),
                ("RenderType.entityTranslucent(player.getSkin().texture())",
                 "COMBAT_FIRST_PERSON_ARM_SLEEVE_TEXTURE"),
                ("PlayerModelPart.RIGHT_SLEEVE", "COMBAT_FIRST_PERSON_RIGHT_SLEEVE"),
                ("PlayerModelPart.LEFT_SLEEVE", "COMBAT_FIRST_PERSON_LEFT_SLEEVE"),
                ("poseStack.pushPose();", "COMBAT_FIRST_PERSON_ARM_POSE_PUSH"),
                ("poseStack.popPose();", "COMBAT_FIRST_PERSON_ARM_POSE_POP")):
            require_contains(
                arm_renderer,
                needle,
                code,
                first_person_arm_renderer_path.name,
                findings)
        transform_order = (
            "FirstPersonSwordTransform.apply(",
            "side * FirstPersonArmPose.GRIP_PIVOT_X",
            "pose.counterRoll()",
            "pose.counterYaw()",
            "pose.counterPitch()",
            "-side * FirstPersonArmPose.GRIP_PIVOT_X",
            "side * FirstPersonArmPose.GRIP_OFFSET_X",
            "Axis.YP.rotationDegrees(side * 45.0F)",
        )
        cursor = -1
        for token in transform_order:
            cursor = arm_renderer.find(token, cursor + 1)
            if cursor < 0:
                findings.append(Finding(
                    "COMBAT_FIRST_PERSON_ARM_GRIP_TRANSFORM_ORDER",
                    first_person_arm_renderer_path.name))
                break
        if "event.setCanceled" in arm_renderer:
            findings.append(Finding(
                "COMBAT_FIRST_PERSON_ARM_ITEM_PASS_CANCEL",
                first_person_arm_renderer_path.name))
        for forbidden in ("getGameTime", "actionStartTick"):
            if forbidden in arm_renderer:
                findings.append(Finding(
                    "COMBAT_FIRST_PERSON_ARM_DUPLICATE_TIMELINE",
                    f"{first_person_arm_renderer_path.name}:{forbidden}"))
        for forbidden in (
                "Camera", "PacketDistributor", "ServerboundSwingPacket", "com.zigythebird"):
            if forbidden in arm_renderer:
                findings.append(Finding(
                    "COMBAT_FIRST_PERSON_ARM_AUTHORITY_OR_CAMERA_LEAK",
                    f"{first_person_arm_renderer_path.name}:{forbidden}"))
        for forbidden in ("yesman.epicfight", "software.bernie.geckolib"):
            if forbidden in arm_renderer:
                findings.append(Finding(
                    "COMBAT_FIRST_PERSON_ARM_THIRD_PARTY_RIG_FORBIDDEN",
                    f"{first_person_arm_renderer_path.name}:{forbidden}"))

    if first_person_arm_model_path.is_file():
        arm_model = text(first_person_arm_model_path)
        for needle, code in (
                ("LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE)",
                 "COMBAT_FIRST_PERSON_ARM_LAYER_DEFINITION"),
                ("addChain(\n                root,\n                \"skin\"",
                 "COMBAT_FIRST_PERSON_ARM_SKIN_CHAIN"),
                ("addChain(\n                root,\n                \"sleeve\"",
                 "COMBAT_FIRST_PERSON_ARM_SLEEVE_CHAIN"),
                ("prefix + \"_upper_arm\"",
                 "COMBAT_FIRST_PERSON_ARM_UPPER_DRIVER"),
                ("prefix + \"_forearm\"",
                 "COMBAT_FIRST_PERSON_ARM_FOREARM_SEGMENT"),
                ("prefix + \"_hand\"",
                 "COMBAT_FIRST_PERSON_ARM_HAND_SEGMENT"),
                ("prefix + \"_connector\"",
                 "COMBAT_FIRST_PERSON_ARM_SCREEN_CONNECTOR"),
                ("SEGMENT_SIDE_FACES",
                 "COMBAT_FIRST_PERSON_ARM_INTERNAL_CAP_GUARD"),
                ("HAND_CAP_FACE",
                 "COMBAT_FIRST_PERSON_ARM_HAND_CAP"),
                ("skin.apply(pose.joints(), arm)",
                 "COMBAT_FIRST_PERSON_ARM_SKIN_JOINT_APPLY"),
                ("sleeve.apply(pose.joints(), arm)",
                 "COMBAT_FIRST_PERSON_ARM_SLEEVE_JOINT_APPLY"),
                ("upperArm.resetPose();",
                 "COMBAT_FIRST_PERSON_ARM_UPPER_RESET"),
                ("forearm.resetPose();",
                 "COMBAT_FIRST_PERSON_ARM_FOREARM_RESET"),
                ("hand.resetPose();",
                 "COMBAT_FIRST_PERSON_ARM_HAND_RESET"),
                ("upperArm.setRotation(",
                 "COMBAT_FIRST_PERSON_ARM_UPPER_ROTATION"),
                ("forearm.setRotation(",
                 "COMBAT_FIRST_PERSON_ARM_FOREARM_ROTATION"),
                ("hand.setRotation(",
                 "COMBAT_FIRST_PERSON_ARM_HAND_ROTATION"),
                ("float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F",
                 "COMBAT_FIRST_PERSON_ARM_JOINT_MIRROR")):
            require_contains(
                arm_model,
                needle,
                code,
                first_person_arm_model_path.name,
                findings)
        for forbidden in ("PlayerModel<", "ModelLayers.PLAYER", "com.zigythebird"):
            if forbidden in arm_model:
                findings.append(Finding(
                    "COMBAT_FIRST_PERSON_ARM_WHOLE_MODEL_FORBIDDEN",
                    f"{first_person_arm_model_path.name}:{forbidden}"))
        for forbidden in ("yesman.epicfight", "software.bernie.geckolib"):
            if forbidden in arm_model:
                findings.append(Finding(
                    "COMBAT_FIRST_PERSON_ARM_THIRD_PARTY_RIG_FORBIDDEN",
                    f"{first_person_arm_model_path.name}:{forbidden}"))

    if first_person_arm_pose_path.is_file():
        arm_pose = text(first_person_arm_pose_path)
        for needle, code in (
                ("PITCH_FOLLOW = 0.10F", "COMBAT_FIRST_PERSON_ARM_PITCH_FOLLOW"),
                ("YAW_FOLLOW = 0.35F", "COMBAT_FIRST_PERSON_ARM_YAW_FOLLOW"),
                ("ROLL_FOLLOW = 0.20F", "COMBAT_FIRST_PERSON_ARM_ROLL_FOLLOW"),
                ("MIN_VIEWMODEL_SCALE = 0.45F",
                 "COMBAT_FIRST_PERSON_ARM_MIN_VIEWMODEL_SCALE"),
                ("VIEWMODEL_SCALE_FADE_PROGRESS = 0.12F",
                 "COMBAT_FIRST_PERSON_ARM_VIEWMODEL_SCALE_FADE"),
                ("SHOULDER_TO_ELBOW_Y = 3.0F",
                 "COMBAT_FIRST_PERSON_ARM_SHOULDER_TO_ELBOW"),
                ("FOREARM_LENGTH = 5.0F",
                 "COMBAT_FIRST_PERSON_ARM_FOREARM_LENGTH"),
                ("HAND_LENGTH = 2.0F",
                 "COMBAT_FIRST_PERSON_ARM_HAND_LENGTH"),
                ("GRIP_ENDPOINT_Y = 10.0F",
                 "COMBAT_FIRST_PERSON_ARM_GRIP_ENDPOINT"),
                ("GRIP_PIVOT_X = 0.055F", "COMBAT_FIRST_PERSON_ARM_GRIP_PIVOT_X"),
                ("GRIP_PIVOT_Y = -0.061F", "COMBAT_FIRST_PERSON_ARM_GRIP_PIVOT_Y"),
                ("GRIP_PIVOT_Z = -0.024F", "COMBAT_FIRST_PERSON_ARM_GRIP_PIVOT_Z"),
                ("GRIP_OFFSET_X = -0.040F", "COMBAT_FIRST_PERSON_ARM_GRIP_OFFSET_X"),
                ("GRIP_OFFSET_Y = -0.182F", "COMBAT_FIRST_PERSON_ARM_GRIP_OFFSET_Y"),
                ("GRIP_OFFSET_Z = 0.394F", "COMBAT_FIRST_PERSON_ARM_GRIP_OFFSET_Z"),
                ("swordPose.x()", "COMBAT_FIRST_PERSON_ARM_GRIP_X"),
                ("swordPose.y()", "COMBAT_FIRST_PERSON_ARM_GRIP_Y"),
                ("swordPose.z()", "COMBAT_FIRST_PERSON_ARM_GRIP_Z"),
                ("swordPose.scale()", "COMBAT_FIRST_PERSON_ARM_GRIP_SCALE"),
                ("-swordPose.pitch() * (1.0F - PITCH_FOLLOW)",
                 "COMBAT_FIRST_PERSON_ARM_PITCH_COUNTER_DERIVATION"),
                ("-swordPose.yaw() * (1.0F - YAW_FOLLOW)",
                 "COMBAT_FIRST_PERSON_ARM_YAW_COUNTER_DERIVATION"),
                ("-swordPose.roll() * (1.0F - ROLL_FOLLOW)",
                 "COMBAT_FIRST_PERSON_ARM_ROLL_COUNTER_DERIVATION"),
                ("private static final JointPose NEUTRAL_JOINTS",
                 "COMBAT_FIRST_PERSON_ARM_NEUTRAL_JOINTS"),
                ("private static final List<Keyframe> THRUST",
                 "COMBAT_FIRST_PERSON_ARM_THRUST_JOINT_TRACK"),
                ("private static final List<Keyframe> HORIZONTAL_CUT",
                 "COMBAT_FIRST_PERSON_ARM_HORIZONTAL_JOINT_TRACK"),
                ("private static final List<Keyframe> RISING_CUT",
                 "COMBAT_FIRST_PERSON_ARM_RISING_JOINT_TRACK"),
                ("private static final List<Keyframe> DIAGONAL_CUT",
                 "COMBAT_FIRST_PERSON_ARM_DIAGONAL_JOINT_TRACK"),
                ("private static final List<Keyframe> LUNGE_THRUST",
                 "COMBAT_FIRST_PERSON_ARM_LUNGE_JOINT_TRACK"),
                ("static Vector3f gripCorrection(Pose pose, HumanoidArm arm)",
                 "COMBAT_FIRST_PERSON_ARM_GRIP_CORRECTION_FUNCTION"),
                ("GRIP_ENDPOINT_Y - endpoint.y",
                 "COMBAT_FIRST_PERSON_ARM_GRIP_CORRECTION_DERIVATION"),
                ("static Vector3f scaledCorrectedElbow(Pose pose, HumanoidArm arm)",
                 "COMBAT_FIRST_PERSON_ARM_SCALED_ELBOW_FUNCTION"),
                ("elbow.sub(grip).mul(pose.viewmodelScale()).add(grip)",
                 "COMBAT_FIRST_PERSON_ARM_SCALED_ELBOW_DERIVATION"),
                ("private static Vector3f handEndpoint(JointPose pose, HumanoidArm arm)",
                 "COMBAT_FIRST_PERSON_ARM_FORWARD_KINEMATICS"),
                (".rotationZYX(radians(roll), radians(yaw), radians(pitch))",
                 "COMBAT_FIRST_PERSON_ARM_JOINT_ROTATION_ORDER"),
                ("private static float viewmodelScale(float progress)",
                 "COMBAT_FIRST_PERSON_ARM_VIEWMODEL_SCALE_FUNCTION"),
                ("edgeDistance / VIEWMODEL_SCALE_FADE_PROGRESS",
                 "COMBAT_FIRST_PERSON_ARM_VIEWMODEL_SCALE_DERIVATION"),
                ("JointPose joints,",
                 "COMBAT_FIRST_PERSON_ARM_POSE_JOINT_PAYLOAD")):
            require_contains(
                arm_pose,
                needle,
                code,
                first_person_arm_pose_path.name,
                findings)
        for forbidden in (
                "getGameTime", "actionStartTick", "Camera", "PacketDistributor",
                "ServerboundSwingPacket", "com.zigythebird"):
            if forbidden in arm_pose:
                findings.append(Finding(
                    "COMBAT_FIRST_PERSON_ARM_AUTHORITY_OR_CAMERA_LEAK",
                    f"{first_person_arm_pose_path.name}:{forbidden}"))
        for forbidden in ("yesman.epicfight", "software.bernie.geckolib"):
            if forbidden in arm_pose:
                findings.append(Finding(
                    "COMBAT_FIRST_PERSON_ARM_THIRD_PARTY_RIG_FORBIDDEN",
                    f"{first_person_arm_pose_path.name}:{forbidden}"))

    if first_person_arm_pose_test_path.is_file():
        arm_pose_test = text(first_person_arm_pose_test_path)
        for needle, code in (
                ("armAndSwordShareTheCorrectedActionFrameAtEverySample",
                 "COMBAT_FIRST_PERSON_ARM_SHARED_FRAME_TEST"),
                ("fiveMovesAuthorDistinctShoulderElbowAndWristPoses",
                 "COMBAT_FIRST_PERSON_ARM_JOINT_TRACK_TEST"),
                ("jointDepthAxesStayInsideTheNearPlaneEnvelope",
                 "COMBAT_FIRST_PERSON_ARM_NEAR_PLANE_TEST"),
                ("gripCorrectionAnchorsTheArticulatedHandForBothHandsAtEverySample",
                 "COMBAT_FIRST_PERSON_ARM_GRIP_ANCHOR_TEST"),
                ("scaledElbowConnectorTargetMirrorsForBothHandsAtEverySample",
                 "COMBAT_FIRST_PERSON_ARM_CONNECTOR_TARGET_TEST"),
                ("jointCurvesStayContinuousAndWithinTheViewmodelEnvelope",
                 "COMBAT_FIRST_PERSON_ARM_JOINT_CONTINUITY_TEST"),
                ("viewmodelScaleProtectsOnlyTheMiddleAndReturnsToFullSize",
                 "COMBAT_FIRST_PERSON_ARM_VIEWMODEL_SCALE_TEST"),
                ("counterRotationCannotMoveTheGripPivot",
                 "COMBAT_FIRST_PERSON_ARM_GRIP_PIVOT_TEST")):
            require_contains(
                arm_pose_test,
                needle,
                code,
                first_person_arm_pose_test_path.name,
                findings)

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
        model = json.loads(text(model_path))
        if (model.get("parent") != "minecraft:item/handheld"
                or model.get("textures", {}).get("layer0") != "myvillage:item/qingfeng_sword"):
            findings.append(Finding("QINGFENG_MODEL_CONTRACT", model_path.name))
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
        require_contains(content, 'PROTOCOL_VERSION = "4"', "COMBAT_PROTOCOL_VERSION", "ModPayloads", findings)
        require_contains(content, "CombatPayloads.register(registrar)", "COMBAT_PAYLOAD_REGISTRATION", "ModPayloads", findings)

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
                ("resetsServerSession", "COMBAT_CLIENT_SESSION_RESET")):
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
        for shape in (
                "center_thrust", "horizontal_arc_110", "rising_diagonal",
                "descending_diagonal_thick", "long_lunge_thrust"):
            require_contains(content, f'"{shape}"', "COMBAT_DISTINCT_SHAPE", shape, findings)
        require_contains(content, "new StepDefinition(6, 0.8, 0.35)", "COMBAT_STEP_BOUND", definition.name, findings)
        require_contains(content, "new HitboxDefinition(shape, samples, 0.20, 0.12)", "COMBAT_TOLERANCE_BOUND", definition.name, findings)

    if session.is_file():
        content = text(session)
        for needle, code in (
                ("boolean bufferedIntent", "COMBAT_ONE_SLOT_BUFFER"),
                ("REJECTED_BUFFER_FULL", "COMBAT_BUFFER_CAPACITY"),
                ("attemptedEntityIds", "COMBAT_HIT_DEDUP"),
                ("remainingTargetCapacity", "COMBAT_ACTION_TARGET_CAP"),
                ("comboDeadline", "COMBAT_COMBO_TIMEOUT"),
                ("originalEndTick", "COMBAT_RECOVERY_END")):
            require_contains(content, needle, code, session.name, findings)
    if manager.is_file():
        content = text(manager)
        for needle, code in (
                ("BLOCKED_UNTIL_TICKS", "COMBAT_RECOVERY_LOCK"),
                ("MeditationManager.status(player).state().active()", "COMBAT_CULTIVATION_EXCLUSION"),
                ("sendToPlayersTrackingEntityAndSelf", "COMBAT_TRACKING_BROADCAST"),
                ("player.serverLevel().getGameTime()", "COMBAT_SERVER_TICK_AUTHORITY")):
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
                ("player.move(MoverType.PLAYER", "COMBAT_STEP_SERVER_MOVE")):
            require_contains(content, needle, code, step.name, findings)
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
    if meditation.is_file():
        require_contains(
            text(meditation),
            "CombatSessionManager.interrupt(player, CombatStopReason.CULTIVATION_STARTED, true)",
            "COMBAT_CULTIVATION_MUTUAL_INTERRUPTION",
            meditation.name,
            findings)


def validate_docs(root: Path, findings: list[Finding]) -> None:
    paths = {
        root / "README.md": (
            "SWORD_COMBAT_FOUNDATION", "myvillage:qingfeng_sword", "/myvillage combat debug on",
            "myvillage_pal_smoke move", "combat_smoke_server", "combat_smoke_game_dir",
            "combat_smoke_username", "RenderHandEvent", "wide/slim", "not_verified"),
        root / "docs/ai-kb/32_pal_combat_integration.md": (
            "PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar", "CombatDamageService", "First-person",
            "IClientItemExtensions", "RegisterClientExtensionsEvent",
            "QingfengFirstPersonArmRenderer", "QingfengFirstPersonArmModel",
            "upper_arm", "forearm", "hand", "connector"),
        root / "AGENTS.md": (
            "validate_sword_combat_foundation.py", "PlayerAnimationLibNeoforge-1.1.4+mc.1.21.1.jar",
            "myvillage_pal_smoke move", "combat_smoke_server", "combat_smoke_game_dir",
            "combat_smoke_username"),
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
    source_paths = (
        root / "src/main/java/com/example/myvillage/item/ModItems.java",
        root / "src/main/java/com/example/myvillage/client/combat/FirstPersonSwordPose.java",
        root / "src/main/java/com/example/myvillage/client/combat/FirstPersonSwordTransform.java",
        root / "src/main/java/com/example/myvillage/client/combat/FirstPersonArmPose.java",
        root / "src/main/java/com/example/myvillage/client/combat/QingfengFirstPersonArmModel.java",
        root / "src/main/java/com/example/myvillage/client/combat/QingfengFirstPersonAnimator.java",
        root / "src/main/java/com/example/myvillage/client/combat/QingfengFirstPersonArmRenderer.java",
        root / "src/main/resources/assets/myvillage/player_animations/sword_combat.json",
        root / "src/main/resources/assets/myvillage/textures/item/qingfeng_sword.png",
    )
    newest_source = max((path.stat().st_mtime for path in source_paths if path.is_file()), default=0)
    if jar.stat().st_mtime < newest_source:
        findings.append(Finding("COMBAT_JAR_STALE", jar.name))
        return
    expected = {
        "assets/myvillage/models/item/qingfeng_sword.json",
        "assets/myvillage/textures/item/qingfeng_sword.png",
        "assets/myvillage/player_animations/sword_combat.json",
        "data/myvillage/recipe/qingfeng_sword.json",
        "data/minecraft/tags/item/swords.json",
        "com/example/myvillage/combat/CombatMode.class",
        "com/example/myvillage/combat/session/CombatSessionManager.class",
        "com/example/myvillage/combat/runtime/CombatDamageService.class",
        "com/example/myvillage/client/combat/FirstPersonSwordPose.class",
        "com/example/myvillage/client/combat/FirstPersonSwordTransform.class",
        "com/example/myvillage/client/combat/FirstPersonArmPose.class",
        "com/example/myvillage/client/combat/FirstPersonArmPose$Pose.class",
        "com/example/myvillage/client/combat/FirstPersonArmPose$JointPose.class",
        "com/example/myvillage/client/combat/QingfengFirstPersonArmModel.class",
        "com/example/myvillage/client/combat/QingfengFirstPersonArmModel$Chain.class",
        "com/example/myvillage/client/combat/QingfengFirstPersonAnimator.class",
        "com/example/myvillage/client/combat/QingfengFirstPersonAnimator$Frame.class",
        "com/example/myvillage/client/combat/QingfengFirstPersonArmRenderer.class",
        "assets/myvillage/lang/en_us.json",
        "assets/myvillage/lang/zh_cn.json",
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


def validate(root: Path = ROOT) -> list[Finding]:
    findings: list[Finding] = []
    validate_pal_jar(root, findings)
    validate_dependency_wiring(root, findings)
    validate_client_boundary(root, findings)
    validate_animation_resource(root, findings)
    validate_qingfeng_item(root, findings)
    validate_preference_and_payloads(root, findings)
    validate_definitions_and_runtime(root, findings)
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
