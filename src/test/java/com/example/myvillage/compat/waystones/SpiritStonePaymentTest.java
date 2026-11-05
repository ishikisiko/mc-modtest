package com.example.myvillage.compat.waystones;

import com.example.myvillage.spiritstone.SpiritStoneDenominations;
import com.example.myvillage.spiritstone.SpiritStoneSources;
import com.example.myvillage.spiritstone.SpiritStoneStorage;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiritStonePaymentTest {
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach void tags() {
        BuiltInRegistries.ITEM.bindTags(Map.of(
                SpiritStoneStorage.BAGS, List.of(Items.RABBIT_HIDE.builtInRegistryHolder()),
                SpiritStoneDenominations.get().tiers().get(0).tag(), List.of(Items.EMERALD.builtInRegistryHolder(), Items.QUARTZ.builtInRegistryHolder()),
                SpiritStoneDenominations.get().tiers().get(1).tag(), List.of(Items.DIAMOND.builtInRegistryHolder()),
                SpiritStoneDenominations.get().tiers().get(2).tag(), List.of(Items.AMETHYST_SHARD.builtInRegistryHolder())));
    }

    @AfterEach void clearTags() { BuiltInRegistries.ITEM.bindTags(Map.of()); }

    private static ItemStack stone() { return new ItemStack(Items.EMERALD); }
    private static ItemStack bag(BigInteger quantity) {
        var bag = new ItemStack(Items.RABBIT_HIDE);
        SpiritStoneStorage.insert(bag, stone(), quantity, false);
        return bag;
    }

    private static final class Inventory {
        final List<ItemStack> slots;
        int changes;
        final List<ItemStack> drops = new ArrayList<>();
        Inventory(ItemStack... values) { slots = new ArrayList<>(List.of(values)); }
        SpiritStoneSources.Access open() {
            var sources = new ArrayList<SpiritStoneSources.Slot>();
            for (int i = 0; i < slots.size(); i++) {
                int at = i;
                sources.add(new SpiritStoneSources.Slot("test:" + at, () -> slots.get(at), value -> slots.set(at, value)));
            }
            return new SpiritStoneSources.Access(sources, () -> changes++);
        }
        BigInteger balance() { return SpiritStonePayment.balance(open(), stone()); }
        void finish(SpiritStonePayment.Receipt receipt, boolean success) { receipt.finish(success, this::open, drops::add); }
    }

    @Test void quotesReadHugeBalancesWithoutMutationOrExpansion() {
        var huge = BigInteger.TEN.pow(100).add(BigInteger.valueOf(17));
        var inv = new Inventory(bag(huge), new ItemStack(Items.EMERALD, 3));
        var original = inv.slots.getFirst().copy();
        assertEquals(huge.add(BigInteger.valueOf(3)), inv.balance());
        assertEquals(huge.add(BigInteger.valueOf(3)), inv.balance());
        assertTrue(ItemStack.matches(original, inv.slots.getFirst()));
        assertEquals(1, SpiritStoneStorage.entries(inv.slots.getFirst()).size());
        assertEquals(0, inv.changes);
    }

    @Test void pausedMultipleBagsAndLooseStonesCanPayOneFee() {
        var paused = bag(BigInteger.TWO);
        SpiritStoneStorage.autoCollect(paused, false);
        var inv = new Inventory(paused, new ItemStack(Items.EMERALD, 3), bag(BigInteger.valueOf(5)));
        var receipt = SpiritStonePayment.debit(inv.open(), stone(), 9).orElseThrow();
        assertEquals(BigInteger.ONE, inv.balance());
        assertFalse(SpiritStoneStorage.autoCollect(inv.slots.getFirst()));
        inv.finish(receipt, true);
        inv.finish(receipt, false);
        assertEquals(BigInteger.ONE, inv.balance());
        assertTrue(inv.drops.isEmpty());
    }

    @Test void shortageAndStaleInventoryNeverPartiallyDebit() {
        var inv = new Inventory(bag(BigInteger.valueOf(8)));
        assertTrue(SpiritStonePayment.debit(inv.open(), stone(), 9).isEmpty());
        assertEquals(BigInteger.valueOf(8), inv.balance());
        var stale = inv.open();
        inv.slots.set(0, bag(BigInteger.valueOf(11)));
        assertTrue(SpiritStonePayment.debit(stale, stone(), 3).isEmpty());
        assertEquals(BigInteger.valueOf(11), inv.balance());
        assertEquals(0, inv.changes);
    }

    @Test void hugePaymentKeepsTheExactRemainder() {
        var huge = BigInteger.TEN.pow(100).add(BigInteger.valueOf(17));
        var inv = new Inventory(bag(huge));
        var receipt = SpiritStonePayment.debit(inv.open(), stone(), Integer.MAX_VALUE).orElseThrow();
        assertEquals(huge.subtract(BigInteger.valueOf(Integer.MAX_VALUE)), inv.balance());
        inv.finish(receipt, false);
        assertEquals(huge, inv.balance());
        assertEquals(1, SpiritStoneStorage.entries(inv.slots.getFirst()).size());
        assertTrue(inv.drops.isEmpty());
    }

    @Test void exactItemAndComponentsPreventGradeConversionAndNamedCurrencySpending() {
        var named = stone();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("留作纪念"));
        var storage = bag(BigInteger.ZERO);
        SpiritStoneStorage.insert(storage, named, 7, false);
        SpiritStoneStorage.insert(storage, new ItemStack(Items.DIAMOND), 3, false);
        SpiritStoneStorage.insert(storage, new ItemStack(Items.QUARTZ), 9, false);
        var inv = new Inventory(storage);
        assertEquals(BigInteger.ZERO, inv.balance());
        assertTrue(SpiritStonePayment.debit(inv.open(), stone(), 1).isEmpty());
        var namedReceipt = SpiritStonePayment.debit(inv.open(), named, 2).orElseThrow();
        assertEquals(BigInteger.valueOf(5), SpiritStonePayment.balance(inv.open(), named));
        inv.finish(namedReceipt, false);
        assertEquals(BigInteger.valueOf(7), SpiritStonePayment.balance(inv.open(), named));
        assertEquals(BigInteger.valueOf(3), SpiritStonePayment.balance(inv.open(), new ItemStack(Items.DIAMOND)));
    }

    @Test void failureRestoresExactSlotsAndRepeatedCompletionCannotMintMoney() {
        var storage = bag(BigInteger.valueOf(7));
        SpiritStoneStorage.autoCollect(storage, false);
        var inv = new Inventory(storage, new ItemStack(Items.EMERALD, 3));
        var original = inv.slots.stream().map(ItemStack::copy).toList();
        var receipt = SpiritStonePayment.debit(inv.open(), stone(), 9).orElseThrow();
        inv.finish(receipt, false);
        inv.finish(receipt, false);
        inv.finish(receipt, true);
        for (int i = 0; i < original.size(); i++) assertTrue(ItemStack.matches(original.get(i), inv.slots.get(i)));
        assertEquals(BigInteger.TEN, inv.balance());
        assertTrue(inv.drops.isEmpty());
    }

    @Test void refundPreservesAnUnrelatedChangeMadeDuringTeleport() {
        var inv = new Inventory(bag(BigInteger.TEN), new ItemStack(Items.IRON_INGOT, 4));
        var receipt = SpiritStonePayment.debit(inv.open(), stone(), 3).orElseThrow();
        inv.slots.set(1, new ItemStack(Items.GOLD_INGOT, 2));
        inv.finish(receipt, false);
        assertEquals(BigInteger.TEN, inv.balance());
        assertTrue(inv.slots.get(1).is(Items.GOLD_INGOT));
        assertEquals(2, inv.slots.get(1).getCount());
    }

    @Test void aMovedBagIsCreditedAtItsCurrentPosition() {
        var inv = new Inventory(bag(BigInteger.TEN), ItemStack.EMPTY);
        var receipt = SpiritStonePayment.debit(inv.open(), stone(), 4).orElseThrow();
        inv.slots.set(1, inv.slots.getFirst());
        inv.slots.set(0, new ItemStack(Items.STONE, 64));
        inv.finish(receipt, false);
        assertEquals(BigInteger.TEN, inv.balance());
        assertTrue(inv.slots.getFirst().is(Items.STONE));
        assertTrue(SpiritStoneStorage.isBag(inv.slots.get(1)));
    }

    @Test void fullInventoryRefundDropsOnlyTheDebitedCurrencyOnce() {
        var inv = new Inventory(new ItemStack(Items.EMERALD, 2));
        var receipt = SpiritStonePayment.debit(inv.open(), stone(), 2).orElseThrow();
        inv.slots.set(0, new ItemStack(Items.STONE, 64));
        inv.finish(receipt, false);
        inv.finish(receipt, false);
        assertEquals(1, inv.drops.size());
        assertTrue(ItemStack.matches(new ItemStack(Items.EMERALD, 2), inv.drops.getFirst()));
        assertEquals(64, inv.slots.getFirst().getCount());
    }

    @Test void lostTagEligibilityDoesNotEraseStoredItems() {
        var inv = new Inventory(bag(BigInteger.TEN));
        clearTags();
        assertEquals(BigInteger.ZERO, inv.balance());
        assertTrue(SpiritStonePayment.debit(inv.open(), stone(), 1).isEmpty());
        assertEquals(BigInteger.TEN, SpiritStoneStorage.entries(inv.slots.getFirst()).getFirst().quantity());
    }

    @Test void invalidPaymentNeverChangesInventory() {
        var inv = new Inventory(bag(BigInteger.TEN));
        assertThrows(IllegalArgumentException.class, () -> SpiritStonePayment.debit(inv.open(), stone(), 0));
        assertThrows(IllegalArgumentException.class, () -> SpiritStonePayment.debit(inv.open(), stone(), -1));
        assertEquals(BigInteger.TEN, inv.balance());
        assertEquals(0, inv.changes);
    }

    @Test void nativeNestedContainersAndLedgerBagsCanPayTogetherWithoutMutatingTheQuote() {
        var bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new net.minecraft.world.item.component.BundleContents(List.of(new ItemStack(Items.EMERALD, 3))));
        var shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(
                List.of(bundle, bag(BigInteger.valueOf(7)))));
        var inv = new Inventory(shulker, new ItemStack(Items.EMERALD, 2));
        var original = shulker.copy();
        assertEquals(BigInteger.valueOf(12), inv.balance());
        assertTrue(ItemStack.matches(original, inv.slots.getFirst()));
        var receipt = SpiritStonePayment.debit(inv.open(), stone(), 11).orElseThrow();
        assertEquals(BigInteger.ONE, inv.balance());
        inv.finish(receipt, false);
        assertEquals(BigInteger.valueOf(12), inv.balance());
        assertTrue(ItemStack.matches(original, inv.slots.getFirst()));
    }

    @Test void anOffhandSlotCanPayAndRefundInPlace() {
        var inv = new Inventory();
        for (int i = 0; i < 40; i++) inv.slots.add(ItemStack.EMPTY);
        inv.slots.add(new ItemStack(Items.EMERALD, 3));
        var receipt = SpiritStonePayment.debit(inv.open(), stone(), 2).orElseThrow();
        assertEquals(1, inv.slots.get(40).getCount());
        inv.finish(receipt, false);
        assertEquals(3, inv.slots.get(40).getCount());
    }
}
