package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.CultivationAttachments;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.time.CultivationServerConfig;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public final class FateService {
    private static final Map<UUID, Long> REQUESTS = new HashMap<>();
    private FateService() { }
    public static FateState state(ServerPlayer player) { return player.getData(CultivationAttachments.FATES.get()); }
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
    public static double effect(ServerPlayer player, String effect) {
        FateState state = state(player); if (!state.confirmed()) return 0;
        var definitions = eligible(player);
        return state.selected().stream().map(definitions::get).filter(Objects::nonNull)
                .mapToDouble(d -> d.effect(effect)).sum();
    }
    /** A server chooses all values; value is only a slot, unlock sentinel or six-bit mask. */
    public static boolean intent(ServerPlayer player, int action, long revision, int value) {
        long now = player.getServer().overworld().getGameTime();
        long next = REQUESTS.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (now < next) return false;
        REQUESTS.put(player.getUUID(), now + FateRules.bundled().requestTicks());
        String error = "";
        FateState current = state(player);
        try {
            if (!player.isAlive() || player.isSpectator()) error = "state";
            else if (current.confirmed()) error = "confirmed";
            else if (revision != current.revision()) error = "stale";
            else if (CultivationService.getProfile(player).spiritualRoot().isEmpty()) error = "root";
            else {
                Map<String, FateDefinition> pool = eligible(player);
                long sequence = Math.addExact(current.rolls(), 1);
                long seed = player.getServer().overworld().getSeed() ^ player.getUUID().getMostSignificantBits()
                        ^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 17) ^ sequence * 0x9E3779B97F4A7C15L;
                FateState replacement = FateSelection.transition(current, action, revision, value,
                        pool, budget(), FateRules.bundled(), new Random(seed));
                CultivationService.replaceFates(player, replacement, true);
                return true;
            }
        } catch (IllegalArgumentException | ArithmeticException e) {
            error = Set.of("confirmed", "stale", "count", "budget", "high", "exclusive", "intent", "locked", "unavailable")
                    .contains(String.valueOf(e.getMessage())) ? e.getMessage() : "unavailable";
        }
        player.displayClientMessage(Component.translatable("fate.myvillage.error." + error), false);
        CultivationService.syncToClient(player);
        return false;
    }
    public static void forget(ServerPlayer player) { REQUESTS.remove(player.getUUID()); }
    public static void stop() { REQUESTS.clear(); }
}
