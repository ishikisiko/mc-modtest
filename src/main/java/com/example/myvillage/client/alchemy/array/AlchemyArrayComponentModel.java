package com.example.myvillage.client.alchemy.array;

import com.example.myvillage.alchemy.array.AlchemyArrayBlock;
import com.example.myvillage.alchemy.array.AlchemyArrayBlockEntity;
import com.example.myvillage.alchemy.array.AlchemyArrayPattern;
import com.example.myvillage.alchemy.array.AlchemyArrayRules;
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

/** Hide only the world mesh of components owned by a server-synchronized formed
 * controller. Item models, selection, collisions and inventories stay intact.
 * No client singleton or world mutation is accessed by chunk baking threads. */
final class AlchemyArrayComponentModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Boolean> FORMED = new ModelProperty<>();

    AlchemyArrayComponentModel(BakedModel original) { super(original); }

    @Override public ModelData getModelData(BlockAndTintGetter view, BlockPos pos,
                                            BlockState state, ModelData data) {
        boolean hidden = false;
        if (state.getBlock() instanceof AlchemyArrayBlock part) {
            hidden = AlchemyArrayPattern.hasPresentationOwner(pos, part.role(), (origin, facing) -> {
                BlockState root = view.getBlockState(origin);
                return root.getBlock() instanceof AlchemyArrayBlock block
                        && block.role() == AlchemyArrayRules.Role.CONTROLLER
                        && root.getValue(AlchemyArrayBlock.FACING) == facing
                        && view.getBlockEntity(origin) instanceof AlchemyArrayBlockEntity controller
                        && !controller.isRemoved() && controller.formed();
            });
        }
        return originalModel.getModelData(view, pos, state, data).derive().with(FORMED, hidden).build();
    }

    @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                              RandomSource random, ModelData data,
                                              @Nullable RenderType layer) {
        return Boolean.TRUE.equals(data.get(FORMED)) ? List.of()
                : originalModel.getQuads(state, side, random, data, layer);
    }
}
