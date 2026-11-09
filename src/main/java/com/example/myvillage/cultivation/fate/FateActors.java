package com.example.myvillage.cultivation.fate;

import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.combat.runtime.CombatActors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.UUID;

/** 效果共用真实身体，持久写入分别由玩家服务和命簿负责。 */
public final class FateActors {
    private FateActors() { }
    public static boolean supported(LivingEntity actor) { return actor instanceof ServerPlayer || actor instanceof NpcEntity; }
    public static boolean valid(LivingEntity actor) {
        return supported(actor) && !actor.level().isClientSide() && actor.isAlive() && !actor.isRemoved()
                && (!(actor instanceof ServerPlayer player) || !player.isSpectator());
    }
    public static ServerLevel level(LivingEntity actor) { return (ServerLevel) actor.level(); }
    public static long now(LivingEntity actor) { return actor.getServer().overworld().getGameTime(); }
    public static int slot(LivingEntity actor) { return actor instanceof ServerPlayer player ? player.getInventory().selected : -1; }
    public static UUID identity(LivingEntity actor) {
        return actor instanceof NpcEntity npc && npc.isLedgerAvatar()
                ? UUID.nameUUIDFromBytes(("myvillage:npc:"+npc.ledgerPersonId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)) : actor.getUUID();
    }
    public static java.util.Optional<com.example.myvillage.stats.CultivationStatsRules.Body> body(LivingEntity actor) {
        if(actor instanceof ServerPlayer player)return com.example.myvillage.cultivation.CultivationStats.find(
                com.example.myvillage.cultivation.CultivationService.getProfile(player));
        if(actor instanceof NpcEntity npc)return com.example.myvillage.stats.runtime.StatsRuntime.assignedRealm(npc)
                .map(key->com.example.myvillage.stats.CultivationStatsRules.bundled().at(key,npc.foundationQuality(),npc.goldenCoreQuality()));
        return java.util.Optional.empty();
    }
    public static LivingEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) if (level.getEntity(id) instanceof LivingEntity actor && supported(actor)) return actor;
        return null;
    }
    public static java.util.List<LivingEntity> all(MinecraftServer server) {
        var actors=new java.util.ArrayList<LivingEntity>(server.getPlayerList().getPlayers());
        for(var npc:com.example.myvillage.entity.npc.NpcCombatRuntime.loadedBodies())
            if(com.example.myvillage.entity.npc.NpcCombatRuntime.canAct(npc)&&com.example.myvillage.entity.npc.NpcCombatRuntime.canTick(npc))actors.add(npc);
        return actors;
    }
    public static void message(LivingEntity actor, net.minecraft.network.chat.Component text) {
        if (actor instanceof ServerPlayer player) player.displayClientMessage(text, true);
    }
    public static boolean mayHarm(LivingEntity actor, LivingEntity target) {
        return actor != target && !actor.isAlliedTo(target) && !target.isAlliedTo(actor) && CombatActors.mayHarm(actor, target);
    }
    public static long powerCap(LivingEntity actor) {
        return actor instanceof ServerPlayer player ? com.example.myvillage.cultivation.CultivationService.spiritualPowerCap(player)
                : actor instanceof NpcEntity npc ? com.example.myvillage.entity.npc.NpcCultivationRuntime.cap(npc) : 0;
    }
    public static boolean recoverPower(LivingEntity actor, long amount) {
        return actor instanceof ServerPlayer player ? com.example.myvillage.cultivation.CultivationService.recoverSpiritualPower(player,amount).success()
                : actor instanceof NpcEntity npc && com.example.myvillage.entity.npc.NpcCultivationRuntime.recover(npc,amount);
    }
}
