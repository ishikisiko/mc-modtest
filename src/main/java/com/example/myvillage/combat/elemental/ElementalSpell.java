package com.example.myvillage.combat.elemental;

import com.example.myvillage.combat.definition.*;
import com.example.myvillage.stats.DamageType;
import com.google.gson.JsonObject;
import java.util.*;
import static com.example.myvillage.combat.elemental.ElementalRules.*;

/** Weapon-independent spell timeline and explicit typed effects. No ID-prefix dispatch. */
public record ElementalSpell(DamageType element,String role,String shape,int windup,int interval,int pulses,int recovery,
        double range,double arc,double radius,int targets,double damage,int slowTicks,int slowAmplifier,
        double armorBreak,int armorTicks,double push,boolean dot,boolean harvest,double wardFraction,
        int wardTicks,int wardPerCharge,double healPerCharge,double speedBonus) {
    public ElementalSpell {
        if(element==DamageType.PHYSICAL || !Set.of("basic","ward","burst","field").contains(role)
                || !Set.of("bolt","ray","self","cone","burst","field").contains(shape)
                || role.equals("ward")!=shape.equals("self") || (!role.equals("field")&&pulses!=1))
            throw new IllegalArgumentException("Invalid elemental role/shape");
    }
    public boolean casterCentered(){return shape.equals("self") || shape.equals("cone");}
    public double footprintRadius(){return shape.equals("cone")?radius*2:radius;}
    public boolean acceptsConeDot(double dot){return Double.isFinite(dot) && dot>=.5;}
    public int lastStrike(){return windup+(pulses-1)*interval;}
    public int totalTicks(){return lastStrike()+1+recovery;}
    public boolean due(int age,int pulse){return pulse<pulses && age>=windup+pulse*interval;}
    public boolean consumes(){return role.equals("burst") || role.equals("ward");}
    public static ElementalSpell parse(JsonObject o) {
        return new ElementalSpell(DamageType.fromId(o.get("element").getAsString()),o.get("role").getAsString(),o.get("shape").getAsString(),
                integer(o,"windup_ticks",1,100),integer(o,"pulse_interval_ticks",1,100),integer(o,"pulses",1,7),integer(o,"recovery_ticks",1,100),
                number(o,"range",1,64),number(o,"acquire_arc_degrees",1,90),number(o,"radius",.1,12),integer(o,"maximum_targets",1,12),
                number(o,"damage_multiplier",.01,4),integer(o,"slow_ticks",0,100),integer(o,"slow_amplifier",0,2),
                number(o,"armor_break_fraction",0,.3),integer(o,"armor_break_ticks",1,400),number(o,"push",0,.8),
                bool(o,"dot"),bool(o,"harvest"),number(o,"ward_fraction",0,.7),integer(o,"ward_ticks",0,200),
                integer(o,"ward_ticks_per_charge",0,100),number(o,"heal_per_charge",0,1),number(o,"speed_bonus",0,.5));
    }
    private static boolean bool(JsonObject o,String key) {
        if(!o.has(key)||!o.get(key).isJsonPrimitive()||!o.getAsJsonPrimitive(key).isBoolean())throw new IllegalArgumentException(key);
        return o.get(key).getAsBoolean();
    }
    public AttackMoveDefinition move(AttackMoveDefinition base) {
        return new AttackMoveDefinition(base.id(),base.displayKey(),base.kind(),totalTicks(),windup,lastStrike(),
                damage,targets,range,lastStrike()+1,totalTicks(),ReactionDefinition.NONE,
                new AnimationDefinition(base.id(),totalTicks()),base.hitbox(),Optional.empty(),
                new MoveFeedback(base.feedback().swingSound(),base.feedback().swingPitch(),base.feedback().hitSound(),Optional.empty(),false,
                        0,0,0),new CameraCues(0,0,0,0,0),Optional.empty());
    }
}
