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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class TownGenerator {
    private static final int WIDTH = 96;
    private static final int DEPTH = 80;
    private static final int CENTER_X = WIDTH / 2;
    private static final int SPINE_HALF_WIDTH = 3;
    private static final int MAX_SLOPE = 5;
    private static final int MAX_FOOTPRINT_AXIS = 96;
    private static final int TEMPLATE_GROUND_LAYER = 1;
    private static final int BLOCK_FLAGS = Block.UPDATE_CLIENTS;

    private TownGenerator() {
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
        if (!loaded(level, base, WIDTH, DEPTH)) {
            source.sendFailure(Component.literal(
                    "Town footprint " + WIDTH + "x" + DEPTH + " spans unloaded chunks from "
                            + base.getX() + "," + base.getZ() + " to "
                            + (base.getX() + WIDTH - 1) + "," + (base.getZ() + DEPTH - 1)));
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
        placePerimeter(level, plan, stats);
        realizeParcels(level, plan, templateRandom, stats);
        placeStreetNetwork(level, plan, stats);
        placeRitualAxisFixtures(level, plan, stats);
        placeFrontages(level, plan, stats);
        furnishStreetRooms(level, plan, stats);
        dressNegativeSpaces(level, plan, stats);
        applySmokeLightAndWear(level, plan, stats);

        source.sendSuccess(
                () -> Component.literal("Generated living town seed=" + seed
                        + " footprint=" + WIDTH + "x" + DEPTH
                        + " placed=" + stats.placedParcels
                        + " skipped=" + stats.skippedParcels
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

    private static boolean loaded(ServerLevel level, BlockPos base, int width, int depth) {
        int minChunkX = base.getX() >> 4;
        int maxChunkX = (base.getX() + width - 1) >> 4;
        int minChunkZ = base.getZ() >> 4;
        int maxChunkZ = (base.getZ() + depth - 1) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static TownPlan plan(long seed, BlockPos base) {
        int laneZ = DEPTH / 2 - 2;
        int shrineWidth = 27;
        int shrineDepth = 20;
        int shrineX0 = CENTER_X - shrineWidth / 2;
        int shrineX1 = shrineX0 + shrineWidth - 1;
        int shrineZ1 = DEPTH - 3;
        int shrineZ0 = shrineZ1 - shrineDepth + 1;
        Rect plaza = new Rect(CENTER_X - 16, shrineZ0 - 9, CENTER_X + 16, shrineZ0 - 1);
        Set<Cell> paifang = rect(CENTER_X - 6, plaza.z0 - 1, CENTER_X + 6, plaza.z0 - 1);
        Set<Cell> lanterns = new HashSet<>();
        for (int z = 8; z < plaza.z0 - 2; z += 5) {
            lanterns.add(new Cell(CENTER_X - 5, z));
            lanterns.add(new Cell(CENTER_X + 5, z));
        }
        List<Gate> gates = List.of(
                new Gate("south_gate", "south", rect(CENTER_X - 2, 0, CENTER_X + 2, 0)));
        Set<Cell> perimeter = boundary();
        Set<Cell> wall = new HashSet<>(perimeter);
        for (Gate gate : gates) {
            wall.removeAll(gate.cells());
        }
        Set<Cell> spine = rect(CENTER_X - SPINE_HALF_WIDTH, 0, CENTER_X + SPINE_HALF_WIDTH, plaza.z1);
        spine.addAll(plaza.cells());
        spine.addAll(paifang);
        Set<Cell> lanes = new HashSet<>();
        lanes.addAll(rect(8, laneZ - 1, WIDTH - 9, laneZ + 1));
        lanes.addAll(rect(8, 16, WIDTH - 9, 18));
        lanes.addAll(rect(8, shrineZ0 - 1, WIDTH - 9, shrineZ0 - 1));
        lanes.removeAll(spine);

        List<Parcel> parcels = new ArrayList<>();
        parcels.add(new Parcel("town_shrine", "civic", new Rect(shrineX0, shrineZ0, shrineX1, shrineZ1),
                3, true, "town_shrine_001"));
        parcels.add(new Parcel("west_core_shop", "market", new Rect(20, 20, 42, laneZ - 2),
                2, false, "cultivation_shop_002"));
        parcels.add(new Parcel("east_core_shop", "market", new Rect(WIDTH - 42, 20, WIDTH - 20, laneZ - 2),
                2, false, "cultivation_shop_003"));
        parcels.add(new Parcel("west_market", "market", new Rect(12, laneZ + 2, 31, shrineZ0 - 2),
                2, false, "cultivation_market_001"));
        parcels.add(new Parcel("east_market", "market", new Rect(WIDTH - 31, laneZ + 2, WIDTH - 9, shrineZ0 - 2),
                2, false, "cultivation_market_001"));
        parcels.add(new Parcel("west_outer_south", "housing", new Rect(16, 1, 36, 15),
                1, false, "cultivation_house_001"));
        parcels.add(new Parcel("east_outer_south", "housing", new Rect(WIDTH - 36, 1, WIDTH - 16, 15),
                1, false, "cultivation_house_002"));
        parcels.add(new Parcel("west_outer_north", "housing", new Rect(8, shrineZ0, 31, shrineZ1 - 1),
                1, false, "cultivation_house_003"));
        parcels.add(new Parcel("east_outer_north", "defense", new Rect(WIDTH - 31, shrineZ0, WIDTH - 9, shrineZ1 - 1),
                1, false, "cultivation_market_002"));

        List<OpenRegion> openRegions = List.of(
                new OpenRegion("market_mouth_square", "market_square",
                        new Rect(CENTER_X - 16, laneZ + 2, CENTER_X - 5, Math.min(laneZ + 7, plaza.z0 - 1)), 3),
                new OpenRegion("well_court", "well_plaza", new Rect(8, laneZ - 11, 16, laneZ - 5), 2),
                new OpenRegion("back_lane_yard", "domestic_yard",
                        new Rect(WIDTH - 18, laneZ - 13, WIDTH - 9, laneZ - 7), 1));
        RitualAxis ritualAxis = new RitualAxis("south_gate", "town_shrine", plaza, paifang, lanterns);
        return new TownPlan(base, perimeter, wall, gates, spine, lanes, parcels, openRegions, ritualAxis);
    }

    private static List<String> validatePlan(TownPlan plan) {
        List<String> errors = new ArrayList<>();
        if (!plan.perimeter.equals(boundary())) {
            errors.add("perimeter_not_closed");
        }
        for (Gate gate : plan.gates) {
            if (!plan.perimeter.containsAll(gate.cells())) {
                errors.add("gate_off_wall:" + gate.id());
            }
        }
        long landmarks = plan.parcels.stream().filter(Parcel::dominant).count();
        if (landmarks != 1) {
            errors.add("dominant_landmark_count:" + landmarks);
        }
        Optional<Parcel> shrine = plan.parcels.stream().filter(p -> p.id.equals("town_shrine")).findFirst();
        if (shrine.isEmpty()) {
            errors.add("missing_town_shrine_anchor");
        } else {
            Parcel shrineParcel = shrine.get();
            if (!shrineParcel.dominant || shrineParcel.importance != 3) {
                errors.add("town_shrine_not_dominant_top_tier");
            }
            if (!plan.ritualAxis.terminusParcel.equals("town_shrine")) {
                errors.add("ritual_axis_wrong_terminus:" + plan.ritualAxis.terminusParcel);
            }
            Set<Cell> shrineFront = rect(
                    shrineParcel.bounds.x0,
                    shrineParcel.bounds.z0 - 1,
                    shrineParcel.bounds.x1,
                    shrineParcel.bounds.z0 - 1);
            if (disjoint(shrineFront, plan.ritualAxis.plaza.cells())) {
                errors.add("town_shrine_not_fronted_by_plaza");
            }
        }
        if (!plan.spine.containsAll(plan.ritualAxis.paifang)) {
            errors.add("paifang_not_on_axis");
        }
        if (plan.ritualAxis.lanterns.size() < 4) {
            errors.add("lantern_approach_too_sparse");
        }
        Set<Cell> streets = plan.streetCells();
        Set<Cell> parcelCells = new HashSet<>();
        for (Parcel parcel : plan.parcels) {
            Set<Cell> cells = parcel.bounds.cells();
            if (!parcelCells.addAll(cells)) {
                errors.add("parcel_overlap:" + parcel.id);
            }
            if (!disjoint(cells, streets)) {
                errors.add("parcel_street_overlap:" + parcel.id);
            }
            if (!touches(cells, streets)) {
                errors.add("parcel_not_reachable_from_spine:" + parcel.id);
            }
        }
        for (OpenRegion region : plan.openRegions) {
            Set<Cell> cells = region.bounds.cells();
            if (!disjoint(cells, streets)) {
                errors.add("negative_space_street_overlap:" + region.id);
            }
            if (!disjoint(cells, parcelCells)) {
                errors.add("negative_space_parcel_overlap:" + region.id);
            }
        }
        Set<Cell> reachable = reachableFrom(new Cell(CENTER_X, DEPTH / 2), streets);
        if (!reachable.containsAll(streets)) {
            errors.add("street_network_disconnected");
        }
        return errors;
    }

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
            for (Cell cell : gate.cells()) {
                BlockPos pos = surfacePos(level, plan.base, cell.x, cell.z);
                place(level, pos, Blocks.POLISHED_ANDESITE.defaultBlockState(), stats);
            }
        }
    }

    private static void realizeParcels(ServerLevel level, TownPlan plan, RandomSource random, BuildStats stats) {
        for (Parcel parcel : plan.parcels) {
            TerrainFit fit = fitParcel(level, plan.base, parcel.bounds);
            if (fit.slope > MAX_SLOPE) {
                stats.skippedParcels++;
                continue;
            }
            placePlinth(level, plan.base, parcel.bounds, fit.baseY, stats);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, parcel.templateId);
            Optional<ModBlockFallback.LoadedTemplate> loadedTemplate = ModBlockFallback.loadTemplate(level, id);
            if (loadedTemplate.isEmpty()) {
                stats.skippedParcels++;
                continue;
            }
            StructureTemplate template = loadedTemplate.get().template();
            Vec3i size = template.getSize();
            int px = plan.base.getX() + parcel.bounds.x0 + Math.max(0, (parcel.bounds.width() - size.getX()) / 2);
            int pz = plan.base.getZ() + parcel.bounds.z0 + Math.max(0, (parcel.bounds.depth() - size.getZ()) / 2);
            BlockPos origin = new BlockPos(px, fit.baseY - 1 - TEMPLATE_GROUND_LAYER, pz);
            BlockPos supportOrigin = new BlockPos(px, fit.baseY - 1, pz);
            clearVolume(level, supportOrigin.above(), size.getX(), size.getY() + 2, size.getZ(), stats);
            placeFootprintSupport(level, supportOrigin, size.getX(), size.getZ(), stats);
            boolean placed = template.placeInWorld(
                    level,
                    origin,
                    origin,
                    new StructurePlaceSettings(),
                    random,
                    BLOCK_FLAGS);
            if (placed) {
                stats.placedParcels++;
                stats.fallbackSubstitutions += loadedTemplate.get().substitutions();
                int localX0 = px - plan.base.getX();
                int localZ0 = pz - plan.base.getZ();
                stats.parcelBaseY.put(parcel.id, fit.baseY);
                stats.parcelTemplateFootprints.put(
                        parcel.id,
                        rect(localX0, localZ0, localX0 + size.getX() - 1, localZ0 + size.getZ() - 1));
            } else {
                stats.skippedParcels++;
            }
        }
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
        placeStepHints(level, plan, stats);
    }

    private static void placeRitualAxisFixtures(ServerLevel level, TownPlan plan, BuildStats stats) {
        Rect plaza = plan.ritualAxis.plaza;
        for (Cell cell : plaza.cells()) {
            BlockPos pos = surfacePos(level, plan.base, cell.x, cell.z);
            place(level, pos, Blocks.SMOOTH_STONE.defaultBlockState(), stats);
            clearHeadroom(level, pos.above(), stats);
        }
        int paifangZ = plan.ritualAxis.paifang.stream()
                .map(c -> c.z)
                .findFirst()
                .orElse(plaza.z0 - 1);
        int minX = plan.ritualAxis.paifang.stream()
                .map(c -> c.x)
                .min(Integer::compareTo)
                .orElse(CENTER_X - 6);
        int maxX = plan.ritualAxis.paifang.stream()
                .map(c -> c.x)
                .max(Integer::compareTo)
                .orElse(CENTER_X + 6);
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

    private static void placeStepHints(ServerLevel level, TownPlan plan, BuildStats stats) {
        List<Cell> ordered = plan.spine.stream()
                .filter(c -> c.x == CENTER_X)
                .sorted(Comparator.comparingInt(c -> c.z))
                .toList();
        for (int i = 1; i < ordered.size(); i++) {
            Cell prev = ordered.get(i - 1);
            Cell cur = ordered.get(i);
            BlockPos prevPos = surfacePos(level, plan.base, prev.x, prev.z);
            BlockPos curPos = surfacePos(level, plan.base, cur.x, cur.z);
            int dy = curPos.getY() - prevPos.getY();
            if (Math.abs(dy) == 1) {
                Direction facing = dy > 0 ? Direction.SOUTH : Direction.NORTH;
                BlockState stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, facing);
                place(level, dy > 0 ? prevPos.above() : curPos.above(), stair, stats);
            }
        }
    }

    private static void placeFrontages(ServerLevel level, TownPlan plan, BuildStats stats) {
        Set<Cell> streets = plan.streetCells();
        for (Parcel parcel : plan.parcels) {
            Integer baseY = stats.parcelBaseY.get(parcel.id);
            if (baseY == null) {
                continue;
            }
            Direction side = streetFacingSide(parcel, plan.streetCells());
            List<Cell> edge = frontageEdge(parcel.bounds, side);
            int placed = 0;
            for (Cell cell : edge) {
                Cell streetCell = offset(cell, side);
                if (!streets.contains(streetCell)) {
                    continue;
                }
                BlockPos ground = groundPos(plan.base, streetCell, baseY);
                if (placed % 4 == 0) {
                    BlockPos pos = ground.above();
                    place(level, pos, Blocks.BARREL.defaultBlockState(), stats);
                    place(level, pos.above(), Blocks.DARK_OAK_SLAB.defaultBlockState(), stats);
                } else if (placed % 5 == 0) {
                    placeLampPost(level, ground, stats);
                }
                placed++;
            }
        }
    }

    private static void furnishStreetRooms(ServerLevel level, TownPlan plan, BuildStats stats) {
        int laneZ = plan.centralLaneZ();
        Set<Cell> streets = plan.streetCells();
        for (int x = CENTER_X - SPINE_HALF_WIDTH - 13; x <= CENTER_X - SPINE_HALF_WIDTH - 5; x += 4) {
            placeStreetStall(level, plan, new Cell(x, laneZ + 1), streets, stats);
        }
        for (int x = CENTER_X + SPINE_HALF_WIDTH + 5; x <= CENTER_X + SPINE_HALF_WIDTH + 13; x += 4) {
            placeStreetStall(level, plan, new Cell(x, laneZ + 1), streets, stats);
        }
        for (int z = 8; z < DEPTH - 8; z += 10) {
            int x = ((z / 10) % 2 == 0) ? CENTER_X - SPINE_HALF_WIDTH : CENTER_X + SPINE_HALF_WIDTH;
            Cell lamp = new Cell(x, z);
            if (streets.contains(lamp)) {
                placeLampPost(level, surfacePos(level, plan.base, lamp.x, lamp.z), stats);
            }
        }
    }

    private static void dressNegativeSpaces(ServerLevel level, TownPlan plan, BuildStats stats) {
        for (OpenRegion region : plan.openRegions) {
            Rect b = region.bounds;
            if (region.kind.equals("well_plaza")) {
                int cx = (b.x0 + b.x1) / 2;
                int cz = (b.z0 + b.z1) / 2;
                BlockPos pos = surfacePos(level, plan.base, cx, cz);
                place(level, pos, Blocks.CAULDRON.defaultBlockState(), stats);
                for (Cell cell : rect(cx - 1, cz - 1, cx + 1, cz + 1)) {
                    if (cell.x != cx || cell.z != cz) {
                        place(level, surfacePos(level, plan.base, cell.x, cell.z),
                                Blocks.STONE_BRICKS.defaultBlockState(), stats);
                    }
                }
            } else if (region.kind.equals("market_square")) {
                placeStall(level, plan.base, b.x0 + 1, b.z0 + 1, stats);
                placeStall(level, plan.base, b.x1 - 3, b.z1 - 2, stats);
                place(level, surfacePos(level, plan.base, b.x0 + 4, b.z1 - 1).above(),
                        Blocks.LANTERN.defaultBlockState(), stats);
            } else {
                for (int x = b.x0; x <= b.x1; x += 2) {
                    place(level, surfacePos(level, plan.base, x, b.z0),
                            Blocks.PODZOL.defaultBlockState(), stats);
                }
                place(level, surfacePos(level, plan.base, b.x0 + 1, b.z1).above(),
                        Blocks.OAK_LOG.defaultBlockState(), stats);
                place(level, surfacePos(level, plan.base, b.x0 + 2, b.z1).above(),
                        Blocks.OAK_LOG.defaultBlockState(), stats);
                place(level, surfacePos(level, plan.base, b.x1 - 1, b.z0 + 1).above(),
                        Blocks.WHITE_WOOL.defaultBlockState(), stats);
            }
        }
    }

    private static void applySmokeLightAndWear(ServerLevel level, TownPlan plan, BuildStats stats) {
        for (Parcel parcel : plan.parcels) {
            Integer baseY = stats.parcelBaseY.get(parcel.id);
            if (baseY == null) {
                continue;
            }
            Cell detail = firstFreeParcelCell(parcel, plan, stats);
            if (detail == null) {
                continue;
            }
            BlockPos ground = groundPos(plan.base, detail, baseY);
            if (parcel.role.equals("housing")) {
                placeGroundCampfire(level, ground, stats);
                Cell lampCell = nextFreeParcelCell(parcel, plan, stats, detail);
                if (lampCell != null) {
                    placeLampPost(level, groundPos(plan.base, lampCell, baseY), stats);
                }
            } else if (parcel.dominant) {
                placeGroundCampfire(level, ground, stats);
                Cell lampCell = nextFreeParcelCell(parcel, plan, stats, detail);
                if (lampCell != null) {
                    placeLampPost(level, groundPos(plan.base, lampCell, baseY), stats);
                }
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

    private static void placeStall(ServerLevel level, BlockPos base, int x, int z, BuildStats stats) {
        BlockPos pos = surfacePos(level, base, x, z);
        place(level, pos.above(), Blocks.BARREL.defaultBlockState(), stats);
        place(level, pos.above(2), Blocks.DARK_OAK_SLAB.defaultBlockState(), stats);
        place(level, pos.east().above(), Blocks.OAK_FENCE.defaultBlockState(), stats);
    }

    private static void placeStreetStall(
            ServerLevel level, TownPlan plan, Cell cell, Set<Cell> streets, BuildStats stats) {
        if (!streets.contains(cell) || !streets.contains(new Cell(cell.x + 1, cell.z))) {
            return;
        }
        BlockPos ground = surfacePos(level, plan.base, cell.x, cell.z);
        place(level, ground.above(), Blocks.BARREL.defaultBlockState(), stats);
        place(level, ground.above(2), Blocks.DARK_OAK_SLAB.defaultBlockState(), stats);
        place(level, surfacePos(level, plan.base, cell.x + 1, cell.z).above(),
                Blocks.OAK_FENCE.defaultBlockState(), stats);
    }

    private static void placeGroundCampfire(ServerLevel level, BlockPos ground, BuildStats stats) {
        place(level, ground, Blocks.STONE_BRICKS.defaultBlockState(), stats);
        place(level, ground.above(), Blocks.CAMPFIRE.defaultBlockState(), stats);
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

    private static void placePlinth(ServerLevel level, BlockPos base, Rect bounds, int baseY, BuildStats stats) {
        for (int x = bounds.x0; x <= bounds.x1; x++) {
            for (int z = bounds.z0; z <= bounds.z1; z++) {
                int surface = surfaceY(level, base, x, z);
                for (int y = surface - 1; y < baseY - 1; y++) {
                    place(level, new BlockPos(base.getX() + x, y, base.getZ() + z),
                            Blocks.STONE_BRICKS.defaultBlockState(), stats);
                }
            }
        }
    }

    private static Direction streetFacingSide(Parcel parcel, Set<Cell> streets) {
        for (Direction direction : List.of(Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH)) {
            if (touches(frontageCells(parcel.bounds, direction), streets)) {
                return direction;
            }
        }
        return parcel.bounds.centerX() < CENTER_X ? Direction.EAST : Direction.WEST;
    }

    private static List<Cell> frontageEdge(Rect bounds, Direction side) {
        List<Cell> cells = new ArrayList<>();
        if (side == Direction.EAST || side == Direction.WEST) {
            int x = side == Direction.EAST ? bounds.x1 : bounds.x0;
            for (int z = bounds.z0 + 1; z <= bounds.z1 - 1; z++) {
                cells.add(new Cell(x, z));
            }
        } else {
            int z = side == Direction.SOUTH ? bounds.z1 : bounds.z0;
            for (int x = bounds.x0 + 1; x <= bounds.x1 - 1; x++) {
                cells.add(new Cell(x, z));
            }
        }
        return cells;
    }

    private static Set<Cell> frontageCells(Rect bounds, Direction side) {
        return new HashSet<>(frontageEdge(bounds, side));
    }

    private static Cell firstFreeParcelCell(Parcel parcel, TownPlan plan, BuildStats stats) {
        return freeParcelCells(parcel, plan, stats).stream()
                .min(Comparator.comparingInt(c -> c.x + c.z))
                .orElse(null);
    }

    private static Cell nextFreeParcelCell(Parcel parcel, TownPlan plan, BuildStats stats, Cell used) {
        return freeParcelCells(parcel, plan, stats).stream()
                .filter(c -> !c.equals(used))
                .min(Comparator.comparingInt(c -> Math.abs(c.x - used.x) + Math.abs(c.z - used.z)))
                .orElse(null);
    }

    private static Set<Cell> freeParcelCells(Parcel parcel, TownPlan plan, BuildStats stats) {
        Set<Cell> blocked = new HashSet<>(plan.streetCells());
        blocked.addAll(stats.parcelTemplateFootprints.getOrDefault(parcel.id, Set.of()));
        Set<Cell> free = parcel.bounds.cells();
        free.removeAll(blocked);
        return free;
    }

    private static Cell offset(Cell cell, Direction direction) {
        return switch (direction) {
            case EAST -> new Cell(cell.x + 1, cell.z);
            case WEST -> new Cell(cell.x - 1, cell.z);
            case SOUTH -> new Cell(cell.x, cell.z + 1);
            case NORTH -> new Cell(cell.x, cell.z - 1);
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

    private static Set<Cell> boundary() {
        Set<Cell> cells = new HashSet<>();
        cells.addAll(rect(0, 0, WIDTH - 1, 0));
        cells.addAll(rect(0, DEPTH - 1, WIDTH - 1, DEPTH - 1));
        cells.addAll(rect(0, 0, 0, DEPTH - 1));
        cells.addAll(rect(WIDTH - 1, 0, WIDTH - 1, DEPTH - 1));
        return cells;
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

    private record Cell(int x, int z) {
    }

    private record Rect(int x0, int z0, int x1, int z1) {
        int width() {
            return x1 - x0 + 1;
        }

        int depth() {
            return z1 - z0 + 1;
        }

        int centerX() {
            return (x0 + x1) / 2;
        }

        Set<Cell> cells() {
            return rect(x0, z0, x1, z1);
        }
    }

    private record Gate(String id, String side, Set<Cell> cells) {
    }

    private record Parcel(String id, String role, Rect bounds, int importance,
                          boolean dominant, String templateId) {
    }

    private record OpenRegion(String id, String kind, Rect bounds, int densityRank) {
    }

    private record RitualAxis(String fromGate, String terminusParcel, Rect plaza,
                              Set<Cell> paifang, Set<Cell> lanterns) {
    }

    private record TerrainFit(int baseY, int slope) {
    }

    private record TownPlan(BlockPos base, Set<Cell> perimeter, Set<Cell> wall, List<Gate> gates,
                            Set<Cell> spine, Set<Cell> lanes, List<Parcel> parcels,
                            List<OpenRegion> openRegions, RitualAxis ritualAxis) {
        Set<Cell> streetCells() {
            Set<Cell> out = new HashSet<>(spine);
            out.addAll(lanes);
            return out;
        }

        int centralLaneZ() {
            return lanes.stream()
                    .filter(c -> c.z > DEPTH / 2 - 4 && c.z < DEPTH / 2 + 4)
                    .map(c -> c.z)
                    .findFirst()
                    .orElse(DEPTH / 2);
        }
    }

    private static final class BuildStats {
        int placedParcels;
        int skippedParcels;
        int blocksPlaced;
        int fallbackSubstitutions;
        Map<String, Integer> parcelBaseY = new HashMap<>();
        Map<String, Set<Cell>> parcelTemplateFootprints = new HashMap<>();
    }
}
