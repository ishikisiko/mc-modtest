package com.example.myvillage.combat.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.DodgeDirection;
import com.example.myvillage.combat.session.CombatStopReason;
import com.example.myvillage.network.ModPayloads;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class CombatPayloadTest {
    private static final ResourceLocation MOVE =
            ResourceLocation.fromNamespaceAndPath("myvillage", "basic_sword_01_thrust");

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
                        8,
                        -37.5F),
                CombatAttackStartPayload.STREAM_CODEC);
        assertRoundTrip(
                new CombatAttackStopPayload(42, 8, CombatStopReason.WEAPON_CHANGED),
                CombatAttackStopPayload.STREAM_CODEC);
        assertRoundTrip(
                new CombatHitConfirmPayload(42, 8, 2),
                CombatHitConfirmPayload.STREAM_CODEC);
        assertRoundTrip(
                new CombatImpactPayload(
                        42,
                        8,
                        ResourceLocation.fromNamespaceAndPath("myvillage", "basic_sword_05_lunge_thrust"),
                        List.of(7, 99),
                        List.of(new Vec3(1.25, 65.5, -3.0), new Vec3(-0.125, 64.0, 2.75))),
                CombatImpactPayload.STREAM_CODEC);
    }

    @Test
    void impactPayloadNeedsAlignedBoundedContacts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CombatImpactPayload(42, 8, MOVE, List.of(), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CombatImpactPayload(42, 8, MOVE, List.of(1, 2), List.of(Vec3.ZERO)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CombatImpactPayload(42, 0, MOVE, List.of(1), List.of(Vec3.ZERO)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CombatImpactPayload(42, 8, MOVE, List.of(1), List.of(new Vec3(Double.NaN, 0.0, 0.0))));
    }

    @Test
    void impactNamesTheMoveByIdAndProtocolIsBumped() {
        CombatImpactPayload impact = new CombatImpactPayload(42, 8, MOVE, List.of(1), List.of(Vec3.ZERO));
        assertEquals(MOVE, impact.moveId());
        assertThrows(
                NullPointerException.class,
                () -> new CombatImpactPayload(42, 8, null, List.of(1), List.of(Vec3.ZERO)));
        // Attacker, revision, move id, struck ids and contact points only: no damage or health.
        assertEquals(
                List.of("attackerEntityId", "revision", "moveId", "struckEntityIds", "contactPoints"),
                java.util.Arrays.stream(CombatImpactPayload.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList());
        assertEquals("15", ModPayloads.PROTOCOL_VERSION);
    }

    @Test
    void hitConfirmationRequiresAnActionAndAHit() {
        assertThrows(IllegalArgumentException.class, () -> new CombatHitConfirmPayload(42, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CombatHitConfirmPayload(42, 8, 0));
        assertThrows(IllegalArgumentException.class, () -> new CombatHitConfirmPayload(-1, 8, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CombatAttackStartPayload(
                        42,
                        ResourceLocation.fromNamespaceAndPath("myvillage", "basic_sword_01_thrust"),
                        1,
                        1,
                        Float.NaN));
    }

    @Test
    void dodgeIntentIsOneByteOfMovementInput() {
        assertEquals(
                List.of("direction"),
                java.util.Arrays.stream(CombatDodgeIntentPayload.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList());
        for (DodgeDirection direction : DodgeDirection.values()) {
            RegistryFriendlyByteBuf buffer = buffer();
            try {
                CombatDodgeIntentPayload payload = new CombatDodgeIntentPayload(direction);
                CombatDodgeIntentPayload.STREAM_CODEC.encode(buffer, payload);
                assertEquals(1, buffer.readableBytes());
                assertEquals(payload, CombatDodgeIntentPayload.STREAM_CODEC.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
        assertThrows(NullPointerException.class, () -> new CombatDodgeIntentPayload(null));
        RegistryFriendlyByteBuf malformed = buffer();
        try {
            malformed.writeByte(DodgeDirection.values().length);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CombatDodgeIntentPayload.STREAM_CODEC.decode(malformed));
        } finally {
            malformed.release();
        }
    }

    @Test
    void dodgeStartRoundTripsAndRejectsOutOfBoundsFields() {
        ResourceLocation technique = ResourceLocation.fromNamespaceAndPath("myvillage", "taxue_wuhen");
        CombatDodgeStartPayload start = new CombatDodgeStartPayload(42, 1_234L, -135.0F, 4.5F, 7, 7, 24, technique);
        assertRoundTrip(start, CombatDodgeStartPayload.STREAM_CODEC);
        assertRoundTrip(
                new CombatDodgeStartPayload(0, 0L, 180.0F, 0.1F, 0, 6, 0, technique),
                CombatDodgeStartPayload.STREAM_CODEC);
        assertNotNull(start.type());

        assertThrows(IllegalArgumentException.class,
                () -> new CombatDodgeStartPayload(-1, 1L, 0.0F, 3.5F, 5, 6, 30, technique));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatDodgeStartPayload(42, -1L, 0.0F, 3.5F, 5, 6, 30, technique));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatDodgeStartPayload(42, 1L, 0.0F, 0.0F, 5, 6, 30, technique));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatDodgeStartPayload(42, 1L, 0.0F, Float.NaN, 5, 6, 30, technique));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatDodgeStartPayload(42, 1L, 0.0F, Float.POSITIVE_INFINITY, 5, 6, 30, technique));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatDodgeStartPayload(42, 1L, Float.NaN, 3.5F, 5, 6, 30, technique));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatDodgeStartPayload(42, 1L, 0.0F, 3.5F, -1, 6, 30, technique));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatDodgeStartPayload(42, 1L, 0.0F, 3.5F, 5, 6, -1, technique));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatDodgeStartPayload(42, 1L, 0.0F, 3.5F, 7, 6, 30, technique));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatDodgeStartPayload(42, 1L, 0.0F, 3.5F, 0, 0, 30, technique));
        assertThrows(NullPointerException.class,
                () -> new CombatDodgeStartPayload(42, 1L, 0.0F, 3.5F, 5, 6, 30, null));
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
