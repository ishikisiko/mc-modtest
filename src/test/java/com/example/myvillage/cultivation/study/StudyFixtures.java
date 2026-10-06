package com.example.myvillage.cultivation.study;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueDefinition.TechniqueStudy;
import com.example.myvillage.cultivation.data.TechniqueLineage;
import com.example.myvillage.cultivation.data.TechniqueRequirements;
import com.example.myvillage.sim.SimData;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.data.Rules;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A small world for study tests: two realms, a 黄阶 core and a 玄阶 active technique on one chain. */
final class StudyFixtures {
    static final ResourceLocation METAL = ModCultivationRegistries.METAL_ELEMENT_ID;
    static final ResourceLocation FIRE = ModCultivationRegistries.FIRE_ELEMENT_ID;
    static final ResourceLocation BREATHING = ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID;
    static final ResourceLocation HUANG = ResourceLocation.fromNamespaceAndPath("myvillage", "gengjin_yinqi_fa");
    static final ResourceLocation XUAN = ResourceLocation.fromNamespaceAndPath("myvillage", "gengjin_jianjue");
    static final ResourceLocation UNKNOWN = ResourceLocation.fromNamespaceAndPath("myvillage", "no_such_technique");

    static final TechniqueDefinition HUANG_DEFINITION = new TechniqueDefinition(
            "cultivation.technique.myvillage.gengjin_yinqi_fa",
            TechniqueCategory.CORE,
            1,
            List.of(METAL),
            new TechniqueRequirements(
                    Optional.of(ModCultivationRegistries.QI_REFINING_REALM_ID),
                    Optional.of(ModCultivationRegistries.QI_REFINING_1_STAGE_ID),
                    Map.of()),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            new TechniqueStudy(4_000, 0, 0));
    static final TechniqueDefinition XUAN_DEFINITION = new TechniqueDefinition(
            "cultivation.technique.myvillage.gengjin_jianjue",
            TechniqueCategory.ACTIVE,
            2,
            List.of(METAL),
            new TechniqueRequirements(
                    Optional.of(ModCultivationRegistries.QI_REFINING_REALM_ID),
                    Optional.of(ModCultivationRegistries.QI_REFINING_1_STAGE_ID),
                    Map.of(METAL, 1_500)),
            Optional.empty(),
            Optional.of(new TechniqueLineage(HUANG)),
            Optional.empty(),
            new TechniqueStudy(12_000, 1, 50));
    static final TechniqueDefinition BREATHING_DEFINITION = new TechniqueDefinition(
            "cultivation.technique.myvillage.basic_breathing",
            TechniqueCategory.CORE,
            0,
            List.of(),
            TechniqueRequirements.none());

    static final Map<ResourceLocation, TechniqueDefinition> TECHNIQUES = Map.of(
            BREATHING, BREATHING_DEFINITION,
            HUANG, HUANG_DEFINITION,
            XUAN, XUAN_DEFINITION);
    static final Map<ResourceLocation, RealmDefinition> REALMS = realms();
    static final Rules RULES = shippedRules();

    private StudyFixtures() {
    }

    /** Qi Refining I, metal 6000 / fire 4000, affinity 10, stability {@code stability}, Basic Breathing running. */
    static CultivationProfile cultivator(int stability) {
        return CultivationProfile.defaultProfile()
                .withRealmAndStage(ModCultivationRegistries.QI_REFINING_REALM_ID,
                        ModCultivationRegistries.QI_REFINING_1_STAGE_ID)
                .withSpiritualRoot(Optional.of(new SpiritualRoot(Map.of(METAL, 6_000, FIRE, 4_000))))
                .learnTechnique(BREATHING, TechniqueCategory.CORE)
                .withStability(stability);
    }

    /** {@link #cultivator} who has also learned the 黄阶 chain head. */
    static CultivationProfile chainCultivator(int stability) {
        return cultivator(stability).learnTechnique(HUANG, TechniqueCategory.CORE);
    }

    private static Map<ResourceLocation, RealmDefinition> realms() {
        RealmDefinition mortal = new RealmDefinition(
                "realm.mortal", 0, 80,
                List.of(
                        new RealmStageDefinition(ModCultivationRegistries.MORTAL_UNAWAKENED_STAGE_ID, "stage.unawakened", 0),
                        new RealmStageDefinition(ModCultivationRegistries.MORTAL_QI_SENSED_STAGE_ID, "stage.sensed", 1)),
                Optional.of(ModCultivationRegistries.QI_REFINING_REALM_ID));
        RealmDefinition qiRefining = new RealmDefinition(
                "realm.qi_refining", 1, 120,
                List.of(new RealmStageDefinition(ModCultivationRegistries.QI_REFINING_1_STAGE_ID, "stage.qi_1", 0)),
                Optional.empty());
        return Map.of(
                ModCultivationRegistries.MORTAL_REALM_ID, mortal,
                ModCultivationRegistries.QI_REFINING_REALM_ID, qiRefining);
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
