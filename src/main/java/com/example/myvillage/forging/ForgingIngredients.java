package com.example.myvillage.forging;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.Tags;
import java.util.List;

/** Costs retain concrete IDs and add #namespace:tag/path references, resolved against live tags. */
public final class ForgingIngredients {
    private ForgingIngredients(){}
    public static String validate(String reference){
        String id=reference.startsWith("#")?reference.substring(1):reference;
        if(!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||id.equals("minecraft:air"))
            throw new IllegalArgumentException("Invalid forging ingredient: "+reference);
        return reference;
    }
    private static ResourceLocation key(String reference){return ResourceLocation.parse(reference.startsWith("#")?reference.substring(1):reference);}
    public static boolean matches(String reference,ItemStack stack){
        if(stack.isEmpty())return false;
        var id=key(reference);
        return reference.startsWith("#")?stack.is(TagKey.create(Registries.ITEM,id)):
                BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }
    public static boolean infusionInput(ItemStack stack){return !stack.isEmpty()&&stack.is(Tags.Items.INGOTS_COPPER);}
    public static ItemStack display(String reference,List<ItemStack> inventory){
        for(var stack:new com.example.myvillage.spiritstone.SpiritStoneStorage.Expanded(inventory).entries())
            if(matches(reference,stack))return new ItemStack(stack.getItem());
        var id=key(reference);
        if(!reference.startsWith("#"))return new ItemStack(BuiltInRegistries.ITEM.get(id));
        var members=BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM,id));
        return members.isPresent()&&members.get().size()>0?new ItemStack(members.get().get(0).value()):new ItemStack(Items.BARRIER);
    }
    public static java.math.BigInteger available(String reference,List<ItemStack> inventory){
        return inventory.stream().map(stack->com.example.myvillage.spiritstone.SpiritStoneStorage.count(
                stack,stone->matches(reference,stone))).reduce(java.math.BigInteger.ZERO,java.math.BigInteger::add);
    }
    public static Component description(String reference){
        var id=key(reference);
        return reference.startsWith("#")?Component.translatableWithFallback(
                "tag.item."+id.getNamespace()+"."+id.getPath().replace('/','.'),reference):BuiltInRegistries.ITEM.get(id).getDescription();
    }
}
