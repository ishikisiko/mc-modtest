package com.example.myvillage.sect;

import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/** 当前宗门蓝图入口；几何、设施和地形均来自编译资源。 */
public final class SectGenerator {
    @FunctionalInterface public interface NaturalHeight { int at(int x,int z); }
    public record BuildArea(int x0,int z0,int width,int depth) { }
    private SectGenerator() { }
    public static BlockPos baseFor(BlockPos anchor) { return SectBlueprint.current().base(anchor); }
    public static BlockPos fitAnchor(ServerLevel level,BlockPos anchor) {
        return SectBlueprint.current().fitAnchor(anchor,level.getMinBuildHeight(),level.getMaxBuildHeight());
    }
    public static BuildArea worldgenBuildArea(BlockPos base) {
        SectBlueprint d=SectBlueprint.current();
        return new BuildArea(base.getX()-d.margin,base.getZ()-d.margin,d.width+2*d.margin,d.depth+2*d.margin);
    }
    public static long seedFromSource(CommandSourceStack source) throws CommandSyntaxException {
        BlockPos pos=source.getPlayerOrException().blockPosition();
        return source.getLevel().getSeed() ^ ((long)pos.getX()<<32) ^ (pos.getZ()&0xffffffffL);
    }
    public static int generate(CommandSourceStack source,long seed) throws CommandSyntaxException {
        return generateAt(source,seed,source.getPlayerOrException().blockPosition());
    }
    public static int generateAt(CommandSourceStack source,long seed,BlockPos anchor) {
        return generateForcedAt(source,seed,null,anchor);
    }
    public static int generateForced(CommandSourceStack source,long seed,String layout) throws CommandSyntaxException {
        return generateForcedAt(source,seed,layout,source.getPlayerOrException().blockPosition());
    }
    public static int generateForcedAt(CommandSourceStack source,long seed,String layout,BlockPos requested) {
        ServerLevel level=source.getLevel();List<ChunkPos> owned=new ArrayList<>();
        try {
            SectBlueprint d=SectBlueprint.current();d.requireLayout(layout);
            BlockPos anchor=fitAnchor(level,requested),base=baseFor(anchor);BuildArea area=worldgenBuildArea(base);
            requireBorder(level,area);
            for (int z=Math.floorDiv(area.z0(),16);z<=Math.floorDiv(area.z0()+area.depth()-1,16);z++)
                for (int x=Math.floorDiv(area.x0(),16);x<=Math.floorDiv(area.x0()+area.width()-1,16);x++) {
                    ChunkPos c=new ChunkPos(x,z);
                    if (!level.getForcedChunks().contains(c.toLong())) { level.setChunkForced(x,z,true);owned.add(c); }
                    level.getChunk(x,z);
                }
            int[] surface=new int[area.width()*area.depth()];
            for (int z=0;z<area.depth();z++) for (int x=0;x<area.width();x++)
                surface[z*area.width()+x]=generatorSurface(level,area.x0()+x,area.z0()+z);
            FramedSite site=prepare(level,seed,layout,anchor,(x,z)->surface[(z-area.z0())*area.width()+x-area.x0()]);
            for (int z=Math.floorDiv(area.z0(),16);z<=Math.floorDiv(area.z0()+area.depth()-1,16);z++)
                for (int x=Math.floorDiv(area.x0(),16);x<=Math.floorDiv(area.x0()+area.width()-1,16);x++)
                    site.realizeClip(x*16,z*16,x*16+15,z*16+15);
            source.sendSuccess(()->Component.literal("已生成宗门蓝图 "+d.name+"，入口 "+anchor.toShortString()+"，写入 "+site.blocksWritten()+" 格"),true);
            return 1;
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(SectGenerator.class).error("宗门蓝图建造失败",ex);
            source.sendFailure(Component.literal("宗门蓝图建造失败："+ex.getMessage()));return 0;
        } finally { for (ChunkPos c:owned) level.setChunkForced(c.x,c.z,false); }
    }
    public static int generatorSurface(ServerLevel level,int x,int z) {
        return level.getChunkSource().getGenerator().getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,level.getChunkSource().randomState());
    }
    public static FramedSite prepare(ServerLevel level,long seed,String layout,BlockPos requested,NaturalHeight natural) {
        SectBlueprint d=SectBlueprint.current();d.requireLayout(layout);BlockPos anchor=fitAnchor(level,requested);
        requireBorder(level,worldgenBuildArea(d.base(anchor)));
        return new FramedSite(level,d,anchor,natural);
    }
    private static void requireBorder(ServerLevel level,BuildArea area) {
        if (!level.getWorldBorder().isWithinBounds(new BlockPos(area.x0(),level.getMinBuildHeight(),area.z0()))
            || !level.getWorldBorder().isWithinBounds(new BlockPos(area.x0()+area.width()-1,level.getMinBuildHeight(),area.z0()+area.depth()-1)))
            throw new IllegalArgumentException("宗门蓝图超出世界边界");
    }
    public static final class FramedSite {
        private final ServerLevel level;private final SectBlueprint data;private final BlockPos anchor,base;
        private final NaturalHeight natural;private long written;
        private FramedSite(ServerLevel level,SectBlueprint data,BlockPos anchor,NaturalHeight natural) {
            this.level=level;this.data=data;this.anchor=anchor;this.base=data.base(anchor);this.natural=natural;
        }
        public BlockPos anchor() { return anchor; }
        public BlockPos base() { return base; }
        public int blocksPlaced() { return Math.toIntExact(written); }
        public long blocksWritten() { return written; }
        public void realizeClip(int x0,int z0,int x1,int z1) {
            BoundingBox box=new BoundingBox(x0,level.getMinBuildHeight(),z0,x1,level.getMaxBuildHeight()-1,z1);
            written+=SectBlueprintPlacement.place(level,data,base,box,natural,new SectBlueprintPlacement.Sink() {
                public BlockState get(BlockPos p) { return level.getBlockState(p); }
                public void set(BlockPos p,BlockState state,CompoundTag nbt) {
                    if (!box.isInside(p)) throw new IllegalStateException("宗门写入越过当前clip");
                    level.setBlock(p,state,SectBlueprintPlacement.FLAGS);
                    if (nbt!=null) {
                        BlockEntity be=level.getBlockEntity(p);
                        if (be==null) throw new IllegalStateException("宗门方块实体没有创建");
                        CompoundTag copy=nbt.copy();copy.putInt("x",p.getX());copy.putInt("y",p.getY());copy.putInt("z",p.getZ());
                        be.loadWithComponents(copy,level.registryAccess());be.setChanged();
                    }
                }
            });
        }
    }
}
