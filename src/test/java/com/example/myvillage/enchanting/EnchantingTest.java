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
        assertEquals(131, EnchantingStructure.SEAL.size());
        for (Direction front : Direction.Plane.HORIZONTAL) {
            var control = new BlockPos(23, 74, -17);
            var center = EnchantingStructure.libraryCenter(control, front);
            assertEquals(control, EnchantingStructure.offset(center, front, 0, 1, 5));
            assertEquals(center.relative(front.getCounterClockWise(), 2),
                    EnchantingStructure.offset(center, front, 2, 0, 0));
        }
    }
    @Test void casketBlueprintMatchesTheThreeConstructionLayers() {
        var cells = EnchantingStructure.SEAL;
        assertEquals(91, cells.stream().filter(cell -> cell.part().equals("seal_tile")).count());
        assertEquals(11, cells.stream().filter(cell -> cell.part().equals("seal_pivot")).count());
        assertEquals(29, cells.stream().filter(cell -> cell.part().equals("minecraft:deepslate_brick_slab")).count());
        assertTrue(cells.stream().allMatch(cell -> Math.abs(cell.x()) <= 6 && Math.abs(cell.z()) <= 6
                && cell.y() >= -1 && cell.y() <= 1));
        assertEquals(cells.size(), cells.stream().map(cell -> new BlockPos(cell.x(), cell.y(), cell.z())).distinct().count());
        for (var front : Direction.Plane.HORIZONTAL) for (var cell : cells) {
            var center = new BlockPos(17, 74, 17);
            var own = EnchantingStructure.offset(center, front, cell.x(), cell.y(), cell.z());
            assertEquals(center, EnchantingStructure.offset(own, front, -cell.x(), -cell.y(), -cell.z()));
        }
    }

    @Test void sealPriceUsesTheInclusiveBoundariesAndCopperCapsAtFifty() {
        var rules = SealRules.RULES;
        var low = new SealRules.Price(15, 3, SealRules.Grade.LOW);
        var middle = new SealRules.Price(30, 30, SealRules.Grade.LOW);
        var high = new SealRules.Price(50, 1, SealRules.Grade.MEDIUM);
        assertEquals(low, rules.price(0));
        assertEquals(low, rules.price(15));
        assertEquals(middle, rules.price(16));
        assertEquals(middle, rules.price(30));
        assertEquals(high, rules.price(31));
        assertEquals(high, rules.price(50));
        assertEquals(50, rules.strength(15, java.util.Collections.nCopies(12, "copper")));
        assertEquals(14, rules.strength(4, java.util.List.of("copper", "crystal", "copper")));
    }

    @Test void frontAndRearDiscsControlTheirOwnDrawsAndTreasureRaisesRisk() {
        var rules = SealRules.RULES;
        var discs = new java.util.ArrayList<>(java.util.Collections.nCopies(16, "empty"));
        discs.set(0, "crystal");
        discs.set(1, "crystal");
        discs.set(2, "crystal");
        discs.set(3, "crystal");
        discs.set(4, "pairing");
        discs.set(6, "mineral");
        discs.set(7, "treasure");
        discs.set(8, "repair");
        discs.set(9, "risky");
        var first = rules.selection(discs, true, true);
        var next = rules.selection(discs, false, true);
        assertEquals(6, first.slots());
        assertEquals(1, first.pairing());
        assertEquals("mineral", first.direction());
        assertEquals(3, next.slots());
        assertEquals(0, next.pairing());
        assertEquals("repair", next.direction());
        assertEquals(5, rules.risk(rules.selection(discs, true, false)));
        assertEquals(20, rules.risk(first));
        assertEquals(35, rules.risk(next));
        discs.set(9, "plain");
        assertFalse(rules.selection(discs, false, true).treasure());
        assertNotEquals(SealRules.signature(50, true, discs), SealRules.signature(50, false, discs));
    }
}
