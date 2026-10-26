package com.example.myvillage.entity.flight;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Escrow lives in player data, not the unsaved vehicle. There is exactly one return authority. */
public final class FlyingSwordInventory {
    private static final String LANDING = "myvillage:flying_sword_pending_landing";
    private static final String KEY = "myvillage:flying_sword_escrow";
    private FlyingSwordInventory() { }
    public static boolean occupied(ServerPlayer p) { return p.getPersistentData().contains(KEY); }
    public static void take(ServerPlayer p, ItemStack stack) {
        if (occupied(p) || stack.isEmpty()) throw new IllegalStateException("飞剑已封存或物品为空");
        p.getPersistentData().put(KEY, stack.copyWithCount(1).save(p.registryAccess()));
        rememberLanding(p, stack.is(com.example.myvillage.item.ModItems.AZURE_CLOUD_FLYING_SWORD.get()));
        stack.shrink(1);
    }
    public static void rememberLanding(ServerPlayer p, boolean azure) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("dimension", p.level().dimension().location().toString()); tag.putBoolean("azure", azure);
        p.getPersistentData().put(LANDING, tag);
    }
    public static void clearLanding(ServerPlayer p) { p.getPersistentData().remove(LANDING); }
    public static net.minecraft.nbt.CompoundTag pendingLanding(ServerPlayer p) {
        return p.getPersistentData().getCompound(LANDING);
    }
    public static void release(ServerPlayer p) {
        var serialized = FlyingSwordEscrow.claim(
                () -> occupied(p) ? p.getPersistentData().getCompound(KEY) : null,
                () -> p.getPersistentData().remove(KEY));
        if (serialized == null) return;
        ItemStack stack = ItemStack.parseOptional(p.registryAccess(), serialized);
        if (!stack.isEmpty()) {
            // Inventory.add may silently void a full-inventory return in creative mode.
            int slot = p.getInventory().getFreeSlot();
            if (slot < 0) p.drop(stack, false);
            else { p.getInventory().setItem(slot, stack); p.getInventory().setChanged(); }
        }
    }
}
