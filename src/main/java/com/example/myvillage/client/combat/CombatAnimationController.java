package com.example.myvillage.client.combat;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.combat.definition.CombatStyles;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.SpeedModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Third-person sword layer on top of PAL. Presentation only: the server decides timing, hits and
 * steps; this class only decides how the body blends between what the server says is playing.
 *
 * <ul>
 *     <li>A START that replaces a move still on screen (a chained combo step) cross-fades from the
 *     current pose over {@link #CHAIN_FADE_TICKS} ticks instead of snapping. The authoritative START
 *     of a locally predicted move is skipped when it is within {@link #RESYNC_TOLERANCE_TICKS} of
 *     what already plays.</li>
 *     <li>A stop is graceful. The pose is held for {@link Lifecycle#STOP_GRACE_TICKS} ticks so that a
 *     chained START (the server sends STOP COMPLETED and the next START in the same tick) or the
 *     ready idle can take over with a fade. Only when nothing takes over does the layer snap off.</li>
 *     <li>When a move runs out before its STOP arrives, the state handler continues straight into
 *     the ready idle. Every move ends on the idle's guard pose, so the body never pops to vanilla.</li>
 *     <li>A {@link SpeedModifier} freezes the body during hit-stop. The local player's rate follows
 *     {@code FirstPersonWeaponAnimator.visualRate(player)} every tick and frame; other players use
 *     {@link #setHitStopRate(AbstractClientPlayer, float)} and repay the frozen time afterwards.</li>
 * </ul>
 */
public final class CombatAnimationController {
    public static final ResourceLocation LAYER_ID = id("sword_combat");
    public static final int LAYER_PRIORITY = 1600;

    /** Cross-fade when a START replaces a move that is still on screen (a chained combo step). */
    static final int CHAIN_FADE_TICKS = 2;
    /** Fade into a move from the ready idle or the mode entry, or for a corrected restart. */
    static final int START_FADE_TICKS = 1;
    /** Fade into the ready idle when a stopping or finished move hands over to it. */
    static final int SETTLE_FADE_TICKS = 4;
    /** Fade into the ready idle from anything else (the historical transition length). */
    static final int IDLE_FADE_TICKS = 3;
    /** The authoritative START of a predicted move restarts it only when it is further off than this. */
    static final float RESYNC_TOLERANCE_TICKS = 1.0F;
    static final float MAX_PLAYBACK_RATE = 4.0F;

    private static final Logger LOGGER = LoggerFactory.getLogger(CombatAnimationController.class);
    private static boolean factoryRegistered;
    private static final Map<ResourceLocation, CachedIdle> CACHED_IDLES = new HashMap<>();

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
        LayerState state = new LayerState(player);
        PlayerAnimationController controller = new PlayerAnimationController(
                player,
                (ignoredController, ignoredState, setter) -> holdReadyIdle(state, setter));
        controller.setFirstPersonMode(FirstPersonMode.DISABLED);
        // First modifier, so hit-stop also slows the chain and idle fades appended after it.
        controller.addModifierBefore(state);
        LOGGER.debug("Created sword-combat PAL layer for client player {}", player.getUUID());
        return controller;
    }

    public static boolean play(
            AbstractClientPlayer player,
            ResourceLocation animationId,
            float elapsedTicks) {
        FirstPersonWeaponAnimator.play(player, animationId, elapsedTicks);
        Optional<PlayerAnimationController> controller = controller(player);
        float elapsed = Math.max(0.0F, elapsedTicks);
        boolean accepted = false;
        if (controller.isPresent()) {
            PlayerAnimationController animationController = controller.get();
            LayerState state = state(player, animationController);
            Lifecycle.Phase phase = state.lifecycle.phase();
            Optional<CombatStyles.MoveRef> moveRef = CombatStyles.bundled().move(animationId);
            boolean move = moveRef.isPresent();
            boolean playingSameMove = move
                    && (phase == Lifecycle.Phase.MOVE || phase == Lifecycle.Phase.STOPPING)
                    && animationId.equals(state.currentAnimation)
                    && animationController.isPlayingTriggeredAnimation();
            if (playingSameMove && !resyncNeeded(animationController.getAnimationTicks(), elapsed)) {
                // The authoritative START confirms what is already on screen; a pending stop is void.
                state.lifecycle.startMove();
                LOGGER.info(
                        "PAL_SMOKE play player={} animation={} elapsed_ticks={} accepted=true kept_prediction=true",
                        player.getUUID(),
                        animationId,
                        elapsed);
                return true;
            }
            int fadeTicks = startFadeTicks(phase, animationController.isActive(), playingSameMove);
            if (fadeTicks > 0) {
                animationController.replaceAnimationWithFade(
                        AbstractFadeModifier.standardFadeIn(fadeTicks, EasingType.EASE_OUT_SINE),
                        animationId);
            }
            accepted = animationController.triggerAnimation(animationId, elapsed);
            if (accepted) {
                state.currentAnimation = animationId;
                if (move) {
                    state.readyIdle = moveRef.get().style().readyIdleAnimation();
                    state.lifecycle.startMove();
                    state.remoteHitStop.clearDebt();
                } else if (CombatStyles.bundled().isReadyIdle(animationId)) {
                    state.readyIdle = animationId;
                    state.lifecycle.idle();
                } else {
                    state.lifecycle.startEnter();
                }
            }
        }
        LOGGER.info(
                "PAL_SMOKE play player={} animation={} elapsed_ticks={} accepted={}",
                player.getUUID(),
                animationId,
                elapsed,
                accepted);
        return accepted;
    }

    public static boolean transition(AbstractClientPlayer player, ResourceLocation animationId) {
        FirstPersonWeaponAnimator.stop(player);
        Optional<PlayerAnimationController> controller = controller(player);
        boolean accepted = false;
        if (controller.isPresent()) {
            PlayerAnimationController animationController = controller.get();
            LayerState state = state(player, animationController);
            Lifecycle.Phase phase = state.lifecycle.phase();
            boolean heldIdleRunning = animationController.isActive()
                    && !animationController.isPlayingTriggeredAnimation()
                    && state.lifecycle.holdsIdle()
                    && animationId.equals(state.currentAnimation);
            boolean readyIdle = CombatStyles.bundled().isReadyIdle(animationId);
            if (readyIdle && heldIdleRunning) {
                // The state handler already continued the finished move into the ready idle.
                accepted = true;
            } else {
                int fadeTicks = phase == Lifecycle.Phase.STOPPING || phase == Lifecycle.Phase.HELD_IDLE
                        ? SETTLE_FADE_TICKS
                        : IDLE_FADE_TICKS;
                accepted = animationController.replaceAnimationWithFade(
                        AbstractFadeModifier.standardFadeIn(fadeTicks, EasingType.EASE_IN_OUT_SINE),
                        animationId);
            }
            if (accepted) {
                state.currentAnimation = animationId;
                Optional<CombatStyles.MoveRef> moveRef = CombatStyles.bundled().move(animationId);
                if (readyIdle) {
                    state.readyIdle = animationId;
                    state.lifecycle.idle();
                } else if (moveRef.isPresent()) {
                    state.readyIdle = moveRef.get().style().readyIdleAnimation();
                    state.lifecycle.startMove();
                } else {
                    state.lifecycle.startEnter();
                }
            }
        }
        LOGGER.info(
                "PAL_SMOKE transition player={} animation={} accepted={}",
                player.getUUID(),
                animationId,
                accepted);
        return accepted;
    }

    /**
     * Ends the sword layer for this player. The current pose is held for a short grace period so a
     * chained START or the ready idle can fade in from it; if neither claims it, the layer snaps
     * off (stopTriggeredAnimation, stop and forceAnimationReset) on a later client tick.
     */
    public static boolean stop(AbstractClientPlayer player) {
        FirstPersonWeaponAnimator.stop(player);
        Optional<PlayerAnimationController> controller = controller(player);
        if (controller.isEmpty()) {
            LOGGER.info("PAL_SMOKE stop player={} controller_present=false", player.getUUID());
            return false;
        }

        PlayerAnimationController animationController = controller.get();
        LayerState state = state(player, animationController);
        boolean activeBefore = animationController.isActive();
        boolean graceful = activeBefore && state.lifecycle.requestStop(gameTime(player));
        if (!graceful) {
            hardStop(animationController, state);
        }
        boolean activeAfter = isActive(player);
        LOGGER.info(
                "PAL_SMOKE stop player={} active_before={} graceful={} active_after={}",
                player.getUUID(),
                activeBefore,
                graceful,
                activeAfter);
        return !activeAfter;
    }

    /**
     * Development probe: plays {@code animationId} on the local player's layer from
     * {@code elapsedTicks} and holds it at rate zero until {@link #releaseThirdPersonProbe}, a stop,
     * or any real start or transition. Starts from a hard reset so no fade is frozen half-way, and
     * keeps the legs out of {@link LocomotionBlend}. Presentation only; sends nothing.
     */
    public static boolean holdThirdPersonProbe(
            AbstractClientPlayer player,
            ResourceLocation animationId,
            float elapsedTicks) {
        Optional<PlayerAnimationController> controller = controller(player);
        if (controller.isEmpty()) {
            return false;
        }
        PlayerAnimationController animationController = controller.get();
        LayerState state = state(player, animationController);
        hardStop(animationController, state);
        boolean accepted = animationController.triggerAnimation(animationId, Math.max(0.0F, elapsedTicks));
        if (accepted) {
            state.currentAnimation = animationId;
            state.lifecycle.holdProbe();
            state.speed = 0.0F;
        }
        return accepted;
    }

    /** Ends a held third-person probe and returns the layer to the vanilla pose. */
    public static boolean releaseThirdPersonProbe(AbstractClientPlayer player) {
        Optional<PlayerAnimationController> controller = controller(player);
        if (controller.isEmpty()) {
            return false;
        }
        LayerState state = state(player, controller.get());
        if (state.lifecycle.phase() != Lifecycle.Phase.PROBE) {
            return false;
        }
        hardStop(controller.get(), state);
        return true;
    }

    /** True while a third-person probe holds this player's layer. */
    public static boolean thirdPersonProbeHeld(AbstractClientPlayer player) {
        Optional<PlayerAnimationController> controller = controller(player);
        return controller.isPresent()
                && state(player, controller.get()).lifecycle.phase() == Lifecycle.Phase.PROBE;
    }

    /** False while a stopped layer waits for a hand-over, so the client may claim the ready idle. */
    public static boolean isActive(AbstractClientPlayer player) {
        Optional<PlayerAnimationController> controller = controller(player);
        if (controller.isEmpty() || !controller.get().isActive()) {
            return false;
        }
        return state(player, controller.get()).lifecycle.reportsActive();
    }

    /**
     * Presentation-only hit-stop rate for another player's sword layer (0 = frozen, 1 = normal).
     * A rate below 1 lasts {@link RemoteHitStop#RATE_TTL_TICKS} client ticks unless it is set again,
     * so call it every client tick while the freeze lasts (or once for a short default freeze);
     * 1 ends it at once. The frozen time is repaid afterwards so the body still ends together with
     * the server-timed move. The local player's rate always comes from
     * {@code FirstPersonWeaponAnimator.visualRate(player)}, so calls for the local player only
     * record the value.
     */
    public static void setHitStopRate(AbstractClientPlayer player, float rate) {
        Optional<PlayerAnimationController> controller = controller(player);
        if (controller.isEmpty() || !Float.isFinite(rate)) {
            return;
        }
        state(player, controller.get()).remoteHitStop.set(rate, gameTime(player));
    }

    /**
     * The mode-entry animation for this player: the held weapon's style's, or the first style's
     * when no registered weapon is held (entering cultivation mode always plays an entry).
     */
    public static ResourceLocation modeEnterAnimation(AbstractClientPlayer player) {
        CombatStyles styles = CombatStyles.bundled();
        return styles.styleFor(player.getMainHandItem())
                .orElseGet(styles::defaultStyle)
                .modeEnterAnimation();
    }

    /** True when the authoritative start is far enough from what plays to restart the move. */
    static boolean resyncNeeded(float playingTicks, float authoritativeElapsedTicks) {
        return Math.abs(playingTicks - authoritativeElapsedTicks) > RESYNC_TOLERANCE_TICKS;
    }

    /** Fade length used when an animation starts (0 = a plain trigger from the vanilla pose). */
    static int startFadeTicks(Lifecycle.Phase phase, boolean layerActive, boolean correctingSameMove) {
        if (!layerActive) {
            return 0;
        }
        if (correctingSameMove) {
            return START_FADE_TICKS;
        }
        return phase == Lifecycle.Phase.MOVE || phase == Lifecycle.Phase.STOPPING
                ? CHAIN_FADE_TICKS
                : START_FADE_TICKS;
    }

    private static PlayState holdReadyIdle(LayerState state, AnimationController.AnimationSetter setter) {
        if (!state.lifecycle.onTriggeredFinished(gameTime(state.player))) {
            return PlayState.STOP;
        }
        ResourceLocation idleId = state.readyIdle != null
                ? state.readyIdle
                : CombatStyles.bundled().defaultStyle().readyIdleAnimation();
        RawAnimation idle = readyIdle(idleId);
        if (idle == null) {
            return PlayState.STOP;
        }
        state.currentAnimation = idleId;
        return setter.setAnimation(idle, 0);
    }

    private static RawAnimation readyIdle(ResourceLocation idleId) {
        if (!PlayerAnimResources.hasAnimation(idleId)) {
            return null;
        }
        Animation source = PlayerAnimResources.getAnimation(idleId);
        CachedIdle cached = CACHED_IDLES.get(idleId);
        if (cached == null || cached.source() != source) {
            cached = new CachedIdle(source, RawAnimation.begin().thenLoop(source));
            CACHED_IDLES.put(idleId, cached);
        }
        return cached.idle();
    }

    private record CachedIdle(Animation source, RawAnimation idle) {
    }

    private static void hardStop(AnimationController controller, LayerState state) {
        state.lifecycle.reset();
        state.currentAnimation = null;
        state.remoteHitStop.clear();
        state.resetLocomotion();
        state.speed = 1.0F;
        controller.stopTriggeredAnimation();
        controller.stop();
        controller.forceAnimationReset();
    }

    private static float localRate(AbstractClientPlayer player) {
        return clampRate(FirstPersonWeaponAnimator.visualRate(player));
    }

    static float clampRate(float rate) {
        if (!Float.isFinite(rate)) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(MAX_PLAYBACK_RATE, rate));
    }

    private static boolean isLocal(AbstractClientPlayer player) {
        return player == Minecraft.getInstance().player;
    }

    private static long gameTime(AbstractClientPlayer player) {
        return player.level().getGameTime();
    }

    /**
     * The layer state lives in the controller's first modifier, so it is owned by the player's
     * PAL animation stack and needs no static per-player map.
     */
    private static LayerState state(AbstractClientPlayer player, PlayerAnimationController controller) {
        for (int index = 0; index < controller.getModifierCount(); index++) {
            if (controller.getModifier(index) instanceof LayerState state) {
                return state;
            }
        }
        LayerState state = new LayerState(player);
        controller.addModifierBefore(state);
        return state;
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

    /**
     * Per-player layer state, installed as the controller's first modifier: a {@link SpeedModifier}
     * whose rate is refreshed every tick (and every frame for the local player), plus the lifecycle
     * and remote hit-stop bookkeeping. Its tick also expires stops nobody took over. Being the
     * outermost modifier, it also blends the legs back to vanilla walking during the ready guard
     * ({@link LocomotionBlend}).
     */
    private static final class LayerState extends SpeedModifier {
        private final AbstractClientPlayer player;
        private final Lifecycle lifecycle = new Lifecycle();
        private final RemoteHitStop remoteHitStop = new RemoteHitStop();
        private ResourceLocation currentAnimation;
        /** The ready idle of the style last played, which a finished move continues into. */
        private ResourceLocation readyIdle;
        private final PlayerAnimBone vanillaPose = new PlayerAnimBone("vanilla");
        private float previousLocomotion;
        private float locomotion;
        private float locomotionWeight;

        private LayerState(AbstractClientPlayer player) {
            super(1.0F);
            this.player = player;
        }

        @Override
        public void tick(AnimationData data) {
            long now = gameTime(player);
            AnimationController controller = getController();
            if (lifecycle.expired(now) && controller != null) {
                LOGGER.debug("PAL layer for {} was not taken over; returning to the vanilla pose", player.getUUID());
                hardStop(controller, this);
            }
            float remoteRate = remoteHitStop.advance(now);
            speed = lifecycle.frozen() ? 0.0F : isLocal(player) ? localRate(player) : clampRate(remoteRate);
            previousLocomotion = locomotion;
            float target = lifecycle.allowsLocomotion()
                    ? LocomotionBlend.target(player.walkAnimation.speed())
                    : 0.0F;
            locomotion = LocomotionBlend.step(locomotion, target);
            super.tick(data);
        }

        @Override
        public void setupAnim(AnimationData data) {
            if (lifecycle.frozen()) {
                speed = 0.0F;
            } else if (isLocal(player)) {
                speed = localRate(player);
            }
            locomotionWeight = LocomotionBlend.lerp(previousLocomotion, locomotion, data.getPartialTick());
            super.setupAnim(data);
        }

        @Override
        public PlayerAnimBone get3DTransform(PlayerAnimBone bone) {
            LocomotionBlend.Part part = locomotionWeight > 0.0F && !lifecycle.frozen()
                    ? LocomotionBlend.part(bone.getName())
                    : null;
            if (part == null) {
                return super.get3DTransform(bone);
            }
            LocomotionBlend.copyPose(bone, vanillaPose);
            PlayerAnimBone animated = super.get3DTransform(bone);
            LocomotionBlend.apply(part, vanillaPose, animated, locomotionWeight);
            return animated;
        }

        private void resetLocomotion() {
            previousLocomotion = 0.0F;
            locomotion = 0.0F;
            locomotionWeight = 0.0F;
        }
    }

    /**
     * What the sword layer is doing, kept free of PAL types so it can be unit tested. Deadlines
     * are in client game ticks.
     */
    static final class Lifecycle {
        /** Ticks a stopped layer waits for a chained START or the ready idle before snapping off. */
        static final int STOP_GRACE_TICKS = 2;
        /** Ticks a finished move holds the ready idle while its STOP is still on the way. */
        static final int AWAIT_STOP_TICKS = 10;
        static final long NO_DEADLINE = Long.MIN_VALUE;

        enum Phase {
            /** Nothing playing. */
            NONE,
            /** sword_mode_enter playing. */
            ENTER,
            /** A combo move playing. */
            MOVE,
            /** A move ran out before its STOP arrived; the ready idle holds the guard meanwhile. */
            HELD_IDLE,
            /** A stop was requested; the pose is held for a chained START or the ready idle. */
            STOPPING,
            /** The ready idle, claimed by the client combat state. */
            IDLE,
            /** A development probe holds one move frozen until released, stopped, or replaced. */
            PROBE
        }

        private Phase phase = Phase.NONE;
        private long deadline = NO_DEADLINE;

        Phase phase() {
            return phase;
        }

        void startMove() {
            set(Phase.MOVE, NO_DEADLINE);
        }

        void startEnter() {
            set(Phase.ENTER, NO_DEADLINE);
        }

        void idle() {
            set(Phase.IDLE, NO_DEADLINE);
        }

        void reset() {
            set(Phase.NONE, NO_DEADLINE);
        }

        void holdProbe() {
            set(Phase.PROBE, NO_DEADLINE);
        }

        /** A held probe plays at rate zero and never blends the legs. */
        boolean frozen() {
            return phase == Phase.PROBE;
        }

        /**
         * PAL asks the state handler what to play because no triggered animation is running.
         * Returns true when the ready idle should keep the body in the guard.
         */
        boolean onTriggeredFinished(long now) {
            switch (phase) {
                case MOVE -> set(Phase.HELD_IDLE, now + AWAIT_STOP_TICKS);
                case ENTER -> set(Phase.STOPPING, now + STOP_GRACE_TICKS);
                case HELD_IDLE, STOPPING, IDLE -> {
                }
                default -> {
                    return false;
                }
            }
            return true;
        }

        /** Returns false when nothing was playing, in which case the caller stops at once. */
        boolean requestStop(long now) {
            if (phase == Phase.NONE) {
                return false;
            }
            if (phase != Phase.STOPPING) {
                set(Phase.STOPPING, now + STOP_GRACE_TICKS);
            }
            return true;
        }

        boolean reportsActive() {
            return phase != Phase.NONE && phase != Phase.STOPPING;
        }

        /**
         * The ready guard and the mode entry give the legs back to vanilla walking; moves keep
         * their authored footwork.
         */
        boolean allowsLocomotion() {
            return phase == Phase.IDLE || phase == Phase.ENTER;
        }

        boolean holdsIdle() {
            return phase == Phase.HELD_IDLE || phase == Phase.STOPPING || phase == Phase.IDLE;
        }

        boolean expired(long now) {
            return deadline != NO_DEADLINE && now > deadline;
        }

        private void set(Phase next, long nextDeadline) {
            phase = next;
            deadline = nextDeadline;
        }
    }

    /**
     * Hit-stop for players other than the local one, fed by impact payloads. Slowed time becomes a
     * debt that is repaid at up to {@link #CATCH_UP_BONUS} extra speed, so the body still finishes
     * together with the server-timed move.
     */
    static final class RemoteHitStop {
        static final int RATE_TTL_TICKS = 2;
        static final float CATCH_UP_BONUS = 0.5F;

        private float rate = 1.0F;
        private long setTick = Lifecycle.NO_DEADLINE;
        private float debt;

        void set(float requested, long now) {
            rate = clampRate(requested);
            setTick = now;
        }

        /** Playback speed for the tick starting at {@code now}. */
        float advance(long now) {
            boolean fresh = setTick != Lifecycle.NO_DEADLINE && now - setTick < RATE_TTL_TICKS;
            float current = fresh ? rate : 1.0F;
            if (current < 1.0F) {
                debt += 1.0F - current;
                return current;
            }
            if (current > 1.0F) {
                debt = Math.max(0.0F, debt - (current - 1.0F));
                return current;
            }
            if (debt > 0.0F) {
                float bonus = Math.min(debt, CATCH_UP_BONUS);
                debt -= bonus;
                return 1.0F + bonus;
            }
            return 1.0F;
        }

        float debt() {
            return debt;
        }

        void clearDebt() {
            debt = 0.0F;
        }

        void clear() {
            rate = 1.0F;
            setTick = Lifecycle.NO_DEADLINE;
            debt = 0.0F;
        }
    }
}
