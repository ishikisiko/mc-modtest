package com.example.myvillage.client.combat;

import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.example.myvillage.combat.definition.MoveFeedback;
import com.example.myvillage.combat.runtime.CombatReactionService;
import com.example.myvillage.combat.session.CombatSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Attacker-local camera feedback. Presentation only: it changes the render camera and the
 * world-pass FOV, never the player's aim, picking or movement.
 *
 * <ul>
 *     <li>Trauma model: each confirmed hit adds the move's {@code cameraTrauma}; the shake is
 *     {@code trauma²} times at most 2.5° roll, 1.5° pitch and 1.0° yaw, driven by ~22 Hz smooth
 *     noise sampled with the partial tick. Trauma decays linearly at 1.6 per second.</li>
 *     <li>Directional kicks per move (degrees, pitch up positive): 撩 +0.6, 斜 −0.8, lunge −1.2
 *     plus a −3° FOV punch (in over 2 ticks, out over 5), 横 leans into the sweep.</li>
 *     <li>Each swing leans the camera 0.3° into the cut; the lunge's step widens the FOV by 2°.</li>
 * </ul>
 * Everything scales with the accessibility options {@code screenEffectScale} (angles) and
 * {@code fovEffectScale} (FOV), and is halved in third person.
 */
public final class CombatCameraFx {
    static final float MAXIMUM_ROLL = 2.5F;
    static final float MAXIMUM_PITCH = 1.5F;
    static final float MAXIMUM_YAW = 1.0F;
    /** Trauma lost per tick: 1.6 per second at 20 ticks per second. */
    static final float TRAUMA_DECAY_PER_TICK = 1.6F / 20.0F;
    /** Noise samples per tick: about 22 Hz. */
    static final float NOISE_SAMPLES_PER_TICK = 22.0F / 20.0F;
    static final float THIRD_PERSON_SCALE = 0.5F;
    static final float SWING_LEAN_DEGREES = 0.3F;
    static final float LUNGE_FOV_SURGE = 2.0F;

    /** Per-move hit kicks, index-aligned with BasicSwordStyle: pitch (up positive) and roll. */
    private static final float[] HIT_PITCH_KICK = {0.0F, 0.0F, 0.6F, -0.8F, -1.2F};
    private static final float[] HIT_ROLL_KICK = {0.0F, 0.5F, 0.0F, 0.4F, 0.0F};
    private static final float[] HIT_FOV_PUNCH = {0.0F, 0.0F, 0.0F, 0.0F, -3.0F};
    /** Swing lean direction per move: +1 leans right (the 横 and 斜 sweeps), -1 left (撩), 0 none. */
    private static final float[] SWING_LEAN_SIGN = {0.0F, 1.0F, -1.0F, 1.0F, 0.0F};

    private static float trauma;
    private static double traumaTime = Double.NaN;
    private static long lastKickRevision = Long.MIN_VALUE;
    private static final List<Kick> KICKS = new ArrayList<>();

    private CombatCameraFx() {
    }

    /**
     * Adds camera trauma (0..1, clamped) plus an optional pitch kick (degrees, up positive) and
     * FOV punch (degrees, negative narrows the view).
     */
    static void addTrauma(float addedTrauma, float pitchKickDeg, float fovPunchDeg) {
        double now = now(0.0F);
        if (Double.isNaN(now)) {
            return;
        }
        trauma = Math.min(1.0F, currentTrauma(now) + Math.max(0.0F, addedTrauma));
        traumaTime = now;
        boolean heavy = Math.abs(pitchKickDeg) >= 1.0F;
        if (pitchKickDeg != 0.0F) {
            KICKS.add(new Kick(now, Channel.PITCH, pitchKickDeg, 1.0F, heavy ? 4.0F : 3.0F));
        }
        if (fovPunchDeg != 0.0F) {
            KICKS.add(new Kick(now, Channel.FOV, fovPunchDeg, 2.0F, 5.0F));
        }
    }

    /**
     * The local attacker's hit confirm. Trauma is added per confirm (a later confirm in the same
     * move adds half); the directional kick plays once per action revision.
     */
    static void onHitConfirm(int moveIndex, long revision) {
        if (moveIndex < 0 || moveIndex >= BasicSwordStyle.DEFINITION.moves().size()) {
            addTrauma(0.25F, 0.0F, 0.0F);
            return;
        }
        MoveFeedback feedback = BasicSwordStyle.feedback(moveIndex);
        boolean firstInAction = revision != lastKickRevision;
        lastKickRevision = revision;
        if (!firstInAction) {
            addTrauma(feedback.cameraTrauma() * 0.5F, 0.0F, 0.0F);
            return;
        }
        addTrauma(feedback.cameraTrauma(), kick(HIT_PITCH_KICK, moveIndex), kick(HIT_FOV_PUNCH, moveIndex));
        float roll = kick(HIT_ROLL_KICK, moveIndex);
        double now = now(0.0F);
        if (roll != 0.0F && !Double.isNaN(now)) {
            KICKS.add(new Kick(now, Channel.ROLL, roll, 1.0F, 3.0F));
        }
    }

    /** A small lean into the cut as the blade starts moving; plays on hits and whiffs alike. */
    static void swingLean(int moveIndex) {
        float sign = kick(SWING_LEAN_SIGN, moveIndex);
        double now = now(0.0F);
        if (sign != 0.0F && !Double.isNaN(now)) {
            KICKS.add(new Kick(now, Channel.ROLL, SWING_LEAN_DEGREES * sign, 1.5F, 3.0F));
        }
    }

    /** FOV widening as the lunge step launches the attacker forward. */
    static void lungeSurge() {
        double now = now(0.0F);
        if (!Double.isNaN(now)) {
            KICKS.add(new Kick(now, Channel.FOV, LUNGE_FOV_SURGE, 2.0F, 6.0F));
        }
    }

    static void clear() {
        trauma = 0.0F;
        traumaTime = Double.NaN;
        lastKickRevision = Long.MIN_VALUE;
        KICKS.clear();
    }

    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || (trauma <= 0.0F && KICKS.isEmpty())) {
            return;
        }
        double now = now((float) event.getPartialTick());
        if (Double.isNaN(now)) {
            return;
        }
        float scale = minecraft.options.screenEffectScale().get().floatValue();
        if (event.getCamera().isDetached()) {
            scale *= THIRD_PERSON_SCALE;
        }
        if (scale <= 0.0F) {
            return;
        }
        float shake = shake(currentTrauma(now));
        float time = (float) (now * NOISE_SAMPLES_PER_TICK);
        float roll = MAXIMUM_ROLL * shake * noise(0, time) + kickSum(Channel.ROLL, now);
        float pitchUp = MAXIMUM_PITCH * shake * noise(1, time) + kickSum(Channel.PITCH, now);
        float yaw = MAXIMUM_YAW * shake * noise(2, time);
        pruneKicks(now);
        // Minecraft pitch is positive looking down, so an upward kick subtracts.
        event.setRoll(event.getRoll() + roll * scale);
        event.setPitch(event.getPitch() - pitchUp * scale);
        event.setYaw(event.getYaw() + yaw * scale);
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov() || KICKS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        double now = now((float) event.getPartialTick());
        if (Double.isNaN(now)) {
            return;
        }
        float offset = kickSum(Channel.FOV, now);
        if (offset == 0.0F) {
            return;
        }
        float scale = minecraft.options.fovEffectScale().get().floatValue();
        event.setFOV(event.getFOV() + offset * scale);
    }

    /** Shake strength from trauma: squared, so small trauma barely moves the camera. */
    static float shake(float currentTrauma) {
        float bounded = Math.max(0.0F, Math.min(1.0F, currentTrauma));
        return bounded * bounded;
    }

    /** Trauma remaining {@code elapsedTicks} after it was {@code trauma}. */
    static float decayedTrauma(float startTrauma, double elapsedTicks) {
        return (float) Math.max(0.0, startTrauma - TRAUMA_DECAY_PER_TICK * Math.max(0.0, elapsedTicks));
    }

    /**
     * Kick envelope {@code elapsed} ticks after the kick: eases in to 1 over {@code attack}
     * ticks, then falls back to 0 over {@code release} ticks with a quadratic tail.
     */
    static float envelope(float elapsed, float attack, float release) {
        if (elapsed < 0.0F) {
            return 0.0F;
        }
        if (elapsed < attack) {
            return (float) Math.sin(Math.PI * 0.5 * elapsed / attack);
        }
        float remaining = 1.0F - (elapsed - attack) / release;
        return remaining <= 0.0F ? 0.0F : remaining * remaining;
    }

    /** Smooth value noise in [-1, 1]; one independent stream per channel. */
    static float noise(int channel, float time) {
        int index = (int) Math.floor(time);
        float fraction = time - index;
        float smooth = fraction * fraction * (3.0F - 2.0F * fraction);
        float first = hash(channel, index);
        float second = hash(channel, index + 1);
        return first + (second - first) * smooth;
    }

    private static float hash(int channel, int index) {
        int value = index * 0x27D4EB2D + channel * 0x165667B1;
        value ^= value >>> 15;
        value *= 0x85EBCA6B;
        value ^= value >>> 13;
        value *= 0xC2B2AE35;
        value ^= value >>> 16;
        return (value & 0xFFFF) / 32767.5F - 1.0F;
    }

    private static float currentTrauma(double now) {
        if (Double.isNaN(traumaTime) || trauma <= 0.0F) {
            return 0.0F;
        }
        if (now < traumaTime) {
            // The world clock went backwards (new world, time command): start over.
            trauma = 0.0F;
            return 0.0F;
        }
        return decayedTrauma(trauma, now - traumaTime);
    }

    private static float kickSum(Channel channel, double now) {
        float sum = 0.0F;
        for (Kick kick : KICKS) {
            if (kick.channel() == channel) {
                sum += kick.degrees() * envelope((float) (now - kick.startTime()), kick.attack(), kick.release());
            }
        }
        return sum;
    }

    private static void pruneKicks(double now) {
        Iterator<Kick> iterator = KICKS.iterator();
        while (iterator.hasNext()) {
            Kick kick = iterator.next();
            double elapsed = now - kick.startTime();
            if (elapsed < 0.0 || elapsed > kick.attack() + kick.release()) {
                iterator.remove();
            }
        }
        if (currentTrauma(now) <= 0.0F) {
            trauma = 0.0F;
        }
    }

    private static float kick(float[] table, int moveIndex) {
        return moveIndex >= 0 && moveIndex < table.length ? table[moveIndex] : 0.0F;
    }

    private static double now(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? Double.NaN : minecraft.level.getGameTime() + partialTick;
    }

    private enum Channel {
        ROLL,
        PITCH,
        FOV
    }

    private record Kick(double startTime, Channel channel, float degrees, float attack, float release) {
    }

    /** Server-side combat slows that must not read as a slowness zoom. */
    private static final ResourceLocation[] COMBAT_SLOW_MODIFIERS = {
            CombatSessionManager.COMMIT_MODIFIER_ID,
            CombatReactionService.STUN_MODIFIER_ID
    };

    /**
     * Vanilla narrows the FOV when movement speed drops (AbstractClientPlayer#getFieldOfViewModifier).
     * The swing commitment and hitstun slows are movement locks, not a status effect, so this
     * restores the FOV the player would have without them. Other speed changes still apply.
     */
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        float ratio = combatSlowFovCorrection(event.getPlayer());
        if (ratio != 1.0F) {
            event.setNewFovModifier(event.getNewFovModifier() * ratio);
        }
    }

    static float combatSlowFovCorrection(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        float walking = player.getAbilities().getWalkingSpeed();
        if (speed == null || walking == 0.0F) {
            return 1.0F;
        }
        double factor = 1.0;
        for (ResourceLocation id : COMBAT_SLOW_MODIFIERS) {
            AttributeModifier modifier = speed.getModifier(id);
            if (modifier != null && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                factor *= 1.0 + modifier.amount();
            }
        }
        return fovCorrection(speed.getValue(), factor, walking);
    }

    /** Ratio of the vanilla speed FOV term without the combat slow to the term with it. */
    static float fovCorrection(double slowedSpeed, double slowFactor, float walkingSpeed) {
        if (!(slowFactor > 0.0) || slowFactor >= 1.0) {
            return 1.0F;
        }
        float slowed = ((float) slowedSpeed / walkingSpeed + 1.0F) / 2.0F;
        float unslowed = ((float) (slowedSpeed / slowFactor) / walkingSpeed + 1.0F) / 2.0F;
        return slowed > 0.0F ? unslowed / slowed : 1.0F;
    }
}
