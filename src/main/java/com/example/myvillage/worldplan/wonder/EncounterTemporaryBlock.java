package com.example.myvillage.worldplan.wonder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** A persisted scheduled tick recovers overlays that were saved during a phase or chunk unload. */
public final class EncounterTemporaryBlock extends Block {
    private final boolean walkway;

    public EncounterTemporaryBlock(Properties properties, boolean walkway) {
        super(properties);
        this.walkway = walkway;
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var playerSpace = new AABB(pos).expandTowards(0, 2, 0).inflate(.35, 0, .35);
        if (FieldArenaEncounter.ownsTemporary(level, pos, walkway)
                || walkway && level.players().stream().anyMatch(p -> p.isAlive() && !p.isSpectator()
                        && p.getBoundingBox().intersects(playerSpace))) {
            level.scheduleTick(pos, this, 30);
            return;
        }
        FieldArenaTerrain.remove(level, pos, this);
    }
}
