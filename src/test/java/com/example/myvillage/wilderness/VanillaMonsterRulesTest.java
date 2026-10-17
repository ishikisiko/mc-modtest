package com.example.myvillage.wilderness;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VanillaMonsterRulesTest {
    @Test void vanillaLargeBaseSaturatesWithoutChangingAuthoredBeastLimits() {
        var rules = com.example.myvillage.stats.StatsRules.bundled();
        var realm = rules.realmKey("myvillage:nascent_soul", 2);
        var derived = com.example.myvillage.stats.RealmStats.vanillaDerived(rules, realm, 100, 30);
        assertEquals(rules.limits().maxHealth(), derived.health());
        assertEquals(30 * rules.growth(realm), derived.attack());
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
        assertFalse(rules.includes("myvillage:demon_wolf", true));
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
                 "spawn_reasons":["NATURAL"],"non_attribute_damage_types":["minecraft:arrow"]}
                """));
    }
}
