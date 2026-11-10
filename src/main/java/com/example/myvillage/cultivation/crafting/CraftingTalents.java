package com.example.myvillage.cultivation.crafting;

import com.example.myvillage.cultivation.fate.FateService;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Reads active, confirmed fate definitions. Startup overrides never overwrite an already initialized score. */
public final class CraftingTalents {
    public static final String ALCHEMY="alchemy_initial_aptitude",FORGING="forging_initial_aptitude",
            MEAN="crafting_mean_shift",VARIANCE="crafting_variance_multiplier";
    private CraftingTalents(){}
    public static CraftingOpeningRules.Selection selection(List<String> ids,Collection<Map<String,Double>> effects){
        int alchemy=-1,forging=-1;
        for(var map:effects){
            if(map.containsKey(ALCHEMY))alchemy=Math.max(alchemy,(int)(double)map.get(ALCHEMY));
            if(map.containsKey(FORGING))forging=Math.max(forging,(int)(double)map.get(FORGING));
        }
        return new CraftingOpeningRules.Selection(ids,alchemy,forging);
    }
    private static List<Map<String,Double>> effects(ServerPlayer player){
        var active=FateService.active(player);
        return FateService.state(player).selected().stream().filter(active::containsKey).map(id->active.get(id).effects()).toList();
    }
    public static CraftingOpeningRules.Selection selection(ServerPlayer player){
        var state=FateService.state(player);
        return state.confirmed()?selection(state.selected(),effects(player)):CraftingOpeningRules.Selection.PENDING;
    }
    public static CraftingQualityRules.Modifier modifier(ServerPlayer player){return modifier(effects(player));}
    public static CraftingQualityRules.Modifier modifier(Collection<Map<String,Double>> effects){
        double mean=0,variance=1;
        for(var map:effects){mean+=map.getOrDefault(MEAN,0.0);variance*=map.getOrDefault(VARIANCE,1.0);}
        return new CraftingQualityRules.Modifier(mean,variance);
    }
}
