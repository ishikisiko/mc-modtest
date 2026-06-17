package com.example.myvillage.sect;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.town.ModBlockFallback;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Runtime terraced cultivation-sect realizer.
 *
 * Produces a terraced axial sect compound structurally equivalent to the Python
 * planner in tools/buildgen/sect.py (gate / disciple / assembly / scripture /
 * summit terrace stack, single fall-line ritual axis, mirrored flanks joined by
 * covered galleries, cliff-backed summit, optional detached-spire flying-bridge
 * feature). Block writes route through a {@link SectSink} so the same plan and
 * realizer serve both the on-the-spot {@code /myvillage sect} command and
 * worldgen ({@link SectStructurePiece}). For worldgen / force-generate the
 * mountain is first derived from the terrace profile (反推山形, {@link SectMountain})
 * and the realizer rests the compound on it; the on-the-spot command rests it on
 * the live world surface, unchanged.
 *
 * Geometry mirrors sect.py with no shared RNG: every cell derives from seed xor
 * coordinates, so the same seed + site yields the same compound (Python/Java
 * parity is asserted by validate_sect_generation.py).
 */
public final class SectGenerator {
    static final int TERRACE_COUNT = 5;
    static final int TERRACE_RISE = 8;
    static final int TERRACE_DEPTH = 28;
    static final int TERRACE_WIDTH = 58;
    static final int SUMMIT_TAPER = 4;
    static final int AXIS_STAIR_W = 5;
    static final int CLIFF_BACK_HEIGHT = 12;
    static final int Z_MARGIN = 4;

    static final int SITE_WIDTH = TERRACE_WIDTH + 6;
    static final int SITE_DEPTH = 2 * Z_MARGIN + TERRACE_COUNT * TERRACE_DEPTH
            + (TERRACE_COUNT - 1) * TERRACE_RISE;

    static final int TEMPLATE_GROUND_LAYER = 0;
    static final int BLOCK_FLAGS = Block.UPDATE_CLIENTS;
    static final int MAX_IMPORTANCE_TIER = 3;
    static final int FEATURE_PERIOD = 4;

    /** Margin (cells) the derived mountain extends beyond the compound footprint. */
    static final int MOUNTAIN_MARGIN = SectMountain.SKIRT_RADIUS + 4;

    private SectGenerator() {
    }

    public static long seedFromSource(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = player.blockPosition();
        long site = (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL);
        return source.getLevel().getSeed() ^ site;
    }

    /** On-the-spot build: rests the compound on the live world surface. */
    public static int generate(CommandSourceStack source, long seed) throws CommandSyntaxException {
        return build(source, seed, null, false);
    }

    /**
     * Force-generate a worldgen-style sect (with its derived mountain) at the
     * player, optionally forcing a detached-spire variant ("none" / a variant
     * name / null = per-seed selection).
     */
    public static int generateForced(CommandSourceStack source, long seed, String featureOverride)
            throws CommandSyntaxException {
        return build(source, seed, featureOverride, true);
    }

    private static int build(CommandSourceStack source, long seed, String featureOverride,
                             boolean worldgenStyle) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        BlockPos base = player.blockPosition().offset(-SITE_WIDTH / 2, 0, -SITE_DEPTH / 2);

        SectPlan plan = plan(seed, base, featureOverride);
        List<String> validationErrors = validatePlan(plan);
        if (!validationErrors.isEmpty()) {
            source.sendFailure(Component.literal("Sect plan failed validation: " + validationErrors));
            return 0;
        }

        RandomSource templateRandom = RandomSource.create(mixSeed(seed, base));
        BuildStats stats = new BuildStats();
        List<ChunkPos> forcedChunks = new ArrayList<>();
        List<String> loadFailures = new ArrayList<>();
        SectMountain mountain = null;
        try {
            if (worldgenStyle) {
                forceLoadFootprint(level, base.offset(-MOUNTAIN_MARGIN, 0, -MOUNTAIN_MARGIN),
                        SITE_WIDTH + 2 * MOUNTAIN_MARGIN, SITE_DEPTH + 2 * MOUNTAIN_MARGIN,
                        forcedChunks, loadFailures);
                mountain = buildMountain(seed, plan,
                        (x, z) -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                base.getX() + x, base.getZ() + z));
                ServerLevelSink sink = new ServerLevelSink(level, base, mountain);
                writeMountain(sink, plan, mountain, stats);
                placeCloudSea(sink, plan, mountain, seed, stats);
                realizeCompound(sink, plan, templateRandom, seed, stats);
            } else {
                forceLoadFootprint(level, base, SITE_WIDTH, SITE_DEPTH, forcedChunks, loadFailures);
                ServerLevelSink sink = new ServerLevelSink(level, base, null);
                realizeCompound(sink, plan, templateRandom, seed, stats);
            }
        } finally {
            for (ChunkPos c : forcedChunks) {
                level.setChunkForced(c.x, c.z, false);
            }
        }

        if (!loadFailures.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("Unable to force-load regions: " + loadFailures),
                    false);
        }
        final boolean derived = worldgenStyle;
        source.sendSuccess(
                () -> Component.literal("Generated sect compound seed=" + seed
                        + (derived ? " (worldgen-style, derived mountain)" : "")
                        + " footprint=" + SITE_WIDTH + "x" + SITE_DEPTH
                        + " terraces=" + plan.terraces.size()
                        + " placed=" + stats.placedSlots
                        + " skipped=" + stats.skippedSlots
                        + " galleries=" + stats.galleriesPlaced
                        + " feature=" + (plan.feature == null ? "none"
                                : plan.feature.variant)
                        + (stats.fallbackSubstitutions > 0
                                ? " fallback_substitutions=" + stats.fallbackSubstitutions
                                : "")
                        + " blocks~=" + stats.blocksPlaced),
                false);
        return 1;
    }

    private static long mixSeed(long seed, BlockPos base) {
        return seed ^ (base.getX() * 341873128712L) ^ (base.getZ() * 132897987541L);
    }

    private static void forceLoadFootprint(
            ServerLevel level, BlockPos base, int width, int depth,
            List<ChunkPos> forced, List<String> failures) {
        int minCX = base.getX() >> 4;
        int maxCX = (base.getX() + width - 1) >> 4;
        int minCZ = base.getZ() >> 4;
        int maxCZ = (base.getZ() + depth - 1) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                if (!level.getWorldBorder().isWithinBounds(pos)) {
                    failures.add("chunk(" + cx + "," + cz + ") outside world border");
                    continue;
                }
                level.setChunkForced(cx, cz, true);
                forced.add(pos);
                LevelChunk chunk = level.getChunk(cx, cz);
                if (chunk == null || chunk.isEmpty()) {
                    // still buildable; nothing else to do.
                }
            }
        }
    }

    // --- mountain derivation hooks (反推山形) --------------------------------

    /** Build the derived mountain for a plan at its base, given natural heights. */
    static SectMountain buildMountain(long seed, SectPlan plan, SectMountain.NaturalHeight natural) {
        List<SectMountain.TerraceBox> boxes = new ArrayList<>();
        for (Terrace t : plan.terraces) {
            boxes.add(new SectMountain.TerraceBox(t.index, t.name, t.elevation,
                    t.bounds.x0, t.bounds.z0, t.bounds.x2(), t.bounds.z1, t.cliffBack));
        }
        int[] detached = null;
        if (plan.feature != null) {
            Rect d = plan.feature.detachedBounds;
            detached = new int[]{d.x0, d.z0, d.x2(), d.z1};
        }
        return SectMountain.derive(seed, boxes, TERRACE_RISE, CLIFF_BACK_HEIGHT, detached, natural);
    }

    /**
     * Bake the derived mountain as solid stone: each footprint+skirt column is
     * filled from the natural surface up to the derived height, and burying
     * terrain above the derived silhouette is cleared. Cliff-back and spire
     * pillars come for free from {@link SectMountain#height}.
     */
    static void writeMountain(SectSink sink, SectPlan plan, SectMountain m, BuildStats stats) {
        int minX = m.coreX0() - MOUNTAIN_MARGIN;
        int maxX = m.coreX1() + MOUNTAIN_MARGIN;
        int minZ = m.coreZ0() - MOUNTAIN_MARGIN;
        int maxZ = m.coreZ1() + MOUNTAIN_MARGIN;
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int top = m.height(x, z);
                int nat = m.naturalAt(x, z);
                for (int y = Math.min(nat, top); y <= top; y++) {
                    place(sink, plan.base.offset(x, y, z), stone, stats);
                }
                // clear terrain that would bury the derived silhouette
                for (int y = top + 1; y <= nat; y++) {
                    place(sink, plan.base.offset(x, y, z), air, stats);
                }
            }
        }
    }

    /**
     * Lay the horizontal cloud-sea (云海面) sheet of translucent glass at the
     * configured Y in the open air between the gate and disciple terraces, with
     * feathered edges and occasional powder-snow (云絮) wisps at the terrace edges.
     */
    static void placeCloudSea(SectSink sink, SectPlan plan, SectMountain m, long seed, BuildStats stats) {
        Terrace gate = plan.terraces.get(0);
        Terrace disciple = plan.terraces.size() > 1 ? plan.terraces.get(1) : gate;
        int y = m.cloudSeaY();
        int z0 = gate.bounds.z1 + 1;
        int z1 = disciple.bounds.z0 - 1;
        if (z1 < z0) {
            return;
        }
        BlockState cloud = Blocks.WHITE_STAINED_GLASS.defaultBlockState();
        BlockState wisp = Blocks.POWDER_SNOW.defaultBlockState();
        for (int x = m.coreX0(); x <= m.coreX1(); x++) {
            int edge = Math.min(x - m.coreX0(), m.coreX1() - x);
            for (int z = z0; z <= z1; z++) {
                if (m.height(x, z) >= y) {
                    continue;  // only float cloud over open air below the terraces
                }
                int zEdge = Math.min(z - z0, z1 - z);
                // feather the rim: thin out cells near the sheet's edges
                if ((edge <= 1 || zEdge == 0) && m.featherNoise(x, z, 4) < 1) {
                    continue;
                }
                if (zEdge == 0 && m.featherNoise(x, z, 3) > 1) {
                    place(sink, plan.base.offset(x, y, z), wisp, stats);
                } else {
                    place(sink, plan.base.offset(x, y, z), cloud, stats);
                }
            }
        }
    }

    // --- plan (mirrors tools/buildgen/sect.py) ------------------------------

    private static String[] skeletonNames(int count) {
        if (count == 4) return new String[]{"gate", "disciple", "scripture", "summit"};
        if (count == 6) return new String[]{"gate", "disciple", "disciple", "assembly", "scripture", "summit"};
        return new String[]{"gate", "disciple", "assembly", "scripture", "summit"};
    }

    static SectPlan plan(long seed, BlockPos base) {
        return plan(seed, base, null);
    }

    static SectPlan plan(long seed, BlockPos base, String featureOverride) {
        int count = TERRACE_COUNT;
        String[] names = skeletonNames(count);
        int axisHalf = AXIS_STAIR_W / 2;
        int xAnchor = (SITE_WIDTH - TERRACE_WIDTH) / 2;

        List<Terrace> terraces = new ArrayList<>();
        int z = Z_MARGIN;
        for (int i = 0; i < count; i++) {
            int width = TERRACE_WIDTH - Math.floorDiv(SUMMIT_TAPER * i, count - 1);
            int x0 = xAnchor + (TERRACE_WIDTH - width) / 2;
            int x1 = x0 + width - 1;
            int z0 = z;
            int z1 = z + TERRACE_DEPTH - 1;
            int elevation = base.getY() + i * TERRACE_RISE;
            terraces.add(new Terrace(i, names[i], elevation, new Rect(x0, z0, x1, z1), width, TERRACE_DEPTH,
                    i == count - 1));
            z = z1 + 1 + TERRACE_RISE;
        }

        List<Slot> slots = new ArrayList<>();
        for (Terrace terrace : terraces) {
            SlotSpec[] specs = slotRoster(terrace.name);
            SlotSpec onAxisSpec = null;
            for (SlotSpec s : specs) if (s.role.equals("on_axis")) onAxisSpec = s;
            int[] onAxisSpan = null;
            if (onAxisSpec != null) onAxisSpan = onAxisXSpan(terrace, onAxisSpec.archetype);
            for (SlotSpec spec : specs) {
                Rect bounds = slotBounds(terrace, spec, axisHalf, onAxisSpan);
                String template = templateFor(spec.archetype, seed, terrace.index);
                slots.add(new Slot(
                        "slot_" + terrace.name + "_" + spec.role + "_" + terrace.index,
                        terrace.index, terrace.name, spec.role, spec.archetype, template,
                        archetypeImportance(spec.archetype), bounds,
                        terrace.name.equals("summit") && spec.role.equals("on_axis")));
            }
        }

        Terrace first = terraces.get(0);
        Terrace last = terraces.get(terraces.size() - 1);
        int cx = (first.bounds.x0 + first.bounds.x2()) / 2;
        int axisX0 = cx - axisHalf;
        int axisX1 = cx + axisHalf;
        int axisZ0 = first.bounds.z0;
        int axisZ1 = last.bounds.z1;
        Set<Cell> axisCells = rect(axisX0, axisZ0, axisX1, axisZ1);

        List<RetainingFace> retaining = new ArrayList<>();
        List<AxisStair> stairs = new ArrayList<>();
        for (int i = 0; i < terraces.size() - 1; i++) {
            Terrace lower = terraces.get(i);
            Terrace upper = terraces.get(i + 1);
            int stairZ0 = lower.bounds.z1 + 1;
            int stairZ1 = upper.bounds.z0 - 1;
            stairs.add(new AxisStair("stair_" + i + "_" + (i + 1), i, i + 1,
                    new Rect(axisX0, stairZ0, axisX1, stairZ1)));
            retaining.add(new RetainingFace("retain_" + i + "_" + (i + 1), i, i + 1,
                    new Rect(upper.bounds.x0, stairZ0, upper.bounds.x2(), stairZ1), TERRACE_RISE));
        }

        List<GalleryLink> galleries = buildGalleries(terraces, slots);
        FlyingBridgeFeature feature = buildFeature(seed, terraces, slots, featureOverride);

        return new SectPlan(base, terraces, axisCells, slots, galleries, retaining, stairs, feature);
    }

    private record SlotSpec(String archetype, String role, String align) {
    }

    private static SlotSpec[] slotRoster(String terraceName) {
        switch (terraceName) {
            case "gate" -> {
                return new SlotSpec[]{
                        new SlotSpec("sect_gate", "on_axis", "front"),
                        new SlotSpec("bell_drum_tower", "flank_left", "back"),
                        new SlotSpec("bell_drum_tower", "flank_right", "back"),
                };
            }
            case "disciple" -> {
                return new SlotSpec[]{
                        new SlotSpec("disciple_quarters", "flank_left", "center"),
                        new SlotSpec("disciple_quarters", "flank_right", "center"),
                };
            }
            case "assembly" -> {
                return new SlotSpec[]{
                        new SlotSpec("alchemy_room", "flank_left", "center"),
                        new SlotSpec("alchemy_room", "flank_right", "center"),
                };
            }
            case "scripture" -> {
                return new SlotSpec[]{
                        new SlotSpec("scripture_pavilion", "on_axis", "center"),
                        new SlotSpec("pagoda", "flank_left", "back"),
                        new SlotSpec("pagoda", "flank_right", "back"),
                };
            }
            case "summit" -> {
                return new SlotSpec[]{new SlotSpec("sect_main_hall", "on_axis", "back")};
            }
            default -> {
                return new SlotSpec[]{
                        new SlotSpec("disciple_quarters", "flank_left", "center"),
                        new SlotSpec("disciple_quarters", "flank_right", "center"),
                };
            }
        }
    }

    private static int[] onAxisXSpan(Terrace terrace, String archetype) {
        int cx = (terrace.bounds.x0 + terrace.bounds.x2()) / 2;
        int tw = Math.min(maxFootprint(archetype)[0],
                terrace.bounds.x2() - terrace.bounds.x0 + 1);
        int sx0 = cx - tw / 2;
        return new int[]{sx0, sx0 + tw - 1};
    }

    private static Rect slotBounds(Terrace terrace, SlotSpec spec, int axisHalf, int[] onAxisSpan) {
        int x0 = terrace.bounds.x0;
        int x1 = terrace.bounds.x2();
        int z0 = terrace.bounds.z0;
        int z1 = terrace.bounds.z1();
        int cx = (x0 + x1) / 2;
        int[] fp = maxFootprint(spec.archetype);
        int tw = Math.min(fp[0], x1 - x0 + 1);
        int td = Math.min(fp[1], z1 - z0 + 1);
        int sx0;
        int sx1;
        if (spec.role.equals("on_axis")) {
            sx0 = cx - tw / 2;
            sx1 = sx0 + tw - 1;
        } else if (spec.role.equals("flank_left")) {
            int inner = (onAxisSpan == null ? cx - axisHalf : onAxisSpan[0]) - 1;
            sx1 = inner;
            sx0 = sx1 - tw + 1;
        } else {
            int inner = (onAxisSpan == null ? cx + axisHalf : onAxisSpan[1]) + 1;
            sx0 = inner;
            sx1 = sx0 + tw - 1;
        }
        sx0 = Math.max(sx0, x0);
        sx1 = Math.min(sx1, x1);
        int[] zs = zSpan(terrace.depth, td, spec.align, z0, z1);
        return new Rect(sx0, zs[0], sx1, zs[1]);
    }

    private static int[] zSpan(int terraceDepth, int td, String align, int z0, int z1) {
        int sz0;
        if (align.equals("front")) {
            sz0 = z0;
        } else if (align.equals("back")) {
            sz0 = z1 - td + 1;
        } else {
            sz0 = z0 + Math.max(0, (terraceDepth - td) / 2);
        }
        return new int[]{sz0, sz0 + td - 1};
    }

    private static List<GalleryLink> buildGalleries(List<Terrace> terraces, List<Slot> slots) {
        List<GalleryLink> links = new ArrayList<>();
        for (Terrace terrace : terraces) {
            Slot onAxis = slotByRole(slots, terrace.index, "on_axis");
            Slot left = slotByRole(slots, terrace.index, "flank_left");
            Slot right = slotByRole(slots, terrace.index, "flank_right");
            if (onAxis != null) {
                Cell onCenter = onAxis.center();
                for (Slot flank : new Slot[]{left, right}) {
                    if (flank == null) continue;
                    links.add(new GalleryLink(
                            "gallery_" + terrace.name + "_" + flank.role + "_" + terrace.index,
                            "covered_gallery", onAxis.id, flank.id,
                            edgeFacing(onAxis, flank.center()), edgeFacing(flank, onCenter),
                            new int[]{terrace.index, terrace.index}));
                }
            } else if (left != null && right != null) {
                links.add(new GalleryLink(
                        "gallery_" + terrace.name + "_cross_" + terrace.index,
                        "covered_gallery", left.id, right.id,
                        edgeFacing(left, right.center()), edgeFacing(right, left.center()),
                        new int[]{terrace.index, terrace.index}));
            }
        }
        return links;
    }

    private static FlyingBridgeFeature buildFeature(long seed, List<Terrace> terraces, List<Slot> slots,
                                                    String featureOverride) {
        String chosen = featureChoiceWithOverride(seed, featureOverride);
        if (chosen == null) return null;
        String[] spec = featureSpec(chosen);
        String archetype = spec[0];
        int offX = Integer.parseInt(spec[1]);
        int offZ = Integer.parseInt(spec[2]);
        String bearing = spec[3];
        int span = Integer.parseInt(spec[4]);
        String shape = spec[5];
        String template = templateFor(archetype, seed, terraces.size());
        int[] fp = templateFootprint(template);
        int tw = fp[0];
        int td = fp[1];

        Terrace summit = terraces.get(terraces.size() - 1);
        int cx = (summit.bounds.x0 + summit.bounds.x2()) / 2;
        int cz = summit.bounds.z0;
        int dx0 = cx + offX - tw / 2;
        int dz0 = cz + offZ - td / 2;
        Rect detachedBounds = new Rect(dx0, dz0, dx0 + tw - 1, dz0 + td - 1);
        String detachedSlotId = "slot_detached_" + archetype + "_feature";
        Cell detachedCenter = new Cell((dx0 + dx0 + tw - 1) / 2, (dz0 + dz0 + td - 1) / 2);

        Slot onAxis = slotByRole(slots, summit.index, "on_axis");
        String fromSlotId = onAxis != null ? onAxis.id : "summit_terrace";
        Rect summitRect = onAxis != null ? onAxis.bounds : summit.bounds;
        Cell fromCell = edgeFacingRect(detachedCenter, summitRect);
        Cell toCell = edgeFacingRect(fromCell, detachedBounds);
        GalleryLink bridge = new GalleryLink("flying_bridge_feature", "flying_bridge",
                fromSlotId, detachedSlotId, fromCell, toCell, new int[]{summit.index, summit.index});
        return new FlyingBridgeFeature(chosen, archetype, template, detachedSlotId, detachedBounds,
                new int[]{offX, offZ}, bearing, span, shape, bridge);
    }

    private static Cell edgeFacingRect(Cell toward, Rect bounds) {
        int ex = Math.min(bounds.x2(), Math.max(bounds.x0, toward.x));
        int ez = Math.min(bounds.z1, Math.max(bounds.z0, toward.z));
        return new Cell(ex, ez);
    }

    private static String featureChoice(long seed) {
        long roll = Math.floorMod(seed, FEATURE_PERIOD);
        if (roll == FEATURE_PERIOD - 1) return null;
        String[] names = featureVariantNames();
        return names[(int) (roll % names.length)];
    }

    /**
     * Resolve the feature variant. {@code override} null = per-seed selection;
     * "none" = no feature; otherwise a specific variant name (force-generate).
     */
    private static String featureChoiceWithOverride(long seed, String override) {
        if (override == null) {
            return featureChoice(seed);
        }
        if (override.equalsIgnoreCase("none")) {
            return null;
        }
        for (String name : featureVariantNames()) {
            if (name.equalsIgnoreCase(override)) {
                return name;
            }
        }
        return featureChoice(seed);
    }

    static String[] featureVariantNames() {
        return new String[]{
                "pavilion_short_straight_east",
                "pagoda_long_arched_west",
                "disciple_medium_angled_north",
        };
    }

    private static String[] featureSpec(String variant) {
        switch (variant) {
            case "pavilion_short_straight_east":
                return new String[]{"pavilion", "12", "0", "E", "6", "straight"};
            case "pagoda_long_arched_west":
                return new String[]{"pagoda", "-14", "-2", "W", "10", "arched"};
            case "disciple_medium_angled_north":
                return new String[]{"disciple_quarters", "4", "14", "N", "8", "angled"};
            default:
                return new String[]{"pavilion", "12", "0", "E", "6", "straight"};
        }
    }

    // --- template registry (mirrors sect.py TEMPLATE_VARIANTS / TEMPLATE_FOOTPRINT) ---

    private static String templateFor(String archetype, long seed, int terraceIndex) {
        String[] variants = variantsOf(archetype);
        long idx = Math.floorMod(seed ^ (terreIndexHash(terraceIndex)), variants.length);
        return variants[(int) idx];
    }

    private static long terreIndexHash(int terraceIndex) {
        return (long) terraceIndex * 341873128712L;
    }

    private static String[] variantsOf(String base) {
        switch (base) {
            case "sect_gate" -> { return new String[]{"sect_gate_001", "sect_gate_002"}; }
            case "sect_main_hall" -> { return new String[]{"sect_main_hall_001", "sect_main_hall_002"}; }
            case "scripture_pavilion" -> { return new String[]{"scripture_pavilion_001", "scripture_pavilion_002"}; }
            case "alchemy_room" -> { return new String[]{"alchemy_room_001", "alchemy_room_002"}; }
            case "disciple_quarters" -> { return new String[]{"disciple_quarters_001", "disciple_quarters_002"}; }
            case "pagoda" -> { return new String[]{"pagoda_001", "pagoda_002", "pagoda_003"}; }
            case "pavilion" -> { return new String[]{"pavilion_001", "pavilion_002", "pavilion_003"}; }
            case "bell_drum_tower" -> { return new String[]{"bell_drum_tower_001", "bell_drum_tower_002", "bell_drum_tower_003"}; }
            default -> { return new String[]{base}; }
        }
    }

    private static int archetypeImportance(String archetype) {
        return switch (archetype) {
            case "sect_main_hall", "pagoda" -> 3;
            case "scripture_pavilion", "pavilion" -> 2;
            case "disciple_quarters", "alchemy_room", "bell_drum_tower" -> 1;
            default -> 0;
        };
    }

    private static int[] maxFootprint(String archetype) {
        String[] variants = variantsOf(archetype);
        int maxW = 0;
        int maxD = 0;
        for (String v : variants) {
            int[] fp = templateFootprint(v);
            maxW = Math.max(maxW, fp[0]);
            maxD = Math.max(maxD, fp[1]);
        }
        return new int[]{maxW, maxD};
    }

    private static int[] templateFootprint(String id) {
        return switch (id) {
            case "sect_gate", "sect_gate_001", "sect_gate_002" -> new int[]{21, 16};
            case "sect_main_hall", "sect_main_hall_001" -> new int[]{27, 25};
            case "sect_main_hall_002" -> new int[]{25, 25};
            case "scripture_pavilion", "scripture_pavilion_001", "scripture_pavilion_002" -> new int[]{17, 19};
            case "alchemy_room", "alchemy_room_001", "alchemy_room_002" -> new int[]{19, 17};
            case "disciple_quarters", "disciple_quarters_001", "disciple_quarters_002" -> new int[]{21, 18};
            case "pagoda", "pagoda_001" -> new int[]{17, 19};
            case "pagoda_002", "pagoda_003" -> new int[]{19, 21};
            case "pavilion", "pavilion_001", "pavilion_003" -> new int[]{23, 21};
            case "pavilion_002" -> new int[]{21, 21};
            case "bell_drum_tower", "bell_drum_tower_001", "bell_drum_tower_003" -> new int[]{17, 19};
            case "bell_drum_tower_002" -> new int[]{17, 21};
            default -> new int[]{15, 15};
        };
    }

    private static Cell edgeFacing(Slot slot, Cell toward) {
        return edgeFacingRect(toward, slot.bounds);
    }

    private static Slot slotByRole(List<Slot> slots, int terraceIndex, String role) {
        for (Slot s : slots) if (s.terraceIndex == terraceIndex && s.role.equals(role)) return s;
        return null;
    }

    // --- validation (mirrors validate_sect_plan) ----------------------------

    private static List<String> validatePlan(SectPlan plan) {
        List<String> errors = new ArrayList<>();
        if (plan.terraces.isEmpty()) {
            errors.add("missing_terraces");
            return errors;
        }
        int prev = Integer.MIN_VALUE;
        for (Terrace t : plan.terraces) {
            if (t.elevation <= prev) errors.add("terrace_not_ascending:" + t.index);
            prev = t.elevation;
        }
        Terrace gate = plan.terraces.get(0);
        Terrace summit = plan.terraces.get(plan.terraces.size() - 1);
        if (!gate.name.equals("gate")) errors.add("foot_terrace_not_gate:" + gate.name);
        if (!summit.name.equals("summit")) errors.add("top_terrace_not_summit:" + summit.name);
        if (!summit.cliffBack) errors.add("summit_missing_cliff_back");
        if (plan.axisCells.isEmpty()) errors.add("missing_axis");
        if (!intersects(plan.axisCells, gate.bounds)) errors.add("axis_not_on_gate_terrace");
        if (!intersects(plan.axisCells, summit.bounds)) errors.add("axis_not_on_summit_terrace");
        if (plan.retaining.size() != plan.terraces.size() - 1) errors.add("retaining_face_count_mismatch");
        if (plan.stairs.size() != plan.terraces.size() - 1) errors.add("axis_stair_count_mismatch");

        // importance non-decreasing up; hall at top tier
        int maxTier = -1;
        Slot hall = null;
        for (Terrace t : plan.terraces) {
            int floor = Integer.MAX_VALUE;
            int terr = Integer.MIN_VALUE;
            for (Slot s : plan.slots) {
                if (s.terraceIndex != t.index) continue;
                floor = Math.min(floor, s.importanceTier);
                terr = Math.max(terr, s.importanceTier);
                if (s.archetype.equals("sect_main_hall")) hall = s;
            }
            if (floor != Integer.MAX_VALUE) {
                if (floor < maxTier) errors.add("importance_decreases_at_terrace:" + t.name);
                maxTier = Math.max(maxTier, terr);
            }
        }
        if (hall == null) errors.add("missing_principal_hall");
        else if (hall.importanceTier < MAX_IMPORTANCE_TIER) errors.add("principal_hall_not_top_tier");
        if (hall != null && !hall.againstCliffBack) errors.add("principal_hall_not_against_cliff_back");

        // slot overlap + inside terrace
        Map<Integer, Terrace> byIndex = new java.util.HashMap<>();
        for (Terrace t : plan.terraces) byIndex.put(t.index, t);
        for (int i = 0; i < plan.slots.size(); i++) {
            Slot a = plan.slots.get(i);
            Terrace t = byIndex.get(a.terraceIndex);
            if (t == null) {
                errors.add("slot_without_terrace:" + a.id);
                continue;
            }
            if (!t.bounds.contains(a.bounds)) errors.add("slot_outside_terrace:" + a.id);
            for (int j = i + 1; j < plan.slots.size(); j++) {
                Slot b = plan.slots.get(j);
                if (b.terraceIndex != a.terraceIndex) continue;
                if (a.bounds.overlaps(b.bounds)) errors.add("slot_overlap:" + a.id + ":" + b.id);
            }
        }

        // gallery + bridge endpoints on volumes/terraces
        List<GalleryLink> allLinks = new ArrayList<>(plan.galleries);
        if (plan.feature != null) allLinks.add(plan.feature.bridge);
        for (GalleryLink g : allLinks) {
            if (!endpointOn(g.fromCell, g.fromSlot, plan)) errors.add("link_from_endpoint_off_volume:" + g.id);
            if (!endpointOn(g.toCell, g.toSlot, plan)) errors.add("link_to_endpoint_off_volume:" + g.id);
        }
        return errors;
    }

    private static boolean endpointOn(Cell cell, String slotId, SectPlan plan) {
        if (slotId.equals("summit_terrace")) return plan.terraces.get(plan.terraces.size() - 1).bounds.contains(cell);
        for (Slot s : plan.slots) {
            if (s.id.equals(slotId)) return s.bounds.contains(cell);
        }
        if (plan.feature != null && plan.feature.detachedSlotId.equals(slotId)) {
            return plan.feature.detachedBounds.contains(cell);
        }
        return false;
    }

    private static boolean intersects(Set<Cell> cells, Rect bounds) {
        for (Cell c : cells) if (bounds.contains(c)) return true;
        return false;
    }

    // --- realization (shared command + worldgen) ----------------------------

    /** The terrace + axis + volume + gallery + feature realizer, sink-targeted. */
    static void realizeCompound(SectSink sink, SectPlan plan, RandomSource templateRandom,
                                long seed, BuildStats stats) {
        carveTerraces(sink, plan, stats);
        placeAxisStairs(sink, plan, stats);
        placeRetainingFaces(sink, plan, stats);
        placeCliffBack(sink, plan, stats);
        realizeSlots(sink, plan, templateRandom, seed, stats);
        placeCoveredGalleries(sink, plan, stats);
        realizeFeature(sink, plan, templateRandom, seed, stats);
    }

    /**
     * Carve and retain each terrace against the surface so platforms step the
     * slope with no sub-footprint air gap (no floating or buried terraces).
     */
    private static void carveTerraces(SectSink sink, SectPlan plan, BuildStats stats) {
        for (Terrace terrace : plan.terraces) {
            int floorY = terrace.elevation - 1;
            for (int x = terrace.bounds.x0; x <= terrace.bounds.x2(); x++) {
                for (int z = terrace.bounds.z0; z <= terrace.bounds.z1; z++) {
                    int natural = surfaceY(sink, plan.base, x, z);
                    // platform surface
                    place(sink, plan.base.offset(x, floorY, z), Blocks.STONE_BRICKS.defaultBlockState(), stats);
                    // fill down to natural ground so no air gap beneath the platform
                    for (int y = floorY - 1; y >= natural && y > floorY - 40; y--) {
                        place(sink, plan.base.offset(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), stats);
                    }
                    // carve headroom above (terrace reads as an open platform)
                    int topClear = Math.max(floorY + 4, natural + 1);
                    for (int y = floorY + 1; y <= topClear; y++) {
                        place(sink, plan.base.offset(x, y, z), Blocks.AIR.defaultBlockState(), stats);
                    }
                }
            }
        }
    }

    private static void placeAxisStairs(SectSink sink, SectPlan plan, BuildStats stats) {
        for (AxisStair stair : plan.stairs) {
            Terrace lower = plan.terraces.get(stair.lower);
            int rows = stair.bounds.z1 - stair.bounds.z0 + 1;
            for (int zi = 0; zi < rows; zi++) {
                int z = stair.bounds.z0 + zi;
                int stepY = lower.elevation + zi;   // each row rises one block toward the summit
                for (int x = stair.bounds.x0; x <= stair.bounds.x2(); x++) {
                    BlockPos at = plan.base.offset(x, stepY - 1, z);
                    BlockState stairState = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.NORTH)
                            .setValue(StairBlock.HALF, Half.BOTTOM);
                    place(sink, at, stairState, stats);
                    place(sink, at.above(), Blocks.AIR.defaultBlockState(), stats);
                    place(sink, at.above(2), Blocks.AIR.defaultBlockState(), stats);
                }
            }
        }
    }

    private static void placeRetainingFaces(SectSink sink, SectPlan plan, BuildStats stats) {
        for (RetainingFace r : plan.retaining) {
            Rect bounds = r.bounds;
            for (int z = bounds.z0; z <= bounds.z1; z++) {
                for (int h = 0; h < r.height; h++) {
                    int y = plan.terraces.get(r.upper).elevation - 1 - h;
                    for (int x = bounds.x0; x <= stairInnerMin(r, plan); x++) {
                        place(sink, plan.base.offset(x, y, z), Blocks.STONE_BRICK_WALL.defaultBlockState(), stats);
                    }
                    for (int x = stairInnerMax(r, plan); x <= bounds.x2(); x++) {
                        place(sink, plan.base.offset(x, y, z), Blocks.STONE_BRICK_WALL.defaultBlockState(), stats);
                    }
                }
            }
        }
    }

    private static int stairInnerMin(RetainingFace retaining, SectPlan plan) {
        AxisStair stair = findStair(plan, retaining.lower, retaining.upper);
        return stair == null ? retaining.bounds.x2() : stair.bounds.x0;
    }

    private static int stairInnerMax(RetainingFace retaining, SectPlan plan) {
        AxisStair stair = findStair(plan, retaining.lower, retaining.upper);
        return stair == null ? retaining.bounds.x0 : stair.bounds.x2();
    }

    private static AxisStair findStair(SectPlan plan, int lower, int upper) {
        for (AxisStair s : plan.stairs) if (s.lower == lower && s.upper == upper) return s;
        return null;
    }

    private static void placeCliffBack(SectSink sink, SectPlan plan, BuildStats stats) {
        Terrace summit = plan.terraces.get(plan.terraces.size() - 1);
        int backZ = summit.bounds.z1;
        int baseY = summit.elevation;
        // sheer stone face rising behind the summit's cliff-back edge
        for (int x = summit.bounds.x0; x <= summit.bounds.x2(); x++) {
            for (int h = 0; h < CLIFF_BACK_HEIGHT; h++) {
                place(sink, plan.base.offset(x, baseY + h, backZ),
                        Blocks.STONE.defaultBlockState(), stats);
            }
            // back the cliff with solid ground so the principal hall backs rock, not air
            int natural = surfaceY(sink, plan.base, x, backZ + 1);
            for (int y = baseY; y < natural; y++) {
                place(sink, plan.base.offset(x, y, backZ + 1), Blocks.STONE.defaultBlockState(), stats);
            }
        }
    }

    private static void realizeSlots(SectSink sink, SectPlan plan, RandomSource random, long seed, BuildStats stats) {
        for (Slot slot : plan.slots) {
            Terrace terrace = findTerrace(plan, slot.terraceIndex);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, slot.templateId);
            Optional<ModBlockFallback.LoadedTemplate> loaded = sink.loadTemplate(id);
            if (loaded.isEmpty()) {
                stats.skippedSlots++;
                stats.skippedSlotIds.add(slot.id);
                continue;
            }
            StructureTemplate template = loaded.get().template();
            Vec3i size = template.getSize();
            int tw = size.getX();
            int td = size.getZ();
            int originX = slot.bounds.x0;
            int originZ = slot.bounds.z0;
            int floorY = terrace.elevation;
            BlockPos origin = new BlockPos(plan.base.getX() + originX, floorY - 1 - TEMPLATE_GROUND_LAYER,
                    plan.base.getZ() + originZ);
            // clear air above the platform so the template places cleanly
            clearVolume(sink, origin.above(), tw, size.getY() + 2, td, stats);
            boolean placed = sink.placeTemplate(template, origin, random);
            if (placed) {
                stats.placedSlots++;
                stats.fallbackSubstitutions += loaded.get().substitutions();
            } else {
                stats.skippedSlots++;
                stats.skippedSlotIds.add(slot.id);
            }
        }
    }

    /**
     * Covered galleries (廊) as block-placed roofed walks between recorded
     * endpoints: floor + side posts + slab roof, leaving walking headroom.
     */
    private static void placeCoveredGalleries(SectSink sink, SectPlan plan, BuildStats stats) {
        for (GalleryLink g : plan.galleries) {
            if (!g.kind.equals("covered_gallery")) continue;
            Terrace terrace = findTerrace(plan, g.terraceIndices[0]);
            if (terrace == null) continue;
            int floorY = terrace.elevation - 1;
            List<Cell> line = bresenham(g.fromCell, g.toCell);
            for (int i = 0; i < line.size(); i++) {
                Cell c = line.get(i);
                BlockPos ground = plan.base.offset(c.x, floorY, c.z);
                place(sink, ground, Blocks.STONE_BRICKS.defaultBlockState(), stats);
                place(sink, ground.above(), Blocks.AIR.defaultBlockState(), stats);
                place(sink, ground.above(2), Blocks.AIR.defaultBlockState(), stats);
                place(sink, ground.above(3), Blocks.STONE_BRICK_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.TOP), stats);
                if (i % 3 == 0) {
                    place(sink, ground.above(), Blocks.OAK_FENCE.defaultBlockState(), stats);
                }
            }
            stats.galleriesPlaced++;
        }
    }

    /**
     * Detached-spire flying bridge: place the detached volume on the surface
     * (the derived spire top in worldgen, the live surface for the on-the-spot
     * command) and span a roofed bridge deck between summit and detached volume.
     */
    private static void realizeFeature(SectSink sink, SectPlan plan, RandomSource random, long seed, BuildStats stats) {
        if (plan.feature == null) return;
        FlyingBridgeFeature f = plan.feature;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, f.detachedTemplate);
        Optional<ModBlockFallback.LoadedTemplate> loaded = sink.loadTemplate(id);
        if (loaded.isPresent()) {
            StructureTemplate template = loaded.get().template();
            int anchorX = f.detachedBounds.x0;
            int anchorZ = f.detachedBounds.z0;
            int surface = surfaceY(sink, plan.base, anchorX, anchorZ);
            BlockPos origin = new BlockPos(plan.base.getX() + anchorX, surface - 1,
                    plan.base.getZ() + anchorZ);
            clearVolume(sink, origin.above(), template.getSize().getX(),
                    template.getSize().getY() + 2, template.getSize().getZ(), stats);
            sink.placeTemplate(template, origin, random);
            stats.placedSlots++;
            stats.fallbackSubstitutions += loaded.get().substitutions();
        }
        // flying bridge deck between endpoints, riding above the gap
        Terrace summit = plan.terraces.get(plan.terraces.size() - 1);
        int deckY = summit.elevation + 1;
        List<Cell> span = bresenham(f.bridge.fromCell, f.bridge.toCell);
        for (Cell c : span) {
            BlockPos deck = plan.base.offset(c.x, deckY, c.z);
            place(sink, deck, Blocks.DARK_OAK_PLANKS.defaultBlockState(), stats);
            place(sink, deck.above(), Blocks.AIR.defaultBlockState(), stats);
            place(sink, deck.below(), Blocks.OAK_FENCE.defaultBlockState(), stats);
            place(sink, deck.east(), Blocks.DARK_OAK_FENCE.defaultBlockState(), stats);
            place(sink, deck.west(), Blocks.DARK_OAK_FENCE.defaultBlockState(), stats);
        }
        stats.galleriesPlaced++;
    }

    private static Terrace findTerrace(SectPlan plan, int index) {
        for (Terrace t : plan.terraces) if (t.index == index) return t;
        return null;
    }

    private static List<Cell> bresenham(Cell a, Cell b) {
        List<Cell> out = new ArrayList<>();
        int x0 = a.x;
        int z0 = a.z;
        int x1 = b.x;
        int z1 = b.z;
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;
        int x = x0;
        int z = z0;
        while (true) {
            out.add(new Cell(x, z));
            if (x == x1 && z == z1) break;
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }
        return out;
    }

    // --- shared helpers -----------------------------------------------------

    private static int surfaceY(SectSink sink, BlockPos base, int localX, int localZ) {
        return sink.surfaceY(base.getX() + localX, base.getZ() + localZ);
    }

    private static void clearVolume(SectSink sink, BlockPos origin, int width, int height, int depth, BuildStats stats) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    place(sink, origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), stats);
                }
            }
        }
    }

    private static void place(SectSink sink, BlockPos pos, BlockState state, BuildStats stats) {
        sink.set(pos, state);
        stats.blocksPlaced++;
    }

    private static Set<Cell> rect(int x0, int z0, int x1, int z1) {
        Set<Cell> cells = new HashSet<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                cells.add(new Cell(x, z));
            }
        }
        return cells;
    }

    // --- command sink (live world) ------------------------------------------

    /**
     * Writes to a live {@link ServerLevel}. Surface height resolves from the
     * derived mountain when one is supplied (force-generate), else from the live
     * world heightmap (on-the-spot command).
     */
    private static final class ServerLevelSink implements SectSink {
        private final ServerLevel level;
        private final BlockPos base;
        private final SectMountain mountain;

        ServerLevelSink(ServerLevel level, BlockPos base, SectMountain mountain) {
            this.level = level;
            this.base = base;
            this.mountain = mountain;
        }

        @Override
        public void set(BlockPos pos, BlockState state) {
            level.setBlock(pos, state, BLOCK_FLAGS);
        }

        @Override
        public int surfaceY(int worldX, int worldZ) {
            if (mountain != null) {
                return mountain.height(worldX - base.getX(), worldZ - base.getZ());
            }
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ);
        }

        @Override
        public Optional<ModBlockFallback.LoadedTemplate> loadTemplate(ResourceLocation id) {
            return ModBlockFallback.loadTemplate(level, id);
        }

        @Override
        public boolean placeTemplate(StructureTemplate template, BlockPos origin, RandomSource random) {
            return template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), random, BLOCK_FLAGS);
        }
    }

    // --- records ------------------------------------------------------------

    private record Cell(int x, int z) {
    }

    record Rect(int x0, int z0, int x2, int z1) {
        int width() {
            return x2 - x0 + 1;
        }

        boolean contains(Cell c) {
            return c.x >= x0 && c.x <= x2 && c.z >= z0 && c.z <= z1;
        }

        boolean contains(Rect other) {
            return other.x0 >= x0 && other.x2 <= x2 && other.z0 >= z0 && other.z1 <= z1;
        }

        boolean overlaps(Rect other) {
            return x2 >= other.x0 && other.x2 >= x0 && z1 >= other.z0 && other.z1 >= z0;
        }
    }

    record Terrace(int index, String name, int elevation, Rect bounds, int width, int depth,
                   boolean cliffBack) {
    }

    private record Slot(String id, int terraceIndex, String terraceName, String role, String archetype,
                        String templateId, int importanceTier, Rect bounds, boolean againstCliffBack) {
        Cell center() {
            return new Cell((bounds.x0 + bounds.x2()) / 2, (bounds.z0 + bounds.z1) / 2);
        }
    }

    private record GalleryLink(String id, String kind, String fromSlot, String toSlot,
                               Cell fromCell, Cell toCell, int[] terraceIndices) {
    }

    private record RetainingFace(String id, int lower, int upper, Rect bounds, int height) {
    }

    private record AxisStair(String id, int lower, int upper, Rect bounds) {
    }

    private record FlyingBridgeFeature(String variant, String detachedArchetype, String detachedTemplate,
                                       String detachedSlotId, Rect detachedBounds, int[] spireOffset,
                                       String bearing, int bridgeSpan, String bridgeShape,
                                       GalleryLink bridge) {
    }

    record SectPlan(BlockPos base, List<Terrace> terraces, Set<Cell> axisCells, List<Slot> slots,
                    List<GalleryLink> galleries, List<RetainingFace> retaining,
                    List<AxisStair> stairs, FlyingBridgeFeature feature) {
    }

    static final class BuildStats {
        int placedSlots;
        int skippedSlots;
        int galleriesPlaced;
        int blocksPlaced;
        int fallbackSubstitutions;
        final List<String> skippedSlotIds = new ArrayList<>();
    }
}
