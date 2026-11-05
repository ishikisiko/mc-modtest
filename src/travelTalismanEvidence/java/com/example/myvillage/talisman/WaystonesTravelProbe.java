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
    static void fixture(ServerPlayer p) {
        if (!registered) {
            Balm.getEvents().onEvent(WaystoneTeleportEvent.Pre.class, e -> {
                if (CANCEL.remove(e.getContext().getEntity().getUUID())) e.setCanceled(true);
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
