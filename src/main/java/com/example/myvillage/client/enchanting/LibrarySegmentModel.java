package com.example.myvillage.client.enchanting;

import com.example.myvillage.enchanting.EnchantingDevice;
import com.example.myvillage.enchanting.EnchantingStructure;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

final class LibrarySegmentModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Boolean> HIDDEN = new ModelProperty<>();
    private final List<EnchantingStructure.Cell> relativeCells;

    LibrarySegmentModel(BakedModel originalModel, String part) {
        super(originalModel);
        relativeCells = EnchantingStructure.LIBRARY.stream().filter(cell -> cell.part().equals(part)).toList();
    }

    @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        boolean hidden = ownedByFormedLibrary(level, pos);
        return originalModel.getModelData(level, pos, state, data).derive().with(HIDDEN, hidden).build();
    }

    private boolean ownedByFormedLibrary(BlockAndTintGetter level, BlockPos pos) {
        for (Direction front : Direction.Plane.HORIZONTAL) for (var cell : relativeCells) {
            // The operation port is at (0,1,5) relative to the base center.
            var port = EnchantingStructure.offset(pos, front, -cell.x(), 1 - cell.y(), 5 - cell.z());
            if (level.getBlockEntity(port) instanceof EnchantingDevice controller && controller.library()
                    && controller.front() == front && controller.formed() && !controller.isRemoved()) return true;
        }
        return false;
    }

    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
            ModelData data, @Nullable RenderType layer) {
        return state != null && Boolean.TRUE.equals(data.get(HIDDEN)) ? List.of()
                : originalModel.getQuads(state, side, random, data, layer);
    }
}
