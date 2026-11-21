package com.example.myvillage.drops;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.stats.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DropRulesTest {
    // 固定样例用于规则优先级及来源切换；实际可编辑包内表由原生启动与导出检查。
    static String defaults()throws Exception{return Files.readString(Path.of("src/test/resources/drops/rules_fixture.json"));}
    static JsonObject json()throws Exception{return JsonParser.parseString(defaults()).getAsJsonObject();}
    static DropRules.Catalog catalog()throws Exception{
        var entities=new HashSet<>(json().getAsJsonObject("entities").keySet());
        return new DropRules.Catalog(Set.of("yuanxian:low_grade_spirit_stone","yuanxian:beast_fire_seed","yuanxian:redplate_scale","minecraft:diamond","yuanxian:pill_huiling","yuanxian:qingfeng_sword","yuanxian:xuantie_helmet","yuanxian:forged_pickaxe")::contains,
            entities::contains,Set.of("donghuang","lingyue","beiyuan","donghai","ximo","nanjiang","moyu","zhongzhou")::contains,
            key->{var s=StatsRules.bundled();s.definition(key);return s.definition(key).order()*16+key.stage();},
            (id,q)->id.equals("yuanxian:pill_huiling")?q>=1&&q<=4:Set.of("yuanxian:qingfeng_sword","yuanxian:xuantie_helmet","yuanxian:forged_pickaxe").contains(id)&&q>=0&&q<=4,
            id->!Set.of("yuanxian:pill_huiling","yuanxian:qingfeng_sword","yuanxian:xuantie_helmet","yuanxian:forged_pickaxe").contains(id));
    }
    static DropRules.Context context(String entity,DropRules.Scene scene){return new DropRules.Context(entity,scene,"donghuang",null,true,false);}
    static JsonObject diamond(String mode){return JsonParser.parseString("{\"mode\":\""+mode+"\",\"drops\":[{\"item\":\"minecraft:diamond\",\"min\":2,\"max\":3,\"chance\":1}]}").getAsJsonObject();}
    @Test void realmPolicyIsSpecificToTheCreatureAndMatchesEveryStage() throws Exception {
        var config = json();
        var lizard = new JsonObject();
        lizard.add("yuanxian:qi_refining", diamond("replace"));
        lizard.add("yuanxian:foundation_establishment", diamond("append"));
        lizard.add("yuanxian:mortal", JsonParser.parseString("{\"mode\":\"disabled\",\"drops\":[]}"));
        var rows = new JsonObject();
        rows.add("yuanxian:redplate_lizard", lizard);
        config.add("realm_entities", rows);
        var catalog = catalog();
        var rules = DropRules.parse(config.toString(), catalog);
        var baseline = DropRules.parse(defaults(), catalog);
        for (var scene : List.of(DropRules.Scene.NORMAL, DropRules.Scene.HABITAT, DropRules.Scene.BATTLE_RUIN)) {
            for (int stage : List.of(0, 8)) {
                int rank = catalog.realmRank().applyAsInt(new RealmKey("qi_refining", stage));
                var context = new DropRules.Context("yuanxian:redplate_lizard", scene, "donghuang", rank, true, false);
                var result = rules.resolve(context);
                assertEquals("realm_entities.yuanxian:redplate_lizard.yuanxian:qi_refining", result.origin());
                assertEquals(DropRules.Mode.REPLACE, result.policy().mode());
                assertTrue(result.additions().isEmpty());
                assertEquals(List.of("minecraft:diamond"), DropRules.roll(result, 0, new Random(1)).stream().map(DropRules.Reward::item).toList());
            }
        }
        int foundation = catalog.realmRank().applyAsInt(new RealmKey("foundation_establishment", 0));
        var supported = new DropRules.Context("yuanxian:redplate_lizard", DropRules.Scene.NORMAL, "donghuang", foundation, true, false);
        assertEquals(DropRules.Mode.APPEND, rules.resolve(supported).policy().mode());
        assertEquals(baseline.resolve(supported).additions(), rules.resolve(supported).additions());
        for (var scene : List.of(DropRules.Scene.QUEST_REALM, DropRules.Scene.SECT_TRIAL, DropRules.Scene.APTITUDE_TRIAL)) {
            var trial = new DropRules.Context("yuanxian:redplate_lizard", scene, "donghuang", foundation, true, false);
            assertEquals(baseline.resolve(trial), rules.resolve(trial));
        }
        int mortal = catalog.realmRank().applyAsInt(new RealmKey("mortal", 1));
        assertEquals(DropRules.Mode.DISABLED, rules.resolve(new DropRules.Context(
                "yuanxian:redplate_lizard", DropRules.Scene.NORMAL, "donghuang", mortal, true, false)).policy().mode());
        for (var unchanged : List.of(context("yuanxian:redplate_lizard", DropRules.Scene.NORMAL),
                new DropRules.Context("yuanxian:demon_wolf", DropRules.Scene.NORMAL, "donghuang", foundation, true, false))) {
            assertEquals(baseline.resolve(unchanged), rules.resolve(unchanged));
        }
    }

    @Test void realmAppendKeepsSceneReplacementAndAcceptsEntitiesWithoutGenericRules() throws Exception {
        var config = json();
        config.getAsJsonObject("entities").remove("minecraft:blaze");
        var realms = new JsonObject();
        realms.add("yuanxian:qi_refining", diamond("append"));
        var overrides = new JsonObject();
        overrides.add("minecraft:blaze", realms);
        config.add("realm_entities", overrides);
        var catalog = catalog();
        var rules = DropRules.parse(config.toString(), catalog);
        var context = new DropRules.Context("minecraft:blaze", DropRules.Scene.HABITAT, "donghuang", 0, true, false);
        var resolved = rules.resolve(context);
        assertTrue(rules.entities().contains("minecraft:blaze"));
        assertEquals(DropRules.Mode.REPLACE, resolved.policy().mode());
        assertEquals(List.of("yuanxian:low_grade_spirit_stone", "minecraft:diamond"),
                resolved.policy().drops().stream().map(DropRules.Entry::item).toList());
        assertEquals(List.of("beast_fire"), resolved.additions().stream().map(DropRules.Addition::id).toList());
        realms.add("yuanxian:unknown", diamond("append"));
        assertThrows(IllegalArgumentException.class, () -> DropRules.parse(config.toString(), catalog));
    }
    @Test void defaultsPreserveNativeTablesAndExplicitSceneOverrides()throws Exception{
        var r=DropRules.parse(defaults(),catalog());assertEquals(71,r.entities().size());
        for(var entity:r.entities())assertEquals(DropRules.Mode.INHERIT,r.resolve(context(entity,DropRules.Scene.NORMAL)).policy().mode());
        var habitat=r.resolve(context("yuanxian:redplate_lizard",DropRules.Scene.HABITAT));
        assertEquals("overrides.donghuang_habitat",habitat.origin());assertEquals(DropRules.Mode.REPLACE,habitat.policy().mode());
        assertEquals("yuanxian:low_grade_spirit_stone",habitat.policy().drops().getFirst().item());assertEquals(1,habitat.policy().drops().getFirst().min());assertEquals(3,habitat.policy().drops().getFirst().max());
        for(var scene:List.of(DropRules.Scene.APTITUDE_TRIAL,DropRules.Scene.SECT_TRIAL,DropRules.Scene.QUEST_REALM))assertEquals(DropRules.Mode.DISABLED,r.resolve(context("yuanxian:demon_wolf",scene)).policy().mode());
    }
    @Test void inheritedFireHasExactConditionsAndNoRewardInDisabledScenes()throws Exception{
        var r=DropRules.parse(defaults(),catalog());var ordinary=r.resolve(context("minecraft:blaze",DropRules.Scene.NORMAL));assertEquals(1,ordinary.additions().size());
        assertEquals(.035,ordinary.additions().getFirst().drops().getFirst().chance());assertEquals("beast_fire",ordinary.additions().getFirst().drops().getFirst().factory());
        assertTrue(r.resolve(new DropRules.Context("minecraft:blaze",DropRules.Scene.NORMAL,"",null,false,false)).additions().isEmpty());
        assertTrue(r.resolve(new DropRules.Context("minecraft:blaze",DropRules.Scene.NORMAL,"",null,true,true)).additions().isEmpty());
        assertTrue(r.resolve(context("minecraft:blaze",DropRules.Scene.QUEST_REALM)).additions().isEmpty());
    }
    @Test void entityReplacementAppendAndDisableHaveUnambiguousModes()throws Exception{
        for(String mode:List.of("replace","append")){
            var j=json();j.getAsJsonObject("entities").add("yuanxian:demon_wolf",diamond(mode));var r=DropRules.parse(j.toString(),catalog());
            var result=r.resolve(context("yuanxian:demon_wolf",DropRules.Scene.NORMAL));assertEquals(DropRules.Mode.valueOf(mode.toUpperCase()),result.policy().mode());assertEquals(1,DropRules.roll(result,0,new Random(1)).size());
            assertEquals(DropRules.Mode.DISABLED,r.resolve(context("yuanxian:demon_wolf",DropRules.Scene.SECT_TRIAL)).policy().mode());
        }
        var j=json();j.getAsJsonObject("entities").add("minecraft:blaze",JsonParser.parseString("{\"mode\":\"disabled\",\"drops\":[]}"));
        assertTrue(DropRules.roll(DropRules.parse(j.toString(),catalog()).resolve(context("minecraft:blaze",DropRules.Scene.NORMAL)),255,new Random(2)).isEmpty());
    }
    @Test void orderedConditionsUseEntitySceneRegionAndInclusiveRealmBounds()throws Exception{
        var j=json();var override=diamond("replace");override.addProperty("id","regional");
        override.add("when",JsonParser.parseString("{\"entities\":[\"yuanxian:redplate_lizard\"],\"scenes\":[\"normal\"],\"regions\":[\"donghuang\"],\"min_realm\":{\"realm\":\"yuanxian:qi_refining\",\"stage\":2},\"max_realm\":{\"realm\":\"yuanxian:qi_refining\",\"stage\":8}}"));
        j.getAsJsonArray("overrides").add(override);var r=DropRules.parse(j.toString(),catalog());
        for(int stage=0;stage<9;stage++){
            int rank=catalog().realmRank().applyAsInt(new RealmKey("qi_refining",stage));
            assertEquals(stage>=2?DropRules.Mode.REPLACE:DropRules.Mode.INHERIT,r.resolve(new DropRules.Context("yuanxian:redplate_lizard",DropRules.Scene.NORMAL,"donghuang",rank,true,false)).policy().mode());
        }
        assertEquals(DropRules.Mode.INHERIT,r.resolve(context("yuanxian:redplate_lizard",DropRules.Scene.NORMAL)).policy().mode());
        assertEquals(DropRules.Mode.INHERIT,r.resolve(new DropRules.Context("yuanxian:redplate_lizard",DropRules.Scene.NORMAL,"lingyue",500,true,false)).policy().mode());
    }
    @Test void rollHandlesChanceEndpointsLootingCapsAndBothQuantityEndpoints()throws Exception{
        var j=json();var policy=diamond("replace");var e=policy.getAsJsonArray("drops").get(0).getAsJsonObject();e.addProperty("min",1);e.addProperty("max",2);
        e.add("looting",JsonParser.parseString("{\"min_per_level\":0,\"max_per_level\":1,\"limit\":4}"));j.getAsJsonObject("entities").add("yuanxian:demon_wolf",policy);
        var result=DropRules.parse(j.toString(),catalog()).resolve(context("yuanxian:demon_wolf",DropRules.Scene.NORMAL));
        Random maximum=new Random(){public int nextInt(int bound){return bound-1;}public double nextDouble(){return Math.nextDown(1d);}};
        assertEquals(2,DropRules.roll(result,0,maximum).getFirst().count());assertEquals(4,DropRules.roll(result,3,maximum).getFirst().count());
        Random minimum=new Random(){public int nextInt(int bound){return 0;}public double nextDouble(){return 0;}};assertEquals(1,DropRules.roll(result,3,minimum).getFirst().count());
        e.addProperty("chance",0);j.getAsJsonObject("entities").add("yuanxian:demon_wolf",policy);assertTrue(DropRules.roll(DropRules.parse(j.toString(),catalog()).resolve(context("yuanxian:demon_wolf",DropRules.Scene.NORMAL)),3,minimum).isEmpty());
        var fire=DropRules.parse(defaults(),catalog()).resolve(context("minecraft:blaze",DropRules.Scene.NORMAL));
        assertEquals(1,DropRules.roll(fire,0,minimum).size());Random boundary=new Random(){public double nextDouble(){return .035;}};assertTrue(DropRules.roll(fire,0,boundary).isEmpty());
    }
    @Test void quantityLootingCanExceedAStackWhenTheLimitIsOmitted()throws Exception{
        var config=json();
        var policy=diamond("replace");
        var drop=policy.getAsJsonArray("drops").get(0).getAsJsonObject();
        drop.addProperty("min",64);
        drop.addProperty("max",64);
        var looting=JsonParser.parseString("""
                {"min_per_level":1,"max_per_level":1}
                """).getAsJsonObject();
        drop.add("looting",looting);
        config.getAsJsonObject("entities").add("yuanxian:demon_wolf",policy);
        var context=context("yuanxian:demon_wolf",DropRules.Scene.NORMAL);
        var uncapped=DropRules.parse(config.toString(),catalog()).resolve(context);
        assertEquals(64,DropRules.roll(uncapped,0,new Random(1)).getFirst().count());
        assertEquals(74,DropRules.roll(uncapped,10,new Random(1)).getFirst().count());

        looting.addProperty("limit",64);
        var capped=DropRules.parse(config.toString(),catalog()).resolve(context);
        assertEquals(64,DropRules.roll(capped,10,new Random(1)).getFirst().count());
    }
    @Test void duplicateFieldsTrailingDocumentsAndUnknownFieldsFailWithPaths()throws Exception{
        var c=catalog();String text=defaults();assertThrows(IllegalArgumentException.class,()->DropRules.parse(text+" {}",c));
        assertThrows(IllegalArgumentException.class,()->DropRules.parse(text.replace("\"schema\": 1","\"schema\": 1, \"schema\": 1"),c));
        var j=json();j.addProperty("typo",1);var error=assertThrows(IllegalArgumentException.class,()->DropRules.parse(j.toString(),c));assertTrue(error.getMessage().contains("$.typo"));
        assertThrows(IllegalArgumentException.class,()->DropRules.parse("{"+" ".repeat(DropRules.MAX_BYTES)+"}",c));
    }
    @Test void invalidRegistryIdsProbabilitiesCountsFactoriesAndRealmsAreRejected()throws Exception{
        var c=catalog();
        for(String field:List.of("item","min","max","chance","factory")){
            var j=json();var p=diamond("replace");var e=p.getAsJsonArray("drops").get(0).getAsJsonObject();
            switch(field){case "item"->e.addProperty(field,"yuanxian:missing");case "min"->e.addProperty(field,1.5);case "max"->e.addProperty(field,65);case "chance"->e.addProperty(field,1.01);case "factory"->e.addProperty(field,"beast_fire");}
            j.getAsJsonObject("entities").add("yuanxian:demon_wolf",p);var error=assertThrows(IllegalArgumentException.class,()->DropRules.parse(j.toString(),c));assertTrue(error.getMessage().contains(".drops[0]."+field));
        }
        var j=json();j.getAsJsonObject("entities").add("yuanxian:missing",diamond("replace"));assertThrows(IllegalArgumentException.class,()->DropRules.parse(j.toString(),c));
        var unknown=json();unknown.getAsJsonArray("overrides").get(3).getAsJsonObject().getAsJsonObject("when").add("regions",JsonParser.parseString("[\"nowhere\"]"));assertThrows(IllegalArgumentException.class,()->DropRules.parse(unknown.toString(),c));
        var badRealm=json();badRealm.getAsJsonArray("overrides").get(3).getAsJsonObject().getAsJsonObject("when").add("min_realm",JsonParser.parseString("{\"realm\":\"yuanxian:qi_refining\",\"stage\":9}"));assertThrows(IllegalArgumentException.class,()->DropRules.parse(badRealm.toString(),c));
    }
    @Test void immutableRulesAreNotChangedThroughExposedJsonOrCollections()throws Exception{
        var r=DropRules.parse(defaults(),catalog());r.json().getAsJsonObject("entities").remove("yuanxian:demon_wolf");assertTrue(r.entities().contains("yuanxian:demon_wolf"));
        var h=r.resolve(context("yuanxian:demon_wolf",DropRules.Scene.HABITAT));assertThrows(UnsupportedOperationException.class,()->h.policy().drops().clear());assertThrows(UnsupportedOperationException.class,()->r.entities().clear());
    }
    @Test void qualityAndRarityReachRewardsWithoutChangingQuantities()throws Exception{
        for(String item:List.of("yuanxian:pill_huiling","yuanxian:qingfeng_sword","yuanxian:xuantie_helmet","yuanxian:forged_pickaxe")){
            var j=json();var policy=diamond("replace");var e=policy.getAsJsonArray("drops").get(0).getAsJsonObject();e.addProperty("item",item);e.addProperty("quality",4);
            j.getAsJsonObject("entities").add("yuanxian:demon_wolf",policy);
            var reward=DropRules.roll(DropRules.parse(j.toString(),catalog()).resolve(context("yuanxian:demon_wolf",DropRules.Scene.NORMAL)),0,new Random(1)).getFirst();
            assertEquals(item,reward.item());assertEquals(4,reward.quality());assertNull(reward.rarity());assertTrue(reward.count()>=2&&reward.count()<=4);
        }
        var j=json();var policy=diamond("replace");policy.getAsJsonArray("drops").get(0).getAsJsonObject().addProperty("rarity","epic");j.getAsJsonObject("entities").add("yuanxian:demon_wolf",policy);
        var reward=DropRules.roll(DropRules.parse(j.toString(),catalog()).resolve(context("yuanxian:demon_wolf",DropRules.Scene.NORMAL)),0,new Random(1)).getFirst();
        assertEquals("epic",reward.rarity());assertNull(reward.quality());
    }
    @Test void invalidVariantsAreRejectedBeforeRulesCanActivate()throws Exception{
        for(String value:List.of("null","true","1.5","-1","5","\"4\"")){
            var j=json();var p=diamond("replace");var e=p.getAsJsonArray("drops").get(0).getAsJsonObject();e.addProperty("item","yuanxian:pill_huiling");e.add("quality",JsonParser.parseString(value));j.getAsJsonObject("entities").add("yuanxian:demon_wolf",p);
            assertThrows(IllegalArgumentException.class,()->DropRules.parse(j.toString(),catalog()),value);
        }
        for(String item:List.of("minecraft:diamond","yuanxian:pill_huiling")){
            var j=json();var p=diamond("replace");var e=p.getAsJsonArray("drops").get(0).getAsJsonObject();e.addProperty("item",item);e.addProperty("quality",0);j.getAsJsonObject("entities").add("yuanxian:demon_wolf",p);assertThrows(IllegalArgumentException.class,()->DropRules.parse(j.toString(),catalog()));
        }
        for(String value:List.of("null","0","true","\"legendary\"","\"EPIC\"")){
            var j=json();var p=diamond("replace");p.getAsJsonArray("drops").get(0).getAsJsonObject().add("rarity",JsonParser.parseString(value));j.getAsJsonObject("entities").add("yuanxian:demon_wolf",p);assertThrows(IllegalArgumentException.class,()->DropRules.parse(j.toString(),catalog()));
        }
        var j=json();var p=diamond("replace");var e=p.getAsJsonArray("drops").get(0).getAsJsonObject();e.addProperty("item","yuanxian:qingfeng_sword");e.addProperty("quality",2);e.addProperty("rarity","epic");j.getAsJsonObject("entities").add("yuanxian:demon_wolf",p);assertThrows(IllegalArgumentException.class,()->DropRules.parse(j.toString(),catalog()));
    }

}
