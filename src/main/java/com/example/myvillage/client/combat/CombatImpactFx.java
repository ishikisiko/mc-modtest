package com.example.myvillage.client.combat;

import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.example.myvillage.combat.definition.MoveFeedback;
import com.example.myvillage.combat.network.CombatImpactPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Client-side presentation of a landed hit, driven by the server's {@link CombatImpactPayload}
 * (sent to the attacker and everyone tracking it). Nothing here changes gameplay state:
 * <ul>
 *     <li>Struck non-player entities skip their client tick for the move's hit-stop (the server
 *     freezes the same mobs for the same number of ticks), so animation, hurt flash countdown and
 *     position interpolation hold still.</li>
 *     <li>Every struck entity except the local player shudders along the strike for the stop:
 *     about ±0.03 blocks on light hits, ±0.06 on heavy hits, decaying to rest.</li>
 *     <li>A remote attacker's third-person animation stops with the blade: rate 0 for the first
 *     60% of the stop, 0.15 for the rest, then back to 1. The local attacker's own hit-stop and
 *     camera trauma come from the attacker-only hit confirm instead, so nothing is counted twice.</li>
 *     <li>The attacker's world 剑光 freezes for the same stop.</li>
 * </ul>
 */
public final class CombatImpactFx {
    static final float FROZEN_SHARE = 0.6F;
    static final float CREEP_RATE = 0.15F;
    static final float LIGHT_JITTER = 0.03F;
    static final float HEAVY_JITTER = 0.06F;
    /** About 12 Hz: reads as a shudder at 60 fps rather than noise. */
    private static final float JITTER_RADIANS_PER_TICK = 3.8F;
    private static final double STALE_TICKS = 40.0;

    private static final Map<Integer, TargetStop> TARGETS = new HashMap<>();
    private static final Map<Integer, AttackerStop> ATTACKERS = new HashMap<>();
    /** Entities whose pose we pushed in RenderLivingEvent.Pre and must pop in Post. */
    private static final Set<Integer> PUSHED = new HashSet<>();

    private CombatImpactFx() {
    }

    static void receive(CombatImpactPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null
                || payload.moveIndex() < 0
                || payload.moveIndex() >= BasicSwordStyle.DEFINITION.moves().size()) {
            return;
        }
        MoveFeedback feedback = BasicSwordStyle.feedback(payload.moveIndex());
        float stopTicks = feedback.hitStopTicks();
        double now = level.getGameTime();
        // Entities that stopped ticking or rendering (unloaded, out of range) never clear
        // their own entry; drop anything long finished.
        TARGETS.values().removeIf(stop -> now >= stop.endTime() + STALE_TICKS || stop.entity().isRemoved());
        Entity attacker = level.getEntity(payload.attackerEntityId());
        Vec3 attackerPosition = attacker == null ? null : attacker.position();

        for (int index = 0; index < payload.struckEntityIds().size(); index++) {
            int entityId = payload.struckEntityIds().get(index);
            Entity target = level.getEntity(entityId);
            if (target == null || target == minecraft.player) {
                continue;
            }
            Vec3 contact = payload.contactPoints().get(index);
            double directionX = 1.0;
            double directionZ = 0.0;
            if (attackerPosition != null) {
                double dx = contact.x - attackerPosition.x;
                double dz = contact.z - attackerPosition.z;
                double length = Math.sqrt(dx * dx + dz * dz);
                if (length > 1.0E-4) {
                    directionX = dx / length;
                    directionZ = dz / length;
                }
            }
            TARGETS.put(entityId, new TargetStop(
                    target,
                    now,
                    Math.round(stopTicks),
                    stopTicks,
                    feedback.heavyHit() ? HEAVY_JITTER : LIGHT_JITTER,
                    (float) directionX,
                    (float) directionZ,
                    !(target instanceof Player)));
        }

        if (attacker != null) {
            CombatWorldTrails.hitStop(attacker.getId(), now, stopTicks);
            if (attacker instanceof AbstractClientPlayer player && attacker != minecraft.player && stopTicks > 0.0F) {
                ATTACKERS.put(attacker.getId(), new AttackerStop(player, now, stopTicks));
            }
        }
    }

    static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (AttackerStop stop : ATTACKERS.values()) {
            if (minecraft.level != null && stop.player().level() == minecraft.level) {
                CombatAnimationController.setHitStopRate(stop.player(), 1.0F);
            }
        }
        TARGETS.clear();
        ATTACKERS.clear();
        PUSHED.clear();
    }

    /** Skips the client tick of a frozen struck entity. Never freezes the local player. */
    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (TARGETS.isEmpty()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!entity.level().isClientSide()) {
            return;
        }
        TargetStop stop = TARGETS.get(entity.getId());
        if (stop == null || stop.entity() != entity) {
            return;
        }
        long now = entity.level().getGameTime();
        if (now >= stop.endTime()) {
            TARGETS.remove(entity.getId());
            return;
        }
        if (stop.freeze() && now < stop.startTime() + stop.freezeTicks()) {
            event.setCanceled(true);
        }
    }

    /** Shudders a struck entity's rendered pose along the strike while its hit-stop runs. */
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (TARGETS.isEmpty() || event.isCanceled()) {
            return;
        }
        Entity entity = event.getEntity();
        TargetStop stop = TARGETS.get(entity.getId());
        if (stop == null || stop.entity() != entity) {
            return;
        }
        double now = entity.level().getGameTime() + event.getPartialTick();
        float offset = jitterOffset((float) (now - stop.startTime()), stop.shakeTicks(), stop.amplitude());
        float across = jitterCross((float) (now - stop.startTime()), stop.shakeTicks(), stop.amplitude());
        if (offset == 0.0F && across == 0.0F) {
            return;
        }
        event.getPoseStack().pushPose();
        PUSHED.add(entity.getId());
        event.getPoseStack().translate(
                stop.directionX() * offset - stop.directionZ() * across,
                0.0F,
                stop.directionZ() * offset + stop.directionX() * across);
    }

    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (!PUSHED.isEmpty() && PUSHED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }

    /** Drives each remote attacker's third-person animation rate through its hit-stop. */
    public static void onRenderFramePre(RenderFrameEvent.Pre event) {
        if (ATTACKERS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ATTACKERS.clear();
            return;
        }
        double now = minecraft.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Iterator<Map.Entry<Integer, AttackerStop>> iterator = ATTACKERS.entrySet().iterator();
        while (iterator.hasNext()) {
            AttackerStop stop = iterator.next().getValue();
            boolean gone = stop.player().isRemoved() || stop.player().level() != minecraft.level;
            float rate = gone ? 1.0F : hitStopRate((float) (now - stop.startTime()), stop.stopTicks());
            if (rate != stop.lastRate) {
                if (!gone) {
                    CombatAnimationController.setHitStopRate(stop.player(), rate);
                }
                stop.lastRate = rate;
            }
            if (gone || now - stop.startTime() >= stop.stopTicks()) {
                if (!gone && stop.lastRate != 1.0F) {
                    CombatAnimationController.setHitStopRate(stop.player(), 1.0F);
                }
                iterator.remove();
            }
        }
    }

    /**
     * Animation rate {@code elapsed} ticks into a hit-stop of {@code stopTicks}: 0 for the first
     * 60%, 0.15 for the rest, 1 before and after.
     */
    static float hitStopRate(float elapsed, float stopTicks) {
        if (stopTicks <= 0.0F || elapsed < 0.0F || elapsed >= stopTicks) {
            return 1.0F;
        }
        return elapsed < stopTicks * FROZEN_SHARE ? 0.0F : CREEP_RATE;
    }

    /** Animation time lost to a hit-stop after {@code elapsed} ticks: the integral of (1 - rate). */
    static float hitStopLostTicks(float elapsed, float stopTicks) {
        if (stopTicks <= 0.0F || elapsed <= 0.0F) {
            return 0.0F;
        }
        float frozenEnd = stopTicks * FROZEN_SHARE;
        float clamped = Math.min(elapsed, stopTicks);
        if (clamped <= frozenEnd) {
            return clamped;
        }
        return frozenEnd + (clamped - frozenEnd) * (1.0F - CREEP_RATE);
    }

    /** Along-strike shudder: a bite forward then recoil, decaying linearly to 0 by the stop's end. */
    static float jitterOffset(float elapsed, float stopTicks, float amplitude) {
        float decay = jitterDecay(elapsed, stopTicks);
        return decay == 0.0F ? 0.0F : (float) Math.sin(elapsed * JITTER_RADIANS_PER_TICK) * amplitude * decay;
    }

    static float jitterCross(float elapsed, float stopTicks, float amplitude) {
        float decay = jitterDecay(elapsed, stopTicks);
        return decay == 0.0F
                ? 0.0F
                : (float) Math.sin(elapsed * JITTER_RADIANS_PER_TICK * 1.7F + 1.3F) * amplitude * 0.35F * decay;
    }

    private static float jitterDecay(float elapsed, float stopTicks) {
        if (stopTicks <= 0.0F || elapsed < 0.0F || elapsed >= stopTicks) {
            return 0.0F;
        }
        return 1.0F - elapsed / stopTicks;
    }

    private record TargetStop(
            Entity entity,
            double startTime,
            int freezeTicks,
            float shakeTicks,
            float amplitude,
            float directionX,
            float directionZ,
            boolean freeze) {
        /** First game tick at which neither the freeze nor the shudder is running. */
        double endTime() {
            return startTime + Math.max(freezeTicks, Math.ceil(shakeTicks));
        }
    }

    private static final class AttackerStop {
        private final AbstractClientPlayer player;
        private final double startTime;
        private final float stopTicks;
        private float lastRate = 1.0F;

        private AttackerStop(AbstractClientPlayer player, double startTime, float stopTicks) {
            this.player = player;
            this.startTime = startTime;
            this.stopTicks = stopTicks;
        }

        AbstractClientPlayer player() {
            return player;
        }

        double startTime() {
            return startTime;
        }

        float stopTicks() {
            return stopTicks;
        }
    }
}
