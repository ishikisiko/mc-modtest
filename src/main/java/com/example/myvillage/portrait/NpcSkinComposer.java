package com.example.myvillage.portrait;

/**
 * Recolours an NPC look's baked texture so its hair and eyes match a person's portrait. Pure: no
 * Minecraft classes, so the Python side can pin goldens for it.
 *
 * <p><b>Role map.</b> {@code tools/npcgen} writes, next to each look's texture, a role map
 * {@code assets/myvillage/npc/<name>_roles.png} of the same size. A role-map pixel with alpha 0 says
 * the texel keeps its baked colour. A pixel with alpha 255 names a material in its red channel and
 * an index in its green channel:
 * <ul>
 *   <li>{@link #MATERIAL_HAIR} (1): the index 0..12 is the painter's half-step tone on a 7-step hair
 *       ramp: the colour is {@code mix(ramp7[i / 2], ramp7[i / 2 + 1], (i % 2) * 0.5)}, and 12 is the
 *       ramp's last step. The 7-step ramp is the portrait's 5-step hair ramp resampled
 *       ({@link #hairRamp7}).</li>
 *   <li>{@link #MATERIAL_IRIS} (2): the index 0..2 is a row of the iris, dark to pale, and takes the
 *       portrait's iris ramp ({@link PortraitPalette#eyes}) at that row; 3 is the pale row mixed half
 *       way toward the eye white {@link #EYE_WHITE}.</li>
 * </ul>
 * Every mix rounds half to even ({@link PortraitPalette#mix}), as the Python painter does. A role texel
 * keeps the base texel's alpha (the exporter only marks painted texels, so this is a guard, not a rule).
 */
public final class NpcSkinComposer {
    public static final int MATERIAL_HAIR = 1;
    public static final int MATERIAL_IRIS = 2;
    /** The painter's hair tones: half steps 0..12 on a 7-step ramp. */
    public static final int HAIR_TONES = 13;
    public static final int RAMP7 = 7;
    /** The iris index of the pale row mixed toward the eye white. */
    public static final int IRIS_WHITE_MIX = 3;
    /** {@code EYE_WHITE} of every npcgen definition. */
    public static final int EYE_WHITE = 0xFFF8FAFF;

    private NpcSkinComposer() {
    }

    /**
     * The baked texture with every role texel recoloured for {@code colours}; a new array in ARGB,
     * the base untouched. The role map must have the base's size.
     */
    public static int[] compose(PortraitMaps.Image base, PortraitMaps.Image roles, NpcColours colours) {
        if (base.width() != roles.width() || base.height() != roles.height()) {
            throw new IllegalArgumentException("role map " + roles.width() + "x" + roles.height()
                    + " does not match the texture " + base.width() + "x" + base.height());
        }
        int[] hair = hairRamp7(colours.hair());
        int[] eyes = PortraitPalette.eyes(colours.eye());
        int[] out = base.argb().clone();
        int[] role = roles.argb();
        for (int i = 0; i < out.length; i++) {
            int r = role[i];
            if ((r >>> 24) == 0) {
                continue;
            }
            int material = (r >> 16) & 0xFF;
            int index = (r >> 8) & 0xFF;
            int colour = switch (material) {
                case MATERIAL_HAIR -> hairTone(hair, index);
                case MATERIAL_IRIS -> iris(eyes, index);
                default -> throw new IllegalArgumentException("unknown role material " + material + " at texel " + i);
            };
            out[i] = (out[i] & 0xFF000000) | (colour & 0x00FFFFFF); // the base alpha stays: a cut-out hole stays a hole
        }
        return out;
    }

    /** The colour of a half-step hair tone {@code index} (0..12) on a 7-step ramp. */
    public static int hairTone(int[] ramp7, int index) {
        if (index < 0 || index >= HAIR_TONES) {
            throw new IllegalArgumentException("hair tone " + index);
        }
        int i = index / 2;
        if (i >= RAMP7 - 1) {
            return ramp7[RAMP7 - 1];
        }
        return PortraitPalette.mix(ramp7[i], ramp7[i + 1], (index % 2) * 0.5);
    }

    /** The colour of an iris row {@code index} (0..2), or 3 for the pale row mixed toward the eye white. */
    public static int iris(int[] eyes, int index) {
        if (index >= 0 && index < IRIS_WHITE_MIX) {
            return eyes[index];
        }
        if (index == IRIS_WHITE_MIX) {
            return PortraitPalette.mix(eyes[2], EYE_WHITE, 0.5);
        }
        throw new IllegalArgumentException("iris index " + index);
    }

    /**
     * The portrait's 5-step hair ramp resampled to the painter's 7 steps: step {@code j} samples the
     * 5-step ramp at {@code 2j / 3}, mixing the two neighbouring steps. {@code tools/npcgen/recolour.py}
     * computes it the same way, in the same order of operations.
     */
    public static int[] hairRamp7(PortraitSpec.HairColour colour) {
        int[] ramp5 = PortraitPalette.hair(colour);
        int[] out = new int[RAMP7];
        for (int j = 0; j < RAMP7; j++) {
            double p = (2.0 * j) / 3.0;
            int i = (int) Math.floor(p);
            if (i >= ramp5.length - 1) {
                out[j] = ramp5[ramp5.length - 1];
            } else {
                out[j] = PortraitPalette.mix(ramp5[i], ramp5[i + 1], p - i);
            }
        }
        return out;
    }
}
