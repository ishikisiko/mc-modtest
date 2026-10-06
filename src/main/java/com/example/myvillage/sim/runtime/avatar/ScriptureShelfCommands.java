package com.example.myvillage.sim.runtime.avatar;

import com.example.myvillage.sect.SectCourtyard;
import com.example.myvillage.sim.runtime.WorldSimText;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /myvillage world sect <id> shelves [place]}: the scripture shelf sites of a realized gate
 * and the shelf found in each ({@link ScriptureShelves#survey}), and {@code place} to place them
 * again (compounds built before the shelves existed). Hung under the {@code sect <id>} node by
 * {@code WorldSimCommands} (which already requires permission 2); reads the {@code id} argument.
 */
public final class ScriptureShelfCommands {
    private ScriptureShelfCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("shelves")
                .executes(ctx -> list(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id")))
                .then(Commands.literal("place")
                        .executes(ctx -> place(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id"))));
    }

    private static int list(CommandSourceStack source, int sectId) {
        ServerLevel level = source.getServer().overworld();
        Optional<GateRealizations.Gate> gate = GateRealizations.get(level).gate(sectId);
        if (gate.isEmpty()) {
            source.sendFailure(WorldSimText.line("shelves.no_gate", sectId));
            return 0;
        }
        GateRealizations.Gate g = gate.get();
        List<ScriptureShelves.Survey> surveys = ScriptureShelves.survey(level, g.seed(), g.anchor(), g.variant());
        long found = surveys.stream().filter(s -> s.shelf() != null).count();
        source.sendSuccess(() -> WorldSimText.line("shelves.header", sectId, found, surveys.size()), false);
        for (ScriptureShelves.Survey s : surveys) {
            BlockPos site = s.site();
            if (s.shelf() == null) {
                source.sendSuccess(() -> WorldSimText.line("shelves.missing", site.getX(), site.getY(), site.getZ()),
                        false);
            } else {
                BlockPos p = s.shelf();
                source.sendSuccess(() -> WorldSimText.line("shelves.line", p.getX(), p.getY(), p.getZ(), s.sectId()),
                        false);
            }
        }
        return (int) found;
    }

    private static int place(CommandSourceStack source, int sectId) {
        ServerLevel level = source.getServer().overworld();
        Optional<GateRealizations.Gate> gate = GateRealizations.get(level).gate(sectId);
        if (gate.isEmpty()) {
            source.sendFailure(WorldSimText.line("shelves.no_gate", sectId));
            return 0;
        }
        GateRealizations.Gate g = gate.get();
        int total = SectCourtyard.scriptureShelfSites(g.seed(), g.anchor(), g.variant()).size();
        List<BlockPos> placed = ScriptureShelves.place(level, sectId, g.seed(), g.anchor(), g.variant());
        source.sendSuccess(() -> WorldSimText.line("shelves.placed", sectId, placed.size(), total), true);
        return placed.size();
    }
}
