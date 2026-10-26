package com.example.myvillage.cultivation.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.example.myvillage.cultivation.crafting.CraftingAptitudes;
import com.example.myvillage.cultivation.fate.FateState;

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
                CultivationProfile.CURRENT_SCHEMA_VERSION,
                id("removed_pack", "lost_realm"),
                id("removed_pack", "lost_stage"),
                123_456_789L,
                88,
                987_654_321L,
                37,
                12_345L,
                678L,
                Optional.of(new SpiritualRoot(Map.of(
                        id("myvillage", "fire"), 7_000,
                        id("removed_pack", "storm"), 3_000))),
                Map.of(
                        id("myvillage", "basic_breathing"), new TechniqueProgress(17),
                        id("removed_pack", "lost_art"), new TechniqueProgress(23)),
                Optional.of(id("myvillage", "basic_breathing")));
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

    @Test
    void snapshotTransmitsActualInitializedScoresAndServerDerivedCaps() {
        var crafting = new CraftingAptitudes(1, true, 39, 90, 73);
        var payload = new CultivationSnapshotPayload(CultivationProfile.defaultProfile(), FateState.empty(), 8,
                crafting, 1, 4, 3);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            CultivationSnapshotPayload.STREAM_CODEC.encode(buffer, payload);
            var decoded = CultivationSnapshotPayload.STREAM_CODEC.decode(buffer);
            assertEquals(payload, decoded);
            assertEquals(crafting, decoded.crafting());
            assertEquals(1, decoded.alchemyQualityCap());
            assertEquals(4, decoded.forgingQualityCap());
            assertEquals(3, decoded.talismanQualityCap());
            assertEquals(73, decoded.crafting().talisman());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test
    void pendingAndInitializedCapBoundsAreValidatedWithoutClientRuleRecalculation() {
        var profile = CultivationProfile.defaultProfile();
        var fates = FateState.empty();
        assertEquals(0, new CultivationSnapshotPayload(profile).alchemyQualityCap());
        assertThrows(IllegalArgumentException.class,
                () -> new CultivationSnapshotPayload(profile, fates, 8, CraftingAptitudes.EMPTY, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CultivationSnapshotPayload(profile, fates, 8, CraftingAptitudes.EMPTY, 0, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new CultivationSnapshotPayload(profile, fates, 8, new CraftingAptitudes(1, true, 0, 0, 90), 1, 1, 0));
        var initialized = new CraftingAptitudes(1, true, 0, 100);
        assertThrows(IllegalArgumentException.class,
                () -> new CultivationSnapshotPayload(profile, fates, 8, initialized, 0, 4));
        assertThrows(IllegalArgumentException.class,
                () -> new CultivationSnapshotPayload(profile, fates, 8, initialized, 1, 5));
        assertThrows(NullPointerException.class,
                () -> new CultivationSnapshotPayload(profile, fates, 8, null, 0, 0));
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
