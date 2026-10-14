package com.example.myvillage.stats.runtime;

import com.example.myvillage.stats.DamageMath;
import com.example.myvillage.stats.DamageType;
import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.StatsRules;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Permission-2 diagnostics. Preview evaluates pure numbers and never hurts or mutates an entity. */
public final class StatsCommands {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();

    private StatsCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        var rules = StatsRules.bundled();
        return Commands.literal("stats").requires(source -> source.hasPermission(2))
                .then(Commands.literal("info")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> info(context.getSource(),
                                        EntityArgument.getEntities(context, "targets")))))
                .then(Commands.literal("trace")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> trace(context.getSource(),
                                        EntityArgument.getEntity(context, "target")))))
                .then(Commands.literal("growth")
                        .executes(context -> growth(context.getSource()))
                        .then(Commands.argument("realm", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        rules.realms().stream().map(StatsRules.RealmDefinition::id), builder))
                                .then(Commands.argument("stage", IntegerArgumentType.integer(0))
                                        .executes(context -> growth(context.getSource(),
                                                ResourceLocationArgument.getId(context, "realm").toString(),
                                                IntegerArgumentType.getInteger(context, "stage"))))))
                .then(damageCommand(rules));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> damageCommand(StatsRules rules) {
        var penetration = Commands.argument("penetration", DoubleArgumentType.doubleArg(0, rules.limits().maxPenetration()))
                .executes(context -> preview(context.getSource(),
                        DoubleArgumentType.getDouble(context, "base"),
                        DoubleArgumentType.getDouble(context, "flat"),
                        DoubleArgumentType.getDouble(context, "move"),
                        DoubleArgumentType.getDouble(context, "bonus"),
                        DoubleArgumentType.getDouble(context, "total"),
                        DoubleArgumentType.getDouble(context, "post_vanilla"),
                        StringArgumentType.getString(context, "type"),
                        DoubleArgumentType.getDouble(context, "resistance"),
                        DoubleArgumentType.getDouble(context, "penetration")));
        var resistance = Commands.argument("resistance", DoubleArgumentType.doubleArg(
                rules.limits().minResistance(), rules.limits().maxResistance())).then(penetration);
        var type = Commands.argument("type", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        rules.damageTypes().stream().map(DamageType::id), builder)).then(resistance);
        var postVanilla = Commands.argument("post_vanilla", DoubleArgumentType.doubleArg(0, rules.limits().maxDamage())).then(type);
        var total = Commands.argument("total", DoubleArgumentType.doubleArg(0, rules.limits().maxTotalMultiplier())).then(postVanilla);
        var bonus = Commands.argument("bonus", DoubleArgumentType.doubleArg(0, rules.limits().maxBonus())).then(total);
        var move = Commands.argument("move", DoubleArgumentType.doubleArg(0, rules.limits().maxMoveMultiplier())).then(bonus);
        var flat = Commands.argument("flat", DoubleArgumentType.doubleArg(0, rules.limits().maxFlatDamage())).then(move);
        var base = Commands.argument("base", DoubleArgumentType.doubleArg(0, rules.limits().maxDamage())).then(flat);
        return Commands.literal("damage").then(Commands.literal("preview").then(base));
    }

    private static int info(CommandSourceStack source, Collection<? extends Entity> targets) {
        int shown = 0;
        for (Entity entity : targets) {
            if (shown >= 64) break;
            if (entity instanceof LivingEntity living) {
                emit(source, "STATS_INFO", StatsRuntime.info(living));
                shown++;
            }
        }
        if (shown == 0) {
            source.sendFailure(Component.literal("No living entity selected"));
        }
        return shown;
    }

    private static int trace(CommandSourceStack source, Entity target) {
        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.literal("Target is not a living entity"));
            return 0;
        }
        var trace = StatsRuntime.lastTrace(living);
        if (trace.isEmpty()) {
            emit(source, "STATS_TRACE", Map.of("entityId", target.getUUID().toString(),
                    "status", "not_observed", "ruleHash", StatsRules.bundled().ruleHash()));
            return 0;
        }
        var result = JSON.toJsonTree(trace.get()).getAsJsonObject();
        result.addProperty("entityId", target.getUUID().toString());
        result.addProperty("status", trace.get().actualHealthLoss() == null ? "pre_health" : "observed");
        emit(source, "STATS_TRACE", result);
        return 1;
    }

    private static int growth(CommandSourceStack source) {
        int count = 0;
        for (var realm : StatsRules.bundled().realms()) {
            for (int stage = 0; stage < realm.stages(); stage++) {
                emitGrowth(source, new RealmKey(realm.id(), stage));
                count++;
            }
        }
        return count;
    }

    private static int growth(CommandSourceStack source, String realm, int stage) {
        try {
            emitGrowth(source, StatsRules.bundled().realmKey(realm, stage));
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static void emitGrowth(CommandSourceStack source, RealmKey key) {
        StatsRules rules = StatsRules.bundled();
        emit(source, "STATS_GROWTH", Map.of("realm", key.realmId(), "stage", key.stage(),
                "factor", rules.growth(key), "ruleHash", rules.ruleHash()));
    }

    private static int preview(CommandSourceStack source, double base, double flat, double move,
                               double bonus, double total, double postVanilla,
                               String type, double resistance, double penetration) {
        try {
            StatsRules rules = StatsRules.bundled();
            DamageType channel = DamageType.fromId(type);
            emit(source, "STATS_PREVIEW", Map.of("type", channel.id(), "outgoing",
                    DamageMath.calculate(base, flat, move, List.of(bonus), total, rules), "mitigation",
                    DamageMath.mitigate(postVanilla, resistance, penetration, rules), "ruleHash", rules.ruleHash()));
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static void emit(CommandSourceStack source, String prefix, Object value) {
        String line = prefix + " " + JSON.toJson(value);
        source.sendSuccess(() -> Component.literal(line), false);
    }
}
