package com.example.myvillage.forging.array;

import com.example.myvillage.MyVillageMod;
import com.google.gson.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator-only readback; never starts a player transaction. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class ForgingArrayCommands {
    private ForgingArrayCommands(){}
    @SubscribeEvent public static void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("myvillage").then(Commands.literal("forgingarray").requires(s->s.hasPermission(2))
            .then(Commands.literal("inspect").then(Commands.argument("pos",BlockPosArgument.blockPos()).executes(ctx->{
                var source=ctx.getSource();var pos=BlockPosArgument.getLoadedBlockPos(ctx,"pos");var result=new JsonObject();
                if(source.getLevel().getBlockEntity(pos) instanceof ForgingArrayBlockEntity root){
                    result.addProperty("controller",true);result.addProperty("formed",root.formed());result.addProperty("active",root.active());
                    result.addProperty("running",root.running());result.addProperty("progress",root.progress());result.addProperty("duration",root.duration());
                    result.addProperty("quality",root.quality());result.addProperty("trait",root.trait());result.addProperty("status",root.status());
                    result.addProperty("authorizer",root.authorizer()==null?"":root.authorizer().toString());
                    result.addProperty("nbt",root.saveWithFullMetadata(source.registryAccess()).toString());
                    var items=new JsonArray();for(int i=0;i<2;i++){var stack=root.getItem(i);var item=new JsonObject();item.addProperty("slot",i);
                        item.addProperty("id",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());item.addProperty("count",stack.getCount());
                        item.addProperty("components",stack.getComponents().toString());items.add(item);}result.add("items",items);
                }else result.addProperty("controller",false);
                source.sendSuccess(()->Component.literal("FORGING_ARRAY "+result),false);return 1;
            })))));
    }
}
