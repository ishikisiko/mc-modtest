package com.example.myvillage.entity.beast;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * {@code /myvillage beast ...} debug commands (permission 2), for captures and server checks
 * ({@code debug on|off} logs each beast's move starts, ends, staggers and hits taken as
 * {@code BEAST_DEBUG} lines in the server log):
 * {@code move <targets> <move_id>} forces the selected beasts to start that move on their next AI
 * step, ignoring range, cooldown and gap (aimed at the current target, or straight ahead without
 * one); {@code status <targets>} prints each beast's move state. Use with {@code /tick freeze} and
 * {@code /tick step} to look at single ticks.
 */
public final class BeastCommands {
    private static final SuggestionProvider<CommandSourceStack> MOVE_IDS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(
                    BeastDefinitions.bundled().all().stream()
                            .flatMap(beast -> beast.moves().stream())
                            .map(BeastMoveDefinition::id),
                    builder);

    private BeastCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("beast")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("move")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("move_id", ResourceLocationArgument.id())
                                        .suggests(MOVE_IDS)
                                        .executes(context -> forceMove(
                                                context.getSource(),
                                                EntityArgument.getEntities(context, "targets"),
                                                ResourceLocationArgument.getId(context, "move_id"))))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("on").executes(context -> debug(context.getSource(), true)))
                        .then(Commands.literal("off").executes(context -> debug(context.getSource(), false))))
                .then(Commands.literal("status")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> status(
                                        context.getSource(),
                                        EntityArgument.getEntities(context, "targets")))));
    }

    private static int forceMove(CommandSourceStack source, Collection<? extends Entity> targets, ResourceLocation moveId) {
        int started = 0;
        for (Entity entity : targets) {
            if (entity instanceof BeastEntity beast && beast.forceMove(moveId)) {
                started++;
            }
        }
        if (started == 0) {
            source.sendFailure(Component.literal("No selected beast has move " + moveId));
            return 0;
        }
        int count = started;
        source.sendSuccess(() -> Component.literal("Starting " + moveId + " on " + count + " beast(s)"), true);
        return started;
    }

    private static int debug(CommandSourceStack source, boolean enabled) {
        BeastEntity.setDebugLog(enabled);
        source.sendSuccess(() -> Component.literal("Beast debug log " + (enabled ? "on" : "off")), true);
        return 1;
    }

    private static int status(CommandSourceStack source, Collection<? extends Entity> targets) {
        int shown = 0;
        for (Entity entity : targets) {
            if (entity instanceof BeastEntity beast) {
                String line = beast.debugStatus();
                source.sendSuccess(() -> Component.literal(line), false);
                shown++;
            }
        }
        if (shown == 0) {
            source.sendFailure(Component.literal("No beast selected"));
        }
        return shown;
    }
}
