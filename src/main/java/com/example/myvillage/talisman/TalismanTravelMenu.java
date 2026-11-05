package com.example.myvillage.talisman;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.example.myvillage.talisman.TalismanTravel.WaystoneEntry;

/** 客户端只提交当页按钮编号；UUID、坐标、手持物与权限均由服务器重新核对。 */
public final class TalismanTravelMenu extends AbstractContainerMenu {
    public static final int PAGE_SIZE = 6, PREVIOUS = 100, NEXT = 101;
    public static final int WIDTH = 320, HEIGHT = 244;
    private final List<WaystoneEntry> entries;
    private final int page, pages;
    private final Player owner;
    private final Level openedIn;
    private final InteractionHand hand;
    private final ItemStack held;

    public TalismanTravelMenu(int id, Inventory inv, RegistryFriendlyByteBuf buffer) {
        this(id, inv, null, ItemStack.EMPTY, readEntries(buffer), buffer.readVarInt(), buffer.readVarInt());
    }
    public TalismanTravelMenu(int id, Inventory inv, InteractionHand hand, ItemStack held,
            List<WaystoneEntry> entries, int page, int pages) {
        super(TalismanContent.TRAVEL_MENU.get(), id);
        if (entries.size() > PAGE_SIZE || page < 0 || pages < 1 || page >= pages) throw new IllegalArgumentException("石碑列表页无效");
        this.entries = List.copyOf(entries); this.page = page; this.pages = pages;
        this.owner = inv.player; this.openedIn = inv.player.level(); this.hand = hand; this.held = held;
    }
    public static int pageCount(int size) { return Math.max(1, (int) ((size + (long) PAGE_SIZE - 1) / PAGE_SIZE)); }
    public static <T> List<T> page(List<T> all, int index) {
        if (index < 0 || index >= pageCount(all.size())) throw new IllegalArgumentException("石碑页码越界");
        int start = index * PAGE_SIZE;
        return List.copyOf(all.subList(start, Math.min(start + PAGE_SIZE, all.size())));
    }
    public static void writePage(RegistryFriendlyByteBuf b, List<WaystoneEntry> entries, int page, int pages) {
        b.writeVarInt(entries.size());
        for (var entry : entries) {
            b.writeUUID(entry.id()); b.writeUtf(entry.name(), 64);
            b.writeResourceLocation(entry.dimension()); b.writeBlockPos(entry.pos());
        }
        b.writeVarInt(page); b.writeVarInt(pages);
    }
    private static List<WaystoneEntry> readEntries(RegistryFriendlyByteBuf b) {
        int size = b.readVarInt();
        if (size < 0 || size > PAGE_SIZE) throw new IllegalArgumentException("石碑列表过大");
        var rows = new java.util.ArrayList<WaystoneEntry>(size);
        for (int i = 0; i < size; i++) rows.add(new WaystoneEntry(b.readUUID(), b.readUtf(64), b.readResourceLocation(), b.readBlockPos()));
        return List.copyOf(rows);
    }
    public List<WaystoneEntry> entries() { return entries; }
    public int page() { return page; }
    public int pages() { return pages; }
    @Override public boolean stillValid(Player p) {
        return p == owner && p.level() == openedIn && p.isAlive() && !p.isSpectator()
                && (p.level().isClientSide() || hand != null && p.getItemInHand(hand) == held && held.is(TalismanContent.HOME.get()));
    }
    @Override public boolean clickMenuButton(Player p, int button) {
        if (!(p instanceof ServerPlayer server) || server.containerMenu != this || !stillValid(p)) return false;
        if (button == PREVIOUS && page > 0) return TalismanTravel.openHome(server, hand, page - 1);
        if (button == NEXT && page + 1 < pages) return TalismanTravel.openHome(server, hand, page + 1);
        if (button < 0 || button >= entries.size()) return false;
        var destination = entries.get(button).id();
        server.closeContainer();
        return TalismanTravel.home(server, hand, destination);
    }
    @Override public ItemStack quickMoveStack(Player p, int index) { return ItemStack.EMPTY; }
}
