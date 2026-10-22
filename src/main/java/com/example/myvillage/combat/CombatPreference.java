package com.example.myvillage.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

public record CombatPreference(CombatMode combatMode, SkillBindings skillBindings) {
    /** Registry path of the player attachment that persists this preference (myvillage namespace). */
    public static final String ATTACHMENT_PATH = "combat_preference";
    public static final Codec<CombatPreference> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    CombatMode.CODEC.fieldOf("combat_mode").forGetter(CombatPreference::combatMode),
                    SkillBindings.CODEC.optionalFieldOf("skill_bindings",SkillBindings.defaults()).forGetter(CombatPreference::skillBindings))
                    .apply(instance, CombatPreference::new));

    public CombatPreference {
        Objects.requireNonNull(combatMode, "combatMode");
        Objects.requireNonNull(skillBindings, "skillBindings");
    }

    public CombatPreference(CombatMode mode){this(mode,SkillBindings.defaults());}

    public static CombatPreference defaultPreference() {
        return new CombatPreference(CombatMode.VANILLA);
    }

    public CombatPreference toggled() {
        return new CombatPreference(combatMode.toggled(),skillBindings);
    }
}
