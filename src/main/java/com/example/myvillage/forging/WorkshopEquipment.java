package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import java.util.*;

/** Idempotent equipment effects. Vanilla enchantments, names and damage remain on the same item. */
public final class WorkshopEquipment {
    private static final ResourceLocation ATTACK=ResourceLocation.fromNamespaceAndPath("myvillage","tempered_edge");
    private WorkshopEquipment(){}
    public static Optional<WorkshopRules.Craft> craft(ItemStack stack){
        if(stack.isEmpty()||!stack.isDamageableItem())return Optional.empty();
        return WorkshopRules.get().forOutput(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
    public static TemperRecord record(ItemStack stack){return stack.getOrDefault(ModDataComponents.TEMPER.get(),TemperRecord.EMPTY);}
    /** The final assembly commits quality before any spirit affinity is selected. */
    public static ItemStack complete(Workpiece piece,int quality){
        if(piece==null||piece.stage()!=4||piece.points()!=WorkshopRules.get().formingPoints(piece.recipe()))throw new IllegalArgumentException("Unfinished workpiece");
        ForgeMark.requireQuality(quality);
        var recipe=ForgingRules.get().recipe(piece.recipe()).orElseThrow();
        var result=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(recipe.output())));
        result.set(ModDataComponents.FORGE_MARK.get(),ForgeMark.uninfused(quality));
        result.set(ModDataComponents.TEMPER.get(),new TemperRecord(0,0,0,0,piece.treatment()));
        result.set(ModDataComponents.FORGE_CRAFT.get(),ForgeCraft.EMPTY);
        refresh(result);return result;
    }
    public static ItemStack complete(ItemStack input,int quality,String handle){
        if(!WorkshopItems.valid(input))throw new IllegalArgumentException("Invalid completed blank");
        var result=complete(input.get(ModDataComponents.WORKPIECE.get()),quality);
        var record=FoundryEquipment.record(input).withHandle(handle);
        result.applyComponents(input.getComponentsPatch());result.remove(ModDataComponents.WORKPIECE.get());
        result.set(ModDataComponents.FORGE_MARK.get(),ForgeMark.uninfused(quality));
        result.set(ModDataComponents.FORGE_CRAFT.get(),record);refresh(result);return result;
    }
    public static double edgeTreatment(TemperRecord r){return r.treatment()==1?1:r.treatment()==0?0.5:0;}
    public static double toughTreatment(TemperRecord r){return r.treatment()==2?1:r.treatment()==0?0.5:0;}
    public static double speedBonus(TemperRecord record,WorkshopRules rules){
        return record.edge()*rules.speed_per_strike()+edgeTreatment(record)*rules.treatment_speed();
    }
    public static void refresh(ItemStack stack){
        var craft=craft(stack).orElseThrow();var r=record(stack);var rules=WorkshopRules.get();
        stack.set(DataComponents.MAX_DAMAGE,baseDurability(stack));
        if(craft.weapon()){
            var before=stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY);
            var entries=new ArrayList<>(before.modifiers());entries.removeIf(e->e.modifier().id().equals(ATTACK));
            double attack=r.edge()*rules.attack_per_strike()+edgeTreatment(r)*rules.treatment_attack();
            if(attack>0)entries.add(new ItemAttributeModifiers.Entry(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(ATTACK,attack,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND));
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS,new ItemAttributeModifiers(entries,before.showInTooltip()));
        }
        FoundryEquipment.refresh(stack);
    }
    public static int baseDurability(ItemStack stack){
        var r=record(stack);var rules=WorkshopRules.get();
        int base=stack.getItem().getDefaultInstance().getMaxDamage();
        var mark=stack.get(ModDataComponents.FORGE_MARK.get());
        double forged=mark!=null&&mark.trait().equals("tough")?ForgingRules.get().trait("tough").value(mark.quality()):1;
        double durability=1+r.endurance()*rules.durability_per_strike()+toughTreatment(r)*rules.treatment_durability();
        return (int)Math.ceil(base*forged*durability);
    }
    public static long qiCost(ItemStack stack,long cost){
        if(cost<=0)return cost;
        return FoundryEquipment.qiCost(stack,cost);
    }
    public static String amount(double n){return java.math.BigDecimal.valueOf(n).setScale(3,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
    public static void tooltip(ItemStack stack,List<Component> lines){
        var craft=craft(stack);if(craft.isEmpty())return;
        var r=record(stack);var rules=WorkshopRules.get();
        var work=FoundryEquipment.record(stack);var benefits=FoundryEquipment.benefits(stack);
        lines.add(Component.translatable("foundry.myvillage.bonus_detail",amount(benefits.attack()*100),amount(benefits.speed()*100),amount(benefits.durability()*100),amount(benefits.armor()*100),amount(-benefits.qiDiscount()*100)).withStyle(net.minecraft.ChatFormatting.AQUA));
        if(!craft.get().armor())lines.add(Component.translatable("foundry.myvillage.handle",FoundryEquipment.handle(stack).name()).withStyle(net.minecraft.ChatFormatting.GRAY));
        if(!work.fire().isEmpty())lines.add(Component.translatable("foundry.myvillage.fire",FoundryRules.get().flame(work.fire()).name(),work.fireMaturity()).withStyle(net.minecraft.ChatFormatting.AQUA));
        lines.add(Component.translatable("workshop.myvillage.experience",r.experience(),rules.available(r,craft.get())).withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("workshop.myvillage.tempering",r.strikes(),craft.get().max_strikes()).withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
        if(r.edge()>0)lines.add(Component.translatable("workshop.myvillage.bonus."+(craft.get().weapon()?"attack":"speed"),
                amount(r.edge()*(craft.get().weapon()?rules.attack_per_strike():rules.speed_per_strike()*100))).withStyle(net.minecraft.ChatFormatting.GRAY));
        if(r.endurance()>0)lines.add(Component.translatable("workshop.myvillage.bonus.durability",amount(r.endurance()*rules.durability_per_strike()*100)).withStyle(net.minecraft.ChatFormatting.GRAY));
        if(r.channeling()>0)lines.add(Component.translatable("workshop.myvillage.bonus.qi",amount(r.channeling()*rules.qi_discount_per_strike()*100)).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
