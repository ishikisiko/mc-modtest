package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.client.cultivation.ClientCultivationState;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.data.SpiritualElementDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import com.example.myvillage.cultivation.meditation.StudyProgress;
import com.example.myvillage.cultivation.network.CultivationTimeSnapshotPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What one frame of the panel reads: the three read-only client caches and the synchronized
 * definition registries. {@code profile}, {@code time}, and {@code meditation} are null until
 * the server has sent them.
 */
public record PanelContext(
        Minecraft minecraft,
        Font font,
        RegistryAccess registries,
        CultivationProfile profile,
        CultivationTimeSnapshotPayload time,
        MeditationStatus meditation) {
    public static PanelContext capture(Minecraft minecraft, Font font) {
        RegistryAccess registries = minecraft != null && minecraft.level != null
                ? minecraft.level.registryAccess()
                : RegistryAccess.EMPTY;
        return new PanelContext(
                minecraft,
                font,
                registries,
                ClientCultivationState.latest().orElse(null),
                ClientCultivationState.time().orElse(null),
                ClientCultivationState.meditation().orElse(null));
    }

    public String text(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    public String unavailableText() {
        return text("screen.myvillage.cultivation.unavailable");
    }

    public Optional<RealmDefinition> realm(ResourceLocation realmId) {
        return registries.registry(ModCultivationRegistries.REALMS)
                .flatMap(registry -> registry.getOptional(realmId));
    }

    /** The current realm's stages in display order; empty when the realm is unresolved. */
    public List<RealmStageDefinition> currentRealmStages() {
        return realm(profile.realmId())
                .map(realm -> realm.stages().stream()
                        .sorted(Comparator.comparingInt(RealmStageDefinition::sortOrder))
                        .toList())
                .orElse(List.of());
    }

    public Optional<RealmStageDefinition> stage(ResourceLocation realmId, ResourceLocation stageId) {
        return realm(realmId).flatMap(realm -> realm.stages().stream()
                .filter(candidate -> candidate.id().equals(stageId))
                .findFirst());
    }

    public Optional<RealmStageDefinition> currentStage() {
        return stage(profile.realmId(), profile.stageId());
    }

    public Optional<SpiritualElementDefinition> element(ResourceLocation elementId) {
        return registries.registry(ModCultivationRegistries.SPIRITUAL_ELEMENTS)
                .flatMap(registry -> registry.getOptional(elementId));
    }

    public Optional<TechniqueDefinition> technique(ResourceLocation techniqueId) {
        return registries.registry(ModCultivationRegistries.TECHNIQUES)
                .flatMap(registry -> registry.getOptional(techniqueId));
    }

    public Component realmName(ResourceLocation realmId) {
        return realm(realmId)
                .<Component>map(definition -> Component.translatable(definition.translationKey()))
                .orElseGet(() -> unavailable(realmId));
    }

    public Component stageName(ResourceLocation realmId, ResourceLocation stageId) {
        return stage(realmId, stageId)
                .<Component>map(definition -> Component.translatable(definition.translationKey()))
                .orElseGet(() -> unavailable(stageId));
    }

    public Component elementName(ResourceLocation elementId) {
        return element(elementId)
                .<Component>map(definition -> Component.translatable(definition.translationKey()))
                .orElseGet(() -> unavailable(elementId));
    }

    public int elementSortOrder(ResourceLocation elementId) {
        return element(elementId).map(SpiritualElementDefinition::sortOrder).orElse(Integer.MAX_VALUE);
    }

    public int elementColor(ResourceLocation elementId) {
        return 0xFF000000 | element(elementId)
                .flatMap(SpiritualElementDefinition::displayColor)
                .orElse(PanelTheme.FALLBACK_ELEMENT & 0xFFFFFF);
    }

    /** Progress over the stage's cap; a stage without a cap says why it has none. */
    public String progressValue() {
        RealmStageDefinition stage = currentStage().orElse(null);
        if (stage == null) {
            return text("screen.myvillage.cultivation.progress_unavailable", profile.cultivationProgress());
        }
        if (stage.cultivationCap().isPresent()) {
            return text(
                    "screen.myvillage.cultivation.progress_capped",
                    profile.cultivationProgress(),
                    stage.cultivationCap().orElseThrow());
        }
        String suffix = stage.id().equals(ModCultivationRegistries.QI_REFINING_4_STAGE_ID)
                ? text("screen.myvillage.cultivation.progress_release_ceiling")
                : text("screen.myvillage.cultivation.progress_unsupported");
        return profile.cultivationProgress() + " / " + suffix;
    }

    public double progressFraction() {
        return currentStage()
                .flatMap(RealmStageDefinition::cultivationCap)
                .map(cap -> PanelReadouts.fraction(profile.cultivationProgress(), cap))
                .orElse(0.0D);
    }

    /** True once progress has reached the current stage's cap. */
    public boolean progressFull() {
        return currentStage()
                .flatMap(RealmStageDefinition::cultivationCap)
                .map(cap -> profile.cultivationProgress() >= cap)
                .orElse(false);
    }

    public String stabilityValue() {
        return currentStage()
                .flatMap(RealmStageDefinition::stabilityCap)
                .map(cap -> profile.stability() + " / " + cap)
                .orElse(profile.stability() + " / " + unavailableText());
    }

    public double stabilityFraction() {
        return currentStage()
                .flatMap(RealmStageDefinition::stabilityCap)
                .map(cap -> PanelReadouts.fraction(profile.stability(), cap))
                .orElse(0.0D);
    }

    /**
     * The session state's display name (研读中 while a study session runs), or the waiting text before
     * the first status arrives.
     */
    public String sessionText() {
        return text(PanelReadouts.sessionKey(meditation));
    }

    /** The running study session's progress; empty without a status or outside a study session. */
    public Optional<StudyProgress> study() {
        return meditation == null ? Optional.empty() : meditation.study();
    }

    /** A technique's name, or its raw id with the unavailable marker when the registry does not know it. */
    public Component techniqueName(ResourceLocation techniqueId) {
        return technique(techniqueId)
                .<Component>map(definition -> Component.translatable(definition.translationKey()))
                .orElseGet(() -> unavailable(techniqueId));
    }

    public int sessionColor() {
        if (meditation == null || !meditation.state().active()) {
            return PanelTheme.MUTED;
        }
        if (meditation.state().advancing()) {
            return PanelTheme.GOLD_BRIGHT;
        }
        return meditation.state().preparing() ? PanelTheme.AMBER : PanelTheme.JADE;
    }

    /** "1 年 第 1 周 第 1 日", or the waiting text before the first time snapshot arrives. */
    public String calendarValue() {
        if (time == null) {
            return text("screen.myvillage.cultivation.time_waiting");
        }
        long elapsed = time.elapsedCalendarTicks();
        return text(
                "screen.myvillage.cultivation.calendar_value",
                PanelReadouts.calendarYear(elapsed, time.ticksPerDay(), time.daysPerYear()),
                PanelReadouts.calendarWeek(elapsed, time.ticksPerDay(), time.daysPerYear(), time.daysPerWeek()),
                PanelReadouts.calendarDayOfWeek(
                        elapsed, time.ticksPerDay(), time.daysPerYear(), time.daysPerWeek()));
    }

    /** Remaining over maximum lifespan in years; exhaustion and unavailability are spelled out. */
    public String remainingLifespanValue() {
        if (time == null) {
            return text("screen.myvillage.cultivation.time_waiting");
        }
        if (!time.lifespanAvailable()) {
            return unavailableText();
        }
        if (time.exhausted()) {
            return text("screen.myvillage.cultivation.lifespan_exhausted", time.maximumLifespanYears());
        }
        return text(
                "screen.myvillage.cultivation.lifespan_fraction",
                PanelReadouts.yearsCeil(time.remainingLifespanTicks(), time.ticksPerDay(), time.daysPerYear()),
                time.maximumLifespanYears());
    }

    public Component unavailable(ResourceLocation id) {
        return Component.literal(id.toString())
                .append(" ")
                .append(Component.translatable("screen.myvillage.cultivation.unavailable"))
                .withStyle(ChatFormatting.RED);
    }
}
