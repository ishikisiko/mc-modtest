package com.example.myvillage.wilderness;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.exploration.ExplorationRules;
import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.StatsRules;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class VanillaMonsterRulesTest {
    @Test void vanillaLargeBaseSaturatesWithoutChangingAuthoredBeastLimits() {
        var rules = com.example.myvillage.stats.StatsRules.bundled();
        var realm = rules.realmKey("yuanxian:nascent_soul", 2);
        var derived = com.example.myvillage.stats.RealmStats.vanillaDerived(rules, realm, 100, 30);
        var additions = com.example.myvillage.stats.CultivationStatsRules.bundled();
        var body = additions.at(realm, 0, 0);
        assertEquals(Math.min(100 + body.health() - additions.reference().health(), rules.limits().maxHealth()), derived.health());
        assertEquals(Math.min(30 + body.attack() - additions.reference().attack(), rules.limits().maxAttack()), derived.attack());
        var saturated = com.example.myvillage.stats.RealmStats.vanillaDerived(rules, realm,
                rules.limits().maxHealth(), rules.limits().maxAttack());
        assertEquals(rules.limits().maxHealth(), saturated.health());
        assertEquals(rules.limits().maxAttack(), saturated.attack());
        assertThrows(IllegalArgumentException.class,
                () -> com.example.myvillage.stats.RealmStats.derived(rules, realm, 100, 30));
    }
    @Test void scopeExcludesBossesOtherModsAndPassiveEntities() {
        var rules = VanillaMonsterRules.bundled();
        for (String id : new String[]{"zombie", "skeleton", "creeper", "slime", "enderman", "witch"})
            assertTrue(rules.includes("minecraft:" + id, true));
        for (String id : new String[]{"wither", "ender_dragon", "warden", "elder_guardian"})
            assertFalse(rules.includes("minecraft:" + id, true));
        assertFalse(rules.includes("minecraft:cow", false));
        assertFalse(rules.includes("yuanxian:demon_wolf", true));
    }
    @Test void explicitSpawnAndDamagePoliciesDoNotDoubleScaleMeleeOrConvertByLocation() {
        var rules = VanillaMonsterRules.bundled();
        assertTrue(rules.spawnReasons().contains("NATURAL"));
        assertTrue(rules.spawnReasons().contains("SPAWNER"));
        for (String reason : new String[]{"COMMAND", "SPAWN_EGG", "CONVERSION", "BREEDING"})
            assertFalse(rules.spawnReasons().contains(reason));
        assertTrue(rules.nonAttributeDamageTypes().contains("minecraft:arrow"));
        assertTrue(rules.nonAttributeDamageTypes().contains("minecraft:explosion"));
        assertFalse(rules.nonAttributeDamageTypes().contains("minecraft:mob_attack"));
        assertFalse(rules.nonAttributeDamageTypes().contains("minecraft:fall"));
    }
    @Test void invalidSchemaAndDuplicateEntriesFail() {
        assertThrows(IllegalArgumentException.class, () -> VanillaMonsterRules.parse("{}"));
        assertThrows(IllegalArgumentException.class, () -> VanillaMonsterRules.parse("""
                {"schema":1,"excluded_entities":["minecraft:wither","minecraft:wither"],
                 "minimum_realm":{"realm":"yuanxian:mortal","stage":0},
                 "spawn_reasons":["NATURAL"],"non_attribute_damage_types":["minecraft:arrow"]}
                """));
    }

    @Test void onlyDonghuangAllowsMortalBirthsAndOtherRegionsShareTheBeastPool() {
        var rules = VanillaMonsterRules.bundled(); var exploration = ExplorationRules.get();
        var unranked = new RealmKey("mortal", 0);
        assertEquals(unranked, rules.minimumRealm());
        assertEquals(1, StatsRules.bundled().growth(unranked));
        for (var region : WildernessRules.bundled().regions().entrySet()) {
            var beast = region.getValue().beast();
            assertTrue(exploration.rank(beast.min()) >= exploration.rank(new RealmKey("qi_refining", 0)));
            var vanilla = exploration.vanillaEncounters(exploration.location("overworld"), region.getValue());
            boolean east = region.getKey().equals("donghuang");
            assertEquals(east ? unranked : beast.min(), vanilla.min());
            assertEquals(beast.max(), vanilla.max());
            if (!east) assertEquals(beast, vanilla);
            var reached = new HashSet<RealmKey>();
            for (int i = 0; i < 4096; i++) reached.add(vanilla.sample(42, region.getKey(), i));
            assertEquals(east, reached.contains(unranked));
            assertTrue(reached.contains(beast.max()));
            assertEquals(new HashSet<>(vanilla.values()), reached);
        }
    }

    @Test void overworldStructuresUseDailyRealmsAndOminousTrialsUseTheEliteBand() {
        var exploration = ExplorationRules.get();
        var regional = WildernessRules.bundled().region("zhongzhou").orElseThrow();
        for (var location : exploration.locations()) {
            if (!location.dimension().equals("minecraft:overworld")) continue;
            var vanilla = exploration.vanillaEncounters(location, regional);
            var expected = location.id().equals("ominous_trial") ? regional.eliteEncounters() : regional.beast();
            assertEquals(expected, vanilla, location.id());
        }
        assertEquals(java.util.List.of(new RealmKey("spirit_transformation", 0), new RealmKey("spirit_transformation", 1)),
                regional.eliteEncounters().values());
        assertEquals(new RealmKey("spirit_transformation", 2), regional.encounterMax());
        assertNull(exploration.vanillaEncounters(exploration.location("overworld"), null));
    }

    @Test void unknownFractionalAndExtraMinimumFieldsAreRejected() throws Exception {
        String input = Files.readString(Path.of("src/main/resources/data/yuanxian/wilderness/vanilla_monsters.json"));
        for (String field : new String[]{"unknown", "fractional", "extra"}) {
            var root = JsonParser.parseString(input).getAsJsonObject(); var minimum = root.getAsJsonObject("minimum_realm");
            switch (field) {
                case "unknown" -> minimum.addProperty("realm", "yuanxian:missing");
                case "fractional" -> minimum.addProperty("stage", .5);
                default -> minimum.addProperty("chance", .5);
            }
            assertThrows(IllegalArgumentException.class, () -> VanillaMonsterRules.parse(root.toString()));
        }
    }
}
