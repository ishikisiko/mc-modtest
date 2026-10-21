package com.example.myvillage.spiritstone;

import com.example.myvillage.item.SpiritStoneTags;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class SpiritStoneExchangeTest {
    private List<SpiritStoneDenominations.Tier> tiers;
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    }
    @BeforeEach void bind() {
        var items = List.of(Items.EMERALD, Items.DIAMOND, Items.AMETHYST_SHARD);
        tiers = new ArrayList<>();
        var tags = new HashMap<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>, List<net.minecraft.core.Holder<net.minecraft.world.item.Item>>>();
        tags.put(SpiritStoneStorage.BAGS, List.of(Items.BUNDLE.builtInRegistryHolder()));
        var configured = SpiritStoneDenominations.get().tiers();
        for (int i = 0; i < configured.size(); i++) {
            var t = configured.get(i); var item = items.get(i);
            tiers.add(new SpiritStoneDenominations.Tier(t.id(), BuiltInRegistries.ITEM.getKey(item), t.tag(), t.units()));
            tags.put(t.tag(), i == 0 ? List.of(item.builtInRegistryHolder(), Items.QUARTZ.builtInRegistryHolder()) : List.of(item.builtInRegistryHolder()));
        }
        BuiltInRegistries.ITEM.bindTags(tags);
    }
    @AfterEach void clear() { BuiltInRegistries.ITEM.bindTags(Map.of()); }
    private ItemStack bag(BigInteger low, BigInteger medium, BigInteger high) {
        var bag = new ItemStack(Items.BUNDLE);
        for (var e : Map.of(Items.EMERALD, low, Items.DIAMOND, medium, Items.AMETHYST_SHARD, high).entrySet())
            SpiritStoneStorage.insert(bag, new ItemStack(e.getKey()), e.getValue(), false);
        return bag;
    }
    private BigInteger count(List<ItemStack> inv, int grade) {
        return inv.stream().map(s -> SpiritStoneStorage.count(s, item -> item.is(tiers.get(grade).tag())))
                .reduce(BigInteger.ZERO, BigInteger::add);
    }
    private SpiritStoneExchange.Plan plan(List<ItemStack> inv, int from, int to, int n) {
        return SpiritStoneExchange.plan(inv, tiers.get(from), tiers.get(to), n);
    }
    @Test void bundledGradesHaveApprovedRatiosAndDistinctItems() {
        assertEquals(List.of(BigInteger.ONE, BigInteger.valueOf(100), BigInteger.valueOf(10000)),
                SpiritStoneDenominations.get().tiers().stream().map(SpiritStoneDenominations.Tier::units).toList());
        assertEquals(3, SpiritStoneDenominations.get().tiers().stream().map(SpiritStoneDenominations.Tier::item).distinct().count());
    }
    @Test void looseAndBagFundsCombineAndRoundTripExactly() {
        var inv = List.of(bag(BigInteger.valueOf(70), BigInteger.ZERO, BigInteger.ZERO), new ItemStack(Items.EMERALD, 30));
        var up = plan(inv, 0, 1, 1); assertTrue(up.ready());
        assertEquals(BigInteger.ZERO, count(up.items(), 0)); assertEquals(BigInteger.ONE, count(up.items(), 1));
        assertEquals(BigInteger.valueOf(100), count(inv, 0));
        var down = plan(up.items(), 1, 0, 1); assertTrue(down.ready());
        assertEquals(BigInteger.valueOf(100), count(down.items(), 0)); assertEquals(BigInteger.ZERO, count(down.items(), 1));
    }
    @Test void hugeBalancesKeepRemaindersAndNeverExpandIntoLooseStacks() {
        var huge = BigInteger.TEN.pow(100).add(BigInteger.valueOf(19));
        var inv = List.of(bag(BigInteger.ZERO, huge, BigInteger.ZERO));
        var up = plan(inv, 1, 2, Integer.MAX_VALUE); assertTrue(up.ready());
        assertEquals(huge.subtract(BigInteger.valueOf(Integer.MAX_VALUE).multiply(BigInteger.valueOf(100))), count(up.items(), 1));
        var down = plan(up.items(), 2, 1, Integer.MAX_VALUE); assertTrue(down.ready());
        assertEquals(huge, count(down.items(), 1)); assertEquals(1, down.items().size());
    }
    @Test void shortageAndFullInventoryNeverMutateOriginal() {
        var shortInv = List.of(new ItemStack(Items.EMERALD, 64), new ItemStack(Items.EMERALD, 35));
        assertEquals("short", plan(shortInv, 0, 1, 1).result()); assertEquals(BigInteger.valueOf(99), count(shortInv, 0));
        var full = new ArrayList<ItemStack>();
        full.add(new ItemStack(Items.DIAMOND, 2));
        for (int i=1; i<36; i++) full.add(new ItemStack(Items.STONE, 64));
        assertEquals("full", plan(full, 1, 0, 1).result()); assertEquals(2, full.getFirst().getCount());
    }
    @Test void splitUsesTwoSlotsAndNeverOverstacks() {
        var inv = List.of(new ItemStack(Items.DIAMOND), ItemStack.EMPTY);
        var split = plan(inv, 1, 0, 1); assertTrue(split.ready());
        assertEquals(List.of(64, 36), split.items().stream().map(ItemStack::getCount).toList());
        assertEquals("full", plan(List.of(new ItemStack(Items.DIAMOND)), 1, 0, 1).result());
    }
    @Test void namedItemsAndExternalAliasesCannotBeLaundered() {
        var named = new ItemStack(Items.DIAMOND); named.set(DataComponents.CUSTOM_NAME, Component.literal("纪念灵石"));
        assertEquals("short", plan(List.of(named, ItemStack.EMPTY, ItemStack.EMPTY), 1, 0, 1).result());
        var external = List.of(new ItemStack(Items.QUARTZ, 64), new ItemStack(Items.QUARTZ, 36));
        assertEquals("short", plan(external, 0, 1, 1).result()); assertEquals(64, external.getFirst().getCount());
    }
    @Test void pausedBagAcceptsActiveExchangeWithoutResumingAutomaticCollection() {
        var bag = bag(BigInteger.ZERO, BigInteger.ONE, BigInteger.ZERO); SpiritStoneStorage.autoCollect(bag, false);
        var down = plan(List.of(bag), 1, 0, 1); assertTrue(down.ready());
        assertFalse(SpiritStoneStorage.autoCollect(down.items().getFirst())); assertEquals(BigInteger.valueOf(100), count(down.items(), 0));
    }
    @Test void highGradesAreCollectedButNeverPayOneForOneLowGradeCosts() {
        var inventory = new ArrayList<>(List.of(bag(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO),
                new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.AMETHYST_SHARD, 2)));
        assertTrue(SpiritStoneStorage.collect(inventory)); assertEquals(BigInteger.valueOf(3), count(inventory, 1));
        assertEquals(BigInteger.valueOf(2), count(inventory, 2));
        assertFalse(SpiritStoneStorage.remove(inventory, SpiritStoneTags::isLowGrade, 1));
        assertEquals(BigInteger.valueOf(2), count(inventory, 2));
    }
    @Test void invalidBatchesOrRemovedTagsCannotMutateBalances() {
        var inv = List.of(bag(BigInteger.valueOf(100), BigInteger.ZERO, BigInteger.ZERO));
        assertEquals("invalid", plan(inv, 0, 1, 0).result());
        assertEquals("invalid", plan(inv, 0, 0, 1).result());
        clear(); assertEquals("invalid", plan(inv, 0, 1, 1).result());
        assertEquals(BigInteger.valueOf(100), SpiritStoneStorage.entries(inv.getFirst()).getFirst().quantity());
    }
}
