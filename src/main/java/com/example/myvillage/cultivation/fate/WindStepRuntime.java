package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.CultivationService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

/** Server-owned landing observations and the transient post-dodge movement bonus. */
public final class WindStepRuntime {
    private static final ResourceLocation SPEED = ResourceLocation.fromNamespaceAndPath("myvillage", "wind_step_speed");
    private static final Map<UUID, Landing> LANDINGS = new HashMap<>();
    private static final Map<UUID, Long> SPEED_UNTIL = new HashMap<>();
    // Geometry tolerances, not gameplay tuning. The foot probe cannot mistake a side wall for a floor.
    static final double FOOT_DEPTH = 1.0E-4;
    static final double FOOT_INSET = 1.0E-6;
    static final double STABLE_Y_TOLERANCE = 0.015;

    private WindStepRuntime() { }

    public static boolean enabled(ServerPlayer player) { return FateService.effect(player, "air_dodge") > 0; }

    /** Ignores the movement packet's onGround bit, including when choosing ground versus air dodge. */
    public static boolean grounded(ServerPlayer player) {
        AABB body = player.getBoundingBox();
        AABB feet = footProbe(body);
        return player.getDeltaMovement().y <= 0.01
                && player.serverLevel().noCollision(player, body.deflate(1.0E-5))
                && player.serverLevel().getBlockCollisions(player, feet).iterator().hasNext();
    }

    public static boolean airChargeAvailable(ServerPlayer player) {
        return enabled(player) && FateService.state(player).windStep().canAirDodge(now(player));
    }

    public static boolean cooldownReady(ServerPlayer player) {
        return !enabled(player) || now(player) >= FateService.state(player).windStep().readyAt();
    }

    public static long cooldownRemaining(ServerPlayer player) {
        return enabled(player) ? Math.max(0, FateService.state(player).windStep().readyAt() - now(player)) : 0;
    }

    /** Called only after actual accepted movement and, for an air dodge, successful qi payment. */
    public static void onSuccessfulDodge(ServerPlayer player, boolean airborne, int cooldownTicks) {
        if (!enabled(player)) return;
        FateState latest = FateService.state(player);
        CultivationService.replaceFates(player,
                latest.withWindStep(latest.windStep().dodged(airborne, now(player), cooldownTicks)), false);
        // A prior floor observation must never count as the new landing after this air dodge.
        if (airborne) LANDINGS.put(player.getUUID(),
                new Landing(now(player), player.getY(), player.level().dimension().location(), false));
        double bonus = FateService.effect(player, "dodge_speed_bonus");
        int ticks = (int) FateService.effect(player, "dodge_speed_ticks");
        if (bonus > 0 && ticks > 0) {
            long now = now(player);
            SPEED_UNTIL.put(player.getUUID(), now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks);
            replaceSpeed(player, bonus);
        }
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isRemoved() || !enabled(player)) {
                clear(player);
                continue;
            }
            long now = now(player);
            boolean standing = !player.isPassenger() && !player.isSpectator()
                    && !player.getAbilities().flying && grounded(player);
            Landing previous = LANDINGS.get(player.getUUID());
            if (stableLanding(previous, now, player.getY(), player.level().dimension().location(), standing)) {
                FateState state = FateService.state(player);
                WindStepState landed = state.windStep().landed();
                if (!landed.equals(state.windStep()))
                    CultivationService.replaceFates(player, state.withWindStep(landed), false);
            }
            LANDINGS.put(player.getUUID(),
                    new Landing(now, player.getY(), player.level().dimension().location(), standing));

            Long until = SPEED_UNTIL.get(player.getUUID());
            if (until == null || now >= until) {
                SPEED_UNTIL.remove(player.getUUID());
                replaceSpeed(player, 0);
            } else replaceSpeed(player, FateService.effect(player, "dodge_speed_bonus"));
        }
        LANDINGS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        SPEED_UNTIL.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    /** Logout/death/dimension change clears presentation and observations, retaining saved charge/cooldown. */
    public static void clear(ServerPlayer player) {
        LANDINGS.remove(player.getUUID());
        SPEED_UNTIL.remove(player.getUUID());
        replaceSpeed(player, 0);
    }

    public static void clearAll() { LANDINGS.clear(); SPEED_UNTIL.clear(); }

    static AABB footProbe(AABB body) {
        double insetX = Math.min(FOOT_INSET, body.getXsize() * 0.2);
        double insetZ = Math.min(FOOT_INSET, body.getZsize() * 0.2);
        return new AABB(body.minX + insetX, body.minY - FOOT_DEPTH, body.minZ + insetZ,
                body.maxX - insetX, body.minY, body.maxZ - insetZ);
    }

    static boolean stableLanding(Landing previous, long now, double y, ResourceLocation dimension, boolean standing) {
        if (!standing || previous == null || !previous.dimension().equals(dimension)) return false;
        // A real airborne -> support transition is already an observed landing, even if the
        // player jumps again on the next tick. Only login/initial ground observations need stability.
        if (!previous.grounded() && previous.tick() <= now && now - previous.tick() <= 1) return true;
        return previous.tick() + 1 == now && Math.abs(y - previous.y()) <= STABLE_Y_TOLERANCE;
    }

    private static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }

    private static void replaceSpeed(ServerPlayer player, double bonus) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        AttributeModifier old = speed.getModifier(SPEED);
        if (old != null && old.amount() == bonus) return;
        speed.removeModifier(SPEED);
        if (bonus > 0) speed.addTransientModifier(new AttributeModifier(SPEED, bonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    record Landing(long tick, double y, ResourceLocation dimension, boolean grounded) { }
}
