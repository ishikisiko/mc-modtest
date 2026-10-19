package com.example.myvillage.boss;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossBarPresentationTest {
    @Test void preservesCustomTitleRealmAndIndependentServerStates() {
        var title = Component.literal("山谷守卫 · 自定义");
        var realm = Component.translatable("world_sim.stage.foundation.1");
        for (boolean enraged : new boolean[]{false, true}) for (boolean broken : new boolean[]{false, true}) {
            var data = BossBarPresentation.read(BossBarPresentation.name(title, realm, enraged, broken)).orElseThrow();
            assertEquals(title, data.title());
            assertEquals(realm, data.realm());
            assertEquals(enraged, data.enraged());
            assertEquals(broken, data.broken());
        }
    }
    @Test void unrelatedNamesOldFormatsAndMalformedStatesUseVanillaFallback() {
        assertTrue(BossBarPresentation.read(Component.literal("雷角山君")).isEmpty());
        assertTrue(BossBarPresentation.read(Component.translatable("entity.minecraft.ender_dragon")).isEmpty());
        assertTrue(BossBarPresentation.read(Component.translatable(BossBarPresentation.KEY, Component.literal("boss"), Component.literal("realm"))).isEmpty());
        assertTrue(BossBarPresentation.read(Component.translatable(BossBarPresentation.KEY,
                Component.literal("boss"), Component.literal("realm"), Component.literal("Phase II"),
                Component.translatable(BossBarPresentation.HORN_INTACT))).isEmpty());
        assertTrue(BossBarPresentation.read(BossBarPresentation.name(Component.literal("boss"), Component.literal("realm"), false, false)
                .copy().append("extra")).isEmpty());
    }
}
