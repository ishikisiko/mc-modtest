package com.example.myvillage.gathering;

import com.example.myvillage.cultivation.meditation.BasicBreathingSettlement;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** At most one eligible array and one reservation per player settlement, with no global cache. */
public final class GatheringRuntime {
    public record Pending(BasicBreathingSettlement.Plan plan, GatheringBlockEntity.Lease lease) {
        public void finish(boolean installed) { if (lease != null) lease.finish(installed); }
    }
    public static Pending prepare(ServerPlayer player, BasicBreathingSettlement.Plan base, long cap) {
        if (base.progressApplied() <= 0 || base.replacement().cultivationProgress() >= cap) return new Pending(base, null);
        BlockPos floor = BlockPos.containing(player.getX(), player.getY() - .05, player.getZ());
        var candidates = new ArrayList<GatheringBlockEntity>();
        int radius = GatheringRules.get().maxInnerRadius();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            BlockPos pos = floor.offset(x, 0, z);
            if (player.level().hasChunkAt(pos) && player.level().getBlockEntity(pos) instanceof GatheringBlockEntity core)
                candidates.add(core);
        }
        candidates.sort(Comparator.<GatheringBlockEntity>comparingDouble(core -> player.distanceToSqr(
                core.getBlockPos().getX() + .5, player.getY(), core.getBlockPos().getZ() + .5))
                .thenComparingLong(core -> core.getBlockPos().asLong()));
        for (var core : candidates) {
            var boosted = GatheringMath.boost(base, cap, core.effectiveBonus());
            if (boosted.progressApplied() <= base.progressApplied()) continue;
            var lease = core.reserve(player);
            if (lease != null) return new Pending(boosted, lease);
        }
        return new Pending(base, null);
    }
    private GatheringRuntime() { }
}
