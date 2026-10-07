package com.example.myvillage.sim.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import org.junit.jupiter.api.Test;

/** The spec can build its default file (FML corrects an empty config) and rejects unknown tiers. */
class WorldSimServerConfigTest {
    @Test
    void anEmptyConfigIsCorrectedToTheDefaults() {
        CommentedConfig config = CommentedConfig.inMemory();
        assertFalse(WorldSimServerConfig.SPEC.isCorrect(config));
        WorldSimServerConfig.SPEC.correct(config);
        assertTrue(WorldSimServerConfig.SPEC.isCorrect(config));
        assertEquals("small", config.get("world_sim.tier"));
        assertEquals(30, config.<Integer>get("world_sim.catch_up_cap_days"));
        assertEquals(true, config.get("rumors.rumors_enabled"));
        assertEquals(2, config.<Integer>get("rumors.rumors_per_minute"));
        assertEquals(true, config.get("avatars.avatars_enabled"));
        assertEquals(64, config.<Integer>get("avatars.avatar_spawn_radius"));
        assertEquals(12, config.<Integer>get("avatars.max_avatars_per_sect"));
        assertEquals(40, config.<Integer>get("avatars.max_avatars"));
        assertEquals(true, config.get("avatars.auto_realize_gates"));
    }

    @Test
    void anUnknownTierIsCorrectedBackToTheDefault() {
        CommentedConfig config = CommentedConfig.inMemory();
        WorldSimServerConfig.SPEC.correct(config);
        config.set("world_sim.tier", "huge");
        assertFalse(WorldSimServerConfig.SPEC.isCorrect(config));
        WorldSimServerConfig.SPEC.correct(config);
        assertEquals("small", config.get("world_sim.tier"));
    }
}
