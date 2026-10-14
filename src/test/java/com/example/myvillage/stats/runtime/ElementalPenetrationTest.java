package com.example.myvillage.stats.runtime;

import static org.junit.jupiter.api.Assertions.*;
import com.example.myvillage.stats.DamageMath;
import com.example.myvillage.stats.DamageType;
import com.example.myvillage.stats.StatsRules;
import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ElementalPenetrationTest {
    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void actualSelectorUsesSixDistinctSavedIdsAndLegacyIdOnlyForPhysical() {
        Map<DamageType, String> expected = Map.of(
                DamageType.PHYSICAL, "resistance_penetration",
                DamageType.METAL, "metal_resistance_penetration",
                DamageType.WOOD, "wood_resistance_penetration",
                DamageType.WATER, "water_resistance_penetration",
                DamageType.FIRE, "fire_resistance_penetration",
                DamageType.EARTH, "earth_resistance_penetration");
        var ids = new HashSet<ResourceLocation>();
        for (DamageType type : DamageType.values()) {
            ResourceLocation actual = id(StatsRuntime.penetration(type));
            assertEquals(ResourceLocation.fromNamespaceAndPath("myvillage", expected.get(type)), actual);
            assertTrue(ids.add(actual), "Two channels share " + actual);
            assertEquals(1, ModStatsAttributes.ALL.stream().filter(holder -> holder.getId().equals(actual)).count());
        }
        assertEquals(6, ids.size());
        assertEquals(15, ModStatsAttributes.ALL.size());
        assertFalse(ModStatsAttributes.ALL.stream().anyMatch(holder ->
                holder.getId().getPath().equals("physical_resistance_penetration")));
        assertSame(ModStatsAttributes.PHYSICAL_RESISTANCE_PENETRATION, StatsRuntime.penetration(DamageType.PHYSICAL));
    }

    @Test
    void registeredFactoryUsesZeroDefaultExistingBoundsAndOrdinaryAttributeSync() {
        for (DamageType type : DamageType.values()) {
            var attribute = ModStatsAttributes.penetrationAttribute(id(StatsRuntime.penetration(type)).getPath());
            assertEquals(0, attribute.getDefaultValue());
            assertEquals(0, attribute.getMinValue());
            assertEquals(StatsRules.bundled().limits().maxPenetration(), attribute.getMaxValue());
            assertTrue(attribute.isClientSyncable());
            assertEquals(0, attribute.sanitizeValue(-1));
            assertEquals(attribute.getMaxValue(), attribute.sanitizeValue(1));
        }
    }

    @Test
    void productionReadPathReadsExactlyOneMatchingHolderAndIgnoresOtherValues() {
        for (DamageType channel : DamageType.values()) {
            Map<ResourceLocation, Double> values = new HashMap<>();
            for (DamageType other : DamageType.values()) {
                values.put(id(StatsRuntime.penetration(other)), other == channel ? 0.1 : 0.7);
            }
            List<ResourceLocation> reads = new ArrayList<>();
            double selected = StatsRuntime.selectedPenetration(channel, holder -> {
                reads.add(id(holder));
                return values.get(id(holder));
            });
            assertEquals(List.of(id(StatsRuntime.penetration(channel))), reads);
            assertEquals(0.1, selected);
            assertEquals(5, DamageMath.mitigate(10, 0.6, selected, StatsRules.bundled()).damage(), 1e-12);
        }
    }

    @Test
    void legacyPhysicalValueDoesNotFallbackIntoFireAndFireCannotAddToPhysical() {
        Map<ResourceLocation, Double> values = new HashMap<>();
        values.put(ModStatsAttributes.PHYSICAL_RESISTANCE_PENETRATION.getId(), 0.8);
        var reader = (java.util.function.ToDoubleFunction<Holder<Attribute>>) holder -> values.getOrDefault(id(holder), 0D);
        assertEquals(0.8, StatsRuntime.selectedPenetration(DamageType.PHYSICAL, reader));
        assertEquals(0, StatsRuntime.selectedPenetration(DamageType.FIRE, reader));
        assertEquals(5, DamageMath.mitigate(10, 0.5,
                StatsRuntime.selectedPenetration(DamageType.FIRE, reader), StatsRules.bundled()).damage());
        values.put(ModStatsAttributes.FIRE_RESISTANCE_PENETRATION.getId(), 0.2);
        assertEquals(0.8, StatsRuntime.selectedPenetration(DamageType.PHYSICAL, reader));
        assertEquals(0.2, StatsRuntime.selectedPenetration(DamageType.FIRE, reader));
        assertEquals(7, DamageMath.mitigate(10, 0.5,
                StatsRuntime.selectedPenetration(DamageType.FIRE, reader), StatsRules.bundled()).damage(), 1e-12);
    }

    @Test
    void selectedChannelKeepsTheExistingPercentagePointRuleAndZeroFloor() {
        for (DamageType type : DamageType.values()) {
            double selected = StatsRuntime.selectedPenetration(type, holder -> 0.6);
            var result = DamageMath.mitigate(20, 0.4, selected, StatsRules.bundled());
            assertEquals(0.6, result.penetration());
            assertEquals(0, result.effectiveResistance());
            assertEquals(20, result.damage());
        }
    }

    @Test
    void vanillaAttributeNbtPreservesEachIndependentModifierWithoutCopyingLegacyData() {
        var registry = new MappedRegistry<Attribute>(ResourceKey.createRegistryKey(
                ResourceLocation.parse("stats_test:penetration_attributes")), Lifecycle.stable());
        Map<ResourceLocation, Holder<Attribute>> holders = new HashMap<>();
        for (DamageType type : DamageType.values()) {
            ResourceLocation id = id(StatsRuntime.penetration(type));
            holders.put(id, Registry.registerForHolder(registry, id,
                    ModStatsAttributes.penetrationAttribute(id.getPath())));
        }
        registry.freeze();
        for (DamageType changed : DamageType.values()) {
            Map<ResourceLocation, AttributeInstance> restored = new HashMap<>();
            for (DamageType type : DamageType.values()) {
                ResourceLocation id = id(StatsRuntime.penetration(type));
                var before = new AttributeInstance(holders.get(id), attribute -> {});
                if (type == changed) {
                    before.addPermanentModifier(new AttributeModifier(ResourceLocation.parse("stats_test:equipment"),
                            0.3, AttributeModifier.Operation.ADD_VALUE));
                }
                var after = new AttributeInstance(holders.get(id), attribute -> {});
                after.load(before.save());
                restored.put(id, after);
            }
            for (DamageType type : DamageType.values()) {
                assertEquals(type == changed ? 0.3 : 0,
                        StatsRuntime.selectedPenetration(type, holder -> restored.get(id(holder)).getValue()), 1e-12);
            }
        }
    }

    private static ResourceLocation id(Holder<Attribute> holder) {
        return holder.unwrapKey().orElseThrow().location();
    }
}
