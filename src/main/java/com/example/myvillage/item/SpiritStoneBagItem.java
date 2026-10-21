package com.example.myvillage.item;

import com.example.myvillage.spiritstone.*;
import java.util.List;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.level.Level;

/** Ordinary nonstacking item: vanilla inventory/death/drop persistence owns the entire bag. */
public final class SpiritStoneBagItem extends Item {
    public SpiritStoneBagItem(Properties properties) {
        super(properties.stacksTo(1));
    }
    @Override public boolean canFitInsideContainerItems() { return false; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bag = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) withdraw(player, bag);
            else SpiritStoneStorage.autoCollect(bag, !SpiritStoneStorage.autoCollect(bag));
            player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
    private static void withdraw(Player player, ItemStack held) {
        var access = SpiritStoneSources.open(player); int index = access.indexOf(held);
        if (index < 0) return;
        var copies = access.copies(); var bag = copies.get(index); var inside = new java.util.ArrayList<>(SpiritStoneStorage.entries(bag));
        for (int i = 0; i < inside.size(); i++) {
            var entry = inside.get(i);
            int amount = entry.quantity().min(java.math.BigInteger.valueOf(entry.item().getMaxStackSize())).intValueExact();
            ItemStack taken = entry.item().copyWithCount(amount);
            if (SpiritStoneStorage.insertLoose(copies, taken, taken.getCount(), 36) != 0) {
                player.displayClientMessage(Component.translatable("message.myvillage.spirit_stone_bag.full"), true); return;
            }
            var left = entry.quantity().subtract(java.math.BigInteger.valueOf(amount));
            if (left.signum() == 0) inside.remove(i); else inside.set(i, new SpiritStoneLedger.Entry(entry.item(), left));
            SpiritStoneStorage.entries(bag, inside);
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
                SpiritStoneStorage.count(stack, SpiritStoneTags::isLowGrade).toString()).withStyle(ChatFormatting.AQUA));
        for (var tier : SpiritStoneDenominations.get().tiers().subList(1, SpiritStoneDenominations.get().tiers().size())) {
            lines.add(Component.translatable("tooltip.myvillage.spirit_stone_bag.grade",
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(tier.item()).getDescription(),
                    SpiritStoneStorage.count(stack, item -> item.is(tier.tag())).toString()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        lines.add(Component.translatable("tooltip.myvillage.spirit_stone_bag.exchange").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("tooltip.myvillage.spirit_stone_bag.capacity").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.myvillage.spirit_stone_bag." + (SpiritStoneStorage.autoCollect(stack) ? "on" : "off"))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.myvillage.spirit_stone_bag.use").withStyle(ChatFormatting.DARK_GREEN));
        lines.add(Component.translatable("tooltip.myvillage.spirit_stone_bag.withdraw").withStyle(ChatFormatting.DARK_GREEN));
    }
}
