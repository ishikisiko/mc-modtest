package com.example.myvillage.client;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.client.entity.FlyingSwordVisuals;
import com.example.myvillage.entity.RideableFlyingSwordEntity;
import com.example.myvillage.network.FlyingSwordInputPayload;
import net.minecraft.client.player.Input;
import net.minecraft.client.Minecraft;
import com.example.myvillage.network.FlyingSwordRecallPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class FlyingSwordClientInput {
    private static boolean useWasDown;
    private static float fov = 1;
    private static final java.util.Set<java.util.UUID> LEANING = new java.util.HashSet<>();

    private FlyingSwordClientInput() {
    }

    @SubscribeEvent
    static void sendRidingInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity().getVehicle() instanceof RideableFlyingSwordEntity)) {
            useWasDown = Minecraft.getInstance().options.keyUse.isDown();
            return;
        }

        Input input = event.getInput();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !mc.isWindowActive()) {
            PacketDistributor.sendToServer(new FlyingSwordInputPayload((byte) 0));
            input.shiftKeyDown = false;
            return;
        }
        FlyingSwordInputPayload payload = FlyingSwordInputPayload.fromKeys(
                input.up,
                input.down,
                input.left,
                input.right,
                input.jumping,
                input.shiftKeyDown, mc.options.keySprint.isDown());
        PacketDistributor.sendToServer(payload);

        // Empty hands still recall the escrowed sword. The launch click must be released first.
        boolean use = mc.options.keyUse.isDown();
        boolean recallHand = mc.player != null && ((mc.player.getMainHandItem().isEmpty() && mc.player.getOffhandItem().isEmpty())
                || mc.player.getMainHandItem().getItem() instanceof com.example.myvillage.item.RideableFlyingSwordItem
                || mc.player.getOffhandItem().getItem() instanceof com.example.myvillage.item.RideableFlyingSwordItem);
        if (use && !useWasDown && recallHand) PacketDistributor.sendToServer(new FlyingSwordRecallPayload());
        useWasDown = use;
        // Shift controls descent here, so do not let vanilla treat it as dismount input.
        input.shiftKeyDown = false;
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    static void lean(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre e) {
        if (!(e.getEntity().getVehicle() instanceof RideableFlyingSwordEntity sword)) return;
        var pose = e.getPoseStack(); pose.pushPose(); LEANING.add(e.getEntity().getUUID());
        float yaw = sword.getYRot();
        float bank = net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(sword.getYRot()-sword.yRotO), -FlyingSwordVisuals.get().max_bank_degrees(), FlyingSwordVisuals.get().max_bank_degrees());
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(sword.phase() == com.example.myvillage.entity.flight.FlyingSwordFlight.Phase.BOOST ? FlyingSwordVisuals.get().boost_lean_degrees() : FlyingSwordVisuals.get().cruise_lean_degrees()));
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-bank));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
    }
    @SubscribeEvent static void unlean(net.neoforged.neoforge.client.event.RenderPlayerEvent.Post e) {
        if (LEANING.remove(e.getEntity().getUUID())) e.getPoseStack().popPose();
    }
    @SubscribeEvent static void fov(net.neoforged.neoforge.client.event.ComputeFovModifierEvent e) {
        e.setNewFovModifier(e.getNewFovModifier() * fov);
    }
    @SubscribeEvent static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        float target = mc.player != null && mc.player.getVehicle() instanceof RideableFlyingSwordEntity s
                && s.phase() == com.example.myvillage.entity.flight.FlyingSwordFlight.Phase.BOOST ? FlyingSwordVisuals.get().boost_fov() : 1;
        fov += (target-fov)*FlyingSwordVisuals.get().fov_response();
        var visual=FlyingSwordVisuals.get();
        if(mc.level!=null) for(var entity:mc.level.entitiesForRendering()) {
            if(!(entity instanceof RideableFlyingSwordEntity sword)
                    || sword.phase()!=com.example.myvillage.entity.flight.FlyingSwordFlight.Phase.BOOST) continue;
            if(sword.tickCount % visual.trail_interval_ticks()==0) {
                var behind=net.minecraft.world.phys.Vec3.directionFromRotation(0,sword.getYRot()).scale(-visual.trail_distance());
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD,sword.getX()+behind.x,
                        sword.getY()+visual.model_y_offset(),sword.getZ()+behind.z,0,.005,0);
            }
            if(sword.tickCount % visual.wind_interval_ticks()==0 && mc.player!=null && mc.player.getVehicle()==sword)
                mc.level.playLocalSound(sword.getX(),sword.getY(),sword.getZ(),net.minecraft.sounds.SoundEvents.ELYTRA_FLYING,
                        net.minecraft.sounds.SoundSource.PLAYERS,visual.wind_volume(),visual.wind_pitch(),false);
        }
        if (mc.player == null || !(mc.player.getVehicle() instanceof RideableFlyingSwordEntity))
            useWasDown = mc.options.keyUse.isDown();
    }
}
