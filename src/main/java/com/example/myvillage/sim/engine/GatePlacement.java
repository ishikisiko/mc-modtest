package com.example.myvillage.sim.engine;

import com.example.myvillage.region.runtime.GenRegion;
import com.example.myvillage.region.runtime.RegionPlacement;
import com.example.myvillage.region.runtime.RegionQueries;
import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.data.Rules;
import com.example.myvillage.sim.model.Sect;
import java.util.Optional;

/**
 * Chooses a sect's gate (山门) coordinate once, at founding: a chunk-centred block inside the home
 * region per {@link RegionQueries#regionAt}, within the rules' radius of the origin and at least the
 * rules' spacing from every other gate, by hash with bounded retries. Never recomputed afterwards.
 */
public final class GatePlacement {
    private GatePlacement() {
    }

    /** Returns {x, z}, or null when no valid point was found within the retry budget. */
    public static int[] choose(SimContext ctx, SimRng rng, String regionId) {
        Rules.Gates g = ctx.rules.gates();
        GenRegion region = ctx.region(regionId);
        int[] center = RegionPlacement.worldBlockFromGraph(region.posX(), region.posZ());
        for (int attempt = 0; attempt < g.retries(); attempt++) {
            double angle = rng.nextDouble() * 2.0 * StrictMath.PI;
            double dist = g.maxOffset() * StrictMath.sqrt(rng.nextDouble());
            int x = chunkCentre((int) StrictMath.round(center[0] + dist * StrictMath.cos(angle)));
            int z = chunkCentre((int) StrictMath.round(center[1] + dist * StrictMath.sin(angle)));
            if (valid(ctx, regionId, x, z, -1)) {
                return new int[] {x, z};
            }
        }
        return null;
    }

    /** Whether a gate at (x, z) for {@code regionId} satisfies the region, radius and spacing rules. */
    public static boolean valid(SimContext ctx, String regionId, int x, int z, int ignoreSectId) {
        Rules.Gates g = ctx.rules.gates();
        // V8/V9's authored province geometry replaces the legacy 3700-block host radius only here.
        double radius=(ctx.graph.rulesetId().equals("myvillage:worldplan_v8")||ctx.graph.rulesetId().equals("myvillage:worldplan_v9") || ctx.graph.rulesetId().equals("myvillage:worldplan_v10"))&&ctx.graph.geometry()!=null
                ?ctx.graph.geometry().radius():g.maxRadius();
        if (StrictMath.hypot(x, z) > radius) {
            return false;
        }
        Optional<String> at = RegionQueries.regionAt(ctx.graph, x, z);
        if (at.isEmpty() || !at.get().equals(regionId)) {
            return false;
        }
        // Reserve planned city sites before ledger compounds are sited.
        if (ctx.graph.geometry() != null && ctx.graph.geometry().reserved(x, z, g.minSpacing())) return false;
        long minSq = (long) g.minSpacing() * g.minSpacing();
        for (Sect s : ctx.state.sects.values()) {
            if (s.id == ignoreSectId) {
                continue;
            }
            long dx = s.gateX - x;
            long dz = s.gateZ - z;
            if (dx * dx + dz * dz < minSq) {
                return false;
            }
        }
        return true;
    }

    static int chunkCentre(int block) {
        return Math.floorDiv(block, 16) * 16 + 8;
    }
}
