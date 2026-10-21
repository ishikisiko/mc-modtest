package com.example.myvillage.gathering;

import com.example.myvillage.MyVillageMod;
import com.google.gson.JsonObject;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Read-only operator diagnostics; activation and meditation always use ordinary player input. */
@EventBusSubscriber(modid = MyVillageMod.MOD_ID)
public final class GatheringCommands {
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("myvillage")
                .then(Commands.literal("gathering").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("inspect").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> {
                                    var pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                    var source = ctx.getSource();
                                    JsonObject out = new JsonObject();
                                    if (source.getLevel().getBlockEntity(pos) instanceof GatheringBlockEntity core) {
                                        out.addProperty("core", true); out.addProperty("formed", core.formed());
                                        out.addProperty("enabled", core.enabled()); out.addProperty("running", core.running());
                                        out.addProperty("charges", core.charges()); out.addProperty("stones", core.getItem(0).getCount());
                                        out.addProperty("users", core.userCount()); out.addProperty("issue", core.data.get(4));
                                        out.addProperty("nbt", core.saveWithFullMetadata(source.registryAccess()).toString());
                                    } else out.addProperty("core", false);
                                    source.sendSuccess(() -> Component.literal("GATHERING_ARRAY " + out), false);
                                    return out.get("core").getAsBoolean() ? 1 : 0;
                                })))));
    }
    private GatheringCommands() { }
}
