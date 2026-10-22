package com.example.myvillage.client.forging.array;

import com.example.myvillage.forging.array.ForgingArrayBlock;
import com.example.myvillage.forging.array.ForgingArrayBlockEntity;
import com.example.myvillage.forging.array.ForgingArrayRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

/** 只读取同步状态；鼎体、法环、炉火和实际器物均不驱动生产。 */
public final class ForgingArrayRenderer implements BlockEntityRenderer<ForgingArrayBlockEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("myvillage", "textures/block/forging_array_formed.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int COLD_RUNE_LIGHT = 0x500050;
    private final ItemRenderer itemRenderer;
    private final Map<ForgingArrayBlockEntity, Long> particleTicks = new WeakHashMap<>();

    public ForgingArrayRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override public void render(ForgingArrayBlockEntity block, float partialTick, PoseStack pose,
                                  MultiBufferSource buffers, int light, int overlay) {
        var level = block.getLevel();
        if (level == null || !block.formed() || block.isRemoved()) return;
        var rules = ForgingArrayRules.get();
        Direction facing = block.getBlockState().getValue(ForgingArrayBlock.FACING);
        Direction back = facing.getOpposite();
        float cx = .5f + back.getStepX() * 2, cz = .5f + back.getStepZ() * 2;
        boolean running = block.running();
        double time = level.getGameTime() + (double) partialTick;
        float spin = running ? (float) ((time * rules.ringSpeed()) % 360) : 0f;
        // 从鼎口上方取环境光，避免控制器被鼎壁包围而使整座鼎漆黑。
        light = LevelRenderer.getLightColor(level, block.getBlockPos().relative(back, 2).above(4));

        pose.pushPose();
        pose.translate(cx, -1, cz);
        // 北向为网格原朝向：局部 +x 为右，+z 为后。
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        VertexConsumer mesh = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        drawGroup(pose, mesh, ForgingArrayMesh.Group.BODY, running, light, overlay, time);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        drawGroup(pose, mesh, ForgingArrayMesh.Group.RING, running, light, overlay, time);
        pose.popPose();
        if (running) drawGroup(pose, mesh, ForgingArrayMesh.Group.FIRE, true, light, overlay, time);

        // 所有网格顶点先写完。ItemRenderer 会切换 RenderType，之后不能复用 mesh。
        var display = block.displayItem();
        if (!display.isEmpty()) {
            pose.pushPose();
            double bob = running ? Math.sin(time * .06) * rules.bob() : 0;
            pose.translate(0, 3.5 + bob, 0);
            float itemSpin = running ? (float) ((time * rules.ringSpeed() * .35) % 360) : 0f;
            pose.mulPose(Axis.YP.rotationDegrees(20f + itemSpin));
            pose.scale(.95f, .95f, .95f);
            // 不施加剑专用翻转；NONE 保留每件实际模型的自然方向和组件覆盖。
            itemRenderer.renderStatic(display, ItemDisplayContext.NONE, running ? FULL_BRIGHT : light,
                    overlay, pose, buffers, level, block.getBlockPos().hashCode());
            pose.popPose();
        }
        pose.popPose();
        if (running) emitParticles(block, back);
    }

    private static void drawGroup(PoseStack pose, VertexConsumer out, ForgingArrayMesh.Group group,
                                  boolean running, int light, int overlay, double time) {
        for (var face : ForgingArrayMesh.INSTANCE.faces()) {
            if (face.group() != group) continue;
            float[] normal = face.normal();
            float shade = Math.max(.56f, .78f + .18f * normal[1] - .10f * normal[2] + .06f * Math.abs(normal[0]));
            int faceLight = light;
            if (group == ForgingArrayMesh.Group.FIRE) {
                // 冷态仍可见深暗煤床，不能呈现常燃橙色炉火。
                shade = running ? .90f + .10f * (float) Math.sin(time * .17) : .065f;
                faceLight = running ? FULL_BRIGHT : light;
            } else if (face.glow()) {
                shade = running ? 1f : .38f;
                faceLight = running ? FULL_BRIGHT : COLD_RUNE_LIGHT;
            }
            int channel = Mth.clamp((int) (255 * shade), 0, 255);
            int color = 0xFF000000 | channel << 16 | channel << 8 | channel;
            int column = face.material() & 3, row = face.material() >> 2;
            // 4×4 材质图内缩，避免采样邻格。
            float u0 = column * .25f + .001f, u1 = (column + 1) * .25f - .001f;
            float v0 = row * .25f + .001f, v1 = (row + 1) * .25f - .001f;
            for (int i = 0; i < 4; i++) {
                float[] point = face.points()[i];
                out.addVertex(pose.last(), point[0], point[1], point[2])
                        .setColor(color).setUv(i == 1 || i == 2 ? u1 : u0, i >= 2 ? v1 : v0)
                        .setOverlay(overlay).setLight(faceLight)
                        .setNormal(pose.last(), normal[0], normal[1], normal[2]);
            }
        }
    }

    private void emitParticles(ForgingArrayBlockEntity block, Direction back) {
        var level = block.getLevel();
        if (level == null) return;
        long tick = level.getGameTime();
        if (tick % ForgingArrayRules.get().particleInterval() != 0
                || particleTicks.getOrDefault(block, Long.MIN_VALUE) == tick) return;
        particleTicks.put(block, tick);
        double cx = block.getBlockPos().getX() + .5 + back.getStepX() * 2;
        double cz = block.getBlockPos().getZ() + .5 + back.getStepZ() * 2;
        double bottom = block.getBlockPos().getY() - 1;
        level.addParticle(ParticleTypes.SMALL_FLAME,
                cx + (level.random.nextDouble() - .5) * 1.2, bottom + 2.6,
                cz + (level.random.nextDouble() - .5) * 1.2, 0, .025, 0);
        double angle = tick * .055;
        level.addParticle(ParticleTypes.ENCHANT,
                cx + Math.cos(angle) * .8, bottom + 3.5, cz + Math.sin(angle) * .8,
                -Math.cos(angle) * .08, -.02, -Math.sin(angle) * .08);
    }

    /** 覆盖整座 5×5 鼎；网格底为控制器 Y-1，最高为底部 +4.6。 */
    @Override public AABB getRenderBoundingBox(ForgingArrayBlockEntity block) {
        var pos = block.getBlockPos();
        Direction back = block.getBlockState().getValue(ForgingArrayBlock.FACING).getOpposite();
        double cx = pos.getX() + .5 + back.getStepX() * 2;
        double cz = pos.getZ() + .5 + back.getStepZ() * 2;
        return new AABB(cx - 2.55, pos.getY() - 1.05, cz - 2.55,
                cx + 2.55, pos.getY() + 3.65, cz + 2.55);
    }

    @Override public boolean shouldRenderOffScreen(ForgingArrayBlockEntity block) { return false; }
    @Override public int getViewDistance() { return 72; }
}
