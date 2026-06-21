package com.example.myvillage.region.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Loads the shipped region ruleset ({@code region_topology.json}) and catalog
 * ({@code region_profile/*.json}) from the filesystem. Java mirror of
 * {@code load_ruleset} / {@code load_catalog_dir} in
 * {@code tools/buildgen/region_topology.py}.
 *
 * <p>The parity test points this at the shipped
 * {@code src/main/resources/data/myvillage/worldgen} tree. The runtime service
 * (task group 3) adds a NeoForge {@code ResourceManager}-based loader that
 * reads the same JSON through the mod's resource pipeline; the {@code fromJson}
 * parsers on {@link Ruleset} / {@link RegionProfile} are shared by both paths
 * so there is no second source of truth.
 */
public final class RegionCatalogLoader {

    private RegionCatalogLoader() {
    }

    /** Load the ruleset from {@code region_topology.json}. */
    public static Ruleset loadRuleset(Path json) {
        try (Reader reader = Files.newBufferedReader(json, StandardCharsets.UTF_8)) {
            return Ruleset.fromJson(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException e) {
            throw new IllegalStateException("failed reading ruleset " + json, e);
        }
    }

    /**
     * Load the catalog by enumerating {@code region_profile/*.json} in the given
     * worldgen directory, sorted by filename (matching the Python
     * {@code sorted(glob("*.json"))} order so the Java generator sees the same
     * catalog sequence).
     */
    public static List<RegionProfile> loadCatalog(Path worldgenDir) {
        Path catalogDir = worldgenDir.resolve("region_profile");
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(catalogDir, "*.json")) {
            for (Path p : ds) {
                files.add(p);
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed enumerating catalog " + catalogDir, e);
        }
        files.sort(Path::compareTo);
        List<RegionProfile> profiles = new ArrayList<>(files.size());
        for (Path p : files) {
            try (Reader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                profiles.add(RegionProfile.fromJson(obj));
            } catch (IOException e) {
                throw new IllegalStateException("failed reading profile " + p, e);
            }
        }
        return List.copyOf(profiles);
    }

    /**
     * Convenience: load ruleset + catalog from the shipped worldgen directory
     * (containing {@code region_topology.json} and the {@code region_profile/}
     * subdirectory).
     */
    public static RegionData loadFromFilesystem(Path worldgenDir) {
        return new RegionData(
                loadRuleset(worldgenDir.resolve("region_topology.json")),
                loadCatalog(worldgenDir));
    }

    /**
     * Load ruleset + catalog from a NeoForge {@link ResourceManager} (the
     * runtime path — reads the same shipped JSON through the mod's resource
     * pipeline). Used by {@link RegionRuntimeService} on world load; shares the
     * {@link Ruleset#fromJson} / {@link RegionProfile#fromJson} parsers with
     * the filesystem path so there is no second source of truth.
     *
     * @param resourceManager the server's resource manager
     * @param namespace       the mod namespace ({@code myvillage})
     */
    public static RegionData loadFromResourceManager(
            net.minecraft.server.packs.resources.ResourceManager resourceManager,
            String namespace) {
        net.minecraft.resources.ResourceLocation rulesetId =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        namespace, "worldgen/region_topology.json");
        Ruleset ruleset;
        try (Reader reader = resourceManager.openAsReader(rulesetId)) {
            ruleset = Ruleset.fromJson(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException e) {
            throw new IllegalStateException("failed reading ruleset " + rulesetId, e);
        }

        Map<net.minecraft.resources.ResourceLocation, net.minecraft.server.packs.resources.Resource> entries =
                resourceManager.listResources(
                        "worldgen/region_profile",
                        rl -> rl.getPath().endsWith(".json"));
        List<RegionProfile> profiles = entries.entrySet().stream()
                .filter(e -> e.getKey().getNamespace().equals(namespace))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    try (Reader reader = e.getValue().openAsReader()) {
                        return RegionProfile.fromJson(
                                JsonParser.parseReader(reader).getAsJsonObject());
                    } catch (IOException ioe) {
                        throw new IllegalStateException("failed reading profile " + e.getKey(), ioe);
                    }
                })
                .collect(Collectors.toUnmodifiableList());
        return new RegionData(ruleset, profiles);
    }

    /** Bundled ruleset + catalog loaded for a world. */
    public record RegionData(Ruleset ruleset, List<RegionProfile> catalog) {
    }
}
