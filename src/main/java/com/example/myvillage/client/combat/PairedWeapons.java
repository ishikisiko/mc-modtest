package com.example.myvillage.client.combat;

import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.definition.WeaponDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A paired weapon ({@code paired} in its weapon file, 0.39.1: one item worn on both hands, like the
 * Xuantie gauntlet) is drawn a second time on the off hand, from the same item model, while the
 * off-hand slot is empty. The model is the right hand's, so on a left off hand it is mirrored. This
 * class decides when ({@link #offHandCopy}) and draws an item model under any pose, a mirroring one
 * included ({@link #renderModel}). The weapon is resolved through {@link CombatStyles}, never by
 * item id. Presentation only: nothing here touches a stack, a slot, or the server.
 */
final class PairedWeapons {
    /** Quads with their vertex order reversed, so a mirroring pose still shows their front faces. */
    private static final Map<BakedQuad, BakedQuad> REVERSED = new WeakHashMap<>();
    private static final Direction[] SIDES = Direction.values();

    private PairedWeapons() {
    }

    static boolean isPaired(ItemStack stack) {
        return !stack.isEmpty() && CombatStyles.bundled().weapon(stack).map(WeaponDefinition::paired).orElse(false);
    }

    /**
     * The main-hand stack to draw again on the off hand: a paired weapon, only while the off-hand
     * slot is empty (anything held there is drawn instead, by vanilla); otherwise empty.
     */
    static ItemStack offHandCopy(LivingEntity entity) {
        if (!entity.getOffhandItem().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack main = entity.getMainHandItem();
        return isPaired(main) ? main : ItemStack.EMPTY;
    }

    /** The off-hand copy mirrors the (right-hand) model on a left off hand. */
    static boolean mirrored(HumanoidArm offArm) {
        return offArm == HumanoidArm.LEFT;
    }

    /** The baked model the item uses in {@code context}, without applying its display transform. */
    static BakedModel model(ItemStack stack, LivingEntity entity, ItemDisplayContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getItemRenderer().getModel(stack, entity.level(), entity, entity.getId());
        return model.applyTransform(context, new PoseStack(), false);
    }

    /**
     * Draws a baked item model's quads as {@code ItemRenderer.render} does after its display
     * transform and {@code translate(-0.5)}: every render pass and render type, the foil buffer,
     * tint colours. With {@code mirrored} the pose on {@code poseStack} reflects the model, so each
     * quad is drawn with its vertex order reversed to keep its front face toward the outside.
     */
    static void renderModel(
            ItemStack stack,
            BakedModel model,
            boolean mirrored,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay) {
        PoseStack.Pose pose = poseStack.last();
        RandomSource random = RandomSource.create();
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            for (RenderType renderType : pass.getRenderTypes(stack, true)) {
                VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(buffers, renderType, true, stack.hasFoil());
                for (Direction side : SIDES) {
                    random.setSeed(42L);
                    quads(consumer, pose, pass.getQuads(null, side, random), stack, mirrored, packedLight, packedOverlay);
                }
                random.setSeed(42L);
                quads(consumer, pose, pass.getQuads(null, null, random), stack, mirrored, packedLight, packedOverlay);
            }
        }
    }

    private static void quads(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            List<BakedQuad> quads,
            ItemStack stack,
            boolean mirrored,
            int packedLight,
            int packedOverlay) {
        for (BakedQuad quad : quads) {
            int colour = -1;
            if (quad.isTinted()) {
                colour = Minecraft.getInstance().getItemColors().getColor(stack, quad.getTintIndex());
            }
            BakedQuad drawn = mirrored ? REVERSED.computeIfAbsent(quad, PairedWeapons::reversed) : quad;
            consumer.putBulkData(pose, drawn,
                    FastColor.ARGB32.red(colour) / 255.0F,
                    FastColor.ARGB32.green(colour) / 255.0F,
                    FastColor.ARGB32.blue(colour) / 255.0F,
                    FastColor.ARGB32.alpha(colour) / 255.0F,
                    packedLight, packedOverlay, true);
        }
    }

    /** The same quad with its vertices in reverse order (positions, colours, UVs and normals travel with them). */
    static BakedQuad reversed(BakedQuad quad) {
        int[] vertices = quad.getVertices();
        int count = 4;
        int stride = vertices.length / count;
        int[] out = new int[vertices.length];
        for (int vertex = 0; vertex < count; vertex++) {
            System.arraycopy(vertices, vertex * stride, out, (count - 1 - vertex) * stride, stride);
        }
        return new BakedQuad(out, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(),
                quad.hasAmbientOcclusion());
    }
}
