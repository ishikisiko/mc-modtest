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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * planner in tools/buildgen/sect.py: a gate / disciple / assembly / scripture /
 * summit terrace stack symmetric about one fall-line ritual axis; a paved axis
 * corridor (御道) from a levelled forecourt through a passage in the gate building
 * up to the principal hall; grand stairs with a landing and cheek walls between
 * terraces, cut into solid stone-brick retaining bands; mirrored flank buildings
 * beside the axis; a cliff-backed summit; and an optional detached-spire
 * flying-bridge feature (built only where it clears the compound). Block writes
 * route through a {@link SectSink} so the same plan and realizer serve both the
 * on-the-spot {@code /myvillage sect} command and worldgen
 * ({@link SectStructurePiece}). For worldgen / force-generate the mountain is
 * first derived from the terrace profile (反推山形, {@link SectMountain}) and the
 * realizer rests the compound on it; the on-the-spot command rests it on the live
 * world surface.
 *
 * Geometry mirrors sect.py with no shared RNG: every cell derives from seed xor
 * coordinates, so the same seed + site yields the same compound (Python/Java
 * parity is asserted by validate_sect_generation.py).
 */
public final class SectGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SectGenerator.class);

    static final int TERRACE_COUNT = 5;
    static final int TERRACE_RISE = 8;
    static final int TERRACE_DEPTH = 28;
    /** Gate-terrace width; the stack is symmetric about {@link #AXIS_X} and narrows 2 per terrace. */
    static final int TERRACE_WIDTH = 59;
    /** Total narrowing from the gate terrace to the summit (59/57/55/53/51). */
    static final int SUMMIT_TAPER = 8;
    static final int CLIFF_BACK_HEIGHT = 12;
    static final int Z_MARGIN = 4;

    static final int SITE_WIDTH = 64;
    static final int SITE_DEPTH = 2 * Z_MARGIN + TERRACE_COUNT * TERRACE_DEPTH
            + (TERRACE_COUNT - 1) * TERRACE_RISE;

    /** Local x of the ritual axis; every terrace, the corridor and the stairs are symmetric about it. */
    static final int AXIS_X = 31;
    /** Width of the paved axis corridor (御道), x 28..34. */
    static final int AXIS_W = 7;
    /** Width of the grand stair treads between terraces, x 27..35 (cheek walls at x 26 and x 36). */
    static final int STAIR_W = 9;
    /** Rows the grand stair projects forward onto the lower terrace. */
    static final int STAIR_PROJECT = 3;
    /** Inner (axis-side) edge of the left flank buildings. */
    static final int FLANK_INNER_LEFT_X1 = 25;
    /** Inner (axis-side) edge of the right flank buildings. */
    static final int FLANK_INNER_RIGHT_X0 = 37;
    /** Rows of the levelled forecourt in front of the gate terrace, z -12..-1 relative to it (z -8..3). */
    static final int APRON_ROWS = 12;
    /** Width of the forecourt, x 21..41 (the gate building's width). */
    static final int APRON_W = 21;
    /** Width of the through-passage cut along the axis through the gate building, x 30..32. */
    static final int GATE_PASSAGE_W = 3;
    /**
     * Air rows of the gate passage above its floor: the door rows and one above, which keeps the
     * gate's two-row hanging plaque (one row higher) intact.
     */
    static final int GATE_PASSAGE_H = 3;
    /** Spacing in x of the pilasters on each retaining face, counted out from {@link #AXIS_X}. */
    static final int PILASTER_SPACING = 8;
    /** Air rows the final corridor pass guarantees above the corridor floor. */
    static final int CORRIDOR_HEADROOM = 5;
    /** Air rows kept above every stair tread and landing. */
    static final int STAIR_HEADROOM = 4;

    static final int AXIS_X0 = AXIS_X - AXIS_W / 2;
    static final int AXIS_X1 = AXIS_X + AXIS_W / 2;
    static final int STAIR_X0 = AXIS_X - STAIR_W / 2;
    static final int STAIR_X1 = AXIS_X + STAIR_W / 2;
    static final int APRON_X0 = AXIS_X - APRON_W / 2;
    static final int APRON_X1 = AXIS_X + APRON_W / 2;
    static final int APRON_Z0 = Z_MARGIN - APRON_ROWS;
    static final int APRON_Z1 = Z_MARGIN - 1;

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
        ServerPlayer player = source.getPlayerOrException();
        return generateAt(source, seed, player.blockPosition());
    }

    /** On-the-spot build at an explicit anchor; rests the compound on the live world surface. */
    public static int generateAt(CommandSourceStack source, long seed, BlockPos anchor) {
        return buildAt(source, seed, null, false, anchor);
    }

    /**
     * Force-generate a worldgen-style sect (with its derived mountain) at the
     * player, optionally forcing a detached-spire variant ("none" / a variant
     * name / null = per-seed selection).
     */
    public static int generateForced(CommandSourceStack source, long seed, String featureOverride)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return generateForcedAt(source, seed, featureOverride, player.blockPosition());
    }

    public static int generateForcedAt(CommandSourceStack source, long seed, String featureOverride,
                                       BlockPos anchor) {
        return buildAt(source, seed, featureOverride, true, anchor);
    }

    private static int buildAt(CommandSourceStack source, long seed, String featureOverride,
                               boolean worldgenStyle, BlockPos anchor) {
        ServerLevel level = source.getLevel();
        BlockPos base = anchor.offset(-SITE_WIDTH / 2, 0, -SITE_DEPTH / 2);

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
                realizeCompound(sink, plan, templateRandom, seed, stats);
            } else {
                // the forecourt lies in front of the site (negative local z)
                forceLoadFootprint(level, base.offset(0, 0, APRON_Z0), SITE_WIDTH, SITE_DEPTH - APRON_Z0,
                        forcedChunks, loadFailures);
                ServerLevelSink sink = new ServerLevelSink(level, base, null);
                realizeCompound(sink, plan, templateRandom, seed, stats);
            }
        } finally {
            for (ChunkPos c : forcedChunks) {
                level.setChunkForced(c.x, c.z, false);
            }
        }

        if (stats.featuresSkipped > 0) {
            LOGGER.info("Sect seed={}: detached spire {} not built, its bounds overlap the compound",
                    seed, plan.feature.variant);
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
                        + " feature=" + (plan.feature == null ? "none"
                                : plan.feature.variant + (stats.featuresSkipped > 0 ? " (skipped)" : ""))
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
        // the solitary peak only rises under a spire that is actually built
        if (featureBuildable(plan)) {
            Rect d = plan.feature.detachedBounds;
            detached = new int[]{d.x0, d.z0, d.x2(), d.z1};
        }
        Rect a = plan.apron;
        return SectMountain.derive(seed, boxes, TERRACE_RISE, CLIFF_BACK_HEIGHT, detached,
                new int[]{a.x0, a.z0, a.x2(), a.z1}, natural);
    }

    /** Top-block material of a skirt column for a {@link SectMountain#surfaceVariant}. */
    private static BlockState skirtSurface(int variant) {
        return switch (variant) {
            case 1 -> Blocks.ANDESITE.defaultBlockState();
            case 2 -> Blocks.TUFF.defaultBlockState();
            case 3 -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }

    /**
     * Bake the derived mountain as solid stone: each footprint+skirt column is
     * filled from the natural surface up to the derived height, and burying
     * terrain above the derived silhouette is cleared. Cliff-back and spire
     * pillars come for free from {@link SectMountain#height}. The top block of a
     * skirt column is stone, andesite, tuff or cobbled deepslate by a hash
     * (mostly stone); everything below it stays stone.
     */
    static void writeMountain(SectSink sink, SectPlan plan, SectMountain m, BuildStats stats) {
        int minX = m.coreX0() - MOUNTAIN_MARGIN;
        int maxX = m.coreX1() + MOUNTAIN_MARGIN;
        int minZ = m.coreZ0() - MOUNTAIN_MARGIN;
        int maxZ = m.coreZ1() + MOUNTAIN_MARGIN;
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        Clip clip = sink.clip();
        int bx = plan.base.getX();
        int bz = plan.base.getZ();
        for (int x = clipLo(minX, clip.x0(), bx); x <= clipHi(maxX, clip.x1(), bx); x++) {
            for (int z = clipLo(minZ, clip.z0(), bz); z <= clipHi(maxZ, clip.z1(), bz); z++) {
                int top = m.height(x, z);
                int nat = m.naturalAt(x, z);
                BlockState surface = m.isSkirt(x, z) ? skirtSurface(m.surfaceVariant(x, z)) : stone;
                for (int y = Math.min(nat, top); y <= top; y++) {
                    place(sink, at(plan.base, x, y, z), y == top ? surface : stone, stats);
                }
                // clear terrain that would bury the derived silhouette
                for (int y = top + 1; y <= nat; y++) {
                    place(sink, at(plan.base, x, y, z), air, stats);
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
        // plan() is a pure function of (seed, base, featureOverride), yet worldgen
        // rebuilds it for every chunk of a site (~120× per site) because each
        // postProcess is independent. Memo the common no-override case in a single
        // volatile entry: within a site the same (seed, base) stays hot; switching
        // sites evicts once. The force-generate command path (non-null override) is
        // rare/manual and bypasses the cache so an override can't leak into a
        // later worldgen call. Safe under parallel chunk-gen threads: reads are a
        // volatile load + identity compare; a same-site race computes the identical
        // immutable plan twice and one write wins, a cross-site race just evicts
        // (the loser recomputes on its next call).
        if (featureOverride == null) {
            PlanCacheEntry hit = PLAN_CACHE;
            if (hit != null && hit.seed == seed && hit.base.equals(base)) {
                return hit.plan;
            }
        }
        SectPlan result = computePlan(seed, base, featureOverride);
        if (featureOverride == null) {
            PLAN_CACHE = new PlanCacheEntry(seed, base, result);
        }
        return result;
    }

    private record PlanCacheEntry(long seed, BlockPos base, SectPlan plan) {
    }

    private static volatile PlanCacheEntry PLAN_CACHE;

    private static SectPlan computePlan(long seed, BlockPos base, String featureOverride) {
        int count = TERRACE_COUNT;
        String[] names = skeletonNames(count);
        int xAnchor = (SITE_WIDTH - TERRACE_WIDTH) / 2;

        List<Terrace> terraces = new ArrayList<>();
        int z = Z_MARGIN;
        for (int i = 0; i < count; i++) {
            // per-side inset, so every terrace stays centred on AXIS_X
            int inset = Math.floorDiv(SUMMIT_TAPER * i, 2 * (count - 1));
            int width = TERRACE_WIDTH - 2 * inset;
            int x0 = xAnchor + inset;
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
            Rect onAxis = null;
            // the on-axis building first, so the flanks can stand clear of it
            for (SlotSpec spec : specs) {
                if (!spec.role.equals("on_axis")) continue;
                String template = templateFor(spec.archetype, seed, terrace.index);
                onAxis = slotBounds(terrace, spec, templateFootprint(template), null);
                slots.add(slot(terrace, spec, template, onAxis));
            }
            for (SlotSpec spec : specs) {
                if (spec.role.equals("on_axis")) continue;
                String template = templateFor(spec.archetype, seed, terrace.index);
                slots.add(slot(terrace, spec, template,
                        slotBounds(terrace, spec, templateFootprint(template), onAxis)));
            }
        }

        Terrace first = terraces.get(0);
        Terrace last = terraces.get(terraces.size() - 1);
        Set<Cell> axisCells = rect(AXIS_X0, APRON_Z0, AXIS_X1, corridorEndZ(last));

        List<RetainingFace> retaining = new ArrayList<>();
        List<AxisStair> stairs = new ArrayList<>();
        for (int i = 0; i < terraces.size() - 1; i++) {
            Terrace lower = terraces.get(i);
            Terrace upper = terraces.get(i + 1);
            int bandZ0 = upper.bounds.z0 - TERRACE_RISE;
            int bandZ1 = upper.bounds.z0 - 1;
            stairs.add(new AxisStair("stair_" + i + "_" + (i + 1), i, i + 1,
                    new Rect(STAIR_X0, bandZ0 - STAIR_PROJECT, STAIR_X1, bandZ1), lower.elevation - 1));
            retaining.add(new RetainingFace("retain_" + i + "_" + (i + 1), i, i + 1,
                    new Rect(upper.bounds.x0, bandZ0, upper.bounds.x2(), bandZ1), TERRACE_RISE));
        }
        Rect apron = new Rect(APRON_X0, APRON_Z0, APRON_X1, APRON_Z1);

        FlyingBridgeFeature feature = buildFeature(seed, terraces, slots, featureOverride);

        return new SectPlan(base, terraces, axisCells, slots, List.of(), retaining, stairs, apron, feature);
    }

    /** Last corridor row: the row in front of the principal hall (the summit's first row + 2). */
    private static int corridorEndZ(Terrace summit) {
        return summit.bounds.z0 + 2;
    }

    private static Slot slot(Terrace terrace, SlotSpec spec, String template, Rect bounds) {
        return new Slot(
                "slot_" + terrace.name + "_" + spec.role + "_" + terrace.index,
                terrace.index, terrace.name, spec.role, spec.archetype, template,
                archetypeImportance(spec.archetype), bounds,
                terrace.name.equals("summit") && spec.role.equals("on_axis"));
    }

    private record SlotSpec(String archetype, String role, String align) {
    }

    /**
     * Buildings per terrace. Only the gate and the principal hall stand on the axis; everything
     * else is a mirrored pair beside it.
     */
    private static SlotSpec[] slotRoster(String terraceName) {
        switch (terraceName) {
            case "gate" -> {
                return new SlotSpec[]{
                        new SlotSpec("sect_gate", "on_axis", "front"),
                        new SlotSpec("bell_drum_tower", "flank_left", "back"),
                        new SlotSpec("bell_drum_tower", "flank_right", "back"),
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
                        new SlotSpec("scripture_pavilion", "flank_left", "center"),
                        new SlotSpec("scripture_pavilion", "flank_right", "center"),
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

    /**
     * Slot rectangle sized by the slot's actual template. On-axis buildings are centred on
     * {@link #AXIS_X}; flanks keep their inner edge at {@link #FLANK_INNER_LEFT_X1} /
     * {@link #FLANK_INNER_RIGHT_X0}, or one block clear of the terrace's on-axis building when that
     * reaches further out, so the pair mirrors about the axis.
     */
    private static Rect slotBounds(Terrace terrace, SlotSpec spec, int[] footprint, Rect onAxis) {
        int x0 = terrace.bounds.x0;
        int x1 = terrace.bounds.x2();
        int z0 = terrace.bounds.z0;
        int z1 = terrace.bounds.z1();
        int tw = Math.min(footprint[0], x1 - x0 + 1);
        int td = Math.min(footprint[1], z1 - z0 + 1);
        int sx0;
        int sx1;
        if (spec.role.equals("on_axis")) {
            sx0 = AXIS_X - tw / 2;
            sx1 = sx0 + tw - 1;
        } else if (spec.role.equals("flank_left")) {
            sx1 = onAxis == null ? FLANK_INNER_LEFT_X1 : Math.min(FLANK_INNER_LEFT_X1, onAxis.x0 - 1);
            sx0 = sx1 - tw + 1;
        } else {
            sx0 = onAxis == null ? FLANK_INNER_RIGHT_X0 : Math.max(FLANK_INNER_RIGHT_X0, onAxis.x2() + 1);
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

    static int[] templateFootprint(String id) {
        return switch (id) {
            case "sect_gate", "sect_gate_001", "sect_gate_002" -> new int[]{21, 16};
            case "sect_main_hall", "sect_main_hall_001" -> new int[]{27, 25};
            case "sect_main_hall_002" -> new int[]{25, 25};
            case "scripture_pavilion", "scripture_pavilion_001", "scripture_pavilion_002" -> new int[]{17, 19};
            case "alchemy_room", "alchemy_room_001", "alchemy_room_002" -> new int[]{19, 17};
            case "disciple_quarters", "disciple_quarters_001", "disciple_quarters_002" -> new int[]{21, 18};
            case "pagoda" -> new int[]{27, 29};
            case "pagoda_001" -> new int[]{19, 21};
            case "pagoda_002" -> new int[]{27, 29};
            case "pagoda_003" -> new int[]{23, 25};
            case "pavilion", "pavilion_001", "pavilion_003" -> new int[]{23, 21};
            case "pavilion_002" -> new int[]{21, 21};
            case "bell_drum_tower", "bell_drum_tower_001", "bell_drum_tower_003" -> new int[]{17, 19};
            case "bell_drum_tower_002" -> new int[]{17, 21};
            default -> new int[]{15, 15};
        };
    }

    private static Slot slotByRole(List<Slot> slots, int terraceIndex, String role) {
        for (Slot s : slots) if (s.terraceIndex == terraceIndex && s.role.equals(role)) return s;
        return null;
    }

    // --- validation (mirrors validate_sect_plan) ----------------------------

    static List<String> validatePlan(SectPlan plan) {
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

        // only the gate and the principal hall stand on the axis; no building on a stair
        Rect corridor = new Rect(AXIS_X0, APRON_Z0, AXIS_X1, corridorEndZ(summit));
        for (Slot s : plan.slots) {
            boolean axial = s.role.equals("on_axis")
                    && (s.archetype.equals("sect_gate") || s.archetype.equals("sect_main_hall"));
            if (!axial && s.bounds.overlaps(corridor)) errors.add("slot_on_axis_corridor:" + s.id);
            for (AxisStair st : plan.stairs) {
                if (s.bounds.overlaps(st.withCheeks())) errors.add("slot_on_stair:" + s.id + ":" + st.id);
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

    /**
     * The terrace + stair + volume + feature realizer, sink-targeted. Order matters: the grand
     * stairs are the last terrace element (nothing later writes onto a tread or its headroom except
     * the corridor pass, which only adds air), buildings come after the terraces, the gate passage
     * is cut through the gate building after it is placed, and {@link #clearAxisCorridor} runs last.
     */
    static void realizeCompound(SectSink sink, SectPlan plan, RandomSource templateRandom,
                                long seed, BuildStats stats) {
        carveTerraces(sink, plan, stats);
        levelApron(sink, plan, stats);
        fillBands(sink, plan, stats);
        placeGrandStairs(sink, plan, stats);
        placeCliffBack(sink, plan, stats);
        realizeSlots(sink, plan, templateRandom, seed, stats);
        cutGatePassage(sink, plan, stats);
        realizeFeature(sink, plan, templateRandom, seed, stats);
        clearAxisCorridor(sink, plan, stats);
    }

    /**
     * Carve and retain each terrace against the surface so platforms step the
     * slope with no sub-footprint air gap (no floating or buried terraces). The
     * axis corridor is paved in polished andesite with a chiseled centre line.
     */
    private static void carveTerraces(SectSink sink, SectPlan plan, BuildStats stats) {
        Clip clip = sink.clip();
        int bx = plan.base.getX();
        int bz = plan.base.getZ();
        int corridorEnd = corridorEndZ(plan.terraces.get(plan.terraces.size() - 1));
        for (Terrace terrace : plan.terraces) {
            int floorY = terrace.elevation - 1;
            for (int x = clipLo(terrace.bounds.x0, clip.x0(), bx); x <= clipHi(terrace.bounds.x2(), clip.x1(), bx); x++) {
                for (int z = clipLo(terrace.bounds.z0, clip.z0(), bz); z <= clipHi(terrace.bounds.z1, clip.z1(), bz); z++) {
                    BlockState floor = z <= corridorEnd ? groundBlock(x) : Blocks.STONE_BRICKS.defaultBlockState();
                    groundColumn(sink, plan.base, x, z, floorY, floor, 4, stats);
                }
            }
        }
    }

    /** Floor block at local x: the corridor paving on the axis, stone bricks elsewhere. */
    private static BlockState groundBlock(int x) {
        if (x == AXIS_X) return Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        if (x >= AXIS_X0 && x <= AXIS_X1) return Blocks.POLISHED_ANDESITE.defaultBlockState();
        return Blocks.STONE_BRICKS.defaultBlockState();
    }

    /**
     * One platform column: {@code floor} at {@code floorY}, stone bricks down to the natural
     * ground (so nothing floats over a hollow), and at least {@code headroom} blocks of air above
     * (more where natural ground stood higher, so the platform reads as an open level).
     */
    private static void groundColumn(SectSink sink, BlockPos base, int x, int z, int floorY, BlockState floor,
                                     int headroom, BuildStats stats) {
        int natural = surfaceY(sink, base, x, z);
        place(sink, at(base, x, floorY, z), floor, stats);
        for (int y = floorY - 1; y >= natural && y > floorY - 40; y--) {
            place(sink, at(base, x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), stats);
        }
        int topClear = Math.max(floorY + headroom, natural + 1);
        for (int y = floorY + 1; y <= topClear; y++) {
            place(sink, at(base, x, y, z), Blocks.AIR.defaultBlockState(), stats);
        }
    }

    /** The forecourt in front of the gate: level with the gate terrace's floor, paved on the axis. */
    private static void levelApron(SectSink sink, SectPlan plan, BuildStats stats) {
        Clip clip = sink.clip();
        int bx = plan.base.getX();
        int bz = plan.base.getZ();
        Rect r = plan.apron;
        int floorY = plan.terraces.get(0).elevation - 1;
        for (int x = clipLo(r.x0, clip.x0(), bx); x <= clipHi(r.x2(), clip.x1(), bx); x++) {
            for (int z = clipLo(r.z0, clip.z0(), bz); z <= clipHi(r.z1, clip.z1(), bz); z++) {
                groundColumn(sink, plan.base, x, z, floorY, groundBlock(x), CORRIDOR_HEADROOM, stats);
            }
        }
    }

    /**
     * The band between two terraces (the 8 rows in front of the upper one) is solid ground up to
     * the upper floor: stone inside, a stone-brick face toward the lower terrace with a chiseled
     * coping course, stone-brick sides. Band cells inside the lower terrace's width but outside the
     * upper one's (the one-block taper strip) are lower floor. One-deep stone-brick pilasters with a
     * chiseled top stand against the face on the lower terrace's last row ({@link #pilasterXs}).
     * No wall blocks.
     */
    private static void fillBands(SectSink sink, SectPlan plan, BuildStats stats) {
        Clip clip = sink.clip();
        int bx = plan.base.getX();
        int bz = plan.base.getZ();
        for (RetainingFace r : plan.retaining) {
            Terrace lower = plan.terraces.get(r.lower);
            Terrace upper = plan.terraces.get(r.upper);
            int lowY = lower.elevation - 1;
            int topY = upper.elevation - 1;
            for (int x = clipLo(lower.bounds.x0, clip.x0(), bx); x <= clipHi(lower.bounds.x2(), clip.x1(), bx); x++) {
                boolean inUpper = x >= upper.bounds.x0 && x <= upper.bounds.x2();
                boolean side = x == upper.bounds.x0 || x == upper.bounds.x2();
                for (int z = clipLo(r.bounds.z0, clip.z0(), bz); z <= clipHi(r.bounds.z1, clip.z1(), bz); z++) {
                    if (!inUpper) {
                        groundColumn(sink, plan.base, x, z, lowY, Blocks.STONE_BRICKS.defaultBlockState(), 4, stats);
                        continue;
                    }
                    boolean face = z == r.bounds.z0;
                    groundColumn(sink, plan.base, x, z, topY,
                            face ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
                                    : Blocks.STONE_BRICKS.defaultBlockState(), 4, stats);
                    // the exposed shell is stone brick, the core plain stone
                    BlockState body = face || side ? Blocks.STONE_BRICKS.defaultBlockState()
                            : Blocks.STONE.defaultBlockState();
                    for (int y = topY - 1; y > lowY; y--) {
                        place(sink, at(plan.base, x, y, z), body, stats);
                    }
                }
            }
            // pilasters standing on the lower floor against the face, full height, chiseled top
            int pz = r.bounds.z0 - 1;
            if (bz + pz < clip.z0() || bz + pz > clip.z1()) continue;
            for (int x : pilasterXs(plan, r)) {
                if (bx + x < clip.x0() || bx + x > clip.x1()) continue;
                for (int y = lowY + 1; y < topY; y++) {
                    place(sink, at(plan.base, x, y, pz), Blocks.STONE_BRICKS.defaultBlockState(), stats);
                }
                place(sink, at(plan.base, x, topY, pz), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), stats);
            }
        }
    }

    /**
     * Local x of the pilasters on a retaining face: every {@link #PILASTER_SPACING} blocks out from
     * {@link #AXIS_X} on both sides, clear of the grand stair and its cheeks (x 26..36), inside the
     * upper terrace's width short of its corners, and not where a building on the lower terrace
     * stands against the face (its slot or template footprint covers the row in front of it).
     * Symmetric about the axis because the flanks mirror.
     */
    static List<Integer> pilasterXs(SectPlan plan, RetainingFace r) {
        Terrace upper = plan.terraces.get(r.upper);
        int pz = r.bounds.z0 - 1;
        List<Integer> out = new ArrayList<>();
        for (int off = PILASTER_SPACING; off <= SITE_WIDTH; off += PILASTER_SPACING) {
            if (off <= STAIR_W / 2 + 1) continue;
            for (int x : new int[]{AXIS_X - off, AXIS_X + off}) {
                if (x <= upper.bounds.x0 || x >= upper.bounds.x2()) continue;
                boolean covered = false;
                for (Slot s : plan.slots) {
                    if (s.terraceIndex != r.lower) continue;
                    int[] fp = templateFootprint(s.templateId);
                    int x0 = s.bounds.x0;
                    int x1 = Math.max(s.bounds.x2(), s.bounds.x0 + fp[0] - 1);
                    int z0 = s.bounds.z0;
                    int z1 = Math.max(s.bounds.z1, s.bounds.z0 + fp[1] - 1);
                    if (x >= x0 && x <= x1 && pz >= z0 && pz <= z1) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) out.add(x);
            }
        }
        out.sort(null);
        return out;
    }

    /**
     * Grand stair between two terraces, {@link #STAIR_W} wide on the axis: four south-facing rises
     * projecting {@link #STAIR_PROJECT} rows onto the lower terrace plus the band's first row, a
     * three-row landing, and four more rises to the upper floor. Every tread stands on solid stone
     * brick down to the lower floor with {@link #STAIR_HEADROOM} air above; cheek walls flank it one
     * block higher than the treads. Written after the bands so nothing overwrites it.
     */
    private static void placeGrandStairs(SectSink sink, SectPlan plan, BuildStats stats) {
        Clip clip = sink.clip();
        int bx = plan.base.getX();
        int bz = plan.base.getZ();
        BlockState tread = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH)
                .setValue(StairBlock.HALF, Half.BOTTOM);
        BlockState solid = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (AxisStair stair : plan.stairs) {
            Rect cheeks = stair.withCheeks();
            for (int z = clipLo(cheeks.z0, clip.z0(), bz); z <= clipHi(cheeks.z1, clip.z1(), bz); z++) {
                int treadY = stair.treadY(z);
                boolean landing = stair.isLanding(z);
                for (int x = clipLo(cheeks.x0, clip.x0(), bx); x <= clipHi(cheeks.x2(), clip.x1(), bx); x++) {
                    if (x == cheeks.x0 || x == cheeks.x2()) {
                        for (int y = stair.lowerFloorY; y <= treadY + 1; y++) {
                            place(sink, at(plan.base, x, y, z), solid, stats);
                        }
                        continue;
                    }
                    for (int y = stair.lowerFloorY; y < treadY; y++) {
                        place(sink, at(plan.base, x, y, z), solid, stats);
                    }
                    place(sink, at(plan.base, x, treadY, z), landing ? solid : tread, stats);
                    for (int y = treadY + 1; y <= treadY + STAIR_HEADROOM; y++) {
                        place(sink, at(plan.base, x, y, z), air, stats);
                    }
                }
            }
        }
    }

    private static void placeCliffBack(SectSink sink, SectPlan plan, BuildStats stats) {
        Terrace summit = plan.terraces.get(plan.terraces.size() - 1);
        int backZ = summit.bounds.z1;
        int baseY = summit.elevation;
        Clip clip = sink.clip();
        int bx = plan.base.getX();
        int bz = plan.base.getZ();
        boolean faceInClip = bz + backZ >= clip.z0() && bz + backZ <= clip.z1();
        boolean fillInClip = bz + backZ + 1 >= clip.z0() && bz + backZ + 1 <= clip.z1();
        if (!faceInClip && !fillInClip) {
            return;
        }
        // sheer stone face rising behind the summit's cliff-back edge
        for (int x = clipLo(summit.bounds.x0, clip.x0(), bx); x <= clipHi(summit.bounds.x2(), clip.x1(), bx); x++) {
            if (faceInClip) {
                for (int h = 0; h < CLIFF_BACK_HEIGHT; h++) {
                    place(sink, at(plan.base, x, baseY + h, backZ),
                            Blocks.STONE.defaultBlockState(), stats);
                }
            }
            // back the cliff with solid ground so the principal hall backs rock, not air
            if (fillInClip) {
                int natural = surfaceY(sink, plan.base, x, backZ + 1);
                for (int y = baseY; y < natural; y++) {
                    place(sink, at(plan.base, x, y, backZ + 1), Blocks.STONE.defaultBlockState(), stats);
                }
            }
        }
    }

    private static void realizeSlots(SectSink sink, SectPlan plan, RandomSource random, long seed, BuildStats stats) {
        Clip clip = sink.clip();
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
            // skip a building whose footprint lies wholly outside this chunk's
            // slice (worldgen) so we don't clear/place out-of-region volume; an
            // UNBOUNDED clip (command path) never skips.
            if (!clipHitsLocal(clip, plan.base, originX, originZ, originX + tw - 1, originZ + td - 1)) {
                continue;
            }
            int floorY = terrace.elevation;
            BlockPos origin = new BlockPos(plan.base.getX() + originX, floorY - 1 - TEMPLATE_GROUND_LAYER,
                    plan.base.getZ() + originZ);
            // clear air above the platform so the template places cleanly
            clearVolume(sink, origin.above(), tw, size.getY() + 2, td, stats);
            // seed placement from the stable sect site + this slot's origin (not
            // the chunk) so a building straddling a chunk seam rolls the same
            // variant/orientation in both halves.
            boolean placed = sink.placeTemplate(template, origin, slotRandom(seed, originX, originZ));
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
     * Cut a {@link #GATE_PASSAGE_W}-wide, {@link #GATE_PASSAGE_H}-high through-passage along the axis
     * through the gate building (doors, back wall and furniture go), pave its floor on the gate's
     * plinth top, and set a row of polished-andesite stairs in front of and behind it so the
     * plinth's one-block step is walkable.
     */
    private static void cutGatePassage(SectSink sink, SectPlan plan, BuildStats stats) {
        Slot gate = gateSlot(plan);
        if (gate == null) return;
        Clip clip = sink.clip();
        int bx = plan.base.getX();
        int bz = plan.base.getZ();
        int plinthY = plan.terraces.get(gate.terraceIndex).elevation;
        int px0 = AXIS_X - GATE_PASSAGE_W / 2;
        int px1 = AXIS_X + GATE_PASSAGE_W / 2;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = clipLo(px0, clip.x0(), bx); x <= clipHi(px1, clip.x1(), bx); x++) {
            for (int z = clipLo(gate.bounds.z0, clip.z0(), bz); z <= clipHi(gate.bounds.z1, clip.z1(), bz); z++) {
                place(sink, at(plan.base, x, plinthY, z), groundBlock(x), stats);
                for (int y = plinthY + 1; y <= plinthY + GATE_PASSAGE_H; y++) {
                    place(sink, at(plan.base, x, y, z), air, stats);
                }
            }
            int front = gate.bounds.z0 - 1;
            int back = gate.bounds.z1 + 1;
            if (bz + front >= clip.z0() && bz + front <= clip.z1()) {
                place(sink, at(plan.base, x, plinthY, front), passageStair(Direction.SOUTH), stats);
            }
            if (bz + back >= clip.z0() && bz + back <= clip.z1()) {
                place(sink, at(plan.base, x, plinthY, back), passageStair(Direction.NORTH), stats);
            }
        }
    }

    private static BlockState passageStair(Direction facing) {
        return Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, Half.BOTTOM);
    }

    static Slot gateSlot(SectPlan plan) {
        for (Slot s : plan.slots) {
            if (s.role.equals("on_axis") && s.archetype.equals("sect_gate")) return s;
        }
        return null;
    }

    /**
     * Local y of the walking surface block under the axis corridor at row z (x 28..34): the
     * forecourt or terrace floor, a stair tread or landing, or, at the gate passage's end stairs
     * (x 30..32 only), the stair block.
     */
    static int corridorFloorY(SectPlan plan, int x, int z) {
        Slot gate = gateSlot(plan);
        if (gate != null && Math.abs(x - AXIS_X) <= GATE_PASSAGE_W / 2) {
            if (z >= gate.bounds.z0 - 1 && z <= gate.bounds.z1 + 1) {
                return plan.terraces.get(gate.terraceIndex).elevation;
            }
        }
        for (AxisStair st : plan.stairs) {
            if (z >= st.bounds.z0 && z <= st.bounds.z1) return st.treadY(z);
        }
        for (Terrace t : plan.terraces) {
            if (z >= t.bounds.z0 && z <= t.bounds.z1) return t.elevation - 1;
        }
        return plan.terraces.get(0).elevation - 1;   // the forecourt
    }

    /**
     * Final pass: guarantee {@link #CORRIDOR_HEADROOM} blocks of air above the axis corridor from
     * the forecourt's front row to the row before the principal hall, everywhere except the gate
     * building (the passage handles that). Only writes air.
     */
    private static void clearAxisCorridor(SectSink sink, SectPlan plan, BuildStats stats) {
        Clip clip = sink.clip();
        int bx = plan.base.getX();
        int bz = plan.base.getZ();
        Slot gate = gateSlot(plan);
        int zEnd = corridorEndZ(plan.terraces.get(plan.terraces.size() - 1));
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int z = clipLo(APRON_Z0, clip.z0(), bz); z <= clipHi(zEnd, clip.z1(), bz); z++) {
            if (gate != null && z >= gate.bounds.z0 && z <= gate.bounds.z1) continue;
            for (int x = clipLo(AXIS_X0, clip.x0(), bx); x <= clipHi(AXIS_X1, clip.x1(), bx); x++) {
                int floorY = corridorFloorY(plan, x, z);
                for (int y = floorY + 1; y <= floorY + CORRIDOR_HEADROOM; y++) {
                    place(sink, at(plan.base, x, y, z), air, stats);
                }
            }
        }
    }

    /**
     * Whether the detached spire can be built: its bounds plus one block must stay clear of every
     * building slot, terrace and stair. None of the three current variants does (each would stand
     * inside the summit), so the feature stays in the plan but is not built until it gets a real
     * outcrop of its own.
     */
    static boolean featureBuildable(SectPlan plan) {
        if (plan.feature == null) return false;
        Rect d = plan.feature.detachedBounds;
        Rect grown = new Rect(d.x0 - 1, d.z0 - 1, d.x2() + 1, d.z1 + 1);
        for (Slot s : plan.slots) if (grown.overlaps(s.bounds)) return false;
        for (Terrace t : plan.terraces) if (grown.overlaps(t.bounds)) return false;
        for (AxisStair st : plan.stairs) if (grown.overlaps(st.withCheeks())) return false;
        return true;
    }

    /**
     * Detached-spire flying bridge: place the detached volume on the surface
     * (the derived spire top in worldgen, the live surface for the on-the-spot
     * command) and span a roofed bridge deck between summit and detached volume.
     * Skipped (and counted) unless {@link #featureBuildable}.
     */
    private static void realizeFeature(SectSink sink, SectPlan plan, RandomSource random, long seed, BuildStats stats) {
        if (plan.feature == null) return;
        if (!featureBuildable(plan)) {
            stats.featuresSkipped++;
            return;
        }
        FlyingBridgeFeature f = plan.feature;
        Clip clip = sink.clip();
        int bx = plan.base.getX();
        int bz = plan.base.getZ();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, f.detachedTemplate);
        Optional<ModBlockFallback.LoadedTemplate> loaded = sink.loadTemplate(id);
        if (loaded.isPresent()) {
            StructureTemplate template = loaded.get().template();
            int anchorX = f.detachedBounds.x0;
            int anchorZ = f.detachedBounds.z0;
            int tw = template.getSize().getX();
            int td = template.getSize().getZ();
            if (clipHitsLocal(clip, plan.base, anchorX, anchorZ, anchorX + tw - 1, anchorZ + td - 1)) {
                int surface = surfaceY(sink, plan.base, anchorX, anchorZ);
                BlockPos origin = new BlockPos(plan.base.getX() + anchorX, surface - 1,
                        plan.base.getZ() + anchorZ);
                clearVolume(sink, origin.above(), tw, template.getSize().getY() + 2, td, stats);
                sink.placeTemplate(template, origin, slotRandom(seed, anchorX, anchorZ));
                stats.placedSlots++;
                stats.fallbackSubstitutions += loaded.get().substitutions();
            }
        }
        // flying bridge deck between endpoints, riding above the gap
        Terrace summit = plan.terraces.get(plan.terraces.size() - 1);
        int deckY = summit.elevation + 1;
        List<Cell> span = bresenham(f.bridge.fromCell, f.bridge.toCell);
        for (Cell c : span) {
            // deck writes the column plus its east/west neighbour (c.x ± 1);
            // skip cells whose ±1 x-span and z fall outside the chunk slice.
            if (bx + c.x + 1 < clip.x0() || bx + c.x - 1 > clip.x1()
                    || bz + c.z < clip.z0() || bz + c.z > clip.z1()) {
                continue;
            }
            BlockPos deck = at(plan.base, c.x, deckY, c.z);
            place(sink, deck, Blocks.DARK_OAK_PLANKS.defaultBlockState(), stats);
            place(sink, deck.above(), Blocks.AIR.defaultBlockState(), stats);
            place(sink, deck.below(), Blocks.OAK_FENCE.defaultBlockState(), stats);
            place(sink, deck.east(), Blocks.DARK_OAK_FENCE.defaultBlockState(), stats);
            place(sink, deck.west(), Blocks.DARK_OAK_FENCE.defaultBlockState(), stats);
        }
        stats.featuresBuilt++;
    }

    private static Terrace findTerrace(SectPlan plan, int index) {
        for (Terrace t : plan.terraces) if (t.index == index) return t;
        return null;
    }

    static List<Cell> bresenham(Cell a, Cell b) {
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

    // --- clip helpers: tighten a local-coord loop range to the sink clip ------
    // The loop variable is local (relative to base); the clip is world-space.
    // Math is done in long so an UNBOUNDED clip (Integer.MIN/MAX) can't overflow.

    /** Lower local bound: max(loopLocalLo, clipWorldLo - base). */
    private static int clipLo(int loopLocalLo, int clipWorldLo, int baseWorld) {
        return (int) Math.max(loopLocalLo, (long) clipWorldLo - baseWorld);
    }

    /** Upper local bound: min(loopLocalHi, clipWorldHi - base). */
    private static int clipHi(int loopLocalHi, int clipWorldHi, int baseWorld) {
        return (int) Math.min(loopLocalHi, (long) clipWorldHi - baseWorld);
    }

    /** Whether a local-coord rect intersects the clip (world-space). */
    private static boolean clipHitsLocal(Clip clip, BlockPos base, int lx0, int lz0, int lx1, int lz1) {
        return clip.intersects(base.getX() + lx0, base.getZ() + lz0,
                base.getX() + lx1, base.getZ() + lz1);
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

    /**
     * Deterministic per-volume placement RNG derived from the sect seed and the
     * volume's stable local origin, so the same volume rolls the same template
     * processors/orientation regardless of which chunk's pass places it.
     */
    private static RandomSource slotRandom(long seed, int localX, int localZ) {
        long mixed = seed
                ^ ((long) localX * 0x9E3779B97F4A7C15L)
                ^ ((long) localZ * 0xC2B2AE3D27D4EB4FL);
        return RandomSource.create(mixed);
    }

    /**
     * Block position at a local (x, z) offset from the compound base but an
     * <em>absolute</em> world Y. Terrace elevations and {@link SectMountain}
     * heights are already absolute (they are compared against the absolute
     * natural surface), so they must NOT be fed through {@link BlockPos#offset}
     * — that would add {@code base.getY()} a second time and float the terrain a
     * full base-height above the buildings (which {@code realizeSlots} already
     * places at the absolute Y).
     */
    private static BlockPos at(BlockPos base, int localX, int worldY, int localZ) {
        return new BlockPos(base.getX() + localX, worldY, base.getZ() + localZ);
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
        public Clip clip() {
            // Command path builds the whole compound in a single pass.
            return Clip.UNBOUNDED;
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
            StructurePlaceSettings settings = new StructurePlaceSettings().addProcessor(DropIsolatedBlocks.INSTANCE);
            return template.placeInWorld(level, origin, origin, settings, random, BLOCK_FLAGS);
        }
    }

    // --- records ------------------------------------------------------------

    /**
     * Inclusive world-space x/z clip the realizer restricts its iteration to.
     * {@link #UNBOUNDED} covers the whole world (command path, one pass); the
     * worldgen path supplies the current chunk's column area.
     */
    record Clip(int x0, int z0, int x1, int z1) {
        static final Clip UNBOUNDED =
                new Clip(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

        /** Whether the world-space rect [wx0,wx1]×[wz0,wz1] intersects this clip. */
        boolean intersects(int wx0, int wz0, int wx1, int wz1) {
            return wx1 >= x0 && wx0 <= x1 && wz1 >= z0 && wz0 <= z1;
        }
    }

    record Cell(int x, int z) {
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

    record Slot(String id, int terraceIndex, String terraceName, String role, String archetype,
                String templateId, int importanceTier, Rect bounds, boolean againstCliffBack) {
        Cell center() {
            return new Cell((bounds.x0 + bounds.x2()) / 2, (bounds.z0 + bounds.z1) / 2);
        }
    }

    record GalleryLink(String id, String kind, String fromSlot, String toSlot,
                       Cell fromCell, Cell toCell, int[] terraceIndices) {
    }

    record RetainingFace(String id, int lower, int upper, Rect bounds, int height) {
    }

    /**
     * Grand stair between terraces {@code lower} and {@code upper}: treads over {@code bounds}
     * (x 27..35, from {@link #STAIR_PROJECT} rows in front of the band to the band's last row),
     * cheek walls one column either side. {@code lowerFloorY} is the lower terrace's floor block y.
     */
    record AxisStair(String id, int lower, int upper, Rect bounds, int lowerFloorY) {
        /** Rises per flight; two flights climb one terrace rise. */
        static final int FLIGHT = TERRACE_RISE / 2;
        /** Rows of the landing between the two flights. */
        static final int LANDING = TERRACE_RISE + STAIR_PROJECT - 2 * FLIGHT;

        /** Treads plus the cheek walls. */
        Rect withCheeks() {
            return new Rect(bounds.x0 - 1, bounds.z0, bounds.x2() + 1, bounds.z1);
        }

        /** Block y of the tread (or landing) in row z. */
        int treadY(int z) {
            int r = z - bounds.z0;
            if (r < FLIGHT) return lowerFloorY + 1 + r;
            if (r < FLIGHT + LANDING) return lowerFloorY + FLIGHT;
            return lowerFloorY + FLIGHT + 1 + (r - FLIGHT - LANDING);
        }

        boolean isLanding(int z) {
            int r = z - bounds.z0;
            return r >= FLIGHT && r < FLIGHT + LANDING;
        }
    }

    record FlyingBridgeFeature(String variant, String detachedArchetype, String detachedTemplate,
                               String detachedSlotId, Rect detachedBounds, int[] spireOffset,
                               String bearing, int bridgeSpan, String bridgeShape,
                               GalleryLink bridge) {
    }

    record SectPlan(BlockPos base, List<Terrace> terraces, Set<Cell> axisCells, List<Slot> slots,
                    List<GalleryLink> galleries, List<RetainingFace> retaining,
                    List<AxisStair> stairs, Rect apron, FlyingBridgeFeature feature) {
        /** Last row of the axis corridor, the row in front of the principal hall. */
        int axisEndZ() {
            return corridorEndZ(terraces.get(terraces.size() - 1));
        }
    }

    static final class BuildStats {
        int placedSlots;
        int skippedSlots;
        int featuresBuilt;
        int featuresSkipped;
        int blocksPlaced;
        int fallbackSubstitutions;
        final List<String> skippedSlotIds = new ArrayList<>();
    }
}
