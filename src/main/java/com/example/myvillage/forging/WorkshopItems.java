package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.item.ModItems;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.*;
import java.util.*;

public final class WorkshopItems {
    public static final List<String> STAGES=List.of("rough","shaped","quenched","tempered","ground");
    private WorkshopItems(){}
    public static Map<String,DeferredItem<com.example.myvillage.item.ForgePatternItem>> registerArmorPatterns(DeferredRegister.Items register){
        var result=new LinkedHashMap<String,DeferredItem<com.example.myvillage.item.ForgePatternItem>>();
        for(var craft:WorkshopRules.get().crafts())if(craft.armor()){
            var recipe=ForgingRules.get().recipe(craft.recipe()).orElseThrow();
            var id=net.minecraft.resources.ResourceLocation.parse(recipe.pattern()).getPath();
            result.put(id,register.registerItem(id,p->new com.example.myvillage.item.ForgePatternItem(p,recipe.id())));
        }
        return Collections.unmodifiableMap(result);
    }
    public static Map<String,DeferredItem<WorkpieceItem>> register(DeferredRegister.Items register){
        Map<String,DeferredItem<WorkpieceItem>> result=new LinkedHashMap<>();
        for(var craft:WorkshopRules.get().crafts())for(int stage=0;stage<STAGES.size();stage++){
            final int n=stage;String id="workpiece_"+craft.recipe()+"_"+STAGES.get(stage);
            result.put(id,register.registerItem(id,props->new WorkpieceItem(props,craft.recipe(),n)));
        }
        return Collections.unmodifiableMap(result);
    }
    public static ItemStack blank(Workpiece piece){
        var item=ModItems.WORKPIECES.get("workpiece_"+piece.recipe()+"_"+STAGES.get(piece.stage()));
        if(item==null)throw new IllegalArgumentException("Unknown workpiece");
        var stack=new ItemStack(item.get());stack.set(ModDataComponents.WORKPIECE.get(),piece);return stack;
    }
    public static boolean valid(ItemStack stack){
        var piece=stack.get(ModDataComponents.WORKPIECE.get());
        return stack.getCount()==1&&stack.getItem() instanceof WorkpieceItem item&&piece!=null
                &&piece.recipe().equals(item.recipe())&&piece.stage()==item.stage()
                &&FoundryEquipment.record(stack).manualPoints()<=piece.points()
                &&FoundryEquipment.record(stack).fire().isEmpty()
                &&piece.points()<=WorkshopRules.get().formingPoints(piece.recipe())
                &&(piece.stage()==0?piece.points()<WorkshopRules.get().shapeAt(piece.recipe()):piece.points()>=WorkshopRules.get().shapeAt(piece.recipe()))
                &&(piece.stage()<2||piece.points()==WorkshopRules.get().formingPoints(piece.recipe()));
    }
    /** Stage changes replace the registered blank but retain all earned work and real heat. */
    public static ItemStack advance(ItemStack original,Workpiece piece){
        var result=blank(piece);result.applyComponents(original.getComponentsPatch());
        result.set(ModDataComponents.WORKPIECE.get(),piece);return result;
    }
}
