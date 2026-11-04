package com.example.myvillage.wilderness;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo.BiomeInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VanillaMonsterSpawnModifierTest {
    private static EntityType<Zombie> modMonster;
    private static EntityType<Zombie> otherMonster;

    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registry = (MappedRegistry<EntityType<?>>) BuiltInRegistries.ENTITY_TYPE;
        registry.unfreeze();
        try {
            modMonster = Registry.register(registry, ResourceLocation.parse("myvillage:spawn_weight_test"),
                    EntityType.Builder.<Zombie>of(Zombie::new, MobCategory.MONSTER).build("myvillage:spawn_weight_test"));
            otherMonster = Registry.register(registry, ResourceLocation.parse("another_mod:spawn_weight_test"),
                    EntityType.Builder.<Zombie>of(Zombie::new, MobCategory.MONSTER).build("another_mod:spawn_weight_test"));
        } finally { registry.freeze(); }
    }

    private static VanillaMonsterSpawnModifier configured() throws Exception {
        var json = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/myvillage/neoforge/biome_modifier/scale_vanilla_monsters.json")));
        assertEquals("myvillage:scale_vanilla_monsters", json.getAsJsonObject().get("type").getAsString());
        return VanillaMonsterSpawnModifier.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static BiomeInfo original() {
        var spawns = new MobSpawnSettings.Builder()
                .addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.ZOMBIE, 100, 2, 4))
                .addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.SKELETON, 100, 1, 4))
                .addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.WITCH, 5, 1, 1))
                .addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(modMonster, 6, 1, 2))
                .addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(otherMonster, 10, 1, 3))
                .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.COW, 8, 2, 4))
                .addSpawn(MobCategory.WATER_CREATURE, new MobSpawnSettings.SpawnerData(EntityType.SQUID, 10, 1, 4))
                .addMobCharge(EntityType.ZOMBIE, 1, 2).creatureGenerationProbability(.2F).build();
        var effects = new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build();
        return new BiomeInfo(new Biome.ClimateSettings(true, .5F, Biome.TemperatureModifier.NONE, .5F),
                effects, BiomeGenerationSettings.EMPTY, spawns);
    }

    @Test void configuredMultiplierOnlyScalesVanillaMonstersAndPreservesPackSizesAndCosts() throws Exception {
        var modifier = configured(); assertEquals(.7, modifier.multiplier());
        var original = original(); var builder = BiomeInfo.Builder.copyOf(original);
        modifier.modify(null, BiomeModifier.Phase.MODIFY, builder);
        var modified = builder.build().mobSpawnSettings();
        assertEquals(List.of(70, 70, 4, 6, 10), modified.getMobs(MobCategory.MONSTER).unwrap().stream()
                .map(spawn -> spawn.getWeight().asInt()).toList());
        var before = original.mobSpawnSettings().getMobs(MobCategory.MONSTER).unwrap();
        var after = modified.getMobs(MobCategory.MONSTER).unwrap();
        for (int i = 0; i < before.size(); i++) {
            assertSame(before.get(i).type, after.get(i).type);
            assertEquals(before.get(i).minCount, after.get(i).minCount);
            assertEquals(before.get(i).maxCount, after.get(i).maxCount);
        }
        for (var category : List.of(MobCategory.CREATURE, MobCategory.WATER_CREATURE))
            assertEquals(original.mobSpawnSettings().getMobs(category).unwrap(), modified.getMobs(category).unwrap());
        assertEquals(original.mobSpawnSettings().getMobSpawnCost(EntityType.ZOMBIE), modified.getMobSpawnCost(EntityType.ZOMBIE));
        assertEquals(original.mobSpawnSettings().getCreatureProbability(), modified.getCreatureProbability());
        assertEquals(100, before.getFirst().getWeight().asInt());
    }

    @Test void onlyModifyPhaseRunsAndFreshRegistryCopiesNeverCompoundTheReduction() throws Exception {
        var modifier = configured(); var original = original();
        for (int reload = 0; reload < 2; reload++) {
            var builder = BiomeInfo.Builder.copyOf(original);
            for (var phase : BiomeModifier.Phase.values()) modifier.modify(null, phase, builder);
            assertEquals(70, builder.build().mobSpawnSettings().getMobs(MobCategory.MONSTER).unwrap().getFirst().getWeight().asInt());
        }
        for (var phase : BiomeModifier.Phase.values()) if (phase != BiomeModifier.Phase.MODIFY) {
            var builder = BiomeInfo.Builder.copyOf(original); modifier.modify(null, phase, builder);
            assertEquals(original.mobSpawnSettings().getMobs(MobCategory.MONSTER).unwrap(),
                    builder.build().mobSpawnSettings().getMobs(MobCategory.MONSTER).unwrap());
        }
    }

    @Test void roundingRetainsRareEntriesAndNeverEnablesDisabledSpawns() throws Exception {
        var builder = BiomeInfo.Builder.copyOf(original());
        var spawns = builder.getMobSpawnSettings().getSpawner(MobCategory.MONSTER); spawns.clear();
        spawns.add(new MobSpawnSettings.SpawnerData(EntityType.ZOMBIE, 0, 1, 1));
        spawns.add(new MobSpawnSettings.SpawnerData(EntityType.SKELETON, 1, 1, 1));
        spawns.add(new MobSpawnSettings.SpawnerData(EntityType.ENDERMAN, 10, 1, 4));
        configured().modify(null, BiomeModifier.Phase.MODIFY, builder);
        assertEquals(List.of(0, 1, 7), spawns.stream().map(spawn -> spawn.getWeight().asInt()).toList());
    }

    @Test void invalidMultiplierCannotBecomeAValidModifier() {
        for (double multiplier : new double[]{0, -1, 1.01, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new VanillaMonsterSpawnModifier(multiplier));
        assertTrue(VanillaMonsterSpawnModifier.CODEC.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"multiplier\":1.01}")).error().isPresent());
        assertTrue(VanillaMonsterSpawnModifier.CODEC.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"multiplier\":\"0.7\"}")).error().isPresent());
    }
}
