package com.example.myvillage.combat.runtime;

import com.example.myvillage.combat.CombatParticles;
import com.example.myvillage.combat.CombatSounds;
import com.example.myvillage.combat.definition.AttackMoveDefinition;
import com.example.myvillage.combat.definition.MoveFeedback;
import com.example.myvillage.combat.network.CombatHitConfirmPayload;
import com.example.myvillage.combat.network.CombatImpactPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-side presentation for accepted actions: swing and hit sounds, the blade-cut and spark
 * particles at the true contact point, the attacker's hit confirmation, and the impact broadcast
 * (struck entities and contact points) that every nearby client uses for target hit-stop.
 * Called only after the server has decided the outcome, so it never feeds back into timing,
 * hits, or damage.
 */
public final class CombatFeedbackService {
    private CombatFeedbackService() {
    }

    /**
     * Plays the swing sound for everyone near the attacker except the attacker, whose
     * client plays it on its own predicted timeline.
     */
    public static void swing(ServerPlayer attacker, AttackMoveDefinition move) {
        MoveFeedback feedback = move.feedback();
        SoundEvent sound = CombatSounds.resolve(feedback.swingSound());
        attacker.serverLevel().playSound(
                attacker,
                attacker.getX(),
                attacker.getY(),
                attacker.getZ(),
                sound,
                SoundSource.PLAYERS,
                0.9F,
                CombatSounds.jitteredSwingPitch(feedback.swingPitch(), attacker.getRandom()));
    }

    public static void hit(
            ServerPlayer attacker,
            AttackMoveDefinition move,
            long revision,
            List<CombatHitResolver.TargetContact> contacts) {
        if (contacts.isEmpty()) {
            return;
        }
        MoveFeedback feedback = move.feedback();
        ServerLevel level = attacker.serverLevel();
        SoundEvent sound = CombatSounds.resolve(feedback.hitSound());
        Optional<SoundEvent> heavyLayer = feedback.heavyLayerSound().map(CombatSounds::resolve);
        double rollRadians = Math.toRadians(feedback.cutRollDegrees());
        double heavy = feedback.heavyHit() ? 1.0 : 0.0;
        List<Integer> struckEntityIds = new ArrayList<>(contacts.size());
        List<Vec3> contactPoints = new ArrayList<>(contacts.size());
        for (CombatHitResolver.TargetContact contact : contacts) {
            Vec3 point = contact.contactPoint();
            float pitch = 0.95F + level.random.nextFloat() * 0.1F;
            level.playSound(null, point.x, point.y, point.z, sound, SoundSource.PLAYERS, 1.0F, pitch);
            if (heavyLayer.isPresent()) {
                level.playSound(
                        null, point.x, point.y, point.z, heavyLayer.get(),
                        SoundSource.PLAYERS, 1.0F, 0.9F + level.random.nextFloat() * 0.1F);
            }
            // count 0: the three "offsets" arrive as the particle's velocity, which the client
            // provider reads as (roll in radians, heavy flag, unused).
            level.sendParticles(
                    CombatParticles.BLADE_CUT.get(), point.x, point.y, point.z,
                    0, rollRadians, heavy, 0.0, 1.0);
            level.sendParticles(
                    ParticleTypes.CRIT, point.x, point.y, point.z,
                    feedback.heavyHit() ? 6 : 3, 0.05, 0.05, 0.05, 0.12);
            struckEntityIds.add(contact.target().getId());
            contactPoints.add(point);
        }
        PacketDistributor.sendToPlayer(
                attacker, new CombatHitConfirmPayload(attacker.getId(), revision, contacts.size()));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                attacker,
                new CombatImpactPayload(
                        attacker.getId(),
                        revision,
                        move.id(),
                        struckEntityIds,
                        contactPoints));
    }

    /**
     * A started dodge: a low thrust whoosh for everyone nearby and a puff of cloud at the feet,
     * spread along the travel direction {@code forward} (a horizontal unit vector).
     */
    public static void dodge(ServerPlayer player, Vec3 forward) {
        ServerLevel level = player.serverLevel();
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                CombatSounds.SWORD_THRUST.get(),
                SoundSource.PLAYERS,
                0.6F,
                0.75F);
        level.sendParticles(
                ParticleTypes.CLOUD,
                player.getX(),
                player.getY() + 0.1,
                player.getZ(),
                8,
                0.1 + Math.abs(forward.x) * 0.6,
                0.05,
                0.1 + Math.abs(forward.z) * 0.6,
                0.02);
    }
}
