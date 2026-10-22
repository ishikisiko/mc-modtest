package com.example.myvillage.forging.array;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ForgingArrayTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    static final class Grid implements ForgingArrayPattern.View {
        final Map<BlockPos, ForgingArrayPattern.Part> parts = new HashMap<>();
        final Set<Long> unloadedChunks = new HashSet<>();
        final Set<BlockPos> reads = new HashSet<>();
        static long chunk(BlockPos p) { return ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4); }
        public boolean loaded(BlockPos p) { return !unloadedChunks.contains(chunk(p)); }
        public ForgingArrayPattern.Part part(BlockPos p) {
            assertTrue(loaded(p), "Must not read an unloaded chunk at " + p);
            reads.add(p.immutable());
            return parts.getOrDefault(p, new ForgingArrayPattern.Part(ForgingArrayRules.Role.AIR, Direction.NORTH));
        }
        void add(BlockPos root, Direction facing) {
            for (var c : ForgingArrayRules.get().cells())
                parts.put(ForgingArrayPattern.worldPos(root, facing, c), new ForgingArrayPattern.Part(c.role(), facing));
        }
    }

    @Test void fourFacingsMapEveryBlueprintCoordinateToTheExpectedWorldPosition() {
        BlockPos root = new BlockPos(-16, 80, 15);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Grid grid = new Grid(); grid.add(root, facing);
            assertEquals(125, grid.parts.size());
            assertEquals(new ForgingArrayPattern.Check(true, -1, 0), ForgingArrayPattern.check(grid, root, facing));
            for (var cell : ForgingArrayRules.get().cells()) {
                int x = cell.x(), z = cell.z();
                BlockPos expected = switch (facing) {
                    case NORTH -> root.offset(x, cell.y(), z);
                    case EAST -> root.offset(-z, cell.y(), x);
                    case SOUTH -> root.offset(-x, cell.y(), -z);
                    case WEST -> root.offset(z, cell.y(), -x);
                    default -> throw new AssertionError(facing);
                };
                assertEquals(expected, ForgingArrayPattern.worldPos(root, facing, cell), cell.toString());
                assertEquals(cell.role(), grid.part(expected).role());
            }
        }
    }

    @Test void everyMissingRequiredCellAndEveryBlockedAirCellReportsItsExactCoordinate() {
        BlockPos root = new BlockPos(15, 70, -16);
        var cells = ForgingArrayRules.get().cells();
        for (Direction facing : Direction.Plane.HORIZONTAL) for (int i = 0; i < cells.size(); i++) {
            var cell = cells.get(i); Grid grid = new Grid(); grid.add(root, facing);
            BlockPos broken = ForgingArrayPattern.worldPos(root, facing, cell);
            if (cell.role() == ForgingArrayRules.Role.AIR)
                grid.parts.put(broken, new ForgingArrayPattern.Part(ForgingArrayRules.Role.CASING, facing));
            else grid.parts.remove(broken);
            var check = ForgingArrayPattern.check(grid, root, facing);
            assertFalse(check.formed(), facing + " " + cell);
            assertEquals(1, check.mismatchCount(), cell.toString());
            assertEquals(i, check.mismatchIndex(), cell.toString());
            assertEquals(broken, ForgingArrayPattern.worldPos(root, facing, cells.get(check.mismatchIndex())));
        }
    }

    @Test void multipleBrokenCellsCountAllMismatchesAndSelectTheFirstBlueprintCell() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Grid grid = new Grid(); BlockPos root = new BlockPos(-35, 71, 17); grid.add(root, facing);
            var cells = ForgingArrayRules.get().cells();
            List<Integer> broken = List.of(2, 28, 96, 124);
            for (int i : broken) {
                var cell = cells.get(i);
                grid.parts.put(ForgingArrayPattern.worldPos(root, facing, cell),
                        new ForgingArrayPattern.Part(cell.role() == ForgingArrayRules.Role.AIR
                                ? ForgingArrayRules.Role.HEARTH : ForgingArrayRules.Role.AIR, facing));
            }
            assertEquals(new ForgingArrayPattern.Check(false, broken.get(0), broken.size()),
                    ForgingArrayPattern.check(grid, root, facing));
        }
    }

    @Test void onlyTheControllerRequiresTheAssemblyFacing() {
        BlockPos root = new BlockPos(0, 64, 0);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Grid grid = new Grid(); grid.add(root, facing);
            for (var cell : ForgingArrayRules.get().cells()) if (cell.role() != ForgingArrayRules.Role.CONTROLLER)
                grid.parts.put(ForgingArrayPattern.worldPos(root, facing, cell),
                        new ForgingArrayPattern.Part(cell.role(), facing.getOpposite()));
            assertTrue(ForgingArrayPattern.check(grid, root, facing).formed());
            grid.parts.put(root, new ForgingArrayPattern.Part(ForgingArrayRules.Role.CONTROLLER, facing.getOpposite()));
            var broken = ForgingArrayPattern.check(grid, root, facing);
            assertEquals(1, broken.mismatchCount());
            assertEquals(ForgingArrayRules.Role.CONTROLLER, ForgingArrayRules.get().cells().get(broken.mismatchIndex()).role());
        }
    }

    @Test void unloadedChunksRejectWithoutAnyBlockReadAndRecoverWhenLoaded() {
        BlockPos root = new BlockPos(15, 70, 15);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Grid complete = new Grid(); complete.add(root, facing);
            Set<Long> chunks = new HashSet<>(); complete.parts.keySet().forEach(p -> chunks.add(Grid.chunk(p)));
            assertTrue(chunks.size() > 1, "Fixture must cross a real chunk boundary");
            for (long chunk : chunks) {
                Grid grid = new Grid(); grid.add(root, facing); grid.unloadedChunks.add(chunk);
                var cells = ForgingArrayRules.get().cells(); int first = -1, count = 0;
                for (int i = 0; i < cells.size(); i++) if (Grid.chunk(ForgingArrayPattern.worldPos(root, facing, cells.get(i))) == chunk) {
                    if (first < 0) first = i; count++;
                }
                assertEquals(new ForgingArrayPattern.Check(false, first, count), ForgingArrayPattern.check(grid, root, facing));
                assertTrue(grid.reads.stream().noneMatch(p -> Grid.chunk(p) == chunk));
                grid.unloadedChunks.clear();
                assertTrue(ForgingArrayPattern.check(grid, root, facing).formed());
            }
        }
    }

    @Test void ownershipScanDoesNotReadUnloadedChunksOutsideAnIntactAssembly() {
        BlockPos root = new BlockPos(8, 70, 8);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Grid grid = new Grid(); grid.add(root, facing);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                if (x != 0 || z != 0) grid.unloadedChunks.add(ChunkPos.asLong(x, z));
            assertTrue(ForgingArrayPattern.check(grid, root, facing).formed());
        }
    }

    @Test void touchingIndependentArraysRemainValidForAllSixteenFacingPairs() {
        BlockPos root = new BlockPos(0, 64, 0);
        for (Direction facing : Direction.Plane.HORIZONTAL) for (Direction other : Direction.Plane.HORIZONTAL) {
            Grid first = new Grid(); first.add(root, facing);
            Grid second = new Grid(); second.add(BlockPos.ZERO, other);
            int right = first.parts.keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow();
            int front = first.parts.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow();
            int left = second.parts.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow();
            int otherFront = second.parts.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow();
            BlockPos neighbor = new BlockPos(right - left + 1, root.getY(), front - otherFront);
            Grid grid = new Grid(); grid.add(root, facing);
            Set<BlockPos> firstFootprint = Set.copyOf(grid.parts.keySet()); grid.add(neighbor, other);
            assertEquals(250, grid.parts.size(), "Independent full footprints must not overlap");
            assertTrue(firstFootprint.stream().anyMatch(p -> grid.parts.containsKey(p.east()) && !firstFootprint.contains(p.east())),
                    "Fixture must place the two footprints directly beside each other");
            assertTrue(ForgingArrayPattern.check(grid, root, facing).formed(), facing + " / " + other);
            assertTrue(ForgingArrayPattern.check(grid, neighbor, other).formed(), facing + " / " + other);
        }
    }

    @Test void collisionSlicesAreLocalAndRotateWithEachStructuralCell() {
        BlockPos root = new BlockPos(31, 67, -17);
        for (var cell : ForgingArrayRules.get().cells()) {
            if (cell.role() == ForgingArrayRules.Role.AIR) continue;
            var north = ForgingArrayCollision.shape(root, Direction.NORTH, ForgingArrayPattern.worldPos(root, Direction.NORTH, cell));
            assertFalse(north.isEmpty(), cell.toString());
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var shape = ForgingArrayCollision.shape(root, facing, ForgingArrayPattern.worldPos(root, facing, cell));
                for (var box : shape.toAabbs()) {
                    assertTrue(box.minX >= -1e-9 && box.minY >= -1e-9 && box.minZ >= -1e-9, cell.toString());
                    assertTrue(box.maxX <= 1 + 1e-9 && box.maxY <= 1 + 1e-9 && box.maxZ <= 1 + 1e-9, cell.toString());
                }
                for (var box : north.toAabbs()) {
                    double x = (box.minX + box.maxX) / 2 - .5, z = (box.minZ + box.maxZ) / 2 - .5;
                    double xx = .5 + facing.getClockWise().getStepX() * x + facing.getOpposite().getStepX() * z;
                    double zz = .5 + facing.getClockWise().getStepZ() * x + facing.getOpposite().getStepZ() * z;
                    double yy = (box.minY + box.maxY) / 2;
                    assertTrue(shape.toAabbs().stream().anyMatch(b -> b.contains(xx, yy, zz)), facing + " " + cell);
                }
            }
        }
    }

    private static String rulesText() throws IOException {
        try (var stream = ForgingArrayTest.class.getResourceAsStream("/data/myvillage/forging/array.json")) {
            return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private static void rejects(JsonObject bad) {
        assertThrows(RuntimeException.class, () -> ForgingArrayRules.parse(new StringReader(bad.toString())));
    }

    @Test void rulesRejectBadDurationsFractionalIntegersAndOutOfRangeAnimation() throws IOException {
        JsonObject original = JsonParser.parseString(rulesText()).getAsJsonObject();
        assertEquals(125, ForgingArrayRules.parse(new StringReader(original.toString())).cells().size());
        for (int duration : List.of(-1, 0, 30001)) {
            var bad = original.deepCopy(); bad.addProperty("duration_ticks", duration); rejects(bad);
        }
        for (String field : List.of("version", "duration_ticks", "light_level", "particle_interval_ticks")) {
            var bad = original.deepCopy(); bad.addProperty(field, 1.5); rejects(bad);
        }
        for (String field : List.of("x", "y", "z")) {
            var bad = original.deepCopy(); bad.getAsJsonArray("cells").get(0).getAsJsonObject().addProperty(field, .5); rejects(bad);
        }
        for (String field : List.of("ring_degrees_per_tick", "item_bob")) {
            var bad = original.deepCopy(); bad.addProperty(field, -.01); rejects(bad);
            var huge = original.deepCopy(); huge.addProperty(field, 10.01); rejects(huge);
            var wrongType = original.deepCopy(); wrongType.addProperty(field, "fast"); rejects(wrongType);
        }
        var highBob = original.deepCopy(); highBob.addProperty("item_bob", .201); rejects(highBob);
    }

    @Test void rulesRejectDuplicateCoordinatesUnknownFieldsAndUnknownRoles() throws IOException {
        JsonObject original = JsonParser.parseString(rulesText()).getAsJsonObject();
        var duplicate = original.deepCopy(); duplicate.getAsJsonArray("cells").set(1, duplicate.getAsJsonArray("cells").get(0).deepCopy()); rejects(duplicate);
        var unknown = original.deepCopy(); unknown.addProperty("extra", 1); rejects(unknown);
        var unknownCell = original.deepCopy(); unknownCell.getAsJsonArray("cells").get(0).getAsJsonObject().addProperty("extra", 1); rejects(unknownCell);
        var role = original.deepCopy(); role.getAsJsonArray("cells").get(0).getAsJsonObject().addProperty("role", "input"); rejects(role);
        var shortLayout = original.deepCopy(); shortLayout.getAsJsonArray("cells").remove(0); rejects(shortLayout);
    }

    @Test void duplicateJsonFieldNamesRejectRatherThanSilentlyReplacingTheDefinition() throws IOException {
        String text = rulesText();
        assertThrows(RuntimeException.class, () -> ForgingArrayRules.parse(new StringReader(
                text.replaceFirst("\"duration_ticks\"\\s*:", "\"duration_ticks\": 1, \"duration_ticks\":"))));
    }
}
