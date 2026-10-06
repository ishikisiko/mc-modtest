package com.example.myvillage.sim.runtime.net;

import com.example.myvillage.MyVillageMod;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientbound: the scripture hall (藏经阁) as seen from one shelf, built by the server
 * ({@code ScriptureHall}) from the ledger and the technique registry. The client only draws it.
 * {@code pos} is the shelf the player used (sent back with a borrow), {@code myRank} is "" for a
 * player who is not of this sect, {@code member}/{@code reason} say whether the list applies
 * ({@code reason} e.g. {@code ok}, {@code not_member}, {@code member_elsewhere}). Each
 * {@link Entry} is one borrowable technique: its ledger id (a path, no namespace), its name as a
 * translatable component, grade 0..{@value #MAX_GRADE}, category, whether this player has borrowed
 * it already, and the borrow cost.
 *
 * <p>Bounds (constructor and decoder): at most {@value #MAX_ENTRIES} entries, sect name
 * {@value #MAX_NAME} chars, rank {@value #MAX_WORD}, reason {@value #MAX_REASON}, technique id
 * {@value #MAX_TECHNIQUE_ID}, category {@value #MAX_WORD}, grade 0..{@value #MAX_GRADE}, cost
 * &ge; 0; out-of-range input is rejected with a {@link DecoderException} on decode and an
 * IllegalArgumentException when built.
 */
public record ScriptureHallPayload(
        BlockPos pos,
        int sectId,
        String sectName,
        String myRank,
        boolean member,
        String reason,
        List<Entry> entries) implements CustomPacketPayload {

    public static final int MAX_ENTRIES = 16;
    public static final int MAX_NAME = 64;
    public static final int MAX_WORD = 16;
    public static final int MAX_REASON = 32;
    public static final int MAX_TECHNIQUE_ID = 64;
    public static final int MAX_GRADE = 4;

    public static final Type<ScriptureHallPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "scripture_hall"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScriptureHallPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ScriptureHallPayload decode(RegistryFriendlyByteBuf buffer) {
            BlockPos pos = buffer.readBlockPos();
            int sectId = buffer.readVarInt();
            String sectName = buffer.readUtf(MAX_NAME);
            String myRank = buffer.readUtf(MAX_WORD);
            boolean member = buffer.readBoolean();
            String reason = buffer.readUtf(MAX_REASON);
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_ENTRIES) {
                throw new DecoderException("scripture hall entry count " + count + " outside 0.." + MAX_ENTRIES);
            }
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String techniqueId = buffer.readUtf(MAX_TECHNIQUE_ID);
                Component name = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
                int grade = buffer.readUnsignedByte();
                if (grade > MAX_GRADE) {
                    throw new DecoderException("scripture hall grade " + grade + " outside 0.." + MAX_GRADE);
                }
                String category = buffer.readUtf(MAX_WORD);
                boolean borrowed = buffer.readBoolean();
                int cost = buffer.readVarInt();
                if (cost < 0) {
                    throw new DecoderException("scripture hall cost " + cost + " is negative");
                }
                entries.add(new Entry(techniqueId, name, grade, category, borrowed, cost));
            }
            return new ScriptureHallPayload(pos, sectId, sectName, myRank, member, reason, entries);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ScriptureHallPayload p) {
            buffer.writeBlockPos(p.pos());
            buffer.writeVarInt(p.sectId());
            buffer.writeUtf(p.sectName(), MAX_NAME);
            buffer.writeUtf(p.myRank(), MAX_WORD);
            buffer.writeBoolean(p.member());
            buffer.writeUtf(p.reason(), MAX_REASON);
            buffer.writeVarInt(p.entries().size());
            for (Entry e : p.entries()) {
                buffer.writeUtf(e.techniqueId(), MAX_TECHNIQUE_ID);
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, e.name());
                buffer.writeByte(e.grade());
                buffer.writeUtf(e.category(), MAX_WORD);
                buffer.writeBoolean(e.borrowed());
                buffer.writeVarInt(e.cost());
            }
        }
    };

    /** Where the client side hands a received hall (installed by the client screen glue); a no-op on a server. */
    private static volatile Consumer<ScriptureHallPayload> receiver = ignored -> {
    };

    public ScriptureHallPayload {
        Objects.requireNonNull(pos, "pos");
        pos = pos.immutable();
        sectName = bounded(sectName, MAX_NAME, "sectName");
        myRank = bounded(myRank, MAX_WORD, "myRank");
        reason = bounded(reason, MAX_REASON, "reason");
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("at most " + MAX_ENTRIES + " entries, got " + entries.size());
        }
    }

    /** One borrowable technique as the hall lists it. */
    public record Entry(String techniqueId, Component name, int grade, String category, boolean borrowed, int cost) {
        public Entry {
            techniqueId = bounded(techniqueId, MAX_TECHNIQUE_ID, "techniqueId");
            Objects.requireNonNull(name, "name");
            category = bounded(category, MAX_WORD, "category");
            if (grade < 0 || grade > MAX_GRADE) {
                throw new IllegalArgumentException("grade " + grade + " outside 0.." + MAX_GRADE);
            }
            if (cost < 0) {
                throw new IllegalArgumentException("cost " + cost + " is negative");
            }
        }
    }

    private static String bounded(String value, int max, String what) {
        Objects.requireNonNull(value, what);
        if (value.length() > max) {
            throw new IllegalArgumentException(what + " longer than " + max + " chars");
        }
        return value;
    }

    /** Cuts a name to {@code max} chars, for the server building a hall from ledger names. */
    public static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Installed once by the client; on a dedicated server nothing is installed and nothing arrives. */
    public static void installReceiver(Consumer<ScriptureHallPayload> newReceiver) {
        receiver = Objects.requireNonNull(newReceiver, "newReceiver");
    }

    /** Hands a received hall to the client side (called on the client thread). */
    static void receive(ScriptureHallPayload payload) {
        receiver.accept(Objects.requireNonNull(payload, "payload"));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
