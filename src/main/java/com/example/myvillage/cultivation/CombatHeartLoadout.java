package com.example.myvillage.cultivation;

import com.example.myvillage.cultivation.data.HeartMethod.Slot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** The two combat slots alongside the existing running cultivation technique. */
public record CombatHeartLoadout(Optional<ResourceLocation> attack, Optional<ResourceLocation> defense) {
    public static final CombatHeartLoadout EMPTY = new CombatHeartLoadout(Optional.empty(), Optional.empty());
    public static final Codec<CombatHeartLoadout> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            ResourceLocation.CODEC.optionalFieldOf("attack").forGetter(CombatHeartLoadout::attack),
            ResourceLocation.CODEC.optionalFieldOf("defense").forGetter(CombatHeartLoadout::defense)
    ).apply(builder, CombatHeartLoadout::new));

    public CombatHeartLoadout {
        Objects.requireNonNull(attack, "attack");
        Objects.requireNonNull(defense, "defense");
    }

    public CombatHeartLoadout filter(Predicate<ResourceLocation> learned) {
        return new CombatHeartLoadout(attack.filter(learned), defense.filter(learned));
    }

    public CombatHeartLoadout with(Slot slot, ResourceLocation id) {
        return switch (slot) {
            case ATTACK -> new CombatHeartLoadout(Optional.of(id), defense);
            case DEFENSE -> new CombatHeartLoadout(attack, Optional.of(id));
            case SPIRIT -> throw new IllegalArgumentException("The cultivation slot uses the active core technique");
        };
    }
}
