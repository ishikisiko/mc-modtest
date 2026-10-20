package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.item.ModItems;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.*;
import java.util.*;

public final class WorkshopItems {
    public static final List<String> STAGES=List.of("rough","shaped","quenched","tempered","ground");
    private WorkshopItems(){}
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
                &&piece.blows()<=WorkshopRules.get().forming_strikes()
                &&(piece.stage()==0?piece.blows()<WorkshopRules.get().shape_at():piece.blows()>=WorkshopRules.get().shape_at())
                &&(piece.stage()<2||piece.blows()==WorkshopRules.get().forming_strikes());
    }
}
