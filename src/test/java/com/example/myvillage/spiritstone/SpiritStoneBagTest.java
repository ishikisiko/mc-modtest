package com.example.myvillage.spiritstone;

import com.example.myvillage.alchemy.AlchemyAutofill;
import com.example.myvillage.alchemy.AlchemyRules;
import com.example.myvillage.forging.ForgingCosts;
import com.example.myvillage.item.SpiritStoneTags;
import com.example.myvillage.sim.commerce.CommerceRules;
import com.example.myvillage.sim.runtime.commerce.CommerceInventory;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class SpiritStoneBagTest {
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    }
    @BeforeEach void tags() {
        BuiltInRegistries.ITEM.bindTags(Map.of(SpiritStoneStorage.BAGS, List.of(Items.BUNDLE.builtInRegistryHolder()),
                SpiritStoneTags.LOW_GRADE_SPIRIT_STONE, List.of(Items.EMERALD.builtInRegistryHolder(), Items.AMETHYST_SHARD.builtInRegistryHolder())));
    }
    @AfterEach void clear() { BuiltInRegistries.ITEM.bindTags(Map.of(SpiritStoneStorage.BAGS, List.of(), SpiritStoneTags.LOW_GRADE_SPIRIT_STONE, List.of())); }
    private static ItemStack bag(ItemStack... items) {
        var bag = new ItemStack(Items.BUNDLE); SpiritStoneStorage.contents(bag, List.of(items)); return bag;
    }
    private static long count(List<ItemStack> inventory) {
        return inventory.stream().mapToLong(s -> SpiritStoneStorage.count(s, SpiritStoneTags::isLowGrade)).sum();
    }
    private static CommerceRules.Offer offer(String kind, int count, int price) {
        return new CommerceRules.Offer("iron", "smith", kind, "minecraft:iron_ingot", count, price, "items", count, 4, 64, 0);
    }
    @Test void collectionPreservesExactExternalItemsAndComponents() {
        var named = new ItemStack(Items.EMERALD, 9); named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep identity"));
        var inventory = new ArrayList<>(List.of(bag(), named.copy(), new ItemStack(Items.AMETHYST_SHARD, 12), new ItemStack(Items.DIAMOND, 5)));
        assertTrue(SpiritStoneStorage.collect(inventory));
        assertTrue(inventory.get(1).isEmpty()); assertTrue(inventory.get(2).isEmpty()); assertEquals(5, inventory.get(3).getCount());
        var stored = SpiritStoneStorage.contents(inventory.get(0));
        assertTrue(ItemStack.matches(named, stored.get(0))); assertTrue(stored.get(1).is(Items.AMETHYST_SHARD)); assertEquals(21, count(inventory));
        assertFalse(SpiritStoneStorage.collect(inventory));
    }
    @Test void fullBagsLeaveOverflowInInventoryWithoutDeletingStones() {
        ItemStack[] stacks = new ItemStack[SpiritStoneBagRules.get().slots()];
        Arrays.setAll(stacks, i -> new ItemStack(Items.EMERALD, 64));
        var full = bag(stacks); var source = new ItemStack(Items.EMERALD, 11);
        assertEquals(11, SpiritStoneStorage.insert(full, source, 11, true));
        assertEquals(SpiritStoneBagRules.get().slots() * 64, SpiritStoneStorage.count(full, SpiritStoneTags::isLowGrade)); assertEquals(11, source.getCount());
    }
    @Test void pausedCollectionDoesNotDisablePaymentAndCannotNestBags() {
        var bag = bag(new ItemStack(Items.EMERALD, 8)); SpiritStoneStorage.autoCollect(bag, false);
        assertEquals(4, SpiritStoneStorage.insert(bag, new ItemStack(Items.EMERALD, 4), 4, true));
        assertFalse(SpiritStoneStorage.collectible(bag));
        assertTrue(SpiritStoneStorage.remove(List.of(bag), SpiritStoneTags::isLowGrade, 3)); assertEquals(5, count(List.of(bag)));
    }
    @Test void multipleBagsAndLooseStonesPayTogetherWithoutPartialShortage() {
        var inventory = List.of(bag(new ItemStack(Items.EMERALD, 2)), new ItemStack(Items.AMETHYST_SHARD, 3), bag(new ItemStack(Items.EMERALD, 5)));
        assertFalse(SpiritStoneStorage.remove(inventory, SpiritStoneTags::isLowGrade, 11)); assertEquals(10, count(inventory));
        assertTrue(SpiritStoneStorage.remove(inventory, SpiritStoneTags::isLowGrade, 9)); assertEquals(1, count(inventory));
    }
    @Test void purchasesAndRestReadBagFundsAndLeaveOriginalUntouched() {
        var inventory = List.of(bag(new ItemStack(Items.EMERALD, 8)), new ItemStack(Items.EMERALD, 2), ItemStack.EMPTY);
        var plan = CommerceInventory.plan(inventory, "minecraft:emerald", offer("buy", 2, 7));
        assertTrue(plan.ready()); assertEquals(3, CommerceInventory.balance(plan.items(), "minecraft:emerald"));
        assertEquals(10, CommerceInventory.balance(inventory, "minecraft:emerald"));
        var rest = new CommerceRules.Offer("rest", "innkeeper", "rest", "minecraft:air", 0, 5, "service", 0, 0, 1, 0);
        assertTrue(CommerceInventory.plan(inventory, "minecraft:emerald", rest).ready());
    }
    @Test void fullInventoryPurchaseAndShortPaymentNeverChangeBag() {
        var inventory = List.of(bag(new ItemStack(Items.EMERALD, 8)), new ItemStack(Items.STONE, 64));
        assertEquals("inventory_full", CommerceInventory.plan(inventory, "minecraft:emerald", offer("buy", 1, 2)).result());
        assertEquals("insufficient_funds", CommerceInventory.plan(inventory, "minecraft:emerald", offer("buy", 1, 9)).result());
        assertEquals(8, count(inventory));
    }
    @Test void saleProceedsGoIntoBagEvenWithoutAFreedOrdinarySlot() {
        var inventory = List.of(bag(new ItemStack(Items.EMERALD, 8)), new ItemStack(Items.IRON_INGOT, 64));
        var plan = CommerceInventory.plan(inventory, "minecraft:emerald", offer("sell", 1, 5));
        assertTrue(plan.ready()); assertEquals(13, count(plan.items())); assertEquals(63, plan.items().get(1).getCount());
        assertEquals(8, count(inventory)); assertEquals(64, inventory.get(1).getCount());
    }
    @Test void configuredCommerceCurrencyDoesNotSpendAliasesOrNamedStones() {
        var named = new ItemStack(Items.EMERALD, 4); named.set(DataComponents.CUSTOM_NAME, Component.literal("Not tender"));
        var inventory = List.of(bag(named, new ItemStack(Items.AMETHYST_SHARD, 8)), ItemStack.EMPTY);
        assertEquals(0, CommerceInventory.balance(inventory, "minecraft:emerald"));
        assertEquals("insufficient_funds", CommerceInventory.plan(inventory, "minecraft:emerald", offer("buy", 1, 1)).result());
        assertEquals(12, count(inventory));
    }
    @Test void overlappingForgingCostsAllocateBagContentsWithoutDoubleCounting() {
        var inventory = List.of(bag(new ItemStack(Items.EMERALD, 2), new ItemStack(Items.AMETHYST_SHARD, 2)));
        var costs = Map.of(SpiritStoneTags.LOW_GRADE_REFERENCE, 2, "minecraft:emerald", 2);
        var plan = ForgingCosts.plan(inventory, costs).orElseThrow(); assertEquals(0, count(plan)); assertEquals(4, count(inventory));
        assertTrue(ForgingCosts.plan(inventory, Map.of(SpiritStoneTags.LOW_GRADE_REFERENCE, 3, "minecraft:emerald", 2)).isEmpty());
    }
    @Test void furnaceAutofillWithdrawsOnlyOneBatchFromBag() {
        var inventory = List.of(bag(new ItemStack(Items.EMERALD, 10)), new ItemStack(Items.CARROT, 2), new ItemStack(Items.BROWN_MUSHROOM, 2));
        var plan = AlchemyAutofill.plan(inventory, List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY),
                List.of("minecraft:carrot", "minecraft:brown_mushroom", AlchemyRules.FUEL), List.of(1, 1, 2)).orElseThrow();
        assertEquals(8, count(plan.inventory())); assertEquals(2, plan.inputs().get(2).getCount()); assertTrue(plan.inputs().get(2).is(Items.EMERALD));
        assertEquals(10, count(inventory));
    }
    @Test void reloadedAwayItemsAreRetainedButStopPaying() {
        var bag = bag(new ItemStack(Items.EMERALD, 4)); clear(); tags();
        BuiltInRegistries.ITEM.bindTags(Map.of(SpiritStoneStorage.BAGS, List.of(Items.BUNDLE.builtInRegistryHolder()), SpiritStoneTags.LOW_GRADE_SPIRIT_STONE, List.of()));
        assertFalse(SpiritStoneStorage.remove(List.of(bag), SpiritStoneTags::isLowGrade, 1));
        assertEquals(4, SpiritStoneStorage.contents(bag).getFirst().getCount());
    }
    @Test void staleSnapshotsRejectWithoutOverwritingNewBagBalance() {
        var slot = new AtomicReference<>(bag(new ItemStack(Items.EMERALD, 6)));
        var access = new SpiritStoneSources.Access(List.of(new SpiritStoneSources.Slot("accessory:test", slot::get, slot::set)), () -> {});
        var planned = access.copies(); assertTrue(SpiritStoneStorage.remove(planned, SpiritStoneTags::isLowGrade, 2));
        slot.set(bag(new ItemStack(Items.EMERALD, 9)));
        assertFalse(access.commit(planned)); assertEquals(9, count(List.of(slot.get())));
    }
    @Test void nativeItemSaveAndRegistryNamedStreamRoundTripsPreserveContentsAndPausedFlag() {
        var named = new ItemStack(Items.EMERALD, 13); named.set(DataComponents.CUSTOM_NAME, Component.literal("Persistent stone"));
        var bag = bag(named, new ItemStack(Items.AMETHYST_SHARD, 7)); SpiritStoneStorage.autoCollect(bag, false);
        var registry = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = RegistryOps.create(JsonOps.INSTANCE, registry);
        var saved = ItemStack.CODEC.encodeStart(ops, bag).getOrThrow();
        var restored = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        assertTrue(ItemStack.matches(bag, restored)); assertFalse(SpiritStoneStorage.autoCollect(restored));
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registry);
        // Standalone bootstrap has no Forge registry-sync negotiation. Named-registry wire encoding
        // exercises the same persistent components; the real-client run covers ID-synced packets.
        var codec = net.minecraft.network.codec.ByteBufCodecs.fromCodecWithRegistries(ItemStack.CODEC);
        try { codec.encode(buf, bag); assertTrue(ItemStack.matches(bag, codec.decode(buf))); }
        finally { buf.release(); }
    }
}
