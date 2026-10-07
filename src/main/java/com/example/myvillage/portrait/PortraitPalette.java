package com.example.myvillage.portrait;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the colour roles of a portrait to ARGB for one person. A port of {@code palette()} in
 * {@code tools/portraitgen/roles.py}: the part maps hold roles, never colours, so one drawing serves
 * every skin tone, hair colour and robe, and the Java side must turn the same roles into the same
 * pixels the Python render approved. Every hex table below is copied from roles.py; change them there
 * first and re-export the palette vectors ({@code src/test/resources/portrait_palette_vectors.json}).
 */
public final class PortraitPalette {
    /**
     * The role letters in the order of the manifest's {@code roles} string: a map's red channel is the
     * index here plus one. {@link PortraitMaps} refuses a manifest with a different order.
     */
    public static final String ROLES = "SsTKLBbHhDIJRWwEe1234PXxMmnOcCQgGuNaAZzVFfY";

    // roles.py SKINS, by Skin ordinal (pale, light, tan): K T s S L
    private static final String[][] SKINS = {
            {"#9A5A62", "#E0A494", "#F4C8B8", "#FFE6DC", "#FFF3EE"},
            {"#8E5250", "#D9A08A", "#EEC0A6", "#F9DCC8", "#FFF0E4"},
            {"#7A4438", "#C58C6E", "#DCA98A", "#ECC4A6", "#F6DCC6"},
    };
    // roles.py HAIRS, by HairColour ordinal for the five base colours: D h H I J
    private static final String[][] HAIRS = {
            {"#1A1236", "#30245C", "#4A3880", "#7664B4", "#B4A4E4"}, // ink_violet
            {"#121A3A", "#223060", "#324A8A", "#5A74BC", "#A0B4E8"}, // ink_blue
            {"#2A1410", "#4A2A1E", "#6E4230", "#9A6648", "#D0A080"}, // dark_brown
            {"#301408", "#6A3418", "#9A5028", "#C47A44", "#EAB07C"}, // chestnut
            {"#4A4E6A", "#8A90AC", "#B8C0D4", "#DCE2EE", "#FAFAFF"}, // silver
    };
    private static final int SILVER = 4;
    /** roles.py: {@code grey_<colour>} is every step of the colour mixed this far toward silver. */
    private static final double GREYING = 0.38;
    // roles.py EYES, by EyeColour ordinal (metal wood water fire earth spirit): 1 2 3 4 (dark -> pale), pupil P
    private static final String[][] EYES = {
            {"#3C4454", "#7E8AA0", "#C4CCD8", "#E8ECF4", "#1C2028"},
            {"#1C5A3A", "#3E9A62", "#96DEB0", "#D4F4E0", "#0C2C1C"},
            {"#1E3A8A", "#3C78D8", "#8CCCF4", "#D4ECFC", "#0E1C48"},
            {"#7A2A14", "#C86A2C", "#F0B878", "#FBE4C0", "#3C120A"},
            {"#4A3018", "#8C6230", "#D2A868", "#F0DCB4", "#24160A"},
            {"#3A1C6E", "#7A48C2", "#B48CEC", "#E0CCF8", "#1C0C38"},
    };
    // roles.py ROBES, by Robe ordinal then [female, male]: O c C Q, trim g G, inner u N
    private static final String[][][] ROBES = {
            {
                    {"#404856", "#9DABBF", "#DEE6EE", "#F8FAFC", "#1E2531", "#617391", "#CFD4DB", "#F5F6F8"},
                    {"#4A4438", "#A89C86", "#D8D0BC", "#ECE6DA", "#1B4A60", "#52A3B8", "#CFD4DB", "#F5F6F8"},
            },
            {
                    {"#34262E", "#836D7B", "#B49FAD", "#DACAD4", "#2A1F27", "#876E7E", "#DEDBD3", "#F8F6F1"},
                    {"#102A36", "#27687F", "#35869E", "#7FC0CF", "#1E2A55", "#4A5E9E", "#CFD4DB", "#F5F6F8"},
            },
            {
                    {"#4A1420", "#8A2A3C", "#B84056", "#D0607A", "#9A7838", "#E2C478", "#D8D0C8", "#F6F2EC"},
                    {"#141E34", "#30466E", "#5373A8", "#84A6D6", "#7A6432", "#E6CC7E", "#CFD4DB", "#F5F6F8"},
            },
            {
                    {"#2C1024", "#5A2244", "#8A3868", "#A84A80", "#9A7838", "#E2C478", "#D8D0C8", "#F6F2EC"},
                    {"#080C1A", "#182243", "#2A3A6E", "#445C9E", "#7A6432", "#E8D08A", "#B4BBC5", "#E6E9ED"},
            },
    };
    // roles.py ACCENTS, by the spec's accent: a A (6 = the rogue grey)
    private static final String[][] ACCENTS = {
            {"#561317", "#C83C40"}, {"#174354", "#3D95AE"}, {"#22665A", "#6CC5B0"}, {"#7A6432", "#E2C97F"},
            {"#3B2A5A", "#8E6CC4"}, {"#5A3A1E", "#C08A50"}, {"#4A4A50", "#A8A8B0"},
    };
    // roles.py FIXED
    private static final String FIXED_ROLES = "WwEeXxZzVFfY";
    private static final String[] FIXED = {"#F8F8FF", "#D6D4EC", "#1C1030", "#B890A8", "#FFFFFF", "#F0E6FF", "#8CDCC0",
            "#3A9080", "#CC4048", "#F4F0E8", "#C8C0B4", "#B04C50"};
    private static final int BLUSH = rgb("#FF96AA");
    private static final int LIP_F = rgb("#D2607A");
    private static final int LOWER_LIP_F = rgb("#F2A8B4");
    private static final int MOUTH_INSIDE_F = rgb("#8A2C48");
    private static final int MOUTH_INSIDE_M_TOWARD = rgb("#3A1A1A");
    /** roles.py: a woman below the brocade tier wears a softer lip. */
    private static final int LIP_F_YOUNG = rgb("#C8707E");

    private PortraitPalette() {
    }

    /** ARGB (alpha 255) per role, indexed by the role's position in {@link #ROLES}. */
    public static int[] colours(PortraitSpec spec) {
        Map<Character, Integer> out = new HashMap<>();
        for (int i = 0; i < FIXED_ROLES.length(); i++) {
            out.put(FIXED_ROLES.charAt(i), rgb(FIXED[i]));
        }
        String[] skin = SKINS[spec.skin().ordinal()];
        int k = rgb(skin[0]);
        int t = rgb(skin[1]);
        int s = rgb(skin[2]);
        int base = rgb(skin[3]);
        out.put('K', k);
        out.put('T', t);
        out.put('s', s);
        out.put('S', base);
        out.put('L', rgb(skin[4]));
        out.put('B', mix(base, BLUSH, 0.55));
        out.put('b', mix(base, BLUSH, 0.28));
        int[] hair = hair(spec.hairColour());
        out.put('D', hair[0]);
        out.put('h', hair[1]);
        out.put('H', hair[2]);
        out.put('I', hair[3]);
        out.put('J', hair[4]);
        out.put('R', mix(hair[3], hair[4], 0.5));
        String[] eye = EYES[spec.eyeColour().ordinal()];
        out.put('1', rgb(eye[0]));
        out.put('2', rgb(eye[1]));
        out.put('3', rgb(eye[2]));
        out.put('4', rgb(eye[3]));
        out.put('P', rgb(eye[4]));
        if (spec.female()) {
            out.put('M', LIP_F);
            out.put('m', LOWER_LIP_F);
            out.put('n', MOUTH_INSIDE_F);
        } else {
            out.put('M', mix(k, t, 0.35));
            out.put('m', mix(base, t, 0.5));
            out.put('n', mix(k, MOUTH_INSIDE_M_TOWARD, 0.5));
        }
        String[] robe = ROBES[spec.robe().ordinal()][spec.female() ? 0 : 1];
        String robeRoles = "OcCQgGuN";
        for (int i = 0; i < robeRoles.length(); i++) {
            out.put(robeRoles.charAt(i), rgb(robe[i]));
        }
        String[] accent = ACCENTS[spec.accent()];
        out.put('a', rgb(accent[0]));
        out.put('A', rgb(accent[1]));
        // roles.py uses the person's realm tier; the robe is chosen by that tier, so its ordinal is the tier
        if (spec.female() && spec.robe().ordinal() < 2) {
            out.put('M', LIP_F_YOUNG);
            out.put('m', mix(base, LOWER_LIP_F, 0.6));
        }
        int[] colours = new int[ROLES.length()];
        for (int i = 0; i < colours.length; i++) {
            Integer c = out.get(ROLES.charAt(i));
            if (c == null) {
                throw new IllegalStateException("no colour for portrait role " + ROLES.charAt(i));
            }
            colours[i] = c;
        }
        return colours;
    }

    /**
     * The D h H I J ramp of a hair colour, dark to pale, as opaque ARGB; the greying forms mix every
     * step toward silver. Shared with the 3D avatar skins ({@link NpcSkinComposer}).
     */
    public static int[] hair(PortraitSpec.HairColour colour) {
        int ordinal = colour.ordinal();
        boolean grey = ordinal > SILVER;
        String[] ramp = HAIRS[grey ? ordinal - SILVER - 1 : ordinal];
        int[] out = new int[5];
        for (int i = 0; i < 5; i++) {
            out[i] = grey ? mix(rgb(ramp[i]), rgb(HAIRS[SILVER][i]), GREYING) : rgb(ramp[i]);
        }
        return out;
    }

    /**
     * The iris ramp of an eye colour, dark to pale (the roles 1 2 3 4) followed by the pupil (P), as
     * opaque ARGB. Shared with the 3D avatar skins ({@link NpcSkinComposer}).
     */
    public static int[] eyes(PortraitSpec.EyeColour colour) {
        String[] eye = EYES[colour.ordinal()];
        int[] out = new int[eye.length];
        for (int i = 0; i < eye.length; i++) {
            out[i] = rgb(eye[i]);
        }
        return out;
    }

    /** {@code #RRGGBB} as opaque ARGB. */
    static int rgb(String hex) {
        return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
    }

    /**
     * roles.py {@code mix}: per channel {@code round(a + (b - a) * t)}. Python rounds half to even, so
     * this uses {@link Math#rint}, not {@link Math#round}.
     */
    public static int mix(int a, int b, double t) {
        int out = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int ca = (a >> shift) & 0xFF;
            int cb = (b >> shift) & 0xFF;
            out |= ((int) Math.rint(ca + (cb - ca) * t)) << shift;
        }
        return out;
    }
}
