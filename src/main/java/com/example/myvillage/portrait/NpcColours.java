package com.example.myvillage.portrait;

import javax.annotation.Nullable;

/**
 * The colours a ledger person's 3D avatar shares with their portrait: the hair colour and the eye
 * colour of their {@link PortraitSpec}. The server packs it into one synced int on the NPC entity
 * ({@code NpcEntity.colours()}); the client recolours the look's baked texture with it
 * ({@link NpcSkinComposer}). {@link #NONE} is the packed form of "no colours": the NPC keeps the
 * colours baked into its texture (every summoned cultivator, and an avatar before the simulation
 * has set them).
 */
public record NpcColours(PortraitSpec.HairColour hair, PortraitSpec.EyeColour eye) {
    /** The packed value of an NPC without colours. */
    public static final int NONE = -1;
    private static final int EYE_BITS = 4;
    private static final int EYE_MASK = (1 << EYE_BITS) - 1;

    public NpcColours {
        if (hair == null || eye == null) {
            throw new IllegalArgumentException("hair and eye colours are required");
        }
    }

    /** The colours of a portrait spec. */
    public static NpcColours of(PortraitSpec spec) {
        return new NpcColours(spec.hairColour(), spec.eyeColour());
    }

    /** {@code hair << 4 | eye}, never {@link #NONE}. */
    public int pack() {
        return hair.ordinal() << EYE_BITS | eye.ordinal();
    }

    /** The colours of a packed value, or null for {@link #NONE} and for any value that names no colour. */
    @Nullable
    public static NpcColours unpack(int packed) {
        if (packed < 0) {
            return null;
        }
        int hair = packed >> EYE_BITS;
        int eye = packed & EYE_MASK;
        PortraitSpec.HairColour[] hairs = PortraitSpec.HairColour.values();
        PortraitSpec.EyeColour[] eyes = PortraitSpec.EyeColour.values();
        if (hair >= hairs.length || eye >= eyes.length) {
            return null;
        }
        return new NpcColours(hairs[hair], eyes[eye]);
    }
}
