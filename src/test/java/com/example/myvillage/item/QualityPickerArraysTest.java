package com.example.myvillage.item;

import com.example.myvillage.alchemy.array.AlchemyArrayPattern;
import com.example.myvillage.alchemy.array.AlchemyArrayRules;
import com.example.myvillage.enchanting.EnchantingStructure;
import com.example.myvillage.network.QualityPickerArrayPayload;
import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static com.example.myvillage.item.QualityPickerArrays.Layout.*;
import static org.junit.jupiter.api.Assertions.*;

final class QualityPickerArraysTest {
    @Test void everyRotationKeepsTheFloorAndUsesOneRectangularClearance() {
        var floor = new BlockPos(14, 72, -15);
        var sizes = Map.of(ALCHEMY, new int[]{3, 3, 3, 27}, FORGING, new int[]{5, 5, 5, 125},
                SEAL, new int[]{13, 7, 13, 133}, LIBRARY, new int[]{11, 13, 11, 345});

        for (Direction front : Direction.Plane.HORIZONTAL) for (var specimen : sizes.entrySet()) {
            var plan = QualityPickerArrays.blueprint(specimen.getKey(), floor, front);
            var box = plan.bounds();
            var metrics = specimen.getValue();
            assertArrayEquals(metrics, new int[]{box.getXSpan(), box.getYSpan(), box.getZSpan(), plan.cells().size()});
            assertEquals(floor.getY() + 1, box.minY());
            assertTrue(box.isInside(floor.above()));
            assertFalse(box.isInside(floor));
            assertEquals(plan.cells().size(), plan.cells().stream().map(QualityPickerArrays.Cell::pos).distinct().count());
            assertTrue(plan.cells().stream().allMatch(cell -> box.isInside(cell.pos())));
            assertEquals(front, plan.front());
        }
    }

    @Test void alchemyPortsFaceOutwardAndTheLibraryUsesItsRealOperationPort() {
        var floor = new BlockPos(15, 65, 15);
        for (Direction front : Direction.Plane.HORIZONTAL) {
            var alchemy = QualityPickerArrays.blueprint(ALCHEMY, floor, front);
            assertEquals(floor.above(2), alchemy.controller());
            var facing = alchemy.cells().stream().collect(Collectors.toMap(QualityPickerArrays.Cell::pos,
                    QualityPickerArrays.Cell::direction));
            for (var cell : AlchemyArrayRules.get().cells()) {
                var pos = AlchemyArrayPattern.worldPos(alchemy.controller(), front, cell);
                switch (cell.role()) {
                    case INPUT -> assertEquals(front.getCounterClockWise(), facing.get(pos));
                    case OUTPUT -> assertEquals(front.getClockWise(), facing.get(pos));
                    case FUEL -> assertEquals(front.getOpposite(), facing.get(pos));
                    default -> assertEquals(front, facing.get(pos));
                }
            }
            var library = QualityPickerArrays.blueprint(LIBRARY, floor, front);
            assertEquals(floor.above(2), library.controller());
            var center = EnchantingStructure.libraryCenter(library.controller(), front);
            assertEquals(library.controller(), EnchantingStructure.offset(center, front, 0, 1, 5));
            assertTrue(library.cells().stream().anyMatch(cell -> cell.pos().equals(library.controller())));
        }
    }

    @Test void theConfirmationCarriesTheIssuedTicketAndDoesNotResolveAsABlueprint() {
        var request = new QualityPickerArrayPayload(QualityPickerArrays.CONFIRM, 381450644217L);
        var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            QualityPickerArrayPayload.STREAM_CODEC.encode(bytes, request);
            assertEquals(request, QualityPickerArrayPayload.STREAM_CODEC.decode(bytes));
            assertEquals(0, bytes.readableBytes());
        } finally {
            bytes.release();
        }
        assertTrue(QualityPickerArrays.Layout.fromId(QualityPickerArrays.CONFIRM).isEmpty());
        assertTrue(QualityPickerArrays.Layout.fromId(QualityPickerArrays.CANCEL).isEmpty());
        assertTrue(QualityPickerArrays.Layout.fromId(-1).isEmpty());
    }
}
