package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.definition.CombatSkillDefinition;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.network.CombatSkillStatePayload;
import com.example.myvillage.combat.network.CombatSkillStatePayload.Phase;
import com.example.myvillage.combat.session.CombatSession;
import com.example.myvillage.combat.session.CombatSkillSession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import com.example.myvillage.cultivation.fate.FateActors;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Server-created derivatives have independent hit ledgers, never own the player's action or qi. */
public final class DerivedSwordWaves {
    private record Wave(UUID owner, ItemStack weapon, int slot, ResourceLocation dimension,
                        CombatSession hits, CombatSkillSession flight) { }
    private static final Map<Long, Wave> WAVES = new LinkedHashMap<>();
    private static long sequence;
    private DerivedSwordWaves() { }

    public static boolean launch(LivingEntity player, CombatSkillDefinition skill, float yaw) {
        if (!FateActors.valid(player)
                || WAVES.values().stream().filter(w -> w.owner().equals(player.getUUID())).count() >= 64)
            return false;
        var style = CombatStyles.bundled().styleFor(player.getMainHandItem()).orElse(null);
        if (style == null || sequence == Long.MAX_VALUE) return false;
        long id = ++sequence;
        long now = FateActors.level(player).getGameTime();
        var hits = new CombatSession(style);
        hits.startExternal(skill.releaseMove(CombatStyles.bundled(), 0), now,
                BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()),
                player.level().dimension().location(), yaw);
        var flight = new CombatSkillSession(skill, skill.id(), 0, now, id);
        // Facing is frozen by the triggering melee action; pitch is authoritative server input.
        Vec3 direction = Vec3.directionFromRotation(player.getXRot(), yaw);
        flight.release(now, player.position().add(0, skill.launchHeight(), 0), direction);
        var wave = new Wave(player.getUUID(), player.getMainHandItem(), FateActors.slot(player),
                player.level().dimension().location(), hits, flight);
        WAVES.put(id, wave);
        send(player, flight, Phase.DERIVED_RELEASED);
        log(player, flight, "released", "");
        return true;
    }

    public static void tick(MinecraftServer server) {
        for (var entry : List.copyOf(WAVES.entrySet())) {
            Wave wave = entry.getValue();
            var player = FateActors.find(server,wave.owner());
            var flight = wave.flight();
            if (player == null || !FateActors.valid(player)
                    || player.getMainHandItem() != wave.weapon() || FateActors.slot(player) != wave.slot()
                    || !player.level().dimension().location().equals(wave.dimension())) {
                WAVES.remove(entry.getKey());
                if (player != null) send(player, flight, Phase.DERIVED_STOPPED);
                continue;
            }
            long now = FateActors.level(player).getGameTime();
            if (!flight.waveActive(now)) continue;
            var skill = flight.skill();
            Vec3 extents = CombatWaveResolver.extents(flight.direction(), wave.hits().facingYaw(),
                    skill.halfWidth(), skill.halfHeight());
            var result = CombatWaveResolver.resolve(player, wave.hits(), flight.position(),
                    flight.nextPosition(), extents, skill.maximumTargets());
            for (var contact : result.contacts()) {
                if (!wave.hits().markAttempted(CombatTargets.hitId(contact.target()))) continue;
                var damage = CombatDamageService.applyDerived(player, contact.target(),
                        wave.hits().currentMove(), wave.hits());
                if (WAVES.get(entry.getKey()) != wave || !player.isAlive()) break;
                if (damage.successful()) log(player, flight, "hit",
                        "target=" + contact.target().getId() + " damage=" + damage.actualDamage());
            }
            if (WAVES.get(entry.getKey()) != wave) continue;
            flight.advance(result.position(), result.blocked());
            send(player, flight, Phase.DERIVED_WAVE);
            if (flight.waveStopped()) {
                WAVES.remove(entry.getKey());
                // Last server segment is allowed to finish its brief render interpolation.
                log(player, flight, "stopped", "reason=" + (result.blocked() ? "WALL" : "RANGE"));
            }
        }
    }
    public static void forget(LivingEntity player) {
        for (var wave : List.copyOf(WAVES.values())) if (wave.owner().equals(player.getUUID())) {
            WAVES.remove(wave.flight().revision());
            send(player, wave.flight(), Phase.DERIVED_STOPPED);
        }
    }
    public static void clear() { WAVES.clear(); sequence = 0; }
    private static void send(LivingEntity player, CombatSkillSession flight, Phase phase) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new CombatSkillStatePayload(player.getId(),
                flight.skill().id(), flight.revision(), FateActors.level(player).getGameTime(), phase,
                flight.position(), flight.direction(), 0));
    }
    private static void log(LivingEntity player, CombatSkillSession flight, String result, String detail) {
        LoggerFactory.getLogger(DerivedSwordWaves.class).info(
                "DERIVED_QI player={} t={} result={} wave={} skill={} {}", player.getName().getString(),
                FateActors.level(player).getGameTime(), result, flight.revision(), flight.skill().id(), detail);
    }
}
