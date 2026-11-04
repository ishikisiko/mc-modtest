package com.example.myvillage.wilderness;

import com.example.myvillage.MyVillageMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Changes the biome lottery once; individual spawn attempts and mob caps stay vanilla. */
public record VanillaMonsterSpawnModifier(double multiplier) implements BiomeModifier {
    public static final MapCodec<VanillaMonsterSpawnModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.doubleRange(0, 1).fieldOf("multiplier").forGetter(VanillaMonsterSpawnModifier::multiplier)
    ).apply(instance, VanillaMonsterSpawnModifier::new));
    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> SERIALIZERS = DeferredRegister.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, MyVillageMod.MOD_ID);
    static { SERIALIZERS.register("scale_vanilla_monsters", () -> CODEC); }

    public VanillaMonsterSpawnModifier {
        if (!Double.isFinite(multiplier) || multiplier <= 0 || multiplier > 1)
            throw new IllegalArgumentException("Monster spawn multiplier must be finite and in (0, 1]");
    }

    public static void register(IEventBus bus) { SERIALIZERS.register(bus); }

    @Override public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.MODIFY) return;
        builder.getMobSpawnSettings().getSpawner(MobCategory.MONSTER).replaceAll(spawn -> {
            if (spawn.type.getCategory() != MobCategory.MONSTER
                    || !BuiltInRegistries.ENTITY_TYPE.getKey(spawn.type).getNamespace().equals("minecraft")) return spawn;
            int oldWeight = spawn.getWeight().asInt();
            // Zero remains disabled. Positive entries retain at least one lottery ticket.
            int weight = oldWeight == 0 ? 0 : Math.max(1, (int) Math.round(oldWeight * multiplier));
            return weight == oldWeight ? spawn : new MobSpawnSettings.SpawnerData(spawn.type, weight, spawn.minCount, spawn.maxCount);
        });
    }

    @Override public MapCodec<? extends BiomeModifier> codec() { return CODEC; }
}
