package com.example.myvillage.cultivation.crafting;

import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/** Stable independent domains. Never consume player/world/root random streams. */
public final class CraftingGenerator {
    // Persistent generation contract: changing either domain rerolls future new players.
    private static final long TALISMAN_DOMAIN = 0x74616c69736d6103L;
    private CraftingGenerator() { }

    public static CraftingAptitudes ensure(CraftingAptitudes current, boolean awakened,
            long worldSeed, UUID player, CraftingRules rules,CraftingOpeningRules.Selection selection) {
        Objects.requireNonNull(current, "current");
        if (current.talismanInitialized() || !awakened || !selection.confirmed()) return current;
        Objects.requireNonNull(player, "player"); Objects.requireNonNull(rules, "rules");
        long identity = mix(worldSeed) ^ mix(player.getMostSignificantBits())
                ^ Long.rotateLeft(mix(player.getLeastSignificantBits()), 23);
        return new CraftingAptitudes(CraftingAptitudes.CURRENT_VERSION, true,
                current.initialized() ? current.alchemy() : CraftingOpeningRules.get().score(player,CraftingAptitudes.Profession.ALCHEMY,selection),
                current.initialized() ? current.forging() : CraftingOpeningRules.get().score(player,CraftingAptitudes.Profession.FORGING,selection),
                roll(identity, TALISMAN_DOMAIN, rules));
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
