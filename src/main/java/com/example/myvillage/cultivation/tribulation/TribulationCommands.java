package com.example.myvillage.cultivation.tribulation;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 权限2；没有 force、模拟或奖励旁路。 */
public final class TribulationCommands {
    private TribulationCommands() { }
    public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("tribulation").requires(source -> source.hasPermission(2));
        for (String action : new String[]{"start", "status", "stop"}) {
            root.then(Commands.literal(action).then(Commands.argument("targets", EntityArgument.players()).executes(context -> {
                int count = 0;
                for (var player : EntityArgument.getPlayers(context, "targets")) {
                    boolean accepted = switch (action) {
                        case "start" -> TribulationManager.start(player);
                        case "stop" -> TribulationManager.stop(player, "管理员终止");
                        default -> true;
                    };
                    if (accepted) count++;
                    var json = TribulationManager.statusJson(player);
                    json.addProperty("command", action); json.addProperty("accepted", accepted);
                    String text = "TRIBULATION_STATUS " + json;
                    context.getSource().sendSuccess(() -> Component.literal(text), false);
                }
                return count;
            })));
        }
        event.getDispatcher().register(Commands.literal("myvillage").then(root));
    }
}
