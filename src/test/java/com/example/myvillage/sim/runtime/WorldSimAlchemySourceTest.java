package com.example.myvillage.sim.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.alchemy.AlchemyRules;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

class WorldSimAlchemySourceTest {
    @Test void npcRecipeProjectionUsesThePlayersBundledSourceEvenWhenDatapacksOverrideOtherData() throws Exception {
        var calls = new AtomicInteger();
        var resources = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getResource")) {
                        calls.incrementAndGet();
                        return Optional.empty();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        var opener = WorldSimRuntime.opener(resources);
        try (var stream = opener.open("data/myvillage/alchemy/rules.json")) {
            assertNotNull(stream);
            var raw = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var recipe : AlchemyRules.get().recipes()) {
                var found = raw.getAsJsonArray("recipes").asList().stream()
                        .map(e -> e.getAsJsonObject()).filter(e -> e.get("id").getAsString().equals(recipe.id()))
                        .findFirst().orElseThrow();
                assertEquals(recipe.durationTicks(), found.get("duration_ticks").getAsInt());
                assertEquals(recipe.outputCount(), found.get("output_count").getAsInt());
            }
        }
        assertEquals(0, calls.get(), "a datapack cannot fork only the NPC half of a shared recipe");
        assertNull(opener.open("data/myvillage/world_sim/life_rules.json"));
        assertEquals(1, calls.get(), "world-sim rules retain their existing datapack path");
    }
}
