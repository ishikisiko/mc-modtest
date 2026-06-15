package com.example.myvillage.town;

import com.example.myvillage.MyVillageMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ModBlockFallback {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModBlockFallback.class);
    private static final ResourceLocation FALLBACK_MAP_ID =
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "mod_block_fallbacks.json");
    private static final FileToIdConverter STRUCTURE_FILES = new FileToIdConverter("structure", ".nbt");
    private static final String DEFAULT_LAST_RESORT_STATE = "minecraft:cobblestone";

    private static Map<ResourceLocation, BlockState> fallbackStates = Map.of();
    private static BlockState defaultLastResort = Blocks.COBBLESTONE.defaultBlockState();
    private static boolean loaded;
    private static boolean parseFailureLogged;

    private ModBlockFallback() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        load(event.getServer());
    }

    public static synchronized void load(MinecraftServer server) {
        HolderLookup<Block> blockLookup = server.registryAccess().lookupOrThrow(Registries.BLOCK);
        defaultLastResort = parseFallbackState(DEFAULT_LAST_RESORT_STATE, blockLookup);
        fallbackStates = loadFallbackStates(server.getResourceManager(), blockLookup);
        loaded = true;
        LOGGER.info("Loaded {} myvillage optional-mod block fallbacks", fallbackStates.size());
    }

    public static Optional<LoadedTemplate> loadTemplate(ServerLevel level, ResourceLocation id) {
        ensureLoaded(level.getServer());
        Optional<CompoundTag> raw = loadStructureTag(level.getServer().getResourceManager(), id);
        if (raw.isEmpty()) {
            return level.getStructureManager().get(id).map(template -> new LoadedTemplate(template, 0));
        }

        CompoundTag patched = raw.get().copy();
        int substitutions = patchPalettes(patched);
        StructureTemplate template = level.getStructureManager().readStructure(patched);
        return Optional.of(new LoadedTemplate(template, substitutions));
    }

    public static BlockState resolveBlockState(ResourceLocation id) {
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            return BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        }
        return fallbackStates.getOrDefault(id, defaultLastResort);
    }

    public static BlockState resolveBlockState(MinecraftServer server, ResourceLocation id) {
        ensureLoaded(server);
        return resolveBlockState(id);
    }

    private static synchronized void ensureLoaded(MinecraftServer server) {
        if (!loaded) {
            load(server);
        }
    }

    private static Map<ResourceLocation, BlockState> loadFallbackStates(
            ResourceManager resourceManager,
            HolderLookup<Block> blockLookup) {
        Map<ResourceLocation, BlockState> states = new HashMap<>();
        try (
                InputStream stream = resourceManager.open(FALLBACK_MAP_ID);
                Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)
        ) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                LOGGER.warn("Ignoring {} because it is not a JSON object", FALLBACK_MAP_ID);
                return Map.of();
            }
            JsonObject object = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id == null) {
                    LOGGER.warn("Ignoring invalid optional-mod fallback id {}", entry.getKey());
                    continue;
                }
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                    logParseFailureOnce("fallback for " + id + " is not a string");
                    states.put(id, defaultLastResort);
                    continue;
                }
                states.put(id, parseFallbackState(entry.getValue().getAsString(), blockLookup));
            }
        } catch (FileNotFoundException exception) {
            LOGGER.warn("No optional-mod fallback map found at {}; using {}", FALLBACK_MAP_ID, DEFAULT_LAST_RESORT_STATE);
        } catch (IOException | IllegalStateException exception) {
            LOGGER.warn("Failed to load optional-mod fallback map {}; using {}", FALLBACK_MAP_ID, DEFAULT_LAST_RESORT_STATE, exception);
        }
        return Map.copyOf(states);
    }

    private static BlockState parseFallbackState(String state, HolderLookup<Block> blockLookup) {
        try {
            return BlockStateParser.parseForBlock(blockLookup, state, false).blockState();
        } catch (CommandSyntaxException exception) {
            logParseFailureOnce("failed to parse fallback block state " + state + "; using " + DEFAULT_LAST_RESORT_STATE);
            return Blocks.COBBLESTONE.defaultBlockState();
        }
    }

    private static void logParseFailureOnce(String message) {
        if (!parseFailureLogged) {
            parseFailureLogged = true;
            LOGGER.warn(message);
        }
    }

    private static Optional<CompoundTag> loadStructureTag(ResourceManager resourceManager, ResourceLocation id) {
        ResourceLocation file = STRUCTURE_FILES.idToFile(id);
        try (InputStream stream = resourceManager.open(file)) {
            return Optional.of(NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap()));
        } catch (FileNotFoundException exception) {
            return Optional.empty();
        } catch (IOException exception) {
            LOGGER.warn("Failed to read structure template NBT {}", id, exception);
            return Optional.empty();
        }
    }

    private static int patchPalettes(CompoundTag tag) {
        int substitutions = 0;
        if (tag.contains("palette", Tag.TAG_LIST)) {
            substitutions += patchPalette(tag.getList("palette", Tag.TAG_COMPOUND));
        }
        if (tag.contains("palettes", Tag.TAG_LIST)) {
            ListTag palettes = tag.getList("palettes", Tag.TAG_LIST);
            for (int i = 0; i < palettes.size(); i++) {
                substitutions += patchPalette(palettes.getList(i));
            }
        }
        return substitutions;
    }

    private static int patchPalette(ListTag palette) {
        int substitutions = 0;
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            String name = entry.getString("Name");
            ResourceLocation id = ResourceLocation.tryParse(name);
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                continue;
            }

            BlockState fallback = id == null ? defaultLastResort : resolveBlockState(id);
            CompoundTag replacement = NbtUtils.writeBlockState(fallback);
            entry.putString("Name", replacement.getString("Name"));
            if (replacement.contains("Properties", Tag.TAG_COMPOUND)) {
                entry.put("Properties", replacement.getCompound("Properties"));
            } else {
                entry.remove("Properties");
            }
            substitutions++;
        }
        return substitutions;
    }

    public record LoadedTemplate(StructureTemplate template, int substitutions) {
    }
}
