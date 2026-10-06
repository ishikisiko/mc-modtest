package com.example.myvillage.sim.runtime.avatar;

import com.example.myvillage.sim.runtime.WorldSimText;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * {@code /myvillage world gates [retry]}: the state of the framed gate realization
 * ({@link GateRealizer#status()}), and {@code retry} to let the gates given up this session be
 * tried again. Hung under the {@code world} node by {@code WorldSimCommands} (which already
 * requires permission 2).
 */
public final class GateRealizerCommands {
    private GateRealizerCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("gates")
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("retry").executes(ctx -> retry(ctx.getSource())));
    }

    private static int status(CommandSourceStack source) {
        String status = GateRealizer.status();
        source.sendSuccess(() -> WorldSimText.line("gates.status", status), false);
        return 1;
    }

    private static int retry(CommandSourceStack source) {
        int cleared = GateRealizer.clearFailed();
        source.sendSuccess(() -> WorldSimText.line("gates.retry", cleared), true);
        return 1;
    }
}
