package com.example.myvillage.cultivation.study;

import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Entry point for reading a technique manual (秘籍): the item calls this on the server when it is used.
 * It runs the start checks ({@link StudyStart}, then the shared meditation checks in
 * {@link MeditationManager#requestStudy}) and starts a study session reading the manual in the used hand's
 * inventory slot. Each refusal is a chat line; success returns {@code consume}, which does not swing the arm
 * (a swing would interrupt the session it just started).
 */
public final class ManualStudy {
    private ManualStudy() {
    }

    /** Begin studying the manual held in {@code hand}. */
    public static InteractionResultHolder<ItemStack> use(ServerPlayer player, InteractionHand hand) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(hand, "hand");
        ItemStack stack = player.getItemInHand(hand);
        Optional<Registry<TechniqueDefinition>> registry =
                player.registryAccess().registry(ModCultivationRegistries.TECHNIQUES);
        Function<ResourceLocation, TechniqueDefinition> techniques = registry
                .<Function<ResourceLocation, TechniqueDefinition>>map(r -> r::get)
                .orElse(id -> null);
        Optional<ResourceLocation> techniqueId = ManualStacks.technique(stack, techniques);
        Optional<StudyStart.Refusal> refusal = StudyStart.refusal(
                techniqueId,
                CultivationService.getProfile(player),
                techniques,
                MeditationManager.realmLookup(player),
                MeditationManager.elementLookup(player),
                MeditationManager.active(player));
        TechniqueDefinition definition = techniqueId.map(techniques).orElse(null);
        if (refusal.isPresent()) {
            TechniqueDefinition previous = definition == null
                    ? null
                    : definition.previous().map(techniques).orElse(null);
            StudyMessages.refused(player, refusal.get(), definition, previous);
            return InteractionResultHolder.fail(stack);
        }

        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
        MeditationStatus status = MeditationManager.requestStudy(
                player,
                slot,
                techniqueId.orElseThrow(),
                StudyStep.progress(techniqueId.get(), ManualStacks.comprehension(stack), StudyRules.of(definition)));
        if (status.reason() != MeditationStopReason.STUDY_ACCEPTED) {
            StudyMessages.refused(player, status.reason());
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.consume(stack);
    }
}
