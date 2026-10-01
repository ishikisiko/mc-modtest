package com.example.myvillage.client.combat;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.CombatParticles;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.definition.WeaponDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Mod(value = MyVillageMod.MOD_ID, dist = Dist.CLIENT)
public final class ClientCombatBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientCombatBootstrap.class);

    public ClientCombatBootstrap(IEventBus modEventBus) {
        modEventBus.addListener(ClientCombatBootstrap::onClientSetup);
        modEventBus.addListener(ClientCombatBootstrap::onRegisterClientExtensions);
        modEventBus.addListener(ClientCombatBootstrap::onRegisterReloadListeners);
        modEventBus.addListener(ClientCombatBootstrap::onRegisterParticleProviders);
        // The arm is drawn first so the translucent 剑光 blends over it.
        NeoForge.EVENT_BUS.addListener(FirstPersonArmRenderer::onRenderHand);
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

    /** The first-person viewmodel extension goes on every registered weapon item. */
    private static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        List<Item> items = new ArrayList<>();
        for (WeaponDefinition weapon : CombatStyles.bundled().weapons()) {
            if (!BuiltInRegistries.ITEM.containsKey(weapon.item())) {
                LOGGER.error("Combat weapon item {} is not registered; it gets no first-person rig", weapon.item());
                continue;
            }
            items.add(BuiltInRegistries.ITEM.get(weapon.item()));
        }
        if (!items.isEmpty()) {
            event.registerItem(FirstPersonWeaponAnimator.INSTANCE, items.toArray(Item[]::new));
        }
    }

    private static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(CombatParticles.BLADE_CUT.get(), BladeCutParticle.Provider::new);
    }
}
