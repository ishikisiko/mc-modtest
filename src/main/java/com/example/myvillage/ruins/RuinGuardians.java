package com.example.myvillage.ruins;

import com.example.myvillage.entity.beast.BeastEntity;
import com.example.myvillage.stats.runtime.StatsRuntime;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** One-time structure birth. Later reloads retain damage and the original saved realm. */
public final class RuinGuardians {
    private RuinGuardians() {}
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof BeastEntity beast)) return;
        CompoundTag marker = beast.getPersistentData().getCompound("myvillage:ruin_guardian");
        if (!marker.getBoolean("pending")) return;
        int index = marker.getInt("index");
        var guardians = RuinRules.get().guardians();
        if (index < 0 || index >= guardians.size()) return;
        var definition = guardians.get(index);
        StatsRuntime.applyRealm(beast,definition.realm(),definition.stage());
        beast.setHealth(beast.getMaxHealth());
        marker.putBoolean("pending",false);
    }
}
