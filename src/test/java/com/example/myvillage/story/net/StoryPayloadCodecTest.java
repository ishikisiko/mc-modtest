package com.example.myvillage.story.net;

import static org.junit.jupiter.api.Assertions.*;

import com.example.myvillage.portrait.PortraitAssign;
import com.example.myvillage.portrait.PortraitSpec;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

/** Wire bounds are enforced before list allocation and at construction on both send and receive. */
class StoryPayloadCodecTest {
    private static final PortraitSpec PORTRAIT = PortraitAssign.of(17, false, "qi_refining", "rogue", 0, 18,
            new int[]{2000, 2000, 2000, 2000, 2000}, new int[]{20, 30, 50, 40, 60}, 18, true);
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }
    private static <T> T roundTrip(StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T value) {
        var b = buffer();
        try { codec.encode(b, value); T result = codec.decode(b); assertEquals(0, b.readableBytes()); return result; }
        finally { b.release(); }
    }
    private static StoryDialoguePayload page(List<String> lines, List<StoryDialoguePayload.Option> options) {
        return new StoryDialoguePayload(17, Long.MAX_VALUE, "zhou_he", "周禾", PORTRAIT, "灯尽之前", lines, options);
    }
    private static StorySnapshotPayload snapshot(List<String> journal) {
        return new StorySnapshotPayload(3, true, true, false, "qianzhen_chapter_1", "灯尽之前",
                "核对旧铭", "先请陆衡说明", "minecraft:overworld", new BlockPos(-10, 70, 55), journal);
    }
    @Test void completeChinesePagesAndSnapshotRoundTrip() {
        var page = page(Collections.nCopies(6, "药圃旧铭。".repeat(51)), List.of(
                new StoryDialoguePayload.Option("choose_medical", "检样".repeat(48)),
                new StoryDialoguePayload.Option("choose_road", "护路"),
                new StoryDialoguePayload.Option("return", "再问"), new StoryDialoguePayload.Option("close", "告辞")));
        assertEquals(page, roundTrip(StoryDialoguePayload.STREAM_CODEC, page));
        var snapshot = snapshot(Collections.nCopies(32, "行".repeat(256)));
        assertEquals(snapshot, roundTrip(StorySnapshotPayload.STREAM_CODEC, snapshot));
        for (String id : List.of("close", "accept", "choose_road", "a".repeat(64))) {
            var intent = new StoryIntentPayload(17, 42, id);
            assertEquals(intent, roundTrip(StoryIntentPayload.STREAM_CODEC, intent));
        }
    }
    @Test void constructorsRefuseInvalidAndAmbiguousOptions() {
        for (String id : List.of("", "x".repeat(65), "next node", "../bad/input", "汉字", "close\n"))
            assertThrows(IllegalArgumentException.class, () -> new StoryIntentPayload(17, 1, id));
        assertThrows(IllegalArgumentException.class, () -> new StoryIntentPayload(-1, 1, "close"));
        assertThrows(IllegalArgumentException.class, () -> new StoryDialoguePayload.Option("ok", "x".repeat(97)));
        assertThrows(IllegalArgumentException.class, () -> new StoryDialoguePayload.Option("ok", " "));
        var close = new StoryDialoguePayload.Option("close", "告辞");
        assertThrows(IllegalArgumentException.class, () -> page(List.of(), List.of(close, close)));
        assertThrows(IllegalArgumentException.class, () -> page(List.of(), Collections.nCopies(5, close)));
        assertThrows(IllegalArgumentException.class, () -> page(Collections.nCopies(7, "x"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> page(List.of("x".repeat(257)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> snapshot(Collections.nCopies(33, "x")));
        assertThrows(IllegalArgumentException.class, () -> snapshot(List.of("x".repeat(257))));
    }
    @Test void payloadCollectionsAndTargetAreDefensiveCopies() {
        var lines = new ArrayList<>(List.of("原话"));
        var options = new ArrayList<>(List.of(new StoryDialoguePayload.Option("close", "告辞")));
        var p = page(lines, options); lines.clear(); options.clear();
        assertEquals(List.of("原话"), p.lines()); assertEquals(1, p.options().size());
        assertThrows(UnsupportedOperationException.class, () -> p.lines().clear());
        var target = new BlockPos.MutableBlockPos(1, 2, 3);
        var snapshot = new StorySnapshotPayload(0, true, false, false, "", "", "", "", "", target, List.of());
        target.set(9, 9, 9); assertEquals(new BlockPos(1, 2, 3), snapshot.target());
    }
    private static void dialogueHead(RegistryFriendlyByteBuf b) {
        b.writeVarInt(17); b.writeLong(42); b.writeUtf("guide"); b.writeUtf("程砚"); PORTRAIT.write(b); b.writeUtf("灯尽之前");
    }
    @Test void maliciousCountsFailBeforeReadingItems() {
        for (int count : new int[]{-1, 7, Integer.MAX_VALUE}) {
            var b = buffer();
            try { dialogueHead(b); b.writeVarInt(count);
                assertThrows(DecoderException.class, () -> StoryDialoguePayload.STREAM_CODEC.decode(b)); }
            finally { b.release(); }
        }
        for (int count : new int[]{-1, 5, Integer.MAX_VALUE}) {
            var b = buffer();
            try { dialogueHead(b); b.writeVarInt(0); b.writeVarInt(count);
                assertThrows(DecoderException.class, () -> StoryDialoguePayload.STREAM_CODEC.decode(b)); }
            finally { b.release(); }
        }
        for (int count : new int[]{-1, 33, Integer.MAX_VALUE}) {
            var b = buffer();
            try {
                b.writeLong(0); b.writeBoolean(true); b.writeBoolean(false); b.writeBoolean(false);
                for (int i = 0; i < 5; i++) b.writeUtf(""); b.writeBlockPos(BlockPos.ZERO); b.writeVarInt(count);
                assertThrows(DecoderException.class, () -> StorySnapshotPayload.STREAM_CODEC.decode(b));
            } finally { b.release(); }
        }
    }
    @Test void intentDecodeRefusesInvalidIdentifiersAndOverlengthUtf() {
        for (String id : List.of("", "x".repeat(65), "bad id", "close\n")) {
            var b = buffer();
            try {
                b.writeVarInt(17); b.writeLong(1); b.writeUtf(id);
                assertThrows(DecoderException.class, () -> StoryIntentPayload.STREAM_CODEC.decode(b));
            } finally { b.release(); }
        }
    }
    @Test void duplicateOptionsCannotEnterFromTheWire() {
        var b = buffer();
        try {
            dialogueHead(b); b.writeVarInt(0); b.writeVarInt(2);
            b.writeUtf("close"); b.writeUtf("告辞"); b.writeUtf("close"); b.writeUtf("伪造重复项");
            assertThrows(DecoderException.class, () -> StoryDialoguePayload.STREAM_CODEC.decode(b));
        } finally { b.release(); }
    }
    @Test void intentContainsNoClientSuppliedProgressOrReward() {
        assertEquals(List.of("entityId", "nonce", "optionId"),
                java.util.Arrays.stream(StoryIntentPayload.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList());
    }
}
