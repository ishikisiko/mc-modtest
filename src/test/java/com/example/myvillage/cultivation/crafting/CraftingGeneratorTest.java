package com.example.myvillage.cultivation.crafting;

import java.io.StringReader;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftingGeneratorTest {
    private static final UUID PLAYER = UUID.fromString("12345678-1234-5678-9abc-def012345678");
    @Test void stableIndependentDomainsHavePinnedResultsAndUseWorldAndPlayerIdentity() {
        var rules = CraftingRules.get();
        var expected = new CraftingAptitudes(1, true, 67, 96);
        assertEquals(expected, CraftingGenerator.ensure(CraftingAptitudes.EMPTY, true, 987654321L, PLAYER, rules));
        assertEquals(expected, CraftingGenerator.ensure(CraftingAptitudes.EMPTY, true, 987654321L, PLAYER, rules));
        assertNotEquals(expected, CraftingGenerator.ensure(CraftingAptitudes.EMPTY, true, 987654322L, PLAYER, rules));
        assertNotEquals(expected, CraftingGenerator.ensure(CraftingAptitudes.EMPTY, true, 987654321L, new UUID(0, 1), rules));
    }
    @Test void pendingAndAlreadyGeneratedStateNeverRegenerateEvenAfterRootAndRuleChanges() {
        assertSame(CraftingAptitudes.EMPTY, CraftingGenerator.ensure(CraftingAptitudes.EMPTY, false, 0, null, null));
        var saved = new CraftingAptitudes(1, true, 0, 100);
        assertSame(saved, CraftingGenerator.ensure(saved, true, Long.MAX_VALUE, null, null));
        assertSame(saved, CraftingGenerator.ensure(saved, false, Long.MIN_VALUE, null, null));
        // Lifecycle callers can change profile/root/world inputs; persisted initialization wins.
        assertSame(saved, CraftingGenerator.ensure(saved, true, 123, PLAYER, CraftingRules.get()));
    }
    @Test void twoProfessionRollsCoverConfiguredIntegerRangeWithoutBeingTiedTogether() {
        var alchemy = new HashSet<Integer>(); var forging = new HashSet<Integer>(); int different = 0;
        for (int i = 0; i < 10000; i++) {
            var state = CraftingGenerator.ensure(CraftingAptitudes.EMPTY, true, 321, new UUID(1, i), CraftingRules.get());
            alchemy.add(state.alchemy()); forging.add(state.forging());
            if (state.alchemy() != state.forging()) different++;
        }
        assertEquals(101, alchemy.size()); assertEquals(101, forging.size()); assertTrue(different > 9000);
    }
    @Test void generationRespectsSingleValueRollRangeFromData() {
        var rules = CraftingRules.parse(new StringReader("""
                {"version":1,"initial_roll":{"min":17,"max":17},"quality_thresholds":[
                 {"quality":1,"minimum":0},{"quality":2,"minimum":40},
                 {"quality":3,"minimum":70},{"quality":4,"minimum":90}]}
                """));
        assertEquals(new CraftingAptitudes(1, true, 17, 17),
                CraftingGenerator.ensure(CraftingAptitudes.EMPTY, true, 1, PLAYER, rules));
    }
}
