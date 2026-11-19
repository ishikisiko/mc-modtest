package com.example.myvillage.client.combat;

import com.example.myvillage.client.immortal.ClientImmortalBootstrap;
import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.mixin.GauntletHandRendererAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Optional;

/** Attaches worn fist weapons to the stock PlayerModel arm and sleeve. */
public final class WornGauntletRenderer {
    private WornGauntletRenderer() {
    }

    private static Optional<FirstPersonSwing> gauntlet(ItemStack stack) {
        return FirstPersonSwingResources.forStack(stack)
                .filter(rig -> rig.weapon().paired() && rig.weapon().family().filter("fist"::equals).isPresent())
                .map(FirstPersonSwingResources.WeaponRig::swing);
    }

    private static boolean slim(AbstractClientPlayer player) {
        return player.getSkin().model() == PlayerSkin.Model.SLIM;
    }

    private static ModelPart armOf(PlayerModel<?> model, HumanoidArm side) {
        return side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
    }

    public static boolean renderThirdPerson(
            AbstractClientPlayer player, PlayerModel<?> model, HumanoidArm side, ItemStack stack,
            PoseStack poses, MultiBufferSource buffers, int light) {
        Optional<FirstPersonSwing> gauntlet = gauntlet(stack);
        if (gauntlet.isEmpty()) {
            return false;
        }
        poses.pushPose();
        try {
            armOf(model, side).translateAndRotate(poses);
            draw(player, side, stack, gauntlet.get().weapon().gripCenter(),
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, poses, buffers, light);
        } finally {
            poses.popPose();
        }
        return true;
    }

    static boolean renderFirstPerson(RenderHandEvent event, LocalPlayer player) {
        if (player.isInvisible() || player.isScoping()) {
            return false;
        }
        ItemStack stack = event.getItemStack();
        Optional<FirstPersonSwing> found = gauntlet(stack);
        if (found.isEmpty()) {
            return false;
        }
        boolean main = event.getHand() == InteractionHand.MAIN_HAND;
        HumanoidArm side = main ? player.getMainArm() : player.getMainArm().getOpposite();
        FirstPersonSwing swing = found.get();
        if (main && ClientCombatState.mode() == CombatMode.CULTIVATION) {
            renderCultivation(event, player, side, stack, swing);
            return true;
        }
        return renderVanilla(event, player, side, stack, swing.weapon().gripCenter());
    }

    private static boolean renderVanilla(RenderHandEvent event, LocalPlayer player, HumanoidArm side,
                                         ItemStack stack, Vector3f gripPixels) {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (!(dispatcher.getRenderer(player) instanceof PlayerRenderer renderer)) {
            return false;
        }
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        try {
            // The vanilla hand path draws the player's arm and sleeve and leaves
            // its view transform in place. The freshly posed arm anchors the item.
            ((GauntletHandRendererAccess) dispatcher.getItemInHandRenderer()).yuanxian$renderPlayerArm(
                    poses, event.getMultiBufferSource(), event.getPackedLight(),
                    event.getEquipProgress(), event.getSwingProgress(), side);
            armOf(renderer.getModel(), side).translateAndRotate(poses);
            draw(player, side, stack, gripPixels, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    poses, event.getMultiBufferSource(), event.getPackedLight());
        } finally {
            poses.popPose();
        }
        return true;
    }

    private static void renderCultivation(
            RenderHandEvent event, LocalPlayer player, HumanoidArm side, ItemStack stack, FirstPersonSwing swing) {
        float equip = event.getEquipProgress();
        FirstPersonSwing.Pose pose = FirstPersonWeaponAnimator.INSTANCE.currentPose(player, event.getPartialTick(), swing);
        Vector3f gripPixels = swing.weapon().gripCenter();
        Matrix4f grip = FirstPersonWeaponTransform.gripFrame(side, equip, swing.rig(), pose);
        Matrix4f arm = WornGauntletPose.armAtGrip(grip, swing.rig().weaponScale(), side, slim(player));
        drawCultivationArm(event, player, stack, gripPixels, side, arm);
        if (player.getOffhandItem().isEmpty()) {
            FirstPersonArmIk.solveOffHand(side, equip, swing, pose).filter(FirstPersonArmIk.OffHandSolution::free)
                    .ifPresent(off -> {
                        HumanoidArm offSide = side.getOpposite();
                        Matrix4f item = FirstPersonWeaponTransform.pairedItem(side, swing, off.arm());
                        Matrix4f offArm = WornGauntletPose.armAtOffHand(item, gripPixels, offSide, slim(player));
                        drawCultivationArm(event, player, stack, gripPixels, offSide, offArm);
                    });
        }
    }

    private static void drawCultivationArm(
            RenderHandEvent event, LocalPlayer player, ItemStack stack, Vector3f gripPixels,
            HumanoidArm side, Matrix4f frame) {
        PoseStack poses = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        PlayerModel<AbstractClientPlayer> model = slim(player) ? StockArms.SLIM : StockArms.WIDE;
        ModelPart arm = armOf(model, side);
        ModelPart sleeve = side == HumanoidArm.RIGHT ? model.rightSleeve : model.leftSleeve;
        PlayerModelPart sleevePart = side == HumanoidArm.RIGHT ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE;
        var texture = player.getSkin().texture();
        poses.pushPose();
        try {
            poses.mulPose(frame);
            poses.pushPose();
            try {
                // The frame is already local to this arm. Remove only its stock
                // shoulder pivot before rendering the original arm and sleeve.
                poses.translate(-arm.x / 16.0F, -arm.y / 16.0F, -arm.z / 16.0F);
                arm.render(poses, buffers.getBuffer(RenderType.entitySolid(texture)), light, OverlayTexture.NO_OVERLAY);
                if (player.isModelPartShown(sleevePart)) {
                    sleeve.render(poses, buffers.getBuffer(RenderType.entityTranslucent(texture)),
                            light, OverlayTexture.NO_OVERLAY);
                }
                ClientImmortalBootstrap.renderFirstPersonArm(
                        player, side, model, poses, buffers, light, event.getPartialTick());
            } finally {
                poses.popPose();
            }
            draw(player, side, stack, gripPixels, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, poses, buffers, light);
        } finally {
            poses.popPose();
        }
    }

    private static void draw(AbstractClientPlayer player, HumanoidArm side, ItemStack stack,
                             Vector3f gripPixels, ItemDisplayContext context, PoseStack poses,
                             MultiBufferSource buffers, int light) {
        poses.pushPose();
        try {
            poses.mulPose(WornGauntletPose.itemOnArm(side, slim(player), gripPixels));
            PairedWeapons.renderModel(stack, PairedWeapons.model(stack, player, context),
                    side == HumanoidArm.LEFT, poses, buffers, light, OverlayTexture.NO_OVERLAY);
        } finally {
            poses.popPose();
        }
    }

    private static final class StockArms {
        private static final PlayerModel<AbstractClientPlayer> WIDE = bake(false);
        private static final PlayerModel<AbstractClientPlayer> SLIM = bake(true);

        private static PlayerModel<AbstractClientPlayer> bake(boolean slim) {
            return new PlayerModel<>(
                    LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
        }
    }
}
