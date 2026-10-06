package com.example.myvillage.cultivation.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class CoreTechniqueSwitchPayloadTest {
    @Test
    void codecRoundTripsOnlyTheTechniqueId() {
        CoreTechniqueSwitchPayload payload = new CoreTechniqueSwitchPayload(
                ResourceLocation.fromNamespaceAndPath("myvillage", "gengjin_yinqi_fa"));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            CoreTechniqueSwitchPayload.STREAM_CODEC.encode(buffer, payload);
            RegistryFriendlyByteBuf expected = new RegistryFriendlyByteBuf(
                    Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
            try {
                expected.writeResourceLocation(payload.techniqueId());
                assertEquals(expected.readableBytes(), buffer.readableBytes());
            } finally {
                expected.release();
            }
            assertEquals(payload, CoreTechniqueSwitchPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
        assertEquals("myvillage:core_technique_switch", CoreTechniqueSwitchPayload.TYPE.id().toString());
    }

    @Test
    void nullTechniqueIsRejected() {
        assertThrows(NullPointerException.class, () -> new CoreTechniqueSwitchPayload(null));
    }
}
