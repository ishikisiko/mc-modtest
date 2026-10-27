package com.example.myvillage.forging;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.block.ModBlocks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Sneak-use is the station's panel shortcut, including while holding a workpiece or hammer. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class PhysicalForgeInteractions {
    private PhysicalForgeInteractions() {}
    static boolean requestsPanel(boolean sneaking, InteractionHand hand, ItemStack held) {
        // Keep vanilla sneak-placement of building blocks against the station.
        return sneaking && hand == InteractionHand.MAIN_HAND && !(held.getItem() instanceof BlockItem);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getUseBlock() == TriState.FALSE || event.getEntity().isSpectator()) return;
        if (event.getLevel().getBlockState(event.getPos()).is(ModBlocks.FORGING_ANVIL.get())
                && requestsPanel(event.getEntity().isShiftKeyDown(), event.getHand(), event.getItemStack()))
            event.setUseBlock(TriState.TRUE);
    }
}
