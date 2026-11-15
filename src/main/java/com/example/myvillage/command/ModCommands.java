package com.example.myvillage.command;

import com.example.myvillage.ModBrand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 完整入口和快捷入口共同引用同一棵命令树。 */
public final class ModCommands {
    private ModCommands() {}
    public static LiteralArgumentBuilder<CommandSourceStack> root() { return CommandRoots.root(ModBrand.COMMAND_ROOT); }
    public static LiteralArgumentBuilder<CommandSourceStack> adminRoot() { return CommandRoots.guardedRoot(ModBrand.COMMAND_ROOT,s->s.hasPermission(2)); }
    public static void register() { NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,ModCommands::complete); }
    private static void complete(RegisterCommandsEvent event) {
        var dispatcher=event.getDispatcher();
        dispatcher.register(root().executes(c->help(c.getSource()))
                .then(Commands.literal("help").executes(c->help(c.getSource())))
                .then(Commands.literal("帮助").executes(c->help(c.getSource()))));
        if(dispatcher.getRoot().getChild(ModBrand.COMMAND_SHORT)!=null)throw new IllegalStateException("命令入口已被占用："+ModBrand.COMMAND_SHORT);
        CommandRoots.registerShortcut(dispatcher,ModBrand.COMMAND_SHORT,ModBrand.COMMAND_ROOT);
    }
    private static int help(CommandSourceStack source) {
        var root=source.getServer().getCommands().getDispatcher().getRoot().getChild(ModBrand.COMMAND_ROOT);
        String commands=root.getChildren().stream().filter(n->n.canUse(source)).map(n->n.getName()).sorted().collect(Collectors.joining("、"));
        source.sendSuccess(()->Component.literal(ModBrand.DISPLAY_NAME+"指令：/"+ModBrand.COMMAND_ROOT+" 或 /"+ModBrand.COMMAND_SHORT+"\n可用子命令："+commands),false);
        return 1;
    }
}
