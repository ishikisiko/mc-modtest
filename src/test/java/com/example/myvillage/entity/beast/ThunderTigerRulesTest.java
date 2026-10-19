package com.example.myvillage.entity.beast;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.stats.RealmStats;
import com.example.myvillage.stats.StatsRules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Boss-specific boundaries without a game bootstrap; lifecycle and hit routing need a server. */
final class ThunderTigerRulesTest {
    private JsonObject source() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources", ThunderTigerRules.PATH))).getAsJsonObject();
    }
    private ThunderTigerRules rules() throws Exception { return ThunderTigerRules.parse(source()); }
    private BeastDefinition beast() {
        return new BeastDefinitions(BeastTestData.load()).require(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("myvillage", "thunder_tiger"));
    }
    @Test void bundledBossHasAllReadableMovesAndSafeAftershockTiming() throws Exception {
        var rules = rules();
        var beast = beast();
        assertEquals(Set.of("claw", "pounce", "thunder_stomp", "roar"), beast.moves().stream().map(BeastMoveDefinition::animation).collect(java.util.stream.Collectors.toSet()));
        for (var move : beast.moves()) {
            assertTrue(move.turnLockTick() < move.activeTicks().first(), move.animation() + " must allow a dodge after lock");
            assertTrue(move.activeTicks().last() < move.totalTicks() - 1, move.animation() + " needs a recovery window");
            if (move.animation().equals("pounce")) {
                int landing = move.lunge().tick() + BeastMotion.simulate(move.lunge().forwardMax(), move.lunge().up()).airborneTicks() + 1;
                assertTrue(landing + rules.phaseTwo().aftershockDelayTicks() < move.totalTicks(), "landing aftershock must occur before its victim ledger is released");
            }
            if (move.animation().equals("thunder_stomp") || move.animation().equals("roar")) {
                assertTrue(move.activeTicks().first() + rules.phaseTwo().aftershockDelayTicks() < move.totalTicks());
            }
        }
    }
    @Test void phaseIsMonotonicIncludingHealedAndReloadedHealth() throws Exception {
        var rules = rules();
        assertEquals(1, rules.phaseAfter(1, 960, 960));
        assertEquals(2, rules.phaseAfter(1, (float) (960 * rules.phaseHealthFraction()), 960));
        assertEquals(2, rules.phaseAfter(2, 960, 960), "healing cannot undo phase two");
        assertEquals(2, rules.phaseAfter(1, 1, 960), "old save phase derives safely from health");
    }
    @Test void fixedEncounterFitsEffectiveCapsWithoutCapDistortion() throws Exception {
        var rules = rules(); var beast = beast(); var stats = StatsRules.bundled();
        var key = stats.realmKey(rules.encounterRealm().toString(), rules.encounterStage());
        var derived = RealmStats.vanillaDerived(stats, key, beast.attributes().maxHealth(), beast.attributes().attackDamage());
        assertEquals(beast.attributes().maxHealth() * derived.factor(), derived.health(), "boss identity must not silently saturate health");
        assertEquals(beast.attributes().attackDamage() * derived.factor(), derived.attack());
        assertTrue(derived.health() > 0);
    }
    @Test void configurationRejectsUnboundedPillarScan() throws Exception {
        var json = source(); json.getAsJsonObject("horn").addProperty("maximum_pillars", 17);
        assertEquals("horn.maximum_pillars", assertThrows(BeastDataException.class, () -> ThunderTigerRules.parse(json)).field());
    }
    @Test void configurationRejectsInvalidHomeAndExposureTimings() throws Exception {
        var json = source(); json.getAsJsonObject("bounds").addProperty("target_radius", 999);
        assertEquals("bounds.target_radius", assertThrows(BeastDataException.class, () -> ThunderTigerRules.parse(json)).field());
        var exposed = source(); exposed.getAsJsonObject("presentation").addProperty("horn_exposed_ticks", 1);
        assertEquals("presentation.horn_exposed_ticks", assertThrows(BeastDataException.class, () -> ThunderTigerRules.parse(exposed)).field());
    }
    @Test void configurationRejectsUnknownOrIncompleteTuning() throws Exception {
        var unknown = source(); unknown.addProperty("extra_damage", 1000);
        assertEquals("extra_damage", assertThrows(BeastDataException.class, () -> ThunderTigerRules.parse(unknown)).field());
        var missing = source(); missing.getAsJsonObject("horn").remove("damage_to_break");
        assertEquals("horn.damage_to_break", assertThrows(BeastDataException.class, () -> ThunderTigerRules.parse(missing)).field());
    }
}
