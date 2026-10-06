package com.example.myvillage.sim.runtime.avatar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** The gate realization record survives a save. */
class GateRealizationsTest {
    @Test
    void recordsRoundTripInSectOrder() {
        GateRealizations gates = new GateRealizations();
        assertFalse(gates.isDirty());
        GateRealizations.Gate b = new GateRealizations.Gate(7, new BlockPos(160, -60, -96), -5L, "none", 612L);
        GateRealizations.Gate a = new GateRealizations.Gate(2, new BlockPos(-3000, 71, 1200), 99L,
                "pagoda_long_arched_west", 600L);
        gates.put(b);
        gates.put(a);
        assertTrue(gates.isDirty());
        CompoundTag tag = gates.save(new CompoundTag(), null);
        assertEquals(GateRealizations.FORMAT, tag.getInt(GateRealizations.FORMAT_TAG));
        GateRealizations loaded = GateRealizations.load(tag, null);
        assertEquals(List.of(a, b), List.copyOf(loaded.all()));
        assertEquals(b, loaded.gate(7).orElseThrow());
        assertTrue(loaded.gate(3).isEmpty());
    }

    @Test
    void aRebuildReplacesTheRecord() {
        GateRealizations gates = new GateRealizations();
        gates.put(new GateRealizations.Gate(1, new BlockPos(0, 64, 0), 1L, "none", 1L));
        GateRealizations.Gate moved = new GateRealizations.Gate(1, new BlockPos(500, -60, 20), 1L, "none", 9L);
        gates.put(moved);
        assertEquals(List.of(moved), List.copyOf(gates.all()));
        gates.remove(1);
        assertTrue(gates.all().isEmpty());
    }
}
