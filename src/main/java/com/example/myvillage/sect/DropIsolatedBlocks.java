package com.example.myvillage.sect;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * Safety net for sect building templates: drops every non-air template block that touches no
 * other non-air template block on any of its six faces (a lone stair of a diagonal eave string, a
 * stray trapdoor), so a template can never leave a fragment floating in the air. Template air is
 * kept (it clears the volume). The templates themselves are fixed at the source as well; this only
 * guards placement.
 */
public final class DropIsolatedBlocks extends StructureProcessor {
    public static final DropIsolatedBlocks INSTANCE = new DropIsolatedBlocks();
    public static final MapCodec<DropIsolatedBlocks> CODEC = MapCodec.unit(() -> INSTANCE);

    private DropIsolatedBlocks() {
    }

    /** The positions of {@code solid} that have no face neighbour in {@code solid}. */
    static Set<BlockPos> isolated(Collection<BlockPos> solid) {
        Set<BlockPos> all = solid instanceof Set<BlockPos> set ? set : new HashSet<>(solid);
        Set<BlockPos> out = new HashSet<>();
        for (BlockPos p : all) {
            boolean touches = false;
            for (Direction d : Direction.values()) {
                if (all.contains(p.relative(d))) {
                    touches = true;
                    break;
                }
            }
            if (!touches) {
                out.add(p);
            }
        }
        return out;
    }

    /** {@code infos} without the isolated non-air blocks, order kept. */
    static List<StructureBlockInfo> filter(List<StructureBlockInfo> infos) {
        Set<BlockPos> solid = new HashSet<>();
        for (StructureBlockInfo info : infos) {
            if (!info.state().isAir()) {
                solid.add(info.pos());
            }
        }
        Set<BlockPos> drop = isolated(solid);
        if (drop.isEmpty()) {
            return infos;
        }
        List<StructureBlockInfo> out = new ArrayList<>(infos.size() - drop.size());
        for (StructureBlockInfo info : infos) {
            if (info.state().isAir() || !drop.contains(info.pos())) {
                out.add(info);
            }
        }
        return out;
    }

    @Override
    public List<StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos offset, BlockPos pos,
                                                       List<StructureBlockInfo> originalBlockInfos,
                                                       List<StructureBlockInfo> processedBlockInfos,
                                                       StructurePlaceSettings settings) {
        return filter(processedBlockInfos);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return SectStructures.DROP_ISOLATED_BLOCKS.get();
    }
}
