package com.example.myvillage.client.combat;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.item.ModItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@Mod(value = MyVillageMod.MOD_ID, dist = Dist.CLIENT)
public final class ClientCombatBootstrap {
    public ClientCombatBootstrap(IEventBus modEventBus) {
        modEventBus.addListener(ClientCombatBootstrap::onClientSetup);
        modEventBus.addListener(ClientCombatBootstrap::onRegisterClientExtensions);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(CombatAnimationController::registerFactory);
    }

    private static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(QingfengFirstPersonAnimator.INSTANCE, ModItems.QINGFENG_SWORD.get());
    }
}
