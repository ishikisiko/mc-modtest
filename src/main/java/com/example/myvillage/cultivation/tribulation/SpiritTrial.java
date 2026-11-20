package com.example.myvillage.cultivation.tribulation;

import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.combat.session.CombatStopReason;
import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.CultivationRecovery;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.data.AdvancementKind;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import com.example.myvillage.cultivation.meditation.MeditationState;
import com.example.myvillage.cultivation.meditation.MeditationStatus;
import com.example.myvillage.cultivation.meditation.MeditationStopReason;
import com.example.myvillage.cultivation.network.SpiritTrialIntentPayload;
import com.example.myvillage.cultivation.network.SpiritTrialStatusPayload;
import com.example.myvillage.cultivation.network.SpiritTrialStatusPayload.Action;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-owned lightning sequence. The attempt and the spent seed are saved with the same player. */
public final class SpiritTrial {
    private static final String SAVE_KEY = "yuanxian_spirit_trial";
    private static final ResourceLocation NASCENT_LATE = ResourceLocation.parse("yuanxian:nascent_late");
    private static final ResourceLocation SPIRIT_EARLY = ResourceLocation.parse("yuanxian:spirit_early");
    private static final Logger LOGGER = LoggerFactory.getLogger(SpiritTrial.class);
    private static final ThreadLocal<StrikeReceipt> STRIKE = new ThreadLocal<>();

    private SpiritTrial() {}

    public enum Route {
        LOW("spirit_low", "下品化神"),
        MIDDLE("spirit_middle", "中品化神"),
        HIGH("spirit_high", "上品化神");

        private final String id;
        private final String title;
        Route(String id, String title) { this.id = id; this.title = title; }
        public String id() { return id; }
        public String title() { return title; }
        public int quality() { return ordinal() + 1; }
    }

    private enum Phase { LIGHTNING, READY, UNION }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SpiritTrial::observeStrike);
    }

    public static boolean source(CultivationProfile profile) {
        return profile.realmId().equals(ModCultivationRegistries.NASCENT_SOUL_REALM_ID)
                && profile.stageId().equals(NASCENT_LATE);
    }

    public static boolean canChoose(RegistryAccess access, CultivationProfile profile) {
        return source(profile) && profile.memory().intact() && !CultivationRecovery.canRestore(access, profile);
    }

    public static boolean active(ServerPlayer player) {
        return player.getPersistentData().contains(SAVE_KEY, Tag.TAG_COMPOUND);
    }

    public static boolean start(ServerPlayer player, Route route) {
        var profile = CultivationService.getProfile(player);
        if (!source(profile)) return reject(player, "只有元婴后期可以引动化神雷劫。");
        if (CultivationRecovery.canRestore(player.registryAccess(), profile))
            return reject(player, "补满修为后按 N 恢复原境界。");
        if (!profile.memory().intact()) return reject(player, "引劫前需要先重凝完整元婴。");
        if (MeditationManager.active(player) || CombatSessionManager.hudBusy(player)
                || !steady(player) || MeditationManager.breakthroughEligibility(player) != MeditationStopReason.NONE)
            return reject(player, "请落地、脱战并结束当前修炼或手中操作，再引动化神雷劫。");

        var realm = MeditationManager.realmLookup(player).apply(profile.realmId());
        var stage = realm == null ? null : realm.stages().stream().filter(s -> s.id().equals(NASCENT_LATE)).findFirst().orElse(null);
        var advance = stage == null ? null : stage.advancement().orElse(null);
        if (advance == null || stage.cultivationCap().isEmpty() || advance.kind() != AdvancementKind.BOTTLENECK
                || !advance.targetRealm().equals(ModCultivationRegistries.SPIRIT_TRANSFORMATION_REALM_ID)
                || !advance.targetStage().equals(SPIRIT_EARLY)
                || CultivationService.validateRealmAndStage(player.registryAccess(), advance.targetRealm(), advance.targetStage()).isPresent())
            return reject(player, "化神阶段定义不可用。");
        long cap = stage.cultivationCap().orElseThrow();
        if (profile.cultivationProgress() < cap || profile.stability() < advance.requiredStability())
            return reject(player, "化神引劫需要 " + cap + " 修为和 " + advance.requiredStability() + " 稳定度。");

        var rules = SpiritTrialRules.get();
        Vec3 anchor = player.position();
        if (!TribulationManager.loaded(player, anchor.x, anchor.z, rules.arenaRadius())
                || !TribulationManager.openGround(player, anchor.x, anchor.y, anchor.z, rules.arenaRadius(), rules.verticalRange()))
            return reject(player, "请到露天平地引劫，周围需要可落脚的活动空间。");

        if (route == Route.HIGH && !CultivationService.replaceProfile(player,
                profile.withMemory(profile.memory().disperseSeed())).success())
            return reject(player, "元婴状态提交失败，化神雷劫未开启。");

        var attempt = new Attempt(UUID.randomUUID(), route, player.level().dimension().location(),
                anchor, cap, advance.requiredStability(), advance.durationTicks());
        attempt.remaining = rules.preparationTicks();
        save(player, attempt);
        CombatSessionManager.interrupt(player, CombatStopReason.CULTIVATION_STARTED, true);
        player.sendSystemMessage(Component.literal(route.title() + "引劫开始。留在劫场内，按屏幕提示操作。"));
        publish(player, attempt);
        return true;
    }

    private static boolean reject(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
        return false;
    }

    private static boolean steady(ServerPlayer player) {
        return player.onGround() && !player.isPassenger() && !player.isFallFlying() && !player.getAbilities().flying
                && !player.isSleeping() && !player.isSwimming() && !player.isUsingItem()
                && player.containerMenu == player.inventoryMenu;
    }

    public static void intent(ServerPlayer player, SpiritTrialIntentPayload intent) {
        var attempt = read(player);
        if (attempt == null || !player.isAlive() || !player.gameMode.isSurvival() || !attempt.id.equals(intent.attempt())
                || attempt.strike != intent.strike() || intent.action() == Action.NONE
                || !inside(player, attempt) || !attempt.dimension.equals(player.level().dimension().location())) return;
        var profile = CultivationService.getProfile(player);
        if (!source(profile) || action(attempt, profile) != intent.action()) return;

        switch (intent.action()) {
            case SCATTER -> {
                if (!CultivationService.replaceProfile(player, profile.withMemory(profile.memory().shatter())).success()) {
                    finish(player, "散婴状态提交失败，化神雷劫中止。");
                    return;
                }
                player.sendSystemMessage(Component.literal("元婴已散形并留下婴种，接下来的雷击由肉身承受。"));
            }
            case BLOCK -> {
                if (profile.memory().intact()) {
                    long cost = SpiritTrialRules.get().blockCost(CultivationService.spiritualPowerCap(player));
                    if (!CultivationService.spendSpiritualPower(player, cost)) {
                        reject(player, "挡雷需要 " + cost + " 点灵力。");
                        return;
                    }
                } else {
                    attempt.power--;
                }
                attempt.blocked = true;
            }
            case UNITE -> {
                if (!steady(player) || CombatSessionManager.hudBusy(player)) {
                    reject(player, "请落地并结束手中操作，再静坐合神。");
                    return;
                }
                attempt.phase = Phase.UNION;
                attempt.remaining = attempt.unionTicks;
                attempt.seat = player.position();
            }
            default -> { return; }
        }
        save(player, attempt);
        publish(player, attempt);
        MeditationManager.notifyStatus(player);
    }

    static Action action(Route route, boolean lightning, boolean ready, int strike, int remaining,
                         boolean blocked, boolean intact, int power, int warningTicks) {
        if (ready) return Action.UNITE;
        if (!lightning) return Action.NONE;
        if (route == Route.MIDDLE && strike == 3 && intact) return Action.SCATTER;
        return remaining <= warningTicks && !blocked && (intact || power > 0) ? Action.BLOCK : Action.NONE;
    }

    private static Action action(Attempt a, CultivationProfile profile) {
        return action(a.route, a.phase == Phase.LIGHTNING, a.phase == Phase.READY,
                a.strike, a.remaining, a.blocked, profile.memory().intact(), a.power, SpiritTrialRules.get().warningTicks());
    }

    private static boolean inside(ServerPlayer player, Attempt attempt) {
        var rules = SpiritTrialRules.get();
        Vec3 difference = player.position().subtract(attempt.anchor);
        return difference.horizontalDistanceSqr() <= (double) rules.arenaRadius() * rules.arenaRadius()
                && Math.abs(difference.y) <= rules.verticalRange();
    }

    public static void tick(ServerPlayer player) {
        if (!active(player)) return;
        var attempt = read(player);
        if (attempt == null) { finish(player, "化神雷劫记录无法读取，本次试炼中止。"); return; }
        var profile = CultivationService.getProfile(player);
        if (!player.isAlive() || player.isRemoved() || !player.gameMode.isSurvival()
                || !source(profile) || !attempt.dimension.equals(player.level().dimension().location())
                || !profile.awakened() || !profile.learnedTechniques().containsKey(ModCultivationRegistries.BASIC_BREATHING_TECHNIQUE_ID)
                || profile.cultivationProgress() < attempt.cap || profile.stability() < attempt.stability) {
            finish(player, "化神资格或所在位置已改变，本次试炼中止。");
            return;
        }
        if (attempt.inFlight) { finish(player, "上一道雷击的结算未确认，本次试炼中止。"); return; }
        if (player.isPassenger() || player.isFallFlying() || player.getAbilities().flying) {
            finish(player, "骑乘或飞行中止化神雷劫。");
            return;
        }

        var rules = SpiritTrialRules.get();
        attempt.outside = inside(player, attempt) ? 0 : attempt.outside + 1;
        if (attempt.outside >= rules.boundaryGraceTicks()) { finish(player, "离开劫场过久，化神雷劫中止。"); return; }

        if (attempt.phase == Phase.UNION) {
            if (!steady(player) || attempt.seat.distanceToSqr(player.position()) > 0.01) {
                finish(player, "静坐合神中断，本次化神未成。");
                return;
            }
            if (--attempt.remaining <= 0) { succeed(player, attempt, profile); return; }
        } else if (attempt.phase == Phase.LIGHTNING) {
            if (--attempt.remaining <= 0) { strike(player, attempt, profile); return; }
            if (attempt.remaining == rules.warningTicks()) {
                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.WEATHER, .7f, 1.3f);
                publish(player, attempt);
            }
        }
        save(player, attempt);
        if (player.level().getGameTime() % 5 == 0) {
            publish(player, attempt);
            if (attempt.phase == Phase.UNION) MeditationManager.notifyStatus(player);
        }
    }

    private static void strike(ServerPlayer player, Attempt attempt, CultivationProfile profile) {
        if (attempt.route == Route.MIDDLE && attempt.strike == 3 && profile.memory().intact()) {
            finish(player, "第四道雷前未散婴，本次中品化神未成。");
            return;
        }
        var rules = SpiritTrialRules.get();
        var level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1f, 1.1f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(),
                24, .5, 1, .5, .16);
        if (!attempt.blocked) {
            var key = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("yuanxian:tribulation_main"));
            DamageSource source = new DamageSource(player.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key));
            StrikeReceipt receipt = new StrikeReceipt(player, source);
            attempt.inFlight = true;
            save(player, attempt);
            boolean accepted;
            STRIKE.set(receipt);
            try {
                accepted = player.hurt(source, rules.damage(attempt.strike, profile.memory().intact()));
            } finally {
                STRIKE.remove();
            }
            if (!active(player) || !player.isAlive() || player.isRemoved()) return;
            if (accepted || receipt.observed) attempt.power++;
            attempt.inFlight = false;
        }
        attempt.strike++;
        attempt.blocked = false;
        if (attempt.strike == rules.damage().size()) {
            if (attempt.power < rules.requiredPower()) {
                finish(player, "劫力只留有 " + attempt.power + " 份，需要 " + rules.requiredPower() + " 份，本次化神未成。");
                return;
            }
            attempt.remaining = 0;
            attempt.phase = Phase.READY;
        } else {
            attempt.remaining = rules.strikeIntervalTicks();
        }
        save(player, attempt);
        publish(player, attempt);
        LOGGER.info("SPIRIT_TRIAL owner={} strike={} power={}", player.getScoreboardName(), attempt.strike, attempt.power);
    }

    private static void observeStrike(LivingDamageEvent.Pre event) {
        var receipt = STRIKE.get();
        if (receipt != null && event.getEntity() == receipt.player && event.getSource() == receipt.source) {
            receipt.observed = true;
        }
    }

    private static void succeed(ServerPlayer player, Attempt attempt, CultivationProfile profile) {
        var memory = profile.memory();
        if (attempt.route == Route.LOW) memory = memory.shatter();
        var next = profile.withRealmAndStage(ModCultivationRegistries.SPIRIT_TRANSFORMATION_REALM_ID, SPIRIT_EARLY)
                .withCultivationProgress(0).withStability(profile.stability() / 2).withMemory(memory.spirit(attempt.route.quality()));
        player.getPersistentData().remove(SAVE_KEY);
        var result = CultivationService.replaceProfile(player, next);
        finish(player, result.success() ? "合神完成，已突破" + attempt.route.title() + "。"
                : "化神结算未能提交，本次试炼中止。");
    }

    public static boolean interrupt(ServerPlayer player, MeditationStopReason reason) {
        var attempt = read(player);
        if (attempt == null) return false;
        if (reason == MeditationStopReason.LOGGED_OUT) return true;
        if (reason == MeditationStopReason.REQUESTED || reason == MeditationStopReason.DIED
                || reason == MeditationStopReason.DIMENSION_CHANGED || attempt.phase == Phase.UNION) {
            finish(player, reason == MeditationStopReason.DIED ? "死亡中止化神试炼。" : "化神试炼已中止。");
            return true;
        }
        return false;
    }

    public static boolean stop(ServerPlayer player, String reason) {
        if (!active(player)) return false;
        finish(player, reason);
        return true;
    }

    public static void login(ServerPlayer player) {
        var attempt = read(player);
        if (attempt == null) return;
        if (attempt.inFlight) { finish(player, "上一道雷击的结算未确认，本次试炼中止。"); return; }
        if (attempt.phase == Phase.LIGHTNING) attempt.remaining = SpiritTrialRules.get().preparationTicks();
        if (attempt.phase == Phase.UNION) { attempt.phase = Phase.READY; attempt.remaining = 0; }
        save(player, attempt);
        publish(player, attempt);
    }

    public static Optional<MeditationStatus> sittingStatus(ServerPlayer player) {
        var attempt = read(player);
        return attempt == null || attempt.phase != Phase.UNION ? Optional.empty()
                : Optional.of(new MeditationStatus(MeditationState.ADVANCING_BOTTLENECK, 0, MeditationStopReason.NONE,
                        Optional.of(AdvancementKind.BOTTLENECK), attempt.unionTicks, attempt.remaining));
    }

    private static void finish(ServerPlayer player, String text) {
        player.getPersistentData().remove(SAVE_KEY);
        PacketDistributor.sendToPlayer(player, SpiritTrialStatusPayload.IDLE);
        MeditationManager.notifyStatus(player);
        player.sendSystemMessage(Component.literal(text));
    }

    private static void publish(ServerPlayer player, Attempt attempt) {
        var profile = CultivationService.getProfile(player);
        Action action = action(attempt, profile);
        PacketDistributor.sendToPlayer(player, new SpiritTrialStatusPayload(attempt.id, attempt.strike, action));
        var rules = SpiritTrialRules.get();
        String prompt = switch (action) {
            case SCATTER -> "散婴，需在第四道雷落下前完成";
            case BLOCK -> profile.memory().intact() ? "挡雷，耗 " + rules.blockPowerPercent() + "% 最大灵力" : "挡雷，耗 1 份劫力";
            case UNITE -> "静坐合神";
            case NONE -> attempt.blocked ? "已准备挡雷" : attempt.phase == Phase.UNION ? "静坐合神中" : "等待落雷";
        };
        String timing = attempt.phase == Phase.LIGHTNING
                ? "第 " + (attempt.strike + 1) + " / " + rules.damage().size() + " 道 " + seconds(attempt.remaining)
                : attempt.phase == Phase.UNION ? "合神 " + seconds(attempt.remaining) : "雷劫结束";
        var message = Component.literal(timing + "   劫力 " + attempt.power + " / " + rules.requiredPower() + "   ");
        if (action != Action.NONE) message.append("按 ").append(Component.keybind("key.yuanxian.start_advancement")).append(" ");
        player.displayClientMessage(message.append(prompt), true);
    }

    private static String seconds(int ticks) { return String.format(java.util.Locale.ROOT, "%.1f 秒", ticks / 20.0); }

    static com.google.gson.JsonObject status(ServerPlayer player) {
        var a = read(player);
        var out = new com.google.gson.JsonObject();
        out.addProperty("schema", 1);
        out.addProperty("active", a != null);
        if (a != null) {
            out.addProperty("kind", a.route.id());
            out.addProperty("phase", a.phase.name());
            out.addProperty("strikes", a.strike);
            out.addProperty("power", a.power);
            out.addProperty("remaining", a.remaining);
            out.addProperty("blocked", a.blocked);
        }
        return out;
    }

    private static Attempt read(ServerPlayer player) {
        if (!active(player)) return null;
        try { return Attempt.decode(player.getPersistentData().getCompound(SAVE_KEY)); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private static void save(ServerPlayer player, Attempt attempt) {
        player.getPersistentData().put(SAVE_KEY, attempt.encode());
    }

    private static final class StrikeReceipt {
        final ServerPlayer player;
        final DamageSource source;
        boolean observed;
        StrikeReceipt(ServerPlayer player, DamageSource source) { this.player = player; this.source = source; }
    }

    private static final class Attempt {
        final UUID id;
        final Route route;
        final ResourceLocation dimension;
        final Vec3 anchor;
        final long cap;
        final long stability;
        final int unionTicks;
        Phase phase = Phase.LIGHTNING;
        int remaining;
        int strike;
        int power;
        int outside;
        boolean blocked;
        boolean inFlight;
        Vec3 seat = Vec3.ZERO;

        Attempt(UUID id, Route route, ResourceLocation dimension, Vec3 anchor, long cap, long stability, int unionTicks) {
            this.id = id; this.route = route; this.dimension = dimension; this.anchor = anchor;
            this.cap = cap; this.stability = stability; this.unionTicks = unionTicks;
        }

        CompoundTag encode() {
            var tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putString("route", route.name());
            tag.putString("dimension", dimension.toString());
            position(tag, "anchor", anchor);
            tag.putLong("cap", cap);
            tag.putLong("stability", stability);
            tag.putInt("union_ticks", unionTicks);
            tag.putString("phase", phase.name());
            tag.putInt("remaining", remaining);
            tag.putInt("strike", strike);
            tag.putInt("power", power);
            tag.putInt("outside", outside);
            tag.putBoolean("blocked", blocked);
            tag.putBoolean("in_flight", inFlight);
            position(tag, "seat", seat);
            return tag;
        }

        static Attempt decode(CompoundTag tag) {
            var a = new Attempt(tag.getUUID("id"), Route.valueOf(tag.getString("route")),
                    ResourceLocation.parse(tag.getString("dimension")), position(tag, "anchor"),
                    tag.getLong("cap"), tag.getLong("stability"), tag.getInt("union_ticks"));
            a.phase = Phase.valueOf(tag.getString("phase"));
            a.remaining = tag.getInt("remaining");
            a.strike = tag.getInt("strike");
            a.power = tag.getInt("power");
            a.outside = tag.getInt("outside");
            a.blocked = tag.getBoolean("blocked");
            a.inFlight = tag.getBoolean("in_flight");
            a.seat = position(tag, "seat");
            int strikes = SpiritTrialRules.get().damage().size();
            if (a.strike < 0 || a.strike > strikes || a.power < 0 || a.power > a.strike
                    || (a.phase == Phase.LIGHTNING) == (a.strike == strikes)
                    || a.remaining < 0 || a.unionTicks <= 0 || a.cap <= 0 || a.stability < 0 || a.outside < 0)
                throw new IllegalArgumentException("Invalid saved spirit trial");
            return a;
        }

        private static void position(CompoundTag tag, String key, Vec3 pos) {
            tag.putDouble(key + "_x", pos.x); tag.putDouble(key + "_y", pos.y); tag.putDouble(key + "_z", pos.z);
        }

        private static Vec3 position(CompoundTag tag, String key) {
            return new Vec3(tag.getDouble(key + "_x"), tag.getDouble(key + "_y"), tag.getDouble(key + "_z"));
        }
    }
}
