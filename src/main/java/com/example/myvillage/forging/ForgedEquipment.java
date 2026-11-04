package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Effects are derived once for new stacks; repairs preserve the whole component patch. */
public final class ForgedEquipment {
    private ForgedEquipment(){}
    public static boolean supported(ItemStack stack){
        return supported(stack, ForgingRules.get());
    }
    private static boolean supported(ItemStack stack,ForgingRules rules){
        return !stack.isEmpty() && stack.isDamageableItem() && rules.forOutput(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).isPresent();
    }
    public static void apply(ItemStack stack, ForgeMark mark){
        apply(stack,mark,ModDataComponents.FORGE_MARK.get(),ForgingRules.get());
    }
    static void apply(ItemStack stack,ForgeMark mark,net.minecraft.core.component.DataComponentType<ForgeMark> type,ForgingRules rules){
        var existing=stack.get(type);
        if(!supported(stack,rules)||!mark.infused()||existing!=null&&(existing.infused()||existing.quality()!=mark.quality()))
            throw new IllegalArgumentException("Infusion must preserve the completed item's quality");
        var recipe=rules.forOutput(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).orElseThrow();
        if(!recipe.traits().contains(mark.trait()))throw new IllegalArgumentException("Unsupported trait");
        double value=rules.trait(mark.trait()).value(mark.quality());
        if(mark.trait().equals("sharp")){
            var attributes=stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY);
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS,attributes.withModifierAdded(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(ResourceLocation.fromNamespaceAndPath("myvillage","forging_sharp"),value,
                            AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND));
        }else if(mark.trait().equals("tough")){
            stack.set(DataComponents.MAX_DAMAGE,(int)Math.ceil(stack.getMaxDamage()*value));
        }
        stack.set(type,mark);
    }
    public static boolean infused(ItemStack stack){
        var mark=stack.get(ModDataComponents.FORGE_MARK.get());return mark!=null&&mark.infused();
    }
    public static long qiCost(ItemStack stack,long original){
        var mark=stack.get(ModDataComponents.FORGE_MARK.get());
        return FoundryEquipment.qiCost(stack,original);
    }
    static long qiCost(long original,ForgeMark mark,ForgingRules rules){
        if(original<0)throw new IllegalArgumentException("Negative qi cost");
        if(original==0 || mark==null || !mark.trait().equals("channeling"))return original;
        return Math.max(1,(long)Math.floor(original*(1-rules.trait("channeling").value(mark.quality()))));
    }
    public static int repairAmount(ItemStack stack){
        return repairAmount(stack,stack.get(ModDataComponents.FORGE_MARK.get()),ForgingRules.get());
    }
    static int repairAmount(ItemStack stack,ForgeMark mark,ForgingRules rules){
        double multiplier=mark!=null && mark.trait().equals("tough")?rules.trait("tough").value(mark.quality()):1;
        return Math.max(1,(int)Math.ceil(stack.getMaxDamage()*rules.repairFraction()*multiplier));
    }
}
