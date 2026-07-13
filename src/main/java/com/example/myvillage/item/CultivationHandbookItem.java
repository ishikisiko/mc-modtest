package com.example.myvillage.item;

import com.example.myvillage.MyVillageMod;
import guideme.GuidesCommon;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Opens the GuideME cultivation guide while keeping the handbook stack. */
public final class CultivationHandbookItem extends Item {
    private static final ResourceLocation CULTIVATION_GUIDE =
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "cultivation");

    public CultivationHandbookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            GuidesCommon.openGuide(player, CULTIVATION_GUIDE);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(
                "item.myvillage.cultivation_handbook.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
