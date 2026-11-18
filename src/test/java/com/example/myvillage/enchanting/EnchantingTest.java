package com.example.myvillage.enchanting;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class EnchantingTest {
    @Test void collectionRemembersTheHighestLevelAfterPrintingLastCopy() {
        var ledger = new CompoundTag();
        String rune = "minecraft:efficiency";
        assertTrue(EnchantingDevice.adjust(ledger, rune, RuneStorage.points(2) * 2, 2));
        assertEquals(4, EnchantingDevice.points(ledger, rune));
        assertEquals(2, EnchantingDevice.recorded(ledger, rune));
        assertTrue(EnchantingDevice.adjust(ledger, rune, RuneStorage.points(3), 3));
        assertTrue(EnchantingDevice.adjust(ledger, rune, -8, 0));
        assertEquals(0, EnchantingDevice.points(ledger, rune));
        assertEquals(3, EnchantingDevice.recorded(ledger, rune));
        assertFalse(EnchantingDevice.adjust(ledger, rune, -1, 0));
        assertFalse(EnchantingDevice.adjust(ledger, rune, EnchantingRules.RULES.point_capacity() + 1, 3));
    }

    @Test void placingTheLibraryFromAnyFrontUsesTheSameRelativeBlueprint() {
        assertEquals(345, EnchantingStructure.LIBRARY.size());
        assertEquals(107, EnchantingStructure.SEAL.size());
        for (Direction front : Direction.Plane.HORIZONTAL) {
            var control = new BlockPos(23, 74, -17);
            var center = EnchantingStructure.libraryCenter(control, front);
            assertEquals(control, EnchantingStructure.offset(center, front, 0, 1, 5));
            assertEquals(center.relative(front.getCounterClockWise(), 2),
                    EnchantingStructure.offset(center, front, 2, 0, 0));
        }
    }
}
