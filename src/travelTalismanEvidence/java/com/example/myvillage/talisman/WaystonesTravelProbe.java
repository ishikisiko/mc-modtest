package com.example.myvillage.talisman;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.waystones.api.MutableWaystone;
import net.blay09.mods.waystones.api.WaystoneStyles;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 独立可选类，未安装 Waystones 的启动场景不得提前解析。 */
final class WaystonesTravelProbe {
    private static final Set<UUID> CANCEL = new HashSet<>();
    private static boolean registered;
    private static int canceledEvents;
    private static long reservedPower = -1;
    private static int reservedHomeCount = -1;
    static com.google.gson.JsonObject events() {
        var j = new com.google.gson.JsonObject(); j.addProperty("canceledEvents", canceledEvents);
        j.addProperty("reservedPower", reservedPower); j.addProperty("reservedHomeCount", reservedHomeCount); return j;
    }
    static com.google.gson.JsonArray inspect(ServerPlayer p) {
        var result = new com.google.gson.JsonArray();
        WaystonesAPI.getAllWaystones(p.getServer()).forEach(stone -> {
            var row = new com.google.gson.JsonObject(); row.addProperty("name", stone.getName().getString());
            row.addProperty("pos", stone.getPos().toShortString()); row.addProperty("class", stone.getClass().getName());
            var level = p.getServer().getLevel(stone.getDimension());
            row.addProperty("loaded", level.hasChunkAt(stone.getPos()));
            row.addProperty("block", level.getBlockState(stone.getPos()).toString());
            row.addProperty("valid", stone.isValidInLevel(level));
            stone.resolveDestination(level).ifPresent(at -> {
                var v = at.location();
                var point = new TravelPoint(level.dimension().location(), v.x, v.y, v.z, at.direction().toYRot(), 0);
                row.add("point", TravelPoint.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, point).getOrThrow());
                var body = p.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(point.position());
                row.addProperty("body", body.toString()); row.addProperty("safe", TalismanTravel.safe(p, level, point));
                row.addProperty("safeArrival", TalismanTravel.safeWaystoneArrival(p, level, point));
                row.addProperty("empty_body", level.noCollision(p, body));
                row.addProperty("support", level.getBlockCollisions(p, body.move(0, -.6, 0)).iterator().hasNext());
                row.addProperty("below", level.getBlockState(BlockPos.containing(v).below()).toString());
            });
            var context = WaystonesAPI.createCustomTeleportContext(p, stone);
            row.addProperty("context", context.left().isPresent() ? "ready" : context.right().orElseThrow().getClass().getSimpleName());
            result.add(row);
        });
        return result;
    }
    static void fixture(ServerPlayer p) {
        if (!registered) {
            Balm.getEvents().onEvent(WaystoneTeleportEvent.Pre.class, e -> {
                if (CANCEL.remove(e.getContext().getEntity().getUUID())) {
                    canceledEvents++;
                    var player = (ServerPlayer) e.getContext().getEntity();
                    reservedPower = com.example.myvillage.cultivation.CultivationService.getProfile(player).currentSpiritualPower();
                    reservedHomeCount = 0;
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        var stack = player.getInventory().getItem(i);
                        if (stack.is(TalismanContent.HOME.get())) reservedHomeCount += stack.getCount();
                    }
                    e.setCanceled(true);
                }
            });
            registered = true;
        }
        var level = p.getServer().overworld();
        for (int i = 0; i < 9; i++) {
            int index = i;
            var at = new BlockPos(128 + i * 32, 80, 0);
            var existing = WaystonesAPI.getWaystoneAt(level, at);
            var stone = existing.orElseGet(() -> {
                TravelTalismanProbe.platform(level, at);
                return WaystonesAPI.placeWaystone(level, at, WaystoneStyles.DEFAULT).orElseThrow();
            });
            ((MutableWaystone) stone).setName(Component.literal(index == 8 ? "未激活石碑" : "传送点" + (index + 1)));
            if (i < 8) WaystonesAPI.activateWaystone(p, stone);
        }
    }
    static void action(ServerPlayer p, String action) {
        if (action.equals("cancel_next")) { CANCEL.add(p.getUUID()); return; }
        var menu = (TalismanTravelMenu) p.containerMenu;
        var stone = WaystonesAPI.getWaystone(p.getServer(), menu.entries().getFirst().id()).orElseThrow();
        if (action.equals("revoke_first")) WaystonesAPI.deactivateWaystone(p, stone);
        else p.getServer().getLevel(stone.getDimension()).destroyBlock(stone.getPos(), false);
    }
}
