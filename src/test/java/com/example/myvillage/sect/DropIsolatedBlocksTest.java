package com.example.myvillage.sect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class DropIsolatedBlocksTest {
    @Test
    void aBlockTouchingNoOtherOnAnyFaceIsIsolated() {
        List<BlockPos> blocks = List.of(
                new BlockPos(0, 0, 0), new BlockPos(1, 0, 0),          // a pair: each touches the other
                new BlockPos(5, 5, 5),                                 // alone
                new BlockPos(7, 0, 0), new BlockPos(8, 1, 0),          // a diagonal string: edges only
                new BlockPos(0, 3, 0), new BlockPos(0, 4, 0));         // stacked
        assertEquals(Set.of(new BlockPos(5, 5, 5), new BlockPos(7, 0, 0), new BlockPos(8, 1, 0)),
                DropIsolatedBlocks.isolated(blocks));
    }

    @Test
    void nothingIsIsolatedInASolidVolumeOrAnEmptyTemplate() {
        assertEquals(Set.of(), DropIsolatedBlocks.isolated(List.of()));
        assertEquals(Set.of(), DropIsolatedBlocks.isolated(List.of(
                new BlockPos(0, 0, 0), new BlockPos(0, 0, 1), new BlockPos(0, 1, 1), new BlockPos(1, 1, 1))));
    }
}
