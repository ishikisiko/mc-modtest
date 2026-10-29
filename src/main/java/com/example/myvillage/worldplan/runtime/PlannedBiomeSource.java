package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.WorldPlanRules;
import com.example.myvillage.worldplan.FrozenRulesEncoding;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.*;
import java.util.*;
import java.util.stream.Stream;

/** Seed-bound surface ecology; the original overworld source retains underground cave biomes. */
public final class PlannedBiomeSource extends BiomeSource {
    public static final MapCodec<PlannedBiomeSource> CODEC=createCodec(WorldPlanRules.LEGACY_ALGORITHM);
    public static final MapCodec<PlannedBiomeSource> CODEC_V2=createCodec(WorldPlanRules.V2_ALGORITHM);
    public static final MapCodec<PlannedBiomeSource> CODEC_V3=createCodec(WorldPlanRules.V3_ALGORITHM);
    public static final MapCodec<PlannedBiomeSource> CODEC_V4=createCodec(WorldPlanRules.ALGORITHM);
    public static final MapCodec<PlannedBiomeSource> CODEC_V5=createCodec(WorldPlanRules.V5_ALGORITHM);
    public static final MapCodec<PlannedBiomeSource> CODEC_V6=createCodec(WorldPlanRules.V6_ALGORITHM);
    public static final MapCodec<PlannedBiomeSource> CODEC_V7=createCodec(WorldPlanRules.V7_ALGORITHM);
    public static final MapCodec<PlannedBiomeSource> CODEC_V8=createCodec(WorldPlanRules.V8_ALGORITHM);
    public static final MapCodec<PlannedBiomeSource> CODEC_V9=createCodec(WorldPlanRules.V9_ALGORITHM);
    public static final MapCodec<PlannedBiomeSource> CODEC_V10=createCodec(WorldPlanRules.V10_ALGORITHM);
    private static MapCodec<PlannedBiomeSource> createCodec(String algorithm){return RecordCodecBuilder.mapCodec(i->i.group(
        BiomeSource.CODEC.fieldOf("delegate").forGetter(s->s.original),
        Codec.STRING.fieldOf("rules").forGetter(s->WorldPlanRules.compressed(s.rules.algorithm) ? FrozenRulesEncoding.encode(s.rules.json()) : s.rules.json()),
        Codec.LONG.fieldOf("seed").forGetter(s->s.plan==null?0L:s.plan.seed())
    ).apply(i,(source,json,seed)->{
        WorldPlanRules rules=new WorldPlanRules(WorldPlanRules.compressed(algorithm) ? FrozenRulesEncoding.decode(json) : json);
        if(!algorithm.equals(rules.algorithm))throw new IllegalArgumentException("Biome source type must match frozen algorithm");
        PlannedBiomeSource result=new PlannedBiomeSource(source,rules);
        result.bind(new WorldPlan(seed,result.rules),null);return result;
    }));}
    private final BiomeSource original;
    private final WorldPlanRules rules;
    private volatile Map<String,Holder<Biome>> holders;
    private volatile WorldPlan plan;
    private volatile Climate.Sampler terrainSampler;
    private final Map<Long,WorldPlan.Context> columns=Collections.synchronizedMap(new LinkedHashMap<>(256,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Long,WorldPlan.Context> entry){return size()>2048;}
    });
    public PlannedBiomeSource(BiomeSource original,WorldPlanRules rules){
        this.original=original;this.rules=rules;
    }
    private synchronized Map<String,Holder<Biome>> resolvedHolders(){
        if(holders!=null)return holders;
        // Data-pack references are unbound while the preset Codec is decoding.
        Map<String,Holder<Biome>> entries=new HashMap<>();
        for(Holder<Biome> b:original.possibleBiomes())b.unwrapKey().ifPresent(k->entries.put(k.location().toString(),b));
        Set<String> required=new HashSet<>(Set.of("minecraft:ocean","minecraft:deep_ocean","minecraft:beach"));
        rules.profiles.forEach(p->required.addAll(p.biomes()));
        if(rules.ecology!=null)required.addAll(rules.ecology.biomes());
        if(rules.ecotone!=null)required.addAll(rules.ecotone.biomes());
        if(!entries.keySet().containsAll(required)){required.removeAll(entries.keySet());throw new IllegalArgumentException("Worldplan delegate biome source is missing "+required);}
        holders=Map.copyOf(entries);return holders;
    }
    void bind(WorldPlan plan,Climate.Sampler sampler){
        if(sampler!=null)resolvedHolders();
        terrainSampler=sampler;columns.clear();this.plan=plan;
    }
    @Override protected MapCodec<? extends BiomeSource> codec(){return switch(rules.algorithm){case WorldPlanRules.LEGACY_ALGORITHM->CODEC;case WorldPlanRules.V2_ALGORITHM->CODEC_V2;case WorldPlanRules.V3_ALGORITHM->CODEC_V3;case WorldPlanRules.V5_ALGORITHM->CODEC_V5;case WorldPlanRules.V6_ALGORITHM->CODEC_V6;case WorldPlanRules.V7_ALGORITHM->CODEC_V7;case WorldPlanRules.V8_ALGORITHM->CODEC_V8;case WorldPlanRules.V9_ALGORITHM->CODEC_V9;case WorldPlanRules.V10_ALGORITHM->CODEC_V10;default->CODEC_V4;};}
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes(){return original.possibleBiomes().stream();}
    @Override public Holder<Biome> getNoiseBiome(int x,int y,int z,Climate.Sampler sampler){
        WorldPlan p=plan;if(p==null)throw new IllegalStateException("Planned biome source used before world seed binding");
        long key=((long)x<<32)^(z&0xffffffffL);
        WorldPlan.Context column=columns.computeIfAbsent(key,ignored->p.sample(x*4,z*4));
        if(y*4<Math.min(48,column.surfaceY()-rules.surfaceSeal-4)){
            Holder<Biome> cave=original.getNoiseBiome(x,y,z,terrainSampler==null?sampler:terrainSampler);
            String name=cave.unwrapKey().map(k->k.location().toString()).orElse("");
            if(Set.of("minecraft:lush_caves","minecraft:dripstone_caves","minecraft:deep_dark").contains(name))return cave;
        }
        return resolvedHolders().get(column.biome());
    }
}
