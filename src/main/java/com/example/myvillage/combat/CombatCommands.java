package com.example.myvillage.combat;

import com.example.myvillage.combat.runtime.CombatDebugService;
import com.example.myvillage.combat.runtime.CombatDodgeService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
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
                                .executes(context -> debugStatus(context.getSource()))))
                .then(Commands.literal("dodge")
                        .then(Commands.literal("status")
                                .executes(context -> dodgeStatus(context.getSource(), null))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> dodgeStatus(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player"))))));
    }

    /** Developer readout (literal, like the capture logs): chosen 身法, cooldown and window left. */
    private static int dodgeStatus(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = target != null ? target : source.getPlayerOrException();
        String technique = CombatDodgeService.currentChoice(player)
                .map(choice -> choice.id() + " grade=" + choice.grade()
                        + " distance=" + choice.movement().dashDistance()
                        + " invuln=" + choice.movement().invulnerableTicks()
                        + " cooldown_ticks=" + choice.movement().cooldownTicks())
                .orElse("none");
        long cooldown = CombatDodgeService.cooldownRemaining(player);
        long window = CombatDodgeService.windowRemaining(player);
        String line = "dodge " + player.getGameProfile().getName()
                + ": technique=" + technique
                + " cooldown_remaining=" + cooldown
                + " window_remaining=" + window
                + " dodging=" + CombatDodgeService.isDodging(player);
        source.sendSuccess(() -> Component.literal(line), false);
        return 1;
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
