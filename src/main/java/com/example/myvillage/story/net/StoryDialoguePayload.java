package com.example.myvillage.story.net;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.portrait.PortraitSpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A complete server-authored page; options are identifiers, never next-node instructions. */
public record StoryDialoguePayload(int entityId, long nonce, String role, String name, PortraitSpec portrait,
        String title, List<String> lines, List<Option> options) implements CustomPacketPayload {
    public static final int MAX_LINES = 6;
    public static final int MAX_OPTIONS = 4;
    public static final int MAX_LINE = 256;
    public static final int MAX_TITLE = 64;
    public static final int MAX_OPTION_TEXT = 96;
    public static final Type<StoryDialoguePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "story_dialogue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StoryDialoguePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public StoryDialoguePayload decode(RegistryFriendlyByteBuf b) {
            return StoryWire.decode(() -> {
                int entity = b.readVarInt(); long nonce = b.readLong();
                String role = b.readUtf(MAX_TITLE), name = b.readUtf(MAX_TITLE);
                PortraitSpec portrait = PortraitSpec.read(b);
                String title = b.readUtf(MAX_TITLE);
                int count = StoryWire.count(b, MAX_LINES, "lines");
                List<String> lines = new ArrayList<>(count);
                for (int i = 0; i < count; i++) lines.add(b.readUtf(MAX_LINE));
                int optionCount = StoryWire.count(b, MAX_OPTIONS, "options");
                List<Option> options = new ArrayList<>(optionCount);
                for (int i = 0; i < optionCount; i++) options.add(new Option(b.readUtf(64), b.readUtf(MAX_OPTION_TEXT)));
                return new StoryDialoguePayload(entity, nonce, role, name, portrait, title, lines, options);
            });
        }
        @Override public void encode(RegistryFriendlyByteBuf b, StoryDialoguePayload p) {
            b.writeVarInt(p.entityId()); b.writeLong(p.nonce()); b.writeUtf(p.role(), MAX_TITLE); b.writeUtf(p.name(), MAX_TITLE);
            p.portrait().write(b); b.writeUtf(p.title(), MAX_TITLE);
            b.writeVarInt(p.lines().size());
            for (String line : p.lines()) b.writeUtf(line, MAX_LINE);
            b.writeVarInt(p.options().size());
            for (Option option : p.options()) { b.writeUtf(option.id(), 64); b.writeUtf(option.text(), MAX_OPTION_TEXT); }
        }
    };
    private static volatile Consumer<StoryDialoguePayload> receiver = ignored -> { };

    public record Option(String id, String text) {
        public Option {
            id = StoryWire.optionId(id);
            text = StoryWire.text(text, MAX_OPTION_TEXT, "option text");
            if (text.isBlank()) throw new IllegalArgumentException("empty story option text");
        }
    }
    public StoryDialoguePayload {
        if (entityId < 0) throw new IllegalArgumentException("negative story entity id");
        role = StoryWire.text(role, MAX_TITLE, "role");
        name = StoryWire.text(name, MAX_TITLE, "name");
        Objects.requireNonNull(portrait, "portrait");
        title = StoryWire.text(title, MAX_TITLE, "title");
        lines = StoryWire.texts(lines, MAX_LINES, MAX_LINE, "lines");
        options = List.copyOf(options);
        if (options.size() > MAX_OPTIONS) throw new IllegalArgumentException("too many story options");
        HashSet<String> ids = new HashSet<>();
        for (Option option : options)
            if (!ids.add(option.id())) throw new IllegalArgumentException("duplicate story option " + option.id());
    }
    public static void installReceiver(Consumer<StoryDialoguePayload> next) { receiver = Objects.requireNonNull(next); }
    static void receive(StoryDialoguePayload payload) { receiver.accept(payload); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
