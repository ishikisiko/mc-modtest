package com.example.myvillage.entity.beast;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * One beast's server data (schema 1): its registered attribute values, chase tuning, the stagger
 * clip name and its attack moves. Move clips are keyed by {@link BeastMoveDefinition#animation()};
 * a move's index in {@link #moves()} is what the entity syncs to clients (index + 1, 0 = none).
 */
public record BeastDefinition(
        ResourceLocation entity,
        Attributes attributes,
        Chase chase,
        String staggerAnimation,
        List<BeastMoveDefinition> moves) {

    /** Clip names the client drives from locomotion and idle; a move or stagger clip may not reuse them. */
    public static final Set<String> RESERVED_CLIPS = Set.of("idle", "walk", "run");

    public BeastDefinition {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(chase, "chase");
        Objects.requireNonNull(staggerAnimation, "staggerAnimation");
        moves = List.copyOf(Objects.requireNonNull(moves, "moves"));
        if (moves.isEmpty()) {
            throw new IllegalArgumentException("A beast needs at least one move");
        }
        Set<ResourceLocation> ids = new HashSet<>();
        Set<String> clips = new HashSet<>(RESERVED_CLIPS);
        if (!clips.add(staggerAnimation)) {
            throw new IllegalArgumentException("stagger clip " + staggerAnimation + " reuses a reserved clip name");
        }
        for (BeastMoveDefinition move : moves) {
            if (!ids.add(move.id())) {
                throw new IllegalArgumentException("Duplicate move id " + move.id());
            }
            if (!clips.add(move.animation())) {
                throw new IllegalArgumentException("Move " + move.id() + " reuses clip name " + move.animation());
            }
        }
    }

    public BeastMoveDefinition move(int index) {
        return moves.get(index);
    }

    public OptionalInt moveIndex(ResourceLocation moveId) {
        for (int index = 0; index < moves.size(); index++) {
            if (moves.get(index).id().equals(moveId)) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    public Optional<BeastMoveDefinition> move(ResourceLocation moveId) {
        OptionalInt index = moveIndex(moveId);
        return index.isPresent() ? Optional.of(moves.get(index.getAsInt())) : Optional.empty();
    }

    /** Registered attribute base values. */
    public record Attributes(
            double maxHealth,
            double attackDamage,
            double movementSpeed,
            double followRange,
            double armor,
            double knockbackResistance,
            double stepHeight) {
        public Attributes {
            if (!(maxHealth > 0.0) || !(movementSpeed > 0.0) || !(followRange > 0.0)) {
                throw new IllegalArgumentException("max_health, movement_speed and follow_range must be positive");
            }
            if (attackDamage < 0.0 || armor < 0.0 || stepHeight < 0.0) {
                throw new IllegalArgumentException("attack_damage, armor and step_height must not be negative");
            }
            if (knockbackResistance < 0.0 || knockbackResistance > 1.0) {
                throw new IllegalArgumentException("knockback_resistance must be in 0..1");
            }
        }
    }

    /**
     * Chase tuning: navigation speed multiplier while chasing, the minimum gap between two moves,
     * and the cooldown a stagger-cancelled move gets instead of its own.
     */
    public record Chase(double speedModifier, int moveGapTicks, int cancelledCooldownTicks) {
        public Chase {
            if (!(speedModifier > 0.0) || moveGapTicks < 0 || cancelledCooldownTicks < 0) {
                throw new IllegalArgumentException(
                        "speed_modifier must be positive, move_gap_ticks and cancelled_cooldown_ticks not negative");
            }
        }
    }
}
