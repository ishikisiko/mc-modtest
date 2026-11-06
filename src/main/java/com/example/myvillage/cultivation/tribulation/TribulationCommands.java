package com.example.myvillage.cultivation.tribulation;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 权限 2；只能选择正式试炼，不能跳过资格、雷击或奖励条件。 */
public final class TribulationCommands {
    private TribulationCommands(){}
    public static void register(RegisterCommandsEvent event){
        var root=Commands.literal("tribulation").requires(s->s.hasPermission(2));
        var start=Commands.literal("start");
        for(var kind:TribulationKind.values())
            start.then(Commands.literal(kind.id()).then(Commands.argument("targets",EntityArgument.players()).executes(c->{
                int count=0;
                for(var p:EntityArgument.getPlayers(c,"targets")){
                    boolean accepted=TribulationManager.start(p,kind);if(accepted)count++;
                    var json=TribulationManager.statusJson(p);json.addProperty("command","start");json.addProperty("accepted",accepted);
                    c.getSource().sendSuccess(()->Component.literal("TRIBULATION_STATUS "+json),false);
                }
                return count;
            })));
        root.then(start);
        for(String action:new String[]{"status","stop"})root.then(Commands.literal(action)
                .then(Commands.argument("targets",EntityArgument.players()).executes(c->{
                    int count=0;
                    for(var p:EntityArgument.getPlayers(c,"targets")){
                        boolean accepted=action.equals("status")||TribulationManager.stop(p,"管理员终止");if(accepted)count++;
                        var json=TribulationManager.statusJson(p);json.addProperty("command",action);json.addProperty("accepted",accepted);
                        c.getSource().sendSuccess(()->Component.literal("TRIBULATION_STATUS "+json),false);
                    }
                    return count;
                })));
        event.getDispatcher().register(Commands.literal("myvillage").then(root));
    }
}
