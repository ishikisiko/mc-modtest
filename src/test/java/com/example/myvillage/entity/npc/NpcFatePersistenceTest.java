package com.example.myvillage.entity.npc;

import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.fate.NpcFatePool;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcFatePersistenceTest {
    @Test void entityNbtPreservesTheEntireRollForBothIdentities() {
        for(boolean prodigy:new boolean[]{false,true}) {
            var saved=NpcFatePool.bundled().roll(new int[5],prodigy,SimRng.at(9,0,4,114));
            assertEquals(saved,NpcFateRuntime.load(NpcFateRuntime.save(saved),prodigy));
            assertThrows(IllegalArgumentException.class,()->NpcFateRuntime.load(NpcFateRuntime.save(saved),!prodigy));
        }
    }
    @Test void corruptOrIncompleteEntityNbtCannotBecomeANewRoll() {
        var saved=NpcFatePool.bundled().roll(new int[5],false,SimRng.at(9,0,4,114));
        for(String key:new String[]{"selected","budget","spent"}) {
            var tag=NpcFateRuntime.save(saved);tag.remove(key);
            assertThrows(IllegalArgumentException.class,()->NpcFateRuntime.load(tag,false));
        }
        var tag=NpcFateRuntime.save(saved);
        tag.getList("selected",8).set(1,StringTag.valueOf(saved.selected().getFirst()));
        assertThrows(IllegalArgumentException.class,()->NpcFateRuntime.load(tag,false));
    }
}
