package com.example.myvillage.forging;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.item.ModDataComponents;
import com.google.gson.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator readback only. It cannot start an action, award experience, or edit an item. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class WorkshopInspection {
    private WorkshopInspection(){}
    private static JsonObject stack(ItemStack stack){
        var out=new JsonObject();out.addProperty("id",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());out.addProperty("count",stack.getCount());
        var gson=new Gson();var piece=stack.get(ModDataComponents.WORKPIECE.get());if(piece!=null)out.add("piece",gson.toJsonTree(piece));
        var temper=stack.get(ModDataComponents.TEMPER.get());if(temper!=null)out.add("temper",gson.toJsonTree(temper));
        var mark=stack.get(ModDataComponents.FORGE_MARK.get());if(mark!=null)out.add("mark",gson.toJsonTree(mark));
        out.addProperty("damage",stack.getDamageValue());out.addProperty("max_damage",stack.getMaxDamage());return out;
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("myvillage").then(Commands.literal("workshop").requires(s->s.hasPermission(2))
            .then(Commands.literal("inspect").then(Commands.argument("player",EntityArgument.player()).executes(ctx->{
                var player=EntityArgument.getPlayer(ctx,"player");var out=new JsonObject();out.add("held",stack(player.getMainHandItem()));
                if(player.containerMenu instanceof WorkshopMenu menu){
                    out.addProperty("open",true);out.addProperty("recipe",menu.recipe().id());
                    out.addProperty("start_reason",menu.reason(WorkshopMenu.START));out.addProperty("knows",menu.knows(menu.recipeIndex()));out.addProperty("heat",menu.heat());
                    out.addProperty("action",menu.action());out.addProperty("remaining",menu.remaining());out.addProperty("hits",menu.hitSequence());
                    out.addProperty("quench_reason",menu.reason(WorkshopMenu.QUENCH));out.addProperty("status",menu.status());out.addProperty("available",menu.available());out.addProperty("cap",menu.qualityCap());
                    out.addProperty("next",menu.nextAction());out.add("input",stack(menu.input()));out.add("output",stack(menu.output()));
                }else out.addProperty("open",false);
                ctx.getSource().sendSuccess(()->Component.literal("WORKSHOP "+out),false);return 1;
            })))));
    }
}
