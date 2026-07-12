package com.example.myvillage.cultivation.data;

import com.example.myvillage.MyVillageMod;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ModCultivationRegistries {
    public static final ResourceKey<Registry<RealmDefinition>> REALMS = registryKey("realm");
    public static final ResourceKey<Registry<SpiritualElementDefinition>> SPIRITUAL_ELEMENTS =
            registryKey("spiritual_element");
    public static final ResourceKey<Registry<TechniqueDefinition>> TECHNIQUES = registryKey("technique");

    public static final ResourceKey<Registry<RealmDefinition>> REALM_REGISTRY_KEY = REALMS;
    public static final ResourceKey<Registry<SpiritualElementDefinition>> SPIRITUAL_ELEMENT_REGISTRY_KEY =
            SPIRITUAL_ELEMENTS;
    public static final ResourceKey<Registry<TechniqueDefinition>> TECHNIQUE_REGISTRY_KEY = TECHNIQUES;

    public static final ResourceLocation MORTAL_REALM_ID = id("mortal");
    public static final ResourceLocation QI_REFINING_REALM_ID = id("qi_refining");
    public static final ResourceLocation FOUNDATION_ESTABLISHMENT_REALM_ID = id("foundation_establishment");

    public static final ResourceLocation MORTAL_UNAWAKENED_STAGE_ID = id("mortal_unawakened");
    public static final ResourceLocation MORTAL_QI_SENSED_STAGE_ID = id("mortal_qi_sensed");
    public static final ResourceLocation QI_REFINING_1_STAGE_ID = id("qi_refining_1");
    public static final ResourceLocation QI_REFINING_2_STAGE_ID = id("qi_refining_2");
    public static final ResourceLocation QI_REFINING_3_STAGE_ID = id("qi_refining_3");
    public static final ResourceLocation QI_REFINING_4_STAGE_ID = id("qi_refining_4");
    public static final ResourceLocation QI_REFINING_5_STAGE_ID = id("qi_refining_5");
    public static final ResourceLocation QI_REFINING_6_STAGE_ID = id("qi_refining_6");
    public static final ResourceLocation QI_REFINING_7_STAGE_ID = id("qi_refining_7");
    public static final ResourceLocation QI_REFINING_8_STAGE_ID = id("qi_refining_8");
    public static final ResourceLocation QI_REFINING_9_STAGE_ID = id("qi_refining_9");
    public static final ResourceLocation FOUNDATION_EARLY_STAGE_ID = id("foundation_early");

    public static final ResourceLocation METAL_ELEMENT_ID = id("metal");
    public static final ResourceLocation WOOD_ELEMENT_ID = id("wood");
    public static final ResourceLocation WATER_ELEMENT_ID = id("water");
    public static final ResourceLocation FIRE_ELEMENT_ID = id("fire");
    public static final ResourceLocation EARTH_ELEMENT_ID = id("earth");

    public static final ResourceLocation BASIC_BREATHING_TECHNIQUE_ID = id("basic_breathing");

    public static final List<ResourceLocation> QI_REFINING_STAGE_IDS = List.of(
            QI_REFINING_1_STAGE_ID,
            QI_REFINING_2_STAGE_ID,
            QI_REFINING_3_STAGE_ID,
            QI_REFINING_4_STAGE_ID,
            QI_REFINING_5_STAGE_ID,
            QI_REFINING_6_STAGE_ID,
            QI_REFINING_7_STAGE_ID,
            QI_REFINING_8_STAGE_ID,
            QI_REFINING_9_STAGE_ID);

    public static final Set<ResourceLocation> REQUIRED_REALM_IDS = orderedSet(List.of(
            MORTAL_REALM_ID,
            QI_REFINING_REALM_ID,
            FOUNDATION_ESTABLISHMENT_REALM_ID));
    public static final Set<ResourceLocation> REQUIRED_ELEMENT_IDS = orderedSet(List.of(
            METAL_ELEMENT_ID,
            WOOD_ELEMENT_ID,
            WATER_ELEMENT_ID,
            FIRE_ELEMENT_ID,
            EARTH_ELEMENT_ID));
    public static final Set<ResourceLocation> REQUIRED_TECHNIQUE_IDS =
            orderedSet(List.of(BASIC_BREATHING_TECHNIQUE_ID));
    public static final Map<ResourceLocation, ResourceLocation> REQUIRED_STAGE_OWNERS =
            requiredStageOwners();

    private ModCultivationRegistries() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModCultivationRegistries::registerDatapackRegistries);
    }

    private static void registerDatapackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(REALMS, RealmDefinition.CODEC, RealmDefinition.CODEC);
        event.dataPackRegistry(
                SPIRITUAL_ELEMENTS,
                SpiritualElementDefinition.CODEC,
                SpiritualElementDefinition.CODEC);
        event.dataPackRegistry(TECHNIQUES, TechniqueDefinition.CODEC, TechniqueDefinition.CODEC);
    }

    public static RegistrySummary validateRequiredEntries(RegistryAccess registryAccess) {
        Objects.requireNonNull(registryAccess, "registryAccess");
        Registry<RealmDefinition> realms = registryAccess.registryOrThrow(REALMS);
        Registry<SpiritualElementDefinition> elements = registryAccess.registryOrThrow(SPIRITUAL_ELEMENTS);
        Registry<TechniqueDefinition> techniques = registryAccess.registryOrThrow(TECHNIQUES);
        List<String> errors = new ArrayList<>();

        requireIds("realm", realms, REQUIRED_REALM_IDS, errors);
        requireIds("spiritual element", elements, REQUIRED_ELEMENT_IDS, errors);
        requireIds("technique", techniques, REQUIRED_TECHNIQUE_IDS, errors);
        validateRealmDefinitions(realms, errors);
        validateTechniqueDefinitions(realms, elements, techniques, errors);

        for (Map.Entry<ResourceLocation, ResourceLocation> entry : REQUIRED_STAGE_OWNERS.entrySet()) {
            RealmDefinition realm = realms.get(entry.getValue());
            if (realm != null && !realm.hasStage(entry.getKey())) {
                errors.add("required stage " + entry.getKey() + " is not owned by realm " + entry.getValue());
            }
        }

        validateNextRealm(realms, MORTAL_REALM_ID, QI_REFINING_REALM_ID, errors);
        validateNextRealm(realms, QI_REFINING_REALM_ID, FOUNDATION_ESTABLISHMENT_REALM_ID, errors);

        if (!errors.isEmpty()) {
            errors.sort(String::compareTo);
            throw new IllegalStateException("Invalid cultivation registry definitions: " + String.join("; ", errors));
        }
        return summary(registryAccess);
    }

    public static RegistrySummary summary(RegistryAccess registryAccess) {
        Objects.requireNonNull(registryAccess, "registryAccess");
        Registry<RealmDefinition> realms = registryAccess.registryOrThrow(REALMS);
        Registry<SpiritualElementDefinition> elements = registryAccess.registryOrThrow(SPIRITUAL_ELEMENTS);
        Registry<TechniqueDefinition> techniques = registryAccess.registryOrThrow(TECHNIQUES);
        int stageCount = realms.stream().mapToInt(realm -> realm.stages().size()).sum();
        return new RegistrySummary(realms.size(), stageCount, elements.size(), techniques.size());
    }

    public static String summaryText(RegistryAccess registryAccess) {
        return summary(registryAccess).toString();
    }

    private static void validateRealmDefinitions(
            Registry<RealmDefinition> realms,
            List<String> errors) {
        Map<ResourceLocation, ResourceLocation> stageOwners = new HashMap<>();
        for (ResourceLocation realmId : sortedIds(realms)) {
            RealmDefinition realm = realms.get(realmId);
            for (RealmStageDefinition stage : realm.stages()) {
                ResourceLocation previousOwner = stageOwners.putIfAbsent(stage.id(), realmId);
                if (previousOwner != null) {
                    errors.add("stage " + stage.id() + " is owned by both " + previousOwner + " and " + realmId);
                }
            }
            realm.nextRealm().ifPresent(nextRealm -> {
                if (!realms.containsKey(nextRealm)) {
                    errors.add("realm " + realmId + " references missing next_realm " + nextRealm);
                }
            });
        }
    }

    private static void validateTechniqueDefinitions(
            Registry<RealmDefinition> realms,
            Registry<SpiritualElementDefinition> elements,
            Registry<TechniqueDefinition> techniques,
            List<String> errors) {
        for (ResourceLocation techniqueId : sortedIds(techniques)) {
            TechniqueDefinition technique = techniques.get(techniqueId);
            for (ResourceLocation elementId : technique.elements()) {
                if (!elements.containsKey(elementId)) {
                    errors.add("technique " + techniqueId + " references missing element " + elementId);
                }
            }
            for (ResourceLocation elementId : technique.requirements().minimumElementAffinity().keySet()) {
                if (!elements.containsKey(elementId)) {
                    errors.add("technique " + techniqueId
                            + " references missing minimum-affinity element " + elementId);
                }
            }

            TechniqueRequirements requirements = technique.requirements();
            requirements.minimumRealm().ifPresent(minimumRealm -> {
                RealmDefinition realm = realms.get(minimumRealm);
                if (realm == null) {
                    errors.add("technique " + techniqueId + " references missing minimum_realm " + minimumRealm);
                    return;
                }
                requirements.minimumStage().ifPresent(minimumStage -> {
                    if (!realm.hasStage(minimumStage)) {
                        errors.add("technique " + techniqueId + " minimum_stage " + minimumStage
                                + " does not belong to minimum_realm " + minimumRealm);
                    }
                });
            });
        }
    }

    private static <T> void requireIds(
            String definitionType,
            Registry<T> registry,
            Set<ResourceLocation> requiredIds,
            List<String> errors) {
        for (ResourceLocation id : requiredIds) {
            if (!registry.containsKey(id)) {
                errors.add("missing required " + definitionType + " " + id);
            }
        }
    }

    private static void validateNextRealm(
            Registry<RealmDefinition> realms,
            ResourceLocation realmId,
            ResourceLocation expectedNextRealm,
            List<String> errors) {
        RealmDefinition realm = realms.get(realmId);
        if (realm != null && !realm.nextRealm().equals(Optional.of(expectedNextRealm))) {
            errors.add("realm " + realmId + " must link next_realm " + expectedNextRealm);
        }
    }

    private static List<ResourceLocation> sortedIds(Registry<?> registry) {
        return registry.keySet().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    private static Map<ResourceLocation, ResourceLocation> requiredStageOwners() {
        LinkedHashMap<ResourceLocation, ResourceLocation> result = new LinkedHashMap<>();
        result.put(MORTAL_UNAWAKENED_STAGE_ID, MORTAL_REALM_ID);
        result.put(MORTAL_QI_SENSED_STAGE_ID, MORTAL_REALM_ID);
        for (ResourceLocation stageId : QI_REFINING_STAGE_IDS) {
            result.put(stageId, QI_REFINING_REALM_ID);
        }
        result.put(FOUNDATION_EARLY_STAGE_ID, FOUNDATION_ESTABLISHMENT_REALM_ID);
        return Collections.unmodifiableMap(result);
    }

    private static Set<ResourceLocation> orderedSet(List<ResourceLocation> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, path);
    }

    private static <T> ResourceKey<Registry<T>> registryKey(String path) {
        return ResourceKey.createRegistryKey(id(path));
    }

    public record RegistrySummary(int realmCount, int stageCount, int elementCount, int techniqueCount) {
        @Override
        public String toString() {
            return "realms=" + realmCount + ", stages=" + stageCount
                    + ", elements=" + elementCount + ", techniques=" + techniqueCount;
        }
    }
}
