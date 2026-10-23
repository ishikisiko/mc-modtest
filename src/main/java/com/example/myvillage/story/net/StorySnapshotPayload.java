package com.example.myvillage.story.net;

import com.example.myvillage.MyVillageMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Owning-player read-only story view. Empty dimension means that no direction is available. */
public record StorySnapshotPayload(long revision, boolean available, boolean accepted, boolean completed,
        String chapter, String title, String objective, String hint, String dimension, BlockPos target,
        List<String> journal) implements CustomPacketPayload {
    public static final int MAX_JOURNAL = 32;
    public static final int MAX_TEXT = 256;
    public static final int MAX_TITLE = 64;
    public static final Type<StorySnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "story_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorySnapshotPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public StorySnapshotPayload decode(RegistryFriendlyByteBuf b) {
            return StoryWire.decode(() -> {
                long revision = b.readLong();
                boolean available = b.readBoolean(), accepted = b.readBoolean(), completed = b.readBoolean();
                String chapter = b.readUtf(MAX_TITLE), title = b.readUtf(MAX_TITLE);
                String objective = b.readUtf(MAX_TEXT), hint = b.readUtf(MAX_TEXT), dimension = b.readUtf(MAX_TEXT);
                BlockPos target = b.readBlockPos();
                int count = StoryWire.count(b, MAX_JOURNAL, "journal");
                List<String> journal = new ArrayList<>(count);
                for (int i = 0; i < count; i++) journal.add(b.readUtf(MAX_TEXT));
                return new StorySnapshotPayload(revision, available, accepted, completed, chapter, title,
                        objective, hint, dimension, target, journal);
            });
        }
        @Override public void encode(RegistryFriendlyByteBuf b, StorySnapshotPayload p) {
            b.writeLong(p.revision()); b.writeBoolean(p.available()); b.writeBoolean(p.accepted()); b.writeBoolean(p.completed());
            b.writeUtf(p.chapter(), MAX_TITLE); b.writeUtf(p.title(), MAX_TITLE);
            b.writeUtf(p.objective(), MAX_TEXT); b.writeUtf(p.hint(), MAX_TEXT); b.writeUtf(p.dimension(), MAX_TEXT);
            b.writeBlockPos(p.target()); b.writeVarInt(p.journal().size());
            for (String item : p.journal()) b.writeUtf(item, MAX_TEXT);
        }
    };
    private static volatile Consumer<StorySnapshotPayload> receiver = ignored -> { };

    public StorySnapshotPayload {
        if (revision < 0) throw new IllegalArgumentException("negative story revision");
        chapter = StoryWire.text(chapter, MAX_TITLE, "chapter");
        title = StoryWire.text(title, MAX_TITLE, "title");
        objective = StoryWire.text(objective, MAX_TEXT, "objective");
        hint = StoryWire.text(hint, MAX_TEXT, "hint");
        dimension = StoryWire.text(dimension, MAX_TEXT, "dimension");
        if (!dimension.isEmpty() && ResourceLocation.tryParse(dimension) == null)
            throw new IllegalArgumentException("invalid story dimension");
        target = Objects.requireNonNull(target, "target").immutable();
        journal = StoryWire.texts(journal, MAX_JOURNAL, MAX_TEXT, "journal");
    }
    public static void installReceiver(Consumer<StorySnapshotPayload> next) { receiver = Objects.requireNonNull(next); }
    static void receive(StorySnapshotPayload payload) { receiver.accept(payload); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
