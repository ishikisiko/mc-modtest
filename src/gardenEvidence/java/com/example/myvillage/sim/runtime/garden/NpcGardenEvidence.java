package com.example.myvillage.sim.runtime.garden;

import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.block.SpiritCropBlock;
import com.example.myvillage.cultivation.time.CultivationCalendarSavedData;
import com.example.myvillage.entity.npc.CultivatorEntity;
import com.example.myvillage.region.runtime.RegionRuntimeService;
import com.example.myvillage.sim.PersonView;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.life.LifePlace;
import com.example.myvillage.sim.life.LifeResidentView;
import com.example.myvillage.sim.life.LifeSite;
import com.example.myvillage.sim.profession.ProfessionSupplyView;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.avatar.WorldSimAvatars;
import com.example.myvillage.sim.runtime.life.NpcGardenRuntime;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in dedicated-server fixture. Never package this subscriber in a release jar. */
@EventBusSubscriber(modid = "myvillage")
public final class NpcGardenEvidence {
    private static final String HERB = "myvillage:spirit_leaf";
    private static final String SITE = "native_garden_evidence";
    private static final String PLOT = "plot";
    private static final String[] INITIAL = {"fixture", "source_gather", "source_delivery", "native_plant", "native_tend", "save_boundary"};
    private static final String[] RESTART = {"reload_identity", "foreign_change_rejected", "mob_griefing_rejected",
            "observer_withdrawal", "native_harvest", "no_duplicate"};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final boolean ENABLED = "1".equals(System.getenv("MYVILLAGE_NPC_GARDEN_EVIDENCE"));
    private static final String PHASE = System.getenv().getOrDefault("MYVILLAGE_NPC_GARDEN_PHASE", "initial");
    private static final Path OUT = Path.of(System.getenv().getOrDefault("MYVILLAGE_NPC_GARDEN_OUT", "../reports/npc-garden-native"));
    private static final String SOURCE = System.getenv().getOrDefault("MYVILLAGE_NPC_GARDEN_SOURCE", "unspecified");
    private static final List<Map<String, Object>> CHECKS = new ArrayList<>();
    private static final List<String> COMPLETED = new ArrayList<>();
    private static final List<List<Integer>> WALK = new ArrayList<>();
    private static final String[] SELECTED = PHASE.equals("restart") ? RESTART : INITIAL;
    private static String state = "init";
    private static boolean finished;
    private static int ticks;
    private static int deadline;
    private static int childId;
    private static int growerId;
    private static BlockPos home;
    private static BlockPos plot;
    private static ServerPlayer observer;
    private static int deliveredStock;
    private static int plantingXp;
    private static int growerXp;
    private static long firstSerial;
    private static int harvestStock;
    private static boolean sawGardenMoving;
    private static boolean sawGardenWorking;
    private static boolean sawGrowerMoving;
    private static boolean sawGrowerWorking;
    private static boolean sawHarvestMoving;
    private static boolean sawHarvestWorking;
    private static JsonObject restartManifest;

    private NpcGardenEvidence() { }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (!ENABLED || finished) return;
        MinecraftServer server = event.getServer();
        // A FakePlayer added to ServerLevel is deliberately not represented as a networked
        // PlayerList login. Feed one explicitly identified fixture tick through the existing
        // saved calendar, rather than silently claiming a real connected player advanced it.
        var calendar = CultivationCalendarSavedData.get(server.overworld());
        calendar.incrementSaturated();
        if (++ticks % 200 == 0) calendar.checkpoint();
        if (ticks < 60) return;
        try {
            WorldSim sim = WorldSimRuntime.sim().orElseThrow(() -> new IllegalStateException("world_sim_inactive"));
            if (ticks % 5 == 0) sample(server, sim);
            if (ticks > deadline && deadline > 0) throw new AssertionError("timeout state=" + state + " tick=" + ticks);
            if (PHASE.equals("restart")) restart(server, sim);
            else if (PHASE.equals("initial")) initial(server, sim);
            else throw new IllegalArgumentException("unknown phase " + PHASE);
        } catch (Throwable failure) {
            finish(server, failure);
        }
    }

    private static void initial(MinecraftServer server, WorldSim sim) throws Exception {
        ServerLevel level = server.overworld();
        switch (state) {
            case "init" -> {
                Files.createDirectories(OUT);
                check("new_world_has_no_probe_site", sim.lifeSite(SITE).isEmpty(), sim.lifeSites().stream().map(LifeSite::id).toList());
                // High isolated platform is a fixture, not a claimed natural settlement.
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, server);
                level.setDayTime(1000);
                var selection = RegionRuntimeService.graph().orElseThrow().regions().stream()
                        .map(region -> sim.livingIn(region.id()).stream()
                                .filter(p -> p.alive() && p.sectId() < 0 && p.rank().equals("rogue")
                                        && !p.status().equals("travelling") && !p.status().equals("secluded")
                                        && sim.life(p.id()).isEmpty()).limit(2).toList())
                        .filter(people -> people.size() == 2).findFirst().orElse(List.of());
                check("two_existing_local_people", selection.size() == 2, selection);
                PersonView child = selection.get(0), grower = selection.get(1);
                check("same_region_people", child.regionId().equals(grower.regionId()), selection);
                childId = child.id(); growerId = grower.id();
                var region = RegionRuntimeService.graph().orElseThrow().regionById(child.regionId());
                int[] center = com.example.myvillage.region.runtime.RegionPlacement.worldBlockFromGraph(region.posX(), region.posZ());
                home = new BlockPos(center[0] + 4, 201, center[1] + 4);
                plot = home.offset(14, 0, 0);
                loadFixtureChunks(level);
                platform(level, home, plot);
                check("fixture_matches_people_region", RegionRuntimeService.regionAt(home.getX(), home.getZ())
                        .filter(child.regionId()::equals).isPresent(), child.regionId());
                var places = List.of(LifePlace.free("home_child", "rest", home.getX(), home.getY(), home.getZ()),
                        LifePlace.free("home_grower", "rest", home.getX() + 2, home.getY(), home.getZ()),
                        LifePlace.free("gather", "gather", home.getX() + 8, home.getY(), home.getZ() - 2),
                        LifePlace.free("market", "market", home.getX() + 5, home.getY(), home.getZ() + 2),
                        LifePlace.free(PLOT, "garden", plot.getX() - 1, plot.getY(), plot.getZ()));
                check("register_site", sim.registerLifeSite(new LifeSite(SITE, "原生药圃证据", "minecraft:overworld",
                        child.regionId(), home.getX(), home.getY(), home.getZ(), places)), selection);
                for (LifePlace place : places) sim.setLifePlaceAvailability(SITE, place.id(), "available");
                check("register_finite_source", sim.registerHerbGathering(SITE, "gather", HERB, 2), HERB);
                check("bind_real_crop", NpcGardenRuntime.bind(level, sim, SITE, PLOT, HERB, plot), plot);
                check("assign_child", sim.staffProfession(childId, SITE, "herb_child", "home_child", "gather"), childId);
                observer = player(level);
                observer.moveTo(home.getX() + 1.5, home.getY(), home.getZ() + 1.5, 0, 0);
                level.addNewPlayer(observer);
                check("fixture_observer_level_only", level.players().contains(observer), observer.getUUID());
                check("fixture_crop_air", level.getBlockState(plot).isAir(), level.getBlockState(plot));
                done("fixture");
                state = "gather"; deadline = ticks + 3600;
                dirty(server);
            }
            case "gather" -> {
                var child = sim.life(childId).orElseThrow();
                if (sim.profession(childId).orElseThrow().gatheringExperience() > 0 && child.herbs() >= 2) {
                    check("source_from_native_gather", child.completedActivities() > 0 && child.herbs() >= 2,
                            Map.of("resident", child, "profession", sim.profession(childId).orElseThrow()));
                    done("source_gather"); state = "deliver"; deadline = ticks + 1600;
                }
            }
            case "deliver" -> {
                var supply = sim.professionSupply(SITE).orElseThrow();
                var child = sim.life(childId).orElseThrow();
                if (supply.herbs().getOrDefault(HERB, 0) >= 2 && child.herbs() == 0) {
                    deliveredStock = supply.herbs().get(HERB);
                    plantingXp = sim.profession(childId).orElseThrow().plantingExperience();
                    check("typed_seed_delivered", deliveredStock >= sim.professionRules().seedUnits(),
                            Map.of("supply", supply, "resident", child));
                    done("source_delivery"); state = "plant"; deadline = ticks + 2000;
                }
            }
            case "plant" -> {
                var crop = level.getBlockState(plot);
                if (crop.is(ModBlocks.SPIRIT_LEAF_CROP.get()) && crop.getValue(SpiritCropBlock.AGE) == 0) {
                    var supply = sim.professionSupply(SITE).orElseThrow();
                    var child = sim.life(childId).orElseThrow();
                    check("actual_path_to_garden_before_plant", sawGardenMoving && sawGardenWorking && WALK.size() >= 3,
                            Map.of("walk", WALK, "resident", child));
                    check("plant_spent_delivered_seed", supply.herbs().getOrDefault(HERB, 0)
                            == deliveredStock - sim.professionRules().seedUnits(), supply);
                    check("plant_xp_once", sim.profession(childId).orElseThrow().plantingExperience()
                            == plantingXp + sim.professionRules().plantingExperience(), sim.profession(childId).orElseThrow());
                    check("crop_age_zero", crop.getValue(SpiritCropBlock.AGE) == 0, crop);
                    done("native_plant");
                    // A second existing person now takes the bounded tending role. The child
                    // remains in the same site, so plot capacity arbitration is also exercised.
                    check("assign_grower", sim.staffProfession(growerId, SITE, "spirit_grower", "home_grower", PLOT), growerId);
                    growerXp = sim.profession(growerId).orElseThrow().plantingExperience();
                    state = "tend"; deadline = ticks + 2400;
                    dirty(server);
                }
            }
            case "tend" -> {
                var crop = level.getBlockState(plot);
                var garden = garden(sim);
                if (crop.is(ModBlocks.SPIRIT_LEAF_CROP.get()) && crop.getValue(SpiritCropBlock.AGE) == 1 && garden.tended()) {
                    check("grower_walked_to_tend", sawGrowerMoving && sawGrowerWorking,
                            Map.of("walk", WALK, "resident", sim.life(growerId).orElseThrow()));
                    check("grower_tend_xp_once", sim.profession(growerId).orElseThrow().plantingExperience()
                            == growerXp + sim.professionRules().plantingExperience(), sim.profession(growerId).orElseThrow());
                    check("tend_did_not_credit_harvest", garden.harvestSerial() == 0, garden);
                    done("native_tend"); state = "save"; deadline = ticks + 80;
                    dirty(server);
                }
            }
            case "save" -> {
                if (ticks % 20 != 0) return;
                var manifest = new LinkedHashMap<String, Object>();
                manifest.put("source", SOURCE); manifest.put("world_seed", level.getSeed());
                manifest.put("child_id", childId); manifest.put("grower_id", growerId);
                manifest.put("home", List.of(home.getX(), home.getY(), home.getZ()));
                manifest.put("plot", List.of(plot.getX(), plot.getY(), plot.getZ()));
                manifest.put("crop_age", level.getBlockState(plot).getValue(SpiritCropBlock.AGE));
                manifest.put("garden", garden(sim));
                manifest.put("supply", sim.professionSupply(SITE).orElseThrow());
                manifest.put("child", sim.life(childId).orElseThrow());
                manifest.put("grower", sim.life(growerId).orElseThrow());
                manifest.put("child_profession", sim.profession(childId).orElseThrow());
                manifest.put("grower_profession", sim.profession(growerId).orElseThrow());
                manifest.put("calendar_ticks", CultivationCalendarSavedData.get(level).elapsedCalendarTicks());
                Files.writeString(OUT.resolve("restart-manifest.json"), GSON.toJson(manifest));
                check("saved_manifest_has_one_unharvested_batch", garden(sim).harvestSerial() == 0
                        && level.getBlockState(plot).getValue(SpiritCropBlock.AGE) == 1, manifest);
                done("save_boundary"); dirty(server); finish(server, null);
            }
            default -> throw new IllegalStateException(state);
        }
    }

    private static void restart(MinecraftServer server, WorldSim sim) throws Exception {
        ServerLevel level = server.overworld();
        switch (state) {
            case "init" -> {
                restartManifest = JsonParser.parseString(Files.readString(OUT.resolve("restart-manifest.json"))).getAsJsonObject();
                check("same_source_after_restart", SOURCE.equals(restartManifest.get("source").getAsString()), SOURCE);
                childId = restartManifest.get("child_id").getAsInt();
                growerId = restartManifest.get("grower_id").getAsInt();
                var h = restartManifest.getAsJsonArray("home");
                var p = restartManifest.getAsJsonArray("plot");
                home = new BlockPos(h.get(0).getAsInt(), h.get(1).getAsInt(), h.get(2).getAsInt());
                plot = new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
                loadFixtureChunks(level);
                level.getChunk(plot);
                awayPlatform(level);
                var site = sim.lifeSite(SITE).orElseThrow(() -> new AssertionError("site_missing_after_restart"));
                var bound = garden(sim);
                check("binding_survived_restart", bound.physical() && bound.cropX() == plot.getX()
                        && bound.cropY() == plot.getY() && bound.cropZ() == plot.getZ()
                        && bound.herb().equals(HERB), bound);
                check("same_people_after_restart", sim.life(childId).isPresent() && sim.life(growerId).isPresent()
                        && site.regionId().equals(sim.person(childId).orElseThrow().regionId()), site);
                firstSerial = restartManifest.getAsJsonObject("garden").get("harvestSerial").getAsLong();
                check("serial_survived_restart", bound.harvestSerial() == firstSerial, bound);
                check("actual_crop_survived_restart", level.getBlockState(plot).is(ModBlocks.SPIRIT_LEAF_CROP.get())
                        && level.getBlockState(plot).getValue(SpiritCropBlock.AGE) == 1, level.getBlockState(plot));
                check("inventory_and_xp_survived_restart",
                        sim.professionSupply(SITE).orElseThrow().herbs().equals(parseHerbs(restartManifest.getAsJsonObject("supply")))
                        && sim.profession(childId).orElseThrow().plantingExperience()
                            == restartManifest.getAsJsonObject("child_profession").get("plantingExperience").getAsInt()
                        && sim.profession(growerId).orElseThrow().plantingExperience()
                            == restartManifest.getAsJsonObject("grower_profession").get("plantingExperience").getAsInt(),
                        Map.of("supply", sim.professionSupply(SITE).orElseThrow(),
                                "child", sim.profession(childId).orElseThrow(), "grower", sim.profession(growerId).orElseThrow()));
                // Explicit fixture: no random crop growth, fixed daylight, and no fake player
                // inserted in PlayerList. The saved cultivation calendar is advanced above.
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, server);
                level.setDayTime(1000);
                observer = player(level);
                observer.moveTo(home.getX() + 1.5, home.getY(), home.getZ() + 1.5, 0, 0);
                level.addNewPlayer(observer);
                done("reload_identity");
                // Simulate a player changing the crop before an NPC harvest plan completes.
                level.setBlock(plot, Blocks.STONE.defaultBlockState(), 3);
                harvestStock = sim.professionSupply(SITE).orElseThrow().herbs().getOrDefault(HERB, 0);
                growerXp = sim.profession(growerId).orElseThrow().plantingExperience();
                plantingXp = sim.profession(childId).orElseThrow().plantingExperience();
                state = "foreign"; deadline = ticks + 440;
            }
            case "foreign" -> {
                if (ticks < deadline - 30) return;
                check("foreign_block_not_overwritten", level.getBlockState(plot).is(Blocks.STONE), level.getBlockState(plot));
                check("external_change_no_reward", noReward(sim), sim.professionSupply(SITE).orElseThrow());
                done("foreign_change_rejected");
                level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, server);
                // Another admin fixture restores a real age-2 plant. The runtime must reject
                // mutating it while mobGriefing is false; it must never infer maturity from time.
                level.setBlock(plot, ModBlocks.SPIRIT_LEAF_CROP.get().defaultBlockState().setValue(SpiritCropBlock.AGE, 2), 3);
                state = "griefing"; deadline = ticks + 440;
            }
            case "griefing" -> {
                if (ticks < deadline - 30) return;
                check("mob_griefing_false_preserves_mature_crop", level.getBlockState(plot).is(ModBlocks.SPIRIT_LEAF_CROP.get())
                        && level.getBlockState(plot).getValue(SpiritCropBlock.AGE) == 2, level.getBlockState(plot));
                check("mob_griefing_false_no_reward", noReward(sim), sim.professionSupply(SITE).orElseThrow());
                done("mob_griefing_rejected");
                observer.moveTo(home.getX() + 128.5, home.getY(), home.getZ() + 0.5, 0, 0);
                level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, server);
                state = "withdraw"; deadline = ticks + 180;
            }
            case "withdraw" -> {
                if (ticks < deadline - 30) return;
                var child = sim.life(childId).orElseThrow();
                var grower = sim.life(growerId).orElseThrow();
                check("observer_away_withdraws_bodies", !child.embodied() && !grower.embodied()
                        && !child.observed() && !grower.observed(), Map.of("child", child, "grower", grower));
                check("away_mature_crop_no_reward", noReward(sim) && garden(sim).harvestSerial() == firstSerial
                        && level.getBlockState(plot).getValue(SpiritCropBlock.AGE) == 2, sim.professionSupply(SITE).orElseThrow());
                done("observer_withdrawal");
                observer.moveTo(home.getX() + 1.5, home.getY(), home.getZ() + 1.5, 0, 0);
                state = "harvest"; deadline = ticks + 2000;
            }
            case "harvest" -> {
                var bound = garden(sim);
                if (bound.harvestSerial() == firstSerial + 1) {
                    check("harvest_has_managed_body_at_station", sawHarvestWorking && managedAtStand(level, sim),
                            Map.of("walk", WALK, "child", sim.life(childId).orElseThrow(), "grower", sim.life(growerId).orElseThrow()));
                    check("real_harvest_removed_crop", level.getBlockState(plot).isAir(), level.getBlockState(plot));
                    int stock = sim.professionSupply(SITE).orElseThrow().herbs().getOrDefault(HERB, 0);
                    check("real_loot_credited_to_warehouse", stock > harvestStock, Map.of("before", harvestStock, "after", stock));
                    check("real_loot_not_double_dropped", level.getEntitiesOfClass(ItemEntity.class,
                            new AABB(plot).inflate(5)).isEmpty(), level.getEntitiesOfClass(ItemEntity.class, new AABB(plot).inflate(5)));
                    check("harvest_xp_once", sim.profession(childId).orElseThrow().plantingExperience() > plantingXp
                            || sim.profession(growerId).orElseThrow().plantingExperience() > growerXp,
                            Map.of("child", sim.profession(childId).orElseThrow(), "grower", sim.profession(growerId).orElseThrow()));
                    done("native_harvest");
                    harvestStock = stock;
                    plantingXp = sim.profession(childId).orElseThrow().plantingExperience();
                    growerXp = sim.profession(growerId).orElseThrow().plantingExperience();
                    level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, server);
                    state = "no_duplicate"; deadline = ticks + 320;
                    dirty(server);
                }
            }
            case "no_duplicate" -> {
                if (ticks < deadline - 30) return;
                check("serial_and_reward_once", garden(sim).harvestSerial() == firstSerial + 1
                        && sim.professionSupply(SITE).orElseThrow().herbs().getOrDefault(HERB, 0) == harvestStock
                        && sim.profession(childId).orElseThrow().plantingExperience() == plantingXp
                        && sim.profession(growerId).orElseThrow().plantingExperience() == growerXp,
                        Map.of("garden", garden(sim), "supply", sim.professionSupply(SITE).orElseThrow()));
                done("no_duplicate"); finish(server, null);
            }
            default -> throw new IllegalStateException(state);
        }
    }

    private static Map<String, Integer> parseHerbs(JsonObject supply) {
        var out = new LinkedHashMap<String, Integer>();
        supply.getAsJsonObject("herbs").entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsInt()));
        return out;
    }

    private static boolean noReward(WorldSim sim) {
        return sim.professionSupply(SITE).orElseThrow().herbs().getOrDefault(HERB, 0) == harvestStock
                && sim.profession(childId).orElseThrow().plantingExperience() == plantingXp
                && sim.profession(growerId).orElseThrow().plantingExperience() == growerXp;
    }

    private static void platform(ServerLevel level, BlockPos home, BlockPos crop) {
        for (int x = home.getX() - 3; x <= crop.getX() + 3; x++)
            for (int z = home.getZ() - 4; z <= home.getZ() + 4; z++) {
                level.getChunk(new BlockPos(x, home.getY(), z));
                level.setBlock(new BlockPos(x, home.getY() - 1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, home.getY(), z), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, home.getY() + 1, z), Blocks.AIR.defaultBlockState(), 3);
            }
        level.setBlock(crop.below(), ModBlocks.SPIRIT_SOIL.get().defaultBlockState(), 3);
        level.setChunkForced(home.getX() >> 4, home.getZ() >> 4, true);
        level.setChunkForced(crop.getX() >> 4, crop.getZ() >> 4, true);
    }

    /** Bounded fixture chunks allow the normal loaded-cache pathfinder; restart never rebuilds blocks. */
    private static void loadFixtureChunks(ServerLevel level) {
        for (int cx = Math.floorDiv(home.getX() - 48, 16); cx <= Math.floorDiv(plot.getX() + 48, 16); cx++)
            for (int cz = Math.floorDiv(home.getZ() - 48, 16); cz <= Math.floorDiv(home.getZ() + 48, 16); cz++) {
                level.getChunk(cx, cz);
                level.setChunkForced(cx, cz, true);
            }
    }

    private static ServerPlayer player(ServerLevel level) {
        var profile = new GameProfile(UUID.nameUUIDFromBytes("NpcGardenFixtureObserver".getBytes(StandardCharsets.UTF_8)),
                "GardenObserver");
        return FakePlayerFactory.get(level, profile);
    }

    private static void awayPlatform(ServerLevel level) {
        var away = home.offset(128, 0, 0);
        level.getChunk(away);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            level.setBlock(away.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            level.setBlock(away.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(away.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setChunkForced(away.getX() >> 4, away.getZ() >> 4, true);
    }

    private static ProfessionSupplyView.GardenView garden(WorldSim sim) {
        return sim.professionSupply(SITE).orElseThrow().gardens().stream()
                .filter(g -> g.placeId().equals(PLOT)).findFirst().orElseThrow();
    }

    private static void sample(MinecraftServer server, WorldSim sim) throws Exception {
        if (home == null || plot == null) return;
        var row = new LinkedHashMap<String, Object>();
        row.put("tick", ticks); row.put("phase", PHASE); row.put("state", state);
        row.put("calendar_ticks", CultivationCalendarSavedData.get(server.overworld()).elapsedCalendarTicks());
        row.put("crop", server.overworld().getBlockState(plot).toString());
        row.put("mob_griefing", server.overworld().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING));
        row.put("garden", sim.professionSupply(SITE).map(v -> v.gardens().stream().filter(g -> g.placeId().equals(PLOT)).findFirst().orElse(null)).orElse(null));
        row.put("supply", sim.professionSupply(SITE).orElse(null));
        row.put("child", residentSample(server.overworld(), sim, childId));
        row.put("grower", residentSample(server.overworld(), sim, growerId));
        row.put("observer", observer == null ? "none" : observer.blockPosition().toString());
        Files.writeString(OUT.resolve(PHASE + "-samples.jsonl"), GSON.toJson(row) + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static Object residentSample(ServerLevel level, WorldSim sim, int id) {
        if (id <= 0 || sim.life(id).isEmpty()) return null;
        LifeResidentView life = sim.life(id).orElseThrow();
        var row = new LinkedHashMap<String, Object>();
        row.put("life", life); row.put("profession", sim.profession(id).orElse(null));
        var entities = level.getEntitiesOfClass(CultivatorEntity.class, new AABB(home).inflate(100),
                e -> e.ledgerPersonId() == id && WorldSimAvatars.isManagedLife(e, life.epoch()));
        row.put("managed_body_count", entities.size());
        if (!entities.isEmpty()) {
            var body = entities.getFirst();
            row.put("body_uuid", body.getUUID().toString());
            row.put("body_xyz", List.of(body.getX(), body.getY(), body.getZ()));
            row.put("body_navigation_done", body.getNavigation().isDone());
            if (life.activity().equals("plant") && life.targetId().equals(PLOT)) {
                if (life.phase().equals("moving")) sawGardenMoving = true;
                if (life.phase().equals("working")) sawGardenWorking = true;
                recordWalk(body);
            }
            if (id == growerId && life.activity().equals("tend") && life.targetId().equals(PLOT)) {
                if (life.phase().equals("moving")) sawGrowerMoving = true;
                if (life.phase().equals("working")) sawGrowerWorking = true;
                recordWalk(body);
            }
            if (PHASE.equals("restart") && life.activity().equals("harvest") && life.targetId().equals(PLOT)) {
                if (life.phase().equals("moving")) sawHarvestMoving = true;
                if (life.phase().equals("working")) sawHarvestWorking = true;
                recordWalk(body);
            }
        }
        return row;
    }

    private static void recordWalk(CultivatorEntity body) {
        var cell = List.of(body.blockPosition().getX(), body.blockPosition().getY(), body.blockPosition().getZ());
        if (WALK.isEmpty() || !WALK.getLast().equals(cell)) WALK.add(cell);
    }

    private static boolean managedAtStand(ServerLevel level, WorldSim sim) {
        BlockPos stand = plot.west();
        for (int id : List.of(childId, growerId)) {
            var life = sim.life(id).orElse(null);
            if (life == null) continue;
            var nearby = level.getEntitiesOfClass(CultivatorEntity.class, new AABB(stand).inflate(3),
                    e -> e.ledgerPersonId() == id && WorldSimAvatars.isManagedLife(e, life.epoch()));
            if (!nearby.isEmpty()) return true;
        }
        return false;
    }

    private static void check(String name, boolean pass, Object detail) {
        CHECKS.add(Map.of("name", name, "pass", pass, "detail", String.valueOf(detail)));
        if (!pass) throw new AssertionError(name + ": " + detail);
    }

    private static void done(String caseName) {
        if (!COMPLETED.contains(caseName)) COMPLETED.add(caseName);
    }

    private static void dirty(MinecraftServer server) {
        WorldSimSavedData.get(server.overworld()).setDirty();
        CultivationCalendarSavedData.get(server.overworld()).checkpoint();
    }

    private static void finish(MinecraftServer server, Throwable failure) {
        if (finished) return;
        finished = true;
        try {
            Files.createDirectories(OUT);
            dirty(server);
            if (observer != null && observer.isAddedToLevel())
                server.overworld().removePlayerImmediately(observer, Entity.RemovalReason.DISCARDED);
            var report = new LinkedHashMap<String, Object>();
            report.put("kind", "npc_garden_native_server"); report.put("phase", PHASE);
            report.put("source", SOURCE); report.put("world_seed", server.overworld().getSeed());
            report.put("selected", List.of(SELECTED)); report.put("completed", List.copyOf(COMPLETED));
            report.put("unrun", List.of(SELECTED).stream().filter(s -> !COMPLETED.contains(s)).toList());
            report.put("checks", CHECKS); report.put("pass", failure == null && COMPLETED.size() == SELECTED.length);
            report.put("calendar_input", "one explicit fixture tick per server tick via CultivationCalendarSavedData; fake observer is ServerLevel-only");
            report.put("player_input", "not_verified; FakePlayer only provides near-field presence");
            report.put("source_provenance", "fixed source provided by runner; see source field");
            report.put("tick", ticks);
            if (failure != null) {
                var writer = new StringWriter(); failure.printStackTrace(new PrintWriter(writer));
                report.put("failure", failure.toString()); report.put("stack", writer.toString());
                failure.printStackTrace();
            }
            Files.writeString(OUT.resolve(PHASE + ".json"), GSON.toJson(report));
        } catch (Exception writing) {
            writing.printStackTrace();
        }
        server.halt(false);
    }
}
