package com.example.myvillage.sim.runtime.avatar;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config of the ledger avatars, file {@code myvillage-world_sim_avatars-server.toml} in the
 * instance's {@code config/} (a world's {@code serverconfig/} copy overrides it). Values are read
 * on use, so a reload takes effect on the next pass.
 */
public final class WorldSimAvatarConfig {
    public static final String FILE_NAME = "myvillage-world_sim_avatars-server.toml";
    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_SPAWN_RADIUS = 64;
    /** Avatars are withdrawn once no player is within the spawn radius plus this margin. */
    public static final int WITHDRAW_MARGIN = 32;
    public static final int DEFAULT_MAX_PER_SECT = 12;
    public static final int DEFAULT_MAX_TOTAL = 40;

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.IntValue SPAWN_RADIUS;
    private static final ModConfigSpec.IntValue MAX_PER_SECT;
    private static final ModConfigSpec.IntValue MAX_TOTAL;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("avatars");
        ENABLED = builder
                .comment("Project the ledger members who are at a realized sect gate as cultivators while a "
                        + "player is near (化身). They are never saved; the ledger stays the only authority.")
                .define("avatars_enabled", DEFAULT_ENABLED);
        SPAWN_RADIUS = builder
                .comment("Avatars appear while a player is within this many blocks (horizontally) of a realized "
                        + "compound's site, and are withdrawn once none is within this radius plus "
                        + WITHDRAW_MARGIN + ". Keep it well inside the server view distance.")
                .defineInRange("avatar_spawn_radius", DEFAULT_SPAWN_RADIUS, 8, 128);
        MAX_PER_SECT = builder
                .comment("Most avatars per compound: the master first, then the elders, then by realm.")
                .defineInRange("max_avatars_per_sect", DEFAULT_MAX_PER_SECT, 0, 64);
        MAX_TOTAL = builder
                .comment("Most avatars in the world at once; the compound nearest a player is served first.")
                .defineInRange("max_avatars", DEFAULT_MAX_TOTAL, 0, 256);
        builder.pop();
        SPEC = builder.build();
    }

    private WorldSimAvatarConfig() {
    }

    public static boolean enabled() {
        return SPEC.isLoaded() ? ENABLED.getAsBoolean() : DEFAULT_ENABLED;
    }

    public static int spawnRadius() {
        return SPEC.isLoaded() ? SPAWN_RADIUS.getAsInt() : DEFAULT_SPAWN_RADIUS;
    }

    public static int withdrawRadius() {
        return spawnRadius() + WITHDRAW_MARGIN;
    }

    public static int maxPerSect() {
        return SPEC.isLoaded() ? MAX_PER_SECT.getAsInt() : DEFAULT_MAX_PER_SECT;
    }

    public static int maxTotal() {
        return SPEC.isLoaded() ? MAX_TOTAL.getAsInt() : DEFAULT_MAX_TOTAL;
    }
}
