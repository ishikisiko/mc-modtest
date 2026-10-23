package com.example.myvillage.combat.foundation;

import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.fate.ability.FateAbilityRuntime;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** 仅管理员的有界 fixture 与服务器读回；不提供玩家施法、扣费或冷却改写入口。 */
public final class FoundationCommands {
    private static final ResourceLocation WOOD = ResourceLocation.parse("myvillage:wood_root");
    private static final ResourceLocation FIXTURE_ROOT = ResourceLocation.parse("myvillage:fixture/root");
    private static final ResourceLocation FIXTURE_HEAL = ResourceLocation.parse("myvillage:fixture/healing_field");
    private static final SimpleCommandExceptionType INVALID = new SimpleCommandExceptionType(
            Component.literal("基础验收 fixture 被拒绝：目标必须为存活实体，数量不超过64，参数必须有界且来源有效"));

    private FoundationCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var foundation = Commands.literal("foundation").requires(source -> source.hasPermission(2));
        foundation.then(Commands.literal("status").then(Commands.argument("targets", EntityArgument.entities())
                .executes(context -> status(context, false))));
        foundation.then(Commands.literal("clear").then(Commands.argument("targets", EntityArgument.entities())
                .executes(context -> status(context, true))));
        foundation.then(Commands.literal("cleanse").then(Commands.argument("targets", EntityArgument.entities())
                .executes(context -> {
                    int count = 0;
                    for (var target : targets(context)) count += ControlService.cleanse(target);
                    final int removed = count;
                    context.getSource().sendSuccess(() -> Component.literal("FOUNDATION_FIXTURE 净化来源=" + removed), false);
                    return 1;
                })));
        var grant = Commands.literal("grant");
        grant.then(Commands.literal("shield").then(Commands.argument("targets", EntityArgument.entities())
                .then(Commands.argument("source", ResourceLocationArgument.id())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(.001, 1000000))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 72000))
                                        .executes(context -> grant(context, false)))))));
        grant.then(Commands.literal("healing_modifier").then(Commands.argument("targets", EntityArgument.entities())
                .then(Commands.argument("source", ResourceLocationArgument.id())
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(-1, 16))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 72000))
                                        .executes(context -> grant(context, true)))))));
        grant.then(Commands.literal("root").then(Commands.argument("caster", EntityArgument.player())
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 72000))
                                .executes(context -> {
                                    var caster = EntityArgument.getPlayer(context, "caster");
                                    int ticks = IntegerArgumentType.getInteger(context, "ticks");
                                    int accepted = 0;
                                    for (var target : targets(context))
                                        if (ControlService.root(caster, target, FIXTURE_ROOT, ticks)) accepted++;
                                    return fixtureReply(context, accepted);
                                })))));
        grant.then(Commands.literal("poise").then(Commands.argument("targets", EntityArgument.entities())
                .then(Commands.argument("source", ResourceLocationArgument.id())
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 72000))
                                .executes(context -> {
                                    var source = ResourceLocationArgument.getId(context, "source");
                                    int ticks = IntegerArgumentType.getInteger(context, "ticks");
                                    int accepted = 0;
                                    for (var target : targets(context))
                                        if (ControlService.grantPoise(target, source, ticks)) accepted++;
                                    return fixtureReply(context, accepted);
                                })))));
        grant.then(Commands.literal("healing_field").then(Commands.argument("caster", EntityArgument.player())
                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(.01, 16))
                        .then(Commands.argument("healing", DoubleArgumentType.doubleArg(.001, 10000))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1200))
                                        .then(Commands.argument("interval", IntegerArgumentType.integer(1, 1200))
                                                .executes(FoundationCommands::healingField)))))));
        foundation.then(grant);
        dispatcher.register(Commands.literal("myvillage").then(foundation));
    }

    private static List<LivingEntity> targets(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var entities = EntityArgument.getEntities(context, "targets");
        if (!validCount(entities.size())) throw INVALID.create();
        List<LivingEntity> result = new ArrayList<>();
        for (var entity : entities) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) throw INVALID.create();
            result.add(living);
        }
        return result;
    }

    /** 命令安全边界，与玩法调参数值无关。 */
    static boolean validCount(int count) { return count > 0 && count <= 64; }
    static boolean validFixture(double amount, int ticks, boolean healing) {
        return Double.isFinite(amount) && ticks > 0 && ticks <= 72000
                && (healing ? amount >= -1 && amount <= 16 : amount >= .001 && amount <= 1000000);
    }

    private static int grant(CommandContext<CommandSourceStack> context, boolean healing) throws CommandSyntaxException {
        double amount = DoubleArgumentType.getDouble(context, "amount");
        int ticks = IntegerArgumentType.getInteger(context, "ticks");
        if (!validFixture(amount, ticks, healing)) throw INVALID.create();
        var source = ResourceLocationArgument.getId(context, "source");
        int accepted = 0;
        for (var target : targets(context)) {
            boolean applied = healing ? HealingRuntime.modifier(target, source, amount, ticks)
                    : ShieldRuntime.grant(target, source, (float) amount, ticks);
            if (applied) accepted++;
        }
        return fixtureReply(context, accepted);
    }

    private static int healingField(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer caster = EntityArgument.getPlayer(context, "caster");
        var spec = new FieldSpec(FIXTURE_HEAL, FIXTURE_HEAL, FieldSpec.Anchor.FOLLOW_SOURCE, null,
                DoubleArgumentType.getDouble(context, "radius"), IntegerArgumentType.getInteger(context, "ticks"),
                IntegerArgumentType.getInteger(context, "interval"), 16, FieldSpec.Effect.HEAL, 0,
                (float) DoubleArgumentType.getDouble(context, "healing"), false, FieldSpec.Targeting.ALLIES);
        return fixtureReply(context, FieldRuntime.spawn(caster, spec) ? 1 : 0);
    }

    private static int fixtureReply(CommandContext<CommandSourceStack> context, int accepted) {
        context.getSource().sendSuccess(() -> Component.literal("FOUNDATION_FIXTURE 接受目标=" + accepted), false);
        return accepted;
    }

    private static int status(CommandContext<CommandSourceStack> context, boolean clear) throws CommandSyntaxException {
        var selected = targets(context);
        for (var target : selected) {
            if (clear) {
                ShieldRuntime.clear(target);
                HealingRuntime.clear(target);
                ControlService.forget(target);
                FieldRuntime.forget(context.getSource().getServer(), target.getUUID());
            }
            JsonObject state = new JsonObject();
            state.addProperty("uuid", target.getUUID().toString());
            state.addProperty("name", target.getName().getString());
            state.addProperty("now", context.getSource().getServer().overworld().getGameTime());
            state.addProperty("health", target.getHealth());
            state.addProperty("max_health", target.getMaxHealth());
            state.addProperty("rooted", ControlService.isRooted(target));
            state.addProperty("root_remaining", ControlService.rootRemainingTicks(target));
            state.addProperty("slow_remaining", ControlService.slowRemainingTicks(target));
            state.addProperty("poised", ControlService.isPoised(target));
            state.addProperty("shield", ShieldRuntime.total(target));
            JsonArray shields = new JsonArray();
            for (var entry : ShieldRuntime.sources(target)) {
                JsonObject row = new JsonObject();
                row.addProperty("source", entry.source().toString());
                row.addProperty("amount", entry.amount());
                row.addProperty("expires_at", entry.expiresAt());
                shields.add(row);
            }
            state.add("shields", shields);
            if (target instanceof ServerPlayer player) {
                state.addProperty("qi", CultivationService.getProfile(player).currentSpiritualPower());
                state.addProperty("qi_cap", CultivationService.spiritualPowerCap(player));
                JsonObject ability = new JsonObject();
                ability.addProperty("eligible", FateAbilityRuntime.available(player, WOOD));
                ability.addProperty("cost", FateAbilityRuntime.cost(player, WOOD));
                ability.addProperty("cooldown_remaining", FateAbilityRuntime.cooldownRemaining(player, WOOD));
                state.add("wood_root", ability);
                JsonArray fields = new JsonArray();
                for (var entry : FieldRuntime.snapshot(player)) {
                    JsonObject row = new JsonObject();
                    row.addProperty("id", entry.instanceId());
                    row.addProperty("kind", entry.kind().toString());
                    row.addProperty("effect", entry.effect().toString());
                    row.addProperty("expires_at", entry.expiresAt());
                    fields.add(row);
                }
                state.add("fields", fields);
            }
            context.getSource().sendSuccess(() -> Component.literal("FOUNDATION " + state), false);
        }
        return selected.size();
    }
}
