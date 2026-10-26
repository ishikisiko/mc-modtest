package com.example.myvillage.combat.session;

import com.example.myvillage.combat.definition.CombatSkillDefinition;
import com.example.myvillage.cultivation.qi.OpenMeridians;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.Objects;

/** Skill metadata alongside the one CombatSession. It cannot apply damage or replace profiles. */
public final class CombatSkillSession {
    private final CombatSkillDefinition skill;
    private final ResourceLocation technique;
    private final int baseCost;
    private final long chargeStart;
    private final long revision;
    private long releaseTick = -1;
    private int chargeLevel;
    private Vec3 origin = Vec3.ZERO;
    private Vec3 direction = new Vec3(0, 0, 1);
    private double travelled;
    private boolean waveStopped;
    private long paidPower;
    private long hitRefund;
    private boolean refundClaimed;
    private boolean masteryClaimed;
    private final int growthCostBp;
    private final int growthDamageBp;

    public CombatSkillSession(CombatSkillDefinition skill, ResourceLocation technique, int baseCost,
                              long chargeStart, long revision) {
        this(skill, technique, baseCost, chargeStart, revision, 10000, 10000);
    }
    public CombatSkillSession(CombatSkillDefinition skill, ResourceLocation technique, int baseCost, long chargeStart,
            long revision, int growthCostBp, int growthDamageBp) {
        if (growthCostBp <= 0 || growthDamageBp <= 0) throw new IllegalArgumentException("Invalid frozen growth effects");
        this.growthCostBp = growthCostBp; this.growthDamageBp = growthDamageBp;
        this.skill = Objects.requireNonNull(skill); this.technique = Objects.requireNonNull(technique);
        if (baseCost < 0 || chargeStart < 0 || revision < 1) throw new IllegalArgumentException("Invalid skill start");
        this.baseCost = baseCost; this.chargeStart = chargeStart; this.revision = revision;
    }
    public CombatSkillDefinition skill() { return skill; }
    public ResourceLocation technique() { return technique; }
    public long revision() { return revision; }
    public boolean charging() { return releaseTick < 0; }
    public boolean full(long tick) { return tick - chargeStart >= skill.chargeTicks(); }
    public int levelAt(long tick) { return charging() ? skill.chargeLevel(tick - chargeStart) : chargeLevel; }
    public long costAt(long tick) { return com.example.myvillage.cultivation.growth.TechniqueGrowth.cost(skill.qiCost(baseCost, levelAt(tick)), growthCostBp); }
    public double growthDamageMultiplier() { return growthDamageBp / 10000.0; }
    /** Only a successful direct hit claims this; multi-target and multi-tick casts earn once. */
    public boolean claimMastery() {
        if (charging() || masteryClaimed) return false;
        masteryClaimed = true;
        return true;
    }
    public long chargeStartTick() { return chargeStart; }
    public long releaseTick() { return releaseTick; }
    public int chargeLevel() { return chargeLevel; }
    public Vec3 direction() { return direction; }
    public Vec3 origin() { return origin; }
    public Vec3 position() { return origin.add(direction.scale(travelled)); }
    public boolean waveStopped() { return waveStopped; }
    public double scale() { return skill.scale(chargeLevel); }
    public long paidPower() { return paidPower; }

    /** Called only by this cast's accepted direct skill hit, never by damage-event derivatives. */
    public long claimHitRefund() {
        if (charging() || refundClaimed) return 0;
        refundClaimed = true;
        return hitRefund;
    }

    /** Called only AFTER CultivationService commits the cost. A duplicate release cannot mutate state. */
    public boolean release(long tick, Vec3 origin, Vec3 direction) {
        return release(tick, origin, direction, 0, 0);
    }

    /** Freeze the final post-forging payment and authored refund when this cast is paid. */
    public boolean release(long tick, Vec3 origin, Vec3 direction, long paidPower, double refundFraction) {
        if (!charging()) return false;
        if (tick < chargeStart || !Double.isFinite(origin.lengthSqr())
                || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1.0E-12) {
            throw new IllegalArgumentException("Invalid server release geometry/time");
        }
        long refund = OpenMeridians.refund(paidPower, refundFraction);
        this.chargeLevel = levelAt(tick); this.releaseTick = tick;
        this.origin = origin; this.direction = direction.normalize();
        this.paidPower = paidPower; this.hitRefund = refund;
        return true;
    }
    public boolean waveActive(long tick) {
        return !charging() && !waveStopped && tick >= releaseTick + skill.releaseDelay(chargeLevel)
                && travelled < skill.range(chargeLevel);
    }
    public Vec3 nextPosition() { return origin.add(direction.scale(Math.min(skill.range(chargeLevel), travelled + skill.speed()))); }
    public void advance(Vec3 position, boolean blocked) {
        travelled = Math.min(skill.range(chargeLevel), origin.distanceTo(position));
        waveStopped = blocked || travelled >= skill.range(chargeLevel) - 1.0E-8;
    }
}
