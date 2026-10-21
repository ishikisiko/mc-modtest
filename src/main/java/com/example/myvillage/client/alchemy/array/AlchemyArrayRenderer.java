package com.example.myvillage.client.alchemy.array;

import com.example.myvillage.alchemy.array.AlchemyArrayBlock;
import com.example.myvillage.alchemy.array.AlchemyArrayBlockEntity;
import com.example.myvillage.alchemy.array.AlchemyArrayPattern;
import com.example.myvillage.alchemy.array.AlchemyArrayRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Formed presentation: a continuous copper cage, a risen alchemy core and port halos.
 * Component blocks, hitboxes and drops are never replaced; all state originates on the server.
 */
public final class AlchemyArrayRenderer implements BlockEntityRenderer<AlchemyArrayBlockEntity> {
    private static final ResourceLocation COPPER = ResourceLocation.withDefaultNamespace("textures/block/copper_block.png");
    private static final ResourceLocation PATINA = ResourceLocation.withDefaultNamespace("textures/block/oxidized_copper.png");
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("myvillage", "textures/block/alchemy_array.png");
    private static final int GLOW = 0xF000F0;
    private final Map<AlchemyArrayBlockEntity, Long> particleTicks = new WeakHashMap<>();
    private final Map<AlchemyArrayBlockEntity, Assembly> assemblies = new WeakHashMap<>();

    private static final class Assembly {
        boolean sawUnformed;
        long started = Long.MIN_VALUE;
    }

    public AlchemyArrayRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(AlchemyArrayBlockEntity block, float partialTick, PoseStack pose,
                                  MultiBufferSource buffers, int light, int overlay) {
        var level = block.getLevel();
        if (level == null) return;
        var rules = AlchemyArrayRules.get();
        Assembly assembly = assemblies.computeIfAbsent(block, ignored -> new Assembly());
        if (!block.formed()) {
            assembly.sawUnformed = true;
            assembly.started = Long.MIN_VALUE;
            return;
        }
        long now = level.getGameTime();
        if (assembly.started == Long.MIN_VALUE)
            assembly.started = assembly.sawUnformed ? now : now - rules.assemblyTicks();
        assembly.sawUnformed = false;
        float progress = Mth.clamp((now + partialTick - assembly.started) / (float) rules.assemblyTicks(), 0f, 1f);
        float eased = progress * progress * (3f - 2f * progress);
        Direction facing = block.getBlockState().getValue(AlchemyArrayBlock.FACING);
        Direction back = facing.getOpposite();
        boolean running = block.running();
        float cx = .5f + back.getStepX(), cy = .5f, cz = .5f + back.getStepZ();
        float time = now + partialTick;
        float coreY = 2.78f - .70f * (1f - eased)
                + (float) Math.sin(time * .06) * (float) rules.coreBob();

        pose.pushPose();
        cage(pose, buffers, cx, cy, cz, eased, light, overlay);
        crown(pose, buffers, cx, cz, eased, time, running, light, overlay);
        core(pose, buffers, cx, coreY, cz, eased, time, running, light, overlay);
        portHalos(pose, buffers, block, facing, eased, time, running, light, overlay);
        pose.popPose();

        if (running) {
            beams(pose, buffers, block, facing, cx, coreY, cz, time, light, overlay);
            emitParticles(block, facing, cx, coreY, cz);
        }
    }

    /** Twelve edge beams slide out of the loose components and lock the 3x3x3 silhouette together. */
    private static void cage(PoseStack pose, MultiBufferSource buffers, float cx, float cy, float cz,
                             float p, int light, int overlay) {
        float edge = 1.42f + .12f * p;
        float half = 1.54f * (.55f + .45f * p);
        float t = .06f;
        // BufferSource shares one builder per vertex format. Finish all copper
        // geometry before requesting the patina type, otherwise the shared
        // builder has already been ended by the second getBuffer call.
        VertexConsumer copper = buffers.getBuffer(RenderType.entityCutoutNoCull(COPPER));
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2)
            box(pose, copper, cx + sx * edge - t, cy - half, cz + sz * edge - t,
                    cx + sx * edge + t, cy + half, cz + sz * edge + t, 0xFFC08A45, light, overlay);
        for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) for (int sz = -1; sz <= 1; sz += 2)
            box(pose, copper, cx + sx * edge - .08f, cy + sy * edge - .08f, cz + sz * edge - .08f,
                    cx + sx * edge + .08f, cy + sy * edge + .08f, cz + sz * edge + .08f,
                    0xFFFFD98A, light, overlay);
        VertexConsumer patina = buffers.getBuffer(RenderType.entityCutoutNoCull(PATINA));
        for (int sy = -1; sy <= 1; sy += 2) for (int sz = -1; sz <= 1; sz += 2)
            box(pose, patina, cx - half, cy + sy * edge - t, cz + sz * edge - t,
                    cx + half, cy + sy * edge + t, cz + sz * edge + t, 0xFF4E8C7A, light, overlay);
        for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2)
            box(pose, patina, cx + sx * edge - t, cy + sy * edge - t, cz - half,
                    cx + sx * edge + t, cy + sy * edge + t, cz + half, 0xFF4E8C7A, light, overlay);
    }

    private static void crown(PoseStack pose, MultiBufferSource buffers, float cx, float cz,
                              float p, float time, boolean running, int light, int overlay) {
        var rules = AlchemyArrayRules.get();
        float speed = (float) (running ? rules.ringSpeedDegrees() : rules.coreSpeedDegrees());
        float angle = time * speed;

        // Lower collar stays around the vent neck, as in the first release.
        pose.pushPose();
        pose.translate(cx, 2.12f, cz);
        pose.mulPose(Axis.YP.rotationDegrees(angle * .35f));
        ring(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(COPPER)),
                .88f, .73f, .045f, light, overlay, false);
        pose.popPose();

        // The assembled crown grows outward while the core rises through it.
        float crownY = 2.44f - .22f * (1f - p);
        float radius = .34f + .44f * p;
        pose.pushPose();
        pose.translate(cx, crownY, cz);
        pose.mulPose(Axis.YP.rotationDegrees(-angle * .55f));
        ring(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(PATINA)),
                radius, radius * .82f, .035f, GLOW, overlay, true);
        pose.popPose();

        pose.pushPose();
        pose.translate(cx, crownY + .06f, cz);
        pose.mulPose(Axis.YP.rotationDegrees(angle * .9f));
        ring(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(COPPER)),
                radius * .72f, radius * .60f, .018f, GLOW, overlay, false);
        pose.popPose();
    }

    private static void core(PoseStack pose, MultiBufferSource buffers, float cx, float coreY, float cz,
                             float p, float time, boolean running, int light, int overlay) {
        var rules = AlchemyArrayRules.get();
        float scale = .55f + .45f * p;
        float alpha = running ? 1f : .86f;
        int fire = ((int) (0xE6 * alpha) << 24) | 0xFF8A2E;
        VertexConsumer atlas = buffers.getBuffer(RenderType.entityTranslucent(ATLAS));
        pose.pushPose();
        pose.translate(cx, coreY, cz);
        pose.mulPose(Axis.YP.rotationDegrees(time * (float) rules.coreSpeedDegrees() * 2.2f));
        octahedron(pose, atlas, .23f * scale, .30f * scale, fire, GLOW, overlay);
        pose.mulPose(Axis.YP.rotationDegrees(time * (float) rules.coreSpeedDegrees() * 3.7f));
        ring(pose, buffers.getBuffer(RenderType.entityTranslucent(ATLAS)),
                .31f * scale, .27f * scale, .018f, GLOW, overlay, true);
        pose.popPose();
    }

    private static void portHalos(PoseStack pose, MultiBufferSource buffers, AlchemyArrayBlockEntity block,
                                  Direction facing, float p, float time, boolean running, int light, int overlay) {
        if (p <= 0f) return;
        VertexConsumer atlas = buffers.getBuffer(RenderType.entityTranslucent(ATLAS));
        BlockPos origin = block.getBlockPos();
        for (var cell : AlchemyArrayRules.get().cells()) {
            if (cell.role() != AlchemyArrayRules.Role.INPUT
                    && cell.role() != AlchemyArrayRules.Role.OUTPUT
                    && cell.role() != AlchemyArrayRules.Role.FUEL) continue;
            BlockPos rel = AlchemyArrayPattern.worldPos(origin, facing, cell).subtract(origin);
            Direction outward = AlchemyArrayPattern.outward(facing, cell.role());
            float x = rel.getX() + .5f + outward.getStepX() * .575f;
            float y = rel.getY() + .5f + outward.getStepY() * .575f;
            float z = rel.getZ() + .5f + outward.getStepZ() * .575f;
            int rgb = switch (cell.role()) {
                case INPUT -> 0x66AA83;
                case OUTPUT -> 0xDECB9E;
                default -> 0x3A6C5E;
            };
            float[] uv = switch (cell.role()) {
                case INPUT -> swatch(5);
                case OUTPUT -> swatch(6);
                default -> swatch(2);
            };
            float pulse = running ? .72f + .28f * (float) Math.sin(time * .25) : .48f;
            int alpha = (int) (255f * p * pulse);
            faceRing(pose, atlas, x, y, z, outward, .36f, .034f, rgb, alpha, uv, GLOW, overlay);
        }
    }

    /** Running rune ribbons connect the floating core to the three dedicated ports. */
    private static void beams(PoseStack pose, MultiBufferSource buffers, AlchemyArrayBlockEntity block,
                              Direction facing, float cx, float coreY, float cz, float time,
                              int light, int overlay) {
        VertexConsumer atlas = buffers.getBuffer(RenderType.entityTranslucent(ATLAS));
        BlockPos origin = block.getBlockPos();
        for (var cell : AlchemyArrayRules.get().cells()) {
            if (cell.role() != AlchemyArrayRules.Role.INPUT
                    && cell.role() != AlchemyArrayRules.Role.OUTPUT
                    && cell.role() != AlchemyArrayRules.Role.FUEL) continue;
            BlockPos rel = AlchemyArrayPattern.worldPos(origin, facing, cell).subtract(origin);
            Direction outward = AlchemyArrayPattern.outward(facing, cell.role());
            float ex = rel.getX() + .5f + outward.getStepX() * .575f;
            float ey = rel.getY() + .5f + outward.getStepY() * .575f;
            float ez = rel.getZ() + .5f + outward.getStepZ() * .575f;
            int rgb = switch (cell.role()) {
                case INPUT -> 0x66AA83;
                case OUTPUT -> 0xDECB9E;
                default -> 0x3A6C5E;
            };
            int alpha = (int) (78f + 42f * (.5f + .5f * (float) Math.sin(time * .25 + cell.x())));
            float anchorY = 2.15f;
            ribbon(pose, atlas, cx, coreY, cz, ex, anchorY, ez, rgb, alpha, .030f, swatch(5), GLOW, overlay);
            ribbon(pose, atlas, ex, anchorY, ez, ex, ey, ez, rgb, alpha, .030f, swatch(5), GLOW, overlay);
        }
        ribbon(pose, atlas, cx, 2.39f, cz, cx, coreY, cz, 0xFF8A2E, 118, .085f, swatch(4), GLOW, overlay);
    }

    /** Flat broken ring lying on a horizontal port face. */
    private static void faceRing(PoseStack pose, VertexConsumer out, float cx, float cy, float cz,
                                 Direction normal, float radius, float thickness, int rgb, int alpha,
                                 float[] uv, int light, int overlay) {
        Direction u = normal.getClockWise();
        Direction v = Direction.UP;
        int color = (alpha << 24) | (rgb & 0xFFFFFF);
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float[][] corners = {
                    {cx + u.getStepX() * c0 * radius + v.getStepX() * s0 * radius,
                            cy + u.getStepY() * c0 * radius + v.getStepY() * s0 * radius,
                            cz + u.getStepZ() * c0 * radius + v.getStepZ() * s0 * radius},
                    {cx + u.getStepX() * c1 * radius + v.getStepX() * s1 * radius,
                            cy + u.getStepY() * c1 * radius + v.getStepY() * s1 * radius,
                            cz + u.getStepZ() * c1 * radius + v.getStepZ() * s1 * radius},
                    {cx + u.getStepX() * c1 * (radius - thickness) + v.getStepX() * s1 * (radius - thickness),
                            cy + u.getStepY() * c1 * (radius - thickness) + v.getStepY() * s1 * (radius - thickness),
                            cz + u.getStepZ() * c1 * (radius - thickness) + v.getStepZ() * s1 * (radius - thickness)},
                    {cx + u.getStepX() * c0 * (radius - thickness) + v.getStepX() * s0 * (radius - thickness),
                            cy + u.getStepY() * c0 * (radius - thickness) + v.getStepY() * s0 * (radius - thickness),
                            cz + u.getStepZ() * c0 * (radius - thickness) + v.getStepZ() * s0 * (radius - thickness)}
            };
            quad(pose, out, corners, color, light, overlay,
                    normal.getStepX(), normal.getStepY(), normal.getStepZ(), uv[0], uv[1], uv[2], uv[3]);
        }
    }

    private static void ribbon(PoseStack pose, VertexConsumer out, float sx, float sy, float sz,
                               float ex, float ey, float ez, int rgb, int alpha, float width,
                               float[] uv, int light, int overlay) {
        float dx = ex - sx, dy = ey - sy, dz = ez - sz;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < .0001f) return;
        dx /= length; dy /= length; dz /= length;
        float ux = dz, uy = 0, uz = -dx;
        float ul = Mth.sqrt(ux * ux + uz * uz);
        if (ul < .0001f) { ux = 1; uy = 0; uz = 0; ul = 1; }
        ux /= ul; uz /= ul;
        float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
        int color = (alpha << 24) | (rgb & 0xFFFFFF);
        for (int side = 0; side < 2; side++) {
            float px = side == 0 ? ux : vx, py = side == 0 ? uy : vy, pz = side == 0 ? uz : vz;
            float[][] corners = {
                    {sx + px * width, sy + py * width, sz + pz * width},
                    {ex + px * width, ey + py * width, ez + pz * width},
                    {ex - px * width, ey - py * width, ez - pz * width},
                    {sx - px * width, sy - py * width, sz - pz * width}
            };
            quad(pose, out, corners, color, light, overlay, px, py, pz, uv[0], uv[1], uv[2], uv[3]);
        }
    }

    private static void octahedron(PoseStack pose, VertexConsumer out, float radius, float height,
                                   int color, int light, int overlay) {
        float[] top = {0, height, 0}, bottom = {0, -height, 0};
        float[][] equator = {{radius, 0, 0}, {0, 0, radius}, {-radius, 0, 0}, {0, 0, -radius}};
        for (int i = 0; i < 4; i++) {
            float[] a = equator[i], b = equator[(i + 1) & 3];
            quad(pose, out, new float[][]{top, a, b, top}, color, light, overlay,
                    (a[0] + b[0]) * .5f, height * .5f, (a[2] + b[2]) * .5f, swatch(4));
            quad(pose, out, new float[][]{bottom, b, a, bottom}, color, light, overlay,
                    (a[0] + b[0]) * .5f, -height * .5f, (a[2] + b[2]) * .5f, swatch(4));
        }
    }

    private static void box(PoseStack pose, VertexConsumer out, float x0, float y0, float z0,
                            float x1, float y1, float z1, int color, int light, int overlay) {
        quad(pose, out, new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}},
                color, light, overlay, 0, 0, -1);
        quad(pose, out, new float[][]{{x1, y0, z1}, {x0, y0, z1}, {x0, y1, z1}, {x1, y1, z1}},
                color, light, overlay, 0, 0, 1);
        quad(pose, out, new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}},
                color, light, overlay, -1, 0, 0);
        quad(pose, out, new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}},
                color, light, overlay, 1, 0, 0);
        quad(pose, out, new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
                color, light, overlay, 0, -1, 0);
        quad(pose, out, new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}},
                color, light, overlay, 0, 1, 0);
    }

    /** Copper inlays with deliberate gaps make slow rotation readable at a distance. */
    private static void ring(PoseStack pose, VertexConsumer out, float outer, float inner, float thickness,
                             int light, int overlay, boolean qi) {
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

    private static float[] swatch(int index) {
        int column = index % 4, row = index / 4;
        return new float[]{column * .25f, row * .25f, (column + 1) * .25f, (row + 1) * .25f};
    }

    private static void quad(PoseStack pose, VertexConsumer out, float[][] corners, int color,
                             int light, int overlay, float nx, float ny, float nz) {
        quad(pose, out, corners, color, light, overlay, nx, ny, nz, 0, 0, 1, 1);
    }

    private static void quad(PoseStack pose, VertexConsumer out, float[][] corners, int color,
                             int light, int overlay, float nx, float ny, float nz, float[] uv) {
        quad(pose, out, corners, color, light, overlay, nx, ny, nz, uv[0], uv[1], uv[2], uv[3]);
    }

    private static void quad(PoseStack pose, VertexConsumer out, float[][] corners, int color,
                             int light, int overlay, float nx, float ny, float nz,
                             float u0, float v0, float u1, float v1) {
        for (int i = 0; i < 4; i++)
            out.addVertex(pose.last(), corners[i][0], corners[i][1], corners[i][2]).setColor(color)
                    .setUv(i == 1 || i == 2 ? u1 : u0, i >= 2 ? v1 : v0)
                    .setOverlay(overlay).setLight(light)
                    .setNormal(pose.last(), nx, ny, nz);
    }

    private void emitParticles(AlchemyArrayBlockEntity block, Direction facing,
                               float cx, float coreY, float cz) {
        var level = block.getLevel();
        if (level == null) return;
        var rules = AlchemyArrayRules.get();
        long tick = level.getGameTime();
        if (tick % rules.smokeIntervalTicks() != 0 || particleTicks.getOrDefault(block, -1L) == tick) return;
        particleTicks.put(block, tick);
        var pos = block.getBlockPos();
        double wx = pos.getX() + cx, wz = pos.getZ() + cz;
        double wy = pos.getY() + coreY;
        double spread = .28;
        level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                wx + (level.random.nextDouble() - .5) * spread, pos.getY() + 2.35,
                wz + (level.random.nextDouble() - .5) * spread, 0, .025, 0);
        double theta = tick * .13;
        level.addParticle(ParticleTypes.ENCHANT, wx + Math.cos(theta) * .68, wy,
                wz + Math.sin(theta) * .68, 0, .03, 0);
        level.addParticle(ParticleTypes.END_ROD, wx + Math.cos(theta) * .27, wy,
                wz + Math.sin(theta) * .27, 0, .012, 0);
        if (tick % 16 == 0)
            level.addParticle(ParticleTypes.FLAME, wx + (level.random.nextDouble() - .5) * .22,
                    wy - .18, wz + (level.random.nextDouble() - .5) * .22, 0, .018, 0);
        level.addParticle(ParticleTypes.FLAME, pos.getX() + .5 + facing.getStepX() * .54,
                pos.getY() + .28, pos.getZ() + .5 + facing.getStepZ() * .54, 0, .012, 0);
    }

    /** Culling follows the whole machine, including the cage and floating core. */
    @Override public AABB getRenderBoundingBox(AlchemyArrayBlockEntity block) {
        var origin = block.getBlockPos();
        Direction back = block.getBlockState().getValue(AlchemyArrayBlock.FACING).getOpposite();
        return new AABB(origin.getX() + back.getStepX() - 1.7, origin.getY() - 1.7,
                origin.getZ() + back.getStepZ() - 1.7,
                origin.getX() + back.getStepX() + 2.7, origin.getY() + 3.6,
                origin.getZ() + back.getStepZ() + 2.7);
    }
    @Override public boolean shouldRenderOffScreen(AlchemyArrayBlockEntity block) { return false; }
    @Override public int getViewDistance() { return 64; }
}
