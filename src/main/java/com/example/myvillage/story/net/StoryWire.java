package com.example.myvillage.story.net;

import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Shared allocation and identifier bounds; no client classes are reachable from this package. */
final class StoryWire {
    private StoryWire() { }

    static String text(String value, int max, String field) {
        Objects.requireNonNull(value, field);
        if (value.length() > max) throw new IllegalArgumentException(field + " exceeds " + max);
        return value;
    }

    static String optionId(String value) {
        text(value, 64, "optionId");
        if (!value.matches("[a-zA-Z0-9_.:-]{1,64}"))
            throw new IllegalArgumentException("invalid story option id");
        return value;
    }

    static List<String> texts(List<String> value, int count, int length, String field) {
        List<String> copy = List.copyOf(value);
        if (copy.size() > count) throw new IllegalArgumentException(field + " exceeds " + count);
        copy.forEach(item -> text(item, length, field));
        return copy;
    }

    static int count(RegistryFriendlyByteBuf buffer, int max, String field) {
        int count = buffer.readVarInt();
        if (count < 0 || count > max) throw new DecoderException("invalid " + field + " count " + count);
        return count;
    }

    static <T> T decode(Supplier<T> read) {
        try { return read.get(); }
        catch (IllegalArgumentException | NullPointerException exception) {
            throw new DecoderException("invalid story payload", exception);
        }
    }
}
