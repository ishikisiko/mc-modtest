package com.example.myvillage.client.combat;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.item.ModItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = MyVillageMod.MOD_ID, dist = Dist.CLIENT)
public final class ClientCombatBootstrap {
    public ClientCombatBootstrap(IEventBus modEventBus) {
        modEventBus.addListener(ClientCombatBootstrap::onClientSetup);
        modEventBus.addListener(ClientCombatBootstrap::onRegisterClientExtensions);
        modEventBus.addListener(ClientCombatBootstrap::onRegisterReloadListeners);
        NeoForge.EVENT_BUS.addListener(FirstPersonSwordTrail::onRenderHand);
        NeoForge.EVENT_BUS.addListener(CombatWorldTrails::onRenderLevelStage);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(CombatAnimationController::registerFactory);
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(FirstPersonSwingResources.INSTANCE);
    }

    private static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(QingfengFirstPersonAnimator.INSTANCE, ModItems.QINGFENG_SWORD.get());
    }
}
