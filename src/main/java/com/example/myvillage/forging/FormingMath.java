package com.example.myvillage.forging;

/** Pure bounded contribution arithmetic; larger hammers do not lose craftsmanship or learning. */
public final class FormingMath {
    private FormingMath(){}
    public record Contribution(int points,int quality){}
    public static int level(int experience,FoundryRules.Skill rules){return Math.clamp(experience/rules.experience_per_level(),0,rules.max_level());}
    public static Contribution contribution(int remaining,FoundryRules.Hammer hammer,int skill,int heat,FoundryRules rules){
        if(remaining<1||heat<rules.heat().minimum())return new Contribution(0,0);
        double temperature=Math.min(1,heat/(double)rules.heat().ideal());
        double efficiency=rules.heat().minimum_efficiency()+(1-rules.heat().minimum_efficiency())*temperature;
        int points=Math.min(remaining,Math.max(1,(int)Math.floor(hammer.power()*(1+skill*rules.skill().power_per_level())*efficiency)));
        int quality=(int)Math.clamp(Math.round((hammer.quality()+skill*rules.skill().quality_per_level())*efficiency),0,10000);
        return new Contribution(points,quality);
    }
    public static int experience(int points,int grade,int level,FoundryRules.Skill rules){
        if(points<=0)return 0;
        double factor=level>grade*rules.grade_level_span()?rules.overleveled_multiplier():1;
        return (int)Math.floor(points*factor);
    }
    public static int experience(int previous,int completed,int grade,int level,FoundryRules.Skill rules){
        if(previous<0||completed<previous)throw new IllegalArgumentException("Invalid completed work");
        return experience(completed,grade,level,rules)-experience(previous,grade,level,rules);
    }
}
