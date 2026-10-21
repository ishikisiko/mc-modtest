package com.example.myvillage.alchemy;

import com.example.myvillage.cultivation.CultivationProfile;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Single bundled source for breakthrough probability and permanent foundation bonuses. */
public final class FoundationRules {
    private final String sourceStage;
    private final Set<String> established;
    private final int[] success, bonus;
    private final int failureDivisor;
    private FoundationRules(JsonObject o) {
        if(!o.keySet().equals(Set.of("version","source_stage","established_realms","success_percent","bonus_percent","failure_stability_divisor")) || o.get("version").getAsInt()!=1) throw new IllegalArgumentException("Invalid foundation rules");
        sourceStage=o.get("source_stage").getAsString();
        established=new HashSet<>();for(var e:o.getAsJsonArray("established_realms"))established.add(e.getAsString());
        if(established.isEmpty() || !sourceStage.matches("[a-z0-9_]+:[a-z0-9_]+"))throw new IllegalArgumentException("Invalid foundation realms");
        success=percentages(o.getAsJsonArray("success_percent"));bonus=percentages(o.getAsJsonArray("bonus_percent"));
        failureDivisor=o.get("failure_stability_divisor").getAsBigDecimal().intValueExact();
        if(failureDivisor<2 || failureDivisor>100)throw new IllegalArgumentException("Invalid failure divisor");
    }
    private static int[] percentages(JsonArray a){
        if(a.size()!=3)throw new IllegalArgumentException("Three foundation qualities required");
        int[] v=new int[3];for(int i=0;i<3;i++){v[i]=a.get(i).getAsBigDecimal().intValueExact();if(v[i]<=0||v[i]>100||(i>0&&v[i]<=v[i-1]))throw new IllegalArgumentException("Increasing percentages required");}return v;
    }
    public static FoundationRules parse(Reader r){return new FoundationRules(JsonParser.parseReader(r).getAsJsonObject());}
    private static final class Holder {
        static final FoundationRules VALUE=load();
        private static FoundationRules load(){try(var in=FoundationRules.class.getResourceAsStream("/data/myvillage/alchemy/foundation.json")){if(in==null)throw new IllegalStateException("Missing foundation rules");return parse(new InputStreamReader(in,StandardCharsets.UTF_8));}catch(IOException e){throw new IllegalStateException(e);}}
    }
    public static FoundationRules get(){return Holder.VALUE;}
    public boolean source(CultivationProfile p){return p.stageId().toString().equals(sourceStage);}
    public boolean established(CultivationProfile p){return established.contains(p.realmId().toString());}
    public int successPercent(int q){require(q);return success[q-1];}
    public int bonusPercent(int q){if(q==0)return 0;require(q);return bonus[q-1];}
    public double bonus(CultivationProfile p){return established(p)?bonusPercent(p.foundationQuality())/100.0:0;}
    public boolean succeeds(int q,int draw){if(draw<0||draw>=100)throw new IllegalArgumentException("Invalid draw");return draw<successPercent(q);}
    public int failedStability(int stability){if(stability<0)throw new IllegalArgumentException("Invalid stability");return stability/failureDivisor;}
    public boolean canRepair(CultivationProfile p,int q){return established(p)&&q>=2&&q<=3&&q>p.foundationQuality();}
    private static void require(int q){if(q<1||q>3)throw new IllegalArgumentException("Foundation quality must be 1..3");}
}
