package com.example.myvillage.block;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Actual Minecraft states/shapes with a read-only world fixture, not a second block implementation. */
class ScriptureShelfBlockTest {
    private static final BlockPos ROOT = new BlockPos(7, 71, -9);
    private static ScriptureShelfBlock block;

    @BeforeAll
    @SuppressWarnings("deprecation")
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Real blocks create intrusive holders. Bootstrap (possibly run by another test) has
        // already frozen vanilla registries, so register this test-only fixture during a
        // bounded NeoForge registry-write window and restore the frozen registry immediately.
        MappedRegistry<Block> registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        synchronized (registry) {
            registry.unfreeze();
            try {
                block = Registry.register(registry, ResourceLocation.fromNamespaceAndPath("myvillage_test", "scripture_shelf_block"),
                        new ScriptureShelfBlock(BlockBehaviour.Properties.of().noOcclusion()));
            } finally {
                registry.freeze();
            }
        }
    }

    private static BlockState lower(Direction facing) {
        return block.defaultBlockState().setValue(ScriptureShelfBlock.FACING, facing);
    }

    private static BlockState upper(Direction facing) {
        return lower(facing).setValue(ScriptureShelfBlock.HALF, DoubleBlockHalf.UPPER);
    }

    private static LevelAccessor world(Map<BlockPos, BlockState> blocks) {
        return (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(),
                new Class<?>[]{LevelAccessor.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockState" -> blocks.getOrDefault(args[0], Blocks.AIR.defaultBlockState());
                    case "getFluidState" -> blocks.getOrDefault(args[0], Blocks.AIR.defaultBlockState()).getFluidState();
                    case "getBlockEntity" -> null;
                    case "getHeight" -> 384;
                    case "getMinBuildHeight" -> -64;
                    case "isClientSide" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void legacyDefaultsRemainTheOwnedLowerRootAndUpperHasNoEntity() {
        BlockState legacy = block.defaultBlockState();
        assertEquals(Direction.NORTH, legacy.getValue(ScriptureShelfBlock.FACING));
        assertEquals(DoubleBlockHalf.LOWER, legacy.getValue(ScriptureShelfBlock.HALF));
        assertEquals(ROOT, ScriptureShelfBlock.rootPosition(world(Map.of(ROOT, legacy)), ROOT));
        assertNull(block.newBlockEntity(ROOT.above(), upper(Direction.NORTH)));
    }

    @Test
    void usingEitherHalfResolvesOneRootAndRejectsAnOrphanOrAnotherBlock() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Map<BlockPos, BlockState> cells = new HashMap<>(Map.of(ROOT, lower(facing), ROOT.above(), upper(facing)));
            LevelAccessor level = world(cells);
            assertEquals(ROOT, ScriptureShelfBlock.rootPosition(level, ROOT));
            assertEquals(ROOT, ScriptureShelfBlock.rootPosition(level, ROOT.above()));
            cells.put(ROOT, Blocks.BOOKSHELF.defaultBlockState());
            assertNull(ScriptureShelfBlock.rootPosition(level, ROOT.above()));
            assertNull(ScriptureShelfBlock.rootPosition(level, ROOT));
            cells.remove(ROOT);
            assertNull(ScriptureShelfBlock.rootPosition(level, ROOT.above()));
        }
    }

    @Test
    void legacyRepairIsAdditiveAndNeverOverwritesAnObstructionOrAnotherRoot() {
        BlockState root = lower(Direction.EAST);
        assertEquals(upper(Direction.EAST), ScriptureShelfBlock.upperRepairState(root, Blocks.AIR.defaultBlockState()));
        assertEquals(upper(Direction.EAST), ScriptureShelfBlock.upperRepairState(root, upper(Direction.WEST)));
        for (BlockState obstruction : List.of(Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                Blocks.BOOKSHELF.defaultBlockState(), lower(Direction.NORTH))) {
            assertNull(ScriptureShelfBlock.upperRepairState(root, obstruction));
        }
        assertNull(ScriptureShelfBlock.upperRepairState(upper(Direction.EAST), Blocks.AIR.defaultBlockState()));
        assertEquals(DoubleBlockHalf.LOWER, root.getValue(ScriptureShelfBlock.HALF));
    }

    @Test
    void missingLegacyUpperAndItsObstructionDoNotEraseTheSavedRootOnNeighborUpdate() {
        BlockState root = lower(Direction.NORTH);
        LevelAccessor level = world(Map.of(ROOT, root, ROOT.below(), Blocks.STONE.defaultBlockState()));
        assertTrue(root.canSurvive(level, ROOT));
        assertSame(root, block.updateShape(root, Direction.UP, Blocks.AIR.defaultBlockState(), level, ROOT, ROOT.above()));
        assertSame(root, block.updateShape(root, Direction.UP, Blocks.STONE.defaultBlockState(), level, ROOT, ROOT.above()));
    }

    @Test
    void supportLossAndOrphanUppersDisappearWhileTwoIndependentRootsCannotStack() {
        BlockState root = lower(Direction.SOUTH);
        BlockState top = upper(Direction.SOUTH);
        Map<BlockPos, BlockState> cells = new HashMap<>(Map.of(ROOT, root, ROOT.above(), top,
                ROOT.below(), Blocks.STONE.defaultBlockState()));
        LevelAccessor level = world(cells);
        assertTrue(root.canSurvive(level, ROOT));
        assertTrue(top.canSurvive(level, ROOT.above()));
        assertFalse(lower(Direction.NORTH).canSurvive(level, ROOT.above()));
        assertFalse(upper(Direction.NORTH).canSurvive(level, ROOT.above()));
        cells.remove(ROOT.below());
        assertFalse(root.canSurvive(level, ROOT));
        assertTrue(block.updateShape(root, Direction.DOWN, Blocks.AIR.defaultBlockState(), level, ROOT, ROOT.below()).isAir());
        cells.remove(ROOT);
        assertTrue(block.updateShape(top, Direction.DOWN, Blocks.AIR.defaultBlockState(), level, ROOT.above(), ROOT).isAir());
    }

    @Test
    void splitOutlineAndCollisionCoverTheAcceptedEnvelopeForEveryOrientation() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState root = lower(facing);
            BlockState top = upper(facing);
            AABB bottomBox = root.getShape(EmptyBlockGetter.INSTANCE, ROOT).bounds();
            AABB topBox = top.getShape(EmptyBlockGetter.INSTANCE, ROOT.above()).bounds();
            assertEquals(1, bottomBox.getYsize(), 1e-9);
            assertEquals(0.72, topBox.getYsize(), 1e-9);
            assertEquals(facing.getAxis() == Direction.Axis.Z ? 0.95 : 0.70, bottomBox.getXsize(), 1e-9);
            assertEquals(facing.getAxis() == Direction.Axis.Z ? 0.70 : 0.95, bottomBox.getZsize(), 1e-9);
            assertEquals(bottomBox.minX, topBox.minX, 1e-9);
            assertEquals(bottomBox.maxZ, topBox.maxZ, 1e-9);
            assertEquals(bottomBox, root.getCollisionShape(EmptyBlockGetter.INSTANCE, ROOT, CollisionContext.empty()).bounds());
            assertEquals(topBox, top.getCollisionShape(EmptyBlockGetter.INSTANCE, ROOT.above(), CollisionContext.empty()).bounds());
            assertTrue(root.propagatesSkylightDown(EmptyBlockGetter.INSTANCE, ROOT));
        }
    }

    @Test
    void rotationAndMirrorKeepTheHalfContract() {
        BlockState top = upper(Direction.NORTH);
        assertEquals(upper(Direction.EAST), top.rotate(Rotation.CLOCKWISE_90));
        assertEquals(upper(Direction.SOUTH), top.mirror(Mirror.LEFT_RIGHT));
        assertEquals(top, top.rotate(Rotation.CLOCKWISE_90).rotate(Rotation.COUNTERCLOCKWISE_90));
        LevelAccessor rotatedWorld = world(Map.of(ROOT, lower(Direction.EAST), ROOT.above(), top));
        assertEquals(upper(Direction.EAST), block.updateShape(top, Direction.DOWN, lower(Direction.EAST),
                rotatedWorld, ROOT.above(), ROOT));
    }

    @Test
    void onlyTheRootLootPoolCanProduceTheSingleShelfItem() throws Exception {
        JsonObject loot = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/myvillage/loot_table/blocks/scripture_shelf.json"))).getAsJsonObject();
        assertEquals(1, loot.getAsJsonArray("pools").size());
        JsonObject pool = loot.getAsJsonArray("pools").get(0).getAsJsonObject();
        assertEquals(1, pool.get("rolls").getAsInt());
        assertEquals(1, pool.getAsJsonArray("entries").size());
        assertEquals("myvillage:scripture_shelf", pool.getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        boolean rootOnly = false;
        for (var value : pool.getAsJsonArray("conditions")) {
            JsonObject condition = value.getAsJsonObject();
            if (condition.get("condition").getAsString().equals("minecraft:block_state_property")) {
                assertEquals("myvillage:scripture_shelf", condition.get("block").getAsString());
                assertEquals("lower", condition.getAsJsonObject("properties").get("half").getAsString());
                rootOnly = true;
            }
        }
        assertTrue(rootOnly, "upper loot must not duplicate the item dropped by root removal");
    }
}
