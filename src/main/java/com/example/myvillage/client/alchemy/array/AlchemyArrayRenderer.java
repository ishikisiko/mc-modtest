package com.example.myvillage.client.alchemy.array;

import com.example.myvillage.alchemy.array.AlchemyArrayBlock;
import com.example.myvillage.alchemy.array.AlchemyArrayBlockEntity;
import com.example.myvillage.alchemy.array.AlchemyArrayRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import java.util.Map;
import java.util.WeakHashMap;

/** Copper and qi rings use vanilla textures; production state is exclusively server-synchronized. */
public final class AlchemyArrayRenderer implements BlockEntityRenderer<AlchemyArrayBlockEntity> {
    private static final ResourceLocation COPPER = ResourceLocation.withDefaultNamespace("textures/block/copper_block.png");
    private static final ResourceLocation PATINA = ResourceLocation.withDefaultNamespace("textures/block/oxidized_copper.png");
    private final Map<AlchemyArrayBlockEntity, Long> particleTicks = new WeakHashMap<>();

    public AlchemyArrayRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(AlchemyArrayBlockEntity block, float partialTick, PoseStack pose,
                                  MultiBufferSource buffers, int light, int overlay) {
        if (block.getLevel() == null || !block.formed()) return;
        Direction facing = block.getBlockState().getValue(AlchemyArrayBlock.FACING);
        Direction back = facing.getOpposite();
        boolean running = block.running();
        var rules = AlchemyArrayRules.get();
        float angle = running ? (float) ((block.getLevel().getGameTime() % 24000 + partialTick) * rules.ringSpeedDegrees()) : 0;
        pose.pushPose();
        pose.translate(.5 + back.getStepX(), 2.09, .5 + back.getStepZ());
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        ring(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(COPPER)), .87f, .72f, .045f, light, overlay, false);
        if (running) {
            pose.translate(0, .18, 0);
            pose.mulPose(Axis.YP.rotationDegrees(-angle * 1.65f));
            ring(pose, buffers.getBuffer(RenderType.entityTranslucent(PATINA)), .63f, .59f, .015f, 0xF000F0, overlay, true);
            emitParticles(block, facing, back);
        }
        pose.popPose();
    }

    private void emitParticles(AlchemyArrayBlockEntity block, Direction facing, Direction back) {
        var level = block.getLevel();
        long tick = level.getGameTime();
        if (tick % AlchemyArrayRules.get().smokeIntervalTicks() != 0 || particleTicks.getOrDefault(block, -1L) == tick) return;
        particleTicks.put(block, tick);
        var pos = block.getBlockPos();
        double cx = pos.getX() + .5 + back.getStepX(), cz = pos.getZ() + .5 + back.getStepZ();
        double spread = .28;
        level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                cx + (level.random.nextDouble() - .5) * spread, pos.getY() + 2.2,
                cz + (level.random.nextDouble() - .5) * spread, 0, .025, 0);
        double theta = tick * .13;
        level.addParticle(ParticleTypes.ENCHANT, cx + Math.cos(theta) * .68, pos.getY() + 2.28,
                cz + Math.sin(theta) * .68, 0, .03, 0);
        level.addParticle(ParticleTypes.FLAME, pos.getX() + .5 + facing.getStepX() * .54,
                pos.getY() + .28, pos.getZ() + .5 + facing.getStepZ() * .54, 0, .012, 0);
    }

    private static void ring(PoseStack pose, VertexConsumer out, float outer, float inner, float thickness,
                             int light, int overlay, boolean qi) {
        // Repeated copper inlays make slow rotation visible even though the silhouette is circular.
        for (int i = 0; i < 48; i++) {
            if (qi && i % 12 >= 9) continue;
            double a = Math.PI * 2 * i / 48, b = Math.PI * 2 * (i + 1) / 48;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            float cb = (float) Math.cos(b), sb = (float) Math.sin(b);
            int color = qi ? 0xBBA4E7C1 : i % 12 < 2 ? 0xFFFFE1A1 : 0xFFDBA567;
            float[][] top = {{ca * outer, thickness, sa * outer}, {cb * outer, thickness, sb * outer},
                    {cb * inner, thickness, sb * inner}, {ca * inner, thickness, sa * inner}};
            quad(pose, out, top, color, light, overlay, 0, 1, 0);
            float[][] bottom = {{ca * inner, 0, sa * inner}, {cb * inner, 0, sb * inner},
                    {cb * outer, 0, sb * outer}, {ca * outer, 0, sa * outer}};
            quad(pose, out, bottom, color, light, overlay, 0, -1, 0);
            float[][] edge = {{ca * outer, 0, sa * outer}, {cb * outer, 0, sb * outer},
                    {cb * outer, thickness, sb * outer}, {ca * outer, thickness, sa * outer}};
            quad(pose, out, edge, color, light, overlay, ca, 0, sa);
            float[][] inset = {{cb * inner, 0, sb * inner}, {ca * inner, 0, sa * inner},
                    {ca * inner, thickness, sa * inner}, {cb * inner, thickness, sb * inner}};
            quad(pose, out, inset, color, light, overlay, -ca, 0, -sa);
        }
    }

    private static void quad(PoseStack pose, VertexConsumer out, float[][] corners, int color,
                             int light, int overlay, float nx, float ny, float nz) {
        for (int i = 0; i < 4; i++)
            out.addVertex(pose.last(), corners[i][0], corners[i][1], corners[i][2]).setColor(color)
                    .setUv(i == 1 || i == 2 ? 1 : 0, i >= 2 ? 1 : 0).setOverlay(overlay).setLight(light)
                    .setNormal(pose.last(), nx, ny, nz);
    }

    /** Culling follows the whole machine, including the ring above its top layer. */
    @Override public AABB getRenderBoundingBox(AlchemyArrayBlockEntity block) {
        var origin = block.getBlockPos();
        Direction back = block.getBlockState().getValue(AlchemyArrayBlock.FACING).getOpposite();
        return new AABB(origin.getX() + back.getStepX() - 1, origin.getY() - 1, origin.getZ() + back.getStepZ() - 1,
                origin.getX() + back.getStepX() + 2, origin.getY() + 2.7, origin.getZ() + back.getStepZ() + 2);
    }
    @Override public boolean shouldRenderOffScreen(AlchemyArrayBlockEntity block) { return false; }
    @Override public int getViewDistance() { return 64; }
}
