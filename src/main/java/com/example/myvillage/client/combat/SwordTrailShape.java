package com.example.myvillage.client.combat;

import com.example.myvillage.combat.definition.MoveKind;

/**
 * Pure shape and fade math shared by the first-person and world 剑光 trails. A trail sample is
 * one blade position (base to tip) at some age in [0, 1], where 0 is the blade drawn this frame
 * and 1 is the oldest remembered position. Older samples keep only a thin strip near the tip,
 * so the ribbon tapers into the arc the tip drew instead of reading as a filled wedge.
 */
final class SwordTrailShape {
    /** Fraction of the blade (base 0, tip 1) where the visible trail starts on the newest sample. */
    static final float INNER_FRACTION_NEW = 0.55F;
    /** ... and on the oldest sample: the tail collapses to a sliver at the tip. */
    static final float INNER_FRACTION_OLD = 0.97F;
    /** Start of the near-white edge band, as a fraction of the blade. */
    static final float EDGE_FRACTION = 0.85F;
    /** Peak opacity of the newest sample. */
    static final float PEAK_ALPHA = 0.7F;
    /** The soft body band is this share of the edge opacity at its outer side, fading to 0 inside. */
    static final float BODY_ALPHA_SHARE = 0.5F;
    /** The edge band keeps this share of its opacity at the very tip. */
    static final float TIP_ALPHA_SHARE = 0.9F;

    private SwordTrailShape() {
    }

    /**
     * The trail form a move draws, from its kind: a thrust sweeps no area and draws one streak
     * along the blade; a cut draws the swept band.
     */
    static boolean streak(MoveKind kind) {
        return kind == MoveKind.THRUST;
    }

    /** Where the visible trail starts along the blade for a sample of this age. */
    static float innerFraction(float age) {
        float t = clamp01(age);
        float smooth = t * t * (3.0F - 2.0F * t);
        return INNER_FRACTION_NEW + (INNER_FRACTION_OLD - INNER_FRACTION_NEW) * smooth;
    }

    /** Where the edge band starts; never inside the tapered inner edge. */
    static float edgeFraction(float age) {
        return Math.max(innerFraction(age), EDGE_FRACTION);
    }

    /** Opacity of a sample: {@code 0.7 * (1 - age)^2}, times the whole-trail fade. */
    static float alpha(float age, float fade) {
        float remaining = 1.0F - clamp01(age);
        return PEAK_ALPHA * remaining * remaining * clamp01(fade);
    }

    /**
     * Whole-trail fade once the strike window is over: 1 during the window, then linear to 0
     * over {@code fadeTicks}.
     */
    static float fade(float now, float windowEnd, float fadeTicks) {
        if (now <= windowEnd) {
            return 1.0F;
        }
        return clamp01(1.0F - (now - windowEnd) / fadeTicks);
    }

    /** A single thrust streak: 0.8 at the strike start, fading linearly to 0 over {@code ticks}. */
    static float streakAlpha(float now, float strikeStart, float ticks) {
        if (now < strikeStart) {
            return 0.0F;
        }
        return 0.8F * clamp01(1.0F - (now - strikeStart) / ticks);
    }

    static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(1.0F, value);
    }
}
