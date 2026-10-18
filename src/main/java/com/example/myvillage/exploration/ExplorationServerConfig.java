package com.example.myvillage.exploration;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world server opt-in; realm thresholds remain owned by exploration/rules.json. */
public final class ExplorationServerConfig {
    public static final String FILE_NAME = "myvillage-exploration-server.toml";
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue PORTAL_GATES_ENABLED;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("portal_gates");
        PORTAL_GATES_ENABLED = builder.comment("Require the configured cultivation realm when players enter the Nether or End through native portals.",
                "Returns to the Overworld, gateway travel, command teleports and non-player entities remain unrestricted.")
                .define("enabled", ExplorationRules.get().portalGates().enabled());
        builder.pop();
        SPEC = builder.build();
    }
    private ExplorationServerConfig() { }
    public static boolean portalGatesEnabled() {
        return SPEC.isLoaded() ? PORTAL_GATES_ENABLED.getAsBoolean() : ExplorationRules.get().portalGates().enabled();
    }
}
