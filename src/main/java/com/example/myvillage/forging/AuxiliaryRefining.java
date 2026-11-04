package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

/** A fire adds one bounded contribution, retaining quality, handle, work, names and enchantments. */
public final class AuxiliaryRefining {
    private AuxiliaryRefining(){}
    public static boolean validSeed(ItemStack stack){
        if(stack.isEmpty())return false;
        var seed=stack.get(ModDataComponents.FIRE_SEED.get());
        return stack.getCount()==1&&stack.getItem() instanceof FoundryMaterialItem item&&item.flame()&&seed!=null
                &&item.material().equals(seed.kind())&&seed.maturity()<=FoundryRules.get().fire().max_maturity();
    }
    public static EquipmentInfusion.Preparation prepare(ItemStack input,ItemStack fire,String trait,int cap){
        if(fire.isEmpty())return EquipmentInfusion.prepare(input,trait,cap);
        if(!validSeed(fire)||input.getCount()!=1||WorkshopEquipment.craft(input).isEmpty()||!FoundryEquipment.valid(input))return rejected(2);
        if(cap<1||cap>4)return rejected(7);
        var existing=FoundryEquipment.record(input);if(!existing.fire().isEmpty())return rejected(21);
        var completed=input.get(ModDataComponents.FORGE_MARK.get());if(completed==null)return rejected(20);
        EquipmentInfusion.Preparation base;
        if(completed.infused()){
            var recipe=ForgingRules.get().recipe(WorkshopEquipment.craft(input).orElseThrow().recipe()).orElseThrow();
            base=new EquipmentInfusion.Preparation(0,input.copy(),WorkshopRules.get().infusionCosts(recipe,completed.trait()));
        }else base=EquipmentInfusion.prepare(input,trait,cap);
        if(!base.valid())return base;
        var result=base.result().copy();var seed=fire.get(ModDataComponents.FIRE_SEED.get());
        result.set(ModDataComponents.FORGE_CRAFT.get(),existing.withFire(seed.kind(),seed.maturity()));WorkshopEquipment.refresh(result);
        return new EquipmentInfusion.Preparation(0,result,base.costs());
    }
    private static EquipmentInfusion.Preparation rejected(int reason){return new EquipmentInfusion.Preparation(reason,ItemStack.EMPTY,Map.of());}
}
