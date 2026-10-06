package com.example.myvillage.cultivation.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A school (流派), file {@code data/<ns>/myvillage/school/<id>.json}. A weapon school names the weapon
 * family it fights with; a special school names its runtime hook. {@code element_lean} may be empty.
 * Sects carry no school.
 */
public record SchoolDefinition(
        String translationKey,
        SchoolKind kind,
        Optional<String> weaponFamily,
        Optional<SchoolRuntime> runtime,
        List<ResourceLocation> elementLean) {
    private static final Codec<SerializedSchool> SERIALIZED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("translation_key").forGetter(SerializedSchool::translationKey),
            SchoolKind.CODEC.fieldOf("kind").forGetter(SerializedSchool::kind),
            Codec.STRING.optionalFieldOf("weapon_family").forGetter(SerializedSchool::weaponFamily),
            SchoolRuntime.CODEC.optionalFieldOf("runtime").forGetter(SerializedSchool::runtime),
            ResourceLocation.CODEC.listOf().optionalFieldOf("element_lean", List.of())
                    .forGetter(SerializedSchool::elementLean)
    ).apply(instance, SerializedSchool::new));

    public static final Codec<SchoolDefinition> CODEC = SERIALIZED_CODEC
            .comapFlatMap(SerializedSchool::decode, SchoolDefinition::serialize);

    public SchoolDefinition {
        if (translationKey == null || translationKey.isBlank()) {
            throw new IllegalArgumentException("School translation_key must not be blank");
        }
        kind = Objects.requireNonNull(kind, "kind");
        weaponFamily = Objects.requireNonNull(weaponFamily, "weaponFamily");
        runtime = Objects.requireNonNull(runtime, "runtime");
        if (kind == SchoolKind.WEAPON) {
            if (weaponFamily.isEmpty() || weaponFamily.get().isBlank()) {
                throw new IllegalArgumentException("A weapon school needs weapon_family");
            }
            if (!weaponFamily.get().matches("[a-z0-9_]+")) {
                throw new IllegalArgumentException(
                        "weapon_family must be lowercase [a-z0-9_], got " + weaponFamily.get());
            }
            if (runtime.isPresent()) {
                throw new IllegalArgumentException("A weapon school must not name a runtime");
            }
        } else {
            if (runtime.isEmpty()) {
                throw new IllegalArgumentException("A special school needs runtime");
            }
            if (weaponFamily.isPresent()) {
                throw new IllegalArgumentException("A special school must not name a weapon_family");
            }
        }
        Objects.requireNonNull(elementLean, "elementLean");
        elementLean = List.copyOf(elementLean);
        Set<ResourceLocation> unique = new HashSet<>();
        for (ResourceLocation element : elementLean) {
            if (!unique.add(Objects.requireNonNull(element, "element_lean id"))) {
                throw new IllegalArgumentException("Duplicate element_lean id " + element);
            }
        }
    }

    private SerializedSchool serialize() {
        return new SerializedSchool(translationKey, kind, weaponFamily, runtime, elementLean);
    }

    private record SerializedSchool(
            String translationKey,
            SchoolKind kind,
            Optional<String> weaponFamily,
            Optional<SchoolRuntime> runtime,
            List<ResourceLocation> elementLean) {
        private DataResult<SchoolDefinition> decode() {
            try {
                return DataResult.success(new SchoolDefinition(
                        translationKey, kind, weaponFamily, runtime, elementLean));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return DataResult.error(exception::getMessage);
            }
        }
    }
}
