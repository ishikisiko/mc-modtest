package com.example.myvillage.combat.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

final class CombatReactionEligibilityTest {
    @Test
    void onlyNonBossMobsFreeze() {
        assertTrue(CombatReactionService.freezes(Zombie.class, 2));
        assertFalse(CombatReactionService.freezes(Zombie.class, 0), "no hit-stop, no freeze");
        assertFalse(CombatReactionService.freezes(EnderDragon.class, 3));
        assertFalse(CombatReactionService.freezes(WitherBoss.class, 3));
        assertFalse(CombatReactionService.freezes(Warden.class, 3));
        assertFalse(CombatReactionService.freezes(ArmorStand.class, 3), "not a Mob");
        assertFalse(CombatReactionService.freezes(Player.class, 3));
        assertFalse(CombatReactionService.freezes(ServerPlayer.class, 3));
        assertTrue(CombatReactionService.excludedType(Warden.class));
        assertFalse(CombatReactionService.excludedType(Zombie.class));
    }

    @Test
    void playersGetTheStunSlowInstead() {
        assertTrue(CombatReactionService.stunSlows(Player.class));
        assertTrue(CombatReactionService.stunSlows(ServerPlayer.class));
        assertFalse(CombatReactionService.stunSlows(Zombie.class));
        assertEquals(-0.6, CombatReactionService.PLAYER_STUN_SLOW);
        assertEquals("myvillage:combat_stun", CombatReactionService.STUN_MODIFIER_ID.toString());
    }

    @Test
    void resistingTargetKeepsOnlyTheFreeze() {
        CombatReactionService.HitPlan normal = CombatReactionService.plan(Zombie.class, 2, 9, false, false);
        assertEquals(new CombatReactionService.HitPlan(true, true, true), normal);
        CombatReactionService.HitPlan resisting = CombatReactionService.plan(Zombie.class, 2, 9, true, false);
        assertTrue(resisting.freeze(), "the hit-stop freeze stays");
        assertFalse(resisting.impulse(), "the knockback impulse is dropped");
        assertFalse(resisting.stun(), "no hitstun");
    }

    @Test
    void hitPlanKeepsTheExistingRules() {
        assertEquals(new CombatReactionService.HitPlan(false, true, true),
                CombatReactionService.plan(Player.class, 2, 9, false, false), "players are stunned, not frozen");
        assertEquals(new CombatReactionService.HitPlan(false, true, false),
                CombatReactionService.plan(Warden.class, 3, 9, false, false), "bosses only take the impulse");
        assertEquals(new CombatReactionService.HitPlan(false, true, false),
                CombatReactionService.plan(Zombie.class, 2, 9, false, true), "a dying target is launched only");
        assertEquals(new CombatReactionService.HitPlan(true, true, false),
                CombatReactionService.plan(Zombie.class, 2, 0, false, false), "no hitstun data, no stun");
    }

    @Test
    void onlyAStaggerResistantThatResistsCounts() {
        assertTrue(StaggerResistant.resists((StaggerResistant) () -> true));
        assertFalse(StaggerResistant.resists((StaggerResistant) () -> false));
        assertFalse(StaggerResistant.resists(new Object()));
        assertFalse(StaggerResistant.resists(null));
    }
}
