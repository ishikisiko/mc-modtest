package com.example.myvillage.sim.runtime;

import com.example.myvillage.sim.SimDate;
import com.example.myvillage.sim.SimEvent;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Turns ledger values into chat components through language keys, so each client reads them in its
 * own language. Event params that start with {@code @} are language keys themselves (realm, rank,
 * cause phrase) and become nested translatables; any other param (names, numbers) is literal.
 */
public final class WorldSimText {
    public static final String KEY = "commands.myvillage.world.";

    private WorldSimText() {
    }

    /** One chronicle event as prose. */
    public static MutableComponent event(SimEvent event) {
        return Component.translatable(event.textKey(), params(event.params()));
    }

    static Object[] params(List<String> params) {
        Object[] args = new Object[params.size()];
        for (int i = 0; i < args.length; i++) {
            String p = params.get(i);
            args[i] = p.startsWith("@") ? Component.translatable(p.substring(1)) : Component.literal(p);
        }
        return args;
    }

    /** 启元 N 年 / 启元前 N 年. */
    public static MutableComponent date(SimDate date) {
        return Component.translatable(date.textKey(), String.valueOf(date.year()));
    }

    /** {@code [date] event}, dated by the ledger's own calendar. */
    public static MutableComponent datedEvent(SimEvent event, long prehistoryDays, int daysPerYear) {
        return Component.literal("[")
                .append(date(SimDate.of(event.day(), prehistoryDays, daysPerYear)))
                .append("] ")
                .append(event(event));
    }

    public static MutableComponent realm(String realmId) {
        return Component.translatable("world_sim.realm." + realmId);
    }

    /** {@code stage} is 0-based, as in the views. */
    public static MutableComponent stage(String realmId, int stage) {
        return Component.translatable("world_sim.stage." + realmId + "." + (stage + 1));
    }

    public static MutableComponent rank(String rank) {
        return Component.translatableWithFallback("world_sim.rank." + rank, rank);
    }

    public static MutableComponent rootGrade(String grade) {
        return Component.translatableWithFallback("world_sim.root." + grade, grade);
    }

    public static MutableComponent status(String status) {
        return Component.translatableWithFallback(KEY + "status." + status, status);
    }

    public static MutableComponent cause(String cause) {
        return Component.translatableWithFallback(KEY + "cause." + cause, cause);
    }

    public static MutableComponent sectState(String state) {
        return Component.translatableWithFallback(KEY + "sect_state." + state, state);
    }

    /** A {@code commands.myvillage.world.<suffix>} line. */
    public static MutableComponent line(String suffix, Object... args) {
        return Component.translatable(KEY + suffix, args);
    }
}
