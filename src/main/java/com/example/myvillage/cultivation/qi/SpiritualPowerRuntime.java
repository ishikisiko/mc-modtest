package com.example.myvillage.cultivation.qi;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import com.example.myvillage.cultivation.technique.CoreTechniqueFactor;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-only timers. Current qi remains exclusively in the existing persisted profile. */
public final class SpiritualPowerRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpiritualPowerRuntime.class);
    private static final ResourceLocation RULES = ResourceLocation.fromNamespaceAndPath("myvillage", "qi/rules.json");
    private static final Map<UUID, QiRegeneration> TIMERS = new HashMap<>();
    private static final Map<UUID, Integer> RESET_TICKS = new HashMap<>();
    private static final QiRulesStore RULE_STORE = new QiRulesStore();

    private SpiritualPowerRuntime() { }

    public static void onServerStarted(MinecraftServer server) {
        TIMERS.clear();
        RESET_TICKS.clear();
        RULE_STORE.clear();
        RULE_STORE.load(() -> load(server));
    }

    private static QiRules load(MinecraftServer server) {
        try (var reader = server.getResourceManager().getResource(RULES)
                .orElseThrow(() -> new IllegalStateException("Missing " + RULES)).openAsReader()) {
            return QiRules.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot load spiritual-power rules " + RULES, exception);
        }
    }

    public static void reload(MinecraftServer server) {
        try {
            RULE_STORE.load(() -> load(server));
        } catch (RuntimeException exception) {
            LOGGER.error("Spiritual-power rule reload rejected; retaining previous rules", exception);
            return;
        }
        TIMERS.clear();
        RESET_TICKS.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) initialize(player);
    }

    public static void initialize(ServerPlayer player) {
        TIMERS.put(player.getUUID(), new QiRegeneration(rules()));
        RESET_TICKS.put(player.getUUID(), player.serverLevel().getServer().getTickCount());
        CultivationService.clampSpiritualPower(player);
    }

    public static void forget(ServerPlayer player) {
        TIMERS.remove(player.getUUID());
        RESET_TICKS.remove(player.getUUID());
    }

    public static void stop() {
        TIMERS.clear();
        RESET_TICKS.clear();
        RULE_STORE.clear();
    }

    public static void spent(ServerPlayer player) {
        TIMERS.computeIfAbsent(player.getUUID(), ignored -> new QiRegeneration(rules())).spent(rules());
        RESET_TICKS.put(player.getUUID(), player.serverLevel().getServer().getTickCount());
    }

    public static void tick(MinecraftServer server) {
        QiRules currentRules = rules();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            CultivationService.clampSpiritualPower(player);
            CultivationProfile profile = CultivationService.getProfile(player);
            long cap = CultivationService.spiritualPowerCap(player);
            if (!TIMERS.containsKey(player.getUUID())) initialize(player);
            // A spend may occur before this tick's Post event: do not count that same tick as elapsed.
            if (RESET_TICKS.getOrDefault(player.getUUID(), Integer.MIN_VALUE) == server.getTickCount()) continue;
            QiRegeneration timer = TIMERS.get(player.getUUID());
            MeditationStatus status = MeditationManager.status(player);
            boolean meditating = status.state().meditating() && status.study().isEmpty() && status.practice().isEmpty();
            int coreFactor = player.registryAccess().registry(ModCultivationRegistries.TECHNIQUES)
                    .map(registry -> CoreTechniqueFactor.progressBasisPoints(profile, registry::get,
                            WorldSimRuntime.data().map(data -> data.rules())))
                    .orElse(CoreTechniqueFactor.UNIT_BASIS_POINTS);
            int growthFactor = profile.activeCoreTechnique().map(id -> com.example.myvillage.cultivation.growth.TechniqueGrowthRuntime.multiplier(
                    player, id, com.example.myvillage.cultivation.growth.TechniqueGrowthRules.Effect.RECOVERY)).orElse(CoreTechniqueFactor.UNIT_BASIS_POINTS);
            coreFactor = (int) Math.min(Integer.MAX_VALUE, (long) coreFactor * growthFactor / CoreTechniqueFactor.UNIT_BASIS_POINTS);
            long recovered = timer.tick(currentRules, coreFactor, meditating, profile.currentSpiritualPower() >= cap);
            if (recovered > 0) CultivationService.recoverSpiritualPower(player, recovered);
        }
    }

    public static int recoveryTicksRemaining(ServerPlayer player) {
        QiRegeneration timer = TIMERS.get(player.getUUID());
        return timer == null ? rules().spendDelayTicks() : timer.ticksRemaining();
    }

    private static QiRules rules() {
        return RULE_STORE.get();
    }
}
