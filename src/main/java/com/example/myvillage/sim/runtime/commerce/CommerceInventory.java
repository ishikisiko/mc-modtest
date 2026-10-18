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
        return plan(original,currency,offer,1);
    }
    public static Plan plan(List<ItemStack> original,String currency,CommerceRules.Offer offer,int quantity) {
        return plan(original,currency,offer,quantity,original.size());
    }
    public static Plan plan(List<ItemStack> original,String currency,CommerceRules.Offer offer,int quantity,int storageSlots) {
        if(storageSlots<0||storageSlots>original.size())throw new IllegalArgumentException("Inventory output slots");
        if(quantity<1||quantity>CommerceIntentPayload.MAX_QUANTITY||offer.kind().equals("rest")&&quantity!=1)
            return new Plan("invalid_quantity",List.of());
        int price=Math.multiplyExact(offer.price(),quantity),count=Math.multiplyExact(offer.quantity(),quantity);
        List<ItemStack> copies=original.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        ItemStack money=new ItemStack(item(currency));
        if(offer.kind().equals("sell")) {
            if(!remove(copies.subList(0,storageSlots),new ItemStack(item(offer.item())),count))return new Plan("missing_items",List.of());
            if(!insert(copies,money,price,storageSlots))return new Plan("inventory_full",List.of());
        } else {
            if(!com.example.myvillage.spiritstone.SpiritStoneStorage.remove(copies,com.example.myvillage.spiritstone.SpiritStoneStorage.ordinary(money),price))return new Plan("insufficient_funds",List.of());
            if(offer.kind().equals("buy")&&!insert(copies,new ItemStack(item(offer.item())),count,storageSlots))
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
    public static java.math.BigInteger balance(List<ItemStack> inventory,String currency) {
        ItemStack type=new ItemStack(item(currency));
        return inventory.stream().map(stack->com.example.myvillage.spiritstone.SpiritStoneStorage.count(
                stack,com.example.myvillage.spiritstone.SpiritStoneStorage.ordinary(type))).reduce(java.math.BigInteger.ZERO,java.math.BigInteger::add);
    }
    private static boolean insert(List<ItemStack> inventory,ItemStack type,int count,int storageSlots) {
        int remaining=com.example.myvillage.spiritstone.SpiritStoneStorage.insertBags(inventory,type,count,true);
        return com.example.myvillage.spiritstone.SpiritStoneStorage.insertLoose(inventory,type,remaining,storageSlots)==0;
    }
}
