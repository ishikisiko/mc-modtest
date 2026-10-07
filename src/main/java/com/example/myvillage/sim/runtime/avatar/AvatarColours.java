package com.example.myvillage.sim.runtime.avatar;

import com.example.myvillage.portrait.NpcColours;
import com.example.myvillage.portrait.PortraitAssign;
import com.example.myvillage.sim.PersonView;
import javax.annotation.Nullable;

/**
 * The hair and eye colours of a ledger person's avatar: those of the person's portrait on the given
 * sim day ({@link PortraitAssign#of(PersonView, long, int)}), so the 3D avatar wears what the
 * dialogue and the 天下 panel draw, and greys with age as the portrait does. Pure: no Minecraft types.
 */
final class AvatarColours {
    private AvatarColours() {
    }

    /**
     * The colours of {@code p} on sim day {@code day}; throws whatever {@link PortraitAssign#of} throws
     * for a malformed record or calendar (the caller keeps the avatar and leaves its colours alone).
     */
    static NpcColours of(PersonView p, long day, int daysPerYear) {
        return NpcColours.of(PortraitAssign.of(p, day, daysPerYear));
    }

    /** The colours to set on an entity that wears {@code current}, or null when it already wears {@code wanted}. */
    @Nullable
    static NpcColours change(@Nullable NpcColours current, NpcColours wanted) {
        return wanted.equals(current) ? null : wanted;
    }
}
