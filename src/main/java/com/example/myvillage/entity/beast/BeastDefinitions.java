package com.example.myvillage.entity.beast;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * The beast registry, loaded once from the mod's own bundled data on first use (both sides read
 * the same jar, so no sync payload is needed). A load failure throws {@link BeastDataException}; it
 * is a startup error, never a silent fallback.
 */
public final class BeastDefinitions {
    private static volatile BeastDefinitions bundled;

    private final Map<ResourceLocation, BeastDefinition> beasts = new LinkedHashMap<>();

    public BeastDefinitions(List<BeastDefinition> definitions) {
        for (BeastDefinition definition : Objects.requireNonNull(definitions, "definitions")) {
            if (beasts.putIfAbsent(definition.entity(), definition) != null) {
                throw new IllegalArgumentException("Duplicate beast " + definition.entity());
            }
        }
    }

    public static BeastDefinitions bundled() {
        BeastDefinitions current = bundled;
        if (current == null) {
            synchronized (BeastDefinitions.class) {
                current = bundled;
                if (current == null) {
                    current = new BeastDefinitions(BeastDataLoader.load(BeastDefinitions::openClasspath));
                    bundled = current;
                }
            }
        }
        return current;
    }

    private static InputStream openClasspath(String path) {
        return BeastDefinitions.class.getResourceAsStream("/" + path);
    }

    public List<BeastDefinition> all() {
        return List.copyOf(beasts.values());
    }

    public Optional<BeastDefinition> get(ResourceLocation entityId) {
        return Optional.ofNullable(beasts.get(entityId));
    }

    /** The definition an entity class is built on; a missing entry is a packaging error. */
    public BeastDefinition require(ResourceLocation entityId) {
        BeastDefinition definition = beasts.get(entityId);
        if (definition == null) {
            throw new IllegalStateException("No beast data for " + entityId + " in " + BeastDataLoader.INDEX_PATH);
        }
        return definition;
    }
}
