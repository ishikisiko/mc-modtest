package com.example.myvillage.exploration;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.wilderness.WildernessRules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;

class ExplorationRulesTest {
    private static JsonObject json() throws Exception { return JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/myvillage/exploration/rules.json"))).getAsJsonObject(); }
    @Test void regionalFloorNeverWeakensLocalMobsOrAddsGrowthTwice() {
        var r = ExplorationRules.get(); var mine = r.location("mineshaft");
        var low = WildernessRules.bundled().region("ximo").orElseThrow().beast();
        var high = WildernessRules.bundled().region("zhongzhou").orElseThrow().beast();
        assertEquals(low, r.floor(mine.encounters(), low));
        assertEquals(high, r.floor(mine.encounters(), high));
        assertEquals(high, r.floor(r.floor(mine.encounters(), high), high));
        assertTrue(r.rank(r.floor(mine.recommended(), high).min()) >= r.rank(high.min()));
    }
    @Test void netherIsIndependentAndEndRemainsGuidanceOnly() {
        var r = ExplorationRules.get();
        assertFalse(r.location("fortress").regional());
        assertEquals(new RealmKey("foundation_establishment",0), r.location("fortress").recommended().min());
        assertEquals(new RealmKey("foundation_establishment",2), r.location("bastion").encounters().max());
        assertNull(r.location("end").encounters()); assertNull(r.location("end_city").encounters());
        assertEquals("none",r.location("end_city").reward());
    }
    @Test void ordinaryAndOminousTrialsHaveDistinctExplicitBands() {
        var r = ExplorationRules.get();
        assertEquals("trial", r.birth("TRIAL_SPAWNER").orElseThrow());
        assertEquals("ominous_trial", r.birth("OMINOUS_TRIAL").orElseThrow());
        assertTrue(r.rank(r.location("ominous_trial").encounters().min()) > r.rank(r.location("trial").encounters().max()));
        assertTrue(r.location("ominous_trial").contextOnly()); assertTrue(r.location("raid").contextOnly());
    }
    @Test void rewardsFollowLocationRiskInsteadOfTheLootingPlayersRealm() {
        var r = ExplorationRules.get();
        assertEquals("novice",r.rewardPool("novice",new RealmKey("qi_refining",0)));
        assertEquals("deep",r.rewardPool("novice",new RealmKey("foundation_establishment",0)));
        assertEquals("exalted",r.rewardPool("novice",new RealmKey("nascent_soul",2)));
        assertEquals("deep",r.rewardPool("deep",new RealmKey("mortal",0)));
        assertTrue(r.lootTables().keySet().stream().noneMatch(s -> s.contains("entities/") || s.contains("spawners/") || s.endsWith("reward_common")));
    }
    @Test void guideUsesEachPlayersRealStateAndSupportsOutOfOrderVanillaProgress() {
        var r = ExplorationRules.get(); var none = new HashSet<String>();
        assertEquals("awaken",r.next(new RealmKey("mortal",0),none).id());
        none.add("awakened"); assertEquals("initiate",r.next(new RealmKey("mortal",1),none).id());
        none.addAll(Set.of("initiated","iron","nether","fortress","stronghold"));
        assertEquals("foundation",r.next(new RealmKey("qi_refining",8),none).id());
        assertEquals("golden",r.next(new RealmKey("foundation_establishment",2),none).id());
        assertEquals("dragon",r.next(new RealmKey("golden_core",0),none).id());
        none.add("dragon"); assertNull(r.next(new RealmKey("golden_core",0),none));
        assertEquals("awaken",r.next(new RealmKey("golden_core",0),Set.of()).id());
    }
    @Test void duplicateUnknownReversedAndUnboundedRulesAreRejected() throws Exception {
        String good = json().toString();
        assertThrows(IllegalArgumentException.class,()->ExplorationRules.parse(good.replace("\"schema\":1","\"schema\":1,\"schema\":1")));
        assertThrows(IllegalArgumentException.class,()->ExplorationRules.parse(good+" {}"));
        var bad = json(); bad.getAsJsonObject("settings").addProperty("context_radius",1000);
        assertThrows(IllegalArgumentException.class,()->ExplorationRules.parse(bad.toString()));
        var extra = json(); extra.addProperty("auto_scale_to_player",true);
        assertThrows(IllegalArgumentException.class,()->ExplorationRules.parse(extra.toString()));
        var reversed = json(); var range = reversed.getAsJsonArray("locations").get(0).getAsJsonObject().getAsJsonObject("recommended");
        range.add("min",range.get("max").deepCopy()); range.getAsJsonObject("max").addProperty("realm","myvillage:mortal");
        assertThrows(IllegalArgumentException.class,()->ExplorationRules.parse(reversed.toString()));
    }
}
