package com.example.myvillage.storagebag;

import com.example.myvillage.MyVillageMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID)
public final class StorageBagPickup {
    private StorageBagPickup() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()
                || event.canPickup().isFalse()) return;
        var entity = event.getItemEntity();
        if (entity.isRemoved() || !event.canPickup().isTrue() && (entity.hasPickUpDelay()
                || entity.getTarget() != null && !entity.getTarget().equals(player.getUUID()))) return;
        var source = entity.getItem();
        if (!StorageBagContents.accepts(StorageBagRules.Kind.MATERIAL, source)) return;

        int remaining = source.getCount();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && remaining > 0; slot++) {
            remaining = fill(player, slot, source, remaining);
        }
        if (remaining > 0) remaining = fill(player, Inventory.SLOT_OFFHAND, source, remaining);
        int moved = source.getCount() - remaining;
        if (moved == 0) return;

        var original = source.copy();
        player.take(entity, moved);
        player.awardStat(Stats.ITEM_PICKED_UP.get(source.getItem()), moved);
        source.setCount(remaining);
        EventHooks.fireItemPickupPost(entity, player, original);
        player.onItemPickup(entity);
        player.containerMenu.broadcastChanges();
        if (source.isEmpty()) {
            event.setCanPickup(TriState.FALSE);
            entity.discard();
        }
    }

    private static int fill(ServerPlayer player, int slot, ItemStack source, int count) {
        var bag = player.getInventory().getItem(slot);
        if (bag.getCount() != 1 || !(bag.getItem() instanceof StorageBagItem item)
                || item.kind() != StorageBagRules.Kind.MATERIAL || !StorageBagContents.autoPickup(bag)) return count;

        var slots = StorageBagContents.read(bag, item.tier().capacity());
        int remaining = StorageBagContents.insert(slots, source, count, ignored -> source.getMaxStackSize());
        if (remaining != count) {
            StorageBagContents.write(bag, slots);
            StorageBagMenu.contentsChanged(player, slot, bag);
        }
        return remaining;
    }
}
