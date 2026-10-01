package com.example.myvillage.client.combat;

import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;

/**
 * Secondary motion for the first-person sword arm: where a loosely sprung arm would trail the
 * grip. The grip's recent path (the rig sampled at earlier visual ticks) is run through an
 * under-damped second-order low-pass, and the offset of that filtered point from the grip now is
 * the lag. While the blade accelerates into a cut the arm trails it (the tip leads and the wrist
 * cocks back); when the blade stops, the filtered point overshoots, so the elbow and wrist follow
 * through past the grip and settle. It reads visual ticks, so it freezes with the hit-stop, and
 * it keeps no frame history: the same move and tick always give the same lag.
 *
 * <p>Presentation only: the sword, its trail and every gameplay decision ignore it.
 */
final class FirstPersonArmLag {
    /** Natural frequency (radians per tick) and damping ratio of the arm spring: about 3 ticks per swing. */
    static final float OMEGA = 2.1F;
    static final float DAMPING = 0.45F;
    static final float GAIN = 0.45F;
    /** Longest lag offset, in blocks. */
    static final float CAP = 0.07F;
    static final float SAMPLE_TICKS = 0.25F;
    static final int SAMPLES = 18;
    /** The lag eases out over a move's last ticks, so the hand-off to the neutral hold never pops. */
    static final float END_FADE_TICKS = 2.0F;

    private static final float[] WEIGHTS = new float[SAMPLES];
    private static final float WEIGHT_SUM;

    static {
        float damped = OMEGA * (float) Math.sqrt(1.0F - DAMPING * DAMPING);
        float sum = 0.0F;
        for (int index = 0; index < SAMPLES; index++) {
            float age = (index + 0.5F) * SAMPLE_TICKS;
            WEIGHTS[index] = (float) (Math.exp(-DAMPING * OMEGA * age) * Math.sin(damped * age));
            sum += WEIGHTS[index];
        }
        WEIGHT_SUM = sum;
    }

    private FirstPersonArmLag() {
    }

    /** Right-arm lag offset (blocks, hand-render space) for {@code move} at visual tick {@code tick}. */
    static Vector3f offset(FirstPersonSwing swing, FirstPersonSwing.Move move, float tick) {
        float gain = GAIN * swing.rig().arm().followThrough();
        if (gain <= 0.0F) {
            return new Vector3f();
        }
        Vector3f filtered = new Vector3f();
        for (int index = 0; index < SAMPLES; index++) {
            float age = (index + 0.5F) * SAMPLE_TICKS;
            filtered.add(grip(swing, move.sample(tick - age)).mul(WEIGHTS[index]));
        }
        filtered.div(WEIGHT_SUM);
        float remaining = Math.max(0.0F, Math.min(1.0F, (move.totalTicks() - tick) / END_FADE_TICKS));
        float fade = remaining * remaining * (3.0F - 2.0F * remaining);
        Vector3f lag = filtered.sub(grip(swing, move.sample(tick))).mul(gain * fade);
        float length = lag.length();
        if (length > CAP) {
            lag.mul(CAP / length);
        }
        return lag;
    }

    private static Vector3f grip(FirstPersonSwing swing, FirstPersonSwing.Pose pose) {
        return FirstPersonSwordTransform.gripFrame(HumanoidArm.RIGHT, 0.0F, swing.rig(), pose)
                .getTranslation(new Vector3f());
    }
}
