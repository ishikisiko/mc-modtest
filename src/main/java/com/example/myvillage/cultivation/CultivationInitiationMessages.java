package com.example.myvillage.cultivation;

import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.SpiritualElementDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.root.SpiritualRootAwakeningService;
import com.example.myvillage.cultivation.technique.TechniqueInheritanceService;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class CultivationInitiationMessages {
    private CultivationInitiationMessages() {
    }

    public static List<Component> awakening(
            ServerPlayer player,
            SpiritualRootAwakeningService.Outcome outcome) {
        return switch (outcome.status()) {
            case SUCCESS -> List.of(Component.translatable(
                    "message.myvillage.cultivation.awakening.success",
                    affinitySummary(player, outcome.profile())));
            case ALREADY_AWAKENED -> List.of(Component.translatable(
                    "message.myvillage.cultivation.awakening.already_awakened",
                    affinitySummary(player, outcome.profile())));
            case INVALID_PROFILE_STATE -> List.of(Component.translatable(
                    "message.myvillage.cultivation.awakening.invalid_profile_state"));
            case NO_ELIGIBLE_ELEMENTS -> List.of(Component.translatable(
                    "message.myvillage.cultivation.awakening.no_eligible_elements"));
            case GENERATION_FAILED -> List.of(Component.translatable(
                    "message.myvillage.cultivation.awakening.generation_failed"));
            case UPDATE_REJECTED -> List.of(Component.translatable(
                    "message.myvillage.cultivation.awakening.update_rejected"));
        };
    }

    public static List<Component> inheritance(
            ServerPlayer player,
            TechniqueInheritanceService.Outcome outcome) {
        return switch (outcome.status()) {
            case SUCCESS -> List.of(
                    Component.translatable(
                            "message.myvillage.cultivation.inheritance.success",
                            basicBreathingName(player)),
                    Component.translatable("message.myvillage.cultivation.inheritance.view_profile"),
                    Component.translatable("message.myvillage.cultivation.inheritance.not_executable"));
            case NOT_AWAKENED -> List.of(Component.translatable(
                    "message.myvillage.cultivation.inheritance.not_awakened"));
            case REQUIREMENTS_NOT_MET -> List.of(Component.translatable(
                    "message.myvillage.cultivation.inheritance.requirements_not_met"));
            case TECHNIQUE_NOT_REGISTERED -> List.of(Component.translatable(
                    "message.myvillage.cultivation.inheritance.technique_not_registered"));
            case ALREADY_LEARNED -> List.of(Component.translatable(
                    "message.myvillage.cultivation.inheritance.already_learned",
                    basicBreathingName(player)));
            case UPDATE_REJECTED -> List.of(Component.translatable(
                    "message.myvillage.cultivation.inheritance.update_rejected"));
        };
    }

    private static Component affinitySummary(ServerPlayer player, CultivationProfile profile) {
        if (profile.spiritualRoot().isEmpty()) {
            return Component.translatable("cultivation.profile.root.unawakened");
        }
        Optional<Registry<SpiritualElementDefinition>> elements =
                player.registryAccess().registry(ModCultivationRegistries.SPIRITUAL_ELEMENTS);
        List<MutableComponent> entries = profile.spiritualRoot().orElseThrow()
                .affinitiesBasisPoints().entrySet().stream()
                .sorted(Comparator.<java.util.Map.Entry<ResourceLocation, Integer>>comparingInt(
                                java.util.Map.Entry::getValue)
                        .reversed()
                        .thenComparing(entry -> entry.getKey().toString()))
                .map(entry -> Component.translatable(
                        "message.myvillage.cultivation.affinity_entry",
                        elementName(elements, entry.getKey()),
                        entry.getValue() / 100,
                        String.format(java.util.Locale.ROOT, "%02d", entry.getValue() % 100)))
                .toList();
        MutableComponent summary = Component.empty();
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) {
                summary.append(Component.translatable(
                        "message.myvillage.cultivation.affinity_separator"));
            }
            summary.append(entries.get(index));
        }
        return summary;
    }

    private static Component elementName(
            Optional<Registry<SpiritualElementDefinition>> registry,
            ResourceLocation id) {
        SpiritualElementDefinition definition = registry.map(value -> value.get(id)).orElse(null);
        if (definition == null) {
            return Component.literal(id.toString());
        }
        return Component.translatableWithFallback(definition.translationKey(), id.toString());
    }

    private static Component basicBreathingName(ServerPlayer player) {
        ResourceLocation id = ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID;
        TechniqueDefinition definition = player.registryAccess()
                .registry(ModCultivationRegistries.TECHNIQUES)
                .map(registry -> registry.get(id))
                .orElse(null);
        if (definition == null) {
            return Component.literal(id.toString());
        }
        return Component.translatableWithFallback(definition.translationKey(), id.toString());
    }
}
