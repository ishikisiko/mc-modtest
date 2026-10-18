package com.example.myvillage.spiritstone;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Optional accessory integrations register slots here on both physical sides; consumers never import their API. */
public final class SpiritStoneSources {
    public record Slot(String key, Supplier<ItemStack> read, Consumer<ItemStack> write) {
        public Slot { Objects.requireNonNull(key); Objects.requireNonNull(read); Objects.requireNonNull(write); }
    }
    @FunctionalInterface public interface Provider { List<Slot> slots(Player player); }
    private static final Map<ResourceLocation, Provider> PROVIDERS = new LinkedHashMap<>();
    private SpiritStoneSources() { }
    public static synchronized AutoCloseable register(ResourceLocation id, Provider provider) {
        if (PROVIDERS.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(provider)) != null)
            throw new IllegalArgumentException("Duplicate spirit stone source " + id);
        return () -> { synchronized (SpiritStoneSources.class) { PROVIDERS.remove(id, provider); } };
    }
    public static List<Slot> inventorySlots(Inventory inventory) {
        List<Slot> result = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            final int slot = i;
            result.add(new Slot("inventory:" + i, () -> inventory.getItem(slot), value -> {
                inventory.setItem(slot, value);
                // Offhand/armor are absent from most open menus: update the owning inventory explicitly.
                if (slot >= inventory.items.size() && inventory.player instanceof net.minecraft.server.level.ServerPlayer server)
                    server.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2, 0, slot, value.copy()));
            }));
        }
        return result;
    }
    public static List<Slot> slots(Player player, boolean inventoryBagsOnly) {
        List<Slot> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<ItemStack> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        var inventory = inventorySlots(player.getInventory());
        for (int i = 0; i < inventory.size(); i++) {
            Slot slot = inventory.get(i);
            // Always reserve the ordinary 36 slots for item outputs; extra sources are bags only.
            if (inventoryBagsOnly && i >= 36 && !SpiritStoneStorage.isBag(slot.read().get())) continue;
            result.add(slot); keys.add(slot.key()); if (!slot.read().get().isEmpty()) identities.add(slot.read().get());
        }
        List<Map.Entry<ResourceLocation, Provider>> providers;
        synchronized (SpiritStoneSources.class) { providers = List.copyOf(PROVIDERS.entrySet()); }
        for (var provider : providers) for (Slot slot : provider.getValue().slots(player)) {
            ItemStack stack = slot.read().get();
            if (!SpiritStoneStorage.isBag(stack) || !keys.add(slot.key()) || !identities.add(stack)) continue;
            result.add(slot);
        }
        return List.copyOf(result);
    }
    public static Access open(Player player) { return new Access(slots(player, true), () -> player.getInventory().setChanged()); }
    public static final class Access {
        private final List<Slot> slots;
        private final List<ItemStack> original;
        private final Runnable changed;
        public Access(List<Slot> slots, Runnable changed) {
            this.slots = List.copyOf(slots); this.changed = changed;
            original = slots.stream().map(s -> s.read().get().copy()).toList();
        }
        public List<ItemStack> copies() { return original.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new)); }
        public int indexOf(ItemStack stack) {
            for (int i = 0; i < slots.size(); i++) if (slots.get(i).read().get() == stack) return i;
            return -1;
        }
        public boolean current() {
            for (int i = 0; i < slots.size(); i++) if (!ItemStack.matches(original.get(i), slots.get(i).read().get())) return false;
            return true;
        }
        public boolean commit(List<ItemStack> replacement) {
            if (replacement.size() != slots.size() || !current()) return false;
            for (int i = 0; i < slots.size(); i++) if (!ItemStack.matches(original.get(i), replacement.get(i)))
                slots.get(i).write().accept(replacement.get(i).copy());
            changed.run(); return true;
        }
    }
}
