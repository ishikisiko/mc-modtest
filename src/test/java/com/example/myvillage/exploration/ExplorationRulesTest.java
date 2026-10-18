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
    @Test void netherAndEndHaveIndependentEncounterBands() {
        var r = ExplorationRules.get();
        assertFalse(r.location("fortress").regional());
        assertEquals(new RealmKey("foundation_establishment",0), r.location("fortress").recommended().min());
        assertEquals(new RealmKey("foundation_establishment",2), r.location("bastion").encounters().max());
        assertEquals(new RealmKey("golden_core",0),r.location("end").encounters().min());
        assertEquals(new RealmKey("golden_core",1),r.location("end_outer").encounters().max());
        assertEquals(new RealmKey("golden_core",2),r.location("end_city").encounters().max());
        for (var location : r.locations()) if (!location.dimension().equals("minecraft:overworld")) assertFalse(location.regional());
        assertEquals("endgame",r.location("end_city").reward());
        assertEquals("endgame",r.lootTables().get("minecraft:chests/end_city_treasure"));
        assertEquals("endgame",r.rewardPool("endgame",new RealmKey("nascent_soul",2)));
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
        none.add("dragon"); assertEquals("islands",r.next(new RealmKey("golden_core",0),none).id());
        none.add("islands"); assertEquals("end_city",r.next(new RealmKey("golden_core",1),none).id());
        none.add("end_city"); assertEquals("elytra",r.next(new RealmKey("golden_core",1),none).id());
        none.add("elytra"); assertNull(r.next(new RealmKey("golden_core",1),none));
        assertEquals(Set.of("elder_guardian", "wither", "warden"), r.sideRoutes().stream().map(ExplorationRules.SideRoute::id).collect(java.util.stream.Collectors.toSet()));
        assertEquals("awaken",r.next(new RealmKey("golden_core",0),Set.of()).id());
    }
    @Test void portalGatesAreOptInAndNeverBlockReturnsOrSameDimension() throws Exception {
        var bundled = ExplorationRules.get();
        assertFalse(bundled.portalGates().enabled());
        assertFalse(ExplorationServerConfig.SPEC.isLoaded());
        assertEquals(bundled.portalGates().enabled(), ExplorationServerConfig.portalGatesEnabled());
        assertEquals(bundled.portalGates().endMinimum(), bundled.portalMinimum("minecraft:overworld", "minecraft:the_end", true).orElseThrow());
        assertTrue(bundled.portalMinimum("minecraft:overworld", "minecraft:the_end").isEmpty());
        var config = json(); config.getAsJsonObject("portal_gates").addProperty("enabled", true);
        var enabled = ExplorationRules.parse(config.toString());
        assertEquals(new RealmKey("foundation_establishment", 0), enabled.portalMinimum("minecraft:overworld", "minecraft:the_nether").orElseThrow());
        assertEquals(new RealmKey("golden_core", 0), enabled.portalMinimum("minecraft:overworld", "minecraft:the_end").orElseThrow());
        assertTrue(enabled.portalMinimum("minecraft:the_end", "minecraft:overworld").isEmpty());
        assertTrue(enabled.portalMinimum("minecraft:the_nether", "minecraft:overworld").isEmpty());
        assertTrue(enabled.portalMinimum("minecraft:the_end", "minecraft:the_end").isEmpty());
        assertTrue(enabled.portalMinimum("minecraft:overworld", "example:other").isEmpty());
        config.getAsJsonObject("portal_gates").addProperty("enabled", "true");
        assertThrows(IllegalArgumentException.class, () -> ExplorationRules.parse(config.toString()));
        var regional = json(); regional.getAsJsonArray("locations").get(regional.getAsJsonArray("locations").size()-1).getAsJsonObject().addProperty("regional", true);
        assertThrows(IllegalArgumentException.class, () -> ExplorationRules.parse(regional.toString()));
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
