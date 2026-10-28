package com.example.myvillage.client.cultivation.motion;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

@Mod(value="myvillage",dist=Dist.CLIENT)
public final class MeditationClient {
    public MeditationClient(IEventBus bus) {
        ClientMeditationMotion.initialize();
        bus.addListener(MeditationClient::reload);
        NeoForge.EVENT_BUS.addListener(MeditationClient::tick);
        NeoForge.EVENT_BUS.addListener(MeditationClient::logout);
        NeoForge.EVENT_BUS.addListener(MeditationClient::camera);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,false,RenderHandEvent.class,MeditationClient::hand);
    }
    private static void reload(RegisterClientReloadListenersEvent e){e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)ClientMeditationMotion::reload);}
    private static void tick(ClientTickEvent.Post e){ClientMeditationMotion.tick();}
    private static void logout(ClientPlayerNetworkEvent.LoggingOut e){ClientMeditationMotion.clear();}
    private static void camera(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles e) {
        // Camera-only eye easing. Player collision, interaction rays and server position stay vanilla.
        if(e.getCamera().isDetached() || !(e.getCamera().getEntity() instanceof net.minecraft.client.player.AbstractClientPlayer player))return;
        MeditationPose pose=ClientMeditationMotion.pose(player,(float)e.getPartialTick());
        if(pose!=null)((com.example.myvillage.mixin.MeditationCameraAccess)e.getCamera()).myvillage$meditationPosition(
                e.getCamera().getPosition().add(0,-pose.drop()*.9375/16,0));
    }
    private static void hand(RenderHandEvent e) {
        var player=Minecraft.getInstance().player;
        if(player!=null&&ClientMeditationMotion.displaying(player))e.setCanceled(true);
    }
}
