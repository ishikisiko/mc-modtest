package com.example.myvillage.forging;

import com.example.myvillage.item.SpiritStoneTags;
import com.example.myvillage.spiritstone.SpiritStoneLedger;
import com.example.myvillage.spiritstone.SpiritStoneStorage;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForgingCostsTest {
    @BeforeAll static void bootstrap() {
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    @BeforeEach void tags() {
        BuiltInRegistries.ITEM.bindTags(Map.of(SpiritStoneStorage.BAGS,List.of(Items.BUNDLE.builtInRegistryHolder()),
                SpiritStoneTags.LOW_GRADE_SPIRIT_STONE,List.of(Items.EMERALD.builtInRegistryHolder(),Items.AMETHYST_SHARD.builtInRegistryHolder())));
    }
    @AfterEach void clear() {
        BuiltInRegistries.ITEM.bindTags(Map.of(SpiritStoneStorage.BAGS,List.of(),SpiritStoneTags.LOW_GRADE_SPIRIT_STONE,List.of()));
    }
    private static void sameInventory(List<ItemStack> expected,List<ItemStack> actual) {
        assertEquals(expected.size(),actual.size());
        for(int i=0;i<expected.size();i++)assertTrue(ItemStack.matches(expected.get(i),actual.get(i)),"slot "+i);
    }

    @Test void bagProjectionReservesExactComponentsAndSplitsWithdrawalsAtMaxStackSize() {
        var named=new ItemStack(Items.EMERALD);named.set(DataComponents.CUSTOM_NAME,Component.literal("祖传灵石"));
        named.set(DataComponents.MAX_STACK_SIZE,16);
        var amount=BigInteger.TEN.pow(60);var bag=new ItemStack(Items.BUNDLE);
        SpiritStoneStorage.entries(bag,List.of(new SpiritStoneLedger.Entry(named,amount),
                new SpiritStoneLedger.Entry(new ItemStack(Items.AMETHYST_SHARD),BigInteger.valueOf(4))));
        var iron=new ItemStack(Items.IRON_INGOT,3);iron.set(DataComponents.CUSTOM_NAME,Component.literal("原料批次"));
        var inventory=List.of(bag,iron,ItemStack.EMPTY);var before=inventory.stream().map(ItemStack::copy).toList();
        var costs=Map.of("minecraft:emerald",35,"minecraft:iron_ingot",2);
        var reservation=ForgingCosts.reserve(inventory,costs).orElseThrow();
        sameInventory(before,inventory);sameInventory(ForgingCosts.plan(inventory,costs).orElseThrow(),reservation.remaining());
        assertEquals(3,reservation.remaining().size());assertTrue(reservation.remaining().get(2).isEmpty());
        var remaining=SpiritStoneStorage.entries(reservation.remaining().getFirst());
        assertEquals(amount.subtract(BigInteger.valueOf(35)),remaining.getFirst().quantity());
        assertTrue(ItemStack.isSameItemSameComponents(named,remaining.getFirst().item()));
        assertEquals(BigInteger.valueOf(4),remaining.get(1).quantity());
        var consumed=reservation.consumed();assertEquals(List.of(16,16,3,2),consumed.stream().map(ItemStack::getCount).toList());
        for(int i=0;i<3;i++)assertTrue(ItemStack.isSameItemSameComponents(named,consumed.get(i)));
        assertTrue(ItemStack.isSameItemSameComponents(iron,consumed.getLast()));
        assertEquals(1,reservation.remaining().get(1).getCount());
        for(var stack:consumed)assertTrue(stack.getCount()>0&&stack.getCount()<=stack.getMaxStackSize());
        consumed.getFirst().remove(DataComponents.CUSTOM_NAME);
        reservation.remaining().get(1).setCount(64);
        assertTrue(SpiritStoneStorage.entries(bag).getFirst().item().has(DataComponents.CUSTOM_NAME));
        sameInventory(before,inventory);
    }

    @Test void distinctBagAndLooseIdentitiesAreReturnedAsTheActualWithdrawnMaterials() {
        var first=new ItemStack(Items.EMERALD,2);first.set(DataComponents.CUSTOM_NAME,Component.literal("甲"));
        var second=new ItemStack(Items.EMERALD,4);second.set(DataComponents.CUSTOM_NAME,Component.literal("乙"));
        var bag=new ItemStack(Items.BUNDLE);SpiritStoneStorage.contents(bag,List.of(first,second));
        var loose=new ItemStack(Items.EMERALD,3);loose.set(DataComponents.CUSTOM_NAME,Component.literal("丙"));
        var inventory=List.of(bag,loose);var before=inventory.stream().map(ItemStack::copy).toList();
        var reservation=ForgingCosts.reserve(inventory,Map.of("minecraft:emerald",8)).orElseThrow();
        var consumed=reservation.consumed();assertEquals(List.of(2,4,2),consumed.stream().map(ItemStack::getCount).toList());
        assertTrue(ItemStack.isSameItemSameComponents(first,consumed.get(0)));
        assertTrue(ItemStack.isSameItemSameComponents(second,consumed.get(1)));
        assertTrue(ItemStack.isSameItemSameComponents(loose,consumed.get(2)));
        assertTrue(SpiritStoneStorage.entries(reservation.remaining().getFirst()).isEmpty());
        assertEquals(1,reservation.remaining().get(1).getCount());sameInventory(before,inventory);
    }

    @Test void legacyBagMigrationOccursOnlyOnTheSuccessfulReturnedCopy() {
        var named=new ItemStack(Items.AMETHYST_SHARD,5);named.set(DataComponents.CUSTOM_NAME,Component.literal("旧袋灵石"));
        var bag=new ItemStack(Items.BUNDLE);
        bag.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(named)));
        var before=bag.copy();
        var reservation=ForgingCosts.reserve(List.of(bag),Map.of("minecraft:amethyst_shard",3)).orElseThrow();
        assertTrue(ItemStack.matches(before,bag));assertTrue(bag.has(DataComponents.CONTAINER));
        assertFalse(reservation.remaining().getFirst().has(DataComponents.CONTAINER));
        assertEquals(BigInteger.valueOf(2),SpiritStoneStorage.entries(reservation.remaining().getFirst()).getFirst().quantity());
        assertEquals(1,reservation.consumed().size());assertEquals(3,reservation.consumed().getFirst().getCount());
        assertTrue(ItemStack.isSameItemSameComponents(named,reservation.consumed().getFirst()));
    }

    @Test void shortageReturnsNoReservationAndLeavesBagAndLooseStacksUntouched() {
        var bag=new ItemStack(Items.BUNDLE);SpiritStoneStorage.contents(bag,List.of(new ItemStack(Items.EMERALD,2)));
        var inventory=List.of(bag,new ItemStack(Items.IRON_INGOT,4));var before=inventory.stream().map(ItemStack::copy).toList();
        assertTrue(ForgingCosts.reserve(inventory,Map.of("minecraft:emerald",3,"minecraft:iron_ingot",2)).isEmpty());
        assertTrue(ForgingCosts.reserve(inventory,Map.of("minecraft:emerald",2,"minecraft:quartz",1)).isEmpty());
        sameInventory(before,inventory);
    }

    @Test void emptyCostsKeepTheOriginalSlotLayoutAndReserveNothing() {
        var inventory=List.of(new ItemStack(Items.IRON_INGOT,4),ItemStack.EMPTY);
        var reservation=ForgingCosts.reserve(inventory,Map.of()).orElseThrow();
        sameInventory(inventory,reservation.remaining());assertTrue(reservation.consumed().isEmpty());
        reservation.remaining().getFirst().setCount(1);assertEquals(4,inventory.getFirst().getCount());
    }
}
