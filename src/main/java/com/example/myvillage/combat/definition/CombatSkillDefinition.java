package com.example.myvillage.combat.definition;

import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.Optional;

/** Bundled wave skill. CombatSession still owns the action, deduplication and damage bookkeeping. */
public record CombatSkillDefinition(ResourceLocation id, ResourceLocation school, int chargeTicks,
        int releaseTicks, int recoveryTicks, double speed, double range, double halfWidth,
        double halfHeight, int maximumTargets, double damageMultiplier, double chargeScale,
        double movementMultiplier, double launchHeight, ResourceLocation feedbackMove, ResourceLocation reactionMove, int chargePoseTicks) {
    public CombatSkillDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(school); Objects.requireNonNull(feedbackMove); Objects.requireNonNull(reactionMove);
        if (chargePoseTicks < 0 || chargePoseTicks >= releaseTicks || chargePoseTicks > chargeTicks
                || (chargePoseTicks > 0 && chargeTicks > 255) || chargeTicks < 1 || chargeTicks > 1200 || releaseTicks < 1 || releaseTicks > 1200
                || recoveryTicks < 1 || recoveryTicks > 1200 || maximumTargets < 1 || maximumTargets > 64
                || !bounded(speed, 0, 16) || !bounded(range, 0, 64) || !bounded(halfWidth, 0, 8)
                || !bounded(halfHeight, 0, 8) || !bounded(damageMultiplier, 0, 100)
                || !bounded(chargeScale, 0, 4) || chargeScale < 1
                || !bounded(movementMultiplier, 0, 1) || !bounded(launchHeight, 0, 3)
                || range * chargeScale > 64 || range * chargeScale / speed > 1200) {
            throw new IllegalArgumentException("Skill numbers exceed bounded wave limits");
        }
    }
    private static boolean bounded(double value, double lower, double upper) {
        return Double.isFinite(value) && value > lower && value <= upper;
    }
    public int chargeLevel(long elapsed) {
        return (int) Math.floor(Math.min(chargeTicks, Math.max(0L, elapsed)) * 255.0 / chargeTicks);
    }
    public double scale(int level) {
        if (level < 0 || level > 255) throw new IllegalArgumentException("Invalid charge level");
        return 1.0 + (chargeScale - 1.0) * level / 255.0;
    }
    /** The preparation already played while charging, reconstructed exactly from the bounded charge byte. */
    public int releaseAnimationOffset(int level) {
        if (level < 0 || level > 255) throw new IllegalArgumentException("Invalid charge level");
        return Math.min(chargePoseTicks, (int) Math.ceil((double) level * chargeTicks / 255));
    }
    public int releaseDelay(int level) { return releaseTicks - releaseAnimationOffset(level); }
    public double chargePoseTick(double elapsed) {
        return Double.isFinite(elapsed) ? Math.clamp(elapsed, 0, chargePoseTicks) : 0;
    }
    public long qiCost(int base, int level) {
        if (base < 0) throw new IllegalArgumentException("Negative qi cost");
        return (long) Math.ceil(base * scale(level));
    }
    public double range(int level) { return range * scale(level); }
    public int travelTicks(int level) { return (int) Math.ceil(range(level) / speed); }
    public AttackMoveDefinition releaseMove(CombatStyles registry, int level) {
        AttackMoveDefinition template = registry.move(feedbackMove).orElseThrow().move();
        int delay = releaseDelay(level);
        int end = delay + travelTicks(level) - 1;
        int total = end + 1 + recoveryTicks;
        // Presentation can change independently of the authored hit reaction; neither uses the melee hitbox.
        return new AttackMoveDefinition(template.id(), template.displayKey(), template.kind(), total,
                delay, end, damageMultiplier * scale(level), maximumTargets, range(level),
                end + 1, total, registry.move(reactionMove).orElseThrow().move().reaction(), new AnimationDefinition(template.id(), total),
                template.hitbox(), Optional.empty(), template.feedback(),
                new CameraCues(template.camera().hitPitchKick(), template.camera().hitRollKick(),
                        template.camera().hitFovPunch(), template.camera().swingLeanDegrees(), 0), Optional.empty());
    }
}
