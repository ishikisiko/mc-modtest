package com.example.myvillage.cultivation.fate.ability;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.CombatService;
import com.example.myvillage.combat.runtime.CombatDodgeService;
import com.example.myvillage.combat.runtime.CombatHotbarService;
import com.example.myvillage.combat.session.CombatSessionManager;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.fate.FateService;
import com.example.myvillage.cultivation.meditation.MeditationManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 六槽意图的天命入口；命中、阵地和控制由已登记的具体执行器负责。 */
public final class FateAbilityRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(FateAbilityRuntime.class);
    private static final Map<ResourceLocation, Executor> EXECUTORS = new HashMap<>();
    private static final Set<UUID> CASTING = new HashSet<>();
    private static boolean registered;

    private FateAbilityRuntime() { }

    @FunctionalInterface public interface Executor {
        /** 只做同步校验和准备；empty 拒绝无扣费。返回动作不再拒绝，也不能修改灵力。 */
        Optional<Runnable> prepare(ServerPlayer player, FateAbilityDefinition definition);
    }

    public static void register() {
        if (registered) return;
        FateAbilityDefinition.all();
        NeoForge.EVENT_BUS.addListener(FateAbilityRuntime::tick);
        NeoForge.EVENT_BUS.addListener(FateAbilityRuntime::stopped);
        registered = true;
    }

    public static void registerExecutor(ResourceLocation id, Executor executor) {
        if (FateAbilityDefinition.find(id).isEmpty()) throw new IllegalArgumentException("主动能力没有作者配置：" + id);
        if (EXECUTORS.putIfAbsent(id, Objects.requireNonNull(executor)) != null)
            throw new IllegalArgumentException("主动能力执行器重复：" + id);
    }

    public static boolean available(ServerPlayer player, ResourceLocation id) {
        return EXECUTORS.containsKey(id) && FateAbilityDefinition.find(id)
                .filter(definition -> definition.granted(FateService.state(player), player.registryAccess())).isPresent();
    }

    public static long cost(ServerPlayer player, ResourceLocation id) {
        return FateAbilityDefinition.find(id).map(definition -> definition.cost(CultivationService.spiritualPowerCap(player))).orElse(0L);
    }

    public static int cooldownRemaining(ServerPlayer player, ResourceLocation id) {
        return (int) Math.min(Integer.MAX_VALUE, cooldowns(player).remaining(player.getUUID(), id, now(player)));
    }

    /** 资格与执行检查均在服务器线程；按键绑定、自动槽和客户端缓存不能授予能力。 */
    public static boolean cast(ServerPlayer player, ResourceLocation id) {
        if (!player.getServer().isSameThread() || !available(player, id) || blocked(player)
                || !CASTING.add(player.getUUID())) return false;
        try {
            var definition = FateAbilityDefinition.find(id).orElseThrow();
            long current = Math.min(CultivationService.getProfile(player).currentSpiritualPower(),
                    CultivationService.spiritualPowerCap(player));
            return FateAbilityCast.attempt(true, cooldownRemaining(player, id), current, cost(player, id),
                    () -> EXECUTORS.get(id).prepare(player, definition),
                    amount -> CultivationService.spendSpiritualPower(player, amount),
                    amount -> {
                        if (!CultivationService.recoverSpiritualPower(player, amount).success())
                            throw new IllegalStateException("主动能力异常后灵力退款失败");
                    },
                    () -> {
                        cooldowns(player).start(player.getUUID(), id, now(player), definition.cooldownTicks());
                        CombatHotbarService.forget(player.getUUID());
                    });
        } catch (RuntimeException exception) {
            LOGGER.error("天命主动能力施放异常，未提交冷却：{} {}", player.getUUID(), id, exception);
            return false;
        } finally {
            CASTING.remove(player.getUUID());
        }
    }

    public static boolean blocked(ServerPlayer player) {
        return CombatService.getMode(player) != CombatMode.CULTIVATION || !player.isAlive() || player.isRemoved()
                || player.isSpectator() || player.isSleeping() || player.isPassenger() || player.isUsingItem()
                || player.containerMenu != player.inventoryMenu || MeditationManager.status(player).state().active()
                || CombatSessionManager.hudBusy(player) || CombatDodgeService.isDodging(player);
    }

    private static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }
    private static FateAbilityCooldowns cooldowns(ServerPlayer player) {
        return FateAbilityCooldowns.get(player.getServer().overworld());
    }

    private static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0)
            FateAbilityCooldowns.get(event.getServer().overworld()).prune(event.getServer().overworld().getGameTime());
    }

    private static void stopped(ServerStoppedEvent event) {
        CASTING.clear();
        // 执行器属于模组注册，不随单个世界关闭删除；SavedData 由世界负责写盘与释放。
    }
}
