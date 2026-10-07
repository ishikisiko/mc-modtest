package com.example.myvillage.sim.runtime.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.myvillage.portrait.PortraitAssign;
import com.example.myvillage.portrait.PortraitSpec;
import com.example.myvillage.sim.runtime.player.SectDialogueScenes;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The sect dialogue page and the intent round-trip and reject what is out of bounds. */
class SectDialoguePayloadTest {
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

    private static <T> T roundTrip(StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                   T value) {
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

    private static final PortraitSpec PORTRAIT = PortraitAssign.of(4711, true, "golden_core", "elder", 7, 120.5,
            new int[] {1000, 4000, 2000, 2000, 1000}, new int[] {20, 30, 75, 40, 60}, 25, true);

    private static SectDialoguePayload page(List<Component> lines, List<Integer> options) {
        return new SectDialoguePayload(4711, 7, "青云宗", "steward", "李三", PORTRAIT, 61, 14, "韩立", "云山", "outer",
                -20, true, "ok", lines, options);
    }

    private static List<Component> lines() {
        return List.of(
                Component.translatable("world_sim.dialogue.steward.greet.1", "青云宗"),
                Component.translatable("world_sim.dialogue.steward.intro.1", "青云宗", "61", "14",
                        Component.translatable("screen.myvillage.sect_dialogue.none")),
                Component.translatable("world_sim.dialogue.steward.member.1", Component.translatable("world_sim.rank.outer")),
                Component.literal("plain"));
    }

    @Test
    void aPageRoundTrips() {
        SectDialoguePayload payload = page(lines(), List.of(0, 2));
        assertEquals(payload, roundTrip(SectDialoguePayload.STREAM_CODEC, payload));
        SectDialoguePayload empty = new SectDialoguePayload(0, 0, "", "elder", "", PORTRAIT, 0, 0, "", "", "", 0,
                false, "selective", List.of(), List.of());
        assertEquals(empty, roundTrip(SectDialoguePayload.STREAM_CODEC, empty));
        SectDialoguePayload full = page(Collections.nCopies(SectDialoguePayload.MAX_LINES, Component.literal("x")),
                List.of(0, 1, 2, 2));
        assertEquals(full, roundTrip(SectDialoguePayload.STREAM_CODEC, full));
    }

    @Test
    void aPageOutOfBoundsIsRefusedWhenBuilt() {
        assertThrows(IllegalArgumentException.class,
                () -> page(Collections.nCopies(SectDialoguePayload.MAX_LINES + 1, Component.literal("x")), List.of()));
        assertThrows(IllegalArgumentException.class, () -> page(List.of(), List.of(0, 1, 2, 2, 2)));
        assertThrows(IllegalArgumentException.class, () -> page(List.of(), List.of(SectDialoguePayload.MAX_OPTION_ID + 1)));
        assertThrows(IllegalArgumentException.class, () -> page(List.of(), List.of(-1)));
        assertThrows(IllegalArgumentException.class, () -> new SectDialoguePayload(1, 1, "x".repeat(65), "steward",
                "", PORTRAIT, 0, 0, "", "", "", 0, false, "ok", List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> new SectDialoguePayload(1, 1, "s", "steward", "", null, 0, 0,
                "", "", "", 0, false, "ok", List.of(), List.of()));
        assertEquals("x".repeat(64), SectDialoguePayload.clip("x".repeat(70), SectDialoguePayload.MAX_NAME));
        assertEquals("", SectDialoguePayload.clip(null, 4));
    }

    /** Writes the fixed head of a page up to the line count. */
    private static void head(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(1);
        buf.writeVarInt(2);
        buf.writeUtf("s");
        buf.writeUtf("steward");
        buf.writeUtf("a");
        PORTRAIT.write(buf);
        buf.writeVarInt(0);
        buf.writeVarInt(0);
        buf.writeUtf("");
        buf.writeUtf("");
        buf.writeUtf("");
        buf.writeVarInt(0);
        buf.writeBoolean(true);
        buf.writeUtf("ok");
    }

    @Test
    void aPageOutOfBoundsIsRejectedOnDecode() {
        RegistryFriendlyByteBuf tooManyLines = buffer();
        try {
            head(tooManyLines);
            tooManyLines.writeVarInt(SectDialoguePayload.MAX_LINES + 1);
            for (int i = 0; i <= SectDialoguePayload.MAX_LINES; i++) {
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(tooManyLines, Component.literal("x"));
            }
            tooManyLines.writeVarInt(0);
            assertThrows(DecoderException.class, () -> SectDialoguePayload.STREAM_CODEC.decode(tooManyLines));
        } finally {
            tooManyLines.release();
        }
        RegistryFriendlyByteBuf badOption = buffer();
        try {
            head(badOption);
            badOption.writeVarInt(0);
            badOption.writeVarInt(1);
            badOption.writeByte(SectDialoguePayload.MAX_OPTION_ID + 1);
            assertThrows(DecoderException.class, () -> SectDialoguePayload.STREAM_CODEC.decode(badOption));
        } finally {
            badOption.release();
        }
        RegistryFriendlyByteBuf tooManyOptions = buffer();
        try {
            head(tooManyOptions);
            tooManyOptions.writeVarInt(0);
            tooManyOptions.writeVarInt(SectDialoguePayload.MAX_OPTIONS + 1);
            for (int i = 0; i <= SectDialoguePayload.MAX_OPTIONS; i++) {
                tooManyOptions.writeByte(2);
            }
            assertThrows(DecoderException.class, () -> SectDialoguePayload.STREAM_CODEC.decode(tooManyOptions));
        } finally {
            tooManyOptions.release();
        }
        RegistryFriendlyByteBuf badPortrait = buffer();
        try {
            badPortrait.writeVarInt(1);
            badPortrait.writeVarInt(2);
            badPortrait.writeUtf("s");
            badPortrait.writeUtf("steward");
            badPortrait.writeUtf("a");
            badPortrait.writeByte(0);
            badPortrait.writeByte(PortraitSpec.Face.values().length); // one past the last face
            assertThrows(DecoderException.class, () -> SectDialoguePayload.STREAM_CODEC.decode(badPortrait));
        } finally {
            badPortrait.release();
        }
        RegistryFriendlyByteBuf longName = buffer();
        try {
            longName.writeVarInt(1);
            longName.writeVarInt(2);
            longName.writeUtf("x".repeat(65));
            assertThrows(DecoderException.class, () -> SectDialoguePayload.STREAM_CODEC.decode(longName));
        } finally {
            longName.release();
        }
    }

    @Test
    void everyIntentRoundTrips() {
        List<SectIntentPayload> intents = new ArrayList<>();
        for (SectDialogueScenes.Option option : SectDialogueScenes.Option.values()) {
            intents.add(new SectIntentPayload(option, 4711, 7));
        }
        intents.add(new SectIntentPayload(SectIntentPayload.JOIN, -1, -1));
        for (SectIntentPayload intent : intents) {
            assertEquals(intent, roundTrip(SectIntentPayload.STREAM_CODEC, intent));
            assertEquals(SectDialogueScenes.Option.of(intent.kind()), intent.option());
        }
        assertEquals(SectDialogueScenes.Option.JOIN.id(), SectIntentPayload.JOIN);
        assertEquals(SectDialogueScenes.Option.LEAVE.id(), SectIntentPayload.LEAVE);
        assertEquals(SectDialogueScenes.Option.FAREWELL.id(), SectIntentPayload.FAREWELL);
        assertEquals(SectDialogueScenes.Option.APPRENTICE.id(), SectIntentPayload.APPRENTICE);
        assertEquals(SectDialogueScenes.Option.TASK_ACCEPT.id(), SectIntentPayload.TASK_ACCEPT);
        assertEquals(SectDialogueScenes.Option.TASK_TURN_IN.id(), SectIntentPayload.TASK_TURN_IN);
        assertEquals(SectDialoguePayload.MAX_OPTION_ID, SectIntentPayload.MAX_KIND);
        assertEquals(List.of("kind", "entityId", "sectId"),
                java.util.Arrays.stream(SectIntentPayload.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList(),
                "an intent carries the choice and what it is about, nothing the server must trust");
    }

    @Test
    void anUnknownIntentKindIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SectIntentPayload((byte) 6, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SectIntentPayload((byte) -1, 1, 1));
        for (int kind : new int[] {6, 255}) {
            RegistryFriendlyByteBuf buf = buffer();
            try {
                buf.writeByte(kind);
                buf.writeVarInt(1);
                buf.writeVarInt(1);
                assertThrows(DecoderException.class, () -> SectIntentPayload.STREAM_CODEC.decode(buf));
            } finally {
                buf.release();
            }
        }
    }

    @Test
    void typeIds() {
        assertEquals("myvillage:sect_dialogue", SectDialoguePayload.TYPE.id().toString());
        assertEquals("myvillage:sect_intent", SectIntentPayload.TYPE.id().toString());
    }
}
