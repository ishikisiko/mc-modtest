package com.example.myvillage.entity.npc;

import com.example.myvillage.sim.PersonView;
import java.util.List;

/**
 * 命簿人物始终从保留的男女组合中稳定选款，境界不切回旧模型。缺失性别沿用男性展示语义，
 * 也选正式男修组合。旧 Look 常量只保留给显式指定与存档兼容。Pure: no Minecraft state, so
 * the world simulation's avatar code and tests can call it directly.
 */
public final class CultivatorLooks {
    /** The male disciple (the cultivator's plain files). Equal to {@link NpcEntity#LOOK_DEFAULT}. */
    public static final String DEFAULT = "default";
    /** 旧版入门女修 ID，仅兼容。 */
    public static final String FEMALE_NOVICE = "f_novice";
    /** 旧版进阶女修 ID，仅兼容，不再自动分配。 */
    public static final String FEMALE_ADEPT = "f_adept";
    static final String FEMALE = "f";

    private CultivatorLooks() {
    }

    /**
     * 同一身份在所有境界保持同一外观；realmOrder 保留调用兼容，不再参与外观选择。
     */
    public static String forPerson(PersonView person, List<String> realmOrder) {
        return FEMALE.equals(person.gender())
                ? CultivatorVariants.forIdentity(person.id(), person.birthDay())
                : CultivatorVariants.forMaleIdentity(person.id(), person.birthDay());
    }
}
