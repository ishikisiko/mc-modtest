package com.example.myvillage.sim.runtime.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The scripture hall and the borrow intent round-trip and reject what is out of bounds. */
class ScriptureHallPayloadTest {
    private static final BlockPos SHELF = new BlockPos(-1203, 87, 4410);

    @BeforeAll
    static void bootstrap() {
        // the component codec reaches ItemStack (hover events), which needs the registries
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static <T> T roundTrip(StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = buffer();
        try {
            codec.encode(buf, value);
            T back = codec.decode(buf);
            assertEquals(0, buf.readableBytes(), "the decoder reads everything the encoder wrote");
            return back;
        } finally {
            buf.release();
        }
    }

    private static ScriptureHallPayload.Entry entry(String id, int grade, boolean borrowed) {
        return new ScriptureHallPayload.Entry(id, Component.translatable("technique.myvillage." + id),
                grade, "sword", borrowed, 0);
    }

    private static ScriptureHallPayload hall(List<ScriptureHallPayload.Entry> entries) {
        return new ScriptureHallPayload(SHELF, 7, "太白剑宗", "inner", true, "ok", entries);
    }

    @Test
    void aHallRoundTrips() {
        ScriptureHallPayload payload = hall(List.of(entry("taibai_sword_heart", 1, true),
                entry("taibai_flying_sword", 2, false),
                new ScriptureHallPayload.Entry("x", Component.literal("plain"), 4, "", false, 300)));
        assertEquals(payload, roundTrip(ScriptureHallPayload.STREAM_CODEC, payload));
        ScriptureHallPayload refused = new ScriptureHallPayload(BlockPos.ZERO, 0, "", "", false, "not_member",
                List.of());
        assertEquals(refused, roundTrip(ScriptureHallPayload.STREAM_CODEC, refused));
        ScriptureHallPayload full = hall(Collections.nCopies(ScriptureHallPayload.MAX_ENTRIES,
                entry("m".repeat(ScriptureHallPayload.MAX_TECHNIQUE_ID), 0, false)));
        assertEquals(full, roundTrip(ScriptureHallPayload.STREAM_CODEC, full));
    }

    @Test
    void aHallOutOfBoundsIsRefusedWhenBuilt() {
        assertThrows(IllegalArgumentException.class,
                () -> hall(Collections.nCopies(ScriptureHallPayload.MAX_ENTRIES + 1, entry("a", 1, false))));
        assertThrows(IllegalArgumentException.class, () -> entry("a", 5, false));
        assertThrows(IllegalArgumentException.class, () -> entry("a", -1, false));
        assertThrows(IllegalArgumentException.class, () -> entry("a".repeat(65), 1, false));
        assertThrows(IllegalArgumentException.class,
                () -> new ScriptureHallPayload.Entry("a", Component.literal("a"), 1, "c".repeat(17), false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ScriptureHallPayload.Entry("a", Component.literal("a"), 1, "c", false, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new ScriptureHallPayload(SHELF, 1, "x".repeat(65), "", false, "ok", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ScriptureHallPayload(SHELF, 1, "", "r".repeat(17), false, "ok", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ScriptureHallPayload(SHELF, 1, "", "", false, "r".repeat(33), List.of()));
        assertEquals("x".repeat(64), ScriptureHallPayload.clip("x".repeat(70), ScriptureHallPayload.MAX_NAME));
        assertEquals("", ScriptureHallPayload.clip(null, 4));
    }

    /** Writes the fixed head of a hall up to the entry count. */
    private static void head(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(SHELF);
        buf.writeVarInt(7);
        buf.writeUtf("s");
        buf.writeUtf("outer");
        buf.writeBoolean(true);
        buf.writeUtf("ok");
    }

    private static void rawEntry(RegistryFriendlyByteBuf buf, int grade, int cost) {
        buf.writeUtf("a");
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, Component.literal("a"));
        buf.writeByte(grade);
        buf.writeUtf("sword");
        buf.writeBoolean(false);
        buf.writeVarInt(cost);
    }

    private static void assertRejected(java.util.function.Consumer<RegistryFriendlyByteBuf> writer) {
        RegistryFriendlyByteBuf buf = buffer();
        try {
            writer.accept(buf);
            assertThrows(DecoderException.class, () -> ScriptureHallPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void aHallOutOfBoundsIsRejectedOnDecode() {
        assertRejected(buf -> {
            head(buf);
            buf.writeVarInt(ScriptureHallPayload.MAX_ENTRIES + 1);
            for (int i = 0; i <= ScriptureHallPayload.MAX_ENTRIES; i++) {
                rawEntry(buf, 1, 0);
            }
        });
        assertRejected(buf -> {
            head(buf);
            buf.writeVarInt(-1);
        });
        assertRejected(buf -> {
            head(buf);
            buf.writeVarInt(1);
            rawEntry(buf, 5, 0);
        });
        assertRejected(buf -> {
            head(buf);
            buf.writeVarInt(1);
            rawEntry(buf, 1, -3);
        });
        assertRejected(buf -> {
            buf.writeBlockPos(SHELF);
            buf.writeVarInt(7);
            buf.writeUtf("x".repeat(65));
        });
        assertRejected(buf -> {
            head(buf);
            buf.writeVarInt(1);
            buf.writeUtf("t".repeat(65));
        });
    }

    @Test
    void aBorrowRoundTripsAndCarriesOnlyTheIntent() {
        for (ScriptureBorrowPayload borrow : List.of(new ScriptureBorrowPayload(SHELF, "taibai_sword_heart"),
                new ScriptureBorrowPayload(BlockPos.ZERO, ""),
                new ScriptureBorrowPayload(SHELF, "t".repeat(ScriptureBorrowPayload.MAX_TECHNIQUE_ID)))) {
            assertEquals(borrow, roundTrip(ScriptureBorrowPayload.STREAM_CODEC, borrow));
        }
        assertEquals(List.of("pos", "techniqueId"),
                java.util.Arrays.stream(ScriptureBorrowPayload.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList(),
                "a borrow carries the shelf and the technique, nothing the server must trust");
    }

    @Test
    void aBorrowOutOfBoundsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ScriptureBorrowPayload(SHELF, "t".repeat(65)));
        assertThrows(NullPointerException.class, () -> new ScriptureBorrowPayload(SHELF, null));
        assertThrows(NullPointerException.class, () -> new ScriptureBorrowPayload(null, "a"));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeBlockPos(SHELF);
            buf.writeUtf("t".repeat(65));
            assertThrows(DecoderException.class, () -> ScriptureBorrowPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    @Test
    void typeIds() {
        assertEquals("myvillage:scripture_hall", ScriptureHallPayload.TYPE.id().toString());
        assertEquals("myvillage:scripture_borrow", ScriptureBorrowPayload.TYPE.id().toString());
    }
}
