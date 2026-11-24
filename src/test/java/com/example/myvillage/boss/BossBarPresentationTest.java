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
            assertEquals(enraged, data.emphasized());
            assertEquals(Component.empty()
                    .append(Component.translatable(enraged ? BossBarPresentation.PHASE_TWO : BossBarPresentation.PHASE_ONE))
                    .append(" · ")
                    .append(Component.translatable(broken ? BossBarPresentation.HORN_BROKEN : BossBarPresentation.HORN_INTACT)),
                    data.status());
        }
    }
    @Test void lotusGuardUsesItsOwnFlankStateAndEncounterRealm() {
        var title = Component.translatable("entity.yuanxian.lotus_guard");
        var realm = Component.translatable("world_sim.stage.qi_refining.9");
        String[] statuses = {BossBarPresentation.LOTUS_RIGHT_EXPOSED, BossBarPresentation.LOTUS_PROTECTED,
                BossBarPresentation.LOTUS_LEFT_EXPOSED};
        for (int flank = -1; flank <= 1; flank++) {
            var data = BossBarPresentation.read(BossBarPresentation.lotusName(title, realm, flank)).orElseThrow();
            assertEquals(title, data.title());
            assertEquals(realm, data.realm());
            assertEquals(Component.translatable(statuses[flank + 1]), data.status());
            assertEquals(flank != 0, data.emphasized());
        }
        assertTrue(BossBarPresentation.read(Component.translatable(BossBarPresentation.LOTUS_KEY, title, realm,
                Component.translatable(BossBarPresentation.HORN_INTACT))).isEmpty());
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
