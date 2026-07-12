package com.example.myvillage.cultivation.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.cultivation.CultivationProfile;
import com.example.myvillage.cultivation.SpiritualRoot;
import com.example.myvillage.cultivation.TechniqueProgress;
import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class CultivationSnapshotPayloadTest {
    @Test
    void actualRegistryFriendlyStreamCodecRoundTripsCompleteSnapshot() {
        CultivationProfile profile = new CultivationProfile(
                1,
                id("removed_pack", "lost_realm"),
                id("removed_pack", "lost_stage"),
                123_456_789L,
                88,
                987_654_321L,
                Optional.of(new SpiritualRoot(Map.of(
                        id("myvillage", "fire"), 7_000,
                        id("removed_pack", "storm"), 3_000))),
                Map.of(
                        id("myvillage", "basic_breathing"), new TechniqueProgress(17),
                        id("removed_pack", "lost_art"), new TechniqueProgress(23)));
        CultivationSnapshotPayload payload = new CultivationSnapshotPayload(profile);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);

        try {
            CultivationSnapshotPayload.STREAM_CODEC.encode(buffer, payload);
            assertTrue(buffer.readableBytes() > 0);

            CultivationSnapshotPayload decoded =
                    CultivationSnapshotPayload.STREAM_CODEC.decode(buffer);

            assertEquals(payload, decoded);
            assertEquals(profile, decoded.profile());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
