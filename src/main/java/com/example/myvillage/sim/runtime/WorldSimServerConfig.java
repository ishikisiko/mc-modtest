package com.example.myvillage.sim.runtime;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config of the world ledger, file {@code myvillage-world_sim-server.toml} in the instance's
 * {@code config/}; a copy in a world's {@code serverconfig/} overrides it for that world. {@code tier}
 * is read only at genesis: once a world has a ledger its tier is fixed in the save and this value is
 * ignored. The other values are read on use, so a reload takes effect at once.
 */
public final class WorldSimServerConfig {
    public static final String FILE_NAME = "myvillage-world_sim-server.toml";
    public static final List<String> TIERS = List.of("small", "medium", "large");
    public static final String DEFAULT_TIER = "small";
    public static final int DEFAULT_CATCH_UP_CAP_DAYS = 30;
    public static final boolean DEFAULT_RUMORS_ENABLED = true;
    public static final int DEFAULT_RUMORS_PER_MINUTE = 2;
    public static final int DEFAULT_RUMOR_QUEUE_CAP = 6;

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<String> TIER;
    private static final ModConfigSpec.IntValue CATCH_UP_CAP_DAYS;
    private static final ModConfigSpec.BooleanValue RUMORS_ENABLED;
    private static final ModConfigSpec.IntValue RUMORS_PER_MINUTE;
    private static final ModConfigSpec.IntValue RUMOR_QUEUE_CAP;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("world_sim");
        TIER = builder
                .comment("Population tier used when this world's ledger is first created (small ~80 people, "
                        + "medium ~300, large ~1000). Read only at genesis: the tier is then fixed in the save.")
                // a mutable copy: the spec probes the list with contains(null), which List.of rejects
                .defineInList("tier", DEFAULT_TIER, new ArrayList<>(TIERS));
        CATCH_UP_CAP_DAYS = builder
                .comment("Most sim days that may be pending settlement at once. A jump of the cultivation "
                        + "calendar (e.g. a smaller ticks_per_day) adds at most this many; one day is settled per tick.")
                .defineInRange("catch_up_cap_days", DEFAULT_CATCH_UP_CAP_DAYS, 1, 3650);
        builder.pop();
        builder.push("rumors");
        RUMORS_ENABLED = builder
                .comment("Tell online players about major ledger events in chat (江湖传闻).")
                .define("rumors_enabled", DEFAULT_RUMORS_ENABLED);
        RUMORS_PER_MINUTE = builder
                .comment("Most rumors one player receives per real minute; the rest wait in the queue.")
                .defineInRange("rumors_per_minute", DEFAULT_RUMORS_PER_MINUTE, 1, 60);
        RUMOR_QUEUE_CAP = builder
                .comment("Most rumors waiting per player; when full, the oldest waiting rumor is dropped.")
                .defineInRange("rumor_queue_cap", DEFAULT_RUMOR_QUEUE_CAP, 1, 100);
        builder.pop();
        SPEC = builder.build();
    }

    private WorldSimServerConfig() {
    }

    public static String tier() {
        return SPEC.isLoaded() ? TIER.get() : DEFAULT_TIER;
    }

    public static int catchUpCapDays() {
        return SPEC.isLoaded() ? CATCH_UP_CAP_DAYS.getAsInt() : DEFAULT_CATCH_UP_CAP_DAYS;
    }

    public static boolean rumorsEnabled() {
        return SPEC.isLoaded() ? RUMORS_ENABLED.getAsBoolean() : DEFAULT_RUMORS_ENABLED;
    }

    public static int rumorsPerMinute() {
        return SPEC.isLoaded() ? RUMORS_PER_MINUTE.getAsInt() : DEFAULT_RUMORS_PER_MINUTE;
    }

    public static int rumorQueueCap() {
        return SPEC.isLoaded() ? RUMOR_QUEUE_CAP.getAsInt() : DEFAULT_RUMOR_QUEUE_CAP;
    }
}
