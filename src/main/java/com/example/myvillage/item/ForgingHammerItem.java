package com.example.myvillage.item;

import com.example.myvillage.forging.WorkshopRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

/** The anvil handles use-on-block; only its successful server contact consumes durability. */
public final class ForgingHammerItem extends Item {
    public ForgingHammerItem(Properties properties) { super(properties.durability(WorkshopRules.get().hammer_durability())); }
    @Override public boolean doesSneakBypassUse(ItemStack stack, net.minecraft.world.level.LevelReader level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player) {
        return level.getBlockState(pos).is(com.example.myvillage.block.ModBlocks.FORGING_ANVIL.get());
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("workshop.myvillage.manual.hammer_hint").withStyle(ChatFormatting.GRAY));
    }
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged || !ItemStack.isSameItem(oldStack, newStack)) return true;
        var before = oldStack.copy(); var after = newStack.copy();
        before.remove(net.minecraft.core.component.DataComponents.DAMAGE);
        after.remove(net.minecraft.core.component.DataComponents.DAMAGE);
        return !ItemStack.matches(before, after);
    }
}
