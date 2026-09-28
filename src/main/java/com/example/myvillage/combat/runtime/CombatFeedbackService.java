package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.CombatSounds;
import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.BasicSwordStyle;
import com.example.myvillage.combat.definition.MoveFeedback;
import com.example.myvillage.combat.network.CombatHitConfirmPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Server-side presentation for accepted actions: swing and hit sounds, hit particles,
 * and the attacker's hit confirmation. Called only after the server has decided the
 * outcome, so it never feeds back into timing, hits, or damage.
 */
public final class CombatFeedbackService {
    private CombatFeedbackService() {
    }

    /**
     * Plays the swing sound for everyone near the attacker except the attacker, whose
     * client plays it on its own predicted timeline.
     */
    public static void swing(ServerPlayer attacker, AttackMoveDefinition move) {
        MoveFeedback feedback = feedback(move);
        SoundEvent sound = feedback.swingSound() == MoveFeedback.SwingSound.THRUST
                ? CombatSounds.SWORD_THRUST.get()
                : CombatSounds.SWORD_CUT.get();
        attacker.serverLevel().playSound(
                attacker,
                attacker.getX(),
                attacker.getY(),
                attacker.getZ(),
                sound,
                SoundSource.PLAYERS,
                0.9F,
                feedback.swingPitch());
    }

    public static void hit(
            ServerPlayer attacker,
            AttackMoveDefinition move,
            long revision,
            List<CombatHitResolver.TargetContact> contacts) {
        if (contacts.isEmpty()) {
            return;
        }
        MoveFeedback feedback = feedback(move);
        ServerLevel level = attacker.serverLevel();
        SoundEvent sound = feedback.heavyHit()
                ? CombatSounds.SWORD_HIT_HEAVY.get()
                : CombatSounds.SWORD_HIT.get();
        for (CombatHitResolver.TargetContact contact : contacts) {
            Vec3 center = contact.target().getBoundingBox().getCenter();
            Vec3 point = contact.contactPoint().lerp(center, 0.5);
            level.playSound(
                    null, point.x, point.y, point.z, sound, SoundSource.PLAYERS, 1.0F,
                    0.95F + level.random.nextFloat() * 0.1F);
            if (feedback.swingSound() == MoveFeedback.SwingSound.CUT) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            level.sendParticles(
                    ParticleTypes.CRIT, point.x, point.y, point.z,
                    feedback.heavyHit() ? 14 : 8, 0.15, 0.2, 0.15, 0.35);
        }
        PacketDistributor.sendToPlayer(
                attacker, new CombatHitConfirmPayload(attacker.getId(), revision, contacts.size()));
    }

    private static MoveFeedback feedback(AttackMoveDefinition move) {
        int index = BasicSwordStyle.DEFINITION.indexOf(move.id());
        return BasicSwordStyle.feedback(Math.max(0, index));
    }
}
