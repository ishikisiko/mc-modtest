package com.example.myvillage.sim.runtime.net;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.portrait.PortraitSpec;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientbound: one page of the sect dialogue, built by the server ({@code SectDialogue}) from the
 * ledger. The client only draws it: {@code lines} are translatable components (each client reads
 * its own language), {@code options} the buttons in order as option ids (0 JOIN, 1 LEAVE,
 * 2 FAREWELL, 3 APPRENTICE, 4 TASK_ACCEPT, 5 TASK_TURN_IN). Names are literals. {@code myRank} is "" for a player who is not of this sect;
 * {@code admissible}/{@code reason} are the ledger's admission of this player to this sect.
 * {@code portrait} is the speaker's portrait parts (the client composes the picture; twelve bytes).
 *
 * <p>Bounds (encoder and decoder): {@link #MAX_LINES} lines, {@link #MAX_OPTIONS} options each
 * 0..{@value #MAX_OPTION_ID}, names {@value #MAX_NAME} chars, role and rank {@value #MAX_WORD}, reason
 * {@value #MAX_REASON}; out-of-range input is rejected with a {@link DecoderException}.
 */
public record SectDialoguePayload(
        int entityId,
        int sectId,
        String sectName,
        String role,
        String avatarName,
        PortraitSpec portrait,
        int prestige,
        int memberCount,
        String masterName,
        String regionName,
        String myRank,
        int myStanding,
        boolean admissible,
        String reason,
        List<Component> lines,
        List<Integer> options) implements CustomPacketPayload {

    public static final int MAX_LINES = 8;
    public static final int MAX_OPTIONS = 4;
    public static final int MAX_OPTION_ID = 5;
    public static final int MAX_NAME = 64;
    public static final int MAX_WORD = 16;
    public static final int MAX_REASON = 32;

    public static final Type<SectDialoguePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "sect_dialogue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SectDialoguePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SectDialoguePayload decode(RegistryFriendlyByteBuf buffer) {
            int entityId = buffer.readVarInt();
            int sectId = buffer.readVarInt();
            String sectName = buffer.readUtf(MAX_NAME);
            String role = buffer.readUtf(MAX_WORD);
            String avatarName = buffer.readUtf(MAX_NAME);
            PortraitSpec portrait = PortraitSpec.read(buffer);
            int prestige = buffer.readVarInt();
            int memberCount = buffer.readVarInt();
            String masterName = buffer.readUtf(MAX_NAME);
            String regionName = buffer.readUtf(MAX_NAME);
            String myRank = buffer.readUtf(MAX_WORD);
            int myStanding = buffer.readVarInt();
            boolean admissible = buffer.readBoolean();
            String reason = buffer.readUtf(MAX_REASON);
            int lineCount = buffer.readVarInt();
            if (lineCount < 0 || lineCount > MAX_LINES) {
                throw new DecoderException("sect dialogue line count " + lineCount + " outside 0.." + MAX_LINES);
            }
            List<Component> lines = new ArrayList<>(lineCount);
            for (int i = 0; i < lineCount; i++) {
                lines.add(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer));
            }
            int optionCount = buffer.readVarInt();
            if (optionCount < 0 || optionCount > MAX_OPTIONS) {
                throw new DecoderException("sect dialogue option count " + optionCount + " outside 0.." + MAX_OPTIONS);
            }
            List<Integer> options = new ArrayList<>(optionCount);
            for (int i = 0; i < optionCount; i++) {
                int option = buffer.readUnsignedByte();
                if (option > MAX_OPTION_ID) {
                    throw new DecoderException("unknown sect dialogue option " + option);
                }
                options.add(option);
            }
            return new SectDialoguePayload(entityId, sectId, sectName, role, avatarName, portrait, prestige,
                    memberCount, masterName, regionName, myRank, myStanding, admissible, reason, lines, options);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, SectDialoguePayload p) {
            buffer.writeVarInt(p.entityId());
            buffer.writeVarInt(p.sectId());
            buffer.writeUtf(p.sectName(), MAX_NAME);
            buffer.writeUtf(p.role(), MAX_WORD);
            buffer.writeUtf(p.avatarName(), MAX_NAME);
            p.portrait().write(buffer);
            buffer.writeVarInt(p.prestige());
            buffer.writeVarInt(p.memberCount());
            buffer.writeUtf(p.masterName(), MAX_NAME);
            buffer.writeUtf(p.regionName(), MAX_NAME);
            buffer.writeUtf(p.myRank(), MAX_WORD);
            buffer.writeVarInt(p.myStanding());
            buffer.writeBoolean(p.admissible());
            buffer.writeUtf(p.reason(), MAX_REASON);
            buffer.writeVarInt(p.lines().size());
            for (Component line : p.lines()) {
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, line);
            }
            buffer.writeVarInt(p.options().size());
            for (int option : p.options()) {
                buffer.writeByte(option);
            }
        }
    };

    /** Where the client side hands a received page (installed by {@code ClientSectDialogue}); a no-op on a server. */
    private static volatile Consumer<SectDialoguePayload> receiver = ignored -> {
    };

    public SectDialoguePayload {
        sectName = bounded(sectName, MAX_NAME, "sectName");
        role = bounded(role, MAX_WORD, "role");
        avatarName = bounded(avatarName, MAX_NAME, "avatarName");
        Objects.requireNonNull(portrait, "portrait");
        masterName = bounded(masterName, MAX_NAME, "masterName");
        regionName = bounded(regionName, MAX_NAME, "regionName");
        myRank = bounded(myRank, MAX_WORD, "myRank");
        reason = bounded(reason, MAX_REASON, "reason");
        lines = List.copyOf(lines);
        options = List.copyOf(options);
        if (lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("at most " + MAX_LINES + " lines, got " + lines.size());
        }
        if (options.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("at most " + MAX_OPTIONS + " options, got " + options.size());
        }
        for (int option : options) {
            if (option < 0 || option > MAX_OPTION_ID) {
                throw new IllegalArgumentException("unknown sect dialogue option " + option);
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

    /** Cuts a name to {@code max} chars, for the server building a page from ledger names. */
    public static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Installed once by the client; on a dedicated server nothing is installed and nothing arrives. */
    public static void installReceiver(Consumer<SectDialoguePayload> newReceiver) {
        receiver = Objects.requireNonNull(newReceiver, "newReceiver");
    }

    /** Hands a received page to the client side (called on the client thread). */
    static void receive(SectDialoguePayload payload) {
        receiver.accept(Objects.requireNonNull(payload, "payload"));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
