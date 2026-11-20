package com.example.myvillage.storagebag;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

final class StorageBagChest {
    private StorageBagChest() {}

    static void unload(ServerPlayer player, InteractionHand hand, ItemStack bag, int capacity,
                       BlockPos pos, BlockState state, ChestBlock block) {
        var level = player.serverLevel();
        if (!player.isAlive() || player.isSpectator() || !mayOpen(player, pos)) return;
        if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                && !mayOpen(player, pos.relative(ChestBlock.getConnectedDirection(state)))) return;
        var chest = ChestBlock.getContainer(block, state, level, pos, false);
        if (chest == null) return;

        var contents = StorageBagContents.read(bag, capacity);
        int moved = transfer(contents, chest);
        if (moved == 0) {
            player.displayClientMessage(Component.translatable("message.yuanxian.storage_bag.unload_empty"), true);
            return;
        }
        StorageBagContents.write(bag, contents);
        int index = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
        StorageBagMenu.contentsChanged(player, index, bag);
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("message.yuanxian.storage_bag.unloaded", moved), true);
    }

    private static boolean mayOpen(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        return level.hasChunkAt(pos) && level.mayInteract(player, pos)
                && level.getBlockEntity(pos) instanceof BaseContainerBlockEntity entity && entity.canOpen(player);
    }

    /** A chest's original contents select item types. Stacks still merge by exact components. */
    static int transfer(List<ItemStack> contents, Container chest) {
        var plan = new ArrayList<ItemStack>(chest.getContainerSize());
        var types = new HashSet<Item>();
        for (int i = 0; i < chest.getContainerSize(); i++) {
            var stack = chest.getItem(i);
            if (!stack.isEmpty()) types.add(stack.getItem());
            plan.add(stack.copy());
        }
        int moved = 0;
        for (int i = 0; i < contents.size(); i++) {
            var stack = contents.get(i);
            if (stack.isEmpty() || !types.contains(stack.getItem())) continue;
            int left = StorageBagContents.insert(plan, stack, stack.getCount(),
                    slot -> chest.canPlaceItem(slot, stack) ? chest.getMaxStackSize(stack) : 0);
            moved += stack.getCount() - left;
            contents.set(i, left == 0 ? ItemStack.EMPTY : stack.copyWithCount(left));
        }
        if (moved > 0) {
            for (int i = 0; i < plan.size(); i++) {
                if (!ItemStack.matches(plan.get(i), chest.getItem(i))) chest.setItem(i, plan.get(i));
            }
            chest.setChanged();
        }
        return moved;
    }
}
