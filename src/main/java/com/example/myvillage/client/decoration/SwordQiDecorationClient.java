package com.example.myvillage.client.decoration;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.decoration.SwordQiDecorationContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class SwordQiDecorationClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SwordQiDecorationContent.ENTITY.get(), SwordQiPedestalRenderer::new);
    }
    private SwordQiDecorationClient() { }
}
