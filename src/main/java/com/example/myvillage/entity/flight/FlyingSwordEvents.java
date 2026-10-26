package com.example.myvillage.entity.flight;

import com.example.myvillage.entity.RideableFlyingSwordEntity;
import com.example.myvillage.cultivation.CultivationService;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber(modid = "myvillage")
public final class FlyingSwordEvents {
    private FlyingSwordEvents() { }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            var pending = FlyingSwordInventory.pendingLanding(p).copy();
            RideableFlyingSwordEntity.recallOwned(p); // returns any restart escrow once, before restoration
            FlyingSwordInventory.clearLanding(p);
            if (pending.isEmpty() || !pending.getString("dimension").equals(p.level().dimension().location().toString())
                    || !p.isAlive() || p.isSpectator() || p.onGround()) return;
            recoverLanding(p, pending.getBoolean("azure"));
        }
    }
    private static void recoverLanding(ServerPlayer p, boolean azure) {
        var level=p.serverLevel();
        var sword=com.example.myvillage.entity.ModEntities.RIDEABLE_FLYING_SWORD.get().create(level);
        if (sword != null) {
            sword.moveTo(p.getX(),p.getY()-.27,p.getZ(),p.getYRot(),0);
            var box=sword.getBoundingBox();
            if (level.hasChunksAt(net.minecraft.core.BlockPos.containing(box.minX,box.minY,box.minZ),
                    net.minecraft.core.BlockPos.containing(box.maxX,box.maxY,box.maxZ)) && level.noBlockCollision(sword,box)) {
                sword.initializeFlight(p,azure,FlyingSwordRules.load(p)); sword.emergencyRecovery();
                if(level.addFreshEntity(sword) && p.startRiding(sword,true)) {
                    FlyingSwordInventory.rememberLanding(p,azure); p.resetFallDistance(); return;
                }
            }
            sword.discard();
        }
        // World geometry may have changed while offline: seek a clear loaded landing cell in this column.
        var pos=p.blockPosition();
        for(int y=Math.min(pos.getY(),level.getMaxBuildHeight()-2);y>level.getMinBuildHeight();y--) {
            var floor=new net.minecraft.core.BlockPos(pos.getX(),y-1,pos.getZ());
            if(!level.hasChunkAt(floor)) break;
            if(level.getBlockState(floor).isFaceSturdy(level,floor,net.minecraft.core.Direction.UP)
                    && level.getFluidState(floor.above()).isEmpty() && level.getFluidState(floor.above(2)).isEmpty()
                    && level.noBlockCollision(p,p.getBoundingBox().move(0,y-p.getY(),0))) {
                p.teleportTo(p.getX(),y,p.getZ());p.resetFallDistance();return;
            }
        }
        // Bounded fallback only when no loaded safe surface/vehicle volume exists. Never persist immunity.
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING,200,0,false,false));
        p.resetFallDistance();
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if(e.getEntity() instanceof ServerPlayer p) FlyingSwordInventory.clearLanding(p);
    }
    @SubscribeEvent public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            FlyingSwordInventory.clearLanding(p); RideableFlyingSwordEntity.recallOwned(p);
        }
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("myvillage").then(Commands.literal("flying_sword")
                .requires(s -> s.hasPermission(2)).then(Commands.literal("status")
                .executes(c -> status(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player()).executes(c ->
                        status(c.getSource(), EntityArgument.getPlayer(c, "player")))))));
    }
    private static int status(net.minecraft.commands.CommandSourceStack source, ServerPlayer p) {
        var json = new com.google.gson.JsonObject();
        var sword = p.getVehicle() instanceof RideableFlyingSwordEntity s ? s : null;
        json.addProperty("owner",p.getUUID().toString());json.addProperty("active",sword!=null);
        json.addProperty("phase",sword==null?"NONE":sword.phase().name());
        json.addProperty("tier",sword==null?"NONE":sword.azure()?"azure":"basic");
        json.addProperty("qi",CultivationService.getProfile(p).currentSpiritualPower());
        var position=sword==null?p.position():sword.position();var velocity=sword==null?p.getDeltaMovement():sword.getDeltaMovement();
        var positionJson=new com.google.gson.JsonArray();positionJson.add(position.x);positionJson.add(position.y);positionJson.add(position.z);
        var velocityJson=new com.google.gson.JsonArray();velocityJson.add(velocity.x);velocityJson.add(velocity.y);velocityJson.add(velocity.z);
        json.add("position",positionJson);json.add("velocity",velocityJson);
        json.addProperty("landing",sword!=null && sword.isLanding());
        json.addProperty("escrow",FlyingSwordInventory.occupied(p));
        source.sendSuccess(() -> Component.literal("FLYING_SWORD_STATUS " + json), false);
        return 1;
    }
}
