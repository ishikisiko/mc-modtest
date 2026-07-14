package com.example.myvillage.combat;

import com.example.myvillage.combat.runtime.CombatDebugService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CombatCommands {
    private CombatCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("combat")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("debug")
                        .then(Commands.literal("on")
                                .executes(context -> setDebug(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setDebug(context.getSource(), false)))
                        .then(Commands.literal("status")
                                .executes(context -> debugStatus(context.getSource()))));
    }

    private static int setDebug(CommandSourceStack source, boolean enabled) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("commands.myvillage.combat.debug.player_only"));
            return 0;
        }
        CombatDebugService.setEnabled(player, enabled);
        source.sendSuccess(() -> Component.translatable(
                enabled
                        ? "commands.myvillage.combat.debug.on"
                        : "commands.myvillage.combat.debug.off"), false);
        return 1;
    }

    private static int debugStatus(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("commands.myvillage.combat.debug.player_only"));
            return 0;
        }
        boolean enabled = CombatDebugService.isEnabled(player);
        source.sendSuccess(() -> Component.translatable(
                enabled
                        ? "commands.myvillage.combat.debug.on"
                        : "commands.myvillage.combat.debug.off"), false);
        return enabled ? 1 : 0;
    }
}
