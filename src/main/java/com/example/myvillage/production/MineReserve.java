package com.example.myvillage.production;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Mineral stock belongs to the natural coordinate even if the machine is replaced. */
final class MineReserve extends SavedData {
    private final Map<Long, Stock> stocks = new HashMap<>();

    record Stock(int count, long time) {
        Stock at(long now, ProductionRules.Mining rules) {
            if (now <= time) return this;
            if (count == rules.capacity()) return new Stock(count, now);
            long recovered = (now - time) / rules.recovery();
            if (recovered >= rules.capacity() - count) return new Stock(rules.capacity(), now);
            return new Stock(count + (int) recovered, time + recovered * rules.recovery());
        }
    }

    static MineReserve get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(MineReserve::new, MineReserve::load), "yuanxian_mine_reserve");
    }

    Stock stock(BlockPos pos, long now) {
        var rules = ProductionRules.get().mining();
        var stored = stocks.get(pos.asLong());
        return stored == null ? new Stock(rules.capacity(), now) : stored.at(now, rules);
    }

    boolean take(BlockPos pos, long now, int leave) {
        Stock current = stock(pos, now);
        if (current.count <= leave) return false;
        stocks.put(pos.asLong(), new Stock(current.count - 1, current.time));
        setDirty();
        return true;
    }

    private static MineReserve load(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new MineReserve();
        int cap = ProductionRules.get().mining().capacity();
        for (Tag value : tag.getList("Stocks", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) value;
            result.stocks.put(row.getLong("Pos"), new Stock(Math.clamp(row.getInt("Count"), 0, cap), Math.max(0, row.getLong("Time"))));
        }
        return result;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        stocks.forEach((pos, stock) -> {
            var row = new CompoundTag();
            row.putLong("Pos", pos);
            row.putInt("Count", stock.count);
            row.putLong("Time", stock.time);
            list.add(row);
        });
        tag.put("Stocks", list);
        return tag;
    }
}
