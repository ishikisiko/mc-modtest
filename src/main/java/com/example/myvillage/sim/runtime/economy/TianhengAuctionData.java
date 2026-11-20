package com.example.myvillage.sim.runtime.economy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** The same save owns opening dates, highest bids, and unclaimed receipts. */
final class TianhengAuctionData extends SavedData {
    static final int PRICE_LIMIT = 20_000_000;

    record Bidder(int person, String name, String preference, int budget) { }
    static final class Lot {
        final TianhengMarkets.Goods goods;
        int price;
        UUID player;
        int npc;
        int attempted;
        boolean settled;

        Lot(TianhengMarkets.Goods goods) { this.goods = goods; }
    }
    record Run(long day, long start, List<Lot> lots, List<Bidder> bidders) { }

    static final class Receipt {
        long refund;
        final List<ItemStack> awards = new ArrayList<>();
    }

    final Map<String, Run> runs = new LinkedHashMap<>();
    final Map<String, Map<UUID, Receipt>> receipts = new LinkedHashMap<>();

    static TianhengAuctionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(TianhengAuctionData::new, TianhengAuctionData::load), "yuanxian_tianheng_auctions");
    }

    Receipt receipt(String site, UUID player, boolean create) {
        if (!create) return receipts.getOrDefault(site, Map.of()).get(player);
        return receipts.computeIfAbsent(site, key -> new LinkedHashMap<>())
                .computeIfAbsent(player, key -> new Receipt());
    }

    static TianhengAuctionData load(CompoundTag root, HolderLookup.Provider registries) {
        if (root.getInt("format") != 1) throw new IllegalArgumentException("拍卖档案格式无效");
        var data = new TianhengAuctionData();
        for (Tag value : root.getList("runs", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) value;
            var bidders = new ArrayList<Bidder>();
            for (Tag entry : tag.getList("bidders", Tag.TAG_COMPOUND)) {
                var bidder = (CompoundTag) entry;
                bidders.add(new Bidder(bidder.getInt("person"), bidder.getString("name"),
                        bidder.getString("preference"), bidder.getInt("budget")));
            }
            var lots = new ArrayList<Lot>();
            for (Tag entry : tag.getList("lots", Tag.TAG_COMPOUND)) {
                var row = (CompoundTag) entry;
                var stack = ItemStack.parseOptional(registries, row.getCompound("item"));
                var lot = new Lot(new TianhengMarkets.Goods(row.getString("id"), row.getString("name"),
                        row.getString("detail"), row.getString("kind"), stack, row.getInt("opening")));
                lot.price = row.getInt("price");
                lot.player = row.hasUUID("player") ? row.getUUID("player") : null;
                lot.npc = row.getInt("npc");
                lot.attempted = row.getInt("attempted");
                lot.settled = row.getBoolean("settled");
                if (stack.isEmpty() || lot.goods.price() < 1 || lot.goods.price() > PRICE_LIMIT
                        || lot.price < 0 || lot.price > PRICE_LIMIT
                        || (lot.price > 0) != (lot.player != null || lot.npc != 0)
                        || lot.player != null && lot.npc != 0)
                    throw new IllegalArgumentException("拍品档案无效");
                lots.add(lot);
            }
            if (tag.getLong("day") < 0 || tag.getLong("start") < 0 || lots.isEmpty() || lots.size() > 3 || bidders.size() > 2)
                throw new IllegalArgumentException("拍卖场次档案无效");
            if (data.runs.put(tag.getString("site"), new Run(tag.getLong("day"), tag.getLong("start"),
                    List.copyOf(lots), List.copyOf(bidders))) != null) throw new IllegalArgumentException("重复拍卖场次");
        }
        for (Tag entry : root.getList("receipts", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) entry;
            var receipt = data.receipt(row.getString("site"), row.getUUID("player"), true);
            receipt.refund = row.getLong("refund");
            if (receipt.refund < 0) throw new IllegalArgumentException("拍卖退款档案无效");
            for (Tag award : row.getList("awards", Tag.TAG_COMPOUND)) {
                var stack = ItemStack.parseOptional(registries, (CompoundTag) award);
                if (stack.isEmpty()) throw new IllegalArgumentException("拍卖领取档案无效");
                receipt.awards.add(stack);
            }
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putInt("format", 1);
        var all = new ListTag();
        runs.forEach((site, run) -> {
            var tag = new CompoundTag();
            tag.putString("site", site);
            tag.putLong("day", run.day());
            tag.putLong("start", run.start());
            var bidders = new ListTag();
            for (var bidder : run.bidders()) {
                var row = new CompoundTag();
                row.putInt("person", bidder.person());
                row.putString("name", bidder.name());
                row.putString("preference", bidder.preference());
                row.putInt("budget", bidder.budget());
                bidders.add(row);
            }
            tag.put("bidders", bidders);
            var lots = new ListTag();
            for (var lot : run.lots()) {
                var row = new CompoundTag();
                row.put("item", lot.goods.stack().save(registries));
                row.putString("id", lot.goods.id());
                row.putString("name", lot.goods.title());
                row.putString("detail", lot.goods.description());
                row.putString("kind", lot.goods.kind());
                row.putInt("opening", lot.goods.price());
                row.putInt("price", lot.price);
                if (lot.player != null) row.putUUID("player", lot.player);
                row.putInt("npc", lot.npc);
                row.putInt("attempted", lot.attempted);
                row.putBoolean("settled", lot.settled);
                lots.add(row);
            }
            tag.put("lots", lots);
            all.add(tag);
        });
        root.put("runs", all);
        var pending = new ListTag();
        receipts.forEach((site, owners) -> owners.forEach((owner, receipt) -> {
            var row = new CompoundTag();
            row.putString("site", site);
            row.putUUID("player", owner);
            row.putLong("refund", receipt.refund);
            var awards = new ListTag();
            for (var item : receipt.awards) awards.add(item.save(registries));
            row.put("awards", awards);
            pending.add(row);
        }));
        root.put("receipts", pending);
        return root;
    }
}
