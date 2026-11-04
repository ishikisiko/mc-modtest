package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import java.util.*;

/** Recomputes only this system's stable modifiers, so assembly/repair never compounds bonuses. */
public final class FoundryEquipment {
    private static final Set<String> MODIFIERS=Set.of("foundry_attack","foundry_speed","foundry_armor_helmet","foundry_armor_chestplate",
            "foundry_armor_leggings","foundry_armor_boots","foundry_move","foundry_knockback");
    private FoundryEquipment(){}
    public record Benefits(double attack,double speed,double durability,double armor,double qiDiscount){}
    public static ForgeCraft record(ItemStack stack){return stack.getOrDefault(ModDataComponents.FORGE_CRAFT.get(),ForgeCraft.EMPTY);}
    public static boolean valid(ItemStack stack){
        var craft=WorkshopEquipment.craft(stack);
        if(craft.isEmpty())return false;
        var r=record(stack);
        try{FoundryRules.get().handle(r.handle());if(!r.fire().isEmpty())FoundryRules.get().flame(r.fire());}
        catch(RuntimeException e){return false;}
        return r.manualPoints()<=craft.get().forming_points()&&r.fireMaturity()<=FoundryRules.get().fire().max_maturity();
    }
    public static double affinity(ItemStack stack,FoundryRules.Fire fire){
        var craft=WorkshopEquipment.craft(stack).orElseThrow();
        var recipe=ForgingRules.get().recipe(craft.recipe()).orElseThrow();
        var refs=new HashSet<>(recipe.costs().keySet());var mark=stack.get(ModDataComponents.FORGE_MARK.get());
        if(mark!=null&&mark.infused())refs.addAll(ForgingRules.get().trait(mark.trait()).costs().keySet());
        var configured=FoundryRules.get().affinity_items().getOrDefault(fire.affinity(),List.of());
        return fire.affinity().equals("universal")||configured.stream().anyMatch(refs::contains)?FoundryRules.get().fire().affinity_bonus():0;
    }
    public static double fireStrength(ItemStack stack){
        var record=record(stack);if(record.fire().isEmpty())return 0;
        var rules=FoundryRules.get();var fire=rules.flame(record.fire());
        double mature=record.fireMaturity()/(double)rules.fire().max_maturity();
        double strength=fire.strength()*((1-rules.fire().maturity_scale())+mature*rules.fire().maturity_scale())+affinity(stack,fire);
        if(fire.id().equals("thunder"))strength+=rules.fire().thunder_bonus();
        return Math.clamp(strength,0,1);
    }
    public static Benefits benefits(ItemStack stack){
        var craft=WorkshopEquipment.craft(stack);if(craft.isEmpty())return new Benefits(0,0,0,0,0);
        var rules=FoundryRules.get();var record=record(stack);
        double manual=record.manualStrength(craft.get().forming_points()),fire=fireStrength(stack);
        var m=rules.manual();var f=rules.fire_bonus();var cap=rules.caps();
        return new Benefits(Math.min(cap.attack(),manual*m.attack()+fire*f.attack()),Math.min(cap.speed(),manual*m.speed()+fire*f.speed()),
                Math.min(cap.durability(),manual*m.durability()+fire*f.durability()),Math.min(cap.armor(),manual*m.armor()+fire*f.armor()),
                Math.min(cap.qi_discount(),manual*m.qi_discount()+fire*f.qi_discount()));
    }
    public static FoundryRules.Handle handle(ItemStack stack){return FoundryRules.get().handle(record(stack).handle());}
    public static double speed(ItemStack stack){
        if(WorkshopEquipment.craft(stack).isEmpty()||!valid(stack))return 1;
        var craft=WorkshopEquipment.craft(stack).orElseThrow();
        double trained=craft.armor()||craft.weapon()?1:1+WorkshopEquipment.speedBonus(WorkshopEquipment.record(stack),WorkshopRules.get());
        return Math.min(1+FoundryRules.get().caps().speed(),trained*(1+benefits(stack).speed())*(craft.armor()?1:handle(stack).speed()));
    }
    public static void refresh(ItemStack stack){
        var craft=WorkshopEquipment.craft(stack);if(craft.isEmpty()||!valid(stack))return;
        var h=handle(stack);var benefits=benefits(stack);boolean armor=craft.get().armor();
        var before=stack.getAttributeModifiers();
        var entries=new ArrayList<>(before.modifiers());
        entries.removeIf(e->e.modifier().id().getNamespace().equals("myvillage")&&MODIFIERS.contains(e.modifier().id().getPath()));
        var defaults=stack.getItem().getDefaultInstance().getAttributeModifiers();
        if(!armor){
            double base=1+defaults.modifiers().stream().filter(e->e.attribute().equals(Attributes.ATTACK_DAMAGE)&&e.modifier().operation()==AttributeModifier.Operation.ADD_VALUE).mapToDouble(e->e.modifier().amount()).sum();
            double ordinary=base+entries.stream().filter(e->e.attribute().equals(Attributes.ATTACK_DAMAGE)
                    &&e.modifier().id().getNamespace().equals("myvillage")
                    &&Set.of("forging_sharp","tempered_edge").contains(e.modifier().id().getPath()))
                    .mapToDouble(e->e.modifier().amount()).sum();
            double total=Math.min(base*(1+FoundryRules.get().caps().attack()),ordinary*(1+benefits.attack())*h.attack());
            add(entries,Attributes.ATTACK_DAMAGE,"foundry_attack",total-ordinary,EquipmentSlotGroup.MAINHAND);
            // Vanilla attacks use this attribute; PAL timing consumes the same speed factor separately.
            double rate=4+defaults.modifiers().stream().filter(e->e.attribute().equals(Attributes.ATTACK_SPEED)&&e.modifier().operation()==AttributeModifier.Operation.ADD_VALUE).mapToDouble(e->e.modifier().amount()).sum();
            add(entries,Attributes.ATTACK_SPEED,"foundry_speed",Math.max(0.1,rate)*(speed(stack)-1),EquipmentSlotGroup.MAINHAND);
            add(entries,Attributes.MOVEMENT_SPEED,"foundry_move",h.movement(),EquipmentSlotGroup.MAINHAND,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            add(entries,Attributes.KNOCKBACK_RESISTANCE,"foundry_knockback",h.knockback(),EquipmentSlotGroup.MAINHAND);
        }else{
            for(var e:defaults.modifiers())if(e.attribute().equals(Attributes.ARMOR))
                add(entries,Attributes.ARMOR,"foundry_armor_"+craft.get().kind(),e.modifier().amount()*benefits.armor(),e.slot());
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS,new ItemAttributeModifiers(entries,before.showInTooltip()));
        // WorkshopEquipment has just rebuilt the mundane/treatment durability from its immutable base.
        double cap=stack.getItem().getDefaultInstance().getMaxDamage()*(1+FoundryRules.get().caps().durability());
        stack.set(DataComponents.MAX_DAMAGE,Math.max(1,(int)Math.ceil(Math.min(cap,WorkshopEquipment.baseDurability(stack)*(1+benefits.durability())*(armor?1:h.durability())))));
    }
    private static void add(List<ItemAttributeModifiers.Entry> entries,net.minecraft.core.Holder<Attribute> attribute,String id,double value,EquipmentSlotGroup slot){add(entries,attribute,id,value,slot,AttributeModifier.Operation.ADD_VALUE);}
    private static void add(List<ItemAttributeModifiers.Entry> entries,net.minecraft.core.Holder<Attribute> attribute,String id,double value,EquipmentSlotGroup slot,AttributeModifier.Operation operation){
        if(Math.abs(value)>0.0000001)entries.add(new ItemAttributeModifiers.Entry(attribute,new AttributeModifier(ResourceLocation.fromNamespaceAndPath("myvillage",id),value,operation),slot));
    }
    public static long qiCost(ItemStack stack,long original){
        if(original<0)throw new IllegalArgumentException("Negative qi cost");if(original==0)return 0;
        var mark=stack.get(ModDataComponents.FORGE_MARK.get());
        double affinity=mark!=null&&mark.trait().equals("channeling")?ForgingRules.get().trait("channeling").value(mark.quality()):0;
        double training=WorkshopEquipment.record(stack).channeling()*WorkshopRules.get().qi_discount_per_strike();
        double bonus=benefits(stack).qiDiscount();double material=WorkshopEquipment.craft(stack).filter(c->!c.armor()).isPresent()?handle(stack).qi():1;
        double discount=Math.min(FoundryRules.get().caps().qi_discount(),1-(1-affinity)*(1-training)*(1-bonus)*material);
        return Math.max(1,(long)Math.floor(original*(1-discount)));
    }
}
