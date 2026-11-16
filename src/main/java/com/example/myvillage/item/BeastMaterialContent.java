package com.example.myvillage.item;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.drops.BeastMaterialRules;
import java.util.*;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class BeastMaterialContent {
    private static final DeferredRegister.Items REGISTER=DeferredRegister.createItems(MyVillageMod.MOD_ID);
    public static final Map<String,DeferredItem<BeastMaterialItem>> ITEMS=new LinkedHashMap<>();
    static {
        for(var variant:BeastMaterialRules.bundled().variants()) {
            String path=variant.id().substring(variant.id().indexOf(':')+1);
            ITEMS.put(variant.id(),REGISTER.registerItem(path,p->new BeastMaterialItem(p,variant)));
        }
    }
    private BeastMaterialContent(){}
    public static void register(IEventBus bus){REGISTER.register(bus);}
    public static void creative(CreativeModeTab.Output output){ITEMS.values().forEach(v->output.accept(v.get()));}
}
