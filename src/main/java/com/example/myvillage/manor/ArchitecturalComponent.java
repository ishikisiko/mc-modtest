package com.example.myvillage.manor;

import net.minecraft.world.level.block.state.BlockState;

/** 细分建筑共用的碰撞与透明面轮廓，不依赖客户端。 */
public interface ArchitecturalComponent {
    ManorCatalog.Profile profile(BlockState state);
}
