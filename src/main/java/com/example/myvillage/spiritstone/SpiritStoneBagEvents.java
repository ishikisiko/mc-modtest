package com.example.myvillage.spiritstone;

import com.example.myvillage.MyVillageMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID)
public final class SpiritStoneBagEvents {
    private SpiritStoneBagEvents() { }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()
                || player.tickCount % SpiritStoneBagRules.get().collectionIntervalTicks() != 0) return;
        var access = SpiritStoneSources.open(player); var copies = access.copies();
        if (SpiritStoneStorage.collect(copies) && access.commit(copies)) player.containerMenu.broadcastChanges();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void pickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator() || event.canPickup().isFalse()) return;
        var entity = event.getItemEntity(); var source = entity.getItem();
        if (!SpiritStoneStorage.collectible(source) || entity.isRemoved()
                || !event.canPickup().isTrue() && (entity.hasPickUpDelay() || entity.getTarget() != null && !entity.getTarget().equals(player.getUUID()))) return;
        var original = source.copy();
        var access = SpiritStoneSources.open(player); var copies = access.copies();
        int remaining = SpiritStoneStorage.insertBags(copies, source, source.getCount(), true);
        int moved = source.getCount() - remaining;
        if (moved == 0 || !access.commit(copies)) return;
        player.take(entity, moved); player.awardStat(Stats.ITEM_PICKED_UP.get(source.getItem()), moved);
        source.setCount(remaining);
        net.neoforged.neoforge.event.EventHooks.fireItemPickupPost(entity, player, original);
        player.onItemPickup(entity); player.containerMenu.broadcastChanges();
        if (source.isEmpty()) { event.setCanPickup(TriState.FALSE); entity.discard(); }
    }
}
