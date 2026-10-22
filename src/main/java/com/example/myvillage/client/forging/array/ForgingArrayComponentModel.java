package com.example.myvillage.client.forging.array;

import com.example.myvillage.forging.array.ForgingArrayBlock;
import com.example.myvillage.forging.array.ForgingArrayBlockEntity;
import com.example.myvillage.forging.array.ForgingArrayPattern;
import com.example.myvillage.forging.array.ForgingArrayRules;
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

/** 仅隐藏服务器已同步成形的世界组件；区块烘焙线程不访问客户端单例。 */
final class ForgingArrayComponentModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Boolean> FORMED = new ModelProperty<>();

    ForgingArrayComponentModel(BakedModel original) { super(original); }

    @Override public ModelData getModelData(BlockAndTintGetter view, BlockPos pos,
                                            BlockState state, ModelData data) {
        boolean hidden = false;
        if (state.getBlock() instanceof ForgingArrayBlock part) {
            hidden = ForgingArrayPattern.hasPresentationOwner(pos, part.role(), (origin, facing) -> {
                BlockState root = view.getBlockState(origin);
                return root.getBlock() instanceof ForgingArrayBlock block
                        && block.role() == ForgingArrayRules.Role.CONTROLLER
                        && root.getValue(ForgingArrayBlock.FACING) == facing
                        && view.getBlockEntity(origin) instanceof ForgingArrayBlockEntity controller
                        && !controller.isRemoved() && controller.formed();
            });
        }
        return originalModel.getModelData(view, pos, state, data).derive().with(FORMED, hidden).build();
    }

    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                              RandomSource random, ModelData data,
                                              @Nullable RenderType layer) {
        return state != null && Boolean.TRUE.equals(data.get(FORMED)) ? List.of()
                : originalModel.getQuads(state, side, random, data, layer);
    }
}
