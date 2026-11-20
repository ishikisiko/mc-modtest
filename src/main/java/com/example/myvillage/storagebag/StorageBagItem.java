package com.example.myvillage.storagebag;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;

import java.util.List;

public final class StorageBagItem extends Item {
    private final StorageBagRules.Kind kind;
    private final StorageBagRules.Tier tier;

    StorageBagItem(Properties properties, StorageBagRules.Kind kind, StorageBagRules.Tier tier) {
        super(properties.stacksTo(1));
        this.kind = kind;
        this.tier = tier;
    }

    public StorageBagRules.Kind kind() { return kind; }

    public StorageBagRules.Tier tier() { return tier; }

    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) { return false; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (stack.getCount() != 1 || player.isSpectator()) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer server) {
            int index = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
            StorageBagMenu.open(server, index, this, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (kind != StorageBagRules.Kind.MATERIAL || player == null || !player.isShiftKeyDown()
                || !(state.getBlock() instanceof ChestBlock chest) || context.getItemInHand().getCount() != 1) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            StorageBagChest.unload(server, context.getHand(), context.getItemInHand(), tier.capacity(), pos, state, chest);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.yuanxian.storage_bag.capacity",
                StorageBagContents.occupied(stack), tier.capacity()).withStyle(ChatFormatting.GRAY));
        if (kind == StorageBagRules.Kind.MATERIAL) {
            lines.add(Component.translatable(StorageBagContents.autoPickup(stack)
                    ? "screen.yuanxian.storage_bag.auto_on" : "screen.yuanxian.storage_bag.auto_off").withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("tooltip.yuanxian.storage_bag.unload").withStyle(ChatFormatting.GRAY));
        } else if (kind == StorageBagRules.Kind.MANUAL) {
            lines.add(Component.translatable("tooltip.yuanxian.storage_bag.manual").withStyle(ChatFormatting.GRAY));
        }
    }
}
