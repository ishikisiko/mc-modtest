package com.example.myvillage.forging;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.item.ModDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Covers supported finished-equipment recipes outside the physical workshop. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class ForgingCompletion {
    private ForgingCompletion() { }

    public static int quality(ServerPlayer player) {
        // Ordinary tools remain craftable before awakening; their initial quality is lower.
        return Math.max(1,EquipmentInfusion.qualityCap(player));
    }

    public static void stamp(ItemStack result,int quality) {
        ForgeMark.requireQuality(quality);
        if(WorkshopEquipment.craft(result).isPresent()&&!result.has(ModDataComponents.FORGE_MARK.get()))
            result.set(ModDataComponents.FORGE_MARK.get(),ForgeMark.uninfused(quality));
    }

    @SubscribeEvent
    public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if(event.getEntity() instanceof ServerPlayer player)stamp(event.getCrafting(),quality(player));
    }
}
