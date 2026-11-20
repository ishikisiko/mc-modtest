package com.example.myvillage.storagebag;

import com.example.myvillage.forging.ForgingCosts;
import com.example.myvillage.forging.PreparationBlockEntity;
import com.example.myvillage.forging.array.ForgingArrayBlockEntity;
import com.example.myvillage.spiritstone.SpiritStoneSources;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;

/** The station validates the next recipe and commits the old bag, all costs and one result together. */
public final class StorageBagForgeMenu extends AbstractContainerMenu {
    public static final int OPEN_BUTTON = 5;
    public static final int START = 0;
    public static final int SELECT_KIND = 1;
    public static final int WIDTH = 230, HEIGHT = 220;
    public static final int INPUT_X = 32, OUTPUT_X = 182, WORK_Y = 59;
    public static final int INVENTORY_X = 34, INVENTORY_Y = 137;

    public record Recipe(StorageBagRules.Kind kind, int tierIndex) {
        public StorageBagRules.Tier tier() { return StorageBagRules.tiers().get(tierIndex); }
    }

    private final Inventory inventory;
    private final BlockEntity station;
    private final StorageBagRules.Station kind;
    private final SimpleContainer work = new SimpleContainer(2);
    private final DataSlot selected = DataSlot.standalone();
    private final DataSlot status = DataSlot.standalone();

    public StorageBagForgeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, null, buffer.readEnum(StorageBagRules.Station.class));
    }

    private StorageBagForgeMenu(int id, Inventory inventory, BlockEntity station, StorageBagRules.Station kind) {
        super(StorageBagContent.FORGE_MENU.get(), id);
        this.inventory = inventory;
        this.station = station;
        this.kind = kind;
        addSlot(new Slot(work, 0, INPUT_X, WORK_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof StorageBagItem; }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(work, 1, OUTPUT_X, WORK_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int i = 0; i < 27; i++) {
            addSlot(new Slot(inventory, i + 9, INVENTORY_X + i % 9 * 18, INVENTORY_Y + i / 9 * 18));
        }
        for (int i = 0; i < 9; i++) addSlot(new Slot(inventory, i, INVENTORY_X + i * 18, INVENTORY_Y + 58));
        work.addListener(ignored -> status.set(0));
        addDataSlot(selected);
        addDataSlot(status);
    }

    public static boolean open(ServerPlayer player, BlockEntity station) {
        if (station.isRemoved() || station.getLevel() != player.level() || !player.isAlive() || player.isSpectator()
                || !Container.stillValidBlockEntity(station, player)) return false;
        StorageBagRules.Station kind;
        if (station instanceof PreparationBlockEntity) kind = StorageBagRules.Station.DING;
        else if (station instanceof ForgingArrayBlockEntity) kind = StorageBagRules.Station.ARRAY;
        else return false;
        return player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                        new StorageBagForgeMenu(id, inventory, station, kind),
                        Component.translatable("screen.yuanxian.storage_bag.forge")),
                buffer -> buffer.writeEnum(kind)).isPresent();
    }

    public ItemStack input() { return work.getItem(0); }
    public ItemStack output() { return work.getItem(1); }
    public int status() { return status.get(); }
    public StorageBagRules.Station stationKind() { return kind; }
    public StorageBagRules.Kind selectedKind() { return StorageBagRules.Kind.values()[selected.get()]; }

    public Optional<Recipe> recipe() {
        if (input().isEmpty()) {
            return kind == StorageBagRules.Station.DING ? Optional.of(new Recipe(selectedKind(), 0)) : Optional.empty();
        }
        return upgradeRecipe(input());
    }

    static Optional<Recipe> upgradeRecipe(ItemStack input) {
        if (input.getCount() != 1 || !(input.getItem() instanceof StorageBagItem bag)) return Optional.empty();
        int index = StorageBagRules.tiers().indexOf(bag.tier());
        if (index < 0 || index + 1 >= StorageBagRules.tiers().size()) return Optional.empty();
        return Optional.of(new Recipe(bag.kind(), index + 1));
    }

    @Override
    public boolean stillValid(Player player) {
        return player == inventory.player && player.isAlive() && !player.isSpectator()
                && (station == null || !station.isRemoved() && Container.stillValidBlockEntity(station, player));
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer server) || station == null || player.containerMenu != this
                || !stillValid(player)) return false;
        int selection = button - SELECT_KIND;
        if (selection >= 0 && selection < StorageBagRules.Kind.values().length
                && kind == StorageBagRules.Station.DING && input().isEmpty() && output().isEmpty()) {
            selected.set(selection);
            return finish(0);
        }
        return button == START && forge(server);
    }

    private boolean forge(ServerPlayer player) {
        if (!output().isEmpty()) return finish(7);
        var recipe = recipe().orElse(null);
        if (recipe == null) return finish(6);
        var tier = recipe.tier();
        if (tier.station() == StorageBagRules.Station.DING) {
            if (!(station instanceof PreparationBlockEntity ding) || ding.active()) return finish(4);
        } else if (!(station instanceof ForgingArrayBlockEntity array) || array.locked() || !array.freshCheck().formed()) {
            return finish(5);
        }
        var access = StorageBagRules.realmAccess(player, tier);
        if (access == StorageBagRules.RealmAccess.UNAVAILABLE) return finish(8);
        if (access == StorageBagRules.RealmAccess.LOW) return finish(2);

        var source = SpiritStoneSources.open(player);
        var payment = ForgingCosts.plan(source.copies(), tier.costs());
        if (payment.isEmpty()) return finish(3);
        var result = StorageBagContent.result(recipe.kind(), recipe.tierIndex(), input());
        if (!source.commit(payment.get())) return finish(3);
        work.setItem(0, ItemStack.EMPTY);
        work.setItem(1, result);
        return finish(1);
    }

    private boolean finish(int state) {
        status.set(state);
        broadcastChanges();
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) clearContainer(player, work);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var before = stack.copy();
        boolean moved = index < 2 ? moveItemStackTo(stack, 2, slots.size(), true)
                : stack.getItem() instanceof StorageBagItem && moveItemStackTo(stack, 0, 1, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return before;
    }
}
