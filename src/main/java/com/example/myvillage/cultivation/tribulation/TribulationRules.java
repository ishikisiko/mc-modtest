package com.example.myvillage.cultivation.tribulation;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.util.List;
import java.util.Objects;

/** 起劫读取服务器规则并冻结；受击目标和品质不能由数据包改成其它突破。 */
public record TribulationRules(int schemaVersion, TribulationKind kind, int requiredHits,
        String sourceRealm, String sourceStage, String targetRealm, String targetStage,
        int preparationTicks, int resumeTicks, int durationTicks, double arenaRadius, double verticalRange,
        int boundaryGraceTicks, double failureStabilityFraction, float survivalHealth, int recoveryTicks,
        double warningRadius, double cloudHeight, int ringPoints, int particleIntervalTicks,
        int statusIntervalTicks, List<Strike> strikes) {
    public static final Gson JSON=new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    public record Strike(int tick,int warningTicks,boolean main,float damageFraction) {
        public Strike {
            if(tick<=0||warningTicks<=0||warningTicks>=tick||!Float.isFinite(damageFraction)||damageFraction<=0||damageFraction>1)
                throw new IllegalArgumentException("雷击时序或伤害无效");
        }
    }
    public TribulationRules {
        Objects.requireNonNull(kind);strikes=List.copyOf(strikes);
        if(schemaVersion!=2||requiredHits<1||requiredHits>32||strikes.size()!=requiredHits
                ||!kind.sourceRealm().equals(sourceRealm)||!kind.sourceStage().equals(sourceStage)
                ||!kind.targetRealm().equals(targetRealm)||!kind.targetStage().equals(targetStage)
                ||preparationTicks<1||preparationTicks>72000||resumeTicks<1||resumeTicks>72000||durationTicks<1||durationTicks>72000
                ||!Double.isFinite(arenaRadius)||arenaRadius<6||arenaRadius>32||!Double.isFinite(verticalRange)||verticalRange<1||verticalRange>32
                ||boundaryGraceTicks<1||boundaryGraceTicks>72000||!Double.isFinite(failureStabilityFraction)||failureStabilityFraction<0||failureStabilityFraction>1
                ||!Float.isFinite(survivalHealth)||survivalHealth<=0||recoveryTicks<1||recoveryTicks>72000
                ||!Double.isFinite(warningRadius)||warningRadius<=0||warningRadius>=arenaRadius
                ||!Double.isFinite(cloudHeight)||cloudHeight<4||cloudHeight>32||ringPoints<12||ringPoints>96
                ||particleIntervalTicks<1||particleIntervalTicks>200||statusIntervalTicks<1||statusIntervalTicks>200)
            throw new IllegalArgumentException("雷劫规则无效");
        int previous=0;
        for(var strike:strikes){
            if(!strike.main()||strike.tick()<=previous||strike.tick()>=durationTicks)throw new IllegalArgumentException("试炼只接受顺序主雷");
            previous=strike.tick();
        }
    }
    public static TribulationRules read(Reader reader){return Objects.requireNonNull(JSON.fromJson(reader,TribulationRules.class));}
    public boolean source(String realm,String stage){return sourceRealm.equals(realm)&&sourceStage.equals(stage);}
    public int failureStabilityLoss(int requiredStability){return (int)Math.floor(requiredStability*failureStabilityFraction);}
}
