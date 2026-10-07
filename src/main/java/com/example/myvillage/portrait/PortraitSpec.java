package com.example.myvillage.portrait;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Which parts a person's 64x64 portrait is composed from. The server derives it from the ledger
 * ({@link PortraitAssign}); the client composes the picture from the part maps under
 * {@code assets/myvillage/portrait/} and the person's palette. The enum names, lower-cased, are
 * the variant names the Python generator ({@code tools/portraitgen}) uses, so both sides draw the
 * same face for the same record.
 *
 * @param accent 0..5 the sect's accent colour (sect id mod 6), 6 the rogue grey
 */
public record PortraitSpec(
        boolean female,
        Face face,
        Skin skin,
        HairColour hairColour,
        Front front,
        Back back,
        EyeShape eyeShape,
        EyeColour eyeColour,
        Brow brow,
        Mouth mouth,
        boolean nose,
        boolean blush,
        Robe robe,
        int accent,
        Headwear headwear,
        boolean bandage,
        boolean scar,
        boolean old,
        boolean dead) {

    public enum Face { OVAL, ROUND, SHARP }

    public enum Skin { PALE, LIGHT, TAN }

    public enum HairColour {
        INK_VIOLET, INK_BLUE, DARK_BROWN, CHESTNUT, SILVER,
        GREY_INK_VIOLET, GREY_INK_BLUE, GREY_DARK_BROWN, GREY_CHESTNUT;

        /** The greying form of a colour (silver stays silver). */
        public HairColour greying() {
            return switch (this) {
                case INK_VIOLET -> GREY_INK_VIOLET;
                case INK_BLUE -> GREY_INK_BLUE;
                case DARK_BROWN -> GREY_DARK_BROWN;
                case CHESTNUT -> GREY_CHESTNUT;
                default -> this;
            };
        }
    }

    public enum Front { HIME, SPLIT, SWEEP, CURTAIN, SPIKY }

    public enum Back { SHORT, LONG, PONYTAIL, TWIN_BUNS, HALF_UP, BUN }

    public enum EyeShape { ROUND, ALMOND, SHARP, DROOPY }

    public enum EyeColour { METAL, WOOD, WATER, FIRE, EARTH, SPIRIT }

    public enum Brow { STRAIGHT, ARCHED, ANGRY, WORRIED }

    public enum Mouth { SMILE, NEUTRAL, SMALL, SMIRK, FROWN, OPEN }

    /** The realm tier of the robe; the gender picks the cut. */
    public enum Robe { PLAIN, DYED, BROCADE, COURT }

    public enum Headwear { NONE, CLOTH, HEADBAND, RIBBON, CROWN, PHOENIX_PIN, JADE_PIN }

    public static final int MAX_ACCENT = 6;

    public PortraitSpec {
        if (accent < 0 || accent > MAX_ACCENT) {
            throw new IllegalArgumentException("accent " + accent);
        }
    }

    /** The Python generator's key for the robe: tier plus "_f" or "_m". */
    public String robeKey() {
        return robe.name().toLowerCase(java.util.Locale.ROOT) + (female ? "_f" : "_m");
    }

    /** Lower-cased enum name: the variant's file name on disk. */
    public static String key(Enum<?> e) {
        return e.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static final int F_FEMALE = 1;
    private static final int F_NOSE = 2;
    private static final int F_BLUSH = 4;
    private static final int F_BANDAGE = 8;
    private static final int F_SCAR = 16;
    private static final int F_OLD = 32;
    private static final int F_DEAD = 64;

    /** Thirteen bytes: a flag byte, eleven enum ordinals, the accent. */
    public void write(FriendlyByteBuf buf) {
        int flags = (female ? F_FEMALE : 0) | (nose ? F_NOSE : 0) | (blush ? F_BLUSH : 0) | (bandage ? F_BANDAGE : 0)
                | (scar ? F_SCAR : 0) | (old ? F_OLD : 0) | (dead ? F_DEAD : 0);
        buf.writeByte(flags);
        buf.writeByte(face.ordinal());
        buf.writeByte(skin.ordinal());
        buf.writeByte(hairColour.ordinal());
        buf.writeByte(front.ordinal());
        buf.writeByte(back.ordinal());
        buf.writeByte(eyeShape.ordinal());
        buf.writeByte(eyeColour.ordinal());
        buf.writeByte(brow.ordinal());
        buf.writeByte(mouth.ordinal());
        buf.writeByte(robe.ordinal());
        buf.writeByte(headwear.ordinal());
        buf.writeByte(accent);
    }

    public static PortraitSpec read(FriendlyByteBuf buf) {
        int flags = buf.readUnsignedByte();
        Face face = ordinal(Face.values(), buf.readUnsignedByte());
        Skin skin = ordinal(Skin.values(), buf.readUnsignedByte());
        HairColour hair = ordinal(HairColour.values(), buf.readUnsignedByte());
        Front front = ordinal(Front.values(), buf.readUnsignedByte());
        Back back = ordinal(Back.values(), buf.readUnsignedByte());
        EyeShape eyeShape = ordinal(EyeShape.values(), buf.readUnsignedByte());
        EyeColour eyeColour = ordinal(EyeColour.values(), buf.readUnsignedByte());
        Brow brow = ordinal(Brow.values(), buf.readUnsignedByte());
        Mouth mouth = ordinal(Mouth.values(), buf.readUnsignedByte());
        Robe robe = ordinal(Robe.values(), buf.readUnsignedByte());
        Headwear headwear = ordinal(Headwear.values(), buf.readUnsignedByte());
        int accent = buf.readUnsignedByte();
        if (accent > MAX_ACCENT) {
            throw new io.netty.handler.codec.DecoderException("portrait accent " + accent);
        }
        return new PortraitSpec((flags & F_FEMALE) != 0, face, skin, hair, front, back, eyeShape, eyeColour, brow, mouth,
                (flags & F_NOSE) != 0, (flags & F_BLUSH) != 0, robe, accent, headwear, (flags & F_BANDAGE) != 0,
                (flags & F_SCAR) != 0, (flags & F_OLD) != 0, (flags & F_DEAD) != 0);
    }

    private static <E extends Enum<E>> E ordinal(E[] values, int i) {
        if (i < 0 || i >= values.length) {
            throw new io.netty.handler.codec.DecoderException("portrait field " + i + " of " + values.length);
        }
        return values[i];
    }
}
