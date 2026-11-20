package com.example.myvillage.item;

import com.example.myvillage.cultivation.CultivationInitiationMessages;
import com.example.myvillage.cultivation.technique.TechniqueInheritanceService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The printed copy grants the same initiation as the inheritance stele. */
public final class BasicBreathingManualItem extends Item {
    public BasicBreathingManualItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            var outcome = TechniqueInheritanceService.inheritBasicBreathing(serverPlayer);
            CultivationInitiationMessages.inheritance(serverPlayer, outcome)
                    .forEach(message -> serverPlayer.displayClientMessage(message, false));
            if (outcome.success() && !player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
