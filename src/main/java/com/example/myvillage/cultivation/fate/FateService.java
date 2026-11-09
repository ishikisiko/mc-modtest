package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.CultivationAttachments;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.time.CultivationServerConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public final class FateService {
    private static final Map<UUID, Long> REQUESTS = new HashMap<>();
    private FateService() { }
    public static FateState state(ServerPlayer player) { return player.getData(CultivationAttachments.FATES.get()); }
    public static FateState state(net.minecraft.world.entity.LivingEntity actor) {
        return actor instanceof ServerPlayer player ? state(player)
                : actor instanceof com.example.myvillage.entity.npc.NpcEntity npc
                    ? com.example.myvillage.entity.npc.NpcFateRuntime.state(npc) : FateState.empty();
    }
    public static int budget() { return CultivationServerConfig.fateBudget(); }
    public static Map<String, FateDefinition> definitions(RegistryAccess registries) {
        Map<String, FateDefinition> result = new TreeMap<>();
        registries.registry(ModCultivationRegistries.INNATE_FATES).ifPresent(r ->
                r.keySet().forEach(id -> result.put(id.toString(), r.get(id))));
        return Collections.unmodifiableMap(result);
    }
    public static Map<String, FateDefinition> eligible(ServerPlayer player) {
        var root = CultivationService.getProfile(player).spiritualRoot();
        Map<String, FateDefinition> result = new TreeMap<>();
        definitions(player.registryAccess()).forEach((id, def) -> { if (def.eligible(root)) result.put(id, def); });
        return result;
    }
    public static Map<String, FateDefinition> active(ServerPlayer player) {
        if (!state(player).confirmed()) return Map.of();
        var root = CultivationService.getProfile(player).spiritualRoot();
        Map<String, FateDefinition> result = new TreeMap<>();
        definitions(player.registryAccess()).forEach((id, def) -> { if (def.active(root)) result.put(id, def); });
        return result;
    }
    public static Map<String, FateDefinition> active(net.minecraft.world.entity.LivingEntity actor) {
        if (actor instanceof ServerPlayer player) return active(player);
        if (!FateActors.supported(actor) || !state(actor).confirmed()) return Map.of();
        Map<String,FateDefinition> result = new TreeMap<>();
        definitions(actor.registryAccess()).forEach((id,definition) -> { if (definition.enabled()) result.put(id,definition); });
        return result;
    }
    public static double effect(net.minecraft.world.entity.LivingEntity actor, String effect) {
        if (actor instanceof ServerPlayer player) return effect(player,effect);
        var state=state(actor);if(!state.confirmed())return 0;
        var definitions=active(actor);
        return state.selected().stream().map(definitions::get).filter(Objects::nonNull).mapToDouble(d->d.effect(effect)).sum();
    }
    public static double effect(ServerPlayer player, String effect) {
        FateState state = state(player); if (!state.confirmed()) return 0;
        var definitions = active(player);
        return state.selected().stream().map(definitions::get).filter(Objects::nonNull)
                .mapToDouble(d -> d.effect(effect)).sum();
    }
    /** Only the server-side stele interaction initializes a missing draft, never an existing one. */
    public static void initializeDraft(ServerPlayer player) {
        if (!SteleAwakeningSessions.valid(player, SteleAwakeningSessions.currentToken(player))
                || CultivationService.getProfile(player).spiritualRoot().isEmpty()) return;
        FateState current = state(player);
        if (current.confirmed() || !current.candidates().isEmpty()) return;
        try {
            FateState replacement = FateSelection.initializeDraft(current, eligible(player), budget(),
                    FateRules.bundled(), random(player, current));
            if (replacement != current) CultivationService.replaceFates(player, replacement, true);
        } catch (IllegalArgumentException | ArithmeticException e) {
            player.displayClientMessage(Component.translatable("fate.myvillage.error.unavailable"), false);
        }
    }

    /** A server chooses all values; value is only a slot, unlock sentinel or six-bit mask. */
    public static boolean intent(ServerPlayer player, int action, long revision, int value, long sessionToken) {
        return request(player,action,revision,value,SteleAwakeningSessions.valid(player,sessionToken),"");
    }
    public static boolean intent(ServerPlayer player,int action,long revision,int value,long sessionToken,String school) {
        return request(player, action, revision, value, SteleAwakeningSessions.valid(player, sessionToken),school);
    }

    /** Explicit permission-checked administrator fixture path; never called by a network handler. */
    public static boolean diagnosticIntent(CommandSourceStack source, ServerPlayer player,
            int action, long revision, int value) {
        if (!source.hasPermission(2)) return false;
        return diagnosticIntent(source,player,action,revision,value,"");
    }
    public static boolean diagnosticIntent(CommandSourceStack source,ServerPlayer player,int action,long revision,int value,String school) {
        if (!source.hasPermission(2)) return false;
        return request(player, action, revision, value, true,school);
    }

    private static boolean request(ServerPlayer player, int action, long revision, int value, boolean authorized,String school) {
        long now = player.getServer().overworld().getGameTime();
        long next = REQUESTS.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (now < next) return false;
        REQUESTS.put(player.getUUID(), now + FateRules.bundled().requestTicks());
        String error = "";
        FateState current = state(player);
        try {
            if (!authorized) error = "session";
            else if (!player.isAlive() || player.isRemoved() || player.isSpectator()) error = "state";
            else if (current.confirmed()) error = "confirmed";
            else if (revision != current.revision()) error = "stale";
            else if (CultivationService.getProfile(player).spiritualRoot().isEmpty()) error = "root";
            else {
                Map<String, FateDefinition> pool = eligible(player);
                FateState replacement = FateSelection.transition(current, action, revision, value,
                        pool, budget(), FateRules.bundled(), random(player, current));
                if (action==2) CultivationService.confirmFates(player,replacement,
                        SchoolInsight.confirm(replacement.selected(),pool,school,SchoolInsightRuntime.schools(player.registryAccess()).keySet()));
                else {
                    if(!school.isEmpty())throw new IllegalArgumentException("school");
                    CultivationService.replaceFates(player, replacement, true);
                }
                return true;
            }
        } catch (IllegalArgumentException | ArithmeticException e) {
            error = Set.of("school", "confirmed", "stale", "count", "budget", "exclusive", "intent", "locked", "unavailable")
                    .contains(String.valueOf(e.getMessage())) ? e.getMessage() : "unavailable";
        }
        player.displayClientMessage(Component.translatable("fate.myvillage.error." + error), false);
        CultivationService.syncToClient(player);
        return false;
    }
    private static Random random(ServerPlayer player, FateState current) {
        long sequence = Math.addExact(current.rolls(), 1);
        long seed = player.getServer().overworld().getSeed() ^ player.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 17) ^ sequence * 0x9E3779B97F4A7C15L;
        return new Random(seed);
    }
    public static void forget(ServerPlayer player) {
        REQUESTS.remove(player.getUUID());
        TemperedBonesRuntime.forget(player);
        SwordObsessionRuntime.forget(player);
        SwordHeartRuntime.forget(player);
        WindStepRuntime.clear(player);
        FireSpiritRuntime.forget(player);
        SteleAwakeningSessions.forget(player);
    }
    public static void stop() { REQUESTS.clear(); TemperedBonesRuntime.stop(); SwordObsessionRuntime.clear();
        SwordHeartRuntime.stop(); WindStepRuntime.clearAll(); FireSpiritRuntime.clear(); }
}
