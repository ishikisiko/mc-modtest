package com.example.myvillage.forging;

import com.example.myvillage.item.ForgingHammerItem;
import java.util.*;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.*;

public final class FoundryItems {
    private FoundryItems(){}
    public static Map<String,DeferredItem<? extends Item>> register(DeferredRegister.Items register){
        var result=new LinkedHashMap<String,DeferredItem<? extends Item>>();
        for(var h:FoundryRules.get().hammers())if(!h.id().equals("forging_hammer"))result.put(h.id(),register.registerItem(h.id(),p->new ForgingHammerItem(p,h.id())));
        for(var h:FoundryRules.get().handles())result.put(h.id()+"_handle",register.registerItem(h.id()+"_handle",p->new FoundryMaterialItem(p,h.id(),false)));
        for(var f:FoundryRules.get().fires())result.put(f.id()+"_fire_seed",register.registerItem(f.id()+"_fire_seed",p->new FoundryMaterialItem(p,f.id(),true)));
        result.put("fire_capture_bottle",register.registerItem("fire_capture_bottle",FireCaptureItem::new));
        return Collections.unmodifiableMap(result);
    }
}
