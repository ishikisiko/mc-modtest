package com.example.myvillage.forging;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

record WorkshopSpecialtyRules(int seekQi, int seekCooldown, int gradeDepth, int herbsRetained,
                              int herbsBonus, List<Sapling> saplings) {
    private static final String RESOURCE = "/data/yuanxian/forging/tool_specialties.json";
    private static final Codec<WorkshopSpecialtyRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, 100).fieldOf("seek_qi").forGetter(WorkshopSpecialtyRules::seekQi),
            Codec.intRange(1, 200).fieldOf("seek_cooldown_ticks").forGetter(WorkshopSpecialtyRules::seekCooldown),
            Codec.intRange(1, 4).fieldOf("grade_depth").forGetter(WorkshopSpecialtyRules::gradeDepth),
            Codec.intRange(1, 4).fieldOf("herbs_retained").forGetter(WorkshopSpecialtyRules::herbsRetained),
            Codec.intRange(1, 8).fieldOf("herbs_bonus").forGetter(WorkshopSpecialtyRules::herbsBonus),
            Sapling.CODEC.listOf().fieldOf("saplings").forGetter(WorkshopSpecialtyRules::saplings)
    ).apply(instance, WorkshopSpecialtyRules::new));

    record Sapling(ResourceLocation log, ResourceLocation plant, List<ResourceLocation> crown) {
        private static final Codec<Sapling> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("log").forGetter(Sapling::log),
                ResourceLocation.CODEC.fieldOf("plant").forGetter(Sapling::plant),
                ResourceLocation.CODEC.listOf().optionalFieldOf("crown", List.of()).forGetter(Sapling::crown)
        ).apply(instance, Sapling::new));
    }

    private static final class Holder {
        static final WorkshopSpecialtyRules RULES = load();
    }

    static WorkshopSpecialtyRules get() {
        return Holder.RULES;
    }

    private static WorkshopSpecialtyRules load() {
        try (var stream = Objects.requireNonNull(WorkshopSpecialtyRules.class.getResourceAsStream(RESOURCE), RESOURCE);
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read " + RESOURCE, error);
        }
    }
}
