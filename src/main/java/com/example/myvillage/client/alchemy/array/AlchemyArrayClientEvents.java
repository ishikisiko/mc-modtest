package com.example.myvillage.client.alchemy.array;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.alchemy.array.AlchemyArrayContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import java.util.stream.Collectors;

/** Dist filtering keeps all client rendering classes off the dedicated-server class-loading path. */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class AlchemyArrayClientEvents {
    private AlchemyArrayClientEvents() { }
    @SubscribeEvent public static void resources(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(AlchemyArrayMesh.INSTANCE);
    }
    @SubscribeEvent public static void models(ModelEvent.ModifyBakingResult event) {
        var ids = AlchemyArrayContent.PARTS.values().stream().map(part -> part.getId()).collect(Collectors.toSet());
        event.getModels().replaceAll((key, model) -> ids.contains(key.id()) && !key.variant().equals("inventory")
                ? new AlchemyArrayComponentModel(model) : model);
    }
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(AlchemyArrayContent.MENU.get(), AlchemyArrayScreen::new);
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AlchemyArrayContent.CONTROLLER_ENTITY.get(), AlchemyArrayRenderer::new);
    }
}
