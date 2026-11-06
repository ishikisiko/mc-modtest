package com.example.myvillage.cultivation.tribulation;

import com.example.myvillage.cultivation.CultivationProfile;
import com.google.gson.annotations.SerializedName;

/** 有界的突破选择；雷击数量、时序和伤害只取服务器规则。 */
public enum TribulationKind {
    @SerializedName("heaven") HEAVEN(0,"heaven","天道筑基",4,"qi_refining","qi_refining_9","foundation_establishment","foundation_early"),
    @SerializedName("golden_low") GOLDEN_LOW(1,"golden_low","下品金丹",1,"foundation_establishment","foundation_late","golden_core","golden_early"),
    @SerializedName("golden_middle") GOLDEN_MIDDLE(2,"golden_middle","中品金丹",2,"foundation_establishment","foundation_late","golden_core","golden_early"),
    @SerializedName("golden_high") GOLDEN_HIGH(3,"golden_high","上品金丹",3,"foundation_establishment","foundation_late","golden_core","golden_early");
    private final int networkId,quality;
    private final String id,displayName,sourceRealm,sourceStage,targetRealm,targetStage;
    TribulationKind(int networkId,String id,String displayName,int quality,String sourceRealm,String sourceStage,String targetRealm,String targetStage){
        this.networkId=networkId;this.id=id;this.displayName=displayName;this.quality=quality;
        this.sourceRealm="myvillage:"+sourceRealm;this.sourceStage="myvillage:"+sourceStage;
        this.targetRealm="myvillage:"+targetRealm;this.targetStage="myvillage:"+targetStage;
    }
    public int networkId(){return networkId;}
    public String id(){return id;}
    public String displayName(){return displayName;}
    public int quality(){return quality;}
    public String sourceRealm(){return sourceRealm;}
    public String sourceStage(){return sourceStage;}
    public String targetRealm(){return targetRealm;}
    public String targetStage(){return targetStage;}
    public boolean source(CultivationProfile p){return sourceRealm.equals(p.realmId().toString())&&sourceStage.equals(p.stageId().toString());}
    public static boolean canChoose(CultivationProfile p){return HEAVEN.source(p)||GOLDEN_LOW.source(p);}
    public static TribulationKind fromNetwork(int id){for(var kind:values())if(kind.networkId==id)return kind;throw new IllegalArgumentException("非法雷劫选择");}
}
