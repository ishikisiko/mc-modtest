package com.example.myvillage.sim.runtime.avatar;

import com.example.myvillage.sect.SectCourtyard;
import com.example.myvillage.sect.SectGenerator;
import com.example.myvillage.sim.SectView;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.WorldSimServerConfig;
import com.example.myvillage.sim.runtime.WorldSimText;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code /myvillage world sect <id> build [here]}: builds a sect's compound (山门) where the ledger
 * puts its gate and records the realization. The ledger stays the authority: the build follows the
 * sect's record, never the other way round, and {@code here} moves the ledger's gate first.
 *
 * <p>At the gate, the anchor's y is the surface (motion-blocking, leaves ignored) at the gate x/z,
 * sampled after loading that chunk. The compound is the worldgen-style one (derived mountain,
 * terraces, grand stairs and buildings; {@link SectGenerator#generateForcedAt}); its seed hashes the world
 * seed with the sect id and its spire variant is picked per sect by hash, so a rebuild at the same
 * place builds the same compound. The build is synchronous on the server thread.
 */
public final class GateBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(GateBuilder.class);

    private GateBuilder() {
    }

    /** Seed of a sect's compound in this world. */
    public static long seed(long worldSeed, int sectId) {
        return AvatarPlanner.mix(worldSeed ^ AvatarPlanner.mix(0x5EC7_0000L + sectId));
    }

    /** Spire variant of a sect's compound ({@code "none"} or a variant name). */
    public static String variant(long worldSeed, int sectId) {
        List<String> variants = SectCourtyard.variants();
        return variants.get((int) Math.floorMod(AvatarPlanner.mix(seed(worldSeed, sectId) ^ 0x7A11L), (long) variants.size()));
    }

    public static int build(CommandSourceStack source, int sectId, boolean here) {
        Optional<WorldSim> active = WorldSimRuntime.sim();
        if (active.isEmpty()) {
            String reason = WorldSimRuntime.inactiveReason();
            source.sendFailure(WorldSimText.line("inactive", reason == null ? "?" : reason));
            return 0;
        }
        WorldSim sim = active.get();
        Optional<SectView> found = sim.sect(sectId);
        if (found.isEmpty()) {
            source.sendFailure(WorldSimText.line("sect.not_found", String.valueOf(sectId)));
            return 0;
        }
        SectView sect = found.get();
        if (GateRealizer.busy(sectId)) {
            source.sendFailure(WorldSimText.line("gates.busy", sect.name()));
            return 0;
        }
        if (!sect.state().equals("active")) {
            source.sendFailure(WorldSimText.line("sect_build.destroyed", sect.name()));
            return 0;
        }
        ServerLevel level = source.getLevel();
        if (level.dimension() != Level.OVERWORLD) {
            source.sendFailure(WorldSimText.line("sect_build.overworld_only"));
            return 0;
        }
        BlockPos anchor;
        if (here) {
            anchor = BlockPos.containing(source.getPosition());
            try {
                sim.moveGate(sectId, anchor.getX(), anchor.getZ());
            } catch (IllegalArgumentException ex) {
                source.sendFailure(WorldSimText.line("sect_build.outside", sect.name(), anchor.getX(), anchor.getZ()));
                return 0;
            }
            sim.markGateRealized(sectId, false);
            markLedgerDirty(level);
            String region = WorldSimRuntime.regionName(sim.sect(sectId).map(SectView::regionId).orElse(""));
            source.sendSuccess(() -> WorldSimText.line("sect_build.moved", sect.name(), anchor.getX(), anchor.getZ(),
                    region), true);
        } else {
            int x = sect.gateX();
            int z = sect.gateZ();
            level.getChunk(x >> 4, z >> 4); // loads (or generates) the chunk so its heightmap is real
            anchor = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        }
        long seed = seed(level.getSeed(), sectId);
        String variant = variant(level.getSeed(), sectId);
        source.sendSuccess(() -> WorldSimText.line("sect_build.start", sect.name(), anchor.getX(), anchor.getY(),
                anchor.getZ(), String.valueOf(seed), variant), true);
        long t0 = System.nanoTime();
        int built;
        try {
            built = SectGenerator.generateForcedAt(source, seed, variant, anchor);
        } catch (RuntimeException ex) {
            LOGGER.error("Building the compound of sect {} at {} failed", sectId, anchor, ex);
            built = 0;
        }
        long seconds = Math.round((System.nanoTime() - t0) / 1e9);
        if (built <= 0) {
            source.sendFailure(WorldSimText.line("sect_build.failed", sect.name()));
            return 0;
        }
        GateRealizations.get(level).put(new GateRealizations.Gate(sectId, anchor, seed, variant, sim.day()));
        placeShelves(level, sectId, seed, anchor, variant);
        sim.markGateRealized(sectId, true);
        markLedgerDirty(level);
        WorldSimAvatars.gateChanged(sectId);
        LOGGER.info("World sim: built the compound of sect {} ({}) at {} {} {}, seed {}, variant {}, in {} s",
                sectId, sect.name(), anchor.getX(), anchor.getY(), anchor.getZ(), seed, variant, seconds);
        source.sendSuccess(() -> WorldSimText.line("sect_build.done", sect.name(), anchor.getX(), anchor.getY(),
                anchor.getZ(), seconds, WorldSimServerConfig.avatarSpawnRadius()), true);
        return 1;
    }

    /** The sect's scripture shelves ({@link ScriptureShelves}); a failure there never fails the build. */
    static void placeShelves(ServerLevel level, int sectId, long seed, BlockPos anchor, String variant) {
        try {
            ScriptureShelves.place(level, sectId, seed, anchor, variant);
        } catch (RuntimeException ex) {
            LOGGER.warn("SCRIPTURE_SHELF sect={} placement failed", sectId, ex);
        }
    }

    /** The ledger payload changed outside a settled day; make sure the next world save writes it. */
    private static void markLedgerDirty(ServerLevel overworld) {
        WorldSimSavedData.get(overworld).setDirty();
    }
}
