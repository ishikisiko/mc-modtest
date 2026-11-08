package com.example.myvillage.cultivation.fate;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.alchemy.AlchemyRules;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HerbalLoreTest {
    @BeforeAll static void bootstrap() {
        if (net.neoforged.fml.loading.LoadingModList.get()==null)
            net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    private static double chance() throws Exception {
        try(var in=HerbalLoreTest.class.getResourceAsStream("/data/myvillage/myvillage/innate_fate/herbal_lore.json")) {
            assertNotNull(in);
            var d=FateDefinition.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8))).getOrThrow();
            assertEquals(0,d.tier());assertEquals(1,d.cost());assertTrue(d.enabled());
            return d.effect("herbal_find_chance");
        }
    }
    private static RandomSource draw(double value) {
        return new LegacyRandomSource(0) { @Override public double nextDouble(){return value;} };
    }
    @Test void configuredTwoPercentUsesAStrictBoundary() throws Exception {
        assertEquals(.02,chance());var plant=Blocks.SHORT_GRASS.defaultBlockState();
        assertTrue(HerbalLoreRuntime.roll(plant,chance(),draw(Math.nextDown(.02))).isPresent());
        assertTrue(HerbalLoreRuntime.roll(plant,chance(),draw(.02)).isEmpty());
        assertTrue(HerbalLoreRuntime.roll(plant,chance(),draw(.99)).isEmpty());
        assertTrue(HerbalLoreRuntime.roll(plant,0,draw(0)).isEmpty());
    }
    @Test void ordinaryPlantsAndEveryCropAgeQualify() {
        for(var block:List.of(Blocks.SHORT_GRASS,Blocks.TALL_GRASS,Blocks.DANDELION,Blocks.OAK_SAPLING,
                Blocks.SEAGRASS,Blocks.KELP,Blocks.WEEPING_VINES,Blocks.BROWN_MUSHROOM,Blocks.WHEAT,Blocks.CARROTS,Blocks.POTTED_DANDELION))
            assertTrue(HerbalLoreRuntime.plant(block.defaultBlockState()),block.toString());
        for(var state:Blocks.WHEAT.getStateDefinition().getPossibleStates())assertTrue(HerbalLoreRuntime.plant(state));
        for(var block:List.of(Blocks.STONE,Blocks.DIRT,Blocks.WATER,Blocks.OAK_PLANKS,Blocks.FLOWER_POT))
            assertFalse(HerbalLoreRuntime.plant(block.defaultBlockState()),block.toString());
    }
    @Test void botanicalTagEntriesResolveAndIncludeBothLargeDripleafParts() throws Exception {
        try(var in=HerbalLoreTest.class.getResourceAsStream("/data/myvillage/tags/block/herbal_lore_plants.json")) {
            assertNotNull(in);var values=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("values");
            var ids=new HashSet<String>();
            for(var value:values) {
                String id=value.getAsString();ids.add(id);
                if(!id.startsWith("#"))assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.ResourceLocation.parse(id)),id);
            }
            assertTrue(ids.containsAll(List.of("minecraft:big_dripleaf","minecraft:big_dripleaf_stem")));
        }
    }
    @Test void materialPoolIncludesEveryDistinctFormulaIngredient() {
        var expected=new TreeSet<String>();AlchemyRules.get().recipes().forEach(r->expected.addAll(r.ingredients()));
        assertEquals(new ArrayList<>(expected),HerbalLoreRuntime.materials());
        assertTrue(expected.containsAll(List.of("myvillage:ningyuan_fruit_high","myvillage:frost_plume","myvillage:thorn_bone")));
        var seen=new HashSet<String>();var random=RandomSource.create(314159);
        for(int i=0;i<2000;i++)seen.add(HerbalLoreRuntime.roll(Blocks.SHORT_GRASS.defaultBlockState(),1,random).orElseThrow());
        assertEquals(expected,seen);
    }
}
