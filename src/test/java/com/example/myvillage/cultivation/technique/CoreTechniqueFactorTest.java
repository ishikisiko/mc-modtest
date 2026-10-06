package com.example.myvillage.cultivation.technique;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueRequirements;
import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.data.Rules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CoreTechniqueFactorTest {
    private static final ResourceLocation METAL = ModCultivationRegistries.METAL_ELEMENT_ID;
    private static final ResourceLocation FIRE = ModCultivationRegistries.FIRE_ELEMENT_ID;
    private static final ResourceLocation XUAN_METAL = ResourceLocation.fromNamespaceAndPath("myvillage", "xuan_metal");
    private static final Rules RULES = shippedRules();

    @Test
    void shippedRulesCarryTheSwitchLossAndTheFactorsThisTestAssumes() {
        assertEquals(0.3, RULES.techniques().switchProgressLoss());
        assertEquals(1.3, RULES.techniques().grades().get("xuan").cultivation());
        assertEquals(0.15, RULES.techniques().elementMatchBonus());
        assertEquals(1500, RULES.roots().elementThresholdBp());
    }

    @Test
    void gradeZeroIsExactlyOneWithoutElementBonus() {
        TechniqueDefinition breathing = technique(0, List.of(METAL));
        assertEquals(10_000, factor(breathing, root(10_000, 0)));
        assertEquals(10_000, factor(breathing, Optional.empty()));
    }

    @Test
    void gradeTwoUsesTheXuanMultiplierAndTheElementBonusOnlyAtTheThreshold() {
        TechniqueDefinition xuan = technique(2, List.of(METAL));
        assertEquals(13_000, factor(xuan, root(1_499, 8_501)));
        assertEquals(14_950, factor(xuan, root(1_500, 8_500)));
        assertEquals(13_000, factor(xuan, Optional.empty()));
        assertEquals(14_950, factor(technique(2, List.of(FIRE, METAL)), root(0, 10_000)));
        assertEquals(13_000, factor(technique(2, List.of()), root(5_000, 5_000)));
        assertEquals(23_000, factor(technique(4, List.of()), Optional.empty()));
    }

    @Test
    void profileFactorIsOneWithoutRunningTechniqueDataOrDefinition() {
        Map<ResourceLocation, TechniqueDefinition> techniques = Map.of(
                ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID, technique(0, List.of()),
                XUAN_METAL, technique(2, List.of(METAL)));
        CultivationProfile none = CultivationProfile.defaultProfile().withSpiritualRoot(root(5_000, 5_000));
        CultivationProfile breathing = none.learnTechnique(
                ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID, TechniqueCategory.CORE);
        CultivationProfile xuan = breathing.learnTechnique(XUAN_METAL).withActiveCoreTechnique(XUAN_METAL);

        assertEquals(10_000, CoreTechniqueFactor.progressBasisPoints(none, techniques::get, Optional.of(RULES)));
        assertEquals(10_000, CoreTechniqueFactor.progressBasisPoints(breathing, techniques::get, Optional.of(RULES)));
        assertEquals(14_950, CoreTechniqueFactor.progressBasisPoints(xuan, techniques::get, Optional.of(RULES)));
        assertEquals(10_000, CoreTechniqueFactor.progressBasisPoints(xuan, techniques::get, Optional.empty()));
        assertEquals(10_000, CoreTechniqueFactor.progressBasisPoints(xuan, id -> null, Optional.of(RULES)));
    }

    @Test
    void applyRoundsDownAndLeavesUnitFactorUntouched() {
        assertEquals(10, CoreTechniqueFactor.apply(10, 10_000));
        assertEquals(50, CoreTechniqueFactor.apply(50, 10_000));
        assertEquals(14, CoreTechniqueFactor.apply(10, 14_950));
        assertEquals(74, CoreTechniqueFactor.apply(50, 14_950));
        assertEquals(0, CoreTechniqueFactor.apply(0, 14_950));
        assertTrue(CoreTechniqueFactor.elementMatch(technique(1, List.of(METAL)), root(1_500, 8_500), RULES.roots()));
        assertFalse(CoreTechniqueFactor.elementMatch(technique(1, List.of(METAL)), Optional.empty(), RULES.roots()));
    }

    private static int factor(TechniqueDefinition technique, Optional<SpiritualRoot> root) {
        return CoreTechniqueFactor.progressBasisPoints(technique, root, RULES.techniques(), RULES.roots());
    }

    private static Optional<SpiritualRoot> root(int metal, int fire) {
        return Optional.of(root(Map.of(METAL, metal, FIRE, fire)));
    }

    private static SpiritualRoot root(Map<ResourceLocation, Integer> affinities) {
        return new SpiritualRoot(affinities);
    }

    private static TechniqueDefinition technique(int grade, List<ResourceLocation> elements) {
        return new TechniqueDefinition(
                "cultivation.technique.test", TechniqueCategory.CORE, grade, elements, TechniqueRequirements.none());
    }

    private static Rules shippedRules() {
        Path resources = Path.of("src/main/resources");
        SimData data = WorldSim.loadData(path -> {
            Path file = resources.resolve(path);
            return Files.isRegularFile(file) ? Files.newInputStream(file) : null;
        });
        return data.rules();
    }
}
