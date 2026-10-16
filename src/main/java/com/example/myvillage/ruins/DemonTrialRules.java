package com.example.myvillage.ruins;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Item limits and encounter geometry share one authored file. */
public record DemonTrialRules(int radius, int height, int separation, int stackSize, int cooldownTicks) {
    private static final class Bundled { static final DemonTrialRules INSTANCE=load(); }
    public static DemonTrialRules get(){return Bundled.INSTANCE;}
    private static DemonTrialRules load(){
        try(var in=Objects.requireNonNull(DemonTrialRules.class.getResourceAsStream("/data/myvillage/ruins/demon_trial.json"))){
            return parse(JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject());
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    private static int number(JsonObject o,String key){return o.get(key).getAsBigDecimal().intValueExact();}
    public static DemonTrialRules parse(JsonObject root){
        var j=root.getAsJsonObject("summoning");
        var r=new DemonTrialRules(number(j,"clear_radius"),number(j,"clear_height"),number(j,"separation"),number(j,"stack_size"),number(j,"cooldown_ticks"));
        if(r.radius<3 || r.radius>12 || r.height<3 || r.height>6 || r.separation<r.radius*2 || r.separation>64
                || r.stackSize<1 || r.stackSize>64 || r.cooldownTicks<1 || r.cooldownTicks>1200)throw new IllegalArgumentException("Summoning limits");
        var battle=BattleRules.parse(root);
        if(!battle.id().equals("demon_trial") || !battle.mode().equals("arena") || battle.rewards().isEmpty())throw new IllegalArgumentException("Trial identity/rewards");
        for(var wave:battle.waves())for(var guard:wave.guards()){
            var p=guard.pos().subtract(battle.cache());
            if(p.y()!=0 || Math.abs(p.x())>=r.radius || Math.abs(p.z())>=r.radius || p.x()==0 && p.z()==0)
                throw new IllegalArgumentException("Guard must fit inside the clear floor and avoid the core");
        }
        return r;
    }
}
