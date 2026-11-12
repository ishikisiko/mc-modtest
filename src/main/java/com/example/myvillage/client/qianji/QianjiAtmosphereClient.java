package com.example.myvillage.client.qianji;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.qianji.QianjiContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid=MyVillageMod.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class QianjiAtmosphereClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(QianjiContent.ENTITY.get(),QianjiAtmosphereRenderer::new);}
    private QianjiAtmosphereClient(){}
}
