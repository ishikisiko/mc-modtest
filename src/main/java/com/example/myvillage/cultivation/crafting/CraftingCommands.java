package com.example.myvillage.cultivation.crafting;

import com.example.myvillage.cultivation.CultivationService;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Administrator diagnostics only; the ordinary player surface is read-only. */
public final class CraftingCommands {
    private CraftingCommands() { }
    public static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        var root = Commands.literal(name).requires(source -> source.hasPermission(2))
                .executes(c -> info(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> info(c.getSource(), EntityArgument.getPlayer(c, "target"))));
        for (String alias : new String[] {"set", "shezhi"}) {
            var target = Commands.argument("target", EntityArgument.player());
            for (String profession : new String[] {"alchemy", "liandan", "forging", "lianqi"}) {
                CraftingAptitudes.Profession canonical = profession.equals("alchemy") || profession.equals("liandan")
                        ? CraftingAptitudes.Profession.ALCHEMY : CraftingAptitudes.Profession.FORGING;
                target.then(Commands.literal(profession).then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                        .executes(c -> set(c.getSource(), EntityArgument.getPlayer(c, "target"), canonical,
                                IntegerArgumentType.getInteger(c, "value")))));
            }
            root.then(Commands.literal(alias).then(target));
        }
        return root;
    }
    public static JsonObject status(CraftingAptitudes state, CraftingRules rules) {
        JsonObject json = new JsonObject();
        json.addProperty("initialized", state.initialized());
        json.addProperty("alchemy", state.alchemy()); json.addProperty("forging", state.forging());
        json.addProperty("alchemy_quality_cap", state.qualityCap(CraftingAptitudes.Profession.ALCHEMY, rules));
        json.addProperty("forging_quality_cap", state.qualityCap(CraftingAptitudes.Profession.FORGING, rules));
        return json;
    }
    private static int info(CommandSourceStack source, ServerPlayer player) {
        String text = "CRAFTING " + status(CultivationService.craftingAptitudes(player), CraftingRules.get());
        source.sendSuccess(() -> Component.literal(text), false); return 1;
    }
    private static int set(CommandSourceStack source, ServerPlayer player, CraftingAptitudes.Profession profession, int value) {
        var result = CultivationService.setCraftingAptitude(player, profession, value);
        if (!result.success()) {
            source.sendFailure(Component.translatable("command.myvillage.crafting.not_ready")); return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.myvillage.crafting.updated", player.getGameProfile().getName()), true);
        return info(source, player);
    }
}
