package com.example.myvillage.sect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.town.ModBlockFallback;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Builds whole compounds into an in-memory world with the real templates (read from
 * {@code src/main/resources/data/myvillage/structure}) and checks what a player would see: the axis
 * is walkable from the forecourt to the principal hall, nothing floats, the generator writes no wall
 * blocks, no building stands on the axis, every grand stair climbs one terrace in single steps, and
 * the courtyard cells are open ground. Both build paths run: the on-the-spot command (live surface)
 * and the worldgen style (derived mountain, then the compound), the latter also chunk by chunk.
 */
class SectCompoundRealizationTest {
    private static final Path STRUCTURES = Path.of("src/main/resources/data/myvillage/structure");
    private static final Path FALLBACKS = Path.of("src/main/resources/data/myvillage/mod_block_fallbacks.json");
    private static final BlockPos ANCHOR = new BlockPos(100, 70, -40);
    private static final BlockPos BASE = SectCourtyard.base(ANCHOR);
    private static final int E = ANCHOR.getY();
    private static final long[] SEEDS = {7L, -123456789L, 20260618L};
    private static final Map<String, Optional<StructureTemplate>> TEMPLATES = new HashMap<>();
    private static final Set<String> RAW_STONE_BRICK_WALL = new HashSet<>();
    /** Template-relative positions of {@code myvillage:hanging_plaque} blocks, per template. */
    private static final Map<String, List<BlockPos>> PLAQUES = new HashMap<>();
    private static Map<String, String> fallbacks;

    @BeforeAll
    static void bootstrap() throws IOException {
        // Blocks need FeatureFlags, whose NeoForge loader reads the (here absent) mod list.
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        JsonObject json = JsonParser.parseString(Files.readString(FALLBACKS)).getAsJsonObject();
        fallbacks = new HashMap<>();
        json.entrySet().forEach(e -> fallbacks.put(e.getKey(), e.getValue().getAsString()));
    }

    // --- the in-memory world ------------------------------------------------

    /** Natural ground: the top solid block's y at a local cell. */
    interface Ground {
        int top(int localX, int localZ);
    }

    static final class MemorySink implements SectSink {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final Set<BlockPos> fromTemplate = new HashSet<>();
        final Ground ground;
        final SectMountain mountain;
        SectGenerator.Clip clip = SectGenerator.Clip.UNBOUNDED;

        MemorySink(Ground ground, SectMountain mountain) {
            this.ground = ground;
            this.mountain = mountain;
        }

        BlockState get(BlockPos p) {
            BlockState s = blocks.get(p);
            if (s != null) return s;
            return p.getY() <= ground.top(p.getX() - BASE.getX(), p.getZ() - BASE.getZ())
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }

        boolean inClip(BlockPos p) {
            return p.getX() >= clip.x0() && p.getX() <= clip.x1() && p.getZ() >= clip.z0() && p.getZ() <= clip.z1();
        }

        @Override
        public void set(BlockPos pos, BlockState state) {
            if (!inClip(pos)) return;
            blocks.put(pos.immutable(), state);
            fromTemplate.remove(pos);
        }

        @Override
        public SectGenerator.Clip clip() {
            return clip;
        }

        @Override
        public int surfaceY(int worldX, int worldZ) {
            if (mountain != null) {
                return mountain.height(worldX - BASE.getX(), worldZ - BASE.getZ());
            }
            // like the live MOTION_BLOCKING heightmap: the first free y above the column
            for (int y = E + 120; y > E - 80; y--) {
                if (!get(new BlockPos(worldX, y, worldZ)).isAir()) return y + 1;
            }
            return E - 80;
        }

        @Override
        public Optional<ModBlockFallback.LoadedTemplate> loadTemplate(ResourceLocation id) {
            return template(id.getPath()).map(t -> new ModBlockFallback.LoadedTemplate(t, 0));
        }

        @Override
        public boolean placeTemplate(StructureTemplate template, BlockPos origin, RandomSource random) {
            // as placeInWorld with default settings plus DropIsolatedBlocks: no rotation, air placed
            List<StructureBlockInfo> infos = new ArrayList<>();
            for (StructureBlockInfo info : blocksOf(template)) {
                infos.add(new StructureBlockInfo(info.pos().offset(origin), info.state(), info.nbt()));
            }
            for (StructureBlockInfo info : DropIsolatedBlocks.filter(infos)) {
                if (!inClip(info.pos())) continue;
                blocks.put(info.pos(), info.state());
                fromTemplate.add(info.pos());
            }
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<StructureBlockInfo> blocksOf(StructureTemplate template) {
        try {
            Field f = StructureTemplate.class.getDeclaredField("palettes");
            f.setAccessible(true);
            return ((List<StructureTemplate.Palette>) f.get(template)).get(0).blocks();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A template read from the resources, with absent mod blocks patched like the runtime does. */
    private static synchronized Optional<StructureTemplate> template(String name) {
        return TEMPLATES.computeIfAbsent(name, n -> {
            Path file = STRUCTURES.resolve(n + ".nbt");
            if (!Files.isRegularFile(file)) return Optional.empty();
            try {
                CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
                ListTag palette = tag.contains("palette", Tag.TAG_LIST)
                        ? tag.getList("palette", Tag.TAG_COMPOUND)
                        : tag.getList("palettes", Tag.TAG_LIST).getList(0);
                Set<Integer> plaqueStates = new HashSet<>();
                for (int i = 0; i < palette.size(); i++) {
                    CompoundTag entry = palette.getCompound(i);
                    String id = entry.getString("Name");
                    if (id.equals("minecraft:stone_brick_wall")) RAW_STONE_BRICK_WALL.add(n);
                    if (id.equals("myvillage:hanging_plaque")) plaqueStates.add(i);
                    if (!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(id))) {
                        String fb = fallbacks.getOrDefault(id, "minecraft:cobblestone");
                        entry.putString("Name", fb.contains("[") ? fb.substring(0, fb.indexOf('[')) : fb);
                        entry.remove("Properties");
                    }
                }
                List<BlockPos> plaques = new ArrayList<>();
                ListTag blockList = tag.getList("blocks", Tag.TAG_COMPOUND);
                for (int i = 0; i < blockList.size(); i++) {
                    CompoundTag b = blockList.getCompound(i);
                    if (plaqueStates.contains(b.getInt("state"))) {
                        ListTag pos = b.getList("pos", Tag.TAG_INT);
                        plaques.add(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)));
                    }
                }
                PLAQUES.put(n, List.copyOf(plaques));
                StructureTemplate t = new StructureTemplate();
                t.load(BuiltInRegistries.BLOCK.asLookup(), tag);
                return Optional.of(t);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    // --- builds -------------------------------------------------------------

    private static final Ground FLAT = (x, z) -> E - 1;
    /** Rolling natural ground for the worldgen path: below the gate in front, rising behind. */
    private static final Ground ROLLING = (x, z) -> E - 6 + Math.floorDiv(z, 9) + (int) Math.round(3 * Math.sin(x / 7.0));
    /** Flat natural ground far below the compound, so the skirt has to fall the whole way. */
    private static final Ground LOW_FLAT = (x, z) -> E - 14;

    private static MemorySink commandBuild(long seed, String variant) {
        SectGenerator.SectPlan plan = SectGenerator.plan(seed, BASE, variant);
        assertEquals(List.of(), SectGenerator.validatePlan(plan), "plan validates");
        MemorySink sink = new MemorySink(FLAT, null);
        SectGenerator.BuildStats stats = new SectGenerator.BuildStats();
        SectGenerator.realizeCompound(sink, plan, RandomSource.create(seed), seed, stats);
        assertEquals(plan.slots().size(), stats.placedSlots, "every building placed: " + stats.skippedSlotIds);
        return sink;
    }

    private static MemorySink worldgenBuild(long seed, String variant, boolean chunked) {
        SectGenerator.SectPlan plan = SectGenerator.plan(seed, BASE, variant);
        assertEquals(List.of(), SectGenerator.validatePlan(plan), "plan validates");
        SectMountain mountain = SectGenerator.buildMountain(seed, plan, ROLLING::top);
        MemorySink sink = new MemorySink(ROLLING, mountain);
        if (!chunked) {
            SectGenerator.BuildStats stats = new SectGenerator.BuildStats();
            SectGenerator.writeMountain(sink, plan, mountain, stats);
            SectGenerator.realizeCompound(sink, plan, RandomSource.create(seed), seed, stats);
            assertEquals(plan.slots().size(), stats.placedSlots, "every building placed: " + stats.skippedSlotIds);
            return sink;
        }
        int m = SectGenerator.MOUNTAIN_MARGIN;
        for (int cx = (BASE.getX() - m) >> 4; cx <= (BASE.getX() + SectGenerator.SITE_WIDTH + m) >> 4; cx++) {
            for (int cz = (BASE.getZ() - m) >> 4; cz <= (BASE.getZ() + SectGenerator.SITE_DEPTH + m) >> 4; cz++) {
                sink.clip = new SectGenerator.Clip(cx << 4, cz << 4, (cx << 4) + 15, (cz << 4) + 15);
                SectGenerator.BuildStats stats = new SectGenerator.BuildStats();
                SectGenerator.writeMountain(sink, plan, mountain, stats);
                SectGenerator.realizeCompound(sink, plan, RandomSource.create(seed), seed, stats);
            }
        }
        sink.clip = SectGenerator.Clip.UNBOUNDED;
        return sink;
    }

    private static List<MemorySink> allBuilds(long seed) {
        return List.of(commandBuild(seed, null), worldgenBuild(seed, null, false));
    }

    private static BlockPos local(int x, int y, int z) {
        return new BlockPos(BASE.getX() + x, y, BASE.getZ() + z);
    }

    /**
     * Walking surface reached from {@code prevFoot} (feet y) at local (x, z): the top of the
     * highest block at or below knee height (prevFoot + 1), stairs as full blocks.
     */
    private static int footing(MemorySink w, int x, int z, int prevFoot) {
        for (int y = prevFoot + 1; y > prevFoot - 12; y--) {
            if (!w.get(local(x, y, z)).isAir()) return y + 1;
        }
        return prevFoot - 12;
    }

    // --- 1. the axis is walkable --------------------------------------------

    @Test
    void theAxisIsWalkableFromTheForecourtToTheHall() {
        for (long seed : SEEDS) {
            for (MemorySink w : allBuilds(seed)) {
                SectGenerator.SectPlan plan = SectGenerator.plan(seed, BASE, null);
                SectGenerator.Slot gate = SectGenerator.gateSlot(plan);
                for (int x = 29; x <= 33; x++) {
                    int prev = E;
                    for (int z = SectGenerator.APRON_Z0; z <= plan.axisEndZ(); z++) {
                        boolean inGate = z >= gate.bounds().z0() && z <= gate.bounds().z1();
                        if (inGate && Math.abs(x - SectGenerator.AXIS_X) > 1) continue;
                        int foot = footing(w, x, z, prev);
                        String at = "seed " + seed + " x " + x + " z " + z + " foot " + foot + " prev " + prev;
                        assertTrue(Math.abs(foot - prev) <= 1, "single step: " + at);
                        for (int h = 0; h < 3; h++) {
                            assertTrue(w.get(local(x, foot + h, z)).isAir(),
                                    "headroom " + h + ": " + w.get(local(x, foot + h, z)) + " at " + at);
                        }
                        prev = foot;
                    }
                    assertEquals(E + 4 * SectGenerator.TERRACE_RISE, prev, "arrives on the summit floor");
                }
            }
        }
    }

    // --- 2. nothing floats ----------------------------------------------------

    @Test
    void noBlockFloatsFreeOfEveryNeighbour() {
        for (long seed : SEEDS) {
            for (MemorySink w : allBuilds(seed)) {
                List<String> floating = new ArrayList<>();
                for (Map.Entry<BlockPos, BlockState> e : w.blocks.entrySet()) {
                    if (e.getValue().isAir()) continue;
                    boolean touches = false;
                    for (Direction d : Direction.values()) {
                        if (!w.get(e.getKey().relative(d)).isAir()) {
                            touches = true;
                            break;
                        }
                    }
                    if (!touches) {
                        BlockPos p = e.getKey().subtract(BASE);
                        floating.add(e.getValue().getBlock() + "@" + p.getX() + "," + (p.getY()) + "," + p.getZ());
                    }
                }
                assertEquals(List.of(), floating.subList(0, Math.min(20, floating.size())),
                        "seed " + seed + ": " + floating.size() + " floating blocks");
            }
        }
    }

    // --- 3. no wall blocks from the generator ----------------------------------

    @Test
    void theGeneratorWritesNoWallBlocks() {
        for (long seed : SEEDS) {
            for (MemorySink w : allBuilds(seed)) {
                long walls = w.blocks.entrySet().stream()
                        .filter(e -> !w.fromTemplate.contains(e.getKey()))
                        .filter(e -> e.getValue().getBlock() instanceof WallBlock)
                        .count();
                assertEquals(0, walls, "seed " + seed + ": wall blocks outside the templates");
            }
        }
        assertEquals(Set.of(), RAW_STONE_BRICK_WALL, "templates that carry vanilla stone_brick_wall themselves");
    }

    // --- 4. buildings keep off the axis and mirror ----------------------------

    @Test
    void onlyTheGateAndTheHallStandOnTheAxisAndFlanksMirror() {
        for (long seed : SEEDS) {
            for (String variant : SectCourtyard.variants()) {
                SectGenerator.SectPlan plan = SectGenerator.plan(seed, BASE, variant);
                for (SectGenerator.Slot s : plan.slots()) {
                    boolean axial = s.role().equals("on_axis");
                    if (axial) {
                        assertTrue(s.archetype().equals("sect_gate") || s.archetype().equals("sect_main_hall"), s.id());
                        assertEquals(2 * SectGenerator.AXIS_X, s.bounds().x0() + s.bounds().x2(), "centred " + s.id());
                    } else {
                        SectGenerator.Rect r = s.bounds();
                        assertTrue(r.x2() < SectGenerator.AXIS_X0 || r.x0() > SectGenerator.AXIS_X1, "off the corridor " + s.id());
                    }
                    assertTrue(!s.archetype().equals("pagoda"), "no pagoda on a terrace");
                }
                for (SectGenerator.Terrace t : plan.terraces()) {
                    SectGenerator.Slot left = null;
                    SectGenerator.Slot right = null;
                    for (SectGenerator.Slot s : plan.slots()) {
                        if (s.terraceIndex() != t.index()) continue;
                        if (s.role().equals("flank_left")) left = s;
                        if (s.role().equals("flank_right")) right = s;
                    }
                    if (left == null && right == null) continue;
                    assertTrue(left != null && right != null, "flanks come in pairs on " + t.name());
                    int tol = left.bounds().width() % 2 == 0 ? 1 : 0;
                    assertTrue(Math.abs(left.bounds().x2() + right.bounds().x0() - 2 * SectGenerator.AXIS_X) <= tol, t.name());
                    assertTrue(Math.abs(left.bounds().x0() + right.bounds().x2() - 2 * SectGenerator.AXIS_X) <= tol, t.name());
                    assertEquals(left.bounds().z0(), right.bounds().z0(), t.name());
                    assertEquals(left.bounds().z1(), right.bounds().z1(), t.name());
                }
                long pavilions = plan.slots().stream()
                        .filter(s -> s.terraceName().equals("scripture") && s.archetype().equals("scripture_pavilion")).count();
                assertEquals(2, pavilions, "two scripture pavilions");
                assertTrue(plan.galleries().isEmpty(), "no galleries");
            }
        }
    }

    // --- 5. the grand stairs ----------------------------------------------------

    @Test
    void everyGrandStairClimbsOneTerraceInSingleSteps() {
        for (long seed : SEEDS) {
            for (MemorySink w : allBuilds(seed)) {
                SectGenerator.SectPlan plan = SectGenerator.plan(seed, BASE, null);
                assertEquals(SectGenerator.TERRACE_COUNT - 1, plan.stairs().size());
                for (SectGenerator.AxisStair st : plan.stairs()) {
                    SectGenerator.Terrace lower = plan.terraces().get(st.lower());
                    SectGenerator.Terrace upper = plan.terraces().get(st.upper());
                    assertEquals(SectGenerator.STAIR_X0, st.bounds().x0());
                    assertEquals(SectGenerator.STAIR_X1, st.bounds().x2());
                    for (int x = st.bounds().x0(); x <= st.bounds().x2(); x++) {
                        int prev = lower.elevation();
                        int start = prev;
                        for (int z = st.bounds().z0() - 1; z <= st.bounds().z1() + 1; z++) {
                            int foot = footing(w, x, z, prev);
                            String at = "seed " + seed + " stair " + st.id() + " x " + x + " z " + z;
                            assertTrue(foot >= prev && foot - prev <= 1, "rises in single steps: " + at + " " + foot + " after " + prev);
                            assertTrue(w.get(local(x, foot, z)).isAir() && w.get(local(x, foot + 1, z)).isAir(), "open: " + at);
                            prev = foot;
                        }
                        assertEquals(upper.elevation() - lower.elevation(), prev - start, "climbs one terrace");
                    }
                    // the cheek walls stand one block above the treads, solid down to the lower floor
                    for (int z = st.bounds().z0(); z <= st.bounds().z1(); z++) {
                        for (int x : new int[]{st.bounds().x0() - 1, st.bounds().x2() + 1}) {
                            for (int y = st.lowerFloorY(); y <= st.treadY(z) + 1; y++) {
                                assertTrue(!w.get(local(x, y, z)).isAir(), "cheek " + x + "," + y + "," + z);
                            }
                        }
                    }
                }
            }
        }
    }

    // --- 6. courtyard cells are open ground -----------------------------------

    @Test
    void courtyardCellsAreOpenGround() {
        for (long seed : SEEDS) {
            List<BlockPos> cells = SectCourtyard.cells(seed, ANCHOR, null);
            for (MemorySink w : allBuilds(seed)) {
                for (BlockPos c : cells) {
                    BlockState below = w.get(c.below());
                    assertTrue(below.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                            "solid under " + c.subtract(BASE) + ": " + below);
                    assertTrue(w.get(c).isAir() && w.get(c.above()).isAir(), "open at " + c.subtract(BASE));
                }
            }
        }
    }

    // --- worldgen slices join -------------------------------------------------

    @Test
    void chunkSlicesBuildTheSameCompound() {
        MemorySink whole = worldgenBuild(7L, null, false);
        MemorySink sliced = worldgenBuild(7L, null, true);
        Set<BlockPos> keys = new HashSet<>(whole.blocks.keySet());
        keys.addAll(sliced.blocks.keySet());
        List<String> diff = new ArrayList<>();
        for (BlockPos p : keys) {
            if (!whole.get(p).equals(sliced.get(p))) {
                diff.add(p.subtract(BASE) + " " + whole.get(p) + " vs " + sliced.get(p));
                if (diff.size() >= 10) break;
            }
        }
        assertEquals(List.of(), diff);
    }

    @Test
    void theForecourtIsLevelAndTheMountainCoreHasNoNoise() {
        for (long seed : SEEDS) {
            SectGenerator.SectPlan plan = SectGenerator.plan(seed, BASE, null);
            SectMountain m = SectGenerator.buildMountain(seed, plan, ROLLING::top);
            for (int x = SectGenerator.APRON_X0; x <= SectGenerator.APRON_X1; x++) {
                for (int z = SectGenerator.APRON_Z0; z <= SectGenerator.APRON_Z1; z++) {
                    assertEquals(E - 1, m.height(x, z), "forecourt " + x + "," + z);
                }
            }
            for (SectGenerator.Terrace t : plan.terraces()) {
                SectGenerator.Rect r = t.bounds();
                for (int x = r.x0(); x <= r.x2(); x++) {
                    for (int z = r.z0(); z <= r.z1(); z++) {
                        assertEquals(t.elevation() - 1, m.height(x, z));
                    }
                }
            }
            for (SectGenerator.RetainingFace f : plan.retaining()) {
                SectGenerator.Terrace lower = plan.terraces().get(f.lower());
                SectGenerator.Terrace upper = plan.terraces().get(f.upper());
                for (int x = lower.bounds().x0(); x <= lower.bounds().x2(); x++) {
                    boolean inUpper = x >= upper.bounds().x0() && x <= upper.bounds().x2();
                    for (int z = f.bounds().z0(); z <= f.bounds().z1(); z++) {
                        assertEquals((inUpper ? upper : lower).elevation() - 1, m.height(x, z), "band " + x + "," + z);
                    }
                }
            }
            // the taper strips slope one block per block away from the nearest terrace
            SectGenerator.Terrace summit = plan.terraces().get(4);
            for (int x = 2; x < summit.bounds().x0(); x++) {
                assertEquals(summit.elevation() - 1 - (summit.bounds().x0() - x), m.height(x, 160), "strip x " + x);
            }
        }
    }

    // --- the gate passage keeps the plaque ---------------------------------------

    @Test
    void theGatePassageKeepsTheHangingPlaque() {
        for (long seed : SEEDS) {
            SectGenerator.SectPlan plan = SectGenerator.plan(seed, BASE, null);
            SectGenerator.Slot gate = SectGenerator.gateSlot(plan);
            for (MemorySink w : allBuilds(seed)) {
                List<BlockPos> plaques = PLAQUES.get(gate.templateId());
                assertTrue(plaques != null && !plaques.isEmpty(), "gate template " + gate.templateId() + " has a plaque");
                BlockPos origin = new BlockPos(BASE.getX() + gate.bounds().x0(), E - 1, BASE.getZ() + gate.bounds().z0());
                for (BlockPos rel : plaques) {
                    BlockPos p = origin.offset(rel);
                    assertTrue(w.fromTemplate.contains(p) && !w.get(p).isAir(),
                            "seed " + seed + ": plaque block " + rel + " of " + gate.templateId() + " survives, got " + w.get(p));
                }
                // the passage itself: GATE_PASSAGE_H air rows over the paved plinth
                for (int x = SectGenerator.AXIS_X - 1; x <= SectGenerator.AXIS_X + 1; x++) {
                    for (int z = gate.bounds().z0(); z <= gate.bounds().z1(); z++) {
                        for (int y = E + 1; y <= E + SectGenerator.GATE_PASSAGE_H; y++) {
                            assertTrue(w.get(local(x, y, z)).isAir(), "passage air " + x + "," + y + "," + z);
                        }
                    }
                }
            }
        }
    }

    // --- retaining-face pilasters -------------------------------------------------

    @Test
    void retainingFacesCarrySymmetricPilasters() {
        for (long seed : SEEDS) {
            SectGenerator.SectPlan plan = SectGenerator.plan(seed, BASE, null);
            Set<BlockPos> courtyard = new HashSet<>(SectCourtyard.cells(seed, ANCHOR, null));
            for (MemorySink w : allBuilds(seed)) {
                for (SectGenerator.RetainingFace f : plan.retaining()) {
                    SectGenerator.Terrace lower = plan.terraces().get(f.lower());
                    SectGenerator.Terrace upper = plan.terraces().get(f.upper());
                    List<Integer> xs = SectGenerator.pilasterXs(plan, f);
                    String at = "seed " + seed + " " + f.id();
                    assertTrue(xs.size() >= 2, at + ": pilasters " + xs);
                    Set<Integer> mirrored = new HashSet<>();
                    for (int x : xs) mirrored.add(2 * SectGenerator.AXIS_X - x);
                    assertEquals(new HashSet<>(xs), mirrored, at + ": symmetric about the axis");
                    int pz = f.bounds().z0() - 1;
                    assertEquals(lower.bounds().z1(), pz, at + ": on the lower terrace's last row");
                    for (int x : xs) {
                        assertTrue(Math.abs(x - SectGenerator.AXIS_X) > SectGenerator.STAIR_W / 2 + 1,
                                at + ": clear of the stair and cheeks, x " + x);
                        assertEquals(0, (x - SectGenerator.AXIS_X) % SectGenerator.PILASTER_SPACING, at + " x " + x);
                        for (int y = lower.elevation(); y < upper.elevation() - 1; y++) {
                            assertEquals(Blocks.STONE_BRICKS, w.get(local(x, y, pz)).getBlock(), at + " pilaster " + x + "," + y);
                        }
                        assertEquals(Blocks.CHISELED_STONE_BRICKS, w.get(local(x, upper.elevation() - 1, pz)).getBlock(),
                                at + " pilaster top " + x);
                        assertTrue(w.get(local(x, upper.elevation(), pz)).isAir(), at + " nothing on the pilaster " + x);
                        assertTrue(!courtyard.contains(local(x, lower.elevation(), pz)), at + " not a courtyard cell " + x);
                    }
                }
                // behind the gate terrace the bell and drum towers (x4..20 / x42..58) stand against the
                // face and hide four; the other faces carry all six
                assertEquals(List.of(23, 39), SectGenerator.pilasterXs(plan, plan.retaining().get(0)));
                for (int i = 1; i < plan.retaining().size(); i++) {
                    assertEquals(List.of(7, 15, 23, 39, 47, 55), SectGenerator.pilasterXs(plan, plan.retaining().get(i)));
                }
            }
        }
    }

    // --- the mountain skirt is smooth ---------------------------------------------

    private static boolean cliffBack(SectGenerator.SectPlan plan, int x, int z) {
        SectGenerator.Terrace summit = plan.terraces().get(plan.terraces().size() - 1);
        return z > summit.bounds().z1() && x >= summit.bounds().x0() && x <= summit.bounds().x2();
    }

    /** The noise-free core rule: terrace floor, band at the upper floor within its width, else the taper slope. */
    private static int expectedCore(SectGenerator.SectPlan plan, int x, int z) {
        List<SectGenerator.Terrace> ts = plan.terraces();
        for (SectGenerator.Terrace t : ts) {
            SectGenerator.Rect r = t.bounds();
            if (x >= r.x0() && x <= r.x2() && z >= r.z0() && z <= r.z1()) return t.elevation() - 1;
        }
        for (int i = 0; i < ts.size() - 1; i++) {
            SectGenerator.Rect lo = ts.get(i).bounds();
            SectGenerator.Rect up = ts.get(i + 1).bounds();
            if (z > lo.z1() && z < up.z0()) {
                if (x >= up.x0() && x <= up.x2()) return ts.get(i + 1).elevation() - 1;
                if (x >= lo.x0() && x <= lo.x2()) return ts.get(i).elevation() - 1;
            }
        }
        int best = Integer.MIN_VALUE;
        int bestDist = Integer.MAX_VALUE;
        for (SectGenerator.Terrace t : ts) {
            SectGenerator.Rect r = t.bounds();
            int d = Math.max(Math.max(Math.max(r.x0() - x, 0), x - r.x2()), Math.max(Math.max(r.z0() - z, 0), z - r.z1()));
            int h = t.elevation() - 1 - d;
            if (d < bestDist || (d == bestDist && h > best)) {
                bestDist = d;
                best = h;
            }
        }
        return best;
    }

    @Test
    void theMountainSkirtIsSmoothAndTheCoreUnchanged() {
        long[] seeds = {7L, -123456789L, 20260618L, 1L, 424242L, Long.MIN_VALUE + 5};
        for (long seed : seeds) {
            SectGenerator.SectPlan plan = SectGenerator.plan(seed, BASE, null);
            for (Ground ground : List.of(ROLLING, LOW_FLAT)) {
                SectMountain m = SectGenerator.buildMountain(seed, plan, ground::top);
                int mm = SectGenerator.MOUNTAIN_MARGIN;
                int x0 = m.coreX0() - mm;
                int x1 = m.coreX1() + mm;
                int z0 = m.coreZ0() - mm;
                int z1 = m.coreZ1() + mm;
                int worst = 0;
                String worstAt = "";
                Set<Integer> sideHeights = new HashSet<>();
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        boolean core = x >= m.coreX0() && x <= m.coreX1() && z >= m.coreZ0() && z <= m.coreZ1();
                        boolean apron = x >= SectGenerator.APRON_X0 && x <= SectGenerator.APRON_X1
                                && z >= SectGenerator.APRON_Z0 && z <= SectGenerator.APRON_Z1;
                        int h = m.height(x, z);
                        if (apron) {
                            assertEquals(E - 1, h, "forecourt " + x + "," + z);
                        } else if (core) {
                            assertEquals(expectedCore(plan, x, z), h, "core " + x + "," + z);
                        }
                        if (core || cliffBack(plan, x, z)) continue;
                        assertTrue(h >= ground.top(x, z), "never below natural ground " + x + "," + z);
                        for (int[] n : new int[][]{{x + 1, z}, {x, z + 1}}) {
                            int nx = n[0];
                            int nz = n[1];
                            if (nx > x1 || nz > z1) continue;
                            boolean nCore = nx >= m.coreX0() && nx <= m.coreX1() && nz >= m.coreZ0() && nz <= m.coreZ1();
                            if (nCore || cliffBack(plan, nx, nz)) continue;
                            int dh = Math.abs(h - m.height(nx, nz));
                            if (dh > worst) {
                                worst = dh;
                                worstAt = x + "," + z + " -> " + nx + "," + nz;
                            }
                        }
                    }
                }
                assertTrue(worst <= SectMountain.SKIRT_SLOPE_LIMIT,
                        "seed " + seed + ": skirt step " + worst + " at " + worstAt);
                // the skirt has relief, not a bare cone: the tall side beside the summit varies
                SectGenerator.Terrace summit = plan.terraces().get(4);
                for (int z = summit.bounds().z0(); z <= summit.bounds().z1(); z += 3) {
                    sideHeights.add(m.height(m.coreX1() + 6, z) - ground.top(m.coreX1() + 6, z));
                }
                assertTrue(sideHeights.size() > 1, "seed " + seed + ": textured flank " + sideHeights);
                // and it meets natural ground by the skirt's edge
                for (int z = z0; z <= z1; z += 7) {
                    assertEquals(ground.top(x0, z), m.height(x0, z), "edge meets natural ground at z " + z);
                }
            }
        }
    }

    @Test
    void skirtColumnsAreMostlyStoneWithAFewOtherStones() {
        SectGenerator.SectPlan plan = SectGenerator.plan(7L, BASE, null);
        MemorySink w = worldgenBuild(7L, null, false);
        SectMountain m = SectGenerator.buildMountain(7L, plan, ROLLING::top);
        Map<net.minecraft.world.level.block.Block, Integer> tops = new HashMap<>();
        int skirt = 0;
        for (int x = m.coreX0() - 20; x <= m.coreX1() + 20; x++) {
            for (int z = m.coreZ0() - 20; z <= m.coreZ1() + 20; z++) {
                if (!m.isSkirt(x, z)) continue;
                skirt++;
                tops.merge(w.get(local(x, m.height(x, z), z)).getBlock(), 1, Integer::sum);
                assertEquals(Blocks.STONE, w.get(local(x, m.height(x, z) - 1, z)).getBlock(), "stone under the top " + x + "," + z);
            }
        }
        assertTrue(tops.getOrDefault(Blocks.STONE, 0) > skirt / 2, "mostly stone: " + tops);
        for (net.minecraft.world.level.block.Block b : List.of(Blocks.ANDESITE, Blocks.TUFF, Blocks.COBBLED_DEEPSLATE)) {
            assertTrue(tops.getOrDefault(b, 0) > skirt / 20, b + " present: " + tops);
        }
    }
}
