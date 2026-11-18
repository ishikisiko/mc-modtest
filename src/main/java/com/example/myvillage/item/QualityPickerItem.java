package com.example.myvillage.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

public final class QualityPickerItem extends Item {
    public QualityPickerItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown() || context.getHand() != InteractionHand.MAIN_HAND)
            return InteractionResult.PASS;
        var layout = QualityPickerArrays.selected(context.getItemInHand());
        if (layout == QualityPickerArrays.Layout.NONE) return InteractionResult.PASS;
        if (context.getPlayer() instanceof ServerPlayer player) return QualityPickerArrays.preview(player, context, layout);
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.yuanxian.quality_picker").withStyle(ChatFormatting.GRAY));
        var layout = QualityPickerArrays.selected(stack);
        if (layout != QualityPickerArrays.Layout.NONE) {
            lines.add(Component.literal(layout.title() + " · 创造模式潜行右键地面预览").withStyle(ChatFormatting.GOLD));
        }
    }
}
