package com.example.myvillage.item;

import com.example.myvillage.aptitude.AptitudeValues;
import com.example.myvillage.stats.StatsRules;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;

/** 有界的召唤意图，属性 ID 和数值还须由服务器根据实际实体校验。 */
public record TestUnitConfig(String entity, String name, String realm, int stage,
        int foundation, int golden, List<String> fates, Map<String, Integer> aptitudes,
        Map<String, Double> attributes, long power, long powerCap, boolean paused, TestUnitRelations.Unit relations) {
    public static final int MAX_JSON = 16384;
    public TestUnitConfig {
        if (entity == null || entity.length() > 128 || name == null || name.length() > 32
                || name.codePoints().anyMatch(Character::isISOControl) || realm == null || realm.length() > 128
                || foundation < 1 || foundation > 4 || golden < 1 || golden > 3 || power < -1 || powerCap < -1
                || fates == null || !(fates.isEmpty() || fates.size() == 3)
                || fates.stream().distinct().count() != fates.size()
                || fates.stream().anyMatch(id -> id == null || id.length() > 128)
                || attributes == null || attributes.size() > 64 || relations == null)
            throw new IllegalArgumentException("召唤配置超出范围");
        StatsRules.bundled().realmKey(realm, stage);
        new AptitudeValues(aptitudes);
        fates = List.copyOf(fates); aptitudes = Map.copyOf(aptitudes); attributes = Map.copyOf(attributes);
        attributes.forEach((id, value) -> {
            if (id.length() > 128 || !Double.isFinite(value)) throw new IllegalArgumentException("属性值必须是有限数值");
        });
    }

    public static TestUnitConfig parse(String json) {
        if (json == null || json.length() > MAX_JSON) throw new IllegalArgumentException("召唤配置过长");
        TestUnitConfig value = new Gson().fromJson(json, TestUnitConfig.class);
        if (value == null) throw new IllegalArgumentException("召唤配置不能为空");
        return value;
    }
    public String json() { return new Gson().toJson(this); }
    public static JsonObject object(String json) {
        if (json == null || json.length() > MAX_JSON) throw new IllegalArgumentException("测试请求过长");
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
