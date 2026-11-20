package com.example.myvillage.storagebag;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class StorageBagMenu extends AbstractContainerMenu {
    public static final int WIDTH = 196;
    public static final int GRID_X = 18;
    public static final int GRID_Y = 36;
    public static final int PAGE_SLOTS = StorageBagRules.COLUMNS * StorageBagRules.PAGE_ROWS;
    public static final int PREVIOUS_PAGE = 0;
    public static final int NEXT_PAGE = 1;
    public static final int TOGGLE_PICKUP = 2;
    private final Inventory inventory;
    private final ItemStack binding;
    private final int bagIndex;
    private final StorageBagRules.Kind kind;
    private final StorageBagRules.Tier tier;
    private final SimpleContainer storage;
    private final DataSlot page = DataSlot.standalone();
    private final DataSlot automatic = DataSlot.standalone();
    private boolean loading;

    public StorageBagMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readVarInt(), buffer.readEnum(StorageBagRules.Kind.class),
                StorageBagRules.tiers().get(buffer.readVarInt()), null);
    }

    private StorageBagMenu(int id, Inventory inventory, int bagIndex, StorageBagRules.Kind kind,
                           StorageBagRules.Tier tier, ItemStack binding) {
        super(StorageBagContent.BAG_MENU.get(), id);
        this.inventory = inventory;
        this.bagIndex = bagIndex;
        this.binding = binding;
        this.kind = kind;
        this.tier = tier;
        storage = new SimpleContainer(tier.capacity());
        if (binding != null) {
            load();
            storage.addListener(ignored -> save());
        }
        for (int i = 0; i < tier.capacity(); i++) {
            int index = i;
            addSlot(new Slot(storage, index, GRID_X + i % 9 * 18, GRID_Y + (i % PAGE_SLOTS) / 9 * 18) {
                @Override public boolean isActive() { return index / PAGE_SLOTS == page(); }
                @Override public boolean mayPlace(ItemStack stack) {
                    return isActive() && StorageBagContents.accepts(kind, stack);
                }
                @Override public boolean mayPickup(Player player) { return isActive(); }
            });
        }
        for (int i = 0; i < 27; i++) {
            addInventorySlot(i + 9, GRID_X + i % 9 * 18, inventoryY() + i / 9 * 18);
        }
        for (int i = 0; i < 9; i++) addInventorySlot(i, GRID_X + i * 18, inventoryY() + 58);
        addDataSlot(page);
        addDataSlot(automatic);
    }

    static void open(ServerPlayer player, int index, StorageBagItem item, ItemStack stack) {
        if (!player.isAlive() || player.getInventory().getItem(index) != stack) return;
        int tierIndex = StorageBagRules.tiers().indexOf(item.tier());
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new StorageBagMenu(id, inventory, index, item.kind(), item.tier(), stack), stack.getHoverName()),
                buffer -> buffer.writeVarInt(index).writeEnum(item.kind()).writeVarInt(tierIndex));
    }

    private void addInventorySlot(int index, int x, int y) {
        addSlot(new Slot(inventory, index, x, y) {
            @Override public boolean mayPlace(ItemStack stack) { return index != bagIndex; }
            @Override public boolean mayPickup(Player player) { return index != bagIndex; }
        });
    }

    private void load() {
        loading = true;
        try {
            var slots = StorageBagContents.read(binding, tier.capacity());
            for (int i = 0; i < slots.size(); i++) storage.setItem(i, slots.get(i));
            automatic.set(StorageBagContents.autoPickup(binding) ? 1 : 0);
        } finally {
            loading = false;
        }
    }

    private void save() {
        if (loading || !stillValid(inventory.player) || !(inventory.player instanceof ServerPlayer player)) return;
        var stacks = new java.util.ArrayList<ItemStack>(tier.capacity());
        for (int i = 0; i < tier.capacity(); i++) stacks.add(storage.getItem(i));
        StorageBagContents.write(binding, stacks);
        syncInventory(player, bagIndex, binding);
    }

    static void contentsChanged(ServerPlayer player, int index, ItemStack bag) {
        if (player.containerMenu instanceof StorageBagMenu menu && menu.binding == bag && menu.stillValid(player)) {
            menu.load();
        }
        syncInventory(player, index, bag);
    }

    private static void syncInventory(ServerPlayer player, int index, ItemStack bag) {
        player.getInventory().setChanged();
        if (index == Inventory.SLOT_OFFHAND) {
            player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, index, bag.copy()));
        }
    }

    public int page() { return page.get(); }
    public int pages() { return (tier.capacity() + PAGE_SLOTS - 1) / PAGE_SLOTS; }
    public int visibleSlots() { return Math.min(PAGE_SLOTS, tier.capacity() - page() * PAGE_SLOTS); }
    public int bagSlots() { return tier.capacity(); }
    public int inventoryY() { return GRID_Y + Math.min(tier.rows(), StorageBagRules.PAGE_ROWS) * 18 + 13; }
    public StorageBagRules.Kind kind() { return kind; }
    public boolean automatic() { return automatic.get() != 0; }

    @Override
    public boolean stillValid(Player player) {
        return inventory.player == player && player.isAlive() && !player.isSpectator()
                && (binding == null || inventory.getItem(bagIndex) == binding && binding.getCount() == 1);
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer server) || player.containerMenu != this || !stillValid(player)) return false;
        if (button == PREVIOUS_PAGE && page() > 0 && getCarried().isEmpty()) page.set(page() - 1);
        else if (button == NEXT_PAGE && page() + 1 < pages() && getCarried().isEmpty()) page.set(page() + 1);
        else if (button == TOGGLE_PICKUP && kind == StorageBagRules.Kind.MATERIAL) {
            StorageBagContents.setAutoPickup(binding, !automatic());
            automatic.set(automatic() ? 0 : 1);
            syncInventory(server, bagIndex, binding);
        } else return false;
        broadcastChanges();
        return true;
    }

    private boolean editable(Slot slot) {
        return slot.isActive() && (slot.container != inventory || slot.getContainerSlot() != bagIndex);
    }

    @Override
    public void clicked(int index, int button, ClickType type, Player player) {
        if (!stillValid(player) || type == ClickType.SWAP && button == bagIndex
                || index >= 0 && (index >= slots.size() || !editable(slots.get(index)))) return;
        super.clicked(index, button, type, player);
    }

    @Override public boolean canDragTo(Slot slot) { return editable(slot); }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return editable(slot); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!editable(slot) || !slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var original = stack.copy();
        int pageStart = page() * PAGE_SLOTS;
        boolean moved;
        if (index < tier.capacity()) {
            moved = moveItemStackTo(stack, tier.capacity(), slots.size(), true);
        } else {
            moved = StorageBagContents.accepts(kind, stack)
                    && moveItemStackTo(stack, pageStart, pageStart + visibleSlots(), false);
        }
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
