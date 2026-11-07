package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.CultivationStats;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SpiritBodyRuntime {
    private static final Map<UUID,Integer> MODES=new HashMap<>();
    private SpiritBodyRuntime() { }
    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e)->{
            if(e.getServer().overworld().getGameTime()%SpiritBodyRules.get().phoenix().syncIntervalTicks()!=0)return;
            for(var p:e.getServer().getPlayerList().getPlayers())sync(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->MODES.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->MODES.clear());
    }
    public static SpiritBodyStatusPayload status(ServerPlayer player) {
        var rules=SpiritBodyRules.get();var clock=PhoenixRuntime.clock(player);
        int mode=clock!=null?3:PhoenixRuntime.enabled(player)?2:JingangRuntime.enabled(player)?1:0;
        return new SpiritBodyStatusPayload(mode,JingangRuntime.state(player).points(),
                CultivationStats.find(CultivationService.getProfile(player)).map(b->b.defense()).orElse(0.0),
                rules.adaptation().defenseFractionPerPoint(),rules.adaptation().capacity(),
                Math.min(rules.phoenix().capacity(),FateService.state(player).phoenix().reserve()),rules.phoenix().capacity(),rules.phoenix().rebirthCost(),
                clock==null?0:clock.durability(),clock==null?0:clock.maximumDurability(),clock==null?0:clock.remaining(),clock==null?rules.phoenix().emberTicks():clock.duration());
    }
    public static void sync(ServerPlayer player) {
        if(player.getServer().getPlayerList().getPlayer(player.getUUID())!=player)return;
        var payload=status(player);int previous=MODES.getOrDefault(player.getUUID(),0);MODES.put(player.getUUID(),payload.mode());
        if(payload.mode()!=0||previous!=0)PacketDistributor.sendToPlayer(player,payload);
    }
}
