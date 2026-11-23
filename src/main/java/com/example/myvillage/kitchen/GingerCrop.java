package com.example.myvillage.kitchen;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class GingerCrop extends CropBlock {
    public static final MapCodec<GingerCrop> CODEC = simpleCodec(GingerCrop::new);
    public GingerCrop(BlockBehaviour.Properties properties) { super(properties); }
    @Override public MapCodec<? extends CropBlock> codec() { return CODEC; }
    @Override protected ItemLike getBaseSeedId() { return KitchenContent.item(KitchenRules.get().ginger); }
}
