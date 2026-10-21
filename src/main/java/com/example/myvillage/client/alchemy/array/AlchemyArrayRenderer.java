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
    private static final int SHELL = 0;
    private static final int SHELL_DARK = 2;
    private static final int TRIM = 3;
    private static final int JADE = 5;
    private static final int FIRE = 6;
    private static final int SOOT = 7;
    private static final int STONE = 8;
    private static final int COPPER = 10;
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

        // Glow is a separate material pass.  Never request another render type
        // while the previous shared-format buffer is still being used.
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucent(SHELL_TEXTURE));
        drawFireboxGlow(pose, glow, frame, time, running, overlay);
        drawFloatingCore(pose, glow, frame, time, running, eased, overlay);
        drawPortHalos(pose, glow, frame, time, running, overlay);
        pose.popPose();

        emitParticles(block, frame, time);
    }

    private static void drawBase(PoseStack pose, VertexConsumer out, Frame f, int light, int overlay) {
        // The stone altar masks the bottom component corners. Feet are outside
        // that altar; no render-only change alters the original cubic collision.
        boxL(pose, out, f, -1.56f, -1.50f, -1.56f, 1.56f, -1.00f, 1.56f, STONE, light, overlay);
        boxL(pose, out, f, -1.88f, -1.50f, -1.88f, 1.88f, -1.39f, 1.88f, STONE, light, overlay);
        for (float[] foot : new float[][]{{-1.22f, -1.55f}, {1.22f, -1.55f}, {0f, 1.67f}}) {
            float x = foot[0], z = foot[1];
            boxL(pose, out, f, x-.18f, -1.39f, z-.19f, x+.18f, -.83f, z+.19f, SHELL_DARK, light, overlay);
            boxL(pose, out, f, x-.25f, -1.39f, z-.30f, x+.25f, -1.24f, z+.26f, TRIM, light, overlay);
            boxL(pose, out, f, x-.25f, -1.02f, z-.24f, x+.25f, -.77f, z+.24f, TRIM, light, overlay);
            boxL(pose, out, f, x-.13f, -.99f, z-.265f, x+.13f, -.82f, z-.245f, JADE, light, overlay);
            for (int toe=-1; toe<=1; toe++)
                boxL(pose, out, f, x+toe*.15f-.035f, -1.40f, z-.32f,
                        x+toe*.15f+.035f, -1.31f, z-.23f, SHELL_DARK, light, overlay);
        }
    }

    private static void drawBody(PoseStack pose, VertexConsumer out, Frame f, float time,
                                 boolean running, int light, int overlay) {
        // All section polygons contain the 3x3 component square. The changing
        // radius gives a generous belly and short neck without exposing corners.
        frustum(pose,out,f,-1.02f,-.68f,1.57f,1.48f,1.88f,1.28f,SHELL_DARK,light,overlay);
        frustum(pose,out,f,-.68f,-.25f,1.88f,1.28f,1.98f,1.22f,SHELL,light,overlay);
        frustum(pose,out,f,-.25f,.42f,1.98f,1.22f,1.98f,1.22f,SHELL,light,overlay);
        frustum(pose,out,f,.42f,.98f,1.98f,1.22f,1.65f,1.40f,SHELL,light,overlay);
        frustum(pose,out,f,.98f,1.52f,1.65f,1.40f,1.65f,1.40f,SHELL_DARK,light,overlay);
        band(pose,out,f,-.70f,-.64f,1.90f,1.29f,TRIM,light,overlay,false);
        band(pose,out,f,1.04f,1.10f,1.69f,1.43f,TRIM,light,overlay,false);
        band(pose,out,f,1.45f,1.51f,1.69f,1.43f,TRIM,light,overlay,false);
        // Bold meanders on the short neck, with only three motifs per face.
        for (int side=0;side<4;side++) {
            for(int n=-1;n<=1;n++) {
                float x=n*.67f;
                faceStroke(pose,out,f,side,x-.22f,1.35f,x+.20f,1.35f,1.657f,.035f,TRIM,light,overlay);
                faceStroke(pose,out,f,side,x+.20f,1.35f,x+.20f,1.18f,1.657f,.035f,TRIM,light,overlay);
                faceStroke(pose,out,f,side,x+.20f,1.18f,x-.10f,1.18f,1.657f,.035f,TRIM,light,overlay);
                faceStroke(pose,out,f,side,x-.10f,1.18f,x-.10f,1.28f,1.657f,.035f,TRIM,light,overlay);
            }
        }
        // Sparse geometric cloud seals, away from the centre fire aperture.
        for(int side=0;side<4;side++) for(int sign:new int[]{-1,1}) {
            float x=sign*.98f;
            faceStroke(pose,out,f,side,x-.13f,-.12f,x+.13f,-.12f,1.987f,.043f,TRIM,light,overlay);
            faceStroke(pose,out,f,side,x+.13f,-.12f,x+.13f,.14f,1.987f,.043f,TRIM,light,overlay);
            faceStroke(pose,out,f,side,x+.13f,.14f,x-.05f,.14f,1.987f,.043f,TRIM,light,overlay);
            faceStroke(pose,out,f,side,x-.05f,.14f,x-.05f,.04f,1.987f,.043f,TRIM,light,overlay);
        }
    }

    private static void drawFront(PoseStack pose, VertexConsumer out, Frame f, float time,
                                  boolean running, int light, int overlay) {
        disc(pose,out,f,0f,.08f,-1.997f,Direction.NORTH,.68f,SOOT,light,overlay,8,255);
        polygonRing(pose,out,f,0f,.08f,-2.005f,.68f,.61f,TRIM,light,overlay);
        polygonRing(pose,out,f,0f,.08f,-2.010f,.46f,.41f,TRIM,light,overlay);
        // Eight different three-line seals. Bars sit in the dark octagonal
        // bronze surround; this is a small ritual aperture, not a fireplace.
        int[] trigrams={7,3,5,1,6,2,4,0};
        for(int i=0;i<8;i++) {
            double a=Math.PI*2*i/8;
            float tx=(float)Math.cos(a),ty=(float)Math.sin(a),rx=-ty,ry=tx;
            for(int row=0;row<3;row++) {
                float radius=.49f+row*.045f;
                boolean solid=(trigrams[i]&(1<<row))!=0;
                for(int half=0;half<(solid?1:2);half++) {
                    float lo=solid?-.12f:half==0?-.12f:.035f;
                    float hi=solid?.12f:half==0?-.035f:.12f;
                    float x=rx*radius,y=.08f+ry*radius;
                    frontBar(pose,out,f,x+tx*lo,y+ty*lo,x+tx*hi,y+ty*hi,-2.016f,.030f,TRIM,light,overlay);
                }
            }
        }
        // Cinnabar seal at the neck, with a restrained gold talisman stroke.
        boxL(pose,out,f,-.12f,1.09f,-1.71f,.12f,1.43f,-1.67f,COPPER,light,overlay);
        frontBar(pose,out,f,0f,1.13f,0f,1.39f,-1.718f,.025f,TRIM,light,overlay);
        frontBar(pose,out,f,-.07f,1.31f,.07f,1.31f,-1.718f,.022f,TRIM,light,overlay);
        frontBar(pose,out,f,-.06f,1.21f,.05f,1.25f,-1.718f,.022f,TRIM,light,overlay);
    }

    private static void drawPorts(PoseStack pose, VertexConsumer out, Frame f, float time,
                                  boolean running, int light, int overlay) {
        portPlate(pose,out,f,-2.015f,0f,0f,SHELL_DARK,light,overlay);
        boxL(pose,out,f,-2.075f,-.29f,-.24f,-2.04f,.29f,.24f,JADE,light,overlay);
        for(int sign:new int[]{-1,1}) {
            boxL(pose,out,f,-2.11f,sign*.32f-.035f,-.30f,-2.00f,sign*.32f+.035f,.30f,TRIM,light,overlay);
            boxL(pose,out,f,-2.11f,-.32f,sign*.27f-.035f,-2.00f,.32f,sign*.27f+.035f,TRIM,light,overlay);
        }
        boxL(pose,out,f,-2.14f,-.04f,-.32f,-2.07f,.04f,.32f,TRIM,light,overlay);
        // Low outlet retains the original bottom-layer hopper connection.
        portPlate(pose,out,f,1.79f,-.96f,0f,SHELL_DARK,light,overlay);
        boxL(pose,out,f,1.80f,-1.02f,-.13f,2.10f,-.82f,.13f,TRIM,light,overlay);
        boxL(pose,out,f,2.04f,-1.14f,-.13f,2.18f,-.82f,.13f,SHELL,light,overlay);
        boxL(pose,out,f,1.90f,-1.30f,-.32f,2.34f,-1.22f,.32f,TRIM,light,overlay);
        boxL(pose,out,f,1.95f,-1.225f,-.25f,2.29f,-1.20f,.25f,SOOT,light,overlay);
        boxL(pose,out,f,2.29f,-1.22f,-.32f,2.35f,-1.14f,.32f,TRIM,light,overlay);
        // Back spirit-stone seal, deliberately not a machinery access door.
        diamond(pose,out,f,0f,.08f,1.998f,.43f,TRIM,light,overlay);
        diamond(pose,out,f,0f,.08f,2.006f,.34f,SOOT,light,overlay);
        diamond(pose,out,f,0f,.08f,2.014f,.25f,JADE,running?GLOW:light,overlay);
    }

    private static void drawShoulderAndLid(PoseStack pose, VertexConsumer out, Frame f, float time,
                                           boolean running, int light, int overlay) {
        band(pose,out,f,1.51f,1.63f,1.82f,1.49f,TRIM,light,overlay,false);
        ringCap(pose,out,f,1.63f,1.82f,1.49f,1.62f,1.36f,TRIM,light,overlay,false);
        float[][] tiers={{1.63f,1.79f,1.62f,1.30f},{1.79f,1.95f,1.30f,.94f},
                {1.95f,2.10f,.94f,.57f},{2.10f,2.18f,.57f,.25f}};
        for(float[] t:tiers) {
            frustum(pose,out,f,t[0],t[1],t[2],t[2]*.64f,t[3],t[3]*.64f,SHELL_DARK,light,overlay);
            band(pose,out,f,t[0],t[0]+.035f,t[2]+.015f,t[2]*.64f+.015f,TRIM,light,overlay,false);
        }
        // A stepped jade finial replaces the stove chimney; it covers the vent.
        boxL(pose,out,f,-.29f,2.16f,-.29f,.29f,2.25f,.29f,TRIM,light,overlay);
        boxL(pose,out,f,-.21f,2.25f,-.21f,.21f,2.40f,.21f,JADE,light,overlay);
        boxL(pose,out,f,-.12f,2.40f,-.12f,.12f,2.54f,.12f,JADE,light,overlay);
    }

    private static void drawHandles(PoseStack pose, VertexConsumer out, Frame f, int light, int overlay) {
        // Two thick angular cloud-scroll ears, readable even without effects.
        float[][] path={{1.59f,1.22f},{2.12f,1.22f},{2.12f,2.80f},{1.72f,2.80f},
                {1.72f,2.44f},{1.96f,2.44f},{1.96f,2.61f}};
        for(int sign:new int[]{-1,1}) for(int i=0;i<path.length-1;i++) {
            float x0=sign*path[i][0],y0=path[i][1],x1=sign*path[i+1][0],y1=path[i+1][1];
            boxL(pose,out,f,Math.min(x0,x1)-.11f,Math.min(y0,y1)-.11f,-1.02f,
                    Math.max(x0,x1)+.11f,Math.max(y0,y1)+.11f,-.66f,SHELL_DARK,light,overlay);
            frontBar(pose,out,f,x0,y0,x1,y1,-1.031f,.045f,TRIM,light,overlay);
            frontBar(pose,out,f,x0,y0,x1,y1,-.649f,.045f,TRIM,light,overlay);
        }
    }

    private static void drawFireboxGlow(PoseStack pose, VertexConsumer out, Frame f, float time,
                                        boolean running, int overlay) {
        float pulse=.86f+.14f*(float)Math.sin(time*.15);
        disc(pose,out,f,0f,.08f,-2.020f,Direction.NORTH,.395f,FIRE,GLOW,overlay,8,(int)((running?220:105)*pulse));
        for(int i=-1;i<=1;i++) {
            float x=i*.14f,h=(i==0?.46f:.30f)+.035f*(float)Math.sin(time*.18+i);
            quadL(pose,out,f,new float[][]{{x-.075f,-.16f,-2.025f},{x+.075f,-.16f,-2.025f},
                    {x+.04f,-.16f+h,-2.025f},{x-.02f,-.11f+h,-2.025f}},
                    0f,0f,-1f,tint(running?235:125,1f),GLOW,overlay,swatch(EMBER));
        }
    }

    private static void drawFloatingCore(PoseStack pose, VertexConsumer out, Frame f, float time,
                                         boolean running, float progress, int overlay) {
        var rules=AlchemyArrayRules.get();
        float bob=(float)Math.sin(time*.06)*(float)rules.coreBob();
        float y=2.98f-.45f*(1f-progress)+bob;
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

    private static void drawPortHalos(PoseStack pose, VertexConsumer out, Frame f, float time,
                                      boolean running, int overlay) {
        // Inlaid angular talisman routes. Most of the bronze stays unlit.
        int light=running?GLOW:0xA000A0;
        for(int side:new int[]{0,2}) {
            faceStroke(pose,out,f,side,.60f,-.22f,.60f,.19f,1.993f,.026f,JADE,light,overlay);
            faceStroke(pose,out,f,side,.60f,.19f,.74f,.19f,1.993f,.026f,JADE,light,overlay);
            faceStroke(pose,out,f,side,.74f,.19f,.74f,.37f,1.993f,.026f,JADE,light,overlay);
        }
    }

    private static void polygonRing(PoseStack pose,VertexConsumer out,Frame f,float x,float y,float z,
                                    float outer,float inner,int uv,int light,int overlay) {
        for(int i=0;i<8;i++) {
            double a=(i+.5)*Math.PI/4,b=(i+1.5)*Math.PI/4;
            float ca=(float)Math.cos(a),sa=(float)Math.sin(a),cb=(float)Math.cos(b),sb=(float)Math.sin(b);
            quadL(pose,out,f,new float[][]{{x+ca*outer,y+sa*outer,z},{x+cb*outer,y+sb*outer,z},
                    {x+cb*inner,y+sb*inner,z},{x+ca*inner,y+sa*inner,z}},0f,0f,-1f,tint(255,1f),light,overlay,swatch(uv));
        }
    }

    private static void frontBar(PoseStack pose,VertexConsumer out,Frame f,float x0,float y0,
                                 float x1,float y1,float z,float width,int uv,int light,int overlay) {
        float dx=x1-x0,dy=y1-y0,len=Mth.sqrt(dx*dx+dy*dy);
        if(len<.0001f)return;
        float nx=-dy/len*width*.5f,ny=dx/len*width*.5f;
        quadL(pose,out,f,new float[][]{{x0-nx,y0-ny,z},{x1-nx,y1-ny,z},
                {x1+nx,y1+ny,z},{x0+nx,y0+ny,z}},0f,0f,-1f,tint(255,1f),light,overlay,swatch(uv));
    }

    private static void faceStroke(PoseStack pose,VertexConsumer out,Frame f,int side,float x0,float y0,
                                   float x1,float y1,float depth,float width,int uv,int light,int overlay) {
        Direction back=f.back(),right=f.right();
        for(int i=0;i<side;i++){back=back.getClockWise();right=right.getClockWise();}
        frontBar(pose,out,new Frame(f.cx(),f.cy(),f.cz(),right,back),x0,y0,x1,y1,-depth,width,uv,light,overlay);
    }

    private static void portPlate(PoseStack pose, VertexConsumer out, Frame f, float x, float y, float z,
                                  int uv, int light, int overlay) {
        boxL(pose, out, f, x - .04f, y - .46f, z - .46f, x + .04f, y + .46f, z + .46f,
                SHELL_DARK, light, overlay);
        boxL(pose, out, f, x - .02f, y - .38f, z - .38f, x + .02f, y + .38f, z + .38f,
                uv, light, overlay);
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
                    nx, 0f, nz, tint(255, shade), light, overlay, swatch(uv));
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
                    f.wx(0f, -2.04f), f.wy(-.10f), f.wz(0f, -2.04f),
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
