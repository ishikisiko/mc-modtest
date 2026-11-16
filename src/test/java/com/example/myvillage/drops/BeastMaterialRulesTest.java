package com.example.myvillage.drops;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class BeastMaterialRulesTest {
    static String production()throws Exception{return Files.readString(Path.of("src/main/resources/data/yuanxian/drops/monster_drops.json"));}
    static DropRules.Catalog catalog()throws Exception {
        var c=DropRulesTest.catalog();
        return new DropRules.Catalog(id->c.item().test(id)||BeastMaterialRules.bundled().find(id).isPresent(),
                c.entity(),c.region(),c.realmRank(),c.quality(),c.rarity());
    }
    static DropRules.Context context(String entity,DropRules.Scene scene,int realm,boolean player) {
        return new DropRules.Context("yuanxian:"+entity,scene,"donghuang",realm,player,false);
    }
    @Test void catalogueHasEveryEffectiveCombinationAndApprovedPrices() {
        var r=BeastMaterialRules.bundled();assertEquals(56,r.variants().size());
        assertEquals(20,r.variants().stream().filter(v->v.family().equals("beast_hide")).count());
        assertEquals(20,r.variants().stream().filter(v->v.family().equals("beast_bone")).count());
        assertEquals(16,r.variants().stream().filter(v->v.family().equals("beast_core")).count());
        assertTrue(r.find("yuanxian:beast_core_qi_refining_common").isEmpty());
        assertEquals(40,r.find("yuanxian:beast_core_foundation_establishment_common").orElseThrow().price());
        assertEquals(25_600,r.find("yuanxian:beast_core_spirit_transformation_legendary").orElseThrow().price());
        assertEquals("yuanxian:beast_bone_nascent_soul_rare",r.resolve("yuanxian:beast_bone_qi_refining_common","yuanxian:nascent_soul","rare").orElseThrow().id());
        assertTrue(r.resolve("yuanxian:beast_core_foundation_establishment_common","yuanxian:qi_refining","common").isEmpty());
    }
    @Test void productionKeepsNativeMaterialsSuppressesBeastHabitatStonesAndRespectsTrialShields()throws Exception {
        var r=DropRules.parse(production(),catalog());
        for(var scene:List.of(DropRules.Scene.NORMAL,DropRules.Scene.HABITAT,DropRules.Scene.BATTLE_RUIN)) {
            var result=r.resolve(context("redplate_lizard",scene,16,true));
            assertEquals(DropRules.Mode.INHERIT,result.policy().mode());
            assertEquals(4,result.additions().stream().mapToInt(a->a.drops().size()).sum());
            assertTrue(result.additions().stream().flatMap(a->a.drops().stream()).noneMatch(e->e.item().contains("spirit_stone")));
            assertEquals(result,r.resolve(context("redplate_lizard",scene,16,false)));
        }
        assertTrue(r.resolve(context("demon_wolf",DropRules.Scene.NORMAL,0,true)).additions().stream().flatMap(a->a.drops().stream()).noneMatch(e->e.item().contains("beast_core")));
        assertEquals(1,r.resolve(context("lantern_sporeling",DropRules.Scene.NORMAL,16,true)).additions().size());
        for(var scene:List.of(DropRules.Scene.QUEST_REALM,DropRules.Scene.SECT_TRIAL,DropRules.Scene.APTITUDE_TRIAL))
            assertTrue(DropRules.roll(r.resolve(context("demon_wolf",scene,32,true)),3,new Random(0)).isEmpty());
        assertTrue(r.resolve(context("stele_guardian",DropRules.Scene.NORMAL,32,true)).additions().isEmpty());
    }
    @Test void exactLootingChanceCapsDoNotIncreaseCoreOrBossDrops()throws Exception {
        var r=DropRules.parse(production(),catalog());
        var entries=r.resolve(context("demon_wolf",DropRules.Scene.NORMAL,16,true)).additions().stream().flatMap(a->a.drops().stream()).toList();
        assertEquals(.2,entries.get(1).effectiveChance(0),1e-12);assertEquals(.5,entries.get(1).effectiveChance(3),1e-12);
        assertEquals(.8,entries.get(2).effectiveChance(3),1e-12);assertEquals(.8,entries.get(2).effectiveChance(255),1e-12);
        assertEquals(.2,entries.get(3).effectiveChance(255),1e-12);
        var boss=r.resolve(context("thunder_tiger",DropRules.Scene.NORMAL,16,true));
        assertEquals(DropRules.roll(boss,0,new Random(42)),DropRules.roll(boss,255,new Random(42)));
        assertEquals(7,DropRules.roll(boss,3,new Random(42)).stream().mapToInt(DropRules.Reward::count).sum());
    }
    @Test void everyBossUnitDrawsItsOwnRarityAtExactWeightBoundaries()throws Exception {
        var rules=DropRules.parse(production(),catalog());
        var entry=rules.resolve(context("thunder_tiger",DropRules.Scene.NORMAL,16,true)).additions().getFirst().drops().getFirst();
        var resolution=new DropRules.Resolution("test",new DropRules.Policy(DropRules.Mode.APPEND,List.of(entry)),List.of());
        var random=new Random(){int n;final int[] draws={0,5000,9900};@Override public int nextInt(int bound){return bound==1?0:draws[n++];}};
        var result=DropRules.roll(resolution,0,random);
        assertEquals(List.of("common","uncommon","legendary"),result.stream().map(DropRules.Reward::materialRarity).toList());
        assertEquals(List.of(1,1,1),result.stream().map(DropRules.Reward::count).toList());
    }
    @Test void invalidMaterialFactoriesWeightsAndChanceLootingCannotActivate()throws Exception {
        var original=JsonParser.parseString(production()).getAsJsonObject();
        for(String weights:List.of("[0,0,0,0]","[1,2,3]","[1,2,3,-1]","[1,2,3,0.5]","[1,2,3,true]")) {
            var j=original.deepCopy();j.getAsJsonArray("additions").get(1).getAsJsonObject().getAsJsonArray("drops").get(0).getAsJsonObject().add("rarity_weights",JsonParser.parseString(weights));
            assertThrows(IllegalArgumentException.class,()->DropRules.parse(j.toString(),catalog()));
        }
        var j=original.deepCopy();var entry=j.getAsJsonArray("additions").get(1).getAsJsonObject().getAsJsonArray("drops").get(0).getAsJsonObject();
        entry.addProperty("item","minecraft:diamond");assertThrows(IllegalArgumentException.class,()->DropRules.parse(j.toString(),catalog()));
    }
}
