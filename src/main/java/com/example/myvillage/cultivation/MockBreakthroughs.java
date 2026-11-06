package com.example.myvillage.cultivation;

import com.example.myvillage.cultivation.data.*;
import com.example.myvillage.cultivation.meditation.*;
import com.example.myvillage.stats.CultivationStatsRules;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.LoggerFactory;

/** 仅用于本轮数值试用的 MOCK 结算；真实引劫物、雷击、天气和存活试炼尚未运行。 */
public final class MockBreakthroughs {
    private MockBreakthroughs() { }
    public record Plan(CultivationProfile replacement, int plannedMainHits) { }

    /** 权限2的试用辅助；与 H 按钮共用资格校验和唯一结算入口。 */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> command() {
        var root = net.minecraft.commands.Commands.literal("mock").requires(s -> s.hasPermission(2));
        String[] names = {"heaven", "golden_low", "golden_middle", "golden_high"};
        for (int i = 0; i < names.length; i++) {
            boolean heaven = i == 0; int quality = heaven ? 4 : i;
            root.then(net.minecraft.commands.Commands.literal(names[i])
                    .then(net.minecraft.commands.Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player())
                            .executes(c -> request(net.minecraft.commands.arguments.EntityArgument.getPlayer(c,"target"), heaven, quality) ? 1 : 0)));
        }
        return root;
    }

    public static Plan plan(CultivationProfile current, RealmStageDefinition stage, boolean heaven, int quality) {
        if (!current.awakened() || !current.learnedTechniques().containsKey(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID))
            throw new IllegalArgumentException("需先觉醒并学会基础吐纳法");
        if (!stage.id().equals(current.stageId()) || stage.cultivationCap().isEmpty() || stage.advancement().isEmpty())
            throw new IllegalArgumentException("当前阶段没有可用突破");
        var next = stage.advancement().orElseThrow();
        if (current.cultivationProgress() < stage.cultivationCap().orElseThrow()
                || current.stability() < next.requiredStability())
            throw new IllegalArgumentException("修为或稳定度尚未达到突破要求");
        var rules = CultivationStatsRules.bundled();
        int hits;
        CultivationProfile result;
        if (heaven) {
            if (quality != 4 || !current.realmId().toString().equals("myvillage:qi_refining")
                    || !current.stageId().toString().equals("myvillage:qi_refining_9")
                    || !next.targetStage().toString().equals("myvillage:foundation_early"))
                throw new IllegalArgumentException("MOCK 天道筑基仅接受炼气九层");
            hits = rules.foundationHits(quality);
            result = current.withFoundationQuality(quality).withGoldenCoreQuality(0);
        } else {
            if (quality < 1 || quality > 3 || !current.realmId().toString().equals("myvillage:foundation_establishment")
                    || !current.stageId().toString().equals("myvillage:foundation_late")
                    || !next.targetStage().toString().equals("myvillage:golden_early"))
                throw new IllegalArgumentException("MOCK 结丹仅接受筑基后期和合法金丹品质");
            hits = rules.goldenHits(quality);
            result = current.withGoldenCoreQuality(quality);
        }
        result = result.withRealmAndStage(next.targetRealm(), next.targetStage())
                .withCultivationProgress(0).withStability(current.stability() / 2);
        return new Plan(result, hits);
    }

    public static boolean request(ServerPlayer player, boolean heaven, int quality) {
        if (!CultivationStatsRules.bundled().mockTribulation()) return reject(player, "MOCK 入口未启用");
        if (MeditationManager.active(player) || MeditationManager.mockEligibility(player) != MeditationStopReason.NONE)
            return reject(player, "请在能够正常修炼且没有活动会话时使用 MOCK 突破");
        var profile = CultivationService.getProfile(player);
        var realm = player.registryAccess().registryOrThrow(ModCultivationRegistries.REALMS).get(profile.realmId());
        var stage = realm == null ? null : realm.stages().stream().filter(s -> s.id().equals(profile.stageId())).findFirst().orElse(null);
        if (stage == null) return reject(player, "当前境界定义不可用");
        try {
            Plan plan = plan(profile, stage, heaven, quality);
            var target = player.registryAccess().registryOrThrow(ModCultivationRegistries.REALMS).get(plan.replacement.realmId());
            if (target == null || !target.hasStage(plan.replacement.stageId())) return reject(player, "目标境界定义不可用");
            var result = CultivationService.replaceProfile(player, plan.replacement);
            if (!result.success()) return reject(player, result.message());
            String name = heaven ? CultivationStats.foundationName(quality) : CultivationStats.goldenName(quality);
            player.displayClientMessage(Component.literal("MOCK 已结算：" + name + "。未执行 " + plan.plannedMainHits + " 次劫雷命中试炼。"), false);
            LoggerFactory.getLogger(MockBreakthroughs.class).info("MOCK_BREAKTHROUGH player={} target={} quality={} plannedMainHits={} actualHits=0",
                    player.getUUID(), plan.replacement.stageId(), quality, plan.plannedMainHits);
            return true;
        } catch (IllegalArgumentException exception) { return reject(player, exception.getMessage()); }
    }
    private static boolean reject(ServerPlayer player, String reason) {
        player.displayClientMessage(Component.literal("MOCK 突破未接受：" + reason), false);
        return false;
    }
}
