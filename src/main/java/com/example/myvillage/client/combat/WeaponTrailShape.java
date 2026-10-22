package com.example.myvillage.client.combat;

import com.example.myvillage.combat.definition.MoveKind;

/**
 * Pure shape and fade math shared by the first-person and world 剑光 trails. A trail sample is
 * one blade position (base to tip) at some age in [0, 1], where 0 is the blade drawn this frame
 * and 1 is the oldest remembered position. Older samples keep only a thin strip near the tip,
 * so the ribbon tapers into the arc the tip drew instead of reading as a filled wedge.
 */
final class WeaponTrailShape {
    private static final com.google.gson.JsonObject PROFILE = loadProfile();
    /** Fraction of the blade (base 0, tip 1) where the visible trail starts on the newest sample. */
    static final float INNER_FRACTION_NEW = value("inner_fraction_new");
    /** ... and on the oldest sample: the tail collapses to a sliver at the tip. */
    static final float INNER_FRACTION_OLD = value("inner_fraction_old");
    /** Start of the near-white edge band, as a fraction of the blade. */
    static final float EDGE_FRACTION = value("edge_fraction");
    /** Peak opacity of the newest sample. */
    static final float PEAK_ALPHA = value("peak_alpha");
    /** The soft body band is this share of the edge opacity at its outer side, fading to 0 inside. */
    static final float BODY_ALPHA_SHARE = value("body_alpha_share");
    /** The edge band keeps this share of its opacity at the very tip. */
    static final float TIP_ALPHA_SHARE = value("tip_alpha_share");

    static {
        if(INNER_FRACTION_NEW>=EDGE_FRACTION || EDGE_FRACTION>=INNER_FRACTION_OLD)
            throw new IllegalArgumentException("Trail bands must be nested");
    }
    private static com.google.gson.JsonObject loadProfile() {
        try(var in=WeaponTrailShape.class.getResourceAsStream("/assets/myvillage/combat/weapon_trail.json")) {
            if(in==null)throw new IllegalStateException("Missing weapon trail profile");
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private static float value(String key) {
        float v=PROFILE.get(key).getAsFloat();
        if(!Float.isFinite(v) || v<0 || v>1)throw new IllegalArgumentException("Invalid trail " + key);
        return v;
    }

    private WeaponTrailShape() {
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

    /** Authored peak opacity with quadratic age falloff, times the whole-trail fade. */
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
