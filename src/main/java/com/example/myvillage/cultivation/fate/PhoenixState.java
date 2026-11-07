package com.example.myvillage.cultivation.fate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** 只保存火种；凝聚与离线余烬另由服务器 SavedData 拥有。 */
public record PhoenixState(boolean initialized, double reserve) {
    public static final PhoenixState EMPTY=new PhoenixState(false,0);
    public static final Codec<PhoenixState> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.BOOL.fieldOf("initialized").forGetter(PhoenixState::initialized),
            Codec.doubleRange(0,10000).fieldOf("reserve").forGetter(PhoenixState::reserve)).apply(i,PhoenixState::new));
    public PhoenixState { if(!Double.isFinite(reserve)||reserve<0||reserve>10000||!initialized&&reserve!=0) throw new IllegalArgumentException("非法凤凰火种"); }
    public PhoenixState initialize(SpiritBodyRules.Phoenix rules) { return initialized?this:new PhoenixState(true,rules.initialReserve()); }
    public PhoenixState recover(double amount,SpiritBodyRules.Phoenix rules) {
        if(!Double.isFinite(amount)||amount<0)throw new IllegalArgumentException("非法火种恢复");
        return new PhoenixState(true,Math.min(rules.capacity(),reserve+amount));
    }
    public boolean ready(SpiritBodyRules.Phoenix rules) { return initialized&&reserve>=rules.rebirthCost(); }
    public double healingBudget(SpiritBodyRules.Phoenix rules) { return Math.min(rules.healingCostPerSecond(),Math.max(0,reserve-rules.rebirthCost())); }
    public PhoenixState spendHealing(double amount,SpiritBodyRules.Phoenix rules) {
        if(!Double.isFinite(amount)||amount<0||amount>Math.max(0,reserve-rules.rebirthCost())+1e-8)throw new IllegalArgumentException("自愈不得消耗保留火种");
        return new PhoenixState(true,reserve>=rules.rebirthCost()?Math.max(rules.rebirthCost(),reserve-amount):Math.max(0,reserve-amount));
    }
    public PhoenixState emptyReserve() { return new PhoenixState(initialized,0); }
    public double emberHealth(double maximum,SpiritBodyRules.Phoenix rules) {
        return maximum*(rules.emberHealthFraction()+Math.max(0,Math.min(reserve,rules.capacity())-rules.rebirthCost())*rules.extraEmberHealthFractionPerSeed());
    }
}
