package com.example.myvillage.cultivation.fate;

/** 凝聚按真实服务器 tick 推进；受击不重置计时，停机期间不补算。 */
public record PhoenixEmberClock(double maximumHealth,double maximumDurability,double durability,
        int duration,int remaining,int protectionRemaining,double movementUsed) {
    public PhoenixEmberClock {
        if(!Double.isFinite(maximumHealth)||!Double.isFinite(maximumDurability)||!Double.isFinite(durability)
                ||!Double.isFinite(movementUsed)||maximumHealth<=0||maximumDurability<=0||maximumDurability>maximumHealth
                ||durability<0||durability>maximumDurability+1e-4||duration<1||remaining<0||remaining>duration
                ||protectionRemaining<0||protectionRemaining>duration||movementUsed<0)
            throw new IllegalArgumentException("非法凤凰凝聚状态");
    }
    public static PhoenixEmberClock begin(double maximum,PhoenixState reserve,SpiritBodyRules.Phoenix rules) {
        if(!reserve.ready(rules))throw new IllegalArgumentException("火种不足，无法凝聚");
        double health=reserve.emberHealth(maximum,rules);
        return new PhoenixEmberClock(maximum,health,health,rules.emberTicks(),rules.emberTicks(),rules.protectionTicks(),0);
    }
    public PhoenixEmberClock health(double amount) {
        if(!Double.isFinite(amount))throw new IllegalArgumentException("非法余烬耐久");
        return new PhoenixEmberClock(maximumHealth,maximumDurability,Math.max(0,Math.min(maximumDurability,amount)),duration,remaining,protectionRemaining,movementUsed);
    }
    public PhoenixEmberClock move(double distance) {
        if(!Double.isFinite(distance)||distance<0)throw new IllegalArgumentException("非法余烬移动");
        return new PhoenixEmberClock(maximumHealth,maximumDurability,durability,duration,remaining,protectionRemaining,movementUsed+distance);
    }
    public PhoenixEmberClock tick(boolean immersed,SpiritBodyRules.Phoenix rules) {
        double loss=immersed?maximumDurability*rules.waterLossFractionPerSecond()/20:0;
        return new PhoenixEmberClock(maximumHealth,maximumDurability,Math.max(0,durability-loss),duration,
                Math.max(0,remaining-1),Math.max(0,protectionRemaining-1),movementUsed);
    }
    public boolean failed(){return durability<=0;}
    public boolean complete(){return remaining==0&&!failed();}
}
