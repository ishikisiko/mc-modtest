package com.example.myvillage.kitchen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class KitchenPrepBlockEntity extends BlockEntity {
    private ItemStack input = ItemStack.EMPTY;

    public KitchenPrepBlockEntity(BlockPos pos, BlockState state) {
        super(KitchenContent.PREP_ENTITY.get(), pos, state);
    }

    private KitchenRules.Station station() {
        return ((KitchenStationBlock) getBlockState().getBlock()).station();
    }

    public void deposit(ServerPlayer player, ItemStack offered) {
        if (player.isSpectator()) return;
        if (!input.isEmpty() && !ItemStack.isSameItemSameComponents(input, offered)) {
            message(player, "occupied", input.getHoverName());
            return;
        }
        int amount = Math.min(offered.getCount(), offered.getMaxStackSize() - input.getCount());
        if (amount <= 0) return;
        if (input.isEmpty()) input = offered.copyWithCount(amount);
        else input.grow(amount);
        if (!player.isCreative()) offered.shrink(amount);
        setChanged();
        message(player, station() == KitchenRules.Station.MILL ? "loaded_mill" : "loaded_board",
                input.getHoverName(), input.getCount());
    }

    public void useEmptyHand(ServerPlayer player) {
        if (player.isSpectator()) return;
        if (player.isShiftKeyDown() && !input.isEmpty()) {
            player.getInventory().placeItemBackInInventory(takeInput());
        } else if (station() == KitchenRules.Station.MILL) {
            process(player, null);
        } else {
            message(player, "board_help");
        }
    }

    public void process(ServerPlayer player, InteractionHand hand) {
        if (player.isSpectator() || level == null) return;
        var recipe = KitchenRules.get().prep(station(), input);
        if (recipe == null) {
            message(player, station() == KitchenRules.Station.MILL ? "mill_help" : "board_help");
            return;
        }
        ItemStack knife = hand == null ? ItemStack.EMPTY : player.getItemInHand(hand);
        if (station() == KitchenRules.Station.BOARD && !knife.is(KitchenContent.KNIFE.get())) return;
        int perRun = recipe.ingredients().getFirst().count();
        int batches = Math.min(input.getCount() / perRun, KitchenRules.get().batchLimit);
        if (knife.isDamageableItem() && !player.isCreative()) {
            batches = Math.min(batches, Math.max(0, knife.getMaxDamage() - knife.getDamageValue()) / perRun);
        }
        if (batches <= 0) return;
        ItemStack result = recipe.result();
        batches = Math.min(batches, result.getMaxStackSize() / result.getCount());
        if (batches <= 0) return;
        int consumed = batches * perRun;
        int produced = batches * recipe.count();
        input.shrink(consumed);
        setChanged();
        if (hand != null) knife.hurtAndBreak(consumed, player,
                hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        level.playSound(null, worldPosition,
                station() == KitchenRules.Station.MILL ? SoundEvents.GRINDSTONE_USE : SoundEvents.WOOD_HIT,
                SoundSource.BLOCKS, 0.7F, 1.0F);
        player.getInventory().placeItemBackInInventory(result.copyWithCount(produced));
        message(player, "prepared", result.getHoverName(), produced);
    }

    ItemStack takeInput() {
        ItemStack taken = input;
        input = ItemStack.EMPTY;
        setChanged();
        return taken;
    }

    private static void message(ServerPlayer player, String key, Object... arguments) {
        player.displayClientMessage(Component.translatable("message.yuanxian.kitchen." + key, arguments), true);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!input.isEmpty()) tag.put("input", input.save(registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input = ItemStack.parseOptional(registries, tag.getCompound("input"));
    }
}
