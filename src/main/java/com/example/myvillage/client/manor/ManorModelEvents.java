package com.example.myvillage.client.manor;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.manor.ManorCatalog;
import java.util.HashSet;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid=MyVillageMod.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ManorModelEvents {
    @SubscribeEvent public static void models(ModelEvent.ModifyBakingResult event){
        var ids=new HashSet<ResourceLocation>();var catalog=ManorCatalog.get();
        for(var material:catalog.materials)if(!material.opaque())ids.add(ResourceLocation.fromNamespaceAndPath("myvillage",material.id()));
        for(var bank:catalog.banks)if(bank.profiles().stream().anyMatch(p->!p.translucentMaterials().isEmpty()))ids.add(ResourceLocation.fromNamespaceAndPath("myvillage",bank.id()));
        event.getModels().replaceAll((key,model)->ids.contains(key.id())&&!key.variant().equals("inventory")?new ManorGlassModel(model):model);
    }
    private ManorModelEvents(){ }
}
