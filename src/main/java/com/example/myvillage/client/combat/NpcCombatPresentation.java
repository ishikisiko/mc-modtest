package com.example.myvillage.client.combat;

import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.entity.npc.NpcEntity;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** One monotonic client clock for NPC bones and trails, seeded from atomic tracked entity data. */
public final class NpcCombatPresentation {
    private record Clock(CompoundTag state, double localStart, int frozenTick) {}
    private static final WeakHashMap<NpcEntity, Clock> CLOCKS = new WeakHashMap<>();
    public record Pose(String clip, float ticks, float yaw) {}
    private NpcCombatPresentation() {}

    public static Pose pose(NpcEntity npc, float partialTick) {
        CompoundTag state = npc.combatState();
        if (!state.contains("move")) {
            if (CLOCKS.remove(npc) != null) CombatWorldTrails.stop(npc.getId());
            return null;
        }
        Clock clock = CLOCKS.get(npc);
        if (clock == null || !clock.state().equals(state)) {
            int elapsed = state.getInt("elapsed");
            boolean frozen = state.getBoolean("frozen");
            long transit = frozen ? 0 : ClientCombatClock.elapsedSinceServer(state.getLong("stamp"));
            clock = new Clock(state.copy(), ClientCombatClock.ticks() - elapsed - transit, frozen ? elapsed : -1);
            CLOCKS.put(npc, clock);
            var id = ResourceLocation.tryParse(state.getString("move"));
            CombatWorldTrails.stop(npc.getId());
            if (!frozen && id != null) CombatStyles.bundled().move(id).ifPresent(ref ->
                    CombatWorldTrails.start(npc, ref.move(), elapsed + transit, state.getFloat("yaw")));
        }
        float tick = clock.frozenTick() >= 0 ? clock.frozenTick()
                : (float) Math.max(0, ClientCombatClock.now(partialTick) - clock.localStart());
        var id = ResourceLocation.tryParse(state.getString("move"));
        return id == null ? null : new Pose(id.getPath(), tick, state.getFloat("yaw"));
    }
}
