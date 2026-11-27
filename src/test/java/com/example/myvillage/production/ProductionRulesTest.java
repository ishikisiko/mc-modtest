package com.example.myvillage.production;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.gathering.GatheringConstruction;
import com.example.myvillage.gathering.GatheringPattern;
import com.example.myvillage.gathering.GatheringRules;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class ProductionRulesTest {
    @Test void rotatingEveryLayoutLeavesTheOutletAndOriginalArrayClear() {
        for (var kind : ProductionKind.values()) {
            var occupied = new HashSet<BlockPos>();
            if (!kind.mining()) {
                GatheringConstruction.pieces(kind.gatheringTier).forEach(piece -> occupied.add(piece.offset()));
                occupied.addAll(GatheringPattern.nodes(kind.gatheringTier));
            }
            for (var facing : Direction.Plane.HORIZONTAL) {
                var layout = ProductionPattern.layout(kind, facing);
                var positions = new HashSet<BlockPos>();
                layout.cells().forEach(cell -> assertTrue(positions.add(cell.offset())));
                assertFalse(positions.contains(layout.outlet().offset().below()));
                assertFalse(occupied.contains(layout.outlet().offset().below()));
                assertTrue(positions.stream().noneMatch(occupied::contains));
                assertEquals(facing, layout.controller().facing());
                assertEquals(kind == ProductionKind.HIGH ? facing.getClockWise() : facing, layout.outlet().facing());
                if (!kind.mining()) {
                    int inner = GatheringRules.get().tier(kind.gatheringTier).innerRadius();
                    assertTrue(positions.stream().allMatch(pos -> Math.max(Math.abs(pos.getX()), Math.abs(pos.getZ())) > inner));
                }
            }
        }
    }

    @Test void condensationRatesUseTheExactDenominationCosts() {
        var rules = ProductionRules.get();
        assertEquals(1_200, ProductionRules.cost(0) / rules.rate(ProductionKind.LOW));
        assertEquals(30_000, ProductionRules.cost(1) / rules.rate(ProductionKind.MEDIUM));
        assertEquals(720_000, ProductionRules.cost(2) / rules.rate(ProductionKind.HIGH));
        assertEquals(100, ProductionRules.cost(1) / ProductionRules.cost(0));
        assertEquals(10_000, ProductionRules.cost(2) / ProductionRules.cost(0));
    }

    @Test void highGradeSupplyReservesLowerGradesBeforeMakingAHighStone() {
        assertEquals(0, ProductionBlockEntity.outputGrade(3, 31, 0, 32, 4));
        assertEquals(1, ProductionBlockEntity.outputGrade(3, 32, 3, 32, 4));
        assertEquals(2, ProductionBlockEntity.outputGrade(3, 32, 4, 32, 4));
        assertEquals(2, ProductionBlockEntity.outputGrade(2, 0, 0, 32, 4));
        assertEquals(1, ProductionBlockEntity.outputGrade(1, 0, 0, 32, 4));
    }

    @Test void mineRecoveryKeepsFractionalTimeAndStopsAtCapacity() {
        var rules = ProductionRules.get().mining();
        var stock = new MineReserve.Stock(rules.capacity() - 3, 10);
        assertSame(stock, stock.at(9, rules));
        assertEquals(stock, stock.at(10 + rules.recovery() - 1, rules));
        assertEquals(new MineReserve.Stock(rules.capacity() - 2, 10L + rules.recovery()), stock.at(11L + rules.recovery(), rules));
        long later = 11L + rules.recovery() * 4L;
        assertEquals(new MineReserve.Stock(rules.capacity(), later), stock.at(later, rules));
    }
}
