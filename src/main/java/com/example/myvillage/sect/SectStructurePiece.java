package com.example.myvillage.sect;

import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** 自然宗门仅处理当前chunk；与命簿分帧实现读取同一份NBT与高度场。 */
public final class SectStructurePiece extends StructurePiece {
    private final BlockPos base;private final long seed;
    SectStructurePiece(BlockPos base,long seed) {
        super(SectStructures.SECT_PIECE.get(),0,box(base));this.base=base;this.seed=seed;
    }
    public SectStructurePiece(CompoundTag tag) {
        super(SectStructures.SECT_PIECE.get(),tag);
        SectBlueprint d=SectBlueprint.current();
        if (!tag.getString("blueprint").equals(d.id.toString()) || !tag.getString("fingerprint").equals(d.fingerprint))
            throw new IllegalArgumentException("宗门结构记录与当前蓝图不一致");
        base=new BlockPos(tag.getInt("bx"),tag.getInt("by"),tag.getInt("bz"));seed=tag.getLong("seed");
    }
    private static BoundingBox box(BlockPos base) {
        SectBlueprint d=SectBlueprint.current();
        return new BoundingBox(base.getX()-d.margin,base.getY(),base.getZ()-d.margin,
                base.getX()+d.width+d.margin-1,base.getY()+d.maxY,base.getZ()+d.depth+d.margin-1);
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        tag.putInt("bx",base.getX());tag.putInt("by",base.getY());tag.putInt("bz",base.getZ());tag.putLong("seed",seed);
        tag.putString("blueprint",SectBlueprint.current().id.toString());tag.putString("fingerprint",SectBlueprint.current().fingerprint);
    }
    @Override public void postProcess(WorldGenLevel world,StructureManager structures,ChunkGenerator generator,
            RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos position) {
        ServerLevel level=world.getLevel();HashMap<Long,Integer> heights=new HashMap<>();
        SectGenerator.NaturalHeight natural=(x,z)->heights.computeIfAbsent(SectBlueprint.key(x,z),
            k->generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,world,level.getChunkSource().randomState()));
        SectBlueprintPlacement.place(level,SectBlueprint.current(),base,clip,natural,new SectBlueprintPlacement.Sink() {
            public BlockState get(BlockPos p) { return world.getBlockState(p); }
            public void set(BlockPos p,BlockState state,CompoundTag entity) {
                if (!clip.isInside(p)) throw new IllegalStateException("宗门世界生成越过当前chunk");
                world.setBlock(p,state,SectBlueprintPlacement.FLAGS);
                if (entity!=null) {
                    CompoundTag copy=entity.copy();copy.putInt("x",p.getX());copy.putInt("y",p.getY());copy.putInt("z",p.getZ());
                    world.getChunk(p).setBlockEntityNbt(copy);
                }
            }
        });
    }
}
