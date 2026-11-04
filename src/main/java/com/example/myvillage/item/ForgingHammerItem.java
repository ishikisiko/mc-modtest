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
    private final String hammerId;
    public ForgingHammerItem(Properties properties) { this(properties,"forging_hammer"); }
    public ForgingHammerItem(Properties properties,String id) {
        super(properties.durability(com.example.myvillage.forging.FoundryRules.get().hammer(id).durability()));hammerId=id;
    }
    public com.example.myvillage.forging.FoundryRules.Hammer definition(){return com.example.myvillage.forging.FoundryRules.get().hammer(hammerId);}
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("workshop.myvillage.manual.hammer_hint").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("基础锻造力："+definition().power()+"　工艺成效："+definition().quality()/100+"%").withStyle(ChatFormatting.AQUA));
    }
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged || !ItemStack.isSameItem(oldStack, newStack)) return true;
        var before = oldStack.copy(); var after = newStack.copy();
        before.remove(net.minecraft.core.component.DataComponents.DAMAGE);
        after.remove(net.minecraft.core.component.DataComponents.DAMAGE);
        return !ItemStack.matches(before, after);
    }
}
