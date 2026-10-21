package com.example.myvillage.client.gathering;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.gathering.GatheringContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid=MyVillageMod.MOD_ID,value=Dist.CLIENT)
public final class GatheringClientEvents {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(GatheringContent.MENU.get(),GatheringScreen::new);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(GatheringContent.ENTITY.get(),GatheringRenderer::new);}
    private GatheringClientEvents(){ }
}
