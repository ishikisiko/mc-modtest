package com.example.myvillage.sim.runtime.commerce;

import com.example.myvillage.sim.commerce.CommerceRules;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import com.example.myvillage.alchemy.PillQuality;
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
        return new CommerceRules.Offer("iron","smith",kind,"minecraft:iron_ingot",quantity,price,"items",quantity,4,64,0,0,0,0);
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
        var rest=new CommerceRules.Offer("inn_rest","innkeeper","rest","minecraft:air",0,5,"service",0,0,1,0,0,0,0);
        var bag=List.of(new ItemStack(Items.EMERALD,9));var plan=CommerceInventory.plan(bag,"minecraft:emerald",rest);
        assertTrue(plan.ready());assertEquals(4,plan.items().get(0).getCount());assertEquals(9,bag.get(0).getCount());
        assertThrows(IllegalArgumentException.class,()->CommerceInventory.item("myvillage:unknown_test_item"));
        assertThrows(IllegalArgumentException.class,()->CommerceInventory.item("minecraft:air"));
    }
    @Test void bulkPurchaseAndSaleSpanStacksWithoutMutatingInputs() {
        var bag=List.of(new ItemStack(Items.EMERALD,64),new ItemStack(Items.EMERALD,64),
                new ItemStack(Items.IRON_INGOT,60),ItemStack.EMPTY,ItemStack.EMPTY);
        var bought=CommerceInventory.plan(bag,"minecraft:emerald",offer("buy",2,3),20);
        assertTrue(bought.ready());
        assertEquals(100,bought.items().stream().filter(s->s.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum());
        assertEquals(68,bought.items().stream().filter(s->s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum());
        var sold=CommerceInventory.plan(bought.items(),"minecraft:emerald",offer("sell",2,2),35);
        assertTrue(sold.ready());
        assertEquals(30,sold.items().stream().filter(s->s.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum());
        assertEquals(138,sold.items().stream().filter(s->s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum());
        assertEquals(64,bag.getFirst().getCount());assertEquals(60,bag.get(2).getCount());
    }
    @Test void batchFailureNeverFallsBackToPartialTrade() {
        var bag=List.of(new ItemStack(Items.EMERALD,10),new ItemStack(Items.IRON_INGOT,63));
        assertEquals("insufficient_funds",CommerceInventory.plan(bag,"minecraft:emerald",offer("buy",1,3),4).result());
        assertEquals("inventory_full",CommerceInventory.plan(bag,"minecraft:emerald",offer("buy",1,3),2).result());
        assertEquals("missing_items",CommerceInventory.plan(bag,"minecraft:emerald",offer("sell",1,2),64).result());
        assertEquals(10,bag.getFirst().getCount());assertEquals(63,bag.get(1).getCount());
        var full=List.of(new ItemStack(Items.IRON_INGOT,64),new ItemStack(Items.EMERALD,63));
        assertEquals("inventory_full",CommerceInventory.plan(full,"minecraft:emerald",offer("sell",1,2),2).result());
        assertEquals(64,full.getFirst().getCount());assertEquals(63,full.get(1).getCount());
        var rest=new CommerceRules.Offer("inn_rest","innkeeper","rest","minecraft:air",0,5,"service",0,0,1,0,0,0,0);
        assertEquals("invalid_quantity",CommerceInventory.plan(bag,"minecraft:emerald",rest,2).result());
    }

    private static final DataComponentType<Integer> QUALITY=DataComponentType.<Integer>builder()
            .persistent(PillQuality.CODEC).networkSynchronized(PillQuality.STREAM_CODEC).build();
    private static CommerceRules.Offer pill(String kind,int quality,int price) {
        return new CommerceRules.Offer("pill","herbalist",kind,"minecraft:paper",1,price,"items",1,0,16,0,quality,0,0);
    }
    @Test void salePaysOnlyForTheQuotedQualityAndLeavesEveryOtherStackIntact() {
        for(int quality=1;quality<=4;quality++) {
            List<ItemStack> bag=new ArrayList<>();
            for(int q=1;q<=4;q++) {
                var stack=new ItemStack(Items.PAPER,3);if(q>1)stack.set(QUALITY,q);bag.add(stack);
            }
            bag.add(ItemStack.EMPTY);
            int price=List.of(6,9,12,18).get(quality-1);
            var plan=CommerceInventory.plan(bag,"minecraft:emerald",pill("sell",quality,price),2,bag.size(),QUALITY);
            assertTrue(plan.ready());assertEquals(2*price,plan.items().stream().filter(s->s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum());
            for(int q=1;q<=4;q++)assertEquals(q==quality?1:3,plan.items().get(q-1).getCount());
            for(var stack:bag.subList(0,4))assertEquals(3,stack.getCount());
        }
    }
    @Test void resaleCreatesTheSameQualityRatherThanAnOrdinaryPill() {
        var bag=List.of(new ItemStack(Items.EMERALD,64),ItemStack.EMPTY);
        for(int quality=1;quality<=4;quality++) {
            var plan=CommerceInventory.plan(bag,"minecraft:emerald",pill("buy",quality,24),2,bag.size(),QUALITY);
            assertTrue(plan.ready());var output=plan.items().stream().filter(s->s.is(Items.PAPER)).findFirst().orElseThrow();
            assertEquals(2,output.getCount());assertEquals(quality,output.getOrDefault(QUALITY,1));
            assertEquals(quality>1,output.has(QUALITY));assertEquals(64,bag.getFirst().getCount());
        }
    }
    @Test void explicitLowerIsAcceptedButRenamedWrongQualityAndInvalidQualityNeverDisappear() {
        var lower=new ItemStack(Items.PAPER,2);lower.set(QUALITY,1);
        assertTrue(CommerceInventory.plan(List.of(lower,ItemStack.EMPTY),"minecraft:emerald",pill("sell",1,6),1,2,QUALITY).ready());
        for(int quality:new int[]{0,2,3,4,5}) {
            var other=lower.copy();other.set(QUALITY,quality);
            assertEquals("missing_items",CommerceInventory.plan(List.of(other,ItemStack.EMPTY),"minecraft:emerald",pill("sell",1,6),1,2,QUALITY).result());
            assertEquals(2,other.getCount());
        }
        var named=new ItemStack(Items.PAPER,2);named.set(QUALITY,4);named.set(DataComponents.CUSTOM_NAME,Component.literal("留作纪念"));
        assertEquals("missing_items",CommerceInventory.plan(List.of(named,ItemStack.EMPTY),"minecraft:emerald",pill("sell",4,18),1,2,QUALITY).result());
        var full=List.of(new ItemStack(Items.PAPER,64),new ItemStack(Items.EMERALD,64));
        assertEquals("inventory_full",CommerceInventory.plan(full,"minecraft:emerald",pill("sell",1,6),1,2,QUALITY).result());
        assertEquals(64,full.getFirst().getCount());assertEquals(64,full.get(1).getCount());
    }
}
