package com.example.myvillage.combat.foundation;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.Lifecycle;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import org.junit.jupiter.api.Test;

class ShieldRuntimeTest {
    @Test void voidAndAdministrativeDamageBypassEvenWithoutVanillaTags() {
        var registry = new MappedRegistry<DamageType>(Registries.DAMAGE_TYPE, Lifecycle.stable());
        var ordinary = register(registry, DamageTypes.GENERIC);
        var voidSource = register(registry, DamageTypes.FELL_OUT_OF_WORLD);
        var admin = register(registry, DamageTypes.GENERIC_KILL);
        var invulnerableBypass = register(registry, key("invulnerable_bypass"));
        var customBypass = register(registry, key("custom_bypass"));
        registry.freeze();
        registry.bindTags(Map.of(DamageTypeTags.BYPASSES_INVULNERABILITY, List.of(invulnerableBypass),
                TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("myvillage:bypasses_shields")),
                List.of(customBypass)));
        assertFalse(ShieldRuntime.bypassesShields(new DamageSource(ordinary)));
        assertTrue(ShieldRuntime.bypassesShields(new DamageSource(voidSource)));
        assertTrue(ShieldRuntime.bypassesShields(new DamageSource(admin)));
        assertTrue(ShieldRuntime.bypassesShields(new DamageSource(invulnerableBypass)));
        assertTrue(ShieldRuntime.bypassesShields(new DamageSource(customBypass)));
    }

    @Test void absentTargetsCannotGrantReadOrRemoveAnyServerState() {
        var source = ResourceLocation.parse("myvillage:fixture");
        assertFalse(ShieldRuntime.grant(null, source, 10, 20));
        assertFalse(ShieldRuntime.removeSource(null, source));
        assertEquals(0, ShieldRuntime.total(null));
        assertTrue(ShieldRuntime.sources(null).isEmpty());
        assertDoesNotThrow(() -> ShieldRuntime.clear(null));
        assertDoesNotThrow(ShieldRuntime::stop);
    }

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("myvillage:" + path));
    }

    private static Holder.Reference<DamageType> register(MappedRegistry<DamageType> registry,
                                                        ResourceKey<DamageType> key) {
        return Registry.registerForHolder(registry, key.location(), new DamageType("测试", 0));
    }
}
