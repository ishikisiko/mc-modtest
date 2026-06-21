package com.example.myvillage;

import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.region.runtime.RegionCommands;
import com.example.myvillage.region.runtime.RegionRuntimeService;
import com.example.myvillage.sect.SectGenerator;
import com.example.myvillage.sect.SectStructures;
import com.example.myvillage.town.TownGenerator;
import com.example.myvillage.town.ModBlockFallback;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
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
 * debug commands for in-game NeoForge validation. Registers the cultivation
 * sect as a custom worldgen {@link SectStructures#SECT structure} — a rare,
 * biome-gated landmark whose mountain is derived from the compound's terrace
 * profile (反推山形) and baked into chunks; locatable via
 * {@code /locate structure myvillage:sect} and force-generatable via
 * {@code /myvillage sect worldgen [seed] [variant]}.
 */
@Mod(MyVillageMod.MOD_ID)
public final class MyVillageMod {
    public static final String MOD_ID = "myvillage";
    private static final int GALLERY_SPACING = 128;
    private static final List<String> GALLERY_GROUP_ORDER = List.of(
            "house",
            "shop",
            "blacksmith",
            "chinese_courtyard",
            "civic",
            "cultivation_town",
            "cultivation_sect",
            "chinese_review",
            "test",
            "other");
    private static final Logger LOGGER = LoggerFactory.getLogger(MyVillageMod.class);

    public MyVillageMod(IEventBus modEventBus) {
        LOGGER.info("MyVillage resource mod loaded");
        ModBlocks.register(modEventBus);
        SectStructures.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(RegionRuntimeService::onServerStarted);
        NeoForge.EVENT_BUS.addListener(RegionRuntimeService::onServerStopping);
    }

    private void onServerStarted(ServerStartedEvent event) {
        ModBlocks.verifyRegistered();
        ModBlockFallback.onServerStarted(event);
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
                        .then(Commands.literal("town")
                                .executes(ctx -> TownGenerator.generate(
                                        ctx.getSource(),
                                        TownGenerator.seedFromSource(ctx.getSource())))
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(ctx -> TownGenerator.generate(
                                                ctx.getSource(),
                                                LongArgumentType.getLong(ctx, "seed")))))
                        .then(Commands.literal("sect")
                                .executes(ctx -> SectGenerator.generate(
                                        ctx.getSource(),
                                        SectGenerator.seedFromSource(ctx.getSource())))
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(ctx -> SectGenerator.generate(
                                                ctx.getSource(),
                                                LongArgumentType.getLong(ctx, "seed"))))
                                .then(Commands.literal("worldgen")
                                        .executes(ctx -> SectGenerator.generateForced(
                                                ctx.getSource(),
                                                SectGenerator.seedFromSource(ctx.getSource()), null))
                                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                                .executes(ctx -> SectGenerator.generateForced(
                                                        ctx.getSource(),
                                                        LongArgumentType.getLong(ctx, "seed"), null))
                                                .then(Commands.argument("variant", StringArgumentType.string())
                                                        .executes(ctx -> SectGenerator.generateForced(
                                                                ctx.getSource(),
                                                                LongArgumentType.getLong(ctx, "seed"),
                                                                StringArgumentType.getString(ctx, "variant")))))))
                        .then(Commands.literal("gallery")
                                .executes(ctx -> placeGallery(ctx.getSource(), GalleryScope.ALL))
                                .then(Commands.literal("original")
                                        .executes(ctx -> placeGallery(ctx.getSource(), GalleryScope.ORIGINAL)))
                                .then(Commands.literal("cultivation")
                                        .executes(ctx -> placeGallery(ctx.getSource(), GalleryScope.CULTIVATION))))
                        .then(Commands.literal("spawn")
                                .then(Commands.literal("info")
                                        .executes(ctx -> RegionCommands.spawnInfo(ctx.getSource())))
                                .then(Commands.literal("recompute")
                                        .executes(ctx -> RegionCommands.spawnRecompute(ctx.getSource())))));
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

    private int placeGallery(CommandSourceStack source, GalleryScope scope) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        List<ResourceLocation> structures = myvillageStructures(level);

        if (structures.isEmpty()) {
            source.sendFailure(Component.literal("No myvillage structures are loaded"));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        BlockPos origin = player.blockPosition();
        int placed = 0;
        Map<String, List<ResourceLocation>> columns = groupedGalleryStructures(structures, scope);
        if (columns.isEmpty()) {
            source.sendFailure(Component.literal("No myvillage " + scope.label() + " structures are loaded"));
            return 0;
        }

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
                () -> Component.literal("Placed myvillage " + scope.label() + " gallery: "
                        + placedCount + " structures"),
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
        Optional<ModBlockFallback.LoadedTemplate> loadedTemplate = ModBlockFallback.loadTemplate(level, structureId);
        if (loadedTemplate.isEmpty()) {
            source.sendFailure(Component.literal("Missing structure template: " + structureId));
            return 0;
        }

        StructureTemplate template = loadedTemplate.get().template();
        BlockPos placementOrigin = origin.offset(0, placementYOffset(structureId), 0);
        boolean placed = template.placeInWorld(
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

        int substitutions = loadedTemplate.get().substitutions();
        source.sendSuccess(
                () -> Component.literal("Placed " + structureId + " at "
                        + placementOrigin.getX() + " " + placementOrigin.getY() + " " + placementOrigin.getZ()
                        + (substitutions > 0 ? " fallback_substitutions=" + substitutions : "")),
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

    private static Map<String, List<ResourceLocation>> groupedGalleryStructures(
            List<ResourceLocation> structures,
            GalleryScope scope) {
        Map<String, List<ResourceLocation>> columns = new LinkedHashMap<>();
        for (String group : GALLERY_GROUP_ORDER) {
            columns.put(group, new ArrayList<>());
        }
        for (ResourceLocation id : structures) {
            String group = galleryGroup(id.getPath());
            if (scope.includesGroup(group)) {
                columns.computeIfAbsent(group, ignored -> new ArrayList<>()).add(id);
            }
        }
        columns.get("civic").sort(
                Comparator.<ResourceLocation>comparingInt(id -> civicGalleryOrder(id.getPath()))
                        .thenComparing(ResourceLocation::getPath));
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
        if (path.startsWith("tavern") || path.startsWith("lord_manor")) {
            return "civic";
        }
        if (path.startsWith("sect_") || path.startsWith("scripture_pavilion")
                || path.startsWith("alchemy_room") || path.startsWith("disciple_quarters")
                || path.startsWith("cultivation_sect")) {
            return "cultivation_sect";
        }
        if (path.startsWith("cultivation_") || path.startsWith("town_shrine")) {
            return "cultivation_town";
        }
        if (path.endsWith("_review")) {
            return "chinese_review";
        }
        if (path.startsWith("test_")) {
            return "test";
        }
        return "other";
    }

    private enum GalleryScope {
        ALL("all"),
        ORIGINAL("original"),
        CULTIVATION("cultivation");

        private final String label;

        GalleryScope(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }

        private boolean includesGroup(String group) {
            boolean cultivation = group.equals("cultivation_town") || group.equals("cultivation_sect");
            return switch (this) {
                case ALL -> true;
                case ORIGINAL -> !cultivation;
                case CULTIVATION -> cultivation;
            };
        }
    }

    private static int civicGalleryOrder(String path) {
        if (path.startsWith("tavern")) {
            return 0;
        }
        if (path.startsWith("lord_manor")) {
            return 1;
        }
        return 2;
    }

}
