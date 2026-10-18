package com.example.myvillage.cultivation.meditation;

import com.example.myvillage.alchemy.AlchemyAutofill;
import com.example.myvillage.alchemy.AlchemyRules;
import com.example.myvillage.forging.ForgingCosts;
import com.example.myvillage.item.SpiritStoneTags;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Disposable datapack aliases exercise Minecraft's live tag membership, never item-name heuristics. */
class SpiritStoneCompatibilityTest {
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    }
    @BeforeEach void bindAliases() {
        BuiltInRegistries.ITEM.bindTags(Map.of(SpiritStoneTags.LOW_GRADE_SPIRIT_STONE,
                List.of(Items.EMERALD.builtInRegistryHolder(), Items.AMETHYST_SHARD.builtInRegistryHolder())));
    }
    @AfterEach void clearAliases() {
        BuiltInRegistries.ITEM.bindTags(Map.of(SpiritStoneTags.LOW_GRADE_SPIRIT_STONE, List.of()));
    }
    @Test void mixedStonesPayTogetherAndRollbackRestoresOriginalComponents() {
        Inventory inventory = new Inventory(null);
        ItemStack named = new ItemStack(Items.EMERALD, 2);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("External stone"));
        inventory.setItem(0, named.copy());
        inventory.setItem(1, new ItemStack(Items.AMETHYST_SHARD, 3));
        inventory.setItem(2, new ItemStack(Items.DIAMOND, 64));
        assertTrue(InventoryBatchRemoval.has(inventory, SpiritStoneTags.LOW_GRADE_SPIRIT_STONE, 5));
        assertFalse(InventoryBatchRemoval.has(inventory, SpiritStoneTags.LOW_GRADE_SPIRIT_STONE, 6));
        assertTrue(InventoryBatchRemoval.remove(inventory, SpiritStoneTags.LOW_GRADE_SPIRIT_STONE, 6).isEmpty());
        assertTrue(ItemStack.matches(named, inventory.getItem(0)));
        var removed = InventoryBatchRemoval.remove(inventory, SpiritStoneTags.LOW_GRADE_SPIRIT_STONE, 4).orElseThrow();
        assertEquals(4, removed.count()); assertTrue(inventory.getItem(0).isEmpty());
        assertEquals(1, inventory.getItem(1).getCount()); assertEquals(64, inventory.getItem(2).getCount());
        InventoryBatchRemoval.restore(inventory, removed);
        assertTrue(ItemStack.matches(named, inventory.getItem(0)));
        assertTrue(inventory.getItem(1).is(Items.AMETHYST_SHARD)); assertEquals(3, inventory.getItem(1).getCount());
    }
    @Test void reloadedTagsImmediatelyChangeEligibility() {
        ItemStack emerald = new ItemStack(Items.EMERALD);
        assertTrue(SpiritStoneTags.isLowGrade(emerald));
        assertFalse(SpiritStoneTags.isLowGrade(new ItemStack(Items.DIAMOND)));
        clearAliases();
        assertFalse(SpiritStoneTags.isLowGrade(emerald));
        assertTrue(ForgingCosts.plan(List.of(emerald), Map.of(SpiritStoneTags.LOW_GRADE_REFERENCE, 1)).isEmpty());
    }
    @Test void forgingConsumesMixedAliasesOnlyAfterCompletePlanning() {
        var inventory = List.of(new ItemStack(Items.EMERALD), new ItemStack(Items.AMETHYST_SHARD, 2));
        assertTrue(ForgingCosts.plan(inventory, Map.of(SpiritStoneTags.LOW_GRADE_REFERENCE, 4)).isEmpty());
        var plan = ForgingCosts.plan(inventory, Map.of(SpiritStoneTags.LOW_GRADE_REFERENCE, 3)).orElseThrow();
        assertTrue(plan.stream().allMatch(ItemStack::isEmpty));
        assertEquals(1, inventory.get(0).getCount()); assertEquals(2, inventory.get(1).getCount());
    }
    @Test void furnaceRefillRespectsExternalStackLimits() {
        ItemStack limited = new ItemStack(Items.EMERALD, 2);
        limited.set(DataComponents.MAX_STACK_SIZE, 1);
        var plan = AlchemyAutofill.plan(List.of(limited), List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY),
                List.of("minecraft:carrot", "minecraft:brown_mushroom", AlchemyRules.FUEL), List.of(1, 1, 2)).orElseThrow();
        assertEquals(1, plan.inputs().get(2).getCount());
        assertEquals(1, plan.inventory().get(0).getCount());
        assertEquals(2, limited.getCount());
    }
    @Test void furnaceRefillAcceptsAliasesWithoutMergingDifferentItems() {
        var wanted = List.of("minecraft:carrot", "minecraft:brown_mushroom", AlchemyRules.FUEL);
        var inventory = List.of(new ItemStack(Items.EMERALD, 3), new ItemStack(Items.AMETHYST_SHARD, 3));
        var inputs = List.of(ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.AMETHYST_SHARD));
        var plan = AlchemyAutofill.plan(inventory, inputs, wanted, List.of(1, 1, 2)).orElseThrow();
        assertTrue(plan.inputs().get(2).is(Items.AMETHYST_SHARD)); assertEquals(2, plan.inputs().get(2).getCount());
        assertEquals(3, plan.inventory().get(0).getCount()); assertEquals(2, plan.inventory().get(1).getCount());
        assertEquals(1, inputs.get(2).getCount());
        var empty = AlchemyAutofill.plan(inventory, List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY),
                wanted, List.of(1, 1, 2)).orElseThrow();
        assertTrue(empty.inputs().get(2).is(Items.EMERALD)); assertEquals(2, empty.inputs().get(2).getCount());
    }
}
