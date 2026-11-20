package com.example.myvillage.forging;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

final class WorkshopGrading {
    static final TagKey<Block> SOILS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("yuanxian", "workshop_grading_soils"));

    record Result(int cut, int filled) {}

    private WorkshopGrading() {}

    static Result grade(ServerPlayer player, ItemStack tool, BlockPos center, int height, int radius, int depth) {
        if (Math.abs(height - center.getY()) > depth) return new Result(0, 0);
        List<BlockPos> columns = new ArrayList<>();
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                var surface = surface(player, center.offset(x, 0, z), height, depth);
                if (surface != null) columns.add(surface);
            }
        }

        var level = player.serverLevel();
        var material = new ArrayDeque<ItemEntity>();
        int cut = 0;
        for (BlockPos top : columns) {
            for (BlockPos pos = top; pos.getY() > height; pos = pos.below()) {
                if (!held(player, tool)) return new Result(cut, 0);
                var state = level.getBlockState(pos);
                if (!state.is(SOILS) || !level.getBlockState(pos.above()).isAir()) break;
                var result = WorkshopToolWork.breakWithDrops(player, pos, state);
                if (!result.broken()) break;
                cut++;
                result.drops().stream().filter(entity -> usableSoil(entity.getItem())).forEach(material::addLast);
            }
        }

        int filled = 0;
        for (BlockPos top : columns) {
            for (BlockPos pos = top.above(); pos.getY() <= height; pos = pos.above()) {
                if (!held(player, tool) || material.isEmpty()) return new Result(cut, filled);
                while (!material.isEmpty() && (!material.getFirst().isAlive() || material.getFirst().getItem().isEmpty()))
                    material.removeFirst();
                if (material.isEmpty()) return new Result(cut, filled);
                var source = material.getFirst();
                if (!WorkshopToolWork.placeOne(player, pos, source.getItem())) break;
                WorkshopToolWork.consumeDrop(source, 1);
                filled++;
            }
        }
        return new Result(cut, filled);
    }

    private static BlockPos surface(ServerPlayer player, BlockPos column, int height, int depth) {
        var level = player.serverLevel();
        BlockPos pos = column.atY(height + depth);
        if (!WorkshopToolWork.accessible(player, pos) || !level.getBlockState(pos.above()).isAir()) return null;
        for (; pos.getY() >= height - depth; pos = pos.below()) {
            var state = level.getBlockState(pos);
            if (state.isAir()) continue;
            return state.is(SOILS) && state.getFluidState().isEmpty() ? pos : null;
        }
        return null;
    }

    private static boolean usableSoil(ItemStack drop) {
        return drop.getItem() instanceof BlockItem block && block.getBlock().defaultBlockState().is(SOILS);
    }

    private static boolean held(ServerPlayer player, ItemStack tool) {
        return !tool.isEmpty() && player.getMainHandItem() == tool && player.isAlive();
    }
}
