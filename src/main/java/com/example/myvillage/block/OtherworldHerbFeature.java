package com.example.myvillage.block;

import com.example.myvillage.MyVillageMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Places the wild crop and its own growing medium as one neighboring pair. */
public final class OtherworldHerbFeature extends Feature<OtherworldHerbFeature.Config> {
    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, MyVillageMod.MOD_ID);

    static { FEATURES.register("otherworld_herb", OtherworldHerbFeature::new); }

    public record Config(BlockState crop, Block ground) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockState.CODEC.fieldOf("crop").forGetter(Config::crop),
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("ground").forGetter(Config::ground)
        ).apply(instance, Config::new));
    }

    public OtherworldHerbFeature() { super(Config.CODEC); }

    public static void register(IEventBus bus) { FEATURES.register(bus); }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        var config = context.config();
        if (!(config.crop().getBlock() instanceof SpiritCropBlock crop)) return false;
        var soil = crop.requiredSoil().orElse(null);
        if (soil == null) return false;
        var level = context.level();
        var pos = context.origin();
        var below = pos.below();
        if (level.isOutsideBuildHeight(pos) || level.isOutsideBuildHeight(below)
                || !level.ensureCanWrite(pos) || !level.ensureCanWrite(below)
                || !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) || !level.isEmptyBlock(pos)) return false;
        var ground = level.getBlockState(below);
        if (!ground.is(config.ground()) || !ground.getFluidState().isEmpty()) return false;
        if (!level.setBlock(below, soil.defaultBlockState(), Block.UPDATE_CLIENTS)) return false;
        if (level.setBlock(pos, config.crop(), Block.UPDATE_CLIENTS)) return true;
        level.setBlock(below, ground, Block.UPDATE_CLIENTS);
        return false;
    }
}
