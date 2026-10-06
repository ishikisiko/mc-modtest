package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.CombatService;
import com.example.myvillage.combat.DodgeDirection;
import com.example.myvillage.combat.network.CombatDodgeStartPayload;
import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.TechniqueProgress;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueEffects;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Server-authoritative 身法 dodge. The client sends only the key press and its eight-way movement
 * input; the server decides everything else:
 *
 * <ol>
 *   <li>preconditions: cultivation combat mode, alive, not spectating, sleeping, using an item,
 *       riding or meditating, and on the ground (as an action step);</li>
 *   <li>the technique: the highest-grade learned movement technique with a movement effect (ties go
 *       to the smaller id), whose data gives distance, invulnerable ticks and cooldown;</li>
 *   <li>the per-player cooldown;</li>
 *   <li>a running attack may be cancelled only in its recovery ({@code CombatStopReason.DODGED});</li>
 *   <li>a collision- and support-safe impulse exactly like {@link CombatStepService};</li>
 *   <li>the invulnerable window, during which {@link #onIncomingDamage} cancels damage (except
 *       sources tagged {@code bypasses_invulnerability}) and attack intents are refused;</li>
 *   <li>one mastery point on the technique, the presentation broadcast, sound and particles, and a
 *       {@code DODGE_DEBUG} log line the capture tooling parses.</li>
 * </ol>
 *
 * <p>{@code qi_cost} is read with the rest of the data but not charged in this version.
 */
public final class CombatDodgeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CombatDodgeService.class);

    /** Ground needed within this depth below the destination, like an action step. */
    static final double SUPPORT_DEPTH = 1.0;
    /** The presentation lasts at least this long even with a short (or no) invulnerable window. */
    static final int MINIMUM_DURATION_TICKS = 6;

    private static final Map<UUID, DodgeState> STATES = new HashMap<>();

    private CombatDodgeService() {
    }

    /** Why a dodge intent was refused; the names are the {@code reason=} values of the log line. */
    public enum Rejection {
        STATE,
        MODE,
        AIRBORNE,
        NO_TECHNIQUE,
        COOLDOWN,
        TIMING,
        BLOCKED
    }

    /** The movement technique a dodge uses. */
    public record Choice(ResourceLocation id, int grade, TechniqueEffects.Movement movement) {
        public Choice {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(movement, "movement");
        }
    }

    /**
     * One player's dodge state, all in server game ticks.
     *
     * @param startTick         the tick the dodge started
     * @param invulnerableUntil first tick no longer protected (exclusive end of the window)
     * @param presentationEnd   first tick after the presentation
     * @param readyTick         first tick a new dodge is accepted
     * @param techniqueId       the technique used
     */
    record DodgeState(
            long startTick,
            long invulnerableUntil,
            long presentationEnd,
            long readyTick,
            ResourceLocation techniqueId) {
        boolean expired(long now) {
            return now >= invulnerableUntil && now >= presentationEnd && now >= readyTick;
        }
    }

    /** A dodge key press with the movement input it was pressed with. Returns whether it started. */
    public static boolean handleIntent(ServerPlayer player, DodgeDirection direction) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(direction, "direction");
        long now = player.serverLevel().getGameTime();

        // 1. Preconditions.
        Optional<Rejection> precondition = preconditionFailure(
                player.isAlive() && !player.isRemoved(),
                player.isSpectator() || player.isSleeping() || player.isUsingItem() || player.isPassenger()
                        || MeditationManager.status(player).state().active(),
                CombatService.getMode(player) == CombatMode.CULTIVATION,
                player.onGround());
        if (precondition.isPresent()) {
            return reject(player, now, precondition.get());
        }

        // 2. The technique.
        CultivationProfile profile = CultivationService.getProfile(player);
        Optional<Choice> choice = chooseTechnique(
                profile.learnedTechniques().keySet(),
                id -> ModCultivationRegistries.technique(player.registryAccess(), id));
        if (choice.isEmpty()) {
            return reject(player, now, Rejection.NO_TECHNIQUE);
        }
        TechniqueEffects.Movement movement = choice.get().movement();

        // 3. Cooldown.
        DodgeState previous = STATES.get(player.getUUID());
        if (previous != null && !ready(now, previous.readyTick())) {
            return reject(player, now, Rejection.COOLDOWN);
        }

        // 4. The attack session: only an action's recovery can be cancelled. Checked before the
        // impulse so that a blocked dodge never costs the player a running attack.
        if (!CombatSessionManager.canDodgeCancel(player)) {
            return reject(player, now, Rejection.TIMING);
        }

        // 5. The impulse.
        float yaw = Mth.wrapDegrees(direction.worldYaw(player.getYRot()));
        Vec3 forward = forward(yaw);
        double distance = CombatStepService.safeDistance(player, forward, movement.dashDistance(), SUPPORT_DEPTH);
        if (!(distance > 0.0)) {
            return reject(player, now, Rejection.BLOCKED);
        }
        if (!CombatSessionManager.tryDodgeCancel(player)) {
            return reject(player, now, Rejection.TIMING);
        }
        double speed = CombatStepService.impulseForDistance(distance);
        player.setDeltaMovement(forward.x * speed, 0.0, forward.z * speed);
        player.hurtMarked = true;
        player.setSprinting(false);

        // 6. The window.
        int invulnerableTicks = movement.invulnerableTicks();
        int durationTicks = durationTicks(invulnerableTicks);
        int cooldownTicks = movement.cooldownTicks();
        ResourceLocation techniqueId = choice.get().id();
        STATES.put(player.getUUID(), new DodgeState(
                now,
                windowEnd(now, invulnerableTicks),
                windowEnd(now, durationTicks),
                windowEnd(now, cooldownTicks),
                techniqueId));

        // 7. Mastery.
        CultivationService.Result mastery = CultivationService.updateProfile(player, current -> {
            TechniqueProgress progress = current.learnedTechniques().get(techniqueId);
            long points = progress == null ? 0L : progress.masteryPoints();
            return current.withTechniqueMastery(techniqueId, nextMastery(points));
        });
        if (!mastery.success()) {
            LOGGER.warn("Dodge mastery for {} on {} not raised: {}",
                    player.getGameProfile().getName(), techniqueId, mastery.message());
        }

        // 8. Presentation.
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                new CombatDodgeStartPayload(
                        player.getId(),
                        Math.max(0L, now),
                        yaw,
                        (float) distance,
                        invulnerableTicks,
                        durationTicks,
                        cooldownTicks,
                        techniqueId));
        CombatFeedbackService.dodge(player, forward);

        // 9. Log.
        LOGGER.info(startedLine(
                player.getGameProfile().getName(), now, techniqueId, direction, yaw, distance,
                invulnerableTicks, cooldownTicks));
        return true;
    }

    /** Server tick: ends windows, expires cooldowns, drops offline players. */
    public static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) {
            return;
        }
        STATES.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            return player == null || entry.getValue().expired(player.serverLevel().getGameTime());
        });
    }

    /** Cancels damage to a player inside its invulnerable window. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (STATES.isEmpty() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DamageSource source = event.getSource();
        if (!isDodging(player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        event.setCanceled(true);
        LOGGER.info(cancelledLine(
                player.getGameProfile().getName(),
                player.serverLevel().getGameTime(),
                event.getAmount(),
                source.typeHolder().getRegisteredName()));
    }

    /** True while the player's dodge window (invulnerable ticks) is open. */
    public static boolean isDodging(ServerPlayer player) {
        DodgeState state = STATES.get(player.getUUID());
        return state != null && windowOpen(player.serverLevel().getGameTime(), state.invulnerableUntil());
    }

    /** Drops all dodge state of a player (logout, death, dimension change). */
    public static void clear(UUID playerId) {
        STATES.remove(playerId);
    }

    public static void clearAll() {
        STATES.clear();
    }

    /** For {@code /myvillage combat dodge status}: the technique a dodge would use now, if any. */
    public static Optional<Choice> currentChoice(ServerPlayer player) {
        return chooseTechnique(
                CultivationService.getProfile(player).learnedTechniques().keySet(),
                id -> ModCultivationRegistries.technique(player.registryAccess(), id));
    }

    /** For the status command: ticks until the next dodge is accepted (0 = ready). */
    public static long cooldownRemaining(ServerPlayer player) {
        DodgeState state = STATES.get(player.getUUID());
        return state == null ? 0L : remaining(player.serverLevel().getGameTime(), state.readyTick());
    }

    /** For the status command: ticks of invulnerability left (0 = no window open). */
    public static long windowRemaining(ServerPlayer player) {
        DodgeState state = STATES.get(player.getUUID());
        return state == null ? 0L : remaining(player.serverLevel().getGameTime(), state.invulnerableUntil());
    }

    // ---- Pure rules (unit-tested without a Minecraft instance) ----

    /**
     * The first failed precondition, in log order: state (dead, spectating, sleeping, using an item,
     * riding, meditating), then combat mode, then footing.
     */
    static Optional<Rejection> preconditionFailure(
            boolean alive, boolean busy, boolean cultivationMode, boolean onGround) {
        if (!alive || busy) {
            return Optional.of(Rejection.STATE);
        }
        if (!cultivationMode) {
            return Optional.of(Rejection.MODE);
        }
        if (!onGround) {
            return Optional.of(Rejection.AIRBORNE);
        }
        return Optional.empty();
    }

    /**
     * The learned technique a dodge uses: among those that resolve to a {@code MOVEMENT} technique
     * with an {@code effects.movement} block, the highest grade; equal grades go to the smaller id
     * (string order). Learned ids that are unknown, of another category or without the block are
     * ignored.
     */
    static Optional<Choice> chooseTechnique(
            Collection<ResourceLocation> learned,
            Function<ResourceLocation, Optional<TechniqueDefinition>> lookup) {
        Choice best = null;
        for (ResourceLocation id : learned) {
            Optional<TechniqueDefinition> definition = lookup.apply(id);
            if (definition == null || definition.isEmpty()
                    || definition.get().category() != TechniqueCategory.MOVEMENT) {
                continue;
            }
            Optional<TechniqueEffects.Movement> movement =
                    definition.get().effects().flatMap(TechniqueEffects::movement);
            if (movement.isEmpty()) {
                continue;
            }
            Choice candidate = new Choice(id, definition.get().grade(), movement.get());
            if (best == null || better(candidate, best)) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean better(Choice candidate, Choice best) {
        if (candidate.grade() != best.grade()) {
            return candidate.grade() > best.grade();
        }
        return candidate.id().toString().compareTo(best.id().toString()) < 0;
    }

    /** Ticks the dodge presentation lasts: the invulnerable window, but at least six. */
    static int durationTicks(int invulnerableTicks) {
        return Math.max(invulnerableTicks, MINIMUM_DURATION_TICKS);
    }

    /** Exclusive end tick of a window of {@code ticks} starting at {@code start} (saturating). */
    static long windowEnd(long start, int ticks) {
        long length = Math.max(0, ticks);
        return start > Long.MAX_VALUE - length ? Long.MAX_VALUE : start + length;
    }

    /** Whether {@code now} lies in the window that ends (exclusively) at {@code end}. */
    static boolean windowOpen(long now, long end) {
        return now < end;
    }

    /** Whether a new dodge is accepted at {@code now}. */
    static boolean ready(long now, long readyTick) {
        return now >= readyTick;
    }

    /** Ticks from {@code now} until {@code end}, never negative. */
    static long remaining(long now, long end) {
        return Math.max(0L, end - now);
    }

    /** One more mastery point, saturating. */
    static long nextMastery(long points) {
        return points == Long.MAX_VALUE ? points : points + 1L;
    }

    /** Horizontal unit vector of a world yaw (Minecraft convention: 0 = +Z, 90 = -X). */
    static Vec3 forward(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    static String startedLine(
            String player,
            long tick,
            ResourceLocation technique,
            DodgeDirection direction,
            float yaw,
            double distance,
            int invulnerableTicks,
            int cooldownTicks) {
        return String.format(
                Locale.ROOT,
                "DODGE_DEBUG player=%s t=%d result=started technique=%s dir=%s yaw=%.2f distance=%.2f invuln=%d cooldown=%d",
                player, tick, technique, direction.name(), yaw, distance, invulnerableTicks, cooldownTicks);
    }

    static String rejectedLine(String player, long tick, Rejection reason) {
        return String.format(
                Locale.ROOT, "DODGE_DEBUG player=%s t=%d result=rejected reason=%s", player, tick, reason.name());
    }

    static String cancelledLine(String player, long tick, float amount, String damageType) {
        return String.format(
                Locale.ROOT,
                "DODGE_DEBUG player=%s t=%d cancelled_damage=%.2f source=%s",
                player, tick, amount, damageType);
    }

    private static boolean reject(ServerPlayer player, long now, Rejection reason) {
        LOGGER.info(rejectedLine(player.getGameProfile().getName(), now, reason));
        return false;
    }
}
