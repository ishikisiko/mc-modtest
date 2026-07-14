package com.example.myvillage.client.combat;

import com.example.myvillage.MyVillageMod;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public final class CombatAnimationController {
    public static final ResourceLocation LAYER_ID = id("sword_combat");
    public static final ResourceLocation SMOKE_ANIMATION = id("sword_mode_enter");
    public static final int LAYER_PRIORITY = 1600;

    private static final Logger LOGGER = LoggerFactory.getLogger(CombatAnimationController.class);
    private static boolean factoryRegistered;

    private CombatAnimationController() {
    }

    public static void registerFactory() {
        if (factoryRegistered) {
            return;
        }
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER_ID,
                LAYER_PRIORITY,
                CombatAnimationController::createController);
        factoryRegistered = true;
        LOGGER.info("PAL_SMOKE factory_registered layer={} priority={}", LAYER_ID, LAYER_PRIORITY);
    }

    private static PlayerAnimationController createController(AbstractClientPlayer player) {
        PlayerAnimationController controller = new PlayerAnimationController(
                player,
                (ignoredController, ignoredState, ignoredSetter) -> PlayState.STOP);
        controller.setFirstPersonMode(FirstPersonMode.DISABLED);
        LOGGER.debug("Created sword-combat PAL layer for client player {}", player.getUUID());
        return controller;
    }

    public static boolean play(
            AbstractClientPlayer player,
            ResourceLocation animationId,
            float elapsedTicks) {
        QingfengFirstPersonAnimator.play(player, animationId, elapsedTicks);
        Optional<PlayerAnimationController> controller = controller(player);
        boolean accepted = controller.isPresent()
                && controller.get().triggerAnimation(animationId, Math.max(0.0F, elapsedTicks));
        LOGGER.info(
                "PAL_SMOKE play player={} animation={} elapsed_ticks={} accepted={}",
                player.getUUID(),
                animationId,
                Math.max(0.0F, elapsedTicks),
                accepted);
        return accepted;
    }

    public static boolean transition(AbstractClientPlayer player, ResourceLocation animationId) {
        QingfengFirstPersonAnimator.stop(player);
        Optional<PlayerAnimationController> controller = controller(player);
        boolean accepted = controller.isPresent()
                && controller.get().replaceAnimationWithFade(
                        AbstractFadeModifier.standardFadeIn(3, EasingType.EASE_IN_OUT_SINE),
                        animationId);
        LOGGER.info(
                "PAL_SMOKE transition player={} animation={} accepted={}",
                player.getUUID(),
                animationId,
                accepted);
        return accepted;
    }

    public static boolean stop(AbstractClientPlayer player) {
        QingfengFirstPersonAnimator.stop(player);
        Optional<PlayerAnimationController> controller = controller(player);
        if (controller.isEmpty()) {
            LOGGER.info("PAL_SMOKE stop player={} controller_present=false", player.getUUID());
            return false;
        }

        PlayerAnimationController animationController = controller.get();
        boolean activeBefore = animationController.isActive();
        boolean stoppedTriggered = animationController.stopTriggeredAnimation();
        animationController.stop();
        animationController.forceAnimationReset();
        boolean activeAfter = animationController.isActive();
        LOGGER.info(
                "PAL_SMOKE stop player={} active_before={} triggered_stopped={} active_after={}",
                player.getUUID(),
                activeBefore,
                stoppedTriggered,
                activeAfter);
        return !activeAfter;
    }

    public static boolean isActive(AbstractClientPlayer player) {
        return controller(player).map(PlayerAnimationController::isActive).orElse(false);
    }

    private static Optional<PlayerAnimationController> controller(AbstractClientPlayer player) {
        try {
            IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER_ID);
            return layer instanceof PlayerAnimationController controller
                    ? Optional.of(controller)
                    : Optional.empty();
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("PAL sword-combat layer is unavailable for player {}", player.getUUID(), exception);
            return Optional.empty();
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, path);
    }
}
