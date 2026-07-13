package com.example.myvillage.cultivation.meditation;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.AdvancementDefinition;
import com.example.myvillage.cultivation.data.AdvancementKind;

import java.util.Objects;

public final class AdvancementProfileTransition {
    private AdvancementProfileTransition() {
    }

    public static CultivationProfile successReplacement(
            CultivationProfile current, AdvancementContext context) {
        requireSource(current, context);
        AdvancementDefinition definition = context.definition();
        if (current.cultivationProgress() < context.cultivationCap()) {
            throw new IllegalArgumentException("Advancement progress requirement is not satisfied");
        }
        if (current.stability() < definition.requiredStability()) {
            throw new IllegalArgumentException("Advancement stability requirement is not satisfied");
        }
        return current
                .withRealmAndStage(definition.targetRealm(), definition.targetStage())
                .withCultivationProgress(0)
                .withStability(current.stability() / 2);
    }

    public static CultivationProfile interruptionPenaltyReplacement(
            CultivationProfile current, AdvancementContext context) {
        requireSource(current, context);
        AdvancementDefinition definition = context.definition();
        if (definition.kind() != AdvancementKind.BOTTLENECK
                || definition.interruptionStabilityLoss() == 0) {
            return current;
        }
        return current.withStability(Math.max(
                0, current.stability() - definition.interruptionStabilityLoss()));
    }

    private static void requireSource(
            CultivationProfile current, AdvancementContext context) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(context, "context");
        if (!current.realmId().equals(context.sourceRealm())
                || !current.stageId().equals(context.sourceStage())) {
            throw new IllegalArgumentException("Cultivation profile no longer matches advancement source");
        }
    }
}
