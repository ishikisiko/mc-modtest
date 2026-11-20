package com.example.myvillage.cultivation.technique;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.HeartMethod;
import com.example.myvillage.cultivation.data.HeartMethod.Slot;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.meditation.BasicBreathingSettlement;
import com.example.myvillage.cultivation.meditation.MeditationMode;
import com.example.myvillage.cultivation.qi.QiRegeneration;
import com.example.myvillage.cultivation.qi.QiRules;
import com.example.myvillage.stats.DamageType;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HeartTechniquesTest {
    private static final Path DEFINITIONS = Path.of("src/main/resources/data/yuanxian/yuanxian/technique");
    private static final ResourceLocation BASIC = id("basic_breathing");

    @Test
    void allShippedHeartsDecodeWithBoundsAndGiveOneRouteToEachManual() throws IOException {
        var counts = new HashMap<String, Integer>();
        try (var files = Files.list(DEFINITIONS)) {
            for (Path path : files.filter(file -> file.toString().endsWith(".json")).toList()) {
                var definition = decode(path);
                if (!definition.isCore()) continue;
                assertTrue(HeartTechniques.method(definition).isPresent(), path.toString());
                var realm = definition.requirements().minimumRealm().orElseThrow();
                counts.merge(realm.getPath(), 1, Integer::sum);
                assertEquals(definition.grade() > 0, HeartManualExchange.price(definition) > 0, path.toString());
                var serialized = TechniqueDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition).getOrThrow();
                assertEquals(HeartTechniques.method(definition), HeartTechniques.method(
                        TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE, serialized).getOrThrow()));
            }
        }
        assertEquals(Map.of("mortal", 1, "qi_refining", 43, "foundation_establishment", 44,
                "golden_core", 44, "nascent_soul", 35, "spirit_transformation", 35), counts);
    }

    @Test
    void combatHeartAddsOnlyTheMatchingFixedValue() throws IOException {
        var fist = decode("taiyue_zhenjing");
        var attack = (HeartMethod.Attack) HeartTechniques.method(fist).orElseThrow();
        assertEquals(11, HeartTechniques.attackBonus(fist, attack, "fist", null, false, false));
        assertEquals(26, HeartTechniques.attackBonus(fist, attack, "fist", null, true, true));
        assertEquals(0, HeartTechniques.attackBonus(fist, attack, "sword", null, true, true));
        var spellDef = decode("baidi_shasheng_jing");
        var spell = (HeartMethod.Attack) HeartTechniques.method(spellDef).orElseThrow();
        assertEquals(26, HeartTechniques.attackBonus(spellDef, spell, "fist", DamageType.METAL, true, true));
        assertEquals(0, HeartTechniques.attackBonus(spellDef, spell, "", DamageType.FIRE, true, true));
        var common = decode("dari_zhenjing");
        var paid = (HeartMethod.Attack) HeartTechniques.method(common).orElseThrow();
        assertEquals(17, HeartTechniques.attackBonus(common, paid, "", DamageType.FIRE, true, true));
        assertEquals(0, HeartTechniques.attackBonus(common, paid, "fist", null, true, false));
        var guardDef = decode("liu_li_jin_shen_jing");
        var defense = (HeartMethod.Defense) HeartTechniques.method(guardDef).orElseThrow();
        assertEquals(60, HeartTechniques.defenseBonus(guardDef, defense, "fist", DamageType.PHYSICAL, true, false));
        assertEquals(20, HeartTechniques.defenseBonus(guardDef, defense, "fist", DamageType.WATER, false, true));
        assertEquals(0, HeartTechniques.defenseBonus(guardDef, defense, "sword", DamageType.PHYSICAL, true, false));
    }

    @Test
    void threeSlotsPersistAndForgettingClearsOnlyTheirOwnSelection() throws IOException {
        var attack = id("taiyue_zhenjing");
        var defense = id("liu_li_jin_shen_jing");
        var profile = CultivationProfile.defaultProfile()
                .learnTechnique(attack, decode("taiyue_zhenjing"))
                .learnTechnique(defense, decode("liu_li_jin_shen_jing"));
        assertTrue(profile.activeCoreTechnique().isEmpty());
        profile = profile.learnTechnique(BASIC, decode("basic_breathing"))
                .withCombatHeart(Slot.ATTACK, attack).withCombatHeart(Slot.DEFENSE, defense)
                .withStability(40).withGoldenCoreQuality(2);
        var encoded = CultivationProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile).getOrThrow();
        var reloaded = CultivationProfile.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(profile, reloaded);
        var after = reloaded.forgetTechnique(attack);
        assertTrue(after.equippedHeart(Slot.ATTACK).isEmpty());
        assertEquals(defense, after.equippedHeart(Slot.DEFENSE).orElseThrow());
        assertEquals(BASIC, after.equippedHeart(Slot.SPIRIT).orElseThrow());
    }

    @Test
    void practiceAndRecoveryAddTheirOwnValuesAfterTheBaseFormula() {
        var profile = CultivationProfile.defaultProfile().learnTechnique(BASIC);
        var remainder = new BasicBreathingSettlement.Accrual(0, new BasicBreathingSettlement.Remainders(0));
        var ordinary = BasicBreathingSettlement.plan(profile, 6000, MeditationMode.NORMAL, remainder, true, 20000, 1, 55, 165);
        var spirit = BasicBreathingSettlement.plan(profile, 6000, MeditationMode.SPIRIT, remainder, true, 20000, 1, 55, 165);
        var downgrade = BasicBreathingSettlement.plan(profile, 6000, MeditationMode.SPIRIT, remainder, false, 20000, 1, 55, 165);
        assertEquals(75, ordinary.progressApplied());
        assertEquals(265, spirit.progressApplied());
        assertTrue(spirit.consumeSpiritStones());
        assertEquals(75, downgrade.progressApplied());
        assertFalse(downgrade.consumeSpiritStones());
        var rules = new QiRules(20, 1, 3, 20);
        var timer = new QiRegeneration(rules);
        long recovered = 0;
        for (int i = 0; i < 40; i++) recovered += timer.tick(rules, 10000, 2, true, false);
        assertEquals(7, recovered);
        var terminal = profile.withStability(12955).withCultivationProgress(3);
        var consolidation = BasicBreathingSettlement.consolidate(terminal, 12960, remainder, 1.0);
        assertEquals(12960, consolidation.replacement().stability());
        assertEquals(3, consolidation.replacement().cultivationProgress());
        assertFalse(consolidation.consumeSpiritStones());
    }

    private static TechniqueDefinition decode(String path) throws IOException {
        return decode(DEFINITIONS.resolve(path + ".json"));
    }

    private static TechniqueDefinition decode(Path path) throws IOException {
        return TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(path))).getOrThrow();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("yuanxian", path); }
}
