package com.example.myvillage.storagebag;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StorageBagTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void fiveTiersGrowByTwoRowsAndKeepDistinctKinds() {
        var tiers = StorageBagRules.tiers();
        assertEquals(List.of(27, 45, 63, 81, 99), tiers.stream().map(StorageBagRules.Tier::capacity).toList());
        assertEquals(List.of(StorageBagRules.Station.DING, StorageBagRules.Station.DING, StorageBagRules.Station.ARRAY,
                StorageBagRules.Station.ARRAY, StorageBagRules.Station.ARRAY),
                tiers.stream().map(StorageBagRules.Tier::station).toList());
        assertEquals(15, tiers.stream()
                .flatMap(tier -> java.util.Arrays.stream(StorageBagRules.Kind.values()).map(tier::itemId))
                .distinct().count());
    }

    @Test
    void partialInsertMergesOnlyMatchingComponentsAndKeepsRemainder() {
        var marked = new ItemStack(Items.IRON_INGOT, 61);
        marked.set(DataComponents.CUSTOM_NAME, Component.literal("原样保留"));
        var plain = new ItemStack(Items.IRON_INGOT, 62);
        var contents = new ArrayList<>(List.of(marked.copy(), plain.copy(), new ItemStack(Items.STONE, 64)));
        assertEquals(5, StorageBagContents.insert(contents, plain, 7, ignored -> 64));
        assertTrue(ItemStack.matches(marked, contents.getFirst()));
        assertEquals(64, contents.get(1).getCount());
        assertEquals(62, plain.getCount());
    }

    @Test
    void unloadKeepsUnmatchedItemsAndPreservesNamedMaterials() {
        var chest = new SimpleContainer(2);
        chest.setItem(0, new ItemStack(Items.GOLD_INGOT, 62));
        var named = new ItemStack(Items.GOLD_INGOT, 3);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("矿样"));
        var copper = new ItemStack(Items.COPPER_INGOT, 9);
        var contents = new ArrayList<>(List.of(named.copy(), new ItemStack(Items.GOLD_INGOT, 5), copper.copy()));

        assertEquals(5, StorageBagChest.transfer(contents, chest));
        assertEquals(64, chest.getItem(0).getCount());
        assertTrue(ItemStack.matches(named, chest.getItem(1)));
        assertTrue(contents.getFirst().isEmpty());
        assertEquals(3, contents.get(1).getCount());
        assertTrue(ItemStack.matches(copper, contents.get(2)));
        assertEquals(0, StorageBagChest.transfer(contents, chest));
    }

    @Test
    void slotAndPickupSettingsSurviveTransmutationAndUseDetachedCopies() {
        var old = new ItemStack(Items.PAPER);
        old.set(DataComponents.CUSTOM_NAME, Component.literal("自用袋"));
        var contents = StorageBagContents.read(old, 27);
        contents.set(26, new ItemStack(Items.AMETHYST_SHARD, 7));
        StorageBagContents.write(old, contents);
        StorageBagContents.setAutoPickup(old, false);
        contents.get(26).grow(9);

        var upgraded = old.transmuteCopy(Items.BOOK);
        var read = StorageBagContents.read(upgraded, 45);
        assertEquals(1, StorageBagContents.occupied(upgraded));
        assertEquals(7, read.get(26).getCount());
        assertTrue(read.get(44).isEmpty());
        assertFalse(StorageBagContents.autoPickup(upgraded));
        assertEquals("自用袋", upgraded.getHoverName().getString());
        assertFalse(StorageBagContents.accepts(StorageBagRules.Kind.GENERAL, upgraded));
        assertFalse(StorageBagContents.accepts(StorageBagRules.Kind.MANUAL, new ItemStack(Items.BOOK)));
    }
}
