package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.CultivationService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class FateCommands {
    private FateCommands() { }
    public static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        var root = Commands.literal(name).executes(c -> info(c.getSource(), c.getSource().getPlayerOrException()));
        for (String alias : new String[]{"status", "chakan"}) root.then(Commands.literal(alias)
                .executes(c -> info(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                        .executes(c -> info(c.getSource(), EntityArgument.getPlayer(c, "player")))));
        for (String alias : new String[]{"roll", "chongzhi"}) root.then(Commands.literal(alias)
                .executes(c -> act(c.getSource().getPlayerOrException(), 0, 0))
                .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                        .executes(c -> diagnostic(c.getSource(), EntityArgument.getPlayer(c, "player"), 0, 0))));
        for (String alias : new String[]{"lock", "suoding"}) root.then(Commands.literal(alias)
                .then(Commands.argument("slot", IntegerArgumentType.integer(0, 6))
                        .executes(c -> act(c.getSource().getPlayerOrException(), 1, slot(IntegerArgumentType.getInteger(c, "slot"))))));
        for (String alias : new String[]{"confirm", "queren"}) root.then(Commands.literal(alias)
                .then(confirmMask(false))
                .then(Commands.argument("player", EntityArgument.player()).requires(source -> source.hasPermission(2))
                        .then(confirmMask(true))));
        for (String alias : new String[]{"reset", "qingchu"}) root.then(Commands.literal(alias).requires(s -> s.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                    FateService.forget(p);
                    // Advance revision, so previously queued confirmations cannot apply to the reset draft.
                    FateState old = FateService.state(p);
                    FateState fresh = old.resetChoices();
                    CultivationService.replaceFates(p, fresh, true);
                    return info(c.getSource(), p);
                })));
        return root;
    }
    private static RequiredArgumentBuilder<CommandSourceStack, Integer> confirmMask(boolean diagnostic) {
        return Commands.argument("mask", IntegerArgumentType.integer(0, 63))
                .executes(c -> confirm(c, diagnostic, false, false))
                .then(Commands.literal("weapon").then(Commands.argument("item", ResourceLocationArgument.id())
                        .executes(c -> confirm(c, diagnostic, false, true))))
                .then(Commands.argument("school", ResourceLocationArgument.id())
                        .executes(c -> confirm(c, diagnostic, true, false))
                        .then(Commands.literal("weapon").then(Commands.argument("item", ResourceLocationArgument.id())
                                .executes(c -> confirm(c, diagnostic, true, true)))));
    }

    private static int confirm(CommandContext<CommandSourceStack> context, boolean diagnostic, boolean schoolChoice,
            boolean weaponChoice) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var source = context.getSource();
        var player = diagnostic ? EntityArgument.getPlayer(context, "player") : source.getPlayerOrException();
        String school = schoolChoice ? ResourceLocationArgument.getId(context, "school").toString() : "";
        int weapon = weaponChoice ? FateReworkRules.get().weapons().indexOf(ResourceLocationArgument.getId(context, "item")) : -1;
        if (weaponChoice && weapon < 0) {
            source.sendFailure(Component.translatable("fate.yuanxian.error.weapon"));
            return 0;
        }
        long revision = FateService.state(player).revision();
        int mask = IntegerArgumentType.getInteger(context, "mask");
        boolean result = diagnostic ? FateService.diagnosticIntent(source, player, 2, revision, mask, school, weapon)
                : FateService.intent(player, 2, revision, mask, SteleAwakeningSessions.currentToken(player), school, weapon);
        return result ? 1 : 0;
    }
    private static int slot(int value) { return value == 0 ? 255 : value - 1; }
    private static int act(ServerPlayer p, int action, int value) {
        return act(p,action,value,"");
    }
    private static int act(ServerPlayer p,int action,int value,String school) {
        return FateService.intent(p, action, FateService.state(p).revision(), value,
                SteleAwakeningSessions.currentToken(p),school) ? 1 : 0;
    }
    private static int diagnostic(CommandSourceStack source, ServerPlayer p, int action, int value) {
        return diagnostic(source,p,action,value,"");
    }
    private static int diagnostic(CommandSourceStack source,ServerPlayer p,int action,int value,String school) {
        return FateService.diagnosticIntent(source,p,action,FateService.state(p).revision(),value,school)?1:0;
    }
    private static int info(CommandSourceStack source, ServerPlayer p) {
        FateState s = FateService.state(p);
        var json = FateState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, s).getOrThrow().getAsJsonObject();
        json.addProperty("player", p.getGameProfile().getName());
        json.addProperty("budget", FateService.budget());
        json.addProperty("sword_stacks", SwordObsessionRuntime.stacks(p));
        json.add("spirit_body",new com.google.gson.Gson().toJsonTree(SpiritBodyRuntime.status(p)));
        json.addProperty("now", p.getServer().overworld().getGameTime());
        json.addProperty("school",SchoolInsightRuntime.state(p).school());
        long token = SteleAwakeningSessions.currentToken(p);
        json.addProperty("awakening_session", SteleAwakeningSessions.valid(p, token) ? token : 0);
        String text = "FATES " + json;
        source.sendSuccess(() -> Component.literal(text), false); return 1;
    }
}
