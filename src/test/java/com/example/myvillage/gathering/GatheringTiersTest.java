package com.example.myvillage.gathering;

import com.example.myvillage.spiritstone.SpiritStoneDenominations;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class GatheringTiersTest {
    private final GatheringRules rules = GatheringRules.get();
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    }
    @AfterEach void clearTags() { BuiltInRegistries.ITEM.bindTags(Map.of()); }
    @Test void approvedSixCombinationsHaveExactBonusesAndFootprints() {
        int[][] expected = {{2500,0,0},{3000,4000,0},{3500,5000,6000}};
        for (var tier : GatheringTier.values()) for (var fuel : GatheringFuel.values()) {
            assertEquals(expected[tier.ordinal()][fuel.ordinal()], rules.tier(tier).bonus(fuel));
            assertEquals(3+tier.ordinal(), rules.tier(tier).radius());
            assertEquals(1+tier.ordinal(), rules.tier(tier).innerRadius());
        }
        assertEquals(3, rules.maxInnerRadius());
        assertEquals(List.of(20,200,2000), Arrays.stream(GatheringFuel.values()).map(rules::charges).toList());
    }
    @Test void higherFuelGetsExactlyItsConfiguredChargesAndNeverFreeRefills() {
        for (var fuel : GatheringFuel.values()) {
            var state = GatheringEnergy.EMPTY;
            var first = state.debit(GatheringTier.HIGH, fuel, rules);
            assertTrue(first.consumedStone()); state = first.remaining();
            for (int i=1; i<rules.charges(fuel); i++) {
                var next = state.debit(GatheringTier.HIGH, null, rules);
                assertNotNull(next); assertFalse(next.consumedStone()); state = next.remaining();
            }
            assertEquals(GatheringEnergy.EMPTY, state);
            assertNull(state.debit(GatheringTier.HIGH, null, rules));
        }
    }
    @Test void replacingQueuedFuelCannotUpgradeOrDowngradeExistingCredit() {
        var low = new GatheringEnergy(2, GatheringFuel.LOW);
        assertEquals(3500, low.bonus(GatheringTier.HIGH, GatheringFuel.HIGH, rules));
        var first = low.debit(GatheringTier.HIGH, GatheringFuel.HIGH, rules);
        assertFalse(first.consumedStone()); assertEquals(1, first.remaining().charges());
        assertEquals(3500, first.remaining().bonus(GatheringTier.HIGH, GatheringFuel.HIGH, rules));
        var empty = first.remaining().debit(GatheringTier.HIGH, GatheringFuel.HIGH, rules).remaining();
        assertEquals(6000, empty.bonus(GatheringTier.HIGH, GatheringFuel.HIGH, rules));
        var high = empty.debit(GatheringTier.HIGH, GatheringFuel.HIGH, rules);
        assertTrue(high.consumedStone()); assertEquals(1999, high.remaining().charges());
        assertEquals(6000, high.remaining().bonus(GatheringTier.HIGH, GatheringFuel.LOW, rules));
        assertEquals(2, low.charges()); // Planning leaves the rollback snapshot intact.
    }
    @Test void unsupportedFuelIsRejectedWithoutConsumingAStone() {
        assertNull(GatheringEnergy.EMPTY.debit(GatheringTier.LOW, GatheringFuel.MEDIUM, rules));
        assertNull(GatheringEnergy.EMPTY.debit(GatheringTier.LOW, GatheringFuel.HIGH, rules));
        assertNull(GatheringEnergy.EMPTY.debit(GatheringTier.MEDIUM, GatheringFuel.HIGH, rules));
    }
    @Test void oldSaveMigratesToLowGradeCreditAndNewSaveKeepsGrade() {
        assertEquals(new GatheringEnergy(17,GatheringFuel.LOW), GatheringEnergy.restore(1,17,"",GatheringTier.LOW,rules));
        assertEquals(new GatheringEnergy(1943,GatheringFuel.HIGH), GatheringEnergy.restore(2,1943,"high_grade",GatheringTier.HIGH,rules));
        assertEquals(GatheringEnergy.EMPTY,GatheringEnergy.restore(2,1943,"high_grade",GatheringTier.MEDIUM,rules));
        for (var bad : List.of("", "unknown")) assertEquals(GatheringEnergy.EMPTY,GatheringEnergy.restore(2,12,bad,GatheringTier.HIGH,rules));
        assertEquals(GatheringEnergy.EMPTY,GatheringEnergy.restore(2,32768,"high_grade",GatheringTier.HIGH,rules));
        assertEquals(GatheringEnergy.EMPTY,GatheringEnergy.restore(99,17,"low_grade",GatheringTier.LOW,rules));
    }
    @Test void explicitGradeTagsAreRequiredAndAmbiguousItemsAreRejected() {
        var tiers = SpiritStoneDenominations.get().tiers();
        Map<TagKey<Item>,List<Holder<Item>>> tags = new HashMap<>();
        var items = List.of(Items.EMERALD,Items.DIAMOND,Items.AMETHYST_SHARD);
        for (int i=0;i<3;i++) tags.put(tiers.get(i).tag(),List.of(items.get(i).builtInRegistryHolder()));
        BuiltInRegistries.ITEM.bindTags(tags);
        for (int i=0;i<3;i++) assertEquals(GatheringFuel.values()[i],GatheringFuel.of(new ItemStack(items.get(i))));
        assertNull(GatheringFuel.of(new ItemStack(Items.STONE)));
        tags.put(tiers.get(0).tag(),List.of(Items.EMERALD.builtInRegistryHolder(),Items.DIAMOND.builtInRegistryHolder()));
        BuiltInRegistries.ITEM.bindTags(tags);assertNull(GatheringFuel.of(new ItemStack(Items.DIAMOND)));
    }
    @Test void allBonusesKeepFloorRoundingAndStageCap() {
        assertEquals(3,GatheringMath.bonus(10,100,3000));assertEquals(3,GatheringMath.bonus(10,100,3500));
        assertEquals(15,GatheringMath.bonus(50,100,3000));assertEquals(20,GatheringMath.bonus(50,100,4000));
        assertEquals(17,GatheringMath.bonus(50,100,3500));assertEquals(25,GatheringMath.bonus(50,100,5000));
        assertEquals(30,GatheringMath.bonus(50,100,6000));assertEquals(1,GatheringMath.bonus(50,1,6000));
        assertEquals(0,GatheringMath.bonus(50,0,6000));
    }
}
