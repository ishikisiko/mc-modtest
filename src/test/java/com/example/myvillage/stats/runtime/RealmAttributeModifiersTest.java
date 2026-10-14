package com.example.myvillage.stats.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.mojang.serialization.Lifecycle;
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
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class RealmAttributeModifiersTest {
    private static final ResourceLocation REALM = id("realm");

    @BeforeAll
    static void bootstrap() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void repeatedReplacementDoesNotStackAndKeepsOtherContributions() {
        AttributeInstance attribute = instance(holder());
        attribute.addPermanentModifier(new AttributeModifier(id("other"), 5, AttributeModifier.Operation.ADD_VALUE));
        RealmAttributeModifiers.replace(attribute, REALM, 3);
        assertEquals(75, attribute.getValue());
        RealmAttributeModifiers.replace(attribute, REALM, 3);
        assertEquals(75, attribute.getValue());
        assertEquals(2, attribute.getModifiers().size());
        RealmAttributeModifiers.replace(attribute, REALM, 1);
        assertEquals(25, attribute.getValue());
        assertFalse(attribute.hasModifier(REALM));
    }

    @Test
    void savedRealmMaximumSurvivesAttributeNbtRoundTrip() {
        Holder<Attribute> holder = holder();
        AttributeInstance attribute = instance(holder);
        RealmAttributeModifiers.replace(attribute, REALM, 3);
        var saved = attribute.save();
        assertEquals(1, saved.getList("modifiers", 10).size());
        AttributeInstance restored = instance(holder);
        restored.load(saved);
        assertEquals(60, restored.getValue());
        RealmAttributeModifiers.replace(restored, REALM, 3);
        assertEquals(60, restored.getValue());
        assertEquals(1, restored.getModifiers().size());
    }

    private static Holder<Attribute> holder() {
        var registry = new MappedRegistry<Attribute>(ResourceKey.createRegistryKey(id("attributes")), Lifecycle.stable());
        var holder = Registry.registerForHolder(registry, id("health"),
                new RangedAttribute("test.health", 20, 1, 1024));
        registry.freeze();
        return holder;
    }

    private static AttributeInstance instance(Holder<Attribute> holder) {
        return new AttributeInstance(holder, attribute -> {});
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("stats_test", path);
    }
}
