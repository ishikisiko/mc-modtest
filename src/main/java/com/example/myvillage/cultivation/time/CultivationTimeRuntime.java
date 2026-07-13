package com.example.myvillage.cultivation.time;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class CultivationTimeRuntime {
    public static final int COMMIT_INTERVAL_TICKS = 600;
    static final int EXHAUSTED_WARNING_MARKER = -1;

    private static final Logger LOGGER = LoggerFactory.getLogger(CultivationTimeRuntime.class);
    private static final Map<UUID, PendingLifespan> PENDING_LIFESPAN = new HashMap<>();
    private static final Map<UUID, Set<Integer>> DELIVERED_WARNINGS = new HashMap<>();
    private static final Map<UUID, Long> WARNING_MAXIMUM_TICKS = new HashMap<>();
    private static final Map<UUID, Long> WARNING_LAST_CONSUMED_TICKS = new HashMap<>();
    private static final Set<String> REPORTED_SCALE_ERRORS = new HashSet<>();

    private static BiConsumer<ServerPlayer, CultivationTimeStatus> statusListener = (player, status) -> {
    };
    private static int activeCalendarTicksSinceCheckpoint;

    private CultivationTimeRuntime() {
    }

    public static void onServerStarted(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        PENDING_LIFESPAN.clear();
        DELIVERED_WARNINGS.clear();
        WARNING_MAXIMUM_TICKS.clear();
        WARNING_LAST_CONSUMED_TICKS.clear();
        REPORTED_SCALE_ERRORS.clear();
        activeCalendarTicksSinceCheckpoint = 0;
        CultivationCalendarSavedData.get(server.overworld());
        CultivationServerConfig.warnAboutRawTickReinterpretation("activated");
    }

    public static void tick(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        boolean calendarAdvances = server.getPlayerList().getPlayers().stream()
                .anyMatch(CultivationTimeRuntime::isSurvivalOrAdventure);
        boolean calendarCheckpoint = false;
        if (calendarAdvances) {
            CultivationCalendarSavedData calendar = CultivationCalendarSavedData.get(server.overworld());
            calendar.incrementSaturated();
            activeCalendarTicksSinceCheckpoint++;
            if (activeCalendarTicksSinceCheckpoint >= COMMIT_INTERVAL_TICKS) {
                activeCalendarTicksSinceCheckpoint = 0;
                calendar.checkpoint();
                calendarCheckpoint = true;
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isEligibleForPersonalLifespan(player)) {
                PendingLifespan pending = PENDING_LIFESPAN.computeIfAbsent(
                        player.getUUID(), ignored -> new PendingLifespan());
                pending.addEligibleTick();
                if (pending.ticksSinceAttempt() >= COMMIT_INTERVAL_TICKS) {
                    pending.resetAttemptInterval();
                    flushPlayer(player);
                    emitWarningIfNeeded(player);
                }
            }
        }
        if (calendarCheckpoint) {
            notifyAllPlayers(server);
        }
    }

    public static void onPlayerLoggedIn(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        DELIVERED_WARNINGS.put(player.getUUID(), new HashSet<>());
        WARNING_MAXIMUM_TICKS.remove(player.getUUID());
        WARNING_LAST_CONSUMED_TICKS.remove(player.getUUID());
        flushPlayer(player);
        emitWarningIfNeeded(player);
        notifyStatus(player);
    }

    public static void onPlayerRespawn(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        flushPlayer(player);
        notifyStatus(player);
    }

    public static void onPlayerChangedDimension(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        flushPlayer(player);
        notifyStatus(player);
    }

    public static void onPlayerDeath(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        flushPlayer(player);
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        flushPlayer(player);
        DELIVERED_WARNINGS.remove(player.getUUID());
        WARNING_MAXIMUM_TICKS.remove(player.getUUID());
        WARNING_LAST_CONSUMED_TICKS.remove(player.getUUID());
    }

    public static void onServerSave(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        boolean committedPendingLifespan = false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            long pending = pendingTicks(player.getUUID());
            if (pending > 0 && flushPlayer(player)) {
                committedPendingLifespan = true;
            }
        }
        CultivationCalendarSavedData calendar = CultivationCalendarSavedData.get(server.overworld());
        calendar.checkpoint();
        server.overworld().getDataStorage().save();
        if (committedPendingLifespan) {
            server.getPlayerList().saveAll();
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!flushPlayer(player)) {
                LOGGER.error(
                        "Unable to commit {} pending cultivation lifespan ticks for {} during clean stop",
                        pendingTicks(player.getUUID()),
                        player.getGameProfile().getName());
            }
        }
        CultivationCalendarSavedData.get(server.overworld()).checkpoint();
        PENDING_LIFESPAN.clear();
        DELIVERED_WARNINGS.clear();
        WARNING_MAXIMUM_TICKS.clear();
        WARNING_LAST_CONSUMED_TICKS.clear();
        REPORTED_SCALE_ERRORS.clear();
        activeCalendarTicksSinceCheckpoint = 0;
    }

    public static boolean flushPlayer(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        PendingLifespan pending = PENDING_LIFESPAN.get(player.getUUID());
        if (pending == null || pending.ticks() == 0) {
            return true;
        }
        long amount = pending.ticks();
        CultivationService.Result result;
        try {
            result = CultivationService.addLifespanConsumedTicks(player, amount);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Cultivation lifespan commit threw for {}; retaining {} pending ticks",
                    player.getGameProfile().getName(),
                    amount,
                    exception);
            return false;
        }
        if (!result.success()) {
            LOGGER.warn(
                    "Cultivation lifespan commit for {} failed; retaining {} pending ticks: {}",
                    player.getGameProfile().getName(),
                    amount,
                    result.message());
            return false;
        }
        pending.removeCommitted(amount);
        if (pending.ticks() == 0) {
            PENDING_LIFESPAN.remove(player.getUUID());
        }
        return true;
    }

    public static CultivationTimeStatus statusFor(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        CultivationServerConfig.Scale scale = CultivationServerConfig.scale();
        CultivationProfile profile = CultivationService.getProfile(player);
        long consumed = CultivationTimeMath.saturatingAdd(
                profile.lifespanConsumedTicks(), pendingTicks(player.getUUID()));
        long calendarTicks = CultivationCalendarSavedData.get(player.getServer().overworld())
                .elapsedCalendarTicks();

        Optional<Registry<RealmDefinition>> registry =
                player.registryAccess().registry(ModCultivationRegistries.REALMS);
        RealmDefinition realm = registry.map(value -> value.get(profile.realmId())).orElse(null);
        if (realm == null) {
            return unresolvedStatus(calendarTicks, consumed, scale);
        }

        try {
            long maximum = scale.maximumLifespanTicks(realm.maximumLifespanYears());
            long remaining = CultivationTimeMath.remainingTicks(consumed, maximum);
            return new CultivationTimeStatus(
                    calendarTicks,
                    consumed,
                    scale.ticksPerDay(),
                    scale.daysPerYear(),
                    true,
                    maximum,
                    remaining,
                    consumed >= maximum);
        } catch (ArithmeticException exception) {
            reportScaleErrorOnce(profile.realmId().toString(), realm.maximumLifespanYears(), scale, exception);
            return unresolvedStatus(calendarTicks, consumed, scale);
        }
    }

    public static boolean isLifespanExhausted(ServerPlayer player) {
        CultivationTimeStatus status = statusFor(player);
        return status.realmResolved() && status.exhausted();
    }

    public static long pendingTicks(UUID playerId) {
        PendingLifespan pending = PENDING_LIFESPAN.get(playerId);
        return pending == null ? 0 : pending.ticks();
    }

    public static void installStatusListener(
            BiConsumer<ServerPlayer, CultivationTimeStatus> listener) {
        statusListener = Objects.requireNonNull(listener, "listener");
    }

    public static void clearStatusListener() {
        statusListener = (player, status) -> {
        };
    }

    public static void notifyStatus(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        try {
            statusListener.accept(player, statusFor(player));
        } catch (RuntimeException exception) {
            LOGGER.error("Cultivation time status listener failed for {}",
                    player.getGameProfile().getName(), exception);
        }
    }

    public static void onScaleReloaded() {
        REPORTED_SCALE_ERRORS.clear();
        DELIVERED_WARNINGS.clear();
        WARNING_MAXIMUM_TICKS.clear();
        WARNING_LAST_CONSUMED_TICKS.clear();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            MeditationManager.cancelAllAdministrative(
                    server, MeditationStopReason.CONFIG_RELOADED);
            notifyAllPlayers(server);
        }
    }

    public static boolean isEligibleForPersonalLifespan(ServerPlayer player) {
        return player != null
                && isSurvivalOrAdventure(player)
                && player.isAlive()
                && !player.isRemoved();
    }

    private static boolean isSurvivalOrAdventure(ServerPlayer player) {
        return player.gameMode.isSurvival();
    }

    private static CultivationTimeStatus unresolvedStatus(
            long calendarTicks,
            long consumedTicks,
            CultivationServerConfig.Scale scale) {
        return new CultivationTimeStatus(
                calendarTicks,
                consumedTicks,
                scale.ticksPerDay(),
                scale.daysPerYear(),
                false,
                0,
                0,
                false);
    }

    private static void emitWarningIfNeeded(ServerPlayer player) {
        CultivationTimeStatus status = statusFor(player);
        Set<Integer> delivered = DELIVERED_WARNINGS.computeIfAbsent(
                player.getUUID(), ignored -> new HashSet<>());
        if (status.realmResolved()) {
            Long previousMaximum = WARNING_MAXIMUM_TICKS.put(
                    player.getUUID(), status.maximumLifespanTicks());
            Long previousConsumed = WARNING_LAST_CONSUMED_TICKS.put(
                    player.getUUID(), status.effectiveLifespanConsumedTicks());
            if ((previousMaximum != null && previousMaximum != status.maximumLifespanTicks())
                    || (previousConsumed != null
                        && previousConsumed > status.effectiveLifespanConsumedTicks())) {
                delivered.clear();
            }
        }
        int threshold = warningMarker(status);
        if (threshold == EXHAUSTED_WARNING_MARKER) {
            if (delivered.add(EXHAUSTED_WARNING_MARKER)) {
                player.sendSystemMessage(Component.translatable(
                        "message.myvillage.cultivation.lifespan_exhausted"));
            }
            return;
        }
        if (threshold == 0) {
            return;
        }
        if (delivered.contains(threshold)) {
            return;
        }
        for (int candidate : new int[]{10, 5, 1}) {
            if (candidate >= threshold) {
                delivered.add(candidate);
            }
        }
        player.sendSystemMessage(Component.translatable(
                "message.myvillage.cultivation.lifespan_warning", threshold));
    }

    static int warningMarker(CultivationTimeStatus status) {
        Objects.requireNonNull(status, "status");
        if (!status.realmResolved()) {
            return 0;
        }
        if (status.exhausted()) {
            return EXHAUSTED_WARNING_MARKER;
        }
        return CultivationTimeMath
                .mostUrgentWarning(status.remainingLifespanTicks(), status.ticksPerYear())
                .orElse(0);
    }

    private static void notifyAllPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            notifyStatus(player);
        }
    }

    private static void reportScaleErrorOnce(
            String realmId,
            int years,
            CultivationServerConfig.Scale scale,
            ArithmeticException exception) {
        String key = realmId + ':' + years + ':' + scale.ticksPerDay() + ':' + scale.daysPerYear();
        if (REPORTED_SCALE_ERRORS.add(key)) {
            LOGGER.error(
                    "Cultivation lifespan scale overflow for realm {}: years={}, ticks_per_day={}, "
                            + "days_per_year={}; lifespan status is unavailable until corrected",
                    realmId,
                    years,
                    scale.ticksPerDay(),
                    scale.daysPerYear(),
                    exception);
        }
    }

    static final class PendingLifespan {
        private long ticks;
        private int ticksSinceAttempt;

        void addEligibleTick() {
            ticks = CultivationTimeMath.saturatingAdd(ticks, 1);
            if (ticksSinceAttempt < COMMIT_INTERVAL_TICKS) {
                ticksSinceAttempt++;
            }
        }

        long ticks() {
            return ticks;
        }

        int ticksSinceAttempt() {
            return ticksSinceAttempt;
        }

        void resetAttemptInterval() {
            ticksSinceAttempt = 0;
        }

        void removeCommitted(long committed) {
            if (committed < 0 || committed > ticks) {
                throw new IllegalArgumentException("Invalid committed lifespan amount " + committed);
            }
            ticks -= committed;
        }
    }
}
