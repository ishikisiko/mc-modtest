package com.example.myvillage.combat.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.session.CombatStopReason;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class CombatPayloadTest {
    @Test
    void c2sIntentsCarryNoAuthorityFieldsOrBytes() {
        assertEquals(0, CombatModeTogglePayload.class.getRecordComponents().length);
        assertEquals(0, SwordAttackIntentPayload.class.getRecordComponents().length);

        RegistryFriendlyByteBuf buffer = buffer();
        try {
            CombatModeTogglePayload.STREAM_CODEC.encode(buffer, CombatModeTogglePayload.INSTANCE);
            SwordAttackIntentPayload.STREAM_CODEC.encode(buffer, SwordAttackIntentPayload.INSTANCE);
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void clientboundPayloadsRoundTripBoundedAuthority() {
        assertRoundTrip(
                new CombatModeSnapshotPayload(CombatMode.CULTIVATION, 7),
                CombatModeSnapshotPayload.STREAM_CODEC);
        assertRoundTrip(
                new CombatAttackStartPayload(
                        42,
                        ResourceLocation.fromNamespaceAndPath(
                                "myvillage", "basic_sword_03_rising_cut"),
                        1_234,
                        8),
                CombatAttackStartPayload.STREAM_CODEC);
        assertRoundTrip(
                new CombatAttackStopPayload(42, 8, CombatStopReason.WEAPON_CHANGED),
                CombatAttackStopPayload.STREAM_CODEC);
    }

    @Test
    void malformedEnumsAreRejected() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            buffer.writeByte(255);
            buffer.writeVarLong(0);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CombatModeSnapshotPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static <T> void assertRoundTrip(
            T payload,
            net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            codec.encode(buffer, payload);
            assertEquals(payload, codec.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);
    }
}
