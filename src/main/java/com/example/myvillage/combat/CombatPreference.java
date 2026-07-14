package com.example.myvillage.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

public record CombatPreference(CombatMode combatMode) {
    public static final Codec<CombatPreference> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    CombatMode.CODEC.fieldOf("combat_mode").forGetter(CombatPreference::combatMode))
                    .apply(instance, CombatPreference::new));

    public CombatPreference {
        Objects.requireNonNull(combatMode, "combatMode");
    }

    public static CombatPreference defaultPreference() {
        return new CombatPreference(CombatMode.VANILLA);
    }

    public CombatPreference toggled() {
        return new CombatPreference(combatMode.toggled());
    }
}
