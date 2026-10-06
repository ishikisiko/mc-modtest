package com.example.myvillage.cultivation.study;

import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** Chat lines of the study mechanic; every key is {@code message.myvillage.cultivation.study.*}. */
public final class StudyMessages {
    public static final String PREFIX = "message.myvillage.cultivation.study.";

    private StudyMessages() {
    }

    /** The key for a start refusal, e.g. {@code message.myvillage.cultivation.study.already_learned}. */
    public static String key(StudyStart.Refusal refusal) {
        return PREFIX + refusal.name().toLowerCase(Locale.ROOT);
    }

    /** Explains a refusal; {@code technique} names the manual's technique and {@code previous} the one before it. */
    public static void refused(
            ServerPlayer player, StudyStart.Refusal refusal, TechniqueDefinition technique, TechniqueDefinition previous) {
        Component subject = refusal == StudyStart.Refusal.PREVIOUS_REQUIRED
                ? name(previous)
                : name(technique);
        player.sendSystemMessage(Component.translatable(key(refusal), subject));
    }

    /** A physical or lifespan refusal from the shared meditation checks, with that reason's own text. */
    public static void refused(ServerPlayer player, MeditationStopReason reason) {
        player.sendSystemMessage(Component.translatable(PREFIX + "refused",
                Component.translatable("message.myvillage.cultivation.session." + reason.name().toLowerCase(Locale.ROOT))));
    }

    public static void gatePassed(ServerPlayer player, int stabilityPaid) {
        player.sendSystemMessage(Component.translatable(PREFIX + "gate_passed", stabilityPaid));
    }

    public static void gateBlocked(ServerPlayer player, int gateCost, int shortfall) {
        player.sendSystemMessage(Component.translatable(PREFIX + "gate", gateCost, shortfall));
    }

    public static void completed(ServerPlayer player, TechniqueDefinition technique) {
        player.sendSystemMessage(Component.translatable(PREFIX + "complete", name(technique)));
    }

    private static Component name(TechniqueDefinition definition) {
        return definition == null
                ? Component.literal("?")
                : Component.translatable(definition.translationKey());
    }
}
