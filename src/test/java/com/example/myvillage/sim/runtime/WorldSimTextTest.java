package com.example.myvillage.sim.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.example.myvillage.sim.SimDate;
import com.example.myvillage.sim.SimEvent;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/** Ledger text reaches clients as language keys: {@code @} params nested, names literal. */
class WorldSimTextTest {
    private static final SimEvent EVENT = new SimEvent(42, 700, "breakthrough", 3, List.of(7), List.of(1),
            "zhongzhou", -1, "world_sim.event.breakthrough.golden_core.1",
            List.of("青云宫", "@world_sim.rank.elder", "韩清漪", "焚天"));

    private static TranslatableContents translatable(Component c) {
        return assertInstanceOf(TranslatableContents.class, c.getContents());
    }

    @Test
    void anEventIsItsTextKeyWithNestedKeysAndLiteralNames() {
        TranslatableContents t = translatable(WorldSimText.event(EVENT));
        assertEquals("world_sim.event.breakthrough.golden_core.1", t.getKey());
        Object[] args = t.getArgs();
        assertEquals(4, args.length);
        assertEquals("青云宫", assertInstanceOf(PlainTextContents.class, ((Component) args[0]).getContents()).text());
        assertEquals("world_sim.rank.elder", translatable((Component) args[1]).getKey());
        assertEquals("韩清漪", ((Component) args[2]).getString());
    }

    @Test
    void datesUseTheEraKeys() {
        TranslatableContents era = translatable(WorldSimText.date(SimDate.of(700, 600, 6)));
        assertEquals(SimDate.KEY_ERA, era.getKey());
        assertEquals("17", era.getArgs()[0]);
        TranslatableContents before = translatable(WorldSimText.date(SimDate.of(590, 600, 6)));
        assertEquals(SimDate.KEY_BEFORE_ERA, before.getKey());
        assertEquals("2", before.getArgs()[0]);
    }

    @Test
    void aRumorWrapsTheEventInItsPrefixKey() {
        TranslatableContents rumor = translatable(WorldSimRumors.message(EVENT));
        assertEquals(WorldSimRumors.RUMOR_KEY, rumor.getKey());
        assertEquals(EVENT.textKey(), translatable((Component) rumor.getArgs()[0]).getKey());
    }

    @Test
    void wordsFallBackToTheirIdWhenAKeyIsMissing() {
        TranslatableContents cause = translatable(WorldSimText.cause("struck_by_lightning"));
        assertEquals("commands.myvillage.world.cause.struck_by_lightning", cause.getKey());
        assertEquals("struck_by_lightning", cause.getFallback());
        assertEquals("world_sim.stage.golden_core.2", translatable(WorldSimText.stage("golden_core", 1)).getKey());
    }
}
