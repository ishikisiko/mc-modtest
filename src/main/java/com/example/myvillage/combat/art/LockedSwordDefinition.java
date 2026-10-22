package com.example.myvillage.combat.art;

import com.example.myvillage.combat.definition.*;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored lock/timing/defence data. Technique JSON alone owns the spiritual-power cost. */
public record LockedSwordDefinition(String mode, double range, double acquireArc, int firstStrike,
        int interval, int strikes, int recovery, double radius, double finalMultiplier,
        double armorBreakFraction, int armorBreakTicks, int telegraphInterval, int slowAmplifier, int slowTicks) {
    public LockedSwordDefinition {
        if (!Set.of("pierce","array","judgment").contains(mode) || !bounded(range,1,48)
                || !bounded(acquireArc,1,180) || firstStrike<1 || firstStrike>200 || interval<1 || interval>100
                || strikes<1 || strikes>7 || recovery<1 || recovery>100 || !bounded(radius,.1,8)
                || !bounded(finalMultiplier,1,4) || !bounded(armorBreakFraction,0,.5)
                || slowAmplifier<0 || slowAmplifier>4 || slowTicks<0 || slowTicks>40
                || armorBreakTicks<0 || armorBreakTicks>400 || telegraphInterval<1 || telegraphInterval>20
                || (!mode.equals("array") && strikes!=1) || (armorBreakFraction>0 && armorBreakTicks==0))
            throw new IllegalArgumentException("Invalid locked sword definition");
    }
    public int lastStrike() { return firstStrike+(strikes-1)*interval; }
    public int totalTicks() { return lastStrike()+1+recovery; }
    public boolean strikeDue(int age,int next) { return next>=0 && next<strikes && age>=firstStrike+next*interval; }
    public double pulseMultiplier(int pulse) { return pulse==strikes-1?finalMultiplier:1; }
    public boolean inCone(double dot) { return Double.isFinite(dot) && dot>=Math.cos(Math.toRadians(acquireArc*.5)); }
    public AttackMoveDefinition move(AttackMoveDefinition base) {
        var camera=base.camera();
        var feedback=base.feedback();
        return new AttackMoveDefinition(base.id(),base.displayKey(),base.kind(),totalTicks(),firstStrike,lastStrike(),
                base.damageMultiplier(),base.maximumTargets(),range,lastStrike()+1,totalTicks(),ReactionDefinition.NONE,
                new AnimationDefinition(base.id(),totalTicks()),base.hitbox(),Optional.empty(),
                new MoveFeedback(feedback.swingSound(),feedback.swingPitch(),feedback.hitSound(),feedback.heavyLayerSound(),feedback.heavyHit(),
                        0,feedback.cameraTrauma(),feedback.cutRollDegrees()),
                new CameraCues(camera.hitPitchKick(),camera.hitRollKick(),camera.hitFovPunch(),camera.swingLeanDegrees(),0),Optional.empty());
    }
    public static LockedSwordDefinition parse(JsonObject r) {
        return new LockedSwordDefinition(r.get("mode").getAsString(),n(r,"range"),n(r,"acquire_arc_degrees"),
                i(r,"first_strike_ticks"),i(r,"strike_interval_ticks"),i(r,"strikes"),i(r,"recovery_ticks"),n(r,"radius"),
                n(r,"final_multiplier"),n(r,"armor_break_fraction"),i(r,"armor_break_ticks"),i(r,"telegraph_interval_ticks"),i(r,"slow_amplifier"),i(r,"slow_ticks"));
    }
    public record Defence(int tailTicks,double tailDamageFraction) {
        public Defence {
            if(tailTicks<0 || tailTicks>40 || !bounded(tailDamageFraction,0,1))throw new IllegalArgumentException("Invalid dodge tail");
        }
        public double damageFraction(long now,long invulnerableUntil) {
            if(now<invulnerableUntil)return 0;
            return now-invulnerableUntil<tailTicks?tailDamageFraction:1;
        }
    }
    private static class Holder {
        static final JsonObject DOCUMENT=read("/data/myvillage/combat/locked_sword_arts.json");
        static final Defence DEFENCE=defence(DOCUMENT.getAsJsonObject("defense"));
        static Defence defence(JsonObject d) { return new Defence(i(d,"tail_ticks"),n(d,"tail_damage_fraction")); }
    }
    public static JsonObject document() {
        if(i(Holder.DOCUMENT,"schema")!=1)throw new IllegalArgumentException("Locked sword schema");
        return Holder.DOCUMENT.deepCopy();
    }
    public static Defence defence() { return Holder.DEFENCE; }
    static int techniqueCost(ResourceLocation id) {
        JsonObject active=read("/data/"+id.getNamespace()+"/myvillage/technique/"+id.getPath()+".json")
                .getAsJsonObject("effects").getAsJsonObject("active");
        int cost=i(active,"qi_cost");
        if(cost<1 || cost>1000 || !active.get("skill").getAsString().equals(id.toString()))
            throw new IllegalArgumentException("Locked sword technique cost/skill mismatch");
        return cost;
    }
    private static JsonObject read(String path) {
        try(var stream=LockedSwordDefinition.class.getResourceAsStream(path)) {
            if(stream==null)throw new IllegalArgumentException("Missing "+path);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(Exception e) { throw new IllegalStateException("Invalid "+path,e); }
    }
    private static double n(JsonObject o,String key) {
        if(!o.has(key)||!o.get(key).isJsonPrimitive()||!o.getAsJsonPrimitive(key).isNumber())throw new IllegalArgumentException(key);
        double v=o.get(key).getAsDouble();if(!Double.isFinite(v))throw new IllegalArgumentException(key);return v;
    }
    private static int i(JsonObject o,String key) { double v=n(o,key);if(v!=Math.rint(v)||v<0||v>Integer.MAX_VALUE)throw new IllegalArgumentException(key);return (int)v; }
    private static boolean bounded(double v,double min,double max) { return Double.isFinite(v)&&v>=min&&v<=max; }
}
