package com.example.myvillage.sim.runtime.commerce;

import com.example.myvillage.sim.commerce.CommerceRules;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommerceInventoryTest {
    @BeforeAll static void bootstrap() {
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    private static CommerceRules.Offer offer(String kind,int quantity,int price) {
        return new CommerceRules.Offer("iron","smith",kind,"minecraft:iron_ingot",quantity,price,"items",quantity,4,64,0);
    }
    @Test void purchasePlansWholeReplacementWithoutChangingOriginalStacks() {
        var bag=List.of(new ItemStack(Items.EMERALD,5),new ItemStack(Items.EMERALD,8),new ItemStack(Items.IRON_INGOT,63),ItemStack.EMPTY);
        var plan=CommerceInventory.plan(bag,"minecraft:emerald",offer("buy",2,7));
        assertTrue(plan.ready());assertTrue(plan.items().get(0).is(Items.IRON_INGOT));assertEquals(1,plan.items().get(0).getCount());
        assertEquals(6,plan.items().get(1).getCount());assertEquals(64,plan.items().get(2).getCount());
        assertEquals(5,bag.get(0).getCount());assertEquals(8,bag.get(1).getCount());assertEquals(63,bag.get(2).getCount());
    }
    @Test void noSpaceOrFundsDoesNotDeductAnything() {
        var bag=List.of(new ItemStack(Items.EMERALD,64),new ItemStack(Items.STONE,64));
        assertEquals("inventory_full",CommerceInventory.plan(bag,"minecraft:emerald",offer("buy",1,2)).result());
        assertEquals("insufficient_funds",CommerceInventory.plan(bag,"minecraft:emerald",offer("buy",1,100)).result());
        assertEquals(64,bag.get(0).getCount());assertEquals(64,bag.get(1).getCount());
    }
    @Test void salesUseFreedSlotsAndDoNotLoseAnItemWhenProceedsCannotFit() {
        var bag=List.of(new ItemStack(Items.IRON_INGOT,1),new ItemStack(Items.STONE,64));
        var plan=CommerceInventory.plan(bag,"minecraft:emerald",offer("sell",1,64));
        assertTrue(plan.ready());assertTrue(plan.items().get(0).is(Items.EMERALD));assertEquals(64,plan.items().get(0).getCount());
        assertEquals("inventory_full",CommerceInventory.plan(bag,"minecraft:emerald",offer("sell",1,65)).result());
        assertTrue(bag.get(0).is(Items.IRON_INGOT));assertEquals(1,bag.get(0).getCount());
    }
    @Test void customizedGoodsAreNotDowngradedIntoOrdinaryWarehouseStock() {
        var named=new ItemStack(Items.IRON_INGOT,3);named.set(DataComponents.CUSTOM_NAME,Component.literal("Keep me"));
        assertEquals("missing_items",CommerceInventory.plan(List.of(named,ItemStack.EMPTY),"minecraft:emerald",offer("sell",1,2)).result());
        assertEquals(3,named.getCount());assertEquals("Keep me",named.getHoverName().getString());
    }
    @Test void paidRestOnlyRemovesCurrencyAndRejectsUnknownOrAirCurrency() {
        var rest=new CommerceRules.Offer("inn_rest","innkeeper","rest","minecraft:air",0,5,"service",0,0,1,0);
        var bag=List.of(new ItemStack(Items.EMERALD,9));var plan=CommerceInventory.plan(bag,"minecraft:emerald",rest);
        assertTrue(plan.ready());assertEquals(4,plan.items().get(0).getCount());assertEquals(9,bag.get(0).getCount());
        assertThrows(IllegalArgumentException.class,()->CommerceInventory.item("myvillage:unknown_test_item"));
        assertThrows(IllegalArgumentException.class,()->CommerceInventory.item("minecraft:air"));
    }
}
