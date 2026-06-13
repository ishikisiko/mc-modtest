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
import java.util.List;
import java.util.Optional;

/**
 * Ships myvillage structure NBTs as datapack resources and exposes small
 * debug commands for in-game NeoForge validation. No worldgen is registered.
 */
@Mod(MyVillageMod.MOD_ID)
public final class MyVillageMod {
    public static final String MOD_ID = "myvillage";
    private static final int GALLERY_SPACING = 20;
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
        List<ResourceLocation> structures = level.getStructureManager()
                .listTemplates()
                .filter(id -> MOD_ID.equals(id.getNamespace()))
                .sorted(Comparator.comparing(ResourceLocation::getPath))
                .toList();

        if (structures.isEmpty()) {
            source.sendFailure(Component.literal("No myvillage structures are loaded"));
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        BlockPos origin = player.blockPosition();
        int placed = 0;
        for (ResourceLocation id : structures) {
            BlockPos pos = origin.offset(placed * GALLERY_SPACING, 0, 0);
            if (placeStructure(source, id, pos) == 1) {
                placed++;
            }
        }

        final int placedCount = placed;
        source.sendSuccess(
                () -> Component.literal("Placed myvillage gallery: " + placedCount + " structures"),
                false);
        return placedCount;
    }

    private int placeStructure(CommandSourceStack source, ResourceLocation structureId, BlockPos origin) {
        ServerLevel level = source.getLevel();
        Optional<StructureTemplate> template = level.getStructureManager().get(structureId);
        if (template.isEmpty()) {
            source.sendFailure(Component.literal("Missing structure template: " + structureId));
            return 0;
        }

        boolean placed = template.get().placeInWorld(
                level,
                origin,
                origin,
                new StructurePlaceSettings(),
                level.getRandom(),
                Block.UPDATE_CLIENTS);
        if (!placed) {
            source.sendFailure(Component.literal("Failed to place structure template: " + structureId));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Placed " + structureId + " at "
                        + origin.getX() + " " + origin.getY() + " " + origin.getZ()),
                false);
        return 1;
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
}
