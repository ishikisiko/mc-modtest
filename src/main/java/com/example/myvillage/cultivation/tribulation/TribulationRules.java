package com.example.myvillage.cultivation.tribulation;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.util.List;

/** 数据包在起劫时读取一次，完整冻结进玩家存档；重载不改变正在进行的雷序。 */
public record TribulationRules(int schemaVersion, String sourceRealm, String sourceStage,
        String targetRealm, String targetStage, int preparationTicks, int resumeTicks, int durationTicks,
        double arenaRadius, double verticalRange, int boundaryGraceTicks, int failureStabilityLoss,
        float survivalHealth, int recoveryTicks, double echoRadius, double cloudHeight, int ringPoints,
        int particleIntervalTicks, int statusIntervalTicks, List<Strike> strikes) {
    public static final Gson JSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    public record Strike(int tick, int warningTicks, boolean main, float damageFraction) {
        public Strike {
            if (tick <= 0 || warningTicks <= 0 || warningTicks >= tick || !Float.isFinite(damageFraction)
                    || damageFraction <= 0 || damageFraction > 1) throw new IllegalArgumentException("雷击时序或伤害无效");
        }
    }
    public TribulationRules {
        if (schemaVersion != 1 || !id(sourceRealm) || !id(sourceStage) || !id(targetRealm) || !id(targetStage)
                || preparationTicks <= 0 || preparationTicks > 72000 || resumeTicks <= 0 || resumeTicks > 72000 || durationTicks <= 0 || durationTicks > 72000
                || !Double.isFinite(arenaRadius) || arenaRadius < 6 || arenaRadius > 32
                || !Double.isFinite(verticalRange) || verticalRange < 1 || verticalRange > 32
                || boundaryGraceTicks <= 0 || boundaryGraceTicks > 72000 || failureStabilityLoss < 0 || !Float.isFinite(survivalHealth)
                || survivalHealth <= 0 || recoveryTicks < 1 || recoveryTicks > 72000 || !Double.isFinite(echoRadius)
                || echoRadius <= 0 || echoRadius >= arenaRadius || !Double.isFinite(cloudHeight)
                || cloudHeight < 4 || cloudHeight > 32 || ringPoints < 12 || ringPoints > 96
                || particleIntervalTicks < 1 || particleIntervalTicks > 200 || statusIntervalTicks < 1 || statusIntervalTicks > 200) throw new IllegalArgumentException("雷劫规则无效");
        strikes = List.copyOf(strikes);
        if (strikes.size() > 32 || strikes.stream().filter(Strike::main).count() != 6)
            throw new IllegalArgumentException("金丹雷劫必须含六道主雷");
        int previous = 0;
        for (var strike : strikes) {
            if (strike.tick() <= previous || strike.tick() >= durationTicks) throw new IllegalArgumentException("雷序必须严格递增且先于结算");
            previous = strike.tick();
        }
    }
    private static boolean id(String id) { return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"); }
    public static TribulationRules read(Reader reader) { return JSON.fromJson(reader, TribulationRules.class); }
    public boolean source(String realm, String stage) { return sourceRealm.equals(realm) && sourceStage.equals(stage); }
}
