package com.example.myvillage.sim.runtime.economy;

import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.commerce.*;
import com.example.myvillage.sim.economy.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** 无临时物品槽；每个菜单拥有独立 nonce 和一次性请求序号。 */
public final class EconomyMenu extends AbstractContainerMenu {
    public record Row(String id, String name, String item, int count, int quality, int cost, String detail) {
        public Row { if (id.length() > 96 || name.length() > 96 || item.length() > 160 || detail.length() > 320 || count < 0 || count > 10000 || quality < 0 || quality > 4 || cost < 0) throw new IllegalArgumentException("兑换行越界"); }
    }
    public static final List<String> STATUS = List.of("ok", "accepted", "completed", "exchanged", "not_member", "rank_too_low", "insufficient_contribution", "inventory_full", "missing_items", "task_active", "task_done_today", "not_ready", "wrong_board", "stale", "busy", "unavailable");
    private final Inventory inventory;
    public final BlockPos pos;
    public final boolean linlang;
    public final String site;
    public final int sectId;
    public final long day, token;
    public final List<Row> rows;
    private final ContainerData data = new SimpleContainerData(9);
    private int lastAction = Integer.MIN_VALUE;
    public EconomyMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), buf.readBoolean(), buf.readUtf(96), buf.readVarInt(), buf.readVarLong(), buf.readLong(), readRows(buf));
    }
    public EconomyMenu(int id, Inventory inventory, BlockPos pos, boolean linlang, String site, int sectId, long day, long token, List<Row> rows) {
        super(com.example.myvillage.sim.runtime.commerce.CommerceMenus.ECONOMY.get(), id);
        this.inventory = inventory; this.pos = pos.immutable(); this.linlang = linlang; this.site = site; this.sectId = sectId; this.day = day; this.token = token; this.rows = List.copyOf(rows);
        if (rows.size() > 32 || rows.isEmpty() || site.length() > 96 || day < 0) throw new IllegalArgumentException("兑换菜单无效");
        addDataSlots(data);
    }
    public int sequence() { return data.get(0); }
    public String status() { return STATUS.get(Math.clamp(data.get(1), 0, STATUS.size() - 1)); }
    public int contribution() { return (data.get(2) & 65535) | (data.get(3) & 65535) << 16; }
    public int reputation() { return (data.get(4) & 65535) | (data.get(5) & 65535) << 16; }
    public int progress() { return data.get(6); }
    public boolean activeTask() { return data.get(7) != 0; }
    public int activeRow() { return data.get(7) - 1; }
    public boolean canAccept() { return data.get(8) == 1; }
    private void result(String reason) { int index = STATUS.indexOf(reason); data.set(1, index < 0 ? STATUS.indexOf("unavailable") : index); }
    @Override public boolean stillValid(Player player) { return player == inventory.player && (player.level().isClientSide() || player instanceof ServerPlayer p && EconomyRuntime.valid(p, pos, linlang, site, sectId)); }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    public void act(ServerPlayer player, EconomyIntentPayload intent) {
        if (player != inventory.player || player.containerMenu != this || intent.menuId() != containerId || intent.token() != token || intent.sequence() != sequence() || intent.row() >= rows.size()) return;
        if (sequence() >= 30000) { player.closeContainer(); return; }
        data.set(0, sequence() + 1);
        if (!stillValid(player)) { player.closeContainer(); return; }
        var sim = WorldSimRuntime.sim().orElseThrow();
        if ((long) player.tickCount - lastAction < sim.playerEconomyRules().actionIntervalTicks()) { result("busy"); broadcastChanges(); return; }
        lastAction = player.tickCount;
        String owner = player.getUUID().toString(); var row = rows.get(intent.row());
        try {
            if (linlang) {
                var offer = sim.playerEconomyRules().offer(row.id()); var stack = new ItemStack(CommerceInventory.item(offer.item()));
                if (offer.pillQuality() > 1) stack.set(com.example.myvillage.item.ModDataComponents.PILL_QUALITY.get(), offer.pillQuality());
                var plan = EconomyInventory.goods(player, stack, offer.quantity());
                if (!plan.ready()) result(plan.result());
                else result(sim.buyLinlang(owner, sectId, offer.id(), plan::commit) ? "exchanged" : "stale");
            } else {
                var active = sim.townTask(owner);
                if (active.isEmpty()) {
                    if (EconomyRuntime.day(player) != day) { result("stale"); broadcastChanges(); return; }
                    sim.acceptTownTask(owner, player.getGameProfile().getName(), site, row.id(), day); result("accepted");
                } else {
                    var a = active.get();
                    if (!a.taskId().equals(row.id()) || !a.siteId().equals(site)) throw new IllegalArgumentException("wrong_board");
                    var task = sim.playerEconomyRules().task(a.taskId());
                    var required = task.kind().equals("supply") ? new ItemStack(CommerceInventory.item(task.item())) : ItemStack.EMPTY;
                    var plan = EconomyInventory.reward(player, a.reward(), required, task.kind().equals("supply") ? task.count() : 0, false);
                    if (!plan.ready()) result(plan.result());
                    else result(sim.completeTownTask(owner, site, a, plan::commit) ? "completed" : "stale");
                }
            }
            EconomyRuntime.dirty(player); player.getInventory().setChanged();
        } catch (IllegalArgumentException error) { result(error.getMessage()); }
        broadcastChanges();
    }
    @Override public void broadcastChanges() {
        if (inventory.player instanceof ServerPlayer player) {
            var sim = WorldSimRuntime.sim().orElse(null);
            if (sim != null) {
                int merit = sim.playerMember(player.getUUID().toString()).map(m -> m.contribution()).orElse(0), rep = sim.jianghuReputation(player.getUUID().toString());
                data.set(2, merit & 65535); data.set(3, merit >>> 16); data.set(4, rep & 65535); data.set(5, rep >>> 16);
                var task = sim.townTask(player.getUUID().toString()); data.set(6, task.map(TownAssignment::progress).orElse(0));
                int active = 0; if (task.isPresent()) { active = -1; for (int i = 0; i < rows.size(); i++) if (rows.get(i).id().equals(task.get().taskId()) && task.get().siteId().equals(site)) active = i + 1; }
                data.set(7, active); data.set(8, sim.townTaskAvailable(player.getUUID().toString(), site, EconomyRuntime.day(player)) ? 1 : 0);
                if (!linlang && active == 0 && data.get(8) == 0 && status().equals("ok")) result("task_done_today");
            }
        }
        super.broadcastChanges();
    }
    public static void writeOpening(RegistryFriendlyByteBuf buf, BlockPos pos, boolean linlang, String site, int sect, long day, long token, List<Row> rows) {
        buf.writeBlockPos(pos); buf.writeBoolean(linlang); buf.writeUtf(site, 96); buf.writeVarInt(sect); buf.writeVarLong(day); buf.writeLong(token); buf.writeVarInt(rows.size());
        for (var row : rows) { buf.writeUtf(row.id(), 96); buf.writeUtf(row.name(), 96); buf.writeUtf(row.item(), 160); buf.writeVarInt(row.count()); buf.writeVarInt(row.quality()); buf.writeVarInt(row.cost()); buf.writeUtf(row.detail(), 320); }
    }
    private static List<Row> readRows(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt(); if (size < 1 || size > 32) throw new IllegalArgumentException("兑换行数越界"); var rows = new ArrayList<Row>();
        for (int i = 0; i < size; i++) rows.add(new Row(buf.readUtf(96), buf.readUtf(96), buf.readUtf(160), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(320)));
        return rows;
    }
}
