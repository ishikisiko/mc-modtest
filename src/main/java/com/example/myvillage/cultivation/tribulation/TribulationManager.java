package com.example.myvillage.cultivation.tribulation;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.cultivation.time.CultivationTimeRuntime;
import com.example.myvillage.cultivation.tribulation.TribulationAttempt.Clock;
import com.example.myvillage.cultivation.tribulation.TribulationAttempt.Phase;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 唯一真实金丹雷劫执行器。管理员入口只替代起始输入，资格、伤害、代价与晋升完全相同。 */
public final class TribulationManager {
    public static final ResourceLocation RULES = ResourceLocation.parse("myvillage:tribulation/golden_core.json");
    private static final String SAVE_KEY = "myvillage:tribulation";
    private static final Logger LOGGER = LoggerFactory.getLogger(TribulationManager.class);
    private static final ThreadLocal<DamageOwner> DAMAGE = new ThreadLocal<>();
    private record DamageOwner(UUID owner, UUID attempt, DamageSource source) { }
    private static boolean registered;
    private TribulationManager() { }

    /** 在 StatsRuntime、丹盾、ShieldRuntime 之后注册，非致命保护最后观察真实余伤。 */
    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(TribulationManager::tick);
        NeoForge.EVENT_BUS.addListener(TribulationManager::login);
        NeoForge.EVENT_BUS.addListener(TribulationManager::logout);
        NeoForge.EVENT_BUS.addListener(TribulationManager::dimension);
        NeoForge.EVENT_BUS.addListener(TribulationManager::clonePlayer);
        NeoForge.EVENT_BUS.addListener(TribulationManager::stopping);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, TribulationManager::damage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, TribulationManager::death);
        NeoForge.EVENT_BUS.addListener(TribulationCommands::register);
    }

    /** 跨模块稳定接口：包括初次/重登准备期；飞剑必须在起飞入口检查。 */
    public static boolean isActive(ServerPlayer player) {
        var attempt = load(player);
        return attempt != null && attempt.active();
    }
    public static boolean recovering(ServerPlayer player) {
        var attempt = load(player);
        return attempt != null && attempt.clock().phase() == Phase.RECOVERING;
    }
    public static boolean source(CultivationProfile profile) {
        // 正式范围固定；数据包可调数值，不能借修改源阶段让其它旧突破经过此执行器。
        return profile.realmId().equals(ResourceLocation.parse("myvillage:foundation_establishment"))
                && profile.stageId().equals(ResourceLocation.parse("myvillage:foundation_late"));
    }
    public static TribulationAttempt load(ServerPlayer player) {
        String json = player.getPersistentData().getString(SAVE_KEY);
        if (json.isEmpty()) return null;
        try {
            var attempt = TribulationAttempt.decode(json);
            return attempt.owner().equals(player.getUUID()) ? attempt : null;
        } catch (RuntimeException exception) {
            // 不删坏档、不重抽；拒绝新起劫，保留原始值供定位。
            return null;
        }
    }
    private static void save(ServerPlayer player, TribulationAttempt attempt) {
        player.getPersistentData().putString(SAVE_KEY, attempt.encode());
    }
    public static boolean start(ServerPlayer player) {
        var previous = load(player);
        if (previous == null && player.getPersistentData().contains(SAVE_KEY)) return reject(player, "雷劫存档无法读取，已保留原始数据，请联系管理员。");
        if (previous != null && (previous.active() || previous.clock().phase() == Phase.RECOVERING))
            return reject(player, previous.clock().phase() == Phase.RECOVERING ? "正在调息，请稍后重新准备。" : "本次雷劫已在进行。");
        if (MeditationManager.active(player)) return reject(player, "请先收功，再选择露天场地引劫。");
        var profile = CultivationService.getProfile(player);
        if (!source(profile)) return reject(player, "仅筑基后期突破金丹时需要引劫；已金丹的旧档无需补劫。");
        if (!player.isAlive() || player.isRemoved() || !player.gameMode.isSurvival()
                || player.isPassenger() || player.isFallFlying() || player.getAbilities().flying
                || !player.onGround() || player.isSleeping() || player.isSwimming() || player.isUsingItem())
            return reject(player, "需存活、落地、非骑乘非飞行，处于生存或冒险状态。");
        if (profile.spiritualRoot().isEmpty() || !profile.learnedTechniques().containsKey(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID))
            return reject(player, "需先觉醒灵根并学会基础吐纳。");
        var time = CultivationTimeRuntime.statusFor(player);
        if (!time.realmResolved() || time.exhausted()) return reject(player, "寿元状态不满足引劫要求。");
        var realm = player.registryAccess().registryOrThrow(ModCultivationRegistries.REALMS).get(profile.realmId());
        var stage = realm == null ? null : realm.stages().stream().filter(s -> s.id().equals(profile.stageId())).findFirst().orElse(null);
        if (stage == null || stage.cultivationCap().isEmpty() || stage.advancement().isEmpty()) return reject(player, "当前突破定义不可用。");
        var advancement = stage.advancement().orElseThrow();
        long cap = stage.cultivationCap().orElseThrow();
        if (profile.cultivationProgress() < cap || profile.stability() < advancement.requiredStability())
            return reject(player, "引劫要求：修为 " + cap + "，稳定度 " + advancement.requiredStability() + "。");
        TribulationRules rules;
        try (var reader = player.getServer().getResourceManager().openAsReader(RULES)) {
            rules = TribulationRules.read(reader);
        } catch (Exception exception) {
            LOGGER.error("雷劫规则读取失败", exception);
            return reject(player, "雷劫规则无法读取，未开始。");
        }
        if (!rules.source(profile.realmId().toString(), profile.stageId().toString())
                || !rules.targetRealm().equals(advancement.targetRealm().toString())
                || !rules.targetStage().equals(advancement.targetStage().toString())
                || !rules.targetStage().equals("myvillage:golden_early")) return reject(player, "雷劫规则与正式金丹过渡不匹配。");
        var target = player.registryAccess().registryOrThrow(ModCultivationRegistries.REALMS).get(advancement.targetRealm());
        if (target == null || !target.containsStage(advancement.targetStage())) return reject(player, "金丹目标定义不可用。");
        var attempt = TribulationAttempt.create(UUID.randomUUID(), player.getUUID(), player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(), cap, advancement.requiredStability(), player.getMaxHealth(), rules);
        if (!loaded(player, attempt) || !openGround(player, attempt))
            return reject(player, "请选择露天场地：周围需有可落脚的活动空间，不能有屋顶、大片落差或未加载区块。");
        var plan = new ArrayList<TribulationAttempt.Mark>();
        for (var mark : attempt.plan()) {
            int height = player.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(mark.x()), (int) Math.floor(mark.z()));
            plan.add(new TribulationAttempt.Mark(mark.x(), height, mark.z()));
        }
        attempt = attempt.withPlan(plan);
        save(player, attempt);
        player.displayClientMessage(Component.literal("主动引劫（已满足修为 " + cap + "、稳定度 " + advancement.requiredStability() + "）：三轮六道主雷锁定自身，护甲、护盾与恢复有效；轮间余雷可离开标定落点躲避。半径 "
                + rules.arenaRadius() + " 格，出界 " + rules.boundaryGraceTicks() / 20.0 + " 秒失败。雷劫致命前保留低血，境界与修为保留、稳定度损失 "
                + rules.failureStabilityLoss() + "；已用物品不返还。准备 " + rules.preparationTicks() / 20.0 + " 秒，按收功键可取消。"), false);
        sync(player, attempt, -1);
        log(player, attempt, "start");
        return true;
    }
    private static boolean reject(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
        return false;
    }
    /** 普通收功仅取消准备；正式起雷后只可通过成功/失败或管理终止离开。 */
    public static boolean cancelPreparation(ServerPlayer player) {
        var a = load(player);
        if (a == null || !a.active()) return false;
        if (a.clock().phase() != Phase.PREPARING && !a.clock().paused()) {
            reject(player, "已引动雷劫，不能收功取消；可在场内移动、用符、服丹与防御。");
            return true;
        }
        if (a.clock().elapsed() > 0 || a.clock().nextStrike() > 0) {
            reject(player, "重登准备将继续同一次雷劫，不能取消已开始的雷序。");
            return true;
        }
        finish(player, a, "准备已取消", false);
        return true;
    }
    public static boolean stop(ServerPlayer player, String reason) {
        var a = load(player);
        if (a == null || !a.active()) return false;
        finish(player, a, reason, false);
        return true;
    }
    private static void finish(ServerPlayer player, TribulationAttempt a, String reason, boolean penalty) {
        if (!a.active()) return;
        var failure = TribulationSettlement.failure(a, CultivationService.getProfile(player), reason, penalty);
        var ended = failure.attempt();
        // 终态与 profile 属于同一玩家 NBT，主线程中先做幂等标志，后服务替换；不创建第二个 SavedData 提交窗口。
        save(player, ended);
        if (penalty) {
            var profile = CultivationService.getProfile(player);
            if (a.rules().source(profile.realmId().toString(), profile.stageId().toString())) {
                var result = CultivationService.replaceProfile(player, failure.profile());
                if (!result.success()) LOGGER.error("雷劫失败稳定度提交失败 attempt={} {}", a.id(), result.message());
            }
        }
        sync(player, ended, -1);
        player.displayClientMessage(Component.literal(reason + (penalty ? "；境界修为保留，稳定度仅扣一次，短暂调息后可重试。" : "；无晋升、无奖励。")), false);
        log(player, ended, "end");
    }
    private static void tick(ServerTickEvent.Post event) {
        for (var player : event.getServer().getPlayerList().getPlayers()) tickPlayer(player);
    }
    private static void tickPlayer(ServerPlayer player) {
        var a = load(player);
        if (a == null) return;
        Clock c = a.clock();
        if (c.phase() == Phase.RECOVERING) {
            a = a.advance(true);
            int left = a.clock().recovery();
            save(player, a);
            if (left % a.rules().statusIntervalTicks() == 0) sync(player, a, -1);
            return;
        }
        if (!a.active()) return;
        var profile = CultivationService.getProfile(player);
        if (!player.isAlive() || player.isRemoved()) { finish(player, a, "外界死亡终止", false); return; }
        if (!a.dimension().equals(player.level().dimension().location().toString())) { finish(player, a, "换维度终止", false); return; }
        if (!a.rules().source(profile.realmId().toString(), profile.stageId().toString())) { finish(player, a, "境界已改变，管理终止", false); return; }
        if (!player.gameMode.isSurvival()) { finish(player, a, "游戏模式改变，管理终止", false); return; }
        if (player.isPassenger() || player.isFallFlying() || player.getAbilities().flying) { finish(player, a, "骑乘或飞行终止雷劫", c.phase() == Phase.ACTIVE); return; }
        if (profile.spiritualRoot().isEmpty() || !profile.learnedTechniques().containsKey(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                || profile.cultivationProgress() < a.cultivationCap() || profile.stability() < a.requiredStability()) {
            finish(player, a, "突破资格已改变，终止", false); return;
        }
        // 玩家自身在线加载时先计出界宽限，不能靠远离到劫场卸载来无限逃劫。
        int outside = a.contains(player.getX(), player.getY(), player.getZ()) ? 0 : c.outside() + 1;
        if (outside >= a.rules().boundaryGraceTicks()) { finish(player, a, "离开劫场过久，渡劫失败", c.phase() == Phase.ACTIVE); return; }
        // 只读已加载区块；未知场地不推进雷序，不强加载，也不补离线时间。
        if (!loaded(player, a)) {
            a = a.withClock(new Clock(c.phase(), c.preparation(), c.elapsed(), c.nextStrike(), outside, c.recovery(), c.paused(), c.result(), c.penaltyApplied())).pause();
            save(player, a);
            if (!c.paused() || outside % a.rules().statusIntervalTicks() == 0) sync(player, a, -1);
            return;
        }
        if (c.paused() || c.preparation() > 0) {
            a = a.advance(outside == 0);
            int preparation = a.clock().preparation();
            save(player, a);
            if (preparation % a.rules().statusIntervalTicks() == 0) sync(player, a, -1);
            return;
        }
        a = a.advance(outside == 0);
        int elapsed = a.clock().elapsed();
        save(player, a);
        if (a.due()) {
            int index = c.nextStrike();
            // 在 hurt 前持久化消费序号；Pre 失败保护可以结束本尝试，回到此处不得覆盖该终态。
            a = a.consumeStrike();
            save(player, a);
            sync(player, a, index);
            strike(player, a, index);
            a = load(player);
            if (a == null || !a.active()) return;
        }
        if (a.readyToSucceed()) {
            success(player, a); return;
        }
        if (elapsed % a.rules().statusIntervalTicks() == 0) sync(player, a, -1);
    }
    private static void strike(ServerPlayer player, TribulationAttempt a, int index) {
        var rule = a.rules().strikes().get(index);
        var mark = rule.main() ? new TribulationAttempt.Mark(player.getX(), player.getY(), player.getZ()) : a.plan().get(index);
        player.serverLevel().playSound(null, mark.x(), mark.y(), mark.z(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0f, rule.main() ? 1.1f : 1.4f);
        if (!a.canHit(player.getUUID(), index, player.getX(), player.getY(), player.getZ())) return;
        var key = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse(rule.main() ? "myvillage:tribulation_main" : "myvillage:tribulation_echo"));
        DamageSource source = new DamageSource(player.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key));
        DAMAGE.set(new DamageOwner(player.getUUID(), a.id(), source));
        try {
            // 不手算护甲/抗性/丹盾/黄心；唯一一次原版 hurt。主雷仅跳过身法无敌窗口。
            player.hurt(source, a.healthBasis() * rule.damageFraction());
        } finally { DAMAGE.remove(); }
        log(player, a, "strike=" + index);
    }
    private static void damage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DamageOwner owner = DAMAGE.get();
        var a = load(player);
        if (owner == null || a == null || !a.active() || !owner.owner().equals(player.getUUID()) || !owner.attempt().equals(a.id()) || event.getSource() != owner.source()) return;
        float absorption = player.getAbsorptionAmount();
        if (!TribulationDamage.lethal(event.getNewDamage(), player.getHealth(), absorption)) return;
        float capped = TribulationDamage.nonLethal(player.getHealth(), absorption, a.rules().survivalHealth());
        event.setNewDamage(capped);
        finish(player, a, "雷劫承受不住，已在致命前散去", true);
    }
    private static void success(ServerPlayer player, TribulationAttempt a) {
        var current = CultivationService.getProfile(player);
        if (!a.rules().source(current.realmId().toString(), current.stageId().toString())
                || current.cultivationProgress() < a.cultivationCap() || current.stability() < a.requiredStability()) {
            finish(player, a, "最终资格改变，终止", false); return;
        }
        var replacement = TribulationSettlement.success(a, current);
        var ended = a.withClock(new Clock(Phase.SUCCEEDED, 0, a.clock().elapsed(), a.clock().nextStrike(), 0, 0, false, "六雷已过，金丹初成", false));
        save(player, ended);
        var result = CultivationService.replaceProfile(player, replacement);
        if (!result.success()) {
            // 数据包移除目标等失败显式管理终止，不逐 tick 重试、不重放雷、不给奖励。
            save(player, a);
            LOGGER.error("雷劫晋升提交失败 attempt={} {}", a.id(), result.message());
            finish(player, a, "金丹结算定义不可用，管理终止", false);
            return;
        }
        sync(player, ended, -1);
        player.displayClientMessage(Component.literal("六雷已过，金丹初成。修为归零，稳定度减半；生命与灵力不补满。"), false);
        log(player, ended, "success");
    }
    private static boolean loaded(ServerPlayer player, TribulationAttempt a) {
        int r = (int) Math.ceil(a.rules().arenaRadius());
        for (int x = ((int) Math.floor(a.x()) - r) >> 4; x <= (((int) Math.floor(a.x()) + r) >> 4); x++)
            for (int z = ((int) Math.floor(a.z()) - r) >> 4; z <= (((int) Math.floor(a.z()) + r) >> 4); z++)
                if (!player.serverLevel().hasChunk(x, z)) return false;
        return true;
    }
    private static boolean openGround(ServerPlayer player, TribulationAttempt a) {
        var level = player.serverLevel();
        if (!level.canSeeSky(player.blockPosition()) || !level.dimensionType().hasSkyLight()) return false;
        int spread = (int) Math.floor(a.rules().arenaRadius() / 2);
        for (int dx : new int[]{-spread, 0, spread}) for (int dz : new int[]{-spread, 0, spread}) {
            int x = (int) Math.floor(a.x()) + dx, z = (int) Math.floor(a.z()) + dz;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - a.y()) > a.rules().verticalRange() / 2) return false;
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.canSeeSky(pos) || !level.getFluidState(pos).isEmpty()
                    || !level.getFluidState(pos.below()).isEmpty() || level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) return false;
            for (int dy = 0; dy < 3; dy++) if (!level.getBlockState(pos.above(dy)).getCollisionShape(level, pos.above(dy)).isEmpty()) return false;
        }
        return true;
    }
    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var a = load(player);
            if (a != null) {
                if (a.active()) { a = a.pause(); save(player, a); }
                sync(player, a, -1);
            }
        }
    }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var a = load(player);
            if (a != null && a.active()) save(player, a.pause());
        }
    }
    private static void stopping(ServerStoppingEvent event) {
        for (var player : event.getServer().getPlayerList().getPlayers()) {
            var a = load(player);
            if (a != null && a.active()) save(player, a.pause());
        }
        DAMAGE.remove();
    }
    private static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player, "换维度终止");
    }
    private static void clonePlayer(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var a = event.getOriginal().getPersistentData().getString(SAVE_KEY);
            if (!a.isEmpty()) {
                try { save(player, TribulationAttempt.decode(a).fail(event.isWasDeath() ? "真实死亡终止" : "跨维度换体终止", false)); }
                catch (RuntimeException exception) { player.getPersistentData().putString(SAVE_KEY, a); }
            }
        }
    }
    private static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player, "真实外界死亡终止");
    }
    /** schema=1；命令行严格使用前缀+单行JSON，运行日志可另附 event。 */
    public static com.google.gson.JsonObject statusJson(ServerPlayer player) {
        var a = load(player);
        var out = statusJson(a);
        out.addProperty("player", player.getUUID().toString());
        out.addProperty("player_name", player.getScoreboardName());
        out.addProperty("unreadable", a == null && player.getPersistentData().contains(SAVE_KEY));
        return out;
    }
    public static com.google.gson.JsonObject statusJson(TribulationAttempt a) {
        var out = new com.google.gson.JsonObject();
        out.addProperty("schema", 1);
        if (a == null) { out.add("attempt_id", com.google.gson.JsonNull.INSTANCE); out.addProperty("phase", "NONE"); return out; }
        out.addProperty("attempt_id", a.id().toString()); out.addProperty("owner", a.owner().toString());
        out.addProperty("phase", a.clock().phase().name()); out.addProperty("elapsed", a.clock().elapsed());
        out.addProperty("next_strike", a.clock().nextStrike()); out.addProperty("main_completed", mainCompleted(a));
        out.addProperty("remaining", a.rules().durationTicks() - a.clock().elapsed());
        out.addProperty("preparation", a.clock().preparation()); out.addProperty("recovery", a.clock().recovery());
        out.addProperty("source_realm", a.rules().sourceRealm()); out.addProperty("source_stage", a.rules().sourceStage());
        out.addProperty("target_realm", a.rules().targetRealm()); out.addProperty("target_stage", a.rules().targetStage());
        var center = new com.google.gson.JsonObject(); center.addProperty("x", a.x()); center.addProperty("y", a.y()); center.addProperty("z", a.z());
        out.add("center", center); out.addProperty("dimension", a.dimension()); out.addProperty("radius", a.rules().arenaRadius());
        out.addProperty("paused", a.clock().paused()); out.addProperty("outside_ticks", a.clock().outside());
        out.addProperty("penalty_applied", a.clock().penaltyApplied()); out.addProperty("result", a.clock().result());
        out.addProperty("rules_sha256", hash(TribulationRules.JSON.toJson(a.rules())));
        out.addProperty("plan_sha256", hash(TribulationRules.JSON.toJson(a.plan())));
        out.addProperty("health_basis", a.healthBasis()); out.addProperty("cultivation_cap", a.cultivationCap());
        out.addProperty("required_stability", a.requiredStability());
        return out;
    }
    private static String hash(String text) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static String status(ServerPlayer player) { return "TRIBULATION_STATUS " + statusJson(player); }
    static int mainCompleted(TribulationAttempt a) { return (int) a.rules().strikes().subList(0, a.clock().nextStrike()).stream().filter(TribulationRules.Strike::main).count(); }
    private static void sync(ServerPlayer player, TribulationAttempt a, int impact) {
        try { PacketDistributor.sendToPlayer(player, TribulationStatusPayload.of(a, impact, player.getX(), player.getY(), player.getZ())); }
        catch (RuntimeException exception) { LOGGER.warn("雷劫状态已提交，但客户端同步失败 attempt={}", a.id(), exception); }
    }
    private static void log(ServerPlayer player, TribulationAttempt a, String event) {
        LOGGER.info("{} event={}", status(player), event);
    }
}
