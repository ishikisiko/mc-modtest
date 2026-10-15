package com.example.myvillage.alchemy;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PillQualityTest {
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null)
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void qualityCodecRoundTripsEveryLegalValueAndRejectsCorruption() {
        for (int quality = 1; quality <= 4; quality++) {
            var encoded = PillQuality.CODEC.encodeStart(JsonOps.INSTANCE, quality).getOrThrow();
            assertEquals(quality, PillQuality.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
            var buffer = Unpooled.buffer();
            try {
                PillQuality.STREAM_CODEC.encode(buffer, quality);
                assertEquals(quality, PillQuality.STREAM_CODEC.decode(buffer));
                assertFalse(buffer.isReadable());
            } finally { buffer.release(); }
        }
        for (int invalid : new int[] {Integer.MIN_VALUE, 0, 5, Integer.MAX_VALUE}) {
            assertTrue(PillQuality.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(invalid)).error().isPresent());
            assertTrue(PillQuality.CODEC.encodeStart(JsonOps.INSTANCE, invalid).error().isPresent());
            var buffer = Unpooled.buffer();
            try {
                ByteBufCodecs.VAR_INT.encode(buffer, invalid);
                assertThrows(IllegalArgumentException.class, () -> PillQuality.STREAM_CODEC.decode(buffer));
                assertThrows(IllegalArgumentException.class, () -> PillQuality.STREAM_CODEC.encode(buffer, invalid));
            } finally { buffer.release(); }
        }
    }

    @Test void canonicalLowerQualityPreservesLegacyStackEqualityAndHighQualitiesStaySeparate() {
        DataComponentType<Integer> type = DataComponentType.<Integer>builder()
                .persistent(PillQuality.CODEC).networkSynchronized(PillQuality.STREAM_CODEC).build();
        ItemStack legacy = new ItemStack(Items.PAPER, 2);
        ItemStack fresh = legacy.copy();
        PillQuality.apply(fresh, 1, type);
        assertEquals(1, PillQuality.get(fresh, type));
        assertFalse(fresh.has(type));
        assertTrue(ItemStack.isSameItemSameComponents(legacy, fresh));
        for (int quality = 2; quality <= 4; quality++) {
            PillQuality.apply(fresh, quality, type);
            assertEquals(quality, PillQuality.get(fresh, type));
            assertFalse(ItemStack.isSameItemSameComponents(legacy, fresh));
        }
        ItemStack high = fresh.copy();
        PillQuality.apply(high, 3, type);
        assertFalse(ItemStack.isSameItemSameComponents(high, fresh));
        PillQuality.apply(fresh, 1, type);
        assertTrue(ItemStack.isSameItemSameComponents(legacy, fresh));
        // Explicit one has baseline strength, with ordinary vanilla component identity.
        fresh.set(type, 1);
        assertEquals(1, PillQuality.get(fresh, type));
        assertFalse(ItemStack.isSameItemSameComponents(legacy, fresh));
        fresh.set(type, Integer.MAX_VALUE);
        assertEquals(1, PillQuality.get(fresh, type));
        assertThrows(IllegalArgumentException.class, () -> PillQuality.apply(fresh, 5, type));
        assertEquals(Integer.MAX_VALUE, fresh.get(type));
    }

    @Test void qualityNamesUseLocalizedKeysAndRejectOutOfRangeInputs() {
        for (int quality = 1; quality <= 4; quality++)
            assertEquals("alchemy.myvillage.quality." + quality,
                    ((TranslatableContents) PillQuality.name(quality).getContents()).getKey());
        assertThrows(IllegalArgumentException.class, () -> PillQuality.name(0));
        assertThrows(IllegalArgumentException.class, () -> PillQuality.name(5));
    }
}
