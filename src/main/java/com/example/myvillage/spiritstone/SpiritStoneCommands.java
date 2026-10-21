package com.example.myvillage.spiritstone;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.math.BigInteger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Public player commands, never an administrative mint or an exchange on another player's behalf. */
public final class SpiritStoneCommands {
    private SpiritStoneCommands() { }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("灵石")
                .requires(s -> s.getEntity() instanceof ServerPlayer)
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("/灵石 余额；/灵石 兑换 中品|上品 [份数]；/灵石 拆分 中品|上品 [份数]。升阶每份换出一枚，拆分每份消耗一枚；默认一份。"), false);
                    return 1;
                })
                .then(Commands.literal("余额").executes(ctx -> balance(ctx.getSource())))
                .then(Commands.literal("兑换")
                        .then(action("中品", "low_grade", "medium_grade"))
                        .then(action("上品", "medium_grade", "high_grade")))
                .then(Commands.literal("拆分")
                        .then(action("中品", "medium_grade", "low_grade"))
                        .then(action("上品", "high_grade", "medium_grade"))));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> action(String name, String from, String to) {
        return Commands.literal(name).executes(ctx -> exchange(ctx.getSource(), from, to, 1))
                .then(Commands.argument("份数", IntegerArgumentType.integer(1))
                        .executes(ctx -> exchange(ctx.getSource(), from, to, IntegerArgumentType.getInteger(ctx, "份数"))));
    }
    private static int balance(CommandSourceStack source) throws CommandSyntaxException {
        var inventory = SpiritStoneSources.open(source.getPlayerOrException()).copies();
        for (var tier : SpiritStoneDenominations.get().tiers()) {
            var quantity = inventory.stream().map(s -> SpiritStoneStorage.count(s, item -> item.is(tier.tag())))
                    .reduce(BigInteger.ZERO, BigInteger::add);
            source.sendSuccess(() -> Component.literal("随身 ").append(BuiltInRegistries.ITEM.get(tier.item()).getDescription())
                    .append("：" + quantity + " 枚（含灵石袋；特殊组件物品不参与兑换）"), false);
        }
        return 1;
    }
    private static int exchange(CommandSourceStack source, String fromId, String toId, int batches) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        if (!player.isAlive() || player.isSpectator()) return 0;
        var rules = SpiritStoneDenominations.get();
        var from = rules.tier(ResourceLocation.fromNamespaceAndPath("myvillage", fromId));
        var to = rules.tier(ResourceLocation.fromNamespaceAndPath("myvillage", toId));
        var access = SpiritStoneSources.open(player);
        var plan = SpiritStoneExchange.plan(access.copies(), from, to, batches);
        if (!plan.ready()) {
            source.sendFailure(Component.literal(switch (plan.result()) {
                case "short" -> "可兑换的普通灵石不足；改名、特殊组件和外部同级物品不参与兑换。";
                case "full" -> "背包空间不足，请腾出空间或携带灵石袋；本次未扣款。";
                default -> "该品级的物品或标签配置不可兑换。";
            }));
            return 0;
        }
        if (!access.commit(plan.items())) {
            source.sendFailure(Component.literal("物品栏已变化，本次未兑换，请重试。")); return 0;
        }
        player.containerMenu.broadcastChanges();
        source.sendSuccess(() -> Component.literal("已兑换：" + plan.consumed() + " 枚 ")
                .append(BuiltInRegistries.ITEM.get(from.item()).getDescription()).append(" → " + plan.produced() + " 枚 ")
                .append(BuiltInRegistries.ITEM.get(to.item()).getDescription()), false);
        return 1;
    }
}
