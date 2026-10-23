package com.example.myvillage.cultivation.fate.ability;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** 世界游戏时钟下的逐人冷却；没有玩家附加数据或 Clone 写入，重启不重置。 */
public final class FateAbilityCooldowns extends SavedData {
    private static final String DATA_NAME = "myvillage_fate_ability_cooldowns";
    private final Map<UUID, Map<ResourceLocation, Long>> deadlines = new HashMap<>();

    public FateAbilityCooldowns() { }

    public static FateAbilityCooldowns get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(FateAbilityCooldowns::new, FateAbilityCooldowns::load), DATA_NAME);
    }

    public long remaining(UUID player, ResourceLocation ability, long now) {
        if (now < 0) throw new IllegalArgumentException("世界时钟不能为负");
        long readyAt = deadlines.getOrDefault(player, Map.of()).getOrDefault(ability, 0L);
        return readyAt <= now ? 0 : readyAt - now;
    }

    public void start(UUID player, ResourceLocation ability, long now, int ticks) {
        if (now < 0 || ticks < 1) throw new IllegalArgumentException("冷却时间越界");
        long readyAt = now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks;
        // 任何重复写入都只能延长，不能通过改配置或入口竞争缩短既有冷却。
        deadlines.computeIfAbsent(player, ignored -> new HashMap<>()).merge(ability, readyAt, Math::max);
        setDirty();
    }

    public void prune(long now) {
        boolean changed = false;
        for (Iterator<Map<ResourceLocation, Long>> players = deadlines.values().iterator(); players.hasNext();) {
            var abilities = players.next();
            changed |= abilities.values().removeIf(readyAt -> readyAt <= now);
            if (abilities.isEmpty()) { players.remove(); changed = true; }
        }
        if (changed) setDirty();
    }

    static FateAbilityCooldowns load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new FateAbilityCooldowns();
        var players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            UUID player;
            try { player = UUID.fromString(key); }
            catch (IllegalArgumentException exception) { data.setDirty(); continue; }
            var stored = players.getCompound(key);
            Map<ResourceLocation, Long> abilities = new HashMap<>();
            for (String ability : stored.getAllKeys()) {
                var id = ResourceLocation.tryParse(ability);
                long readyAt = stored.getLong(ability);
                if (id == null || !stored.contains(ability, Tag.TAG_LONG) || readyAt < 0) { data.setDirty(); continue; }
                if (readyAt > 0) abilities.put(id, readyAt);
            }
            if (!abilities.isEmpty()) data.deadlines.put(player, abilities);
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var players = new CompoundTag();
        deadlines.forEach((player, abilities) -> {
            var stored = new CompoundTag();
            abilities.forEach((id, readyAt) -> stored.putLong(id.toString(), readyAt));
            players.put(player.toString(), stored);
        });
        tag.put("players", players);
        return tag;
    }
}
