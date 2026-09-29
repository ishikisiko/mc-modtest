package com.example.myvillage.client.combat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * {@code myvillage:blade_cut}: a thin white slash line at the contact point, camera-facing and
 * rolled to the cut's angle. The server spawns it with count 0, so the "speed" arguments carry
 * data instead of motion: x is the roll in radians, y above 0.5 marks a heavy hit (a larger
 * slash). It lives 4 ticks, snaps open during the first tick, fades from opaque to clear, and is
 * full-bright so it reads at night.
 */
public final class BladeCutParticle extends TextureSheetParticle {
    static final int LIFETIME = 4;
    static final float LIGHT_SIZE = 0.55F;
    static final float HEAVY_SIZE = 0.8F;
    /** The slash starts at this share of its size and reaches full size after one tick. */
    static final float OPEN_FROM = 0.6F;
    private static final int FULL_BRIGHT = 0xF000F0;

    BladeCutParticle(ClientLevel level, double x, double y, double z, double rollRadians, boolean heavy, SpriteSet sprites) {
        super(level, x, y, z);
        this.lifetime = LIFETIME;
        this.quadSize = heavy ? HEAVY_SIZE : LIGHT_SIZE;
        this.roll = (float) rollRadians;
        this.oRoll = this.roll;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
        this.alpha = 1.0F;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    @Override
    public void render(VertexConsumer consumer, Camera camera, float partialTicks) {
        this.alpha = alphaAt(this.age + partialTicks);
        super.render(consumer, camera, partialTicks);
    }

    @Override
    public float getQuadSize(float partialTicks) {
        return this.quadSize * openAt(this.age + partialTicks);
    }

    @Override
    protected int getLightColor(float partialTicks) {
        return FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Opacity {@code age} ticks in: 1 at birth, linearly to 0 at the end of its life. */
    static float alphaAt(float age) {
        return Math.max(0.0F, Math.min(1.0F, 1.0F - age / LIFETIME));
    }

    /** Size share {@code age} ticks in: opens from 60% to full over the first tick. */
    static float openAt(float age) {
        if (age >= 1.0F) {
            return 1.0F;
        }
        float t = Math.max(0.0F, age);
        return OPEN_FROM + (1.0F - OPEN_FROM) * (1.0F - (1.0F - t) * (1.0F - t));
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            return new BladeCutParticle(level, x, y, z, xSpeed, ySpeed > 0.5, sprites);
        }
    }
}
