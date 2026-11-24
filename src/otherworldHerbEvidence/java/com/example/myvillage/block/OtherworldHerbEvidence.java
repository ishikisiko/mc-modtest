package com.example.myvillage.block;

import com.example.myvillage.item.ModItems;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "yuanxian")
public final class OtherworldHerbEvidence {
    private static final List<Map<String, Object>> checks = new ArrayList<>();
    private static final int MAX_CHUNKS = 100;
    private static int ticks, job, candidate, generated;
    private static boolean finished;

    private record Case(String id, ResourceKey<Level> dimension, List<ResourceKey<Biome>> biomes,
                        SpiritCropBlock crop, Block soil, Block otherSoil, Item herb) {}

    private static List<Case> cases() {
        return List.of(
                new Case("red_furnace_vine", Level.NETHER, List.of(Biomes.CRIMSON_FOREST), ModBlocks.RED_FURNACE_VINE_CROP.get(),
                        ModBlocks.CINDER_SOIL.get(), ModBlocks.END_SAND_SOIL.get(), ModItems.RED_FURNACE_VINE.get()),
                new Case("star_dew_grass", Level.END, List.of(Biomes.END_HIGHLANDS, Biomes.END_MIDLANDS), ModBlocks.STAR_DEW_GRASS_CROP.get(),
                        ModBlocks.END_SAND_SOIL.get(), ModBlocks.CINDER_SOIL.get(), ModItems.STAR_DEW_GRASS.get())
        );
    }

    private static void check(String name, boolean pass, Object detail) {
        checks.add(Map.of("name", name, "pass", pass, "detail", detail.toString()));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (finished || !"1".equals(System.getenv("YUANXIAN_OTHERWORLD_HERB_EVIDENCE")) || ++ticks < 20) return;
        var server = event.getServer();
        try {
            var cases = cases();
            if (job < cases.size()) {
                var c = cases.get(job);
                var level = server.getLevel(c.dimension());
                if (level == null) throw new IllegalStateException("Missing dimension " + c.dimension());
                var next = findChunk(level, c);
                if (next != null) {
                    var chunk = level.getChunk(next[0], next[1]);
                    generated++;
                    var found = new ArrayList<BlockPos>();
                    chunk.findBlocks(s -> s.is(c.crop()), (pos, state) -> found.add(pos.immutable()));
                    if (!found.isEmpty()) {
                        check(c.id() + "_naturally_generated", found.stream().allMatch(pos ->
                                level.getBlockState(pos.below()).is(c.soil()) && c.crop().isMaxAge(level.getBlockState(pos))),
                                "checked " + generated + " chunks, found " + found);
                        cultivation(server.overworld(), c, job);
                        advance();
                    }
                } else {
                    check(c.id() + "_naturally_generated", false, "searched " + generated + " chunks");
                    advance();
                }
            }
            if (job >= cases.size()) finish(server);
        } catch (Exception failure) {
            failure.printStackTrace();
            check("exception", false, failure);
            finish(server);
        }
    }

    private static void advance() { job++; candidate = 0; generated = 0; }

    private static int[] findChunk(ServerLevel level, Case c) {
        var source = level.getChunkSource();
        var biomes = source.getGenerator().getBiomeSource();
        var sampler = source.randomState().sampler();
        boolean nether = level.dimension().equals(Level.NETHER);
        while (candidate < 1024 && generated < MAX_CHUNKS) {
            int i = candidate++;
            int x = (nether ? -16 : 84) + i % 32, z = i / 32 - 12;
            for (int y : new int[]{38, 62, 84}) {
                var biome = biomes.getNoiseBiome(QuartPos.fromBlock(x * 16 + 8), QuartPos.fromBlock(y),
                        QuartPos.fromBlock(z * 16 + 8), sampler);
                if (c.biomes().stream().anyMatch(biome::is)) return new int[]{x, z};
            }
        }
        return null;
    }

    private static void cultivation(ServerLevel level, Case c, int index) {
        int x = 8 + 16 * index;
        level.getChunk(x >> 4, 0);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, 8);
        var support = new BlockPos(x, Math.max(ground + 1, level.getMinBuildHeight() + 32), 8);
        var pos = support.above();
        if (!level.isEmptyBlock(pos)) throw new IllegalStateException("Planting test plot is occupied at " + pos);
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "OtherworldHerbs"));
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(x + 2.5, support.getY() + 1, 8.5, 0, 0);
        var hit = new BlockHitResult(Vec3.atCenterOf(support).add(0, .5, 0), Direction.UP, support, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(c.herb(), 2));
        for (var wrong : List.of(ModBlocks.SPIRIT_SOIL.get(), c.otherSoil())) {
            level.setBlockAndUpdate(support, wrong.defaultBlockState());
            c.herb().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            check(c.id() + "_refuses_" + wrong.getDescriptionId(),
                    level.isEmptyBlock(pos) && player.getMainHandItem().getCount() == 2, pos);
        }
        level.setBlockAndUpdate(support, c.soil().defaultBlockState());
        c.herb().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        check(c.id() + "_plant_at_home", level.getBlockState(pos).is(c.crop())
                && player.getMainHandItem().getCount() == 1, pos);
        var random = RandomSource.create(69453);
        for (int i = 0; i < 512; i++) {
            var state = level.getBlockState(pos);
            if (!state.is(c.crop()) || c.crop().isMaxAge(state)) break;
            state.randomTick(level, pos, random);
        }
        var mature = level.getBlockState(pos);
        check(c.id() + "_grows_on_own_soil", mature.is(c.crop()) && c.crop().isMaxAge(mature), mature);
        var harvest = Block.getDrops(mature, level, pos, null);
        var count = harvest.stream().filter(s -> s.is(c.herb())).mapToInt(ItemStack::getCount).sum();
        check(c.id() + "_mature_loot", count >= 2 && count <= 4 && level.getBlockState(support).is(c.soil()), count);
        var soilLoot = Block.getDrops(c.soil().defaultBlockState(), level, support, null);
        check(c.id() + "_soil_harvest", soilLoot.size() == 1 && soilLoot.getFirst().is(c.soil().asItem()), soilLoot);
    }

    private static void finish(MinecraftServer server) {
        finished = true;
        try {
            var output = Path.of(System.getenv("YUANXIAN_OTHERWORLD_HERB_RESULT"));
            Files.createDirectories(output.getParent());
            boolean pass = job == 2 && checks.stream().allMatch(row -> Boolean.TRUE.equals(row.get("pass")));
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(
                    Map.of("pass", pass, "source", "natural_chunks_and_server_item_use", "checks", checks)));
        } catch (Exception error) { error.printStackTrace(); }
        server.halt(false);
    }
}
