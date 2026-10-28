package com.example.myvillage.worldplan.travel;

import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;

/** Reads actual loaded blocks and standing-player collision. It never edits or requests a chunk. */
public final class AtlasCityTravelSafety {
    private final AtlasCityTravelRules rules;
    private final List<TagKey<Block>> hazardTags;
    public AtlasCityTravelSafety(AtlasCityTravelRules rules) {
        this.rules = rules;
        hazardTags = rules.hazardTags().stream()
                .map(id -> TagKey.create(Registries.BLOCK,ResourceLocation.parse(id))).toList();
        for (String id : rules.hazardBlocks()) if (!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(id)))
            throw new IllegalArgumentException("未知舆图危险方块："+id);
    }
    public boolean hazardous(BlockState state) {
        return rules.hazardBlocks().contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())
                || hazardTags.stream().anyMatch(state::is);
    }
    public boolean solidDryFloor(BlockState state, BlockGetter blocks, BlockPos floor) {
        return state.getFluidState().isEmpty() && !hazardous(state) && state.isCollisionShapeFullBlock(blocks,floor);
    }
    public static AABB standingBody(ServerPlayer player, AtlasCityLanding.Point point) {
        return player.getDimensions(Pose.STANDING).makeBoundingBox(point.x()+.5,point.y(),point.z()+.5);
    }
    public boolean safe(ServerLevel level, ServerPlayer player, AtlasCityLanding.Point point) {
        AABB body = standingBody(player,point);
        if (point.y() <= level.getMinBuildHeight() || body.maxY >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(body)) return false;
        // Collision implementations may inspect one block beyond the box. Guard that area first.
        int minX = (int)Math.floor(body.minX)-rules.hazardPadding()-1;
        int maxX = (int)Math.floor(body.maxX)+rules.hazardPadding()+1;
        int minZ = (int)Math.floor(body.minZ)-rules.hazardPadding()-1;
        int maxZ = (int)Math.floor(body.maxZ)+rules.hazardPadding()+1;
        for (int z = Math.floorDiv(minZ,16); z <= Math.floorDiv(maxZ,16); z++)
            for (int x = Math.floorDiv(minX,16); x <= Math.floorDiv(maxX,16); x++)
                if (level.getChunkSource().getChunkNow(x,z) == null) return false;

        if (!blocksSafe(level,at -> state(level,at),point,body)) return false;
        // Standing dimensions prevent a crouched/swimming pose from admitting a one-block gap.
        // The level also checks entities and collision shapes that depend on this particular player.
        return level.noCollision(player,body);
    }

    /** Same actual-block predicate can be tested with a small block view, without a running server. */
    public boolean blocksSafe(BlockGetter shapes, Function<BlockPos,BlockState> read,
            AtlasCityLanding.Point point, AABB body) {
        BlockPos floor = new BlockPos(point.x(),point.y()-1,point.z());
        BlockState below = read.apply(floor);
        if (below == null || !solidDryFloor(below,shapes,floor)) return false;
        var bodyShape=Shapes.create(body);
        for (BlockPos at : BlockPos.betweenClosed((int)Math.floor(body.minX)-rules.hazardPadding(),point.y()-1,
                (int)Math.floor(body.minZ)-rules.hazardPadding(),(int)Math.floor(body.maxX)+rules.hazardPadding(),
                (int)Math.floor(Math.nextDown(body.maxY)),(int)Math.floor(body.maxZ)+rules.hazardPadding())) {
            BlockState actual = read.apply(at);
            if (actual == null || !actual.getFluidState().isEmpty() || hazardous(actual)) return false;
            var collision=actual.getCollisionShape(shapes,at,CollisionContext.empty()).move(at.getX(),at.getY(),at.getZ());
            if (Shapes.joinIsNotEmpty(collision,bodyShape,BooleanOp.AND)) return false;
        }
        return true;
    }
    private static BlockState state(ServerLevel level, BlockPos at) {
        var chunk = level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4);
        return chunk == null ? null : chunk.getBlockState(at);
    }
}
