package com.example.myvillage.region.runtime;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code /myvillage spawn} command surface — a thin query/debug wrapper over
 * {@link RegionRuntimeService}. Ships two literals:
 *
 * <ul>
 *   <li>{@code /myvillage spawn info} — query only: prints the computed spawn
 *       region, the spawn block, and the calling player's current region /
 *       rung / next-rung region set.</li>
 *   <li>{@code /myvillage spawn recompute} — admin/permission-gated: forces a
 *       recompute of spawn for the current world and calls
 *       {@code setDefaultSpawnPos}. This overrides any existing spawn — the
 *       documented admin-override path; the automatic world-load binding
 *       otherwise preserves existing custom spawns.</li>
 * </ul>
 *
 * <p>Registered under the existing {@code /myvillage} dispatcher (which already
 * requires permission level 2), so both subcommands inherit the admin gate.
 */
public final class RegionCommands {

    private RegionCommands() {
    }

    /**
     * {@code /myvillage spawn info} — print the computed spawn region + block
     * and the calling player's current region / rung / next-rung set.
     *
     * @return 1 on success, 0 if the runtime is inactive
     */
    public static int spawnInfo(CommandSourceStack source) throws CommandSyntaxException {
        if (RegionRuntimeService.graph().isEmpty()) {
            source.sendFailure(Component.literal(
                    "Region runtime not loaded for this world (see server log)"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();

        Optional<RegionSpawnSelector.SpawnSelection> spawn = RegionRuntimeService.spawnSelection();
        if (spawn.isPresent()) {
            RegionSpawnSelector.SpawnSelection s = spawn.get();
            source.sendSuccess(() -> Component.literal(
                    "Spawn region: " + s.regionId()
                            + " (center " + s.worldX() + ", *, " + s.worldZ() + ")"),
                    false);
        } else {
            source.sendSuccess(() -> Component.literal(
                    "Spawn region: <none eligible>"), false);
        }

        Optional<RegionRuntimeState> state = RegionRuntimeService.state();
        state.ifPresent(st -> {
            BlockPos bound = st.spawnBlock();
            if (bound != null) {
                source.sendSuccess(() -> Component.literal(
                        "Bound spawn block: " + bound.getX() + " " + bound.getY() + " " + bound.getZ()
                                + " (region " + st.spawnRegionId() + ")"), false);
            }
        });

        Optional<String> region = RegionRuntimeService.currentRegion(player);
        Optional<Integer> rung = RegionRuntimeService.currentRung(player);
        Set<String> next = RegionRuntimeService.nextRungRegions(player);
        Optional<List<Integer>> ladder = RegionRuntimeService.ladder();

        source.sendSuccess(() -> Component.literal(
                "You are in region: " + region.orElse("<outside / unresolved>")), false);
        source.sendSuccess(() -> Component.literal(
                "Current rung (tier): " + rung.map(String::valueOf).orElse("<off-ladder>")), false);
        source.sendSuccess(() -> Component.literal(
                "Next-rung regions: "
                        + (next.isEmpty() ? "<none — at top rung or off-ladder>" : sortedCsv(next))),
                false);
        ladder.ifPresent(l -> source.sendSuccess(() -> Component.literal(
                "Rung ladder (tiers): " + l), false));
        return 1;
    }

    /**
     * {@code /myvillage spawn recompute} — force a spawn recompute for the
     * current world and call {@code setDefaultSpawnPos}, overriding any
     * existing spawn (admin override). Does nothing if the runtime is inactive.
     *
     * @return 1 on success, 0 if the runtime is inactive or no spawn region is eligible
     */
    public static int spawnRecompute(CommandSourceStack source) {
        if (RegionRuntimeService.graph().isEmpty()) {
            source.sendFailure(Component.literal(
                    "Region runtime not loaded for this world (see server log)"));
            return 0;
        }
        boolean ok = RegionRuntimeService.recomputeSpawn();
        if (!ok) {
            source.sendFailure(Component.literal(
                    "Spawn recompute failed (no eligible spawn region)"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Spawn recomputed and set for this world (overrode any existing spawn)"),
                true);
        return 1;
    }

    private static String sortedCsv(Set<String> ids) {
        return ids.stream().sorted().collect(Collectors.joining(", "));
    }
}
