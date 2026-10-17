package com.example.myvillage.entity.npc;

import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.runtime.CombatActors;
import com.example.myvillage.combat.runtime.CombatReactionService;
import com.example.myvillage.combat.runtime.CombatStepService;
import com.example.myvillage.combat.session.CombatSessionManager;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Reactive, bounded decision maker. Damage is exclusively the shared combat executor's job. */
public final class NpcCombatGoal extends Goal {
    private final NpcEntity npc;
    public NpcCombatGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }
    @Override public boolean canUse() { return NpcCombatRuntime.canAct(npc) && npc.getTarget() != null; }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    private void stopWalking() {
        npc.getNavigation().stop();
        npc.getMoveControl().setWantedPosition(npc.getX(), npc.getY(), npc.getZ(), 0);
        npc.setZza(0); npc.setXxa(0);
    }
    @Override public void start() { stopWalking(); NpcCombatRuntime.engage(npc, true); }
    @Override public void stop() {
        npc.getNavigation().stop();
        // Stun disables Goal flags temporarily; it must not erase the attacker or resume wages.
        if (npc.getTarget() == null || !NpcCombatRuntime.canAct(npc)) NpcCombatRuntime.disengage(npc);
    }

    @Override public void tick() {
        var target = npc.getTarget();
        if (target == null) return;
        var rules = NpcCombatRules.get();
        long now = npc.level().getGameTime();
        if (!CombatActors.mayHarm(npc, target) || target.isRemoved()
                || npc.position().distanceToSqr(npc.combatAnchor) > rules.pursuitRadius() * rules.pursuitRadius()) {
            NpcCombatRuntime.disengage(npc); return;
        }
        if (npc.hasLineOfSight(target)) npc.lastThreatTick = now;
        if (now - npc.lastThreatTick > rules.lostTargetTicks()
                || npc.distanceToSqr(target) > rules.followRange() * rules.followRange()) {
            NpcCombatRuntime.disengage(npc); return;
        }
        if (CombatSessionManager.hasAction(npc)) stopWalking();
        if (CombatReactionService.isStaggered(npc) || now < npc.nextCombatDecision) return;
        npc.nextCombatDecision = now + rules.decisionTicks();
        if (CombatSessionManager.hasAction(npc)) {
            // Buffered input is still checked against the same active/buffer/chain windows.
            if (npc.getHealth() / npc.getMaxHealth() > rules.retreatFraction() && npc.hasLineOfSight(target))
                CombatSessionManager.handleAttackIntent(npc);
            return;
        }
        Vec3 toward = target.position().subtract(npc.position());
        float yaw = (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
        npc.setYRot(yaw); npc.setYHeadRot(yaw); npc.setYBodyRot(yaw);
        npc.getLookControl().setLookAt(target, 30, 30);
        if (npc.getHealth() / npc.getMaxHealth() <= rules.retreatFraction() || npc.getMainHandItem().isEmpty()) {
            if (npc.distanceToSqr(target) >= rules.escapeDistance() * rules.escapeDistance()) {
                NpcCombatRuntime.disengage(npc); return;
            }
            Vec3 away = new Vec3(-toward.x, 0, -toward.z).normalize();
            double distance = CombatStepService.safeDistance(npc, away, rules.escapeDistance(), 1);
            if (distance > 0) {
                Vec3 point = npc.position().add(away.scale(distance));
                npc.getNavigation().moveTo(point.x, point.y, point.z, rules.retreatSpeed());
            }
            return;
        }
        var style = CombatStyles.bundled().styleFor(npc.getMainHandItem());
        if (style.isEmpty()) return;
        double range = style.get().moves().get(0).range();
        if (npc.distanceToSqr(target) <= range * range && npc.hasLineOfSight(target)
                && com.example.myvillage.combat.runtime.CombatHitResolver.canReach(npc, target, style.get().moves().get(0), yaw)) {
            stopWalking();
            CombatSessionManager.handleAttackIntent(npc);
        } else npc.getNavigation().moveTo(target, rules.approachSpeed());
    }
}
