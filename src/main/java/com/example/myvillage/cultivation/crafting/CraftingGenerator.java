package com.example.myvillage.cultivation.crafting;

import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/** Stable independent domains. Never consume player/world/root random streams. */
public final class CraftingGenerator {
    // Persistent generation contract: changing either domain rerolls future new players.
    private static final long ALCHEMY_DOMAIN = 0x616c6368656d7901L;
    private static final long FORGING_DOMAIN = 0x666f7267696e6702L;
    private CraftingGenerator() { }

    public static CraftingAptitudes ensure(CraftingAptitudes current, boolean awakened,
            long worldSeed, UUID player, CraftingRules rules) {
        Objects.requireNonNull(current, "current");
        if (current.initialized() || !awakened) return current;
        Objects.requireNonNull(player, "player"); Objects.requireNonNull(rules, "rules");
        long identity = mix(worldSeed) ^ mix(player.getMostSignificantBits())
                ^ Long.rotateLeft(mix(player.getLeastSignificantBits()), 23);
        return new CraftingAptitudes(CraftingAptitudes.CURRENT_VERSION, true,
                roll(identity, ALCHEMY_DOMAIN, rules), roll(identity, FORGING_DOMAIN, rules));
    }
    private static int roll(long identity, long domain, CraftingRules rules) {
        return rules.initialMin() + new Random(mix(identity ^ domain))
                .nextInt(rules.initialMax() - rules.initialMin() + 1);
    }
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
