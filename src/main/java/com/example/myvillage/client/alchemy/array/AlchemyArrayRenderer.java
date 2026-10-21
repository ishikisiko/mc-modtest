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
 * The formed array is drawn as one continuous bronze furnace rather than a cage
 * around the 3x3x3 component blocks.  The component blocks, collision, drops and
 * server-side structure checks remain untouched; every surface below is only the
 * controller's presentation layer.
 */
public final class AlchemyArrayRenderer implements BlockEntityRenderer<AlchemyArrayBlockEntity> {
    private static final ResourceLocation SHELL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("myvillage", "textures/block/alchemy_array_formed.png");
    private static final int GLOW = 0xF000F0;
    private static final int SHELL = 0;
    private static final int SHELL_LIGHT = 1;
    private static final int SHELL_DARK = 2;
    private static final int TRIM = 3;
    private static final int PATINA = 4;
    private static final int JADE = 5;
    private static final int FIRE = 6;
    private static final int SOOT = 7;
    private static final int STONE = 8;
    private static final int CERAMIC = 9;
    private static final int COPPER = 10;
    private static final int ASH = 11;
    private static final int RIVET = 12;
    private static final int RUNE = 13;
    private static final int EMBER = 14;
    private static final int GOLD = 15;

    private final Map<AlchemyArrayBlockEntity, Long> particleTicks = new WeakHashMap<>();
    private final Map<AlchemyArrayBlockEntity, Assembly> assemblies = new WeakHashMap<>();

    private static final class Assembly {
        boolean sawUnformed;
        long started = Long.MIN_VALUE;
    }

    /** Local coordinates: +x is the screen-right side, +z is the furnace back. */
    private record Frame(float cx, float cy, float cz, Direction right, Direction back) {
        float wx(float lx, float lz) { return cx + right.getStepX() * lx + back.getStepX() * lz; }
        float wz(float lx, float lz) { return cz + right.getStepZ() * lx + back.getStepZ() * lz; }
        float wy(float ly) { return cy + ly; }
        float nx(float x, float z) { return right.getStepX() * x + back.getStepX() * z; }
        float nz(float x, float z) { return right.getStepZ() * x + back.getStepZ() * z; }
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
        Direction right = facing.getClockWise();
        float cx = .5f + back.getStepX(), cy = .5f, cz = .5f + back.getStepZ();
        Frame frame = new Frame(cx, cy, cz, right, back);
        boolean running = block.running();
        float time = now + partialTick;

        // The shell expands from a slightly smaller footprint.  At the start of
        // the animation the loose components remain visible around it; at the
        // end it is a single continuous furnace skin.
        float grow = .84f + .16f * eased;
        pose.pushPose();
        pose.translate(cx, 0f, cz);
        pose.scale(grow, 1f, grow);
        pose.translate(-cx, 0f, -cz);

        VertexConsumer opaque = buffers.getBuffer(RenderType.entityCutoutNoCull(SHELL_TEXTURE));
        drawBase(pose, opaque, frame, light, overlay);
        drawBody(pose, opaque, frame, time, running, light, overlay);
        drawFront(pose, opaque, frame, time, running, light, overlay);
        drawPorts(pose, opaque, frame, time, running, light, overlay);

        // The hood and lid drop into place after the body has grown.
        float lidFall = .95f * (1f - eased);
        pose.pushPose();
        pose.translate(0f, lidFall, 0f);
        drawShoulderAndLid(pose, opaque, frame, time, running, light, overlay);
        drawHandles(pose, opaque, frame, light, overlay);
        pose.popPose();
        pose.popPose();

        // Glow is a separate material pass.  Never request another render type
        // while the previous shared-format buffer is still being used.
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucent(SHELL_TEXTURE));
        drawFireboxGlow(pose, glow, frame, time, running, overlay);
        drawFloatingCore(pose, glow, frame, time, running, eased, overlay);
        drawPortHalos(pose, glow, frame, time, running, overlay);

        emitParticles(block, frame, time);
    }

    private static void drawBase(PoseStack pose, VertexConsumer out, Frame f, int light, int overlay) {
        // A low plinth and three claw feet.  The feet are outside the body, so
        // the silhouette reads as a vessel rather than a block sitting on grass.
        prism(pose, out, f, -1.48f, -1.32f, 1.70f, 1.34f, 1.70f, 1.34f, STONE, light, overlay, false, .0f);
        ringCap(pose, out, f, -1.32f, 1.70f, 1.34f, 1.56f, 1.22f, TRIM, light, overlay, false);
        prism(pose, out, f, -1.32f, -1.04f, 1.70f, 1.34f, 1.76f, 1.28f, SHELL_DARK, light, overlay, false, .0f);
        ringCap(pose, out, f, -1.04f, 1.76f, 1.28f, 1.58f, 1.14f, GOLD, light, overlay, false);
        // Three forward-set tripod legs.  They stand outside the plinth, so the
        // lower silhouette is a vessel on feet rather than a box on a slab.
        for (float[] foot : new float[][]{{-1.28f, -1.42f}, {1.28f, -1.42f}, {0f, 1.50f}}) {
            float x = foot[0], z = foot[1];
            boxL(pose, out, f, x - .16f, -1.50f, z - .16f, x + .16f, -1.02f, z + .16f,
                    SHELL_DARK, light, overlay);
            boxL(pose, out, f, x - .21f, -1.22f, z - .21f, x + .21f, -1.10f, z + .21f,
                    TRIM, light, overlay);
            boxL(pose, out, f, x - .20f, -1.50f, z - .38f, x + .20f, -1.36f, z + .28f,
                    STONE, light, overlay);
            boxL(pose, out, f, x - .12f, -1.50f, z - .44f, x + .12f, -1.38f, z - .22f,
                    GOLD, light, overlay);
        }
    }

    private static void drawBody(PoseStack pose, VertexConsumer out, Frame f, float time,
                                 boolean running, int light, int overlay) {
        // A three-part barrel silhouette: narrower foot and shoulder around a
        // wider belly.  The front face is always omitted and rebuilt around the
        // arch, so the furnace mouth reads as a recess rather than a flat tile.
        prism(pose, out, f, -1.04f, -.50f, 1.76f, 1.28f, 1.76f, 1.28f, SHELL, light, overlay, true, .0f);
        prism(pose, out, f, -.50f, .50f, 1.86f, 1.18f, 1.86f, 1.18f, SHELL, light, overlay, true, .0f);
        prism(pose, out, f, .50f, 1.50f, 1.76f, 1.28f, 1.76f, 1.28f, SHELL, light, overlay, true, .0f);
        // Raised skin plates make the individual octagonal faces visible without
        // falling back to a 3x3 grid of component-block textures.
        prism(pose, out, f, -1.00f, -.54f, 1.768f, 1.288f, 1.768f, 1.288f, SHELL_LIGHT, light, overlay, true, .0f);
        prism(pose, out, f, -.46f, .46f, 1.868f, 1.188f, 1.868f, 1.188f, SHELL_LIGHT, light, overlay, true, .0f);
        prism(pose, out, f, .54f, 1.46f, 1.768f, 1.288f, 1.768f, 1.288f, SHELL_LIGHT, light, overlay, true, .0f);

        // The horizontal bands follow the bulge and give the body a cast-metal
        // profile instead of a rectangular crate.
        band(pose, out, f, -.58f, -.50f, 1.87f, 1.19f, GOLD, light, overlay, true);
        band(pose, out, f, .50f, .58f, 1.87f, 1.19f, GOLD, light, overlay, true);
        band(pose, out, f, -.20f, -.12f, 1.875f, 1.195f, PATINA, light, overlay, true);
        band(pose, out, f, 1.33f, 1.41f, 1.775f, 1.295f, TRIM, light, overlay, true);
        band(pose, out, f, .83f, .87f, 1.875f, 1.195f, RUNE, GLOW, overlay, true);
        band(pose, out, f, -1.00f, -.96f, 1.775f, 1.295f, PATINA, light, overlay, true);

        // Vertical ribs at the octagon vertices, split around the belly.
        for (float[] section : new float[][]{{-1.04f, -.50f, 1.76f, 1.28f},
                {-.50f, .50f, 1.86f, 1.18f}, {.50f, 1.50f, 1.76f, 1.28f}}) {
            float y0 = section[0], y1 = section[1], r = section[2], c = section[3];
            for (float[] p : octagon(r, c))
                boxL(pose, out, f, p[0] - .042f, y0, p[1] - .042f,
                        p[0] + .042f, y1, p[1] + .042f, SHELL_DARK, light, overlay);
        }

        // Rivets on the readable faces.  The belly carries the middle row.
        for (int sy = -1; sy <= 1; sy++) {
            float y = -.92f + sy * .74f;
            for (int sx = -1; sx <= 1; sx += 2) {
                rivet(pose, out, f, sx * 1.58f, y, -1.83f, light, overlay);
                rivet(pose, out, f, sx * 1.91f, y, .48f, light, overlay);
                rivet(pose, out, f, sx * 1.20f, y, 1.90f, light, overlay);
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 3; i++) {
                float z = -.82f + i * .70f;
                boxL(pose, out, f, side * 1.875f, -.46f + i * .11f, z,
                        side * 1.885f, -.04f + i * .11f, z + .10f, PATINA, light, overlay);
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int z = -1; z <= 1; z += 2) {
                float zz = z * .72f;
                boxL(pose, out, f, side * 1.862f, -1.00f, zz - .035f,
                        side * 1.872f, 1.44f, zz + .035f, SHELL_DARK, light, overlay);
            }
        }
    }

    private static void drawFront(PoseStack pose, VertexConsumer out, Frame f, float time,
                                  boolean running, int light, int overlay) {
        float z = -1.88f;
        // The two side piers and the lintel are a real frame around the mouth.
        boxL(pose, out, f, -1.30f, -1.04f, z, -.72f, 1.50f, -1.80f, SHELL, light, overlay);
        boxL(pose, out, f, .72f, -1.04f, z, 1.30f, 1.50f, -1.80f, SHELL, light, overlay);
        boxL(pose, out, f, -1.30f, 1.18f, z, 1.30f, 1.50f, -1.80f, SHELL, light, overlay);
        boxL(pose, out, f, -1.30f, -1.04f, z, 1.30f, -.94f, -1.80f, SHELL_DARK, light, overlay);

        // Recessed soot back.  This plane deliberately sits just in front of the
        // controller block, hiding its 16px face while leaving the block itself
        // and its interaction hitbox untouched.
        boxL(pose, out, f, -.74f, -.94f, -1.52f, .74f, 1.05f, -1.47f, SOOT, light, overlay);
        boxL(pose, out, f, -.58f, -1.00f, -.98f, .58f, -.88f, -.30f, ASH, light, overlay);
        boxL(pose, out, f, -.54f, -.94f, -1.50f, .54f, -.88f, -1.22f, EMBER, GLOW, overlay);

        // A heavy arch, plus a slightly wider outer hood.  The opening stays a
        // real hole, so the fire and the dark interior remain readable.
        archRing(pose, out, f, z - .012f, .30f, .72f, .10f, 18, TRIM, light, overlay);
        archRing(pose, out, f, z - .020f, .30f, .88f, .055f, 18, SHELL_DARK, light, overlay);
        for (int sx = -1; sx <= 1; sx += 2) {
            boxL(pose, out, f, sx * .78f - .055f, -.96f, z - .025f,
                    sx * .78f + .055f, .30f, z + .020f, TRIM, light, overlay);
            boxL(pose, out, f, sx * 1.06f - .035f, -.96f, z - .018f,
                    sx * 1.06f + .035f, 1.16f, z + .012f, GOLD, light, overlay);
        }
        // A projecting mantel above the mouth catches the eye and breaks the
        // square front profile.
        boxL(pose, out, f, -1.00f, 1.12f, -1.82f, 1.00f, 1.22f, -1.58f, SHELL_LIGHT, light, overlay);
        boxL(pose, out, f, -1.10f, 1.22f, -1.78f, 1.10f, 1.28f, -1.60f, GOLD, light, overlay);
        for (int sx = -1; sx <= 1; sx += 2)
            boxL(pose, out, f, sx * .84f - .07f, .98f, -1.77f, sx * .84f + .07f, 1.16f, -1.66f,
                    TRIM, light, overlay);

        // Grate and fire sill.  These pieces sit inside the opening and do not
        // cover the controller's clickable front face.
        for (int i = -1; i <= 1; i++) {
            boxL(pose, out, f, -.54f, -.83f + i * .13f, -1.78f,
                    .54f, -.79f + i * .13f, -1.73f, SHELL_DARK, light, overlay);
        }
        for (int i = -1; i <= 1; i++) {
            boxL(pose, out, f, i * .36f - .022f, -.96f, -1.76f,
                    i * .36f + .022f, .27f, -1.71f, SHELL_DARK, light, overlay);
        }
        // Jade status seal on the lintel: dim while idle, bright while brewing.
        disc(pose, out, f, 0f, 1.34f, z - .035f, f.back().getOpposite(), .10f, JADE,
                running ? GLOW : light, overlay, 20, 150);
        boxL(pose, out, f, -.15f, 1.31f, z - .045f, .15f, 1.37f, z - .025f, GOLD, light, overlay);
    }

    private static void drawPorts(PoseStack pose, VertexConsumer out, Frame f, float time,
                                  boolean running, int light, int overlay) {
        // Input side: a green porthole with a receiving tray.  The actual port
        // block is behind the shell, so no component texture leaks into the
        // formed silhouette.
        portPlate(pose, out, f, -1.905f, 0f, 0f, PATINA, light, overlay);
        faceRing(pose, out, f, -1.925f, .0f, .0f, f.right().getOpposite(), .38f, .075f,
                0xFFFFFF, 255, JADE, GLOW, overlay);
        disc(pose, out, f, -1.925f, .0f, .0f, f.right().getOpposite(), .290f,
                PATINA, GLOW, overlay, 20, 210);
        boxL(pose, out, f, -2.16f, -.48f, -.40f, -1.86f, -.28f, .40f, TRIM, light, overlay);
        boxL(pose, out, f, -2.09f, -.40f, -.30f, -1.92f, -.24f, .30f, SHELL_DARK, light, overlay);
        boxL(pose, out, f, -1.98f, -.22f, -.32f, -1.88f, -.08f, .32f, COPPER, light, overlay);
        for (int sz = -1; sz <= 1; sz += 2)
            rivet(pose, out, f, -1.96f, .30f, sz * .34f, light, overlay);

        // Output side: a lower spout and a shallow pill trough.  It is readable
        // from the player's left/right and never implies stored items.
        portPlate(pose, out, f, 1.905f, -.98f, 0f, CERAMIC, light, overlay);
        faceRing(pose, out, f, 1.925f, -.98f, 0f, f.right(), .37f, .070f,
                0xFFFFFF, 255, GOLD, GLOW, overlay);
        disc(pose, out, f, 1.925f, -.98f, 0f, f.right(), .280f,
                SOOT, GLOW, overlay, 20, 190);
        // A bronze tap, not a beige crate: the pipe ends in a downspout and a
        // shallow ceramic receiving bowl.
        boxL(pose, out, f, 1.82f, -1.04f, -.16f, 2.14f, -.84f, .16f, COPPER, light, overlay);
        boxL(pose, out, f, 2.12f, -1.14f, -.12f, 2.26f, -.82f, .12f, TRIM, light, overlay);
        boxL(pose, out, f, 2.20f, -1.16f, -.08f, 2.30f, -1.00f, .08f, GOLD, light, overlay);
        boxL(pose, out, f, 2.02f, -1.28f, -.30f, 2.38f, -1.14f, .30f, COPPER, light, overlay);
        boxL(pose, out, f, 2.06f, -1.18f, -.22f, 2.34f, -1.12f, .22f, SOOT, light, overlay);
        boxL(pose, out, f, 2.00f, -1.15f, -.32f, 2.40f, -1.10f, .32f, GOLD, light, overlay);
        boxL(pose, out, f, 1.98f, -.84f, -.22f, 2.06f, -.72f, -.14f, GOLD, light, overlay);
        boxL(pose, out, f, 2.06f, -.88f, -.22f, 2.34f, -.72f, -.14f, TRIM, light, overlay);

        // Back fuel hatch: a square bronze door with a jade diamond.  The hatch
        // face is intentionally larger than the old 16px fuel panel.
        boxL(pose, out, f, -.50f, -.50f, 1.86f, .50f, .50f, 1.92f, SHELL_DARK, light, overlay);
        boxL(pose, out, f, -.44f, -.44f, 1.915f, .44f, .44f, 1.98f, COPPER, light, overlay);
        boxL(pose, out, f, -.40f, -.40f, 1.975f, .40f, .40f, 1.99f, SOOT, light, overlay);
        diamond(pose, out, f, 0f, 0f, 2.00f, .28f, JADE, GLOW, overlay);
        faceRing(pose, out, f, 0f, 0f, 2.005f, f.back(), .37f, .040f,
                0xFFFFFF, 230, JADE, GLOW, overlay);
        for (int sx = -1; sx <= 1; sx += 2) {
            boxL(pose, out, f, sx * .36f - .035f, -.38f, 2.00f,
                    sx * .36f + .035f, -.28f, 2.03f, TRIM, light, overlay);
        }
    }

    private static void drawShoulderAndLid(PoseStack pose, VertexConsumer out, Frame f, float time,
                                           boolean running, int light, int overlay) {
        // A rolled lip and a curved bell dome.  The lip is narrow and the dome
        // begins inside it, so the top reads as a vessel lid rather than a roof.
        prism(pose, out, f, 1.48f, 1.68f, 1.88f, 1.57f, 1.88f, 1.57f, SHELL_DARK, light, overlay, false, .0f);
        ringCap(pose, out, f, 1.68f, 1.88f, 1.57f, 1.55f, 1.32f, TRIM, light, overlay, false);
        band(pose, out, f, 1.62f, 1.68f, 1.905f, 1.595f, GOLD, light, overlay, false);
        band(pose, out, f, 1.48f, 1.53f, 1.90f, 1.59f, PATINA, light, overlay, false);

        frustum(pose, out, f, 1.68f, 1.88f, 1.55f, 1.32f, 1.30f, 1.10f, SHELL, light, overlay);
        frustum(pose, out, f, 1.88f, 2.05f, 1.30f, 1.10f, .98f, .82f, SHELL_LIGHT, light, overlay);
        frustum(pose, out, f, 2.05f, 2.18f, .98f, .82f, .62f, .52f, SHELL, light, overlay);
        frustum(pose, out, f, 2.18f, 2.26f, .62f, .52f, .38f, .32f, SHELL_LIGHT, light, overlay);
        ringCap(pose, out, f, 2.26f, .38f, .32f, .24f, .20f, TRIM, light, overlay, false);
        band(pose, out, f, 1.68f, 1.72f, 1.57f, 1.34f, PATINA, light, overlay, false);
        band(pose, out, f, 2.02f, 2.06f, 1.00f, .84f, GOLD, light, overlay, false);

        // Eight ribs, following the dome radius at each tier.
        for (int i = 0; i < 8; i++) {
            float a = (float) (Math.PI * 2 * i / 8 + Math.PI / 8);
            float cx = (float) Math.cos(a), cz = (float) Math.sin(a);
            for (float[] rib : new float[][]{{1.55f, 1.32f, 1.68f, 1.88f, 1.30f, 1.10f},
                    {1.30f, 1.10f, 1.88f, 2.05f, .98f, .82f}, {.98f, .82f, 2.05f, 2.18f, .62f, .52f}}) {
                float r0 = rib[0], c0 = rib[1], y0 = rib[2], y1 = rib[3], r1 = rib[4], c1 = rib[5];
                float x0 = cx * r0, z0 = cz * c0, x1 = cx * r1, z1 = cz * c1;
                float nx = x1 - x0, nz = z1 - z0;
                float len = Mth.sqrt(nx * nx + nz * nz);
                if (len < .001f) continue;
                nx /= len; nz /= len;
                // Thin raised ridge; no flat plate over the opening.
                quadL(pose, out, f,
                        new float[][]{{x0 - nz * .035f, y0, z0 + nx * .035f},
                                {x0 + nz * .035f, y0, z0 - nx * .035f},
                                {x1 + nz * .035f, y1, z1 - nx * .035f},
                                {x1 - nz * .035f, y1, z1 + nx * .035f}},
                        f.nx(nx, nz), .2f, f.nz(nx, nz), tint(255, 1f), light, overlay, swatch(TRIM));
            }
        }

        // Crown flue.
        prism(pose, out, f, 2.24f, 2.58f, .31f, .26f, .31f, .26f, COPPER, light, overlay, false, .0f);
        band(pose, out, f, 2.48f, 2.54f, .36f, .30f, GOLD, light, overlay, false);
        ringCap(pose, out, f, 2.58f, .39f, .33f, .22f, .19f, TRIM, light, overlay, false);
        for (int i = 0; i < 4; i++) {
            float a = (float) (Math.PI * .5 * i + Math.PI * .25);
            float x = (float) Math.cos(a) * .33f, z = (float) Math.sin(a) * .28f;
            boxL(pose, out, f, x - .03f, 2.54f, z - .03f, x + .03f, 2.62f, z + .03f,
                    GOLD, light, overlay);
        }
    }

    private static void drawHandles(PoseStack pose, VertexConsumer out, Frame f, int light, int overlay) {
        // Front handles and side handles are separate rings, not another cage.
        for (int sx = -1; sx <= 1; sx += 2) {
            boxL(pose, out, f, sx * 1.10f - .10f, 1.40f, -1.98f,
                    sx * 1.10f + .10f, 1.58f, -1.82f, SHELL_DARK, light, overlay);
            faceRing(pose, out, f, sx * 1.10f, 1.48f, -2.00f, f.back().getOpposite(), .30f, .050f,
                    0xFFFFFF, 255, TRIM, light, overlay);
            faceRing(pose, out, f, sx * 1.91f, 1.38f, .78f, sx > 0 ? f.right() : f.right().getOpposite(),
                    .36f, .055f, 0xFFFFFF, 255, TRIM, light, overlay);
        }
    }

    private static void drawFireboxGlow(PoseStack pose, VertexConsumer out, Frame f, float time,
                                        boolean running, int overlay) {
        float pulse = .78f + .22f * (float) Math.sin(time * .22);
        int alpha = (int) ((running ? 225 : 140) * pulse);
        // Three flame tongues behind the grate.
        for (int i = -1; i <= 1; i++) {
            float h = (i == 0 ? 1.00f : .72f) + .07f * (float) Math.sin(time * .19 + i);
            float x = i * .37f;
            quadL(pose, out, f,
                    new float[][]{{x - .18f, -.96f, -1.545f}, {x + .18f, -.96f, -1.545f},
                            {x + .13f, -.96f + h, -1.545f}, {x - .13f, -.96f + h, -1.545f}},
                    0f, 0f, -1f, tint(alpha, 1f), GLOW, overlay, swatch(FIRE));
        }
        // Thin hot line along the sill.
        boxL(pose, out, f, -.48f, -1.02f, -1.548f, .48f, -.985f, -1.54f,
                EMBER, GLOW, overlay);
        // A small orbiting ember inside the mouth.
        float a = time * .08f;
        float ex = (float) Math.cos(a) * .28f, ey = -.35f + (float) Math.sin(a * 1.7f) * .18f;
        sphere(pose, out, f, ex, ey, -1.535f, .035f, 190, swatch(EMBER), GLOW, overlay);
    }

    private static void drawFloatingCore(PoseStack pose, VertexConsumer out, Frame f, float time,
                                         boolean running, float p, int overlay) {
        var rules = AlchemyArrayRules.get();
        float rise = .72f * (1f - p);
        float bob = (float) Math.sin(time * .06) * (float) rules.coreBob();
        float y = 3.10f - rise + bob;
        float spin = time * (float) (running ? rules.ringSpeedDegrees() : rules.coreSpeedDegrees());

        // The core floats above the funnel-shaped lid.  Its rings are the only
        // horizontal furniture up here; the lid itself is part of the furnace.
        // Halo rings around the pill: one horizontal, one tilted, both slow.
        pose.pushPose();
        pose.translate(f.wx(0f, 0f), y, f.wz(0f, 0f));
        pose.mulPose(Axis.YP.rotationDegrees(-spin * 1.25f));
        sphere(pose, out, 0f, 0f, 0f, .58f, running ? 62 : 42, swatch(PATINA), GLOW, overlay);
        sphere(pose, out, 0f, 0f, 0f, .43f, running ? 128 : 96, swatch(FIRE), GLOW, overlay);
        sphere(pose, out, 0f, 0f, 0f, .32f, running ? 220 : 175, swatch(EMBER), GLOW, overlay);
        sphere(pose, out, 0f, 0f, 0f, .19f, 255, swatch(GOLD), GLOW, overlay);
        pose.mulPose(Axis.ZP.rotationDegrees(18f));
        flatRing(pose, out, .72f, .62f, .020f, JADE, 165, GLOW, overlay, true);
        pose.mulPose(Axis.YP.rotationDegrees(90f));
        flatRing(pose, out, .88f, .78f, .016f, RUNE, 130, GLOW, overlay, true);
        pose.popPose();

        // Two crossed vertical halos keep the floating pill readable from eye
        // level, where a horizontal lid ring is seen almost edge-on.  These are
        // in world space: faceRing already applies the frame transform.
        faceRing(pose, out, f, f.wx(0f, 0f), y, f.wz(0f, 0f), f.right(),
                .84f, .018f, 0xFFFFFF, 170, RUNE, GLOW, overlay);
        faceRing(pose, out, f, f.wx(0f, 0f), y, f.wz(0f, 0f), f.back(),
                .84f, .018f, 0xFFFFFF, 150, JADE, GLOW, overlay);
        // Thin gold bands around the pill make it read as a forged elixir rather
        // than a solid orange ball.
        faceRing(pose, out, f, f.wx(0f, 0f), y, f.wz(0f, 0f), f.right(),
                .34f, .012f, 0xFFFFFF, 235, GOLD, GLOW, overlay);
        faceRing(pose, out, f, f.wx(0f, 0f), y, f.wz(0f, 0f), f.back(),
                .34f, .012f, 0xFFFFFF, 235, GOLD, GLOW, overlay);
    }

    private static void drawPortHalos(PoseStack pose, VertexConsumer out, Frame f, float time,
                                      boolean running, int overlay) {
        float pulse = running ? .70f + .30f * (float) Math.sin(time * .25) : .38f;
        int a = (int) (210f * pulse);
        faceRing(pose, out, f, -1.95f, 0f, 0f, f.right().getOpposite(), .48f, .045f,
                0xFFFFFF, a, JADE, GLOW, overlay);
        faceRing(pose, out, f, 1.95f, -.98f, 0f, f.right(), .47f, .043f,
                0xFFFFFF, a, GOLD, GLOW, overlay);
        faceRing(pose, out, f, 0f, 0f, 2.035f, f.back(), .48f, .043f,
                0xFFFFFF, a, JADE, GLOW, overlay);
    }

    private static void portPlate(PoseStack pose, VertexConsumer out, Frame f, float x, float y, float z,
                                  int uv, int light, int overlay) {
        boxL(pose, out, f, x - .04f, y - .46f, z - .46f, x + .04f, y + .46f, z + .46f,
                SHELL_DARK, light, overlay);
        boxL(pose, out, f, x - .02f, y - .38f, z - .38f, x + .02f, y + .38f, z + .38f,
                uv, light, overlay);
    }

    private static void rivet(PoseStack pose, VertexConsumer out, Frame f, float x, float y, float z,
                              int light, int overlay) {
        float h = .045f;
        boxL(pose, out, f, x - h, y - h, z - h, x + h, y + h, z + h, RIVET, light, overlay);
    }

    private static void band(PoseStack pose, VertexConsumer out, Frame f, float y0, float y1,
                             float r, float c, int uv, int light, int overlay, boolean skipFront) {
        prism(pose, out, f, y0, y1, r, c, r, c, uv, light, overlay, skipFront, .0f);
    }

    private static void prism(PoseStack pose, VertexConsumer out, Frame f, float y0, float y1,
                              float r0, float c0, float r1, float c1, int uv,
                              int light, int overlay, boolean skipFront, float shadeBias) {
        float[][] p0 = octagon(r0, c0), p1 = octagon(r1, c1);
        for (int i = 0; i < 8; i++) {
            if (skipFront && i == 6) continue;
            int j = (i + 1) & 7;
            float dx = p0[j][0] - p0[i][0], dz = p0[j][1] - p0[i][1];
            float nx = dz, nz = -dx;
            float len = Mth.sqrt(nx * nx + nz * nz);
            if (len < .0001f) continue;
            nx /= len; nz /= len;
            float mx = (p0[i][0] + p0[j][0]) * .5f, mz = (p0[i][1] + p0[j][1]) * .5f;
            if (nx * mx + nz * mz < 0f) { nx = -nx; nz = -nz; }
            float shade = wallShade(nx, nz) + shadeBias;
            quadL(pose, out, f,
                    new float[][]{{p0[i][0], y0, p0[i][1]}, {p0[j][0], y0, p0[j][1]},
                            {p1[j][0], y1, p1[j][1]}, {p1[i][0], y1, p1[i][1]}},
                    f.nx(nx, nz), 0f, f.nz(nx, nz), tint(255, shade), light, overlay, swatch(uv));
        }
    }

    private static void frustum(PoseStack pose, VertexConsumer out, Frame f, float y0, float y1,
                                float r0, float c0, float r1, float c1, int uv, int light, int overlay) {
        prism(pose, out, f, y0, y1, r0, c0, r1, c1, uv, light, overlay, false, 0f);
    }

    /** A ring of eight top faces, used for plinths, rims and lid collars. */
    private static void ringCap(PoseStack pose, VertexConsumer out, Frame f, float y,
                                float or, float oc, float ir, float ic, int uv,
                                int light, int overlay, boolean bottom) {
        float[][] o = octagon(or, oc), i = octagon(ir, ic);
        for (int n = 0; n < 8; n++) {
            int m = (n + 1) & 7;
            float[][] corners = bottom
                    ? new float[][]{{o[n][0], y, o[n][1]}, {i[n][0], y, i[n][1]},
                            {i[m][0], y, i[m][1]}, {o[m][0], y, o[m][1]}}
                    : new float[][]{{o[n][0], y, o[n][1]}, {o[m][0], y, o[m][1]},
                            {i[m][0], y, i[m][1]}, {i[n][0], y, i[n][1]}};
            quadL(pose, out, f, corners, 0f, bottom ? -1f : 1f, 0f,
                    tint(255, bottom ? .48f : 1f), light, overlay, swatch(uv));
        }
    }

    private static void archRing(PoseStack pose, VertexConsumer out, Frame f, float z, float spring,
                                 float radius, float thickness, int segments, int uv,
                                 int light, int overlay) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * i / segments, a1 = Math.PI * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float[][] corners = {
                    {c0 * radius, spring + s0 * radius, z},
                    {c1 * radius, spring + s1 * radius, z},
                    {c1 * (radius - thickness), spring + s1 * (radius - thickness), z},
                    {c0 * (radius - thickness), spring + s0 * (radius - thickness), z}
            };
            quadL(pose, out, f, corners, 0f, 0f, -1f, tint(255, .9f), light, overlay, swatch(uv));
        }
    }

    private static void diamond(PoseStack pose, VertexConsumer out, Frame f, float x, float y, float z,
                                float radius, int uv, int light, int overlay) {
        quadL(pose, out, f,
                new float[][]{{x, y + radius, z}, {x + radius, y, z},
                        {x, y - radius, z}, {x - radius, y, z}},
                0f, 0f, 1f, tint(255, 1f), light, overlay, swatch(uv));
    }

    private static void disc(PoseStack pose, VertexConsumer out, Frame f, float x, float y, float z,
                             Direction normal, float radius, int uv, int light, int overlay,
                             int segments, int alpha) {
        Direction u = normal.getClockWise();
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float[][] corners = {
                    {x, y, z},
                    {x + u.getStepX() * c0 * radius, y + s0 * radius, z + u.getStepZ() * c0 * radius},
                    {x + u.getStepX() * c1 * radius, y + s1 * radius, z + u.getStepZ() * c1 * radius},
                    {x, y, z}
            };
            quadL(pose, out, f, corners, normal.getStepX(), normal.getStepY(), normal.getStepZ(),
                    tint(alpha, 1f), light, overlay, swatch(uv));
        }
    }

    /** Flat side ring; used for handles and the lower port frames. */
    private static void faceRing(PoseStack pose, VertexConsumer out, Frame f,
                                 float cx, float cy, float cz, Direction normal,
                                 float radius, float thickness, int rgb, int alpha, int uv,
                                 int light, int overlay) {
        Direction u = normal.getClockWise();
        int color = (alpha << 24) | (rgb & 0xFFFFFF);
        for (int i = 0; i < 32; i++) {
            double a0 = Math.PI * 2 * i / 32, a1 = Math.PI * 2 * (i + 1) / 32;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float[][] corners = {
                    {cx + u.getStepX() * c0 * radius, cy + s0 * radius, cz + u.getStepZ() * c0 * radius},
                    {cx + u.getStepX() * c1 * radius, cy + s1 * radius, cz + u.getStepZ() * c1 * radius},
                    {cx + u.getStepX() * c1 * (radius - thickness), cy + s1 * (radius - thickness), cz + u.getStepZ() * c1 * (radius - thickness)},
                    {cx + u.getStepX() * c0 * (radius - thickness), cy + s0 * (radius - thickness), cz + u.getStepZ() * c0 * (radius - thickness)}
            };
            quadL(pose, out, f, corners, normal.getStepX(), normal.getStepY(), normal.getStepZ(),
                    color, light, overlay, swatch(uv));
        }
    }

    /** Horizontal ring drawn at the current pose origin. */
    private static void flatRing(PoseStack pose, VertexConsumer out, float outer, float inner,
                                 float thickness, int uv, int alpha, int light, int overlay,
                                 boolean broken) {
        int segments = 36;
        int color = (alpha << 24) | 0xFFFFFF;
        for (int i = 0; i < segments; i++) {
            if (broken && i % 9 >= 7) continue;
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float[][] top = {{c0 * outer, thickness, s0 * outer}, {c1 * outer, thickness, s1 * outer},
                    {c1 * inner, thickness, s1 * inner}, {c0 * inner, thickness, s0 * inner}};
            quad(pose, out, top, color, light, overlay, 0f, 1f, 0f, swatch(uv));
            float[][] bottom = {{c0 * inner, 0f, s0 * inner}, {c1 * inner, 0f, s1 * inner},
                    {c1 * outer, 0f, s1 * outer}, {c0 * outer, 0f, s0 * outer}};
            quad(pose, out, bottom, color, light, overlay, 0f, -1f, 0f, swatch(uv));
            float[][] edge = {{c0 * outer, 0f, s0 * outer}, {c1 * outer, 0f, s1 * outer},
                    {c1 * outer, thickness, s1 * outer}, {c0 * outer, thickness, s0 * outer}};
            quad(pose, out, edge, color, light, overlay, c0, 0f, s0, swatch(uv));
            float[][] innerEdge = {{c1 * inner, 0f, s1 * inner}, {c0 * inner, 0f, s0 * inner},
                    {c0 * inner, thickness, s0 * inner}, {c1 * inner, thickness, s1 * inner}};
            quad(pose, out, innerEdge, color, light, overlay, -c0, 0f, -s0, swatch(uv));
        }
    }

    private static void sphere(PoseStack pose, VertexConsumer out, Frame f, float x, float y, float z,
                               float radius, int alpha, float[] uv, int light, int overlay) {
        pose.pushPose();
        pose.translate(f.wx(x, z), f.wy(y), f.wz(x, z));
        sphere(pose, out, 0f, 0f, 0f, radius, alpha, uv, light, overlay);
        pose.popPose();
    }

    private static void sphere(PoseStack pose, VertexConsumer out, float x, float y, float z,
                               float radius, int alpha, float[] uv, int light, int overlay) {
        int slices = 10, stacks = 6;
        int color = tint(alpha, 1f);
        for (int s = 0; s < stacks; s++) {
            double p0 = Math.PI * s / stacks - Math.PI / 2;
            double p1 = Math.PI * (s + 1) / stacks - Math.PI / 2;
            for (int i = 0; i < slices; i++) {
                double a0 = Math.PI * 2 * i / slices, a1 = Math.PI * 2 * (i + 1) / slices;
                float[][] corners = {
                        {x + (float) (Math.cos(p0) * Math.cos(a0)) * radius,
                                y + (float) Math.sin(p0) * radius,
                                z + (float) (Math.cos(p0) * Math.sin(a0)) * radius},
                        {x + (float) (Math.cos(p0) * Math.cos(a1)) * radius,
                                y + (float) Math.sin(p0) * radius,
                                z + (float) (Math.cos(p0) * Math.sin(a1)) * radius},
                        {x + (float) (Math.cos(p1) * Math.cos(a1)) * radius,
                                y + (float) Math.sin(p1) * radius,
                                z + (float) (Math.cos(p1) * Math.sin(a1)) * radius},
                        {x + (float) (Math.cos(p1) * Math.cos(a0)) * radius,
                                y + (float) Math.sin(p1) * radius,
                                z + (float) (Math.cos(p1) * Math.sin(a0)) * radius}
                };
                float nx = (corners[0][0] + corners[1][0] + corners[2][0] + corners[3][0]) / 4f - x;
                float ny = (corners[0][1] + corners[1][1] + corners[2][1] + corners[3][1]) / 4f - y;
                float nz = (corners[0][2] + corners[1][2] + corners[2][2] + corners[3][2]) / 4f - z;
                float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > .0001f) { nx /= len; ny /= len; nz /= len; }
                quad(pose, out, corners, color, light, overlay, nx, ny, nz, uv);
            }
        }
    }

    private static void boxL(PoseStack pose, VertexConsumer out, Frame f,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             int uv, int light, int overlay) {
        // Front/back.
        quadL(pose, out, f, new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}},
                0f, 0f, -1f, tint(255, .98f), light, overlay, swatch(uv));
        quadL(pose, out, f, new float[][]{{x1, y0, z1}, {x0, y0, z1}, {x0, y1, z1}, {x1, y1, z1}},
                0f, 0f, 1f, tint(255, .86f), light, overlay, swatch(uv));
        // Left/right.
        quadL(pose, out, f, new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x0, y1, z0}, {x0, y1, z1}},
                -1f, 0f, 0f, tint(255, .82f), light, overlay, swatch(uv));
        quadL(pose, out, f, new float[][]{{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}},
                1f, 0f, 0f, tint(255, .90f), light, overlay, swatch(uv));
        // Bottom/top.
        quadL(pose, out, f, new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}},
                0f, -1f, 0f, tint(255, .60f), light, overlay, swatch(uv));
        quadL(pose, out, f, new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}},
                0f, 1f, 0f, tint(255, 1f), light, overlay, swatch(uv));
    }

    private static void quadL(PoseStack pose, VertexConsumer out, Frame f, float[][] corners,
                              float nx, float ny, float nz, int color, int light, int overlay,
                              float[] uv) {
        // The controller's own packed light is shadowed by its neighbours; the
        // forged shell therefore uses its baked panel shading at full light.
        light = GLOW;
        float[] world = new float[corners.length * 3];
        for (int i = 0; i < corners.length; i++) {
            world[i * 3] = f.wx(corners[i][0], corners[i][2]);
            world[i * 3 + 1] = f.wy(corners[i][1]);
            world[i * 3 + 2] = f.wz(corners[i][0], corners[i][2]);
        }
        for (int i = 0; i < corners.length; i++)
            out.addVertex(pose.last(), world[i * 3], world[i * 3 + 1], world[i * 3 + 2])
                    .setColor(color).setUv(i == 1 || i == 2 ? uv[2] : uv[0], i >= 2 ? uv[3] : uv[1])
                    .setOverlay(overlay).setLight(light)
                    .setNormal(pose.last(), f.nx(nx, nz), ny, f.nz(nx, nz));
    }

    private static void quad(PoseStack pose, VertexConsumer out, float[][] corners, int color,
                             int light, int overlay, float nx, float ny, float nz, float[] uv) {
        for (int i = 0; i < 4; i++)
            out.addVertex(pose.last(), corners[i][0], corners[i][1], corners[i][2])
                    .setColor(color).setUv(i == 1 || i == 2 ? uv[2] : uv[0], i >= 2 ? uv[3] : uv[1])
                    .setOverlay(overlay).setLight(light)
                    .setNormal(pose.last(), nx, ny, nz);
    }

    private static void box(PoseStack pose, VertexConsumer out, float x0, float y0, float z0,
                            float x1, float y1, float z1, int color, int light, int overlay,
                            float[] uv) {
        quad(pose, out, new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}},
                color, light, overlay, 0f, 0f, -1f, uv);
        quad(pose, out, new float[][]{{x1, y0, z1}, {x0, y0, z1}, {x0, y1, z1}, {x1, y1, z1}},
                color, light, overlay, 0f, 0f, 1f, uv);
        quad(pose, out, new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x0, y1, z0}, {x0, y1, z1}},
                color, light, overlay, -1f, 0f, 0f, uv);
        quad(pose, out, new float[][]{{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}},
                color, light, overlay, 1f, 0f, 0f, uv);
        quad(pose, out, new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}},
                color, light, overlay, 0f, -1f, 0f, uv);
        quad(pose, out, new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}},
                color, light, overlay, 0f, 1f, 0f, uv);
    }

    private static float[][] octagon(float r, float c) {
        return new float[][]{
                {r, -c}, {r, c}, {c, r}, {-c, r},
                {-r, c}, {-r, -c}, {-c, -r}, {c, -r}
        };
    }

    private static float wallShade(float nx, float nz) {
        return .80f + .15f * Math.max(0f, -nz) + .05f * Math.max(0f, nz)
                + .04f * Math.abs(nx);
    }

    private static int tint(int alpha, float shade) {
        int v = Mth.clamp((int) (255f * shade), 0, 255);
        return (Mth.clamp(alpha, 0, 255) << 24) | (v << 16) | (v << 8) | v;
    }

    private static float[] swatch(int index) {
        int column = index & 3, row = (index >> 2) & 3;
        return new float[]{column * .25f, row * .25f, (column + 1) * .25f, (row + 1) * .25f};
    }

    private static int lightFor(boolean running) { return GLOW; }

    private void emitParticles(AlchemyArrayBlockEntity block, Frame f, float time) {
        var level = block.getLevel();
        if (level == null) return;
        var rules = AlchemyArrayRules.get();
        long tick = level.getGameTime();
        boolean running = block.running();
        int interval = running ? rules.smokeIntervalTicks() : Math.max(12, rules.smokeIntervalTicks() * 2);
        if (tick % interval != 0 || particleTicks.getOrDefault(block, -1L) == tick) return;
        particleTicks.put(block, tick);
        float x = f.wx(0f, 0f), z = f.wz(0f, 0f);
        double top = f.wy(2.60f);
        // Chimney smoke and hot sparks.  The server's LIT state decides whether
        // the extra running plume appears; the idle plume stays subtle.
        level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                x + (level.random.nextDouble() - .5) * .20, top,
                z + (level.random.nextDouble() - .5) * .20, 0, .03, 0);
        if (running) {
            level.addParticle(ParticleTypes.FLAME,
                    f.wx(-.25f, -1.53f), f.wy(-.90f), f.wz(-.25f, -1.53f),
                    (level.random.nextDouble() - .5) * .01, .018, (level.random.nextDouble() - .5) * .01);
            level.addParticle(ParticleTypes.END_ROD,
                    x + Math.cos(time * .11) * .52, f.wy(2.62f),
                    z + Math.sin(time * .11) * .52, 0, .014, 0);
        }
        double angle = time * .13;
        level.addParticle(ParticleTypes.ENCHANT,
                x + Math.cos(angle) * .78, f.wy(2.20f),
                z + Math.sin(angle) * .78, 0, .02, 0);
        if (tick % 16 == 0)
            level.addParticle(ParticleTypes.FLAME,
                    f.wx((level.random.nextFloat() - .5f) * .3f, -1.55f),
                    f.wy(-1.05f), f.wz((level.random.nextFloat() - .5f) * .3f, -1.55f),
                    0, .015, 0);
    }

    /** Culling follows the whole furnace, including the levitating lid and core. */
    @Override public AABB getRenderBoundingBox(AlchemyArrayBlockEntity block) {
        var origin = block.getBlockPos();
        Direction back = block.getBlockState().getValue(AlchemyArrayBlock.FACING).getOpposite();
        return new AABB(origin.getX() + back.getStepX() - 2.5, origin.getY() - 1.9,
                origin.getZ() + back.getStepZ() - 2.5,
                origin.getX() + back.getStepX() + 3.5, origin.getY() + 4.6,
                origin.getZ() + back.getStepZ() + 3.5);
    }
    @Override public boolean shouldRenderOffScreen(AlchemyArrayBlockEntity block) { return false; }
    @Override public int getViewDistance() { return 72; }
}
