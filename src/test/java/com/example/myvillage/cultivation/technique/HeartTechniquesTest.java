package com.example.myvillage.cultivation.technique;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.HeartMethod;
import com.example.myvillage.cultivation.data.HeartMethod.Slot;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueEffects;
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
import java.util.List;
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
                int price = definition.effects().flatMap(TechniqueEffects::core)
                        .map(TechniqueEffects.Core::manualPrice).orElse(0);
                assertEquals(definition.grade() > 0, price > 0, path.toString());
                var serialized = TechniqueDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition).getOrThrow();
                assertEquals(HeartTechniques.method(definition), HeartTechniques.method(
                        TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE, serialized).getOrThrow()));
            }
        }
        assertEquals(Map.of("mortal", 1, "qi_refining", 51, "foundation_establishment", 45,
                "golden_core", 45, "nascent_soul", 35, "spirit_transformation", 35), counts);
    }

    @Test
    void repurposedManualsAllUseTheExistingDefenseSlot() throws IOException {
        for (String name : List.of("duanti_jue", "tiegu_gong", "jingang_gong", "panshi_gong", "yanjia_jue",
                "jinlin_cuiti_jue", "jingang_huti_gong", "shijia_xuangong", "gengjin_bumie_ti", "wanjie_jinshen_jue")) {
            var definition = decode(name);
            assertTrue(definition.isCore(), name);
            assertEquals(Slot.DEFENSE, HeartTechniques.slot(definition), name);
            if (definition.grade() == 2) {
                assertEquals(id("qi_refining_5"), definition.requirements().minimumStage().orElseThrow(), name);
            }
        }
        assertEquals(4, defense("duanti_jue").maxHealth());
        assertEquals(0.25, defense("panshi_gong").attributes().knockbackResistance());
        assertEquals(0.15, defense("jingang_huti_gong").attributes().physicalResistance());
        assertEquals(3, defense("shijia_xuangong").attributes().armor());
        assertEquals(1, defense("shijia_xuangong").attributes().armorToughness());
    }

    @Test
    void shieldRequiresASurvivingHitThatCrossesTheConfiguredThreshold() throws IOException {
        var definition = decode("wanjie_jinshen_jue");
        assertEquals(id("golden_core"), definition.requirements().minimumRealm().orElseThrow());
        assertEquals(id("gengjin_bumie_ti"), definition.previous().orElseThrow());
        var shield = defense("wanjie_jinshen_jue").shield().orElseThrow();
        assertEquals(10, shield.amount());
        assertEquals(120, shield.durationTicks());
        assertEquals(1200, shield.cooldownTicks());
        assertTrue(shield.crossesThreshold(31, 30, 100));
        assertFalse(shield.crossesThreshold(31, 30.01, 100));
        assertFalse(shield.crossesThreshold(30, 20, 100));
        assertFalse(shield.crossesThreshold(31, 0, 100));
    }

    @Test
    void combatHeartKeepsBaseAndOnlyAddsExtraForMatchingEvents() throws IOException {
        var sword = decode("zhan_yun_jian_zhang");
        var attack = (HeartMethod.Attack) HeartTechniques.method(sword).orElseThrow();
        assertEquals(44, attack.attack());
        assertEquals(0, attack.flatPenetration());
        var spear = decode("bei_chen_guan_yue_jing");
        var penetration = (HeartMethod.Attack) HeartTechniques.method(spear).orElseThrow();
        assertEquals(22, penetration.attack());
        assertEquals(32, penetration.flatPenetration());
        assertEquals(30, HeartTechniques.attackExtra(spear, penetration, "spear", null, true, true));
        assertEquals(0, HeartTechniques.attackExtra(spear, penetration, "sword", null, true, true));
        assertEquals(0, HeartTechniques.attackExtra(sword, attack, "sword", null, false, false));
        assertEquals(30, HeartTechniques.attackExtra(sword, attack, "sword", null, true, true));
        assertEquals(0, HeartTechniques.attackExtra(sword, attack, "fist", null, true, true));
        var spellDef = decode("baidi_shasheng_jing");
        var spell = (HeartMethod.Attack) HeartTechniques.method(spellDef).orElseThrow();
        assertEquals(10, spell.attack());
        assertEquals(15, HeartTechniques.attackExtra(spellDef, spell, "", DamageType.METAL, true, true));
        assertEquals(0, HeartTechniques.attackExtra(spellDef, spell, "", DamageType.FIRE, true, true));
        var common = decode("dari_zhenjing");
        var paid = (HeartMethod.Attack) HeartTechniques.method(common).orElseThrow();
        assertEquals(17, HeartTechniques.attackExtra(common, paid, "", DamageType.FIRE, true, true));
        assertEquals(0, HeartTechniques.attackExtra(common, paid, "fist", null, true, false));

        var guardDef = decode("xuan_qiong_shou_jian_jing");
        var defense = (HeartMethod.Defense) HeartTechniques.method(guardDef).orElseThrow();
        assertEquals(90, defense.maxHealth());
        assertEquals(18, defense.defense());
        assertEquals(20, HeartTechniques.defenseExtra(guardDef, defense, "sword", DamageType.WATER, false, true));
        assertEquals(0, HeartTechniques.defenseExtra(guardDef, defense, "sword", DamageType.PHYSICAL, true, false));
        assertEquals(0, HeartTechniques.defenseExtra(guardDef, defense, "fist", DamageType.WATER, false, true));

        var spirit = (HeartMethod.Spirit) HeartTechniques.method(decode("basic_breathing")).orElseThrow();
        assertEquals(2, spirit.cultivation());
        assertEquals(3, spirit.stoneExtra());
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

    private static HeartMethod.Defense defense(String path) throws IOException {
        return (HeartMethod.Defense) HeartTechniques.method(decode(path)).orElseThrow();
    }

    private static TechniqueDefinition decode(Path path) throws IOException {
        return TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(path))).getOrThrow();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("yuanxian", path); }
}
