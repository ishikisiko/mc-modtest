package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.TechniqueProgress;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/** 流派选择与熟练度小数结算；所有效果数值由天命定义提供。 */
public final class SchoolInsight {
    public static final int UNIT = 10_000;
    private SchoolInsight() { }
    public static boolean requiresSchool(List<String> picks, Map<String,FateDefinition> definitions) {
        return picks.stream().map(definitions::get).filter(java.util.Objects::nonNull)
                .anyMatch(d -> d.effect("school_study") > 0 || d.effect("school_mastery") > 0);
    }
    public static SchoolInsightState confirm(List<String> picks, Map<String,FateDefinition> definitions,
            String school, java.util.Set<String> available) {
        if(school==null || school.length()>128)throw new IllegalArgumentException("school");
        if (requiresSchool(picks, definitions)) {
            if (!available.contains(school)) throw new IllegalArgumentException("school");
            return new SchoolInsightState(school);
        }
        if (!school.isEmpty()) throw new IllegalArgumentException("school");
        return SchoolInsightState.EMPTY;
    }
    public static boolean matches(SchoolInsightState state, TechniqueDefinition technique) {
        return technique != null && technique.school().map(id -> id.toString().equals(state.school())).orElse(false);
    }
    public static TechniqueProgress gain(TechniqueProgress before, long amount, int bonusBp, long cap) {
        if (amount < 0 || bonusBp < 0 || bonusBp > UNIT || cap < 0) throw new IllegalArgumentException("无效熟练奖励");
        long room = Math.max(0, cap - before.masteryPoints());
        if (amount == 0 || room == 0) return before;
        var total = BigInteger.valueOf(amount).multiply(BigInteger.valueOf(UNIT + (long)bonusBp))
                .add(BigInteger.valueOf(before.masteryRemainder()));
        var parts = total.divideAndRemainder(BigInteger.valueOf(UNIT));
        long whole = parts[0].min(BigInteger.valueOf(room)).longValueExact();
        int remainder = parts[0].compareTo(BigInteger.valueOf(room)) >= 0 ? 0 : parts[1].intValueExact();
        return new TechniqueProgress(before.masteryPoints()+whole,before.insight(),before.insightSpent(),before.revision(),remainder);
    }
}
