package com.example.myvillage.item;

import java.util.Map;
import java.util.UUID;

/** 测试关系的纯规则；指定单位的关系优先于阵营和种类。 */
public final class TestUnitRelations {
    public static final int TEAMS = 4;
    private TestUnitRelations() { }

    public record Unit(UUID owner, int team, int enemies, boolean player, boolean monsters,
                       Map<UUID, Boolean> pairs) {
        public Unit {
            if (owner == null || team < 0 || team >= TEAMS || enemies < 0 || enemies >= 1 << TEAMS
                    || pairs.size() > 64) throw new IllegalArgumentException("测试单位关系超出范围");
            pairs = Map.copyOf(pairs);
        }
    }

    /** null 表示两个身体都不属于测试单位，交回正式关系规则。 */
    public static Boolean hostile(UUID aId, Unit a, boolean aMonster, boolean aPlayer,
                                  UUID bId, Unit b, boolean bMonster, boolean bPlayer) {
        if (aId.equals(bId)) return false;
        if (a == null && b == null) return null;
        if (a == null) return hostile(bId, b, bMonster, bPlayer, aId, null, aMonster, aPlayer);
        if (b != null) {
            if (!a.owner.equals(b.owner)) return false;
            if (a.pairs.containsKey(bId)) return a.pairs.get(bId);
            if (b.pairs.containsKey(aId)) return b.pairs.get(aId);
            return (a.enemies & 1 << b.team) != 0 || (b.enemies & 1 << a.team) != 0
                    || a.monsters && bMonster || b.monsters && aMonster;
        }
        if (bPlayer) return a.owner.equals(bId) && a.player;
        return bMonster && a.monsters;
    }
}
