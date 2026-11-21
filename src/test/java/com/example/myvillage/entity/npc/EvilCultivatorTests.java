package com.example.myvillage.entity.npc;

import com.example.myvillage.client.entity.beast.BeastModelFile;
import com.example.myvillage.client.entity.beast.BeastSurfaceMesh;
import com.example.myvillage.cultivation.data.SchoolDefinition;
import com.example.myvillage.cultivation.data.SchoolKind;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueRequirements;
import com.example.myvillage.stats.CultivationStatsRules;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvilCultivatorTests {
    private static final SchoolDefinition SWORD = weapon("sword");
    private static final SchoolDefinition SPEAR = weapon("spear");

    @Test
    void ranksAndGradesStayWithinTheirOwnBands() {
        var rules = EvilCultivatorRules.get();
        assertEquals(0.1, CultivationStatsRules.bundled().npcProdigyChance());
        assertEquals(0.5, rules.manualPreference());
        assertTrue(rules.prodigy().manualChance() > rules.ordinary().manualChance());
        assertTrue(rules.prodigy().learnedMaxGrade() > rules.ordinary().learnedMaxGrade());
        var entries = new ArrayList<EvilCultivatorTechniques.Entry>();
        for (int grade = 1; grade <= 4; grade++) entries.add(entry("sword_" + grade, grade, SWORD));
        var known = EvilCultivatorTechniques.AttackStyle.SWORD;
        var random = RandomSource.create(13);
        for (int i = 0; i < 1000; i++) {
            int ordinary = EvilCultivatorTechniques.pick(entries, rules.ordinary(), known, 0.5, random).orElseThrow().grade();
            int gifted = EvilCultivatorTechniques.pick(entries, rules.prodigy(), known, 0.5, random).orElseThrow().grade();
            assertTrue(ordinary >= 1 && ordinary <= 3);
            assertTrue(gifted >= 2 && gifted <= 4);
        }
    }

    @Test
    void swordAndCommonHeartsShareOneHalfEvenWhenOtherPoolIsLarger() {
        var rules = EvilCultivatorRules.get();
        var known = EvilCultivatorTechniques.AttackStyle.SWORD;
        var universal = entry("common_heart", 2, null);
        assertTrue(universal.commonHeart());
        var entries = new ArrayList<>(List.of(entry("sword", 1, SWORD), universal));
        for (int i = 0; i < 20; i++) entries.add(entry("spear_" + i, 3, SPEAR));
        int matching = 0;
        var random = RandomSource.create(1121);
        for (int i = 0; i < 5000; i++) {
            var picked = EvilCultivatorTechniques.pick(entries, rules.ordinary(), known, rules.manualPreference(), random);
            if (picked.orElseThrow().matches(known)) matching++;
        }
        assertTrue(matching > 2400 && matching < 2600, "matching=" + matching);
        // An empty preferred bucket still yields an allowed manual.
        var onlyOther = List.of(entry("spear", 3, SPEAR), entry("heaven_spear", 4, SPEAR));
        assertEquals(onlyOther.getFirst(), EvilCultivatorTechniques.pick(onlyOther, rules.ordinary(), known, 1, random).orElseThrow());
    }

    @Test
    void approvedMaskParsesAsAnAdditiveHeadSurface() throws Exception {
        String modelPath = "assets/yuanxian/npc/evil_cultivator_model.json";
        try (var modelReader = resource(modelPath)) {
            var model = BeastModelFile.parse(modelPath, ResourceLocation.parse("yuanxian:evil_cultivator"), modelReader);
            assertEquals(ResourceLocation.parse("yuanxian:npc/evil_cultivator_surface.json"), model.surfaceMesh());
            String surface = "assets/yuanxian/npc/evil_cultivator_surface.json";
            try (var maskReader = resource(surface)) {
                var mesh = BeastSurfaceMesh.parse(surface, model, maskReader);
                assertEquals(List.of("head"), mesh.parts().stream().map(BeastSurfaceMesh.Part::bone).toList());
                assertFalse(mesh.parts().getFirst().triangles().isEmpty());
            }
        }
    }

    private static InputStreamReader resource(String path) {
        return new InputStreamReader(Objects.requireNonNull(EvilCultivatorTests.class.getClassLoader()
                .getResourceAsStream(path), path), StandardCharsets.UTF_8);
    }

    private static EvilCultivatorTechniques.Entry entry(String id, int grade, SchoolDefinition school) {
        var def = new TechniqueDefinition("test." + id, school == null ? TechniqueCategory.CORE : TechniqueCategory.ACTIVE,
                grade, List.of(), TechniqueRequirements.none(),
                school == null ? Optional.empty() : Optional.of(ResourceLocation.parse("yuanxian:" + school.weaponFamily().orElseThrow())),
                Optional.empty(), Optional.empty());
        return new EvilCultivatorTechniques.Entry(ResourceLocation.parse("yuanxian:" + id), def, school);
    }

    private static SchoolDefinition weapon(String family) {
        return new SchoolDefinition("test." + family, SchoolKind.WEAPON, Optional.of(family), Optional.empty(), List.of());
    }
}
