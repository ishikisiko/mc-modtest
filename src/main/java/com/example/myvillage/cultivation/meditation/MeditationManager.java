package com.example.myvillage.cultivation.meditation;

import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.combat.session.CombatStopReason;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.AdvancementDefinition;
import com.example.myvillage.cultivation.data.AdvancementKind;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.time.CultivationServerConfig;
import com.example.myvillage.cultivation.time.CultivationTimeMath;
import com.example.myvillage.cultivation.time.CultivationTimeRuntime;
import com.example.myvillage.cultivation.time.CultivationTimeStatus;
import com.example.myvillage.item.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class MeditationManager {
    public static final int PREPARATION_TICKS = 40;
    public static final int RECENT_DAMAGE_TICKS = 100;
    public static final int DUPLICATE_FEEDBACK_INTERVAL_TICKS = 20;
    public static final int ADVANCEMENT_FEEDBACK_INTERVAL_TICKS = 20;
    public static final double MOVEMENT_TOLERANCE = 0.01D;

    private static final Logger LOGGER = LoggerFactory.getLogger(MeditationManager.class);
    private static final Set<MeditationStopReason> PENALIZED_INTERRUPTION_REASONS = EnumSet.of(
            MeditationStopReason.REQUESTED,
            MeditationStopReason.NOT_ON_GROUND,
            MeditationStopReason.MOUNTED,
            MeditationStopReason.SWIMMING,
            MeditationStopReason.FLYING,
            MeditationStopReason.SLEEPING,
            MeditationStopReason.USING_ITEM,
            MeditationStopReason.MOVED,
            MeditationStopReason.JUMPED,
            MeditationStopReason.DAMAGED,
            MeditationStopReason.ATTACKED,
            MeditationStopReason.MINING,
            MeditationStopReason.INTERACTED,
            MeditationStopReason.GAME_MODE_CHANGED,
            MeditationStopReason.DIMENSION_CHANGED,
            MeditationStopReason.DIED,
            MeditationStopReason.LOGGED_OUT);

    private static final Map<UUID, MeditationSession> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_POSITIVE_DAMAGE_TICK = new HashMap<>();
    private static final Set<UUID> SETTLEMENTS_IN_PROGRESS = new HashSet<>();

    private static BiConsumer<ServerPlayer, MeditationStatus> statusListener = (player, status) -> {
    };
    private static long serverTick;

    private MeditationManager() {
    }

    public static MeditationStatus requestStart(ServerPlayer player, MeditationMode mode) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(mode, "mode");
        MeditationSession existing = SESSIONS.get(player.getUUID());
        if (existing != null) {
            return duplicateStatus(player, existing);
        }

        MeditationStopReason rejection = eligibilityFailure(player);
        if (rejection == MeditationStopReason.NONE
                && resolveCurrentStage(player, CultivationService.getProfile(player))
                .flatMap(resolved -> resolved.stage().cultivationCap())
                .isEmpty()) {
            rejection = MeditationStopReason.STAGE_NOT_CULTIVATABLE;
        }
        if (rejection != MeditationStopReason.NONE) {
            return reject(player, rejection);
        }

        CultivationProfile profile = CultivationService.getProfile(player);
        MeditationSession session = new MeditationSession(
                mode,
                profile.realmId(),
                profile.stageId(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.level().dimension());
        CombatSessionManager.interrupt(player, CombatStopReason.CULTIVATION_STARTED, true);
        SESSIONS.put(player.getUUID(), session);
        MeditationStatus accepted = session.status(MeditationStopReason.START_ACCEPTED);
        notifyStatus(player, accepted);
        return accepted;
    }

    public static MeditationStatus requestAdvancement(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        MeditationSession existing = SESSIONS.get(player.getUUID());
        if (existing != null) {
            return duplicateStatus(player, existing);
        }

        MeditationStopReason rejection = eligibilityFailure(player);
        CultivationProfile profile = CultivationService.getProfile(player);
        ResolvedStage resolved = null;
        AdvancementDefinition definition = null;
        long cap = 0;
        if (rejection == MeditationStopReason.NONE) {
            resolved = resolveCurrentStage(player, profile).orElse(null);
            if (resolved == null
                    || resolved.stage().cultivationCap().isEmpty()
                    || resolved.stage().advancement().isEmpty()) {
                rejection = MeditationStopReason.ADVANCEMENT_UNAVAILABLE;
            } else {
                cap = resolved.stage().cultivationCap().orElseThrow();
                definition = resolved.stage().advancement().orElseThrow();
            }
        }
        if (rejection == MeditationStopReason.NONE && !targetExists(player, definition)) {
            rejection = MeditationStopReason.ADVANCEMENT_TARGET_UNAVAILABLE;
        }
        if (rejection == MeditationStopReason.NONE && profile.cultivationProgress() < cap) {
            rejection = MeditationStopReason.ADVANCEMENT_PROGRESS_REQUIRED;
        }
        if (rejection == MeditationStopReason.NONE
                && profile.stability() < definition.requiredStability()) {
            rejection = MeditationStopReason.ADVANCEMENT_STABILITY_REQUIRED;
        }
        if (rejection != MeditationStopReason.NONE) {
            return reject(player, rejection);
        }

        AdvancementContext context = new AdvancementContext(
                profile.realmId(), profile.stageId(), cap, definition);
        MeditationSession session = MeditationSession.advancement(
                context,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.level().dimension());
        CombatSessionManager.interrupt(player, CombatStopReason.CULTIVATION_STARTED, true);
        SESSIONS.put(player.getUUID(), session);
        MeditationStatus accepted = session.status(MeditationStopReason.ADVANCEMENT_ACCEPTED);
        notifyStatus(player, accepted);
        return accepted;
    }

    public static boolean requestStop(ServerPlayer player, MeditationStopReason reason) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reason, "reason");
        MeditationSession removed = SESSIONS.remove(player.getUUID());
        if (removed == null) {
            return false;
        }
        applyInterruptionPenalty(player, removed, reason);
        notifyStatus(player, MeditationStatus.idle(reason));
        return true;
    }

    public static void cancelAllAdministrative(
            MinecraftServer server, MeditationStopReason reason) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(reason, "reason");
        if (PENALIZED_INTERRUPTION_REASONS.contains(reason)) {
            throw new IllegalArgumentException(
                    "Administrative session teardown cannot use a penalized reason: " + reason);
        }
        for (UUID playerId : SESSIONS.keySet().toArray(UUID[]::new)) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                SESSIONS.remove(playerId);
            } else {
                requestStop(player, reason);
            }
        }
    }

    public static MeditationStatus status(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return status(player.getUUID());
    }

    public static MeditationStatus status(UUID playerId) {
        MeditationSession session = SESSIONS.get(Objects.requireNonNull(playerId, "playerId"));
        return session == null
                ? MeditationStatus.idle(MeditationStopReason.NONE)
                : session.status(MeditationStopReason.NONE);
    }

    public static void tick(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        serverTick = CultivationTimeMath.saturatingAdd(serverTick, 1);
        for (UUID playerId : SESSIONS.keySet().toArray(UUID[]::new)) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                SESSIONS.remove(playerId);
                continue;
            }
            MeditationSession session = SESSIONS.get(playerId);
            MeditationStopReason interruption = continuingFailure(player, session);
            if (interruption != MeditationStopReason.NONE) {
                requestStop(player, interruption);
                continue;
            }
            if (player.swinging) {
                requestStop(player, MeditationStopReason.ATTACKED);
                continue;
            }
            if (session.state().preparing()) {
                if (session.advancePreparation()) {
                    notifyStatus(player, session.status(MeditationStopReason.PREPARATION_COMPLETE));
                }
            } else if (session.state().meditating()) {
                if (session.advanceMeditationTick()) {
                    settleMeditation(player, session);
                }
            } else if (session.state().advancing()) {
                if (session.advanceAdvancementTick()) {
                    completeAdvancement(player, session);
                } else if (session.shouldReportAdvancementProgress()) {
                    notifyStatus(player, session.status(MeditationStopReason.NONE));
                }
            }
        }
        LAST_POSITIVE_DAMAGE_TICK.entrySet().removeIf(entry ->
                serverTick - entry.getValue() >= RECENT_DAMAGE_TICKS);
    }

    public static void recordPositiveDamage(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        LAST_POSITIVE_DAMAGE_TICK.put(player.getUUID(), serverTick);
        requestStop(player, MeditationStopReason.DAMAGED);
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        requestStop(player, MeditationStopReason.LOGGED_OUT);
    }

    public static void onServerStarted() {
        SESSIONS.clear();
        LAST_POSITIVE_DAMAGE_TICK.clear();
        SETTLEMENTS_IN_PROGRESS.clear();
        serverTick = 0;
    }

    public static void onServerStopping(MinecraftServer server) {
        cancelAllAdministrative(server, MeditationStopReason.SERVER_STOPPING);
        SESSIONS.clear();
        LAST_POSITIVE_DAMAGE_TICK.clear();
        SETTLEMENTS_IN_PROGRESS.clear();
        serverTick = 0;
    }

    public static void installStatusListener(BiConsumer<ServerPlayer, MeditationStatus> listener) {
        statusListener = Objects.requireNonNull(listener, "listener");
    }

    public static void clearStatusListener() {
        statusListener = (player, status) -> {
        };
    }

    public static void notifyStatus(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        notifyStatus(player, status(player));
    }

    private static MeditationStatus duplicateStatus(
            ServerPlayer player, MeditationSession existing) {
        MeditationStatus duplicate = existing.status(MeditationStopReason.DUPLICATE_START);
        if (existing.allowDuplicateFeedback(serverTick)) {
            notifyStatus(player, duplicate);
        }
        return duplicate;
    }

    private static MeditationStatus reject(ServerPlayer player, MeditationStopReason reason) {
        MeditationStatus status = MeditationStatus.idle(reason);
        notifyStatus(player, status);
        return status;
    }

    private static void settleMeditation(ServerPlayer player, MeditationSession session) {
        UUID playerId = player.getUUID();
        if (!beginSettlement(playerId)) {
            LOGGER.error("Reentrant cultivation settlement rejected for {}",
                    player.getGameProfile().getName());
            return;
        }
        try {
            settleMeditationGuarded(player, session);
        } finally {
            endSettlement(playerId);
        }
    }

    static boolean beginSettlement(UUID playerId) {
        return SETTLEMENTS_IN_PROGRESS.add(Objects.requireNonNull(playerId, "playerId"));
    }

    static void endSettlement(UUID playerId) {
        SETTLEMENTS_IN_PROGRESS.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    private static void settleMeditationGuarded(ServerPlayer player, MeditationSession session) {
        CultivationProfile current = CultivationService.getProfile(player);
        ResolvedStage resolved = resolveCurrentStage(player, current).orElse(null);
        if (resolved == null || resolved.stage().cultivationCap().isEmpty()) {
            requestStop(player, MeditationStopReason.STAGE_NOT_CULTIVATABLE);
            return;
        }

        BasicBreathingSettlement.Accrual accrual;
        long cap = resolved.stage().cultivationCap().orElseThrow();
        int spiritStoneCost = 0;
        boolean spiritStonesAvailable = false;
        if (session.mode() == MeditationMode.SPIRIT && current.cultivationProgress() < cap) {
            spiritStoneCost = resolved.stage().spiritStoneCost().orElse(0);
            if (spiritStoneCost <= 0) {
                requestStop(player, MeditationStopReason.SETTLEMENT_FAILED);
                return;
            }
            spiritStonesAvailable = InventoryBatchRemoval.has(
                    player.getInventory(), ModItems.LOW_GRADE_SPIRIT_STONE.get(), spiritStoneCost);
        }

        BasicBreathingSettlement.Plan plan;
        try {
            accrual = BasicBreathingSettlement.accrue(
                    session.settlementRemainders(),
                    BasicBreathingSettlement.SETTLEMENT_INTERVAL_TICKS,
                    CultivationServerConfig.scale().ticksPerYear());
            plan = BasicBreathingSettlement.plan(
                    current,
                    cap,
                    session.mode(),
                    accrual,
                    spiritStonesAvailable);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            LOGGER.warn("Cultivation settlement validation failed for {}",
                    player.getGameProfile().getName(), exception);
            requestStop(player, MeditationStopReason.SETTLEMENT_FAILED);
            return;
        }

        if (plan.consumeSpiritStones()) {
            int requiredSpiritStoneCost = spiritStoneCost;
            CultivationService.Result[] serviceResult = new CultivationService.Result[1];
            InventoryBatchRemoval.TransactionResult<InventoryBatchRemoval.Removal> transaction =
                    InventoryBatchRemoval.transact(
                            () -> InventoryBatchRemoval.remove(
                                    player.getInventory(),
                                    ModItems.LOW_GRADE_SPIRIT_STONE.get(),
                                    requiredSpiritStoneCost),
                            removed -> InventoryBatchRemoval.restore(player.getInventory(), removed),
                            () -> {
                                serviceResult[0] = CultivationService.replaceProfile(
                                        player, plan.replacement());
                                return serviceResult[0].success();
                            },
                            () -> CultivationService.getProfile(player).equals(plan.replacement()));
            if (!transaction.committed()) {
                if (transaction.failure() != null) {
                    LOGGER.error(
                            transaction.state()
                                            == InventoryBatchRemoval.TransactionState.INSTALL_FAILED_AFTER_COMMIT
                                    ? "Cultivation settlement installed for {}, but later commit work threw"
                                    : "Cultivation settlement commit threw for {}",
                            player.getGameProfile().getName(),
                            transaction.failure());
                } else if (serviceResult[0] != null) {
                    LOGGER.warn("Cultivation settlement commit failed for {}: {}",
                            player.getGameProfile().getName(), serviceResult[0].message());
                }
                requestStop(player, MeditationStopReason.SETTLEMENT_FAILED);
                return;
            }
        } else if (plan.profileChangedFrom(current)) {
            CultivationService.Result result;
            try {
                result = CultivationService.replaceProfile(player, plan.replacement());
            } catch (RuntimeException exception) {
                LOGGER.error("Cultivation settlement commit threw for {}",
                        player.getGameProfile().getName(), exception);
                requestStop(player, MeditationStopReason.SETTLEMENT_FAILED);
                return;
            }
            if (!result.success()) {
                LOGGER.warn("Cultivation settlement commit failed for {}: {}",
                        player.getGameProfile().getName(), result.message());
                requestStop(player, MeditationStopReason.SETTLEMENT_FAILED);
                return;
            }
        }

        session.settlementSucceeded(accrual.remainders());
        if (plan.downgradeToNormal()) {
            session.downgradeToNormal();
            notifyStatus(player, session.status(MeditationStopReason.SPIRIT_RESOURCES_EXHAUSTED));
        }
    }

    private static void completeAdvancement(ServerPlayer player, MeditationSession session) {
        AdvancementContext context = session.advancementContext().orElseThrow();
        CultivationProfile current = CultivationService.getProfile(player);
        MeditationStopReason failure = finalAdvancementFailure(player, current, context);
        if (failure != MeditationStopReason.NONE) {
            requestStop(player, failure);
            return;
        }

        CultivationProfile replacement = AdvancementProfileTransition.successReplacement(
                current, context);
        SESSIONS.remove(player.getUUID());
        try {
            CultivationService.Result result = CultivationService.replaceProfile(player, replacement);
            if (!result.success()) {
                LOGGER.warn("Cultivation advancement commit failed for {}: {}",
                        player.getGameProfile().getName(), result.message());
                notifyStatus(player, MeditationStatus.idle(MeditationStopReason.ADVANCEMENT_INVALIDATED));
                return;
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Cultivation advancement commit threw for {}",
                    player.getGameProfile().getName(), exception);
            notifyStatus(player, MeditationStatus.idle(MeditationStopReason.ADVANCEMENT_INVALIDATED));
            return;
        }
        notifyStatus(player, MeditationStatus.idle(MeditationStopReason.ADVANCEMENT_COMPLETED));
    }

    private static MeditationStopReason finalAdvancementFailure(
            ServerPlayer player,
            CultivationProfile profile,
            AdvancementContext context) {
        if (!profile.realmId().equals(context.sourceRealm())
                || !profile.stageId().equals(context.sourceStage())) {
            return MeditationStopReason.ADVANCEMENT_INVALIDATED;
        }
        ResolvedStage resolved = resolveCurrentStage(player, profile).orElse(null);
        if (resolved == null
                || !resolved.stage().cultivationCap().equals(Optional.of(context.cultivationCap()))
                || !resolved.stage().advancement().equals(Optional.of(context.definition()))) {
            return MeditationStopReason.ADVANCEMENT_INVALIDATED;
        }
        if (!targetExists(player, context.definition())) {
            return MeditationStopReason.ADVANCEMENT_TARGET_UNAVAILABLE;
        }
        if (profile.cultivationProgress() < context.cultivationCap()) {
            return MeditationStopReason.ADVANCEMENT_PROGRESS_REQUIRED;
        }
        if (profile.stability() < context.definition().requiredStability()) {
            return MeditationStopReason.ADVANCEMENT_STABILITY_REQUIRED;
        }
        return MeditationStopReason.NONE;
    }

    private static void applyInterruptionPenalty(
            ServerPlayer player,
            MeditationSession session,
            MeditationStopReason reason) {
        AdvancementContext context = session.advancementContext().orElse(null);
        if (context == null
                || context.definition().kind() != AdvancementKind.BOTTLENECK
                || context.definition().interruptionStabilityLoss() == 0
                || !PENALIZED_INTERRUPTION_REASONS.contains(reason)) {
            return;
        }
        CultivationProfile current = CultivationService.getProfile(player);
        if (!current.realmId().equals(context.sourceRealm())
                || !current.stageId().equals(context.sourceStage())) {
            return;
        }
        CultivationProfile replacement = AdvancementProfileTransition
                .interruptionPenaltyReplacement(current, context);
        if (replacement.equals(current)) {
            return;
        }
        try {
            CultivationService.Result result = CultivationService.replaceProfile(
                    player, replacement);
            if (!result.success()) {
                LOGGER.warn("Cultivation advancement interruption penalty failed for {}: {}",
                        player.getGameProfile().getName(), result.message());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Cultivation advancement interruption penalty threw for {}",
                    player.getGameProfile().getName(), exception);
        }
    }

    private static Optional<ResolvedStage> resolveCurrentStage(
            ServerPlayer player, CultivationProfile profile) {
        Optional<Registry<RealmDefinition>> realms =
                player.registryAccess().registry(ModCultivationRegistries.REALMS);
        RealmDefinition realm = realms.map(registry -> registry.get(profile.realmId())).orElse(null);
        if (realm == null) {
            return Optional.empty();
        }
        return realm.stages().stream()
                .filter(stage -> stage.id().equals(profile.stageId()))
                .findFirst()
                .map(stage -> new ResolvedStage(realm, stage));
    }

    private static boolean targetExists(
            ServerPlayer player, AdvancementDefinition definition) {
        Optional<Registry<RealmDefinition>> realms =
                player.registryAccess().registry(ModCultivationRegistries.REALMS);
        RealmDefinition targetRealm = realms
                .map(registry -> registry.get(definition.targetRealm()))
                .orElse(null);
        return targetRealm != null && targetRealm.containsStage(definition.targetStage());
    }

    private static MeditationStopReason eligibilityFailure(ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved()) {
            return MeditationStopReason.NOT_ALIVE;
        }
        if (!player.gameMode.isSurvival()) {
            return MeditationStopReason.INVALID_GAME_MODE;
        }
        CultivationProfile profile = CultivationService.getProfile(player);
        if (profile.spiritualRoot().isEmpty()) {
            return MeditationStopReason.NOT_AWAKENED;
        }
        if (!profile.learnedTechniques().containsKey(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)) {
            return MeditationStopReason.BASIC_BREATHING_REQUIRED;
        }
        if (!basicBreathingDefinitionEligible(player, profile)) {
            return MeditationStopReason.BASIC_BREATHING_UNAVAILABLE;
        }
        CultivationTimeStatus timeStatus = CultivationTimeRuntime.statusFor(player);
        if (!timeStatus.realmResolved()) {
            return MeditationStopReason.LIFESPAN_UNAVAILABLE;
        }
        if (timeStatus.exhausted()) {
            return MeditationStopReason.LIFESPAN_EXHAUSTED;
        }
        MeditationStopReason physicalFailure = physicalFailure(player);
        if (physicalFailure != MeditationStopReason.NONE) {
            return physicalFailure;
        }
        Long lastDamage = LAST_POSITIVE_DAMAGE_TICK.get(player.getUUID());
        if (lastDamage != null && serverTick - lastDamage < RECENT_DAMAGE_TICKS) {
            return MeditationStopReason.RECENT_DAMAGE;
        }
        return MeditationStopReason.NONE;
    }

    private static MeditationStopReason continuingFailure(
            ServerPlayer player, MeditationSession session) {
        if (!player.isAlive() || player.isRemoved()) {
            return MeditationStopReason.DIED;
        }
        if (!player.gameMode.isSurvival()) {
            return MeditationStopReason.GAME_MODE_CHANGED;
        }
        if (!session.dimension().equals(player.level().dimension())) {
            return MeditationStopReason.DIMENSION_CHANGED;
        }
        if (session.moved(player.getX(), player.getY(), player.getZ())) {
            return MeditationStopReason.MOVED;
        }
        CultivationProfile profile = CultivationService.getProfile(player);
        if (profile.spiritualRoot().isEmpty()) {
            return MeditationStopReason.NOT_AWAKENED;
        }
        if (!profile.learnedTechniques().containsKey(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)) {
            return MeditationStopReason.BASIC_BREATHING_REQUIRED;
        }
        if (!basicBreathingDefinitionEligible(player, profile)) {
            return MeditationStopReason.BASIC_BREATHING_UNAVAILABLE;
        }
        CultivationTimeStatus timeStatus = CultivationTimeRuntime.statusFor(player);
        if (!timeStatus.realmResolved()) {
            return MeditationStopReason.LIFESPAN_UNAVAILABLE;
        }
        if (timeStatus.exhausted()) {
            return MeditationStopReason.LIFESPAN_EXHAUSTED;
        }
        ResolvedStage resolved = resolveCurrentStage(player, profile).orElse(null);
        if (session.state().meditating()
                && !session.matchesSource(profile.realmId(), profile.stageId())) {
            return MeditationStopReason.PROFILE_INVALIDATED;
        }
        if (session.state().meditating()
                && (resolved == null || resolved.stage().cultivationCap().isEmpty())) {
            return MeditationStopReason.STAGE_NOT_CULTIVATABLE;
        }
        if (session.state().advancing()) {
            AdvancementContext context = session.advancementContext().orElseThrow();
            if (!profile.realmId().equals(context.sourceRealm())
                    || !profile.stageId().equals(context.sourceStage())
                    || resolved == null
                    || !resolved.stage().cultivationCap().equals(Optional.of(context.cultivationCap()))
                    || !resolved.stage().advancement().equals(Optional.of(context.definition()))) {
                return MeditationStopReason.ADVANCEMENT_INVALIDATED;
            }
        }
        return physicalFailure(player);
    }

    private static boolean basicBreathingDefinitionEligible(
            ServerPlayer player, CultivationProfile profile) {
        Optional<Registry<TechniqueDefinition>> techniques =
                player.registryAccess().registry(ModCultivationRegistries.TECHNIQUES);
        TechniqueDefinition definition = techniques
                .map(registry -> registry.get(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID))
                .orElse(null);
        return definition != null
                && BasicBreathingEligibility.requirementsSatisfied(
                player.registryAccess(), profile, definition);
    }

    private static MeditationStopReason physicalFailure(ServerPlayer player) {
        if (!player.onGround()) {
            return MeditationStopReason.NOT_ON_GROUND;
        }
        if (player.isPassenger()) {
            return MeditationStopReason.MOUNTED;
        }
        if (player.isSwimming()) {
            return MeditationStopReason.SWIMMING;
        }
        if (player.isFallFlying() || player.getAbilities().flying) {
            return MeditationStopReason.FLYING;
        }
        if (player.isSleeping()) {
            return MeditationStopReason.SLEEPING;
        }
        if (player.isUsingItem()) {
            return MeditationStopReason.USING_ITEM;
        }
        return MeditationStopReason.NONE;
    }

    private static void notifyStatus(ServerPlayer player, MeditationStatus status) {
        try {
            statusListener.accept(player, status);
        } catch (RuntimeException exception) {
            LOGGER.error("Meditation status listener failed for {}",
                    player.getGameProfile().getName(), exception);
        }
    }

    private record ResolvedStage(RealmDefinition realm, RealmStageDefinition stage) {
    }

}
