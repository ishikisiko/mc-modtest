package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.FieldBiomeLayout;
import com.example.myvillage.worldplan.WorldPlanRules;
import com.example.myvillage.worldplan.FrozenRulesEncoding;
import com.example.myvillage.worldplan.wonder.FoldedRidge;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.*;
import java.util.*;
import java.util.stream.Stream;

/** Seed-bound surface ecology; the original overworld source retains underground cave biomes. */
public final class PlannedBiomeSource extends BiomeSource {
    public static final MapCodec<PlannedBiomeSource> CODEC_V11=createCodec(WorldPlanRules.V11_ALGORITHM);
    private static MapCodec<PlannedBiomeSource> createCodec(String algorithm){return RecordCodecBuilder.mapCodec(i->i.group(
        BiomeSource.CODEC.fieldOf("delegate").forGetter(s->s.original),
        Biome.CODEC.fieldOf("folded_ridge_biome").forGetter(s->s.foldedRidgeBiome),
        Codec.unboundedMap(Codec.STRING, Biome.CODEC).optionalFieldOf("field_biomes", Map.of()).forGetter(s->s.fieldBiomes),
        Codec.STRING.fieldOf("rules").forGetter(s->WorldPlanRules.compressed(s.rules.algorithm) ? FrozenRulesEncoding.encode(s.rules.json()) : s.rules.json()),
        Codec.LONG.fieldOf("seed").forGetter(s->s.plan==null?0L:s.plan.seed())
    ).apply(i,(source,ridgeBiome,fieldBiomes,json,seed)->{
        WorldPlanRules rules=new WorldPlanRules(WorldPlanRules.compressed(algorithm) ? FrozenRulesEncoding.decode(json) : json);
        if(!algorithm.equals(rules.algorithm))throw new IllegalArgumentException("Biome source type must match frozen algorithm");
        PlannedBiomeSource result=new PlannedBiomeSource(source,ridgeBiome,fieldBiomes,rules);
        result.bind(new WorldPlan(seed,result.rules),null);return result;
    }));}
    private final BiomeSource original;
    private final WorldPlanRules rules;
    private final Holder<Biome> foldedRidgeBiome;
    private final Map<String, Holder<Biome>> fieldBiomes;
    private volatile FoldedRidge foldedRidge;
    private volatile FieldBiomeLayout fieldLayout;
    private volatile Map<String,Holder<Biome>> holders;
    private volatile WorldPlan plan;
    private volatile Climate.Sampler terrainSampler;
    private final Map<Long,WorldPlan.Context> columns=Collections.synchronizedMap(new LinkedHashMap<>(256,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Long,WorldPlan.Context> entry){return size()>2048;}
    });
    public PlannedBiomeSource(BiomeSource original,Holder<Biome> foldedRidgeBiome, Map<String, Holder<Biome>> fieldBiomes,WorldPlanRules rules){
        this.original=original;this.foldedRidgeBiome=foldedRidgeBiome;this.rules=rules;
        this.fieldBiomes=Map.copyOf(fieldBiomes);
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
        for (var entry : fieldBiomes.entrySet()) {
            if (!entry.getValue().unwrapKey().orElseThrow().location().toString().equals(entry.getKey()))
                throw new IllegalArgumentException("野外群系引用与键不一致 " + entry.getKey());
            entries.put(entry.getKey(), entry.getValue());
        }
        if(rules.fieldBiomes!=null)rules.fieldBiomes.domains.forEach(domain->required.add(domain.biome()));
        if(rules.provincialBiomes!=null)rules.provincialBiomes.profiles.forEach(profile->required.add(profile.biome()));
        if(!entries.keySet().containsAll(required)){required.removeAll(entries.keySet());throw new IllegalArgumentException("Worldplan delegate biome source is missing "+required);}
        holders=Map.copyOf(entries);return holders;
    }
    void bind(WorldPlan plan,Climate.Sampler sampler){
        if(sampler!=null)resolvedHolders();
        terrainSampler=sampler;columns.clear();this.plan=plan;
    }
    void bindFoldedRidge(FoldedRidge ridge) {
        foldedRidge = ridge;
    }
    void bindFields(FieldBiomeLayout layout) {
        fieldLayout = layout;
        columns.clear();
    }
    @Override protected MapCodec<? extends BiomeSource> codec(){return CODEC_V11;}
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes(){return Stream.concat(original.possibleBiomes().stream(),Stream.concat(Stream.of(foldedRidgeBiome),fieldBiomes.values().stream()));}
    @Override public Holder<Biome> getNoiseBiome(int x,int y,int z,Climate.Sampler sampler){
        WorldPlan p=plan;if(p==null)throw new IllegalStateException("Planned biome source used before world seed binding");
        FoldedRidge ridge = foldedRidge;
        if (ridge != null && ridge.ownsBiome(x*4+2, y*4+2, z*4+2)) return foldedRidgeBiome;
        long key=((long)x<<32)^(z&0xffffffffL);
        WorldPlan.Context column=columns.computeIfAbsent(key,ignored->{
            var base = p.sample(x*4,z*4);
            return fieldLayout == null ? base : fieldLayout.apply(base,x*4,z*4);
        });
        if(y*4<Math.min(48,column.surfaceY()-rules.surfaceSeal-4)){
            Holder<Biome> cave=original.getNoiseBiome(x,y,z,terrainSampler==null?sampler:terrainSampler);
            String name=cave.unwrapKey().map(k->k.location().toString()).orElse("");
            if(Set.of("minecraft:lush_caves","minecraft:dripstone_caves","minecraft:deep_dark").contains(name))return cave;
        }
        return resolvedHolders().get(column.biome());
    }
}
