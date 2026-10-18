package com.example.myvillage.sim.runtime.commerce;

import com.example.myvillage.sim.commerce.CommerceRules;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Copy-plan-commit: a full backpack can never consume money or lose a sold item. */
public final class CommerceInventory {
    public record Plan(String result,List<ItemStack> items) { public boolean ready(){return result.equals("ok");} }
    private CommerceInventory() { }
    public static Item item(String id) {
        ResourceLocation key=ResourceLocation.parse(id);
        if(id.equals("minecraft:air")||!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalArgumentException("Unknown commerce item "+id);
        return BuiltInRegistries.ITEM.get(key);
    }
    public static Plan plan(List<ItemStack> original,String currency,CommerceRules.Offer offer) {
        List<ItemStack> copies=original.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        ItemStack money=new ItemStack(item(currency));
        if(offer.kind().equals("sell")) {
            if(!remove(copies,new ItemStack(item(offer.item())),offer.quantity()))return new Plan("missing_items",List.of());
            if(!insert(copies,money,offer.price()))return new Plan("inventory_full",List.of());
        } else {
            if(!remove(copies,money,offer.price()))return new Plan("insufficient_funds",List.of());
            if(offer.kind().equals("buy")&&!insert(copies,new ItemStack(item(offer.item())),offer.quantity()))
                return new Plan("inventory_full",List.of());
        }
        return new Plan("ok",List.copyOf(copies));
    }
    private static boolean remove(List<ItemStack> inventory,ItemStack type,int count) {
        for(int i=0;i<inventory.size()&&count>0;i++) {
            var s=inventory.get(i);
            // Only ordinary commodity stacks. Named, enchanted, high-quality or otherwise
            // modified goods are never silently downgraded into the plain warehouse.
            if(!s.isEmpty()&&ItemStack.isSameItemSameComponents(s,type)) {
                int take=Math.min(count,s.getCount());s.shrink(take);count-=take;
                if(s.isEmpty())inventory.set(i,ItemStack.EMPTY);
            }
        }
        return count==0;
    }
    private static boolean insert(List<ItemStack> inventory,ItemStack type,int count) {
        for(var s:inventory) if(!s.isEmpty()&&ItemStack.isSameItemSameComponents(s,type)) {
            int add=Math.min(count,Math.max(0,Math.min(64,s.getMaxStackSize())-s.getCount()));
            s.grow(add);count-=add;if(count==0)return true;
        }
        for(int i=0;i<inventory.size()&&count>0;i++) if(inventory.get(i).isEmpty()) {
            int add=Math.min(count,Math.min(64,type.getMaxStackSize()));
            inventory.set(i,type.copyWithCount(add));count-=add;
        }
        return count==0;
    }
}
