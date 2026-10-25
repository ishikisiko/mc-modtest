package com.example.myvillage.entity.npc;

import com.example.myvillage.sim.PersonView;
import java.util.List;

/**
 * Which of {@link CultivatorEntity#LOOKS} a world-ledger person's avatar wears: a woman at or below
 * Qi Refining wears the novice look, a woman above it the adept look; men use the stable male
 * catalogue. Unknown genders retain the old default. A realm the order does not know counts as the lowest. Pure: no Minecraft state, so
 * the world simulation's avatar code and tests can call it directly.
 */
public final class CultivatorLooks {
    /** The male disciple (the cultivator's plain files). Equal to {@link NpcEntity#LOOK_DEFAULT}. */
    public static final String DEFAULT = "default";
    /** 女·刚入门: a woman at or below {@link #NOVICE_CEILING}. */
    public static final String FEMALE_NOVICE = "f_novice";
    /** 女·小有所成: a woman above {@link #NOVICE_CEILING}. */
    public static final String FEMALE_ADEPT = "f_adept";
    /** The highest realm that still wears the novice look. */
    public static final String NOVICE_CEILING = "qi_refining";
    static final String FEMALE = "f";

    private CultivatorLooks() {
    }

    /**
     * The look for {@code person}; {@code realmOrder} lists realm ids weakest first (the ledger's
     * {@code WorldSim#realmIds()}).
     */
    public static String forPerson(PersonView person, List<String> realmOrder) {
        if ("m".equals(person.gender())) {
            return CultivatorVariants.forMaleIdentity(person.id(), person.birthDay());
        }
        if (!FEMALE.equals(person.gender())) {
            return DEFAULT;
        }
        return realmOrder.indexOf(person.realmId()) <= realmOrder.indexOf(NOVICE_CEILING)
                ? CultivatorVariants.forIdentity(person.id(), person.birthDay()) : FEMALE_ADEPT;
    }
}
