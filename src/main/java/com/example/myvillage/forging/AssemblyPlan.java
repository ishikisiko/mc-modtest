package com.example.myvillage.forging;

import com.example.myvillage.item.*;
import net.minecraft.world.item.*;

/** Pure preview and commit share the same plan. A replacement keeps all non-handle components. */
public record AssemblyPlan(ItemStack result,ItemStack returned,int reason) {
    public boolean valid(){return reason==0;}
    private static AssemblyPlan invalid(int reason){return new AssemblyPlan(ItemStack.EMPTY,ItemStack.EMPTY,reason);}
    public static AssemblyPlan prepare(ItemStack input,ItemStack material,int quality){
        if(input.getCount()!=1)return invalid(1);
        var piece=WorkshopItems.valid(input)?input.get(ModDataComponents.WORKPIECE.get()):null;
        var craft=piece!=null?WorkshopRules.get().craft(piece.recipe()):WorkshopEquipment.craft(input).orElse(null);
        if(craft==null)return invalid(1);
        if(piece!=null&&piece.stage()!=4)return invalid(2);
        if(craft.armor()){
            if(piece==null||!ForgingIngredients.matches(FoundryRules.get().assembly().armor_material(),material))return invalid(3);
            var result=WorkshopEquipment.complete(input,quality,"wood");return new AssemblyPlan(result,ItemStack.EMPTY,0);
        }
        if(!(material.getItem() instanceof FoundryMaterialItem part)||part.flame()||material.isEmpty())return invalid(3);
        var handle=part.material();
        if(piece!=null)return new AssemblyPlan(WorkshopEquipment.complete(input,quality,handle),ItemStack.EMPTY,0);
        var mark=input.get(ModDataComponents.FORGE_MARK.get());
        if(mark==null||!FoundryEquipment.valid(input))return invalid(1);
        var before=FoundryEquipment.record(input);if(before.handle().equals(handle))return invalid(4);
        var result=input.copy();double wear=input.getDamageValue()/(double)Math.max(1,input.getMaxDamage());
        result.set(ModDataComponents.FORGE_CRAFT.get(),before.withHandle(handle));WorkshopEquipment.refresh(result);
        result.setDamageValue(Math.min(result.getMaxDamage()-1,(int)Math.ceil(result.getMaxDamage()*wear)));
        var old=ModItems.FOUNDRY_ITEMS.get(before.handle()+"_handle");
        if(old==null)return invalid(1);
        return new AssemblyPlan(result,new ItemStack(old.get()),0);
    }
}
