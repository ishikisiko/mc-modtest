package com.example.myvillage.alchemy.array;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.alchemy.PillQuality;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator-only readback and simulation evidence. Never used by the player production path. */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID)
public final class AlchemyArrayCommands {
    private AlchemyArrayCommands() { }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("myvillage")
                .then(Commands.literal("array").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("inspect").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> inspect(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
                        .then(Commands.literal("port").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> port(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
                        .then(Commands.literal("stale").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> stale(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))));
    }

    private static int inspect(CommandSourceStack source, BlockPos pos) {
        JsonObject result = new JsonObject();
        BlockEntity entity = source.getLevel().getBlockEntity(pos);
        result.addProperty("controller", entity instanceof AlchemyArrayBlockEntity);
        if (entity instanceof AlchemyArrayBlockEntity array) {
            CompoundTag saved = array.saveWithFullMetadata(source.registryAccess());
            result.addProperty("formed", array.formed());
            result.addProperty("enabled", array.enabled());
            result.addProperty("running", array.running());
            result.addProperty("status", array.status());
            result.addProperty("progress", array.progress());
            result.addProperty("duration", array.duration());
            result.addProperty("recipe", array.selectedRecipe());
            result.addProperty("cap", array.qualityCap());
            result.addProperty("mismatch_index", array.mismatchIndex());
            result.addProperty("mismatch_count", array.mismatchCount());
            result.addProperty("authorizer", array.authorizer() == null ? "" : array.authorizer().toString());
            result.addProperty("identity", saved.hasUUID("Identity") ? saved.getUUID("Identity").toString() : "");
            result.addProperty("nbt", saved.toString());
            JsonObject batch = new JsonObject();
            if (saved.contains("Batch")) {
                CompoundTag tag = saved.getCompound("Batch");
                batch.addProperty("brewer", tag.hasUUID("Brewer") ? tag.getUUID("Brewer").toString() : "");
                batch.addProperty("recipe", tag.getString("Recipe"));
                batch.addProperty("score", tag.getInt("Score"));
                batch.addProperty("cap", tag.getInt("Cap"));
                batch.addProperty("quality", tag.getInt("Quality"));
            }
            result.add("batch", batch);
            JsonArray items = new JsonArray();
            for (int slot = 0; slot < array.getContainerSize(); slot++) {
                JsonObject item = stack(array.getItem(slot));
                item.addProperty("slot", slot);
                items.add(item);
            }
            result.add("items", items);
        }
        source.sendSuccess(() -> Component.literal("ALCHEMY_ARRAY " + result), false);
        return entity instanceof AlchemyArrayBlockEntity ? 1 : 0;
    }

    private static JsonObject stack(ItemStack stack) {
        JsonObject result = new JsonObject();
        result.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        result.addProperty("count", stack.getCount());
        result.addProperty("quality", stack.isEmpty() ? 0 : PillQuality.get(stack));
        result.addProperty("components", stack.getComponents().toString());
        return result;
    }

    private static int port(CommandSourceStack source, BlockPos pos) {
        var level = source.getLevel();
        JsonObject result = new JsonObject();
        var controller = AlchemyArrayPattern.controller(level, pos);
        CompoundTag before = controller == null ? new CompoundTag() : controller.saveWithFullMetadata(source.registryAccess());
        result.addProperty("bound", controller != null);
        JsonArray sides = new JsonArray();
        for (Direction direction : Direction.values()) {
            JsonObject side = new JsonObject();
            side.addProperty("side", direction.getName());
            var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
            side.addProperty("available", handler != null);
            if (handler != null) {
                side.addProperty("slots", handler.getSlots());
                JsonArray probes = new JsonArray();
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    JsonObject probe = new JsonObject();
                    probe.addProperty("slot", slot);
                    probe.add("visible", stack(handler.getStackInSlot(slot)));
                    for (String id : new String[]{"minecraft:cobblestone", "myvillage:spirit_leaf",
                            "myvillage:dew_blossom", "myvillage:low_grade_spirit_stone"}) {
                        ItemStack input = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)), 16);
                        ItemStack original = input.copy();
                        ItemStack remainder = handler.insertItem(slot, input, true);
                        probe.addProperty(id, input.getCount() - remainder.getCount());
                        if (!ItemStack.matches(input, original))
                            throw new IllegalStateException("simulate changed caller stack");
                    }
                    probe.addProperty("extract", handler.extractItem(slot, 16, true).getCount());
                    probes.add(probe);
                }
                side.add("probes", probes);
            }
            sides.add(side);
        }
        result.add("sides", sides);
        result.addProperty("simulation_unchanged", controller == null
                || before.equals(controller.saveWithFullMetadata(source.registryAccess())));
        source.sendSuccess(() -> Component.literal("ALCHEMY_ARRAY_PORT " + result), false);
        return 1;
    }

    /** Bounded operator fixture: only an empty, idle machine can be temporarily replaced. */
    private static int stale(CommandSourceStack source, BlockPos portPos) {
        var level = source.getLevel();
        var original = AlchemyArrayPattern.controller(level, portPos);
        if (original == null || !original.isEmpty() || original.active()) {
            source.sendFailure(Component.literal("仅允许对库存为空且没有活动批次的完整炉阵执行缓存诊断。"));
            return 0;
        }
        var origin = original.getBlockPos();
        var controllerState = original.getBlockState();
        var facing = controllerState.getValue(AlchemyArrayBlock.FACING);
        var portState = level.getBlockState(portPos);
        var side = portState.getValue(AlchemyArrayBlock.FACING);
        var oldHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, portPos, side);
        if (oldHandler == null || oldHandler.getSlotLimit(0) <= 0) return 0;
        var casingCell = AlchemyArrayRules.get().cells().stream()
                .filter(cell -> cell.role() == AlchemyArrayRules.Role.CASING).findFirst().orElseThrow();
        var casingPos = AlchemyArrayPattern.worldPos(origin, facing, casingCell);
        var casingState = level.getBlockState(casingPos);
        CompoundTag saved = original.saveWithFullMetadata(source.registryAccess());
        JsonObject result = new JsonObject();
        boolean replaced = false;
        try {
            level.setBlockAndUpdate(casingPos, Blocks.AIR.defaultBlockState());
            result.addProperty("broken_rejected", oldHandler.getSlotLimit(0) == 0
                    && oldHandler.getStackInSlot(0).isEmpty()
                    && oldHandler.extractItem(0, 1, true).isEmpty());
            level.setBlockAndUpdate(casingPos, casingState);
            level.setBlockAndUpdate(origin, Blocks.AIR.defaultBlockState());
            replaced = true;
            level.setBlockAndUpdate(origin, controllerState);
            result.addProperty("replacement_rejected", oldHandler.getSlotLimit(0) == 0
                    && oldHandler.getStackInSlot(0).isEmpty()
                    && oldHandler.extractItem(0, 1, true).isEmpty());
            level.invalidateCapabilities(portPos);
            var fresh = level.getCapability(Capabilities.ItemHandler.BLOCK, portPos, side);
            result.addProperty("fresh_accepted", fresh != null && fresh.getSlotLimit(0) > 0);
        } finally {
            level.setBlockAndUpdate(casingPos, casingState);
            if (replaced) {
                level.setBlockAndUpdate(origin, controllerState);
                if (level.getBlockEntity(origin) instanceof AlchemyArrayBlockEntity restored) {
                    restored.loadWithComponents(saved, source.registryAccess());
                    restored.freshCheck();
                    restored.setChanged();
                }
            }
            for (var cell : AlchemyArrayRules.get().cells()) {
                var pos = AlchemyArrayPattern.worldPos(origin, facing, cell);
                if (level.hasChunkAt(pos)) level.invalidateCapabilities(pos);
            }
        }
        result.addProperty("restored", level.getBlockEntity(origin) instanceof AlchemyArrayBlockEntity restored
                && saved.equals(restored.saveWithFullMetadata(source.registryAccess()))
                && AlchemyArrayPattern.check(level, origin, facing).formed());
        source.sendSuccess(() -> Component.literal("ALCHEMY_ARRAY_STALE " + result), false);
        return 1;
    }
}
