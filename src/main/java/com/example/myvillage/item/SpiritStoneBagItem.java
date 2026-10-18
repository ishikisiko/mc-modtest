package com.example.myvillage.item;

import com.example.myvillage.spiritstone.*;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/** Ordinary nonstacking item: vanilla inventory/death/drop persistence owns the entire bag. */
public final class SpiritStoneBagItem extends Item {
    public SpiritStoneBagItem(Properties properties) {
        super(properties.stacksTo(1).component(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
    }
    @Override public boolean canFitInsideContainerItems() { return false; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bag = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) withdraw(player, bag);
            else SpiritStoneStorage.autoCollect(bag, !SpiritStoneStorage.autoCollect(bag));
            player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
        }
        return InteractionResultHolder.sidedSuccess(bag, level.isClientSide());
    }
    private static void withdraw(Player player, ItemStack held) {
        var access = SpiritStoneSources.open(player); int index = access.indexOf(held);
        if (index < 0) return;
        var copies = access.copies(); var bag = copies.get(index); var inside = SpiritStoneStorage.contents(bag);
        for (int i = 0; i < inside.size(); i++) if (!inside.get(i).isEmpty()) {
            ItemStack taken = inside.get(i).copy();
            if (SpiritStoneStorage.insertLoose(copies, taken, taken.getCount(), 36) != 0) {
                player.displayClientMessage(Component.translatable("message.myvillage.spirit_stone_bag.full"), true); return;
            }
            inside.set(i, ItemStack.EMPTY); SpiritStoneStorage.contents(bag, inside);
            SpiritStoneStorage.autoCollect(bag, false); access.commit(copies); return;
        }
        // Empty bags can still be paused, useful when preparing manual recipe ingredients.
        SpiritStoneStorage.autoCollect(bag, false); access.commit(copies);
    }
    @Override public boolean overrideOtherStackedOnMe(ItemStack bag, ItemStack other, Slot slot,
                                                       ClickAction action, Player player, SlotAccess cursor) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player) || !SpiritStoneStorage.collectible(other)) return false;
        if (!player.level().isClientSide()) {
            int remaining = SpiritStoneStorage.insert(bag, other, other.getCount(), false);
            other.setCount(remaining); player.getInventory().setChanged();
        }
        return true;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.myvillage.spirit_stone_bag.balance",
                SpiritStoneStorage.count(stack, SpiritStoneTags::isLowGrade)).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.myvillage.spirit_stone_bag.capacity", SpiritStoneBagRules.get().slots()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.myvillage.spirit_stone_bag." + (SpiritStoneStorage.autoCollect(stack) ? "on" : "off"))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.myvillage.spirit_stone_bag.use").withStyle(ChatFormatting.DARK_GREEN));
        lines.add(Component.translatable("tooltip.myvillage.spirit_stone_bag.withdraw").withStyle(ChatFormatting.DARK_GREEN));
    }
}
