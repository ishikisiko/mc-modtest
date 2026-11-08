package com.example.myvillage.aptitude;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.example.myvillage.sim.runtime.WorldSimRuntime;

public final class AptitudeCommands {
    private AptitudeCommands() { }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("资质").executes(c -> info(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.literal("停止").executes(c -> { AptitudeCultivation.stop(c.getSource().getPlayerOrException()); return 1; })));
        dispatcher.register(Commands.literal("myvillage").then(Commands.literal("aptitude")
                .executes(c -> info(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.literal("info").requires(s -> s.hasPermission(2)).then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> info(c.getSource(), EntityArgument.getPlayer(c, "target")))))
                .then(Commands.literal("set").requires(s -> s.hasPermission(2)).then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("aptitude", StringArgumentType.word()).suggests((c,b) -> {
                            AptitudeRules.get().ids().forEach(b::suggest); return b.buildFuture();
                        }).then(Commands.argument("points", IntegerArgumentType.integer(0)).executes(c -> {
                            boolean changed = AptitudeRuntime.set(EntityArgument.getPlayer(c, "target"), StringArgumentType.getString(c, "aptitude"), IntegerArgumentType.getInteger(c, "points"));
                            c.getSource().sendSuccess(() -> Component.literal(changed ? "资质已更新" : "资质更新失败，请检查觉醒状态和属性名"), false); return changed ? 1 : 0;
                        })))))
                .then(Commands.literal("npc").requires(s -> s.hasPermission(2)).then(Commands.argument("person", IntegerArgumentType.integer(0))
                        .executes(c -> { var values = WorldSimRuntime.sim().map(s -> s.aptitudes(IntegerArgumentType.getInteger(c,"person"))).orElse(AptitudeValues.EMPTY);
                            c.getSource().sendSuccess(() -> Component.literal(values.values().toString()), false); return 1;
                        }).then(Commands.argument("aptitude", StringArgumentType.word()).then(Commands.argument("points", IntegerArgumentType.integer(0))
                                .executes(c -> WorldSimRuntime.sim().map(s -> s.setAptitude(IntegerArgumentType.getInteger(c,"person"),
                                        StringArgumentType.getString(c,"aptitude"),IntegerArgumentType.getInteger(c,"points")) ? 1 : 0).orElse(0))))))));
    }
    private static int info(CommandSourceStack source, ServerPlayer player) {
        var text = new StringBuilder("资质点数");
        for (String id : AptitudeRules.get().ids()) text.append("\n").append(AptitudeRuntime.name(id)).append("：").append(AptitudeRuntime.score(player,id));
        var training = com.example.myvillage.cultivation.CultivationService.aptitudes(player).training();
        training.ifPresent(c -> text.append("\n当前研习：").append(switch(c.kind()) {
            case "memory" -> "战意试炼";
            case "teaching" -> "名师授业";
            default -> "课题研究";
        }).append(" ").append(c.progress()).append("/").append(c.required()));
        source.sendSuccess(() -> Component.literal(text.toString()), false); return 1;
    }
}
