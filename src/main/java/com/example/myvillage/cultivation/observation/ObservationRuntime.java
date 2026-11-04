package com.example.myvillage.cultivation.observation;

import com.example.myvillage.combat.runtime.CombatDodgeService;
import com.example.myvillage.combat.runtime.CombatHotbarService;
import com.example.myvillage.combat.runtime.CombatTargets;
import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.exploration.ExplorationRuntime;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.StatsRules;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** A bound intent supplies no target or realm; the server aims, checks and publishes the reading. */
public final class ObservationRuntime {
    private static final String REALM = "myvillage:stats_realm";
    private static final Map<UUID, Long> READY_AT = new HashMap<>(), LAST_REQUEST = new HashMap<>();
    private ObservationRuntime() { }
    public static boolean available(ServerPlayer player) { return ObservationRules.bundled().unlocked(CultivationService.getProfile(player)); }
    public static boolean blocked(ServerPlayer player) {
        return !player.isAlive() || player.isRemoved() || player.isSpectator() || player.isSleeping()
                || player.isPassenger() || player.isUsingItem() || player.containerMenu != player.inventoryMenu
                || MeditationManager.status(player).state().active() || CombatSessionManager.hudBusy(player)
                || CombatDodgeService.isDodging(player);
    }
    private static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }
    public static int cooldownRemaining(ServerPlayer player) {
        return (int) Math.clamp(READY_AT.getOrDefault(player.getUUID(), 0L) - now(player), 0, Integer.MAX_VALUE);
    }
    public static boolean cast(ServerPlayer player) {
        var rules = ObservationRules.bundled(); long now = now(player);
        Long previous = LAST_REQUEST.get(player.getUUID());
        if (previous != null && now - previous < rules.requestIntervalTicks()) return false;
        LAST_REQUEST.put(player.getUUID(), now);
        if (!available(player) || blocked(player) || cooldownRemaining(player) > 0) return false;
        var target = target(player, rules.range());
        if (target.isEmpty()) { player.sendSystemMessage(Component.literal("观察范围内没有能看清的目标。")); return false; }
        var observer = ObservationRules.profileRealm(CultivationService.getProfile(player));
        var realm = realm(target.get());
        if (observer.isEmpty() || realm.isEmpty()) {
            player.sendSystemMessage(Component.literal("你暂时无法判断对方的修为。")); return false;
        }
        var reading = rules.read(observer.get(), realm.get());
        player.sendSystemMessage(message(target.get().getDisplayName(), reading));
        READY_AT.put(player.getUUID(), now + rules.cooldownTicks());
        CombatHotbarService.forget(player.getUUID());
        return true;
    }
    static Optional<LivingEntity> target(ServerPlayer player, double range) {
        var eye = player.getEyePosition(); var direction = player.getLookAngle().scale(range); var end = eye.add(direction);
        var wall = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() == HitResult.Type.BLOCK) end = wall.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(player, eye, end,
                player.getBoundingBox().expandTowards(direction).inflate(1), entity -> {
                    var owner = CombatTargets.owner(entity);
                    return CombatTargets.physicalTarget(entity) && owner instanceof LivingEntity living && owner != player
                            && living.isAlive() && !living.isRemoved() && !living.isSpectator() && !living.isInvisibleTo(player);
                }, eye.distanceToSqr(end));
        return hit == null ? Optional.empty() : Optional.of((LivingEntity) CombatTargets.owner(hit.getEntity()));
    }
    private static Optional<RealmKey> realm(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) return ObservationRules.profileRealm(CultivationService.getProfile(player));
        if (entity instanceof NpcEntity npc && npc.isLedgerAvatar())
            return WorldSimRuntime.sim().flatMap(sim -> sim.person(npc.ledgerPersonId())).flatMap(person -> {
                try { return Optional.of(StatsRules.bundled().realmKey(person.realmId(), person.stage())); }
                catch (IllegalArgumentException invalid) { return Optional.empty(); }
            });
        return storedRealm(entity.getPersistentData());
    }
    static Optional<RealmKey> storedRealm(CompoundTag data) {
        var stats = StatsRules.bundled();
        if (!data.contains(REALM)) return Optional.of(stats.realmKey("myvillage:mortal", 0));
        if (!data.contains(REALM, Tag.TAG_COMPOUND)) return Optional.empty();
        var realm = data.getCompound(REALM);
        if (!realm.contains("realm", Tag.TAG_STRING) || !realm.contains("stage", Tag.TAG_INT)) return Optional.empty();
        try { return Optional.of(stats.realmKey(realm.getString("realm"), realm.getInt("stage"))); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }
    static Component message(Component targetName, ObservationRules.Reading reading) {
        var text = Component.literal("观察：").append(targetName.copy()).append("，");
        if (StatsRules.bundled().definition(reading.minimum()).mortal()) return text.append("未感知到修为。");
        if (reading.exact()) return text.append("修为为").append(ExplorationRuntime.realmText(reading.minimum())).append("。");
        return text.append("修为在").append(ExplorationRuntime.realmText(reading.minimum())).append("至")
                .append(ExplorationRuntime.realmText(reading.maximum())).append("之间。");
    }
    public static void forget(UUID player) { READY_AT.remove(player); LAST_REQUEST.remove(player); }
    public static void clear() { READY_AT.clear(); LAST_REQUEST.clear(); }
}
