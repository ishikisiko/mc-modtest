package com.example.myvillage.cultivation.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record RealmDefinition(
        String translationKey,
        int sortOrder,
        List<RealmStageDefinition> stages,
        Optional<ResourceLocation> nextRealm) {
    private static final Codec<SerializedRealm> SERIALIZED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("translation_key").forGetter(SerializedRealm::translationKey),
            Codec.INT.fieldOf("sort_order").forGetter(SerializedRealm::sortOrder),
            RealmStageDefinition.CODEC.listOf().fieldOf("stages").forGetter(SerializedRealm::stages),
            ResourceLocation.CODEC.optionalFieldOf("next_realm").forGetter(SerializedRealm::nextRealm)
    ).apply(instance, SerializedRealm::new));

    public static final Codec<RealmDefinition> CODEC = SERIALIZED_CODEC
            .comapFlatMap(SerializedRealm::decode, RealmDefinition::serialize);

    public RealmDefinition {
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException("Realm translation_key must not be blank");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("Realm sort_order must be non-negative, got " + sortOrder);
        }
        Objects.requireNonNull(stages, "stages");
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("Realm stages must not be empty");
        }
        stages = List.copyOf(stages);
        nextRealm = Objects.requireNonNull(nextRealm, "nextRealm");

        Set<ResourceLocation> stageIds = new HashSet<>();
        Set<Integer> stageOrders = new HashSet<>();
        int previousOrder = -1;
        for (RealmStageDefinition stage : stages) {
            Objects.requireNonNull(stage, "stage");
            if (!stageIds.add(stage.id())) {
                throw new IllegalArgumentException("Duplicate realm stage id " + stage.id());
            }
            if (!stageOrders.add(stage.sortOrder())) {
                throw new IllegalArgumentException("Duplicate realm stage sort_order " + stage.sortOrder());
            }
            if (stage.sortOrder() <= previousOrder) {
                throw new IllegalArgumentException("Realm stages must have strictly increasing sort_order values");
            }
            previousOrder = stage.sortOrder();
        }
    }

    public boolean hasStage(ResourceLocation stageId) {
        Objects.requireNonNull(stageId, "stageId");
        return stages.stream().anyMatch(stage -> stage.id().equals(stageId));
    }

    public boolean containsStage(ResourceLocation stageId) {
        return hasStage(stageId);
    }

    private SerializedRealm serialize() {
        return new SerializedRealm(translationKey, sortOrder, stages, nextRealm);
    }

    private record SerializedRealm(
            String translationKey,
            int sortOrder,
            List<RealmStageDefinition> stages,
            Optional<ResourceLocation> nextRealm) {
        private DataResult<RealmDefinition> decode() {
            try {
                return DataResult.success(new RealmDefinition(translationKey, sortOrder, stages, nextRealm));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }
}
