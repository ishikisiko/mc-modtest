package com.example.myvillage.exploration.boss;

import com.example.myvillage.stats.RealmKey;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossRulesTest {
    private String source() throws IOException {
        try (var in = BossRulesTest.class.getResourceAsStream(BossRules.PATH)) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void rejects(Consumer<JsonObject> mutation) throws IOException {
        var root = JsonParser.parseString(source()).getAsJsonObject();
        mutation.accept(root);
        assertThrows(IllegalArgumentException.class, () -> BossRules.parse(root.toString()));
    }

    private static BossRules.Profile fixed(String entity) {
        var set = BossRules.get().forEntity("minecraft:" + entity).orElseThrow();
        assertEquals(1, set.variants().size());
        return set.sample(bound -> fail("A fixed profile must not use randomness"));
    }

    @Test void fixedBossesAddTheHighestGrowthAtTheirOwnStage() {
        assertSame(BossRules.get(), BossRules.get());
        assertEquals(4, BossRules.get().profiles().size());
        var elder = fixed("elder_guardian");
        assertEquals(new RealmKey("foundation_establishment", 1), elder.realm());
        assertEquals(304, elder.health());
        assertEquals(38, elder.attributeAttack());
        assertEquals(6.5, elder.bodyDefense());
        var dragon = fixed("ender_dragon");
        assertEquals(new RealmKey("golden_core", 2), dragon.realm());
        assertEquals(940, dragon.health());
        assertNull(dragon.attributeAttack());
        assertEquals(14, dragon.bodyDefense());
        var wither = fixed("wither");
        assertEquals(new RealmKey("golden_core", 1), wither.realm());
        assertEquals(1090, wither.health());
        assertNull(wither.attributeAttack());
        assertEquals(13, wither.bodyDefense());
    }

    @Test void wardenCanRollEveryCrossQualityAndStageCombination() {
        var set = BossRules.get().forEntity("minecraft:warden").orElseThrow();
        assertEquals(new RealmKey("nascent_soul", 0), set.minimumRealm());
        assertEquals(new RealmKey("nascent_soul", 2), set.maximumRealm());
        var samples = new HashSet<BossRules.Growth>();
        for (int i = 0; i < 108; i++) {
            int index = i;
            var selected = set.sample(bound -> { assertEquals(108, bound); return index; });
            assertTrue(samples.add(selected.growth()));
            assertSame(selected, set.variant(selected.growth()).orElseThrow());
        }
        for (int stage = 0; stage < 3; stage++) for (int foundation = 1; foundation <= 4; foundation++)
            for (int golden = 1; golden <= 3; golden++) for (int nascent = 1; nascent <= 3; nascent++)
                assertTrue(samples.contains(new BossRules.Growth(stage, foundation, golden, nascent)));
        var low = set.variant(new BossRules.Growth(0, 1, 1, 1)).orElseThrow();
        var high = set.variant(new BossRules.Growth(2, 4, 3, 3)).orElseThrow();
        assertEquals(1188, low.health());
        assertEquals(141, low.attributeAttack());
        assertEquals(16, low.bodyDefense());
        assertEquals(1304, high.health());
        assertEquals(177, high.attributeAttack());
        assertEquals(28, high.bodyDefense());
    }

    @Test void nativeDamageAddsGrowthOnceAfterTheFactorWithoutAnOldDamageCap() {
        var dragon = fixed("ender_dragon");
        var contact = dragon.damage().get(BossRules.Channel.DRAGON_CONTACT);
        assertEquals(54, contact.apply(5));
        assertEquals(69, contact.apply(10));
        var cloud = dragon.damage().get(BossRules.Channel.DRAGON_BREATH);
        assertEquals(51, cloud.apply(3));
        assertEquals(63, cloud.apply(6));
        assertEquals(69, fixed("wither").damage().get(BossRules.Channel.WITHER_SKULL).apply(8));
        assertEquals(47, fixed("elder_guardian").damage().get(BossRules.Channel.GUARDIAN_BEAM).apply(11));
        assertThrows(UnsupportedOperationException.class, () -> dragon.damage().clear());
    }

    @Test void damageNeverCreatesDamageFromZeroOrOverflowsItsNativeFloat() {
        for (var set : BossRules.get().profiles()) for (var p : set.variants()) for (var d : p.damage().values()) {
            assertEquals(0, d.apply(0));
            assertEquals(-0.0f, d.apply(-0.0f));
            assertEquals(Float.MAX_VALUE, d.apply(Float.MAX_VALUE));
        }
    }

    @Test void fakeAttackPathsAndInvalidCultivationIndicesAreRejected() throws IOException {
        rejects(r -> r.getAsJsonArray("profiles").get(1).getAsJsonObject().addProperty("attribute_attack", 30));
        rejects(r -> r.getAsJsonArray("profiles").get(0).getAsJsonObject().add("attribute_attack", com.google.gson.JsonNull.INSTANCE));
        rejects(r -> r.getAsJsonArray("profiles").get(0).getAsJsonObject().addProperty("health", "240"));
        rejects(r -> r.getAsJsonArray("profiles").get(0).getAsJsonObject()
                .getAsJsonObject("cultivation").getAsJsonArray("stages").add(0.5));
        rejects(r -> r.getAsJsonArray("profiles").get(3).getAsJsonObject()
                .getAsJsonObject("cultivation").getAsJsonArray("nascent_soul").add(4));
        rejects(r -> r.getAsJsonArray("profiles").get(3).getAsJsonObject()
                .getAsJsonObject("cultivation").getAsJsonArray("nascent_soul").add(1));
    }

    @Test void missingDuplicateUnexpectedAndUnsafeDamagePoliciesAreRejected() throws IOException {
        rejects(r -> r.addProperty("schema", 1));
        rejects(r -> r.addProperty("unknown", true));
        rejects(r -> r.getAsJsonArray("profiles").remove(0));
        rejects(r -> r.getAsJsonArray("profiles").add(r.getAsJsonArray("profiles").get(0).deepCopy()));
        rejects(r -> r.getAsJsonArray("profiles").get(0).getAsJsonObject().getAsJsonObject("damage").remove("guardian_beam"));
        rejects(r -> r.getAsJsonArray("profiles").get(0).getAsJsonObject().getAsJsonObject("damage")
                .getAsJsonObject("guardian_beam").addProperty("factor", 0));
        rejects(r -> r.getAsJsonArray("profiles").get(0).getAsJsonObject().getAsJsonObject("damage")
                .getAsJsonObject("guardian_beam").addProperty("maximum", 30));
        assertThrows(IllegalArgumentException.class, () -> BossRules.parse(source() + "{}"));
        assertThrows(IllegalArgumentException.class, () -> BossRules.parse(source().replace("\"schema\": 2", "\"schema\": 2, \"schema\": 2")));
    }
}
