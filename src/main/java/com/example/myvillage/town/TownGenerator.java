package com.example.myvillage.town;

import com.example.myvillage.MyVillageMod;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Runtime cultivation town realizer.
 *
 * Produces a districted ~160x160 town plan structurally equivalent to the
 * Python planner in tools/buildgen/town.py (districts gate/market/residential/
 * civic_core/fringe, ritual axis inside the civic core, street frontage with
 * party-wall rows and typed alleys). The footprint is force-loaded via chunk
 * tickets before placement and released afterwards; regions that cannot be
 * loaded or built are reported rather than silently skipped.
 */
public final class TownGenerator {
    static final int WIDTH = 160;
    static final int DEPTH = 160;
    static final int CENTER_X = WIDTH / 2; // base/reference center only
    static final int SPINE_HALF_WIDTH = 3;
    static final int MAX_SLOPE = 5;
    static final int MAX_FOOTPRINT_AXIS = 160;
    static final int TEMPLATE_GROUND_LAYER = 0;
    static final int BLOCK_FLAGS = Block.UPDATE_CLIENTS;
    static final int INTERIOR_LANE_WIDTH = 2;

    static final int CENTER_X_JITTER = 4;
    static final int LANE_JITTER = 2;
    static final int DISTRICT_WIDTH_JITTER = 3;
    static final String[] PERIMETER_FAMILIES = {
            "square", "circle", "oval", "dshape", "octagon", "trapezoid"};
    static final String[] PERIMETER_MODIFIERS = {"none", "barbican", "bastion"};
    static final int GATE_RUN_HALF = 8, GATE_BAND_DEPTH = 2, CIRCLE_MARGIN = 1;
    static final int OVAL_RX_MIN = 60, OVAL_RX_MAX = 74;
    static final int OVAL_RZ_MIN = 50, OVAL_RZ_MAX = 64;
    static final int OCTAGON_K = 44, TRAPEZOID_SLANT = 12;
    static final int BARBICAN_OFFSET = 9, BARBICAN_WIDTH = 8, BARBICAN_DEPTH = 6;
    static final int BASTION_HALF_W = 10, BASTION_DEPTH = 8;

    private TownGenerator() {
    }

    /** Package-visible parity surface used by the JUnit Python⇄Java fixture. */
    static Map<String, Object> paritySnapshot(long seed) {
        TownPlan plan = plan(seed, BlockPos.ZERO);
        int[][] bands = laneBands(seed);
        ShapeSelection shape = new ShapeSelection(plan.shapeFamily, plan.shapeModifier);
        Rect protectedCore = new Rect(plan.centerX - 36, bands[2][1] + 1,
                plan.centerX + 36, DEPTH - 2);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("seed", seed);
        out.put("family", plan.shapeFamily);
        out.put("modifier", plan.shapeModifier);
        out.put("center_x", plan.centerX);
        out.put("lanes", List.of(
                List.of(bands[0][0], bands[0][1]),
                List.of(bands[1][0], bands[1][1]),
                List.of(bands[2][0], bands[2][1])));
        out.put("perimeter_cells", plan.perimeter.size());
        out.put("interior_cells", perimeterInterior(seed, shape, protectedCore).size());
        out.put("district_widths", plan.districts.stream()
                .map(d -> d.bounds.width()).toList());
        out.put("district_cells", plan.districts.stream()
                .map(d -> d.cells.size()).toList());
        out.put("validation_errors", validatePlan(plan));
        return out;
    }

    /** Explicit integer-curve sweep hook (circle/oval risk mitigation). */
    static List<Integer> curveCounts(long seed, String family) {
        Set<Cell> interior = familyInterior(seed, family);
        ShapeSelection shape = new ShapeSelection(family, "none");
        int cx = centerX(seed);
        int[][] bands = laneBands(seed);
        Rect protectedCore = new Rect(cx - 36, bands[2][1] + 1, cx + 36, DEPTH - 2);
        return List.of(interior.size(), boundary(seed, shape, protectedCore).size());
    }

    public static long seedFromSource(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = player.blockPosition();
        long site = (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL);
        return source.getLevel().getSeed() ^ site;
    }

    public static int generate(CommandSourceStack source, long seed) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        BlockPos base = player.blockPosition().offset(-WIDTH / 2, 0, -DEPTH / 2);
        if (WIDTH > MAX_FOOTPRINT_AXIS || DEPTH > MAX_FOOTPRINT_AXIS) {
            source.sendFailure(Component.literal(
                    "Town footprint " + WIDTH + "x" + DEPTH + " exceeds max " + MAX_FOOTPRINT_AXIS));
            return 0;
        }

        TownPlan plan = plan(seed, base);
        List<String> validationErrors = validatePlan(plan);
        if (!validationErrors.isEmpty()) {
            source.sendFailure(Component.literal("Town plan failed validation: " + validationErrors));
            return 0;
        }

        RandomSource templateRandom = RandomSource.create(mixSeed(seed, base));
        BuildStats stats = new BuildStats();
        List<ChunkPos> forcedChunks = new ArrayList<>();
        List<String> loadFailures = new ArrayList<>();
        try {
            forceLoadFootprint(level, base, WIDTH, DEPTH, forcedChunks, loadFailures);
            placePerimeter(level, plan, stats);
            placePrecinctWalls(level, plan, stats);
            realizeParcels(level, plan, templateRandom, seed, stats);
            placeStreetNetwork(level, plan, stats);
            placeRitualAxisFixtures(level, plan, stats);
            placeSpiritWay(level, plan, stats);
            placeColonnade(level, plan, stats);
            placeFormationFloor(level, plan, stats);
            placeFrontages(level, plan, stats);
            furnishStreetRooms(level, plan, stats);
            dressSpineStreetscape(level, plan, stats);
            dressNegativeSpaces(level, plan, stats);
            placeInhabitants(level, plan, templateRandom, stats);
            applySmokeLightAndWear(level, plan, stats);
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
        int placed = stats.placedParcels;
        int skipped = stats.skippedParcels;
        source.sendSuccess(
                () -> Component.literal("Generated living town seed=" + seed
                        + " footprint=" + WIDTH + "x" + DEPTH
                        + " districts=" + plan.districts.size()
                        + " placed=" + placed
                        + " skipped=" + skipped
                        + " decor=" + stats.decorFixturesPlaced
                        + " inhabitants=" + stats.inhabitantsPlaced
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

    /**
     * Acquire chunk-load tickets across the footprint and synchronously load
     * each chunk. Records chunks that fall outside the world border (or otherwise
     * fail to load) so the command can report them. Tickets are released by the
     * caller's finally block.
     */
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
                boolean inBorder = level.getWorldBorder().isWithinBounds(pos);
                if (!inBorder) {
                    failures.add("chunk(" + cx + "," + cz + ") outside world border");
                    continue;
                }
                level.setChunkForced(cx, cz, true);
                forced.add(pos);
                LevelChunk chunk = level.getChunk(cx, cz);
                if (chunk == null || chunk.isEmpty()) {
                    // A fully-empty chunk after a forced load is still buildable;
                    // we only report hard failures above. Nothing else to do.
                }
            }
        }
    }

    // --- plan ---------------------------------------------------------------

    private static TownPlan plan(long seed, BlockPos base) {
        int centerX = centerX(seed);
        int spineX0 = centerX - SPINE_HALF_WIDTH;
        int spineX1 = centerX + SPINE_HALF_WIDTH;
        int[][] laneBands = laneBands(seed);
        int[] laneS = laneBands[0], laneM = laneBands[1], laneN = laneBands[2];
        Map<String, Integer> widthJitter = districtWidthJitters(seed);
        ShapeSelection shape = selectPerimeterShape(seed);
        Rect protectedCore = new Rect(centerX - 36, laneN[1] + 1, centerX + 36, DEPTH - 2);
        Set<Cell> interior = perimeterInterior(seed, shape, protectedCore);
        Set<Cell> perimeter = boundary(seed, shape, protectedCore);
        Set<Cell> southGate = rect(centerX - 2, 0, centerX + 2, 0);
        List<Gate> gates = List.of(new Gate("south_gate", "south", southGate));
        Set<Cell> wall = new HashSet<>(perimeter);
        wall.removeAll(southGate);

        // Ritual axis geometry, expressed entirely inside the civic core band.
        // shrineDepth sizes the parcel depth so the 22-deep town_shrine template
        // fits with one cell of margin; mirrors town.py shrine_d == 21.
        int shrineWidth = 23;
        int shrineDepth = 21;
        int shrineX0 = centerX - shrineWidth / 2 - 1;
        int shrineX1 = shrineX0 + shrineWidth + 1;
        int shrineZ1 = DEPTH - 2;
        int shrineZ0 = shrineZ1 - shrineDepth;
        Rect shrineParcel = new Rect(shrineX0, shrineZ0, shrineX1, shrineZ1);
        Rect plaza = new Rect(centerX - 16, shrineZ0 - 9, centerX + 16, shrineZ0 - 1);
        Set<Cell> paifang = rect(centerX - 6, plaza.z0 - 1, centerX + 6, plaza.z0 - 1);
        Set<Cell> lanterns = new HashSet<>();
        for (int z = laneN[1] + 2; z < plaza.z0 - 1; z += 5) {
            lanterns.add(new Cell(centerX - 5, z));
            lanterns.add(new Cell(centerX + 5, z));
        }

        Set<Cell> spine = rect(spineX0, 0, spineX1, plaza.z1);
        spine.addAll(plaza.cells());
        spine.addAll(paifang);
        Set<Cell> lanes = new HashSet<>();
        for (int[] lane : laneBands) {
            lanes.addAll(rect(8, lane[0], WIDTH - 9, lane[1]));
        }
        lanes.removeAll(spine);
        spine.retainAll(interior);
        lanes.retainAll(interior);
        Set<Cell> streets = new HashSet<>(spine);
        streets.addAll(lanes);

        // Districts (mirror town.py _layout). [kind, x0, z0, x1, z1, density, sMin, sMax, material, roster...]
        Rect westCivic = new Rect(centerX - 33, plaza.z0 - 10, plaza.x0 - 1, plaza.z1);
        Rect eastCivic = new Rect(plaza.x1 + 1, plaza.z0 - 10, centerX + 33, plaza.z1);
        List<District> districts = new ArrayList<>();
        districts.add(district("gate", centerX - 24, 1, centerX + 24, laneS[0] - 1));
        districts.add(district("residential", 8 - widthJitter.get("south_res_w"), 1, centerX - 25, laneS[0] - 1));
        districts.add(district("residential", centerX + 25, 1, WIDTH - 9 + widthJitter.get("south_res_e"), laneS[0] - 1));
        districts.add(district("market", 8 - widthJitter.get("market_w"), laneS[1] + 1, spineX0 - 1, laneM[0] - 1));
        districts.add(district("market", spineX1 + 1, laneS[1] + 1, WIDTH - 9 + widthJitter.get("market_e"), laneM[0] - 1));
        districts.add(district("residential", 8 - widthJitter.get("mid_res_w"), laneM[1] + 1, spineX0 - 1, laneN[0] - 1));
        districts.add(district("residential", spineX1 + 1, laneM[1] + 1, WIDTH - 9 + widthJitter.get("mid_res_e"), laneN[0] - 1));
        districts.add(district("civic_core", centerX - 36, laneN[1] + 1, centerX + 36, DEPTH - 2));
        districts.add(district("fringe", 8 - widthJitter.get("fringe_w"), laneN[1] + 1, centerX - 37, DEPTH - 2));
        districts.add(district("fringe", centerX + 37, laneN[1] + 1, WIDTH - 9 + widthJitter.get("fringe_e"), DEPTH - 2));
        if (!shape.family.equals("square") || !shape.modifier.equals("none")) {
            for (int i = 0; i < districts.size(); i++) {
                District d = districts.get(i);
                if (d.kind.equals("civic_core")) continue;
                Set<Cell> clipped = new HashSet<>(d.bounds.cells());
                clipped.retainAll(interior);
                if (!clipped.equals(d.cells)) districts.set(i, d.withCells(clipped));
            }
        }

        List<Parcel> parcels = new ArrayList<>();
        List<OpenRegion> openRegions = new ArrayList<>();
        List<OpenRegion> alleys = new ArrayList<>();

        District core = districts.stream().filter(d -> d.kind.equals("civic_core")).findFirst().orElseThrow();
        parcels.add(new Parcel("town_shrine", "civic", shrineParcel, 3, true, "town_shrine_001",
                "civic_core", "timber_ceremonial", core.storeyMax, ""));

        // Vertical landmarks (pagoda + bell/drum tower) flank the shrine inside
        // the civic core so the skyline rises above the surrounding roofline.
        int landmarkW = 19;
        int landmarkD = 21; // mirrors town.py landmark_d so the parcel fits the 21-deep templates
        int landmarkZ0 = shrineZ0;
        int landmarkZ1 = Math.min(DEPTH - 2, shrineZ0 + landmarkD - 1);
        Rect pagodaParcel = new Rect(shrineX0 - 3 - landmarkW, landmarkZ0, shrineX0 - 3, landmarkZ1);
        Rect towerParcel = new Rect(shrineX1 + 3, landmarkZ0, shrineX1 + 3 + landmarkW, landmarkZ1);
        parcels.add(new Parcel("pagoda_west", "civic", pagodaParcel, core.storeyMax, false, "pagoda_001",
                "civic_core", "timber_ceremonial", core.storeyMax, ""));
        parcels.add(new Parcel("bell_drum_tower_east", "civic", towerParcel, core.storeyMax, false, "bell_drum_tower_001",
                "civic_core", "timber_ceremonial", core.storeyMax, ""));

        // Side halls (配殿) enclose the plaza forecourt: low-tier civic parcels
        // placed in the forecourt gaps between the civic halls, capped below the
        // dominant-landmark tier and within the storey band. The gaps are
        // narrower than any shipped template, so these are block-built civic
        // fill (empty templateId); the realizer dresses them as compact halls.
        int sideTier = MAX_IMPORTANCE_TIER_INTERNAL - 1;
        Rect sideHallWest = new Rect(plaza.x0, westCivic.z0, plaza.x0 + 10, plaza.z0 - 2);
        Rect sideHallEast = new Rect(plaza.x1 - 10, eastCivic.z0, plaza.x1, plaza.z0 - 2);
        if (sideHallWest.x1 >= sideHallWest.x0 && sideHallWest.z1 >= sideHallWest.z0) {
            parcels.add(new Parcel("civic_side_hall_west", "civic", sideHallWest, sideTier, false, "",
                    "civic_core", core.material, core.storeyMin, ""));
        }
        if (sideHallEast.x1 >= sideHallEast.x0 && sideHallEast.z1 >= sideHallEast.z0) {
            parcels.add(new Parcel("civic_side_hall_east", "civic", sideHallEast, sideTier, false, "",
                    "civic_core", core.material, core.storeyMin, ""));
        }

        int[] idx = new int[]{100};
        for (District d : districts) {
            subdivideDistrict(d, streets, westCivic, eastCivic, parcels, alleys,
                    openRegions, idx, seed, lanes, spineX0, spineX1);
        }

        // Fringe spirit-field negative spaces (灵田/药圃).
        for (District d : districts) {
            if (!d.kind.equals("fringe")) continue;
            Rect field = new Rect(d.bounds.x0 + 1, d.bounds.z0 + 6,
                    d.bounds.x1 - 1, d.bounds.z1 - 1);
            Set<Cell> fieldCells = new HashSet<>(field.cells());
            fieldCells.retainAll(d.cells);
            openRegions.add(new OpenRegion(
                    "spirit_field_" + d.bounds.x0 + "_" + d.bounds.z0,
                    "spirit_field", field, 0, fieldCells));
        }

        Precinct precinct = buildPrecinct(core, spine, plaza, paifang, lanterns,
                westCivic, eastCivic, shrineParcel, pagodaParcel, towerParcel,
                parcels, streets, centerX, spineX0, spineX1);

        RitualAxis ritualAxis = new RitualAxis("south_gate", "town_shrine", plaza, paifang, lanterns);
        return new TownPlan(seed, base, perimeter, wall, gates, spine, lanes, parcels,
                openRegions, alleys, districts, ritualAxis, precinct,
                shape.family, shape.modifier, centerX, spineX0, spineX1,
                laneS[1]);
    }

    /**
     * Derive the civic-precinct framing (mirrors tools/buildgen/town.py
     * _layout + generate_town_plan masking). Deterministic from cx / spine /
     * plaza / shrine / landmark bounds so the Python planner and this realizer
     * agree cell-for-cell. Returns the gate, spirit-way, colonnade, wall, and
     * side-gate cell sets.
     */
    private static Precinct buildPrecinct(District core, Set<Cell> spine, Rect plaza,
                                          Set<Cell> paifang, Set<Cell> lanterns,
                                          Rect westCivic, Rect eastCivic, Rect shrineParcel,
                                          Rect pagodaParcel, Rect towerParcel,
                                          List<Parcel> parcels, Set<Cell> streets,
                                          int centerX, int spineX0, int spineX1) {
        int coreX0 = core.bounds.x0, coreZ0 = core.bounds.z0;
        int coreX1 = core.bounds.x1, coreZ1 = core.bounds.z1;

        // Precinct gate: paifang-style run on the spine at the core's
        // gate-facing edge (z-min). Stays spine (passable); the wall opens here.
        Set<Cell> precinctGate = rect(spineX0, coreZ0, spineX1, coreZ0);

        // Spirit-way band: flanking statue/stele cells along the spine, every
        // other row, masked off the spine walking width and the lantern cells.
        Set<Cell> spiritWay = new HashSet<>();
        int spiritZ0 = coreZ0 + 1;
        int spiritZ1 = plaza.z0 - 1;
        for (int z = spiritZ0; z <= spiritZ1; z++) {
            if ((z - spiritZ0) % 2 != 0) continue;
            for (int fx : new int[]{centerX - 5, centerX + 5}) {
                Cell c = new Cell(fx, z);
                if (spine.contains(c) || lanterns.contains(c)) continue;
                spiritWay.add(c);
            }
        }

        // Colonnade edge runs consume the lateral core slivers; the outermost
        // sliver column is reserved for the precinct wall, the inner columns
        // carry the covered walk. A sliver narrower than 2 degrades to wall-only.
        Set<Cell> civicRectCells = new HashSet<>();
        civicRectCells.addAll(westCivic.cells());
        civicRectCells.addAll(eastCivic.cells());
        civicRectCells.addAll(shrineParcel.cells());
        civicRectCells.addAll(pagodaParcel.cells());
        civicRectCells.addAll(towerParcel.cells());
        Set<Cell> approachCells = new HashSet<>(spine);
        approachCells.addAll(plaza.cells());
        approachCells.addAll(lanterns);
        approachCells.addAll(paifang);
        Set<Cell> colonnadeWest = colonnadeRun(coreX0 + 1, coreX0 + 2, coreZ0, coreZ1, civicRectCells, approachCells);
        Set<Cell> colonnadeEast = colonnadeRun(coreX1 - 2, coreX1 - 1, coreZ0, coreZ1, civicRectCells, approachCells);

        // Precinct wall on the gate-facing (south) + lateral edges (back/north
        // open). The spine gate opens at the precinct gate; one 2-cell side
        // gate per lateral edge sits at the edge midpoint.
        Set<Cell> precinctWall = new HashSet<>();
        precinctWall.addAll(rect(coreX0, coreZ0, coreX1, coreZ0));
        precinctWall.addAll(rect(coreX0, coreZ0, coreX0, coreZ1));
        precinctWall.addAll(rect(coreX1, coreZ0, coreX1, coreZ1));
        precinctWall.removeAll(spine);
        int sideZ = (coreZ0 + coreZ1) / 2;
        Set<Cell> precinctSideGates = new HashSet<>();
        precinctSideGates.add(new Cell(coreX0, sideZ));
        precinctSideGates.add(new Cell(coreX0, sideZ + 1));
        precinctSideGates.add(new Cell(coreX1, sideZ));
        precinctSideGates.add(new Cell(coreX1, sideZ + 1));
        precinctWall.removeAll(precinctSideGates);
        // Wall takes priority over the colonnade at the south corners.
        colonnadeWest.removeAll(precinctWall);
        colonnadeEast.removeAll(precinctWall);

        // Second mask pass against the final parcel + street set so nothing
        // overlaps (accounts for the side halls emitted above).
        Set<Cell> parcelCellSet = new HashSet<>();
        for (Parcel p : parcels) parcelCellSet.addAll(p.bounds.cells());
        colonnadeWest.removeAll(parcelCellSet);
        colonnadeWest.removeAll(streets);
        colonnadeEast.removeAll(parcelCellSet);
        colonnadeEast.removeAll(streets);
        precinctWall.removeAll(parcelCellSet);
        precinctWall.removeAll(streets);
        spiritWay.removeAll(parcelCellSet);

        Set<Cell> colonnade = new HashSet<>(colonnadeWest);
        colonnade.addAll(colonnadeEast);
        return new Precinct(precinctGate, spiritWay, colonnade, precinctWall, precinctSideGates);
    }

    private static Set<Cell> colonnadeRun(int innerX0, int innerX1, int z0, int z1,
                                          Set<Cell> civicRectCells, Set<Cell> approachCells) {
        if (innerX1 - innerX0 + 1 < 2) return new HashSet<>(); // degraded to wall-only
        Set<Cell> raw = rect(innerX0, z0, innerX1, z1);
        raw.removeAll(civicRectCells);
        raw.removeAll(approachCells);
        return raw;
    }

    private static District district(String kind, int x0, int z0, int x1, int z1) {
        int density;
        int sMin;
        int sMax;
        String material;
        String rosterHead;
        switch (kind) {
            case "gate" -> { density = 2; sMin = 1; sMax = 1; material = "timber_lantern_gate"; rosterHead = "cultivation_house"; }
            case "market" -> { density = 5; sMin = 1; sMax = 2; material = "timber_painted_market"; rosterHead = "cultivation_shop"; }
            case "residential" -> { density = 5; sMin = 1; sMax = 2; material = "timber_plain_house"; rosterHead = "cultivation_house"; }
            case "civic_core" -> { density = 3; sMin = 2; sMax = 3; material = "timber_ceremonial"; rosterHead = "cultivation_shop"; }
            default -> { density = 1; sMin = 1; sMax = 1; material = "field_stone_fringe"; rosterHead = "cultivation_house"; }
        }
        Rect bounds = new Rect(x0, z0, x1, z1);
        return new District(kind, bounds, bounds.cells(), density, sMin, sMax, material, rosterHead);
    }

    private static void subdivideDistrict(
            District d, Set<Cell> streets, Rect westCivic, Rect eastCivic,
            List<Parcel> parcels, List<OpenRegion> alleys, List<OpenRegion> openRegions, int[] idx,
            long seed, Set<Cell> lanes, int spineX0, int spineX1) {
        Rect b = d.bounds;
        switch (d.kind) {
            case "civic_core" -> {
                addCivicHall(d, "civic_west_hall", westCivic, "cultivation_shop", parcels, idx);
                addCivicHall(d, "civic_east_hall", eastCivic, "cultivation_shop", parcels, idx);
                return;
            }
            case "gate" -> {
                addGateParcel(d, new Rect(b.x0, b.z0, spineX0 - 1, b.z1), "E", parcels, idx);
                addGateParcel(d, new Rect(spineX1 + 1, b.z0, b.x1, b.z1), "W", parcels, idx);
                return;
            }
            case "fringe" -> {
                return;
            }
            default -> { /* market / residential -> frontage */ }
        }
        // choose the street-facing edge (prefer S, N, E, W)
        String side = chooseFrontageEdge(b, streets);
        if (side.isEmpty()) return;
        int perp;
        int alongStart;
        int alongEnd;
        if (side.equals("E") || side.equals("W")) {
            perp = b.x1 - b.x0 + 1;
            alongStart = b.z0;
            alongEnd = b.z1;
        } else {
            perp = b.z1 - b.z0 + 1;
            alongStart = b.x0;
            alongEnd = b.x1;
        }
        if (perp < 12) return;
        String[] variants = variantsOf(d.rosterHead);
        int maxTd = 0;
        int minTw = Integer.MAX_VALUE;
        int minTd = Integer.MAX_VALUE;
        for (String v : variants) {
            int w = templateWidth(v);
            int dep = templateDepth(v);
            maxTd = Math.max(maxTd, dep);
            minTw = Math.min(minTw, w);
            minTd = Math.min(minTd, dep);
        }
        int yard = 8;
        // Decide densification against the FULL district depth (perp), not a
        // leftover already shrunk by the padded primary band. When the district
        // is deep enough for primary + lane + secondary + residual, trim the
        // primary band to exact template depth so a back row can be carved.
        boolean hasSecondary = perp >= 2 * maxTd + INTERIOR_LANE_WIDTH;
        int depth = hasSecondary ? maxTd : Math.min(maxTd + yard, perp);
        if (depth < maxTd) return;

        int bandLo;
        int bandHi;
        int yardStart;
        int yardEnd;

        if (side.equals("E") || side.equals("W")) {
            if (side.equals("E")) {
                bandLo = b.x1 - depth + 1;
                bandHi = b.x1;
                yardStart = b.x0;
                yardEnd = bandLo - 1;
            } else {
                bandLo = b.x0;
                bandHi = b.x0 + depth - 1;
                yardStart = bandHi + 1;
                yardEnd = b.x1;
            }
        } else if (side.equals("S")) {
            bandLo = b.z0;
            bandHi = b.z0 + depth - 1;
            yardStart = bandHi + 1;
            yardEnd = b.z1;
        } else { // N
            bandLo = b.z1 - depth + 1;
            bandHi = b.z1;
            yardStart = b.z0;
            yardEnd = bandLo - 1;
        }

        subdivideFrontage(d, side, alongStart, alongEnd, bandLo, bandHi, d.rosterHead, parcels, alleys, idx, seed, maxTd);

        if (hasSecondary) {
            int secBandLo;
            int secBandHi;
            String secSide = oppositeEdge(side);
            if (side.equals("S")) {
                secBandLo = bandHi + INTERIOR_LANE_WIDTH + 1;
                secBandHi = secBandLo + maxTd - 1;
                subdivideFrontage(d, secSide, alongStart, alongEnd, secBandLo, secBandHi,
                        d.rosterHead, parcels, alleys, idx, seed, maxTd);
                lanes.addAll(rect(alongStart, bandHi + 1, alongEnd, secBandLo - 1));
                Rect yardBounds = new Rect(alongStart, secBandHi + 1, alongEnd, b.z1);
                if (yardBounds.x1 >= yardBounds.x0 && yardBounds.z1 >= yardBounds.z0
                        && (yardBounds.x1 - yardBounds.x0 + 1) * (yardBounds.z1 - yardBounds.z0 + 1) >= 8) {
                    openRegions.add(new OpenRegion("yard_" + d.kind + "_" + idx[0], "courtyard_yard", yardBounds, 1));
                    idx[0]++;
                }
            } else if (side.equals("N")) {
                secBandLo = bandLo - INTERIOR_LANE_WIDTH - maxTd;
                secBandHi = bandLo - INTERIOR_LANE_WIDTH - 1;
                subdivideFrontage(d, secSide, alongStart, alongEnd, secBandLo, secBandHi,
                        d.rosterHead, parcels, alleys, idx, seed, maxTd);
                lanes.addAll(rect(alongStart, secBandHi + 1, alongEnd, bandLo - 1));
                Rect yardBounds = new Rect(alongStart, b.z0, alongEnd, secBandLo - 1);
                if (yardBounds.x1 >= yardBounds.x0 && yardBounds.z1 >= yardBounds.z0
                        && (yardBounds.x1 - yardBounds.x0 + 1) * (yardBounds.z1 - yardBounds.z0 + 1) >= 8) {
                    openRegions.add(new OpenRegion("yard_" + d.kind + "_" + idx[0], "courtyard_yard", yardBounds, 1));
                    idx[0]++;
                }
            } else if (side.equals("E")) {
                secBandLo = bandLo - INTERIOR_LANE_WIDTH - maxTd;
                secBandHi = bandLo - INTERIOR_LANE_WIDTH - 1;
                subdivideFrontage(d, secSide, alongStart, alongEnd, secBandLo, secBandHi,
                        d.rosterHead, parcels, alleys, idx, seed, maxTd);
                lanes.addAll(rect(secBandHi + 1, alongStart, bandLo - 1, alongEnd));
                Rect yardBounds = new Rect(b.x0, alongStart, secBandLo - 1, alongEnd);
                if (yardBounds.x1 >= yardBounds.x0 && yardBounds.z1 >= yardBounds.z0
                        && (yardBounds.x1 - yardBounds.x0 + 1) * (yardBounds.z1 - yardBounds.z0 + 1) >= 8) {
                    openRegions.add(new OpenRegion("yard_" + d.kind + "_" + idx[0], "courtyard_yard", yardBounds, 1));
                    idx[0]++;
                }
            } else { // W
                secBandLo = bandHi + INTERIOR_LANE_WIDTH + 1;
                secBandHi = secBandLo + maxTd - 1;
                subdivideFrontage(d, secSide, alongStart, alongEnd, secBandLo, secBandHi,
                        d.rosterHead, parcels, alleys, idx, seed, maxTd);
                lanes.addAll(rect(bandHi + 1, alongStart, secBandLo - 1, alongEnd));
                Rect yardBounds = new Rect(secBandHi + 1, alongStart, b.x1, alongEnd);
                if (yardBounds.x1 >= yardBounds.x0 && yardBounds.z1 >= yardBounds.z0
                        && (yardBounds.x1 - yardBounds.x0 + 1) * (yardBounds.z1 - yardBounds.z0 + 1) >= 8) {
                    openRegions.add(new OpenRegion("yard_" + d.kind + "_" + idx[0], "courtyard_yard", yardBounds, 1));
                    idx[0]++;
                }
            }
            return;
        }

        Rect yardBounds;
        if (side.equals("E") || side.equals("W")) {
            yardBounds = new Rect(yardStart, b.z0, yardEnd, b.z1);
        } else {
            yardBounds = new Rect(b.x0, yardStart, b.x1, yardEnd);
        }
        if (yardBounds.x1 >= yardBounds.x0 && yardBounds.z1 >= yardBounds.z0
                && (yardBounds.x1 - yardBounds.x0 + 1) * (yardBounds.z1 - yardBounds.z0 + 1) >= 8) {
            openRegions.add(new OpenRegion("yard_" + d.kind + "_" + idx[0], "courtyard_yard", yardBounds, 1));
            idx[0]++;
        }
    }

    private static void addCivicHall(District d, String id, Rect bounds, String baseArchetype,
                                     List<Parcel> parcels, int[] idx) {
        if (bounds.x1 < bounds.x0 || bounds.z1 < bounds.z0) return;
        String tpl = variantThatFits(baseArchetype, bounds.x1 - bounds.x0 + 1, bounds.z1 - bounds.z0 + 1);
        int storey = Math.min(MAX_IMPORTANCE_TIER_INTERNAL, d.storeyMax);
        parcels.add(new Parcel(id, "civic", bounds, storey, false, tpl,
                d.kind, d.material, storey, ""));
        idx[0]++;
    }

    private static void addGateParcel(District d, Rect bounds, String edge, List<Parcel> parcels, int[] idx) {
        if (bounds.x1 < bounds.x0) return;
        if (!d.cells.containsAll(bounds.cells())) return;
        String tpl = variantThatFits("cultivation_house", bounds.x1 - bounds.x0 + 1, bounds.z1 - bounds.z0 + 1);
        parcels.add(new Parcel("parcel_gate_" + idx[0], "housing", bounds, d.storeyMin, false, tpl,
                d.kind, d.material, d.storeyMin, edge));
        idx[0]++;
    }

    private static void subdivideFrontage(
            District d, String side, int alongStart, int alongEnd, int bandLo, int bandHi,
            String baseArchetype, List<Parcel> parcels, List<OpenRegion> alleys, int[] idx,
            long seed, int maxDepth) {
        String[] variants = variantsOf(baseArchetype);
        int minWidth = Integer.MAX_VALUE;
        for (String v : variants) minWidth = Math.min(minWidth, templateWidth(v));
        int alleyEvery = 3;
        int run = 0;
        int i = alongStart;
        int parcelCounter = 0;
        while (i <= alongEnd) {
            int remaining = (alongEnd - i + 1);
            if (remaining < minWidth) {
                Rect bounds = parcelBounds(side, i, alongEnd, bandLo, bandHi);
                alleys.add(new OpenRegion("alley_" + d.kind + "_" + idx[0], "alley", bounds, 0));
                idx[0]++;
                break;
            }
            int reserve = Math.max(0, 2 - run) * minWidth;
            List<String> fitting = new ArrayList<>();
            for (String variant : variants) {
                int width = templateWidth(variant);
                if (width > remaining || width > remaining - reserve) continue;
                Rect candidate = parcelBounds(side, i, i + width - 1, bandLo, bandHi);
                if (d.cells.containsAll(candidate.cells())) fitting.add(variant);
            }
            if (fitting.isEmpty()) {
                i++;
                continue;
            }
            int variantIdx = TownHash.range64(seed,
                    "frontage_variant:" + d.id() + ":" + side + ":" + i,
                    0, fitting.size() - 1);
            String chosenVariant = fitting.get(variantIdx);
            int segWidth = templateWidth(chosenVariant);
            int segEnd = i + segWidth - 1;
            Rect bounds = parcelBounds(side, i, segEnd, bandLo, bandHi);
            int storey = storeyWithin(d, parcelCounter);
            parcels.add(new Parcel("parcel_" + d.kind + "_" + idx[0], roleForKind(d.kind), bounds,
                    importanceForKind(d.kind), false, chosenVariant, d.kind, d.material, storey, side));
            idx[0]++;
            parcelCounter++;
            i = segEnd + 1;
            run++;
            if (run >= alleyEvery && i <= alongEnd) {
                Rect gap = parcelBounds(side, i, i, bandLo, bandHi);
                alleys.add(new OpenRegion("alley_" + d.kind + "_" + idx[0], "alley", gap, 0));
                idx[0]++;
                i++;
                run = 0;
            }
        }
    }

    private static Rect parcelBounds(String side, int segStart, int segEnd, int bandLo, int bandHi) {
        if (side.equals("N") || side.equals("S")) {
            return new Rect(segStart, bandLo, segEnd, bandHi);
        }
        return new Rect(bandLo, segStart, bandHi, segEnd);
    }

    private static String chooseFrontageEdge(Rect b, Set<Cell> streets) {
        for (String side : new String[]{"S", "N", "E", "W"}) {
            if (edgeTouchesStreet(b, side, streets)) return side;
        }
        return "";
    }

    private static String oppositeEdge(String edge) {
        return switch (edge) {
            case "N" -> "S";
            case "S" -> "N";
            case "E" -> "W";
            case "W" -> "E";
            default -> "";
        };
    }

    private static boolean edgeTouchesStreet(Rect b, String side, Set<Cell> streets) {
        Set<Cell> nbr = new HashSet<>();
        switch (side) {
            case "S" -> nbr.addAll(rect(b.x0, b.z0 - 1, b.x1, b.z0 - 1));
            case "N" -> nbr.addAll(rect(b.x0, b.z1 + 1, b.x1, b.z1 + 1));
            case "E" -> nbr.addAll(rect(b.x1 + 1, b.z0, b.x1 + 1, b.z1));
            default -> nbr.addAll(rect(b.x0 - 1, b.z0, b.x0 - 1, b.z1));
        }
        for (Cell c : nbr) {
            if (streets.contains(c)) return true;
        }
        return false;
    }

    private static int importanceForKind(String kind) {
        return switch (kind) {
            case "civic_core" -> 3;
            case "market" -> 2;
            case "residential", "gate" -> 1;
            default -> 0;
        };
    }

    private static String roleForKind(String kind) {
        return switch (kind) {
            case "market" -> "market";
            case "civic_core" -> "civic";
            default -> "housing";
        };
    }

    private static int storeyWithin(District d, int idx) {
        int lo = d.storeyMin;
        int hi = d.storeyMax;
        if (hi <= lo) return lo;
        return lo + idx % (hi - lo + 1);
    }

    // --- template registry (mirrors town.py TEMPLATE_VARIANTS / TEMPLATE_FOOTPRINT) ---

    private static String canonicalTemplate(String baseArchetype) {
        // first shipped variant of the archetype
        return variant(baseArchetype, 0);
    }

    private static String variant(String base, int i) {
        String[] variants = variantsOf(base);
        return variants[Math.floorMod(i, variants.length)];
    }

    private static String[] variantsOf(String base) {
        return switch (base) {
            case "cultivation_house" -> new String[]{"cultivation_house_001", "cultivation_house_002", "cultivation_house_003"};
            case "cultivation_shop" -> new String[]{"cultivation_shop_001", "cultivation_shop_002", "cultivation_shop_003"};
            case "cultivation_market" -> new String[]{"cultivation_market_001", "cultivation_market_002", "cultivation_market_003"};
            case "cultivation_inn" -> new String[]{"cultivation_inn_001", "cultivation_inn_002", "cultivation_inn_003"};
            case "town_shrine" -> new String[]{"town_shrine_001"};
            case "pagoda" -> new String[]{"pagoda_001", "pagoda_002", "pagoda_003"};
            case "pavilion" -> new String[]{"pavilion_001", "pavilion_002", "pavilion_003"};
            case "bell_drum_tower" -> new String[]{"bell_drum_tower_001", "bell_drum_tower_002", "bell_drum_tower_003"};
            default -> new String[]{base};
        };
    }

    private static String variantThatFits(String base, int maxW, int maxD) {
        for (String v : variantsOf(base)) {
            if (templateWidth(v) <= maxW && templateDepth(v) <= maxD) return v;
        }
        return variant(base, 0);
    }

    private static int templateWidth(String id) {
        return templateFootprint(id)[0];
    }

    private static int templateDepth(String id) {
        return templateFootprint(id)[1];
    }

    private static int[] templateFootprint(String id) {
        return switch (id) {
            case "cultivation_house", "cultivation_house_001", "cultivation_house_002" -> new int[]{15, 15};
            case "cultivation_house_003" -> new int[]{17, 16};
            case "cultivation_shop", "cultivation_shop_002" -> new int[]{17, 17};
            case "cultivation_shop_001" -> new int[]{17, 19};
            case "cultivation_shop_003" -> new int[]{15, 17};
            case "cultivation_market", "cultivation_market_001" -> new int[]{17, 17};
            case "cultivation_market_002", "cultivation_market_003" -> new int[]{17, 19};
            case "cultivation_inn", "cultivation_inn_001", "cultivation_inn_002" -> new int[]{22, 19};
            case "cultivation_inn_003" -> new int[]{22, 17};
            case "town_shrine", "town_shrine_001" -> new int[]{23, 20};
            case "pagoda", "pagoda_001", "pagoda_003" -> new int[]{17, 19};
            case "pagoda_002" -> new int[]{19, 21};
            case "pavilion", "pavilion_001", "pavilion_003" -> new int[]{23, 21};
            case "pavilion_002" -> new int[]{21, 21};
            case "bell_drum_tower", "bell_drum_tower_001", "bell_drum_tower_003" -> new int[]{17, 19};
            case "bell_drum_tower_002" -> new int[]{17, 21};
            default -> new int[]{15, 15};
        };
    }

    private static final int MAX_IMPORTANCE_TIER_INTERNAL = 3;

    // --- validation ---------------------------------------------------------

    private static List<String> validatePlan(TownPlan plan) {
        List<String> errors = new ArrayList<>();
        ShapeSelection shape = new ShapeSelection(plan.shapeFamily, plan.shapeModifier);
        int[][] bands = laneBands(plan.seed());
        Rect protectedCore = new Rect(plan.centerX - 36, bands[2][1] + 1,
                plan.centerX + 36, DEPTH - 2);
        if (!plan.perimeter.equals(boundary(plan.seed(), shape, protectedCore))) {
            errors.add("perimeter_not_closed");
        }
        for (Gate gate : plan.gates) {
            if (!plan.perimeter.containsAll(gate.cells)) {
                errors.add("gate_off_wall:" + gate.id);
            }
        }
        long landmarks = plan.parcels.stream().filter(p -> p.dominant).count();
        if (landmarks != 1) {
            errors.add("dominant_landmark_count:" + landmarks);
        }
        Optional<Parcel> shrine = plan.parcels.stream().filter(p -> p.id.equals("town_shrine")).findFirst();
        if (shrine.isEmpty()) {
            errors.add("missing_town_shrine_anchor");
        } else {
            Parcel sp = shrine.get();
            if (!sp.dominant || sp.importance != 3) {
                errors.add("town_shrine_not_dominant_top_tier");
            }
            if (!plan.ritualAxis.terminusParcel.equals("town_shrine")) {
                errors.add("ritual_axis_wrong_terminus:" + plan.ritualAxis.terminusParcel);
            }
            Set<Cell> shrineFront = rect(sp.bounds.x0, sp.bounds.z0 - 1, sp.bounds.x1, sp.bounds.z0 - 1);
            if (disjoint(shrineFront, plan.ritualAxis.plaza.cells())) {
                errors.add("town_shrine_not_fronted_by_plaza");
            }
        }
        Set<Cell> streets = plan.streetCells();
        Set<Cell> shapeInterior = perimeterInterior(plan.seed, shape, protectedCore);
        Set<Cell> parcelCells = new HashSet<>();
        for (Parcel parcel : plan.parcels) {
            Set<Cell> cells = parcel.bounds.cells();
            boolean inDistrict = plan.districts.stream()
                    .filter(d -> d.kind.equals(parcel.districtKind))
                    .anyMatch(d -> d.cells.containsAll(cells));
            if (!inDistrict) errors.add("parcel_outside_district:" + parcel.id);
            if (!shapeInterior.containsAll(cells)) {
                errors.add("parcel_outside_perimeter:" + parcel.id);
            }
            if (!parcelCells.addAll(cells)) {
                errors.add("parcel_overlap:" + parcel.id);
            }
            if (!disjoint(cells, streets)) {
                errors.add("parcel_street_overlap:" + parcel.id);
            }
            if (!touches(cells, streets)) {
                errors.add("parcel_not_reachable:" + parcel.id);
            }
        }
        for (OpenRegion region : plan.openRegions) {
            Set<Cell> cells = region.cells;
            if (!disjoint(cells, streets)) {
                errors.add("negative_space_street_overlap:" + region.id);
            }
            if (!disjoint(cells, parcelCells)) {
                errors.add("negative_space_parcel_overlap:" + region.id);
            }
        }
        for (OpenRegion alley : plan.alleys) {
            Set<Cell> cells = alley.cells;
            if (!disjoint(cells, parcelCells)) {
                errors.add("alley_parcel_overlap:" + alley.id);
            }
            if (!disjoint(cells, streets)) {
                errors.add("alley_street_overlap:" + alley.id);
            }
        }
        Set<Cell> reachable = reachableFrom(new Cell(plan.centerX, DEPTH / 2), streets);
        if (!reachable.containsAll(streets)) {
            errors.add("street_network_disconnected");
        }
        // districts present
        Set<String> kinds = new HashSet<>();
        for (District d : plan.districts) kinds.add(d.kind);
        for (String k : new String[]{"gate", "market", "residential", "civic_core", "fringe"}) {
            if (!kinds.contains(k)) errors.add("missing_district:" + k);
        }
        errors.addAll(validatePrecinct(plan));
        return errors;
    }

    /**
     * Civic-precinct framing invariants (mirrors Python _validate_precinct):
     * wall on the gate-facing + lateral core edges, a spine-axis precinct gate,
     * a non-empty off-spine spirit-way band, the wall disjoint from parcels and
     * streets, and a wall run on each shared core&lt;-&gt;fringe edge.
     */
    private static List<String> validatePrecinct(TownPlan plan) {
        List<String> errors = new ArrayList<>();
        District core = plan.districts.stream().filter(d -> d.kind.equals("civic_core")).findFirst().orElse(null);
        if (core == null) return errors;
        Precinct p = plan.precinct;
        int cx0 = core.bounds.x0, cz0 = core.bounds.z0;
        int cx1 = core.bounds.x1, cz1 = core.bounds.z1;
        Set<Cell> coreCells = core.bounds.cells();
        Set<Cell> streets = plan.streetCells();
        Set<Cell> parcelCells = new HashSet<>();
        for (Parcel par : plan.parcels) parcelCells.addAll(par.bounds.cells());

        if (p.wall.isEmpty()) {
            errors.add("precinct_wall_missing");
            return errors;
        }
        if (p.gate.isEmpty()) {
            errors.add("precinct_gate_missing");
        } else {
            if (!plan.spine.containsAll(p.gate)) errors.add("precinct_gate_not_on_spine");
            if (!coreCells.containsAll(p.gate)) errors.add("precinct_gate_outside_civic_core");
            Set<Integer> gateZs = new HashSet<>();
            for (Cell c : p.gate) gateZs.add(c.z);
            if (gateZs.size() != 1 || !gateZs.contains(cz0)) errors.add("precinct_gate_not_on_gate_facing_edge");
        }
        if (disjoint(p.wall, rect(cx0, cz0, cx1, cz0))) errors.add("precinct_wall_missing_on_edge:gate_facing");
        if (disjoint(p.wall, rect(cx0, cz0, cx0, cz1))) errors.add("precinct_wall_missing_on_edge:lateral_west");
        if (disjoint(p.wall, rect(cx1, cz0, cx1, cz1))) errors.add("precinct_wall_missing_on_edge:lateral_east");
        if (!disjoint(p.wall, parcelCells)) errors.add("precinct_wall_overlaps_parcel");
        if (!disjoint(p.wall, streets)) errors.add("precinct_wall_overlaps_street");
        if (p.spiritWay.isEmpty()) {
            errors.add("precinct_spirit_way_empty");
        } else {
            if (!coreCells.containsAll(p.spiritWay)) errors.add("precinct_spirit_way_outside_civic_core");
            if (!disjoint(p.spiritWay, plan.spine)) errors.add("precinct_spirit_way_overlaps_spine");
        }
        // fringe separation: wall on each shared core<->fringe lateral edge
        Set<Cell> fringeCells = new HashSet<>();
        for (District d : plan.districts) {
            if (d.kind.equals("fringe")) fringeCells.addAll(d.cells);
        }
        Set<Cell> sharedWest = new HashSet<>();
        Set<Cell> sharedEast = new HashSet<>();
        for (int z = cz0; z <= cz1; z++) {
            if (fringeCells.contains(new Cell(cx0 - 1, z))) sharedWest.add(new Cell(cx0, z));
            if (fringeCells.contains(new Cell(cx1 + 1, z))) sharedEast.add(new Cell(cx1, z));
        }
        if (!sharedWest.isEmpty() && disjoint(p.wall, sharedWest)) errors.add("precinct_wall_missing_on_fringe_edge:fringe_west");
        if (!sharedEast.isEmpty() && disjoint(p.wall, sharedEast)) errors.add("precinct_wall_missing_on_fringe_edge:fringe_east");
        return errors;
    }

    // --- realization --------------------------------------------------------

    private static void placePerimeter(ServerLevel level, TownPlan plan, BuildStats stats) {
        for (Cell cell : plan.wall) {
            BlockPos pos = surfacePos(level, plan.base, cell.x, cell.z);
            place(level, pos, Blocks.STONE_BRICKS.defaultBlockState(), stats);
            for (int y = 1; y <= 3; y++) {
                place(level, pos.above(y), Blocks.STONE_BRICKS.defaultBlockState(), stats);
            }
            place(level, pos.above(4), Blocks.STONE_BRICK_SLAB.defaultBlockState(), stats);
        }
        for (Gate gate : plan.gates) {
            for (Cell cell : gate.cells) {
                BlockPos pos = surfacePos(level, plan.base, cell.x, cell.z);
                place(level, pos, Blocks.POLISHED_ANDESITE.defaultBlockState(), stats);
            }
        }
    }

    /**
     * 坊墙 precinct wall + side gates + 山门牌坊 precinct gate. The wall is a low
     * stone-brick run along the core's gate-facing and lateral edges; the spine
     * gate stays passable (those cells are spine, paved by the street pass) and
     * the side gates are framed openings. Mirrors the planner's precinct wall.
     */
    private static void placePrecinctWalls(ServerLevel level, TownPlan plan, BuildStats stats) {
        Precinct p = plan.precinct;
        for (Cell cell : p.wall) {
            BlockPos pos = surfacePos(level, plan.base, cell.x, cell.z);
            place(level, pos, Blocks.STONE_BRICKS.defaultBlockState(), stats);
            for (int y = 1; y <= 3; y++) {
                place(level, pos.above(y), Blocks.STONE_BRICKS.defaultBlockState(), stats);
            }
            place(level, pos.above(4), Blocks.STONE_BRICK_SLAB.defaultBlockState(), stats);
        }
        // Side gates: passable openings (polished-andesite threshold, cleared).
        for (Cell cell : p.sideGates) {
            BlockPos pos = surfacePos(level, plan.base, cell.x, cell.z);
            place(level, pos, Blocks.POLISHED_ANDESITE.defaultBlockState(), stats);
            clearHeadroom(level, pos.above(), stats);
        }
        // Precinct gate (山门牌坊) on the spine: paifang-style, stays passable.
        placePaifangRun(level, plan, p.gate, stats);
    }

    /**
     * 神道 spirit-way guardians: a dressed line of 灵兽/stele compositions
     * flanking the approach, reusing the lamp-post vocabulary plus a chiseled
     * stone guardian base so the band reads as a ceremonial guard line.
     */
    private static void placeSpiritWay(ServerLevel level, TownPlan plan, BuildStats stats) {
        for (Cell cell : plan.precinct.spiritWay) {
            BlockPos ground = surfacePos(level, plan.base, cell.x, cell.z);
            place(level, ground, Blocks.STONE_BRICKS.defaultBlockState(), stats);
            place(level, ground.above(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), stats);
            place(level, ground.above(2), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), stats);
            place(level, ground.above(3), Blocks.LANTERN.defaultBlockState(), stats);
        }
    }

    /**
     * 廊庑 colonnade: a covered walk (stone floor, dark-oak fence posts on a
     * deterministic cadence, spruce-slab roof line) along the lateral core
     * edges, enclosing the precinct.
     */
    private static void placeColonnade(ServerLevel level, TownPlan plan, BuildStats stats) {
        for (Cell cell : plan.precinct.colonnade) {
            BlockPos ground = surfacePos(level, plan.base, cell.x, cell.z);
            place(level, ground, Blocks.STONE_BRICKS.defaultBlockState(), stats);
            place(level, ground.above(3), Blocks.SPRUCE_SLAB.defaultBlockState(), stats);
            if ((cell.x + cell.z) % 3 == 0) {
                for (int y = 1; y <= 3; y++) {
                    place(level, ground.above(y), Blocks.DARK_OAK_FENCE.defaultBlockState(), stats);
                }
            }
        }
    }

    private static void placePaifangRun(ServerLevel level, TownPlan plan, Set<Cell> gate, BuildStats stats) {
        if (gate.isEmpty()) return;
        int z = gate.iterator().next().z;
        int minX = gate.stream().map(c -> c.x).min(Integer::compareTo).orElse(CENTER_X - 6);
        int maxX = gate.stream().map(c -> c.x).max(Integer::compareTo).orElse(CENTER_X + 6);
        for (int x = minX; x <= maxX; x++) {
            BlockPos ground = surfacePos(level, plan.base, x, z);
            place(level, ground, Blocks.POLISHED_ANDESITE.defaultBlockState(), stats);
            clearHeadroom(level, ground.above(), stats);
            if (x == minX || x == maxX || x == CENTER_X) {
                for (int y = 1; y <= 5; y++) {
                    place(level, ground.above(y), Blocks.DARK_OAK_LOG.defaultBlockState(), stats);
                }
            }
            if (x > minX && x < maxX) {
                place(level, ground.above(5), Blocks.DARK_OAK_SLAB.defaultBlockState(), stats);
            }
        }
    }

    private static void realizeParcels(ServerLevel level, TownPlan plan, RandomSource random, long seed, BuildStats stats) {
        for (Parcel parcel : plan.parcels) {
            TerrainFit fit = fitParcel(level, plan.base, parcel.bounds);
            if (fit.slope > MAX_SLOPE) {
                stats.skippedParcels++;
                stats.skippedParcelIds.add(parcel.id);
                continue;
            }
            // Block-built civic fill (e.g. the forecourt side halls, which are
            // narrower than any shipped template): realize as a compact hall.
            if (parcel.templateId.isEmpty()) {
                placeBlockBuiltHall(level, plan, parcel, fit, stats);
                continue;
            }
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, parcel.templateId);
            Optional<ModBlockFallback.LoadedTemplate> loadedTemplate = ModBlockFallback.loadTemplate(level, id);
            if (loadedTemplate.isEmpty()) {
                stats.skippedParcels++;
                stats.skippedParcelIds.add(parcel.id);
                continue;
            }
            StructureTemplate template = loadedTemplate.get().template();
            Vec3i size = template.getSize();
            int tw = size.getX();
            int td = size.getZ();
            Rect b = parcel.bounds;
            // Party-wall frontage placement: align to the frontage edge so a run
            // of parcels reads as one continuous shopfront wall. For frontage
            // parcels we lay the template at the edge (no centered plinth ring).
            int px;
            int pz;
            if (!parcel.frontageEdge.isEmpty()) {
                switch (parcel.frontageEdge) {
                    case "S" -> { px = b.x0; pz = b.z0; }
                    case "N" -> { px = b.x0; pz = b.z1 - td + 1; }
                    case "E" -> { px = b.x1 - tw + 1; pz = b.z0; }
                    case "W" -> { px = b.x0; pz = b.z0; }
                    default -> { px = b.x0; pz = b.z0; }
                }
            } else {
                px = b.x0 + Math.max(0, (b.width() - tw) / 2);
                pz = b.z0 + Math.max(0, (b.depth() - td) / 2);
            }
            BlockPos origin = new BlockPos(plan.base.getX() + px, fit.baseY - 1 - TEMPLATE_GROUND_LAYER, plan.base.getZ() + pz);
            BlockPos supportOrigin = new BlockPos(plan.base.getX() + px, fit.baseY - 1, plan.base.getZ() + pz);
            clearVolume(level, supportOrigin.above(), tw, size.getY() + 2, td, stats);
            // Continuous footprint support under the template only; frontage
            // parcels get no surrounding plinth ring so buildings meet the street.
            placeFootprintSupport(level, supportOrigin, tw, td, stats);
            boolean placed;
            if (!parcel.frontageEdge.isEmpty()) {
                long parcelSeed = seed ^ (parcel.id.hashCode() * 341873128712L);
                RandomSource localRand = RandomSource.create(parcelSeed);
                StructurePlaceSettings settings = new StructurePlaceSettings();
                switch (parcel.frontageEdge) {
                    case "N", "S" -> settings.setMirror(localRand.nextBoolean()
                            ? Mirror.LEFT_RIGHT : Mirror.NONE);
                    case "E", "W" -> settings.setMirror(localRand.nextBoolean()
                            ? Mirror.FRONT_BACK : Mirror.NONE);
                }
                placed = template.placeInWorld(
                        level, origin, origin, settings, random, BLOCK_FLAGS);
            } else {
                placed = template.placeInWorld(
                        level, origin, origin, new StructurePlaceSettings(), random, BLOCK_FLAGS);
            }
            if (placed) {
                stats.placedParcels++;
                stats.fallbackSubstitutions += loadedTemplate.get().substitutions();
                int localX0 = px;
                int localZ0 = pz;
                stats.parcelBaseY.put(parcel.id, fit.baseY);
                stats.parcelTemplateFootprints.put(
                        parcel.id,
                        rect(localX0, localZ0, localX0 + tw - 1, localZ0 + td - 1));
            } else {
                stats.skippedParcels++;
                stats.skippedParcelIds.add(parcel.id);
            }
        }
    }

    /**
     * Block-built civic hall for parcels narrower than any shipped template
     * (the 配殿 side halls): stone foundation, spruce walls, dark-oak corner
     * posts, and a slab roof. Realized within the parcel bounds so it stays
     * off the streets and reads as a compact flanking hall.
     */
    private static void placeBlockBuiltHall(ServerLevel level, TownPlan plan, Parcel parcel,
                                            TerrainFit fit, BuildStats stats) {
        Rect b = parcel.bounds;
        int baseY = fit.baseY;
        placeFootprintSupport(level,
                new BlockPos(plan.base.getX() + b.x0, baseY - 1, plan.base.getZ() + b.z0),
                b.width(), b.depth(), stats);
        for (int x = b.x0; x <= b.x1; x++) {
            for (int z = b.z0; z <= b.z1; z++) {
                boolean edge = (x == b.x0 || x == b.x1 || z == b.z0 || z == b.z1);
                BlockPos ground = new BlockPos(plan.base.getX() + x, baseY - 1, plan.base.getZ() + z);
                if (edge) {
                    for (int y = 0; y < 3; y++) {
                        place(level, ground.above(y), Blocks.SPRUCE_PLANKS.defaultBlockState(), stats);
                    }
                } else {
                    place(level, ground, Blocks.POLISHED_ANDESITE.defaultBlockState(), stats);
                    for (int y = 1; y < 3; y++) {
                        place(level, ground.above(y), Blocks.AIR.defaultBlockState(), stats);
                    }
                }
            }
        }
        for (int[] c : new int[][]{{b.x0, b.z0}, {b.x1, b.z0}, {b.x0, b.z1}, {b.x1, b.z1}}) {
            BlockPos post = new BlockPos(plan.base.getX() + c[0], baseY - 1, plan.base.getZ() + c[1]);
            for (int y = 0; y < 4; y++) {
                place(level, post.above(y), Blocks.DARK_OAK_LOG.defaultBlockState(), stats);
            }
        }
        for (int x = b.x0; x <= b.x1; x++) {
            for (int z = b.z0; z <= b.z1; z++) {
                BlockPos roof = new BlockPos(plan.base.getX() + x, baseY - 1, plan.base.getZ() + z);
                place(level, roof.above(3), Blocks.SPRUCE_SLAB.defaultBlockState(), stats);
            }
        }
        stats.placedParcels++;
        stats.parcelBaseY.put(parcel.id, baseY);
        stats.parcelTemplateFootprints.put(parcel.id, b.cells());
    }

    private static void placeStreetNetwork(ServerLevel level, TownPlan plan, BuildStats stats) {
        for (Cell cell : plan.spine) {
            BlockPos pos = surfacePos(level, plan.base, cell.x, cell.z);
            place(level, pos, Blocks.STONE_BRICKS.defaultBlockState(), stats);
            clearHeadroom(level, pos.above(), stats);
        }
        for (Cell cell : plan.lanes) {
            BlockPos pos = surfacePos(level, plan.base, cell.x, cell.z);
            place(level, pos, Blocks.COARSE_DIRT.defaultBlockState(), stats);
            clearHeadroom(level, pos.above(), stats);
        }
    }

    private static void placeRitualAxisFixtures(ServerLevel level, TownPlan plan, BuildStats stats) {
        Rect plaza = plan.ritualAxis.plaza;
        for (Cell cell : plaza.cells()) {
            BlockPos pos = surfacePos(level, plan.base, cell.x, cell.z);
            place(level, pos, Blocks.SMOOTH_STONE.defaultBlockState(), stats);
            clearHeadroom(level, pos.above(), stats);
        }
        int paifangZ = plan.ritualAxis.paifang.stream().map(c -> c.z).findFirst().orElse(plaza.z0 - 1);
        int minX = plan.ritualAxis.paifang.stream().map(c -> c.x).min(Integer::compareTo).orElse(CENTER_X - 6);
        int maxX = plan.ritualAxis.paifang.stream().map(c -> c.x).max(Integer::compareTo).orElse(CENTER_X + 6);
        for (int x = minX; x <= maxX; x++) {
            BlockPos ground = surfacePos(level, plan.base, x, paifangZ);
            place(level, ground, Blocks.POLISHED_ANDESITE.defaultBlockState(), stats);
            if (x == minX || x == maxX || x == CENTER_X) {
                for (int y = 1; y <= 5; y++) {
                    place(level, ground.above(y), Blocks.DARK_OAK_LOG.defaultBlockState(), stats);
                }
            }
            if (x >= minX + 1 && x <= maxX - 1) {
                place(level, ground.above(5), Blocks.DARK_OAK_SLAB.defaultBlockState(), stats);
            }
        }
        for (Cell lantern : plan.ritualAxis.lanterns) {
            placeLampPost(level, surfacePos(level, plan.base, lantern.x, lantern.z), stats);
        }
    }

    /**
     * Place cultivation street-facing dressing along realized frontage edges:
     * 幌子 shop banners at frontage corners and lamp posts spacing the row.
     * Themed cultivation vocabulary replaces the earlier bare lamp-only markers.
     */
    private static void placeFrontages(ServerLevel level, TownPlan plan, BuildStats stats) {
        Set<Cell> streets = plan.streetCells();
        for (Parcel parcel : plan.parcels) {
            if (parcel.frontageEdge.isEmpty()) continue;
            Integer baseY = stats.parcelBaseY.get(parcel.id);
            if (baseY == null) continue;
            List<Cell> edge = frontageEdge(parcel.bounds, parcel.frontageEdge);
            int placed = 0;
            for (Cell cell : edge) {
                if (!streets.contains(offset(cell, parcel.frontageEdge))) continue;
                if (placed % 6 == 0) {
                    placeLampPost(level, groundPos(plan.base, cell, baseY), stats);
                }
                // 幌子 shop banner at each frontage run corner
                if (placed == 0 || placed == edge.size() - 1) {
                    placeShopBanner(level, groundPos(plan.base, cell, baseY).above(),
                            parcel.frontageEdge, stats);
                }
                placed++;
            }
        }
    }

    /**
     * Furnish the market lanes with cultivation street life: 法器摊 artifact
     * stalls and 炼丹炉 alchemy furnaces, using profile-gated decor blocks.
     */
    private static void furnishStreetRooms(ServerLevel level, TownPlan plan, BuildStats stats) {
        Set<Cell> streets = plan.streetCells();
        for (int x = 8; x <= WIDTH - 9; x += 9) {
            Cell spot = new Cell(x, plan.laneSouthZ1 + 1);
            if (!streets.contains(spot)) continue;
            BlockPos ground = surfacePos(level, plan.base, spot.x, spot.z);
            if ((x / 9) % 2 == 0) {
                placeArtifactStall(level, ground, stats);
            } else {
                placeAlchemyFurnace(level, ground, stats);
            }
        }
    }

    /**
     * Dress open regions with cultivation planting beds: 药圃/灵田 spirit-fields
     * carry tended crop rows + moss + water channels; courtyard yards are left
     * as grounded tissue. Replaces placeholder farmland-only dressing.
     */
    private static void dressNegativeSpaces(ServerLevel level, TownPlan plan, BuildStats stats) {
        for (OpenRegion region : plan.openRegions) {
            Rect b = region.bounds;
            if (region.kind.equals("spirit_field")) {
                dressSpiritField(level, plan.base, region.cells, b, stats);
            } else if (region.kind.equals("courtyard_yard")) {
                dressCourtyardYard(level, plan, region, stats);
            }
        }
    }

    /**
     * 阵纹 formation floor pattern across the civic-core plaza: a polished
     * stone + redstone-lamp inlay reading as a cultivation formation array.
     */
    private static void placeFormationFloor(ServerLevel level, TownPlan plan, BuildStats stats) {
        Rect plaza = plan.ritualAxis.plaza;
        int cx = (plaza.x0 + plaza.x1) / 2;
        int cz = (plaza.z0 + plaza.z1) / 2;
        for (Cell cell : plaza.cells()) {
            BlockPos pos = surfacePos(level, plan.base, cell.x, cell.z);
            int ring = Math.max(Math.abs(cell.x - cx), Math.abs(cell.z - cz));
            BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
            if (ring == 3 || ring == 6) {
                floor = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            } else if ((cell.x - cx) * (cell.x - cx) + (cell.z - cz) * (cell.z - cz) <= 2) {
                floor = Blocks.REDSTONE_LAMP.defaultBlockState();
            }
            place(level, pos, floor, stats);
        }
    }

    private static void applySmokeLightAndWear(ServerLevel level, TownPlan plan, BuildStats stats) {
        for (Parcel parcel : plan.parcels) {
            Integer baseY = stats.parcelBaseY.get(parcel.id);
            if (baseY == null) continue;
            Cell detail = firstFreeParcelCell(parcel, plan, stats);
            if (detail == null) continue;
            BlockPos ground = groundPos(plan.base, detail, baseY);
            if (parcel.role.equals("housing") || parcel.dominant) {
                // cultivation accent: lantern post rather than placeholder campfire
                placeLampPost(level, ground, stats);
            }
        }
        for (Cell cell : plan.wall.stream().filter(c -> (c.x + c.z) % 17 == 0).toList()) {
            place(level, surfacePos(level, plan.base, cell.x, cell.z),
                    Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), stats);
        }
        for (Cell cell : plan.spine.stream().filter(c -> (c.x * 31 + c.z) % 29 == 0).toList()) {
            place(level, surfacePos(level, plan.base, cell.x, cell.z),
                    Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), stats);
        }
    }

    /** 幌子 shop banner on a timber post, facing the street. */
    private static void placeShopBanner(ServerLevel level, BlockPos at, String frontageEdge, BuildStats stats) {
        place(level, at, Blocks.DARK_OAK_FENCE.defaultBlockState(), stats);
        int rotation = switch (frontageEdge) {
            case "S" -> 8;   // facing north (toward town from south edge)
            case "N" -> 0;
            case "E" -> 12;
            case "W" -> 4;
            default -> 0;
        };
        BlockState banner = (level.random.nextInt(3) == 0 ? Blocks.RED_BANNER : Blocks.GREEN_BANNER)
                .defaultBlockState().setValue(BannerBlock.ROTATION, rotation);
        place(level, at.above(), banner, stats);
    }

    /** 法器摊 artifact stall: profile-gated display rack + lectern counter. */
    private static void placeArtifactStall(ServerLevel level, BlockPos ground, BuildStats stats) {
        // decor rack resolved through the mod fallback map: full uses the staged
        // fetzisdisplays rack, vanilla falls back to a barrel.
        BlockState rack = ModBlockFallback.resolveBlockState(
                ResourceLocation.fromNamespaceAndPath("fetzisdisplays", "dark_oak_vertical_rack_a"));
        place(level, ground, rack, stats);
        place(level, ground.above(), Blocks.LECTERN.defaultBlockState(), stats);
        place(level, ground.east(), Blocks.BARREL.defaultBlockState(), stats);
        placeLampPost(level, ground.west(), stats);
        stats.decorFixturesPlaced++;
    }

    /** 炼丹炉 alchemy furnace: blast furnace + cauldron + soul-lantern heat. */
    private static void placeAlchemyFurnace(ServerLevel level, BlockPos ground, BuildStats stats) {
        place(level, ground, Blocks.STONE_BRICKS.defaultBlockState(), stats);
        place(level, ground.above(), Blocks.BLAST_FURNACE.defaultBlockState(), stats);
        place(level, ground.east(), Blocks.CAULDRON.defaultBlockState(), stats);
        place(level, ground.above().east(), Blocks.SOUL_LANTERN.defaultBlockState(), stats);
        stats.decorFixturesPlaced++;
    }

    /** 药圃/灵田 spirit-field: tended crop rows + moss + water channels. */
    private static void dressSpiritField(ServerLevel level, BlockPos base, Set<Cell> cells,
                                         Rect b, BuildStats stats) {
        for (int x = b.x0; x <= b.x1; x++) {
            boolean channel = (x - b.x0) % 4 == 0;
            for (int z = b.z0; z <= b.z1; z++) {
                if (!cells.contains(new Cell(x, z))) continue;
                BlockPos pos = surfacePos(level, base, x, z);
                if (channel) {
                    place(level, pos, Blocks.WATER.defaultBlockState(), stats);
                    continue;
                }
                place(level, pos, Blocks.FARMLAND.defaultBlockState(), stats);
                BlockPos cropPos = pos.above();
                int r = (x * 31 + z * 17) % 7;
                BlockState crop;
                if (r == 0) {
                    place(level, cropPos, Blocks.MOSS_BLOCK.defaultBlockState(), stats);
                    continue;
                } else if (r <= 2) {
                    crop = Blocks.BEETROOTS.defaultBlockState().setValue(BeetrootBlock.AGE, 3);
                } else {
                    crop = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
                }
                place(level, cropPos, crop, stats);
            }
        }
    }

    /**
     * Dress a courtyard yard region with domestic props and an enclosing low
     * wall, turning leftover yard into lived-in 院落 tissue. Props respect
     * street and parcel footprint cells so circulation is never blocked.
     */
    private static void dressCourtyardYard(ServerLevel level, TownPlan plan, OpenRegion region, BuildStats stats) {
        Rect b = region.bounds;
        Set<Cell> streets = plan.streetCells();
        Set<Cell> allFootprints = new HashSet<>();
        for (Set<Cell> fp : stats.parcelTemplateFootprints.values()) allFootprints.addAll(fp);

        for (Cell cell : region.cells) {
            boolean onEdge = cell.x == b.x0 || cell.x == b.x1 || cell.z == b.z0 || cell.z == b.z1;
            if (!onEdge) continue;
            if (streets.contains(cell)) continue;
            BlockPos pos = surfacePos(level, plan.base, cell.x, cell.z);
            place(level, pos, Blocks.COBBLESTONE_WALL.defaultBlockState(), stats);
        }

        Set<Cell> blocked = new HashSet<>(streets);
        blocked.addAll(allFootprints);
        List<Cell> free = new ArrayList<>();
        for (Cell cell : region.cells) {
            if (!blocked.contains(cell) && cell.x > b.x0 && cell.x < b.x1 && cell.z > b.z0 && cell.z < b.z1) {
                free.add(cell);
            }
        }
        if (free.size() < 3) return;

        long yardSeed = region.id.hashCode();
        java.util.Collections.shuffle(free, new java.util.Random(yardSeed));
        int props = Math.min(free.size(), Math.max(2, free.size() / 8));
        for (int k = 0; k < props && k < free.size(); k++) {
            Cell cell = free.get(k);
            BlockPos ground = surfacePos(level, plan.base, cell.x, cell.z);
            placeCourtyardProp(level, ground, k, stats);
        }
    }

    /** Place one courtyard prop variant: well, planting plot, drying rack, woodpile, urn, or seating. */
    private static void placeCourtyardProp(ServerLevel level, BlockPos ground, int variant, BuildStats stats) {
        switch (Math.floorMod(variant, 6)) {
            case 0 -> { // well
                place(level, ground, Blocks.STONE_BRICKS.defaultBlockState(), stats);
                place(level, ground.above(), Blocks.CAULDRON.defaultBlockState(), stats);
            }
            case 1 -> { // planting plot
                place(level, ground, Blocks.FARMLAND.defaultBlockState(), stats);
                place(level, ground.above(), Blocks.BEETROOTS.defaultBlockState().setValue(BeetrootBlock.AGE, 2), stats);
            }
            case 2 -> { // drying rack
                place(level, ground, Blocks.OAK_FENCE.defaultBlockState(), stats);
                place(level, ground.above(), Blocks.OAK_FENCE.defaultBlockState(), stats);
                place(level, ground.above(2), Blocks.OAK_SLAB.defaultBlockState(), stats);
            }
            case 3 -> { // woodpile
                place(level, ground, Blocks.OAK_LOG.defaultBlockState(), stats);
                place(level, ground.above(), Blocks.OAK_LOG.defaultBlockState(), stats);
            }
            case 4 -> { // urn
                place(level, ground, Blocks.STONE_BRICK_SLAB.defaultBlockState(), stats);
                place(level, ground.above(), Blocks.FLOWER_POT.defaultBlockState(), stats);
            }
            case 5 -> { // seating
                BlockState stairs = Blocks.SPRUCE_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, net.minecraft.core.Direction.NORTH);
                place(level, ground, stairs, stats);
            }
        }
        stats.decorFixturesPlaced++;
    }

    /**
     * Line the main-street spine with a 坊市 streetscape (stalls, banner/lantern
     * poles, carts, crates) at the spine edges, keeping the central walking
     * width clear. Density follows market-to-lane falloff: higher near plaza.
     */
    private static void dressSpineStreetscape(ServerLevel level, TownPlan plan, BuildStats stats) {
        Set<Cell> spine = plan.spine;
        int plazaZ0 = plan.ritualAxis.plaza.z0;
        int minZ = spine.stream().mapToInt(c -> c.z).min().orElse(0);
        int maxZ = Math.max(1, spine.stream().mapToInt(c -> c.z).max().orElse(DEPTH));
        int zRange = Math.max(1, maxZ - minZ);

        for (Cell cell : spine) {
            if (cell.x != plan.spineX0 && cell.x != plan.spineX1) continue;
            int distToPlaza = Math.abs(cell.z - plazaZ0);
            float falloff = 1.0f - Math.min(1.0f, (float) distToPlaza / (float) zRange);
            int spacing = Math.max(3, (int) (10 - 6 * falloff));
            if ((cell.x + cell.z) % spacing != 0) continue;

            BlockPos ground = surfacePos(level, plan.base, cell.x, cell.z);
            if (!plan.streetCells().contains(new Cell(
                    cell.x + (cell.x == plan.spineX0 ? -1 : 1), cell.z))) continue;
            placeSpineProp(level, ground, (cell.x * 31 + cell.z * 17) % 4, stats);
        }
    }

    /** Place one spine streetscape prop: stall, banner pole, cart, or crate stack. */
    private static void placeSpineProp(ServerLevel level, BlockPos ground, int variant, BuildStats stats) {
        switch (Math.floorMod(variant, 4)) {
            case 0 -> { // market stall: awning post + slab roof
                place(level, ground, Blocks.OAK_FENCE.defaultBlockState(), stats);
                place(level, ground.above(), Blocks.OAK_FENCE.defaultBlockState(), stats);
                place(level, ground.above(2), Blocks.WHITE_CARPET.defaultBlockState(), stats);
            }
            case 1 -> { // banner pole
                placeLampPost(level, ground, stats);
                place(level, ground.above(3), (level.random.nextInt(3) == 0
                        ? Blocks.RED_BANNER : Blocks.GREEN_BANNER).defaultBlockState(), stats);
            }
            case 2 -> { // cart
                place(level, ground, Blocks.OAK_PLANKS.defaultBlockState(), stats);
                place(level, ground.above(), Blocks.CHEST.defaultBlockState(), stats);
            }
            case 3 -> { // crate stack
                place(level, ground, Blocks.BARREL.defaultBlockState(), stats);
                place(level, ground.above(), Blocks.BARREL.defaultBlockState(), stats);
            }
        }
        stats.decorFixturesPlaced++;
    }

    /**
     * Place inhabitants across districts: villagers (and occasional 灵狐 spirit
     * foxes) scaled to the town's parcel count so the town reads as occupied.
     */
    private static void placeInhabitants(ServerLevel level, TownPlan plan, RandomSource random, BuildStats stats) {
        int parcelCount = Math.max(1, plan.parcels.size());
        // roughly one inhabitant per placed parcel, capped to keep the fair lively
        int targetVillagers = Math.min(parcelCount, 48);
        int targetBeasts = Math.max(1, parcelCount / 16);
        int placedVillagers = 0;
        int placedBeasts = 0;
        java.util.List<Parcel> shuffled = new java.util.ArrayList<>(plan.parcels);
        java.util.Collections.shuffle(shuffled, new java.util.Random(random.nextLong()));
        for (Parcel parcel : shuffled) {
            if (placedVillagers >= targetVillagers && placedBeasts >= targetBeasts) break;
            Integer baseY = stats.parcelBaseY.get(parcel.id);
            if (baseY == null) continue;
            Cell spot = firstFreeParcelCell(parcel, plan, stats);
            if (spot == null) continue;
            BlockPos at = groundPos(plan.base, spot, baseY).above();
            if (placedVillagers < targetVillagers) {
                spawnVillager(level, at);
                placedVillagers++;
                stats.inhabitantsPlaced++;
            }
            if (placedBeasts < targetBeasts && random.nextInt(4) == 0) {
                spawnSpiritFox(level, at.east());
                placedBeasts++;
                stats.inhabitantsPlaced++;
            }
        }
    }

    private static void spawnVillager(ServerLevel level, BlockPos at) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) return;
        villager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                level.random.nextFloat() * 360.0F, 0.0F);
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(at),
                MobSpawnType.COMMAND, null);
        level.addFreshEntity(villager);
    }

    private static void spawnSpiritFox(ServerLevel level, BlockPos at) {
        Fox fox = EntityType.FOX.create(level);
        if (fox == null) return;
        fox.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                level.random.nextFloat() * 360.0F, 0.0F);
        fox.finalizeSpawn(level, level.getCurrentDifficultyAt(at),
                MobSpawnType.COMMAND, null);
        level.addFreshEntity(fox);
    }

    private static void placeLampPost(ServerLevel level, BlockPos ground, BuildStats stats) {
        place(level, ground, Blocks.STONE_BRICKS.defaultBlockState(), stats);
        place(level, ground.above(), Blocks.OAK_FENCE.defaultBlockState(), stats);
        place(level, ground.above(2), Blocks.LANTERN.defaultBlockState(), stats);
    }

    private static void placeFootprintSupport(
            ServerLevel level, BlockPos origin, int width, int depth, BuildStats stats) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                place(level, origin.offset(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState(), stats);
            }
        }
    }

    private static TerrainFit fitParcel(ServerLevel level, BlockPos base, Rect bounds) {
        List<Integer> samples = List.of(
                surfaceY(level, base, bounds.x0, bounds.z0),
                surfaceY(level, base, bounds.x1, bounds.z0),
                surfaceY(level, base, bounds.x0, bounds.z1),
                surfaceY(level, base, bounds.x1, bounds.z1),
                surfaceY(level, base, (bounds.x0 + bounds.x1) / 2, (bounds.z0 + bounds.z1) / 2));
        int min = samples.stream().min(Integer::compareTo).orElse(base.getY());
        int max = samples.stream().max(Integer::compareTo).orElse(base.getY());
        return new TerrainFit(max, max - min);
    }

    private static List<Cell> frontageEdge(Rect bounds, String side) {
        List<Cell> cells = new ArrayList<>();
        if (side.equals("E") || side.equals("W")) {
            int x = side.equals("E") ? bounds.x1 : bounds.x0;
            for (int z = bounds.z0 + 1; z <= bounds.z1 - 1; z++) cells.add(new Cell(x, z));
        } else {
            int z = side.equals("S") ? bounds.z0 : bounds.z1;
            for (int x = bounds.x0 + 1; x <= bounds.x1 - 1; x++) cells.add(new Cell(x, z));
        }
        return cells;
    }

    private static Cell firstFreeParcelCell(Parcel parcel, TownPlan plan, BuildStats stats) {
        return freeParcelCells(parcel, plan, stats).stream()
                .min((a, b) -> Integer.compare(a.x + a.z, b.x + b.z))
                .orElse(null);
    }

    private static Cell nextFreeParcelCell(Parcel parcel, TownPlan plan, BuildStats stats, Cell used) {
        return freeParcelCells(parcel, plan, stats).stream()
                .filter(c -> !c.equals(used))
                .min((a, b) -> Integer.compare(Math.abs(a.x - used.x) + Math.abs(a.z - used.z),
                        Math.abs(b.x - used.x) + Math.abs(b.z - used.z)))
                .orElse(null);
    }

    private static Set<Cell> freeParcelCells(Parcel parcel, TownPlan plan, BuildStats stats) {
        Set<Cell> blocked = new HashSet<>(plan.streetCells());
        blocked.addAll(stats.parcelTemplateFootprints.getOrDefault(parcel.id, Set.of()));
        Set<Cell> free = parcel.bounds.cells();
        free.removeAll(blocked);
        return free;
    }

    private static Cell offset(Cell cell, String side) {
        return switch (side) {
            case "S" -> new Cell(cell.x, cell.z - 1);
            case "N" -> new Cell(cell.x, cell.z + 1);
            case "E" -> new Cell(cell.x + 1, cell.z);
            case "W" -> new Cell(cell.x - 1, cell.z);
            default -> cell;
        };
    }

    private static BlockPos groundPos(BlockPos base, Cell cell, int baseY) {
        return new BlockPos(base.getX() + cell.x, baseY - 1, base.getZ() + cell.z);
    }

    private static BlockPos surfacePos(ServerLevel level, BlockPos base, int localX, int localZ) {
        int x = base.getX() + localX;
        int z = base.getZ() + localZ;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    }

    private static int surfaceY(ServerLevel level, BlockPos base, int localX, int localZ) {
        int x = base.getX() + localX;
        int z = base.getZ() + localZ;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static void clearHeadroom(ServerLevel level, BlockPos pos, BuildStats stats) {
        for (int y = 0; y < 3; y++) {
            place(level, pos.above(y), Blocks.AIR.defaultBlockState(), stats);
        }
    }

    private static void clearVolume(ServerLevel level, BlockPos origin, int width, int height, int depth, BuildStats stats) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    place(level, origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), stats);
                }
            }
        }
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state, BuildStats stats) {
        level.setBlock(pos, state, BLOCK_FLAGS);
        stats.blocksPlaced++;
    }

    private static int centerX(long seed) {
        return WIDTH / 2 + TownHash.range64(seed, "cx", -CENTER_X_JITTER, CENTER_X_JITTER);
    }

    private static int[][] laneBands(long seed) {
        int south = TownHash.range64(seed, "lane_s", 0, LANE_JITTER);
        int middle = TownHash.range64(seed, "lane_m", -LANE_JITTER, LANE_JITTER);
        int north = TownHash.range64(seed, "lane_n", -LANE_JITTER, LANE_JITTER);
        return new int[][]{{16 + south, 18 + south},
                {60 + middle, 62 + middle}, {108 + north, 110 + north}};
    }

    private static Map<String, Integer> districtWidthJitters(long seed) {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (String key : new String[]{"south_res_w", "south_res_e", "market_w", "market_e",
                "mid_res_w", "mid_res_e", "fringe_w", "fringe_e"}) {
            out.put(key, TownHash.range64(seed, "district_width_" + key,
                    -DISTRICT_WIDTH_JITTER, DISTRICT_WIDTH_JITTER));
        }
        return out;
    }

    private static ShapeSelection selectPerimeterShape(long seed) {
        return new ShapeSelection(TownHash.pick(seed, "family", PERIMETER_FAMILIES),
                TownHash.pick(seed, "modifier", PERIMETER_MODIFIERS));
    }

    private static Set<Cell> gateBand(int cx) {
        return rect(Math.max(0, cx - GATE_RUN_HALF), 0,
                Math.min(WIDTH - 1, cx + GATE_RUN_HALF), GATE_BAND_DEPTH);
    }

    private static Set<Cell> familyInterior(long seed, String family) {
        Set<Cell> out = new HashSet<>();
        int cx = WIDTH / 2, cz = DEPTH / 2;
        if (family.equals("square")) return rect(0, 0, WIDTH - 1, DEPTH - 1);
        if (family.equals("circle")) {
            int r = Math.min(WIDTH, DEPTH) / 2 - CIRCLE_MARGIN;
            long r2 = (long) r * r;
            for (int x = Math.max(0, cx-r); x <= Math.min(WIDTH-1, cx+r); x++)
                for (int z = Math.max(0, cz-r); z <= Math.min(DEPTH-1, cz+r); z++)
                    if ((long)(x-cx)*(x-cx) + (long)(z-cz)*(z-cz) <= r2) out.add(new Cell(x,z));
            out.addAll(gateBand(cx));
        } else if (family.equals("oval")) {
            int rx = TownHash.range64(seed, "oval_rx", OVAL_RX_MIN, OVAL_RX_MAX);
            int rz = TownHash.range64(seed, "oval_rz", OVAL_RZ_MIN, OVAL_RZ_MAX);
            cz = rz + 1;
            long rx2=(long)rx*rx, rz2=(long)rz*rz, rhs=rx2*rz2;
            for (int x=Math.max(0,cx-rx); x<=Math.min(WIDTH-1,cx+rx); x++)
                for (int z=Math.max(0,cz-rz); z<=Math.min(DEPTH-1,cz+rz); z++)
                    if (rz2*(long)(x-cx)*(x-cx)+rx2*(long)(z-cz)*(z-cz)<=rhs) out.add(new Cell(x,z));
            out.addAll(gateBand(cx));
        } else if (family.equals("dshape")) {
            int r=WIDTH/2-CIRCLE_MARGIN, mid=DEPTH/2; long r2=(long)r*r;
            out.addAll(rect(0,0,WIDTH-1,mid));
            for(int x=Math.max(0,cx-r);x<=Math.min(WIDTH-1,cx+r);x++)
                for(int z=mid;z<=Math.min(DEPTH-1,mid+r);z++)
                    if((long)(x-cx)*(x-cx)+(long)(z-mid)*(z-mid)<=r2) out.add(new Cell(x,z));
        } else if (family.equals("octagon")) {
            out.addAll(rect(0,0,WIDTH-1,DEPTH-1));
            for(int x=0;x<OCTAGON_K;x++) for(int z=0;z<OCTAGON_K;z++) if(x+z<OCTAGON_K) {
                out.remove(new Cell(x,z)); out.remove(new Cell(WIDTH-1-x,z));
                out.remove(new Cell(x,DEPTH-1-z)); out.remove(new Cell(WIDTH-1-x,DEPTH-1-z));
            }
        } else if (family.equals("trapezoid")) {
            out.addAll(rect(0,0,WIDTH-1,DEPTH-1));
            for(int z=0;z<DEPTH;z++) for(int x=0;x<(TRAPEZOID_SLANT*z)/Math.max(1,DEPTH-1);x++) {
                out.remove(new Cell(x,z)); out.remove(new Cell(WIDTH-1-x,z));
            }
        }
        return out;
    }

    private static Set<Cell> modifierBitten(String modifier) {
        Set<Cell> out = new HashSet<>(); int cx=WIDTH/2;
        if (modifier.equals("barbican")) {
            int x0=cx+BARBICAN_OFFSET, x1=Math.min(WIDTH-1,x0+BARBICAN_WIDTH-1);
            out.addAll(rect(x0,0,x1,BARBICAN_DEPTH));
        } else if (modifier.equals("bastion")) {
            int z0=DEPTH/2-BASTION_HALF_W,z1=DEPTH/2+BASTION_HALF_W;
            out.addAll(rect(0,z0,BASTION_DEPTH-1,z1));
            out.addAll(rect(WIDTH-BASTION_DEPTH,z0,WIDTH-1,z1));
        }
        return out;
    }

    private static Set<Cell> perimeterInterior(long seed, ShapeSelection shape, Rect protectedCore) {
        Set<Cell> interior = familyInterior(seed, shape.family);
        interior.removeAll(modifierBitten(shape.modifier));
        interior.addAll(protectedCore.cells());
        return interior;
    }

    private static Set<Cell> boundary(long seed, ShapeSelection shape, Rect protectedCore) {
        Set<Cell> interior = perimeterInterior(seed, shape, protectedCore);
        Set<Cell> perimeter = new HashSet<>();
        for (Cell c : interior) {
            if (!interior.contains(new Cell(c.x + 1, c.z))
                    || !interior.contains(new Cell(c.x - 1, c.z))
                    || !interior.contains(new Cell(c.x, c.z + 1))
                    || !interior.contains(new Cell(c.x, c.z - 1))) {
                perimeter.add(c);
            }
        }
        return perimeter;
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

    private static boolean disjoint(Set<Cell> a, Set<Cell> b) {
        for (Cell cell : a) {
            if (b.contains(cell)) {
                return false;
            }
        }
        return true;
    }

    private static boolean touches(Set<Cell> cells, Set<Cell> targets) {
        for (Cell cell : cells) {
            if (targets.contains(cell)
                    || targets.contains(new Cell(cell.x + 1, cell.z))
                    || targets.contains(new Cell(cell.x - 1, cell.z))
                    || targets.contains(new Cell(cell.x, cell.z + 1))
                    || targets.contains(new Cell(cell.x, cell.z - 1))) {
                return true;
            }
        }
        return false;
    }

    private static Set<Cell> reachableFrom(Cell start, Set<Cell> cells) {
        Set<Cell> seen = new HashSet<>();
        if (!cells.contains(start)) {
            return seen;
        }
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            Cell cell = queue.removeFirst();
            for (Cell next : List.of(
                    new Cell(cell.x + 1, cell.z),
                    new Cell(cell.x - 1, cell.z),
                    new Cell(cell.x, cell.z + 1),
                    new Cell(cell.x, cell.z - 1))) {
                if (cells.contains(next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    // --- records & value types ---------------------------------------------

    private record Cell(int x, int z) {
    }

    private record Rect(int x0, int z0, int x1, int z1) {
        int width() {
            return x1 - x0 + 1;
        }

        int depth() {
            return z1 - z0 + 1;
        }

        Set<Cell> cells() {
            return rect(x0, z0, x1, z1);
        }
    }

    private record Gate(String id, String side, Set<Cell> cells) {
    }

    private record District(String kind, Rect bounds, Set<Cell> cells, int density,
                            int storeyMin, int storeyMax, String material, String rosterHead) {
        District withCells(Set<Cell> replacement) {
            return new District(kind, bounds, Set.copyOf(replacement), density,
                    storeyMin, storeyMax, material, rosterHead);
        }
        String id() {
            return kind + "_" + bounds.x0 + "_" + bounds.z0 + "_" + bounds.x1 + "_" + bounds.z1;
        }
    }

    private record Parcel(String id, String role, Rect bounds, int importance, boolean dominant,
                          String templateId, String districtKind, String materialRegister,
                          int storeyHint, String frontageEdge) {
    }

    private record OpenRegion(String id, String kind, Rect bounds, int densityRank,
                              Set<Cell> cells) {
        OpenRegion(String id, String kind, Rect bounds, int densityRank) {
            this(id, kind, bounds, densityRank, bounds.cells());
        }
    }

    private record RitualAxis(String fromGate, String terminusParcel, Rect plaza,
                              Set<Cell> paifang, Set<Cell> lanterns) {
    }

    private record Precinct(Set<Cell> gate, Set<Cell> spiritWay, Set<Cell> colonnade,
                            Set<Cell> wall, Set<Cell> sideGates) {
    }

    private record TerrainFit(int baseY, int slope) {
    }

    private record ShapeSelection(String family, String modifier) {
    }

    private record TownPlan(long seed, BlockPos base, Set<Cell> perimeter, Set<Cell> wall, List<Gate> gates,
                            Set<Cell> spine, Set<Cell> lanes, List<Parcel> parcels,
                            List<OpenRegion> openRegions, List<OpenRegion> alleys,
                            List<District> districts, RitualAxis ritualAxis, Precinct precinct,
                            String shapeFamily, String shapeModifier, int centerX,
                            int spineX0, int spineX1, int laneSouthZ1) {
        Set<Cell> streetCells() {
            Set<Cell> out = new HashSet<>(spine);
            out.addAll(lanes);
            return out;
        }
    }

    private static final class BuildStats {
        int placedParcels;
        int skippedParcels;
        int blocksPlaced;
        int fallbackSubstitutions;
        int decorFixturesPlaced;
        int inhabitantsPlaced;
        final Map<String, Integer> parcelBaseY = new java.util.HashMap<>();
        final Map<String, Set<Cell>> parcelTemplateFootprints = new java.util.HashMap<>();
        final List<String> skippedParcelIds = new ArrayList<>();
    }
}
