package com.example.myvillage.production;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Places fixed mineral sources on natural cave floors with room for at least one rig orientation. */
final class MineEyeFeature extends Feature<NoneFeatureConfiguration> {
    MineEyeFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        var level = context.level();
        var chunk = new ChunkPos(context.origin());
        var rules = ProductionRules.get().generation();
        var random = context.random();
        int min = Math.max(rules.minY(), level.getMinBuildHeight() + 2);
        for (int i = 0; i < rules.columns(); i++) {
            // Two cells of margin keep the complete machine in the eye's chunk.
            int x = chunk.getMinBlockX() + 2 + random.nextInt(12);
            int z = chunk.getMinBlockZ() + 2 + random.nextInt(12);
            int max = Math.min(rules.maxY(), level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 8);
            if (max < min) continue;
            int height = max - min + 1;
            int start = random.nextInt(height);
            for (int dy = 0; dy < height; dy++) {
                int y = min + (start + dy) % height;
                var pos = new BlockPos(x, y, z);
                if (!level.getBlockState(pos).is(BlockTags.BASE_STONE_OVERWORLD) || !level.getBlockState(pos.above()).isAir()) continue;
                if (!rigFits(level, pos)) continue;
                var eye = y <= rules.xuantieY() ? ProductionPart.XUANTIE_MINE_EYE : ProductionPart.IRON_MINE_EYE;
                return level.setBlock(pos, ProductionContent.block(eye).defaultBlockState(), 2);
            }
        }
        return false;
    }

    private static boolean rigFits(WorldGenLevel level, BlockPos pos) {
        for (var direction : Direction.Plane.HORIZONTAL) {
            var layout = ProductionPattern.layout(ProductionKind.MINE, direction);
            boolean clear = true;
            for (var cell : layout.cells()) {
                BlockPos at = pos.offset(cell.offset());
                if (!level.getBlockState(at).isAir()
                        || cell.offset().getY() == 1 && !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
                    clear = false;
                    break;
                }
            }
            if (clear && level.getBlockState(pos.relative(direction).above()).isAir()
                    && level.getBlockState(pos.relative(direction).above(2)).isAir()) return true;
        }
        return false;
    }
}
