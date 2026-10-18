package com.example.myvillage.exploration.boss;

import com.example.myvillage.stats.StatsRules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossRulesTest {
    private String source() throws IOException {
        try (var in = BossRulesTest.class.getResourceAsStream(BossRules.PATH)) {
            assertNotNull(in); return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private void rejects(Consumer<JsonObject> mutation) throws IOException {
        var root = JsonParser.parseString(source()).getAsJsonObject(); mutation.accept(root);
        assertThrows(IllegalArgumentException.class, () -> BossRules.parse(root.toString()));
    }
    @Test void allBossesUseEffectiveAttributesInsideNativeLimits() {
        var rules = BossRules.get();
        assertSame(rules, BossRules.get());
        assertEquals(Set.of("minecraft:elder_guardian", "minecraft:ender_dragon", "minecraft:wither", "minecraft:warden"),
                rules.profiles().stream().map(BossRules.Profile::entity).collect(java.util.stream.Collectors.toSet()));
        for (var profile : rules.profiles()) {
            assertTrue(profile.health() > 0 && profile.health() <= StatsRules.bundled().limits().maxHealth());
            assertNotEquals("myvillage:mortal", profile.realm().realmId());
            assertSame(profile, rules.profile(profile.id()).orElseThrow());
            assertThrows(UnsupportedOperationException.class, () -> profile.damage().clear());
        }
        assertThrows(UnsupportedOperationException.class, () -> rules.profiles().clear());
        // Dragon and wither attacks are hard-coded; assigning an attack attribute cannot scale them.
        assertNull(rules.forEntity("minecraft:ender_dragon").orElseThrow().attributeAttack());
        assertNull(rules.forEntity("minecraft:wither").orElseThrow().attributeAttack());
    }
    @Test void nativeAttackPathsHaveSeparatePoliciesAndRetainVariantRatios() {
        var dragon = BossRules.get().forEntity("minecraft:ender_dragon").orElseThrow();
        var contact = dragon.damage().get(BossRules.Channel.DRAGON_CONTACT);
        assertEquals(2 * contact.apply(5), contact.apply(10));
        var cloud = dragon.damage().get(BossRules.Channel.DRAGON_BREATH);
        assertEquals(2 * cloud.apply(3), cloud.apply(6));
        assertNotEquals(contact.factor(), cloud.factor());
        var elder = BossRules.get().forEntity("minecraft:elder_guardian").orElseThrow();
        assertEquals(5.0 / 3.0, elder.damage().get(BossRules.Channel.GUARDIAN_BEAM).apply(5)
                / elder.damage().get(BossRules.Channel.GUARDIAN_BEAM).apply(3), 1e-6);
        var wither = BossRules.get().forEntity("minecraft:wither").orElseThrow();
        assertNotEquals(wither.damage().get(BossRules.Channel.WITHER_SKULL).factor(),
                wither.damage().get(BossRules.Channel.WITHER_SKULL_EXPLOSION).factor());
        assertTrue(wither.damage().get(BossRules.Channel.WITHER_SPAWN_EXPLOSION).apply(200) <= 96);
    }
    @Test void damageCapsNeverCreateDamageFromZeroOrOverflowNormalInputs() {
        for (var p : BossRules.get().profiles()) for (var d : p.damage().values()) {
            assertEquals(0, d.apply(0));
            assertEquals(-0.0f, d.apply(-0.0f));
            assertTrue(Float.isFinite(d.apply(Float.MAX_VALUE)));
            assertTrue(d.apply(Float.MAX_VALUE) <= d.maximum());
        }
    }
    @Test void healthAboveVanillaCeilingAndFakeAttributePathsAreRejected() throws IOException {
        rejects(r -> r.getAsJsonArray("profiles").get(0).getAsJsonObject().addProperty("health", 1025));
        rejects(r -> r.getAsJsonArray("profiles").get(1).getAsJsonObject().addProperty("attribute_attack", 30));
        rejects(r -> r.getAsJsonArray("profiles").get(0).getAsJsonObject().add("attribute_attack", com.google.gson.JsonNull.INSTANCE));
        rejects(r -> r.getAsJsonArray("profiles").get(0).getAsJsonObject().addProperty("stage", 0.5));
        rejects(r -> r.getAsJsonArray("profiles").get(0).getAsJsonObject().addProperty("health", "240"));
    }
    @Test void missingDuplicateUnexpectedAndUnsafeDamagePoliciesAreRejected() throws IOException {
        rejects(r -> r.addProperty("schema", 2));
        rejects(r -> r.addProperty("unknown", true));
        rejects(r -> r.getAsJsonArray("profiles").remove(0));
        rejects(r -> r.getAsJsonArray("profiles").add(r.getAsJsonArray("profiles").get(0).deepCopy()));
        rejects(r -> r.getAsJsonArray("profiles").get(0).getAsJsonObject().getAsJsonObject("damage").remove("guardian_beam"));
        rejects(r -> r.getAsJsonArray("profiles").get(0).getAsJsonObject().getAsJsonObject("damage")
                .getAsJsonObject("guardian_beam").addProperty("factor", 0));
        rejects(r -> r.getAsJsonArray("profiles").get(0).getAsJsonObject().getAsJsonObject("damage")
                .getAsJsonObject("guardian_beam").addProperty("maximum", 1e100));
        assertThrows(IllegalArgumentException.class, () -> BossRules.parse(source() + "{}"));
        assertThrows(IllegalArgumentException.class, () -> BossRules.parse(source().replace("\"schema\": 1", "\"schema\": 1, \"schema\": 1")));
    }
}
