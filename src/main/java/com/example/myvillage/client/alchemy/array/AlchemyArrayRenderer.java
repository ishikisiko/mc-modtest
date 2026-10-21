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
    private static final int TRIM = 3;
    private static final int JADE = 5;
    private static final int FIRE = 6;
    private static final int EMBER = 14;

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
        // Sample open air above the vessel rather than the enclosed controller.
        light = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, block.getBlockPos().above(4));
        float time = now + partialTick;

        // The assembled shell unfolds independently of the masked component
        // meshes; those return when the server reports an unformed array.
        float grow = .84f + .16f * eased;
        pose.pushPose();
        pose.translate(cx, 0f, cz);
        pose.scale(grow, 1f, grow);
        pose.translate(-cx, 0f, -cz);

        VertexConsumer opaque = buffers.getBuffer(RenderType.entityCutoutNoCull(SHELL_TEXTURE));
        drawMesh(pose, opaque, frame, false, running, light, overlay);
        float lidFall = .75f * (1f - eased);
        pose.pushPose();
        pose.translate(0f, lidFall, 0f);
        drawMesh(pose, opaque, frame, true, running, light, overlay);
        pose.popPose();

        // Glow is a separate material pass.  Never request another render type
        // while the previous shared-format buffer is still being used.
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucent(SHELL_TEXTURE));
        drawFireboxGlow(pose, glow, frame, time, running, overlay);
        drawFloatingCore(pose, glow, frame, time, running, eased, overlay);
        pose.popPose();

        emitParticles(block, frame, time);
    }

    private static void drawMesh(PoseStack pose, VertexConsumer out, Frame frame, boolean lid,
                                 boolean running, int light, int overlay) {
        for (var face : AlchemyArrayMesh.INSTANCE.faces()) {
            if (face.lid()!=lid) continue;
            float[] n=face.normal();
            float shade=face.glow()?1f:Math.max(.56f, .78f+.18f*n[1]-.10f*n[2]+.06f*Math.abs(n[0]));
            int faceLight=face.glow()?(running?GLOW:0xB000B0):light;
            quadL(pose,out,frame,face.points(),n[0],n[1],n[2],tint(255,shade),faceLight,overlay,swatch(face.material()));
        }
    }

    private static void drawFireboxGlow(PoseStack pose, VertexConsumer out, Frame f, float time,
                                        boolean running, int overlay) {
        float pulse=.86f+.14f*(float)Math.sin(time*.15);
        disc(pose,out,f,0f,.20f,-1.84f,Direction.NORTH,.44f,FIRE,GLOW,overlay,8,(int)((running?220:105)*pulse));
        for(int i=-1;i<=1;i++) {
            float x=i*.14f,h=(i==0?.46f:.30f)+.035f*(float)Math.sin(time*.18+i);
            quadL(pose,out,f,new float[][]{{x-.075f,-.04f,-1.85f},{x+.075f,-.04f,-1.85f},
                    {x+.04f,-.04f+h,-1.85f},{x-.02f,.01f+h,-1.85f}},
                    0f,0f,-1f,tint(running?235:125,1f),GLOW,overlay,swatch(EMBER));
        }
    }

    private static void drawFloatingCore(PoseStack pose, VertexConsumer out, Frame f, float time,
                                         boolean running, float progress, int overlay) {
        var rules=AlchemyArrayRules.get();
        float bob=(float)Math.sin(time*.06)*(float)rules.coreBob();
        float y=2.80f-.45f*(1f-progress)+bob;
        float spin=time*(float)(running?rules.ringSpeedDegrees():rules.coreSpeedDegrees());
        pose.pushPose();
        pose.translate(f.wx(0f,0f),f.wy(y),f.wz(0f,0f));
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        sphere(pose,out,0f,0f,0f,.17f,255,swatch(EMBER),GLOW,overlay);
        // Parallel octagonal seal rings: no atom-model orbs or crossed halos.
        for(int layer=0;layer<2;layer++) {
            pose.pushPose();
            pose.translate(0f,layer==0?-.21f:.25f,0f);
            pose.mulPose(Axis.YP.rotationDegrees(layer==0?0f:-2f*spin+22.5f));
            flatRing(pose,out,1.00f,.96f,.045f,TRIM,230,GLOW,overlay,true);
            flatRing(pose,out,.94f,.92f,.065f,JADE,running?235:145,GLOW,overlay,true);
            for(int i=0;i<8;i++) {
                pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(i*45f));
                box(pose,out,-.045f,-.025f,.91f,.045f,.09f,1.035f,0xFFF0DEAB,GLOW,overlay,swatch(TRIM));
                pose.popPose();
            }
            pose.popPose();
        }
        pose.popPose();
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

    /** Horizontal ring drawn at the current pose origin. */
    private static void flatRing(PoseStack pose, VertexConsumer out, float outer, float inner,
                                 float thickness, int uv, int alpha, int light, int overlay,
                                 boolean broken) {
        int segments = 8;
        int color = (alpha << 24) | 0xFFFFFF;
        for (int i = 0; i < segments; i++) {

            double a0 = Math.PI * 2 * (i + (broken ? .035 : 0)) / segments,
                    a1 = Math.PI * 2 * (i + 1 - (broken ? .035 : 0)) / segments;
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

    private static void quadL(PoseStack pose, VertexConsumer out, Frame f, float[][] corners,
                              float nx, float ny, float nz, int color, int light, int overlay,
                              float[] uv) {
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

    private static int tint(int alpha, float shade) {
        int v = Mth.clamp((int) (255f * shade), 0, 255);
        return (Mth.clamp(alpha, 0, 255) << 24) | (v << 16) | (v << 8) | v;
    }

    private static float[] swatch(int index) {
        int column = index & 3, row = (index >> 2) & 3;
        return new float[]{column * .25f, row * .25f, (column + 1) * .25f, (row + 1) * .25f};
    }

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
        // Finial vapour and sparse hot sparks.  The server's LIT state decides whether
        // the extra running plume appears; the idle plume stays subtle.
        level.addParticle(ParticleTypes.SMOKE,
                x + (level.random.nextDouble() - .5) * .20, top,
                z + (level.random.nextDouble() - .5) * .20, 0, .03, 0);
        if (running) {
            level.addParticle(ParticleTypes.FLAME,
                    f.wx(0f, -1.94f), f.wy(.04f), f.wz(0f, -1.94f),
                    (level.random.nextDouble() - .5) * .01, .018, (level.random.nextDouble() - .5) * .01);
            level.addParticle(ParticleTypes.END_ROD,
                    x + Math.cos(time * .11) * .52, f.wy(2.62f),
                    z + Math.sin(time * .11) * .52, 0, .014, 0);
        }
        double angle = time * .13;
        level.addParticle(ParticleTypes.ENCHANT,
                x + Math.cos(angle) * .78, f.wy(2.20f),
                z + Math.sin(angle) * .78, 0, .02, 0);
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
