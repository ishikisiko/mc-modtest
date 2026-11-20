package com.example.myvillage.sim.runtime.economy;

import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.life.LifeResidentView;
import com.example.myvillage.sim.life.LifeSite;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.economy.TianhengAuctionData.Bidder;
import com.example.myvillage.sim.runtime.economy.TianhengAuctionData.Lot;
import com.example.myvillage.sim.runtime.economy.TianhengAuctionData.Run;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class TianhengAuctions {
    public static final String OPEN = "start";
    public static final String CLAIM = "claim";
    public static final int IDLE = 0, PREVIEW = 1, LIVE = 2, SOLD = 3, PASSED = 4, STARTABLE = 5, CLAIMABLE = 6;
    public static final int YOU = 1, OTHER_PLAYER = 2, NPC = 3;
    record Opening(long run, List<String> bidders, List<EconomyMenu.Row> rows) { }
    public record Quote(int state, int price, int next, int seconds, int leader) {
        static final Quote NONE = new Quote(IDLE, 0, 0, 0, 0);
    }

    private TianhengAuctions() { }

    static Opening opening(ServerPlayer player, String site) {
        var server = player.getServer();
        var data = TianhengAuctionData.get(server);
        update(server, data, site);
        var run = data.runs.get(site);
        var rows = new ArrayList<EconomyMenu.Row>();
        if (run == null || finished(run) && run.day() < EconomyRuntime.day(player)) {
            rows.add(action(OPEN, "开始本场拍卖", "开场后先看拍品，随后按顺序竞价。每座城每天开一场。"));
            run = null;
        } else {
            for (int i = 0; i < run.lots().size(); i++) {
                var row = run.lots().get(i).goods.row();
                rows.add(new EconomyMenu.Row("lot/" + i, row.name(), row.item(), row.count(), 0, row.cost(), row.detail()));
            }
        }
        rows.add(action(CLAIM, "领取成交物和退款", "未领拍品和被超价退回的灵石会留在本城。"));
        return new Opening(run == null ? 0 : run.start(), run == null ? List.of()
                : run.bidders().stream().map(Bidder::name).toList(), List.copyOf(rows));
    }

    private static EconomyMenu.Row action(String id, String name, String detail) {
        return new EconomyMenu.Row(id, name, "minecraft:air", 0, 0, 0, detail);
    }

    static String act(ServerPlayer player, String site, long start, String action, int price) {
        var server = player.getServer();
        var data = TianhengAuctionData.get(server);
        update(server, data, site);
        return switch (action) {
            case OPEN -> start(player, data, site);
            case CLAIM -> claim(player, data, site);
            default -> bid(player, data, site, start, lotIndex(action), price);
        };
    }

    private static String start(ServerPlayer player, TianhengAuctionData data, String siteId) {
        long day = EconomyRuntime.day(player);
        var old = data.runs.get(siteId);
        if (old != null && !finished(old)) return "started";
        if (old != null && day <= old.day()) return "stale";
        var sim = WorldSimRuntime.sim().orElseThrow();
        var site = sim.lifeSite(siteId).orElseThrow();
        long now = player.serverLevel().getGameTime();
        var goods = TianhengMarkets.lots(player.registryAccess(), site.regionId(),
                player.serverLevel().getSeed() ^ Long.rotateLeft(day, 17) ^ siteId.hashCode());
        if (goods.size() != 3) return "unavailable";
        var bidders = invite(sim, site, goods);
        data.runs.put(siteId, new Run(day, now, goods.stream().map(Lot::new).toList(), bidders));
        data.setDirty();
        boolean moved = false;
        for (int i = 0; i < bidders.size(); i++)
            moved |= sim.visitAuction(bidders.get(i).person(), siteId, "tianheng_seat_" + i);
        if (moved) EconomyRuntime.dirty(player);
        return "started";
    }

    private static List<Bidder> invite(WorldSim sim, LifeSite site, List<TianhengMarkets.Goods> goods) {
        var entrance = site.place("tianheng_auction").orElseThrow();
        var roster = sim.lifeResidents().stream().filter(r -> r.siteId().equals(site.id()))
                .filter(r -> Set.of("herbalist", "smith", "innkeeper", "alchemist", "rogue", "visitor", "spirit_grower")
                        .contains(r.role()))
                .sorted(Comparator.comparingDouble((LifeResidentView r) -> {
                    double dx = r.x() - entrance.x(), dz = r.z() - entrance.z();
                    return dx * dx + dz * dz;
                }).thenComparingInt(LifeResidentView::personId)).toList();
        var bidders = new ArrayList<Bidder>();
        var invitedKinds = new HashSet<String>();
        for (var resident : roster) {
            var person = sim.person(resident.personId()).filter(p -> p.alive()).orElse(null);
            if (person == null) continue;
            String kind = switch (resident.role()) {
                case "herbalist", "alchemist", "spirit_grower" -> "herb";
                case "smith" -> "metal";
                default -> "manual";
            };
            if (!invitedKinds.add(kind)) continue;
            int opening = goods.stream().filter(lot -> lot.kind().equals(kind)).findFirst().orElseThrow().price();
            int caution = person.traits().get(2);
            int budget = opening + (int) ((long) opening * (TianhengMarkets.rules().npcBudgetPercent() - 95 - caution / 5) / 100);
            bidders.add(new Bidder(person.id(), person.name(), kind, budget));
            if (bidders.size() == 2) break;
        }
        return List.copyOf(bidders);
    }

    private static String bid(ServerPlayer player, TianhengAuctionData data, String site, long start, int index, int price) {
        var run = data.runs.get(site);
        if (run == null || run.start() != start || index < 0 || index >= run.lots().size()) return "stale";
        var lot = run.lots().get(index);
        int cost = next(lot);
        long now = player.serverLevel().getGameTime();
        if (now < begins(run, index) || now >= ends(run, index) || lot.settled || cost <= 0
                || price != cost || player.getUUID().equals(lot.player)) return "stale";
        var payment = EconomyInventory.tianhengPayment(player, cost, ItemStack.EMPTY);
        if (!payment.ready()) return payment.result();
        if (!payment.commit()) return "stale";
        releasePrevious(data, site, lot);
        lot.price = cost;
        lot.player = player.getUUID();
        lot.npc = 0;
        data.setDirty();
        return "bid_placed";
    }

    private static void releasePrevious(TianhengAuctionData data, String site, Lot lot) {
        if (lot.player == null) return;
        var receipt = data.receipt(site, lot.player, true);
        receipt.refund = Math.addExact(receipt.refund, lot.price);
    }

    private static String claim(ServerPlayer player, TianhengAuctionData data, String site) {
        var receipt = data.receipt(site, player.getUUID(), false);
        if (receipt == null) return "not_ready";
        if (receipt.refund > 0) {
            var refund = EconomyInventory.tianhengReceipt(player, receipt.refund, ItemStack.EMPTY);
            if (!refund.ready()) return refund.result();
            if (!refund.commit()) return "stale";
            receipt.refund = 0;
        } else if (!receipt.awards.isEmpty()) {
            var prize = EconomyInventory.tianhengReceipt(player, 0, receipt.awards.getFirst());
            if (!prize.ready()) return prize.result();
            if (!prize.commit()) return "stale";
            receipt.awards.removeFirst();
        }
        if (receipt.refund == 0 && receipt.awards.isEmpty()) data.receipts.get(site).remove(player.getUUID());
        data.setDirty();
        return "claimed";
    }

    static Quote quote(ServerPlayer player, String site, long start, String id) {
        var data = TianhengAuctionData.get(player.getServer());
        var run = data.runs.get(site);
        if (id.equals(CLAIM)) {
            var pending = data.receipt(site, player.getUUID(), false);
            return pending == null ? Quote.NONE : new Quote(CLAIMABLE, 0, 0, 0, 0);
        }
        if (id.equals(OPEN)) {
            return run == null || !finished(run) || run.day() < EconomyRuntime.day(player)
                    ? new Quote(STARTABLE, 0, 0, 0, 0) : Quote.NONE;
        }
        int i = lotIndex(id);
        if (run == null || run.start() != start || i < 0 || i >= run.lots().size()) return Quote.NONE;
        var lot = run.lots().get(i);
        long now = player.serverLevel().getGameTime();
        int leader = 0;
        if (lot.player != null) leader = lot.player.equals(player.getUUID()) ? YOU : OTHER_PLAYER;
        else if (lot.npc != 0) {
            for (int k = 0; k < run.bidders().size(); k++) if (run.bidders().get(k).person() == lot.npc) leader = NPC + k;
        }
        if (lot.settled || now >= ends(run, i))
            return new Quote(lot.price > 0 ? SOLD : PASSED, lot.price, 0, 0, leader);
        if (now < begins(run, i))
            return new Quote(PREVIEW, 0, lot.goods.price(), seconds(begins(run, i) - now), 0);
        return new Quote(LIVE, lot.price, next(lot), seconds(ends(run, i) - now), leader);
    }

    static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % 20 != 0 || WorldSimRuntime.sim().isEmpty()) return;
        var data = TianhengAuctionData.get(server);
        for (String site : data.runs.keySet()) update(server, data, site);
    }

    private static void update(MinecraftServer server, TianhengAuctionData data, String site) {
        var run = data.runs.get(site);
        if (run == null || finished(run)) return;
        long now = server.overworld().getGameTime();
        var sim = WorldSimRuntime.sim().orElseThrow();
        for (int i = 0; i < run.lots().size(); i++) {
            var lot = run.lots().get(i);
            if (lot.settled || now < begins(run, i)) continue;
            if (now >= ends(run, i)) {
                if (lot.player != null) data.receipt(site, lot.player, true).awards.add(lot.goods.stack().copy());
                lot.settled = true;
                data.setDirty();
            } else {
                npcBids(sim, data, site, run, i, lot, now);
            }
        }
        if (finished(run)) {
            boolean changed = false;
            for (var bidder : run.bidders()) changed |= sim.leaveAuction(bidder.person(), site);
            if (changed) WorldSimSavedData.get(server.overworld()).setDirty();
        }
    }

    private static void npcBids(WorldSim sim, TianhengAuctionData data, String site, Run run, int i, Lot lot, long now) {
        for (int j = 0; j < run.bidders().size(); j++) {
            var bidder = run.bidders().get(j);
            int bit = 1 << j;
            long delay = (long) TianhengMarkets.rules().lotTicks()
                    * (3 + Math.floorMod(bidder.person() + run.day(), 4)) / 10;
            if ((lot.attempted & bit) != 0 || !bidder.preference().equals(lot.goods.kind())
                    || now < begins(run, i) + delay) continue;
            lot.attempted |= bit;
            data.setDirty();
            int next = next(lot);
            if (next < 1 || next > bidder.budget() || lot.npc == bidder.person()
                    || sim.person(bidder.person()).filter(p -> p.alive()).isEmpty()) continue;
            releasePrevious(data, site, lot);
            lot.price = next;
            lot.player = null;
            lot.npc = bidder.person();
        }
    }

    static int next(Lot lot) {
        if (lot.price == 0) return lot.goods.price();
        long step = Math.max(1, (lot.goods.price() * (long) TianhengMarkets.rules().stepPercent() + 99) / 100);
        long cost = lot.price + step;
        return cost <= TianhengAuctionData.PRICE_LIMIT ? (int) cost : 0;
    }

    private static int lotIndex(String id) {
        return switch (id) {
            case "lot/0" -> 0;
            case "lot/1" -> 1;
            case "lot/2" -> 2;
            default -> -1;
        };
    }

    private static boolean finished(Run run) { return run.lots().stream().allMatch(lot -> lot.settled); }
    private static long begins(Run run, int i) {
        return run.start() + TianhengMarkets.rules().previewTicks() + (long) i * TianhengMarkets.rules().lotTicks();
    }
    private static long ends(Run run, int i) { return begins(run, i) + TianhengMarkets.rules().lotTicks(); }
    private static int seconds(long ticks) { return (int) ((ticks + 19) / 20); }
}
