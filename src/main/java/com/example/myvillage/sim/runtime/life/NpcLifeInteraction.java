package com.example.myvillage.sim.runtime.life;

import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.life.LifeResidentView;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.avatar.WorldSimAvatars;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Small server-owned conversations backed by actual activity and persistent acquaintance records. */
public final class NpcLifeInteraction {
    private record Conversation(int personId, UUID player) { }
    /** Prevents a repeated use packet from starting another conversation or extending its pause. */
    private static final Map<Conversation, Long> UNTIL = new HashMap<>();
    private static final Set<String> ACTIVITIES = Set.of("rest", "work", "gather", "deliver", "cultivate", "social");
    private static final Set<String> PHASES = Set.of("moving", "working", "waiting");
    private static final Set<String> REASONS = Set.of("facility_missing", "target_blocked", "path_unavailable",
            "path_timeout", "stuck", "target_unloaded", "too_far", "stale_target", "registered",
            "injured", "night", "tired", "sell_herbs", "prepare_medicine", "inn_service", "earn_living",
            "guide_service", "meet_neighbors", "cultivate", "idle_rest", "no_available_place", "cannot_afford_cultivation", "combat", "combat_return", "combat_reload", "combat_recovery", "unknown");

    private NpcLifeInteraction() { }

    /** True only for a managed resident; an existing sect-role dialogue may still be opened afterward. */
    public static boolean interact(ServerPlayer player, NpcEntity npc) {
        long epoch = WorldSimAvatars.lifeEpoch(npc);
        if (epoch < 0) return false;
        WorldSim sim = WorldSimRuntime.sim().orElse(null);
        if (sim == null || npc.isRemoved() || !npc.isAlive() || !player.isAlive() || player.isSpectator()
                || player.level() != npc.level() || player.getServer() == null) {
            return true;
        }
        LifeResidentView life = sim.life(npc.ledgerPersonId()).orElse(null);
        if (life == null || !life.embodied() || life.epoch() != epoch) return true;
        double range = life.role().equals("guide") ? com.example.myvillage.exploration.ExplorationRules.get().settings().guideRange()
                : WorldSimRuntime.data().map(d -> d.rules().player().steward().interactRange()).orElse(0.0);
        if (player.distanceToSqr(npc) > range * range || !player.hasLineOfSight(npc)) return true;

        long now = player.getServer().overworld().getGameTime();
        UNTIL.values().removeIf(until -> until <= now);
        Conversation conversation = new Conversation(life.personId(), player.getUUID());
        if (UNTIL.containsKey(conversation)) return true;
        UNTIL.put(conversation, now + (life.role().equals("guide")
                ? com.example.myvillage.exploration.ExplorationRules.get().settings().guideCooldownTicks() : sim.lifeRules().conversationPauseTicks()));

        String playerId = player.getUUID().toString();
        sim.registerPlayerActor(playerId, player.getGameProfile().getName());
        var context = sim.conversationContext(life.personId(), playerId);
        String name = sim.person(life.personId()).map(p -> p.name()).orElse("");
        sim.rememberConversation(life.personId(), playerId);
        sim.pauseLife(life.personId(), sim.lifeRules().conversationPauseTicks());
        npc.getNavigation().stop();
        npc.getLookControl().setLookAt(player, 30.0F, 30.0F);
        WorldSimSavedData.get(player.getServer().overworld()).setDirty();

        if (life.role().equals("guide")) {
            com.example.myvillage.exploration.ExplorationRuntime.guide(player, Component.literal(name), false);
            return true;
        }

        player.sendSystemMessage(Component.translatable(context.metBefore()
                ? "message.myvillage.life.greeting.known" : "message.myvillage.life.greeting.first",
                name, context.familiarity()));
        player.sendSystemMessage(Component.translatable("message.myvillage.life.status", name,
                activityText(life.activity()), phaseText(life)));
        if (!life.reason().isEmpty() && !"none".equals(life.reason())) {
            String reason = REASONS.contains(life.reason()) ? life.reason() : "unknown";
            player.sendSystemMessage(Component.translatable("message.myvillage.life.reason." + reason));
        }
        return true;
    }

    public static Component activityText(String activity) {
        return Component.translatable("message.myvillage.life.activity."
                + (ACTIVITIES.contains(activity) ? activity : "rest"));
    }

    public static Component phaseText(LifeResidentView life) {
        String phase = life.pauseTicks() > 0 ? "talking" : PHASES.contains(life.phase()) ? life.phase() : "waiting";
        return Component.translatable("message.myvillage.life.phase." + phase);
    }

    public static Component stateText(LifeResidentView life) {
        return Component.translatable("message.myvillage.life.state", activityText(life.activity()), phaseText(life));
    }

    public static void clear() {
        UNTIL.clear();
    }
}
