package com.example.myvillage.cultivation.qi;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SpiritualPowerTest {
    @Test
    void spendRefusalDoesNotOfferPartialDebitAndExactBalanceReachesZero() {
        assertTrue(SpiritualPower.spend(9, 100, 10).isEmpty());
        assertEquals(0, SpiritualPower.spend(10, 100, 10).orElseThrow());
        assertEquals(10, SpiritualPower.spend(10, 100, 0).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> SpiritualPower.spend(10, 100, -1));
    }

    @Test
    void oldOverCapSaveCannotSpendHiddenBalanceAndRecoveryCannotOverflow() {
        assertEquals(100, SpiritualPower.clamp(Long.MAX_VALUE, 100));
        assertTrue(SpiritualPower.spend(Long.MAX_VALUE, 100, 101).isEmpty());
        assertEquals(90, SpiritualPower.spend(Long.MAX_VALUE, 100, 10).orElseThrow());
        assertEquals(Long.MAX_VALUE, SpiritualPower.recover(Long.MAX_VALUE - 1, Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(0, SpiritualPower.recover(999, 0, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> SpiritualPower.recover(0, 100, -1));
    }

    @Test
    void profileStaysImmutableAndKeepsExistingWireVersion() {
        CultivationProfile before = CultivationProfile.defaultProfile().withCurrentSpiritualPower(10);
        CultivationProfile after = before.withCurrentSpiritualPower(
                SpiritualPower.spend(before.currentSpiritualPower(), 100, 10).orElseThrow());
        assertEquals(10, before.currentSpiritualPower());
        assertEquals(0, after.currentSpiritualPower());
        assertEquals(before.schemaVersion(), after.schemaVersion());
    }

    @Test
    void syncedStageCodecCarriesCapAndRejectsNegativeCap() {
        RealmStageDefinition stage = new RealmStageDefinition(
                ResourceLocation.parse("myvillage:qi_refining_1"), "stage.test", 0,
                Optional.empty(), Optional.empty(), Optional.empty(), 100);
        var encoded = RealmStageDefinition.CODEC.encodeStart(JsonOps.INSTANCE, stage).getOrThrow();
        assertEquals(100, encoded.getAsJsonObject().get("spiritual_power_cap").getAsLong());
        assertEquals(stage, RealmStageDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        encoded.getAsJsonObject().addProperty("spiritual_power_cap", -1);
        assertTrue(RealmStageDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).isError());
    }

    @Test
    void absentCapInOlderDefinitionsIsZeroRatherThanUnlimited() {
        var json = JsonParser.parseString("""
                {"id":"myvillage:old_stage","translation_key":"stage.old","sort_order":0}
                """);
        assertEquals(0, RealmStageDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().spiritualPowerCap());
    }
}
