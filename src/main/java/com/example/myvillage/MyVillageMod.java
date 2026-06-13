package com.example.myvillage;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ships myvillage structure NBTs as datapack resources and exposes small
 * debug commands for in-game NeoForge validation. No worldgen is registered.
 */
@Mod(MyVillageMod.MOD_ID)
public final class MyVillageMod {
    public static final String MOD_ID = "myvillage";
    private static final int GALLERY_SPACING = 60;
    private static final Logger LOGGER = LoggerFactory.getLogger(MyVillageMod.class);

    public MyVillageMod() {
        LOGGER.info("MyVillage resource mod loaded");
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal(MOD_ID)
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("place")
                                .then(Commands.argument("structure_id", StringArgumentType.string())
                                        .executes(ctx -> placeNamedStructure(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "structure_id")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listStructures(ctx.getSource())))
                        .then(Commands.literal("gallery")
                                .executes(ctx -> placeGallery(ctx.getSource()))));
    }

    private int placeNamedStructure(CommandSourceStack source, String rawId) throws CommandSyntaxException {
        ResourceLocation structureId = parseStructureId(rawId);
        if (structureId == null) {
            source.sendFailure(Component.literal("Invalid myvillage structure id: " + rawId));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        return placeStructure(source, structureId, player.blockPosition());
    }

    private int placeGallery(CommandSourceStack source) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        List<ResourceLocation> structures = myvillageStructures(level);

        if (structures.isEmpty()) {
            source.sendFailure(Component.literal("No myvillage structures are loaded"));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        BlockPos origin = player.blockPosition();
        int placed = 0;
        Map<String, List<ResourceLocation>> columns = groupedGalleryStructures(structures);
        int column = 0;
        for (List<ResourceLocation> columnStructures : columns.values()) {
            int row = 0;
            for (ResourceLocation id : columnStructures) {
                BlockPos pos = origin.offset(column * GALLERY_SPACING, 0, row * GALLERY_SPACING);
                if (placeStructure(source, id, pos) == 1) {
                    placed++;
                    row++;
                }
            }
            column++;
        }

        final int placedCount = placed;
        source.sendSuccess(
                () -> Component.literal("Placed myvillage gallery: " + placedCount + " structures"),
                false);
        return placedCount;
    }

    private int listStructures(CommandSourceStack source) {
        List<ResourceLocation> structures = myvillageStructures(source.getLevel());
        if (structures.isEmpty()) {
            source.sendFailure(Component.literal("No myvillage structures are loaded"));
            return 0;
        }

        String names = structures.stream()
                .map(ResourceLocation::toString)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        source.sendSuccess(
                () -> Component.literal("Loaded myvillage structures (" + structures.size() + "): " + names),
                false);
        return structures.size();
    }

    private int placeStructure(CommandSourceStack source, ResourceLocation structureId, BlockPos origin) {
        ServerLevel level = source.getLevel();
        Optional<StructureTemplate> template = level.getStructureManager().get(structureId);
        if (template.isEmpty()) {
            source.sendFailure(Component.literal("Missing structure template: " + structureId));
            return 0;
        }

        BlockPos placementOrigin = origin.offset(0, placementYOffset(structureId), 0);
        boolean placed = template.get().placeInWorld(
                level,
                placementOrigin,
                placementOrigin,
                new StructurePlaceSettings(),
                level.getRandom(),
                Block.UPDATE_CLIENTS);
        if (!placed) {
            source.sendFailure(Component.literal("Failed to place structure template: " + structureId));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Placed " + structureId + " at "
                        + placementOrigin.getX() + " " + placementOrigin.getY() + " " + placementOrigin.getZ()),
                false);
        return 1;
    }

    private static int placementYOffset(ResourceLocation structureId) {
        return structureId.getPath().startsWith("test_") ? 0 : -1;
    }

    private static ResourceLocation parseStructureId(String rawId) {
        String trimmed = rawId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.indexOf(':') >= 0) {
            return ResourceLocation.tryParse(trimmed);
        }
        return ResourceLocation.tryBuild(MOD_ID, trimmed);
    }

    private static List<ResourceLocation> myvillageStructures(ServerLevel level) {
        return level.getStructureManager()
                .listTemplates()
                .filter(id -> MOD_ID.equals(id.getNamespace()))
                .sorted(Comparator.comparing(ResourceLocation::getPath))
                .toList();
    }

    private static Map<String, List<ResourceLocation>> groupedGalleryStructures(List<ResourceLocation> structures) {
        Map<String, List<ResourceLocation>> columns = new LinkedHashMap<>();
        for (String group : List.of("house", "shop", "blacksmith", "chinese_courtyard", "chinese_review", "test", "other")) {
            columns.put(group, new ArrayList<>());
        }
        for (ResourceLocation id : structures) {
            columns.computeIfAbsent(galleryGroup(id.getPath()), ignored -> new ArrayList<>()).add(id);
        }
        columns.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return columns;
    }

    private static String galleryGroup(String path) {
        if (path.startsWith("small_shop") || path.startsWith("medium_shop")) {
            return "shop";
        }
        if (path.startsWith("small_house") || path.startsWith("medium_house") || path.startsWith("big_house")) {
            return "house";
        }
        if (path.startsWith("blacksmith")) {
            return "blacksmith";
        }
        if (path.startsWith("chinese_courtyard")) {
            return "chinese_courtyard";
        }
        if (path.endsWith("_review")) {
            return "chinese_review";
        }
        if (path.startsWith("test_")) {
            return "test";
        }
        return "other";
    }
}
