package com.example.myvillage.client.combat;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.CombatParticles;
import com.example.myvillage.item.ModItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = MyVillageMod.MOD_ID, dist = Dist.CLIENT)
public final class ClientCombatBootstrap {
    public ClientCombatBootstrap(IEventBus modEventBus) {
        modEventBus.addListener(ClientCombatBootstrap::onClientSetup);
        modEventBus.addListener(ClientCombatBootstrap::onRegisterClientExtensions);
        modEventBus.addListener(ClientCombatBootstrap::onRegisterReloadListeners);
        modEventBus.addListener(ClientCombatBootstrap::onRegisterParticleProviders);
        // The arm is drawn first so the translucent 剑光 blends over it.
        NeoForge.EVENT_BUS.addListener(QingfengFirstPersonArmRenderer::onRenderHand);
        NeoForge.EVENT_BUS.addListener(FirstPersonSwordTrail::onRenderHand);
        NeoForge.EVENT_BUS.addListener(CombatWorldTrails::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(CombatCameraFx::onComputeCameraAngles);
        NeoForge.EVENT_BUS.addListener(CombatCameraFx::onComputeFov);
        NeoForge.EVENT_BUS.addListener(CombatCameraFx::onComputeFovModifier);
        NeoForge.EVENT_BUS.addListener(CombatImpactFx::onEntityTickPre);
        NeoForge.EVENT_BUS.addListener(CombatImpactFx::onRenderFramePre);
        // Lowest priority, skipping cancelled renders, so the Pre push always meets its Post pop.
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST, false, RenderLivingEvent.Pre.class, CombatImpactFx::onRenderLivingPre);
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST, false, RenderLivingEvent.Post.class, CombatImpactFx::onRenderLivingPost);
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

    private static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(CombatParticles.BLADE_CUT.get(), BladeCutParticle.Provider::new);
    }
}
