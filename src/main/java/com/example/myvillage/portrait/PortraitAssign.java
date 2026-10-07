package com.example.myvillage.portrait;

import com.example.myvillage.portrait.PortraitSpec.Back;
import com.example.myvillage.portrait.PortraitSpec.Brow;
import com.example.myvillage.portrait.PortraitSpec.EyeColour;
import com.example.myvillage.portrait.PortraitSpec.EyeShape;
import com.example.myvillage.portrait.PortraitSpec.Face;
import com.example.myvillage.portrait.PortraitSpec.Front;
import com.example.myvillage.portrait.PortraitSpec.HairColour;
import com.example.myvillage.portrait.PortraitSpec.Headwear;
import com.example.myvillage.portrait.PortraitSpec.Mouth;
import com.example.myvillage.portrait.PortraitSpec.Robe;
import com.example.myvillage.portrait.PortraitSpec.Skin;
import com.example.myvillage.sim.PersonView;
import java.util.List;
import java.util.Map;

/**
 * Derives a person's {@link PortraitSpec} from the ledger. A line-by-line port of {@code assign()} in
 * {@code tools/portraitgen/person.py}: the Python generator drew and the owner approved the faces, so
 * Java must pick the same parts for the same record (parity vectors in
 * {@code src/test/resources/portrait_assign_vectors.json}). Every constant, option list and salt below
 * is copied from person.py; change them there first and re-export the vectors. Pure: no Minecraft
 * types, deterministic in the inputs.
 */
public final class PortraitAssign {
    /** person.py {@code REALMS}: the order gives the robe tier. */
    static final List<String> REALMS =
            List.of("mortal", "qi_refining", "foundation_establishment", "golden_core", "nascent_soul");
    /** person.py {@code LIFESPAN} in years; an unknown realm counts as {@link #DEFAULT_LIFESPAN}. */
    static final Map<String, Integer> LIFESPAN = Map.of("mortal", 80, "qi_refining", 120,
            "foundation_establishment", 240, "golden_core", 500, "nascent_soul", 1000);
    static final int DEFAULT_LIFESPAN = 120;

    /** person.py trait order (ambition aggression caution wanderlust loyalty); also the mood names. */
    static final int AMBITION = 0;
    static final int AGGRESSION = 1;
    static final int CAUTION = 2;
    static final int WANDERLUST = 3;
    static final int LOYALTY = 4;
    /** No trait at or above {@link #MOOD_THRESHOLD}: calm. */
    static final int CALM = -1;
    static final int MOOD_THRESHOLD = 70;

    /** Age fractions of the lifespan: silver at {@code OLD}, greying from {@code GREYING} below it. */
    static final double OLD = 0.8;
    static final double GREYING = 0.55;

    // person.py option lists, in order (the order is part of the hash's meaning).
    private static final Face[] FACE_SHAPES = {Face.OVAL, Face.ROUND, Face.SHARP};
    private static final Skin[] SKIN_TONES = {Skin.PALE, Skin.LIGHT, Skin.TAN};
    private static final HairColour[] HAIR_COLOURS =
            {HairColour.INK_BLUE, HairColour.INK_VIOLET, HairColour.DARK_BROWN, HairColour.CHESTNUT};
    private static final EyeShape[] EYE_SHAPES_F =
            {EyeShape.ROUND, EyeShape.ROUND, EyeShape.ALMOND, EyeShape.DROOPY, EyeShape.SHARP};
    private static final EyeShape[] EYE_SHAPES_M =
            {EyeShape.ALMOND, EyeShape.ALMOND, EyeShape.SHARP, EyeShape.ROUND, EyeShape.DROOPY};
    private static final Front[] FRONTS_M = {Front.SPLIT, Front.SWEEP, Front.SPIKY, Front.SPLIT, Front.CURTAIN};
    private static final Front[] FRONTS_F = {Front.HIME, Front.SPLIT, Front.SWEEP, Front.CURTAIN, Front.HIME};
    private static final Back[] BACKS_M = {Back.SHORT, Back.SHORT, Back.LONG, Back.PONYTAIL};
    private static final Back[] BACKS_F = {Back.LONG, Back.LONG, Back.TWIN_BUNS, Back.PONYTAIL, Back.HALF_UP};
    private static final Brow[] CALM_BROWS = {Brow.STRAIGHT, Brow.ARCHED, Brow.STRAIGHT};
    private static final Mouth[] CALM_MOUTHS = {Mouth.SMILE, Mouth.NEUTRAL, Mouth.SMALL};
    private static final EyeColour[] ELEMENTS =
            {EyeColour.METAL, EyeColour.WOOD, EyeColour.WATER, EyeColour.FIRE, EyeColour.EARTH};

    /** The rogue's accent (sects take {@code sectId % 6}). */
    static final int ROGUE_ACCENT = 6;
    static final int INJURY_BANDAGE = 20;
    static final int INJURY_SCAR = 50;

    /** The dead have no root or traits on record: all elements equal, all traits at 50 (calm). */
    private static final int[] NO_ROOT = {2000, 2000, 2000, 2000, 2000};
    private static final int[] NO_TRAITS = {50, 50, 50, 50, 50};

    private PortraitAssign() {
    }

    /**
     * The portrait of a ledger person on sim day {@code day}. The age is counted to the day of death
     * for the dead; a view without root or traits (the dead) counts as all-equal and all-50.
     */
    public static PortraitSpec of(PersonView p, long day, int daysPerYear) {
        if (daysPerYear <= 0) {
            throw new IllegalArgumentException("daysPerYear must be positive, got " + daysPerYear);
        }
        long until = p.alive() ? day : p.deathDay();
        double ageYears = Math.max(0L, until - p.birthDay()) / (double) daysPerYear;
        return of(p.id(), "f".equals(p.gender()), p.realmId(), p.rank(), p.sectId(), ageYears, ints(p.root(), NO_ROOT),
                ints(p.traits(), NO_TRAITS), p.injury(), p.alive());
    }

    /**
     * The pure port of person.py {@code assign()}.
     *
     * @param root   five basis points over metal, wood, water, fire, earth; null or not five long → all equal
     * @param traits ambition, aggression, caution, wanderlust, loyalty; null or not five long → all 50
     */
    public static PortraitSpec of(int id, boolean female, String realmId, String rank, int sectId, double ageYears,
                                  int[] root, int[] traits, int injury, boolean alive) {
        if (root == null || root.length != 5) {
            root = NO_ROOT;
        }
        if (traits == null || traits.length != 5) {
            traits = NO_TRAITS;
        }
        long s = id;
        int realmIndex = REALMS.indexOf(realmId);
        int tier = Math.max(0, Math.min(3, (realmIndex < 0 ? 1 : realmIndex) - 1));
        double ageFraction = ageYears / LIFESPAN.getOrDefault(realmId, DEFAULT_LIFESPAN);
        boolean old = ageFraction >= OLD;
        boolean greying = GREYING <= ageFraction && ageFraction < OLD;
        boolean elderly = "elder".equals(rank) || "sect_master".equals(rank);

        Face face = pick(s, 1, FACE_SHAPES);
        if (female && face == Face.SHARP && mix(s, 2) % 3 != 0) {
            face = Face.OVAL; // women are mostly soft-jawed
        }
        Skin skin = pick(s, 3, SKIN_TONES);
        if (female && skin == Skin.TAN && mix(s, 4) % 2 == 0) {
            skin = Skin.PALE;
        }
        HairColour hair = old ? HairColour.SILVER : pick(s, 5, HAIR_COLOURS);
        if (greying) {
            hair = hair.greying();
        }
        Front front = pick(s, 6, female ? FRONTS_F : FRONTS_M);
        Back back = pick(s, 7, female ? BACKS_F : BACKS_M);
        if (!female && elderly) {
            back = Back.BUN; // 道髻: the sect's men of standing tie their hair up
            if (front == Front.SPIKY) {
                front = Front.SPLIT;
            }
        }
        if (female && elderly && (back == Back.TWIN_BUNS || back == Back.PONYTAIL)) {
            back = Back.HALF_UP;
        }
        if (female && back == Back.TWIN_BUNS && (tier >= 2 || ageYears > 40)) {
            back = Back.LONG;
        }
        int mood = mood(traits);
        EyeShape eyeShape = pick(s, 8, female ? EYE_SHAPES_F : EYE_SHAPES_M);
        if (mood == AGGRESSION) {
            eyeShape = EyeShape.SHARP;
        } else if (mood == WANDERLUST) {
            eyeShape = EyeShape.ROUND;
        } else if (mood == CAUTION && eyeShape == EyeShape.SHARP) {
            eyeShape = EyeShape.ALMOND;
        }
        if (female && eyeShape == EyeShape.SHARP && mood != AGGRESSION) {
            eyeShape = EyeShape.ALMOND;
        }
        Brow brow = switch (mood) {
            case AGGRESSION -> Brow.ANGRY;
            case AMBITION, WANDERLUST -> Brow.ARCHED;
            case CAUTION -> Brow.WORRIED;
            case LOYALTY -> Brow.STRAIGHT;
            default -> pick(s, 9, CALM_BROWS);
        };
        Mouth mouth = switch (mood) {
            case AGGRESSION -> Mouth.FROWN;
            case AMBITION -> Mouth.SMIRK;
            case CAUTION -> Mouth.SMALL;
            case WANDERLUST -> Mouth.OPEN;
            case LOYALTY -> Mouth.NEUTRAL;
            default -> pick(s, 10, CALM_MOUTHS);
        };
        boolean nose = mix(s, 11) % 3 != 0;
        boolean blush = female || (ageYears < 30 && mix(s, 12) % 2 == 0);
        Robe robe = Robe.values()[tier];
        int accent = sectId < 0 ? ROGUE_ACCENT : sectId % 6;
        Headwear headwear = switch (rank == null ? "" : rank) {
            case "sect_master" -> Headwear.CROWN;
            case "elder" -> Headwear.HEADBAND;
            case "inner" -> Headwear.RIBBON;
            default -> Headwear.NONE; // outer, rogue
        };
        if ("rogue".equals(rank) && mix(s, 13) % 3 == 0) {
            headwear = Headwear.CLOTH;
        }
        if (female && headwear == Headwear.CROWN) {
            headwear = Headwear.PHOENIX_PIN;
        }
        if (female && headwear == Headwear.HEADBAND) {
            headwear = Headwear.JADE_PIN;
        }
        boolean bandage = injury >= INJURY_BANDAGE;
        boolean scar = injury >= INJURY_SCAR || (injury >= INJURY_BANDAGE && mood == AGGRESSION);
        EyeColour eyeColour = "nascent_soul".equals(realmId) ? EyeColour.SPIRIT : ELEMENTS[argmax(root)];
        return new PortraitSpec(female, face, skin, hair, front, back, eyeShape, eyeColour, brow, mouth, nose, blush,
                robe, accent, headwear, bandage, scar, old, !alive);
    }

    /** person.py {@code mix}: splitmix64 of {@code seed * golden + salt * C1}, 64-bit wrapping, non-negative. */
    static long mix(long seed, long salt) {
        long z = seed * 0x9E3779B97F4A7C15L + salt * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return (z ^ (z >>> 31)) & 0x7FFFFFFFFFFFFFFFL;
    }

    static <T> T pick(long seed, long salt, T[] options) {
        return options[(int) (mix(seed, salt) % options.length)];
    }

    /** person.py {@code mood}: the strongest trait (ties to the first) when at least 70, else {@link #CALM}. */
    static int mood(int[] traits) {
        int best = argmax(traits);
        return traits[best] >= MOOD_THRESHOLD ? best : CALM;
    }

    /** Index of the largest value, ties to the lowest index. */
    private static int argmax(int[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }

    private static int[] ints(List<Integer> values, int[] fallback) {
        if (values == null || values.size() != 5) {
            return fallback;
        }
        int[] out = new int[5];
        for (int i = 0; i < 5; i++) {
            out[i] = values.get(i);
        }
        return out;
    }
}
