package com.example.myvillage.client.forging.array;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.forging.array.ForgingArrayContent;
import java.util.stream.Collectors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** 客户端限定注册，独立服务器不加载表现类。 */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class ForgingArrayClientEvents {
    private ForgingArrayClientEvents() { }

    @SubscribeEvent public static void resources(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(ForgingArrayMesh.INSTANCE);
    }

    @SubscribeEvent public static void models(ModelEvent.ModifyBakingResult event) {
        var ids = ForgingArrayContent.PARTS.values().stream().map(part -> part.getId()).collect(Collectors.toSet());
        event.getModels().replaceAll((key, model) -> ids.contains(key.id()) && !key.variant().equals("inventory")
                ? new ForgingArrayComponentModel(model) : model);
    }

    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(ForgingArrayContent.MENU.get(), ForgingArrayScreen::new);
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ForgingArrayContent.CONTROLLER_ENTITY.get(), ForgingArrayRenderer::new);
    }
}
